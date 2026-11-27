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
13. TI activa directamente al subrogante predesignado (desde 0.5.0 reemplaza a la autoridad habilitada; ver Precision 0.5.0, D1), con el acto institucional como fundamento. No hay cadena obligatoria de solicitud, aprobacion y ejecucion. Ambito/periodo/retorno/revocacion permanecen.
14. Roles tienen ambitos. Coordinacion de Requerimientos no es toda UPR. Lectura de Direcciones por materia no otorga gestion regional. TI no obtiene expedientes por administrar cuentas ni combina su funcion con otras (Precision 0.5.0, D2).
15. Tareas y actuaciones son independientes; desde 0.2.0 toda tarea pendiente bloquea el cierre del Caso, conforme a la revision manual mas reciente. Reasignar profesional transfiere todas las tareas no terminales de ese Caso, no por subrogancia ni traslado de REQ.
16. PULL/ACK sera registro oficial y correo copia adicional: corresponde a fase B, no implementado aqui.

## Organigrama representado

Catalogo central UPR, UPD, UEP, UGI; areas UPR Requerimientos/NNA bajo cuidado del Estado/Representacion Judicial/Proteccion; UPD Educacion/Difusion/Participacion; UEP Estudios/Observatorio. Sedes regionales: un ambito por sede (unidad REG), con region del Formulario, estado PROYECTADA/ACTIVA/CERRADA y fecha de apertura; su unica cabeza es la Coordinacion regional, registrada como `DIRECCION` de la sede (Precision 0.5.0, D7-D9). Las sedes reales se cargan desde el archivo de estructura inicial o desde el panel de TI; la sede REG-DEMO (region sintetica 05) existe solo en los datos de prueba. Gabinete, Defensoria, DAI y Auditoria no adquieren bandejas/permisos universales por figurar en el catalogo.

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

## Precision 0.5.0 - administracion en TI, consulta institucional, buscador y reportes

Decisiones del responsable del proyecto del 2026-11-20 (plan en `docs/PLAN_0_5_0.md`). Se implementan por gates; cada una rige desde el gate que la completa (`docs/BITACORA_0_5_0.md`).

D1. **TI administra el sistema completo**: cuentas, claves, ambitos, concesion y revocacion de funciones, predesignacion, activacion y termino de subrogancias, calendario habil, liberacion de documentos e integraciones. **Se suprime el rol AUTORIDAD.** El acto institucional que respalda cada decision (resolucion, memo) se registra como fundamento obligatorio. Reemplaza en el punto 13 a la "autoridad habilitada" por TI; el resto del punto 13 (ambito, periodo, retorno, revocacion) se mantiene. El titular conserva la facultad de terminar su propia subrogancia. La instancia conserva siempre al menos una cuenta TI habilitada, vigente y sin fecha de termino. Al actualizar, las funciones AUTORIDAD vigentes se revocan una sola vez, sin borrar la historia, a nombre de la identidad tecnica `sistema:actualizacion`; las cuentas que ya combinaban TI con otras funciones no se corrigen solas: se avisan para que TI las resuelva. Si aun asi la instancia queda sin TI permanente, se recupera fuera de la interfaz con un procedimiento auditado y un acto institucional (`scripts/Recover-Ti.ps1`), nunca por una autoridad dentro del sistema. Una concesion no puede terminar en una fecha ya pasada.

D2. **TI no tiene funciones operativas ni de lectura.** Una cuenta con TI no puede tener ninguna otra funcion (ni operativa, ni consulta, reportes, exportacion o auditoria), y una cuenta con cualquier otra funcion no puede recibir TI. TI no se concede funciones a si misma. Se mantiene el punto 14: TI no obtiene acceso a expedientes por administrar cuentas.

D3. **Funciones combinables.** Una cuenta acumula funciones por ambito o institucionales mediante su perfil (por ejemplo Distribucion + Administrativo + Profesional). Los roles siguen siendo fijos, con sus reglas actuales; no se crean roles libres. Se admiten varios distribuidores simultaneos; una misma persona no recibe la misma funcion en periodos superpuestos. El perfil (`PROFILE_SET`) fija todas las funciones de una cuenta en un solo acto con un fundamento: conserva las que coinciden, revoca (sin borrar) las que faltan y otorga las nuevas; D2 se evalua sobre el resultado, de modo que el mismo acto puede reemplazar funciones operativas por TI. Una cuenta deshabilitada solo admite revocaciones.

D4. **Consulta institucional** (`CONSULTA_INSTITUCIONAL`): lectura completa de todas las COM, SOL, REQ, Casos, flujos y primeras atenciones, incluidas las fichas NNA y sus versiones, los originales externos del Formulario y los documentos no reservados. **Excluye las constancias y documentos marcados como reservados.** Nunca concede gestion. Toda consulta se audita.

D5. **Buscador de personas y folios.** Por RUN, nombre o folio, sobre los datos declarados de requirentes, adultos y NNA. Es un indice de coincidencias, no un registro maestro: no fusiona identidades ni autentica personas. Solo se indexan quien presenta o requiere, NNA, adulto relacionado, acompanante y curador/a (con su nombre de preferencia); nunca la persona senalada del Formulario ni "otra persona" de la COM manual o de la ficha (decision del responsable, gate G07b). `LECTURA` encuentra REQ y Casos por folio, no personas: no lee COM ni fichas de primera atencion. Para buscar personas se concede `CONSULTA_INSTITUCIONAL` (decision del responsable, registrada en el gate G09).

D6. **Reportes.** `REPORTES` (cifras agregadas por unidad o ambito, sin datos de personas), `REPORTES_PERSONAS` (desglose por funcionario; su concesion es una decision institucional) y `EXPORTACION` (filas sin datos ciudadanos). Los tiempos se informan en **horas corridas y en dias habiles** (lunes a viernes, excluidos los feriados del calendario que mantiene TI). Los feriados son nacionales o de una region (codigo del Formulario). Los dias habiles descuentan los feriados nacionales y los de la region de la sede responsable; en el nivel central, solo los nacionales. Los dias habiles solo se usan en reportes y antiguedad, no en plazos, alertas ni cierres, y cambiar esta regla requiere aprobacion expresa del responsable (precisado en 0.7.0, D22). TI los registra uno a uno o los importa por lineas (todo o nada), siempre con fundamento; retirar uno lo borra del calendario y la historia queda en la auditoria. No se siembran feriados en el codigo (gate G09). Alcance de `REPORTES` y `REPORTES_PERSONAS` (gate G11): nulo = toda la institucion; raiz de una unidad = sus ambitos centrales; area o sede = ese ambito; raiz de UPR, UPD o UEP = ademas lo de su materia en las sedes (como D9). `REPORTES_PERSONAS` exige `REPORTES` en el mismo alcance. TI no recibe reportes (D2). Una concesion sobre una sede cerrada deja de regir, pero los registros historicos de esa sede siguen en los reportes de institucion, unidad y materia. Los tiempos se calculan con el calendario habil vigente: agregar o retirar un feriado cambia tambien los calculos de periodos pasados. Cada tiempo informa cuantos de sus tramos fueron reconstruidos desde la historia anterior a 0.5.0. `EXPORTACION` (gate G12) exige `REPORTES` en el mismo alcance y tiene su mismo alcance: entrega en CSV una fila por REQ o por Caso creado en el periodo (folios, codigos de origen, ingreso, canal, unidad, ambito, nivel, region, materia, complejidad, modalidad, estado, fechas y tiempos en horas y dias habiles), sin nombres, RUN, relato, asunto, motivo ni contactos; si excede el limite se rechaza y nunca se entrega truncado, y cada exportacion se audita con el tipo, la cantidad de filas y los nombres de los filtros. El resumen de bandejas de 0.4.0 (folio, estado, ambito y fecha) tambien exige `EXPORTACION` y sigue acotado a lo que la cuenta lee. Todo CSV usa UTF-8 con BOM, punto y coma, coma decimal, fechas aaaa-mm-dd hh:mm en hora de Chile y el ambito por su codigo.

