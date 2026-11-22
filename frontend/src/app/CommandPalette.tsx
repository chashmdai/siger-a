import {useEffect, useMemo, useRef, useState} from 'react';
import type {KeyboardEvent} from 'react';
import {Icon} from '../components/Icon';
import type {IconName} from '../components/Icon';
import {useExit} from '../components/useExit';
import type {MenuGroup} from './menu';
import {openSearch, paletteEntry} from '../features/search/searchModel';
import {boardEntries, openBoardSearch} from '../features/worklist/boardSearch';

type Entry = {id: string; section: string; label: string; hint?: string; icon: IconName; route: string; run?: () => void};

const normalize = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('es-CL');

/**
 * Paleta para ir a cualquier pantalla o buscar sin recorrer menus (Ctrl+K). Solo navega: no lista resultados.
 * "Buscar persona o folio" deja el termino en la pestana y abre #buscar (sin el termino en la ruta); un folio
 * exacto abre su ficha desde esa vista. La busqueda en bandejas usa el alcance autorizado de la sesion y, como el
 * buscador, deja el texto en la pestana y abre la bandeja sin el texto en la ruta (G08c).
 */
export function CommandPalette({groups, canSearch, close}: {groups: MenuGroup[]; canSearch: boolean; close: () => void}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [query, setQuery] = useState(''), [selected, setSelected] = useState(0);
  const [leaving, exit] = useExit(close, 120);
  useEffect(() => {ref.current?.showModal();}, []);

  const entries = useMemo(() => {
    const available = new Set(groups.flatMap(g => g.items.map(i => i.path)));
    const q = normalize(query.trim());
    const pages: Entry[] = groups.flatMap(g => g.items.map(i => ({id: 'go:' + i.path, section: g.title, label: i.label, hint: i.hint, icon: i.icon, route: i.path})))
      .filter(e => !q || normalize(e.label + ' ' + (e.hint ?? '') + ' ' + e.section).includes(q));
    const searches: Entry[] = boardEntries(query, available).map(b => ({
      id: 'search:' + b.path, section: 'Buscar en bandejas', label: b.label, icon: 'search' as IconName, route: b.route, run: () => openBoardSearch(b.path, b.query),
    }));
    const person = paletteEntry(query, canSearch);
    const people: Entry[] = person && 'term' in person ? [{id: 'person', section: 'Buscador', label: person.label, icon: 'user', route: person.route, run: () => openSearch(person.term)}] : [];
    // Un folio escrito (por ejemplo REQ-...) prioriza la busqueda sobre la navegacion.
    return [...people, ...(/\d/.test(query) ? [...searches, ...pages] : [...pages, ...searches])];
  }, [groups, query, canSearch]);
  const shortHint = (() => {const p = paletteEntry(query, canSearch); return p && 'hint' in p ? p.hint : '';})();

  const current = Math.min(selected, Math.max(0, entries.length - 1));
  function choose(e?: Entry) {
    if (!e) return;
    if (e.run) e.run();
    else location.hash = e.route;
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
        autoComplete="off" spellCheck={false} aria-label={canSearch ? 'Escribe una pantalla, un RUN, un nombre o un folio' : 'Escribe una pantalla, un folio o un texto'}
        placeholder={canSearch ? 'Escribe una pantalla, un RUN, un nombre o un folio…' : 'Escribe una pantalla, un folio o un texto…'} value={query}
        onChange={e => {setQuery(e.target.value); setSelected(0);}} onKeyDown={keys}/>
      <kbd>Esc</kbd>
    </div>
    <ul className="palette-list" id="palette-list" role="listbox" aria-label="Resultados">
      {shortHint && <li className="palette-section palette-hint" role="presentation">{shortHint}</li>}
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
