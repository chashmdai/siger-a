import {LabeledField} from '../../components/LabeledField';
import {ChipRadios} from '../../components/Choice';
import {Icon} from '../../components/Icon';
import {EmptyState} from '../../components/Primitives';
import type {PersonGroup} from '../../components/person/PersonCard';
import {PersonCard, PersonFields} from '../../components/person/PersonCard';
import {InstitutionList} from '../../components/person/InstitutionList';
import {FactsFields} from '../../components/person/FactsFields';
import type {DeclaredPerson, Institution, PersonField, StatCodes} from '../../lib/person/personModel.ts';
import {emptyPerson} from '../../lib/person/personModel.ts';
import type {IntakeData, PresenterKind} from './intakeModel.ts';
import {INTAKE_ROLES, MAX_PERSONS} from './intakeModel.ts';

/** Controles de la ficha de ingreso de la COM manual: los usan el registro (Intake.tsx) y la complementacion (ComDetail). */
export type IntakeControls = {
  data: IntakeData;
  /** Version vigente (vista canonica) para conservar lo historico identico; vacia al registrar. */
  previous: IntakeData;
  update: (fn: (data: IntakeData) => IntakeData) => void;
  kind: PresenterKind;
  setKind: (kind: PresenterKind) => void;
  error: (key: string) => string | undefined;
  touch: (key: string) => void;
  codes: StatCodes;
  regions: readonly string[];
};

/** Quien presenta: a la vista los mismos datos del ingreso anterior (nombre, identificacion, domicilio, contactos) y la region. */
export const PRESENTER_MAIN: PersonGroup[] = ['names', 'identity', 'territory', 'address', 'contact'];
/** Personas relacionadas: a la vista los mismos datos del ingreso anterior (rol, nombre, identificacion, vinculo, localizacion). */
export const INTAKE_PERSON_MAIN: PersonGroup[] = ['names', 'identity', 'relationship', 'location'];

/** Quien presenta el ingreso y como contactar de forma segura. Todo opcional. */
export function PresenterFields({c}: {c: IntakeControls}) {
  const change = (key: PersonField, value: string) => c.update(d => ({...d, presenter: {...d.presenter, [key]: value}}));
  return <>
    <ChipRadios legend="¿Quién presenta el ingreso?" value={c.kind} onChange={v => c.setKind(v as PresenterKind)}
      options={[{value: 'PERSONA', label: 'Una persona'}, {value: 'INSTITUCION', label: 'Una institución (por ejemplo, un oficio)'}]}
      help={c.kind === 'INSTITUCION' ? 'Registra la institución en «Instituciones», en Antecedentes. No se registra una persona natural.' : 'Solo lo que la persona haya entregado. Un dato ausente no impide registrar el ingreso.'}/>
    {c.kind === 'PERSONA' && <PersonFields person={c.data.presenter} previous={[c.previous.presenter]} change={change} error={key => c.error(`presenter.${key}`)} touch={key => c.touch(`presenter.${key}`)}
      codes={c.codes} regions={c.regions} main={PRESENTER_MAIN} roles={['REQUIRENTE']} territoryLegend="Región y comuna del requirente"/>}
    <LabeledField label="Restricciones o indicaciones de contacto seguro">
      <textarea maxLength={1000} value={c.data.contact_restrictions} onChange={e => {const v = e.target.value;c.update(d => ({...d, contact_restrictions: v}));}} placeholder="Por ejemplo: no llamar; solo correo autorizado."/>
    </LabeledField>
  </>;
}

/** NNA y otras personas relacionadas con el ingreso (hasta 12; cada una con algun dato). */
export function PersonsFields({c}: {c: IntakeControls}) {
  const set = (i: number, key: PersonField, value: string) => c.update(d => ({...d, persons: d.persons.map((p, n) => n === i ? {...p, [key]: value} : p)}));
  const persons: DeclaredPerson[] = c.data.persons;
  return <>
    {persons.length === 0 && <EmptyState compact icon="people" title="Sin personas agregadas">Agrega a un NNA u otra persona solo si es pertinente para este ingreso.</EmptyState>}
    <ul className="people-list">
      {persons.map((p, i) => <PersonCard key={i} index={i} title={`Persona ${i + 1}`} person={p} previous={c.previous.persons} change={(key, value) => set(i, key, value)}
        error={key => c.error(`persons.${i}.${key}`)} touch={key => c.touch(`persons.${i}.${key}`)} codes={c.codes} regions={c.regions} main={INTAKE_PERSON_MAIN} roles={INTAKE_ROLES}
        remove={() => c.update(d => ({...d, persons: d.persons.filter((_, n) => n !== i)}))}/>)}
    </ul>
    <button type="button" disabled={persons.length >= MAX_PERSONS} onClick={() => c.update(d => ({...d, persons: [...d.persons, emptyPerson('NNA')]}))}><Icon name="plus" size={18}/>Agregar NNA o persona relacionada</button>
  </>;
}

/** Lugar de los hechos e instituciones (opcionales). */
export function FactsAndInstitutions({c}: {c: IntakeControls}) {
  const values = c.data as unknown as Record<string, unknown>;
  return <>
    <h3 className="form-subtitle">Lugar de los hechos</h3>
    <FactsFields values={values} previous={c.previous as unknown as Record<string, unknown>} change={(key, value) => c.update(d => ({...d, [key]: value}))} error={c.error} touch={c.touch} regions={c.regions}/>
    <h3 className="form-subtitle">Instituciones</h3>
    {c.error('institutions_list') && <p className="field-error" role="alert">{c.error('institutions_list')}</p>}
    <InstitutionList items={c.data.institutions_list} previous={c.previous.institutions_list} update={fn => c.update(d => ({...d, institutions_list: fn(d.institutions_list) as Institution[]}))}
      error={key => c.error(`institutions_list.${key}`)} touch={key => c.touch(`institutions_list.${key}`)} codes={c.codes} regions={c.regions}
      lead="Por ejemplo, la institución que envía un oficio o las instituciones involucradas en los hechos."/>
  </>;
}
