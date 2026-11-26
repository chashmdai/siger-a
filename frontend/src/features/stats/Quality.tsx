import {useState} from 'react';
import type {Context} from '../../app/context';
import {Chart} from '../../components/charts/Chart';
import {Callout, Panel, Skeleton} from '../../components/Primitives';
import type {Period, StatsCatalog, TableModel} from './statsModel';
import {encodeRoute, fmtInt, fmtPct, readResult, requestBody, sameMinute, sourceFooter, tableModel} from './statsModel';
import {byMonth, byScope, byVariable, partsText, pendingLink, REGION_NOTE, share, stateParts} from './qualityModel';
import {useStat} from './statQuery';
import {Freshness, HowToRead} from './ResultView';
import {panoramaChoices} from './Panorama';
import {chileDay} from './statsModel';

/** Barra apilada de los estados de una fila, con el porcentaje del servidor en el ancho y en texto (nunca solo el color). */
function StateBar({t, row, label}: {t: TableModel; row: string[]; label: string}) {
  const parts = stateParts(t, row);
  return <div className="stat-quality-bar">
    <div className="stat-quality-track" role="img" aria-label={`${label}: ${partsText(parts)}`}>
      {parts.map(p => <span key={p.state} className={'q-' + p.state.toLowerCase()} style={{width: p.percent + '%'}}/>)}
    </div>
    <p className="field-help">{partsText(parts)}</p>
  </div>;
}

/**
 * Calidad (DISENO 10.4): completitud por variable (pares requerimiento-variable de la medida COMPLETITUD), por ambito y
 * mes a mes de la variable elegida, con los porcentajes del servidor, y el enlace a la bandeja con el filtro "Con datos
 * estadisticos pendientes" solo si la cuenta la tiene (condicion 1 del GO de E13). La region del requirente se informa con
 * su "Sin información" y sin enlace.
 */
