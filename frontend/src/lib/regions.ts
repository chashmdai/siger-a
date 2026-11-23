/**
 * Region de origen de la persona (0.7.0, D21; gate T03): logica pura, sin React, cubierta por tests/regions.test.ts.
 * El servidor (OriginRegion) entrega en la ficha de primera atencion origin_region {registered, current, corrected}
 * solo si la ficha es legible; aqui solo se decide que mostrar.
 */
import type {Row} from './rows.ts';

type Named = {code: string; name: string};
export const NO_REGION_DECLARED = 'Sin región declarada';

/** Nombre de la region del catalogo con su codigo; nulo = sin region declarada. */
export const regionText = (regions: Named[], code: unknown) =>
  code == null || code === '' ? NO_REGION_DECLARED : (regions.find(r => r.code === String(code))?.name ?? String(code)) + ' (' + String(code) + ')';

/**
 * Region registrada y vigente cuando difieren (correccion, segun el servidor): la ficha muestra ambas. null si no
 * hubo correccion o no viene la region (ficha no legible): la region vigente ya se ve en la ficha.
 */
export function correctedOrigin(origin: Row | null | undefined, regions: Named[]): {registered: string; current: string} | null {
  if (!origin || origin.corrected !== true) return null;
  return {registered: regionText(regions, origin.registered), current: regionText(regions, origin.current)};
}
