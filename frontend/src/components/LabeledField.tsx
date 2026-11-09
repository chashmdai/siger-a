import type {ReactNode} from 'react';

/** Etiqueta que envuelve su control y una ayuda opcional. */
export function LabeledField({label, children, help, required}: {label: string; children: ReactNode; help?: string; required?: boolean}) {
  return <label>
    <span>{label}{required && <span className="required-mark" aria-hidden="true">*</span>}</span>
    {children}
    {help && <small>{help}</small>}
  </label>;
}
