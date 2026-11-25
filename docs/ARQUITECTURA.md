# SIGER 0.7.0 - arquitectura

Un JAR Spring Boot con React compilado y una base propia. Java 25 / Spring Boot 4.1.1 / Spring JDBC / Security /
Flyway; React 19 / TypeScript 6 / Vite 8; MySQL 8.4 propio en la VM y H2 (`MODE=MySQL`) para pruebas y uso local.
Sin microservicios, ORM ni infraestructura nueva. El SQL es el mismo en H2 y MySQL (la suite completa tambien corre
sobre MySQL 8.4 con `MySqlSuite`).

## Responsabilidades

- `Db`: envoltorio de JdbcTemplate con SQL directo (filas `Map<String,Object>` con claves en minusculas), sin capas JPA
  ni entidades, y el lock global (`Db.lock()`).
- `Access`: funciones y ambitos efectivos (titular/subrogante), lectura y escritura por registro, destinos validos
  (`receiverOf`/`destination`) y lectura por materia. Una sola regla resuelve una cuenta o todo el personal habilitado.
- `Queries` / `BoardQueries`: lecturas por GET (detalle autorizado, catalogo, workspace, administracion) y bandejas
  paginadas, contadores y dashboard, limitados siempre al alcance del actor.
- `BoardQueries.Visibility`: la misma visibilidad en SQL para bandejas, conteos, dashboard y buscador (equivalencia
  probada contra `Access` en `InstitutionalReaderTest`). La bandeja de comunicaciones (lista y cola "Ingresos por
  distribuir") agrega por fila `origin_region` (solo el Formulario), `risk` (primer CAMPO `riesgo` afirmativo; el valor
  no sale del servidor) y `to_distribute` (la expresion del filtro `com:pending`) para la marca de D23; las condiciones,
  los filtros, el orden, los conteos y la visibilidad no cambian.
- `Roles`: catalogo unico de funciones (alcance AMBITO, INSTITUCIONAL u OPCIONAL, grupo), publicado en `/api/catalog`.
- `Workflow`: envoltorio unico de comandos transaccionales e idempotentes (lock global, actor de sesion, clave,
  registro) y despacho por dominio.
- `IntakeCommands` / `RequirementCommands` / `CaseCommands` / `Attention`: flujo COM/SOL, REQ y flujos entre unidades,
  Casos y tareas, primera atencion (ficha 1.1 de la persona adulta con `AdultIdentity`). `Lifecycle` concentra las
  transiciones compartidas y escribe los hitos. `Rules`: reglas de cierre puras (cierre de Caso por nivel o jefatura,
  cierre de REQ, derivacion).
- `Personnel`: cuentas, perfil de funciones (`PROFILE_SET`), subrogancias, sedes (`SCOPE_*`) y su cobertura territorial
  (`SCOPE_COVERAGE`, `CENTRAL_COVERAGE`), avisos de administracion; `Holidays`: calendario habil. Solo TI.
- `Territory`: regla unica de "quien atiende hoy una region": sede ACTIVA por su region fisica o por cobertura abierta,
  y nivel central por su cobertura; una sede PROYECTADA solo se informa y una CERRADA no atiende. Calcula el efecto
  territorial con la confirmacion `regiones_sin_sede` en `SCOPE_COVERAGE`, `CENTRAL_COVERAGE`, `SCOPE_CLOSE` y
  `SCOPE_UPDATE` con cambio de region, y el aviso permanente `REGION_SIN_SEDE`. Vista previa sin escrituras ni
  auditoria: `POST /api/administration/territorial-effect` (`Personnel.territorialPreview`, solo TI).
- `OriginRegion`: regla unica, en Java y en SQL portable, de la region de origen de la persona: registrada, vigente y
  corregida (ficha, Formulario y, desde 0.8.0, la ficha de ingreso de la COM manual). La usan los reportes, la
  exportacion, la ficha y la region propuesta del selector (`Access.suggestedRegion`). Los dias habiles siguen con la
  region de la sede (D22).
- `DeclaredPerson` / `IntakeRecord` (0.8.0, E03): persona declarada canonica (`persona-1`) con las reglas de la ficha
  1.1 y del Formulario, compartida por la ficha 1.2 y la ficha de ingreso de la COM manual; resumen de cambios
  (complemento o rectificacion) calculado por el servidor. `IntakeRecord` guarda la ficha de ingreso (unica fuente de
  esas COM: columnas de resumen derivadas, sin filas en `communication_person`) y atiende `COM_COMPLETE`. Desde E05
  `COM_CREATE` y `COM_IMMEDIATE` exigen la ficha de ingreso (400 sin ella); las COM anteriores siguen legibles.