export default function Quality({ctx, cat, scope, params}: {ctx: Context; cat: StatsCatalog; scope: string; params: URLSearchParams}) {
  const options = panoramaChoices(chileDay());
  const chosen = options.find(o => o.key === params.get('pp')) ?? options[0];
  const period: Period = chosen.period;
  const vars = cat.dimensions.find(d => d.code === 'VARIABLE')?.options ?? [];
  const [variable, setVariable] = useState(params.get('v') && vars.some(v => v.code === params.get('v')) ? params.get('v')! : vars[0]?.code ?? 'TIPO_SOLICITUD');
  const main = useStat(requestBody(byVariable(period), cat), ctx.user.id);
  const scopes = useStat(requestBody(byScope(period, variable), cat), ctx.user.id);
  const months = useStat(requestBody(byMonth(period, variable), cat), ctx.user.id);
  const common = sameMinute([main, scopes, months].map(s => s.status === 'ok' ? s.at : 0));
  const refreshAll = () => {main.refresh(); scopes.refresh(); months.refresh();};
  const region = variable === 'REGION_REQUIRENTE';
  const gap = region ? 'SIN_INFORMACION' : 'PENDIENTE';
  const label = vars.find(v => v.code === variable)?.label ?? 'Variable';
  const t = main.status === 'ok' && main.data ? tableModel(readResult(main.data), ['VARIABLE'], ['ESTADO_VARIABLE']) : null;
  const ts = scopes.status === 'ok' && scopes.data ? tableModel(readResult(scopes.data), ['AMBITO'], ['ESTADO_VARIABLE']) : null;
  const tm = months.status === 'ok' && months.data ? tableModel(readResult(months.data), ['PERIODO_MES'], ['ESTADO_VARIABLE']) : null;
  const source = sourceFooter({at: common ?? (main.at || Date.now()), scope, basis: 'RECEPCION', mode: 'VIGENTE'});
  return <section aria-label="Calidad">
    <div className="stat-toolbar">
      <label><span>Periodo</span><select value={chosen.key} onChange={e => {location.hash = encodeRoute('calidad', undefined, {pp: e.target.value === 'curso' ? '' : e.target.value});}}>
        {options.map(o => <option key={o.key} value={o.key}>{o.label}</option>)}</select></label>
      {common != null && <Freshness at={common} refresh={refreshAll}/>}
    </div>
    <Panel title="Completitud por variable" icon="checkCircle">
      {main.status === 'error' ? <Callout tone="warn">{main.error}</Callout> : !t ? <Skeleton lines={6}/> : t.rows.length === 0 ? <p className="field-help">Sin registros en el periodo.</p> : <ul className="stat-quality">
        {t.rows.map(r => {
          const code = r.key[0], link = pendingLink(ctx.user, code);
          return <li key={code} className={code === variable ? 'on' : ''}>
            <div className="stat-quality-head">
              <button type="button" className="link" aria-pressed={code === variable} onClick={() => setVariable(code)}>{r.labels[0]}</button>
              <span>{code === 'REGION_REQUIRENTE' ? `Sin información ${fmtPct(share(t, r.key, 'SIN_INFORMACION'))}` : `Pendiente ${fmtPct(share(t, r.key, 'PENDIENTE'))}`}</span>
            </div>
            <StateBar t={t} row={r.key} label={r.labels[0]}/>
            {link ? (link.href ? <a className="link" href={link.href}>{link.text}</a> : <p className="field-help">{link.text}</p>)
              : <p className="field-help">{REGION_NOTE}</p>}
          </li>;
        })}
      </ul>}
      {common == null && main.status === 'ok' && <Freshness at={main.at} refresh={main.refresh}/>}
    </Panel>
    <Panel title={`${label}: por ámbito`} icon="layers">
      {scopes.status === 'error' ? <Callout tone="warn">{scopes.error}</Callout> : !ts ? <Skeleton lines={4}/> : ts.rows.length === 0 ? <p className="field-help">Sin registros en el periodo.</p>
        : <ul className="stat-quality">{ts.rows.map(r => <li key={r.key[0]}><div className="stat-quality-head"><strong>{r.labels[0]}</strong>
          <span>{fmtInt(ts.rowTotal(r.key)?.n ?? 0)} requerimientos</span></div><StateBar t={ts} row={r.key} label={r.labels[0]}/></li>)}</ul>}
      {common == null && scopes.status === 'ok' && <Freshness at={scopes.at} refresh={scopes.refresh}/>}
    </Panel>
    <Panel title={`${label}: mes a mes`} icon="chart">
      {months.status === 'error' ? <Callout tone="warn">{months.error}</Callout> : !tm ? <Skeleton lines={4}/> : <Chart kind="lineas" title={`${label}: ${region ? 'sin información' : 'pendiente'}, mes a mes`}
        notes={['El porcentaje lo calcula SIGER sobre los requerimientos de cada mes.']} source={source} file={'siger-calidad-' + variable.toLowerCase()}
        data={{categories: tm.rows.map(r => ({key: r.key[0], label: r.labels[0]})), series: [{key: gap, label: region ? 'Sin información' : 'Pendiente', values: tm.rows.map(r => share(tm, r.key, gap))}],
          total: null, percent: true, format: v => fmtPct(v)}}/>}
      {common == null && months.status === 'ok' && <Freshness at={months.at} refresh={months.refresh}/>}
    </Panel>
    <HowToRead lines={[
      'Completitud cuenta cada par de requerimiento y variable: por eso un requerimiento aparece una vez en cada variable.',
      'Los porcentajes los calcula SIGER sobre los requerimientos de cada fila; aquí no se suma ni se divide nada.',
      '«Pendiente» es lo aún no clasificado; «Sin información», «No declarado» y «No aplica» son estados resueltos.',
      REGION_NOTE,
      'Con alcance de una sede, un derecho o contexto registrado solo en un Caso de otro ámbito puede figurar como pendiente.',
      `Alcance: ${scope}.`,
    ]}/>
    <p className="stat-source">{source}</p>
  </section>;
}
