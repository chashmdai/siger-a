// Canales del registro manual y de la atencion directa (0.8.0, D31, gate E05): la lista del frontend es la del servidor
// (Lifecycle.CHANNELS, leida del codigo) y cada canal, incluidos los del Formulario, tiene su rotulo en lenguaje llano.
// node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {CHANNEL_LABELS} from '../src/lib/labels.ts';

const text = (path: string) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8');
// fields.ts importa modulos sin extension (no se carga con node --test): su lista se lee del codigo, como la del servidor.
const codes = (source: string) => [...source.matchAll(/["']([A-Z_]+)["']/g)].map(x => x[1]);
const fields = /export const CHANNELS = \[([^\]]*)\]/.exec(text('../src/lib/fields.ts'));
const CHANNELS = fields ? codes(fields[1]) : [];

test('canales del frontend iguales a Lifecycle.CHANNELS del servidor', () => {
  const m = /static final String\[\] CHANNELS=\{([^}]*)\}/.exec(text('../../backend/src/main/java/cl/nexbyte/sigera/Lifecycle.java'));
  assert.ok(m, 'Lifecycle.CHANNELS');
  assert.ok(fields, 'CHANNELS de fields.ts');
  assert.deepEqual(CHANNELS, codes(m[1]));
  assert.ok(CHANNELS.includes('REDES_SOCIALES') && CHANNELS.includes('INTEGRANTE_DEFENSORIA'));
});

test('cada canal tiene un rotulo sin codigos', () => {
  for (const channel of [...CHANNELS, 'FORMULARIO_WEB_PUBLICO', 'FORMULARIO_WEB_NNA']) {
    const label = CHANNEL_LABELS[channel];
    assert.ok(label, channel);
    assert.doesNotMatch(label, /_/, channel);
  }
  assert.equal(CHANNEL_LABELS.REDES_SOCIALES, 'Redes sociales');
  assert.equal(CHANNEL_LABELS.INTEGRANTE_DEFENSORIA, 'Integrante de la Defensoría');
});
