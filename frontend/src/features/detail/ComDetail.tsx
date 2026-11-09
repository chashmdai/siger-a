import type {Row} from '../../lib/rows';
import {bool, list, number, obj, text, when} from '../../lib/rows';
import {SOURCE_LABELS} from '../../lib/formulario';
import {ExternalCom} from './ExternalCom';
import {solicitationPeople} from '../../lib/catalog';
import {choose, reason, routingFields} from '../../lib/fields';
import {CHANNEL_LABELS, humanize, PERSON_ROLE_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Kind, Status} from '../../components/Status';
import {Table} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {Callout, EmptyState, Facts, Panel} from '../../components/Primitives';
import {Files} from './Files';
import {ActionBar, DetailGrid} from './parts';

/** Comunicacion: lo recibido, tal como se registro. Distribucion, antecedente o asuncion propia. */
export function ComDetail({r, ctx}: {r: Row; ctx: Context}) {
  const route = () => choose(ctx, 'Clasificar y enviar SOL', 'COM_ROUTE', r, [...routingFields(ctx.catalog), reason]);
  const link = () => choose(ctx, 'Vincular al REQ existente', 'COM_LINK', r, [{key: 'folio', label: 'Folio exacto del REQ', required: true, max: 48}, reason], 'La COM conserva su folio. El vínculo no crea otro REQ ni reabre Casos.');
  const assume = () => choose(ctx, 'Asumir ingreso propio', 'COM_ASSUME', r, [{key: 'responsible_id', label: 'Profesional encargado de la unidad', type: 'select', required: true, options: solicitationPeople(ctx.catalog, number(r, 'origin_scope'))}, reason], 'La jefatura asume directamente un ingreso de su competencia, sin enviarlo artificialmente a central.');
  const restrictions = text(r, 'contact_restrictions');
  const continuity = list(r, 'solicitations').length + list(r, 'requirements').length + list(r, 'links').length;
  // COM del Formulario Publico: proyeccion autorizada que entrega el servidor (sin el documento original).
  const external = obj(r, 'external'), isExternal = Object.keys(external).length > 0;
  const presenter = list(external, 'persons').find(p => text(p, 'person_role') === 'PRESENTADOR') ?? {};
  const identitySource = text(presenter, 'run_number') && bool(presenter, 'run_valid') ? humanize(presenter.run_source, SOURCE_LABELS) : text(presenter, 'foreign_document_number') ? 'Documento extranjero declarado' : '';
  return <DetailGrid
    main={<>
      <ActionBar>
        {bool(r, 'can_route') && <>
          <button className="primary" onClick={route}><Icon name="send" size={18}/>Distribuir SOL</button>
          {bool(r, 'can_link') && <button onClick={link}><Icon name="link" size={18}/>Proponer antecedente</button>}
        </>}
        {bool(r, 'can_assume') && <button onClick={assume}><Icon name="check" size={18}/>Asumir como jefatura</button>}
      </ActionBar>
      <Panel title="Relato original" icon="message" description="Tal como se registró al recibirlo.">
        <p className="preserve reading">{text(r, 'body') || (isExternal ? 'El tipo de presentación no incluye un relato.' : 'Sin relato registrado.')}</p>
      </Panel>
      {isExternal && <ExternalCom external={external}/>}
      {list(r, 'persons').length > 0 && <Panel title="Personas relacionadas" icon="people" count={list(r, 'persons').length} description="Datos declarados en el ingreso; no constituyen un registro maestro.">
        <Table rows={list(r, 'persons')} columns={[
          {name: 'Rol', render: p => humanize(p.person_role, PERSON_ROLE_LABELS)},
          {name: 'Nombre / identificación', render: p => <>{text(p, 'name')}<small>{text(p, 'identity_document')}</small></>},
          {name: 'Vínculo', render: p => text(p, 'relationship')},
          {name: 'Localización', render: p => text(p, 'location_text')},
        ]}/>
      </Panel>}
      <Panel title="Continuidad" icon="flow" description="Solicitudes, requerimientos y vínculos que nacieron de esta comunicación.">
        {continuity === 0 ? <EmptyState compact icon="send" title="Aún sin destino">{bool(r, 'can_route') ? 'Distribúyela a la jefatura competente o propón vincularla a un REQ existente.' : 'Distribución todavía no la ha clasificado.'}</EmptyState> : <ul className="link-list">
          {list(r, 'solicitations').map(s => <li key={'s' + number(s, 'id')}><button className="link-row" onClick={() => ctx.go('SOL', number(s, 'id'))}><Kind kind="SOL"/><span>Solicitud {number(s, 'id')}</span><Icon name="right" size={18}/></button></li>)}
          {list(r, 'requirements').map(q => <li key={'q' + number(q, 'id')}><button className="link-row" onClick={() => ctx.go('REQ', number(q, 'id'))}><Kind kind="REQ"/><span className="folio">{text(q, 'folio')}</span><Icon name="right" size={18}/></button></li>)}
          {list(r, 'links').map(l => <li key={'l' + number(l, 'id')} className="link-row static"><Icon name="link" size={18}/><span>Vínculo a <span className="folio">{text(l, 'folio')}</span>{l.returned_ms ? ' · Devuelta a clasificación' : ''}</span><Status value={l.state}/></li>)}
        </ul>}
      </Panel>
      <Files row={r} kind="COM" ctx={ctx} editable={text(r, 'state') === 'NUEVA' && number(r, 'created_by') === ctx.user.id}/>
    </>}
    side={<>
      <Panel title="Recepción" icon="inbox">
        <Facts stacked items={[
          ['Canal', humanize(r.channel, CHANNEL_LABELS)],
          ['Recepción efectiva', when(r.received_ms)],
          ['Registro en SIGER', when(r.created_ms)],
        ]}/>
      </Panel>
      <Panel title="Requiriente y contacto" icon="user">
        <Facts stacked items={isExternal ? [
          ['Persona que presenta', text(r, 'presenter')],
          ['Identificación', text(r, 'identity_document')],
          ['Fuente de la identificación', identitySource],
          ['Domicilio o residencia informada', text(r, 'presenter_domicile')],
          ['Correo', text(r, 'contact_email')],
          ['Teléfono', text(r, 'contact_phone')],
          ['Medios de contacto', text(r, 'contact')],
        ] : [
          ['Requiriente', text(r, 'presenter')],
          ['Identificación declarada', text(r, 'identity_document')],
          ['Domicilio del requiriente', text(r, 'presenter_domicile')],
          ['Contacto', [text(r, 'contact'), text(r, 'contact_email'), text(r, 'contact_phone')].filter(Boolean).join(' · ')],
        ]}/>
        {isExternal && <p className="muted">Disponer de un correo o teléfono no implica autorización para contactar. Revisa las indicaciones de contacto.</p>}
        {restrictions && <Callout tone="warn" title="Indicaciones de contacto seguro"><p className="preserve">{restrictions}</p></Callout>}
      </Panel>
    </>}/>;
}
