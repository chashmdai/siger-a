import {useEffect, useRef} from 'react';
import type {Row} from '../../lib/rows';
import {list, obj, text, when} from '../../lib/rows';
import {CHANNEL_LABELS, humanize, PERSON_ROLE_LABELS} from '../../lib/labels';
import type {StatCodes} from '../../lib/person/personModel.ts';
import {canonicalInstitutions, canonicalList, codeLabel, NO_CODES, personFacts} from '../../lib/person/personModel.ts';
import {regionName} from '../../lib/person/catalog.ts';
import {Icon} from '../../components/Icon';
import {useExit} from '../../components/useExit';
import {LABELS, optionLabel, OPTIONS} from './schema';
import {displayValue} from './adult/adultForm.ts';
import emblem from '../../assets/emblema-320.png';

/** Valor visible de un campo: etiqueta de la opcion cerrada, rotulo del listado o el texto registrado (nunca un codigo). */
function shown(key: string, value: string, codes: StatCodes) {
  if (key === 'modality') return value === 'NNA' ? 'Atención directa a NNA' : 'Persona adulta o atención general';
  if (OPTIONS[key]) return optionLabel(key, value).replaceAll('_', ' ');
  if (key === 'relationship_code') return codeLabel(codes.relationship, value);
  if (key === 'facts_region_code') return regionName(value);
  return displayValue(key, value);
}

/**
 * Ficha completa o version anterior, imprimible. Solo muestra los campos informados. Lee las versiones 1.0, 1.1 y 1.2:
 * las personas en su vista canonica (persona-1, con el texto libre anterior como registrado antes).
 */
export function Preview({data, close, codes = NO_CODES}: {data: Row; close: () => void; codes?: StatCodes}) {
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
            <p className="eyebrow">SIGER · Vista previa de la ficha</p>
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
        <p className="muted">Solo se muestran los campos informados; los ausentes permanecen sin informar. Los datos de identificación y contacto son declarados: no se verificaron con ClaveÚnica, Registro Civil ni PISEE.</p>
        <dl className="preview-fields">
          {Object.entries(LABELS).filter(([k]) => text(f, k)).map(([k, l]) => <div key={k}><dt>{l}</dt><dd className="preserve">{shown(k, text(f, k), codes)}</dd></div>)}
        </dl>
        {canonicalList(list(f, 'people')).map((p, i) => <section key={i} className="preview-person">
          <h3>Persona relacionada {i + 1} · {humanize(p.role, PERSON_ROLE_LABELS)}</h3>
          <dl className="preview-fields">{personFacts(p, codes).map(([l, v]) => <div key={l}><dt>{l}</dt><dd className="preserve">{v}</dd></div>)}</dl>
        </section>)}
        {canonicalInstitutions(f.institutions_list).map((x, i) => <section key={'i' + i} className="preview-person">
          <h3>Institución {i + 1}</h3>
          <dl className="preview-fields">{([['Nombre', x.name], ['Tipo', codeLabel(codes.institution, x.type_code)], ['Rol o vínculo', x.role], ['Región', x.region_code ? regionName(x.region_code) : ''], ['Comuna', x.commune]] as [string, string][])
            .filter(([, v]) => v).map(([l, v]) => <div key={l}><dt>{l}</dt><dd>{v}</dd></div>)}</dl>
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
