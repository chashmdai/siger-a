import type {Row} from './rows';
import {list, number, text} from './rows';
import type {Field} from '../components/ActionDialog';
import type {Context} from '../app/context';
import {people, scopes} from './catalog';
import {labeled, MATTER_LABELS} from './labels';
import type {Operation} from './destination';
import {destinations, REGIONAL_MATTERS} from './destination';

// Campos reutilizados por los dialogos de comandos. El servidor valida nuevamente cada valor.

/** Canales del registro manual y de la atencion directa (igual a Lifecycle.CHANNELS; 0.8.0, D31). */
export const CHANNELS = ['PRESENCIAL', 'TELEFONO', 'CORREO', 'OFICIO', 'REDES_SOCIALES', 'INTEGRANTE_DEFENSORIA', 'OTRO'];
export const IMMEDIATE_RESULTS = ['ORIENTACION', 'RESPUESTA_DIRECTA', 'REMISION_CONCLUIDA'];
export const DOCUMENT_TYPES = ['OFICIO', 'INFORME', 'CORREO', 'CONSTANCIA', 'OTRO'];
export const REMINDERS = [{value: '0', label: 'Avisar una sola vez'}, {value: '24', label: 'Cada 24 horas'}, {value: '48', label: 'Cada 48 horas'}, {value: '168', label: 'Cada 7 días'}];

export const reason: Field = {key: 'reason', label: 'Fundamento o resultado de la decisión', type: 'textarea', required: true, max: 12000};
export const matter: Field = {key: 'matter', label: 'Materia del Caso (no es complejidad)', type: 'select', options: labeled(['UPR', 'UPD', 'UEP', 'OTRA'], MATTER_LABELS), required: true};
export const reqMatter: Field = {key: 'matter', label: 'Materia del Requerimiento', type: 'select', options: labeled(REGIONAL_MATTERS, MATTER_LABELS), required: true};
export const objective: Field = {key: 'objective', label: 'Objetivo de esta línea de gestión', type: 'textarea', required: true, max: 3000};

export const person = (catalog: Row, key = 'responsible_id', label = 'Profesional responsable', scopeId?: number): Field => ({key, label, type: 'select', options: people(catalog, scopeId), required: true});
export const scopeField = (catalog: Row, key = 'scope_id', label = 'Ámbito de destino'): Field => ({key, label, type: 'select', options: scopes(catalog), required: true});
/** Adscripción de una cuenta: ámbitos activos y sedes proyectadas (dotación preparada); nunca una sede cerrada. */
export const staffScopeField = (catalog: Row, label = 'Adscripción principal', value?: number): Field => ({key: 'scope_id', label, type: 'select', required: true, value,
  options: list(catalog, 'scopes').filter(s => s.enabled !== false || text(s, 'state') === 'PROYECTADA')
    .map(s => ({value: number(s, 'id'), label: text(s, 'code') + ' · ' + text(s, 'name') + (text(s, 'state') === 'PROYECTADA' ? ' (sede proyectada)' : '')}))});

/** Select de una sola opcion ya decidida en la pantalla (la persona, el Caso o la decision elegidos). */
export const fixedSelect = (key: string, label: string, value: string | number, optionLabel: string = String(value)): Field =>
  ({key, label, type: 'select', required: true, value, options: [{value, label: optionLabel}]});
/** Decision con varias alternativas, sin valor preseleccionado. */
export const decision = (options: [string, string][], key = 'decision'): Field =>
  ({key, label: 'Decisión', type: 'select', required: true, options: options.map(([value, label]) => ({value, label}))});

/** Destino de COM_ROUTE/SOL_ROUTE (SOL), FLOW_REQUEST y entregas en dos pasos (lib/destination.ts); region = propuesta del servidor. */
export const destinationField = (catalog: Row, operation: Operation, region?: string | null): Field =>
  ({key: 'destination', label: 'Destino', type: 'destination', required: true, destination: {destinations: destinations(catalog, operation), region}});

/** Abre el dialogo de un comando sobre una fila con id y revision. */
export function choose(ctx: Context, title: string, type: string, row: Row, fields: Field[], note?: string) {
  ctx.act({title, type, id: number(row, 'id'), revision: number(row, 'revision'), fields, note});
}
