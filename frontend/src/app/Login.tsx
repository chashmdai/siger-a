import {useState} from 'react';
import type {FormEvent, KeyboardEvent, ReactNode} from 'react';
import * as api from '../lib/api';
import type {User} from '../lib/access';
import {firstName} from '../lib/labels';
import {Icon} from '../components/Icon';
import {Busy, ErrorMessage} from '../components/Primitives';
import {Thread} from './Shell';
import emblem from '../assets/emblema-320.png';

/** Marco de las pantallas sin sesion: identidad institucional sobre blanco y formulario. */
function AuthFrame({children}: {children: ReactNode}) {
  return <div className="auth">
    <div className="environment auth-environment" role="note"><span className="environment-dot" aria-hidden="true"/>Candidata de pruebas · Datos sintéticos</div>
    <div className="auth-card">
      <section className="auth-brand" aria-label="SIGER A">
        <img src={emblem} alt="Emblema de la Defensoría de la Niñez" width={150} height={147}/>
        <Thread/>
        <h1>SIGER A</h1>
        <p className="auth-tagline">Gestión de Comunicaciones, Solicitudes, Requerimientos y Casos</p>
        <p className="auth-institution">Defensoría de la Niñez</p>
        <ul className="auth-points">
          <li><Icon name="attention" size={18}/>Un registro desde el primer contacto</li>
          <li><Icon name="history" size={18}/>Cada decisión conserva su autoría y su fundamento</li>
          <li><Icon name="shield" size={18}/>Accesos según tus funciones vigentes</li>
        </ul>
      </section>
      <section className="auth-form">{children}</section>
    </div>
  </div>;
}

/** Pantalla de conexion inicial. */
export function Splash({error}: {error: string}) {
  return <div className="splash" role="status">
    <img src={emblem} alt="" width={84} height={82}/>
    <p>{error ? 'No fue posible conectar con SIGER A.' : 'Conectando con SIGER A…'}</p>
    {error && <ErrorMessage>{error}</ErrorMessage>}
  </div>;
}

/** Campo de clave con opcion de mostrarla y aviso de mayusculas activadas. */
function PasswordInput({label, value, set, autoComplete, minLength}: {label: string; value: string; set: (v: string) => void; autoComplete: string; minLength?: number}) {
  const [visible, setVisible] = useState(false), [caps, setCaps] = useState(false);
  const capsCheck = (e: KeyboardEvent<HTMLInputElement>) => setCaps(e.getModifierState?.('CapsLock') ?? false);
  return <label>
    {label}
    <span className="input-with-action">
      <input type={visible ? 'text' : 'password'} required value={value} minLength={minLength} autoComplete={autoComplete}
        onChange={e => set(e.target.value)} onKeyUp={capsCheck} onKeyDown={capsCheck} onBlur={() => setCaps(false)}/>
      <button type="button" className="ghost icon-only sm" aria-label={visible ? 'Ocultar clave' : 'Mostrar clave'} aria-pressed={visible} onClick={() => setVisible(v => !v)}>
        <Icon name={visible ? 'eyeOff' : 'eye'} size={18}/>
      </button>
    </span>
    {caps && <small className="caps-warning" role="status"><Icon name="alert" size={14}/> Mayúsculas activadas</small>}
  </label>;
}

export function Login({error, done}: {error: string; done: () => void}) {
  const [user, setUser] = useState(''), [pass, setPass] = useState(''), [message, setMessage] = useState(error), [busy, setBusy] = useState(false);
  async function login(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage('');
    try {
      await api.session(); // token CSRF vigente antes de enviar credenciales
      await api.request('/login', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({username: user, password: pass})});
      setPass('');
      done();
    } catch (e) {
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return <AuthFrame>
    <h2>Ingresar</h2>
    <p className="muted">Usa tu cuenta individual. Tu sesión determina qué puedes ver y registrar.</p>
    <form onSubmit={login} className="login-form">
      <label>Usuario
        <span className="input-icon"><Icon name="user" size={18}/><input value={user} required autoComplete="username" autoCapitalize="none" spellCheck={false} onChange={e => setUser(e.target.value)}/></span>
      </label>
      <PasswordInput label="Clave" value={pass} set={setPass} autoComplete="current-password"/>
      {message && <ErrorMessage>{message}</ErrorMessage>}
      <button className="primary block" disabled={busy}><Busy busy={busy} label="Ingresando…" idle={<>Ingresar<Icon name="arrowRight" size={18}/></>}/></button>
    </form>
    <p className="auth-note"><Icon name="lock" size={16}/><span>Las claves iniciales son aleatorias y están en el archivo privado <code>bootstrap-usuarios.csv</code> del directorio de datos. No existe una clave universal.</span></p>
  </AuthFrame>;
}

/** Cambio obligatorio de la clave temporal; al guardar, el servidor cierra la sesion. */
export function PasswordChange({user, done}: {user: User; done: () => void}) {
  const [current, setCurrent] = useState(''), [pass, setPass] = useState(''), [confirm, setConfirm] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const bytes = new TextEncoder().encode(pass).length;
  const checks: [boolean, string][] = [[pass.length >= 14, 'Al menos 14 caracteres'], [bytes > 0 && bytes <= 72, 'Máximo 72 bytes (los caracteres con tilde ocupan más)'], [pass !== '' && pass === confirm, 'Ambas claves nuevas coinciden']];
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (pass !== confirm) {setError('Las claves nuevas no coinciden.'); return;}
    setBusy(true);
    try {
      await api.postJson('/password', {current, password: pass});
      done();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return <AuthFrame>
    <h2>Hola, {firstName(user.name)}</h2>
    <p className="muted">Antes de continuar, reemplaza tu clave temporal por una personal. Al guardarla volverás a ingresar.</p>
    <form onSubmit={submit} className="login-form">
      <PasswordInput label="Clave temporal" value={current} set={setCurrent} autoComplete="current-password"/>
      <PasswordInput label="Clave nueva" value={pass} set={setPass} autoComplete="new-password" minLength={14}/>
      <PasswordInput label="Repetir clave nueva" value={confirm} set={setConfirm} autoComplete="new-password"/>
      <ul className="password-checks" aria-label="Requisitos de la clave">
        {checks.map(([ok, label]) => <li key={label} className={ok ? 'ok' : ''}><Icon name={ok ? 'checkCircle' : 'clock'} size={16}/>{label}<span className="sr-only">{ok ? ': cumplido' : ': pendiente'}</span></li>)}
      </ul>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      <button className="primary block" disabled={busy}><Busy busy={busy} label="Guardando…" idle="Guardar y volver a ingresar"/></button>
    </form>
  </AuthFrame>;
}
