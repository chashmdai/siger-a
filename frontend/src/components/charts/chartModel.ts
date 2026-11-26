// Graficos del explorador estadistico (0.8.0, gate E12; DISENO 10.3): logica pura, sin dependencias. Sugerencia segun la
// forma de los datos, reglas del circular, escala que siempre parte en 0 y geometria de cada marca. Los colores son los
// tokens --chart-* de styles/tokens.css (tests/charts.test.ts comprueba que coinciden y su contraste): se escriben como
// atributos para que el SVG y el PNG descargados no dependan de la hoja de estilos.

/** Paleta categorica en orden fijo (nunca ciclica): contraste >= 3:1 sobre blanco y separacion validada para daltonismo. */
export const CHART_COLORS = ['#1565c0', '#c4501a', '#6a4ce2', '#c2307a', '#2e7d32', '#5c6bc0', '#e05a00', '#00866e'];
/** Serie de la cifra base en una comparacion (gris neutro, no es una categoria). */
export const BASE_COLOR = '#66787f';
export const TEXT_COLOR = '#2f4955';
export const MUTED_COLOR = '#4a626d';
export const GRID_COLOR = '#dce5ea';
export const FONT = 'Cabin, Arial, sans-serif';
export const MAX_SERIES = CHART_COLORS.length;
export const MAX_PIE = 6;
export const MAX_BARS = 80;

export type ChartKind = 'barras' | 'agrupadas' | 'apiladas' | 'lineas' | 'circular' | 'pareadas';
export const KIND_LABELS: Record<ChartKind, string> = {barras: 'Barras', agrupadas: 'Barras agrupadas', apiladas: 'Barras apiladas', lineas: 'Líneas', circular: 'Circular', pareadas: 'Barras pareadas'};
/** Forma de los datos: dimensiones, si son de periodo o multiples, si hay comparacion y cuantas categorias y series hay. */
export type Shape = {rows: number; columns: number; period: boolean; multipleRows: boolean; multipleColumns: boolean; comparison: boolean; percent: boolean;
  categories: number; series: number};
export type Choice = {kind: ChartKind; label: string; ok: boolean; reason: string};

/**
 * Grafico sugerido (tabla de DISENO 10.3): una comparacion -> barras pareadas; un eje de periodo -> lineas; dos
 * dimensiones -> barras agrupadas; una dimension (de valor unico o multiple) -> barras horizontales de mayor a menor.
 */
export function suggest(s: Shape): ChartKind | null {
  const all = choices(s).filter(c => c.ok);
  if (all.length === 0) return null;
  const order: ChartKind[] = s.comparison ? ['pareadas'] : s.period ? ['lineas'] : s.columns > 0 ? ['agrupadas', 'apiladas'] : ['barras'];
  return order.find(k => all.some(c => c.kind === k)) ?? all[0].kind;
}
/** Graficos que admite la forma, con la razon en lenguaje llano de los que no. */
export function choices(s: Shape, values?: number[], total?: number): Choice[] {
  const c = (kind: ChartKind, reason: string): Choice => ({kind, label: KIND_LABELS[kind], ok: reason === '', reason});
  const tooMany = s.categories > MAX_BARS ? `Hay ${s.categories} categorías: el gráfico no se leería. Usa la tabla o acota con un filtro.` : '';
  const empty = s.categories === 0 ? 'No hay cifras para graficar.' : '';
  const base = empty || tooMany;
  const seriesProblem = s.series > MAX_SERIES ? `Hay ${s.series} columnas y solo ${MAX_SERIES} colores distinguibles. Pasa ese campo a filas o usa la tabla.` : '';
  return [
    c('barras', base || (s.columns > 0 ? 'Con columnas usa barras agrupadas o apiladas.' : s.comparison ? 'Con una comparación usa barras pareadas.' : '')),
    c('agrupadas', base || (s.columns === 0 ? 'Necesita un campo en columnas.' : s.comparison ? 'Con una comparación usa barras pareadas.' : seriesProblem)),
    c('apiladas', base || (s.columns === 0 ? 'Necesita un campo en columnas.' : s.comparison ? 'Con una comparación usa barras pareadas.'
      : s.multipleColumns ? 'El campo de columnas tiene valores múltiples: sus partes no suman el total y no se pueden apilar.'
        : s.percent ? 'Los porcentajes no se apilan: usa barras agrupadas.' : seriesProblem)),
    c('lineas', base || (!s.period ? 'Las líneas necesitan un eje de periodo en filas.' : s.comparison ? 'Con una comparación usa barras pareadas.' : seriesProblem)),
    c('circular', base || pieReason(s, values, total)),
    c('pareadas', base || (!s.comparison ? 'Necesita una comparación con otro periodo.' : s.columns > 0 ? 'La comparación se grafica sin columnas: quita el campo de columnas o usa la tabla.' : '')),
  ];
}
/**
 * Circular (D38): solo con una dimension de valor unico, sin columnas, con 6 categorias o menos y cuyas partes suman el
 * 100 % del total del servidor. Si no, se explica por que y se proponen barras.
 */
