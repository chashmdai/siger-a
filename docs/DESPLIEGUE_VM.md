# Despliegue de SIGER en la VM institucional

Guia corta de la salida a produccion con SIGER 0.6.0 (`docs/PLAN_0_6_0.md`, decisiones D10-D16 y pasos V1-V6 y B de
su seccion 3): cerrar la validacion 0.4.0 sin actualizarla, instalar produccion nueva con el paquete 0.6.0, acreditarla
en automatico y eliminar la validacion con constancia. Cada comando es una linea de PowerShell y se ejecuta tal cual,
con `powershell.exe ... -File`, desde una consola de PowerShell. `FECHA` es el sufijo del ZIP (`SIGER-FECHA.zip`). El
detalle tecnico de cada script esta en `docs/INSTALACION_VM.md` y en su encabezado.

Valores de esta VM: validacion `D:\SIGER-VALIDACION` (0.4.0, esquema V10), produccion `D:\SIGER`, cuenta de servicio
`DDN-SIGER\svc-siger` (la misma en ambas, duena de DPAPI), motor `D:\Herramientas\mysql-8.4` (compartido: nunca se
borra), MySQL 13326 en loopback y HTTPS 18543 (los mismos en ambas: no conviven), respaldos de la validacion en
`D:\Respaldos-SIGER-VALIDACION` y constancias en `D:\SIGER-CONSTANCIAS`. Solo se trabaja en NEW-SIGER dentro de `D:`;
no se inspecciona ni modifica SMR ni otros volumenes (D15).

## Datos que debe entregar TI

