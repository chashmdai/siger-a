// Correcciones de la revision visual de F02 (0.8.1, gate F02b; docs/PLAN_0_8_1.md, seccion 6). (1) Informes: tabla y
// grafico lado a lado solo si el grafico cabe a su tamano natural (opcion A del GO); si no, bajo la tabla hasta 840 px, y
// ningun texto bajo 10 px efectivos. (2) Panorama: la linea mensual llena el alto de la tarjeta, en el color de acento de
// la tarjeta con contraste >= 3:1 sobre su fondo real. (4) Comentario de tokens.css y nota de D56 con verde y ambar.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {contrast} from '../src/components/charts/chartModel.ts';
import {BREAKDOWN_GAP, BREAKDOWN_SIDE_MIN, BREAKDOWN_STACKED_MAX, BREAKDOWN_TABLE_MIN, breakdownLayout, CHART_FRAME, CHART_MIN_FONT, CHART_MIN_TEXT, CHART_VIEW_WIDTH,
  effectiveText, TONE_NOTE} from '../src/features/stats/statsModel.ts';

const read = (path: string) => readFileSync(fileURLToPath(new URL('../src/' + path, import.meta.url)), 'utf8');
const noComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '');

// Ancho del desglose de Informes a 1440 px (medido en el humo de F02b: 1.030 px; el panel deja unos 1.030 a 1.045).
const AT_1440 = [1030, 1043];

test('(1) Informes: lado a lado solo si el gráfico cabe a su tamaño natural; nunca con menos de 560 px ni con texto bajo 10 px', () => {
  assert.equal(BREAKDOWN_SIDE_MIN, BREAKDOWN_TABLE_MIN + BREAKDOWN_GAP + CHART_VIEW_WIDTH + CHART_FRAME);
  for (let width = 320; width <= 2400; width++) {
    const l = breakdownLayout(width);
    if (l.side) {
      assert.ok(l.chart >= 560, `${width}: lado a lado con ${l.chart} px de gráfico`);
      assert.equal(l.chart, CHART_VIEW_WIDTH, `${width}: lado a lado el gráfico se dibuja entero, a su tamaño natural`);
      assert.ok(l.text >= CHART_MIN_TEXT, `${width}: texto de ${l.text.toFixed(2)} px lado a lado`);
    }
    // Bajo la tabla, desde que el desglose deja el ancho de 840 px, el texto mas chico queda entre 11 y 13 px.
    if (!l.side && width >= BREAKDOWN_STACKED_MAX + CHART_FRAME) {
      assert.equal(l.chart, BREAKDOWN_STACKED_MAX, `${width}: bajo la tabla, hasta 840 px`);
      assert.ok(l.text >= 11 && l.text <= 13, `${width}: texto de ${l.text.toFixed(2)} px bajo la tabla`);
    }
  }
  for (const width of AT_1440) {
    const l = breakdownLayout(width);
    assert.equal(l.side, false, `a 1440 px (${width}) el gráfico va bajo la tabla`);
    assert.ok(l.text >= CHART_MIN_TEXT, `a 1440 px el texto se ve a ${l.text.toFixed(2)} px`);
  }
  assert.ok(breakdownLayout(BREAKDOWN_SIDE_MIN).side && !breakdownLayout(BREAKDOWN_SIDE_MIN - 1).side, 'el umbral es exacto');
  assert.equal(effectiveText(CHART_MIN_FONT, CHART_VIEW_WIDTH), CHART_MIN_FONT, 'a tamaño natural el texto se ve a su tamaño');
  assert.ok(effectiveText(CHART_MIN_FONT, 560) < CHART_MIN_TEXT, 'con el umbral de 560 px el texto quedaría bajo 10 px (por eso la opción A)');
});

