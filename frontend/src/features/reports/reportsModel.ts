/**
 * Reportes de gestion (G12): logica pura de la pantalla, sin React, cubierta por tests/reports.test.ts. El servidor
 * (Reports.java) decide el alcance, valida los filtros y calcula; aqui solo se arma el cuerpo de POST /api/reports, se
 * eligen las agrupaciones que tiene sentido ofrecer y se dan formato a cifras, grupos y barras.
 */
import {CHANNEL_LABELS, EXIT_LABELS, MATTER_LABELS, ORIGIN_LABELS, REPORT_LABELS, humanize} from '../../lib/labels.ts';

export const NOTE = 'Reporte de gestión del sistema; su carácter oficial lo define la institución.';
export type Kind = 'VOLUMEN' | 'TIEMPO' | 'ANTIGUEDAD';
export type ReportDef = {code: string; kind: Kind};

/** Que mide cada reporte: el tramo de tiempo o el conteo, en palabras del funcionario. */
export const REPORT_HELP: Record<string, string> = {
  VOLUMEN: 'COM por canal e ingreso, REQ por estado, unidad, complejidad y origen, y Casos por estado y materia, creados en el período.',
  REGISTRO: 'Desde la recepción de la comunicación hasta su registro en SIGER.',
  DISTRIBUCION: 'Desde el registro de la COM hasta su primera distribución como SOL.',
  TENENCIA_SOL: 'Cada llegada de una SOL a una jefatura hasta su salida: asumida, redirigida, devuelta o descartada.',
  ASUNCION: 'Desde la última llegada de la SOL hasta que la jefatura la asume.',
  SOL_TOTAL: 'Desde la primera distribución de la SOL hasta que se asume, con todas sus redirecciones.',
  APERTURA_CASO: 'Desde la creación del REQ hasta la apertura de su primer Caso.',
  ACUSE: 'Desde la asignación de un Caso hasta el acuse del profesional.',
  GESTION_CASO: 'Desde la apertura (o reapertura) del Caso hasta su cierre, sin el tiempo en espera, que se informa aparte.',
  REVISION_CIERRE: 'Desde la solicitud de término hasta el cierre confirmado o la devolución; retirar la solicitud la anula.',
  REQ_TOTAL: 'Desde la creación (o reactivación) del REQ hasta su cierre.',
  PRIMERA_ATENCION: 'Desde la entrega de una primera atención hasta que el destinatario la decide.',
  FLUJOS: 'Desde la solicitud de una colaboración, derivación o traslado hasta su decisión.',
  ANTIGUEDAD: 'REQ y Casos abiertos hoy, por días hábiles desde su creación.',
};

/** REGION = nivel y region de la sede responsable; REGION_ORIGEN = region de origen registrada de la persona (D21). */
export const GROUPS = ['NINGUNA', 'UNIDAD', 'AMBITO', 'REGION', 'REGION_ORIGEN', 'MATERIA', 'ORIGEN', 'AMBITO_MATERIA', 'SALIDA', 'PERSONA'];
/** Tramos con mas de una salida posible: solo en ellos tiene sentido agrupar o separar por salida. */
export const EXITS = ['TENENCIA_SOL', 'REVISION_CIERRE'];

/**
 * Agrupaciones que se ofrecen (Reports.report): el volumen no agrupa; la antiguedad no agrupa por salida ni por persona;
 * salida solo en los tramos con varias salidas; persona solo con Reportes por persona.
 */
export function groupsFor(report: ReportDef | undefined, people: boolean) {
  if (!report || report.kind === 'VOLUMEN') return ['NINGUNA'];
  return GROUPS.filter(g => (g !== 'PERSONA' || people && report.kind === 'TIEMPO') && (g !== 'SALIDA' || report.kind === 'TIEMPO' && EXITS.includes(report.code)));
}
/** Agrupacion vigente al cambiar de reporte: la elegida si se ofrece, si no "Sin agrupar". */
export const keepGroup = (group: string, allowed: string[]) => allowed.includes(group) ? group : 'NINGUNA';

/** Separacion por defecto del servidor: REQ total por origen y Tenencia de SOL por salida. */
export const defaultSplit = (code: string) => ({byOrigin: code === 'REQ_TOTAL', byExit: code === 'TENENCIA_SOL'});

export type Filters = {
  report: string; from: string; to: string; group: string; byOrigin: boolean; byExit: boolean;
  unit: string; scope: string; region: string; origin_region: string; level: string; complexity: string; channel: string; matter: string; modality: string; origin: string;
};
/**
 * Filtros de contenido (no el reporte, el periodo ni la agrupacion): los usa tambien la exportacion. region = region de
 * la sede; origin_region = region de origen registrada (codigo o SIN = sin region declarada).
 */
export const FILTER_KEYS = ['unit', 'level', 'region', 'origin_region', 'scope', 'complexity', 'channel', 'matter', 'modality', 'origin'] as const;
export const activeFilters = (f: Filters) => FILTER_KEYS.filter(k => f[k] !== '').length;
export const clearFilters = (f: Filters): Filters => ({...f, ...Object.fromEntries(FILTER_KEYS.map(k => [k, '']))});

/** Hoy en hora de Chile (aaaa-mm-dd), como Reports.filters. */
export const chileToday = (now = new Date()) => new Intl.DateTimeFormat('sv-SE', {timeZone: 'America/Santiago'}).format(now);
/** Periodo por defecto del servidor: los ultimos 12 meses, ambos dias incluidos. */
export function defaultPeriod(today: string) {
  const [y, m, d] = today.split('-').map(Number);
  const from = new Date(Date.UTC(y - 1, m - 1, d + 1));
  return {from: from.toISOString().slice(0, 10), to: today};
}
export function initialFilters(today: string): Filters {
  return {report: 'VOLUMEN', ...defaultPeriod(today), group: 'NINGUNA', byOrigin: false, byExit: false,
    unit: '', scope: '', region: '', origin_region: '', level: '', complexity: '', channel: '', matter: '', modality: '', origin: ''};
}

