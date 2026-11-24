// Ficha de ingreso de la COM manual (0.8.0, D33, gate E04): bloque intake de COM_CREATE y COM_COMPLETE, con la lista
// cerrada comparada con IntakeRecord.java. Ninguna pantalla envia el formato anterior (campos sueltos). node --test.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {comCreatePayload, completePayload, emptyIntake, INTAKE_KEYS, INTAKE_ROLES, INTAKE_SCHEMA, intakeFromSaved, intakePayload, intakeProblems, LEGACY_COM_FIELDS, MAX_PERSONS, presenterKindOf} from '../src/features/intake/intakeModel.ts';
import {emptyPerson, PERSON_FIELDS} from '../src/lib/person/personModel.ts';

const root = (path: string) => fileURLToPath(new URL(path, import.meta.url));
const java = readFileSync(root('../../backend/src/main/java/cl/nexbyte/sigera/IntakeRecord.java'), 'utf8');
const declared = (name: string) => {
  const at = java.indexOf(name);
  assert.ok(at >= 0, name);
  return [...java.slice(at, java.indexOf(')', at)).matchAll(/"([^"]*)"/g)].map(m => m[1]);
};
const BASE = {scope: '3', channel: 'PRESENCIAL', received: '2026-11-24T10:00', subject: 'Consulta', body: 'Relato', attachments: []};

test('lista cerrada, roles, maximo y campos del formato anterior iguales a IntakeRecord', () => {
  assert.deepEqual([...INTAKE_KEYS], declared('KEYS=List.of('));
  assert.deepEqual([...INTAKE_ROLES].sort(), declared('PERSON_ROLES=Set.of(').sort());
  assert.deepEqual([...LEGACY_COM_FIELDS], declared('LEGACY_FIELDS=List.of('));
  assert.ok(java.includes(`SCHEMA="${INTAKE_SCHEMA}"`));
  assert.ok(java.includes(`MAX_PERSONS=${MAX_PERSONS};`));
  assert.deepEqual(Object.keys(intakePayload(emptyIntake(), 'PERSONA')), [...INTAKE_KEYS]);
});

test('el ingreso minimo de hoy (asunto y relato) se registra sin campos obligatorios nuevos ni campos sueltos', () => {
  assert.deepEqual(intakeProblems(emptyIntake(), 'PERSONA'), {});
  const payload = comCreatePayload(BASE, emptyIntake(), 'PERSONA');
  for (const key of LEGACY_COM_FIELDS) assert.ok(!(key in payload), key);
  assert.deepEqual(Object.keys(payload).sort(), ['attachment_tokens', 'body', 'channel', 'intake', 'received_at', 'scope_id', 'subject']);
  assert.deepEqual((payload.intake as Record<string, unknown>).presenter, {}, 'presentador vacio = {}');
});

test('un ingreso institucional se registra sin datos de persona natural', () => {
  const data = {...emptyIntake(), presenter: {...emptyPerson('REQUIRENTE'), first_name: 'Escrito antes'}, institutions_list: [{name: 'Juzgado de Familia', type_code: '', role: 'Envía oficio', region_code: '07', commune: 'Talca'}]};
  assert.deepEqual(intakeProblems(data, 'INSTITUCION'), {});
  const intake = intakePayload(data, 'INSTITUCION');
  assert.deepEqual(intake.presenter, {}, 'no se envia una persona natural');
  assert.equal((intake.institutions_list as unknown[]).length, 1);
  assert.match(intakeProblems(emptyIntake(), 'INSTITUCION').institutions_list, /institución/);
  assert.equal(presenterKindOf(intakeFromSaved(intake)), 'INSTITUCION');
  assert.equal(presenterKindOf(emptyIntake()), 'PERSONA');
});

test('quien presenta y las personas siguen las reglas de persona-1; cada persona con algun dato', () => {
  const data = {...emptyIntake(), presenter: {...emptyPerson('REQUIRENTE'), identity_type: 'RUN', run_number: '12345678', run_dv: '6', mobile: '123'}, persons: [emptyPerson('NNA')]};
  const problems = intakeProblems(data, 'PERSONA');
  assert.deepEqual(Object.keys(problems).sort(), ['persons.0.role', 'presenter.mobile', 'presenter.run_number']);
  const ok = {...data, presenter: {...data.presenter, run_dv: '5', mobile: '+56912345678', region_code: '07'}, persons: [{...emptyPerson('NNA'), age_years: '9'}]};
  assert.deepEqual(intakeProblems(ok, 'PERSONA'), {});
  const intake = intakePayload(ok, 'PERSONA');
  assert.equal((intake.presenter as Record<string, string>).role, 'REQUIRENTE');
  assert.deepEqual(Object.keys(intake.presenter as object), [...PERSON_FIELDS]);
});

test('complementar: lo historico identico se conserva y se reenvia sin versiones derivadas', () => {
  const saved = {schema_version: '1.0', presenter: {...emptyPerson('REQUIRENTE'), first_name: 'Ana', mobile: '22 345 6789', relationship_code: 'MADRE', relationship_version: 3},
    persons: [{...emptyPerson('NNA'), legacy_name: 'Hijo de Ana'}], contact_restrictions: '', facts_region_code: '', facts_commune: '', facts_place: '',
    institutions_list: [{name: 'Liceo', type_code: 'EDUCACION', type_version: 4, role: '', region_code: '', commune: ''}]};
  const data = intakeFromSaved(saved);
  assert.deepEqual(intakeProblems(data, 'PERSONA', saved), {}, 'celular, codigo e historico identicos no se revalidan');
  const body = completePayload({...data, facts_region_code: '07'}, 'PERSONA', 'Se informa la región');
  assert.equal(body.reason, 'Se informa la región');
  assert.equal(JSON.stringify(body).match(/"\w*_version"/g)?.join(), '"schema_version"');
  assert.ok(intakeProblems({...data, persons: [{...data.persons[0], legacy_name: 'Otro'}]}, 'PERSONA', saved)['persons.0.legacy_name']);
});

test('ninguna pantalla envia el formato anterior de COM_CREATE ni de COM_IMMEDIATE', () => {
  const files: string[] = [];
  const walk = (dir: string) => {for (const e of readdirSync(dir, {withFileTypes: true})) e.isDirectory() ? walk(join(dir, e.name)) : /\.tsx?$/.test(e.name) && files.push(join(dir, e.name));};
  walk(root('../src'));
  const senders = files.filter(f => /'COM_CREATE'|'COM_IMMEDIATE'/.test(readFileSync(f, 'utf8')));
  assert.deepEqual(senders.map(f => f.replace(/\\/g, '/').replace(/^.*\/src\//, '')), ['features/intake/Intake.tsx']);
  const intake = readFileSync(senders[0], 'utf8');
  assert.ok(intake.includes("command('COM_CREATE', null, null, payload") && intake.includes('comCreatePayload('));
  assert.ok(!/\bpersons\b\s*[,}]|contact_email|presenter_domicile/.test(intake.slice(intake.indexOf('async function submit'))), 'sin campos sueltos');
});
