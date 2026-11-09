import {useEffect, useRef} from 'react';
import type {Row} from '../../lib/rows';
import {list, obj, text, when} from '../../lib/rows';
import {CHANNEL_LABELS, humanize, PERSON_ROLE_LABELS} from '../../lib/labels';
import {Icon} from '../../components/Icon';
import {useExit} from '../../components/useExit';
import {LABELS, optionLabel, OPTIONS, PREVIEW_PERSON_LABELS} from './schema';
import emblem from '../../assets/emblema-320.png';

/** Valor visible de un campo: etiqueta de la opcion cerrada o el texto registrado. */
const shown = (key: string, value: string) => key === 'modality' ? (value === 'NNA' ? 'Atención directa a NNA' : 'Persona adulta o atención general') : OPTIONS[key] ? optionLabel(key, value) : value;

/** Ficha completa o version anterior, imprimible. Solo muestra los campos informados. */
export function Preview({data, close}: {data: Row; close: () => void}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close);
  useEffect(() => {ref.current?.showModal();}, []);
  const f = obj(data, 'form'), origin = obj(data, 'origin');
  return <dialog ref={ref} className={'modal attention-preview' + (leaving ? ' closing' : '')} aria-labelledby="preview-title" onCancel={e => {e.preventDefault(); exit();}}>
    <div className="modal-frame">
      <header className="modal-head">
        <div className="preview-brand">
          <img src={emblem} alt="" width={44} height={43}/>
          <div>
            <p className="eyebrow">SIGER A · Candidata de pruebas · Datos sintéticos</p>
            <h2 id="preview-title">Ficha de atención {text(data, 'folio')}</h2>
          </div>
        </div>
        <button type="button" className="ghost icon-only print-hide" aria-label="Cerrar" onClick={exit}><Icon name="close"/></button>
      </header>
      <div className="modal-body">
        <dl className="preview-meta">
          {origin.folio != null && <div><dt>Ingreso</dt><dd>{text(origin, 'folio')} · {humanize(origin.channel, CHANNEL_LABELS)} · Recepción: {when(origin.received_ms)}</dd></div>}
          {data.registered_ms != null && <div><dt>Versión registrada</dt><dd>{when(data.registered_ms)}</dd></div>}
          {data.author_name != null && <div><dt>Registrado por</dt><dd>{text(data, 'author_name')}</dd></div>}
          {data.reason != null && <div><dt>Motivo de la versión</dt><dd>{text(data, 'reason')}</dd></div>}
        </dl>
        <p className="muted">Solo se muestran los campos informados; los ausentes permanecen sin informar.</p>
        <dl className="preview-fields">
          {Object.entries(LABELS).filter(([k]) => text(f, k)).map(([k, l]) => <div key={k}><dt>{l}</dt><dd className="preserve">{shown(k, text(f, k)).replaceAll('_', ' ')}</dd></div>)}
        </dl>
        {list(f, 'people').map((p, i) => <section key={i} className="preview-person">
          <h3>Persona relacionada {i + 1}</h3>
          <dl className="preview-fields">{Object.entries(p).filter(([, v]) => v).map(([k, v]) => <div key={k}><dt>{PREVIEW_PERSON_LABELS[k] ?? k}</dt><dd>{k === 'role' ? humanize(v, PERSON_ROLE_LABELS) : String(v)}</dd></div>)}</dl>
        </section>)}
      </div>
      <footer className="modal-foot print-hide">
        <span/>
        <div className="actions">
          <button onClick={exit}>Cerrar</button>
          <button className="primary" onClick={() => window.print()}><Icon name="printer" size={18}/>Imprimir ficha</button>
        </div>
      </footer>
    </div>
  </dialog>;
}
