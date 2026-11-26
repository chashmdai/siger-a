# Instalacion aislada en DDN-SIGER

> Salida a produccion con 0.6.0 (datos de TI, cierre de la validacion 0.4.0, produccion nueva, acreditacion, eliminacion de la validacion y plan B): `docs/DESPLIEGUE_VM.md`; recorridos en `docs/PRUEBAS_MANUALES_0_6_0.md`.
> Paquete 0.7.0 (cobertura territorial de las sedes y region de origen, esquema V16): solo en V4 y solo segun el corte del 07/12 (`docs/PLAN_0_7_0.md` seccion 3); si no, V4 usa 0.6.0. Recorridos en `docs/PRUEBAS_MANUALES_0_7_0.md`.
> Instancias institucionales: `Init-Server.ps1 -Mode Institucional -Proposito Validacion|Produccion` (la validacion solo recibe entregas con
> `"validacionPullAutorizada": true`, ver `DESPLIEGUE_VM.md`; `-MismoReleaseQue` exige el mismo JAR validado y no se usa en 0.6.0, porque la validacion sigue en 0.4.0; `-AccesoRed` escucha en todas las interfaces).
> Credenciales en 0.6.0: la configuracion del Formulario se copia de la validacion a produccion (misma cuenta de servicio, DPAPI) y Graph se instala con `Import-GraphCredential.ps1`;
> `Export-SecretPackage.ps1` lee SMR y no se usa (D15). Sonda sin reclamar: `Invoke-FormularioPull.ps1 -SoloComprobar`. Activacion: `Set-Integration.ps1`.
> Cierre de la validacion: `Close-Validation.ps1 -RecepcionesDeclaradas` (D11). Eliminacion con constancia: `Remove-Validation.ps1` (D12). Modo de emergencia D17: `Set-OriginRelease.ps1`.

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

**Restablecer la contrasena de la cuenta de servicio** (no cambiarla con la anterior) deja sin descifrar todo lo protegido con su DPAPI. Antes se exporta la configuracion a un paquete portable (0.6.0, H06r; paso P0 de `docs/DESPLIEGUE_VM.md`): `Export-InstanceConfig.ps1`, como la cuenta duena (`ownerSid`), cifra con una frase de paso escrita dos veces todo `config\` (cada `*.clixml` descifrado solo en memoria; los `.p12` de nombre conocido deben abrir con su clave de `secrets.clixml`; otro `.p12` viaja tal cual, sin abrirse, y uno con el nombre de un conocido salvo mayusculas impide exportar; H06r2) y `mysql\my.ini`, con el SHA-256 de cada archivo y la huella de cada secreto (formato `SIGER-CONFIG-1`, `.sigerpkg`; `ConfigPackage.ps1`); no sobrescribe y admite `-WhatIf`. `Test-InstanceConfig.ps1`, como otra cuenta y solo con la frase, lo verifica en memoria y sin escribir ni cambiar permisos: integridad, huellas, cada archivo contra el disco, los `.p12` de nombre conocido (certificados y vencimiento; uno sin clave conocida solo por SHA-256, informado como `SIN_CLAVE_CONOCIDA`), la identidad ACK (`AckKeyCheck.java` con el Java de la instancia) y un `SELECT 1` con cada cuenta de MySQL (contrasena en `MYSQL_PWD` del proceso hijo). Despues del restablecimiento (0.6.0, H06s; paso V0 de `docs/DESPLIEGUE_VM.md`): `Import-InstanceConfig.ps1`, como la cuenta duena en una sesion abierta despues del restablecimiento y con la instancia detenida, verifica el paquete (integridad, contenido, cuenta y raiz), vuelve a proteger SIEMPRE cada `*.clixml` con la DPAPI de esa sesion y con ACL privada, restaura por SHA-256 los demas archivos que falten o difieran (un `.p12` sin clave conocida solo como bytes) y conserva los anteriores en `config\rotacion-<UTC>\anterior\` con `importacion.json`, sin borrar nada; al terminar relee todo con la DPAPI y lo compara con el paquete. Si la sesion ya descifra los clixml del disco (al repetir la importacion), lo informa en una linea, sin pedir nada. `Set-ServicePassword.ps1` (administrador, cuadro de credenciales) actualiza la contrasena guardada de `SIGER` y `SIGERMySQL` sin reinstalarlos, despues de comprobar como la cuenta, en un inicio de sesion nuevo, que la contrasena es valida y que descifra todos los clixml (`Test-ServiceIdentity.ps1`, la misma comprobacion de `Install-Service.ps1`); no pide confirmacion y se puede repetir.

## Paso 2. Inicializacion unica

Sustituir solo las rutas del release y de MySQL por las carpetas efectivamente extraidas. Cada orden es una linea.

**Institucional** (sin cuentas, sedes ni datos de prueba), ejecutada **como la cuenta de servicio**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Init-Server.ps1' -Mode Institucional -EstructuraInicial 'D:\Entregas\estructura-inicial.json' -Root 'D:\SIGER-A' -Release 'D:\Entregas\SIGER-FECHA' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13326 -WebPort 18543
```

