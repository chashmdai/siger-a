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
| G04 | Perfil de cuenta en la interfaz | CERRADO |
| G04b | Perfil de cuenta: vista movil | CERRADO |
| G05 | Consulta institucional: lectura | CERRADO |
| G05b | Consulta institucional: base intacta y original del Formulario | CERRADO |
| G06 | Consulta institucional: bandejas e interfaz | CERRADO |
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

### G04 - Perfil de cuenta en la interfaz   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G04: perfil de cuenta en la interfaz`)
- Hecho: modal "Perfil de la cuenta" (`ProfileDialog.tsx`, boton Perfil por cuenta salvo la propia). Matriz de ambitos habilitados (mas los deshabilitados con funciones) x funciones por ambito de `catalog.roles`, con desplazamiento horizontal y la columna de ambitos fija; casillas institucionales; vigencia opcional; paso 2 con "Se otorgan / Se revocan" y fundamento; clave idempotente con reintento ante resultado incierto. TI limpia y bloquea las demas casillas. Si la cuenta esta deshabilitada, solo admite desmarcar; dos periodos de la misma funcion y ambito bloquean con la indicacion de revocar uno desde "Funciones otorgadas"; un perfil sin cambios no se puede confirmar. Logica pura en `profile.ts` (misma comparacion que el servidor; reenvia `until_at` exacto en ms y `from_at` solo si es programada o se cambio).
- Administracion: sin "Conceder funcion" individual (decision del responsable; `GRANT_CREATE` sigue en el servidor); "Funciones otorgadas" para TI con Desde/Hasta/Estado y Revocar sin campo de ambito; `warnings` como aviso (TI y AUDITORIA); panel "Incompatibilidades por resolver" con Abrir perfil. Subrogancias, menu, `access.ts` (sin `authorityAt`), `labels.ts` ("Autoridad (suprimido)") y dashboard (sin seccion `authority`; textos de `technical` y "Contacta a TI") pasan a TI.
- Archivos clave: `frontend/src/features/admin/{ProfileDialog.tsx,profile.ts,Administration.tsx,Substitutions.tsx}`, `app/menu.ts`, `lib/{access,labels}.ts`, `features/dashboard/{Dashboard.tsx,texts.ts}`, `styles/screens.css`, `frontend/tests/profile.test.ts`, `ProfileSetTest.java`, `CLAUDE.md`
- Verificacion: JUnit 90 ok + 1 omitida (antes 89 + 1; +1 `ProfileSetTest.perfilReenviadoPorLaInterfazSinCambios`: fechas serializadas como `toISOString` dan "El perfil no tiene cambios."; redondeadas a minutos revocan y otorgan); frontend 18 ok (antes 10; +8 `profile.test.ts`); typecheck ok; build ok; humo por API 12/12 con el JAR y `./target/humo-gate`: catalogo sin AUTORIDAD, alta + Distribucion + Administrativo y Profesional (UPR-REQ) en un `PROFILE_SET`, reintento con la misma clave, perfil reenviado sin cambios -> 409, sesion de la cuenta con las 3 funciones, TI predesigna/activa/termina una subrogancia; el bundle servido contiene la interfaz nueva. Sin navegador en este equipo: el recorrido visual queda como verificacion manual.
- Desvios del plan: no hay boton de concesion individual (decision del responsable). El menu se verifico por la sesion y por `menuFor`, no con una prueba: `menu.ts` importa sin extension y `node --test` no lo carga.
- Hallazgos para gates siguientes:
  - G05/G06/G11: la matriz toma los roles de `catalog.roles`: `reach` AMBITO = columna con ambito obligatorio; cualquier otro = casilla institucional sin ambito. Los roles de ambito opcional de G11 (REPORTES, REPORTES_PERSONAS, EXPORTACION) necesitan un `reach` nuevo y soporte en `ProfileDialog`/`profile.ts`. G06 agrega `CONSULTA_INSTITUCIONAL` a `read` en `menu.ts`.
  - Tras `PROFILE_SET` el epoch cierra las sesiones abiertas de la cuenta (lo avisa el dialogo).
  - `Dashboard.tsx` sigue mostrando "Version 0.4.0" fija -> G14.
