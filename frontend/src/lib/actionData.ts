/**
 * Datos y pasos del dialogo de comandos (components/ActionDialog.tsx): logica pura, sin React, cubierta por
 * tests/actionData.test.ts. Envio con revision previa (0.7.0, gate T03): el primer Continuar pide la revision (por
 * ejemplo, el efecto territorial que calcula el servidor sin escribir); el envio lleva sus datos extra y, si la
 * revision trae una casilla de confirmacion, solo sale con ella marcada. Un dialogo sin revision envia como antes.
 */
import type {Row} from './rows.ts';
import type {Field} from '../components/ActionDialog';
import type {Choice} from './destination.ts';
import {payloadFor} from './destination.ts';

/** Datos que se envian: fechas en UTC (las exactas sin redondear), el destino con las claves de cada comando y las listas en el orden de sus opciones. */
export function payloadOf(fields: Field[], data: Row): Row {
  const payload = {...data};
  for (const f of fields) if (f.type === 'datetime-local') payload[f.key] = f.exact != null && f.exact > 0 && data[f.key] === f.value ? new Date(f.exact).toISOString()
    : data[f.key] ? new Date(String(data[f.key])).toISOString() : null;
  for (const f of fields) if (f.destination) {delete payload[f.key]; Object.assign(payload, payloadFor(f.destination.destinations.operation, data[f.key] as Choice));}
  // Una lista marcada se envia en el orden de sus opciones, no en el orden en que se marco.
  for (const f of fields) if (f.type === 'checklist') {const marked = (data[f.key] as string[] | undefined) ?? []; payload[f.key] = (f.options ?? []).map(o => String(o.value)).filter(v => marked.includes(v));}
  return payload;
}

/** Huella de los datos para saber si una revision sigue vigente; con una fecha invalida no coincide con ninguna revision. */
export function signatureOf(fields: Field[], data: Row) {
  try {return JSON.stringify(payloadOf(fields, data));} catch {return 'invalido:' + JSON.stringify(data);}
}

/** Revision vigente: sus datos extra y, si la trae, la casilla de confirmacion expresa. */
export type Reviewed = {extra?: Row; confirm?: string};
export type Step = {step: 'review'} | {step: 'confirm'} | {step: 'send'; payload: Row};
/**
 * Que hace Confirmar/Continuar: sin revision configurada, enviar; con revision y sin una vigente para estos datos,
 * pedirla; con la casilla sin marcar, nada; si no, enviar con los datos extra de la revision.
 */
export function submitStep(payload: Row, reviewed: boolean, current: Reviewed | null, confirmed: boolean): Step {
  if (!reviewed) return {step: 'send', payload};
  if (!current) return {step: 'review'};
  if (current.confirm && !confirmed) return {step: 'confirm'};
  return {step: 'send', payload: {...payload, ...current.extra}};
}
