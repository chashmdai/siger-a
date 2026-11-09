import type {ReactNode} from 'react';
import {useId} from 'react';
import {Icon} from './Icon';
import type {IconName} from './Icon';

/** Opciones breves visibles de una vez (un clic en vez de abrir una lista). Radios nativos. */
export function ChipRadios({legend, value, options, onChange, disabled, help}: {
  legend: string; value: string; options: {value: string; label: string}[]; onChange: (v: string) => void; disabled?: boolean; help?: ReactNode;
}) {
  const name = useId();
  return <fieldset className="field-group" disabled={disabled}>
    <legend>{legend}</legend>
    <div className="chip-radios">
      {options.map(o => <label key={o.value}>
        <input type="radio" name={name} value={o.value} checked={value === o.value} onChange={() => onChange(o.value)}/>{o.label}
      </label>)}
    </div>
    {help && <small className="field-help">{help}</small>}
  </fieldset>;
}

/** Alternativas con descripcion, cuando la eleccion cambia lo que se pregunta despues. */
export function ChoiceCards({legend, value, options, onChange}: {
  legend: string; value: string; options: {value: string; title: string; text: string; icon: IconName}[]; onChange: (v: string) => void;
}) {
  const name = useId();
  return <fieldset className="field-group">
    <legend>{legend}</legend>
    <div className="choice-cards">
      {options.map(o => <label key={o.value} className="choice-card">
        <span className="choice-icon"><Icon name={o.icon}/></span>
        <span><strong>{o.title}</strong><small>{o.text}</small></span>
        <input type="radio" name={name} value={o.value} checked={value === o.value} onChange={() => onChange(o.value)}/>
      </label>)}
    </div>
  </fieldset>;
}