D7. **Sede regional = un ambito por sede** (no un ambito por sede y unidad). Su unica cabeza es la Coordinacion regional, con rango de Direccion: se registra como `DIRECCION` en el ambito de la sede. Las sedes no tienen `JEFATURA`. Sigue rigiendo una direccion por ambito. Al actualizar a 0.5.0, cada `JEFATURA` vigente o programada en una sede se revoca y se otorga `DIRECCION` con el mismo periodo (rutina auditada por `sistema:actualizacion`, una sola vez); las subrogancias vigentes pasan a la nueva concesion. Si ya hay una `DIRECCION` superpuesta en la sede, la `JEFATURA` no se convierte y queda como aviso para TI. El rechazo de `JEFATURA` en sedes aplica a concesiones nuevas: una no convertida que se conserva en el perfil no impide guardarlo.

D8. **Ciclo de vida de la sede.** Cada sede tiene region (codigo de region como en el Formulario), estado `PROYECTADA`, `ACTIVA` o `CERRADA` y fecha de apertura. Una sede proyectada no aparece como destino, pero TI puede preparar su dotacion (cuentas y funciones que rigen al activarse). Cerrar exige que no queden REQ ni Casos abiertos ni SOL pendientes en la sede, ni COM sin encaminar, SOL devueltas, peticiones entre unidades, entregas, reaperturas o vinculaciones pendientes, ni funciones vigentes o programadas, ni cuentas con adscripcion principal en ella. Una sede cerrada no recibe destinos, cuentas ni funciones, pero sus expedientes siguen legibles para quien tenga lectura. Puede haber mas de una sede activa en una region. Los ambitos deshabilitados de versiones anteriores quedan `CERRADA`. TI administra las sedes (`SCOPE_CREATE`, `SCOPE_UPDATE`, `SCOPE_ACTIVATE`, `SCOPE_CLOSE`). No se siembran sedes reales en el codigo ni en migraciones: se cargan desde el archivo de estructura inicial o desde el panel de TI.

D9. **Lectura nacional por materia.** Las Direcciones nacionales de UPR, UPD y UEP leen, a nivel nacional y sin gestionar, todo lo de su materia en las sedes: las SOL con esa materia sugerida, los REQ con esa materia y los Casos con esa materia. Es la misma lectura de la consulta institucional (D4), acotada a la materia y con lo reservado excluido. La gestion regional sigue en la Coordinacion regional (punto 14). Rige desde el gate R02.
   - Materia: al distribuir o redirigir una SOL a una sede se puede indicar una materia sugerida (UPR, UPD o UEP; nunca a una unidad central, y redirigir la reemplaza). El REQ la hereda al asumirse, y la Coordinacion regional la registra o rectifica con fundamento (`REQ_MATTER`, solo en sedes). La materia del Caso sigue aparte (`CASE_MATTER`); al abrir un Caso se propone la del REQ.
   - Alcance: las materias salen de las `DIRECCION` vigentes, propias o subrogadas, en el ambito raiz central de UPR, UPD o UEP (la subrogancia traslada la lectura). Una sede cerrada sigue legible. Por la materia de la SOL o del REQ se lee la cadena completa: la COM y sus originales, la primera atencion (NNA y versiones), el registro del REQ y sus flujos. Por la materia de un Caso solo se lee ese Caso (constancias y documentos no reservados) y el resumen de su REQ, sin la COM, sus originales ni la ficha de primera atencion. Todo lo legible figura tambien en las bandejas de la Direccion, y la ficha avisa "Lectura nacional por materia".

Precisiones de cierre de 0.5.0 (gates G13 y G14, decididas por el responsable):
- **Ingreso sin distinguir mayusculas** (G13): el usuario se guarda en minusculas (como siempre lo hizo `STAFF_CREATE`) y el ingreso pasa lo escrito a minusculas y exige coincidencia exacta, con un solo cupo de intentos por usuario. `TI` entra como `ti`; `tí` no entra. No hay comando para renombrar una cuenta.
- **Descargas con nombre neutro** (G14): un documento se descarga como `<FOLIO>-documento-<id>.<ext>` (folio del registro, numero del documento y la extension del original solo si es alfanumerica de 1 a 8 caracteres). El nombre original, que puede contener el de una persona, sigue visible en la ficha autorizada, pero no queda en el historial de descargas del navegador.
- **Hitos omitidos** (G14): el aviso de Administracion por eventos historicos que la carga inicial no reconocio es permanente y no se descarta; se documenta en `docs/ACTUALIZACION_0_5_0.md`. Produccion se instala limpia y no tiene historia que reconstruir.
- El consumidor PULL/ACK del punto 16 existe desde 0.4.0 y sigue inactivo hasta configurarlo.

## Decision para 0.6.0 - produccion y fin de la validacion

Decision del responsable del proyecto del 2026-11-21 (gate G14b): **produccion sera una instancia nueva inicializada en
modo institucional** (`Init-Server.ps1 -Mode Institucional -Proposito Produccion`), no una migracion que borre datos.
La instancia de validacion se cierra con `Close-Validation.ps1` y luego **se elimina por completo** (base, documentos y
respaldos), dejando constancia documentada de la eliminacion. Fecha objetivo: antes del 15/12/2026, fecha en que el
Formulario Publico deja el acceso temporal con contrasena y queda abierto al publico; hasta que produccion consuma, el
consumidor de la validacion se pausa para que ninguna entrega ciudadana real llegue a ella. La validacion contiene 9
recepciones reales del Formulario, que son pruebas del responsable con sus propios datos y con autorizacion.

## Precision 0.6.0 - salida a produccion y fin de la validacion

Decisiones del responsable del proyecto del 2026-11-21 (plan en `docs/PLAN_0_6_0.md`); precisan la "Decision para 0.6.0" anterior. Puertos y fechas confirmados por el responsable el 2026-11-21; el mismo dia el responsable decidio el **salto directo** (la validacion no se actualiza), que revisa D10, D11 y D14. Los gates nombrados (H01, H02...) y los pasos de la VM (V1-V6 y el plan B) son los de ese plan; las decisiones abiertas siguen en el plan y en `docs/BITACORA_0_6_0.md`.

