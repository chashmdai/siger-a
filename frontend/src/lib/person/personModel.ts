// Persona declarada canonica (0.8.0, D33, gate E04; esquema persona-1 de DISENO 2.4) para la ficha 1.2 y la ficha de
// ingreso de la COM manual, con las mismas reglas que el servidor (DeclaredPerson.java, que es la autoridad). Nada se
// inventa: vacio nunca es error; un valor igual al de la version anterior se conserva sin revalidarse; los datos
// historicos (legacy_*) nunca se crean ni se reescriben (solo se conservan o se quitan) y un nombre historico no se
// divide. Los codigos de relacion y de tipo de institucion salen solo del catalogo estadistico VIGENTE; el servidor
// deriva su version (relationship_version, type_version) y la interfaz nunca la reenvia.
import {MOBILE_PATTERN, birthDateProblem, documentNumberProblem, formatRunNumber, nameProblem, normalizeDocumentNumber, normalizeName, validEmail, validRun} from './identity.ts';
import {NATIONALITY_PREFIX, regionName, validCommune, validNationality, validRegion} from './catalog.ts';

/** Campos de persona-1, en el orden de DeclaredPerson.FIELDS (una prueba compara ambas listas). */
export const PERSON_FIELDS = ['role', 'first_name', 'middle_name', 'last_name', 'second_last_name', 'legacy_name',
  'identity_type', 'run_number', 'run_dv', 'document_kind', 'document_number', 'legacy_document', 'nationality', 'legacy_nationality',
  'birth_date', 'age_years', 'legacy_age', 'region_code', 'commune', 'address', 'email', 'mobile', 'other_contact', 'preferred_name',
  'treatment', 'relationship_code', 'relationship_text', 'schooling', 'location_text'] as const;
export type PersonField = (typeof PERSON_FIELDS)[number];
export type DeclaredPerson = Record<PersonField, string>;
export const NAME_PARTS = ['first_name', 'middle_name', 'last_name', 'second_last_name'] as const;
export const LEGACY_FIELDS = ['legacy_name', 'legacy_document', 'legacy_nationality', 'legacy_age'] as const;
/** Equivalencias de una persona 1.0/1.1 (texto libre) con persona-1: el texto se conserva como historico (FROM_11). */
export const FROM_11: Record<string, PersonField> = {name: 'legacy_name', age: 'legacy_age', identity_document: 'legacy_document', nationality: 'legacy_nationality', relationship: 'relationship_text', location: 'location_text'};
/** Claves que deriva el servidor y que la interfaz no envia. */
export const DERIVED_KEYS = ['relationship_version', 'type_version'] as const;

export const IDENTITY_TYPES: [string, string][] = [['RUN', 'RUN chileno'], ['DOCUMENTO_EXTRANJERO', 'Documento extranjero u otro documento'], ['NO_DISPONIBLE', 'No dispone o no informa documento']];
export const DOCUMENT_KINDS: [string, string][] = [['PASAPORTE', 'Pasaporte'], ['IDENTIDAD_EXTRANJERA', 'Documento de identidad extranjero'], ['OTRO', 'Otro documento de identificación']];
/** Trato del Formulario (adult-treatment, nna-treatment; formulario-web 6f886eb). */
export const TREATMENTS: [string, string][] = [['ella', 'Ella'], ['el', 'Él'], ['elle', 'Elle'], ['nombre', 'Usar su nombre'], ['otra', 'Otra forma'], ['noresponde', 'No responde']];
export const ROLE_LABELS: Record<string, string> = {NNA: 'NNA', ACOMPANANTE: 'Acompañante', REQUIRENTE: 'Requirente', OTRA_PERSONA: 'Otra persona'};

/** Rotulos de persona-1 (ficha imprimible, vista de la ficha de ingreso y mensajes). */
export const PERSON_LABELS: Record<PersonField, string> = {
  role: 'Relación con la atención', first_name: 'Primer nombre', middle_name: 'Segundo nombre', last_name: 'Primer apellido', second_last_name: 'Segundo apellido',
  legacy_name: 'Nombre o referencia registrado anteriormente', identity_type: 'Identificación disponible', run_number: 'RUN (número)', run_dv: 'RUN (dígito verificador)',
  document_kind: 'Tipo de documento', document_number: 'Número de documento', legacy_document: 'Identificación registrada anteriormente', nationality: 'Nacionalidad',
  legacy_nationality: 'Nacionalidad registrada anteriormente', birth_date: 'Fecha de nacimiento', age_years: 'Edad (años cumplidos)', legacy_age: 'Edad registrada anteriormente',
  region_code: 'Región', commune: 'Comuna', address: 'Domicilio', email: 'Correo', mobile: 'Celular', other_contact: 'Otro medio de contacto', preferred_name: 'Nombre de preferencia',
  treatment: 'Forma de trato', relationship_code: 'Relación con el NNA', relationship_text: 'Vínculo (detalle)', schooling: 'Escolaridad', location_text: 'Domicilio o localización (texto)',
};

