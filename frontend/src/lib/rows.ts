/** Fila JSON devuelta por la API. Los accesores toleran campos ausentes o nulos. */
export type Row = Record<string, unknown>;

export const text = (r: Row, k: string) => r[k] == null ? '' : String(r[k]);
export const number = (r: Row, k: string) => Number(r[k] ?? 0);
export const bool = (r: Row, k: string) => r[k] === true || r[k] === 1;
export const list = (r: Row, k: string): Row[] => Array.isArray(r[k]) ? r[k] as Row[] : [];
export const obj = (r: Row, k: string): Row => r[k] != null && typeof r[k] === 'object' && !Array.isArray(r[k]) ? r[k] as Row : {};
export const ids = (r: Row, k: string): number[] => Array.isArray(r[k]) ? (r[k] as unknown[]).map(Number) : [];

/** Fecha visible en America/Santiago, formato corto chileno. */
export function when(value: unknown) {
  if (value == null || value === '') return '-';
  return new Intl.DateTimeFormat('es-CL', {dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Santiago'}).format(new Date(Number(value)));
}

/** Valor para <input type="datetime-local"> en la zona del navegador, desplazado en milisegundos. */
export function localTime(delta = 0) {
  const d = new Date(Date.now() + delta);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
