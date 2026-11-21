// Buscador (features/search/search.ts, G08b): la ruta nunca lleva el termino, el estado vive en la pestana y se borra
// al cerrar sesion, folio exacto, lecturas, motivos, filtros, periodo y paleta. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {
  alternatives, apiBody, chileDay, clearSearch, emptyNotes, exactTarget, FOLIO_ONLY_NOTE, NO_PERSON_NOTE, FILTER_THRESHOLD, formatRun, interpretation, matchReason, nextIndex, paletteEntry,
  periodRange, primaryTarget, readSearch, saveSearch, scopeLabel, SEARCH_ROUTE, searchable, SHORT_HINT, showFilters, startSearch, summaryLines, tooShort,
} from '../src/features/search/search.ts';
import type {Store} from '../src/features/search/search.ts';
import {menuFor, menuItems} from '../src/app/menu.ts';
import {parseRoute} from '../src/app/routes.ts';

/** sessionStorage sintetico. */
function memory(): Store & {data: Map<string, string>} {
  const data = new Map<string, string>();
  return {data, getItem: k => data.get(k) ?? null, setItem: (k, v) => {data.set(k, v);}, removeItem: k => {data.delete(k);}};
}
const TERMS = ['12.345.678-5', '12345678-5', '123456785', 'Ana Pérez Soto', 'REQ-A-000012', '001', 'Pasaporte AB-123.456', 'ñandú & co?x=1#y'];

test('la ruta nunca contiene el término: siempre #buscar, sin parámetros', () => {
  for (const term of TERMS) {
    const store = memory();
    const route = startSearch(term, store);
    assert.equal(route, SEARCH_ROUTE);
    assert.equal(route, 'buscar');
    for (const piece of [term, encodeURIComponent(term), term.split(' ')[0], term.replace(/\D/g, '')].filter(p => p.length >= 3)) assert.ok(!route.includes(piece), term);
    assert.deepEqual(parseRoute(route), {view: 'buscar', filter: '', isDetail: false, kind: 'buscar', id: undefined});
    // El termino queda solo en la pestana.
    assert.equal(readSearch(store)?.q, term.trim());
    const entry = paletteEntry(term, true);
    assert.ok(entry && 'route' in entry);
    assert.equal(entry.route, SEARCH_ROUTE);
  }
  const reader = {id: 1, username: 'x', name: 'X', home_scope: 1, must_change: false, grants: [{role: 'LECTURA', scope: 1}], substitutions: [], can_search: true};
  assert.equal(menuItems(menuFor(reader, 0)).find(i => i.path.startsWith('buscar'))?.path, 'buscar');
});

test('estado en la pestaña: filtros conservados al volver de una ficha y borrados al cerrar sesión', () => {
  const store = memory();
  startSearch('  Ana  ', store);
  assert.deepEqual(readSearch(store), {q: 'Ana', fresh: true});
  saveSearch(store, {q: 'Ana', role: 'NNA', open: true, period: 'd30'});
  assert.deepEqual(readSearch(store), {q: 'Ana', role: 'NNA', open: true, period: 'd30'});
  clearSearch(store);
  assert.equal(readSearch(store), null);
  assert.equal(store.data.size, 0);
  // Contenido danado o sin almacenamiento: no falla.
  store.setItem('siger-busqueda', '{no es json');
  assert.equal(readSearch(store), null);
  assert.equal(readSearch(null), null);
  saveSearch(null, {q: 'x'});
  clearSearch(null);
});

test('largo del término y pista del correlativo corto', () => {
  assert.ok(tooShort('1') && tooShort(' 12 ') && !tooShort('') && !tooShort('001'));
  assert.ok(searchable('001') && !searchable('ab') && !searchable('x'.repeat(161)));
  assert.match(SHORT_HINT, /001/);
  const hint = paletteEntry('12', true);
  assert.ok(hint && 'hint' in hint && hint.hint.includes('001'));
  assert.equal(paletteEntry('', true), null);
  // Sin funcion de lectura de expedientes (TI, AUDITORIA sola): la paleta no ofrece el buscador.
  assert.equal(paletteEntry('12.345.678-5', false), null);
  assert.equal(paletteEntry('12', false), null);
  const entry = paletteEntry(' REQ-A-12 ', true);
  assert.deepEqual(entry, {label: 'Buscar persona o folio «REQ-A-12»', term: 'REQ-A-12', route: 'buscar'});
});

