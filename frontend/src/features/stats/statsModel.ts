// Explorador estadistico (0.8.0, D38, gate E12; DISENO 10.3): logica pura de Panorama, Informes y Explorar. El servidor
// (POST /api/stats/query, contrato en docs/CONTRATO_STATS_0_8_0.md) cuenta, agrega, calcula margenes y porcentajes; aqui
// solo se arma la peticion, se lee la respuesta y se redactan los textos. Nada se suma en el cliente: los margenes y el
// total vienen del servidor y una celda que falta en un cruce vale cero.
import type {Row} from '../../lib/rows.ts';
import {list, text} from '../../lib/rows.ts';
import {countryNames, communesOf, regionCodes} from '../../lib/person/catalog.ts';

// --- Catalogo del motor (/api/catalog -> stats) -----------------------------------------------------------------------
export type Option = {code: string; label: string};
export type StatDim = {code: string; label: string; type: string; cardinality: 'MULTIPLE' | 'UNICA'; catalog: string | null; filterable: boolean;
  measures: string[] | null; options: Option[]};
export type StatMeasure = {code: string; label: string; unit: string; percent_unit: string; needs_multiple: boolean; default_date_basis: string};
export type StatsCatalog = {enabled: boolean; max_cells: number; measures: StatMeasure[]; dimensions: StatDim[]; special: Option[]; periods: string[];
  date_basis: string[]; time_modes: string[]; compares: string[]; percents: string[]};

export function statsCatalog(catalog: Row): StatsCatalog | null {
  const s = catalog.stats as Row | undefined;
  if (!s || typeof s !== 'object') return null;
  return {
    enabled: s.enabled !== false, max_cells: Number(s.max_cells ?? 0),
    measures: list(s, 'measures') as unknown as StatMeasure[],
    dimensions: list(s, 'dimensions').map(d => ({...d, options: list(d, 'options') as unknown as Option[]})) as unknown as StatDim[],
    special: list(s, 'special') as unknown as Option[],
    periods: (s.periods as string[]) ?? [], date_basis: (s.date_basis as string[]) ?? [], time_modes: (s.time_modes as string[]) ?? [],
    compares: (s.compares as string[]) ?? [], percents: (s.percents as string[]) ?? [],
  };
}
export const dimOf = (cat: StatsCatalog, code: string) => cat.dimensions.find(d => d.code === code);
export const measureOf = (cat: StatsCatalog, code: string) => cat.measures.find(m => m.code === code);
export const isMultiple = (cat: StatsCatalog, code: string) => dimOf(cat, code)?.cardinality === 'MULTIPLE';
export const isPeriodDim = (code: string) => code.startsWith('PERIODO_');

// --- Formato chileno -----------------------------------------------------------------------------------------------
const group = (digits: string) => digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
/** Miles con punto (tambien 1.234, sin depender de la regla de agrupacion del navegador) y sin decimales. */
export const fmtInt = (n: number) => (n < 0 ? '−' : '') + group(String(Math.round(Math.abs(n))));
/** Un decimal a lo mas, con coma: 12,5; 12. */
export function fmtDecimal(n: number) {
  const r = Math.round(Math.abs(n) * 10) / 10;
  const [i, d] = r.toFixed(1).split('.');
  return (n < 0 && r > 0 ? '−' : '') + group(i) + (d === '0' ? '' : ',' + d);
}
/** Porcentaje con coma decimal y un decimal a lo mas: "12,5 %". Nulo = sin denominador. */
export const fmtPct = (p: number | null | undefined) => p == null || !Number.isFinite(p) ? '—' : fmtDecimal(p) + ' %';
/** Diferencia con signo: "+3", "−2" o "0". */
export const fmtSigned = (n: number) => n > 0 ? '+' + fmtInt(n) : n < 0 ? '−' + fmtInt(-n) : '0';
export const fmtPoints = (n: number) => (n > 0 ? '+' : n < 0 ? '−' : '') + fmtDecimal(Math.abs(n)) + (Math.abs(n) === 1 ? ' punto porcentual' : ' puntos porcentuales');

/**
 * Variacion respecto de la base, en palabras y sin depender del color: con base 0 no hay variacion porcentual (nunca
 * infinito ni 100 %): "sin base de comparación".
 */
export function variation(deltaN: number, deltaPercent: number | null | undefined, base: number) {
  const direction = deltaN > 0 ? 'up' : deltaN < 0 ? 'down' : 'same';
  const word = direction === 'up' ? 'Aumenta' : direction === 'down' ? 'Disminuye' : 'Sin cambio';
  const arrow = direction === 'up' ? '↑' : direction === 'down' ? '↓' : '→';
  if (base === 0) return {direction, arrow, text: deltaN === 0 ? 'Sin cambio; sin base de comparación' : `${word} ${fmtSigned(deltaN)}; sin base de comparación`};
  return {direction, arrow, text: direction === 'same' ? 'Sin cambio (0 %)' : `${word} ${fmtSigned(deltaN)} (${deltaPercent == null ? '—' : (deltaN > 0 ? '+' : '−') + fmtPct(Math.abs(deltaPercent))})`};
}

// --- Fechas en hora de Chile ----------------------------------------------------------------------------------------
export const ZONE = 'America/Santiago';
/** Dia civil de Chile de un instante (nunca la zona del navegador). */
export const chileDay = (now: Date = new Date()) => new Intl.DateTimeFormat('sv-SE', {timeZone: ZONE}).format(now);
/** Hora y minutos de Chile de un instante. */
export const chileTime = (ms: number) => new Intl.DateTimeFormat('es-CL', {timeZone: ZONE, hour: '2-digit', minute: '2-digit', hour12: false}).format(new Date(ms));
export const chileDateTime = (ms: number) => new Intl.DateTimeFormat('es-CL', {timeZone: ZONE, dateStyle: 'long', timeStyle: 'short'}).format(new Date(ms));
/** Suma dias a un dia civil aaaa-mm-dd, sin pasar por zonas horarias. */
export function addDays(day: string, n: number) {
  const [y, m, d] = day.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + n));
  return t.toISOString().slice(0, 10);
}
const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
/** "1 de julio de 2025". */
export function longDay(day: string) {
  const [y, m, d] = day.split('-').map(Number);
  return `${d} de ${MONTHS[m - 1]} de ${y}`;
}
export type Period = {type: string; from: string; to?: string; basis?: string};
/** Cuenta Publica (julio a junio) que contiene el dia. */
export const publicYearOf = (day: string) => {const [y, m] = day.split('-').map(Number); return m >= 7 ? y : y - 1;};
export const publicAccount = (startYear: number): Period => ({type: 'CUENTA_PUBLICA', from: `${startYear}-07-01`, to: `${startYear + 1}-06-30`});

/**
 * Periodo por defecto del Panorama (DISENO 10.3; GO de E12, B): la Cuenta Publica en curso hasta ayer, como PERSONALIZADO,
 * con "ayer" en hora de Chile. El 1 de julio la Cuenta Publica en curso aun no tiene dias: se muestra la recien terminada,
 * completa y rotulada como tal.
 */
export function panoramaPeriod(today: string): {period: Period; label: string} {
  const year = publicYearOf(today);
  if (today === `${year}-07-01`) {
    const p = publicAccount(year - 1);
    return {period: p, label: `Cuenta Pública ${year - 1}-${year}, recién terminada (${longDay(p.from)} al ${longDay(p.to!)})`};
  }
  const p: Period = {type: 'PERSONALIZADO', from: `${year}-07-01`, to: addDays(today, -1)};
  return {period: p, label: `Cuenta Pública ${year}-${year + 1} en curso: ${longDay(p.from)} al ${longDay(p.to!)}`};
}
/** Rotulo de un periodo canonico del servidor ({type, from, to}). */
export function periodLabel(p: Row | Period | undefined) {
  if (!p) return '';
  const from = text(p as Row, 'from'), to = text(p as Row, 'to'), type = text(p as Row, 'type');
  if (type === 'CUENTA_PUBLICA' && from.endsWith('-07-01')) return `Cuenta Pública ${from.slice(0, 4)}-${Number(from.slice(0, 4)) + 1}`;
  if (!to || from === to) return longDay(from);
  return `${longDay(from)} al ${longDay(to)}`;
}

