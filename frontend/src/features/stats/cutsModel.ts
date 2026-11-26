// Cortes estadisticos en la interfaz (0.8.0, D39, gate E13; DISENO 10.4): logica pura del asistente de cuatro pasos, de la
// lista y de la verificacion. El servidor (STAT_CUT_CREATE, GET /api/stats/cuts, POST /cuts/{id}/verify, STAT_CUT_ANNUL;
// contrato, seccion 11) congela, calcula las huellas y decide quien genera, ve y anula; aqui solo se arma el borrador.
import type {Row} from '../../lib/rows.ts';
import {list, text} from '../../lib/rows.ts';
import type {Period, Query} from './statsModel.ts';
import {periodLabel} from './statsModel.ts';

/** Aviso textual del paso de revision (GO de E13, C; decision A de E10). */
export const CUT_WARNING = 'Generar el corte detiene brevemente las operaciones de todos. Hazlo fuera del horario de atención';
/** Lo que congela un corte (diferencia de referencia de E10): toda la poblacion del periodo y del alcance, no los filtros. */
export const CUT_EXPLAIN = 'El corte congela todos los ingresos del periodo y del alcance elegidos, con sus valores de este momento. Los filtros, filas y columnas de la consulta no se guardan: se aplican al consultarlo.';
export const CUT_STEPS = ['Alcance', 'Periodo', 'Título y fundamento', 'Revisión'] as const;

export type ScopeOption = {id: number | null; label: string};
export type CutDraft = {scope: number | null | undefined; period: Period; title: string; reason: string};

/**
 * Alcances en que la cuenta puede generar un corte: los de su funcion Gestion estadistica (nulo = toda la institucion),
 * en el orden de sus concesiones. El servidor vuelve a decidirlo (STAT_CUT_CREATE).
 */
export function managedScopes(user: {grants: {role: string; scope: number | null}[]}, catalog: Row): ScopeOption[] {
  const scopes = list(catalog, 'scopes');
  const out: ScopeOption[] = [];
  for (const g of user.grants.filter(x => x.role === 'GESTION_ESTADISTICA')) {
    if (out.some(o => o.id === g.scope)) continue;
    out.push(g.scope == null ? {id: null, label: 'Toda la institución'} : {id: g.scope, label: text(scopes.find(s => Number(s.id) === g.scope) ?? {}, 'name') || 'Ámbito de la cuenta'});
  }
  return out.sort((a, b) => Number(a.id != null) - Number(b.id != null));
}

/** Borrador prellenado desde la consulta (periodo y titulo) con el primer alcance de la cuenta. */
export function cutDraft(q: Query | null, title: string, scopes: ScopeOption[], fallback: Period): CutDraft {
  const p = q?.period ?? fallback;
  const period: Period = {type: p.type, from: p.from, ...(p.to ? {to: p.to} : {})};
  return {scope: scopes.length ? scopes[0].id : undefined, period, title: (title ? 'Corte: ' + title : 'Corte estadístico').slice(0, 200), reason: ''};
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;
/** Lo que falta en un paso, en lenguaje llano; vacio = se puede continuar. */
export function stepProblem(d: CutDraft, step: number): string {
  if (step === 0 && d.scope === undefined) return 'Elige el alcance del corte.';
  if (step === 1) {
    if (!d.period.type || !DAY.test(d.period.from)) return 'Elige el periodo del corte.';
    if (d.period.type === 'PERSONALIZADO' && (!d.period.to || !DAY.test(d.period.to))) return 'Indica la fecha de término del periodo.';
    if (d.period.to && d.period.to < d.period.from) return 'La fecha de término no puede ser anterior a la de inicio.';
  }
  if (step === 2) {
    if (!d.title.trim()) return 'Escribe un título para el corte.';
    if (d.title.length > 200) return 'El título admite hasta 200 caracteres.';
    if (!d.reason.trim()) return 'Escribe el fundamento del corte.';
  }
  return '';
}
/** Primer paso con un problema (para no saltarse ninguno al confirmar), o -1. */
export const firstProblem = (d: CutDraft) => [0, 1, 2].find(s => stepProblem(d, s) !== '') ?? -1;

/** Cuerpo de STAT_CUT_CREATE (lista cerrada del contrato: sin date_basis ni filtros). */
export function cutBody(d: CutDraft): Row {
  const period: Row = {type: d.period.type, from: d.period.from};
  if (d.period.to && d.period.type === 'PERSONALIZADO') period.to = d.period.to;
  const body: Row = {title: d.title.trim(), period, reason: d.reason.trim()};
  if (d.scope != null) body.scope_id = d.scope;
  return body;
}

/** La misma consulta sobre el corte recien generado: solo valores vigentes, sin comparacion por periodo ni fecha. */
export function onCut(q: Query, cut: number): Query {
  return {...q, cut, mode: 'VIGENTE', asOf: '', compare: q.compare === 'CORTE' ? q.compare : '', compareCut: q.compare === 'CORTE' ? q.compareCut : null};
}

export type CutRow = {id: number; code: string; title: string; scopeLabel: string; period: string; asOf: string; state: string; rows: number; hash: string;
  createdBy: string; revision: number; canAnnul: boolean; periodRow: Row};
export function readCut(r: Row): CutRow {
  return {id: Number(r.id), code: text(r, 'code'), title: text(r, 'title'), scopeLabel: text(r, 'scope_label') || 'Toda la institución',
    period: periodLabel(r.period as Row), asOf: text(r, 'as_of'), state: text(r, 'state'), rows: Number(r.row_count ?? 0), hash: text(r, 'rows_sha256').slice(0, 12),
    createdBy: text(r, 'created_by'), revision: Number(r.revision ?? 0), canAnnul: r.can_annul === true, periodRow: (r.period ?? {}) as Row};
}
export const cutStateLabel = (state: string) => state === 'ANULADO' ? 'Anulado (se conserva y se puede consultar)' : 'Vigente';
/** Resultado de la verificacion de integridad en una frase. */
export function verifyText(r: Row): string {
  const exportOk = r.export_ok;
  if (r.ok === true) return 'Íntegro: las huellas recalculadas coinciden con las guardadas' + (exportOk === true ? ', también las del complemento para el paquete ZIP.' : '.');
  const parts = [r.rows_ok === false ? 'las filas congeladas' : '', r.metadata_ok === false ? 'los metadatos' : '', exportOk === false ? 'el complemento para el paquete ZIP' : ''].filter(Boolean);
  return 'Alterado: no coinciden ' + (parts.length ? parts.join(', ') : 'las huellas') + '. Avisa al equipo técnico; el corte no se modificó desde SIGER.';
}
/** Consulta sobre un corte: su periodo (el servidor exige uno dentro del corte). */
export function cutQuery(base: Query, c: CutRow): Query {
  const p = c.periodRow;
  return onCut({...base, period: {type: text(p, 'type'), from: text(p, 'from'), ...(text(p, 'type') === 'PERSONALIZADO' ? {to: text(p, 'to')} : {})}}, c.id);
}
