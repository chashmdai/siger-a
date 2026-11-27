// Datos fijos y normalizacion del SVG exportado (0.8.1, F01, ajuste 6 del GO): lo que importa del archivo descargado es que
// tenga las mismas marcas (con su nombre accesible), las mismas cifras y textos y los mismos colores como atributos. La
// referencia de 0.8.0 (chart-export-080.json) se genero con el Chart.tsx de v0.8.0 y estos mismos datos.
const fmt = v => v == null ? '—' : String(v).replace('.', ',');
const cats = n => Array.from({length: n}, (_, i) => ({key: 'c' + i, label: 'Categoría ' + (i + 1)}));
const months = ['julio de 2025', 'agosto de 2025', 'septiembre de 2025', 'octubre de 2025', 'noviembre de 2025', 'diciembre de 2025'].map((label, i) => ({key: 'm' + i, label}));
export const CASES = [
  ['barras', {categories: cats(4), series: [{key: 'n', label: 'Ingresos', values: [12, 40, 0, 7]}], total: 59, percent: false, format: fmt}],
  ['agrupadas', {categories: cats(3), series: [{key: 'a', label: 'Serie A', values: [5, 9, 2]}, {key: 'b', label: 'Serie B', values: [3, 0, 6]}], total: 25, percent: false, format: fmt}],
  ['apiladas', {categories: cats(3).map((c, i) => ({...c, total: [8, 9, 8][i]})), series: [{key: 'a', label: 'Serie A', values: [5, 9, 2]}, {key: 'b', label: 'Serie B', values: [3, 0, 6]}], total: 25, percent: false, format: fmt}],
  ['pareadas', {categories: cats(3), series: [{key: 'n', label: 'Cifra actual', values: [10, 4, 7]}, {key: 'base', label: 'Periodo anterior', values: [8, 6, 7], base: true}], total: 21, percent: false, format: fmt}],
  ['columnas', {categories: months, series: [{key: 'n', label: 'Ingresos', values: [3, 8, 0, 12, 5, 9]}], total: 37, percent: false, format: fmt, keepOrder: true}],
  ['columnas', {categories: months, series: [{key: 'n', label: 'Cifra actual', values: [3, 8, 0, 12, 5, 9]}, {key: 'base', label: 'Periodo anterior', values: [2, 6, 1, 10, 7, 9], base: true}], total: 37, percent: false, format: fmt, keepOrder: true}],
  ['lineas', {categories: months, series: [{key: 'n', label: 'Ingresos', values: [3, 8, 0, 12, null, 9]}], total: 32, percent: false, format: fmt, keepOrder: true}],
  ['lineas', {categories: months, series: [{key: 'n', label: 'Serie', values: [3, 8, 0, 12, 5, 9]}, {key: 'base', label: 'SIGER', values: [2, 6, 1, 10, 7, 9], base: true}], total: 37, percent: false, format: fmt, keepOrder: true,
    divider: {after: 2, label: 'Cambio de fuente'}}],
  ['circular', {categories: cats(4), series: [{key: 'n', label: 'Ingresos', values: [12, 40, 0, 7]}], total: 59, percent: false, format: fmt}],
];
/** El SVG del grafico dentro del marcado de <Chart>. */
export function chartSvg(html) {
  const m = html.match(/<div class="stat-chart-canvas"[^>]*>[\s\S]*?(<svg[\s\S]*?<\/svg>)/);
  if (!m) throw new Error('sin SVG');
  return m[1];
}
const unescape = s => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#x27;/g, "'").replace(/&#39;/g, "'").replace(/&amp;/g, '&');
/**
 * Lo que importa del SVG exportado: nombres de las marcas (categoria, serie y cifra), textos a la vista (cifras, ejes,
 * leyenda, ayudas) y colores de relleno y trazo dados como atributos. Ordenados: el orden de dibujo puede cambiar.
 */
export function essence(svg) {
  const marks = [...svg.matchAll(/role="img"[^>]*?aria-label="([^"]*)"|aria-label="([^"]*)"[^>]*?role="img"/g)].map(m => unescape(m[1] ?? m[2])).sort();
  const texts = [...svg.matchAll(/<(text|tspan|title)\b[^>]*>([^<]+)</g)].map(m => unescape(m[2]).trim()).filter(Boolean).sort();
  const colors = [...new Set([...svg.matchAll(/(?:fill|stroke|stop-color)="(#[0-9a-fA-F]{3,8})"/g)].map(m => m[1].toLowerCase()))].sort();
  return {marks, texts, colors};
}
