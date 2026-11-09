# Instalacion aislada en DDN-SIGER

## Condicion de entrada

Compilar primero en el PC. No ejecutar esta instalacion sobre un paquete cuyo build haya fallado. Los scripts estan preparados para Windows PowerShell 5.1, pero **no se ejecutaron en Windows durante la preparacion de esta entrega**: revisaremos juntos la primera ejecucion.

Usar solo datos sinteticos. La VM tambien aloja sistemas existentes: el aislamiento de puertos/carpetas evita compartir instalacion, pero no elimina la competencia por CPU, RAM o disco. El inventario aportado indica 16 GB totales y alrededor de 7 GB libres en ese momento. El launcher limita Java a 1 GB y configura el buffer MySQL a 256 MB; esto no equivale a un limite total del consumo del motor.

## Lo que se copia a D:

1. El ZIP de release generado por `Build.ps1 -IncludeJava` y extraido, por ejemplo en `D:\Entregas\SIGER-<fecha>`.
2. Una distribucion **ZIP noinstall MySQL Community 8.4 Windows x64** extraida en una carpeta nueva como `D:\Herramientas\mysql-8.4`. El parametro MySqlHome debe apuntar a la carpeta que contiene `bin\mysqld.exe`, no al propio bin ni al datadir.
3. El runtime Microsoft Visual C++ requerido por esa distribucion, solo si falta y TI autoriza instalarlo. No viene integrado ni se instala automaticamente. Que `mysqld.exe --version` falle puede indicar esta dependencia; no borres ni cambies el MySQL80 existente.

Descargas oficiales para los componentes: https://dev.mysql.com/downloads/mysql/ y https://adoptium.net/temurin/releases/ . Elegir Windows x64, conservar version, licencias y huella publicada. No subir binarios o claves al chat.

El release incluye Java si se construyo con `-IncludeJava`; no necesita Node, Maven, VS Code ni fuentes en el servidor.

## Directorios

`D:\SIGER-A` debe **no existir** al inicializar. Se creara con ACL solo para la identidad instaladora, Administradores y SYSTEM; el resto de D: no cambia.

- `releases/`: JAR y runtime por version.
- `mysql/data/`: datadir del motor nuevo.
- `mysql/my.ini`: configuracion exclusiva, enlace 127.0.0.1, puerto 13326, MySQL X desactivado.
- `config/`: descriptor de instancia, claves cifradas DPAPI, keystores.
- `data/documents/`: originales privados.
- `data/bootstrap-usuarios.csv`: accesos sinteticos iniciales; dato privado.
- `logs/`, `tmp/`, `run/`: archivos de esta instancia.
- `scripts/`: herramientas propias de operacion.

No usar C: para runtime/datos por su escaso espacio. No apuntar MySqlHome al datadir de otro motor ni copiar configuraciones de MySQL80.

### Claves confiables del Formulario Publico (opcional)

Las Comunicaciones externas del Formulario solo se aceptan si su sello de custodia Ed25519 verifica con una clave publica confiable del operador. Copiar el mismo archivo que SMR usa como `institucional.comprobante.trusted-keys` (formato `{"keyId":"-----BEGIN PUBLIC KEY-----..."}`, hasta 20 claves) a `config\formulario-trusted-keys.json` dentro de la raiz de la instancia, con la misma ACL de `config/`. `Environment.ps1` lo entrega como `SIGER_FORMULARIO_TRUSTED_KEYS`.

- Sin el archivo, SIGER rechaza todo original externo (comportamiento por defecto).
- Un archivo presente pero invalido detiene el arranque: corregirlo o retirarlo.
- Quien pueda escribir el archivo puede agregar una clave confiable: protegerlo como un secreto de integridad. Retirar una clave exige reiniciar y no invalida originales ya conservados.
- Este archivo no activa ningun consumidor PULL/ACK ni conexion con SMR.

### Consumidor del Formulario Publico (PULL/ACK, inactivo por defecto)

Por defecto el servicio no consume la cola del Formulario y no hay endpoint ni comando de funcionario para hacerlo; el modo permanente se describe en "Operacion permanente 24/7". Un ciclo (concilia ACK pendientes, reclama a lo mas una entrega, la valida, descarga y custodia sus originales con SHA-256, registra COM + originales + comprobante en un COMMIT y recien entonces confirma) se ejecuta solo a mano con `scripts\Invoke-FormularioPull.ps1`, en un proceso aparte sin servidor web ni tareas, que termina al acabar el ciclo. No crea SOL, REQ ni Caso.

