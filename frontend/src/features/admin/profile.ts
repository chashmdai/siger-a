/**
 * Perfil de una cuenta (PROFILE_SET, 0.5.0): logica pura, sin React, cubierta por tests/profile.test.ts.
 * El servidor decide: compara cada funcion pedida con las vigentes o programadas por (rol, ambito), el mismo
 * termino exacto (en milisegundos) y, si se envia, el mismo inicio. Aqui se replica esa comparacion solo para
 * mostrar el resumen "Se otorgan / Se revocan" y para no enviar un perfil sin cambios.
 */
import {fold} from '../../lib/text.ts';

export type RoleDef = {code: string; reach: string; group: string};
export type Grant = {id: number; user_id: number; role_code: string; scope_id: number | null; valid_from: number; valid_until: number | null; revoked_at: number | null};
/** Funcion marcada en el perfil. Las fechas van en milisegundos; from null = desde ahora, until null = sin termino. */
export type Entry = {role: string; scope: number | null; from: number | null; until: number | null; grant?: Grant};
export type Profile = Map<string, Entry>;
/** Funcion tal como se envia en data.grants. */
export type Wanted = {role: string; scope_id?: number; from_at?: string; until_at?: string};

export const TI = 'TI';
export const keyOf = (role: string, scope: number | null) => role + '@' + (scope ?? '');

const num = (v: unknown) => v == null || v === '' ? null : Number(v);

/** Normaliza las filas de administration.grants. */
export const grantOf = (r: Record<string, unknown>): Grant => ({
  id: Number(r.id), user_id: Number(r.user_id), role_code: String(r.role_code), scope_id: num(r.scope_id),
  valid_from: Number(r.valid_from), valid_until: num(r.valid_until), revoked_at: num(r.revoked_at),
});

/** Funciones que el servidor considera en PROFILE_SET: sin revocar y sin termino o con termino futuro (vigentes o programadas). */
export const liveGrants = (grants: Grant[], user: number, now: number) =>
  grants.filter(g => g.user_id === user && g.revoked_at == null && (g.valid_until == null || g.valid_until > now));

/** Estado de una fila de la tabla de funciones otorgadas. */
export function grantState(g: Grant, now: number) {
  if (g.revoked_at != null) return 'REVOCADA';
  if (g.valid_until != null && g.valid_until <= now) return 'VENCIDA';
  return g.valid_from > now ? 'PROGRAMADA' : 'VIGENTE';
}

/** Funciones con mas de un periodo en el mismo ambito: el perfil no puede representarlas (una entrada por rol y ambito). */
export function duplicates(live: Grant[]) {
  const seen = new Map<string, Grant[]>();
  for (const g of live) seen.set(keyOf(g.role_code, g.scope_id), [...(seen.get(keyOf(g.role_code, g.scope_id)) ?? []), g]);
  return [...seen.values()].filter(list => list.length > 1).map(list => list[0]);
}

/** Perfil inicial: una entrada por funcion vigente o programada, con sus fechas originales. */
export function initial(live: Grant[]): Profile {
  return new Map(live.map(g => [keyOf(g.role_code, g.scope_id), {role: g.role_code, scope: g.scope_id, from: g.valid_from, until: g.valid_until, grant: g}]));
}

/**
 * Activa o quita una funcion. TI es incompatible con cualquier otra (D2): al marcarla quedan solo TI;
 * con TI marcada no se agregan otras. Una funcion que la cuenta ya tenia vuelve con sus fechas originales.
 */
export function toggle(profile: Profile, role: string, scope: number | null, on: boolean, live: Grant[]): Profile {
  const key = keyOf(role, scope);
  if (!on) {const next = new Map(profile); next.delete(key); return next;}
  if (role !== TI && hasTi(profile)) return profile;
  const original = initial(live).get(key) ?? {role, scope, from: null, until: null};
  return role === TI ? new Map([[key, original]]) : new Map(profile).set(key, original);
}

export const hasTi = (profile: Profile) => [...profile.values()].some(e => e.role === TI);

const iso = (ms: number) => new Date(ms).toISOString();

/**
 * Cuerpo de data.grants. Una funcion existente reenvia su termino exacto (omitirlo la revocaria y la volveria
 * a otorgar sin termino) y su inicio solo si es programada o si se cambio; una nueva envia lo que se haya indicado.
 */
