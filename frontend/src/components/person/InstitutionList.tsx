import {LabeledField} from '../LabeledField';
import {Icon} from '../Icon';
import {EmptyState} from '../Primitives';
import {CodeSelect} from './CodeSelect';
import {RegionCommuneFields} from './Controls';
import type {Institution, StatCodes} from '../../lib/person/personModel.ts';
import {MAX_INSTITUTIONS, emptyInstitution} from '../../lib/person/personModel.ts';
import {validCommune, validRegion} from '../../lib/person/catalog.ts';

/**
 * Instituciones de la ficha 1.2 y de la ficha de ingreso (0.8.0, D33): nombre, tipo del listado vigente, rol o vinculo,
 * region y comuna. Todo opcional salvo el nombre de cada institucion agregada.
 */
export function InstitutionList({items, previous, update, error, touch, codes, regions, lead}: {
  items: Institution[]; previous: Institution[]; update: (fn: (items: Institution[]) => Institution[]) => void; error: (key: string) => string | undefined; touch: (key: string) => void;
  codes: StatCodes; regions: readonly string[]; lead?: string;
}) {
  // Actualizacion funcional: region y comuna cambian en el mismo evento.
  const set = (i: number, key: keyof Institution, value: string) => update(list => list.map((x, n) => n === i ? {...x, [key]: value} : x));
  // Region o comuna guardadas fuera de catalogo e identicas a la version anterior: visibles como registradas antes.
  const legacy = (item: Institution, key: 'region_code' | 'commune') => {
    const value = item[key];
    if (!value || !previous.some(q => q[key] === value)) return '';
    return key === 'region_code' ? (validRegion(value) ? '' : value) : (validRegion(item.region_code) && validCommune(item.region_code, value) ? '' : value);
  };
  return <div className="institution-list">
    {lead && <p className="field-help">{lead}</p>}
    {items.length === 0 && <EmptyState compact icon="layers" title="Sin instituciones agregadas">Agrega solo las instituciones pertinentes para este registro.</EmptyState>}
    <ul className="people-list">
      {items.map((item, i) => <li className="person-card" key={i}>
        <div className="person-card-head">
          <h3><span className="avatar sm" aria-hidden="true">{i + 1}</span>Institución {i + 1}</h3>
          <button type="button" className="ghost sm" onClick={() => update(list => list.filter((_, n) => n !== i))}><Icon name="close" size={16}/>Retirar de esta versión</button>
        </div>
        <div className="form-grid">
          <LabeledField label="Nombre de la institución" error={error(`${i}.name`)}>
            <input type="text" maxLength={200} value={item.name} aria-invalid={!!error(`${i}.name`) || undefined} onChange={e => set(i, 'name', e.target.value)} onBlur={() => touch(`${i}.name`)}/>
          </LabeledField>
          <CodeSelect label="Tipo de institución" list={codes.institution} value={item.type_code} onChange={x => {set(i, 'type_code', x);touch(`${i}.type_code`);}} error={error(`${i}.type_code`)}/>
          <LabeledField label="Rol o vínculo con los hechos" help="Por ejemplo: establecimiento donde estudia el NNA.">
            <input type="text" maxLength={300} value={item.role} onChange={e => set(i, 'role', e.target.value)}/>
          </LabeledField>
        </div>
        <RegionCommuneFields legend="Región y comuna de la institución" region={item.region_code} commune={item.commune} order={regions}
          onRegion={x => {set(i, 'region_code', x);touch(`${i}.region_code`);}} onCommune={x => {set(i, 'commune', x);touch(`${i}.commune`);}}
          legacyRegion={legacy(item, 'region_code')} legacyCommune={legacy(item, 'commune')} errors={{region: error(`${i}.region_code`) ?? '', commune: error(`${i}.commune`) ?? ''}}/>
      </li>)}
    </ul>
    <button type="button" disabled={items.length >= MAX_INSTITUTIONS} onClick={() => update(list => [...list, emptyInstitution()])}><Icon name="plus" size={18}/>Agregar institución</button>
  </div>;
}
