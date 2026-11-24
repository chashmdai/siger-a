import {LabeledField} from '../LabeledField';
import {RegionCommuneFields} from './Controls';
import {validCommune, validRegion} from '../../lib/person/catalog.ts';

/**
 * Region, comuna y lugar de los hechos (ficha 1.2 y ficha de ingreso; 0.8.0, D32-D33). Catalogo del Formulario; un valor
 * anterior fuera de catalogo e identico se muestra como registrado antes. Todo opcional.
 */
export function FactsFields({values, previous, change, error, touch, regions}: {
  values: Record<string, unknown>; previous: Record<string, unknown>; change: (key: string, value: string) => void;
  error: (key: string) => string | undefined; touch: (key: string) => void; regions: readonly string[];
}) {
  const v = (key: string) => String(values[key] ?? '');
  const kept = (key: string) => v(key) !== '' && v(key) === String(previous[key] ?? '');
  const region = v('facts_region_code'), commune = v('facts_commune');
  return <div className="facts-fields">
    <RegionCommuneFields legend="Región y comuna de los hechos" region={region} commune={commune} order={regions}
      onRegion={x => {change('facts_region_code', x);touch('facts_region_code');}} onCommune={x => {change('facts_commune', x);touch('facts_commune');}}
      legacyRegion={kept('facts_region_code') && !validRegion(region) ? region : ''} legacyCommune={kept('facts_commune') && !(validRegion(region) && validCommune(region, commune)) ? commune : ''}
      errors={{region: error('facts_region_code') ?? '', commune: error('facts_commune') ?? ''}}/>
    <LabeledField label="Lugar de los hechos" help="Por ejemplo: establecimiento educacional, domicilio, vía pública. Sin datos que identifiquen a terceros.">
      <input type="text" maxLength={500} value={v('facts_place')} onChange={e => change('facts_place', e.target.value)}/>
    </LabeledField>
  </div>;
}
