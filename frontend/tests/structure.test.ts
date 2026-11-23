// Panel Estructura (R03b): orden y agrupacion de ambitos, acciones por estado, marca "Sin region", volumen y
// funcionarios del ambito. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {actionsFor, canEditCoverage, confirmationOf, coverageOf, coverageOptions, effectLines, membersOf, missingRegion, periodsOf, previewBody, regionChanged, regionLabel, territorialOrders,
  scopeRow, structureGroups, volume} from '../src/features/admin/structureModel.ts';
import {grantOf} from '../src/features/admin/profile.ts';

// Orden del Formulario tal como lo publica catalog.regions (de norte a sur).
const REGIONS = [{code: '15', name: 'Arica y Parinacota'}, {code: '05', name: 'Valparaíso'}, {code: '13', name: 'Metropolitana de Santiago'}, {code: '08', name: 'Biobío'}, {code: '12', name: 'Magallanes'}];
const rows = [
  {id: 1, code: 'UPR', level: 'CENTRAL', state: 'ACTIVA', region_code: null},
  {id: 2, code: 'REG-MAG', level: 'REGIONAL', state: 'ACTIVA', region_code: '12', close_blockers: ['1 REQ abiertos', '2 funciones vigentes o programadas']},
  {id: 3, code: 'REG-VIEJA', level: 'REGIONAL', state: 'ACTIVA', region_code: null, close_blockers: []},
  {id: 4, code: 'REG-BIO', level: 'REGIONAL', state: 'PROYECTADA', region_code: '08', close_blockers: []},
  {id: 5, code: 'REG-ARI', level: 'REGIONAL', state: 'CERRADA', region_code: '15', close_blockers: []},
  {id: 6, code: 'UPD', level: 'CENTRAL', state: 'ACTIVA', region_code: null},
  {id: 7, code: 'REG-BIO-2', level: 'REGIONAL', state: 'PROYECTADA', region_code: null, close_blockers: []},
];

test('nivel central en el orden del servidor y sedes de norte a sur, sin region al final', () => {
  const {central, sites} = structureGroups(rows, REGIONS);
  assert.deepEqual(central.map(r => r.code), ['UPR', 'UPD']);
  assert.deepEqual(sites.map(r => r.code), ['REG-ARI', 'REG-BIO', 'REG-MAG', 'REG-BIO-2', 'REG-VIEJA']);
  assert.deepEqual(sites.filter(missingRegion).map(r => r.code), ['REG-BIO-2', 'REG-VIEJA']);
  assert.ok(!missingRegion(scopeRow(rows[0])), 'el nivel central no tiene region y no se marca');
});

test('acciones por estado: activar la proyectada con region, cerrar la activa sin bloqueos', () => {
  const by = (code: string) => actionsFor(scopeRow(rows.find(r => r.code === code)!));
  assert.deepEqual(by('UPR'), {edit: true, activate: false, activateBlocked: '', close: false, closeBlocked: []});
  assert.deepEqual(by('REG-MAG').closeBlocked, ['1 REQ abiertos', '2 funciones vigentes o programadas']);
  assert.ok(by('REG-MAG').close && !by('REG-MAG').activate);
  assert.deepEqual(by('REG-VIEJA').closeBlocked, []);
  assert.equal(by('REG-VIEJA').activateBlocked, '', 'una sede activa sin region no muestra la nota de activacion');
  assert.ok(by('REG-BIO').activate && by('REG-BIO').activateBlocked === '' && !by('REG-BIO').close);
  assert.match(by('REG-BIO-2').activateBlocked, /Asigna la región/);
  const closed = by('REG-ARI');
  assert.ok(!closed.edit && !closed.activate && !closed.close, 'una sede cerrada no se edita, activa ni cierra');
});

test('volumen abierto en texto', () => {
  assert.equal(volume({open_req: 2, open_cases: 1, pending_sol: 0}), '2 REQ · 1 Caso · 0 SOL');
  assert.equal(volume({}), '0 REQ · 0 Casos · 0 SOL');
});

test('funcionarios: funciones vigentes o programadas en el ambito o adscripcion principal', () => {
  const NOW = Date.parse('2026-11-20T15:00:00Z'), H = 3_600_000;
  const users = [{id: 1, name: 'Zoe', home_scope: 9}, {id: 2, name: 'Ana', home_scope: 2}, {id: 3, name: 'Beto', home_scope: 9}, {id: 4, name: 'Carla', home_scope: 9}];
  const grants = [
    {id: 1, user_id: 1, role_code: 'DIRECCION', scope_id: 2, valid_from: NOW - H, valid_until: null, revoked_at: null},
    {id: 2, user_id: 1, role_code: 'PROFESIONAL', scope_id: 2, valid_from: NOW + H, valid_until: null, revoked_at: null},
    {id: 3, user_id: 3, role_code: 'PROFESIONAL', scope_id: 2, valid_from: NOW - 9 * H, valid_until: null, revoked_at: NOW - H},
    {id: 4, user_id: 4, role_code: 'LECTURA', scope_id: 2, valid_from: NOW - 9 * H, valid_until: NOW - H, revoked_at: null},
  ].map(grantOf);
  const members = membersOf(2, users, grants, NOW);
  assert.deepEqual(members.map(m => [m.user.name, m.roles, m.home]), [['Ana', [], true], ['Zoe', ['DIRECCION', 'PROFESIONAL'], false]]);
});

