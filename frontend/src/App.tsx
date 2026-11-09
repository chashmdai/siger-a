import {useCallback, useEffect, useRef, useState} from 'react';
import * as api from './lib/api';
import type {Result, Session} from './lib/api';
import type {Row} from './lib/rows';
import {ActionDialog} from './components/ActionDialog';
import type {Action} from './components/ActionDialog';
import {ErrorMessage, Skeleton} from './components/Primitives';
import type {Context} from './app/context';
import {BOARDS, DETAIL_KINDS, parseRoute} from './app/routes';
import {menuFor} from './app/menu';
import {Login, PasswordChange, Splash} from './app/Login';
import {Shell} from './app/Shell';
import {CommandPalette} from './app/CommandPalette';
import Detail from './features/detail/Detail';
import Administration from './features/admin/Administration';
import Substitutions from './features/admin/Substitutions';
import Dashboard from './features/dashboard/Dashboard';
import WorkspaceList from './features/worklist/WorkspaceList';
import Intake from './features/intake/Intake';
import Attention from './features/attention/Attention';

const currentRoute = () => location.hash.slice(1) || 'inicio';

/** Mensajes propios para comandos de distribucion; el resto usa el mensaje del servidor. */
const SOL_MESSAGES: Record<string, string> = {
  SOL_ROUTE: 'SOL redirigida al nuevo destino.',
  SOL_RETURN: 'SOL devuelta a Clasificación y Distribución.',
  SOL_DISCARD: 'SOL resuelta y registrada.',
};

