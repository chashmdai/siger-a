// Graficos del explorador (0.8.0, gate E12; DISENO 10.3): components/charts/chartModel.ts y chartExport.ts. Sugerencia
// segun la forma de los datos, reglas del circular, escala que parte en 0, paleta de tokens.css con su contraste
// (3:1 para los elementos graficos y 4,5:1 para el texto sobre la superficie) y SVG descargable. node --test.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {barLength, BASE_COLOR, byValue, CHART_COLORS, choices, contrast, GRID_COLOR, linePoints, MUTED_COLOR, pieReason, pieSlices, scale, suggest, TEXT_COLOR} from '../src/components/charts/chartModel.ts';
import {standalone, wrap} from '../src/components/charts/chartExport.ts';

const shape = (o = {}) => ({rows: 1, columns: 0, period: false, multipleRows: false, multipleColumns: false, comparison: false, percent: false, categories: 4, series: 1, ...o});
const ok = (s: ReturnType<typeof shape>, kind: string, values?: number[], total?: number) => choices(s, values, total).find(c => c.kind === kind)!.ok;

test('sugerencia según la forma: periodo -> líneas; una dimensión -> barras; dos -> agrupadas; múltiple -> barras; comparación -> pareadas', () => {
  assert.equal(suggest(shape({period: true})), 'lineas');
  assert.equal(suggest(shape()), 'barras');
  assert.equal(suggest(shape({columns: 1, series: 3})), 'agrupadas');
  assert.equal(suggest(shape({multipleRows: true})), 'barras');
  assert.equal(suggest(shape({comparison: true, series: 2})), 'pareadas');
  assert.equal(suggest(shape({period: true, comparison: true, series: 2})), 'pareadas');
  assert.equal(suggest(shape({categories: 0})), null);
  assert.equal(suggest(shape({categories: 81})), null, 'demasiadas categorías: la tabla');
});

test('apiladas solo si las columnas son de valor único y en cantidades; más de 8 series no se colorean', () => {
  assert.ok(ok(shape({columns: 1, series: 3}), 'apiladas'));
  assert.ok(!ok(shape({columns: 1, series: 3, multipleColumns: true}), 'apiladas'));
  assert.ok(!ok(shape({columns: 1, series: 3, percent: true}), 'apiladas'));
  assert.ok(!ok(shape({columns: 1, series: 9}), 'agrupadas'));
  assert.ok(!ok(shape({period: true, columns: 1, series: 9}), 'lineas'));
  assert.ok(!ok(shape(), 'lineas'), 'sin periodo no hay líneas');
});

test('circular: una dimensión de valor único, sin columnas, 6 categorías o menos y partes que suman el total; si no, la razón', () => {
  assert.ok(ok(shape({categories: 3}), 'circular', [2, 1, 1], 4));
  assert.match(pieReason(shape({multipleRows: true})), /varios valores/);
  assert.match(pieReason(shape({columns: 1, series: 2})), /columnas/);
  assert.match(pieReason(shape({categories: 7})), /6 o menos/);
  assert.ok(ok(shape({categories: 6}), 'circular', [1, 1, 1, 1, 1, 1], 6));
  assert.match(pieReason(shape({categories: 3}), [2, 2, 1], 4), /no suman el total/);
  assert.match(pieReason(shape({period: true})), /líneas/);
  assert.match(pieReason(shape({percent: true})), /Cantidad/);
  assert.match(pieReason(shape({comparison: true})), /pareadas/);
  for (const c of choices(shape({multipleRows: true}))) if (!c.ok) assert.ok(c.reason.length > 10, c.kind);
  const slices = pieSlices([2, 1, 1], 4);
  assert.equal(slices.reduce((a, s) => a + s.percent, 0), 100);
  assert.ok(Math.abs(slices[2].end - Math.PI * 2) < 1e-9);
});

