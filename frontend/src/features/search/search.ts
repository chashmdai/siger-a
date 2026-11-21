/**
 * Buscador de personas y folios (0.5.0, D5, gate G08b): logica pura de la pantalla #buscar, la paleta y el campo de
 * Inicio. El motor es POST /api/search (PersonSearch; G08c: el termino va en el cuerpo): aqui solo se arma la consulta
 * y se presentan sus resultados.
 *
 * Privacidad (decision del responsable): el termino y los filtros nunca van en la ruta. La vista es siempre "#buscar",
 * sin parametros; el estado vive solo en sessionStorage de la pestana (para volver de una ficha a los resultados) y se
 * borra al cerrar sesion. No hay historial de busquedas ni busqueda aproximada.
 */
import type {Row} from '../../lib/rows.ts';
import {list, number, obj, text} from '../../lib/rows.ts';
import {EXTERNAL_ROLE_LABELS} from '../../lib/formulario.ts';
import {PERSON_ROLE_LABELS} from '../../lib/labels.ts';
import type {Store} from '../../lib/tabStore.ts';
import {sessionStore} from '../../lib/tabStore.ts';

/** Ruta unica de la vista. Nunca lleva el termino. */
export const SEARCH_ROUTE = 'buscar';
export const MIN_LENGTH = 3, MAX_LENGTH = 160;
export const SHORT_HINT = 'Escribe al menos 3 caracteres. Para un correlativo corto escribe 001.';
/** Sobre este numero de grupos (o si el servidor indica que hay mas) se ofrecen los filtros. */
export const FILTER_THRESHOLD = 10;
/** Evento para que la vista abierta relea el estado cuando se busca desde la paleta sin cambiar de ruta. */
export const SEARCH_EVENT = 'siger-search';

export type Period = '' | 'd30' | 'm12' | 'custom';
export type SearchState = {
  q: string;
  /** Lectura elegida por la persona (FOLIO, RUN, DOCUMENTO o NOMBRE); vacia = la que detecta el servidor. */
  as?: string;
  role?: string;
  open?: boolean;
  scope?: number;
  region?: string;
  period?: Period;
  /** Fechas aaaa-mm-dd del periodo "Elegir fechas". */
  from?: string;
  to?: string;
  /** Busqueda recien enviada: un folio exacto abre su ficha. Al volver de la ficha ya no se reabre. */
  fresh?: boolean;
};
export type {Store};
export {sessionStore};

