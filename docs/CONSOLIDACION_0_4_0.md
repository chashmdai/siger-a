# SIGER A 0.4.0 - consolidacion del backend

Fecha: 2026-11-08. Alcance: reorganizacion interna del backend y del frontend, y correcciones de rendimiento con evidencia. Sin cambios de contratos HTTP, reglas institucionales, permisos, trazabilidad, esquema ni migraciones (V1-V5 intactas). El frontend no cambia en comportamiento ni en lo visible (ver seccion Frontend). La version sigue siendo 0.4.0.

## Cambios

### 1. Division de `Workflow` por dominio

`Workflow.java` (450 lineas, todos los comandos) queda como envoltorio transaccional unico: validacion, lock global, actor de la sesion, clave idempotente, despacho y `command_log`. Los comandos se distribuyen siguiendo el patron ya usado por `Attention` y `Personnel`:

| Clase | Comandos |
|---|---|
| `IntakeCommands` | COM_CREATE, COM_IMMEDIATE, COM_ROUTE, COM_ASSUME, SOL_*, COM_LINK, LINK_DECIDE, LINK_RETURN |
| `RequirementCommands` | REQ_*, FLOW_REQUEST, FLOW_DECIDE, REOPEN_* |
| `CaseCommands` | CASE_*, NOTE_RECTIFY, TASK_* |
| `Lifecycle` | Transiciones compartidas: creacion de REQ y Caso, asignacion, cierre de Caso, constancias, folios, catalogos de valores (canales, materias, resultados) y guardas de estado |

Motivo: legibilidad y mantenimiento. Cada dominio cabe en un archivo legible (una sentencia por linea). Las transiciones que antes se repetian (folios COM/REQ en Workflow y Attention, conteo de entregas pendientes en 6 lugares, listas de materias/canales) tienen ahora un solo origen. No se agregaron dependencias ni capas.

### 2. Resolucion de funciones efectivas en lote (rendimiento y concurrencia)

Problema: cada aviso a jefaturas, receptores de SOL o distribucion (`notifyHeads`, `notifySolReceivers`, `notifyDistributors`, entrega de primera atencion a jefatura) recorria todo el personal habilitado y resolvia las funciones de cada cuenta con 2 consultas o mas por persona, **dentro del lock global de comandos**. `GET /api/catalog`, que el frontend pide al cargar, tras cada comando y al recuperar el foco de la ventana, hacia lo mismo.

Correccion: `Access.enabledActors()` obtiene en consultas constantes el personal habilitado, sus funciones vigentes y las subrogancias efectivas, y aplica la **misma** funcion `resolve()` que usa `Access.actor()` para una cuenta. La regla de subrogancia (el titular cede la funcion y conserva su cartera personal; el subrogante la recibe en los mismos ambitos) no cambio y existe en un solo lugar. Las reglas de destinatario de entregas (`handoffRecipient`, `attentionWritable`) dejaron de estar duplicadas entre `Access` y `Attention`.

Medicion (H2 en memoria, 319 cuentas habilitadas, misma prueba sobre la fuente original y la nueva):

| Operacion | Original | Nueva |
|---|---|---|
| Sentencias SQL de un COM_CREATE (con aviso a distribucion) | 1292 | 19 |
| Sentencias SQL de un COM_ROUTE (con aviso a receptores) | 1296 | 23 |
| 80 comandos con aviso | 661 ms | 449 ms |
| 20 catalogos | 109 ms | 37 ms |

H2 en memoria no tiene latencia de red. En MySQL cada sentencia es un viaje cliente-servidor, asi que la reduccion del numero de sentencias pesa mas que el tiempo medido aqui. El lock global queda tomado mucho menos tiempo por comando.

### 3. Tamano del pool configurable

`spring.datasource.hikari.maximum-pool-size=${SIGER_DB_POOL_MAX:6}`. El valor por defecto no cambia. Un comando conserva su conexion mientras espera el lock global; las lecturas toman una conexion por sentencia. Subirlo en la VM solo despues de una prueba de carga (ver pendientes).

### 4. Tests repetibles

`Version031Test` y `Attention040Test` usaban una carpeta de datos fija bajo `target/`. Al repetir `mvnw test` sin `clean`, el bootstrap encontraba `bootstrap-usuarios.csv` de la corrida anterior y, correctamente, se negaba a sobrescribirlo, lo que hacia fallar 7 tests. Ahora cada contexto usa `./target/test-dataNNN-${random.uuid}`.

