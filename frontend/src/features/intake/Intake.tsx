import {useEffect, useRef, useState} from 'react';
import type {FormEvent, ReactNode} from 'react';
import {command, discardDraft, draftDocuments, stageIntakeDocument} from '../../lib/api';
import type {Result} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {localTime, number, text, when} from '../../lib/rows';
import {registersAt} from '../../lib/access';
import {scopeOf, scopes} from '../../lib/catalog';
import {CHANNELS} from '../../lib/fields';
import {CHANNEL_LABELS, labeled, PERSON_ROLE_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import {ChipRadios} from '../../components/Choice';
import {Icon} from '../../components/Icon';
import type {IconName} from '../../components/Icon';
import {Busy, Callout, EmptyState, ErrorMessage, PageHeader} from '../../components/Primitives';

type Person = {role: string; name: string; identity_document: string; relationship: string; location: string};
const PERSON_FIELDS = ['role', 'name', 'identity_document', 'relationship', 'location'] as const;
const PERSON_LABELS: Record<keyof Person, string> = {role: 'Relación con el ingreso', name: 'Nombre disponible', identity_document: 'Identificación disponible', relationship: 'Vínculo con el requiriente', location: 'Antecedentes de localización del NNA o persona'};
const PERSON_ROLES = ['NNA', 'ACOMPANANTE', 'OTRA_PERSONA'];
const MAX_PERSONS = 12;
const emptyPerson = (): Person => ({role: 'NNA', name: '', identity_document: '', relationship: '', location: ''});
const personMax = (k: keyof Person) => k === 'location' ? 500 : k === 'identity_document' ? 100 : 200;

const SECTIONS: [id: string, title: string, icon: IconName][] = [['recepcion', 'Recepción', 'inbox'], ['requiriente', 'Requiriente y contacto', 'user'], ['personas', 'Personas relacionadas', 'people'], ['antecedentes', 'Antecedentes', 'message'], ['documentos', 'Documentos', 'file']];

function Section({n, children, lead}: {n: number; children: ReactNode; lead?: ReactNode}) {
  const [id, title, icon] = SECTIONS[n - 1];
  return <section className="panel form-section" id={'ingreso-' + id} aria-labelledby={'ingreso-' + id + '-t'}>
    <div className="panel-head"><div>
      <h2 id={'ingreso-' + id + '-t'}><span className="section-number" aria-hidden="true">{n}</span><Icon name={icon}/>{title}</h2>
      {lead && <p>{lead}</p>}
    </div></div>
    {children}
  </section>;
}

/**
 * Registro de una Comunicacion externa por clasificar: crea solo la COM, pendiente de distribucion.
 * La clave de operacion se conserva mientras la pantalla siga abierta, para reintentos seguros.
 */
export default function Intake({ctx, completed}: {ctx: Context; completed: (r: Result) => void}) {
  const [scope, setScope] = useState(String(ctx.user.home_scope)), [channel, setChannel] = useState('PRESENCIAL'), [received, setReceived] = useState(localTime());
  const [values, setValues] = useState<Record<string, string>>({presenter: '', identity_document: '', presenter_domicile: '', contact_email: '', contact_phone: '', contact_restrictions: '', contact: '', subject: '', body: '', reason: '', result: 'ORIENTACION'});
  const [persons, setPersons] = useState<Person[]>([]), [drafts, setDrafts] = useState<Row[]>([]), [selected, setSelected] = useState<string[]>([]);
  const [file, setFile] = useState<File | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(false), [fileEpoch, setFileEpoch] = useState(0);
  const key = useRef(crypto.randomUUID()), uploadKey = useRef(crypto.randomUUID());
  useEffect(() => {
    let live = true;
    void draftDocuments().then(d => {if (live) setDrafts(d);}).catch(e => {if (live) setError((e as Error).message);});
    return () => {live = false;};
  }, [ctx.user.id]);
  const allowed = scopes(ctx.catalog, s => registersAt(ctx.user, number(s, 'id')));
  const change = (field: string, value: string) => setValues(v => ({...v, [field]: value}));
  const updatePerson = (i: number, k: keyof Person, value: string) => setPersons(rows => rows.map((p, n) => n === i ? {...p, [k]: value} : p));
  const clearFile = () => {setFile(null); setFileEpoch(x => x + 1);};

  async function prepare() {
    if (!file) return;
    setBusy(true);
    setError('');
    try {
      const d = await stageIntakeDocument(scope, uploadKey.current, file);
      setDrafts(x => [...x.filter(v => text(v, 'token') !== text(d, 'token')), {...d, scope_id: Number(scope), created_ms: Date.now()}]);
      setSelected(x => Array.from(new Set([...x, text(d, 'token')])));
      clearFile();
      uploadKey.current = crypto.randomUUID();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function remove(token: string) {
    setBusy(true);
    setError('');
    try {
      await discardDraft(token);
      setDrafts(x => x.filter(d => text(d, 'token') !== token));
      setSelected(x => x.filter(t => t !== token));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (file) {setError('Prepara el archivo elegido o retíralo antes de confirmar el ingreso.'); return;}
    setBusy(true);
    setError('');
    try {
      const payload = {...values, scope_id: scope, channel, received_at: new Date(received).toISOString(), persons, attachment_tokens: selected};
      completed(await command('COM_CREATE', null, null, payload, key.current));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function field(name: string, label: string, max: number, required = false, type = 'text', help?: string) {
    return <label><span>{label}{required && <span className="required-mark" aria-hidden="true">*</span>}</span><input type={type} required={required} maxLength={max} value={values[name]} onChange={e => change(name, e.target.value)}/>{help && <small>{help}</small>}</label>;
  }

  return <>
    <PageHeader eyebrow={<><Icon name="inbox" size={14}/>Comunicación externa</>} title="Ingreso por clasificar"
      lead="Registra lo recibido tal como llegó. Se crea solo la COM, pendiente de distribución."/>
    <Callout title="¿Estás atendiendo directamente a una persona?">Usa <a href="#atencion">Nueva atención</a>: crea la COM y el REQ desde el primer registro y permite continuar la ficha.</Callout>
    <div className="intake-layout">
      <nav className="intake-index" aria-label="Secciones del ingreso">
        <ol>{SECTIONS.map(([id, title], i) => <li key={id}><a href={'#ingreso-' + id} onClick={e => {e.preventDefault(); document.getElementById('ingreso-' + id)?.scrollIntoView({behavior: 'smooth', block: 'start'});}}><span className="section-number" aria-hidden="true">{i + 1}</span>{title}</a></li>)}</ol>
      </nav>
      <form onSubmit={submit} className="intake-form">
        <fieldset disabled={busy}>
          <Section n={1}>
            <div className="form-grid">
              <label><span>Ámbito de captura<span className="required-mark" aria-hidden="true">*</span></span><select required disabled={selected.length > 0} value={scope} onChange={e => setScope(e.target.value)}>{allowed.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select>{selected.length > 0 && <small>Los adjuntos preparados pertenecen a este ámbito. Desmárcalos antes de cambiarlo.</small>}</label>
              <label><span>Fecha y hora de recepción<span className="required-mark" aria-hidden="true">*</span></span><input type="datetime-local" value={received} required onChange={e => setReceived(e.target.value)}/></label>
            </div>
            <ChipRadios legend="Canal real" value={channel} options={labeled(CHANNELS, CHANNEL_LABELS)} onChange={setChannel}/>
          </Section>
          <Section n={2} lead="Solo lo que la persona haya entregado. Un dato ausente no impide registrar el ingreso.">
            <div className="form-grid">
              {field('presenter', 'Requiriente o presentador', 200)}
              {field('identity_document', 'Identificación declarada (opcional)', 100)}
              {field('presenter_domicile', 'Domicilio del requiriente (no el del NNA)', 500)}
              {field('contact_email', 'Correo autorizado (opcional)', 200, false, 'email')}
              {field('contact_phone', 'Teléfono autorizado (opcional)', 80)}
              {field('contact', 'Otro medio de contacto (opcional)', 300)}
            </div>
            <label>Restricciones o indicaciones de contacto seguro<textarea maxLength={1000} value={values.contact_restrictions} onChange={e => change('contact_restrictions', e.target.value)} placeholder="Por ejemplo: no llamar; solo correo autorizado."/></label>
          </Section>
          <Section n={3} lead="Opcional. No es un registro maestro de personas ni una búsqueda por RUN: conserva los antecedentes de este ingreso.">
            {persons.length === 0 && <EmptyState compact icon="people" title="Sin personas agregadas">Agrega a un NNA u otra persona solo si es pertinente para este ingreso.</EmptyState>}
            <ul className="people-list">
              {persons.map((person, i) => <li className="person-card" key={i}>
                <div className="person-card-head">
                  <h3><span className="avatar sm" aria-hidden="true">{i + 1}</span>Persona {i + 1}</h3>
                  <button type="button" className="ghost sm" onClick={() => setPersons(rows => rows.filter((_, n) => n !== i))}><Icon name="close" size={16}/>Retirar de esta captura</button>
                </div>
                <div className="form-grid">
                  {PERSON_FIELDS.map(k => <label key={k}>{PERSON_LABELS[k]}{k === 'role'
                    ? <select value={person.role} onChange={e => updatePerson(i, 'role', e.target.value)}>{PERSON_ROLES.map(v => <option key={v} value={v}>{PERSON_ROLE_LABELS[v] ?? v}</option>)}</select>
                    : <input maxLength={personMax(k)} value={person[k]} onChange={e => updatePerson(i, k, e.target.value)}/>}</label>)}
                </div>
              </li>)}
            </ul>
            <button type="button" disabled={persons.length >= MAX_PERSONS} onClick={() => setPersons(rows => [...rows, emptyPerson()])}><Icon name="plus" size={18}/>Agregar NNA o persona relacionada</button>
          </Section>
          <Section n={4}>
            {field('subject', 'Asunto', 240, true, 'text', 'Una frase que permita reconocer el ingreso en las bandejas.')}
            <label><span>Relato original y antecedentes disponibles<span className="required-mark" aria-hidden="true">*</span></span><textarea required rows={6} maxLength={20000} value={values.body} onChange={e => change('body', e.target.value)}/></label>
          </Section>
          <Section n={5} lead="Se preparan en almacenamiento privado y se incorporan con la COM al confirmar. Siempre quedan en cuarentena.">
            <div className="file-picker">
              <input type="file" key={fileEpoch} aria-label="Original, máximo 25 MiB" onChange={e => {setFile(e.target.files?.[0] ?? null); uploadKey.current = crypto.randomUUID();}}/>
              <button type="button" disabled={!file} onClick={() => void prepare()}><Icon name="upload" size={18}/>Preparar adjunto</button>
              {file && <button type="button" className="ghost" onClick={clearFile}>Retirar archivo elegido</button>}
            </div>
            {drafts.length > 0 && <>
              <h3 className="subheading">Adjuntos preparados <small className="muted">· marca los que corresponden a esta COM</small></h3>
              <ul className="draft-list">
                {drafts.map(d => <li className="draft-row" key={text(d, 'token')}>
                  <label className="check">
                    <input type="checkbox" disabled={number(d, 'scope_id') !== Number(scope)} checked={selected.includes(text(d, 'token'))} onChange={e => setSelected(x => e.target.checked ? [...x, text(d, 'token')] : x.filter(t => t !== text(d, 'token')))}/>
                    <span><Icon name="file" size={16}/> {text(d, 'name')}<small>{scopeOf(ctx.catalog, d.scope_id)} · {when(d.created_ms)}</small></span>
                  </label>
                  <button type="button" className="ghost sm" onClick={() => void remove(text(d, 'token'))}>Eliminar preparación</button>
                </li>)}
              </ul>
            </>}
            <p className="field-help">Hasta 12 adjuntos preparados por usuario. Los preparados no enviados quedan privados para recuperarlos o retirarlos; no generan una COM.</p>
          </Section>
        </fieldset>
        {error && <ErrorMessage>{error}</ErrorMessage>}
        <div className="save-bar">
          <span className="save-state"><Icon name="file" size={16}/>{selected.length === 1 ? '1 adjunto' : `${selected.length} adjuntos`} para esta comunicación<small>Si la red falla, reintenta sin cambiar datos: no se duplicará el registro.</small></span>
          <div className="actions">
            <button className="primary" disabled={busy}><Busy busy={busy} label="Conservando…" idle={<><Icon name="check" size={18}/>Registrar COM pendiente</>}/></button>
          </div>
        </div>
      </form>
    </div>
  </>;
}
