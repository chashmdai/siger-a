# SIGER A - fuente 0.4.0

Monolito institucional: Java 25 / Spring Boot 4.1.1, React 19 / TypeScript y base propia H2 local o MySQL independiente. Fase A manual y sintetica; SMR/PULL/ACK es fase B, todavia no integrado. El frontend se empaqueta dentro del JAR.

## Estado real

**Candidata de codigo fuente, no version productiva ni JAR compilado en el entorno de preparacion.** Se ejecutaron comprobaciones auxiliares de codigo, tipos, SQL y dominio; no se dispuso de JDK25, Maven/dependencias, React/Vite instalados ni PowerShell Windows. No se ejecutaron aqui los 12 metodos JUnit del proyecto, Flyway H2/MySQL nativos ni el flujo integrado en navegador. Ver `docs/VERIFICACION.md`.

La 0.4.0 amplifica primera atencion sin reproducir literalmente los HTML conceptuales ni convertirlos en normas. Respeta las decisiones mas recientes del responsable del proyecto.

## Primer build o actualizacion en tu PC

Extraer en una carpeta fuera de OneDrive y del proyecto anterior de SIGER. La raiz contiene `mvnw.cmd`, `backend`, `frontend`, `scripts`.

Si ya tienes `.tools` de la 0.3.1, **conservala**, junto con `frontend/package-lock.json`, `frontend/node_modules`, `.git` y toda `var/`. No hace falta descargar herramientas otra vez.

Solo para preparar herramientas que falten (internet, sin instalacion global):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Get-Tools.ps1
```

Compilar y generar paquete con Java portable:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -IncludeJava
```

El build comprueba la sintaxis de los scripts y los hashes .NET, prepara React, ejecuta Maven `clean verify` y empaqueta solo si termina sin errores. En un primer build resuelve `package-lock.json` y lo conserva para Git. No se entrega un lockfile inventado. Puedes indicar `-JavaHome` y `-NodeHome` si usas tus propias carpetas portables. No cambia PATH/JAVA_HOME persistentes.

Arranque local, **solo despues de build correcto**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-Local.ps1
```

Navegador: `http://localhost:18543`. Java25 propio y H2 persistente bajo `var/local-db`; no necesita MySQL local. Claves iniciales privadas en `var/bootstrap-usuarios.csv`. Las claves existentes no cambian. No compartir esos CSV, bases, originales ni configuraciones privadas.

## Cambios principales 0.4.0

- **Nueva atencion ciudadana**: COM + REQ desde el primer registro, abiertos y sin SOL/Caso automaticos. Motivo breve, canal, fecha y ambito; datos personales, RUN, NNA, judiciales y entrevista no obligatorios.
- Una ficha compartida general/NNA, por secciones, editable durante primera atencion. Versiones con autoria/motivo; no modifica la COM original. Diferencia relato, observacion, acciones y compromisos. Asentimiento negativo o desconocido no bloquea el ingreso.
- Entrega directa a profesional habilitado o a autoridad receptora, con aceptar/devolver/retirar. El encargado de origen permanece hasta la recepcion. **No transfiere la jefatura principal ni crea un Caso.**
- Resolucion inmediata por quien atiende o jefatura competente, con resultado y fecha reales, sin SOL/Caso. Si hay seguimiento, la jefatura abre un Caso en el mismo REQ.
- Bandejas de atenciones a cargo, entregas entrantes/salientes, iniciadas por mi y seguimiento por asignar. Contadores limitados al alcance autorizado.
- Adjuntos originales preparados al ingresar o incorporados durante primera atencion editable; separados de documentos de gestion, siempre en cuarentena.
- Registro externo por clasificar sigue COM -> SOL -> REQ. No convierte automaticamente ingresos antiguos.
- V5 aditiva. V1/V2/V3/V4 identicas a la 0.3.1.

## Errores de build contemplados

- Hash SHA-256/SHA-512 con .NET (`scripts/Hash.ps1`), sin depender de `Get-FileHash`.
- Ninguna asignacion a `$HOME`: Java usa `$JavaDirectory`; Maven `$mavenHome`.
- `package-lock.json` se lee con `JSON.parse` de Node. Evita claves vacias/case-sensitive y no exige `ConvertFrom-Json -AsHashtable` de PowerShell6+.
- `Db.dataSource()` publico y unico, llamado por Bootstrap; JdbcTemplate privado, no acceso directo al campo de un proxy.
- `cas.longValue()` solo tras comprobar `cas != null`; `UserDetails` sin `Serializable` redundante.

## VM posterior

Ver `docs/INSTALACION_VM.md`. Destino propuesto `D:\SIGER-A`, Java25 portable, MySQL propio en 13326 y HTTPS18543 candidatos, solo loopback inicialmente. No instalar Node/Maven en VM. No tocar Java8, MySQL80, Tomcat, SQLServer, SMR ni firewall. Los scripts nativos requieren validacion guiada y permisos, no autorizan operar por haber generado este ZIP.

## Lo que no se declara terminado

Sin SMR, Graph/correo/SMS, antivirus o visor seguro, politicas completas de reserva excepcional, plazos normativos 48h, migracion del SIGER historico ni puesta en produccion. Solo datos sinteticos. No se atribuye firma externa a una version documental.

Al abrir un Caso se conserva la ficha inicial y la gestion continua en actuaciones del Caso; no hay edicion libre de una ficha cerrada ni de los originales. Completar tareas no genera actuaciones. Los compromisos escritos no crean tareas por su cuenta.

## Documentos utiles

- `docs/ACTUALIZACION_0_4_0.md`: aplicar fuente sin perder datos.
- `docs/PRUEBAS_MANUALES_0_4_0.md`: seis recorridos focales de primera atencion.
- `docs/DECISIONES_FUNCIONALES.md`: reglas vigentes y limites de esta entrega.
- `docs/ALCANCE_Y_PENDIENTES.md`: lo incluido frente a lo pendiente.
- `docs/VERIFICACION.md`: evidencia exacta y pruebas que quedan para el PC/VM.

No se modifico ningun repositorio remoto ni servidor para generar esta entrega.
