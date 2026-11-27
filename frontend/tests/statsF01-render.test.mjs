// Rediseno visual del explorador (0.8.1, gate F01) renderizado en servidor. (a) La tarjeta del Panorama es, en toda su
// superficie, un enlace real a su consulta en Explorar, sin otro control dentro; en error no es enlace. Tono de la
// variacion (D56). (f) Graficos: marcas enfocables con nombre, barras desde 0 y el SVG descargado igual al de 0.8.0 en lo que
// importa (marcas, cifras y colores como atributos; tests/chart-export-080.json), sin clases, estilos ni animaciones.
// (g) Barridos de las cinco secciones rediseñadas: sin codigos a la vista, controles con nombre y tablas con encabezados.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {CATALOG, USER, response} from './stats-fixture.mjs';
import {CASES, chartSvg, essence} from './chart-export-fixture.mjs';

let vite, model, panorama, chart, exporter, stats, view;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  model = await vite.ssrLoadModule('/src/features/stats/statsModel.ts');
  panorama = await vite.ssrLoadModule('/src/features/stats/Panorama.tsx');
  chart = await vite.ssrLoadModule('/src/components/charts/Chart.tsx');
  exporter = await vite.ssrLoadModule('/src/components/charts/chartExport.ts');
  stats = await vite.ssrLoadModule('/src/features/stats/Stats.tsx');
  view = await vite.ssrLoadModule('/src/features/stats/ResultView.tsx');
});
after(async () => {await vite?.close();});

