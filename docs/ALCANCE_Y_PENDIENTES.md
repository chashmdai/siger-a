# SIGER 0.8.1 - alcance y pendientes

## Incluido en el fuente

Monolito de COM/SOL/REQ/Caso con roles por ambito y organigrama; derivaciones, colaboraciones y transferencias;
subrogancias; actuaciones, tareas y recordatorios; cierres e historial; documentos privados en cuarentena con analisis
antivirus para liberarlos; primera atencion progresiva (general y NNA, con versiones y entregas); dashboard por
funciones; consumidor PULL/ACK del Formulario Publico, correo por Microsoft Graph y servicio Windows (inactivos hasta
configurarlos).

Nuevo en 0.5.0 (decisiones D1-D9 de `docs/DECISIONES_FUNCIONALES.md`, "Precision 0.5.0"):

- **Administracion en TI** (D1-D3): catalogo unico de roles; TI concede y revoca funciones con el perfil de la cuenta
  (`PROFILE_SET`, un acto con fundamento) y administra subrogancias, sedes y calendario. Se suprime AUTORIDAD. TI no
  combina su funcion con otras ni lee expedientes; la instancia conserva siempre una TI permanente y, si la pierde, se
  recupera con `Recover-Ti.ps1`. Varios distribuidores.
- **Consulta institucional** (D4): lectura de todo, salvo constancias y documentos reservados, sin gestion.
- **Sedes regionales** (D7-D9): un ambito por sede con region, estado PROYECTADA/ACTIVA/CERRADA y apertura; la
  Coordinacion regional es `DIRECCION`; panel Estructura; selector de destino por nivel, region y sede; materia
  sugerida (UPR, UPD, UEP) y lectura nacional por materia de las Direcciones.
- **Buscador de personas y folios** (D5): indice de coincidencias (`person_reference`) por RUN, documento, nombre o
  folio, filtrado por lo que la cuenta lee, con auditoria sin el termino en claro. El termino nunca va en una URL.
- **Calendario habil** (D6): feriados nacionales y regionales que carga TI.
- **Hitos y reportes** (D6): hitos de cada transicion (con carga historica), reportes de volumen, antiguedad y 12
  tiempos en horas corridas y dias habiles (mediana, p90, maximo), por unidad, ambito, region, materia u origen y, con
  `REPORTES_PERSONAS`, por funcionario; exportacion CSV de REQ y Casos sin datos ciudadanos (`EXPORTACION`).
- Ingreso sin distinguir mayusculas (el usuario se guarda y se compara en minusculas). Descargas con nombre neutro.

Nuevo en 0.6.0 (decisiones D10-D17, "Precision 0.6.0"; salida a produccion, sin funcionalidad nueva de expedientes ni
migraciones):

- **Cierre de la validacion con recepciones de prueba autorizadas** (D11): `Close-Validation.ps1 -RecepcionesDeclaradas`
  exige la pausa vigente, la cantidad declarada igual a las confirmadas, ningun ACK pendiente y nada posterior a la pausa;
  conserva los datos y deja la constancia en `D:\SIGER-CONSTANCIAS`.
- **Eliminacion de la validacion cerrada, con constancia** (D12): `Remove-Validation.ps1`, el unico script que borra
  datos de una instancia; la eliminacion es logica, no forense.
- **Graph**: credencial instalada como la cuenta de servicio, sin SMR (`Import-GraphCredential.ps1`), y prueba
  controlada de token o de un envio de texto fijo (`Test-GraphMail.ps1`); el correo puede partir desactivado (D16).
- **Scripts invocables con `-File`** (listas por texto, cuentas literales), proceso propio acreditado por el servicio
  cuando WMI oculta la ruta, y guia de la salida a produccion (`docs/DESPLIEGUE_VM.md`, V1-V6 y plan B).
