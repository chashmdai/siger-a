// Marca territorial y de riesgo en la bandeja (0.7.0, D23; gate T03b), escenario 8, con los componentes reales
// renderizados en servidor (Vite ssrLoadModule + react-dom/server, como territory-render): marca con icono y texto
// accesible, columnas de la lista de COM sin cambios, marca solo en la lista y la cola de comunicaciones, y animacion
// liviana de 5 a 6 segundos (solo opacidad) que se apaga con prefers-reduced-motion.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

let vite, mark, worklist, dashboard;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  mark = await vite.ssrLoadModule('/src/components/RegionMark.tsx');
  worklist = await vite.ssrLoadModule('/src/features/worklist/WorkspaceList.tsx');
  dashboard = await vite.ssrLoadModule('/src/features/dashboard/Dashboard.tsx');
});
after(async () => {await vite?.close();});

const ALL = ['SOL', 'COLABORACION', 'TRANSFERENCIA', 'DERIVACION', 'ENTREGA'];
const UPR = {id: 1, code: 'UPR-REQ', name: 'Requerimientos', unit_code: 'UPR', area_code: 'REQ', site_code: 'CENTRAL', level: 'CENTRAL', state: 'ACTIVA', enabled: true, receives: ALL, coverage: []};
const OHI = {id: 2, code: 'REG-OHI', name: "Sede O'Higgins", unit_code: 'REG', site_code: 'OHI', regional: true, level: 'REGIONAL', region_code: '06', state: 'ACTIVA', enabled: true, receives: ALL, coverage: ['07']};
const catalog = {scopes: [UPR, OHI], regions: [{code: '06', name: "Libertador General Bernardo O'Higgins"}, {code: '07', name: 'Maule'}, {code: '13', name: 'Metropolitana de Santiago'}],
  units: [{code: 'UPR', name: 'Protección'}], central_coverage: ['13'], people: []};
const ctx = {catalog, user: {id: 7}, act: () => {}, reload: () => {}, go: () => {}, message: () => {}};
const com = (id, origin_region, risk, to_distribute) => ({id, folio: 'COM-2026-00000' + id, subject: 'Asunto sintético ' + id, state: to_distribute ? 'NUEVA' : 'DISTRIBUIDA', channel: 'FORMULARIO', origin_scope: 1, created_ms: 0, origin_region, risk, to_distribute});
const render = row => renderToStaticMarkup(createElement(mark.RegionMark, {catalog, row}));
const esc = s => s.replaceAll("'", '&#x27;');

