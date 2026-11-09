// Ficha 1.1: identificacion y contacto estructurados de la persona adulta (modalidad GENERAL). La ficha NNA conserva
// sus campos. Todo es opcional salvo el motivo; los datos son declarados y nunca se presentan como verificados.
import {communesOf, isCountry, matchCommune, matchCountry, matchRegion, NATIONALITY_PREFIX, regionName, validCommune, validNationality, validRegion} from './catalog.ts';
import {birthDateProblem, canonicalMobile, cleanEmail, documentNumberProblem, formatRunNumber, MOBILE_PATTERN, nameProblem, normalizeDocumentNumber, normalizeName, pastedRun, validEmail, validRun} from './identity.ts';

export const SCHEMA_VERSION = '1.1';
export const NAME_KEYS = ['presenter_first_name', 'presenter_middle_name', 'presenter_last_name', 'presenter_second_last_name'] as const;
/** Campos nuevos de 1.1; en una ficha 1.0 no existen (se agregan vacios al editarla). */
export const ADULT_KEYS = [...NAME_KEYS, 'presenter_run_number', 'presenter_run_dv', 'presenter_birth_date', 'presenter_document_kind', 'presenter_document_number'] as const;
export const DOCUMENT_KINDS: [string, string][] = [['PASAPORTE', 'Pasaporte'], ['IDENTIDAD_EXTRANJERA', 'Documento de identidad extranjero'], ['OTRO', 'Otro documento de identificación']];
export const ADULT_IDENTITY_TYPES: [string, string][] = [['RUN', 'RUN chileno'], ['OTRO', 'Documento extranjero u otro documento'], ['NO_DISPONIBLE', 'No dispone o no informa documento']];

type Form = Record<string, unknown>;
const str = (form: Form, key: string) => String(form[key] ?? '');

/**
 * Prepara para edicion una ficha guardada. Las versiones guardadas no cambian: solo el borrador en pantalla pasa a 1.1.
 * Para la modalidad GENERAL solo se aplican conversiones equivalentes (mismo celular normalizado, region o pais
 * reconocidos en el catalogo, RUN completo y valido en sus partes). Un valor antiguo que no corresponde al catalogo
 * se conserva tal cual: el servidor lo admite mientras no cambie y la pantalla lo muestra como registrado antes.
 */
export function upgrade(form: Form): Form {
  const out: Form = {...form};
  for (const key of ADULT_KEYS) if (out[key] == null) out[key] = '';
  const legacy = str(form, 'schema_version') !== SCHEMA_VERSION;
  out.schema_version = SCHEMA_VERSION;
  if (!legacy || out.modality === 'NNA') return out;
  const phone = canonicalMobile(str(out, 'contact_phone'));
  if (phone) out.contact_phone = phone;
  out.contact_email = cleanEmail(str(out, 'contact_email'));
  const region = matchRegion(str(out, 'region'));
  if (region) {
    out.region = region;
    const commune = matchCommune(region, str(out, 'commune'));
    if (commune) out.commune = commune;
  }
  const nationality = matchCountry(str(out, 'nationality'));
  if (nationality) out.nationality = nationality;
  const run = out.identity_type === 'RUN' ? pastedRun(str(out, 'identity_document')) : null;
  if (run && validRun(run.numero, run.dv)) {out.presenter_run_number = run.numero; out.presenter_run_dv = run.dv; out.identity_document = '';}
  return out;
}

/** Valor antiguo fuera de catalogo o formato: se conserva hasta que se reemplace. */
export function legacyValue(form: Form, key: string): string {
  const value = str(form, key);
  if (!value) return '';
  switch (key) {
    case 'region': return validRegion(value) ? '' : value;
    case 'commune': return validRegion(str(form, 'region')) && validCommune(str(form, 'region'), value) ? '' : value;
    case 'contact_phone': return MOBILE_PATTERN.test(value) ? '' : value;
    case 'nationality': return isCountry(value) || value.startsWith(NATIONALITY_PREFIX) ? '' : value;
    case 'identity_document': return value;
    default: return '';
  }
}

