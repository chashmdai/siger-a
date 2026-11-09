/**
 * Rotulos del Formulario Publico completo 1.0/1.1 para la ficha COM externa. Solo presentacion: los codigos y
 * valores conservados no cambian. Campos y opciones portados de CamposFormulario.ts (SMR 02d4184); fuentes, niveles,
 * roles y estados registrales segun IdentificacionCiudadana y ResultadoSrcei del Formulario (a84d2e7).
 */
const FIELD_LABELS: Record<string, string> = {
  tipoIdentificacion: 'Tipo de identificación', nacionalidad: 'Nacionalidad declarada', tipoDocumento: 'Tipo de documento', numeroDocumento: 'Número de documento extranjero', 'afectados-modalidad': 'Identificación de NNA afectados',
  motivo: 'Motivo principal', tema: 'Tipo de consulta', relato: 'Relato', 'nna-story': 'Relato NNA', 'consulta-general-detalle': 'Consulta general', 'consulta-confirmada': 'Orientación de consulta revisada',
  'entrevista-motivo': 'Motivo de entrevista', 'entrevista-rol': 'Perfil de quien solicita entrevista', 'entrevista-rol-otro': 'Otro perfil de entrevista', 'entrevista-entidad': 'Entidad de quien solicita entrevista',
  'stats-confirm': 'Información estadística publicada revisada', 'stats-descripcion': 'Solicitud estadística', 'stats-periodo': 'Período solicitado', 'stats-desglose': 'Desglose solicitado',
  'adult-region': 'Región de residencia', 'adult-commune': 'Comuna de residencia', 'adult-address': 'Dirección declarada', email: 'Correo', telefono: 'Celular',
  riesgo: 'Riesgo actual', relacion: 'Relación con el NNA', 'relacion-familiar': 'Parentesco familiar', 'hechos-region': 'Región de los hechos', 'hechos-commune': 'Comuna de los hechos', lugar: 'Lugar de los hechos', momento: 'Cuándo ocurrieron los hechos', 'identifica-responsables': 'Personas o instituciones señaladas',
  organizacion: 'Representación de organización', 'tipo-organizacion': 'Tipo de organización', 'nombre-organizacion': 'Nombre de la organización', actividad: 'Actividad solicitada', publico: 'Público destinatario', 'fecha-actividad': 'Fecha de la actividad', 'promocion-region': 'Región de la actividad', 'promocion-commune': 'Comuna de la actividad',
  'nna-region': 'Región de residencia', 'nna-commune': 'Comuna de residencia', 'nna-preferred': 'Nombre preferido', 'nna-treatment': 'Cómo referirse al NNA', 'nna-treatment-text': 'Otra forma de referirse al NNA', 'nna-contact-instructions': 'Precauciones para el contacto', 'nna-extra-contact': 'Otra forma de contacto informada', 'nna-safe': 'Seguridad del contacto',
  nombres: 'Nombres', apellidos: 'Apellidos', primerNombre: 'Primer nombre', segundoNombre: 'Segundo nombre', primerApellido: 'Primer apellido', segundoApellido: 'Segundo apellido', fechaNacimiento: 'Fecha de nacimiento', sexo: 'Sexo registral', edad: 'Edad', runNumero: 'Número de RUN', runDv: 'Dígito verificador', region: 'Región', comuna: 'Comuna', datosDesconocidos: 'Datos del NNA desconocidos', alias: 'Alias', rol: 'Rol informado', nombre: 'Nombre', tipo: 'Tipo',
};
/** Atributos de identificacion; en SRCeI la nacionalidad es registral, no declarada. */
const ATTRIBUTE_LABELS: Record<string, string> = {
  nacionalidad: 'Nacionalidad', estadoCivil: 'Estado civil', fechaDefuncion: 'Fecha de defunción', fechaDeMuertePresunta: 'Fecha de muerte presunta', profesion: 'Profesión', circunscripcionNacimiento: 'Circunscripción de inscripción del nacimiento',
};
export const REGIONS: Record<string, string> = {'15': 'Arica y Parinacota', '01': 'Tarapacá', '02': 'Antofagasta', '03': 'Atacama', '04': 'Coquimbo', '05': 'Valparaíso', '13': 'Metropolitana de Santiago', '06': "Libertador General Bernardo O'Higgins", '07': 'Maule', '16': 'Ñuble', '08': 'Biobío', '09': 'La Araucanía', '14': 'Los Ríos', '10': 'Los Lagos', '11': 'Aysén del General Carlos Ibáñez del Campo', '12': 'Magallanes y de la Antártica Chilena'};
const OPTIONS: Record<string, Record<string, string>> = {
  motivo: {consulta: 'Consulta', vulneracion: 'Vulneración de derechos', promocion: 'Promoción de derechos'}, tema: {general: 'Consulta general', entrevista: 'Solicitud de entrevista', estadisticas: 'Solicitud de estadísticas'},
  'entrevista-rol': {estudiante: 'Estudiante', independiente: 'Independiente', prensa: 'Prensa', otro: 'Otro'},
  relacion: {madre: 'Madre', padre: 'Padre', cuidador: 'Cuidador o cuidadora', 'otro-familiar': 'Otro familiar', docente: 'Docente', salud: 'Personal de salud', testigo: 'Testigo', organizacion: 'Organización', otro: 'Otra relación'},
  'nna-treatment': {ella: 'Ella', el: 'Él', elle: 'Elle', nombre: 'Usar mi nombre', otra: 'Otra forma', noresponde: 'Prefiero no responder'}, 'nna-safe': {yes: 'Sí', unsure: 'No está seguro o segura', no: 'Necesita acordar otra forma'},
};
const BOOLEAN_FIELDS = ['consulta-confirmada', 'stats-confirm', 'identifica-responsables', 'datosDesconocidos', 'nna-extra-contact', 'riesgo', 'organizacion'];
const lookup = (map: Record<string, string>, value: string) => Object.hasOwn(map, value) ? map[value] : value;

