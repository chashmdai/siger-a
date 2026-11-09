# SIGER A 0.3.1 - bandejas y continuidad

**Fuente candidata. No es un binario probado en tu PC ni una version habilitada para datos reales.** Actualizacion acumulativa sobre 0.3.0; no sustituye SMR ni integra Graph.

## Actualizar tu fuente local

1. Cerrar sesion y detener `Run-Local.ps1` con Ctrl+C. No copiar una base H2 mientras esta abierta.
2. Respaldar privadamente el proyecto y especialmente `var/` completo: base H2, documentos, adjuntos preparados y credenciales. No subir ese respaldo al chat.
3. Extraer `SIGER-A-cambios-0.3.1.zip` en la carpeta **padre** del proyecto. Si trabajas en `C:\Users\Benja\Documents\siger-a`, extraer en `C:\Users\Benja\Documents`. El ZIP ya contiene `siger-a/`.
4. Reemplazar los archivos de fuente indicados. Conservar `.tools/`, `frontend/package-lock.json`, `frontend/node_modules/`, `var/`, configuraciones privadas y Git. Si editaste el fuente por tu cuenta, comparar antes de reemplazar; el ZIP de cambios no mezcla tus modificaciones automaticamente.
5. Abrir PowerShell en la raiz `siger-a` y compilar:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -IncludeJava
```

6. Solo si el build termina correctamente, arrancar localmente:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-Local.ps1
```

7. Abrir `http://localhost:18543`, entrar con tus claves existentes y actualizar el navegador. La version del pie debe ser **0.3.1**.

El primer arranque local aplica **V4**. V1/V2/V3 conservan exactamente sus bytes. No borrar H2, no usar `flyway repair`, no editar migraciones aplicadas. Si falla, detener y revisar el primer error. No arrancar un JAR viejo sobre la base migrada para intentar una reversion improvisada.

No se cambian nombres ni claves de cuentas existentes por esta actualizacion. Las cuentas demo y sus permisos ya configurados no se reconstruyen en cada reinicio. Una instalacion vacia sigue creando los usuarios sinteticos; eso no es configuracion institucional nominal.

## Cambios incluidos

| Pregunta de trabajo | Respuesta incorporada |
|---|---|
| Que me toca hacer | Portada por funcion con pendientes, colas de trabajo y accesos a filtros; panorama limitado a los registros autorizados. |
| Quien debe decidir | Subrogancia conserva Direccion/Coordinacion y sus ambitos; recibir en unidad y gestionar en area son decisiones separadas. |
| Que falta para terminar | Fichas explican bloqueos de cierre y mantienen tarea pendiente/revision/decision humana separadas. |
| Donde esta la evidencia | Documentos asociados a actuaciones, versiones relacionadas, ingreso con adjuntos, retorno de antecedentes rechazados e historial preservado. |

### Bandejas y metricas

- Distribucion: COM por distribuir, SOL devueltas y avisos sin leer.
- Direccion/Coordinacion: SOL por asumir, cierres por revisar, decisiones entrantes y REQ sin bloqueos de cierre.
- Profesional: Casos propios, asignaciones sin acuse, cierres devueltos y recordatorios vencidos/proximos.
- Primera atencion: ingresos propios y atenciones sin Caso visibles.
- Autoridad: subrogancias activas/programadas en su ambito.
- TI: cuentas y cuarentena, sin abrir expedientes por ese perfil. Auditoria: su trazabilidad autorizada.
- Una persona con varias funciones cambia de espacio sin cambiar cuenta. El resumen general es **de su alcance**, no una habilitacion transversal ni un reporte oficial.
- Listados con busqueda, filtros y paginas de 25 desde SQL; no se descarga toda la cartera para paginar en React. Los conteos no dependen de la pagina abierta.

### Continuidad y captura

Subrogante de Direccion recibe SOL y gestiona solo las areas previamente habilitadas a esa funcion. La Coordinadora UPR-Requerimientos sigue siendo la unica excepcion central de area. La titular conserva gestion de sus propios Casos; no hay traslado de cartera ni autocierre del subrogante propio.

Al asumir un ingreso propio o aceptar colaboracion/derivacion, se selecciona el area y profesional habilitados. Transferir REQ sigue sin mover Casos o tareas.

Ingreso manual admite contacto separado, instrucciones de contacto seguro y hasta 12 personas relacionadas opcionales, sin exigir RUN ni crear maestro de personas. Los adjuntos se preparan privadamente y se confirman junto con la COM, incluida primera atencion resuelta. Si se interrumpe la captura, los preparados permanecen privados para recuperar o eliminar explicitamente; no generan folio ni COM por si solos.

Un antecedente rechazado puede volver a Clasificacion mediante decision fundada, conservando COM y rechazo. El retorno no reabre el REQ anterior. El circuito incluido permite redistribuir ese ingreso; no incorpora una cadena general de propuestas sucesivas a multiples REQ.

Documento de gestion: tipo, descripcion, actuacion opcional y version anterior opcional del mismo contexto. No se sobrescribe ni se atribuye firma/visacion externa. La reserva de actuacion/version anterior se conserva. Los originales ciudadanos no se convierten en documentos de gestion.

### Operacion en VM

Scripts de respaldo con manifiesto verificable, ensayo `Restore-Check.ps1` en esquema/carpeta nuevos del MySQL propio, seleccion de JAR solo despues de migracion correcta y sonda HTTPS de disponibilidad con certificado local fijado. Una actualizacion fallida bloquea el arranque ambiguo. Ver `INSTALACION_VM.md`.

**Estos scripts no han sido ejecutados aqui en Windows/MySQL.** No reemplazar una instalacion de VM ni ensayar restauracion antes del build y la revision guiada. No se abre firewall, no se registra SCM y no se toca MySQL80/Tomcat/Java global.

## Verificacion y limites

Se ejecutaron controles auxiliares de sintaxis/tipos, politicas puras y consultas SQL sobre un modelo SQLite, ademas de revision visual estatica del dashboard. **No equivalen al build real Java 25/Spring/React, a Flyway H2/MySQL ni a pruebas integradas de navegador.** El proyecto prepara nueve metodos JUnit en total (cinco anteriores y cuatro recorridos focales nuevos); aqui no se ejecutaron con las dependencias reales.

SMR, Graph, antivirus/visor seguro, permisos excepcionales completos, calendario normativo de 48 h, datos historicos y puesta en produccion siguen fuera de esta version. Usar datos y documentos sinteticos. El certificado local no es certificado institucional.

Guion breve: `PRUEBAS_MANUALES_0_3_1.md`. Evidencia de preparacion: `VERIFICACION.md`.
