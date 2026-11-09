import {useEffect, useRef, useState} from 'react';
import type {ReactNode} from 'react';
import type {User} from '../lib/access';
import {initials, ROLE_LABELS} from '../lib/labels';
import {Icon} from '../components/Icon';
import {useExit} from '../components/useExit';
import type {MenuGroup} from './menu';
import emblem from '../assets/emblema-320.png';

/** Bandeja que corresponde a cada ficha, para mantener la orientacion en la navegacion. */
const DETAIL_BOARD: Record<string, string> = {COM: 'com', SOL: 'sol', REQ: 'req', CAS: 'cas', FLUJO: 'flujos'};

export const activePath = (view: string) => DETAIL_BOARD[view.split('/')[0]] ?? view;

export const rolesOf = (user: User) => [...new Set(user.grants.map(g => ROLE_LABELS[g.role] ?? g.role))].join(' · ') || 'Sin funciones vigentes';

export function Brand() {
  return <a className="brand" href="#inicio" aria-label="SIGER A, ir al inicio">
    <img src={emblem} alt="" width={42} height={41}/>
    <span className="brand-name"><strong>SIGER A</strong><span>Defensoría de la Niñez</span></span>
  </a>;
}

export const Thread = () => <div className="brand-thread" aria-hidden="true"><span/><span/><span/><span/><span/><span/></div>;

type SidebarProps = {groups: MenuGroup[]; view: string; user: User; refresh: () => void; logout: () => void; palette: () => void; navigate?: () => void};

function Sidebar({groups, view, user, refresh, logout, palette, navigate}: SidebarProps) {
  const current = activePath(view);
  return <div className="sidebar">
    <Brand/>
    <Thread/>
    <button className="palette-trigger" onClick={palette}><Icon name="search" size={18}/>Buscar o ir a…<kbd>Ctrl K</kbd></button>
    <nav className="nav" aria-label="Navegación principal">
      {groups.map(g => <div className="nav-group" key={g.title}>
        <p className="nav-group-title">{g.title}</p>
        {g.items.map(item => <a key={item.path} href={'#' + item.path} aria-current={current === item.path ? 'page' : undefined} title={item.hint} onClick={navigate}>
          <Icon name={item.icon}/>
          <span>{item.label}</span>
          {item.badge ? <span className="nav-badge" aria-label={item.badge + ' sin leer'}>{item.badge}</span> : null}
        </a>)}
      </div>)}
    </nav>
    <div className="user-card">
      <div className="user-card-head">
        <span className="avatar" aria-hidden="true">{initials(user.name)}</span>
        <span className="user-card-name"><strong>{user.name}</strong><span title={rolesOf(user)}>{rolesOf(user)}</span></span>
      </div>
      <div className="user-card-actions">
        <button className="sm" onClick={refresh} title="Volver a consultar tus datos y permisos"><Icon name="refresh" size={16}/>Actualizar</button>
        <button className="sm" onClick={logout}><Icon name="logout" size={16}/>Salir</button>
      </div>
      <p className="app-meta">Fechas en hora de Chile continental (America/Santiago). Versión 0.4.0.</p>
    </div>
  </div>;
}

/** Cajon lateral en pantallas angostas: dialogo modal nativo (foco atrapado y cierre con Esc). */
function Drawer({close, ...props}: SidebarProps & {close: () => void}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close, 200);
  useEffect(() => {ref.current?.showModal();}, []);
  return <dialog ref={ref} className={'drawer' + (leaving ? ' closing' : '')} aria-label="Menú" onCancel={e => {e.preventDefault(); exit();}}
    onClick={e => {if (e.target === ref.current) exit();}}>
    <button className="ghost icon-only drawer-close" aria-label="Cerrar menú" onClick={exit}><Icon name="close"/></button>
    <Sidebar {...props} navigate={exit} refresh={() => {exit(); props.refresh();}} palette={() => {exit(); props.palette();}}/>
  </dialog>;
}

/** Barra fina de progreso cuando una consulta tarda mas de un instante. */
function Progress() {
  const [active, setActive] = useState(false);
  useEffect(() => {
    let timer = 0;
    const changed = (e: Event) => {
      const count = (e as CustomEvent<number>).detail;
      window.clearTimeout(timer);
      if (count > 0) timer = window.setTimeout(() => setActive(true), 180);
      else setActive(false);
    };
    window.addEventListener('siger-busy', changed);
    return () => {window.removeEventListener('siger-busy', changed); window.clearTimeout(timer);};
  }, []);
  return <div className={'progress' + (active ? ' active' : '')} aria-hidden="true"/>;
}

/** Aviso de resultado: se anuncia a lectores de pantalla y se retira solo, salvo con el puntero o el foco encima. */
export function Toast({message, close}: {message: string; close: () => void}) {
  const [leaving, exit] = useExit(close, 200);
  const hold = useRef(false);
  useEffect(() => {
    const timer = window.setInterval(() => {if (!hold.current) exit();}, 9000);
    return () => window.clearInterval(timer);
  }, [exit]);
  return <div className={'toast' + (leaving ? ' leaving' : '')} onMouseEnter={() => {hold.current = true;}} onMouseLeave={() => {hold.current = false;}}
    onFocus={() => {hold.current = true;}} onBlur={() => {hold.current = false;}}>
    <Icon name="checkCircle"/>
    <p>{message}</p>
    <button className="ghost icon-only sm" aria-label="Cerrar aviso" onClick={exit}><Icon name="close" size={18}/></button>
  </div>;
}

export function Shell({groups, view, user, refresh, logout, palette, message, clearMessage, children}: SidebarProps & {message: string; clearMessage: () => void; children: ReactNode}) {
  const [drawer, setDrawer] = useState(false);
  // Cualquier cambio de pantalla (enlace, Atras del navegador, paleta) cierra el cajon movil.
  useEffect(() => {setDrawer(false);}, [view]);
  const unread = groups.flatMap(g => g.items).find(i => i.path === 'avisos')?.badge;
  return <>
    <a className="skip-link" href="#contenido" onClick={e => {e.preventDefault(); document.getElementById('contenido')?.focus();}}>Saltar al contenido</a>
    <Progress/>
    <div className="environment" role="note"><span className="environment-dot" aria-hidden="true"/>Candidata de pruebas · Datos sintéticos<span className="hide-narrow"> · Sin recepción pública ni correo real</span></div>
    <div className="app">
      <aside className="sidebar-slot" aria-label="Menú lateral"><Sidebar {...{groups, view, user, refresh, logout, palette}}/></aside>
      <div className="workspace">
        <header className="topbar">
          <button className="ghost icon-only" aria-label="Abrir menú" aria-haspopup="dialog" onClick={() => setDrawer(true)}><Icon name="menu"/></button>
          <Brand/>
          <button className="ghost icon-only" aria-label="Buscar o ir a" onClick={palette}><Icon name="search"/></button>
          <a className="button ghost icon-only topbar-badge" href="#avisos" aria-label={unread ? `Mis avisos, ${unread} sin leer` : 'Mis avisos'}>
            <Icon name="bell"/>{unread ? <span className="nav-badge" aria-hidden="true">{unread}</span> : null}
          </a>
        </header>
        <main id="contenido" tabIndex={-1}>{children}</main>
      </div>
    </div>
    {drawer && <Drawer {...{groups, view, user, refresh, logout, palette}} close={() => setDrawer(false)}/>}
    <div className="toasts" aria-live="polite" role="status">{message && <Toast key={message} message={message} close={clearMessage}/>}</div>
  </>;
}
