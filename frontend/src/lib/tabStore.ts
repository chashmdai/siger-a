/**
 * Estado de la pestana (G08b, G08c): el termino del buscador y el texto de las bandejas viven solo en sessionStorage,
 * nunca en la ruta, y se borran al cerrar o perder la sesion (App.tsx). Es la unica excepcion aprobada a no guardar
 * datos sensibles en el navegador.
 */
export type Store = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

/** sessionStorage de la pestana o null si el navegador lo bloquea (todo sigue funcionando, sin poder volver). */
export function sessionStore(): Store | null {
  try {return globalThis.sessionStorage ?? null;} catch {return null;}
}
