# SIGER A 0.4.0 - alcance y pendientes

## Incluido en el fuente

Monolito previo de COM/SOL/REQ/Caso, roles y organigrama, derivaciones/colaboraciones/transferencias, subrogancias, actuaciones, recordatorios, cierres/historial, documentos privados en cuarentena y dashboard por funciones. La fase A usa datos sinteticos.

Nuevo: primera atencion directa con COM+REQ desde ingreso; datos parciales general/NNA; ficha con versiones; entrega operativa directa al profesional o autoridad; aceptacion/devolucion/retiro; resultado inmediato sin Caso; alerta de seguimiento y apertura posterior por jefatura; bandejas propias; adjuntos durante primera atencion.

## Decisiones de implementacion acotadas

- Origen conserva el encargo hasta recibir la entrega. El destino habilitado puede leer la ficha para decidir, sin convertirse en jefe principal del REQ. Si cambia el principal se usa la transferencia formal.
- Una sola entrega pendiente, no entrega simultanea a varios profesionales. El bloqueo transaccional/idempotencia del monolito existente protege las operaciones.
- Mientras una entrega este pendiente no se complementa/cierra la ficha ni se inicia traslado formal: primero recibir, devolver o retirar. No se impide atender a la persona fuera del sistema por una incidencia informatica.
- Ficha editable mientras primera atencion abierta y sin Caso. Al abrir Caso se conserva como antecedente; las actuaciones posteriores se registran en el Caso. No es una agenda paralela ni edicion de originales.
- Campos sensibles voluntarios; lectura bajo los permisos actuales de atencion y expediente. No se declara implementada toda reserva excepcional por campo.
- Motivo breve requerido; contexto fecha/canal/ambito obligatorio, identidad del funcionario desde la sesion. Nombres, RUN, contacto, nacionalidad, personas y judiciales son opcionales. El formulario no autentica ciudadanos.
- Resolucion inmediata permite cerrar solo si no indica seguimiento y no hay entregas/decisiones pendientes. Compromisos escritos requieren revision humana; no se interpretan semanticamente ni crean tareas automaticas.
- Lo atendido directamente no usa SOL. Una COM externa por clasificar si usa SOL. Las filas antiguas no se convierten retrospectivamente por inferencia.

## Pendientes que NO se presentan como terminados

SMR/PULL/ACK y recepcion real (fase B); Graph y correo/SMS; antivirus/visor documental seguro; autorizaciones excepcionales completas y reserva especial; calendario normativo48h; informes oficiales definitivos; migracion historica; copias fuera del anfitrion y recuperacion productiva calificada; SSO/MFA si se solicita; primeras atenciones adicionales sobre un expediente cerrado sin reapertura autorizada; editar ficha estructurada despues de abrir Caso; guardado offline/autosave entre sesiones; nominas/cargos/territorios reales.

No usar datos personales reales antes de la calificacion institucional y de los pendientes de seguridad/documentos. La ficha conceptual no es un formulario aprobado. La fuente necesita build y pruebas reales locales.