`estructura-inicial.json` lo prepara la institucion (dato real; no se versiona):

```json
{"fundamento":"Resolucion o acto que designa a TI y a los funcionarios iniciales",
 "ambitos":[{"codigo":"UGI","nombre":"Gestion Institucional","unidad":"UGI","sede":"CENTRAL"},
            {"codigo":"UPR-REQ","nombre":"Requerimientos / Atencion de Personas","unidad":"UPR","area":"REQ","sede":"CENTRAL","regional":false}],
 "ti":{"usuario":"nombre.apellido","nombre":"Nombre de la persona TI","correo":"persona@defensorianinez.cl","ambito":"UGI"},
 "funcionarios":[{"usuario":"nombre.distribucion","nombre":"Nombre Apellido","ambito":"UPR-REQ",
                  "funciones":[{"funcion":"DISTRIBUIDOR"},{"funcion":"ADMINISTRATIVO","ambito":"UPR-REQ"},{"funcion":"PROFESIONAL","ambito":"UPR-REQ"}]}]}
```

- `unidad` es uno de los codigos del producto: UPR, UPD, UEP, UGI, REG, DAI, GAB, DEF, AUD.
- `funcionarios` es opcional. Funciones por ambito (con `ambito`): ADMINISTRATIVO, PROFESIONAL, JEFATURA, DIRECCION, LECTURA, AUDITORIA; institucionales (sin `ambito`): DISTRIBUIDOR y TI. Una persona combina funciones; se admiten varios distribuidores; una jefatura o direccion por ambito; TI va sola (no se combina con ninguna otra funcion).
- Desde 0.5.0 TI administra el sistema (cuentas, funciones y subrogancias) sin acceso a expedientes. **No existe la autoridad habilitante**: un archivo con la entrada `autoridad` (preparado para 0.4.0) detiene la inicializacion con el mensaje "desde 0.5.0 TI administra funciones; retira la entrada autoridad".
- Cada cuenta recibe clave temporal individual en `data\bootstrap-usuarios.csv` con cambio obligatorio: entregarlas por canal seguro y retirar el archivo.
- Desde 0.7.0 (D18-D19), opcionales: `"cobertura":["03"]` en una sede regional (regiones que atiende ademas de su region fisica; sin repetir, sin la region fisica, solo en sedes regionales; en una sede PROYECTADA rige al activarla) y `"coberturaNivelCentral":["13"]` en la raiz (regiones que atiende el nivel central; no agrega destinos al selector). Cada region queda como un periodo a nombre de la TI inicial, con el fundamento del archivo; despues TI la administra en Estructura. 0.6.0 ignora ambas claves sin aviso. Reglas y ejemplo en `config-ejemplo\`.
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

La version anterior se conserva. Si una migracion falla, no arrancar un JAR anterior a ciegas. Reversion de datos/esquema y ensayo de restauracion quedan pendientes de calificacion manual. Ningun script borra datos de una instancia, salvo `Remove-Validation.ps1`, que solo elimina una validacion cerrada, con constancia (D12; excepcion registrada en la Precision 0.6.0 de `docs/DECISIONES_FUNCIONALES.md`).

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

### Actualizar a 0.5.0

**Referencia; no se ejecuta**: la validacion de la VM no se actualiza (se cierra en 0.4.0) y produccion se instala con 0.6.0 (D14 de `docs/PLAN_0_6_0.md`). La secuencia completa (consultas previas, respaldo, ensayo de restauracion obligatorio con `Restore-Check.ps1`, `Update.ps1`, cifras a revisar y tareas de TI posteriores) esta en `docs/ACTUALIZACION_0_5_0.md`, con las consultas previas de solo lectura en su anexo. Desde 0.5.0 el ingreso pasa el usuario a minusculas, el CSV de las bandejas exige la funcion `EXPORTACION` y las descargas llevan un nombre neutro (`<FOLIO>-documento-<id>.<ext>`).

### Actualizar a 0.8.0

La actualizacion de produccion de 0.7.0 a 0.8.0 es el paso U de `docs/DESPLIEGUE_VM.md` (seccion 5): requisitos (DI-11,
ventana sin usuarios y margen antes del 15/12), consultas previas de solo lectura, respaldo y `Restore-Check.ps1`
inmediatamente antes de `Update.ps1`, criterio de detencion ante `MIGRATION_FAILED`, `PROMOTION_FAILED` o
`START_FAILED`, verificacion posterior (marcadores, originales del Formulario y motor estadistico) y activacion de los
catalogos por TI. Los scripts son los mismos de 0.6.0 y 0.7.0. Recorridos de 0.8.0 en `docs/PRUEBAS_MANUALES_0_8_0.md`.

### Supresion de AUTORIDAD al actualizar a 0.5.0

Al primer arranque de 0.5.0 (tambien con `Update.ps1`, que migra con el JAR candidato) una rutina revoca las funciones AUTORIDAD vigentes o programadas. No borra filas: conserva la historia y registra un evento `FUNCION_REVOCADA` por cada una, con el fundamento "Rol suprimido en 0.5.0 (D1)", a nombre de la identidad tecnica `sistema:actualizacion` (sin sesion, sin funciones y fuera de las listas). Se aplica una sola vez (marcador `ROL_AUTORIDAD_SUPRIMIDO_050`). La cuenta que tenia la autoridad sigue existiendo, sin esa funcion.

La rutina no quita funciones a las cuentas que combinen TI con otras: quedan en Administracion como "Incompatibilidades por resolver". En cada arranque, si no hay una TI permanente o hay cuentas incompatibles, el log del servicio registra un aviso `Administracion: SIN_TI_PERMANENTE` o `Administracion: TI_INCOMPATIBLE` (solo codigo y cantidad, sin nombres), y Administracion muestra el mismo aviso.

### Recuperacion cuando no queda ninguna TI vigente

SIGER impide por la interfaz quedar sin una cuenta TI habilitada, vigente y sin fecha de termino. Si aun asi ocurre (por ejemplo, la unica TI se jubila o su funcion vencio), se recupera con `Recover-Ti.ps1`, respaldado por un acto institucional:

1. Obtener la resolucion o memo que designa a la persona TI. Hacer un respaldo (`Backup.ps1`).
2. Detener SIGER (`Stop.ps1`, sin `-AlsoMySql`): el MySQL propio debe seguir activo.
3. Ejecutar **como la cuenta de servicio**. Para una cuenta existente (se habilita, recibe clave temporal y conserva o recibe TI permanente; no debe tener otras funciones vigentes):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Recover-Ti.ps1' -Root 'D:\SIGER-A' -Usuario 'nombre.apellido' -Fundamento 'Resolucion exenta N ... que designa TI'
```

   Para una cuenta nueva, agregar `-Nombre 'Nombre Apellido' -Ambito 'UGI'` (codigo de un ambito habilitado).
