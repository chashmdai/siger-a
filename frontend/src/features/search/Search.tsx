import {useCallback, useEffect, useRef, useState} from 'react';
import type {KeyboardEvent, ReactNode} from 'react';
import {request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {list, number, obj, text, when} from '../../lib/rows';
import {FLOW_LABELS, humanize, MATTER_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Kind, Status} from '../../components/Status';
import {Icon} from '../../components/Icon';
import {Callout, EmptyState, ErrorMessage, PageHeader, Skeleton} from '../../components/Primitives';
import {SearchField} from './SearchField';
import type {Period, SearchState} from './search';
import {
  alternatives, apiPath, clearSearch, exactTarget, filtered, formatRun, interpretation, MATCH_ROLE_LABELS, matchReason, nextIndex, primaryTarget,
  READING_LABELS, READING_TITLES, readSearch, saveSearch, scopeLabel, SEARCH_EVENT, SEARCH_ROUTE, searchable, sessionStore, showFilters, shownReadings,
  SOURCE_LABELS, startSearch, summaryLines, UNCERTAIN, withoutFilters,
} from './search';

const store = sessionStore();

/**
 * Vista unica de resultados del buscador (#buscar, sin parametros: el termino vive en sessionStorage). Un solo motor
 * (/api/search) para Inicio, la paleta y el menu. Cada resultado es la cadena COM -> SOL -> REQ -> Casos legible.
 */
export default function Search({ctx}: {ctx: Context}) {
  const [state, setState] = useState<SearchState | null>(() => readSearch(store));
  const [data, setData] = useState<Row | null>(null), [error, setError] = useState(''), [loading, setLoading] = useState(false);
  const input = useRef<HTMLInputElement>(null), results = useRef<HTMLDivElement>(null);

  // Una ruta antigua con parametros (#buscar?q=...) se reemplaza: el termino nunca queda en la direccion.
  useEffect(() => {if (location.hash.includes('?')) location.replace('#' + SEARCH_ROUTE);}, []);
  // La paleta puede iniciar otra busqueda con la vista abierta.
  useEffect(() => {
    const reread = () => setState(readSearch(store));
    window.addEventListener(SEARCH_EVENT, reread);
    return () => window.removeEventListener(SEARCH_EVENT, reread);
  }, []);

  useEffect(() => {
    if (!state || !searchable(state.q)) {setData(null); setError(''); return;}
    let live = true;
    setLoading(true);
    request(apiPath(state, new Date())).then(r => {
      if (!live) return;
      const exact = state.fresh ? exactTarget(r) : null;
      if (state.fresh) saveSearch(store, {...state, fresh: false});
      // Un folio exacto abre su ficha; replace evita que Atras vuelva a abrirla.
      if (exact) {location.replace(`#${exact[0]}/${exact[1]}`); return;}
      setData(r); setError('');
    }).catch(e => {
      if (!live) return;
      setData(null); setError((e as Error).message);
      if (state.fresh) saveSearch(store, {...state, fresh: false});
    }).finally(() => {if (live) setLoading(false);});
    return () => {live = false;};
  }, [state]);

  const update = useCallback((next: SearchState) => {saveSearch(store, next); setState(next);}, []);
  function search(term: string) {startSearch(term, store); setState(readSearch(store) ?? {q: term, fresh: true});}
  function clear() {clearSearch(store); setState(null); setData(null); input.current?.focus();}

  function focusResult(index: number) {
    results.current?.querySelectorAll<HTMLElement>('[data-result]')[index]?.focus();
  }
  function keys(e: KeyboardEvent<HTMLDivElement>) {
    const items = [...(results.current?.querySelectorAll<HTMLElement>('[data-result]') ?? [])];
    const current = items.findIndex(el => el === document.activeElement || el.contains(document.activeElement));
    if (e.key === 'Escape') {e.preventDefault(); input.current?.focus(); return;}
    if (current < 0) return;
    if (e.key === 'Enter' && e.target === items[current]) {
      const target = items[current].dataset.target;
      if (target) {e.preventDefault(); location.hash = target;}
      return;
    }
    const next = nextIndex(e.key, current, items.length);
    if (next != null) {e.preventDefault(); items[next].focus();}
  }

  const readings = data ? list(data, 'readings') : [];
  const multiple = readings.length > 1;
  return <section className="search-page">
    <PageHeader eyebrow={<><Icon name="search" size={14}/>Buscador</>} title="Buscar persona o folio"
      lead="Por RUN, nombre, documento o folio. Ves solo lo que tus funciones permiten leer, y cada búsqueda queda registrada sin el término."/>
    <SearchField key={state?.q ?? ''} initial={state?.q ?? ''} onSearch={search} inputRef={input} autoFocus={!state} onArrowDown={() => focusResult(0)}/>
    {state && <div className="actions search-reset"><button type="button" className="ghost sm" onClick={clear}><Icon name="close" size={16}/>Limpiar búsqueda</button></div>}
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {!state && <EmptyState icon="search" title="Escribe un RUN, un nombre, un documento o un folio">
      Un folio exacto abre su ficha. Un número puede leerse como correlativo y como RUN: verás cada lectura por separado. La búsqueda por nombre no es aproximada: cada palabra debe coincidir con el inicio de una palabra del nombre.
    </EmptyState>}
    {loading && !data && <div aria-busy="true" aria-label="Buscando"><Skeleton title lines={4}/></div>}
    {data && <div ref={results} onKeyDown={keys} aria-busy={loading}>
      <Interpretation data={data} state={state!} update={update}/>
      {readings.map(r => <Reading key={text(r, 'type')} reading={r} multiple={multiple} state={state!} update={update} ctx={ctx}/>)}
    </div>}
  </section>;
}

/** "Buscando como ..." con las otras lecturas posibles. */
function Interpretation({data, state, update}: {data: Row; state: SearchState; update: (s: SearchState) => void}) {
  const others = alternatives(data), forced = Boolean(data.forced);
  return <div className="search-interpretation" role="status">
    <p><Icon name="info" size={16}/><strong>{interpretation(data)}</strong>{forced && ' (elegido por ti)'}</p>
    {(others.length > 0 || forced) && <div className="segmented" role="group" aria-label="Cambiar la interpretación">
      {others.map(k => <button type="button" key={k} onClick={() => update({...withoutFilters(state), as: k, fresh: true})}>
        {shownReadings(data).includes(k) ? 'Solo como ' : 'Buscar como '}{READING_LABELS[k] ?? k}
      </button>)}
      {forced && <button type="button" onClick={() => update({...withoutFilters(state), as: undefined})}>Interpretación automática</button>}
    </div>}
  </div>;
}

function Reading({reading, multiple, state, update, ctx}: {reading: Row; multiple: boolean; state: SearchState; update: (s: SearchState) => void; ctx: Context}) {
  const type = text(reading, 'type'), groups = list(reading, 'groups'), total = number(reading, 'total');
  const titleId = 'lectura-' + type;
  return <section className="search-reading" aria-labelledby={titleId}>
    <div className="search-reading-head">
      <h2 id={titleId}>{multiple ? READING_TITLES[type] ?? type : 'Resultados'}{reading.too_many !== true && <span className="panel-count">{total.toLocaleString('es-CL')}</span>}</h2>
      {multiple && <button type="button" className="ghost sm" onClick={() => update({...withoutFilters(state), as: type, fresh: true})}>Ver solo como {READING_LABELS[type]}<Icon name="arrowRight" size={16}/></button>}
    </div>
    {reading.warning != null && <Callout tone="warn">{text(reading, 'warning')}</Callout>}
    {reading.too_many === true && <Callout tone="warn" role="status">{text(reading, 'message')}</Callout>}
    {reading.person != null && reading.too_many !== true && <Person reading={reading}/>}
    {!multiple && reading.too_many !== true && showFilters(reading, state) && <Filters reading={reading} state={state} update={update} ctx={ctx}/>}
    {reading.more === true && reading.too_many !== true && <Callout tone="neutral">{text(reading, 'message')}</Callout>}
    {reading.too_many !== true && groups.length === 0 && <EmptyState compact icon="search" title={filtered(state) ? 'Sin resultados con estos filtros' : 'Sin resultados que puedas leer'}
      actions={filtered(state) ? <button type="button" className="sm" onClick={() => update(withoutFilters(state))}>Quitar filtros</button> : undefined}>
      La búsqueda solo muestra lo que tus funciones permiten leer. Prueba con el RUN, otro orden del nombre o un folio.
    </EmptyState>}
    {groups.length > 0 && <ol className="search-results" aria-label={multiple ? READING_TITLES[type] : 'Resultados'}>
      {groups.map((g, i) => <Group key={i} group={g}/>)}
    </ol>}
  </section>;
}

/** Encabezado de la persona buscada por RUN o documento, con sus totales legibles (lo abierto primero). */
function Person({reading}: {reading: Row}) {
  const person = obj(reading, 'person'), names = (person.names as string[] | undefined) ?? [], summary = summaryLines(obj(reading, 'summary'));
  const id = text(person, 'run_number') ? 'RUN ' + formatRun(text(person, 'run_number'), text(person, 'run_dv') || null) : 'Documento ' + text(person, 'document');
  return <div className="search-person">
    <span className="search-person-icon" aria-hidden="true"><Icon name="user"/></span>
    <div>
      <h3>{id}</h3>
      <p>{names.length ? 'Nombres declarados: ' + names.join(' · ') : 'Sin nombre declarado en lo que puedes leer.'}</p>
      <p className="search-person-auth">{person.authenticated === true ? <><Icon name="lock" size={14}/>Identidad autenticada con ClaveÚnica en al menos un registro.</> : 'Identidad declarada; no autenticada.'}</p>
    </div>
    <dl className="search-totals">
      <div><dt>Abierto</dt><dd>{summary.open}</dd></div>
      <div><dt>Total legible</dt><dd>{summary.total}</dd></div>
    </dl>
  </div>;
}

const PERIODS: [Period, string][] = [['', 'Cualquier fecha'], ['d30', 'Últimos 30 días'], ['m12', 'Últimos 12 meses'], ['custom', 'Elegir fechas']];

/** Filtros sobre lo legible, como botones; solo con muchos resultados o si ya hay uno activo. */
function Filters({reading, state, update, ctx}: {reading: Row; state: SearchState; update: (s: SearchState) => void; ctx: Context}) {
  const facets = obj(reading, 'facets'), roles = obj(facets, 'roles'), regions = obj(facets, 'regions');
  const scopes = list(facets, 'scopes').sort((a, b) => number(b, 'count') - number(a, 'count')).slice(0, 8);
  const regionOrder = list(ctx.catalog, 'regions');
  const regionName = (code: string) => text(regionOrder.find(r => text(r, 'code') === code) ?? {}, 'name') || code;
  const orderedRegions = Object.keys(regions).sort((a, b) => regionOrder.findIndex(r => text(r, 'code') === a) - regionOrder.findIndex(r => text(r, 'code') === b));
  const set = (patch: Partial<SearchState>) => update({...state, ...patch, fresh: false});
  const [from, setFrom] = useState(state.from ?? ''), [to, setTo] = useState(state.to ?? '');
  return <div className="search-filters" role="group" aria-label="Filtros">
    <p className="search-filters-title"><Icon name="layers" size={16}/>Refina los resultados{filtered(state) && <button type="button" className="ghost sm" onClick={() => update(withoutFilters(state))}>Quitar filtros</button>}</p>
    <FilterRow label="Estado">
      <button type="button" aria-pressed={!state.open} onClick={() => set({open: false})}>Todos</button>
      <button type="button" aria-pressed={state.open === true} onClick={() => set({open: true})}>Solo abiertos <span className="panel-count">{number(facets, 'open')}</span></button>
    </FilterRow>
    {Object.keys(roles).length > 0 && <FilterRow label="Rol de la persona">
      <button type="button" aria-pressed={!state.role} onClick={() => set({role: undefined})}>Todos</button>
      {Object.entries(roles).map(([role, count]) => <button type="button" key={role} aria-pressed={state.role === role} onClick={() => set({role})}>
        {MATCH_ROLE_LABELS[role] ?? humanize(role)} <span className="panel-count">{Number(count)}</span>
      </button>)}
    </FilterRow>}
    {orderedRegions.length > 0 && <FilterRow label="Región">
      <button type="button" aria-pressed={!state.region} onClick={() => set({region: undefined})}>Todas</button>
      {orderedRegions.map(code => <button type="button" key={code} aria-pressed={state.region === code} onClick={() => set({region: code})}>
        {regionName(code)} <span className="panel-count">{Number(regions[code])}</span>
      </button>)}
    </FilterRow>}
    {scopes.length > 1 && <FilterRow label="Sede o unidad">
      <button type="button" aria-pressed={!state.scope} onClick={() => set({scope: undefined})}>Todas</button>
      {scopes.map(s => {const sc = obj(s, 'scope'), id = number(sc, 'id');
        return <button type="button" key={id} aria-pressed={state.scope === id} onClick={() => set({scope: id})}>{scopeLabel(sc)} <span className="panel-count">{number(s, 'count')}</span></button>;})}
    </FilterRow>}
    <FilterRow label="Recepción">
      {PERIODS.map(([value, label]) => <button type="button" key={value} aria-pressed={(state.period ?? '') === value}
        onClick={() => value === 'custom' ? set({period: 'custom', from: from || undefined, to: to || undefined}) : set({period: value || undefined, from: undefined, to: undefined})}>{label}</button>)}
    </FilterRow>
    {state.period === 'custom' && <form className="search-dates" onSubmit={e => {e.preventDefault(); set({period: 'custom', from: from || undefined, to: to || undefined});}}>
      <label>Desde<input type="date" value={from} onChange={e => setFrom(e.target.value)}/></label>
      <label>Hasta<input type="date" value={to} onChange={e => setTo(e.target.value)}/></label>
      <button type="submit" className="sm">Aplicar fechas</button>
    </form>}
  </div>;
}
function FilterRow({label, children}: {label: string; children: ReactNode}) {
  return <div className="search-filter-row"><span className="search-filter-label">{label}</span><div className="segmented" role="group" aria-label={label}>{children}</div></div>;
}

/** Un resultado: por que coincide y la cadena legible. Enter abre la ficha principal; Tab recorre sus enlaces. */
function Group({group}: {group: Row}) {
  const target = primaryTarget(group), matches = list(group, 'matches'), folios = list(group, 'folio_matches');
  const com = group.communication != null ? obj(group, 'communication') : null;
  const sols = list(group, 'solicitations'), reqs = list(group, 'requirements'), cases = list(group, 'cases'), flows = list(group, 'flows');
  const direct = sols.length === 0 && reqs.some(r => text(r, 'origin') === 'ATENCION_DIRECTA' && text(r, 'relation') === 'ORIGEN');
  const label = (com ? text(com, 'folio') : text(reqs[0] ?? cases[0] ?? {}, 'folio')) + (group.open === true ? ', abierto' : ', cerrado');
  return <li>
    <article className="search-group" tabIndex={0} data-result data-target={target ? `${target[0]}/${target[1]}` : undefined} aria-label={'Resultado ' + label}>
      <ul className="search-matches">
        {folios.map(f => <li key={text(f, 'kind') + number(f, 'id')}><Icon name="check" size={16}/>Coincide por folio <strong className="folio">{text(f, 'folio')}</strong></li>)}
        {matches.map((m, i) => {const reason = matchReason(m);
          return <li key={i} className={reason.uncertain ? 'uncertain' : undefined}>
            <Icon name={reason.uncertain ? 'alert' : 'check'} size={16}/>
            <span><strong>{text(m, 'display_name') || 'Sin nombre declarado'}</strong> · {MATCH_ROLE_LABELS[text(m, 'role')] ?? humanize(text(m, 'role'))}. {reason.text} en {SOURCE_LABELS[text(m, 'source')] ?? text(m, 'source')}.</span>
            {reason.uncertain && <span className="chip uncertain-chip">{UNCERTAIN}</span>}
          </li>;})}
      </ul>
      <ol className="search-chain" aria-label="Cadena del registro">
        {com && <Step kind="COM" href={`#COM/${number(com, 'id')}`} title={text(com, 'folio')} state={com.state} facts={[
          ['Origen', scopeLabel(com.origin)],
          ['Registró', text(com, 'registered_by') || (com.external === true ? 'Formulario Público' : 'Sin información')],
          ['Recibida', when(com.received_ms)],
        ]}/>}
        {sols.map(s => <Step key={'s' + number(s, 'id')} kind="SOL" href={`#SOL/${number(s, 'id')}`} title={'SOL ' + number(s, 'id')} state={s.state} facts={[
          ['Destino', scopeLabel(s.destination)], ['Recibe', text(s, 'receiver')],
          ...(s.suggested_matter ? [['Materia sugerida', MATTER_LABELS[text(s, 'suggested_matter')] ?? text(s, 'suggested_matter')] as [string, string]] : []),
        ]}/>)}
        {direct && <li className="search-step skipped"><Kind kind="SOL"/><span>No aplica: atención directa, sin SOL.</span></li>}
        {reqs.map(r => <Step key={'r' + number(r, 'id')} kind="REQ" href={`#REQ/${number(r, 'id')}`} title={text(r, 'folio')} state={r.state}
          tag={text(r, 'relation') === 'ANTECEDENTE' ? 'Antecedente' : undefined} facts={[
            ['Principal', scopeLabel(r.principal)], ['Encargado', text(r, 'attention_user') || 'Sin encargado'],
            ['Último movimiento', when(r.last_ms)],
          ]}>
          {[...cases.filter(c => number(c, 'requirement_id') === number(r, 'id')).map(c => <Step key={'c' + number(c, 'id')} kind="CAS" href={`#CAS/${number(c, 'id')}`} title={text(c, 'folio')} state={c.state} facts={[
            ['Ámbito', scopeLabel(c.scope)], ['Responsable', text(c, 'responsible') || 'Sin responsable'], ['Último movimiento', when(c.last_ms)],
          ]}/>),
          ...flows.filter(f => number(f, 'requirement_id') === number(r, 'id')).map(f => <li key={'f' + number(f, 'id')} className="search-step">
            <span className="search-step-head"><Kind kind="FLUJO"/><a href={`#FLUJO/${number(f, 'id')}`}>{FLOW_LABELS[text(f, 'kind')] ?? humanize(text(f, 'kind'))} pendiente</a><Status value="PENDIENTE"/></span>
            <span className="search-step-facts">{scopeLabel(f.origin)} → {scopeLabel(f.target)} · solicitada {when(f.created_ms)}</span>
          </li>)]}
        </Step>)}
        {/* Casos legibles cuyo REQ no se lee (por ejemplo, por materia del Caso). */}
        {cases.filter(c => !reqs.some(r => number(r, 'id') === number(c, 'requirement_id'))).map(c => <Step key={'c' + number(c, 'id')} kind="CAS" href={`#CAS/${number(c, 'id')}`} title={text(c, 'folio')} state={c.state} facts={[
          ['Ámbito', scopeLabel(c.scope)], ['Responsable', text(c, 'responsible') || 'Sin responsable'], ['Último movimiento', when(c.last_ms)],
        ]}/>)}
      </ol>
    </article>
  </li>;
}

function Step({kind, href, title, state, facts, tag, children}: {kind: string; href: string; title: string; state: unknown; facts: [string, string][]; tag?: string; children?: ReactNode}) {
  return <li className="search-step">
    <span className="search-step-head"><Kind kind={kind}/><a href={href} className="folio">{title}</a><Status value={state}/>{tag && <span className="chip">{tag}</span>}</span>
    <span className="search-step-facts">{facts.map(([label, value]) => <span key={label}><span className="search-fact-label">{label}:</span> {value}</span>)}</span>
    {children && <ol className="search-chain nested">{children}</ol>}
  </li>;
}
