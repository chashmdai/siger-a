// Estado visible de un documento en la ficha. "Disponible · análisis en origen" (D17, H04c): original del Formulario
// liberado por el análisis que hizo el Formulario antes de aceptar la entrega, no por un análisis antivirus de la VM.
import type {Row} from '../../lib/rows.ts';
import {bool, text} from '../../lib/rows.ts';

export function fileStatus(d: Row): {label: string; tone: '' | 'warn'} {
  if (bool(d, 'quarantined')) return {label: 'En cuarentena', tone: 'warn'};
  if (bool(d, 'reserved')) return {label: 'Reservado', tone: ''};
  if (text(d, 'release_basis') === 'ORIGEN') return {label: 'Disponible · análisis en origen', tone: ''};
  return {label: 'Disponible', tone: ''};
}
