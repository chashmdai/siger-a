// Caracterizacion estadistica en el flujo (0.8.0, D28-D31, gate E09; DISENO 10.1). Logica pura, sin React: listados
// vigentes publicados por el servidor (catalog.stat_catalogs), variables exigibles (catalog.stat_completeness), el bloque
// opcional "stat" de los comandos de ingreso, distribucion y cierre, y la cuenta de bloqueos que el dialogo de cierre puede
// resolver. Ninguna lista de variables ni regla de completitud esta fija aqui: todo sale de lo que entrega el servidor
// (StatValues.view, StatCompleteness.requirements). La propuesta del Formulario nunca se preselecciona ni se envia sola:
// solo con "Usar propuesta" (D29, decision B del GO de E09; diferencia registrada con DISENO 10.1).
import type {Row} from '../../lib/rows.ts';

export type CatalogItem = {code: string; label: string; parent: string; level: number; selectable: boolean; description: string};
export type StatList = {catalog: string; label: string; version: number | null; items: CatalogItem[]};
export type Special = {code: string; label: string};
export type Completeness = {intake: string[]; vulnerability: string[]; duplicate: string[]};
/** Variable de valor unico tal como la entrega la ficha (characterization.variables) o, sin ficha, una pendiente. */
export type StatVar = {variable: string; label: string; state: string; code: string; value_label: string; catalog_current: boolean; proposal: {code: string; label: string} | null};
/** Bloque "stat" listo para enviar: {VARIABLE: {code} | {status}, reason?}. undefined = no se envia. */
export type StatBlock = Record<string, {code?: string; status?: string} | string>;

export const PENDING = 'PENDIENTE', INFORMED = 'INFORMADO';
const str = (v: unknown) => v == null ? '' : String(v);
const rowsOf = (v: unknown): Row[] => Array.isArray(v) ? v.filter(x => x && typeof x === 'object') as Row[] : [];
const strings = (v: unknown): string[] => Array.isArray(v) ? v.map(String) : [];

/** Listados VIGENTES por catalogo (sin vigente: version null e items vacios). */
export function statLists(catalog: unknown): Record<string, StatList> {
  const lists = rowsOf((catalog as {stat_catalogs?: {catalogs?: unknown}} | undefined)?.stat_catalogs?.catalogs);
  return Object.fromEntries(lists.map(c => {
    const version = c.version && typeof c.version === 'object' ? Number((c.version as Row).id) : null;
    const items = version == null ? [] : rowsOf(c.items).map(i => ({code: str(i.code), label: str(i.label), parent: str(i.parent_code), level: Number(i.level ?? 1),
      selectable: i.selectable !== false, description: str(i.description)}));
    return [str(c.catalog), {catalog: str(c.catalog), label: str(c.label), version, items}];
  }));
}
/** Estados especiales con su rotulo, como los publica el servidor. */
export const specials = (catalog: unknown): Special[] =>
  rowsOf((catalog as {stat_catalogs?: {special?: unknown}} | undefined)?.stat_catalogs?.special).map(s => ({code: str(s.code), label: str(s.label)}));
/** Variables exigibles por tipo de cierre (solo las de catalogo vigente; StatCompleteness.requirements). */
export function completeness(catalog: unknown): Completeness {
  const c = ((catalog as {stat_completeness?: unknown} | undefined)?.stat_completeness ?? {}) as Row;
  return {intake: strings(c.intake), vulnerability: strings(c.vulnerability), duplicate: strings(c.duplicate)};
}

/** Variables de la ficha (characterization.variables) con listado vigente: las unicas que se ofrecen y se envian. */
export function fichaVars(characterization: Row): StatVar[] {
  return rowsOf(characterization.variables).map(v => ({variable: str(v.variable), label: str(v.label), state: str(v.state) || PENDING, code: str(v.code),
    value_label: str(v.value_label), catalog_current: v.catalog_current === true,
    proposal: v.proposal && typeof v.proposal === 'object' ? {code: str((v.proposal as Row).code), label: str((v.proposal as Row).label)} : null}));
}
/**
 * Registro nuevo (ingreso manual, nueva atencion): las variables del ingreso con listado vigente. Pendientes, salvo la que
 * el servidor deriva del canal (D31: un item seleccionable del listado vigente con el mismo codigo que el canal, como en
 * StatValues.CHANNEL_VIA para los canales manuales): esa queda informada y cambiarla es una correccion con motivo.
 */
