/**
 * Textos visibles para los codigos que entrega o recibe el servidor. Solo cambian la presentacion:
 * el valor enviado en cada comando sigue siendo el codigo original.
 */
export const KIND_LABELS: Record<string, string> = {COM: 'Comunicación', SOL: 'Solicitud', REQ: 'Requerimiento', CAS: 'Caso', FLUJO: 'Petición entre unidades'};
export const KIND_SHORT: Record<string, string> = {COM: 'COM', SOL: 'SOL', REQ: 'REQ', CAS: 'Caso', FLUJO: 'Flujo'};

export const ROLE_LABELS: Record<string, string> = {
  ADMINISTRATIVO: 'Administrativo', PROFESIONAL: 'Profesional', JEFATURA: 'Jefatura', DIRECCION: 'Dirección', DISTRIBUIDOR: 'Distribución',
  AUTORIDAD: 'Autoridad', LECTURA: 'Lectura', AUDITORIA: 'Auditoría', TI: 'TI',
};

export const CHANNEL_LABELS: Record<string, string> = {PRESENCIAL: 'Presencial', TELEFONO: 'Teléfono', CORREO: 'Correo', OFICIO: 'Oficio', OTRO: 'Otro'};
export const RESULT_LABELS: Record<string, string> = {ORIENTACION: 'Orientación', RESPUESTA_DIRECTA: 'Respuesta directa', REMISION_CONCLUIDA: 'Remisión concluida'};
export const DOCUMENT_LABELS: Record<string, string> = {OFICIO: 'Oficio', INFORME: 'Informe', CORREO: 'Correo', CONSTANCIA: 'Constancia', OTRO: 'Otro', INGRESO: 'Ingreso'};
export const PERSON_ROLE_LABELS: Record<string, string> = {NNA: 'NNA', ACOMPANANTE: 'Acompañante', REQUIRENTE: 'Requirente', OTRA_PERSONA: 'Otra persona'};
export const COMPLEXITY_LABELS: Record<string, string> = {BAJA: 'Baja', MEDIANA: 'Mediana', ALTA: 'Alta'};
export const FLOW_LABELS: Record<string, string> = {DERIVACION: 'Derivación', COLABORACION: 'Colaboración', TRANSFERENCIA: 'Transferencia'};

export const STATE_LABELS: Record<string, string> = {
  NUEVA: 'Nueva', ENVIADA: 'Enviada', ASUMIDA: 'Asumida', DEVUELTA: 'Devuelta', DESCARTADA: 'Descartada', VINCULADA: 'Vinculada', ATENDIDA: 'Atendida',
  ABIERTO: 'Abierto', CERRADO: 'Cerrado', EN_GESTION: 'En gestión', EN_ESPERA: 'En espera', EN_REVISION: 'En revisión de cierre',
  PENDIENTE: 'Pendiente', EN_CURSO: 'En curso', COMPLETADA: 'Completada', CANCELADA: 'Cancelada',
  ACEPTADA: 'Aceptada', RECHAZADA: 'Rechazada', RETIRADA: 'Retirada', ADMITIDA: 'Admitida', RECIBIDA: 'Recibida',
  ACTIVA: 'Activa', PROGRAMADA: 'Programada', TERMINADA: 'Terminada', EXPIRADA: 'Expirada',
  EN_ATENCION: 'Primera atención en curso', PENDIENTE_RECEPCION: 'Entrega por recibir', REQUIERE_CASO: 'Seguimiento pendiente de Caso',
  CON_CASO: 'Continúa en Caso', RESUELTA: 'Atención resuelta', RESERVADA: 'Reservada', INVALIDA: 'Inválida', DUPLICADA: 'Duplicada',
  BAJA: 'Baja', MEDIANA: 'Mediana', ALTA: 'Alta',
};

/** Comandos registrados en la historia, en lenguaje de la accion realizada. */
export const ACTION_LABELS: Record<string, string> = {
  COM_CREATE: 'Comunicación registrada', COM_ROUTE: 'SOL distribuida', COM_LINK: 'Vínculo a REQ propuesto', COM_ASSUME: 'Ingreso asumido por jefatura',
  SOL_ASSUME: 'SOL asumida', SOL_ROUTE: 'SOL redirigida', SOL_RETURN: 'SOL devuelta a Distribución', SOL_DISCARD: 'SOL declarada inválida o duplicada',
  LINK_DECIDE: 'Antecedente evaluado', LINK_RETURN: 'Antecedente devuelto a clasificación',
  REQ_CLASSIFY: 'Complejidad clasificada', REQ_CASE_CREATE: 'Caso abierto', REQ_CLOSE: 'REQ cerrado', REQ_NOTE: 'Atención sin Caso registrada',
  FLOW_REQUEST: 'Petición entre unidades solicitada', FLOW_DECIDE: 'Petición entre unidades decidida',
  REOPEN_REQUEST: 'Nueva gestión solicitada', REOPEN_AUTHORIZE: 'Reactivación autorizada', REOPEN_DECIDE: 'Nueva gestión decidida',
  CASE_NOTE: 'Actuación registrada', CASE_ACK: 'Asignación acusada', CASE_MEMBER_ADD: 'Integrante incorporado', CASE_MEMBER_REMOVE: 'Integrante retirado',
  CASE_REQUEST_CLOSE: 'Término solicitado', CASE_WITHDRAW_CLOSE: 'Solicitud de término retirada', CASE_CLOSE: 'Caso cerrado', CASE_ASSIGN: 'Responsable reasignado',
  CASE_MATTER: 'Materia rectificada', CASE_WAIT: 'Caso en espera', CASE_RESUME: 'Gestión reanudada', CASE_RETURN: 'Término devuelto con observaciones',
  NOTE_RECTIFY: 'Actuación rectificada', TASK_CREATE: 'Tarea creada', TASK_COMPLETE: 'Tarea completada', TASK_ATTEMPT: 'Intento registrado', TASK_CANCEL: 'Tarea cancelada', TASK_EDIT: 'Tarea reprogramada',
  ATTENTION_CREATE: 'Primera atención registrada', ATTENTION_SAVE: 'Ficha complementada', ATTENTION_SEND: 'Atención entregada', ATTENTION_DECIDE: 'Entrega decidida', ATTENTION_RESOLVE: 'Atención resuelta',
};

/** Codigo legible: etiqueta conocida o el codigo en frase ("ATENCION_INICIADA" -> "Atencion iniciada"). */
export function humanize(code: unknown, labels: Record<string, string> = STATE_LABELS) {
  const v = String(code ?? '');
  if (!v) return '';
  if (labels[v]) return labels[v];
  const plain = v.replaceAll('_', ' ').toLocaleLowerCase('es-CL');
  const sentence = plain.charAt(0).toLocaleUpperCase('es-CL') + plain.slice(1);
  return sentence.split(' ').map(w => ['req', 'com', 'sol', 'ti'].includes(w.toLowerCase()) ? w.toUpperCase() : w).join(' ');
}

/** Opciones de select con etiqueta legible y el mismo valor. */
export const labeled = (values: string[], labels: Record<string, string>) => values.map(v => ({value: v, label: labels[v] ?? humanize(v)}));

export const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]?.toLocaleUpperCase('es-CL')).join('') || '·';
export const firstName = (name: string) => name.split(/\s+/)[0] ?? name;
