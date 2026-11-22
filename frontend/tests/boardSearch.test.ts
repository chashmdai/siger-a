// Texto fuera de la URL (G08c): ninguna ruta del frontend lleva el termino del buscador ni el texto de una bandeja; el
// texto vive en la pestana (sessionStorage) y se borra al cerrar sesion; las consultas van por POST con el texto en el
// cuerpo. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  boardEntries, clearBoardSearch, legacyRoute, readBoardQuery, saveBoardQuery, SEARCHABLE, startBoardSearch,
} from '../src/features/worklist/boardSearch.ts';
import {clearSearch, paletteEntry, readSearch, startSearch} from '../src/features/search/searchModel.ts';
import type {Store} from '../src/lib/tabStore.ts';
import {BOARDS, boardRoute, parseRoute} from '../src/app/routes.ts';

function memory(): Store & {data: Map<string, string>} {
  const data = new Map<string, string>();
  return {data, getItem: k => data.get(k) ?? null, setItem: (k, v) => {data.set(k, v);}, removeItem: k => {data.delete(k);}};
}
const TERMS = ['12.345.678-5', '12345678-5', 'Ana Pérez Soto', 'REQ-A-000012', '001', 'Pasaporte AB-123.456', 'ñandú & co?x=1#y', 'k=1&q=2'];
/** Trozos reconocibles del texto (tal cual, codificado, primera palabra y solo digitos). */
const pieces = (term: string) => [term, encodeURIComponent(term), new URLSearchParams({q: term}).toString().slice(2), term.split(' ')[0], term.replace(/\D/g, '')]
  .filter(p => p.length >= 3);
function clean(route: string, term: string) {
  for (const p of pieces(term)) assert.ok(!route.includes(p), `la ruta «${route}» contiene «${p}»`);
  assert.ok(!('query' in parseRoute(route)), 'parseRoute ya no lee texto de la ruta');
}

test('ninguna ruta del frontend contiene el término del buscador ni el texto de una bandeja', () => {
  const available = new Set(BOARDS);
  for (const term of TERMS) {
    const store = memory();
    clean(startSearch(term, store), term);
    const person = paletteEntry(term, true);
    if (person && 'route' in person) clean(person.route, term);
    for (const kind of BOARDS) clean(startBoardSearch(kind, term, store), term);
    const entries = boardEntries(term, available);
    assert.equal(entries.length, SEARCHABLE.length);
    for (const e of entries) {clean(e.route, term); assert.equal(e.query, term.trim());}
    // Una ruta antigua con texto se reemplaza por la misma sin el texto.
    for (const old of ['req?q=' + encodeURIComponent(term), 'cas?filter=open&q=' + encodeURIComponent(term), 'buscar?q=' + encodeURIComponent(term)]) {
      const next = legacyRoute(old, store);
      assert.ok(next !== null);
      clean(next, term);
    }
    // El texto quedo solo en la pestana.
    assert.equal(readSearch(store)?.q, term.trim());
    assert.equal(readBoardQuery(store, 'req'), term.trim());
  }
  // Rutas de Inicio y del menu: solo bandeja y filtro.
  assert.equal(boardRoute('req'), 'req');
  assert.equal(boardRoute('req', 'open'), 'req?filter=open');
  assert.deepEqual(parseRoute('req?filter=open'), {view: 'req', filter: 'open', isDetail: false, kind: 'req', id: undefined});
});

test('ruta antigua: el texto pasa a la pestaña y se conservan los demás parámetros', () => {
  const store = memory();
  assert.equal(legacyRoute('req?filter=open&q=Ana%20P%C3%A9rez', store), 'req?filter=open');
  assert.equal(readBoardQuery(store, 'req'), 'Ana Pérez');
  assert.equal(legacyRoute('cas?q=', store), 'cas');
  assert.equal(readBoardQuery(store, 'cas'), '');
  // Fuera de una bandeja (por ejemplo #buscar?q=) el texto se descarta: el buscador tiene su propio estado.
  assert.equal(legacyRoute('buscar?q=Ana', store), 'buscar');
  assert.equal(readBoardQuery(store, 'buscar'), '');
  assert.equal(legacyRoute('req?filter=open', store), null);
  assert.equal(legacyRoute('REQ/3', store), null);
  assert.equal(legacyRoute('inicio', store), null);
});

test('texto por bandeja en la pestaña, borrado al cerrar sesión', () => {
  const store = memory();
  saveBoardQuery(store, 'req', '  REQ-A-12 ');
  saveBoardQuery(store, 'cas', 'Ana');
  saveBoardQuery(store, 'desconocida', 'x');
  assert.equal(readBoardQuery(store, 'req'), 'REQ-A-12');
  assert.equal(readBoardQuery(store, 'cas'), 'Ana');
  assert.equal(readBoardQuery(store, 'desconocida'), '');
  assert.equal(readBoardQuery(store, 'com'), '');
  saveBoardQuery(store, 'req', '');
  assert.equal(readBoardQuery(store, 'req'), '');
  saveBoardQuery(store, 'cas', 'x'.repeat(200));
  assert.equal(readBoardQuery(store, 'cas').length, 160);
  startSearch('Ana', store);
  clearBoardSearch(store);
  clearSearch(store);
  assert.equal(store.data.size, 0, 'nada queda en la pestaña');
  // Contenido danado o sin almacenamiento: no falla.
  store.setItem('siger-bandejas', '["no es un objeto"]');
  assert.equal(readBoardQuery(store, 'req'), '');
  store.setItem('siger-bandejas', '{no es json');
  assert.equal(readBoardQuery(store, 'req'), '');
  assert.equal(readBoardQuery(null, 'req'), '');
  saveBoardQuery(null, 'req', 'x');
  clearBoardSearch(null);
  // La paleta solo ofrece las bandejas del menu de la cuenta y nada sin texto.
  assert.deepEqual(boardEntries('Ana', new Set(['cas', 'inicio'])).map(e => e.path), ['cas']);
  assert.deepEqual(boardEntries('  ', new Set(BOARDS)), []);
});

/** Codigo de src sin comentarios (los comentarios pueden citar las rutas antiguas). */
function sources(dir: string): [string, string][] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sources(path);
    if (!/\.(ts|tsx)$/.test(name)) return [];
    const code = readFileSync(path, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[\s;{}])\/\/.*$/gm, '$1');
    return [[path, code] as [string, string]];
  });
}

test('el código no arma rutas ni consultas con el texto: buscador y bandejas por POST', () => {
  const files = sources(fileURLToPath(new URL('../src', import.meta.url)));
  assert.ok(files.length > 20);
  const forbidden: [RegExp, string][] = [
    [/[?&]q=/, 'parámetro q en una ruta o URL'],
    [/\.set\(\s*['"]q['"]/, 'URLSearchParams con q'],
    [/['"`]\/search\?/, 'consulta GET al buscador'],
    [/\/worklist\/['"`]\s*\+[^\n]*['"`]\?/, 'consulta GET a una bandeja'],
    [/request\(\s*['"`]\/(search|worklist)/, 'buscador o bandeja sin POST'],
  ];
  for (const [path, code] of files) for (const [pattern, what] of forbidden) assert.ok(!pattern.test(code), `${path}: ${what}`);
  const all = files.map(([, code]) => code).join('\n');
  assert.match(all, /postJson\('\/search'/);
  assert.match(all, /postJson\('\/worklist\/' \+ kind/);
});
