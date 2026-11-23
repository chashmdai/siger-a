// Marca territorial y de riesgo de la bandeja de comunicaciones (lib/regionMark.ts, 0.7.0, D23; gate T03b), escenario 8:
// numero romano de los 16 codigos, color con los escenarios 1-4 (sede que cubre otra region), la RM del nivel central y
// sin region, y la marca roja de riesgo que respira solo por distribuir. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {attendanceText, regionMark, regionNumber, REGION_NUMBERS, RISK_TEXT} from '../src/lib/regionMark.ts';

const ALL = ['SOL', 'COLABORACION', 'TRANSFERENCIA', 'DERIVACION', 'ENTREGA'];
const REGIONS = [
  ['15', 'Arica y Parinacota'], ['01', 'Tarapacá'], ['02', 'Antofagasta'], ['03', 'Atacama'], ['04', 'Coquimbo'], ['05', 'Valparaíso'],
  ['13', 'Metropolitana de Santiago'], ['06', "Libertador General Bernardo O'Higgins"], ['07', 'Maule'], ['16', 'Ñuble'], ['08', 'Biobío'],
  ['09', 'La Araucanía'], ['14', 'Los Ríos'], ['10', 'Los Lagos'], ['11', 'Aysén del General Carlos Ibáñez del Campo'], ['12', 'Magallanes y de la Antártica Chilena'],
].map(([code, name]) => ({code, name}));
let next = 10;
const site = (code: string, name: string, region: string, coverage: string[], state = 'ACTIVA') =>
  ({id: next++, code, name, unit_code: 'REG', site_code: code, regional: true, level: 'REGIONAL', region_code: region, state, enabled: state === 'ACTIVA', receives: state === 'ACTIVA' ? ALL : [], coverage});
const UPR = {id: 1, code: 'UPR', name: 'Dirección UPR', unit_code: 'UPR', area_code: null, site_code: 'CENTRAL', level: 'CENTRAL', state: 'ACTIVA', enabled: true, receives: ALL, coverage: []};
// Escenarios 1-4: Coquimbo cubre Atacama, O'Higgins cubre Maule, Biobio cubre Nuble y La Araucania cubre Los Rios.
// Valparaiso tiene solo una sede PROYECTADA y Aysen una sede CERRADA que la cubria: ninguna atiende hoy.
const catalog = {regions: REGIONS, units: [{code: 'UPR', name: 'Protección'}], central_coverage: ['13'], scopes: [UPR,
  site('REG-COQ', 'Sede Coquimbo', '04', ['03']), site('REG-OHI', "Sede O'Higgins", '06', ['07']), site('REG-BIO', 'Sede Biobío', '08', ['16']),
  site('REG-ARA', 'Sede La Araucanía', '09', ['14']), site('REG-VAL', 'Sede Valparaíso', '05', [], 'PROYECTADA'), site('REG-LAG', 'Sede Los Lagos', '10', ['11'], 'CERRADA')]};
const row = (origin_region: string | null, risk = false, to_distribute = false) => ({origin_region, risk, to_distribute});

test('número de la región: los 16 códigos del Formulario, RM para 13 y nada para un código desconocido', () => {
  const expected = {'01': 'I', '02': 'II', '03': 'III', '04': 'IV', '05': 'V', '06': 'VI', '07': 'VII', '08': 'VIII', '09': 'IX', '10': 'X',
    '11': 'XI', '12': 'XII', '13': 'RM', '14': 'XIV', '15': 'XV', '16': 'XVI'};
  assert.deepEqual(REGION_NUMBERS, expected);
  for (const [code, number] of Object.entries(expected)) assert.equal(regionNumber(code), number, code);
  for (const bad of ['', '00', '17', '1', '7', 'XX', 'RM', null, undefined, 7, 'constructor', 'toString']) assert.equal(regionNumber(bad), null, String(bad));
});

test('escenario 8: Maule cubierta por O\'Higgins lleva VII con el color regional y su título', () => {
  assert.deepEqual(regionMark(catalog, row('07')), {text: 'VII', tone: 'regional', breathing: false, label: "Maule - atiende Sede O'Higgins"});
});

