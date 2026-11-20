import {useEffect, useState} from 'react';
import {request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {bool, list, number, obj, text, when} from '../../lib/rows';
import {has} from '../../lib/access';
import {nameOf, scopeOf} from '../../lib/catalog';
import {choose, reason, staffScopeField} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Table} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {Callout, ErrorMessage, PageHeader, Panel} from '../../components/Primitives';
import {humanize, ROLE_LABELS} from '../../lib/labels';
import {ProfileDialog} from './ProfileDialog';
import {grantOf, grantState} from './profile';

const INTEGRATIONS: [string, string][] = [['FORMULARIO_PULL', 'Consumidor del Formulario Público'], ['CORREO_GRAPH', 'Correo institucional (Microsoft Graph)']];
const WARNING_TITLES: Record<string, string> = {SIN_TI_PERMANENTE: 'Sin cuenta TI permanente', TI_INCOMPATIBLE: 'Cuentas con TI incompatible'};
const MODE_LABELS: Record<string, string> = {DESACTIVADO: 'Desactivado', MANUAL: 'Ciclo manual', AUTOMATICO: 'Automático', GRAPH: 'Graph'};
const GRANT_STATES: Record<string, string> = {VIGENTE: 'Vigente', PROGRAMADA: 'Programada', VENCIDA: 'Vencida', REVOCADA: 'Revocada'};

/**
 * TI administra cuentas, ambitos y funciones (D1): el perfil de cada cuenta fija todas sus funciones en un acto
 * (PROFILE_SET). En modo auditoria muestra cuarentena (TI) y eventos autorizados.
 */
