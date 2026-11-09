// Reglas de identificacion y contacto DECLARADOS, portadas del Formulario Publico (run.ts, personalData.ts y
// contacts.ts). El servidor (AdultIdentity.java) aplica las mismas reglas y es la autoridad. Ninguna acredita identidad,
// existencia registral ni titularidad: SIGER no consulta ClaveUnica, el Registro Civil ni PISEE.

/** Solo validacion matematica del digito verificador. */
export function validRun(numero: string, dv: string): boolean {
  if (!/^[1-9][0-9]{0,7}$/.test(numero) || !/^[0-9Kk]$/.test(dv)) return false;
  let sum = 0, multiplier = 2;
  for (let i = numero.length - 1; i >= 0; i--) {sum += Number(numero[i]) * multiplier; multiplier = multiplier === 7 ? 2 : multiplier + 1;}
  const value = 11 - sum % 11;
  return dv.toUpperCase() === (value === 11 ? '0' : value === 10 ? 'K' : String(value));
}
export const runNumber = (input: string) => input.replace(/\D/g, '').slice(0, 8);
export const formatRunNumber = (input: string) => runNumber(input).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
/** RUN completo pegado o escrito ("12.345.678-5", "123456785"); null si no es un RUN completo. */
export function pastedRun(input: string): {numero: string; dv: string} | null {
  const compact = input.trim().replace(/[.\s]/g, '');
  const explicit = /^([0-9]{1,8})-([0-9Kk])$/.exec(compact);
  if (explicit) return {numero: explicit[1], dv: explicit[2].toUpperCase()};
  if (/^[0-9]{8}[0-9Kk]$/.test(compact) || /^[0-9]{1,8}[Kk]$/.test(compact)) return {numero: compact.slice(0, -1), dv: compact.slice(-1).toUpperCase()};
  return null;
}

export const NAME_MAX_LENGTH = 100;
export const DOCUMENT_MAX_LENGTH = 40;
const WORD = '\\p{L}[\\p{L}\\p{M}]*';
const NAME_SHAPE = new RegExp(`^${WORD}(?:(?:[ '\\-]|\\. ?)${WORD})*\\.?$`, 'u');
const TYPOGRAPHIC_APOSTROPHES = /[‘’`´]/gu;
/** NFC, apostrofo recto y un espacio entre palabras. */
export function normalizeName(value: string): string {
  return value.normalize('NFC').replace(TYPOGRAPHIC_APOSTROPHES, "'").replace(/\s+/gu, ' ').trim();
}
/** Admite una inicial: quien atiende registra lo que la persona entrega (dato parcial). */
export function nameProblem(raw: string): string | null {
  const value = normalizeName(raw);
  if (!value) return null;
  if (/\p{N}/u.test(value)) return 'No incluyas números.';
  if (/[^\p{L}\p{M} '.-]/u.test(value)) return "Usa solo letras, espacios, guion (-), apóstrofo (') o punto.";
  if (value.length > NAME_MAX_LENGTH) return `Admite hasta ${NAME_MAX_LENGTH} caracteres.`;
  if (!NAME_SHAPE.test(value)) return 'Los espacios y signos deben ir entre letras, como en «Pérez-Cotapos» u «O\'Higgins».';
  return null;
}
export const normalizeDocumentNumber = (value: string) => value.normalize('NFC').replace(/\s+/gu, ' ').trim();
export function documentNumberProblem(raw: string): string | null {
  const value = normalizeDocumentNumber(raw);
  if (!value) return null;
  if (value.length > DOCUMENT_MAX_LENGTH) return `Admite hasta ${DOCUMENT_MAX_LENGTH} caracteres.`;
  if (/[^\p{L}\p{N} ./-]/u.test(value)) return 'Usa solo letras, números, espacios, puntos, guiones o barras.';
  if ((value.match(/[\p{L}\p{N}]/gu) || []).length < 3) return 'Escribe el número completo, tal como aparece en el documento.';
  return null;
}
const localDate = (d: Date) => new Intl.DateTimeFormat('en-CA', {timeZone: 'America/Santiago', year: 'numeric', month: '2-digit', day: '2-digit'}).format(d);
export const today = () => localDate(new Date());
/** Fecha real, no futura y dentro de 130 anos (misma ventana que el servidor, hora de Chile). */
export function birthDateProblem(value: string, now = new Date()): string | null {
  if (!value) return null;
  const parsed = new Date(`${value}T12:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) return 'Revisa la fecha: usa día, mes y año válidos.';
  const current = localDate(now);
  if (value > current) return 'La fecha de nacimiento no puede ser posterior a hoy.';
  if (value < `${Number(current.slice(0, 4)) - 130}${current.slice(4)}`) return 'Revisa el año de nacimiento.';
  return null;
}

// Contactos declarados: el formato no acredita existencia ni titularidad.
export const cleanEmail = (value: string) => value.trim();
export function validEmail(raw: string) {
  if (/[\r\n\0]/.test(raw)) return false;
  const value = cleanEmail(raw), parts = value.split('@');
  if (value.length > 254 || parts.length !== 2) return false;
  const [local, domain] = parts;
  if (!local || local.length > 64 || local.startsWith('.') || local.endsWith('.') || local.includes('..') || /[\s@<>()\[\]:;,"\\\x00-\x1f\x7f]/u.test(local)) return false;
  const labels = domain.split('.');
  return labels.length >= 2 && labels.every(label => /^[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?$/u.test(label));
}
/** Celular chileno normalizado +569XXXXXXXX; '' si vacio; null si no es un celular chileno completo. */
export function canonicalMobile(raw: string): string | null {
  const value = raw.trim();
  if (!value) return '';
  if (!/^\+?[0-9\s().-]+$/.test(value)) return null;
  const digits = value.replace(/\D/g, '');
  if (value.startsWith('+')) return /^569[0-9]{8}$/.test(digits) ? '+' + digits : null;
  if (/^[0-9]{8}$/.test(digits)) return '+569' + digits;
  if (/^9[0-9]{8}$/.test(digits)) return '+56' + digits;
  if (/^569[0-9]{8}$/.test(digits)) return '+' + digits;
  if (/^00569[0-9]{8}$/.test(digits)) return '+' + digits.slice(2);
  return null;
}
export const MOBILE_PATTERN = /^\+569[0-9]{8}$/;
/**
 * Texto pegado en el campo de ocho digitos: numero completo (con o sin +56 9) o digitos parciales insertados en la
 * seleccion. Devuelve los ocho (o menos) digitos resultantes o un mensaje si no se incorpora.
 */
export function pasteMobile(text: string, shown: string, start: number, end: number): {local: string; cursor: number} | {error: string} {
  const canonical = canonicalMobile(text), digits = text.replace(/\D/g, '');
  const full = !!canonical && digits.length > 8, partial = /^[0-9\s().-]+$/.test(text) && digits.length > 0 && digits.length <= 8;
  if (!canonical && !partial) return {error: 'No se incorporó el número. Pega ocho dígitos o un celular chileno con prefijo +56 9.'};
  const next = full ? canonical!.slice(4) : shown.slice(0, start) + digits + shown.slice(end);
  if (next.length > 8) return {error: 'El número admite ocho dígitos. Selecciona el contenido que quieres reemplazar.'};
  return {local: next, cursor: full ? 8 : start + digits.length};
}
