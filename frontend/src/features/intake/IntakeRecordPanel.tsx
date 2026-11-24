import {useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {command, request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {bool, list, number, obj, text, when} from '../../lib/rows';
import {nameOf} from '../../lib/catalog';
import type {Context} from '../../app/context';
import {LabeledField} from '../../components/LabeledField';
import {Icon} from '../../components/Icon';
import {Busy, Callout, ErrorMessage, Panel} from '../../components/Primitives';
import {orderedRegions} from '../../lib/person/catalog.ts';
import {changeSummaryText, statCodes} from '../../lib/person/personModel.ts';
import type {IntakeControls} from './IntakeFields';
import {FactsAndInstitutions, PersonsFields, PresenterFields} from './IntakeFields';
import {IntakePreview, IntakeSummary} from './IntakePreview';
import type {IntakeData, PresenterKind} from './intakeModel.ts';
import {completePayload, intakeFromSaved, intakeProblems, presenterKindOf} from './intakeModel.ts';

/**
 * Ficha de ingreso de una COM manual (0.8.0, D33, gate E04): datos vigentes, versiones con su resumen de cambios y, con
 * can_complete (cadena abierta y facultad, calculadas por el servidor), complementacion o rectificacion con motivo
 * (COM_COMPLETE, id = COM y su revision). La clave de operacion se conserva para reintentar un resultado incierto.
 */
export function IntakeRecordPanel({r, ctx}: {r: Row; ctx: Context}) {
  const info = obj(r, 'intake'), saved = obj(info, 'data');
  const codes = statCodes(ctx.catalog), regions = orderedRegions(list(ctx.catalog, 'regions'));
  const current = intakeFromSaved(saved);
  const [editing, setEditing] = useState(false), [data, setData] = useState<IntakeData>(current), [kind, setKind] = useState<PresenterKind>(() => presenterKindOf(current));
  const [reason, setReason] = useState(''), [touched, setTouched] = useState<Set<string>>(() => new Set()), [attempted, setAttempted] = useState(false);
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [version, setVersion] = useState<Row | null>(null);
  const key = useRef(crypto.randomUUID());
  const problems = intakeProblems(data, kind, saved, codes);
  const c: IntakeControls = {data, previous: current, update: setData, kind, setKind, codes, regions,
    error: k => problems[k] && (attempted || touched.has(k)) ? problems[k] : undefined, touch: k => setTouched(old => old.has(k) ? old : new Set(old).add(k))};

  function start() {setData(current); setKind(presenterKindOf(current)); setReason(''); setTouched(new Set()); setAttempted(false); setError(''); key.current = crypto.randomUUID(); setEditing(true);}
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (Object.keys(problems).length) {setAttempted(true); setError('Revisa los datos marcados. Puedes dejarlos en blanco si aún no se conocen.'); return;}
    if (!reason.trim()) {setError('Indica el motivo de la complementación o rectificación.'); return;}
    setBusy(true);
    setError('');
    try {
      const result = await command('COM_COMPLETE', number(r, 'id'), number(r, 'revision'), completePayload(data, kind, reason), key.current);
      setUncertain(false);
      setEditing(false);
      ctx.message(result.message);
      ctx.reload();
    } catch (err) {
      const failure = err as Error & {status?: number};
      setError(failure.message);
      // Sin respuesta o error 5xx: solo se permite reintentar la misma operacion, con la misma clave.
      setUncertain(!failure.status || failure.status >= 500);
    } finally {
      setBusy(false);
    }
  }
  async function open(id: number) {
    try {
      setVersion(await request(`/intake/${number(r, 'id')}/versions/${id}`));
    } catch (err) {
      setError((err as Error).message);
    }
  }

  const versions = list(info, 'versions');
  return <Panel title="Ficha de ingreso" icon="user" description="Datos declarados al registrar el ingreso, con sus complementaciones y autoría conservadas."
    actions={!editing && bool(info, 'can_complete') ? <button onClick={start}><Icon name="edit" size={18}/>Completar o rectificar</button> : undefined}>
    {!editing ? <IntakeSummary data={current} codes={codes}/> : <form noValidate onSubmit={submit} className="intake-complete">
      <Callout tone="info" title="Editando la ficha de ingreso">Al guardar se crea una nueva versión con tu nombre y el motivo que indiques. La versión anterior se conserva.</Callout>
      <fieldset disabled={busy || uncertain} className="attention-fields">
        <legend className="sr-only">Ficha de ingreso</legend>
        <h3 className="form-subtitle">Quién presenta y contacto</h3>
        <PresenterFields c={c}/>
        <h3 className="form-subtitle">Personas relacionadas</h3>
        <PersonsFields c={c}/>
        <FactsAndInstitutions c={c}/>
        <LabeledField label="Motivo de esta complementación o rectificación" required help="Queda registrado junto a la nueva versión.">
          <input required maxLength={2000} value={reason} onChange={e => setReason(e.target.value)}/>
        </LabeledField>
      </fieldset>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {uncertain && <Callout tone="warn" title="No se conoce el resultado">Reintenta con la misma clave, sin modificar la captura. No se generará una segunda versión.</Callout>}
      <div className="actions">
        <button type="button" className="ghost" disabled={busy} onClick={() => {setEditing(false); setUncertain(false); setError('');}}>Cancelar</button>
        <button className="primary" disabled={busy}><Busy busy={busy} label="Guardando…" idle={<><Icon name={uncertain ? 'refresh' : 'check'} size={18}/>{uncertain ? 'Reintentar la misma operación' : 'Guardar nueva versión'}</>}/></button>
      </div>
    </form>}
    {!editing && error && <ErrorMessage>{error}</ErrorMessage>}
    {versions.length > 0 && <details className="disclosure">
      <summary><Icon name="history" size={18}/>Versiones de la ficha de ingreso <small className="muted">({versions.length})</small><Icon name="down" size={18}/></summary>
      <div className="disclosure-body"><ol className="timeline compact">
        {versions.map(v => <li key={number(v, 'id')}>
          <div className="timeline-head"><strong>{text(v, 'author')}</strong><span className="timeline-meta">{when(v.registered_ms)}</span></div>
          {text(v, 'reason') && <p className="timeline-body">{text(v, 'reason')}</p>}
          {changeSummaryText(v.change_summary) && <p className="timeline-meta">{changeSummaryText(v.change_summary)}</p>}
          <div className="timeline-actions"><button className="sm" onClick={() => void open(number(v, 'id'))}><Icon name="eye" size={16}/>Ver esta versión</button></div>
        </li>)}
      </ol></div>
    </details>}
    {version && <IntakePreview version={version} folio={text(r, 'folio')} author={nameOf(ctx.catalog, version.author_id)} codes={codes} close={() => setVersion(null)}/>}
  </Panel>;
}