/** Cuerpo de POST /api/reports: sin los filtros vacios; la separacion solo en los tiempos. */
export function requestBody(f: Filters, report: ReportDef | undefined): Record<string, unknown> {
  const body: Record<string, unknown> = {report: f.report};
  if (report?.kind !== 'ANTIGUEDAD') {body.from = f.from; body.to = f.to;}
  if (report?.kind !== 'VOLUMEN') body.group = f.group;
  if (report?.kind === 'TIEMPO') {body.by_origin = f.byOrigin; body.by_exit = EXITS.includes(f.report) ? f.byExit : false;}
  if (report?.kind === 'ANTIGUEDAD') body.by_origin = f.byOrigin;
  for (const k of FILTER_KEYS) if (f[k] !== '') body[k] = k === 'scope' ? Number(f[k]) : f[k];
  return body;
}
/** Cuerpo de la exportacion: el periodo y los filtros de contenido (sin reporte ni agrupacion). */
export function exportBody(f: Filters): Record<string, unknown> {
  const body: Record<string, unknown> = {from: f.from, to: f.to};
  for (const k of FILTER_KEYS) if (f[k] !== '') body[k] = k === 'scope' ? Number(f[k]) : f[k];
  return body;
}

const number = (v: number, digits = 2) => v.toLocaleString('es-CL', {maximumFractionDigits: digits, useGrouping: true});
/** Cifras con coma decimal (es-CL); nulo = sin tramos. */
export const hours = (v: unknown) => v == null ? '—' : number(Number(v)) + ' h';
export const days = (v: unknown) => v == null ? '—' : number(Number(v)) + ' dh';
export const count = (v: unknown) => number(Number(v ?? 0), 0);

/** Ancho de una barra en porcentaje del maximo (0-100); un valor positivo nunca queda invisible. */
export function barWidth(value: unknown, max: number) {
  const v = Number(value ?? 0);
  if (!(max > 0) || !(v > 0)) return 0;
  return Math.max(2, Math.min(100, Math.round(v / max * 1000) / 10));
}

type Named = {code: string; name: string};
/** Rotulo de cada dimension de un grupo (Reports.key), en el orden de dimensions. */
export function groupParts(dimensions: string[], group: Record<string, unknown>, regions: Named[]): {dimension: string; label: string}[] {
  const regionName = (code: unknown) => code == null ? '' : regions.find(r => r.code === code)?.name ?? String(code);
  return dimensions.map(d => {
    switch (d) {
      case 'UNIDAD': return {dimension: d, label: String(group.unit ?? '') || 'Sin unidad'};
      case 'AMBITO': return {dimension: d, label: String(group.scope_code ?? '') + (group.regional === true && group.region != null ? ' · ' + regionName(group.region) : '')};
      case 'REGION': return {dimension: d, label: group.level === 'REGIONAL' ? (group.region == null ? 'Sede sin región' : 'Región ' + regionName(group.region)) : 'Nivel central'};
      case 'REGION_ORIGEN': return {dimension: d, label: group.origin_region == null ? NO_ORIGIN_REGION : regionName(group.origin_region)};
      case 'MATERIA': return {dimension: d, label: group.matter == null ? 'Sin materia' : MATTER_LABELS[String(group.matter)] ?? String(group.matter)};
      case 'ORIGEN': return {dimension: d, label: humanize(group.origin, ORIGIN_LABELS)};
      case 'SALIDA': return {dimension: d, label: humanize(group.exit, EXIT_LABELS)};
      case 'PERSONA': return {dimension: d, label: group.person_name == null ? 'Sin responsable' : String(group.person_name)};
      default: return {dimension: d, label: String(group[d.toLowerCase()] ?? '')};
    }
  });
}

/** Region de origen ausente (COM manual, ficha sin region o Formulario sin region valida). */
export const NO_ORIGIN_REGION = 'Sin región declarada';

/**
 * Registros con la region de origen corregida (D21): se cuentan por la region registrada. Solo si hay alguno (vacio
 * con 0); noun = [singular, plural] de lo que se cuenta.
 */
export function correctedNote(value: unknown, noun: [string, string]) {
  const n = Number(value ?? 0);
  if (!(n > 0)) return '';
  return `${count(n)} ${n === 1 ? noun[0] : noun[1]} con la región de origen corregida: ${n === 1 ? 'se cuenta' : 'se cuentan'} por la región registrada.`;
}

/** Rotulo de un conteo del volumen; nulo = "Sin ..." segun lo que se cuenta. */
export function tallyLabel(field: string, value: unknown) {
  if (value == null) return field === 'complexity' ? 'Sin clasificar' : field === 'matter' ? 'Sin materia' : 'Sin valor';
  if (field === 'channel') return humanize(value, CHANNEL_LABELS);
  if (field === 'matter') return MATTER_LABELS[String(value)] ?? String(value);
  if (field === 'origin') return humanize(value, ORIGIN_LABELS);
  if (field === 'unit') return String(value);
  return humanize(value);
}

/** Nombre de archivo de Content-Disposition (attachment; filename="..."), con un nombre neutro si no viene. */
export function filenameOf(header: string | null, fallback: string) {
  const m = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(header ?? '');
  const name = m ? decodeURIComponent(m[1] ?? m[2]) : '';
  return /^[\w.-]+$/.test(name) ? name : fallback;
}

export const reportName = (code: string) => REPORT_LABELS[code] ?? humanize(code);