export function payload(profile: Profile, now: number): Wanted[] {
  return [...profile.values()].map(e => {
    const w: Wanted = {role: e.role};
    if (e.scope != null) w.scope_id = e.scope;
    const sendFrom = e.from != null && (!e.grant || e.grant.valid_from > now || e.from !== e.grant.valid_from);
    if (sendFrom) w.from_at = iso(e.from as number);
    if (e.until != null) w.until_at = iso(e.until);
    return w;
  });
}

/** Misma comparacion que Personnel.profile: lo que coincide se conserva; lo demas se revoca u otorga. */
export function diff(live: Grant[], wanted: Wanted[]) {
  const pool = [...live], grant: Wanted[] = [];
  for (const w of wanted) {
    const from = w.from_at ? Date.parse(w.from_at) : null, until = w.until_at ? Date.parse(w.until_at) : null;
    const i = pool.findIndex(g => g.role_code === w.role && g.scope_id === (w.scope_id ?? null) && g.valid_until === until && (from == null || g.valid_from === from));
    if (i >= 0) pool.splice(i, 1); else grant.push(w);
  }
  return {grant, revoke: pool};
}

/** Problemas que el servidor rechazaria por periodo; se muestran antes de confirmar. */
export function periodProblems(profile: Profile, now: number) {
  return [...profile.values()].filter(e => e.until != null && e.until <= Math.max(e.from ?? 0, now)).map(e => keyOf(e.role, e.scope));
}

/** Funciones marcadas en un ambito (vista movil: contador de cada tarjeta), sin las que esta vista no muestra (skip). */
export const countIn = (profile: Profile, scope: number, skip: ReadonlySet<string> = new Set()) => [...profile.values()].filter(e => e.scope === scope && !skip.has(e.role)).length;

/** Roles de ambito opcional (REPORTES, REPORTES_PERSONAS, EXPORTACION, GESTION_ESTADISTICA): bloque propio del perfil, por alcance. */
export const optionalRoles = (roles: RoleDef[]) => new Set(roles.filter(r => r.reach === 'OPCIONAL').map(r => r.code));
export const REPORTES = 'REPORTES';
/** Funciones que solo rigen con REPORTES en el mismo alcance (Roles.NEEDS_REPORTES en el servidor). */
export const NEEDS_REPORTES = new Set(['REPORTES_PERSONAS', 'EXPORTACION', 'GESTION_ESTADISTICA']);

/**
 * Filas del bloque "Reportes y exportacion": siempre "Toda la institucion" (null) y luego cada ambito con alguna de esas
 * funciones en el perfil o que el usuario agrego, en el orden de scopes (catalogo).
 */
export function reportScopes(profile: Profile, roles: RoleDef[], added: ReadonlySet<number>, order: number[]): (number | null)[] {
  const optional = optionalRoles(roles), used = new Set<number>(added);
  for (const e of profile.values()) if (e.scope != null && optional.has(e.role)) used.add(e.scope);
  const rank = new Map(order.map((id, i) => [id, i]));
  return [null, ...[...used].sort((a, b) => (rank.get(a) ?? order.length) - (rank.get(b) ?? order.length) || a - b)];
}
/** Una funcion dependiente no se marca sin REPORTES en el mismo alcance (si ya esta marcada, siempre se puede quitar). */
export const needsReportes = (profile: Profile, role: string, scope: number | null) =>
  NEEDS_REPORTES.has(role) && !profile.has(keyOf(role, scope)) && !profile.has(keyOf(REPORTES, scope));
/** Como toggle, pero quitar REPORTES quita tambien las funciones que dependen de ella en ese alcance (el servidor las rechazaria). */
export function toggleReport(profile: Profile, role: string, scope: number | null, on: boolean, live: Grant[]): Profile {
  let next = toggle(profile, role, scope, on, live);
  if (!on && role === REPORTES) for (const dependent of NEEDS_REPORTES) next = toggle(next, dependent, scope, false, live);
  return next;
}

/** Texto comparable (lib/text.ts), reexportado para tests/profile.test.ts. */
export {fold} from '../../lib/text.ts';

/**
 * Ambitos de la vista movil. Con "solo ambitos con funciones" se listan los que tienen funciones marcadas o que la
 * cuenta ya tenia (desmarcar no hace desaparecer la tarjeta) y los que el usuario dejo abiertos. El buscador
 * compara codigo y nombre, cada palabra por separado.
 */
