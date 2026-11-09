// Componentes reales de la ficha adulta renderizados en servidor (Vite ssrLoadModule + react-dom/server, sin
// dependencias nuevas): estructura, catalogos en los desplegables, accesibilidad basica, datos historicos y que la
// modalidad NNA conserve sus controles de texto libre.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

let vite, sections, form, adult;
before(async () => {
  vite = await createServer({root: new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'), server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  sections = await vite.ssrLoadModule('/src/features/attention/FormSections.tsx');
  form = await vite.ssrLoadModule('/src/features/attention/schema.ts');
  adult = await vite.ssrLoadModule('/src/features/attention/adult/adultForm.ts');
});
after(async () => {await vite?.close();});

function render(data, previous = {}, shown = () => true) {
  const errors = adult.adultProblems(data, previous);
  const c = sections.controls(data, () => {}, () => {}, previous, errors, shown, () => {});
  return renderToStaticMarkup(createElement(sections.FirstContact, {c}));
}
const count = (html, needle) => html.split(needle).length - 1;

test('ficha adulta: identificacion separada, contacto estructurado y catalogos', () => {
  const html = render({...form.emptyForm(), motive: 'Consulta'});
  for (const label of ['Primer nombre', 'Segundo nombre', 'Primer apellido', 'Segundo apellido', 'Identificación disponible', 'Fecha de nacimiento', 'Nacionalidad declarada', 'Buscar país', 'Celular de contacto', 'Correo de contacto', 'Región de atención', 'Comuna de atención', 'Preferencias de trato', 'Forma de trato o pronombres', 'Nombre de preferencia'])
    assert.ok(html.includes(label), label);
  assert.ok(html.includes('+56 9'));
  assert.ok(html.includes('inputMode="numeric"') || html.includes('inputmode="numeric"'));
  assert.equal(count(html, '<option value="13">Metropolitana de Santiago</option>'), 1);
  assert.ok(html.includes('Selecciona primero una región'));
  assert.ok(html.includes('no se consultan ClaveÚnica, Registro Civil ni PISEE'));
  assert.ok(!/verificad[oa] con/i.test(html.replace('no se consultan', '')), 'no presenta datos como verificados');
  assert.ok(!html.includes('Tribunal'), 'no mezcla la escucha NNA');
});

test('RUN separado, comunas de la region elegida y errores accesibles', () => {
  const data = {...form.emptyForm(), motive: 'Consulta', identity_type: 'RUN', presenter_run_number: '12345678', presenter_run_dv: '6', region: '13', commune: 'Viña del Mar', contact_email: 'no-es-correo'};
  const html = render(data);
  assert.ok(html.includes('RUN (número)') && html.includes('Dígito verificador'));
  assert.ok(html.includes('value="12.345.678"'));
  assert.ok(html.includes('<option value="Ñuñoa">Ñuñoa</option>'));
  assert.ok(!html.includes('<option value="Viña del Mar">'), 'solo comunas de la region');
  assert.ok(html.includes('role="alert">El número y el dígito verificador no coinciden.'));
  assert.ok(html.includes('role="alert">Revisa el formato del correo electrónico.'));
  assert.ok(html.includes('aria-invalid="true"'));
  // Sin tocar ni intentar guardar, no se muestran errores.
  assert.ok(!render(data, {}, () => false).includes('role="alert"'));
});

test('ficha 1.0 con valores fuera de catalogo: se muestran como registrados antes, sin reescribirlos', () => {
  const saved = {schema_version: '1.0', modality: 'GENERAL', motive: 'Consulta', contact_phone: '22 345 6789', region: 'Santiago Centro', commune: 'Centro', identity_type: 'RUN', identity_document: 'sin número', nationality: 'chilena', people: []};
  const html = render(adult.upgrade({...form.emptyForm(), ...saved}), saved);
  assert.ok(html.includes('Teléfono registrado anteriormente') && html.includes('22 345 6789'));
  assert.ok(html.includes('Registrada anteriormente: Santiago Centro'));
  assert.ok(html.includes('Documento registrado anteriormente') && html.includes('sin número'));
  assert.ok(html.includes('value="chilena"') && html.includes('Registrada anteriormente como texto libre'));
  assert.ok(!html.includes('role="alert"'), 'lo historico no se marca como error');
});

test('modalidad NNA conserva sus controles de texto libre', () => {
  const html = render({...form.emptyForm(), modality: 'NNA', motive: 'Escucha'});
  assert.ok(html.includes('NNA que solicita atención'));
  assert.ok(!html.includes('+56 9') && !html.includes('Buscar país') && !html.includes('Primer apellido'));
  assert.ok(html.includes('Región de atención') && html.includes('Comuna de atención'));
});
