// Ficha de primera atencion 1.2 (0.8.0, D33, gate E04): preparacion del borrador desde 1.0/1.1/1.2, validacion en ambas
// modalidades y datos que se envian, con la lista cerrada de campos comparada con Attention.java. node --test.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {emptyForm, FICHA_VERSION, KEYS_12, PERSON_ROLES, STRING_FIELDS} from '../src/features/attention/schema.ts';
import {fichaPayload, fichaProblems, prepareFicha, problemStep} from '../src/features/attention/fichaModel.ts';
import {emptyPerson, PERSON_FIELDS, statCodes} from '../src/lib/person/personModel.ts';

const java = readFileSync(fileURLToPath(new URL('../../backend/src/main/java/cl/nexbyte/sigera/Attention.java', import.meta.url)), 'utf8');
const strings = (from: number, to: number) => [...java.slice(from, to).matchAll(/"([^"]*)"/g)].map(m => m[1]);
const declared = (name: string, open = '(', close = ')') => {
  const at = java.indexOf(name);
  assert.ok(at >= 0, name);
  return strings(java.indexOf(open, at), java.indexOf(close, java.indexOf(open, at)));
};
const CATALOG = {stat_catalogs: {catalogs: [{catalog: 'RELACION', version: {id: 3}, items: [{code: 'MADRE', label: 'Madre', selectable: true, level: 1}]},
  {catalog: 'TIPO_INSTITUCION', version: {id: 4}, items: [{code: 'EDUCACION', label: 'Establecimiento educativo', selectable: true, level: 1}]}]}};
const codes = statCodes(CATALOG);

