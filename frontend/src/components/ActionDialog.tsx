import {useEffect, useRef, useState} from 'react';
import type {FormEvent, ReactNode} from 'react';
import type {Row} from '../lib/rows';
import type {Result} from '../lib/api';
import {command} from '../lib/api';
import {Icon} from './Icon';
import {Busy, Callout, ErrorMessage} from './Primitives';
import {useExit} from './useExit';
import {DestinationPicker} from './DestinationPicker';
import type {Choice, Destinations} from '../lib/destination';
import {initialChoice} from '../lib/destination';
import {payloadOf, signatureOf, submitStep} from '../lib/actionData';

export type Option = {value: string | number; label: string; parent?: string | number};
export type Field = {
  key: string; label: string;
  type?: 'text' | 'textarea' | 'select' | 'date' | 'datetime-local' | 'checkbox' | 'password' | 'destination' | 'checklist' | 'custom';
  required?: boolean; options?: Option[];
  /** Filtra las opciones por el valor elegido en otro campo (Option.parent). */
  dependsOn?: string;
  /** checklist: valores marcados (string[]); se envian como lista, en el orden de sus opciones. */
  value?: string | number | boolean | string[]; help?: string; max?: number;
  /** datetime-local: instante exacto (ms) del valor inicial; si no se cambia, se reenvia sin redondear a minutos. */
  exact?: number | null;
  /** Selector de destino (type 'destination'): sus datos se envian con las claves de cada comando (payloadFor). */
  destination?: {destinations: Destinations; region?: string | null};
  /**
   * Campo propio (type 'custom', 0.8.0, E09): dibuja su control y guarda en los datos un valor ya listo para enviar (un
   * objeto o una lista; undefined = no se envia). Entra en la revision previa y en el envio como cualquier otro dato.
   */
  render?: (value: unknown, set: (value: unknown) => void, disabled: boolean, data: Row) => ReactNode;
  /** Valor inicial de un campo propio. */
  initial?: unknown;
  /** Validacion de un campo propio antes de enviar: mensaje en lenguaje llano, o null. */
  validate?: (value: unknown, data: Row) => string | null;
};
/**
 * Revision previa al envio (opcional): lo que la orden provocara, calculado por el servidor sin escribir. extra se suma
 * a los datos del comando (por ejemplo, la confirmacion exacta que el servidor exige) y confirm, si viene, es una
 * casilla obligatoria para confirmar expresamente.
 */
export type Review = {content: ReactNode; extra?: Row; confirm?: string};
/** Comando que se confirma en un dialogo: tipo, registro, revision esperada y campos. */
export type Action = {title: string; type: string; id: number | null; revision: number | null; fields: Field[]; note?: string; after?: (r: Result) => void;
  /** Rechazo del servidor: el dialogo sigue abierto con su mensaje (por ejemplo, para recargar la pantalla de fondo). */
  failed?: (e: Error & {status?: number}) => void;
  /**
   * Revision antes de enviar (0.7.0, efecto territorial): el primer Continuar la pide y la muestra; el envio lleva sus
   * datos extra. Cambiar los datos la invalida y un rechazo la descarta: hay que revisar de nuevo. null = no hace falta.
   */
  review?: (payload: Row) => Promise<Review | null>;
  /** Dialogo ancho (por ejemplo, la clasificacion de derechos con su arbol). */
  wide?: boolean};

