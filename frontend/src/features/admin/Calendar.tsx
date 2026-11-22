import {useEffect, useState} from 'react';
import {request} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {list, text} from '../../lib/rows';
import {reason} from '../../lib/fields';
import type {Context} from '../../app/context';
import {Table} from '../../components/Table';
import {Icon} from '../../components/Icon';
import {ChipRadios} from '../../components/Choice';
import {Callout, ErrorMessage, PageHeader, Panel, Skeleton} from '../../components/Primitives';
import type {Holiday} from './calendarModel';
import {displayDay, holidaysOf, inYear, missingYears, onWeekend, scopeLabel, weekdayLabel, yearOf, yearsOf, yearSummary} from './calendarModel';

/**
 * Calendario habil (G09, solo TI): feriados nacionales y regionales que descuentan los dias habiles de los reportes.
 * Agregar uno, importar varios (todo o nada) y retirar, siempre con fundamento. El servidor valida y audita.
 */
export default function Calendar({ctx}: {ctx: Context}) {
  const [data, setData] = useState<Row | null>(null), [error, setError] = useState('');
  const [year, setYear] = useState<number | null>(null);
  useEffect(() => {
    let live = true;
    void request('/calendar').then(r => {if (live) setData(r);}).catch(e => {if (live) setError(e.message);});
    return () => {live = false;};
  }, []);
  const regions = list(ctx.catalog, 'regions').map(r => ({code: text(r, 'code'), name: text(r, 'name')}));

  if (error) return <ErrorMessage>{error}</ErrorMessage>;
  if (!data) return <div aria-busy="true" aria-label="Cargando calendario"><Skeleton title lines={4}/></div>;
  const today = text(data, 'today');
  const holidays = holidaysOf(list(data, 'holidays'));
  const years = yearsOf(holidays, today);
  const shown = year ?? yearOf(today);
  const rows = inYear(holidays, shown);

  const add = () => ctx.act({title: 'Agregar feriado', type: 'HOLIDAY_ADD', id: null, revision: null, fields: [
    {key: 'day', label: 'Fecha', type: 'date', required: true},
    {key: 'name', label: 'Nombre del feriado', required: true, max: 120},
    {key: 'region_code', label: 'Alcance', type: 'select', value: '', options: [{value: '', label: 'Nacional (todo el país)'}, ...regions.map(r => ({value: r.code, label: 'Región ' + r.name}))],
      help: 'Un feriado regional solo descuenta días hábiles en los REQ y Casos de esa región.'},
    reason,
  ], note: 'El día deja de contar como hábil en los reportes. Un feriado en sábado o domingo se registra, pero no altera el cómputo.'});
  const importMany = () => ctx.act({title: 'Importar feriados', type: 'HOLIDAY_IMPORT', id: null, revision: null, fields: [
    {key: 'lines', label: 'Feriados, uno por línea', type: 'textarea', required: true, max: 30000,
      help: 'Formato aaaa-mm-dd y el nombre; si es regional, el código de la región al final entre corchetes. Ejemplo: 2027-06-07 Asalto y Toma del Morro de Arica [15]. Las líneas vacías y las que empiezan con # se omiten.'},
    reason,
  ], note: 'Se revisan todas las líneas antes de grabar: si una tiene un problema o choca con un feriado registrado, no se registra ninguna y se indica la línea. Cada feriado queda en la auditoría con este fundamento.'});
  const remove = (h: Holiday) => ctx.act({title: 'Retirar feriado del ' + displayDay(h.day), type: 'HOLIDAY_REMOVE', id: h.id, revision: null, fields: [reason],
    note: `${h.name} (${scopeLabel(h.region, regions).toLocaleLowerCase('es-CL')}). El día vuelve a contar como hábil en los reportes. La historia queda en la auditoría.`});

  return <>
    <PageHeader eyebrow={<><Icon name="calendar" size={14}/>Administración</>} title="Calendario hábil"
      lead="Feriados que descuentan días hábiles en los reportes (lunes a viernes, hora de Chile). Los nacionales rigen para todos; los regionales, solo para los REQ y Casos de su región."
      aside={<>
        <button className="primary" onClick={add}><Icon name="plus" size={18}/>Agregar feriado</button>
        <button onClick={importMany}><Icon name="upload" size={18}/>Importar feriados</button>
      </>}/>
    {missingYears(holidays, today).map(y => <Callout key={y} tone="warn" role="status" title={'Sin feriados cargados para ' + y}>
      No hay feriados nacionales registrados para {y}: los días hábiles de ese año se calcularían solo con los fines de semana. Cárgalos con Agregar o Importar.
    </Callout>)}
    <Panel title={'Feriados ' + shown} icon="calendar" count={rows.length} description={yearSummary(rows)}>
      <ChipRadios legend="Año" value={String(shown)} options={years.map(y => ({value: String(y), label: String(y)}))} onChange={v => setYear(Number(v))}/>
      <Table rows={rows as unknown as Row[]} empty={'No hay feriados registrados para ' + shown + '.'} emptyIcon="calendar" columns={[
        {name: 'Fecha', render: r => <span className="nowrap tabular">{displayDay(text(r, 'day'))}</span>},
        {name: 'Día', render: r => onWeekend(text(r, 'day'))
          ? <span>{weekdayLabel(text(r, 'day'))} <span className="chip">Fin de semana: no altera el cómputo</span></span>
          : weekdayLabel(text(r, 'day'))},
        {name: 'Feriado', render: r => <span className="cell-strong">{text(r, 'name')}</span>},
        {name: 'Alcance', render: r => r.region == null ? <span className="chip chip-ok">Nacional</span> : <span className="chip">{scopeLabel(text(r, 'region'), regions)}</span>},
        {name: 'Acción', render: r => <button className="sm" onClick={() => remove(r as unknown as Holiday)}>Retirar</button>},
      ]}/>
    </Panel>
  </>;
}