// --- Cobertura territorial (0.7.0, D18-D19; gate T03) ---------------------------------------------------------------
// Las vistas previas imitan POST /api/administration/territorial-effect (Territory.effect y Territory.unattended).
const ALL_REGIONS = [{code: '03', name: 'Atacama'}, {code: '04', name: 'Coquimbo'}, {code: '13', name: 'Metropolitana de Santiago'}, {code: '07', name: 'Maule'}, {code: '14', name: 'Los Ríos'}];

test('cobertura de cada sede: editable salvo cerrada o nivel central, sin su región física entre las opciones', () => {
  const coq = scopeRow({id: 9, code: 'REG-COQ', level: 'REGIONAL', state: 'ACTIVA', region_code: '04', coverage: ['03']});
  assert.deepEqual(coverageOf(coq), ['03']);
  assert.deepEqual(coverageOf(scopeRow(rows[1])), [], 'sin cobertura publicada: vacía');
  assert.deepEqual(coverageOptions(coq, ALL_REGIONS).map(r => r.code), ['03', '13', '07', '14'], 'la región física (04) no se ofrece');
  assert.ok(canEditCoverage(coq));
  assert.ok(canEditCoverage(scopeRow(rows[3])), 'una sede proyectada prepara su cobertura');
  assert.ok(!canEditCoverage(scopeRow(rows[4])), 'una sede cerrada no tiene cobertura');
  assert.ok(!canEditCoverage(scopeRow(rows[0])), 'el nivel central se declara aparte');
  assert.equal(regionLabel(ALL_REGIONS, '03'), 'Atacama (03)');
  assert.equal(regionLabel(ALL_REGIONS, null), 'Sin región');
});

test('vista previa de las cuatro órdenes con las claves del comando', () => {
  assert.deepEqual(previewBody('CIERRE', 9, {reason: 'x'}), {kind: 'CIERRE', scope_id: 9});
  assert.deepEqual(previewBody('COBERTURA', 9, {regions: ['03', '07'], reason: 'x'}), {kind: 'COBERTURA', scope_id: 9, regions: ['03', '07']});
  assert.deepEqual(previewBody('COBERTURA', 9, {regions: [], reason: 'x'}), {kind: 'COBERTURA', scope_id: 9, regions: []}, 'quitar toda la cobertura también se revisa');
  assert.deepEqual(previewBody('REGION', 9, {name: 'Sede', region_code: '03', reason: 'x'}), {kind: 'REGION', scope_id: 9, region_code: '03'});
  assert.deepEqual(previewBody('NIVEL_CENTRAL', null, {regions: ['13'], reason: 'x'}), {kind: 'NIVEL_CENTRAL', regions: ['13']});
  // Editar una sede se revisa solo si cambia su region fisica.
  const coq = scopeRow({id: 9, code: 'REG-COQ', level: 'REGIONAL', state: 'ACTIVA', region_code: '04'});
  assert.ok(!regionChanged(coq, {name: 'Otro nombre', region_code: '04'}));
  assert.ok(regionChanged(coq, {region_code: '03'}));
  assert.ok(!regionChanged(scopeRow(rows[6]), {region_code: ''}), 'una sede sin región que no la asigna no cambia');
  assert.ok(regionChanged(scopeRow(rows[6]), {region_code: '08'}));
  assert.ok(!regionChanged(scopeRow(rows[0]), {region_code: '03'}), 'el nivel central no tiene región física');
});

test('efecto territorial por región: quién la atenderá, sin atención y proyectadas solo como dato', () => {
  const lines = effectLines([
    {region: '04', attended_by: ['REG-COQ2'], central: false, projected: [], unattended: false},
    {region: '03', attended_by: ['REG-ATA', 'REG-VAL'], central: true, projected: [], unattended: false},
    {region: '14', attended_by: [], central: false, projected: ['REG-RIO'], unattended: true},
    {region: '13', attended_by: [], central: true, projected: ['REG-A', 'REG-B'], unattended: false},
  ], ALL_REGIONS);
  assert.deepEqual(lines.map(l => [l.label, l.text, l.unattended]), [
    ['Coquimbo (04)', 'Sigue atendida por REG-COQ2.', false],
    ['Atacama (03)', 'Sigue atendida por REG-ATA, REG-VAL y el nivel central.', false],
    ['Los Ríos (14)', 'Queda sin atención. La sede proyectada REG-RIO la atenderá al activarse.', true],
    ['Metropolitana de Santiago (13)', 'Sigue atendida por el nivel central. Las sedes proyectadas REG-A y REG-B la atenderán al activarse.', false],
  ]);
  assert.deepEqual(effectLines([], ALL_REGIONS), []);
});

