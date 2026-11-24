// Ficha de primera atencion, esquema 1.2 (0.8.0, D33, gate E04; DISENO 3.4). 1.0 y 1.1 siguen legibles y pasan a 1.2 al
// editarlas (fichaModel.ts: prepareFicha), sin reescribir lo historico. El servidor valida la misma lista cerrada de campos
// (Attention.normalize y version12): agregar un campo aqui exige agregarlo tambien alli (tests/ficha12.test.ts compara
// ambas listas).
import {ADULT_KEYS} from './adult/adultForm.ts';
import type {DeclaredPerson, Institution} from '../../lib/person/personModel.ts';

/** Version que escribe la pantalla. */
export const FICHA_VERSION = '1.2';
export type Person = DeclaredPerson;
export type AttentionData = {schema_version: string; modality: string; course: string; people: DeclaredPerson[]; institutions_list: Institution[]; [key: string]: string | DeclaredPerson[] | Institution[]};

/** Campos que agrega la ficha 1.2 (Attention.KEYS_12); relationship_version la deriva el servidor y no se envia. */
export const KEYS_12 = ['relationship_code', 'facts_region_code', 'facts_commune', 'facts_place', 'institutions_list'] as const;
/** Campos de texto y de seleccion de la ficha; vacio significa "no registrado", no una respuesta negativa. */
export const STRING_FIELDS = [...ADULT_KEYS, 'presenter_name', 'identity_document', 'identity_type', 'relationship', 'contact_email', 'contact_phone', 'presenter_domicile', 'region', 'commune', 'preferred_name', 'pronouns', 'supports', 'court', 'rit', 'ruc', 'guardian_name', 'nationality', 'group_description', 'approximate_count', 'motive', 'story', 'request_text', 'observations', 'orientation', 'actions', 'commitments', 'institutions', 'safety_note', 'participation_note', 'child_message', 'identity_note', 'priority_note', 'safe_contact', 'judicial_status', 'guardian_status', 'participation', 'participation_mode', 'role_explained', 'priority', 'children_scope', 'relationship_code', 'facts_region_code', 'facts_commune', 'facts_place'];

export function emptyForm(): AttentionData {
  return {...Object.fromEntries(STRING_FIELDS.map(k => [k, ''])), schema_version: FICHA_VERSION, modality: 'GENERAL', course: 'EN_ATENCION', people: [], institutions_list: []};
}
export const MAX_PEOPLE = 30;
/** Roles de las personas relacionadas de la ficha (Attention.PEOPLE_ROLES). */
export const PERSON_ROLES = ['NNA', 'ACOMPANANTE', 'REQUIRENTE', 'OTRA_PERSONA'];

