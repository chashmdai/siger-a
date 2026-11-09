import type {Row} from './rows';
import {list, number, text} from './rows';
import type {Field} from '../components/ActionDialog';
import type {Context} from '../app/context';
import {people, scopes} from './catalog';

// Campos reutilizados por los dialogos de comandos. El servidor valida nuevamente cada valor.

export const CHANNELS = ['PRESENCIAL', 'TELEFONO', 'CORREO', 'OFICIO', 'OTRO'];
export const IMMEDIATE_RESULTS = ['ORIENTACION', 'RESPUESTA_DIRECTA', 'REMISION_CONCLUIDA'];
export const DOCUMENT_TYPES = ['OFICIO', 'INFORME', 'CORREO', 'CONSTANCIA', 'OTRO'];
export const REMINDERS = [{value: '0', label: 'Avisar una sola vez'}, {value: '24', label: 'Cada 24 horas'}, {value: '48', label: 'Cada 48 horas'}, {value: '168', label: 'Cada 7 días'}];

export const reason: Field = {key: 'reason', label: 'Fundamento o resultado de la decisión', type: 'textarea', required: true, max: 12000};
export const matter: Field = {key: 'matter', label: 'Materia del Caso (no es complejidad)', type: 'select', options: ['UPR', 'UPD', 'UEP', 'OTRA'].map(v => ({value: v, label: v === 'OTRA' ? 'Otra' : v})), required: true};
export const objective: Field = {key: 'objective', label: 'Objetivo de esta línea de gestión', type: 'textarea', required: true, max: 3000};

export const person = (catalog: Row, key = 'responsible_id', label = 'Profesional responsable', scopeId?: number): Field => ({key, label, type: 'select', options: people(catalog, scopeId), required: true});
export const scopeField = (catalog: Row, key = 'scope_id', label = 'Ámbito de destino'): Field => ({key, label, type: 'select', options: scopes(catalog), required: true});

/** Select de una sola opcion ya decidida en la pantalla (la persona, el Caso o la decision elegidos). */
export const fixedSelect = (key: string, label: string, value: string | number, optionLabel: string = String(value)): Field =>
  ({key, label, type: 'select', required: true, value, options: [{value, label: optionLabel}]});
/** Decision con varias alternativas, sin valor preseleccionado. */
export const decision = (options: [string, string][], key = 'decision'): Field =>
  ({key, label: 'Decisión', type: 'select', required: true, options: options.map(([value, label]) => ({value, label}))});

/** Destino de SOL: unidad o sede obligatoria y area sugerida opcional. */
export function routingFields(catalog: Row): Field[] {
  const all = list(catalog, 'scopes').filter(s => s.enabled !== false);
  const units = all.filter(s => s.regional === true || (!text(s, 'area_code') && ['UPR', 'UPD', 'UEP', 'UGI'].includes(text(s, 'unit_code'))));
  const areas = all.filter(s => !s.regional && !!text(s, 'area_code'));
  const roots = new Map<string, number>(units.filter(s => !s.regional).map((s): [string, number] => [text(s, 'unit_code'), number(s, 'id')]));
  return [
    {key: 'scope_id', label: 'Unidad o sede de destino', type: 'select', required: true, options: units.map(s => ({value: number(s, 'id'), label: s.regional ? text(s, 'name') : text(s, 'unit_code') + ' · Dirección de unidad'}))},
    {key: 'area_scope_id', label: 'Área sugerida (opcional)', type: 'select', dependsOn: 'scope_id',
      options: areas.filter(s => roots.has(text(s, 'unit_code'))).map(s => ({value: number(s, 'id'), label: text(s, 'name') + (text(s, 'code') === 'UPR-REQ' ? ' · llega a la Coordinadora' : ' · recibe la Dirección'), parent: roots.get(text(s, 'unit_code'))})),
      help: 'Solo el área de Requerimientos de UPR llega directamente a su Coordinadora; las demás áreas las recibe la Dirección.'},
  ];
}

/** Abre el dialogo de un comando sobre una fila con id y revision. */
export function choose(ctx: Context, title: string, type: string, row: Row, fields: Field[], note?: string) {
  ctx.act({title, type, id: number(row, 'id'), revision: number(row, 'revision'), fields, note});
}