Archivos en `config\` (fuera del repositorio, con la ACL privada de la raiz; el script rechaza permisos mas amplios en la privada y en la credencial):

| Archivo | Contenido |
|---|---|
| `formulario-trusted-keys.json` | Clave publica de custodia del Formulario (ver arriba). |
| `formulario-pull.json` | `{"origin":"https://formulario.defensorianinez.gob.cl","scope":"<codigo del ambito receptor>","ackKeyId":"<keyId del ACK>","caFile":""}`. `caFile` es opcional (certificado PEM de CA en `config\`); vacio usa la confianza del JDK. |
| `formulario-pull-token.clixml` | Bearer entregado privadamente, protegido con DPAPI de la cuenta que ejecuta el servicio: `Read-Host -AsSecureString \| Export-Clixml D:\SIGER-A\config\formulario-pull-token.clixml`. |
| `formulario-ack-private.pem` / `formulario-ack-public.pem` | Par Ed25519 del comprobante institucional, generado una vez con `scripts\New-FormularioAckKey.ps1 -Root D:\SIGER-A`. Solo la publica (con su keyId) se entrega al operador del Formulario. |

`instance.json` debe incluir `"folioPrefix"` compatible con el folio del comprobante (`[A-Z][A-Z0-9]{1,15}`, por ejemplo `COMA`): el prefijo por defecto `COM-A` no lo es y el consumidor no arranca con el. El prefijo aplica a todas las COM nuevas (manuales y externas) y requiere reiniciar el servicio; las COM existentes conservan su folio.

Reglas que el procedimiento no puede verificar por si solo:

- **Un solo consumidor por cola.** El Bearer da acceso a la misma cola que el consumidor del Formulario de SMR. Antes de cada ciclo debe estar detenido, con autorizacion, el consumidor de SMR; de lo contrario las entregas se reparten entre ambos sistemas. El script exige `-ConsumidorUnicoConfirmado`. El lease `formulario_consumer_lock` solo impide dos ciclos simultaneos de SIGER.
- Los originales descargados quedan en cuarentena como cualquier original de ingreso; su descarga sigue la regla vigente de liberacion.
- Un ciclo con incidentes termina con codigo distinto de cero y deja el detalle tecnico (sin datos ciudadanos ni credenciales) en `logs\formulario-pull-<fecha>.*.log` y en `formulario_reception`. No se reintenta automaticamente: diagnosticar antes de repetir.
- Un ACK pendiente (respuesta perdida, reserva vencida) se concilia en el ciclo siguiente con el mismo comprobante, UUID institucional y folio; nunca se crea otra COM. Rotar la clave ACK con ACK pendientes los bloquea para revision.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Invoke-FormularioPull.ps1' -Root 'D:\SIGER-A' -ConsumidorUnicoConfirmado
```

## Paso 1. Consulta previa

En PowerShell elevada de la VM, verificar que los puertos siguen disponibles:

```powershell
Get-NetTCPConnection -State Listen | Where-Object {$_.LocalPort -in @(13326,18543)} | Select-Object LocalAddress,LocalPort,OwningProcess
```

Sin resultados significa sin listener observado, **no** una reserva permanente ni prueba de conectividad. El instalador vuelve a comprobarlo y falla si estan ocupados; nunca mata otro proceso.

## Identidades de Windows (instalacion institucional)

Se separan dos identidades:

| Identidad | Para que | Privilegios |
|---|---|---|
| **Cuenta de servicio de SIGER** (p. ej. `DOMINIO\svc-siger`, cuenta de dominio o local) | Inicializa la instancia, es duena de su custodia DPAPI (`config\*.clixml`), ejecuta los servicios `SIGER` y `SIGERMySQL` y las tareas de operacion (Start, Stop, Estado, Backup, Migrate, Update, Import-Integrations, Test-Service). | **Ninguno administrativo.** Solo "Iniciar sesion como servicio", inicio de sesion local durante la instalacion (para `runas`), escritura en `D:\SIGER-A` y control de sus dos servicios. |
| **Administrador de TI** | Crea la cuenta, prepara `D:\SIGER-A` vacia si la cuenta no puede crearla, y ejecuta una sola vez `Install-Service.ps1` (elevado). | Administrador local, solo durante la instalacion. |

