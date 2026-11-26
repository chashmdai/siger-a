import {useCallback, useEffect, useState} from 'react';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {ErrorMessage, Panel} from '../../components/Primitives';
import {command, request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {text} from '../../lib/rows';
import type {Query, StatsCatalog} from './statsModel';
import {encodeRoute, fromBody, requestBody} from './statsModel';
import {queryTitle} from './ResultView';

const uuid = () => (globalThis.crypto?.randomUUID?.() ?? Date.now().toString(16) + Math.random().toString(16).slice(2));

/**
 * Vistas guardadas (E10, STAT_VIEW_*): las propias y las compartidas. Abrir pone su consulta en la direccion; cada cuenta
 * la ejecuta con su propio alcance. Compartir exige Gestion estadistica (lo decide el servidor: can_share).
 */
export function SavedViews({ctx, cat, q}: {ctx: Context; cat: StatsCatalog; q: Query | null}) {
  const [views, setViews] = useState<Row[]>([]), [error, setError] = useState(''), [busy, setBusy] = useState(0);
  const load = useCallback(() => {request<Row[]>('/stats/views').then(setViews, (e: Error) => setError(e.message));}, []);
  useEffect(load, [load]);
  const save = () => {
    if (!q) return;
    const definition = requestBody(q, cat);
    ctx.act({title: 'Guardar vista', type: 'STAT_VIEW_SAVE', id: null, revision: null, note: 'La vista guarda la consulta, no las cifras: al abrirla se calcula de nuevo con tu alcance.',
      fields: [{key: 'title', label: 'Nombre de la vista', required: true, max: 200},
        {key: 'definition', label: 'Consulta', type: 'custom', initial: definition, render: () => <p className="field-help">Consulta: {queryTitle(cat, q)}.</p>}],
      after: () => load()});
  };
  const share = async (v: Row, shared: boolean) => {
    setBusy(Number(v.id));
    setError('');
    try {
      await command('STAT_VIEW_SHARE', Number(v.id), Number(v.revision), {shared}, uuid());
      ctx.message(shared ? 'Vista compartida con las cuentas con Reportes.' : 'La vista ya no está compartida.');
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(0);
    }
  };
  const remove = (v: Row) => ctx.act({title: 'Borrar vista', type: 'STAT_VIEW_DELETE', id: Number(v.id), revision: Number(v.revision), fields: [],
    note: `Se borrará la vista «${text(v, 'title')}». Las cifras no cambian.`, after: () => load()});
  return <Panel title="Vistas guardadas" icon="layers" actions={q ? <button type="button" className="sm" onClick={save}><Icon name="plus" size={16}/>Guardar vista</button> : undefined}>
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {views.length === 0 ? <p className="field-help">Aún no hay vistas. Guarda una consulta para volver a ella o compartirla.</p>
      : <ul className="stat-views">{views.map(v => <li key={text(v, 'id')}>
        <button type="button" className="link" onClick={() => {location.hash = encodeRoute('explorar', fromBody((v.definition ?? {}) as Row));}}>{text(v, 'title')}</button>
        <span className="field-help">{v.mine ? (v.shared ? 'Tuya · compartida' : 'Tuya') : 'Compartida por ' + (text(v, 'owner') || 'otra cuenta')}</span>
        <span className="actions">
          {v.can_share === true && <button type="button" className="sm" disabled={busy === Number(v.id)} onClick={() => void share(v, v.shared !== true)}>{v.shared ? 'Dejar de compartir' : 'Compartir'}</button>}
          {v.can_edit === true && <button type="button" className="sm ghost" onClick={() => remove(v)}>Borrar</button>}
        </span>
      </li>)}</ul>}
  </Panel>;
}
