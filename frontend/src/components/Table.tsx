import {useState} from 'react';
import type {ReactNode} from 'react';
import type {Row} from '../lib/rows';
import {text} from '../lib/rows';
import {Icon} from './Icon';
import {EmptyState} from './Primitives';
import type {IconName} from './Icon';

export type Column = {name: string; render: (row: Row) => ReactNode};

const PAGE = 30;

/**
 * Tabla accesible. Con local=true filtra y pagina en el navegador las filas ya recibidas;
 * con local=false solo presenta la pagina que entrego el servidor. En pantallas angostas cada
 * fila se presenta como tarjeta con la etiqueta de cada columna. "open" hace clicable la fila
 * completa; el enlace de referencia sigue siendo el control accesible por teclado.
 */
export function Table({rows, columns, empty = 'No hay registros en tu alcance.', emptyHint, emptyIcon = 'inbox', local = true, open}: {
  rows: Row[]; columns: Column[]; empty?: string; emptyHint?: ReactNode; emptyIcon?: IconName; local?: boolean; open?: (row: Row) => void;
}) {
  const [query, setQuery] = useState(''), [page, setPage] = useState(0);
  const filtered = local ? rows.filter(r => Object.values(r).some(v => typeof v !== 'object' && String(v ?? '').toLocaleLowerCase().includes(query.toLocaleLowerCase()))) : rows;
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE));
  const current = Math.min(page, pages - 1);
  const visible = local ? filtered.slice(current * PAGE, current * PAGE + PAGE) : rows;
  const searchable = local && rows.length > 8;
  return <section className="table-block">
    {searchable && <div className="table-search">
      <label className="input-icon">
        <span className="sr-only">Filtrar registros visibles</span>
        <Icon name="search" size={18}/>
        <input type="search" placeholder="Filtrar registros visibles" value={query} onChange={e => {setQuery(e.target.value); setPage(0);}}/>
      </label>
    </div>}
    {filtered.length === 0 ? <EmptyState compact icon={query ? 'search' : emptyIcon} title={query ? 'Sin coincidencias' : empty}>{query ? 'Prueba con otra palabra o limpia el filtro.' : emptyHint}</EmptyState> : <>
      <div className="table-scroll responsive">
        <table>
          <thead><tr>{columns.map(c => <th key={c.name} scope="col">{c.name}</th>)}</tr></thead>
          <tbody>{visible.map((r, i) => <tr key={text(r, 'id') || i} className={open ? 'clickable' : undefined}
            onClick={open ? e => {if (!(e.target as Element).closest('a,button,input,select,textarea,label')) open(r);} : undefined}>
            {columns.map(c => <td key={c.name} data-label={c.name}>{c.render(r)}</td>)}
          </tr>)}</tbody>
        </table>
      </div>
      {local && pages > 1 && <div className="table-foot">
        <span>{filtered.length} registros</span>
        <div className="pager">
          <button className="sm icon-only" aria-label="Página anterior" disabled={current === 0} onClick={() => setPage(current - 1)}><Icon name="left" size={18}/></button>
          <span>Página {current + 1} de {pages}</span>
          <button className="sm icon-only" aria-label="Página siguiente" disabled={current + 1 >= pages} onClick={() => setPage(current + 1)}><Icon name="right" size={18}/></button>
        </div>
      </div>}
    </>}
  </section>;
}
