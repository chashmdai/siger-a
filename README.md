# SIGER - fuente 0.4.0

SIGER (Defensoria de la Ninez) gestiona Comunicaciones, Solicitudes, Requerimientos y Casos. Es un monolito Java 25 / Spring Boot 4.1.1 con frontend React 19 / TypeScript empaquetado en el mismo JAR, base MySQL 8.4 propia en la VM y H2 para desarrollo y pruebas. Antes se llamaba SIGER A; los nombres internos (`siger-a.jar`, esquema `siger_a_*`, `D:\SIGER-A`) se conservan por compatibilidad.

## Estado real

- Codigo compilado y probado en el PC de preparacion (Windows Server 2022): 71 pruebas JUnit en 19 clases, incluida una prueba de integracion sobre un MySQL 8.4.3 aislado y desechable (`MySql84IntegrationTest`, solo con `-Dsiger.mysql.base=...`; V1-V10, ciclo PULL/ACK con productor sintetico, outbox con Graph falso, inicializacion institucional), y el JAR real con el perfil `mysql` (TLS VERIFY_CA): migracion, salud y parada ordenada.
- **No es una puesta en produccion acreditada.** No se han ejecutado aun en la VM: la instalacion institucional, el registro de servicios, un PULL real con el Formulario, un envio real por Graph ni el analisis antivirus con el motor institucional.
- Integraciones existentes e **inactivas hasta configurarlas**: consumidor PULL/ACK del Formulario Publico (manual o automatico), correo institucional por Microsoft Graph con outbox, servicio permanente de Windows y analisis antivirus para liberar originales.

## Modos de instalacion

- **Institucional** (`Init-Server.ps1 -Mode Institucional`): sin cuentas, sedes ni datos de prueba. Crea solo los ambitos reales del archivo de estructura inicial, una cuenta TI y una autoridad habilitante; el resto se registra por la interfaz con autoria y fundamento. Se inicializa y opera con una cuenta de servicio de Windows **sin privilegios administrativos**; un administrador solo registra los servicios.
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
- `docs/PRUEBAS_MANUALES_0_4_0.md` y `docs/VERIFICACION.md`: recorridos y evidencia de versiones anteriores.
