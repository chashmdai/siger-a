import type {Row} from '../../lib/rows';
import {bool, list, number, text, when} from '../../lib/rows';
import {nameOf, scopeOf} from '../../lib/catalog';
import {choose, fixedSelect, matter, person, reason} from '../../lib/fields';
import {humanize, initials} from '../../lib/labels';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {Kind, Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import {Callout, EmptyState, Facts, Panel} from '../../components/Primitives';
import {blockersOf, ClosureStatus} from './ClosureStatus';
import {Files} from './Files';
import {Notes} from './Notes';
import {noteFields, requestFlow, requestReopening, taskEditFields, taskFields} from './actions';
import {ActionBar, DetailGrid} from './parts';

const OPEN_TASK = ['PENDIENTE', 'EN_CURSO'];

/**
 * Caso: una linea de gestion del REQ. El responsable gestiona y solicita termino; la jefatura
 * asigna, decide el cierre (salvo BAJA o jefatura propia) y deriva. Las tareas no son actuaciones.
 */
export function CaseDetail({r, ctx}: {r: Row; ctx: Context}) {
  const id = number(r, 'id'), state = text(r, 'state'), closed = state === 'CERRADO';
  const head = bool(r, 'can_head'), write = bool(r, 'can_write'), owner = bool(r, 'is_owner');
  const choice = (title: string, type: string, fields: Field[], note?: string) => choose(ctx, title, type, r, fields, note);
  const pendingTasks = number(r, 'pending_tasks');
  const pendingTasksTitle = (fallback: string) => pendingTasks > 0 ? 'Resuelve primero las tareas pendientes' : fallback;
  // Solicitar término (responsable) y confirmar el cierre son facultades distintas: cada una con su evaluación del servidor.
  const requestBlockers = blockersOf(r, 'close_request_blockers');
  const unacknowledged = list(r, 'assignments').some(x => bool(x, 'current_flag') && !x.acknowledged_ms);
  // Confirmar cierre: jefatura propia, responsable de Caso BAJA o jefatura en revision; nunca el subrogante en su Caso.
  const mayConfirmClose = !closed && !bool(r, 'own_substitution') && (head && owner || owner && text(r, 'req_complexity') === 'BAJA' || head && state === 'EN_REVISION');
  const tasks = list(r, 'tasks');

  return <DetailGrid
    main={<>
      {!write && <Callout tone="neutral">Consulta autorizada. Poder leer no concede facultad de gestionar, reasignar ni cerrar.</Callout>}
      {state === 'EN_REVISION' && <Callout tone="warn" role="status" title="En revisión de cierre">El responsable solicitó terminar este Caso; aún no está cerrado y la jefatura debe decidir. Mientras siga pendiente, el responsable puede retirar su solicitud y volver a gestión.</Callout>}
      {bool(r, 'own_substitution') && !closed && <Callout tone="warn">Ejerces subrogancia sobre tu propio Caso: puedes solicitar el término, pero no autoconfirmarlo. La revisión excepcional por un tercero aún no está disponible.</Callout>}
      <ActionBar label="Gestión">
        {owner && unacknowledged && <button className="primary" onClick={() => choice('Acusar asignación', 'CASE_ACK', [], 'La responsabilidad ya es efectiva. Este acuse es manual y no registra una actuación.')}><Icon name="check" size={18}/>Acusar asignación</button>}
        {!closed && write && <>
          <button className={(owner && unacknowledged) || (mayConfirmClose && state === 'EN_REVISION') ? '' : 'primary'} onClick={() => choice('Registrar actuación', 'CASE_NOTE', noteFields())}><Icon name="file" size={18}/>Registrar actuación</button>
          <button onClick={() => choice('Crear tarea del Caso', 'TASK_CREATE', taskFields(ctx, r))}><Icon name="tasks" size={18}/>Nueva tarea</button>
        </>}
        {!closed && (head || owner) && <>
          <button onClick={() => choice('Incorporar colaborador al equipo', 'CASE_MEMBER_ADD', [person(ctx.catalog, 'user_id', 'Colaborador del mismo ámbito', number(r, 'scope_id')), reason])}><Icon name="people" size={18}/>Agregar integrante</button>
          {owner && state !== 'EN_REVISION' && <button disabled={!bool(r, 'can_request_close')} title={requestBlockers[0] ?? 'Solicitar término'} onClick={() => choice('Solicitar término', 'CASE_REQUEST_CLOSE', [reason], 'El Caso pasa a revisión de cierre. Debes resolver primero todas las tareas pendientes.')}><Icon name="flag" size={18}/>Solicitar cierre</button>}
          {owner && state === 'EN_REVISION' && <button onClick={() => choice('Retirar solicitud de cierre', 'CASE_WITHDRAW_CLOSE', [reason], 'El Caso volverá a En gestión. Se conserva la solicitud original y se avisa a la jefatura.')}><Icon name="arrowLeft" size={18}/>Retirar mi solicitud de cierre</button>}
        </>}
        {mayConfirmClose && <button className={state === 'EN_REVISION' ? 'primary' : ''} disabled={!bool(r, 'can_close')} title={pendingTasksTitle('Cerrar Caso')} onClick={() => choice('Cerrar este Caso', 'CASE_CLOSE', [reason], 'Solo cierra este Caso. Las tareas pendientes impiden el cierre: deben estar completadas o canceladas con fundamento.')}><Icon name="lock" size={18}/>Confirmar cierre</button>}
        {closed && owner && text(r, 'closure_mode') !== 'DERIVACION' && <button onClick={() => requestReopening(ctx, 'CAS', r, id)}><Icon name="refresh" size={18}/>Solicitar reapertura</button>}
      </ActionBar>
      {!closed && head && <ActionBar label="Jefatura">
        <button onClick={() => choice('Reasignar responsable', 'CASE_ASSIGN', [person(ctx.catalog, 'responsible_id', 'Nuevo responsable del Caso', number(r, 'scope_id')), reason], 'Todas las tareas pendientes del Caso pasan al nuevo profesional, incluidas las de colaboradores. No se modifican las terminadas.')}><Icon name="user" size={18}/>Reasignar</button>
        <button onClick={() => choice('Rectificar materia del Caso', 'CASE_MATTER', [{...matter, value: text(r, 'matter')}, reason])}><Icon name="edit" size={18}/>Materia</button>
        {state === 'EN_GESTION' && <>
          <button onClick={() => requestFlow(ctx, 'CAS', r, 'DERIVACION')}><Icon name="send" size={18}/>Derivar Caso</button>
          <button onClick={() => choice('Confirmar espera de antecedentes', 'CASE_WAIT', [reason], 'El Caso sigue abierto. La espera no suspende automáticamente los plazos.')}><Icon name="clock" size={18}/>Poner en espera</button>
        </>}
        {state === 'EN_ESPERA' && <button onClick={() => choice('Continuar gestión', 'CASE_RESUME', [reason])}><Icon name="refresh" size={18}/>Salir de espera</button>}
        {state === 'EN_REVISION' && <button onClick={() => choice('Devolver término con observaciones', 'CASE_RETURN', [reason])}><Icon name="arrowLeft" size={18}/>Devolver con observaciones</button>}
      </ActionBar>}
      {closed && <Panel title="Cierre conservado en el historial" icon="lock">
        <p className="muted">{humanize(r.closure_mode)} · {when(r.closed_ms)} · {nameOf(ctx.catalog, r.closed_by)}</p>
        <p className="preserve">{text(r, 'close_reason')}</p>
      </Panel>}
      <Notes row={r} ctx={ctx} editable={!closed && write}/>
      <Panel title="Agenda del Caso" icon="tasks" count={tasks.length}
        description="Recordatorios para quien debe realizar cada gestión. Una tarea no es una actuación, y ninguna tarea pendiente permite cerrar el Caso.">
        {pendingTasks > 0 && <p className="pending-tasks"><Icon name="clock" size={16}/>{pendingTasks === 1 ? '1 tarea pendiente' : `${pendingTasks} tareas pendientes`}</p>}
        {tasks.length === 0 ? <EmptyState compact icon="tasks" title="Sin tareas registradas">{!closed && write ? 'Usa «Nueva tarea» para agendar un recordatorio del equipo.' : undefined}</EmptyState>
          : <ul className="stack-list">{tasks.map(t => <Task key={number(t, 'id')} t={t} r={r} ctx={ctx}/>)}</ul>}
      </Panel>
      <Files row={r} kind="CAS" ctx={ctx} editable={!closed && write}/>
    </>}
    side={<>
      <Panel title="Datos del Caso" icon="briefcase">
        <Facts stacked items={[
          ['Requerimiento', <button className="link folio" onClick={() => ctx.go('REQ', number(r, 'requirement_id'))}>{text(r, 'req_folio')}</button>],
          ['Ámbito', scopeOf(ctx.catalog, r.scope_id)],
          ['Responsable', nameOf(ctx.catalog, r.responsible_id)],
          ['Complejidad del REQ', r.req_complexity ? <Status value={r.req_complexity}/> : ''],
          ['Materia', text(r, 'matter')],
        ]}/>
      </Panel>
      {!closed && (head || owner) && <ClosureStatus row={r} action={owner && !mayConfirmClose && state !== 'EN_REVISION' ? 'request' : 'close'}/>}
      <Panel title="Equipo" icon="people">
        <ul className="team">
          <li><span className="avatar sm" aria-hidden="true">{initials(nameOf(ctx.catalog, r.responsible_id))}</span><span><strong>{nameOf(ctx.catalog, r.responsible_id)}</strong><small>Responsable principal</small></span></li>
          {list(r, 'members').map(m => <li key={number(m, 'user_id')}>
            <span className="avatar sm" aria-hidden="true">{initials(text(m, 'name'))}</span>
            <span><strong>{text(m, 'name')}</strong><small>Integrante</small></span>
            {(head || owner) && !closed && <button className="ghost sm" onClick={() => choice('Retirar integrante', 'CASE_MEMBER_REMOVE', [fixedSelect('user_id', 'Integrante', number(m, 'user_id'), text(m, 'name')), reason])}>Retirar</button>}
          </li>)}
        </ul>
        {list(r, 'assignments').length > 0 && <details className="assignments">
          <summary>Historial de asignaciones ({list(r, 'assignments').length})</summary>
          <ol className="timeline compact">{list(r, 'assignments').map((x, i) => <li key={i}>
            <div className="timeline-head"><strong>{text(x, 'name')}</strong>{bool(x, 'current_flag') && <span className="chip">Vigente</span>}</div>
            <p className="timeline-meta">Asignación: {when(x.assigned_ms)} · Acuse: {x.acknowledged_ms ? when(x.acknowledged_ms) : 'pendiente'}</p>
          </li>)}</ol>
        </details>}
      </Panel>
      {list(r, 'flows').length > 0 && <Panel title="Derivaciones" icon="send">
        <ul className="link-list">{list(r, 'flows').map(f => <li key={number(f, 'id')} className="link-row static">
          <Kind kind="FLUJO"/><span>Derivación #{number(f, 'id')}</span><Status value={f.state}/>
          {Boolean(f.successor_case_id) && <button className="sm" onClick={() => ctx.go('CAS', number(f, 'successor_case_id'))}>Sucesor (requiere permiso)</button>}
        </li>)}</ul>
      </Panel>}
    </>}/>;
}

/** Tarea de agenda: el ejecutor la completa; el equipo puede registrar intentos, cancelar o reprogramar. */
function Task({t, r, ctx}: {t: Row; r: Row; ctx: Context}) {
  const write = bool(r, 'can_write'), closed = text(r, 'state') === 'CERRADO';
  const hours = number(t, 'reminder_interval_hours');
  const overdue = OPEN_TASK.includes(text(t, 'state')) && Number(t.due_ms) < Date.now();
  return <li className={'card task' + (overdue ? ' overdue' : '')}>
    <div className="row-between"><strong>{text(t, 'title')}</strong><Status value={t.state}/></div>
    <p className="timeline-meta">
      <Icon name="user" size={14}/> {nameOf(ctx.catalog, t.executor_id)} · <Icon name="calendar" size={14}/> {when(t.due_ms)}{overdue && <strong className="overdue-label"> · Vencida</strong>} · Próximo aviso: {when(t.next_reminder_ms)} · {hours === 0 ? 'Aviso único' : `Aviso cada ${hours} h`}
    </p>
    {write && OPEN_TASK.includes(text(t, 'state')) && <div className="actions">
      {number(t, 'executor_id') === ctx.user.id && <button className="sm primary" onClick={() => choose(ctx, 'Confirmar cumplimiento manual', 'TASK_COMPLETE', t, [reason], 'No genera una actuación.')}><Icon name="check" size={16}/>Completar</button>}
      <button className="sm" onClick={() => choose(ctx, 'Registrar intento sin dar por cumplida', 'TASK_ATTEMPT', t, [reason])}>Registrar intento</button>
      <button className="sm" onClick={() => choose(ctx, 'Cancelar tarea con fundamento', 'TASK_CANCEL', t, [reason])}>Cancelar</button>
      {!closed && <button className="sm" onClick={() => choose(ctx, 'Reprogramar o reasignar tarea', 'TASK_EDIT', t, taskEditFields(ctx, r, t))}>Editar agenda</button>}
    </div>}
  </li>;
}
