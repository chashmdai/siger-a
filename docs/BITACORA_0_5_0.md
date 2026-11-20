# SIGER 0.5.0 - bitacora de gates

Estado vivo del trabajo de `docs/PLAN_0_5_0.md`. Cada sesion de Claude Code toma el primer gate ABIERTO, lo cierra aqui con el formato de la seccion 7 del plan y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado y con commit), BLOQUEADO (detenido; ver su registro).

## Estado

| Gate | Titulo | Estado |
|---|---|---|
| G00 | Linea base | CERRADO |
| G01 | Catalogo de roles y TI administrador | CERRADO |
| G02 | Comando PROFILE_SET | CERRADO |
| G03 | Supresion de AUTORIDAD e inicializacion | CERRADO |
| G04 | Perfil de cuenta en la interfaz | ABIERTO |
| G05 | Consulta institucional: lectura | ABIERTO |
| G06 | Consulta institucional: bandejas e interfaz | ABIERTO |
| G07 | Indice de personas (V11) | ABIERTO |
| G08 | Buscador | ABIERTO |
| G09 | Calendario habil (V12) | ABIERTO |
| G10 | Hitos (V13) | ABIERTO |
| G11 | Reportes: calculo | ABIERTO |
| G12 | Reportes: interfaz y exportacion | ABIERTO |
| G13 | Integracion MySQL | ABIERTO |
| G14 | Cierre de version | ABIERTO |

## Antecedentes para G00 (revision previa del codigo, sin ejecutar)

- Base `cd21f6e` (0.4.0, esquema V10). El README declara 77 pruebas JUnit en 20 clases; en el codigo hay 83 metodos `@Test` en 22 archivos de prueba (`FormularioDocs` no tiene pruebas). G00 registra el conteo real ejecutado.
- `CLAUDE.md` todavia indica que la version y el esquema se cambian a mano en varios lugares; desde `cd21f6e`, `Build.ps1` toma la version de `backend/pom.xml` (el frontend debe coincidir) y deriva el esquema de las migraciones. Se corrige en G14 (o antes, si un gate lo necesita).
- Los scripts de `scripts/` son Windows PowerShell 5.1 con herramientas portables en `.tools/`; en Linux se usan `./mvnw` y `npm` directamente (seccion 2 del plan).

## Registros de cierre

### G00 - Linea base   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G00: linea base`)
- Hecho: sin cambios de codigo. Suite completa ejecutada sobre `cd21f6e` en Linux (CachyOS). `PLAN_0_5_0.md` y `BITACORA_0_5_0.md` movidos de la raiz a `docs/` (estaban sin seguimiento). Nueva seccion "Trabajo en curso 0.5.0" en `CLAUDE.md`.
- Archivos clave: `CLAUDE.md`, `docs/PLAN_0_5_0.md`, `docs/BITACORA_0_5_0.md`
- Verificacion: JUnit 79 ok + 1 omitida (`DocumentReleaseTest.defenderNoOperativoFallaCerrado`: sin Defender) + 2 de `MySql84IntegrationTest` omitidas en `@BeforeAll` (Maven las cuenta como 0) = 82 metodos `@Test` en 21 clases; frontend 10 ok; typecheck ok; build ok; humo no aplica
- Herramientas: Temurin 25.0.5 (`JAVA_HOME` vacio, `java` en PATH); Maven 3.9.11 del wrapper; Node 26.13.1 / npm 12.2.3; `pwsh` en `/usr/bin/pwsh`
- Desvios del plan: Node 26 en vez de 24 (aprobado por el responsable): `npm ci` avisa EBADENGINE (`engines` `>=24 <25`), pero no falla. El build de entrega sigue siendo el de Windows con Node 24 portable (G14).
- Hallazgos para gates siguientes:
  - Los 83 `@Test` de los antecedentes incluian un `@TestConfiguration`; el numero real es 82. Contar con `grep -rwE "@Test"` excluyendo `@TestConfiguration`, o con los informes de surefire.
  - `mvnw` esta en git con modo 100644: `./mvnw` da "permiso denegado". Usar `sh ./mvnw ...`. No se cambio el modo.
  - `pwsh` esta disponible: G03 puede intentar `Check-Scripts.ps1` aqui (verificar compatibilidad con PS 5.1 aparte).
  - `backend/target` conserva carpetas de ejecuciones anteriores (`institucional-*`, `formulario-pull-*`); son inocuas.
