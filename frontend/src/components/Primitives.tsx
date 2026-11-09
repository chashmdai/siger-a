import type {ReactNode} from 'react';
import {Icon} from './Icon';
import type {IconName} from './Icon';

/** Encabezado de pantalla: contexto, titulo, explicacion breve y acciones propias. */
export function PageHeader({eyebrow, title, lead, aside}: {eyebrow?: ReactNode; title: ReactNode; lead?: ReactNode; aside?: ReactNode}) {
  return <header className="page-header">
    <div className="page-header-text">
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <h1>{title}</h1>
      {lead && <p className="lead">{lead}</p>}
    </div>
    {aside && <div className="page-header-aside">{aside}</div>}
  </header>;
}

/** Bloque con titulo; "flush" deja el contenido a ras (listas y tablas). */
export function Panel({title, icon, count, description, actions, children, className = '', flush, id}: {
  title?: ReactNode; icon?: IconName; count?: number; description?: ReactNode; actions?: ReactNode; children?: ReactNode; className?: string; flush?: boolean; id?: string;
}) {
  return <section className={'panel ' + (flush ? 'flush ' : '') + className} id={id}>
    {(title || actions) && <div className="panel-head">
      <div>
        {title && <h2>{icon && <Icon name={icon}/>}{title}{count != null && <span className="panel-count">{count}</span>}</h2>}
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="actions panel-actions">{actions}</div>}
    </div>}
    {children}
  </section>;
}

const CALLOUT_ICONS: Record<string, IconName> = {info: 'info', warn: 'alert', ok: 'checkCircle', neutral: 'info'};

/** Orientacion contextual. "warn" para condiciones que bloquean o requieren atencion. */
export function Callout({tone = 'info', title, children, role}: {tone?: 'info' | 'warn' | 'ok' | 'neutral'; title?: ReactNode; children?: ReactNode; role?: 'status' | 'alert'}) {
  return <div className={'callout ' + tone} role={role}>
    <Icon name={CALLOUT_ICONS[tone]}/>
    <div>{title && <p><strong>{title}</strong></p>}{children}</div>
  </div>;
}

export function ErrorMessage({children}: {children: ReactNode}) {
  return <p role="alert" className="error"><Icon name="alert"/><span>{children}</span></p>;
}

/** Estado vacio con una explicacion de por que no hay datos y, si corresponde, que hacer. */
export function EmptyState({icon = 'inbox', title, children, actions, compact}: {icon?: IconName; title: ReactNode; children?: ReactNode; actions?: ReactNode; compact?: boolean}) {
  return <div className={'empty-state' + (compact ? ' compact' : '')}>
    <span className="empty-icon"><Icon name={icon} size={compact ? 20 : 24}/></span>
    <div className="empty-text">
      <strong>{title}</strong>
      {children && <p>{children}</p>}
      {actions && <div className="actions">{actions}</div>}
    </div>
  </div>;
}

export function Skeleton({lines = 3, title}: {lines?: number; title?: boolean}) {
  return <div aria-hidden="true">
    {title && <span className="skeleton title"/>}
    {Array.from({length: lines}, (_, i) => <span key={i} className="skeleton line" style={{width: `${92 - (i * 17) % 40}%`}}/>)}
  </div>;
}

/** Datos clave en pares etiqueta/valor. Un valor vacio se muestra como "Sin informar". */
export function Facts({items, stacked}: {items: [label: string, value: ReactNode, wide?: boolean][]; stacked?: boolean}) {
  return <dl className={'facts' + (stacked ? ' stacked' : '')}>
    {items.map(([label, value, wide]) => {
      const empty = value == null || value === '' || value === '-';
      return <div key={label} className={wide ? 'wide' : undefined}><dt>{label}</dt><dd className={empty ? 'empty' : undefined}>{empty ? 'Sin informar' : value}</dd></div>;
    })}
  </dl>;
}

/** Texto de un boton mientras la operacion esta en curso. */
export function Busy({busy, label, idle}: {busy: boolean; label: string; idle: ReactNode}) {
  return busy ? <><span className="spinner" aria-hidden="true"/>{label}</> : <>{idle}</>;
}
