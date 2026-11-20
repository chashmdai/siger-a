# SIGER 0.5.0 - plan de implementacion

Fecha: 2026-11-20. Base: commit `cd21f6e` (0.4.0, esquema V10). Decisiones tomadas por el responsable del proyecto el 2026-11-20.
Texto comun y estable para todas las sesiones de Claude Code. El trabajo se ejecuta por **gates** (seccion 6): una sesion toma un gate, lo implementa, lo verifica, lo cierra en `docs/BITACORA_0_5_0.md`, hace commit y se detiene. El usuario hace `/clear` y la siguiente sesion toma el gate siguiente. La seccion 3 es el diseno de referencia; los gates indican que parte de ella corresponde a cada sesion.
Este documento solo cambia con aprobacion del responsable. Si una sesion encuentra que el plan es incorrecto o incompleto, lo registra en la bitacora como desvio o hallazgo y, si bloquea el gate, se detiene y lo informa.

## 0. Decisiones (agregar a `docs/DECISIONES_FUNCIONALES.md` como "Precision 0.5.0")

D1. **TI administra el sistema completo**: cuentas, claves, ambitos, concesion y revocacion de funciones, predesignacion, activacion y termino de subrogancias, calendario habil, liberacion de documentos e integraciones. **Se suprime el rol AUTORIDAD.** El acto institucional que respalda cada decision (resolucion, memo) se registra como fundamento obligatorio. Reemplaza en el punto 13 a la "autoridad habilitada" por TI; el resto del punto 13 (ambito, periodo, retorno, revocacion) se mantiene.

D2. **TI no tiene funciones operativas ni de lectura.** Una cuenta con TI no puede tener ninguna otra funcion (ni operativa, ni consulta, reportes, exportacion o auditoria), y una cuenta con cualquier otra funcion no puede recibir TI. TI no se concede funciones a si misma. Se mantiene el punto 14: TI no obtiene acceso a expedientes por administrar cuentas.

D3. **Funciones combinables.** Una cuenta acumula funciones por ambito o institucionales mediante su perfil (por ejemplo Distribucion + Administrativo + Profesional). Los roles siguen siendo fijos, con sus reglas actuales; no se crean roles libres. Se admiten varios distribuidores simultaneos.

D4. **Consulta institucional** (`CONSULTA_INSTITUCIONAL`): lectura completa de todas las COM, SOL, REQ, Casos, flujos y primeras atenciones, incluidas las fichas NNA y sus versiones, los originales externos del Formulario y los documentos no reservados. **Excluye las constancias y documentos marcados como reservados.** Nunca concede gestion. Toda consulta se audita.

D5. **Buscador de personas y folios.** Por RUN, nombre o folio, sobre los datos declarados de requirentes, adultos y NNA. Es un indice de coincidencias, no un registro maestro: no fusiona identidades ni autentica personas.

D6. **Reportes.** `REPORTES` (cifras agregadas por unidad o ambito, sin datos de personas), `REPORTES_PERSONAS` (desglose por funcionario; su concesion es una decision institucional) y `EXPORTACION` (filas sin datos ciudadanos). Los tiempos se informan en **horas corridas y en dias habiles** (lunes a viernes, excluidos los feriados del calendario que mantiene TI).

## 1. Que queda reservado hoy (verificado en el codigo de 0.4.0)

- **Constancias de Caso** con `management_note.reserved=TRUE`: solo las ven la jefatura del ambito del Caso y el responsable (`Access.reserved`). Una rectificacion no puede desclasificarlas (`CaseCommands.rectify`).
- **Documentos de gestion reservados** (`document_item.reserved=TRUE`): de Caso, jefatura del ambito y responsable; de REQ, jefatura principal. Un documento hereda la reserva de su constancia o de su version anterior (`Documents.upload`).
- **Constancias de REQ**: la columna admite el valor, pero la interfaz no ofrece la casilla (`noteFields(true)`) y `Queries` no filtra reservadas en el REQ. En la practica no existen; igual se excluyen de la consulta institucional.
- **No admiten reserva hoy**: COM y sus adjuntos de ingreso (`Documents.authorize` lo rechaza), SOL, la ficha de primera atencion (general y NNA, con sus versiones), los datos del Formulario, tareas, historial y cierres.

