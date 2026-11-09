# SIGER A 0.4.0 - seis pruebas manuales

Usar solamente datos sinteticos. No compartir contrasenas, CSV privados ni capturas de informacion real. Primero compilar sin SkipChecks y respaldar var detenida. Credenciales existentes; versiones de antes conservadas.

## 1. Ingreso parcial que ya es REQ

Con `administrativo`, abrir **Nueva atencion ciudadana**. Elegir el ambito de Requerimientos y modalidad general o NNA. Completar solo un motivo sintetico breve; fecha/canal ya tienen valores. Guardar desde primer abordaje sin RUN, nombre, entrevista ni judiciales.

Esperado: COM y REQ creados una sola vez, REQ abierto y ficha EN ATENCION, cero SOL/casos. Recargar conserva el ingreso. Este REQ aparece en Atencion ciudadana / A mi cargo. Abrirlo desde la tarjeta del dashboard debe abrir REQ (no Caso).

## 2. Administrativo entrega directo a profesional

En ese REQ, elegir **Entregar primera atencion**, destinatario Profesional y `profesional` en UPR-Requerimientos. Fundamento. La directora no debe aprobar esta entrega.

Esperado: origen conserva encargo hasta recepcion; estado Pendiente de recepcion. Editar/cerrar queda bloqueado. Como profesional, aparece en **Entregas por recibir**, puede leer la ficha y **Recibir y continuar**. Misma COM/REQ, sin SOL/caso, jefatura principal intacta. El administrativo conserva seguimiento de la entrega, no acceso libre a entrevista complementada. Un profesional UPD ajeno no ve el contenido por conocer el ID.

Repetir brevemente una segunda entrega con Devolver o Retirar: no cambia encargado y conserva historia. No aceptar dos veces la misma entrega.

## 3. Complementacion NNA sin forzar respuestas

Profesional: completar datos que faltaban, una observacion separada del relato y participacion **No desea conversar**. Guardar con motivo. Opcionales todos los datos sensibles. No inventar respuesta por silencio.

Esperado: el REQ persiste, la negativa no bloquea. En Versiones estan primer registro y nueva version, cada una con autor/fecha/motivo. Original COM conserva motivo inicial. Vista completa/imprimir muestra lo registrado, no datos de ejemplo. Recargar conserva la ultima version. Cambiar de menu con cambios sin guardar advierte.

## 4. Resultado inmediato vs seguimiento

A) Iniciar otra atencion con administrativo/profesional/jefatura, guardar y Resolver atencion inmediata con resultado real sintetico, fecha y fundamento. Esperado: REQ cerrado, sin SOL/caso, constancia y cierre conservados; ficha cerrada no editable.

B) En un REQ abierto marcar **Requiere Caso de seguimiento**. Resolver inmediatamente o cerrar el REQ sin Caso debe bloquear. La coordinadora ve Seguimiento por asignar. Puede recibir una entrega dirigida a UPR-Requerimientos y abrir/asignar un Caso en ese mismo REQ. Administrativo no tiene ese boton/facultad. La ficha pasa a CON CASO y la gestion sigue en actuaciones, documentos y tareas existentes.

Si se envio a jefatura, no debe abrirse Caso hasta recibir o retirar esa entrega. No simular una SOL nueva para desbloquearla.

## 5. Documentos y aislamiento de destinatarios

Preparar un TXT o PDF sintetico antes de crear atencion y seleccionarlo. Otro archivo se puede agregar al original mientras la primera atencion este abierta/editable. Si elegiste un archivo pero no lo preparaste, guardar debe pedir preparar o retirar, no perderlo silenciosamente.

Esperado: originales bajo COM, separados de documentos profesionales. Siempre cuarentena. No liberar datos reales. La entrega a profesional da acceso contextual al ingreso, no a otros REQ. Puede entregarse a profesional habilitado de otra area/unidad sin modificar principal; un cambio formal de principal sigue requiriendo transferencia ya existente.

## 6. Regresion breve y persistencia

Registrar **Comunicacion por clasificar** (no atencion directa): queda COM pendiente; distribuidor envia a UPR sin area -> Direccion UPR, UPR/Requerimientos -> Coordinadora. El circuito SOL/asuncion sigue funcionando y no se ha creado REQ en esa via antes de asumir.

Detener y volver a arrancar local: fichas, versiones, entrega pendiente, usuarios y permisos se conservan. Revisar un REQ/caso antiguo de 0.3.1. Tareas y bloqueo de cierre siguen iguales. No usar esta prueba como acreditacion de MySQL/SMR.

## Resultado a informar

Para cada punto: CORRECTO / FALLA / NO PROBADO. En fallo: rol, accion, esperado, mensaje exacto; sin datos personales o secretos. No hace falta repetir toda la matriz ni construir decenas de tests unitarios.
