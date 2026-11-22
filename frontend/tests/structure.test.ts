// Panel Estructura (R03b): orden y agrupacion de ambitos, acciones por estado, marca "Sin region", volumen y
// funcionarios del ambito. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {actionsFor, membersOf, missingRegion, scopeRow, structureGroups, volume} from '../src/features/admin/structureModel.ts';
import {grantOf} from '../src/features/admin/profile.ts';

// Orden del Formulario tal como lo publica catalog.regions (de norte a sur).
const REGIONS = [{code: '15', name: 'Arica y Parinacota'}, {code: '05', name: 'Valparaíso'}, {code: '13', name: 'Metropolitana de Santiago'}, {code: '08', name: 'Biobío'}, {code: '12', name: 'Magallanes'}];
const rows = [
  {id: 1, code: 'UPR', level: 'CENTRAL', state: 'ACTIVA', region_code: null},
  {id: 2, code: 'REG-MAG', level: 'REGIONAL', state: 'ACTIVA', region_code: '12', close_blockers: ['1 REQ abiertos', '2 funciones vigentes o programadas']},
  {id: 3, code: 'REG-VIEJA', level: 'REGIONAL', state: 'ACTIVA', region_code: null, close_blockers: []},
  {id: 4, code: 'REG-BIO', level: 'REGIONAL', state: 'PROYECTADA', region_code: '08', close_blockers: []},
  {id: 5, code: 'REG-ARI', level: 'REGIONAL', state: 'CERRADA', region_code: '15', close_blockers: []},
  {id: 6, code: 'UPD', level: 'CENTRAL', state: 'ACTIVA', region_code: null},
  {id: 7, code: 'REG-BIO-2', level: 'REGIONAL', state: 'PROYECTADA', region_code: null, close_blockers: []},
];

test('nivel central en el orden del servidor y sedes de norte a sur, sin region al final', () => {
  const {central, sites} = structureGroups(rows, REGIONS);
  assert.deepEqual(central.map(r => r.code), ['UPR', 'UPD']);
  assert.deepEqual(sites.map(r => r.code), ['REG-ARI', 'REG-BIO', 'REG-MAG', 'REG-BIO-2', 'REG-VIEJA']);
  assert.deepEqual(sites.filter(missingRegion).map(r => r.code), ['REG-BIO-2', 'REG-VIEJA']);
  assert.ok(!missingRegion(scopeRow(rows[0])), 'el nivel central no tiene region y no se marca');
});

test('acciones por estado: activar la proyectada con region, cerrar la activa sin bloqueos', () => {
  const by = (code: string) => actionsFor(scopeRow(rows.find(r => r.code === code)!));
  assert.deepEqual(by('UPR'), {edit: true, activate: false, activateBlocked: '', close: false, closeBlocked: []});
  assert.deepEqual(by('REG-MAG').closeBlocked, ['1 REQ abiertos', '2 funciones vigentes o programadas']);
  assert.ok(by('REG-MAG').close && !by('REG-MAG').activate);
  assert.deepEqual(by('REG-VIEJA').closeBlocked, []);
  assert.equal(by('REG-VIEJA').activateBlocked, '', 'una sede activa sin region no muestra la nota de activacion');
  assert.ok(by('REG-BIO').activate && by('REG-BIO').activateBlocked === '' && !by('REG-BIO').close);
  assert.match(by('REG-BIO-2').activateBlocked, /Asigna la región/);
  const closed = by('REG-ARI');
  assert.ok(!closed.edit && !closed.activate && !closed.close, 'una sede cerrada no se edita, activa ni cierra');
});

test('volumen abierto en texto', () => {
  assert.equal(volume({open_req: 2, open_cases: 1, pending_sol: 0}), '2 REQ · 1 Caso · 0 SOL');
  assert.equal(volume({}), '0 REQ · 0 Casos · 0 SOL');
});

test('funcionarios: funciones vigentes o programadas en el ambito o adscripcion principal', () => {
  const NOW = Date.parse('2026-11-20T15:00:00Z'), H = 3_600_000;
  const users = [{id: 1, name: 'Zoe', home_scope: 9}, {id: 2, name: 'Ana', home_scope: 2}, {id: 3, name: 'Beto', home_scope: 9}, {id: 4, name: 'Carla', home_scope: 9}];
  const grants = [
    {id: 1, user_id: 1, role_code: 'DIRECCION', scope_id: 2, valid_from: NOW - H, valid_until: null, revoked_at: null},
    {id: 2, user_id: 1, role_code: 'PROFESIONAL', scope_id: 2, valid_from: NOW + H, valid_until: null, revoked_at: null},
    {id: 3, user_id: 3, role_code: 'PROFESIONAL', scope_id: 2, valid_from: NOW - 9 * H, valid_until: null, revoked_at: NOW - H},
    {id: 4, user_id: 4, role_code: 'LECTURA', scope_id: 2, valid_from: NOW - 9 * H, valid_until: NOW - H, revoked_at: null},
  ].map(grantOf);
  const members = membersOf(2, users, grants, NOW);
  assert.deepEqual(members.map(m => [m.user.name, m.roles, m.home]), [['Ana', [], true], ['Zoe', ['DIRECCION', 'PROFESIONAL'], false]]);
});
