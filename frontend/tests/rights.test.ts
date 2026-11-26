// Derechos, contexto, caracterizacion, rol del Estado y grupos prioritarios (0.8.0, D34-D35, gate E09; lib/rightsModel.ts):
// busqueda sin tildes con ancestros y descendientes, seleccion como diferencia con lo registrado, estados especiales solo
// sin valores, motivo al quitar o reemplazar un estado, solo variables con listado vigente y consolidado del REQ.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {children, consolidatedRows, needsReason, normalize, rightsPayload, rightsPending, rightsProblems, rightsVars, searchCodes, selection, toggle, withStatus} from '../src/lib/rightsModel.ts';
import {statLists} from '../src/features/detail/characterizationModel.ts';

const derechos = JSON.parse(readFileSync(fileURLToPath(new URL('../../deploy/config-ejemplo/catalogos/derechos-2022-v2.json', import.meta.url)), 'utf8'));
const ITEMS = statLists({stat_catalogs: {catalogs: [{catalog: 'DERECHO', label: 'Derechos', version: {id: 1},
  items: derechos.items.map((i: Record<string, unknown>) => ({code: i.code, label: i.label, parent_code: i.parent ?? null, selectable: i.selectable !== false, description: i.descripcion ?? null}))}]}}).DERECHO.items;
const RIGHTS = {revision: 3, blocked: null, can_classify: true, variables: [
  {variable: 'DERECHO', label: 'Derechos', state: 'INFORMADO', catalog_current: true, values: [{code: '3.1', label: 'Derecho 3.1'}]},
  {variable: 'CONTEXTO', label: 'Contexto', state: 'SIN_INFORMACION', catalog_current: true, values: []},
  {variable: 'GRUPO_PRIORITARIO', label: 'Grupos prioritarios', state: 'PENDIENTE', catalog_current: false, values: []},
], consolidated: {DERECHO: [{code: '3.1', label: 'Derecho 3.1', in_req: true, cases: [{id: 9, folio: 'CAS-A-000009'}]}], CONTEXTO: [], GRUPO_PRIORITARIO: []}};

test('búsqueda sin tildes: «educacion» encuentra el derecho, sus subcategorías y su eje', () => {
  assert.equal(normalize('  Educación  Básica '), 'educacion basica');
  const found = searchCodes(ITEMS, 'educacion')!;
  const education = ITEMS.find(i => i.label === 'Derecho a la educación')!;
  assert.ok(found.has(education.code) && found.has(education.parent), 'el derecho y su eje');
  const subs = ITEMS.filter(i => i.parent === education.code);
  assert.ok(subs.length > 0 && subs.every(s => found.has(s.code)), 'buscar un derecho muestra sus subcategorías');
  assert.deepEqual(searchCodes(ITEMS, 'educación'), found, 'con o sin tilde, lo mismo');
  const byName = searchCodes(ITEMS, 'derecho a la educacion')!;
  assert.ok(subs.every(s => byName.has(s.code)) && subs.some(s => !normalize(s.label).includes('derecho')), 'las subcategorías llegan por descendencia, aunque no contengan las palabras');
  assert.equal(searchCodes(ITEMS, '  '), null);
  assert.equal(searchCodes(ITEMS, 'zzzz')!.size, 0);
  assert.ok(children(ITEMS).get('')!.every(i => !i.selectable), 'los ejes no se seleccionan');
});

test('marcar y quitar se envían como diferencia con lo registrado, nunca como el conjunto completo', () => {
  const vars = rightsVars(RIGHTS);
  const sub = ITEMS.find(i => i.parent === '3.1' && i.selectable)!;
  let change = toggle(ITEMS, ['3.1'], undefined, sub.code, true);
  assert.deepEqual(change, {add: [sub.code], remove: []});
  assert.deepEqual(rightsPayload(vars, {DERECHO: change}), {DERECHO: {add: [sub.code], remove: []}});
  change = toggle(ITEMS, ['3.1'], change, '3.1', false);
  assert.deepEqual(rightsPayload(vars, {DERECHO: change}), {DERECHO: {add: [sub.code], remove: ['3.1']}});
  assert.deepEqual([...selection(['3.1'], change)], [sub.code]);
  const axis = ITEMS.find(i => !i.selectable)!;
  assert.deepEqual(toggle(ITEMS, [], undefined, axis.code, true), {add: [], remove: []}, 'un eje no se marca');
  assert.equal(rightsPayload(vars, {DERECHO: toggle(ITEMS, ['3.1'], undefined, '3.1', true)}), undefined, 'sin cambios no viaja');
});

test('quitar o reemplazar un estado exige motivo; agregar no', () => {
  const vars = rightsVars(RIGHTS);
  const add = {DERECHO: toggle(ITEMS, ['3.1'], undefined, '3.2', true)};
  assert.equal(needsReason(vars, add), false);
  assert.deepEqual(rightsProblems(vars, add, ''), []);
  const remove = {DERECHO: toggle(ITEMS, ['3.1'], undefined, '3.1', false)};
  assert.equal(needsReason(vars, remove), true);
  assert.deepEqual(rightsProblems(vars, remove, ''), ['Indica el motivo de la corrección.']);
  assert.deepEqual(rightsProblems(vars, remove, 'Error de registro'), []);
  const status = {CONTEXTO: withStatus([], 'NO_APLICA')};
  assert.equal(needsReason(vars, status), true, 'reemplazar «Sin información» es una corrección');
  assert.deepEqual(rightsProblems(vars, undefined, ''), ['Marca o quita al menos un valor.']);
});

test('un estado especial solo sin valores: elegirlo quita lo marcado', () => {
  const vars = rightsVars(RIGHTS);
  assert.deepEqual(withStatus(['3.1'], 'SIN_INFORMACION'), {add: [], remove: ['3.1'], status: 'SIN_INFORMACION'});
  assert.deepEqual(withStatus(['3.1'], ''), {add: [], remove: []}, 'volver a «elegir» no borra lo registrado');
  assert.deepEqual(rightsProblems(vars, {DERECHO: {add: ['3.2'], remove: [], status: 'SIN_INFORMACION'}}, 'x'), ['Derechos: un estado como «Sin información» se registra solo sin valores.']);
});

test('solo variables con listado vigente; consolidado y pendientes de la vulneración', () => {
  const vars = rightsVars(RIGHTS);
  assert.equal(rightsPayload(vars, {GRUPO_PRIORITARIO: {add: ['8.01'], remove: []}}), undefined, 'sin listado vigente no se envía');
  assert.deepEqual(consolidatedRows(RIGHTS, 'DERECHO'), [{code: '3.1', label: 'Derecho 3.1', in_req: true, cases: ['CAS-A-000009']}]);
  assert.deepEqual(rightsPending(['DERECHO', 'CONTEXTO'], RIGHTS), [], 'informado o con estado especial: resuelto');
  const empty = {variables: [{variable: 'DERECHO', state: 'PENDIENTE', values: []}], consolidated: {DERECHO: []}};
  assert.deepEqual(rightsPending(['DERECHO'], empty), ['DERECHO']);
  assert.deepEqual(rightsPending(['DERECHO'], {...empty, consolidated: {DERECHO: [{code: '3.1', in_req: false, cases: [{folio: 'CAS-1'}]}]}}), [], 'un Caso legible con el valor lo resuelve');
  assert.deepEqual(rightsPending([], empty), [], 'sin catálogo vigente exigible, nada');
});
