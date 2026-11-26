import {LabeledField} from './LabeledField';
import {Icon} from './Icon';
import type {CatalogItem, Special, StatBlock, StatList, StatVar} from '../features/detail/characterizationModel.ts';
import {choiceOf, currentChoice, needsReason, offered, PENDING, reasonOf, setChoice, setReason, statPayload, valueText} from '../features/detail/characterizationModel.ts';

/** Ayuda breve de un item: su definicion operativa, si el listado vigente la trae (descripcion del catalogo). */
export const itemHelp = (items: CatalogItem[], code: string) => items.find(i => i.code === code)?.description || undefined;

/**
 * Seleccion de un codigo del listado VIGENTE con la definicion del item elegido como ayuda (por ejemplo, la regla de
 * "Oficio" en el tipo de actuacion). Un codigo ya registrado con un listado anterior se conserva con su rotulo. Nunca
 * muestra codigos.
 */
export function CatalogSelect({label, list, value, onChange, previousLabel, empty = 'Sin especificar', disabled}: {
  label: string; list: StatList; value: string; onChange: (v: string) => void; previousLabel?: string; empty?: string; disabled?: boolean;
}) {
  const options = list.items.filter(i => i.selectable);
  const retired = !!value && !options.some(o => o.code === value);
  return <LabeledField label={label} help={itemHelp(list.items, value)}>
    <select value={value} disabled={disabled} onChange={e => onChange(e.target.value)}>
      <option value="">{empty}</option>
      {retired && <option value={value}>{(previousLabel || 'Valor registrado') + ' (listado anterior)'}</option>}
      {options.map(o => <option key={o.code} value={o.code}>{o.label}</option>)}
    </select>
  </LabeledField>;
}

/**
 * Bloque "Caracterizacion del ingreso" (0.8.0, D29-D31, gate E09; DISENO 10.1): tipo de solicitud, requirente y via,
 * solo las variables con listado vigente. Opcional salvo las que el acto exige (required). La propuesta del Formulario se
 * muestra como propuesta y solo se usa si la persona la elige ("Usar propuesta"); corregir un valor informado pide motivo.
 */
export function StatFields({vars, lists, special, value, onChange, required = [], disabled, lead}: {
  vars: StatVar[]; lists: Record<string, StatList>; special: Special[]; value: StatBlock | undefined; onChange: (v: StatBlock | undefined) => void;
  required?: string[]; disabled?: boolean; lead?: string;
}) {
  const shown = offered(vars);
  if (!shown.length) return null;
  // Lo que se guarda es siempre el bloque listo para enviar: solo variables con listado vigente que cambian (statPayload).
  const emit = (b: StatBlock | undefined) => onChange(statPayload(vars, b));
  return <fieldset className="field-group stat-fields" disabled={disabled}>
    <legend>Caracterización del ingreso</legend>
    <p className="field-help">{lead ?? 'Opcional: puedes completarla ahora o más adelante en la ficha.'}</p>
    {shown.map(v => {
      const list = lists[v.variable] ?? {catalog: v.variable, label: v.label, version: null, items: []};
      const choice = choiceOf(value, v.variable), registered = currentChoice(v);
      const selected = choice || registered;
      const code = selected.startsWith('code:') ? selected.slice(5) : '';
      const mustChoose = required.includes(v.variable) && v.state === PENDING;
      return <div key={v.variable} className="stat-variable">
        <LabeledField label={v.label} required={mustChoose} help={itemHelp(list.items, code)}>
          <select value={selected} required={mustChoose} onChange={e => emit(setChoice(value, v.variable, e.target.value === registered ? '' : e.target.value))}>
            <option value="">{registered ? 'Sin cambios' : 'Sin clasificar'}</option>
            {v.state && v.state !== PENDING && <option value={registered}>{valueText(v, special)} (registrado)</option>}
            <optgroup label="Listado vigente">
              {list.items.filter(i => i.selectable && 'code:' + i.code !== registered).map(i => <option key={i.code} value={'code:' + i.code}>{i.label}</option>)}
            </optgroup>
            <optgroup label="Sin un valor del listado">
              {special.filter(s => 'status:' + s.code !== registered).map(s => <option key={s.code} value={'status:' + s.code}>{s.label}</option>)}
            </optgroup>
          </select>
        </LabeledField>
        {v.proposal && 'code:' + v.proposal.code !== selected && <p className="stat-proposal">
          <span><Icon name="info" size={14}/>Propuesta: {v.proposal.label}<small> · sugerida por los datos del Formulario; no cuenta como informada hasta que la elijas.</small></span>
          <button type="button" className="sm" onClick={() => emit(setChoice(value, v.variable, 'code:' + v.proposal!.code))}>Usar propuesta</button>
        </p>}
      </div>;
    })}
    {needsReason(vars, value) && <LabeledField label="Motivo de la corrección" required help="Corriges un valor ya informado: queda en el historial de la clasificación.">
      <textarea rows={2} maxLength={2000} value={reasonOf(value)} onChange={e => emit(setReason(value, e.target.value))}/>
    </LabeledField>}
  </fieldset>;
}
