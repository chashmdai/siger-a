import {useCallback, useEffect, useRef, useState} from 'react';
import type {Context} from '../../app/context';
import {Chart} from '../../components/charts/Chart';
import {Icon} from '../../components/Icon';
import {Callout, ErrorMessage, Panel, Skeleton} from '../../components/Primitives';
import {downloadPost} from '../../lib/api';
import {filenameOf} from '../reports/reportsModel';
import type {Query, StatsCatalog} from './statsModel';
import {chileDay, emptyQuery, encodeRoute, errorText, FIGURE_DIMS, FIGURE_TITLES, figureRows, figureTable, fileStem, fmtInt, fmtPct, howToRead, periodLabel, publicAccount, publicYearOf,
  readResult, requestBody, sameMinute, sourceFooter} from './statsModel';
import {queue, useStat} from './statQuery';
import {Freshness, HowToRead} from './ResultView';

/** Cuentas Publicas elegibles: las seis ultimas completas (julio a junio), la mas reciente primero. */
export function figureYears(today: string) {
  const last = publicYearOf(today) - 1;
  return Array.from({length: 6}, (_, i) => last - i);
}
/** Consulta de un desglose de la Figura 1: ingresos de julio a junio por la dimension, con el porcentaje del total. */
export const figureQuery = (dim: string, year: number): Query => ({...emptyQuery(publicAccount(year)), rows: [dim], percent: 'TOTAL'});

function Breakdown({ctx, cat, dim, year, scope, exporter, report, ownFresh}: {ctx: Context; cat: StatsCatalog; dim: string; year: number; scope: string; exporter: boolean;
  report: (dim: string, at: number, refresh: () => void) => void; ownFresh: boolean}) {
  const q = figureQuery(dim, year);
  const body = requestBody(q, cat);
  const s = useStat(body, ctx.user.id);
  const [busy, setBusy] = useState(''), [error, setError] = useState('');
  const at = s.status === 'ok' ? s.at : 0;
  useEffect(() => report(dim, at, s.refresh), [dim, at, report, s.refresh]);
  const title = `Figura 1, ${FIGURE_TITLES[dim].toLocaleLowerCase('es-CL')}: Cuenta Pública ${year}-${year + 1}`;
  const download = (format: 'csv' | 'xlsx') => {
    setBusy(format);
    setError('');
    queue.run(() => downloadPost('/stats/export/' + format, body, fileStem(title) + '.' + format, filenameOf))
      .then(() => ctx.message('Archivo generado.'), (e: Error & {status?: number}) => setError(errorText(e))).finally(() => setBusy(''));
  };
  if (s.status === 'error') return <Panel title={FIGURE_TITLES[dim]}><ErrorMessage>{s.error}</ErrorMessage></Panel>;
  if (s.status !== 'ok' || !s.data) return <Panel title={FIGURE_TITLES[dim]}><div aria-busy="true"><Skeleton lines={5}/></div></Panel>;
  const r = readResult(s.data);
  const figure = figureTable(figureRows(cat, ctx.catalog, dim, r));
  const rows = figure.rows;
  const percent = (key: string) => r.cells.find(c => c.row[0] === key)?.percent ?? 0;
  const source = sourceFooter({at: s.at, scope, basis: 'RECEPCION', mode: 'VIGENTE'});
  const tableId = 'figura-' + dim.toLowerCase();
  return <Panel title={FIGURE_TITLES[dim]} actions={exporter ? <>
    <button type="button" className="sm" disabled={busy !== ''} onClick={() => download('csv')}><Icon name="download" size={16}/>{busy === 'csv' ? 'Generando…' : 'CSV'}</button>
    <button type="button" className="sm" disabled={busy !== ''} onClick={() => download('xlsx')}><Icon name="download" size={16}/>{busy === 'xlsx' ? 'Generando…' : 'XLSX'}</button>
  </> : undefined}>
    {error && <ErrorMessage>{error}</ErrorMessage>}
    <div className="stat-breakdown">
      <div className="stat-breakdown-table">
        <div className="table-scroll">
          <table id={tableId} tabIndex={-1} className="stat-table sticky-first">
            <caption>{title}</caption>
            <thead><tr><th scope="col">Categoría</th><th scope="col" className="num">Ingresos</th><th scope="col" className="num">% del total</th></tr></thead>
            <tbody>{rows.map(x => <tr key={x.key}><th scope="row">{x.label}</th><td className="num">{fmtInt(x.n)}</td><td className="num">{fmtPct(percent(x.key))}</td></tr>)}</tbody>
            <tfoot><tr className="total"><th scope="row">Total</th><td className="num">{fmtInt(r.grand.n)}</td><td className="num">{r.grand.n > 0 ? fmtPct(100) : '—'}</td></tr></tfoot>
          </table>
        </div>
        {figure.note && <p className="field-help stat-empty-note">{figure.note}</p>}
      </div>
      <div className="stat-breakdown-chart">
        <Chart kind="barras" title={title} subtitle={periodLabel(r.metadata.period as Record<string, unknown>)} notes={figure.note ? [figure.note] : []} source={source} file={fileStem(title)} tableId={tableId}
          data={{categories: rows.map(x => ({key: x.key, label: x.label})), series: [{key: 'n', label: 'Ingresos', values: rows.map(x => x.n)}], total: r.grand.n, percent: false,
            format: v => v == null ? '—' : fmtInt(v), keepOrder: true}}/>
      </div>
    </div>
    {ownFresh && <Freshness at={s.at} refresh={s.refresh}/>}
  </Panel>;
}

