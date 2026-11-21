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

/** Funciones marcadas en un ambito (vista movil: contador de cada tarjeta). */
export const countIn = (profile: Profile, scope: number) => [...profile.values()].filter(e => e.scope === scope).length;

/** Texto comparable (lib/text.ts), reexportado para tests/profile.test.ts. */
export {fold} from '../../lib/text.ts';

/**
 * Ambitos de la vista movil. Con "solo ambitos con funciones" se listan los que tienen funciones marcadas o que la
 * cuenta ya tenia (desmarcar no hace desaparecer la tarjeta) y los que el usuario dejo abiertos. El buscador
 * compara codigo y nombre, cada palabra por separado.
 */
export function visibleScopes<S extends {id: number; code: string; name: string}>(scopes: S[], profile: Profile, live: Grant[], onlyWith: boolean, query: string, open: ReadonlySet<number> = new Set()) {
  const words = fold(query).split(' ').filter(Boolean);
  const held = new Set(live.map(g => g.scope_id));
  return scopes.filter(s => (!onlyWith || countIn(profile, s.id) > 0 || held.has(s.id) || open.has(s.id))
    && words.every(w => fold(s.code + ' ' + s.name).includes(w)));
}
