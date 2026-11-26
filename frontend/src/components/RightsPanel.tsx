import {useState} from 'react';
import type {ReactNode} from 'react';
import type {Row} from '../lib/rows';
import {bool, list, number, text, when} from '../lib/rows';
import type {Context} from '../app/context';
import type {Field} from './ActionDialog';
import {ChipRadios} from './Choice';
import {Icon} from './Icon';
import {Callout, EmptyState, Panel} from './Primitives';
import type {CatalogItem, Special, StatList} from '../features/detail/characterizationModel.ts';
import {specials, statLists} from '../features/detail/characterizationModel.ts';
import type {Changes, RightsVar} from '../lib/rightsModel.ts';
import {children, consolidatedRows, PENDING, rightsPayload, rightsProblems, rightsVars, searchCodes, selection, toggle, withStatus} from '../lib/rightsModel.ts';

/** Ayuda fija de los grupos prioritarios (GO de E09): sin seleccion automatica ni sugerida. */
export const PRIORITY_HELP = 'Marca un grupo solo con antecedentes pertinentes. Pertenecer a un grupo no constituye por sí mismo una vulneración.';
const PRIORITY = 'GRUPO_PRIORITARIO';
/** Ancla del panel en la ficha, para llegar desde "Registrar actuacion" y desde la primera atencion. */
export const rightsAnchor = (kind: string) => 'derechos-' + kind.toLowerCase();

const stateText = (v: {state: string}, special: Special[]) => v.state === PENDING ? 'Pendiente' : special.find(s => s.code === v.state)?.label ?? '';
const ORIGIN_TEXT: Record<string, string> = {RETROSPECTIVA: ' · después de una reapertura'};

/** Arbol de un listado (eje -> derecho -> subcategoria), filtrado por la busqueda. */
function Tree({items, parent, visible, selected, onToggle, disabled}: {items: Map<string, CatalogItem[]>; parent: string; visible: Set<string> | null; selected: Set<string>; onToggle: (code: string, on: boolean) => void; disabled?: boolean}) {
  const nodes = (items.get(parent) ?? []).filter(i => !visible || visible.has(i.code));
  if (!nodes.length) return null;
  return <ul className="rights-tree">
    {nodes.map(i => {
      const sub = <Tree items={items} parent={i.code} visible={visible} selected={selected} onToggle={onToggle} disabled={disabled}/>;
      const node: ReactNode = i.selectable
        ? <label className="check rights-item"><input type="checkbox" disabled={disabled} checked={selected.has(i.code)} onChange={e => onToggle(i.code, e.target.checked)}/>
          <span>{i.label}{i.description && <small className="field-help">{i.description}</small>}</span></label>
        : <span className="rights-group">{i.label}{i.description && <small className="field-help">{i.description}</small>}</span>;
      const hasChildren = (items.get(i.code) ?? []).length > 0;
      // Sin busqueda, cada rama de primer nivel se abre al pedirla (o si ya tiene algo marcado); con busqueda, todo abierto.
      if (hasChildren && !visible && !i.parent) {
        const marked = [...selected].some(c => within(items, i.code, c));
        return <li key={i.code}><details open={marked}><summary>{node}</summary>{sub}</details></li>;
      }
      return <li key={i.code}>{node}{sub}</li>;
    })}
  </ul>;
}
function within(items: Map<string, CatalogItem[]>, root: string, code: string): boolean {
  return (items.get(root) ?? []).some(k => k.code === code || within(items, k.code, code));
}

/**
 * Editor de la clasificacion (dentro del dialogo de RIGHTS_CLASSIFY): elige la variable, busca sin tildes, navega el
 * arbol y marca o quita valores; un estado especial solo sin valores. Guarda en el dialogo el cambio listo para enviar.
 */
