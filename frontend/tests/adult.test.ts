// Pruebas de la ficha adulta 1.1 sin dependencias: node --test (Node 24 ejecuta TypeScript quitando los tipos).
// Cubren catalogos, validaciones, normalizacion del celular, actualizacion de fichas 1.0 y reglas de datos historicos.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {communesOf, countryNames, matchCountry, matchRegion, NATIONALITY_PREFIX, REGIONS, regionCodes, searchCountries, validCommune, validNationality, validRegion} from '../src/features/attention/adult/catalog.ts';
import {birthDateProblem, canonicalMobile, documentNumberProblem, formatRunNumber, nameProblem, normalizeName, pasteMobile, pastedRun, validEmail, validRun} from '../src/features/attention/adult/identity.ts';
import {adultProblems, documentSummary, fullName, legacyValue, SCHEMA_VERSION, upgrade} from '../src/features/attention/adult/adultForm.ts';

const json = (path: string) => JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));

test('catalogos identicos a los del servidor y al Formulario Publico', () => {
  for (const name of ['territorio.json', 'paises.json'])
    assert.deepEqual(json('../src/features/attention/adult/' + name), json('../../backend/src/main/resources/catalog/' + name), name);
  assert.equal(regionCodes.length, 16);
  assert.equal(regionCodes.reduce((n, code) => n + communesOf(code).length, 0), 346);
  assert.equal(countryNames.length, 249);
  assert.equal(REGIONS['13'], 'Metropolitana de Santiago');
  assert.ok(validCommune('13', 'Ñuñoa'));
  assert.ok(!validCommune('05', 'Ñuñoa'));
  assert.ok(!validRegion('Santiago'));
  assert.equal(matchRegion('metropolitana de santiago'), '13');
  assert.equal(matchRegion('Nuble'), '16');
  assert.equal(matchRegion('Santiago'), null);
  assert.equal(matchCountry('peru'), 'Perú');
  assert.ok(searchCountries('peru').includes('Perú'));
  assert.ok(validNationality('Chile') && validNationality(NATIONALITY_PREFIX + 'Apátrida'));
  assert.ok(!validNationality('chilena') && !validNationality(NATIONALITY_PREFIX + '  ') && !validNationality(NATIONALITY_PREFIX + '123'));
});

test('RUN: validacion matematica, pegado y formato', () => {
  assert.ok(validRun('12345678', '5'));
  assert.ok(validRun('10000013', 'k'));
  assert.ok(!validRun('12345678', '6'));
  assert.ok(!validRun('012345678', '5'));
  assert.deepEqual(pastedRun('12.345.678-5'), {numero: '12345678', dv: '5'});
  assert.deepEqual(pastedRun('123456785'), {numero: '12345678', dv: '5'});
  assert.equal(pastedRun('1234'), null);
  assert.equal(formatRunNumber('12345678'), '12.345.678');
});

test('celular chileno: prefijo fijo, ocho digitos, pegado y formato normalizado', () => {
  for (const raw of ['12345678', '912345678', '+56 9 1234 5678', '56912345678', '0056912345678', '+56 (9) 1234-5678']) assert.equal(canonicalMobile(raw), '+56912345678', raw);
  for (const raw of ['1234567', '+56 2 2345 6789', '+1 555 1234567', 'abc', '9123456789']) assert.equal(canonicalMobile(raw), null, raw);
  assert.equal(canonicalMobile(''), '');
  assert.deepEqual(pasteMobile('+56 9 8765 4321', '', 0, 0), {local: '87654321', cursor: 8});
  assert.deepEqual(pasteMobile('4321', '8765', 4, 4), {local: '87654321', cursor: 8});
  assert.ok('error' in pasteMobile('123456789', '1', 1, 1));
  assert.ok('error' in pasteMobile('fono: x', '', 0, 0));
});

test('correo, nombres, documento y fecha de nacimiento', () => {
  assert.ok(validEmail('persona@ejemplo.cl') && validEmail('María.José@niñez.cl'));
  for (const bad of ['sin-arroba', 'a@b', 'a..b@ejemplo.cl', 'a b@ejemplo.cl', 'a@ejemplo.cl\nBcc: x']) assert.ok(!validEmail(bad), bad);
  assert.equal(nameProblem('María José'), null);
  assert.equal(nameProblem("O'Higgins"), null);
  assert.equal(nameProblem('J'), null); // dato parcial admitido
  assert.equal(normalizeName('  María' + String.fromCharCode(0xa0) + ' José '), 'María José'); // igual que el servidor (espacio sin quiebre)
  assert.ok(validEmail(String.fromCharCode(0xa0) + 'persona@ejemplo.cl'));
  assert.match(nameProblem('Juan2') ?? '', /números/);
  assert.match(nameProblem('Juan  -') ?? '', /espacios y signos/);
  assert.equal(documentNumberProblem('AB123456'), null);
  assert.match(documentNumberProblem('A1') ?? '', /completo/);
  assert.equal(birthDateProblem(''), null);
  assert.equal(birthDateProblem('1990-02-28'), null);
  assert.match(birthDateProblem('1990-02-30') ?? '', /válidos/);
  assert.match(birthDateProblem('2999-01-01') ?? '', /posterior/);
});

