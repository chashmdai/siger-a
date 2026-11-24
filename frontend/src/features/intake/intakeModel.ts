// Ficha de ingreso de la COM manual (0.8.0, D33, gate E04; DISENO 3.5): borrador, validacion y datos que se envian, con
// las reglas del servidor (IntakeRecord, DeclaredPerson). Es la unica fuente de los datos de personas de la COM: la
// pantalla envia el bloque intake y nunca los campos sueltos del formato anterior (presenter, contact, identity_document,
// contact_email, contact_phone, contact_restrictions, presenter_domicile, persons). Todo es opcional: el ingreso se
// registra con asunto y relato, como antes; un ingreso institucional (oficio) se registra sin persona natural.
import type {DeclaredPerson, Institution, Problems, StatCodes} from '../../lib/person/personModel.ts';
import {blankPerson, canonicalInstitutions, canonicalList, canonicalPerson, emptyPerson, factProblems, institutionProblems, NO_CODES, personPayload, personProblems, prefixed} from '../../lib/person/personModel.ts';

/** Version y lista cerrada de la ficha de ingreso (IntakeRecord.SCHEMA y KEYS; una prueba compara ambas). */
export const INTAKE_SCHEMA = '1.0';
export const INTAKE_KEYS = ['schema_version', 'presenter', 'persons', 'contact_restrictions', 'facts_region_code', 'facts_commune', 'facts_place', 'institutions_list'] as const;
/** Roles de las personas relacionadas del ingreso (IntakeRecord.PERSON_ROLES); quien presenta es REQUIRENTE. */
export const INTAKE_ROLES = ['NNA', 'ACOMPANANTE', 'OTRA_PERSONA'];
export const MAX_PERSONS = 12;
/** Campos del formato anterior: con la ficha de ingreso el servidor los rechaza (una sola fuente). */
export const LEGACY_COM_FIELDS = ['presenter', 'contact', 'identity_document', 'contact_email', 'contact_phone', 'contact_restrictions', 'presenter_domicile', 'persons'] as const;

/** Quien presenta: una persona natural o una institucion (oficio), sin inventar una persona. */
export type PresenterKind = 'PERSONA' | 'INSTITUCION';
export type IntakeData = {
  presenter: DeclaredPerson; persons: DeclaredPerson[]; contact_restrictions: string;
  facts_region_code: string; facts_commune: string; facts_place: string; institutions_list: Institution[];
};

export function emptyIntake(): IntakeData {
  return {presenter: emptyPerson('REQUIRENTE'), persons: [], contact_restrictions: '', facts_region_code: '', facts_commune: '', facts_place: '', institutions_list: []};
}
const str = (v: unknown) => v == null ? '' : String(v);
/** Borrador desde la ficha de ingreso guardada (vista canonica, sin versiones de catalogo derivadas). */
export function intakeFromSaved(data: Record<string, unknown>): IntakeData {
  const p = data.presenter && typeof data.presenter === 'object' ? canonicalPerson({role: 'REQUIRENTE', ...(data.presenter as Record<string, unknown>)}) : emptyPerson('REQUIRENTE');
  p.role = 'REQUIRENTE';
  return {presenter: p, persons: canonicalList(data.persons), contact_restrictions: str(data.contact_restrictions), facts_region_code: str(data.facts_region_code),
    facts_commune: str(data.facts_commune), facts_place: str(data.facts_place), institutions_list: canonicalInstitutions(data.institutions_list)};
}
/** Un ingreso guardado sin persona natural y con instituciones se presenta como ingreso institucional. */
export const presenterKindOf = (data: IntakeData): PresenterKind => blankPerson(data.presenter) && data.institutions_list.length > 0 ? 'INSTITUCION' : 'PERSONA';

/**
 * Problemas por ruta (presenter.first_name, persons.0.role, institutions_list.0.name, facts_*). previous = ficha de
 * ingreso vigente (vacia al crear). Con un ingreso institucional no se valida al presentador (no se envia).
 */
export function intakeProblems(data: IntakeData, kind: PresenterKind, previous: Record<string, unknown> = {}, codes: StatCodes = NO_CODES): Problems {
  const before = intakeFromSaved(previous);
  const errors: Problems = {...factProblems(data, previous)};
  if (kind === 'PERSONA') Object.assign(errors, prefixed('presenter', personProblems(data.presenter, blankPerson(before.presenter) ? [] : [before.presenter], codes)));
  data.persons.forEach((p, i) => Object.assign(errors, prefixed(`persons.${i}`, personProblems(p, before.persons, codes, true))));
  data.institutions_list.forEach((x, i) => Object.assign(errors, prefixed(`institutions_list.${i}`, institutionProblems(x, before.institutions_list, codes))));
  if (kind === 'INSTITUCION' && data.institutions_list.length === 0) errors['institutions_list'] = 'Agrega la institución que presenta el ingreso, o elige «Una persona».';
  return errors;
}

/** Bloque intake: presentador vacio (o ingreso institucional) = {}; listas sin claves derivadas. */
export function intakePayload(data: IntakeData, kind: PresenterKind): Record<string, unknown> {
  const presenter = kind === 'PERSONA' && !blankPerson(data.presenter) ? {...personPayload(data.presenter), role: 'REQUIRENTE'} : {};
  return {schema_version: INTAKE_SCHEMA, presenter, persons: data.persons.map(personPayload), contact_restrictions: data.contact_restrictions,
    facts_region_code: data.facts_region_code, facts_commune: data.facts_commune, facts_place: data.facts_place, institutions_list: canonicalInstitutions(data.institutions_list)};
}

/** Datos de COM_CREATE: recepcion, asunto, relato, adjuntos y la ficha de ingreso (sin campos sueltos de personas). */
export function comCreatePayload(base: {scope: string; channel: string; received: string; subject: string; body: string; attachments: string[]}, data: IntakeData, kind: PresenterKind): Record<string, unknown> {
  return {scope_id: base.scope, channel: base.channel, received_at: new Date(base.received).toISOString(), subject: base.subject, body: base.body,
    attachment_tokens: base.attachments, intake: intakePayload(data, kind)};
}
/** Datos de COM_COMPLETE (id = COM y su revision; motivo obligatorio). */
export const completePayload = (data: IntakeData, kind: PresenterKind, reason: string) => ({intake: intakePayload(data, kind), reason});