Consecuencia de D4: la consulta institucional ve las fichas NNA completas. Reservar campos de la ficha (por ejemplo `safety_note` o `identity_note`) seria una funcionalidad nueva; sigue en pendientes como "reserva excepcional por campo".

## 2. Reglas para todos los gates

- Leer `CLAUDE.md` antes de empezar. V1-V10 no se modifican; cada cambio de esquema es una migracion aditiva nueva, compatible con H2 `MODE=MySQL` y MySQL 8.4.
- Toda escritura nueva es un comando de `Workflow.run` (lock, actor de sesion, idempotencia, `revision`, `db.audit`). Ninguna lectura nueva recibe rol, autor ni alcance desde el navegador.
- Toda regla de visibilidad existe dos veces: `Access` (Java, por registro) y `BoardQueries.Visibility` (SQL, bandejas y conteos). Cualquier cambio va en ambas y se cubre con una prueba de equivalencia, como `AccessResolutionTest`.
- Las funciones nuevas se agregan a un catalogo unico de roles (ver iteracion 1). El frontend lo recibe por `/api/catalog` y no repite reglas de alcance.
- Textos visibles con ortografia completa; codigos sin cambios; etiquetas en `lib/labels.ts`. No hay librerias nuevas en frontend ni backend.
- Version: `0.5.0` en `backend/pom.xml` y `frontend/package.json`. Desde `cd21f6e`, `Build.ps1` deriva el esquema de las migraciones y `Start.ps1` lee el manifiesto; `CLAUDE.md` todavia dice que hay que cambiar la version y el esquema a mano en varios lugares: corregirlo.
- En Linux no se usan `Build.ps1` ni `Run-Local.ps1` (son Windows con `.tools` portables). Equivalente: JDK 25 y Node 24 locales, `./mvnw -B -ntp -f backend/pom.xml test`, `cd frontend && npm ci && npm run typecheck && npm test && npm run build`. El build completo con `Build.ps1 -IncludeJava` se ejecuta en el PC Windows antes de una entrega.
- Cierre de cada gate: pruebas JUnit y de frontend verdes, `npm run build` correcto si se toco el frontend, documentacion del cambio actualizada y un commit (seccion 5). `SOURCE_SHA256.txt` se regenera solo en G14.

## 3. Iteraciones

### Iteracion 1 - Administracion centralizada en TI y perfil de cuenta (sin migracion)

**Backend**
- Catalogo unico de roles (`Roles.java` o equivalente), con alcance y grupo:
  - por ambito obligatorio: `ADMINISTRATIVO`, `PROFESIONAL`, `JEFATURA`, `DIRECCION`, `LECTURA`, `AUDITORIA`;
  - institucionales (sin ambito): `TI`, `DISTRIBUIDOR`.
  - Las iteraciones 2 y 5 agregan `CONSULTA_INSTITUCIONAL`, `REPORTES`, `REPORTES_PERSONAS` y `EXPORTACION`. No se publica un rol antes de que tenga efecto.
  - `Personnel.SCOPED_ROLES` y las listas del frontend pasan a leer este catalogo.
- `Personnel`: `GRANT_CREATE`, `GRANT_REVOKE`, `DESIGNATE`, `SUBSTITUTE_ACTIVATE` y `SUBSTITUTE_END` exigen TI (`ti(a)` en vez de `authority(a,scope)`). El titular conserva la facultad de terminar su propia subrogancia.
- Se mantienen: no autoconcesion, una jefatura efectiva por ambito, subrogancias no superpuestas, subrogante elegible, fundamento obligatorio.
- Se elimina la exclusividad de `DISTRIBUIDOR`: hoy el conteo de `GRANT_CREATE` no filtra por usuario y solo permite un distribuidor central en toda la institucion. Se conserva el rechazo de un periodo superpuesto para la misma persona.
- Incompatibilidad TI (D2) en `GRANT_CREATE`, `PROFILE_SET` y la inicializacion institucional.
- Nuevo comando `PROFILE_SET` (id = cuenta; `revision` = `staff.security_version`):
  - `data = {grants:[{role, scope_id|null, from_at?, until_at?}], reason}`.
  - Calcula la diferencia con las funciones vigentes: revoca las que faltan (`revoked_at`, nunca borra) y crea las nuevas, con un evento `FUNCION_OTORGADA` o `FUNCION_REVOCADA` por cada cambio y un solo `epoch`.
  - Valida todo antes de escribir; si una regla falla, no registra nada.
  - Las concesiones por periodo (desde/hasta) siguen disponibles; por defecto rigen desde ahora y sin termino.
