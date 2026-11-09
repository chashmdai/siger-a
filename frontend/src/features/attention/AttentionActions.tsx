import {useState} from 'react';
import type {FormEvent} from 'react';
import type {Row} from '../../lib/rows';
import {bool, ids, list, localTime, number, text} from '../../lib/rows';
import {nameOf, scopeOf, scopeRow, scopes} from '../../lib/catalog';
import {fixedSelect, IMMEDIATE_RESULTS} from '../../lib/fields';
import {labeled, RESULT_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {LabeledField} from '../../components/LabeledField';
import {ChipRadios} from '../../components/Choice';
import {Icon} from '../../components/Icon';
import {Callout} from '../../components/Primitives';

const LOCKED_NOTICE: Record<string, string> = {
  CON_CASO: 'La ficha inicial queda conservada. Las nuevas gestiones se registran en los Casos.',
  RESUELTA: 'Atención resuelta. La nueva información se registra por el circuito de antecedentes, sin sobrescribir esta ficha.',
};
/** Funcion que recibe una entrega a jefatura en el ambito elegido. */
const receiverLabel = (target?: Row) => text(target ?? {}, 'code') === 'UPR-REQ' ? 'Coordinadora de Requerimientos' : target?.regional ? 'Coordinación regional' : 'Dirección de unidad';

/**
 * Continuidad sin Caso: entregar a un profesional habilitado o a la funcion receptora,
 * o resolver durante el primer contacto. Ninguna de las dos crea SOL ni Caso.
 */
export function AttentionActions({ctx, row, info}: {ctx: Context; row: Row; info: Row}) {
  const [recipient, setRecipient] = useState('PROFESIONAL'), [scope, setScope] = useState(''), [user, setUser] = useState(''), [reason, setReason] = useState('');
  const target = scopeRow(ctx.catalog, scope);
  const candidates = list(ctx.catalog, 'people').filter(p => ids(p, 'handoff_scopes').includes(Number(scope)));
  // A jefatura solo se entrega en sedes regionales, Direcciones de unidad o UPR-REQ.
  const destinations = scopes(ctx.catalog, s => recipient === 'PROFESIONAL' || Boolean(s.regional) || !text(s, 'area_code') || text(s, 'code') === 'UPR-REQ');

  function send(e: FormEvent) {
    e.preventDefault();
    const fields: Field[] = [
      fixedSelect('recipient_kind', 'Destinatario', recipient, recipient === 'PROFESIONAL' ? 'Profesional' : 'Jefatura'),
      fixedSelect('scope_id', 'Ámbito', scope, scopeOf(ctx.catalog, scope)),
      ...(recipient === 'PROFESIONAL' ? [fixedSelect('user_id', 'Profesional', user, nameOf(ctx.catalog, user))] : []),
      {key: 'reason', label: 'Motivo y antecedentes para la continuidad', type: 'textarea', value: reason, required: true, max: 2000},
    ];
    ctx.act({title: 'Entregar primera atención', type: 'ATTENTION_SEND', id: number(row, 'id'), revision: number(row, 'revision'), fields, note: 'No crea una SOL ni un Caso. La persona a cargo se mantiene hasta que el destino recibe. No transfiere el principal del REQ.'});
  }
  function resolve() {
    ctx.act({title: 'Resolver primera atención sin Caso', type: 'ATTENTION_RESOLVE', id: number(row, 'id'), revision: number(row, 'revision'), fields: [
      {key: 'result', label: 'Resultado', type: 'select', required: true, options: labeled(IMMEDIATE_RESULTS, RESULT_LABELS)},
      {key: 'effective_at', label: 'Fecha de la gestión realizada', type: 'datetime-local', required: true, value: localTime()},
      {key: 'reason', label: 'Qué se resolvió efectivamente', type: 'textarea', required: true, max: 12000},
    ], note: 'Cierra el REQ mediante la facultad de primera atención. No crea Caso, no envía correo y no cancela compromisos automáticamente.'});
  }

  if (!bool(info, 'can_edit') && text(info, 'state') === 'PENDIENTE_RECEPCION') return null;
  if (!bool(info, 'can_edit')) return <Callout tone="neutral">{LOCKED_NOTICE[text(info, 'state')] ?? 'La entrega debe recibirse, devolverse o retirarse antes de seguir editando.'}</Callout>;
  const followup = bool(info, 'followup_required');
  return <div className="continuity-grid">
    <section className="panel">
      <div className="panel-head"><div><h2><Icon name="handoff"/>Entregar la atención</h2><p>Puede continuar un profesional habilitado sin esperar la asignación de un Caso. La jefatura principal se conserva.</p></div></div>
      <form onSubmit={send}>
        <ChipRadios legend="Enviar a" value={recipient} onChange={v => {setRecipient(v); setScope(''); setUser('');}} options={[{value: 'PROFESIONAL', label: 'Profesional disponible'}, {value: 'JEFATURA', label: 'Dirección o coordinación competente'}]}/>
        <div className="form-grid">
          <LabeledField label="Ámbito de destino" required><select required value={scope} onChange={e => {setScope(e.target.value); setUser('');}}>
            <option value="">Seleccionar</option>
            {destinations.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select></LabeledField>
          {recipient === 'PROFESIONAL' && <LabeledField label="Profesional habilitado en el destino" required><select required value={user} onChange={e => setUser(e.target.value)}>
            <option value="">{scope ? 'Seleccionar' : 'Elige primero el ámbito'}</option>
            {candidates.map(p => <option key={number(p, 'id')} value={number(p, 'id')}>{text(p, 'name')}</option>)}
          </select></LabeledField>}
        </div>
        {recipient === 'JEFATURA' && scope && <p className="receiver"><Icon name="user" size={16}/>Recibe: <strong>{receiverLabel(target)}</strong></p>}
        <LabeledField label="Motivo de la entrega" required><textarea required maxLength={2000} value={reason} onChange={e => setReason(e.target.value)}/></LabeledField>
        <button className="primary"><Icon name="send" size={18}/>Preparar entrega</button>
      </form>
    </section>
    <section className="panel">
      <div className="panel-head"><div><h2><Icon name="checkCircle"/>Resolver en el primer contacto</h2><p>Solo si la orientación, respuesta o remisión quedaron efectivamente concluidas, sin seguimiento pendiente.</p></div></div>
      {followup && <Callout tone="warn">La ficha indica seguimiento: requiere abrir un Caso o rectificar, con fundamento, el curso de acción.</Callout>}
      <button disabled={followup} onClick={resolve}><Icon name="check" size={18}/>Resolver atención inmediata</button>
    </section>
  </div>;
}
