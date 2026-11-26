// Explorador estadistico (0.8.0, gate E12; DISENO 10.3): logica pura de features/stats/statsModel.ts. Formato chileno,
// fechas en hora de Chile (tambien con el reloj del navegador en otra zona), cuerpo de la consulta, direccion, cola y
// cache, tabla, jerarquia de derechos, Figura 1, avisos, "Como leer" y pie de fuente. node --test, sin dependencias.
// La zona del proceso se fija lejos de Chile: una fecha calculada con la zona del navegador falla aqui.
process.env.TZ = 'Pacific/Kiritimati';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Cancelled, CACHE_TTL, canonical, chileDay, chileIso, completeFilters, completeShare, createQueue, decodeRoute, emptyQuery, encodeRoute, figureRows, fieldLabel,
  fmtInt, fmtPct, fmtPoints, fromBody, guidedQuery, howToRead, HIERARCHY_NOTE, indicatorQuery, INDICATORS, MULTIPLE_NOTE, NON_ADDITIVE_NOTE, panoramaPeriod, periodStart,
  queryProblem, readResult, requestBody, rightsTree, scopeLabel, sourceFooter, statsCatalog, tableModel, variation, warningText, WARNING_CODES, cellFigure,
  publicAccount, errorText} from '../src/features/stats/statsModel.ts';
import {CATALOG, DERECHOS, response, SOLICITUD, USER} from './stats-fixture.mjs';

const cat = statsCatalog(CATALOG)!;
const CP = publicAccount(2025);

test('formato chileno: miles con punto, coma decimal y raya sin dato', () => {
  assert.equal(fmtInt(1234), '1.234');
  assert.equal(fmtInt(1234567), '1.234.567');
  assert.equal(fmtInt(0), '0');
  assert.equal(fmtPct(12.5), '12,5 %');
  assert.equal(fmtPct(100), '100 %');
  assert.equal(fmtPct(33.333), '33,3 %');
  assert.equal(fmtPct(null), '—');
  assert.equal(fmtPoints(2.5), '+2,5 puntos porcentuales');
  assert.equal(fmtPoints(-1), '−1 punto porcentual');
});

test('variación: cifra, porcentaje, flecha y texto; con base 0 dice «sin base de comparación», nunca infinito ni 100 %', () => {
  assert.deepEqual(variation(3, 150, 2), {direction: 'up', arrow: '↑', text: 'Aumenta +3 (+150 %)'});
  assert.deepEqual(variation(-1, -50, 2), {direction: 'down', arrow: '↓', text: 'Disminuye −1 (−50 %)'});
  assert.equal(variation(0, 0, 4).text, 'Sin cambio (0 %)');
  const none = variation(5, null, 0);
  assert.equal(none.text, 'Aumenta +5; sin base de comparación');
  assert.ok(!/∞|Infinity|100 %/.test(none.text));
  assert.equal(variation(0, null, 0).text, 'Sin cambio; sin base de comparación');
});

test('«hoy» y «ayer» en hora de Chile aunque el navegador esté en otra zona; 30 de junio, 1 de julio y cambios de hora', () => {
  // 01-07-2026 02:00 UTC = 30-06 22:00 en Chile (invierno, UTC-4).
  assert.equal(chileDay(new Date('2026-07-01T02:00:00Z')), '2026-06-30');
  assert.equal(chileDay(new Date('2026-07-01T04:00:00Z')), '2026-07-01');
  // Fin del horario de verano 2026: 05-04 02:30Z y 03:30Z son ambas 04-04 23:30; 04:00Z ya es 05-04.
  assert.equal(chileDay(new Date('2026-04-05T02:30:00Z')), '2026-04-04');
  assert.equal(chileDay(new Date('2026-04-05T03:30:00Z')), '2026-04-04');
  assert.equal(chileDay(new Date('2026-04-05T04:00:00Z')), '2026-04-05');
  // Inicio del horario de verano 2026 (06-09): 03:59Z es 05-09 23:59 (UTC-4) y 04:00Z ya es 06-09 01:00 (UTC-3).
  assert.equal(chileDay(new Date('2026-09-06T03:59:00Z')), '2026-09-05');
  assert.equal(chileDay(new Date('2026-09-06T04:00:00Z')), '2026-09-06');
  // Hora de Chile -> ISO con el desplazamiento de ese instante.
  assert.equal(chileIso('2026-07-15T10:00'), '2026-07-15T10:00:00-04:00');
  assert.equal(chileIso('2026-12-15T10:00'), '2026-12-15T10:00:00-03:00');
});

