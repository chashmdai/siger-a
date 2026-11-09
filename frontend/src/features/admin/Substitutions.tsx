import type {Row} from '../../lib/rows';
import {list, localTime, number, text, when} from '../../lib/rows';
import {authorityAt} from '../../lib/access';
import {nameOf, scopeOf} from '../../lib/catalog';
import {choose, person, reason, scopeField} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Table} from '../../components/Table';
import {Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import {Callout, PageHeader, Panel} from '../../components/Primitives';

/**
 * Subrogancias: la autoridad habilitada activa a una persona predesignada, con periodo finito.
 * El titular puede registrar su retorno. No se mueven Casos ni tareas.
 */
export default function Substitutions({ctx, workspace}: {ctx: Context; workspace: Row}) {
  const designate = () => ctx.act({title: 'Predesignar subrogante', type: 'DESIGNATE', id: null, revision: null, fields: [scopeField(ctx.catalog), person(ctx.catalog, 'titular_id', 'Titular de la función'), person(ctx.catalog, 'substitute_id', 'Profesional predesignado'), reason]});
  const activate = (d: Row) => choose(ctx, 'Activar subrogancia directamente', 'SUBSTITUTE_ACTIVATE', d, [
    {key: 'from_at', label: 'Desde', type: 'datetime-local', required: true, value: localTime()},
    {key: 'until_at', label: 'Hasta (fecha límite de revisión)', type: 'datetime-local', required: true, value: localTime(86400000)},
    reason,
  ], 'Exige autoridad independiente, período finito y ausencia de superposición. No cambia responsables profesionales.');
  const end = (d: Row) => choose(ctx, 'Registrar retorno o revocación', 'SUBSTITUTE_END', d, [reason], 'El titular recupera su función. No se confirman cierres pendientes automáticamente.');
  const canEnd = (d: Row) => ['ACTIVA', 'PROGRAMADA'].includes(text(d, 'state')) && (authorityAt(ctx.user, number(d, 'scope_id')) || number(d, 'titular_id') === ctx.user.id);

  const authority = ctx.user.grants.some(g => g.role === 'AUTORIDAD');
  return <>
    <PageHeader eyebrow={<><Icon name='swap' size={14}/>Funciones temporales</>} title='Subrogancias'
      lead='La autoridad habilitada activa a una persona predesignada por un período finito. No requiere intervención de TI ni mueve Casos o tareas.'
      aside={authority && <button className='primary' onClick={designate}><Icon name='plus' size={18}/>Registrar predesignación</button>}/>
    <Panel title='Predesignaciones' icon='people' count={list(workspace, 'designations').length} description='Personas designadas de antemano para subrogar una función.'>
      <Table rows={list(workspace, 'designations')} emptyIcon='people' empty='Sin predesignaciones en tu alcance' columns={[
        {name: 'Ámbito', render: d => scopeOf(ctx.catalog, d.scope_id)},
        {name: 'Titular', render: d => nameOf(ctx.catalog, d.titular_id)},
        {name: 'Subrogante', render: d => nameOf(ctx.catalog, d.substitute_id)},
        {name: 'Acción', render: d => authorityAt(ctx.user, number(d, 'scope_id')) ? <button className='sm' onClick={() => activate(d)}>Activar</button> : <span className='muted'>Consulta</span>},
      ]}/>
    </Panel>
    <Panel title='Períodos e historial' icon='history' count={list(workspace, 'substitutions').length}>
      <Table rows={list(workspace, 'substitutions')} emptyIcon='history' empty='Sin subrogancias registradas' columns={[
        {name: 'Ámbito', render: d => scopeOf(ctx.catalog, d.scope_id)},
        {name: 'Persona', render: d => nameOf(ctx.catalog, d.substitute_id)},
        {name: 'Estado', render: d => <Status value={d.state}/>},
        {name: 'Inicio', render: d => when(d.from_ms)},
        {name: 'Término', render: d => when(d.until_ms)},
        {name: 'Acción', render: d => canEnd(d) ? <button className='sm' onClick={() => end(d)}>Terminar</button> : <span className='muted'>Histórico</span>},
      ]}/>
    </Panel>
    <Callout tone='neutral'>El Caso propio del subrogante conserva la restricción de autocierre. No se habilita una revisión excepcional por el mero título de autoridad.</Callout>
  </>;
}