Por DPAPI, todo lo que lee secretos (arranque, respaldo, migracion, importaciones) se ejecuta **como la cuenta de servicio**, por ejemplo `runas /user:DOMINIO\svc-siger powershell.exe`. El administrador no descifra ni necesita la custodia. No se admite inicializar una instancia institucional con una cuenta administradora.

Si TI crea la raiz por la cuenta: `New-Item -ItemType Directory D:\SIGER-A` y `icacls D:\SIGER-A /grant "DOMINIO\svc-siger:(OI)(CI)F"` (debe quedar vacia; `Init-Server.ps1` la vuelve privada para la cuenta, SYSTEM y Administradores).

## Paso 2. Inicializacion unica

Sustituir solo las rutas del release y de MySQL por las carpetas efectivamente extraidas. Cada orden es una linea.

**Institucional** (sin cuentas, sedes ni datos de prueba), ejecutada **como la cuenta de servicio**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Init-Server.ps1' -Mode Institucional -EstructuraInicial 'D:\Entregas\estructura-inicial.json' -Root 'D:\SIGER-A' -Release 'D:\Entregas\SIGER-FECHA' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13326 -WebPort 18543
```

`estructura-inicial.json` lo prepara la institucion (dato real; no se versiona):

```json
{"fundamento":"Resolucion o acto que designa a TI y a la autoridad habilitante inicial",
 "ambitos":[{"codigo":"UGI","nombre":"Gestion Institucional","unidad":"UGI","sede":"CENTRAL"},
            {"codigo":"UPR-REQ","nombre":"Requerimientos / Atencion de Personas","unidad":"UPR","area":"REQ","sede":"CENTRAL","regional":false}],
 "ti":{"usuario":"nombre.apellido","nombre":"Nombre de la persona TI","correo":"persona@defensorianinez.cl","ambito":"UGI"},
 "autoridad":{"usuario":"nombre.autoridad","nombre":"Nombre de la autoridad","correo":"autoridad@defensorianinez.cl","ambito":"UGI","ambitos":["*"]}}
```

- `unidad` es uno de los codigos del producto: UPR, UPD, UEP, UGI, REG, DAI, GAB, DEF, AUD. `ambitos` de la autoridad acepta `"*"` (todos los listados) o codigos.
- TI y autoridad son personas distintas. TI administra cuentas sin acceso a expedientes; la autoridad concede las demas funciones por la interfaz. Ambas reciben clave temporal individual en `data\bootstrap-usuarios.csv` con cambio obligatorio: entregarlas por canal seguro y retirar el archivo.
- Un archivo invalido detiene la inicializacion sin escribir datos. Se admite UTF-8 con o sin BOM.
- Esquema `siger_a_inst`; sin etiqueta de entorno en pantalla; la liberacion de originales exige analisis antivirus.

**Pruebas** (datos sinteticos, como hasta ahora; no usar para datos ciudadanos):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Init-Server.ps1' -Mode Pruebas -Root 'D:\SIGER-PRUEBAS' -Release 'D:\Entregas\SIGER-FECHA' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13327 -WebPort 18544
```

La orden crea la instancia y sus cuentas MySQL, TLS local, el esquema y la migracion (y, en modo Pruebas, los datos sinteticos). No usa credenciales root del MySQL existente. Durante la inicializacion se utiliza un archivo privado temporal de instrucciones SQL y luego se elimina; el datadir no se expone a red externa. No se instala un servicio Windows ni se cambia PATH, JAVA_HOME, firewall o confianza global.

La cuenta habitual de la aplicacion tiene DML; la migradora es distinta. La cuenta administrativa del motor nuevo solo sirve a sus scripts. DPAPI vincula secretos al usuario Windows que inicializo la instancia: ejecutar despues bajo otra identidad requiere reprovision/traslado controlado, no copiar XML y asumir que descifra.

**Si falla:** no repetir con `--initialize` ni borrar D:\SIGER-A. Conservar el error y revisar el log del motor propio. Una inicializacion parcial requiere diagnostico, no reinstalacion destructiva automatica.

## Paso 3. Arranque y acceso

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Start.ps1' -Root 'D:\SIGER-A'
```

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Estado.ps1' -Root 'D:\SIGER-A'
```

Abrir **https://localhost:18543** en el navegador **dentro de la VM**. Desde el PC, localhost seria tu PC, no el servidor. No hay acceso LAN habilitado por estos scripts.

