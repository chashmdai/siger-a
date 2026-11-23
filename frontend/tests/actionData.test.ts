// Dialogo de comandos (lib/actionData.ts, gate T03): datos que se envian, huella de la revision y paso del envio con
// revision previa (efecto territorial). Un dialogo sin revision envia como antes. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {payloadOf, signatureOf, submitStep} from '../src/lib/actionData.ts';
import {destinations, withLevel, withRegion} from '../src/lib/destination.ts';

const REGIONS = [{code: '03', name: 'Atacama'}, {code: '04', name: 'Coquimbo'}, {code: '07', name: 'Maule'}];
const checklist = {key: 'regions', label: 'Regiones', type: 'checklist' as const, options: REGIONS.map(r => ({value: r.code, label: r.name}))};
const reason = {key: 'reason', label: 'Fundamento', type: 'textarea' as const, required: true};

test('lista marcada en el orden de sus opciones; sin marcar, lista vacía', () => {
  assert.deepEqual(payloadOf([checklist, reason], {regions: ['07', '03'], reason: 'x'}), {regions: ['03', '07'], reason: 'x'});
  assert.deepEqual(payloadOf([checklist, reason], {regions: [], reason: 'x'}), {regions: [], reason: 'x'});
  assert.deepEqual(payloadOf([checklist], {}), {regions: []});
  assert.deepEqual(payloadOf([checklist], {regions: ['99', '04']}), {regions: ['04']}, 'solo valores de sus opciones');
});

test('fechas y destino se envían como antes de la revisión', () => {
  const exact = Date.parse('2026-11-20T15:00:30.123Z');
  const date = {key: 'opens_at', label: 'Apertura', type: 'datetime-local' as const, value: '2026-11-20T12:00', exact};
  assert.deepEqual(payloadOf([date], {opens_at: '2026-11-20T12:00'}), {opens_at: new Date(exact).toISOString()}, 'sin cambiar, el instante exacto');
  assert.deepEqual(payloadOf([date], {opens_at: ''}), {opens_at: null});
  const catalog = {scopes: [{id: 4, code: 'REG-COQ', name: 'Sede Coquimbo', level: 'REGIONAL', region_code: '04', coverage: ['03'], receives: ['SOL']}], regions: REGIONS, units: []};
  const d = destinations(catalog, 'SOL');
  const field = {key: 'destination', label: 'Destino', type: 'destination' as const, destination: {destinations: d}};
  assert.deepEqual(payloadOf([field, reason], {destination: withRegion(d, withLevel('REGIONAL'), '03'), reason: 'x'}), {scope_id: 4, reason: 'x'});
});

test('huella: cambia con los datos y no falla con una fecha inválida', () => {
  assert.equal(signatureOf([checklist], {regions: ['03']}), signatureOf([checklist], {regions: ['03']}));
  assert.notEqual(signatureOf([checklist], {regions: ['03']}), signatureOf([checklist], {regions: ['03', '07']}));
  assert.equal(signatureOf([checklist], {regions: ['07', '03']}), signatureOf([checklist], {regions: ['03', '07']}), 'el orden de marcado no cambia la orden');
  const date = {key: 'opens_at', label: 'Apertura', type: 'datetime-local' as const};
  assert.match(signatureOf([date], {opens_at: 'no es fecha'}), /^invalido:/);
});

test('paso del envío: sin revisión envía; con revisión la pide, exige la casilla y envía con sus datos extra', () => {
  const payload = {regions: ['04'], reason: 'x'};
  assert.deepEqual(submitStep(payload, false, null, false), {step: 'send', payload}, 'un diálogo sin revisión no cambia');
  assert.deepEqual(submitStep(payload, true, null, false), {step: 'review'}, 'sin revisión vigente para estos datos: se pide, no se envía');
  const unattended = {extra: {regiones_sin_sede: ['03']}, confirm: 'Confirmo que Atacama (03) queda sin atención…'};
  assert.deepEqual(submitStep(payload, true, unattended, false), {step: 'confirm'}, 'sin la casilla no se envía');
  assert.deepEqual(submitStep(payload, true, unattended, true), {step: 'send', payload: {regions: ['04'], reason: 'x', regiones_sin_sede: ['03']}});
  // Sin regiones afectadas: la revision no trae casilla ni datos extra y se envia tal cual.
  assert.deepEqual(submitStep(payload, true, {extra: {}}, false), {step: 'send', payload});
  assert.deepEqual(submitStep(payload, true, {}, false), {step: 'send', payload});
});