test('consulta al servidor: término y filtros en el cuerpo del POST; período en hora de Chile', () => {
  const now = new Date('2026-03-01T02:30:00Z'); // 28 de febrero en Chile (UTC-3)
  assert.equal(chileDay(now), '2026-02-28');
  assert.deepEqual(apiBody({q: ' Ana Pérez '}, now), {q: 'Ana Pérez'});
  assert.deepEqual(apiBody({q: '12345678', as: 'RUN', role: 'NNA', open: true, scope: 7, region: '05', period: 'd30', fresh: true}, now),
    {q: '12345678', as: 'RUN', role: 'NNA', open: true, scope: 7, region: '05', from: '2026-01-30', to: '2026-02-28'});
  assert.deepEqual(periodRange({q: 'x', period: 'm12'}, now), {from: '2025-03-01', to: '2026-02-28'});
  assert.deepEqual(periodRange({q: 'x', period: 'custom', from: '2026-01-01'}, now), {from: '2026-01-01', to: undefined});
  assert.deepEqual(periodRange({q: 'x'}, now), {});
});

test('folio exacto: solo una lectura FOLIO con un registro abre la ficha', () => {
  const reading = (type: string, total: number, exact: unknown = null) => ({type, total, exact});
  assert.deepEqual(exactTarget({readings: [reading('FOLIO', 1, {kind: 'REQ', id: 12, folio: 'REQ-A-000012'})]}), ['REQ', 12]);
  assert.equal(exactTarget({readings: [reading('FOLIO', 1, {kind: 'REQ', id: 12}), reading('RUN', 0)]}), null, 'un número con dos lecturas no abre nada');
  assert.equal(exactTarget({readings: [reading('FOLIO', 2, null)]}), null);
  assert.equal(exactTarget({readings: [reading('FOLIO', 0, null)]}), null);
  assert.equal(exactTarget({readings: [reading('RUN', 1)]}), null);
});

test('interpretación detectada y lecturas para cambiarla', () => {
  assert.equal(interpretation({detected: ['RUN'], available: ['RUN', 'DOCUMENTO']}), 'Buscando como RUN');
  assert.deepEqual(alternatives({detected: ['RUN'], available: ['RUN', 'DOCUMENTO']}), ['DOCUMENTO']);
  // Un número: dos lecturas a la vez, cada una se puede ver sola.
  assert.equal(interpretation({detected: ['FOLIO', 'RUN'], available: ['FOLIO', 'RUN', 'DOCUMENTO']}), 'Buscando como folio y como RUN');
  assert.deepEqual(alternatives({detected: ['FOLIO', 'RUN'], available: ['FOLIO', 'RUN', 'DOCUMENTO']}), ['FOLIO', 'RUN', 'DOCUMENTO']);
  assert.equal(interpretation({detected: ['FOLIO', 'RUN'], forced: 'NOMBRE', available: ['NOMBRE']}), 'Buscando como nombre');
});

test('motivo de cada coincidencia; solo por nombre puede ser otra persona', () => {
  assert.deepEqual(matchReason({reason: 'RUN', authenticated: true}), {text: 'Coincide por RUN autenticado con ClaveÚnica', uncertain: false});
  assert.deepEqual(matchReason({reason: 'RUN', authenticated: false}), {text: 'Coincide por RUN declarado', uncertain: false});
  assert.equal(matchReason({reason: 'DOCUMENTO'}).uncertain, false);
  assert.deepEqual(matchReason({reason: 'NOMBRE', exact: true}), {text: 'Coincide por el nombre completo', uncertain: true});
  assert.equal(matchReason({reason: 'NOMBRE', exact: false}).uncertain, true);
});