test('color regional con los escenarios 1-4 y con la región física de la sede', () => {
  assert.deepEqual(regionMark(catalog, row('03')), {text: 'III', tone: 'regional', breathing: false, label: 'Atacama - atiende Sede Coquimbo'});
  assert.deepEqual(regionMark(catalog, row('16')), {text: 'XVI', tone: 'regional', breathing: false, label: 'Ñuble - atiende Sede Biobío'});
  assert.deepEqual(regionMark(catalog, row('14')), {text: 'XIV', tone: 'regional', breathing: false, label: 'Los Ríos - atiende Sede La Araucanía'});
  assert.deepEqual(regionMark(catalog, row('04')), {text: 'IV', tone: 'regional', breathing: false, label: 'Coquimbo - atiende Sede Coquimbo'});
});

test('neutra: la RM del nivel central, una región sin nadie y las sedes PROYECTADA o CERRADA', () => {
  assert.deepEqual(regionMark(catalog, row('13')), {text: 'RM', tone: 'neutral', breathing: false, label: 'Metropolitana de Santiago - atiende el nivel central'});
  assert.deepEqual(regionMark(catalog, row('02')), {text: 'II', tone: 'neutral', breathing: false, label: 'Antofagasta - sin sede ni nivel central que la atienda'});
  assert.equal(regionMark(catalog, row('05'))?.tone, 'neutral', 'una sede PROYECTADA no atiende');
  assert.equal(regionMark(catalog, row('10'))?.tone, 'neutral', 'una sede CERRADA no atiende por región física');
  assert.equal(regionMark(catalog, row('11'))?.tone, 'neutral', 'una sede CERRADA no atiende por cobertura');
});

test('varias sedes y sede con el nivel central: el título nombra a todas', () => {
  const both = {...catalog, central_coverage: ['13', '03'], scopes: [...catalog.scopes, site('REG-ATA', 'Sede Atacama', '03', []), site('REG-HUA', 'Sede Huasco', '05', ['03'])]};
  assert.equal(attendanceText(both, '03'), 'Atacama - atienden Sede Atacama, Sede Coquimbo, Sede Huasco y el nivel central');
  assert.equal(attendanceText(both, '04'), 'Coquimbo - atiende Sede Coquimbo');
  assert.equal(attendanceText({...both, scopes: [UPR]}, '03'), 'Atacama - atiende el nivel central');
  assert.equal(attendanceText({...both, scopes: [UPR, site('REG-ATB', 'Sede Atacama B', '03', [])]}, '03'), 'Atacama - atienden Sede Atacama B y el nivel central');
  assert.equal(regionMark(both, row('03'))?.tone, 'regional');
});

test('sin región y sin riesgo no hay marca; un código desconocido equivale a sin región', () => {
  assert.equal(regionMark(catalog, row(null)), null);
  assert.equal(regionMark(catalog, row('')), null);
  assert.equal(regionMark(catalog, row('99')), null);
  assert.equal(regionMark(catalog, {}), null);
});

test('riesgo: roja en vez del color regional, también en la RM y sin región, con el texto accesible', () => {
  assert.deepEqual(regionMark(catalog, row('07', true)), {text: 'VII', tone: 'risk', breathing: false, label: RISK_TEXT + ". Maule - atiende Sede O'Higgins"});
  assert.deepEqual(regionMark(catalog, row('13', true)), {text: 'RM', tone: 'risk', breathing: false, label: 'Riesgo actual informado. Metropolitana de Santiago - atiende el nivel central'});
  assert.deepEqual(regionMark(catalog, row(null, true)), {text: 'Riesgo', tone: 'risk', breathing: false, label: 'Riesgo actual informado. Sin región declarada'});
});

test('riesgo: respira solo mientras la COM está por distribuir; distribuida queda fija', () => {
  assert.equal(regionMark(catalog, row('07', true, true))?.breathing, true);
  assert.equal(regionMark(catalog, row(null, true, true))?.breathing, true);
  assert.equal(regionMark(catalog, row('13', true, true))?.breathing, true);
  assert.equal(regionMark(catalog, row('07', true, false))?.breathing, false);
  // Sin riesgo nunca respira, aunque este por distribuir.
  assert.equal(regionMark(catalog, row('07', false, true))?.breathing, false);
  assert.equal(regionMark(catalog, row(null, false, true)), null);
});

test('solo el riesgo afirmativo que informa el servidor (risk === true) pinta de rojo', () => {
  for (const risk of [false, undefined, null, 'si', 'true', 1]) assert.notEqual(regionMark(catalog, {origin_region: '07', risk, to_distribute: true})?.tone, 'risk', String(risk));
});
