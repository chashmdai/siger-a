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

export type ChartKind = 'barras' | 'agrupadas' | 'apiladas' | 'lineas' | 'columnas' | 'circular' | 'pareadas';
export const KIND_LABELS: Record<ChartKind, string> = {barras: 'Barras', agrupadas: 'Barras agrupadas', apiladas: 'Barras apiladas', lineas: 'Líneas', columnas: 'Columnas',
  circular: 'Circular', pareadas: 'Barras pareadas'};
/** E13 (ajuste 3): el eje de periodo siempre es horizontal, con columnas o lineas; nunca barras horizontales. */
export const PERIOD_HORIZONTAL = 'El eje de periodo va en horizontal: usa columnas o líneas.';
/** E14 (correccion 3): con un eje de periodo, la comparacion se ofrece en columnas pareadas, como dice su boton. */
export const PERIOD_PAIRED = 'Usa columnas pareadas: con un eje de periodo, la comparación va en columnas.';
/** Forma de los datos: dimensiones, si son de periodo o multiples, si hay comparacion y cuantas categorias y series hay. */
export type Shape = {rows: number; columns: number; period: boolean; multipleRows: boolean; multipleColumns: boolean; comparison: boolean; percent: boolean;
  categories: number; series: number};
export type Choice = {kind: ChartKind; label: string; ok: boolean; reason: string};

/**
 * Grafico sugerido (tabla de DISENO 10.3, con los ajustes de E13): una comparacion de periodos -> columnas pareadas; otra
 * comparacion -> barras pareadas; un eje de periodo -> lineas; dos dimensiones -> barras agrupadas; una dimension (de valor
 * unico o multiple) -> barras horizontales de mayor a menor.
 */
