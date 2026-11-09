/**
 * Redaccion visible del inicio. El servidor entrega titulos y ayudas sin tildes; aqui se presentan
 * con ortografia completa. Un texto no listado se muestra tal como llega (no se pierde informacion).
 */
const TEXT: Record<string, string> = {
  'Clasificacion y distribucion': 'Clasificación y distribución',
  'Necesitan nueva clasificacion': 'Necesitan una nueva clasificación',
  'No equivalen a gestion': 'No equivalen a gestión',
  'Coordinacion de mi sede': 'Coordinación de mi sede',
  'Direccion de mi unidad': 'Dirección de mi unidad',
  'Coordinacion de Requerimientos': 'Coordinación de Requerimientos',
  'Recibir, asignar, decidir y cerrar dentro de tus ambitos vigentes.': 'Recibe, asigna, decide y cierra dentro de tus ámbitos vigentes.',
  'Todavia no son REQ': 'Todavía no son REQ',
  'Leer fundamento y condiciones': 'Lee el fundamento y las condiciones',
  'Colaboracion o traslado': 'Colaboración o traslado',
  'Cierre expreso; no automatico': 'Cierre expreso, nunca automático',
  'Solicitudes que requieren tu decision': 'Solicitudes que requieren tu decisión',
  'Revisar observaciones': 'Revisa las observaciones',
  'Fecha personal, no SLA institucional': 'Fecha personal, no un plazo institucional',
  'Proximos recordatorios (7 dias)': 'Próximos recordatorios (7 días)',
  'Atencion ciudadana': 'Atención ciudadana',
  'Un REQ desde el primer contacto; datos parciales y entregas sin una SOL artificial.': 'Un REQ desde el primer contacto, con datos parciales y entregas sin una SOL artificial.',
  'Continuar ficha y registrar resultado': 'Continúa la ficha y registra el resultado',
  'Recibir o devolver con fundamento': 'Recíbelas o devuélvelas con fundamento',
  'Aun bajo encargo de origen': 'Siguen a cargo de quien las envió',
  'Jefatura abre Caso en el mismo REQ': 'La jefatura abre el Caso en el mismo REQ',
  'Atenciones iniciadas por mi': 'Atenciones iniciadas por mí',
  'Una atencion, varios Casos posibles': 'Una atención, varios Casos posibles',
  'Lineas de gestion': 'Líneas de gestión',
  'Periodo vigente': 'Período vigente',
  'Administracion tecnica': 'Administración técnica',
  'Usuarios y operacion. Este perfil no concede lectura de expedientes.': 'Usuarios y operación. Este perfil no concede lectura de expedientes.',
  'Solo metadatos tecnicos': 'Solo metadatos técnicos',
  'Auditoria autorizada': 'Auditoría autorizada',
  'Consulta de trazabilidad limitada a los ambitos concedidos.': 'Consulta de trazabilidad limitada a los ámbitos concedidos.',
  'No hay una funcion operativa asignada. Contacta a la autoridad habilitada.': 'No tienes una función operativa asignada. Contacta a la autoridad habilitada.',
  'Informacion personal': 'Información personal',
};

export const say = (value: string) => TEXT[value] ?? value;

/** Metricas que piden una accion de quien las ve cuando son mayores que cero. */
export const ACTIONABLE = new Set(['com/pending', 'sol/returned', 'sol/pending', 'cas/review', 'flujos/incoming', 'cas/unacknowledged', 'cas/returned', 'tareas/overdue', 'atenciones/incoming', 'atenciones/followup', 'avisos/unread']);

/** Saludo segun la hora de Chile continental. */
export function greeting() {
  const hour = Number(new Intl.DateTimeFormat('es-CL', {hour: 'numeric', hourCycle: 'h23', timeZone: 'America/Santiago'}).format(new Date()));
  return hour < 12 ? 'Buenos días' : hour < 20 ? 'Buenas tardes' : 'Buenas noches';
}

export const today = () => {
  const s = new Intl.DateTimeFormat('es-CL', {weekday: 'long', day: 'numeric', month: 'long', timeZone: 'America/Santiago'}).format(new Date());
  return s.charAt(0).toLocaleUpperCase('es-CL') + s.slice(1);
};
