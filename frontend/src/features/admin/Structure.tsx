import {useEffect, useRef, useState} from 'react';
import type {Row} from '../../lib/rows';
import {list, number, text, when} from '../../lib/rows';
import {nameOf} from '../../lib/catalog';
import {reason} from '../../lib/fields';
import {postJson} from '../../lib/api';
import type {Context} from '../../app/context';
import type {Review} from '../../components/ActionDialog';
import {Table} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {Callout, Panel} from '../../components/Primitives';
import {useExit} from '../../components/useExit';
import {humanize, ROLE_LABELS, SCOPE_STATE_LABELS, SITE_ROLE_LABELS} from '../../lib/labels';
import {grantOf} from './profile';
import type {EffectLine, Period, ScopeRow, TerritorialKind} from './structureModel';
import {actionsFor, canEditCoverage, codes, confirmationOf, coverageOf, effectLines, membersOf, missingRegion, periodsOf, previewBody,
  regionLabel, structureGroups, territorialOrders, volume} from './structureModel';

/**
 * Panel Estructura (R03b, TI): nivel central y sedes regionales con su cabeza vigente, subrogancia, dotacion y
 * volumen abierto (solo cantidades). Acciones: Ver funcionarios (abre su perfil), Editar, Activar y Cerrar. Cerrar
 * queda deshabilitado mientras el servidor informe bloqueos; si igual responde 409, el dialogo muestra su mensaje y
 * el panel se recarga.
 * Cobertura territorial (0.7.0, D18-D19): cobertura adicional vigente e historia de cada sede y del nivel central.
 * Cerrar, Cobertura, Editar con cambio de region y la cobertura del nivel central muestran antes de confirmar el
 * efecto territorial que calcula el servidor (la misma regla del comando) y, si alguna region queda sin atencion, exigen
 * confirmarlo expresamente: la orden lleva regiones_sin_sede tal como lo entrego la vista previa.
 */