// --- Consulta ----------------------------------------------------------------------------------------------------
export type Query = {
  measure: string; rows: string[]; columns: string[]; filters: Record<string, string[]>; period: Period;
  mode: string; asOf: string; compare: string; percent: string; proposals: boolean; mention: string;
};
export const emptyQuery = (period: Period): Query => ({measure: 'INGRESOS', rows: [], columns: [], filters: {}, period, mode: 'VIGENTE', asOf: '', compare: '', percent: '', proposals: false, mention: ''});

/** "aaaa-mm-ddThh:mm" de Chile -> ISO con el desplazamiento de Chile en ese instante. */
export function chileIso(local: string) {
  if (!local) return null;
  const [day, time = '00:00'] = local.split('T');
  // Instante en que ese reloj de Chile marca esa hora: se prueba con los dos desplazamientos posibles (-3 y -4).
  for (const offset of ['-03:00', '-04:00']) {
    const iso = `${day}T${time}:00${offset}`;
    const t = new Date(iso);
    const shown = new Intl.DateTimeFormat('sv-SE', {timeZone: ZONE, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false}).format(t).replace(' ', 'T');
    if (shown === `${day}T${time}`) return iso;
  }
  return `${day}T${time}:00-03:00`;
}

/** Cuerpo de POST /api/stats/query (lista cerrada del contrato, seccion 2). */
export function requestBody(q: Query, cat?: StatsCatalog | null): Row {
  const filters = Object.fromEntries(Object.entries(q.filters).filter(([, v]) => v.length > 0).sort(([a], [b]) => a.localeCompare(b)));
  const basis = q.period.basis || (cat ? measureOf(cat, q.measure)?.default_date_basis : undefined) || 'RECEPCION';
  const period: Row = {type: q.period.type, from: q.period.from, date_basis: basis};
  if (q.period.to) period.to = q.period.to;
  return {
    measure: q.measure, rows: q.rows, columns: q.columns, filters, period,
    time_mode: q.mode || 'VIGENTE', as_of: q.mode === 'FECHA' ? chileIso(q.asOf) : null, cut_id: null,
    compare: q.compare ? {type: q.compare} : null, percent: q.percent || null, include_proposals: q.proposals,
    mention_dimension: q.mention || null,
  };
}
/** Texto canonico (claves ordenadas): la misma consulta da siempre la misma clave de cache. */
export function canonical(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical((value as Row)[k])).join(',') + '}';
  return JSON.stringify(value ?? null);
}

/** Consulta de una vista guardada (su definicion es un cuerpo de POST /api/stats/query). */
export function fromBody(def: Row): Query {
  const period = (def.period ?? {}) as Row;
  const asOf = text(def, 'as_of');
  return {
    measure: text(def, 'measure'), rows: ((def.rows as string[]) ?? []).slice(), columns: ((def.columns as string[]) ?? []).slice(),
    filters: Object.fromEntries(Object.entries((def.filters ?? {}) as Record<string, unknown[]>).map(([k, v]) => [k, (v ?? []).map(String)])),
    period: {type: text(period, 'type'), from: text(period, 'from'), ...(text(period, 'to') ? {to: text(period, 'to')} : {}), ...(text(period, 'date_basis') ? {basis: text(period, 'date_basis')} : {})},
    mode: text(def, 'time_mode') || 'VIGENTE', asOf: asOf ? chileLocal(Date.parse(asOf)) : '',
    compare: text((def.compare ?? {}) as Row, 'type'), percent: text(def, 'percent'), proposals: def.include_proposals === true, mention: text(def, 'mention_dimension'),
  };
}
/** Instante -> "aaaa-mm-ddThh:mm" en hora de Chile. */
export const chileLocal = (ms: number) => new Intl.DateTimeFormat('sv-SE', {timeZone: ZONE, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false})
  .format(new Date(ms)).replace(' ', 'T');
/** Primer dia del periodo predefinido que contiene el dia elegido (el servidor identifica el periodo por su inicio). */
export function periodStart(type: string, day: string) {
  const [y, m] = day.split('-').map(Number);
  const pad = (n: number) => String(n).padStart(2, '0');
  switch (type) {
    case 'MES': return `${y}-${pad(m)}-01`;
    case 'TRIMESTRE': return `${y}-${pad(Math.floor((m - 1) / 3) * 3 + 1)}-01`;
    case 'SEMESTRE': return `${y}-${m <= 6 ? '01' : '07'}-01`;
    case 'ANIO': return `${y}-01-01`;
    case 'CUENTA_PUBLICA': return `${publicYearOf(day)}-07-01`;
    default: return day;
  }
}

// --- Direccion (#estadisticas?...) -----------------------------------------------------------------------------------
/**
 * La consulta queda en la direccion para volver atras y compartir. Solo lleva codigos de medidas, dimensiones, periodos,
 * modos y valores de catalogo; nunca datos personales ni el texto del buscador de campos (que no es parte de Query).
 */
export const SECTIONS = ['panorama', 'informes', 'explorar'] as const;
export type Section = typeof SECTIONS[number];
export function encodeRoute(section: Section, q?: Query, extra: Record<string, string> = {}) {
  const p = new URLSearchParams();
  if (section !== 'panorama') p.set('s', section);
  if (q) {
    p.set('m', q.measure);
    if (q.rows.length) p.set('r', q.rows.join(','));
    if (q.columns.length) p.set('c', q.columns.join(','));
    const filters = Object.entries(q.filters).filter(([, v]) => v.length);
    if (filters.length) p.set('f', JSON.stringify(Object.fromEntries(filters)));
    p.set('p', q.period.type);
    p.set('d', q.period.from);
    if (q.period.to) p.set('h', q.period.to);
    if (q.period.basis) p.set('b', q.period.basis);
    if (q.mode && q.mode !== 'VIGENTE') p.set('t', q.mode);
    if (q.mode === 'FECHA' && q.asOf) p.set('a', q.asOf);
    if (q.compare) p.set('k', q.compare);
    if (q.percent) p.set('pc', q.percent);
    if (q.proposals) p.set('pr', '1');
    if (q.mention) p.set('md', q.mention);
  }
  for (const [k, v] of Object.entries(extra)) if (v) p.set(k, v);
  const s = p.toString();
  return 'estadisticas' + (s ? '?' + s : '');
}
const CODE = /^[A-Z][A-Z0-9_]{0,63}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;
/** Lee la direccion. Lo que no es un codigo o una fecha se descarta (una direccion alterada no llega al servidor). */
export function decodeRoute(route: string): {section: Section; query: Query | null; params: URLSearchParams} {
  const params = new URLSearchParams(route.split('?')[1] ?? '');
  const s = params.get('s') ?? '';
  const section: Section = (SECTIONS as readonly string[]).includes(s) ? s as Section : 'panorama';
  const m = params.get('m') ?? '', d = params.get('d') ?? '', type = params.get('p') ?? '';
  if (!CODE.test(m) || !DAY.test(d) || !CODE.test(type)) return {section, query: null, params};
  const codes = (k: string) => (params.get(k) ?? '').split(',').filter(x => CODE.test(x));
  let filters: Record<string, string[]> = {};
  try {
    const raw = JSON.parse(params.get('f') ?? '{}') as Row;
    filters = Object.fromEntries(Object.entries(raw).filter(([k, v]) => CODE.test(k) && Array.isArray(v))
      .map(([k, v]) => [k, (v as unknown[]).filter(x => typeof x === 'string' && x.length > 0 && x.length <= 120) as string[]]));
  } catch {
    filters = {};
  }
  const h = params.get('h') ?? '', b = params.get('b') ?? '', t = params.get('t') ?? '', a = params.get('a') ?? '';
  const k = params.get('k') ?? '', pc = params.get('pc') ?? '', md = params.get('md') ?? '';
  return {section, params, query: {
    measure: m, rows: codes('r'), columns: codes('c'), filters,
    period: {type, from: d, ...(DAY.test(h) ? {to: h} : {}), ...(CODE.test(b) ? {basis: b} : {})},
    mode: CODE.test(t) ? t : 'VIGENTE', asOf: /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(a) ? a : '',
    compare: CODE.test(k) ? k : '', percent: CODE.test(pc) ? pc : '', proposals: params.get('pr') === '1', mention: CODE.test(md) ? md : '',
  }};
}

