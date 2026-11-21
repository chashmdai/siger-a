/** Texto comparable para buscadores: minusculas, sin tildes ni signos, espacios simples. */
export const fold = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

/** Cada palabra de la busqueda aparece en el texto (sin tildes ni mayusculas). Una busqueda vacia coincide siempre. */
export const matchesWords = (haystack: string, query: string) => {
  const target = fold(haystack);
  return fold(query).split(' ').filter(Boolean).every(w => target.includes(w));
};
