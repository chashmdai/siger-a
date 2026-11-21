import {useState} from 'react';
import type {FormEvent, ReactNode} from 'react';
import {downloadPost, postJson} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {list, number, text, when} from '../../lib/rows';
import {COMPLEXITY_LABELS, LEVEL_LABELS, MATTER_LABELS, MODALITY_LABELS, ORIGIN_LABELS, REPORT_GROUP_LABELS, CHANNEL_LABELS, INTAKE_LABELS, humanize} from '../../lib/labels';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {Table} from '../../components/Table';
import {Busy, Callout, EmptyState, ErrorMessage, PageHeader, Panel} from '../../components/Primitives';
import {displayDay} from '../admin/calendar';
import type {Filters, ReportDef} from './reports';
import {activeFilters, barWidth, chileToday, clearFilters, count, days, defaultSplit, EXITS, exportBody, filenameOf, groupParts, groupsFor, hours,
  initialFilters, keepGroup, NOTE, REPORT_HELP, reportName, requestBody, tallyLabel} from './reports';

type Named = {code: string; name: string};
const KIND_TITLES: Record<string, string> = {VOLUMEN: 'Volumen', TIEMPO: 'Tiempos', ANTIGUEDAD: 'Antigüedad'};

/**
 * Reportes de gestion (G12; calculo en G11): cifras agregadas del alcance de la cuenta, sin datos de personas. La
 * consulta se envia solo al presionar Consultar (cada una queda en la auditoria); con Exportacion, los CSV de REQ y Casos
 * usan el mismo periodo y filtros. El servidor decide alcance, agrupaciones permitidas y limite de filas.
 */