// --- Cola de consultas y cache ---------------------------------------------------------------------------------------
export class Cancelled extends Error {constructor() {super('Consulta cancelada.'); this.name = 'Cancelled';}}
export type Fetched = {data: Row; at: number; cached: boolean};
/** Vida maxima de un resultado en la cache (GO de E12, condicion 1). */
export const CACHE_TTL = 5 * 60 * 1000;
/**
 * Una consulta a la vez por cuenta (el servidor admite una: StatGuard), con cache de la sesion por cuerpo canonico. Las
 * pendientes se cancelan al cambiar de seccion; la que esta en curso termina y deja su resultado en la cache.
 */
export function createQueue(send: (body: Row) => Promise<Row>, now: () => number = Date.now) {
  const cache = new Map<string, {data: Row; at: number}>();
  type Job = {key: string; task: () => Promise<Row>; resolve: (f: Fetched) => void; reject: (e: unknown) => void; store: boolean};
  const pending: Job[] = [];
  const inflight = new Map<string, Promise<Fetched>>();
  let running = false, owner = '';
  function pump() {
    if (running) return;
    const job = pending.shift();
    if (!job) return;
    running = true;
    void job.task().then(data => {
      const at = now();
      if (job.store) cache.set(job.key, {data, at});
      job.resolve({data, at, cached: false});
    }, job.reject).finally(() => {running = false; pump();});
  }
  const fresh = (key: string) => {
    const hit = cache.get(key);
    if (hit && now() - hit.at < CACHE_TTL) return hit;
    if (hit) cache.delete(key);
    return null;
  };
  return {
    /** Consulta con cache; force la pide de nuevo. user separa la cache entre cuentas. */
    query(body: Row, user: string, force = false): Promise<Fetched> {
      if (user !== owner) {cache.clear(); inflight.clear(); owner = user;}
      const key = canonical(body);
      const hit = force ? null : fresh(key);
      if (hit) return Promise.resolve({data: hit.data, at: hit.at, cached: true});
      // La misma consulta ya en la fila (dos tarjetas que piden lo mismo): una sola peticion.
      const same = inflight.get(key);
      if (same && !force) return same;
      const promise = new Promise<Fetched>((resolve, reject) => {pending.push({key, task: () => send(body), resolve, reject, store: true}); pump();});
      inflight.set(key, promise);
      const done = () => {if (inflight.get(key) === promise) inflight.delete(key);};
      promise.then(done, done);
      return promise;
    },
    /** Otra operacion del cupo (descarga): en la misma fila y sin cache. */
    run(task: () => Promise<unknown>): Promise<void> {
      return new Promise((resolve, reject) => {pending.push({key: '', task: async () => {await task(); return {};}, resolve: () => resolve(), reject, store: false}); pump();});
    },
    /** Cancela las pendientes (no la que esta en curso). */
    cancelPending() {
      for (const job of pending.splice(0)) job.reject(new Cancelled());
    },
    clear() {cache.clear(); inflight.clear();},
    get busy() {return running;},
    get waiting() {return pending.length;},
  };
}
export type Queue = ReturnType<typeof createQueue>;
const BUSY = 'Hay otra consulta estadística en curso. Espera a que termine y vuelve a intentarlo.';
const TIMEOUT = 'La consulta tardó demasiado y se canceló sin resultados parciales. Acota el periodo o quita campos y vuelve a intentarlo.';
/**
 * Mensaje de un rechazo: el del servidor, que ya viene en lenguaje llano (429 sin cupo, 503 por plazo o motor
 * deshabilitado, 409 por el limite de celdas); si no trae, uno propio para 429 y 503.
 */
export function errorText(e: {status?: number; message?: string}) {
  const server = (e.message ?? '').trim();
  if (server && !/^No se pudo confirmar la operacion|^Demasiados intentos/.test(server)) return server;
  if (e.status === 429) return BUSY;
  if (e.status === 503) return TIMEOUT;
  return server || 'No se pudo calcular la consulta. Vuelve a intentarlo.';
}

