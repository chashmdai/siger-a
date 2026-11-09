import {useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {uploadDocument} from '../../lib/api';
import type {Row} from '../../lib/rows';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {Busy, ErrorMessage} from '../../components/Primitives';

/** Original aportado durante la primera atencion abierta: se adjunta a la COM, separado de la gestion. */
export function OriginalUpload({ctx, row}: {ctx: Context; row: Row}) {
  const [file, setFile] = useState<File | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const key = useRef(crypto.randomUUID());
  async function upload(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    setError('');
    try {
      await uploadDocument({kind: 'COM', target: String(row.communication_id), type: 'OTRO', key: key.current}, file);
      ctx.message('Original conservado en cuarentena dentro de la misma atención.');
      ctx.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return <details className="disclosure">
    <summary><Icon name="upload" size={18}/>Agregar un original aportado durante esta atención<Icon name="down" size={18}/></summary>
    <div className="disclosure-body">
      <p className="muted">No es un documento de gestión profesional. Se conserva aparte y en cuarentena, solo durante la primera atención abierta.</p>
      <form onSubmit={upload} className="file-picker">
        <input type="file" required disabled={busy} aria-label="Elegir archivo original" onChange={e => {setFile(e.target.files?.[0] ?? null); key.current = crypto.randomUUID();}}/>
        <button className="primary" disabled={busy || !file}><Busy busy={busy} label="Conservando…" idle="Conservar original"/></button>
      </form>
      {error && <ErrorMessage>{error}</ErrorMessage>}
    </div>
  </details>;
}
