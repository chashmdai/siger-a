import {useId, useRef, useState} from 'react';
import type {ReactNode} from 'react';
import {Icon} from '../Icon';
import type {ChartKind} from './chartModel';
import {arcPath, barLength, BASE_COLOR, byValue, CHART_COLORS, FONT, GRID_COLOR, KIND_LABELS, linePoints, MUTED_COLOR, periodAxis, pieSlices, scale, TEXT_COLOR} from './chartModel';
import {downloadPng, downloadSvg} from './chartExport';

export type Category = {key: string; label: string; special?: boolean; total?: number | null};
export type Series = {key: string; label: string; values: (number | null)[]; base?: boolean};
/**
 * keepOrder: conserva el orden recibido (orden institucional) en vez de ordenar las barras de mayor a menor. divider: marca de
 * discontinuidad (E13, serie historica junto a SIGER) despues de la categoria indicada, con su texto: nunca solo una linea.
 */
export type ChartData = {categories: Category[]; series: Series[]; total?: number | null; percent: boolean; format: (v: number | null) => string; keepOrder?: boolean;
  divider?: {after: number; label: string}};

const W = 720;
/**
 * SVG que se descarga (0.8.1, regla 4 del plan): el del grafico sin lo que solo sirve en la pagina (clases, estilos y
 * animaciones), para que el archivo siga autonomo con los colores como atributos.
 */
export function exportMarkup(markup: string) {
  return markup.replace(/<style[\s\S]*?<\/style>/g, '').replace(/<animate\w*\b[^>]*\/>/g, '').replace(/<animate\w*\b[\s\S]*?<\/animate\w*>/g, '')
    .replace(/\s(?:class|style)="[^"]*"/g, '');
}
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
      return <g key={t}><line x1={x} x2={x} y1={legendH} y2={height - 24} stroke={GRID_COLOR} strokeWidth={1} strokeDasharray="3 4"/>
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
            {len > 0 && <rect x={x} y={y} width={Math.max(0, stacked && i < series.length - 1 ? len - gap : len)} height={bar} rx={stacked ? 0 : 4} fill={color(s, i)} className="chart-grow-x"/>}
            {/* Rotulo directo de la cifra; con varias series, un cero no se rotula (la tabla lo dice). */}
            {!stacked && (series.length === 1 || (v ?? 0) > 0) && <text x={x0 + len + 6} y={y + bar - 2} fontSize={11} fill={TEXT_COLOR} fontFamily={FONT}>{data.format(v)}</text>}
          </Mark>;
        })}
        {stacked && <text x={x0 + offset + 6} y={mid + 4} fontSize={11} fill={TEXT_COLOR} fontFamily={FONT}>{data.format(c.total ?? null)}</text>}
      </g>;
    })}
  </svg>;
}

/**
 * Rotulo del eje de periodo (E14, correccion 1): abreviado o en dos lineas, sin cortar; el nombre completo en la ayuda
 * emergente (y en la tabla).
 */
function Tick({x, y, lines, title}: {x: number; y: number; lines: string[]; title: string}) {
  return <text x={x} y={y} fontSize={11} fill={MUTED_COLOR} fontFamily={FONT} textAnchor="middle" className="chart-tick"><title>{title}</title>
    {lines.map((l, k) => <tspan key={k} x={x} dy={k ? 13 : 0}>{l}</tspan>)}</text>;
}

/** Marca de discontinuidad: linea punteada entre dos categorias con su texto a la vista (y en el nombre accesible). */
function Divider({x, height, label}: {x: number; height: number; label: string}) {
  return <g role="img" aria-label={label} className="chart-divider"><title>{label}</title>
    <line x1={x} x2={x} y1={-6} y2={height} stroke={TEXT_COLOR} strokeWidth={1.5} strokeDasharray="5 4"/>
    <text x={x + 6} y={6} fontSize={11} fill={TEXT_COLOR} fontFamily={FONT}>{short(label, 60)}</text>
  </g>;
}

/**
 * Columnas verticales para un eje de periodo (E13, ajuste 3): una serie, agrupadas por columnas o pareadas con la cifra
 * base de una comparacion (gris); siempre desde 0 y con el periodo en el eje horizontal.
 */
