# SIGER A 0.4.0 - actualizar desde 0.3.1

**Fuente candidata; probar con datos sinteticos.** Cambios nuevos de atencion ciudadana y correcciones de compilacion. No activa SMR, Graph, servicios Windows ni recepcion real.

## 1. Preservar tu trabajo

Cerrar sesion y detener `Run-Local.ps1` con Ctrl+C. Respaldar el proyecto y **toda la carpeta var** con la base apagada: H2, documentos, adjuntos preparados y claves. No subir ese respaldo al chat ni a Git.

El ZIP de cambios y el ZIP completo contienen una carpeta `siger-a/`. Extraer en la **carpeta padre**, por ejemplo `C:\Users\Benja\Documents`, no dentro de `siger-a`. Comparar cambios propios antes de reemplazar.

Conservar `.tools`, `frontend/package-lock.json`, `frontend/node_modules`, `.git`, `var` y configuraciones privadas. El ZIP completo no contiene datos ni runtimes; no elimina tus carpetas al extraerlo. V1-V4 permanecen byte a byte iguales a 0.3.1; se agrega V5. No borrar H2 ni ejecutar `flyway repair`.

## 2. Compilar y ejecutar

Desde la raiz del proyecto (no desde `frontend`):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -IncludeJava
```

No usar `-SkipChecks` para acreditar la candidata. Se esperan **12 metodos JUnit**: nueve anteriores y tres nuevos recorridos integrados. Si falla, detenerse en el primer error; un build fallido no acredita release.

Solo si compila:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-Local.ps1
```

Abrir `http://localhost:18543`, refrescar navegador y comprobar pie **0.4.0**. H2 aplica V5 automaticamente al arrancar con perfil local. Claves/cuentas se conservan y las concesiones demo no se regeneran en cada reinicio.

La VM se actualiza despues, con paquete compilado y MySQL propio: no copiar este fuente sobre el runtime activo. No arrancar JAR0.3.x sobre esquema V5 como supuesto rollback. Para revertir un ensayo se necesita una copia coherente de BD/archivos/configuracion y el JAR anterior, con la aplicacion detenida y siguiendo la guia.

## 3. Que cambia para el funcionario

| Entrada | Efecto |
|---|---|
| Nueva atencion ciudadana | COM + REQ abierto desde Guardar. No SOL ni Caso automatico. |
| Comunicacion por clasificar | Conserva solo COM pendiente; el distribuidor genera SOL y jefatura asume REQ. |
| Resolver atencion inmediata | Resultado real, fecha y cierre REQ sin Caso por facultad de primera atencion. |
| Entregar primera atencion | Profesional habilitado o jefatura recibe/devolvera; origen conserva encargo hasta recepcion. No cambia principal del REQ. |
| Necesita seguimiento | Se marca en ficha y bandeja de jefatura. Solo jefatura crea/asigna Caso dentro del mismo REQ. |

La primera atencion puede iniciarla administrativo, profesional o jefatura con funcion de registro en ese ambito. No se obtienen permisos al escoger un nombre/rol en un formulario. UPR-Requerimientos sigue siendo area de UPR y la unica coordinacion central receptora de area; el resto de areas usa su Direccion.

## 4. Ficha progresiva

Registro general o NNA con campos opcionales, un motivo breve obligatorio, fecha/canal/ambito. Datos desconocidos no se convierten en falsos; no exigir RUN o relato completo. NNA: participacion, forma de expresion y preferencias voluntarias, sin emoji ni diagnostico. Incluye personas/grupos relacionados, contacto seguro y judiciales condicionales.

Cada complementacion requiere motivo y conserva version/autor/fecha. Se puede guardar desde cualquier seccion. El primer ingreso COM no se sobrescribe. La impresion incluye contexto/version y la indicacion de candidata de pruebas.

Mientras haya una entrega pendiente, la ficha no se edita ni se cierra; se puede recibir, devolver o retirar. Al pasar a Caso queda como antecedente y se continua la gestion en el Caso. Un registrador que ya entrego la atencion conserva seguimiento minimo, no acceso permanente a todas las entrevistas posteriores.

## 5. Correcciones de herramientas incluidas

Maven y otros scripts usan hashes por .NET. No se asigna `$HOME`. El lockfile se interpreta con Node (compatible con PowerShell5.1). `Db.dataSource()` y Bootstrap coinciden; no hay acceso directo a `db.jdbc`. Nullable Long comprobado antes de convertir; sin Serializable redundante.

El build ejecuta primero `Check-Scripts.ps1`: analisis sintactico PowerShell + dos vectores de hash .NET. Esto es una comprobacion del PC al ejecutarlo, no evidencia anticipada de instalacion Windows.

## 6. Limites de evidencia

Comprobaciones auxiliares efectuadas en preparacion: codigo/sintaxis/tipos, modelo SQL y 34 aserciones sobre los servicios Java reales usando dobles JDBC/Jackson y SQLite. No son Spring/H2/MySQL nativos ni E2E de navegador. No se ejecuto aqui el build completo Java25/Node24, los 12 JUnit, los scripts en PowerShell Windows ni la VM. No hay antivirus o correo real. Primer paso: build real del operador y guia manual.
