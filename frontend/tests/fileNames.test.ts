// Nombres de archivo en un sistema que no distingue mayusculas (H06a): en Windows `import ... from './Calendar'`
// encontraba `calendar.ts` antes que `Calendar.tsx`. Dentro de una misma carpeta de src y de tests, dos entradas no
// pueden diferir solo en mayusculas ni solo en la extension. node --test, sin dependencias.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync} from 'node:fs';
import {join, relative} from 'node:path';
import {fileURLToPath} from 'node:url';

/**
 * Clave de una entrada en su carpeta, en minusculas: un archivo sin su ultima extension, una carpeta con su nombre
 * completo. Archivos y carpetas se comparan por separado (`App.tsx` junto a la carpeta `app` no se marca).
 */
function keyOf(name: string, directory: boolean): string {
  return (directory ? 'carpeta:' + name : 'archivo:' + name.replace(/\.[^.]+$/, '')).toLowerCase();
}

/** Pares de nombres de la misma carpeta que chocan en Windows. */
function clashes(entries: {name: string, directory: boolean}[]): string[][] {
  const seen = new Map<string, string[]>();
  for (const e of entries) {
    const key = keyOf(e.name, e.directory);
    seen.set(key, [...(seen.get(key) ?? []), e.name]);
  }
  return [...seen.values()].filter(names => names.length > 1);
}

function walk(root: string, dir: string = root): string[] {
  const entries = readdirSync(dir, {withFileTypes: true});
  const here = clashes(entries.map(e => ({name: e.name, directory: e.isDirectory()})))
    .map(names => (relative(root, dir) || '.') + ': ' + names.join(', '));
  return [...here, ...entries.filter(e => e.isDirectory()).flatMap(e => walk(root, join(dir, e.name)))];
}

test('ningun archivo de src ni de tests difiere de otro solo en mayusculas o en la extension', () => {
  assert.deepEqual(clashes([{name: 'Calendar.tsx', directory: false}, {name: 'calendar.ts', directory: false}]), [['Calendar.tsx', 'calendar.ts']]);
  assert.deepEqual(clashes([{name: 'Admin', directory: true}, {name: 'admin', directory: true}]), [['Admin', 'admin']]);
  assert.deepEqual(clashes([{name: 'Calendar.tsx', directory: false}, {name: 'calendarModel.ts', directory: false}]), []);
  assert.deepEqual(clashes([{name: 'search.test.ts', directory: false}, {name: 'search.ts', directory: false}]), []);
  assert.deepEqual(clashes([{name: 'App.tsx', directory: false}, {name: 'app', directory: true}]), []);
  for (const folder of ['../src', '../tests']) {
    const root = fileURLToPath(new URL(folder, import.meta.url));
    assert.ok(readdirSync(root).length > 5, folder);
    assert.deepEqual(walk(root), [], folder);
  }
});
