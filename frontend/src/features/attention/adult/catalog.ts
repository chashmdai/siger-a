// Catalogos del Formulario Publico (github.com/chashmdai/formulario-web, main bbed7ad), copiados sin cambios:
// territorio.json (regiones y comunas) y paises.json (ISO 3166-1, iso-codes 4.16.0). El backend valida con las mismas
// copias (backend/src/main/resources/catalog), y una prueba exige que sean identicas.
import territory from './territorio.json' with {type: 'json'};
import countries from './paises.json' with {type: 'json'};

/** Codigo de region -> nombre, en el orden geografico del catalogo (norte a sur). */
export const REGIONS: Record<string, string> = territory.regiones;
const COMMUNES: Record<string, readonly string[]> = territory.comunas;
export const regionCodes = Object.keys(REGIONS);
export const communesOf = (region: string): readonly string[] => COMMUNES[region] ?? [];
export const regionName = (value: string) => REGIONS[value] ?? value;
export const validRegion = (value: string) => Object.hasOwn(REGIONS, value);
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
