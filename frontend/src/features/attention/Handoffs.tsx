import type {Row} from '../../lib/rows';
import {bool, list, number, text, when} from '../../lib/rows';
import {nameOf, scopeOf} from '../../lib/catalog';
import {fixedSelect} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import {EmptyState, Panel} from '../../components/Primitives';

const DECISIONS = ['RECIBIDA', 'DEVUELTA', 'RETIRADA'];
const TITLES: Record<string, string> = {RECIBIDA: 'Recibir y continuar la atención', DEVUELTA: 'Devolver la atención con fundamento', RETIRADA: 'Retirar la entrega pendiente'};
const BUTTONS: Record<string, string> = {RECIBIDA: 'Recibir y continuar', DEVUELTA: 'Devolver', RETIRADA: 'Retirar entrega'};
const OPTION_LABELS: Record<string, string> = {RECIBIDA: 'Recibir', DEVUELTA: 'Devolver', RETIRADA: 'Retirar'};

/** Entregas de la atencion. El destinatario recibe o devuelve; quien esta a cargo puede retirarla. */
export function Handoffs({ctx, row, info}: {ctx: Context; row: Row; info: Row}) {
  const decide = (h: Row, d: string) => ctx.act({title: TITLES[d], type: 'ATTENTION_DECIDE', id: number(row, 'id'), revision: number(row, 'revision'), fields: [
    fixedSelect('handoff_id', 'Entrega', number(h, 'id'), String(h.id)),
    fixedSelect('decision', 'Decisión', d, OPTION_LABELS[d]),
    {key: 'reason', label: 'Fundamento o constancia', type: 'textarea', required: true, max: 2000},
  ]});
  const handoffs = list(info, 'handoffs');
  return <Panel title="Entregas de la atención" icon="handoff" count={handoffs.length}>
    {handoffs.length === 0 ? <EmptyState compact icon="handoff" title="Sin entregas">Quien inicia la atención permanece a cargo hasta entregarla.</EmptyState> : <ol className="timeline">
      {handoffs.map(h => <li key={number(h, 'id')} className="handoff">
        <div className="timeline-head">
          <strong>{scopeOf(ctx.catalog, h.target_scope)}{h.target_user ? ' · ' + nameOf(ctx.catalog, h.target_user) : ''}</strong>
          <Status value={h.state}/>
        </div>
        <p className="timeline-meta">{when(h.sent_ms)}{h.sent_by ? ' · Enviada por ' + nameOf(ctx.catalog, h.sent_by) : ''}</p>
        {h.reason ? <p className="timeline-body preserve">{text(h, 'reason')}</p> : null}
        {h.decision_reason ? <p className="timeline-body preserve"><strong>Decisión:</strong> {text(h, 'decision_reason')}</p> : null}
        <div className="timeline-actions">
          {DECISIONS.filter(d => d === 'RETIRADA' ? bool(h, 'can_withdraw') : bool(h, 'can_receive')).map(d => <button key={d} className={'sm' + (d === 'RECIBIDA' ? ' primary' : '')} onClick={() => decide(h, d)}>
            {d === 'RECIBIDA' && <Icon name="check" size={16}/>}{BUTTONS[d]}
          </button>)}
        </div>
      </li>)}
    </ol>}
  </Panel>;
}
