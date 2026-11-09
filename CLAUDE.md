# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

SIGER (antes SIGER A; Nexbyte, Defensoria de la Ninez de Chile) es un monolito Spring Boot 4.1.1 / Java 25 con el frontend React 19 + TypeScript 6 + Vite 8 empaquetado dentro del mismo JAR. Usa MySQL independiente en la VM y H2 para pruebas locales. Los nombres internos (paquete `cl.nexbyte.sigera`, esquema `siger_a_*`, raiz `D:\SIGER-A`, namespace `SIGER-A-2026`, cookie `SIGERA_SESSION`) se conservan por compatibilidad; lo visible dice SIGER. El consumidor PULL/ACK del Formulario (manual o automatico), el correo Graph y el servicio Windows existen pero estan inactivos salvo configuracion explicita (ver abajo). La documentacion y los mensajes al usuario estan en espanol.

## Comandos

Las herramientas son portables y viven en `.tools/` (`java25`, `apache-maven-3.9.11`, `node-v24.22.0-win-x64`). No se instalan globalmente ni se modifican PATH ni JAVA_HOME de forma persistente. Si faltan, ejecutar `scripts\Get-Tools.ps1`.

```powershell
# Build completo: chequea scripts, npm ci + tsc + vite build, Maven clean verify (con tests) y genera release\SIGER-<fecha>.zip
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -IncludeJava
#   -SkipChecks omite los tests (un build asi no acredita pruebas)

# Ejecutar en local (requiere backend\target\siger-a.jar): perfil local, H2 en var\local-db, http://localhost:18543
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-Local.ps1
```

Para iterar sin pasar por Build.ps1, en una sesion de PowerShell:

```powershell
$env:JAVA_HOME='C:\siger-a\.tools\java25'; $env:PATH="C:\siger-a\.tools\node-v24.22.0-win-x64;$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -B -ntp -f backend\pom.xml test                                   # todos los JUnit
.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=RulesTest                  # una clase
.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=Attention040Test#metodo    # un metodo
cd frontend; npm run typecheck   # tsc --noEmit (no hay linter configurado)
cd frontend; npm run build       # genera frontend\dist
cd frontend; npm run dev         # Vite en 127.0.0.1:5173, proxy de /api a 127.0.0.1:18543 (el backend debe estar corriendo)
```

`backend/pom.xml` copia `../frontend/dist` a `static/` en la fase `process-resources`. Por eso hay que ejecutar `npm run build` antes de `mvnw package`; si no, el JAR queda sin la UI o con una version anterior.

Los tests usan `@SpringBootTest` + `@ActiveProfiles("test")` (H2 en memoria `MODE=MySQL`, Flyway activo, sin semillas). `WorkflowTest` inserta por SQL su propio organigrama, cuentas y grants sinteticos y ejecuta comandos con `flow.run(...)`. Los que usan `siger.seed-demo=true` necesitan un nombre de H2 propio y `siger.data=./target/...-${random.uuid}`: el bootstrap no sobrescribe un `bootstrap-usuarios.csv` existente y fallaria al repetir sin `clean`.

## Arquitectura

El backend es un solo paquete plano `cl.nexbyte.sigera`, sin capas JPA ni entidades: SQL directo mediante `Db` (envoltorio de JdbcTemplate que devuelve `Map<String,Object>` con claves en minusculas, con helpers estaticos `n/s/b/nullableId`). El SQL debe funcionar igual en H2 `MODE=MySQL` y en MySQL.

**Toda escritura de negocio pasa por `POST /api/commands` → `Workflow.run`.** El comando es `{key: UUID, type, id, revision, data}`. Dentro de una sola transaccion, `run`:
1. toma un lock global (`Db.lock()`: `SELECT ... FROM operation_lock FOR UPDATE`);
2. resuelve el `Actor` desde la sesion (nunca desde el cliente) y bloquea si `must_change`;
3. aplica idempotencia con `command_log`: la misma clave y el mismo hash devuelven el resultado previo, y la misma clave con otros datos es un conflicto;
4. despacha por `type` al servicio del dominio: `IntakeCommands` (COM_*, SOL_*, LINK_*), `RequirementCommands` (REQ_*, FLOW_*, REOPEN_*), `CaseCommands` (CASE_*, NOTE_RECTIFY, TASK_*), `Attention` (ATTENTION_*), `Personnel` (STAFF_/GRANT_/SCOPE_/SUBSTITUTE_*, DOC_RELEASE);
5. registra el resultado.