- `StatValues` / `StatProposals` (0.8.0, E05): clasificacion estadistica de la COM (tipo de solicitud, tipo de
  requirente y via de ingreso) en `stat_event`, solo inserciones; regla unica en Java y SQL portable del valor vigente,
  a una fecha y del conjunto de las variables multiples; via derivada del canal calculada sin escribir; propuestas de
  la regla version 1 desde los datos estructurados del Formulario, que nunca cuentan como informadas. `STAT_CLASSIFY` y
  el bloque `stat` de los comandos de ingreso y distribucion; facultad `Access.statWritable`, inmutabilidad de lo
  descartado, vinculado o cerrado (D35) y origen RETROSPECTIVA tras la reapertura existente (DI-07).
- `Rights` (0.8.0, E06): derechos, contexto, caracterizacion, rol del Estado y grupos prioritarios de cada REQ y Caso
  (`RIGHTS_CLASSIFY`, eventos ADD/REMOVE/STATUS en `stat_event`), con facultad de gestion (`reqMain` o `caseWrite`),
  inmutabilidad del registro cerrado (D35) y consolidado del requerimiento solo con lo que la cuenta lee. Las
  actuaciones se tipifican en las constancias existentes (`Lifecycle.note`: tipo de actuacion e instituciones), las
  tareas registran el compromiso sin volverse actuaciones y el resultado de la primera atencion tiene una sola fuente
  (`requirement.immediate_result`, con carga historica desde su constancia).
- `TerritorialDims` / `PersonDims` (0.8.0, E07): territorio al ingreso, al constituirse el REQ, vigente y a una
  fecha. Consulta hitos, cobertura histórica y estado de sede según auditoría. Región y comuna por fuente, edad
  cumplida en Chile, tramo del catálogo vigente y nacionalidad estructurada. La proyección por versión conserva
  solo los datos estadísticos. El Formulario se consulta desde sus tablas normalizadas.
- `StatCompleteness` (E07): una regla alimenta bloqueos visibles y valida el cierre del REQ, la resolución de primera
  atención y el descarte duplicado. Suma la clasificación del REQ y sus Casos para saber si una vulneración está
  completa. La región del requirente se mide como calidad y la propuesta sigue pendiente hasta su confirmación.
- `StatSource` / `StatEngine` (E08): motor estadístico nativo (`POST /api/stats/query`, contrato en
  `CONTRATO_STATS_0_8_0.md`). Una fila por COM y relaciones aparte para lo multivaluado, agregadas en SQL dentro del
  alcance de REPORTES; porcentajes, comparaciones, rótulos y avisos en Java. Corte "al constituirse el REQ" al fin del
  comando que lo creó; `Workflow` espera a que el reloj supere el fin del comando anterior y evita empates.
- `PersonIndex` / `PersonSearch`: indice de coincidencias de personas (derivado, reconstruible) y busqueda por RUN,
  documento, nombre o folio con auditoria HMAC opcional del termino.
- `Milestones` / `BusinessCalendar` / `Reports` / `Csv`: hitos de cada transicion (y su carga historica), dias
  habiles en hora de Chile, reportes agregados y exportacion CSV sin datos ciudadanos.
- `Documents` / `DocumentScanner`: originales privados en cuarentena, analisis antivirus para liberar y descarga con
  nombre neutro. `OriginRelease`: liberacion por analisis en origen del Formulario (D17), leida una vez al arrancar
  desde su archivo de configuracion; rige hasta que se configure un motor admitido o se desactive, falla cerrado si es
  invalida y libera solo los originales del Formulario con SHA-256 verificado.
- `Workflow.conserveExternal` / `ExternalOriginal` / `ExternalProjection` / `FormularioKeys`: entrada interna de las
  comunicaciones externas del Formulario Publico (no es un comando de `/api/commands`): valida el contrato, recalcula
  huellas y verifica el sello Ed25519 antes de escribir; el original firmado queda inmutable y la proyeccion se calcula
  una vez al crear.
- `FormularioPull` / `FormularioConsumer` / `FormularioClient` / `FormularioReception` / `FormularioAutomatic`:
  consumidor PULL/ACK del Formulario (manual de una pasada o automatico con lease SQL propio), con la identidad tecnica
  `sistema:formulario-publico`; confirma solo tras el COMMIT. `MailOutbox` / `MailWorker` / `GraphMail`: correo por
  Microsoft Graph encolado en el mismo COMMIT. `GraphCheck`: prueba controlada de Graph sin web ni tareas, que no toca
  la base ni la cola. `Integrations` / `IntegrationWatchdog`: pausa auditada de TI, estado de `/api/health` y latidos
  vencidos. Todas inactivas por defecto.
