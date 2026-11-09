import {useEffect, useRef, useState} from 'react';
import type {FormEvent} from 'react';
import type {Row} from '../lib/rows';
import type {Result} from '../lib/api';
import {command} from '../lib/api';
import {Icon} from './Icon';
import {Busy, Callout, ErrorMessage} from './Primitives';
import {useExit} from './useExit';

export type Option = {value: string | number; label: string; parent?: string | number};
export type Field = {
  key: string; label: string;
  type?: 'text' | 'textarea' | 'select' | 'datetime-local' | 'checkbox' | 'password';
  required?: boolean; options?: Option[];
  /** Filtra las opciones por el valor elegido en otro campo (Option.parent). */
  dependsOn?: string;
  value?: string | number | boolean; help?: string; max?: number;
};
/** Comando que se confirma en un dialogo: tipo, registro, revision esperada y campos. */
export type Action = {title: string; type: string; id: number | null; revision: number | null; fields: Field[]; note?: string; after?: (r: Result) => void};

function initialData(fields: Field[]): Row {
  return Object.fromEntries(fields.map(f => [f.key, f.value ?? (f.type === 'checkbox' ? false : '')]));
}

/** Contador visible en textos largos; se destaca al acercarse al limite. */
function Counter({value, max}: {value: string; max: number}) {
  if (!value) return null;
  return <span className={'field-counter' + (value.length > max * .9 ? ' near' : '')} aria-hidden="true">{value.length.toLocaleString('es-CL')} / {max.toLocaleString('es-CL')}</span>;
}

/**
 * Dialogo modal de confirmacion. La clave de operacion se genera una vez por dialogo:
 * reintentar tras un error de red reutiliza la misma clave y no duplica el registro.
 */
export function ActionDialog({action, close, completed}: {action: Action; close: () => void; completed: (r: Result) => void}) {
  const [data, setData] = useState<Row>(() => initialData(action.fields));
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [key] = useState(() => crypto.randomUUID());
  const dialog = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close);
  useEffect(() => {dialog.current?.showModal();}, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const payload = {...data};
      for (const f of action.fields) if (f.type === 'datetime-local') payload[f.key] = data[f.key] ? new Date(String(data[f.key])).toISOString() : null;
      const r = await command(action.type, action.id, action.revision, payload, key);
      (action.after ?? completed)(r);
      exit();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  /** Al cambiar un select se limpian los campos que dependen de el. */
  function selectChanged(f: Field, value: string) {
    setData(previous => ({...previous, [f.key]: value, ...Object.fromEntries(action.fields.filter(x => x.dependsOn === f.key).map(x => [x.key, '']))}));
  }
  function control(f: Field) {
    if (f.type === 'select') {
      const options = f.options?.filter(o => !f.dependsOn || String(o.parent) === String(data[f.dependsOn] ?? '')) ?? [];
      return <select required={f.required} value={String(data[f.key] ?? '')} onChange={e => selectChanged(f, e.target.value)}>
        <option value="">{f.dependsOn && !data[f.dependsOn] ? 'Elige primero el campo anterior' : f.required ? 'Seleccionar' : 'Sin especificar'}</option>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>;
    }
    if (f.type === 'textarea') return <>
      <textarea required={f.required} maxLength={f.max ?? 12000} rows={4} value={String(data[f.key] ?? '')} onChange={e => setData({...data, [f.key]: e.target.value})}/>
      <Counter value={String(data[f.key] ?? '')} max={f.max ?? 12000}/>
    </>;
    if (f.type === 'checkbox') return <><input type="checkbox" required={f.required} checked={data[f.key] === true} onChange={e => setData({...data, [f.key]: e.target.checked})}/><span>{f.label}{f.required && <span className="required-mark" aria-hidden="true">*</span>}</span></>;
    return <input type={f.type ?? 'text'} required={f.required} maxLength={f.max ?? 1000} value={String(data[f.key] ?? '')} autoComplete={f.type === 'password' ? 'new-password' : 'off'} onChange={e => setData({...data, [f.key]: e.target.value})}/>;
  }
  const anyRequired = action.fields.some(f => f.required);

  return <dialog ref={dialog} className={'modal' + (leaving ? ' closing' : '')} aria-labelledby="action-title" onCancel={e => {e.preventDefault(); if (!busy) exit();}}>
    <form onSubmit={submit}>
      <header className="modal-head">
        <div>
          <p className="eyebrow">Confirmar acción</p>
          <h2 id="action-title">{action.title}</h2>
        </div>
        <button type="button" className="ghost icon-only" aria-label="Cerrar sin registrar" disabled={busy} onClick={exit}><Icon name="close"/></button>
      </header>
      <div className="modal-body">
        {action.note && <Callout>{action.note}</Callout>}
        {action.fields.length === 0 && <p>Esta acción no requiere datos adicionales. Confirma para registrarla.</p>}
        {action.fields.map(f => <label key={f.key} className={f.type === 'checkbox' ? 'check' : ''}>
          {f.type !== 'checkbox' && <span>{f.label}{f.required && <span className="required-mark" aria-hidden="true">*</span>}</span>}
          {control(f)}
          {f.help && <small>{f.help}</small>}
        </label>)}
        {action.fields.some(f => f.type === 'datetime-local') && <p className="field-help">Fechas en la zona horaria de este equipo ({Intl.DateTimeFormat().resolvedOptions().timeZone}). Se guardan en UTC.</p>}
        {anyRequired && <p className="field-help"><span className="required-mark" aria-hidden="true">*</span> Campo obligatorio.</p>}
        {error && <ErrorMessage>{error}</ErrorMessage>}
      </div>
      <footer className="modal-foot">
        <small>Si la red falla, vuelve a confirmar sin cambiar los datos: se usa la misma clave y no se duplica el registro.</small>
        <div className="actions">
          <button type="button" disabled={busy} onClick={exit}>Cancelar</button>
          <button className="primary" disabled={busy}><Busy busy={busy} label="Registrando…" idle={<><Icon name="check" size={18}/>Confirmar</>}/></button>
        </div>
      </footer>
    </form>
  </dialog>;
}