1. **Cuenta de servicio** `DDN-SIGER\svc-siger`, la misma de la validacion: sin privilegios administrativos, con su contrasena, inicio de sesion local para `runas` y el derecho "Iniciar sesion como servicio" (ya concedido para la validacion).
2. **Carpetas** (las crea un administrador en la preparacion): `D:\SIGER` vacia y `D:\SIGER-CONSTANCIAS`, con control total para la cuenta de servicio, y ese mismo control sobre `D:\Respaldos-SIGER-VALIDACION`.
3. **MySQL 8.4 Community ZIP** ya extraido en `D:\Herramientas\mysql-8.4` (lo usa la validacion y lo usara produccion).
4. **`estructura-inicial.json` real de produccion** (decision abierta antes de V4): ambitos, la persona TI y los funcionarios con sus funciones (plantilla y reglas en `config-ejemplo\`). Toda sede regional lleva `"region"` (codigo del Formulario) o `Init-Server.ps1` la rechaza: el `D:\Entregas\estructura-inicial.json` de la validacion es de 0.4.0, no la trae y solo sirve de base. Debe contener el ambito que recibe las Comunicaciones del Formulario (el `scope` de `config\formulario-pull.json` de la validacion, por ejemplo `UPR-REQ`).
5. **Graph (opcional, D16)**: Tenant ID, Client ID y valor vigente del secreto de SIGER-Notificaciones (permiso `Mail.Send` limitado a `siger@defensorianinez.cl`), entregados de forma privada para escribirlos en la consola de `Import-GraphCredential.ps1`; el destinatario fijo de la prueba de envio (decision abierta); salida desde la VM a `login.microsoftonline.com:443` y `graph.microsoft.com:443` (no verificada en la VM).
6. **Acceso de funcionarios** (decision abierta antes de V4): solo RDP a la VM, o regla de firewall entrante al puerto 18543 limitada a sus direcciones (decide `-AccesoRed`) y, de preferencia, certificado HTTPS institucional (PFX) para el nombre de la VM.
7. **Motor antivirus** (decision abierta): produccion parte sin motor; mientras tanto los originales del Formulario se
   liberan por el modo de emergencia D17 (ver V4 y la seccion 4).

## Antecedente: la validacion

La validacion `D:\SIGER-VALIDACION` se instalo con 0.4.0 (paquete de secretos desde SMR, servicios con la cuenta de
servicio) y consume el Formulario en automatico con `"validacionPullAutorizada": true` en `config\instance.json`: recibe
solo entregas de prueba autorizadas del responsable del proyecto, porque el Formulario esta tras un acceso temporal con
contrasena hasta el **15/12/2026 00:00 (America/Santiago)**; despues queda abierto al publico. El consumidor de SMR ya
esta detenido desde entonces: SIGER es el unico consumidor de la cola.

Con 0.6.0 la validacion **no se actualiza** (D14): sigue en 0.4.0 hasta su cierre, y su cierre y su eliminacion se
ejecutan con los scripts de la carpeta del paquete 0.6.0. Su instalacion no se repite y `Export-SecretPackage.ps1` no se
usa (D15): produccion reutiliza la configuracion del Formulario de la validacion (misma cuenta, misma VM). Entre la pausa
de la validacion y produccion en automatico no se hacen entregas de prueba, y en produccion tampoco (D13 y D14).

## 3. Secuencia de la salida a produccion

| Paso | Que | Quien | Fecha |
|---|---|---|---|
| Preparacion | ZIP, carpetas y permisos; cifras del cierre con `-WhatIf` | administrador y svc-siger | antes de V1 |
| V1 | Pausa definitiva del consumidor de la validacion | TI | desde 04/12 |
| V2 | Cierre de la validacion con 9 recepciones declaradas | svc-siger | desde 04/12 |
| V3 | Retiro de los servicios de la validacion | administrador | desde 04/12 |
| V4 | Produccion con el paquete 0.6.0, en automatico | svc-siger y administrador | a mas tardar el 10/12 |
| V5 | Observacion (D13) y tareas de TI | TI | 10/12-14/12 |
| V6 | Eliminacion de la validacion con constancia | svc-siger | despues de V5 |
| B | Plan B, solo si la validacion sigue sin pausar ni cerrar | TI y svc-siger | 12/12 |

Cada paso se anota con sus cifras (recepciones, fechas, resultados), sin datos personales ni secretos.

### Preparacion

Como administrador, comprobar el SHA-256 del ZIP **antes de descomprimirlo**: debe ser igual al del registro de H06d en
`docs/BITACORA_0_6_0.md` del repositorio (la bitacora que trae el paquete se genero antes de ese registro y no lo
tiene). Si no coincide, no se descomprime ni se instala: se informa al responsable.

```powershell
certutil -hashfile 'D:\Entregas\SIGER-FECHA.zip' SHA256
```
```powershell
Expand-Archive -LiteralPath 'D:\Entregas\SIGER-FECHA.zip' -DestinationPath 'D:\Entregas'
```
```powershell
New-Item -ItemType Directory -Path 'D:\SIGER','D:\SIGER-CONSTANCIAS'
```
```powershell
icacls 'D:\SIGER' /grant 'DDN-SIGER\svc-siger:(OI)(CI)F'
```
```powershell
icacls 'D:\SIGER-CONSTANCIAS' /grant 'DDN-SIGER\svc-siger:(OI)(CI)F'
```
```powershell
icacls 'D:\Respaldos-SIGER-VALIDACION'
```

Si la ultima orden no muestra `DDN-SIGER\svc-siger:(OI)(CI)(F)`, concederlo (V6 borra esos respaldos como la cuenta de
servicio):

```powershell
icacls 'D:\Respaldos-SIGER-VALIDACION' /grant 'DDN-SIGER\svc-siger:(OI)(CI)F'
```

Como la cuenta de servicio (una consola abierta con `runas /user:DDN-SIGER\svc-siger powershell.exe`), ver las cifras
del cierre sin detener nada. Con la validacion aun sin pausar, el unico bloqueo esperado es `SIN_PAUSA`; cualquier otro
(por ejemplo `COM_EXTERNA_SIN_RECEPCION`) se informa al responsable antes de V1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Close-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -RecepcionesDeclaradas 9 -Motivo 'Fin de la validacion: salida a produccion' -WhatIf
```

### V1. Pausa definitiva de la validacion

TI, en la validacion: Auditoria y operacion > Integraciones permanentes > Pausar, con motivo. El responsable declara en
ese momento la cantidad de recepciones confirmadas (9). La pausa queda en la base y sobrevive a los reinicios. Desde
aqui no se ejecuta `Invoke-FormularioPull.ps1` en la validacion: el ciclo manual no consulta la pausa.

### V2. Cierre de la validacion

Como la cuenta de servicio, desde la carpeta del paquete. Muestra cifras y bloqueos, pide confirmacion, detiene SIGER,
vuelve a contar, detiene el motor y deja el marcador `config\piloto-cerrado.json` y su copia
`cierre-validacion-<fecha>.json` en `D:\SIGER-CONSTANCIAS`. No borra nada. Exige la pausa vigente, 9 recepciones
`CONFIRMADA`, ninguna `REGISTRADA` ni ACK pendiente y ninguna recepcion posterior a la pausa (D11):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Close-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -RecepcionesDeclaradas 9 -Motivo 'Fin de la validacion: salida a produccion'
```

Si se niega, no se fuerza: se lee el bloqueo y se informa al responsable. Una validacion cerrada no vuelve a iniciarse
(`Start.ps1` rechaza el marcador).

### V3. Retiro de los servicios de la validacion

Como administrador, desde la carpeta del paquete (el script no cambio desde 0.4.0). Retira solo el registro de `SIGER` y
`SIGERMySQL`; datos, configuracion y logs se conservan hasta V6:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Uninstall-Service.ps1' -Root 'D:\SIGER-VALIDACION'
```

### V4. Produccion

Como la cuenta de servicio, inicializar produccion con el paquete 0.6.0, sin `-MismoReleaseQue` (la validacion sigue en
0.4.0; esa garantia la reemplaza el ensayo de H06e) y con los mismos puertos (libres desde V2). La estructura real se
copia antes a `D:\Entregas\estructura-inicial-produccion.json`. Con acceso de funcionarios por red (firewall de TI):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Init-Server.ps1' -Mode Institucional -Proposito Produccion -EstructuraInicial 'D:\Entregas\estructura-inicial-produccion.json' -Root 'D:\SIGER' -Release 'D:\Entregas\SIGER-FECHA' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13326 -WebPort 18543 -AccesoRed
```

Solo por RDP (escucha en 127.0.0.1; hoy no hay script para pasar despues a acceso por red):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Init-Server.ps1' -Mode Institucional -Proposito Produccion -EstructuraInicial 'D:\Entregas\estructura-inicial-produccion.json' -Root 'D:\SIGER' -Release 'D:\Entregas\SIGER-FECHA' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13326 -WebPort 18543
```

El MySQL propio queda activo y SIGER sin iniciar. Anotar la fecha: el certificado HTTPS autofirmado vence a los 365 dias
(seccion 4). Entregar a cada persona su clave temporal de `D:\SIGER\data\bootstrap-usuarios.csv` por un canal seguro y
luego retirar el archivo. Los folios parten en `SIGER-000001`.

Copiar la configuracion del Formulario de la validacion (Bearer e identidad ACK protegidos con DPAPI de la misma cuenta
en la misma VM, claves de custodia, CA y `formulario-pull.json`) y **dejarla en Manual antes de iniciar**: la copia trae
el modo `AUTOMATICO` de la validacion, y sin la orden siguiente produccion consumiria desde su primer arranque, sin sonda:

La copia deja fuera las copias de seguridad `*.bak-atomic-*` que quedan en la validacion cada vez que un script
reescribio `formulario-pull.json`:

```powershell
Copy-Item -Path 'D:\SIGER-VALIDACION\config\formulario-*' -Exclude '*.bak-atomic-*' -Destination 'D:\SIGER\config'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-Integration.ps1' -Root 'D:\SIGER' -Formulario Manual
```

Clave del buscador de personas y folios, antes del primer inicio para que toda busqueda tenga huella desde el primer
dia (no se imprime ni se sobrescribe):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\New-AuditSearchKey.ps1' -Root 'D:\SIGER'
```

Antivirus: produccion parte **sin motor**; no se crea `config\antivirus.json`. Sin motor, los adjuntos subidos en SIGER
quedan en cuarentena y no se descargan. Para los originales del Formulario rige el modo de emergencia D17 (liberacion por
analisis en origen), que se activa aqui, antes del primer inicio, con su fundamento y **sin fecha de termino**: rige
hasta que se configure un motor antivirus admitido o se desactive de forma expresa; la revision periodica queda a cargo
del responsable, segun el acta institucional. La orden pide confirmacion y escribe `config\liberacion-origen.json`;
SIGER lo valida y lo audita al arrancar (Auditoria y operacion), y desde entonces cada original del Formulario se libera
al registrarse, despues de verificar su SHA-256 firmado, con la auditoria "analisis en origen del Formulario" (la ficha
lo muestra como "Disponible · analisis en origen"). Mientras rige, TI ve en Administracion el aviso permanente "Rige el
modo de emergencia D17". Sin este paso, o si el archivo no es valido, los originales quedan en cuarentena y TI ve el
aviso. Desactivar y agregar un motor admitido: seccion 4.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-OriginRelease.ps1' -Root 'D:\SIGER' -Motivo 'Decision del responsable del 2026-11-21 (D17), con correo y constancia institucional'
```

Se anota la fecha de activacion (la muestra el aviso) para la revision periodica del responsable.

Detener el motor propio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Stop.ps1' -Root 'D:\SIGER' -AlsoMySql
```

Como administrador, en una consola interactiva (RDP): registra `SIGER` y `SIGERMySQL` con la cuenta de servicio y
recuperacion automatica, sin iniciarlos. Windows PowerShell 5.1 abre el **cuadro de credenciales de Windows** con la
cuenta ya escrita y pide solo la contrasena (no la lee de la consola). El control de los servicios (iniciar, detener y
el control de la prueba de caida) queda solo para SYSTEM, Administradores y la cuenta de servicio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Install-Service.ps1' -Root 'D:\SIGER' -ServiceCredential 'DDN-SIGER\svc-siger' -ConcederInicioComoServicio
```

Como la cuenta de servicio: iniciar, ver el estado y probar arranque, parada y recuperacion en una ventana sin usuarios
(el Formulario sigue en Manual):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Start.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Estado.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Test-Service.ps1' -Root 'D:\SIGER' -VentanaAutorizada -IncluirMySql
```

`Estado.ps1` debe mostrar `AppRunning` verdadero, `Health` UP, version 0.6.0 y el Formulario en `DESACTIVADO`: en Manual
el servicio no tiene consumidor (`MANUAL` solo lo informa el ciclo de `Invoke-FormularioPull.ps1`). Si informa que el
PID no coincide (corregido en H04a y, para la cuenta local `.\svc-siger`, en H06c; se acredita en H06e), se anota y se
informa al responsable; la sonda HTTPS de `Start.ps1` sigue siendo la prueba de salud. `Test-Service.ps1` simula la
caida de Java con el control de servicio 200 (la cuenta de servicio no puede terminar el proceso del servicio) y
comprueba que Windows lo reinicia; deja `logs\prueba-servicio-<fecha>.json`.

Sonda del Formulario (configuracion, DNS, TLS con la CA y Bearer, sin reclamar ni confirmar nada) y activacion del
consumidor permanente. `Set-Integration.ps1` exige produccion, una sonda acreditada de menos de 24 horas con el JAR
activo y la declaracion de que SMR no consume; si encuentra el servicio `SMRRecepcion` de este equipo activo o con
inicio habilitado se niega, y no se toca SMR: se consulta al responsable.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Invoke-FormularioPull.ps1' -Root 'D:\SIGER' -SoloComprobar
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-Integration.ps1' -Root 'D:\SIGER' -Formulario Automatico -SmrDetenidoConfirmado
```

El modo se aplica al reiniciar:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Stop.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Start.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Estado.ps1' -Root 'D:\SIGER'
```

`Estado.ps1` debe mostrar el Formulario en `ESPERANDO_ENTREGAS`. Toda entrega queda registrada, custodiada y confirmada
por ACK; ninguna se descarta.

Graph (opcional, D16: produccion puede partir con el correo desactivado). Como la cuenta de servicio, instalar la
credencial (pide Tenant ID, Client ID y el secreto por consola; deja el correo DESACTIVADO; no copia nada de la
validacion, que no tiene Graph), pedir solo un token y enviar un correo de texto fijo al destinatario fijo (decision
abierta; la orden pide confirmacion antes de enviar):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Import-GraphCredential.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Test-GraphMail.ps1' -Root 'D:\SIGER' -Modo Token
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Test-GraphMail.ps1' -Root 'D:\SIGER' -Modo Envio -Destinatario 'destinatario.fijo@defensorianinez.cl'
```

Con el Envio ACEPTADO (`run\graph-prueba.json`, menos de 24 horas, mismo JAR), activar el correo y reiniciar con
`Stop.ps1` y `Start.ps1` como arriba:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-Integration.ps1' -Root 'D:\SIGER' -Correo Graph
```

Solo los avisos nuevos generan correo, desde `siger@defensorianinez.cl` y para los dominios permitidos.

### V5. Observacion de produccion y tareas de TI

Acreditacion (D13), a mas tardar el 10/12: TI, en Auditoria y operacion de produccion, verifica el Formulario en
"Esperando entregas", ciclos corriendo y ultimo exito reciente. Un ciclo en pausa tambien registra exito: lo que cuenta
es el estado. No hay entregas de prueba en produccion. Se anota la fecha y el estado.

Tareas de TI tras el primer arranque:

1. Ingresar con la clave temporal y cambiarla.
2. Calendario habil (`#calendario`): importar los feriados nacionales y regionales de 2026 y 2027 con fundamento. Sin
   ellos, los dias habiles se calculan sin esos feriados y los reportes avisan los anios faltantes.
3. Estructura: revisar la region de cada sede (viene del archivo) y activar las sedes PROYECTADA cuando abran.
4. Auditoria y operacion: comprobar el aviso permanente "Rige el modo de emergencia D17" con su fecha de activacion.
   Los adjuntos subidos en SIGER siguen en cuarentena (seccion 4).

### V6. Eliminacion de la validacion

Despues de V5, como la cuenta de servicio y desde la carpeta del paquete. Exige el marcador de V2 y su copia en las
constancias, servicios retirados (V3), motor detenido y produccion inicializada con la misma cuenta, el mismo origen e
identidad ACK del Formulario y en AUTOMATICO: con el plan B vigente no se elimina. Primero solo el inventario y los
bloqueos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Remove-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -Produccion 'D:\SIGER' -Respaldos 'D:\Respaldos-SIGER-VALIDACION' -RespaldosIncompletos 'D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109' -Ensayos NINGUNO -Motivo 'Eliminacion de la validacion tras acreditar produccion' -WhatIf
```

Luego la eliminacion (pide la confirmacion de PowerShell y que se escriba la ruta de la raiz; escribe la constancia
`eliminacion-validacion-<fecha>.json` en `D:\SIGER-CONSTANCIAS` antes de borrar y la completa por elemento):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Remove-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -Produccion 'D:\SIGER' -Respaldos 'D:\Respaldos-SIGER-VALIDACION' -RespaldosIncompletos 'D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109' -Ensayos NINGUNO -Motivo 'Eliminacion de la validacion tras acreditar produccion'
```

`-WhatIf` no comprueba el permiso para borrar las carpetas mismas. Si la eliminacion falla por permisos, la constancia
queda con el elemento FALLIDO y el resto NO_INTENTADO: un administrador corrige el permiso y se reintenta con las rutas
que la constancia no dejo ELIMINADO (sin las ya eliminadas; el reintento exige incluir todo lo pendiente y referencia la
constancia anterior). Si el fallo ocurre al borrar los ultimos archivos de `config\` (los que el reintento necesita
leer), el reintento no puede leer la instancia: se termina a mano con la constancia INCOMPLETA como registro y se
informa al responsable. Los respaldos y snapshots de TI y la copia del Formulario quedan fuera de alcance, y la
eliminacion es logica, no forense (D12).

No restaurar un respaldo de la validacion sobre el motor de produccion (`Restore-Check.ps1 -Root 'D:\SIGER'`): esos
datos quedarian en el MySQL de produccion, fuera del alcance de la eliminacion.

### B. Plan B (12/12)

Solo si al 12/12 la validacion todavia no se ha pausado ni cerrado (V1-V2): TI pausa su consumidor en Auditoria y
operacion (como en V1) y, como la cuenta de servicio, con los scripts de la propia instancia 0.4.0 y en este orden:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Set-Integration.ps1' -Root 'D:\SIGER-VALIDACION' -Formulario Manual
```

Despues retirar la linea `"validacionPullAutorizada": true` de `D:\SIGER-VALIDACION\config\instance.json` (con el
JSON valido) y reiniciar; en el orden inverso `Start.ps1` rechazaria el modo AUTOMATICO sin autorizacion:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Stop.ps1' -Root 'D:\SIGER-VALIDACION'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Start.ps1' -Root 'D:\SIGER-VALIDACION'
```

Si la validacion ya esta cerrada y produccion aun no consume, no hay nada que hacer en la validacion: las entregas
esperan en la cola del Formulario hasta completar V4.

## 4. Operacion de produccion

### HTTPS

- **Autofirmado** (por defecto): `Init-Server.ps1` crea un certificado `CN=localhost` (SAN localhost y 127.0.0.1) por
  365 dias. Por RDP en `https://localhost:18543` el navegador advierte que no es de confianza; por red, en
  `https://<nombre-de-la-VM>:18543`, advierte ademas que el nombre no coincide. Es aceptable solo de forma transitoria.
- **PFX institucional** para el nombre de la VM (con clave privada, vigente y con mas de 30 dias por delante). Como la
  cuenta de servicio; pide la clave del PFX, conserva el certificado anterior en `config\https-anterior-<fecha>.p12` y
  rige al reiniciar (`Stop.ps1` y `Start.ps1`):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Import-Integrations.ps1' -Root 'D:\SIGER' -HttpsPfx 'D:\Entregas\siger-https.pfx'
```

- **Solo RDP**: produccion inicializada sin `-AccesoRed` escucha solo en 127.0.0.1.
- **Renovacion**: con un certificado vencido la sonda local lo rechaza y `Start.ps1` no acredita la salud. Antes del
  vencimiento (anotado en V4; el importador exige al menos 30 dias de vigencia) se importa un PFX nuevo con la orden de
  arriba. Hoy no hay script para regenerar el autofirmado: sin PFX institucional, su renovacion no tiene procedimiento.

### Motor antivirus

Produccion parte sin motor: Microsoft Defender esta inactivo en la VM y el antivirus institucional (Kaspersky Endpoint
Security) no es un motor admitido por SIGER. Mientras tanto rige el modo de emergencia D17 para los originales del
Formulario (V4) y los adjuntos subidos en SIGER siguen en cuarentena.

El modo D17 **no tiene fecha de termino**: rige hasta que se configure un motor admitido o se desactive con la orden de
abajo. La revision periodica queda a cargo del responsable, segun el acta institucional; TI ve mientras tanto el aviso
permanente en Administracion. Repetir la orden de V4 reemplaza el registro (pide confirmarlo; por ejemplo, con un nuevo
fundamento) y rige desde el reinicio con `Stop.ps1` y `Start.ps1`. Al arrancar con el modo vigente (tambien al volver a
activarlo tras una desactivacion o un archivo no valido), SIGER libera los originales del Formulario que quedaron en
cuarentena mientras no regia, con la misma verificacion (SHA-256 completo de nuevo); nunca un adjunto subido en SIGER ni
un original con una deteccion previa.

**Desactivar el modo D17** (al configurar un motor admitido o al terminar la emergencia), como la cuenta de servicio.
Renombra el archivo a `config\liberacion-origen.retirado-<UTC>.json` (no borra nada) y rige desde el reinicio; SIGER lo
audita al arrancar:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-OriginRelease.ps1' -Root 'D:\SIGER' -Desactivar
```

Para agregar despues un motor admitido, ClamAV (con autorizacion de TI para instalarlo en `D:\Herramientas\clamav` y
salida de red para actualizar sus firmas), como la cuenta de servicio:

0. Desactivar el modo D17 con la orden de arriba. Con un motor configurado SIGER no aplica el modo (aviso
   `LIBERACION_ORIGEN_CON_MOTOR`) y rige el analisis local; `Set-OriginRelease.ps1` no lo activa si existe
   `antivirus.json`.
1. Solo con ClamAV ya instalado y sus firmas descargadas en `D:\Herramientas\clamav` (con un `executable` que no existe
   SIGER no arranca, por diseno), crear `D:\SIGER\config\antivirus.json` con
   `{"engine":"CLAMAV","executable":"D:\\Herramientas\\clamav\\clamscan.exe","database":"D:\\Herramientas\\clamav\\db"}`.
2. Mantener las firmas de la carpeta `database` actualizadas (freshclam): SIGER rechaza firmas de mas de 72 horas.
3. Reiniciar (`Stop.ps1` y `Start.ps1`). TI comprueba en Auditoria y operacion que **Analizar** da LIMPIO en un original
   y solo entonces libera (detalle en "Liberacion segura de originales" de `docs/INSTALACION_VM.md`).

### Volver a Manual

Pausar el consumidor (TI, Auditoria y operacion), esperar a que termine su ciclo y comprobar cero ACK pendientes; luego
`Set-Integration.ps1 -Root 'D:\SIGER' -Formulario Manual` y reiniciar. Las entregas esperan en la cola del Formulario.
SMR no se reactiva desde esta guia: se consulta al responsable. Nunca hay dos consumidores activos, y no se borran COM,
originales, recepciones ni leases para revertir.