Un comando nuevo se agrega al `switch` de `Workflow.run` y al `run()` del servicio de su dominio. Las transiciones compartidas (crear REQ o Caso, asignar, cerrar Caso, constancias, folios, listas de canales, materias y resultados) están en `Lifecycle`; hay que reutilizarlas, no duplicarlas. Las mutaciones usan concurrencia optimista: el cliente envia `revision` y se valida con `Db.revision(row, expected)`. Las acciones relevantes se registran con `db.audit(...)` en `audit_event`. El frontend reutiliza la misma `key` al reintentar una operacion incierta.

Las lecturas usan endpoints GET en `Api.java`: `Queries` (detalle autorizado, catalogo, workspace, administracion) y `BoardQueries` (bandejas paginadas `/worklist/{kind}`, contadores, dashboard). Los conteos se limitan siempre al alcance del actor.

**Permisos (`Access`)**: un `Actor` tiene `grants` (rol + ambito `org_scope`) vigentes. Una subrogancia activa traslada temporalmente al subrogante los grants del titular, en los mismos ambitos, y el titular conserva su cartera personal como PROFESIONAL. `actor(username)` y `enabledActors()` comparten `resolve()`. Para avisar o filtrar a "todos los que cumplen X" se usa `enabledActors()` o `notifyWhere`/`notifyReceivers`/`notifyHeads`, nunca un bucle de `actor()` por cuenta: corre dentro del lock global. Hay helpers como `head(scope)`, `professional(scope)`, `registrar(scope)`, `authority(scope)` y `solReceiver(...)`. Toda verificacion de permiso se hace en el servidor; el frontend (`lib/access.ts: has/headAt/authorityAt`) solo decide que botones mostrar.

**Errores**: `Problem(status, mensaje)`. `Problem.require` → 409, `Problem.denied()` → 403. `ApiErrors` devuelve `{message}`, audita las denegaciones 403/404 y nunca registra SQL ni datos ciudadanos en el log. Los mensajes van en espanol y son visibles al funcionario. `Input` valida la entrada (`text(key,max,required)`, `id`, `choice`, `instant`, `reason`) y no acepta estado, autor ni rol enviados por el navegador.

**`Rules`**: reglas de cierre puras (cierre de Caso por nivel o jefatura, cierre de REQ, derivacion), cubiertas por `RulesTest`. Solicitar termino (`Rules.closeRequest`, responsable) y confirmar el cierre (`Rules.closure`) son facultades distintas: la ficha del Caso expone `can_request_close`/`close_request_blockers` aparte de `can_close`/`closure_blockers`, y el frontend no debe inferir una de la otra. El origen del REQ (`solicitation_id` nulo = primera atencion directa; en el Caso, `req_origin`) se toma del servidor.

**`Attention`** (0.4.0): primera atencion progresiva sobre COM+REQ. Ficha 1.1 (1.0 sigue aceptada y legible): en modalidad GENERAL la persona adulta tiene nombres y apellidos separados, RUN con DV (validacion solo matematica) o documento extranjero, nacimiento opcional, nacionalidad del catalogo, celular `+569XXXXXXXX`, correo validado y region/comuna del catalogo del Formulario (`AdultIdentity`, `catalog/territorio.json` y `paises.json`, identicos a `frontend/src/features/attention/adult/`). Un valor igual al de la ficha vigente se conserva sin revalidarse (datos historicos); `presenter_name` e `identity_document` se derivan de las partes. NNA conserva sus campos libres. Pruebas frontend: `npm test` (node --test, sin dependencias nuevas). Comprende la ficha JSON validada con lista cerrada de campos (`attention_record`), versiones con autor y motivo (`attention_revision`) y entregas con aceptar/devolver/retirar (`attention_handoff`). Se ejecuta dentro de la transaccion de `Workflow`; no es un motor aparte.

**Comunicaciones externas (Formulario Publico via SMR)**: `Workflow.conserveExternal` es una entrada interna (no es un comando de `run()`; `/api/commands` la rechaza para cualquier sesion), y conserva el original sin descargar sus adjuntos. `ExternalOriginal.parse` valida el contrato (reglas portadas de `EntregaCompleta.validar` de SMR), recalcula huellas y verifica el sello Ed25519 con `FormularioKeys` (`siger.formulario-trusted-keys`; sin claves se rechaza todo) antes de escribir. El texto firmado queda inmutable en `communication_original`; `ExternalProjection` calcula una vez, al crear, la COM (sin textos de procedencia) y `external_person`/`external_attribute`/`external_field` (procedencia, discrepancias, diccionario SRCeI, edad al ingreso). Un reenvio idempotente no reproyecta. La ficha COM entrega la proyeccion solo tras `comForRead`, nunca el JSON original.

