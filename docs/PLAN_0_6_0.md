# SIGER 0.6.0 - plan de implementacion

Fecha: 2026-11-21. Base: commit `4cde534` (plan 0.5.0 cerrado, version 0.5.0 en codigo, esquema V14). Decisiones tomadas por el responsable del proyecto el 2026-11-21.
Texto comun y estable para todas las sesiones de Claude Code. El trabajo se ejecuta por **gates** (seccion 6): una sesion toma un gate, lo implementa, lo verifica, lo cierra en `docs/BITACORA_0_6_0.md`, hace commit y push, y se detiene. El usuario hace `/clear` y la siguiente sesion toma el gate siguiente.
0.6.0 no agrega funcionalidad de expedientes: prepara la salida a produccion. Objetivo: un solo build 0.6.0, una sola actualizacion de la validacion de la VM (0.4.0 -> 0.6.0), el cierre y la eliminacion de la validacion con constancia, y una instancia de produccion nueva con el mismo release, consumiendo en automatico antes del **15/12/2026 00:00 (America/Santiago)**.
Este documento solo cambia con aprobacion del responsable. Si una sesion encuentra que el plan es incorrecto o incompleto, lo registra en la bitacora como desvio o hallazgo y, si bloquea el gate, se detiene y lo informa. Los datos de la VM y los hallazgos por gate estan en "Antecedentes" de la bitacora.

## 0. Decisiones (agregar a `docs/DECISIONES_FUNCIONALES.md` como "Precision 0.6.0"; las copia H00)

D10. **Produccion reemplaza a la validacion; no conviven.** Produccion es una instancia nueva en `D:\SIGER` (modo institucional, proposito Produccion), inicializada con el mismo release que la validacion actualizada (`-MismoReleaseQue`), con la misma cuenta de servicio (`DDN-SIGER\svc-siger`) y los mismos puertos (MySQL 13326 en loopback, HTTPS 18543). La secuencia es la de la seccion 3: la validacion se cierra y se retiran sus servicios antes de inicializar produccion, y se elimina despues de acreditar produccion.

D11. **Recepciones de prueba autorizadas.** Una validacion con recepciones del Formulario se cierra solo si se cumplen las tres condiciones:
1. las recepciones confirmadas coinciden con la cantidad que el responsable declara al pausar definitivamente (hoy 9; 10 con la entrega de prueba de D14);
2. ninguna recepcion es posterior a esa pausa;
3. no hay ACK pendientes.
Si una falla, no se cierra. El cierre no borra nada (la eliminacion es D12). Una validacion sin recepciones se cierra como hasta ahora.

