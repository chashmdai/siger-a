import {useEffect, useRef} from 'react';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import type {IconName} from '../../components/Icon';
import {Callout, EmptyState, PageHeader} from '../../components/Primitives';
import type {Section} from './statsModel';
import {decodeRoute, encodeRoute, scopeLabel, statsCatalog, tabScroll} from './statsModel';
import {queue} from './statQuery';
import Panorama from './Panorama';
import Informes from './Informes';
import Explore from './Explore';
import CutsSeries from './CutsSeries';
import Quality from './Quality';

const TABS: {key: Section; label: string; hint: string; icon: IconName}[] = [
  {key: 'panorama', label: 'Panorama', hint: 'Indicadores principales y su variación', icon: 'dashboard'},
  {key: 'informes', label: 'Informes', hint: 'Figura 1 de la Cuenta Pública', icon: 'fileChart'},
  {key: 'explorar', label: 'Explorar', hint: 'Tablas, cruces y gráficos a tu medida', icon: 'chartCombined'},
  {key: 'cortes', label: 'Cortes y series', hint: 'Cifras congeladas y series de informes anteriores', icon: 'layers'},
  {key: 'calidad', label: 'Calidad', hint: 'Completitud de la clasificación y registros pendientes', icon: 'shield'},
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
  // En movil las pestanas se desplazan en una fila: la activa se lleva a la vista moviendo solo el contenedor (scrollLeft),
  // sin desplazar la pagina en vertical; con movimiento reducido, sin animacion (ajuste 5 del GO de F01).
  const nav = useRef<HTMLElement>(null);
  useEffect(() => {
    const el = nav.current, on = el?.querySelector<HTMLElement>('[aria-current=page]');
    if (!el || !on) return;
    const a = el.getBoundingClientRect(), b = on.getBoundingClientRect();
    const left = tabScroll({left: a.left, right: a.right, scroll: el.scrollLeft}, {left: b.left, right: b.right});
    if (left == null) return;
    const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    el.scrollTo({left, behavior: reduced ? 'auto' : 'smooth'});
  }, [section]);
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
    <nav ref={nav} className="stat-tabs" aria-label="Secciones del explorador">
      {TABS.map(t => <button key={t.key} type="button" className={'stat-tab' + (t.key === section ? ' on' : '')} aria-current={t.key === section ? 'page' : undefined}
        title={t.hint} onClick={() => go(t.key)}><Icon name={t.icon} size={18}/>{t.label}</button>)}
    </nav>
    {content}
  </>;
}
