# Instalacion aislada en DDN-SIGER

## Condicion de entrada

Compilar primero en el PC. No ejecutar esta instalacion sobre un paquete cuyo build haya fallado. Los scripts estan preparados para Windows PowerShell 5.1, pero **no se ejecutaron en Windows durante la preparacion de esta entrega**: revisaremos juntos la primera ejecucion.

Usar solo datos sinteticos. La VM tambien aloja sistemas existentes: el aislamiento de puertos/carpetas evita compartir instalacion, pero no elimina la competencia por CPU, RAM o disco. El inventario aportado indica 16 GB totales y alrededor de 7 GB libres en ese momento. El launcher limita Java a 1 GB y configura el buffer MySQL a 256 MB; esto no equivale a un limite total del consumo del motor.

## Lo que se copia a D:

1. El ZIP de release generado por `Build.ps1 -IncludeJava` y extraido, por ejemplo en `D:\Entregas\SIGER-A-<fecha>`.
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

- Sin el archivo, SIGER A rechaza todo original externo (comportamiento por defecto).
- Un archivo presente pero invalido detiene el arranque: corregirlo o retirarlo.
- Quien pueda escribir el archivo puede agregar una clave confiable: protegerlo como un secreto de integridad. Retirar una clave exige reiniciar y no invalida originales ya conservados.
- Este archivo no activa ningun consumidor PULL/ACK ni conexion con SMR.

### Consumidor del Formulario Publico (PULL/ACK, inactivo por defecto)

El servicio nunca consume la cola del Formulario: no hay tarea programada, endpoint ni comando de funcionario para hacerlo. Un ciclo (concilia ACK pendientes, reclama a lo mas una entrega, la valida, descarga y custodia sus originales con SHA-256, registra COM + originales + comprobante en un COMMIT y recien entonces confirma) se ejecuta solo a mano con `scripts\Invoke-FormularioPull.ps1`, en un proceso aparte sin servidor web ni tareas, que termina al acabar el ciclo. No crea SOL, REQ ni Caso.

Archivos en `config\` (fuera del repositorio, con la ACL privada de la raiz; el script rechaza permisos mas amplios en la privada y en la credencial):

| Archivo | Contenido |
|---|---|
| `formulario-trusted-keys.json` | Clave publica de custodia del Formulario (ver arriba). |
| `formulario-pull.json` | `{"origin":"https://formulario.defensorianinez.gob.cl","scope":"<codigo del ambito receptor>","ackKeyId":"<keyId del ACK>","caFile":""}`. `caFile` es opcional (certificado PEM de CA en `config\`); vacio usa la confianza del JDK. |
| `formulario-pull-token.clixml` | Bearer entregado privadamente, protegido con DPAPI de la cuenta que ejecuta el servicio: `Read-Host -AsSecureString \| Export-Clixml D:\SIGER-A\config\formulario-pull-token.clixml`. |
| `formulario-ack-private.pem` / `formulario-ack-public.pem` | Par Ed25519 del comprobante institucional, generado una vez con `scripts\New-FormularioAckKey.ps1 -Root D:\SIGER-A`. Solo la publica (con su keyId) se entrega al operador del Formulario. |

`instance.json` debe incluir `"folioPrefix"` compatible con el folio del comprobante (`[A-Z][A-Z0-9]{1,15}`, por ejemplo `COMA`): el prefijo por defecto `COM-A` no lo es y el consumidor no arranca con el. El prefijo aplica a todas las COM nuevas (manuales y externas) y requiere reiniciar el servicio; las COM existentes conservan su folio.

Reglas que el procedimiento no puede verificar por si solo:

- **Un solo consumidor por cola.** El Bearer da acceso a la misma cola que el consumidor del Formulario de SMR. Antes de cada ciclo debe estar detenido, con autorizacion, el consumidor de SMR; de lo contrario las entregas se reparten entre ambos sistemas. El script exige `-ConsumidorUnicoConfirmado`. El lease `formulario_consumer_lock` solo impide dos ciclos simultaneos de SIGER A.
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

## Paso 2. Inicializacion unica

Sustituir solo las rutas del release y de MySQL por las carpetas efectivamente extraidas. Cada orden es una linea:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-A-FECHA\scripts\Init-Server.ps1' -Root 'D:\SIGER-A' -Release 'D:\Entregas\SIGER-A-FECHA' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13326 -WebPort 18543
```

