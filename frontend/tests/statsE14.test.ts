// Explorador, gate E14 (correcciones de presentacion de E13, revision de capturas del 2026-11-26): etiquetas de periodo
// abreviadas o en dos lineas sin superponerse ni cortarse (1), comparacion con un corte pareada y con su codigo (2), texto de
// las barras pareadas con eje de periodo (3), periodo sin registros y la nota unica por desglose (4) y Calidad sin enlace con
// 0 pendientes (5). Las cifras estan escritas a mano. node --test.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {choices, PERIOD_PAIRED, periodAxis, periodTick, pieReason, TICK_CHAR, TICK_GAP} from '../src/components/charts/chartModel.ts';
import {breakdownView, chartData, chartShape, cutComparisonText, EMPTY_PERIOD, emptyQuery, howToRead, readResult, statsCatalog, tableModel} from '../src/features/stats/statsModel.ts';
import {count, NO_PENDING_TEXT, pendingLink} from '../src/features/stats/qualityModel.ts';
import {CATALOG, response, USER} from './stats-fixture.mjs';

const cat = statsCatalog(CATALOG)!;
const PERIOD = {type: 'CUENTA_PUBLICA', from: '2025-07-01', to: '2026-06-30'};
const MONTHS = ['julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre', 'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio'];
const months = (n: number) => Array.from({length: n}, (_, i) => `${MONTHS[i % 12]} de ${2024 + Math.floor((i + 6) / 12)}`);
const PLOT = 720 - 56 - 24;

test('corrección 1: rótulos de periodo abreviados, sin cortar y con el nombre completo aparte', () => {
  assert.deepEqual(periodTick('julio de 2026'), ['jul', '2026']);
  assert.deepEqual(periodTick('septiembre de 2025'), ['sep', '2025']);
  assert.deepEqual(periodTick('1.º trimestre de 2026'), ['T1', '2026']);
  assert.deepEqual(periodTick('2.º semestre de 2025'), ['S2', '2025']);
  assert.deepEqual(periodTick('Cuenta Pública 2025-2026'), ['CP', '2025-2026']);
  assert.deepEqual(periodTick('01-07-2026'), ['01-07', '2026']);
  assert.deepEqual(periodTick('2026'), ['2026']);
  assert.deepEqual(periodTick('Serie histórica de Estudios'), ['Serie histórica', 'de Estudios'], 'otro texto: dos líneas por palabras');
  // Con espacio suficiente, en una línea; el nombre completo queda en la ayuda.
  const wide = periodAxis(['1.º trimestre de 2026', '2.º trimestre de 2026'], PLOT / 2);
  assert.deepEqual(wide.ticks.map(t => t.lines), [['T1 2026'], ['T2 2026']]);
  assert.equal(wide.ticks[0].title, '1.º trimestre de 2026');
  assert.equal(wide.step, 1);
});

test('corrección 1: con 12, 24, 36 y 60 meses ningún rótulo se superpone ni se corta; los 12 meses de una Cuenta Pública se rotulan todos', () => {
  const expected: Record<number, {lines: number; step: number}> = {12: {lines: 2, step: 1}, 24: {lines: 2, step: 2}, 36: {lines: 2, step: 2}, 60: {lines: 2, step: 4}};
  for (const n of [12, 24, 36, 60]) {
    const band = PLOT / n;
    const axis = periodAxis(months(n), band);
    assert.deepEqual({lines: axis.ticks[0].lines.length, step: axis.step}, expected[n], `${n} meses`);
    const widest = Math.max(...axis.ticks.flatMap(t => t.lines.map(l => l.length * TICK_CHAR)));
    assert.ok(axis.step * band >= widest + TICK_GAP, `${n} meses: dos rótulos visibles no se tocan`);
    for (const t of axis.ticks) {
      assert.ok(t.lines.every(l => !l.includes('…')), 'sin cortar');
      assert.match(t.title, /^[a-z]+ de \d{4}$/, 'nombre completo en la ayuda');
    }
  }
});

test('corrección 2: la comparación con un corte va pareada (cifra actual y del corte), sin circular y con «Comparación con el corte <código>»', () => {
  const r = readResult(response({rows: ['TIPO_SOLICITUD'], values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'CONSULTA_ORIENTACION', label: 'Consulta u orientación'}]},
    cells: [{row: ['VULNERACION'], column: [], n: 5}, {row: ['CONSULTA_ORIENTACION'], column: [], n: 2}], grand: {n: 7, unique: 7},
    rowTotals: [{key: ['VULNERACION'], n: 5}, {key: ['CONSULTA_ORIENTACION'], n: 2}],
    comparison: {cut_id: 3, cut_code: 'CORTE-2026-001', cells: [{row: ['VULNERACION'], column: [], base_n: 3, delta_n: 2, delta_percent: 66.7},
      {row: ['CONSULTA_ORIENTACION'], column: [], base_n: 3, delta_n: -1, delta_percent: -33.3}], totals: {grand: {base_n: 6, delta_n: 1, delta_percent: 16.7}, rows: [], columns: []}}}));
  const q = {...emptyQuery(PERIOD), rows: ['TIPO_SOLICITUD'], compare: 'CORTE', compareCut: 3};
  const t = tableModel(r, q.rows, q.columns);
  const data = chartData(t, 'n', true, '', 'CORTE-2026-001');
  assert.deepEqual(data.series.map(s => s.label), ['Cifra actual', 'Corte CORTE-2026-001']);
  assert.deepEqual(data.series.map(s => s.values), [[5, 2], [3, 3]], 'cifra actual y la del corte, a mano');
  assert.equal(data.series[1].base, true);
  const shape = chartShape(cat, q, t, true);
  assert.ok(!choices(shape, [5, 2], 7).find(c => c.kind === 'circular')!.ok, 'sin circular');
  assert.ok(choices(shape).find(c => c.kind === 'pareadas')!.ok, 'barras pareadas');
  assert.equal(cutComparisonText('CORTE-2026-001'), 'Comparación con el corte CORTE-2026-001');
  const lines = howToRead({cat, q, r, scope: 'Toda la institución'});
  assert.ok(lines.some(l => l.startsWith('Comparación con el corte CORTE-2026-001')), lines.join(' | '));
  // Una comparación de periodos conserva su texto y sus rótulos.
  assert.deepEqual(chartData(t, 'n', true, 'Cuenta Pública 2024-2025').series.map(s => s.label), ['Periodo consultado', 'Comparación: Cuenta Pública 2024-2025']);
});