**Consumidor del Formulario (PULL/ACK, V8)**: inactivo salvo `siger.formulario.pull.enabled=true` y `run-once=true` en un proceso sin web y con `siger.scheduling=false` (`FormularioPull`, lanzado solo por `scripts/Invoke-FormularioPull.ps1`); sin planificador, endpoint ni comando de funcionario. `FormularioConsumer` hace un ciclo: concilia ACK pendientes, reclama una entrega (`FormularioClient`: HTTPS, sin redirecciones, Bearer solo al origen), valida con `ExternalOriginal.parse`, custodia los originales por streaming con tamano y SHA-256 firmados (`Documents.custodyExternal`, clave derivada de UUID), y `FormularioReception.register` crea en un COMMIT la COM, los `document_item` INGRESO en cuarentena (+ `external_document`), el comprobante `FormularioAck` y la reserva con ACK pendiente (`formulario_reception`). Confirma solo tras el COMMIT y una nueva verificacion de la custodia; despues del COMMIT nunca informa fallo. La version del ACK sigue al canal (NNA 1.1, publico 1.0). Registra con la identidad tecnica `sistema:formulario-publico` (`staff.technical`): sin sesion, sin funciones, fuera de listas y de la administracion de cuentas. El folio del comprobante exige `siger.folio-prefix` `[A-Z][A-Z0-9]{1,15}`. Solo un consumidor por cola: el de SMR debe estar detenido (no lo verifica SIGER).

**Operacion permanente (V9)**: `siger.formulario.pull.automatic=true` (exige `single-consumer-confirmed=true`; excluyente con `run-once`) crea `FormularioAutomatic` en el servicio: mismo ciclo de `FormularioConsumer` con lease SQL propio del proceso (`formulario_consumer_lock`, renovado por latido y conservado entre ciclos; si se pierde, el ciclo falla cerrado), drenaje, espera 10 s a 5 min y latido en `integration_status`. `Integrations` maneja la pausa auditada de TI (`integration_control`, comandos `INTEGRATION_PAUSE`/`INTEGRATION_RESUME` en `Personnel`) y el codigo de estado de `/api/health`. Correo: `Access.notice` encola en `MailOutbox` (mismo COMMIT) solo con `siger.mail.mode=GRAPH`; `MailWorker` (solo con `siger.scheduling=true`) envia con `GraphMail` (OAuth client_credentials + sendMail, endpoints fijos) registrando el intento antes de la red (`mail_attempt`); `AMBIGUO` nunca se reenvia solo (TI: `MAIL_RESOLVE`). Exige `siger.mail.allowed-domains`. `ServiceStop` cierra el contexto al aparecer `siger.stop-file` (parada ordenada del servicio). Las pruebas usan productor y Graph falsos; nunca red real.

**`Documents`**: originales privados bajo `siger.data` (por defecto `./var`), en cuarentena y separados de los documentos de gestion. Las descargas se sirven como attachment con `Content-Security-Policy: sandbox`. Liberar (`DOC_RELEASE`, TI) exige un analisis previo del servidor (`POST /api/documents/{id}/scan` -> `DocumentScanner`, ClamAV o Defender, motor operativo y firmas recientes, SHA-256 verificado antes y despues; evidencia en `document_scan`, V10): ultimo analisis LIMPIO, reciente (1 h), mismos bytes, nunca una deteccion acreditada (ClamAV: firma distinta de Heuristics.Limits.Exceeded; Defender: texto "found N threats", porque MpCmdRun tambien devuelve 2 al fallar). La revision manual sin motor solo existe con `siger.documents.synthetic-release=true` (instancias de pruebas). `IntegrationWatchdog` y `Integrations.stale` detectan latidos vencidos (`SIN_LATIDO`): cada ciclo registra antes de empezar el plazo en que promete terminar.

**Arranque**: `Bootstrap` crea semillas sinteticas si `siger.seed-demo=true` y la tabla `staff` esta vacia, y escribe las claves iniciales en `var/bootstrap-usuarios.csv`. Con `siger.bootstrap-file` (excluyente con seed-demo) aplica en su lugar la inicializacion institucional: solo los ambitos reales del archivo, una cuenta TI y una autoridad habilitante. `SecurityConfig` usa sesion con CSRF (`HttpSessionCsrfTokenRepository`) y login por `POST /api/login`. `TaskReminders` es un `@Scheduled`. La etiqueta de entorno visible sale de `siger.environment-label` (vacia en la VM; el perfil `local` la muestra).

