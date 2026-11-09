/** Fichas con detalle propio: #COM/1, #SOL/1, #REQ/1, #CAS/1, #FLUJO/1. */
export const DETAIL_KINDS = ['COM', 'SOL', 'REQ', 'CAS', 'FLUJO'];
/** Bandejas paginadas por el servidor (/api/worklist/{kind}). */
export const BOARDS = ['com', 'sol', 'req', 'cas', 'flujos', 'tareas', 'avisos', 'atenciones'];

/** Ficha que abre una fila de bandeja. Las tareas abren su Caso; las atenciones, su REQ. */
export const BOARD_DETAIL: Record<string, string> = {com: 'COM', sol: 'SOL', req: 'REQ', cas: 'CAS', flujos: 'FLUJO', atenciones: 'REQ', tareas: 'CAS'};

/** "#req?filter=open&q=texto" -> {view:'req', filter:'open', query:'texto'}; "#REQ/3" -> detalle. */
export function parseRoute(route: string) {
  const [path, search] = route.split('?');
  const [kind, id] = path.split('/');
  const params = new URLSearchParams(search ?? '');
  return {view: path, filter: params.get('filter') ?? '', query: params.get('q') ?? '', isDetail: path.includes('/'), kind, id};
}

/** Ruta de bandeja con filtro y busqueda opcionales. */
export function boardRoute(kind: string, filter = '', query = '') {
  const params = new URLSearchParams();
  if (filter) params.set('filter', filter);
  if (query) params.set('q', query);
  const search = params.toString();
  return kind + (search ? '?' + search : '');
}
