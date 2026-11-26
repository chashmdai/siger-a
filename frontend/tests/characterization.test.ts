// Caracterizacion estadistica en el flujo (0.8.0, D28-D31, gate E09): bloque "stat" de los comandos de ingreso,
// distribucion y cierre, propuesta que solo se envia si se elige (decision B del GO), variables solo con listado vigente,
// motivo al corregir y cuenta de bloqueos que el dialogo de cierre puede resolver, todo desde datos del servidor.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {changed, closeState, completeness, currentChoice, fichaVars, needsReason, newRecordVars, setChoice, setReason, specials, statLists, statPayload, statProblems, valueText} from '../src/features/detail/characterizationModel.ts';

const item = (code: string, label: string, extra: Record<string, unknown> = {}) => ({code, label, parent_code: null, level: 1, selectable: true, description: null, ...extra});
const CATALOG = {
  stat_catalogs: {
    catalogs: [
      {catalog: 'TIPO_SOLICITUD', label: 'Tipo de solicitud', version: {id: 11}, items: [item('VULNERACION', 'Vulneración de derechos'), item('CONSULTA', 'Consulta u orientación', {description: 'Pide información u orientación.'})]},
      {catalog: 'TIPO_REQUIRENTE', label: 'Tipo de requirente', version: {id: 12}, items: [item('NNA', 'Niño, niña o adolescente')]},
      {catalog: 'VIA_INGRESO', label: 'Vía de ingreso', version: {id: 13}, items: [item('GRUPO_PRESENCIAL', 'Presencial', {selectable: false}), item('PRESENCIAL', 'Presencial', {parent_code: 'GRUPO_PRESENCIAL', level: 2}), item('CORREO', 'Correo', {parent_code: 'GRUPO_DIGITAL', level: 2})]},
      {catalog: 'CONTEXTO', label: 'Contexto', version: null, items: []},
    ],
    special: [{code: 'SIN_INFORMACION', label: 'Sin información'}, {code: 'NO_DECLARADO', label: 'No declarado'}, {code: 'NO_APLICA', label: 'No aplica'}],
  },
  stat_completeness: {intake: ['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO'], vulnerability: ['DERECHO'], duplicate: ['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO']},
};
const variable = (v: string, state = 'PENDIENTE', code: string | null = null, extra: Record<string, unknown> = {}) =>
  ({variable: v, label: CATALOG.stat_catalogs.catalogs.find(c => c.catalog === v)?.label ?? v, state, code, value_label: code ? 'Rótulo' : null, catalog_current: true, proposal: null, ...extra});
const CHARACTERIZATION = {revision: 2, blocked: null, can_classify: true, variables: [
  variable('TIPO_SOLICITUD', 'PENDIENTE', null, {proposal: {code: 'VULNERACION', label: 'Vulneración de derechos', rule: 'PROPUESTA_V1'}}),
  variable('TIPO_REQUIRENTE', 'INFORMADO', 'NNA'),
  variable('VIA_INGRESO', 'PENDIENTE', null, {catalog_current: false}),
]};

test('listados vigentes, estados especiales y exigibles salen del catálogo publicado por el servidor', () => {
  const lists = statLists(CATALOG);
  assert.equal(lists.TIPO_SOLICITUD.version, 11);
  assert.equal(lists.CONTEXTO.version, null);
  assert.equal(lists.TIPO_SOLICITUD.items[1].description, 'Pide información u orientación.');
  assert.deepEqual(specials(CATALOG).map(s => s.label), ['Sin información', 'No declarado', 'No aplica']);
  assert.deepEqual(completeness(CATALOG).duplicate, ['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO']);
  assert.deepEqual(completeness({}), {intake: [], vulnerability: [], duplicate: []}, 'sin datos del servidor, nada se exige');
});

test('la propuesta no se preselecciona ni se envía sola; solo al elegirla', () => {
  const vars = fichaVars(CHARACTERIZATION);
  assert.equal(currentChoice(vars[0]), '', 'una propuesta no es un valor registrado');
  assert.equal(statPayload(vars, undefined), undefined, 'sin elegir nada, no viaja el bloque');
  const chosen = setChoice(undefined, 'TIPO_SOLICITUD', 'code:VULNERACION');
  assert.deepEqual(statPayload(vars, chosen), {TIPO_SOLICITUD: {code: 'VULNERACION'}});
});

