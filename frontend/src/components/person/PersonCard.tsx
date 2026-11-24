import type {ReactNode} from 'react';
import {LabeledField} from '../LabeledField';
import {Icon} from '../Icon';
import {BirthDateField, EmailField, LegacyNote, MobileField, NationalityField, RegionCommuneFields, RunFields} from './Controls';
import {CodeSelect} from './CodeSelect';
import type {DeclaredPerson, PersonField, StatCodes} from '../../lib/person/personModel.ts';
import {DOCUMENT_KINDS, IDENTITY_TYPES, LEGACY_FIELDS, PERSON_LABELS, ROLE_LABELS, TREATMENTS, legacyValueOf, personDocument, personName} from '../../lib/person/personModel.ts';
import {normalizeDocumentNumber, normalizeName} from '../../lib/person/identity.ts';

/**
 * Grupos de datos de una persona declarada (persona-1). Cada mecanismo elige cuales quedan a la vista; el resto va en
 * "Más datos", de modo que la captura sigue siendo breve y nada nuevo es obligatorio.
 */
export type PersonGroup = 'names' | 'identity' | 'age' | 'birth' | 'nationality' | 'relationship' | 'territory' | 'address' | 'contact' | 'preferences' | 'schooling' | 'location';
export const PERSON_GROUPS: PersonGroup[] = ['names', 'identity', 'relationship', 'location', 'age', 'birth', 'nationality', 'schooling', 'preferences', 'territory', 'address', 'contact'];

type Props = {
  person: DeclaredPerson;
  /** Personas de la version guardada (vista canonica): un valor identico se conserva sin revalidarse. */
  previous: DeclaredPerson[];
  change: (key: PersonField, value: string) => void;
  /** Mensaje del campo (ya filtrado por tocado o intento de guardar). */
  error: (key: PersonField) => string | undefined;
  touch: (key: PersonField) => void;
  codes: StatCodes;
  regions: readonly string[];
  /** Grupos a la vista; los demas quedan en "Más datos". */
  main: PersonGroup[];
  /** Roles admitidos (sin selector si hay uno solo). */
  roles: string[];
  territoryLegend?: string;
};

const NAME_HELP: Partial<Record<PersonField, string>> = {middle_name: 'Si corresponde.', second_last_name: 'Si corresponde.'};

