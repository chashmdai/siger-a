// Actuaciones tipificadas y compromisos en los dialogos (0.8.0, D36, gate E09; DISENO 3.3 y 10.1). Logica pura: el tipo de
// actuacion sale del listado VIGENTE de TIPO_ACTUACION (sin vigente no se ofrece y la clave no viaja: el servidor conserva
// lo anterior); las instituciones usan las reglas de la ficha 1.2. En una rectificacion se precargan el tipo y las
// instituciones de la constancia rectificada, de modo que confirmar sin tocarlos los conserva (un codigo igual conserva su
// version en el servidor, aunque el listado se haya retirado).
import type {Row} from '../../lib/rows.ts';
import type {Institution, StatCodes} from '../../lib/person/personModel.ts';
import {canonicalInstitutions, institutionProblems, NO_CODES} from '../../lib/person/personModel.ts';
import type {StatList} from './characterizationModel.ts';
import {statLists} from './characterizationModel.ts';

export const ACTION_CATALOG = 'TIPO_ACTUACION';
const str = (v: unknown) => v == null ? '' : String(v);

/** Listado vigente de tipos de actuacion (version null si no hay). */
export const actionList = (catalog: unknown): StatList => statLists(catalog)[ACTION_CATALOG] ?? {catalog: ACTION_CATALOG, label: 'Tipo de actuación', version: null, items: []};
/** Se ofrece el selector con un listado vigente o si ya hay un tipo registrado (para conservarlo o quitarlo). */
export const offersActionType = (list: StatList, current = '') => list.version != null || current !== '';

/** Valores iniciales de NOTE_RECTIFY: el tipo y las instituciones de la constancia rectificada (sin claves derivadas). */
export function rectifyInitial(note: Row): {action_type: string; institutions: Institution[]} {
  return {action_type: str(note.action_type), institutions: canonicalInstitutions(note.institutions)};
}
/** Primer problema de las instituciones de una actuacion, con su numero, o null. */
export function institutionsProblem(items: Institution[], previous: Institution[] = [], codes: StatCodes = NO_CODES): string | null {
  for (let i = 0; i < items.length; i++) {
    const first = Object.values(institutionProblems(items[i], previous, codes))[0];
    if (first) return `Institución ${i + 1}: ${first}`;
  }
  return null;
}
