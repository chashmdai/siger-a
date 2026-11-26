import type {ReactNode} from 'react';
import type {Row} from '../../lib/rows';
import {bool, number, obj, text} from '../../lib/rows';
import {humanize, MATTER_LABELS} from '../../lib/labels';
import {scopeOf, scopeRow, solicitationPeople} from '../../lib/catalog';
import {choose, destinationField, optionalObservation} from '../../lib/fields';
import type {Context} from '../../app/context';
import type {Field} from '../../components/ActionDialog';
import {Icon} from '../../components/Icon';
import {Callout, Facts, Panel} from '../../components/Primitives';
import {ActionBar, DetailGrid} from './parts';
import {request} from '../../lib/api';
import {blockersOf} from './ClosureStatus';
import {characterizationOf} from './Characterization';
import {fichaVars} from './characterizationModel.ts';
import {discardFields} from './actions';

/** Solicitud a una jefatura: asumir (nace el REQ), redirigir, devolver o descartar. */
export function SolDetail({r, ctx}: {r: Row; ctx: Context}) {
  const com = obj(r, 'communication');
  const regional = scopeRow(ctx.catalog, r.destination_scope)?.regional === true;
  const choice = (title: string, type: string, fields: Field[], note?: string) => () => choose(ctx, title, type, r, fields, note);
  /**
   * Descarte (0.8.0, E09): una duplicada cuenta en las estadisticas y exige tipo de solicitud, requirente y via
   * (stat_completeness.duplicate); el dialogo permite completarlos en el mismo acto. La ficha de la SOL no trae la
   * caracterizacion de su COM: se lee de la ficha de la COM, que quien decide la SOL puede consultar.
   */
  async function discard() {
    let vars = fichaVars({});
    try {
      const comRow = await request(`/detail/COM/${number(r, 'communication_id')}`);
      const c = characterizationOf(comRow);
      if (!text(c, 'blocked')) vars = fichaVars(c);
    } catch {/* Sin la caracterizacion, el servidor sigue validando: un 409 se muestra en el dialogo. */}
    const pending = blockersOf(r, 'discard_blockers');
    choose(ctx, 'Declarar ingreso inválido o duplicado', 'SOL_DISCARD', r, [
      ...discardFields(ctx.catalog, vars),
      ...(pending.length ? [{key: 'discard_info', label: 'Pendiente', type: 'custom' as const,
        render: () => <Callout tone="warn" title="Para declararla duplicada">{pending.join(' ')}</Callout>}] : []),
    ], 'No elimina el original. No usar para consultas legítimas resueltas.');
  }
  return <DetailGrid
    main={<>
      <ActionBar label="Decisión">
        {bool(r, 'can_decide') && <>
          <button className="primary" onClick={choice('Asumir SOL y crear REQ', 'SOL_ASSUME', [{key: 'responsible_id', label: 'Profesional encargado de la unidad', type: 'select', required: true, options: solicitationPeople(ctx.catalog, number(r, 'destination_scope'))}], 'Se crea un REQ sin Caso. Si hay seguimiento, abre un Caso desde el REQ.')}><Icon name="check" size={18}/>Asumir y asignar atención</button>
          <button onClick={choice('Redirigir la misma SOL', 'SOL_ROUTE', [destinationField(ctx.catalog, 'SOL', text(obj(r, 'communication'), 'suggested_region'))])}><Icon name="send" size={18}/>Redirigir</button>
          <button onClick={choice('Devolver a clasificación', 'SOL_RETURN', [optionalObservation], 'La misma SOL vuelve a su distribuidor; no se elimina ni se crea otro ingreso.')}><Icon name="arrowLeft" size={18}/>Devolver a Distribución</button>
          <button onClick={() => void discard()}>Inválida o duplicada</button>
        </>}
        {bool(r, 'can_redistribute') && <button className="primary" onClick={choice('Redistribuir SOL devuelta', 'SOL_ROUTE', [destinationField(ctx.catalog, 'SOL', text(obj(r, 'communication'), 'suggested_region'))])}><Icon name="send" size={18}/>Redistribuir</button>}
      </ActionBar>
      <Panel title="Ingreso que la originó" icon="mail" actions={<button className="sm" onClick={() => ctx.go('COM', number(r, 'communication_id'))}>Ver ingreso y documentos<Icon name="right" size={16}/></button>}>
        <h3>{text(com, 'subject')}</h3>
        <p className="preserve reading">{text(com, 'body')}</p>
      </Panel>
    </>}
    side={<Panel title="Destino" icon="send">
      <Facts stacked items={[
        ['Recibe', text(r, 'receiving_authority')],
        regional ? ['Materia sugerida', r.suggested_matter ? humanize(r.suggested_matter, MATTER_LABELS) : 'Sin materia sugerida'] : ['Área sugerida', r.requested_area_scope ? scopeOf(ctx.catalog, r.requested_area_scope) : 'Sin área específica'],
        ...(text(r, 'classification').trim() ? [['Clasificación', <span className="preserve">{text(r, 'classification')}</span>] as [string, ReactNode]] : []),
      ]}/>
    </Panel>}/>;
}
