import {useCallback, useEffect, useState} from 'react';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {CHART_COLORS, linePoints, MUTED_COLOR, scale} from '../../components/charts/chartModel';
import {Callout, Skeleton} from '../../components/Primitives';
import type {Indicator, Period, Query, Result, StatsCatalog} from './statsModel';
import {chileDay, chileTime, COMPARE_LABELS, COMPLETE_NOTE, completeShare, encodeRoute, fmtInt, fmtPoints, indicatorQuery, INDICATORS, panoramaPeriod, periodLabel,
  publicAccount, publicYearOf, readResult, requestBody, sameMinute, sourceFooter, tableModel, variation} from './statsModel';
import type {StatState} from './statQuery';
import {useStat} from './statQuery';
import {Freshness, HowToRead} from './ResultView';

/** Periodos elegibles: la Cuenta Publica en curso (por defecto) y las cinco anteriores completas. */
export function panoramaChoices(today: string): {key: string; label: string; period: Period}[] {
  const current = panoramaPeriod(today);
  const year = publicYearOf(today);
  const out = [{key: 'curso', label: current.period.type === 'PERSONALIZADO' ? 'Cuenta Pública en curso, hasta ayer' : current.label, period: current.period}];
  for (let y = year - 1; y >= year - 5; y--) if (!(current.period.type === 'CUENTA_PUBLICA' && current.period.from === `${y}-07-01`))
    out.push({key: 'cp-' + y, label: `Cuenta Pública ${y}-${y + 1}`, period: publicAccount(y)});
  return out;
}

function Spark({values, label}: {values: number[]; label: string}) {
  const sc = scale(Math.max(0, ...values));
  const pts = linePoints(values, 116, 28, sc.max);
  const d = pts.map((p, i) => (i ? 'L' : 'M') + (p.x + 2).toFixed(1) + ' ' + ((p.y ?? 28) + 2).toFixed(1)).join(' ');
  return <svg viewBox="0 0 120 32" width={120} height={32} role="img" aria-label={label} className="stat-spark">
    <line x1={2} x2={118} y1={30} y2={30} stroke={MUTED_COLOR} strokeWidth={1}/>
    <path d={d} fill="none" stroke={CHART_COLORS[0]} strokeWidth={2} strokeLinejoin="round"/>
  </svg>;
}

function Card({ind, cat, catalog, period, compare, user, total, report, inlineFresh}: {ind: Indicator; cat: StatsCatalog; catalog: Context['catalog']; period: Period; compare: string; user: number;
  total?: Result | null; report: (key: string, at: number) => void; inlineFresh: boolean}) {
  const q = indicatorQuery(ind, cat, catalog, period, compare);
  const s = useStat(q ? requestBody(q, cat) : null, user);
  const at = s.status === 'ok' ? s.at : 0;
  useEffect(() => report(ind.key, q ? at : -1), [ind.key, at, report, q]);
  return <IndicatorCard ind={ind} q={q} s={s} total={total} inlineFresh={inlineFresh}/>;
}

/**
 * Una tarjeta del Panorama segun el estado de su propia consulta (E14): cada indicador se muestra apenas llega su
 * respuesta, sin esperar a los demas; solo la caracterizacion completa espera ademas el total de ingresos.
 */
export function IndicatorCard({ind, q, s, total, inlineFresh}: {ind: Indicator; q: Query | null; s: StatState & {refresh: () => void}; total?: Result | null; inlineFresh: boolean}) {
  const open = () => {if (q) location.hash = encodeRoute('explorar', q);};
  if (!q) return <div className="stat-card"><h3>{ind.label}</h3><p className="field-help">No disponible con los campos que publica el servidor.</p></div>;
  if (s.status === 'error') return <div className="stat-card"><h3>{ind.label}</h3><p role="alert" className="error">{s.error}</p>
    <button type="button" className="sm" onClick={s.refresh}><Icon name="refresh" size={16}/>Reintentar</button></div>;
  // Mientras calcula, la tarjeta ya abre su consulta en Explorar.
  const loading = <div className="stat-card clickable" aria-busy="true">
    <h3><button type="button" className="stat-card-link" onClick={open} aria-label={`${ind.label}: calculando. Abrir en Explorar.`}>{ind.label}</button></h3>
    <Skeleton lines={3}/><span className="stat-card-open" aria-hidden="true">Abrir en Explorar <Icon name="arrowRight" size={16}/></span></div>;
  if (s.status !== 'ok' || !s.data) return loading;
  const r = readResult(s.data);
  const months = tableModel(r, ['PERIODO_MES'], []).rows.map(row => ({label: row.labels[0], n: r.cells.find(c => c.row[0] === row.key[0])?.n ?? 0}));
  const g = r.comparison?.grand;
  let value = fmtInt(r.grand.n), change = g ? variation(g.delta_n, g.delta_percent, g.base_n) : null, extra = '';
  if (ind.key === 'completos') {
    if (!total) return loading;
    const share = completeShare(r.grand.n, total.grand.n, g?.base_n ?? null, total.comparison?.grand?.base_n ?? null);
    value = share.text;
    change = share.points == null ? (g ? {direction: 'same', arrow: '→', text: 'Sin base de comparación'} : null)
      : {direction: share.points > 0 ? 'up' : share.points < 0 ? 'down' : 'same', arrow: share.points > 0 ? '↑' : share.points < 0 ? '↓' : '→', text: fmtPoints(share.points)};
    extra = COMPLETE_NOTE;
  }
  return <article className="stat-card clickable">
    <h3><button type="button" className="stat-card-link" onClick={open} aria-label={`${ind.label}: ${value}. Abrir en Explorar.`}>{ind.label}</button></h3>
    <p className="stat-card-value">{value}</p>
    {change && <p className={'stat-change ' + change.direction}><span aria-hidden="true">{change.arrow} </span>{change.text}{g && g.base_n > 0 && ind.key !== 'completos' ? ` respecto de ${fmtInt(g.base_n)}` : ''}</p>}
    <Spark values={months.map(m => m.n)} label={'Mes a mes: ' + months.map(m => `${m.label} ${fmtInt(m.n)}`).join('; ')}/>
    <p className="field-help">{ind.help}{extra ? ' ' + extra : ''}</p>
    <span className="stat-card-open" aria-hidden="true">Abrir en Explorar <Icon name="arrowRight" size={16}/></span>
    {inlineFresh && <span className="stat-fresh-inline">Datos de las {chileTime(s.at)}</span>}
  </article>;
}

