import {useState} from 'react';
import type {Row} from '../../lib/rows';
import {list} from '../../lib/rows';
import type {Field} from '../../components/ActionDialog';
import {CatalogSelect} from '../../components/StatFields';
import {Callout} from '../../components/Primitives';
import {InstitutionList} from '../../components/person/InstitutionList';
import {orderedRegions} from '../../lib/person/catalog.ts';
import type {Institution, StatCodes} from '../../lib/person/personModel.ts';
import {institutionProblems, statCodes} from '../../lib/person/personModel.ts';
import {actionList, institutionsProblem, offersActionType} from './notesModel.ts';

/**
 * Tipo de actuacion (o compromiso de una tarea) del listado vigente, con la definicion del item como ayuda. Sin listado
 * vigente y sin tipo registrado no se ofrece: la clave no viaja y el servidor conserva lo anterior.
 */
export function actionTypeField(catalog: Row, label: string, initial = '', previousLabel = ''): Field[] {
  const items = actionList(catalog);
  if (!offersActionType(items, initial)) return [];
  return [{key: 'action_type', label, type: 'custom', initial,
    render: (value, set, disabled) => <CatalogSelect label={label} list={items} value={String(value ?? '')} onChange={set} previousLabel={previousLabel} empty="Sin tipo" disabled={disabled}/>}];
}

function Institutions({value, set, previous, codes, regions, disabled}: {value: Institution[]; set: (v: Institution[]) => void; previous: Institution[]; codes: StatCodes; regions: readonly string[]; disabled: boolean}) {
  const [touched, setTouched] = useState<Set<string>>(() => new Set());
  const problems = (i: number) => institutionProblems(value[i], previous, codes);
  return <fieldset className="field-group" disabled={disabled}>
    <legend>Instituciones de la actuación (opcional)</legend>
    <InstitutionList items={value} previous={previous} codes={codes} regions={regions} lead="Por ejemplo, la institución a la que se dirigió el oficio o la denuncia."
      update={fn => set(fn(value))} touch={k => setTouched(old => new Set(old).add(k))}
      error={k => {const [i, f] = k.split('.');return touched.has(k) ? problems(Number(i))[f as keyof Institution] : undefined;}}/>
  </fieldset>;
}
/** Instituciones de una actuacion (reglas de la ficha 1.2); en una rectificacion parte de las de la constancia. */
export function institutionsField(catalog: Row, initial: Institution[] = []): Field {
  const codes = statCodes(catalog), regions = orderedRegions(list(catalog, 'regions'));
  return {key: 'institutions', label: 'Instituciones', type: 'custom', initial,
    render: (value, set, disabled) => <Institutions value={(value as Institution[] | undefined) ?? []} set={set} previous={initial} codes={codes} regions={regions} disabled={disabled}/>,
    validate: value => institutionsProblem((value as Institution[] | undefined) ?? [], initial, codes)};
}

/** Acceso al panel de derechos desde "Registrar actuacion" (DISENO 10.2): orientacion dentro del dialogo. */
export const rightsHint: Field = {key: 'rights_hint', label: 'Derechos y contexto', type: 'custom',
  render: () => <Callout tone="info">Para clasificar derechos, contexto o grupos prioritarios de este registro, usa el panel «Derechos y contexto» de la ficha (botón «Derechos y contexto» en Actuaciones).</Callout>};