export function visibleScopes<S extends {id: number; code: string; name: string; regionName?: string}>(scopes: S[], profile: Profile, live: Grant[], onlyWith: boolean, query: string, open: ReadonlySet<number> = new Set()) {
  const words = fold(query).split(' ').filter(Boolean);
  const held = new Set(live.map(g => g.scope_id));
  return scopes.filter(s => (!onlyWith || countIn(profile, s.id) > 0 || held.has(s.id) || open.has(s.id))
    && words.every(w => fold(s.code + ' ' + s.name + ' ' + (s.regionName ?? '')).includes(w)));
}

/** El filtro "solo con funciones" arranca activo solo si la cuenta ya tiene funciones por ambito (decision R03b). */
export const startFiltered = (live: Grant[]) => live.some(g => g.scope_id != null);

export type Level = 'CENTRAL' | 'REGIONAL';
/** Ambito del perfil, a partir de catalog.scopes (level, state, region_code, enabled). */
export type ScopeItem = {id: number; code: string; name: string; level: Level; state: string; region: string | null; regionName: string; staffable: boolean};
type Region = {code: string; name: string};

/** Admite funciones nuevas: activo, o sede PROYECTADA (dotacion preparada; regla Access.staffableScope). Nunca CERRADA. */
export const staffable = (s: {enabled?: unknown; state?: unknown}) => s.enabled !== false || s.state === 'PROYECTADA';

/**
 * Ambitos del perfil: los que admiten funciones (activos y sedes proyectadas, aunque no tengan funciones) y los
 * cerrados o deshabilitados solo si la cuenta tiene funciones en ellos (para poder revocarlas).
 */
export function profileScopes(scopes: Record<string, unknown>[], live: Grant[], regions: Region[]): ScopeItem[] {
  const used = new Set(live.map(g => g.scope_id)), names = new Map(regions.map(r => [r.code, r.name]));
  return scopes.filter(s => staffable(s) || used.has(Number(s.id))).map(s => {
    const region = s.region_code == null || s.region_code === '' ? null : String(s.region_code);
    return {id: Number(s.id), code: String(s.code ?? ''), name: String(s.name ?? ''), level: s.level === 'REGIONAL' || s.regional === true ? 'REGIONAL' : 'CENTRAL',
      state: String(s.state ?? (s.enabled === false ? 'CERRADA' : 'ACTIVA')), region, regionName: region ? names.get(region) ?? region : '', staffable: staffable(s)};
  });
}

/**
 * Grupos Nivel central / Sedes regionales (se omite el vacio). Las sedes siguen el orden de catalog.regions (de norte
 * a sur) y, dentro de una region, el codigo; las sedes sin region van al final.
 */
export function groupScopes<S extends {level: Level; region: string | null; code: string}>(items: S[], regions: Region[]): {level: Level; items: S[]}[] {
  const rank = new Map(regions.map((r, i) => [r.code, i]));
  const at = (s: S) => s.region != null && rank.has(s.region) ? rank.get(s.region) as number : regions.length;
  const sites = items.filter(s => s.level === 'REGIONAL').sort((a, b) => at(a) - at(b) || a.code.localeCompare(b.code, 'es'));
  return [{level: 'CENTRAL' as Level, items: items.filter(s => s.level === 'CENTRAL')}, {level: 'REGIONAL' as Level, items: sites}].filter(g => g.items.length > 0);
}

/** En las sedes no hay Jefatura (D7): se muestra solo si la cuenta conserva una heredada en una sede, para revocarla. */
export const JEFATURA = 'JEFATURA';
export function columnsFor(level: Level, roles: RoleDef[], live: Grant[], scopes: ScopeItem[]) {
  if (level === 'CENTRAL') return roles;
  const sites = new Set(scopes.filter(s => s.level === 'REGIONAL').map(s => s.id));
  const inherited = live.some(g => g.role_code === JEFATURA && sites.has(g.scope_id as number));
  return roles.filter(r => r.code !== JEFATURA || inherited);
}

/** Se puede agregar la funcion en el ambito: admite funciones y no es Jefatura en una sede. */
export const offered = (role: string, scope: ScopeItem) => scope.staffable && !(role === JEFATURA && scope.level === 'REGIONAL');
