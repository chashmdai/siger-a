import type {Row} from './rows';
import {ids, list, number, text} from './rows';
import type {Option} from '../components/ActionDialog';

// Consultas sobre el catalogo de /api/catalog: ambitos, unidades y directorio minimo de personas.
// "assignable_scopes" y "handoff_scopes" los calcula el servidor con las funciones vigentes.

export const scopes = (catalog: Row, allowed?: (s: Row) => boolean): Option[] =>
  list(catalog, 'scopes').filter(s => s.enabled !== false && (!allowed || allowed(s))).map(s => ({value: number(s, 'id'), label: text(s, 'code') + ' · ' + text(s, 'name')}));

export const nameOf = (catalog: Row, id: unknown) => text(list(catalog, 'people').find(p => number(p, 'id') === Number(id)) ?? {}, 'name') || String(id ?? '-');
export const scopeOf = (catalog: Row, id: unknown) => text(list(catalog, 'scopes').find(p => number(p, 'id') === Number(id)) ?? {}, 'code') || String(id ?? '-');
export const scopeRow = (catalog: Row, id: unknown) => list(catalog, 'scopes').find(s => number(s, 'id') === Number(id));

const enabledPeople = (catalog: Row) => list(catalog, 'people').filter(p => p.enabled !== false);

/** Personas asignables en un ambito, o todas las habilitadas. */
export const people = (catalog: Row, scopeId?: number): Option[] =>
  enabledPeople(catalog).filter(p => scopeId === undefined || ids(p, 'assignable_scopes').includes(scopeId)).map(p => ({value: number(p, 'id'), label: text(p, 'name')}));

/** Profesionales elegibles al asumir una SOL: coordinacion directa (regional o UPR-REQ) o cualquier area de la unidad. */
export function solicitationPeople(catalog: Row, scopeId: number): Option[] {
  const all = list(catalog, 'scopes'), target = all.find(s => number(s, 'id') === scopeId);
  if (!target) return [];
  const isDirect = Boolean(target.regional) || text(target, 'code') === 'UPR-REQ';
  const available = all.filter(s => isDirect ? number(s, 'id') === scopeId : text(s, 'unit_code') === text(target, 'unit_code') && !s.regional);
  const allowed = new Set(available.map(s => number(s, 'id')));
  return enabledPeople(catalog).filter(p => ids(p, 'assignable_scopes').some(s => allowed.has(s))).map(p => ({value: number(p, 'id'), label: text(p, 'name')}));
}

export function caseScopeOptions(catalog: Row, scopeIds: number[]): Option[] {
  const allowed = new Set(scopeIds);
  return scopes(catalog, s => allowed.has(number(s, 'id')));
}

/** Una opcion por persona y ambito permitido; el campo de persona depende del ambito elegido. */
export function casePersonOptions(catalog: Row, scopeIds: number[]): Option[] {
  const allowed = new Set(scopeIds);
  return enabledPeople(catalog).flatMap(p => ids(p, 'assignable_scopes').filter(s => allowed.has(s)).map(s => ({value: number(p, 'id'), label: text(p, 'name'), parent: s})));
}