- `Bootstrap` / `TiRecovery` / `MigrationGuard`: semillas sinteticas o inicializacion institucional, rutinas de
  actualizacion con marcador, recuperacion auditada de TI en un proceso sin web y rechazo de migrar un esquema ajeno.
  `ServiceStop`: parada ordenada del servicio al aparecer `siger.stop-file`. `TaskReminders`: recordatorios programados.
  `DistributionNotices` (0.7.0, D24): tarea programada aparte de la recepcion que avisa una sola vez a distribucion
  (`Access.notifyDistributors`, con su correo por `MailOutbox` si Graph esta activo) cada COM del Formulario por
  distribuir; la marca de "ya avisado" es el propio aviso, sin migracion.
- `Api` / `ApiErrors` / `SecurityConfig` / `Input` / `Problem`: HTTP, sesion, CSRF, validacion de entrada y errores
  sin datos ciudadanos. Actor del servidor, nunca un rol enviado por el cliente. Texto de busqueda solo en cuerpos POST.

## Persistencia aditiva

V1-V10 de versiones anteriores se conservan. 0.5.0 agrega: V11 (sedes: region, estado, apertura y `revision` de
`org_scope`; materia sugerida de la SOL y materia del REQ), V12 (`person_reference`), V13 (`business_holiday`) y V14
(`milestone`, `application_marker.amount`). Las rutinas de actualizacion (supresion de AUTORIDAD, conversion de las
Coordinaciones regionales, indice de personas, hitos historicos) corren una sola vez al arrancar, por lotes con el lock
global, y dejan un marcador.

0.6.0 no agrega migraciones. 0.7.0 agrega V15 (`territorial_coverage`: periodos de cobertura de cada sede y del nivel
central, con inicio, termino, actor y fundamento; `scope_id` nulo = nivel central; terminar no borra) y V16
(`attention_revision.region_code`: region de cada version de la ficha, escrita al crearla; carga inicial por lotes con
el lock global y marcador `REGION_FICHA_V16`, tambien con `migrate-only`).

