// Correcciones de la revision visual de F02 (0.8.1, gate F02b) renderizadas en servidor. (1) El texto mas chico de las
// barras de Informes es el que supone breakdownLayout. (2) La linea mensual de cada tarjeta usa el color de acento de su
// tarjeta (currentColor) y su punto final es redondo a cualquier proporcion. (3) Aviso "Desliza para ver el grafico
// completo" y difuminado del borde cuando el grafico no cabe a lo ancho: estado puro, observador que se desconecta al
// desmontarse, aviso fuera del lienzo y nada de eso en el SVG que se descarga.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {CATALOG, response} from './stats-fixture.mjs';
import {CASES, chartSvg} from './chart-export-fixture.mjs';

let vite, model, panorama, chart;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  model = await vite.ssrLoadModule('/src/features/stats/statsModel.ts');
  panorama = await vite.ssrLoadModule('/src/features/stats/Panorama.tsx');
  chart = await vite.ssrLoadModule('/src/components/charts/Chart.tsx');
});
after(async () => {await vite?.close();});

const source = path => readFileSync(fileURLToPath(new URL('../src/' + path, import.meta.url)), 'utf8');

// --- (1) Informes -----------------------------------------------------------------------------------------------------
test('(1) Informes dibuja barras horizontales; su texto más chico es CHART_MIN_FONT y a 1440 px se ve a 10 px o más', () => {
  assert.match(source('features/stats/Informes.tsx'), /<Chart kind="barras"/);
  assert.match(source('features/stats/Informes.tsx'), /<div className="stat-breakdown-box"><div className="stat-breakdown">/);
  const [, bars] = CASES[0];
  const svg = chartSvg(renderToStaticMarkup(createElement(chart.Chart, {kind: 'barras', data: bars, title: 'T', notes: [], source: 'F', file: 'x'})));
  assert.equal(Number(/viewBox="0 0 (\d+) /.exec(svg)[1]), model.CHART_VIEW_WIDTH, 'ancho del lienzo');
  const fonts = [...svg.matchAll(/font-size="([\d.]+)"/g)].map(m => Number(m[1]));
  assert.equal(Math.min(...fonts), model.CHART_MIN_FONT, 'texto más chico de las barras');
  const layout = model.breakdownLayout(1030, Math.min(...fonts));
  assert.ok(!layout.side && layout.text >= model.CHART_MIN_TEXT, `a 1440 px: ${layout.text.toFixed(2)} px`);
});

// --- (2) Panorama -----------------------------------------------------------------------------------------------------
const period = () => model.panoramaPeriod('2026-11-26').period;
const ok = (n, base) => ({status: 'ok', at: Date.parse('2026-11-26T15:30:00Z'), error: '', refresh: () => {}, data: response({rows: ['PERIODO_MES'],
  values: {PERIODO_MES: [{key: '2026-07', label: 'julio de 2026'}, {key: '2026-08', label: 'agosto de 2026'}, {key: '2026-09', label: 'septiembre de 2026'}]},
  cells: [{row: ['2026-07'], column: [], n: 1}, {row: ['2026-08'], column: [], n: n - 3}, {row: ['2026-09'], column: [], n: 2}], grand: {n, unique: n},
  comparison: {period: {type: 'PERSONALIZADO', from: '2025-07-01', to: '2025-11-25'}, cells: [],
    totals: {grand: {base_n: base, delta_n: n - base, delta_percent: (n - base) / base * 100}, rows: [], columns: []}}})});
const card = (key, s, total = null) => {
  const ind = model.INDICATORS.find(i => i.key === key);
  const q = model.indicatorQuery(ind, model.statsCatalog(CATALOG), CATALOG, period(), 'MISMO_PERIODO_ANIO_ANTERIOR');
  return renderToStaticMarkup(createElement(panorama.IndicatorCard, {ind, q, s, total, inlineFresh: false}));
};
const ACCENTS = {req: 'req', casos: 'cas', actuaciones: 'act', completos: 'ok', tardios: 'warn'};

test('(2) la línea mensual va en el color de acento de su tarjeta y llena la zona de la línea', () => {
  const total = model.readResult(ok(40, 32).data);
  for (const [key, accent] of Object.entries(ACCENTS)) {
    const html = card(key, ok(key === 'completos' ? 10 : 40, key === 'completos' ? 8 : 32), total);
    assert.match(html, new RegExp(`^<a class="stat-card stat-card-link" href="[^"]+" data-accent="${accent}"`), key + ': acento en la tarjeta');
    assert.match(html, /<span class="stat-card-trend spark"><svg viewBox="0 0 240 48" preserveAspectRatio="none" class="stat-spark"/, key + ': zona de la línea que se estira');
    const spark = /<svg[^>]*class="stat-spark"[\s\S]*?<\/svg>/.exec(html)[0];
    assert.ok(!/#[0-9a-fA-F]{6}/.test(spark.replace(/stroke="#[0-9a-fA-F]{6}" stroke-width="1" stroke-opacity="0.5"/, '')), key + ': sin colores fijos fuera de la línea base');
    assert.equal((spark.match(/stop-color="currentColor"/g) ?? []).length, 2, key + ': área degradada en el color de acento');
    assert.match(spark, /<path d="M[^"]+" fill="none" stroke="currentColor" stroke-width="2"[^>]*vector-effect="non-scaling-stroke"/, key + ': línea en el color de acento');
    assert.match(spark, /<path class="stat-spark-dot" d="M[\d.]+ [\d.]+h0" fill="none" stroke="currentColor" stroke-width="7" stroke-linecap="round" vector-effect="non-scaling-stroke"/,
      key + ': punto final redondo (trazo de largo cero con extremo redondo, sin deformarse)');
    assert.ok(!/<circle/.test(spark), key + ': sin círculo que se deforme al estirarse');
  }
  const main = card('ingresos', ok(40, 32));
  assert.match(main, /data-accent="com"/);
  assert.match(main, /<span class="stat-card-trend"><span class="stat-months"/, 'ingresos conserva sus columnas mensuales');
});

// --- (3) Aviso de desplazamiento -----------------------------------------------------------------------------------------
test('(3) chartScroll: sin desborde, al comienzo, en medio y al final (con 2 px de margen)', () => {
  const none = {overflow: false, start: true, end: true};
  assert.deepEqual(chart.chartScroll(300, 300, 0), none);
  assert.deepEqual(chart.chartScroll(302, 300, 0), none, 'dentro del margen no hay desborde');
  assert.deepEqual(chart.chartScroll(576, 307, 0), {overflow: true, start: true, end: false});
  assert.deepEqual(chart.chartScroll(576, 307, 120), {overflow: true, start: false, end: false});
  assert.deepEqual(chart.chartScroll(576, 307, 269), {overflow: true, start: false, end: true});
  assert.deepEqual(chart.chartScroll(576, 307, 268), {overflow: true, start: false, end: true}, 'redondeo del navegador al final');
  assert.deepEqual(chart.chartScroll(576, 307, 266), {overflow: true, start: false, end: false});
});

test('(3) watchScroll: escucha pasiva y observador de tamaño; al desmontarse los desconecta', () => {
  const calls = [];
  const svg = {name: 'svg'};
  const el = {scrollWidth: 576, clientWidth: 307, scrollLeft: 0, firstElementChild: svg,
    addEventListener: (type, fn, options) => calls.push(['add', type, fn, options]), removeEventListener: (type, fn) => calls.push(['remove', type, fn])};
  const observed = [];
  let disconnected = 0, resize;
  const states = [];
  const stop = chart.watchScroll(el, s => states.push(s), update => {resize = update; return {observe: t => observed.push(t), disconnect: () => disconnected++};});
  assert.deepEqual(states, [{overflow: true, start: true, end: false}], 'estado inicial');
  assert.deepEqual(observed, [el, svg], 'observa el lienzo y el gráfico');
  const [add] = calls;
  assert.equal(add[1], 'scroll');
  assert.deepEqual(add[3], {passive: true}, 'escucha pasiva');
  el.scrollLeft = 269;
  add[2]();
  assert.deepEqual(states.at(-1), {overflow: true, start: false, end: true}, 'al desplazarse');
  el.clientWidth = 600;
  resize();
  assert.deepEqual(states.at(-1), {overflow: false, start: true, end: true}, 'al cambiar de tamaño');
  stop();
  assert.equal(disconnected, 1, 'desconecta el observador');
  assert.deepEqual(calls.at(-1), ['remove', 'scroll', add[2]], 'quita la misma escucha');
  // Sin ResizeObserver (render en servidor o navegador antiguo) igual escucha y limpia.
  const bare = {...el, firstElementChild: null};
  const stop2 = chart.watchScroll(bare, () => {}, null);
  stop2();
  assert.deepEqual(calls.at(-1)[0], 'remove');
});

test('(3) ScrollHintView: aviso visible (aria-hidden) sobre el lienzo y difuminado solo en el borde que sigue', () => {
  const view = state => renderToStaticMarkup(createElement(chart.ScrollHintView, {state}, createElement('div', {className: 'stat-chart-canvas'}, 'lienzo')));
  const start = view(chart.chartScroll(576, 307, 0));
  assert.match(start, /^<p class="stat-chart-hint" aria-hidden="true"><svg[^>]*aria-hidden="true"[\s\S]*?<\/svg>Desliza para ver el gráfico completo<\/p><div class="stat-chart-frame fade-end"><div class="stat-chart-canvas">/);
  assert.equal(chart.CHART_SCROLL_HINT, 'Desliza para ver el gráfico completo');
  assert.match(view(chart.chartScroll(576, 307, 120)), /<div class="stat-chart-frame fade-start fade-end">/);
  const end = view(chart.chartScroll(576, 307, 269));
  assert.match(end, /<div class="stat-chart-frame fade-start">/, 'al final no se difumina el borde derecho: nada tapa los últimos rótulos ni cifras');
  assert.ok(end.includes(chart.CHART_SCROLL_HINT), 'el aviso sigue sobre el lienzo, fuera de él');
  const none = view(chart.chartScroll(300, 300, 0));
  assert.equal(none, '<div class="stat-chart-frame"><div class="stat-chart-canvas">lienzo</div></div>', 'sin desborde: ni aviso ni difuminado');
});

test('(3) el gráfico va dentro del marco; el aviso queda fuera del lienzo y del SVG que se descarga', () => {
  for (const [kind, data] of CASES) {
    const html = renderToStaticMarkup(createElement(chart.Chart, {kind, data, title: 'T', notes: [], source: 'F', file: 'x', tableId: 't'}));
    assert.match(html, /<div class="stat-chart-frame"><div class="stat-chart-canvas" role="group"/, kind + ': lienzo dentro del marco');
    const svg = chartSvg(html);
    assert.match(svg, /^<svg[^>]*class="chart-svg"/, kind + ': el primer SVG del lienzo es el gráfico (el que se descarga)');
    const exported = chart.exportMarkup(svg);
    assert.ok(!/Desliza|stat-chart-hint|stat-chart-frame|fade-/.test(exported), kind + ': nada del aviso en el SVG descargado');
  }
  // El componente se observa con un efecto que devuelve la limpieza de watchScroll.
  assert.match(source('components/charts/Chart.tsx'), /useEffect\(\(\) => \{\s*const el = target\.current;\s*if \(!el\) return;\s*return watchScroll\(el,/);
});
