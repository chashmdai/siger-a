// Explorador estadistico (0.8.0, gate E12; DISENO 10.3) renderizado en servidor: cada seccion (Panorama, Informes y
// Explorar), el resultado con grafico, tabla y "Como leer esta cifra", la jerarquia de derechos y cada tipo de grafico.
// Barridos: ningun codigo ni SQL a la vista (texto, aria-label y title), botones con nombre accesible, tablas con
// encabezados y titulo, graficos con nombre y alternativa textual, barras desde 0 y las marcas de valores multiples y
// "no aditiva". Tambien los recorridos U de E12 (numero de acciones desde la entrada al explorador).
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {CATALOG, DERECHOS, response, USER} from './stats-fixture.mjs';

let vite, stats, view, chart, model, explore;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  stats = await vite.ssrLoadModule('/src/features/stats/Stats.tsx');
  view = await vite.ssrLoadModule('/src/features/stats/ResultView.tsx');
  chart = await vite.ssrLoadModule('/src/components/charts/Chart.tsx');
  model = await vite.ssrLoadModule('/src/features/stats/statsModel.ts');
  explore = await vite.ssrLoadModule('/src/features/stats/Explore.tsx');
});
after(async () => {await vite?.close();});

const ctx = (user = USER, catalog = CATALOG) => ({user, catalog, act: () => {}, go: () => {}, reload: () => {}, message: () => {}});
const page = (route, c = ctx()) => renderToStaticMarkup(createElement(stats.default, {ctx: c, route}));
// Texto visible y el que leen los lectores de pantalla: contenido, <title> de las marcas y los atributos aria-label, title y placeholder.
const visible = html => html.replace(/<[^>]+>/g, tag => ' ' + [...tag.matchAll(/(?:aria-label|title|placeholder)="([^"]*)"/g)].map(m => m[1]).join(' ') + ' ')
  .replace(/&[a-z]+;/g, ' ').replace(/\s+/g, ' ');
const CODES = /(\b[A-Z]{2,}_[A-Z0-9_]+\b|\b(INGRESOS|DERECHO|CONTEXTO|CANAL|PENDIENTE|INFORMADO|VIGENTE|RECEPCION|FILA|COLUMNA|CASOS|ACTUACIONES|MENCIONES|COMPLETITUD|PERSONALIZADO|SELECT|FROM|WHERE)\b|\b\d\.\d{1,2}(\.\d{2})?\b)/;
function sweep(html, where) {
  const text = visible(html);
  assert.ok(!CODES.test(text), `${where}: código a la vista «${(text.match(CODES) ?? [''])[0]}»`);
  for (const b of html.match(/<button\b[^>]*>[\s\S]*?<\/button>/g) ?? []) {
    const name = /aria-label="[^"]+"/.test(b) || visible(b).trim().length > 0;
    assert.ok(name, `${where}: botón sin nombre accesible ${b.slice(0, 80)}`);
  }
  for (const t of html.match(/<table\b[\s\S]*?<\/table>/g) ?? []) {
    assert.ok(/<caption>/.test(t), `${where}: tabla sin título`);
    assert.ok(/<th scope="col"/.test(t), `${where}: tabla sin encabezados`);
  }
  for (const s of html.match(/<figure\b[\s\S]*?<\/figure>/g) ?? []) assert.ok(/role="group" aria-label="[^"]+"/.test(s), `${where}: gráfico sin nombre`);
  return text;
}

test('Panorama: seis indicadores con su periodo, comparación, «Cómo leer» y fuente; sin códigos', () => {
  const html = page('estadisticas');
  const text = sweep(html, 'Panorama');
  for (const t of ['Explorador estadístico', 'Panorama', 'Informes', 'Explorar', 'Ingresos', 'REQ constituidos', 'Casos', 'Actuaciones', 'Ingresos con caracterización completa',
    'Registros tardíos', 'Mismo periodo del año anterior', 'Cómo leer esta cifra', 'dos consultas', 'puntos porcentuales', 'Fuente: SIGER, Defensoría de la Niñez', 'Actualizar'])
    assert.ok(text.includes(t), t);
  assert.ok(html.includes('aria-current="page"'));
  assert.equal((html.match(/class="stat-card[ "]/g) ?? []).length, 6, 'seis indicadores (mientras cargan, con esqueleto)');
});