test('periodo del Panorama: Cuenta Pública en curso hasta ayer; el 1 de julio, la recién terminada completa y rotulada', () => {
  const jul1 = panoramaPeriod('2026-07-01');
  assert.deepEqual(jul1.period, {type: 'CUENTA_PUBLICA', from: '2025-07-01', to: '2026-06-30'});
  assert.ok(jul1.label.includes('recién terminada'));
  assert.deepEqual(panoramaPeriod('2026-06-30').period, {type: 'PERSONALIZADO', from: '2025-07-01', to: '2026-06-29'});
  assert.deepEqual(panoramaPeriod('2026-07-02').period, {type: 'PERSONALIZADO', from: '2026-07-01', to: '2026-07-01'});
  assert.deepEqual(panoramaPeriod('2026-11-26').period, {type: 'PERSONALIZADO', from: '2026-07-01', to: '2026-11-25'});
  assert.deepEqual(panoramaPeriod('2027-01-01').period, {type: 'PERSONALIZADO', from: '2026-07-01', to: '2026-12-31'});
  assert.ok(panoramaPeriod('2026-11-26').label.includes('1 de julio de 2026 al 25 de noviembre de 2026'));
  assert.equal(periodStart('TRIMESTRE', '2026-05-20'), '2026-04-01');
  assert.equal(periodStart('CUENTA_PUBLICA', '2026-05-20'), '2025-07-01');
  assert.equal(periodStart('SEMESTRE', '2026-08-02'), '2026-07-01');
});

test('cuerpo de la consulta: lista cerrada del contrato, filtros vacíos fuera, fecha de análisis por defecto de la medida', () => {
  const q = {...emptyQuery(CP), measure: 'CASOS', rows: ['TIPO_SOLICITUD'], filters: {CANAL: ['CORREO'], TARDANZA: []}, compare: 'PERIODO_ANTERIOR', percent: 'FILA'};
  const body = requestBody(q, cat);
  assert.deepEqual(Object.keys(body).sort(), ['as_of', 'columns', 'compare', 'cut_id', 'filters', 'include_proposals', 'measure', 'mention_dimension', 'percent', 'period', 'rows', 'time_mode']);
  assert.deepEqual(body.filters, {CANAL: ['CORREO']});
  assert.deepEqual(body.period, {type: 'CUENTA_PUBLICA', from: '2025-07-01', date_basis: 'CREACION_CASO', to: '2026-06-30'});
  assert.deepEqual(body.compare, {type: 'PERIODO_ANTERIOR'});
  assert.equal(body.as_of, null, 'as_of solo con el modo FECHA');
  assert.equal(requestBody({...q, mode: 'FECHA', asOf: '2026-07-15T10:00'}, cat).as_of, '2026-07-15T10:00:00-04:00');
  assert.equal(canonical({b: 1, a: [2, {d: 1, c: 2}]}), canonical({a: [2, {c: 2, d: 1}], b: 1}));
  assert.notEqual(canonical(requestBody(q, cat)), canonical(requestBody({...q, percent: 'TOTAL'}, cat)));
  // Una vista guardada vuelve a la misma consulta.
  assert.deepEqual(requestBody(fromBody(body), cat), body);
});

