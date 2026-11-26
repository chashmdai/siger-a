import {useMemo, useState} from 'react';
import type {FormEvent} from 'react';
import type {Row} from '../../lib/rows';
import {bool, ids, list, localTime, number, text} from '../../lib/rows';
import {nameOf, scopeOf, scopes} from '../../lib/catalog';
import {fixedSelect, IMMEDIATE_RESULTS} from '../../lib/fields';
import {labeled, RESULT_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {LabeledField} from '../../components/LabeledField';
import {ChipRadios} from '../../components/Choice';
import {Icon} from '../../components/Icon';
import {Callout} from '../../components/Primitives';
import {DestinationPicker} from '../../components/DestinationPicker';
import {destinations, initialChoice, payloadFor, receiver} from '../../lib/destination';
import {obj} from '../../lib/rows';
import {characterizationOf, statField} from '../detail/Characterization';
import {closeState, completeness, fichaVars} from '../detail/characterizationModel.ts';
import {blockersOf} from '../detail/ClosureStatus';
import {rightsPending} from '../../lib/rightsModel.ts';

const LOCKED_NOTICE: Record<string, string> = {
  CON_CASO: 'La ficha inicial queda conservada. Las nuevas gestiones se registran en los Casos.',
  RESUELTA: 'Atención resuelta. La nueva información se registra por el circuito de antecedentes, sin sobrescribir esta ficha.',
};

/**
 * Continuidad sin Caso: entregar a un profesional habilitado o a la funcion receptora,
 * o resolver durante el primer contacto. Ninguna de las dos crea SOL ni Caso.
 */
export function AttentionActions({ctx, row, info}: {ctx: Context; row: Row; info: Row}) {
  const [recipient, setRecipient] = useState('PROFESIONAL'), [scope, setScope] = useState(''), [user, setUser] = useState(''), [reason, setReason] = useState('');
  const candidates = list(ctx.catalog, 'people').filter(p => ids(p, 'handoff_scopes').includes(Number(scope)));
  // A jefatura: selector de destino con lo que el catalogo publica para ENTREGA y la sede de la region de la ficha propuesta.
  const heads = useMemo(() => destinations(ctx.catalog, 'ENTREGA'), [ctx.catalog]);
  const region = text(info, 'suggested_region');
  const [choice, setChoice] = useState(() => initialChoice(heads, region));
  const professionals = scopes(ctx.catalog);

  function send(e: FormEvent) {
    e.preventDefault();
    const target = recipient === 'PROFESIONAL' ? scope : String(payloadFor('ENTREGA', choice).scope_id ?? '');
    const fields: Field[] = [
      fixedSelect('recipient_kind', 'Destinatario', recipient, recipient === 'PROFESIONAL' ? 'Profesional' : 'Jefatura · ' + receiver(heads, choice)),
      fixedSelect('scope_id', 'Ámbito', target, scopeOf(ctx.catalog, target)),
      ...(recipient === 'PROFESIONAL' ? [fixedSelect('user_id', 'Profesional', user, nameOf(ctx.catalog, user))] : []),
      {key: 'reason', label: 'Motivo y antecedentes para la continuidad', type: 'textarea', value: reason, required: true, max: 2000},
    ];
    ctx.act({title: 'Entregar primera atención', type: 'ATTENTION_SEND', id: number(row, 'id'), revision: number(row, 'revision'), fields, note: 'No crea una SOL ni un Caso. La persona a cargo se mantiene hasta que el destino recibe. No transfiere el principal del REQ.'});
  }
  // Resolucion (D28, E09): bloqueos del servidor (resolve_blockers) y caracterizacion pendiente que el dialogo completa en
  // el mismo acto; derechos y contexto de una vulneracion se clasifican en el panel del REQ.
  const statVars = fichaVars(characterizationOf(row)), need = completeness(ctx.catalog);
  const resolving = closeState(blockersOf(info, 'resolve_blockers'), need.intake, statVars);
  const rightsLeft = rightsPending(need.vulnerability, obj(row, 'rights'));
  function resolve() {
    ctx.act({title: 'Resolver primera atención sin Caso', type: 'ATTENTION_RESOLVE', id: number(row, 'id'), revision: number(row, 'revision'), fields: [
      {key: 'result', label: 'Resultado', type: 'select', required: true, options: labeled(IMMEDIATE_RESULTS, RESULT_LABELS)},
      {key: 'effective_at', label: 'Fecha de la gestión realizada', type: 'datetime-local', required: true, value: localTime()},
      {key: 'reason', label: 'Qué se resolvió efectivamente', type: 'textarea', required: true, max: 12000},
      ...(resolving.resolvable.length ? [statField(ctx.catalog, statVars, {required: () => need.intake, lead: 'Para resolver, completa la caracterización pendiente (puede ser «Sin información»).'})] : []),
    ], note: 'Cierra el REQ mediante la facultad de primera atención. No crea Caso, no envía correo y no cancela compromisos automáticamente.'});
  }
  const blockers = blockersOf(info, 'resolve_blockers');

  if (!bool(info, 'can_edit') && text(info, 'state') === 'PENDIENTE_RECEPCION') return null;
  if (!bool(info, 'can_edit')) return <Callout tone="neutral">{LOCKED_NOTICE[text(info, 'state')] ?? 'La entrega debe recibirse, devolverse o retirarse antes de seguir editando.'}</Callout>;
  const followup = bool(info, 'followup_required');
  return <div className="continuity-grid">
    <section className="panel">
      <div className="panel-head"><div><h2><Icon name="handoff"/>Entregar la atención</h2><p>Puede continuar un profesional habilitado sin esperar la asignación de un Caso. La jefatura principal se conserva.</p></div></div>
      <form onSubmit={send}>
        <ChipRadios legend="Enviar a" value={recipient} onChange={v => {setRecipient(v); setScope(''); setUser(''); setChoice(initialChoice(heads, region));}} options={[{value: 'PROFESIONAL', label: 'Profesional disponible'}, {value: 'JEFATURA', label: 'Dirección o coordinación competente'}]}/>
        {recipient === 'PROFESIONAL' ? <div className="form-grid">
          <LabeledField label="Ámbito de destino" required><select required value={scope} onChange={e => {setScope(e.target.value); setUser('');}}>
            <option value="">Seleccionar</option>
            {professionals.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select></LabeledField>
          <LabeledField label="Profesional habilitado en el destino" required><select required value={user} onChange={e => setUser(e.target.value)}>
            <option value="">{scope ? 'Seleccionar' : 'Elige primero el ámbito'}</option>
            {candidates.map(p => <option key={number(p, 'id')} value={number(p, 'id')}>{text(p, 'name')}</option>)}
          </select></LabeledField>
        </div> : <DestinationPicker destinations={heads} region={region} value={choice} onChange={setChoice}/>}
        <LabeledField label="Motivo de la entrega" required><textarea required maxLength={2000} value={reason} onChange={e => setReason(e.target.value)}/></LabeledField>
        <button className="primary"><Icon name="send" size={18}/>Preparar entrega</button>
      </form>
    </section>
    <section className="panel">
      <div className="panel-head"><div><h2><Icon name="checkCircle"/>Resolver en el primer contacto</h2><p>Solo si la orientación, respuesta o remisión quedaron efectivamente concluidas, sin seguimiento pendiente.</p></div></div>
      {followup && <Callout tone="warn">La ficha indica seguimiento: requiere abrir un Caso o rectificar, con fundamento, el curso de acción.</Callout>}
      {blockers.length > 0 && <Callout tone={resolving.enabled ? 'info' : 'warn'} title={resolving.enabled ? 'Se completa al resolver' : 'Antes de resolver'}>
        <ul>{blockers.map(b => <li key={b}>{b}</li>)}</ul>
        {rightsLeft.length > 0 && !resolving.enabled && <p>Clasifica derechos y contexto en el panel «Derechos y contexto» de este REQ.</p>}
      </Callout>}
      <button disabled={followup || !resolving.enabled} onClick={resolve}><Icon name="check" size={18}/>Resolver atención inmediata</button>
    </section>
  </div>;
}