/** Persona vacia de un rol. */
export function emptyPerson(role: string): DeclaredPerson {
  const p = Object.fromEntries(PERSON_FIELDS.map(k => [k, ''])) as DeclaredPerson;
  p.role = role;
  return p;
}
const str = (v: unknown) => v == null ? '' : String(v);

/**
 * Vista canonica de una persona guardada (DeclaredPerson.canonical): persona-1 tal cual; una persona 1.0/1.1 con su
 * texto libre como historico, sin dividirlo ni interpretarlo. Quita las versiones de catalogo derivadas por el servidor.
 */
export function canonicalPerson(raw: Record<string, unknown>): DeclaredPerson {
  const p = emptyPerson(str(raw.role) || 'NNA');
  const legacy = Object.hasOwn(raw, 'name');
  for (const [key, value] of Object.entries(raw)) {
    const target = (legacy ? FROM_11[key] ?? key : key) as PersonField;
    if ((PERSON_FIELDS as readonly string[]).includes(target)) p[target] = str(value);
  }
  return p;
}
export const canonicalList = (raw: unknown): DeclaredPerson[] => Array.isArray(raw) ? raw.filter(x => x && typeof x === 'object').map(x => canonicalPerson(x as Record<string, unknown>)) : [];

/** Persona para enviar: solo los campos de persona-1, sin las claves derivadas. */
export function personPayload(p: DeclaredPerson): DeclaredPerson {
  return Object.fromEntries(PERSON_FIELDS.map(k => [k, str(p[k])])) as DeclaredPerson;
}
/** Persona sin datos (solo rol): por ejemplo, el presentador de un ingreso institucional (DeclaredPerson.blank). */
export const blankPerson = (p: Partial<DeclaredPerson>) => PERSON_FIELDS.every(k => k === 'role' || !str(p[k]).trim());

/** Nombre para mostrar: las partes unidas o el nombre historico. */
export function personName(p: Partial<DeclaredPerson>): string {
  const parts = NAME_PARTS.map(k => normalizeName(str(p[k]))).filter(Boolean).join(' ');
  return parts || str(p.legacy_name);
}
/** Documento resumido (dato declarado): RUN con formato, documento extranjero o el texto historico. */
export function personDocument(p: Partial<DeclaredPerson>): string {
  if (p.identity_type === 'RUN' && str(p.run_number) && str(p.run_dv)) return `${formatRunNumber(str(p.run_number))}-${str(p.run_dv).toUpperCase()}`;
  if (p.identity_type === 'DOCUMENTO_EXTRANJERO' && str(p.document_number)) {
    const kind = DOCUMENT_KINDS.find(([v]) => v === p.document_kind)?.[1] ?? 'Documento';
    return `${kind} ${normalizeDocumentNumber(str(p.document_number))}`;
  }
  return str(p.legacy_document);
}

// --- Catalogos estadisticos vigentes (RELACION, TIPO_INSTITUCION) ---------------------------------------------
export type CodeItem = {code: string; label: string; selectable: boolean; level: number};
/** Version VIGENTE publicada en catalog.stat_catalogs: sin vigente, items vacio (solo se registra el texto). */
export type CodeList = {version: number | null; items: CodeItem[];
  /** Rotulos de los codigos registrados en la ficha segun su version ("version:codigo" -> rotulo; code_labels, E06). */
  labels?: Record<string, string>};
