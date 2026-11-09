> Para actualizar desde 0.3.0, empezar por `PRUEBAS_MANUALES_0_3_1.md`. El guion completo siguiente sigue como referencia; no hace falta repetirlo entero.

# Recorrido manual - SIGER A

Estado inicial: guion preparado, **no ejecutado en la VM por quien genero el ZIP**. Anotar por recorrido: version/JAR, usuario, folios, resultado esperado/observado y error. Usar solo personas y archivos sinteticos.

## Cuentas de partida

Las claves se generan privadamente al inicializar. Cada cuenta cambia su clave antes de operar. Usar perfiles de navegador separados o cerrar sesion antes de cambiar persona; dos pestanas del mismo navegador comparten sesion.

| Usuario | Funcion sintetica |
|---|---|
| distribucion | Distribucion central y registro en UPR-REQ |
| administrativo | Registro / primera atencion UPR-REQ |
| profesional | Profesional UPR-REQ |
| coordinacion.requerimientos | Coordinadora del area UPR-REQ, no de toda UPR |
| direccion.upr | Direccion en otras areas UPR y lectura UPR-REQ; consulta regional por materia UPR |
| direccion.upd / profesional.upd | Direccion UPD / profesional de Educacion |
| coordinacion.regional / profesional.regional | Titular y profesional de REG-DEMO |
| autoridad | Autoridad sintetica para los ambitos iniciales; no acceso clinico por ese titulo |
| ti | Cuentas/configuracion tecnica y cuarentena; no expedientes |
| auditoria | Auditoria del ambito UPR-REQ |
| gabinete | Consulta concedida en UPR-REQ; no permiso universal |

Las jefaturas sinteticas tienen tambien una concesion profesional independiente en su ambito para conservar sus Casos personales al ser sustituidas. REG-DEMO no es una sede institucional real. La identidad institucional, nomina y cobertura real no se importaron.

## 1. Acceso, cambio de clave y vistas

Entrar con cada rol principal. Esperado: clave inicial obliga a cambiar; TI ve administracion, profesional ve sus Casos/tareas, jefatura ve SOL/REQ y decisiones, Distribucion ve ingresos. No deben aparecer datos ajenos por cambiar una URL o intentar abrir la ficha de otro ambito. Probar sesion cerrada y cuenta deshabilitada por TI. Una persona con varias funciones ve sus espacios en la misma cuenta.

## 2. Primera atencion resuelta

Con administrativo: Registrar ingreso -> Registrar atencion in situ resuelta. Canal TELEFONO, fecha real sintetica no futura, resultado ORIENTACION, relato y fundamento.

Esperado: un folio COM-A y un REQ-A CERRADO; cero SOL y cero Casos. Historia del resultado y del cierre. No usar un simple acuse como respuesta concluida. Los documentos o avisos no crean otro REQ.

## 3. Recorrido ordinario completo, complejidad BAJA

Con distribucion: Registrar ingreso pendiente, UPR-REQ, canal PRESENCIAL. En la COM usar Distribuir SOL a UPR-REQ.

Con coordinacion.requerimientos: SOL pendientes -> Asumir / asignar atencion a profesional. Esperado: nace REQ abierto sin Caso. Clasificar BAJA con fundamento. Abrir Caso, responsable profesional, materia UPR y objetivo de seguimiento.

Con profesional: Mis Casos -> abrir Caso, Acusar asignacion, registrar una actuacion con fecha efectiva. Confirmar cierre con fundamento. Esperado: Caso CERRADO, REQ sigue ABIERTO. No se crea una actuacion por el acuse.

Con coordinacion.requerimientos: abrir REQ y Cerrar REQ expresamente. Esperado: REQ CERRADO y ambos cierres conservados. Profesional no puede cerrar el REQ por su mero rol.

## 4. MEDIANA/ALTA y jefatura propia

Crear otro recorrido. Jefatura clasifica MEDIANA. Profesional registra gestion y Solicitar cierre; no puede confirmar directamente. Jefatura puede devolver con observaciones o aprobar. Luego probar otro Caso asignado a la propia jefatura: puede confirmar su cierre con fundamento, sin fingir un revisor distinto. No cierra por ello los Casos hermanos ni el REQ.

