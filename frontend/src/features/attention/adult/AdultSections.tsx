import {LabeledField} from '../../../components/LabeledField';
import {Callout} from '../../../components/Primitives';
import type {Controls} from '../FormSections';
import {FormSection} from '../FormSections';
import {ADULT_IDENTITY_TYPES, DOCUMENT_KINDS, documentSummary, fullName, legacyValue, NAME_KEYS} from './adultForm.ts';
import {BirthDateField, EmailField, LegacyNote, MobileField, NationalityField, RegionCommuneFields, RunFields} from './Controls';
import {normalizeDocumentNumber, normalizeName} from './identity.ts';

const NAME_LABELS: Record<(typeof NAME_KEYS)[number], [string, string?]> = {
  presenter_first_name: ['Primer nombre'], presenter_middle_name: ['Segundo nombre', 'Si corresponde.'],
  presenter_last_name: ['Primer apellido'], presenter_second_last_name: ['Segundo apellido', 'Si corresponde.'],
};

/** Valor anterior conservado solo si sigue igual al de la version guardada. */
const legacyOf = (c: Controls, key: string) => String(c.form[key] ?? '') === String(c.previous[key] ?? '') ? legacyValue(c.form, key) : '';

/** Persona adulta: identificacion declarada (nombres, RUN o documento, nacimiento, nacionalidad) y trato preferido. */
export function AdultPresenter({c}: {c: Controls}) {
  const {form} = c, v = (key: string) => String(form[key] ?? '');
  const type = v('identity_type'), name = fullName(form), summary = documentSummary(form), legacyDocument = legacyOf(c, 'identity_document');
  const setType = (next: string) => {
    c.change('identity_type', next);
    // Cambiar el tipo no mezcla un RUN con un documento extranjero.
    for (const key of ['presenter_run_number', 'presenter_run_dv', 'presenter_document_kind', 'presenter_document_number']) if (v(key)) c.change(key, '');
  };
  return <FormSection title="Persona requirente" icon="user" lead="Completa solo lo que la persona entregue; cada dato puede quedar pendiente. Son datos declarados: no se consultan ClaveÚnica, Registro Civil ni PISEE.">
    <h3 className="form-subtitle">Identificación declarada</h3>
    <div className="form-grid">
      {NAME_KEYS.map(key => <LabeledField key={key} label={NAME_LABELS[key][0]} help={NAME_LABELS[key][1]} error={c.error(key)}>
        <input type="text" maxLength={100} autoCapitalize="words" autoComplete="off" value={v(key)} aria-invalid={!!c.error(key) || undefined}
          onChange={e => c.change(key, e.target.value)} onBlur={e => {const clean = normalizeName(e.target.value);if (clean !== e.target.value) c.change(key, clean);c.touch(key);}}/>
      </LabeledField>)}
    </div>
    {name ? <p className="field-help">Nombre registrado en la ficha: <strong>{name}</strong></p>
      : <LabeledField label="Referencia de la persona" help="Úsala si aún no se conocen sus nombres (por ejemplo, «vecina del sector»)."><input type="text" maxLength={200} value={v('presenter_name')} onChange={e => c.change('presenter_name', e.target.value)}/></LabeledField>}
    <div className="form-grid">
      <LabeledField label="Identificación disponible">
        <select value={type} onChange={e => setType(e.target.value)}><option value="">No registrada</option>{ADULT_IDENTITY_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
      </LabeledField>
      <BirthDateField value={v('presenter_birth_date')} onChange={x => c.change('presenter_birth_date', x)} error={c.error('presenter_birth_date')} onBlur={() => c.touch('presenter_birth_date')}/>
    </div>
    {type === 'RUN' && <RunFields number={v('presenter_run_number')} dv={v('presenter_run_dv')} onNumber={x => c.change('presenter_run_number', x)} onDv={x => c.change('presenter_run_dv', x)}
      error={c.error('presenter_run_number')} onBlur={() => c.touch('presenter_run_number')}/>}
    {type === 'OTRO' && <div className="form-grid">
      <LabeledField label="Tipo de documento"><select value={v('presenter_document_kind')} onChange={e => c.change('presenter_document_kind', e.target.value)}><option value="">No registrado</option>{DOCUMENT_KINDS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></LabeledField>
      <LabeledField label="Número del documento" help="Tal como aparece en el documento." error={c.error('presenter_document_number')}>
        <input type="text" maxLength={40} autoComplete="off" value={v('presenter_document_number')} aria-invalid={!!c.error('presenter_document_number') || undefined}
          onChange={e => c.change('presenter_document_number', e.target.value)} onBlur={e => {const clean = normalizeDocumentNumber(e.target.value);if (clean !== e.target.value) c.change('presenter_document_number', clean);c.touch('presenter_document_number');}}/>
      </LabeledField>
    </div>}
    {type === 'OTRO' && <p className="field-help">Puede usarse aunque la situación migratoria no esté regularizada.</p>}
    {summary && <p className="field-help">Documento registrado: <strong>{summary}</strong> (dato declarado; la validación del RUN es solo matemática).</p>}
    <LegacyNote value={legacyDocument} label="Documento registrado anteriormente" clear={() => c.change('identity_document', '')}/>
    <NationalityField value={v('nationality')} onChange={x => c.change('nationality', x)} error={c.error('nationality')} onBlur={() => c.touch('nationality')}/>
    <div className="form-grid">
      {c.input('relationship')}
      {c.input('presenter_domicile')}
    </div>
    <h3 className="form-subtitle">Preferencias de trato</h3>
    <p className="field-help">Opcionales. No cambian los datos de identificación.</p>
    <div className="form-grid">
      {c.input('preferred_name', 'text', 'Cómo prefiere que se le llame.')}
      {c.input('pronouns', 'text', 'Por ejemplo: ella, él, elle, usar su nombre.')}
    </div>
    {c.input('supports', 'text', 'Por ejemplo: intérprete, lengua de señas, lectura fácil.')}
  </FormSection>;
}

/** Contacto seguro de la persona adulta: celular +56 9, correo validado y region/comuna del catalogo. */
export function AdultContact({c}: {c: Controls}) {
  const {form} = c, v = (key: string) => String(form[key] ?? '');
  return <FormSection title="Contacto seguro" icon="lock" lead="Registra cómo y cuándo es seguro contactar. En esta fase no se envían correos ni mensajes reales.">
    <div className="form-grid">
      <MobileField label="Celular de contacto" value={v('contact_phone')} onChange={x => c.change('contact_phone', x)} error={c.error('contact_phone')} onBlur={() => c.touch('contact_phone')} legacy={legacyOf(c, 'contact_phone')}/>
      <EmailField label="Correo de contacto" value={v('contact_email')} onChange={x => c.change('contact_email', x)} error={c.error('contact_email')} onBlur={() => c.touch('contact_email')}/>
      {c.select('safe_contact')}
      <span/>
      <RegionCommuneFields region={v('region')} commune={v('commune')} onRegion={x => {c.change('region', x);c.touch('region');}} onCommune={x => {c.change('commune', x);c.touch('commune');}}
        legacyRegion={legacyOf(c, 'region')} legacyCommune={legacyOf(c, 'commune')} errors={{region: c.error('region') ?? '', commune: c.error('commune') ?? ''}}/>
    </div>
    {c.area('safety_note', 'Por ejemplo: no llamar al teléfono del hogar; solo contactar en horario laboral.')}
    {form.safe_contact === 'NO_CONTACTAR' && <Callout tone="warn">Quedará destacado en la ficha que no se debe contactar por estos medios.</Callout>}
  </FormSection>;
}
