import {useId, useRef, useState} from 'react';
import type {ReactNode} from 'react';
import {Icon} from '../Icon';
import type {ChartKind} from './chartModel';
import {arcPath, barLength, BASE_COLOR, byValue, CHART_COLORS, FONT, GRID_COLOR, KIND_LABELS, linePoints, MUTED_COLOR, pieSlices, scale, TEXT_COLOR} from './chartModel';
import {downloadPng, downloadSvg} from './chartExport';

export type Category = {key: string; label: string; special?: boolean; total?: number | null};
export type Series = {key: string; label: string; values: (number | null)[]; base?: boolean};
/** keepOrder: conserva el orden recibido (orden institucional) en vez de ordenar las barras de mayor a menor. */
export type ChartData = {categories: Category[]; series: Series[]; total?: number | null; percent: boolean; format: (v: number | null) => string; keepOrder?: boolean};

const W = 720;
const short = (s: string, max = 34) => s.length > max ? s.slice(0, max - 1) + '…' : s;
const color = (s: Series, i: number) => s.base ? BASE_COLOR : CHART_COLORS[i % CHART_COLORS.length];

/** Marca enfocable con su cifra: el nombre accesible y la ayuda emergente dicen categoria, serie y valor. */
function Mark({label, children}: {label: string; children: ReactNode}) {
  return <g tabIndex={0} role="img" aria-label={label} className="chart-mark"><title>{label}</title>{children}</g>;
}
function Legend({series, y}: {series: Series[]; y: number}) {
  if (series.length < 2) return null;
  let x = 16;
  return <g>{series.map((s, i) => {
    const at = x;
    x += 26 + Math.min(44, s.label.length) * 6.5;
    return <g key={s.key}><rect x={at} y={y - 10} width={12} height={12} rx={3} fill={color(s, i)}/>
      <text x={at + 18} y={y} fontSize={12} fill={TEXT_COLOR} fontFamily={FONT}>{short(s.label, 44)}</text></g>;
  })}</g>;
}

/** Barras horizontales (una serie, agrupadas, apiladas o pareadas), siempre desde 0. */
function Bars({data, mode}: {data: ChartData; mode: 'barras' | 'agrupadas' | 'apiladas' | 'pareadas'}) {
  const series = data.series;
  const stacked = mode === 'apiladas';
  const cats = mode === 'barras' && !data.keepOrder ? byValue(data.categories.map((c, i) => ({...c, value: series[0]?.values[i] ?? null, index: i}))) : data.categories.map((c, i) => ({...c, value: null, index: i}));
  const legendH = series.length > 1 ? 28 : 6;
  const labelW = 230, valueW = 76, plot = W - labelW - valueW - 16;
  const bar = mode === 'barras' ? 16 : 11, gap = 2;
  const rowH = stacked || mode === 'barras' ? 30 : series.length * (bar + gap) + 14;
  const peak = Math.max(0, ...cats.map(c => stacked ? Number(c.total ?? series.reduce((a, s) => a + (s.values[c.index] ?? 0), 0)) : Math.max(0, ...series.map(s => s.values[c.index] ?? 0))));
  const sc = scale(peak, !data.percent);
  const height = legendH + cats.length * rowH + 30;
  const x0 = labelW;
  return <svg xmlns="http://www.w3.org/2000/svg" viewBox={`0 0 ${W} ${height}`} width={W} height={height} className="chart-svg" aria-hidden="false">
    <Legend series={series} y={18}/>
    {sc.ticks.map(t => {
      const x = x0 + barLength(t, sc.max, plot);
      return <g key={t}><line x1={x} x2={x} y1={legendH} y2={height - 24} stroke={GRID_COLOR} strokeWidth={1}/>
        <text x={x} y={height - 8} fontSize={11} fill={MUTED_COLOR} fontFamily={FONT} textAnchor="middle">{data.format(t)}</text></g>;
    })}
    <line x1={x0} x2={x0} y1={legendH} y2={height - 24} stroke={MUTED_COLOR} strokeWidth={1}/>
    {cats.map((c, row) => {
      const top = legendH + row * rowH;
      const mid = top + rowH / 2;
      let offset = 0;
      return <g key={c.key}>
        <text x={labelW - 8} y={mid + 4} fontSize={12} fill={TEXT_COLOR} fontFamily={FONT} textAnchor="end"><title>{c.label}</title>{short(c.label)}</text>
        {series.map((s, i) => {
          const v = s.values[c.index];
          const len = barLength(v, sc.max, plot);
          const y = stacked || mode === 'barras' ? mid - bar / 2 : top + 7 + i * (bar + gap);
          const x = stacked ? x0 + offset : x0;
          if (stacked) offset += len;
          const label = `${c.label}${series.length > 1 ? ', ' + s.label : ''}: ${data.format(v)}`;
          return <Mark key={s.key} label={label}>
            {len > 0 && <rect x={x} y={y} width={Math.max(0, stacked && i < series.length - 1 ? len - gap : len)} height={bar} rx={stacked ? 0 : 3} fill={color(s, i)}/>}
            {/* Rotulo directo de la cifra; con varias series, un cero no se rotula (la tabla lo dice). */}
            {!stacked && (series.length === 1 || (v ?? 0) > 0) && <text x={x0 + len + 6} y={y + bar - 2} fontSize={11} fill={TEXT_COLOR} fontFamily={FONT}>{data.format(v)}</text>}
          </Mark>;
        })}
        {stacked && <text x={x0 + offset + 6} y={mid + 4} fontSize={11} fill={TEXT_COLOR} fontFamily={FONT}>{data.format(c.total ?? null)}</text>}
      </g>;
    })}
  </svg>;
}

