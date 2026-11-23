/**
 * Panel Estructura (R03b, TI): logica pura sobre administration.structure, sin React; cubierta por
 * tests/structure.test.ts. El servidor decide cada accion (SCOPE_UPDATE, SCOPE_ACTIVATE, SCOPE_CLOSE); aqui solo se
 * ordena, se agrupa y se decide que botones mostrar, con los mismos bloqueos que publica el servidor.
 */
import type {Grant, Level} from './profile.ts';
import {groupScopes, liveGrants} from './profile.ts';
import type {Action, Field, Review} from '../../components/ActionDialog';
import {localInput} from '../../lib/rows.ts';

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

// --- Cobertura territorial (0.7.0, D18-D19; gate T03) ----------------------------------------------------------------
// El servidor (Territory) decide quien atiende cada region y calcula el efecto; aqui solo se arma la vista previa, se
// rotula y se fija la confirmacion: regiones_sin_sede es exactamente la lista que entrega el servidor (mismo orden) y no
// se envia si ninguna region queda sin atencion (el servidor responde 409 a una lista de mas).

/** Cambio territorial que se revisa antes de confirmar (POST /api/administration/territorial-effect). */
export type TerritorialKind = 'CIERRE' | 'COBERTURA' | 'REGION' | 'NIVEL_CENTRAL';

/** Codigos de region de una lista del servidor (cobertura, central_coverage.regions, regiones_sin_sede). */
export const codes = (v: unknown) => Array.isArray(v) ? v.map(String) : [];
/** Region con su nombre y su codigo, como la nombra el servidor: "Atacama (03)". */
export const regionLabel = (regions: Region[], code: string | null | undefined) =>
  code == null || code === '' ? 'Sin región' : (regions.find(r => r.code === code)?.name ?? code) + ' (' + code + ')';
/** Cobertura adicional vigente de la sede (en una proyectada, preparada). */
export const coverageOf = (r: Record<string, unknown>) => codes(r.coverage);
/** Solo una sede regional no cerrada tiene cobertura adicional editable. */
export const canEditCoverage = (r: ScopeRow) => r.level === 'REGIONAL' && r.state !== 'CERRADA';
/** Regiones que se pueden agregar a la cobertura: todas menos la region fisica, que siempre esta cubierta. */
export const coverageOptions = (r: ScopeRow, regions: Region[]) => regions.filter(x => x.code !== r.region);
/** Editar una sede pide la revision solo si cambia su region fisica. */
export const regionChanged = (r: ScopeRow, payload: Record<string, unknown>) =>
  r.level === 'REGIONAL' && String(payload.region_code ?? '') !== (r.region ?? '');

/** Cuerpo de la vista previa: las mismas claves que el comando que se va a confirmar. */
export function previewBody(kind: TerritorialKind, scope: number | null, payload: Record<string, unknown>): Record<string, unknown> {
  switch (kind) {
    case 'CIERRE': return {kind, scope_id: scope};
    case 'COBERTURA': return {kind, scope_id: scope, regions: codes(payload.regions)};
    case 'REGION': return {kind, scope_id: scope, region_code: payload.region_code};
    case 'NIVEL_CENTRAL': return {kind, regions: codes(payload.regions)};
  }
}

/** Lista en texto: "A", "A y B", "A, B y C". */
const joined = (items: string[]) => items.length < 2 ? items.join('') : items.slice(0, -1).join(', ') + ' y ' + items[items.length - 1];

export type EffectLine = {region: string; label: string; text: string; unattended: boolean};
/**
 * Una linea por region que hoy atiende la sede (o el nivel central), en el orden del servidor: quien la atendera
 * despues (sedes activas y nivel central) o "Queda sin atención"; las sedes proyectadas solo como dato.
 */
export function effectLines(effect: Record<string, unknown>[], regions: Region[]): EffectLine[] {
  return effect.map(e => {
    const region = String(e.region ?? ''), sites = codes(e.attended_by), projected = codes(e.projected);
    const who = [...sites, ...(e.central === true ? ['el nivel central'] : [])];
    const unattended = e.unattended === true;
    const base = unattended ? 'Queda sin atención' : 'Sigue atendida por ' + joined(who);
    const later = projected.length ? '. ' + (projected.length === 1 ? 'La sede proyectada ' + projected[0] + ' la atenderá' : 'Las sedes proyectadas ' + joined(projected) + ' la atenderán') + ' al activarse' : '';
    return {region, label: regionLabel(regions, region), text: base + later + '.', unattended};
  });
}

/**
 * Confirmacion de la orden (D19): sin regiones afectadas no se envia nada; con regiones, regiones_sin_sede es la
 * lista del servidor tal cual y una casilla obligatoria las nombra todas.
 */
export function confirmationOf(preview: Record<string, unknown>, regions: Region[]): {unattended: string[]; extra: Record<string, unknown>; confirm?: string} {
  const unattended = codes(preview.regiones_sin_sede);
  if (!unattended.length) return {unattended, extra: {}};
  const names = unattended.map(code => regionLabel(regions, code)).join(', ');
  return {unattended, extra: {regiones_sin_sede: unattended},
    confirm: `Confirmo que ${names} ${unattended.length === 1 ? 'queda' : 'quedan'} sin atención de una sede activa o del nivel central, y que el fundamento registra la decisión institucional.`};
}