Nueva prueba: `AccessResolutionTest`. Verifica que la resolucion en lote coincide con la resolucion por cuenta para todo el personal, con una subrogancia activa y un titular con cartera personal. Tambien comprueba que un aviso de SOL llega al subrogante y no al titular, y que el catalogo no ofrece funciones a una cuenta deshabilitada.

## Verificacion ejecutada (PC de desarrollo, Java 25.0.5, Node 24.22, Maven 3.9.11 offline)

- Linea base antes de cambios: `npm run build` correcto y `mvnw clean verify` con 12/12 JUnit.
- **Caracterizacion de equivalencia** (herramienta temporal, fuera del repositorio): un escenario de mas de 100 comandos que cubre COM, SOL, REQ, Casos, tareas, constancias, flujos, antecedentes, reaperturas, primera atencion, subrogancia, sede regional y administracion, incluidos los rechazos esperados. Despues, para cada usuario: catalogo, workspace, dashboard, administracion, todas las bandejas y filtros, y el detalle de cada ficha y version. Al final, el volcado canonico de todas las tablas, incluidos `inbox_notice` y `audit_event`. La salida de la fuente original y la de la nueva es **identica byte a byte** (snapshot de ~1 MB). Ejecutar dos veces la fuente original tambien da el mismo resultado.
- Final: `npm run build` correcto, `mvnw clean verify` con 13/13 JUnit y `siger-a.jar` con `static/index.html`.
- Prueba de humo del JAR real por HTTP, en instancia aislada (puerto 18600, H2 y datos en carpeta temporal, sin tocar `var/`): health UP esquema 5, login con CSRF, cambio de clave temporal, catalogo, ATTENTION_CREATE, ATTENTION_SEND a jefatura (aviso recibido por la coordinadora), ATTENTION_DECIDE, dashboard, workspace, export CSV, `index.html` con CSP. Rechazos: POST sin token CSRF 403, catalogo anonimo 401. Sin errores en los logs.

## No verificado / pendiente

- **Build.ps1 no se ejecuto**. Se ejecutaron sus pasos equivalentes (npm build + Maven clean verify), pero no `npm ci`, Check-Scripts ni el empaquetado ZIP.
- **MySQL real**: las consultas nuevas son SQL estandar ya usado en el proyecto, pero no se ejecutaron contra MySQL 8. Toda la verificacion fue con H2 `MODE=MySQL`.
- **Prueba de carga de 150 usuarios concurrentes**: no realizada. Riesgos conocidos a medir:
  - El lock global serializa todos los comandos. Ahora es mas corto, pero sigue siendo unico.
  - Los conteos del dashboard (unos 15 `COUNT` con `EXISTS` correlacionados) se piden cada 60 s por usuario visible y pueden crecer con el volumen de REQ y Casos.
  - `audit_event` recibe una fila `CONSULTA` por cada ficha abierta, asi que crecera rapido.
  - El pool de 6 conexiones.
- `SIGER_BIND=127.0.0.1` en la VM: para 150 usuarios en red habra que decidir la exposicion (proxy o enlace a la interfaz). El limitador de intentos de login usa la IP remota; detras de un proxy todas las solicitudes compartirian IP.
- `scripts/Start.ps1` exige `schema -eq 5` en el health. Una futura V6 debe actualizar ese chequeo.
- `SOURCE_SHA256.txt` no refleja estos cambios. Regenerarlo al preparar una entrega fuente.
- Observacion sin cambio: `Attention.normalize` arma las personas de la ficha recorriendo un `Set.of`, por lo que el orden de las claves dentro de `data_json` varia entre ejecuciones de la JVM. Es semanticamente inocuo (JSON con las mismas claves y valores).

## Frontend

### Cambios

Se reorganizo `frontend/src` sin dependencias nuevas, sin router ni libreria de estado, y sin tocar `style.css`. El CSS compilado es identico byte a byte (`index-lYEdJ75F.css`). Antes eran 13 archivos planos, con `Detail.tsx` y `Attention.tsx` de ~30 KB cada uno y lineas de hasta 1.700 caracteres. Ahora:

| Carpeta | Contenido |
|---|---|
| `App.tsx` | Shell: sesion, carga de catalogo/workspace, ruteo por hash, layout y dialogo de comandos |
| `app/` | `context.ts` (tipo Context), `routes.ts` (fichas, bandejas, `parseRoute`, bandeja -> ficha), `menu.ts` (navegacion por funcion), `Login.tsx` (ingreso y cambio de clave temporal) |
| `lib/` | `api.ts` (HTTP, CSRF, comandos, carga y preparacion de documentos), `rows.ts` (accesores y formato de fechas), `access.ts` (funciones de la sesion), `catalog.ts` (ambitos y personas), `fields.ts` (campos de dialogo y listas de valores) |
| `components/` | `ActionDialog`, `Table`, `Status`, `LabeledField` |
| `features/` | `dashboard`, `worklist`, `intake`, `admin` (usuarios, auditoria y subrogancias), `attention` (ficha: `schema.ts`, `FormSections.tsx` y componentes de acciones, entregas, adjuntos y vista imprimible), `detail` (una ficha por tipo COM/SOL/REQ/CAS/FLUJO, mas `Notes`, `Files`, `ClosureStatus` y `actions.ts`, con las acciones compartidas por REQ y Caso) |

