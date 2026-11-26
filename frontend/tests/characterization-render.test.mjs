// Captura estadistica en el flujo (0.8.0, gate E09) renderizada en servidor (Vite ssrLoadModule + react-dom/server, sin
// dependencias nuevas): bloque "Caracterizacion del ingreso" con la propuesta rotulada y sin preseleccionar, ayuda desde la
// descripcion del item vigente (regla de "Oficio"), tarjeta de la ficha (lector sin botones, registro cerrado con
// "Requiere reapertura formal"), ingreso urgente sin campos obligatorios nuevos, campo propio del dialogo y vista movil.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

let vite, fields, card, model, intake, dialog;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  fields = await vite.ssrLoadModule('/src/components/StatFields.tsx');
  card = await vite.ssrLoadModule('/src/features/detail/Characterization.tsx');
  model = await vite.ssrLoadModule('/src/features/detail/characterizationModel.ts');
  intake = await vite.ssrLoadModule('/src/features/intake/Intake.tsx');
  dialog = await vite.ssrLoadModule('/src/components/ActionDialog.tsx');
});
after(async () => {await vite?.close();});

const item = (code, label, extra = {}) => ({code, label, parent_code: null, level: 1, selectable: true, description: null, ...extra});
const CATALOG = {regions: [], people: [], scopes: [{id: 3, code: 'UPR', name: 'Unidad de Protección', enabled: true}],
  stat_catalogs: {catalogs: [
    {catalog: 'TIPO_SOLICITUD', label: 'Tipo de solicitud', version: {id: 11}, items: [item('VULNERACION', 'Vulneración de derechos'), item('CONSULTA', 'Consulta u orientación', {description: 'Pide información u orientación, sin antecedentes de vulneración.'})]},
    {catalog: 'TIPO_REQUIRENTE', label: 'Tipo de requirente', version: {id: 12}, items: [item('NNA', 'Niño, niña o adolescente')]},
    {catalog: 'VIA_INGRESO', label: 'Vía de ingreso', version: {id: 13}, items: [item('PRESENCIAL', 'Presencial'), item('INTEGRANTE_DEFENSORIA', 'Integrante de la Defensoría', {description: 'Solo si un integrante es el origen efectivo de la comunicación.'})]},
    {catalog: 'TIPO_ACTUACION', label: 'Tipo de actuación', version: {id: 14}, items: [item('OFICIO', 'Oficio', {description: 'Se clasifica por su propósito: Oficio solo si no corresponde a otro tipo.'})]},
  ], special: [{code: 'SIN_INFORMACION', label: 'Sin información'}, {code: 'NO_DECLARADO', label: 'No declarado'}, {code: 'NO_APLICA', label: 'No aplica'}]},
  stat_completeness: {intake: ['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO'], vulnerability: [], duplicate: ['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO']}};
const ctx = {user: {id: 5, name: 'Rosa Registro', home_scope: 3, grants: [{role: 'REGISTRO', scope: 3}]}, catalog: CATALOG, act: () => {}, go: () => {}, reload: () => {}, message: () => {}};
const visible = html => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
const CODES = /\b(VULNERACION|CONSULTA|PRESENCIAL|INTEGRANTE_DEFENSORIA|SIN_INFORMACION|NO_DECLARADO|NO_APLICA|TIPO_SOLICITUD|TIPO_REQUIRENTE|VIA_INGRESO|INFORMADO|PENDIENTE|FUNCIONARIO|SISTEMA|RETROSPECTIVA|OFICIO)\b/;
const variable = (v, label, state = 'PENDIENTE', code = null, extra = {}) => ({variable: v, label, state, code, value_label: code ? 'Niño, niña o adolescente' : null, catalog_current: true, proposal: null, origin: code ? 'FUNCIONARIO' : null, history: [], ...extra});
const CHAR = (extra = {}) => ({revision: 1, blocked: null, can_classify: true, variables: [
  variable('TIPO_SOLICITUD', 'Tipo de solicitud', 'PENDIENTE', null, {proposal: {code: 'VULNERACION', label: 'Vulneración de derechos', rule: 'PROPUESTA_V1'}}),
  variable('TIPO_REQUIRENTE', 'Tipo de requirente', 'INFORMADO', 'NNA', {history: [{id: 1, event: 'SET', status: 'INFORMADO', code: 'NNA', origin: 'FUNCIONARIO', recorded_ms: 1764000000000, author: 'Rosa Registro', value_label: 'Niño, niña o adolescente'}]}),
  variable('VIA_INGRESO', 'Vía de ingreso', 'INFORMADO', 'PRESENCIAL', {value_label: 'Presencial', origin: 'SISTEMA'}),
], ...extra});
const block = (vars, value) => renderToStaticMarkup(createElement(fields.StatFields, {vars, lists: model.statLists(CATALOG), special: model.specials(CATALOG), value, onChange: () => {}}));

test('la propuesta se rotula como propuesta y no se preselecciona; «Usar propuesta» la elige', () => {
  const html = block(model.fichaVars(CHAR()), undefined);
  assert.ok(html.includes('Caracterización del ingreso'));
  assert.ok(visible(html).includes('Propuesta: Vulneración de derechos') && html.includes('Usar propuesta'));
  assert.ok(html.includes('no cuenta como informada hasta que la elijas'));
  assert.ok(!/<option[^>]*value="code:VULNERACION"[^>]*selected/.test(html), 'la propuesta no queda elegida');
  const chosen = block(model.fichaVars(CHAR()), {TIPO_SOLICITUD: {code: 'VULNERACION'}});
  assert.ok(!chosen.includes('Usar propuesta'), 'una vez elegida ya no se ofrece');
  assert.ok(!CODES.test(visible(html)), 'ningún código visible');
});