test('ficha 1.0 se actualiza solo con conversiones equivalentes y conserva lo historico', () => {
  const old = {schema_version: '1.0', modality: 'GENERAL', motive: 'Consulta', presenter_name: 'Ana Pérez', identity_type: 'RUN', identity_document: '12.345.678-5',
    contact_phone: '+56 9 1234 5678', contact_email: ' ana@ejemplo.cl ', region: 'Metropolitana de Santiago', commune: 'nunoa', nationality: 'chilena', people: []};
  const up = upgrade(old);
  assert.equal(up.schema_version, SCHEMA_VERSION);
  assert.equal(up.contact_phone, '+56912345678');
  assert.equal(up.contact_email, 'ana@ejemplo.cl');
  assert.equal(up.region, '13');
  assert.equal(up.commune, 'Ñuñoa');
  assert.equal(up.nationality, 'chilena'); // no es un pais del catalogo: se conserva
  assert.equal(legacyValue(up, 'nationality'), 'chilena');
  assert.equal(up.presenter_run_number, '12345678');assert.equal(up.presenter_run_dv, '5');assert.equal(up.identity_document, '');
  assert.equal(up.presenter_name, 'Ana Pérez');
  assert.deepEqual(old.contact_phone, '+56 9 1234 5678'); // el original no se modifica
  // Valores fuera de catalogo se conservan y no se revalidan mientras no cambien.
  const keep = upgrade({schema_version: '1.0', modality: 'GENERAL', contact_phone: '22 345 6789', region: 'Santiago Centro', commune: 'Centro', identity_type: 'RUN', identity_document: 'sin dato', people: []});
  assert.equal(keep.contact_phone, '22 345 6789');assert.equal(keep.region, 'Santiago Centro');assert.equal(keep.identity_document, 'sin dato');
  assert.deepEqual(adultProblems(keep, keep), {});
  assert.ok(adultProblems({...keep, contact_phone: '22 345 6780'}, keep).contact_phone);
  // NNA no cambia.
  const nna = upgrade({schema_version: '1.0', modality: 'NNA', contact_phone: '+56 9 1234 5678', region: 'Biobío', people: []});
  assert.equal(nna.contact_phone, '+56 9 1234 5678');assert.equal(nna.region, 'Biobío');
  assert.deepEqual(adultProblems(nna), {});
});

test('identificacion adulta: parcial admitida, RUN completo y valido, datos derivados', () => {
  const base = {schema_version: SCHEMA_VERSION, modality: 'GENERAL', motive: 'Consulta', people: []};
  assert.deepEqual(adultProblems(base), {}); // solo el motivo es indispensable
  assert.match(adultProblems({...base, identity_type: 'RUN', presenter_run_number: '12345678'}).presenter_run_number, /Completa/);
  assert.match(adultProblems({...base, identity_type: 'RUN', presenter_run_number: '12345678', presenter_run_dv: '6'}).presenter_run_number, /no coinciden/);
  assert.deepEqual(adultProblems({...base, identity_type: 'RUN', presenter_run_number: '12345678', presenter_run_dv: '5'}), {});
  assert.ok(adultProblems({...base, region: '13', commune: 'Viña del Mar'}).commune);
  assert.ok(adultProblems({...base, contact_phone: '+5691234'}).contact_phone);
  assert.ok(adultProblems({...base, nationality: NATIONALITY_PREFIX}).nationality);
  const named = {...base, presenter_first_name: ' María  José ', presenter_last_name: 'Del Río', identity_type: 'RUN', presenter_run_number: '12345678', presenter_run_dv: '5'};
  assert.equal(fullName(named), 'María José Del Río');
  assert.equal(documentSummary(named), '12.345.678-5');
  assert.equal(documentSummary({...base, identity_type: 'OTRO', presenter_document_kind: 'PASAPORTE', presenter_document_number: 'AB 123'}), 'Pasaporte AB 123');
});