export default function App() {
  const [s, setS] = useState<Session | null>(null), [catalog, setCatalog] = useState<Row>({}), [workspace, setWorkspace] = useState<Row>({});
  const [route, setRoute] = useState(currentRoute()), [detail, setDetail] = useState<Row | null>(null), [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(''), [error, setError] = useState(''), [action, setAction] = useState<Action | null>(null), [tick, setTick] = useState(0), [unread, setUnread] = useState(0);
  const [palette, setPalette] = useState(false);

  // Sesion y contexto comun. Se recarga tras cada comando, al recuperar el foco y ante 401/428.
  const load = useCallback(async () => {
    try {
      const next = await api.session();
      setS(next);
      if (next.authenticated && !next.user?.must_change) {
        const [c, w] = await Promise.all([api.request('/catalog'), api.request('/workspace')]);
        setCatalog(c);
        setWorkspace(w);
        setUnread((await api.request<{unread: number}>('/notices/unread')).unread);
      } else {
        setCatalog({});
        setWorkspace({});
        setDetail(null);
        setUnread(0);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {void load();}, [load, tick]);
  useEffect(() => {
    const changed = () => {setRoute(currentRoute()); setError('');};
    window.addEventListener('hashchange', changed);
    return () => window.removeEventListener('hashchange', changed);
  }, []);
  useEffect(() => {
    const refresh = () => {void load();};
    window.addEventListener('siger-session', refresh);
    window.addEventListener('focus', refresh);
    return () => {window.removeEventListener('siger-session', refresh); window.removeEventListener('focus', refresh);};
  }, [load]);
  // Contador de avisos cada minuto mientras la pestana esta visible.
  useEffect(() => {
    if (!s?.authenticated || s.user?.must_change) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void api.request<{unread: number}>('/notices/unread').then(v => setUnread(v.unread)).catch(() => {});
    }, 60000);
    return () => window.clearInterval(timer);
  }, [s?.authenticated, s?.user?.must_change]);
  // Ficha de detalle: se consulta con los permisos actuales en cada cambio de ruta o recarga.
  // Al recargar la misma ficha se conserva la version visible hasta recibir la nueva (sin parpadeo).
  const shownDetail = useRef('');
  useEffect(() => {
    let live = true;
    const {kind, id} = parseRoute(route);
    if (shownDetail.current !== route) {setDetail(null); shownDetail.current = route;}
    if (s?.authenticated && !s.user?.must_change && DETAIL_KINDS.includes(kind) && id) {
      void api.request(`/detail/${kind}/${id}`).then(d => {if (live) setDetail(d);}).catch(e => {if (live) setError((e as Error).message);});
    }
    return () => {live = false;};
  }, [route, tick, s?.authenticated, s?.user?.must_change]);
  // Cada pantalla nueva comienza arriba y el foco pasa al contenido (lectores de pantalla y teclado).
  const page = route.split('?')[0];
  useEffect(() => {
    setAction(null);
    window.scrollTo({top: 0});
    document.getElementById('contenido')?.focus({preventScroll: true});
  }, [page]);
  // Ctrl+K (o Cmd+K) abre la paleta para ir a una pantalla o buscar un folio.
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k' && !document.querySelector('dialog[open]')) {e.preventDefault(); setPalette(true);}
    };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, []);

  function reload() {setTick(v => v + 1);}
  /** Las pantallas con cambios sin guardar pueden cancelar este evento. */
  function allowLeave() {return window.dispatchEvent(new Event('siger-before-navigation', {cancelable: true}));}
  function go(kind: string, id: number) {location.hash = `${kind}/${id}`;}
  function completed(r: Result, source?: Action) {
    const type = source?.type;
    if (type === 'COM_ROUTE') {setMessage('Distribución completada. La SOL fue enviada a la jefatura destinataria.'); reload(); return;}
    if (type && SOL_MESSAGES[type]) {setMessage(SOL_MESSAGES[type]); location.hash = 'sol'; reload(); return;}
    setMessage(r.message);
    reload();
    if (DETAIL_KINDS.includes(r.kind)) go(r.kind, r.id);
  }
  async function logout() {
    if (!allowLeave()) return;
    try {
      await api.request('/logout', {method: 'POST'});
      setS(null);
      setAction(null);
      setDetail(null);
      location.hash = 'inicio';
      reload();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  if (loading && !s) return <Splash error={error}/>;
  if (!s?.authenticated || !s.user) return <Login error={error} done={() => {setError(''); reload();}}/>;
  const user = s.user;
  if (user.must_change) return <PasswordChange user={user} done={() => {setS(null); reload();}}/>;

  const {view, filter, query, isDetail, kind} = parseRoute(route);
  const ctx: Context = {user, catalog, act: setAction, go, reload, message: setMessage};
  const groups = menuFor(user, unread);
  let content;
  if (isDetail) content = detail ? <Detail kind={kind} row={detail} ctx={ctx}/> : error ? null : <div className="detail-loading" role="status" aria-label="Cargando ficha"><Skeleton title lines={2}/><span className="skeleton block"/><Skeleton lines={4}/></div>;
  else if (BOARDS.includes(view)) content = <WorkspaceList key={view} kind={view} initialFilter={filter} initialQuery={query} ctx={ctx} version={tick}/>;
  else switch (view) {
    case 'atencion': content = <Attention ctx={ctx} completed={r => completed(r)}/>; break;
    case 'nuevo': content = <Intake ctx={ctx} completed={r => completed(r)}/>; break;
    case 'subrogancias': content = <Substitutions ctx={ctx} workspace={workspace}/>; break;
    case 'administracion': case 'auditoria': content = <Administration key={view + tick} ctx={ctx} auditOnly={view === 'auditoria'}/>; break;
    default: content = <Dashboard ctx={ctx} version={tick}/>;
  }

  return <Shell groups={groups} view={view} user={user} message={message} clearMessage={() => setMessage('')}
    refresh={() => {if (allowLeave()) {setError(''); reload();}}} logout={() => void logout()} palette={() => setPalette(true)}>
    {error && <ErrorMessage>{isDetail ? 'No fue posible abrir la ficha. ' + error : error}</ErrorMessage>}
    <div className="page" key={isDetail ? route : view}>{content}</div>
    {action && <ActionDialog key={action.type + ':' + action.id} action={action} close={() => setAction(null)} completed={r => completed(r, action)}/>}
    {palette && <CommandPalette groups={groups} close={() => setPalette(false)}/>}
  </Shell>;
}