// --- Familias de campos y textos de ayuda ---------------------------------------------------------------------------
export const FAMILIES: {key: string; label: string; codes: string[]}[] = [
  {key: 'periodo', label: 'Periodo', codes: ['PERIODO_DIA', 'PERIODO_MES', 'PERIODO_TRIMESTRE', 'PERIODO_SEMESTRE', 'PERIODO_ANIO', 'PERIODO_CUENTA_PUBLICA', 'TARDANZA']},
  {key: 'ingreso', label: 'Ingreso', codes: ['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO', 'VIA_AGRUPACION', 'CANAL', 'MECANISMO', 'MODALIDAD']},
  {key: 'territorio', label: 'Territorio', codes: ['REGION_REQUIRENTE', 'COMUNA_REQUIRENTE', 'REGION_ORIGEN', 'REGION_NNA', 'COMUNA_NNA', 'REGION_HECHOS', 'COMUNA_HECHOS',
    'ADSCRIPCION_INICIAL', 'SEDE_INICIAL', 'SEDE_VIGENTE', 'AMBITO', 'TERRITORIO_COBERTURA', 'REGION_SEDE', 'UNIDAD', 'NIVEL']},
  {key: 'derechos', label: 'Derechos', codes: ['DERECHO_EJE', 'DERECHO', 'DERECHO_SUBCATEGORIA', 'CONTEXTO', 'CARACTERIZACION', 'ROL_ESTADO', 'GRUPO_PRIORITARIO']},
  {key: 'actuaciones', label: 'Actuaciones', codes: ['TIPO_ACTUACION', 'ORIGEN_ACTUACION', 'TIPO_INSTITUCION', 'RESULTADO_PRIMERA_ATENCION']},
  {key: 'personas', label: 'Personas', codes: ['EDAD_NNA', 'TRAMO_EDAD_NNA', 'NACIONALIDAD_NNA', 'GRUPO_NACIONALIDAD_NNA', 'EDAD_REQUIRENTE', 'TRAMO_EDAD_REQUIRENTE',
    'NACIONALIDAD_REQUIRENTE', 'GRUPO_NACIONALIDAD_REQUIRENTE']},
  {key: 'cadena', label: 'Estado de la cadena', codes: ['ESTADO_COM', 'ESTADO_SOL', 'ESTADO_REQ', 'ORIGEN_REQ', 'MATERIA', 'COMPLEJIDAD']},
  {key: 'calidad', label: 'Calidad', codes: ['ESTADO_TIPO_SOLICITUD', 'ESTADO_TIPO_REQUIRENTE', 'ESTADO_VIA_INGRESO', 'ESTADO_DERECHO', 'ESTADO_CONTEXTO', 'ESTADO_CARACTERIZACION',
    'ESTADO_ROL_ESTADO', 'ESTADO_GRUPO_PRIORITARIO', 'VARIABLE', 'ESTADO_VARIABLE']},
];
/** Familias con las dimensiones que publica el servidor; una dimension nueva sin familia va a "Otros campos". */
export function families(cat: StatsCatalog) {
  const known = new Set(FAMILIES.flatMap(f => f.codes));
  const out = FAMILIES.map(f => ({...f, dims: f.codes.map(c => dimOf(cat, c)).filter((d): d is StatDim => !!d)}));
  const rest = cat.dimensions.filter(d => !known.has(d.code));
  if (rest.length) out.push({key: 'otros', label: 'Otros campos', codes: rest.map(d => d.code), dims: rest});
  return out.filter(f => f.dims.length > 0);
}
const DIM_HELP: Record<string, string> = {
  PERIODO_DIA: 'Cada día del periodo, en hora de Chile.', PERIODO_MES: 'Cada mes del periodo.', PERIODO_TRIMESTRE: 'Cada trimestre del periodo.',
  PERIODO_SEMESTRE: 'Cada semestre del periodo.', PERIODO_ANIO: 'Cada año calendario del periodo.', PERIODO_CUENTA_PUBLICA: 'Cada periodo de julio a junio.',
  TARDANZA: 'Si el ingreso se registró después del fin del periodo en que se recibió.',
  TIPO_SOLICITUD: 'Clasificación institucional: vulneración, consulta, promoción, reclamo u otra.', TIPO_REQUIRENTE: 'Quién presenta el requerimiento, según la clasificación institucional.',
  VIA_INGRESO: 'Por dónde llegó el requerimiento.', VIA_AGRUPACION: 'Las vías de ingreso agrupadas: digital, presencial, telefónica, documental, interna u otra.',
  CANAL: 'Canal registrado originalmente en el ingreso.', MECANISMO: 'Formulario Público, atención directa o registro manual.', MODALIDAD: 'Modalidad de la primera atención.',
  REGION_REQUIRENTE: 'Región declarada de quien presenta.', COMUNA_REQUIRENTE: 'Comuna declarada de quien presenta.', REGION_ORIGEN: 'Región de origen registrada en el ingreso.',
  REGION_NNA: 'Región de cada NNA afectado: un requerimiento con NNA de dos regiones cuenta en ambas.', COMUNA_NNA: 'Comuna de cada NNA afectado.',
  REGION_HECHOS: 'Región donde ocurrieron los hechos.', COMUNA_HECHOS: 'Comuna donde ocurrieron los hechos.',
  ADSCRIPCION_INICIAL: 'La sede regional u Oficina Nacional a la que se adscribió primero el ingreso.', SEDE_INICIAL: 'Primer ámbito responsable del ingreso.',
  SEDE_VIGENTE: 'Ámbito responsable hoy.', AMBITO: 'Ámbito de referencia vigente del requerimiento.', TERRITORIO_COBERTURA: 'Quién atendía la región de origen al recibir el ingreso.',
  REGION_SEDE: 'Región de la sede responsable.', UNIDAD: 'Unidad del ámbito responsable.', NIVEL: 'Nivel central o sede regional.',
  DERECHO_EJE: 'Eje del modelo de derechos.', DERECHO: 'Derecho afectado, del modelo vigente.', DERECHO_SUBCATEGORIA: 'Subcategoría del derecho afectado.',
  CONTEXTO: 'Contexto en que ocurre la situación.', CARACTERIZACION: 'Caracterización de la vulneración.', ROL_ESTADO: 'Rol del Estado en la situación.',
  GRUPO_PRIORITARIO: 'Grupos prioritarios de la población afectada.', TIPO_ACTUACION: 'Tipo de actuación institucional registrada.',
  ORIGEN_ACTUACION: 'Si la actuación es de la primera atención, de la gestión del requerimiento o de un Caso.',
  TIPO_INSTITUCION: 'Tipo de institución a la que se dirige la actuación.', RESULTADO_PRIMERA_ATENCION: 'Resultado de la primera atención resuelta en el acto.',
  EDAD_NNA: 'Edad del NNA afectado a la fecha de recepción.', TRAMO_EDAD_NNA: 'Tramo de edad del NNA afectado.', NACIONALIDAD_NNA: 'País de nacionalidad del NNA afectado.',
  GRUPO_NACIONALIDAD_NNA: 'Chile o extranjera.', EDAD_REQUIRENTE: 'Edad de quien presenta, si es persona natural.', TRAMO_EDAD_REQUIRENTE: 'Tramo de edad de quien presenta.',
  NACIONALIDAD_REQUIRENTE: 'País de nacionalidad de quien presenta.', GRUPO_NACIONALIDAD_REQUIRENTE: 'Chile o extranjera.',
  ESTADO_COM: 'Estado de la comunicación recibida.', ESTADO_SOL: 'Estado de la solicitud a jefatura.', ESTADO_REQ: 'Abierto o cerrado.',
  ORIGEN_REQ: 'Si el requerimiento nació de una solicitud a jefatura o de una atención directa.', MATERIA: 'Materia del requerimiento.', COMPLEJIDAD: 'Complejidad asignada al requerimiento.',
  VARIABLE: 'Cada variable de la completitud.', ESTADO_VARIABLE: 'Informado, pendiente o un estado especial.',
};
export const dimHelp = (d: StatDim) => DIM_HELP[d.code] ?? (d.code.startsWith('ESTADO_') ? 'Si la clasificación está informada, pendiente o en un estado especial.' : '');
const MEASURE_HELP: Record<string, string> = {
  INGRESOS: 'Cuenta requerimientos recibidos (una comunicación por requerimiento). No cuenta los inválidos ni los vinculados como antecedente.',
  EXCLUIDOS: 'Cuenta las comunicaciones descartadas como inválidas. Es informativa: no son requerimientos.',
  ANTECEDENTES: 'Cuenta las comunicaciones vinculadas como antecedente de otro requerimiento. Es informativa.',
  REQ_CONSTITUIDOS: 'Cuenta los requerimientos constituidos (REQ), uno por comunicación.',
  CASOS: 'Cuenta Casos: un requerimiento puede tener varios.',
  ACTUACIONES: 'Cuenta actuaciones tipificadas vigentes y el resultado de las primeras atenciones; las constancias reservadas no se cuentan.',
  REQUERIMIENTOS_CON_ACTUACION: 'Cuenta requerimientos con al menos una actuación.',
  CLASIFICACIONES_DERECHO: 'Cuenta cada derecho asignado en un requerimiento o en sus Casos.',
  REQUERIMIENTOS_CON_DERECHO: 'Cuenta requerimientos con al menos un derecho clasificado.',
  REQUERIMIENTOS_CON_VALOR: 'Cuenta requerimientos que tienen cada valor; un requerimiento puede tener varios valores.',
  MENCIONES: 'Cuenta cada valor de cada requerimiento: un requerimiento con tres valores aporta tres menciones.',
  COMPLETITUD: 'Cuenta, para cada requerimiento y variable, si la variable está informada, pendiente o en un estado especial.',
};
export const measureHelp = (code: string) => MEASURE_HELP[code] ?? '';
export const UNIT_LABELS: Record<string, string> = {COM: 'requerimientos', ACTUACION: 'actuaciones', CLASIFICACION: 'clasificaciones', CASO: 'Casos', VARIABLE: 'pares de requerimiento y variable'};
export const BASIS_LABELS: Record<string, string> = {RECEPCION: 'Fecha de recepción', REGISTRO: 'Fecha de registro', CONSTITUCION_REQ: 'Fecha de constitución del REQ', CREACION_CASO: 'Fecha de creación del Caso'};
export const MODE_LABELS: Record<string, string> = {VIGENTE: 'Valores vigentes', AL_INGRESO: 'Valores al ingreso', AL_REQ: 'Valores al constituirse el REQ', FECHA: 'Valores a una fecha'};
export const MODE_HELP: Record<string, string> = {VIGENTE: 'El último valor registrado.', AL_INGRESO: 'El primer valor registrado de cada ingreso.',
  AL_REQ: 'El valor vigente al constituirse el requerimiento.', FECHA: 'El valor vigente en la fecha y hora elegidas.'};
export const COMPARE_LABELS: Record<string, string> = {PERIODO_ANTERIOR: 'Periodo anterior', MISMO_PERIODO_ANIO_ANTERIOR: 'Mismo periodo del año anterior'};
export const PERCENT_LABELS: Record<string, string> = {'': 'Cantidad', TOTAL: '% del total', FILA: '% por fila', COLUMNA: '% por columna'};
export const PERIOD_LABELS: Record<string, string> = {PERSONALIZADO: 'Personalizado', MES: 'Mes', TRIMESTRE: 'Trimestre', SEMESTRE: 'Semestre', ANIO: 'Año calendario', CUENTA_PUBLICA: 'Cuenta Pública (julio a junio)'};
export const MULTIPLE_NOTE = 'Un requerimiento puede tener varios valores.';
export const NON_ADDITIVE = 'no aditiva';
export const NON_ADDITIVE_NOTE = 'La región de los NNA es no aditiva: un requerimiento con NNA de varias regiones cuenta en cada una, por lo que las regiones suman más que el total.';
export const HIERARCHY_NOTE = 'Los niveles inferiores no suman el superior: un requerimiento con dos subcategorías del mismo derecho cuenta una vez en el derecho.';
export const NON_ADDITIVE_DIMS = ['REGION_NNA', 'COMUNA_NNA'];