4. La clave temporal queda solo en `data\recuperacion-ti-<marca>.csv`: entregarla por canal seguro y retirar el archivo. Iniciar SIGER (`Start.ps1` o el servicio); la persona cambia la clave al ingresar.

La recuperacion se rechaza sin cambios si queda una TI permanente (la administracion se hace por la interfaz), si la cuenta tiene otras funciones (TI va sola), si falta el fundamento o si SIGER esta en ejecucion. Queda auditada a nombre de `sistema:actualizacion` (`TI_RECUPERADA`, y segun el caso `CUENTA_CREADA` o `CUENTA_ACTUALIZADA`, `CLAVE_TEMPORAL_REPUESTA`, `FUNCION_REVOCADA` y `FUNCION_OTORGADA`), con el fundamento. El detalle queda en `logs\recuperacion-ti-*.log`, sin la clave.

## Operacion permanente 24/7 (V9-V10)

Esta seccion prepara SIGER para operar como servicio permanente. Nada de lo descrito se activa solo: cada paso es una orden explicita del operador. El esquema subio a V10 en 0.4.0 y a V14 en 0.5.0 (`Migrate.ps1` con respaldo previo). `Start.ps1` exige la version y el esquema que declara el manifiesto del release (`Build.ps1` los toma de `backend/pom.xml` y de las migraciones).