**Frontend** (`frontend/src`, sin router ni librerias de estado):
- `App.tsx` maneja el ruteo por hash (`#inicio`, `#COM/123`, `#REQ/…`, `#CAS/…`, `#FLUJO/…`; ver `app/routes.ts`) y la carga de sesion, catalogo y workspace. `app/menu.ts` arma la navegacion por funcion.
- `lib/api.ts` incluye `request()` (agrega el header CSRF y emite el evento `siger-session` ante 401/428), `command()` y las llamadas de documentos. `lib/rows.ts` tiene los accesores de fila (`text`, `number`…) y las fechas (`when`, `localTime`). `lib/catalog.ts` resuelve ambitos y personas. `lib/fields.ts` contiene los campos de dialogo y las listas de valores; usar `fixedSelect`, `decision` y `choose` en vez de literales.
- Un comando se abre con `ctx.act(...)` y se confirma en `components/ActionDialog`, que conserva la clave idempotente por dialogo.
- Las pantallas viven en `features/`: `attention` (ficha progresiva; `schema.ts` replica la lista cerrada de campos de `Attention.normalize` en el backend), `intake`, `detail` (una ficha por tipo, mas `actions.ts`), `dashboard`, `worklist` y `admin`.
- Al partir JSX en varias lineas se pierden los espacios entre elementos de la misma linea (`</strong> <Status/>`). Esos casos quedan en una sola linea o con `{' '}`.
- No se guardan datos sensibles en localStorage.
- Diseno visual (decisiones en `docs/DISENO_0_4_0.md`): tokens en `src/styles/tokens.css` y estilos en `base`, `layout`, `components` y `screens` (sin librerias de UI). El armazon esta en `app/Shell.tsx` (barra lateral, cajon movil y avisos) y `app/CommandPalette.tsx` (Ctrl+K, rutas `#req?q=…`). Las piezas compartidas son `components/Primitives.tsx` (PageHeader, Panel, Callout, EmptyState, Facts, Busy), `Icon.tsx` (iconos propios), `Choice.tsx` (ChipRadios, ChoiceCards) y `useExit.ts` (salida animada). En las fichas: `features/detail/parts.tsx` (DetailHeader con la cadena COM→SOL→REQ→Caso, ActionBar y DetailGrid).
- Los textos visibles llevan ortografia completa. Los codigos que se envian al servidor no cambian: las etiquetas salen de `lib/labels.ts` (`humanize`, `labeled`, `ACTION_LABELS`). Una sola accion `primary` por grupo. El estado nunca se comunica solo por color. Respetar `prefers-reduced-motion` y el contraste (`--field-line` para los bordes de campos).
- El escudo (`src/assets/emblema-*.png`, derivado sin alterar de `brand/`) va siempre sobre fondo blanco. Cabin (OFL) se sirve desde el JAR, sin fuentes remotas. La CSP del backend no admite estilos ni recursos externos.

## Base de datos y perfiles

- Migraciones Flyway en `backend/src/main/resources/db/migration`. **V1 a V10 no se modifican nunca**; cada cambio se agrega como `V11__...` aditivo, compatible con H2 y MySQL.
- `MigrationGuard` rechaza migrar MySQL si el esquema no coincide con `siger_a_*` o si detecta tablas de SMR o del SIGER anterior, y solo admite los motores MySQL/H2.
- Hay tres perfiles. `application.properties` (VM) toma todo de variables `SIGER_*` y mantiene Flyway desactivado salvo `SIGER_MIGRATE=true`. `local` usa H2 en archivo `./var/local-db` con semillas y sin TLS. `test` usa H2 en memoria.
- La version `0.4.0` esta fija en `backend/pom.xml`, `frontend/package.json`, `Api.health()` y el manifiesto de `scripts/Build.ps1`. Al subir de version hay que cambiarla en todos esos lugares.
- `scripts/Start.ps1` exige que el health responda `schema -eq 10` y la version del manifiesto. Cada migracion nueva obliga a actualizar ese chequeo (V6: original de Comunicacion externa; V7: su proyeccion normalizada; V8: consumidor PULL/ACK del Formulario; V9: operacion continua, outbox de correo y control de integraciones; V10: evidencia de analisis antivirus).
- El pool Hikari se ajusta con `SIGER_DB_POOL_MAX` (por defecto 6). Un comando retiene su conexion mientras espera el lock global.

## Reglas de dominio que no deben romperse

