# SIGER 0.9.1 - plan de implementación

Ampliación contractual, parte habilitable: **antecedentes complementarios** solicitados por la gestión y aportados por
el requirente en el Formulario Público con ClaveÚnica, y **aviso de cierre de gestión** por correo. Decisiones del
responsable del 2026-11-26 (aquí D58-D74). Versión 0.9.1, prefijo de gates **A**, registro en
`docs/BITACORA_0_9_1.md`, diseño de referencia en `docs/DISENO_0_9_1.md`, hoja de ruta de la serie en
`docs/PLAN_0_9_0.md`. Es la única versión de la serie que modifica el repositorio `formulario-web` (gates
**[FORMULARIO]**). Este plan solo cambia con aprobación del responsable; si una sesión encuentra que es incorrecto o
incompleto, lo registra como desvío o hallazgo y, si bloquea el gate, se detiene.

Fuera de esta versión, en el bloque PDF/SMS (`docs/PLAN_0_9_X_PDF_SMS.md`): el PDF de cierre adjunto al aviso y los
SMS. Bloqueados por insumos de la Defensoría (plantilla del PDF y proveedor de SMS).

## 0. Decisiones (las copia A00 a `docs/DECISIONES_FUNCIONALES.md` como "Precisión 0.9.1")

### Antecedentes complementarios

- **D58 Invitación.** El profesional del Caso (quien puede escribir en el Caso abierto) solicita antecedentes al
  requirente desde el CAS. Solo se puede invitar a un requirente con **RUN verificado por ClaveÚnica** y con **correo
  declarado**; si falta cualquiera de los dos, SIGER no ofrece la acción. Sin ClaveÚnica no hay invitación.
- **D59 Correo y enlace.** Graph envía la invitación al correo declarado por el requirente, con un texto fijo mínimo y
  un enlace al Formulario (`https://formulario.defensorianinez.gob.cl/antecedentes#<token>`). El token es aleatorio
  (256 bits) y viaja en el fragmento de la URL, que el navegador no envía al servidor: no queda en registros de acceso
  ni en `Referer`. El enlace no lleva RUN, folio interno ni datos del expediente.
- **D60 Identidad.** En el Formulario, el aporte exige autenticarse **exclusivamente con ClaveÚnica** (sin la vía de
  identificación declarada) y que el RUN autenticado coincida con el esperado. Si no coincide, la invitación venció,
  fue revocada o ya se usó, el Formulario muestra un mensaje genérico que no revela nada.
- **D61 Vigencia y uso.** Vigencia de **17 días corridos**. **Una sola entrega** por invitación, con texto y adjuntos
  opcionales. Si se necesita más, el profesional crea otra invitación.
