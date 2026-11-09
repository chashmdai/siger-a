// Controles de la persona adulta, adaptados de los del Formulario Publico (MobileInput, EmailInput,
// NationalitySelect, regiones/comunas e identificacion) al diseno y la ficha de SIGER.
import {useId, useRef, useState} from 'react';
import type {ClipboardEvent, ReactNode} from 'react';
import {LabeledField} from '../../../components/LabeledField';
import {Icon} from '../../../components/Icon';
import {communesOf, countryNames, NATIONALITY_PREFIX, nationalityDetail, nationalitySelection, OTHER_NATIONALITY, REGIONS, regionCodes, searchCountries} from './catalog.ts';
import {canonicalMobile, cleanEmail, formatRunNumber, pasteMobile, pastedRun, runNumber, today} from './identity.ts';

type Value = {value: string; onChange: (value: string) => void; error?: string; onBlur?: () => void};

/** Valor anterior conservado (fuera de catalogo o formato): visible y retirable, nunca reescrito en silencio. */
export function LegacyNote({value, label = 'Registrado anteriormente', clear, children}: {value: string; label?: string; clear?: () => void; children?: ReactNode}) {
  if (!value) return null;
  return <p className="legacy-note">
    <Icon name="history" size={16}/><span>{label}: <strong className="preserve">{value}</strong>. {children ?? 'Se conserva mientras no lo reemplaces.'}</span>
    {clear && <button type="button" className="ghost sm" onClick={clear}>Quitar</button>}
  </p>;
}

/** Prefijo +56 9 fijo y ocho digitos; guarda +569XXXXXXXX. Admite pegar el numero completo. */
export function MobileField({label, value, onChange, error, onBlur, legacy, help}: Value & {label: string; legacy: string; help?: string}) {
  const ref = useRef<HTMLInputElement>(null), prefix = useId(), [feedback, setFeedback] = useState('');
  const shown = /^\+569[0-9]{0,8}$/.test(value) ? value.slice(4) : '';
  const update = (local: string) => onChange(local ? '+569' + local : '');
  function paste(event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault();
    const input = event.currentTarget, start = input.selectionStart ?? shown.length, end = input.selectionEnd ?? start;
    const result = pasteMobile(event.clipboardData.getData('text'), shown, start, end);
    if ('error' in result) {setFeedback(result.error); return;}
    setFeedback('');onChange(result.local ? '+569' + result.local : '');
    requestAnimationFrame(() => ref.current?.setSelectionRange(result.cursor, result.cursor));
  }
  return <div className="field-stack"><LabeledField label={label} help={help ?? 'Escribe los ocho dígitos. También puedes pegar el número con +56 9.'} error={feedback || error}>
    <span className="mobile-input">
      <span className="mobile-prefix" id={prefix}>+56 9</span>
      <input ref={ref} type="tel" inputMode="numeric" autoComplete="off" maxLength={8} placeholder="12345678" value={shown} aria-describedby={prefix} aria-invalid={!!(feedback || error) || undefined}
        onChange={e => {setFeedback('');const raw = e.target.value, parsed = canonicalMobile(raw);
          if (parsed && raw.replace(/\D/g, '').length > 8) onChange(parsed); else update(raw.replace(/\D/g, '').slice(0, 8));}}
        onPaste={paste} onBlur={() => {setFeedback(shown && shown.length !== 8 ? 'Escribe los ocho dígitos después de +56 9.' : '');onBlur?.();}}/>
    </span>
  </LabeledField>
  <LegacyNote value={legacy} label="Teléfono registrado anteriormente" clear={() => onChange('')}>Se conserva mientras no escribas un celular nuevo.</LegacyNote>
  </div>;
}

export function EmailField({label, value, onChange, error, onBlur}: Value & {label: string}) {
  return <LabeledField label={label} help="Dato declarado; el formato no acredita que el correo exista." error={error}>
    <input type="email" inputMode="email" autoComplete="off" maxLength={200} placeholder="nombre@ejemplo.cl" value={value} aria-invalid={!!error || undefined}
      onChange={e => onChange(e.target.value)} onBlur={e => {const next = cleanEmail(e.target.value);if (next !== e.target.value) onChange(next);onBlur?.();}}/>
  </LabeledField>;
}

