// Explorador, gate E13, renderizado en servidor (DISENO 10.3 y 10.4): secciones Cortes y series y Calidad, la revision del
// corte con su aviso textual, las descargas con la regla del ZIP, "Congelar estas cifras" solo con Gestion estadistica, la
// tabla con la variacion compacta y su nota unica, la tabla ancha con su indicador, las celdas cambiadas frente a un corte
// (tambien con columnas), las columnas verticales con la discontinuidad y el filtro de las bandejas. Barrido sin codigos.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {CATALOG, response, USER} from './stats-fixture.mjs';

let vite, stats, table, chart, model, exportBar, cuts;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  stats = await vite.ssrLoadModule('/src/features/stats/Stats.tsx');
  table = await vite.ssrLoadModule('/src/features/stats/ResultTable.tsx');
  chart = await vite.ssrLoadModule('/src/components/charts/Chart.tsx');
  model = await vite.ssrLoadModule('/src/features/stats/statsModel.ts');
  exportBar = await vite.ssrLoadModule('/src/features/stats/ExportBar.tsx');
  cuts = await vite.ssrLoadModule('/src/features/stats/CutsSeries.tsx');
});
after(async () => {await vite?.close();});

const MANAGER = {...USER, grants: [...USER.grants, {role: 'GESTION_ESTADISTICA', scope: null}], can_manage_stats: true};
const ctx = (user = USER) => ({user, catalog: CATALOG, act: () => {}, go: () => {}, reload: () => {}, message: () => {}});
const page = (route, c = ctx()) => renderToStaticMarkup(createElement(stats.default, {ctx: c, route}));
const visible = html => html.replace(/<[^>]+>/g, tag => ' ' + [...tag.matchAll(/(?:aria-label|title|placeholder)="([^"]*)"/g)].map(m => m[1]).join(' ') + ' ')
  .replace(/&[a-z]+;/g, ' ').replace(/\s+/g, ' ');
const CODES = /(\b[A-Z]{2,}_[A-Z0-9_]+\b|\b(INGRESOS|DERECHO|CONTEXTO|CANAL|PENDIENTE|INFORMADO|VIGENTE|RECEPCION|FILA|COLUMNA|CASOS|ACTUACIONES|MENCIONES|COMPLETITUD|PERSONALIZADO|SELECT|FROM|WHERE)\b)/;
function sweep(html, where) {
  const text = visible(html);
  assert.ok(!CODES.test(text), `${where}: código a la vista «${(text.match(CODES) ?? [''])[0]}»`);
  for (const b of html.match(/<button\b[^>]*>[\s\S]*?<\/button>/g) ?? []) assert.ok(/aria-label="[^"]+"/.test(b) || visible(b).trim().length > 0, `${where}: botón sin nombre`);
  for (const t of html.match(/<table\b[\s\S]*?<\/table>/g) ?? []) {
    assert.ok(/<caption>/.test(t), `${where}: tabla sin título`);
    assert.ok(/<th scope="col"/.test(t), `${where}: tabla sin encabezados`);
  }
  return text;
}

test('secciones nuevas: «Cortes y series» y «Calidad» en la barra del explorador; sin códigos', () => {
  const html = page('estadisticas?s=cortes');
  const text = sweep(html, 'Cortes y series');
  for (const t of ['Cortes y series', 'Calidad', 'Cortes estadísticos', 'Series históricas', 'congela las cifras']) assert.ok(text.includes(t), t);
  assert.ok(!text.includes('Generar un corte'), 'sin Gestión estadística no se genera');
  assert.ok(!text.includes('Importar una serie'));
  const manager = sweep(page('estadisticas?s=cortes', ctx(MANAGER)), 'Cortes, gestión');
  assert.ok(manager.includes('Generar un corte') && manager.includes('Importar una serie'));
  const quality = sweep(page('estadisticas?s=calidad'), 'Calidad');
  for (const t of ['Completitud por variable', 'por ámbito', 'mes a mes', 'Cómo leer esta cifra', 'La región del requirente no se clasifica']) assert.ok(quality.includes(t), t);
});

test('«Congelar estas cifras» abre el asistente prellenado: cuatro pasos y qué congela el corte', () => {
  const q = {...model.emptyQuery({type: 'CUENTA_PUBLICA', from: '2025-07-01', to: '2026-06-30'}), rows: ['TIPO_SOLICITUD']};
  const html = page(model.encodeRoute('cortes', q, {nuevo: '1'}), ctx(MANAGER));
  const text = sweep(html, 'Asistente de corte');
  for (const t of ['Generar un corte estadístico', 'Alcance', 'Periodo', 'Título y fundamento', 'Revisión', 'Toda la institución',
    'Los filtros, filas y columnas de la consulta no se guardan: se aplican al consultarlo']) assert.ok(text.includes(t), t);
  assert.ok(html.includes('aria-current="step"'));
  // Sin Gestion estadistica la direccion no abre el asistente.
  assert.ok(!visible(page(model.encodeRoute('cortes', q, {nuevo: '1'}))).includes('Generar un corte estadístico'));
});

test('revisión del corte: el aviso textual de la detención', () => {
  const draft = {scope: null, period: {type: 'CUENTA_PUBLICA', from: '2025-07-01'}, title: 'Corte de la Cuenta Pública', reason: 'Informe anual'};
  const text = sweep(renderToStaticMarkup(createElement(cuts.CutReview, {draft, scopeLabel: 'Toda la institución'})), 'Revisión');
  assert.ok(text.includes('Generar el corte detiene brevemente las operaciones de todos. Hazlo fuera del horario de atención'));
  assert.ok(text.includes('Cuenta Pública 2025-2026') && text.includes('Informe anual'));
});

test('descargas: ZIP deshabilitado con su explicación si la medida no es de requerimientos; sin Exportación se explica; congelar solo con Gestión', () => {
  const q = {...model.emptyQuery({type: 'MES', from: '2026-01-01'}), measure: 'CASOS'};
  const body = model.requestBody(q);
  const off = renderToStaticMarkup(createElement(exportBar.ExportBar, {ctx: ctx(), q, body, title: 'Casos'}));
  assert.match(off, /<button[^>]*disabled=""[^>]*>[\s\S]*?Paquete ZIP/);
  assert.ok(visible(off).includes('El paquete ZIP se arma solo para medidas de requerimientos'));
  const on = renderToStaticMarkup(createElement(exportBar.ExportBar, {ctx: ctx(), q: {...q, measure: 'INGRESOS'}, body, title: 'Ingresos'}));
  assert.doesNotMatch(on, /disabled=""/);
  assert.ok(visible(on).includes('microdatos'));
  const none = renderToStaticMarkup(createElement(exportBar.ExportBar, {ctx: ctx({...USER, can_export: false}), q, body, title: 'Casos'}));
  assert.ok(visible(none).includes('necesitas la función Exportación'));
  assert.equal(renderToStaticMarkup(createElement(exportBar.FreezeButton, {ctx: ctx(), q})), '');
  assert.ok(visible(renderToStaticMarkup(createElement(exportBar.FreezeButton, {ctx: ctx(MANAGER), q}))).includes('Congelar estas cifras'));
  assert.equal(renderToStaticMarkup(createElement(exportBar.FreezeButton, {ctx: ctx(MANAGER), q: {...q, cut: 3}})), '', 'nunca sobre un corte');
});

const compared = () => model.readResult(response({rows: ['PERIODO_MES'], values: {PERIODO_MES: [{key: '2026-01', label: 'enero de 2026'}, {key: '2026-02', label: 'febrero de 2026'}, {key: '2026-03', label: 'marzo de 2026'}]},
  cells: [{row: ['2026-01'], column: [], n: 2}, {row: ['2026-02'], column: [], n: 0}, {row: ['2026-03'], column: [], n: 11}], grand: {n: 13, unique: 13},
  rowTotals: [{key: ['2026-01'], n: 2}, {key: ['2026-02'], n: 0}, {key: ['2026-03'], n: 11}],
  comparison: {period: {type: 'PERSONALIZADO', from: '2025-01-01', to: '2025-03-31'}, cells: [{row: ['2026-01'], column: [], base_n: 0, delta_n: 2, delta_percent: null},
    {row: ['2026-02'], column: [], base_n: 0, delta_n: 0, delta_percent: null}, {row: ['2026-03'], column: [], base_n: 13, delta_n: -2, delta_percent: -15.38}],
    totals: {grand: {base_n: 13, delta_n: 0, delta_percent: 0}, rows: [], columns: []}}}));

test('ajuste 5: variación compacta con flecha y cifra; «Sin base de comparación» una sola vez al pie (desvío: sin la columna «Diferencia»)', () => {
  const r = compared();
  const html = renderToStaticMarkup(createElement(table.ResultTable, {id: 't', caption: 'Ingresos por mes', table: model.tableModel(r, ['PERIODO_MES'], []), metric: 'n', multiple: false, comparison: true}));
  const text = sweep(html, 'Variación');
  assert.ok(text.includes('+2 *'), 'sin base: cifra y marca');
  assert.ok(text.includes('−2 (−15,4 %)'));
  assert.ok(text.includes('='));
  assert.equal((text.match(/Sin base de comparación/g) ?? []).length, 1, 'una sola vez');
  assert.ok(!text.includes('Diferencia'));
  assert.ok(html.includes('aria-label="Disminuye −2 (−15,4 %)"'));
});

test('ajuste 4: la tabla ancha indica el desplazamiento y fija la primera columna', () => {
  const values = {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}], CANAL: ['A', 'B', 'C', 'D'].map(k => ({key: k, label: 'Canal ' + k}))};
  const r = model.readResult(response({rows: ['TIPO_SOLICITUD'], columns: ['CANAL'], values, cells: values.CANAL.map(c => ({row: ['VULNERACION'], column: [c.key], n: 1})), grand: {n: 4, unique: 4},
    rowTotals: [{key: ['VULNERACION'], n: 4}], colTotals: values.CANAL.map(c => ({key: [c.key], n: 1}))}));
  const html = renderToStaticMarkup(createElement(table.ResultTable, {id: 'w', caption: 'Ancha', table: model.tableModel(r, ['TIPO_SOLICITUD'], ['CANAL']), metric: 'n', multiple: false, comparison: false}));
  assert.ok(html.includes('stat-table-wrap wide'));
  assert.ok(visible(html).includes(table.SCROLL_HINT));
  assert.ok(html.includes('role="region"') && html.includes('sticky-first'));
  const narrow = renderToStaticMarkup(createElement(table.ResultTable, {id: 'n', caption: 'Angosta', table: model.tableModel(compared(), ['PERIODO_MES'], []), metric: 'n', multiple: false, comparison: false}));
  assert.ok(!narrow.includes(' wide'));
  const css = readFileSync(fileURLToPath(new URL('../src/styles/screens.css', import.meta.url)), 'utf8');
  assert.match(css, /\.stat-table-wrap\.wide \.table-scroll \{[\s\S]*?local,\s*radial-gradient\([^;]*scroll/, 'sombra de desplazamiento');
  assert.match(css, /\.stat-table thead th:first-child, \.stat-table tbody th \{ position: sticky; left: 0/, 'primera columna fija');
  // Ajuste 2, reemplazado en 0.8.1 por F02b (observacion 1 de F02, opcion A del GO): la tabla y el grafico de Informes van
  // lado a lado solo si el grafico cabe a su tamano natural; lo decide el ancho del desglose (@container), no la ventana.
  assert.match(css, /@container desglose \(min-width: 1094px\) \{\s*\.stat-breakdown \{ grid-template-columns: minmax\(340px, 1fr\) 738px; \}/);
});

test('comparación con un corte: cada celda cambiada dice «antes» y la diferencia, también con columnas, y se resume', () => {
  const r = model.readResult(response({rows: ['TIPO_SOLICITUD'], columns: ['CANAL'],
    values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}], CANAL: [{key: 'CORREO', label: 'Correo'}, {key: 'TELEFONO', label: 'Teléfono'}]},
    cells: [{row: ['VULNERACION'], column: ['CORREO'], n: 5}, {row: ['VULNERACION'], column: ['TELEFONO'], n: 1}], grand: {n: 6, unique: 6},
    rowTotals: [{key: ['VULNERACION'], n: 6}], colTotals: [{key: ['CORREO'], n: 5}, {key: ['TELEFONO'], n: 1}],
    comparison: {cut_id: 3, cut_code: 'CORTE-2026-001', cells: [{row: ['VULNERACION'], column: ['CORREO'], base_n: 3, delta_n: 2, delta_percent: 66.7},
      {row: ['VULNERACION'], column: ['TELEFONO'], base_n: 1, delta_n: 0, delta_percent: 0}], totals: {grand: {base_n: 4, delta_n: 2, delta_percent: 50}, rows: [], columns: []}}}));
  const html = renderToStaticMarkup(createElement(table.ResultTable, {id: 'c', caption: 'Frente al corte', table: model.tableModel(r, ['TIPO_SOLICITUD'], ['CANAL']), metric: 'n', multiple: false,
    comparison: true, cutCompare: true}));
  const text = sweep(html, 'Corte');
  assert.ok(text.includes('1 celda cambió respecto del corte.'));
  assert.ok(text.includes('antes 3, +2'));
  assert.equal((html.match(/class="num changed"/g) ?? []).length, 1, 'solo la celda que cambió');
});