El certificado es autofirmado para localhost/127.0.0.1 y no se instala en el almacen de confianza global. El navegador puede advertirlo. Antes de aceptar una excepcion local de prueba, contrastar la huella del certificado que muestra con la del keystore propio, usando `keytool -list -v` y su contrasena de forma privada. No deshabilitar TLS o validacion global ni generalizar esa excepcion a otros sitios. Para acceso institucional posterior se necesita hostname/certificado aprobado y configuracion de red especifica.

Consultar `data\bootstrap-usuarios.csv` **localmente**, sin pegarlo en chats/logs/capturas. Cada cuenta tiene clave aleatoria distinta y cambio obligatorio. Despues de cambiarla, entrar de nuevo. Conserva o elimina de forma controlada el archivo temporal de accesos cuando ya no se necesite; contiene claves iniciales, no un mecanismo de recuperacion permanente.

## Paso 4. Parada

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Stop.ps1' -Root 'D:\SIGER-A'
```

Para detener tambien el motor nuevo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Stop.ps1' -Root 'D:\SIGER-A' -AlsoMySql
```

El PID de la aplicacion se contrasta contra su ejecutable/ruta. En Windows esta parada de proceso **no es un drenaje productivo certificado**. Hacerla sin usuarios realizando operaciones. MySQL se apaga autenticadamente, no con `taskkill` global. No toca MySQL80, Tomcat ni SQL Server.

## Respaldo y actualizacion

Con SIGER parado y su motor propio disponible:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Backup.ps1' -Root 'D:\SIGER-A' -Destination 'D:\Respaldos-SIGER-A\PRUEBA-001'
```

La carpeta destino debe ser nueva. Incluye SQL, originales, configuracion privada y hashes. **No esta cifrada como archivo portable**: esta protegida por ACL y contiene informacion privada. El dump puede incluir hashes de contrasenas. DPAPI requiere la identidad Windows original. No se acredita respaldo externo, restauracion o RPO/RTO por crear esta copia.

Para actualizar, despues de compilar una nueva version y hacer respaldo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Update.ps1' -Root 'D:\SIGER-A' -Release 'D:\Entregas\SIGER-NUEVA' -BackupDirectory 'D:\Respaldos-SIGER-A\PRUEBA-001'
```

La version anterior se conserva. Si una migracion falla, no arrancar un JAR anterior a ciegas. Reversion de datos/esquema y ensayo de restauracion quedan pendientes de calificacion manual. No se incluyen comandos destructivos automatizados.

## Complemento operativo 0.3.1

Las ordenes anteriores de instalacion siguen requiriendo build valido y datos sinteticos. La 0.3.1 no instala software de forma remota; son herramientas que ejecuta el operador bajo la misma identidad Windows.

### Comprobar arranque

`Start.ps1` ya no declara exito por crear el PID: consulta HTTPS local con el certificado propio fijado y exige version del manifiesto y esquema compatible (V4 o posterior). Si falla, conserva logs y devuelve error. `Estado.ps1` permite repetir el diagnostico. No deshabilita TLS ni cambia el almacen global. No usarlo como comprobacion de acceso LAN: solo verifica localhost.

### Respaldo y ensayo de restauracion

Con la aplicacion detenida y solo el MySQL propio activo, generar respaldo en carpeta nueva. La copia incluye SQL, documentos, configuracion privada, conteos por tabla y manifiesto de hashes. Si tu VM ya tenia scripts viejos, ejecutar Backup desde el nuevo release compilado, usando su parametro Root; no hace falta migrar primero para respaldar.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Backup.ps1' -Root 'D:\SIGER-A' -Destination 'D:\Respaldos-SIGER-A\PRUEBA-031'
```

Ensayar la copia en **carpeta nueva fuera de la instancia** y esquema nuevo generado automaticamente en el motor MySQL propio verificado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Restore-Check.ps1' -Root 'D:\SIGER-A' -BackupDirectory 'D:\Respaldos-SIGER-A\PRUEBA-031' -Destination 'D:\SIGER-A-RESTORE-PRUEBA-031'
```

Solo informa `completed=true` tras validar hashes de copia, importacion SQL, conteos y documentos restaurados. No arranca otra aplicacion, no cambia instance.json ni sobreescribe la base activa. Conserva carpeta/esquema del ensayo para revision; su eliminacion posterior es una operacion expresa, no automatica.

