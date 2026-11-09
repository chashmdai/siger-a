import {useEffect, useRef, useState} from 'react';
import {request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {list, number, text, when} from '../../lib/rows';
import {nameOf, scopeOf, scopes} from '../../lib/catalog';
import {CHANNEL_LABELS, FLOW_LABELS, humanize} from '../../lib/labels';
import type {Context} from '../../app/context';
import {BOARD_DETAIL, DETAIL_KINDS} from '../../app/routes';
import {Table} from '../../components/Table';
import type {Column} from '../../components/Table';
import {Kind, Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import type {IconName} from '../../components/Icon';
import {Callout, ErrorMessage, PageHeader} from '../../components/Primitives';

type Board = {title: string; lead: string; icon: IconName; empty: string};
const BOARDS: Record<string, Board> = {
  atenciones: {title: 'Primeras atenciones', icon: 'people', lead: 'Atenciones registradas como REQ desde el primer contacto. Abre una para completar la ficha, entregarla o resolverla.', empty: 'No hay atenciones para este filtro.'},
  com: {title: 'Comunicaciones', icon: 'mail', lead: 'Todo lo recibido, tal como se registró. Las COM por distribuir esperan su clasificación.', empty: 'No hay comunicaciones para este filtro.'},
  sol: {title: 'Solicitudes', icon: 'send', lead: 'Solicitudes enviadas a una jefatura. Al asumir una SOL nace su Requerimiento.', empty: 'No hay solicitudes pendientes para este filtro.'},
  req: {title: 'Requerimientos', icon: 'folder', lead: 'Un REQ puede contener varios Casos y se cierra de forma expresa por su jefatura principal.', empty: 'No hay requerimientos para este filtro.'},
  cas: {title: 'Casos', icon: 'briefcase', lead: 'Líneas de gestión profesional dentro de un REQ. Las tareas pendientes impiden el cierre.', empty: 'No hay Casos para este filtro.'},
  flujos: {title: 'Colaboraciones y traslados', icon: 'flow', lead: 'Colaboraciones, derivaciones y transferencias entre unidades y su decisión.', empty: 'No hay peticiones entre unidades para este filtro.'},
  tareas: {title: 'Mis tareas', icon: 'tasks', lead: 'Recordatorios personales del equipo de cada Caso.', empty: 'No tienes tareas para este filtro.'},
  avisos: {title: 'Mis avisos', icon: 'bell', lead: 'Avisos internos de SIGER. Un aviso no equivale a acuse ni a actuación.', empty: 'No tienes avisos para este filtro.'},
};
/** Filtros que reconoce /api/worklist/{kind}; el primero es el predeterminado salvo en tareas. */
const FILTERS: Record<string, [string, string][]> = {
  atenciones: [['all', 'Todas'], ['mine', 'A mi cargo'], ['incoming', 'Por recibir'], ['outgoing', 'Entregas enviadas'], ['created', 'Iniciadas por mí'], ['followup', 'Seguimiento por asignar'], ['closed', 'Resueltas']],
  com: [['all', 'Todas'], ['pending', 'Por distribuir'], ['mine', 'Mis ingresos']],
  sol: [['all', 'Pendientes y devueltas'], ['pending', 'Por asumir'], ['returned', 'Devueltas a Distribución']],
  req: [['all', 'Todos'], ['open', 'Abiertos'], ['closed', 'Cerrados'], ['without_case', 'Abiertos sin Caso'], ['ready', 'Sin bloqueos para cierre']],
  cas: [['all', 'Todos'], ['mine', 'Mis Casos abiertos'], ['open', 'Abiertos'], ['closed', 'Cerrados'], ['review', 'Cierres por revisar'], ['unacknowledged', 'Asignaciones sin acuse'], ['returned', 'Mis cierres devueltos']],
  flujos: [['all', 'Todos'], ['pending', 'Pendientes'], ['incoming', 'Para mi decisión'], ['outgoing', 'Solicitados por mi ámbito']],
  tareas: [['pending', 'Pendientes'], ['overdue', 'Vencidas'], ['soon', 'Próximos 7 días'], ['all', 'Incluye terminadas']],
  avisos: [['all', 'Todos'], ['unread', 'Sin leer']],
};
const PAGE_SIZE = 25;
const defaultFilter = (kind: string, initial: string) => initial || (kind === 'tareas' ? 'pending' : 'all');

function columnsFor(kind: string, ctx: Context): Column[] {
  const open = (k: string, id: number) => () => ctx.go(k, id);
  const target = BOARD_DETAIL[kind] ?? 'FLUJO';
  const reference: Column = {name: 'Referencia', render: r => <span className="ref-cell"><Kind kind={target}/><button className="link folio" onClick={open(target, number(r, 'id'))}>{text(r, 'folio') || (target === 'SOL' ? 'SOL ' : 'Petición ') + number(r, 'id')}</button></span>};
  const state: Column = {name: 'Estado', render: r => <Status value={r.state}/>};
  const scope: Column = {name: 'Ámbito', render: r => scopeOf(ctx.catalog, r.principal_scope ?? r.scope_id ?? r.destination_scope ?? r.origin_scope)};
  const created: Column = {name: 'Registro', render: r => <span className="nowrap">{when(r.created_ms)}</span>};
  switch (kind) {
    case 'com': return [reference, {name: 'Asunto', render: r => <span className="cell-strong">{text(r, 'subject')}</span>}, state, {name: 'Canal', render: r => humanize(r.channel, CHANNEL_LABELS)}, scope, created];
    case 'sol': return [reference, {name: 'COM y asunto', render: r => <><span className="folio">{text(r, 'com_folio')}</span><small>{text(r, 'subject')}</small></>}, state, scope, created];
    case 'req': return [reference, state, scope, created, {name: 'Complejidad', render: r => <Status value={r.complexity}/>}, {name: 'Casos abiertos', render: r => <span className="tabular">{text(r, 'open_cases')} de {text(r, 'total_cases')}</span>}];
    case 'cas': return [reference, {name: 'Objetivo', render: r => <span className="cell-strong">{text(r, 'objective')}</span>}, state, {name: 'REQ', render: r => <span className="folio">{text(r, 'req_folio')}</span>}, {name: 'Responsable', render: r => nameOf(ctx.catalog, r.responsible_id)}, scope];
    case 'flujos': return [reference, {name: 'Tipo', render: r => humanize(r.kind, FLOW_LABELS)}, {name: 'REQ', render: r => <span className="folio">{text(r, 'req_folio')}</span>}, {name: 'Origen', render: r => scopeOf(ctx.catalog, r.origin_scope)}, {name: 'Destino', render: r => scopeOf(ctx.catalog, r.target_scope)}, state];
    case 'atenciones': return [
      {name: 'Requerimiento', render: r => <span className="ref-cell"><Kind kind="REQ"/><button className="link folio" onClick={open('REQ', number(r, 'id'))}>{text(r, 'folio')}</button></span>},
      {name: 'Motivo inicial', render: r => <span className="cell-strong clamp">{text(r, 'subject')}</span>},
      {name: 'Continuidad', render: r => <Status value={r.state}/>},
      {name: 'A cargo de', render: r => nameOf(ctx.catalog, r.attention_user)},
      {name: 'Última complementación', render: r => <span className="nowrap">{when(r.updated_ms)}</span>},
    ];
    case 'tareas': return [
      {name: 'Caso', render: r => <span className="ref-cell"><Kind kind="CAS"/><button className="link folio" onClick={open('CAS', number(r, 'case_id'))}>{text(r, 'case_folio')}</button></span>},
      {name: 'Recordatorio', render: r => <span className="cell-strong">{text(r, 'title')}</span>},
      state,
      {name: 'Fecha prevista', render: r => <span className="nowrap">{when(r.due_ms)}</span>},
      {name: 'Próximo aviso', render: r => <span className="nowrap">{when(r.next_reminder_ms)}</span>},
    ];
    case 'avisos': return [
      {name: 'Fecha', render: r => <span className="nowrap">{when(r.at_ms)}</span>},
      {name: 'Aviso', render: r => <span className={r.read_ms ? '' : 'cell-strong'}>{text(r, 'text_value')}</span>},
      {name: 'Lectura', render: r => r.read_ms ? <span className="chip"><Icon name="check" size={14}/>Leído</span> : <button className="sm" onClick={() => ctx.act({title: 'Marcar aviso leído', type: 'NOTICE_READ', id: number(r, 'id'), revision: null, fields: [], after: ctx.reload})}>Marcar leído</button>},
      {name: 'Referencia', render: r => DETAIL_KINDS.includes(text(r, 'resource_type')) ? <button className="sm" onClick={open(text(r, 'resource_type'), number(r, 'resource_id'))}>Abrir con permisos actuales</button> : text(r, 'resource_type')},
    ];
    default: return [reference, state, scope, created];
  }
}

/** Fila que abre su ficha al hacer clic en cualquier parte (el enlace de referencia sigue disponible por teclado). */
function opener(kind: string, ctx: Context): ((r: Row) => void) | undefined {
  if (kind === 'avisos') return undefined;
  if (kind === 'tareas') return r => ctx.go('CAS', number(r, 'case_id'));
  return r => ctx.go(BOARD_DETAIL[kind] ?? 'FLUJO', number(r, 'id'));
}

/** Bandeja paginada en el servidor: busqueda, filtro y ambito se aplican sobre el alcance autorizado. */
export default function WorkspaceList({kind, initialFilter, initialQuery = '', ctx, version}: {kind: string; initialFilter: string; initialQuery?: string; ctx: Context; version: number}) {
  const [state, setState] = useState(defaultFilter(kind, initialFilter)), [q, setQ] = useState(initialQuery), [query, setQuery] = useState(initialQuery), [scope, setScope] = useState('');
  const [page, setPage] = useState(0), [data, setData] = useState<Row | null>(null), [busy, setBusy] = useState(true), [error, setError] = useState('');
  const board = BOARDS[kind];
  useEffect(() => {setState(defaultFilter(kind, initialFilter)); setPage(0);}, [kind, initialFilter]);
  useEffect(() => {setQ(initialQuery); setQuery(initialQuery); setPage(0);}, [initialQuery]);
  // La busqueda se aplica sola tras una breve pausa al escribir; Enter la aplica de inmediato.
  const typing = useRef(0);
  useEffect(() => {
    window.clearTimeout(typing.current);
    if (q === query) return;
    typing.current = window.setTimeout(() => {setQuery(q.trim()); setPage(0);}, 450);
    return () => window.clearTimeout(typing.current);
  }, [q, query]);
  useEffect(() => {
    let active = true;
    setBusy(true);
    const params = new URLSearchParams({page: String(page), size: String(PAGE_SIZE), q: query, filter: state});
    if (scope) params.set('scope', scope);
    void request('/worklist/' + kind + '?' + params)
      .then(r => {if (active) {setData(r); setError('');}})
      .catch(e => {if (active) setError((e as Error).message);})
      .finally(() => {if (active) setBusy(false);});
    return () => {active = false;};
  }, [kind, page, query, state, scope, ctx.user.id, version]);

  const total = number(data ?? {}, 'total'), actual = number(data ?? {}, 'page'), pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const from = total === 0 ? 0 : actual * PAGE_SIZE + 1, to = Math.min(total, (actual + 1) * PAGE_SIZE);
  const filtered = Boolean(query || scope);
  const clear = () => {setQ(''); setQuery(''); setScope(''); setPage(0);};
  return <>
    <PageHeader eyebrow={<><Icon name={board?.icon ?? 'inbox'} size={14}/>Bandeja</>} title={board?.title ?? kind} lead={board?.lead}
      aside={<>
        {data && <span className="result-count" aria-live="polite"><strong className="tabular">{total.toLocaleString('es-CL')}</strong> {total === 1 ? 'registro' : 'registros'}</span>}
        {['req', 'cas', 'com'].includes(kind) && <a className="button sm" href={'/api/export?kind=' + kind.toUpperCase()}><Icon name="download" size={16}/>Exportar CSV</a>}
      </>}/>
    {kind === 'tareas' && <Callout tone="warn">Toda tarea pendiente bloquea el cierre del Caso. Los avisos internos requieren que SIGER esté encendido.</Callout>}
    <div className="board-tools">
      <div className="segmented" role="group" aria-label="Filtrar bandeja">
        {(FILTERS[kind] ?? []).map(([v, l]) => <button key={v} aria-pressed={state === v} onClick={() => {setState(v); setPage(0);}}>{l}</button>)}
      </div>
      <form className="board-search" role="search" onSubmit={e => {e.preventDefault(); window.clearTimeout(typing.current); setQuery(q.trim()); setPage(0);}}>
        <label className="input-icon">
          <span className="sr-only">Buscar por referencia o texto visible</span>
          <Icon name="search" size={18}/>
          <input type="search" maxLength={160} placeholder="Buscar por folio o texto" value={q} onChange={e => setQ(e.target.value)}/>
        </label>
        {kind !== 'avisos' && <label className="board-scope">
          <span className="sr-only">Ámbito</span>
          <select value={scope} onChange={e => {setScope(e.target.value); setPage(0);}}>
            <option value="">Todos mis ámbitos</option>
            {scopes(ctx.catalog).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </label>}
      </form>
    </div>
    {error && <ErrorMessage>{error}</ErrorMessage>}
    <div className="table-busy" aria-busy={busy}>
      {!data ? <div className="table-skeleton" aria-label="Consultando bandeja" role="status">{Array.from({length: 6}, (_, i) => <span key={i} className="skeleton line"/>)}</div>
        : <Table rows={list(data, 'items')} columns={columnsFor(kind, ctx)} local={false} open={opener(kind, ctx)} emptyIcon={board?.icon}
          empty={filtered ? 'Sin resultados con esta búsqueda' : board?.empty ?? 'No hay registros para este filtro.'}
          emptyHint={filtered ? <>Revisa el texto o el ámbito elegido. <button className="link" onClick={clear}>Limpiar búsqueda</button></> : 'Solo se muestran registros de tu alcance autorizado.'}/>}
    </div>
    {total > 0 && <div className="table-foot">
      <span className="tabular">Mostrando {from}–{to} de {total.toLocaleString('es-CL')} · {PAGE_SIZE} por página</span>
      <div className="pager">
        <button className="sm" disabled={busy || actual === 0} onClick={() => setPage(actual - 1)}><Icon name="left" size={16}/>Anterior</button>
        <span>Página {actual + 1} de {pages}</span>
        <button className="sm" disabled={busy || (actual + 1) * PAGE_SIZE >= total} onClick={() => setPage(actual + 1)}>Siguiente<Icon name="right" size={16}/></button>
      </div>
    </div>}
  </>;
}