/** Lineas para un eje de periodo: 2 px, marcadores de 8 px, base en 0. */
function Lines({data}: {data: ChartData}) {
  const series = data.series;
  const legendH = series.length > 1 ? 30 : 8, left = 56, right = 24, plotH = 220;
  const plot = W - left - right;
  const peak = Math.max(0, ...series.flatMap(s => s.values.map(v => v ?? 0)));
  const sc = scale(peak, !data.percent);
  const height = legendH + plotH + 52;
  const every = Math.max(1, Math.ceil(data.categories.length / 12));
  return <svg xmlns="http://www.w3.org/2000/svg" viewBox={`0 0 ${W} ${height}`} width={W} height={height} className="chart-svg">
    <Legend series={series} y={18}/>
    <g transform={`translate(${left} ${legendH})`}>
      {sc.ticks.map(t => {
        const y = plotH - t / sc.max * plotH;
        return <g key={t}><line x1={0} x2={plot} y1={y} y2={y} stroke={GRID_COLOR} strokeWidth={1}/>
          <text x={-8} y={y + 4} fontSize={11} fill={MUTED_COLOR} fontFamily={FONT} textAnchor="end">{data.format(t)}</text></g>;
      })}
      <line x1={0} x2={plot} y1={plotH} y2={plotH} stroke={MUTED_COLOR} strokeWidth={1}/>
      {data.categories.map((c, i) => i % every === 0 && <text key={c.key} x={linePoints(data.categories.map(() => 0), plot, plotH, sc.max)[i].x} y={plotH + 18}
        fontSize={11} fill={MUTED_COLOR} fontFamily={FONT} textAnchor="middle">{short(c.label, 14)}</text>)}
      {series.map((s, si) => {
        const pts = linePoints(s.values, plot, plotH, sc.max);
        const path = pts.reduce((acc, p, i) => p.y == null ? acc : acc + (i === 0 || pts[i - 1].y == null ? ' M ' : ' L ') + p.x.toFixed(1) + ' ' + p.y.toFixed(1), '').trim();
        return <g key={s.key}>
          <path d={path} fill="none" stroke={color(s, si)} strokeWidth={2} strokeLinejoin="round"/>
          {pts.map((p, i) => p.y != null && <Mark key={i} label={`${data.categories[i].label}${series.length > 1 ? ', ' + s.label : ''}: ${data.format(p.value)}`}>
            <circle cx={p.x} cy={p.y} r={4} fill={color(s, si)} stroke="#ffffff" strokeWidth={2}/>
          </Mark>)}
        </g>;
      })}
    </g>
  </svg>;
}

