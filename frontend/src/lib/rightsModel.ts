// Derechos, contexto, caracterizacion, rol del Estado y grupos prioritarios de un REQ o de un Caso (0.8.0, D34-D35, gate
// E09; DISENO 4.3, 4.4 y 10.2). Logica pura del panel (components/RightsPanel.tsx): busqueda sin tildes con sus ancestros y
// descendientes, seleccion multiple como diferencia con lo registrado (add/remove), estados especiales solo sin valores,
// motivo al quitar o al reemplazar un estado, y consolidado del REQ con sus Casos legibles. Las variables y sus valores
// salen de la ficha (rights) y de los listados vigentes (catalog.stat_catalogs); el servidor vuelve a validar todo.
import type {Row} from './rows.ts';
import type {CatalogItem} from '../features/detail/characterizationModel.ts';

export const PENDING = 'PENDIENTE', INFORMED = 'INFORMADO';
/** Cambio de una variable: codigos que se agregan y que se quitan, y estado especial (solo sin valores). */
export type Change = {add: string[]; remove: string[]; status?: string};
/** Valor que viaja en RIGHTS_CLASSIFY.variables: {VARIABLE: Change}. */
export type Changes = Record<string, Change>;
export type RightsVar = {variable: string; label: string; state: string; catalog_current: boolean; values: {code: string; label: string}[]};

const str = (v: unknown) => v == null ? '' : String(v);
const rowsOf = (v: unknown): Row[] => Array.isArray(v) ? v.filter(x => x && typeof x === 'object') as Row[] : [];

/** Variables de la ficha (rights.variables), en el orden del servidor. */
export function rightsVars(rights: Row): RightsVar[] {
  return rowsOf(rights.variables).map(v => ({variable: str(v.variable), label: str(v.label), state: str(v.state) || PENDING, catalog_current: v.catalog_current === true,
    values: rowsOf(v.values).map(x => ({code: str(x.code), label: str(x.label)}))}));
}

/** Texto comparable: minusculas, sin tildes ni signos repetidos. */
export const normalize = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-CL').replace(/\s+/g, ' ').trim();

/** Hijos de cada codigo ('' = raiz), en el orden publicado. */
export function children(items: CatalogItem[]): Map<string, CatalogItem[]> {
  const known = new Set(items.map(i => i.code));
  const out = new Map<string, CatalogItem[]>();
  for (const i of items) {const p = known.has(i.parent) ? i.parent : '';out.set(p, [...(out.get(p) ?? []), i]);}
  return out;
}
/**
 * Codigos visibles para una busqueda: los que contienen todas las palabras (rotulo o descripcion, sin tildes), sus
 * ancestros (para ubicarlos) y sus descendientes (buscar un derecho muestra sus subcategorias). Sin busqueda, null.
 */
export function searchCodes(items: CatalogItem[], query: string): Set<string> | null {
  const words = normalize(query).split(' ').filter(Boolean);
  if (!words.length) return null;
  const byCode = new Map(items.map(i => [i.code, i]));
  const kids = children(items);
  const out = new Set<string>();
  const down = (code: string) => {for (const k of kids.get(code) ?? []) if (!out.has(k.code)) {out.add(k.code);down(k.code);}};
  for (const i of items) {
    const hay = normalize(i.label + ' ' + i.description);
    if (!words.every(w => hay.includes(w))) continue;
    out.add(i.code);
    down(i.code);
    for (let p = byCode.get(i.parent); p && !out.has(p.code); p = byCode.get(p.parent)) out.add(p.code);
  }
  return out;
}