test('Informes: Figura 1 de la Cuenta Pública con sus cuatro desgloses; sin Exportación se explica cómo descargar', () => {
  const html = page('estadisticas?s=informes');
  const text = sweep(html, 'Informes');
  for (const t of ['Figura 1: ingresos de la Cuenta Pública', 'Por tipo de solicitud', 'Por tipo de requirente', 'Por vía de ingreso', 'Por adscripción institucional',
    'Cada desglose cuenta los mismos ingresos una vez', 'Pendiente de asignación']) assert.ok(text.includes(t), t);
  assert.ok(!text.includes('función Exportación'), 'con Exportación no hay aviso');
  const reader = sweep(page('estadisticas?s=informes', ctx({...USER, can_export: false})), 'Informes sin exportación');
  assert.ok(reader.includes('necesitas la función Exportación'));
});

test('Explorar: preguntas guiadas al entrar; con una consulta, campos por familia con sus marcas, ejes, cifra y opciones', () => {
  const start = sweep(page('estadisticas?s=explorar'), 'Explorar inicio');
  for (const t of ['¿Qué quieres saber?', '¿Cuántos ingresos hubo?', 'Por vía de ingreso', 'Comparar con el periodo anterior', 'Figura 1 de la Cuenta Pública', 'Vistas guardadas'])
    assert.ok(start.includes(t), t);
  const q = {...model.emptyQuery(model.publicAccount(2025)), rows: ['TIPO_SOLICITUD'], columns: ['REGION_NNA'], filters: {REGION_REQUIRENTE: ['07']}};
  const html = page(model.encodeRoute('explorar', q));
  const text = sweep(html, 'Explorar');
  for (const t of ['Buscar un campo', 'Periodo', 'Ingreso', 'Territorio', 'Derechos', 'Personas', 'Calidad', 'valores múltiples', 'no aditiva', 'Filas', 'Columnas', 'Filtros',
    'Tipo de solicitud', 'Región del NNA afectado (no aditiva)', 'Región del requirente: Maule', 'Qué contar', 'Cifra', '% por fila', 'Comparar con', 'Opciones avanzadas', 'Fecha de análisis',
    'Mostrar aparte las propuestas', 'Guardar vista', 'Agregar Tipo de solicitud a filas', 'Pasar Tipo de solicitud a columnas', 'Quitar Región del NNA afectado (no aditiva)'])
    assert.ok(text.includes(t), t);
  assert.ok(html.includes('draggable="true"'), 'los campos se arrastran');
  // Cada campo multiple del selector lleva su marca (no basta con la de los ejes).
  for (const d of CATALOG.stats.dimensions.filter(x => x.cardinality === 'MULTIPLE' && x.type !== 'PERIODO')) {
    const field = new RegExp('<strong>' + d.label + '</strong><span class="stat-badge"[^>]*>valores múltiples</span>');
    assert.ok(field.test(html), 'marca en el campo ' + d.label);
  }
  assert.ok(!/<strong>Tipo de solicitud<\/strong><span class="stat-badge"/.test(html), 'un campo de valor único no lleva la marca');
  assert.ok(text.includes('Con valores múltiples, contar') && text.includes('Requerimientos con el valor') && text.includes('Menciones'));
});

test('motor deshabilitado o sin Reportes: mensaje llano, sin consultas', () => {
  const off = sweep(page('estadisticas', ctx(USER, {...CATALOG, stats: {...CATALOG.stats, enabled: false}})), 'deshabilitado');
  assert.ok(off.includes('El explorador estadístico está deshabilitado') && off.includes('Avisa al equipo técnico'));
  const none = sweep(page('estadisticas', ctx(USER, {...CATALOG, stats: undefined})), 'sin stats');
  assert.ok(none.includes('Requiere la función Reportes'));
});

