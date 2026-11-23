/**
 * Selector de destino (R03a): logica pura, sin React, cubierta por tests/destination.test.ts.
 * Un solo modelo para COM_ROUTE y SOL_ROUTE (SOL), FLOW_REQUEST (COLABORACION, TRANSFERENCIA, DERIVACION) y la
 * entrega de primera atencion a jefatura (ENTREGA). Los destinos salen solo de catalog.scopes[].receives, que el
 * servidor calcula con la misma regla que valida cada comando (Access.receiverOf); aqui no se repite esa regla.
 * Dos pasos: nivel central -> unidad y area opcional; sede regional -> region (de norte a sur) y sede.
 * Cobertura territorial (0.7.0, D18 y D20; gate T03): una sede aparece bajo su region fisica y, marcada (covering),
 * bajo cada region de su cobertura adicional vigente (scopes[].coverage), despues de las sedes con esa region fisica.
 * La cobertura del nivel central (central_coverage) no agrega destinos: solo avisa que el nivel central atiende la region.
 */
import type {Row} from './rows.ts';
import {list, number, text} from './rows.ts';
import {MATTER_LABELS} from './labels.ts';
import {matchesWords} from './text.ts';

export type Operation = 'SOL' | 'COLABORACION' | 'TRANSFERENCIA' | 'DERIVACION' | 'ENTREGA';
/** Materia sugerida de una SOL a una sede (D9); solo COM_ROUTE y SOL_ROUTE la aceptan. */
export const REGIONAL_MATTERS = ['UPR', 'UPD', 'UEP'];
/** Codigo del grupo de sedes sin region asignada (las existentes antes de V11 hasta que TI les asigne region). */
export const NO_REGION = '-';

export type Place = {id: number; code: string; name: string};
/** Sede bajo una region: covering = la atiende por cobertura adicional (no es su region fisica). */
export type Site = Place & {covering?: true};
/** Area de una unidad central. direct: el area recibe por si misma (hoy UPR-REQ, su Coordinadora). */
export type Area = Place & {direct: boolean};
/** Unidad central (su Direccion recibe) o, si standalone, un area receptora cuya unidad no recibe. */
export type Unit = Place & {unit: string; label: string; areas: Area[]; standalone: boolean};
/** Region del catalogo con sus sedes que reciben la operacion; NO_REGION agrupa las sedes sin region asignada. */
export type Region = {code: string; name: string; sites: Site[]};
type Named = {code: string; name: string};
/** centralRegions: regiones que atiende el nivel central (catalog.central_coverage), con su nombre, de norte a sur. */
export type Destinations = {operation: Operation; central: Unit[]; regions: Region[]; centralRegions: Named[]};
export type Level = 'CENTRAL' | 'REGIONAL';
/** Eleccion en curso. scope = unidad (o area receptora suelta) o sede; area solo en el nivel central; matter solo en SOL a sede. */
export type Choice = {level: Level; scope: number | null; area: number | null; region: string; matter: string};

const receives = (s: Row, op: Operation) => Array.isArray(s.receives) && (s.receives as unknown[]).includes(op);
const place = (s: Row): Place => ({id: number(s, 'id'), code: text(s, 'code'), name: text(s, 'name')});
const isRegional = (s: Row) => text(s, 'level') === 'REGIONAL' || s.regional === true;
const coverageOf = (s: Row) => Array.isArray(s.coverage) ? (s.coverage as unknown[]).map(String) : [];

export function destinations(catalog: Row, operation: Operation): Destinations {
  const all = list(catalog, 'scopes');
  const unitNames = new Map(list(catalog, 'units').map(u => [text(u, 'code'), text(u, 'name')]));
  const central = all.filter(s => !isRegional(s));
  const areaOf = (s: Row): Area => ({...place(s), direct: receives(s, operation)});
  const unitOf = (s: Row, areas: Area[], standalone = false): Unit => {
    const unit = text(s, 'unit_code');
    return {...place(s), unit, label: standalone ? unit + ' · ' + text(s, 'name') : MATTER_LABELS[unit] ?? (unit + ' · ' + (unitNames.get(unit) || text(s, 'name'))), areas, standalone};
  };
  // En una SOL el area es una sugerencia (requested_area_scope): cualquier area habilitada de la unidad. En flujos y
  // entregas el servidor resuelve un area en la Direccion de su unidad, salvo la que recibe por si misma: solo esa se ofrece.
  const areasFor = (root: Row) => central
    .filter(s => text(s, 'area_code') && text(s, 'unit_code') === text(root, 'unit_code') && text(s, 'site_code') === text(root, 'site_code')
      && s.enabled !== false && (operation === 'SOL' || receives(s, operation)))
    .map(areaOf);
  const roots = central.filter(s => !text(s, 'area_code') && receives(s, operation));
  const units = roots.map(r => unitOf(r, areasFor(r)));
  // Un area receptora cuya unidad no recibe (Direccion deshabilitada) se ofrece como destino propio.
  const covered = new Set(units.flatMap(u => u.areas.map(a => a.id)));
  for (const s of central) if (text(s, 'area_code') && receives(s, operation) && !covered.has(number(s, 'id'))) units.push(unitOf(s, [], true));

  // Solo reciben las sedes ACTIVA (receives), asi que una PROYECTADA o CERRADA no aparece tampoco por su cobertura.
  const sites = all.filter(s => isRegional(s) && receives(s, operation));
  const regions: Region[] = list(catalog, 'regions')
    .map(r => {
      const code = text(r, 'code');
      const own = sites.filter(s => text(s, 'region_code') === code).map(place);
      const covering = sites.filter(s => text(s, 'region_code') !== code && coverageOf(s).includes(code)).map(s => ({...place(s), covering: true as const}));
      return {code, name: text(r, 'name'), sites: [...own, ...covering]};
    })
    .filter(r => r.sites.length > 0);
  const known = new Set(regions.map(r => r.code));
  const orphan = sites.filter(s => !known.has(text(s, 'region_code'))).map(place);
  if (orphan.length) regions.push({code: NO_REGION, name: 'Sede sin región asignada', sites: orphan});
  const centralCodes = Array.isArray(catalog.central_coverage) ? (catalog.central_coverage as unknown[]).map(String) : [];
  const centralRegions = list(catalog, 'regions').filter(r => centralCodes.includes(text(r, 'code'))).map(r => ({code: text(r, 'code'), name: text(r, 'name')}));
  return {operation, central: units, regions, centralRegions};
}

