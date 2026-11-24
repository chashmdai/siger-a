import type {ReactNode} from 'react';
import {LabeledField} from '../../components/LabeledField';
import {ChoiceCards} from '../../components/Choice';
import {Icon} from '../../components/Icon';
import type {IconName} from '../../components/Icon';
import {Callout, EmptyState} from '../../components/Primitives';
import type {AttentionData, Person} from './schema';
import {LABELS, MAX_PEOPLE, OPTIONS, PERSON_ROLES} from './schema';
import {AdultContact, AdultPresenter} from './adult/AdultSections';
import {orderedRegions} from '../../lib/person/catalog.ts';
import type {DeclaredPerson, Institution, StatCodes} from '../../lib/person/personModel.ts';
import {canonicalInstitutions, canonicalList, emptyPerson, NO_CODES} from '../../lib/person/personModel.ts';
import type {PersonGroup} from '../../components/person/PersonCard';
import {PersonCard} from '../../components/person/PersonCard';
import {InstitutionList} from '../../components/person/InstitutionList';
import {FactsFields} from '../../components/person/FactsFields';

/** Controles enlazados a la ficha en edicion; cada cambio marca la pantalla con cambios sin guardar. */
export type Controls = {
  form: AttentionData;
  /** Version guardada (datos historicos: un valor igual no se revalida). */
  previous: Record<string, unknown>;
  change: (key: string, value: string) => void;
  /** Mensaje del campo, visible tras salir de el o intentar guardar. */
  error: (key: string) => string | undefined;
  touch: (key: string) => void;
  setPeople: (update: (people: Person[]) => Person[]) => void;
  input: (key: string, type?: string, help?: string, max?: number) => ReactNode;
  area: (key: string, help?: string, required?: boolean) => ReactNode;
  select: (key: string, help?: string) => ReactNode;
  /** Codigos de region en el orden de catalog.regions (orderedRegions). */
  regions: readonly string[];
  /** Codigos vigentes de relacion y tipo de institucion (sin vigente, solo texto). */
  codes: StatCodes;
  setInstitutions: (update: (items: Institution[]) => Institution[]) => void;
  /** Personas e instituciones de la version guardada, en vista canonica. */
  previousPeople: DeclaredPerson[];
  previousInstitutions: Institution[];
};

