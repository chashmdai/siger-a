// Selector de destino (lib/destination.ts, R03a). El catalogo sintetico imita /api/catalog: cada ambito trae level,
// state, region_code y receives (calculado en el servidor con Access.receiverOf). node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {complete, destinations, filterRegions, initialChoice, levels, NO_REGION, payloadFor, propose, receiver, withLevel, withRegion, withUnit} from '../src/lib/destination.ts';
import type {Operation} from '../src/lib/destination.ts';
import {REGIONS} from '../src/features/attention/adult/catalog.ts';

// Orden de catalog.regions tal como lo entrega el servidor (territorio.json, de norte a sur). Object.keys(REGIONS) no
// sirve: JavaScript ordena primero las claves numericas sin cero inicial (10 a 16).
const NORTH_TO_SOUTH = ['15', '01', '02', '03', '04', '05', '13', '06', '07', '16', '08', '09', '14', '10', '11', '12'];

const ALL = ['SOL', 'COLABORACION', 'TRANSFERENCIA', 'DERIVACION', 'ENTREGA'];
let next = 1;
const scope = (code: string, unit: string, extra: Record<string, unknown> = {}) => ({
  id: next++, code, name: 'Ámbito ' + code, unit_code: unit, area_code: null, site_code: 'CENTRAL', regional: false,
  region_code: null, state: 'ACTIVA', enabled: true, level: 'CENTRAL', receives: [] as string[], ...extra,
});
const site = (code: string, region: string | null, state = 'ACTIVA', name = 'Sede ' + code) =>
  scope(code, 'REG', {site_code: 'SEDE-' + code, regional: true, region_code: region, state, enabled: state === 'ACTIVA', level: 'REGIONAL', name, receives: state === 'ACTIVA' ? ALL : []});

const UPR = scope('UPR', 'UPR', {receives: ALL}), UPD = scope('UPD', 'UPD', {receives: ALL});
const UPR_REQ = scope('UPR-REQ', 'UPR', {area_code: 'REQ', receives: ALL}), UPR_RJ = scope('UPR-RJ', 'UPR', {area_code: 'RJ'});
const UPD_EDU = scope('UPD-EDU', 'UPD', {area_code: 'EDU'}), OFF = scope('UPD-OFF', 'UPD', {area_code: 'OFF', enabled: false, state: 'CERRADA'});
const DEF = scope('DEF', 'DEF');
const VALPO = site('REG-V', '05', 'ACTIVA', 'Sede Valparaíso'), BIOBIO_A = site('REG-B1', '08'), BIOBIO_B = site('REG-B2', '08');
const NUBLE = site('REG-N', '16', 'ACTIVA', 'Sede Ñuble'), AYSEN = site('REG-AY', '11', 'ACTIVA', 'Sede Aysén');
const PROJECTED = site('REG-P', '13', 'PROYECTADA'), CLOSED = site('REG-C', '01', 'CERRADA'), OLD = site('REG-OLD', null, 'ACTIVA', 'Sede antigua');
const catalog = {
  scopes: [UPR, UPD, UPR_REQ, UPR_RJ, UPD_EDU, OFF, DEF, VALPO, BIOBIO_A, BIOBIO_B, NUBLE, AYSEN, PROJECTED, CLOSED, OLD],
  regions: NORTH_TO_SOUTH.map(code => ({code, name: REGIONS[code]})),
  units: [{code: 'UPR', name: 'Proteccion'}, {code: 'UPD', name: 'Promocion'}, {code: 'DEF', name: 'Defensoria'}],
};

test('destinos por operación: solo lo publicado en receives, sin sedes proyectadas ni cerradas', () => {
  const sol = destinations(catalog, 'SOL');
  assert.deepEqual(sol.central.map(u => u.code), ['UPR', 'UPD']);
  const sites = sol.regions.flatMap(r => r.sites.map(s => s.code));
  assert.ok(!sites.includes('REG-P') && !sites.includes('REG-C'));
  assert.deepEqual(levels(sol), ['CENTRAL', 'REGIONAL']);
  // Sin destinos regionales publicados queda solo el nivel central, y la eleccion inicial es central.
  const onlyCentral = destinations({...catalog, scopes: [UPR, UPR_REQ]}, 'SOL');
  assert.deepEqual(levels(onlyCentral), ['CENTRAL']);
  assert.equal(initialChoice(onlyCentral, '05').level, 'CENTRAL');
  assert.equal(destinations({...catalog, scopes: [DEF, PROJECTED]}, 'ENTREGA').central.length + destinations({...catalog, scopes: [DEF, PROJECTED]}, 'ENTREGA').regions.length, 0);
});

test('áreas: en una SOL todas las habilitadas de la unidad (sugeridas); en flujos y entregas solo las que reciben', () => {
  const sol = destinations(catalog, 'SOL');
  assert.deepEqual(sol.central[0].areas.map(a => [a.code, a.direct]), [['UPR-REQ', true], ['UPR-RJ', false]]);
  assert.deepEqual(sol.central[1].areas.map(a => a.code), ['UPD-EDU'], 'un área deshabilitada no se ofrece');
  for (const op of ['COLABORACION', 'TRANSFERENCIA', 'DERIVACION', 'ENTREGA'] as Operation[]) {
    const d = destinations(catalog, op);
    assert.deepEqual(d.central[0].areas.map(a => a.code), ['UPR-REQ'], op);
    assert.deepEqual(d.central[1].areas, [], op);
  }
  // Un área receptora cuya Dirección no recibe se ofrece como destino propio.
  const orphan = destinations({...catalog, scopes: [{...UPR, receives: []}, UPR_REQ]}, 'COLABORACION');
  assert.deepEqual(orphan.central.map(u => [u.code, u.standalone]), [['UPR-REQ', true]]);
  assert.equal(receiver(orphan, {...withLevel('CENTRAL'), scope: UPR_REQ.id}), 'Coordinación de Ámbito UPR-REQ');
});