- `Access.Actor.authority()` se elimina:
  - `Queries.workspace` filtra designaciones y subrogancias para TI, titular y subrogante;
  - `Queries.administration` entrega a TI cuentas, funciones vigentes e historicas, designaciones y subrogancias;
  - `AUDITORIA` sigue viendo los eventos de su alcance mas todos los eventos `USUARIO`, `AMBITO` y `SUBROGANCIA`.
- Dashboard: la seccion de autoridad pasa a la seccion de TI (subrogancias activas y programadas, cuentas habilitadas, cuarentena).
- Supresion de AUTORIDAD en datos existentes:
  - rutina Java idempotente al arrancar, con marcador `application_marker` `ROL_AUTORIDAD_SUPRIMIDO_050`;
  - revoca las concesiones `AUTORIDAD` vigentes y audita `FUNCION_REVOCADA` con fundamento "Rol suprimido en 0.5.0 (D1)", usando la cuenta TI de la instancia;
  - no quita funciones a cuentas que hoy combinen TI con otras: las lista en Administracion como "Incompatibilidades por resolver".
- `Bootstrap`:
  - semillas demo sin la cuenta `autoridad`; `ti` solo con TI;
  - el archivo institucional ya no lleva `autoridad`: si la trae, el arranque falla con un mensaje claro ("desde 0.5.0 TI administra funciones; retira la entrada autoridad");
  - TI puede venir en el archivo junto con las demas cuentas y sus funciones;
  - actualizar `deploy/config-ejemplo/estructura-inicial.json`, `LEEME.txt` y `scripts/Init-Server.ps1`.

**Frontend**
- Administracion > cuenta > "Perfil de la cuenta" (componente propio; `ActionDialog` no admite matrices):
  - matriz de ambitos habilitados x funciones por ambito con casillas, y casillas para funciones institucionales;
  - TI deshabilita y limpia las demas casillas (y viceversa), con la explicacion visible;
  - vigencia opcional por funcion;
  - antes de confirmar muestra el resumen "Se otorgan: ... / Se revocan: ..." y pide fundamento;
  - clave idempotente estable y reintento con la misma clave ante un resultado incierto, como en `Attention.tsx`.
- `Substitutions.tsx` y `menu.ts`: TI en vez de AUTORIDAD. `lib/access.ts` pierde `authorityAt`. `ROLE_LABELS` conserva "Autoridad (suprimido)" solo para mostrar el historial.

**Pruebas** (actualizar `AccessResolutionTest`, `Version031Test`, `InstitutionalBootstrapTest`, `InstitutionalStaffTest`, `MySql84IntegrationTest` y los usos de `autoridad` en `ExternalOriginalTest` y `FormularioConsumerTest`)
- `PROFILE_SET`:
  - diferencia correcta, una sola transaccion e idempotencia por clave;
  - `revision` obsoleta da 409;
  - una regla violada no deja cambios parciales.
- TI:
  - no se concede funciones a si misma;
  - TI + otra funcion es rechazado en ambos sentidos;
  - una cuenta sin TI recibe 403 en todo `Personnel`;
  - TI recibe 403 en el detalle de COM, SOL, REQ, CAS y FLUJO y no ve bandejas de expedientes.
- Varios distribuidores vigentes reciben el aviso de COM por clasificar.
- Una jefatura por ambito sigue vigente.
- La rutina de supresion es idempotente y conserva la historia.

**Aceptacion manual**: TI crea una cuenta y en un solo paso le asigna Distribucion + Administrativo (UPR-REQ) + Profesional (UPR-REQ). Esa cuenta ve el menu de las tres funciones. TI activa una subrogancia sin intervencion de otra autoridad.

### Iteracion 2 - Consulta institucional (sin migracion)

**Backend**
- Rol institucional `CONSULTA_INSTITUCIONAL`; `Actor.institutionalReader()`.
- Lectura (`Access` y `Queries`):
  - `comRead`, `reqRead` y `caseRead` devuelven verdadero para el lector institucional;
  - `caseSourceDocuments` y `Attention.mayRead` tambien (ficha general y NNA, versiones, entregas);
  - `Queries`: `solVisible` y `flowVisible` verdaderos; historial del REQ, constancias del REQ, cierres, vinculos, reaperturas, `close_reason` y documentos del REQ visibles;
  - proyeccion externa del Formulario visible.
