// Persona declarada canonica persona-1 (0.8.0, D33, gate E04): reglas de lib/person/personModel.ts, iguales a las de
// DeclaredPerson.java (lista de campos comparada con el codigo del servidor). node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {blankPerson, canonicalInstitutions, canonicalPerson, changeSummaryText, codeLabel, emptyPerson, factProblems, FROM_11, INSTITUTION_FIELDS, institutionProblems,
  legacyValueOf, NO_CODES, PERSON_FIELDS, personDocument, personFacts, personName, personPayload, personProblems, relationLines, RETIRED_CODE_TEXT, statCodes, TREATMENTS} from '../src/lib/person/personModel.ts';

const java = (name: string) => readFileSync(fileURLToPath(new URL('../../backend/src/main/java/cl/nexbyte/sigera/' + name, import.meta.url)), 'utf8');
/** Lista de cadenas de una declaracion Java `NAME=List.of(...)` o `Set.of(...)`. */
function javaList(source: string, name: string): string[] {
  const at = source.indexOf(name + '=');
  assert.ok(at >= 0, name);
  const body = source.slice(source.indexOf('(', at) + 1, source.indexOf(')', at));
  return [...body.matchAll(/"([^"]*)"/g)].map(m => m[1]);
}

/** Catalogo de sesion con RELACION y TIPO_INSTITUCION vigentes (como lo publica StatCatalogs.published). */
const CATALOG = {stat_catalogs: {catalogs: [
  {catalog: 'RELACION', version: {id: 7}, items: [{code: 'MADRE', label: 'Madre', selectable: true, level: 1}, {code: 'OTRO_FAMILIAR', label: 'Otro familiar', selectable: true, level: 1}]},
  {catalog: 'TIPO_INSTITUCION', version: {id: 8}, items: [{code: 'EDUCACION', label: 'Establecimiento educativo', selectable: true, level: 1}, {code: 'GRUPO', label: 'Grupo', selectable: false, level: 1}]},
  {catalog: 'DERECHO', version: null, items: []},
]}};
const codes = statCodes(CATALOG);
const person = (over: Partial<Record<string, string>>) => ({...emptyPerson('NNA'), ...over});

test('persona-1 igual al servidor: campos, roles de instituciones, equivalencias de 1.1 y trato', () => {
  const source = java('DeclaredPerson.java');
  assert.deepEqual([...PERSON_FIELDS], javaList(source, 'FIELDS'));
  assert.deepEqual([...INSTITUTION_FIELDS], javaList(source, 'INSTITUTION_FIELDS'));
  assert.deepEqual(TREATMENTS.map(([v]) => v), javaList(source, 'TREATMENTS'));
  const from = source.slice(source.indexOf('FROM_11=Map.of('), source.indexOf(');', source.indexOf('FROM_11=Map.of(')));
  const pairs = [...from.matchAll(/"([^"]*)"/g)].map(m => m[1]);
  assert.deepEqual(Object.fromEntries(pairs.flatMap((v, i) => i % 2 ? [] : [[v, pairs[i + 1]]])), FROM_11);
});

test('una persona 1.0/1.1 pasa a persona-1 con su texto libre como historico, sin dividir el nombre', () => {
  const p = canonicalPerson({role: 'NNA', name: 'María José Pérez Soto', age: 'unos 10', identity_document: '12.345.678-5', nationality: 'chilena', relationship: 'hija', location: 'Talca', birth_date: '2015-01-02', schooling: '4º básico', preferred_name: 'Majo'});
  assert.equal(p.legacy_name, 'María José Pérez Soto');
  assert.equal(p.first_name, '');assert.equal(p.last_name, '');
  assert.equal(p.legacy_age, 'unos 10');assert.equal(p.age_years, '');
  assert.equal(p.legacy_document, '12.345.678-5');assert.equal(p.run_number, '');
  assert.equal(p.legacy_nationality, 'chilena');assert.equal(p.nationality, '');
  assert.equal(p.relationship_text, 'hija');assert.equal(p.location_text, 'Talca');
  assert.equal(p.birth_date, '2015-01-02');assert.equal(p.preferred_name, 'Majo');
  assert.equal(personName(p), 'María José Pérez Soto');
  assert.equal(personDocument(p), '12.345.678-5');
  // Lo historico identico se conserva sin revalidarse; crearlo o cambiarlo no.
  assert.deepEqual(personProblems(p, [p]), {});
  assert.ok(personProblems(person({legacy_name: 'Nuevo'})).legacy_name);
  assert.ok(personProblems({...p, legacy_age: 'once'}, [p]).legacy_age);
  assert.deepEqual(personProblems({...p, legacy_name: ''}, [p]), {}, 'quitar un historico se admite');
});

