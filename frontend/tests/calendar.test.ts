// Calendario habil (G09): orden, anios, avisos de anios sin feriados nacionales, dia de la semana y alcance.
// node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {displayDay, holidaysOf, inYear, missingYears, onWeekend, scopeLabel, weekdayLabel, yearsOf, yearSummary} from '../src/features/admin/calendarModel.ts';

const REGIONS = [{code: '15', name: 'Arica y Parinacota'}, {code: '16', name: 'Ñuble'}];
const rows = [
  {id: 3, holiday_day: '2027-06-07', region_code: '15', name: 'Asalto y Toma del Morro de Arica'},
  {id: 1, holiday_day: '2026-12-25', region_code: null, name: 'Navidad'},
  {id: 4, holiday_day: '2026-08-20', region_code: '16', name: 'Regional sintético'},
  {id: 2, holiday_day: '2026-09-19', region_code: null, name: 'Glorias del Ejército'},
];

test('feriados ordenados por fecha, con region nula como nacional', () => {
  const h = holidaysOf(rows);
  assert.deepEqual(h.map(x => x.day), ['2026-08-20', '2026-09-19', '2026-12-25', '2027-06-07']);
  assert.equal(h[1].region, null);
  assert.equal(h[0].region, '16');
  assert.equal(holidaysOf([{id: 9, holiday_day: '2026-01-01', region_code: '', name: 'X'}])[0].region, null);
});

test('anios del selector: los registrados mas el actual y el siguiente', () => {
  const h = holidaysOf(rows);
  assert.deepEqual(yearsOf(h, '2026-11-21'), [2026, 2027]);
  assert.deepEqual(yearsOf(h, '2028-03-01'), [2026, 2027, 2028, 2029]);
  assert.deepEqual(yearsOf([], '2026-11-21'), [2026, 2027]);
  assert.deepEqual(inYear(h, 2027).map(x => x.id), [3]);
});

test('aviso por anio sin feriados nacionales (un regional no basta)', () => {
  const h = holidaysOf(rows);
  assert.deepEqual(missingYears(h, '2026-11-21'), [2027]);
  assert.deepEqual(missingYears([], '2026-11-21'), [2026, 2027]);
  assert.deepEqual(missingYears(h, '2025-05-01'), [2025]);
});

test('dia de la semana sin zona horaria y feriados en fin de semana', () => {
  assert.equal(weekdayLabel('2026-12-25'), 'viernes');
  assert.equal(weekdayLabel('2026-09-19'), 'sábado');
  assert.equal(weekdayLabel('2026-04-05'), 'domingo');
  assert.equal(weekdayLabel('2026-09-16'), 'miércoles');
  assert.ok(onWeekend('2026-09-19') && onWeekend('2026-04-05'));
  assert.ok(!onWeekend('2026-12-25'));
  assert.equal(displayDay('2026-12-25'), '25-12-2026');
});

test('alcance y resumen en texto', () => {
  assert.equal(scopeLabel(null, REGIONS), 'Nacional');
  assert.equal(scopeLabel('16', REGIONS), 'Región Ñuble');
  assert.equal(scopeLabel('08', REGIONS), 'Región 08');
  const h = holidaysOf(rows);
  assert.equal(yearSummary(inYear(h, 2026)), '2 nacionales y 1 regional');
  assert.equal(yearSummary(inYear(h, 2027)), '1 regional');
  assert.equal(yearSummary([]), 'Sin feriados registrados');
});
