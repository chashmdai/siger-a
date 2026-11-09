// Ficha de primera atencion, esquema 1.0. El servidor valida la misma lista cerrada de campos
// (Attention.normalize): agregar un campo aqui exige agregarlo tambien alli.

export type Person = {role: string; name: string; identity_document: string; relationship: string; location: string; age: string; birth_date: string; nationality: string; schooling: string; preferred_name: string};
export type AttentionData = {schema_version: string; modality: string; course: string; people: Person[]; [key: string]: string | Person[]};

/** Campos de texto y de seleccion de la ficha; vacio significa "no registrado", no una respuesta negativa. */
export const STRING_FIELDS = ['presenter_name', 'identity_document', 'identity_type', 'relationship', 'contact_email', 'contact_phone', 'presenter_domicile', 'region', 'commune', 'preferred_name', 'pronouns', 'supports', 'court', 'rit', 'ruc', 'guardian_name', 'nationality', 'group_description', 'approximate_count', 'motive', 'story', 'request_text', 'observations', 'orientation', 'actions', 'commitments', 'institutions', 'safety_note', 'participation_note', 'child_message', 'identity_note', 'priority_note', 'safe_contact', 'judicial_status', 'guardian_status', 'participation', 'participation_mode', 'role_explained', 'priority', 'children_scope'];

export const emptyPerson = (): Person => ({role: 'NNA', name: '', identity_document: '', relationship: '', location: '', age: '', birth_date: '', nationality: '', schooling: '', preferred_name: ''});
export function emptyForm(): AttentionData {
  return {...Object.fromEntries(STRING_FIELDS.map(k => [k, ''])), schema_version: '1.0', modality: 'GENERAL', course: 'EN_ATENCION', people: []};
}
export const MAX_PEOPLE = 30;
export const PERSON_ROLES = ['NNA', 'ACOMPANANTE', 'REQUIRENTE', 'OTRA_PERSONA'];
export const PERSON_FIELDS = ['name', 'identity_document', 'relationship', 'location', 'age', 'birth_date', 'nationality', 'schooling', 'preferred_name'] as const;
export const PERSON_LABELS: Record<(typeof PERSON_FIELDS)[number], string> = {name: 'Nombre o referencia', identity_document: 'Identificación disponible', relationship: 'Vínculo', location: 'Domicilio o localización', age: 'Edad informada o aproximada', birth_date: 'Fecha de nacimiento', nationality: 'Nacionalidad', schooling: 'Escolaridad', preferred_name: 'Nombre de preferencia'};
/** Etiquetas breves de persona en la vista imprimible. */
export const PREVIEW_PERSON_LABELS: Record<string, string> = {role: 'Rol', name: 'Nombre', identity_document: 'Documento', relationship: 'Vínculo', location: 'Localización', age: 'Edad', birth_date: 'Nacimiento', nationality: 'Nacionalidad', schooling: 'Escolaridad', preferred_name: 'Nombre preferido'};

/** Etiquetas de la ficha; su orden es el de la vista imprimible. */
export const LABELS: Record<string, string> = {
  presenter_name: 'Persona requirente o referencia', identity_document: 'Documento de identidad', identity_type: 'Tipo de identificación', relationship: 'Vínculo con el NNA',
  contact_email: 'Correo', contact_phone: 'Teléfono', presenter_domicile: 'Domicilio del requirente', region: 'Región de atención', commune: 'Comuna de atención',
  preferred_name: 'Nombre de preferencia', pronouns: 'Forma de trato o pronombres', supports: 'Apoyos de comunicación o accesibilidad', court: 'Tribunal o fiscalía', rit: 'RIT', ruc: 'RUC',
  guardian_name: 'Curador/a conocido/a', nationality: 'Nacionalidad declarada', group_description: 'Descripción del grupo de NNA', approximate_count: 'Cantidad aproximada',
  motive: 'Motivo inicial', story: 'Relato de la persona o voz del NNA', request_text: 'Qué solicita a la Defensoría', observations: 'Observaciones de quien atiende',
  orientation: 'Orientación o material entregado', actions: 'Acciones realizadas durante este contacto', commitments: 'Gestiones comprometidas (aún no realizadas)', institutions: 'Instituciones intervinientes',
  safety_note: 'Indicaciones de contacto seguro', participation_note: 'Manifestación u observación sobre su participación', child_message: 'Mensaje espontáneo del NNA',
  identity_note: 'Información voluntaria relevante para el trato', priority_note: 'Necesidad de atención prioritaria observada', safe_contact: 'Condición de contacto',
  judicial_status: 'Antecedentes judiciales', guardian_status: 'Curador/a ad litem', participation: 'Disposición a conversar', participation_mode: 'Forma de expresar su decisión',
  role_explained: 'Se explicó el rol institucional', priority: 'Evaluación inicial de prioridad', children_scope: 'NNA relacionados', course: 'Continuidad prevista', modality: 'Modalidad',
};

type Options = [string, string][];
export const OPTIONS: Record<string, Options> = {
  identity_type: [['RUN', 'RUN'], ['OTRO', 'Otro documento'], ['NO_DISPONIBLE', 'No disponible']],
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
