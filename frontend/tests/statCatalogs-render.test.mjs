// Pantalla de catalogos estadisticos (0.8.0, gate E02) renderizada en servidor (Vite ssrLoadModule + react-dom/server,
// sin dependencias nuevas): rotulos y estados en texto, sin codigos internos de catalogo, Activar solo en propuestas y
// Correspondencia solo con otra version del mismo catalogo.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

let vite, screen;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  screen = await vite.ssrLoadModule('/src/features/admin/StatCatalogs.tsx');
});
after(async () => {await vite?.close();});

const version = (id, catalog, state, label) => ({id, catalog, label, state, source: 'Fuente sintética', file_sha256: 'fb985d09ad3f633f68c6324b' + '0'.repeat(40), items: 87, revision: 1,
  loaded_ms: Date.parse('2026-11-24T12:00:00Z'), loaded_by: 'Tamara TI', basis: 'Fundamento sintético', activated_ms: state === 'VIGENTE' ? 1 : null, retired_ms: null});
const DATA = {
  catalogs: [{catalog: 'TIPO_SOLICITUD', label: 'Tipo de solicitud'}, {catalog: 'DERECHO', label: 'Derechos'}],
  special: [{code: 'PENDIENTE', label: 'Pendiente'}], formats: {catalog: 'siger-catalogo-1', mapping: 'siger-correspondencia-1'},
  versions: [version(2, 'DERECHO', 'PROPUESTA', 'Modelo de derechos versión 2.0 (2022)'), version(1, 'DERECHO', 'VIGENTE', 'Modelo anterior')],
  mappings: [{from_version: 1, to_version: 2, pairs: 3, loaded_ms: 0, loaded_by: 'Tamara TI'}],
};
const ctx = {user: {id: 1, grants: [{role: 'TI', scope: null}]}, catalog: {}, act: () => {}, go: () => {}, reload: () => {}, message: () => {}};
const render = () => renderToStaticMarkup(createElement(screen.default, {ctx, initial: DATA}));

test('catálogos con su estado en texto y sin códigos internos visibles', () => {
  const html = render();
  assert.match(html, /Catálogos estadísticos/);
  for (const label of ['Tipo de solicitud', 'Derechos', 'Aún no se importa ninguna versión de este catálogo.', 'Vigente: versión 1, «Modelo anterior». 1 propuesta por revisar.',
    'Propuesta', 'Vigente', 'fb985d09ad3f', 'versión 1 → versión 2 (3 pares)', 'Importar catálogo']) assert.ok(html.includes(label), label);
  for (const code of ['TIPO_SOLICITUD', 'DERECHO<', '>PROPUESTA<', '>VIGENTE<', 'siger-catalogo-1']) assert.ok(!html.includes(code), code);
});

test('Activar solo en la propuesta; Correspondencia en ambas versiones del mismo catálogo', () => {
  const html = render();
  assert.equal(html.match(/>Activar</g)?.length, 1);
  assert.equal(html.match(/>Correspondencia</g)?.length, 2);
  assert.equal(html.match(/>Ver ítems</g)?.length, 2);
});
