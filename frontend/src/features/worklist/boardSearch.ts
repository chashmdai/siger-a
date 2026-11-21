/**
 * Texto de busqueda de las bandejas (G08c): como el del buscador, nunca va en la ruta (#req, no #req?q=...) ni en la URL
 * de la consulta (POST /api/worklist/{kind} con el texto en el cuerpo). Vive solo en sessionStorage de la pestana, una
 * entrada por bandeja, para volver de una ficha con la misma busqueda, y se borra al cerrar o perder la sesion.
 */
import {BOARDS, boardRoute} from '../../app/routes.ts';
import type {Store} from '../../lib/tabStore.ts';
import {sessionStore} from '../../lib/tabStore.ts';

const KEY = 'siger-bandejas';
/** Aviso a una bandeja abierta de que la paleta dejo otro texto para ella. */
export const BOARD_SEARCH_EVENT = 'siger-bandeja';
export const BOARD_MAX_LENGTH = 160;

function readAll(store: Store | null): Record<string, string> {
  try {
    const raw = store?.getItem(KEY);
    const value: unknown = raw ? JSON.parse(raw) : null;
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
    return Object.fromEntries(Object.entries(value).filter(([k, v]) => BOARDS.includes(k) && typeof v === 'string')) as Record<string, string>;
  } catch {return {};}
}
export function readBoardQuery(store: Store | null, kind: string) {return readAll(store)[kind] ?? '';}
export function saveBoardQuery(store: Store | null, kind: string, query: string) {
  if (!BOARDS.includes(kind)) return;
  const all = readAll(store), q = query.trim().slice(0, BOARD_MAX_LENGTH);
  if (q) all[kind] = q; else delete all[kind];
  try {
    if (Object.keys(all).length) store?.setItem(KEY, JSON.stringify(all));
    else store?.removeItem(KEY);
  } catch {/* sin almacenamiento: la bandeja no recordara el texto */}
}
/** Al cerrar sesion (o si la sesion termina) no queda texto de ninguna bandeja en la pestana. */
export function clearBoardSearch(store: Store | null) {
  try {store?.removeItem(KEY);} catch {/* nada que borrar */}
}

/** Busqueda en una bandeja desde la paleta: guarda el texto y devuelve la ruta, sin el texto. */
export function startBoardSearch(kind: string, query: string, store: Store | null) {
  saveBoardQuery(store, kind, query);
  return boardRoute(kind);
}
export function openBoardSearch(kind: string, query: string) {
  const route = startBoardSearch(kind, query, sessionStore());
  if (location.hash.slice(1) === route) window.dispatchEvent(new Event(BOARD_SEARCH_EVENT));
  else location.hash = route;
}

/** Bandejas donde la busqueda por folio o texto tiene sentido, en orden de uso. */
export const SEARCHABLE: [path: string, label: string][] = [['req', 'Requerimientos'], ['cas', 'Casos'], ['atenciones', 'Primeras atenciones'], ['com', 'Comunicaciones'], ['sol', 'Solicitudes']];
export type BoardEntry = {path: string; label: string; query: string; route: string};
/** Entradas "Buscar «...» en <bandeja>" de la paleta, solo para las bandejas del menu de la cuenta. */
export function boardEntries(query: string, available: ReadonlySet<string>): BoardEntry[] {
  const q = query.trim();
  if (!q) return [];
  return SEARCHABLE.filter(([path]) => available.has(path)).map(([path, label]) => ({path, label: `Buscar «${q}» en ${label}`, query: q, route: boardRoute(path)}));
}

/**
 * Ruta antigua con texto (#req?filter=open&q=..., por ejemplo un marcador de 0.4.0): el texto pasa a la pestana y se
 * devuelve la ruta sin el, para reemplazarla con location.replace. null si la ruta no trae texto.
 */
export function legacyRoute(route: string, store: Store | null): string | null {
  const [path, search] = route.split('?');
  if (search === undefined) return null;
  const params = new URLSearchParams(search);
  if (!params.has('q')) return null;
  const q = params.get('q') ?? '';
  params.delete('q');
  if (q.trim()) saveBoardQuery(store, path, q);
  const rest = params.toString();
  return path + (rest ? '?' + rest : '');
}