/** Rotulo de un campo con sus marcas: "valores múltiples" y, en la region de los NNA, "no aditiva". */
export function fieldLabel(cat: StatsCatalog, code: string) {
  const d = dimOf(cat, code);
  const label = d?.label ?? 'Campo';
  return NON_ADDITIVE_DIMS.includes(code) ? `${label} (${NON_ADDITIVE})` : label;
}

/** Si un campo cabe con la medida: algunas dimensiones solo valen con su propia medida (actuaciones, completitud). */
export function fits(cat: StatsCatalog, measure: string, code: string) {
  const d = dimOf(cat, code);
  return !!d && (d.measures == null || d.measures.includes(measure));
}
export const multipleIn = (cat: StatsCatalog, q: Query) => [...q.rows, ...q.columns].filter(c => isMultiple(cat, c));
/** Lo que impide enviar una consulta, en lenguaje llano (el servidor valida igual). */
export function queryProblem(cat: StatsCatalog, q: Query) {
  const m = measureOf(cat, q.measure);
  if (!m) return 'Elige qué contar.';
  for (const c of [...q.rows, ...q.columns, ...Object.keys(q.filters)]) if (!fits(cat, q.measure, c)) return `«${dimOf(cat, c)?.label ?? 'Ese campo'}» no se puede usar con «${m.label}». Elige otra medida o quita el campo.`;
  if (m.needs_multiple && multipleIn(cat, q).length === 0) return `«${m.label}» necesita un campo de valores múltiples en filas o columnas (por ejemplo, Derecho o Contexto).`;
  if (m.code === 'MENCIONES' && multipleIn(cat, q).length > 1 && !q.mention) return 'Con dos campos de valores múltiples, elige de cuál se cuentan las menciones.';
  if (q.mode === 'FECHA' && !q.asOf) return 'Elige la fecha y hora de los valores.';
  if (q.period.type === 'PERSONALIZADO' && (!q.period.to || q.period.to < q.period.from)) return 'Revisa el periodo: la fecha final no puede ser anterior a la inicial.';
  return '';
}

// --- Filtros -------------------------------------------------------------------------------------------------------
/** Valores que ofrece el filtro de un campo, desde /api/catalog: opciones del motor, catalogos vigentes, regiones, ambitos. */
export function filterValues(cat: StatsCatalog, catalog: Row, code: string): Option[] {
  const d = dimOf(cat, code);
  if (!d) return [];
  const specials = cat.special;
  let own: Option[] = [];
  switch (d.type) {
    case 'LISTA': own = d.options; break;
    case 'CATALOGO': own = vigentItems(catalog, d.catalog ?? '').filter(i => code !== 'VIA_AGRUPACION' ? i.selectable : !i.selectable).map(i => ({code: i.code, label: i.label})); break;
    case 'REGION': own = [...regionsOf(catalog), ...d.options]; break;
    case 'AMBITO': own = [...d.options, ...list(catalog, 'scopes').map(s => ({code: text(s, 'code'), label: text(s, 'name')}))]; break;
    case 'UNIDAD': own = list(catalog, 'units').map(u => ({code: text(u, 'code'), label: text(u, 'name')})); break;
    case 'COMUNA': own = [...new Set(regionCodes.flatMap(r => communesOf(r)))].sort((a, b) => a.localeCompare(b, 'es')).map(c => ({code: c, label: c})); break;
    case 'PAIS': own = countryNames.map(c => ({code: c, label: c})); break;
    case 'EDAD': own = Array.from({length: 131}, (_, i) => ({code: String(i), label: i === 1 ? '1 año' : i + ' años'})); break;
    default: own = d.options;
  }
  const seen = new Set(own.map(o => o.code));
  return [...own, ...specials.filter(s => !seen.has(s.code) && s.code !== 'SIN_DESGLOSE')];
}
export const regionsOf = (catalog: Row): Option[] => list(catalog, 'regions').map(r => ({code: text(r, 'code'), label: text(r, 'name')}));
type Item = {code: string; label: string; parent: string; selectable: boolean; description: string};
/** Items del catalogo estadistico VIGENTE (lo unico que publica /api/catalog), en su orden. */
export function vigentItems(catalog: Row, name: string): Item[] {
  const all = list(catalog.stat_catalogs as Row ?? {}, 'catalogs');
  const c = all.find(x => text(x, 'catalog') === name);
  if (!c || !c.version) return [];
  return list(c, 'items').map(i => ({code: text(i, 'code'), label: text(i, 'label'), parent: text(i, 'parent_code'), selectable: i.selectable !== false, description: text(i, 'description')}));
}

/**
 * Filtros de "caracterizacion completa" (DISENO 10.3; GO de E12, A): las seis variables de completitud de E07 sin
 * PENDIENTE. Cada lista sale de las opciones de /api/catalog (los estados del motor; en la region, todas las regiones mas
 * los estados especiales) quitando solo PENDIENTE: nada escrito a mano.
 */
export const COMPLETE_STATES = ['ESTADO_TIPO_SOLICITUD', 'ESTADO_TIPO_REQUIRENTE', 'ESTADO_VIA_INGRESO', 'ESTADO_DERECHO', 'ESTADO_CONTEXTO'];
export function completeFilters(cat: StatsCatalog, catalog: Row): Record<string, string[]> | null {
  const out: Record<string, string[]> = {};
  for (const code of COMPLETE_STATES) {
    const d = dimOf(cat, code);
    if (!d) return null;
    out[code] = d.options.map(o => o.code).filter(c => c !== 'PENDIENTE');
  }
  if (!dimOf(cat, 'REGION_REQUIRENTE')) return null;
  out.REGION_REQUIRENTE = filterValues(cat, catalog, 'REGION_REQUIRENTE').map(o => o.code).filter(c => c !== 'PENDIENTE');
  return out;
}

// --- Lectura de la respuesta ---------------------------------------------------------------------------------------
export type Value = {key: string; label: string};
export type Cell = {row: string[]; column: string[]; n: number; unique: number; mentions: number | null; percent: number | null; denominator: number | null};
export type Delta = {base_n: number; delta_n: number; delta_percent: number | null};
export type Result = {values: Record<string, Value[]>; cells: Cell[]; grand: {n: number; unique: number; mentions: number | null};
  rowTotals: Map<string, {n: number; unique: number; mentions: number | null}>; colTotals: Map<string, {n: number; unique: number; mentions: number | null}>;
  comparison: {cells: Map<string, Delta>; grand: Delta | null; rows: Map<string, Delta>; columns: Map<string, Delta>; period: Row | null} | null;
  metadata: Row; warnings: Row[]; late: Row | null; coverage: Row; proposals: Row[]};
export const keyOf = (parts: string[]) => parts.join('\u0001');
export function readResult(data: Row): Result {
  const totals = (data.totals ?? {}) as Row;
  const map = (rows: Row[]) => new Map(rows.map(r => [keyOf((r.key as string[]) ?? []), {n: Number(r.n ?? 0), unique: Number(r.unique ?? 0), mentions: r.mentions == null ? null : Number(r.mentions)}]));
  const cmp = data.comparison as Row | null;
  const deltas = (rows: Row[], k: (r: Row) => string[]) => new Map(rows.map(r => [keyOf(k(r)), {base_n: Number(r.base_n ?? 0), delta_n: Number(r.delta_n ?? 0), delta_percent: r.delta_percent == null ? null : Number(r.delta_percent)}]));
  const grand = (totals.grand ?? {}) as Row;
  return {
    values: (data.values ?? {}) as Record<string, Value[]>,
    cells: list(data, 'cells').map(c => ({row: (c.row as string[]) ?? [], column: (c.column as string[]) ?? [], n: Number(c.n ?? 0), unique: Number(c.unique ?? 0),
      mentions: c.mentions == null ? null : Number(c.mentions), percent: c.percent == null ? null : Number(c.percent), denominator: c.denominator == null ? null : Number(c.denominator)})),
    grand: {n: Number(grand.n ?? 0), unique: Number(grand.unique ?? 0), mentions: grand.mentions == null ? null : Number(grand.mentions)},
    rowTotals: map(list(totals, 'rows')), colTotals: map(list(totals, 'columns')),
    comparison: cmp ? {
      cells: deltas(list(cmp, 'cells'), r => [...((r.row as string[]) ?? []), ...((r.column as string[]) ?? [])]),
      grand: (cmp.totals as Row)?.grand ? deltas([(cmp.totals as Row).grand as Row], () => []).get('') ?? null : null,
      rows: deltas(list(cmp.totals as Row ?? {}, 'rows'), r => (r.key as string[]) ?? []),
      columns: deltas(list(cmp.totals as Row ?? {}, 'columns'), r => (r.key as string[]) ?? []),
      period: (cmp.period as Row) ?? null,
    } : null,
    metadata: (data.metadata ?? {}) as Row, warnings: list(data, 'warnings'), late: (data.late_records as Row) ?? null,
    coverage: (data.data_coverage ?? {}) as Row, proposals: list(data, 'proposals'),
  };
}

