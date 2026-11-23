/**
 * Marca territorial y de riesgo de la bandeja de comunicaciones (0.7.0, D23; gate T03b): logica pura, sin React,
 * cubierta por tests/regionMark.test.ts. Solo presentacion: no cambia filas, filtros, orden ni distribucion.
 * El servidor entrega en cada fila de COM origin_region (region de origen vigente con la regla del selector de la COM),
 * risk (campo riesgo del Formulario afirmativo) y to_distribute (condicion del filtro "Por distribuir").
 * Quien atiende la region sale de la misma regla del selector (lib/destination.ts): sedes ACTIVA por region fisica o
 * cobertura vigente, y la cobertura del nivel central.
 */
import type {Row} from './rows.ts';
import {list, text} from './rows.ts';
import {destinations, regionOf} from './destination.ts';

/** Numero de la region segun su codigo del Formulario: romano y "RM" para la Metropolitana (13). */
export const REGION_NUMBERS: Record<string, string> = {
  '01': 'I', '02': 'II', '03': 'III', '04': 'IV', '05': 'V', '06': 'VI', '07': 'VII', '08': 'VIII', '09': 'IX', '10': 'X',
  '11': 'XI', '12': 'XII', '13': 'RM', '14': 'XIV', '15': 'XV', '16': 'XVI',
};
export const RISK_TEXT = 'Riesgo actual informado';

export const regionNumber = (code: unknown): string | null =>
  typeof code === 'string' && Object.hasOwn(REGION_NUMBERS, code) ? REGION_NUMBERS[code] : null;

/** regional: la atiende hoy una sede ACTIVA; neutral: solo el nivel central o nadie; risk: riesgo actual informado. */
export type Tone = 'regional' | 'neutral' | 'risk';
/** text = lo visible (numero o "Riesgo"); label = titulo emergente y texto para lectores de pantalla. */
export type Mark = {text: string; tone: Tone; breathing: boolean; label: string};

const joined = (names: string[]) => names.length < 2 ? names.join('') : names.slice(0, -1).join(', ') + ' y ' + names[names.length - 1];

/** Sedes ACTIVA que atienden hoy la region (primero las de region fisica) y si el nivel central la atiende. */
export function attendance(catalog: Row, code: string): {sites: string[]; central: boolean} {
  const d = destinations(catalog, 'SOL');
  return {sites: regionOf(d, code)?.sites.map(s => s.name) ?? [], central: d.centralRegions.some(r => r.code === code)};
}

/** "Maule - atiende Sede O'Higgins"; sin sede: el nivel central o nadie. */
export function attendanceText(catalog: Row, code: string): string {
  const name = list(catalog, 'regions').find(r => text(r, 'code') === code);
  const {sites, central} = attendance(catalog, code);
  const names = [...sites, ...(central ? ['el nivel central'] : [])];
  const who = names.length ? (names.length === 1 ? 'atiende ' : 'atienden ') + joined(names) : 'sin sede ni nivel central que la atienda';
  return (name ? text(name, 'name') : code) + ' - ' + who;
}

/**
 * Marca de una fila de COM, o null sin region y sin riesgo (no se muestra marca). Con riesgo la marca es roja en vez
 * del color regional (sin region dice "Riesgo") y respira solo mientras la COM esta por distribuir.
 */
export function regionMark(catalog: Row, row: Row): Mark | null {
  const number = regionNumber(row.origin_region), risk = row.risk === true;
  if (!number && !risk) return null;
  const code = String(row.origin_region);
  const place = number ? attendanceText(catalog, code) : 'Sin región declarada';
  if (risk) return {text: number ?? 'Riesgo', tone: 'risk', breathing: row.to_distribute === true, label: RISK_TEXT + '. ' + place};
  return {text: number!, tone: attendance(catalog, code).sites.length ? 'regional' : 'neutral', breathing: false, label: place};
}
