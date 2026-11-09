# Pruebas manuales focales - 0.3.1

No repetir todo SIGER. Primera condicion: build Java25/Node24 sin `-SkipChecks`, arranque local y V4 aplicada. Datos sinteticos; conservar base anterior respaldada. Cada recorrido se considera pendiente hasta que el operador informe su resultado.

| Recorrido | Que hacer | Resultado esperado |
|---|---|---|
| 1. Bandejas por rol | Entrar como distribucion, direccion.upd, coordinacion.requerimientos, profesional, autoridad y ti. Abrir una metrica y su listado. | Portadas y pendientes distintos; la cifra coincide con el filtro y su alcance. TI no ve COM/REQ/Casos. Una Direccion tiene ademas espacio personal sin confundirlo con supervision. |
| 2. Subrogancia de Direccion | Con autoridad, predesignar direccion.upd -> profesional.upd en UPD y activar periodo de prueba. Distribuir una COM a UPD/Difusion. Entrar como suplente; asumir, elegir area y profesional. Terminar la subrogancia como autoridad. | El suplente recibe y decide como Direccion en sus areas, no UPR/Requerimientos. Titular no ejerce esa funcion durante el periodo pero mantiene sus Casos personales. Tras retorno, solo recupera facultades quien corresponde. |
| 3. Permiso revocado no reaparece | En cuenta demo/ambito de prueba anotar la concesion, revocarla con autoridad y reiniciar SIGER. Consultar nuevamente; luego restaurar explicitamente si fue un ensayo. | La concesion permanece revocada. No hay cuentas ni claves regeneradas por reiniciar. No probar con la unica autoridad disponible. |
| 4. Unidad receptora, area gestora | Asuncion propia de direccion.upd; en otro REQ solicitar colaboracion o derivacion hacia UPD. Destino acepta eligiendo Difusion y profesional.difusion. Resolver tareas del origen antes de derivar. | Seleccion de profesional del area, sin darle Direccion. Colaboracion crea Caso paralelo; derivacion cierra origen y crea sucesor; transferencia REQ no mueve Casos. |
| 5. In situ con evidencia | Como administrativo, registrar atencion resuelta, NNA sintetico, contacto seguro y un TXT inofensivo. Pulsar Preparar adjunto, seleccionarlo y confirmar ingreso. Recargar. | COM y REQ cerrado sin Caso; datos y archivo preservados. Adjunto en cuarentena, no disponible como archivo limpio. Una preparacion sin enviar no genera COM y solo la ve su autor. |
| 6. Antecedente rechazado | Distribucion vincula nueva COM a un REQ. Jefatura rechaza y elige Devolver a clasificacion con fundamento. Distribuidor la encamina al destino correcto. | Misma COM/folio; rechazo y retorno conservados. No reapertura ni segundo REQ por el mero vinculo. El anterior receptor no conserva acceso adicional a la COM por el vinculo retornado. |
| 7. Actuacion y version documental | Profesional registra actuacion, carga TXT de gestion asociado a ella, luego otro indicando version anterior. | Dos originales, relacion visible con actuacion y version. Original del requiriente sigue separado. No cambia firma ni se borra contenido previo; reserva se hereda si corresponde. |
| 8. Cierre y listados | Crear tarea pendiente e intentar cierre; observar explicacion previa. Resolver tarea, solicitar cierre y retirarlo. Abrir lista con filtros y paginar si hay mas de 25 registros. | Se explica bloqueo, se conserva retiro y posterior cierre explicito. Busqueda/filtros/cifras coherentes; recarga no pierde datos. |

## Comprobacion posterior en VM (no ejecutar sobre otra instancia)

Solo cuando el build y el recorrido local esten correctos: instalacion aislada MySQL/Java portable, Inicio y Estado muestran version/esquema; detener solo SIGER A, respaldar y ensayar restauracion en carpeta nueva fuera de la instancia. Ver `INSTALACION_VM.md`. La copia de restauracion no reemplaza la base activa.

No enviar CSV de credenciales, configuraciones DPAPI, dumps ni documentos al chat. Informar solo paso, resultado y primer error sanitizado.
