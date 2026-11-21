import {useId, useState} from 'react';
import type {KeyboardEvent, RefObject} from 'react';
import {Icon} from '../../components/Icon';
import {MAX_LENGTH, SHORT_HINT, tooShort} from './search';

/**
 * Campo unico del buscador (Inicio y vista #buscar). Busca solo al presionar Enter o el boton: no hay busqueda mientras
 * se escribe ni sugerencias. autoComplete="off" y sin atributo name: el navegador no guarda historial de terminos.
 */
export function SearchField({initial = '', onSearch, inputRef, onArrowDown, autoFocus, compact}: {
  initial?: string; onSearch: (term: string) => void; inputRef?: RefObject<HTMLInputElement | null>; onArrowDown?: () => void; autoFocus?: boolean; compact?: boolean;
}) {
  const id = useId();
  const [value, setValue] = useState(initial), [hint, setHint] = useState(false);
  function submit() {
    if (!value.trim()) return;
    if (tooShort(value)) {setHint(true); return;}
    setHint(false);
    onSearch(value.trim());
  }
  function keys(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown' && onArrowDown) {e.preventDefault(); onArrowDown();}
  }
  return <form role="search" className={'search-field' + (compact ? ' compact' : '')} aria-label="Buscar persona o folio" onSubmit={e => {e.preventDefault(); submit();}}>
    <label htmlFor={id} className="sr-only">Buscar persona o folio</label>
    <div className="search-field-row">
      <span className="search-field-input">
        <Icon name="search"/>
        <input id={id} ref={inputRef} type="search" autoComplete="off" spellCheck={false} maxLength={MAX_LENGTH} autoFocus={autoFocus}
          placeholder="RUN, nombre, documento o folio" aria-describedby={id + '-help'} value={value}
          onChange={e => {setValue(e.target.value); if (hint && !tooShort(e.target.value)) setHint(false);}} onKeyDown={keys}/>
      </span>
      <button type="submit" className="primary">Buscar</button>
    </div>
    <small id={id + '-help'} className={'field-help' + (hint ? ' search-hint' : '')} aria-live="polite">
      {hint ? SHORT_HINT : 'Por ejemplo 12.345.678-5, Ana Pérez o REQ-A-000012. Solo verás lo que tus funciones permiten leer.'}
    </small>
  </form>;
}