- Pendientes: recorrido visual del perfil en `PRUEBAS_MANUALES_0_5_0.md` -> G14; `PROFILE_SET` en `MySql84IntegrationTest` -> G13
- Verificacion manual sugerida al usuario: lista paso a paso entregada al cierre de la sesion (crear cuenta con Distribucion + Administrativo + Profesional en UPR-REQ; guardar sin cambios; marcar TI y ver que se limpian las demas; entrar con la cuenta nueva y revisar el menu; predesignar y activar una subrogancia como TI; pantalla angosta).

### G04b - Perfil de cuenta: vista movil   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G04b: perfil de cuenta, vista movil`)
- Origen: la verificacion manual de G04 paso salvo el paso 6. En ancho de celular la columna fija de ambitos ocupaba mas de la mitad, solo se veia una funcion con el encabezado cortado y no se notaba que habia mas columnas.
- Hecho: bajo 640 px (`useNarrow`, `matchMedia`, mismo corte que los modales) `ProfileDialog` reemplaza la matriz por una tarjeta por ambito: codigo, descripcion y contador de funciones marcadas en texto ("Sin funciones", "2 funciones"). La tarjeta es un `button` con `aria-expanded`/`aria-controls` dentro de un `h4` y al expandirse muestra las funciones como casillas en lista, con su nombre completo. Arriba van el filtro "Solo ambitos con funciones" (activo por defecto) y un buscador por codigo o nombre (sin tildes, palabra por palabra), con "Mostrando N de M ambitos" (`aria-live`) y un estado vacio con "Mostrar todos los ambitos" o "Buscar en todos los ambitos" (el foco pasa al buscador). Con el filtro se mantienen visibles los ambitos que la cuenta ya tenia y las tarjetas abiertas, para que desmarcar no haga desaparecer la tarjeta. En escritorio la matriz no cambia. Ambas vistas usan el mismo `Profile` de `profile.ts` (`countIn`, `fold`, `visibleScopes`). Animacion de apertura y giro del icono desactivados con `prefers-reduced-motion`; controles nativos y objetivos de 44 px o mas.
- Archivos clave: `frontend/src/features/admin/{ProfileDialog.tsx,profile.ts}`, `frontend/src/styles/screens.css`, `frontend/tests/profile.test.ts`
- Verificacion: JUnit 90 ok + 1 omitida (sin cambios; Maven informa 91 ejecutadas); frontend 21 ok (antes 18; +3: contador, filtro y buscador); typecheck ok; build ok; instancia aislada reiniciada en el puerto 18600 con `./target/humo-manual` (health UP, el bundle contiene la vista nueva). La revision visual en celular queda a cargo del responsable.
- Desvios del plan: ninguno (division de G04 prevista en la seccion 5).
- Hallazgos para gates siguientes: los roles nuevos de `catalog.roles` con `reach` AMBITO aparecen solos como columnas de la matriz y como casillas de cada tarjeta. Los de ambito opcional (G11) siguen pendientes, como se anoto en G04.
- Pendientes: recorrido movil del perfil en `PRUEBAS_MANUALES_0_5_0.md` -> G14
- Correcciones tras la revision del responsable (commit `gate G04b: correcciones de la revision movil`):
  - Modal descentrado en movil: `dialog.modal.profile-modal` fijaba su ancho con mas especificidad que la hoja inferior comun. Ahora la regla comun usa `width: min(var(--modal-width), calc(100% - 2rem))`, `margin: auto` y en movil `width: 100%` (no `100vw`, que incluye la barra de desplazamiento), y cada variante solo cambia `--modal-width`. Esto tambien corrige la vista previa de la ficha, que en escritorio quedaba en 640 px en vez de 920 por la misma causa. La paleta pasa a `100%`.
  - Botones de accion en la vista de tarjetas: la columna de valor pasa a `minmax(0, 1fr)`, los grupos `.actions` bajan de linea dentro de la tarjeta y el texto de cada boton no se parte (`white-space: nowrap`).
  - Fecha de inicio 0 de las semillas ("31-12-69"): la tabla muestra "Sin fecha registrada", el campo Desde del perfil queda vacio y el resumen de revocaciones dice "vigente" en vez de "desde ahora". +1 prueba frontend (22).
  - Verificado con `chrome-headless-shell` por CDP en una instancia propia (18601, borrada): a 414 px el perfil, `ActionDialog` y la vista previa quedan con 0 px a cada lado y la paleta con 24/24; ningun boton sale de su tarjeta ni se parte en 51 tarjetas de Administracion y 5 de Subrogancias (tambien a 700 px); sin desplazamiento horizontal de pagina. En escritorio los dialogos quedan centrados en el area visible. JUnit 90 ok + 1 omitida.