const cat = () => model.statsCatalog(CATALOG);
const CROSS = () => response({rows: ['TIPO_SOLICITUD'], columns: ['REGION_NNA'], percent: 'FILA',
  values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'CONSULTA_ORIENTACION', label: 'Consulta u orientación'}],
    REGION_NNA: [{key: '05', label: 'Valparaíso'}, {key: '07', label: 'Maule'}]},
  cells: [{row: ['VULNERACION'], column: ['05'], n: 2, unique: 2, percent: 66.67}, {row: ['VULNERACION'], column: ['07'], n: 2, unique: 2, percent: 66.67},
    {row: ['CONSULTA_ORIENTACION'], column: ['07'], n: 1, unique: 1, percent: 100}],
  grand: {n: 4, unique: 4}, rowTotals: [{key: ['VULNERACION'], n: 3, unique: 3}, {key: ['CONSULTA_ORIENTACION'], n: 1, unique: 1}],
  colTotals: [{key: ['05'], n: 2, unique: 2}, {key: ['07'], n: 3, unique: 3}], warnings: [{code: 'PORCENTAJES_MULTIVALOR', dimensions: ['REGION_NNA']}]});
const render = (q, data, extra = {}) => renderToStaticMarkup(createElement(view.ResultView, {cat: cat(), catalog: CATALOG, q, r: model.readResult(data), at: Date.parse('2026-11-26T15:30:00Z'),
  refresh: () => {}, scope: 'Toda la institución', ...extra}));

test('resultado de un cruce: barras agrupadas, tabla con márgenes del servidor, «no aditiva» en tabla, gráfico y «Cómo leer»', () => {
  const q = {...model.emptyQuery(model.publicAccount(2025)), rows: ['TIPO_SOLICITUD'], columns: ['REGION_NNA'], percent: 'FILA'};
  const html = render(q, CROSS());
  const text = sweep(html, 'cruce');
  for (const t of ['Datos de las 12:30', 'Actualizar', 'Barras agrupadas', 'Cómo leer esta cifra', 'Total único de requerimientos (cantidad)', '66,7 %', '0 %', 'Descargar PNG', 'Descargar SVG',
    'Fuente: SIGER, Defensoría de la Niñez', 'pueden sumar más de 100 %', 'Un requerimiento puede tener varios valores'])
    assert.ok(text.includes(t), t);
  const caption = /<caption>([^<]*)<\/caption>/.exec(html)[1];
  assert.ok(caption.includes('no aditiva'), 'tabla');
  assert.ok(/<figcaption><strong[^>]*>[^<]*no aditiva/.test(html), 'gráfico');
  assert.ok(/stat-howto[\s\S]*La región de los NNA es no aditiva/.test(html), 'Cómo leer');
  // Margenes del servidor: 3 para Vulneracion (no 2 + 2) y 4 en total.
  assert.ok(/<td class="num total">3<\/td>/.test(html) && /<tr class="total"><th scope="row">[^<]*<\/th><td class="num">2<\/td><td class="num">3<\/td><td class="num">4<\/td>/.test(html));
  assert.ok(text.includes('Circular no disponible: Solo sin campos en columnas'), 'el circular no se ofrece y se explica por qué');
  assert.ok(!/role="radio"[^>]*>Circular</.test(html));
});

test('una dimensión de valor único: barras desde 0, el circular disponible (6 o menos), tabla como alternativa y pie de fuente en el SVG', () => {
  const q = {...model.emptyQuery(model.publicAccount(2025)), rows: ['TIPO_SOLICITUD']};
  const data = response({rows: ['TIPO_SOLICITUD'], values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'PROMOCION', label: 'Promoción'}, {key: 'PENDIENTE', label: 'Pendiente'}]},
    cells: [{row: ['VULNERACION'], column: [], n: 1234, unique: 1234}, {row: ['PROMOCION'], column: [], n: 2, unique: 2}, {row: ['PENDIENTE'], column: [], n: 1, unique: 1}],
    grand: {n: 1237, unique: 1237}, rowTotals: []});
  const html = render(q, data);
  const text = sweep(html, 'una dimensión');
  assert.ok(text.includes('1.234') && text.includes('1.237'));
  assert.ok(/role="radio"[^>]*>Circular</.test(html), 'circular ofrecido');
  const rects = [...html.matchAll(/<rect x="([\d.]+)" y="[\d.]+" width="([\d.]+)" height="16"/g)];
  assert.equal(rects.length, 3);
  for (const [, x] of rects) assert.equal(Number(x), 230, 'cada barra parte en la línea base (0)');
  assert.ok(Number(rects[0][2]) > Number(rects[1][2]), 'de mayor a menor');
  assert.ok(/Ver la tabla/.test(text) && /id="tabla-[^"]+"/.test(html));
});

