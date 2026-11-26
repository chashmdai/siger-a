import {useState} from 'react';
import type {DragEvent} from 'react';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {Callout, ErrorMessage, Panel, Skeleton} from '../../components/Primitives';
import type {Axis} from './FieldPicker';
import {FieldPicker} from './FieldPicker';
import type {Query, Result, StatsCatalog} from './statsModel';
import {BASIS_LABELS, chileDay, COMPARE_LABELS, dimOf, encodeRoute, fieldLabel, filterValues, GUIDED, guidedQuery, isMultiple, measureHelp, measureOf, MODE_HELP,
  MODE_LABELS, multipleIn, panoramaPeriod, PERCENT_LABELS, PERIOD_LABELS, periodStart, queryProblem, readResult, requestBody, RIGHTS_LEVELS, rightsTree, vigentItems} from './statsModel';
import {useStat} from './statQuery';
import {ResultView} from './ResultView';
import {SavedViews} from './SavedViews';

const fold = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('es-CL');
const AXIS_LABELS: Record<Axis, string> = {rows: 'Filas', columns: 'Columnas', filters: 'Filtros'};
const COUNTS = ['REQUERIMIENTOS_CON_VALOR', 'MENCIONES'];

/** Agrega un campo a filas, columnas o filtros (sale de los otros ejes; un filtro conserva sus valores). */
export function place(q: Query, axis: Axis, code: string): Query {
  const rows = q.rows.filter(c => c !== code), columns = q.columns.filter(c => c !== code);
  if (axis === 'filters') return {...q, rows, columns, filters: {...q.filters, [code]: q.filters[code] ?? []}};
  const filters = {...q.filters};
  return axis === 'rows' ? {...q, rows: [...rows, code], columns, filters} : {...q, rows, columns: [...columns, code], filters};
}
/** Mueve un campo dentro de su eje (teclado: subir y bajar). */
export function move(list: string[], code: string, delta: number) {
  const i = list.indexOf(code), j = i + delta;
  if (i < 0 || j < 0 || j >= list.length) return list;
  const out = [...list];
  [out[i], out[j]] = [out[j], out[i]];
  return out;
}
/** Medida coherente con los campos: con un campo de valores multiples, "con el valor" o "menciones"; sin el, ingresos. */
export function withMeasure(q: Query, measure: string): Query {
  const next = {...q, measure};
  if (q.percent === 'FILA' && next.rows.length === 0) next.percent = '';
  if (q.percent === 'COLUMNA' && next.columns.length === 0) next.percent = '';
  return next;
}

function FilterEditor({cat, catalog, code, values, set, close}: {cat: StatsCatalog; catalog: Context['catalog']; code: string; values: string[]; set: (v: string[]) => void; close: () => void}) {
  const [term, setTerm] = useState('');
  const all = filterValues(cat, catalog, code);
  const shown = all.filter(o => !term || fold(o.label).includes(fold(term))).slice(0, 200);
  const on = new Set(values);
  return <div className="stat-filter-editor" role="group" aria-label={'Valores de ' + fieldLabel(cat, code)}>
    {all.length > 12 && <label className="input-icon"><span className="sr-only">Buscar un valor</span><Icon name="search" size={18}/>
      <input type="search" placeholder="Buscar un valor" value={term} autoComplete="off" onChange={e => setTerm(e.target.value)}/></label>}
    <ul>{shown.map(o => <li key={o.code}><label className="check"><input type="checkbox" checked={on.has(o.code)}
      onChange={e => set(e.target.checked ? [...values, o.code] : values.filter(v => v !== o.code))}/><span>{o.label}</span></label></li>)}</ul>
    {shown.length < all.filter(o => !term || fold(o.label).includes(fold(term))).length && <p className="field-help">Escribe para ver más valores.</p>}
    <div className="actions"><button type="button" className="sm" onClick={close}>Listo</button>{values.length > 0 && <button type="button" className="sm ghost" onClick={() => set([])}>Quitar valores</button>}</div>
  </div>;
}

/**
 * Explorar (DISENO 10.3): preguntas guiadas, campos por familia, filas y columnas por boton o arrastre, filtros como chips,
 * medida en lenguaje llano, selector de cifra, opciones avanzadas (fecha de analisis, modo temporal, comparacion y
 * propuestas), resultado con grafico y tabla, "Como leer esta cifra" y vistas guardadas. En derechos, una consulta por nivel.
 */
