import {useState} from 'react';
import {Icon} from '../../components/Icon';
import type {IconName} from '../../components/Icon';
import type {Query, StatsCatalog} from './statsModel';
import {dimHelp, families, fits, MULTIPLE_NOTE, NON_ADDITIVE, NON_ADDITIVE_DIMS} from './statsModel';

export type Axis = 'rows' | 'columns' | 'filters';
/** Icono de cada familia de campos (0.8.1, seccion 3.3): apoyo visual junto al nombre. */
const FAMILY_ICONS: Record<string, IconName> = {periodo: 'calendarRange', ingreso: 'inbox', territorio: 'mapPin', derechos: 'scale', actuaciones: 'activity',
  personas: 'people', cadena: 'milestone', calidad: 'shield', otros: 'tag'};
const fold = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('es-CL');

/**
 * Campos agrupados por familia, con buscador (el texto no sale de la pantalla: no va en la direccion), descripcion breve y
 * la marca "valores múltiples". Cada campo se agrega a filas, columnas o filtros con un boton (teclado) o arrastrandolo.
 */
export function FieldPicker({cat, q, add, open = true}: {cat: StatsCatalog; q: Query; add: (axis: Axis, code: string) => void; open?: boolean}) {
  const [term, setTerm] = useState('');
  const t = fold(term.trim());
  const groups = families(cat).map(f => ({...f, dims: f.dims.filter(d => !t || fold(d.label + ' ' + dimHelp(d)).includes(t))})).filter(f => f.dims.length);
  const used = new Set([...q.rows, ...q.columns, ...Object.keys(q.filters)]);
  return <aside id="stat-campos" className={'stat-fields' + (open ? ' open' : '')} aria-label="Campos">
    <label className="input-icon">
      <span className="sr-only">Buscar un campo</span>
      <Icon name="search" size={18}/>
      <input type="search" placeholder="Buscar un campo" value={term} autoComplete="off" onChange={e => setTerm(e.target.value)}/>
    </label>
    {groups.length === 0 && <p className="field-help">Ningún campo coincide con la búsqueda.</p>}
    {groups.map(f => <details key={f.key} className="stat-family" open={!!t || f.key === 'ingreso'}>
      <summary><Icon name={FAMILY_ICONS[f.key] ?? 'tag'} size={18}/>{f.label} <small>{f.dims.length}</small></summary>
      <ul>
        {f.dims.map(d => {
          const ok = fits(cat, q.measure, d.code);
          const multiple = d.cardinality === 'MULTIPLE';
          const period = d.type === 'PERIODO';
          return <li key={d.code} className={'stat-field' + (ok ? '' : ' unavailable') + (used.has(d.code) ? ' used' : '')} draggable={ok}
            onDragStart={e => {e.dataTransfer.setData('text/plain', d.code); e.dataTransfer.effectAllowed = 'move';}}>
            <div className="stat-field-name">
              <strong>{d.label}</strong>
              {multiple && <span className="stat-badge" title={MULTIPLE_NOTE}>valores múltiples</span>}
              {NON_ADDITIVE_DIMS.includes(d.code) && <span className="stat-badge warn">{NON_ADDITIVE}</span>}
              {used.has(d.code) && <span className="stat-used"><Icon name="check" size={14}/>En uso</span>}
            </div>
            {dimHelp(d) && <p className="stat-field-help">{dimHelp(d)}{multiple ? ' ' + MULTIPLE_NOTE : ''}</p>}
            {ok ? <div className="stat-field-actions" role="group" aria-label={'Usar ' + d.label}>
              <button type="button" className="sm" onClick={() => add('rows', d.code)} aria-label={`Agregar ${d.label} a filas`}>Filas</button>
              <button type="button" className="sm" onClick={() => add('columns', d.code)} aria-label={`Agregar ${d.label} a columnas`}>Columnas</button>
              {d.filterable && !period && <button type="button" className="sm" onClick={() => add('filters', d.code)} aria-label={`Filtrar por ${d.label}`}>Filtrar</button>}
            </div> : <p className="stat-field-help">No se puede usar con la medida elegida.</p>}
          </li>;
        })}
      </ul>
    </details>)}
  </aside>;
}
