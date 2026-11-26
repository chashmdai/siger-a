// Panel de derechos y contexto (0.8.0, D34, gate E09; DISENO 10.2) renderizado en servidor: valores con su rotulo y
// consolidado sin codigos, lector sin botones, registro cerrado con "Requiere reapertura formal", ayuda fija de los grupos
// prioritarios sin seleccion sugerida, y U10: buscar "educación" y marcar una subcategoria en tres pasos o menos
// (abrir Clasificar, escribir, marcar: con la busqueda la subcategoria ya esta a la vista, sin abrir ramas).
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

let vite, panel, model;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  panel = await vite.ssrLoadModule('/src/components/RightsPanel.tsx');
  model = await vite.ssrLoadModule('/src/lib/rightsModel.ts');
});
after(async () => {await vite?.close();});

const file = name => JSON.parse(readFileSync(fileURLToPath(new URL('../../deploy/config-ejemplo/catalogos/' + name, import.meta.url)), 'utf8'));
const published = (name, catalog, id) => ({catalog, label: file(name).label, version: {id}, items: file(name).items.map(i => ({code: i.code, label: i.label, parent_code: i.parent ?? null, selectable: i.selectable !== false, description: i.descripcion ?? null}))});
const CATALOG = {stat_catalogs: {catalogs: [published('derechos-2022-v2.json', 'DERECHO', 1), published('grupos-prioritarios-v1.json', 'GRUPO_PRIORITARIO', 2)],
  special: [{code: 'SIN_INFORMACION', label: 'Sin información'}, {code: 'NO_DECLARADO', label: 'No declarado'}, {code: 'NO_APLICA', label: 'No aplica'}]}};
const ctx = {user: {id: 5, name: 'Rosa'}, catalog: CATALOG, act: () => {}, go: () => {}, reload: () => {}, message: () => {}};
const visible = html => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
const CODES = /(\b\d\.\d{1,2}(\.\d{2})?\b|\bDERECHO\b|\bCONTEXTO\b|\bGRUPO_PRIORITARIO\b|\bSIN_INFORMACION\b|\bPENDIENTE\b|\bINFORMADO\b|\bRETROSPECTIVA\b|\bADD\b|\bREMOVE\b)/;
const education = CATALOG.stat_catalogs.catalogs[0].items.find(i => i.label === 'Derecho a la educación');
const sub = CATALOG.stat_catalogs.catalogs[0].items.find(i => i.parent_code === education.code && i.selectable);
const RIGHTS = (extra = {}) => ({revision: 2, blocked: null, can_classify: true, variables: [
  {variable: 'DERECHO', label: 'Derechos', state: 'INFORMADO', catalog_current: true, values: [{code: education.code, label: education.label}],
    history: [{id: 1, event: 'ADD', status: 'INFORMADO', code: education.code, origin: 'RETROSPECTIVA', recorded_ms: 1764000000000, author: 'Rosa', value_label: education.label, basis: null}]},
  {variable: 'CONTEXTO', label: 'Contexto', state: 'SIN_INFORMACION', catalog_current: false, values: [], history: []},
  {variable: 'GRUPO_PRIORITARIO', label: 'Grupos prioritarios', state: 'PENDIENTE', catalog_current: true, values: [], history: []},
], consolidated: {DERECHO: [{code: education.code, label: education.label, in_req: true, cases: [{id: 9, folio: 'CAS-A-000009'}]}], CONTEXTO: [], GRUPO_PRIORITARIO: []}, ...extra});

test('panel: valores, estados, consolidado e historial con rótulos; ningún código visible', () => {
  const html = renderToStaticMarkup(createElement(panel.RightsPanel, {rights: RIGHTS(), kind: 'REQ', id: 4, ctx}));
  const text = visible(html);
  for (const t of ['Derechos y contexto', 'Derecho a la educación', 'Sin información', 'Pendiente', 'Requerimiento con sus Casos', 'en el REQ', 'en CAS-A-000009', 'después de una reapertura', 'Clasificar'])
    assert.ok(text.includes(t), t);
  assert.ok(html.includes('id="derechos-req"'), 'ancla para llegar desde «Registrar actuación» y desde la primera atención');
  assert.ok(!CODES.test(text), 'sin códigos: ' + (text.match(CODES) ?? [''])[0]);
});

test('lector sin botones; registro cerrado con «Requiere reapertura formal»', () => {
  const reader = renderToStaticMarkup(createElement(panel.RightsPanel, {rights: RIGHTS({can_classify: false}), kind: 'CAS', id: 9, ctx}));
  assert.ok(!visible(reader).includes('Clasificar'));
  const closed = renderToStaticMarkup(createElement(panel.RightsPanel, {rights: RIGHTS({can_classify: false, blocked: 'El Caso está cerrado: la clasificación requiere reapertura formal.'}), kind: 'CAS', id: 9, ctx}));
  assert.ok(closed.includes('Requiere reapertura formal') && !visible(closed).includes('Clasificar '));
  assert.equal(renderToStaticMarkup(createElement(panel.RightsPanel, {rights: {}, kind: 'REQ', id: 4, ctx})), '', 'sin la clasificación del servidor (registro no legible) no se muestra nada');
});

test('grupos prioritarios: ayuda fija y ninguna selección automática ni sugerida', () => {
  const vars = model.rightsVars(RIGHTS());
  const lists = {GRUPO_PRIORITARIO: {items: CATALOG.stat_catalogs.catalogs[1].items.map(i => ({...i, parent: i.parent_code ?? ''}))}};
  const html = renderToStaticMarkup(createElement(panel.RightsEditor, {vars, lists, special: CATALOG.stat_catalogs.special, value: undefined, onChange: () => {}, initialVariable: 'GRUPO_PRIORITARIO'}));
  assert.ok(html.includes(panel.PRIORITY_HELP));
  assert.equal(panel.PRIORITY_HELP, 'Marca un grupo solo con antecedentes pertinentes. Pertenecer a un grupo no constituye por sí mismo una vulneración.');
  assert.ok(!/<input type="checkbox"[^>]*checked/.test(html), 'nada marcado de antemano');
  assert.ok(!/Propuesta|Usar propuesta|sugerid/i.test(visible(html)));
});

test('U10: buscar «educación» deja la subcategoría a la vista para marcarla (tres pasos o menos)', () => {
  const vars = model.rightsVars(RIGHTS());
  const lists = {DERECHO: {items: CATALOG.stat_catalogs.catalogs[0].items.map(i => ({...i, parent: i.parent_code ?? ''}))}};
  const editor = q => renderToStaticMarkup(createElement(panel.RightsEditor, {vars, lists, special: CATALOG.stat_catalogs.special, value: undefined, onChange: () => {}, initialVariable: 'DERECHO', initialQuery: q}));
  const html = editor('educación');
  const at = html.indexOf(sub.label);
  assert.ok(at > 0, 'la subcategoría aparece');
  const before = html.slice(0, at);
  assert.ok(/<input type="checkbox"[^>]*>\s*<span>$/.test(before.slice(before.lastIndexOf('<label'))), 'se marca con un solo clic');
  assert.ok(!html.includes('<details'), 'con búsqueda, nada queda dentro de una rama cerrada');
  assert.ok(!visible(html).includes('Vida y supervivencia'), 'solo lo pertinente a la búsqueda');
  const tree = editor('');
  assert.ok(tree.includes('<details') && visible(tree).includes('Vida y supervivencia'), 'sin búsqueda, el árbol por eje');
  assert.ok(!CODES.test(visible(html)));
});
