// Pantalla de reportes (features/reports/reportsModel.ts, G12): cuerpo de la consulta y de la exportacion, agrupaciones
// ofrecidas, formato con coma decimal, barras, rotulos de grupos y nombre del archivo. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {activeFilters, barWidth, chileToday, clearFilters, count, days, defaultPeriod, defaultSplit, exportBody, filenameOf, groupParts, groupsFor, hours,
  initialFilters, keepGroup, requestBody, tallyLabel} from '../src/features/reports/reportsModel.ts';

const VOLUME = {code: 'VOLUMEN', kind: 'VOLUMEN' as const}, AGE = {code: 'ANTIGUEDAD', kind: 'ANTIGUEDAD' as const};
const TENURE = {code: 'TENENCIA_SOL', kind: 'TIEMPO' as const}, TOTAL = {code: 'REQ_TOTAL', kind: 'TIEMPO' as const};
const REGIONS = [{code: '15', name: 'Arica y Parinacota'}, {code: '05', name: 'Valparaíso'}];

test('agrupaciones: volumen no agrupa; antigüedad sin salida ni persona; salida solo con varias salidas; persona solo con su función', () => {
  assert.deepEqual(groupsFor(VOLUME, true), ['NINGUNA']);
  assert.ok(!groupsFor(AGE, true).includes('SALIDA') && !groupsFor(AGE, true).includes('PERSONA'));
  assert.ok(groupsFor(TENURE, true).includes('SALIDA') && groupsFor(TENURE, true).includes('PERSONA'));
  assert.ok(!groupsFor(TOTAL, true).includes('SALIDA'));
  assert.ok(!groupsFor(TENURE, false).includes('PERSONA'));
  assert.equal(keepGroup('PERSONA', groupsFor(TENURE, false)), 'NINGUNA');
  assert.equal(keepGroup('AMBITO', groupsFor(TOTAL, false)), 'AMBITO');
  assert.deepEqual(defaultSplit('REQ_TOTAL'), {byOrigin: true, byExit: false});
  assert.deepEqual(defaultSplit('TENENCIA_SOL'), {byOrigin: false, byExit: true});
});

test('cuerpo de la consulta y de la exportación: sin filtros vacíos, ámbito como número y período solo donde corresponde', () => {
  const f = {...initialFilters('2026-11-21'), report: 'REQ_TOTAL', group: 'AMBITO', byOrigin: true, unit: 'UPR', scope: '7', matter: 'SIN'};
  assert.deepEqual(requestBody(f, TOTAL), {report: 'REQ_TOTAL', from: '2025-11-22', to: '2026-11-21', group: 'AMBITO', by_origin: true, by_exit: false, unit: 'UPR', scope: 7, matter: 'SIN'});
  assert.deepEqual(requestBody({...f, report: 'VOLUMEN'}, VOLUME), {report: 'VOLUMEN', from: '2025-11-22', to: '2026-11-21', unit: 'UPR', scope: 7, matter: 'SIN'});
  assert.deepEqual(requestBody({...f, report: 'ANTIGUEDAD', byOrigin: false}, AGE), {report: 'ANTIGUEDAD', group: 'AMBITO', by_origin: false, unit: 'UPR', scope: 7, matter: 'SIN'});
  // La exportacion lleva el periodo y los filtros, nunca el reporte ni la agrupacion.
  assert.deepEqual(exportBody(f), {from: '2025-11-22', to: '2026-11-21', unit: 'UPR', scope: 7, matter: 'SIN'});
  assert.equal(activeFilters(f), 3);
  assert.equal(activeFilters(clearFilters(f)), 0);
  assert.equal(clearFilters(f).report, 'REQ_TOTAL');
});

test('período por defecto: últimos 12 meses en hora de Chile', () => {
  assert.deepEqual(defaultPeriod('2026-11-21'), {from: '2025-11-22', to: '2026-11-21'});
  assert.deepEqual(defaultPeriod('2026-01-01'), {from: '2025-01-02', to: '2026-01-01'});
  // 21-11-2026 02:30 UTC es aun el 20 en Chile (UTC-3).
  assert.equal(chileToday(new Date('2026-11-21T02:30:00Z')), '2026-11-20');
});

test('cifras con coma decimal, guion sin tramos y barras que nunca desaparecen', () => {
  assert.equal(hours(30), '30 h');
  assert.equal(hours(2.25), '2,25 h');
  assert.equal(days(1.5), '1,5 dh');
  assert.equal(hours(null), '—');
  assert.equal(count(12345), '12.345');
  assert.equal(barWidth(50, 100), 50);
  assert.equal(barWidth(0.1, 1000), 2);
  assert.equal(barWidth(0, 100), 0);
  assert.equal(barWidth(5, 0), 0);
  assert.equal(barWidth(150, 100), 100);
});

test('rótulos de grupos: ámbito con región, nivel, materia nula, origen, salida y persona', () => {
  const group = {scope_code: 'REG-DEMO', regional: true, region: '05', level: 'REGIONAL', matter: null, origin: 'ATENCION_DIRECTA', exit: 'SOL_REDIRIGIDA', person_name: null, unit: 'REG'};
  assert.deepEqual(groupParts(['AMBITO', 'REGION', 'MATERIA', 'ORIGEN', 'SALIDA', 'PERSONA', 'UNIDAD'], group, REGIONS).map(p => p.label),
    ['REG-DEMO · Valparaíso', 'Región Valparaíso', 'Sin materia', 'Atención directa', 'Redirigida', 'Sin responsable', 'REG']);
  assert.equal(groupParts(['REGION'], {level: 'CENTRAL'}, REGIONS)[0].label, 'Nivel central');
  assert.equal(groupParts(['AMBITO'], {scope_code: 'UPR-REQ', regional: false}, REGIONS)[0].label, 'UPR-REQ');
  assert.equal(tallyLabel('complexity', null), 'Sin clasificar');
  assert.equal(tallyLabel('channel', 'TELEFONO'), 'Teléfono');
  assert.equal(tallyLabel('unit', 'UPR'), 'UPR');
  assert.equal(tallyLabel('state', 'ABIERTO'), 'Abierto');
});

test('nombre del archivo: el del servidor si es seguro; si no, uno neutro', () => {
  assert.equal(filenameOf('attachment; filename="siger-req-2026-01-01-2026-12-31.csv"', 'x.csv'), 'siger-req-2026-01-01-2026-12-31.csv');
  assert.equal(filenameOf(null, 'siger-cas.csv'), 'siger-cas.csv');
  assert.equal(filenameOf('attachment; filename="../../a b.csv"', 'siger-cas.csv'), 'siger-cas.csv');
});