/**
 * Panorama (DISENO 10.3): seis indicadores del periodo elegido, cada uno con su variacion (cifra, porcentaje, flecha y
 * texto, nunca solo color) y su linea mensual. Por defecto la Cuenta Publica en curso hasta ayer (hora de Chile),
 * comparada con el mismo periodo del anio anterior. Un clic abre la consulta en Explorar.
 */
export default function Panorama({ctx, cat, scope, params}: {ctx: Context; cat: StatsCatalog; scope: string; params: URLSearchParams}) {
  const today = chileDay();
  const options = panoramaChoices(today);
  const chosen = options.find(o => o.key === params.get('pp')) ?? options[0];
  const compare = (['PERIODO_ANTERIOR', 'MISMO_PERIODO_ANIO_ANTERIOR'] as string[]).includes(params.get('pk') ?? '') ? params.get('pk')! : 'MISMO_PERIODO_ANIO_ANTERIOR';
  const set = (pp: string, pk: string) => {location.hash = encodeRoute('panorama', undefined, {pp: pp === 'curso' ? '' : pp, pk: pk === 'MISMO_PERIODO_ANIO_ANTERIOR' ? '' : pk});};
  const total = INDICATORS[0];
  const totalQuery = indicatorQuery(total, cat, ctx.catalog, chosen.period, compare);
  const totalState = useStat(totalQuery ? requestBody(totalQuery, cat) : null, ctx.user.id);
  const totalResult = totalState.status === 'ok' && totalState.data ? readResult(totalState.data) : null;
  const label = chosen.key === 'curso' ? panoramaPeriod(today).label : periodLabel(chosen.period);
  // Ajuste 6 (E13): una sola hora en la seccion si todas sus consultas son del mismo minuto; si no, cada tarjeta la suya.
  const [ats, setAts] = useState<Record<string, number>>({});
  const report = useCallback((key: string, at: number) => setAts(prev => prev[key] === at ? prev : {...prev, [key]: at}), []);
  const common = sameMinute(INDICATORS.map(i => ats[i.key] ?? 0).filter(a => a !== -1));
  return <section aria-label="Panorama">
    <div className="stat-toolbar">
      <label><span>Periodo</span><select value={chosen.key} onChange={e => set(e.target.value, compare)}>{options.map(o => <option key={o.key} value={o.key}>{o.label}</option>)}</select></label>
      <label><span>Comparar con</span><select value={compare} onChange={e => set(chosen.key, e.target.value)}>
        {['MISMO_PERIODO_ANIO_ANTERIOR', 'PERIODO_ANTERIOR'].filter(c => cat.compares.includes(c)).map(c => <option key={c} value={c}>{COMPARE_LABELS[c]}</option>)}
      </select></label>
      <Freshness at={common ?? 0} refresh={totalState.refresh}/>
    </div>
    <p className="stat-period">{label}</p>
    {totalState.status === 'error' && <Callout tone="warn">{totalState.error}</Callout>}
    <div className="stat-cards">
      {INDICATORS.map(ind => <Card key={ind.key} ind={ind} cat={cat} catalog={ctx.catalog} period={chosen.period} compare={compare} user={ctx.user.id}
        total={totalResult} report={report} inlineFresh={common == null}/>)}
    </div>
    <HowToRead lines={[
      `Periodo: ${label}, según la fecha de recepción (los REQ, por su constitución; los Casos, por su creación), en hora de Chile.`,
      `Cada variación compara con ${COMPARE_LABELS[compare].toLocaleLowerCase('es-CL')}: diferencia en cantidad y en porcentaje sobre la cifra base. Sin cifra base no hay variación porcentual.`,
      'Ingresos cuenta cada requerimiento una vez; no cuenta los inválidos ni los vinculados como antecedente. Las actuaciones reservadas no se cuentan.',
      COMPLETE_NOTE + ' La variación de ese indicador va en puntos porcentuales.',
      `Alcance: ${scope}.`,
      'Haz clic en un indicador para abrirlo en Explorar y desglosarlo.',
    ]}/>
    <p className="stat-source">{sourceFooter({at: totalState.at || Date.now(), scope, basis: 'RECEPCION', mode: 'VIGENTE'})}</p>
  </section>;
}
