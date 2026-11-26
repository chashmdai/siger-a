import type {Row} from '../../lib/rows';
import {bool, list, number, obj, text, when} from '../../lib/rows';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {StatFields} from '../../components/StatFields';
import {Icon} from '../../components/Icon';
import {Callout, Panel} from '../../components/Primitives';
import type {StatBlock, StatVar} from './characterizationModel.ts';
import {fichaVars, offered, specials, statLists, statPayload, statProblems, valueText} from './characterizationModel.ts';

/** Origen de un valor, en lenguaje llano (los codigos de origen nunca se muestran). */
const ORIGIN_TEXT: Record<string, string> = {SISTEMA: 'Derivada del canal de ingreso', RETROSPECTIVA: 'Registrada después de una reapertura', FUNCIONARIO: 'Registrada por un funcionario'};

/**
 * Campo del bloque "Caracterizacion del ingreso" para un dialogo de comando (campo propio de ActionDialog, clave "stat"):
 * guarda el bloque listo para enviar (o nada) y valida lo que el acto exige. required puede depender de otros datos del
 * dialogo (por ejemplo, la decision de descartar por duplicado).
 */
export function statField(catalog: Row, vars: StatVar[], opts: {required?: (data: Row) => string[]; lead?: string; mustChange?: boolean} = {}): Field {
  const lists = statLists(catalog), special = specials(catalog);
  return {key: 'stat', label: 'Caracterización del ingreso', type: 'custom',
    render: (value, set, disabled, data) => <StatFields vars={vars} lists={lists} special={special} value={value as StatBlock | undefined} onChange={set} disabled={disabled}
      required={opts.required?.(data) ?? []} lead={opts.lead}/>,
    validate: (value, data) => {
      if (opts.mustChange && !statPayload(vars, value as StatBlock | undefined)) return 'Elige al menos un valor para registrar.';
      return statProblems(vars, value as StatBlock | undefined, opts.required?.(data) ?? [])[0] ?? null;
    }};
}

/**
 * Tarjeta "Caracterizacion" de la COM o del REQ (0.8.0, E09; DISENO 10.1): estado de cada variable, valor con su rotulo,
 * propuesta aparte (no es un estado), historial y Clasificar o corregir (STAT_CLASSIFY sobre la COM). Sin facultad no hay
 * botones; un registro cerrado se lee con "Requiere reapertura formal". closedRecord: el REQ o el Caso esta cerrado.
 */
export function Characterization({c, comId, ctx, closedRecord}: {c: Row; comId: number; ctx: Context; closedRecord?: boolean}) {
  const vars = fichaVars(c), special = specials(ctx.catalog);
  if (!vars.length) return null;
  const blocked = text(c, 'blocked');
  const classify = () => ctx.act({title: 'Clasificar o corregir el ingreso', type: 'STAT_CLASSIFY', id: comId, revision: number(c, 'revision'),
    fields: [statField(ctx.catalog, vars, {mustChange: true, lead: 'Elige solo lo que sabes. Corregir un valor ya informado pide un motivo.'})],
    note: 'La clasificación no cambia el canal ni el relato originales. Queda en el historial con tu nombre.'});
  const history = vars.flatMap(v => list(list(c, 'variables').find(x => text(x, 'variable') === v.variable) ?? {}, 'history').map(h => ({...h, var: v.label}) as Row));
  return <Panel title="Caracterización" icon="layers" description="Tipo de solicitud, requirente y vía de ingreso: datos para las estadísticas institucionales."
    actions={bool(c, 'can_classify') && offered(vars).length > 0 ? <button className="sm" onClick={classify}><Icon name="edit" size={16}/>Clasificar o corregir</button> : undefined}>
    {blocked && <Callout tone="neutral" title={closedRecord ? 'Requiere reapertura formal' : undefined}>{blocked}</Callout>}
    <dl className="stat-list">
      {vars.map(v => {
        const row = list(c, 'variables').find(x => text(x, 'variable') === v.variable) ?? {};
        return <div key={v.variable}>
          <dt>{v.label}</dt>
          <dd>
            <span className={'stat-value' + (v.state === 'PENDIENTE' ? ' pending' : '')}>{valueText(v, special)}</span>
            {text(row, 'origin') && v.state !== 'PENDIENTE' && <small className="muted">{ORIGIN_TEXT[text(row, 'origin')] ?? ''}</small>}
            {v.proposal && <small className="stat-proposal-note">Propuesta: {v.proposal.label} · no cuenta como informada hasta que alguien la confirme.</small>}
            {!v.catalog_current && <small className="muted">Sin listado vigente: no se clasifica por ahora.</small>}
          </dd>
        </div>;
      })}
    </dl>
    {history.length > 0 && <details className="disclosure compact">
      <summary><Icon name="history" size={16}/>Historial de la clasificación <small className="muted">({history.length})</small></summary>
      <ol className="timeline compact">{history.map(h => <li key={number(h, 'id')}>
        <div className="timeline-head"><strong>{text(h, 'var')}</strong><span className="timeline-meta">{when(h.recorded_ms)} · {text(h, 'author')}</span></div>
        <p className="timeline-body">{valueText({state: text(h, 'status'), value_label: text(h, 'value_label')}, special)}{ORIGIN_TEXT[text(h, 'origin')] ? ' · ' + ORIGIN_TEXT[text(h, 'origin')] : ''}</p>
        {text(h, 'basis') && <p className="timeline-meta preserve">Motivo: {text(h, 'basis')}</p>}
      </li>)}</ol>
    </details>}
  </Panel>;
}

/** Caracterizacion de la ficha (o vacia si el servidor no la entrega). */
export const characterizationOf = (r: Row) => obj(r, 'characterization');