test('corrección 3: con un eje de periodo, la razón de las barras pareadas dice «Usa columnas pareadas», como el botón', () => {
  const shape = {rows: 1, columns: 0, period: true, multipleRows: false, multipleColumns: false, comparison: true, percent: false, categories: 12, series: 2};
  const all = choices(shape);
  assert.equal(all.find(c => c.kind === 'pareadas')!.reason, PERIOD_PAIRED);
  assert.ok(PERIOD_PAIRED.startsWith('Usa columnas pareadas'));
  assert.equal(all.find(c => c.kind === 'columnas')!.label, 'Columnas pareadas');
  assert.match(pieReason(shape), /Usa columnas pareadas/);
  assert.match(pieReason({...shape, period: false}), /Usa barras pareadas/, 'sin periodo siguen las barras pareadas');
});

test('corrección 4: un periodo sin registros muestra el texto en lugar del gráfico; la nota «Sin registros en» una sola vez por desglose', () => {
  const note = 'Sin registros en: Sin información, No declarado, No aplica.';
  assert.deepEqual(breakdownView(note, 0), {chart: false, empty: EMPTY_PERIOD, tableNote: note, chartNotes: []});
  assert.deepEqual(breakdownView(note, 4), {chart: true, empty: '', tableNote: '', chartNotes: [note]});
  assert.deepEqual(breakdownView('', 4), {chart: true, empty: '', tableNote: '', chartNotes: []});
  for (const total of [0, 4]) {
    const v = breakdownView(note, total);
    assert.equal([v.tableNote, ...v.chartNotes].filter(x => x === note).length, 1, `una sola nota con total ${total}`);
  }
  assert.equal(EMPTY_PERIOD, 'Sin registros en el periodo.');
});

test('corrección 5: Calidad no ofrece el enlace a la bandeja cuando la variable tiene 0 pendientes', () => {
  const user = {...USER, grants: [...USER.grants, {role: 'CONSULTA_INSTITUCIONAL', scope: null}]};
  const withBoard = pendingLink(user, 'TIPO_SOLICITUD', 3);
  assert.ok(withBoard && withBoard.href, 'con pendientes y con la bandeja: enlace');
  assert.deepEqual(pendingLink(user, 'TIPO_SOLICITUD', 0), {href: null, text: NO_PENDING_TEXT});
  assert.deepEqual(pendingLink(user, 'DERECHO', 0), {href: null, text: NO_PENDING_TEXT});
  assert.equal(pendingLink(user, 'REGION_REQUIRENTE', 0), null, 'la región sigue sin enlace');
  // La cantidad sale de la celda PENDIENTE del servidor (0 si no hay celda).
  const r = readResult(response({rows: ['VARIABLE'], columns: ['ESTADO_VARIABLE'], measure: 'COMPLETITUD',
    values: {VARIABLE: [{key: 'TIPO_SOLICITUD', label: 'Tipo de solicitud'}, {key: 'VIA_INGRESO', label: 'Vía de ingreso'}], ESTADO_VARIABLE: [{key: 'INFORMADO', label: 'Informado'}, {key: 'PENDIENTE', label: 'Pendiente'}]},
    cells: [{row: ['TIPO_SOLICITUD'], column: ['INFORMADO'], n: 6}, {row: ['TIPO_SOLICITUD'], column: ['PENDIENTE'], n: 4}, {row: ['VIA_INGRESO'], column: ['INFORMADO'], n: 10}],
    grand: {n: 20, unique: 10}}));
  const t = tableModel(r, ['VARIABLE'], ['ESTADO_VARIABLE']);
  assert.equal(count(t, ['TIPO_SOLICITUD'], 'PENDIENTE'), 4);
  assert.equal(count(t, ['VIA_INGRESO'], 'PENDIENTE'), 0);
  assert.equal(pendingLink(user, 'VIA_INGRESO', count(t, ['VIA_INGRESO'], 'PENDIENTE'))!.href, null, 'la vía con 0 % pendiente no enlaza (hallazgo de E13)');
});