export function Structure({ctx, rows, users, grants, central: centralCoverage, openProfile}: {ctx: Context; rows: Row[]; users: Row[]; grants: Row[]; central: Row; openProfile: (account: Row) => void}) {
  const regions = list(ctx.catalog, 'regions').map(r => ({code: text(r, 'code'), name: text(r, 'name')}));
  const regionName = (code: string | null) => regions.find(r => r.code === code)?.name ?? code ?? '';
  const {central, sites} = structureGroups(rows, regions);
  const [members, setMembers] = useState<ScopeRow | null>(null);
  const reloadOn409 = (e: Error & {status?: number}) => {if (e.status === 409) ctx.reload();};

  /** Revision previa: efecto territorial del cambio, sin escrituras, y la confirmacion que exige el servidor. */
  const territorial = (kind: TerritorialKind, scope: number | null) => async (payload: Row): Promise<Review> => {
    const preview = await postJson('/administration/territorial-effect', previewBody(kind, scope, payload));
    const confirmation = confirmationOf(preview, regions);
    return {content: <TerritorialEffect kind={kind} lines={effectLines(list(preview, 'effect'), regions)} unattended={confirmation.unattended.map(code => regionLabel(regions, code))}/>,
      extra: confirmation.extra, confirm: confirmation.confirm};
  };

  const centralRegions = codes(centralCoverage.regions);
  // Editar, Cerrar, Cobertura y la cobertura del nivel central (con su revision territorial): structureModel.ts.
  const orders = territorialOrders({regions, central: centralCoverage, reason, reviewer: territorial, failed: reloadOn409});
  const edit = (r: ScopeRow) => ctx.act(orders.edit(r));
  const activate = (r: ScopeRow) => ctx.act({title: 'Activar sede ' + r.code, type: 'SCOPE_ACTIVATE', id: r.id, revision: number(r, 'revision'), fields: [reason],
    note: `La sede pasa a recibir SOL, peticiones entre unidades y entregas. Su dotación preparada (${number(r, 'staff')} ${number(r, 'staff') === 1 ? 'cuenta' : 'cuentas'}) empieza a regir y esas sesiones se renuevan.`});
  const close = (r: ScopeRow) => ctx.act(orders.close(r));
  const coverage = (r: ScopeRow) => ctx.act(orders.coverage(r));
  const editCentral = () => ctx.act(orders.central());

  const head = (r: ScopeRow) => r.head_id == null ? <span className="muted">Sin cabeza vigente</span>
    : <span>{nameOf(ctx.catalog, r.head_id)}{r.level === 'CENTRAL' && <small className="structure-sub muted">{humanize(text(r, 'head_role'), ROLE_LABELS)}</small>}</span>;
  /** Subrogancia activa o la proxima programada, bajo la cabeza vigente. */
  const subrogated = (r: ScopeRow) => {
    const x = r.substitution as Row | null | undefined;
    if (!x) return <small className="structure-sub muted">Sin subrogancia</small>;
    return <small className="structure-sub">{text(x, 'state') === 'PROGRAMADA' ? 'Subrogancia programada: ' : 'Subroga: '}{nameOf(ctx.catalog, x.substitute_id)}
      {' '}<span className="muted nowrap">{text(x, 'state') === 'PROGRAMADA' ? 'desde ' + when(x.from_ms) : 'hasta ' + when(x.until_ms)}</span></small>;
  };
  const state = (r: ScopeRow) => <span className={'chip' + (r.state === 'ACTIVA' ? ' chip-ok' : r.state === 'PROYECTADA' ? ' chip-warn' : '')}>{SCOPE_STATE_LABELS[r.state] ?? humanize(r.state)}</span>;
  const load = (r: ScopeRow) => <span className="tabular">{number(r, 'staff')} {number(r, 'staff') === 1 ? 'cuenta' : 'cuentas'}<small className="structure-sub muted">{volume(r)}</small></span>;
  const actions = (r: ScopeRow) => {
    const a = actionsFor(r), reasonId = 'close-blockers-' + r.id, activateId = 'activate-blocked-' + r.id;
    return <div className="structure-actions">
      <div className="actions compact">
        <button className="sm" onClick={() => setMembers(r)}><Icon name="people" size={16}/>Ver funcionarios</button>
        {a.edit && <button className="sm" onClick={() => edit(r)}><Icon name="edit" size={16}/>Editar</button>}
        {canEditCoverage(r) && <button className="sm" onClick={() => coverage(r)}><Icon name="layers" size={16}/>Cobertura</button>}
        {a.activate && <button className="sm" disabled={!!a.activateBlocked} aria-describedby={a.activateBlocked ? activateId : undefined} onClick={() => activate(r)}>Activar</button>}
        {a.close && <button className="sm" disabled={a.closeBlocked.length > 0} aria-describedby={a.closeBlocked.length ? reasonId : undefined} onClick={() => close(r)}>Cerrar</button>}
      </div>
      {a.activateBlocked && <small id={activateId} className="muted">{a.activateBlocked}</small>}
      {a.closeBlocked.length > 0 && <small id={reasonId} className="structure-blockers">Para cerrarla no deben quedar: {a.closeBlocked.join('; ')}.</small>}
    </div>;
  };
  const scopeCell = (r: ScopeRow) => <span><span className="cell-strong">{r.code}</span><small className="structure-sub">{text(r, 'name')}</small></span>;
  /** Region fisica, cobertura adicional vigente (preparada en una proyectada) e historia de periodos. */
  const regionCell = (r: ScopeRow) => {
    const extra = coverageOf(r);
    return <div>
      {missingRegion(r) ? <span className="chip chip-warn nowrap"><Icon name="alert" size={14}/>Sin región</span> : regionName(r.region)}
      {extra.length > 0 && <small className="structure-sub">Cubre además: {extra.map(code => regionName(code)).join(', ')}{r.state === 'PROYECTADA' ? ' (rige al activarla)' : ''}</small>}
      <CoverageHistory ctx={ctx} periods={periodsOf(list(r, 'coverage_history'), regions)}/>
    </div>;
  };

  return <Panel title="Estructura" icon="layers" count={rows.length} description="Nivel central y sedes regionales. Solo cantidades: TI no accede a los expedientes. Una sede proyectada no recibe nada, pero admite su dotación, que rige al activarla.">
    <h3 className="structure-title">Nivel central</h3>
    <div className="structure-coverage">
      <p><strong>Regiones que atiende el nivel central:</strong> {centralRegions.length ? centralRegions.map(code => regionName(code)).join(', ') : <span className="muted">ninguna declarada</span>}</p>
      <button className="sm" onClick={editCentral}><Icon name="layers" size={16}/>Editar cobertura del nivel central</button>
      <CoverageHistory ctx={ctx} periods={periodsOf(list(centralCoverage, 'history'), regions)}/>
    </div>
    <Table rows={central} columns={[
      {name: 'Ámbito', render: r => <div>{scopeCell(r as ScopeRow)}{(r as ScopeRow).state !== 'ACTIVA' && <> {state(r as ScopeRow)}</>}</div>},
      {name: 'Jefatura o Dirección', render: r => <div>{head(r as ScopeRow)}{subrogated(r as ScopeRow)}</div>},
      {name: 'Dotación y volumen abierto', render: r => load(r as ScopeRow)},
      {name: 'Acciones', render: r => actions(r as ScopeRow)},
    ]}/>
    <h3 className="structure-title">Sedes regionales</h3>
    <Table rows={sites} empty="No hay sedes regionales registradas." emptyIcon="layers" columns={[
      {name: 'Sede', render: r => scopeCell(r as ScopeRow)},
      {name: 'Región y cobertura', render: r => regionCell(r as ScopeRow)},
      {name: 'Estado', render: r => <div>{state(r as ScopeRow)}{number(r, 'opens_ms') > 0 && <small className="structure-sub muted">Apertura <span className="nowrap">{when(r.opens_ms)}</span></small>}</div>},
      {name: 'Coordinación regional', render: r => <div>{head(r as ScopeRow)}{subrogated(r as ScopeRow)}</div>},
      {name: 'Dotación y volumen abierto', render: r => load(r as ScopeRow)},
      {name: 'Acciones', render: r => actions(r as ScopeRow)},
    ]}/>
    {members && <MembersDialog ctx={ctx} scope={members} users={users} grants={grants} close={() => setMembers(null)}
      open={account => {setMembers(null); openProfile(account);}}/>}
  </Panel>;
}

