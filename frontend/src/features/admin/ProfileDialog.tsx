import {useEffect, useMemo, useRef, useState} from 'react';
import type {FormEvent} from 'react';
import type {Row} from '../../lib/rows';
import {bool, list, localInput, number, text, when} from '../../lib/rows';
import {command} from '../../lib/api';
import {scopeOf} from '../../lib/catalog';
import {humanize, LEVEL_LABELS, ROLE_LABELS, SCOPE_STATE_LABELS, SITE_ROLE_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {Busy, Callout, ErrorMessage} from '../../components/Primitives';
import {useExit} from '../../components/useExit';
import type {Entry, Grant, Level, Profile, RoleDef, ScopeItem, Wanted} from './profile';
import {columnsFor, countIn, diff, optionalRoles, preserved, duplicates, grantOf, groupScopes, hasTi, initial, keyOf, liveGrants, offered, payload, periodProblems, profileScopes, startFiltered, TI, toggle, visibleScopes} from './profile';

const fromInput = (value: string) => value ? new Date(value).getTime() : null;

/** Bajo este ancho la matriz no cabe: cada ambito se muestra como una tarjeta expandible. Igual que el corte movil de los modales. */
const NARROW = '(max-width: 640px)';
function useNarrow() {
  const [narrow, setNarrow] = useState(() => typeof matchMedia === 'function' && matchMedia(NARROW).matches);
  useEffect(() => {
    if (typeof matchMedia !== 'function') return;
    const media = matchMedia(NARROW), update = () => setNarrow(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);
  return narrow;
}

/** Envio pendiente: se conserva con su clave para reintentar igual si el resultado es incierto. */
type Pending = {key: string; data: Row};

/**
 * Perfil de la cuenta: TI fija en un acto todas sus funciones (PROFILE_SET). Matriz de ambitos x funciones por
 * ambito en escritorio y, en pantallas angostas, una tarjeta expandible por ambito (mismo estado de profile.ts);
 * casillas institucionales, vigencia opcional, resumen de lo que se otorga y revoca, y fundamento.
 * El servidor valida todo de nuevo (D2, una jefatura por ambito, cuenta deshabilitada, revision).
 */
export function ProfileDialog({ctx, account, grants, close}: {ctx: Context; account: Row; grants: Row[]; close: () => void}) {
  const [now] = useState(() => Date.now());
  const id = number(account, 'id'), enabled = bool(account, 'enabled');
  const live = useMemo(() => liveGrants(grants.map(grantOf), id, now), [grants, id, now]);
  const conflicts = duplicates(live);
  const roles = list(ctx.catalog, 'roles') as unknown as RoleDef[];
  const scoped = roles.filter(r => r.reach === 'AMBITO'), institutional = roles.filter(r => r.reach !== 'AMBITO');
  const regions = list(ctx.catalog, 'regions').map(r => ({code: text(r, 'code'), name: text(r, 'name')}));
  const scopeItems = profileScopes(list(ctx.catalog, 'scopes'), live, regions);
  const byId = new Map(scopeItems.map(s => [s.id, s]));
  const narrow = useNarrow();
  const [onlyWith, setOnlyWith] = useState(() => startFiltered(live)), [query, setQuery] = useState(''), [open, setOpen] = useState<ReadonlySet<number>>(() => new Set());
  const search = useRef<HTMLInputElement>(null);

  const [profile, setProfile] = useState<Profile>(() => initial(live));
  const [step, setStep] = useState<'edit' | 'confirm'>('edit');
  const [reason, setReason] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [uncertain, setUncertain] = useState(false);
  const pending = useRef<Pending | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close);
  useEffect(() => {dialog.current?.showModal();}, []);

  const wanted = payload(profile, now), changes = diff(live, wanted), unchanged = changes.grant.length === 0 && changes.revoke.length === 0;
  const invalid = periodProblems(profile, now), ti = hasTi(profile), locked = busy || uncertain;
  /** En una sede, DIRECCION se muestra como Coordinacion regional (D7). */
  const roleName = (role: string, level?: Level) => (level === 'REGIONAL' ? SITE_ROLE_LABELS[role] : undefined) ?? ROLE_LABELS[role] ?? humanize(role);
  const label = (role: string, scope: number | null | undefined) => roleName(role, scope != null ? byId.get(scope)?.level : undefined) + (scope != null ? ' en ' + scopeOf(ctx.catalog, scope) : '');
  const period = (from: number | null, until: number | null) => (from != null && from > now ? 'desde ' + when(from) : 'desde ahora') + (until != null ? ', hasta ' + when(until) : ', sin término');
  /** Periodo de una funcion existente (resumen de revocaciones): no es "desde ahora", ya rige o esta programada. */
  const current = (g: Grant) => (g.valid_from > now ? 'programada desde ' + when(g.valid_from) : g.valid_from > 0 ? 'vigente desde ' + when(g.valid_from) : 'vigente') + (g.valid_until != null ? ', hasta ' + when(g.valid_until) : ', sin término');
  const wantedPeriod = (w: Wanted) => period(w.from_at ? Date.parse(w.from_at) : null, w.until_at ? Date.parse(w.until_at) : null);

  const set = (role: string, scope: number | null, on: boolean) => setProfile(p => toggle(p, role, scope, on, live));
  const dates = (e: Entry, patch: Partial<Entry>) => setProfile(p => new Map(p).set(keyOf(e.role, e.scope), {...e, ...patch}));
  /**
   * Agregar una funcion: no con TI marcada (D2), ni en una cuenta deshabilitada, ni en un ambito que no admite
   * funciones (cerrado) ni como Jefatura en una sede (D7). Desmarcar lo que ya esta siempre se puede.
   */
  const blocked = (role: string, scope: ScopeItem | null) =>
    locked || !profile.has(keyOf(role, scope?.id ?? null)) && (role !== TI && ti || !enabled || scope != null && !offered(role, scope));

  function review() {
    if (invalid.length) {setError('Revisa la vigencia: el término debe ser posterior al inicio y a la fecha actual.'); return;}
    setError('');
    setStep('confirm');
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (step === 'edit') {review(); return;}
    if (!pending.current) pending.current = {key: crypto.randomUUID(), data: {grants: wanted, reason}};
    setBusy(true);
    setError('');
    try {
      const r = await command('PROFILE_SET', id, number(account, 'security_version'), pending.current.data, pending.current.key);
      pending.current = null;
      ctx.message(r.message || 'Perfil de la cuenta actualizado.');
      ctx.reload();
      exit();
    } catch (e) {
      const failure = e as Error & {status?: number};
      setError(failure.message);
      // Sin respuesta o error 5xx: no se sabe si quedo registrado; solo se permite reintentar con la misma clave.
      const ambiguous = !failure.status || failure.status >= 500;
      setUncertain(ambiguous);
      if (!ambiguous) pending.current = null;
    } finally {
      setBusy(false);
    }
  }
  /** Al salir tras un resultado incierto se recarga: si el perfil quedo registrado, su revision ya cambio. */
  const leave = () => {if (uncertain) ctx.reload(); exit();};

  const flip = (scope: number) => setOpen(o => {const next = new Set(o); if (!next.delete(scope)) next.add(scope); return next;});
  const showAll = () => {setOnlyWith(false); search.current?.focus();};
  const shown = visibleScopes(scopeItems, profile, live, onlyWith, query, open);
  const groups = groupScopes(shown, regions);
  const cell = (role: string, scope: ScopeItem | null, name: string) => <input type="checkbox" aria-label={name}
    checked={profile.has(keyOf(role, scope?.id ?? null))} disabled={blocked(role, scope)} onChange={e => set(role, scope?.id ?? null, e.target.checked)}/>;
  /** Estado o region bajo el codigo del ambito: las sedes muestran su region y, si no esta activa, su estado. */
  const detail = (s: ScopeItem) => [s.name, s.level === 'REGIONAL' ? s.regionName || 'Sin región' : '', s.state !== 'ACTIVA' ? (s.level === 'REGIONAL' ? 'sede ' : '') + (SCOPE_STATE_LABELS[s.state] ?? humanize(s.state)).toLowerCase() : ''].filter(Boolean).join(' · ');

  return <dialog ref={dialog} className={'modal profile-modal' + (leaving ? ' closing' : '')} aria-labelledby="profile-title" onCancel={e => {e.preventDefault(); if (!busy) leave();}}>
    <form onSubmit={submit}>
      <header className="modal-head">
        <div>
          <p className="eyebrow">Perfil de la cuenta · {step === 'edit' ? 'paso 1 de 2' : 'paso 2 de 2'}</p>
          <h2 id="profile-title">{text(account, 'name')} <code>{text(account, 'username')}</code></h2>
        </div>
        <button type="button" className="ghost icon-only" aria-label="Cerrar sin registrar" disabled={busy} onClick={leave}><Icon name="close"/></button>
      </header>
      <div className="modal-body">
        {conflicts.length > 0 && <Callout tone="warn" title="Hay funciones con más de un período en el mismo ámbito">
          <p>{conflicts.map(g => label(g.role_code, g.scope_id)).join('; ')}. El perfil admite un solo período por función y ámbito.</p>
          <p>Para resolverlo, cierra este perfil, revoca uno de los períodos desde la tabla <strong>Funciones otorgadas</strong> (botón Revocar) y vuelve a abrir el perfil.</p>
        </Callout>}
        {!enabled && <Callout tone="warn" title="Cuenta deshabilitada">Su perfil solo admite revocaciones. Habilítala con «Editar o desactivar» antes de otorgar funciones.</Callout>}
        {step === 'edit' ? <>
          {ti
            ? <Callout tone="neutral" title="TI es una función exclusiva">Una cuenta con TI no puede tener ninguna otra función, operativa ni de consulta. Al marcar TI se quitaron las demás; desmárcala para asignar otras funciones.</Callout>
            : <p className="field-help">Marca las funciones por ámbito. Lo que ya tiene la cuenta aparece marcado; desmarcarlo la revoca.</p>}
          <h3 className="profile-subtitle">Funciones por ámbito</h3>
          <div className="profile-scope-tools">
            <label className="check"><input type="checkbox" checked={onlyWith} onChange={e => setOnlyWith(e.target.checked)}/><span>Solo ámbitos con funciones</span></label>
            <label className="input-icon"><Icon name="search" size={18}/><input ref={search} type="search" aria-label="Buscar ámbito por código, nombre o región" placeholder="Buscar ámbito por código, nombre o región" value={query} onChange={e => setQuery(e.target.value)}/></label>
          </div>
          <p className="field-help" aria-live="polite">Mostrando {shown.length} de {scopeItems.length} ámbitos.</p>
          {shown.length === 0 ? <div className="profile-scope-empty">
            <p>{query.trim() ? 'Ningún ámbito coincide con la búsqueda' + (onlyWith ? ' entre los que tienen funciones.' : '.') : 'La cuenta no tiene funciones por ámbito.'}</p>
            {onlyWith && <button type="button" className="sm" onClick={showAll}>{query.trim() ? 'Buscar en todos los ámbitos' : 'Mostrar todos los ámbitos'}</button>}
          </div> : groups.map(g => {
            const columns = columnsFor(g.level, scoped, live, scopeItems);
            return <section key={g.level} className="profile-group" aria-labelledby={'profile-group-' + g.level}>
              <h4 id={'profile-group-' + g.level} className="profile-group-title">{LEVEL_LABELS[g.level]} <small>({g.items.length})</small></h4>
              {g.level === 'REGIONAL' && <p className="field-help">En las sedes la Coordinación regional se registra como Dirección de la sede y no hay Jefatura. Una sede proyectada admite su dotación, que rige al activarla.</p>}
              {narrow
                ? <ul className="profile-scope-cards">
                  {g.items.map(s => {
                    const count = countIn(profile, s.id, optionalRoles(roles)), expanded = open.has(s.id);
                    return <li key={s.id} className={'profile-scope-card' + (expanded ? ' open' : '')}>
                      <h5>
                        <button type="button" className="profile-scope-toggle" aria-expanded={expanded} aria-controls={'profile-scope-' + s.id} onClick={() => flip(s.id)}>
                          <span className="profile-scope-text"><span className="cell-strong">{s.code}</span><small>{detail(s)}</small></span>
                          <span className={'chip' + (count > 0 ? ' chip-ok' : '')}>{count === 0 ? 'Sin funciones' : count === 1 ? '1 función' : count + ' funciones'}</span>
                          <Icon name="down" size={18}/>
                        </button>
                      </h5>
                      {expanded && <ul id={'profile-scope-' + s.id} className="profile-scope-roles">
                        {columns.map(r => <li key={r.code}><label className="check">{cell(r.code, s, roleName(r.code, g.level) + ' en ' + s.code)}<span>{roleName(r.code, g.level)}</span></label></li>)}
                      </ul>}
                    </li>;
                  })}
                </ul>
                : <div className="profile-matrix-scroll" role="region" aria-label={'Funciones por ámbito: ' + LEVEL_LABELS[g.level]} tabIndex={0}>
                  <table className="profile-matrix">
                    <thead><tr><th scope="col">{g.level === 'REGIONAL' ? 'Sede' : 'Ámbito'}</th>{columns.map(r => <th scope="col" key={r.code}>{roleName(r.code, g.level)}</th>)}</tr></thead>
                    <tbody>{g.items.map(s => <tr key={s.id}>
                      <th scope="row"><span className="cell-strong">{s.code}</span><small>{detail(s)}</small></th>
                      {columns.map(r => <td key={r.code}>{offered(r.code, s) || profile.has(keyOf(r.code, s.id)) ? cell(r.code, s, roleName(r.code, g.level) + ' en ' + s.code) : <span className="muted" aria-label={'No se ofrece ' + roleName(r.code, g.level) + ' en ' + s.code}>—</span>}</td>)}
                    </tr>)}</tbody>
                  </table>
                </div>}
            </section>;
          })}
          <h3 className="profile-subtitle">Funciones institucionales</h3>
          <div className="profile-institutional">
            {institutional.map(r => <label key={r.code} className="check">
              {cell(r.code, null, ROLE_LABELS[r.code] ?? r.code)}
              <span>{ROLE_LABELS[r.code] ?? humanize(r.code)}
                {r.code === TI && <small>{ti ? 'Exclusiva: impide marcar otras funciones.' : 'Exclusiva: al marcarla se quitan las demás funciones del perfil.'}</small>}
                {r.code !== TI && <small>Toda la institución, sin ámbito.</small>}
              </span>
            </label>)}
          </div>
          {preserved(profile, roles).length > 0 && <p className="field-help">Funciones por ámbito que esta vista aún no edita; se conservan al guardar: {preserved(profile, roles).map(e => label(e.role, e.scope)).join('; ')}.</p>}
          {profile.size > 0 && <>
            <h3 className="profile-subtitle">Vigencia (opcional)</h3>
            <p className="field-help">Sin inicio rige desde ahora; sin término no vence. Fechas en la zona horaria de este equipo ({Intl.DateTimeFormat().resolvedOptions().timeZone}).</p>
            <ul className="profile-periods">
              {[...profile.values()].map(e => <li key={keyOf(e.role, e.scope)} className={invalid.includes(keyOf(e.role, e.scope)) ? 'invalid' : ''}>
                <span className="cell-strong">{label(e.role, e.scope)}</span>
                <label>Desde<input type="datetime-local" disabled={locked} value={localInput(e.from)} onChange={x => dates(e, {from: fromInput(x.target.value)})}/></label>
                <label>Hasta<input type="datetime-local" disabled={locked} value={localInput(e.until)} onChange={x => dates(e, {until: fromInput(x.target.value)})}/></label>
              </li>)}
            </ul>
          </>}
          {profile.size === 0 && <Callout tone="neutral">El perfil quedará sin funciones: la cuenta solo verá su espacio personal.</Callout>}
        </> : <>
          <div className="profile-summary">
            <section>
              <h3 className="profile-subtitle"><Icon name="plus" size={16}/>Se otorgan</h3>
              {changes.grant.length ? <ul>{changes.grant.map(w => <li key={keyOf(w.role, w.scope_id ?? null)}>{label(w.role, w.scope_id)} <small>({wantedPeriod(w)})</small></li>)}</ul> : <p className="muted">Ninguna.</p>}
            </section>
            <section>
              <h3 className="profile-subtitle"><Icon name="close" size={16}/>Se revocan</h3>
              {changes.revoke.length ? <ul>{changes.revoke.map((g: Grant) => <li key={g.id}>{label(g.role_code, g.scope_id)} <small>({current(g)})</small></li>)}</ul> : <p className="muted">Ninguna.</p>}
            </section>
          </div>
          <p className="field-help">Cambiar la vigencia de una función la revoca y la vuelve a otorgar con el período nuevo. Al confirmar se cierran las sesiones abiertas de la cuenta; al volver a ingresar verá sus funciones nuevas.</p>
          <label>
            <span>Fundamento (acto institucional que respalda la decisión)<span className="required-mark" aria-hidden="true">*</span></span>
            <textarea required maxLength={1000} rows={3} disabled={locked} value={reason} onChange={e => setReason(e.target.value)}/>
          </label>
          {uncertain && <Callout tone="warn" title="No se conoce el resultado">Reintenta sin cambiar nada: se usa la misma clave y el perfil no se registra dos veces. Si cierras, la administración se recarga.</Callout>}
        </>}
        {error && <ErrorMessage>{error}</ErrorMessage>}
      </div>
      <footer className="modal-foot">
        <small>{unchanged ? 'El perfil no tiene cambios.' : `Se otorgan ${changes.grant.length} y se revocan ${changes.revoke.length}.`}</small>
        <div className="actions">
          {step === 'confirm'
            ? <button type="button" disabled={locked} onClick={() => {setError(''); setStep('edit');}}><Icon name="left" size={18}/>Volver</button>
            : <button type="button" disabled={busy} onClick={leave}>Cancelar</button>}
          <button className="primary" disabled={busy || unchanged || conflicts.length > 0}>
            {step === 'edit'
              ? <>Revisar cambios<Icon name="arrowRight" size={18}/></>
              : <Busy busy={busy} label="Registrando…" idle={<><Icon name="check" size={18}/>{uncertain ? 'Reintentar' : 'Confirmar perfil'}</>}/>}
          </button>
        </div>
      </footer>
    </form>
  </dialog>;
}
