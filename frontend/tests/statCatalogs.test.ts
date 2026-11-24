// Catalogos estadisticos (0.8.0, D27, gate E02): logica pura de la pantalla de TI (features/admin/statCatalogsModel.ts).
// El resumen del archivo solo lo reconoce (la validacion completa es del servidor); se prueba con los archivos de
// referencia reales de deploy/config-ejemplo/catalogos. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {activationNote, canActivate, groupSummary, groupsOf, itemRows, mappingLoaded, mappingSources, normalized, previewCatalog, previewLines, previewMapping,
  shortHash, STATE_LABELS, stateTone, versionsOf} from '../src/features/admin/statCatalogsModel.ts';
import type {Version} from '../src/features/admin/statCatalogsModel.ts';

const DIR = fileURLToPath(new URL('../../deploy/config-ejemplo/catalogos/', import.meta.url));
const file = (name: string) => readFileSync(DIR + name, 'utf8');
const CATALOGS = [{catalog: 'TIPO_SOLICITUD', label: 'Tipo de solicitud'}, {catalog: 'DERECHO', label: 'Derechos'}, {catalog: 'TRAMO_EDAD', label: 'Tramos de edad'},
  {catalog: 'CONTEXTO', label: 'Contexto'}];
const row = (id: number, catalog: string, state: string, extra: Record<string, unknown> = {}) => ({id, catalog, label: 'Versión ' + id, state, source: 'Fuente', file_sha256: 'ab'.repeat(32),
  items: 5, revision: 1, loaded_ms: 1000, loaded_by: 'Tamara TI', basis: 'Fundamento', activated_ms: null, retired_ms: null, ...extra});

test('el modelo de derechos se reconoce: catálogo, ítems, seleccionables y niveles', () => {
  const p = previewCatalog(file('derechos-2022-v2.json'), CATALOGS);
  assert.ok(p.ok);
  if (!p.ok) return;
  assert.equal(p.catalogLabel, 'Derechos');
  assert.deepEqual([p.items, p.selectable, p.levels], [87, 77, 3]);
  assert.match(p.source, /versión 2\.0, 2022/);
  assert.deepEqual(previewLines(p).map(([label]) => label), ['Catálogo', 'Nombre de la versión', 'Fuente', 'Ítems']);
  assert.equal(previewLines(p)[3][1], '87 ítems, 77 seleccionables, en 3 niveles');
  // Un archivo plano no informa niveles ni seleccionables de mas.
  const flat = previewCatalog(file('tipo-solicitud-v1.json'), CATALOGS);
  assert.ok(flat.ok && previewLines(flat)[3][1] === '5 ítems');
});

test('rechazos legibles antes de enviar: plantilla, otro catálogo, correspondencia, JSON inválido y tamaño', () => {
  const template = previewCatalog(file('plantillas/tramo-edad.json'), CATALOGS);
  assert.ok(!template.ok && /no tiene ítems: es una plantilla/.test(template.problem));
  const unknown = previewCatalog(file('relacion-v1.json'), CATALOGS);
  assert.ok(!unknown.ok && /ninguno de los catálogos/.test(unknown.problem));
  const mapping = previewCatalog(JSON.stringify({format: 'siger-correspondencia-1', pairs: [{from: 'A', to: 'B'}]}), CATALOGS);
  assert.ok(!mapping.ok && /correspondencia/.test(mapping.problem));
  assert.ok(!previewCatalog('{"format":', CATALOGS).ok);
  assert.ok(!previewCatalog('[]', CATALOGS).ok);
  assert.ok(!previewCatalog(JSON.stringify({format: 'otro', catalog: 'DERECHO', items: [{}]}), CATALOGS).ok);
  const big = previewCatalog(' '.repeat(400_001) + '{}', CATALOGS);
  assert.ok(!big.ok && /tamaño/.test(big.problem));
  // Ningun texto de rechazo muestra el codigo interno del catalogo.
  for (const p of [template, unknown, mapping]) assert.ok(!p.ok && !/TRAMO_EDAD|RELACION|siger-catalogo/.test(p.problem), p.ok ? '' : p.problem);
});

