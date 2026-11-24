// Ficha de primera atencion 1.2 (0.8.0, D33, gate E04; DISENO 3.4): preparacion del borrador, validacion y datos que se
// envian, con las reglas del servidor (Attention.version12, DeclaredPerson). Las versiones guardadas no cambian: solo el
// borrador en pantalla pasa a 1.2. En modalidad NNA rigen las reglas de la persona adulta; el texto libre de una ficha
// NNA 1.0/1.1 (region, comuna, telefono, nacionalidad, documento) se conserva como registrado antes y nunca se convierte.
import type {AttentionData} from './schema';
import {emptyForm, FICHA_VERSION} from './schema.ts';
import {presenterProblems, upgrade} from './adult/adultForm.ts';
import type {Problems, StatCodes} from '../../lib/person/personModel.ts';
import {canonicalInstitutions, canonicalList, factProblems, institutionProblems, NO_CODES, personPayload, personProblems, prefixed, selectableCodes} from '../../lib/person/personModel.ts';

type Form = Record<string, unknown>;
const str = (form: Form, key: string) => String(form[key] ?? '');

/**
 * Borrador 1.2 desde una ficha guardada (o vacia). 1.0 recibe primero las conversiones equivalentes de 1.1 (solo modalidad
 * GENERAL: adult/adultForm.ts upgrade); las personas pasan a persona-1 con su texto libre como historico (sin dividir
 * nombres); se quitan las versiones de catalogo que deriva el servidor.
 */
export function prepareFicha(saved: Form): AttentionData {
  const version = str(saved, 'schema_version') || '1.0';
  const merged: Form = {...emptyForm(), ...saved, schema_version: version};
  const base = version === '1.0' ? upgrade(merged) : merged;
  const out: Form = {...base, schema_version: FICHA_VERSION};
  delete out.relationship_version;
  for (const key of ['relationship_code', 'facts_region_code', 'facts_commune', 'facts_place']) out[key] = str(out, key);
  out.people = canonicalList(saved.people ?? []);
  out.institutions_list = canonicalInstitutions(saved.institutions_list ?? []);
  return out as AttentionData;
}

/** Problemas de la ficha 1.2, por ruta (presenter_*, people.0.first_name, institutions_list.1.name, facts_*). */
export function fichaProblems(form: AttentionData, saved: Form = {}, codes: StatCodes = NO_CODES): Problems {
  const previous = saved as Form;
  const errors: Problems = {...presenterProblems(form, previous), ...factProblems(form, previous)};
  const code = str(form, 'relationship_code');
  if (code && code !== str(previous, 'relationship_code') && !selectableCodes(codes.relationship).some(i => i.code === code)) errors.relationship_code = 'Selecciona una opción del listado vigente.';
  const count = str(form, 'approximate_count').trim();
  if (count && count !== str(previous, 'approximate_count') && !/^[1-9][0-9]{0,5}$/.test(count)) errors.approximate_count = 'Escribe un número entero mayor que cero.';
  const people = canonicalList(previous.people ?? []);
  form.people.forEach((p, i) => Object.assign(errors, prefixed(`people.${i}`, personProblems(p, people, codes))));
  const institutions = canonicalInstitutions(previous.institutions_list ?? []);
  form.institutions_list.forEach((x, i) => Object.assign(errors, prefixed(`institutions_list.${i}`, institutionProblems(x, institutions, codes))));
  return errors;
}

/** Paso de la ficha donde esta el campo (hechos e instituciones en Antecedentes; el resto en Primer abordaje). */
export const problemStep = (key: string) => key.startsWith('facts_') || key.startsWith('institutions_list') ? 2 : 1;

/** Datos que se envian: version 1.2, solo los campos de cada lista y sin las versiones de catalogo derivadas. */
export function fichaPayload(form: AttentionData): Form {
  const out: Form = {...form, schema_version: FICHA_VERSION};
  delete out.relationship_version;
  out.people = form.people.map(personPayload);
  out.institutions_list = canonicalInstitutions(form.institutions_list);
  return out;
}