La fuente de verdad es `docs/DECISIONES_FUNCIONALES.md`; el alcance y lo pendiente estan en `docs/ALCANCE_Y_PENDIENTES.md`. Los invariantes clave son:
- La cadena es COM (lo recibido) → SOL (a una jefatura) → REQ (nace al asumir la SOL) → Caso(s) bajo el mismo REQ. Un ingreso externo por clasificar no crea REQ ni Caso.
- La primera atencion directa crea COM+REQ en un solo acto, sin SOL ni Caso. Entregar no transfiere la jefatura principal ni crea un Caso. Para dar seguimiento, la jefatura abre un Caso en el mismo REQ.
- La complejidad se asigna por REQ (BAJA/MEDIANA/ALTA), y no tener clasificacion no equivale a BAJA. El profesional solo cierra Casos BAJA; los demas niveles pasan a revision de jefatura. El subrogante no confirma su propio Caso.
- Una tarea no terminal bloquea el cierre del Caso. Cerrar un Caso no cierra el REQ, que la jefatura principal cierra de forma expresa.
- Una derivacion aceptada crea el sucesor y cierra el origen en la misma transaccion. Una colaboracion crea un Caso paralelo. Una transferencia cambia solo al principal.
- Los roles son concesiones dentro de un ambito. TI no obtiene acceso a expedientes por administrar cuentas, y la historia conserva al actor real.

## Operacion y archivos sensibles

- `var/` (DB local, documentos, `bootstrap-usuarios.csv`), `release/` y `.tools/` estan en `.gitignore`. Se conservan entre actualizaciones y no se borran ni se comparten. Tampoco se borran `frontend/package-lock.json` ni `frontend/node_modules`.
- H2 abre en solo lectura, sin avisar, un archivo que el proceso no puede escribir (por ejemplo, `var\local-db.mv.db` creado desde una consola elevada y usado luego sin elevacion). `Bootstrap` llama a `Db.requireWritable` y aborta el arranque en ese caso, y `Run-Local.ps1` lo verifica antes de iniciar. El ingreso solo queda abierto si se audita (`SecurityConfig.loginSucceeded`). Detalle en `docs/INCIDENTE_LOGIN_0_4_0.md`.
- Una instancia en ejecucion bloquea `backend\target\siger-a.jar` y `mvnw clean` falla. No se detiene la instancia del usuario: se compila en una copia del proyecto.
- Identidades: la instancia institucional se inicializa y opera con una cuenta de servicio SIN privilegios administrativos (duena de DPAPI, `ownerSid` en `instance.json`); un administrador solo ejecuta `Install-Service.ps1`, que verifica esa cuenta con `Test-ServiceIdentity.ps1` y le concede control de sus dos servicios. `Init-Server.ps1 -Mode Institucional|Pruebas` (obligatorio).
- Servicio Windows: `Install-Service.ps1` compila `SigerService.cs` (C# 5, Job Object, parada por `run\stop.request`, modo `--probar` en consola) y registra `SIGER` y `SIGERMySQL` con la cuenta de servicio; `Test-Service.ps1` prueba arranque, parada y recuperacion en la VM; `Service.ps1` es el proceso del servicio. `Environment.ps1` lee `formulario-pull.json` (`mode` MANUAL/AUTOMATICO) y `correo-graph.json`; `Import-Integrations.ps1` reutiliza credenciales de SMR por DPAPI sin imprimirlas.
- Los scripts de `scripts/` deben funcionar en Windows PowerShell 5.1. `Check-Scripts.ps1` rechaza caracteres de control (un `\a` invisible ya rompio una ruta): escribir scripts con la herramienta de archivos, no con heredocs de bash que reducen `\\`. `Check-Scripts.ps1` los parsea en cada build. Evitar `Get-FileHash` (usar `Hash.ps1` / `Get-SigerFileHash`), asignar `$HOME` y `ConvertFrom-Json -AsHashtable`.
- `SOURCE_SHA256.txt` es el manifiesto de hashes de la entrega fuente y queda desactualizado al editar archivos.
- Instalacion en la VM: `docs/INSTALACION_VM.md`; despliegue abreviado (validacion con `purpose` VALIDACION que nunca recibe entregas reales, cierre del piloto con `Close-Validation.ps1`, produccion con el mismo JAR, paquete de secretos `Export/Import-SecretPackage.ps1`, sonda `-SoloComprobar`, `Set-Integration.ps1`): `docs/DESPLIEGUE_VM.md`. No se toca Java 8, MySQL80, Tomcat, SQL Server, SMR ni el firewall existentes.
- Las pruebas manuales por version estan en `docs/PRUEBAS_MANUALES_0_4_0.md`, y la evidencia de verificacion en `docs/VERIFICACION.md`. La consolidacion del backend (cambios, mediciones y pendientes) esta en `docs/CONSOLIDACION_0_4_0.md`.
