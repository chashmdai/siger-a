import type {Row} from '../../lib/rows';
import {Icon} from '../../components/Icon';

/** Condiciones de cierre calculadas por el servidor (closure_blockers / can_close). */
export function ClosureStatus({row}: {row: Row}) {
  const blockers = Array.isArray(row.closure_blockers) ? row.closure_blockers as string[] : [];
  const ready = Boolean(row.can_close);
  return <section className={'closure-status' + (ready ? ' ready' : '')} aria-label="Condiciones de cierre">
    <h3><Icon name={ready ? 'checkCircle' : 'lock'} size={18}/>{ready ? 'Sin bloqueos para el cierre' : 'Antes de cerrar'}</h3>
    {blockers.length > 0
      ? <ul>{blockers.map(b => <li key={b}>{b}</li>)}</ul>
      : <p>La decisión debe registrarse expresamente, con fundamento.</p>}
  </section>;
}