export type StatCodes = {relationship: CodeList; institution: CodeList};
export const NO_CODES: StatCodes = {relationship: {version: null, items: []}, institution: {version: null, items: []}};
/** Codigos vigentes de relacion y tipo de institucion desde el catalogo de la sesion. */
export function statCodes(catalog: unknown): StatCodes {
  const lists = (catalog as {stat_catalogs?: {catalogs?: unknown[]}} | undefined)?.stat_catalogs?.catalogs ?? [];
  const of = (name: string): CodeList => {
    const c = lists.find(x => (x as {catalog?: unknown}).catalog === name) as {version?: {id?: unknown} | null; items?: unknown[]} | undefined;
    if (!c?.version) return {version: null, items: []};
    return {version: Number(c.version.id), items: (c.items ?? []).map(i => {
      const r = i as Record<string, unknown>;
      return {code: str(r.code), label: str(r.label), selectable: r.selectable !== false, level: Number(r.level ?? 1)};
    })};
  };
  return {relationship: of('RELACION'), institution: of('TIPO_INSTITUCION')};
}
export const selectableCodes = (list: CodeList) => list.items.filter(i => i.selectable);
/**
 * Rotulos por version que entrega la ficha (code_labels, gate E06; adoptados en E09) agregados a los listados: un codigo de
 * un listado retirado se muestra con el rotulo con que se registro.
 */
export function withCodeLabels(codes: StatCodes, raw: unknown): StatCodes {
  const labels = raw && typeof raw === 'object' && !Array.isArray(raw) ? Object.fromEntries(Object.entries(raw as Record<string, unknown>).map(([k, v]) => [k, str(v)])) : {};
  return {relationship: {...codes.relationship, labels}, institution: {...codes.institution, labels}};
}
/** Rotulo registrado de un codigo que no esta en el listado vigente ('' si no hay uno unico). */
export function registeredLabel(list: CodeList, code: string): string {
  const found = new Set(Object.entries(list.labels ?? {}).filter(([k]) => k.slice(k.indexOf(':') + 1) === code).map(([, v]) => v).filter(Boolean));
  return found.size === 1 ? [...found][0] : '';
}
/** Codigo registrado con un listado retirado sin rotulo conocido: se muestra sin el codigo. */
export const RETIRED_CODE_TEXT = 'Valor registrado con un listado anterior';
/** Rotulo visible de un codigo: el del listado vigente, el registrado (code_labels) o el aviso de listado anterior; nunca el codigo. */
export const codeLabel = (list: CodeList, code: string) => !code ? '' : list.items.find(i => i.code === code)?.label || registeredLabel(list, code) || RETIRED_CODE_TEXT;
/** Relacion con el NNA para lectura: rotulo vigente o registrado y, sin rotulo, el texto que ya existia y el aviso. */
export function relationLines(list: CodeList, code: string, text: string): string[] {
  if (!code) return text ? [text] : [];
  const label = list.items.find(i => i.code === code)?.label || registeredLabel(list, code);
  return label ? [label, ...(text ? [text] : [])] : [...(text ? [text] : []), RETIRED_CODE_TEXT];
}

/**
 * Valor guardado fuera de catalogo o formato (region, comuna, celular) que sigue igual al de la version anterior: se
 * muestra como "registrado anteriormente" y se conserva hasta que se reemplace; nunca se reescribe en silencio.
 */
export function legacyValueOf(p: DeclaredPerson, previous: DeclaredPerson[], key: PersonField): string {
  const value = str(p[key]);
  if (!value || !previous.some(q => str(q[key]) === value)) return '';
  if (key === 'region_code') return validRegion(value) ? '' : value;
  if (key === 'commune') return validRegion(str(p.region_code)) && validCommune(str(p.region_code), value) ? '' : value;
  if (key === 'mobile') return MOBILE_PATTERN.test(value) ? '' : value;
  return '';
}

// --- Validacion --------------------------------------------------------------------------------------------------
export type Problems = Record<string, string>;
/**
 * Problemas de una persona-1 con las reglas del servidor, por campo. previous = personas de la version anterior en vista
 * canonica (un valor identico de cualquiera de ellas se conserva sin revalidarse). requireDatum exige algun dato ademas
 * del rol (personas de la ficha de ingreso de la COM manual).
 */
