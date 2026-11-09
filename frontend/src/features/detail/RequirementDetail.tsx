import type {Row} from '../../lib/rows';
import {bool, list, number, obj, text, when} from '../../lib/rows';
import {headAt} from '../../lib/access';
import {caseScopeOptions, casePersonOptions, nameOf, scopeOf} from '../../lib/catalog';
import {choose, decision, matter, objective, reason} from '../../lib/fields';
import {COMPLEXITY_LABELS, FLOW_LABELS, humanize, labeled} from '../../lib/labels';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {Kind, Status} from '../../components/Status';
import {Table} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {EmptyState, Facts, Panel} from '../../components/Primitives';
import Attention from '../attention/Attention';
import {ClosureStatus} from './ClosureStatus';
import {Files} from './Files';
import {Notes} from './Notes';
import {noteFields, requestFlow, requestReopening} from './actions';
import {ActionBar, DetailGrid} from './parts';

/**
 * Requerimiento: puede contener varios Casos. La jefatura principal clasifica, abre Casos,
 * pide colaboracion o transferencia y cierra expresamente; la primera atencion se muestra arriba.
 */
export function RequirementDetail({r, ctx}: {r: Row; ctx: Context}) {
  const head = bool(r, 'can_head'), closed = text(r, 'state') === 'CERRADO';
  const hasAttention = Object.keys(obj(r, 'attention')).length > 0;
  const caseScopes = (r.case_scopes as number[]) ?? [];
  const choice = (title: string, type: string, fields: Field[], note?: string) => choose(ctx, title, type, r, fields, note);
  const classify = () => choice('Clasificar o reclasificar REQ', 'REQ_CLASSIFY', [{key: 'complexity', label: 'Complejidad del Requerimiento', type: 'select', required: true, value: text(r, 'complexity'), options: labeled(['BAJA', 'MEDIANA', 'ALTA'], COMPLEXITY_LABELS)}, reason]);
  const openCase = () => choice('Abrir Caso para seguimiento', 'REQ_CASE_CREATE', [
    objective,
    {key: 'scope_id', label: 'Área en la que se gestionará el Caso', type: 'select', required: true, options: caseScopeOptions(ctx.catalog, caseScopes)},
    {key: 'responsible_id', label: 'Profesional del área elegida', type: 'select', required: true, dependsOn: 'scope_id', options: casePersonOptions(ctx.catalog, caseScopes)},
    matter,
  ], 'Solo la jefatura crea y asigna. Puede seleccionarse a sí misma si tiene facultad en este ámbito.');
  const close = () => choice('Cerrar Requerimiento expresamente', 'REQ_CLOSE', [reason], 'Requiere cero Casos abiertos y resolver las peticiones de continuidad. No se cierra al cerrar el último Caso.');
  const decideLink = (l: Row) => choose(ctx, 'Evaluar pertinencia y destino del antecedente', 'LINK_DECIDE', l, [
    decision([['ADMITIDA', 'Admitir'], ['RECHAZADA', 'No admitir']]),
    {key: 'case_id', label: 'Caso pertinente (opcional; no abre un Caso cerrado)', type: 'select', options: list(r, 'case_rows').map(c => ({value: number(c, 'id'), label: text(c, 'folio')}))},
    reason,
  ], 'La decisión queda auditada. El vínculo no reabre el REQ ni reemplaza una actuación profesional.');
  const returnLink = (l: Row) => choose(ctx, 'Devolver COM rechazada a clasificación', 'LINK_RETURN', l, [reason], 'Conserva la COM, su folio y este rechazo. No abre otro REQ ni elimina el antecedente.');
  const continuity = list(r, 'flows').length + list(r, 'reopenings').length;

  return <>
    {hasAttention && <Attention key={String(r.id) + ':' + String(r.revision)} ctx={ctx} row={r}/>}
    <DetailGrid
      main={<>
        <ActionBar label={hasAttention ? 'Gestión del REQ' : 'Acciones'}>
          {!closed && head && <>
            <button className="primary" onClick={openCase}><Icon name="briefcase" size={18}/>Abrir Caso</button>
            <button onClick={classify}><Icon name="layers" size={18}/>Complejidad</button>
            <button onClick={() => requestFlow(ctx, 'REQ', r, 'COLABORACION')}><Icon name="people" size={18}/>Pedir colaboración</button>
            <button onClick={() => requestFlow(ctx, 'REQ', r, 'TRANSFERENCIA')}><Icon name="swap" size={18}/>Transferir principal</button>
            <button disabled={!bool(r, 'can_close')} title={bool(r, 'can_close') ? undefined : 'Revisa las condiciones de cierre'} onClick={close}><Icon name="lock" size={18}/>Cerrar REQ</button>
          </>}
          {!closed && bool(r, 'can_attention') && number(r, 'total_cases') === 0 && !hasAttention && <button onClick={() => choice('Registrar atención sin seguimiento', 'REQ_NOTE', noteFields(true))}><Icon name="file" size={18}/>Registrar atención sin Caso</button>}
          {closed && head && <button onClick={() => requestReopening(ctx, 'REQ', r)}><Icon name="refresh" size={18}/>Evaluar nueva gestión</button>}
        </ActionBar>
        <Panel flush title="Casos del Requerimiento" icon="briefcase" count={list(r, 'case_rows').length}>
          {list(r, 'case_rows').length === 0 ? <EmptyState compact icon="briefcase" title="Este REQ aún no tiene Casos">{head && !closed ? 'Si requiere seguimiento, abre un Caso y asigna a un profesional del área.' : 'La jefatura principal abre un Caso cuando se requiere seguimiento.'}</EmptyState>
            : <div className="panel-table"><Table rows={list(r, 'case_rows')} open={c => {if (bool(c, 'readable')) ctx.go('CAS', number(c, 'id'));}} columns={[
              {name: 'Caso', render: c => bool(c, 'readable') ? <span className="ref-cell"><Kind kind="CAS"/><button className="link folio" onClick={() => ctx.go('CAS', number(c, 'id'))}>{text(c, 'folio')}</button></span> : <span className="folio">{text(c, 'folio')}</span>},
              {name: 'Estado', render: c => <Status value={c.state}/>},
              {name: 'Ámbito', render: c => scopeOf(ctx.catalog, c.scope_id)},
              {name: 'Objetivo autorizado', render: c => text(c, 'objective') || <span className="muted">Solo estado de coordinación</span>},
            ]}/></div>}
        </Panel>
        <Notes row={r} ctx={ctx} editable={!closed && bool(r, 'can_attention')}/>
        <Panel title="Comunicaciones posteriores" icon="mail" count={list(r, 'links').length} description="Antecedentes que llegaron después y se propusieron para este REQ.">
          {list(r, 'links').length === 0 ? <EmptyState compact icon="mail" title="Sin antecedentes vinculados visibles"/> : <ul className="stack-list">
            {list(r, 'links').map(l => <li key={number(l, 'id')} className="card">
              <div className="row-between"><span className="ref-cell"><Kind kind="COM"/><strong className="folio">{text(l, 'com_folio')}</strong></span><Status value={l.state}/></div>
              <p className="preserve">{text(l, 'reason')}</p>
              {l.returned_ms ? <p className="muted">Devuelta a clasificación el {when(l.returned_ms)}. El rechazo se conserva en el historial.</p> : null}
              <div className="actions">
                {!l.returned_ms && <button className="sm" onClick={() => ctx.go('COM', number(l, 'communication_id'))}>Ver COM autorizada</button>}
                {head && !l.returned_ms && <button className="sm" onClick={() => decideLink(l)}>Decidir o rectificar</button>}
                {head && !l.returned_ms && text(l, 'state') === 'RECHAZADA' && <button className="sm" onClick={() => returnLink(l)}>Devolver a clasificación</button>}
              </div>
            </li>)}
          </ul>}
        </Panel>
        {continuity > 0 && <Panel title="Continuidad" icon="flow" description="Peticiones entre unidades y solicitudes de nueva gestión.">
          <ul className="link-list">
            {list(r, 'flows').map(f => <li key={number(f, 'id')}><button className="link-row" onClick={() => ctx.go('FLUJO', number(f, 'id'))}><Kind kind="FLUJO"/><span>{humanize(f.kind, FLOW_LABELS)} #{number(f, 'id')}</span><Status value={f.state}/><Icon name="right" size={18}/></button></li>)}
          </ul>
          {list(r, 'reopenings').map(p => <Reopening key={number(p, 'id')} p={p} r={r} ctx={ctx}/>)}
        </Panel>}
        <Files row={r} kind="REQ" ctx={ctx} editable={!closed && bool(r, 'can_attention') && number(r, 'total_cases') === 0}/>
      </>}
      side={<>
        <Panel title="Datos del REQ" icon="folder">
          <Facts stacked items={[
            ['Jefatura principal', scopeOf(ctx.catalog, r.principal_scope)],
            ['Área sugerida', r.requested_area_scope ? scopeOf(ctx.catalog, r.requested_area_scope) : 'Sin área específica'],
            ['Complejidad', r.complexity ? <Status value={r.complexity}/> : <span className="muted">Sin clasificar (no equivale a baja)</span>],
            ['Casos abiertos', `${text(r, 'open_cases') || 0} de ${text(r, 'total_cases') || 0}`],
            ['Atención sin Caso', r.attention_user ? nameOf(ctx.catalog, r.attention_user) : ''],
          ]}/>
          {bool(r, 'can_read_com') && <button className="block" onClick={() => ctx.go('COM', number(r, 'communication_id'))}><Icon name="mail" size={18}/>Consultar COM de origen</button>}
        </Panel>
        {!closed && head && <ClosureStatus row={r}/>}
      </>}/>
  </>;
}

/** Peticion de nueva gestion: la jefatura del Caso decide; un REQ cerrado requiere autorizar su reactivacion. */
function Reopening({p, r, ctx}: {p: Row; r: Row; ctx: Context}) {
  const head = bool(r, 'can_head'), closed = text(r, 'state') === 'CERRADO';
  const caseRow = list(r, 'case_rows').find(c => number(c, 'id') === number(p, 'case_id'));
  const canDecide = caseRow ? headAt(ctx.user, number(caseRow, 'scope_id')) : head;
  return <div className="card">
    <div className="row-between"><strong>Solicitud de nueva gestión #{number(p, 'id')}</strong><Status value={p.state}/></div>
    <p className="preserve">{text(p, 'reason')}</p>
    {text(p, 'state') === 'PENDIENTE' && <div className="actions">
      {head && closed && <button className="sm" onClick={() => choose(ctx, 'Autorizar reactivación del REQ', 'REOPEN_AUTHORIZE', p, [reason], 'La autorización sola no reabre. Se aplica al aceptar la petición.')}>Autorizar reactivación del REQ</button>}
      {canDecide && <button className="sm primary" onClick={() => choose(ctx, 'Decidir nueva gestión', 'REOPEN_DECIDE', p, [decision([['ACEPTADA', 'Aceptar'], ['RECHAZADA', 'Rechazar']]), reason])}>Decidir</button>}
    </div>}
  </div>;
}