- Pendientes: corregir en `CLAUDE.md` el texto sobre version y esquema manuales -> G14 (o antes si un gate lo necesita); README declara 77 JUnit en 20 clases -> G14
- Verificacion manual sugerida al usuario: no aplica

### G01 - Catalogo de roles y TI administrador   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G01: catalogo de roles y TI administrador`)
- Hecho: catalogo unico `Roles` (codigo, alcance AMBITO/INSTITUCIONAL, grupo; sin AUTORIDAD), publicado en `/api/catalog` como `roles`. `GRANT_CREATE`/`GRANT_REVOKE`/`DESIGNATE`/`SUBSTITUTE_ACTIVATE`/`SUBSTITUTE_END` exigen TI (el titular termina la suya); `Actor.authority()` eliminado. Varios distribuidores (rechazo de periodo superpuesto por persona+rol+ambito, para todo rol); TI concedible e incompatible con otra funcion en ambos sentidos; `GRANT_REVOKE` rechaza la ya revocada y audita el rol.
- Agregado por el responsable: `GRANT_CREATE`, `GRANT_REVOKE` y `STAFF_UPDATE` nunca dejan la instancia sin una TI habilitada, vigente y sin termino (`Personnel.keepTi`, postcondicion dentro de la transaccion de `Workflow`; sin ella vigente, solo procede la concesion que la repone).
- `Queries`: `workspace` (TI ve todas las designaciones/subrogancias), `administration` (solo TI o AUDITORIA; TI recibe users con `security_version`, grants vigentes e historicos, `designations` y `substitutions`; AUDITORIA ve su alcance + `USUARIO/AMBITO/SUBROGANCIA`). Dashboard: seccion `authority` fusionada en `technical`. Seccion "Precision 0.5.0" en `DECISIONES_FUNCIONALES.md` (puntos 13 y 14 anotados).
- Archivos clave: `Roles.java`, `Personnel.java`, `Queries.java`, `BoardQueries.java`, `Access.java`, `TiAdministrationTest.java`, `docs/DECISIONES_FUNCIONALES.md`, `CLAUDE.md`
- Verificacion: JUnit 81 ok + 1 omitida (antes 79 + 1; +2 de `TiAdministrationTest`; Maven informa 82 ejecutadas); frontend 10 ok; typecheck ok; build ok (frontend sin cambios); humo no aplica (sin cambios de interfaz)
- Desvios del plan: se elimino la regla "distribucion central no se concede desde un ambito regional" (aprobado). En las funciones institucionales se ignora `scope_id` y la auditoria queda sin ambito. Se actualizaron ya en G01 `AccessResolutionTest`, `Version031Test` e `InstitutionalBootstrapTest` (fallaban con `autoridad`).
- Hallazgos para gates siguientes:
  - G04: entre G01 y G04 la interfaz no permite conceder funciones ni activar subrogancias: `Administration.tsx` y `Substitutions.tsx` muestran esas acciones solo a AUTORIDAD (`authorityAt`), que ya no tiene efecto; TI solo puede hacerlo por API. `Dashboard.tsx` conserva `authority: []` en `QUEUES_BY_SECTION`; `GRANTABLE_ROLES` debe salir de `catalog.roles`.
  - G02: reutilizar `Personnel.tiCompatible`, `keepTi` y la consulta de superposicion de `GRANT_CREATE`; `administration.users` ya trae `security_version` (revision de `PROFILE_SET`).
  - G03: `Bootstrap` institucional aun rechaza mas de un distribuidor (`distributors>1`) y crea la autoridad; `InstitutionalStaffTest` (caso `twoDistributors`), `FormularioConsumerTest:176`, `ExternalOriginalTest:125` y `MySql84IntegrationTest:117` siguen con AUTORIDAD (no fallan). La cuenta demo `autoridad` existe sin facultades. Las cuentas que hoy combinen TI con otra funcion no se tocan: G03 las lista.
  - `TiAdministrationTest` usa `@Order`: el metodo 2 deja `ti` con TI permanente al terminar (restaura en `finally`).
