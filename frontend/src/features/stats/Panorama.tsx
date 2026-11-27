import {useCallback, useEffect, useId, useState} from 'react';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import type {IconName} from '../../components/Icon';
import {barLength, linePoints, MUTED_COLOR, periodTick, scale} from '../../components/charts/chartModel';
import {Callout} from '../../components/Primitives';
import type {Indicator, Period, Query, Result, StatsCatalog} from './statsModel';
import {cardHref, chileDay, chileTime, COMPARE_LABELS, COMPLETE_NOTE, completeShare, encodeRoute, fmtInt, fmtPoints, indicatorQuery, INDICATORS, panoramaPeriod, periodLabel,
  publicAccount, publicYearOf, readResult, requestBody, sameMinute, sourceFooter, tableModel, TONE_NOTE, variation, variationTone} from './statsModel';
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

/** Icono y acento de cada indicador (0.8.1, seccion 3.1 del plan): apoyo visual; el nombre va siempre en texto. */
const LOOK: Record<string, {icon: IconName; accent: string}> = {
  ingresos: {icon: 'inbox', accent: 'com'}, req: {icon: 'folderCheck', accent: 'req'}, casos: {icon: 'briefcase', accent: 'cas'},
  actuaciones: {icon: 'activity', accent: 'act'}, completos: {icon: 'badgeCheck', accent: 'ok'}, tardios: {icon: 'clockAlert', accent: 'warn'},
};
const look = (key: string): {icon: IconName; accent: string} => LOOK[key] ?? {icon: 'chart', accent: 'com'};

/**
 * Linea mensual decorativa (las cifras van en el texto de la tarjeta): area degradada, linea base y punto en el ultimo
 * mes. El degradado usa un identificador propio de la tarjeta. F02b (observacion 2): la linea ocupa todo el alto que queda
 * en la tarjeta (el SVG se estira) y toma el color de acento de su tarjeta (currentColor, token --stat-spark-*). Los trazos
 * no se deforman al estirarse (non-scaling-stroke) y el punto final es un trazo de largo cero con extremo redondo, que se
 * ve redondo a cualquier proporcion.
 */