function Columns({data}: {data: ChartData}) {
  const series = data.series;
  const legendH = series.length > 1 ? 30 : 8, left = 56, right = 24, plotH = 220, top = data.divider ? 18 : 0;
  const plot = W - left - right;
  const n = Math.max(1, data.categories.length);
  const band = plot / n, groupW = Math.min(64, band * 0.72), barW = groupW / Math.max(1, series.length);
  const peak = Math.max(0, ...series.flatMap(s => s.values.map(v => v ?? 0)));
  const sc = scale(peak, !data.percent);
  const height = legendH + top + plotH + 52;
  const axis = periodAxis(data.categories.map(c => c.label), band);
  const value = n <= 24 && series.length <= 2;
  return <svg xmlns="http://www.w3.org/2000/svg" viewBox={`0 0 ${W} ${height}`} width={W} height={height} className="chart-svg">
    <Legend series={series} y={18}/>
    <g transform={`translate(${left} ${legendH + top})`}>
      {sc.ticks.map(t => {
        const y = plotH - t / sc.max * plotH;
        return <g key={t}><line x1={0} x2={plot} y1={y} y2={y} stroke={GRID_COLOR} strokeWidth={1} strokeDasharray="3 4"/>
          <text x={-8} y={y + 4} fontSize={11} fill={MUTED_COLOR} fontFamily={FONT} textAnchor="end">{data.format(t)}</text></g>;
      })}
      <line x1={0} x2={plot} y1={plotH} y2={plotH} stroke={MUTED_COLOR} strokeWidth={1}/>
      {data.categories.map((c, i) => {
        const x0 = i * band + (band - groupW) / 2;
        return <g key={c.key}>
          {i % axis.step === 0 && <Tick x={i * band + band / 2} y={plotH + 18} {...axis.ticks[i]}/>}
          {series.map((s, si) => {
            const v = s.values[i];
            const h = barLength(v, sc.max, plotH);
            const x = x0 + si * barW;
            return <Mark key={s.key} label={`${c.label}${series.length > 1 ? ', ' + s.label : ''}: ${data.format(v)}`}>
              {h > 0 && <rect x={x} y={plotH - h} width={Math.max(1, barW - 2)} height={h} rx={Math.min(4, (barW - 2) / 3)} fill={color(s, si)} className="chart-grow-y"/>}
              {value && (series.length === 1 || (v ?? 0) > 0) && <text x={x + barW / 2 - 1} y={plotH - h - 4} fontSize={10} fill={TEXT_COLOR} fontFamily={FONT} textAnchor="middle">{data.format(v)}</text>}
            </Mark>;
          })}
        </g>;
      })}
      {data.divider && <Divider x={(data.divider.after + 1) * band} height={plotH} label={data.divider.label}/>}
    </g>
  </svg>;
}

