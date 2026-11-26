import {useEffect} from 'react';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {Callout, EmptyState, PageHeader} from '../../components/Primitives';
import type {Section} from './statsModel';
import {decodeRoute, encodeRoute, scopeLabel, statsCatalog} from './statsModel';
import {queue} from './statQuery';
import Panorama from './Panorama';
import Informes from './Informes';
import Explore from './Explore';
import CutsSeries from './CutsSeries';
import Quality from './Quality';

const TABS: {key: Section; label: string; hint: string}[] = [
  {key: 'panorama', label: 'Panorama', hint: 'Indicadores principales y su variación'},
  {key: 'informes', label: 'Informes', hint: 'Figura 1 de la Cuenta Pública'},
  {key: 'explorar', label: 'Explorar', hint: 'Tablas, cruces y gráficos a tu medida'},
  {key: 'cortes', label: 'Cortes y series', hint: 'Cifras congeladas y series de informes anteriores'},
  {key: 'calidad', label: 'Calidad', hint: 'Completitud de la clasificación y registros pendientes'},
];

/**
 * Explorador estadistico (0.8.0, D38, gates E12 y E13; DISENO 10.3 y 10.4): Panorama, Informes, Explorar, Cortes y series y
 * Calidad sobre POST /api/stats/query,
 * con el alcance de la funcion Reportes de la cuenta (lo aplica el servidor). La consulta vive en la direccion
 * (#estadisticas?...) para volver atras y compartirla.
 */
export default function Stats({ctx, route}: {ctx: Context; route: string}) {
  const cat = statsCatalog(ctx.catalog);
  const {section, query, params} = decodeRoute(route);
  // Al cambiar de seccion se cancelan las consultas que esperan su turno; la que esta en curso termina y queda en la cache.
  useEffect(() => () => queue.cancelPending(), [section]);
  const scope = scopeLabel(ctx.user, ctx.catalog);
  const go = (s: Section) => {location.hash = encodeRoute(s);};
  let content;
  if (!cat) content = <EmptyState icon="chart" title="El explorador no está disponible para tu cuenta">Requiere la función Reportes vigente.</EmptyState>;
  else if (!cat.enabled) content = <Callout tone="warn" title="El explorador estadístico está deshabilitado">La configuración técnica de la base de datos no permite calcular cifras en este momento. Avisa al equipo técnico; el resto de SIGER funciona con normalidad.</Callout>;
  else if (section === 'panorama') content = <Panorama ctx={ctx} cat={cat} scope={scope} params={params}/>;
  else if (section === 'informes') content = <Informes ctx={ctx} cat={cat} scope={scope} params={params}/>;
  else if (section === 'cortes') content = <CutsSeries ctx={ctx} cat={cat} scope={scope} query={query} params={params}/>;
  else if (section === 'calidad') content = <Quality ctx={ctx} cat={cat} scope={scope} params={params}/>;
  else content = <Explore ctx={ctx} cat={cat} scope={scope} query={query}/>;
  return <>
    <PageHeader eyebrow={<><Icon name="chart" size={14}/>Reportes</>} title="Explorador estadístico"
      lead="Cifras exactas de los requerimientos de tu alcance, sin datos de personas. Cada cifra trae cómo leerla."/>
    <nav className="stat-tabs" aria-label="Secciones del explorador">
      {TABS.map(t => <button key={t.key} type="button" className={'stat-tab' + (t.key === section ? ' on' : '')} aria-current={t.key === section ? 'page' : undefined}
        title={t.hint} onClick={() => go(t.key)}>{t.label}</button>)}
    </nav>
    {content}
  </>;
}