La restauracion comparte el motor dedicado y por tanto consume recursos de esa VM; hacerla en una ventana acordada sin usuarios en SIGER. No acredita recuperacion desde otro host ni descifrado DPAPI con otro usuario. No entregar dumps/configuracion a terceros.

### Actualizacion y recuperacion de error

El actualizador exige respaldo completo 0.3.1 de la instancia/version activa y un release con pruebas no omitidas. Copia la candidata, conserva descriptor anterior, instala el bloqueo de arranque y migra con el JAR candidato **antes** de cambiar la referencia activa. Tras exito queda PENDING_START hasta comprobar salud.

MIGRATION_FAILED/PROMOTION_FAILED bloquean inicio ambiguo. START_FAILED permite reintentar la misma candidata tras diagnostico. No borrar el diario, hacer repair o cambiar al JAR anterior para forzar verde: una migracion parcialmente aplicada puede no ser reversible. Conservar respaldo, descriptor previo, error y logs; recuperar sobre otra copia o corregir hacia delante con evidencia. La copia previa no equivale a un rollback automatico.

Los scripts siguen sin ejecutarse aqui sobre Windows/MySQL. Su primera validacion es parte de la prueba guiada, no una evidencia ya obtenida.

## Operacion permanente 24/7 (V9-V10)

Esta seccion prepara SIGER para operar como servicio permanente. Nada de lo descrito se activa solo: cada paso es una orden explicita del operador. El esquema sube a V10 (`Migrate.ps1` con respaldo previo; `Start.ps1` exige esquema 10).

### Servicios de Windows (SIGER y su MySQL propio)

`Install-Service.ps1` lo ejecuta **un administrador** (PowerShell elevada) indicando la cuenta de servicio, y registra dos servicios **sin iniciarlos**, ambos con esa cuenta sin privilegios administrativos:

- `SIGERMySQL`: el motor propio de la instancia (`mysqld --install` con su `mysql\my.ini`), inicio automatico, ejecutado con la cuenta de servicio (duena del datadir).
- `SIGER`: un host propio (`service\SigerService.exe`, compilado en ese momento desde `scripts\SigerService.cs` con el compilador de .NET Framework de Windows; sin binarios de terceros) que ejecuta `scripts\Service.ps1`. Inicio automatico diferido, dependiente de `SIGERMySQL`, recuperacion con reinicio a los 30 s, 60 s y 5 min.