- Verificacion final (2026-11-20): el responsable reviso a 390 px y lo aprobo. Tambien se verifico con el MCP de Playwright (Chromium del sistema, `--headless`) a 390 px en una instancia propia (18601, borrada), con sesion de `ti`. En la lista de cuentas hay 51 tarjetas, sin botones fuera de la tarjeta ni partidos y sin desplazamiento horizontal. El perfil de `humo.perfil` ocupa todo el ancho (0 px a cada lado), con la vista de tarjetas (1 de 14 ambitos con el filtro), sin matriz y sin desplazamiento horizontal. Se revisaron las capturas. G04b queda cerrado.
- Verificacion manual sugerida al usuario: en http://localhost:18600 abre como `ti` el perfil de `prueba.perfil` en ancho de celular. Comprueba que se ve la tarjeta UPR-REQ con "2 funciones" y que al desactivar el filtro aparecen todos los ambitos. Busca "nna", expande la tarjeta y recorre con Tab y Espacio las casillas de funciones; marca TI y comprueba que las tarjetas quedan bloqueadas.

### G05 - Consulta institucional: lectura   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G05: consulta institucional, lectura`)
- Hecho: rol `CONSULTA_INSTITUCIONAL` en `Roles` (institucional, grupo CONSULTA; aparece solo en el perfil e incompatible con TI por `tiCompatible`). `Actor.institutionalReader()` abre `comRead`, `reqRead`, `caseRead`, `caseSourceDocuments`, `Attention.mayRead` (ficha general y NNA, versiones, entregas), `solVisible` (todo estado) y `flowVisible`. Nuevo `Access.reqRecords` (= `reqMain` o lector) para el registro completo del REQ: `close_reason`, constancias, cierres, historial, documentos y su descarga (`Documents.authorize` REQ: leer con `reqRecords`, escribir con `reqMain`). Vinculos y reaperturas del REQ visibles. Reservados: los filtros existentes (jefatura o responsable) no cambian; las constancias reservadas del REQ, ahora visibles para quien no es `reqMain`, se excluyen.
- Banderas corregidas: `can_link` (COM) exige distribucion o jefatura de origen, como `COM_LINK`, y `can_redistribute` (SOL) exige `canDistribute`. Antes no dependian de quien consultaba: la primera estaba oculta en la interfaz bajo `can_route` y la segunda la protegia `solVisible`.
- Archivos clave: `Roles.java`, `Access.java`, `Attention.java`, `Queries.java`, `Documents.java`, `frontend/src/lib/labels.ts` (etiqueta), `InstitutionalReaderTest.java`, `CLAUDE.md`
- Verificacion: JUnit 96 ok + 1 omitida (antes 90 + 1; +6 `InstitutionalReaderTest`; Maven informa 97 ejecutadas); frontend 22 ok; typecheck ok; build ok; humo por API 13/13 con el JAR en una instancia aislada (18605, borrada): catalogo con el rol, TI otorga por perfil (TI + consulta -> 409), el lector lee COM/SOL/REQ/CAS sin ninguna `can_*`, la constancia reservada no aparece, `CASE_NOTE` y `REQ_CLASSIFY` -> 403, TI sigue con 403 en el Caso.
- Barrido: los tipos se leen del `switch` de `Workflow.run`; con revision vigente y datos validos, los 57 terminan en 403 (ninguno en 409). `NOTICE_READ` queda fuera (marcar un aviso propio no es gestion); sobre un aviso ajeno responde exito sin modificarlo (se comprueba).
- Desvios del plan: el rol se publica en G05 (aprobado). Hasta G06 un lector puro no tiene bandejas, contadores ni menu (`/worklist` da 0) y solo llega a una ficha por enlace. Las dos banderas corregidas no estaban en el alcance, pero las expone la lectura nueva.
- Hallazgos para gates siguientes:
  - G06: `BoardQueries.Visibility` necesita el equivalente SQL de `institutionalReader` en `comRead`, `reqRead`, `caseRead` y en las SOL de todo estado. `/api/export` usa las bandejas y heredara el cambio. En `menu.ts`, `read` debe incluir `CONSULTA_INSTITUCIONAL`. `InstitutionalReaderTest.fixture()` sirve de base para la equivalencia `Access` vs `BoardQueries`.
  - G06 (interfaz): las demas banderas de las fichas ya llegan en falso para el lector. Revisar los botones que no dependen de `can_*` (por ejemplo, acciones de `SolDetail` o `ReqDetail` ligadas a `can_head`).
  - `TaskReminders` usa `caseRead`: un ejecutor que deja de ser miembro pero tiene la consulta sigue recibiendo recordatorios (inocuo).
