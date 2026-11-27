# Despliegue de SIGER en la VM institucional

Guia corta de la salida a produccion con SIGER 0.6.0 (`docs/PLAN_0_6_0.md`, decisiones D10-D16 y pasos V1-V6 y B de
su seccion 3): cerrar la validacion 0.4.0 sin actualizarla, instalar produccion nueva, acreditarla en automatico y
eliminar la validacion con constancia. P0 y V0-V3 se ejecutan con el paquete 0.6.0. V4 instala produccion con el paquete
0.7.0 (`docs/PLAN_0_7_0.md`, cobertura territorial de las sedes y region de origen) solo si al corte del 07/12 el gate
T06b esta CERRADO, existe la etiqueta `v0.7.0` y el responsable lo aprueba de forma expresa; si no, V4 usa el paquete
0.6.0. Cada comando es una linea de PowerShell y se ejecuta tal cual,
con `powershell.exe ... -File`, desde una consola de PowerShell. `FECHA` es el sufijo del ZIP (`SIGER-FECHA.zip`). El
detalle tecnico de cada script esta en `docs/INSTALACION_VM.md` y en su encabezado. La actualizacion de produccion a 0.8.0 (paso U) esta en la seccion 5.

Valores de esta VM: validacion `D:\SIGER-VALIDACION` (0.4.0, esquema V10), produccion `D:\SIGER`, cuenta de servicio
`DDN-SIGER\svc-siger` (la misma en ambas, duena de DPAPI), motor `D:\Herramientas\mysql-8.4` (compartido: nunca se
borra), MySQL 13326 en loopback y HTTPS 18543 (los mismos en ambas: no conviven), respaldos de la validacion en
`D:\Respaldos-SIGER-VALIDACION` y constancias en `D:\SIGER-CONSTANCIAS`. Solo se trabaja en NEW-SIGER dentro de `D:`;
no se inspecciona ni modifica SMR ni otros volumenes (D15).

## Datos que debe entregar TI