test('marca regional: número visible, título y texto para lectores de pantalla (escenario 8)', () => {
  const html = render(com(1, '07', false, true));
  assert.match(html, /^<span class="region-mark region-mark-regional" title="Maule - atiende Sede O&#x27;Higgins">/);
  assert.match(html, /<span aria-hidden="true">VII<\/span><span class="sr-only">Maule - atiende Sede O&#x27;Higgins<\/span>/);
  assert.doesNotMatch(html, /<svg/, 'sin riesgo no lleva icono de alerta');
  assert.match(render(com(1, '13', false, true)), /class="region-mark region-mark-neutral" title="Metropolitana de Santiago - atiende el nivel central"/);
});

test('marca de riesgo: roja con icono y "Riesgo actual informado"; respira solo por distribuir', () => {
  const pending = render(com(2, null, true, true));
  assert.match(pending, /class="region-mark region-mark-risk region-mark-breathing" title="Riesgo actual informado\. Sin región declarada"/);
  assert.match(pending, /<svg[^>]*aria-hidden="true"/);
  assert.match(pending, /<span aria-hidden="true">Riesgo<\/span><span class="sr-only">Riesgo actual informado\. Sin región declarada<\/span>/);
  const routed = render(com(3, '13', true, false));
  assert.match(routed, /class="region-mark region-mark-risk" title="Riesgo actual informado\. Metropolitana de Santiago - atiende el nivel central"/);
  assert.match(routed, /<svg/);
  assert.equal(render(com(4, null, false, true)), '', 'sin región y sin riesgo no hay marca');
});

test('lista de COM: mismas columnas en el mismo orden y la marca junto a la referencia', () => {
  const columns = worklist.columnsFor('com', ctx);
  assert.deepEqual(columns.map(c => c.name), ['Referencia', 'Asunto', 'Estado', 'Canal', 'Ámbito', 'Registro']);
  const cell = renderToStaticMarkup(columns[0].render(com(5, '07', true, true)));
  assert.match(cell, /^<span class="ref-cell">.*<button class="link folio">COM-2026-000005<\/button><span class="region-mark region-mark-risk region-mark-breathing" title="Riesgo actual informado\. Maule - atiende Sede O&#x27;Higgins">/);
  // Las demas bandejas no llevan marca.
  for (const kind of ['sol', 'req', 'cas', 'flujos']) assert.doesNotMatch(renderToStaticMarkup(worklist.columnsFor(kind, ctx)[0].render(com(5, '07', true, true))), /region-mark/, kind);
});

test('cola "Ingresos por distribuir": misma lista y orden, con la marca en cada COM', () => {
  const items = [com(9, '07', false, true), com(8, null, true, true), com(7, null, false, true)];
  const html = renderToStaticMarkup(createElement(dashboard.Queue, {ctx, queue: {title: 'Ingresos por distribuir', route: 'com', filter: 'pending', page: {items, total: 3}}}));
  assert.deepEqual([...html.matchAll(/<span class="folio">(COM-[0-9-]+)<\/span>/g)].map(m => m[1]), ['COM-2026-000009', 'COM-2026-000008', 'COM-2026-000007']);
  assert.match(html, new RegExp(esc('COM-2026-000009</span><span class="region-mark region-mark-regional" title="Maule - atiende Sede O\'Higgins">').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.match(html, /COM-2026-000008<\/span><span class="region-mark region-mark-risk region-mark-breathing"/);
  assert.match(html, /COM-2026-000007<\/span><\/span>/, 'sin región y sin riesgo, sin marca');
  // Otra cola no lleva marca.
  const sol = renderToStaticMarkup(createElement(dashboard.Queue, {ctx, queue: {title: 'Solicitudes', route: 'sol', filter: 'pending', page: {items: [com(9, '07', true, true)], total: 1}}}));
  assert.doesNotMatch(sol, /region-mark/);
});

test('CSS: respiración de 5 a 6 segundos solo con opacidad, sin animación con prefers-reduced-motion', () => {
  const css = readFileSync(fileURLToPath(new URL('../src/styles/screens.css', import.meta.url)), 'utf8');
  const rule = /\.region-mark-breathing::after \{([^}]*)\}/.exec(css)?.[1] ?? '';
  const animation = /animation: region-mark-breathe ([0-9.]+)s [^;]*infinite;/.exec(rule);
  assert.ok(animation, 'la capa de riesgo respira');
  const seconds = Number(animation[1]);
  assert.ok(seconds >= 5 && seconds <= 6, 'ciclo de 5 a 6 segundos: ' + seconds);
  assert.match(rule, /position: absolute;/, 'la capa no ocupa espacio en la fila');
  const frames = /@keyframes region-mark-breathe \{(.*)\}\n/.exec(css.replaceAll('\r', ''))?.[1] ?? '';
  assert.ok(frames.length > 0);
  const properties = [...frames.matchAll(/([a-z-]+):/g)].map(m => m[1]);
  assert.ok(properties.length > 0 && properties.every(p => p === 'opacity' || p === 'box-shadow'), 'solo opacidad o sombra: ' + properties);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\) \{ \.region-mark-breathing::after \{ animation: none;/);
  // La marca de riesgo no depende solo del color: el componente lleva icono y texto (pruebas anteriores).
  assert.match(css, /\.region-mark-risk \{ background: var\(--risk-mark-bg\);/);
  assert.match(css, /\.region-mark-regional \{ background: var\(--region-mark-bg\);/);
});