export function controls(form: AttentionData, change: Controls['change'], setPeople: Controls['setPeople'],
  previous: Record<string, unknown> = {}, errors: Record<string, string> = {}, shown: (key: string) => boolean = () => false, touch: (key: string) => void = () => {},
  regions: readonly string[] = orderedRegions(undefined), codes: StatCodes = NO_CODES, setInstitutions: Controls['setInstitutions'] = () => {}): Controls {
  const value = (key: string) => String(form[key] ?? '');
  return {
    form, previous, change, setPeople, touch, regions, codes, setInstitutions,
    previousPeople: canonicalList(previous.people ?? []), previousInstitutions: canonicalInstitutions(previous.institutions_list ?? []),
    error: key => errors[key] && shown(key) ? errors[key] : undefined,
    input: (key, type = 'text', help, max = 500) => <LabeledField label={LABELS[key] ?? key} help={help}><input type={type} maxLength={max} value={value(key)} onChange={e => change(key, e.target.value)}/></LabeledField>,
    area: (key, help, required = false) => <LabeledField label={LABELS[key] ?? key} help={help} required={required}><textarea rows={key === 'motive' || key === 'story' ? 4 : 3} required={required} maxLength={key === 'safety_note' ? 1000 : 12000} value={value(key)} onChange={e => change(key, e.target.value)}/></LabeledField>,
    select: (key, help) => <LabeledField label={LABELS[key] ?? key} help={help}><select value={value(key)} onChange={e => change(key, e.target.value)}><option value="">No registrado</option>{OPTIONS[key].map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></LabeledField>,
  };
}

/** Bloque de la ficha con titulo y explicacion breve. */
export function FormSection({title, icon, lead, children, tone}: {title: string; icon: IconName; lead?: ReactNode; children: ReactNode; tone?: 'nna'}) {
  return <section className={'panel form-section' + (tone ? ' ' + tone : '')}>
    <div className="panel-head"><div><h2><Icon name={icon}/>{title}</h2>{lead && <p>{lead}</p>}</div></div>
    {children}
  </section>;
}

export function ModalitySelect({c}: {c: Controls}) {
  return <ChoiceCards legend="¿A quién se está atendiendo?" value={c.form.modality} onChange={v => c.change('modality', v)} options={[
    {value: 'GENERAL', icon: 'user', title: 'Persona adulta o atención general', text: 'Quien consulta es una persona adulta o se trata de una consulta general.'},
    {value: 'NNA', icon: 'heart', title: 'Atención directa a un NNA', text: 'El niño, niña o adolescente participa. Se habilita la sección de escucha.'},
  ]}/>;
}

/** Datos de cada persona relacionada a la vista (los mismos de la ficha 1.1); el resto va en "Más datos". */
export const FICHA_PERSON_MAIN: PersonGroup[] = ['names', 'identity', 'relationship', 'location', 'age', 'birth', 'nationality', 'schooling', 'preferences'];

/** Paso 1: motivo, persona requirente (o NNA que consulta), contacto seguro y personas relacionadas. */
export function FirstContact({c, intake}: {c: Controls; intake?: ReactNode}) {
  const {form} = c;
  const nna = form.modality === 'NNA';
  const updatePerson = (i: number, key: keyof Person, value: string) => c.setPeople(people => people.map((p, n) => n === i ? {...p, [key]: value} : p));
  return <>
    {intake}
    {!intake && <section className="panel form-section"><ModalitySelect c={c}/></section>}
    <FormSection title="Motivo inicial" icon="message" lead="Es lo único necesario para comenzar. Un resumen preliminar basta; no se exige un relato completo.">
      <div className="motive-field">{c.area('motive', undefined, true)}</div>
    </FormSection>
    <AdultPresenter c={c} nna={nna}/>
    <AdultContact c={c} nna={nna}/>
    <FormSection title="NNA y personas relacionadas" icon="people" lead="No es un registro maestro de personas ni requiere RUN. Un acompañante no es automáticamente representante.">
      {c.select('children_scope')}
      {form.children_scope === 'GRUPO' && <div className="form-grid">{c.input('group_description')}
        <LabeledField label={LABELS.approximate_count} help="Número entero." error={c.error('approximate_count')}>
          <input type="text" inputMode="numeric" maxLength={6} value={String(form.approximate_count ?? '')} aria-invalid={!!c.error('approximate_count') || undefined}
            onChange={e => c.change('approximate_count', e.target.value)} onBlur={() => c.touch('approximate_count')}/>
        </LabeledField>
      </div>}
      {form.people.length === 0 && <EmptyState compact icon="people" title="Sin personas agregadas">Agrega solo a quienes sea pertinente individualizar en esta atención.</EmptyState>}
      <ul className="people-list">
        {form.people.map((p, i) => <PersonCard key={i} index={i} title={`Persona relacionada ${i + 1}`} person={p} previous={c.previousPeople}
          change={(key, value) => updatePerson(i, key, value)} error={key => c.error(`people.${i}.${key}`)} touch={key => c.touch(`people.${i}.${key}`)}
          codes={c.codes} regions={c.regions} main={FICHA_PERSON_MAIN} roles={PERSON_ROLES} remove={() => c.setPeople(people => people.filter((_, n) => i !== n))}/>)}
      </ul>
      <button type="button" disabled={form.people.length >= MAX_PEOPLE} onClick={() => c.setPeople(people => [...people, emptyPerson('NNA')])}><Icon name="plus" size={18}/>Agregar persona relacionada</button>
    </FormSection>
  </>;
}

/** Paso 2: escucha del NNA, relato separado de observacion y antecedentes complementarios. */
export function Listening({c}: {c: Controls}) {
  const {form} = c;
  return <>
    {form.modality === 'NNA' && <FormSection tone="nna" title="Escucha y participación del NNA" icon="heart"
      lead="No es una encuesta para que el NNA complete solo. Consigna lo que expresa durante la atención, sin interpretar el silencio como asentimiento.">
      <div className="form-grid three">{c.select('participation')}{c.select('participation_mode')}{c.select('role_explained')}</div>
      {form.participation === 'NO' && <Callout tone="ok">Su decisión se respeta: no se exige relato ni entrevista para mantener el REQ. No completes respuestas en su nombre.</Callout>}
      {c.area('participation_note')}
      {c.area('child_message', 'Sus palabras, tal como las expresó.')}
      {c.area('identity_note', 'Voluntario y pertinente para el trato. No es requisito de atención.')}
    </FormSection>}
    <FormSection title="Relato y observación, por separado" icon="message" lead="Distinguir lo que la persona relata de lo que observa quien atiende protege su voz.">
      {c.area('story', 'Conserva la voz de quien relata. Los aportes posteriores quedan en nuevas versiones.')}
      {c.area('request_text')}
      {c.area('observations', 'Hechos observables; no atribuir competencias clínicas al rol de registro.')}
      {c.area('orientation')}
    </FormSection>
    <FormSection title="Lugar de los hechos" icon="flag" lead="Opcional. Dónde ocurrieron los hechos que motivan la atención, si se conocen.">
      <FactsFields values={form} previous={c.previous} change={c.change} error={c.error} touch={c.touch} regions={c.regions}/>
    </FormSection>
    <FormSection title="Antecedentes complementarios" icon="layers">
      <h3 className="form-subtitle">Instituciones intervinientes</h3>
      <InstitutionList items={form.institutions_list} previous={c.previousInstitutions} update={c.setInstitutions} error={key => c.error(`institutions_list.${key}`)}
        touch={key => c.touch(`institutions_list.${key}`)} codes={c.codes} regions={c.regions}/>
      {String(c.previous.institutions ?? '') !== '' && c.area('institutions', 'Registrado antes como texto; se conserva. Agrega las instituciones en la lista.')}
      <div className="form-grid">{c.select('judicial_status')}{c.select('priority', 'No cambia la complejidad del REQ ni activa protocolos automáticos.')}</div>
      {form.judicial_status === 'SI' && <div className="form-grid three nested">{c.input('court')}{c.input('rit')}{c.input('ruc')}{c.select('guardian_status')}{c.input('guardian_name')}</div>}
      {form.priority === 'PRIORITARIA' && c.area('priority_note')}
    </FormSection>
  </>;
}

/** Paso 3: lo realizado frente a lo comprometido y la continuidad prevista. */
export function Outcome({c}: {c: Controls}) {
  return <>
    <FormSection title="Lo realizado y lo comprometido" icon="checkCircle" lead="Cada versión conserva quién registró y cuándo. No se atribuyen acciones a un titular ausente.">
      {c.area('actions')}
      {c.area('commitments', 'No se marcan como realizadas ni crean tareas automáticamente.')}
    </FormSection>
    <FormSection title="Continuidad" icon="flow">
      {c.select('course')}
      <Callout tone="neutral">
        <p>Entregar la atención a otro profesional no es una derivación formal de Caso ni cambia la jefatura principal. Si requiere seguimiento, la jefatura abre el Caso dentro del mismo REQ.</p>
        <p>Para resolver sin Caso, guarda primero la ficha y usa <strong>Resolver atención inmediata</strong>, dejando el resultado real.</p>
      </Callout>
    </FormSection>
  </>;
}