1. **Cuenta de servicio** `DDN-SIGER\svc-siger`, la misma de la validacion: sin privilegios administrativos, con su contrasena, inicio de sesion local para `runas` y el derecho "Iniciar sesion como servicio" (ya concedido para la validacion).
2. **Carpetas** (las crea un administrador en la preparacion): `D:\SIGER` vacia y `D:\SIGER-CONSTANCIAS`, con control total para la cuenta de servicio, y ese mismo control sobre `D:\Respaldos-SIGER-VALIDACION`.
3. **MySQL 8.4 Community ZIP** ya extraido en `D:\Herramientas\mysql-8.4` (lo usa la validacion y lo usara produccion).
4. **`estructura-inicial.json` real de produccion** (decision abierta antes de V4): ambitos, la persona TI y los funcionarios con sus funciones (plantilla y reglas en `config-ejemplo\`). Toda sede regional lleva `"region"` (codigo del Formulario) o `Init-Server.ps1` la rechaza: el `D:\Entregas\estructura-inicial.json` de la validacion es de 0.4.0, no la trae y solo sirve de base. Debe contener el ambito que recibe las Comunicaciones del Formulario (el `scope` de `config\formulario-pull.json` de la validacion, por ejemplo `UPR-REQ`). Con el paquete 0.7.0 admite ademas `"cobertura"` en cada sede regional (regiones que la sede atiende ademas de su region fisica, por ejemplo `["03"]`) y `"coberturaNivelCentral"` en la raiz (regiones que atiende el nivel central, por ejemplo `["13"]`), ambas opcionales (reglas en `config-ejemplo\LEEME.txt`). Si V4 vuelve a 0.6.0, esas dos claves se retiran del archivo: 0.6.0 las ignora sin aviso.
5. **Graph (opcional, D16)**: Tenant ID, Client ID y valor vigente del secreto de SIGER-Notificaciones (permiso `Mail.Send` limitado a `siger@defensorianinez.cl`), entregados de forma privada para escribirlos en la consola de `Import-GraphCredential.ps1`; el destinatario fijo de la prueba de envio (decision abierta); salida desde la VM a `login.microsoftonline.com:443` y `graph.microsoft.com:443` (no verificada en la VM).
6. **Acceso de funcionarios** (decidido el 2026-11-22: produccion **con acceso por red**, `-AccesoRed`, como ya se usa la validacion): regla de firewall entrante al puerto 18543 limitada a sus direcciones (en la VM ya existe "SIGER-A Validacion HTTPS - Red DDN": TCP 18543, perfil Dominio, origen 192.168.100.0/24) y, cuando exista, certificado HTTPS institucional (PFX) para el nombre de la VM; se parte con el autofirmado de `Init-Server.ps1`.
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
| P0 | Exportacion portable de la configuracion de la validacion y su verificacion (H06r) | svc-siger (sesion ya abierta) y administrador | cuanto antes, sin esperar el paquete |
| Preparacion | ZIP, carpetas y permisos | administrador | antes de V0 |
| V0 | Rotacion de la contrasena de svc-siger: importacion de la configuracion y contrasena de los servicios (H06s); cifras del cierre con `-WhatIf` | administrador y svc-siger | despues de la Preparacion, antes de V1 |
| V1 | Pausa definitiva del consumidor de la validacion | TI | desde 04/12 |
| V2 | Cierre de la validacion con 9 recepciones declaradas | svc-siger | desde 04/12 |
| V3 | Retiro de los servicios de la validacion | administrador | desde 04/12 |
| V4 | Produccion con el paquete 0.7.0 (segun el corte del 07/12) o 0.6.0, en automatico | svc-siger y administrador | con 0.7.0 entre el 08 y el 10/12; con 0.6.0 a mas tardar el 10/12 |
| V5 | Observacion (D13) y tareas de TI | TI | 10/12-14/12 |
| V6 | Eliminacion de la validacion con constancia | svc-siger | despues de V5 |
| B | Plan B, solo si la validacion sigue sin pausar ni cerrar | TI y svc-siger | 12/12 |

Cada paso se anota con sus cifras (recepciones, fechas, resultados), sin datos personales ni secretos.

### P0. Exportacion de la configuracion de la validacion

La contrasena de `DDN-SIGER\svc-siger` es desconocida y V3 y V4 la exigen. Restablecerla (paso V0, gate H06s) deja sin
descifrar todo lo protegido con la DPAPI de la cuenta: `secrets.clixml` (claves de MySQL, HTTPS y truststore), el
Bearer y la identidad ACK del Formulario. P0 deja antes un paquete portable de toda la configuracion, cifrado con una
frase de paso y verificado por otra cuenta. **No cambia nada en la validacion**: no la detiene ni toca sus servicios,
y la PowerShell ya abierta como `svc-siger` se mantiene abierta hasta terminar el punto 4.

Se usan seis archivos del commit `gate H06r2` del repositorio (sirven para la validacion 0.4.0; no se espera el paquete
0.6.0): `Export-InstanceConfig.ps1`, `Test-InstanceConfig.ps1`, `ConfigPackage.ps1`, `AckKeyCheck.java`, `Common.ps1` y
`Hash.ps1`. Su SHA-256 esta en el registro de H06r2 de `docs/BITACORA_0_6_0.md` del repositorio. Los de H06r (carpeta
`D:\Rotacion-SIGER\scripts` de la VM, primer intento del 2026-11-22) **ya no se usan y no se borran**: su exportacion se
niega por `config\https-red-candidato.p12` (`P12_SIN_CLAVE_CONOCIDA`). En la VM no se borra, mueve, renombra ni cambia
permisos de ningun archivo, ni se parchan scripts a mano.

1. Como administrador, preparar la carpeta de los scripts (queda fuera de la validacion, de sus respaldos y de
   produccion; V6 no la toca).

   En la VM, donde `D:\Rotacion-SIGER` ya existe con el permiso de `svc-siger` (primer intento), solo se crea la
   carpeta nueva, que hereda ese permiso, y se comprueba sin cambiarlo (debe mostrar `DDN-SIGER\svc-siger:(I)(OI)(CI)(F)`):

```powershell
New-Item -ItemType Directory -Path 'D:\Rotacion-SIGER\scripts-H06r2'
```
```powershell
icacls 'D:\Rotacion-SIGER\scripts-H06r2'
```

   En un equipo donde `D:\Rotacion-SIGER` no existe, se crea con control total para la cuenta de servicio:

```powershell
New-Item -ItemType Directory -Path 'D:\Rotacion-SIGER','D:\Rotacion-SIGER\scripts-H06r2'
```
```powershell
icacls 'D:\Rotacion-SIGER' /grant 'DDN-SIGER\svc-siger:(OI)(CI)F'
```

2. Copiar los seis archivos del commit `gate H06r2` a `D:\Rotacion-SIGER\scripts-H06r2` y comprobar cada SHA-256 contra
   el registro de H06r2, uno por uno (si alguno no coincide, no se ejecuta nada y se informa al responsable):

```powershell
certutil -hashfile 'D:\Rotacion-SIGER\scripts-H06r2\Export-InstanceConfig.ps1' SHA256
```

   (lo mismo con `Test-InstanceConfig.ps1`, `ConfigPackage.ps1`, `AckKeyCheck.java`, `Common.ps1` y `Hash.ps1`).

3. **En la PowerShell ya abierta como `svc-siger`** (no se usa `runas` ni el cuadro de credenciales: esa sesion es la
   unica que hoy descifra los secretos). Comprobar la cuenta con `whoami` (debe decir `ddn-siger\svc-siger`) y ver el
   inventario sin escribir nada:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Rotacion-SIGER\scripts-H06r2\Export-InstanceConfig.ps1' -Root 'D:\SIGER-VALIDACION' -Destination 'D:\Rotacion-SIGER\validacion-config.sigerpkg' -WhatIf
```

   Criterio: la linea `Inventario: 26 archivos (20 ARCHIVO, 3 CLIXML, 3 P12), ... bytes.` (el 22/11 fueron 39514 bytes:
   solo referencia), los mismos 26 nombres del inventario del 22/11 ("Datos de la VM: cuenta de servicio" de la
   bitacora) y, para el almacen que ninguna configuracion usa, la linea

   `  P12     config\https-red-candidato.p12 (3538 bytes, sin clave conocida: se conserva tal cual, sin abrirlo)`

   (los conocidos dicen `(3478 bytes, clave Tls)` para `https.p12` y `(1174 bytes, clave Trust)` para
   `mysql-trust.p12`; los `https.p12.bak-*` son `ARCHIVO`). Sin "No se puede exportar". **Si los conteos o los nombres
   cambiaron desde el 22/11, no se exporta y se informa al responsable.** Luego exportar: pide la frase de paso dos
   veces, escrita a mano (minimo 20 caracteres; no se pega ni se guarda junto al archivo). Cada secreto se descifra solo
   en memoria; `https-red-candidato.p12` viaja tal cual, sin abrirlo ni necesitar su contrasena; el archivo es nuevo
   (nunca sobrescribe), queda solo para la cuenta, SYSTEM y Administradores, y se verifica leyendolo de nuevo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Rotacion-SIGER\scripts-H06r2\Export-InstanceConfig.ps1' -Root 'D:\SIGER-VALIDACION' -Destination 'D:\Rotacion-SIGER\validacion-config.sigerpkg'
```

   Debe decir `Contenido: 26 archivos con SHA-256, ... secretos con su huella, 2 almacen(es) .p12 que abren con su clave
   y 1 sin clave conocida conservado(s) tal cual (config\https-red-candidato.p12).` Anotar el SHA-256 que informa.

4. Como administrador, en una consola elevada (otra cuenta: el script rechaza a `svc-siger`), verificar con la frase de
   paso. No escribe nada ni cambia permisos; comprueba integridad, huellas y SHA-256, cada archivo contra el disco, los
   `.p12` de nombre conocido con su clave del paquete (certificados y vencimiento), que la clave privada ACK corresponde
   a su publica y a su keyId, y un `SELECT 1` con cada contrasena de MySQL contra el motor de la validacion (que sigue
   activo):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Rotacion-SIGER\scripts-H06r2\Test-InstanceConfig.ps1' -Package 'D:\Rotacion-SIGER\validacion-config.sigerpkg' -Root 'D:\SIGER-VALIDACION'
```

   Debe terminar en `Resultado: CORRECTO.`, con el mismo SHA-256 del punto 3, `Contra el disco: 26 iguales, 0 distintos,
   0 faltan, 0 no legibles, 0 en disco y no en el paquete.`, 4 de 4 cuentas de MySQL autenticadas, la identidad ACK
   `COINCIDE`, `https.p12` y `mysql-trust.p12` `IGUALES` y, para el almacen sin clave conocida, estas dos lineas (no son
   una observacion; con ellas el resultado sigue siendo CORRECTO):

   `Almacen config\https-red-candidato.p12: sin clave conocida, se conserva tal cual (tamano y SHA-256 verificados; no se abre ni se comparan certificados).`

   `Almacen del disco config\https-red-candidato.p12: SIN_CLAVE_CONOCIDA (comparado solo por SHA-256: IGUAL).`

   Si informa archivos `NO_LEGIBLE` (la ACL de `config\` no deja
   leer al administrador), no se cambian permisos (ni `takeown` ni `icacls` sobre `config\`): se anota lo que no pudo
   comparar y se informa al responsable. Con cualquier otra observacion, no se sigue: se informa al responsable.

5. Custodia: copiar `validacion-config.sigerpkg` fuera de la VM por un medio aprobado y guardar la frase de paso por
   separado (otro lugar y otro custodio). Conservar la copia de la VM en `D:\Rotacion-SIGER`. Solo con el punto 4
   CORRECTO y la copia externa hecha se puede cerrar la PowerShell de `svc-siger`.

Reversion: P0 no cambia la validacion. Si la verificacion falla, el paquete no se usa: se exporta otro con un nombre
nuevo (el destino nunca se sobrescribe) mientras la sesion siga abierta. Si la sesion se pierde antes de un paquete
verificado, se detiene y se reevalua con el responsable. El paquete contiene los secretos de la instancia: se borra de
forma controlada (tambien su copia externa) cuando el responsable lo indique, despues de V5.

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

Las cifras del cierre (`Close-Validation.ps1 -WhatIf`) se ven al final de V0, como la cuenta de servicio: hasta V0 su
contrasena es desconocida y no se puede abrir una sesion nueva con ella.

### V0. Rotacion de la contrasena de la cuenta de servicio

V3 y V4 exigen la contrasena de `DDN-SIGER\svc-siger`, que es desconocida. V0 la restablece y recupera lo que su DPAPI
protegia con el paquete de P0 (`D:\Rotacion-SIGER\validacion-config.sigerpkg`, SHA-256
`b33b167f4e1b988dce0a050eae5c5ef26413fc22ae058a007cb5dad466486ecd`) y su frase de paso. Se usan los scripts de la
carpeta del paquete 0.6.0 sobre la validacion 0.4.0 (los de la instancia 0.4.0 fallan como la cuenta de servicio). No se
reinstalan servicios ni se cambia nada mas de la validacion; V1-V6 no cambian. **El punto de no retorno es el punto 4**
(restablecer la contrasena): antes de el, todo se revierte arrancando de nuevo la validacion.

1. Como administrador, revisar en solo lectura que nada mas use la cuenta (consultas filtradas por la cuenta; no se leen
   archivos de C: ni configuraciones de otros sistemas, D15). Deben aparecer solo `SIGER` y `SIGERMySQL`, ninguna tarea
   programada y, como procesos, la PowerShell abierta de P0 y los de los servicios (`SigerService`, `powershell`, `java`
   y `mysqld`). Si aparece otra cosa, no se sigue y se informa al responsable:

```powershell
Get-CimInstance Win32_Service | Where-Object {$_.StartName -like '*svc-siger'} | Select-Object Name,State,StartName
```
```powershell
Get-ScheduledTask | Where-Object {$_.Principal.UserId -like '*svc-siger'} | Select-Object TaskPath,TaskName,State
```
```powershell
Get-Process -IncludeUserName | Where-Object {$_.UserName -eq 'DDN-SIGER\svc-siger'} | Select-Object Id,ProcessName
```

2. Como administrador, con la validacion todavia activa, verificar el paquete contra el disco justo antes de restablecer
   (pide la frase de paso; no escribe nada):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Test-InstanceConfig.ps1' -Package 'D:\Rotacion-SIGER\validacion-config.sigerpkg' -Root 'D:\SIGER-VALIDACION'
```

   Debe informar el SHA-256 `b33b167f4e1b988dce0a050eae5c5ef26413fc22ae058a007cb5dad466486ecd` y terminar como en el
   punto 4 de P0: `Resultado: CORRECTO.`, 26 iguales, 4 de 4 cuentas de MySQL y ACK `COINCIDE`. Si el SHA-256 es otro o
   hay cualquier observacion, no se sigue y se informa al responsable.

3. Detener la validacion. En la PowerShell de `svc-siger` abierta desde P0:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Stop.ps1' -Root 'D:\SIGER-VALIDACION' -AlsoMySql
```

   Si esa sesion ya no existe, la misma orden en una consola elevada (administrador): con servicios registrados solo
   detiene `SIGER` y despues `SIGERMySQL`, sin descifrar nada. En ambos casos, comprobar que los dos quedaron `Stopped`:

```powershell
Get-Service -Name 'SIGER','SIGERMySQL'
```

4. Como administrador, restablecer la contrasena (la nueva la define TI o el responsable, se escribe dos veces sin
   mostrarse y se custodia; nunca se anota en la bitacora) y dejar "Contrasena requerida" en Si. **Desde aqui no hay
   vuelta a la contrasena anterior**:

```powershell
net user svc-siger *
```
```powershell
net user svc-siger /passwordreq:yes
```
```powershell
net user svc-siger
```

   La ultima debe mostrar "Contrasena requerida Si" y la fecha de hoy en "Ultimo cambio de contrasena".

5. Cerrar la PowerShell de `svc-siger` de P0 (`exit`): ya no se necesita (tras el restablecimiento tampoco descifra
   los secretos anteriores; ensayo de H06s). Abrir una
   sesion nueva con la contrasena nueva (`runas /user:DDN-SIGER\svc-siger powershell.exe`), comprobar con `whoami` que
   dice `ddn-siger\svc-siger` e importar primero sin escribir (pide la frase de paso):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Import-InstanceConfig.ps1' -Root 'D:\SIGER-VALIDACION' -Package 'D:\Rotacion-SIGER\validacion-config.sigerpkg' -WhatIf
```

   Criterio: el SHA-256 informado es `b33b167f4e1b988dce0a050eae5c5ef26413fc22ae058a007cb5dad466486ecd`, `Plan: 26
   archivos del paquete (3 REEMPLAZAR, 23 SIN_CAMBIO); 0 archivo(s) del disco que no estan en el paquete...` (solo los
   tres clixml se vuelven a proteger; `https-red-candidato.p12` queda `SIN_CAMBIO`, comparado solo por SHA-256). La
   primera vez no aparece la linea "Esta sesion ya descifra los clixml del disco..."; al repetir la importacion si, y es
   solo informativa. Si el plan es otro, no se importa y se informa al responsable. Luego la importacion (pide la frase
   y la confirmacion de PowerShell; los anteriores quedan en `config\rotacion-<UTC>\anterior\` y nada se borra):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Import-InstanceConfig.ps1' -Root 'D:\SIGER-VALIDACION' -Package 'D:\Rotacion-SIGER\validacion-config.sigerpkg'
```

   Debe informar de nuevo ese SHA-256 y decir `Importacion COMPLETA: 3 archivos aplicados, 23 sin cambio`, `Almacen
   config\https-red-candidato.p12: SIN_CLAVE_CONOCIDA (...)`, `Identidad ACK: privada y publica COINCIDE.` y
   `Resultado: CORRECTO.` Si falla a mitad, se repite la misma orden: vuelve a proteger los clixml y restaura lo que
   falte.

6. Como administrador, en una consola elevada interactiva (Windows PowerShell 5.1 pide la contrasena nueva en el cuadro
   de credenciales), actualizar la contrasena guardada de los servicios, sin reinstalarlos ni iniciarlos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Set-ServicePassword.ps1' -Root 'D:\SIGER-VALIDACION' -ServiceCredential 'DDN-SIGER\svc-siger'
```

   Antes de cambiar nada comprueba, como la cuenta y en un inicio de sesion nuevo, que la contrasena es valida y que
   descifra los 3 clixml: debe decir "descifra 3 de 3". **No pide confirmacion**: con las comprobaciones correctas
   actualiza `SIGERMySQL` y `SIGER` de inmediato. Si se niega por la contrasena, se repite con la correcta. Si se niega
   con `DPAPI` ("descifra 0 de 3"), falta la importacion del punto 5: se hace y se repite esta orden. Si informa que un
   servicio quedo actualizado y el otro no, se repite la misma orden (vuelve a comprobar todo y fija la misma contrasena
   en ambos) y no se inician los servicios hasta que termine bien.

7. Cerrar la sesion de `svc-siger` del punto 5 y abrir **otra sesion nueva** (`runas`), que prueba que la cuenta
   descifra con un inicio de sesion nuevo. En ella, arrancar y comprobar la validacion (UP, version 0.4.0, Formulario
   `ESPERANDO_ENTREGAS` en Auditoria y operacion):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Start.ps1' -Root 'D:\SIGER-VALIDACION'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Estado.ps1' -Root 'D:\SIGER-VALIDACION'
```

   Un respaldo nuevo, que acredita las contrasenas de MySQL y contiene las 9 recepciones (V6 lo elimina con los demas):
   se detiene solo SIGER (el motor sigue activo), se respalda y se vuelve a arrancar:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Stop.ps1' -Root 'D:\SIGER-VALIDACION'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Backup.ps1' -Root 'D:\SIGER-VALIDACION' -Destination 'D:\Respaldos-SIGER-VALIDACION\posterior-v0'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Start.ps1' -Root 'D:\SIGER-VALIDACION'
```

8. En esa misma sesion, las cifras del cierre sin detener nada. Con la validacion aun sin pausar, el unico bloqueo
   esperado es `SIN_PAUSA`; cualquier otro (por ejemplo `COM_EXTERNA_SIN_RECEPCION`) se informa al responsable antes de
   V1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Close-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -RecepcionesDeclaradas 9 -Motivo 'Fin de la validacion: salida a produccion' -WhatIf
```

Reversion: hasta el punto 3, nada cambio. Entre el 3 y el 4, se vuelve a arrancar (`Start.ps1` en la sesion de P0 o
`Start-Service -Name 'SIGER'` como administrador). Desde el punto 4 la contrasena anterior no vuelve: la recuperacion es
repetir la importacion (punto 5) con el paquete y su frase (la copia de la VM o, si faltara, la copia externa de P0) y
repetir `Set-ServicePassword.ps1`; lo anterior queda en `config\rotacion-<UTC>\anterior\`. Si asi no se logra, se
detiene y se reevalua con el responsable. El paquete y su frase se conservan hasta despues de V5 (ver P0).

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

Paquete de V4 (decision del corte del 07/12, `docs/PLAN_0_7_0.md` seccion 3): 0.7.0 solo con T06b CERRADO, la
etiqueta `v0.7.0` y la aprobacion expresa del responsable; si no, 0.6.0, el mismo de P0 y V0-V3. Con 0.7.0, antes de
seguir, un administrador repite con el ZIP 0.7.0 los primeros pasos de la Preparacion: copiarlo a `D:\Entregas`,
comprobar su SHA-256 con `certutil` **antes de descomprimirlo** (debe ser igual al que registra T06a en
`docs/BITACORA_0_7_0.md` del repositorio; si no coincide, no se descomprime ni se instala y se informa al responsable) y
descomprimirlo con `Expand-Archive`, con las mismas ordenes. En adelante `FECHA` es el sufijo del ZIP 0.7.0 y la
estructura puede llevar `cobertura` y `coberturaNivelCentral` (punto 4 de "Datos que debe entregar TI"); con 0.6.0
esas claves se retiran.

Como la cuenta de servicio, inicializar produccion con ese paquete, sin `-MismoReleaseQue` (la validacion sigue en
0.4.0; esa garantia la reemplaza el ensayo de H06e o, con 0.7.0, el de T06b) y con los mismos puertos (libres desde V2).
La estructura real se copia antes a `D:\Entregas\estructura-inicial-produccion.json`. Con acceso de funcionarios por red (firewall de TI;
**es la opcion decidida** el 2026-11-22):

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
el modo `AUTOMATICO` de la validacion, y sin `Set-Integration.ps1 -Formulario Manual` (despues de la copia) produccion
consumiria desde su primer arranque, sin sonda.

La copia nombra uno por uno los cinco archivos que usa la configuracion del Formulario y deja fuera las copias de
seguridad de `formulario-pull.json` que quedan en la validacion (en la VM, `*.bak-atomic-*` y `*.bak-auto-20261109`;
hallazgo de H06r2). Si se detiene con un error (falta alguno), no se sigue y se informa al responsable:

```powershell
Copy-Item -LiteralPath 'D:\SIGER-VALIDACION\config\formulario-pull.json','D:\SIGER-VALIDACION\config\formulario-pull-token.clixml','D:\SIGER-VALIDACION\config\formulario-ack-identity.clixml','D:\SIGER-VALIDACION\config\formulario-trusted-keys.json','D:\SIGER-VALIDACION\config\formulario-ca.pem' -Destination 'D:\SIGER\config' -ErrorAction Stop
```
```powershell
Get-ChildItem -LiteralPath 'D:\SIGER\config' -Filter 'formulario-*' | Select-Object Name,Length
```

Debe listar exactamente `formulario-ack-identity.clixml`, `formulario-ca.pem`, `formulario-pull.json`,
`formulario-pull-token.clixml` y `formulario-trusted-keys.json`, ningun `*.bak-*`. Luego, dejarla en Manual:

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

`Estado.ps1` debe mostrar `AppRunning` verdadero, `Health` UP, la version del paquete (0.7.0 o 0.6.0) y el
Formulario en `DESACTIVADO`: en Manual
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

Prueba final del acceso por red (produccion con `-AccesoRed`). Como administrador, comprobar que SIGER escucha en todas
las interfaces y el MySQL propio solo en loopback: el puerto 18543 aparece en `::` o `0.0.0.0` (Java en Windows abre un
socket de doble pila: `::` tambien atiende IPv4; ensayo de H06e) y el 13326 solo en `127.0.0.1`:

```powershell
Get-NetTCPConnection -LocalPort 18543,13326 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
```

Luego, desde un PC de funcionarios de la red 192.168.100.0/24, abrir `https://<nombre o IP de la VM>:18543` (con el
autofirmado el navegador advierte que no es de confianza y que el nombre no coincide: seccion 4). Debe mostrar el ingreso
de SIGER. Si no responde: revisar la regla de firewall de Windows del puerto 18543 (la de la validacion sirve, porque el
puerto es el mismo) y, si existe, el firewall por aplicacion de Kaspersky, que puede exigir una regla para el Java de
produccion, `D:\SIGER\releases\<release>\runtime\java25\bin\java.exe` (la ruta exacta es el proceso dueno del puerto
18543). No se cambian reglas sin TI; el resultado se anota y se informa al responsable.

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

## 5. Actualizacion de produccion a 0.8.0 (paso U)

Actualiza en sitio la produccion de `D:\SIGER` desde 0.7.0 (esquema 16; DI-15: instalada con 0.6.0 y actualizada a
0.7.0 con `Update.ps1`) al paquete 0.8.0 (`docs/PLAN_0_8_0.md`, seccion 3 y gate E14). No hay ensayo en TEST-SIGER
(precision de D50 del 2026-11-25): lo compensan las tareas de esta seccion. Se ejecuta solo despues de E17 CERRADO, con la
etiqueta `v0.8.0` y la **autorizacion expresa del responsable** para esta ventana. Los scripts son los mismos de 0.6.0 y
0.7.0 (`scripts\` sin cambios en 0.8.0). `FECHA080` es el sufijo del ZIP 0.8.0 (`SIGER-FECHA080.zip`); el registro de
E17 en `docs/BITACORA_0_8_0.md` del repositorio trae su nombre y su SHA-256.

Reglas de toda la seccion:

- **Solo lectura o cambio de estado.** Cada tarea dice cual es. Las de cambio de estado (U3 a U5 y U7) las ejecuta el
  responsable, o alguien en su presencia, con su autorizacion expresa para esta ventana; las de solo lectura (U1, U2 y
  U6) no cambian la instancia y se pueden repetir.
- **Consolas.** "Consola de svc-siger" es una consola de Windows PowerShell 5.1 abierta como `DDN-SIGER\svc-siger`; la
  abre el responsable (RDP con esa cuenta o `runas /user:DDN-SIGER\svc-siger powershell.exe`, que pide la contrasena en
  la propia ventana). "Consola de administrador" es una consola elevada de un administrador de la VM. Ninguna contrasena
  ni secreto se escribe en esta guia, en una orden ni en un archivo de notas.
- **Se preservan**: el paso P0 y `D:\Custodia-SIGER-P0`, los respaldos y constancias existentes (`D:\Respaldos-*`,
  `D:\SIGER-CONSTANCIAS`), los originales y documentos de `D:\SIGER\data`, y los paquetes inmutables de `D:\Entregas`
  (0.6.0, 0.7.0 y 0.8.0). Nada de esta seccion borra, renombra ni sobrescribe esos archivos.
- **Detencion.** Si una tarea no da el resultado esperado, no se pasa a la siguiente: se anota el resultado y se informa
  al responsable. Las cifras se anotan sin datos personales ni secretos.

### U0. Requisitos antes de fijar la ventana (responsable)

| | |
|---|---|
| Equipo y cuenta | Ninguno: es una verificacion documental del responsable |
| Precondiciones | 1) E17 CERRADO y etiqueta `v0.8.0`. 2) **DI-11 respondida** en la tabla de definiciones de la bitacora (alcance de la clasificacion retrospectiva en produccion: desde que fecha, que registros y quien); sin respuesta no se empieza. 3) Conformidad institucional de DI-01 a DI-04 registrada (2026-11-25), que cita U7. 4) Ventana (c) sin usuarios en SIGER: si es antes del 15/12/2026, debe terminar a mas tardar el 12/12/2026 a las 23:59 (al menos dos dias de margen antes de la apertura del Formulario del 15/12 a las 00:00); **con menos de 2 dias de margen no se ejecuta** y se deja para despues del 15/12. 5) Carpeta `D:\Respaldos-SIGER` con control total para `DDN-SIGER\svc-siger` (si no existe, la crea un administrador con las ordenes de abajo). 6) El ZIP 0.8.0 copiado en `D:\Entregas`. |
| Resultado esperado | Los seis puntos cumplidos y anotados con la fecha de la ventana |
| Criterio de detencion | Cualquier punto sin cumplir: no se fija la ventana |

Solo si falta la carpeta de respaldos, en la consola de administrador (cambio de estado fuera de la instancia):

```powershell
New-Item -ItemType Directory -Path 'D:\Respaldos-SIGER'
```
```powershell
icacls 'D:\Respaldos-SIGER' /grant 'DDN-SIGER\svc-siger:(OI)(CI)F'
```

### Entregas del Formulario mientras SIGER esta detenido

Verificado en E14 en el codigo de SIGER y del Formulario (chashmdai/formulario-web `6f886eb`, solo lectura). Durante la
ventana el consumidor automatico no corre y el Formulario sigue recibiendo y guardando entregas:

- Cada entrega nueva queda en `PENDIENTE` en la cola del Formulario
  (`formulario-publico/backend/src/main/java/cl/nexbyte/siger/formulario/RecepcionFormularioJdbc.java:67`). Solo se
  reclama con un reclamo de SIGER; el reclamo toma las `PENDIENTE` y las `RESERVADA` cuyo plazo vencio (`:124-136`), de
  modo que nada sale de la cola sin SIGER.
- La reserva de una entrega que SIGER tenia tomada al detenerse dura 5 minutos (`:132`, `lease_until_ms=now+300000`) y
  despues vuelve a ser reclamable. Si SIGER ya la registro
  (COM con ACK pendiente), al volver concilia primero los ACK pendientes (`backend/src/main/java/cl/nexbyte/sigera/FormularioConsumer.java:47-49`)
  y el Formulario acepta la confirmacion tardia (`RecepcionFormularioJdbc.java:156-172`, `REGISTRO_SIGER_CONFIRMADO_TARDIO`).
- **No hay plazo de retencion de entregas sin ACK.** Lo que el Formulario minimiza o borra exige una entrega confirmada:
  la custodia transitoria solo recorre `CONFIRMADA` (`custodia/CustodiaTransitoria.java:57` y `:86-104`), la retirada de
  adjuntos exige estado `CONFIRMADA` y ACK (`multimedia/CicloVidaCustodia.java:70`), y el abandono y la minimizacion de
  borradores solo tocan recorridos **sin** entrega (`CicloVidaCustodia.java:259-261`, `custodia/MinimizacionBorradores.java:65`).
- Limites que no pierden entregas: la cuarentena por mas de 10 reclamos (`RecepcionFormularioJdbc.java:134`) cuenta
  reclamos, no tiempo, y con SIGER detenido no hay reclamos; una entrega en cuarentena se repone (`:175`) y SIGER puede
  confirmarla (`:165`). El piso de espacio libre del Formulario (`custodia/GuardiaCapacidad.java:9-11`,
  `capacidad.minimo-libre`) solo impide **admitir** entregas nuevas si su disco se llena; no borra las guardadas.
- Al volver, el ciclo automatico drena la cola (`FormularioAutomatic.java:21`: un segundo entre entregas, reintentos de 10 s
  a 5 min). La COM registrada conserva su fecha de recepcion del Formulario: una entrega recibida durante la ventana no
  cambia su fecha ni su conteo.

Duracion maxima segura de la ventana: respecto de las entregas no hay un maximo tecnico (ningun plazo, tamano ni
limpieza las pierde mientras no estan confirmadas). La ventana se planifica por la atencion, no por la cola: **hasta 4
horas**. Si una falla (U5) deja SIGER detenido, las entregas siguen esperando sin perderse; si la detencion supera 24
horas, el responsable informa a la Direccion y al equipo del Formulario que la cola esta en espera.

### U1. Paquete 0.8.0 (preparacion, no cambia la instancia)

| | |
|---|---|
| Equipo, cuenta y consola | DDN-SIGER, administrador, consola de administrador |
| Precondiciones | U0 cumplido; `D:\Entregas\SIGER-FECHA080.zip` copiado |
| Resultado esperado | `certutil` muestra el mismo SHA-256 del registro de E17; la carpeta `D:\Entregas\SIGER-FECHA080` queda creada, con `manifest.json` 0.8.0 |
| Riesgos | Un paquete distinto del registrado (copia danada o equivocada) |
| Criterio de detencion | Huella distinta: no se descomprime ni se sigue |

```powershell
certutil -hashfile 'D:\Entregas\SIGER-FECHA080.zip' SHA256
```
```powershell
Expand-Archive -LiteralPath 'D:\Entregas\SIGER-FECHA080.zip' -DestinationPath 'D:\Entregas'
```
```powershell
Get-Content -LiteralPath 'D:\Entregas\SIGER-FECHA080\manifest.json'
```

`manifest.json` debe decir `"version": "0.8.0"`, el esquema de la ultima migracion del paquete (24, o la que registre
E17) y `"automatedTestsExecuted": true`.

### U2. Consultas previas (a), solo lectura

| | |
|---|---|
| Equipo, cuenta y consola | DDN-SIGER, `DDN-SIGER\svc-siger`, consola de svc-siger |
| Precondiciones | U1 conforme; SIGER en servicio (aun 0.7.0) |
| Resultado esperado | Version 0.7.0, esquema 16, diario `COMPLETE`, `datadir`, `tmpdir` e `innodb_temp_tablespaces_dir` en D:, espacio libre suficiente |
| Riesgos | Ninguno: no cambia nada |
| Criterio de detencion | Cualquier valor distinto del esperado; en particular, si alguna de las tres rutas no esta en D: **el paso U se detiene aqui**, antes del respaldo |

Estado de la instancia:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Estado.ps1' -Root 'D:\SIGER'
```

Debe mostrar `AppRunning` verdadero, `Health` UP, `Version` 0.7.0, `Migration` 16 y `Update` COMPLETE (diario de la
actualizacion de 0.6.0 a 0.7.0). Con otro valor en `Update`, `Update.ps1` se negaria a seguir: se informa.

Rutas del motor propio en su configuracion:

```powershell
Select-String -LiteralPath 'D:\SIGER\mysql\my.ini' -Pattern '^(datadir|tmpdir)='
```

Las mismas rutas vistas por el motor, con el bloque interactivo de solo lectura. Se pega completo en la consola de
svc-siger; usa las funciones de `D:\SIGER\scripts\Common.ps1`, crea un archivo temporal de cliente con la credencial de
la instancia (sin mostrarla) y lo borra al terminar aunque algo falle:

```powershell
. 'D:\SIGER\scripts\Common.ps1'
$s=Read-Settings 'D:\SIGER'; Assert-InstanceOwner $s 'la consulta previa'
$client=$null
try {
    $client=MySql-ClientFile $s (Import-Clixml 'D:\SIGER\config\secrets.clixml') 'owner'
    Require-OwnMySql $s $client
    Invoke-SigerSql $s $client 'SELECT @@datadir, @@tmpdir, @@innodb_temp_tablespaces_dir'
    Invoke-SigerSql $s $client 'SELECT COUNT(*) FROM communication_original' $s.database
} finally { Remove-SecretFile $client }
```

Las tres rutas de la primera linea deben empezar con `D:` (las relativas, como `.\#innodb_temp\`, quedan dentro del
`datadir`). La segunda cifra es la cantidad de originales del Formulario: se anota para U6.

Espacio libre en D: (respaldo, ensayo de restauracion y candidata; temporales del motor estadistico, medidos en E08b y
E14):

```powershell
Get-PSDrive -Name D | Select-Object Used,Free
```
```powershell
(Get-ChildItem -LiteralPath 'D:\SIGER' -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
```

El espacio libre debe ser al menos tres veces el tamano de `D:\SIGER` y nunca menor que 20 GiB (umbral con que se midio
el motor estadistico con 50.005 COM, muy por sobre el volumen real).

### U3. Detener SIGER (cambio de estado)

| | |
|---|---|
| Equipo, cuenta y consola | DDN-SIGER, `DDN-SIGER\svc-siger`, consola de svc-siger; ejecuta el responsable con su autorizacion expresa |
| Precondiciones | U2 conforme; ventana iniciada y sin usuarios |
| Resultado esperado | Servicio `SIGER` detenido y `SIGERMySQL` en ejecucion (el respaldo lo necesita) |
| Riesgos | Las entregas del Formulario esperan en su cola mientras dure la ventana (ver arriba); no se pierden |
| Criterio de detencion | El servicio no se detiene: no se sigue y se informa |

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Stop.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Estado.ps1' -Root 'D:\SIGER'
```

`Estado.ps1` debe mostrar `AppRunning` falso, `Service` SIGER detenido y `MySqlService` en ejecucion.

### U4. Respaldo y ensayo de restauracion (b), cambio de estado

| | |
|---|---|
| Equipo, cuenta y consola | DDN-SIGER, `DDN-SIGER\svc-siger`, consola de svc-siger; ejecuta el responsable |
| Precondiciones | U3 conforme; carpetas de destino nuevas; en la misma ventana e **inmediatamente antes** de U5 |
| Resultado esperado | `Backup.ps1` termina con "Respaldo coherente e integridad verificada"; `Restore-Check.ps1` con "Restauracion comprobada" y `restore-result.json` con `completed` verdadero |
| Riesgos | Ocupa espacio en D: (U2) y carga el motor durante la restauracion; no toca el esquema activo |
| Criterio de detencion | Cualquier error o `completed` distinto de verdadero: **no se actualiza**; se vuelve a iniciar SIGER 0.7.0 (`Start.ps1`) y se informa |

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Backup.ps1' -Root 'D:\SIGER' -Destination 'D:\Respaldos-SIGER\previo-0.8.0'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Restore-Check.ps1' -Root 'D:\SIGER' -BackupDirectory 'D:\Respaldos-SIGER\previo-0.8.0' -Destination 'D:\SIGER-RESTORE-PREVIO-080'
```
```powershell
Get-Content -LiteralPath 'D:\SIGER-RESTORE-PREVIO-080\restore-result.json'
```

`restore-result.json` debe decir `"completed": true` y trae el nombre del esquema restaurado (`siger_a_restore_*`),
que U6 usa para comparar. El respaldo y el ensayo se conservan.

### U5. Actualizacion (cambio de estado)

| | |
|---|---|
| Equipo, cuenta y consola | DDN-SIGER, `DDN-SIGER\svc-siger`, consola de svc-siger; ejecuta el responsable |
| Precondiciones | U4 conforme en esta misma ventana; SIGER detenido |
| Resultado esperado | `Update.ps1`: "Candidata migrada y seleccionada"; `Start.ps1` confirma version 0.8.0 y el esquema del paquete, y el diario queda en `COMPLETE` |
| Riesgos | Una migracion que falle a mitad no es reversible por si sola; por eso el respaldo de U4 |
| Criterio de detencion (d) | Ante `MIGRATION_FAILED`, `PROMOTION_FAILED` o `START_FAILED` en el diario (`Estado.ps1`, campo `Update`): **detenerse**. Se prohibe reintentar, reparar Flyway, borrar el diario o volver al JAR anterior. Se conservan el respaldo, el descriptor anterior (`config\instance-update-*.previous.json`), el diario (`config\update-state.json`) y los logs, y se informa al responsable (`docs/INSTALACION_VM.md`, "Actualizacion y recuperacion de error"). Las entregas del Formulario siguen esperando en su cola |

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Update.ps1' -Root 'D:\SIGER' -Release 'D:\Entregas\SIGER-FECHA080' -BackupDirectory 'D:\Respaldos-SIGER\previo-0.8.0'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Start.ps1' -Root 'D:\SIGER'
```

`Update.ps1` migra con el JAR candidato antes de seleccionarlo. Al primer arranque, 0.8.0 completa sus cargas
derivadas con marcador (resumen de cambios de las fichas, resultado de la primera atencion y dimensiones declaradas):
corren por lotes con el lock global y el arranque puede tardar algo mas que de costumbre. El Formulario vuelve en el
modo en que estaba (automatico) y drena su cola.

### U6. Verificacion (e), solo lectura

| | |
|---|---|
| Equipo, cuenta y consola | DDN-SIGER, `DDN-SIGER\svc-siger`, consola de svc-siger; luego un navegador con una cuenta con la funcion Reportes |
| Precondiciones | U5 conforme |
| Resultado esperado | Version 0.8.0, esquema del paquete, diario `COMPLETE`; los tres marcadores; `communication_original` con el mismo conteo de U2 y del respaldo y cero filas distintas; sin aviso `MOTOR_ESTADISTICO_DESHABILITADO`; el Explorador estadistico abre |
| Riesgos | Ninguno: no cambia nada |
| Criterio de detencion | Una cifra distinta: no se activa ningun catalogo (U7); se conserva todo y se informa al responsable |

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Estado.ps1' -Root 'D:\SIGER'
```

Debe mostrar `Health` UP, `Version` 0.8.0, `Migration` igual al esquema de `manifest.json` (U1) y `Update` COMPLETE.

Bloque interactivo de solo lectura (consola de svc-siger; reemplazar `siger_a_restore_AAAAMMDDHHMMSS_xxxxxx` por el
esquema de `restore-result.json` de U4):

```powershell
. 'D:\SIGER\scripts\Common.ps1'
$s=Read-Settings 'D:\SIGER'; Assert-InstanceOwner $s 'la verificacion'
$restore='siger_a_restore_AAAAMMDDHHMMSS_xxxxxx'
$client=$null
try {
    $client=MySql-ClientFile $s (Import-Clixml 'D:\SIGER\config\secrets.clixml') 'owner'
    Require-OwnMySql $s $client
    Invoke-SigerSql $s $client "SELECT code, applied_ms, amount FROM application_marker WHERE code IN ('RESUMEN_CAMBIOS_V18','RESULTADO_INMEDIATO_V20','DIMENSIONES_V21') ORDER BY code" $s.database
    Invoke-SigerSql $s $client "SELECT (SELECT COUNT(*) FROM communication_original), (SELECT COUNT(*) FROM ``$restore``.communication_original), (SELECT COUNT(*) FROM communication_original a LEFT JOIN ``$restore``.communication_original b ON b.id=a.id AND b.document_sha256=a.document_sha256 WHERE b.id IS NULL), (SELECT COUNT(*) FROM ``$restore``.communication_original b LEFT JOIN communication_original a ON a.id=b.id AND a.document_sha256=b.document_sha256 WHERE a.id IS NULL)" $s.database
} finally { Remove-SecretFile $client }
```

La primera consulta debe listar los tres marcadores. La segunda devuelve cuatro cifras: originales vivos, originales
del respaldo, vivos sin su par identico en el respaldo y del respaldo sin su par identico vivos. Las dos primeras deben
ser iguales entre si y a la de U2, y las dos ultimas, 0. (Si el Formulario ya registro entregas nuevas despues de
iniciar, la primera cifra es mayor y la tercera cuenta exactamente esas entregas: se anota la diferencia; la cuarta
sigue siendo 0.)

Motor estadistico habilitado: sin el aviso en el log desde el arranque de U5 (no debe devolver lineas):

```powershell
Select-String -Path 'D:\SIGER\logs\siger*.log' -Pattern 'MOTOR_ESTADISTICO_DESHABILITADO'
```

Y con una cuenta con la funcion Reportes: menu "Explorador estadistico", seccion Panorama; abre con sus indicadores (sin
catalogos vigentes, las clasificaciones figuran como pendientes, lo que es correcto hasta U7). TI no ve el explorador
(D2). Si nadie tiene aun Reportes, TI la concede en el perfil de la cuenta de Estudios que designe el responsable.

### U7. Activacion de los catalogos (f), cambio de estado

| | |
|---|---|
| Equipo, cuenta y consola | Navegador, cuenta TI de produccion; con autorizacion expresa del responsable |
| Precondiciones | U6 conforme; conformidad institucional de DI-01 a DI-04 (Direccion de UEP, 2026-11-25) |
| Resultado esperado | Los doce catalogos en estado VIGENTE, cada uno con su fundamento |
| Riesgos | Desde la activacion, los cierres de REQ y de primera atencion exigen las clasificaciones del catalogo vigente (D28); antes de activar nada se exige |
| Criterio de detencion | Un archivo rechazado al importar: no se activa ese catalogo y se informa (los archivos son los mismos que importan las pruebas de `ReferenceCatalogsTest`) |

En "Catalogos estadisticos" (`#catalogos`), "Importar catalogo" con cada archivo de
`D:\Entregas\SIGER-FECHA080\config-ejemplo\catalogos\` (doce archivos `.json`; `LEEME.txt` trae sus fuentes y huellas)
y, para cada version importada, "Activar" con el fundamento:

> Conformidad institucional de DI-01 a DI-04 (Direccion de UEP, 2026-11-25), plan 0.8.0.

Al terminar, cada catalogo muestra una version VIGENTE. La clasificacion retrospectiva de registros anteriores sigue lo
que defina DI-11 (U0) y usa el mecanismo de reapertura vigente (DI-07).

Despues, TI revisa en Auditoria y operacion el Formulario en "Esperando entregas" con su ultimo exito reciente, y el
responsable anota la ventana con sus cifras (U2, U4 y U6) en la bitacora de produccion.

## 6. Actualizacion de produccion a 0.8.1 (paso U81)

Actualiza en sitio la produccion de `D:\SIGER` de 0.8.0 a 0.8.1 (`docs/PLAN_0_8_1.md`, D57 y seccion 9). 0.8.1 es solo
interfaz: no tiene migraciones (esquema 24, igual que 0.8.0) ni cambia `scripts\`, el servidor, las consultas ni las
cifras. Lo ejecuta el responsable, solo en DDN-SIGER y solo en D:, con **autorizacion expresa** para esta ventana y
**despues de F03 CERRADO** (paquete y etiqueta `v0.8.1`). Esta seccion no agrega ordenes: cada tarea usa las mismas de la
seccion 5, con el paquete 0.8.1 en lugar del 0.8.0. `FECHA081` es el sufijo del ZIP 0.8.1 (`SIGER-FECHA081.zip`); el
registro de F03 en `docs/BITACORA_0_8_1.md` trae su nombre, su tamano y su SHA-256 (y los del JAR). Rigen las reglas de
la seccion 5 (solo lectura o cambio de estado, consolas, lo que se preserva y la detencion). El paquete 0.8.0 y las
etiquetas anteriores no se tocan.

| Tarea | Tipo | Como (ordenes de la seccion 5) | Resultado esperado |
|---|---|---|---|
| U81-0. Requisitos | Responsable | F03 CERRADO; ventana acordada; aviso a los usuarios | Ventana autorizada por el responsable |
| U81-1. Paquete | Preparacion, no cambia la instancia | Como U1, con `D:\Entregas\SIGER-FECHA081.zip`: `certutil` contra el SHA-256 del registro de F03 antes de descomprimir, `Expand-Archive` y lectura de `manifest.json` | Misma huella; `manifest.json` con `"version": "0.8.1"`, `"schema": 24` y `"automatedTestsExecuted": true` |
| U81-2. Detener SIGER | Cambio de estado | Como U3 | Servicio detenido; las entregas del Formulario esperan en su cola, como en la seccion 5 |
| U81-3. Respaldo y ensayo | Cambio de estado | Como U4, inmediatamente antes de actualizar (respaldo y `Restore-Check.ps1`), con carpetas nuevas para 0.8.1 en lugar de las de U4 (por ejemplo `previo-0.8.1` y `D:\SIGER-RESTORE-PREVIO-081`); las de 0.8.0 se conservan | Respaldo verificado y restauracion de ensayo conforme |
| U81-4. Actualizacion | Cambio de estado | Como U5: `Update.ps1` con la carpeta del paquete 0.8.1 y arranque | Diario sin `MIGRATION_FAILED`, `PROMOTION_FAILED` ni `START_FAILED`; ninguna migracion aplicada (esquema 24) |
| U81-5. Verificacion | Solo lectura | Como U6: `/api/health` y, en el navegador, una cuenta con Reportes | `/api/health` con `version` 0.8.1 y `schema` 24; en "Explorador estadistico", un clic en la cifra de una tarjeta del Panorama abre Explorar con la consulta del indicador |

- **Sin U2 ni U7.** Los temporales de MySQL ya se verificaron en U2 de 0.8.0 y 0.8.1 no los cambia; la activacion de los
  catalogos (U7 de 0.8.0) sigue su propio curso con UEP y no depende de 0.8.1.
- **Ante una falla** se aplica la detencion de U5 (seccion 5): no se reintenta ni se vuelve al JAR anterior por cuenta
  propia; se conservan el respaldo de U81-3, el descriptor anterior (`config\instance-update-*.previous.json`), el diario
  y los logs, y se informa al responsable. La vuelta atras es la de la seccion 5 (descriptor anterior y respaldo de U81-3).
- **Despues**: el responsable anota la ventana (U81-1 a U81-5, sin datos personales ni secretos) en la bitacora de
  produccion.