La orden crea la instancia y sus cuentas MySQL, TLS local, esquema `siger_a_lab`, migracion y datos sinteticos. No usa credenciales root del MySQL existente. Durante la inicializacion se utiliza un archivo privado temporal de instrucciones SQL y luego se elimina; el datadir no se expone a red externa. No se instala un servicio Windows ni se cambia PATH, JAVA_HOME, firewall o confianza global.

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

Con SIGER A parado y su motor propio disponible:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Backup.ps1' -Root 'D:\SIGER-A' -Destination 'D:\Respaldos-SIGER-A\PRUEBA-001'
```

La carpeta destino debe ser nueva. Incluye SQL, originales, configuracion privada y hashes. **No esta cifrada como archivo portable**: esta protegida por ACL y contiene informacion privada. El dump puede incluir hashes de contrasenas. DPAPI requiere la identidad Windows original. No se acredita respaldo externo, restauracion o RPO/RTO por crear esta copia.

Para actualizar, despues de compilar una nueva version y hacer respaldo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-A\scripts\Update.ps1' -Root 'D:\SIGER-A' -Release 'D:\Entregas\SIGER-A-NUEVA' -BackupDirectory 'D:\Respaldos-SIGER-A\PRUEBA-001'
```

La version anterior se conserva. Si una migracion falla, no arrancar un JAR anterior a ciegas. Reversion de datos/esquema y ensayo de restauracion quedan pendientes de calificacion manual. No se incluyen comandos destructivos automatizados.

## Complemento operativo 0.3.1

Las ordenes anteriores de instalacion siguen requiriendo build valido y datos sinteticos. La 0.3.1 no instala software de forma remota; son herramientas que ejecuta el operador bajo la misma identidad Windows.

### Comprobar arranque

`Start.ps1` ya no declara exito por crear el PID: consulta HTTPS local con el certificado propio fijado y exige version del manifiesto y esquema compatible (V4 o posterior). Si falla, conserva logs y devuelve error. `Estado.ps1` permite repetir el diagnostico. No deshabilita TLS ni cambia el almacen global. No usarlo como comprobacion de acceso LAN: solo verifica localhost.

### Respaldo y ensayo de restauracion

Con la aplicacion detenida y solo el MySQL propio activo, generar respaldo en carpeta nueva. La copia incluye SQL, documentos, configuracion privada, conteos por tabla y manifiesto de hashes. Si tu VM ya tenia scripts viejos, ejecutar Backup desde el nuevo release compilado, usando su parametro Root; no hace falta migrar primero para respaldar.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-A-FECHA\scripts\Backup.ps1' -Root 'D:\SIGER-A' -Destination 'D:\Respaldos-SIGER-A\PRUEBA-031'
```

Ensayar la copia en **carpeta nueva fuera de la instancia** y esquema nuevo generado automaticamente en el motor MySQL propio verificado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-A-FECHA\scripts\Restore-Check.ps1' -Root 'D:\SIGER-A' -BackupDirectory 'D:\Respaldos-SIGER-A\PRUEBA-031' -Destination 'D:\SIGER-A-RESTORE-PRUEBA-031'
```

Solo informa `completed=true` tras validar hashes de copia, importacion SQL, conteos y documentos restaurados. No arranca otra aplicacion, no cambia instance.json ni sobreescribe la base activa. Conserva carpeta/esquema del ensayo para revision; su eliminacion posterior es una operacion expresa, no automatica.

La restauracion comparte el motor dedicado y por tanto consume recursos de esa VM; hacerla en una ventana acordada sin usuarios en SIGER A. No acredita recuperacion desde otro host ni descifrado DPAPI con otro usuario. No entregar dumps/configuracion a terceros.

### Actualizacion y recuperacion de error

El actualizador exige respaldo completo 0.3.1 de la instancia/version activa y un release con pruebas no omitidas. Copia la candidata, conserva descriptor anterior, instala el bloqueo de arranque y migra con el JAR candidato **antes** de cambiar la referencia activa. Tras exito queda PENDING_START hasta comprobar salud.

MIGRATION_FAILED/PROMOTION_FAILED bloquean inicio ambiguo. START_FAILED permite reintentar la misma candidata tras diagnostico. No borrar el diario, hacer repair o cambiar al JAR anterior para forzar verde: una migracion parcialmente aplicada puede no ser reversible. Conservar respaldo, descriptor previo, error y logs; recuperar sobre otra copia o corregir hacia delante con evidencia. La copia previa no equivale a un rollback automatico.

Los scripts siguen sin ejecutarse aqui sobre Windows/MySQL. Su primera validacion es parte de la prueba guiada, no una evidencia ya obtenida.
