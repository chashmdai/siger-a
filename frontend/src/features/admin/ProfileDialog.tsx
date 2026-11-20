import {useEffect, useMemo, useRef, useState} from 'react';
import type {FormEvent} from 'react';
import type {Row} from '../../lib/rows';
import {bool, list, number, text, when} from '../../lib/rows';
import {command} from '../../lib/api';
import {scopeOf} from '../../lib/catalog';
import {humanize, ROLE_LABELS} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {Busy, Callout, ErrorMessage} from '../../components/Primitives';
import {useExit} from '../../components/useExit';
import type {Entry, Grant, Profile, RoleDef, Wanted} from './profile';
import {diff, duplicates, grantOf, hasTi, initial, keyOf, liveGrants, payload, periodProblems, TI, toggle} from './profile';

/** Valor para <input type="datetime-local"> en la zona del navegador. */
const localInput = (ms: number | null) => ms == null ? '' : new Date(ms - new Date(ms).getTimezoneOffset() * 60000).toISOString().slice(0, 16);
const fromInput = (value: string) => value ? new Date(value).getTime() : null;

/** Envio pendiente: se conserva con su clave para reintentar igual si el resultado es incierto. */
type Pending = {key: string; data: Row};

/**
 * Perfil de la cuenta: TI fija en un acto todas sus funciones (PROFILE_SET). Matriz de ambitos x funciones por
 * ambito, casillas institucionales, vigencia opcional, resumen de lo que se otorga y revoca, y fundamento.
 * El servidor valida todo de nuevo (D2, una jefatura por ambito, cuenta deshabilitada, revision).
 */
export function ProfileDialog({ctx, account, grants, close}: {ctx: Context; account: Row; grants: Row[]; close: () => void}) {
  const [now] = useState(() => Date.now());
  const id = number(account, 'id'), enabled = bool(account, 'enabled');
  const live = useMemo(() => liveGrants(grants.map(grantOf), id, now), [grants, id, now]);
  const conflicts = duplicates(live);
  const roles = list(ctx.catalog, 'roles') as unknown as RoleDef[];
  const scoped = roles.filter(r => r.reach === 'AMBITO'), institutional = roles.filter(r => r.reach !== 'AMBITO');
  const used = new Set(live.map(g => g.scope_id));
  const scopes = list(ctx.catalog, 'scopes').filter(s => s.enabled !== false || used.has(number(s, 'id')));

  const [profile, setProfile] = useState<Profile>(() => initial(live));
  const [step, setStep] = useState<'edit' | 'confirm'>('edit');
  const [reason, setReason] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [uncertain, setUncertain] = useState(false);
  const pending = useRef<Pending | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close);
  useEffect(() => {dialog.current?.showModal();}, []);

  const wanted = payload(profile, now), changes = diff(live, wanted), unchanged = changes.grant.length === 0 && changes.revoke.length === 0;
  const invalid = periodProblems(profile, now), ti = hasTi(profile), locked = busy || uncertain;
  const label = (role: string, scope: number | null | undefined) => (ROLE_LABELS[role] ?? humanize(role)) + (scope != null ? ' en ' + scopeOf(ctx.catalog, scope) : '');
  const period = (from: number | null, until: number | null) => (from != null && from > now ? 'desde ' + when(from) : 'desde ahora') + (until != null ? ', hasta ' + when(until) : ', sin término');
  const wantedPeriod = (w: Wanted) => period(w.from_at ? Date.parse(w.from_at) : null, w.until_at ? Date.parse(w.until_at) : null);

  const set = (role: string, scope: number | null, on: boolean) => setProfile(p => toggle(p, role, scope, on, live));
  const dates = (e: Entry, patch: Partial<Entry>) => setProfile(p => new Map(p).set(keyOf(e.role, e.scope), {...e, ...patch}));
  /** Agregar una funcion: no con TI marcada (D2), ni en una cuenta deshabilitada, ni en un ambito deshabilitado. */
  const blocked = (role: string, scope: number | null, scopeEnabled = true) =>
    locked || !profile.has(keyOf(role, scope)) && (role !== TI && ti || !enabled || !scopeEnabled);

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

  const cell = (role: string, scope: number | null, name: string, scopeEnabled = true) => <input type="checkbox" aria-label={name}
    checked={profile.has(keyOf(role, scope))} disabled={blocked(role, scope, scopeEnabled)} onChange={e => set(role, scope, e.target.checked)}/>;

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
          <div className="profile-matrix-scroll" role="region" aria-label="Funciones por ámbito" tabIndex={0}>
            <table className="profile-matrix">
              <thead><tr><th scope="col">Ámbito</th>{scoped.map(r => <th scope="col" key={r.code}>{ROLE_LABELS[r.code] ?? humanize(r.code)}</th>)}</tr></thead>
              <tbody>{scopes.map(s => {
                const sid = number(s, 'id'), on = s.enabled !== false;
                return <tr key={sid}>
                  <th scope="row"><span className="cell-strong">{text(s, 'code')}</span><small>{text(s, 'name')}{on ? '' : ' (deshabilitado)'}</small></th>
                  {scoped.map(r => <td key={r.code}>{cell(r.code, sid, (ROLE_LABELS[r.code] ?? r.code) + ' en ' + text(s, 'code'), on)}</td>)}
                </tr>;
              })}</tbody>
            </table>
          </div>
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
              {changes.revoke.length ? <ul>{changes.revoke.map((g: Grant) => <li key={g.id}>{label(g.role_code, g.scope_id)} <small>({period(g.valid_from, g.valid_until)})</small></li>)}</ul> : <p className="muted">Ninguna.</p>}
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