test('(1) screens.css usa los mismos valores: @container del desglose, columnas, tope de 840 px y marco del lienzo', () => {
  const css = noComments(read('styles/screens.css'));
  assert.match(css, /\.stat-breakdown-box \{ container: desglose \/ inline-size; \}/);
  assert.ok(css.includes(`@container desglose (min-width: ${BREAKDOWN_SIDE_MIN}px)`), 'umbral del @container = BREAKDOWN_SIDE_MIN');
  assert.ok(css.includes(`.stat-breakdown { grid-template-columns: minmax(${BREAKDOWN_TABLE_MIN}px, 1fr) ${CHART_VIEW_WIDTH + CHART_FRAME}px; }`), 'columnas lado a lado');
  assert.ok(css.includes(`.stat-breakdown { grid-template-columns: minmax(0, ${BREAKDOWN_STACKED_MAX + CHART_FRAME}px); }`), 'columna apilada');
  assert.ok(css.includes(`.stat-breakdown-chart .stat-chart-canvas svg { width: 100%; max-width: ${BREAKDOWN_STACKED_MAX}px; }`), 'tope del gráfico bajo la tabla');
  assert.ok(!/@media[^{]*\{\s*\.stat-breakdown \{/.test(css), 'la disposición ya no la decide el ancho de la ventana');
  assert.ok(/\.stat-breakdown \{[^}]*gap: 1rem/.test(css) && BREAKDOWN_GAP === 16, 'separación de la grilla');
  // Marco del lienzo: relleno horizontal de .5rem por lado (la ultima regla) y borde de 1 px.
  const paddings = [...css.matchAll(/\.stat-chart-canvas \{[^}]*padding: ([^;]+);/g)].map(m => m[1].trim());
  assert.equal(paddings[paddings.length - 1].split(/\s+/)[1] ?? paddings[paddings.length - 1], '.5rem');
  assert.match(css, /\.stat-chart-canvas \{[^}]*border: 1px solid/);
  assert.equal(CHART_FRAME, 2 * 8 + 2 * 1);
});

test('(2) Panorama: la línea mensual ocupa el alto que queda en la tarjeta, con la ayuda y el pie abajo', () => {
  const css = noComments(read('styles/screens.css'));
  assert.match(css, /\.stat-card-trend\.spark \{ position: relative; flex: 1 1 auto; min-height: 56px; \}/, 'la zona de la línea crece');
  assert.match(css, /\.stat-card-trend\.spark \.stat-spark \{ position: absolute; inset: 0; width: 100%; height: 100%;/, 'el SVG llena la zona');
  assert.match(css, /\.stat-card \{[^}]*display: flex; flex-direction: column;/, 'la tarjeta es una columna flexible');
  assert.match(css, /\.stat-card-help \{ margin-top: auto;/, 'ayuda y pie abajo');
  assert.match(css, /\.stat-card-skeleton \{ grid-template-rows: auto auto minmax\(56px, 1fr\) auto;/, 'el esqueleto también llena la tarjeta');
});

test('(2) cada acento de tarjeta tiene su color de línea, con contraste >= 3:1 sobre la tarjeta y el indicador principal', () => {
  const tokens = read('styles/tokens.css');
  const css = noComments(read('styles/screens.css'));
  const value = (name: string) => {
    const m = new RegExp(name.replace(/-/g, '\\-') + ':\\s*(#[0-9a-fA-F]{6})').exec(tokens);
    assert.ok(m, 'token ' + name);
    return m![1];
  };
  const accents = [...read('features/stats/Panorama.tsx').matchAll(/accent: '(\w+)'/g)].map(m => m[1]);
  assert.deepEqual([...new Set(accents)].sort(), ['act', 'cas', 'com', 'ok', 'req', 'warn']);
  for (const accent of new Set(accents)) {
    const token = '--stat-spark-' + accent;
    for (const back of ['--surface', '--stat-hero-bg']) {
      const c = contrast(value(token), value(back));
      assert.ok(c >= 3, `${token} sobre ${back} = ${c.toFixed(2)}:1`);
    }
    const rule = accent === 'com' ? /\.stat-card-trend\.spark \.stat-spark \{[^}]*color: var\(--stat-spark-com\)/
      : new RegExp(`\\.stat-card\\[data-accent=${accent}\\] \\.stat-spark \\{ color: var\\(--stat-spark-${accent}\\); \\}`);
    assert.match(css, rule, 'regla de color de ' + accent);
  }
});

test('(4) el comentario de tokens.css cita la prueba que existe y la nota de D56 nombra verde y ámbar en los dos indicadores', () => {
  const tokens = read('styles/tokens.css');
  assert.ok(!tokens.includes('tokensF01'), 'sin la cita a tests/tokensF01.test.ts');
  assert.match(tokens, /Contraste probado en tests\/statsF01\.test\.ts/);
  assert.match(TONE_NOTE, /caracterización completa subir es favorable \(verde\) y bajar, desfavorable \(ámbar\)/);
  assert.match(TONE_NOTE, /registros tardíos bajar es favorable \(verde\) y subir, desfavorable \(ámbar\)/);
  assert.match(TONE_NOTE, /el color es neutro/);
});