0.8.0 agrega V17 (catalogos estadisticos versionados, E01: `stat_catalog_version`, `stat_catalog_item` y
`stat_catalog_mapping`; una sola version VIGENTE por catalogo garantizada por el UNIQUE de `current_catalog`, que solo
tiene valor mientras la version esta VIGENTE). TI los importa y activa con fundamento; ninguna migracion los siembra.
Desde E02 TI lo hace en la pantalla `#catalogos` (Administracion > Catalogos estadisticos) con los archivos de
referencia de `deploy/config-ejemplo/catalogos/` (propuesta version 1 de los 12 catalogos y modelo de derechos 2.0 de
2022, completados en E06 con la respuesta propuesta a DI-01 a DI-04, con fuentes y huellas en su `LEEME.txt`), que viajan en el paquete dentro de `config-ejemplo\`. La
funcion `GESTION_ESTADISTICA` (ambito opcional, exige REPORTES en el mismo alcance, incompatible con TI; D43) se concede
desde E02 y habilita cortes, series y vistas compartidas a partir de E10 y E11.
V18 (E03): `attention_revision.change_summary` (resumen de cambios de cada version de la ficha; carga de las versiones
existentes con el marcador `RESUMEN_CAMBIOS_V18`, tambien con `migrate-only`) e `intake_record`/`intake_revision`
(ficha de ingreso de la COM manual con sus versiones, region del presentador y resumen de cambios).
V19 (E05): `stat_event` (eventos de clasificacion estadistica; ninguna migracion los escribe).
V20 (E06): `stat_catalog_item.description`, `management_note.action_type`/`action_catalog_version`, `note_institution`,
`task_item.action_type`/`action_catalog_version` y `requirement.immediate_result`/`immediate_result_reconstructed`
(carga historica con el marcador `RESULTADO_INMEDIATO_V20`, tambien con `migrate-only`).
V21 (E07): `stat_declared_version` y `stat_declared_person` proyectan las fichas originales sin guardar las fechas de
nacimiento. `DIMENSIONES_V21` identifica la carga histórica idempotente, que también corre con `migrate-only`.
V22 (E08): indices de las fechas de análisis y del resultado de los comandos, con su evidencia de EXPLAIN en la bitácora.
V1 a V22 no se modifican nunca; un cambio nuevo se agrega como `V23__...` aditivo, compatible con H2 y MySQL. Ninguna
migracion siembra sedes, regiones, feriados, catalogos ni personas reales. La actualizacion desde 0.4.0 y desde 0.7.0
se prueba sobre MySQL 8.4 con volcados sinteticos (`MySql84UpgradeTest`, `MySql84Upgrade070Test`).

La COM conserva original y folio; el REQ sigue siendo el padre de los Casos. El indice de personas no es un registro
maestro: no fusiona identidades. La ficha JSON se valida con lista cerrada de campos y se entrega solo en detalle
autorizado.

## Frontend

Organizacion: `app/` (contexto, rutas por hash, menu, armazon), `lib/` (HTTP, accesores, funciones, catalogo, campos,
destinos), `components/` (dialogo de comandos, selector de destino, primitivas) y `features/` por pantalla (`attention`,
`intake`, `detail`, `dashboard`, `worklist`, `search`, `reports`, `admin`). La logica pura de cada pantalla vive en un
`.ts` con su prueba en `frontend/tests` (`node --test`, sin dependencias nuevas). El texto de busqueda vive solo en
`sessionStorage` de la pestana y se borra al cerrar o perder la sesion; nunca en la ruta. La version visible sale de
`package.json` (`__SIGER_VERSION__`). React va en un chunk propio que se carga al inicio, sin carga diferida.

Desde 0.7.0, `ActionDialog` admite el campo `checklist` y `review` opcional, con la logica en `lib/actionData.ts`: las
ordenes territoriales de Estructura (`structureModel.ts: territorialOrders`) piden la vista previa antes de confirmar.
El selector lista una sede tambien bajo cada region de su cobertura vigente. `lib/regionMark.ts` y
`components/RegionMark.tsx` arman la marca de D23 en la lista de comunicaciones y en la cola de Inicio.

Desde 0.8.0 (E04, D33), `lib/person/` (reglas de identidad y contacto, catalogos de territorio y paises, modelo de la
persona canonica `persona-1`) y `components/person/` (controles, tarjeta de persona con "Más datos", instituciones,
lugar de los hechos y seleccion de codigos del listado vigente) son comunes a la ficha 1.2 de primera atencion
(`features/attention/fichaModel.ts`) y a la ficha de ingreso de la COM manual (`features/intake/intakeModel.ts`, panel
en la ficha de la COM). Los archivos `territorio.json` y `paises.json` siguen en `features/attention/adult/` (huellas de
E00); los modulos de esa carpeta reexportan los de `lib/person/`.

Presentacion: sistema visual propio sin dependencias nuevas (tokens CSS, iconos propios, Cabin OFL y escudo
institucional servidos desde el JAR), con barra lateral por tipo de trabajo, paleta Ctrl+K, fichas con cadena
COM->SOL->REQ->Caso y version movil. Decisiones en `docs/DISENO_0_4_0.md`.

## Operacion

Los scripts (Windows PowerShell 5.1) ejecutan Java por ruta absoluta, MySQL en instancia independiente, configuracion,
documentos y base fuera del JAR. `Build.ps1` toma la version de `backend/pom.xml` (el frontend debe declarar la misma)
y el esquema de las migraciones, y los escribe en el manifiesto; `Start.ps1` y `Test-Service.ps1` exigen que
`/api/health` responda esa version y ese esquema. Hashes mediante .NET; `Check-Scripts.ps1` en cada build. Sin cambio
persistente de PATH/JAVA_HOME, firewall ni servicios ajenos.

La instancia institucional se inicializa y opera con una cuenta de servicio sin privilegios administrativos (duena de
DPAPI); un administrador solo ejecuta `Install-Service.ps1`, que registra los servicios `SIGER` y `SIGERMySQL`
(`SigerService.cs`, parada por `run\stop.request`). Credencial y prueba de Graph: `Import-GraphCredential.ps1` y
`Test-GraphMail.ps1`. Liberacion por analisis en origen: `Set-OriginRelease.ps1`. Rotacion de la cuenta de servicio:
`Export-InstanceConfig.ps1`, `Test-InstanceConfig.ps1`, `Import-InstanceConfig.ps1` y `Set-ServicePassword.ps1`.
Salida a produccion (`docs/DESPLIEGUE_VM.md`): la validacion se cierra con `Close-Validation.ps1` y se elimina con
constancia con `Remove-Validation.ps1`.
