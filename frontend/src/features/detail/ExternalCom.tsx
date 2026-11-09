import type {Row} from '../../lib/rows';
import {bool, list, number, obj, text, when} from '../../lib/rows';
import {humanize} from '../../lib/labels';
import {attributeLabel, EXTERNAL_ROLE_LABELS, fieldLabel, fieldValue, INTAKE_KIND_LABELS, LEVEL_LABELS, REGISTRY_LABELS, SOURCE_LABELS} from '../../lib/formulario';
import type {ReactNode} from 'react';
import {Table} from '../../components/Table';
import {Callout, Facts, Panel} from '../../components/Primitives';

/** Procedencia como texto, nunca solo por color. */
function Source({value}: {value: unknown}) {
  return value ? <span className="chip">{humanize(value, SOURCE_LABELS)}</span> : null;
}
/** Valor conservado; con diccionario SRCeI, su significado y el codigo original a la vista. */
const valueOf = (a: Row) => {
  const value = text(a, 'value_text'), meaning = text(a, 'meaning'), code = text(a, 'code_text');
  const parts = [meaning || value || 'Sin dato'];
  if (meaning && value && value !== meaning && value !== code && code) parts.push(value);
  if (code) parts.push(`código ${code}`);
  else if (meaning && value && value !== meaning) parts.push(`código ${value}`);
  return parts.join(' · ');
};
const attributeColumns = [
  {name: 'Dato', render: (a: Row) => attributeLabel(text(a, 'attr_key'))},
  {name: 'Valor', render: (a: Row) => valueOf(a)},
  {name: 'Fuente', render: (a: Row) => <Source value={a.source}/>},
  {name: 'Verificación', render: (a: Row) => humanize(a.level_code, LEVEL_LABELS)},
  {name: 'Observado', render: (a: Row) => when(a.observed_ms)},
];

/** Persona del documento: valores normalizados, su procedencia y verificaciones por separado. */
function PersonCard({p, details}: {p: Row; details: Row[]}) {
  const attrs = list(p, 'attributes').filter(a => !bool(a, 'discrepancy')), disc = list(p, 'attributes').filter(a => bool(a, 'discrepancy'));
  const run = text(p, 'run_number') ? `${text(p, 'run_number')}-${text(p, 'run_dv')}` : '';
  const age = (key: string) => p[key] == null ? '' : `${number(p, key)} años`;
  return <article className="external-person">
    <h3>{humanize(p.person_role, EXTERNAL_ROLE_LABELS)}<span className="chip">{bool(p, 'authenticated') ? 'Identidad autenticada con ClaveÚnica' : 'Sin autenticación'}</span></h3>
    <Facts items={[
      ['Nombres', text(p, 'given_names') && <>{text(p, 'given_names')} <Source value={p.given_names_source}/></>],
      ['Apellidos', text(p, 'surnames') && <>{text(p, 'surnames')} <Source value={p.surnames_source}/></>],
      ['RUN', run && <>{run} <Source value={p.run_source}/></>],
      ['Dígito verificador', run ? (bool(p, 'run_valid') ? 'Válido (módulo 11)' : 'No válido (módulo 11)') : ''],
      ['Documento extranjero', [text(p, 'foreign_document_type'), text(p, 'foreign_document_number')].filter(Boolean).join(' ')],
      ['Fecha de nacimiento', text(p, 'birth_date') && <>{text(p, 'birth_date')} <Source value={p.birth_date_source}/></>],
      ['Edad al ingreso (calculada)', age('age_at_intake')],
      ['Edad actual (calculada)', age('current_age')],
      ['Edad declarada', age('declared_age')],
      ['Consulta registral', humanize(p.registry_state, REGISTRY_LABELS) + (p.registry_checked_ms ? ` · ${when(p.registry_checked_ms)}` : '')],
      ...details.map(d => [fieldLabel(text(d, 'field_key')), fieldValue(text(d, 'field_key'), d.value_text == null ? null : text(d, 'value_text'))] as [string, string]),
    ]}/>
    {attrs.length > 0 && <Table rows={attrs} columns={attributeColumns}/>}
    {disc.length > 0 && <Callout tone="warn" title="Antecedentes discrepantes conservados">
      <p>La fuente indicada informó un valor distinto del registrado arriba. Ninguno se reemplazó.</p>
      <Table rows={disc} columns={attributeColumns}/>
    </Callout>}
  </article>;
}

/** Ficha de una COM recibida desde el Formulario Público: proyección autorizada, sin el documento original. */
export function ExternalCom({external}: {external: Row}) {
  const original = obj(external, 'original'), fields = list(external, 'fields');
  const answers = fields.filter(f => text(f, 'field_group') === 'CAMPO');
  const institutions = Object.values(fields.filter(f => text(f, 'field_group') === 'INSTITUCION').reduce<Record<number, Row[]>>((acc, f) => {(acc[number(f, 'item')] ??= []).push(f); return acc;}, {}));
  return <>
    <Panel title="Formulario web" icon="inbox" description="Comunicación recibida desde el Formulario Público. El documento firmado se conserva íntegro y sin cambios.">
      <Facts items={[
        ['Tipo de ingreso', humanize(original.document_kind, INTAKE_KIND_LABELS)],
        ['Versión del documento', text(original, 'document_version')],
        ['Identificador de origen', <span className="folio">{text(original, 'external_id')}</span>],
        ['Sello de custodia', `Firma Ed25519 verificada · clave ${text(original, 'custody_key_id')}`],
        ['Huella de contenido', <code>{text(original, 'content_hash')}</code>, true],
        ['Conservado en SIGER', when(original.stored_ms)],
      ]}/>
    </Panel>
    <Panel title="Personas e identificación" icon="people" count={list(external, 'persons').length} description="Valores normalizados con su fuente y verificación. ClaveÚnica autentica; el Registro Civil contrasta; la declaración ciudadana no está verificada.">
      {list(external, 'persons').map(p => <PersonCard key={text(p, 'person_key')} p={p} details={fields.filter(f => text(f, 'field_group') === 'PERSONA' && text(f, 'person_key') === text(p, 'person_key'))}/>)}
    </Panel>
    <Panel title="Respuestas del formulario" icon="message" count={answers.length}>
      <Facts items={answers.map(f => [fieldLabel(text(f, 'field_key')), <span className="preserve">{fieldValue(text(f, 'field_key'), f.value_text == null ? null : text(f, 'value_text'))}</span>, text(f, 'value_text').length > 120] as [string, ReactNode, boolean])}/>
    </Panel>
    {institutions.length > 0 && <Panel title="Instituciones relacionadas" icon="flow" count={institutions.length}>
      {institutions.map((inst, i) => <Facts key={i} items={inst.map(f => [fieldLabel(text(f, 'field_key')), fieldValue(text(f, 'field_key'), f.value_text == null ? null : text(f, 'value_text'))] as [string, string])}/>)}
    </Panel>}
  </>;
}