### Servicios de Windows (SIGER y su MySQL propio)

`Install-Service.ps1` lo ejecuta **un administrador** (PowerShell elevada) indicando la cuenta de servicio, y registra dos servicios **sin iniciarlos**, ambos con esa cuenta sin privilegios administrativos:

- `SIGERMySQL`: el motor propio de la instancia (`mysqld --install` con su `mysql\my.ini`), inicio automatico, ejecutado con la cuenta de servicio (duena del datadir).
- `SIGER`: un host propio (`service\SigerService.exe`, compilado en ese momento desde `scripts\SigerService.cs` con el compilador de .NET Framework de Windows; sin binarios de terceros) que ejecuta `scripts\Service.ps1`. Inicio automatico diferido, dependiente de `SIGERMySQL`, recuperacion con reinicio a los 30 s, 60 s y 5 min.

El script exige que la cuenta de servicio sea **distinta** del administrador que instala y **la misma que inicializo la instancia** (`ownerSid` en `instance.json`). Lanza `Test-ServiceIdentity.ps1` con esa cuenta y perfil cargado para comprobar que **no pertenece a Administradores** y que descifra la custodia DPAPI; si falla, no registra nada. Concede a la cuenta permiso para iniciar, detener y consultar solo esos dos servicios y enviarles el control de la prueba de caida (control definido por el usuario, CR); ese derecho queda solo en SYSTEM, Administradores y la cuenta (Windows lo da por defecto a usuarios interactivos y a cuentas de servicio, y el script se lo quita y comprueba el descriptor aplicado). El derecho "Iniciar sesion como servicio" se comprueba por SID (`secedit` escribe una cuenta local por su nombre) y, con `-ConcederInicioComoServicio`, se agrega en la directiva local; una directiva de dominio puede prevalecer y debe gestionarla TI. La contrasena pasa al sistema como objeto, nunca como argumento: la orden lleva solo el nombre de la cuenta y Windows PowerShell 5.1 la pide en el cuadro de credenciales de Windows, con la cuenta ya escrita (consola interactiva; con `-File`, un `(Get-Credential ...)` llegaria como texto). Windows registra una cuenta local como `.\cuenta`; los scripts la traducen como `EQUIPO\cuenta` para acreditar el proceso propio (0.6.0, H06c).

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Install-Service.ps1' -Root 'D:\SIGER-A' -ServiceCredential 'DOMINIO\svc-siger' -ConcederInicioComoServicio
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

#### Credencial y prueba controlada (0.6.0)

Procedimiento de 0.6.0 para instalar la credencial de Graph en una instancia existente (validacion o produccion), sin SMR, sin paquete de secretos y sin tocar la configuracion del Formulario. Todo se ejecuta **como la cuenta de servicio** (la de `ownerSid` en `instance.json`, duena de DPAPI); con otra cuenta los scripts se rechazan.

