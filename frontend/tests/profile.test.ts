// Perfil de la cuenta (PROFILE_SET): el cuerpo que arma la interfaz conserva lo que no cambia y el resumen
// replica la comparacion del servidor (Personnel.profile). node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {countIn, diff, duplicates, fold, grantOf, grantState, hasTi, initial, keyOf, liveGrants, payload, periodProblems, toggle, visibleScopes} from '../src/features/admin/profile.ts';

const NOW = Date.parse('2026-11-20T15:00:00.000Z');
const HOUR = 3_600_000;
// Filas como las entrega /api/administration (fechas en milisegundos, con milisegundos no nulos).
const rows = [
  {id: 1, user_id: 7, role_code: 'PROFESIONAL', scope_id: 3, valid_from: NOW - 50 * HOUR, valid_until: null, revoked_at: null},
  {id: 2, user_id: 7, role_code: 'LECTURA', scope_id: 4, valid_from: NOW - 2 * HOUR, valid_until: NOW + 48 * HOUR + 123, revoked_at: null},
  {id: 3, user_id: 7, role_code: 'LECTURA', scope_id: 5, valid_from: NOW + 5 * HOUR + 456, valid_until: null, revoked_at: null},
  {id: 4, user_id: 7, role_code: 'DISTRIBUIDOR', scope_id: null, valid_from: NOW - 9 * HOUR, valid_until: null, revoked_at: NOW - HOUR},
  {id: 5, user_id: 7, role_code: 'ADMINISTRATIVO', scope_id: 3, valid_from: NOW - 9 * HOUR, valid_until: NOW - HOUR, revoked_at: null},
  {id: 6, user_id: 8, role_code: 'TI', scope_id: null, valid_from: NOW - 9 * HOUR, valid_until: null, revoked_at: null},
];
const grants = rows.map(grantOf);
const live = liveGrants(grants, 7, NOW);

test('solo cuentan las funciones vigentes o programadas de la cuenta', () => {
  assert.deepEqual(live.map(g => g.id), [1, 2, 3]);
  assert.deepEqual(grants.map(g => grantState(g, NOW)), ['VIGENTE', 'VIGENTE', 'PROGRAMADA', 'REVOCADA', 'VENCIDA', 'VIGENTE']);
});

test('un perfil sin cambios reenvia los terminos exactos y no produce diferencias', () => {
  const body = payload(initial(live), NOW);
  assert.deepEqual(body, [
    {role: 'PROFESIONAL', scope_id: 3},
    {role: 'LECTURA', scope_id: 4, until_at: new Date(NOW + 48 * HOUR + 123).toISOString()},
    {role: 'LECTURA', scope_id: 5, from_at: new Date(NOW + 5 * HOUR + 456).toISOString()},
  ]);
  assert.equal(body[1].until_at, '2026-11-22T15:00:00.123Z');
  const d = diff(live, body);
  assert.equal(d.grant.length, 0);
  assert.equal(d.revoke.length, 0);
});

test('cambiar el termino revoca y vuelve a otorgar; quitar una funcion la revoca', () => {
  let profile = initial(live);
  const lectura = profile.get(keyOf('LECTURA', 4))!;
  profile = new Map(profile).set(keyOf('LECTURA', 4), {...lectura, until: NOW + 72 * HOUR});
  profile = toggle(profile, 'PROFESIONAL', 3, false, live);
  const d = diff(live, payload(profile, NOW));
  assert.deepEqual(d.grant, [{role: 'LECTURA', scope_id: 4, until_at: new Date(NOW + 72 * HOUR).toISOString()}]);
  assert.deepEqual(d.revoke.map(g => g.id).sort(), [1, 2]);
});

test('una funcion nueva sin fechas rige desde ahora y sin termino', () => {
  const profile = toggle(initial(live), 'DISTRIBUIDOR', null, true, live);
  const d = diff(live, payload(profile, NOW));
  assert.deepEqual(d.grant, [{role: 'DISTRIBUIDOR'}]);
  assert.equal(d.revoke.length, 0);
});

test('volver a marcar una funcion existente recupera sus fechas originales', () => {
  const off = toggle(initial(live), 'LECTURA', 4, false, live);
  const on = toggle(off, 'LECTURA', 4, true, live);
  assert.deepEqual(diff(live, payload(on, NOW)), {grant: [], revoke: []});
});