const cat = () => model.statsCatalog(CATALOG);
const ctx = (user = USER, catalog = CATALOG) => ({user, catalog, act: () => {}, go: () => {}, reload: () => {}, message: () => {}});
const page = (route, c = ctx()) => renderToStaticMarkup(createElement(stats.default, {ctx: c, route}));
const unescape = s => s.replace(/&quot;/g, '"').replace(/&#x27;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
const visible = html => html.replace(/<[^>]+>/g, tag => ' ' + [...tag.matchAll(/(?:aria-label|title|placeholder)="([^"]*)"/g)].map(m => m[1]).join(' ') + ' ')
  .replace(/&[a-z]+;/g, ' ').replace(/\s+/g, ' ');
const CODES = /(\b[A-Z]{2,}_[A-Z0-9_]+\b|\b(INGRESOS|DERECHO|CONTEXTO|CANAL|PENDIENTE|INFORMADO|VIGENTE|RECEPCION|FILA|COLUMNA|CASOS|ACTUACIONES|MENCIONES|COMPLETITUD|PERSONALIZADO)\b)/;
function sweep(html, where) {
  const text = visible(html);
  assert.ok(!CODES.test(text), `${where}: código a la vista «${(text.match(CODES) ?? [''])[0]}»`);
  for (const b of html.match(/<button\b[^>]*>[\s\S]*?<\/button>/g) ?? []) assert.ok(/aria-label="[^"]+"/.test(b) || visible(b).trim().length > 0, `${where}: botón sin nombre ${b.slice(0, 80)}`);
  for (const a of html.match(/<a\b[^>]*>[\s\S]*?<\/a>/g) ?? []) assert.ok(/aria-label="[^"]+"/.test(a) || visible(a).trim().length > 0, `${where}: enlace sin nombre ${a.slice(0, 80)}`);
  for (const t of html.match(/<table\b[\s\S]*?<\/table>/g) ?? []) {
    assert.ok(/<caption>/.test(t), `${where}: tabla sin título`);
    assert.ok(/<th scope="col"/.test(t), `${where}: tabla sin encabezados`);
  }
  for (const s of html.match(/<svg\b[^>]*>/g) ?? []) assert.ok(/aria-hidden="true"|role="img"|class="chart-svg"/.test(s), `${where}: svg sin papel definido ${s.slice(0, 80)}`);
  return text;
}

// --- (a) Tarjetas del Panorama ----------------------------------------------------------------------------------------------
const period = () => model.panoramaPeriod('2026-11-26').period;
const ok = (n, base) => ({status: 'ok', at: Date.parse('2026-11-26T15:30:00Z'), error: '', refresh: () => {}, data: response({rows: ['PERIODO_MES'],
  values: {PERIODO_MES: [{key: '2026-07', label: 'julio de 2026'}, {key: '2026-08', label: 'agosto de 2026'}]},
  cells: [{row: ['2026-07'], column: [], n: n - 1}, {row: ['2026-08'], column: [], n: 1}], grand: {n, unique: n},
  comparison: base == null ? null : {period: {type: 'PERSONALIZADO', from: '2025-07-01', to: '2025-11-25'}, cells: [],
    totals: {grand: {base_n: base, delta_n: n - base, delta_percent: base > 0 ? (n - base) / base * 100 : null}, rows: [], columns: []}}})});
const loading = {status: 'loading', at: 0, error: '', data: null, refresh: () => {}};
const failed = {status: 'error', at: 0, error: 'No se pudo calcular la cifra.', data: null, refresh: () => {}};
const ind = key => model.INDICATORS.find(i => i.key === key);
const card = (key, s, total = null) => {
  const i = ind(key), q = model.indicatorQuery(i, cat(), CATALOG, period(), 'MISMO_PERIODO_ANIO_ANTERIOR');
  return {q, html: renderToStaticMarkup(createElement(panorama.IndicatorCard, {ind: i, q, s, total, inlineFresh: false}))};
};

test('(a) D53: la tarjeta con datos es un único <a> con el href de su consulta, que contiene título, cifra, variación y línea mensual, sin otro control', () => {
  const total = model.readResult(ok(40, 32).data);
  for (const i of model.INDICATORS) {
    const {q, html} = card(i.key, ok(i.key === 'completos' ? 10 : 40, i.key === 'completos' ? 8 : 32), total);
    if (!q) continue;
    assert.match(html, /^<a class="stat-card[^"]*\bstat-card-link\b[^"]*" href="([^"]+)"/, i.key + ': la tarjeta completa es el enlace');
    assert.equal(unescape(/^<a [^>]*href="([^"]+)"/.exec(html)[1]), model.cardHref(q), i.key + ': href = cardHref(q)');
    assert.equal((html.match(/<a\b/g) ?? []).length, 1, i.key + ': un solo enlace');
    assert.ok(!/<(button|input|select|textarea)\b|tabindex=/.test(html), i.key + ': sin otro control interactivo dentro');
    assert.ok(html.includes(`<h3>${i.label}</h3>`), i.key + ': título');
    assert.match(html, /class="stat-card-value">[^<]+</, i.key + ': cifra');
    assert.match(html, /class="stat-change (up|down|same) tone-(favorable|desfavorable|neutro)"/, i.key + ': variación con su tono');
    assert.match(html, /class="stat-(spark|months)"/, i.key + ': línea o columnas mensuales dentro del enlace');
    const label = unescape(/aria-label="([^"]+)"/.exec(html)[1]);
    assert.match(label, new RegExp(`^${i.label}: .+\\. Abrir en Explorar\\.$`), i.key + ': nombre = indicador, cifra y destino');
    for (const id of /aria-describedby="([^"]+)"/.exec(html)[1].split(' ')) assert.ok(html.includes(`id="${id}"`), i.key + ': descripción ' + id);
    sweep(html, 'tarjeta ' + i.key);
  }
  assert.ok(visible(card('ingresos', ok(40, 32)).html).includes('Ingresos: 40. Abrir en Explorar.'));
  assert.ok(visible(card('ingresos', ok(40, 32)).html).includes('Mes a mes: julio de 2026 39; agosto de 2026 1.'), 'la serie mensual en texto para lectores de pantalla');
});

test('(a) D56: tono de la variación en la tarjeta: tardíos que suben es desfavorable, completos que suben es favorable, ingresos neutro', () => {
  const total = model.readResult(ok(40, 40).data);
  assert.match(card('tardios', ok(12, 6)).html, /stat-change up tone-desfavorable/);
  assert.match(card('tardios', ok(3, 6)).html, /stat-change down tone-favorable/);
  assert.match(card('completos', ok(30, 20), total).html, /stat-change up tone-favorable/);
  assert.match(card('completos', ok(10, 20), total).html, /stat-change down tone-desfavorable/);
  assert.match(card('ingresos', ok(40, 10)).html, /stat-change up tone-neutro/);
  assert.match(card('ingresos', ok(40, 0)).html, /tone-neutro/, 'sin base: neutro');
  // La variacion nunca solo por color: flecha y texto siguen.
  const text = visible(card('tardios', ok(12, 6)).html);
  assert.ok(text.includes('↑') && text.includes('Aumenta +6 (+100 %) respecto de 6'), text);
});

