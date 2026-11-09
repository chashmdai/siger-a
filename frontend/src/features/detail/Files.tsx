import {useState} from 'react';
import type {FormEvent} from 'react';
import {uploadDocument} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {bool, list, number, text, when} from '../../lib/rows';
import {DOCUMENT_TYPES} from '../../lib/fields';
import {DOCUMENT_LABELS, humanize} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Table} from '../../components/Table';
import type {Column} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {Busy, ErrorMessage, Panel} from '../../components/Primitives';

const TITLES: Record<string, string> = {COM: 'Documentos del ingreso', CAS: 'Documentos de la gestión profesional'};

function DocumentTable({documents, includeSource, empty}: {documents: Row[]; includeSource: boolean; empty: string}) {
  const source: Column[] = includeSource ? [{name: 'Comunicación', render: d => <span className="folio">{text(d, 'source_folio') || 'Ingreso'}</span>}] : [];
  return <Table rows={documents} emptyIcon="file" empty={empty} columns={[
    ...source,
    {name: 'Archivo', render: d => <span className="file-name"><Icon name="file" size={18}/>{text(d, 'name')}</span>},
    {name: 'Tipo / referencia', render: d => <>
      {humanize(text(d, 'document_type') || 'INGRESO', DOCUMENT_LABELS)}
      {d.note_id ? <small>Actuación #{number(d, 'note_id')}</small> : null}
      {d.previous_document_id ? <small>Versión de documento #{number(d, 'previous_document_id')}</small> : null}
      {text(d, 'description') && <small>{text(d, 'description')}</small>}
    </>},
    {name: 'Tamaño', render: d => <span className="tabular nowrap">{(number(d, 'size_bytes') / 1024).toLocaleString('es-CL', {maximumFractionDigits: 1})} KiB</span>},
    {name: 'Estado', render: d => <span className={'chip' + (bool(d, 'quarantined') ? ' chip-warn' : '')}>{bool(d, 'quarantined') ? 'En cuarentena' : bool(d, 'reserved') ? 'Reservado' : 'Disponible'}</span>},
    {name: 'Original', render: d => !bool(d, 'quarantined') ? <a className="button sm" href={`/api/documents/${number(d, 'id')}/download`}><Icon name="download" size={16}/>Descargar</a> : <span className="muted">Descarga bloqueada</span>},
  ]}/>;
}

/**
 * Documentos de una ficha. En REQ y Caso muestra tambien los originales del requiriente (solo lectura).
 * Toda carga queda en cuarentena; una version posterior referencia al documento anterior.
 */
export function Files({row, kind, ctx, editable}: {row: Row; kind: string; ctx: Context; editable: boolean}) {
  const [file, setFile] = useState<File | null>(null), [reserved, setReserved] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState(''), [epoch, setEpoch] = useState(0);
  const [docType, setDocType] = useState('OTRO'), [description, setDescription] = useState(''), [note, setNote] = useState(''), [previous, setPrevious] = useState(''), [uploadKey, setUploadKey] = useState(() => crypto.randomUUID());
  const documents = list(row, 'documents');
  // Solo se versiona el ultimo documento de cada cadena.
  const versionable = documents.filter(d => !documents.some(l => number(l, 'previous_document_id') === number(d, 'id')));

  async function upload(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    setError('');
    try {
      await uploadDocument({kind, target: String(number(row, 'id')), reserved: String(reserved), key: uploadKey, type: docType, description, note: note || undefined, previous: previous || undefined}, file);
      setFile(null);
      setEpoch(v => v + 1);
      setNote('');
      setPrevious('');
      setDescription('');
      setUploadKey(crypto.randomUUID());
      ctx.message('Original custodiado en cuarentena. No se ejecutó un antivirus.');
      ctx.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return <>
    {(kind === 'CAS' || kind === 'REQ') && <Panel title="Documentos del requiriente" icon="inbox" count={list(row, 'citizen_documents').length}
      description="Archivos del ingreso original y de comunicaciones posteriores admitidas. Solo lectura: no se duplican ni modifican los originales.">
      <DocumentTable documents={list(row, 'citizen_documents')} includeSource={true} empty="Sin documentos del requiriente"/>
    </Panel>}
    <Panel title={TITLES[kind] ?? 'Documentos del Requerimiento'} icon="file" count={documents.length}
      description="Los originales se guardan en cuarentena con huella SHA-256. No hay antivirus ni visor integrados; solo personal autorizado libera archivos sintéticos tras una revisión externa.">
      <DocumentTable documents={documents} includeSource={false} empty="Sin documentos cargados"/>
      {editable && <details className="upload-box">
        <summary><Icon name="upload" size={18}/>{kind === 'CAS' ? 'Adjuntar documento de gestión' : 'Cargar original sintético'}<small>Máximo 25 MiB</small></summary>
        <form onSubmit={upload}>
          <label>Archivo<input key={epoch} type="file" required onChange={e => {setFile(e.target.files?.[0] ?? null); setUploadKey(crypto.randomUUID());}}/></label>
          {kind !== 'COM' && <div className="form-grid">
            <label>Tipo de documento<select value={docType} onChange={e => setDocType(e.target.value)}>{DOCUMENT_TYPES.map(v => <option key={v} value={v}>{DOCUMENT_LABELS[v] ?? v}</option>)}</select></label>
            <label>Actuación asociada (opcional)<select value={note} onChange={e => setNote(e.target.value)}>
              <option value="">Sin asociación</option>
              {list(row, 'notes').map(n => <option key={number(n, 'id')} value={number(n, 'id')}>#{number(n, 'id')} · {when(n.effective_ms)}</option>)}
            </select></label>
            <label>Nueva versión de (opcional)<select value={previous} onChange={e => setPrevious(e.target.value)}>
              <option value="">Documento nuevo</option>
              {versionable.map(d => <option key={number(d, 'id')} value={number(d, 'id')}>#{number(d, 'id')} {text(d, 'name')}</option>)}
            </select></label>
            <label>Descripción breve<input maxLength={500} value={description} onChange={e => setDescription(e.target.value)}/></label>
          </div>}
          {kind !== 'COM' && <label className="check"><input type="checkbox" checked={reserved} onChange={e => setReserved(e.target.checked)}/>Reservado</label>}
          {error && <ErrorMessage>{error}</ErrorMessage>}
          <button className="primary" disabled={busy}><Busy busy={busy} label="Conservando…" idle={<><Icon name="upload" size={18}/>Cargar documento</>}/></button>
        </form>
      </details>}
    </Panel>
  </>;
}