/**
 * Informes (DISENO 10.3): la Figura 1 de la Cuenta Publica (julio a junio) con sus cuatro desgloses de ingresos (tipo de
 * solicitud, requirente, via y adscripcion institucional, DI-13), con todas las categorias del catalogo (0 si no hay) en el
 * orden institucional; los estados especiales sin registros van en una nota (E13, ajuste 1). Tabla y grafico lado a lado
 * en escritorio (ajuste 2) y "Datos de las HH:MM" una sola vez si los cuatro desgloses son del mismo momento (ajuste 6).
 * Cada desglose suma el total y se descarga en CSV y XLSX con la funcion Exportacion; con Gestion estadistica se congela.
 */
export default function Informes({ctx, cat, scope, params}: {ctx: Context; cat: StatsCatalog; scope: string; params: URLSearchParams}) {
  const years = figureYears(chileDay());
  const year = years.includes(Number(params.get('cp'))) ? Number(params.get('cp')) : years[0];
  const exporter = ctx.user.can_export === true;
  const q = figureQuery(FIGURE_DIMS[0], year);
  const dims = FIGURE_DIMS.filter(d => cat.dimensions.some(x => x.code === d));
  const known = useRef(new Map<string, {at: number; refresh: () => void}>());
  const [ats, setAts] = useState<Record<string, number>>({});
  const report = useCallback((dim: string, at: number, refresh: () => void) => {
    known.current.set(dim, {at, refresh});
    setAts(prev => prev[dim] === at ? prev : {...prev, [dim]: at});
  }, []);
  const common = sameMinute(dims.map(d => ats[d] ?? 0));
  return <section aria-label="Informes">
    <div className="stat-toolbar">
      <label><span>Cuenta Pública</span><select value={year} onChange={e => {location.hash = encodeRoute('informes', undefined, {cp: e.target.value === String(years[0]) ? '' : e.target.value});}}>
        {years.map(y => <option key={y} value={y}>{`Julio de ${y} a junio de ${y + 1}`}</option>)}
      </select></label>
      {common != null && <Freshness at={common} refresh={() => known.current.forEach(k => k.refresh())}/>}
      {ctx.user.can_manage_stats === true && <button type="button" className="sm" onClick={() => {location.hash = encodeRoute('cortes', {...q, rows: [], percent: ''}, {nuevo: '1'});}}>
        <Icon name="layers" size={16}/>Congelar estas cifras</button>}
    </div>
    <h2 className="stat-section-title">Figura 1: ingresos de la Cuenta Pública {year}-{year + 1}</h2>
    {!exporter && <Callout tone="neutral">Para descargar estas tablas en CSV o XLSX necesitas la función Exportación.</Callout>}
    <div className="stat-figure">
      {dims.map(d => <Breakdown key={d + year} ctx={ctx} cat={cat} dim={d} year={year} scope={scope} exporter={exporter} report={report} ownFresh={common == null}/>)}
    </div>
    <HowToRead lines={howToRead({cat, q: {...q, rows: []}, r: null, scope, extra: [
      'Cada desglose cuenta los mismos ingresos una vez: todos suman el total.',
      'Se muestran todas las categorías del listado vigente, también las que no tienen ingresos (0). Los estados especiales aparecen como fila solo si tienen ingresos: «Pendiente» reúne los ingresos aún sin clasificar; los que no tienen ingresos se nombran bajo la tabla.',
      'La adscripción institucional es la sede regional u Oficina Nacional a la que se adscribió primero cada ingreso; «Pendiente de asignación», si aún no tiene destino.',
    ]})}/>
  </section>;
}