1. **Instalar la credencial.** Pide el Tenant ID y el Client ID por consola y el secreto como entrada privada; ninguno va como argumento ni se muestra. Valida el formato (GUID, remitente, secreto de 20 a 512 caracteres) y deja `config\graph-client-secret.clixml` (DPAPI) y `config\correo-graph.json` en `"mode":"DESACTIVADO"`: el servicio no envia correo. No sobrescribe: si ya existe cualquiera de los dos archivos, no escribe nada. No contacta Graph.

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Import-GraphCredential.ps1' -Root 'D:\SIGER-A'
   ```

   `-Remitente` (por defecto `siger@defensorianinez.cl`, el buzon con permiso `Mail.Send`) y `-DominiosPermitidos` (por defecto `defensorianinez.cl`) son opcionales.

2. **Prueba Token.** Un proceso de SIGER sin servidor web ni tareas programadas pide un token OAuth y **no envia correo**. Acredita que la credencial es valida y que la VM sale hacia `login.microsoftonline.com`. Requiere el MySQL propio activo; el servicio puede estar activo o detenido.

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Test-GraphMail.ps1' -Root 'D:\SIGER-A' -Modo Token
   ```

3. **Un solo Envio**, despues de un Token acreditado, al destinatario institucional fijo (de un dominio permitido). Envia un correo de asunto y texto fijos, sin datos de expedientes. Acredita ademas la salida hacia `graph.microsoft.com` y el permiso `Mail.Send` sobre el remitente. El script **pide confirmacion** antes de enviar (`-WhatIf` no envia). HTTP 202 significa aceptado por Graph, no entregado: confirmar con el destinatario que llego.

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Test-GraphMail.ps1' -Root 'D:\SIGER-A' -Modo Envio -Destinatario '<destinatario institucional fijo>'
   ```

La prueba no usa la cola de avisos (`mail_outbox`, `mail_attempt`), no escribe en la base y no cambia el modo del correo. Muestra solo modo, estado, fase, HTTP y request-id, y deja esa evidencia con la hora UTC y el SHA-256 del JAR en `run\graph-prueba.json` (sin destinatario, secreto, token, tenant ni cliente), tambien cuando falla. El detalle del arranque queda en `logs\graph-prueba-*.log`. Activar el correo es un paso aparte (`Set-Integration.ps1 -Correo Graph` y reinicio).

`Import-Integrations.ps1 -GraphSecretFile`/`-GraphSecretPrompt` se conserva, pero no se usa en esta secuencia: instala solo el secreto, sin `correo-graph.json`, y si se hubiera usado, `Import-GraphCredential.ps1` se rechaza porque el secreto ya existe.

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

### Clave de auditoria del buscador (opcional, 0.5.0)

El buscador de personas y folios audita cada consulta (evento `BUSQUEDA`) con el tipo de termino y la cantidad de resultados, nunca con el termino. Con la clave, agrega la huella HMAC-SHA256 del termino normalizado: con la misma clave se puede comprobar despues si un RUN, nombre o folio fue buscado. Sin la clave no hay huella. Se genera una sola vez con la cuenta del servicio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\New-AuditSearchKey.ps1' -Root 'D:\SIGER-A'
```

