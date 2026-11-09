import type {ComponentType} from 'react';
import type {Row} from '../../lib/rows';
import {list, text, when} from '../../lib/rows';
import {nameOf} from '../../lib/catalog';
import {ACTION_LABELS, humanize} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Table} from '../../components/Table';
import {EmptyState, Panel} from '../../components/Primitives';
import {ComDetail} from './ComDetail';
import {SolDetail} from './SolDetail';
import {RequirementDetail} from './RequirementDetail';
import {CaseDetail} from './CaseDetail';
import {FlowDetail} from './FlowDetail';
import {DetailHeader} from './parts';

const BODIES: Record<string, ComponentType<{r: Row; ctx: Context}>> = {COM: ComDetail, SOL: SolDetail, REQ: RequirementDetail, CAS: CaseDetail, FLUJO: FlowDetail};

/** Ficha autorizada: encabezado, cuerpo segun el tipo, cierres conservados e historia de decisiones. */
export default function Detail({kind, row: r, ctx}: {kind: string; row: Row; ctx: Context}) {
  const Body = BODIES[kind];
  const history = list(r, 'history');
  return <article className="detail fade-in">
    <DetailHeader kind={kind} r={r} ctx={ctx}/>
    {Body && <Body r={r} ctx={ctx}/>}
    {list(r, 'closures').length > 0 && <Panel title="Cierres anteriores" icon="history" description="Se conservan aunque el registro se haya reabierto.">
      <Table rows={list(r, 'closures')} columns={[
        {name: 'Fecha', render: c => when(c.closed_ms)},
        {name: 'Modalidad', render: c => humanize(c.mode)},
        {name: 'Autor', render: c => nameOf(ctx.catalog, c.actor_id)},
        {name: 'Fundamento', render: c => <span className="preserve">{text(c, 'reason')}</span>},
      ]}/>
    </Panel>}
    <Panel title="Historia de decisiones" icon="history" count={history.length} description="Quién decidió qué y cuándo. La historia no se edita.">
      {history.length === 0 ? <EmptyState compact icon="history" title="Sin decisiones visibles">La historia se muestra según tus permisos sobre este registro.</EmptyState>
        : <ol className="timeline">{history.map((e, i) => <li key={i}>
          <div className="timeline-head"><strong>{humanize(e.action, ACTION_LABELS)}</strong><span className="timeline-meta">{text(e, 'actor')} · {when(e.at_ms)}</span></div>
          {text(e, 'note') && <p className="timeline-body preserve">{text(e, 'note')}</p>}
        </li>)}</ol>}
    </Panel>
  </article>;
}
