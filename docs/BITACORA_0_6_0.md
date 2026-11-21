# SIGER 0.6.0 - bitacora de gates

Estado vivo del trabajo de `docs/PLAN_0_6_0.md`. Cada sesion de Claude Code toma el primer gate ABIERTO, lo cierra aqui con el formato de la seccion 7 del plan, hace commit y push, y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado, con commit y push), BLOQUEADO (detenido; ver su registro), POSTERGADO (fuera del cierre de 0.6.0, con su motivo).

## Estado

| Gate | Titulo | Estado |
|---|---|---|
| H00 | Linea base y cierre administrativo de 0.5.0 | CERRADO |
| H01 | Graph: credencial y prueba controlada | ABIERTO |
| H02 | Cierre de la validacion con recepciones de prueba autorizadas | ABIERTO |
| H03 | Eliminacion de la validacion cerrada con constancia | ABIERTO |
| H04 | Guia de salida a produccion y scripts de operacion | ABIERTO |
| H05 | Cierre de version en Linux | ABIERTO |
| H06a | Build de entrega en Windows | ABIERTO |
| H06b | Ensayo en Windows y etiquetas | ABIERTO |

## Decisiones abiertas del responsable

Se responden aqui (fecha y respuesta) antes del gate o paso indicado.

| Decision | Antes de | Respuesta |
|---|---|---|
| Puertos de produccion iguales a los de la validacion (D10) | H00 | 2026-11-21: confirmados los mismos de la validacion (MySQL 13326 en loopback, HTTPS 18543). |
| Fechas de la seccion 3 y de D13 (automatico 10/12, plan B 12/12) | H00 | 2026-11-21: confirmadas V1-V3 el 04/12, V5-V7 el 08/12, produccion en automatico a mas tardar el 10/12 y plan B el 12/12. |
| Entrega de prueba en la validacion 0.6.0 (D14) | H00 | 2026-11-21: confirmada. |
| Etiqueta `v0.5.0` sobre `4cde534` o ninguna | H06b | |
| Destinatario fijo y cuenta de Windows de la prueba de Graph | V4 | |
| Acceso de funcionarios por red (firewall, PFX institucional, `-AccesoRed`) | V8 | |
| `estructura-inicial.json` real de produccion | V8 | |
| Lectura del estado de `SMRRecepcion` por `Set-Integration.ps1` frente a D15 | H04 | |

## Antecedentes para H00

- Base: `4cde534` (`plan 0.5.0: cierre`) y el commit que agrega este plan y esta bitacora. Cifras de cierre de 0.5.0: JUnit H2 152 ok + 1 omitida (153 ejecutadas; la omitida es `DocumentReleaseTest.defenderNoOperativoFallaCerrado`); suite sobre MySQL 8.4.13 155 ok + 1 omitida; frontend 73; `Check-Scripts.ps1` con `pwsh`: 31 scripts.
- `CLAUDE.md` lineas 10-11: G15 POSTERGADO como requisito para instalar en la VM y "No se trabaja sin instruccion del responsable: ni G15, ni 0.6.0...". H00 lo actualiza.
- `ACTUALIZACION_0_5_0.md` no se ejecuta en la VM. Su contenido vigente (consultas previas de `DESPLIEGUE_VM.md`, cifras de referencia, rutinas de arranque y lineas del log) pasa a `ACTUALIZACION_0_6_0.md` en H05.

## Datos de la VM (auditoria del responsable, 2026-11-21; interfaz Auditoria y operacion y scripts)

