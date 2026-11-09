import type {Row} from '../../lib/rows';
import {bool, number, text, when} from '../../lib/rows';
import {caseScopeOptions, casePersonOptions, scopeOf} from '../../lib/catalog';
import {choose, fixedSelect, matter, reason} from '../../lib/fields';
import {FLOW_LABELS, humanize} from '../../lib/labels';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {Icon} from '../../components/Icon';
import {Callout, Facts, Panel} from '../../components/Primitives';
import {ActionBar, DetailGrid} from './parts';

/** Peticion entre unidades. El destino acepta o rechaza; quien la solicito puede retirarla. */
export function FlowDetail({r, ctx}: {r: Row; ctx: Context}) {
  const kind = text(r, 'kind'), caseScopes = (r.case_scopes as number[]) ?? [];
  const decide = (title: string, value: string, label: string, extra: Field[] = [], note?: string) => () => choose(ctx, title, 'FLOW_DECIDE', r, [fixedSelect('decision', 'Decisión', value, label), ...extra, reason], note);
  // La transferencia solo cambia el principal; colaboracion y derivacion crean un Caso en el destino.
  const caseFields: Field[] = kind === 'TRANSFERENCIA' ? [] : [
    {key: 'case_scope_id', label: 'Área que gestionará el Caso', type: 'select', required: true, options: caseScopeOptions(ctx.catalog, caseScopes)},
    {key: 'responsible_id', label: 'Profesional del área seleccionada', type: 'select', required: true, dependsOn: 'case_scope_id', options: casePersonOptions(ctx.catalog, caseScopes)},
    matter,
  ];
  const acceptNote = kind === 'DERIVACION' ? 'Cierra el origen por derivación y crea el sucesor en el mismo REQ. Todas las tareas pendientes deben resolverse antes; no se borran tareas.' : 'La operación conserva los originales y el historial.';
  const pending = text(r, 'state') === 'PENDIENTE';
  return <DetailGrid
    main={<>
      {pending && <ActionBar label="Decisión">
        {bool(r, 'can_accept') && <>
          <button className="primary" onClick={decide('Aceptar petición', 'ACEPTADA', 'Aceptar', caseFields, acceptNote)}><Icon name="check" size={18}/>Aceptar</button>
          <button onClick={decide('Rechazar petición', 'RECHAZADA', 'Rechazar')}><Icon name="close" size={18}/>Rechazar</button>
        </>}
        {bool(r, 'can_withdraw') && <button onClick={decide('Retirar solicitud pendiente', 'RETIRADA', 'Retirar')}><Icon name="arrowLeft" size={18}/>Retirar</button>}
      </ActionBar>}
      <Panel title="Paquete de evaluación" icon="file">
        <h3>Motivo</h3>
        <p className="preserve reading">{text(r, 'reason')}</p>
        {text(r, 'objective') && <><h3>Objetivo propuesto</h3><p className="preserve reading">{text(r, 'objective')}</p></>}
        <Callout tone="neutral">No concede acceso automático al expediente ni a los documentos del origen.</Callout>
      </Panel>
      {!pending && <Panel title="Decisión registrada" icon="checkCircle">
        <p className="muted">{when(r.decided_ms)}</p>
        <p className="preserve">{text(r, 'decision_reason')}</p>
      </Panel>}
    </>}
    side={<Panel title="Petición" icon="flow">
      <Facts stacked items={[
        ['Tipo', humanize(kind, FLOW_LABELS)],
        ['Origen', scopeOf(ctx.catalog, r.origin_scope)],
        ['Destino', scopeOf(ctx.catalog, r.target_scope)],
      ]}/>
      {Boolean(r.successor_case_id) && <button className="block" onClick={() => ctx.go('CAS', number(r, 'successor_case_id'))}><Icon name="briefcase" size={18}/>Abrir Caso de destino</button>}
    </Panel>}/>;
}
