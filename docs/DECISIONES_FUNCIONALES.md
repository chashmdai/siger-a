# Base funcional de esta reconstruccion

Corte de decisiones: conversacion del 7 de noviembre de 2026 con el responsable del proyecto. Los documentos de octubre son referencias, no mandatos para reproducir todas sus decisiones tecnicas. Esta lista no acredita designaciones personales, produccion o cumplimiento completo.

1. COM es lo recibido. El ingreso externo por clasificar no crea por si solo REQ/Caso. Desde 0.4.0, iniciar atencion directa SI crea COM+REQ desde el primer registro.
2. Una SOL inicial a una jefatura; redirigir directamente o devolver a Distribucion conserva trazabilidad.
3. Al asumir SOL nace REQ aun sin Caso. Una jefatura puede asumir su ingreso propio.
4. Primera atencion directa crea COM + REQ abierto sin SOL/Caso; si se resuelve, registra resultado y termina ese REQ. No se exige jefatura para registrar o entregar a un profesional habilitado.
5. Seguimiento requiere Caso, lo gestione profesional o jefatura. Solo jefatura crea/asigna; varios objetivos pueden generar varios Casos del mismo REQ.
6. Complejidad por REQ: BAJA, MEDIANA, ALTA. Ausencia de clasificacion no significa BAJA. Jefatura reclasifica con fundamento e historial.
7. Responsable profesional puede cerrar Caso BAJA; para los demas niveles solicita revision de jefatura. Jefatura gestora puede cerrar su propio Caso con fundamento.
8. Subrogante no confirma su Caso propio por adquirir temporalmente jefatura; se mantiene la excepcion especifica ya aprobada.
9. Cerrar Caso no cierra REQ. Jefatura principal cierra expresamente, salvo la primera atencion resuelta. El profesional no cierra ordinariamente el REQ sin Caso.
10. Derivacion aceptada: sucesor y cierre del origen en la misma transaccion, modalidad DERIVACION, mismo REQ. Pendiente/rechazo/retiro no cierra origen.
11. Colaboracion aceptada crea Caso paralelo; no transfiere principal. Transferencia de REQ cambia solo principal, no Casos/profesionales/tareas.
12. COM posterior conserva folio y se vincula a REQ; si termino, se evalua nueva gestion, no se reabre al recibir.
13. Autoridad habilitada activa directamente al subrogante predesignado. No hay cadena obligatoria de solicitud, aprobacion y ejecucion TI. Ambito/periodo/retorno/revocacion permanecen.
14. Roles tienen ambitos. Coordinacion de Requerimientos no es toda UPR. Lectura de Direcciones por materia no otorga gestion regional. TI no obtiene expedientes por administrar cuentas.
15. Tareas y actuaciones son independientes; desde 0.2.0 toda tarea pendiente bloquea el cierre del Caso, conforme a la revision manual mas reciente. Reasignar profesional transfiere todas las tareas no terminales de ese Caso, no por subrogancia ni traslado de REQ.
16. PULL/ACK sera registro oficial y correo copia adicional: corresponde a fase B, no implementado aqui.

## Organigrama representado

Catalogo central UPR, UPD, UEP, UGI; areas UPR Requerimientos/NNA bajo cuidado del Estado/Representacion Judicial/Proteccion; UPD Educacion/Difusion/Participacion; UEP Estudios/Observatorio. Sede REG-DEMO para pruebas, sin inventar sedes reales. Gabinete, Defensoria, DAI y Auditoria no adquieren bandejas/permisos universales por figurar en el catalogo.

Los roles son concesiones en un ambito, no un frontend separado por persona. Titular y subrogante utilizan las mismas operaciones cuando tienen facultad vigente. La historia conserva al actor real.

## No se arrastran estas reglas antiguas

No se espera al primer Caso para crear REQ; no hay complejidad por Caso; no se exige revisor diferente en todos los cierres propios de jefatura; no hay cierre posterior extra tras aceptar derivacion; no se exige TI para activar cada subrogancia. Los restantes requisitos pendientes no se consideran eliminados: ver ALCANCE_Y_PENDIENTES.

## Precisacion 2026-11-08, SIGER A 0.3.0

La Distribucion indica **unidad obligatoria** y **area opcional**. La SOL de unidades centrales se presenta **siempre a su Direccion**, independientemente del area propuesta, excepto UPR-Requerimientos, cuyo receptor directo es la **Coordinadora de Requerimientos/Atencion de Personas**. No se crean jefaturas ficticias en Educacion, Difusion, Participacion, Proteccion, Representacion Judicial, Estudios u Observatorio. Las sedes regionales conservan su Coordinacion. La Direccion organiza profesionales y Casos dentro de su unidad sin otorgar jefatura por esa sola asignacion.

## Precisiones de implementacion 0.3.1

Se mantienen las ultimas decisiones de 0.2/0.3: toda tarea no terminal bloquea cierre; UPR-Requerimientos es la unica coordinacion central de area receptora; el resto de areas pasan por su Direccion; subrogante no autocierra su Caso propio. No cambian COM/SOL/REQ/Caso, cierre por derivacion, primera atencion ni cierre expreso del REQ.

Las preguntas de trabajo se concretan asi: (1) pendientes por rol; (2) autoridad efectiva titular/subrogante y area gestora; (3) bloqueos explicitos antes de cerrar; (4) evidencia conservada en actuaciones, archivos, versiones e historial.

Las metricas son conteos del alcance de quien consulta. No son cumplimiento de SLA ni un reporte institucional oficialmente aprobado. Fecha de tarea no es plazo normativo. Los campos de personas/contactos son opcionales y no autentican identidad. La version documental es una referencia entre originales, no firma ni visacion.

La reactivacion de un antecedente rechazado devuelve la COM a clasificacion con motivo y conserva la decision anterior; no reabre ni cambia el principal del REQ anterior.

## Precision 0.4.0 - primera atencion progresiva

La ultima decision del responsable distingue atencion ciudadana de recepcion externa. Administrativo, profesional o jefatura habilitados inician COM+REQ en un mismo acto, con datos parciales y motivo breve. Entregar el primer abordaje no crea un Caso ni transfiere principal; el destinatario puede ser profesional habilitado o funcion receptora. Origen mantiene encargo hasta recepcion, con devolucion/retiro trazados. Una vez detectado seguimiento la jefatura crea el Caso bajo el mismo REQ, sin exigir otra SOL.

Se usa una ficha comun con secciones general/NNA, relato separado de observaciones y acciones realizadas separadas de compromisos. No respuesta, dato faltante y respuesta negativa son distintos. Asentimiento para conversar no condiciona la existencia del REQ; identidad declarada no es identidad autenticada. No hay emojis ni protocolos de urgencia inventados. Autor/ambito se derivan de la sesion; no se declaran roles desde el formulario.

Prototipos HTML recibidos: referencias conceptuales exclusivamente. Los documentos de octubre y fichas Word ayudan a identificar datos, pero no prevalecen sobre las decisiones posteriores ni obligan a copiar pantallas, todos los campos o sus procedimientos antiguos. Las simplificaciones conservan seguridad e historia.