test('U12: la jerarquía de derechos explica a la vista (0 acciones) por qué los niveles no suman; «Sin subcategoría» como fila propia', () => {
  const eje = DERECHOS.find(i => !i.parent_code), right = DERECHOS.find(i => i.parent_code === eje.code), sub = DERECHOS.find(i => i.parent_code === right.code);
  const one = (dim, entries) => response({rows: [dim], values: {[dim]: entries.map(([key, label]) => ({key, label}))}, cells: entries.map(([key, , n]) => ({row: [key], column: [], n, unique: n})),
    grand: {n: 3, unique: 3}, rowTotals: entries.map(([key, , n]) => ({key: [key], n, unique: n}))});
  const levels = {DERECHO_EJE: model.readResult(one('DERECHO_EJE', [[eje.code, eje.label, 2], ['PENDIENTE', 'Pendiente', 1]])),
    DERECHO: model.readResult(one('DERECHO', [[right.code, right.label, 2], ['PENDIENTE', 'Pendiente', 1]])),
    DERECHO_SUBCATEGORIA: model.readResult(one('DERECHO_SUBCATEGORIA', [[sub.code, sub.label, 1], ['SIN_DESGLOSE', 'Sin subcategoría', 1], ['PENDIENTE', 'Pendiente', 1]]))};
  const tree = model.rightsTree(levels, CATALOG);
  const q = {...model.emptyQuery(model.publicAccount(2025)), measure: 'REQUERIMIENTOS_CON_VALOR', rows: ['DERECHO']};
  const html = render(q, one('DERECHO', [[right.code, right.label, 2], ['PENDIENTE', 'Pendiente', 1]]), {levels, tree});
  const text = sweep(html, 'derechos');
  assert.ok(text.includes(model.HIERARCHY_NOTE), 'la nota está a la vista sin abrir nada');
  for (const t of [eje.label, right.label, sub.label, 'Sin subcategoría']) assert.ok(text.includes(t), t);
  assert.ok(html.includes('aria-expanded="true"'), 'los niveles se pliegan');
  assert.ok(/<tr class="level-2 special"><th scope="row"><span[^>]*>Sin subcategoría<\/span><\/th><td class="num">1<\/td>/.test(html));
});

test('cada tipo de gráfico: nombre accesible, ayuda por marca enfocable y descarga; líneas con marcadores y circular con porcentajes', () => {
  const data = {categories: [{key: 'a', label: 'Enero'}, {key: 'b', label: 'Febrero'}], series: [{key: 's1', label: 'Correo', values: [2, 3]}, {key: 's2', label: 'Teléfono', values: [1, 0]}],
    total: 6, percent: false, format: v => v == null ? '—' : String(v)};
  for (const kind of ['barras', 'agrupadas', 'apiladas', 'lineas', 'circular', 'pareadas']) {
    const d = kind === 'circular' ? {...data, series: [data.series[0]], total: 5} : kind === 'pareadas' ? {...data, series: [data.series[0], {...data.series[1], base: true}]} : data;
    const html = renderToStaticMarkup(createElement(chart.Chart, {kind, data: d, title: 'Ingresos', subtitle: 'Cuenta Pública', notes: ['Nota de prueba'], source: 'Fuente: SIGER', file: 'f', tableId: 't'}));
    sweep(html, kind);
    assert.ok(/<g tabindex="0" role="img" aria-label="Enero[^"]*"/.test(html), kind + ': marca enfocable');
    assert.ok(/<title>Enero/.test(html), kind + ': ayuda emergente');
    assert.ok(html.includes('Nota de prueba') && html.includes('Fuente: SIGER') && html.includes('Descargar PNG'));
    if (kind === 'lineas') assert.ok(/<circle[^>]*r="4"/.test(html));
    if (kind === 'circular') assert.ok(html.includes('40,0 %') && html.includes('60,0 %'));
    if (kind !== 'barras' && kind !== 'circular') assert.ok(html.includes('Teléfono'), kind + ': leyenda con dos series');
  }
});

