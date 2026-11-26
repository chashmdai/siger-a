import {useCallback, useEffect, useRef, useState} from 'react';
import {postJson} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {Cancelled, createQueue, errorText} from './statsModel';

/**
 * Fila unica de consultas estadisticas de la pestana (el servidor admite una consulta a la vez por cuenta) con su cache de
 * cinco minutos. La cache se vacia al perder o cerrar la sesion (evento siger-session) y al cambiar de cuenta.
 */
export const queue = createQueue(body => postJson('/stats/query', body));
if (typeof window !== 'undefined') window.addEventListener('siger-session', () => queue.clear());

export type StatState = {status: 'idle' | 'loading' | 'ok' | 'error'; data: Row | null; at: number; error: string; status429?: boolean};

/**
 * Consulta de una pantalla: se envia al cambiar el cuerpo (o con Actualizar), espera su turno en la fila y, si la pantalla
 * ya no la necesita, su resultado solo queda en la cache. body nulo = no consultar.
 */
export function useStat(body: Row | null, user: number) {
  const key = body ? JSON.stringify(body) : '';
  const [state, setState] = useState<StatState>({status: body ? 'loading' : 'idle', data: null, at: 0, error: ''});
  const [nonce, setNonce] = useState(0);
  const force = useRef(false);
  useEffect(() => {
    if (!body) {setState({status: 'idle', data: null, at: 0, error: ''}); return;}
    let alive = true;
    setState(s => ({...s, status: 'loading', error: ''}));
    const refresh = force.current;
    force.current = false;
    queue.query(body, String(user), refresh).then(r => {
      if (alive) setState({status: 'ok', data: r.data, at: r.at, error: ''});
    }, (e: Error & {status?: number}) => {
      if (alive && !(e instanceof Cancelled)) setState({status: 'error', data: null, at: 0, error: errorText(e), status429: e.status === 429});
    });
    return () => {alive = false;};
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, user, nonce]);
  const refresh = useCallback(() => {force.current = true; setNonce(n => n + 1);}, []);
  return {...state, refresh};
}