export type Period = {id: number; region: string; label: string; open: boolean; from: number; by: number; basis: string; until: number | null; untilBy: number | null; untilBasis: string};
/** Periodos de cobertura (historia), en el orden del servidor (del mas reciente al mas antiguo); abierto = vigente. */
export const periodsOf = (history: Record<string, unknown>[], regions: Region[]): Period[] => history.map(p => ({
  id: Number(p.id), region: String(p.region_code ?? ''), label: regionLabel(regions, String(p.region_code ?? '')), open: p.until_ms == null,
  from: Number(p.from_ms ?? 0), by: Number(p.from_by ?? 0), basis: String(p.from_basis ?? ''),
  until: p.until_ms == null ? null : Number(p.until_ms), untilBy: p.until_by == null ? null : Number(p.until_by), untilBasis: String(p.until_basis ?? ''),
}));

/** Vista previa del efecto territorial de una orden (Structure.tsx la pide al servidor y arma su contenido). */
export type Reviewer = (kind: TerritorialKind, scope: number | null) => (payload: Record<string, unknown>) => Promise<Review>;
/**
 * Ordenes de TI con efecto territorial (D19), con la revision previa que exige la condicion de T03: Cerrar (CIERRE),
 * Cobertura de una sede (COBERTURA), Editar una sede con cambio de region (REGION; sin cambio, sin revision) y la
 * cobertura del nivel central (NIVEL_CENTRAL, id 0 y revision = central_coverage.revision).
 */
export function territorialOrders({regions, central, reason, reviewer, failed}: {regions: Region[]; central: Record<string, unknown>; reason: Field;
  reviewer: Reviewer; failed: (e: Error & {status?: number}) => void}) {
  const num = (r: Record<string, unknown>, k: string) => Number(r[k] ?? 0);
  const options = (list: Region[]) => list.map(x => ({value: x.code, label: x.name}));
  return {
    edit: (r: ScopeRow): Action => ({title: 'Editar ' + (r.level === 'REGIONAL' ? 'sede ' : 'ámbito ') + r.code, type: 'SCOPE_UPDATE', id: r.id, revision: num(r, 'revision'), fields: [
      {key: 'name', label: 'Nombre', required: true, max: 160, value: String(r.name ?? '')},
      ...(r.level === 'REGIONAL' ? [{key: 'region_code', label: 'Región', type: 'select' as const, required: true, value: r.region ?? '', options: options(regions)}] : []),
      {key: 'opens_at', label: 'Fecha de apertura (opcional)', type: 'datetime-local', value: localInput(num(r, 'opens_ms') || null), exact: num(r, 'opens_ms') || null},
      reason,
    ], note: 'El nivel, la unidad y el código no cambian. ' + (r.level === 'REGIONAL' ? 'La región física siempre la cubre la sede y permite proponerla al elegir un destino; si cambia, antes de confirmar se muestra el efecto territorial.' : ''),
    review: r.level === 'REGIONAL' ? payload => regionChanged(r, payload) ? reviewer('REGION', r.id)(payload) : Promise.resolve(null) : undefined,
    failed: r.level === 'REGIONAL' ? failed : undefined}),
    close: (r: ScopeRow): Action => ({title: 'Cerrar sede ' + r.code, type: 'SCOPE_CLOSE', id: r.id, revision: num(r, 'revision'), fields: [reason],
      note: 'La sede deja de recibir comunicaciones, cuentas y funciones. Sus expedientes siguen legibles. Su cobertura adicional termina en el mismo acto; antes de confirmar se muestra el efecto territorial.',
      review: reviewer('CIERRE', r.id), failed}),
    coverage: (r: ScopeRow): Action => ({title: 'Cobertura de la sede ' + r.code, type: 'SCOPE_COVERAGE', id: r.id, revision: num(r, 'revision'), fields: [
      {key: 'regions', label: 'Regiones que atiende además de su región física', type: 'checklist', value: coverageOf(r), options: options(coverageOptions(r, regions))},
      reason,
    ], note: (r.region ? 'Su región física, ' + regionLabel(regions, r.region) + ', siempre está cubierta y no se quita aquí. ' : 'La sede aún no tiene región física. ')
      + 'Quitar una región termina su período y agregarla abre uno nuevo; la historia se conserva.' + (r.state === 'PROYECTADA' ? ' La sede está proyectada: la cobertura queda preparada y rige al activarla.' : ''),
    review: reviewer('COBERTURA', r.id), failed}),
    central: (): Action => ({title: 'Cobertura del nivel central', type: 'CENTRAL_COVERAGE', id: 0, revision: num(central, 'revision'), fields: [
      {key: 'regions', label: 'Regiones que atiende el nivel central (Oficina Nacional), sin sede regional', type: 'checklist', value: codes(central.regions), options: options(regions)},
      reason,
    ], note: 'Una región atendida por el nivel central no genera el aviso de región sin sede. No agrega destinos: al distribuir, el selector parte en el nivel central y la unidad la elige quien distribuye.',
    review: reviewer('NIVEL_CENTRAL', null), failed}),
  };
}
