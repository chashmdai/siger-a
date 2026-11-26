// Series historicas en la interfaz (0.8.0, D42, gate E13; DISENO 7 y 10.4): logica pura del asistente de importacion y de
// la vista junto a SIGER. El servidor (GET /stats/series/template, POST /series/preview sin escrituras, STAT_SERIES_IMPORT,
// STAT_SERIES_ANNUL, POST /series/compare) valida, guarda y decide si la suma procede; aqui nada se combina ni se suma.
import type {Row} from '../../lib/rows.ts';
import {list, text} from '../../lib/rows.ts';
import type {Query, Result} from './statsModel.ts';
import {addDays, emptyQuery, fmtInt, keyOf, periodLabel} from './statsModel.ts';

export const SERIES_COLUMNS: {key: string; label: string}[] = [
  {key: 'periodo_desde', label: 'Desde (aaaa-mm-dd)'}, {key: 'periodo_hasta', label: 'Hasta (aaaa-mm-dd)'}, {key: 'dimension', label: 'Campo de SIGER'},
  {key: 'categoria', label: 'Categoría'}, {key: 'valor', label: 'Cifra'},
];
export const GRANULARITIES: {code: string; label: string; dim: string | null}[] = [
  {code: 'MES', label: 'Mensual', dim: 'PERIODO_MES'}, {code: 'TRIMESTRE', label: 'Trimestral', dim: 'PERIODO_TRIMESTRE'},
  {code: 'SEMESTRE', label: 'Semestral', dim: 'PERIODO_SEMESTRE'}, {code: 'ANIO', label: 'Anual (enero a diciembre)', dim: 'PERIODO_ANIO'},
  {code: 'CUENTA_PUBLICA', label: 'Cuenta Pública (julio a junio)', dim: 'PERIODO_CUENTA_PUBLICA'}, {code: 'DIA', label: 'Diaria', dim: 'PERIODO_DIA'},
  {code: 'PERSONALIZADO', label: 'Periodos propios', dim: null},
];
export const SERIES_STEPS = ['Datos de la serie', 'Archivo', 'Vista previa', 'Confirmación'] as const;

export type SeriesDraft = {title: string; indicator: string; scope: number | null | undefined; source: string; methodology: string; granularity: string;
  period_from: string; period_to: string; reason: string};
export const emptySeries = (scope: number | null | undefined): SeriesDraft => ({title: '', indicator: 'INGRESOS', scope, source: '', methodology: '', granularity: 'CUENTA_PUBLICA',
  period_from: '', period_to: '', reason: ''});

const DAY = /^\d{4}-\d{2}-\d{2}$/;
/** Lo que falta en los datos de la serie, en lenguaje llano. */
export function draftProblem(d: SeriesDraft): string {
  if (d.scope === undefined) return 'Elige el ámbito de la serie.';
  if (!d.title.trim()) return 'Escribe el título de la serie.';
  if (!d.indicator.trim()) return 'Indica qué cuenta la serie (por ejemplo, ingresos).';
  if (!d.source.trim()) return 'Indica la fuente (el informe o documento de origen).';
  if (!d.methodology.trim()) return 'Describe la metodología con que se contaron las cifras.';
  if (!DAY.test(d.period_from) || !DAY.test(d.period_to)) return 'Indica el periodo que cubre la serie.';
  if (d.period_to < d.period_from) return 'La fecha de término no puede ser anterior a la de inicio.';
  if (!d.reason.trim()) return 'Escribe el fundamento de la importación.';
  return '';
}

/** Bytes del archivo en base64 (el servidor recibe el contenido en content; nunca en la URL). */
export function toBase64(bytes: Uint8Array): string {
  let s = '';
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}
export const formatOf = (name: string) => /\.xlsx$/i.test(name) ? 'xlsx' : 'csv';

export type Mapping = {dimension: string; category: string; code: string};
/**
 * Cuerpo de la vista previa y de STAT_SERIES_IMPORT: los datos, el archivo, las columnas relacionadas (si el archivo no
 * usa la plantilla) y solo las correspondencias confirmadas una a una (DI-13: nunca se aplican solas).
 */
export function seriesBody(d: SeriesDraft, file: {format: string; content: string}, columns: Record<string, string>, confirmed: Mapping[]): Row {
  const body: Row = {title: d.title.trim(), indicator: d.indicator.trim(), source: d.source.trim(), methodology: d.methodology.trim(), granularity: d.granularity,
    period_from: d.period_from, period_to: d.period_to, reason: d.reason.trim(), format: file.format, content: file.content};
  if (d.scope != null) body.scope_id = d.scope;
  if (Object.values(columns).some(Boolean)) body.columns = Object.fromEntries(Object.entries(columns).filter(([, v]) => v));
  if (confirmed.length) body.mappings = confirmed.map(m => ({dimension: m.dimension, category: m.category, code: m.code}));
  return body;
}
/** Correspondencias que sugiere el servidor, todas sin marcar (la cuenta confirma cada una). */
export function suggestions(preview: Row): (Mapping & {checked: boolean; text: string})[] {
  return list(preview, 'suggested_mappings').map(m => ({dimension: text(m, 'dimension'), category: text(m, 'category'), code: text(m, 'code'), checked: false,
    text: `«${text(m, 'category')}» corresponde a ${text(m, 'code') === 'OFICINA_NACIONAL' ? '«Oficina Nacional»' : 'otra categoría de SIGER'}`}));
}