- Validacion `D:\SIGER-VALIDACION`: SIGER 0.4.0, esquema V10, base `siger_a_inst`, MySQL 13326 en loopback, HTTPS 18543, `bindAddress` 0.0.0.0. Servicios `SIGER` y `SIGERMySQL` activos con `DDN-SIGER\svc-siger`. Servicio UP.
- Formulario: consumidor AUTOMATICO, `ESPERANDO_ENTREGAS`, ultimo ciclo y ultimo exito 21/11/2026 17:25, sin incidentes, 9 recepciones confirmadas. Las 9 son pruebas del responsable, con sus propios datos y autorizacion (`"validacionPullAutorizada": true`).
- Respaldo `D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109-v2`: integro segun `Test-SigerBackup`, `complete=true`, `restorationTested=false`. Es anterior al PULL: no contiene las 9 recepciones. No tiene ensayo de restauracion.
- Graph: desactivado; no existe `config\graph-client-secret.clixml`. La prueba minima se hizo en el equipo fisico TEST-SIGER, no en la VM: la salida de la VM hacia `login.microsoftonline.com` y `graph.microsoft.com` no esta verificada.
- HTTPS: autofirmado `CN=localhost`, SAN localhost y 127.0.0.1, vence el 09/11/2027. Acceso local por RDP verificado; acceso por red y PFX institucional pendientes.
- Dos originales en cuarentena sin analizar (adjuntos de prueba del responsable). No hay `config\antivirus.json`, aunque el antivirus de la VM esta operativo.
- `Estado.ps1` falla como `svc-siger` en la comparacion del PID, aunque desde una cuenta administradora PID, Java, JAR y puerto coinciden, y la sonda HTTPS como `svc-siger` da UP.
- No existe todavia una instancia de produccion en `D:`.
- Restriccion (D15): solo NEW-SIGER dentro de `D:`; no inspeccionar ni modificar otros volumenes ni el sistema legado.

## Antecedentes por gate (revision del codigo al planificar, sin ejecutar)