export function fieldLabel(key: string): string {
  if (Object.hasOwn(FIELD_LABELS, key)) return FIELD_LABELS[key];
  const group = /^grupo-nna-([1-5])-(nombre|descripcion|cantidad|edades|region|comuna)$/.exec(key);
  if (group) return `Grupo de NNA ${group[1]} · ${lookup({nombre: 'nombre', descripcion: 'descripción colectiva', cantidad: 'cantidad informada', edades: 'edades', region: 'región', comuna: 'comuna'}, group[2])}`;
  const c = /^nna-(primary|secondary)-(method|celular|email|network|other-network|handle)$/.exec(key);
  if (c) return `${c[1] === 'primary' ? 'Contacto principal' : 'Contacto alternativo'} · ${lookup({method: 'forma de contacto', celular: 'celular', email: 'correo electrónico', network: 'red social', 'other-network': 'otra red social', handle: 'cuenta'}, c[2])}`;
  return key.replaceAll('-', ' ').replaceAll('_', ' ');
}
export function attributeLabel(key: string): string {
  const truncated = /^(.*)Truncada$/.exec(key);
  if (truncated) return attributeLabel(truncated[1]) + ' (fecha incompleta)';
  return Object.hasOwn(ATTRIBUTE_LABELS, key) ? ATTRIBUTE_LABELS[key] : fieldLabel(key);
}
export function fieldValue(key: string, value: string | null): string {
  if (value === null || value === '') return 'Sin dato';
  if (key === 'region' || key.endsWith('-region')) return lookup(REGIONS, value);
  if (/^nna-(primary|secondary)-method$/.test(key)) return lookup({celular: 'Celular', email: 'Correo electrónico', social: 'Red social', unsafe: 'No tiene una forma segura de contacto'}, value);
  if (Object.hasOwn(OPTIONS, key)) return lookup(OPTIONS[key], value);
  if (BOOLEAN_FIELDS.includes(key)) return lookup({true: 'Sí', false: 'No', si: 'Sí', no: 'No'}, value);
  return value;
}

export const SOURCE_LABELS: Record<string, string> = {
  CLAVE_UNICA_USERINFO: 'ClaveÚnica', SRCEI: 'Registro Civil (SRCeI)', DECLARACION_CIUDADANA: 'Declaración ciudadana', SIN_DATO: 'Sin dato', MIXTA: 'Fuentes distintas por componente',
};
export const LEVEL_LABELS: Record<string, string> = {
  IDENTIDAD_AUTENTICADA: 'Identidad autenticada', CONTRASTADO_EN_FUENTE: 'Contrastado en registro; no autentica', DECLARADO_NO_CONTRASTADO: 'Declarado; no contrastado',
};
export const EXTERNAL_ROLE_LABELS: Record<string, string> = {
  PRESENTADOR: 'Persona que presenta', AFECTADO_NNA: 'NNA afectado', ADULTO_RELACIONADO: 'Adulto relacionado', PERSONA_SENALADA: 'Persona señalada',
};
export const INTAKE_KIND_LABELS: Record<string, string> = {
  ADULTO_CU: 'Persona adulta autenticada con ClaveÚnica', ADULTO_DECLARADO: 'Persona adulta con identificación declarada', NNA: 'Niño, niña o adolescente',
};
export const REGISTRY_LABELS: Record<string, string> = {
  NO_HABILITADO: 'Consulta registral no habilitada', COMPLETO: 'Consulta registral completa', PARCIAL: 'Consulta registral parcial', VACIO: 'Sin antecedentes registrales',
  NEGATIVA: 'Consulta registral negativa', RED: 'Consulta fallida (red)', TIMEOUT: 'Consulta fallida (tiempo de espera)', AUTORIZACION: 'Consulta sin autorización',
  INDISPONIBLE: 'Servicio registral no disponible', XML_INVALIDO: 'Respuesta registral inválida', RESPUESTA_EXCESIVA: 'Respuesta registral excesiva', SATURADO: 'Servicio registral saturado',
  RUN_DISCREPANTE: 'El RUN no coincide con el registro',
};
