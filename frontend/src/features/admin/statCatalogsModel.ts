// Catalogos estadisticos (0.8.0, D27, gate E02): logica pura de la pantalla de TI. El servidor valida todo el archivo
// (StatCatalogs.parse) y decide; aqui solo se resume el archivo elegido para que TI lo reconozca antes de confirmar, se
// agrupan las versiones por catalogo y se arma el arbol de items. Los codigos internos no se muestran como texto.
import type {Row} from '../../lib/rows';

/** Limite del archivo (StatCatalogs.MAX_CONTENT) y formatos del servidor. */
export const MAX_CONTENT = 400_000;
export const FORMAT = 'siger-catalogo-1', MAPPING_FORMAT = 'siger-correspondencia-1';

export type CatalogDef = {catalog: string; label: string};
export type State = 'PROPUESTA' | 'VIGENTE' | 'RETIRADA';
export type Version = {
  id: number; catalog: string; label: string; state: State; source: string; sha: string; items: number; revision: number;
  loadedMs: number; loadedBy: string; basis: string; activatedMs: number | null; activatedBy: string; activationBasis: string; retiredMs: number | null; retiredBy: string;
};
export type Group = CatalogDef & {current: Version | null; versions: Version[]};
export type Mapping = {from: number; to: number; pairs: number; loadedMs: number; loadedBy: string};

export const STATE_LABELS: Record<State, string> = {PROPUESTA: 'Propuesta', VIGENTE: 'Vigente', RETIRADA: 'Retirada'};
/** Tono del estado (el texto siempre acompana al color). */
export const stateTone = (s: State) => s === 'VIGENTE' ? 'chip chip-ok' : s === 'PROPUESTA' ? 'chip chip-warn' : 'chip';

const num = (v: unknown) => v == null || v === '' ? null : Number(v);
const str = (v: unknown) => v == null ? '' : String(v);

/** Filas de /api/stat-catalogs (versions) a versiones tipadas. */
export function versionsOf(rows: Row[]): Version[] {
  return rows.map(r => ({
    id: Number(r.id), catalog: str(r.catalog), label: str(r.label), state: str(r.state) as State, source: str(r.source), sha: str(r.file_sha256),
    items: Number(r.items ?? 0), revision: Number(r.revision ?? 0), loadedMs: Number(r.loaded_ms ?? 0), loadedBy: str(r.loaded_by), basis: str(r.basis),
    activatedMs: num(r.activated_ms), activatedBy: str(r.activated_by), activationBasis: str(r.activation_basis), retiredMs: num(r.retired_ms), retiredBy: str(r.retired_by),
  }));
}
export const mappingsOf = (rows: Row[]): Mapping[] =>
  rows.map(r => ({from: Number(r.from_version), to: Number(r.to_version), pairs: Number(r.pairs ?? 0), loadedMs: Number(r.loaded_ms ?? 0), loadedBy: str(r.loaded_by)}));

/** Un grupo por catalogo de la lista cerrada del servidor (en su orden), con la vigente y las versiones de la mas nueva a la mas antigua. */
export function groupsOf(catalogs: CatalogDef[], versions: Version[]): Group[] {
  return catalogs.map(c => {
    const own = versions.filter(v => v.catalog === c.catalog).sort((a, b) => b.id - a.id);
    return {...c, current: own.find(v => v.state === 'VIGENTE') ?? null, versions: own};
  });
}

/** Solo una version propuesta se activa (StatCatalogs.activate). */
export const canActivate = (v: Version) => v.state === 'PROPUESTA';
/** Versiones de origen posibles de una correspondencia hacia target: las demas del mismo catalogo. */
export const mappingSources = (versions: Version[], target: Version) => versions.filter(v => v.catalog === target.catalog && v.id !== target.id).sort((a, b) => b.id - a.id);
/** La correspondencia entre ese par de versiones ya esta cargada (el servidor admite una por par). */
export const mappingLoaded = (mappings: Mapping[], from: number, to: number) => mappings.some(m => m.from === from && m.to === to);

export const shortHash = (sha: string) => sha.slice(0, 12);
/** Texto como lo guarda el servidor: sin BOM y con saltos LF (la huella es la de ese texto). */
export const normalized = (text: string) => (text.startsWith('﻿') ? text.slice(1) : text).replace(/\r\n/g, '\n');

const plural = (n: number, one: string, many: string) => n.toLocaleString('es-CL') + ' ' + (n === 1 ? one : many);

export type Preview =
  | {ok: true; catalog: string; catalogLabel: string; label: string; source: string; items: number; selectable: number; levels: number; ranges: boolean}
  | {ok: false; problem: string};

function parsed(text: string): {root?: Record<string, unknown>; problem?: string} {
  if (!text.trim()) return {problem: 'El archivo está vacío.'};
  if (text.length > MAX_CONTENT) return {problem: 'El archivo excede el tamaño admitido (' + MAX_CONTENT.toLocaleString('es-CL') + ' caracteres).'};
  let root: unknown;
  try {root = JSON.parse(normalized(text));} catch {return {problem: 'El archivo no es un catálogo válido: debe ser un archivo JSON como los de config-ejemplo\\catalogos.'};}
  if (root == null || typeof root !== 'object' || Array.isArray(root)) return {problem: 'El archivo no es un catálogo válido: debe ser un objeto JSON.'};
  return {root: root as Record<string, unknown>};
}

