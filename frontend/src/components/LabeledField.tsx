import type {ReactNode} from 'react';

/** Etiqueta que envuelve su control, una ayuda opcional y, si corresponde, el mensaje de error del campo. */
export function LabeledField({label, children, help, required, error}: {label: string; children: ReactNode; help?: ReactNode; required?: boolean; error?: string}) {
  return <label className={error ? 'has-error' : undefined}>
    <span>{label}{required && <span className="required-mark" aria-hidden="true">*</span>}</span>
    {children}
    {help && <small>{help}</small>}
    {error && <small className="field-error" role="alert">{error}</small>}
  </label>;
}