Duplicaciones eliminadas:
- Canales, resultados inmediatos, tipos de documento y recordatorios definidos una vez.
- Filtro de ambitos de registro (`registersAt`) unificado entre Intake y Attention.
- Selects de una sola opcion y de decision (`fixedSelect`, `decision`); antes 15 copias literales (12 fijos y 3 decisiones).
- Llamadas de carga de documentos y de adjuntos preparados unificadas.
- Mapeo bandeja -> ficha, compartido por Dashboard y WorkspaceList.
- Lista de tipos de ficha, que App usaba dos veces.
- Peticion de flujo y reapertura, compartidas entre REQ y Caso.

### Verificacion

- `npm run typecheck` y `npm run build` correctos; `mvnw clean verify` con 13/13 JUnit y el JAR con el bundle verificado (`index-6xre6VEY.js`).
- **Equivalencia en navegador real** con un arnes temporal fuera del repositorio (scratchpad `ui/`): Chrome headless controlado por DevTools Protocol desde Node 24, sin dependencias. El backend sirve cada `dist` por `spring.web.resources.static-locations` sobre la **misma base H2 restaurada** desde un snapshot con datos sinteticos preparados por HTTP: COM, SOL pendientes y devueltas, REQ abiertos y cerrados, Casos en gestion, cerrados y en revision, tareas, colaboracion pendiente, antecedente propuesto, tres primeras atenciones (una con entrega pendiente), subrogancia programada y un documento.
  - Recorrido: 11 usuarios con funciones distintas, 433 paginas (todas las entradas del menu y las fichas COM 1-12, SOL 1-5, REQ 1-8, CAS 1-4 y FLUJO 1-2) y 569 clics. Se pulso cada boton habilitado de cada pagina sobre una pagina recargada, capturando el dialogo abierto o el destino. Luego 5 envios reales por formulario: nueva atencion, COM por clasificar, reclasificacion de REQ, recepcion de entrega y nueva tarea.
  - Captura: DOM serializado con atributos ordenados, valores reales de inputs, selects y checkboxes, estados `disabled`/`open`/`selected`/`hidden`, mas cada solicitud HTTP con su cuerpo (metodo, URL y payload).
  - Resultado: **EQUIVALENTE**, 82.257 lineas canonicas por lado. Los 5 POST tienen cuerpos identicos.

### Limitaciones de la comparacion

- Se normalizan fechas visibles, fechas ISO y UUID (claves de operacion y tokens). Un error que afectara solo al formato de una fecha quedaria fuera de la comparacion.
- El comparador une nodos de texto contiguos. Las capturas crudas difieren en 456 lineas, todas nodos de texto y de un solo patron: el pie de paginacion `| 25 por pagina` ahora se emite como tres nodos porque 25 sale de la constante `PAGE_SIZE`. El texto visible es identico.
- Fuera del recorrido quedaron: carga real de archivos (el arnes no selecciona archivos), impresion, interacciones dentro de los dialogos salvo los 5 envios, edicion de la ficha mas alla del paso 2, cambio de filtros y paginas de las bandejas, navegacion con cambios sin guardar (`confirm`), vista movil y estilos calculados. El CSS no cambio.
- Determinismo: una captura completa de la base y una del frontend nuevo, sobre el mismo snapshot. **La segunda captura de la base, que debia confirmar el determinismo, quedo inconclusa**: un primer intento se detuvo por un cuelgue del arnes (una llamada CDP sin respuesta, luego corregida con limites de tiempo y reintentos), y el segundo se detuvo a pedido sin generar `ui-base2.txt`. El resultado identico entre base y nuevo indica un recorrido estable, pero no hay una repeticion completa que lo pruebe.
- No hubo prueba manual con lector de pantalla ni en otros navegadores.

Evidencia conservada (scratchpad de la sesion, fuera del repositorio): `ui/ui-base.txt`, `ui/ui-new.txt`, `ui/EVIDENCIA_EQUIVALENCIA.txt` y los scripts `prep.mjs`, `harness.mjs`, `compare.mjs` y `run-ui.ps1`.
