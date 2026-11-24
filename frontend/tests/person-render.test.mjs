// Componentes de la normalizacion integral (0.8.0, D33, gate E04) renderizados en servidor (Vite ssrLoadModule +
// react-dom/server, sin dependencias nuevas): ficha 1.2 (modalidad NNA con los datos principales de antes a la vista y el
// resto en "Más datos"), personas, hechos e instituciones, ingreso manual (lo minimo sin campos obligatorios nuevos;
// ingreso institucional sin persona natural), ficha de ingreso en la COM y vista imprimible, sin codigos visibles.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

let vite, sections, schema, ficha, person, intakeScreen, fields, intakeModel, panel, preview;
before(async () => {
  vite = await createServer({root: fileURLToPath(new URL('..', import.meta.url)), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  sections = await vite.ssrLoadModule('/src/features/attention/FormSections.tsx');
  schema = await vite.ssrLoadModule('/src/features/attention/schema.ts');
  ficha = await vite.ssrLoadModule('/src/features/attention/fichaModel.ts');
  person = await vite.ssrLoadModule('/src/lib/person/personModel.ts');
  intakeScreen = await vite.ssrLoadModule('/src/features/intake/Intake.tsx');
  fields = await vite.ssrLoadModule('/src/features/intake/IntakeFields.tsx');
  intakeModel = await vite.ssrLoadModule('/src/features/intake/intakeModel.ts');
  panel = await vite.ssrLoadModule('/src/features/intake/IntakeRecordPanel.tsx');
  preview = await vite.ssrLoadModule('/src/features/attention/Preview.tsx');
});
after(async () => {await vite?.close();});

const CATALOG = {regions: [], people: [{id: 5, name: 'Rosa Registro'}], scopes: [{id: 3, code: 'UPR', name: 'Unidad de Protección', enabled: true}],
  stat_catalogs: {catalogs: [{catalog: 'RELACION', version: {id: 3}, items: [{code: 'MADRE', label: 'Madre', selectable: true, level: 1}, {code: 'OTRO_FAMILIAR', label: 'Otro familiar', selectable: true, level: 1}]},
    {catalog: 'TIPO_INSTITUCION', version: {id: 4}, items: [{code: 'EDUCACION', label: 'Establecimiento educativo', selectable: true, level: 1}]}]}};
const ctx = {user: {id: 5, name: 'Rosa Registro', home_scope: 3, grants: [{role: 'REGISTRO', scope: 3}]}, catalog: CATALOG, act: () => {}, go: () => {}, reload: () => {}, message: () => {}};
/** Texto visible: sin etiquetas ni atributos (los codigos pueden ir en value, nunca a la vista). */
const visible = html => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
const CODES = /\b(MADRE|OTRO_FAMILIAR|EDUCACION|DOCUMENTO_EXTRANJERO|NO_DISPONIBLE|REQUIRENTE|OTRA_PERSONA|ACOMPANANTE)\b/;

function render(component, form, saved = {}, catalog = CATALOG) {
  const codes = person.statCodes(catalog);
  const problems = ficha.fichaProblems(form, saved, codes);
  const c = sections.controls(form, () => {}, () => {}, saved, problems, () => true, () => {}, undefined, codes, () => {});
  return renderToStaticMarkup(createElement(sections[component], {c}));
}

test('ficha NNA 1.2: los datos principales de antes a la vista y el resto en «Más datos»', () => {
  const html = render('FirstContact', {...ficha.prepareFicha({}), modality: 'NNA', motive: 'Escucha'});
  const details = html.indexOf('class="more-data"');
  assert.ok(details > 0);
  const shown = html.slice(0, details), more = html.slice(details, html.indexOf('</details>', details));
  for (const label of ['NNA que solicita atención', 'Primer nombre', 'Primer apellido', 'Identificación disponible', 'Nacionalidad declarada', 'Relación con el NNA', 'Vínculo con el NNA', 'Domicilio del requirente', 'Nombre de preferencia', 'Forma de trato o pronombres', 'Apoyos de comunicación'])
    assert.ok(shown.includes(label), label);
  for (const label of ['Segundo nombre', 'Segundo apellido', 'Fecha de nacimiento']) assert.ok(more.includes(label) && !shown.includes(label), label);
  assert.ok(html.includes('Región y comuna del requirente') && html.includes('+56 9'));
  assert.ok(html.includes('horario escolar'));
  assert.ok(!CODES.test(visible(html)));
});

test('ficha NNA 1.1 guardada: el texto libre se muestra como registrado antes, sin errores', () => {
  const saved = {schema_version: '1.1', modality: 'NNA', motive: 'Escucha', region: 'Biobío', contact_phone: '9 8765 4321', nationality: 'chilena', identity_type: 'RUN', identity_document: 'no recuerda', people: []};
  const html = render('FirstContact', ficha.prepareFicha(saved), saved);
  assert.ok(html.includes('Registrada anteriormente: Biobío'));
  assert.ok(html.includes('Teléfono registrado anteriormente') && html.includes('9 8765 4321'));
  assert.ok(html.includes('Documento registrado anteriormente') && html.includes('no recuerda'));
  assert.ok(html.includes('Registrada anteriormente como texto libre'));
  assert.ok(!html.includes('role="alert"'));
});

test('relación con el NNA solo con un listado vigente, con rótulos y sin códigos', () => {
  const form = {...ficha.prepareFicha({}), motive: 'Consulta'};
  const withList = render('FirstContact', form);
  assert.ok(withList.includes('Relación con el NNA') && withList.includes('>Madre</option>'));
  const without = render('FirstContact', form, {}, {regions: []});
  assert.ok(!without.includes('Relación con el NNA'), 'sin listado vigente solo queda el texto');
  assert.ok(without.includes('Vínculo con el NNA'));
  // Un codigo de un listado retirado: aviso sin el codigo.
  const retired = render('FirstContact', {...form, relationship_code: 'PADRE'}, {relationship_code: 'PADRE'});
  assert.ok(retired.includes('Valor registrado con un listado anterior') && !visible(retired).includes('PADRE'));
});

test('personas relacionadas de la ficha: mismos datos de antes a la vista, contacto y territorio en «Más datos»', () => {
  const p = {...person.emptyPerson('NNA'), legacy_name: 'Hijo mayor', legacy_age: 'unos 10'};
  const html = render('FirstContact', {...ficha.prepareFicha({}), motive: 'Consulta', people: [p]}, {people: [p]});
  const card = html.slice(html.indexOf('Persona relacionada 1'));
  const details = card.indexOf('class="more-data"'), shown = card.slice(0, details), more = card.slice(details);
  for (const label of ['Relación con la atención', 'Primer nombre', 'Identificación disponible', 'Relación con el NNA', 'Domicilio o localización (texto)', 'Edad (años cumplidos)', 'Fecha de nacimiento', 'Nacionalidad', 'Escolaridad', 'Nombre de preferencia'])
    assert.ok(shown.includes(label), label);
  for (const label of ['Región y comuna de la persona', 'Celular', 'Correo']) assert.ok(more.includes(label), label);
  assert.ok(shown.includes('Nombre o referencia registrado anteriormente') && shown.includes('Hijo mayor') && shown.includes('unos 10'));
  assert.ok(!html.includes('role="alert"'), 'lo historico identico no es error');
});

test('antecedentes: lugar de los hechos e instituciones con su tipo del listado vigente', () => {
  const html = render('Listening', {...ficha.prepareFicha({}), motive: 'Consulta', institutions_list: [{name: 'Liceo', type_code: 'EDUCACION', role: '', region_code: '', commune: ''}]});
  for (const label of ['Lugar de los hechos', 'Región y comuna de los hechos', 'Instituciones intervinientes', 'Nombre de la institución', 'Tipo de institución', 'Región y comuna de la institución', 'Agregar institución'])
    assert.ok(html.includes(label), label);
  assert.ok(html.includes('>Establecimiento educativo</option>'));
  assert.ok(!html.includes('Instituciones intervinientes (texto)'), 'el texto anterior solo si existia');
  const old = render('Listening', {...ficha.prepareFicha({institutions: 'Colegio'}), motive: 'Consulta'}, {institutions: 'Colegio'});
  assert.ok(old.includes('Instituciones intervinientes (texto)') && old.includes('Colegio'));
});

test('ingreso manual: lo mínimo de hoy, sin campos obligatorios nuevos, y la región del requirente', () => {
  const html = renderToStaticMarkup(createElement(intakeScreen.default, {ctx, completed: () => {}}));
  const required = [...html.matchAll(/<span>([^<]*)<span class="required-mark"/g)].map(m => m[1]);
  assert.deepEqual(required, ['Ámbito de captura', 'Fecha y hora de recepción', 'Asunto', 'Relato original y antecedentes disponibles']);
  for (const label of ['¿Quién presenta el ingreso?', 'Una persona', 'Una institución (por ejemplo, un oficio)', 'Primer nombre', 'Identificación disponible', 'Región y comuna del requirente', 'Restricciones o indicaciones de contacto seguro', 'Agregar NNA o persona relacionada', 'Lugar de los hechos', 'Agregar institución'])
    assert.ok(html.includes(label), label);
  assert.ok(!CODES.test(visible(html)));
});

test('ingreso institucional: sin datos de persona natural', () => {
  const c = {data: intakeModel.emptyIntake(), previous: intakeModel.emptyIntake(), update: () => {}, kind: 'INSTITUCION', setKind: () => {}, error: () => undefined, touch: () => {}, codes: person.statCodes(CATALOG), regions: []};
  const html = renderToStaticMarkup(createElement(fields.PresenterFields, {c}));
  assert.ok(html.includes('Registra la institución'));
  assert.ok(!html.includes('Primer nombre') && !html.includes('Identificación disponible'));
});

test('ficha de la COM: datos de la ficha de ingreso, versiones con su resumen y acción solo con can_complete', () => {
  const data = {schema_version: '1.0', presenter: {...person.emptyPerson('REQUIRENTE'), first_name: 'Ana', last_name: 'Soto', region_code: '07', relationship_code: 'PADRE', relationship_text: 'padre de crianza', relationship_version: 1},
    persons: [{...person.emptyPerson('NNA'), legacy_name: 'Hijo de Ana', age_years: '9'}], contact_restrictions: 'Solo correo', facts_region_code: '07', facts_commune: 'Talca', facts_place: 'Colegio',
    institutions_list: [{name: 'Liceo', type_code: 'EDUCACION', type_version: 4, role: 'Establecimiento', region_code: '', commune: ''}]};
  const r = {id: 9, revision: 2, folio: 'COM-000009', intake: {data, can_complete: true, versions: [{id: 2, revision: 1, author: 'Rosa Registro', registered_ms: 0, reason: 'Se agrega la región', change_summary: '{"COMPLEMENTO":["presenter.region_code"],"RECTIFICACION":[]}'}]}};
  const html = renderToStaticMarkup(createElement(panel.IntakeRecordPanel, {r, ctx}));
  for (const text of ['Ficha de ingreso', 'Completar o rectificar', 'Ana Soto', 'Maule', 'padre de crianza', 'Valor registrado con un listado anterior', 'Hijo de Ana', 'Solo correo', 'Talca', 'Colegio', 'Liceo', 'Establecimiento educativo', '1 dato complementado.', 'Se agrega la región'])
    assert.ok(html.includes(text), text);
  const v = visible(html);
  assert.ok(v.indexOf('padre de crianza') < v.indexOf('Valor registrado con un listado anterior'), 'el texto existente y debajo el aviso');
  assert.ok(!CODES.test(v) && !v.includes('PADRE'));
  const readOnly = renderToStaticMarkup(createElement(panel.IntakeRecordPanel, {r: {...r, intake: {...r.intake, can_complete: false}}, ctx}));
  assert.ok(!readOnly.includes('Completar o rectificar'));
});

test('vista imprimible: ficha 1.1 y 1.2 con personas canónicas e instituciones, sin códigos', () => {
  const codes = person.statCodes(CATALOG);
  const old = renderToStaticMarkup(createElement(preview.Preview, {codes, close: () => {}, data: {form: {schema_version: '1.1', modality: 'NNA', motive: 'Escucha', region: 'Biobío',
    people: [{role: 'OTRA_PERSONA', name: 'Vecino', age: '40', relationship: 'vecino', identity_document: '', location: '', birth_date: '', nationality: '', schooling: '', preferred_name: ''}]}}}));
  assert.ok(old.includes('Región del requirente') && old.includes('Biobío') && old.includes('Vecino') && old.includes('Edad registrada anteriormente'));
  const current = renderToStaticMarkup(createElement(preview.Preview, {codes, close: () => {}, data: {form: {...ficha.prepareFicha({}), motive: 'Consulta', relationship_code: 'MADRE', facts_region_code: '07',
    people: [{...person.emptyPerson('NNA'), first_name: 'Luz', identity_type: 'DOCUMENTO_EXTRANJERO', document_kind: 'PASAPORTE', document_number: 'AB123'}],
    institutions_list: [{name: 'Liceo', type_code: 'EDUCACION', role: '', region_code: '', commune: ''}]}}}));
  for (const text of ['Relación con el NNA', 'Madre', 'Región de los hechos', 'Maule', 'Luz', 'Pasaporte AB123', 'Institución 1', 'Establecimiento educativo']) assert.ok(current.includes(text), text);
  assert.ok(!CODES.test(visible(old + current)));
});
