// Region de origen en la ficha de primera atencion (lib/regions.ts, D21, gate T03): la registrada se muestra solo
// cuando el servidor informa que una version posterior la corrigio. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {correctedOrigin, NO_REGION_DECLARED, regionText} from '../src/lib/regions.ts';

const REGIONS = [{code: '03', name: 'Atacama'}, {code: '04', name: 'Coquimbo'}];

test('región registrada y vigente solo cuando difieren (corrected del servidor)', () => {
  assert.deepEqual(correctedOrigin({registered: '03', current: '04', corrected: true}, REGIONS), {registered: 'Atacama (03)', current: 'Coquimbo (04)'});
  // Quitar la region de la ficha despues de registrarla tambien es correccion (T02).
  assert.deepEqual(correctedOrigin({registered: '03', current: null, corrected: true}, REGIONS), {registered: 'Atacama (03)', current: NO_REGION_DECLARED});
  // Completar una ficha sin region no es correccion; tampoco la region igual ni la ficha no legible.
  assert.equal(correctedOrigin({registered: '03', current: '03', corrected: false}, REGIONS), null);
  assert.equal(correctedOrigin({registered: null, current: null, corrected: false}, REGIONS), null);
  assert.equal(correctedOrigin({}, REGIONS), null);
  assert.equal(correctedOrigin(undefined, REGIONS), null);
});

test('nombre de la región con su código; sin región, sin región declarada', () => {
  assert.equal(regionText(REGIONS, '04'), 'Coquimbo (04)');
  assert.equal(regionText(REGIONS, '99'), '99 (99)');
  assert.equal(regionText(REGIONS, null), 'Sin región declarada');
  assert.equal(regionText(REGIONS, ''), 'Sin región declarada');
});
