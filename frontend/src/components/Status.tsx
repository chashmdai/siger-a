import {humanize, KIND_LABELS, KIND_SHORT} from '../lib/labels';

const ATTENTION = ['ALTA', 'EN_REVISION', 'PENDIENTE', 'DEVUELTA', 'PENDIENTE_RECEPCION', 'REQUIERE_CASO', 'NUEVA', 'ENVIADA'];
const OK = ['COMPLETADA', 'BAJA', 'ADMITIDA', 'ACEPTADA', 'RESUELTA', 'RECIBIDA', 'ACTIVA', 'ASUMIDA'];
const INFO = ['EN_GESTION', 'EN_ATENCION', 'EN_CURSO', 'ABIERTO', 'CON_CASO', 'MEDIANA', 'PROGRAMADA', 'EN_ESPERA', 'VINCULADA'];
const CLOSED = ['CERRADO', 'CANCELADA', 'RETIRADA', 'TERMINADA', 'EXPIRADA', 'DESCARTADA', 'ATENDIDA'];
const DANGER = ['RECHAZADA', 'INVALIDA', 'DUPLICADA'];

/** Estado legible. El tono solo refuerza el texto, que siempre esta visible. */
export function Status({value}: {value: unknown}) {
  const v = value == null || value === '' ? 'Sin clasificar' : String(value);
  const tone = ATTENTION.includes(v) ? 'attention' : OK.includes(v) ? 'ok' : INFO.includes(v) ? 'info' : CLOSED.includes(v) ? 'closed' : DANGER.includes(v) ? 'danger' : '';
  return <span className={'status' + (tone ? ' status-' + tone : '')}>{humanize(v)}</span>;
}

/** Tipo de registro con nombre completo para lectores de pantalla. */
export function Kind({kind}: {kind: string}) {
  return <span className={'kind kind-' + kind} title={KIND_LABELS[kind]}>
    <span aria-hidden="true">{KIND_SHORT[kind] ?? kind}</span><span className="sr-only">{KIND_LABELS[kind] ?? kind}</span>
  </span>;
}