/** Lineas para un eje de periodo: 2 px, marcadores de 8 px, base en 0; la serie base de una comparacion, punteada. */
function Lines({data}: {data: ChartData}) {
  const uid = 'g' + useId().replace(/:/g, '');
  const series = data.series;
  const legendH = series.length > 1 ? 30 : 8, left = 56, right = 24, plotH = 220, top = data.divider ? 18 : 0;
  const plot = W - left - right;
  const peak = Math.max(0, ...series.flatMap(s => s.values.map(v => v ?? 0)));
  const sc = scale(peak, !data.percent);
  const height = legendH + top + plotH + 52;
  const xs = linePoints(data.categories.map(() => 0), plot, plotH, sc.max).map(p => p.x);
  const axis = periodAxis(data.categories.map(c => c.label), xs.length > 1 ? xs[1] - xs[0] : plot);
  return <svg xmlns="http://www.w3.org/2000/svg" viewBox={`0 0 ${W} ${height}`} width={W} height={height} className="chart-svg">
    <Legend series={series} y={18}/>
    <g transform={`translate(${left} ${legendH + top})`}>
      {sc.ticks.map(t => {
        const y = plotH - t / sc.max * plotH;
        return <g key={t}><line x1={0} x2={plot} y1={y} y2={y} stroke={GRID_COLOR} strokeWidth={1} strokeDasharray="3 4"/>
          <text x={-8} y={y + 4} fontSize={11} fill={MUTED_COLOR} fontFamily={FONT} textAnchor="end">{data.format(t)}</text></g>;
      })}
      <line x1={0} x2={plot} y1={plotH} y2={plotH} stroke={MUTED_COLOR} strokeWidth={1}/>
      {data.categories.map((c, i) => i % axis.step === 0 && <Tick key={c.key} x={xs[i]} y={plotH + 18} {...axis.ticks[i]}/>)}
      {series.map((s, si) => {
        const pts = linePoints(s.values, plot, plotH, sc.max);
        const path = pts.reduce((acc, p, i) => p.y == null ? acc : acc + (i === 0 || pts[i - 1].y == null ? ' M ' : ' L ') + p.x.toFixed(1) + ' ' + p.y.toFixed(1), '').trim();
        // Area degradada bajo una serie completa (no la base de una comparacion): el color va como atributo del degradado.
        const full = !s.base && pts.length > 1 && pts.every(p => p.y != null);
        const area = full ? `${path} L ${pts[pts.length - 1].x.toFixed(1)} ${plotH} L ${pts[0].x.toFixed(1)} ${plotH} Z` : '';
        return <g key={s.key}>
          {full && <defs><linearGradient id={`${uid}a${si}`} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor={color(s, si)} stopOpacity={0.18}/><stop offset="100%" stopColor={color(s, si)} stopOpacity={0}/>
          </linearGradient></defs>}
          {full && <path d={area} fill={`url(#${uid}a${si})`} stroke="none"/>}
          <path d={path} fill="none" stroke={color(s, si)} strokeWidth={2.25} strokeLinejoin="round" strokeLinecap="round" strokeDasharray={s.base ? '6 4' : undefined} className="chart-draw"/>
          {pts.map((p, i) => p.y != null && <Mark key={i} label={`${data.categories[i].label}${series.length > 1 ? ', ' + s.label : ''}: ${data.format(p.value)}`}>
            <circle cx={p.x} cy={p.y} r={4} fill={color(s, si)} stroke="#ffffff" strokeWidth={2}/>
          </Mark>)}
        </g>;
      })}
      {data.divider && xs.length > 1 && <Divider x={((xs[data.divider.after] ?? 0) + (xs[data.divider.after + 1] ?? plot)) / 2} height={plotH} label={data.divider.label}/>}
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
  const [tip, setTip] = useState<{text: string; x: number; y: number; below: boolean} | null>(null);
  const hidden = useRef<{title: Element; text: string} | null>(null);
  // El archivo descargado es el SVG del grafico sin clases, estilos ni animaciones (0.8.1, regla 4): mismas marcas, cifras
  // y colores como atributos que en 0.8.0. Se reconstruye como elemento para que chartExport.ts no cambie.
  const svg = () => {
    const el = ref.current?.querySelector('svg');
    if (!el) return null;
    return new DOMParser().parseFromString(exportMarkup(new XMLSerializer().serializeToString(el)), 'image/svg+xml').documentElement as unknown as SVGSVGElement;
  };
  // Ayuda emergente al pasar el mouse o enfocar una marca: el mismo texto de su nombre accesible. Mientras se muestra, el
  // <title> de la marca se vacia para que el navegador no muestre la suya encima, y se restituye al salir.
  const restore = () => {if (hidden.current) {hidden.current.title.textContent = hidden.current.text; hidden.current = null;}};
  const show = (target: EventTarget | null) => {
    const mark = (target as Element | null)?.closest?.('.chart-mark');
    const box = ref.current?.getBoundingClientRect();
    if (!mark || !box) return;
    restore();
    const title = mark.querySelector('title');
    if (title) {hidden.current = {title, text: title.textContent ?? ''}; title.textContent = '';}
    const r = mark.getBoundingClientRect();
    const top = r.top - box.top;
    setTip({text: mark.getAttribute('aria-label') ?? '', x: Math.min(Math.max(r.left - box.left + r.width / 2 + (ref.current?.scrollLeft ?? 0), 90), box.width - 90 + (ref.current?.scrollLeft ?? 0)),
      y: top < 48 ? r.bottom - box.top + 8 : top - 8, below: top < 48});
  };
  const hide = () => {restore(); setTip(null);};
  const texts = {title, subtitle, notes, source};
  const download = async (format: 'png' | 'svg') => {
    setError('');
    hide();
    const el = svg();
    if (!el) return;
    try {
      if (format === 'svg') downloadSvg(el, texts, file);
      else await downloadPng(el, texts, file);
    } catch (e) {
      setError((e as Error).message);
    }
  };
  const body = kind === 'lineas' ? <Lines data={data}/> : kind === 'columnas' ? <Columns data={data}/> : kind === 'circular' ? <Pie data={data}/> : <Bars data={data} mode={kind}/>;
  return <figure className="stat-chart" aria-labelledby={id + 't'}>
    <figcaption>
      <strong id={id + 't'}>{title}</strong>
      {subtitle && <span className="stat-chart-sub">{subtitle}</span>}
    </figcaption>
    <div ref={ref} className="stat-chart-canvas" role="group" aria-label={`${KIND_LABELS[kind]}: ${title}. ${data.categories.length} categorías.${tableId ? ' Las cifras están también en la tabla.' : ''}`}
      onMouseOver={e => show(e.target)} onMouseLeave={hide} onFocus={e => show(e.target)} onBlur={hide}>{body}
      {tip && <span className={'chart-tip' + (tip.below ? ' below' : '')} style={{left: tip.x, top: tip.y}} aria-hidden="true">{tip.text}</span>}</div>
    {data.divider && <p className="stat-chart-divider"><span aria-hidden="true">┆ </span>{data.divider.label}</p>}
    {notes.length > 0 && <ul className="stat-chart-notes">{notes.map(n => <li key={n}>{n}</li>)}</ul>}
    <p className="stat-source">{source}</p>
    <div className="actions">
      <button type="button" className="sm" onClick={() => void download('png')}><Icon name="fileImage" size={16}/>Descargar PNG</button>
      <button type="button" className="sm" onClick={() => void download('svg')}><Icon name="fileCode" size={16}/>Descargar SVG</button>
      {tableId && <button type="button" className="sm ghost" onClick={() => document.getElementById(tableId)?.focus()}>Ver la tabla</button>}
    </div>
    {error && <p role="alert" className="error">{error}</p>}
  </figure>;
}