/** Nombre compuesto desde las partes; '' si no hay ninguna. */
export const fullName = (form: Form) => NAME_KEYS.map(k => normalizeName(str(form, k))).filter(Boolean).join(' ');
/** Documento resumido para la COM y la ficha (dato declarado). */
export function documentSummary(form: Form): string {
  if (form.identity_type === 'RUN' && str(form, 'presenter_run_number') && str(form, 'presenter_run_dv')) return `${formatRunNumber(str(form, 'presenter_run_number'))}-${str(form, 'presenter_run_dv').toUpperCase()}`;
  if (form.identity_type === 'OTRO' && str(form, 'presenter_document_number')) {
    const kind = DOCUMENT_KINDS.find(([v]) => v === form.presenter_document_kind)?.[1] ?? 'Documento';
    return `${kind} ${normalizeDocumentNumber(str(form, 'presenter_document_number'))}`;
  }
  return '';
}

/**
 * Problemas de la persona adulta, con las reglas del servidor. Un valor igual al de la version anterior (dato
 * historico) no se revalida. Vacio nunca es un problema: la ficha admite informacion parcial.
 */
export function adultProblems(form: Form, previous: Form = {}): Record<string, string> {
  const errors: Record<string, string> = {};
  if (form.modality === 'NNA') return errors;
  const kept = (key: string) => str(form, key) !== '' && str(form, key) === str(previous, key);
  const check = (key: string, problem: string | null) => {if (problem && !kept(key)) errors[key] = problem;};
  for (const key of NAME_KEYS) check(key, nameProblem(str(form, key)));
  check('presenter_birth_date', birthDateProblem(str(form, 'presenter_birth_date')));
  const type = str(form, 'identity_type'), number = str(form, 'presenter_run_number'), dv = str(form, 'presenter_run_dv');
  if (type === 'RUN' && (number || dv)) {
    if (!number || !dv) errors.presenter_run_number = 'Completa el número y el dígito verificador, o deja el RUN en blanco.';
    else if (!validRun(number, dv)) errors.presenter_run_number = 'El número y el dígito verificador no coinciden.';
  }
  if (type === 'OTRO') check('presenter_document_number', documentNumberProblem(str(form, 'presenter_document_number')));
  const phone = str(form, 'contact_phone');
  if (phone && !MOBILE_PATTERN.test(phone)) check('contact_phone', 'Escribe los ocho dígitos después de +56 9.');
  const email = str(form, 'contact_email');
  if (email && !validEmail(email)) check('contact_email', 'Revisa el formato del correo electrónico.');
  const region = str(form, 'region'), commune = str(form, 'commune');
  if (region && !validRegion(region)) check('region', 'Selecciona una región del listado.');
  if (commune && !(validRegion(region) && validCommune(region, commune))) check('commune', 'Selecciona una comuna de la región indicada.');
  const nationality = str(form, 'nationality');
  if (nationality && !validNationality(nationality)) check('nationality', nationality.startsWith(NATIONALITY_PREFIX) ? 'Indica la nacionalidad o situación especial.' : 'Selecciona un país o la opción Otra / situación especial.');
  return errors;
}

/** Texto visible de un campo 1.1 en la vista imprimible y el resumen. */
export function displayValue(key: string, value: string): string {
  if (key === 'region') return regionName(value);
  if (key === 'presenter_run_number') return formatRunNumber(value);
  if (key === 'presenter_document_kind') return DOCUMENT_KINDS.find(([v]) => v === value)?.[1] ?? value;
  if (key === 'nationality' && value.startsWith(NATIONALITY_PREFIX)) return 'Otra / situación especial: ' + value.slice(NATIONALITY_PREFIX.length);
  return value;
}
export const communesFor = communesOf;