Probar reclasificacion BAJA -> ALTA en otra sesion mientras el profesional mantiene una ficha abierta: la autorizacion al cerrar debe volver a comprobar la complejidad actual, no la antigua del navegador.

## 5. SOL equivocada

Crear COM/SOL para UPR-REQ. Jefatura.upr devuelve a Distribucion. Distribucion ve la misma SOL devuelta y la redistribuye a UPD-EDU. Esperado: misma COM y SOL, sin REQ duplicado, historial de destinos.

En otra SOL pendiente, coordinacion.requerimientos redirige directamente a UPD-EDU. Direccion.upd asume. El anterior destino no conserva acceso por el solo hecho de aparecer en la historia. No declarar INVALIDA una consulta legitima solo porque llego a la unidad incorrecta.

## 6. REQ sin Caso que requiere seguimiento

Asumir una SOL, registrar atencion sin Caso solo si no hay seguimiento. Si cambia la necesidad, jefatura abre un Caso en el mismo REQ. No generar otro REQ. Intentar cerrar el REQ con Caso abierto: debe bloquearse. Con cero Casos, el cierre ordinario corresponde a jefatura principal; la excepcion de in situ es el recorrido 2.

## 7. Tareas y reasignacion

Crear Caso con dos tareas personales, una para hoy y otra sin cumplir. Completar una tarea no debe registrar actuacion. Registrar intento fallido mantiene la tarea pendiente. Cualquier tarea pendiente bloquea solicitud y confirmacion de cierre. Completar o cancelar explicitamente con fundamento permite continuar.

Jefatura reasigna el Caso a otra persona habilitada (puede usarse coordinacion.requerimientos como responsable para este ensayo). Todas las tareas no terminales pasan al nuevo responsable, conservando fechas e historial; las completadas no cambian. Nuevo acuse pendiente. Probar que un profesional ajeno no se agrega ni asigna Casos por si mismo.

## 8. Colaboracion y transferencia no son derivacion

Desde un REQ principal UPR, pedir colaboracion a UPD-EDU. Direccion.upd acepta y asigna profesional.upd. Esperado: Caso paralelo del mismo REQ; no cambia el principal, no cierra el Caso UPR y no concede edicion del Caso ajeno.

En otro ensayo, Transferir principal a UPD-EDU y aceptar. Esperado: mismo REQ/folio, nuevo principal; todos los Casos, responsables y tareas permanecen en sus ambitos. La nueva jefatura ve los estados necesarios para coordinar, no contenido ajeno por el solo traslado.

## 9. Derivacion atomica

Desde Caso UPR abierto, coordinacion.requerimientos pide derivacion a UPD-EDU. Mientras esta pendiente, origen sigue abierto. Direccion.upd acepta y asigna responsable.

Esperado: origen CERRADO por DERIVACION, un sucesor abierto bajo el mismo REQ, principal sin cambio. No hay segunda solicitud de cierre del origen. Reintento de la misma aceptacion no crea otro sucesor. Rechazo o retirada antes de aceptar no cierra origen.

Limite deliberado de candidata: todas las tareas pendientes deben resolverse antes de aceptar; no se borran ni transfieren tacitamente. Este borde debe revisarse con el responsable si se requiere otra regla.

## 10. COM hija y nueva gestion

Registrar otra COM sobre el asunto y Proponer antecedente usando el folio exacto del REQ. Jefatura evalua la propuesta y su destino pertinente. Esperado: nueva COM con folio propio, mismo REQ, ningun Caso reabierto automaticamente.

Con REQ cerrado: solicitar/evaluar nueva gestion, autorizar reactivacion del principal y decidir. Una reapertura de Caso conserva su identificador y todos sus cierres previos. Un Caso cerrado por derivacion no ofrece reapertura ordinaria: continuar en el sucesor o decidir otra gestion.

Esta entrega incorpora una evaluacion de pertinencia por jefatura; no acredita el circuito completo de valoracion profesional secundaria ni permisos documentales por todas las combinaciones de materias.

## 11. Subrogancia

Con autoridad, abrir Subrogancias. Ya existe predesignacion sintetica para REG-DEMO: titular coordinacion.regional, sustituto profesional.regional. Activar un periodo finito con fundamento. La activacion es directa: no hay paso obligatorio de TI.

