// Datos sinteticos del explorador estadistico (gate E12): un /api/catalog con el bloque stats que publica StatEngine.catalog()
// (subconjunto con los mismos codigos y la misma forma) y respuestas de POST /api/stats/query escritas a mano. Lo usan
// stats.test.ts y stats-render.test.mjs.
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const STATES = [['INFORMADO', 'Informado'], ['PENDIENTE', 'Pendiente'], ['SIN_INFORMACION', 'Sin información'], ['NO_DECLARADO', 'No declarado'], ['NO_APLICA', 'No aplica']]
  .map(([code, label]) => ({code, label}));
const dim = (code, label, type, multiple = false, extra = {}) => ({code, label, type, cardinality: multiple ? 'MULTIPLE' : 'UNICA', catalog: null, filterable: type !== 'PERIODO',
  measures: null, options: [], ...extra});
export const STATS = {
  methodology_version: '1', max_cells: 20000, enabled: true,
  measures: [
    ['INGRESOS', 'Ingresos', 'COM', 'RECEPCION'], ['EXCLUIDOS', 'Ingresos inválidos (excluidos)', 'COM', 'RECEPCION'], ['REQ_CONSTITUIDOS', 'Requerimientos constituidos (REQ)', 'COM', 'CONSTITUCION_REQ'],
    ['CASOS', 'Casos', 'CASO', 'CREACION_CASO'], ['ACTUACIONES', 'Actuaciones', 'ACTUACION', 'RECEPCION'], ['REQUERIMIENTOS_CON_VALOR', 'Requerimientos con el valor', 'COM', 'RECEPCION'],
    ['MENCIONES', 'Menciones', 'COM', 'RECEPCION'], ['COMPLETITUD', 'Completitud por variable', 'VARIABLE', 'RECEPCION'],
  ].map(([code, label, unit, basis]) => ({code, label, unit, percent_unit: ['CASO', 'ACTUACION', 'VARIABLE'].includes(unit) ? unit : 'COM',
    needs_multiple: code === 'REQUERIMIENTOS_CON_VALOR' || code === 'MENCIONES', default_date_basis: basis})),
  dimensions: [
    dim('PERIODO_MES', 'Mes', 'PERIODO'), dim('PERIODO_ANIO', 'Año', 'PERIODO'),
    dim('TARDANZA', 'Registro tardío', 'LISTA', false, {options: [{code: 'SI', label: 'Registrado después del periodo'}, {code: 'NO', label: 'Registrado dentro del periodo'}]}),
    dim('CANAL', 'Canal original', 'LISTA', false, {options: [{code: 'CORREO', label: 'Correo'}, {code: 'TELEFONO', label: 'Teléfono'}]}),
    dim('VIA_INGRESO', 'Vía de ingreso', 'CATALOGO', false, {catalog: 'VIA_INGRESO'}),
    dim('TIPO_SOLICITUD', 'Tipo de solicitud', 'CATALOGO', false, {catalog: 'TIPO_SOLICITUD'}),
    dim('TIPO_REQUIRENTE', 'Tipo de requirente', 'CATALOGO', false, {catalog: 'TIPO_REQUIRENTE'}),
    dim('REGION_REQUIRENTE', 'Región del requirente', 'REGION'),
    dim('REGION_NNA', 'Región del NNA afectado', 'REGION', true),
    dim('ADSCRIPCION_INICIAL', 'Adscripción institucional', 'AMBITO', false, {options: [{code: 'OFICINA_NACIONAL', label: 'Oficina Nacional'}, {code: 'PENDIENTE', label: 'Pendiente de asignación'}]}),
    dim('DERECHO_EJE', 'Eje de derechos', 'CATALOGO', true, {catalog: 'DERECHO'}), dim('DERECHO', 'Derecho', 'CATALOGO', true, {catalog: 'DERECHO'}),
    dim('DERECHO_SUBCATEGORIA', 'Subcategoría del derecho', 'CATALOGO', true, {catalog: 'DERECHO'}),
    dim('CONTEXTO', 'Contexto', 'CATALOGO', true, {catalog: 'CONTEXTO'}),
    dim('TIPO_ACTUACION', 'Tipo de actuación', 'CATALOGO', true, {catalog: 'TIPO_ACTUACION', measures: ['ACTUACIONES']}),
    dim('EDAD_NNA', 'Edad del NNA afectado', 'EDAD', true),
    ...['TIPO_SOLICITUD', 'TIPO_REQUIRENTE', 'VIA_INGRESO', 'DERECHO', 'CONTEXTO'].map(v => dim('ESTADO_' + v, 'Estado de la clasificación: ' + v.toLowerCase().replace(/_/g, ' '), 'LISTA', false, {options: STATES})),
    dim('VARIABLE', 'Variable', 'LISTA', false, {measures: ['COMPLETITUD'], options: [{code: 'TIPO_SOLICITUD', label: 'Tipo de solicitud'}]}),
  ],
  special: [['PENDIENTE', 'Pendiente'], ['SIN_INFORMACION', 'Sin información'], ['NO_DECLARADO', 'No declarado'], ['NO_APLICA', 'No aplica'], ['SIN_DESGLOSE', 'Sin subcategoría']].map(([code, label]) => ({code, label})),
  periods: ['PERSONALIZADO', 'MES', 'TRIMESTRE', 'SEMESTRE', 'ANIO', 'CUENTA_PUBLICA'], date_basis: ['RECEPCION', 'REGISTRO', 'CONSTITUCION_REQ', 'CREACION_CASO'],
  time_modes: ['VIGENTE', 'AL_INGRESO', 'AL_REQ', 'FECHA'], compares: ['PERIODO_ANTERIOR', 'MISMO_PERIODO_ANIO_ANTERIOR', 'CORTE'], percents: ['TOTAL', 'FILA', 'COLUMNA'],
};
const file = name => JSON.parse(readFileSync(fileURLToPath(new URL('../../deploy/config-ejemplo/catalogos/' + name, import.meta.url)), 'utf8'));
const published = (name, catalog, id) => ({catalog, label: file(name).label, version: {id}, items: file(name).items.map(i => ({code: i.code, label: i.label, parent_code: i.parent ?? null,
  selectable: i.selectable !== false, description: i.descripcion ?? null}))});