test('dirección: ida y vuelta solo con códigos; nada que no sea un código o una fecha llega al servidor; sin «q»', () => {
  const q = {...emptyQuery(CP), rows: ['TIPO_SOLICITUD'], columns: ['REGION_REQUIRENTE'], filters: {REGION_REQUIRENTE: ['07', '13'], COMUNA_REQUIRENTE: ['Viña del Mar']},
    percent: 'FILA', compare: 'MISMO_PERIODO_ANIO_ANTERIOR', mode: 'FECHA', asOf: '2026-07-15T10:00', proposals: true};
  const route = encodeRoute('explorar', q);
  assert.ok(route.startsWith('estadisticas?s=explorar'));
  const back = decodeRoute(route);
  assert.equal(back.section, 'explorar');
  assert.deepEqual(back.query, q);
  const keys = [...new URLSearchParams(route.split('?')[1]).keys()];
  assert.ok(!keys.includes('q'), 'el texto de busqueda nunca va en la direccion');
  for (const k of keys) assert.ok(['s', 'm', 'r', 'c', 'f', 'p', 'd', 'h', 'b', 't', 'a', 'k', 'pc', 'pr', 'md'].includes(k), k);
  const altered = decodeRoute('estadisticas?s=explorar&m=INGRESOS&p=MES&d=2026-01-01&r=TIPO_SOLICITUD,<script>&f=%7B%22x-y%22%3A%5B%22a%22%5D%7D&pc=drop%20table');
  assert.deepEqual(altered.query?.rows, ['TIPO_SOLICITUD']);
  assert.deepEqual(altered.query?.filters, {});
  assert.equal(altered.query?.percent, '');
  assert.equal(decodeRoute('estadisticas').section, 'panorama');
  assert.equal(decodeRoute('estadisticas?s=otra').section, 'panorama');
  assert.equal(decodeRoute('estadisticas?s=explorar').query, null);
});

test('fila de consultas: una a la vez, caché de 5 minutos por cuerpo, cancelación de las pendientes y cache por cuenta', async () => {
  let clock = 1_000_000, active = 0, peak = 0, calls = 0;
  const release: (() => void)[] = [];
  const send = (body: Record<string, unknown>) => new Promise<Record<string, unknown>>(resolve => {
    active++; peak = Math.max(peak, active); calls++;
    release.push(() => {active--; resolve({echo: body.measure});});
  });
  const queue = createQueue(send, () => clock);
  const a = queue.query({measure: 'A'}, '5'), b = queue.query({measure: 'B'}, '5'), again = queue.query({measure: 'A'}, '5');
  await Promise.resolve();
  assert.equal(active, 1, 'una sola consulta en curso');
  assert.equal(queue.waiting, 1, 'la misma consulta pendiente no se repite');
  release.shift()!();
  assert.deepEqual((await a).data, {echo: 'A'});
  assert.equal(again, a);
  await new Promise(r => setTimeout(r, 0));
  release.shift()!();
  assert.deepEqual((await b).data, {echo: 'B'});
  assert.equal(peak, 1);
  // Cache: el mismo cuerpo (en otro orden de claves) no se pide de nuevo antes de 5 minutos; despues si.
  const hit = await queue.query({measure: 'A'}, '5');
  assert.equal(hit.cached, true);
  clock += CACHE_TTL - 1;
  assert.equal((await queue.query({measure: 'A'}, '5')).cached, true);
  clock += 1;
  const stale = queue.query({measure: 'A'}, '5');
  await Promise.resolve();
  assert.equal(calls, 3, 'una caché vencida no se sirve');
  release.shift()!();
  assert.equal((await stale).cached, false);
  // Actualizar fuerza la consulta.
  const forced = queue.query({measure: 'A'}, '5', true);
  await Promise.resolve();
  release.shift()!();
  assert.equal((await forced).cached, false);
  // Cancelar: las pendientes fallan con Cancelled; la que esta en curso termina y queda en la cache.
  const running = queue.query({measure: 'C'}, '5'), waiting = queue.query({measure: 'D'}, '5');
  await Promise.resolve();
  queue.cancelPending();
  // Con tope: si la pendiente no se cancela, la prueba falla en un segundo en vez de quedar esperando.
  const limit = new Promise((_, reject) => setTimeout(() => reject(new Error('la consulta pendiente no se canceló')), 1000));
  await assert.rejects(Promise.race([waiting, limit]), e => e instanceof Cancelled);
  release.shift()!();
  await running;
  assert.equal((await queue.query({measure: 'C'}, '5')).cached, true);
  assert.equal(active, 0, 'sin consultas huérfanas');
  // Otra cuenta no ve la cache de la anterior.
  const other = queue.query({measure: 'C'}, '9');
  await Promise.resolve();
  release.shift()!();
  assert.equal((await other).cached, false);
  queue.clear();
});

