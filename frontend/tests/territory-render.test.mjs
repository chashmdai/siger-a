// Cobertura territorial en la interfaz (0.7.0, gate T03), con los componentes reales renderizados en servidor (Vite
// ssrLoadModule + react-dom/server, sin dependencias nuevas): selector de destino (aviso del nivel central, sede que
// cubre la region) y panel Estructura (cobertura vigente, historia, boton Cobertura y cobertura del nivel central).
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

let vite, picker, destination, structure;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  picker = await vite.ssrLoadModule('/src/components/DestinationPicker.tsx');
  destination = await vite.ssrLoadModule('/src/lib/destination.ts');
  structure = await vite.ssrLoadModule('/src/features/admin/Structure.tsx');
});
after(async () => {await vite?.close();});

const REGIONS = [{code: '03', name: 'Atacama'}, {code: '04', name: 'Coquimbo'}, {code: '13', name: 'Metropolitana de Santiago'}];
const ALL = ['SOL', 'COLABORACION', 'TRANSFERENCIA', 'DERIVACION', 'ENTREGA'];
const UPR = {id: 1, code: 'UPR', name: 'Protección', unit_code: 'UPR', area_code: null, site_code: 'CENTRAL', level: 'CENTRAL', state: 'ACTIVA', enabled: true, receives: ALL, coverage: []};
const COQ = {id: 2, code: 'REG-COQ', name: 'Sede Coquimbo', unit_code: 'REG', site_code: 'COQ', regional: true, level: 'REGIONAL', region_code: '04', state: 'ACTIVA', enabled: true, receives: ALL, coverage: ['03']};
const catalog = {scopes: [UPR, COQ], regions: REGIONS, units: [{code: 'UPR', name: 'Protección'}], central_coverage: ['13'], people: [{id: 7, name: 'Tamara TI'}]};

function pick(region) {
  const d = destination.destinations(catalog, 'SOL');
  return renderToStaticMarkup(createElement(picker.DestinationPicker, {destinations: d, region, value: destination.initialChoice(d, region), onChange: () => {}}));
}

test('selector: región del nivel central con aviso, sin unidad propuesta (escenario 6)', () => {
  const html = pick('13');
  assert.match(html, /La región declarada, <strong>Metropolitana de Santiago<\/strong>, la atiende el nivel central/);
  assert.doesNotMatch(html, /Propuesta según la región declarada/);
  assert.match(html, /<option value="" selected="">Seleccionar<\/option>/, 'la unidad queda por elegir');
});

test('selector: la sede que cubre la región se propone y se explica (escenario 1)', () => {
  const html = pick('03');
  assert.match(html, /Propuesta según la región declarada: <strong>Atacama<\/strong>/);
  assert.doesNotMatch(html, /la atiende el nivel central/);
  assert.match(html, /Atacama \(atendida por Sede Coquimbo\)/);
  assert.match(html, /Sede Coquimbo atiende Atacama por cobertura adicional\./);
  assert.match(html, /Recibe: <strong>Coordinación regional · Sede Coquimbo<\/strong>/);
});

test('Estructura: cobertura vigente, historia, botón Cobertura y cobertura del nivel central', () => {
  const site = {...COQ, revision: 3, staff: 1, open_req: 0, open_cases: 0, pending_sol: 0, close_blockers: [], territorial_effect: [],
    coverage_history: [{id: 4, region_code: '03', from_ms: Date.parse('2026-11-23T12:00:00Z'), from_by: 7, from_basis: 'Resolución exenta 1', until_ms: null, until_by: null, until_basis: null}]};
  const closed = {...COQ, id: 3, code: 'REG-OLD', name: 'Sede antigua', state: 'CERRADA', enabled: false, coverage: [], coverage_history: [], close_blockers: []};
  const ctx = {catalog, user: {id: 7}, act: () => {}, reload: () => {}, go: () => {}, message: () => {}};
  const html = renderToStaticMarkup(createElement(structure.Structure, {ctx, rows: [{...UPR, revision: 1}, site, closed], users: [], grants: [],
    central: {regions: ['13'], revision: 2, history: [{id: 1, region_code: '13', from_ms: 1, from_by: 7, from_basis: 'Oficina Nacional', until_ms: null}]}, openProfile: () => {}}));
  assert.match(html, /Regiones que atiende el nivel central:<\/strong> Metropolitana de Santiago/);
  assert.match(html, /Editar cobertura del nivel central/);
  assert.match(html, /Cubre además: Atacama/);
  assert.match(html, /Historia de cobertura \(1\)/);
  assert.match(html, /Atacama \(03\)<\/strong> <span class="chip chip-ok">Vigente<\/span>/);
  assert.match(html, /Tamara TI: <span class="preserve">Resolución exenta 1<\/span>/);
  assert.equal((html.match(/>Cobertura<\/button>/g) ?? []).length, 1, 'solo la sede no cerrada ofrece Cobertura');
  assert.match(html, /Región y cobertura/);
});

test('Reportes: filtros por región de la sede y de origen, y la exportación nombra las tres regiones', async () => {
  const reports = await vite.ssrLoadModule('/src/features/reports/Reports.tsx');
  const ctx = {catalog: {...catalog, reports: [{code: 'VOLUMEN', kind: 'VOLUMEN'}, {code: 'REQ_TOTAL', kind: 'TIEMPO'}]}, user: {id: 7, can_report_people: false, can_export: true},
    act: () => {}, reload: () => {}, go: () => {}, message: () => {}};
  const html = renderToStaticMarkup(createElement(reports.default, {ctx}));
  assert.match(html, /<span>Región de la sede<\/span><select/);
  assert.match(html, /<span>Región de origen<\/span><select[^>]*><option value="" selected="">Todas<\/option><option value="03">Atacama<\/option><option value="04">Coquimbo<\/option><option value="13">Metropolitana de Santiago<\/option><option value="SIN">Sin región declarada<\/option>/);
  assert.match(html, /tres regiones \(región de la sede, región de origen registrada y región de origen vigente\)/);
});