const KEY = 'siger-busqueda';
export function readSearch(store: Store | null): SearchState | null {
  try {
    const raw = store?.getItem(KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as SearchState;
    return typeof value?.q === 'string' ? value : null;
  } catch {return null;}
}
export function saveSearch(store: Store | null, state: SearchState) {
  try {store?.setItem(KEY, JSON.stringify(state));} catch {/* sin almacenamiento: no se podra volver a los resultados */}
}
/** Al cerrar sesion (o si la sesion termina) no queda ningun termino en la pestana. */
export function clearSearch(store: Store | null) {
  try {store?.removeItem(KEY);} catch {/* nada que borrar */}
}

/** Nueva busqueda desde la paleta, Inicio o la vista: guarda el termino sin filtros y devuelve la ruta (sin el termino). */
export function startSearch(term: string, store: Store | null): string {
  saveSearch(store, {q: term.trim(), fresh: true});
  return SEARCH_ROUTE;
}

/** Navega a la vista con el termino ya guardado. Si la vista ya esta abierta, le avisa para que lo relea. */
export function openSearch(term: string) {
  const route = startSearch(term, sessionStore());
  if (location.hash.slice(1) === route) window.dispatchEvent(new Event(SEARCH_EVENT));
  else location.hash = route;
}

export const tooShort = (q: string) => q.trim().length > 0 && q.trim().length < MIN_LENGTH;
export const searchable = (q: string) => q.trim().length >= MIN_LENGTH && q.trim().length <= MAX_LENGTH;

/** aaaa-mm-dd en hora de Chile (el servidor interpreta el periodo en esa zona). */
export function chileDay(at: Date) {
  return new Intl.DateTimeFormat('en-CA', {timeZone: 'America/Santiago', year: 'numeric', month: '2-digit', day: '2-digit'}).format(at);
}
function shift(day: string, days: number, years = 0) {
  const [y, m, d] = day.split('-').map(Number);
  const date = new Date(Date.UTC(y + years, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}
/** Periodo de recepcion: ultimos 30 dias o 12 meses (incluido hoy), o las fechas elegidas. */
export function periodRange(state: SearchState, now: Date): {from?: string; to?: string} {
  const today = chileDay(now);
  if (state.period === 'd30') return {from: shift(today, -29), to: today};
  if (state.period === 'm12') return {from: shift(today, 1, -1), to: today};
  if (state.period === 'custom') return {from: state.from || undefined, to: state.to || undefined};
  return {};
}

/** Cuerpo de POST /api/search (G08c): el termino y los filtros viajan en el cuerpo, nunca en una URL ni en el historial. */
export type SearchBody = {q: string; as?: string; role?: string; open?: boolean; scope?: number; region?: string; from?: string; to?: string};
export function apiBody(state: SearchState, now: Date): SearchBody {
  const body: SearchBody = {q: state.q.trim()};
  if (state.as) body.as = state.as;
  if (state.role) body.role = state.role;
  if (state.open) body.open = true;
  if (state.scope) body.scope = state.scope;
  if (state.region) body.region = state.region;
  const {from, to} = periodRange(state, now);
  if (from) body.from = from;
  if (to) body.to = to;
  return body;
}
/** Lecturas de persona: un folio no es una coincidencia de persona. */
export const PERSON_READINGS = ['RUN', 'DOCUMENTO', 'NOMBRE'];
export const NO_PERSON_NOTE = 'Solo se muestran coincidencias en registros que puedes leer. Que no aparezca nada no significa que la persona no tenga registros.';
export const FOLIO_ONLY_NOTE = 'Tu función permite encontrar REQ y Casos por folio; las personas aparecen solo si puedes leer la comunicación o la ficha.';
/**
 * Avisos de una lectura de persona sin coincidencias (G08c). El servidor indica con can_find_people si las funciones de
 * la cuenta abren alguna COM o ficha (por ejemplo, solo LECTURA no): la pantalla no lo calcula.
 */
export function emptyNotes(response: Row, reading: Row): string[] {
  if (!PERSON_READINGS.includes(text(reading, 'type')) || reading.too_many === true || list(reading, 'groups').length > 0) return [];
  return response.can_find_people === false ? [NO_PERSON_NOTE, FOLIO_ONLY_NOTE] : [NO_PERSON_NOTE];
}
/** Filtros activos (para mostrarlos aunque el resultado ya sea corto y poder quitarlos). */
export const filtered = (s: SearchState) => Boolean(s.role || s.open || s.scope || s.region || s.period);
/** Quita los filtros y conserva el termino y la lectura. */
export const withoutFilters = (s: SearchState): SearchState => ({q: s.q, as: s.as});

/** Ficha que se abre directamente: una sola lectura FOLIO con un solo registro. */
export function exactTarget(response: Row): [kind: string, id: number] | null {
  const readings = list(response, 'readings');
  if (readings.length !== 1) return null;
  const r = readings[0], exact = obj(r, 'exact');
  if (text(r, 'type') !== 'FOLIO' || number(r, 'total') !== 1 || !text(exact, 'kind')) return null;
  return [text(exact, 'kind'), number(exact, 'id')];
}

export const READING_LABELS: Record<string, string> = {FOLIO: 'folio', RUN: 'RUN', DOCUMENTO: 'documento', NOMBRE: 'nombre'};
export const READING_TITLES: Record<string, string> = {FOLIO: 'Como folio o correlativo', RUN: 'Como RUN', DOCUMENTO: 'Como documento', NOMBRE: 'Como nombre'};

/** Lecturas que se muestran: la elegida o las detectadas por el servidor. */
export const shownReadings = (response: Row): string[] => response.forced ? [text(response, 'forced')] : (response.detected as string[] | undefined) ?? [];

/** "Buscando como RUN" o "Buscando como folio y como RUN". */
export function interpretation(response: Row) {
  const kinds = shownReadings(response).map(k => READING_LABELS[k] ?? k);
  return 'Buscando como ' + kinds.join(' y como ');
}
/** Otras lecturas que se pueden elegir. Con dos detectadas, cada una se ofrece por separado. */
export function alternatives(response: Row): string[] {
  const shown = shownReadings(response);
  const available = (response.available as string[] | undefined) ?? [];
  return available.filter(k => shown.length > 1 || !shown.includes(k));
}

export const showFilters = (reading: Row, state: SearchState) =>
  number(reading, 'total') > FILTER_THRESHOLD || reading.more === true || filtered(state);

/** Roles de persona del indice (D5): los de la COM y la ficha, mas los del Formulario. */
export const MATCH_ROLE_LABELS: Record<string, string> = {...PERSON_ROLE_LABELS, ...EXTERNAL_ROLE_LABELS, CURADOR: 'Curador o curadora'};
export const SOURCE_LABELS: Record<string, string> = {
  FORMULARIO: 'Formulario Público', FICHA: 'ficha de primera atención', COM: 'comunicación', COM_PERSONA: 'comunicación',
};

/** Por que coincide; uncertain = solo por nombre ("puede ser otra persona"). */
export function matchReason(m: Row): {text: string; uncertain: boolean} {
  switch (text(m, 'reason')) {
    case 'RUN': return {text: m.authenticated === true ? 'Coincide por RUN autenticado con ClaveÚnica' : 'Coincide por RUN declarado', uncertain: false};
    case 'DOCUMENTO': return {text: 'Coincide por documento declarado', uncertain: false};
    default: return {text: m.exact === true ? 'Coincide por el nombre completo' : 'Coincide por palabras del nombre', uncertain: true};
  }
}
export const UNCERTAIN = 'Puede ser otra persona';

/** Ambito o sede legible: "UPR-REQ · Nombre" en el nivel central; "Sede Nombre · Region" en las sedes. */
export function scopeLabel(scope: unknown) {
  if (!scope || typeof scope !== 'object') return 'Sin información';
  const s = scope as Row;
  if (text(s, 'level') === 'REGIONAL') return `${text(s, 'name')} · ${text(s, 'region_name') || 'sin región asignada'}`;
  return `${text(s, 'code')} · ${text(s, 'name')}`;
}

/** RUN con puntos y DV ("12.345.678-5"); sin DV escrito, solo el numero. */
export function formatRun(number: string, dv?: string | null) {
  const dotted = number.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return dv ? `${dotted}-${dv}` : dotted;
}

const plural = (n: number, one: string, many: string) => `${n.toLocaleString('es-CL')} ${n === 1 ? one : many}`;
/** Totales de la persona: primero lo abierto, luego el total legible. */
export function summaryLines(summary: Row): {open: string; total: string} {
  const parts = (suffix: '' | '_open') => [
    plural(number(summary, 'com' + suffix), 'COM', 'COM'), plural(number(summary, 'req' + suffix), 'REQ', 'REQ'), plural(number(summary, 'cas' + suffix), 'Caso', 'Casos'),
  ].join(' · ');
  return {open: parts('_open'), total: parts('')};
}

/** Entrada de la paleta: lleva a la vista (sin listar resultados) o, con 1-2 caracteres, la pista del correlativo. */
export function paletteEntry(query: string, canSearch: boolean): {label: string; term: string; route: string} | {hint: string} | null {
  if (!canSearch || !query.trim()) return null;
  if (tooShort(query)) return {hint: 'Buscar persona o folio: ' + SHORT_HINT.charAt(0).toLowerCase() + SHORT_HINT.slice(1)};
  if (!searchable(query)) return null;
  return {label: `Buscar persona o folio «${query.trim()}»`, term: query.trim(), route: SEARCH_ROUTE};
}

/** Movimiento de foco entre resultados con flechas, Inicio y Fin. */
export function nextIndex(key: string, current: number, count: number): number | null {
  if (count === 0) return null;
  if (key === 'ArrowDown') return Math.min(count - 1, current + 1);
  if (key === 'ArrowUp') return Math.max(0, current - 1);
  if (key === 'Home') return 0;
  if (key === 'End') return count - 1;
  return null;
}

/** Ficha principal de un grupo (Enter sobre el resultado): la COM legible o, si no, el primer REQ o Caso. */
export function primaryTarget(group: Row): [kind: string, id: number] | null {
  const folio = list(group, 'folio_matches')[0];
  if (folio) return [text(folio, 'kind'), number(folio, 'id')];
  const com = obj(group, 'communication');
  if (text(com, 'id')) return ['COM', number(com, 'id')];
  const req = list(group, 'requirements')[0];
  if (req) return ['REQ', number(req, 'id')];
  const cas = list(group, 'cases')[0];
  return cas ? ['CAS', number(cas, 'id')] : null;
}