/** Historia de cobertura: cada periodo con su inicio, su termino (o vigente), quien lo registro y el fundamento. */
function CoverageHistory({ctx, periods}: {ctx: Context; periods: Period[]}) {
  if (!periods.length) return null;
  return <details className="structure-history">
    <summary>Historia de cobertura ({periods.length})</summary>
    <ul>
      {periods.map(p => <li key={p.id}>
        <strong>{p.label}</strong>{p.open && <>{' '}<span className="chip chip-ok">Vigente</span></>}
        <small className="structure-sub">Desde <span className="nowrap">{when(p.from)}</span>, {nameOf(ctx.catalog, p.by)}: <span className="preserve">{p.basis}</span></small>
        {!p.open && <small className="structure-sub">Hasta <span className="nowrap">{when(p.until)}</span>, {nameOf(ctx.catalog, p.untilBy)}: <span className="preserve">{p.untilBasis}</span></small>}
      </li>)}
    </ul>
  </details>;
}

const EFFECT_SUBJECTS: Record<TerritorialKind, string> = {CIERRE: 'la sede', COBERTURA: 'la sede', REGION: 'la sede', NIVEL_CENTRAL: 'el nivel central'};
/** Efecto territorial calculado por el servidor para la orden (vista previa), con las regiones que quedan sin atencion. */
function TerritorialEffect({kind, lines, unattended}: {kind: TerritorialKind; lines: EffectLine[]; unattended: string[]}) {
  return <>
    <h3 className="action-review-title">Efecto territorial</h3>
    {lines.length === 0
      ? <p className="field-help">Hoy {EFFECT_SUBJECTS[kind]} no atiende regiones: la orden no deja ninguna región sin atención.</p>
      : <>
        <p className="field-help">Regiones que hoy atiende {EFFECT_SUBJECTS[kind]} y quién las atenderá después de la orden:</p>
        <ul className="territorial-effect">
          {lines.map(l => <li key={l.region}><strong>{l.label}</strong>{' '}{l.unattended ? <span className="chip chip-warn"><Icon name="alert" size={14}/>{l.text}</span> : <span>{l.text}</span>}</li>)}
        </ul>
      </>}
    {unattended.length > 0
      ? <Callout tone="warn" title={(unattended.length === 1 ? 'Queda sin atención: ' : 'Quedan sin atención: ') + unattended.join(', ')}>
        Ninguna sede activa ni el nivel central atenderá {unattended.length === 1 ? 'esa región' : 'esas regiones'}. Para registrar la orden confírmalo expresamente y deja en el fundamento la decisión institucional.
      </Callout>
      : lines.length > 0 && <p className="field-help">Ninguna región queda sin atención. Confirma para registrar la orden.</p>}
  </>;
}