- Pendientes: interfaz de concesion y subrogancias para TI -> G04; supresion de AUTORIDAD en datos y bootstrap -> G03
- Verificacion manual sugerida al usuario: no aplica (sin cambios de interfaz hasta G04)

### G02 - Comando PROFILE_SET   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G02: comando PROFILE_SET`)
- Hecho: comando `PROFILE_SET` (TI; id = cuenta; `revision` = `staff.security_version`; `data={grants:[{role,scope_id?,from_at?,until_at?}],reason}`) en `Personnel.profile`. Compara por (rol, ambito) con las funciones vigentes o programadas: coincide si tiene el mismo termino (sin `until_at` = sin termino) y, si trae `from_at`, el mismo inicio; conserva lo que coincide (misma fila), revoca lo demas y otorga lo nuevo (desde `from_at` o ahora). Cambiar la vigencia = revocar + otorgar. Un evento `FUNCION_OTORGADA`/`FUNCION_REVOCADA` por cambio, un solo `epoch`, `keepTi` como postcondicion. Todo se valida antes de escribir.
- Decisiones del responsable: D2 se evalua sobre el estado final (un acto puede pasar de {Profesional} a {TI}); perfil vacio admitido (revoca todo, salvo `keepTi`); cuenta deshabilitada solo admite revocaciones (si agregaria una funcion: 409 "La cuenta esta deshabilitada: su perfil solo admite revocaciones..."). Perfil identico: 409 "El perfil no tiene cambios.".
- Otros: `Input.list(key,max)` (lista de objetos, 400 si no lo es; maximo 100 entradas en el perfil). `GRANT_CREATE` reutiliza los helpers nuevos `headFree` e `insertGrant` (mismo comportamiento). `Personnel.HEADS` agrupa JEFATURA/DIRECCION.
- Archivos clave: `Personnel.java`, `Input.java`, `Workflow.java`, `ProfileSetTest.java`, `docs/DECISIONES_FUNCIONALES.md` (D3), `CLAUDE.md`
- Verificacion: JUnit 87 ok + 1 omitida (antes 81 + 1; +6 de `ProfileSetTest`; Maven informa 88 ejecutadas); frontend 10 ok; typecheck ok; build ok (frontend sin cambios); humo no aplica (sin cambios de interfaz)
- Desvios del plan: ninguno. El termino de una entrada nueva debe ser posterior al inicio y a la fecha actual (`GRANT_CREATE` admite un termino pasado; no se cambio).
- Hallazgos para gates siguientes:
  - G04: el componente de perfil debe reenviar para cada funcion existente su `valid_until` (y, si es programada, su `valid_from`) tal como vienen en `administration.grants`, en ISO-8601; omitir `until_at` en una funcion con termino la revoca y la reotorga sin termino. La revision es `users[].security_version`; tras un exito cambia, por lo que hay que recargar la administracion antes de otro perfil. Un resultado incierto se reintenta con la misma clave.
  - G04: los mensajes de rechazo (jefatura ocupada, TI incompatible, entrada repetida, cuenta deshabilitada, sin cambios) llegan como 409 con texto para el funcionario.
  - G03: `Bootstrap` institucional valida funciones por su cuenta (`STAFF_ROLES`, `distributors>1`); puede reutilizar `Roles`, pero no `PROFILE_SET` (no hay actor TI en la inicializacion).
  - `access.user(id)` (que usa `GRANT_CREATE`) responde 401 "Cuenta inactiva" para una cuenta deshabilitada; `PROFILE_SET` lee `staff` directamente para admitir revocaciones.
- Pendientes: interfaz del perfil -> G04; `PROFILE_SET` en `MySql84IntegrationTest` -> G13
- Verificacion manual sugerida al usuario: no aplica (sin interfaz hasta G04)