test('TI es exclusiva: al marcarla quedan solo TI y no se agregan otras', () => {
  const ti = toggle(initial(live), 'TI', null, true, live);
  assert.deepEqual([...ti.keys()], [keyOf('TI', null)]);
  assert.ok(hasTi(ti));
  assert.equal(toggle(ti, 'PROFESIONAL', 3, true, live), ti);
  const d = diff(live, payload(ti, NOW));
  assert.deepEqual(d.grant, [{role: 'TI'}]);
  assert.equal(d.revoke.length, 3);
  // Al desmarcar TI se pueden marcar otras funciones de nuevo.
  assert.ok(toggle(toggle(ti, 'TI', null, false, live), 'PROFESIONAL', 3, true, live).has(keyOf('PROFESIONAL', 3)));
});

test('dos periodos de la misma funcion y ambito se detectan', () => {
  const twice = [...live, grantOf({id: 9, user_id: 7, role_code: 'PROFESIONAL', scope_id: 3, valid_from: NOW + 100 * HOUR, valid_until: null, revoked_at: null})];
  assert.deepEqual(duplicates(twice).map(g => keyOf(g.role_code, g.scope_id)), [keyOf('PROFESIONAL', 3)]);
  assert.equal(duplicates(live).length, 0);
});

test('un termino pasado o anterior al inicio se marca antes de confirmar', () => {
  const profile = toggle(initial(live), 'DISTRIBUIDOR', null, true, live);
  const past = new Map(profile).set(keyOf('DISTRIBUIDOR', null), {role: 'DISTRIBUIDOR', scope: null, from: null, until: NOW - HOUR});
  assert.deepEqual(periodProblems(past, NOW), [keyOf('DISTRIBUIDOR', null)]);
  const reversed = new Map(profile).set(keyOf('DISTRIBUIDOR', null), {role: 'DISTRIBUIDOR', scope: null, from: NOW + 10 * HOUR, until: NOW + 5 * HOUR});
  assert.deepEqual(periodProblems(reversed, NOW), [keyOf('DISTRIBUIDOR', null)]);
  assert.deepEqual(periodProblems(initial(live), NOW), []);
});

// Vista angosta (G04b): tarjetas por ambito sobre el mismo estado del perfil.
const SCOPES = [
  {id: 3, code: 'UPR-REQ', name: 'Requerimientos / Atención de Personas'},
  {id: 4, code: 'UPD', name: 'Dirección UPD'},
  {id: 5, code: 'UEP', name: 'Dirección UEP'},
  {id: 6, code: 'UPR-NCE', name: 'NNA bajo cuidado del Estado'},
];
const codes = (list: {code: string}[]) => list.map(s => s.code);

test('el contador de cada tarjeta sale del mismo perfil que la matriz', () => {
  let profile = initial(live);
  assert.equal(countIn(profile, 3), 1);
  profile = toggle(profile, 'ADMINISTRATIVO', 3, true, live);
  assert.equal(countIn(profile, 3), 2);
  assert.equal(countIn(profile, 6), 0);
  assert.equal(countIn(toggle(profile, 'TI', null, true, live), 3), 0);
});

test('solo ambitos con funciones: incluye los que la cuenta tenia y los abiertos', () => {
  const profile = initial(live);
  assert.deepEqual(codes(visibleScopes(SCOPES, profile, live, true, '')), ['UPR-REQ', 'UPD', 'UEP']);
  assert.deepEqual(codes(visibleScopes(SCOPES, profile, live, false, '')), ['UPR-REQ', 'UPD', 'UEP', 'UPR-NCE']);
  // Desmarcar no hace desaparecer la tarjeta; una tarjeta abierta se mantiene aunque quede sin funciones.
  const none = toggle(profile, 'PROFESIONAL', 3, false, live);
  assert.ok(codes(visibleScopes(SCOPES, none, live, true, '')).includes('UPR-REQ'));
  assert.deepEqual(codes(visibleScopes(SCOPES, initial([]), [], true, '', new Set([6]))), ['UPR-NCE']);
  // Una funcion nueva en otro ambito lo hace visible con el filtro.
  assert.ok(codes(visibleScopes(SCOPES, toggle(profile, 'LECTURA', 6, true, live), live, true, '')).includes('UPR-NCE'));
});

test('el buscador compara codigo y nombre sin tildes ni mayusculas, palabra por palabra', () => {
  assert.equal(fold('  Atención  de PERSONAS '), 'atencion de personas');
  const all = (q: string) => codes(visibleScopes(SCOPES, initial(live), live, false, q));
  assert.deepEqual(all('upr'), ['UPR-REQ', 'UPR-NCE']);
  assert.deepEqual(all('atencion personas'), ['UPR-REQ']);
  assert.deepEqual(all('DIRECCIÓN'), ['UPD', 'UEP']);
  assert.deepEqual(all('estado nna'), ['UPR-NCE']);
  assert.deepEqual(all('inexistente'), []);
  assert.deepEqual(codes(visibleScopes(SCOPES, initial(live), live, true, 'nna')), []);
});
