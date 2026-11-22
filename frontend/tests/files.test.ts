// Estado visible de un documento (H04c): la liberacion por analisis en origen se distingue de un analisis local.
// node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fileStatus} from '../src/features/detail/files.ts';

test('cuarentena, reserva y disponible', () => {
  assert.deepEqual(fileStatus({quarantined: true, release_basis: 'ORIGEN'}), {label: 'En cuarentena', tone: 'warn'});
  assert.equal(fileStatus({quarantined: false, reserved: true}).label, 'Reservado');
  assert.equal(fileStatus({quarantined: false}).label, 'Disponible');
  assert.equal(fileStatus({quarantined: false, release_basis: null}).label, 'Disponible');
});

test('liberado por analisis en origen', () => {
  assert.deepEqual(fileStatus({quarantined: false, release_basis: 'ORIGEN'}), {label: 'Disponible · análisis en origen', tone: ''});
  assert.equal(fileStatus({quarantined: false, release_basis: 'OTRO'}).label, 'Disponible');
});