export function personProblems(p: DeclaredPerson, previous: DeclaredPerson[] = [], codes: StatCodes = NO_CODES, requireDatum = false): Problems {
  const errors: Problems = {};
  const kept = (key: PersonField) => str(p[key]) !== '' && previous.some(q => str(q[key]) === str(p[key]));
  const check = (key: PersonField, problem: string | null) => {if (problem && !kept(key)) errors[key] = problem;};
  for (const key of NAME_PARTS) check(key, nameProblem(str(p[key])));
  check('birth_date', birthDateProblem(str(p.birth_date)));
  const type = str(p.identity_type), number = str(p.run_number).replace(/[.\s]/g, ''), dv = str(p.run_dv);
  if (number || dv) {
    if (type !== 'RUN') errors.run_number = 'El RUN corresponde a la identificación RUN chileno.';
    else if (!number || !dv) errors.run_number = 'Completa el número y el dígito verificador, o deja el RUN en blanco.';
    else if (!validRun(number, dv)) errors.run_number = 'El número y el dígito verificador no coinciden.';
  }
  if (str(p.document_kind) || str(p.document_number)) {
    if (type !== 'DOCUMENTO_EXTRANJERO') errors.document_number = 'El documento corresponde a la identificación Documento extranjero u otro.';
    else check('document_number', documentNumberProblem(str(p.document_number)));
  }
  if (str(p.mobile) && !MOBILE_PATTERN.test(str(p.mobile))) check('mobile', 'Escribe los ocho dígitos después de +56 9.');
  if (str(p.email) && !validEmail(str(p.email))) check('email', 'Revisa el formato del correo electrónico.');
  const region = str(p.region_code), commune = str(p.commune);
  if (region && !validRegion(region)) check('region_code', 'Selecciona una región del listado.');
  if (commune && !(validRegion(region) && validCommune(region, commune))) check('commune', 'Selecciona una comuna de la región indicada.');
  const nationality = str(p.nationality);
  if (nationality && !validNationality(nationality)) check('nationality', nationality.startsWith(NATIONALITY_PREFIX) ? 'Indica la nacionalidad o situación especial.' : 'Selecciona un país o la opción Otra / situación especial.');
  const age = str(p.age_years).trim(), max = p.role === 'NNA' ? 17 : 130;
  if (age && !kept('age_years') && (!/^[0-9]{1,3}$/.test(age) || Number(age) > max)) errors.age_years = `Escribe un número entero entre 0 y ${max}.`;
  for (const key of LEGACY_FIELDS) if (str(p[key]) && !kept(key)) errors[key] = 'Los datos registrados anteriormente no se crean ni se modifican; solo se conservan o se quitan.';
  const code = str(p.relationship_code);
  if (code && !kept('relationship_code') && !selectableCodes(codes.relationship).some(i => i.code === code)) errors.relationship_code = 'Selecciona una opción del listado vigente.';
  if (requireDatum && PERSON_FIELDS.every(k => k === 'role' || !str(p[k]).trim())) errors.role = 'Registra algún antecedente disponible de la persona, sin inventar identidad.';
  return errors;
}

// --- Instituciones y hechos ----------------------------------------------------------------------------------------
export const INSTITUTION_FIELDS = ['name', 'type_code', 'role', 'region_code', 'commune'] as const;
export type Institution = Record<(typeof INSTITUTION_FIELDS)[number], string>;
export const MAX_INSTITUTIONS = 20;
export const emptyInstitution = (): Institution => ({name: '', type_code: '', role: '', region_code: '', commune: ''});
/** Institucion guardada sin su type_version (lo deriva el servidor). */
export const canonicalInstitutions = (raw: unknown): Institution[] => Array.isArray(raw)
  ? raw.filter(x => x && typeof x === 'object').map(x => Object.fromEntries(INSTITUTION_FIELDS.map(k => [k, str((x as Record<string, unknown>)[k])])) as Institution) : [];