test('solo variables con listado vigente y que cambian; un valor igual al registrado no viaja', () => {
  const vars = fichaVars(CHARACTERIZATION);
  let block = setChoice(undefined, 'VIA_INGRESO', 'code:PRESENCIAL');
  assert.equal(statPayload(vars, block), undefined, 'una variable sin listado vigente nunca se envía');
  block = setChoice(block, 'TIPO_REQUIRENTE', 'code:NNA');
  assert.equal(statPayload(vars, block), undefined, 'igual al registrado: no se envía');
  block = setChoice(block, 'TIPO_SOLICITUD', 'status:SIN_INFORMACION');
  assert.deepEqual(statPayload(vars, block), {TIPO_SOLICITUD: {status: 'SIN_INFORMACION'}});
  assert.equal(setChoice(setChoice(undefined, 'TIPO_SOLICITUD', 'code:CONSULTA'), 'TIPO_SOLICITUD', ''), undefined, 'quitar la elección deja el bloque vacío');
});

test('corregir un valor informado exige motivo; completar uno pendiente no', () => {
  const vars = fichaVars(CHARACTERIZATION);
  const complete = setChoice(undefined, 'TIPO_SOLICITUD', 'code:CONSULTA');
  assert.equal(needsReason(vars, complete), false);
  assert.deepEqual(statProblems(vars, complete), []);
  const fix = setChoice(undefined, 'TIPO_REQUIRENTE', 'status:NO_DECLARADO');
  assert.equal(needsReason(vars, fix), true);
  assert.deepEqual(statProblems(vars, fix), ['Indica el motivo de la corrección.']);
  const withReason = setReason(fix, 'Se revisó el expediente');
  assert.deepEqual(statProblems(vars, withReason), []);
  assert.deepEqual(statPayload(vars, withReason), {TIPO_REQUIRENTE: {status: 'NO_DECLARADO'}, reason: 'Se revisó el expediente'});
  assert.deepEqual(changed(vars, withReason).map(v => v.variable), ['TIPO_REQUIRENTE']);
});

test('lo que el acto exige: las variables pendientes deben elegirse (un estado especial basta)', () => {
  const vars = fichaVars(CHARACTERIZATION);
  assert.deepEqual(statProblems(vars, undefined, completeness(CATALOG).intake), ['Completa tipo de solicitud.'], 'la vía sin listado vigente no se exige');
  assert.deepEqual(statProblems(vars, setChoice(undefined, 'TIPO_SOLICITUD', 'status:SIN_INFORMACION'), completeness(CATALOG).intake), []);
});

test('registro nuevo: variables del ingreso con listado vigente; la vía derivada del canal queda informada', () => {
  const vars = newRecordVars(CATALOG, 'PRESENCIAL');
  assert.deepEqual(vars.map(v => [v.variable, v.state, v.code]), [['TIPO_SOLICITUD', 'PENDIENTE', ''], ['TIPO_REQUIRENTE', 'PENDIENTE', ''], ['VIA_INGRESO', 'INFORMADO', 'PRESENCIAL']]);
  assert.equal(newRecordVars(CATALOG, 'TELEFONO')[2].state, 'PENDIENTE', 'sin un ítem del canal, la vía queda pendiente');
  const fix = setChoice(undefined, 'VIA_INGRESO', 'code:CORREO');
  assert.deepEqual(statProblems(vars, fix), ['Indica el motivo de la corrección.'], 'cambiar la vía derivada es una corrección');
  assert.deepEqual(newRecordVars({}, 'PRESENCIAL'), [], 'sin listados vigentes no hay bloque');
});

test('valor visible: rótulo, estado especial o Pendiente; nunca el código', () => {
  const special = specials(CATALOG);
  assert.equal(valueText({state: 'INFORMADO', value_label: 'Consulta u orientación'}, special), 'Consulta u orientación');
  assert.equal(valueText({state: 'SIN_INFORMACION', value_label: ''}, special), 'Sin información');
  assert.equal(valueText({state: 'PENDIENTE', value_label: ''}, special), 'Pendiente');
  assert.equal(valueText({state: 'INFORMADO', value_label: ''}, special), 'Valor registrado con un listado anterior');
});

test('cierre: el diálogo resuelve solo las variables exigibles pendientes que informa la ficha', () => {
  const vars = fichaVars(CHARACTERIZATION), required = completeness(CATALOG).intake;
  assert.deepEqual(closeState([], required, vars), {resolvable: [vars[0]], enabled: true});
  assert.equal(closeState(['Completa tipo de solicitud.'], required, vars).enabled, true, 'todos los bloqueos son resolubles en el acto');
  assert.equal(closeState(['Completa tipo de solicitud.', '1 Casos abiertos.'], required, vars).enabled, false, 'otro bloqueo no se resuelve en el diálogo');
  assert.equal(closeState(['1 Casos abiertos.'], required, fichaVars({variables: [variable('TIPO_SOLICITUD', 'INFORMADO', 'CONSULTA')]})).enabled, false);
  assert.equal(closeState(['Completa tipo de solicitud.'], [], vars).enabled, false, 'sin variables exigibles publicadas, nada se resuelve en el acto');
});