- Reserva: constancias y documentos con `reserved=TRUE` excluidos de listados, versiones y descargas, salvo que la misma cuenta los pueda ver por otra funcion (jefatura del ambito o responsable).
- Escritura sin cambios: `caseWrite`, `reqMain`, `attentionWritable`, `canDistribute` y `handoffRecipient` no consideran el rol. Prueba de barrido: cada tipo de comando de `Workflow.run` ejecutado por un lector institucional puro termina en 403 o 409, nunca en exito.
- `BoardQueries.Visibility`:
  - el lector ve todo en `com`, `sol` (todos los estados), `req`, `cas`, `flujos` y `atenciones`; `tareas` y `avisos` siguen siendo personales;
  - seccion de dashboard "Consulta institucional" con los conteos generales.
- Auditoria: se mantienen `CONSULTA`, `FICHA_VERSION_CONSULTADA` y `DESCARGA`.

**Frontend**
- Menu de bandejas para el lector (`read = operative || LECTURA || CONSULTA_INSTITUCIONAL`).
- Aviso en las fichas: "Consulta institucional: lectura sin facultades de gestion". Los botones ya dependen de las banderas del servidor (`can_*`); verificar que ninguno aparezca.

**Pruebas**
- Equivalencia `Access` vs `BoardQueries` para el lector y para una cuenta comun, en todas las bandejas.
- Constancias y documentos reservados invisibles para el lector.
- Ficha NNA y versiones legibles.
- Barrido de comandos.
- El lector combinado con Profesional conserva exactamente sus facultades de gestion.

### Iteracion 3 - Buscador de personas y folios (V11)

**Fuentes verificadas**
| Fuente | Datos |
|---|---|
| `communication` | `presenter`, `identity_document` (texto libre), `folio`, `external_id` |
| `communication_person` | `name`, `identity_document` y rol (NNA, ACOMPANANTE, OTRA_PERSONA), hasta 12 por COM |
| `attention_record.data_json` | `presenter_*` (nombres, RUN y DV, documento), `presenter_name`, `identity_document`, `guardian_name`, `people[]` (`name`, `identity_document`, `preferred_name`, rol) |
| `external_person` | `given_names`, `surnames`, `run_number`, `run_dv`, `foreign_document_number`, rol, `authenticated` |
| Folios | `communication.folio`, `requirement.folio`, `case_record.folio`, `formulario_reception.folio` |

La ficha es JSON y no se puede buscar de forma portable en SQL; por eso se usa un indice.

**V11 `person_reference`** (dato derivado, reconstruible)
- Columnas: `id`, `communication_id` (no nulo), `requirement_id` (nulo), `source` (COM, COM_PERSONA, FICHA, FORMULARIO), `person_role`, `display_name`, `name_key` (minusculas, sin tildes ni puntuacion, espacios simples), `run_number`, `run_dv`, `document_key`, `authenticated`, `updated_ms`.
- Indices por `run_number`, `document_key` y `communication_id`.

**Mantenimiento**
- `PersonIndex.refresh(com)` en la misma transaccion de `COM_CREATE`, `COM_IMMEDIATE`, `ATTENTION_CREATE`, `ATTENTION_SAVE` y ambas rutas de `conserveExternal`: borra las referencias de esa COM y las vuelve a calcular con los datos vigentes. Las versiones antiguas de la ficha no se indexan.
- Carga inicial en Java al arrancar, por lotes, con el marcador `PERSON_INDEX_V11`.
- Un RUN escrito en texto libre (`12.345.678-9`, `12345678-9`) se reconoce y se normaliza.

**Busqueda** (`GET /api/search?q=`, 3 a 160 caracteres)
- Un folio da coincidencia exacta en COM, REQ, CAS y comprobante del Formulario.
- Un RUN (con o sin puntos y DV) busca el numero exacto. Si el DV no cumple modulo 11, se muestra el aviso y se busca igual por numero.
- Cualquier otro texto se trata como nombre: cada palabra normalizada debe aparecer en `name_key`.
- Hasta 50 resultados; si hay mas, se pide refinar.
- Resultado agrupado por COM, con su cadena:
  - COM: folio, estado, canal y fecha;
  - REQ: folio, estado, principal y encargado;
  - Casos: folio, estado y responsable;
  - coincidencia: rol, fuente, nombre mostrado y "identidad declarada" o "autenticada (ClaveUnica)".

