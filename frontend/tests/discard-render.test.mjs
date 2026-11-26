import {after, before, test} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {Children, createElement, isValidElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {createServer} from 'vite';

let vite, ComDetail, SolDetail, Detail, ActionDialog;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)),
    server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  ({ComDetail} = await vite.ssrLoadModule('/src/features/detail/ComDetail.tsx'));
  ({SolDetail} = await vite.ssrLoadModule('/src/features/detail/SolDetail.tsx'));
  ({default: Detail} = await vite.ssrLoadModule('/src/features/detail/Detail.tsx'));
  ({ActionDialog} = await vite.ssrLoadModule('/src/components/ActionDialog.tsx'));
});
after(async () => {await vite?.close();});

const typeOfRequest = {variable: 'TIPO_SOLICITUD', label: 'Tipo de solicitud', state: 'PENDIENTE', catalog_current: true};
const catalog = {
  regions: [], people: [], scopes: [],
  stat_catalogs: {catalogs: [{catalog: 'TIPO_SOLICITUD', label: 'Tipo de solicitud', version: {id: 1},
    items: [{code: 'CONSULTA', label: 'Consulta', selectable: true}]}]},
  stat_completeness: {duplicate: ['TIPO_SOLICITUD']},
};
const com = {id: 1, revision: 3, state: 'NUEVA', origin_scope: 2, folio: 'COM-000001',
  characterization: {variables: [typeOfRequest]}, discard_blockers: ['Completa el tipo de solicitud.']};
const sol = {id: 5, revision: 2, communication_id: 1, destination_scope: 2,
  communication: {id: 1, folio: com.folio}, state: 'PENDIENTE'};
const ctx = {user: {id: 8, name: 'Persona de ensayo', grants: []}, catalog, go() {}, reload() {}, message() {}};

function* buttons(tree) {
  for (const node of Children.toArray(tree)) {
    if (!isValidElement(node)) continue;
    if (node.type === 'button') yield node;
    yield* buttons(node.props.children);
  }
}

async function open(Component, row, buttonLabel) {
  let action;
  const screen = Component({r: row, ctx: {...ctx, act: value => {action = value;}}});
  const button = [...buttons(screen.props.main)].find(n => Children.toArray(n.props.children).includes(buttonLabel));
  assert.ok(button, buttonLabel);
  await button.props.onClick();
  // SOL lee la caracterización de la COM antes de abrir el diálogo.
  if (!action) await new Promise(resolve => setImmediate(resolve));
  assert.ok(action, buttonLabel);
  return {action, html: renderToStaticMarkup(createElement(ActionDialog, {action, close() {}, completed() {}}))};
}

test('distribución, asunción, redirección y redistribución abren sin campo de fundamento', async () => {
  for (const [Component, row, label, command] of [
    [ComDetail, {...com, can_route: true}, 'Distribuir SOL', 'COM_ROUTE'],
    [ComDetail, {...com, can_assume: true}, 'Asumir como jefatura', 'COM_ASSUME'],
    [SolDetail, {...sol, can_decide: true}, 'Redirigir', 'SOL_ROUTE'],
    [SolDetail, {...sol, can_redistribute: true, state: 'DEVUELTA'}, 'Redistribuir', 'SOL_ROUTE'],
    [SolDetail, {...sol, can_decide: true}, 'Asumir y asignar atención', 'SOL_ASSUME'],
  ]) {
    const {action, html} = await open(Component, row, label);
    assert.equal(action.type, command);
    assert.ok(!action.fields.some(f => f.key === 'reason'));
    assert.doesNotMatch(html, /Fundamento|Observación|<textarea/);
  }
});

test('vincular y devolver ofrecen una observación opcional', async () => {
  for (const [Component, row, label, command] of [
    [ComDetail, {...com, can_route: true, can_link: true}, 'Proponer antecedente', 'COM_LINK'],
    [SolDetail, {...sol, can_decide: true}, 'Devolver a Distribución', 'SOL_RETURN'],
  ]) {
    const {action, html} = await open(Component, row, label);
    assert.equal(action.type, command);
    assert.equal(action.fields.find(f => f.key === 'reason').required, false);
    assert.match(html, /Observación \(opcional\)/);
    assert.match(html, /<textarea[^>]+>/);
    assert.doesNotMatch(html, /<textarea[^>]+required/);
  }
});

test('desestimar respeta la facultad, pide fundamento y permite resolver la caracterización', async () => {
  const reader = renderToStaticMarkup(createElement(ComDetail, {r: com, ctx}));
  assert.doesNotMatch(reader, /Desestimar ingreso/);
  const {action, html} = await open(ComDetail, {...com, can_discard: true}, 'Desestimar ingreso');
  assert.equal(action.type, 'COM_DISCARD');
  assert.equal(action.revision, 3);
  assert.match(html, /Inválida/);
  assert.match(html, /Duplicada/);
  assert.match(html, /La decisión es definitiva/);
  assert.match(html, /Fundamento/);
  assert.match(html, /<textarea[^>]+required/);
  assert.match(html, /Caracterización del ingreso/);
  const stat = action.fields.find(f => f.key === 'stat');
  assert.equal(stat.validate(undefined, {disposition: 'INVALIDA'}), null);
  assert.ok(stat.validate(undefined, {disposition: 'DUPLICADA'}));
  assert.equal(stat.validate({TIPO_SOLICITUD: {code: 'CONSULTA'}}, {disposition: 'DUPLICADA'}), null);
});

test('descartar una SOL conserva el fundamento obligatorio', async t => {
  globalThis.window = new EventTarget();
  t.after(() => {delete globalThis.window;});
  t.mock.method(globalThis, 'fetch', async () => Response.json(com));
  const {action, html} = await open(SolDetail, {...sol, can_decide: true}, 'Inválida o duplicada');
  assert.equal(action.type, 'SOL_DISCARD');
  assert.match(html, /Fundamento/);
  assert.match(html, /<textarea[^>]+required/);
});

test('fichas e historia omiten observaciones vacías y muestran el fundamento de la desestimación', () => {
  const empty = renderToStaticMarkup(createElement(SolDetail, {r: {...sol, classification: ''}, ctx}));
  const withText = renderToStaticMarkup(createElement(SolDetail, {r: {...sol, classification: 'Observación sintética'}, ctx}));
  assert.doesNotMatch(empty, /<dt>Clasificación<\/dt>/);
  assert.match(withText, /<dt>Clasificación<\/dt>.*Observación sintética/);
  const html = renderToStaticMarkup(createElement(Detail, {kind: 'COM', row: {...com, state: 'INVALIDA', history: [
    {at_ms: 1234, action: 'COM_INVALIDA', actor: 'Persona', note: 'Motivo sintético'},
    {at_ms: 1233, action: 'COM_REGISTRADA', actor: 'Persona', note: ' '},
  ]}, ctx}));
  assert.match(html, /Ingreso desestimado como inválido/);
  assert.match(html, /Motivo sintético/);
  assert.equal((html.match(/timeline-body preserve/g) ?? []).length, 1);
});