test('filtros solo con muchos resultados o si ya hay uno activo', () => {
  assert.equal(showFilters({total: FILTER_THRESHOLD}, {q: 'ana'}), false);
  assert.equal(showFilters({total: FILTER_THRESHOLD + 1}, {q: 'ana'}), true);
  assert.equal(showFilters({total: 3, more: true}, {q: 'ana'}), true);
  assert.equal(showFilters({total: 2}, {q: 'ana', open: true}), true, 'un filtro activo se puede quitar');
});

test('presentación: sede con región, RUN, totales con lo abierto primero y ficha principal', () => {
  assert.equal(scopeLabel({code: 'UPR-REQ', name: 'Requerimientos', level: 'CENTRAL'}), 'UPR-REQ · Requerimientos');
  assert.equal(scopeLabel({code: 'REG-DEMO', name: 'Sede sintética', level: 'REGIONAL', region_name: 'Valparaíso'}), 'Sede sintética · Valparaíso');
  assert.equal(scopeLabel({code: 'REG-X', name: 'Sede X', level: 'REGIONAL', region_name: null}), 'Sede X · sin región asignada');
  assert.equal(scopeLabel(null), 'Sin información');
  assert.equal(formatRun('12345678', '5'), '12.345.678-5');
  assert.equal(formatRun('1234567', null), '1.234.567');
  assert.deepEqual(summaryLines({com: 3, com_open: 1, req: 2, req_open: 1, cas: 1, cas_open: 0}), {open: '1 COM · 1 REQ · 0 Casos', total: '3 COM · 2 REQ · 1 Caso'});
  assert.deepEqual(primaryTarget({communication: {id: 4}, requirements: [{id: 2}]}), ['COM', 4]);
  assert.deepEqual(primaryTarget({communication: null, requirements: [], cases: [{id: 9}]}), ['CAS', 9], 'sin COM legible');
  assert.deepEqual(primaryTarget({communication: {id: 4}, folio_matches: [{kind: 'CAS', id: 9}]}), ['CAS', 9], 'el folio buscado');
});

test('teclado: flechas, Inicio y Fin entre resultados', () => {
  assert.equal(nextIndex('ArrowDown', 0, 3), 1);
  assert.equal(nextIndex('ArrowDown', 2, 3), 2);
  assert.equal(nextIndex('ArrowUp', 0, 3), 0);
  assert.equal(nextIndex('Home', 2, 3), 0);
  assert.equal(nextIndex('End', 0, 3), 2);
  assert.equal(nextIndex('Tab', 0, 3), null);
  assert.equal(nextIndex('ArrowDown', 0, 0), null);
});

test('sin coincidencias de persona: aviso siempre y, si la cuenta no lee COM ni fichas, por qué', () => {
  const empty = (type: string) => ({type, total: 0, groups: []});
  assert.deepEqual(emptyNotes({can_find_people: true}, empty('NOMBRE')), [NO_PERSON_NOTE]);
  assert.deepEqual(emptyNotes({can_find_people: true}, empty('RUN')), [NO_PERSON_NOTE]);
  assert.deepEqual(emptyNotes({can_find_people: false}, empty('DOCUMENTO')), [NO_PERSON_NOTE, FOLIO_ONLY_NOTE]);
  assert.match(NO_PERSON_NOTE, /no significa que la persona no tenga registros/);
  assert.match(FOLIO_ONLY_NOTE, /REQ y Casos por folio/);
  // Un folio no es una coincidencia de persona; con resultados o demasiados no hay aviso.
  assert.deepEqual(emptyNotes({can_find_people: false}, empty('FOLIO')), []);
  assert.deepEqual(emptyNotes({can_find_people: false}, {type: 'NOMBRE', total: 1, groups: [{}]}), []);
  assert.deepEqual(emptyNotes({can_find_people: true}, {type: 'NOMBRE', too_many: true, groups: []}), []);
});
