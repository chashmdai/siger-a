import {useEffect, useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {command, request} from '../../lib/api';
import type {Result} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {bool, list, localTime, number, obj, text, when} from '../../lib/rows';
import {registersAt} from '../../lib/access';
import {nameOf, scopeOf, scopes} from '../../lib/catalog';
import {CHANNELS} from '../../lib/fields';
import {CHANNEL_LABELS, labeled} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Status} from '../../components/Status';
import {LabeledField} from '../../components/LabeledField';
import {ChipRadios} from '../../components/Choice';
import {Icon} from '../../components/Icon';
import {Busy, Callout, ErrorMessage, PageHeader, Panel} from '../../components/Primitives';
import {reducedMotion} from '../../components/useExit';
import type {AttentionData, Person} from './schema';
import {STEPS, emptyForm} from './schema';
import {controls, FirstContact, FormSection, Listening, ModalitySelect, Outcome} from './FormSections';
import {AttentionSummary} from './AttentionSummary';
import {AttentionActions} from './AttentionActions';
import {Handoffs} from './Handoffs';
import {PreparedFiles} from './PreparedFiles';
import {OriginalUpload} from './OriginalUpload';
import {Preview} from './Preview';
import {adultProblems, upgrade} from './adult/adultForm.ts';

type Pending = {type: string; id: number | null; revision: number | null; data: Row; key: string};

/** Orientacion segun el estado de continuidad calculado por el servidor. */
const NEXT_STEP: Record<string, ['info' | 'warn', string, string]> = {
  EN_ATENCION: ['info', 'Qué sigue', 'Completa la ficha a medida que obtengas datos. Si la atención quedó concluida, resuélvela; si otra persona debe continuar, entrégala.'],
  REQUIERE_CASO: ['warn', 'Requiere seguimiento', 'La ficha indica seguimiento: la jefatura principal debe abrir un Caso en este mismo REQ.'],
  PENDIENTE_RECEPCION: ['warn', 'Entrega por recibir', 'El destino debe recibirla o devolverla. Mientras tanto, la persona a cargo se mantiene y la ficha no se puede editar.'],
};

/**
 * Primera atencion progresiva. Sin row: nueva atencion (crea COM + REQ). Con row: ficha del REQ,
 * en lectura o edicion segun las facultades calculadas por el servidor (can_read / can_edit).
 */