D10. **Produccion reemplaza a la validacion; no conviven.** Produccion es una instancia nueva en `D:\SIGER` (modo institucional, proposito Produccion), inicializada con el paquete 0.6.0 sin `-MismoReleaseQue` (la validacion sigue en 0.4.0; el ensayo de H06b reemplaza esa garantia), con la misma cuenta de servicio (`DDN-SIGER\svc-siger`) y los mismos puertos (MySQL 13326 en loopback, HTTPS 18543). La secuencia es la de la seccion 3 de `docs/PLAN_0_6_0.md`: la validacion se cierra y se retiran sus servicios antes de inicializar produccion, y se elimina despues de acreditar produccion.

D11. **Recepciones de prueba autorizadas.** Una validacion con recepciones del Formulario se cierra solo si se cumplen las tres condiciones:
1. las recepciones confirmadas coinciden con la cantidad que el responsable declara al pausar definitivamente (9);
2. ninguna recepcion es posterior a esa pausa;
3. no hay ACK pendientes.
Si una falla, no se cierra. El cierre no borra nada (la eliminacion es D12). Una validacion sin recepciones se cierra como hasta ahora.

D12. **Eliminacion de la validacion cerrada, con constancia.**
- Alcance: dentro de `D:\SIGER-VALIDACION` (base, incluidos los esquemas `siger_a_restore_*` de los ensayos de restauracion, documentos, logs, `config\` con los secretos DPAPI y `releases\`); fuera de la raiz, `D:\Respaldos-SIGER-VALIDACION` y los destinos de `Restore-Check.ps1` de esa instancia.
- Nunca: el `mysqlHome` (compartido con produccion), la raiz de produccion ni nada fuera de `D:`.
- Constancia en `D:\SIGER-CONSTANCIAS` (fuera de ambas instancias) y en el registro institucional, sin datos ciudadanos ni secretos. Declara como fuera de alcance los respaldos y snapshots de TI (sujetos a su retencion) y la copia que conserva el Formulario, y aclara que la eliminacion es logica, no forense.

D13. **Acreditacion de produccion.** Solo con `Invoke-FormularioPull.ps1 -SoloComprobar` y produccion en automatico a mas tardar el **10/12/2026**, verificando en Auditoria y operacion "Esperando entregas", ciclos corriendo y ultimo exito reciente (un ciclo en pausa tambien registra exito: lo que cuenta es el estado). No hay entregas de prueba en produccion. **Plan B**: si al **12/12/2026** la validacion todavia no se ha pausado ni cerrado (V1-V2), TI pausa su consumidor en Auditoria y operacion, se deja en Manual con el `Set-Integration.ps1` de la propia instancia 0.4.0 y se retira `validacionPullAutorizada` de su `instance.json`. Si ya esta cerrada y produccion no consume, no hay nada que hacer en la validacion: las entregas esperan en la cola del Formulario hasta completar V4.

D14. **La validacion no se actualiza.** Sigue en 0.4.0 (esquema V10) hasta su cierre; los scripts de cierre y eliminacion de 0.6.0 se ejecutan desde la carpeta del paquete sobre esa instancia. No hay entrega de prueba en la VM con el codigo de 0.5.0: la primera recepcion con ese codigo ocurre en produccion, y si falla, el consumidor no confirma el ACK y la entrega queda en la cola del Formulario. Entre la pausa de la validacion y produccion en automatico no se hacen entregas de prueba.

D15. **Alcance del trabajo en la VM.** Solo NEW-SIGER dentro de `D:`. No se inspeccionan ni modifican otros volumenes ni el sistema legado (SMR). Ningun procedimiento de 0.6.0 depende de SMR: `Export-SecretPackage.ps1` no se usa. Precision (H04a, 2026-11-21): consultar el estado del servicio de Windows `SMRRecepcion` desde `Set-Integration.ps1` no es inspeccionar el sistema legado; no se leen archivos, configuracion ni base de SMR, y no se cambia nada.

D16. **Correo.** Graph no bloquea la salida a produccion: produccion puede partir con el correo desactivado. La credencial de Graph se instala en la VM como la cuenta de servicio, sin SMR (H01).

D17. **Liberacion por analisis en origen (modo de emergencia).** Mientras la VM no tenga un motor antivirus admitido por SIGER (Defender inactivo; Kaspersky KES 14 no integrado; sin ClamAV), los originales recibidos del Formulario se liberan de forma automatica e inmediata al registrarse, sin revision manual. La base es el analisis que el Formulario hace antes de aceptar la entrega (ClamAV e inspector aislado) y la verificacion de SIGER de la firma Ed25519 de la entrega y del SHA-256 de cada original. Solo aplica a los originales del Formulario: los adjuntos subidos en SIGER siguen en cuarentena. Los originales del Formulario que quedaron en cuarentena mientras el modo no regia se liberan al reactivarlo, con la misma verificacion. El modo se activa de forma expresa en la instancia y queda auditado; rige hasta que se configure un motor antivirus admitido o se desactive de forma expresa; la revision periodica queda a cargo del responsable, segun el acta institucional; cada liberacion se audita con el motivo 'analisis en origen del Formulario'. Cuando se configure un motor admitido, el modo se desactiva y vuelve a regir el analisis local. Riesgo residual aceptado por el responsable: amenazas que ClamAV no conocia al momento de la subida. Capas compensatorias: descarga como adjunto con CSP sandbox y nombre neutro, y Kaspersky en la VM y en los equipos. Decision del responsable del 2026-11-21, con correo y constancia institucional.

Excepcion a "los scripts no incluyen comandos destructivos" (H03, 2026-11-21): `scripts/Remove-Validation.ps1` es el unico procedimiento que borra datos, y solo aplica D12. Actua sobre una VALIDACION cerrada por `Close-Validation.ps1` de 0.6.0 (marcador con `kind` CIERRE_VALIDACION y su copia identica en las constancias), con servicios retirados, motor detenido, ningun archivo en uso ni enlace, y una produccion inicializada de la misma cuenta que ya tiene el mismo origen e identidad ACK del Formulario en AUTOMATICO y la credencial de Graph si la validacion la tenia (las unicas copias de esas credenciales en NEW-SIGER estan en la validacion y sus respaldos). Borra solo rutas de `D:` fuera de produccion, de los motores, de Java, de las constancias y del paquete: la raiz, los respaldos con `backup.json` de esa raiz y base, los respaldos abortados que declara el responsable y los ensayos de restauracion hechos sobre su motor. Exige confirmacion de PowerShell y escribir la ruta de la raiz, escribe la constancia antes de borrar y la completa por elemento; ante un fallo se detiene y se repite con lo que queda. Ningun otro script borra datos de una instancia.

## Precision 0.7.0 - cobertura territorial y region de origen

Decisiones del responsable del proyecto del 2026-11-23 (plan en `docs/PLAN_0_7_0.md`, registro en `docs/BITACORA_0_7_0.md`), copiadas en el gate T00. Se implementan por gates (T01-T04); los escenarios de aceptacion 1-7 estan en la seccion 0 del plan. D22 precisa D6.

D18. **Cobertura territorial de las sedes.** Cada sede regional conserva su region fisica (`org_scope.region_code`), que
siempre cubre y que no se quita, y puede cubrir ademas otras regiones del catalogo del Formulario (cobertura adicional).
Solo las sedes regionales tienen region fisica y cobertura adicional; la cobertura adicional no repite la region fisica
ni una region dos veces. Una region puede quedar cubierta por mas de una sede (como D8 ya admite varias sedes en una
region). La cobertura de una sede PROYECTADA queda preparada y rige al activarse; una sede CERRADA no tiene cobertura
vigente.
   - **Nivel central** (precision del responsable, 2026-11-23): la Region Metropolitana (13) la atiende la Oficina
     Nacional, sin sede regional. TI declara las regiones que atiende el nivel central ("cobertura del nivel central"),
     con el mismo mecanismo de vigencia, fundamento y auditoria de la cobertura de una sede. No se siembra ninguna region
     en el codigo ni en migraciones. La cobertura del nivel central no agrega destinos al selector (D20): solo cuenta como
     region atendida en el aviso `REGION_SIN_SEDE` y en el efecto territorial (D19).

D19. **Administracion, historia y efecto territorial.**
   - TI administra desde la interfaz la cobertura adicional de cada sede y la del nivel central, con fundamento,
     concurrencia optimista y auditoria (cobertura anterior -> nueva, solo con codigos de region). Cada region cubierta es
     un periodo con inicio y termino; terminar no borra: la historia queda consultable en Estructura.
   - Archivo de estructura inicial: `"cobertura": ["03"]` opcional en cada sede regional y `"coberturaNivelCentral":
     ["13"]` opcional en la raiz, con las mismas reglas que los comandos. Un archivo sin esas claves sigue valido. 0.6.0
     ignora esas claves sin aviso: si V4 vuelve a 0.6.0, se retiran del archivo.
   - Efecto territorial: para cada region que hoy atiende la sede (primero la region fisica, luego la cobertura
     adicional) o el nivel central, el servidor informa "sigue atendida por <sedes ACTIVA y/o nivel central>" o "queda
     sin atencion". Las sedes PROYECTADA se informan solo como dato (no reciben). Se calcula en `SCOPE_CLOSE`, en el
     cambio de cobertura de una sede o del nivel central y en `SCOPE_UPDATE` cuando cambia la region fisica.
   - Si alguna region queda sin atencion, la orden exige `regiones_sin_sede` igual a la lista que calcula el servidor (si
     difiere, 409: la situacion cambio) y su fundamento registra la decision institucional. Sin regiones afectadas basta
     el fundamento de siempre. La auditoria del acto registra el efecto (solo codigos de region y de sede).
   - Al cerrar una sede, su cobertura adicional termina en el mismo acto. El efecto territorial no es un bloqueo de
     `Personnel.closeBlockers` (dejaria Cerrar siempre deshabilitado): Estructura lo muestra antes de confirmar. Los
     bloqueos de cierre de D8 no cambian.
   - Aviso permanente `REGION_SIN_SEDE` en Administracion: regiones del catalogo que hoy no atiende ninguna sede ACTIVA
     (por region fisica o cobertura adicional vigente) ni el nivel central (cobertura del nivel central vigente). Con 13
     declarada como del nivel central, 13 no genera aviso.

D20. **Selector de destino.** Una sede aparece bajo su region fisica y bajo cada region de su cobertura adicional
vigente. La propuesta no cambia de regla: una sola sede que atiende la region queda preseleccionada; con varias, solo la
region; sin sede, el nivel central, como hoy (si el nivel central atiende esa region, el selector lo indica con un aviso
y no propone unidad). Si una sede y el nivel central atienden la misma region, rige la propuesta de sede. El servidor no
valida la region del destino: el distribuidor conserva la decision. No hay distribucion automatica. La propuesta usa la
region vigente (D21).

D21. **Region de origen de la persona, con trazabilidad.**
   - Fuentes existentes e inmutables: en una COM del Formulario, la proyeccion (`external_field` `nna-region` y, si falta
     o no es valida, `adult-region`), que no cambia despues de crear la COM; en una atencion directa, las versiones de la
     ficha (`attention_revision`, con autor, fecha y motivo). Una COM del Formulario no tiene ficha. Una COM manual no
     tiene region.
   - Region registrada: la del Formulario o, en una atencion directa, la primera region valida que registro la ficha
     (completar una ficha que no tenia region no es una correccion). Region vigente: la de la version actual de la ficha
     (en el Formulario, igual a la registrada). Correccion: region vigente distinta de una region ya registrada. Sin
     region: "sin region declarada".
   - Mecanismo: V16 agrega una columna derivada con la region de cada version de la ficha (`attention_revision`),
     escrita al crear la version desde sus propios datos y reconstruible con un marcador (patron de `PersonIndex` y
     `Milestones`). No cambia la recepcion del Formulario (seccion 2 de `docs/PLAN_0_7_0.md`).
   - Reportes: filtran y agrupan por la region registrada (estable: una correccion posterior no cambia las cifras
     historicas) e informan cuantos registros tienen la region corregida. La region que hoy informan pasa a rotularse
     "region de la sede". Exportacion: region registrada, region vigente y region de la sede. La ficha muestra la region
     registrada cuando difiere de la vigente. `FICHA_COMPLEMENTADA` agrega "Region de origen modificada", sin codigos:
     los valores quedan en las versiones de la ficha, con su lectura protegida.

D22. **Feriados regionales (sin cambio de regla).** Los dias habiles descuentan los feriados nacionales y los de la region
de la sede responsable; en el nivel central, solo los nacionales (regla vigente: `Reports.java:206` y `:296-297`). Se
escribe asi en D6 y se fija con pruebas. Alcance: los dias habiles solo se usan en reportes y antiguedad
(el calculo de dias habiles (`businessDays`) solo esta en `Reports`; `Csv` y `Holidays` usan solo sus constantes), no en
plazos, alertas ni cierres. Usar la region de origen cambiaria tambien el nivel central y dependeria de datos declarados
que pueden faltar o corregirse. Cualquier cambio de esta regla requiere aprobacion expresa del responsable.

D23 se agrego despues del cierre de T03 (decision del responsable del 2026-11-23) y se copio en el gate T03b; su escenario de aceptacion es el 8 de la seccion 0 del plan.

D23. **Marca territorial y de riesgo en la bandeja de comunicaciones** (decision del responsable, 2026-11-23, despues del
cierre de T03; se implementa en T03b). La lista de Comunicaciones (todas sus vistas) y la cola "Ingresos por distribuir"
de Inicio muestran una marca en cada COM:
   - Numero: la region de origen vigente de la COM (D21; la misma regla del selector, `OriginRegion.current`) en numero
     romano segun su codigo del Formulario (01 = I ... 12 = XII, 14 = XIV, 15 = XV, 16 = XVI) y "RM" para 13. Sin region
     declarada, la marca no lleva numero.
   - Color: uno solo y distintivo cuando esa region la atiende hoy una sede regional ACTIVA, por region fisica o por
     cobertura vigente (la regla de D18-D20 con la cobertura del catalogo); neutro si solo la atiende el nivel central o
     nadie. El texto completo (nombre de la region y sede o sedes que la atienden, o "nivel central") va como titulo
     emergente y texto para lectores de pantalla.
   - Riesgo: si la COM del Formulario trae el campo `riesgo` afirmativo (`si` o `true`, sin distinguir mayusculas; es la
     pregunta "NNA en riesgo inminente" del Formulario, rotulada "Riesgo actual" en la ficha COM), la marca es roja en vez
     del color regional, tambien sin region o en la RM (sin region dice "Riesgo"). Mientras la COM esta por distribuir
     (la misma condicion del filtro "Por distribuir" de la lista), la marca roja tiene una animacion suave de respiracion
     (breathing glow) con un ciclo de 5 a 6 segundos, liviana: solo opacidad o sombra, sin mover el diseno de la fila ni
     recalcular la pagina. Distribuida, queda roja fija. Con `prefers-reduced-motion` no hay animacion. Siempre lleva
     icono y el texto accesible "Riesgo actual informado": nunca solo color.
   - Solo presentacion: no cambia quien ve que, los filtros, el orden de la lista, el estado, la complejidad ni la
     distribucion, y no activa protocolos. La distribucion sigue manual (D20).
   - Fuera de 0.7.0 por decision del responsable: la distribucion territorial del Formulario (que la distribucion de una
     sede vea y distribuya solo las COM de su territorio) pasa a 0.8.0 con decision propia; el orden de prioridad y los
     filtros nuevos en "Por distribuir" no se hacen.

D24 se agrego despues de T05d (decision del responsable del 2026-11-23) y se copio en el gate T03c; su escenario de aceptacion es el 9 de la seccion 0 del plan.

D24. **Aviso a distribucion de las COM del Formulario** (decision del responsable, 2026-11-23, despues de T05d; se
implementa en T03c). Cuando una COM del Formulario queda registrada y por distribuir, SIGER avisa una sola vez a quienes
pueden distribuirla (`Access.notifyDistributors`; hoy la funcion `DISTRIBUIDOR`, porque su ambito de origen es el
receptor central), por la via de avisos que ya existe: "Mis avisos" y, con el correo Graph activo (D16), su correo por
`MailOutbox`.
   - Sin tocar la recepcion del Formulario (seccion 2): lo hace una tarea periodica aparte, como `TaskReminders` (cada
     60 segundos, con el lock global), que busca las COM con origen en el Formulario que siguen por distribuir y aun no
     tienen este aviso.
   - Una sola vez por COM: nunca dos avisos de llegada para la misma COM, tampoco despues de un reinicio. Si en ese
     momento nadie puede distribuir, el aviso no se pierde: sale cuando haya quien pueda y la COM siga por distribuir.
     Una COM ya distribuida no se avisa.
   - No cambia los otros avisos: la COM manual ya avisa al crearse (`IntakeCommands.java:65`); la SOL devuelta y la COM
     rechazada como antecedente siguen igual.
   - Texto sin datos de personas ni del relato: "Hay un ingreso del Formulario pendiente de distribucion." y, si la COM
     trae `riesgo` afirmativo (la regla de D23: la pregunta "NNA en riesgo inminente" del Formulario), "Hay un ingreso
     del Formulario con NNA en riesgo inminente, pendiente de distribucion."
   - Preferentemente sin migracion. Si la marca de "ya avisado" exige una, sera V17 aditiva, justificada en el plan del
     gate y aprobada en el GO.
   - Graph: sin prueba real previa en TEST-SIGER (decision del responsable): el correo se activa en V4 segun D16 y, si
     falla, se corrige en produccion.

## Precision 0.8.0 - normalizacion integral e inteligencia de negocios para Estudios

Copia textual de las decisiones D25-D51 de `docs/PLAN_0_8_0.md` (seccion 0), aprobadas por el responsable el
2026-11-24, con sus precisiones del plan, las respuestas DI-05 a DI-10 y los ajustes de aprobacion (gate E00). Las
definiciones institucionales abiertas (DI-xx) y las respuestas posteriores estan en `docs/BITACORA_0_8_0.md`; el
diseno de referencia, en `docs/DISENO_0_8_0.md`.

D25. **Unidad estadistica del requerimiento recibido** (DP-01). La COM es la unidad estadistica del requerimiento
recibido, exista o no un REQ. La cadena COM, SOL, REQ y Caso se cuenta una sola vez. Las COM invalidas se excluyen, las
duplicadas se cuentan y las vinculadas como antecedente no se cuentan como requerimientos nuevos. Se incluyen las
pendientes y las primeras atenciones. Reaperturas, transferencias y Casos adicionales no multiplican el conteo.
   - Precision del plan: computable = estado distinto de INVALIDA y de VINCULADA; una COM cuyo vinculo se devolvio
     vuelve a NUEVA y vuelve a contar (`DISENO` 4.1). Las excluidas y los antecedentes se informan aparte, nunca se
     pierden.

D26. **Fechas y periodos** (DP-02). `received_ms` para la demanda institucional, `created_ms` para el registro
administrativo y la fecha de creacion del REQ para el analisis operacional, en America/Santiago. Periodos
personalizados y predefinidos: Cuenta Publica (julio a junio), anio calendario, semestre, trimestre y mes. Se
identifican los registros tardios. Los reportes operacionales existentes se conservan.
   - Precision del plan: registro tardio = recibido en el periodo y registrado despues de su fin (`DISENO` 5.5).

D27. **Catalogos estadisticos versionados.** Precision del plan para D29-D31 y D34-D36: cada catalogo (tipo de
solicitud, requirente, via, contexto, caracterizacion, derecho, tipo de actuacion, tipo de institucion, relacion) es un
dato versionado con estado PROPUESTA, VIGENTE o RETIRADA, fuente y fundamento; TI lo importa y activa (D1), su
contenido lo valida la institucion; ninguna migracion lo siembra; las correspondencias entre versiones permiten
comparar anios. Cada clasificacion guarda la version del catalogo con que se registro (`DISENO` 3.1).

D28. **Captura progresiva, estados y completitud** (DP-12). Captura progresiva sin bloquear ingresos ni atencion
urgente. Las dimensiones estadisticas pertinentes deben estar resueltas al cierre. Estados: pendiente, sin informacion,
no declarado, no aplica e informado; el estado pendiente impide el cierre. No se obliga a inventar datos. Indicadores de
calidad y completitud por variable, ambito y periodo.
   - Precision del plan: variables pertinentes por tipo de cierre en `DISENO` 4.5; una variable solo es exigible si su
     catalogo tiene version VIGENTE; una propuesta no es un estado: deja la variable pendiente hasta su confirmacion;
     una sola regla (`StatCompleteness`) para los bloqueos que se muestran y los que se validan.

D29. **Tipo de solicitud** (DP-04). Clasificacion institucional: vulneracion, consulta u orientacion, promocion,
reclamo sobre otra institucion, otro y valores especiales. Catalogo versionado sujeto a validacion institucional. Los
datos estructurados del Formulario son propuesta, que un funcionario confirma o corrige. Se conserva el dato original.
   - Precision del plan: reglas de propuesta version 1 en `DISENO` 4.2 (la COM NNA no tiene propuesta).

D30. **Tipo de requirente y roles** (DP-05). Clasificacion independiente: persona natural adulta, NNA, persona
juridica u organismo, actuacion de oficio y valores especiales. Se distinguen presentador, requirente, representacion,
modalidad de atencion y persona afectada.
   - Precision del plan: equivalencias de roles en `DISENO` 2.3; los codigos historicos de roles no se reescriben.

D31. **Via de ingreso** (DP-06). Formulario Publico adulto, Formulario Publico NNA, presencial, correo, telefono,
oficio, redes sociales, integrante de la Defensoria y otro. Agrupaciones estadisticas sin perder el canal original. Se
diferencian via, modalidad de atencion, tipo de requirente y medio de contacto. Correcciones auditadas.
   - Precision del plan: canales manuales nuevos `REDES_SOCIALES` e `INTEGRANTE_DEFENSORIA`; la via se deriva del canal
     (estado informado, origen sistema) y una correccion es un evento con motivo; el canal original no cambia.

D32. **Territorio multidimensional** (DP-07). Region y comuna del requirente, del NNA afectado y de los hechos;
sede responsable inicial y vigente; cobertura territorial historica; region de origen registrada. No se confunden
estas dimensiones ni se modifican inadvertidamente las reglas territoriales vigentes.
   - Precision del plan: fuentes por dimension en `DISENO` 4.6. D18-D24 no cambian, salvo una precision de D21
     aprobada aqui: una COM manual tiene region de origen si la registra su ficha de ingreso (D33). D22 no cambia.
   - DI-08 (2026-11-24): el rotulo de la region de la ficha es "Region y comuna del requirente", sin modificar D21.

D33. **Normalizacion integral de la captura** (DP-08 y condicion transversal). El Formulario y las fichas de SIGER son
referencias complementarias; la ficha presencial conserva integramente su informacion institucional. Un mismo dato
tiene la misma estructura y significado en todos los mecanismos. Region y comuna estructuradas en la ficha NNA y en la
COM manual; normalizacion de identidad, contacto, personas relacionadas e instituciones; reutilizacion de componentes
y validaciones; se conservan los campos exclusivos, la captura progresiva, los datos historicos y su procedencia; no se
inventa informacion ni se dividen nombres historicos; se distingue complementacion de rectificacion; no se duplican
fuentes de verdad. No se modifican los originales firmados, contratos ni protocolos de integracion del Formulario.
   - Precision del plan: matriz en `DISENO` 2.2; esquema canonico `persona-1` (2.4); ficha 1.2 (3.4); ficha de ingreso
     de la COM manual (3.5); resumen de cambios COMPLEMENTO/RECTIFICACION calculado por el servidor (2.5).

D34. **Derechos, contexto y caracterizacion** (DP-09). Clasificacion oficial de derechos de la Defensoria segun su
version vigente, con categorias, subcategorias y dimensiones; sin inventar categorias ni suponer 24 derechos. Se
distinguen contexto, derechos afectados y caracterizacion de la vulneracion. Seleccion multiple, busqueda, navegacion
jerarquica, clasificacion progresiva e historial. Componente reutilizable: en `RequirementDetail.tsx` despues de "Datos
del REQ", en `CaseDetail.tsx` despues de "Datos del Caso", accesible desde "Registrar actuacion" y desde la primera
atencion. Clasifican profesionales, integrantes autorizados, jefaturas, direcciones y coordinaciones regionales dentro
de sus facultades efectivas de gestion, sin rol nuevo, respetando ambito y lectura institucional. Los derechos se
consolidan entre REQ y Casos sin duplicar conteos ni exponer informacion no autorizada.
   - Precision del plan: version vigente identificada = "Modelo de conceptualizacion y categorizacion de derechos de la
     ninez y adolescencia", version 2.0 (2022): 4 ejes, 17 derechos y sus subcategorias (`DISENO` 11), los mismos 17 del
     Informe Anual 2026; confirmacion institucional en DI-04. Reglas de clasificacion y consolidacion en `DISENO` 4.3 y
     4.4.

D35. **Inmutabilidad de los expedientes cerrados** (DP-09 y DP-15). Un expediente cerrado es inmutable; cualquier
modificacion requiere reapertura formal, sin excepciones para jefaturas, direcciones, UEP o TI.
   - Precision del plan: rige para toda clasificacion, actuacion y ficha; las excepciones actuales que no modifican el
     expediente (`LINK_DECIDE` sobre un REQ cerrado y `CASE_ACK`) se documentan y se prueban (`DISENO` 1.2, S2). La COM
     descartada no tiene reapertura: su clasificacion queda como se registro al descartarla.

D36. **Actuaciones y respuestas institucionales** (DP-10). Se actualizan los mecanismos existentes de actuaciones; no
se crea un modulo paralelo. Tipificacion estructurada de orientaciones, respuestas directas, derivaciones externas,
oficios, denuncias administrativas y penales, medidas de proteccion, querellas, acciones constitucionales, amicus curiae
y otras actuaciones; fecha efectiva, autor, ambito e instituciones cuando corresponda; se conservan constancias,
documentos, reserva e historial; se distinguen acciones realizadas de compromisos; se cuentan actuaciones y
requerimientos unicos; no se desarrolla un sistema paralelo de seguimiento judicial.
   - Precision del plan: `management_note.action_type`, `note_institution`, `task_item.action_type` (compromiso) y
     resultado estructurado de la primera atencion (`DISENO` 3.3).

D37. **Dimensiones temporales de analisis** (DP-11). Analisis segun las dimensiones al ingreso de la COM, al
constituirse el REQ, vigentes, a una fecha historica y segun cortes estadisticos, sin reconstruir informacion historica
sin evidencia (`DISENO` 4.7).

D38. **Motor estadistico nativo y explorador** (DP-13). Motor estadistico propio en Java y MySQL; SIGER es la
herramienta principal de analisis institucional, sin depender de Excel ni Power BI. Explorador multidimensional
intuitivo para Estudios: filtros combinables, cruces, tablas dinamicas, matrices, comparaciones temporales, series
historicas, frecuencias, porcentajes, graficos y configuraciones reutilizables. Pantalla, exportacion y cortes comparten
una unica metodologia de calculo; agregacion eficiente en SQL; metodologia, permisos y validaciones en Java.
   - Precision del plan: fuente derivada unica por COM sin tabla materializada para lo vigente, objetivo de rendimiento
     y alternativa con decision (`DISENO` 5).
   - Ajuste de aprobacion: edad (en anios y por tramos) y nacionalidad (pais y agrupacion) del NNA afectado y del
     requirente persona natural son dimensiones estadisticas, calculadas solo desde datos estructurados; sin informacion
     suficiente, la categoria es "sin informacion" y el explorador muestra el porcentaje con dato (`DISENO` 4.8).
   - Ajuste de aprobacion: graficos de barras, barras apiladas, lineas y circulares; el circular solo para una
     distribucion de valor unico cuyas partes suman el total (`DISENO` 10.3).

D39. **Cortes estadisticos** (DP-03). Inmutables, versionados, auditados y reproducibles, con fundamento, metodologia,
catalogos, calendario, filtros, metadatos y SHA-256; comparables con datos vigentes y con otros cortes. Destinataria
principal: la Direccion de UEP, en especial Estudios. Se respetan las facultades existentes y se definen las necesarias
para generarlos (D43).

D40. **Exportaciones** (DP-13). CSV, XLSX y ZIP con agregados, microdatos estadisticos autorizados, diccionarios,
catalogos, metadatos y huellas de integridad; exportacion de graficos y tablas. Las dependencias nuevas se evaluan antes
de pedir autorizacion.
   - Precision del plan: XLSX y ZIP con la JDK; sin dependencias nuevas salvo autorizacion (DI-12); contenido del
     paquete en `DISENO` 8.
   - Ajuste de aprobacion: las dimensiones multivaluadas (derechos, contexto, caracterizacion, actuaciones,
     instituciones y datos de varios NNA afectados) se exportan sin duplicar requerimientos ni perder valores: el
     microdato tiene una fila por requerimiento y un archivo relacionado con una fila por requerimiento y valor; el
     agregado distingue requerimientos con el valor, menciones y total de requerimientos unicos (`DISENO` 4.4 y 8).

D41. **Cifras exactas y proteccion de datos** (DP-14). Se reportan las cifras exactas, incluso categorias con un solo
requerimiento. No hay supresion primaria ni secundaria, modo publicable diferenciado ni limite arbitrario de
dimensiones. Se mantienen permisos, auditoria, proteccion de datos personales y reserva; el riesgo residual de
reidentificacion se documenta; la reportabilidad integra no autoriza exponer identidades ni antecedentes reservados.

D42. **Series historicas y clasificacion retrospectiva** (DP-15). Importacion de series agregadas de informes
anteriores o del legado con un asistente CSV/XLSX muy simple (plantillas, correspondencia de columnas, vista previa,
validacion, fuente, periodo, metodologia y confirmacion). Comparaciones historicas sin combinar automaticamente series
incompatibles o superpuestas. No se migran expedientes ni se conecta al legado. Clasificacion retrospectiva de registros
propios de SIGER con trazabilidad y reapertura obligatoria cuando esten cerrados.
   - Ajuste de aprobacion: cada serie declara su ambito (institucional o una sede) al importarse; la ven las cuentas con
     REPORTES cuyo alcance cubre ese ambito; la importan y anulan cuentas con GESTION_ESTADISTICA en ese alcance; TI no
     accede (D2); exportarla exige EXPORTACION (`DISENO` 7).
   - DI-07 (2026-11-24): se conservan integramente el mecanismo de reapertura y el motor de tiempos; sin modalidades
     especiales de reapertura ni cambios en `REQ_TOTAL`. La trazabilidad de lo retrospectivo la registra el sistema en
     el evento (referencia a la reapertura vigente y origen RETROSPECTIVA).

D43. **Facultades estadisticas.** Precision del plan para D38-D42: funcion nueva `GESTION_ESTADISTICA` (alcance
opcional, exige REPORTES en el mismo alcance, incompatible con TI) para generar y anular cortes, importar y anular
series y compartir configuraciones; REPORTES ve el explorador, los cortes y las series de su alcance; EXPORTACION
exporta, incluidos los microdatos; TI administra los catalogos; la clasificacion usa la facultad efectiva de gestion
(`DISENO` 9). Aprobada por el responsable el 2026-11-24.

### Hoja de ruta 0.9.0, no vigente en 0.8.0 (D44-D49)

Hoja de ruta 0.9.0: D44 a D49 quedan aprobadas y postergadas por decision del responsable del 2026-11-24 (seccion 3);
no rigen en 0.8.0, ningun gate de 0.8.0 las implementa y se conservan para la planificacion de 0.9.0 (seccion 9).

D44. **Fechas de las sedes** (DP-16). Fecha proyectada, apertura efectiva y activacion como datos distintos.
   - Precision del plan: `opens_ms` = apertura proyectada; `opened_ms` = apertura efectiva declarada por TI;
     `activated_ms` = instante de `SCOPE_ACTIVATE`; la historia de la activacion se reconstruye desde `AMBITO_ACTIVADO`
     marcada como reconstruida (`DISENO` 3.6).
   - DI-06 (2026-11-24): tres fechas independientes. TI declara la apertura efectiva al activar la sede, con la fecha
     actual por defecto y posibilidad de modificarla (en la activacion y despues, con motivo auditado).

D45. **Distribucion territorial del Formulario** (DP-16). Se incorporara en 0.9.0 (`DISENO` 15.2).
   - DI-05 (2026-11-24): distribucion por la coordinacion regional de la sede del territorio, con respaldo central;
     visibilidad nacional para la distribucion central; las COM sin region quedan a cargo del nivel central; avisos
     territoriales; las devoluciones vuelven a la bandeja que corresponda; ante concurrencia prevalece la primera
     operacion valida.

D46. **Correo de avisos** (DP-16). Folio en el aviso, asunto segun el tipo de aviso y enlace directo a la ficha, sin
datos ciudadanos (`DISENO` 15.3).
   - DI-09 (2026-11-24): asuntos diferenciados segun los eventos existentes que generan avisos, con folio, descripcion
     breve y enlace directo cuando este habilitado el acceso por red; sin datos personales.

D47. **Observaciones de funcionarios** (DP-16). Se incorporan conforme se reciban y validen: el responsable las registra
y valida en la bitacora y cada una se convierte en un gate acotado. En 0.8.0 solo se registran; su incorporacion pasa
a 0.9.0, salvo que el responsable decida expresamente incluir una que corresponda a los objetivos de 0.8.0.

D48. **Indice de `inbox_notice`** (DP-16). Se evalua con medicion y se implementa solo si se justifica (`DISENO` 15.4).

D49. **Scripts** (DP-16). Antiguedad del respaldo en `Update.ps1` y paso de RDP a acceso por red, cada uno en un gate
propio de scripts.
   - DI-10 (2026-11-24): desestimada la renovacion del certificado; se mantienen el certificado y la configuracion
     HTTPS actuales.

### Vigentes en 0.8.0 (D50-D51)

D50. **Actualizacion de produccion y verificacion** (DP-17). Se aprovechan los ensayos y evidencias existentes; no se
repiten innecesariamente las pruebas de infraestructura. Las verificaciones se concentran en las migraciones nuevas,
H2 y MySQL 8.4, la integridad de datos, los permisos, los originales firmados y las funcionalidades nuevas. Se ensaya la
actualizacion desde la version efectiva de produccion, con respaldo previo verificado y auditoria independiente. Una
vez autorizada, la actualizacion se instala sin periodos artificiales de espera.

D51. **Organizacion de la version** (DP-18, ajustada por el responsable el 2026-11-24). 0.8.0 se concentra en la
normalizacion integral y la inteligencia de negocios para Estudios; los pendientes operacionales (D44-D49) pasan a 0.9.0
(seccion 9). Sin otra division ni limite artificial de gates: los gates se consolidan segun las dependencias reales, sin
comprometer criterios, pruebas ni seguridad. Prefijo E, decisiones D25 en adelante y migraciones V17 en adelante. La
facilidad de uso es criterio transversal de aceptacion, en especial para la Direccion de UEP (`DISENO` 10.5). El
ejecutor puede ser Claude Code o Codex: el repositorio agrega un `AGENTS.md` que remite a `CLAUDE.md`, sin duplicar
contenido.

## Precision 0.8.1 - rediseno visual del explorador estadistico e iconografia

Decisiones del responsable del 2026-11-27, copiadas de la seccion 0 de `docs/PLAN_0_8_1.md` en el gate F01. 0.8.1 es solo
interfaz: no cambia datos, cifras, consultas, contratos, exportaciones, servidor, migraciones ni scripts. Las decisiones
D1-D51 siguen vigentes sin cambio; la hoja de ruta 0.9.0 (D44-D49) sigue fuera de alcance.

D52. **Alcance.** 0.8.1 redisena por completo la presentacion del explorador estadistico (`#estadisticas`:
Panorama, Informes, Explorar, Cortes y series, Calidad), incluido el dibujo de sus graficos y tablas, y cambia la
iconografia de toda la aplicacion. Es un cambio de presentacion, no de contenido: sin cambios en el servidor (salvo
el numero de version del `pom.xml`), en migraciones (esquema 24), en `scripts/` ni `.mvn/`, en las consultas que se
envian, en el modelo de graficos (`chartModel.ts`: paleta, escalas, barras desde 0) ni en su exportacion
(`chartExport.ts`), en el significado de ninguna cifra ni en los textos de ayuda y de "Como leer esta cifra" salvo lo
que agrega D56. Ninguna pantalla agrega consultas nuevas al servidor para decorar.