**Permisos**
- El filtro usa los mismos predicados SQL de `Visibility`: el lector institucional ve todo y las demas cuentas solo lo que ya pueden leer.
- TI recibe 403.

**Auditoria**
- Evento `BUSQUEDA` con tipo de termino (RUN, NOMBRE o FOLIO) y cantidad de resultados.
- El termino nunca se guarda en claro. Si se configura `siger.audit-search-key` (secreto con el mismo tratamiento DPAPI que las credenciales del Formulario en `Environment.ps1`), se guarda HMAC-SHA256 del termino normalizado. Un SHA-256 simple de un RUN se revierte por fuerza bruta.
- Nada del termino va al log de la aplicacion.

**Frontend**
- Entrada de menu "Buscar persona o folio" y campo en la paleta Ctrl+K (`#buscar?q=...`).
- Resultados con enlaces a las fichas.

**Pruebas**
- Normalizacion: tildes, mayusculas, puntos, DV K.
- Cada fuente queda indexada; la referencia se actualiza al guardar la ficha.
- Filtrado por permisos; TI 403.
- Auditoria sin el termino en claro.
- Carga inicial idempotente.
- Mismas consultas en `MySql84IntegrationTest`.

### Iteracion 4 - Calendario habil (V12) e hitos (V13)

**V12 `business_holiday`**
- Columnas: `day` CHAR(10) PK `aaaa-mm-dd`, `name`, `created_by`, `created_ms`.
- Comandos de TI `HOLIDAY_ADD` y `HOLIDAY_REMOVE`, con fundamento.
- No se siembran feriados inventados: TI los carga por anio. Los reportes advierten "sin feriados cargados para AAAA".

**V13 `milestone`**
- Columnas: `id`, `resource_type`, `resource_id`, `requirement_id`, `case_id`, `event`, `actor_id`, `subject_user` (por ejemplo el profesional asignado), `scope_id`, `at_ms`, `reconstructed`.
- Indices por (`event`, `at_ms`) y `requirement_id`.

**Eventos** (`Lifecycle.milestone(...)` en la misma transaccion del cambio de estado)
- Ingreso y distribucion: `COM_REGISTRADA`, `SOL_DISTRIBUIDA`, `SOL_REDIRIGIDA`, `SOL_DEVUELTA`, `SOL_ASUMIDA`.
- REQ: `REQ_CREADO`, `REQ_CERRADO`, `REQ_REACTIVADO`.
- Caso: `CASO_CREADO`, `CASO_ASIGNADO`, `ASIGNACION_ACUSADA`, `CASO_ESPERA`, `CASO_REANUDADO`, `CIERRE_SOLICITADO`, `CIERRE_RETIRADO`, `CIERRE_DEVUELTO`, `CASO_CERRADO`, `CASO_REABIERTO`.
- Tareas: `TAREA_CREADA`, `TAREA_COMPLETADA`, `TAREA_CANCELADA`.
- Entregas y flujos: `ENTREGA_ENVIADA`, `ENTREGA_DECIDIDA`, `FLUJO_SOLICITADO`, `FLUJO_DECIDIDO`.

**Carga historica** (Java, marcador `MILESTONES_V13`)
- Desde las columnas de fecha existentes: `received_ms`/`created_ms`, `solicitation.created_ms`, `case_assignment.assigned_ms`/`acknowledged_ms`, `attention_handoff`, `flow_request`, `closure_record`.
- Desde `audit_event` para lo que solo esta ahi: `SOL_REDIRIGIDA`, `CASE_WAIT`, `CASE_RESUME`, `CASE_REQUEST_CLOSE`, `CASE_WITHDRAW_CLOSE`, `CASE_RETURN`, `TASK_COMPLETE`, `TASK_CANCEL`.
- Esas filas llevan `reconstructed=TRUE`.