function Spark({values}: {values: number[]}) {
  const uid = useId().replace(/:/g, '');
  const w = 240, h = 48, pad = 4;
  const sc = scale(Math.max(0, ...values));
  const pts = linePoints(values, w - pad * 2, h - pad * 2, sc.max).map(p => ({x: p.x + pad, y: (p.y ?? h - pad * 2) + pad}));
  const line = pts.map((p, i) => (i ? 'L' : 'M') + p.x.toFixed(1) + ' ' + p.y.toFixed(1)).join(' ');
  const last = pts[pts.length - 1];
  const area = last ? `${line} L${last.x.toFixed(1)} ${h - pad} L${pts[0].x.toFixed(1)} ${h - pad} Z` : '';
  return <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="stat-spark" aria-hidden="true" focusable="false">
    <defs><linearGradient id={'sg' + uid} x1="0" x2="0" y1="0" y2="1">
      <stop offset="0%" stopColor="currentColor" stopOpacity={0.22}/><stop offset="100%" stopColor="currentColor" stopOpacity={0}/>
    </linearGradient></defs>
    <line x1={pad} x2={w - pad} y1={h - pad} y2={h - pad} stroke={MUTED_COLOR} strokeWidth={1} strokeOpacity={0.5} vectorEffect="non-scaling-stroke"/>
    {pts.length > 1 && <path d={area} fill={`url(#sg${uid})`}/>}
    {pts.length > 1 && <path d={line} fill="none" stroke="currentColor" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke"/>}
    {last && <path className="stat-spark-dot" d={`M${last.x.toFixed(1)} ${last.y.toFixed(1)}h0`} fill="none" stroke="currentColor" strokeWidth={7} strokeLinecap="round"
      vectorEffect="non-scaling-stroke"/>}
  </svg>;
}

/**
 * Perfil mensual del indicador principal: una columna por mes, desde 0, con su cifra y el mes abreviado (las mismas cifras
 * de la consulta, sin sumar ni calcular otras). Decorativo para los lectores de pantalla: la serie va en la descripcion.
 */
function MonthBars({months}: {months: {label: string; n: number}[]}) {
  const sc = scale(Math.max(0, ...months.map(m => m.n)));
  return <span className="stat-months" aria-hidden="true">
    {months.map((m, i) => <span key={m.label} className={'stat-month' + (i === months.length - 1 ? ' last' : '')} title={m.label}>
      <span className="stat-month-n">{fmtInt(m.n)}</span>
      <span className="stat-month-bar"><span style={{height: `${barLength(m.n, sc.max, 100).toFixed(1)}%`}}/></span>
      <span className="stat-month-label">{periodTick(m.label)[0]}</span>
    </span>)}
  </span>;
}

/** Anillo de progreso de la caracterizacion completa (decorativo: la cifra en texto es la fuente). */
function Ring({share}: {share: number | null}) {
  const r = 22, c = 2 * Math.PI * r, part = share == null ? 0 : Math.max(0, Math.min(100, share)) / 100;
  return <svg viewBox="0 0 56 56" width={56} height={56} className="stat-ring" aria-hidden="true" focusable="false">
    <circle cx={28} cy={28} r={r} fill="none" className="stat-ring-track" strokeWidth={6}/>
    {part > 0 && <circle cx={28} cy={28} r={r} fill="none" className="stat-ring-value" strokeWidth={6} strokeLinecap="round"
      strokeDasharray={`${(c * part).toFixed(2)} ${c.toFixed(2)}`} transform="rotate(-90 28 28)"/>}
  </svg>;
}

function CardHead({ind}: {ind: Indicator}) {
  const l = look(ind.key);
  return <span className="stat-card-head">
    <span className="stat-card-icon" data-accent={l.accent}><Icon name={l.icon} size={18}/></span>
    <h3>{ind.label}</h3>
  </span>;
}
function Open() {
  return <span className="stat-card-open" aria-hidden="true">Abrir en Explorar <Icon name="arrowRight" size={16} className="stat-card-arrow"/></span>;
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
 * 0.8.1 (D53): la tarjeta que calcula o tiene datos es, en toda su superficie, un enlace real a su consulta en Explorar
 * (clic en cualquier punto, teclado, clic central y otra pestana) y no contiene otro control. Su nombre dice indicador,
 * cifra y destino; la variacion, la serie mensual y la ayuda van en su descripcion. La tarjeta en error no es enlace.
 */
export function IndicatorCard({ind, q, s, total, inlineFresh}: {ind: Indicator; q: Query | null; s: StatState & {refresh: () => void}; total?: Result | null; inlineFresh: boolean}) {
  const id = useId().replace(/:/g, '');
  const cls = 'stat-card' + (ind.key === 'ingresos' ? ' main' : '');
  if (!q) return <article className={cls + ' off'}><CardHead ind={ind}/><p className="field-help">No disponible con los campos que publica el servidor.</p></article>;
  if (s.status === 'error') return <article className={cls + ' failed'}><CardHead ind={ind}/>
    <p role="alert" className="error"><Icon name="alert" size={16}/><span>{s.error}</span></p>
    <div className="actions"><button type="button" className="sm" onClick={s.refresh}><Icon name="refresh" size={16}/>Reintentar</button></div></article>;
  const href = cardHref(q);
  // Mientras calcula, la tarjeta ya abre su consulta en Explorar; el esqueleto conserva el tamano final.
  const loading = <a className={cls + ' stat-card-link loading'} href={href} aria-busy="true" aria-label={`${ind.label}: calculando. Abrir en Explorar.`}>
    <CardHead ind={ind}/>
    <span className="stat-card-skeleton" aria-hidden="true"><span className="skeleton value"/><span className="skeleton line"/><span className="skeleton spark"/><span className="skeleton line"/></span>
    <span className="stat-card-foot"><Open/></span>
  </a>;
  if (s.status !== 'ok' || !s.data) return loading;
  const r = readResult(s.data);
  const months = tableModel(r, ['PERIODO_MES'], []).rows.map(row => ({label: row.labels[0], n: r.cells.find(c => c.row[0] === row.key[0])?.n ?? 0}));
  const g = r.comparison?.grand;
  let value = fmtInt(r.grand.n), change = g ? variation(g.delta_n, g.delta_percent, g.base_n) : null, extra = '', share: number | null = null, hasBase = !!g && g.base_n > 0;
  if (ind.key === 'completos') {
    if (!total) return loading;
    const sh = completeShare(r.grand.n, total.grand.n, g?.base_n ?? null, total.comparison?.grand?.base_n ?? null);
    value = sh.text;
    share = sh.share;
    hasBase = sh.points != null;
    change = sh.points == null ? (g ? {direction: 'same', arrow: '→', text: 'Sin base de comparación'} : null)
      : {direction: sh.points > 0 ? 'up' : sh.points < 0 ? 'down' : 'same', arrow: sh.points > 0 ? '↑' : sh.points < 0 ? '↓' : '→', text: fmtPoints(sh.points)};
    extra = COMPLETE_NOTE;
  }
  const tone = change ? variationTone(ind.key, change.direction, hasBase) : 'neutro';
  const base = g && g.base_n > 0 && ind.key !== 'completos' ? ` respecto de ${fmtInt(g.base_n)}` : '';
  const described = [change ? id + 'c' : '', id + 'm', id + 'h'].filter(Boolean).join(' ');
  return <a className={cls + ' stat-card-link'} href={href} data-accent={look(ind.key).accent} aria-label={`${ind.label}: ${value}. Abrir en Explorar.`} aria-describedby={described}>
    <CardHead ind={ind}/>
    <span className="stat-card-body">
      <span className="stat-card-figure">
        <span className="stat-card-value">{value}</span>
        {change && <span id={id + 'c'} className={'stat-change ' + change.direction + ' tone-' + tone}>
          <Icon name={change.direction === 'up' ? 'trendUp' : change.direction === 'down' ? 'trendDown' : 'moveRight'} size={16}/>
          <span><span className="sr-only">{change.arrow} </span>{change.text}{base}</span>
        </span>}
      </span>
      {ind.key === 'completos' && <Ring share={share}/>}
    </span>
    <span className={'stat-card-trend' + (ind.key === 'ingresos' ? '' : ' spark')}>
      {ind.key === 'ingresos' ? <MonthBars months={months}/> : <Spark values={months.map(m => m.n)}/>}
    </span>
    <span id={id + 'm'} className="sr-only">{'Mes a mes: ' + months.map(m => `${m.label} ${fmtInt(m.n)}`).join('; ') + '.'}</span>
    <span id={id + 'h'} className="stat-card-help">{ind.help}{extra ? ' ' + extra : ''}</span>
    <span className="stat-card-foot"><Open/>{inlineFresh && <span className="stat-fresh-inline">Datos de las {chileTime(s.at)}</span>}</span>
  </a>;
}

/**
 * Panorama (DISENO 10.3): seis indicadores del periodo elegido, cada uno con su variacion (cifra, porcentaje, flecha y
 * texto, nunca solo color) y su linea mensual. Por defecto la Cuenta Publica en curso hasta ayer (hora de Chile),
 * comparada con el mismo periodo del anio anterior. Cada tarjeta abre su consulta en Explorar (0.8.1, D53).
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
  return <section aria-label="Panorama" className="stat-panorama">
    <div className="stat-section-head">
      <div className="stat-section-lead">
        <p className="stat-period"><Icon name="calendarRange" size={18}/>{label}</p>
        <p className="stat-marks">
          <span className="stat-mark"><Icon name="mapPin" size={14}/>{scope}</span>
          <span className="stat-mark"><Icon name="swap" size={14}/>Comparado con {COMPARE_LABELS[compare].toLocaleLowerCase('es-CL')}</span>
        </p>
      </div>
      <div className="stat-toolbar">
        <label><span>Periodo</span><select value={chosen.key} onChange={e => set(e.target.value, compare)}>{options.map(o => <option key={o.key} value={o.key}>{o.label}</option>)}</select></label>
        <label><span>Comparar con</span><select value={compare} onChange={e => set(chosen.key, e.target.value)}>
          {['MISMO_PERIODO_ANIO_ANTERIOR', 'PERIODO_ANTERIOR'].filter(c => cat.compares.includes(c)).map(c => <option key={c} value={c}>{COMPARE_LABELS[c]}</option>)}
        </select></label>
        <Freshness at={common ?? 0} refresh={totalState.refresh}/>
      </div>
    </div>
    {totalState.status === 'error' && <Callout tone="warn">{totalState.error}</Callout>}
    <div className="stat-cards">
      {INDICATORS.map(ind => <Card key={ind.key} ind={ind} cat={cat} catalog={ctx.catalog} period={chosen.period} compare={compare} user={ctx.user.id}
        total={totalResult} report={report} inlineFresh={common == null}/>)}
    </div>
    <HowToRead lines={[
      `Periodo: ${label}, según la fecha de recepción (los REQ, por su constitución; los Casos, por su creación), en hora de Chile.`,
      `Cada variación compara con ${COMPARE_LABELS[compare].toLocaleLowerCase('es-CL')}: diferencia en cantidad y en porcentaje sobre la cifra base. Sin cifra base no hay variación porcentual.`,
      TONE_NOTE,
      'Ingresos cuenta cada requerimiento una vez; no cuenta los inválidos ni los vinculados como antecedente. Las actuaciones reservadas no se cuentan.',
      COMPLETE_NOTE + ' La variación de ese indicador va en puntos porcentuales.',
      `Alcance: ${scope}.`,
      'Haz clic en un indicador para abrirlo en Explorar y desglosarlo.',
    ]}/>
    <p className="stat-source">{sourceFooter({at: totalState.at || Date.now(), scope, basis: 'RECEPCION', mode: 'VIGENTE'})}</p>
  </section>;
}