- **D62 Admisión automática.** La entrega llega por el PULL/ACK existente como una COM nueva y queda **vinculada y
  admitida automáticamente** al REQ y al CAS que la solicitaron (actor técnico, fundamento "Antecedentes solicitados por
  la gestión", auditoría). Se avisa al profesional. Los archivos aparecen en "Documentos del requiriente" del Caso. En
  estadística cuenta como VINCULADA, no como requerimiento nuevo.
- **D63 Cierre.** La invitación **muere automáticamente al cerrarse el CAS que la emitió**. Una entrega que cruce el
  cierre no se pierde: entra como COM ordinaria a Distribución, sin admisión automática.
- **D64 Reutilización.** Se reutiliza lo existente: PULL/ACK v3, custodia, integridad y D17; vínculo COM-REQ-CAS;
  panel "Documentos del requiriente"; ClaveÚnica y carga de adjuntos del Formulario; Graph con su outbox; auditoría y
  avisos. Lo nuevo: la invitación (SIGER y Formulario), el canal de creación y revocación SIGER→Formulario, la pantalla
  del Formulario, la variante de entrega y las plantillas de correo al ciudadano.
- **D65 Habilitación.** Las invitaciones se despliegan **apagadas por configuración** en SIGER y en el Formulario. Se
  encienden cuando ClaveÚnica esté certificada por Gobierno Digital y el responsable haya acreditado un ingreso real de
  punta a punta con sus propios datos. El aviso de cierre no depende de ClaveÚnica.
- **D66 Lo que debe aportar.** Al invitar, el profesional escribe un texto breve con lo que el requirente debe aportar.
  Ese texto se muestra solo dentro del Formulario, después de ingresar con ClaveÚnica; nunca va en el correo ni en la
  auditoría.
- **D67 Estado visible.** El CAS muestra el estado de cada invitación (pendiente de publicación, vigente, entregada,
  vencida, revocada) y el profesional puede revocarla.

### Aviso de cierre

- **D68 Check opcional.** Al cerrar un CAS, el profesional puede marcar **"Dar aviso de cierre de gestión"**. Viene
  desmarcado y solo aparece si el requirente tiene correo declarado.
- **D69 Envío y trazabilidad.** Graph envía el aviso al correo declarado mediante el outbox existente. Se registran
  destinatario, fecha, intentos y resultado. La aceptación de Graph no acredita la entrega. Queda auditado quién marcó
  el aviso y cuándo, aunque el envío falle.
- **D70 Contenido.** En 0.9.1 el aviso es un correo con texto institucional mínimo. El bloque PDF/SMS le agrega después
  el PDF de cierre adjunto (plantilla de la Defensoría) y el SMS.
- **D71 Destinatarios.** El aviso no exige ClaveÚnica: basta el correo declarado. Al incorporar el PDF se reevalúa si
  su contenido exige una verificación adicional.
- **D72 Textos.** Los correos de invitación y de aviso de cierre usan textos fijos y sencillos definidos por el equipo
  de SIGER (propuesta en `DISENO_0_9_1.md` 5.3; el texto final se aprueba en el GO de A03).

### Despliegue y confianza

- **D73 Despliegue.** Primero el paso U de SIGER 0.9.1; después la actualización **en sitio** del Formulario con su
  procedimiento vigente (`docs/ACTUALIZACION_UBUNTU_PRODUCCION.md` de `formulario-web`), con paquete de release y
  simulacro aislado de restauración y migración. Sin reinstalación limpia.
- **D74 Confianza.** La confianza Formulario-SIGER no cambia: Bearer de integración, claves Ed25519 del Formulario, par
  ACK de SIGER en DPAPI y CA del TLS se conservan; no se crean ni regeneran credenciales. Las invitaciones viajan con
  el Bearer existente y además van **firmadas por SIGER con su clave ACK**, con la etiqueta de dominio
  `SIGER-INVITACION-v1`, para que el Bearer solo no baste para emitirlas.

## 1. Punto de partida (verificado al planificar, 2026-11-27)

- SIGER: `main` = `203d15c` (planificación de esta serie), sobre `v0.8.1` = `99c31b2`. Versión 0.8.1 (rediseño visual del
  explorador estadístico e iconografía, D52-D57; solo interfaz sobre `v0.8.0` = `842d012`), esquema 24 (V1-V24).
  Producción (DDN-SIGER) en 0.8.0 desde el 2026-11-26 (paso U, U0-U6 conformes; U7 pendiente con UEP); actualización a
  0.8.1 pendiente (paso U81 del responsable). El paso U de 0.9.1 parte de 0.8.0 o de 0.8.1, ambos con esquema 24.
- Pruebas al cierre de 0.8.0 (E14): JUnit H2 388 ok y 4 omitidas; MySQL 8.4 392 ok y 4 omitidas; frontend 252 ok;
  `Check-Scripts.ps1`: 40 scripts, 81 órdenes en 63 procesos. A00 vuelve a contar sobre el HEAD.
- Formulario: `main` = `6f886eb` (15/11/2026). Producción en DDN-UBUNTU con RC1 `c16d135` (04/11/2026). Flyway del
  Formulario hasta V13 (`db/publico/{h2,mysql}`). Acceso temporal con contraseña hasta el 15/12/2026 00:00.
  ClaveÚnica conectada al proveedor; certificación pendiente (`CU_CALLBACK_APPROVED`).
- Piezas de SIGER que se reutilizan (detalle y líneas en `DISENO_0_9_1.md` 1): `document_item` y
  `external_document` (originales de la COM), `communication_link` (con `target_case_id`), `Queries.incomingDocuments`
  y el panel "Documentos del requiriente" (`Files.tsx`), `FormularioClient`/`FormularioReception`, `MailOutbox`,
  `MailWorker` y `GraphMail`, `Access.notice`, `CASE_CLOSE`.

## 2. Reglas para todos los gates (condiciones del responsable)

1. **Inmutables**: `v0.5.0` a `v0.8.1`, sus paquetes y evidencias, los respaldos y constancias de las VM. El ejecutor
   no modifica DDN-SIGER ni DDN-UBUNTU.
2. **Recepción del Formulario en SIGER**: `FormularioClient`, `FormularioConsumer`, `FormularioAutomatic`,
   `FormularioPull`, `ExternalOriginal`, `ExternalProjection`, `FormularioReception` e
   `IntakeCommands.conserveExternal` solo cambian en A02 y de forma aditiva: los documentos 1.0, 1.1 y NNA se procesan
   igual que en `v0.8.1` (regresión con sus vectores de prueba). Ningún código actualiza ni borra
   `communication_original`, `external_person`, `external_attribute`, `external_field` ni `external_document`.
3. **Scripts**: `git diff v0.8.1 -- scripts/ .mvn/` vacío en toda la versión. Las propiedades nuevas van a
   `deploy/config-ejemplo/*` comentadas y apagadas.
4. **Migraciones SIGER**: V1-V24 no se modifican; V25 es aditiva y portable (H2 `MODE=MySQL` y MySQL 8.4);
   `MySql84UpgradeTest` y `MySql84Upgrade070Test` pasan hasta V25, y A00 agrega la prueba de actualización desde un
   volcado sintético de 0.8.0.
5. **Migraciones Formulario**: V1-V13 no se modifican; V14 es aditiva en H2 y MySQL; Flyway nunca baja.
6. **Escritura y lectura**: toda escritura de SIGER por `Workflow.run`; ninguna lectura recibe rol, autor ni alcance
   del navegador; visibilidad doble (`Access` y `BoardQueries.Visibility`) con su equivalencia si cambia.
7. **Reglas vigentes**: D1-D57 no cambian salvo lo que digan D58-D74. D2 (TI sin lectura), D17 (liberación por
   análisis en origen) y D25 (estadística de ingresos) intactos.
8. **Inmutabilidad** (D35): no se invita ni se admite sobre un Caso cerrado; los comandos nuevos se clasifican en
   `ImmutabilityTest`.
9. **Datos personales y secretos**: el token en claro solo existe en el cuerpo del correo pendiente en el outbox
   (hasta que el envío queda en estado final o la invitación vence, momento en que se minimiza), en el correo enviado
   y en el navegador del ciudadano; en ambos sistemas se guarda solo su huella SHA-256. Nunca en logs, URLs de
   servidor, auditoría, bitácora ni salidas. El texto de lo requerido (D66) no va al correo ni a la
   auditoría. RUN nunca en URL. Los correos al ciudadano no llevan datos del expediente más allá del folio SIGER.
10. **Apagado por defecto**: toda función nueva de SIGER y del Formulario arranca apagada; encenderla es un paso del
    responsable en producción (D65). El Formulario rechaza encender las invitaciones sin ClaveÚnica aprobada.
11. **Confianza** (D74): ninguna credencial, clave ni certificado nuevo o regenerado.
12. **Dependencias**: ninguna librería nueva sin autorización expresa.
13. **Mutación**: cada regla nueva con una prueba que falla si se rompe; evidencia "regla -> prueba que falló".
14. **Facilidad de uso**: todo gate de interfaz declara cómo lo comprobó (texto, flujo, ayuda, móvil) y no expone
    códigos ni términos técnicos.
15. **Comandos y entornos**: los de `CLAUDE.md` de cada repositorio; MySQL 8.4 solo aislado y desechable en
    `127.0.0.1`; Graph, Formulario y ClaveÚnica siempre falsos o simulados en pruebas (nunca red real); en TEST-SIGER,
    rutas de ensayo nuevas (`D:\Ensayo-A0n`).
16. **Documentos**: las órdenes `powershell.exe ... -File` solo en las guías que enlaza `Check-Scripts.ps1`.

## 3. Relación con producción y plazo

- 0.9.1 no cambia la producción hasta su paso U. Orden (D73): paso U de SIGER 0.9.1 (redacta A05, ejecuta el
  responsable con autorización expresa, respaldo y verificación en la misma ventana); luego la actualización en sitio
  del Formulario; luego, por separado, la habilitación (D65).
- El aviso de cierre (D68-D72) puede encenderse desde el paso U, porque solo depende de Graph, ya operativo.
- Plazo de la serie: 31/12/2026; calendario de referencia en `PLAN_0_9_0.md` 2. El plazo no reduce pruebas,
  mutaciones, auditoría ni ensayo.
- Trabajo en paralelo del responsable: GO oportunos; respuestas a las definiciones (bitácora); gestión de la
  certificación de ClaveÚnica.

## 4. Documentación que se actualiza

SIGER: `DECISIONES_FUNCIONALES.md` (D58-D74, A00), `CLAUDE.md` (estado en A00; arquitectura en cada gate; revisión en
A05), `AGENTS.md` (A00: remite al prompt de `PLAN_0_9_0.md` 5), `ARQUITECTURA.md`, `ALCANCE_Y_PENDIENTES.md`,
`DESPLIEGUE_VM.md` (paso U de 0.9.1 y habilitación, A05), `deploy/config-ejemplo/*`, el nuevo
`docs/PRUEBAS_MANUALES_0_9_1.md` (A05) y `DISENO_0_9_1.md` solo para diferencias aprobadas.
Formulario: `contratos/antecedentes-complementarios-v1.0.md` y sus fixtures (A00), `docs/API.md`, `README.md`,
`docs/ACTUALIZACION_UBUNTU_PRODUCCION.md` (sección "Qué cambia en esta versión", A05), `CLAUDE.md` si su estado lo pide.

## 5. Protocolo de cada sesión (gate)

1. **Leer**: lo que indica el prompt universal (`PLAN_0_9_0.md` 5), este plan, la bitácora completa y las secciones
   de `DISENO_0_9_1.md` del gate. Tomar el **primer gate ABIERTO**; se saltan CERRADO, BLOQUEADO, POSTERGADO y
   EXTERNO. Si el prompt nombra otro gate, detenerse y preguntar. Un gate por sesión. En un gate [FORMULARIO], leer
   también `AGENTS.md` y `CLAUDE.md` del clon del Formulario (ruta: DI-17).
2. **Precondiciones**: **equipo correcto** (el de la columna "Equipo" de la sección 6; se comprueba con el sistema
   operativo y la ruta del clon, DI-17); `git status` limpio y `git pull` al día (en ambos repositorios si el gate es
   [FORMULARIO]); `git log --oneline -5` con el commit del último gate cerrado; dependencias CERRADO; definiciones
   exigidas respondidas en la bitácora. Si el equipo no corresponde, no inventariar ni editar: detenerse e indicar al
   responsable a qué equipo cambiarse. Si falta otra cosa, detenerse.
3. **Inventario y plan antes de editar**: usos de todo lo que se cambia, plan, archivos propuestos, pruebas previstas
   (mutaciones, MySQL, CI del Formulario), riesgos. Esperar el **GO explícito**; un mensaje que solo repite el plan no
   es GO.
4. **Editar** solo lo autorizado. Un archivo fuera de "Archivos autorizados" exige detenerse y consultar, salvo pruebas
   y documentos del propio gate.
5. **Comprobar**: SIGER con la suite completa (H2; MySQL 8.4 si hay SQL nuevo o cambiado; frontend si se toca;
   `Check-Scripts.ps1` si se tocan guías enlazadas; humo si cambia algo visible, en `./target/humo-*`); Formulario con
   sus pruebas locales y la CI remota verde en los tres jobs; mutaciones; condiciones de la sección 2 con `git diff`.
6. **Cerrar**: registro en la bitácora con el formato de la sección 7 y estado CERRADO. Commits: en SIGER,
   `gate ANN: <título>`; en el Formulario, `gate ANN: <título>` con el mismo título. El registro de la bitácora cita el
   commit del Formulario.
7. **Push** a `main` en `origin` de cada repositorio tocado, después de revisar con `git show --stat HEAD` que no
   viajen archivos sensibles (`var/`, `.env`, CSV de claves, bases H2, `release/`, `.tools/`, `*.clixml`, `*.pfx`,
   `*.p12`, `*.pem`, `*.sigerpkg`, secretos o configuración privada del Formulario).
8. **Detenerse** e informar: qué se hizo, cómo verificarlo, cuál es el gate siguiente y **en qué equipo se ejecuta**.
   Si cambia de equipo, decirlo en la primera línea del informe ("Siguiente gate: A08, en CachyOS; haz pull de ambos
   repositorios allí"). No continuar.

### Equipos

- **TEST-SIGER** (Windows Server, `C:\sigera` y `D:\formulario-web`): equipo principal. Todo lo de SIGER (PowerShell
  5.1, `Check-Scripts.ps1`, DPAPI, MySQL 8.4 aislado en `D:\Ensayo-*`, build de entrega) y el desarrollo y las pruebas
  del Formulario.
- **CachyOS** (Linux del responsable, `~/sigera` y `~/formulario-web`): solo lo del Formulario que exige Linux y
  Docker (paquete de release y simulacro aislado; el ensayo de A05 si A00 determina que el Formulario local necesita
  Docker).
- Un gate corre entero en un solo equipo. Al cambiar de equipo: push en el que termina, pull de ambos repositorios en
  el que empieza. La bitácora registra el equipo de cada cierre.
- Si A00 determina que A05 debe correr en CachyOS, lo registra como hallazgo y el responsable actualiza la columna
  "Equipo" de A05 con un commit de documentación antes de A05.

Gates EXTERNO (A06): no los ejecuta el ejecutor ni edita su registro o estado. Con hallazgos en A06, el responsable deja
A06 BLOQUEADO y agrega `A06b` (ABIERTO, corrección) y `A06c` (EXTERNO, repetición), y así sucesivamente. A07 exige el
ciclo de A06 cerrado y ningún gate de corrección ABIERTO. Un gate que resulte mayor se divide (`ANNa` cerrado con lo
verificado y `ANNb` ABIERTO), sin reducir pruebas ni criterios. Un gate BLOQUEADO por una definición pasa a ABIERTO
cuando el responsable registra la respuesta.

### Corte por cuota o cambio de agente

1. **Prevención.** Con menos del 25 % de cuota no se inician corridas largas (suite MySQL, mutaciones). Con menos del
   10 %, resguardar y detenerse.
2. **Resguardo.** Rama `wip/<gate>-<agente>` desde el HEAD del gate en cada repositorio tocado; `git add` con rutas
   explícitas (sin `target/`, `.tools/`, logs, capturas, `.cnf`, claves); commit
   `wip: <gate> de <agente> sin cerrar (no es cierre de gate)` con un bloque «Dónde quedó» (hecho, corridas con su log
   en `D:\Ensayo-<gate>`, pendiente); push de la rama. Nunca en `main`.
3. **Corte sin resguardo.** El responsable no toca los clones ni la carpeta de ensayo; el siguiente agente hace el
   resguardo como encargo especial y se detiene tras el push.
4. **Revisión.** Claude Padre revisa la rama contra el GO y entrega el encargo de cierre. Si el agente que continúa no
   cierra en la misma sesión, registra el GO íntegro en «GO aprobados pendientes de ejecución» de la bitácora.
5. **Validez de la evidencia anterior.** Una corrida vale si ningún archivo de `src` cambió después; una mutación, si su
   archivo no cambió; el humo, si `src/main` no cambió. Cada corrida MySQL usa una ruta nueva (`D:\Ensayo-<gate>b`, ...)
   con el puerto 3417 libre.
6. **Procesos auxiliares.** Cerrar por PID en `finally` navegadores y servidores de humo; no dejar procesos propios;
   nunca cerrar procesos ajenos.
7. **Cierre.** `git merge --squash` a `main` y un solo commit `gate <gate>: ...` por repositorio; registrar como desvío
   quién ejecutó, quién cerró y de quién es cada evidencia. Borrar la rama WIP después de la verificación de Claude
   Padre.
8. **Traspaso.** Toda condición acordada para un gate futuro se escribe en su detalle en este plan, con un commit de
   documentación. El plan es la única fuente del traspaso.

## 6. Gates

Con un solo ejecutor, los gates se ejecutan en el orden de la tabla.

| Gate | Título | Repos | Equipo | Depende de | DISENO |
|---|---|---|---|---|---|
| A00 | Línea base, decisiones, contrato de antecedentes y diseño verificado | ambos [FORMULARIO] | TEST-SIGER | - | 1, 2, 7 |
| A01 | Invitaciones en SIGER: modelo, comandos, revocación y canal firmado hacia el Formulario (servidor) | SIGER | TEST-SIGER | A00 | 2, 3 |
| A02 | Recepción de antecedentes y admisión automática (servidor) | SIGER | TEST-SIGER | A01 | 4 |
| A03 | Interfaz del Caso y correos al ciudadano: invitación y aviso de cierre | SIGER | TEST-SIGER | A02 | 5, 6 |
| A04 | Formulario: invitaciones, ingreso con ClaveÚnica, aporte de antecedentes y variante de entrega | Formulario [FORMULARIO] | TEST-SIGER | A00 | 7 |
| A05 | Integración de punta a punta, MySQL, aceptación y cierre de versión | ambos [FORMULARIO] | TEST-SIGER (CachyOS si A00 lo determina) | A01-A04 | 8, 9 |
| A06 | Auditoría independiente (EXTERNO) | ambos | - | A05 | todo |
| A07 | Build de entrega de SIGER y etiqueta | SIGER | TEST-SIGER | Ciclo de A06 cerrado | 9 |
| A08 | Paquete del Formulario, simulacro aislado y etiqueta | Formulario [FORMULARIO] | CachyOS | A07 | 9 |

Ruta crítica: A00 -> A01 -> A02 -> A03 -> A05 -> A06 -> A07 -> A08 -> pasos en producción. A04 solo depende de A00 y
debe estar CERRADO antes de A05. A07 y A08 son un solo build separado por equipo: SIGER se empaqueta en Windows y el
Formulario en Linux con Docker.

### Detalle de cada gate

Valores comunes cuando un gate no los detalla: dependencias de la tabla; restricciones de la sección 2 completas;
pruebas de la sección 5 sin bajar el número de pruebas del gate anterior; al menos una mutación por regla nueva;
evidencia con el formato de la sección 7; cierre con estado CERRADO, commit(s) y push, y detenerse.

**A00 - Línea base, decisiones, contrato de antecedentes y diseño verificado** [FORMULARIO]
- Alcance, en este orden:
  1. Línea base: contar pruebas sobre el HEAD de ambos repositorios (SIGER: H2, MySQL si hay servidor aislado,
     frontend, `Check-Scripts.ps1` si hay Windows; Formulario: pruebas locales y última CI de `main`). Verificar contra
     el código cada referencia de `DISENO_0_9_1.md` 1; una diferencia se registra como hallazgo y, si afecta una
     decisión, detiene el gate. Determinar si el Formulario local puede levantarse en TEST-SIGER sin Docker para el
     ensayo de A05; si no puede, registrarlo como hallazgo ("A05 en CachyOS").
  2. Contrato: redactar en el Formulario `contratos/antecedentes-complementarios-v1.0.md` (invitación: creación y
     revocación con Bearer y firma `SIGER-INVITACION-v1`; variante de entrega: campos, firma Ed25519 del Formulario,
     referencia a la invitación, RUN autenticado; errores) y sus fixtures firmados con claves de prueba en
     `contratos/fixtures/`. Copiar los fixtures a `backend/src/test/resources/formulario/` de SIGER con
     `PROCEDENCIA.txt` (commit, ruta y SHA-256). Pruebas de SIGER que solo verifican que los fixtures se parsean y
     firman como dice el contrato (sin código de producción nuevo).
  3. Decide la vía de la variante (`DISENO_0_9_1.md` 4.1): misma cola `formulario-completo` con `document_kind`
     nuevo (preferida) o cola propia; registra la evidencia.
  4. Decisiones: copiar D58-D74 textuales a `DECISIONES_FUNCIONALES.md` ("Precisión 0.9.1"); `CLAUDE.md`: estado
     "plan 0.9.1 en curso"; `AGENTS.md`: remitir a `CLAUDE.md` y al prompt de `PLAN_0_9_0.md` 5. Prueba de
     actualización desde un volcado sintético de 0.8.0 (V24) en H2 y MySQL.
- Archivos autorizados: SIGER: `docs/DECISIONES_FUNCIONALES.md`, `CLAUDE.md`, `AGENTS.md`, pruebas y recursos de
  prueba, la bitácora y diferencias de `DISENO_0_9_1.md`. Formulario: `contratos/*` nuevos, `docs/API.md`.
- Restricciones: ninguna línea de `backend/src/main` ni `frontend/src` de SIGER cambia; en el Formulario, nada fuera de
  contratos y documentación.
- Definiciones: DI-17 (ruta del clon del Formulario) respondida.
- Cierre: `gate A00: linea base, decisiones y contrato de antecedentes` en ambos repositorios.

**A01 - Invitaciones en SIGER (servidor)**
- Alcance: V25 (`case_invitation` y `invitation_sync`, `DISENO_0_9_1.md` 2) y las columnas nulas de la marca del
  aviso de cierre en `case_record` (quién y cuándo, DI-16), sin lógica; la lógica es de A03; comandos `CASE_INVITE` y
  `INVITE_REVOKE` por `Workflow.run`; elegibilidad (D58: requirente con RUN autenticado por ClaveÚnica y correo
  declarado; Caso abierto; actor con escritura en el Caso; una invitación vigente por Caso, DI-18); revocación
  automática dentro de `CASE_CLOSE` (D63); vencimiento por tiempo (D61); canal SIGER→Formulario con outbox durable,
  firma `SIGER-INVITACION-v1` con la clave ACK y el Bearer existentes, reintentos y estados (`DISENO_0_9_1.md` 3);
  propiedad `siger.antecedentes.enabled=false` por defecto (con ella apagada, los comandos responden 409 y el canal no
  envía); auditoría sin token, sin texto requerido y sin RUN; ficha del Caso con `invitations` (estado, fechas, actor;
  el texto requerido solo para quien lee el Caso).
- Archivos autorizados: migración V25, `CaseCommands.java` (solo el enganche de `CASE_CLOSE`), clases nuevas de
  invitación y canal, `Workflow.java`, `Access.java` si hace falta una facultad, `Queries.java` (ficha del Caso),
  `FormularioClient.java` (solo métodos nuevos de invitación; regla 2), configuración de ejemplo, pruebas.
- Definiciones: DI-16, DI-18 y DI-20 respondidas.
- Pruebas y mutación: elegibilidad (incluida la restricción de contacto de DI-20) (sin RUN ClaveÚnica, sin correo, Caso cerrado, sin facultad, segunda vigente);
  revocación al cierre en la misma transacción; vencimiento; firma verificable con la clave pública de prueba y
  rechazo con la etiqueta de un ACK; idempotencia del canal; Formulario falso que falla (reintento) y que rechaza
  (estado final); token nunca en auditoría ni logs (prueba que lo busca); `ImmutabilityTest` con los comandos nuevos;
  barrido de `InstitutionalReaderTest`.
- Cierre: `gate A01: invitaciones en SIGER`.

**A02 - Recepción de antecedentes y admisión automática (servidor)**
- Alcance: rama aditiva en la recepción para la variante del contrato (A00), con los fixtures; validación de firma,
  de la referencia a la invitación y del RUN autenticado contra el esperado (defensa en profundidad); creación de la COM
  por el camino existente con sus originales (custodia y D17 sin cambios); si la invitación estaba vigente y el Caso
  abierto: vínculo `ADMITIDA` al REQ con `target_case_id`, actor técnico, fundamento fijo, invitación ENTREGADA, aviso
  al profesional (`Access.notice`), estado de la COM y estadística como VINCULADA; si no: COM NUEVA a Distribución con
  la referencia visible de la invitación vencida o revocada (D63); idempotencia por origen e identificador.
- Archivos autorizados: `FormularioReception.java`, `ExternalOriginal.java` (solo la variante nueva),
  `IntakeCommands.java` (vínculo admitido técnico), clases de invitación de A01, `Queries.java` (referencia en la COM),
  pruebas.
- Pruebas y mutación: regresión de 1.0, 1.1 y NNA con sus vectores (`git diff` de la regla 2 acotado a ramas nuevas);
  admisión con Caso abierto; cruce con el cierre; RUN distinto rechazado sin crear COM; reentrega idempotente;
  documentos visibles en `citizen_documents` del Caso; estadística (VINCULADA, sin requerimiento nuevo, D25);
  concurrencia entre la recepción y `CASE_CLOSE` (un solo resultado coherente).
- Cierre: `gate A02: recepcion de antecedentes y admision automatica`.

**A03 - Interfaz del Caso y correos al ciudadano**
- Alcance: en la ficha del Caso, panel "Antecedentes solicitados" (crear con el texto de lo requerido, estados de D67,
  revocar; acción oculta si no es elegible, con la razón en texto simple); en el cierre del Caso, el check "Dar aviso de
  cierre de gestión" (D68, según DI-16); envíos al ciudadano por el outbox (`DISENO_0_9_1.md` 5): plantillas fijas de
  invitación (solo cuando el Formulario confirmó la publicación) y de aviso de cierre (al cierre efectivo), destinatario
  externo fuera de `siger.mail.allowed-domains` solo para estas dos plantillas, minimización del cuerpo con el enlace
  al quedar en estado final, estados visibles para TI como los demás correos; propiedad
  `siger.mail.citizen.enabled=false` por defecto.
- Archivos autorizados: `MailOutbox.java`, `MailWorker.java`, `GraphMail.java` (sin cambiar endpoints ni credencial),
  clases de A01, `CaseCommands.java` (check del cierre), `Queries.java`, `features/detail/CaseDetail.tsx`,
  `features/detail/*` nuevos, configuración de ejemplo, pruebas.
- Definiciones: DI-16 (acto del check), DI-19 (textos de `DISENO_0_9_1.md` 5.3, aprobados en el GO) y DI-20.
- Pruebas y mutación: Graph falso; avisos internos siguen restringidos a los dominios permitidos; correo ciudadano solo
  con sus plantillas; sin correo declarado, sin check; el aviso no sale si el cierre no se concreta; minimización;
  ningún correo con texto requerido, RUN ni relato; interfaz en 1366×768 y 390×844 (regla 14).
- Cierre: `gate A03: interfaz del caso y correos al ciudadano`.

**A04 - Formulario: invitaciones, ingreso con ClaveÚnica, aporte de antecedentes y variante de entrega** [FORMULARIO]
- Alcance (`DISENO_0_9_1.md` 7): V14 (H2 y MySQL) con la tabla de invitaciones (huella del token, RUN esperado, texto
  requerido, vencimiento, estado, entrega); rutas de integración de creación y revocación (Bearer existente y firma
  `SIGER-INVITACION-v1` verificada con la clave pública institucional ya configurada); página `/antecedentes` que lee
  el token del fragmento, lo canjea por la sesión y limpia la URL; ingreso solo con ClaveÚnica; comparación del RUN;
  mensajes genéricos; formulario de texto y adjuntos reutilizando los componentes y límites actuales (inspector,
  ClamAV, cuotas); una sola entrega (invitación USADA en la misma transacción que crea la entrega); variante firmada en
  la cola elegida en A00; comprobante al ciudadano; propiedad `formulario.antecedentes.habilitado=false` por defecto y
  arranque rechazado si se enciende con ClaveÚnica sin aprobar; el token nunca en logs de la aplicación ni de Caddy.
- Archivos autorizados: los del módulo nuevo del Formulario, `formulario/*` y `identidad/*` solo donde se engancha,
  `entrega/*` para la variante, migraciones V14, front, Caddyfile solo si hace falta para la ruta, pruebas, documentación.
- Restricciones: los recorridos actuales de adultos y NNA, los contratos 1.0/1.1, PISEE, el acceso temporal y su
  contraseña no cambian (regresión completa del Formulario).
- Pruebas y mutación: token inválido, vencido, revocado o usado; RUN distinto; segunda entrega rechazada; carrera de
  dos envíos; firma de invitación inválida o con etiqueta ajena; Bearer sin firma rechazado; apagado por defecto;
  logs sin token (prueba que los inspecciona); CI remota verde en los tres jobs.
- Cierre: `gate A04: antecedentes complementarios en el Formulario` (Formulario) y registro en la bitácora de SIGER
  con commit `docs: cierre de A04`.

**A05 - Integración de punta a punta, MySQL, aceptación y cierre de versión** [FORMULARIO]
- Alcance: ensayo con SIGER y el Formulario locales (identidad simulada del perfil de pruebas del Formulario, nunca
  ClaveÚnica real): invitar, publicar, correo con Graph falso, aportar, PULL/ACK, admisión, aviso, documentos en el
  Caso; cierre con invitación vigente (revocación y entrega cruzada); aviso de cierre. Suite MySQL de SIGER completa en
  `D:\Ensayo-A05`. Versión 0.9.1 en `backend/pom.xml`, `frontend/package.json` y `package-lock.json`. Redactar el paso U
  de 0.9.1 y la habilitación (D65: orden, propiedades en ambos sistemas, prueba real del responsable con sus propios
  datos) en `DESPLIEGUE_VM.md`; `PRUEBAS_MANUALES_0_9_1.md`; sección "Qué cambia en esta versión" de
  `ACTUALIZACION_UBUNTU_PRODUCCION.md` del Formulario; `ALCANCE_Y_PENDIENTES.md`, `CLAUDE.md`, `ARQUITECTURA.md`,
  `README.md`.
- Cierre: `gate A05: integracion y cierre de version` en ambos repositorios.

**A06 - Auditoría independiente** (EXTERNO)
- La realiza Claude Padre sobre el commit de A05 de ambos repositorios: decisiones D58-D74, reglas de la sección 2,
  seguridad del token y de la firma, privacidad, regresión de la recepción, interfaz y documentación. El responsable
  registra el resultado.

**A07 - Build de entrega de SIGER y etiqueta** (TEST-SIGER)
- Alcance: build de entrega con el procedimiento vigente, `SOURCE_SHA256.txt`, etiqueta anotada `v0.9.1`. Ante un
  fallo, BLOQUEADO con la evidencia y sin etiqueta.
- Cierre: `gate A07: build y etiqueta de SIGER`. El informe indica que A08 se ejecuta en CachyOS.

**A08 - Paquete del Formulario, simulacro aislado y etiqueta** [FORMULARIO] (CachyOS)
- Alcance: pull de ambos repositorios; paquete con `scripts/linux/empaquetar-release.py --commit <SHA de A05 o
  posterior>`, sus `SHA256SUMS` y manifiesto; simulacro aislado de restauración y migración de
  `docs/ACTUALIZACION_UBUNTU_PRODUCCION.md` (debe informar `appSaludable: true`, `flywayFailed: 0`, Flyway en V14 y
  `coincideConOrigen: true`); etiqueta anotada `siger-v0.9.1` en `formulario-web`. El paquete queda fuera de Git y el
  responsable lo copia a DDN-UBUNTU. Marcar 0.9.1 CERRADA y 0.9.2 EN CURSO en `PLAN_0_9_0.md` 1. Ante un fallo,
  BLOQUEADO con la evidencia y sin etiqueta.
- Cierre: `gate A08: paquete y simulacro del Formulario` en ambos repositorios, con "Cierre del plan 0.9.1". El informe
  indica que 0.9.2 vuelve a TEST-SIGER.

## 7. Formato del registro de cierre (en la bitácora)

Máximo 25 líneas por gate.

```
### ANN - <título>   (CERRADO | BLOQUEADO)
- Fecha: AAAA-MM-DD (commit SIGER `gate ANN: ...` | commit Formulario `<sha>`), equipo y ejecutor; base `<commits>`
- GO del responsable: <condiciones del GO>
- Hecho: <cambios principales, en 2-6 líneas>
- Archivos clave: <rutas, por repositorio>
- Migraciones: <V25 / V14 y su contenido | no aplica>
- Verificación: JUnit H2 <n> ok (antes <m>); MySQL <n> ok | no aplica; frontend <n> ok; Formulario <pruebas> y CI <run> | no aplica; humo <ok | no aplica>
- Evidencia de mutación: <regla -> prueba que falló | no aplica>
- Condiciones de la sección 2: <comprobadas con git diff>
- Facilidad de uso: <cómo se comprobó | no aplica>
- Definiciones usadas: <DI-xx y su respuesta | ninguna>
- Desvíos del plan: <ninguno | qué y por qué>
- Hallazgos para gates siguientes: <datos que la próxima sesión debe saber>
- Pendientes: <tarea -> gate o paso que la toma>
- Verificación manual sugerida al usuario: <pasos | no aplica>
```

## 8. Prompt universal

Es el de `docs/PLAN_0_9_0.md` sección 5, idéntico en toda la serie.