/** Circular: partes del total del servidor, con el porcentaje en la leyenda. */
function Pie({data}: {data: ChartData}) {
  const values = data.series[0]?.values.map(v => v ?? 0) ?? [];
  const total = Number(data.total ?? 0);
  const slices = pieSlices(values, total);
  const height = Math.max(260, 40 + data.categories.length * 26);
  const cx = 150, cy = height / 2, r = 110;
  return <svg xmlns="http://www.w3.org/2000/svg" viewBox={`0 0 ${W} ${height}`} width={W} height={height} className="chart-svg">
    {slices.map((s, i) => s.end > s.start && <Mark key={data.categories[i].key} label={`${data.categories[i].label}: ${data.format(values[i])} (${s.percent.toFixed(1).replace('.', ',')} %)`}>
      <path d={arcPath(cx, cy, r, s.start, s.end)} fill={CHART_COLORS[i]} stroke="#ffffff" strokeWidth={2}/>
    </Mark>)}
    {data.categories.map((c, i) => {
      const y = cy - data.categories.length * 13 + i * 26 + 13;
      return <g key={c.key}><rect x={300} y={y - 10} width={12} height={12} rx={3} fill={CHART_COLORS[i]}/>
        <text x={320} y={y} fontSize={12} fill={TEXT_COLOR} fontFamily={FONT}>{short(c.label, 40)}: {data.format(values[i])} ({(slices[i]?.percent ?? 0).toFixed(1).replace('.', ',')} %)</text></g>;
    })}
  </svg>;
}

/**
 * Grafico con titulo, subtitulo, notas y pie de fuente; nombre accesible, ayuda por marca (con foco por teclado) y la
 * tabla como alternativa textual (la presenta la pantalla). Descarga en PNG y SVG con los mismos textos.
 */
export function Chart({kind, data, title, subtitle, notes, source, file, tableId}: {kind: ChartKind; data: ChartData; title: string; subtitle?: string; notes: string[];
  source: string; file: string; tableId?: string}) {
  const id = useId(), ref = useRef<HTMLDivElement>(null);
  const [error, setError] = useState('');
  const svg = () => ref.current?.querySelector('svg') as SVGSVGElement | null;
  const texts = {title, subtitle, notes, source};
  const download = async (format: 'png' | 'svg') => {
    setError('');
    const el = svg();
    if (!el) return;
    try {
      if (format === 'svg') downloadSvg(el, texts, file);
      else await downloadPng(el, texts, file);
    } catch (e) {
      setError((e as Error).message);
    }
  };
  const body = kind === 'lineas' ? <Lines data={data}/> : kind === 'circular' ? <Pie data={data}/> : <Bars data={data} mode={kind}/>;
  return <figure className="stat-chart" aria-labelledby={id + 't'}>
    <figcaption>
      <strong id={id + 't'}>{title}</strong>
      {subtitle && <span className="stat-chart-sub">{subtitle}</span>}
    </figcaption>
    <div ref={ref} className="stat-chart-canvas" role="group" aria-label={`${KIND_LABELS[kind]}: ${title}. ${data.categories.length} categorías.${tableId ? ' Las cifras están también en la tabla.' : ''}`}>{body}</div>
    {notes.length > 0 && <ul className="stat-chart-notes">{notes.map(n => <li key={n}>{n}</li>)}</ul>}
    <p className="stat-source">{source}</p>
    <div className="actions">
      <button type="button" className="sm" onClick={() => void download('png')}><Icon name="download" size={16}/>Descargar PNG</button>
      <button type="button" className="sm" onClick={() => void download('svg')}><Icon name="download" size={16}/>Descargar SVG</button>
      {tableId && <button type="button" className="sm ghost" onClick={() => document.getElementById(tableId)?.focus()}>Ver la tabla</button>}
    </div>
    {error && <p role="alert" className="error">{error}</p>}
  </figure>;
}