Queda en `config\audit-search-key.clixml` (DPAPI de esa cuenta). El servicio la lee al iniciar (variable de proceso `SIGER_AUDIT_SEARCH_KEY`, minimo 32 caracteres; una clave mas corta detiene el arranque). El script no la imprime ni la sobrescribe: si se reemplaza, las huellas anteriores dejan de poder compararse. No viaja en el paquete de secretos; produccion genera la suya.

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
   - **Defender con la cuenta de servicio (sin privilegios administrativos).** Verificado en el equipo de preparacion con un token restringido (mismo usuario, Administradores solo de denegacion, `runas /trustlevel:0x20000`): `Get-MpComputerStatus` es legible sin privilegios. El analisis no pudo acreditarse alli porque el motor esta desactivado: `MpCmdRun` fallo con `hr = 0x800106ba` igual que con el token administrador. **Ese fallo devuelve el codigo 2, el mismo de "amenaza encontrada"**, por eso SIGER no confia en el codigo: LIMPIO exige codigo 0 y "found no threats" para el archivo; INFECTADO exige codigo 2 y "found N threats" sin mensaje de fallo; todo lo demas es ERROR (sigue en cuarentena, sin marca de infectado). En la VM, `Install-Service.ps1` ejecuta `Test-ServiceIdentity.ps1` como la cuenta de servicio y, si `antivirus.json` usa Defender, comprueba con esa cuenta el estado y el analisis de un archivo limpio sintetico; informa `OPERATIVO`, `ESTADO_NO_LEGIBLE`, `MOTOR_INACTIVO` o `ANALISIS_FALLIDO_<hr>` y advierte si no es utilizable. No se elevan privilegios ni se cambian exclusiones o servicios de Defender: si la cuenta no puede analizar, TI decide (otra configuracion de Defender o ClamAV) y los originales permanecen en cuarentena.
2. TI, en Auditoria y operacion, pulsa **Analizar**: se verifica el SHA-256 custodiado, que el motor este operativo y con firmas de menos de 72 horas, se analiza el archivo con orden fija y tiempo maximo, y se vuelve a verificar el SHA-256. La evidencia queda en `document_scan` y en la auditoria. Analizar no libera.
3. **Liberar** solo procede si el ultimo analisis es LIMPIO, de los mismos bytes, del motor vigente y de la ultima hora, nunca hubo una deteccion y el archivo vuelve a coincidir con su SHA-256. Cualquier duda (error, firmas antiguas, motor inactivo, archivo alterado o retirado por el antivirus) deja el original en cuarentena. Solo una deteccion acreditada es definitiva: en ClamAV, una firma informada para ese archivo distinta de `Heuristics.Limits.Exceeded.*`; un limite excedido (`LIMITES_EXCEDIDOS`) o una salida sin resultado legible (`ANALISIS_INCOMPLETO`) quedan como ERROR y pueden volver a analizarse. La carpeta temporal de cada analisis se elimina siempre al terminar.
4. Sin motor configurado no hay liberacion de originales reales. La revision manual sin motor existe solo en instancias de pruebas.
5. **Modo de emergencia D17 (0.6.0, H04c): liberacion por analisis en origen.** Mientras la instancia no tenga un motor admitido, `scripts\Set-OriginRelease.ps1 -Root ... -Motivo ...` (como la cuenta de servicio, con confirmacion; sin fecha de termino desde H05: rige hasta que se configure un motor antivirus admitido o se desactive de forma expresa, y la revision periodica queda a cargo del responsable, segun el acta institucional) deja `config\liberacion-origen.json`, que solo reciben el servicio y el ciclo manual del Formulario. SIGER lo valida al arrancar (lista cerrada de campos de la version 2; ilegible, con otros campos, con un termino `until` o con `antivirus.json` falla cerrado y TI ve el aviso; con el modo vigente TI ve un aviso permanente) y audita cada cambio de estado (recurso SISTEMA 17). Con el modo vigente, cada original **del Formulario** se libera en la transaccion de recepcion despues de verificar su SHA-256 firmado (si no coincide, la entrega no se registra), con la auditoria `DOCUMENTO_LIBERADO_EN_ORIGEN` y el motivo "analisis en origen del Formulario"; no se escribe `document_scan`, asi que nunca figura como un analisis LIMPIO de un motor de la VM, y la ficha lo muestra como "Disponible · analisis en origen". Al arrancar con el modo vigente se liberan tambien, uno por transaccion, los originales del Formulario que quedaron en cuarentena mientras no regia (mismo SHA-256 y ningun analisis INFECTADO previo). Los adjuntos subidos en SIGER nunca se liberan por este modo. `-Desactivar` lo retira (renombra el archivo); ver `docs/DESPLIEGUE_VM.md` seccion 4.

