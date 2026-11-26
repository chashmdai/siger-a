// Actuaciones tipificadas y compromisos (0.8.0, D36, gate E09), rotulos por version de los codigos registrados
// (code_labels, pendiente de E04 y E06) y campo propio del dialogo de comandos (decision A del GO: sus valores entran en la
// revision previa y en el envio, sin cambiar los demas campos). node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {actionList, institutionsProblem, offersActionType, rectifyInitial} from '../src/features/detail/notesModel.ts';
import {codeLabel, relationLines, RETIRED_CODE_TEXT, registeredLabel, statCodes, withCodeLabels} from '../src/lib/person/personModel.ts';
import {payloadOf, signatureOf, submitStep} from '../src/lib/actionData.ts';

const ACTIONS = {stat_catalogs: {catalogs: [{catalog: 'TIPO_ACTUACION', label: 'Tipo de actuación', version: {id: 21}, items: [
  {code: 'OFICIO', label: 'Oficio', selectable: true, description: 'Solo si el propósito no corresponde a otro tipo.'}]}]}};

test('tipo de actuación: con listado vigente o con un tipo ya registrado', () => {
  assert.equal(offersActionType(actionList(ACTIONS)), true);
  assert.equal(offersActionType(actionList({})), false, 'sin listado vigente no se ofrece: la clave no viaja');
  assert.equal(offersActionType(actionList({}), 'QUERELLA'), true, 'un tipo registrado se puede conservar o quitar');
  assert.equal(actionList(ACTIONS).items[0].description, 'Solo si el propósito no corresponde a otro tipo.');
});

test('rectificar parte del tipo y las instituciones de la constancia (confirmar sin tocarlos los conserva)', () => {
  const note = {id: 4, action_type: 'DENUNCIA_PENAL', action_label: 'Denuncia penal',
    institutions: [{name: 'Fiscalía Local', type_code: 'JUSTICIA', type_version: 7, role: 'Recibe la denuncia', region_code: '13', commune: 'Santiago'}]};
  const initial = rectifyInitial(note);
  assert.equal(initial.action_type, 'DENUNCIA_PENAL');
  assert.deepEqual(initial.institutions, [{name: 'Fiscalía Local', type_code: 'JUSTICIA', role: 'Recibe la denuncia', region_code: '13', commune: 'Santiago'}], 'sin la versión derivada');
  assert.deepEqual(rectifyInitial({}), {action_type: '', institutions: []});
});

test('instituciones: el primer problema con su número', () => {
  assert.equal(institutionsProblem([]), null);
  assert.equal(institutionsProblem([{name: 'Liceo', type_code: '', role: '', region_code: '', commune: ''}, {name: '', type_code: '', role: '', region_code: '', commune: ''}]), 'Institución 2: Indica el nombre de la institución.');
});

test('code_labels: un código de un listado retirado se muestra con el rótulo con que se registró', () => {
  const codes = withCodeLabels(statCodes({}), {'3:MADRE': 'Madre', '4:EDUCACION': 'Establecimiento educativo'});
  assert.equal(codeLabel(codes.relationship, 'MADRE'), 'Madre');
  assert.equal(registeredLabel(codes.institution, 'EDUCACION'), 'Establecimiento educativo');
  assert.deepEqual(relationLines(codes.relationship, 'MADRE', 'mamá'), ['Madre', 'mamá']);
  assert.equal(codeLabel(statCodes({}).relationship, 'MADRE'), RETIRED_CODE_TEXT, 'sin rótulo conocido, el aviso sin el código');
  assert.equal(codeLabel(withCodeLabels(statCodes({}), {'3:X': 'Uno', '5:X': 'Otro'}).relationship, 'X'), RETIRED_CODE_TEXT, 'rótulos distintos: no se adivina');
});

test('campo propio: su valor entra en el envío y en la huella de la revisión; undefined no viaja', () => {
  const custom = {key: 'stat', label: 'Caracterización', type: 'custom' as const};
  const reason = {key: 'reason', label: 'Fundamento', type: 'textarea' as const};
  const value = {TIPO_SOLICITUD: {code: 'CONSULTA'}};
  assert.deepEqual(payloadOf([custom, reason], {stat: value, reason: 'x'}), {stat: value, reason: 'x'});
  assert.notEqual(signatureOf([custom], {stat: value}), signatureOf([custom], {stat: {TIPO_SOLICITUD: {code: 'VULNERACION'}}}), 'cambiar el valor invalida la revisión');
  assert.equal(JSON.stringify(payloadOf([custom, reason], {stat: undefined, reason: 'x'})), '{"reason":"x"}');
  const step = submitStep(payloadOf([custom], {stat: value}), true, {extra: {regiones_sin_sede: []}}, false);
  assert.deepEqual(step, {step: 'send', payload: {stat: value, regiones_sin_sede: []}}, 'con revisión previa, viaja junto a sus datos extra');
});
