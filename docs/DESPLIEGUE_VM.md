# Despliegue de SIGER en la VM institucional

Guia corta para levantar la validacion, cerrar el piloto y habilitar produccion con el mismo release. Cada comando es
una linea de PowerShell. `FECHA` es el sufijo del ZIP (`SIGER-FECHA.zip`). El detalle tecnico de cada script esta en
`docs/INSTALACION_VM.md`.

## Datos que debe entregar TI

1. **Cuenta de servicio de Windows** para SIGER (por ejemplo `DOMINIO\svc-siger`), **sin privilegios administrativos**, con su contrasena, inicio de sesion local durante la instalacion (para `runas`) y el derecho "Iniciar sesion como servicio" (o autorizacion para que `Install-Service.ps1 -ConcederInicioComoServicio` lo agregue en la directiva local).
2. **Carpetas vacias** `D:\SIGER-VALIDACION` y, despues, `D:\SIGER`, con control total para esa cuenta.
3. **MySQL 8.4 Community ZIP (noinstall) para Windows x64**, extraido en `D:\Herramientas\mysql-8.4` (y el runtime Visual C++ si `mysqld --version` falla).
4. **`estructura-inicial.json`** con los ambitos reales, la persona TI y los funcionarios con sus funciones (sin autoridad habilitante desde 0.5.0) (plantilla en `config-ejemplo\`).
5. **Codigo del ambito** que recibe las Comunicaciones del Formulario (por ejemplo `UPR-REQ`; debe estar en la estructura).
6. **Graph (SIGER-Notificaciones)**: Tenant ID, Client ID, valor vigente del secreto de cliente y permiso `Mail.Send` limitado al buzon `siger@defensorianinez.cl`.
7. **Red de salida** desde la VM: `formulario.defensorianinez.gob.cl:443`, `login.microsoftonline.com:443` y `graph.microsoft.com:443`.
8. **Acceso de funcionarios** (si no entraran por RDP a la VM): regla de firewall entrante al puerto 18543 limitada a sus direcciones y, de preferencia, certificado HTTPS institucional (PFX) para el nombre de la VM.
9. **Ventana autorizada** con los responsables del Formulario y de SMR para detener el consumidor de SMR (sustitucion).
10. Opcional: motor antivirus operativo (Defender activo o ClamAV) para liberar originales; sin el, los originales quedan en cuarentena.

## 1. Traslado de credenciales desde SMR (equipo de SMR, cuenta duena de SMR)

Copiar solo `scripts\Export-SecretPackage.ps1` del ZIP a ese equipo. Pide el Tenant ID, el Client ID y el secreto de Graph, y una frase de paso (al menos 20 caracteres) que se entrega por otro canal.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'C:\Traslado\Export-SecretPackage.ps1' -SmrRoot 'C:\req\smr' -Destination 'C:\Traslado\siger-secretos.sigerpkg' -IncluirGraph
```

Llevar `siger-secretos.sigerpkg` a la VM por un medio controlado y comprobar ahi el SHA-256 que imprimio el script. El paquete va cifrado y no es el ZIP: el ZIP nunca contiene secretos.

## 2. Levantar la validacion institucional

Como administrador:

```powershell
Expand-Archive -LiteralPath 'D:\Entregas\SIGER-FECHA.zip' -DestinationPath 'D:\Entregas'
```
```powershell
icacls 'D:\SIGER-VALIDACION' /grant 'DOMINIO\svc-siger:(OI)(CI)F'
```

Como la cuenta de servicio (una consola abierta con `runas /user:DOMINIO\svc-siger powershell.exe`):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Init-Server.ps1' -Mode Institucional -Proposito Validacion -EstructuraInicial 'D:\Entregas\estructura-inicial.json' -Root 'D:\SIGER-VALIDACION' -Release 'D:\Entregas\SIGER-FECHA' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13326 -WebPort 18543 -AccesoRed
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Import-SecretPackage.ps1' -Root 'D:\SIGER-VALIDACION' -Package 'D:\Traslado\siger-secretos.sigerpkg' -Ambito 'UPR-REQ' -EliminarPaquete
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Stop.ps1' -Root 'D:\SIGER-VALIDACION' -AlsoMySql
```

Como administrador (registra los servicios con la cuenta de servicio y recuperacion automatica; no los inicia):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Install-Service.ps1' -Root 'D:\SIGER-VALIDACION' -ServiceCredential (Get-Credential 'DOMINIO\svc-siger') -ConcederInicioComoServicio
```

Como la cuenta de servicio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Start.ps1' -Root 'D:\SIGER-VALIDACION'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Test-Service.ps1' -Root 'D:\SIGER-VALIDACION' -VentanaAutorizada -IncluirMySql
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Invoke-FormularioPull.ps1' -Root 'D:\SIGER-VALIDACION' -SoloComprobar
```

La ultima orden solo comprueba configuracion, DNS, TLS con la CA de SMR y el Bearer (tres `HEAD` sin reclamar ni confirmar nada). La validacion **nunca** recibe entregas reales: el ciclo y el modo automatico se rechazan fuera de produccion.

Entregar a cada persona su clave temporal de `D:\SIGER-VALIDACION\data\bootstrap-usuarios.csv` por un canal seguro y luego retirar el archivo. La pantalla muestra "Validacion institucional · Datos ficticios"; los funcionarios prueban todos los flujos con datos ficticios en `https://<nombre-de-la-VM>:18543`. El correo queda desactivado.

## 3. Cerrar el piloto

Como la cuenta de servicio (comprueba que no haya ninguna entrega del Formulario, detiene y marca cerrada la instancia; no borra nada):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Close-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -Motivo 'Fin del piloto de validacion'
```

Como administrador:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Uninstall-Service.ps1' -Root 'D:\SIGER-VALIDACION'
```

`D:\SIGER-VALIDACION` se conserva como evidencia del piloto y no vuelve a iniciarse.

## 4. Habilitar produccion (instancia limpia, mismo release)

Como administrador:

```powershell
icacls 'D:\SIGER' /grant 'DOMINIO\svc-siger:(OI)(CI)F'
```

Como la cuenta de servicio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Init-Server.ps1' -Mode Institucional -Proposito Produccion -EstructuraInicial 'D:\Entregas\estructura-inicial.json' -Root 'D:\SIGER' -Release 'D:\Entregas\SIGER-FECHA' -MismoReleaseQue 'D:\SIGER-VALIDACION' -MySqlHome 'D:\Herramientas\mysql-8.4' -MySqlPort 13326 -WebPort 18543 -AccesoRed
```
```powershell
Copy-Item -Path 'D:\SIGER-VALIDACION\config\formulario-*','D:\SIGER-VALIDACION\config\graph-client-secret.clixml','D:\SIGER-VALIDACION\config\correo-graph.json' -Destination 'D:\SIGER\config'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Stop.ps1' -Root 'D:\SIGER' -AlsoMySql
```

La copia reutiliza las credenciales ya protegidas con DPAPI de la misma cuenta en la misma VM. Si la cuenta cambio, repetir el paso 1 e importar con `Import-SecretPackage.ps1 -Root 'D:\SIGER'`. Los folios parten en `SIGER-000001`.

Como administrador:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Install-Service.ps1' -Root 'D:\SIGER' -ServiceCredential (Get-Credential 'DOMINIO\svc-siger') -ConcederInicioComoServicio
```

Como la cuenta de servicio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Start.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Invoke-FormularioPull.ps1' -Root 'D:\SIGER' -SoloComprobar
```

### Sustitucion de SMR (ventana autorizada)

1. El responsable de SMR pausa su consumidor, espera a que no queden ACK pendientes en SMR y lo detiene (servicio detenido y deshabilitado si esta en esta VM).
2. Activar SIGER (exige produccion, una sonda acreditada de menos de 24 horas y la declaracion de que SMR ya no consume):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-Integration.ps1' -Root 'D:\SIGER' -Formulario Automatico -SmrDetenidoConfirmado
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Stop.ps1' -Root 'D:\SIGER'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Start.ps1' -Root 'D:\SIGER'
```

`Estado.ps1` debe mostrar el Formulario en `ESPERANDO_ENTREGAS`. Toda entrega queda registrada, custodiada y confirmada por ACK; ninguna se descarta. Para volver atras: `Set-Integration.ps1 -Formulario Manual`, reiniciar SIGER y, solo con cero ACK pendientes en SIGER, reactivar SMR.

### Correo (cuando se autorice)

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-Integration.ps1' -Root 'D:\SIGER' -Correo Graph
```

Reiniciar SIGER. Solo los avisos nuevos generan correo, desde `siger@defensorianinez.cl` y para destinatarios `@defensorianinez.cl`.

## Actualizacion a 0.5.0: inventario de Coordinaciones regionales (antes de actualizar)

Desde 0.5.0 la Coordinacion regional se registra como `DIRECCION` de su sede (D7). Al primer arranque con 0.5.0, una rutina auditada (`sistema:actualizacion`, marcador `COORDINACION_REGIONAL_050`) convierte cada `JEFATURA` vigente o programada en una sede. Si la sede ya tiene una `DIRECCION` superpuesta, esa `JEFATURA` no se convierte y Administracion muestra el aviso `JEFATURA_EN_SEDE`. Para saber de antemano que hara la rutina, ejecuta en la instancia 0.4.0 (esquema V10) esta consulta de solo lectura con el cliente `mysql` de la VM, sobre el esquema `siger_a_*` de la instancia. No modifica nada.

```sql
-- 1) JEFATURA vigentes o programadas en sedes regionales: se convertiran en DIRECCION con el mismo periodo.
--    superpuesta=1: hay una DIRECCION que se superpone en la misma sede; no se convertira (aviso JEFATURA_EN_SEDE).
SELECT s.code AS sede, u.username, g.id AS concesion, FROM_UNIXTIME(g.valid_from/1000) AS desde,
       IF(g.valid_until IS NULL,NULL,FROM_UNIXTIME(g.valid_until/1000)) AS hasta,
       EXISTS(SELECT 1 FROM role_assignment d WHERE d.scope_id=g.scope_id AND d.role_code='DIRECCION' AND d.revoked_at IS NULL
              AND d.valid_from<COALESCE(g.valid_until,9223372036854775807) AND (d.valid_until IS NULL OR d.valid_until>g.valid_from)) AS superpuesta