/** Funcionarios del ambito (funciones vigentes o programadas, o adscripcion principal), con acceso a su perfil. */
function MembersDialog({ctx, scope, users, grants, close, open}: {ctx: Context; scope: ScopeRow; users: Row[]; grants: Row[]; close: () => void; open: (account: Row) => void}) {
  const [now] = useState(() => Date.now());
  const dialog = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close);
  useEffect(() => {dialog.current?.showModal();}, []);
  const site = scope.level === 'REGIONAL';
  const people = membersOf(scope.id, users, grants.map(grantOf), now);
  const role = (code: string) => (site ? SITE_ROLE_LABELS[code] : undefined) ?? ROLE_LABELS[code] ?? humanize(code);
  return <dialog ref={dialog} className={'modal' + (leaving ? ' closing' : '')} aria-labelledby="members-title" onCancel={e => {e.preventDefault(); exit();}}>
    <header className="modal-head">
      <div>
        <p className="eyebrow">Funcionarios {site ? 'de la sede' : 'del ámbito'}</p>
        <h2 id="members-title">{scope.code} <small>{text(scope, 'name')}</small></h2>
      </div>
      <button type="button" className="ghost icon-only" aria-label="Cerrar" onClick={exit}><Icon name="close"/></button>
    </header>
    <div className="modal-body">
      <p className="field-help">Cuentas con funciones vigentes o programadas en {site ? 'la sede' : 'el ámbito'} y cuentas con su adscripción principal aquí. Para cambiar sus funciones abre su perfil.</p>
      <Table rows={people.map(m => ({...m.user, roles: m.roles, home: m.home}))} empty="Sin funcionarios." emptyIcon="people" columns={[
        {name: 'Nombre', render: u => <span className="cell-strong">{text(u, 'name')}</span>},
        {name: 'Usuario', render: u => <code>{text(u, 'username')}</code>},
        {name: 'Funciones aquí', render: u => (u.roles as string[]).length ? (u.roles as string[]).map(role).join(', ') : <span className="muted">Sin funciones</span>},
        {name: 'Adscripción', render: u => u.home ? 'Principal' : <span className="muted">Otra</span>},
        {name: 'Perfil', render: u => number(u, 'id') === ctx.user.id ? <span className="muted">Tu cuenta</span>
          : <button className="sm" onClick={() => open(users.find(x => number(x, 'id') === number(u, 'id')) ?? u)}><Icon name="shield" size={16}/>Perfil</button>},
      ]}/>
    </div>
    <footer className="modal-foot"><span/><div className="actions"><button type="button" onClick={exit}>Cerrar</button></div></footer>
  </dialog>;
}
