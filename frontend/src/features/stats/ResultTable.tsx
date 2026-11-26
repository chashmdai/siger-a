import {useState} from 'react';
import type {Metric, Result, Sort, TableModel, TreeRow} from './statsModel';
import {beforeText, cellFigure, changedCells, changedSummary, compactDelta, fmtInt, keyOf, NO_BASE_NOTE, sortRows, tableModel} from './statsModel';

const ariaSort = (sort: Sort, by: 'label' | 'value', column = 0) => !sort || sort.by !== by || (by === 'value' && sort.column !== column) ? 'none' : sort.dir === 'asc' ? 'ascending' : 'descending';
/** Tabla ancha (E13, ajuste 4): con mas de tres columnas de cifras se indica el desplazamiento horizontal. */
export const WIDE_COLUMNS = 4;
export const SCROLL_HINT = 'Desliza la tabla hacia el lado para ver todas las columnas; la primera queda fija.';

/**
 * Tabla de cifras del explorador: encabezados fijos, primera columna fija y sombra de desplazamiento en las tablas anchas,
 * cifras a la derecha con tabular-nums y formato chileno, orden por columna, margenes y total del servidor destacados
 * (nunca sumados aqui), 0 como cero y "—" con su explicacion. Con una comparacion de periodos agrega la cifra base y la
 * variacion compacta ("+1", "−2 (−15,4 %)", "="), con "Sin base de comparación" una sola vez al pie. Con un corte, cada
 * celda que cambio se marca con texto ("antes 5, +2") ademas del color y se resume cuantas cambiaron. En derechos, la
 * jerarquia eje -> derecho -> subcategoria se pliega.
 */
