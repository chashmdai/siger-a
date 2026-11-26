/**
 * Funciones de la sesion. Solo deciden que acciones mostrar: toda autorizacion se valida en el servidor.
 */
export type Grant = {role: string; scope: number | null};
/**
 * can_search: el servidor decide si la cuenta lee expedientes (buscador, G08b). can_report, can_report_people y can_export
 * (G11, G12): Reportes, su desglose por persona y la exportacion, cada una con REPORTES vigente en su alcance. La pantalla
 * no los calcula. can_manage_stats (E02, E13): Gestion estadistica vigente (cortes y series).
 */
export type User = {id: number; username: string; name: string; home_scope: number; must_change: boolean; grants: Grant[]; substitutions: number[];
  can_search?: boolean; can_report?: boolean; can_report_people?: boolean; can_export?: boolean; can_manage_stats?: boolean};

export const REGISTRAR_ROLES = ['ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION'];

export const has = (u: User, ...roles: string[]) => u.grants.some(g => roles.includes(g.role));
export const headAt = (u: User, id: number) => u.grants.some(g => g.scope === id && ['JEFATURA', 'DIRECCION'].includes(g.role));
/** Ambito en el que la cuenta puede registrar ingresos o primeras atenciones. */
export const registersAt = (u: User, id: number) => u.grants.some(g => g.scope === id && REGISTRAR_ROLES.includes(g.role));
