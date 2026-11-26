// Explorador, gate E13 (DISENO 10.3 y 10.4; GO del 2026-11-26 y su precision): ajustes de presentacion 1, 5 y 6, consulta y
// comparacion con cortes en la direccion y en el cuerpo, la regla del ZIP comparada con StatSource.java, el asistente de
// corte, las series historicas (correspondencias nunca marcadas, la suma solo si el servidor la admite) y el panel de
// Calidad con su enlace. Las cifras de las matrices estan escritas a mano. node --test.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {changedCells, changedSummary, beforeText, compactDelta, decodeRoute, emptyQuery, encodeRoute, figureRows, figureTable, fromBody, NO_ZIP_MEASURES, NO_ZIP_TEXT, readResult,
  requestBody, sameMinute, statsCatalog, tableModel, zipProblem} from '../src/features/stats/statsModel.ts';
import {CUT_EXPLAIN, CUT_WARNING, cutBody, cutDraft, firstProblem, managedScopes, onCut, stepProblem, verifyText} from '../src/features/stats/cutsModel.ts';
import {draftProblem, emptySeries, periodKey, seriesBody, seriesCategories, seriesChart, sigerQuery, suggestions, sumRows, toBase64} from '../src/features/stats/seriesModel.ts';
import {boardFor, byMonth, byScope, byVariable, NO_BOARD_TEXT, partsText, pendingLink, share, stateParts} from '../src/features/stats/qualityModel.ts';
import {CATALOG, response, USER} from './stats-fixture.mjs';

const cat = statsCatalog(CATALOG)!;
const PERIOD = {type: 'CUENTA_PUBLICA', from: '2025-07-01', to: '2026-06-30'};

test('ajuste 1: los estados especiales con 0 van a la nota «Sin registros en»; con más de 0 son fila; los ítems del catálogo con 0 conservan su fila', () => {
  // Matriz a mano: vulneracion 3, consulta 0 (item), promocion 0 (item), PENDIENTE 2 (fila), SIN_INFORMACION 0, NO_DECLARADO 0, NO_APLICA 0 (nota).
  const r = readResult(response({rows: ['TIPO_SOLICITUD'], values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'PENDIENTE', label: 'Pendiente'}]},
    cells: [{row: ['VULNERACION'], column: [], n: 3}, {row: ['PENDIENTE'], column: [], n: 2}], grand: {n: 5, unique: 5}}));
  const all = figureRows(cat, CATALOG, 'TIPO_SOLICITUD', r);
  const f = figureTable(all);
  assert.ok(f.rows.some(x => x.key === 'PENDIENTE' && x.n === 2), 'pendiente con 2: fila');
  assert.ok(f.rows.filter(x => !['PENDIENTE'].includes(x.key)).every(x => !['SIN_INFORMACION', 'NO_DECLARADO', 'NO_APLICA'].includes(x.key)));
  assert.equal(f.rows.length, all.length - 3);
  assert.ok(f.rows.some(x => x.n === 0), 'ítems del catálogo con 0 conservan su fila');
  assert.equal(f.note, 'Sin registros en: Sin información, No declarado, No aplica.');
  assert.equal(figureTable(all.map(x => ({...x, n: 1}))).note, '', 'sin ceros no hay nota');
  // Adscripcion: "Pendiente de asignacion" con 0 tambien va a la nota.
  const ads = figureTable(figureRows(cat, CATALOG, 'ADSCRIPCION_INICIAL', null));
  assert.match(ads.note, /Pendiente de asignación/);
  assert.ok(ads.rows.some(x => x.key === 'OFICINA_NACIONAL'));
});

test('ajuste 5: variación compacta «+1», «−2 (−15,4 %)», «=»; sin base, solo la cifra y la nota al pie', () => {
  assert.deepEqual(compactDelta(1, null, 0), {text: '+1', label: 'Aumenta +1', noBase: true});
  assert.equal(compactDelta(-2, -15.38, 13).text, '−2 (−15,4 %)');
  assert.equal(compactDelta(-2, -15.38, 13).noBase, false);
  assert.equal(compactDelta(0, 0, 4).text, '=');
  assert.equal(compactDelta(3, 150, 2).text, '+3 (+150 %)');
});