export function ResultTable({id, caption, table, metric, multiple, comparison, cutCompare, tree, levels}: {id: string; caption: string; table: TableModel; metric: Metric;
  multiple: boolean; comparison: boolean; cutCompare?: boolean; tree?: TreeRow[] | null; levels?: Partial<Record<string, Result>>}) {
  const [sort, setSort] = useState<Sort>(null);
  const [closed, setClosed] = useState<Set<string>>(new Set());
  const cols = table.columns;
  const hasCols = table.colDims.length > 0;
  const compare = comparison && !hasCols && !cutCompare;
  const toggle = (by: 'label' | 'value', column = 0) => setSort(s => s && s.by === by && s.column === column ? (s.dir === 'desc' ? {...s, dir: 'asc'} : null) : {by, column, dir: by === 'label' ? 'asc' : 'desc'});
  let anyMissing = false, noBase = false;
  const figure = (row: string[], col: string[], model = table) => {
    const f = cellFigure(model.value(row, col), metric);
    if (f.missing) anyMissing = true;
    return f;
  };
  const numeric = (hasCols ? cols.length + 1 : 1) + (compare ? 2 : 0);
  const wide = numeric >= WIDE_COLUMNS;
  const totalLabel = (multiple ? 'Total único de requerimientos' : 'Total') + (metric === 'n' ? '' : ' (cantidad)');
  const head = <tr>
    <th scope="col" aria-sort={ariaSort(sort, 'label')}><button type="button" className="sort" onClick={() => toggle('label')}>{table.rowDims.length ? 'Categoría' : 'Cifra'}</button></th>
    {hasCols ? cols.map((c, i) => <th key={keyOf(c.key)} scope="col" className="num" aria-sort={ariaSort(sort, 'value', i)}>
      <button type="button" className="sort" onClick={() => toggle('value', i)}>{c.labels.join(' · ')}</button></th>)
      : <th scope="col" className="num" aria-sort={ariaSort(sort, 'value')}><button type="button" className="sort" onClick={() => toggle('value')}>{metric === 'percent' ? 'Porcentaje' : metric === 'mentions' ? 'Menciones' : 'Cantidad'}</button></th>}
    {hasCols && <th scope="col" className="num total">{totalLabel}</th>}
    {compare && <><th scope="col" className="num">Cifra base</th><th scope="col" className="num">Variación</th></>}
  </tr>;
  const compareCells = (row: string[]) => {
    const d = table.delta(row, []);
    if (!d) return <><td className="num">—</td><td className="num">—</td></>;
    const v = compactDelta(d.delta_n, d.delta_percent, d.base_n);
    if (v.noBase) noBase = true;
    const arrow = d.delta_n > 0 ? '↑ ' : d.delta_n < 0 ? '↓ ' : '';
    return <><td className="num">{fmtInt(d.base_n)}</td>
      <td className="num delta" aria-label={v.label + (v.noBase ? '; sin base de comparación' : '')}><span aria-hidden="true">{arrow}{v.text}{v.noBase ? ' *' : ''}</span></td></>;
  };
  /** Celda frente a un corte: la cifra y, si cambio, "antes N, ±d" a la vista. */
  const cell = (row: string[], col: string[], model: TableModel, total = false) => {
    const f = figure(row, col, model);
    const d = cutCompare && !total ? model.delta(row, col) : null;
    const changed = !!d && d.delta_n !== 0;
    return <td key={keyOf(col)} className={'num' + (total ? ' total' : '') + (changed ? ' changed' : '')}>
      {f.text}{changed && <small className="stat-before">{beforeText(d!)}</small>}
    </td>;
  };
  const rowCells = (row: string[], model = table) => <>
    {hasCols ? cols.map(c => cell(row, c.key, model)) : cell(row, [], model)}
    {hasCols && <td className="num total">{fmtInt(model.rowTotal(row)?.n ?? 0)}</td>}
    {compare && compareCells(row)}
  </>;
  let body;
  if (tree && levels) {
    const visible = tree.filter(r => {
      let p = r.parent;
      while (p) {if (closed.has(p)) return false; p = tree.find(x => x.key === p)?.parent ?? '';}
      return true;
    });
    const hasChildren = (k: string) => tree.some(x => x.parent === k);
    const models = new Map(Object.entries(levels).map(([dim, r]) => [dim, tableModel(r!, [dim], table.colDims)]));
    body = visible.map(r => {
      const model = models.get(r.dim) ?? table;
      return <tr key={r.dim + r.key} className={'level-' + r.level + (r.special ? ' special' : '')}>
        <th scope="row">
          <span className="tree-indent" style={{paddingInlineStart: r.level * 1.25 + 'rem'}}>
            {hasChildren(r.key) ? <button type="button" className="tree-toggle" aria-expanded={!closed.has(r.key)}
              onClick={() => setClosed(s => {const n = new Set(s); if (n.has(r.key)) n.delete(r.key); else n.add(r.key); return n;})}>
              <span aria-hidden="true">{closed.has(r.key) ? '▸' : '▾'}</span><span className="sr-only">{closed.has(r.key) ? 'Mostrar' : 'Ocultar'} el detalle de</span> {r.label}</button> : r.label}
          </span>
        </th>
        {rowCells([r.key], model)}
      </tr>;
    });
  } else {
    body = sortRows(table, metric, sort).map(r => <tr key={keyOf(r.key)}><th scope="row">{r.labels.join(' · ') || 'Total'}</th>{rowCells(r.key)}</tr>);
  }
  const foot = table.rowDims.length > 0 && <tr className="total">
    <th scope="row">{totalLabel}</th>
    {hasCols ? cols.map(c => <td key={keyOf(c.key)} className="num">{fmtInt(table.colTotal(c.key)?.n ?? 0)}</td>) : <td className="num">{fmtInt(table.rowTotal([])?.n ?? 0)}</td>}
    {hasCols && <td className="num">{fmtInt(table.rowTotal([])?.n ?? 0)}</td>}
    {compare && compareCells([])}
  </tr>;
  const changed = cutCompare ? changedCells(table) : 0;
  return <div className={'stat-table-wrap' + (wide ? ' wide' : '')}>
    {cutCompare && <p className="stat-changed" role="status"><strong>{changedSummary(changed)}</strong>{changed > 0 && ' Las celdas marcadas dicen la cifra del corte («antes») y la diferencia.'}</p>}
    {wide && <p className="field-help stat-scroll-hint">{SCROLL_HINT}</p>}
    <div className="table-scroll" tabIndex={wide ? 0 : undefined} role={wide ? 'region' : undefined} aria-label={wide ? 'Tabla desplazable: ' + caption : undefined}>
      <table id={id} tabIndex={-1} className="stat-table sticky-first">
        <caption>{caption}</caption>
        <thead>{head}</thead>
        <tbody>{body}</tbody>
        {foot && <tfoot>{foot}</tfoot>}
      </table>
    </div>
    {noBase && <p className="field-help stat-footnote">* {NO_BASE_NOTE}</p>}
    {anyMissing && <p className="field-help">«—»: no hay denominador para calcular el porcentaje (el total de esa fila o columna es cero) o no hay menciones que contar.</p>}
    {hasCols && <p className="field-help">Los totales los calcula SIGER sobre cada fila y columna{multiple ? '; con valores múltiples no son la suma de las celdas' : ''}.</p>}
  </div>;
}
