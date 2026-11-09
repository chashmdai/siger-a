/**
 * Funciones de la sesion. Solo deciden que acciones mostrar: toda autorizacion se valida en el servidor.
 */
export type Grant = {role: string; scope: number | null};
export type User = {id: number; username: string; name: string; home_scope: number; must_change: boolean; grants: Grant[]; substitutions: number[]};

export const REGISTRAR_ROLES = ['ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION'];

export const has = (u: User, ...roles: string[]) => u.grants.some(g => roles.includes(g.role));
export const headAt = (u: User, id: number) => u.grants.some(g => g.scope === id && ['JEFATURA', 'DIRECCION'].includes(g.role));
export const authorityAt = (u: User, id: number) => u.grants.some(g => g.scope === id && g.role === 'AUTORIDAD');
/** Ambito en el que la cuenta puede registrar ingresos o primeras atenciones. */
export const registersAt = (u: User, id: number) => u.grants.some(g => g.scope === id && REGISTRAR_ROLES.includes(g.role));
