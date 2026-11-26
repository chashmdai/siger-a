// Explorador, gate E14, renderizado en servidor (correcciones de presentacion de E13): eje de periodo abreviado o en dos
// lineas con el nombre completo en la ayuda (1), comparacion con un corte en barras pareadas, sin circular y con su codigo en
// "Como leer esta cifra" (2), periodo sin registros con su texto en lugar del grafico (4) y el Panorama, que muestra cada
// indicador apenas llega su respuesta, sin esperar a los demas (GO de E14, A). Barrido sin codigos.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {CATALOG, response} from './stats-fixture.mjs';

let vite, chart, model, view, panorama;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  chart = await vite.ssrLoadModule('/src/components/charts/Chart.tsx');
  model = await vite.ssrLoadModule('/src/features/stats/statsModel.ts');
  view = await vite.ssrLoadModule('/src/features/stats/ResultView.tsx');
  panorama = await vite.ssrLoadModule('/src/features/stats/Panorama.tsx');
});
after(async () => {await vite?.close();});

const cat = () => model.statsCatalog(CATALOG);
const visible = html => html.replace(/<[^>]+>/g, tag => ' ' + [...tag.matchAll(/(?:aria-label|title|placeholder)="([^"]*)"/g)].map(m => m[1]).join(' ') + ' ')
  .replace(/&[a-z]+;/g, ' ').replace(/\s+/g, ' ');
const CODES = /(\b[A-Z]{2,}_[A-Z0-9_]+\b|\b(INGRESOS|DERECHO|CONTEXTO|CANAL|PENDIENTE|INFORMADO|VIGENTE|RECEPCION|FILA|COLUMNA|CASOS|ACTUACIONES|MENCIONES|COMPLETITUD|PERSONALIZADO)\b)/;
const sweep = (html, where) => {
  const text = visible(html);
  assert.ok(!CODES.test(text), `${where}: código a la vista «${(text.match(CODES) ?? [''])[0]}»`);
  return text;
};
const render = (q, data) => renderToStaticMarkup(createElement(view.ResultView, {cat: cat(), catalog: CATALOG, q, r: model.readResult(data), at: Date.parse('2026-11-26T15:30:00Z'),
  refresh: () => {}, scope: 'Toda la institución'}));
const MONTHS = ['julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre', 'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio'];
const SHORT = ['jul', 'ago', 'sep', 'oct', 'nov', 'dic', 'ene', 'feb', 'mar', 'abr', 'may', 'jun'];

test('corrección 1: columnas y líneas rotulan los 12 meses abreviados en dos líneas, sin «…» y con el nombre completo en la ayuda', () => {
  const categories = MONTHS.map((m, i) => ({key: String(i), label: `${m} de ${i < 6 ? 2025 : 2026}`}));
  const data = {categories, series: [{key: 'n', label: 'Ingresos', values: categories.map((_, i) => i + 1)}], total: null, percent: false, format: v => v == null ? '—' : String(v)};
  for (const kind of ['columnas', 'lineas']) {
    const html = renderToStaticMarkup(createElement(chart.Chart, {kind, data, title: 'Ingresos mes a mes', notes: [], source: 'Fuente: SIGER', file: 'x'}));
    const ticks = [...html.matchAll(/<text[^>]*class="chart-tick"[^>]*><title>([^<]*)<\/title>((?:<tspan[^>]*>[^<]*<\/tspan>)+)<\/text>/g)];
    assert.equal(ticks.length, 12, `${kind}: los 12 meses rotulados`);
    ticks.forEach((t, i) => {
      assert.equal(t[1], categories[i].label, 'nombre completo en la ayuda');
      const lines = [...t[2].matchAll(/<tspan[^>]*>([^<]*)<\/tspan>/g)].map(m => m[1]);
      assert.deepEqual(lines, [SHORT[i], i < 6 ? '2025' : '2026'], `${kind}: rótulo ${i}`);
    });
    assert.ok(!/…/.test(html), `${kind}: ningún rótulo cortado`);
  }
});

test('corrección 2: comparación con un corte en barras pareadas (cifra actual y corte), sin circular y con «Comparación con el corte <código>»', () => {
  const q = {...model.emptyQuery(model.publicAccount(2025)), rows: ['TIPO_SOLICITUD'], compare: 'CORTE', compareCut: 3};
  const html = render(q, response({rows: ['TIPO_SOLICITUD'], values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'CONSULTA_ORIENTACION', label: 'Consulta u orientación'}]},
    cells: [{row: ['VULNERACION'], column: [], n: 5}, {row: ['CONSULTA_ORIENTACION'], column: [], n: 2}], grand: {n: 7, unique: 7},
    rowTotals: [{key: ['VULNERACION'], n: 5}, {key: ['CONSULTA_ORIENTACION'], n: 2}],
    comparison: {cut_id: 3, cut_code: 'CORTE-2026-001', cells: [{row: ['VULNERACION'], column: [], base_n: 3, delta_n: 2, delta_percent: 66.7},
      {row: ['CONSULTA_ORIENTACION'], column: [], base_n: 3, delta_n: -1, delta_percent: -33.3}], totals: {grand: {base_n: 6, delta_n: 1, delta_percent: 16.7}, rows: [], columns: []}}}));
  const text = sweep(html, 'corte');
  assert.ok(/role="radio" aria-checked="true" class="stat-kind">Barras pareadas</.test(html), 'barras pareadas sugeridas');
  assert.ok(!/class="stat-kind">Circular</.test(html), 'el circular no se ofrece');
  assert.ok(text.includes('Cifra actual') && text.includes('Corte CORTE-2026-001'), 'leyenda con la cifra actual y la del corte');
  assert.ok(text.includes('Vulneración de derechos, Corte CORTE-2026-001: 3'), 'marca de la cifra del corte (a mano)');
  assert.ok(text.includes('Comparación con el corte CORTE-2026-001:'), '«Cómo leer esta cifra» nombra el corte');
});