function initialData(fields: Field[]): Row {
  return Object.fromEntries(fields.map(f => [f.key, f.type === 'custom' ? f.initial : f.destination ? initialChoice(f.destination.destinations, f.destination.region)
    : f.value ?? (f.type === 'checkbox' ? false : f.type === 'checklist' ? [] : '')]));
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
  // Revision vigente solo para los datos con que se pidio (signature); cambiar un dato la deja sin efecto.
  const [review, setReview] = useState<(Review & {signature: string}) | null>(null), [confirmed, setConfirmed] = useState(false);
  // Solo los dialogos con revision la calculan; los demas se comportan como antes.
  const signature = action.review ? signatureOf(action.fields, data) : '';
  const current = review && review.signature === signature ? review : null;
  const dialog = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close);
  useEffect(() => {dialog.current?.showModal();}, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    // Campos propios: su validacion se informa en el dialogo, sin enviar.
    const invalid = action.fields.map(f => f.validate?.(data[f.key], data) ?? null).find(Boolean);
    if (invalid) {setError(invalid); return;}
    setBusy(true);
    setError('');
    try {
      const payload = payloadOf(action.fields, data);
      const next = submitStep(payload, action.review != null, current, confirmed);
      if (next.step === 'confirm') {setError('Confirma expresamente el efecto antes de registrar la orden.'); return;}
      // Primero la revision (sin escrituras): se muestra y el envio queda para el siguiente Confirmar.
      if (next.step === 'review') {
        const found = await action.review!(payload);
        if (found) {setReview({...found, signature}); setConfirmed(false); return;}
      }
      const r = await command(action.type, action.id, action.revision, next.step === 'send' ? next.payload : payload, key);
      (action.after ?? completed)(r);
      exit();
    } catch (e) {
      setError((e as Error).message);
      // Un rechazo descarta la revision: la situacion pudo cambiar y hay que revisarla de nuevo antes de reenviar.
      if (action.review) setReview(null);
      action.failed?.(e as Error & {status?: number});
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
        {/* Si una opcion ya usa el valor vacio (por ejemplo "Nacional"), no se agrega el marcador. */}
        {!options.some(o => o.value === '') && <option value="">{f.dependsOn && !data[f.dependsOn] ? 'Elige primero el campo anterior' : f.required ? 'Seleccionar' : 'Sin especificar'}</option>}
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>;
    }
    if (f.type === 'textarea') return <>
      <textarea required={f.required} maxLength={f.max ?? 12000} rows={4} value={String(data[f.key] ?? '')} onChange={e => setData({...data, [f.key]: e.target.value})}/>
      <Counter value={String(data[f.key] ?? '')} max={f.max ?? 12000}/>
    </>;
    if (f.type === 'checklist') {
      const marked = (data[f.key] as string[] | undefined) ?? [];
      return <div className="checklist">{(f.options ?? []).map(o => <label key={o.value} className="check">
        <input type="checkbox" checked={marked.includes(String(o.value))} onChange={e => setData(previous => {
          const rest = ((previous[f.key] as string[] | undefined) ?? []).filter(v => v !== String(o.value));
          return {...previous, [f.key]: e.target.checked ? [...rest, String(o.value)] : rest};
        })}/><span>{o.label}</span>
      </label>)}</div>;
    }
    if (f.type === 'checkbox') return <><input type="checkbox" required={f.required} checked={data[f.key] === true} onChange={e => setData({...data, [f.key]: e.target.checked})}/><span>{f.label}{f.required && <span className="required-mark" aria-hidden="true">*</span>}</span></>;
    return <input type={f.type ?? 'text'} required={f.required} maxLength={f.max ?? 1000} value={String(data[f.key] ?? '')} autoComplete={f.type === 'password' ? 'new-password' : 'off'} onChange={e => setData({...data, [f.key]: e.target.value})}/>;
  }
  const anyRequired = action.fields.some(f => f.required);

  return <dialog ref={dialog} className={'modal' + (action.wide ? ' wide' : '') + (leaving ? ' closing' : '')} aria-labelledby="action-title" onCancel={e => {e.preventDefault(); if (!busy) exit();}}>
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
        {action.fields.map(f => f.type === 'custom'
          ? <div key={f.key} className="field-custom">{f.render?.(data[f.key], v => setData(previous => ({...previous, [f.key]: v})), busy, data)}</div>
          : f.destination
          ? <DestinationPicker key={f.key} destinations={f.destination.destinations} region={f.destination.region} value={data[f.key] as Choice} disabled={busy} onChange={c => setData(previous => ({...previous, [f.key]: c}))}/>
          : f.type === 'checklist' ? <fieldset key={f.key} className="field-group" disabled={busy}>
            <legend>{f.label}</legend>
            {control(f)}
            {f.help && <small className="field-help">{f.help}</small>}
          </fieldset>
          : <label key={f.key} className={f.type === 'checkbox' ? 'check' : ''}>
          {f.type !== 'checkbox' && <span>{f.label}{f.required && <span className="required-mark" aria-hidden="true">*</span>}</span>}
          {control(f)}
          {f.help && <small>{f.help}</small>}
        </label>)}
        {action.fields.some(f => f.type === 'datetime-local') && <p className="field-help">Fechas en la zona horaria de este equipo ({Intl.DateTimeFormat().resolvedOptions().timeZone}). Se guardan en UTC.</p>}
        {anyRequired && <p className="field-help"><span className="required-mark" aria-hidden="true">*</span> Campo obligatorio.</p>}
        {review && !current && <Callout tone="info" role="status">Cambiaste los datos: al continuar se revisa de nuevo el efecto de la orden.</Callout>}
        {current && <section className="action-review" aria-label="Efecto de la orden" role="status">
          {current.content}
          {current.confirm && <label className="check"><input type="checkbox" required checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)}/><span>{current.confirm}<span className="required-mark" aria-hidden="true">*</span></span></label>}
        </section>}
        {error && <ErrorMessage>{error}</ErrorMessage>}
      </div>
      <footer className="modal-foot">
        <small>Si la red falla, vuelve a confirmar sin cambiar los datos: se usa la misma clave y no se duplica el registro.</small>
        <div className="actions">
          <button type="button" disabled={busy} onClick={exit}>Cancelar</button>
          {action.review && !current
            ? <button className="primary" disabled={busy}><Busy busy={busy} label="Revisando…" idle={<>Continuar<Icon name="right" size={18}/></>}/></button>
            : <button className="primary" disabled={busy}><Busy busy={busy} label="Registrando…" idle={<><Icon name="check" size={18}/>Confirmar</>}/></button>}
        </div>
      </footer>
    </form>
  </dialog>;
}