test('ajuste 6: «Datos de las HH:MM» una sola vez si todas las consultas son del mismo minuto de Chile', () => {
  const t = Date.UTC(2026, 10, 26, 15, 30, 5);
  assert.equal(sameMinute([t, t + 20000, t + 40000]), t + 40000);
  assert.equal(sameMinute([t, t + 60000]), null, 'otro minuto: cada panel la suya');
  assert.equal(sameMinute([t, 0]), null, 'una consulta aún sin responder');
  assert.equal(sameMinute([]), null);
});

test('corte en la consulta: cut_id y compare CORTE en el cuerpo y en la dirección, solo con identificadores', () => {
  const q = {...emptyQuery(PERIOD), rows: ['TIPO_SOLICITUD'], cut: 12};
  const body = requestBody({...q, mode: 'FECHA', asOf: '2026-01-01T10:00'});
  assert.equal(body.cut_id, 12);
  assert.equal(body.time_mode, 'VIGENTE', 'un corte solo admite valores vigentes');
  assert.equal(body.as_of, null);
  const cmp = requestBody({...emptyQuery(PERIOD), compare: 'CORTE', compareCut: 7});
  assert.deepEqual(cmp.compare, {type: 'CORTE', cut_id: 7});
  assert.equal(requestBody({...emptyQuery(PERIOD), compare: 'CORTE'}).compare, null, 'sin corte elegido no se compara');
  const route = encodeRoute('explorar', {...q, compare: 'CORTE', compareCut: 7});
  assert.match(route, /ct=12/);
  assert.match(route, /kc=7/);
  const back = decodeRoute(route).query!;
  assert.equal(back.cut, 12);
  assert.equal(back.compareCut, 7);
  assert.equal(decodeRoute('estadisticas?s=explorar&m=INGRESOS&p=MES&d=2026-01-01&ct=1%3B2').query!.cut, undefined, 'un identificador alterado se descarta');
  assert.equal(fromBody(body).cut, 12);
  assert.equal(fromBody(cmp).compareCut, 7);
  assert.equal(decodeRoute('estadisticas?s=cortes').section, 'cortes');
  assert.equal(decodeRoute('estadisticas?s=calidad').section, 'calidad');
});

