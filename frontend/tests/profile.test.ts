// Perfil de la cuenta (PROFILE_SET): el cuerpo que arma la interfaz conserva lo que no cambia y el resumen
// replica la comparacion del servidor (Personnel.profile). node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {columnsFor, countIn, diff, optionalRoles, preserved, duplicates, fold, grantOf, grantState, groupScopes, hasTi, initial, keyOf, liveGrants, offered, payload, periodProblems, profileScopes, startFiltered, toggle, visibleScopes} from '../src/features/admin/profile.ts';

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

test('una funcion sin fecha de inicio registrada (semillas, inicio 0) se conserva sin enviar inicio', () => {
  const seed = [grantOf({id: 20, user_id: 9, role_code: 'PROFESIONAL', scope_id: 3, valid_from: 0, valid_until: null, revoked_at: null})];
  assert.equal(grantState(seed[0], NOW), 'VIGENTE');
  assert.deepEqual(payload(initial(seed), NOW), [{role: 'PROFESIONAL', scope_id: 3}]);
  assert.deepEqual(diff(seed, payload(initial(seed), NOW)), {grant: [], revoke: []});
});

// Perfil agrupado (R03b): Nivel central / Sedes regionales, sedes proyectadas listadas y sin Jefatura en sedes.
const CATALOG_SCOPES = [
  {id: 3, code: 'UPR-REQ', name: 'Requerimientos', level: 'CENTRAL', state: 'ACTIVA', enabled: true, region_code: null},
  {id: 20, code: 'REG-MAG', name: 'Sede Magallanes', level: 'REGIONAL', state: 'ACTIVA', enabled: true, region_code: '12'},
  {id: 21, code: 'REG-ARI', name: 'Sede Arica', level: 'REGIONAL', state: 'PROYECTADA', enabled: false, region_code: '15'},
  {id: 22, code: 'REG-OLD', name: 'Sede cerrada', level: 'REGIONAL', state: 'CERRADA', enabled: false, region_code: '05'},
  {id: 23, code: 'REG-SUR', name: 'Sede cerrada con funciones', level: 'REGIONAL', state: 'CERRADA', enabled: false, region_code: '14'},
  {id: 9, code: 'UGI', name: 'Unidad cerrada', level: 'CENTRAL', state: 'CERRADA', enabled: false, region_code: null},
];
const PUBLISHED = [{code: '15', name: 'Arica y Parinacota'}, {code: '05', name: 'Valparaíso'}, {code: '14', name: 'Los Ríos'}, {code: '12', name: 'Magallanes y de la Antártica Chilena'}];
const ROLES = ['ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION', 'LECTURA'].map(code => ({code, reach: 'AMBITO', group: 'X'}));
const siteGrants = [
  {id: 30, user_id: 7, role_code: 'PROFESIONAL', scope_id: 23, valid_from: NOW - HOUR, valid_until: null, revoked_at: null},
].map(grantOf);

test('perfil agrupado: proyectadas siempre, cerradas solo con funciones, sedes de norte a sur', () => {
  const items = profileScopes(CATALOG_SCOPES, siteGrants, PUBLISHED);
  assert.deepEqual(items.map(s => s.code), ['UPR-REQ', 'REG-MAG', 'REG-ARI', 'REG-SUR']);
  const groups = groupScopes(items, PUBLISHED);
  assert.deepEqual(groups.map(g => [g.level, g.items.map(s => s.code)]), [['CENTRAL', ['UPR-REQ']], ['REGIONAL', ['REG-ARI', 'REG-SUR', 'REG-MAG']]]);
  const arica = items.find(s => s.code === 'REG-ARI')!, sur = items.find(s => s.code === 'REG-SUR')!;
  assert.equal(arica.regionName, 'Arica y Parinacota');
  assert.ok(arica.staffable && offered('PROFESIONAL', arica), 'la sede proyectada admite su dotacion');
  assert.ok(!sur.staffable && !offered('PROFESIONAL', sur), 'la cerrada solo admite revocar');
  // El buscador tambien encuentra por el nombre de la region, sin tildes.
  assert.deepEqual(visibleScopes(items, new Map(), siteGrants, false, 'rios').map(s => s.code), ['REG-SUR']);
  assert.deepEqual(groupScopes(items.filter(s => s.level === 'CENTRAL'), PUBLISHED).map(g => g.level), ['CENTRAL'], 'se omite el grupo vacio');
});

test('sin Jefatura en sedes, salvo una heredada para revocarla; Direccion se ofrece', () => {
  const items = profileScopes(CATALOG_SCOPES, [], PUBLISHED), mag = items.find(s => s.code === 'REG-MAG')!;
  assert.deepEqual(columnsFor('CENTRAL', ROLES, [], items).map(r => r.code), ROLES.map(r => r.code));
  assert.deepEqual(columnsFor('REGIONAL', ROLES, [], items).map(r => r.code), ['ADMINISTRATIVO', 'PROFESIONAL', 'DIRECCION', 'LECTURA']);
  assert.ok(!offered('JEFATURA', mag) && offered('DIRECCION', mag));
  const inherited = [grantOf({id: 40, user_id: 7, role_code: 'JEFATURA', scope_id: 20, valid_from: NOW - HOUR, valid_until: null, revoked_at: null})];
  assert.ok(columnsFor('REGIONAL', ROLES, inherited, items).some(r => r.code === 'JEFATURA'));
  assert.ok(!offered('JEFATURA', mag), 'heredada: se muestra pero no se vuelve a ofrecer');
});

test('el filtro "solo con funciones" arranca activo solo si la cuenta tiene funciones por ambito', () => {
  assert.equal(startFiltered(live), true);
  assert.equal(startFiltered([]), false);
  assert.equal(startFiltered([grantOf({id: 50, user_id: 7, role_code: 'DISTRIBUIDOR', scope_id: null, valid_from: 0, valid_until: null, revoked_at: null})]), false);
});

test('una funcion de reportes por ambito hecha por API se conserva al guardar el perfil (G11)', () => {
  const roles = [{code: 'PROFESIONAL', reach: 'AMBITO', group: 'OPERATIVA'}, {code: 'REPORTES', reach: 'OPCIONAL', group: 'REPORTES'}, {code: 'REPORTES_PERSONAS', reach: 'OPCIONAL', group: 'REPORTES'}];
  const own = liveGrants([...grants, grantOf({id: 9, user_id: 7, role_code: 'REPORTES', scope_id: 3, valid_from: NOW - 9 * HOUR, valid_until: null, revoked_at: null})], 7, NOW);
  const profile = initial(own);
  // Sin tocar nada: el cuerpo la reenvia con su ambito y no hay diferencias.
  const body = payload(profile, NOW);
  assert.deepEqual(body.find(w => w.role === 'REPORTES'), {role: 'REPORTES', scope_id: 3});
  assert.deepEqual(diff(own, body), {grant: [], revoke: []});
  // Se lista como conservada y no cuenta en la tarjeta del ambito (la vista no la muestra como casilla).
  assert.deepEqual(preserved(profile, roles).map(e => keyOf(e.role, e.scope)), ['REPORTES@3']);
  assert.equal(countIn(profile, 3), 2);
  assert.equal(countIn(profile, 3, optionalRoles(roles)), 1);
  // Marcar Reportes institucional la agrega sin quitar la del ambito.
  const next = toggle(profile, 'REPORTES', null, true, own);
  const changes = diff(own, payload(next, NOW));
  assert.deepEqual(changes.grant, [{role: 'REPORTES'}]);
  assert.deepEqual(changes.revoke, []);
});
