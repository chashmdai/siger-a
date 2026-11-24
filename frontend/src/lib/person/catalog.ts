// Catalogos del Formulario Publico (github.com/chashmdai/formulario-web, main bbed7ad), copiados sin cambios:
// territorio.json (regiones y comunas) y paises.json (ISO 3166-1, iso-codes 4.16.0). El backend valida con las mismas
// copias (backend/src/main/resources/catalog), y una prueba exige que sean identicas.
import territory from '../../features/attention/adult/territorio.json' with {type: 'json'};
import countries from '../../features/attention/adult/paises.json' with {type: 'json'};

/** Codigo de region -> nombre, en el orden geografico del catalogo (norte a sur). */
export const REGIONS: Record<string, string> = territory.regiones;
const COMMUNES: Record<string, readonly string[]> = territory.comunas;
/** Codigos de region (para validar y contar). No sirve como orden: JavaScript enumera antes las claves enteras ("10".."16"). */
export const regionCodes = Object.keys(REGIONS);
export const communesOf = (region: string): readonly string[] => COMMUNES[region] ?? [];
export const regionName = (value: string) => REGIONS[value] ?? value;
export const validRegion = (value: string) => Object.hasOwn(REGIONS, value);
/**
 * Orden de las regiones en los desplegables: el que publica el servidor en catalog.regions (orden del Formulario, de
 * norte a sur, leido del mismo territorio.json). Descarta codigos ajenos al catalogo local y agrega al final los que
 * falten; sin catalogo publicado, cae al orden de regionCodes.
 */
export function orderedRegions(published: readonly {code?: unknown}[] | undefined): string[] {
  const codes = [...new Set((published ?? []).map(r => String(r.code ?? '')).filter(validRegion))];
  return [...codes, ...regionCodes.filter(code => !codes.includes(code))];
}
export const validCommune = (region: string, commune: string) => communesOf(region).includes(commune);

const fold = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('es').replace(/\s+/g, ' ').trim();
/** Region de un texto antiguo (codigo o nombre, sin distinguir tildes ni mayusculas); null si no corresponde. */
export function matchRegion(value: string): string | null {
  if (validRegion(value)) return value;
  const target = fold(value);
  return target ? regionCodes.find(code => fold(REGIONS[code]) === target) ?? null : null;
}
export function matchCommune(region: string, value: string): string | null {
  const target = fold(value);
  return target ? communesOf(region).find(name => fold(name) === target) ?? null : null;
}

// Nacionalidad: un pais del catalogo o la opcion explicita "Otra / situacion especial: <detalle>".
export const countryNames = Object.values(countries.paises as Record<string, string>).sort((a, b) => a.localeCompare(b, 'es'));
export const OTHER_NATIONALITY = '__OTRA__';
export const NATIONALITY_PREFIX: string = countries.especial;
const names = new Set<string>(countryNames);
export const isCountry = (value: string) => names.has(value);
export function nationalitySelection(value: string) {return names.has(value) ? value : value ? OTHER_NATIONALITY : '';}
export function nationalityDetail(value: string) {return value.startsWith(NATIONALITY_PREFIX) ? value.slice(NATIONALITY_PREFIX.length) : names.has(value) ? '' : value;}
export function validNationality(value: string) {
  if (names.has(value)) return true;
  if (!value.startsWith(NATIONALITY_PREFIX)) return false;
  const detail = value.slice(NATIONALITY_PREFIX.length);
  return detail.length <= 120 && !!detail.trim() && /\p{L}/u.test(detail) && !/[\u0000-\u001f\u007f-\u009f]/u.test(detail);
}
export function matchCountry(value: string): string | null {
  const target = fold(value);
  return target ? countryNames.find(name => fold(name) === target) ?? null : null;
}
export function searchCountries(query: string) {
  const terms = fold(query).split(/\s+/);
  return countryNames.filter(name => terms.every(term => fold(name).includes(term)));
}
