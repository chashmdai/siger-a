import {useEffect, useState} from 'react';
import {request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {bool, list, localTime, number, obj, text, when} from '../../lib/rows';
import {has} from '../../lib/access';
import {nameOf, scopeOf} from '../../lib/catalog';
import {choose, person, reason, scopeField} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Table} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {ErrorMessage, PageHeader, Panel} from '../../components/Primitives';
import {humanize, ROLE_LABELS} from '../../lib/labels';

const INTEGRATIONS: [string, string][] = [['FORMULARIO_PULL', 'Consumidor del Formulario Público'], ['CORREO_GRAPH', 'Correo institucional (Microsoft Graph)']];
const MODE_LABELS: Record<string, string> = {DESACTIVADO: 'Desactivado', MANUAL: 'Ciclo manual', AUTOMATICO: 'Automático', GRAPH: 'Graph'};

const GRANTABLE_ROLES = ['ADMINISTRATIVO', 'PROFESIONAL', 'JEFATURA', 'DIRECCION', 'AUTORIDAD', 'LECTURA', 'AUDITORIA', 'DISTRIBUIDOR'];

/**
 * TI administra cuentas y ambitos; la autoridad concede funciones dentro de su alcance.
 * En modo auditoria muestra cuarentena (TI) y eventos autorizados.
 */