- Pendientes: bandejas, dashboard, menu y aviso en las fichas -> G06; recorrido del lector en `PRUEBAS_MANUALES_0_5_0.md` -> G14; `MySql84IntegrationTest` con el lector -> G13
- Verificacion manual sugerida al usuario: no aplica en la interfaz hasta G06. Opcional: como `ti`, abre el perfil de una cuenta y comprueba que aparece la casilla "Consulta institucional" y que al marcar TI se limpia.

### G05b - Consulta institucional: base intacta y original del Formulario   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G05b: base intacta y original del Formulario`)
- Origen: pedido del responsable al cerrar G05. Solo pruebas; sin cambios de codigo de produccion.
- Hecho:
  - El barrido toma una instantanea antes y despues de cada intento del lector: filas de todas las tablas del esquema (incluidas `audit_event` y `command_log`), suma de `revision` en las tablas que la tienen, `security_version`, funciones revocadas, avisos leidos y documentos en cuarentena. Las 57 instantaneas quedan identicas. Un control negativo comprueba que la instantanea detecta un cambio real (`NOTICE_READ` propio: +1 aviso leido, +1 `command_log`).
  - Nueva `lectorDescargaElOriginalDeUnaComDelFormulario`: entrega 1.1 con adjunto registrada por el camino del consumidor (`custodyExternal` + `FormularioReception.register` + comprobante firmado). El lector ve el original en la ficha; en cuarentena la descarga da 409. Tras la liberacion (simulada por SQL), descarga los mismos bytes y nombre, y queda un `DESCARGA` con la COM y "Documento N".
- Archivos clave: `InstitutionalReaderTest.java` (agrega `siger.folio-prefix=COMA`, que exige el comprobante)
- Verificacion: JUnit 97 ok + 1 omitida (antes 96 + 1; +1 prueba nueva; Maven informa 98 ejecutadas); frontend sin cambios (22 ok en G05); humo no aplica (sin cambios de comportamiento)
- Desvios del plan: ninguno (division de G05 prevista en la seccion 5).
- Hallazgos para gates siguientes: G06 puede reutilizar `snapshot()` en la prueba de equivalencia de bandejas y `adultWithFile` (copia del vector de `FormularioConsumerTest`) si necesita una COM del Formulario con original.
- Pendientes: ninguno nuevo
- Verificacion manual sugerida al usuario: no aplica