export function RightsEditor({vars, lists, special, value, onChange, disabled, initialQuery = '', initialVariable}: {
  vars: RightsVar[]; lists: Record<string, StatList>; special: Special[]; value: Changes | undefined; onChange: (v: Changes | undefined) => void;
  disabled?: boolean; initialQuery?: string; initialVariable?: string;
}) {
  const editable = vars.filter(v => v.catalog_current);
  const [variable, setVariable] = useState(initialVariable ?? editable[0]?.variable ?? '');
  const [query, setQuery] = useState(initialQuery);
  const v = editable.find(x => x.variable === variable);
  if (!v) return <Callout tone="neutral">No hay listados vigentes para clasificar.</Callout>;
  const items = lists[v.variable]?.items ?? [];
  const current = v.values.map(x => x.code);
  const change = value?.[v.variable];
  const selected = selection(current, change);
  const set = (next: Changes) => onChange(rightsPayload(vars, next));
  const status = change?.status ?? (selected.size === 0 && v.state !== PENDING && v.state !== 'INFORMADO' ? v.state : '');
  const visible = searchCodes(items, query);
  return <div className="rights-editor">
    <ChipRadios legend="Qué clasificas" value={v.variable} disabled={disabled} onChange={x => {setVariable(x);setQuery('');}}
      options={editable.map(x => ({value: x.variable, label: x.label + (selection(x.values.map(y => y.code), value?.[x.variable]).size ? ` (${selection(x.values.map(y => y.code), value?.[x.variable]).size})` : '')}))}/>
    {v.variable === PRIORITY && <Callout tone="info">{PRIORITY_HELP}</Callout>}
    <label className="rights-search"><span>Buscar en {v.label.toLocaleLowerCase('es-CL')}</span>
      <input type="search" value={query} disabled={disabled} autoComplete="off" placeholder="Escribe una palabra, por ejemplo: educación" onChange={e => setQuery(e.target.value)}/>
    </label>
    {visible && visible.size === 0 && <p className="muted">Sin coincidencias. Prueba con otra palabra.</p>}
    <Tree items={children(items)} parent="" visible={visible} selected={selected} disabled={disabled}
      onToggle={(code, on) => set({...value, [v.variable]: toggle(items, current, change, code, on)})}/>
    <label><span>Sin un valor del listado</span>
      <select value={status} disabled={disabled} onChange={e => set({...value, [v.variable]: withStatus(current, e.target.value)})}>
        <option value="">{selected.size ? 'No aplica: hay valores marcados' : 'Elegir si no hay un valor que marcar'}</option>
        {special.map(s => <option key={s.code} value={s.code}>{s.label}</option>)}
      </select>
      <small>Por ejemplo, «Sin información». Elegirlo quita los valores marcados de esta lista.</small>
    </label>
  </div>;
}

/** Dialogo de RIGHTS_CLASSIFY: registro (REQ o CAS), cambios por variable y motivo (obligatorio al quitar o reemplazar). */
export function rightsFields(ctx: Context, kind: string, rights: Row, initialVariable?: string): Field[] {
  const vars = rightsVars(rights), lists = statLists(ctx.catalog), special = specials(ctx.catalog);
  return [
    {key: 'record', label: 'Registro', type: 'custom', initial: kind, render: () => null},
    {key: 'variables', label: 'Clasificación', type: 'custom',
      render: (value, set, disabled) => <RightsEditor vars={vars} lists={lists} special={special} value={value as Changes | undefined} onChange={set} disabled={disabled} initialVariable={initialVariable}/>,
      validate: (value, data) => rightsProblems(vars, value as Changes | undefined, text(data, 'reason'))[0] ?? null},
    {key: 'reason', label: 'Motivo de la corrección (obligatorio al quitar un valor o reemplazar un estado)', type: 'textarea', max: 2000},
  ];
}

/**
 * Derechos, contexto, caracterizacion, rol del Estado y grupos prioritarios de un REQ o un Caso (0.8.0, D34, gate E09;
 * DISENO 10.2). Resumen en la ficha y clasificacion en un dialogo amplio con busqueda. Solo lectura sin facultad;
 * con el registro cerrado, "Requiere reapertura formal". En el REQ, el consolidado con los Casos que la cuenta puede leer.
 */