export function suggest(s: Shape): ChartKind | null {
  const all = choices(s).filter(c => c.ok);
  if (all.length === 0) return null;
  const order: ChartKind[] = s.comparison ? (s.period ? ['columnas', 'lineas'] : ['pareadas']) : s.period ? ['lineas', 'columnas'] : s.columns > 0 ? ['agrupadas', 'apiladas'] : ['barras'];
  return order.find(k => all.some(c => c.kind === k)) ?? all[0].kind;
}
/** Graficos que admite la forma, con la razon en lenguaje llano de los que no. */
export function choices(s: Shape, values?: number[], total?: number): Choice[] {
  const c = (kind: ChartKind, reason: string, label = KIND_LABELS[kind]): Choice => ({kind, label, ok: reason === '', reason});
  const tooMany = s.categories > MAX_BARS ? `Hay ${s.categories} categorías: el gráfico no se leería. Usa la tabla o acota con un filtro.` : '';
  const empty = s.categories === 0 ? 'No hay cifras para graficar.' : '';
  const base = empty || tooMany;
  const seriesProblem = s.series > MAX_SERIES ? `Hay ${s.series} columnas y solo ${MAX_SERIES} colores distinguibles. Pasa ese campo a filas o usa la tabla.` : '';
  return [
    c('barras', base || (s.period ? PERIOD_HORIZONTAL : s.columns > 0 ? 'Con columnas usa barras agrupadas o apiladas.' : s.comparison ? 'Con una comparación usa barras pareadas.' : '')),
    c('agrupadas', base || (s.period ? PERIOD_HORIZONTAL : s.columns === 0 ? 'Necesita un campo en columnas.' : s.comparison ? 'Con una comparación usa barras pareadas.' : seriesProblem)),
    c('apiladas', base || (s.period ? PERIOD_HORIZONTAL : s.columns === 0 ? 'Necesita un campo en columnas.' : s.comparison ? 'Con una comparación usa barras pareadas.'
      : s.multipleColumns ? 'El campo de columnas tiene valores múltiples: sus partes no suman el total y no se pueden apilar.'
        : s.percent ? 'Los porcentajes no se apilan: usa barras agrupadas.' : seriesProblem)),
    // Con una comparacion de periodos, dos lineas: la del periodo consultado y la de comparacion.
    c('lineas', base || (!s.period ? 'Las líneas necesitan un eje de periodo en filas.' : seriesProblem)),
    c('columnas', base || (!s.period ? 'Las columnas se usan para un eje de periodo; para categorías usa barras.' : seriesProblem), s.comparison ? 'Columnas pareadas' : KIND_LABELS.columnas),
    c('circular', base || pieReason(s, values, total)),
    c('pareadas', base || (!s.comparison ? 'Necesita una comparación con otro periodo.' : s.period ? PERIOD_PAIRED
      : s.columns > 0 ? 'La comparación se grafica sin columnas: quita el campo de columnas o usa la tabla.' : '')),
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
  if (s.comparison) return s.period ? 'No muestra comparaciones. Usa columnas pareadas.' : 'No muestra comparaciones. Usa barras pareadas.';
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

// --- Eje de periodo (E14, correccion 1) --------------------------------------------------------------------------------
const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const SHORT_MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
/** Ancho estimado de un caracter del eje (11 px de Cabin) y separacion minima entre dos rotulos. */
export const TICK_CHAR = 6.4;
export const TICK_GAP = 8;
/**
 * Rotulo abreviado de un periodo del motor, en una o dos partes y sin cortar: "julio de 2026" -> jul / 2026,
 * "1.º trimestre de 2026" -> T1 / 2026, "2.º semestre de 2026" -> S2 / 2026, "Cuenta Pública 2025-2026" -> CP / 2025-2026,
 * "01-07-2026" -> 01-07 / 2026. Otro texto se reparte por palabras en dos lineas. El nombre completo va en la ayuda.
 */
export function periodTick(label: string): string[] {
  let m = /^(\p{L}+) de (\d{4})$/u.exec(label);
  if (m && MONTHS.includes(m[1].toLocaleLowerCase('es-CL'))) return [SHORT_MONTHS[MONTHS.indexOf(m[1].toLocaleLowerCase('es-CL'))], m[2]];
  if ((m = /^(\d)\.º trimestre de (\d{4})$/.exec(label))) return ['T' + m[1], m[2]];
  if ((m = /^(\d)\.º semestre de (\d{4})$/.exec(label))) return ['S' + m[1], m[2]];
  if ((m = /^Cuenta Pública (\d{4}-\d{4})$/.exec(label))) return ['CP', m[1]];
  if ((m = /^(\d{2}-\d{2})-(\d{4})$/.exec(label))) return [m[1], m[2]];
  const words = label.split(/\s+/).filter(Boolean);
  if (words.length < 2) return [label];
  let best = 1, worst = Infinity;
  for (let i = 1; i < words.length; i++) {
    const w = Math.max(words.slice(0, i).join(' ').length, words.slice(i).join(' ').length);
    if (w < worst) {worst = w; best = i;}
  }
  return [words.slice(0, best).join(' '), words.slice(best).join(' ')];
}
/**
 * Rotulos del eje de periodo para un espacio de `band` px por categoria: en una linea ("jul 2026") si todos caben, si no en
 * dos; y cada cuantas categorias se rotula (step) para que dos rotulos nunca se superpongan. Nunca se cortan con "…".
 */
export function periodAxis(labels: string[], band: number): {ticks: {lines: string[]; title: string}[]; step: number} {
  const parts = labels.map(periodTick);
  const width = (t: string) => t.length * TICK_CHAR;
  const oneLine = parts.every(p => width(p.join(' ')) + TICK_GAP <= band);
  const ticks = parts.map((p, i) => ({lines: oneLine ? [p.join(' ')] : p, title: labels[i]}));
  const widest = Math.max(0, ...ticks.flatMap(t => t.lines.map(width)));
  return {ticks, step: band > 0 ? Math.max(1, Math.ceil((widest + TICK_GAP) / band)) : 1};
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
