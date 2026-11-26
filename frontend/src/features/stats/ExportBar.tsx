import {useState} from 'react';
import type {Context} from '../../app/context';
import {Icon} from '../../components/Icon';
import {ErrorMessage} from '../../components/Primitives';
import {downloadPost} from '../../lib/api';
import type {Row} from '../../lib/rows';
import {filenameOf} from '../reports/reportsModel';
import type {Query} from './statsModel';
import {encodeRoute, errorText, fileStem, zipProblem} from './statsModel';
import {queue} from './statQuery';

export const EXPORT_NEEDS = 'Para descargar estas cifras en CSV, XLSX o el paquete ZIP necesitas la función Exportación.';
export const ZIP_HELP = 'El paquete ZIP trae el agregado, los microdatos (un folio por requerimiento, sin datos de personas), el diccionario, los catálogos y sus huellas. Se genera uno a la vez en SIGER.';

/**
 * Descargas de una vista (E13; DISENO 10.3): CSV y XLSX del agregado y el paquete ZIP, con la funcion Exportacion y en la
 * misma fila de consultas de la pestana. El ZIP se deshabilita con su explicacion si la medida no es de requerimientos; los
 * demas rechazos (limite de folios 409, grupos prioritarios 403, otro ZIP en curso 429, corte anterior a V24 409) se
 * muestran con el mensaje del servidor.
 */
export function ExportBar({ctx, q, body, title}: {ctx: Context; q: Query; body: Row; title: string}) {
  const [busy, setBusy] = useState(''), [error, setError] = useState('');
  if (ctx.user.can_export !== true) return <p className="field-help">{EXPORT_NEEDS}</p>;
  const zip = zipProblem(q);
  const download = (format: 'csv' | 'xlsx' | 'zip') => {
    setBusy(format);
    setError('');
    queue.run(() => downloadPost('/stats/export/' + format, body, fileStem(title) + '.' + format, filenameOf))
      .then(() => ctx.message(format === 'zip' ? 'Paquete ZIP generado.' : 'Archivo generado.'), (e: Error & {status?: number}) => setError(errorText(e))).finally(() => setBusy(''));
  };
  const button = (format: 'csv' | 'xlsx' | 'zip', label: string, disabled = false) => <button type="button" className="sm" disabled={busy !== '' || disabled}
    aria-describedby={format === 'zip' ? 'zip-ayuda' : undefined} onClick={() => download(format)}><Icon name="download" size={16}/>{busy === format ? 'Generando…' : label}</button>;
  return <div className="stat-export" role="group" aria-label="Descargar estas cifras">
    <div className="actions">{button('csv', 'CSV')}{button('xlsx', 'XLSX')}{button('zip', 'Paquete ZIP', zip !== '')}</div>
    <p id="zip-ayuda" className="field-help">{zip || ZIP_HELP}</p>
    {error && <ErrorMessage>{error}</ErrorMessage>}
  </div>;
}

/**
 * "Congelar estas cifras" (E13; DISENO 10.4): abre el asistente de corte prellenado con la consulta. Solo con Gestion
 * estadistica (can_manage_stats lo decide el servidor) y nunca sobre un corte.
 */
export function FreezeButton({ctx, q}: {ctx: Context; q: Query}) {
  if (ctx.user.can_manage_stats !== true || q.cut) return null;
  return <button type="button" className="sm" onClick={() => {location.hash = encodeRoute('cortes', q, {nuevo: '1'});}}><Icon name="layers" size={16}/>Congelar estas cifras</button>;
}
