import type {Row} from './rows';
import {text} from './rows';
import type {User} from './access';

export type Session = {authenticated: boolean; environment: string; user?: User; csrf: {token: string; header: string}};
export type Result = {kind: string; id: number; message: string};

let csrf: Session['csrf'] | undefined;

const GENERIC_ERRORS: Record<number, string> = {
  401: 'Sesion finalizada o credenciales incorrectas.',
  403: 'Accion no autorizada o sesion obsoleta. Actualiza la pantalla.',
  428: 'Debes cambiar tu clave temporal.',
  429: 'Demasiados intentos. Espera antes de volver a ingresar.',
};

// Solicitudes en curso: la barra de progreso global las observa mediante el evento "siger-busy".
let inflight = 0;
function track(delta: number) {
  inflight += delta;
  window.dispatchEvent(new CustomEvent('siger-busy', {detail: inflight}));
}

/** Solicitud a /api con sesion y token CSRF. Un 401/428 avisa a la aplicacion para recargar la sesion. */
export async function request<T = Row>(path: string, options: RequestInit = {}): Promise<T> {
  track(1);
  try {
    return await send<T>(path, options);
  } finally {
    track(-1);
  }
}

async function send<T>(path: string, options: RequestInit): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.method && options.method !== 'GET' && csrf) headers.set(csrf.header, csrf.token);
  const response = await fetch('/api' + path, {...options, headers, credentials: 'same-origin', cache: 'no-store'});
  if (!response.ok) {
    if (response.status === 401 || response.status === 428) window.dispatchEvent(new Event('siger-session'));
    const body: Row = await response.json().catch(() => ({}));
    const message = text(body, 'message') || GENERIC_ERRORS[response.status] || `No se pudo confirmar la operacion (${response.status}). Reintenta sin cambiar los datos.`;
    throw Object.assign(new Error(message), {status: response.status});
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function session() {
  const s = await request<Session>('/session');
  csrf = s.csrf;
  return s;
}

/** Comando de negocio. La misma clave en un reintento evita registrar dos veces. */
export async function command(type: string, id: number | null, revision: number | null, data: Row, key: string) {
  return request<Result>('/commands', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({key, type, id, revision, data})});
}

export const postJson = (path: string, body: unknown) =>
  request(path, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});

// --- Documentos ---------------------------------------------------------------------------
/** Original o documento de gestion; siempre queda en cuarentena. */
export function uploadDocument(fields: Record<string, string | undefined>, file: File) {
  const data = new FormData();
  for (const [k, v] of Object.entries(fields)) if (v !== undefined) data.set(k, v);
  data.set('file', file);
  return request('/documents', {method: 'POST', body: data});
}
/** Adjunto preparado para un ingreso todavia no confirmado. */
export function stageIntakeDocument(scope: string, key: string, file: File) {
  const data = new FormData();
  data.set('scope', scope);
  data.set('key', key);
  data.set('file', file);
  return request('/intake/documents', {method: 'POST', body: data});
}
export const draftDocuments = () => request<Row[]>('/intake/documents');
export const discardDraft = (token: string) => request('/intake/documents/' + encodeURIComponent(token), {method: 'DELETE'});