test('las barras siempre parten en 0: escala desde 0, largo proporcional y un valor nulo o cero sin barra', () => {
  for (const max of [1, 3, 7, 12, 99, 1234, 50005]) {
    const s = scale(max);
    assert.equal(s.ticks[0], 0, 'la primera marca es 0');
    assert.ok(s.max >= max && s.ticks[s.ticks.length - 1] === s.max, String(max));
    assert.ok(s.ticks.every(t => Number.isInteger(t)), 'conteos sin marcas fraccionarias');
  }
  assert.equal(barLength(0, 10, 300), 0);
  assert.equal(barLength(null, 10, 300), 0);
  assert.equal(barLength(-3, 10, 300), 0);
  assert.equal(barLength(5, 10, 300), 150);
  assert.equal(barLength(10, 10, 300), 300);
  const pts = linePoints([0, 5, null, 10], 300, 100, 10);
  assert.equal(pts[0].y, 100, 'el 0 queda en la base');
  assert.equal(pts[3].y, 0);
  assert.equal(pts[2].y, null);
  // De mayor a menor, con los estados especiales al final.
  assert.deepEqual(byValue([{value: 1}, {value: 5, special: true}, {value: 3}]).map(x => x.value), [3, 1, 5]);
});

const tokens = readFileSync(fileURLToPath(new URL('../src/styles/tokens.css', import.meta.url)), 'utf8');
const token = (name: string) => (new RegExp('--' + name + ':\\s*(#[0-9a-fA-F]{6})').exec(tokens) ?? [])[1]?.toLowerCase();

test('paleta de tokens.css: los mismos valores del modelo, contraste 3:1 de lo gráfico y 4,5:1 del texto sobre la superficie', () => {
  const surface = token('surface')!;
  assert.equal(surface, '#ffffff');
  assert.deepEqual(CHART_COLORS.map((_, i) => token('chart-' + (i + 1))), CHART_COLORS.map(c => c.toLowerCase()));
  assert.equal(token('chart-base'), BASE_COLOR.toLowerCase());
  assert.equal(token('chart-text'), TEXT_COLOR.toLowerCase());
  assert.equal(token('chart-muted'), MUTED_COLOR.toLowerCase());
  assert.equal(token('chart-grid'), GRID_COLOR.toLowerCase());
  for (const c of [...CHART_COLORS, BASE_COLOR]) {
    assert.ok(contrast(c, surface) >= 3, `${c}: ${contrast(c, surface).toFixed(2)}`);
    assert.ok(contrast(c, token('surface-2')!) >= 3, `${c} sobre surface-2`);
  }
  for (const c of [TEXT_COLOR, MUTED_COLOR]) assert.ok(contrast(c, surface) >= 4.5, `${c}: ${contrast(c, surface).toFixed(2)}`);
  assert.equal(new Set(CHART_COLORS).size, CHART_COLORS.length, 'colores distintos');
  assert.ok(contrast('#000000', '#ffffff') > 20.9 && contrast('#777777', '#ffffff') < 4.5, 'fórmula del contraste');
});

test('SVG descargable: título, subtítulo, notas y pie de fuente con texto escapado y el gráfico dentro', () => {
  const chart = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 200" width="720" height="200"><rect x="230" y="10" width="40" height="16" fill="#1565c0"/></svg>';
  const out = standalone(chart, 720, 200, {title: 'Ingresos por vía', subtitle: 'Cuenta Pública 2025-2026', notes: ['Un requerimiento puede tener varios valores.'],
    source: 'Fuente: SIGER, Defensoría de la Niñez. Consulta del 26 de noviembre de 2026 <prueba> & otra'});
  assert.ok(out.svg.startsWith('<svg xmlns="http://www.w3.org/2000/svg"'));
  for (const t of ['Ingresos por vía', 'Cuenta Pública 2025-2026', 'Un requerimiento puede tener varios valores.', 'Fuente: SIGER, Defensoría de la Niñez', '&lt;prueba&gt; &amp; otra',
    '<rect x="230" y="10" width="40" height="16" fill="#1565c0"/>']) assert.ok(out.svg.includes(t), t);
  assert.equal((out.svg.match(/<svg/g) ?? []).length, 1, 'un solo SVG autónomo');
  assert.ok(out.height > 200);
  assert.deepEqual(wrap('uno dos tres cuatro', 9), ['uno dos', 'tres', 'cuatro']);
});