/** Combinaciones de valores de un eje (filas o columnas), en el orden del servidor. Solo las que tienen celdas o margen. */
function axisKeys(r: Result, dims: string[], side: 'row' | 'column'): string[][] {
  if (dims.length === 0) return [[]];
  const present = new Set<string>();
  for (const c of r.cells) present.add(keyOf(c[side]));
  for (const k of (side === 'row' ? r.rowTotals : r.colTotals).keys()) present.add(k);
  const periodOnly = dims.length === 1 && isPeriodDim(dims[0]);
  const combos: string[][] = [];
  const walk = (i: number, acc: string[]) => {
    if (i === dims.length) {if (periodOnly || present.has(keyOf(acc))) combos.push(acc); return;}
    for (const v of r.values[dims[i]] ?? []) walk(i + 1, [...acc, v.key]);
  };
  walk(0, []);
  return combos;
}
export type Metric = 'n' | 'percent' | 'mentions';
export type TableModel = {rowDims: string[]; colDims: string[]; rows: {key: string[]; labels: string[]}[]; columns: {key: string[]; labels: string[]}[];
  value: (row: string[], col: string[]) => Cell | null; rowTotal: (row: string[]) => {n: number; unique: number; mentions: number | null} | null;
  colTotal: (col: string[]) => {n: number; unique: number; mentions: number | null} | null; delta: (row: string[], col: string[]) => Delta | null};
export function labelOf(r: Result, dim: string, key: string) {
  return (r.values[dim] ?? []).find(v => v.key === key)?.label ?? 'Sin rótulo';
}
/**
 * Tabla de una respuesta: filas y columnas con los valores del servidor (solo las categorias que devuelve, con 0 en las
 * celdas vacias del cruce y en los intervalos de periodo), celdas por clave y margenes del servidor (nunca sumados).
 */
export function tableModel(r: Result, rowDims: string[], colDims: string[]): TableModel {
  const cells = new Map(r.cells.map(c => [keyOf([...c.row, ...c.column]), c]));
  const label = (dims: string[], key: string[]) => key.map((k, i) => labelOf(r, dims[i], k));
  return {
    rowDims, colDims,
    rows: axisKeys(r, rowDims, 'row').map(key => ({key, labels: label(rowDims, key)})),
    columns: axisKeys(r, colDims, 'column').map(key => ({key, labels: label(colDims, key)})),
    value: (row, col) => cells.get(keyOf([...row, ...col])) ?? null,
    rowTotal: row => rowDims.length === 0 || row.length === 0 ? r.grand : r.rowTotals.get(keyOf(row)) ?? null,
    colTotal: col => colDims.length === 0 || col.length === 0 ? r.grand : r.colTotals.get(keyOf(col)) ?? null,
    delta: (row, col) => row.length === 0 && col.length === 0 && (rowDims.length > 0 || colDims.length > 0) ? r.comparison?.grand ?? null : r.comparison?.cells.get(keyOf([...row, ...col])) ?? null,
  };
}
/** Cifra de una celda segun lo elegido: cantidad, porcentaje del servidor o menciones. Celda ausente = 0. */
export function cellFigure(c: Cell | null, metric: Metric): {value: number | null; text: string; missing: boolean} {
  if (metric === 'percent') {
    if (!c) return {value: 0, text: fmtPct(0), missing: false};
    return {value: c.percent, text: fmtPct(c.percent), missing: c.percent == null};
  }
  if (metric === 'mentions') {
    const v = c ? c.mentions : 0;
    return {value: v, text: v == null ? '—' : fmtInt(v), missing: v == null};
  }
  return {value: c ? c.n : 0, text: fmtInt(c ? c.n : 0), missing: false};
}
export type Sort = {by: 'label' | 'value'; column: number; dir: 'asc' | 'desc'} | null;
/** Orden de las filas: por rotulo o por la cifra de una columna. Sin orden, el del servidor. */
export function sortRows(t: TableModel, metric: Metric, sort: Sort) {
  if (!sort) return t.rows;
  const sign = sort.dir === 'asc' ? 1 : -1;
  return [...t.rows].sort((a, b) => {
    if (sort.by === 'label') return sign * a.labels.join(' ').localeCompare(b.labels.join(' '), 'es');
    const col = t.columns[sort.column]?.key ?? [];
    const va = cellFigure(t.value(a.key, col), metric).value ?? -1, vb = cellFigure(t.value(b.key, col), metric).value ?? -1;
    return sign * (va - vb);
  });
}

// --- Jerarquia de derechos -------------------------------------------------------------------------------------------
export const RIGHTS_LEVELS = ['DERECHO_EJE', 'DERECHO', 'DERECHO_SUBCATEGORIA'] as const;
export type TreeRow = {key: string; label: string; level: number; dim: string; parent: string; special: boolean};
/**
 * Una consulta por nivel (eje, derecho y subcategoria), anidadas con el catalogo: cada cifra viene del servidor y nunca se
 * suma. "Sin subcategoría" y los estados quedan como filas propias al final.
 */
export function rightsTree(levels: Partial<Record<typeof RIGHTS_LEVELS[number], Result>>, catalog: Row): TreeRow[] {
  const items = vigentItems(catalog, 'DERECHO');
  const parentOf = new Map(items.map(i => [i.code, i.parent]));
  const out: TreeRow[] = [];
  const special = new Set<string>();
  const present = (dim: typeof RIGHTS_LEVELS[number]) => {
    const r = levels[dim];
    return r ? (r.values[dim] ?? []).filter(v => r.cells.some(c => c.row[0] === v.key)) : [];
  };
  const ejes = present('DERECHO_EJE'), rights = present('DERECHO'), subs = present('DERECHO_SUBCATEGORIA');
  const isItem = (k: string) => parentOf.has(k);
  for (const e of ejes) {
    if (!isItem(e.key)) {special.add(e.key); continue;}
    out.push({key: e.key, label: e.label, level: 0, dim: 'DERECHO_EJE', parent: '', special: false});
    for (const d of rights.filter(x => parentOf.get(x.key) === e.key)) {
      out.push({key: d.key, label: d.label, level: 1, dim: 'DERECHO', parent: e.key, special: false});
      for (const s of subs.filter(x => parentOf.get(x.key) === d.key)) out.push({key: s.key, label: s.label, level: 2, dim: 'DERECHO_SUBCATEGORIA', parent: d.key, special: false});
    }
  }
  // Derechos o subcategorias sin padre en el catalogo vigente (version anterior): al final de su nivel, sin anidar.
  for (const d of rights) if (isItem(d.key) && !out.some(o => o.key === d.key) && !ejes.some(e => e.key === parentOf.get(d.key))) out.push({key: d.key, label: d.label, level: 1, dim: 'DERECHO', parent: '', special: false});
  for (const d of rights) if (!isItem(d.key)) special.add(d.key);
  const noDetail = subs.find(s => s.key === 'SIN_DESGLOSE');
  if (noDetail) out.push({key: 'SIN_DESGLOSE', label: noDetail.label, level: 2, dim: 'DERECHO_SUBCATEGORIA', parent: '', special: true});
  for (const k of special) {
    const r = levels.DERECHO_EJE ?? levels.DERECHO;
    out.push({key: k, label: r ? labelOf(r, levels.DERECHO_EJE ? 'DERECHO_EJE' : 'DERECHO', k) : k, level: 0, dim: levels.DERECHO_EJE ? 'DERECHO_EJE' : 'DERECHO', parent: '', special: true});
  }
  return out;
}