Al reingresar, profesional.regional ejerce jefatura temporal del ambito; titular pierde esa facultad mientras corresponde sustitucion, no sus funciones profesionales independientes. No se trasladan Casos ni tareas. El subrogante puede decidir otros Casos del ambito, pero no confirmar su propio Caso. Autoridad o titular registra termino/retorno; no se cierran Casos pendientes automaticamente. Probar tambien expiracion y prohibicion de periodos superpuestos.

El revisor excepcional SUB-CONT-01 no esta implementado: no usar TI ni otra cuenta como atajo.

## 12. Archivos, persistencia y negativas

Cargar un TXT sintetico sin datos reales. Esperado: UUID de almacenamiento privado, hash SHA-256, metadatos y cuarentena. No hay visor. TI ve identificador/tamano/huella, no el relato ni descarga por su rol. Solo tras revision externa del archivo sintetico puede liberar tecnicamente; la descarga sigue exigiendo permiso sobre su contexto. No probar malware ni archivos reales.

Crear una nota reservada: colaborador/lector ordinario no debe verla; responsable/jefatura autorizados si. La reserva basica no equivale a todo el modelo FIR institucional.

Cerrar sesiones, parar solo SIGER A, volver a arrancar y verificar folios, Casos, cierres, archivos y permisos. Exportar CSV autorizado sin filas ajenas. Al terminar, dejar anotados bugs y version exacta. La prueba de restauracion se prepara separadamente; un backup creado no es una restauracion acreditada.

## Registro de resultados

| Recorrido | Usuario(s) | COM/REQ/CAS | Esperado | Observado | Aprobado / pendiente |
|---|---|---|---|---|---|
| 1-12 | Completar durante ensayo | Solo folios sinteticos | Segun guion | No ejecutado aun | Pendiente |

No pegar claves, SQL completo, archivos ciudadanos ni configuraciones privadas al comunicar un error.

## 13. Revisión específica 0.2.0

1. Con distribucion, distribuir COM y verificar confirmación "Distribución completada" sin intentar navegar a una SOL fuera del alcance del distribuidor. Jefatura ve esa SOL en su bandeja.
2. Como jefatura al asumir SOL, el selector de "Profesional encargado" muestra solo funcionarios habilitados del ambito de destino. Si no aparece alguno, comprobar su adscripción/autorización antes de modificar el filtro.
3. Como profesional responsable crear tarea de contacto con fecha de aviso a unos minutos del momento actual y repetición cada 24 horas. Comprobar avisos internos tras la fecha (ejecutor en usuario activo, revisar Mis avisos; el trabajador procesa cada 60 s). Repeticiones 48 h o 7 días no se aceleran en las pruebas manuales. No envía correo.
4. Con una tarea PENDIENTE/EN_CURSO aunque no marcada obligatoria en datos antiguos, intentar Solicitar cierre y Confirmar cierre: debe rechazar. Cumplir/cancelar explícitamente y repetir.
5. Solicitar término (Caso EN REVISION), verificar el aviso visible y botón "Retirar mi solicitud de cierre". Retirar con fundamento, comprobar vuelta a EN GESTION e historia preservada.
6. Ingresar COM con un TXT sintético, abrir un Caso asociado y ver el archivo bajo "Documentos del requiriente" (solo lectura, cuarentena). Adjuntar otro TXT al Caso y verlo exclusivamente en "Documentos de la gestion profesional". Los originales no se duplican.
7. Verificar el botón de cierre normal y que jefatura pueda seguir cerrando el REQ explícitamente.

**Actualización de base local:** no editar V1 ni borrar `var/local-db`. V2 es una migración Flyway incremental sobre la misma BD. Respaldar carpeta `var` con SIGER detenido antes de ejecutar por primera vez 0.2.0.

## 14. Revisión específica 0.3.0

Usar las **cuatro pruebas** de `docs/ACTUALIZACION_0_3_0.md`; no se pide repetir toda la matriz anterior. Para bases actualizadas desde 0.2.0, ver el cambio de nombres de login y las nuevas credenciales privadas descritos en el mismo documento. Los flujos ya creados no se mueven ni renumeran.