/** Etiquetas de la ficha; su orden es el de la vista imprimible. */
export const LABELS: Record<string, string> = {
  presenter_name: 'Persona requirente o referencia',
  presenter_first_name: 'Primer nombre', presenter_middle_name: 'Segundo nombre', presenter_last_name: 'Primer apellido', presenter_second_last_name: 'Segundo apellido',
  identity_type: 'Tipo de identificación', presenter_run_number: 'RUN (número)', presenter_run_dv: 'RUN (dígito verificador)',
  presenter_document_kind: 'Tipo de documento', presenter_document_number: 'Número de documento', identity_document: 'Documento de identidad',
  presenter_birth_date: 'Fecha de nacimiento', relationship: 'Vínculo con el NNA', relationship_code: 'Relación con el NNA',
  contact_email: 'Correo', contact_phone: 'Teléfono', presenter_domicile: 'Domicilio del requirente', region: 'Región del requirente', commune: 'Comuna del requirente',
  preferred_name: 'Nombre de preferencia', pronouns: 'Forma de trato o pronombres', supports: 'Apoyos de comunicación o accesibilidad', court: 'Tribunal o fiscalía', rit: 'RIT', ruc: 'RUC',
  guardian_name: 'Curador/a conocido/a', nationality: 'Nacionalidad declarada', group_description: 'Descripción del grupo de NNA', approximate_count: 'Cantidad aproximada',
  motive: 'Motivo inicial', story: 'Relato de la persona o voz del NNA', request_text: 'Qué solicita a la Defensoría', observations: 'Observaciones de quien atiende',
  orientation: 'Orientación o material entregado', actions: 'Acciones realizadas durante este contacto', commitments: 'Gestiones comprometidas (aún no realizadas)', institutions: 'Instituciones intervinientes (texto)',
  facts_region_code: 'Región de los hechos', facts_commune: 'Comuna de los hechos', facts_place: 'Lugar de los hechos',
  safety_note: 'Indicaciones de contacto seguro', participation_note: 'Manifestación u observación sobre su participación', child_message: 'Mensaje espontáneo del NNA',
  identity_note: 'Información voluntaria relevante para el trato', priority_note: 'Necesidad de atención prioritaria observada', safe_contact: 'Condición de contacto',
  judicial_status: 'Antecedentes judiciales', guardian_status: 'Curador/a ad litem', participation: 'Disposición a conversar', participation_mode: 'Forma de expresar su decisión',
  role_explained: 'Se explicó el rol institucional', priority: 'Evaluación inicial de prioridad', children_scope: 'NNA relacionados', course: 'Continuidad prevista', modality: 'Modalidad',
};

type Options = [string, string][];
export const OPTIONS: Record<string, Options> = {
  identity_type: [['RUN', 'RUN'], ['OTRO', 'Documento extranjero u otro'], ['NO_DISPONIBLE', 'No disponible']],
  safe_contact: [['SEGURO', 'Contacto autorizado'], ['VERIFICAR', 'Verificar antes de contactar'], ['NO_CONTACTAR', 'No contactar por estos medios']],
  children_scope: [['INDIVIDUAL', 'Un NNA'], ['VARIOS', 'Varios NNA'], ['GRUPO', 'Grupo sin individualización'], ['POR_IDENTIFICAR', 'Por identificar']],
  participation: [['SI', 'Desea conversar'], ['NO', 'No desea conversar'], ['SIN_RESPUESTA', 'No expresa respuesta'], ['NO_CONSULTADO', 'Aún no se consulta']],
  participation_mode: [['VERBAL', 'Verbal'], ['ALTERNATIVA', 'Comunicación alternativa o gesto contextualizado'], ['NO_ENTREVISTA', 'No se realizó entrevista']],
  role_explained: [['SI', 'Sí'], ['NO', 'No'], ['PENDIENTE', 'Pendiente']],
  judicial_status: [['SI', 'Se informan antecedentes'], ['NO', 'No se informan'], ['DESCONOCIDO', 'Se desconoce']],
  guardian_status: [['SI', 'Tiene curador/a'], ['NO', 'Informa no tener'], ['DESCONOCIDO', 'Se desconoce']],
  priority: [['NO_EVALUADA', 'No evaluada'], ['SIN_SENALES', 'Sin necesidad prioritaria reportada'], ['PRIORITARIA', 'Requiere atención prioritaria']],
  course: [['EN_ATENCION', 'Continúa el primer contacto o aún por definir'], ['SEGUIMIENTO', 'Requiere un Caso de seguimiento']],
};

/** Secciones de la ficha progresiva. */
export const STEPS: {title: string; text: string}[] = [
  {title: 'Primer abordaje', text: 'Motivo, quién consulta y cómo contactar de forma segura'},
  {title: 'Antecedentes y escucha', text: 'Relato, participación del NNA y antecedentes'},
  {title: 'Resultado y continuidad', text: 'Lo realizado, lo comprometido y lo que sigue'},
];

/** Texto visible de una opcion cerrada ('' si no esta registrada). */
export const optionLabel = (key: string, value: unknown) => OPTIONS[key]?.find(([v]) => v === value)?.[1] ?? String(value ?? '');
