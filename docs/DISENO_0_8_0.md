# SIGER 0.8.0 - diseno, auditoria complementaria y modelo de informacion

Documento de referencia de `docs/PLAN_0_8_0.md` (decisiones D25-D51). Lo redacto el asistente del responsable ("Claude
Padre") el 2026-11-24 sobre `chashmdai/siger-a` en `3938e4e` (`v0.7.0`) y, solo en lectura, `chashmdai/formulario-web`
en `6f886eb`, y lo ajusto con la aprobacion del plan del mismo dia (DI-05 a DI-10, GESTION_ESTADISTICA y seis
correcciones) y con la reconsideracion del alcance: 0.8.0 se concentra en normalizacion integral e inteligencia de
negocios para Estudios, y los pendientes operacionales quedan como hoja de ruta 0.9.0 (secciones 3.6, 3.8 y 15), sin
gates en 0.8.0. Este documento no autoriza implementar: cada gate toma de aqui solo la seccion que su fila del plan
indica. Si el codigo contradice este documento, prevalece el codigo verificado y la sesion lo registra como diferencia
de referencia (o se detiene si cambia una decision, el alcance o una condicion).

Indice: 1 Auditoria complementaria, 2 Matriz de normalizacion, 3 Modelo de informacion, 4 Reglas unicas, 5 Motor
estadistico, 6 Cortes, 7 Series historicas, 8 Exportaciones, 9 Facultades y auditoria, 10 Interfaces y facilidad
de uso, 11 Catalogo de derechos de referencia, 12 Dependencias, 13 Estrategia de pruebas, 14 Migraciones y
actualizacion, 15 Hoja de ruta 0.9.0, 16 Riesgos, 17 Definiciones pendientes.

---

## 1. Auditoria complementaria

### 1.1 Base verificada

- SIGER: `main` = `v0.7.0` = `3938e4e`, esquema 16, 194 metodos `@Test` (191 en H2 + 3 solo MySQL), 112 pruebas de
  frontend, 40 scripts, `SOURCE_SHA256.txt` coherente con el indice, `git diff v0.6.0 -- scripts/ .mvn/` vacio. No
  existe `AGENTS.md`.
- Formulario Publico: `main` en `6f886eb` (2026-11-15). Revisado solo en lectura: validaciones Java
  (`formulario-publico/backend/src/main/java/cl/nexbyte/siger/formulario/`), modelo de identificacion
  (`IdentificacionCiudadana.java`), frontend (`formulario-publico/front/src/formulario/`) y contratos (`contratos/`).

### 1.2 Hallazgos en SIGER (codigo real)

| Id | Hallazgo | Evidencia | Consecuencia para 0.8.0 |
|---|---|---|---|
| S1 | Los comandos de Caso y de REQ exigen el registro abierto (`life.openCase`, `life.openReq`); la ficha solo se edita abierta y sin Caso | `CaseCommands.java:51-52`, `RequirementCommands.java:38`, `Access.attentionWritable` | La inmutabilidad de cerrados (D35) ya rige para la gestion; toda clasificacion nueva debe usar las mismas guardas |
| S2 | Excepciones actuales sobre cerrados: `LINK_DECIDE` decide sobre una COM posterior aunque el REQ este cerrado (no cambia datos del REQ) y `CASE_ACK` acusa una asignacion | `IntakeCommands.java:276-295`, `CaseCommands.java:40-49` | Se conservan (no modifican el expediente cerrado); el barrido de D35 las declara como excepciones documentadas |
| S3 | Los bloqueos de cierre se calculan dos veces: para mostrarlos (`Queries.java:73-83`, `:95-97`) y al validar el comando (`RequirementCommands.java:73-75`, `Rules`) | lectura | Los bloqueos nuevos (D28) van en una sola regla usada por ambos lados |
| S4 | Resultado de la primera atencion solo como prefijo de texto `RESULTADO: ...` de una constancia y en la nota de auditoria | `IntakeCommands.java:74`, `Attention.java:237`, `:242` | Columna estructurada y carga historica por el prefijo exacto (evidencia), marcada como reconstruida |
| S5 | `management_note` no tiene tipo; `document_item.document_type` (OFICIO, INFORME, CORREO, CONSTANCIA, OTRO) existe | V1, V4, `Documents.java:157` | La tipificacion de actuaciones extiende `management_note` (D36), sin modulo paralelo |
| S6 | La ficha 1.1 estructura solo a la persona adulta de la modalidad GENERAL; en NNA, region y comuna son texto libre; `people[]` tiene `name`, `age`, `nationality` e `identity_document` libres; `institutions` es texto | `Attention.java:55-140`, `FormSections.tsx:89`, `schema.ts:5-18` | Ficha 1.2 (D33) con la estructura canonica de persona; lo historico no se divide ni se reinterpreta |
| S7 | La COM manual guarda presentador, documento, domicilio y contactos como texto sin validar, y personas con `role`, `name`, `identity_document`, `relationship`, `location`; no tiene region | `IntakeCommands.java:45-63`, `:136-149`, `Intake.tsx:17-22`, `:124-129` | Ficha de ingreso de la COM manual con el mismo esquema canonico (D33) |
| S8 | El rotulo de la region de la ficha es "Region de atencion", pero D21 la usa como region de origen de la persona | `schema.ts` (LABELS), `OriginRegion.java:13-24` | Rotulo "Region y comuna del requirente" (DI-08), sin cambiar la regla de D21 |
| S9 | `TRANSFERENCIA` cambia `requirement.principal_scope`; la historia queda en hitos (`REQ_CREADO.scope_id`, `FLUJO_DECIDIDO`) | `RequirementCommands.java:139`, `Lifecycle.java:68` | La sede responsable inicial y la vigente se derivan de hitos (D37) |
| S10 | `Reports` fecha el volumen por `created_ms` y usa el principal vigente; no tiene corte | `Reports.java:380-394` | Se preserva tal cual (reportes operacionales); la demanda usa el motor nuevo (D38) |
| S11 | `PersonIndex` indexa ficha, COM manual y Formulario con roles `REQUIRENTE, CURADOR, NNA, ACOMPANANTE, PRESENTADOR, AFECTADO_NNA, ADULTO_RELACIONADO` | `PersonIndex.java:35` | Debe leer la estructura canonica nueva sin indexar `PERSONA_SENALADA` ni `OTRA_PERSONA` (D5) |
| S12 | El correo tiene asunto fijo y `siger.mail.link` solo admite `https://host[:puerto]/`; el aviso cabe en 300 caracteres | `MailOutbox.java:24`, `:38-43`, `:101-104`, V1 `inbox_notice.text_value` | Hoja de ruta 0.9.0 (seccion 15.3) |
| S13 | `SCOPE_ACTIVATE` conserva la apertura declarada (`COALESCE(opens_ms, ahora)`); la activacion queda solo en la auditoria `AMBITO_ACTIVADO` | `Personnel.java:92-99` | Tres fechas distintas (D44, hoja de ruta 0.9.0) |
| S14 | `Update.ps1` acepta cualquier respaldo integro de la misma raiz, base y JAR | `Update.ps1:6-7` | Hoja de ruta 0.9.0 (D49); en 0.8.0, respaldo y verificacion en la misma ventana del paso U |
| S15 | `Init-Server.ps1` crea un autofirmado `CN=localhost` por 365 dias; no hay script para pasar de RDP a red ni para renovar | `Init-Server.ps1:18`, `:81` | Acceso por red: hoja de ruta 0.9.0 (D49); el certificado se mantiene (DI-10) |
| S16 | `inbox_notice` no tiene indice por recurso; `DistributionNotices` lo consulta cada minuto con el lock global | V1, observacion 1 de T05e | Evaluacion con medicion (D48, hoja de ruta 0.9.0) |
| S17 | CSP: `img-src 'self' data:`; sin estilos ni recursos externos | `SecurityConfig.java:78` | Graficos SVG propios; PNG por `canvas` con `data:`; sin librerias externas |
| S18 | Catalogos de territorio y paises: misma huella en backend, frontend y Formulario (`territorio.json` `191621ac...`, `paises.json` `0cc7d5c8...`); `frontend/tests/adult.test.ts` compara backend y frontend | `sha256sum`, `adult.test.ts:33` | La equivalencia con el Formulario se fija por huella en una prueba (E00) |

### 1.3 Hallazgos en el Formulario Publico (solo lectura)

| Id | Hallazgo | Evidencia | Consecuencia |
|---|---|---|---|
| F1 | Modelo de persona: `id`, `rol` (PRESENTADOR, AFECTADO_NNA, ADULTO_RELACIONADO, PERSONA_SENALADA), `runNumero`, `runDv`, `modulo11`, `autenticada`, `estadoConsulta`, `atributos` y `discrepancias`; cada atributo con `valor`, `codigo`, `fuente` (CLAVE_UNICA_USERINFO, SRCEI, DECLARACION_CIUDADANA, SIN_DATO), `nivel`, `observadoEn` y `evidencia` | `IdentificacionCiudadana.java:12-36` | La procedencia por atributo es el estandar a respetar: SIGER nunca eleva un dato declarado |
| F2 | Atributos de identidad: `primerNombre`, `segundoNombre`, `primerApellido`, `segundoApellido`, `fechaNacimiento`, `sexo`, `edad` (0-17), `nacionalidad`, `tipoDocumento` (PASAPORTE, IDENTIDAD_EXTRANJERA, OTRO), `numeroDocumento`, `tipoIdentificacion`; los agregados `nombres` y `apellidos` (ClaveUnica, SRCeI) "nunca se dividen por espacios" | `IdentificacionCiudadana.java:38-41`, `:76-87`, `:185-192` | Estructura canonica de nombres en cuatro partes mas el agregado historico (2.4) |
| F3 | `sexo` admite `F, M, I, U`; SIGER rotula solo `F, M, I` | `IdentificacionCiudadana.java:191`, `ExternalProjection.java:30` | Rotulo de `U` en la interfaz; la proyeccion no se modifica |
| F4 | Contactos: correo con las mismas reglas que SIGER; el celular se normaliza a `+569XXXXXXXX` desde varios formatos | `ContactosFormulario.java:12-28`, `AdultIdentity.java:105-115`, `adult/identity.ts:77-89` | Mismo formato canonico; SIGER conserva su normalizacion de pegado |
| F5 | Reglas de nombre, RUN y contacto del frontend sin cambios desde `bbed7ad` (base del puerto de 0.4.0) | `git log bbed7ad..HEAD` sobre `personalData.ts`, `run.ts`, `contacts.ts` | Las validaciones de SIGER siguen equivalentes |
| F6 | Formulario adulto por motivo: consulta (`tema` general, entrevista con `entrevista-rol`, estadisticas), promocion (`organizacion`, `tipo-organizacion`, `actividad`, `publico`, `promocion-region`/`-commune`) y vulneracion (`riesgo`, `relacion` con 9 valores y `relacion-familiar`, `hechos-region`/`-commune`, `lugar`, `identifica-responsables`, `afectados-modalidad`, grupos `grupo-nna-N-*`) | `ValidacionAdultos.java:19-68` | Propuestas de tipo de solicitud y requirente, region de los hechos, relacion de quien presenta |
| F7 | Personas relacionadas: afectado (`datosDesconocidos`, `region`, `comuna`), adulto relacionado (`relacion`: Familiar, Representante, Persona de contacto, Testigo, Otro adulto relacionado), senalado (`alias`, `rol`, `region`, `comuna`) | `ValidacionAdultos.java:88-103` | Region y comuna del NNA afectado; la persona senalada nunca entra a estadistica personal ni al indice |
| F8 | Instituciones: `nombre`, `tipo` (Establecimiento educativo, Organismo publico, Empresa o entidad privada, Fundacion u ONG, Otra institucion), `rol`, `region`, `comuna`; solo existen como instituciones senaladas en vulneracion | `DatosFormulario.java:9-12`, `ValidacionAdultos.java:60-66`, `adultRules.ts:93` | Tipo de institucion involucrada como dimension; no son requirentes |
| F9 | Formulario NNA: `nna-region` y `nna-commune` obligatorias, relato, contacto seguro (`nna-primary-method` celular/email/social/unsafe, `nna-safe`); sin motivo | `ValidacionFormulario.java:21-31` | El tipo de solicitud de una COM NNA no se propone: queda pendiente para el funcionario |
| F10 | Nota contractual del 15/11/2026: el RUN del `presentador-nna` puede venir con `fuente=SRCEI` y `nivel=CONTRASTADO_EN_FUENTE` (no autenticado); "SIGER debe confirmar su consumidor antes de desplegar". SIGER no tiene prueba con esos vectores. El analisis estatico indica compatibilidad (`ExternalOriginal.java:97-149` admite SRCEI con ese nivel sin autenticacion; `ExternalProjection` exige la misma fuente en numero y DV) | `contratos/NOTA_PROCEDENCIA_RUN_SRCEI.md`, `contratos/fixtures/formulario-nna-srcei-v1.{0,1}.json` | Paso 1 de E00 (solo pruebas), primero en la secuencia: afecta a produccion desde el 15/12 |

### 1.4 Consecuencias de diseno
1. La recepcion del Formulario no cambia (D33, seccion 2 del plan): todo dato del Formulario se lee de
   `external_person`, `external_attribute` y `external_field`, ya proyectados.
2. Se usa un esquema canonico unico de persona declarada para la ficha (1.2) y la COM manual, con las mismas
   validaciones de backend y frontend que hoy usa la persona adulta (`AdultIdentity`, `adult/identity.ts`).
3. Todo indicador tiene una regla unica en Java y SQL portable, usada por pantalla, exportacion y corte.

---

## 2. Matriz de normalizacion

### 2.1 Principios (condicion transversal del responsable)
- El Formulario y las fichas de SIGER son referencias complementarias; ninguna reemplaza a la otra. La ficha presencial
  conserva integramente sus campos institucionales exclusivos (participacion, curador, prioridad, contacto seguro,
  tribunal, RIT, RUC y los demas de `Attention.normalize`).
- Un mismo dato tiene la misma estructura, el mismo catalogo y la misma validacion en todos los mecanismos.
- Nada se inventa: lo vacio queda vacio; no se dividen nombres historicos; los valores historicos identicos se
  conservan sin revalidarse (regla vigente de la ficha 1.1).
- Una sola fuente de verdad por dato: el Formulario en `external_*`; la ficha en `attention_revision`; la COM manual en
  su ficha de ingreso (3.5). Las columnas de resumen de `communication` (`presenter`, `identity_document`, contactos)
  son derivadas y no se usan como fuente estadistica.
- Procedencia siempre visible: Formulario (con su fuente y nivel por atributo), ficha (declarado ante un funcionario,
  con autor y version) o COM manual (registrado por un funcionario).

### 2.2 Matriz de equivalencias

| Dato | Formulario (campo, validacion, fuente) | Ficha GENERAL 1.1 | Ficha NNA 1.1 | COM manual | Estructura canonica 0.8.0 |
|---|---|---|---|---|---|
| Primer nombre | `primerNombre`, reglas de `personalData.ts`, CU, SRCeI o declarado | `presenter_first_name`, `AdultIdentity.nameProblem` | `presenter_name` libre | `presenter` libre | `first_name` (100), misma regla |
| Segundo nombre | `segundoNombre` | `presenter_middle_name` | - | - | `middle_name` (100) |
| Primer apellido | `primerApellido` | `presenter_last_name` | - | - | `last_name` (100) |
| Segundo apellido | `segundoApellido` | `presenter_second_last_name` | - | - | `second_last_name` (100) |
| Nombre agregado historico | `nombres`/`apellidos` (CU, SRCeI) | `presenter_name` derivado | `presenter_name` libre | `presenter` | `legacy_name`: se conserva y nunca se divide |
| Tipo de identificacion | `tipoIdentificacion` (DOCUMENTO_EXTRANJERO) o RUN | `identity_type` RUN/OTRO/NO_DISPONIBLE | `identity_type` | - | `identity_type` RUN, DOCUMENTO_EXTRANJERO, NO_DISPONIBLE (OTRO 1.1 = DOCUMENTO_EXTRANJERO) |
| RUN y DV | `runNumero`, `runDv`, `modulo11` | `presenter_run_number`, `presenter_run_dv` (modulo 11 solo matematico) | `identity_document` libre | `identity_document` libre | `run_number`, `run_dv` |
| Documento extranjero | `tipoDocumento`, `numeroDocumento` | `presenter_document_kind`, `presenter_document_number` | libre | libre | `document_kind` (PASAPORTE, IDENTIDAD_EXTRANJERA, OTRO), `document_number` (40) |
| Nacionalidad | `nacionalidad` (249 paises + especial) | `nationality` (mismo catalogo) | libre | - | `nationality` (mismo catalogo) |
| Fecha de nacimiento | `fechaNacimiento` | `presenter_birth_date` (`birthProblem`) | - | - | `birth_date` (aaaa-mm-dd, no futura, 130 anios) |
| Edad | `edad` 0-17 (afectado) | `people[].age` libre | `people[].age` libre | - | `age_years` entero declarado; el texto historico se conserva como `legacy_age` |
| Sexo | `sexo` F/M/I/U (presentador; SRCeI o declarado) | - | - | - | Exclusivo del Formulario: se conserva, no se agrega a la captura de SIGER |
| Region y comuna del requirente | `adult-region`/`-commune` o `nna-region`/`-commune` | `region`, `commune` (catalogo) | `region`, `commune` libres | - | `region_code`, `commune` (catalogo del Formulario) |
| Domicilio | `adult-address` | `presenter_domicile` | `presenter_domicile` | `presenter_domicile` | `address` (500) |
| Correo | `email` | `contact_email` (`validEmail`) | libre | `contact_email` sin validar | `email` (misma regla) |
| Celular | `telefono` normalizado a `+569` | `contact_phone` (`validMobile`) | libre | `contact_phone` sin validar | `mobile` `+569XXXXXXXX` |
| Otro contacto | `nna-*-social`, handle | - | - | `contact` | `other_contact` (300) |
| Contacto seguro | `nna-primary-method`, `nna-safe`, instrucciones | `safe_contact`, `safety_note` | idem | `contact_restrictions` | Exclusivos de cada mecanismo: se conservan sin equivalencia forzada |
| Nombre de preferencia y trato | `adult-preferred`, `adult-treatment`, `nna-*` | `preferred_name`, `pronouns` | idem | - | `preferred_name`, `treatment` (catalogo del Formulario: ella, el, elle, nombre, otra, no responde) |
| Relacion con el NNA | `relacion` (madre, padre, cuidador, otro-familiar, docente, salud, testigo, organizacion, otro) | `relationship` libre | libre | `persons[].relationship` libre | `relationship_code` (catalogo RELACION, version 1 = valores del Formulario) + `relationship_text` historico |
| NNA afectado | persona AFECTADO_NNA con identidad, `edad`, `region`, `comuna`, `datosDesconocidos` | `people[]` rol NNA | `people[]` rol NNA | `persons[]` rol NNA | Persona canonica con `role` y `region_code`, `commune` |
| Grupo de NNA | `grupo-nna-N-nombre/descripcion/cantidad/edades/region/comuna` | `children_scope`, `group_description`, `approximate_count` | idem | - | Se conserva cada mecanismo; `approximate_count` pasa a entero validado en 1.2 |
| Adulto relacionado | ADULTO_RELACIONADO con `relacion` | `people[]` ACOMPANANTE, REQUIRENTE | idem | ACOMPANANTE | Persona canonica (equivalencia de roles en 2.3) |
| Persona senalada | PERSONA_SENALADA (`alias`, `rol`, region) | `people[]` OTRA_PERSONA | idem | OTRA_PERSONA | Se conserva; nunca se indexa ni se usa como dimension estadistica personal |
| Instituciones | `instituciones[]` (`nombre`, `tipo` 5 valores, `rol`, `region`, `comuna`) | `institutions` texto | idem | - | `institutions[]` (`name`, `type_code` catalogo TIPO_INSTITUCION, `region_code`, `commune`) + texto historico |
| Hechos | `hechos-region`, `hechos-commune`, `lugar` | - | - | - | `facts_region_code`, `facts_commune`, `facts_place` (ficha 1.2 y COM manual) |
| Motivo | `motivo`, `tema` | `motive` libre obligatorio | idem | `subject` | Se conservan; el tipo de solicitud es una clasificacion aparte (D29) |
| Relato | `relato`, `nna-story` | `story` | idem | `body` | Sin cambios |
| Riesgo | `riesgo` si/no (vulneracion adulta) | `priority` (NO_EVALUADA, SIN_SENALES, PRIORITARIA) | idem | - | Significados distintos: no se fusionan |

### 2.3 Equivalencia de roles de persona (sin reescribir codigos historicos)

| Concepto (D30) | Formulario | Ficha | COM manual | Indice (`PersonIndex`) |
|---|---|---|---|---|
| Presentador (quien presenta) | PRESENTADOR | persona de nivel superior (`presenter_*`) | presentador | PRESENTADOR / REQUIRENTE |
| NNA afectado | AFECTADO_NNA | NNA | NNA | NNA, AFECTADO_NNA |
| Adulto relacionado | ADULTO_RELACIONADO (relacion) | ACOMPANANTE, REQUIRENTE | ACOMPANANTE | ACOMPANANTE, ADULTO_RELACIONADO, CURADOR |
| Persona senalada u otra | PERSONA_SENALADA | OTRA_PERSONA | OTRA_PERSONA | nunca |

El tipo de requirente (D30) es una clasificacion de la COM, no un rol de persona. La modalidad GENERAL/NNA es de la
atencion. La representacion se expresa con `relationship_code` (por ejemplo, representante u organizacion).

### 2.4 Persona declarada canonica (esquema `persona-1`)
Campos (todos opcionales salvo `role`): `role`, `first_name`, `middle_name`, `last_name`, `second_last_name`,
`legacy_name`, `identity_type`, `run_number`, `run_dv`, `document_kind`, `document_number`, `nationality`, `birth_date`,
`age_years`, `legacy_age`, `region_code`, `commune`, `address`, `email`, `mobile`, `other_contact`, `preferred_name`,
`treatment`, `relationship_code`, `relationship_text`, `schooling`, `location_text`.
- Validaciones: las de `AdultIdentity` (backend) y `adult/identity.ts` (frontend), extraidas a un componente comun
  (`DeclaredPerson` en Java; `lib/person/` en el frontend). `age_years` entero 0-130 (0-17 para el rol NNA).
- Derivados (no editables): nombre para mostrar = partes unidas o `legacy_name`; documento resumido.
- Historico: un valor igual al de la version anterior se conserva sin revalidarse; `legacy_*` nunca se reescribe; un
  nombre historico no se divide en partes.
- Procedencia: la de su contenedor (ficha o ficha de ingreso), con autor, fecha y motivo de la version.

### 2.5 Complementacion y rectificacion
Cada version nueva de una ficha o de la ficha de ingreso guarda un resumen de cambios por campo: COMPLEMENTO (de vacio
a valor) o RECTIFICACION (de un valor a otro, incluido quitarlo). Lo calcula el servidor comparando versiones; no lo
declara el usuario. La region de origen mantiene la regla de D21 (completar no es corregir).

### 2.6 Almacenamiento por mecanismo
- Formulario: sin cambios; lectura de `external_*`.
- Ficha de primera atencion: `attention_record`/`attention_revision` con `schema_version` 1.2 (1.0 y 1.1 siguen
  legibles; se actualizan al editarlas, como hoy 1.0 -> 1.1). Columna derivada nueva de resumen de cambios.
- COM manual: ficha de ingreso nueva (`intake_record`, `intake_revision`; seccion 3.5) con el mismo esquema canonico.
  `communication_person` queda como historia de las COM anteriores y no recibe filas nuevas.

### 2.7 Componentes reutilizables
- Backend: `DeclaredPerson` (normalizacion y validacion), `Territory` y `OriginRegion` (sin cambio de reglas),
  `AdultIdentity` como fuente de catalogos.
- Frontend: campos de identidad, contacto, region y comuna, nacionalidad y relacion extraidos de
  `features/attention/adult/` a `lib/person/` y `components/person/`, usados por la ficha y por el ingreso manual.

---

## 3. Modelo de informacion (migraciones V17 en adelante)

Los numeros definitivos los fija cada gate (la siguiente migracion libre). Ninguna migracion siembra catalogos, sedes,
regiones, feriados ni personas; ninguna reescribe datos existentes salvo columnas derivadas nuevas con marcador.

### 3.1 Catalogos estadisticos versionados
- `stat_catalog_version`: `id`, `catalog` (TIPO_SOLICITUD, TIPO_REQUIRENTE, VIA_INGRESO, CONTEXTO, CARACTERIZACION,
  DERECHO, TIPO_ACTUACION, TIPO_INSTITUCION, RELACION, TRAMO_EDAD; lista cerrada en Java), `label`, `state` (PROPUESTA,
  VIGENTE, RETIRADA), `source` (documento y version de origen), `file_sha256`, `loaded_by`, `loaded_ms`, `basis`,
  `activated_by`, `activated_ms`, `retired_ms`. Una sola VIGENTE por catalogo.
- `stat_catalog_item`: `version_id`, `code`, `label`, `parent_code`, `level`, `sort`, `selectable`, `range_from` y
  `range_to` (enteros, solo TRAMO_EDAD: anios cumplidos, inclusive, sin solapes ni vacios validados al importar);
  UNIQUE (`version_id`, `code`).
- `stat_catalog_mapping`: correspondencia entre versiones (`from_version`, `from_code`, `to_version`, `to_code`).
- Valores especiales fijos en Java, fuera de los catalogos: PENDIENTE, SIN_INFORMACION, NO_DECLARADO, NO_APLICA (D28).
- Administracion: TI con fundamento (`CATALOG_IMPORT` todo o nada, `CATALOG_ACTIVATE`, `CATALOG_MAPPING_IMPORT`). El
  repositorio trae archivos de referencia en `deploy/catalogos/` (propuesta version 1 y derechos 2022), que TI importa;
  ninguna migracion los carga.

### 3.2 Eventos de clasificacion (una tabla para todas las variables)
`stat_event`: `id`, `communication_id`, `record_type` (COM, REQ, CAS), `record_id`, `variable` (TIPO_SOLICITUD,
TIPO_REQUIRENTE, VIA_INGRESO, DERECHO, CONTEXTO, CARACTERIZACION), `event` (SET, ADD, REMOVE, STATUS), `status`
(INFORMADO, SIN_INFORMACION, NO_DECLARADO, NO_APLICA), `catalog_version_id`, `code`, `origin` (FUNCIONARIO, SISTEMA,
RETROSPECTIVA), `author_id`, `recorded_ms`, `basis`, `reopening_id`. Solo inserciones: la historia es la tabla. Indices
por (`communication_id`, `variable`, `id`) y (`record_type`, `record_id`, `variable`, `id`).
- Variables de valor unico (tipo de solicitud, requirente, via): el valor vigente es el ultimo SET o STATUS.
- Variables multiples (derecho, contexto, caracterizacion): el conjunto vigente son los ADD no seguidos de un REMOVE
  del mismo codigo; un STATUS (por ejemplo, SIN_INFORMACION) vale solo si el conjunto esta vacio.
- PENDIENTE = sin evento. La propuesta del Formulario no es un evento: se calcula (4.2).
- `origin` y `reopening_id` los asigna el servidor, nunca el cliente: SISTEMA para la via derivada; RETROSPECTIVA, con
  la reapertura vigente en `reopening_id`, para todo evento registrado sobre un REQ que ya tuvo un cierre (reabierto con
  el mecanismo existente, DI-07); FUNCIONARIO en los demas casos.

### 3.3 Actuaciones tipificadas
- `management_note`: columnas nuevas `action_type` (codigo de TIPO_ACTUACION; nulo = constancia sin tipificar,
  incluida toda la historia) y `action_catalog_version`. Una rectificacion (`NOTE_RECTIFY`) crea la constancia nueva con
  su tipo; la vigente es la que no tiene rectificacion posterior.
- `note_institution`: `note_id`, `name`, `type_code`, `catalog_version_id`, `region_code`, `commune`.
- `task_item.action_type` opcional: la tarea es un compromiso; completarla no crea una actuacion (regla vigente).
- `requirement.immediate_result` y `immediate_result_reconstructed`: resultado de la primera atencion (ORIENTACION,
  RESPUESTA_DIRECTA, REMISION_CONCLUIDA), escrito por `COM_IMMEDIATE` y `ATTENTION_RESOLVE`; la carga historica lo lee
  del prefijo exacto de la constancia (S4) con marcador.

### 3.4 Ficha 1.2
Agrega a `data_json`: `people[]` con el esquema `persona-1`; region y comuna con catalogo tambien en modalidad NNA;
`relationship_code`; `facts_region_code`, `facts_commune`, `facts_place`; `institutions_list[]`. Conserva todos los
campos de 1.1. `attention_revision.change_summary` (derivado, JSON con COMPLEMENTO/RECTIFICACION por campo).

### 3.5 Ficha de ingreso de la COM manual
`intake_record` (`communication_id` PK, `data_json`, `updated_by`, `updated_ms`) e `intake_revision` (`id`,
`communication_id`, `revision`, `author_id`, `registered_ms`, `reason`, `data_json`, `region_code`, `change_summary`).
`data_json`: presentador `persona-1`, `persons[]` `persona-1`, hechos e instituciones como en la ficha 1.2. Comando
`COM_COMPLETE` (complementar o rectificar con motivo) mientras la cadena este abierta. `OriginRegion` agrega la COM
manual como fuente (precision de D21 aprobada en D32): registrada = primera version con region; vigente = ultima.

### 3.6 Sedes (hoja de ruta 0.9.0, sin migracion en 0.8.0)
Tres fechas independientes (DI-06): `opens_ms` = apertura proyectada (sin cambio de columna; activar ya no la rellena);
`org_scope.opened_ms` = apertura efectiva, que TI declara al activar (fecha actual por defecto, editable; no futura) y
puede corregir despues con `SCOPE_UPDATE` y motivo; `activated_ms` y `activated_by` = instante y cuenta de
`SCOPE_ACTIVATE` (sistema). Carga historica: `activated_ms` desde `AMBITO_ACTIVADO` (evidencia), marcada como
reconstruida; `opened_ms` no se reconstruye ("sin declarar").

### 3.7 Cortes, series y configuraciones
- `stat_cut` y `stat_cut_row`, `stat_cut_multi` (seccion 6).
- `stat_series` y `stat_series_value` (seccion 7).
- `stat_view`: configuraciones guardadas (`owner_id`, `shared`, `title`, `definition_json`, `revision`).

### 3.8 Otros (hoja de ruta 0.9.0, sin migracion en 0.8.0)
- Indice de `inbox_notice` (`resource_type`, `resource_id`) solo si una medicion lo justifica (15.4).
- Distribucion territorial (DI-05, seccion 15.2): sin migracion prevista; la regla se deriva de datos existentes.

---

## 4. Reglas unicas

### 4.1 Ingreso computable (D25)
Una COM es un ingreso computable si su estado no es INVALIDA ni VINCULADA. Se cuentan las DUPLICADA, las pendientes
(NUEVA, con SOL pendiente o devuelta) y las de primera atencion (ATENDIDA). La cadena COM -> SOL -> REQ -> Casos cuenta
una vez: reaperturas, transferencias, derivaciones y Casos adicionales no multiplican. Una COM VINCULADA cuyo vinculo
se devolvio vuelve a NUEVA y vuelve a contar (estado vigente, o el del instante consultado). Fecha de referencia:
`received_ms` en America/Santiago.

### 4.2 Estados y propuestas
- Estados de una variable: PENDIENTE (sin evento), INFORMADO, SIN_INFORMACION, NO_DECLARADO, NO_APLICA.
- Propuesta (no es un estado): valor sugerido por una regla versionada sobre datos existentes, visible y confirmable.
  Version 1 de la regla:
  - TIPO_SOLICITUD: Formulario adulto `motivo` consulta -> consulta u orientacion; vulneracion -> vulneracion;
    promocion -> promocion. Formulario NNA, COM manual y atencion directa: sin propuesta.
  - TIPO_REQUIRENTE: Formulario NNA -> NNA; Formulario adulto -> persona natural adulta, salvo `motivo` promocion con
    `organizacion=si` -> persona juridica u organismo. Manual y directa: sin propuesta.
  - VIA_INGRESO: no es propuesta; se deriva del canal (FORMULARIO_WEB_PUBLICO -> Formulario Publico adulto;
    FORMULARIO_WEB_NNA -> Formulario Publico NNA; PRESENCIAL, CORREO, TELEFONO, OFICIO, REDES_SOCIALES,
    INTEGRANTE_DEFENSORIA, OTRO -> su via). Estado INFORMADO, origen SISTEMA; una correccion es un evento SET con
    motivo.
- Una propuesta nunca cuenta como INFORMADO: los reportes la muestran como "pendiente con propuesta" y permiten ver el
  valor propuesto por separado.

### 4.3 Quien clasifica (D34, D35)
- Variables de la COM: quien hoy puede actuar sobre la cadena: distribucion de la COM NUEVA (`Access.canDistribute`),
  receptor de la SOL pendiente (`Access.solReceiver`), jefatura principal o encargado del REQ abierto (`Access.reqMain`)
  y quien edita la ficha (`Access.attentionWritable`). Regla unica `Access.statWritable(actor, com)`.
- Derechos, contexto y caracterizacion: a nivel de REQ, `reqMain` o `attentionWritable` con el REQ abierto; a nivel de
  Caso, `caseForWrite` con el Caso abierto (responsable, integrantes y jefatura del ambito). Sin rol nuevo.
- Nunca: lectura institucional, lectura por materia, LECTURA, AUDITORIA, REPORTES ni TI.
- Inmutabilidad: si el REQ o el Caso estan cerrados, o la COM es INVALIDA, DUPLICADA o VINCULADA sin vinculo devuelto,
  todo evento se rechaza (409) sin excepcion de funcion. La unica via es la reapertura formal existente (`REOPEN_*`),
  sin modalidades nuevas (DI-07); lo registrado durante ella queda marcado como RETROSPECTIVA (3.2). La COM descartada
  no tiene reapertura y su clasificacion queda como se registro al descartarla.

### 4.4 Consolidacion, conteos y dimensiones multivaluadas
- Derechos de un requerimiento = union del conjunto vigente del REQ y de todos sus Casos. Cuenta de "requerimientos
  con el derecho X" = COM computables distintas; cuenta de "clasificaciones" aparte. La suma por derecho puede superar
  el total y el reporte lo dice.
- Actuaciones: cuenta de actuaciones vigentes por tipo y de requerimientos unicos con al menos una actuacion del tipo.
- La ficha muestra la consolidacion solo de los registros que la cuenta puede leer; los agregados la calculan dentro
  del alcance de REPORTES.
- Dimensiones multivaluadas: derecho (eje, derecho, subcategoria), contexto, caracterizacion, tipo de actuacion, tipo
  de institucion y las del NNA afectado (region, comuna, edad, tramo y nacionalidad) cuando hay mas de uno. La fuente
  tiene una fila por COM (5.1) y una relacion aparte (`StatSource` multivalor) con una fila por (COM, variable, codigo)
  y sus conteos: `en_req` (si/no), `casos` (Casos que lo registran), `menciones` (actuaciones o personas con ese
  valor). Un mismo valor en el REQ y en un Caso es una sola fila: no se duplica el requerimiento ni se pierde el dato.
- Medidas sobre una dimension multivaluada: REQUERIMIENTOS_CON_VALOR (COM distintas), MENCIONES y el total de
  requerimientos unicos (nunca la suma de categorias); los porcentajes usan como denominador el total de requerimientos
  unicos y la respuesta advierte que pueden sumar mas de 100 %. Un requerimiento sin valores aparece en su estado
  (PENDIENTE, SIN_INFORMACION u otro especial): ninguno queda fuera.
- Cruce de dos dimensiones multivaluadas: cada celda cuenta las COM que tienen ambos valores. Cruce con una de valor
  unico: cada celda cuenta COM distintas; el total de fila o columna de la dimension de valor unico es el total unico.

### 4.5 Completitud y cierre (D28)
Variables pertinentes por cierre (solo si su catalogo tiene version VIGENTE; sin catalogo vigente no bloquean):
- REQ_CLOSE, ATTENTION_RESOLVE y COM_IMMEDIATE: tipo de solicitud, tipo de requirente, via y region del requirente
  (INFORMADO o especial); si el tipo de solicitud es vulneracion, al menos un derecho (o SIN_INFORMACION) en el REQ o
  sus Casos y contexto (o especial).
- SOL_DISCARD con DUPLICADA: tipo de solicitud, requirente y via (la duplicada cuenta).
- CASE_CLOSE: ninguna variable nueva obligatoria.
- PENDIENTE (con o sin propuesta) impide el cierre; el dialogo de cierre permite resolverlo en el mismo acto.
- Una sola regla (`StatCompleteness`) alimenta `closure_blockers` y la validacion del comando.

### 4.6 Territorio multidimensional (D32)

| Dimension | Formulario | Ficha | COM manual | Regla |
|---|---|---|---|---|
| Region y comuna del requirente | `adult-*` o `nna-*` | `region`, `commune` | presentador | Version al ingreso y vigente |
| Region de origen registrada (D21) | `nna-region`, luego `adult-region` | primera version con region | primera version con region | `OriginRegion` sin cambio de regla; agrega la COM manual |
| Region y comuna del NNA afectado | detalle PERSONA `afectado-N`, `grupo-nna-N-*` | `people[]` rol NNA | `persons[]` rol NNA | Multiple: COM distintas por region |
| Region y comuna de los hechos | `hechos-*` | `facts_*` | `facts_*` | Valor unico |
| Sede responsable inicial | - | - | - | `REQ_CREADO.scope_id`; sin REQ: destino de la primera SOL |
| Sede responsable vigente | - | - | - | `principal_scope` (o destino de la SOL vigente) |
| Territorio por cobertura historica | - | - | - | Quien atendia la region de origen a la fecha de recepcion (`territorial_coverage` y estado de sedes) |
| Region de la sede (D22) | - | - | - | `Reports.region`: sin cambios |

### 4.7 Dimensiones temporales (D37)
- Al ingreso: primer valor (o el vigente en `received_ms` para territorio y cobertura).
- Al constituirse el REQ: valor vigente en `requirement.created_ms`.
- Vigente: ultimo valor.
- A una fecha historica t: valor vigente en t (eventos con `recorded_ms` <= t; versiones de ficha con
  `registered_ms` <= t; principal segun hitos <= t). Sin evidencia anterior a t, PENDIENTE: no se reconstruye.
- Segun corte: el valor congelado en el corte.

### 4.8 Edad y nacionalidad
- Personas: NNA afectado (mismas personas y fuentes que su region en 4.6) y requirente persona natural (el presentador
  cuando el tipo de requirente es persona natural adulta o NNA). Nunca adultos relacionados ni personas senaladas.
- Edad: anios cumplidos a la fecha de recepcion (America/Santiago) desde `birth_date` valida; si no hay, `age_years`
  entero declarado (Formulario `edad` 0-17 o ficha 1.2); si no hay, SIN_INFORMACION. Un texto historico (`legacy_age`,
  `people[].age` libre de 1.0 y 1.1) se conserva pero no se interpreta, salvo que sea exactamente un entero dentro del
  rango del rol. Nunca se estima. Los grupos de NNA (`grupo-nna-*`, `children_scope`) no aportan edades individuales.
- Tramo de edad: segun TRAMO_EDAD VIGENTE (contenido institucional, DI-02); sin catalogo vigente solo se ofrece la edad
  en anios.
- Nacionalidad: codigo del catalogo de paises (Formulario, ficha 1.1 y 1.2, ficha de ingreso); agrupacion Chile,
  extranjera y sin informacion, con el pais original disponible. Un texto libre de 1.0 no se convierte en pais.
- "Informacion suficiente": la dimension siempre existe con su categoria SIN_INFORMACION, y el explorador, la
  exportacion y el corte muestran el porcentaje de requerimientos con dato; no se oculta ni se infiere.
- Proteccion: la fecha de nacimiento no sale de la regla (solo la edad); el microdato lleva edad, tramo y nacionalidad
  sin otros identificadores (riesgo residual documentado, D41).

---

## 5. Motor estadistico

### 5.1 Arquitectura
- `StatSource`: una consulta SQL portable (H2 `MODE=MySQL` y MySQL 8.4) que produce una fila por COM con las columnas
  de 5.3 para un modo temporal (vigente, fecha t o REQ), con subconsultas correlacionadas como `OriginRegion`. No hay
  tabla materializada para lo vigente: no hay deriva entre la fuente y lo calculado. Para un corte, las mismas columnas
  salen de `stat_cut_row`.
- `StatEngine` (Java): valida la peticion contra listas cerradas, aplica alcance y filtros en SQL, agrega en SQL
  (`GROUP BY` sobre la fuente derivada), calcula porcentajes y comparaciones en Java, audita.
- Una sola metodologia: pantalla, exportacion y corte llaman al mismo `StatEngine` con la misma `StatSource`.

### 5.2 Medidas
INGRESOS (D25), EXCLUIDOS (invalidas) y ANTECEDENTES (vinculadas) como informativos; REQ constituidos; Casos;
ACTUACIONES y REQUERIMIENTOS_CON_ACTUACION; CLASIFICACIONES_DERECHO y REQUERIMIENTOS_CON_DERECHO; para toda dimension
multivaluada, REQUERIMIENTOS_CON_VALOR y MENCIONES con el total unico (4.4); COMPLETITUD (por variable y estado).

### 5.3 Dimensiones del explorador
Periodo de recepcion (dia, mes, trimestre, semestre, anio, Cuenta Publica julio-junio), registro tardio, estado de la
cadena, ingreso (manual, Formulario, atencion directa), via (original y agrupada), tipo de solicitud, tipo de
requirente, modalidad de atencion, region y comuna del requirente, region de origen registrada, region y comuna del
NNA afectado, region y comuna de los hechos, sede responsable inicial y vigente, territorio por cobertura, unidad,
nivel, materia, complejidad, origen (SOL o atencion directa), resultado de primera atencion, derecho (eje, derecho,
subcategoria), contexto, caracterizacion, tipo de actuacion, tipo de institucion, edad, tramo de edad y nacionalidad
(pais y agrupacion) del NNA afectado y del requirente persona natural (4.8), estado de cada variable. Sin
dimensiones de funcionario (el desglose por persona sigue en Reportes con REPORTES_PERSONAS).

### 5.4 Peticion
`POST /api/stats/query` (CSRF; GET 405): `measure`, `rows[]`, `columns[]`, `filters{}`, `period{type, from, to}`,
`time_mode` (VIGENTE, FECHA, AL_INGRESO, AL_REQ), `as_of`, `cut_id`, `compare` (periodo anterior, mismo periodo del
anio anterior u otro corte), `percent` (TOTAL, FILA, COLUMNA), `include_proposals`. Respuesta: celdas con `n`,
denominador, porcentaje y totales; avisos de multiplicidad y de variables pendientes; metadatos de la consulta.

### 5.5 Periodos y registros tardios
Periodos predefinidos y personalizados; limites por dia en hora de Chile (inclusive, como `Reports`). Registro tardio
= COM recibida en el periodo y registrada despues de su fin; se informa el numero y la distribucion del desfase.

### 5.6 Alcance
REPORTES con su alcance vigente (`Reports.reach`): una COM esta en el alcance si su ambito de referencia (principal
vigente del REQ; sin REQ, destino de la SOL vigente; sin SOL, ambito de origen) esta en los ambitos del alcance o, en
UPR/UPD/UEP, si es una sede con esa materia. El alcance se aplica en SQL antes de contar.

### 5.7 Rendimiento y limites tecnicos
Objetivo: respuesta del explorador menor a 3 s con 50.000 COM sinteticas en MySQL 8.4 (medido en E14). Sin limite de
dimensiones por politica; solo una salvaguarda tecnica de celdas de respuesta (configurable) con mensaje explicito.
Si la medicion no cumple, la sesion se detiene y propone una tabla derivada con marcador (decision expresa).

### 5.8 Relacion con Reports
`Reports` (tiempos, volumen, antiguedad), su exportacion, el tramo `REQ_TOTAL` y la reapertura no cambian (DI-07).

---

## 6. Cortes estadisticos
- `stat_cut`: `id`, `code` (CORTE-AAAA-NNN), `title`, `scope_id` (alcance; nulo = institucion), `period_type`,
  `period_from`, `period_to`, `as_of_ms`, `methodology_version`, `catalog_versions` (JSON), `calendar_sha256`,
  `filters_json`, `row_count`, `rows_sha256`, `metadata_sha256`, `created_by`, `created_ms`, `basis`, `state` (VIGENTE,
  ANULADO), `annulled_by`, `annulled_ms`, `annul_basis`.
- `stat_cut_row`: mismas columnas de `StatSource` mas `cut_id`; `stat_cut_multi`: (`cut_id`, `communication_id`,
  `variable`, `code`, `en_req`, `casos`, `menciones`), la relacion multivaluada de 4.4 congelada.
- `STAT_CUT_CREATE` (GESTION_ESTADISTICA con REPORTES en el mismo alcance): congela la fuente en un instante, calcula
  las huellas sobre una serializacion canonica (filas ordenadas por COM, UTF-8, LF) y audita. Inmutable: sin UPDATE ni
  DELETE; `STAT_CUT_ANNUL` con fundamento lo marca anulado y lo conserva.
- Verificacion de integridad recalculando las huellas; comparacion corte vs vigente y corte vs corte por celda y por
  fila (sin exponer filas fuera del alcance).

## 7. Series historicas importadas
- `stat_series`: `id`, `title`, `indicator`, `scope_id` (ambito de la serie: nulo = institucional, o una sede),
  `source` (documento), `methodology`, `granularity`, `period_from`, `period_to`, `imported_by`, `imported_ms`,
  `basis`, `file_sha256`, `state` (VIGENTE, ANULADA), `annulled_by`, `annulled_ms`, `annul_basis`.
- `stat_series_value`: `series_id`, `period_from`, `period_to`, `dimension`, `category_label`, `category_code`
  (opcional, mapeado a un catalogo), `value` (entero >= 0).
- Asistente: plantilla CSV/XLSX descargable (`periodo_desde`, `periodo_hasta`, `dimension`, `categoria`, `valor`),
  correspondencia de columnas, vista previa sin escrituras, validacion (fechas, enteros, totales por dimension contra el
  total si se informa), fuente, periodo y metodologia, confirmacion (`STAT_SERIES_IMPORT`, todo o nada). Nunca se
  combina automaticamente con datos de SIGER ni con otra serie; las superposiciones de periodo se avisan y la suma solo
  se ofrece si el usuario la pide y no hay superposicion.
- Ambito y visibilidad (misma regla que los cortes):
  - Importar y anular: GESTION_ESTADISTICA con REPORTES en el mismo alcance; el ambito declarado debe estar dentro de
    ese alcance (una serie institucional exige alcance institucional).
  - Ver y consultar: REPORTES cuyo alcance cubre el ambito de la serie; una cuenta con alcance de una sede ve las
    series de esa sede, no las institucionales ni las de otras sedes.
  - Exportar: ademas EXPORTACION en el mismo alcance. TI, AUDITORIA y las funciones de gestion sin REPORTES no la ven.
  - Las series son agregados sin datos personales; se auditan su importacion, anulacion, consulta y exportacion
    (`SERIE_IMPORTADA`, `SERIE_ANULADA`, `STAT_CONSULTA`, `STAT_EXPORTACION`).
  - Al compararla con SIGER, el explorador avisa si el ambito de la serie y el de la consulta difieren; nunca los
    combina.

## 8. Exportaciones
- CSV: convenciones vigentes de `Csv` (UTF-8 con BOM, `;`, coma decimal, fechas de Chile, proteccion de formulas).
- XLSX: escritor propio con la JDK (`java.util.zip` y XML), una hoja por tabla, deterministico (fechas internas fijas)
  para que la huella sea reproducible; lector propio para importar series (zip limitado en entradas y tamano, sin DTD).
- ZIP "paquete estadistico": `LEEME.txt`, `metadatos.json` (consulta, alcance, periodo, modo temporal, corte, versiones
  de metodologia, catalogos y calendario, cuenta, instante), `agregado.csv` y `agregado.xlsx`, `microdatos.csv` (una
  fila por COM computable con las columnas de valor unico de 5.3; con folio; sin nombres, RUN, fecha de nacimiento,
  relatos, contactos ni texto libre; por cada variable multivaluada, su estado y el numero de valores),
  `microdatos_multivalor.csv` (una fila por folio, variable y codigo, con rotulo, nivel jerarquico, `en_req`, `casos` y
  `menciones`; 4.4), `diccionario.csv`, `catalogos.csv`, `SHA256SUMS.txt`. El XLSX lleva las mismas tablas en hojas.
- Multivaluadas sin duplicar ni perder: el microdato nunca repite un folio; todo valor esta en el archivo relacionado;
  un requerimiento sin valores figura con su estado; el agregado de una dimension multivaluada trae las columnas
  REQUERIMIENTOS_CON_VALOR, MENCIONES y el total de requerimientos unicos, con una nota en `LEEME.txt` y en el
  diccionario. Una prueba re-agrega ambos archivos y reproduce el agregado.
- Graficos y tablas de pantalla: SVG y PNG generados en el navegador (sin librerias), CSV/XLSX desde el servidor con
  el mismo calculo.

## 9. Facultades y auditoria

| Funcion | Que permite en 0.8.0 |
|---|---|
| Operativas (Administrativo, Profesional, Jefatura, Direccion, Coordinacion regional, Distribucion) | Clasificar dentro de su facultad efectiva de gestion (4.3) |
| CONSULTA_INSTITUCIONAL, LECTURA, lectura por materia | Ver clasificaciones de lo que leen; nunca escribir |
| REPORTES (alcance) | Explorador, cortes y series de su alcance (7), calidad; Reportes actuales |
| REPORTES_PERSONAS | Sin cambios (solo Reportes actuales) |
| EXPORTACION (con REPORTES en el mismo alcance) | CSV, XLSX y ZIP, incluidos microdatos |
| GESTION_ESTADISTICA (nueva y aprobada, OPCIONAL, exige REPORTES en el mismo alcance; incompatible con TI) | Generar y anular cortes e importar y anular series dentro de su alcance; compartir configuraciones |
| TI | Catalogos estadisticos con fundamento; sin acceso a estadisticas (D2) |
| AUDITORIA | Eventos de auditoria |

Auditoria: `STAT_CONSULTA` y `STAT_EXPORTACION` (medida, nombres de dimensiones y filtros, modo, formato y filas;
nunca valores), `CORTE_GENERADO`, `CORTE_ANULADO`, `SERIE_IMPORTADA`, `SERIE_ANULADA`, `VISTA_COMPARTIDA`,
`CATALOGO_IMPORTADO`, `CATALOGO_ACTIVADO`, `CLASIFICACION_REGISTRADA` y `CLASIFICACION_CORREGIDA` (solo variable y
registro; los codigos quedan en `stat_event`, con lectura protegida).

## 10. Interfaces y facilidad de uso

### 10.1 Captura
- Distribucion (`COM_ROUTE`, `COM_ASSUME`), ingreso manual, atencion directa y resolucion en el acto: un bloque
  "Caracterizacion del ingreso" con tipo de solicitud, requirente y via, prellenado con la propuesta y marcado como tal;
  opcional salvo cuando el acto cierra (4.5).
- Ficha de COM y de REQ: tarjeta "Caracterizacion" con estado de cada variable, propuesta, historial y boton
  Clasificar/Corregir (con motivo si ya estaba informada).
- Actuaciones: el dialogo "Registrar actuacion" agrega Tipo de actuacion (opcional) e instituciones; el dialogo de
  tarea agrega "Compromiso de" (opcional).

### 10.2 Componente de derechos
`components/RightsPanel.tsx` (logica en `lib/rightsModel.ts`): busqueda por texto, navegacion eje -> derecho ->
subcategoria, seleccion multiple, contexto y caracterizacion, estado y historial. Ubicaciones: `RequirementDetail.tsx`
inmediatamente despues del panel "Datos del REQ"; `CaseDetail.tsx` inmediatamente despues de "Datos del Caso"; acceso
desde "Registrar actuacion" (enlace al panel del mismo registro) y desde la ficha de primera atencion. Solo lectura sin
facultad; deshabilitado con el registro cerrado y el texto "Requiere reapertura formal".

### 10.3 Explorador estadístico
Interfaz profesional, útil y clara, que aprovecha todo lo que entrega el motor, sin cambios de servidor ni
dependencias nuevas.

**Estructura**: `#estadisticas` con las secciones Panorama, Informes y Explorar (E12), y Cortes y series y Calidad
(E13).

**Panorama**
- Periodo elegible. Por defecto, la Cuenta Pública en curso, como `PERSONALIZADO` desde el 1 de julio hasta el día
  anterior a la consulta, comparado con `MISMO_PERIODO_ANIO_ANTERIOR`. El rótulo muestra las fechas exactas.
- Indicadores: ingresos, REQ constituidos, Casos, actuaciones, porcentaje de ingresos con caracterización completa y
  registros tardíos.
- "Caracterización completa" = ingresos computables sin ninguna de las seis variables de completitud de E07 en estado
  `PENDIENTE`, sobre el total de ingresos. Lo calcula el servidor con filtros de los `ESTADO_*`; el cliente no calcula
  ni suma.
- Cada indicador con su variación respecto del periodo anterior (cifra, porcentaje, flecha y texto, nunca solo color)
  y una línea mensual pequeña.
- Un clic abre la consulta en Explorar.

**Informes**
- Figura 1 (tipo de solicitud, requirente, vía y adscripción institucional, DI-13), de julio a junio, con rótulos y
  orden institucionales.
- Exportación CSV y XLSX.
- El botón "Congelar estas cifras" se agrega en E13.

**Explorar**
- Estado inicial con preguntas guiadas: "¿Cuántos ingresos hubo?", "Por vía de ingreso", "Comparar con el periodo
  anterior" y "Figura 1 de la Cuenta Pública".
- Campos agrupados por familia (Periodo, Ingreso, Territorio, Derechos, Actuaciones, Personas, Estado de la cadena y
  Calidad), con buscador, descripción breve y la marca "valores múltiples".
- Filas y columnas por selección o arrastre, también con teclado.
- Filtros con chips.
- Medida en lenguaje llano.
- Selector de cifra: cantidad, % del total, % por fila y % por columna; en las múltiples, "requerimientos con el
  valor", "menciones" y "total único".
- "Cómo leer esta cifra", siempre visible: qué cuenta la medida y su unidad, fecha de análisis, modo temporal,
  alcance, periodo, versiones de catálogo, cobertura de edad y nacionalidad, y cada aviso del servidor traducido a una
  frase. La región de los NNA se rotula como no aditiva.
- Todas las categorías se muestran; no se agrupan automáticamente.
- Jerarquía de derechos: una consulta por nivel (eje, derecho y subcategoría), anidadas con el catálogo; cada cifra
  viene del servidor y nunca se suma en el cliente. Nota "los niveles inferiores no suman el superior" y "Sin
  desglose" como fila propia.

**Exportación**
- E12 exporta CSV y XLSX solo en Informes.
- E13 exporta CSV, XLSX y ZIP desde cualquier vista y desde cortes, con los mensajes de las restricciones del ZIP:
  límite de folios, solo medidas de requerimientos, grupos prioritarios y cortes anteriores a V24.

**Pie de fuente**: en toda tabla y gráfico, incluido en PNG y SVG: "Fuente: SIGER, Defensoría de la Niñez. Consulta del
<fecha y hora>; <alcance>; <fecha de análisis>; <modo>", más el código del corte si corresponde.

**Gráficos**
- Sugerencia automática según la forma de los datos:

  | Forma de los datos | Gráfico sugerido |
  |---|---|
  | Un eje de periodo | Líneas |
  | Una dimensión de valor único | Barras horizontales ordenadas de mayor a menor |
  | Dos dimensiones | Barras agrupadas, o apiladas solo si las columnas son de valor único |
  | Una dimensión múltiple | Barras con la nota |
  | Una comparación | Barras pareadas o de diferencia |

- Circular opcional solo con una dimensión de valor único, sin columnas y con 6 categorías o menos, sumando el 100 %;
  si no, se explica por qué y se proponen barras.
- Paleta institucional accesible (contraste AA, distinguible con daltonismo), rótulos directos, formato chileno, foco
  por teclado y la tabla como alternativa textual.
- Descarga en PNG y SVG con título, subtítulo, notas y fuente.

**Tabla**
- Encabezados fijos y cifras a la derecha con `tabular-nums`.
- Miles con punto y decimales con coma.
- Orden por columna.
- Márgenes y total destacados, nunca sumados en el cliente.
- Jerarquía eje → derecho → subcategoría plegable.
- 0 como cero y "—" con explicación cuando no hay dato.

**Estado y rendimiento**
- La consulta queda en la dirección (`#estadisticas?...`) para volver atrás y compartir.
- Vistas guardadas.
- Consultas en fila, una a la vez por cuenta por el cupo de `StatGuard`, con caché de la sesión y esqueletos de carga.
- Mensajes llanos para 429, 503 y motor deshabilitado.

**Móvil**: Panorama e Informes completos; Explorar simplificado; tablas con desplazamiento horizontal.

**Restricciones**: sin códigos ni SQL a la vista; sin dependencias nuevas; gráficos SVG propios.

### 10.4 Cortes, series y calidad
Cortes: generar (asistente de cuatro pasos), ver, verificar integridad, comparar y anular. Series: asistente de
importacion (plantilla, columnas, vista previa, validacion, confirmacion). Calidad: completitud por variable, ambito y
periodo, con enlace a los registros pendientes que la cuenta puede leer.
- "Congelar estas cifras" desde cualquier vista abre el asistente de corte prellenado y advierte: "Generar el corte
  detiene brevemente las operaciones de todos. Hazlo fuera del horario de atención".
- La comparación con un corte resalta las diferencias celda a celda.
- Una serie histórica se muestra en el mismo gráfico que SIGER, con la discontinuidad marcada y la nota de ámbito, sin
  sumarlas salvo que se pida.
- Calidad: barras de completitud por variable y ámbito, tendencia mensual y enlace a los pendientes.
- Las bandejas existentes agregan el filtro "Con clasificación pendiente" en `BoardQueries.filter`, con las mismas
  reglas de lectura de la bandeja y sin ruta nueva. El panel de Calidad enlaza a ese filtro.

### 10.5 Criterios de facilidad de uso
Los acredita el ejecutor en el humo y los revisa Claude Padre en E16, porque E15 está postergado.

U1 Obtener la tabla tipo Figura 1 (cuatro desgloses de la Cuenta Publica) en menos de 2 minutos, sin ayuda. U2
Comparar con el periodo anterior. U3 Cruzar tipo de solicitud por region del requirente y cambiar a porcentaje por
fila. U4 Exportar el paquete ZIP. U5 Generar un corte con fundamento. U6 Importar una serie desde la plantilla. U7
Hacer un grafico y descargarlo en PNG. U8 Guardar y compartir una vista. U9 Encontrar los registros pendientes de su
alcance. U10 Clasificar derechos de un REQ con busqueda en menos de 1 minuto. U11 Abrir un indicador del Panorama en
Explorar y pasarlo a % por fila en menos de 1 minuto. U12 Explicar, con la nota de la pantalla, por qué un desglose de
derechos no suma el total. U13 Descargar un gráfico listo para el informe, con título, notas y fuente. Un escenario no
logrado es un hallazgo.

## 11. Catalogo de derechos de referencia
Fuente: Defensoria de la Ninez, "Modelo de conceptualizacion y categorizacion de derechos de la ninez y adolescencia",
version 2.0 (2022), Observatorio de Derechos
(`https://observatorio.defensorianinez.cl/wp-content/uploads/2022/10/Instructivo-categorizacion-de-derechos-.pdf`). El
Informe Anual 2026 (Figura 11) usa los mismos 4 ejes y 17 derechos, con rotulos levemente distintos (por ejemplo,
"Derecho al acceso a la informacion"). No hay evidencia de 24 derechos. El documento no define reglas de seleccion
unica o multiple ni de informacion faltante; esas reglas son de SIGER (D34).

- Eje Vida y supervivencia: 3.1 Salud (salud integral; nutricional; sexual y reproductiva; bucal; mental); 3.2
  Vivienda adecuada (vivienda; acceso a servicios; seguridad del entorno); 3.3 Medioambiente sano (entornos sin
  contaminacion; crisis climatica); 3.4 Nivel de vida adecuado (seguridad de ingresos y fin a la pobreza; apoyo en
  situaciones de crisis; proteccion social; seguridad alimentaria y vestuario).
- Eje Desarrollo: 4.1 Desarrollo integral (primera infancia; infancia; adolescencia); 4.2 Descanso, esparcimiento,
  cultura y arte (descanso y tiempo libre; esparcimiento, juego y recreacion; culturas y artes); 4.3 Educacion (acceso;
  calidad y pertinencia); 4.4 Uso y disfrute del entorno publico (seguridad y proteccion; planificacion y pertinencia).
- Eje Proteccion: 5.1 Vivir en familia (cuidado y crianza; procesos que resguardan el derecho a vivir en familia; NNA
  separados de su entorno familiar); 5.2 Proteccion contra toda forma de violencia (descuido o negligencia; violencia
  mental, psicologica y emocional; violencia fisica y corporal; torturas y tratos crueles, inhumanos y degradantes;
  violencia sexual; explotacion sexual comercial infantil; explotacion economica y trabajo infantil; NNA en contexto de
  conflictos armados; NNA con conducta delictiva); 5.3 Trato justo (procedimientos judiciales; justicia penal
  adolescente; privacion de libertad); 5.4 No discriminacion (NNA con discapacidad; migrantes; pueblos originarios y
  tribales; mujeres, ninas y adolescentes; diversidad sexual; situacion de calle; adolescentes en el sistema de
  responsabilidad penal; NNA en proteccion y cuidado); 5.5 Identidad, nombre y nacionalidad (identidad, nombre y
  nacionalidad; NNA migrantes).
- Eje Participacion: 6.1 Libertad de pensamiento, conciencia y religion (a nivel social, digital y familiar; en espacios
  educativos y de responsabilidad del Estado; de NNA de pueblos o comunidades etnicas, religiosas, linguisticas,
  extranjeras o indigenas); 6.2 Informacion y libertad de expresion (conocimiento de los derechos; acceso a la
  informacion en procedimientos; libertad de expresion); 6.3 Libertad de asociacion (asociacion; reuniones y
  manifestaciones pacificas); 6.4 Ser escuchados (instancias de participacion; procesos administrativos y judiciales;
  nivel familiar y social).
- No seleccionables para requerimientos salvo decision (DI-04): el eje transversal "Rol del Estado como garante
  principal" y los grupos prioritarios (seccion VIII del modelo, no transcrita), que pueden servir de caracterizacion.

Los rotulos anteriores estan abreviados para lectura; el archivo `deploy/catalogos/derechos-2022-v2.json` (E02) los
transcribe textuales con su numeracion y pagina, y el responsable verifica la transcripcion contra el PDF antes de
activarlo. Codigos internos: `3.1`, `3.1.01`... (estables, propios de SIGER).

## 12. Dependencias
Sin dependencias nuevas de ejecucion: XLSX y ZIP con la JDK; graficos SVG propios; PNG con `canvas` (CSP lo permite con
`data:`). Alternativa solo con autorizacion expresa (DI-12): `org.dhatim:fastexcel` (Apache 2.0) para escribir XLSX.
No se usa Apache POI ni librerias de graficos o de tablas.

## 13. Estrategia de pruebas
1. Conjuntos sinteticos con esperados calculados a mano en recursos de prueba (CSV fijos), nunca con el codigo bajo
   prueba: cadenas completas por `flow.run`, COM del Formulario por `conserveExternal` con vectores sinteticos.
2. Mutacion por regla critica, con la evidencia "regla -> prueba que fallo" en cada registro.
3. Suite completa en H2 y sobre MySQL 8.4 aislado; `MySql84UpgradeTest` desde 0.4.0 y desde el volcado sintetico 0.7.0
   (E01) hasta la ultima migracion.
4. Barrido de inmutabilidad: todo comando sobre un REQ o Caso cerrado y toda clasificacion se rechaza (como el barrido
   de `InstitutionalReaderTest`), con las excepciones documentadas de S2.
5. Conciliacion: pantalla = exportacion = corte para la misma consulta; la suma de cada desglose de valor unico es el
   total; la re-agregacion del microdato y de su archivo multivalor reproduce el agregado, tambien en las dimensiones
   multivaluadas (escenario 15); ningun folio se repite en el microdato.
6. Prueba de aceptacion "Figura 1 sintetica" (escenario 1 del plan).
7. Permisos: cada endpoint nuevo con cuenta sin funcion, fuera de alcance, TI y lector; CSRF y GET 405.
8. Edad y nacionalidad: fechas limite (cumpleanos, 29 de febrero, cambio de horario), textos historicos sin interpretar,
   nacionalidad libre sin convertir (escenario 14).
9. Originales: `communication_original.document_sha256` y filas `external_*` identicas antes y despues de cada gate
   que toque ingreso o actualizacion.
10. Rendimiento en MySQL con 50.000 COM sinteticas (E14).
11. Frontend: logica pura en `<nombre>Model.ts` con `node --test`; render SSR como `territory-render`; modelo del
    grafico circular.
12. Facilidad de uso: escenarios U1-U13.

## 14. Migraciones y actualizacion
- Aditivas desde V17, portables, sin CHECK que H2 y MySQL traten distinto, con indices para cada consulta nueva.
- Rutinas de arranque con marcador (tambien con `migrate-only`): resultado de primera atencion (S4) y resumen de cambios
  de versiones existentes (2.5); la activacion de sedes (3.6) pasa a 0.9.0. Lo que no reconocen lo omiten y lo cuentan
  (patron de `MILESTONES_V14`).
- Sin catalogos vigentes despues de actualizar: nada bloquea el cierre (4.5) hasta que TI los active.
- Actualizacion de produccion (desde 0.6.0, esquema 14, o 0.7.0, esquema 16, la que este en produccion): `Backup.ps1`,
  `Restore-Check.ps1` (evidencia del respaldo verificado; ambos en la misma ventana, inmediatamente antes de la
  actualizacion), `Update.ps1` (migra con el JAR candidato y activa),
  `Start.ps1`, `Estado.ps1`. Ensayo en TEST-SIGER con el paquete inmutable de la version efectiva (E17). La ejecucion en
  la VM es un paso del responsable con autorizacion expresa, sin esperas artificiales.

## 15. Hoja de ruta 0.9.0 (postergado el 2026-11-24; sin gates en 0.8.0)
Pendientes heredados con decision aprobada que no se implementan en 0.8.0. Se conserva su diseno para planificar 0.9.0;
ningun gate de 0.8.0 toca estos puntos. Mientras tanto, produccion mantiene el comportamiento de 0.6.0 o 0.7.0.

15.1 Sedes (D44, DI-06): tres fechas independientes (3.6); Estructura las muestra; `SCOPE_ACTIVATE` pide la apertura
efectiva (por defecto, el dia de la activacion, editable, no futura) y no modifica la proyectada (se elimina el
`COALESCE` actual; hoy `opens_ms` solo se muestra y edita: `Queries.java:269`, `Personnel.java:89`, `:96`);
`SCOPE_UPDATE` la corrige con motivo auditado; carga de `activated_ms` desde la auditoria con marcador. Archivos
previstos: migracion, `Personnel.java`, `Bootstrap.java`, `Queries.java`, `features/admin/Structure.tsx`,
`structureModel.ts`. Pruebas: activacion sin fecha usa la del dia; fecha futura rechazada; correccion sin motivo
rechazada; `opens_ms` intacto tras activar; no TI 403.

15.2 Distribucion territorial del Formulario (D45, DI-05):
- Quien: la coordinacion regional (jefatura de la sede, como hoy `Access.distributes` para el origen regional) de la
  sede activa que atiende hoy (`Territory`) la region vigente de la COM (`OriginRegion.current`) ve y distribuye las COM
  del Formulario en estado NUEVA de su territorio.
- Respaldo central: la distribucion central (DISTRIBUIDOR) conserva visibilidad nacional y puede distribuir cualquier
  COM del Formulario.
- Nivel central exclusivo: COM sin region vigente, o cuya region no atiende ninguna sede activa (incluida una sede
  proyectada).
- Avisos territoriales (D24): el aviso de COM nueva va a la coordinacion regional del territorio; la distribucion
  central conserva su aviso actual como respaldo.
- Devoluciones: una COM que vuelve a NUEVA (SOL devuelta o vinculo devuelto) vuelve a la bandeja que corresponda por la
  regla vigente en ese momento (territorio o central).
- Concurrencia: prevalece la primera operacion valida (revision de la COM); la segunda recibe 409 con un mensaje que
  indica que la COM ya fue distribuida.
- Sin cambios: la distribucion de COM manuales, las reglas de destino y la recepcion del Formulario.
- Archivos previstos (servidor): `Access.java`, `BoardQueries.java`, `DistributionNotices.java`, `IntakeCommands.java`
  (sin `conserveExternal`), `Queries.java`; (interfaz) `features/worklist/*`, `features/detail/ComDetail.tsx`.
- Pruebas: equivalencia de `Access` y `BoardQueries.Visibility` en las bandejas, escenarios por sede y central, COM sin
  region, region de sede proyectada, cambio de cobertura entre el ingreso y la distribucion, devolucion, dos
  distribuciones concurrentes.

15.3 Correo (D46, DI-09): asunto diferenciado por cada evento existente que genera avisos (lista cerrada derivada del
inventario de `Access` y `DistributionNotices`), folio del registro y descripcion breve (el texto actual del aviso,
respetando 300 caracteres), enlace directo a la ficha (`siger.mail.link` mas la ruta `#TIPO/id`) solo cuando esta
configurado, lo que exige el acceso por red (15.5). Sin datos personales. Archivos previstos: `MailOutbox.java`,
`Access.java`, `application.properties` (comentario). Pruebas con Graph falso, nunca red: un asunto por evento; ningun
asunto ni cuerpo con nombres, RUN, relatos ni contactos; sin `siger.mail.link`, sin enlace.

15.4 Indice de `inbox_notice` (D48): medir el plan de la consulta de `DistributionNotices` en MySQL con un volumen
sintetico alto; si usa recorrido completo, migracion con indice; si no, se registra la evidencia y no se agrega.

15.5 Scripts (D49): `Update.ps1` exige un respaldo verificado reciente (definido por un parametro con valor por defecto
y la evidencia de `Restore-Check.ps1`); script nuevo para pasar `bindAddress` a red en una instancia inicializada sin
`-AccesoRed` (instancia detenida, confirmacion, sin tocar el firewall). Cada uno con `Check-Scripts.ps1`, PS 5.1 y
ensayo en TEST-SIGER. La renovacion del certificado se desestimo (DI-10): se mantienen el certificado y la configuracion
HTTPS actuales. En 0.8.0 la antiguedad del respaldo se cubre con el procedimiento del paso U (respaldo y verificacion
en la misma ventana, inmediatamente antes de `Update.ps1`).

15.6 Observaciones de funcionarios (D47): registro en la bitacora (seccion "Observaciones de funcionarios"); el
responsable valida y Claude Padre las convierte en gates de alcance acotado de 0.9.0, salvo decision expresa de
incluir en 0.8.0 una que corresponda a sus objetivos.

15.7 Instituciones de los hechos (GO de E08, 2026-11-25): dimension estadistica multiple con el tipo, la region y la
comuna de las instituciones declaradas en la ficha de primera atencion, la ficha de ingreso y el Formulario
(`external_field` INSTITUCION). En 0.8.0 `TIPO_INSTITUCION` sale solo de `note_institution` ("Tipo de institucion de las
actuaciones"); las declaradas estan en JSON y exigen una proyeccion relacional por version, como V21.

## 16. Riesgos
- R1 Catalogos sin validacion institucional (DI-01 a DI-04): se desarrolla con versiones PROPUESTA; la activacion en
  produccion espera la validacion.
- R2 Carga operativa: propuestas, captura progresiva y bloqueo solo al cierre.
- R3 Reidentificacion: cifras exactas por decision (D41); riesgo residual documentado en el diccionario y en
  `LEEME.txt`; permisos, auditoria y reserva vigentes.
- R4 Rendimiento de la fuente derivada: medicion en E14 y alternativa con decision.
- R5 Reapertura para clasificar: por DI-07 conserva su efecto actual en los tiempos (`REQ_TOTAL` incluido); el
  indicador de calidad identifica lo retrospectivo para que Estudios lo interprete.
- R6 Primera actualizacion en sitio de produccion: ensayo E17, respaldo verificado y auditoria.
- R7 Contrato NNA SRCeI sin prueba (F10): E00, paso 1, antes de todo.
- R8 Deriva de catalogos con el Formulario: huellas fijadas en pruebas (E00).
- R9 Gates consolidados: dependencias reales explicitas, division `ENNa`/`ENNb` si un gate no cabe en una sesion con
  todas sus verificaciones, y auditoria independiente.
- R10 Plazo del 15/12/2026: ruta critica y calendario de referencia en las secciones 3 y 6 del plan; el plazo no reduce
  pruebas ni auditoria; produccion abre el Formulario con la version de V0-V6 si 0.8.0 no esta lista. Mitigado ademas
  por la reduccion del alcance (seccion 15) y la consolidacion de gates.
- R11 Reidentificacion por edad y nacionalidad en el microdato: mismas medidas que R3; la fecha de nacimiento no se
  exporta.
- R12 Pendientes operacionales postergados: hasta 0.9.0 siguen la distribucion central del Formulario, el correo con
  asunto fijo, el acceso por RDP y la apertura de sedes sin fecha efectiva; ninguno afecta la normalizacion ni la
  reportabilidad.

## 17. Definiciones pendientes
Ver la tabla "Definiciones pendientes" de `docs/BITACORA_0_8_0.md` (DI-01 a DI-15), con el gate o paso antes del cual
se necesita cada una y las respuestas ya registradas (DI-05 a DI-10, 2026-11-24; DI-05, DI-06, DI-09 y DI-10 quedan
para la hoja de ruta 0.9.0).