test('ZIP: las medidas sin paquete son las de StatSource.java con unidad propia o fuera de los requerimientos computables', () => {
  const java = readFileSync(fileURLToPath(new URL('../../backend/src/main/java/cl/nexbyte/sigera/StatSource.java', import.meta.url)), 'utf8');
  const measures = [...java.matchAll(/new Measure\("([A-Z_]+)","[^"]*",Unit\.[A-Z]+,Population\.([A-Z]+),(true|false),(true|false)\)/g)];
  assert.ok(measures.length >= 12);
  const expected = measures.filter(m => m[3] === 'true' || !['COMPUTABLE', 'REQUERIMIENTO'].includes(m[2])).map(m => m[1]);
  assert.deepEqual([...NO_ZIP_MEASURES].sort(), expected.sort());
  assert.equal(zipProblem({...emptyQuery(PERIOD), measure: 'CASOS'}), NO_ZIP_TEXT);
  assert.equal(zipProblem({...emptyQuery(PERIOD), measure: 'INGRESOS'}), '');
  assert.equal(zipProblem({...emptyQuery(PERIOD), measure: 'MENCIONES'}), '');
});

test('comparación con un corte: matriz a mano de celdas cambiadas, también con columnas, y su texto', () => {
  // Tipo de solicitud x canal. Hoy: VUL/CORREO 5 (antes 3), VUL/TEL 1 (igual), CON/CORREO 2 (antes 3), CON/TEL 0 (igual).
  const r = readResult(response({rows: ['TIPO_SOLICITUD'], columns: ['CANAL'],
    values: {TIPO_SOLICITUD: [{key: 'VULNERACION', label: 'Vulneración de derechos'}, {key: 'CONSULTA_ORIENTACION', label: 'Consulta u orientación'}], CANAL: [{key: 'CORREO', label: 'Correo'}, {key: 'TELEFONO', label: 'Teléfono'}]},
    cells: [{row: ['VULNERACION'], column: ['CORREO'], n: 5}, {row: ['VULNERACION'], column: ['TELEFONO'], n: 1}, {row: ['CONSULTA_ORIENTACION'], column: ['CORREO'], n: 2}],
    grand: {n: 8, unique: 8}, rowTotals: [{key: ['VULNERACION'], n: 6}, {key: ['CONSULTA_ORIENTACION'], n: 2}], colTotals: [{key: ['CORREO'], n: 7}, {key: ['TELEFONO'], n: 1}],
    comparison: {cut_id: 3, cut_code: 'CORTE-2026-001', cells: [
      {row: ['VULNERACION'], column: ['CORREO'], base_n: 3, delta_n: 2, delta_percent: 66.7}, {row: ['VULNERACION'], column: ['TELEFONO'], base_n: 1, delta_n: 0, delta_percent: 0},
      {row: ['CONSULTA_ORIENTACION'], column: ['CORREO'], base_n: 3, delta_n: -1, delta_percent: -33.3}, {row: ['CONSULTA_ORIENTACION'], column: ['TELEFONO'], base_n: 0, delta_n: 0, delta_percent: null}],
      totals: {grand: {base_n: 7, delta_n: 1, delta_percent: 14.3}, rows: [], columns: []}}}));
  const t = tableModel(r, ['TIPO_SOLICITUD'], ['CANAL']);
  assert.equal(changedCells(t), 2);
  assert.equal(changedSummary(2), '2 celdas cambiaron respecto del corte.');
  assert.equal(changedSummary(1), '1 celda cambió respecto del corte.');
  assert.equal(beforeText(t.delta(['VULNERACION'], ['CORREO'])!), 'antes 3, +2');
  assert.equal(beforeText(t.delta(['CONSULTA_ORIENTACION'], ['CORREO'])!), 'antes 3, −1');
  assert.equal(r.comparison!.cutCode, 'CORTE-2026-001');
});

test('asistente de corte: alcances de Gestión estadística, prellenado desde la consulta, pasos, cuerpo cerrado y avisos textuales', () => {
  const user = {...USER, grants: [{role: 'REPORTES', scope: 6}, {role: 'GESTION_ESTADISTICA', scope: 6}, {role: 'GESTION_ESTADISTICA', scope: null}]};
  const scopes = managedScopes(user, CATALOG);
  assert.deepEqual(scopes, [{id: null, label: 'Toda la institución'}, {id: 6, label: 'Sede O’Higgins'}]);
  assert.deepEqual(managedScopes(USER, CATALOG), [], 'sin Gestión estadística no hay alcances');
  const q = {...emptyQuery({...PERIOD, basis: 'REGISTRO'}), rows: ['TIPO_SOLICITUD'], filters: {CANAL: ['CORREO']}};
  const d = cutDraft(q, 'Ingresos por Tipo de solicitud', scopes, {type: 'MES', from: '2026-01-01'});
  assert.deepEqual(d.period, PERIOD, 'el periodo de la consulta, sin la fecha de análisis');
  assert.equal(d.title, 'Corte: Ingresos por Tipo de solicitud');
  assert.equal(d.scope, null);
  assert.match(stepProblem(d, 2), /fundamento/);
  assert.equal(firstProblem(d), 2);
  const ready = {...d, reason: 'Cuenta Pública'};
  assert.equal(firstProblem(ready), -1);
  assert.deepEqual(cutBody(ready), {title: 'Corte: Ingresos por Tipo de solicitud', period: {type: 'CUENTA_PUBLICA', from: '2025-07-01'}, reason: 'Cuenta Pública'}, 'sin filtros, sin date_basis ni alcance institucional');
  assert.deepEqual(cutBody({...ready, scope: 6, period: {type: 'PERSONALIZADO', from: '2026-01-01', to: '2026-01-31'}}).period, {type: 'PERSONALIZADO', from: '2026-01-01', to: '2026-01-31'});
  assert.equal(cutBody({...ready, scope: 6}).scope_id, 6);
  assert.match(stepProblem({...ready, scope: undefined}, 0), /alcance/);
  assert.match(stepProblem({...ready, period: {type: 'PERSONALIZADO', from: '2026-02-01', to: '2026-01-01'}}, 1), /anterior/);
  assert.equal(CUT_WARNING, 'Generar el corte detiene brevemente las operaciones de todos. Hazlo fuera del horario de atención');
  assert.match(CUT_EXPLAIN, /todos los ingresos del periodo y del alcance/);
  assert.match(CUT_EXPLAIN, /filtros.*se aplican al consultarlo/);
  const on = onCut({...q, mode: 'FECHA', compare: 'PERIODO_ANTERIOR'}, 9);
  assert.equal(on.cut, 9);
  assert.equal(on.mode, 'VIGENTE');
  assert.equal(on.compare, '');
  assert.match(verifyText({ok: true, rows_ok: true, metadata_ok: true, export_ok: true}), /^Íntegro/);
  assert.match(verifyText({ok: false, rows_ok: false, metadata_ok: true}), /^Alterado: no coinciden las filas congeladas/);
});

const SERIES = {id: 4, title: 'Informe Anual 2023-2025', granularity: 'CUENTA_PUBLICA', period_from: '2023-07-01', period_to: '2025-06-30', scope_id: null, state: 'VIGENTE',
  values: [
    {period_from: '2023-07-01', period_to: '2024-06-30', dimension: 'ADSCRIPCION_INICIAL', category_label: 'Total', category_code: null, is_total: true, value: 900},
    {period_from: '2024-07-01', period_to: '2025-06-30', dimension: 'ADSCRIPCION_INICIAL', category_label: 'Total', category_code: null, is_total: true, value: 1000},
    {period_from: '2023-07-01', period_to: '2024-06-30', dimension: 'ADSCRIPCION_INICIAL', category_label: 'Metropolitana', category_code: 'OFICINA_NACIONAL', is_total: false, value: 400},
    {period_from: '2024-07-01', period_to: '2025-06-30', dimension: 'ADSCRIPCION_INICIAL', category_label: 'Metropolitana', category_code: 'OFICINA_NACIONAL', is_total: false, value: 450},
  ]};

test('series: correspondencias sugeridas nunca marcadas; solo las confirmadas viajan; base64 del archivo', () => {
  const s = suggestions({suggested_mappings: [{dimension: 'ADSCRIPCION_INICIAL', category: 'Metropolitana', code: 'OFICINA_NACIONAL'}]});
  assert.equal(s.length, 1);
  assert.equal(s[0].checked, false, 'nunca viene marcada');
  assert.match(s[0].text, /«Metropolitana» corresponde a «Oficina Nacional»/);
  const d = {...emptySeries(null), title: 'Informe', source: 'Informe Anual', methodology: 'Conteo por adscripción', period_from: '2023-07-01', period_to: '2025-06-30', reason: 'Comparar'};
  assert.equal(draftProblem(d), '');
  assert.match(draftProblem({...d, scope: undefined}), /ámbito/);
  const file = {format: 'csv', content: toBase64(new TextEncoder().encode('a;b'))};
  assert.equal(file.content, 'YTti');
  assert.equal(seriesBody(d, file, {}, []).mappings, undefined, 'sin confirmar no viaja ninguna');
  assert.deepEqual(seriesBody(d, file, {}, [{...s[0]}]).mappings, [{dimension: 'ADSCRIPCION_INICIAL', category: 'Metropolitana', code: 'OFICINA_NACIONAL'}]);
  assert.equal(seriesBody(d, file, {}, []).scope_id, undefined, 'institucional: sin scope_id');
  assert.deepEqual(seriesBody(d, file, {periodo_desde: 'Desde', valor: ''}, []).columns, {periodo_desde: 'Desde'});
});

test('series junto a SIGER: claves de periodo del motor, consulta hasta ayer, dos líneas sin sumar y la discontinuidad con texto', () => {
  assert.equal(periodKey('MES', '2025-03-01'), '2025-03');
  assert.equal(periodKey('TRIMESTRE', '2025-07-01'), '2025-T3');
  assert.equal(periodKey('SEMESTRE', '2025-07-01'), '2025-S2');
  assert.equal(periodKey('ANIO', '2025-01-01'), '2025');
  assert.equal(periodKey('CUENTA_PUBLICA', '2025-07-01'), '2025-2026');
  const q = sigerQuery(SERIES, '2026-11-26', {dimension: 'ADSCRIPCION_INICIAL', code: 'OFICINA_NACIONAL'})!;
  assert.deepEqual(q.period, {type: 'PERSONALIZADO', from: '2023-07-01', to: '2026-11-25'}, 'desde el inicio de la serie hasta ayer');
  assert.deepEqual(q.rows, ['PERIODO_CUENTA_PUBLICA']);
  assert.deepEqual(q.filters, {ADSCRIPCION_INICIAL: ['OFICINA_NACIONAL']});
  assert.equal(sigerQuery({...SERIES, granularity: 'PERSONALIZADO'}, '2026-11-26'), null);
  const cats = seriesCategories(SERIES);
  assert.equal(cats[0].total, true, 'el total primero');
  // SIGER: 2025-2026 = 1.200 y 2026-2027 = 300 (a mano).
  const siger = readResult(response({rows: ['PERIODO_CUENTA_PUBLICA'], values: {PERIODO_CUENTA_PUBLICA: [{key: '2023-2024', label: 'Cuenta Pública 2023-2024'}, {key: '2024-2025', label: 'Cuenta Pública 2024-2025'},
    {key: '2025-2026', label: 'Cuenta Pública 2025-2026'}, {key: '2026-2027', label: 'Cuenta Pública 2026-2027'}]},
    cells: [{row: ['2025-2026'], column: [], n: 1200}, {row: ['2026-2027'], column: [], n: 300}], grand: {n: 1500, unique: 1500}}));
  const data = seriesChart(SERIES, cats[0], siger, 'PERIODO_CUENTA_PUBLICA');
  assert.deepEqual(data.categories.map(c => c.key), ['2023-2024', '2024-2025', '2025-2026', '2026-2027']);
  assert.deepEqual(data.series[0].values, [900, 1000, null, null], 'la serie: solo sus cifras declaradas');
  assert.deepEqual(data.series[1].values, [0, 0, 1200, 300], 'SIGER: las del servidor');
  assert.equal(data.series.length, 2, 'dos líneas, nunca una suma');
  assert.equal(data.divider!.after, 1);
  assert.match(data.divider!.label, /^Discontinuidad: la serie «Informe Anual 2023-2025» llega hasta Cuenta Pública 2024-2025/);
});

test('suma de series: solo la que entrega el servidor; con solapamiento no se ofrece aunque se pida (matriz a mano)', () => {
  // Dos series sin solapamiento: el servidor suma 400 + 450 = 850 (Metropolitana) y el total 1.900.
  const ok = {series: [], warnings: [], sum: [{dimension: 'ADSCRIPCION_INICIAL', category: 'OFICINA_NACIONAL', is_total: false, value: 850}, {dimension: 'ADSCRIPCION_INICIAL', category: 'Total', is_total: true, value: 1900}]};
  assert.deepEqual(sumRows(ok)!.map(r => r.value), [850, 1900]);
  const overlapping = {series: [], warnings: [{code: 'PERIODOS_SUPERPUESTOS'}], sum: null};
  assert.equal(sumRows(overlapping), null, 'con solapamiento el servidor no suma y la pantalla no ofrece cifra');
  assert.equal(sumRows(null), null);
});

test('Calidad: consultas de COMPLETITUD, barras con los porcentajes del servidor y matriz a mano', () => {
  assert.deepEqual(byVariable(PERIOD).rows, ['VARIABLE']);
  assert.deepEqual(byVariable(PERIOD).columns, ['ESTADO_VARIABLE']);
  assert.equal(byVariable(PERIOD).percent, 'FILA');
  assert.deepEqual(byScope(PERIOD, 'DERECHO').filters, {VARIABLE: ['DERECHO']});
  assert.deepEqual(byMonth(PERIOD, 'DERECHO').rows, ['PERIODO_MES']);
  // Matriz: tipo de solicitud 6 informados (60 %), 1 sin informacion (10 %), 3 pendientes (30 %); region 8 informada (80 %), 2 sin informacion (20 %).
  const r = readResult(response({measure: 'COMPLETITUD', rows: ['VARIABLE'], columns: ['ESTADO_VARIABLE'], percent: 'FILA',
    values: {VARIABLE: [{key: 'TIPO_SOLICITUD', label: 'Tipo de solicitud'}, {key: 'REGION_REQUIRENTE', label: 'Región del requirente'}],
      ESTADO_VARIABLE: [{key: 'INFORMADO', label: 'Informado'}, {key: 'PENDIENTE', label: 'Pendiente'}, {key: 'SIN_INFORMACION', label: 'Sin información'}]},
    cells: [{row: ['TIPO_SOLICITUD'], column: ['PENDIENTE'], n: 3, percent: 30}, {row: ['TIPO_SOLICITUD'], column: ['INFORMADO'], n: 6, percent: 60},
      {row: ['TIPO_SOLICITUD'], column: ['SIN_INFORMACION'], n: 1, percent: 10},
      {row: ['REGION_REQUIRENTE'], column: ['INFORMADO'], n: 8, percent: 80}, {row: ['REGION_REQUIRENTE'], column: ['SIN_INFORMACION'], n: 2, percent: 20},
      // Contexto: 1 informado y 2 pendientes de 3; el servidor redondea a 33,3 y 66,7 (el cliente no recalcula).
      {row: ['CONTEXTO'], column: ['INFORMADO'], n: 1, percent: 33.3}, {row: ['CONTEXTO'], column: ['PENDIENTE'], n: 2, percent: 66.7}],
    grand: {n: 23, unique: 10}, rowTotals: [{key: ['TIPO_SOLICITUD'], n: 10}, {key: ['REGION_REQUIRENTE'], n: 10}, {key: ['CONTEXTO'], n: 3}]}));
  const t = tableModel(r, ['VARIABLE'], ['ESTADO_VARIABLE']);
  assert.equal(share(t, ['CONTEXTO'], 'PENDIENTE'), 66.7);
  assert.equal(partsText(stateParts(t, ['CONTEXTO'])), 'Informado 33,3 %; Pendiente 66,7 %');
  assert.deepEqual(stateParts(t, ['TIPO_SOLICITUD']).map(p => [p.state, p.percent, p.n]), [['INFORMADO', 60, 6], ['SIN_INFORMACION', 10, 1], ['PENDIENTE', 30, 3]]);
  assert.equal(share(t, ['TIPO_SOLICITUD'], 'PENDIENTE'), 30);
  assert.equal(share(t, ['REGION_REQUIRENTE'], 'SIN_INFORMACION'), 20);
  assert.equal(share(t, ['REGION_REQUIRENTE'], 'PENDIENTE'), 0);
  assert.equal(partsText(stateParts(t, ['REGION_REQUIRENTE'])), 'Informado 80 %; Sin información 20 %');
});

test('Calidad: enlace a la bandeja con el filtro solo si la cuenta la tiene; la región sin enlace (condición 1 y precisión del GO)', () => {
  assert.equal(boardFor('TIPO_SOLICITUD'), 'com');
  assert.equal(boardFor('VIA_INGRESO'), 'com');
  assert.equal(boardFor('DERECHO'), 'req');
  assert.equal(boardFor('CONTEXTO'), 'req');
  assert.equal(boardFor('REGION_REQUIRENTE'), null);
  const reader = {...USER, grants: [...USER.grants, {role: 'CONSULTA_INSTITUCIONAL', scope: null}]};
  assert.deepEqual(pendingLink(reader, 'TIPO_SOLICITUD'), {href: '#com?filter=stat_pending', text: 'Ver las comunicaciones con datos pendientes'});
  assert.deepEqual(pendingLink(reader, 'DERECHO'), {href: '#req?filter=stat_pending', text: 'Ver los requerimientos con datos pendientes'});
  assert.deepEqual(pendingLink(USER, 'TIPO_SOLICITUD'), {href: null, text: NO_BOARD_TEXT + '.'}, 'solo Reportes: sin bandeja');
  assert.equal(NO_BOARD_TEXT, 'Los registros pendientes los completa quien los gestiona; tu cuenta no tiene esa bandeja');
  assert.equal(pendingLink(reader, 'REGION_REQUIRENTE'), null);
});