D53. **Tarjetas del Panorama.** Cada tarjeta de indicador es, en toda su superficie, un enlace real a su consulta
en Explorar: un `<a href="#estadisticas?...">` con el `href` que hoy calcula `encodeRoute('explorar', q)`. Responde
al clic en cualquier punto (titulo, cifra, variacion, linea mensual, ayuda, pie), al teclado (Tab y Enter), al clic
central y a "abrir en una pestana nueva". La tarjeta en error no es enlace (conserva "Reintentar"); la tarjeta que
calcula si lo es, como hoy. Se retira el patron de boton con capa `::after`.

D54. **Iconos: autorizacion de dependencia (DI-12 de 0.8.0, para este caso).** Se autoriza una sola dependencia
nueva de frontend: **`lucide-react`** (licencia ISC), en `dependencies`, con **version exacta** (sin `^` ni `~`),
cuya `peerDependencies` admita React 19, verificada con `npm info lucide-react@<version> peerDependencies` y
registrada en el cierre. Se adopta a traves del componente existente `components/Icon.tsx`, que conserva su API
(`<Icon name size className/>`, `aria-hidden`, `focusable="false"`): ningun archivo importa `lucide-react`
directamente salvo `Icon.tsx`. Solo se importan iconos por nombre (sin importacion global ni iconos dinamicos por
cadena). El texto de la licencia ISC queda en `docs/LICENCIAS_TERCEROS.md` (con la de Cabin, OFL). Ninguna otra
dependencia (ni de graficos, animacion, estilos o componentes) sin autorizacion expresa.