test('corrección 4: un periodo sin registros muestra «Sin registros en el periodo» en lugar del gráfico', () => {
  const q = {...model.emptyQuery(model.publicAccount(2025)), rows: ['PERIODO_MES']};
  const html = render(q, response({rows: ['PERIODO_MES'], values: {PERIODO_MES: [{key: '2025-07', label: 'julio de 2025'}]}, cells: [{row: ['2025-07'], column: [], n: 0}], grand: {n: 0, unique: 0}}));
  const text = sweep(html, 'vacío');
  assert.ok(text.includes('Sin registros en el periodo.'));
  assert.ok(!/<svg[^>]*class="chart-svg"/.test(html), 'sin gráfico');
  assert.ok(!/Descargar PNG/.test(html), 'sin descarga de un gráfico vacío');
  assert.ok(/<table/.test(html), 'la tabla sigue a la vista');
});

test('Panorama: cada indicador se muestra apenas llega su respuesta, sin esperar a los demás', () => {
  const c = cat(), period = model.panoramaPeriod('2026-11-26').period;
  const ok = n => ({status: 'ok', at: Date.parse('2026-11-26T15:30:00Z'), error: '', refresh: () => {}, data: response({rows: ['PERIODO_MES'],
    values: {PERIODO_MES: [{key: '2025-07', label: 'julio de 2025'}]}, cells: [{row: ['2025-07'], column: [], n}], grand: {n, unique: n}})});
  const loading = {status: 'loading', at: 0, error: '', data: null, refresh: () => {}};
  const card = (ind, s, total) => createElement(panorama.IndicatorCard, {key: ind.key, ind, q: model.indicatorQuery(ind, c, CATALOG, period, 'MISMO_PERIODO_ANIO_ANTERIOR'), s, total, inlineFresh: false});
  const [ingresos, req, casos] = model.INDICATORS;
  // Llegó la respuesta de REQ antes que la de ingresos y casos: REQ ya muestra su cifra; los otros, «calculando».
  const html = renderToStaticMarkup(createElement('div', null, card(ingresos, loading), card(req, ok(37)), card(casos, loading)));
  const text = sweep(html, 'Panorama');
  assert.ok(text.includes('REQ constituidos: 37. Abrir en Explorar.'), 'REQ con su cifra sin esperar a los demás');
  assert.equal((html.match(/aria-busy="true"/g) ?? []).length, 2, 'los otros dos siguen calculando');
  assert.ok(text.includes('Ingresos: calculando. Abrir en Explorar.'));
  // La caracterización completa espera además el total de ingresos (dos consultas).
  const completos = model.INDICATORS.find(i => i.key === 'completos');
  const waiting = renderToStaticMarkup(card(completos, ok(3), null));
  assert.ok(/aria-busy="true"/.test(waiting));
  const done = renderToStaticMarkup(card(completos, ok(3), model.readResult(ok(4).data)));
  assert.ok(visible(done).includes('3 de 4 ingresos (75 %)'), visible(done));
});
