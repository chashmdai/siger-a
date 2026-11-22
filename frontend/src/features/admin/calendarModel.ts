// Calendario habil (G09): logica pura de la pantalla de feriados de TI. El servidor valida y calcula; aqui solo se
// ordena, agrupa por anio y se decide que avisos mostrar.
import type {Row} from '../../lib/rows';

export type Holiday = {id: number; day: string; region: string | null; name: string};
export type Region = {code: string; name: string};

const WEEKDAYS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

/** Filas de /api/calendar (holiday_day, region_code), ordenadas por fecha y con los nacionales primero. */
export function holidaysOf(rows: Row[]): Holiday[] {
  return rows.map(r => ({id: Number(r.id), day: String(r.holiday_day ?? ''), region: r.region_code == null || r.region_code === '' ? null : String(r.region_code), name: String(r.name ?? '')}))
    .sort((a, b) => a.day.localeCompare(b.day) || (a.region ?? '').localeCompare(b.region ?? ''));
}

export const yearOf = (day: string) => Number(day.slice(0, 4));

/** Dia de la semana de una fecha aaaa-mm-dd (calendario, sin zona horaria). */
export function weekdayOf(day: string) {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay();
}
export const weekdayLabel = (day: string) => WEEKDAYS[weekdayOf(day)];
/** Un feriado en sabado o domingo no altera el computo de dias habiles. */
export const onWeekend = (day: string) => [0, 6].includes(weekdayOf(day));
/** aaaa-mm-dd -> dd-mm-aaaa. */
export const displayDay = (day: string) => day.split('-').reverse().join('-');

/** Anios que ofrece el selector: los que tienen feriados, mas el actual y el siguiente; de menor a mayor. */
export function yearsOf(holidays: Holiday[], today: string): number[] {
  const current = yearOf(today);
  return [...new Set([...holidays.map(h => yearOf(h.day)), current, current + 1])].sort((a, b) => a - b);
}

export const inYear = (holidays: Holiday[], year: number) => holidays.filter(h => yearOf(h.day) === year);

/**
 * Anios actual y siguiente sin feriados nacionales cargados: los reportes calcularian sus dias habiles solo con los
 * fines de semana (el servidor lo advierte igual con BusinessCalendar.missingYears).
 */
export function missingYears(holidays: Holiday[], today: string): number[] {
  const current = yearOf(today);
  return [current, current + 1].filter(y => !holidays.some(h => h.region == null && yearOf(h.day) === y));
}

/** Alcance visible: "Nacional" o el nombre de la region del catalogo (con su codigo si no se conoce). */
export function scopeLabel(region: string | null, regions: Region[]) {
  if (region == null) return 'Nacional';
  return 'Región ' + (regions.find(r => r.code === region)?.name ?? region);
}

/** Resumen de un anio: cantidad de nacionales y regionales, en texto. */
export function yearSummary(holidays: Holiday[]) {
  const national = holidays.filter(h => h.region == null).length, regional = holidays.length - national;
  if (holidays.length === 0) return 'Sin feriados registrados';
  const part = (n: number, one: string, many: string) => n + ' ' + (n === 1 ? one : many);
  return [national > 0 ? part(national, 'nacional', 'nacionales') : '', regional > 0 ? part(regional, 'regional', 'regionales') : ''].filter(Boolean).join(' y ');
}