/**
 * Resumen del archivo de catalogo elegido: que catalogo es, su nombre, su fuente y cuantos items, seleccionables y
 * niveles trae. Solo reconoce el archivo; la validacion completa (codigos, padres, tramos) la hace el servidor al importar.
 */
export function previewCatalog(text: string, catalogs: CatalogDef[]): Preview {
  const {root, problem} = parsed(text);
  if (!root) return {ok: false, problem: problem!};
  if (root.format === MAPPING_FORMAT) return {ok: false, problem: 'Este archivo es una correspondencia entre versiones: cárgalo con «Correspondencia» en la versión de destino.'};
  if (root.format !== FORMAT) return {ok: false, problem: 'El archivo no tiene el formato de catálogo de SIGER (' + FORMAT + ').'};
  const def = catalogs.find(c => c.catalog === root.catalog);
  if (!def) return {ok: false, problem: 'El archivo no corresponde a ninguno de los catálogos estadísticos de SIGER.'};
  const items = Array.isArray(root.items) ? root.items as Record<string, unknown>[] : null;
  if (!items) return {ok: false, problem: 'El archivo no trae la lista de ítems.'};
  if (items.length === 0) return {ok: false, problem: 'El archivo de ' + def.label + ' no tiene ítems: es una plantilla. Complétala con los valores aprobados por la institución antes de importarla.'};
  const depth = new Map<string, number>();
  for (const i of items) {
    const parent = typeof i?.parent === 'string' ? i.parent : null;
    depth.set(String(i?.code ?? ''), parent != null && depth.has(parent) ? depth.get(parent)! + 1 : 1);
  }
  return {ok: true, catalog: def.catalog, catalogLabel: def.label, label: str(root.label).trim(), source: str(root.source).trim(), items: items.length,
    selectable: items.filter(i => i?.selectable !== false).length, levels: Math.max(...depth.values()), ranges: items.some(i => i?.range_from != null || i?.range_to != null)};
}

/** Lineas legibles del resumen (dialogo de importacion). */
export function previewLines(p: Extract<Preview, {ok: true}>): [string, string][] {
  return [
    ['Catálogo', p.catalogLabel],
    ['Nombre de la versión', p.label || 'Sin nombre (el servidor lo exige)'],
    ['Fuente', p.source || 'Sin fuente (el servidor la exige)'],
    ['Ítems', plural(p.items, 'ítem', 'ítems') + (p.selectable < p.items ? ', ' + plural(p.selectable, 'seleccionable', 'seleccionables') : '') + (p.levels > 1 ? ', en ' + p.levels + ' niveles' : '')],
  ];
}

/** Resumen de un archivo de correspondencia (cantidad de pares). */
export function previewMapping(text: string): {ok: true; pairs: number} | {ok: false; problem: string} {
  const {root, problem} = parsed(text);
  if (!root) return {ok: false, problem: problem!};
  if (root.format === FORMAT) return {ok: false, problem: 'Este archivo es un catálogo: impórtalo con «Importar catálogo».'};
  if (root.format !== MAPPING_FORMAT) return {ok: false, problem: 'El archivo no tiene el formato de correspondencia de SIGER (' + MAPPING_FORMAT + ').'};
  if (!Array.isArray(root.pairs) || root.pairs.length === 0) return {ok: false, problem: 'La correspondencia no tiene pares.'};
  return {ok: true, pairs: root.pairs.length};
}

/** Texto del dialogo de activacion: que version deja de regir. */
export function activationNote(group: Group, v: Version) {
  const before = group.current
    ? `La versión ${group.current.id} («${group.current.label}») deja de estar vigente y queda retirada; sigue disponible para consulta y comparación.`
    : 'Hoy no hay versión vigente de este catálogo.';
  return `La versión ${v.id} («${v.label}», ${plural(v.items, 'ítem', 'ítems')}) pasa a ser la vigente de ${group.label}: SIGER la ofrece y la exige donde corresponda. ${before} Activa solo una versión validada por la institución.`;
}

/** Estado del catalogo para el encabezado de su panel. */
export function groupSummary(g: Group) {
  const proposals = g.versions.filter(v => v.state === 'PROPUESTA').length;
  const current = g.current ? `Vigente: versión ${g.current.id}, «${g.current.label}».` : 'Sin versión vigente: no se ofrece ni se exige.';
  return current + (proposals ? ' ' + plural(proposals, 'propuesta por revisar', 'propuestas por revisar') + '.' : '');
}

export type ItemRow = {code: string; label: string; depth: number; selectable: boolean; range: string; ref: string};
/** Items de una version (/api/stat-catalogs/{id}) en su orden, con la profundidad del arbol y el rango de edad si lo hay. */
export function itemRows(rows: Row[]): ItemRow[] {
  return rows.map(r => ({code: str(r.code), label: str(r.label), depth: Math.max(1, Number(r.level ?? 1)), selectable: r.selectable !== false,
    range: r.range_from == null ? '' : `${r.range_from} a ${r.range_to} años`, ref: str(r.source_ref)}));
}