El script exige que la cuenta de servicio sea **distinta** del administrador que instala y **la misma que inicializo la instancia** (`ownerSid` en `instance.json`). Lanza `Test-ServiceIdentity.ps1` con esa cuenta y perfil cargado para comprobar que **no pertenece a Administradores** y que descifra la custodia DPAPI; si falla, no registra nada. Concede a la cuenta permiso para iniciar, detener y consultar solo esos dos servicios. El derecho "Iniciar sesion como servicio" se comprueba y, con `-ConcederInicioComoServicio`, se agrega en la directiva local; una directiva de dominio puede prevalecer y debe gestionarla TI. La contrasena pasa al sistema como objeto, nunca como argumento.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Install-Service.ps1' -Root 'D:\SIGER-A' -ServiceCredential (Get-Credential 'DOMINIO\svc-siger') -ConcederInicioComoServicio
```

Despues, **como la cuenta de servicio**, `Start.ps1`, `Stop.ps1`, `Start-MySql.ps1` y `Estado.ps1` operan sobre los servicios (mismas ordenes de siempre). `Backup.ps1`, `Migrate.ps1` y `Update.ps1` siguen exigiendo SIGER detenido (`Stop.ps1`). `Uninstall-Service.ps1` retira solo el registro, con los servicios detenidos; no borra datos.

Comportamiento del servicio:

- **Parada ordenada**: el host crea `run\stop.request`; SIGER cierra su contexto, termina el ciclo del Formulario o del correo en curso, libera sus leases y sale. Si no termina en 180 s, el host termina su arbol de procesos (Job Object). `Stop.ps1` sin servicio usa la misma solicitud antes de detener el proceso.
- **Caida**: si Java termina sin solicitud de parada, el host sale con error y Windows aplica la recuperacion. Un `OutOfMemoryError` termina la JVM (`-XX:+ExitOnOutOfMemoryError`) para que se reinicie limpia.
- **Reinicio del equipo**: `SIGERMySQL` inicia primero; `Service.ps1` espera hasta 120 s a que acepte conexiones y repite todas las comprobaciones de `Start.ps1` (JAR, actualizacion, motor propio).
- **Logs**: `logs\siger.log` rotativo (20 MB, 30 archivos, tope 1 GB) y `logs\service-<fecha>.*.log` para errores tempranos de la JVM. El registro de eventos Application (origen `SIGER`) anota caidas.

### Consumidor automatico del Formulario

Con `"mode":"AUTOMATICO"` en `config\formulario-pull.json` el servicio consume la cola de forma permanente con el mismo ciclo del modo manual: concilia ACK pendientes, reclama una entrega, valida, custodia los originales, registra en un COMMIT y confirma despues. Agrega:

- **Exclusividad**: lease SQL del proceso, renovado por un latido y conservado entre ciclos; ningun otro proceso de SIGER (ni `Invoke-FormularioPull.ps1`, que rechaza el modo AUTOMATICO) consume en paralelo. Si el lease se pierde, el ciclo se detiene sin reclamar, registrar ni confirmar.
- **Espera**: sondeo cada 15 s, drenaje inmediato tras una entrega y espera creciente de 10 s a 5 min ante incidentes.
- **Recuperacion**: tras una caida o reinicio, el lease vencido se retoma y los ACK pendientes se concilian con el mismo comprobante; nunca se crea otra COM.
- **Pausa** auditada por TI desde Auditoria y operacion, sin reiniciar.

La exclusividad con **otros sistemas no es verificable por SIGER**: el modo AUTOMATICO exige declarar `"consumidorUnicoConfirmado": true` despues de detener con autorizacion el consumidor del Formulario de SMR (ver el procedimiento de sustitucion abajo).

```json
{"origin":"https://formulario.defensorianinez.gob.cl","scope":"<ambito receptor>","caFile":"formulario-ca.pem","mode":"AUTOMATICO","consumidorUnicoConfirmado":true}
```

### Correo institucional (Microsoft Graph)

Con `config\correo-graph.json` en modo `GRAPH` y el secreto en `config\graph-client-secret.clixml`, cada aviso interno genera su correo en el mismo COMMIT (outbox `mail_outbox`). El servicio lo envia con OAuth client_credentials y `sendMail` (`https://login.microsoftonline.com` y `https://graph.microsoft.com`, fijos, sin redirecciones):

```json
{"mode":"GRAPH","tenant":"<GUID del tenant>","client":"<GUID de la aplicacion>","sender":"<buzon remitente>","allowedDomains":["defensorianinez.cl"],"link":"https://<host institucional de SIGER>"}
```

- El correo lleva solo el texto generico del aviso, el enlace opcional y una referencia; nunca datos de expedientes. El destinatario se resuelve en cada intento desde su cuenta vigente: cuenta inactiva, sin correo, dominio no permitido o aviso ya leido en SIGER se suprimen sin enviar.
- Cada intento queda registrado **antes** de la red (`mail_attempt`). 202 = aceptado por Graph (no es entrega). 429 o fallo sin conexion se reintentan con espera, hasta 8 intentos; un 4xx se rechaza.
- **Envio ambiguo**: un 5xx, una respuesta perdida o un intento interrumpido por caida quedan `AMBIGUO` y **nunca se reenvian solos**. TI decide en Auditoria y operacion reenviar (el destinatario podria recibirlo dos veces) o descartar; la decision queda auditada.
- Activar el correo no envia avisos anteriores. Con `"mode":"DESACTIVADO"` o sin archivo no hay correo.

Se eliminaron las restricciones de laboratorio heredadas de SMR (allowlist de un solo destinatario, presupuesto de cuatro intentos, asunto "[SIGER LAB]" y texto de prueba). Se conservan las salvaguardas: dominio institucional obligatorio, intento durable, AMBIGUO sin reenvio y pausa auditada.

### Reutilizar credenciales existentes (SMR, Graph, certificado)

`Import-Integrations.ps1` copia a `config\` lo compatible, descifrando con DPAPI de la cuenta actual solo en memoria y volviendo a proteger con DPAPI. Solo **lee** SMR; no lo detiene ni modifica; no sobrescribe archivos existentes ni imprime secretos.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Import-Integrations.ps1' -Root 'D:\SIGER-A' -SmrRoot '<raiz de SMR>' -FormularioBearer -AckIdentity -FormularioTrust -FormularioCa
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Import-Integrations.ps1' -Root 'D:\SIGER-A' -GraphSecretPrompt
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Import-Integrations.ps1' -Root 'D:\SIGER-A' -HttpsPfx '<certificado.pfx>'
```