- H01: `Import-SecretPackage.ps1:41-42` rechaza si ya existe algun `formulario-*`, y el paquete exige el bloque del Formulario: no sirve para agregar Graph a la validacion ni a produccion. `Export-SecretPackage.ps1` lee SMR (D15). `Environment.ps1` (`Get-GraphMailConfig`) espera `correo-graph.json` (`mode`, `tenant`, `client`, `sender`, `allowedDomains`, `link`) y `graph-client-secret.clixml`. `GraphMail` valida tenant y cliente como GUID y el secreto de 20 a 512 caracteres.
- H02: `Close-Validation.ps1` cuenta todas las filas de `formulario_reception`, incluidas las `RECHAZADA` (sin COM). Las "9 recepciones confirmadas" de la interfaz son las `CONFIRMADA`. "Posterior a la pausa" puede compararse con `integration_control.updated_ms` de `FORMULARIO_PULL` con `paused=TRUE`; el inventario define si se usa `registered_ms`, `created_ms` o ambos. El marcador `config\piloto-cerrado.json` queda dentro de la raiz que H03 elimina: la copia del registro va en `D:\SIGER-CONSTANCIAS`.
- H03: `Backup.ps1` deja `siger-a.sql`, `documents\`, `config-private\` (secretos DPAPI), `hashes.csv`, `table-counts.csv` y `backup.json` (con `sourceRoot`). `Restore-Check.ps1` deja `documents\` y `restore-result.json` (con `schema`) en su destino y el esquema `siger_a_restore_*` en el MySQL propio, dentro del datadir de la raiz. El `mysqlHome` esta fuera de la raiz (en `DESPLIEGUE_VM.md`, `D:\Herramientas\mysql-8.4`; confirmar en `instance.json`) y lo usa produccion. `Uninstall-Service.ps1` conserva datos y renombra `config\service.json`.
- H04:
  - `Estado.ps1`: hipotesis por confirmar. Sin elevacion, WMI no entrega `ExecutablePath` ni `CommandLine` del proceso que lanzo el servicio, y `Own-Process` (`Common.ps1:41`) lo trata como "PID no coincide". Tambien afecta al ciclo manual de `Invoke-FormularioPull.ps1` con el servicio arriba (por ejemplo, en la vuelta atras a Manual). No afecta a `Start.ps1` ni `Stop.ps1` con servicios registrados.
  - `Update.ps1:6-7` acepta el respaldo `previo-pull-20261109-v2`, aunque es anterior a las 9 recepciones.
  - `DESPLIEGUE_VM.md` seccion 4 copia archivos de Graph que la validacion no tiene.
  - `Set-Integration.ps1` consulta `Win32_Service` `SMRRecepcion` (decision abierta frente a D15).
  - `HealthProbe.cs:20` rechaza un certificado vencido: la renovacion lleva fecha.
- H05: desde 0.5.0 la recepcion escribe `COM_REGISTRADA` y refresca `person_reference` (`IntakeCommands.java:114` y `:130`); D14 lo ejercita en la VM. `MySql84UpgradeTest` cubre 0.4.0 -> V14 con un volcado sintetico. `ACTUALIZACION_0_5_0.md` (cifras a, b, c y lineas del log) es la base de `ACTUALIZACION_0_6_0.md`.
- H06a y H06b: rigen los "Antecedentes para G15" de `BITACORA_0_5_0.md` (clon nuevo, `.gitattributes`, `.tools`, Node 24, lock de npm 12, revision del paquete, `Recover-Ti.ps1` con una instancia propia y el comando de `SOURCE_SHA256.txt`), con 0.6.0 en lugar de 0.5.0 y el esquema que derive `Build.ps1`.

## Registros de cierre

### H00 - Linea base y cierre administrativo de 0.5.0   (CERRADO)
- Fecha: 2026-11-21 (commit `gate H00: linea base y cierre administrativo de 0.5.0`; base `a2f055f plan 0.6.0: planificacion`, que registra en la tabla de decisiones los puertos, las fechas y la entrega de prueba confirmados por el responsable).
- Hecho: sin cambios de codigo. `DECISIONES_FUNCIONALES.md`: "Precision 0.6.0" con D10-D16 copiadas del plan (la "seccion 3" remite a `docs/PLAN_0_6_0.md`; sin la lista de decisiones abiertas); la "Decision para 0.6.0" de G14b queda intacta. `CLAUDE.md`: estado "plan 0.6.0 en curso" (G15 absorbido por H06a/H06b; se mantiene la regla de no trabajar sin instruccion) y lineas 126 y 128 (`SOURCE_SHA256.txt` en H06b; actualizacion 0.4.0 -> 0.6.0 en `ACTUALIZACION_0_6_0.md`). `BITACORA_0_5_0.md`: notas de G15 absorbido, sin reescribir registros. `ACTUALIZACION_0_5_0.md`: aviso "No se ejecuta". `ALCANCE_Y_PENDIENTES.md`: requisito del build pasa a H06a y la actualizacion de la validacion a `ACTUALIZACION_0_6_0.md`.
- Archivos clave: `CLAUDE.md`, `docs/DECISIONES_FUNCIONALES.md`, `docs/ALCANCE_Y_PENDIENTES.md`, `docs/ACTUALIZACION_0_5_0.md`, `docs/BITACORA_0_5_0.md`.
- Verificacion: JUnit H2 152 ok + 1 omitida (153 ejecutadas; omitida `DocumentReleaseTest.defenderNoOperativoFallaCerrado`), igual que el cierre de 0.5.0; suite sobre MySQL 8.4.13 (`SELECT VERSION()`, imagen local `mysql:8.4` en contenedor desechable en `127.0.0.1`, esquemas `siger_a_mysql_pruebas` y `siger_a_mysql_inst` creados a mano, contenedor detenido al terminar) 155 ok + 1 omitida (156 ejecutadas, con `MySql84IntegrationTest` 2 y `MySql84UpgradeTest` 1); frontend 73 ok, typecheck y build sin avisos (sin `npm ci`); `Check-Scripts.ps1` con `pwsh`: 31 scripts ok y modo de instancia 8 casos; humo no aplica.
- Entorno: JDK Temurin 25.0.5 LTS, Maven 3.9.11 (`.tools`), Node v26.13.1 y npm 12.2.3 del sistema (no el Node 24 portable de Windows), PowerShell 7.7.0 (`pwsh`, solo sintaxis), Docker con MySQL 8.4.13.
- Desvios del plan: ninguno. Ajustes del responsable al aprobar: intro de la Precision con referencia al plan, correccion de `CLAUDE.md:128` y `ALCANCE_Y_PENDIENTES.md:70`, texto del aviso de `ACTUALIZACION_0_5_0.md`.
- Hallazgos para gates siguientes: `docs/ACTUALIZACION_0_6_0.md` ya esta referido desde `CLAUDE.md`, `ALCANCE_Y_PENDIENTES.md` y `ACTUALIZACION_0_5_0.md`, pero no existe hasta H05. La imagen local `mysql:8.4` es 8.4.13.
- Pendientes: `README.md:8` (G15 postergado) y `:39` (`ACTUALIZACION_0_5_0.md`), `INSTALACION_VM.md:222` y `DESPLIEGUE_VM.md:188` -> H05 (`DESPLIEGUE_VM.md` secciones 3 y 4 -> H04). `PRUEBAS_MANUALES_0_5_0.md:100` queda como esta (documento de 0.5.0; el aviso se explica en `ACTUALIZACION_0_5_0.md`). Decisiones abiertas restantes en la tabla de arriba.
- Verificacion manual sugerida al usuario: leer "Precision 0.6.0" al final de `docs/DECISIONES_FUNCIONALES.md` y la seccion "Estado" de `CLAUDE.md`.
