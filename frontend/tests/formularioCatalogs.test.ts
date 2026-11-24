// Catalogos de territorio y paises del frontend con la misma huella que los del Formulario Publico (0.8.0, gate E00).
// Las huellas del Formulario (formulario-web 6f886eb) estan en backend/src/test/resources/formulario/PROCEDENCIA.txt;
// FormularioSrceiContractTest compara contra ellas los del servidor. SHA-256 del texto con CRLF pasado a LF: una copia
// de trabajo de Windows con "* text=auto" tiene CRLF y git guarda LF. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const text = (path: string) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8').replace(/\r\n/g, '\n');
const sha256 = (value: string) => createHash('sha256').update(value, 'utf8').digest('hex');

/** Huellas de PROCEDENCIA.txt por nombre: lineas "<sha256>  <nombre>  <origen>". */
function provenance(): Map<string, string> {
  const out = new Map<string, string>();
  for (const line of text('../../backend/src/test/resources/formulario/PROCEDENCIA.txt').split('\n')) {
    const m = /^([0-9a-f]{64}) {2}(\S+) {2}(.+)$/.exec(line);
    if (m) out.set(m[2], m[1]);
  }
  return out;
}

test('catalogos del frontend con la huella de los del Formulario Publico', () => {
  const hashes = provenance();
  for (const name of ['territorio.json', 'paises.json']) {
    assert.match(hashes.get(name) ?? '', /^[0-9a-f]{64}$/, name);
    assert.equal(sha256(text('../src/features/attention/adult/' + name)), hashes.get(name), name);
  }
});