D12. **Eliminacion de la validacion cerrada, con constancia.**
- Alcance: dentro de `D:\SIGER-VALIDACION` (base, incluidos los esquemas `siger_a_restore_*` de los ensayos de restauracion, documentos, logs, `config\` con los secretos DPAPI y `releases\`); fuera de la raiz, `D:\Respaldos-SIGER-VALIDACION` y los destinos de `Restore-Check.ps1` de esa instancia.
- Nunca: el `mysqlHome` (compartido con produccion), la raiz de produccion ni nada fuera de `D:`.
- Constancia en `D:\SIGER-CONSTANCIAS` (fuera de ambas instancias) y en el registro institucional, sin datos ciudadanos ni secretos. Declara como fuera de alcance los respaldos y snapshots de TI (sujetos a su retencion) y la copia que conserva el Formulario, y aclara que la eliminacion es logica, no forense.

D13. **Acreditacion de produccion.** Solo con `Invoke-FormularioPull.ps1 -SoloComprobar` y produccion en automatico a mas tardar el **10/12/2026**, verificando en Auditoria y operacion "Esperando entregas", ciclos corriendo y ultimo exito reciente (un ciclo en pausa tambien registra exito: lo que cuenta es el estado). No hay entregas de prueba en produccion. **Plan B**: si al **12/12/2026** produccion no esta en automatico, se pausa la validacion, se deja en Manual y se retira `validacionPullAutorizada`; las entregas ciudadanas esperan en la cola del Formulario hasta que produccion consuma.

D14. **Entrega de prueba en la validacion 0.6.0.** Despues de actualizar, el responsable hace una entrega de prueba con sus propios datos (antes del 15/12) para ejercitar en la VM la recepcion con el codigo de 0.5.0 (indice de personas e hito `COM_REGISTRADA`). Entre la pausa definitiva de la validacion y produccion en automatico no se hacen entregas de prueba: `reclamar` no filtra y entrega la pendiente mas antigua.

D15. **Alcance del trabajo en la VM.** Solo NEW-SIGER dentro de `D:`. No se inspeccionan ni modifican otros volumenes ni el sistema legado (SMR). Ningun procedimiento de 0.6.0 depende de SMR: `Export-SecretPackage.ps1` no se usa.

D16. **Correo.** Graph no bloquea la salida a produccion: produccion puede partir con el correo desactivado. La credencial de Graph se instala en la VM como la cuenta de servicio, sin SMR (H01).

**Decisiones abiertas** (cada una se cierra antes del gate o del paso que la necesita y se anota en la bitacora):
- Puertos (D10), fechas (D13 y seccion 3) y entrega de prueba (D14): propuestos en esta version; el responsable los confirma antes de H00.
- Etiqueta `v0.5.0`: no habra paquete 0.5.0. Crearla sobre `4cde534` o no crearla -> antes de H06b.
- Destinatario fijo y cuenta de Windows de la prueba de Graph -> antes de ejecutar H01 en la VM.
- Acceso de funcionarios por red (firewall y PFX institucional; hoy solo esta verificado RDP) -> antes del paso V8 (decide `-AccesoRed` en produccion).
- `estructura-inicial.json` real de produccion (ambitos, sedes, TI y funcionarios) -> antes del paso V8.
- `Set-Integration.ps1 -Formulario Automatico` consulta el estado del servicio `SMRRecepcion` del equipo: confirmar que esa lectura es compatible con D15 -> H04.

## 1. Punto de partida (verificado en el codigo al planificar)

- **Repositorio**: `4cde534`. JUnit H2 152 ok + 1 omitida (153 ejecutadas); suite sobre MySQL 8.4 155 ok + 1 omitida; frontend 73. G15 POSTERGADO; sin etiquetas.
- **Por que produccion y validacion no conviven**:
  - `Install-Service.ps1:19-21` registra siempre `SIGER` y `SIGERMySQL` y rechaza si ya existen.
  - `Init-Server.ps1:36` exige puertos libres (`Free-Port`): con los mismos puertos, la validacion debe estar detenida.
  - `-MismoReleaseQue` (`Init-Server.ps1:30-33`) lee `config\instance.json` de la validacion y compara el SHA-256 del JAR: la validacion debe estar en 0.6.0 y con su raiz intacta al inicializar produccion.
  - `DESPLIEGUE_VM.md` seccion 4 copia `config\formulario-*` desde la validacion. La misma orden copia `graph-client-secret.clixml` y `correo-graph.json`, que la validacion no tiene.
- **Cierre actual**: `Close-Validation.ps1:17-22` cuenta todas las filas de `formulario_reception` (tambien las `RECHAZADA`) y las COM externas, y rechaza si hay alguna. Escribe `config\piloto-cerrado.json` dentro de la raiz; `Start.ps1` se niega a iniciar una instancia con ese marcador.
- **Recepciones y pausa**: `formulario_reception.state` es `RECHAZADA`, `REGISTRADA` o `CONFIRMADA`, con `ack_pending`, `registered_ms` y `acked_ms` (V8). La pausa de TI vive en `integration_control` (`paused`, `updated_ms`, `reason`; V9) y sobrevive a los reinicios. Un ciclo automatico sin entregas registra exito con estado `ESPERANDO_ENTREGAS`, y uno en pausa registra exito con `PAUSADO` (`FormularioAutomatic.java:60` y `:82-83`).
- **Recepcion con codigo de 0.5.0**: `IntakeCommands.conserveExternal` escribe el hito `COM_REGISTRADA` y refresca `person_reference` (`IntakeCommands.java:114` y `:130`). En la VM solo ha recibido 0.4.0.
- **Respaldos**: `Backup.ps1` copia `data\documents` y `config\` (como `config-private`, con los secretos DPAPI). `Restore-Check.ps1` crea un esquema `siger_a_restore_*` en el MySQL propio y copia los documentos a su destino, con `restore-result.json`. `backup.json.restorationTested` queda siempre en `false`: la evidencia del ensayo es `restore-result.json`. `Update.ps1:6-7` acepta cualquier respaldo integro de la misma raiz, base y JAR, sin mirar su antiguedad.
- **Graph**: `Import-SecretPackage.ps1` exige el bloque del Formulario y rechaza si ya existe algun `formulario-*` (lineas 41-42): no sirve para agregar solo Graph a una instancia existente. `Export-SecretPackage.ps1` lee SMR (fuera de alcance, D15). `GraphMail` (client_credentials y sendMail, endpoints fijos, sin registrar cuerpos ni credenciales) existe; no hay un proceso de prueba controlada.
- **Proceso propio**: `Own-Process` (`Common.ps1:38-42`) lanza "PID no coincide..." si `ExecutablePath` o `CommandLine` llegan vacios. `App-Running` lo usa en `Estado.ps1`, en el ciclo manual de `Invoke-FormularioPull.ps1` (linea 18), en `Backup.ps1`, `Migrate.ps1`, `Update.ps1`, `Restore-Check.ps1` y `Recover-Ti.ps1`, y en `Start.ps1`/`Stop.ps1` solo sin servicios registrados (con servicios usan `Start-Service`/`Stop-Service`).
- **HTTPS**: `Init-Server.ps1:78` crea un autofirmado `CN=localhost` (SAN localhost y 127.0.0.1) por 365 dias. `HealthProbe.cs` fija el certificado completo, no valida el nombre y rechaza uno vencido (linea 20): vencido, `Start.ps1` no acredita la salud. `Import-Integrations.ps1 -HttpsPfx` reemplaza el autofirmado por un PFX conservando la clave del almacen.
- **Formulario** (repositorio `formulario-web`): al vencer el acceso temporal el 15/12/2026 pasa a `OPEN` (`docs/ACTUALIZACION_UBUNTU_PRODUCCION.md`); `reclamar` no filtra y entrega la pendiente mas antigua (`docs/SMR_PROCEDIMIENTO_CONEXION_Y_E2E.md`); la retirada de originales tras el ACK no esta habilitada (`docs/RETENCION_ACK_72H_2026-10-26.md`): el Formulario conserva su copia.

## 2. Reglas para todos los gates

- Leer `CLAUDE.md` antes de empezar. V1-V14 no se modifican. 0.6.0 no necesita migraciones; si un gate la necesita, es `V15__...` aditiva, compatible con H2 `MODE=MySQL` y MySQL 8.4, y `MySql84UpgradeTest` (0.4.0 -> ultima) debe seguir pasando.
- Toda escritura de negocio pasa por `Workflow.run`. La visibilidad sigue doble (`Access` y `BoardQueries.Visibility`) con su equivalencia. Nada de datos ciudadanos ni secretos en logs, URLs, salidas de consola, constancias ni en la bitacora.
- Scripts: Windows PowerShell 5.1 y las reglas de `CLAUDE.md` (escribir con la herramienta de archivos; sin `Get-FileHash`, sin asignar `$HOME`, sin `ConvertFrom-Json -AsHashtable`; `Check-Scripts.ps1` sin errores). `pwsh` en Linux solo acredita sintaxis: no ejercita DPAPI, servicios, ACL ni MySQL de Windows. Lo que dependa de eso se acredita en H06b.
- Scripts que cierran o eliminan: actuan solo sobre una instancia de proposito VALIDACION y dentro de `D:`; nunca sobre produccion, el `mysqlHome` ni otros volumenes; fallan cerrado ante cualquier duda; usan `SupportsShouldProcess` y una confirmacion expresa; muestran antes lo que haran; no imprimen secretos ni datos ciudadanos.
- Las reglas de decision llevan pruebas que fallan si se rompe la regla (mutacion). Si una regla queda en PowerShell, el ensayo de H06b incluye un caso de rechazo por cada condicion.
- Linux: `sh ./mvnw -B -ntp -f backend/pom.xml test` y `cd frontend && npm ci && npm run typecheck && npm test && npm run build`; MySQL 8.4 desechable como indica `CLAUDE.md`. `Build.ps1` y el ensayo de scripts son del PC Windows (H06a y H06b).
- Version `0.6.0` en `backend/pom.xml`, `frontend/package.json` y `package-lock.json` (H05). `SOURCE_SHA256.txt` se regenera solo en H06b.

## 3. Secuencia en la VM (la ejecuta el responsable; cada gate se disena contra ella)

Fechas propuestas (decision abierta). Todo con `D:\SIGER-VALIDACION` y `D:\SIGER`; cada paso registra sus cifras sin datos personales.

| Paso | Que | Quien | Fecha objetivo |
|---|---|---|---|
| V1 | Pausa temporal del consumidor de la validacion (Auditoria y operacion, con motivo) | TI | 04/12 |
| V2 | Consultas previas, respaldo nuevo (`Backup.ps1`) y `Restore-Check.ps1` sobre ese respaldo, con las cifras de `ACTUALIZACION_0_6_0.md` | svc-siger | 04/12 |
| V3 | `Update.ps1` 0.4.0 -> 0.6.0 y `Start.ps1` | svc-siger | 04/12 |
| V4 | Reanudar el consumidor; entrega de prueba (D14); `antivirus.json` y analisis de los 2 originales en cuarentena; credencial y prueba de Graph (H01, si esta lista); recorridos de `PRUEBAS_MANUALES_0_6_0.md` | responsable, TI, svc-siger | 05/12-07/12 |
| V5 | Pausa definitiva y declaracion de la cantidad de recepciones (D11) | TI y responsable | 08/12 |
| V6 | Cierre de la validacion (H02) | svc-siger | 08/12 |
| V7 | `Uninstall-Service.ps1` de la validacion | administrador | 08/12 |
| V8 | `Init-Server.ps1` de produccion con `-MismoReleaseQue`, copia de la configuracion del Formulario, `Install-Service.ps1`, `Start.ps1`, `-SoloComprobar`, `Set-Integration.ps1 -Formulario Automatico` y reinicio | svc-siger y administrador | 09/12-10/12 |
| V9 | Observacion de produccion (D13) y tareas de TI tras el primer arranque | TI | 10/12-14/12 |
| V10 | Eliminacion de la validacion con constancia (H03) | svc-siger y administrador | despues de V9 |
| B | Plan B (D13) si al 12/12 produccion no esta en automatico | TI y svc-siger | 12/12 |

Gates de codigo: H00-H04 hasta el 29/11, H05 hasta el 01/12, H06a y H06b hasta el 03/12. Si la VM encuentra un defecto bloqueante en V2-V4, se abre un gate de correccion, se genera 0.6.1 (nuevo H06a/H06b) y se repiten V2-V4 con 0.6.1; el plan B sigue protegiendo el 15/12. Si el calendario aprieta, H01 puede postergarse (D16).

## 4. Documentacion que se actualiza

`DECISIONES_FUNCIONALES.md` (D10-D16 en H00; excepcion a "los scripts no incluyen comandos destructivos" en H03), `ALCANCE_Y_PENDIENTES.md`, `DESPLIEGUE_VM.md` (secuencia de la seccion 3, H04), `INSTALACION_VM.md`, `CLAUDE.md`, `README.md`, `deploy/config-ejemplo/LEEME.txt` si cambia algo, y los nuevos `ACTUALIZACION_0_6_0.md` y `PRUEBAS_MANUALES_0_6_0.md` (H05). `ACTUALIZACION_0_5_0.md` queda marcado como no ejecutable (H00).

## 5. Protocolo de cada sesion (gate)

1. **Leer**: `CLAUDE.md`, este plan y `docs/BITACORA_0_6_0.md` completa (antecedentes y hallazgos de los gates cerrados). Tomar el **primer gate con estado ABIERTO**; si el prompt nombra otro gate, detenerse y preguntar. Nunca trabajar dos gates en una sesion.
2. **Comprobar el punto de partida**: `git status` limpio y `git log --oneline -5` con el commit `gate HNN` del ultimo gate cerrado (para H00, el commit que agrego este plan). Si no coincide, detenerse e informar.
3. **Inventariar antes de editar**: buscar todos los usos de lo que se va a cambiar y presentar el inventario y el plan de cambios del gate. Esperar la confirmacion **explicita** del responsable. Un mensaje que solo repite el plan, o que no responde a lo propuesto, no es una aprobacion: preguntar.
4. **Editar** solo lo que pide el gate. Lo que quede fuera se registra como pendiente para el gate que corresponda.
5. **Comprobar**, siempre con la suite completa:
   - backend: `sh ./mvnw -B -ntp -f backend/pom.xml test`;
   - frontend, si el gate lo toca: `cd frontend && npm run typecheck && npm test && npm run build`;
   - scripts, si el gate los toca: `Check-Scripts.ps1` con `pwsh`;
   - SQL nuevo o cambiado: suite sobre el MySQL 8.4 desechable;
   - humo, si cambia comportamiento visible (como en 0.5.0, con datos en `./target/humo-*`, nunca el `var/` del usuario).
   - Si algo falla y no se resuelve dentro del alcance, el gate queda BLOQUEADO con la causa y se informa.
6. **Cerrar**: registro en la bitacora con el formato de la seccion 7, estado CERRADO en la tabla y un solo commit `gate HNN: <titulo>` con codigo, pruebas, documentos y bitacora.
7. **Push** a `main` en `origin`. Antes, revisar con `git show --stat HEAD` que no viajen archivos sensibles: `var/`, `.env`, CSV de claves, bases H2, `release/`, `.tools/`, `*.clixml`, `*.pfx`, `*.p12`, `*.pem`, `*.sigerpkg`.
8. **Detenerse** y avisar: que se hizo, como verificarlo y cual es el gate siguiente.

Si un gate resulta mas grande de lo previsto, se divide: se cierra `HNNa` con lo implementado y verificado y se agrega `HNNb` como ABIERTO, con su alcance restante.

## 6. Gates

| Gate | Titulo | Alcance | Verificacion especifica |
|---|---|---|---|
| H00 | Linea base y cierre administrativo de 0.5.0 | Sin cambios de codigo. Suite completa (H2; MySQL si hay contenedor). D10-D16 en `DECISIONES_FUNCIONALES.md`. `CLAUDE.md`: estado "plan 0.6.0 en curso" (apunta a este plan y a la bitacora; G15 absorbido por H06a/H06b; la regla de no trabajar sin instruccion se mantiene, sin la mencion a G15 y 0.6.0). `BITACORA_0_5_0.md`: nota de G15 absorbido, sin reescribir registros. `ACTUALIZACION_0_5_0.md`: aviso inicial "No se ejecuta: la validacion pasa de 0.4.0 a 0.6.0 con `ACTUALIZACION_0_6_0.md`". `ALCANCE_Y_PENDIENTES.md`: el requisito de G15 pasa a H06a. | Conteos iguales a los del cierre de 0.5.0 (152 + 1 en H2, 73 en frontend); una diferencia se registra antes de seguir. |
| H01 | Graph: credencial y prueba controlada | (1) Instalar solo la credencial de Graph (tenant, cliente, remitente y secreto, pedidos de forma privada) en una instancia existente, como la cuenta de servicio, con DPAPI, sin SMR ni el bloque del Formulario, sin sobrescribir y con el correo DESACTIVADO. (2) Prueba controlada en un proceso sin web ni tareas: solo token, o un correo de texto fijo a un destinatario institucional fijo; no usa la cola de avisos ni datos ciudadanos; informa solo estado, fase, HTTP y request-id. | Pruebas con Graph falso (nunca red real) que fallan si se envia sin pedirlo, si el secreto aparece en la salida o si se toca la cola; `Check-Scripts.ps1`. Ejecucion real: paso V4. |
| H02 | Cierre de la validacion con recepciones de prueba autorizadas | `Close-Validation.ps1` con D11: cantidad declarada por parametro igual a las `CONFIRMADA`; ninguna `REGISTRADA` ni `ack_pending`; pausa de `FORMULARIO_PULL` vigente y ninguna recepcion posterior a ella; el inventario define el tratamiento de las `RECHAZADA` (sin COM). Conserva los datos. Marcador y registro de cierre solo con cifras, fecha, cuenta y motivo; copia del registro en `D:\SIGER-CONSTANCIAS`. Sin recepciones, el comportamiento no cambia. Comentarios del script. | Un rechazo por cada condicion (cantidad distinta, posterior a la pausa, ACK pendiente, sin pausa, proposito distinto de VALIDACION) y el caso valido: pruebas automatizadas si la regla se implementa en Java, o ensayo de H06b si queda en PowerShell; `Check-Scripts.ps1`. |
| H03 | Eliminacion de la validacion cerrada con constancia | Script nuevo (primer procedimiento destructivo del repositorio) con D12: exige el marcador de cierre de H02, servicios retirados, motor detenido y una produccion distinta; inventario previo dentro de la raiz y en las rutas externas indicadas por parametro (cada respaldo verificado por `backup.json.sourceRoot`, cada ensayo por `restore-result.json`); confirmacion expresa; constancia escrita antes de borrar y completada con el resultado de cada elemento. Excepcion registrada en `DECISIONES_FUNCIONALES.md`. | Rechazos: sin marcador, con servicios registrados, raiz de produccion o fuera de `D:`, respaldo de otra instancia, ruta dentro del `mysqlHome`; constancia sin datos ciudadanos ni secretos; `Check-Scripts.ps1`. Ejecucion real: H06b y paso V10. |
| H04 | Guia de salida a produccion y scripts de operacion | `DESPLIEGUE_VM.md` secciones 3 y 4 reescritas como la secuencia de la seccion 3: comprobaciones, plan B, puertos, `-MismoReleaseQue`, copia de la configuracion del Formulario, Graph por H01, estructura real y `-AccesoRed` segun las decisiones abiertas. HTTPS: autofirmado con advertencia, PFX con `Import-Integrations.ps1 -HttpsPfx` o solo RDP, y renovacion antes del vencimiento. Tareas de TI tras el primer arranque: feriados 2026-2027, region de cada sede, clave del buscador y `antivirus.json`. Comentarios de `Environment.ps1` (`Get-InstancePurpose`) e `Init-Server.ps1`. `Estado.ps1` como la cuenta de servicio: causa y correccion sin debilitar la comprobacion de proceso propio. `Update.ps1`: exigencia sobre la antiguedad del respaldo. Si el inventario lo justifica, se divide en H04a (scripts) y H04b (guia). | `Check-Scripts.ps1`; cada orden de la guia existe con esos parametros; la correccion de `Estado.ps1` se acredita en H06b como la cuenta de servicio. |
| H05 | Cierre de version en Linux | Version 0.6.0. `ACTUALIZACION_0_6_0.md` directo desde 0.4.0 (pasos V1-V4: consultas previas y cifras de 0.5.0 vigentes, respaldo nuevo con el consumidor pausado, `Restore-Check.ps1` obligatorio, `Update.ps1`, comprobaciones, entrega de prueba y analisis antivirus; de las tareas de TI de 0.5.0, solo las que necesitan las pruebas: la validacion se elimina). `PRUEBAS_MANUALES_0_6_0.md` (validacion 0.6.0, Graph, cierre, produccion segun D13, eliminacion). Resto de la seccion 4. | Suite completa en H2 y sobre MySQL 8.4 (con `MySql84UpgradeTest`); frontend; `Check-Scripts.ps1`; humo con health 0.6.0. |
| H06a | Build de entrega en Windows | Antecedentes de G15 (`BITACORA_0_5_0.md`) aplicados a 0.6.0: `Build.ps1 -IncludeJava` con Node 24 portable, `Check-Scripts.ps1` en Windows PowerShell 5.1, `npm ci` con el lock de npm 12 (si lo rechaza, detenerse y registrarlo) y revision del paquete. Sin etiquetas ni `SOURCE_SHA256.txt`. | `manifest.json` con 0.6.0, esquema derivado y `automatedTestsExecuted` true; JUnit en Windows registrado tal cual; paquete sin archivos prohibidos. |
| H06b | Ensayo en Windows y etiquetas | Con el paquete de H06a, en el PC Windows: instancia propia Institucional/Validacion con MySQL 8.4 ZIP y una cuenta local sin privilegios como cuenta de servicio; servicios; `Estado.ps1` como esa cuenta; recepciones simuladas; cierre (H02) con un rechazo por condicion y el caso valido; `Uninstall-Service.ps1`; produccion de ensayo con `-MismoReleaseQue` y los mismos puertos; eliminacion (H03) con constancia; `Recover-Ti.ps1` (pendiente de G15). Al final, `SOURCE_SHA256.txt` y etiquetas `v0.6.0` (y `v0.5.0` segun la decision abierta). | Resultado de cada paso; nada tocado fuera de las rutas del ensayo. Un defecto abre un gate de correccion y un nuevo H06a antes de la VM. |

## 7. Formato del registro de cierre (en la bitacora)

Maximo 20 lineas por gate; lo importante es lo que necesita la siguiente sesion.

```
### HNN - <titulo>   (CERRADO | BLOQUEADO)
- Fecha: AAAA-MM-DD (commit `gate HNN: ...`)
- Hecho: <cambios principales, en 2-5 lineas>
- Archivos clave: <rutas>
- Verificacion: JUnit <n> ok (antes <m>); frontend <n> ok; build ok; scripts <ok | no aplica>; humo <ok | no aplica>
- Desvios del plan: <ninguno | que y por que>
- Hallazgos para gates siguientes: <datos que la proxima sesion debe saber>
- Pendientes: <tarea -> gate o paso que la toma>
- Verificacion manual sugerida al usuario: <pasos | no aplica>
```

## 8. Prompt de cada sesion (igual para todos los gates)

> Lee `CLAUDE.md`, `docs/PLAN_0_6_0.md` y `docs/BITACORA_0_6_0.md`. Trabaja el primer gate ABIERTO (HNN) siguiendo el protocolo de la seccion 5 del plan: inventario y plan antes de editar, suite completa, cierre en la bitacora con el formato de la seccion 7, commit y push a main en origin revisando archivos sensibles, y detente. Responde en espanol.

El usuario hace `/clear` entre gates y reemplaza `HNN` por el gate que espera. Si el gate nombrado no es el primer ABIERTO de la bitacora, la sesion se detiene y pregunta antes de leer o editar codigo.
