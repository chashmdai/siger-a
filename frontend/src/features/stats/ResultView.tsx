import {useId, useState} from 'react';
import type {ReactNode} from 'react';
import {Chart} from '../../components/charts/Chart';
import type {ChartKind} from '../../components/charts/chartModel';
import {choices, suggest} from '../../components/charts/chartModel';
import {Icon} from '../../components/Icon';
import type {Metric, Query, Result, StatsCatalog, TreeRow} from './statsModel';
import {catalogName, chartData, chartShape, chileTime, EMPTY_PERIOD, dimOf, fieldLabel, fileStem, fmtInt, howToRead, isMultiple, measureOf, MULTIPLE_NOTE, NON_ADDITIVE_DIMS,
  NON_ADDITIVE_NOTE, periodLabel, sourceFooter, tableModel, HIERARCHY_NOTE} from './statsModel';
import {ResultTable} from './ResultTable';

/** "Como leer esta cifra": siempre a la vista, con cada aviso del servidor en una frase. */
export function HowToRead({lines}: {lines: string[]}) {
  return <section className="stat-howto" aria-label="Cómo leer esta cifra">
    <h3><Icon name="info" size={18}/>Cómo leer esta cifra</h3>
    <ul>{lines.map(l => <li key={l}>{l}</li>)}</ul>
  </section>;
}
/** "Datos de las HH:MM" y Actualizar (la cache dura cinco minutos). */
export function Freshness({at, refresh, busy}: {at: number; refresh: () => void; busy?: boolean}) {
  return <p className="stat-fresh">
    {at > 0 && <span>Datos de las {chileTime(at)}</span>}
    <button type="button" className="sm ghost" onClick={refresh} disabled={busy}><Icon name="refresh" size={16}/>Actualizar</button>
  </p>;
}

/** Titulo de una consulta en lenguaje llano: "Ingresos por Tipo de solicitud y Región del requirente". */
export function queryTitle(cat: StatsCatalog, q: Query) {
  const dims = [...q.rows, ...q.columns].map(c => fieldLabel(cat, c));
  return (measureOf(cat, q.measure)?.label ?? 'Cifras') + (dims.length ? ' por ' + (dims.length === 1 ? dims[0] : dims.slice(0, -1).join(', ') + ' y ' + dims[dims.length - 1]) : '');
}

/**
 * Resultado de una consulta: grafico sugerido (con los demas posibles y la razon de los que no), tabla con las cifras del
 * servidor como alternativa textual, propuestas aparte y "Como leer esta cifra".
 */
export function ResultView({cat, catalog, q, r, at, refresh, scope, levels, tree, actions, cutCode}: {cat: StatsCatalog; catalog: Record<string, unknown>; q: Query; r: Result; at: number;
  refresh: () => void; scope: string; levels?: Partial<Record<string, Result>>; tree?: TreeRow[] | null; actions?: ReactNode; cutCode?: string}) {
  const id = useId().replace(/:/g, '');
  const metric: Metric = q.percent ? 'percent' : 'n';
  const comparison = !!r.comparison;
  // Comparacion con un corte (E13): por celdas y margenes, marcadas en la tabla. E14 (correccion 2): sin columnas, el grafico
  // pone la cifra actual y la del corte pareadas y no ofrece el circular; con columnas muestra la cifra actual (sin circular).
  const cutCompare = comparison && q.compare === 'CORTE';
  const table = tableModel(r, q.rows, q.columns);
  const paired = comparison && !(cutCompare && q.columns.length > 0);
  const shape = chartShape(cat, q, table, paired);
  const data = chartData(table, metric, paired, r.comparison?.period && !cutCompare ? periodLabel(r.comparison.period) : '', cutCompare ? r.comparison?.cutCode ?? '' : null);
  const values = data.series[0]?.values.map(v => v ?? 0);
  const options = choices(shape, values, data.total ?? undefined);
  const suggested = suggest(shape);
  const [picked, setPicked] = useState<ChartKind | null>(null);
  const kind = picked && options.find(o => o.kind === picked)?.ok ? picked : suggested;
  const multiple = [...q.rows, ...q.columns].some(c => isMultiple(cat, c));
  const title = queryTitle(cat, q);
  const subtitle = periodLabel(r.metadata.period as Record<string, unknown>) + (cutCode ? ' · cifras del corte ' + cutCode : '')
    + (cutCompare ? ' · comparado con el corte ' + (r.comparison?.cutCode || '') : r.comparison?.period ? ' · comparado con ' + periodLabel(r.comparison.period) : '');
  const source = sourceFooter({at, scope, basis: String((r.metadata.period as Record<string, unknown>)?.date_basis ?? ''), mode: String(r.metadata.time_mode ?? 'VIGENTE'), cut: cutCode || null});
  const notes: string[] = [];
  if (multiple) notes.push(MULTIPLE_NOTE + ' Las categorías no suman el total.');
  if ([...q.rows, ...q.columns].some(c => NON_ADDITIVE_DIMS.includes(c))) notes.push(NON_ADDITIVE_NOTE);
  if (tree) notes.push(HIERARCHY_NOTE);
  const lines = howToRead({cat, q, r, scope, hierarchy: !!tree});
  const tableId = 'tabla-' + id;
  const unavailable = options.filter(o => !o.ok && o.kind !== kind);
  return <div className="stat-result">
    <Freshness at={at} refresh={refresh}/>
    <p className="stat-total"><strong>{fmtInt(r.grand.n)}</strong> {multiple ? 'requerimientos únicos en total' : 'en total'}</p>
    {r.grand.n === 0 ? <p className="field-help stat-empty">{EMPTY_PERIOD}</p> : kind ? <>
      <div className="stat-kinds" role="radiogroup" aria-label="Tipo de gráfico">
        {options.filter(o => o.ok).map(o => <button key={o.kind} type="button" role="radio" aria-checked={o.kind === kind} className="stat-kind"
          onClick={() => setPicked(o.kind)}>{o.label}</button>)}
      </div>
      {unavailable.some(o => o.kind === 'circular') && <p className="field-help">Circular no disponible: {unavailable.find(o => o.kind === 'circular')!.reason}</p>}
      <Chart kind={kind} data={data} title={title} subtitle={subtitle} notes={notes} source={source} file={fileStem(title)} tableId={tableId}/>
    </> : <p className="field-help">{options[0]?.reason || 'No hay cifras para graficar.'} Las cifras están en la tabla.</p>}
    <ResultTable id={tableId} caption={title + ' · ' + subtitle} table={table} metric={metric} multiple={multiple} comparison={comparison} cutCompare={cutCompare} tree={tree} levels={levels}/>
    {actions}
    <p className="stat-source">{source}</p>
    {r.proposals.length > 0 && <section className="stat-proposals" aria-label="Propuestas pendientes de confirmar">
      <h3>Propuestas pendientes de confirmar</h3>
      <p className="field-help">El Formulario propone estos valores a partir de sus datos estructurados. No son valores informados y no están en las cifras de arriba.</p>
      <ul>{r.proposals.map((p, i) => <li key={i}>{catalogName(String(p.variable))}: {String(p.label ?? 'valor sin rótulo')}: {fmtInt(Number(p.n ?? 0))} {Number(p.n) === 1 ? 'ingreso' : 'ingresos'}</li>)}</ul>
    </section>}
    <HowToRead lines={lines}/>
    {[...q.rows, ...q.columns].length === 0 && dimOf(cat, 'PERIODO_MES') && <p className="field-help">Agrega un campo en filas para ver el desglose.</p>}
  </div>;
}