export function RightsPanel({rights, kind, id, ctx}: {rights: Row; kind: 'REQ' | 'CAS'; id: number; ctx: Context}) {
  const vars = rightsVars(rights), special = specials(ctx.catalog);
  if (!vars.length) return null;
  const blocked = text(rights, 'blocked');
  const open = (variable?: string) => ctx.act({title: kind === 'REQ' ? 'Clasificar derechos y contexto del REQ' : 'Clasificar derechos y contexto del Caso', type: 'RIGHTS_CLASSIFY', id,
    revision: number(rights, 'revision'), wide: true, fields: rightsFields(ctx, kind, rights, variable),
    note: 'Marca solo lo que consta en los antecedentes. Quitar un valor o reemplazar un estado pide un motivo; todo queda en el historial.'});
  const editable = bool(rights, 'can_classify') && vars.some(v => v.catalog_current);
  const history = vars.flatMap(v => list(list(rights, 'variables').find(x => text(x, 'variable') === v.variable) ?? {}, 'history').map(h => ({...h, var: v.label}) as Row));
  return <Panel id={rightsAnchor(kind)} title="Derechos y contexto" icon="shield"
    description={kind === 'REQ' ? 'Clasificación del requerimiento. Un mismo valor en el REQ y en sus Casos se cuenta una vez.' : 'Clasificación de este Caso; se suma al requerimiento sin duplicar.'}
    actions={editable ? <button className="sm" onClick={() => open()}><Icon name="edit" size={16}/>Clasificar</button> : undefined}>
    {blocked && <Callout tone="neutral" title="Requiere reapertura formal">{blocked}</Callout>}
    <dl className="stat-list">
      {vars.map(v => <div key={v.variable}>
        <dt>{v.label}</dt>
        <dd>
          {v.values.length ? <ul className="chips">{v.values.map(x => <li key={x.code} className="chip">{x.label || 'Valor de un listado anterior'}</li>)}</ul>
            : <span className={'stat-value' + (v.state === PENDING ? ' pending' : '')}>{stateText(v, special)}</span>}
          {v.variable === PRIORITY && <small className="muted">{PRIORITY_HELP}</small>}
          {!v.catalog_current && <small className="muted">Sin listado vigente: no se clasifica por ahora.</small>}
        </dd>
      </div>)}
    </dl>
    {kind === 'REQ' && <Consolidated rights={rights} vars={vars}/>}
    {history.length > 0 && <details className="disclosure compact">
      <summary><Icon name="history" size={16}/>Historial <small className="muted">({history.length})</small></summary>
      <ol className="timeline compact">{history.map(h => <li key={number(h, 'id')}>
        <div className="timeline-head"><strong>{text(h, 'var')}</strong><span className="timeline-meta">{when(h.recorded_ms)} · {text(h, 'author')}</span></div>
        <p className="timeline-body">{text(h, 'event') === 'REMOVE' ? 'Quitado: ' : text(h, 'event') === 'ADD' ? 'Agregado: ' : 'Estado: '}
          {text(h, 'event') === 'STATUS' ? stateText({state: text(h, 'status')}, special) : text(h, 'value_label') || 'Valor de un listado anterior'}{ORIGIN_TEXT[text(h, 'origin')] ?? ''}</p>
        {text(h, 'basis') && <p className="timeline-meta preserve">Motivo: {text(h, 'basis')}</p>}
      </li>)}</ol>
    </details>}
  </Panel>;
}

/** Consolidado del REQ y de sus Casos legibles (el servidor ya excluye los Casos que la cuenta no lee). */
function Consolidated({rights, vars}: {rights: Row; vars: RightsVar[]}) {
  const rows = vars.map(v => ({v, rows: consolidatedRows(rights, v.variable)})).filter(x => x.rows.length);
  if (!rows.length) return <EmptyState compact icon="layers" title="Sin valores en el requerimiento ni en sus Casos visibles"/>;
  return <details className="disclosure compact">
    <summary><Icon name="layers" size={16}/>Requerimiento con sus Casos</summary>
    {rows.map(({v, rows: r}) => <div key={v.variable} className="consolidated">
      <strong>{v.label}</strong>
      <ul>{r.map(x => <li key={x.code}>{x.label || 'Valor de un listado anterior'} <small className="muted">{[x.in_req ? 'en el REQ' : '', ...x.cases.map(f => 'en ' + f)].filter(Boolean).join(' · ')}</small></li>)}</ul>
    </div>)}
  </details>;
}