test('confirmación: sin regiones afectadas no se envía nada; con regiones, la lista exacta del servidor y una casilla que las nombra todas', () => {
  // Cerrar o cambiar cobertura sin dejar regiones sin atencion: el servidor responde 409 a una lista de mas.
  for (const preview of [{effect: [], regiones_sin_sede: []}, {effect: [{region: '04'}]}]) {
    const c = confirmationOf(preview, ALL_REGIONS);
    assert.deepEqual(c.extra, {});
    assert.equal(c.confirm, undefined);
    assert.deepEqual(c.unattended, []);
  }
  // Escenario 7 (CIERRE) y COBERTURA: el orden es el del servidor (fisica primero), nunca se reordena.
  const c = confirmationOf({regiones_sin_sede: ['14', '03']}, ALL_REGIONS);
  assert.deepEqual(c.extra, {regiones_sin_sede: ['14', '03']});
  assert.deepEqual(c.unattended, ['14', '03']);
  assert.match(c.confirm ?? '', /^Confirmo que Los Ríos \(14\), Atacama \(03\) quedan sin atención/);
  assert.match(c.confirm ?? '', /el fundamento registra la decisión institucional/);
  // NIVEL_CENTRAL: retirar 13 del nivel central sin sede que la atienda.
  const one = confirmationOf({effect: [{region: '13', attended_by: [], central: false, projected: [], unattended: true}], regiones_sin_sede: ['13']}, ALL_REGIONS);
  assert.deepEqual(one.extra, {regiones_sin_sede: ['13']});
  assert.match(one.confirm ?? '', /^Confirmo que Metropolitana de Santiago \(13\) queda sin atención/);
});

test('historia de cobertura: periodos en el orden del servidor, vigente sin término', () => {
  const periods = periodsOf([
    {id: 5, region_code: '03', from_ms: 300, from_by: 2, from_basis: 'Resolución 2', until_ms: null, until_by: null, until_basis: null},
    {id: 2, region_code: '03', from_ms: 100, from_by: 1, from_basis: 'Resolución 1', until_ms: 200, until_by: 2, until_basis: 'Apertura de sede propia'},
  ], ALL_REGIONS);
  assert.deepEqual(periods.map(p => [p.id, p.label, p.open, p.from, p.until, p.untilBy, p.untilBasis]), [
    [5, 'Atacama (03)', true, 300, null, null, ''],
    [2, 'Atacama (03)', false, 100, 200, 2, 'Apertura de sede propia'],
  ]);
});

test('las órdenes con efecto territorial pasan por la revisión: CIERRE, COBERTURA, REGION (solo si cambia) y NIVEL_CENTRAL', async () => {
  const asked: [string, number | null, Record<string, unknown>][] = [];
  const reviewer = (kind: string, scope: number | null) => async (payload: Record<string, unknown>) => {asked.push([kind, scope, payload]); return {content: kind};};
  const failed = () => {};
  const reason = {key: 'reason', label: 'Fundamento', type: 'textarea' as const, required: true};
  const orders = territorialOrders({regions: ALL_REGIONS, central: {regions: ['13'], revision: 6}, reason, reviewer, failed});
  const coq = scopeRow({id: 9, code: 'REG-COQ', name: 'Sede Coquimbo', level: 'REGIONAL', state: 'ACTIVA', region_code: '04', coverage: ['03'], revision: 4});

  const close = orders.close(coq);
  assert.deepEqual([close.type, close.id, close.revision], ['SCOPE_CLOSE', 9, 4]);
  await close.review!({reason: 'x'});
  const coverage = orders.coverage(coq);
  assert.deepEqual([coverage.type, coverage.id, coverage.revision], ['SCOPE_COVERAGE', 9, 4]);
  assert.deepEqual(coverage.fields[0].value, ['03'], 'parte de la cobertura vigente');
  assert.deepEqual(coverage.fields[0].options?.map(o => o.value), ['03', '13', '07', '14'], 'sin la región física');
  await coverage.review!({regions: [], reason: 'x'});
  const central = orders.central();
  assert.deepEqual([central.type, central.id, central.revision], ['CENTRAL_COVERAGE', 0, 6]);
  assert.deepEqual(central.fields[0].value, ['13']);
  await central.review!({regions: [], reason: 'x'});
  const edit = orders.edit(coq);
  assert.equal(await edit.review!({name: 'Sede Coquimbo', region_code: '04', reason: 'x'}), null, 'sin cambio de región no hay revisión');
  await edit.review!({name: 'Sede Coquimbo', region_code: '03', reason: 'x'});
  assert.deepEqual(asked.map(([kind, scope]) => [kind, scope]), [['CIERRE', 9], ['COBERTURA', 9], ['NIVEL_CENTRAL', null], ['REGION', 9]]);
  for (const order of [close, coverage, central, edit]) assert.equal(order.failed, failed, order.type + ': un rechazo recarga el panel');
  // Un ambito del nivel central se edita sin revision territorial (no tiene region fisica).
  assert.equal(orders.edit(scopeRow(rows[0])).review, undefined);
});
