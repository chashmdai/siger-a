/**
 * Panel Estructura (R03b, TI): logica pura sobre administration.structure, sin React; cubierta por
 * tests/structure.test.ts. El servidor decide cada accion (SCOPE_UPDATE, SCOPE_ACTIVATE, SCOPE_CLOSE); aqui solo se
 * ordena, se agrupa y se decide que botones mostrar, con los mismos bloqueos que publica el servidor.
 */
import type {Grant, Level} from './profile.ts';
import {groupScopes, liveGrants} from './profile.ts';

type Region = {code: string; name: string};
/** Fila de administration.structure, con la region normalizada para agrupar. */
export type ScopeRow = Record<string, unknown> & {id: number; code: string; level: Level; region: string | null; state: string};

export const scopeRow = (r: Record<string, unknown>): ScopeRow => ({...r, id: Number(r.id), code: String(r.code ?? ''),
  level: r.level === 'REGIONAL' ? 'REGIONAL' : 'CENTRAL', region: r.region_code == null || r.region_code === '' ? null : String(r.region_code), state: String(r.state ?? 'ACTIVA')});

/** Nivel central (orden del servidor) y sedes regionales (orden de catalog.regions; sin region al final). */
export function structureGroups(rows: Record<string, unknown>[], regions: Region[]) {
  const groups = groupScopes(rows.map(scopeRow), regions);
  return {central: groups.find(g => g.level === 'CENTRAL')?.items ?? [], sites: groups.find(g => g.level === 'REGIONAL')?.items ?? []};
}

/** Sede sin region asignada (por ejemplo, las existentes antes de 0.5.0): no se propone por region ni se activa. */
export const missingRegion = (r: ScopeRow) => r.level === 'REGIONAL' && r.region == null;

export const blockersOf = (r: Record<string, unknown>) => ((r.close_blockers ?? []) as unknown[]).map(String);

/**
 * Acciones de una fila. Editar: todo ambito no cerrado. Activar: sede PROYECTADA (exige region). Cerrar: sede ACTIVA,
 * deshabilitado mientras el servidor informe bloqueos.
 */
export function actionsFor(r: ScopeRow) {
  const site = r.level === 'REGIONAL';
  return {
    edit: r.state !== 'CERRADA',
    activate: site && r.state === 'PROYECTADA',
    activateBlocked: site && r.state === 'PROYECTADA' && missingRegion(r) ? 'Asigna la región de la sede con Editar antes de activarla.' : '',
    close: site && r.state === 'ACTIVA',
    closeBlocked: site && r.state === 'ACTIVA' ? blockersOf(r) : [],
  };
}

/** Volumen abierto en texto: REQ abiertos, Casos abiertos y SOL pendientes. */
export function volume(r: Record<string, unknown>) {
  const n = (k: string) => Number(r[k] ?? 0);
  return `${n('open_req')} REQ · ${n('open_cases')} ${n('open_cases') === 1 ? 'Caso' : 'Casos'} · ${n('pending_sol')} SOL`;
}

export type Member = {user: Record<string, unknown>; roles: string[]; home: boolean};
/**
 * Funcionarios del ambito: cuentas con funciones vigentes o programadas en el (la misma dotacion que cuenta el
 * servidor) y cuentas con adscripcion principal en el, que tambien impiden cerrar una sede. Orden por nombre.
 */
export function membersOf(scope: number, users: Record<string, unknown>[], grants: Grant[], now: number): Member[] {
  return users.map(u => {
    const id = Number(u.id);
    const roles = [...new Set(liveGrants(grants, id, now).filter(g => g.scope_id === scope).map(g => g.role_code))];
    return {user: u, roles, home: Number(u.home_scope) === scope};
  }).filter(m => m.roles.length > 0 || m.home)
    .sort((a, b) => String(a.user.name ?? '').localeCompare(String(b.user.name ?? ''), 'es'));
}
