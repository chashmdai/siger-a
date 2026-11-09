import {useEffect, useRef, useState} from 'react';
import {discardDraft, draftDocuments, stageIntakeDocument} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {number, text} from '../../lib/rows';
import {scopeOf} from '../../lib/catalog';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {Busy, ErrorMessage} from '../../components/Primitives';

/**
 * Originales preparados antes de registrar la atencion. Se incorporan al confirmar el ingreso;
 * pertenecen al ambito en que se prepararon.
 */
export function PreparedFiles({ctx, scope, selected, select, disabled, pendingFile}: {ctx: Context; scope: number; selected: string[]; select: (v: string[]) => void; disabled: boolean; pendingFile: (v: boolean) => void}) {
  const [rows, setRows] = useState<Row[]>([]), [file, setFile] = useState<File | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState(''), [epoch, setEpoch] = useState(0);
  const key = useRef(crypto.randomUUID());
  const load = () => draftDocuments().then(setRows);
  useEffect(() => {void load().catch(e => setError((e as Error).message));}, []);
  const clearFile = () => {setFile(null); pendingFile(false); setEpoch(x => x + 1);};

  async function prepare() {
    if (!file) return;
    setBusy(true);
    setError('');
    try {
      const result = await stageIntakeDocument(String(scope), key.current, file);
      select([...new Set([...selected, text(result, 'token')])]);
      clearFile();
      key.current = crypto.randomUUID();
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function remove(token: string) {
    void discardDraft(token).then(() => {select(selected.filter(t => t !== token)); return load();}).catch(e => setError((e as Error).message));
  }
  return <fieldset disabled={disabled || busy} className="panel form-section">
    <legend className="sr-only">Originales aportados durante el ingreso</legend>
    <div className="panel-head"><div><h2><Icon name="upload"/>Originales aportados</h2><p>Opcionales. Máximo 25 MiB por archivo; quedan en cuarentena. Solo datos sintéticos en esta candidata.</p></div></div>
    <div className="file-picker">
      <input key={epoch} type="file" aria-label="Elegir archivo original" onChange={e => {setFile(e.target.files?.[0] ?? null); pendingFile(Boolean(e.target.files?.length)); key.current = crypto.randomUUID();}}/>
      <button type="button" disabled={!file} onClick={() => void prepare()}><Busy busy={busy} label="Preparando…" idle={<><Icon name="upload" size={18}/>Preparar adjunto</>}/></button>
      {file && <button type="button" className="ghost" onClick={clearFile}>Retirar archivo elegido</button>}
    </div>
    {rows.length > 0 && <ul className="draft-list">
      {rows.map(r => <li className="draft-row" key={text(r, 'token')}>
        <label className="check">
          <input type="checkbox" disabled={number(r, 'scope_id') !== scope} checked={selected.includes(text(r, 'token'))} onChange={e => select(e.target.checked ? [...selected, text(r, 'token')] : selected.filter(t => t !== text(r, 'token')))}/>
          <span><Icon name="file" size={16}/> {text(r, 'name')}<small>{scopeOf(ctx.catalog, r.scope_id)}{number(r, 'scope_id') !== scope ? ' · pertenece a otro ámbito' : ''}</small></span>
        </label>
        <button type="button" className="ghost sm" onClick={() => remove(text(r, 'token'))}>Retirar preparación</button>
      </li>)}
    </ul>}
    {error && <ErrorMessage>{error}</ErrorMessage>}
  </fieldset>;
}
