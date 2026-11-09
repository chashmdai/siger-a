import type {Row} from '../../lib/rows';
import {bool, number, obj, text} from '../../lib/rows';
import {scopeOf, solicitationPeople} from '../../lib/catalog';
import {choose, decision, reason, routingFields} from '../../lib/fields';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {Icon} from '../../components/Icon';
import {Facts, Panel} from '../../components/Primitives';
import {ActionBar, DetailGrid} from './parts';

/** Solicitud a una jefatura: asumir (nace el REQ), redirigir, devolver o descartar. */
export function SolDetail({r, ctx}: {r: Row; ctx: Context}) {
  const com = obj(r, 'communication');
  const choice = (title: string, type: string, fields: Field[], note?: string) => () => choose(ctx, title, type, r, fields, note);
  return <DetailGrid
    main={<>
      <ActionBar label="Decisión">
        {bool(r, 'can_decide') && <>
          <button className="primary" onClick={choice('Asumir SOL y crear REQ', 'SOL_ASSUME', [{key: 'responsible_id', label: 'Profesional encargado de la unidad', type: 'select', required: true, options: solicitationPeople(ctx.catalog, number(r, 'destination_scope'))}, reason], 'Se crea un REQ sin Caso. Si hay seguimiento, abre un Caso desde el REQ.')}><Icon name="check" size={18}/>Asumir y asignar atención</button>
          <button onClick={choice('Redirigir la misma SOL', 'SOL_ROUTE', [...routingFields(ctx.catalog), reason])}><Icon name="send" size={18}/>Redirigir</button>
          <button onClick={choice('Devolver a clasificación', 'SOL_RETURN', [reason], 'La misma SOL vuelve a su distribuidor; no se elimina ni se crea otro ingreso.')}><Icon name="arrowLeft" size={18}/>Devolver a Distribución</button>
          <button onClick={choice('Declarar ingreso inválido o duplicado', 'SOL_DISCARD', [decision([['INVALIDA', 'Inválida'], ['DUPLICADA', 'Duplicada']], 'disposition'), reason], 'No elimina el original. No usar para consultas legítimas resueltas.')}>Inválida o duplicada</button>
        </>}
        {bool(r, 'can_redistribute') && <button className="primary" onClick={choice('Redistribuir SOL devuelta', 'SOL_ROUTE', [...routingFields(ctx.catalog), reason])}><Icon name="send" size={18}/>Redistribuir</button>}
      </ActionBar>
      <Panel title="Ingreso que la originó" icon="mail" actions={<button className="sm" onClick={() => ctx.go('COM', number(r, 'communication_id'))}>Ver ingreso y documentos<Icon name="right" size={16}/></button>}>
        <h3>{text(com, 'subject')}</h3>
        <p className="preserve reading">{text(com, 'body')}</p>
      </Panel>
    </>}
    side={<Panel title="Destino" icon="send">
      <Facts stacked items={[
        ['Recibe', text(r, 'receiving_authority')],
        ['Área sugerida', r.requested_area_scope ? scopeOf(ctx.catalog, r.requested_area_scope) : 'Sin área específica'],
        ['Clasificación', text(r, 'classification') ? <span className="preserve">{text(r, 'classification')}</span> : ''],
      ]}/>
    </Panel>}/>;
}