export default function Attention({ctx, row, completed}: {ctx: Context; row?: Row; completed?: (r: Result) => void}) {
  const info = row ? obj(row, 'attention') : {};
  const isNew = !row;
  // Version guardada: se conserva intacta; el borrador en pantalla se prepara como ficha 1.1 (ver upgrade).
  const saved = row ? obj(info, 'form') : {};
  const [form, setForm] = useState<AttentionData>(() => upgrade({...emptyForm(), ...saved}) as AttentionData);
  const [touched, setTouched] = useState<Set<string>>(() => new Set()), [attempted, setAttempted] = useState(false);
  const [editing, setEditing] = useState(isNew), [scope, setScope] = useState(String(ctx.user.home_scope)), [channel, setChannel] = useState('PRESENCIAL'), [received, setReceived] = useState(localTime());
  const [step, setStep] = useState(1), [dirty, setDirty] = useState(false), [reason, setReason] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [preview, setPreview] = useState<Row | null>(null);
  const [attachments, setAttachments] = useState<string[]>([]), [unprepared, setUnprepared] = useState(false);
  // Operacion en curso: se conserva para reintentar con la misma clave si el resultado es incierto.
  const pending = useRef<Pending | null>(null);
  const top = useRef<HTMLDivElement>(null);
  const canRead = isNew || bool(info, 'can_read'), editable = isNew || bool(info, 'can_edit');

  // Proteccion de cambios sin guardar: cierre de pestana, navegacion interna y enlaces.
  useEffect(() => {
    if (!dirty && !uncertain && !busy) return;
    const warn = (e: BeforeUnloadEvent) => {e.preventDefault(); e.returnValue = '';};
    const allow = () => !busy && !uncertain && confirm('Hay cambios sin guardar. ¿Salir de esta pantalla y descartarlos?');
    const guard = (e: Event) => {if (!allow()) e.preventDefault();};
    const click = (e: MouseEvent) => {
      const link = (e.target as Element).closest?.('a[href]');
      if (link && link.getAttribute('href')?.startsWith('#') && link.getAttribute('href') !== location.hash && link.getAttribute('href') !== '#contenido' && !allow()) {e.preventDefault(); e.stopPropagation();}
    };
    window.addEventListener('beforeunload', warn);
    window.addEventListener('siger-before-navigation', guard);
    document.addEventListener('click', click, true);
    return () => {
      window.removeEventListener('beforeunload', warn);
      window.removeEventListener('siger-before-navigation', guard);
      document.removeEventListener('click', click, true);
    };
  }, [dirty, uncertain, busy]);

  const change = (key: string, value: string) => {setForm(old => ({...old, [key]: value})); setDirty(true);};
  const setPeople = (update: (people: Person[]) => Person[]) => {setForm(old => ({...old, people: update(old.people)})); setDirty(true);};
  const problems = adultProblems(form, saved);
  const touch = (key: string) => setTouched(old => old.has(key) ? old : new Set(old).add(key));
  const c = controls(form, change, setPeople, saved, problems, key => attempted || touched.has(key), touch);

  function goStep(n: number) {
    setStep(n);
    top.current?.scrollIntoView({behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start'});
  }
  async function execute() {
    const p = pending.current;
    if (!p) return;
    setBusy(true);
    setError('');
    try {
      const result = await command(p.type, p.id, p.revision, p.data, p.key);
      pending.current = null;
      setUncertain(false);
      setDirty(false);
      setEditing(false);
      if (completed) completed(result);
      else {ctx.message(result.message); ctx.reload();}
    } catch (e) {
      const failure = e as Error & {status?: number};
      setError(failure.message);
      // Sin respuesta o error 5xx: no se sabe si quedo registrado; solo se permite reintentar igual.
      const ambiguous = !failure.status || failure.status >= 500;
      setUncertain(ambiguous);
      if (!ambiguous) pending.current = null;
    } finally {
      setBusy(false);
    }
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    if (unprepared) {setError('Prepara el archivo seleccionado o retíralo antes de guardar.'); return;}
    if (!String(form.motive).trim()) {setError('Escribe un motivo breve. No necesitas RUN ni todos los datos personales.'); goStep(1); return;}
    if (Object.keys(problems).length) {setAttempted(true); setError('Revisa los datos marcados en Primer abordaje. Puedes dejarlos en blanco si aún no se conocen.'); goStep(1); return;}
    if (isNew && (!scope || !received || Number.isNaN(new Date(received).getTime()))) {setError('Selecciona el ámbito y una fecha de recepción válida.'); goStep(1); return;}
    if (!isNew && !reason.trim()) {setError('Indica el motivo de la complementación.'); return;}
    const data: Row = {form};
    if (isNew) Object.assign(data, {scope_id: Number(scope), channel, received_at: new Date(received).toISOString(), attachment_tokens: attachments});
    else data.reason = reason;
    pending.current = {type: isNew ? 'ATTENTION_CREATE' : 'ATTENTION_SAVE', id: row ? number(row, 'id') : null, revision: row ? number(row, 'revision') : null, data, key: crypto.randomUUID()};
    void execute();
  }
  async function oldVersion(id: number) {
    try {
      const v = await request(`/attention/${number(row ?? {}, 'id')}/versions/${id}`);
      setPreview({...v, origin: info.origin, folio: row?.folio, author_name: nameOf(ctx.catalog, v.author_id)});
    } catch (e) {
      setError((e as Error).message);
    }
  }
  function cancelEditing() {
    if (!dirty || confirm('¿Descartar los cambios no guardados de esta pantalla?')) {
      setForm({...emptyForm(), ...obj(info, 'form')} as AttentionData);
      setDirty(false);
      setEditing(false);
      setError('');
    }
  }

  if (!canRead) return <Panel className="attention-record" title="Continuidad de tu primera atención" icon="heart" actions={<Status value={info.state}/>}>
    <p>Registraste el ingreso. La ficha complementada queda disponible solo para quienes tienen actualmente su atención o gestión a cargo.</p>
    <dl className="attention-context">
      <div><dt>Encargo actual</dt><dd>{nameOf(ctx.catalog, row?.attention_user)}</dd></div>
      <div><dt>Jefatura principal</dt><dd>{scopeOf(ctx.catalog, row?.principal_scope)}</dd></div>
    </dl>
    <Handoffs info={info} row={row!} ctx={ctx}/>
  </Panel>;

  const intakeContext = isNew ? <FormSection title="Contexto del ingreso" icon="inbox" lead="La identidad y las facultades de quien registra provienen de su sesión; no se elige un rol para obtener permisos.">
    <div className="form-grid">
      <LabeledField label="Ámbito donde se atiende" required><select required value={scope} onChange={e => {setScope(e.target.value); setAttachments([]); setDirty(true);}}>{scopes(ctx.catalog, s => registersAt(ctx.user, number(s, 'id'))).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></LabeledField>
      <LabeledField label="Recepción efectiva" required><input type="datetime-local" required value={received} onChange={e => {setReceived(e.target.value); setDirty(true);}}/></LabeledField>
    </div>
    <ChipRadios legend="Canal" value={channel} options={labeled(CHANNELS, CHANNEL_LABELS)} onChange={v => {setChannel(v); setDirty(true);}}/>
    <ModalitySelect c={c}/>
  </FormSection> : undefined;
  const locked = busy || uncertain;
  const motiveReady = String(form.motive).trim() !== '';
  const next = NEXT_STEP[text(info, 'state')];

  return <section className={'attention' + (isNew ? ' attention-new' : '')}>
    {isNew && <PageHeader eyebrow={<><Icon name="heart" size={14}/>Atención ciudadana</>} title="Nueva atención"
      lead="Registra lo esencial ahora y completa el resto cuando esté disponible. Al guardar se crean la COM y el REQ, sin SOL ni Caso automático."/>}
    {!editing ? <section className="panel attention-record">
      <div className="attention-record-head">
        <div>
          <p className="eyebrow"><Icon name="heart" size={14}/>Primera atención</p>
          <h2>Ficha de primera atención</h2>
          <p className="muted">Una sola atención, con sus complementaciones y autoría conservadas.</p>
        </div>
        <Status value={info.state}/>
      </div>
      <dl className="attention-context">
        <div><dt>A cargo de</dt><dd>{nameOf(ctx.catalog, row?.attention_user)}</dd></div>
        <div><dt>Ámbito de atención</dt><dd>{scopeOf(ctx.catalog, row?.attention_scope)}</dd></div>
        <div><dt>Jefatura principal</dt><dd>{scopeOf(ctx.catalog, row?.principal_scope)}</dd></div>
      </dl>
      {next && <Callout tone={next[0]} title={next[1]}>{next[2]}</Callout>}
      <div className="actions">
        {editable && <button className="primary" onClick={() => {setEditing(true); setStep(1);}}><Icon name="edit" size={18}/>Completar o rectificar ficha</button>}
        <button onClick={() => setPreview({form, origin: info.origin, folio: row?.folio, author_name: nameOf(ctx.catalog, info.updated_by)})}><Icon name="printer" size={18}/>Ver ficha completa o imprimir</button>
      </div>
      <AttentionSummary form={form}/>
    </section> : <form noValidate onSubmit={submit} className="attention-editor">
      <div className="attention-layout" ref={top}>
        <nav className="stepper" aria-label="Secciones de la ficha">
          <ol>
            {STEPS.map((s, i) => <li key={s.title}>
              <button type="button" aria-current={step === i + 1 ? 'step' : undefined} onClick={() => goStep(i + 1)}>
                <span className="step-number" aria-hidden="true">{i === 0 && motiveReady ? <Icon name="check" size={16}/> : i + 1}</span>
                <span className="step-text"><strong>{s.title}</strong><small>{s.text}</small></span>
              </button>
            </li>)}
          </ol>
          <div className="stepper-note">
            <p><Icon name="user" size={16}/>Registra: <strong>{ctx.user.name}</strong></p>
            <p>Un campo vacío significa «no registrado», no una respuesta negativa.</p>
          </div>
        </nav>
        <div className="attention-panes">
          {!isNew && <Callout tone="info" title="Editando la ficha">Al guardar se crea una nueva versión con tu nombre y el motivo que indiques. La versión anterior se conserva.</Callout>}
          <fieldset disabled={locked} className="attention-fields">
            <legend className="sr-only">Registro de {ctx.user.name}</legend>
            <div className="step-pane" hidden={step !== 1}><FirstContact c={c} intake={intakeContext}/></div>
            <div className="step-pane" hidden={step !== 2}><Listening c={c}/></div>
            <div className="step-pane" hidden={step !== 3}><Outcome c={c}/></div>
            {!isNew && <section className="panel form-section">
              <LabeledField label="Motivo de esta complementación o rectificación" required help="Queda registrado junto a la nueva versión."><input required maxLength={2000} value={reason} onChange={e => {setReason(e.target.value); setDirty(true);}}/></LabeledField>
            </section>}
          </fieldset>
          {isNew && <PreparedFiles ctx={ctx} scope={Number(scope)} selected={attachments} select={v => {setAttachments(v); setDirty(true);}} disabled={locked} pendingFile={setUnprepared}/>}
          {error && <ErrorMessage>{error}</ErrorMessage>}
          {uncertain && <Callout tone="warn" title="No se conoce el resultado">Reintenta con la misma clave, sin modificar la captura. No se generará un segundo ingreso.</Callout>}
        </div>
      </div>
      <div className="save-bar" role="region" aria-label="Guardar ficha">
        <span className={'save-state' + (dirty ? ' dirty' : '')}><span className="save-dot" aria-hidden="true"/>{dirty ? 'Cambios sin guardar' : 'Sin cambios pendientes'}<small>Puedes guardar desde cualquier sección.</small></span>
        <div className="actions">
          {!isNew && <button type="button" className="ghost" disabled={locked} onClick={cancelEditing}>Cancelar edición</button>}
          <button type="button" disabled={step === 1 || locked} onClick={() => goStep(step - 1)} aria-label="Sección anterior"><Icon name="left" size={18}/><span className="hide-sm">Anterior</span></button>
          <button type="button" disabled={step === 3 || locked} onClick={() => goStep(step + 1)} aria-label="Sección siguiente"><span className="hide-sm">Siguiente</span><Icon name="right" size={18}/></button>
          {uncertain
            ? <button type="button" className="primary" disabled={busy} onClick={() => void execute()}><Busy busy={busy} label="Reintentando…" idle={<><Icon name="refresh" size={18}/>Reintentar la misma operación</>}/></button>
            : <button className="primary" disabled={busy}><Busy busy={busy} label="Guardando…" idle={<><Icon name="check" size={18}/>{isNew ? 'Registrar atención y crear REQ' : 'Guardar complementación'}</>}/></button>}
        </div>
      </div>
    </form>}
    {!isNew && !editing && <>
      <AttentionActions ctx={ctx} row={row} info={info}/>
      <Handoffs ctx={ctx} row={row} info={info}/>
      {bool(info, 'can_edit') && <OriginalUpload ctx={ctx} row={row}/>}
      <details className="disclosure">
        <summary><Icon name="history" size={18}/>Versiones de la ficha <small className="muted">({list(info, 'versions').length} más recientes)</small><Icon name="down" size={18}/></summary>
        <div className="disclosure-body">
          {list(info, 'versions').length === 0 ? <p className="muted">Aún no hay versiones anteriores.</p> : <ol className="timeline compact">
            {list(info, 'versions').map(v => <li key={number(v, 'id')}>
              <div className="timeline-head"><strong>{text(v, 'author')}</strong><span className="timeline-meta">{when(v.registered_ms)}</span></div>
              {text(v, 'reason') && <p className="timeline-body">{text(v, 'reason')}</p>}
              <div className="timeline-actions"><button className="sm" onClick={() => void oldVersion(number(v, 'id'))}><Icon name="eye" size={16}/>Ver versión {text(v, 'requirement_revision')}</button></div>
            </li>)}
          </ol>}
        </div>
      </details>
    </>}
    {!editing && error && <ErrorMessage>{error}</ErrorMessage>}
    {preview && <Preview data={preview} close={() => setPreview(null)}/>}
  </section>;
}