| Opcion | Origen | Destino |
|---|---|---|
| `-FormularioBearer` | `instances\recepcion\private\producer-token.dpapi` de SMR | `formulario-pull-token.clixml` |
| `-AckIdentity` | `secrets\ack-identity.dpapi` de SMR (keyId y par Ed25519 que el Formulario ya confia) | `formulario-ack-identity.clixml`; tiene prioridad sobre los PEM |
| `-FormularioTrust` | `config\formulario-signing-trust.json` de SMR | `formulario-trusted-keys.json` |
| `-FormularioCa` | `config\public-edge-root-ca.crt` de SMR (PEM) | `formulario-ca.pem` |
| `-GraphSecretFile` / `-GraphSecretPrompt` | archivo de secreto existente o entrada privada | `graph-client-secret.clixml` |
| `-HttpsPfx` | PFX vigente con clave privada | `https.p12` (conserva la clave del keystore; guarda el anterior) |

DPAPI exige que SMR y SIGER usen la misma cuenta de Windows; si no, el script lo informa y no importa nada. Reutilizar la identidad ACK de SMR evita instalar otra clave en el Formulario, pero obliga a que SMR **no** vuelva a confirmar entregas mientras SIGER consume. El certificado HTTPS se usa tras reiniciar; el enlace sigue en 127.0.0.1 (abrir la red es otra decision).

### Monitoreo y latidos vencidos

- `Estado.ps1`: servicios, salud HTTPS, esquema y estado de cada integracion (`DESACTIVADO`, `MANUAL`, `INICIANDO`, `ESPERANDO_ENTREGAS`, `RECIBIENDO`, `OCUPADO`, `PAUSADO`, `DEGRADADO`, `SIN_LATIDO`; correo: `OPERATIVO`, `ENVIANDO`, `PAUSADO`, `DEGRADADO`, `SIN_LATIDO`).
- **Latido vencido (`SIN_LATIDO`)**: al iniciar cada ciclo, el consumidor y el correo registran el plazo en que prometen terminar (10 y 15 minutos como maximo) y, al terminar, el de su ciclo siguiente. Si ese plazo pasa con 2 minutos de margen (hilo caido, ciclo colgado o trabajador detenido con el servicio activo), `/api/health`, `Estado.ps1` y Auditoria y operacion lo informan, y un vigilante independiente lo anota una vez como ERROR en `logs\siger.log` (y otra al recuperarse). La pausa de TI no vence el latido. Nada se reinicia ni corrige solo: revisar el log y reiniciar el servicio (`Stop.ps1`, `Start.ps1`).
- `Estado.ps1 -Alerta` termina con codigo 2 si SIGER no esta sano o una integracion permanente esta `SIN_LATIDO`, `DEGRADADO` o `NO_DISPONIBLE`; sirve para una tarea programada de monitoreo de la cuenta de servicio.
- `/api/health` (solo localhost) incluye esos codigos, sin contadores ni datos.
- Auditoria y operacion (TI): ultimo ciclo y exito, incidente, ACK pendientes o bloqueados, estado del outbox y correos ambiguos con su resolucion. No muestra documentos ni contenidos.
- Un estado `DEGRADADO` persistente, ACK bloqueados o correos ambiguos requieren revision; no editar tablas ni borrar leases para limpiarlos.

### Liberacion segura de originales

Todo original (ingreso manual, documento de gestion u original descargado del Formulario) entra en cuarentena y no se descarga. En una instancia institucional **solo sale de cuarentena tras un analisis antivirus hecho por el servidor**:

1. Configurar el motor en `config\antivirus.json` y reiniciar SIGER:
   - ClamAV (mismos limites que SMR): `{"engine":"CLAMAV","executable":"D:\\Herramientas\\clamav\\clamscan.exe","database":"D:\\Herramientas\\clamav\\db"}`. Las bases deben actualizarse (freshclam) desde una ubicacion aprobada.
   - Microsoft Defender: `{"engine":"DEFENDER","executable":"C:\\Program Files\\Windows Defender\\MpCmdRun.exe"}`. Exige el motor y la proteccion activos (`Get-MpComputerStatus`); en el equipo de preparacion estaba desactivado y SIGER lo rechazo.
