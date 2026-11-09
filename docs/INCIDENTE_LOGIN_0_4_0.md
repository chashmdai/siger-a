# Incidente: 500 en el ingreso y en el cambio obligatorio de clave (0.4.0)

Fecha: 2026-11-08. Entorno: instancia local de pruebas (`scripts\Run-Local.ps1`, perfil `local`, H2 en `var\local-db.mv.db`).

## Síntomas informados

- `POST /api/login` devolvía 500 en el primer intento. Al reintentar se llegaba a la pantalla de cambio de clave temporal.
- `POST /api/password` devolvía 500 y no permitía continuar, aunque la clave nueva cumplía los requisitos.

## Investigación

1. **Instancia y JAR.** La instancia afectada era `Run-Local` (PID 150848, puerto 18543), iniciada a las 22:42 con el JAR vigente. No se detuvo ni se modificó.
2. **Mismo JAR en otras condiciones.** Se probó el mismo JAR con una base nueva y con una copia de `var\local-db`, por HTTP y en Chrome, desde un proceso elevado: el flujo funcionaba.
3. **Contenido de la base en disco** (copia leída en modo solo lectura, sin consultar hashes):
   - No había ningún evento `LOGIN` ni `CLAVE_CAMBIADA`. Solo existía el evento del bootstrap.
   - Las 19 cuentas seguían con `must_change=TRUE` y `security_version=0`.
   - El archivo no se escribía desde las 19:22, aunque la instancia corría desde las 22:42.
   - Conclusión: ninguna escritura de esa instancia se confirmaba.
4. **Permisos.**
   - `local-db.mv.db` fue creado a las 19:21 por un proceso elevado. Su dueño es `BUILTIN\Administradores` y `BUILTIN\Usuarios` solo puede leerlo.
   - La carpeta `var` no hereda el permiso de la cuenta de trabajo.
   - La instancia de `Run-Local` corría **sin elevación**, verificado con el token del proceso.
   - Al intentar abrir el archivo para escritura sin elevación se obtiene `UnauthorizedAccessException`. Solo se abrió, sin modificarlo.
5. **Reproducción exacta.** Una copia con el mismo ACL, abierta por Java sin elevación (`runas /trustlevel:0x20000`), reproduce los síntomas:
   - el ingreso responde 500 y la sesión queda autenticada;
   - el cambio de clave responde 500 y la clave no cambia;
   - en el registro aparece `INSERT INTO audit_event … [90097] The database is read only`.

## Causa

- **Entorno.** H2 abre en **modo solo lectura, sin avisar**, un archivo que el proceso no puede escribir.
  - El arranque solo lee (Flyway valida y el bootstrap encuentra cuentas), así que la instancia quedaba «UP».
  - La primera escritura real fallaba.
- **Código: ingreso no atómico.**
  - Spring Security guarda la sesión autenticada **antes** de invocar el manejador de éxito, que es donde se audita el ingreso.
  - Cuando esa auditoría fallaba, la respuesta era 500 y la sesión ya estaba iniciada, sin traza.
  - Por eso «al reintentar» se llegaba al cambio de clave.
  - Era una operación completada a medias.
- **Cambio de clave.**
  - Es transaccional y se revertía por completo (sin cambios parciales).
  - Respondía 500 con un mensaje sobre «reintentar con la misma clave» que no orientaba.
  - La sesión se invalidaba dentro de la transacción, antes de su confirmación.

## Correcciones

| Archivo | Cambio |
| --- | --- |
| `Db.java` | `readOnly(Throwable)` reconoce una base de solo lectura: H2 90097, SQLState 25006 y MySQL 1290/1836. `requireWritable(Connection)` actualiza sin cambios la fila de `operation_lock` y luego revierte. |
| `Bootstrap.java` | Al arrancar se llama a `Db.requireWritable`. Una base de solo lectura **impide el arranque**, con un mensaje que indica revisar los permisos. Antes quedaba «UP» y fallaba en el primer ingreso. |
| `SecurityConfig.java` | El ingreso solo queda abierto si su auditoría se registra (`loginSucceeded`). Si falla, se limpia el contexto, se invalida la sesión y se responde 503 con `{message, incident}`. El registro no incluye SQL ni credenciales. |
| `Api.java` (`/api/password`) | La sesión se invalida **después** de confirmar la transacción (`afterCommit`). |
| `ApiErrors.java` | Una base de solo lectura responde 503, con un mensaje claro e identificador de incidente, en lugar del 500 genérico. |
| `scripts/Run-Local.ps1` | Antes de iniciar verifica que la consola pueda escribir `var\local-db.mv.db`. Solo abre el archivo, no lo modifica. Si no puede, se detiene y explica cómo resolverlo. |
| `LoginPasswordTest.java` (nuevo) | Pruebas de regresión por HTTP real: sesión, CSRF y filtros. |