**`BusinessCalendar`** (Java, zona America/Santiago)
- Horas corridas = (fin - inicio) / 3.600.000.
- Dias habiles = cantidad de dias habiles `d` con `fecha(inicio) < d <= fecha(fin)` (el dia de inicio no cuenta, como en el computo de plazos administrativos). Mismo dia = 0.
- Ejemplo: viernes 17:00 a lunes 09:00 = 64 h corridas y 1 dia habil.

**Pruebas**: fines de semana, feriados, mismo dia, cambio de horario de Chile, carga historica idempotente, hitos escritos por cada comando.

### Iteracion 5 - Reportes y exportacion

**Roles** (en el catalogo, ambito opcional: nulo = toda la institucion)
- `REPORTES`, `REPORTES_PERSONAS` (exige `REPORTES` en el mismo alcance) y `EXPORTACION`. Incompatibles con TI.
- Los reportes agregados no requieren permiso de lectura de expedientes porque no muestran contenido.

**Alcance**: registros cuyo `principal_scope` (REQ) o `scope_id` (Caso) esta dentro del alcance concedido.

**Catalogo inicial**

| Reporte | Tramo o calculo |
|---|---|
| Volumen | COM por canal y origen (manual, atencion directa, Formulario); REQ por estado, unidad, complejidad y origen (SOL o atencion directa); REQ con y sin Caso; Casos por estado y materia |
| Registro | `received_ms` -> `created_ms` de la COM |
| Distribucion | COM registrada -> SOL distribuida (por distribuidor) |
| Asuncion | SOL distribuida o redirigida -> SOL asumida (REQ creado), por cada tramo y jefatura receptora |
| Apertura de Caso | REQ creado -> primer Caso |
| Acuse | Caso asignado -> asignacion acusada (por profesional) |
| Gestion del Caso | creado -> cerrado, con tiempo en espera descontado e informado aparte |
| Revision de cierre | cierre solicitado -> cerrado o devuelto (por jefatura) |
| REQ total | creado -> cerrado |
| Primera atencion | entrega enviada -> decidida |
| Flujos | solicitado -> decidido |
| Antiguedad de abiertos | REQ y Casos abiertos por tramos de dias habiles (0-5, 6-10, 11-20, mas de 20) |

**Calculo y filtros**
- Para cada tiempo: n, mediana, percentil 90 y maximo, en horas corridas y en dias habiles. Se calcula en Java sobre las duraciones del periodo (MySQL no tiene funcion de percentil y el SQL debe ser igual en H2), con un limite de filas y error explicito si se excede.
- Filtros: periodo, unidad, ambito, complejidad, canal, materia y modalidad (GENERAL o NNA).
- Agrupacion por unidad o ambito; por persona solo con `REPORTES_PERSONAS`.

**Exportacion** (`EXPORTACION`)
- CSV UTF-8 con BOM, una fila por REQ y otra hoja logica (archivo aparte) por Caso: folios, origen, canal, unidad, complejidad, estados, fechas y tiempos en h y dh.
- Sin nombres, RUN, relato, motivo ni contactos.
- Mismo criterio que `/api/export`: si excede el limite, se rechaza y se pide acotar; nunca se entrega truncado.
- Auditado con los filtros usados.

**Frontend**
- Pantalla "Reportes" con filtros, tarjetas y tablas; barras simples en CSS, sin librerias.
- El boton de exportar solo aparece con `EXPORTACION`.
- Texto fijo: "Reporte de gestion del sistema; su caracter oficial lo define la institucion".

**Pruebas**
- Conjunto sintetico con fechas fijas y medianas y percentiles esperados.
- Alcance por ambito.
- Desglose por persona oculto sin `REPORTES_PERSONAS`.
- Exportacion sin columnas ciudadanas.
- TI 403.
- Calendario aplicado.

## 4. Documentacion que se actualiza

`DECISIONES_FUNCIONALES.md` (seccion 0), `ALCANCE_Y_PENDIENTES.md`, `ARQUITECTURA.md`, `CLAUDE.md` (roles, TI, version y esquema automaticos, nuevos modulos), `INSTALACION_VM.md` y `DESPLIEGUE_VM.md` (sin autoridad habilitante), `deploy/config-ejemplo/*`, y una nueva `docs/PRUEBAS_MANUALES_0_5_0.md` con los recorridos de cada iteracion.

## 5. Protocolo de cada sesion (gate)