D55. **Direccion visual.** "Tablero institucional": claro, sobrio y con jerarquia fuerte, dentro de
`docs/MANUAL_IDENTIDAD_DDN_SIGER_A.md`. Se construye con los tokens existentes y, si hacen falta, tokens nuevos en
`styles/tokens.css` derivados de la paleta (los colores del emblema solo como acentos, nunca de fondo de texto
largo). Contraste: textos >= 4,5:1 e iconos y marcas >= 3:1 sobre su fondo, con prueba que lo calcula para cada
token nuevo (como `tests/charts.test.ts`). Movimiento breve (<= 500 ms) y siempre desactivado con
`prefers-reduced-motion: reduce`. Sin modo oscuro.

D56. **Sentido de la variacion.** La variacion conserva cifra, porcentaje (o puntos), flecha y texto. El color es
apoyo y solo expresa juicio donde hay uno: en "Ingresos con caracterizacion completa" subir es favorable; en
"Registros tardios" bajar es favorable; en Ingresos, REQ constituidos, Casos y Actuaciones el color es neutro (mas
ingresos no es mejor ni peor). Favorable usa los tonos `--ok-*`; desfavorable, `--warn-*` (nunca `--danger-*`);
neutro, `--info-*` o la tinta. "Como leer esta cifra" del Panorama agrega una linea que lo explica.

