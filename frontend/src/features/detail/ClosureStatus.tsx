import type {Row} from '../../lib/rows';
import {Icon} from '../../components/Icon';

export const blockersOf = (row: Row, key: string): string[] => Array.isArray(row[key]) ? (row[key] as unknown[]).map(String) : [];

/**
 * Condiciones calculadas por el servidor. «close» corresponde a confirmar el cierre (can_close /
 * closure_blockers); «request», a que el responsable solicite el término (can_request_close /
 * close_request_blockers). Son facultades distintas y no se infieren una de otra.
 */
export function ClosureStatus({row, action = 'close'}: {row: Row; action?: 'close' | 'request'}) {
  const request = action === 'request';
  const blockers = blockersOf(row, request ? 'close_request_blockers' : 'closure_blockers');
  const ready = Boolean(row[request ? 'can_request_close' : 'can_close']);
  const title = request ? (ready ? 'Puedes solicitar el término' : 'Antes de solicitar el término') : (ready ? 'Sin bloqueos para el cierre' : 'Antes de cerrar');
  return <section className={'closure-status' + (ready ? ' ready' : '')} aria-label={request ? 'Condiciones para solicitar el término' : 'Condiciones de cierre'}>
    <h3><Icon name={ready ? 'checkCircle' : 'lock'} size={18}/>{title}</h3>
    {blockers.length > 0
      ? <ul>{blockers.map(b => <li key={b}>{b}</li>)}</ul>
      : <p>{request ? 'La jefatura revisa la solicitud y decide el cierre.' : 'La decisión debe registrarse expresamente, con fundamento.'}</p>}
  </section>;
}