/** Seleccion resultante: lo registrado, menos lo quitado, mas lo agregado. */
export function selection(current: string[], change?: Change): Set<string> {
  const out = new Set(current);
  for (const c of change?.remove ?? []) out.delete(c);
  for (const c of change?.add ?? []) out.add(c);
  return out;
}
/** Diferencia con lo registrado para una seleccion: lo que se agrega y lo que se quita (nunca el conjunto completo). */
export function diff(current: string[], selected: Set<string>, status?: string): Change {
  const before = new Set(current);
  const change: Change = {add: [...selected].filter(c => !before.has(c)), remove: current.filter(c => !selected.has(c))};
  if (status && selected.size === 0) change.status = status;
  return change;
}
/** Marca o desmarca un codigo seleccionable; marcar un valor deja sin efecto el estado especial elegido. */
export function toggle(items: CatalogItem[], current: string[], change: Change | undefined, code: string, on: boolean): Change {
  const item = items.find(i => i.code === code);
  const selected = selection(current, change);
  if (on && item?.selectable) selected.add(code); else if (!on) selected.delete(code);
  return diff(current, selected, on ? undefined : change?.status);
}
/** Estado especial (Sin informacion, ...): solo sin valores; elegirlo quita los valores marcados. '' vuelve a lo registrado. */
export const withStatus = (current: string[], status: string): Change => status ? diff(current, new Set(), status) : {add: [], remove: []};

/** Cambio efectivo de una variable (vacio = no se envia). */
export function effective(v: RightsVar, change?: Change): Change | undefined {
  if (!change) return undefined;
  const add = change.add.filter(c => !v.values.some(x => x.code === c));
  const remove = change.remove.filter(c => v.values.some(x => x.code === c));
  const status = change.status && change.status !== v.state ? change.status : undefined;
  if (!add.length && !remove.length && !status) return undefined;
  return {add, remove, ...(status ? {status} : {})};
}
/** RIGHTS_CLASSIFY.variables: solo variables con listado vigente y algun cambio; undefined si no cambia nada. */
export function rightsPayload(vars: RightsVar[], changes: Changes | undefined): Changes | undefined {
  const out: Changes = {};
  for (const v of vars) {
    if (!v.catalog_current) continue;
    const c = effective(v, changes?.[v.variable]);
    if (c) out[v.variable] = c;
  }
  return Object.keys(out).length ? out : undefined;
}
/** Corregir (quitar un valor o reemplazar un estado especial) exige motivo (Rights.classify). */
export function needsReason(vars: RightsVar[], changes: Changes | undefined): boolean {
  const payload = rightsPayload(vars, changes) ?? {};
  return vars.some(v => {
    const c = payload[v.variable];
    return !!c && (c.remove.length > 0 || !!c.status && v.state !== PENDING && v.state !== INFORMED);
  });
}
/** Problemas antes de enviar, en lenguaje llano. */
export function rightsProblems(vars: RightsVar[], changes: Changes | undefined, reason: string): string[] {
  const payload = rightsPayload(vars, changes);
  if (!payload) return ['Marca o quita al menos un valor.'];
  const out: string[] = [];
  for (const v of vars) {
    const c = payload[v.variable];
    if (c?.status && selection(v.values.map(x => x.code), c).size > 0) out.push(`${v.label}: un estado como «Sin información» se registra solo sin valores.`);
  }
  if (needsReason(vars, changes) && !reason.trim()) out.push('Indica el motivo de la corrección.');
  return out;
}

/** Consolidado del requerimiento: una fila por valor, con si esta en el REQ y los Casos legibles que lo registran. */
export function consolidatedRows(rights: Row, variable: string): {code: string; label: string; in_req: boolean; cases: string[]}[] {
  const c = (rights.consolidated && typeof rights.consolidated === 'object' ? rights.consolidated : {}) as Row;
  return rowsOf(c[variable]).map(r => ({code: str(r.code), label: str(r.label), in_req: r.in_req === true, cases: rowsOf(r.cases).map(x => str(x.folio))}));
}
/**
 * Variables de la vulneracion (stat_completeness.vulnerability) aun sin resolver en el REQ y sus Casos legibles: el
 * dialogo de cierre no las resuelve (REQ_CLOSE no recibe derechos) y remite al panel.
 */
export function rightsPending(vulnerability: string[], rights: Row): string[] {
  return rightsVars(rights).filter(v => vulnerability.includes(v.variable) && v.state === PENDING && consolidatedRows(rights, v.variable).length === 0).map(v => v.variable);
}
