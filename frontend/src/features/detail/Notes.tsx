import type {ReactNode} from 'react';
import type {Row} from '../../lib/rows';
import {bool, list, localTime, number, text, when} from '../../lib/rows';
import {choose, reason} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import {EmptyState, Panel} from '../../components/Primitives';

/** Actuaciones del REQ o del Caso. Rectificar crea una nueva constancia; el original se conserva. */
export function Notes({row, ctx, editable, action}: {row: Row; ctx: Context; editable: boolean; action?: ReactNode}) {
  const rectify = (n: Row) => choose(ctx, 'Rectificar sin sobrescribir el original', 'NOTE_RECTIFY', n, [
    {key: 'body', label: 'Nueva constancia', type: 'textarea', required: true, max: 20000},
    {key: 'effective_at', label: 'Fecha efectiva', type: 'datetime-local', required: true, value: localTime()},
    {key: 'reserved', label: 'Reservada (no desclasifica una anterior)', type: 'checkbox', value: bool(n, 'reserved')},
    reason,
  ]);
  const notes = list(row, 'notes');
  return <Panel title="Actuaciones" icon="file" count={notes.length} description="Gestiones efectivamente realizadas. Rectificar agrega una constancia nueva; la original se conserva." actions={action}>
    {notes.length === 0 ? <EmptyState compact icon="file" title="Sin actuaciones visibles">Las actuaciones registradas aparecerán aquí en orden.</EmptyState> : <ol className="timeline">
      {notes.map(n => <li key={number(n, 'id')} className={bool(n, 'reserved') ? 'reserved' : undefined}>
        <div className="timeline-head">
          <strong>{text(n, 'author')}</strong>
          <span className="timeline-meta">#{number(n, 'id')} · Gestión: {when(n.effective_ms)} · Registro: {when(n.registered_ms)}{n.rectifies_id ? ` · Rectifica #${n.rectifies_id}` : ''}</span>
          {bool(n, 'reserved') && <Status value="RESERVADA"/>}
        </div>
        <p className="timeline-body preserve">{text(n, 'body')}</p>
        {n.correction_reason ? <p className="timeline-meta">Fundamento de la rectificación: {text(n, 'correction_reason')}</p> : null}
        {editable && (number(n, 'author_id') === ctx.user.id || bool(row, 'can_head')) && <div className="timeline-actions">
          <button className="sm" onClick={() => rectify(n)}><Icon name="edit" size={16}/>Rectificar</button>
        </div>}
      </li>)}
    </ol>}
  </Panel>;
}