test('recorridos U de E12: número máximo de acciones desde la entrada al explorador (#estadisticas)', () => {
  // Cada paso es un clic o una eleccion en un control que esta a la vista en la pantalla renderizada.
  const panorama = visible(page('estadisticas'));
  const informes = visible(page('estadisticas?s=informes'));
  // U1 (max. 3): 1. Informes -> la Figura 1 de la ultima Cuenta Publica completa con sus cuatro desgloses.
  assert.ok(panorama.includes('Informes') && informes.includes('Por adscripción institucional'));
  const base = {...model.emptyQuery(model.publicAccount(2025))};
  // U2 (max. 2 desde un resultado): 1. «Comparar con» -> «Periodo anterior».
  const withResult = visible(page(model.encodeRoute('explorar', {...base, rows: ['TIPO_SOLICITUD']})));
  assert.ok(withResult.includes('Comparar con') && withResult.includes('Periodo anterior'));
  // U3 (max. 6): 1. Explorar; 2. «Filas» de Tipo de solicitud; 3. «Columnas» de Región del requirente; 4. Cifra «% por fila».
  const start = visible(page(model.encodeRoute('explorar', base)));
  assert.ok(start.includes('Agregar Tipo de solicitud a filas') && start.includes('Agregar Región del requirente a columnas'));
  const crossed = explore.place(explore.place(base, 'rows', 'TIPO_SOLICITUD'), 'columns', 'REGION_REQUIRENTE');
  assert.deepEqual([crossed.rows, crossed.columns], [['TIPO_SOLICITUD'], ['REGION_REQUIRENTE']]);
  assert.ok(visible(page(model.encodeRoute('explorar', crossed))).includes('% por fila'));
  // U7 (max. 3) y U13 (max. 2): el resultado ya trae el grafico sugerido; 1. «Descargar PNG» (o SVG), con titulo, notas y fuente.
  const result = render({...base, rows: ['TIPO_SOLICITUD']}, response({rows: ['TIPO_SOLICITUD'], values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}]},
    cells: [{row: ['VULNERACION'], column: [], n: 2, unique: 2}], grand: {n: 2, unique: 2}}));
  assert.ok(visible(result).includes('Descargar PNG') && visible(result).includes('Descargar SVG'));
  assert.ok(/<figure[\s\S]*Ingresos por Tipo de solicitud[\s\S]*Fuente: SIGER, Defensoría de la Niñez[\s\S]*Descargar PNG/.test(result), 'título y fuente en la figura que se descarga');
  // U8 (max. 4): 1. «Guardar vista»; 2. nombre; 3. Confirmar; 4. «Compartir» en la lista (sin otro dialogo).
  assert.ok(withResult.includes('Guardar vista'));
  // U11 (max. 4): 1. clic en un indicador del Panorama (abre Explorar con su consulta); 2. Cifra «% por fila».
  assert.ok(panorama.includes('Abrir en Explorar'));
  const opened = model.indicatorQuery(model.INDICATORS[0], cat(), CATALOG, model.panoramaPeriod('2026-11-26').period, 'MISMO_PERIODO_ANIO_ANTERIOR');
  assert.deepEqual(opened.rows, ['PERIODO_MES']);
  assert.equal(explore.withMeasure({...opened, percent: 'FILA'}, 'INGRESOS').percent, 'FILA');
});