No se modificaron migraciones, datos, cuentas, permisos de archivos ni la instancia en ejecución.

## Pruebas de regresión (`LoginPasswordTest`)

- **`ingresoCambioObligatorioYNuevoIngreso`**
  1. El ingreso con la clave temporal responde 204 y `must_change=true`.
  2. Las demás consultas responden 428.
  3. Una clave actual incorrecta responde 403; una clave nueva corta, 409.
  4. El cambio de clave responde 200 y cierra la sesión: `must_change=false`, `security_version=1` y queda auditado.
  5. La clave temporal pasa a responder 401.
  6. El ingreso con la clave nueva responde 204 y el catálogo 200, con dos eventos `LOGIN`.
- **`ingresoSinAuditoriaNoQuedaAbiertoYElCambioDeClaveEsAtomico`**
  - Con la auditoría sin poder escribirse, el ingreso responde 503 y **no queda sesión**.
  - Si falla la auditoría del cambio de clave, la clave no cambia y la sesión sigue vigente para reintentar.
  - Con el manejador anterior esta prueba falla: `expected <503> but was <500>`.
- **`arranqueRechazaBaseDeSoloLectura`**
  - Sobre H2 en archivo, la verificación no altera datos si la base es escribible.
  - Si se abre con `ACCESS_MODE_DATA=r`, rechaza el arranque.
  - Se comprueba además la detección de los códigos H2 y MySQL.

## Resultados

- **Suite completa:** 16 pruebas, 0 fallas (13 anteriores y 3 nuevas), en una copia del proyecto, porque la instancia en ejecución bloquea `backend\target\siger-a.jar`.
- **Tu escenario con el JAR corregido** (sin elevación y con el ACL original): la instancia **no arranca** e informa: «La base de datos se abrió en modo solo lectura…».
- **Base escribible, Java sin elevación y JAR corregido:**
  - En Chrome: ingreso con clave temporal (204), pantalla de cambio, cambio de clave (200), regreso al ingreso, ingreso con la clave nueva (204) y carga del inicio.
  - Por HTTP: la clave temporal posterior se rechaza (401). Sin errores en el registro.
- **`Check-Scripts.ps1`:** 17 scripts válidos en PowerShell 5.1.

## Ingreso: centrado

- La tarjeta de ingreso se centra en ambos ejes del viewport.
  - El aviso de entorno quedó posicionado arriba y no desplaza la tarjeta.
  - Se usa `100dvh` en móviles.
- **Pantallas bajas** (≤680 px de alto): se reducen los márgenes y el escudo.
- **Pantallas angostas** (≤820 px de ancho):
  - la tarjeta mide como máximo 480 px;
  - «Defensoría de la Niñez» queda junto al nombre, al lado del escudo.
- **Verificado en:** 1440×900, 1366×640, 1920×1080, 768×1024, 390×844 y 320×568.
- **Ajuste menor:** en anchos ≤420 px, «Ver todos» de las colas del inicio ya no se parte en dos líneas.

## Para recuperar la instancia local (no ejecutado)

La instancia actual sigue sobre la base de solo lectura y con el JAR anterior. Se recomienda:

1. Detener `Run-Local`.
2. Elegir una de estas opciones:
   - Desde una consola **de administrador**, conceder Modificar a la cuenta de trabajo sobre `var`:
     `icacls C:\siger-a\var /grant "DEFENSORIANINEZ\nexbyte.proveedor:(OI)(CI)M" /T`
   - Ejecutar siempre `Run-Local` con la misma elevación con que se creó la base.
3. Recompilar (`Build.ps1` o `mvnw … verify` después de `npm run build`) e iniciar `Run-Local`. Si la base aún no se pudiera escribir, el script lo advertirá antes de arrancar.