test('caracterización completa: cada filtro sale de las opciones de /api/catalog y excluye solo «Pendiente»', () => {
  const f = completeFilters(cat, CATALOG)!;
  assert.deepEqual(Object.keys(f).sort(), ['ESTADO_CONTEXTO', 'ESTADO_DERECHO', 'ESTADO_TIPO_REQUIRENTE', 'ESTADO_TIPO_SOLICITUD', 'ESTADO_VIA_INGRESO', 'REGION_REQUIRENTE']);
  for (const code of ['ESTADO_TIPO_SOLICITUD', 'ESTADO_CONTEXTO']) {
    const all = cat.dimensions.find(d => d.code === code)!.options.map(o => o.code);
    assert.deepEqual(f[code], all.filter(c => c !== 'PENDIENTE'));
    assert.equal(all.length - f[code].length, 1);
  }
  assert.deepEqual(f.REGION_REQUIRENTE, ['05', '07', '13', 'SIN_INFORMACION', 'NO_DECLARADO', 'NO_APLICA']);
  const ind = INDICATORS.find(i => i.key === 'completos')!;
  const q = indicatorQuery(ind, cat, CATALOG, CP, 'MISMO_PERIODO_ANIO_ANTERIOR')!;
  assert.deepEqual(q.rows, ['PERIODO_MES']);
  assert.deepEqual(q.filters, f);
  assert.equal(completeShare(30, 40, 10, 20).text, '30 de 40 ingresos (75 %)');
  assert.equal(completeShare(30, 40, 10, 20).points, 25);
  assert.equal(completeShare(0, 0, null, null).text, '0 de 0 ingresos (—)');
  assert.equal(completeShare(5, 10, 0, 0).points, null, 'sin base no hay variacion');
});

test('cada aviso del servidor en una frase, sin su código; uno desconocido tiene una frase general', () => {
  for (const code of WARNING_CODES) {
    const t = warningText({code, dimensions: ['REGION_NNA'], n: 3}, cat);
    assert.ok(t.length > 40 && !t.includes(code) && !t.includes('REGION_NNA'), code);
    assert.ok(t.includes('Región del NNA afectado'), code);
  }
  assert.ok(warningText({code: 'HISTORIA_INCOMPLETA', dimensions: ['TIPO_SOLICITUD'], n: 1234}, cat).includes('1.234'));
  assert.ok(!warningText({code: 'OTRO_AVISO', dimensions: []}, cat).includes('OTRO_AVISO'));
});

test('pie de fuente: institución, fecha y hora de Chile, alcance, fecha de análisis, modo y corte', () => {
  const at = Date.parse('2026-11-26T15:30:00Z');
  const s = sourceFooter({at, scope: 'Toda la institución', basis: 'RECEPCION', mode: 'VIGENTE'});
  assert.ok(s.startsWith('Fuente: SIGER, Defensoría de la Niñez. Consulta del 26 de noviembre de 2026'));
  assert.ok(s.includes('12:30') && s.includes('Toda la institución') && s.includes('fecha de recepción') && s.includes('valores vigentes'));
  assert.ok(sourceFooter({at, scope: 'x', basis: 'REGISTRO', mode: 'FECHA', cut: 'CORTE-2026-001'}).includes('corte CORTE-2026-001'));
  assert.equal(scopeLabel(USER, CATALOG), 'Toda la institución');
  assert.equal(scopeLabel({grants: [{role: 'REPORTES', scope: 6}]}, CATALOG), 'Sede O’Higgins');
});

