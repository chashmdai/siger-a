# SIGER 0.5.0 - arquitectura

Un JAR Spring Boot con React compilado y una base propia. Java 25 / Spring Boot 4.1.1 / Spring JDBC / Security /
Flyway; React 19 / TypeScript 6 / Vite 8; MySQL 8.4 propio en la VM y H2 (`MODE=MySQL`) para pruebas y uso local.
Sin microservicios, ORM ni infraestructura nueva. El SQL es el mismo en H2 y MySQL (la suite completa tambien corre
sobre MySQL 8.4 con `MySqlSuite`).

## Responsabilidades

- `Access`: funciones y ambitos efectivos (titular/subrogante), lectura y escritura por registro, destinos validos
  (`receiverOf`/`destination`) y lectura por materia. Una sola regla resuelve una cuenta o todo el personal habilitado.
- `BoardQueries.Visibility`: la misma visibilidad en SQL para bandejas, conteos, dashboard y buscador (equivalencia
  probada contra `Access` en `InstitutionalReaderTest`).
- `Roles`: catalogo unico de funciones (alcance AMBITO, INSTITUCIONAL u OPCIONAL, grupo), publicado en `/api/catalog`.
- `Workflow`: envoltorio unico de comandos transaccionales e idempotentes (lock global, actor de sesion, clave,
  registro) y despacho por dominio.
- `IntakeCommands` / `RequirementCommands` / `CaseCommands` / `Attention`: flujo COM/SOL, REQ y flujos entre unidades,
  Casos y tareas, primera atencion. `Lifecycle` concentra las transiciones compartidas y escribe los hitos.
- `Personnel`: cuentas, perfil de funciones (`PROFILE_SET`), subrogancias, sedes (`SCOPE_*`), avisos de administracion;
  `Holidays`: calendario habil. Solo TI.
- `PersonIndex` / `PersonSearch`: indice de coincidencias de personas (derivado, reconstruible) y busqueda por RUN,
  documento, nombre o folio con auditoria HMAC opcional del termino.
- `Milestones` / `BusinessCalendar` / `Reports` / `Csv`: hitos de cada transicion (y su carga historica), dias
  habiles en hora de Chile, reportes agregados y exportacion CSV sin datos ciudadanos.
- `Documents` / `DocumentScanner`: originales privados en cuarentena, analisis antivirus para liberar y descarga con
  nombre neutro.
- `FormularioConsumer` / `FormularioAutomatic` / `MailWorker` / `Integrations`: integraciones, inactivas por defecto.
- `Bootstrap` / `TiRecovery`: semillas sinteticas o inicializacion institucional, rutinas de actualizacion con marcador
  y recuperacion auditada de TI en un proceso sin web.
- `Api` / `ApiErrors` / `SecurityConfig`: HTTP, sesion, CSRF, errores sin datos ciudadanos. Actor del servidor, nunca
  un rol enviado por el cliente. Texto de busqueda solo en cuerpos POST.

## Persistencia aditiva

V1-V10 de versiones anteriores se conservan. 0.5.0 agrega: V11 (sedes: region, estado, apertura y `revision` de
`org_scope`; materia sugerida de la SOL y materia del REQ), V12 (`person_reference`), V13 (`business_holiday`) y V14
(`milestone`, `application_marker.amount`). Las rutinas de actualizacion (supresion de AUTORIDAD, conversion de las
Coordinaciones regionales, indice de personas, hitos historicos) corren una sola vez al arrancar, por lotes con el lock
global, y dejan un marcador. Ninguna migracion siembra sedes, feriados ni personas reales.

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

Presentacion: sistema visual propio sin dependencias nuevas (tokens CSS, iconos propios, Cabin OFL y escudo
institucional servidos desde el JAR), con barra lateral por tipo de trabajo, paleta Ctrl+K, fichas con cadena
COM->SOL->REQ->Caso y version movil. Decisiones en `docs/DISENO_0_4_0.md`.

## Operacion

Los scripts (Windows PowerShell 5.1) ejecutan Java por ruta absoluta, MySQL en instancia independiente, configuracion,
documentos y base fuera del JAR. `Build.ps1` toma la version de `backend/pom.xml` (el frontend debe declarar la misma)
y el esquema de las migraciones, y los escribe en el manifiesto; `Start.ps1` y `Test-Service.ps1` exigen que
`/api/health` responda esa version y ese esquema. Hashes mediante .NET; `Check-Scripts.ps1` en cada build. Sin cambio
persistente de PATH/JAVA_HOME, firewall ni servicios ajenos.