test('lista cerrada de la ficha 1.2 igual a la del servidor (Attention.normalize y version12)', () => {
  const choices = [...java.slice(java.indexOf('var choices='), java.indexOf('choices.forEach')).matchAll(/choices\.put\("([^"]+)"/g)].map(m => m[1]);
  const server = new Set(['schema_version', 'modality', 'course', 'people', ...declared('ADULT_KEYS='), ...declared('KEYS_12='),
    ...declared('shortFields=', '{', '}'), ...declared('longFields=', '{', '}'), ...choices]);
  const screen = new Set(Object.keys(fichaPayload(emptyForm())));
  assert.deepEqual([...screen].sort(), [...server].sort());
  assert.deepEqual([...KEYS_12], declared('KEYS_12='));
  assert.deepEqual([...PERSON_ROLES].sort(), declared('PEOPLE_ROLES=').sort());
  assert.ok(declared('VERSIONS=').includes(FICHA_VERSION) && declared('VERSIONS=').at(-1) === FICHA_VERSION);
  assert.equal(new Set(STRING_FIELDS).size, STRING_FIELDS.length, 'sin campos repetidos');
});

test('una ficha GENERAL 1.0 pasa a 1.2 con las conversiones equivalentes de 1.1 y sus personas como historico', () => {
  const saved = {schema_version: '1.0', modality: 'GENERAL', motive: 'Consulta', contact_phone: '+56 9 1234 5678', region: 'Metropolitana de Santiago', commune: 'nunoa',
    course: 'EN_ATENCION', people: [{role: 'NNA', name: 'Pedro Pablo Rojas', age: '8', identity_document: '', relationship: 'hijo', location: '', birth_date: '', nationality: '', schooling: '', preferred_name: ''}]};
  const f = prepareFicha(saved);
  assert.equal(f.schema_version, '1.2');
  assert.equal(f.contact_phone, '+56912345678');
  assert.equal(f.region, '13');assert.equal(f.commune, 'Ñuñoa');
  assert.equal(f.people[0].legacy_name, 'Pedro Pablo Rojas');
  assert.equal(f.people[0].first_name, '', 'un nombre historico no se divide');
  assert.equal(f.people[0].legacy_age, '8');assert.equal(f.people[0].age_years, '', 'la edad historica no se interpreta');
  assert.equal(f.people[0].relationship_text, 'hijo');
  assert.deepEqual(fichaProblems(f, saved), {});
  assert.equal(saved.contact_phone, '+56 9 1234 5678', 'la version guardada no cambia');
});

test('una ficha NNA 1.1 conserva su texto libre como registrado antes: no se convierte en region ni en pais', () => {
  const saved = {schema_version: '1.1', modality: 'NNA', motive: 'Escucha', region: 'Biobío', commune: 'Conce', contact_phone: '9 8765 4321', nationality: 'chilena',
    presenter_name: 'Sofía', identity_type: 'RUN', identity_document: 'no recuerda', people: []};
  const f = prepareFicha(saved);
  assert.equal(f.region, 'Biobío');assert.equal(f.commune, 'Conce');assert.equal(f.contact_phone, '9 8765 4321');assert.equal(f.nationality, 'chilena');
  assert.equal(f.presenter_name, 'Sofía');assert.equal(f.presenter_first_name, '');
  assert.deepEqual(fichaProblems(f, saved), {}, 'lo historico identico no se revalida');
  // Un valor nuevo si sigue las reglas en la modalidad NNA (ficha 1.2).
  const problems = fichaProblems({...f, region: 'Concepción', contact_phone: '123', nationality: 'chileno'}, saved);
  assert.ok(problems.region && problems.contact_phone && problems.nationality);
  // Completar la region desde vacio es complemento, sin errores.
  const empty = prepareFicha({schema_version: '1.1', modality: 'NNA', motive: 'Escucha', people: []});
  assert.deepEqual(fichaProblems({...empty, region: '08', commune: 'Concepción'}, {}), {});
});

test('ficha 1.2 guardada: se reenvia sin las versiones de catalogo que deriva el servidor', () => {
  const saved = {schema_version: '1.2', modality: 'GENERAL', motive: 'Consulta', relationship_code: 'MADRE', relationship_version: 3, course: 'EN_ATENCION',
    people: [{...emptyPerson('NNA'), first_name: 'Luz', relationship_code: 'MADRE', relationship_version: 3}],
    institutions_list: [{name: 'Liceo', type_code: 'EDUCACION', type_version: 4, role: '', region_code: '', commune: ''}]};
  const payload = fichaPayload(prepareFicha(saved));
  assert.ok(!('relationship_version' in payload));
  const people = payload.people as Record<string, unknown>[], institutions = payload.institutions_list as Record<string, unknown>[];
  assert.deepEqual(Object.keys(people[0]), [...PERSON_FIELDS]);
  assert.ok(!('type_version' in institutions[0]));
  assert.equal(payload.schema_version, '1.2');
  assert.deepEqual(JSON.stringify(payload).match(/"\w*_version"/g), ['"schema_version"'], 'ninguna clave *_version salvo schema_version');
});

test('validacion de la ficha 1.2: cantidad, relacion, hechos, personas e instituciones, con su paso', () => {
  const f = {...prepareFicha({}), motive: 'Consulta'};
  assert.deepEqual(fichaProblems(f, {}, codes), {}, 'solo el motivo es indispensable');
  assert.match(fichaProblems({...f, approximate_count: 'unos 20'}).approximate_count, /entero/);
  assert.deepEqual(fichaProblems({...f, approximate_count: '20'}), {});
  assert.ok(fichaProblems({...f, relationship_code: 'MADRE'}).relationship_code, 'sin listado vigente no se admite un codigo');
  assert.deepEqual(fichaProblems({...f, relationship_code: 'MADRE'}, {}, codes), {});
  const p = fichaProblems({...f, facts_region_code: '07', facts_commune: 'Ñuñoa', people: [{...emptyPerson('NNA'), age_years: '30'}],
    institutions_list: [{name: '', type_code: '', role: '', region_code: '', commune: ''}]}, {}, codes);
  assert.deepEqual(Object.keys(p).sort(), ['facts_commune', 'institutions_list.0.name', 'people.0.age_years']);
  assert.equal(problemStep('facts_commune'), 2);assert.equal(problemStep('institutions_list.0.name'), 2);assert.equal(problemStep('people.0.age_years'), 1);
  assert.equal(problemStep('presenter_run_number'), 1);
});