export default function Administration({ctx, auditOnly}: {ctx: Context; auditOnly: boolean}) {
  const [data, setData] = useState<Row>({}), [error, setError] = useState('');
  useEffect(() => {
    let live = true;
    void request('/administration').then(r => {if (live) setData(r);}).catch(e => {if (live) setError(e.message);});
    return () => {live = false;};
  }, []);
  const ti = has(ctx.user, 'TI');
  const [profile, setProfile] = useState<Row | null>(null);
  const [now] = useState(() => Date.now());
  const openProfile = (id: unknown) => {const account = list(data, 'users').find(u => number(u, 'id') === Number(id)); if (account) setProfile(account);};

  const createUser = () => ctx.act({title: 'Crear cuenta individual', type: 'STAFF_CREATE', id: null, revision: null, fields: [
    {key: 'username', label: 'Usuario (minúsculas, puntos o guiones)', required: true, max: 64},
    {key: 'name', label: 'Nombre', required: true, max: 160},
    {key: 'email', label: 'Correo (opcional, no se envía)', max: 200},
    staffScopeField(ctx.catalog),
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
    {key: 'region_code', label: 'Región (obligatoria en sedes regionales)', type: 'select', options: list(ctx.catalog, 'regions').map(r => ({value: text(r, 'code'), label: text(r, 'name')}))},
    {key: 'state', label: 'Estado inicial', type: 'select', required: true, value: 'ACTIVA', options: [{value: 'ACTIVA', label: 'Activa'}, {value: 'PROYECTADA', label: 'Proyectada (solo sedes regionales)'}]},
    reason,
  ], note: 'Una sede proyectada no recibe comunicaciones ni solicitudes; puedes preparar sus cuentas y funciones, que rigen al activarla.'});
  const accountActions = (r: Row) => <div className="actions compact">
    {number(r, 'id') !== ctx.user.id && <button className="sm" onClick={() => setProfile(r)}><Icon name="shield" size={16}/>Perfil</button>}
    <button className="sm" onClick={() => choose(ctx, 'Actualizar cuenta', 'STAFF_UPDATE', r, [{key: 'name', label: 'Nombre', required: true, value: text(r, 'name'), max: 160}, {key: 'email', label: 'Correo', value: text(r, 'email'), max: 200}, {key: 'enabled', label: 'Habilitada', type: 'checkbox', value: bool(r, 'enabled')}, staffScopeField(ctx.catalog, 'Adscripción principal', number(r, 'home_scope')), reason])}>Editar o desactivar</button>
    <button className="sm" onClick={() => choose(ctx, 'Restablecer clave temporal', 'STAFF_RESET', r, [{key: 'password', label: 'Nueva clave individual temporal', type: 'password', required: true, max: 72}, reason], 'Invalida sesiones y exige cambio al ingresar.')}>Restablecer</button>
  </div>;
  // Revocacion individual (por ejemplo, uno de dos periodos de la misma funcion); el perfil cubre el resto.
  const revoke = (g: Row) => choose(ctx, 'Revocar función', 'GRANT_REVOKE', g, [reason], 'Las sesiones abiertas de la cuenta se cierran. La historia de la función se conserva.');
  // Liberacion: un original real sale de cuarentena solo tras un analisis antivirus limpio y reciente del servidor.
  // La revision manual sin motor existe unicamente en instancias de pruebas (politica del servidor).
  const policy = obj(data, 'document_policy');
  const synthetic = bool(policy, 'synthetic_release'), engine = text(policy, 'engine');
  const [scanning, setScanning] = useState<number | null>(null);
  const scan = async (d: Row) => {
    setScanning(number(d, 'id'));
    try {
      const r = await request(`/documents/${number(d, 'id')}/scan`, {method: 'POST'});
      ctx.reload();
      ctx.message(text(r, 'verdict') === 'LIMPIO' ? 'Análisis limpio registrado. Ya puedes liberar el original.' : 'Análisis registrado: ' + humanize(text(r, 'verdict')) + (text(r, 'detail') ? ' (' + humanize(text(r, 'detail')) + ')' : '') + '. El original sigue en cuarentena.');
    } catch (e) {setError((e as Error).message);} finally {setScanning(null);}
  };
  const release = (d: Row) => text(d, 'scan_verdict') === 'LIMPIO' && number(d, 'detections') === 0
    ? choose(ctx, 'Liberar original analizado', 'DOC_RELEASE', d, [reason], 'Se verifica de nuevo el SHA-256 y que el análisis limpio sea reciente. Una detección previa impide la liberación.')
    : choose(ctx, 'Habilitar descarga de archivo de prueba', 'DOC_RELEASE', d, [{key: 'synthetic_review', label: 'Confirmo que es un archivo de prueba revisado externamente; no certifico un antivirus', type: 'checkbox', required: true}, reason], 'Solo en instancias de pruebas. No usar para documentos ciudadanos reales.');

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
      lead={auditOnly ? 'Trazabilidad y cuarentena dentro de tus facultades. Consultar no concede acceso a los expedientes.' : 'TI crea cuentas y fija sus funciones con el perfil de cada cuenta, con el acto institucional como fundamento. Nadie puede otorgarse funciones a sí mismo y TI no tiene acceso a los expedientes.'}
      aside={!auditOnly && ti && <>
        <button className='primary' onClick={createUser}><Icon name='plus' size={18}/>Crear usuario</button>
        <button onClick={createScope}><Icon name='layers' size={18}/>Nuevo ámbito</button>
      </>}/>
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {list(data, 'warnings').map(w => <Callout key={text(w, 'code')} tone='warn' role='status' title={WARNING_TITLES[text(w, 'code')] ?? 'Aviso de administración'}>{text(w, 'message')}</Callout>)}
    {profile && <ProfileDialog ctx={ctx} account={profile} grants={list(data, 'grants')} close={() => setProfile(null)}/>}
    {!auditOnly && <>
      {list(data, 'incompatibilities').length > 0 && <Panel title='Incompatibilidades por resolver' icon='alert' count={list(data, 'incompatibilities').length} description='Cuentas que combinan TI con otras funciones (heredadas de versiones anteriores). TI no puede tener otras funciones: deja en el perfil solo TI o solo las demás.'>
        <Table rows={list(data, 'incompatibilities')} columns={[
          {name: 'Usuario', render: r => <code>{text(r, 'username')}</code>},
          {name: 'Nombre', render: r => <span className='cell-strong'>{text(r, 'name')}</span>},
          {name: 'Funciones', render: r => ((r.roles ?? []) as string[]).map(code => humanize(code, ROLE_LABELS)).join(', ')},
          {name: 'Acción', render: r => number(r, 'id') === ctx.user.id ? <span className='muted'>Otra cuenta TI debe resolverla</span> : <button className='sm' onClick={() => openProfile(r.id)}>Abrir perfil</button>},
        ]}/>
      </Panel>}
      <Panel title='Cuentas' icon='people' count={list(data, 'users').length}>
        <Table rows={list(data, 'users')} columns={[
          {name: 'Usuario', render: r => <code>{text(r, 'username')}</code>},
          {name: 'Nombre', render: r => <span className='cell-strong'>{text(r, 'name')}</span>},
          {name: 'Estado', render: r => bool(r, 'enabled') ? <span className='chip chip-ok'>Habilitada</span> : <span className='chip'>Deshabilitada</span>},
          {name: 'Ámbito', render: r => scopeOf(ctx.catalog, r.home_scope)},
          {name: 'Acciones TI', render: r => ti ? accountActions(r) : <span className='muted'>Consulta</span>},
        ]}/>
      </Panel>
      {ti && <Panel title='Funciones otorgadas' icon='shield' count={list(data, 'grants').length} description='Vigentes, programadas e historia. Para cambiar las funciones de una cuenta usa su perfil; Revocar retira una sola concesión.'>
        <Table rows={list(data, 'grants')} columns={[
          {name: 'Persona', render: g => nameOf(ctx.catalog, g.user_id)},
          {name: 'Función', render: g => <span className='chip'>{humanize(g.role_code, ROLE_LABELS)}</span>},
          {name: 'Ámbito', render: g => g.scope_id == null ? <span className='muted'>Institucional</span> : scopeOf(ctx.catalog, g.scope_id)},
          {name: 'Desde', render: g => number(g, 'valid_from') > 0 ? <span className='nowrap'>{when(g.valid_from)}</span> : <span className='muted'>Sin fecha registrada</span>},
          {name: 'Hasta', render: g => <span className='nowrap'>{g.valid_until == null ? 'Sin término' : when(g.valid_until)}</span>},
          {name: 'Estado', render: g => {const state = grantState(grantOf(g), now); return <span className={'chip' + (state === 'VIGENTE' ? ' chip-ok' : '')}>{GRANT_STATES[state]}{state === 'REVOCADA' ? ' ' + when(g.revoked_at) : ''}</span>;}},
          {name: 'Acción', render: g => ['VIGENTE', 'PROGRAMADA'].includes(grantState(grantOf(g), now)) && number(g, 'user_id') !== ctx.user.id ? <button className='sm' onClick={() => revoke(g)}>Revocar</button> : <span className='muted'>—</span>},
        ]}/>
      </Panel>}
    </>}
    {auditOnly && ti && Object.keys(integrations).length > 0 && <Panel title='Integraciones permanentes' icon='activity' description='Estado técnico sin datos de expedientes. Un ciclo con incidentes reintenta con espera creciente; un correo ambiguo nunca se reenvía sin tu decisión.'>
      <Table rows={INTEGRATIONS.map(([key, label]) => ({...obj(integrations, key), key, label}))} columns={[
        {name: 'Integración', render: r => <span className='cell-strong'>{text(r, 'label')}</span>},
        {name: 'Modo', render: r => <span className='chip'>{MODE_LABELS[text(r, 'mode')] ?? text(r, 'mode')}</span>},
        {name: 'Estado', render: r => bool(r, 'paused') ? <span className='chip'>Pausada</span> : bool(r, 'stale') ? <span className='chip'>Sin latido: revisar el servicio</span> : humanize(text(obj(r, 'status'), 'state'))},
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
    {auditOnly && ti && <Panel title='Cuarentena documental' icon='lock' count={list(data, 'quarantine').length} description={engine && engine !== 'NONE'
      ? 'TI ve identificador, tamaño, huella y análisis, no el contenido del expediente. Un original sale de cuarentena solo con un análisis limpio y reciente de ' + engine + '.'
      : 'Sin motor antivirus configurado: los originales permanecen en cuarentena' + (synthetic ? ' (en esta instancia de pruebas se admite la revisión manual).' : '.')}>
      <Table rows={list(data, 'quarantine')} emptyIcon='checkCircle' empty='Sin archivos en cuarentena' columns={[
        {name: 'ID', render: d => text(d, 'id')},
        {name: 'Bytes', render: d => <span className='tabular'>{number(d, 'size_bytes').toLocaleString('es-CL')}</span>},
        {name: 'SHA-256', render: d => <code>{text(d, 'sha256')}</code>},
        {name: 'Análisis', render: d => text(d, 'scan_verdict') ? <span className={'chip' + (text(d, 'scan_verdict') === 'LIMPIO' ? ' chip-ok' : '')}>{humanize(text(d, 'scan_verdict'))}{number(d, 'detections') > 0 ? ' · con detección previa' : ''}</span> : <span className='muted'>Sin análisis</span>},
        {name: 'Fecha', render: d => <span className='nowrap'>{when(d.scan_ms)}</span>},
        {name: 'Acciones', render: d => <div className='actions compact'>
          {engine && engine !== 'NONE' && <button className='sm' disabled={scanning === number(d, 'id')} onClick={() => void scan(d)}>{scanning === number(d, 'id') ? 'Analizando…' : 'Analizar'}</button>}
          {(text(d, 'scan_verdict') === 'LIMPIO' && number(d, 'detections') === 0 || synthetic) && <button className='sm' onClick={() => release(d)}>Liberar</button>}
        </div>},
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