test('reglas del Formulario: nombres, RUN, documento, nacimiento, contacto, region y nacionalidad', () => {
  assert.deepEqual(personProblems(emptyPerson('NNA')), {}, 'vacio nunca es error');
  assert.match(personProblems(person({first_name: 'Juan2'})).first_name, /números/);
  assert.match(personProblems(person({identity_type: 'RUN', run_number: '12345678', run_dv: '6'})).run_number, /no coinciden/);
  assert.match(personProblems(person({identity_type: 'RUN', run_number: '12345678'})).run_number, /Completa/);
  assert.match(personProblems(person({identity_type: 'NO_DISPONIBLE', run_number: '12345678', run_dv: '5'})).run_number, /RUN chileno/);
  assert.deepEqual(personProblems(person({identity_type: 'RUN', run_number: '12345678', run_dv: '5'})), {});
  assert.match(personProblems(person({identity_type: 'DOCUMENTO_EXTRANJERO', document_kind: 'PASAPORTE', document_number: 'A1'})).document_number, /completo/);
  assert.match(personProblems(person({document_number: 'AB123456'})).document_number, /Documento extranjero/);
  assert.match(personProblems(person({birth_date: '2999-01-01'})).birth_date, /posterior/);
  assert.ok(personProblems(person({mobile: '+5691234'})).mobile);
  assert.ok(personProblems(person({email: 'no-es-correo'})).email);
  assert.ok(personProblems(person({region_code: 'Santiago'})).region_code);
  assert.ok(personProblems(person({region_code: '13', commune: 'Viña del Mar'})).commune);
  assert.ok(personProblems(person({nationality: 'chilena'})).nationality);
  assert.deepEqual(personProblems(person({first_name: 'Ana', last_name: "O'Higgins", region_code: '13', commune: 'Ñuñoa', nationality: 'Chile', mobile: '+56912345678', email: 'ana@ejemplo.cl'})), {});
  assert.equal(personDocument(person({identity_type: 'DOCUMENTO_EXTRANJERO', document_kind: 'PASAPORTE', document_number: 'AB 123'})), 'Pasaporte AB 123');
});

test('edad: entero de 0 a 17 para un NNA y de 0 a 130 para otra persona', () => {
  assert.deepEqual(personProblems(person({age_years: '17'})), {});
  assert.match(personProblems(person({age_years: '18'})).age_years, /0 y 17/);
  assert.match(personProblems(person({age_years: '10,5'})).age_years, /entero/);
  assert.deepEqual(personProblems({...person({age_years: '45'}), role: 'ACOMPANANTE'}), {});
  assert.match(personProblems({...person({age_years: '131'}), role: 'ACOMPANANTE'}).age_years, /0 y 130/);
});

test('un valor identico al de cualquier persona de la version anterior se conserva sin revalidarse', () => {
  const old = person({mobile: '22 345 6789', region_code: 'Biobío'});
  assert.deepEqual(personProblems(old, [person({}), old]), {});
  assert.ok(personProblems({...old, mobile: '22 345 6780'}, [old]).mobile);
  assert.equal(legacyValueOf(old, [old], 'region_code'), 'Biobío');
  assert.equal(legacyValueOf(old, [], 'region_code'), '', 'sin version anterior no es historico');
});