export const CATALOG = {
  stats: STATS,
  regions: [{code: '05', name: 'Valparaíso'}, {code: '07', name: 'Maule'}, {code: '13', name: 'Metropolitana de Santiago'}],
  scopes: [{id: 1, code: 'UPR-REQ', name: 'Requerimientos', level: 'CENTRAL', state: 'ACTIVA'}, {id: 6, code: 'REG-06', name: 'Sede O’Higgins', level: 'REGIONAL', state: 'ACTIVA'},
    {id: 7, code: 'REG-07', name: 'Sede Maule', level: 'REGIONAL', state: 'PROYECTADA'}],
  units: [{code: 'UPR', name: 'Unidad de Protección'}],
  stat_catalogs: {catalogs: [published('tipo-solicitud-v1.json', 'TIPO_SOLICITUD', 11), published('tipo-requirente-v1.json', 'TIPO_REQUIRENTE', 12),
    published('via-ingreso-v1.json', 'VIA_INGRESO', 13), published('derechos-2022-v2.json', 'DERECHO', 14)], special: []},
};
export const USER = {id: 5, username: 'estudios', name: 'Cuenta de Estudios', home_scope: 1, must_change: false, grants: [{role: 'REPORTES', scope: null}], substitutions: [],
  can_report: true, can_export: true};
const items = name => CATALOG.stat_catalogs.catalogs.find(c => c.catalog === name).items;
export const SOLICITUD = items('TIPO_SOLICITUD');
export const DERECHOS = items('DERECHO');

/** Respuesta sintetica de POST /api/stats/query con la forma de StatEngine.response. */
export function response({rows = [], columns = [], values = {}, cells = [], grand, rowTotals = [], colTotals = [], comparison = null, warnings = [], coverage = {}, measure = 'INGRESOS',
  percent = null, late = null, proposals = []}) {
  return {
    metadata: {methodology_version: '1', measure, rows, columns, filters: [], period: {type: 'CUENTA_PUBLICA', from: '2025-07-01', to: '2026-06-30', date_basis: 'RECEPCION'},
      zone: 'America/Santiago', time_mode: 'VIGENTE', as_of: null, cut_id: null, percent, percent_unit: 'COM', mention_dimension: null, catalog_versions: {}, calculated_ms: 1764000000000},
    values, cells: cells.map(c => ({mentions: null, percent_numerator: null, denominator: null, percent: null, ...c})),
    totals: {grand, rows: rowTotals, columns: colTotals}, comparison, late_records: late, data_coverage: coverage, proposals, warnings,
  };
}
