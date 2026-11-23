import {useState} from 'react';
import type {Choice, Destinations} from '../lib/destination';
import {centralRegion, coveringSite, filterRegions, levels, propose, receiver, REGIONAL_MATTERS, regionOf, unitOf, withLevel, withRegion, withUnit} from '../lib/destination';
import {MATTER_LABELS} from '../lib/labels';
import {ChipRadios} from './Choice';
import {LabeledField} from './LabeledField';
import {Callout} from './Primitives';
import {Icon} from './Icon';

const LEVEL_LABELS = {CENTRAL: 'Nivel central', REGIONAL: 'Sede regional'};

/**
 * Selector de destino en dos pasos (R03a): nivel central -> unidad y area opcional; sede regional -> region
 * (de norte a sur, con busqueda), sede si la region tiene varias y, en una SOL, materia sugerida. Solo muestra lo que
 * el catalogo publica para la operacion (lib/destination.ts). Los selects son nativos y obligatorios donde corresponde.
 * Una sede aparece tambien bajo las regiones de su cobertura adicional (marcada); si la region declarada la atiende el
 * nivel central y ninguna sede, parte en el nivel central con un aviso y sin proponer unidad (D20).
 */
export function DestinationPicker({destinations: d, region, value: c, onChange, disabled}: {
  destinations: Destinations; region?: string | null; value: Choice; onChange: (c: Choice) => void; disabled?: boolean;
}) {
  const [query, setQuery] = useState('');
  const sol = d.operation === 'SOL';
  const proposal = propose(d, region), proposedName = proposal ? regionOf(d, proposal.region)?.name : '';
  const central = centralRegion(d, region), covering = coveringSite(d, c);
  const options = levels(d);
  const unit = unitOf(d, c), selectedRegion = regionOf(d, c.region);
  // La region elegida se mantiene en la lista aunque la busqueda ya no la incluya.
  const shown = filterRegions(d.regions, query);
  const regionList = selectedRegion && !shown.includes(selectedRegion) ? [selectedRegion, ...shown] : shown;
  const who = receiver(d, c);

  if (!options.length) return <Callout tone="warn">No hay destinos habilitados para esta operación. Revisa con TI la estructura de ámbitos.</Callout>;
  return <fieldset className="field-group destination-picker" disabled={disabled}>
    <legend>Destino</legend>
    {proposal && <Callout tone="neutral">Propuesta según la región declarada: <strong>{proposedName}</strong>. Puedes elegir otro destino.</Callout>}
    {central && <Callout tone="neutral">La región declarada, <strong>{central.name}</strong>, la atiende el nivel central (Oficina Nacional), sin sede regional. Elige la unidad que corresponda; puedes elegir otro destino.</Callout>}
    {options.length > 1
      ? <ChipRadios legend="Nivel" value={c.level} options={options.map(l => ({value: l, label: LEVEL_LABELS[l]}))} onChange={v => onChange(withLevel(v as Choice['level']))}/>
      : <p className="field-help">{LEVEL_LABELS[options[0]]}: es el único nivel que recibe esta operación.</p>}
    {c.level === 'CENTRAL' ? <div className="form-grid">
      <LabeledField label="Unidad" required>
        <select required value={c.scope ?? ''} onChange={e => onChange(withUnit(c, e.target.value ? Number(e.target.value) : null))}>
          <option value="">Seleccionar</option>
          {d.central.map(u => <option key={u.id} value={u.id}>{u.label}</option>)}
        </select>
      </LabeledField>
      {unit && unit.areas.length > 0 && <LabeledField label={sol ? 'Área sugerida (opcional)' : 'Área que recibe (opcional)'}
        help={sol ? 'Solo un área con coordinación propia recibe directamente; las demás las recibe la Dirección de la unidad.' : 'Sin área, recibe la Dirección de la unidad.'}>
        <select value={c.area ?? ''} onChange={e => onChange({...c, area: e.target.value ? Number(e.target.value) : null})}>
          <option value="">{sol ? 'Sin área específica' : 'Dirección de la unidad'}</option>
          {unit.areas.map(a => <option key={a.id} value={a.id}>{a.name}{a.direct ? ' · llega a su coordinación' : sol ? ' · recibe la Dirección' : ''}</option>)}
        </select>
      </LabeledField>}
    </div> : <div className="form-grid">
      {d.regions.length > 5 && <LabeledField label="Buscar región">
        <span className="input-icon"><Icon name="search" size={18}/><input type="search" value={query} placeholder="Nombre de la región o de la sede"
          onChange={e => setQuery(e.target.value)} onKeyDown={e => {if (e.key === 'Enter') e.preventDefault();}}/></span>
      </LabeledField>}
      <LabeledField label="Región" required help={regionList.length === 0 ? 'Ninguna región coincide con la búsqueda.' : 'De norte a sur. Solo aparecen las regiones que atiende una sede activa, por su región o por cobertura.'}>
        <select required value={c.region} onChange={e => onChange(withRegion(d, c, e.target.value))}>
          <option value="">Seleccionar</option>
          {regionList.map(r => <option key={r.code} value={r.code}>{r.name}{r.sites.length > 1 ? ' (' + r.sites.length + ' sedes)' : r.sites[0].covering ? ' (atendida por ' + r.sites[0].name + ')' : ''}</option>)}
        </select>
      </LabeledField>
      {selectedRegion && selectedRegion.sites.length > 1 && <LabeledField label="Sede" required>
        <select required value={c.scope ?? ''} onChange={e => onChange({...c, scope: e.target.value ? Number(e.target.value) : null})}>
          <option value="">Seleccionar</option>
          {selectedRegion.sites.map(s => <option key={s.id} value={s.id}>{s.name}{s.covering ? ' · cubre esta región' : ''}</option>)}
        </select>
      </LabeledField>}
      {sol && <ChipRadios legend="Materia sugerida (opcional)" value={c.matter} onChange={v => onChange({...c, matter: v})}
        options={[{value: '', label: 'Sin materia'}, ...REGIONAL_MATTERS.map(m => ({value: m, label: m}))]}
        help={c.matter ? MATTER_LABELS[c.matter] + ': su Dirección nacional podrá leer la SOL y el REQ, sin gestionarlos. El REQ la hereda al asumirse.' : 'La Dirección nacional de la materia podrá leer la SOL y el REQ, sin gestionarlos.'}/>}
    </div>}
    {covering && selectedRegion && <p className="field-help">{covering.name} atiende {selectedRegion.name} por cobertura adicional.</p>}
    {who && <p className="receiver"><Icon name="user" size={16}/>Recibe: <strong>{who}</strong></p>}
  </fieldset>;
}
