// Rediseno visual del explorador (0.8.1, gate F01; seccion 3 de docs/PLAN_0_8_1.md): logica pura nueva y guardias de
// estilo. Destino de las tarjetas del Panorama (D53), sentido de la variacion (D56), desplazamiento solo horizontal de las
// pestanas (ajuste 5 del GO), barra de datos, leyenda de Calidad, contraste de cada token nuevo (D55) y la regla que
// evita el defecto de 0.8.0: nada en la tarjeta-enlace se transforma al presionarla salvo la tarjeta completa.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {contrast} from '../src/components/charts/chartModel.ts';
import {cardHref, databarWidth, emptyQuery, encodeRoute, indicatorQuery, INDICATORS, panoramaPeriod, statsCatalog, tabScroll, TONE_NOTE, variationTone} from '../src/features/stats/statsModel.ts';
import {legendStates} from '../src/features/stats/qualityModel.ts';
import {CATALOG} from './stats-fixture.mjs';

const css = (name: string) => readFileSync(fileURLToPath(new URL('../src/styles/' + name, import.meta.url)), 'utf8');

test('D53: el destino de cada tarjeta es la direccion de su consulta en Explorar', () => {
  const cat = statsCatalog(CATALOG)!;
  const period = panoramaPeriod('2026-11-26').period;
  for (const ind of INDICATORS) {
    const q = indicatorQuery(ind, cat, CATALOG, period, 'MISMO_PERIODO_ANIO_ANTERIOR');
    if (!q) continue;
    assert.equal(cardHref(q), '#' + encodeRoute('explorar', q), ind.key);
    assert.match(cardHref(q), /^#estadisticas\?s=explorar&m=/);
  }
  assert.equal(cardHref({...emptyQuery(period), rows: ['PERIODO_MES']}), '#estadisticas?s=explorar&m=INGRESOS&r=PERIODO_MES&p=PERSONALIZADO&d=2026-07-01&h=2026-11-25');
});

test('D56: el color solo juzga en caracterización completa (subir es favorable) y registros tardíos (bajar es favorable)', () => {
  const expected: Record<string, Record<string, string>> = {
    ingresos: {up: 'neutro', down: 'neutro'}, req: {up: 'neutro', down: 'neutro'}, casos: {up: 'neutro', down: 'neutro'}, actuaciones: {up: 'neutro', down: 'neutro'},
    completos: {up: 'favorable', down: 'desfavorable'}, tardios: {up: 'desfavorable', down: 'favorable'},
  };
  assert.deepEqual(INDICATORS.map(i => i.key).sort(), Object.keys(expected).sort(), 'los seis indicadores');
  for (const [key, dirs] of Object.entries(expected)) {
    for (const [dir, tone] of Object.entries(dirs)) assert.equal(variationTone(key, dir, true), tone, `${key} ${dir}`);
    assert.equal(variationTone(key, 'same', true), 'neutro', key + ' sin cambio');
    assert.equal(variationTone(key, 'up', false), 'neutro', key + ' sin base');
    assert.equal(variationTone(key, 'down', false), 'neutro', key + ' sin base');
  }
  assert.match(TONE_NOTE, /caracterización completa subir es favorable/);
  assert.match(TONE_NOTE, /registros tardíos bajar es favorable/);
  assert.match(TONE_NOTE, /el color es neutro/);
});

test('ajuste 5 del GO: la pestaña activa se lleva a la vista solo con el desplazamiento horizontal del contenedor', () => {
  const view = {left: 16, right: 374, scroll: 0};
  assert.equal(tabScroll(view, {left: 20, right: 140}), null, 'ya visible: no se mueve');
  assert.equal(tabScroll(view, {left: 400, right: 520}), 0 + (520 - 374) + 12, 'a la derecha: avanza lo justo y un margen');
  assert.equal(tabScroll({left: 16, right: 374, scroll: 300}, {left: -100, right: 10}), 300 - 116 - 12, 'a la izquierda: retrocede');
  assert.equal(tabScroll({left: 16, right: 374, scroll: 20}, {left: -100, right: 10}), 0, 'nunca negativo');
  const source = readFileSync(fileURLToPath(new URL('../src/features/stats/Stats.tsx', import.meta.url)), 'utf8');
  assert.ok(!/scrollIntoView\(/.test(source), 'sin scrollIntoView, que también desplaza la página en vertical');
  assert.match(source, /el\.scrollTo\(\{left, behavior: reduced \? 'auto' : 'smooth'\}\)/, 'solo scrollLeft, sin animación con movimiento reducido');
});

test('barra de datos de una celda de porcentaje: entre 0 % y 100 %', () => {
  assert.equal(databarWidth(30.4), '30.4%');
  assert.equal(databarWidth(0), '0.0%');
  assert.equal(databarWidth(140), '100.0%');
  assert.equal(databarWidth(-3), '0.0%');
  assert.equal(databarWidth(null), '0.0%');
});

test('leyenda de Calidad: los estados de las columnas en el orden de las barras', () => {
  const t = {columns: [{key: ['PENDIENTE'], labels: ['Pendiente']}, {key: ['INFORMADO'], labels: ['Informado']}, {key: ['SIN_INFORMACION'], labels: ['Sin información']}]};
  assert.deepEqual(legendStates(t as never).map(s => s.label), ['Informado', 'Sin información', 'Pendiente']);
});

// D55: cada token nuevo se prueba con el fondo o el texto con que se usa. Texto >= 4,5:1; marcas e iconos >= 3:1.
const NEW_TOKENS = ['--stat-segment-bg', '--stat-row-alt', '--stat-row-hover', '--stat-databar', '--stat-track', '--stat-ring', '--stat-accent-act-bg', '--stat-accent-act-ink',
  '--stat-hero-bg', '--stat-hero-line'];
const PAIRS: [fore: string, back: string, min: number, uso: string][] = [
  ['--ink-600', '--stat-segment-bg', 4.5, 'texto de las pestañas sobre la pista'],
  ['--ink-500', '--stat-segment-bg', 3, 'icono de las pestañas'],
  ['--ink-800', '--stat-row-alt', 4.5, 'texto en filas alternas'],
  ['--ink-800', '--stat-row-hover', 4.5, 'texto en la fila realzada'],
  ['--ink-900', '--stat-databar', 4.5, 'cifra sobre la barra de datos'],
  ['--stat-ring', '--surface', 3, 'progreso del anillo sobre la tarjeta'],
  ['--stat-ring', '--stat-track', 3, 'progreso del anillo sobre su pista'],
  ['--stat-accent-act-ink', '--stat-accent-act-bg', 4.5, 'icono de actuaciones'],
  ['--ink-900', '--stat-hero-bg', 4.5, 'cifra del indicador principal'],
  ['--primary-ink', '--stat-hero-bg', 4.5, '«Abrir en Explorar» del indicador principal'],
  ['--ink-600', '--stat-hero-bg', 4.5, 'ayuda del indicador principal'],
  ['--stat-hero-line', '--surface', 1.3, 'borde del indicador principal (separación visible del fondo)'],
  // Tonos de la variacion (D56) y fichas de icono con tokens existentes.
  ['--ok-ink', '--ok-bg', 4.5, 'variación favorable'], ['--warn-ink', '--warn-bg', 4.5, 'variación desfavorable'], ['--ink-700', '--surface-3', 4.5, 'variación neutra'],
  ['--kind-com-ink', '--kind-com-bg', 3, 'icono de ingresos'], ['--kind-req-ink', '--kind-req-bg', 3, 'icono de REQ'], ['--kind-cas-ink', '--kind-cas-bg', 3, 'icono de Casos'],
];
test('D55: contraste de cada token nuevo con el texto o el fondo con que se usa', () => {
  const tokens = css('tokens.css');
  const value = (name: string) => {
    const m = new RegExp(name.replace(/[-]/g, '\\-') + ':\\s*(#[0-9a-fA-F]{6})').exec(tokens);
    assert.ok(m, 'token ' + name);
    return m![1];
  };
  for (const token of NEW_TOKENS) assert.ok(PAIRS.some(([f, b]) => f === token || b === token), 'el token nuevo ' + token + ' tiene su prueba de contraste');
  for (const [fore, back, min, uso] of PAIRS) {
    const c = contrast(value(fore), value(back));
    assert.ok(c >= min, `${uso}: ${fore} sobre ${back} = ${c.toFixed(2)}:1 (mínimo ${min}:1)`);
  }
});

test('guardia del defecto de 0.8.0: ninguna regla transforma un descendiente de la tarjeta-enlace al presionarla y no queda la capa ::after', () => {
  const all = ['base.css', 'components.css', 'layout.css', 'screens.css'].map(css).join('\n').replace(/\/\*[\s\S]*?\*\//g, '');
  assert.ok(!/\.stat-card-link::after/.test(all), 'sin la capa ::after de 0.8.0');
  for (const [, selectors, body] of all.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    if (!/transform\s*:/.test(body) || /transform\s*:\s*none/.test(body)) continue;
    for (const sel of selectors.split(',').map(s => s.trim()).filter(s => s.includes(':active'))) {
      if (!/stat-card/.test(sel)) continue;
      const parts = sel.split(/\s+|>|\+|~/).filter(Boolean);
      const last = parts[parts.length - 1];
      assert.ok(last.includes(':active') && /stat-card-link/.test(last), `«${sel}» transforma al presionar algo distinto de la tarjeta completa`);
    }
  }
  // La regla de los botones sigue existiendo: por eso la tarjeta no contiene botones (lo prueba statsF01-render).
  assert.match(all, /button:active:not\(:disabled\)[^{]*\{[^}]*transform/);
});