test('regiones de norte a sur, solo con sedes que reciben, y las sedes sin región al final', () => {
  const d = destinations(catalog, 'SOL');
  assert.deepEqual(d.regions.map(r => r.code), ['05', '16', '08', '11', NO_REGION]);
  assert.deepEqual(d.regions.find(r => r.code === '08')?.sites.map(s => s.code), ['REG-B1', 'REG-B2']);
  assert.equal(d.regions.at(-1)?.name, 'Sede sin región asignada');
  assert.deepEqual(d.regions.at(-1)?.sites.map(s => s.code), ['REG-OLD']);
});

test('búsqueda de región sin tildes ni mayúsculas, palabra por palabra, también por la sede', () => {
  const d = destinations(catalog, 'SOL');
  assert.deepEqual(filterRegions(d.regions, 'nuble').map(r => r.code), ['16']);
  assert.deepEqual(filterRegions(d.regions, 'AYSEN').map(r => r.code), ['11']);
  assert.deepEqual(filterRegions(d.regions, 'bio').map(r => r.code), ['08']);
  assert.deepEqual(filterRegions(d.regions, 'antigua').map(r => r.code), [NO_REGION]);
  assert.equal(filterRegions(d.regions, '').length, d.regions.length);
  assert.deepEqual(filterRegions(d.regions, 'region inexistente'), []);
});

test('propuesta por la región declarada: sede única elegida, varias sedes solo la región, sin sede ninguna', () => {
  const d = destinations(catalog, 'SOL');
  assert.deepEqual(propose(d, '05'), {level: 'REGIONAL', region: '05', scope: VALPO.id, area: null, matter: ''});
  assert.deepEqual(propose(d, '08'), {level: 'REGIONAL', region: '08', scope: null, area: null, matter: ''});
  assert.equal(propose(d, '13'), null, 'la región 13 solo tiene una sede proyectada');
  assert.equal(propose(d, null), null);
  assert.equal(propose(d, NO_REGION), null);
  assert.equal(initialChoice(d, '05').scope, VALPO.id);
  assert.deepEqual(initialChoice(d, '13'), withLevel('CENTRAL'));
  // Elegir una región con una sola sede la deja elegida; con varias, hay que elegir la sede.
  assert.equal(withRegion(d, withLevel('REGIONAL'), '16').scope, NUBLE.id);
  assert.equal(complete(withRegion(d, withLevel('REGIONAL'), '08')), false);
});

test('datos de cada comando: materia solo en SOL hacia una sede; área solo en el nivel central', () => {
  const sol = destinations(catalog, 'SOL');
  const central = {...withUnit(withLevel('CENTRAL'), UPR.id), area: UPR_RJ.id};
  assert.deepEqual(payloadFor('SOL', central), {scope_id: UPR.id, area_scope_id: UPR_RJ.id});
  assert.equal(receiver(sol, central), 'Dirección UPR (área sugerida: Ámbito UPR-RJ)');
  assert.equal(receiver(sol, {...central, area: UPR_REQ.id}), 'Coordinación de Ámbito UPR-REQ');
  assert.deepEqual(payloadFor('SOL', {...central, matter: 'UPD'}), {scope_id: UPR.id, area_scope_id: UPR_RJ.id}, 'sin materia en el nivel central');
  const regional = {...withRegion(sol, withLevel('REGIONAL'), '05'), matter: 'UPD', area: UPR_RJ.id};
  assert.deepEqual(payloadFor('SOL', regional), {scope_id: VALPO.id, suggested_matter: 'UPD'}, 'sin área en una sede');
  assert.equal(receiver(sol, regional), 'Coordinación regional · Sede Valparaíso');
  assert.deepEqual(payloadFor('SOL', withUnit(withLevel('CENTRAL'), UPD.id)), {scope_id: UPD.id});
  for (const op of ['COLABORACION', 'TRANSFERENCIA', 'DERIVACION'] as Operation[]) {
    assert.deepEqual(payloadFor(op, regional), {target_scope: VALPO.id}, op);
    assert.deepEqual(payloadFor(op, {...central, area: UPR_REQ.id}), {target_scope: UPR_REQ.id}, op);
    assert.deepEqual(payloadFor(op, withUnit(withLevel('CENTRAL'), UPD.id)), {target_scope: UPD.id}, op);
  }
  assert.deepEqual(payloadFor('ENTREGA', regional), {scope_id: VALPO.id});
  assert.deepEqual(payloadFor('ENTREGA', {...central, area: UPR_REQ.id}), {scope_id: UPR_REQ.id});
  assert.deepEqual(payloadFor('SOL', withLevel('REGIONAL')), {}, 'sin destino elegido no se envía nada');
  // Cambiar de nivel o de unidad limpia lo elegido después.
  assert.deepEqual(withLevel('CENTRAL'), {level: 'CENTRAL', scope: null, area: null, region: '', matter: ''});
  assert.equal(withUnit(central, UPD.id).area, null);
});