- **Liberacion por analisis en origen** (D17, modo de emergencia): mientras no haya un motor antivirus admitido, los
  originales del Formulario se liberan al registrarse, despues de verificar la firma de la entrega y el SHA-256 de cada
  original, con auditoria propia (nunca como un analisis local); los adjuntos subidos en SIGER siguen en cuarentena. Se
  activa de forma expresa (`Set-OriginRelease.ps1`) y no tiene fecha de termino: rige hasta que se configure un motor
  admitido o se desactive de forma expresa; la revision periodica queda a cargo del responsable, segun el acta
  institucional. Mientras rige, TI ve un aviso permanente. Al regir de nuevo libera los originales del Formulario que
  quedaron en cuarentena.

Nuevo en 0.7.0 (decisiones D18-D24, "Precision 0.7.0"; migraciones V15 y V16, aditivas):

- **Cobertura territorial de las sedes** (D18-D19): cada sede regional conserva su region fisica y puede atender otras
  regiones del catalogo (cobertura adicional); TI declara tambien las regiones que atiende el nivel central (por
  ejemplo, la Region Metropolitana). TI la administra en Estructura con fundamento, concurrencia y auditoria (solo
  codigos); cada region es un periodo y la historia queda consultable. Al cerrar una sede, cambiar su region o su
  cobertura, o la del nivel central, el servidor informa el efecto territorial (region por region, quien la sigue
  atendiendo o si queda sin atencion) y, si alguna queda sin atencion, exige confirmar esa lista exacta. Aviso
  permanente `REGION_SIN_SEDE`. El archivo de estructura inicial admite `cobertura` y `coberturaNivelCentral`.
- **Selector de destino** (D20): una sede aparece tambien bajo las regiones que cubre; la propuesta no cambia de regla y
  una region atendida por el nivel central parte en Nivel central con aviso. El servidor no valida la region del
  destino ni distribuye solo.
- **Region de origen de la persona** (D21): region registrada (Formulario o primera region de la ficha), vigente y
  corregida, desde las versiones de la ficha (V16). Reportes con filtro y agrupacion por region de origen registrada,
  cantidad de corregidas y el rotulo "region de la sede"; exportacion con las tres regiones (`region_sede`,
  `region_registrada`, `region_vigente`, que reemplazan la columna `region`). La ficha muestra la registrada cuando
  difiere de la vigente.
- **Feriados regionales sin cambio de regla** (D22): los dias habiles descuentan los nacionales y los de la region de la
  sede responsable (nivel central: solo nacionales).
- **Marca territorial y de riesgo en la bandeja de comunicaciones** (D23): region de origen vigente en numero romano
  ("RM" para 13), color regional si la atiende una sede activa y marca roja con icono y texto si el Formulario informa
  riesgo actual (con respiracion lenta solo mientras esta por distribuir, sin animacion con movimiento reducido). Solo
  presentacion: no cambia filas, orden, filtros ni la distribucion manual.
- **Aviso a distribucion de las COM del Formulario** (D24): una tarea periodica aparte de la recepcion (cada 60
  segundos) avisa una sola vez por COM del Formulario por distribuir, que no haya pasado por una SOL ni por un vinculo, a
  quienes pueden distribuirla: "Mis avisos" y, con el correo Graph activo, su correo. Texto fijo, sin datos de personas
  ni del relato, con un texto propio si el Formulario informa riesgo (regla de D23). Si nadie puede distribuir, el aviso
  sale cuando haya quien pueda (en produccion, cuando TI cree las cuentas de distribucion en V5). Las COM del Formulario
  que ya esten por distribuir al instalar 0.7.0 se avisan en el primer ciclo, de a 100 por ciclo.

Nuevo en 0.8.0 (decisiones D25-D43, "Precision 0.8.0"; normalizacion integral de la informacion e inteligencia de
negocios para Estudios; migraciones V17 a V24, aditivas; sin cambios en scripts ni en la recepcion del Formulario):

- **Normalizacion de la captura** (D33): persona declarada canonica (`persona-1`) con las mismas reglas del Formulario
  en la ficha de primera atencion 1.2 (tambien en la modalidad NNA: region y comuna estructuradas, relacion, hechos e
  instituciones) y en la nueva ficha de ingreso de la COM manual; complementar se distingue de rectificar y los datos
  historicos se conservan sin dividirse ni interpretarse.
