// Descarga de graficos en SVG y PNG (gate E12; DISENO 10.3): el grafico con titulo, subtitulo, notas y pie de fuente,
// sin dependencias. El PNG se dibuja desde el SVG como imagen data: (la CSP admite img-src data:) en un canvas.
import {FONT, MUTED_COLOR, TEXT_COLOR} from './chartModel.ts';

export type Texts = {title: string; subtitle?: string; notes: string[]; source: string};
const escape = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
/** Corta un texto en lineas de a lo mas max caracteres, por palabras. */
export function wrap(text: string, max: number) {
  const lines: string[] = [];
  let line = '';
  for (const word of text.split(/\s+/).filter(Boolean)) {
    if (line && (line + ' ' + word).length > max) {lines.push(line); line = word;}
    else line = line ? line + ' ' + word : word;
  }
  if (line) lines.push(line);
  return lines;
}
/**
 * SVG autonomo: encabezado (titulo y subtitulo), el grafico (su SVG, con colores como atributos) y al pie las notas y la
 * fuente. Devuelve el texto y sus dimensiones.
 */
export function standalone(chart: string, width: number, height: number, t: Texts) {
  const chars = Math.floor((width - 32) / 7.2);
  const head: [string, number, string, number][] = [];
  let y = 30;
  for (const line of wrap(t.title, Math.floor((width - 32) / 9.5))) {head.push([line, 17, TEXT_COLOR, y]); y += 22;}
  for (const line of wrap(t.subtitle ?? '', chars)) {head.push([line, 13, MUTED_COLOR, y]); y += 18;}
  const top = y + 6;
  let foot = top + height + 20;
  const tail: [string, number, string, number][] = [];
  for (const note of t.notes) for (const line of wrap(note, chars)) {tail.push([line, 12, TEXT_COLOR, foot]); foot += 16;}
  for (const line of wrap(t.source, chars)) {tail.push([line, 11, MUTED_COLOR, foot]); foot += 15;}
  const total = foot + 10;
  const text = ([s, size, color, at]: [string, number, string, number]) =>
    `<text x="16" y="${at}" font-family="${FONT}" font-size="${size}" fill="${color}"${size === 17 ? ' font-weight="700"' : ''}>${escape(s)}</text>`;
  const inner = chart.replace(/^<svg\b[^>]*>/, '').replace(/<\/svg>\s*$/, '');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${total}" viewBox="0 0 ${width} ${total}">`
    + `<rect width="${width}" height="${total}" fill="#ffffff"/>${head.map(text).join('')}`
    + `<g transform="translate(0 ${top})">${inner}</g>${tail.map(text).join('')}</svg>`;
  return {svg, width, height: total};
}
function save(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function source(svg: SVGSVGElement) {
  const box = svg.viewBox.baseVal;
  return {markup: new XMLSerializer().serializeToString(svg), width: box?.width || svg.clientWidth || 720, height: box?.height || svg.clientHeight || 360};
}
export function downloadSvg(svg: SVGSVGElement, t: Texts, name: string) {
  const s = source(svg);
  save(new Blob([standalone(s.markup, s.width, s.height, t).svg], {type: 'image/svg+xml'}), name + '.svg');
}
export function downloadPng(svg: SVGSVGElement, t: Texts, name: string): Promise<void> {
  const s = source(svg);
  const out = standalone(s.markup, s.width, s.height, t);
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const ratio = 2;
      const canvas = document.createElement('canvas');
      canvas.width = out.width * ratio;
      canvas.height = out.height * ratio;
      const g = canvas.getContext('2d');
      if (!g) {reject(new Error('El navegador no permite generar la imagen.')); return;}
      g.scale(ratio, ratio);
      g.drawImage(img, 0, 0);
      canvas.toBlob(blob => {if (blob) {save(blob, name + '.png'); resolve();} else reject(new Error('No se pudo generar la imagen.'));}, 'image/png');
    };
    img.onerror = () => reject(new Error('No se pudo generar la imagen.'));
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(out.svg);
  });
}
