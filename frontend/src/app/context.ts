import type {Row} from '../lib/rows';
import type {User} from '../lib/access';
import type {Action} from '../components/ActionDialog';

/** Contexto que la aplicacion entrega a cada pantalla. */
export type Context = {
  user: User;
  catalog: Row;
  /** Abre el dialogo de confirmacion de un comando. */
  act: (a: Action) => void;
  /** Navega a una ficha: COM, SOL, REQ, CAS o FLUJO. */
  go: (kind: string, id: number) => void;
  /** Recarga sesion, catalogo y la ficha visible. */
  reload: () => void;
  message: (s: string) => void;
};
