import {useEffect, useState} from 'react';
import {request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {list, number, obj, text, when} from '../../lib/rows';
import {has, REGISTRAR_ROLES} from '../../lib/access';
import {scopeOf} from '../../lib/catalog';
import {firstName} from '../../lib/labels';
import type {Context} from '../../app/context';
import {BOARD_DETAIL, boardRoute} from '../../app/routes';
import {Kind, Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import {Callout, EmptyState, ErrorMessage, PageHeader, Panel} from '../../components/Primitives';
import {ACTIONABLE, greeting, say, today} from './texts';

/** Colas resumidas que se muestran en cada espacio de trabajo. */
const QUEUES_BY_SECTION: Record<string, string[]> = {distribution: ['com'], management: ['sol', 'cas', 'atenciones'], personal: ['tareas', 'atenciones'], overview: [], intake: ['atenciones'], technical: [], authority: [], audit: []};

const goto = (r: Row) => {location.hash = boardRoute(text(r, 'route'), text(r, 'filter'));};

/** Inicio: lo pendiente segun cada funcion vigente, con cifras del alcance autorizado. Se refresca cada minuto. */
export default function Dashboard({ctx, version}: {ctx: Context; version: number}) {
  const [data, setData] = useState<Row>({}), [error, setError] = useState(''), [selected, setSelected] = useState(''), [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const r = await request('/dashboard');
        if (active) {setData(r); setError('');}
      } catch (e) {
        if (active) setError((e as Error).message);
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    const timer = setInterval(() => {if (document.visibilityState === 'visible') void load();}, 60000);
    return () => {active = false; clearInterval(timer);};
  }, [version, ctx.user.id]);

  const sections = list(data, 'sections'), section = sections.find(s => text(s, 'id') === selected) ?? sections[0], role = section ? text(section, 'id') : '';
  const queues = list(data, 'queues').filter(q => (QUEUES_BY_SECTION[role] ?? []).includes(text(q, 'route')));
  const registrar = has(ctx.user, ...REGISTRAR_ROLES), operative = has(ctx.user, 'ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION', 'DISTRIBUIDOR');

  return <section className="dashboard">
    <PageHeader eyebrow={<><Icon name="calendar" size={14}/>{today()}</>} title={`${greeting()}, ${firstName(ctx.user.name)}`}
      lead={section ? say(text(section, 'help')) : 'Consultando tus funciones vigentes…'}
      aside={<div className="quick-actions">
        {registrar && <a className="button primary" href="#atencion"><Icon name="attention" size={18}/>Nueva atención</a>}
        {operative && <a className="button" href="#nuevo"><Icon name="inbox" size={18}/>Ingreso por clasificar</a>}
      </div>}/>
    {data.substituting === true && <Callout tone="warn" title="Estás ejerciendo una subrogancia">Ves también las funciones del titular en sus mismos ámbitos. Tu cartera personal se mantiene separada.</Callout>}
    {sections.length > 1 && <div className="workspace-tabs segmented" role="group" aria-label="Espacios de trabajo">
      {sections.map(s => <button aria-pressed={text(s, 'id') === role} key={text(s, 'id')} onClick={() => setSelected(text(s, 'id'))}>{say(text(s, 'title'))}</button>)}
    </div>}
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {loading && !section ? <div className="metrics" aria-busy="true" aria-label="Preparando tu inicio">{[0, 1, 2, 3].map(i => <span key={i} className="skeleton block"/>)}</div> : section && <div key={role} className="fade-in">
      <h2 className="sr-only">{say(text(section, 'title'))}</h2>
      <div className="metrics">
        {list(section, 'metrics').map(m => {
          const count = number(m, 'count'), actionable = ACTIONABLE.has(text(m, 'route') + '/' + text(m, 'filter')) && count > 0;
          return <button className={'metric' + (actionable ? ' needs-action' : '') + (count === 0 ? ' is-zero' : '')} key={text(m, 'label')} onClick={() => goto(m)}>
            <span className="metric-label">{say(text(m, 'label'))}{actionable && <span className="sr-only"> (requiere atención)</span>}</span>
            <span className="metric-value">{count.toLocaleString('es-CL')}</span>
            <span className="metric-hint">{say(text(m, 'hint'))}</span>
            <span className="metric-go">{count === 0 ? 'Ver bandeja' : 'Revisar'}<Icon name="arrowRight" size={16}/></span>
          </button>;
        })}
      </div>
      {role === 'intake' && <div className="actions">
        <a className="button" href="#atenciones?filter=incoming"><Icon name="handoff" size={18}/>Entregas por recibir</a>
        <a className="button" href="#com?filter=mine"><Icon name="mail" size={18}/>Mis registros</a>
      </div>}
      {role === 'overview' && <Callout tone="neutral">COM, SOL, REQ y Caso no son equivalentes: un REQ puede contener varios Casos. Los conteos se calculan sobre tu alcance autorizado, no sobre una página de resultados.</Callout>}
      {role === 'technical' && <Panel title="Estado de la candidata" icon="info">
        <p>Versión 0.4.0. SMR y correo real desactivados. La cuarentena no equivale a un análisis antivirus.</p>
        <p>Utiliza el diagnóstico y los respaldos de la instancia antes de una actualización.</p>
        <a className="button sm" href="#auditoria">Ver operación autorizada<Icon name="arrowRight" size={16}/></a>
      </Panel>}
      {role === 'authority' && <Callout>La subrogancia sustituye la función vigente, incluidas sus áreas habilitadas. No copia Casos ni tareas y no permite el autocierre del Caso propio del subrogante.</Callout>}
      {queues.length > 0 && <div className="queues">{queues.map(q => <Queue key={text(q, 'title')} queue={q} ctx={ctx}/>)}</div>}
    </div>}
    <p className="dashboard-footnote"><Icon name="info" size={16}/>Los recordatorios vencidos son tu agenda personal, no un incumplimiento normativo. Un aviso no equivale a acuse ni a actuación.</p>
  </section>;
}

function Queue({queue, ctx}: {queue: Row; ctx: Context}) {
  const page = obj(queue, 'page'), rows = list(page, 'items'), kind = text(queue, 'route');
  const route = BOARD_DETAIL[kind] ?? 'CAS';
  return <Panel flush title={say(text(queue, 'title'))} count={number(page, 'total')}
    actions={<button className="ghost sm" onClick={() => goto(queue)}>Ver todos<Icon name="arrowRight" size={16}/></button>}>
    {rows.length === 0 ? <EmptyState compact icon="checkCircle" title="Sin pendientes en esta cola">Los registros aparecerán aquí cuando te corresponda intervenir.</EmptyState> : <ul className="work-list">
      {rows.map(r => <li key={number(r, 'id')}>
        <button className="work-item" onClick={() => ctx.go(route, kind === 'tareas' ? number(r, 'case_id') : number(r, 'id'))}>
          <span className="work-item-ref"><Kind kind={route}/><span className="folio">{text(r, 'folio') || text(r, 'case_folio') || 'SOL ' + number(r, 'id')}</span></span>
          <span className="work-item-main">
            <strong>{text(r, 'subject') || text(r, 'title') || text(r, 'objective') || 'Sin asunto registrado'}</strong>
            <span>{kind === 'tareas' ? 'Fecha prevista: ' + when(r.due_ms) : scopeOf(ctx.catalog, r.destination_scope ?? r.principal_scope ?? r.scope_id ?? r.origin_scope) + ' · ' + when(r.created_ms)}</span>
          </span>
          <span className="work-item-end"><Status value={r.state}/><Icon name="right" size={18}/></span>
        </button>
      </li>)}
    </ul>}
  </Panel>;
}
