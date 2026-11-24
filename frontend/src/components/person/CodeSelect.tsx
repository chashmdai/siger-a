import {LabeledField} from '../LabeledField';
import {LegacyNote} from './Controls';
import type {CodeList} from '../../lib/person/personModel.ts';
import {RETIRED_CODE_TEXT, selectableCodes} from '../../lib/person/personModel.ts';

/**
 * Seleccion de un codigo del catalogo estadistico VIGENTE (relacion con el NNA, tipo de institucion; 0.8.0, D33). Sin
 * listado vigente no se ofrece: el dato queda como texto. Un codigo de un listado retirado se muestra como "Valor
 * registrado con un listado anterior" (sin el codigo) y se conserva mientras no se reemplace. Nunca muestra codigos.
 */
export function CodeSelect({label, list, value, onChange, error, help, onBlur}: {label: string; list: CodeList; value: string; onChange: (v: string) => void; error?: string; help?: string; onBlur?: () => void}) {
  const options = selectableCodes(list);
  const retired = !!value && !options.some(o => o.code === value);
  if (!options.length) return retired ? <LegacyNote value={RETIRED_CODE_TEXT} label={label} clear={() => onChange('')}>Se conserva mientras no lo quites.</LegacyNote> : null;
  return <LabeledField label={label} help={help} error={error}>
    <select value={value} onChange={e => onChange(e.target.value)} onBlur={onBlur} aria-invalid={!!error || undefined}>
      <option value="">No registrada</option>
      {retired && <option value={value}>{RETIRED_CODE_TEXT}</option>}
      {options.map(o => <option key={o.code} value={o.code}>{o.label}</option>)}
    </select>
  </LabeledField>;
}