### G06 - Consulta institucional: bandejas e interfaz   (CERRADO)
- Fecha: 2026-11-20 (commit `gate G06: consulta institucional, bandejas e interfaz`)
- Hecho:
  - `BoardQueries.Visibility.institutional`: `comRead`, `reqRead` y `caseRead` dan `1=1` para el lector, y los permisos de `sol` (todos los estados, `solRead`) y `flujos` (`flowRead`) tambien. `reqMain`, `distribution`, `attentionReceiver`, `receivers` y `heads` no cambian. Tareas y avisos siguen siendo personales.
  - `sol:pending` exige ser receptora y `sol:returned` exige distribucion. Sin ese ajuste, una Direccion con la consulta contaba en "SOL por asumir" todas las SOL pendientes de la institucion. Sin la consulta el resultado no cambia.
  - Inicio: seccion `institutional` (comunicaciones, REQ abiertos, Casos abiertos y peticiones entre unidades pendientes), que reemplaza a `overview` cuando la cuenta tiene la consulta. Ficha: `institutional_read` (`Queries.readable` sobre `Actor.withoutInstitutionalReader()`) muestra el aviso "Consulta institucional: lectura sin facultades de gestion..." en `Detail.tsx`, y `CaseDetail` no repite su aviso generico.
  - Menu: `read` incluye la consulta, y el lector ve Solicitudes, Casos y Colaboraciones. `menu.ts` importa `../lib/access.ts` con extension para que `node --test` lo cargue. La bandeja SOL `all` pasa a llamarse "Todas".
- Archivos clave: `BoardQueries.java`, `Access.java`, `Queries.java`, `InstitutionalReaderTest.java`, `frontend/src/app/menu.ts`, `features/detail/{Detail,CaseDetail}.tsx`, `features/dashboard/{Dashboard.tsx,texts.ts}`, `features/worklist/WorkspaceList.tsx`, `frontend/tests/menu.test.ts`, `CLAUDE.md`
- Verificacion: JUnit 100 ok + 1 omitida (antes 97 + 1; +3 en `InstitutionalReaderTest`: equivalencia de las 8 bandejas con todas las paginas para 10 cuentas, Inicio y filtros de decision propia, aviso de la ficha; Maven informa 101 ejecutadas). Mutaciones comprobadas: sin `institutional` o con el `sol:pending` anterior, las pruebas fallan. Frontend 27 ok (antes 22; +5 `menu.test.ts`: lector puro, lector + Profesional, Direccion + consulta, TI y cuenta sin funciones); typecheck ok; build ok. Humo con el JAR en una instancia aislada (18606, borrada): 19/19 por API (TI otorga la consulta por perfil, solo la seccion institucional, bandejas visibles, "Por asumir" vacia, tareas en 0, fichas COM/SOL con `institutional_read` y sin `can_*`, `SOL_ASSUME` 403, TI con 0 en bandejas). Con Playwright: menu de 8 entradas, Inicio institucional y ficha COM con el aviso, sin botones de gestion.
- Desvios del plan: el aviso depende de una bandera del servidor y no del rol en el navegador, para que una cuenta mixta no lo vea en lo que gestiona. Los filtros `sol:*` se ajustaron aunque no estaban en el alcance, porque la lectura nueva los exponia.
- Hallazgos para gates siguientes:
  - G12: `/api/export` usa las bandejas, asi que el lector institucional ya exporta el resumen de toda la institucion (folio, estado, ambito y fecha; sin datos ciudadanos; auditado; tope de 10.000 filas). G12 decide si el resumen actual pasa a exigir `EXPORTACION` (decision del responsable: no se toco en G06).
  - G08: el buscador puede filtrar con `Visibility.comRead/reqRead/caseRead` (ya consideran al lector).
  - Un filtro nuevo de bandeja que represente una decision propia debe llevar su condicion de rol: el permiso de la bandeja ya no lo acota para las cuentas con la consulta.
  - `InstitutionalReaderTest` crea `lector.direccion` (DIRECCION@UPD + consulta) por SQL, como segunda Direccion de UPD; solo existe en esa clase.
- Pendientes: recorrido del lector en `PRUEBAS_MANUALES_0_5_0.md` -> G14; `MySql84IntegrationTest` con las bandejas del lector -> G13
- Verificacion manual sugerida al usuario: como `ti`, otorga "Consulta institucional" a una cuenta nueva desde su perfil. Entra con esa cuenta y comprueba: el menu (Comunicaciones, Solicitudes, Requerimientos, Casos y Colaboraciones, sin Mis tareas ni Nueva atencion), Inicio con "Consulta institucional", el aviso en una ficha COM, REQ o Caso, y que no hay botones de gestion. Opcional: agrega Profesional a la misma cuenta y comprueba que en su propio Caso no aparece el aviso y conserva sus acciones.