/** Regiones que coinciden con la busqueda (nombre de la region o de sus sedes, sin tildes, palabra por palabra). */
export const filterRegions = (regions: Region[], query: string) =>
  regions.filter(r => matchesWords(r.name + ' ' + r.sites.map(s => s.code + ' ' + s.name).join(' '), query));

export const regionOf = (d: Destinations, code: string) => d.regions.find(r => r.code === code);

/**
 * Propuesta por la region que entrega el servidor (suggested_region): la sede de esa region si es la unica que
 * recibe la operacion; con varias, solo la region. Sin sede que reciba, no hay propuesta.
 */
export function propose(d: Destinations, region: string | null | undefined): Choice | null {
  const r = region ? regionOf(d, region) : undefined;
  if (!r || r.code === NO_REGION) return null;
  return {level: 'REGIONAL', region: r.code, scope: r.sites.length === 1 ? r.sites[0].id : null, area: null, matter: ''};
}

/**
 * Region que atiende el nivel central (D18, D20), solo si no hay propuesta de sede (si una sede tambien la atiende,
 * rige la propuesta de sede) y el nivel central recibe la operacion. No propone unidad: el selector parte en el nivel
 * central (initialChoice) y lo indica con un aviso.
 */
export function centralRegion(d: Destinations, region: string | null | undefined): Named | undefined {
  if (!region || propose(d, region) || !d.central.length) return undefined;
  return d.centralRegions.find(r => r.code === region);
}

/** Sede elegida que atiende la region elegida por cobertura adicional (para explicarlo antes de confirmar). */
export function coveringSite(d: Destinations, c: Choice): Site | undefined {
  if (c.level !== 'REGIONAL' || c.scope == null) return undefined;
  const site = regionOf(d, c.region)?.sites.find(s => s.id === c.scope);
  return site?.covering ? site : undefined;
}

/** Eleccion inicial: la propuesta; si no, el nivel central (o el regional si no hay destinos centrales). */
export function initialChoice(d: Destinations, region?: string | null): Choice {
  return propose(d, region) ?? {level: d.central.length || !d.regions.length ? 'CENTRAL' : 'REGIONAL', scope: null, area: null, region: '', matter: ''};
}

export const levels = (d: Destinations): Level[] => [...(d.central.length ? ['CENTRAL' as const] : []), ...(d.regions.length ? ['REGIONAL' as const] : [])];

/** Cambiar el nivel limpia lo demas; elegir una region con una sola sede la deja elegida. */
export const withLevel = (level: Level): Choice => ({level, scope: null, area: null, region: '', matter: ''});
export function withRegion(d: Destinations, c: Choice, code: string): Choice {
  const r = regionOf(d, code);
  return {...c, region: code, scope: r && r.sites.length === 1 ? r.sites[0].id : null};
}
export const withUnit = (c: Choice, scope: number | null): Choice => ({...c, scope, area: null});

export const complete = (c: Choice) => c.scope != null;

/** Unidad elegida en el nivel central. */
export const unitOf = (d: Destinations, c: Choice) => c.level === 'CENTRAL' ? d.central.find(u => u.id === c.scope) : undefined;

/** Quien recibe lo enviado, para mostrarlo antes de confirmar. */
export function receiver(d: Destinations, c: Choice): string {
  if (!complete(c)) return '';
  if (c.level === 'REGIONAL') {
    const site = d.regions.flatMap(r => r.sites).find(s => s.id === c.scope);
    return 'Coordinación regional' + (site ? ' · ' + site.name : '');
  }
  const unit = unitOf(d, c), area = unit?.areas.find(a => a.id === c.area);
  if (!unit) return '';
  if (area?.direct) return 'Coordinación de ' + area.name;
  if (unit.standalone) return 'Coordinación de ' + unit.name;
  return 'Dirección ' + unit.unit + (area ? ' (área sugerida: ' + area.name + ')' : '');
}

/** Datos que envia cada comando. Los vacios se omiten: el servidor los trata como no indicados. */
export function payloadFor(op: Operation, c: Choice): Record<string, string | number> {
  if (c.scope == null) return {};
  const area = c.level === 'CENTRAL' ? c.area : null;
  if (op === 'SOL') return {
    scope_id: c.scope,
    ...(area != null ? {area_scope_id: area} : {}),
    ...(c.level === 'REGIONAL' && c.matter ? {suggested_matter: c.matter} : {}),
  };
  const target = area ?? c.scope;
  return op === 'ENTREGA' ? {scope_id: target} : {target_scope: target};
}