/** Busqueda local y seleccion nativa: el texto de busqueda nunca se guarda como nacionalidad. */
export function NationalityField({value, onChange, error, onBlur}: Value) {
  const [query, setQuery] = useState(''), id = useId(), selected = nationalitySelection(value), filtered = searchCountries(query);
  const options = selected && selected !== OTHER_NATIONALITY && !filtered.includes(selected) ? countryNames.filter(n => n === selected || filtered.includes(n)) : filtered;
  const legacy = selected === OTHER_NATIONALITY && !value.startsWith(NATIONALITY_PREFIX);
  return <fieldset className="nationality-field">
    <legend>Nacionalidad declarada</legend>
    <LabeledField label="Buscar país"><input type="search" autoComplete="off" placeholder="Escribe para filtrar países" maxLength={100} value={query} aria-controls={id} onChange={e => setQuery(e.target.value)}/></LabeledField>
    <p className="field-help" role="status">{filtered.length ? `${filtered.length} países coinciden con la búsqueda.` : 'Sin coincidencias. Puedes elegir Otra / situación especial.'}</p>
    <LabeledField label="País" error={selected === OTHER_NATIONALITY ? undefined : error}>
      <select id={id} value={selected} onChange={e => {setQuery('');onChange(e.target.value === OTHER_NATIONALITY ? NATIONALITY_PREFIX : e.target.value);}} onBlur={onBlur}>
        <option value="">No registrada</option>{options.map(n => <option key={n} value={n}>{n}</option>)}
        <option value={OTHER_NATIONALITY}>Otra / situación especial</option>
      </select>
    </LabeledField>
    {selected === OTHER_NATIONALITY && <LabeledField label="Nacionalidad o situación especial" error={error}
      help={legacy ? 'Registrada anteriormente como texto libre; se conserva mientras no la cambies.' : 'Por ejemplo, si la nacionalidad no aparece o la persona indica no tener nacionalidad.'}>
      <input type="text" maxLength={120} value={nationalityDetail(value)} aria-invalid={!!error || undefined} onChange={e => onChange(NATIONALITY_PREFIX + e.target.value)} onBlur={onBlur}/>
    </LabeledField>}
  </fieldset>;
}

/** Region y comuna dependientes (catalogo del Formulario). Cambiar la region limpia la comuna. */
export function RegionCommuneFields({region, commune, onRegion, onCommune, legacyRegion, legacyCommune, errors}: {region: string; commune: string; onRegion: (v: string) => void; onCommune: (v: string) => void; legacyRegion: string; legacyCommune: string; errors: Record<string, string>}) {
  const communes = communesOf(region);
  return <>
    <LabeledField label="Región de atención" error={errors.region}>
      <select value={region} onChange={e => {onRegion(e.target.value);onCommune('');}}>
        <option value="">No registrada</option>
        {legacyRegion && <option value={legacyRegion}>Registrada anteriormente: {legacyRegion}</option>}
        {regionCodes.map(code => <option key={code} value={code}>{REGIONS[code]}</option>)}
      </select>
    </LabeledField>
    <LabeledField label="Comuna de atención" error={errors.commune} help={!region ? 'Selecciona primero una región.' : undefined}>
      <select value={commune} disabled={!region && !legacyCommune} onChange={e => onCommune(e.target.value)}>
        <option value="">{region ? 'No registrada' : 'Selecciona primero una región'}</option>
        {legacyCommune && <option value={legacyCommune}>Registrada anteriormente: {legacyCommune}</option>}
        {communes.map(name => <option key={name} value={name}>{name}</option>)}
      </select>
    </LabeledField>
  </>;
}

/** RUN con numero y digito verificador separados; pegar un RUN completo en el numero lo separa. */
export function RunFields({number, dv, onNumber, onDv, error, onBlur}: {number: string; dv: string; onNumber: (v: string) => void; onDv: (v: string) => void; error?: string; onBlur?: () => void}) {
  return <div className="run-fields">
    <LabeledField label="RUN (número)" error={error}>
      <input type="text" inputMode="numeric" autoComplete="off" maxLength={10} placeholder="12.345.678" value={formatRunNumber(number)} aria-invalid={!!error || undefined}
        onChange={e => onNumber(runNumber(e.target.value))} onBlur={onBlur}
        onPaste={e => {const full = pastedRun(e.clipboardData.getData('text'));if (full) {e.preventDefault();onNumber(full.numero);onDv(full.dv);}}}/>
    </LabeledField>
    <span className="run-dash" aria-hidden="true">-</span>
    <LabeledField label="Dígito verificador">
      <input type="text" autoComplete="off" maxLength={1} placeholder="K" value={dv} aria-invalid={!!error || undefined}
        onChange={e => onDv(e.target.value.replace(/[^0-9Kk]/g, '').toUpperCase().slice(0, 1))} onBlur={onBlur}/>
    </LabeledField>
  </div>;
}

export function BirthDateField({value, onChange, error, onBlur}: Value) {
  return <LabeledField label="Fecha de nacimiento" help="Opcional." error={error}>
    <input type="date" max={today()} value={value} aria-invalid={!!error || undefined} onChange={e => onChange(e.target.value)} onBlur={onBlur}/>
  </LabeledField>;
}
