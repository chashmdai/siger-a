# Verificacion y limites - SIGER A 0.4.0

## Ejecutado durante preparacion

| Comprobacion | Evidencia | Limite |
|---|---|---|
| Sintaxis Java | Fuentes Java del proyecto analizadas con el parser javac21 sin errores. | No build Java25/Spring. |
| Tipos de servicios Java | Db/Input/Problem/Access/Rules/Documents/Personnel/Attention/Workflow/Queries/BoardQueries compilados con javac21 contra firmas auxiliares de Spring/JDBC/Jackson. | Dobles, no librerias reales. No incluidos en ZIP. |
| Dominio y consultas | **34 aserciones** sobre los servicios Java reales ejecutados con dobles JDBC/Jackson y motor SQLite: ingreso parcial, idempotencia, permisos positivos/negativos, entrega/recepcion, versiones, cierres, seguimiento y bandejas. | Schema SQLite adaptado; no Flyway nativo, JSON real, transacciones de Spring, locks MySQL ni concurrencia. |
| Frontend | 12 fuentes TS/TSX transpiladas sin errores con TS5.8.3; chequeo auxiliar de tipos sin errores. | No TS6/React19/Vite8 reales. Declaraciones de React simplificadas, no sustituyen npm build. |
| PowerShell | Inspeccion de codigo, ausencia de asignaciones HOME y llamadas a Get-FileHash; parser de lockfile Node con claves vacias/case sensibles. | PowerShell5.1/7 no ejecutados en preparacion. Check-Scripts.ps1 queda para ejecutar al compilar en Windows. |
| Migraciones | V1-V4 identicas a la 0.3.1; V5 aditiva. | Upgrade H2/MySQL debe probarse en el PC/VM. |
| Empaquetado | JSON/XML validos, integridad ZIP y manifiestos SHA256. | Fuente candidata, no acreditacion productiva. |

Los 34 checks son un arnes auxiliar fuera del producto, no 34 nuevas pruebas unitarias incorporadas. El modelo SQL ejecuta las consultas reales de las bandejas; no permite asegurar compatibilidad total con MySQL/H2. No se publican datos reales, secretos ni dobles dentro del fuente.

## No ejecutado en este entorno

No se pudo descargar dependencias; entorno disponible Java21 y Node22. No se ejecuto Maven verify/JDK25, npm ci/Vite con dependencias reales, los JUnit/Spring, motor H2/MySQL nativo, navegador conectado al backend real, Windows PowerShell, instalacion VM ni integracion externa. No se produjo un JAR compilado. No afirmar que estos checks sustituyen esa evidencia ni reutilizar resultados 0.2/0.3 como prueba de 0.4.

## JUnit incluidos, pendientes del build real

- RulesTest: 4 metodos existentes.
- WorkflowTest: 1 recorrido transaccional existente.
- Version031Test: 4 recorridos existentes.
- Attention040Test: **3 nuevos recorridos**, no decenas de tests: ingreso parcial/entrega/versiones/permisos; resolucion inmediata por funciones habilitadas; seguimiento y apertura de Caso por jefatura.

Total: **12 metodos JUnit**. Un build con SkipChecks no acredita pruebas. Windows debe ejecutar Build.ps1 sin ese switch y producir release/manifiesto antes de trasladarlo a la VM.

## Puntos manuales prioritarios

La nueva ficha y su impresion, bandejas por rol, navegacion sin perder cambios, envio/recepcion/devolucion/retiro con sesiones distintas, creacion de Caso, conservacion de base existente. Guia `PRUEBAS_MANUALES_0_4_0.md`.

La proteccion de guardado incierto reutiliza la misma clave mientras permanece abierta la pantalla. Una interrupcion completa del navegador antes de recibir confirmacion exige consultar los registros antes de repetir un ingreso; no se declara un borrador offline recuperable ni persistencia de datos ciudadanos en localStorage.
