import type {Row} from '../lib/rows';
import {regionMark} from '../lib/regionMark';
import {Icon} from './Icon';

/**
 * Marca territorial y de riesgo de una COM en la bandeja y en la cola de Inicio (0.7.0, D23; gate T03b). El riesgo
 * siempre lleva icono y texto; el color nunca va solo: el titulo y el texto para lectores de pantalla nombran la region
 * y quien la atiende.
 */
export function RegionMark({catalog, row}: {catalog: Row; row: Row}) {
  const mark = regionMark(catalog, row);
  if (!mark) return null;
  return <span className={'region-mark region-mark-' + mark.tone + (mark.breathing ? ' region-mark-breathing' : '')} title={mark.label}>
    {mark.tone === 'risk' && <Icon name="alert" size={13}/>}
    <span aria-hidden="true">{mark.text}</span>
    <span className="sr-only">{mark.label}</span>
  </span>;
}
