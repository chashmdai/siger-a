import {useEffect, useMemo, useRef, useState} from 'react';
import type {KeyboardEvent} from 'react';
import {Icon} from '../components/Icon';
import type {IconName} from '../components/Icon';
import {useExit} from '../components/useExit';
import type {MenuGroup} from './menu';
import {boardRoute} from './routes';

type Entry = {id: string; section: string; label: string; hint?: string; icon: IconName; route: string};

/** Bandejas donde la busqueda por folio o texto tiene sentido, en orden de uso. */
const SEARCHABLE: [path: string, label: string][] = [['req', 'Requerimientos'], ['cas', 'Casos'], ['atenciones', 'Primeras atenciones'], ['com', 'Comunicaciones'], ['sol', 'Solicitudes']];

const normalize = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('es-CL');

/**
 * Paleta para ir a cualquier pantalla o buscar un folio sin recorrer menus (Ctrl+K).
 * Solo navega: la busqueda se ejecuta en la bandeja con el alcance autorizado de la sesion.
 */
export function CommandPalette({groups, close}: {groups: MenuGroup[]; close: () => void}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [query, setQuery] = useState(''), [selected, setSelected] = useState(0);
  const [leaving, exit] = useExit(close, 120);
  useEffect(() => {ref.current?.showModal();}, []);

  const entries = useMemo(() => {
    const available = new Set(groups.flatMap(g => g.items.map(i => i.path)));
    const q = normalize(query.trim());
    const pages: Entry[] = groups.flatMap(g => g.items.map(i => ({id: 'go:' + i.path, section: g.title, label: i.label, hint: i.hint, icon: i.icon, route: i.path})))
      .filter(e => !q || normalize(e.label + ' ' + (e.hint ?? '') + ' ' + e.section).includes(q));
    const searches: Entry[] = query.trim() ? SEARCHABLE.filter(([path]) => available.has(path)).map(([path, label]) => ({
      id: 'search:' + path, section: 'Buscar en bandejas', label: `Buscar «${query.trim()}» en ${label}`, icon: 'search' as IconName, route: boardRoute(path, '', query.trim()),
    })) : [];
    // Un folio escrito (por ejemplo REQ-...) prioriza la busqueda sobre la navegacion.
    return /\d/.test(query) ? [...searches, ...pages] : [...pages, ...searches];
  }, [groups, query]);

  const current = Math.min(selected, Math.max(0, entries.length - 1));
  function choose(e?: Entry) {
    if (!e) return;
    location.hash = e.route;
    exit();
  }
  function keys(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {e.preventDefault(); setSelected((current + 1) % Math.max(1, entries.length));}
    else if (e.key === 'ArrowUp') {e.preventDefault(); setSelected((current - 1 + entries.length) % Math.max(1, entries.length));}
    else if (e.key === 'Enter') {e.preventDefault(); choose(entries[current]);}
  }
  useEffect(() => {document.getElementById('palette-' + current)?.scrollIntoView({block: 'nearest'});}, [current]);

  let lastSection = '';
  return <dialog ref={ref} className={'palette' + (leaving ? ' closing' : '')} aria-label="Buscar o ir a" onCancel={e => {e.preventDefault(); exit();}}
    onClick={e => {if (e.target === ref.current) exit();}}>
    <div className="palette-search">
      <Icon name="search"/>
      <input autoFocus role="combobox" aria-expanded="true" aria-controls="palette-list" aria-activedescendant={entries.length ? 'palette-' + current : undefined}
        aria-label="Escribe una pantalla, un folio o un texto" placeholder="Escribe una pantalla, un folio o un texto…" value={query}
        onChange={e => {setQuery(e.target.value); setSelected(0);}} onKeyDown={keys}/>
      <kbd>Esc</kbd>
    </div>
    <ul className="palette-list" id="palette-list" role="listbox" aria-label="Resultados">
      {entries.length === 0 && <li className="palette-section" role="presentation">Sin resultados. Prueba con otra palabra.</li>}
      {entries.map((e, i) => {
        const heading = e.section !== lastSection ? <li className="palette-section" role="presentation" key={'s' + e.section}>{e.section}</li> : null;
        lastSection = e.section;
        return [heading, <li key={e.id} role="presentation">
          <button type="button" id={'palette-' + i} role="option" tabIndex={-1} aria-selected={i === current} className="palette-item"
            onMouseMove={() => setSelected(i)} onClick={() => choose(e)}>
            <Icon name={e.icon}/>{e.label}{e.hint && <small>{e.hint}</small>}
          </button>
        </li>];
      })}
    </ul>
    <div className="palette-foot"><span><kbd>↑</kbd> <kbd>↓</kbd> para moverte</span><span><kbd>Enter</kbd> para abrir</span><span>Las búsquedas respetan tus permisos.</span></div>
  </dialog>;
}