test('codigos solo del listado vigente; sin vigente no se admite un codigo nuevo', () => {
  assert.equal(codes.relationship.version, 7);
  assert.deepEqual(personProblems(person({relationship_code: 'MADRE'}), [], codes), {});
  assert.ok(personProblems(person({relationship_code: 'MADRE'}), [], NO_CODES).relationship_code);
  assert.ok(personProblems(person({relationship_code: 'TIA'}), [], codes).relationship_code);
  // Un codigo ya registrado se conserva aunque el listado haya cambiado.
  const kept = person({relationship_code: 'PADRE'});
  assert.deepEqual(personProblems(kept, [kept], codes), {});
  assert.ok(institutionProblems({name: 'Liceo', type_code: 'GRUPO', role: '', region_code: '', commune: ''}, [], codes).type_code, 'item no seleccionable');
  assert.deepEqual(institutionProblems({name: 'Liceo', type_code: 'EDUCACION', role: '', region_code: '13', commune: 'Ñuñoa'}, [], codes), {});
  assert.match(institutionProblems({name: '', type_code: '', role: '', region_code: '', commune: ''}).name, /nombre/);
  assert.deepEqual(statCodes({}), NO_CODES);
});

test('rotulos sin codigos: listado vigente o aviso de listado anterior con el texto existente', () => {
  assert.equal(codeLabel(codes.relationship, 'MADRE'), 'Madre');
  assert.equal(codeLabel(codes.relationship, 'PADRE'), RETIRED_CODE_TEXT);
  assert.deepEqual(relationLines(codes.relationship, 'PADRE', 'padre biológico'), ['padre biológico', RETIRED_CODE_TEXT]);
  assert.deepEqual(relationLines(codes.relationship, 'MADRE', ''), ['Madre']);
  const facts = personFacts(person({first_name: 'Ana', relationship_code: 'OTRO_FAMILIAR', region_code: '07', identity_type: 'RUN', run_number: '12345678', run_dv: '5'}), codes);
  assert.deepEqual(facts.find(([l]) => l === 'Relación'), ['Relación', 'Otro familiar']);
  assert.deepEqual(facts.find(([l]) => l === 'Región'), ['Región', 'Maule']);
  assert.ok(!JSON.stringify(facts).includes('OTRO_FAMILIAR') && !JSON.stringify(facts).includes('"RUN"'));
});

test('datos que se envian: solo persona-1, sin versiones de catalogo derivadas', () => {
  const saved = {...person({relationship_code: 'MADRE'}), relationship_version: 7} as unknown as Record<string, unknown>;
  const p = canonicalPerson(saved);
  assert.ok(!('relationship_version' in p));
  assert.deepEqual(Object.keys(personPayload({...p, extra: 'x'} as never)), [...PERSON_FIELDS]);
  assert.deepEqual(canonicalInstitutions([{name: 'Liceo', type_code: 'EDUCACION', type_version: 8}]), [{name: 'Liceo', type_code: 'EDUCACION', role: '', region_code: '', commune: ''}]);
  assert.ok(blankPerson(emptyPerson('REQUIRENTE')) && !blankPerson(person({address: 'x'})));
  assert.match(personProblems(emptyPerson('NNA'), [], NO_CODES, true).role, /algún antecedente/);
});

test('hechos y resumen de cambios', () => {
  assert.deepEqual(factProblems({facts_region_code: '07', facts_commune: 'Talca'}), {});
  assert.ok(factProblems({facts_region_code: '07', facts_commune: 'Ñuñoa'}).facts_commune);
  assert.deepEqual(factProblems({facts_region_code: 'Maule'}, {facts_region_code: 'Maule'}), {});
  assert.equal(changeSummaryText(JSON.stringify({COMPLEMENTO: ['a', 'b'], RECTIFICACION: ['c']})), '2 datos complementados y 1 dato rectificado.');
  assert.equal(changeSummaryText({COMPLEMENTO: ['region'], RECTIFICACION: []}), '1 dato complementado.');
  assert.equal(changeSummaryText(null), '');
  assert.equal(changeSummaryText('no-json'), '');
});
