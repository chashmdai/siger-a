import {useCallback, useEffect, useState} from 'react';
import type {Context} from '../../app/context';
import {Chart} from '../../components/charts/Chart';
import {Icon} from '../../components/Icon';
import {Callout, EmptyState, ErrorMessage, Panel, Skeleton} from '../../components/Primitives';
import {command, downloadGet, downloadPost, postJson, request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {list, text} from '../../lib/rows';
import {filenameOf} from '../reports/reportsModel';
import type {CutDraft, CutRow, ScopeOption} from './cutsModel';
import {CUT_EXPLAIN, CUT_STEPS, CUT_WARNING, cutBody, cutDraft, cutQuery, cutStateLabel, firstProblem, managedScopes, onCut, readCut, stepProblem, verifyText} from './cutsModel';
import type {Mapping, SeriesDraft} from './seriesModel';
import {draftProblem, emptySeries, formatOf, GRANULARITIES, scopeNote, SERIES_COLUMNS, SERIES_STEPS, seriesBody, seriesCategories, seriesChart, seriesStateLabel, seriesWarning,
  sigerQuery, suggestions, sumRows, toBase64} from './seriesModel';
import type {Query, StatsCatalog} from './statsModel';
import {chileDateTime, chileDay, emptyQuery, encodeRoute, errorText, fmtInt, panoramaPeriod, PERIOD_LABELS, periodLabel, periodStart, readResult, requestBody, sourceFooter} from './statsModel';
import {queue} from './statQuery';
import {queryTitle} from './ResultView';
import {ExportBar} from './ExportBar';

const uuid = () => (globalThis.crypto?.randomUUID?.() ?? Date.now().toString(16) + Math.random().toString(16).slice(2));
const when = (iso: string) => iso ? chileDateTime(Date.parse(iso)) : '';
const scopeName = (ctx: Context, id: unknown) => id == null ? 'Toda la institución' : text(list(ctx.catalog, 'scopes').find(s => Number(s.id) === Number(id)) ?? {}, 'name') || 'Ámbito';

function Steps({names, step}: {names: readonly string[]; step: number}) {
  return <ol className="stat-steps" aria-label="Pasos">{names.map((n, i) => <li key={n} className={i === step ? 'on' : i < step ? 'done' : ''} aria-current={i === step ? 'step' : undefined}>
    <span className="stat-step-n">{i + 1}</span>{n}</li>)}</ol>;
}

// --- Cortes -------------------------------------------------------------------------------------------------------------
/** Paso de revision del corte: lo que se congela y el aviso de la detencion, textual (GO de E13, C). */
export function CutReview({draft, scopeLabel}: {draft: CutDraft; scopeLabel: string}) {
  return <>
    <dl className="facts">
      <div><dt>Alcance</dt><dd>{scopeLabel}</dd></div>
      <div><dt>Periodo</dt><dd>{periodLabel(draft.period)}</dd></div>
      <div><dt>Título</dt><dd>{draft.title}</dd></div>
      <div><dt>Fundamento</dt><dd>{draft.reason}</dd></div>
    </dl>
    <p>{CUT_EXPLAIN}</p>
    <Callout tone="warn" title="Antes de generar">{CUT_WARNING}.</Callout>
  </>;
}

/**
 * Asistente de corte en cuatro pasos (DISENO 10.4; GO de E13, C): alcance, periodo, titulo y fundamento, y revision con el
 * aviso de la detencion. Prellenado desde la consulta; explica que el corte congela todo el periodo y el alcance y que los
 * filtros se aplican al consultarlo. Al terminar ofrece "Ver estas cifras sobre el corte".
 */
export function CutWizard({ctx, cat, q, scopes, onDone, onCancel}: {ctx: Context; cat: StatsCatalog; q: Query | null; scopes: ScopeOption[]; onDone: () => void; onCancel: () => void}) {
  const today = chileDay();
  const [draft, setDraft] = useState<CutDraft>(() => cutDraft(q, q ? queryTitle(cat, q) : '', scopes, panoramaPeriod(today).period));
  const [step, setStep] = useState(0), [key] = useState(uuid), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [created, setCreated] = useState<number | null>(null);
  const problem = step < 3 ? stepProblem(draft, step) : '';
  const set = (p: Partial<CutDraft>) => {setDraft(d => ({...d, ...p})); setError('');};
  const confirm = async () => {
    const bad = firstProblem(draft);
    if (bad >= 0) {setStep(bad); return;}
    setBusy(true);
    setError('');
    try {
      const r = await command('STAT_CUT_CREATE', null, null, cutBody(draft), key);
      setCreated(r.id);
      ctx.message('Corte generado.');
      onDone();
    } catch (e) {
      setError(errorText(e as Error & {status?: number}));
    } finally {
      setBusy(false);
    }
  };
  if (created != null) {
    const base = q && q.period.from === draft.period.from && q.period.type === draft.period.type ? q : {...emptyQuery(draft.period), rows: ['ADSCRIPCION_INICIAL']};
    return <Panel title="Corte generado" icon="check">
      <p>El corte quedó guardado con su huella de integridad. No se puede modificar ni borrar; solo anular con fundamento.</p>
      <div className="actions">
        <button type="button" className="primary" onClick={() => {location.hash = encodeRoute('explorar', onCut({...base, period: draft.period}, created));}}>Ver estas cifras sobre el corte</button>
        <button type="button" className="ghost" onClick={onCancel}>Volver a la lista</button>
      </div>
    </Panel>;
  }
  return <Panel title="Generar un corte estadístico" icon="layers">
    <Steps names={CUT_STEPS} step={step}/>
    <p className="field-help">{CUT_EXPLAIN}</p>
    {step === 0 && <fieldset className="stat-choices"><legend>¿Qué alcance congela el corte?</legend>
      {scopes.length === 0 ? <p className="field-help">Tu cuenta no tiene la función Gestión estadística en ningún ámbito.</p>
        : scopes.map(s => <label key={String(s.id)} className="check"><input type="radio" name="alcance-corte" checked={draft.scope === s.id} onChange={() => set({scope: s.id})}/><span>{s.label}</span></label>)}
    </fieldset>}
    {step === 1 && <div className="form-grid">
      <label><span>Tipo de periodo</span><select value={draft.period.type} onChange={e => {
        const type = e.target.value;
        set({period: type === 'PERSONALIZADO' ? {type, from: draft.period.from, to: draft.period.to ?? today} : {type, from: periodStart(type, draft.period.from)}});
      }}>{cat.periods.map(p => <option key={p} value={p}>{PERIOD_LABELS[p] ?? p}</option>)}</select></label>
      <label><span>{draft.period.type === 'PERSONALIZADO' ? 'Desde' : 'Periodo que contiene el día'}</span><input type="date" value={draft.period.from}
        onChange={e => {if (e.target.value) set({period: {...draft.period, from: draft.period.type === 'PERSONALIZADO' ? e.target.value : periodStart(draft.period.type, e.target.value)}});}}/></label>
      {draft.period.type === 'PERSONALIZADO' && <label><span>Hasta</span><input type="date" value={draft.period.to ?? ''} min={draft.period.from}
        onChange={e => set({period: {...draft.period, to: e.target.value}})}/></label>}
      <p className="field-help span-all">Periodo elegido: {periodLabel(draft.period)}.</p>
    </div>}
    {step === 2 && <div className="form-grid">
      <label className="span-all"><span>Título</span><input type="text" maxLength={200} value={draft.title} onChange={e => set({title: e.target.value})}/></label>
      <label className="span-all"><span>Fundamento</span><textarea rows={3} maxLength={12000} value={draft.reason} onChange={e => set({reason: e.target.value})}/>
        <small>Para qué se congelan estas cifras (por ejemplo, la Cuenta Pública). Queda en el corte, no en la auditoría.</small></label>
    </div>}
    {step === 3 && <CutReview draft={draft} scopeLabel={scopes.find(s => s.id === draft.scope)?.label ?? '—'}/>}
    {problem && step < 3 && <p className="field-help">{problem}</p>}
    {error && <ErrorMessage>{error}</ErrorMessage>}
    <div className="actions">
      {step > 0 && <button type="button" className="ghost" disabled={busy} onClick={() => setStep(step - 1)}><Icon name="arrowLeft" size={18}/>Atrás</button>}
      {step < 3 ? <button type="button" className="primary" disabled={problem !== ''} onClick={() => setStep(step + 1)}>Continuar</button>
        : <button type="button" className="primary" disabled={busy} onClick={() => void confirm()}>{busy ? 'Generando el corte…' : 'Generar el corte'}</button>}
      <button type="button" className="ghost" disabled={busy} onClick={onCancel}>Cancelar</button>
    </div>
  </Panel>;
}

function CutItem({ctx, c, reload}: {ctx: Context; c: CutRow; reload: () => void}) {
  const [verify, setVerify] = useState(''), [busy, setBusy] = useState(false), [detail, setDetail] = useState<Row | null>(null), [open, setOpen] = useState(false), [error, setError] = useState('');
  const base = {...emptyQuery({type: 'CUENTA_PUBLICA', from: '2000-07-01'}), rows: ['ADSCRIPCION_INICIAL']};
  const consult = cutQuery(base, c);
  const check = () => {
    setBusy(true); setError('');
    let out: Row = {};
    queue.run(async () => {out = await postJson('/stats/cuts/' + c.id + '/verify', {});}).then(() => setVerify(verifyText(out)), (e: Error & {status?: number}) => setError(errorText(e))).finally(() => setBusy(false));
  };
  const toggle = () => {
    setOpen(!open);
    if (!detail) request<Row>('/stats/cuts/' + c.id).then(setDetail, (e: Error) => setError(e.message));
  };
  const annul = () => ctx.act({title: 'Anular el corte ' + c.code, type: 'STAT_CUT_ANNUL', id: c.id, revision: c.revision, after: () => reload(),
    note: 'El corte anulado se conserva con sus filas y huellas, y se puede seguir consultando y verificando. La anulación no se deshace.',
    fields: [{key: 'reason', label: 'Fundamento de la anulación', type: 'textarea', required: true, max: 12000}]});
  return <li className="stat-cut">
    <div className="stat-cut-head">
      <strong>{c.code}</strong> <span>{c.title}</span>
      <span className={'status' + (c.state === 'ANULADO' ? ' muted' : '')}>{cutStateLabel(c.state)}</span>
    </div>
    <p className="field-help">{c.scopeLabel} · {c.period} · congelado el {when(c.asOf)} · {fmtInt(c.rows)} ingresos congelados · por {c.createdBy || 'una cuenta'}</p>
    <div className="actions">
      <button type="button" className="sm" onClick={() => {location.hash = encodeRoute('explorar', consult);}}>Consultar</button>
      <button type="button" className="sm" onClick={() => {location.hash = encodeRoute('explorar', {...base, period: consult.period, compare: 'CORTE', compareCut: c.id});}}>Comparar con hoy</button>
      <button type="button" className="sm" disabled={busy} onClick={check}>{busy ? 'Verificando…' : 'Verificar integridad'}</button>
      <button type="button" className="sm ghost" aria-expanded={open} onClick={toggle}>{open ? 'Ocultar detalle' : 'Ver detalle'}</button>
      {c.canAnnul && <button type="button" className="sm ghost" onClick={annul}>Anular</button>}
    </div>
    {verify && <p role="status" className={verify.startsWith('Íntegro') ? 'stat-ok' : 'error'}>{verify}</p>}
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {open && <div className="stat-cut-detail">
      {!detail ? <Skeleton lines={3}/> : <dl className="facts">
        <div><dt>Fundamento</dt><dd>{text(detail, 'basis')}</dd></div>
        <div><dt>Huella de las filas</dt><dd><code>{text(detail, 'rows_sha256')}</code></dd></div>
        <div><dt>Huella de los metadatos</dt><dd><code>{text(detail, 'metadata_sha256')}</code></dd></div>
        {detail.annul_basis != null && <div><dt>Anulado</dt><dd>{when(text(detail, 'annulled_at'))}: {text(detail, 'annul_basis')}</dd></div>}
      </dl>}
      <ExportBar ctx={ctx} q={consult} body={requestBody(consult)} title={'Corte ' + c.code}/>
    </div>}
  </li>;
}

function Cuts({ctx, cat, prefill}: {ctx: Context; cat: StatsCatalog; prefill: Query | null}) {
  const scopes = managedScopes(ctx.user, ctx.catalog);
  const [cuts, setCuts] = useState<CutRow[] | null>(null), [error, setError] = useState('');
  const manager = ctx.user.can_manage_stats === true;
  const [wizard, setWizard] = useState(prefill != null && manager);
  const prefillKey = prefill ? JSON.stringify(requestBody(prefill)) : '';
  useEffect(() => {if (prefillKey && manager) setWizard(true);}, [prefillKey, manager]);
  const load = useCallback(() => {request<Row[]>('/stats/cuts').then(r => setCuts(r.map(readCut)), (e: Error) => setError(e.message));}, []);
  useEffect(load, [load]);
  return <>
    {wizard && <CutWizard key={prefillKey} ctx={ctx} cat={cat} q={prefill} scopes={scopes} onDone={load} onCancel={() => {setWizard(false); location.hash = encodeRoute('cortes');}}/>}
    <Panel title="Cortes estadísticos" icon="layers" actions={manager && !wizard ? <button type="button" className="sm" onClick={() => setWizard(true)}><Icon name="plus" size={16}/>Generar un corte</button> : undefined}>
      <p className="field-help">Un corte congela las cifras de un periodo y un alcance en un instante, con su huella de integridad: sirve para informes oficiales y para comparar con las cifras de hoy.</p>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {!cuts ? <Skeleton lines={4}/> : cuts.length === 0 ? <EmptyState icon="layers" title="Aún no hay cortes que puedas ver">{manager ? 'Genera uno con «Generar un corte» o con «Congelar estas cifras» desde una consulta.' : 'Los genera una cuenta con Gestión estadística.'}</EmptyState>
        : <ul className="stat-cuts">{cuts.map(c => <CutItem key={c.id} ctx={ctx} c={c} reload={load}/>)}</ul>}
    </Panel>
  </>;
}

// --- Series historicas ------------------------------------------------------------------------------------------------
/** Asistente de importacion de una serie (DISENO 7 y 10.4): datos, archivo, vista previa sin escrituras y confirmacion. */
export function SeriesWizard({ctx, scopes, onDone, onCancel}: {ctx: Context; scopes: ScopeOption[]; onDone: () => void; onCancel: () => void}) {
  const [draft, setDraft] = useState<SeriesDraft>(() => emptySeries(scopes[0]?.id));
  const [step, setStep] = useState(0), [key] = useState(uuid), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [file, setFile] = useState<{name: string; format: string; content: string} | null>(null);
  const [columns, setColumns] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState<Row | null>(null);
  const [checks, setChecks] = useState<(Mapping & {checked: boolean; text: string})[]>([]);
  const set = (p: Partial<SeriesDraft>) => {setDraft(d => ({...d, ...p})); setError('');};
  const confirmed = checks.filter(c => c.checked);
  const template = (format: 'csv' | 'xlsx') => downloadGet('/stats/series/template/' + format, 'plantilla-serie-historica.' + format, filenameOf).catch((e: Error) => setError(e.message));
  const pick = async (f: File | undefined) => {
    setPreview(null); setChecks([]); setColumns({});
    if (!f) {setFile(null); return;}
    setFile({name: f.name, format: formatOf(f.name), content: toBase64(new Uint8Array(await f.arrayBuffer()))});
  };
  const review = async () => {
    if (!file) return;
    setBusy(true); setError('');
    try {
      const p = await postJson('/stats/series/preview', seriesBody(draft, file, columns, []));
      setPreview(p);
      setChecks(suggestions(p));
      setStep(2);
    } catch (e) {
      setError(errorText(e as Error & {status?: number}));
    } finally {
      setBusy(false);
    }
  };
  const confirm = async () => {
    if (!file) return;
    setBusy(true); setError('');
    try {
      await command('STAT_SERIES_IMPORT', null, null, seriesBody(draft, file, columns, confirmed), key);
      ctx.message('Serie histórica importada.');
      onDone();
    } catch (e) {
      setError(errorText(e as Error & {status?: number}));
    } finally {
      setBusy(false);
    }
  };
  const problem = step === 0 ? draftProblem(draft) : step === 1 && !file ? 'Elige el archivo de la serie.' : '';
  const values = list(preview ?? {}, 'values');
  return <Panel title="Importar una serie histórica" icon="upload">
    <Steps names={SERIES_STEPS} step={step}/>
    {step === 0 && <div className="form-grid">
      <fieldset className="span-all stat-choices"><legend>Ámbito de la serie</legend>
        {scopes.map(s => <label key={String(s.id)} className="check"><input type="radio" name="alcance-serie" checked={draft.scope === s.id} onChange={() => set({scope: s.id})}/><span>{s.label}</span></label>)}
        <small>La ven las cuentas con Reportes cuyo alcance cubre este ámbito. Una serie institucional exige alcance institucional.</small>
      </fieldset>
      <label className="span-all"><span>Título</span><input type="text" maxLength={200} value={draft.title} onChange={e => set({title: e.target.value})}/></label>
      <label><span>Qué cuenta</span><input type="text" maxLength={40} value={draft.indicator} onChange={e => set({indicator: e.target.value})}/><small>Por ejemplo, «Ingresos».</small></label>
      <label><span>Periodicidad</span><select value={draft.granularity} onChange={e => set({granularity: e.target.value})}>{GRANULARITIES.map(g => <option key={g.code} value={g.code}>{g.label}</option>)}</select></label>
      <label><span>Desde</span><input type="date" value={draft.period_from} onChange={e => set({period_from: e.target.value})}/></label>
      <label><span>Hasta</span><input type="date" value={draft.period_to} min={draft.period_from} onChange={e => set({period_to: e.target.value})}/></label>
      <label className="span-all"><span>Fuente</span><input type="text" maxLength={300} value={draft.source} onChange={e => set({source: e.target.value})}/><small>El informe o documento de donde salen las cifras.</small></label>
      <label className="span-all"><span>Metodología</span><textarea rows={3} maxLength={12000} value={draft.methodology} onChange={e => set({methodology: e.target.value})}/></label>
      <label className="span-all"><span>Fundamento</span><textarea rows={2} maxLength={12000} value={draft.reason} onChange={e => set({reason: e.target.value})}/></label>
    </div>}
    {step === 1 && <>
      <p>Descarga la plantilla, completa una fila por periodo, campo y categoría, y súbela. También puedes usar tu propio archivo: en el paso siguiente indicas qué columna es cada dato.</p>
      <div className="actions"><button type="button" className="sm" onClick={() => void template('csv')}><Icon name="download" size={16}/>Plantilla CSV</button>
        <button type="button" className="sm" onClick={() => void template('xlsx')}><Icon name="download" size={16}/>Plantilla XLSX</button></div>
      <label className="field"><span>Archivo de la serie (CSV o XLSX)</span><input type="file" accept=".csv,.xlsx" onChange={e => void pick(e.target.files?.[0])}/></label>
      {file && <p className="field-help">Archivo elegido: {file.name}.</p>}
    </>}
    {step === 2 && preview && <>
      <p className="field-help">La vista previa no guarda nada: revisa las cifras antes de confirmar.</p>
      {preview.needs_columns === true ? <>
        <Callout tone="info" title="Indica qué columna es cada dato">El archivo no usa los encabezados de la plantilla.</Callout>
        <div className="form-grid">{SERIES_COLUMNS.map(c => <label key={c.key}><span>{c.label}</span><select value={columns[c.key] ?? ''} onChange={e => setColumns({...columns, [c.key]: e.target.value})}>
          <option value="">Elige una columna</option>{((preview.headers as string[]) ?? []).map(h => <option key={h} value={h}>{h}</option>)}</select></label>)}</div>
        <div className="actions"><button type="button" className="sm" disabled={busy || SERIES_COLUMNS.some(c => !columns[c.key])} onClick={() => void review()}>Revisar de nuevo</button></div>
      </> : <>
        {list(preview, 'warnings').map((w, i) => <Callout key={i} tone="warn">{seriesWarning(w)}</Callout>)}
        <div className="table-scroll"><table className="stat-table sticky-first"><caption>Vista previa: {fmtInt(values.length)} cifras</caption>
          <thead><tr><th scope="col">Periodo</th><th scope="col">Campo</th><th scope="col">Categoría</th><th scope="col" className="num">Cifra</th></tr></thead>
          <tbody>{values.slice(0, 100).map((v, i) => <tr key={i}><th scope="row">{periodLabel({type: draft.granularity, from: text(v, 'period_from'), to: text(v, 'period_to')})}</th>
            <td>{text(v, 'dimension')}</td><td>{v.is_total === true ? 'Total' : text(v, 'category_label')}</td><td className="num">{fmtInt(Number(v.value ?? 0))}</td></tr>)}</tbody>
        </table></div>
        {values.length > 100 && <p className="field-help">Se muestran las primeras 100 cifras.</p>}
        {checks.length > 0 && <fieldset className="stat-choices"><legend>Correspondencias sugeridas (confírmalas una a una)</legend>
          {checks.map((c, i) => <label key={c.dimension + c.category} className="check"><input type="checkbox" checked={c.checked}
            onChange={e => setChecks(checks.map((x, j) => j === i ? {...x, checked: e.target.checked} : x))}/><span>{c.text}</span></label>)}
          <small>Una correspondencia solo sirve para comparar con SIGER; no cambia el rótulo original de la serie.</small>
        </fieldset>}
      </>}
    </>}
    {step === 3 && <dl className="facts">
      <div><dt>Serie</dt><dd>{draft.title} ({draft.indicator})</dd></div>
      <div><dt>Ámbito</dt><dd>{scopes.find(s => s.id === draft.scope)?.label ?? '—'}</dd></div>
      <div><dt>Periodo</dt><dd>{periodLabel({type: 'PERSONALIZADO', from: draft.period_from, to: draft.period_to})}</dd></div>
      <div><dt>Cifras</dt><dd>{fmtInt(values.length)}</dd></div>
      <div><dt>Correspondencias confirmadas</dt><dd>{confirmed.length ? confirmed.map(c => c.text).join('; ') : 'Ninguna'}</dd></div>
    </dl>}
    {problem && <p className="field-help">{problem}</p>}
    {error && <ErrorMessage>{error}</ErrorMessage>}
    <div className="actions">
      {step > 0 && <button type="button" className="ghost" disabled={busy} onClick={() => setStep(step - 1)}><Icon name="arrowLeft" size={18}/>Atrás</button>}
      {step === 0 && <button type="button" className="primary" disabled={problem !== ''} onClick={() => setStep(1)}>Continuar</button>}
      {step === 1 && <button type="button" className="primary" disabled={problem !== '' || busy} onClick={() => void review()}>{busy ? 'Revisando…' : 'Ver la vista previa'}</button>}
      {step === 2 && <button type="button" className="primary" disabled={!preview || preview.needs_columns === true} onClick={() => setStep(3)}>Continuar</button>}
      {step === 3 && <button type="button" className="primary" disabled={busy} onClick={() => void confirm()}>{busy ? 'Importando…' : 'Importar la serie'}</button>}
      <button type="button" className="ghost" disabled={busy} onClick={onCancel}>Cancelar</button>
    </div>
  </Panel>;
}

/**
 * Serie junto a SIGER (GO de E13, D): dos lineas con la granularidad de la serie, desde su inicio hasta ayer; la
 * discontinuidad con texto; las notas de ambito y superposicion del servidor; suma solo con la casilla y si el servidor la
 * admite. Nunca se combinan.
 */
function SeriesCompare({ctx, cat, scope, series, all, close}: {ctx: Context; cat: StatsCatalog; scope: string; series: Row; all: Row[]; close: () => void}) {
  const today = chileDay();
  const cats = seriesCategories(series);
  const [choice, setChoice] = useState(cats[0]?.key ?? '');
  const [others, setOthers] = useState<number[]>([]), [sum, setSum] = useState(false);
  const [result, setResult] = useState<Row | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const chosen = cats.find(c => c.key === choice) ?? cats[0];
  const q = chosen ? sigerQuery(series, today, {dimension: chosen.dimension, code: chosen.code}) : null;
  const g = GRANULARITIES.find(x => x.code === text(series, 'granularity'));
  const body = JSON.stringify({ids: [Number(series.id), ...others], sum, q: q ? requestBody(q, cat) : null});
  useEffect(() => {
    let alive = true, out: Row = {};
    setBusy(true); setError('');
    const req: Row = {series_ids: [Number(series.id), ...others], sum};
    if (q) req.query = requestBody(q, cat);
    queue.run(async () => {out = await postJson('/stats/series/compare', req);}).then(() => {if (alive) setResult(out);}, (e: Error & {status?: number}) => {if (alive) setError(errorText(e));})
      .finally(() => {if (alive) setBusy(false);});
    return () => {alive = false;};
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [body]);
  const siger = result?.siger ? readResult(result.siger as Row) : null;
  const data = chosen ? seriesChart(series, chosen, siger, g?.dim ?? null) : null;
  const sums = sumRows(result);
  const title = `${text(series, 'title')} y SIGER`;
  return <Panel title={'Serie junto a SIGER: ' + text(series, 'title')} icon="chart" actions={<button type="button" className="sm ghost" onClick={close}>Cerrar</button>}>
    <div className="form-grid">
      <label><span>Línea de la serie</span><select value={choice} onChange={e => setChoice(e.target.value)}>{cats.map(c => <option key={c.key} value={c.key}>{c.label}</option>)}</select></label>
      {all.length > 1 && <fieldset className="stat-choices"><legend>Otras series para sumar</legend>
        {all.filter(s => s.id !== series.id).map(s => <label key={text(s, 'id')} className="check"><input type="checkbox" checked={others.includes(Number(s.id))}
          onChange={e => setOthers(e.target.checked ? [...others, Number(s.id)] : others.filter(x => x !== Number(s.id)))}/><span>{text(s, 'title')}</span></label>)}
        <label className="check"><input type="checkbox" checked={sum} onChange={e => setSum(e.target.checked)}/><span>Sumar las series elegidas (solo si SIGER confirma que no se superponen)</span></label>
      </fieldset>}
    </div>
    <p className="field-help">{scopeNote(scopeName(ctx, series.scope_id))}</p>
    {error && <ErrorMessage>{error}</ErrorMessage>}
    {busy && !result ? <Skeleton lines={5}/> : result && <>
      {list(result, 'warnings').map((w, i) => <Callout key={i} tone="warn">{seriesWarning(w)}</Callout>)}
      {!q && <Callout tone="info">La serie tiene periodos propios: SIGER no tiene un eje comparable. Revisa sus cifras en la tabla.</Callout>}
      {data && <Chart kind="lineas" data={data} title={title} subtitle={periodLabel({type: 'PERSONALIZADO', from: text(series, 'period_from'), to: q?.period.to ?? text(series, 'period_to')})}
        notes={['Las dos líneas no se suman: la serie histórica tiene su propia fuente y metodología.']} file={'siger-serie-' + text(series, 'id')}
        source={sourceFooter({at: Date.now(), scope, basis: 'RECEPCION', mode: 'VIGENTE'}) + ' Serie: ' + text(series, 'source') + '.'}/>}
      {sum && (sums ? <div className="table-scroll"><table className="stat-table sticky-first"><caption>Suma de las series elegidas (pedida)</caption>
        <thead><tr><th scope="col">Campo</th><th scope="col">Categoría</th><th scope="col" className="num">Cifra</th></tr></thead>
        <tbody>{sums.map((r, i) => <tr key={i}><th scope="row">{text(r, 'dimension')}</th><td>{r.is_total === true ? 'Total' : text(r, 'category')}</td><td className="num">{fmtInt(Number(r.value ?? 0))}</td></tr>)}</tbody>
      </table></div> : <p className="field-help">SIGER no suma estas series: deben estar vigentes, ser compatibles y no superponerse.</p>)}
    </>}
    {q && <p className="field-help">SIGER: {queryTitle(cat, q)}, {periodLabel(q.period)}.</p>}
  </Panel>;
}

function Series({ctx, cat, scope}: {ctx: Context; cat: StatsCatalog; scope: string}) {
  const scopes = managedScopes(ctx.user, ctx.catalog);
  const [series, setSeries] = useState<Row[] | null>(null), [error, setError] = useState(''), [wizard, setWizard] = useState(false);
  const [open, setOpen] = useState<Row | null>(null);
  const load = useCallback(() => {request<Row[]>('/stats/series').then(setSeries, (e: Error) => setError(e.message));}, []);
  useEffect(load, [load]);
  const manager = ctx.user.can_manage_stats === true;
  const view = (s: Row) => request<Row>('/stats/series/' + s.id).then(setOpen, (e: Error) => setError(e.message));
  const exportSeries = (s: Row, f: 'csv' | 'xlsx') => queue.run(() => downloadPost('/stats/series/' + s.id + '/export/' + f, {}, 'siger-serie-' + s.id + '.' + f, filenameOf))
    .then(() => ctx.message('Archivo generado.'), (e: Error & {status?: number}) => setError(errorText(e)));
  const annul = (s: Row) => ctx.act({title: 'Anular la serie', type: 'STAT_SERIES_ANNUL', id: Number(s.id), revision: Number(s.revision), after: () => load(),
    note: 'La serie anulada se conserva con sus cifras y correspondencias, pero ya no se ofrece para comparar.', fields: [{key: 'reason', label: 'Fundamento de la anulación', type: 'textarea', required: true, max: 12000}]});
  return <>
    {wizard && <SeriesWizard ctx={ctx} scopes={scopes} onDone={() => {setWizard(false); load();}} onCancel={() => setWizard(false)}/>}
    {open && <SeriesCompare ctx={ctx} cat={cat} scope={scope} series={open} all={(series ?? []).filter(s => text(s, 'state') !== 'ANULADA')} close={() => setOpen(null)}/>}
    <Panel title="Series históricas" icon="calendar" actions={manager && !wizard ? <button type="button" className="sm" onClick={() => setWizard(true)}><Icon name="upload" size={16}/>Importar una serie</button> : undefined}>
      <p className="field-help">Cifras agregadas de informes anteriores, sin datos de personas. Se muestran junto a SIGER, nunca combinadas con sus cifras.</p>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {!series ? <Skeleton lines={3}/> : series.length === 0 ? <EmptyState icon="calendar" title="Aún no hay series que puedas ver">{manager ? 'Importa una con «Importar una serie».' : 'Las importa una cuenta con Gestión estadística.'}</EmptyState>
        : <ul className="stat-cuts">{series.map(s => <li key={text(s, 'id')} className="stat-cut">
          <div className="stat-cut-head"><strong>{text(s, 'title')}</strong> <span className="status">{seriesStateLabel(text(s, 'state'))}</span></div>
          <p className="field-help">{text(s, 'indicator')} · {scopeName(ctx, s.scope_id)} · {periodLabel({type: 'PERSONALIZADO', from: text(s, 'period_from'), to: text(s, 'period_to')})} · {GRANULARITIES.find(g => g.code === text(s, 'granularity'))?.label ?? ''} · Fuente: {text(s, 'source')}</p>
          <div className="actions">
            <button type="button" className="sm" onClick={() => void view(s)}>Ver junto a SIGER</button>
            {ctx.user.can_export === true && <><button type="button" className="sm" onClick={() => void exportSeries(s, 'csv')}><Icon name="download" size={16}/>CSV</button>
              <button type="button" className="sm" onClick={() => void exportSeries(s, 'xlsx')}><Icon name="download" size={16}/>XLSX</button></>}
            {manager && text(s, 'state') !== 'ANULADA' && <button type="button" className="sm ghost" onClick={() => annul(s)}>Anular</button>}
          </div>
        </li>)}</ul>}
    </Panel>
  </>;
}

/** Seccion "Cortes y series" del explorador (E13; DISENO 10.4). nuevo=1 abre el asistente de corte con la consulta. */
export default function CutsSeries({ctx, cat, scope, query, params}: {ctx: Context; cat: StatsCatalog; scope: string; query: Query | null; params: URLSearchParams}) {
  const prefill = params.get('nuevo') === '1' ? query ?? null : null;
  return <section aria-label="Cortes y series">
    <Cuts ctx={ctx} cat={cat} prefill={params.get('nuevo') === '1' ? prefill ?? {...emptyQuery(panoramaPeriod(chileDay()).period)} : null}/>
    <Series ctx={ctx} cat={cat} scope={scope}/>
  </section>;
}