// --- Figura 1 (Informes) ---------------------------------------------------------------------------------------------
export const FIGURE_DIMS = ['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO', 'ADSCRIPCION_INICIAL'];
export const FIGURE_TITLES: Record<string, string> = {TIPO_SOLICITUD: 'Por tipo de solicitud', TIPO_REQUIRENTE: 'Por tipo de requirente', VIA_INGRESO: 'Por vía de ingreso',
  ADSCRIPCION_INICIAL: 'Por adscripción institucional'};
const SPECIAL_ORDER = ['PENDIENTE', 'SIN_INFORMACION', 'NO_DECLARADO', 'NO_APLICA'];
/**
 * Categorias de la Figura 1 (GO de E12, C): todas las de /api/catalog para la dimension, con 0 si no hay registros, para
 * que la tabla tenga siempre la misma forma: items seleccionables del catalogo VIGENTE (en su orden) o, en la adscripcion,
 * las sedes regionales del catalogo y las opciones del motor; despues los estados especiales. Una categoria que devuelve el
 * servidor y no esta en esas opciones (version anterior) se agrega con su rotulo del servidor.
 */
export function figureRows(cat: StatsCatalog, catalog: Row, dim: string, r: Result | null): {key: string; label: string; n: number}[] {
  const d = dimOf(cat, dim);
  let base: Option[];
  if (dim === 'ADSCRIPCION_INICIAL') {
    const sites = list(catalog, 'scopes').filter(s => text(s, 'level') === 'REGIONAL' && text(s, 'state') !== 'PROYECTADA').map(s => ({code: text(s, 'code'), label: text(s, 'name')}));
    const own = d?.options ?? [];
    base = [...sites, ...own.filter(o => o.code !== 'PENDIENTE'), ...own.filter(o => o.code === 'PENDIENTE')];
  } else {
    base = vigentItems(catalog, d?.catalog ?? dim).filter(i => i.selectable).map(i => ({code: i.code, label: i.label}));
  }
  const specials = SPECIAL_ORDER.map(c => cat.special.find(s => s.code === c)).filter((s): s is Option => !!s).filter(s => !base.some(b => b.code === s.code));
  const rows = [...base, ...specials].map(o => ({key: o.code, label: o.label, n: 0}));
  if (r) {
    for (const c of r.cells) {
      const k = c.row[0];
      const row = rows.find(x => x.key === k);
      if (row) row.n = c.n;
      else rows.splice(rows.length - specials.length, 0, {key: k, label: labelOf(r, dim, k), n: c.n});
    }
  }
  return rows;
}

// --- Avisos y "Como leer esta cifra" ---------------------------------------------------------------------------------
/** Cada aviso del servidor en una frase; un aviso desconocido tiene una frase general (nunca su codigo). */
export function warningText(w: Row, cat: StatsCatalog | null): string {
  const dims = ((w.dimensions as string[]) ?? []).map(c => cat ? dimOf(cat, c)?.label ?? 'un campo' : 'un campo');
  const names = dims.length === 0 ? 'algunos campos' : dims.length === 1 ? `«${dims[0]}»` : dims.slice(0, -1).map(d => `«${d}»`).join(', ') + ` y «${dims[dims.length - 1]}»`;
  switch (text(w, 'code')) {
    case 'PORCENTAJES_MULTIVALOR': return `${names} admite varios valores por requerimiento: los porcentajes pueden sumar más de 100 % y las categorías no suman el total.`;
    case 'VARIABLES_PENDIENTES': return `Hay requerimientos con ${names} pendiente de clasificar: aparecen en la fila «Pendiente».`;
    case 'VERSIONES_DISTINTAS': return `${names} tiene valores registrados con distintas versiones del listado; se muestran con el rótulo de la versión más reciente.`;
    case 'SIN_CATALOGO_VIGENTE': return `${names} no tiene un listado vigente: no se puede calcular por tramos.`;
    case 'HISTORIA_INCOMPLETA': return `${names}: en ${fmtInt(Number(w.n ?? 0))} requerimientos no hay evidencia del valor en esa fecha; quedan como pendientes.`;
    default: return 'El servidor informó una advertencia sobre esta consulta. Revisa los campos elegidos.';
  }
}
export const WARNING_CODES = ['PORCENTAJES_MULTIVALOR', 'VARIABLES_PENDIENTES', 'VERSIONES_DISTINTAS', 'SIN_CATALOGO_VIGENTE', 'HISTORIA_INCOMPLETA'];

/** Alcance de la cuenta para el pie de fuente: los ambitos de sus funciones Reportes (o toda la institucion). */
export function scopeLabel(user: {grants: {role: string; scope: number | null}[]}, catalog: Row) {
  const grants = user.grants.filter(g => g.role === 'REPORTES');
  if (grants.length === 0) return 'Alcance de la cuenta';
  if (grants.some(g => g.scope == null)) return 'Toda la institución';
  const scopes = list(catalog, 'scopes');
  const names = grants.map(g => text(scopes.find(s => Number(s.id) === g.scope) ?? {}, 'name')).filter(Boolean);
  return names.length ? names.join(', ') : 'Alcance de la cuenta';
}
/** Pie de fuente de toda tabla y grafico (tambien en PNG y SVG). */
export function sourceFooter(o: {at: number; scope: string; basis: string; mode: string; cut?: string | null}) {
  return `Fuente: SIGER, Defensoría de la Niñez. Consulta del ${chileDateTime(o.at)}; ${o.scope}; ${(BASIS_LABELS[o.basis] ?? 'fecha de análisis').toLocaleLowerCase('es-CL')}; ${(MODE_LABELS[o.mode] ?? 'valores vigentes').toLocaleLowerCase('es-CL')}`
    + (o.cut ? `; corte ${o.cut}` : '') + '.';
}

/** Lineas de "Como leer esta cifra": que cuenta, fecha, modo, alcance, periodo, versiones, cobertura y avisos. */
export function howToRead(o: {cat: StatsCatalog; q: Query; r: Result | null; scope: string; hierarchy?: boolean; extra?: string[]}): string[] {
  const {cat, q, r} = o;
  const m = measureOf(cat, q.measure);
  const lines: string[] = [];
  if (m) lines.push(`${m.label}: ${measureHelp(m.code) || 'cuenta ' + (UNIT_LABELS[m.unit] ?? 'registros') + '.'}`);
  const meta = r?.metadata ?? {};
  const basis = text(meta.period as Row ?? {}, 'date_basis') || q.period.basis || m?.default_date_basis || 'RECEPCION';
  lines.push(`Periodo: ${periodLabel((meta.period as Row) ?? q.period)}, según la ${(BASIS_LABELS[basis] ?? 'fecha de recepción').toLocaleLowerCase('es-CL')}, en hora de Chile.`);
  const mode = text(meta, 'time_mode') || q.mode || 'VIGENTE';
  lines.push(`${MODE_LABELS[mode] ?? 'Valores vigentes'}: ${MODE_HELP[mode] ?? ''}${mode === 'FECHA' && text(meta, 'as_of') ? ' Fecha: ' + chileDateTime(Date.parse(text(meta, 'as_of'))) + '.' : ''}`);
  lines.push(`Alcance: ${o.scope}. Solo se cuentan los requerimientos de tu alcance.`);
  if (q.percent) lines.push(`${PERCENT_LABELS[q.percent]}: el porcentaje lo calcula SIGER sobre ${q.percent === 'TOTAL' ? 'el total' : q.percent === 'FILA' ? 'el total de cada fila' : 'el total de cada columna'} de ${UNIT_LABELS[text(meta, 'percent_unit') || 'COM'] ?? 'requerimientos'} únicos.`);
  if (r?.comparison?.period) lines.push(`Comparación con ${periodLabel(r.comparison.period)}: diferencia y variación sobre la cifra base.`);
  const versions = Object.entries((meta.catalog_versions ?? {}) as Record<string, number[]>);
  if (versions.length) lines.push('Listados usados: ' + versions.map(([c, v]) => `${catalogName(c)} (versión ${v.join(', ')})`).join('; ') + '.');
  const multiple = multipleIn(cat, q);
  if (multiple.length) lines.push(`${multiple.map(c => dimOf(cat, c)?.label).join(', ')}: valores múltiples. ${MULTIPLE_NOTE} Por eso las categorías pueden sumar más que el total, que cuenta cada requerimiento una vez.`);
  if ([...q.rows, ...q.columns].some(c => NON_ADDITIVE_DIMS.includes(c))) lines.push(NON_ADDITIVE_NOTE);
  if (o.hierarchy) lines.push(HIERARCHY_NOTE + ' «Sin subcategoría» reúne los requerimientos con derecho y sin subcategoría.');
  for (const [code, c] of Object.entries(r?.coverage ?? {})) {
    const cv = c as Row;
    lines.push(`${dimOf(cat, code)?.label ?? 'Dato demográfico'}: con dato en ${fmtInt(Number(cv.with_data ?? 0))} de ${fmtInt(Number(cv.total ?? 0) - Number(cv.not_applicable ?? 0))} requerimientos en que aplica (${fmtPct(cv.percent == null ? null : Number(cv.percent))}).`);
  }
  if (r?.late && Number(r.late.n ?? 0) > 0) lines.push(`Registros tardíos: ${fmtInt(Number(r.late.n))} ingresos se recibieron en el periodo y se registraron después de su fin.`);
  for (const w of r?.warnings ?? []) lines.push(warningText(w, cat));
  lines.push(...(o.extra ?? []));
  return lines;
}
const CATALOG_NAMES: Record<string, string> = {TIPO_SOLICITUD: 'Tipo de solicitud', TIPO_REQUIRENTE: 'Tipo de requirente', VIA_INGRESO: 'Vía de ingreso', CONTEXTO: 'Contexto',
  CARACTERIZACION: 'Caracterización', DERECHO: 'Derechos', ROL_ESTADO: 'Rol del Estado', GRUPO_PRIORITARIO: 'Grupos prioritarios', TIPO_ACTUACION: 'Tipo de actuación',
  TIPO_INSTITUCION: 'Tipo de institución', RELACION: 'Relación', TRAMO_EDAD: 'Tramos de edad'};