1. **Leer**: `CLAUDE.md`, este plan y `docs/BITACORA_0_5_0.md` completa (en especial los hallazgos de los gates cerrados). Tomar el **primer gate con estado ABIERTO** de la tabla de la bitacora. Nunca trabajar dos gates en una sesion.
2. **Comprobar el punto de partida**: `git status` limpio y `git log --oneline -5` con el commit `gate GNN` del ultimo gate cerrado (o la base `cd21f6e` para G00). Si hay cambios sin commit o el historial no coincide, detenerse e informar.
3. **Inventariar antes de editar**: buscar todos los usos de lo que se va a cambiar (clases, comandos, columnas, textos, pruebas, scripts, documentos) y presentar al usuario el inventario y el plan de cambios del gate. Esperar su confirmacion.
4. **Editar** solo lo que pide el gate. Lo que quede fuera se registra como pendiente para el gate que corresponda; no se adelanta trabajo de otros gates.
5. **Comprobar**, siempre con la suite completa (no solo las pruebas nuevas):
   - backend: `./mvnw -B -ntp -f backend/pom.xml test`;
   - frontend, si el gate lo toca: `cd frontend && npm run typecheck && npm test && npm run build`;
   - humo, si el gate cambia comportamiento visible: `npm run build`, `./mvnw -B -ntp -f backend/pom.xml package -DskipTests`, `java -jar backend/target/siger-a.jar --spring.profiles.active=local --siger.data=./target/humo-gate` y recorrido por `http://localhost:18543` (las claves sinteticas quedan en `./target/humo-gate/bootstrap-usuarios.csv`). No usar el `var/` del usuario.
   - Si algo falla y no se puede resolver dentro del alcance del gate, no se cierra: se registra el estado como BLOQUEADO con la causa y se informa.
6. **Cerrar**: completar el registro del gate en la bitacora con el formato de la seccion 7, cambiar su estado a CERRADO en la tabla y hacer un solo commit `gate GNN: <titulo>` con codigo, pruebas, documentos y bitacora. El mensaje del commit identifica el gate; no hace falta anotar el hash.
7. **Detenerse** y avisar al usuario: que se hizo, como verificarlo manualmente (si aplica) y cual es el gate siguiente.

Si un gate resulta mas grande de lo previsto, la sesion puede dividirlo: cierra `GNNa` con lo implementado y verificado, y agrega `GNNb` como ABIERTO en la tabla, con su alcance restante.

## 6. Gates

