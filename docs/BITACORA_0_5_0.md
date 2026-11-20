# SIGER 0.5.0 - bitacora de gates

Estado vivo del trabajo de `docs/PLAN_0_5_0.md`. Cada sesion de Claude Code toma el primer gate ABIERTO, lo cierra aqui con el formato de la seccion 7 del plan y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado y con commit), BLOQUEADO (detenido; ver su registro).

## Estado

| Gate | Titulo | Estado |
|---|---|---|
| G00 | Linea base | CERRADO |
| G01 | Catalogo de roles y TI administrador | CERRADO |
| G02 | Comando PROFILE_SET | ABIERTO |
| G03 | Supresion de AUTORIDAD e inicializacion | ABIERTO |
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