export function newRecordVars(catalog: unknown, channel = ''): StatVar[] {
  const lists = statLists(catalog);
  return completeness(catalog).intake.filter(v => lists[v]?.version != null).map(v => {
    const derived = channel ? lists[v].items.find(i => i.selectable && i.code === channel) : undefined;
    return {variable: v, label: lists[v].label, state: derived ? INFORMED : PENDING, code: derived?.code ?? '', value_label: derived?.label ?? '', catalog_current: true, proposal: null};
  });
}
/** Variables que el bloque ofrece: solo con listado vigente (sin vigente no se clasifica ni se exige; D28). */
export const offered = (vars: StatVar[]) => vars.filter(v => v.catalog_current);

/** Eleccion en el selector: 'code:X', 'status:Y' o '' (sin cambios). */
export const currentChoice = (v: StatVar) => v.state === INFORMED ? 'code:' + v.code : v.state && v.state !== PENDING ? 'status:' + v.state : '';
export function choiceOf(block: StatBlock | undefined, variable: string): string {
  const entry = block?.[variable];
  if (!entry || typeof entry === 'string') return '';
  return entry.code ? 'code:' + entry.code : entry.status ? 'status:' + entry.status : '';
}
/** Nuevo bloque con la eleccion de una variable ('' la quita); sin elecciones ni motivo, undefined. */
export function setChoice(block: StatBlock | undefined, variable: string, choice: string): StatBlock | undefined {
  const next: StatBlock = {...(block ?? {})};
  delete next[variable];
  if (choice.startsWith('code:')) next[variable] = {code: choice.slice(5)};
  else if (choice.startsWith('status:')) next[variable] = {status: choice.slice(7)};
  return tidy(next);
}
export function setReason(block: StatBlock | undefined, reason: string): StatBlock | undefined {
  const next: StatBlock = {...(block ?? {})};
  if (reason) next.reason = reason; else delete next.reason;
  return tidy(next);
}
const tidy = (b: StatBlock) => Object.keys(b).length ? b : undefined;
export const reasonOf = (block: StatBlock | undefined) => typeof block?.reason === 'string' ? block.reason : '';

/** Variables que cambian respecto de lo registrado (una eleccion igual al valor vigente no se envia). */
export const changed = (vars: StatVar[], block: StatBlock | undefined) =>
  offered(vars).filter(v => {const c = choiceOf(block, v.variable);return c !== '' && c !== currentChoice(v);});
/** Corregir un valor INFORMADO exige motivo (StatValues.apply). */
export const needsReason = (vars: StatVar[], block: StatBlock | undefined) => changed(vars, block).some(v => v.state === INFORMED);

/**
 * Bloque "stat" que se envia: solo variables ofrecidas (listado vigente) que cambian, mas el motivo si lo hay; undefined
 * si no cambia nada. Una variable sin listado vigente nunca viaja (409 en el servidor).
 */
export function statPayload(vars: StatVar[], block: StatBlock | undefined): StatBlock | undefined {
  const out: StatBlock = {};
  for (const v of changed(vars, block)) out[v.variable] = block![v.variable];
  if (!Object.keys(out).length) return undefined;
  const reason = reasonOf(block);
  if (reason.trim()) out.reason = reason;
  return out;
}
/** Problemas antes de enviar: variables exigidas por el acto aun pendientes y motivo de una correccion. */
export function statProblems(vars: StatVar[], block: StatBlock | undefined, required: string[] = []): string[] {
  const out: string[] = [];
  for (const v of offered(vars)) if (required.includes(v.variable) && v.state === PENDING && !choiceOf(block, v.variable)) out.push(`Completa ${v.label.toLocaleLowerCase('es-CL')}.`);
  if (needsReason(vars, block) && !reasonOf(block).trim()) out.push('Indica el motivo de la corrección.');
  return out;
}

/** Rotulo visible del valor de una variable: valor del listado, estado especial o Pendiente; nunca el codigo. */
export function valueText(v: {state: string; value_label: string}, special: Special[]): string {
  if (v.state === INFORMED) return v.value_label || 'Valor registrado con un listado anterior';
  if (!v.state || v.state === PENDING) return 'Pendiente';
  return special.find(s => s.code === v.state)?.label ?? 'Registrado';
}

/**
 * Bloqueos de cierre que el dialogo puede resolver en el mismo acto: las variables exigibles del acto (stat_completeness)
 * que la ficha informa PENDIENTE. El boton se habilita sin bloqueos o si todos los bloqueos del servidor son esos; si el
 * servidor encuentra otro (por ejemplo, derechos de una vulneracion elegida en el dialogo), responde 409 y el dialogo lo
 * muestra.
 */
export function closeState(blockers: string[], required: string[], vars: StatVar[]) {
  const resolvable = offered(vars).filter(v => required.includes(v.variable) && v.state === PENDING);
  return {resolvable, enabled: blockers.length === 0 || resolvable.length > 0 && blockers.length === resolvable.length};
}
