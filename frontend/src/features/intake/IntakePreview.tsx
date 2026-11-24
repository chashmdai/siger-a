import {useEffect, useRef} from 'react';
import type {Row} from '../../lib/rows';
import {obj, text, when} from '../../lib/rows';
import {Icon} from '../../components/Icon';
import {useExit} from '../../components/useExit';
import type {StatCodes} from '../../lib/person/personModel.ts';
import {changeSummaryText, codeLabel, personFacts, ROLE_LABELS} from '../../lib/person/personModel.ts';
import {regionName} from '../../lib/person/catalog.ts';
import type {IntakeData} from './intakeModel.ts';
import {intakeFromSaved} from './intakeModel.ts';

/** Filas [rotulo, valor] informadas de la ficha de ingreso (sin codigos internos). */
function Facts({rows}: {rows: [string, string][]}) {
  const shown = rows.filter(([, v]) => v);
  return shown.length ? <dl className="preview-fields">{shown.map(([l, v]) => <div key={l}><dt>{l}</dt><dd className="preserve">{v}</dd></div>)}</dl> : null;
}

/** Lectura de la ficha de ingreso: quien presenta, personas, contacto seguro, hechos e instituciones. */
export function IntakeSummary({data, codes}: {data: IntakeData; codes: StatCodes}) {
  const presenter = personFacts(data.presenter, codes);
  const facts: [string, string][] = [['Región', data.facts_region_code ? regionName(data.facts_region_code) : ''], ['Comuna', data.facts_commune], ['Lugar', data.facts_place]];
  return <div className="intake-summary">
    <section className="preview-person">
      <h3>Quién presenta</h3>
      {presenter.length ? <Facts rows={presenter}/> : <p className="muted">{data.institutions_list.length ? 'Ingreso institucional: sin datos de una persona natural.' : 'Sin datos de quien presenta.'}</p>}
      {data.contact_restrictions && <Facts rows={[['Indicaciones de contacto seguro', data.contact_restrictions]]}/>}
    </section>
    {data.persons.map((p, i) => <section key={i} className="preview-person">
      <h3>Persona {i + 1} · {ROLE_LABELS[p.role] ?? p.role}</h3>
      <Facts rows={personFacts(p, codes)}/>
    </section>)}
    {facts.some(([, v]) => v) && <section className="preview-person"><h3>Lugar de los hechos</h3><Facts rows={facts}/></section>}
    {data.institutions_list.map((x, i) => <section key={'i' + i} className="preview-person">
      <h3>Institución {i + 1}</h3>
      <Facts rows={[['Nombre', x.name], ['Tipo', codeLabel(codes.institution, x.type_code)], ['Rol o vínculo', x.role], ['Región', x.region_code ? regionName(x.region_code) : ''], ['Comuna', x.commune]]}/>
    </section>)}
  </div>;
}

/** Version anterior de la ficha de ingreso (GET /api/intake/{id}/versions/{v}), en un dialogo imprimible. */
export function IntakePreview({version, folio, author, codes, close}: {version: Row; folio: string; author: string; codes: StatCodes; close: () => void}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [leaving, exit] = useExit(close);
  useEffect(() => {ref.current?.showModal();}, []);
  const summary = changeSummaryText(version.change_summary);
  return <dialog ref={ref} className={'modal attention-preview' + (leaving ? ' closing' : '')} aria-labelledby="intake-preview-title" onCancel={e => {e.preventDefault(); exit();}}>
    <div className="modal-frame">
      <header className="modal-head">
        <div><p className="eyebrow">SIGER · Ficha de ingreso</p><h2 id="intake-preview-title">Versión de la ficha de ingreso {folio}</h2></div>
        <button type="button" className="ghost icon-only print-hide" aria-label="Cerrar" onClick={exit}><Icon name="close"/></button>
      </header>
      <div className="modal-body">
        <dl className="preview-meta">
          <div><dt>Versión registrada</dt><dd>{when(version.registered_ms)}</dd></div>
          <div><dt>Registrado por</dt><dd>{author}</dd></div>
          {text(version, 'reason') && <div><dt>Motivo de la versión</dt><dd>{text(version, 'reason')}</dd></div>}
          {summary && <div><dt>Cambios</dt><dd>{summary}</dd></div>}
        </dl>
        <p className="muted">Solo se muestran los datos informados. Son datos declarados: no se verificaron con ClaveÚnica, Registro Civil ni PISEE.</p>
        <IntakeSummary data={intakeFromSaved(obj(version, 'form'))} codes={codes}/>
      </div>
      <footer className="modal-foot print-hide">
        <span/>
        <div className="actions">
          <button onClick={exit}>Cerrar</button>
          <button className="primary" onClick={() => window.print()}><Icon name="printer" size={18}/>Imprimir</button>
        </div>
      </footer>
    </div>
  </dialog>;
}