FROM role_assignment g JOIN org_scope s ON s.id=g.scope_id JOIN staff u ON u.id=g.user_id
WHERE g.role_code='JEFATURA' AND s.regional=TRUE AND g.revoked_at IS NULL
  AND (g.valid_until IS NULL OR g.valid_until>UNIX_TIMESTAMP()*1000)
ORDER BY s.code, g.id;
-- 2) Subrogancias vivas (no terminadas ni vencidas) sobre esas concesiones: pasaran a la nueva DIRECCION.
SELECT s.code AS sede, x.id AS subrogancia, t.username AS titular, b.username AS subrogante,
       FROM_UNIXTIME(x.from_ms/1000) AS desde, FROM_UNIXTIME(x.until_ms/1000) AS hasta
FROM substitution_scope ss JOIN substitution x ON x.id=ss.substitution_id JOIN substitution_designation d ON d.id=x.designation_id
JOIN role_assignment g ON g.id=ss.grant_id JOIN org_scope s ON s.id=g.scope_id
JOIN staff t ON t.id=d.titular_id JOIN staff b ON b.id=d.substitute_id
WHERE g.role_code='JEFATURA' AND s.regional=TRUE AND g.revoked_at IS NULL AND x.ended_ms IS NULL AND x.until_ms>UNIX_TIMESTAMP()*1000
ORDER BY s.code, x.id;
-- 3) Sedes existentes: quedaran ACTIVA (o CERRADA si estan deshabilitadas) y sin region; TI la registra despues.
SELECT code, name, site_code, enabled FROM org_scope WHERE regional=TRUE ORDER BY code;
```

Despues de actualizar, TI registra la region de cada sede (comando `SCOPE_UPDATE`; panel Estructura desde R03) y resuelve en el perfil de cada cuenta las `JEFATURA` que hayan quedado sin convertir.
