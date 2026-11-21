import {readFileSync} from 'node:fs';
import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
// Version visible = la de package.json (Build.ps1 exige que coincida con backend/pom.xml): una sola fuente.
const version = (JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as {version: string}).version;
export default defineConfig({
  plugins: [react()],
  define: {__SIGER_VERSION__: JSON.stringify(version)},
  // React en un chunk propio que index.html carga al inicio (modulepreload), sin carga diferida: ninguna pantalla
  // depende de pedir un archivo despues, y la aplicacion queda bajo el aviso de 500 kB de Vite.
  build: {rolldownOptions: {output: {codeSplitting: {groups: [{name: 'react', test: /node_modules[\\/](react|react-dom|scheduler)[\\/]/}]}}}},
  server: {host: '127.0.0.1', port: 5173, strictPort: true, proxy: {'/api': {target: 'http://127.0.0.1:18543', changeOrigin: false}}}
});
