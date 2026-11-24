# SIGER - fuente 0.7.0

SIGER (Defensoria de la Ninez) gestiona Comunicaciones, Solicitudes, Requerimientos y Casos. Es un monolito Java 25 / Spring Boot 4.1.1 con frontend React 19 / TypeScript empaquetado en el mismo JAR, base MySQL 8.4 propia en la VM y H2 para desarrollo y pruebas. Antes se llamaba SIGER A; los nombres internos (`siger-a.jar`, esquema `siger_a_*`, `D:\SIGER-A`) se conservan por compatibilidad.

## Estado real

- 0.7.0 agrega la cobertura territorial de las sedes y la region de origen de la persona (`docs/PLAN_0_7_0.md`, decisiones D18-D24; esquema V16, migraciones V15 y V16 aditivas): cada sede regional atiende su region fisica y, si TI lo declara, otras regiones; TI declara tambien las regiones del nivel central (por ejemplo, la Region Metropolitana); efecto territorial con confirmacion al cerrar una sede o cambiar su region o cobertura; aviso `REGION_SIN_SEDE`; selector de destino con la cobertura; region de origen registrada, vigente y corregida en reportes, exportacion y ficha; feriados sin cambio de regla; marca de region y de riesgo en la bandeja de comunicaciones. Sin cambios en scripts ni en la recepcion del Formulario respecto de 0.6.0. Desde T03c (D24), un aviso a distribucion por cada COM del Formulario por distribuir ("Mis avisos" y, con Graph, correo). Compilado y probado en Windows (TEST-SIGER) con JDK 25: 191 pruebas JUnit en H2, todas correctas, y 112 pruebas del frontend (`npm test`, node --test sin dependencias nuevas); la suite completa tambien paso sobre un MySQL 8.4 aislado y desechable (`-Dsiger.mysql.suite=true`, 194 pruebas), incluida la actualizacion desde un volcado sintetico de 0.4.0 hasta V16 (`MySql84UpgradeTest`). Detalle por gate en `docs/BITACORA_0_7_0.md`.
- 0.6.0 prepara la salida a produccion sin funcionalidad nueva de expedientes (`docs/PLAN_0_6_0.md`, decisiones D10-D17): cierre de la validacion con recepciones de prueba autorizadas (`Close-Validation.ps1`), eliminacion de la validacion cerrada con constancia (`Remove-Validation.ps1`), credencial y prueba controlada de Graph, scripts invocables con `-File`, guia de la salida a produccion y liberacion por analisis en origen de los originales del Formulario (modo de emergencia D17, sin fecha de termino). Paquete de entrega `SIGER-20261122-201515.zip` (H06d), etiqueta `v0.6.0`. Sobre 0.5.0 (administracion en TI, consulta institucional, sedes regionales, buscador, calendario habil, hitos, reportes y exportacion). Detalle en `docs/BITACORA_0_6_0.md` y `docs/BITACORA_0_5_0.md`.
- 0.7.0 esta cerrada: paquete de entrega `SIGER-20261123-133838.zip` (T06a), ensayado en TEST-SIGER (T06b), etiqueta `v0.7.0`. V4 usa 0.7.0 solo con la aprobacion expresa del responsable al corte del 07/12; si no, V4 usa 0.6.0.
- **No es una puesta en produccion acreditada.** El PULL/ACK real con el Formulario Publico esta verificado en la instancia de validacion 0.4.0 de la VM (2026-11-21: modo automatico, 9 recepciones de prueba del responsable confirmadas; ver `docs/ALCANCE_Y_PENDIENTES.md`). No se han ejecutado aun: la instancia de produccion, un envio real por Graph ni el analisis antivirus con un motor de la VM (produccion parte sin motor; rige D17 para los originales del Formulario).
- Integraciones existentes e **inactivas hasta configurarlas**: consumidor PULL/ACK del Formulario Publico (manual o automatico), correo institucional por Microsoft Graph con outbox, servicio permanente de Windows y analisis antivirus para liberar originales.

## Modos de instalacion

- **Institucional** (`Init-Server.ps1 -Mode Institucional`): sin cuentas, sedes ni datos de prueba. Crea solo los ambitos reales del archivo de estructura inicial, una cuenta TI y los funcionarios que traiga (sin autoridad habilitante desde 0.5.0); el resto se registra por la interfaz con autoria y fundamento. Se inicializa y opera con una cuenta de servicio de Windows **sin privilegios administrativos**; un administrador solo registra los servicios.
- **Pruebas** (`-Mode Pruebas`): datos sinteticos, etiqueta visible "Entorno de pruebas" y liberacion manual de archivos de prueba.

## Desarrollo en el PC

Herramientas portables en `.tools/` (sin instalacion global; `scripts\Get-Tools.ps1` si faltan):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -IncludeJava   # build con pruebas y paquete release\SIGER-<fecha>.zip
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-Local.ps1           # local con H2 y datos sinteticos
```

Conservar `.tools`, `var/`, `frontend/package-lock.json` y `frontend/node_modules`. No compartir `var/bootstrap-usuarios.csv`, bases, originales ni configuracion privada.

## Instalacion en la VM

`docs/INSTALACION_VM.md`: cuentas e identidades, inicializacion institucional, servicios Windows, integraciones, antivirus, monitoreo de latidos, pruebas de arranque/parada/recuperacion y sustitucion del consumidor de SMR. No se tocan Java 8, MySQL80, Tomcat, SQL Server, SMR ni el firewall existentes.

## Documentos

- `docs/INSTALACION_VM.md`: instalacion y operacion.
- `docs/DECISIONES_FUNCIONALES.md`: reglas de dominio vigentes.
- `docs/ALCANCE_Y_PENDIENTES.md`: alcance funcional y pendientes.
- `docs/ARQUITECTURA.md`: modulos y persistencia.
- `docs/DESPLIEGUE_VM.md`: salida a produccion en la VM (V0-V3 con 0.6.0; V4 con 0.7.0 o 0.6.0 segun el corte del 07/12; cierre y eliminacion de la validacion, produccion nueva, plan B y operacion).
- `docs/PRUEBAS_MANUALES_0_7_0.md`: recorridos de la cobertura territorial, la region de origen y la bandeja (escenarios 1-8).
- `docs/PLAN_0_7_0.md` y `docs/BITACORA_0_7_0.md`: plan y registro de los gates de 0.7.0.
- `docs/PRUEBAS_MANUALES_0_6_0.md`: recorridos de la salida a produccion, D17 y la primera entrega ciudadana.
- `docs/PLAN_0_6_0.md` y `docs/BITACORA_0_6_0.md`: plan y registro de los gates de 0.6.0.
- `docs/ACTUALIZACION_0_5_0.md`: actualizacion 0.4.0 -> 0.5.0; referencia, no se ejecuta (la validacion no se actualiza, D14).
- `docs/PRUEBAS_MANUALES_0_5_0.md`, `docs/PLAN_0_5_0.md` y `docs/BITACORA_0_5_0.md`: recorridos, plan y registro de 0.5.0.
- `docs/PRUEBAS_MANUALES_0_4_0.md` y `docs/VERIFICACION.md`: recorridos y evidencia de versiones anteriores.