const CROSS = readResult(response({
  rows: ['TIPO_SOLICITUD'], columns: ['REGION_NNA'],
  values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'CONSULTA_ORIENTACION', label: 'Consulta u orientación'}],
    REGION_NNA: [{key: '05', label: 'Valparaíso'}, {key: '07', label: 'Maule'}]},
  cells: [{row: ['VULNERACION'], column: ['05'], n: 2, unique: 2, percent: 66.67}, {row: ['VULNERACION'], column: ['07'], n: 2, unique: 2, percent: 66.67},
    {row: ['CONSULTA_ORIENTACION'], column: ['07'], n: 1, unique: 1, percent: 100}],
  grand: {n: 4, unique: 4},
  rowTotals: [{key: ['VULNERACION'], n: 3, unique: 3}, {key: ['CONSULTA_ORIENTACION'], n: 1, unique: 1}],
  colTotals: [{key: ['05'], n: 2, unique: 2}, {key: ['07'], n: 3, unique: 3}], percent: 'FILA',
}));

test('tabla: categorías del servidor, 0 en la celda vacía del cruce y márgenes del servidor (nunca la suma de las celdas)', () => {
  const t = tableModel(CROSS, ['TIPO_SOLICITUD'], ['REGION_NNA']);
  assert.deepEqual(t.rows.map(r => r.labels[0]), ['Vulneración de derechos', 'Consulta u orientación']);
  assert.deepEqual(t.columns.map(c => c.labels[0]), ['Valparaíso', 'Maule']);
  assert.equal(cellFigure(t.value(['CONSULTA_ORIENTACION'], ['05']), 'n').text, '0');
  assert.equal(cellFigure(t.value(['CONSULTA_ORIENTACION'], ['05']), 'percent').text, '0 %');
  // Valores multiples: el margen de Vulneracion es 3 (no 2 + 2) y el total 4 (no 5).
  assert.equal(t.rowTotal(['VULNERACION'])!.n, 3);
  assert.equal(t.rowTotal([])!.n, 4);
  assert.equal(cellFigure(t.value(['VULNERACION'], ['05']), 'percent').text, '66,7 %');
  assert.equal(cellFigure({row: [], column: [], n: 0, unique: 0, mentions: null, percent: null, denominator: 0}, 'percent').text, '—');
  // Un eje de periodo muestra todos sus intervalos (el servidor los entrega con cero).
  const months = readResult(response({rows: ['PERIODO_MES'], values: {PERIODO_MES: [{key: '2025-07', label: 'julio de 2025'}, {key: '2025-08', label: 'agosto de 2025'}]},
    cells: [{row: ['2025-07'], column: [], n: 2, unique: 2}], grand: {n: 2, unique: 2}, rowTotals: [{key: ['2025-07'], n: 2, unique: 2}]}));
  assert.deepEqual(tableModel(months, ['PERIODO_MES'], []).rows.map(r => r.key[0]), ['2025-07', '2025-08']);
});

const level = (dim: string, entries: [string, string, number][], grand = 3) => readResult(response({rows: [dim], values: {[dim]: entries.map(([key, label]) => ({key, label}))},
  cells: entries.map(([key, , n]) => ({row: [key], column: [], n, unique: n})), grand: {n: grand, unique: grand}, rowTotals: entries.map(([key, , n]) => ({key: [key], n, unique: n}))}));

