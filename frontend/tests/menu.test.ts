// Menu por funciones (app/menu.ts). Ocultar una entrada no reemplaza la autorizacion del servidor; esta prueba
// fija que entradas ve cada combinacion de funciones. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {menuFor, menuItems} from '../src/app/menu.ts';
import type {Grant, User} from '../src/lib/access.ts';

const UPR_REQ = 3, UPD = 5;
// can_search lo calcula el servidor (Actor.readsRecords): funciones operativas o de consulta, o Direccion con materia.
const READS = ['ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION', 'DISTRIBUIDOR', 'LECTURA', 'CONSULTA_INSTITUCIONAL'];
const user = (...grants: Grant[]): User => ({id: 9, username: 'cuenta', name: 'Cuenta Sintética', home_scope: UPR_REQ, must_change: false, grants, substitutions: [],
  can_search: grants.some(g => READS.includes(g.role))});
const paths = (u: User, unread = 0) => menuItems(menuFor(u, unread)).map(i => i.path);
const READER: Grant = {role: 'CONSULTA_INSTITUCIONAL', scope: null};
const BOARDS = ['com', 'sol', 'req', 'cas', 'flujos'];
const PERSONAL = ['inicio', 'avisos'];
const SEARCH = 'buscar';

test('lector institucional puro: bandejas de expedientes, sin gestión, tareas ni administración', () => {
  const menu = paths(user(READER));
  assert.deepEqual(menu, ['inicio', SEARCH, 'avisos', 'atenciones', 'com', 'sol', 'req', 'cas', 'flujos']);
  for (const hidden of ['tareas', 'atencion', 'nuevo', 'subrogancias', 'administracion', 'calendario', 'auditoria']) assert.ok(!menu.includes(hidden), hidden);
  assert.deepEqual(menuFor(user(READER), 0).map(g => g.title), ['Mi trabajo', 'Atención ciudadana', 'Gestión']);
});

test('lector + Profesional: suma las bandejas del lector a su trabajo profesional', () => {
  const menu = paths(user(READER, {role: 'PROFESIONAL', scope: UPR_REQ}));
  assert.deepEqual(menu, ['inicio', SEARCH, 'tareas', 'avisos', 'atencion', 'atenciones', 'nuevo', 'com', 'sol', 'req', 'cas', 'flujos', 'subrogancias']);
  // Sin la consulta, un profesional no ve Solicitudes ni Colaboraciones y traslados.
  const own = paths(user({role: 'PROFESIONAL', scope: UPR_REQ}));
  assert.ok(!own.includes('sol') && !own.includes('flujos'));
  assert.ok(!menu.includes('administracion') && !menu.includes('auditoria'));
});

test('Dirección + consulta: mismo menú que la Dirección sola (ya ve todas las bandejas)', () => {
  const direction: Grant = {role: 'DIRECCION', scope: UPD};
  const menu = paths(user(READER, direction));
  assert.deepEqual(menu, paths(user(direction)));
  for (const path of [...BOARDS, SEARCH, 'tareas', 'atencion', 'nuevo', 'subrogancias']) assert.ok(menu.includes(path), path);
  assert.ok(!menu.includes('administracion') && !menu.includes('auditoria'));
});

test('TI: solo administración, además de inicio y avisos', () => {
  const menu = paths(user({role: 'TI', scope: null}));
  assert.deepEqual(menu, [...PERSONAL, 'subrogancias', 'administracion', 'calendario', 'auditoria']);
  assert.deepEqual(menuFor(user({role: 'TI', scope: null}), 0).map(g => g.title), ['Mi trabajo', 'Administración']);
});

test('cuenta sin funciones: solo inicio y avisos, con el contador de no leídos', () => {
  assert.deepEqual(paths(user()), PERSONAL);
  const avisos = menuItems(menuFor(user(), 4)).find(i => i.path === 'avisos');
  assert.equal(avisos?.badge, 4);
  assert.equal(menuItems(menuFor(user(), 0)).find(i => i.path === 'avisos')?.badge, undefined);
});

test('buscador: solo cuentas que leen expedientes (operativas, Lectura, consulta institucional, Dirección con materia)', () => {
  const cases: [string, Grant[]][] = [
    ['Administrativo', [{role: 'ADMINISTRATIVO', scope: UPR_REQ}]], ['Profesional', [{role: 'PROFESIONAL', scope: UPR_REQ}]],
    ['Jefatura', [{role: 'JEFATURA', scope: UPR_REQ}]], ['Distribución', [{role: 'DISTRIBUIDOR', scope: null}]],
    ['Lectura', [{role: 'LECTURA', scope: UPR_REQ}]], ['Consulta institucional', [READER]],
    ['Dirección con materia (UPD)', [{role: 'DIRECCION', scope: UPD}]], ['Auditoría + Profesional', [{role: 'AUDITORIA', scope: UPR_REQ}, {role: 'PROFESIONAL', scope: UPR_REQ}]],
  ];
  for (const [label, grants] of cases) {
    const menu = paths(user(...grants));
    assert.equal(menu[1], SEARCH, label);
    assert.equal(menuItems(menuFor(user(...grants), 0)).find(i => i.path === SEARCH)?.label, 'Buscar persona o folio', label);
  }
});

test('buscador: nunca para TI ni para una cuenta solo con Auditoría (ni sin funciones)', () => {
  for (const [label, grants] of [['TI', [{role: 'TI', scope: null}]], ['Auditoría', [{role: 'AUDITORIA', scope: UPR_REQ}]], ['Sin funciones', []]] as [string, Grant[]][]) {
    assert.ok(!paths(user(...grants)).includes(SEARCH), label);
  }
  assert.deepEqual(paths(user({role: 'AUDITORIA', scope: UPR_REQ})), [...PERSONAL, 'auditoria']);
  // La pantalla usa el dato del servidor, no lo calcula: sin can_search no hay entrada aunque tenga funciones.
  assert.ok(!paths({...user({role: 'PROFESIONAL', scope: UPR_REQ}), can_search: false}).includes(SEARCH));
});
