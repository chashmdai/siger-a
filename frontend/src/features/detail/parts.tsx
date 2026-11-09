import type {ReactNode} from 'react';
import type {Row} from '../../lib/rows';
import {list, number, obj, text} from '../../lib/rows';
import {FLOW_LABELS, humanize, KIND_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Kind, Status} from '../../components/Status';
import {Icon} from '../../components/Icon';

/** Grupo de acciones disponibles para la ficha; se oculta si el servidor no habilita ninguna. */
export function ActionBar({label = 'Acciones', children}: {label?: string; children: ReactNode}) {
  return <div className="action-bar" role="group" aria-label={label}>
    <span className="action-bar-label">{label}</span>
    {children}
  </div>;
}

/** Disposicion de ficha: contenido principal y columna de datos clave. */
export function DetailGrid({main, side}: {main: ReactNode; side: ReactNode}) {
  return <div className="detail-grid">
    <div className="detail-main">{main}</div>
    <aside className="detail-side" aria-label="Datos clave">{side}</aside>
  </div>;
}

type Step = {kind: string; label: string; target?: [string, number]; note?: string; skipped?: boolean};

/** Cadena COM -> SOL -> REQ -> Caso: ubica la ficha en el recorrido institucional. */
function chainFor(kind: string, r: Row): Step[] | null {
  const steps: Step[] = [{kind: 'COM', label: 'Comunicación'}, {kind: 'SOL', label: 'Solicitud'}, {kind: 'REQ', label: 'Requerimiento'}, {kind: 'CAS', label: 'Caso'}];
  const set = (k: string, patch: Partial<Step>) => Object.assign(steps.find(s => s.kind === k)!, patch);
  switch (kind) {
    case 'COM': {
      const sol = list(r, 'solicitations')[0], req = list(r, 'requirements')[0];
      if (sol) set('SOL', {target: ['SOL', number(sol, 'id')], note: 'SOL ' + number(sol, 'id')});
      if (req) set('REQ', {target: ['REQ', number(req, 'id')], note: text(req, 'folio')});
      return steps;
    }
    case 'SOL':
      set('COM', {target: ['COM', number(r, 'communication_id')], note: text(obj(r, 'communication'), 'folio') || undefined});
      return steps;
    case 'REQ':
      if (r.can_read_com) set('COM', {target: ['COM', number(r, 'communication_id')]});
      if (Object.keys(obj(r, 'attention')).length > 0) set('SOL', {note: 'No aplica: atención directa', skipped: true});
      set('CAS', {note: number(r, 'total_cases') ? `${number(r, 'total_cases')} en este REQ` : 'Sin Casos'});
      return steps;
    case 'CAS':
      set('REQ', {target: ['REQ', number(r, 'requirement_id')], note: text(r, 'req_folio')});
      return steps;
    default:
      return null;
  }
}

function Chain({kind, r, ctx}: {kind: string; r: Row; ctx: Context}) {
  const steps = chainFor(kind, r);
  if (!steps) return null;
  const at = steps.findIndex(s => s.kind === kind);
  return <ol className="chain" aria-label="Ubicación en el recorrido">
    {steps.map((s, i) => {
      const content = <><span className="chain-dot" aria-hidden="true"/><span className="chain-label">{s.label}</span>{s.note && <span className="chain-note">{s.note}</span>}</>;
      return <li key={s.kind} className={(i === at ? 'current' : i < at ? 'before' : 'after') + (s.skipped ? ' skipped' : '')} aria-current={i === at ? 'step' : undefined}>
        {s.target && i !== at ? <button className="chain-link" onClick={() => ctx.go(s.target![0], s.target![1])}>{content}</button> : <span className="chain-static">{content}</span>}
      </li>;
    })}
  </ol>;
}

function subtitle(kind: string, r: Row) {
  switch (kind) {
    case 'COM': return text(r, 'subject');
    case 'SOL': return text(obj(r, 'communication'), 'subject');
    case 'CAS': return text(r, 'objective');
    case 'FLUJO': return humanize(r.kind, FLOW_LABELS) + (text(r, 'req_folio') ? ' · ' + text(r, 'req_folio') : '');
    default: return '';
  }
}

export function DetailHeader({kind, r, ctx}: {kind: string; r: Row; ctx: Context}) {
  const sub = subtitle(kind, r);
  return <header className="detail-head">
    <button className="ghost sm back" onClick={() => history.back()}><Icon name="arrowLeft" size={16}/>Volver</button>
    <div className="detail-title">
      <Kind kind={kind}/>
      <h1 className="folio">{text(r, 'folio') || (KIND_LABELS[kind] ?? kind) + ' ' + number(r, 'id')}</h1>
      <Status value={r.state}/>
    </div>
    {sub && <p className="detail-subtitle preserve">{sub}</p>}
    <Chain kind={kind} r={r} ctx={ctx}/>
  </header>;
}