2. TI, en Auditoria y operacion, pulsa **Analizar**: se verifica el SHA-256 custodiado, que el motor este operativo y con firmas de menos de 72 horas, se analiza el archivo con orden fija y tiempo maximo, y se vuelve a verificar el SHA-256. La evidencia queda en `document_scan` y en la auditoria. Analizar no libera.
3. **Liberar** solo procede si el ultimo analisis es LIMPIO, de los mismos bytes, del motor vigente y de la ultima hora, nunca hubo una deteccion y el archivo vuelve a coincidir con su SHA-256. Cualquier duda (error, firmas antiguas, motor inactivo, archivo alterado o retirado por el antivirus) deja el original en cuarentena. Una deteccion es definitiva.
4. Sin motor configurado no hay liberacion de originales reales. La revision manual sin motor existe solo en instancias de pruebas.

La descarga posterior sigue autorizada por la ficha y se entrega como adjunto con `Content-Security-Policy: sandbox`; el antivirus no sustituye el criterio de quien abre el archivo.

### Pruebas de arranque, parada y recuperacion de los servicios

Despues de registrar los servicios, en una ventana sin usuarios y **como la cuenta de servicio**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Test-Service.ps1' -Root 'D:\SIGER-A' -VentanaAutorizada -IncluirMySql
```

Comprueba arranque y salud (esquema 10), parada ordenada (registro de parada, menos de 180 s, sin proceso residual y lease del Formulario libre), recuperacion automatica tras terminar Java a la fuerza (nuevo proceso sano y evento de caida) y, con `-IncluirMySql`, la detencion y recuperacion junto al motor propio. Deja el resultado en `logs\prueba-servicio-<fecha>.json` y termina con error si algun paso falla. El reinicio del equipo se prueba aparte (reiniciar en ventana autorizada y luego `Estado.ps1`). El host del servicio tambien tiene un modo de prueba en consola (`service\SigerService.exe --probar <raiz> <segundos> [plazo_ms]`) que no requiere registrar servicios.

### Procedimiento de sustitucion del consumidor de SMR

1. **Preparar sin consumir**: respaldo (`Backup.ps1`), actualizacion y migracion a V9 (`Update.ps1`), `folioPrefix` compatible y distinto del de SMR en `instance.json`, importacion de credenciales (`Import-Integrations.ps1`) y `formulario-pull.json` en `"mode":"MANUAL"`. Registrar los servicios si corresponde. SIGER sigue sin consumir.
2. **Ventana autorizada**: acordar con el responsable del Formulario y con quien opera SMR una ventana sin usuarios. Pausar o cerrar el ingreso publico solo a traves de su responsable.
3. **Drenar SMR**: pausar el consumidor automatico de SMR con su control auditado (`Windows-Control-Consumidor-QA.ps1 -Accion pausar` o el equivalente de su instalacion), esperar `PAUSADO_POR_CONTROL` y comprobar **cero ACK pendientes** en SMR. Con ACK pendientes en SMR no se continua: los concilia SMR, no SIGER.
4. **Detener el consumidor de SMR** con autorizacion y comprobar que no queda proceso ni tarea que reclame la cola. La recepcion historica y la consulta de SMR pueden seguir disponibles; solo se detiene su consumo.
5. **Activar SIGER**: en `formulario-pull.json` poner `"mode":"AUTOMATICO"` y `"consumidorUnicoConfirmado": true`, y reiniciar SIGER (`Stop.ps1` y `Start.ps1`). Comprobar con `Estado.ps1` que el Formulario pasa a `ESPERANDO_ENTREGAS` y, en Auditoria y operacion, sondeos correctos sin incidentes.
6. **Primera entrega**: con autorizacion, una entrega real controlada; verificar COM unica, folio, originales con SHA-256, comprobante, ACK confirmado (`formulario_reception` en `CONFIRMADA`) y estado CONFIRMADA en el Formulario. Despues reabrir el ingreso publico.
7. **Reversion** (si fuera necesaria): pausar el consumidor de SIGER (TI) y esperar a que termine su ciclo; comprobar cero ACK pendientes en SIGER (`ACK_PENDIENTE` y `ACK_BLOQUEADO` en cero); volver `formulario-pull.json` a `"mode":"MANUAL"` y reiniciar SIGER; solo entonces reactivar el consumidor de SMR. Nunca hay dos consumidores activos, y no se borran COM, originales, recepciones ni leases para revertir.
