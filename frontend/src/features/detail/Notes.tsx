import type {ReactNode} from 'react';
import type {Row} from '../../lib/rows';
import {bool, list, localTime, number, text, when} from '../../lib/rows';
import {choose, reason} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import {EmptyState, Panel} from '../../components/Primitives';
import {rightsAnchor} from '../../components/RightsPanel';
import {reducedMotion} from '../../components/useExit';
import {regionName} from '../../lib/person/catalog.ts';
import {codeLabel, statCodes} from '../../lib/person/personModel.ts';
import {actionTypeField, institutionsField} from './ActionFields';
import {rectifyInitial} from './notesModel.ts';

/**
 * Actuaciones del REQ o del Caso. Rectificar crea una nueva constancia; el original se conserva. Desde 0.8.0 (D36, E09)
 * cada constancia muestra su tipo y sus instituciones, y la rectificacion parte del tipo y las instituciones vigentes
 * (confirmar sin tocarlos los conserva). rightsKind: acceso al panel de derechos del mismo registro.
 */
export function Notes({row, ctx, editable, action, rightsKind}: {row: Row; ctx: Context; editable: boolean; action?: ReactNode; rightsKind?: string}) {
  const rectify = (n: Row) => {
    const initial = rectifyInitial(n);
    choose(ctx, 'Rectificar sin sobrescribir el original', 'NOTE_RECTIFY', n, [
      {key: 'body', label: 'Nueva constancia', type: 'textarea', required: true, max: 20000},
      {key: 'effective_at', label: 'Fecha efectiva', type: 'datetime-local', required: true, value: localTime()},
      {key: 'reserved', label: 'Reservada (no desclasifica una anterior)', type: 'checkbox', value: bool(n, 'reserved')},
      ...actionTypeField(ctx.catalog, 'Tipo de actuación (opcional)', initial.action_type, text(n, 'action_label')),
      institutionsField(ctx.catalog, initial.institutions),
      reason,
    ]);
  };
  const toRights = () => document.getElementById(rightsAnchor(rightsKind ?? ''))?.scrollIntoView({behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start'});
  const notes = list(row, 'notes');
  return <Panel title="Actuaciones" icon="file" count={notes.length} description="Gestiones efectivamente realizadas. Rectificar agrega una constancia nueva; la original se conserva."
    actions={<>{action}{rightsKind && <button className="sm" onClick={toRights}><Icon name="shield" size={16}/>Derechos y contexto</button>}</>}>
    {notes.length === 0 ? <EmptyState compact icon="file" title="Sin actuaciones visibles">Las actuaciones registradas aparecerán aquí en orden.</EmptyState> : <ol className="timeline">
      {notes.map(n => <li key={number(n, 'id')} className={bool(n, 'reserved') ? 'reserved' : undefined}>
        <div className="timeline-head">
          <strong>{text(n, 'author')}</strong>
          <span className="timeline-meta">#{number(n, 'id')} · Gestión: {when(n.effective_ms)} · Registro: {when(n.registered_ms)}{n.rectifies_id ? ` · Rectifica #${n.rectifies_id}` : ''}</span>
          {bool(n, 'reserved') && <Status value="RESERVADA"/>}
        </div>
        {(text(n, 'action_label') || n.action_type) ? <p className="timeline-meta"><span className="chip">{text(n, 'action_label') || 'Tipo de un listado anterior'}</span></p> : null}
        <p className="timeline-body preserve">{text(n, 'body')}</p>
        {list(n, 'institutions').length > 0 && <p className="timeline-meta">Instituciones: {list(n, 'institutions').map(i => [text(i, 'name'), codeLabel(statCodes(ctx.catalog).institution, text(i, 'type_code')), text(i, 'role'), i.region_code ? regionName(text(i, 'region_code')) : ''].filter(Boolean).join(', ')).join(' · ')}</p>}
        {n.correction_reason ? <p className="timeline-meta">Fundamento de la rectificación: {text(n, 'correction_reason')}</p> : null}
        {editable && (number(n, 'author_id') === ctx.user.id || bool(row, 'can_head')) && <div className="timeline-actions">
          <button className="sm" onClick={() => rectify(n)}><Icon name="edit" size={16}/>Rectificar</button>
        </div>}
      </li>)}
    </ol>}
  </Panel>;
}