const WARNINGS: Record<string, string> = {
  PERIODOS_SUPERPUESTOS: 'Hay series con periodos que se superponen: no se suman y se muestran por separado.',
  ALCANCES_DISTINTOS: 'El ámbito de la serie y el de la consulta de SIGER no son el mismo: compáralas con cuidado; nunca se combinan.',
  SERIES_INCOMPATIBLES: 'Las series no tienen el mismo indicador, ámbito, metodología, granularidad o campos: no se pueden sumar.',
};
export const seriesWarning = (w: Row) => WARNINGS[text(w, 'code')] ?? 'El servidor informó una advertencia sobre estas series.';
export const scopeNote = (scopeLabel: string) => `Ámbito de la serie: ${scopeLabel}. Las cifras de SIGER son las de tu alcance.`;

/** Clave de periodo del motor (StatEngine.key) para el inicio de un periodo de la serie. */
export function periodKey(granularity: string, day: string): string {
  const [y, m] = day.split('-').map(Number);
  switch (granularity) {
    case 'DIA': return day;
    case 'MES': return `${y}-${String(m).padStart(2, '0')}`;
    case 'TRIMESTRE': return `${y}-T${Math.floor((m - 1) / 3) + 1}`;
    case 'SEMESTRE': return `${y}-S${m <= 6 ? 1 : 2}`;
    case 'ANIO': return String(y);
    case 'CUENTA_PUBLICA': return `${y}-${y + 1}`;
    default: return day;
  }
}
/**
 * Consulta de SIGER junto a la serie (GO de E13, D): ingresos con la granularidad de la serie, desde el inicio de la serie
 * hasta ayer en hora de Chile; con una categoria de la serie que tiene correspondencia confirmada, filtrada por ese valor.
 * Una serie de periodos propios no tiene un eje comparable: nulo.
 */
export function sigerQuery(series: Row, today: string, category?: {dimension: string; code: string | null}): Query | null {
  const g = GRANULARITIES.find(x => x.code === text(series, 'granularity'));
  if (!g?.dim) return null;
  const to = addDays(today, -1), from = text(series, 'period_from');
  if (!DAY.test(from) || to < from) return null;
  const q: Query = {...emptyQuery({type: 'PERSONALIZADO', from, to}), rows: [g.dim]};
  if (category?.code) q.filters = {[category.dimension]: [category.code]};
  return q;
}
/** Categorias de la serie para elegir la linea (el total declarado primero). */
export function seriesCategories(series: Row): {key: string; label: string; dimension: string; category: string; code: string | null; total: boolean}[] {
  const out: {key: string; label: string; dimension: string; category: string; code: string | null; total: boolean}[] = [];
  for (const v of list(series, 'values')) {
    const total = v.is_total === true, dimension = text(v, 'dimension'), category = text(v, 'category_label');
    const key = keyOf([dimension, total ? '' : category]);
    if (out.some(o => o.key === key)) continue;
    out.push({key, dimension, category, total, code: v.category_code == null ? null : text(v, 'category_code'), label: total ? `Total (${dimension})` : category});
  }
  return out.sort((a, b) => Number(b.total) - Number(a.total));
}

/**
 * Grafico de la serie junto a SIGER: dos lineas sobre el eje de periodos (union de los dos, en orden), sin sumarlas; la
 * discontinuidad (fin de la serie historica) se marca con texto. Los valores son los declarados y los del servidor.
 */
export function seriesChart(series: Row, choice: {dimension: string; category: string; total: boolean}, siger: Result | null, sigerDim: string | null) {
  const g = text(series, 'granularity');
  const own = new Map<string, {label: string; value: number}>();
  for (const v of list(series, 'values')) {
    if (text(v, 'dimension') !== choice.dimension || (v.is_total === true) !== choice.total || (!choice.total && text(v, 'category_label') !== choice.category)) continue;
    own.set(periodKey(g, text(v, 'period_from')), {label: periodLabel({type: g, from: text(v, 'period_from'), to: text(v, 'period_to')}), value: Number(v.value ?? 0)});
  }
  const mine = new Map<string, {label: string; value: number}>();
  if (siger && sigerDim) for (const p of siger.values[sigerDim] ?? []) mine.set(p.key, {label: p.label, value: siger.cells.find(c => c.row[0] === p.key)?.n ?? 0});
  const keys = [...new Set([...own.keys(), ...mine.keys()])].sort();
  const lastOwn = keys.reduce((last, k, i) => own.has(k) ? i : last, -1);
  const categories = keys.map(k => ({key: k, label: own.get(k)?.label ?? mine.get(k)?.label ?? k}));
  const divider = lastOwn >= 0 && lastOwn < keys.length - 1
    ? {after: lastOwn, label: `Discontinuidad: la serie «${text(series, 'title')}» llega hasta ${categories[lastOwn].label}; desde ahí solo hay cifras de SIGER, con otra fuente y metodología.`}
    : undefined;
  return {
    categories, divider, total: null, percent: false, format: (v: number | null) => v == null ? '—' : fmtInt(v),
    series: [
      {key: 'serie', label: 'Serie histórica: ' + text(series, 'title'), values: keys.map(k => own.get(k)?.value ?? null)},
      ...(siger ? [{key: 'siger', label: 'SIGER', values: keys.map(k => mine.has(k) ? mine.get(k)!.value : null)}] : []),
    ],
  };
}
/** La suma solo se ofrece si el servidor la admite (series compatibles, vigentes y sin solapamiento) y se pidio. */
export const sumRows = (compare: Row | null) => compare && Array.isArray(compare.sum) ? list(compare, 'sum') : null;
export const seriesStateLabel = (state: string) => state === 'ANULADA' ? 'Anulada (se conserva)' : 'Vigente';