test('la definición del ítem elegido llega como ayuda (vía «Integrante de la Defensoría», actuación «Oficio»)', () => {
  const html = block(model.fichaVars(CHAR()), {VIA_INGRESO: {code: 'INTEGRANTE_DEFENSORIA'}});
  assert.ok(html.includes('Solo si un integrante es el origen efectivo de la comunicación.'));
  const consult = block(model.fichaVars(CHAR()), {TIPO_SOLICITUD: {code: 'CONSULTA'}});
  assert.ok(consult.includes('Pide información u orientación, sin antecedentes de vulneración.'));
  const action = renderToStaticMarkup(createElement(fields.CatalogSelect, {label: 'Tipo de actuación', list: model.statLists(CATALOG).TIPO_ACTUACION, value: 'OFICIO', onChange: () => {}}));
  assert.ok(action.includes('Se clasifica por su propósito: Oficio solo si no corresponde a otro tipo.'));
  assert.ok(visible(action).includes('Oficio'));
});

test('corregir un valor informado muestra el motivo obligatorio', () => {
  const html = block(model.fichaVars(CHAR()), {TIPO_REQUIRENTE: {status: 'NO_DECLARADO'}});
  assert.ok(html.includes('Motivo de la corrección'));
  assert.ok(!block(model.fichaVars(CHAR()), {TIPO_SOLICITUD: {code: 'CONSULTA'}}).includes('Motivo de la corrección'), 'completar no pide motivo');
});

test('tarjeta de la ficha: estado, origen, propuesta e historial sin códigos; botón solo con facultad', () => {
  const html = renderToStaticMarkup(createElement(card.Characterization, {c: CHAR(), comId: 7, ctx}));
  const text = visible(html);
  for (const t of ['Caracterización', 'Pendiente', 'Niño, niña o adolescente', 'Derivada del canal de ingreso', 'Propuesta: Vulneración de derechos', 'Historial de la clasificación', 'Clasificar o corregir'])
    assert.ok(text.includes(t), t);
  assert.ok(!CODES.test(text));
  const reader = renderToStaticMarkup(createElement(card.Characterization, {c: CHAR({can_classify: false}), comId: 7, ctx}));
  assert.ok(!reader.includes('Clasificar o corregir'), 'un lector no ve botones');
  const closed = renderToStaticMarkup(createElement(card.Characterization, {c: CHAR({can_classify: false, blocked: 'El REQ está cerrado: la clasificación requiere reapertura formal.'}), comId: 7, ctx, closedRecord: true}));
  assert.ok(closed.includes('Requiere reapertura formal') && !closed.includes('Clasificar o corregir'));
});

test('ingreso urgente: el bloque es opcional y no agrega campos obligatorios', () => {
  const required = html => (html.match(/class="required-mark"/g) ?? []).length;
  const withStats = renderToStaticMarkup(createElement(intake.default, {ctx, completed: () => {}}));
  const without = renderToStaticMarkup(createElement(intake.default, {ctx: {...ctx, catalog: {...CATALOG, stat_catalogs: {catalogs: [], special: []}, stat_completeness: {intake: [], vulnerability: [], duplicate: []}}}, completed: () => {}}));
  assert.ok(withStats.includes('Caracterización del ingreso') && !without.includes('Caracterización del ingreso'));
  assert.equal(required(withStats), required(without), 'ningún obligatorio nuevo');
  assert.ok(visible(withStats).includes('Opcional: puedes completarla ahora o más adelante en la ficha.'));
  assert.ok(withStats.includes('Presencial (registrado)'), 'la vía derivada del canal se muestra como registrada');
});

test('diálogo: el campo propio se dibuja dentro del diálogo existente y el ancho es opcional', () => {
  const action = {title: 'Clasificar', type: 'STAT_CLASSIFY', id: 7, revision: 1, wide: true,
    fields: [card.statField(CATALOG, model.fichaVars(CHAR()), {mustChange: true}), {key: 'reason', label: 'Fundamento', type: 'textarea'}]};
  const html = renderToStaticMarkup(createElement(dialog.ActionDialog, {action, close: () => {}, completed: () => {}}));
  assert.ok(html.includes('class="modal wide"') && html.includes('Caracterización del ingreso') && html.includes('Fundamento'));
  const plain = renderToStaticMarkup(createElement(dialog.ActionDialog, {action: {...action, wide: undefined, fields: [{key: 'reason', label: 'Fundamento', type: 'textarea'}]}, close: () => {}, completed: () => {}}));
  assert.ok(plain.includes('class="modal"'), 'los demás diálogos no cambian');
});

test('móvil: las filas de la caracterización pasan a una columna', () => {
  const css = readFileSync(fileURLToPath(new URL('../src/styles/components.css', import.meta.url)), 'utf8');
  const at = css.indexOf('Captura estadistica en el flujo');
  assert.ok(at > 0);
  assert.match(css.slice(at), /@media \(max-width: 640px\) \{\s*\.stat-list > div \{ grid-template-columns: minmax\(0, 1fr\)/);
});