test('(a) la tarjeta en error no es enlace y conserva «Reintentar»; la que calcula sí es enlace', () => {
  const error = card('ingresos', failed).html;
  assert.ok(!/<a\b/.test(error), 'error: no es enlace');
  assert.match(error, /role="alert"/);
  assert.match(error, /<button type="button" class="sm">[\s\S]*Reintentar<\/button>/);
  const busy = card('req', loading);
  assert.match(busy.html, /^<a class="stat-card[^"]*stat-card-link[^"]*loading" href="[^"]+" aria-busy="true" aria-label="REQ constituidos: calculando\. Abrir en Explorar\."/);
  assert.equal(unescape(/href="([^"]+)"/.exec(busy.html)[1]), model.cardHref(busy.q));
  assert.ok(!/<(button|input|select)\b/.test(busy.html));
});

test('(a) Panorama: seis tarjetas stat-card, ingresos como principal, y la línea de D56 en «Cómo leer esta cifra»', () => {
  const html = page('estadisticas');
  assert.equal((html.match(/class="stat-card[ "]/g) ?? []).length, 6);
  assert.equal((html.match(/class="stat-card main /g) ?? []).length, 1, 'un indicador principal');
  assert.ok(visible(html).includes(model.TONE_NOTE));
  assert.match(html, /<nav class="stat-tabs"[^>]*>(<button[^>]*><svg[^>]*aria-hidden="true"[\s\S]*?<\/svg>[^<]+<\/button>){5}<\/nav>/, 'cinco pestañas con icono decorativo y texto');
  assert.match(html, /aria-current="page"/);
});

// --- (f) Graficos ------------------------------------------------------------------------------------------------------
const REF = JSON.parse(readFileSync(fileURLToPath(new URL('./chart-export-080.json', import.meta.url)), 'utf8'));
test('(f) cada tipo de gráfico: marcas enfocables con nombre accesible y el SVG descargado igual al de 0.8.0 en marcas, cifras y colores', () => {
  assert.equal(CASES.length, REF.length);
  CASES.forEach(([kind, data], i) => {
    const html = renderToStaticMarkup(createElement(chart.Chart, {kind, data, title: 'Título', notes: ['Nota'], source: 'Fuente: SIGER', file: 'x'}));
    const svg = chartSvg(html);
    const marks = svg.match(/<g tabindex="0" role="img" aria-label="[^"]+" class="chart-mark">/g) ?? [];
    assert.ok(marks.length > 0 && marks.length === REF[i].marks.filter(m => m !== 'Cambio de fuente').length, `${kind} ${i}: marcas enfocables con nombre`);
    const exported = exporter.standalone(chart.exportMarkup(svg), 720, 300, {title: 'Título', notes: ['Nota'], source: 'Fuente: SIGER'}).svg;
    assert.ok(!/\sclass="|<style|<animate|\sstyle="/.test(exported), `${kind} ${i}: el SVG descargado no lleva clases, estilos ni animaciones`);
    assert.deepEqual({kind, ...essence(exported)}, REF[i], `${kind} ${i}: mismas marcas, cifras y colores que 0.8.0`);
  });
});

test('(f) las barras parten de 0: horizontales desde el eje y columnas desde la base', () => {
  const [, bars] = CASES[0];
  const h = chartSvg(renderToStaticMarkup(createElement(chart.Chart, {kind: 'barras', data: bars, title: 'T', notes: [], source: 'F', file: 'x'})));
  const xs = [...h.matchAll(/<rect x="([\d.]+)"[^>]*class="chart-grow-x"/g)].map(m => Number(m[1]));
  assert.ok(xs.length === 3 && xs.every(x => x === 230), 'cada barra empieza en el eje: ' + xs);
  const [, cols] = CASES[4];
  const v = chartSvg(renderToStaticMarkup(createElement(chart.Chart, {kind: 'columnas', data: cols, title: 'T', notes: [], source: 'F', file: 'x'})));
  const rects = [...v.matchAll(/<rect x="[\d.]+" y="([\d.]+)" width="[\d.]+" height="([\d.]+)"/g)].map(m => Number(m[1]) + Number(m[2]));
  assert.ok(rects.length === 5 && rects.every(b => Math.abs(b - 220) < 1e-6), 'cada columna termina en la base: ' + rects);
});

test('(f) mutación: una clase que quede en el SVG descargado se detecta', () => {
  const [kind, data] = CASES[0];
  const svg = chartSvg(renderToStaticMarkup(createElement(chart.Chart, {kind, data, title: 'T', notes: [], source: 'F', file: 'x'})));
  assert.match(svg, /class="chart-mark"/, 'en la página las marcas llevan su clase');
  assert.ok(!/\sclass="/.test(chart.exportMarkup(svg)), 'exportMarkup la quita');
  assert.ok(!/\sstyle="|<style/.test(chart.exportMarkup(svg + '<style>.x{}</style>'.replace('<style>', '<g style="opacity:.5"/><style>'))));
});

// --- (g) Barridos de las cinco secciones -------------------------------------------------------------------------------
test('(g) las cinco secciones rediseñadas: sin códigos a la vista, controles y enlaces con nombre, tablas con encabezados', () => {
  for (const [route, where] of [['estadisticas', 'Panorama'], ['estadisticas?s=informes', 'Informes'], ['estadisticas?s=explorar', 'Explorar inicio'],
    ['estadisticas?s=cortes', 'Cortes y series'], ['estadisticas?s=calidad', 'Calidad']]) sweep(page(route), where);
  const start = page('estadisticas?s=explorar');
  assert.equal((start.match(/class="stat-guided-item"/g) ?? []).length, 4, 'cuatro preguntas como mosaicos');
  assert.match(start, /class="stat-guided-icon"><svg[^>]*aria-hidden="true"/);
  assert.ok(visible(start).includes('Consulta vacía'));
  // Explorar con resultado: banda con el total y el titulo, tipos de grafico con icono, campos con su boton en movil.
  const q = {...model.emptyQuery({type: 'CUENTA_PUBLICA', from: '2025-07-01'}), rows: ['TIPO_SOLICITUD']};
  const data = response({rows: ['TIPO_SOLICITUD'], values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'CONSULTA_ORIENTACION', label: 'Consulta u orientación'}]},
    cells: [{row: ['VULNERACION'], column: [], n: 3}, {row: ['CONSULTA_ORIENTACION'], column: [], n: 2}], grand: {n: 5, unique: 5}, rowTotals: []});
  const result = renderToStaticMarkup(createElement(view.ResultView, {cat: cat(), catalog: CATALOG, q, r: model.readResult(data), at: Date.parse('2026-11-26T15:30:00Z'), refresh: () => {}, scope: 'Toda la institución'}));
  const text = sweep(result, 'Resultado');
  assert.match(result, /class="stat-result-band"><p class="stat-total"><strong>5<\/strong> en total<\/p><div class="stat-result-what"><h3>Ingresos por Tipo de solicitud<\/h3>/);
  assert.match(result, /role="radio" aria-checked="true" class="stat-kind">Barras<svg[^>]*aria-hidden="true"/, 'tipo de gráfico con su icono');
  assert.ok(text.includes('Datos de las 12:30'));
  const explore = page(model.encodeRoute('explorar', q));
  assert.match(explore, /<button type="button" class="stat-fields-toggle" aria-expanded="false" aria-controls="stat-campos">/, 'botón «Campos» para móvil');
  assert.match(explore, /<aside id="stat-campos" class="stat-fields"/);
  assert.match(explore, /<summary><svg[^>]*aria-hidden="true"[\s\S]*?<\/svg>Ingreso <small>/, 'familia con icono');
  assert.match(explore, /class="stat-used"><svg[^>]*aria-hidden="true"[\s\S]*?<\/svg>En uso<\/span>/, 'campo en uso con icono y texto');
});