export default function Explore({ctx, cat, scope, query}: {ctx: Context; cat: StatsCatalog; scope: string; query: Query | null}) {
  const today = chileDay();
  const base = panoramaPeriod(today).period;
  const q = query;
  const [editing, setEditing] = useState(''), [over, setOver] = useState<Axis | ''>('');
  const go = (next: Query) => {location.hash = encodeRoute('explorar', next);};
  const problem = q ? queryProblem(cat, q) : '';
  const body = q && !problem ? requestBody(q, cat) : null;
  const main = useStat(body, ctx.user.id);
  // Jerarquia de derechos: una consulta por nivel, con los mismos filtros, medida y columnas.
  const rightsRow = q && q.rows.length === 1 && (RIGHTS_LEVELS as readonly string[]).includes(q.rows[0]) && vigentItems(ctx.catalog, 'DERECHO').length > 0 && !q.compare;
  const [flat, setFlat] = useState(false);
  const levelBody = (dim: string) => rightsRow && !flat && q && !problem && dim !== q.rows[0] ? requestBody({...q, rows: [dim]}, cat) : null;
  const eje = useStat(levelBody('DERECHO_EJE'), ctx.user.id), der = useStat(levelBody('DERECHO'), ctx.user.id), sub = useStat(levelBody('DERECHO_SUBCATEGORIA'), ctx.user.id);

  if (!q) return <section aria-label="Explorar">
    <h2 className="stat-section-title">¿Qué quieres saber?</h2>
    <div className="stat-guided">{GUIDED.map(g => <button key={g.key} type="button" className="stat-guided-item" onClick={() => {
      if (g.key === 'figura') location.hash = encodeRoute('informes');
      else {const next = guidedQuery(g.key, base); if (next) go(next);}
    }}><strong>{g.label}</strong><span>{g.help}</span></button>)}</div>
    <p className="field-help">También puedes partir de una consulta vacía y armarla con los campos.</p>
    <div className="actions"><button type="button" onClick={() => go({...guidedQuery('cuantos', base)!, rows: []})}><Icon name="plus" size={18}/>Consulta vacía</button></div>
    <SavedViews ctx={ctx} cat={cat} q={null}/>
  </section>;

  const add = (axis: Axis, code: string) => {
    go(place(q, axis, code));
    if (axis === 'filters') setEditing(code);
  };
  const drop = (axis: Axis) => (e: DragEvent) => {
    e.preventDefault();
    setOver('');
    const code = e.dataTransfer.getData('text/plain');
    if (dimOf(cat, code)) add(axis, code);
  };
  const zone = (axis: Axis) => ({onDragOver: (e: DragEvent) => {e.preventDefault(); setOver(axis);}, onDragLeave: () => setOver(''), onDrop: drop(axis)});
  const removeFrom = (axis: Axis, code: string) => {
    if (axis === 'filters') {const f = {...q.filters}; delete f[code]; go({...q, filters: f}); return;}
    const next = withMeasure({...q, [axis]: q[axis].filter(c => c !== code)}, q.measure);
    go(COUNTS.includes(next.measure) && multipleIn(cat, next).length === 0 ? {...next, measure: 'INGRESOS', mention: ''} : next);
  };
  const axisChips = (axis: 'rows' | 'columns') => q[axis].map((code, i) => <li key={code} className="stat-chip">
    <span>{fieldLabel(cat, code)}{isMultiple(cat, code) && <small> · valores múltiples</small>}</span>
    <span className="stat-chip-actions">
      <button type="button" className="icon-only sm" disabled={i === 0} aria-label={`Subir ${fieldLabel(cat, code)}`} onClick={() => go({...q, [axis]: move(q[axis], code, -1)})}><Icon name="left" size={14}/></button>
      <button type="button" className="icon-only sm" disabled={i === q[axis].length - 1} aria-label={`Bajar ${fieldLabel(cat, code)}`} onClick={() => go({...q, [axis]: move(q[axis], code, 1)})}><Icon name="right" size={14}/></button>
      <button type="button" className="icon-only sm" aria-label={`Pasar ${fieldLabel(cat, code)} a ${axis === 'rows' ? 'columnas' : 'filas'}`} onClick={() => add(axis === 'rows' ? 'columns' : 'rows', code)}><Icon name="swap" size={14}/></button>
      <button type="button" className="icon-only sm" aria-label={`Quitar ${fieldLabel(cat, code)}`} onClick={() => removeFrom(axis, code)}><Icon name="close" size={14}/></button>
    </span>
  </li>);
  const m = measureOf(cat, q.measure);
  const multiple = multipleIn(cat, q);
  const counting = multiple.length > 0 && ['INGRESOS', ...COUNTS].includes(q.measure);
  const basisOptions = cat.date_basis.filter(b => b !== 'CREACION_CASO' || q.measure === 'CASOS');
  const r = main.status === 'ok' && main.data ? readResult(main.data) : null;
  const levels: Partial<Record<string, Result>> = {};
  if (rightsRow && !flat && r) {
    levels[q.rows[0]] = r;
    for (const [dim, s] of [['DERECHO_EJE', eje], ['DERECHO', der], ['DERECHO_SUBCATEGORIA', sub]] as const) if (dim !== q.rows[0] && s.status === 'ok' && s.data) levels[dim] = readResult(s.data);
  }
  const treeReady = rightsRow && !flat && RIGHTS_LEVELS.every(d => levels[d]);
  const tree = treeReady ? rightsTree(levels as Record<typeof RIGHTS_LEVELS[number], Result>, ctx.catalog) : null;
  const levelError = [eje, der, sub].find(s => s.status === 'error')?.error;

  return <section aria-label="Explorar" className="stat-explore">
    <FieldPicker cat={cat} q={q} add={add}/>
    <div className="stat-work">
      <Panel title="Consulta" icon="search">
        <div className="form-grid">
          <label className="span-all"><span>Qué contar</span>
            <select value={counting && COUNTS.includes(q.measure) ? 'INGRESOS' : q.measure} onChange={e => go(withMeasure(q, e.target.value))}>
              {cat.measures.filter(x => !COUNTS.includes(x.code)).map(x => <option key={x.code} value={x.code}>{x.label}</option>)}
            </select>
            <small>{measureHelp(q.measure) || m?.label}</small>
          </label>
          {counting && <fieldset className="span-all stat-count"><legend>Con valores múltiples, contar</legend>
            {[['INGRESOS', 'Requerimientos con el valor'], ['MENCIONES', 'Menciones']].map(([code, label]) => <label key={code} className="check">
              <input type="radio" name="contar" checked={code === 'MENCIONES' ? q.measure === 'MENCIONES' : q.measure !== 'MENCIONES'} onChange={() => go(withMeasure(q, code === 'INGRESOS' ? 'REQUERIMIENTOS_CON_VALOR' : 'MENCIONES'))}/>
              <span>{label}</span></label>)}
            <small>El total muestra los requerimientos únicos: cada uno cuenta una vez aunque tenga varios valores.</small>
          </fieldset>}
          {q.measure === 'MENCIONES' && multiple.length > 1 && <label><span>Menciones de</span>
            <select value={q.mention} onChange={e => go({...q, mention: e.target.value})}><option value="">Elige un campo</option>{multiple.map(c => <option key={c} value={c}>{fieldLabel(cat, c)}</option>)}</select></label>}
          <label><span>Cifra</span><select value={q.percent} onChange={e => go({...q, percent: e.target.value})}>
            {['', ...cat.percents].map(p => <option key={p} value={p} disabled={(p === 'FILA' && q.rows.length === 0) || (p === 'COLUMNA' && q.columns.length === 0)}>{PERCENT_LABELS[p] ?? p}</option>)}
          </select></label>
          <label><span>Periodo</span><select value={q.period.type} onChange={e => {
            const type = e.target.value;
            go({...q, period: type === 'PERSONALIZADO' ? {...q.period, type, to: q.period.to ?? today} : {type, from: periodStart(type, q.period.from), ...(q.period.basis ? {basis: q.period.basis} : {})}});
          }}>{cat.periods.map(p => <option key={p} value={p}>{PERIOD_LABELS[p] ?? p}</option>)}</select></label>
          <label><span>{q.period.type === 'PERSONALIZADO' ? 'Desde' : 'Periodo que contiene el día'}</span><input type="date" value={q.period.from} required onChange={e => {
            if (!e.target.value) return;
            go({...q, period: {...q.period, from: q.period.type === 'PERSONALIZADO' ? e.target.value : periodStart(q.period.type, e.target.value)}});
          }}/></label>
          {q.period.type === 'PERSONALIZADO' && <label><span>Hasta</span><input type="date" value={q.period.to ?? ''} min={q.period.from} required onChange={e => {if (e.target.value) go({...q, period: {...q.period, to: e.target.value}});}}/></label>}
          <label><span>Comparar con</span><select value={q.compare} onChange={e => go({...q, compare: e.target.value})}>
            <option value="">Sin comparación</option>
            {cat.compares.filter(c => c !== 'CORTE').map(c => <option key={c} value={c}>{COMPARE_LABELS[c] ?? c}</option>)}
          </select></label>
        </div>
        <details className="disclosure">
          <summary><Icon name="layers" size={18}/>Opciones avanzadas <small>{BASIS_LABELS[q.period.basis || m?.default_date_basis || 'RECEPCION']}; {MODE_LABELS[q.mode]}</small><Icon name="down" size={18}/></summary>
          <div className="disclosure-body form-grid">
            <label><span>Fecha de análisis</span><select value={q.period.basis || m?.default_date_basis || 'RECEPCION'} onChange={e => go({...q, period: {...q.period, basis: e.target.value}})}>
              {basisOptions.map(b => <option key={b} value={b}>{BASIS_LABELS[b] ?? b}</option>)}</select></label>
            <label><span>Valores</span><select value={q.mode} onChange={e => go({...q, mode: e.target.value})}>
              {cat.time_modes.map(t => <option key={t} value={t}>{MODE_LABELS[t] ?? t}</option>)}</select><small>{MODE_HELP[q.mode]}</small></label>
            {q.mode === 'FECHA' && <label><span>Fecha y hora (hora de Chile)</span><input type="datetime-local" value={q.asOf} max={today + 'T23:59'} onChange={e => go({...q, asOf: e.target.value})}/></label>}
            <label className="check span-all"><input type="checkbox" checked={q.proposals} onChange={e => go({...q, proposals: e.target.checked})}/>
              <span>Mostrar aparte las propuestas del Formulario pendientes de confirmar (nunca se suman a las cifras)</span></label>
          </div>
        </details>
        <div className="stat-axes">
          {(['rows', 'columns'] as const).map(axis => <div key={axis} className={'stat-axis' + (over === axis ? ' over' : '')} {...zone(axis)} aria-label={AXIS_LABELS[axis]} role="group">
            <h3>{AXIS_LABELS[axis]}</h3>
            {q[axis].length ? <ul>{axisChips(axis)}</ul> : <p className="field-help">Usa el botón «{AXIS_LABELS[axis]}» de un campo o arrástralo aquí.</p>}
          </div>)}
          <div className={'stat-axis' + (over === 'filters' ? ' over' : '')} {...zone('filters')} aria-label="Filtros" role="group">
            <h3>Filtros</h3>
            {Object.keys(q.filters).length ? <ul>{Object.entries(q.filters).map(([code, values]) => {
              const labels = filterValues(cat, ctx.catalog, code).filter(o => values.includes(o.code)).map(o => o.label);
              return <li key={code} className="stat-chip filter">
                <span>{fieldLabel(cat, code)}: {values.length === 0 ? 'elige valores' : labels.length <= 2 ? labels.join(', ') : `${labels.slice(0, 2).join(', ')} y ${labels.length - 2} más`}</span>
                <span className="stat-chip-actions">
                  <button type="button" className="sm ghost" aria-expanded={editing === code} onClick={() => setEditing(editing === code ? '' : code)}>Editar</button>
                  <button type="button" className="icon-only sm" aria-label={`Quitar el filtro ${fieldLabel(cat, code)}`} onClick={() => removeFrom('filters', code)}><Icon name="close" size={14}/></button>
                </span>
                {editing === code && <FilterEditor cat={cat} catalog={ctx.catalog} code={code} values={values} close={() => setEditing('')}
                  set={v => go({...q, filters: {...q.filters, [code]: v}})}/>}
              </li>;
            })}</ul> : <p className="field-help">Sin filtros: se cuentan todos los requerimientos de tu alcance.</p>}
            {Object.values(q.filters).some(v => v.length === 0) && <p className="field-help">Un filtro sin valores no se aplica.</p>}
          </div>
        </div>
        {rightsRow && <label className="check"><input type="checkbox" checked={!flat} onChange={e => setFlat(!e.target.checked)}/><span>Ver eje, derecho y subcategoría anidados</span></label>}
      </Panel>
      <Panel title="Resultado" icon="chart">
        {problem ? <Callout tone="info">{problem}</Callout>
          : main.status === 'error' ? <Callout tone="warn" title={main.status429 ? 'Hay otra consulta en curso' : 'No se pudo calcular'}>{main.error}
            <div className="actions"><button type="button" className="sm" onClick={main.refresh}><Icon name="refresh" size={16}/>Reintentar</button></div></Callout>
            : !r || (rightsRow && !flat && !treeReady && !levelError) ? <div aria-busy="true"><Skeleton lines={6} title/><p className="field-help">Calculando…</p></div>
              : <>
                {levelError && <ErrorMessage>{levelError}</ErrorMessage>}
                <ResultView cat={cat} catalog={ctx.catalog} q={q} r={r} at={main.at} refresh={main.refresh} scope={scope} levels={tree ? levels : undefined} tree={tree}/>
              </>}
      </Panel>
      <SavedViews ctx={ctx} cat={cat} q={problem ? null : q}/>
      <div className="actions"><button type="button" className="ghost" onClick={() => {location.hash = encodeRoute('explorar');}}><Icon name="arrowLeft" size={18}/>Otra pregunta</button></div>
    </div>
  </section>;
}