- **Catalogos estadisticos versionados** (D27): doce catalogos (tipo de solicitud, requirente, via, contexto,
  caracterizacion, derechos del modelo 2.0 de 2022, rol del Estado, grupos prioritarios, tipo de actuacion, tipo de
  institucion, relacion y tramos de edad) que TI importa y activa con fundamento; archivos de referencia en
  `config-ejemplo\catalogos\` (PROPUESTA, con conformidad institucional de DI-01 a DI-04 del 2026-11-25). Sin catalogo
  vigente nada se exige.
- **Clasificacion estadistica** (D28-D31, D34-D36): tipo de solicitud, requirente y via de cada ingreso (la via se
  deriva del canal; el Formulario propone y un funcionario confirma); derechos, contexto, caracterizacion, rol del
  Estado y grupos prioritarios del REQ y de sus Casos, consolidados sin duplicar; actuaciones tipificadas con sus
  instituciones y resultado de la primera atencion; historia completa y clasificacion retrospectiva solo con reapertura
  formal (D35, DI-07). El cierre exige lo pendiente con catalogo vigente.
- **Motor estadistico y explorador** (D37-D41): una sola metodologia para pantalla, exportacion y cortes; Panorama,
  Informes (Figura 1 con la adscripcion institucional, DI-13), Explorar (cruces, porcentajes, comparaciones, cuatro
  modos temporales, graficos con descarga PNG y SVG, vistas guardadas y compartidas), Cortes y series, y Calidad
  (completitud por variable, ambito y mes, con enlace a las bandejas filtradas por "Con datos estadisticos pendientes").
  Cifras exactas, con limites de ejecucion (concurrencia, plazo de 30 s y confinamiento de los temporales de MySQL en
  la unidad de la instancia).
- **Cortes, exportaciones y series** (D39-D42): cortes inmutables con huella SHA-256 y verificacion (generarlos detiene
  las operaciones unos segundos); CSV, XLSX y paquete ZIP con microdatos sin datos ciudadanos (un ZIP a la vez, hasta
  30.000 folios); series historicas importadas con vista previa y correspondencia confirmada, nunca sumadas solas.
- **Funcion GESTION_ESTADISTICA** (D43): genera y anula cortes, importa y anula series y comparte vistas; exige REPORTES
  en el mismo alcance y no combina con TI.
- **Desestimacion directa de una COM** (E12b): quien distribuye puede desestimarla como invalida o duplicada sin pasar
  por una SOL, con fundamento obligatorio; distribuir, asumir, redirigir y redistribuir admiten omitir el fundamento.
- Rendimiento medido en E14 con 50.005 COM sinteticas: cada consulta del Panorama y del explorador bajo 3 s (registro de
  E14 en `docs/BITACORA_0_8_0.md`).
- **Rediseno visual del explorador estadistico e iconografia** (0.8.1, `docs/PLAN_0_8_1.md`, D52-D57, gate F01): solo
  interfaz, sin cambios de servidor, consultas, cifras, migraciones (esquema 24) ni scripts. Las tarjetas del Panorama
  abren su consulta en Explorar al hacer clic en cualquier punto (enlace real, tambien con el teclado y en otra pestana);
  el color de la variacion solo juzga en caracterizacion completa y registros tardios (D56); las cinco secciones y sus
  graficos y tablas tienen nueva presentacion; los iconos de toda la aplicacion son de `lucide-react` 1.71.0 (ISC,
  `docs/LICENCIAS_TERCEROS.md`).

## Verificado en la VM

- **PULL/ACK real con el Formulario Publico** (verificado por el responsable el 2026-11-21 en "Auditoria y operacion" de
  la instancia de validacion 0.4.0): consumidor en modo automatico, estado "Esperando entregas", ultimo exito
  21-11-26 16:22, sin incidentes, 9 recepciones confirmadas (PULL y ACK completos), credenciales instaladas con DPAPI.
  Las 9 entregas son pruebas del responsable, hechas exclusivamente con sus propios datos y con autorizacion; no hay
  datos de terceros. La validacion recibe porque su `instance.json` tiene `"validacionPullAutorizada": true`.
- Correo por Microsoft Graph: **desactivado** en esa instancia (sigue pendiente).

## Fecha critica: 15/12/2026 00:00 (America/Santiago)

Hasta esa fecha el Formulario Publico esta protegido por un acceso temporal con contrasena; despues queda abierto al
publico. **Antes del 15/12/2026, produccion debe estar consumiendo (a mas tardar el 10/12, D13), o la validacion debe
estar pausada y cerrada** (V1-V2) **o en Manual por el plan B** (12/12), para que ninguna entrega ciudadana real llegue a
la validacion. Solo un consumidor por cola: al activar produccion, el de la validacion ya debe estar detenido
(`docs/DESPLIEGUE_VM.md`).

## Decisiones de implementacion acotadas

- Origen conserva el encargo hasta recibir la entrega. El destino habilitado puede leer la ficha para decidir, sin
  convertirse en jefe principal del REQ. Si cambia el principal se usa la transferencia formal.
- Una sola entrega pendiente; mientras lo este, la ficha no se complementa ni se cierra y no se inicia un traslado.
- Ficha editable mientras la primera atencion este abierta y sin Caso; al abrir Caso queda como antecedente.
- Lo atendido directamente no usa SOL. Una COM externa por clasificar si usa SOL. Las filas antiguas no se convierten
  retrospectivamente por inferencia.
- El buscador no fusiona identidades ni autentica personas: muestra coincidencias, y una coincidencia solo por nombre
  puede ser otra persona. No indexa a la persona senalada ni a "otra persona".
- Los reportes son de gestion del sistema: su caracter oficial lo define la institucion. Se calculan con el calendario
  habil vigente (agregar o retirar un feriado cambia tambien periodos pasados) e informan cuantos tramos se
  reconstruyeron desde la historia anterior a 0.5.0. En un REQ resuelto en el acto (`COM_IMMEDIATE`) la fecha de cierre
  es la declarada y el tiempo usa el instante del registro.
- Los hitos que la carga historica no reconoce se omiten y se cuentan (aviso permanente en Administracion).

## Pendientes que NO se presentan como terminados

- **Produccion en 0.8.0 y activacion de los catalogos**: el paso U se ejecuto el 2026-11-26 (U0-U6 conformes,
  `docs/PLAN_0_8_1.md` seccion 1); queda U7 (TI activa los catalogos con la conformidad de UEP), que sigue su propio
  curso y no depende de 0.8.1.
- **Produccion en 0.8.1**: el plan 0.8.1 esta cerrado (revision visual y auditoria conformes en F02c; paquete
  `SIGER-20261127-122505.zip` y etiqueta `v0.8.1` en F03, con nombre, tamano y SHA-256 del ZIP y del JAR en su registro
  de `docs/BITACORA_0_8_1.md`); falta la actualizacion de produccion por el responsable con autorizacion expresa (paso
  U81 de `docs/DESPLIEGUE_VM.md`, seccion 6).
- Ejecucion en la VM de la salida a produccion (pasos V1-V6 de `docs/PLAN_0_6_0.md` y `docs/DESPLIEGUE_VM.md`): cierre
  de la validacion 0.4.0 con sus 9 recepciones de prueba, produccion nueva (0.7.0 o 0.6.0 segun el corte) en
  automatico, acreditacion (D13), eliminacion de la validacion con constancia y, sin entregas de prueba en produccion, la
  primera entrega ciudadana despues del 15/12 como unica prueba de punta a punta (`docs/PRUEBAS_MANUALES_0_6_0.md`).
  Tambien `Recover-Ti.ps1` y un envio real por Graph.

Hoja de ruta 0.9.0 (postergado por decision del responsable del 2026-11-24, `docs/PLAN_0_8_0.md` seccion 9; las
decisiones y definiciones ya aprobadas se conservan como base de su planificacion; diseno de cada punto en
`docs/DISENO_0_8_0.md` seccion 15; ningun gate de 0.8.0 los implementa):

| Punto | Decision | Definicion aprobada |
|---|---|---|
| Fechas de las sedes: apertura proyectada, apertura efectiva y activacion (hoy `SCOPE_ACTIVATE` conserva la apertura proyectada; observacion de T06b) | D44 | DI-06 |
| Distribucion territorial del Formulario por coordinacion regional | D45 | DI-05 |
| Correo de avisos: asunto por evento, folio, descripcion breve y enlace | D46 | DI-09 |
| Observaciones de funcionarios (registro en la bitacora de 0.8.0) | D47 | - |
| Indice de `inbox_notice`: evaluacion con medicion | D48 | - |
| Antiguedad del respaldo en `Update.ps1` (hoy acepta cualquier respaldo integro de la misma raiz, base y JAR) | D49 | - |
| Paso de RDP a acceso por red (requisito del enlace directo del correo; no hay script para cambiar `bindAddress` despues de `Init-Server.ps1` sin `-AccesoRed`) | D49 | - |
| Renovacion del certificado autofirmado | D49 | DI-10: desestimada; se mantienen certificado y HTTPS |
| Dimension estadistica "instituciones de los hechos" (ficha, ficha de ingreso y Formulario) | D38 | GO de E08 (2026-11-25) |
| Pantallas de resolucion en el acto con derechos (COM_IMMEDIATE y ATTENTION_CREATE con resolve_now) | - | GO de E09, decision D |
| Caracterizacion de la COM en la ficha de la SOL (evita leer la ficha de la COM en el descarte) | - | hallazgo de E09 |
| Corte sin lock global: instantanea consistente con bloqueo de milisegundos (opcion D de la detencion de E10), si el volumen se acerca al plazo | - | detencion de E10 |
| Microdatos a nivel de actuacion y de Caso en el paquete estadistico | - | GO de E11 |
| Paquete ZIP escrito por flujo a un temporal (menos memoria; opcion 4 de la detencion de E11) | - | detencion de E11 |
| Figura 1 en un solo libro XLSX (cuatro hojas) | - | GO de E12 |
| Reportes de gestion y su exportacion (`Reports`) que excluyan a una cuenta TI con una concesion inconsistente (TI con REPORTES insertada por SQL; la aplicacion no permite crearla y Administracion avisa `TI_INCOMPATIBLE`); en 0.8.0 queda como excepcion explicita de `PermisosFinalesTest` | - | hallazgo de E14 (preexistente desde 0.5.0) |
| Retencion y borrado del esquema siger_a_restore_* que deja el paso U en produccion | - | auditoria E16 |
| Division del codigo del frontend (code splitting; 508 kB) | - | auditoria E16 |
| Otras mejoras operacionales sin relacion directa con los objetivos de 0.8.0 | - | - |

Pendientes sin version asignada (detalle en `docs/BITACORA_0_6_0.md`):

- **Motor antivirus definitivo**: ClamAV en `D:\Herramientas\clamav` (sin codigo nuevo; requiere autorizacion de TI y
  salida de red para las firmas) o Kaspersky KES 14 (codigo nuevo en `DocumentScanner`, previa confirmacion con TI de
  que la cuenta de servicio puede analizar). Mientras tanto rige D17 para los originales del Formulario y los adjuntos
  subidos en SIGER siguen en cuarentena.

Otros pendientes:

- Reserva excepcional por campo de la ficha (por ejemplo, nota de seguridad o de identidad) y autorizaciones
  excepcionales completas.
- Plazos normativos (por ejemplo, 48 horas) sobre el calendario habil; informes oficiales definitivos.
- Migracion historica desde sistemas anteriores; nominas, cargos y sedes reales (se cargan en la VM).
- Copias fuera del anfitrion y recuperacion productiva calificada; SSO/MFA si se solicita.
- Primeras atenciones adicionales sobre un expediente cerrado sin reapertura autorizada; editar la ficha estructurada
  despues de abrir Caso; guardado offline entre sesiones.
- Renombrar una cuenta (no hay comando; el usuario se fija al crearla).

Produccion (D:\SIGER) opera con datos personales reales desde V4, con visto bueno institucional. La validacion 0.4.0 contiene solo entregas de prueba autorizadas del responsable y se elimina en V6. Los pendientes de seguridad de esta lista (motor antivirus local, certificado HTTPS institucional, respaldos fuera del anfitrion) siguen abiertos.
La ficha conceptual no es un formulario aprobado.