D57. **Entrega y produccion.** 0.8.1 se entrega con su propio paquete y la etiqueta `v0.8.1`; el paquete 0.8.0
(`SIGER-20261126-212826.zip`) y las etiquetas anteriores quedan inmutables. La actualizacion de produccion
(DDN-SIGER) de 0.8.0 a 0.8.1 la ejecuta el responsable, con autorizacion expresa, por el paso U81 (seccion 9),
despues de F03. La activacion de los catalogos (U7 de 0.8.0) sigue su propio curso con UEP y no depende de 0.8.1.

Aplicacion en F01 (GO del responsable del 2026-11-27): `lucide-react` fijado en **1.71.0** (publicado el 2026-11-10;
`peerDependencies` `react ^16.5.1 || ^17 || ^18 || ^19` y `@types/react` opcional). Su `LICENSE` es ISC con un aviso MIT
para los iconos derivados de Feather: `docs/LICENCIAS_TERCEROS.md` copia ese archivo integro (desvio de D54, que solo
nombra ISC). Tres nombres indicativos del plan no existen en esa version y se usan sus equivalentes:
`TriangleAlert` -> `TriangleExclamationPoint`, `ClockAlert` -> `ClockExclamationPoint` e `History` -> `RotateCcwClock`.
Ningun icono conserva su trazo propio. La animacion de conteo de la cifra (opcional en el plan) no se implemento: el
render en servidor, el texto accesible y la pantalla muestran siempre la cifra exacta.