| Gate | Titulo | Alcance (seccion 3) | Verificacion especifica |
|---|---|---|---|
| G00 | Linea base | Sin cambios de codigo. Confirmar JDK 25 y Node 24, ejecutar la suite completa en este equipo y registrar resultados. Agregar a `CLAUDE.md` una seccion breve "Trabajo en curso 0.5.0" que apunte a este plan y a la bitacora. | Conteo de pruebas JUnit y frontend; cualquier falla de la base queda registrada y se informa antes de seguir. |
| G01 | Catalogo de roles y TI administrador | It. 1 backend: catalogo unico de roles; `Personnel` exige TI en funciones y subrogancias; elimina `authority()`; `Queries.workspace`/`administration`; dashboard; varios distribuidores; incompatibilidad TI en `GRANT_CREATE`. Seccion 0 a `DECISIONES_FUNCIONALES.md`. | Pruebas de TI: concede y revoca, no autoconcesion, incompatibilidad en ambos sentidos, 403 de TI en fichas y bandejas, varios distribuidores reciben aviso, una jefatura por ambito. |
| G02 | Comando PROFILE_SET | It. 1 backend: `PROFILE_SET` con diferencia, validacion previa y un solo `epoch`. | Diferencia correcta, idempotencia, revision obsoleta 409, sin cambios parciales ante una regla violada. |
| G03 | Supresion de AUTORIDAD e inicializacion | It. 1: rutina idempotente de supresion; `Bootstrap` demo e institucional; `deploy/config-ejemplo`; `Init-Server.ps1`; pruebas existentes que usan `autoridad`; `INSTALACION_VM.md` y `DESPLIEGUE_VM.md`. | Rutina idempotente con historia conservada; archivo institucional con `autoridad` rechazado con mensaje claro; `Check-Scripts.ps1` si hay PowerShell disponible (`pwsh`), si no, registrarlo como pendiente para G14. |
| G04 | Perfil de cuenta en la interfaz | It. 1 frontend: componente de perfil, `Substitutions.tsx`, `menu.ts`, `lib/access.ts`, `labels.ts`. | typecheck, `npm test`, build y humo: TI crea una cuenta con Distribucion + Administrativo + Profesional en un paso y esa cuenta ve su menu. |
| G05 | Consulta institucional: lectura | It. 2 backend: rol, `Access`, `Queries`, `Attention.mayRead`, `Documents` (lectura y descarga), exclusion de reservados. | Fichas COM/SOL/REQ/CAS/FLUJO y NNA legibles; reservados invisibles; barrido de todos los comandos de `Workflow.run` sin exito para un lector puro. |
| G06 | Consulta institucional: bandejas e interfaz | It. 2: `BoardQueries.Visibility`, dashboard, menu y aviso en fichas. | Equivalencia `Access` vs `BoardQueries` en todas las bandejas para el lector y una cuenta comun; humo con un lector. |
| G07 | Indice de personas (V11) | It. 3: migracion V11, normalizacion, `PersonIndex.refresh` en los comandos, carga inicial con marcador. | Normalizacion (tildes, puntos, DV K); cada fuente indexada; refresco al guardar ficha; carga inicial idempotente. |
| G08 | Buscador | It. 3: `GET /api/search`, filtro por permisos, auditoria HMAC, pantalla y paleta Ctrl+K. | Folio, RUN y nombre; permisos; TI 403; auditoria sin el termino en claro; humo. |
| G09 | Calendario habil (V12) | It. 4: `business_holiday`, `HOLIDAY_ADD`/`HOLIDAY_REMOVE`, `BusinessCalendar`, pantalla de feriados para TI. | Fines de semana, feriados, mismo dia, cambio de horario de Chile. |
| G10 | Hitos (V13) | It. 4: tabla `milestone`, escritura en cada comando, carga historica. | Un hito por cada transicion; carga historica idempotente con `reconstructed`. |
| G11 | Reportes: calculo | It. 5 backend: roles `REPORTES`, `REPORTES_PERSONAS`, `EXPORTACION`; catalogo de reportes; estadisticas; endpoints. | Conjunto sintetico con mediana y p90 esperados; alcance; desglose por persona oculto sin su rol; TI 403. |
| G12 | Reportes: interfaz y exportacion | It. 5: pantalla de reportes, CSV sin datos ciudadanos, auditoria. | Columnas exportadas; rechazo al exceder el limite; humo. |
| G13 | Integracion MySQL | `MySql84IntegrationTest` extendido a V11-V13, buscador, reportes y perfil. | Requiere un MySQL 8.4 desechable (`-Dsiger.mysql.base=...`); si no hay, queda para el PC Windows. |
| G14 | Cierre de version | Documentacion de la seccion 4, `PRUEBAS_MANUALES_0_5_0.md`, revision final de `CLAUDE.md`, `Build.ps1 -IncludeJava` y `Check-Scripts.ps1` en Windows, `SOURCE_SHA256.txt`. | Build completo y paquete `release\SIGER-<fecha>.zip`. |

## 7. Formato del registro de cierre (en la bitacora)

Maximo 20 lineas por gate; lo importante es lo que necesita la siguiente sesion.

```
### GNN - <titulo>   (CERRADO | BLOQUEADO)
- Fecha: AAAA-MM-DD (commit `gate GNN: ...`)
- Hecho: <cambios principales, en 2-5 lineas>
- Archivos clave: <rutas>
- Verificacion: JUnit <n> ok (antes <m>); frontend <n> ok; build ok; humo <ok | no aplica>
- Desvios del plan: <ninguno | que y por que>
- Hallazgos para gates siguientes: <datos que la proxima sesion debe saber>
- Pendientes: <tarea -> gate que la toma>
- Verificacion manual sugerida al usuario: <pasos | no aplica>
```

## 8. Prompt de cada sesion (igual para todos los gates)

> Lee `CLAUDE.md`, `docs/PLAN_0_5_0.md` y `docs/BITACORA_0_5_0.md`. Trabaja solo el primer gate ABIERTO siguiendo el protocolo de la seccion 5 del plan: muestrame el inventario y el plan de cambios antes de editar, verifica con la suite completa, cierra el gate en la bitacora con el formato de la seccion 7, haz commit y detente.