export function pieReason(s: Shape, values?: number[], total?: number): string {
  if (s.multipleRows) return 'Un requerimiento puede tener varios valores: las partes no suman el total. Usa barras.';
  if (s.rows !== 1) return s.rows === 0 ? 'Necesita un campo en filas.' : 'Solo con un campo en filas. Usa barras.';
  if (s.columns > 0) return 'Solo sin campos en columnas. Usa barras agrupadas.';
  if (s.comparison) return 'No muestra comparaciones. Usa barras pareadas.';
  if (s.period) return 'Un eje de tiempo se lee en líneas, no en un circular.';
  if (s.percent) return 'Elige «Cantidad»: el circular ya muestra el porcentaje de cada parte.';
  if (s.categories > MAX_PIE) return `Tiene ${s.categories} categorías; el circular admite ${MAX_PIE} o menos. Usa barras.`;
  if (values && total != null && values.reduce((a, b) => a + b, 0) !== total) return 'Las partes no suman el total. Usa barras.';
  return '';
}

/** Escala desde 0 hasta un maximo "redondo" con 4 o 5 marcas; las barras siempre parten en 0. */
export function scale(max: number, integer = true): {max: number; ticks: number[]} {
  if (!(max > 0)) return {max: 1, ticks: [0, 1]};
  const raw = max / 4;
  const pow = 10 ** Math.floor(Math.log10(raw));
  const found = [1, 2, 2.5, 5, 10].map(f => f * pow).find(x => x >= raw) ?? 10 * pow;
  // Los conteos no tienen marcas fraccionarias.
  const step = integer ? Math.max(1, Math.ceil(found)) : found;
  const top = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let t = 0; t <= top + step / 2; t += step) ticks.push(Math.round(t * 1e6) / 1e6);
  return {max: top, ticks};
}
/** Largo de una barra en pixeles: proporcional al valor, desde 0; un valor nulo no dibuja barra. */
export const barLength = (value: number | null, max: number, width: number) => value == null || !(value > 0) ? 0 : value / max * width;

/** Rebanadas del circular: angulos en radianes desde arriba y el porcentaje de cada parte sobre el total del servidor. */
export function pieSlices(values: number[], total: number) {
  let angle = 0;
  return values.map(v => {
    const share = total > 0 ? v / total : 0;
    const start = angle;
    angle += share * Math.PI * 2;
    return {start, end: angle, percent: share * 100};
  });
}
/** Arco SVG de una rebanada (centro cx, cy y radio r). */
export function arcPath(cx: number, cy: number, r: number, start: number, end: number) {
  if (end - start >= Math.PI * 2 - 1e-9) return `M ${cx} ${cy - r} A ${r} ${r} 0 1 1 ${cx - 0.01} ${cy - r} Z`;
  const p = (a: number) => [cx + r * Math.sin(a), cy - r * Math.cos(a)].map(x => Math.round(x * 100) / 100);
  const [x1, y1] = p(start), [x2, y2] = p(end);
  return `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${end - start > Math.PI ? 1 : 0} 1 ${x2} ${y2} Z`;
}
/** Puntos de una linea: x repartido en el ancho, y desde la base (0) hacia arriba. Un nulo corta la linea. */
export function linePoints(values: (number | null)[], width: number, height: number, max: number) {
  const step = values.length > 1 ? width / (values.length - 1) : 0;
  return values.map((v, i) => ({x: values.length > 1 ? i * step : width / 2, y: v == null ? null : height - (v / max) * height, value: v}));
}
/** Orden de barras horizontales de mayor a menor (DISENO 10.3); los estados especiales siempre al final. */
export function byValue<T extends {value: number | null; special?: boolean}>(items: T[]) {
  return [...items].sort((a, b) => Number(!!a.special) - Number(!!b.special) || (b.value ?? -1) - (a.value ?? -1));
}

// --- Contraste (WCAG 2.x) -------------------------------------------------------------------------------------------
function luminance(hex: string) {
  const v = hex.replace('#', '');
  const [r, g, b] = [0, 2, 4].map(i => parseInt(v.slice(i, i + 2), 16) / 255).map(c => c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
export function contrast(a: string, b: string) {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}
