# SIGER A 0.4.0 - arquitectura

Un JAR Spring Boot con React compilado y una base propia. Java25/Boot4.1.1/SpringJDBC/Security/Flyway; React19.3/TS6/Vite8.3; MySQL propio para VM, H2 persistente solo para pruebas locales. Sin microservicios ni nueva infraestructura.

## Responsabilidades

- Access: funciones/ambitos efectivos, titular/subrogante, lectura/escritura. Una sola regla resuelve una cuenta o todo el personal habilitado (avisos y catalogo) con consultas constantes.
- Workflow: envoltorio unico de comandos transaccionales e idempotentes (lock, actor, clave, registro) y despacho por dominio.
- IntakeCommands / RequirementCommands / CaseCommands: flujo COM/SOL, REQ y flujos entre unidades, Casos y tareas. Lifecycle concentra las transiciones compartidas (REQ, Caso, asignacion, cierre, constancias, folios).
- Attention (nuevo): primera atencion sobre COM/REQ; validacion de ficha, entregas y versiones. Se ejecuta dentro de Workflow, no tiene otro motor de procesos.
- Queries/BoardQueries: detalle autorizado, listas/conteos SQL paginados y dashboard.
- Documents: originales privados, preparacion y cuarentena. Sin antivirus acreditado.
- Personnel/Bootstrap: cuentas/funciones y semillas sinteticas de una sola aplicacion.
- Api/SecurityConfig: HTTP/sesion/CSRF. Actor del servidor, no rol enviado por cliente.

## Persistencia aditiva

V1-V4 se conservan. V5 agrega solo `attention_record` (ficha actual vinculada a REQ), `attention_revision` (versiones) y `attention_handoff` (entrega operativa). La COM conserva original/folio; REQ sigue siendo el padre de Casos. No se crea un expediente paralelo a SIGER ni un maestro universal de personas.

El esquema de ficha JSON1.0 es pequeno y validado mediante lista cerrada de campos/valores y limites, no JSON arbitrario. SQL conserva campos de enrutamiento/estado para filtros; la ficha completa se entrega solo en detalle autorizado. No se guardan datos sensibles en localStorage.

## Frontend

Organizacion: `app/` (contexto, rutas, menu, ingreso), `lib/` (HTTP, accesores, funciones, catalogo, campos), `components/` (dialogo de comandos, tabla, estado) y `features/` por pantalla. `features/attention` es el registro progresivo de tres secciones, que reutiliza API, componentes de dialogo, avisos y documentos. `features/intake` queda para la COM externa por clasificar. `features/detail` tiene una ficha por tipo. Dashboard y WorkspaceList reflejan las atenciones sin cargar toda la cartera ni crear cuentas por vista.

Presentacion: sistema visual propio sin dependencias nuevas (tokens CSS, iconos propios, Cabin OFL y escudo institucional servidos desde el JAR), con barra lateral por tipo de trabajo, paleta Ctrl+K, fichas con cadena COM→SOL→REQ→Caso y version movil. Las decisiones y su verificacion estan en `docs/DISENO_0_4_0.md`.

No se duplican los HTML conceptuales como aplicaciones independientes. La cuenta no puede adoptar permisos al seleccionar un supuesto rol en la ficha.

## Operacion

Los scripts ejecutan Java por ruta absoluta, MySQL en instancia independiente, configuracion/documentos/DB fuera del JAR. Hashes mediante .NET; parser de lockfile Node. Check-Scripts.ps1 se ejecuta al compilar en el PC. Backup/Restore-Check existentes se conservan; necesitan ensayo real Windows/MySQL. Sin cambio de PATH/JAVA_HOME persistente, firewall o servicio ajeno.