export const catalogName = (c: string) => CATALOG_NAMES[c] ?? 'Listado';

// --- Panorama --------------------------------------------------------------------------------------------------------
export type Indicator = {key: string; label: string; measure: string; filters?: (cat: StatsCatalog, catalog: Row) => Record<string, string[]> | null; help: string};
export const INDICATORS: Indicator[] = [
  {key: 'ingresos', label: 'Ingresos', measure: 'INGRESOS', help: 'Requerimientos recibidos en el periodo.'},
  {key: 'req', label: 'REQ constituidos', measure: 'REQ_CONSTITUIDOS', help: 'Requerimientos constituidos en el periodo.'},
  {key: 'casos', label: 'Casos', measure: 'CASOS', help: 'Casos abiertos en el periodo.'},
  {key: 'actuaciones', label: 'Actuaciones', measure: 'ACTUACIONES', help: 'Actuaciones tipificadas y resultados de primeras atenciones.'},
  {key: 'completos', label: 'Ingresos con caracterización completa', measure: 'INGRESOS', filters: completeFilters,
    help: 'Ingresos sin ninguna de las seis variables de completitud pendiente: tipo de solicitud, tipo de requirente, vía, región del requirente, derecho y contexto.'},
  {key: 'tardios', label: 'Registros tardíos', measure: 'INGRESOS', filters: () => ({TARDANZA: ['SI']}), help: 'Ingresos recibidos en el periodo y registrados después de su fin.'},
];
export function indicatorQuery(ind: Indicator, cat: StatsCatalog, catalog: Row, period: Period, compare: string): Query | null {
  const filters = ind.filters ? ind.filters(cat, catalog) : {};
  if (!filters) return null;
  return {...emptyQuery(period), measure: ind.measure, rows: ['PERIODO_MES'], filters, compare};
}
/**
 * "X de Y ingresos (Z %)" (GO de E12, A): X e Y vienen del servidor y Z es una sola division; la variacion va en puntos
 * porcentuales con las cifras base de la comparacion (tambien del servidor).
 */
export function completeShare(x: number, y: number, baseX: number | null, baseY: number | null) {
  const share = y > 0 ? x / y * 100 : null;
  const base = baseX != null && baseY != null && baseY > 0 ? baseX / baseY * 100 : null;
  return {
    text: `${fmtInt(x)} de ${fmtInt(y)} ingresos (${fmtPct(share == null ? null : Math.round(share * 10) / 10)})`,
    share, points: share != null && base != null ? Math.round((share - base) * 10) / 10 : null,
  };
}
export const COMPLETE_NOTE = 'La caracterización completa sale de dos consultas al servidor: los ingresos sin variables pendientes y el total de ingresos; el porcentaje es la división de esas dos cifras.';

// --- Preguntas guiadas ------------------------------------------------------------------------------------------------
export const GUIDED = [
  {key: 'cuantos', label: '¿Cuántos ingresos hubo?', help: 'El total del periodo, mes a mes.'},
  {key: 'via', label: 'Por vía de ingreso', help: 'Ingresos por vía, de mayor a menor.'},
  {key: 'comparar', label: 'Comparar con el periodo anterior', help: 'El mismo cálculo junto al periodo anterior.'},
  {key: 'figura', label: 'Figura 1 de la Cuenta Pública', help: 'Los cuatro desgloses de julio a junio.'},
];
export function guidedQuery(key: string, period: Period): Query | null {
  const q = emptyQuery(period);
  if (key === 'cuantos') return {...q, rows: ['PERIODO_MES']};
  if (key === 'via') return {...q, rows: ['VIA_INGRESO']};
  if (key === 'comparar') return {...q, rows: ['PERIODO_MES'], compare: 'PERIODO_ANTERIOR'};
  return null;
}
/** Nombre neutro del archivo descargado. */
export const fileStem = (title: string) => 'siger-' + title.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 60);

// --- Grafico de una tabla --------------------------------------------------------------------------------------------
/** Forma de los datos para sugerir el grafico (components/charts/chartModel.ts). */
export function chartShape(cat: StatsCatalog, q: Query, t: TableModel, comparison: boolean) {
  return {rows: q.rows.length, columns: q.columns.length, period: q.rows.length === 1 && isPeriodDim(q.rows[0]),
    multipleRows: q.rows.some(c => isMultiple(cat, c)), multipleColumns: q.columns.some(c => isMultiple(cat, c)), comparison, percent: q.percent !== '',
    categories: t.rows.length, series: q.columns.length ? t.columns.length : comparison ? 2 : 1};
}
const SPECIALS = new Set(['PENDIENTE', 'SIN_INFORMACION', 'NO_DECLARADO', 'NO_APLICA', 'SIN_DESGLOSE']);
/**
 * Datos del grafico desde la tabla: categorias = filas, series = columnas (o la cifra y su base en una comparacion). Las
 * cifras son las del servidor; los totales por categoria (barras apiladas) son sus margenes.
 */
export function chartData(t: TableModel, metric: Metric, comparison: boolean, basePeriod: string) {
  const categories = t.rows.map(r => ({key: keyOf(r.key), label: r.labels.join(' · ') || 'Total', special: r.key.some(k => SPECIALS.has(k)), total: t.rowTotal(r.key)?.n ?? null}));
  const series = t.colDims.length
    ? t.columns.map(c => ({key: keyOf(c.key), label: c.labels.join(' · '), values: t.rows.map(r => cellFigure(t.value(r.key, c.key), metric).value)}))
    : [{key: 'actual', label: comparison ? 'Periodo consultado' : 'Cifra', values: t.rows.map(r => cellFigure(t.value(r.key, []), metric).value)},
      ...(comparison ? [{key: 'base', label: basePeriod ? 'Comparación: ' + basePeriod : 'Periodo de comparación', base: true, values: t.rows.map(r => t.delta(r.key, [])?.base_n ?? 0)}] : [])];
  return {categories, series, total: t.rowTotal([])?.n ?? null, percent: metric === 'percent', format: (v: number | null) => metric === 'percent' ? fmtPct(v) : v == null ? '—' : fmtInt(v)};
}