export default function Administration({ctx, auditOnly}: {ctx: Context; auditOnly: boolean}) {
  const [data, setData] = useState<Row>({}), [error, setError] = useState('');
  useEffect(() => {
    let live = true;
    void request('/administration').then(r => {if (live) setData(r);}).catch(e => {if (live) setError(e.message);});
    return () => {live = false;};
  }, []);
  const ti = has(ctx.user, 'TI'), authority = has(ctx.user, 'AUTORIDAD');

  const createUser = () => ctx.act({title: 'Crear cuenta individual', type: 'STAFF_CREATE', id: null, revision: null, fields: [
    {key: 'username', label: 'Usuario (minúsculas, puntos o guiones)', required: true, max: 64},
    {key: 'name', label: 'Nombre', required: true, max: 160},
    {key: 'email', label: 'Correo (opcional, no se envía)', max: 200},
    scopeField(ctx.catalog, 'scope_id', 'Adscripción principal'),
    {key: 'password', label: 'Clave temporal individual (14 a 72 bytes)', type: 'password', required: true, max: 72},
    reason,
  ], note: 'No recibe funciones operativas automaticamente. Entrega la clave por un canal seguro.'});
  const createScope = () => ctx.act({title: 'Crear ámbito operativo', type: 'SCOPE_CREATE', id: null, revision: null, fields: [
    {key: 'code', label: 'Código único', required: true, max: 48},
    {key: 'name', label: 'Nombre del ámbito', required: true, max: 160},
    {key: 'unit_code', label: 'Unidad', type: 'select', required: true, options: list(ctx.catalog, 'units').map(u => ({value: text(u, 'code'), label: text(u, 'name')}))},
    {key: 'area_code', label: 'Área (opcional)', max: 48},
    {key: 'site_code', label: 'Sede / CENTRAL', required: true, max: 48},
    {key: 'regional', label: 'Es sede regional', type: 'checkbox'},
    reason,
  ]});
  const grantRole = () => ctx.act({title: 'Conceder función con respaldo', type: 'GRANT_CREATE', id: null, revision: null, fields: [
    person(ctx.catalog, 'user_id', 'Funcionario'),
    scopeField(ctx.catalog),
    {key: 'role', label: 'Función', type: 'select', required: true, options: GRANTABLE_ROLES.map(v => ({value: v, label: ROLE_LABELS[v] ?? v}))},
    {key: 'from_at', label: 'Inicio', type: 'datetime-local', required: true, value: localTime()},
    {key: 'until_at', label: 'Término (opcional)', type: 'datetime-local'},
    reason,
  ]});
  const accountActions = (r: Row) => <div className="actions compact">
    <button className="sm" onClick={() => choose(ctx, 'Actualizar cuenta', 'STAFF_UPDATE', r, [{key: 'name', label: 'Nombre', required: true, value: text(r, 'name'), max: 160}, {key: 'email', label: 'Correo', value: text(r, 'email'), max: 200}, {key: 'enabled', label: 'Habilitada', type: 'checkbox', value: bool(r, 'enabled')}, reason])}>Editar o desactivar</button>
    <button className="sm" onClick={() => choose(ctx, 'Restablecer clave temporal', 'STAFF_RESET', r, [{key: 'password', label: 'Nueva clave individual temporal', type: 'password', required: true, max: 72}, reason], 'Invalida sesiones y exige cambio al ingresar.')}>Restablecer</button>
  </div>;
  // Las funciones centrales (sin ambito) se revocan indicando el ambito de la autoridad.
  const revoke = (g: Row) => choose(ctx, 'Revocar función', 'GRANT_REVOKE', g, [...(g.scope_id ? [] : [scopeField(ctx.catalog, 'scope_id', 'Ámbito central de tu autoridad')]), reason]);
  const release = (d: Row) => choose(ctx, 'Habilitar descarga de archivo sintético', 'DOC_RELEASE', d, [{key: 'synthetic_review', label: 'Confirmo que es un archivo sintético revisado externamente; no certifico un antivirus', type: 'checkbox', required: true}, reason], 'No usar esta liberación manual para documentos ciudadanos reales.');

  // Pausar o reanudar no cancela un ciclo en curso: el trabajador lo respeta en su ciclo siguiente.
  const control = (r: Row, name: string) => {
    const paused = bool(r, 'paused');
    choose(ctx, (paused ? 'Reanudar ' : 'Pausar ') + name.toLowerCase(), paused ? 'INTEGRATION_RESUME' : 'INTEGRATION_PAUSE', r,
      [{key: 'reason', label: 'Motivo (queda auditado)', type: 'textarea', required: true, max: 1000}],
      paused ? 'El trabajador vuelve a operar en su siguiente ciclo.' : 'El ciclo en curso termina normalmente; no se inician ciclos nuevos hasta reanudar.');
  };
  const resolve = (m: Row) => choose(ctx, 'Resolver correo ambiguo', 'MAIL_RESOLVE', m, [
    {key: 'decision', label: 'Decisión', type: 'select', required: true, options: [{value: 'DESCARTAR', label: 'Descartar: no volver a enviarlo'}, {value: 'REENVIAR', label: 'Reenviar: el destinatario podría recibirlo dos veces'}]},
    reason,
  ], 'El intento pudo haber salido o no. Antes de reenviar, consulta al destinatario o el registro de envío del buzón.');
  const integrations = obj(data, 'integrations');

  return <>
    <PageHeader eyebrow={<><Icon name={auditOnly ? 'activity' : 'shield'} size={14}/>Administración</>} title={auditOnly ? 'Auditoría y operación' : 'Personas y funciones'}
      lead={auditOnly ? 'Trazabilidad y cuarentena dentro de tus facultades. Consultar no concede acceso a los expedientes.' : 'TI crea cuentas. La autoridad habilitada concede funciones dentro de su alcance; nadie puede otorgarse privilegios a sí mismo.'}
      aside={!auditOnly && <>
        {ti && <><button onClick={createUser}><Icon name='plus' size={18}/>Crear usuario</button><button onClick={createScope}><Icon name='layers' size={18}/>Nuevo ámbito</button></>}
        {authority && <button className='primary' onClick={grantRole}><Icon name='shield' size={18}/>Conceder función</button>}
      </>}/>
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {!auditOnly && <>
      <Panel title='Cuentas' icon='people' count={list(data, 'users').length}>
        <Table rows={list(data, 'users')} columns={[
          {name: 'Usuario', render: r => <code>{text(r, 'username')}</code>},
          {name: 'Nombre', render: r => <span className='cell-strong'>{text(r, 'name')}</span>},
          {name: 'Estado', render: r => bool(r, 'enabled') ? <span className='chip chip-ok'>Habilitada</span> : <span className='chip'>Deshabilitada</span>},
          {name: 'Ámbito', render: r => scopeOf(ctx.catalog, r.home_scope)},
          {name: 'Acciones TI', render: r => ti ? accountActions(r) : <span className='muted'>Consulta</span>},
        ]}/>
      </Panel>
      {authority && <Panel title='Funciones otorgadas' icon='shield' count={list(data, 'grants').length}>
        <Table rows={list(data, 'grants')} columns={[
          {name: 'Persona', render: g => nameOf(ctx.catalog, g.user_id)},
          {name: 'Función', render: g => <span className='chip'>{humanize(g.role_code, ROLE_LABELS)}</span>},
          {name: 'Ámbito', render: g => scopeOf(ctx.catalog, g.scope_id)},
          {name: 'Hasta', render: g => when(g.valid_until)},
          {name: 'Revocación', render: g => g.revoked_at ? when(g.revoked_at) : <button className='sm' onClick={() => revoke(g)}>Revocar</button>},
        ]}/>
      </Panel>}
    </>}
    {auditOnly && ti && Object.keys(integrations).length > 0 && <Panel title='Integraciones permanentes' icon='activity' description='Estado técnico sin datos de expedientes. Un ciclo con incidentes reintenta con espera creciente; un correo ambiguo nunca se reenvía sin tu decisión.'>
      <Table rows={INTEGRATIONS.map(([key, label]) => ({...obj(integrations, key), key, label}))} columns={[
        {name: 'Integración', render: r => <span className='cell-strong'>{text(r, 'label')}</span>},
        {name: 'Modo', render: r => <span className='chip'>{MODE_LABELS[text(r, 'mode')] ?? text(r, 'mode')}</span>},
        {name: 'Estado', render: r => bool(r, 'paused') ? <span className='chip'>Pausada</span> : humanize(text(obj(r, 'status'), 'state'))},
        {name: 'Último ciclo', render: r => <span className='nowrap'>{when(obj(r, 'status').last_cycle_ms)}</span>},
        {name: 'Último éxito', render: r => <span className='nowrap'>{when(obj(r, 'status').last_success_ms)}</span>},
        {name: 'Incidente', render: r => text(obj(r, 'status'), 'last_error') ? <code>{text(obj(r, 'status'), 'last_error')}</code> : <span className='muted'>Sin incidentes</span>},
        {name: 'Contadores', render: r => Object.entries(obj(r, 'counts')).map(([k, v]) => humanize(k) + ': ' + String(v)).join(' · ') || '—'},
        {name: 'Control', render: r => <button className='sm' onClick={() => control(r, text(r, 'label'))}>{bool(r, 'paused') ? 'Reanudar' : 'Pausar'}</button>},
      ]}/>
      {list(obj(integrations, 'CORREO_GRAPH'), 'ambiguous').length > 0 && <Table rows={list(obj(integrations, 'CORREO_GRAPH'), 'ambiguous')} columns={[
        {name: 'Correo', render: m => '#' + text(m, 'id')},
        {name: 'Destinatario', render: m => <code>{text(m, 'username')}</code>},
        {name: 'Referencia', render: m => <code>{text(m, 'correlation_id')}</code>},
        {name: 'Desde', render: m => <span className='nowrap'>{when(m.updated_ms)}</span>},
        {name: 'Decisión', render: m => <button className='sm' onClick={() => resolve(m)}>Resolver</button>},
      ]}/>}
    </Panel>}
    {auditOnly && ti && <Panel title='Cuarentena documental' icon='lock' count={list(data, 'quarantine').length} description='TI ve identificador, tamaño y huella, no el contenido del expediente. No existe análisis antivirus automático en esta entrega.'>
      <Table rows={list(data, 'quarantine')} emptyIcon='checkCircle' empty='Sin archivos en cuarentena' columns={[
        {name: 'ID', render: d => text(d, 'id')},
        {name: 'Bytes', render: d => <span className='tabular'>{number(d, 'size_bytes').toLocaleString('es-CL')}</span>},
        {name: 'SHA-256', render: d => <code>{text(d, 'sha256')}</code>},
        {name: 'Revisión externa', render: d => <button className='sm' onClick={() => release(d)}>Registrar revisión manual</button>},
      ]}/>
    </Panel>}
    {auditOnly && <Panel title='Auditoría autorizada' icon='activity' count={list(data, 'audit').length} description='Hasta 300 eventos recientes dentro de tus facultades. Una consulta no demuestra lectura comprensiva ni trabajo efectuado.'>
      <Table rows={list(data, 'audit')} emptyIcon='activity' empty='Sin eventos en tu alcance' columns={[
        {name: 'Fecha', render: e => <span className='nowrap'>{when(e.at_ms)}</span>},
        {name: 'Actor', render: e => text(e, 'actor')},
        {name: 'Acción', render: e => humanize(e.action)},
        {name: 'Recurso', render: e => text(e, 'resource_type') + ' #' + number(e, 'resource_id')},
        {name: 'Constancia', render: e => <span className='preserve'>{text(e, 'note')}</span>},
      ]}/>
    </Panel>}
  </>;
}