test('jerarquía de derechos: una consulta por nivel, anidada con el catálogo; «Sin subcategoría» y los estados como filas propias', () => {
  const eje = DERECHOS.find(i => !i.parent_code)!, right = DERECHOS.find(i => i.parent_code === eje.code)!;
  const subs = DERECHOS.filter(i => i.parent_code === right.code).slice(0, 2);
  const levels = {
    DERECHO_EJE: level('DERECHO_EJE', [[eje.code, eje.label, 2], ['PENDIENTE', 'Pendiente', 1]]),
    DERECHO: level('DERECHO', [[right.code, right.label, 2], ['PENDIENTE', 'Pendiente', 1]]),
    DERECHO_SUBCATEGORIA: level('DERECHO_SUBCATEGORIA', [[subs[0].code, subs[0].label, 1], [subs[1].code, subs[1].label, 1], ['SIN_DESGLOSE', 'Sin subcategoría', 1], ['PENDIENTE', 'Pendiente', 1]]),
  };
  const tree = rightsTree(levels, CATALOG);
  assert.deepEqual(tree.map(r => [r.level, r.label]), [[0, eje.label], [1, right.label], [2, subs[0].label], [2, subs[1].label], [2, 'Sin subcategoría'], [0, 'Pendiente']]);
  assert.equal(tree.find(r => r.key === 'SIN_DESGLOSE')!.special, true);
  // Cada fila toma su cifra de la consulta de su nivel: el derecho cuenta 2 aunque sus subcategorias sumen 2 + 1 sin desglose.
  const t = tableModel(levels.DERECHO, ['DERECHO'], []);
  assert.equal(t.value([right.code], [])!.n, 2);
  assert.ok(HIERARCHY_NOTE.includes('no suman el superior'));
});

test('Figura 1: todas las categorías del catálogo vigente con 0, en su orden, y los estados especiales; suma = total del servidor', () => {
  const r = readResult(response({rows: ['TIPO_SOLICITUD'], values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'PENDIENTE', label: 'Pendiente'}]},
    cells: [{row: ['VULNERACION'], column: [], n: 3, unique: 3}, {row: ['PENDIENTE'], column: [], n: 1, unique: 1}], grand: {n: 4, unique: 4}}));
  const rows = figureRows(cat, CATALOG, 'TIPO_SOLICITUD', r);
  assert.deepEqual(rows.map(x => x.key), [...SOLICITUD.filter(i => i.selectable).map(i => i.code), 'PENDIENTE', 'SIN_INFORMACION', 'NO_DECLARADO', 'NO_APLICA']);
  assert.deepEqual(rows.map(x => x.n), [3, 0, 0, 0, 0, 1, 0, 0, 0]);
  assert.equal(rows.reduce((a, x) => a + x.n, 0), r.grand.n);
  // Sin respuesta, la misma forma (todo en 0).
  assert.equal(figureRows(cat, CATALOG, 'TIPO_SOLICITUD', null).length, rows.length);
  // Adscripcion: sedes regionales del catalogo (no las proyectadas), Oficina Nacional y Pendiente de asignacion al final.
  const ads = figureRows(cat, CATALOG, 'ADSCRIPCION_INICIAL', readResult(response({rows: ['ADSCRIPCION_INICIAL'],
    values: {ADSCRIPCION_INICIAL: [{key: 'REG-99', label: 'Sede cerrada antigua'}]}, cells: [{row: ['REG-99'], column: [], n: 1, unique: 1}], grand: {n: 1, unique: 1}})));
  assert.deepEqual(ads.map(x => x.label).slice(0, 3), ['Sede O’Higgins', 'Oficina Nacional', 'Pendiente de asignación']);
  assert.ok(ads.some(x => x.key === 'REG-99' && x.n === 1), 'una categoria del servidor fuera del catalogo se agrega con su rotulo');
  // La via: solo items seleccionables (las agrupaciones padre no son categorias).
  assert.ok(figureRows(cat, CATALOG, 'VIA_INGRESO', null).every(x => !x.key.startsWith('GRUPO_')));
});

