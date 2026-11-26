// Panel de Calidad (0.8.0, D28, gate E13; DISENO 10.4): completitud por variable, por ambito y su tendencia mensual con la
// medida COMPLETITUD del motor (porcentajes del servidor; nada se suma ni se divide aqui), y el enlace a los registros
// pendientes de la bandeja con el filtro "Con datos estadisticos pendientes" (BoardQueries, misma regla).
import type {User} from '../../lib/access';
import {menuFor, menuItems} from '../../app/menu.ts';
import {boardRoute} from '../../app/routes.ts';
import type {Period, Query, TableModel} from './statsModel.ts';
import {emptyQuery, fmtPct} from './statsModel.ts';

export const PENDING_FILTER = 'stat_pending';
export const NO_BOARD_TEXT = 'Los registros pendientes los completa quien los gestiona; tu cuenta no tiene esa bandeja';
export const REGION_NOTE = 'La región del requirente no se clasifica: se muestra su porcentaje «Sin información» y no tiene enlace a una bandeja.';
/** Estados en el orden de las barras: informado, estados especiales y pendiente al final. */
export const STATE_ORDER = ['INFORMADO', 'SIN_INFORMACION', 'NO_DECLARADO', 'NO_APLICA', 'PENDIENTE'];

const base = (period: Period): Query => ({...emptyQuery(period), measure: 'COMPLETITUD', percent: 'FILA'});
/** Estado de cada variable (pares requerimiento-variable), con el porcentaje por fila del servidor. */
export const byVariable = (period: Period): Query => ({...base(period), rows: ['VARIABLE'], columns: ['ESTADO_VARIABLE']});
/** Una variable por ambito de referencia. */
export const byScope = (period: Period, variable: string): Query => ({...base(period), rows: ['AMBITO'], columns: ['ESTADO_VARIABLE'], filters: {VARIABLE: [variable]}});
/** Una variable mes a mes. */
export const byMonth = (period: Period, variable: string): Query => ({...base(period), rows: ['PERIODO_MES'], columns: ['ESTADO_VARIABLE'], filters: {VARIABLE: [variable]}});

/** Partes de una barra: cada estado con su porcentaje y su cantidad, tal como los entrega el servidor. */
export function stateParts(t: TableModel, row: string[]): {state: string; label: string; percent: number; n: number}[] {
  const out: {state: string; label: string; percent: number; n: number}[] = [];
  for (const col of t.columns) {
    const c = t.value(row, col.key);
    if (!c || c.n === 0) continue;
    out.push({state: col.key[0], label: col.labels[0], percent: c.percent ?? 0, n: c.n});
  }
  return out.sort((a, b) => STATE_ORDER.indexOf(a.state) - STATE_ORDER.indexOf(b.state));
}
/** Porcentaje de un estado en una fila (del servidor; 0 si no hay celda). */
export function share(t: TableModel, row: string[], state: string): number {
  const c = t.value(row, [state]);
  return c?.percent ?? 0;
}
/** Frase de una barra: "Informado 60 %; Pendiente 40 %". */
export const partsText = (parts: {label: string; percent: number}[]) => parts.map(p => `${p.label} ${fmtPct(p.percent)}`).join('; ') || 'Sin registros en el periodo';

/** Bandeja de los registros pendientes de la variable: las del ingreso en COM, derecho y contexto en REQ; la region, ninguna. */
export function boardFor(variable: string): 'com' | 'req' | null {
  if (['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO'].includes(variable)) return 'com';
  if (['DERECHO', 'CONTEXTO'].includes(variable)) return 'req';
  return null;
}
/**
 * Enlace a los pendientes (condicion 1 del GO de E13): solo si la cuenta tiene esa bandeja en su menu; si no, la frase.
 * La bandeja aplica sus propias reglas de lectura.
 */
export function pendingLink(user: User, variable: string): {href: string; text: string} | {href: null; text: string} | null {
  const kind = boardFor(variable);
  if (!kind) return null;
  const has = menuItems(menuFor(user, 0)).some(i => i.path === kind);
  if (!has) return {href: null, text: NO_BOARD_TEXT + '.'};
  return {href: '#' + boardRoute(kind, PENDING_FILTER), text: kind === 'com' ? 'Ver las comunicaciones con datos pendientes' : 'Ver los requerimientos con datos pendientes'};
}