export function institutionProblems(item: Institution, previous: Institution[] = [], codes: StatCodes = NO_CODES): Problems {
  const errors: Problems = {};
  const kept = (key: keyof Institution) => str(item[key]) !== '' && previous.some(q => str(q[key]) === str(item[key]));
  if (!str(item.name).trim()) errors.name = 'Indica el nombre de la institución.';
  const region = str(item.region_code), commune = str(item.commune);
  if (region && !validRegion(region) && !kept('region_code')) errors.region_code = 'Selecciona una región del listado.';
  if (commune && !(validRegion(region) && validCommune(region, commune)) && !kept('commune')) errors.commune = 'Selecciona una comuna de la región indicada.';
  const code = str(item.type_code);
  if (code && !kept('type_code') && !selectableCodes(codes.institution).some(i => i.code === code)) errors.type_code = 'Selecciona una opción del listado vigente.';
  return errors;
}
export const FACT_FIELDS = ['facts_region_code', 'facts_commune', 'facts_place'] as const;
/** Region y comuna de los hechos (catalogo del Formulario; un valor anterior identico se conserva). */
export function factProblems(form: Record<string, unknown>, previous: Record<string, unknown> = {}): Problems {
  const errors: Problems = {};
  const kept = (key: string) => str(form[key]) !== '' && str(form[key]) === str(previous[key]);
  const region = str(form.facts_region_code), commune = str(form.facts_commune);
  if (region && !validRegion(region) && !kept('facts_region_code')) errors.facts_region_code = 'Selecciona una región del listado.';
  if (commune && !(validRegion(region) && validCommune(region, commune)) && !kept('facts_commune')) errors.facts_commune = 'Selecciona una comuna de la región indicada.';
  return errors;
}
/** Agrega los problemas de una lista con su ruta (people.0.first_name). */
export function prefixed(prefix: string, problems: Problems): Problems {
  return Object.fromEntries(Object.entries(problems).map(([k, v]) => [`${prefix}.${k}`, v]));
}

// --- Lectura ---------------------------------------------------------------------------------------------------------
/** Valor visible de un campo de persona-1 (sin codigos internos). */
export function personValue(key: PersonField, value: string, codes: StatCodes = NO_CODES): string {
  if (!value) return '';
  switch (key) {
    case 'role': return ROLE_LABELS[value] ?? value;
    case 'identity_type': return IDENTITY_TYPES.find(([v]) => v === value)?.[1] ?? value;
    case 'document_kind': return DOCUMENT_KINDS.find(([v]) => v === value)?.[1] ?? value;
    case 'treatment': return TREATMENTS.find(([v]) => v === value)?.[1] ?? value;
    case 'region_code': return regionName(value);
    case 'run_number': return formatRunNumber(value);
    case 'relationship_code': return codeLabel(codes.relationship, value);
    case 'nationality': return value.startsWith(NATIONALITY_PREFIX) ? 'Otra / situación especial: ' + value.slice(NATIONALITY_PREFIX.length) : value;
    default: return value;
  }
}
/** Filas [rotulo, valor] informadas de una persona para lectura: nombre y documento resumidos, luego el resto. */
export function personFacts(p: DeclaredPerson, codes: StatCodes = NO_CODES): [string, string][] {
  const rows: [string, string][] = [];
  const name = personName(p), doc = personDocument(p);
  if (name) rows.push(['Nombre', name]);
  if (doc) rows.push(['Identificación', doc]);
  const skip = new Set<string>(['role', ...NAME_PARTS, 'legacy_name', 'identity_type', 'run_number', 'run_dv', 'document_kind', 'document_number', 'legacy_document', 'relationship_code', 'relationship_text']);
  const relation = relationLines(codes.relationship, p.relationship_code, p.relationship_text);
  if (relation.length) rows.push(['Relación', relation.join('\n')]);
  for (const key of PERSON_FIELDS) if (!skip.has(key) && str(p[key])) rows.push([PERSON_LABELS[key], personValue(key, str(p[key]), codes)]);
  return rows;
}

/**
 * Resumen de cambios de una version (DISENO 2.5; lo calcula el servidor: COMPLEMENTO de vacio a valor, RECTIFICACION de
 * un valor a otro o a vacio), en texto llano y solo con cantidades. '' sin resumen (primera version o anterior a 0.8.0).
 */
export function changeSummaryText(raw: unknown): string {
  let value = raw;
  if (typeof value === 'string') {
    if (!value.trim()) return '';
    try {value = JSON.parse(value);} catch {return '';}
  }
  if (!value || typeof value !== 'object') return '';
  const count = (key: string) => {const v = (value as Record<string, unknown>)[key];return Array.isArray(v) ? v.length : 0;};
  const added = count('COMPLEMENTO'), fixed = count('RECTIFICACION');
  const part = (n: number, one: string, many: string) => n === 1 ? `1 ${one}` : `${n} ${many}`;
  if (!added && !fixed) return 'Sin cambios en los datos.';
  return [added ? part(added, 'dato complementado', 'datos complementados') : '', fixed ? part(fixed, 'dato rectificado', 'datos rectificados') : ''].filter(Boolean).join(' y ') + '.';
}