export default function Reports({ctx}: {ctx: Context}) {
  const reports = list(ctx.catalog, 'reports') as unknown as ReportDef[];
  const regions = list(ctx.catalog, 'regions').map(r => ({code: text(r, 'code'), name: text(r, 'name')}));
  const units = list(ctx.catalog, 'units').map(r => ({code: text(r, 'code'), name: text(r, 'name')}));
  const scopes = list(ctx.catalog, 'scopes');
  const people = ctx.user.can_report_people === true, exporter = ctx.user.can_export === true;
  const [f, setF] = useState<Filters>(() => initialFilters(chileToday()));
  const [data, setData] = useState<Row | null>(null), [sent, setSent] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [exporting, setExporting] = useState(''), [exportError, setExportError] = useState('');
  const report = reports.find(r => r.code === f.report);
  const groups = groupsFor(report, people);
  const body = requestBody(f, report), key = JSON.stringify(body);
  const set = (patch: Partial<Filters>) => setF(x => ({...x, ...patch}));
  const pick = (code: string) => {
    const next = reports.find(r => r.code === code);
    setF(x => ({...x, report: code, group: keepGroup(x.group, groupsFor(next, people)), ...defaultSplit(code)}));
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      setData(await postJson('/reports', body));
      setSent(key);
    } catch (x) {
      setError((x as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function exportRows(kind: 'REQ' | 'CAS') {
    setExporting(kind);
    setExportError('');
    try {
      await downloadPost('/reports/export/' + kind, exportBody(f), 'siger-' + kind.toLowerCase() + '.csv', filenameOf);
      ctx.message(kind === 'REQ' ? 'Archivo de REQ generado.' : 'Archivo de Casos generado.');
    } catch (x) {
      setExportError((x as Error).message);
    } finally {
      setExporting('');
    }
  }

  const select = (label: string, field: keyof Filters, options: {value: string; label: string}[], empty = 'Todos') =>
    <label><span>{label}</span><select value={String(f[field])} onChange={e => set({[field]: e.target.value} as Partial<Filters>)}>
      <option value="">{empty}</option>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select></label>;
  const byKind = (kind: string) => reports.filter(r => r.kind === kind);
  const filtersOn = activeFilters(f);
  const scopeOptions = scopes.filter(s => f.level === '' || text(s, 'level') === f.level).filter(s => f.unit === '' || text(s, 'unit_code') === f.unit)
    .map(s => ({value: text(s, 'id'), label: text(s, 'code') + ' · ' + text(s, 'name')}));
  const age = report?.kind === 'ANTIGUEDAD';

  return <>
    <PageHeader eyebrow={<><Icon name="chart" size={14}/>Reportes</>} title="Reportes de gestión"
      lead="Cifras agregadas de tu alcance, sin datos de personas. Los tiempos se informan en horas corridas (h) y en días hábiles (dh): lunes a viernes, sin los feriados del calendario hábil."/>
    <Callout tone="neutral">{NOTE}</Callout>
    <Panel title="Consulta" icon="search">
      <form className="report-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="span-all"><span>Reporte</span>
            <select value={f.report} onChange={e => pick(e.target.value)}>
              {['VOLUMEN', 'TIEMPO', 'ANTIGUEDAD'].map(kind => <optgroup key={kind} label={KIND_TITLES[kind]}>
                {byKind(kind).map(r => <option key={r.code} value={r.code}>{reportName(r.code)}</option>)}
              </optgroup>)}
            </select>
            <small>{REPORT_HELP[f.report]}</small>
          </label>
          {age
            ? <p className="field-help span-all">La antigüedad se calcula a la fecha de hoy, sin período.</p>
            : <>
              <label><span>Desde</span><input type="date" required value={f.from} max={f.to} onChange={e => set({from: e.target.value})}/></label>
              <label><span>Hasta</span><input type="date" required value={f.to} min={f.from} onChange={e => set({to: e.target.value})}/></label>
            </>}
          {report?.kind !== 'VOLUMEN' && <label><span>Agrupar por</span>
            <select value={f.group} onChange={e => set({group: e.target.value})}>
              {groups.map(g => <option key={g} value={g}>{REPORT_GROUP_LABELS[g] ?? humanize(g)}</option>)}
            </select>
            {!people && <small>El desglose por funcionario requiere la función Reportes por persona.</small>}
          </label>}
          {report?.kind !== 'VOLUMEN' && <div className="report-splits">
            <label className="check"><input type="checkbox" checked={f.byOrigin} disabled={f.group === 'ORIGEN'} onChange={e => set({byOrigin: e.target.checked})}/><span>Separar por origen (SOL o atención directa)</span></label>
            {EXITS.includes(f.report) && <label className="check"><input type="checkbox" checked={f.byExit} disabled={f.group === 'SALIDA'} onChange={e => set({byExit: e.target.checked})}/><span>Separar por salida</span></label>}
          </div>}
        </div>
        <details className="disclosure report-filters" open={filtersOn > 0 || undefined}>
          <summary><Icon name="layers" size={18}/>Filtros <small>{filtersOn === 0 ? 'ninguno activo' : filtersOn === 1 ? '1 activo' : filtersOn + ' activos'}</small><Icon name="down" size={18}/></summary>
          <div className="disclosure-body">
            <div className="form-grid three">
              {select('Unidad', 'unit', units.map(u => ({value: u.code, label: u.code + ' · ' + u.name})), 'Todas')}
              {select('Nivel', 'level', ['CENTRAL', 'REGIONAL'].map(v => ({value: v, label: LEVEL_LABELS[v]})))}
              {select('Región', 'region', regions.map(r => ({value: r.code, label: r.name})), 'Todas')}
              {select('Ámbito o sede', 'scope', scopeOptions)}
              {select('Complejidad', 'complexity', [...['BAJA', 'MEDIANA', 'ALTA'].map(v => ({value: v, label: COMPLEXITY_LABELS[v]})), {value: 'SIN', label: 'Sin clasificar'}], 'Todas')}
              {select('Canal', 'channel', Object.keys(CHANNEL_LABELS).map(v => ({value: v, label: CHANNEL_LABELS[v]})))}
              {select('Materia', 'matter', [...Object.keys(MATTER_LABELS).map(v => ({value: v, label: MATTER_LABELS[v]})), {value: 'SIN', label: 'Sin materia'}], 'Todas')}
              {select('Modalidad de la atención', 'modality', Object.keys(MODALITY_LABELS).map(v => ({value: v, label: MODALITY_LABELS[v]})), 'Todas')}
              {select('Origen del REQ', 'origin', Object.keys(ORIGIN_LABELS).map(v => ({value: v, label: ORIGIN_LABELS[v]})))}
            </div>
            <p className="field-help">La materia es la del REQ (o la sugerida de la SOL) y, en los tiempos de Casos, la del Caso. La modalidad solo existe en las primeras atenciones.</p>
            {filtersOn > 0 && <button type="button" className="sm" onClick={() => setF(clearFilters)}><Icon name="close" size={16}/>Quitar filtros</button>}
          </div>
        </details>
        <div className="actions">
          <button className="primary" disabled={busy}><Busy busy={busy} label="Calculando…" idle={<><Icon name="chart" size={18}/>Consultar</>}/></button>
          {exporter && <>
            <button type="button" disabled={exporting !== ''} onClick={() => void exportRows('REQ')}><Busy busy={exporting === 'REQ'} label="Generando…" idle={<><Icon name="download" size={18}/>Exportar REQ (CSV)</>}/></button>
            <button type="button" disabled={exporting !== ''} onClick={() => void exportRows('CAS')}><Busy busy={exporting === 'CAS'} label="Generando…" idle={<><Icon name="download" size={18}/>Exportar Casos (CSV)</>}/></button>
          </>}
        </div>
        {exporter && <p className="field-help">La exportación usa el período y los filtros (no la agrupación): una fila por REQ o Caso creado en el período, con folios, códigos, fechas y tiempos. No incluye nombres, RUN, relatos, motivos ni contactos. Separador punto y coma.</p>}
        {exportError && <ErrorMessage>{exportError}</ErrorMessage>}
      </form>
    </Panel>
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {data && sent !== key && <Callout tone="info" role="status">Cambiaste la consulta: presiona Consultar para actualizar el resultado.</Callout>}
    {data && <Result data={data} regions={regions}/>}
  </>;
}

function Result({data, regions}: {data: Row; regions: Named[]}) {
  const calendar = (data.calendar ?? null) as Row | null;
  const warnings = calendar ? (calendar.warnings as string[] | undefined) ?? [] : [];
  const period = data.from ? 'Período ' + displayDay(text(data, 'from')) + ' al ' + displayDay(text(data, 'to')) : 'A la fecha: ' + when(number(data, 'as_of'));
  return <section className="report-result" aria-labelledby="report-result-title" aria-live="polite">
    <h2 id="report-result-title" className="report-result-title">{reportName(text(data, 'report'))} <small>{period}</small></h2>
    {warnings.map(w => <Callout key={w} tone="warn" title={w}>Los días hábiles de ese año se calcularon solo con los fines de semana. TI carga los feriados en Calendario hábil.</Callout>)}
    {data.kind === 'TIEMPO' && <TimeResult data={data} regions={regions}/>}
    {data.kind === 'VOLUMEN' && <VolumeResult data={data}/>}
    {data.kind === 'ANTIGUEDAD' && <AgeResult data={data} regions={regions}/>}
    {calendar && <p className="field-help">{text(calendar, 'note')} Agregar o retirar un feriado cambia también los cálculos de períodos pasados.</p>}
  </section>;
}

/** Cifras de un tiempo: tramos, mediana, p90 y maximo en horas y dias habiles. */
function Stats({stats, wait}: {stats: Row; wait?: boolean}) {
  const h = (stats.hours ?? {}) as Row, d = (stats.business_days ?? {}) as Row;
  const w = (stats.wait ?? null) as Row | null;
  const items: [string, ReactNode][] = [
    ['Tramos', count(stats.n)],
    ['Mediana', <>{hours(h.median)}<small>{days(d.median)}</small></>],
    ['Percentil 90', <>{hours(h.p90)}<small>{days(d.p90)}</small></>],
    ['Máximo', <>{hours(h.max)}<small>{days(d.max)}</small></>],
  ];
  if (wait && w) items.push(['Espera (mediana)', <>{hours((w.hours as Row)?.median)}<small>{count(w.n)} con espera</small></>]);
  return <dl className="report-stats">
    {items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
  </dl>;
}

function TimeResult({data, regions}: {data: Row; regions: Named[]}) {
  const total = data.total as Row, rows = list(data, 'rows'), dimensions = (data.dimensions as string[]) ?? [];
  const wait = total.wait != null, rebuilt = number(total, 'reconstructed');
  const max = Math.max(0, ...rows.map(r => Number(((r.hours ?? {}) as Row).median ?? 0)));
  return <>
    <Panel title="Total" icon="clock"><Stats stats={total} wait={wait}/>
      {rebuilt > 0 && <p className="field-help">{count(rebuilt)} {rebuilt === 1 ? 'tramo fue reconstruido' : 'tramos fueron reconstruidos'} desde la historia anterior a 0.5.0.</p>}
    </Panel>
    {dimensions.length > 0 && <Panel title="Por grupo" icon="layers" count={rows.length} flush>
      <Table rows={rows} empty="Sin tramos en el período y el alcance consultados." emptyIcon="chart" columns={[
        ...dimensions.map(d => ({name: REPORT_GROUP_LABELS[d] ?? humanize(d), render: (r: Row) =>
          <span className="cell-strong">{groupParts([d], (r.group ?? {}) as Row, regions)[0].label}</span>})),
        {name: 'Tramos', render: r => <span className="tabular">{count(r.n)}</span>},
        {name: 'Mediana', render: r => <Bar value={((r.hours ?? {}) as Row).median} max={max} label={hours(((r.hours ?? {}) as Row).median)} detail={days(((r.business_days ?? {}) as Row).median)}/>},
        {name: 'P90', render: r => <span className="tabular nowrap">{hours(((r.hours ?? {}) as Row).p90)} · {days(((r.business_days ?? {}) as Row).p90)}</span>},
        {name: 'Máximo', render: r => <span className="tabular nowrap">{hours(((r.hours ?? {}) as Row).max)} · {days(((r.business_days ?? {}) as Row).max)}</span>},
        ...(wait ? [{name: 'Espera (mediana)', render: (r: Row) => <span className="tabular nowrap">{hours(((((r.wait ?? {}) as Row).hours ?? {}) as Row).median)}</span>}] : []),
        {name: 'Reconstruidos', render: r => <span className="tabular">{count(r.reconstructed)}</span>},
      ]}/>
    </Panel>}
    {number(total, 'n') === 0 && <EmptyState compact icon="chart" title="Sin tramos en el período">No hay tramos de este tiempo que terminen en el período, con tu alcance y los filtros elegidos.</EmptyState>}
  </>;
}

/** Barra horizontal con su valor siempre en texto (el estado nunca se comunica solo por la barra). */
function Bar({value, max, label, detail}: {value: unknown; max: number; label: ReactNode; detail?: ReactNode}) {
  return <span className="report-bar">
    <span className="report-bar-track" aria-hidden="true"><span className="report-bar-fill" style={{width: barWidth(value, max) + '%'}}/></span>
    <span className="report-bar-value tabular">{label}{detail && <small>{detail}</small>}</span>
  </span>;
}

/** Conteos de un campo como lista de barras. */
function Tally({title, items, field}: {title: string; items: Row[]; field: string}) {
  const max = Math.max(0, ...items.map(i => Number(i.count ?? 0)));
  return <div className="report-tally">
    <h3>{title}</h3>
    {items.length === 0 ? <p className="muted">Sin registros.</p> : <ul>
      {items.map((i, n) => <li key={String(i.value ?? i.channel ?? '') + n}>
        <span className="report-tally-label">{field === 'channel' ? humanize(i.channel, CHANNEL_LABELS) + ' · ' + humanize(i.intake, INTAKE_LABELS) : field === 'intake' ? humanize(i.value, INTAKE_LABELS) : tallyLabel(field, i.value)}</span>
        <Bar value={i.count} max={max} label={count(i.count)}/>
      </li>)}
    </ul>}
  </div>;
}

function VolumeResult({data}: {data: Row}) {
  const com = data.com as Row, req = data.req as Row, cases = data.cases as Row;
  const rows = (r: Row, k: string) => (r[k] as Row[] | undefined) ?? [];
  const noMatter = number(req, 'regional_without_matter');
  return <>
    <dl className="report-stats">
      <div><dt>Comunicaciones</dt><dd>{count(com.total)}</dd></div>
      <div><dt>Requerimientos</dt><dd>{count(req.total)}<small>{count(req.with_case)} con Caso · {count(req.without_case)} sin Caso</small></dd></div>
      <div><dt>Casos</dt><dd>{count(cases.total)}</dd></div>
    </dl>
    {noMatter > 0 && <Callout tone="warn" title={noMatter === 1 ? '1 REQ regional sin materia' : count(noMatter) + ' REQ regionales sin materia'}>La Coordinación regional registra la materia con el botón Materia del REQ; sin ella no entra en la lectura nacional por materia.</Callout>}
    <Panel title="Comunicaciones" icon="mail">
      <div className="report-tallies">
        <Tally title="Por ingreso" items={rows(com, 'by_intake')} field="intake"/>
        <Tally title="Por canal e ingreso" items={rows(com, 'by_channel')} field="channel"/>
      </div>
    </Panel>
    <Panel title="Requerimientos" icon="folder">
      <div className="report-tallies">
        <Tally title="Por estado" items={rows(req, 'by_state')} field="state"/>
        <Tally title="Por unidad" items={rows(req, 'by_unit')} field="unit"/>
        <Tally title="Por complejidad" items={rows(req, 'by_complexity')} field="complexity"/>
        <Tally title="Por origen" items={rows(req, 'by_origin')} field="origin"/>
      </div>
    </Panel>
    <Panel title="Casos" icon="briefcase">
      <div className="report-tallies">
        <Tally title="Por estado" items={rows(cases, 'by_state')} field="state"/>
        <Tally title="Por materia" items={rows(cases, 'by_matter')} field="matter"/>
      </div>
    </Panel>
  </>;
}

const BRACKETS = ['0-5', '6-10', '11-20', '21+'];
function AgeResult({data, regions}: {data: Row; regions: Named[]}) {
  const dimensions = (data.dimensions as string[]) ?? [];
  const block = (title: string, icon: 'folder' | 'briefcase', part: Row) => {
    const totals = (part.brackets ?? {}) as Row, max = Math.max(0, ...BRACKETS.map(b => Number(totals[b] ?? 0)));
    return <Panel title={title} icon={icon} count={number(part, 'n')}>
      <div className="report-tally"><ul>
        {BRACKETS.map(b => <li key={b}><span className="report-tally-label">{b === '21+' ? 'Más de 20 días hábiles' : b + ' días hábiles'}</span><Bar value={totals[b]} max={max} label={count(totals[b])}/></li>)}
      </ul></div>
      {dimensions.length > 0 && <Table rows={list(part, 'rows')} empty="Sin abiertos en tu alcance." emptyIcon="chart" columns={[
        ...dimensions.map(d => ({name: REPORT_GROUP_LABELS[d] ?? humanize(d), render: (r: Row) => <span className="cell-strong">{groupParts([d], (r.group ?? {}) as Row, regions)[0].label}</span>})),
        {name: 'Abiertos', render: r => <span className="tabular">{count(r.n)}</span>},
        ...BRACKETS.map(b => ({name: b === '21+' ? '21+ dh' : b + ' dh', render: (r: Row) => <span className="tabular">{count(((r.brackets ?? {}) as Row)[b])}</span>})),
      ]}/>}
    </Panel>;
  };
  return <>
    {block('Requerimientos abiertos', 'folder', data.req as Row)}
    {block('Casos abiertos', 'briefcase', data.cases as Row)}
  </>;
}