test('ajuste 3 y serie histórica: columnas verticales desde 0 y la discontinuidad con texto', () => {
  const data = {categories: [{key: 'a', label: 'enero'}, {key: 'b', label: 'febrero'}, {key: 'c', label: 'marzo'}],
    series: [{key: 's', label: 'Serie histórica', values: [3, 4, null]}, {key: 'g', label: 'SIGER', values: [null, 0, 5], base: true}], total: null, percent: false,
    format: v => v == null ? '—' : String(v), divider: {after: 1, label: 'Discontinuidad: la serie llega hasta febrero'}};
  for (const kind of ['columnas', 'lineas']) {
    const html = renderToStaticMarkup(createElement(chart.Chart, {kind, data, title: 'Serie y SIGER', notes: [], source: 'Fuente: SIGER', file: 'x'}));
    assert.ok(visible(html).includes('Discontinuidad: la serie llega hasta febrero'), kind + ': texto a la vista');
    assert.match(html, /<p class="stat-chart-divider">[\s\S]*?Discontinuidad: la serie llega hasta febrero<\/p>/, kind + ': el texto también fuera del dibujo');
    assert.ok(html.includes('stroke-dasharray'), kind + ': línea punteada');
    if (kind === 'columnas') assert.ok(/<rect x="[\d.]+" y="[\d.]+" width="[\d.]+" height="[\d.]+"/.test(html), 'columnas verticales');
  }
});

test('bandejas: el filtro «Con datos estadísticos pendientes» en Comunicaciones, Solicitudes y Requerimientos', () => {
  const src = readFileSync(fileURLToPath(new URL('../src/features/worklist/WorkspaceList.tsx', import.meta.url)), 'utf8');
  for (const kind of ['com', 'sol', 'req']) assert.match(src, new RegExp(`\\n  ${kind}: \\[[^\\n]*\\['stat_pending', 'Con datos estadísticos pendientes'\\]`), kind);
  assert.doesNotMatch(src, /cas: \[[^\n]*stat_pending/);
});