test('«Cómo leer esta cifra»: medida, periodo, modo, alcance, valores múltiples, región de los NNA no aditiva, cobertura y avisos', () => {
  const q = {...emptyQuery(CP), rows: ['TIPO_SOLICITUD'], columns: ['REGION_NNA'], percent: 'FILA'};
  const r = readResult({...response({rows: q.rows, columns: q.columns, grand: {n: 4, unique: 4}, warnings: [{code: 'PORCENTAJES_MULTIVALOR', dimensions: ['REGION_NNA']}],
    coverage: {EDAD_NNA: {with_data: 2, not_applicable: 0, total: 4, percent: 50}}, late: {n: 2, by_delay_days: []}})});
  const lines = howToRead({cat, q, r, scope: 'Toda la institución', hierarchy: true});
  const all = lines.join(' ');
  for (const t of ['Ingresos:', 'Cuenta Pública 2025-2026', 'fecha de recepción', 'Valores vigentes', 'Alcance: Toda la institución', '% por fila', MULTIPLE_NOTE, NON_ADDITIVE_NOTE,
    HIERARCHY_NOTE, 'Edad del NNA afectado: con dato en 2 de 4', 'Registros tardíos: 2', 'pueden sumar más de 100 %']) assert.ok(all.includes(t), t);
  assert.ok(!/\b[A-Z]{2,}_[A-Z0-9_]+\b/.test(all), 'sin codigos: ' + all.match(/\b[A-Z]{2,}_[A-Z0-9_]+\b/)?.[0]);
  assert.ok(fieldLabel(cat, 'REGION_NNA').includes('no aditiva'));
  assert.ok(!fieldLabel(cat, 'REGION_REQUIRENTE').includes('no aditiva'));
});

test('consulta incompleta explicada en lenguaje llano; preguntas guiadas', () => {
  assert.match(queryProblem(cat, {...emptyQuery(CP), measure: 'REQUERIMIENTOS_CON_VALOR', rows: ['TIPO_SOLICITUD']}), /valores múltiples/);
  assert.match(queryProblem(cat, {...emptyQuery(CP), rows: ['TIPO_ACTUACION']}), /no se puede usar/);
  assert.equal(queryProblem(cat, {...emptyQuery(CP), measure: 'ACTUACIONES', rows: ['TIPO_ACTUACION']}), '');
  assert.match(queryProblem(cat, {...emptyQuery(CP), mode: 'FECHA'}), /fecha y hora/);
  assert.match(queryProblem(cat, {...emptyQuery({type: 'PERSONALIZADO', from: '2026-02-01', to: '2026-01-01'})}), /periodo/);
  assert.deepEqual(guidedQuery('via', CP)?.rows, ['VIA_INGRESO']);
  assert.equal(guidedQuery('comparar', CP)?.compare, 'PERIODO_ANTERIOR');
  assert.equal(guidedQuery('figura', CP), null, 'la Figura 1 abre Informes');
});

test('mensajes de 429 y 503 en lenguaje llano: el del servidor y, si no trae, uno propio (nunca el genérico de ingreso)', () => {
  const busy = 'Ya hay consultas estadísticas en curso. Espera a que terminen y vuelve a intentarlo.';
  assert.equal(errorText({status: 429, message: busy}), busy);
  assert.match(errorText({status: 429, message: 'Demasiados intentos. Espera antes de volver a ingresar.'}), /otra consulta estadística en curso/);
  assert.match(errorText({status: 503, message: 'No se pudo confirmar la operacion (503). Reintenta sin cambiar los datos.'}), /tardó demasiado/);
  assert.equal(errorText({status: 503, message: 'El motor estadístico está deshabilitado.'}), 'El motor estadístico está deshabilitado.');
  assert.equal(errorText({status: 409, message: 'La consulta supera el máximo de celdas.'}), 'La consulta supera el máximo de celdas.');
});
