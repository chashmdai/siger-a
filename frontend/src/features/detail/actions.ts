import type {Row} from '../../lib/rows';
import {list, localTime, number, text} from '../../lib/rows';
import type {Field} from '../../components/ActionDialog';
import {fixedSelect, objective, reason, REMINDERS, scopeField} from '../../lib/fields';
import {people} from '../../lib/catalog';
import type {Context} from '../../app/context';
import {FLOW_LABELS} from '../../lib/labels';

/** Actuacion de REQ (sin seguimiento) o de Caso (con reserva opcional). */
export function noteFields(noCase = false): Field[] {
  return [
    {key: 'body', label: 'Actuación efectivamente realizada', type: 'textarea', required: true, max: 20000},
    {key: 'effective_at', label: 'Fecha efectiva de la gestión', type: 'datetime-local', required: true, value: localTime()},
    noCase
      ? {key: 'no_followup', label: 'Atención sin seguimiento posterior; si requiere seguimiento debe existir un Caso.', type: 'checkbox', required: true}
      : {key: 'reserved', label: 'Constancia reservada (responsable y jefatura)', type: 'checkbox'},
  ];
}

/** Responsable e integrantes del Caso: los unicos ejecutores posibles de una tarea. */
export function teamOptions(ctx: Context, c: Row) {
  const team = [number(c, 'responsible_id'), ...list(c, 'members').map(m => number(m, 'user_id'))];
  return people(ctx.catalog, number(c, 'scope_id')).filter(p => team.includes(Number(p.value)));
}

export function taskFields(ctx: Context, c: Row): Field[] {
  return [
    {key: 'title', label: 'Qué hay que hacer', required: true, max: 300},
    {key: 'executor_id', label: 'Responsable de esta tarea (equipo del Caso)', required: true, type: 'select', options: teamOptions(ctx, c), value: number(c, 'responsible_id')},
    {key: 'due_at', label: 'Cuándo recordarlo (fecha y hora)', type: 'datetime-local', required: true, value: localTime(3600000)},
    {key: 'remind_every_hours', label: 'Repetición del aviso interno', type: 'select', options: REMINDERS, value: '24', required: true},
  ];
}

export function taskEditFields(ctx: Context, c: Row, t: Row): Field[] {
  return [
    {key: 'executor_id', label: 'Responsable del equipo', type: 'select', required: true, options: teamOptions(ctx, c), value: number(t, 'executor_id')},
    {key: 'due_at', label: 'Nueva fecha y hora de recordatorio', type: 'datetime-local', required: true, value: t.due_ms ? localTime(Number(t.due_ms) - Date.now()) : localTime(3600000)},
    {key: 'remind_every_hours', label: 'Repetición del aviso', type: 'select', required: true, options: REMINDERS, value: String(t.reminder_interval_hours ?? 24)},
    reason,
  ];
}

const FLOW_TITLES: Record<string, string> = {DERIVACION: 'Solicitar derivación del Caso', TRANSFERENCIA: 'Transferir principal del REQ', COLABORACION: 'Solicitar colaboración'};
const FLOW_NOTES: Record<string, string> = {
  TRANSFERENCIA: 'El principal cambia solo al aceptar. Los Casos y sus tareas permanecen en sus ambitos.',
  DERIVACION: 'Mientras está pendiente, el origen sigue abierto. Aceptar crea el sucesor y cierra el origen en una misma operación.',
  COLABORACION: 'La unidad destino acepta y crea su Caso. No cambia el principal del REQ.',
};

/**
 * Peticion de colaboracion, transferencia o derivacion. Se registra sobre el REQ; desde un Caso
 * usa el REQ padre y su revision, y la derivacion identifica el Caso de origen.
 */
export function requestFlow(ctx: Context, kind: string, r: Row, flowKind: string) {
  const fromCase = kind === 'CAS';
  const fields: Field[] = [fixedSelect('kind', 'Operación', flowKind, FLOW_LABELS[flowKind] ?? flowKind), scopeField(ctx.catalog, 'target_scope'), reason];
  if (flowKind !== 'TRANSFERENCIA') fields.push({...objective, value: fromCase ? text(r, 'objective') : ''});
  if (flowKind === 'DERIVACION') fields.push(fixedSelect('case_id', 'Caso de origen', number(r, 'id'), text(r, 'folio')));
  ctx.act({
    title: FLOW_TITLES[flowKind] ?? FLOW_TITLES.COLABORACION, type: 'FLOW_REQUEST',
    id: fromCase ? number(r, 'requirement_id') : number(r, 'id'),
    revision: fromCase ? number(r, 'req_revision') : number(r, 'revision'),
    fields, note: FLOW_NOTES[flowKind] ?? FLOW_NOTES.COLABORACION,
  });
}

/** Solicitud de nueva gestion de un REQ cerrado o de un Caso cerrado (caseId). */
export function requestReopening(ctx: Context, kind: string, r: Row, caseId?: number) {
  ctx.act({
    title: 'Solicitar nueva gestión o reapertura', type: 'REOPEN_REQUEST',
    id: kind === 'CAS' ? number(r, 'requirement_id') : number(r, 'id'), revision: null,
    fields: [reason, ...(caseId ? [fixedSelect('case_id', 'Caso', caseId, text(r, 'folio'))] : [])],
    note: 'No se reabre por recibir antecedentes. Se registra una solicitud y luego una decisión de jefatura. Un REQ cerrado requiere la autorización de su principal.',
  });
}