test('el texto se normaliza como en el servidor (sin BOM y con LF) y el BOM no impide reconocerlo', () => {
  assert.equal(normalized('﻿{"a":1}\r\n'), '{"a":1}\n');
  assert.ok(previewCatalog('﻿' + file('tipo-solicitud-v1.json').replace(/\n/g, '\r\n'), CATALOGS).ok);
});

test('correspondencias: pares y formatos', () => {
  assert.deepEqual(previewMapping(JSON.stringify({format: 'siger-correspondencia-1', pairs: [{from: 'A', to: 'B'}, {from: 'A', to: 'C'}]})), {ok: true, pairs: 2});
  assert.ok(!previewMapping(file('tipo-solicitud-v1.json')).ok);
  assert.ok(!previewMapping(JSON.stringify({format: 'siger-correspondencia-1', pairs: []})).ok);
});

test('versiones agrupadas por catálogo, vigente, activación y correspondencias posibles', () => {
  const versions = versionsOf([row(1, 'DERECHO', 'RETIRADA', {retired_ms: 5}), row(2, 'DERECHO', 'VIGENTE', {activated_ms: 3}), row(4, 'DERECHO', 'PROPUESTA'), row(3, 'TIPO_SOLICITUD', 'PROPUESTA')]);
  const groups = groupsOf(CATALOGS, versions);
  assert.deepEqual(groups.map(g => g.catalog), ['TIPO_SOLICITUD', 'DERECHO', 'TRAMO_EDAD', 'CONTEXTO'], 'orden del servidor');
  const rights = groups[1];
  assert.equal(rights.current?.id, 2);
  assert.deepEqual(rights.versions.map(v => v.id), [4, 2, 1], 'la más nueva primero');
  assert.deepEqual(versions.filter(canActivate).map(v => v.id), [4, 3], 'solo las propuestas se activan');
  assert.equal(groupSummary(rights), 'Vigente: versión 2, «Versión 2». 1 propuesta por revisar.');
  assert.equal(groupSummary(groups[2]), 'Sin versión vigente: no se ofrece ni se exige.');
  assert.match(activationNote(rights, versions.find(v => v.id === 4) as Version), /La versión 2 \(«Versión 2»\) deja de estar vigente y queda retirada/);
  assert.match(activationNote(groups[0], versions.find(v => v.id === 3) as Version), /Hoy no hay versión vigente/);
  const target = versions.find(v => v.id === 4) as Version;
  assert.deepEqual(mappingSources(versions, target).map(v => v.id), [2, 1], 'mismo catálogo, otra versión');
  assert.deepEqual(mappingSources(versions, versions.find(v => v.id === 3) as Version), []);
  assert.equal(mappingLoaded([{from: 2, to: 4, pairs: 3, loadedMs: 0, loadedBy: ''}], 2, 4), true);
  assert.equal(mappingLoaded([{from: 2, to: 4, pairs: 3, loadedMs: 0, loadedBy: ''}], 1, 4), false);
});

test('estado con texto y tono, huella corta e ítems en árbol con rango de edad', () => {
  assert.deepEqual(Object.values(STATE_LABELS), ['Propuesta', 'Vigente', 'Retirada']);
  assert.equal(stateTone('VIGENTE'), 'chip chip-ok');assert.equal(stateTone('RETIRADA'), 'chip');
  assert.equal(shortHash('fb985d09ad3f633f68c6324b'), 'fb985d09ad3f');
  const rows = itemRows([{code: '3', label: 'Vida', level: 1, selectable: false}, {code: '3.1', label: 'Salud', level: 2, selectable: true, source_ref: 'p. 11'},
    {code: 'T0', label: 'Primera infancia', level: 1, selectable: true, range_from: 0, range_to: 5}]);
  assert.deepEqual(rows.map(r => [r.depth, r.selectable, r.range, r.ref]), [[1, false, '', ''], [2, true, '', 'p. 11'], [1, true, '0 a 5 años', '']]);
});