La descarga posterior sigue autorizada por la ficha y se entrega como adjunto con `Content-Security-Policy: sandbox`; el antivirus no sustituye el criterio de quien abre el archivo.

### Pruebas de arranque, parada y recuperacion de los servicios

Despues de registrar los servicios, en una ventana sin usuarios y **como la cuenta de servicio**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Test-Service.ps1' -Root 'D:\SIGER-A' -VentanaAutorizada -IncluirMySql
```

Se niega con otra cuenta antes de descifrar. Comprueba arranque y salud (version y esquema del manifiesto; 0.7.0 y 16 en esta version), parada ordenada (registro de parada, menos de 180 s, sin proceso residual y lease del Formulario libre), recuperacion automatica tras una caida simulada con el control de servicio 200 (`SigerService.exe` termina su Job, PowerShell y Java, porque la cuenta de servicio no puede terminar el proceso del servicio con `Stop-Process`; nuevo proceso sano y eventos "caida simulada solicitada" y "termino inesperadamente") y, con `-IncluirMySql`, la detencion y recuperacion junto al motor propio. Deja el resultado en `logs\prueba-servicio-<fecha>.json` y termina con error si algun paso falla. El reinicio del equipo se prueba aparte (reiniciar en ventana autorizada y luego `Estado.ps1`). El host del servicio tambien tiene un modo de prueba en consola (`service\SigerService.exe --probar <raiz> <segundos> [plazo_ms]`) que no requiere registrar servicios.

### Procedimiento de sustitucion del consumidor de SMR

1. **Preparar sin consumir**: respaldo (`Backup.ps1`), actualizacion y migracion a V9 (`Update.ps1`), `folioPrefix` compatible y distinto del de SMR en `instance.json`, importacion de credenciales (`Import-Integrations.ps1`) y `formulario-pull.json` en `"mode":"MANUAL"`. Registrar los servicios si corresponde. SIGER sigue sin consumir.
2. **Ventana autorizada**: acordar con el responsable del Formulario y con quien opera SMR una ventana sin usuarios. Pausar o cerrar el ingreso publico solo a traves de su responsable.
3. **Drenar SMR**: pausar el consumidor automatico de SMR con su control auditado (`Windows-Control-Consumidor-QA.ps1 -Accion pausar` o el equivalente de su instalacion), esperar `PAUSADO_POR_CONTROL` y comprobar **cero ACK pendientes** en SMR. Con ACK pendientes en SMR no se continua: los concilia SMR, no SIGER.
4. **Detener el consumidor de SMR** con autorizacion y comprobar que no queda proceso ni tarea que reclame la cola. La recepcion historica y la consulta de SMR pueden seguir disponibles; solo se detiene su consumo.
5. **Activar SIGER**: en `formulario-pull.json` poner `"mode":"AUTOMATICO"` y `"consumidorUnicoConfirmado": true`, y reiniciar SIGER (`Stop.ps1` y `Start.ps1`). Comprobar con `Estado.ps1` que el Formulario pasa a `ESPERANDO_ENTREGAS` y, en Auditoria y operacion, sondeos correctos sin incidentes.
6. **Primera entrega**: con autorizacion, una entrega real controlada; verificar COM unica, folio, originales con SHA-256, comprobante, ACK confirmado (`formulario_reception` en `CONFIRMADA`) y estado CONFIRMADA en el Formulario. Despues reabrir el ingreso publico.
7. **Reversion** (si fuera necesaria): pausar el consumidor de SIGER (TI) y esperar a que termine su ciclo; comprobar cero ACK pendientes en SIGER (`ACK_PENDIENTE` y `ACK_BLOQUEADO` en cero); volver `formulario-pull.json` a `"mode":"MANUAL"` y reiniciar SIGER; solo entonces reactivar el consumidor de SMR. Nunca hay dos consumidores activos, y no se borran COM, originales, recepciones ni leases para revertir.