### G03 - Supresion de AUTORIDAD e inicializacion   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G03: supresion de AUTORIDAD e inicializacion`)
- Hecho:
  - `Bootstrap.suppressAuthority`: en cada arranque, una sola vez (marcador `ROL_AUTORIDAD_SUPRIMIDO_050`), revoca las AUTORIDAD vigentes o programadas sin borrar filas, con un `FUNCION_REVOCADA` "AUTORIDAD. Rol suprimido en 0.5.0 (D1)" por concesion y un epoch por cuenta. Actor: identidad tecnica `sistema:actualizacion` (`Bootstrap.maintenance`; disabled, `technical`, sin funciones; se crea solo si hay algo que revocar).
  - Avisos: `warnAdministration` registra en el log `SIN_TI_PERMANENTE`/`TI_INCOMPATIBLE` (solo codigo y cantidad). `Queries.administration` entrega `warnings` (TI y AUDITORIA) e `incompatibilities` (TI: cuenta y roles); helpers `Personnel.permanentTi`, `tiConflicts` y `warnings`.
  - Semillas demo sin la cuenta `autoridad`. Archivo institucional: rechaza la entrada `autoridad` ("desde 0.5.0 TI administra funciones; retira la entrada autoridad") y la funcion AUTORIDAD; valida con `Roles` (varios distribuidores, TI como funcion unica, sin repetir rol+ambito); se retira `STAFF_ROLES`.
  - `GRANT_CREATE` rechaza un termino igual o anterior al inicio o a la fecha actual (mismo mensaje que `PROFILE_SET`).
  - Recuperacion de TI: `TiRecovery` (`siger.recover-ti.*`, proceso sin web ni tareas, solo sin TI permanente; auditada por `sistema:actualizacion` con `TI_RECUPERADA`; clave temporal en `data/recuperacion-ti-*.csv`) y `scripts/Recover-Ti.ps1` (PS 5.1, SIGER detenido, datos por variables del proceso).
- Archivos clave: `Bootstrap.java`, `TiRecovery.java`, `Personnel.java`, `Queries.java`, `application.properties`, `AuthoritySuppressionTest.java`, `scripts/Recover-Ti.ps1`, `scripts/Init-Server.ps1`, `deploy/config-ejemplo/*`, `docs/INSTALACION_VM.md`, `docs/DESPLIEGUE_VM.md`, `docs/DECISIONES_FUNCIONALES.md` (D1), `README.md`, `CLAUDE.md`
- Verificacion: JUnit 89 ok + 1 omitida (antes 87 + 1; +2 de `AuthoritySuppressionTest`; Maven informa 90 ejecutadas); frontend 10 ok; typecheck ok; build ok (frontend sin cambios); `Check-Scripts.ps1` ok con `pwsh` en Linux (30 scripts; no equivale a PS 5.1); humo no aplica (sin cambios de interfaz)
- Desvios del plan (decididos por el responsable): actor `sistema:actualizacion` en vez de "la cuenta TI de la instancia"; agregados el termino pasado en `GRANT_CREATE`, el procedimiento de recuperacion de TI y los avisos en el log y en Administracion. Una recuperacion sobre una cuenta con TI con termino o programada la revoca y otorga una permanente (sin superponer); si ya es permanente (cuenta solo deshabilitada), solo habilita y repone la clave.
- Hallazgos para gates siguientes:
  - G04: mostrar `administration.warnings` e `incompatibilities` ("Incompatibilidades por resolver"); `texts.ts` aun dice "Contacta a la autoridad habilitada". Las semillas nuevas no tienen `autoridad`; un `var/` local existente conserva esa cuenta, sin funciones tras el arranque. Agregar una prueba de que guardar un perfil sin cambios responde "El perfil no tiene cambios.".
  - G07/G10: las cargas iniciales pueden seguir el patron de `suppressAuthority` (transaccion + `db.lock()` + marcador) y usar `Bootstrap.maintenance(db)` si auditan.
  - G13 y G14: 0.4.0 esta en la VM como validacion. Hay que probar la actualizacion desde un MySQL V10 con datos: rutina de supresion, cargas iniciales de G07 y G10, y `Update.ps1`.
  - `MySql84IntegrationTest` se ajusto (1 cuenta, 0 AUTORIDAD, marcador) sin ejecutarse aqui.
- Pendientes: `Recover-Ti.ps1` y `TiRecovery.ready()` (modo proceso) sin ejecutar en Windows ni MySQL -> G14 (o G13); recorrido de recuperacion en `PRUEBAS_MANUALES_0_5_0.md` -> G14; interfaz de avisos e incompatibilidades -> G04
- Verificacion manual sugerida al usuario: no aplica en interfaz. Opcional: arrancar una copia de un `var/` 0.4.0 con el perfil local y comprobar en `audit_event` los `FUNCION_REVOCADA` de `sistema:actualizacion`.