/** Campos de una persona declarada, con las reglas del Formulario y los datos historicos a la vista. */
export function PersonFields({person: p, previous, change, error, touch, codes, regions, main, roles, territoryLegend = 'Región y comuna de la persona'}: Props) {
  const legacy = (key: PersonField) => legacyValueOf(p, previous, key);
  const text = (key: PersonField, max: number, help?: string, type = 'text') => <LabeledField key={key} label={PERSON_LABELS[key]} help={help} error={error(key)}>
    <input type={type} maxLength={max} value={p[key]} aria-invalid={!!error(key) || undefined} onChange={e => change(key, e.target.value)} onBlur={() => touch(key)}/>
  </LabeledField>;
  const setType = (next: string) => {
    change('identity_type', next);
    // Cambiar el tipo no mezcla un RUN con un documento extranjero.
    for (const key of ['run_number', 'run_dv', 'document_kind', 'document_number'] as PersonField[]) if (p[key]) change(key, '');
  };
  const groups: Record<PersonGroup, () => ReactNode> = {
    names: () => <div className="form-grid" key="names">
      {(['first_name', 'middle_name', 'last_name', 'second_last_name'] as PersonField[]).map(key => <LabeledField key={key} label={PERSON_LABELS[key]} help={NAME_HELP[key]} error={error(key)}>
        <input type="text" maxLength={100} autoCapitalize="words" autoComplete="off" value={p[key]} aria-invalid={!!error(key) || undefined}
          onChange={e => change(key, e.target.value)} onBlur={e => {const clean = normalizeName(e.target.value);if (clean !== e.target.value) change(key, clean);touch(key);}}/>
      </LabeledField>)}
    </div>,
    identity: () => <div key="identity">
      <div className="form-grid">
        <LabeledField label={PERSON_LABELS.identity_type}>
          <select value={p.identity_type} onChange={e => setType(e.target.value)}><option value="">No registrada</option>{IDENTITY_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
        </LabeledField>
        {p.identity_type === 'DOCUMENTO_EXTRANJERO' && <LabeledField label={PERSON_LABELS.document_kind}>
          <select value={p.document_kind} onChange={e => change('document_kind', e.target.value)}><option value="">No registrado</option>{DOCUMENT_KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
        </LabeledField>}
      </div>
      {p.identity_type === 'RUN' && <RunFields number={p.run_number} dv={p.run_dv} onNumber={x => change('run_number', x)} onDv={x => change('run_dv', x)} error={error('run_number')} onBlur={() => touch('run_number')}/>}
      {p.identity_type === 'DOCUMENTO_EXTRANJERO' && <LabeledField label={PERSON_LABELS.document_number} help="Tal como aparece en el documento." error={error('document_number')}>
        <input type="text" maxLength={40} autoComplete="off" value={p.document_number} aria-invalid={!!error('document_number') || undefined}
          onChange={e => change('document_number', e.target.value)} onBlur={e => {const clean = normalizeDocumentNumber(e.target.value);if (clean !== e.target.value) change('document_number', clean);touch('document_number');}}/>
      </LabeledField>}
      {error('run_number') && p.identity_type !== 'RUN' && <p className="field-error" role="alert">{error('run_number')}</p>}
    </div>,
    age: () => <div className="form-grid" key="age">{text('age_years', 3, p.role === 'NNA' ? 'Número entero entre 0 y 17.' : 'Número entero.', 'text')}</div>,
    birth: () => <div className="form-grid" key="birth"><BirthDateField value={p.birth_date} onChange={x => change('birth_date', x)} error={error('birth_date')} onBlur={() => touch('birth_date')}/></div>,
    nationality: () => <NationalityField key="nationality" legend="Nacionalidad" value={p.nationality} onChange={x => change('nationality', x)} error={error('nationality')} onBlur={() => touch('nationality')}/>,
    relationship: () => <div className="form-grid" key="relationship">
      <CodeSelect label={PERSON_LABELS.relationship_code} list={codes.relationship} value={p.relationship_code} onChange={x => {change('relationship_code', x);touch('relationship_code');}} error={error('relationship_code')}/>
      {text('relationship_text', 500, 'Por ejemplo: tía materna, vecina, profesora jefe.')}
    </div>,
    territory: () => <RegionCommuneFields key="territory" legend={territoryLegend} region={p.region_code} commune={p.commune} order={regions}
      onRegion={x => {change('region_code', x);touch('region_code');}} onCommune={x => {change('commune', x);touch('commune');}}
      legacyRegion={legacy('region_code')} legacyCommune={legacy('commune')} errors={{region: error('region_code') ?? '', commune: error('commune') ?? ''}}/>,
    address: () => <div className="form-grid" key="address">{text('address', 500)}</div>,
    contact: () => <div className="form-grid" key="contact">
      <MobileField label={PERSON_LABELS.mobile} value={p.mobile} onChange={x => change('mobile', x)} error={error('mobile')} onBlur={() => touch('mobile')} legacy={legacy('mobile')}/>
      <EmailField label={PERSON_LABELS.email} value={p.email} onChange={x => change('email', x)} error={error('email')} onBlur={() => touch('email')}/>
      {text('other_contact', 300, 'Por ejemplo: red social o teléfono fijo.')}
    </div>,
    preferences: () => <div className="form-grid" key="preferences">
      {text('preferred_name', 500, 'Cómo prefiere que se le nombre.')}
      <LabeledField label={PERSON_LABELS.treatment}>
        <select value={p.treatment} onChange={e => change('treatment', e.target.value)}><option value="">No registrada</option>{TREATMENTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
      </LabeledField>
    </div>,
    schooling: () => <div className="form-grid" key="schooling">{text('schooling', 500)}</div>,
    location: () => <div className="form-grid" key="location">{text('location_text', 500, 'Antecedentes de localización, si no corresponden a un domicilio.')}</div>,
  };
  const more = PERSON_GROUPS.filter(g => !main.includes(g));
  const shownLegacy = LEGACY_FIELDS.filter(k => p[k]);
  return <div className="person-fields">
    {roles.length > 1 && <div className="form-grid">
      <LabeledField label={PERSON_LABELS.role} error={error('role')}><select value={p.role} onChange={e => change('role', e.target.value)}>{roles.map(v => <option key={v} value={v}>{ROLE_LABELS[v] ?? v}</option>)}</select></LabeledField>
    </div>}
    {roles.length <= 1 && error('role') && <p className="field-error" role="alert">{error('role')}</p>}
    {shownLegacy.map(key => <LegacyNote key={key} value={p[key]} label={PERSON_LABELS[key]} clear={() => change(key, '')}>{error(key) ?? 'Se conserva tal como se registró; no se divide ni se reinterpreta.'}</LegacyNote>)}
    {main.map(g => groups[g]())}
    {more.length > 0 && <details className="more-data">
      <summary><Icon name="plus" size={16}/>Más datos <small className="muted">(opcionales)</small></summary>
      <div className="more-data-body">{more.map(g => groups[g]())}</div>
    </details>}
  </div>;
}

/** Tarjeta de una persona relacionada con su titulo, resumen y accion para retirarla de la version. */
export function PersonCard({index, title, remove, ...props}: Props & {index: number; title: string; remove: () => void}) {
  const name = personName(props.person), doc = personDocument(props.person);
  return <li className="person-card">
    <div className="person-card-head">
      <h3><span className="avatar sm" aria-hidden="true">{index + 1}</span>{title}{name && <small className="muted"> · {name}{doc ? ' · ' + doc : ''}</small>}</h3>
      <button type="button" className="ghost sm" onClick={remove}><Icon name="close" size={16}/>Retirar de esta versión</button>
    </div>
    <PersonFields {...props}/>
  </li>;
}
