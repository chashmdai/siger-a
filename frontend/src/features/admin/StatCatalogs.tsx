import {useEffect, useRef, useState} from 'react';
import {request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {list, text, when} from '../../lib/rows';
import {reason} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Table} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {Callout, ErrorMessage, Facts, PageHeader, Panel, Skeleton} from '../../components/Primitives';
import type {CatalogDef, Group, Preview, Version} from './statCatalogsModel';
import {activationNote, canActivate, groupSummary, groupsOf, itemRows, MAX_CONTENT, mappingLoaded, mappingSources, mappingsOf, normalized, previewCatalog,
  previewLines, previewMapping, shortHash, STATE_LABELS, stateTone, versionsOf} from './statCatalogsModel';

/**
 * Catalogos estadisticos (0.8.0, D27, gate E02; solo TI): importar un archivo (queda propuesta), revisar sus items,
 * activarlo con fundamento (retira la vigente anterior) y cargar correspondencias entre versiones. El archivo viaja
 * como texto en el comando (StatCatalogs); el servidor valida todo y responde con el item que falla.
 */
export default function StatCatalogs({ctx, initial}: {ctx: Context; initial?: Row}) {
  // initial: datos ya cargados (solo el render en servidor de tests/statCatalogs-render.test.mjs); sin el, se piden al servidor.
  const [data, setData] = useState<Row | null>(initial ?? null), [error, setError] = useState(''), [problem, setProblem] = useState('');
  const [detail, setDetail] = useState<Row | null>(null), [detailError, setDetailError] = useState('');
  const importInput = useRef<HTMLInputElement>(null), mappingInput = useRef<HTMLInputElement>(null), mappingTarget = useRef<Version | null>(null);
  useEffect(() => {
    if (initial) return;
    let live = true;
    void request('/stat-catalogs').then(r => {if (live) setData(r);}).catch(e => {if (live) setError(e.message);});
    return () => {live = false;};
  }, [initial]);

  if (error) return <ErrorMessage>{error}</ErrorMessage>;
  if (!data) return <div aria-busy="true" aria-label="Cargando catálogos estadísticos"><Skeleton title lines={4}/></div>;
  const catalogs: CatalogDef[] = list(data, 'catalogs').map(c => ({catalog: text(c, 'catalog'), label: text(c, 'label')}));
  const versions = versionsOf(list(data, 'versions')), mappings = mappingsOf(list(data, 'mappings'));
  const groups = groupsOf(catalogs, versions);
  const labelOf = (catalog: string) => catalogs.find(c => c.catalog === catalog)?.label ?? 'Catálogo';
  const byId = new Map(versions.map(v => [v.id, v]));

  async function read(file: File | undefined): Promise<string | null> {
    setProblem('');
    if (!file) return null;
    if (file.size > MAX_CONTENT * 4) {setProblem(`«${file.name}» excede el tamaño admitido.`); return null;}
    return normalized(await file.text());
  }
  async function importChosen(file: File | undefined) {
    const content = await read(file);
    if (content == null || !file) return;
    const p: Preview = previewCatalog(content, catalogs);
    if (!p.ok) {setProblem(`«${file.name}»: ${p.problem}`); return;}
    ctx.act({title: 'Importar ' + p.catalogLabel, type: 'CATALOG_IMPORT', id: null, revision: null,
      fields: [{...reason, label: 'Fundamento de la importación', help: 'Por ejemplo, la solicitud o el documento que respalda esta versión.'}],
      note: `Archivo «${file.name}». Queda como propuesta: no se ofrece ni se exige hasta que se active. SIGER revisa todo el archivo antes de grabar: si un ítem tiene un problema, no se graba nada y se indica cuál.`,
      review: async () => ({content: <Facts stacked items={previewLines(p)}/>, extra: {content}})});
  }
  async function mappingChosen(file: File | undefined) {
    const content = await read(file), target = mappingTarget.current;
    if (content == null || !file || !target) return;
    const p = previewMapping(content);
    if (!p.ok) {setProblem(`«${file.name}»: ${p.problem}`); return;}
    const sources = mappingSources(versions, target).filter(v => !mappingLoaded(mappings, v.id, target.id));
    if (sources.length === 0) {setProblem('Ya están cargadas las correspondencias hacia la versión ' + target.id + ' desde todas las demás versiones de ' + labelOf(target.catalog) + '.'); return;}
    ctx.act({title: 'Correspondencia hacia la versión ' + target.id + ' de ' + labelOf(target.catalog), type: 'CATALOG_MAPPING_IMPORT', id: null, revision: null,
      fields: [{key: 'from_version', label: 'Versión de origen', type: 'select', required: true, options: sources.map(v => ({value: v.id, label: `Versión ${v.id} · ${v.label} (${STATE_LABELS[v.state].toLocaleLowerCase('es-CL')})`}))},
        {...reason, label: 'Fundamento de la correspondencia'}],
      note: `Archivo «${file.name}» con ${p.pairs.toLocaleString('es-CL')} ${p.pairs === 1 ? 'par' : 'pares'}. Permite comparar cifras registradas con la versión de origen y con esta versión. Se carga una sola vez por par de versiones.`,
      review: async payload => ({content: <Facts stacked items={[['Origen', 'Versión ' + String(payload.from_version)], ['Destino', 'Versión ' + target.id], ['Pares', p.pairs.toLocaleString('es-CL')]]}/>, extra: {content, to_version: target.id}})});
  }
  const activate = (g: Group, v: Version) => ctx.act({title: `Activar versión ${v.id} de ${g.label}`, type: 'CATALOG_ACTIVATE', id: v.id, revision: v.revision,
    fields: [{...reason, label: 'Fundamento de la activación', help: 'Acto o validación institucional que aprueba esta versión.'}], note: activationNote(g, v)});
  async function show(v: Version) {
    setDetailError('');
    try {setDetail(await request('/stat-catalogs/' + v.id));}
    catch (e) {setDetailError((e as Error).message);}
  }

  const shown = detail ? byId.get(Number(detail.id)) ?? null : null;
  const items = detail ? itemRows(list(detail, 'items')) : [];
  return <>
    <PageHeader eyebrow={<><Icon name="layers" size={14}/>Administración</>} title="Catálogos estadísticos"
      lead="Listas de valores con que se clasifican los requerimientos (tipo de solicitud, vía, derechos y otros). Cada archivo se importa como propuesta y se activa solo cuando la institución lo valida; mientras un catálogo no tenga versión vigente, SIGER no lo ofrece ni lo exige."
      aside={<button className="primary" onClick={() => importInput.current?.click()}><Icon name="upload" size={18}/>Importar catálogo</button>}/>
    <input ref={importInput} type="file" accept=".json,application/json" hidden aria-hidden="true" tabIndex={-1}
      onChange={e => {const f = e.target.files?.[0]; e.target.value = ''; void importChosen(f);}}/>
    <input ref={mappingInput} type="file" accept=".json,application/json" hidden aria-hidden="true" tabIndex={-1}
      onChange={e => {const f = e.target.files?.[0]; e.target.value = ''; void mappingChosen(f);}}/>
    {problem && <Callout tone="warn" role="alert" title="No se puede usar ese archivo">{problem}</Callout>}
    <Callout tone="info" title="Archivos de referencia">
      El paquete de SIGER trae en <span className="nowrap">config-ejemplo\catalogos</span> la propuesta versión 1 y el modelo de derechos 2.0 (2022), con su fuente y huella en LEEME.txt. Las plantillas de contexto, caracterización y tramos de edad se completan con los valores que defina la institución.
    </Callout>
    {groups.map(g => <Panel key={g.catalog} title={g.label} icon="layers" count={g.versions.length} description={groupSummary(g)}>
      <Table rows={g.versions as unknown as Row[]} empty="Aún no se importa ninguna versión de este catálogo." emptyIcon="layers" columns={[
        {name: 'Versión', render: r => <span><span className="cell-strong">{text(r, 'label')}</span> <small className="nowrap">N.º {text(r, 'id')}</small></span>},
        {name: 'Estado', render: r => <span className={stateTone(r.state as Version['state'])}>{STATE_LABELS[r.state as Version['state']]}</span>},
        {name: 'Ítems', render: r => <span className="tabular">{Number(r.items).toLocaleString('es-CL')}</span>},
        {name: 'Importada', render: r => <span>{when(r.loadedMs)} · {text(r, 'loadedBy')}</span>},
        {name: 'Huella', render: r => <code title={text(r, 'sha')}>{shortHash(text(r, 'sha'))}</code>},
        {name: 'Acciones', render: r => {const v = r as unknown as Version; return <span className="actions">
          <button className="sm" onClick={() => void show(v)}>Ver ítems</button>{' '}
          {canActivate(v) && <><button className="sm primary" onClick={() => activate(g, v)}>Activar</button>{' '}</>}
          {mappingSources(versions, v).length > 0 && <button className="sm" onClick={() => {mappingTarget.current = v; mappingInput.current?.click();}}>Correspondencia</button>}
        </span>;}},
      ]}/>
      {mappings.some(m => byId.get(m.to)?.catalog === g.catalog) && <p className="field-help">
        Correspondencias cargadas: {mappings.filter(m => byId.get(m.to)?.catalog === g.catalog).map(m => `versión ${m.from} → versión ${m.to} (${m.pairs.toLocaleString('es-CL')} ${m.pairs === 1 ? 'par' : 'pares'})`).join('; ')}.
      </p>}
    </Panel>)}
    {detailError && <ErrorMessage>{detailError}</ErrorMessage>}
    {detail && <Panel title={`${text(detail, 'catalog_label')}: versión ${text(detail, 'id')}`} icon="layers" count={items.length}
      description={text(detail, 'label')} actions={<button className="sm" onClick={() => setDetail(null)}>Cerrar</button>}>
      <Facts items={[
        ['Estado', <span className={stateTone(text(detail, 'state') as Version['state'])}>{STATE_LABELS[text(detail, 'state') as Version['state']] ?? text(detail, 'state')}</span>],
        ['Fuente', text(detail, 'source'), true],
        ['Importada', `${when(detail.loaded_ms)} · ${text(detail, 'loaded_by_name')}`],
        ['Fundamento de la importación', text(detail, 'basis'), true],
        ...(detail.activated_ms != null ? [['Activada', `${when(detail.activated_ms)} · ${text(detail, 'activated_by_name')}`] as [string, string], ['Fundamento de la activación', text(detail, 'activation_basis'), true] as [string, string, boolean]] : []),
        ...(detail.retired_ms != null ? [['Retirada', `${when(detail.retired_ms)} · ${text(detail, 'retired_by_name')}`] as [string, string]] : []),
        ['Huella (SHA-256)', <code className="break">{text(detail, 'file_sha256')}</code>, true],
      ]}/>
      {shown && canActivate(shown) && <Callout tone="warn" title="Propuesta">Revisa cada ítem contra su fuente antes de activarla.</Callout>}
      <Table rows={items as unknown as Row[]} empty="La versión no tiene ítems." emptyIcon="layers" columns={[
        {name: 'Ítem', render: r => <span style={{paddingInlineStart: (Number(r.depth) - 1) * 1.25 + 'rem'}} className={Number(r.depth) === 1 ? 'cell-strong' : undefined}>{text(r, 'label')}</span>},
        {name: 'Código', render: r => <code>{text(r, 'code')}</code>},
        {name: 'Seleccionable', render: r => r.selectable ? 'Sí' : <span className="chip">No: agrupa</span>},
        ...(items.some(i => i.range) ? [{name: 'Edades', render: (r: Row) => text(r, 'range')}] : []),
        {name: 'Referencia en la fuente', render: r => text(r, 'ref')},
      ]}/>
    </Panel>}
  </>;
}
