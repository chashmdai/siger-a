// Iconos de la aplicacion (0.8.1, gate F01, D54): todos los nombres de components/Icon.tsx se dibujan con lucide-react como un
// <svg> decorativo (aria-hidden, sin foco); lucide-react se importa solo en Icon.tsx, por nombre, y con version exacta.
import {test, before, after} from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';

const root = fileURLToPath(new URL('..', import.meta.url));
let vite, icon;
before(async () => {
  vite = await createServer({root, server: {middlewareMode: true, hmr: false}, appType: 'custom', logLevel: 'error'});
  icon = await vite.ssrLoadModule('/src/components/Icon.tsx');
});
after(async () => {await vite?.close();});

test('cada IconName se dibuja como <svg aria-hidden="true" focusable="false">, con su tamaño y clase', () => {
  assert.ok(icon.ICON_NAMES.length >= 49 + 30, 'los 49 de 0.8.0 y los nuevos del explorador');
  for (const old of ['home', 'attention', 'people', 'inbox', 'alert', 'history', 'chart', 'sparkle', 'handoff', 'layers']) assert.ok(icon.ICON_NAMES.includes(old), old);
  for (const name of icon.ICON_NAMES) {
    const html = renderToStaticMarkup(createElement(icon.Icon, {name, size: 18, className: 'x'}));
    assert.match(html, /^<svg\b[^>]*>[\s\S]*<\/svg>$/, name);
    assert.match(html, /\saria-hidden="true"/, name + ': decorativo');
    assert.match(html, /\sfocusable="false"/, name + ': sin foco');
    assert.match(html, /\swidth="18"[^>]*\sheight="18"/, name + ': tamaño');
    assert.match(html, /\sstroke-width="1.8"/, name + ': trazo uniforme');
    assert.match(html, /class="[^"]*\bx\b/, name + ': clase');
    assert.match(html, /<(path|circle|rect|line|polyline|polygon|ellipse)\b/, name + ': con trazo');
  }
});

function files(dir) {
  return readdirSync(dir).flatMap(f => {const p = join(dir, f); return statSync(p).isDirectory() ? files(p) : [p];});
}
test('lucide-react solo se importa en components/Icon.tsx, por nombre, y con versión exacta en package.json', () => {
  const src = join(root, 'src');
  const users = files(src).filter(f => /\.(tsx?|mjs|js)$/.test(f) && /from\s+['"]lucide-react/.test(readFileSync(f, 'utf8')));
  assert.deepEqual(users.map(f => f.slice(src.length + 1).replace(/\\/g, '/')), ['components/Icon.tsx']);
  const own = readFileSync(join(src, 'components', 'Icon.tsx'), 'utf8');
  assert.ok(!/import\s+\*\s+as/.test(own) && !/lucide-react\/dynamic|DynamicIcon|icons\[/.test(own), 'sin importación global ni iconos dinámicos');
  const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
  assert.equal(pkg.dependencies['lucide-react'], '1.71.0', 'versión exacta, sin ^ ni ~');
  assert.deepEqual(Object.keys(pkg.dependencies).sort(), ['lucide-react', 'react', 'react-dom'], 'ninguna otra dependencia nueva');
  const lock = JSON.parse(readFileSync(join(root, 'package-lock.json'), 'utf8'));
  assert.equal(lock.packages['node_modules/lucide-react'].version, '1.71.0');
  assert.equal(lock.packages['node_modules/lucide-react'].license, 'ISC');
});
