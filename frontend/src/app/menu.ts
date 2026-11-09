import type {User} from '../lib/access';
import {has} from '../lib/access';
import type {IconName} from '../components/Icon';

export type MenuItem = {path: string; label: string; icon: IconName; badge?: number; hint?: string};
export type MenuGroup = {title: string; items: MenuItem[]};

/**
 * Navegacion segun funciones vigentes, agrupada por el tipo de trabajo. Ocultar una entrada no
 * reemplaza la autorizacion del servidor.
 */
export function menuFor(user: User, unread: number): MenuGroup[] {
  const management = has(user, 'JEFATURA', 'DIRECCION');
  const operative = has(user, 'ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION', 'DISTRIBUIDOR');
  const read = operative || has(user, 'LECTURA');
  const groups: [string, [MenuItem, boolean][]][] = [
    ['Mi trabajo', [
      [{path: 'inicio', label: 'Inicio', icon: 'home', hint: 'Pendientes y cifras de tus funciones'}, true],
      [{path: 'tareas', label: 'Mis tareas', icon: 'tasks', hint: 'Recordatorios de tus Casos'}, operative],
      [{path: 'avisos', label: 'Mis avisos', icon: 'bell', badge: unread || undefined, hint: 'Avisos internos'}, true],
    ]],
    ['Atención ciudadana', [
      [{path: 'atencion', label: 'Nueva atención', icon: 'attention', hint: 'Registrar un primer contacto (crea COM y REQ)'}, has(user, 'ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION')],
      [{path: 'atenciones', label: 'Primeras atenciones', icon: 'people', hint: 'Fichas en curso, entregas y seguimiento'}, read],
      [{path: 'nuevo', label: 'Ingreso por clasificar', icon: 'inbox', hint: 'Registrar una comunicación externa (solo COM)'}, operative],
    ]],
    ['Gestión', [
      [{path: 'com', label: 'Comunicaciones', icon: 'mail'}, read],
      [{path: 'sol', label: 'Solicitudes', icon: 'send', hint: 'SOL pendientes y devueltas'}, management || has(user, 'DISTRIBUIDOR')],
      [{path: 'req', label: 'Requerimientos', icon: 'folder'}, read],
      [{path: 'cas', label: 'Casos', icon: 'briefcase'}, has(user, 'PROFESIONAL', 'JEFATURA', 'DIRECCION', 'LECTURA')],
      [{path: 'flujos', label: 'Colaboraciones y traslados', icon: 'flow'}, management],
    ]],
    ['Administración', [
      [{path: 'subrogancias', label: 'Subrogancias', icon: 'swap'}, has(user, 'PROFESIONAL', 'JEFATURA', 'DIRECCION', 'AUTORIDAD')],
      [{path: 'administracion', label: 'Usuarios y permisos', icon: 'shield'}, has(user, 'TI', 'AUTORIDAD')],
      [{path: 'auditoria', label: 'Auditoría y operación', icon: 'activity'}, has(user, 'TI', 'AUDITORIA')],
    ]],
  ];
  return groups.map(([title, items]) => ({title, items: items.filter(([, visible]) => visible).map(([item]) => item)})).filter(g => g.items.length > 0);
}

/** Titulo de la entrada activa, para el encabezado movil y la paleta. */
export const menuItems = (groups: MenuGroup[]) => groups.flatMap(g => g.items);
