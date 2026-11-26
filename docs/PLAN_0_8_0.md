# SIGER 0.8.0 - plan de implementacion

Normalizacion integral de la informacion e inteligencia de negocios para Estudios. Decisiones del responsable del
2026-11-24 (DP-01 a DP-18 del diagnostico, aqui D25-D51), aprobacion del plan del mismo dia con sus ajustes
(definiciones DI-05 a DI-10, funcion GESTION_ESTADISTICA y seis correcciones) y reconsideracion del alcance del mismo
dia (seccion 3), que posterga a 0.9.0 los pendientes operacionales con sus decisiones aprobadas (seccion 9). Version
0.8.0, prefijo de gates **E**, registro en `docs/BITACORA_0_8_0.md`, diseno de referencia en `docs/DISENO_0_8_0.md`.
Este plan solo cambia con aprobacion del responsable; si una sesion encuentra que es incorrecto o incompleto, lo
registra como desvio o hallazgo y, si bloquea el gate, se detiene.

0.8.0 es un ciclo separado del despliegue institucional: no cambia los paquetes de 0.6.0 ni de 0.7.0, ni la secuencia
V0-V6 de `docs/DESPLIEGUE_VM.md`. La actualizacion de produccion a 0.8.0 es un paso del responsable, con autorizacion
expresa, despues del cierre de la version (seccion 3).

## 0. Decisiones (las copia E00 a `docs/DECISIONES_FUNCIONALES.md` como "Precision 0.8.0")

Cada decision reproduce la del responsable (DP-xx) y agrega, como "Precision del plan", el detalle de diseno que el
responsable aprueba al aprobar este plan. Las definiciones institucionales aun abiertas (DI-xx) estan en la bitacora.

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

D50. **Actualizacion de produccion y verificacion** (DP-17). Se aprovechan los ensayos y evidencias existentes; no se
repiten innecesariamente las pruebas de infraestructura. Las verificaciones se concentran en las migraciones nuevas,
H2 y MySQL 8.4, la integridad de datos, los permisos, los originales firmados y las funcionalidades nuevas. Se ensaya la
actualizacion desde la version efectiva de produccion, con respaldo previo verificado y auditoria independiente. Una
vez autorizada, la actualizacion se instala sin periodos artificiales de espera.
   - Precisión del responsable (2026-11-25): sin ensayo de la actualización en TEST-SIGER; compensaciones en el paso U (E14).

D51. **Organizacion de la version** (DP-18, ajustada por el responsable el 2026-11-24). 0.8.0 se concentra en la
normalizacion integral y la inteligencia de negocios para Estudios; los pendientes operacionales (D44-D49) pasan a 0.9.0
(seccion 9). Sin otra division ni limite artificial de gates: los gates se consolidan segun las dependencias reales, sin
comprometer criterios, pruebas ni seguridad. Prefijo E, decisiones D25 en adelante y migraciones V17 en adelante. La
facilidad de uso es criterio transversal de aceptacion, en especial para la Direccion de UEP (`DISENO` 10.5). El
ejecutor puede ser Claude Code o Codex: el repositorio agrega un `AGENTS.md` que remite a `CLAUDE.md`, sin duplicar
contenido.

Escenarios de aceptacion (pruebas automatizadas y humo de los gates correspondientes):
1. **Figura 1 sintetica**: con un conjunto sintetico de esperados conocidos, el explorador entrega los cuatro desgloses
   (tipo de solicitud, requirente, via, adscripcion institucional (DI-13)) de un periodo julio-junio; cada desglose suma
   el total; la exportacion y un corte de la misma consulta dan las mismas cifras. La región del requirente, la del NNA
   y la de los hechos se consultan como dimensiones aparte.
2. Conteo D25: invalida excluida, duplicada contada, antecedente no contado, vinculo devuelto vuelve a contar, COM
   pendiente y primera atencion contadas, REQ reabierto, transferido y con varios Casos contado una vez.
3. Fechas D26: recepcion el 30-06 a las 23:59:59,999 y el 01-07 a las 00:00 (hora de Chile) en periodos distintos;
   cambio de horario; registro tardio identificado.
4. Propuestas: COM del Formulario adulto con motivo vulneracion propone vulneracion y persona natural adulta; promocion
   con organizacion propone persona juridica; NNA sin propuesta de tipo y con requirente NNA; la propuesta no cuenta
   como informado.
5. Territorio: una COM de Maule (07) atendida por O'Higgins distingue region del requirente 07, sede responsable
   REG-06, territorio por cobertura REG-06 y region de la sede 06; una transferencia cambia la sede vigente y no la
   inicial.
6. Derechos: un REQ con dos Casos que comparten un derecho cuenta un requerimiento con ese derecho; una cuenta sin
   lectura del segundo Caso no ve su clasificacion; un REQ cerrado rechaza la clasificacion hasta su reapertura.
7. Cierre D28: con catalogos vigentes, un REQ con tipo pendiente no cierra; con "sin informacion" si; sin catalogo
   vigente no bloquea.
8. Actuaciones: dos denuncias penales en un REQ cuentan dos actuaciones y un requerimiento con denuncia penal; una
   tarea "compromiso de querella" completada no cuenta como querella.
9. Cortes: una correccion posterior no cambia el corte; la comparacion la muestra; la verificacion de integridad
   recalcula la misma huella; el corte no se puede modificar ni borrar.
10. Series: una serie importada de 2024-2025 se muestra junto a SIGER sin sumarse; una serie superpuesta se avisa; una
    serie de una sede no la ve una cuenta con REPORTES de otra sede; una serie institucional exige alcance
    institucional; TI no la ve.
11. Ficha 1.2 y COM manual: un nombre historico no se divide; completar una region no es correccion; RUN, documento,
    nacionalidad, correo y celular con las mismas reglas del Formulario.
12. Contrato: las entregas NNA con RUN de fuente SRCeI se registran sin autenticacion y sin elevar la procedencia.
13. Facilidad de uso: escenarios U1-U13 de DISENO 10.5, acreditados por el ejecutor en el humo y revisados en E16 (E15 postergado el 2026-11-25).
14. Edad y nacionalidad: un NNA con fecha de nacimiento tiene su edad a la fecha de recepcion; uno con edad declarada
    entera la conserva; un texto historico de edad no se interpreta y queda "sin informacion"; una nacionalidad libre
    de una ficha 1.0 no se convierte en pais; la fecha de nacimiento nunca se exporta.
15. Multivaluadas: un REQ con tres derechos (uno repetido en un Caso) tiene una fila en el microdato con 3 valores y
    tres filas en el archivo relacionado; el agregado por derecho cuenta un requerimiento por derecho, informa las
    menciones y el total de requerimientos unicos; re-agregar los dos archivos reproduce el agregado; el grafico
    circular no se ofrece para esa dimension.

## 1. Punto de partida (verificado al planificar, 2026-11-24)

- `main` = `v0.7.0` = `3938e4e`; etiquetas `v0.5.0` (`4cde534`) y `v0.6.0` (`7c9b89b`). Version 0.7.0 en
  `backend/pom.xml`, `frontend/package.json` y los dos campos de `package-lock.json`. Esquema 16. `SOURCE_SHA256.txt`
  coherente con el indice. Sin `AGENTS.md`.
- Pruebas: 194 metodos `@Test` en 40 clases (191 en H2; 194 sobre MySQL 8.4 con `-Dsiger.mysql.suite=true`); 112 de
  frontend; `Check-Scripts.ps1` (PS 5.1): 40 scripts, 73 ordenes documentadas en 60 procesos (bitacora 0.7.0).
- `scripts/`: 44 archivos (39 `.ps1` y 5 fuentes auxiliares); `git diff v0.6.0 -- scripts/ .mvn/` vacio.
- Paquete 0.7.0 `SIGER-20261123-133838.zip` (SHA-256 `e09de639...66f2`) y 0.6.0 `SIGER-20261122-201515.zip`
  (`08a526e2...cb787`): inmutables, fuera de Git.
- Referencias de codigo: `DISENO_0_8_0.md` seccion 1.2 (S1-S18). Formulario: `6f886eb`, seccion 1.3 (F1-F10).

## 2. Reglas para todos los gates (condiciones del responsable)

1. **Inmutables**: `v0.5.0`, `v0.6.0`, `v0.7.0`, sus paquetes, las evidencias de H06e, T06b (incluidos `D:\SIGER`,
   `D:\SIGER-CONSTANCIAS`, `D:\Ensayo-H06*`, `D:\Ensayo-T06b`, `D:\Custodia-SIGER-P0` de TEST-SIGER) y los respaldos y
   constancias de la VM. No se modifica la VM durante el desarrollo.
2. **Formulario**: `git diff v0.7.0` vacio para `FormularioReception.java`, `FormularioConsumer.java`,
   `FormularioAutomatic.java`, `FormularioClient.java`, `FormularioPull.java`, `ExternalOriginal.java`,
   `ExternalProjection.java` e `IntakeCommands.conserveExternal`. Ningun codigo actualiza ni borra
   `communication_original`, `external_person`, `external_attribute` ni `external_field`. El repositorio
   `formulario-web` solo se lee.
3. **Scripts**: `git diff v0.7.0 -- scripts/ .mvn/` vacio en toda la version (los cambios de scripts pasan a 0.9.0,
   seccion 9).
4. **Migraciones**: V1-V16 no se modifican; cada cambio es la siguiente `Vn__...` aditiva, portable entre H2
   `MODE=MySQL` y MySQL 8.4; `MySql84UpgradeTest` (desde 0.4.0 y, desde E01, desde 0.7.0) pasa hasta la ultima.
5. **Escritura y lectura**: toda escritura por `Workflow.run`; ninguna lectura recibe rol, autor ni alcance del
   navegador; visibilidad doble (`Access` y `BoardQueries.Visibility`) con su equivalencia cuando cambie.
6. **Reglas vigentes**: D1-D24 no cambian salvo lo que diga una decision de este plan; D2 (TI sin lectura ni
   reportes), D6 y D21-D22 (dias habiles con la region de la sede) intactos; el motor de tiempos de `Reports`, el
   tramo `REQ_TOTAL` y el mecanismo de reapertura (`REOPEN_*`) no cambian (DI-07).
7. **Inmutabilidad** (D35): ninguna escritura sobre un REQ o Caso cerrado sin reapertura formal; barrido de comandos
   con las excepciones documentadas.
8. **Sin inferencia**: ninguna clasificacion se infiere de relatos ni con IA; las propuestas solo usan datos
   estructurados con una regla versionada.
9. **Datos personales**: sin datos ciudadanos ni secretos en logs, URLs, notas de auditoria, salidas, constancias ni
   bitacora; la auditoria estadistica registra solo nombres de medidas, dimensiones y filtros.
10. **Dependencias**: ninguna libreria nueva de frontend ni de backend sin autorizacion expresa (DI-12).
11. **Mutacion**: cada regla con una prueba que falla si se rompe, con la evidencia "regla -> prueba que fallo" en el
    registro del gate.
12. **Facilidad de uso**: todo gate de interfaz declara como lo comprobo (texto, flujo, ayuda, movil) y no expone
    codigos ni terminos tecnicos al usuario final.
13. **Comandos**: los de `CLAUDE.md` del equipo donde se trabaje (Linux o Windows en TEST-SIGER); MySQL 8.4 solo
    aislado y desechable en `127.0.0.1`.
14. **Entornos institucionales**: en DDN-SIGER solo `D:`; prohibido consultar o intervenir `C:`, `E:`, otros volumenes o
    el sistema legado; sin detener servicios, reiniciar, borrar ni actualizar produccion sin autorizacion expresa del
    responsable. En TEST-SIGER, rutas de ensayo nuevas (`D:\Ensayo-E14\...`).
15. **Documentos**: las ordenes `powershell.exe ... -File` solo van en las guias que enlaza `Check-Scripts.ps1`
    (`DESPLIEGUE_VM.md`, `INSTALACION_VM.md`, `PRUEBAS_MANUALES_0_6_0.md`, `README.md`, `CLAUDE.md`), con argumentos
    literales que enlacen.
16. Si V0-V6 encuentra un defecto bloqueante de 0.6.0 o 0.7.0, la correccion sale de una rama desde su etiqueta, no de
    `main`.

## 3. Relacion con produccion y plazo

- V0-V6 siguen con 0.6.0 o 0.7.0 segun el corte del 07/12 (`docs/PLAN_0_7_0.md` seccion 3), registradas en
  `docs/BITACORA_0_6_0.md`. 0.8.0 no las cambia.
- **El contrato del Formulario va primero (E00, paso 1) y debe resultar conforme antes de cualquier otro trabajo**:
  verifica con pruebas que SIGER admite las entregas NNA con RUN de fuente SRCeI (nota contractual del Formulario del
  15/11/2026). Todo gate depende de E00. Si falla, E00 queda BLOQUEADO, la version no avanza y el responsable decide una
  correccion desde la etiqueta de la version que usara produccion (regla 16), antes de la apertura del Formulario del
  15/12; E00 vuelve a ABIERTO cuando esa decision se registra.
- La actualizacion de produccion a 0.8.0 (pasos U de `DESPLIEGUE_VM.md`, que redacta E14) la ejecuta el responsable con
  autorizacion expresa, despues de E17 CERRADO, desde 0.7.0, version efectiva de produccion (DI-15), con `Backup.ps1` y
  `Restore-Check.ps1` en la misma ventana, inmediatamente antes de `Update.ps1`. Los catalogos se importan y activan
  despues de su validacion institucional (DI-01 a DI-04); sin catalogos vigentes nada bloquea el cierre.

### Alcance de 0.8.0 (reconsideracion del responsable, 2026-11-24)

0.8.0 se concentra en dos objetivos: **normalizacion integral de la informacion** e **inteligencia de negocios para
Estudios**. Incluye: normalizacion de todos los mecanismos de captura con componentes, estructuras, catalogos y
validaciones compartidas; clasificacion de requerimientos, requirentes y vias; caracterizacion territorial y
demografica; clasificacion oficial de derechos y vulneraciones; tipificacion de actuaciones y respuestas; motor
estadistico nativo; explorador; cortes; series historicas; exportaciones; calidad y completitud; GESTION_ESTADISTICA.
Se mantienen la compatibilidad con el Formulario, las migraciones, la seguridad de la actualizacion, los respaldos y la
auditoria independiente. Lo postergado a 0.9.0, con sus decisiones ya aprobadas, esta en la seccion 9.

### Plazo: 15 de diciembre de 2026

Ruta critica en la seccion 6. Calendario de referencia (no es una espera: un gate empieza en cuanto se cumplen sus
dependencias y llega el GO):

| Dias | Gates |
|---|---|
| 24-25/11 | E00, E01 |
| 25-26/11 | E02, E03 |
| 26-28/11 | E04, E05 |
| 28-30/11 | E06, E07 |
| 30/11-02/12 | E08, E09 |
| 02-04/12 | E10, E11 |
| 04-06/12 | E12, E13 |
| 07/12 | E14 |
| 09-10/12 | E16 y sus correcciones |
| 10-11/12 | E17 |
| 11-13/12 | Paso U en produccion, con autorizacion expresa, respaldo verificado y el margen de E14 |

Reglas del plazo:
- El plazo no reduce pruebas, mutaciones, auditoria ni ensayo. Si un gate no se cierra con sus verificaciones, no se
  cierra; la unica forma de sacar un gate del cierre de 0.8.0 es que el responsable lo pase a POSTERGADO con fundamento.
- Por decisión del 2026-11-25, E15 queda POSTERGADO y E17 no ensaya la actualización (bitácora, Decisiones del responsable).
- La apertura del Formulario del 15/12 no depende de 0.8.0: produccion abre con la version de V0-V6. Si E17 no esta
  CERRADO a tiempo, el paso U se realiza despues, sin atajos.
- Trabajo en paralelo del responsable, sin esperar al ejecutor: GO oportunos; validacion institucional de los
  catalogos (DI-01 a DI-04) para que TI los active en el paso U; DI-11 y ratificación institucional de DI-13;
  verificacion de la transcripcion de derechos (E02).
- Revision anticipada opcional: el responsable puede pedir a Claude Padre revisar bloques de gates ya cerrados mientras
  avanza la implementacion; no reemplaza a E16 ni cambia su alcance, y acorta su duracion.

## 4. Documentacion que se actualiza

`DECISIONES_FUNCIONALES.md` (D25-D51, E00), `CLAUDE.md` (estado en E00; arquitectura en cada gate que agregue piezas y
revision final en E14), `AGENTS.md` (E00), `ARQUITECTURA.md`, `ALCANCE_Y_PENDIENTES.md` (con la hoja de ruta 0.9.0 de
la seccion 9, E14), `README.md`, `INSTALACION_VM.md` si corresponde, `DESPLIEGUE_VM.md` (seccion U de actualizacion a
0.8.0, E14), `deploy/config-ejemplo/*` si cambia, `deploy/catalogos/*` (E02), el nuevo
`docs/PRUEBAS_MANUALES_0_8_0.md` (E14, sin ordenes `-File`) y `docs/DISENO_0_8_0.md` solo para registrar diferencias
de referencia aprobadas.

## 5. Protocolo de cada sesion (gate)

1. **Leer**: `AGENTS.md` (si existe), `CLAUDE.md`, este plan, `docs/BITACORA_0_8_0.md` completa y las secciones de
   `docs/DISENO_0_8_0.md` que indica el gate. Tomar el **primer gate ABIERTO** de la tabla de la bitacora; se saltan
   CERRADO, BLOQUEADO, POSTERGADO y EXTERNO. Si el prompt nombra otro gate, detenerse y preguntar. Un gate por sesion.
2. **Precondiciones**: `git status` limpio; `git log --oneline -5` con el commit del ultimo gate cerrado; dependencias
   del gate CERRADO (todo gate depende de E00 y de su contrato del Formulario); definiciones (DI) que el gate exige
   respondidas en la bitacora. Si falta algo, detenerse.
3. **Inventario y plan antes de editar**: usos de todo lo que se cambia (clases, comandos, columnas, textos, pruebas,
   scripts, documentos), plan de implementacion, archivos que se proponen modificar, pruebas previstas (incluidas
   mutaciones y MySQL), riesgos. Esperar el **GO explicito** del responsable; un mensaje que solo repite el plan no es
   GO.
4. **Editar** solo lo autorizado. Un archivo fuera de "Archivos autorizados" exige detenerse y consultar, salvo pruebas
   y documentos del propio gate.
5. **Comprobar** con la suite completa (backend; frontend si se toca; MySQL 8.4 si hay SQL nuevo o cambiado;
   `Check-Scripts.ps1` si se tocan scripts o guias enlazadas; humo si cambia comportamiento visible, con datos en
   `./target/humo-*`, nunca el `var/` del usuario), las mutaciones y las condiciones de la seccion 2 con `git diff`.
6. **Cerrar**: registro en la bitacora con el formato de la seccion 7, estado CERRADO y un solo commit
   `gate ENN: <titulo>`.
7. **Push** a `main` en `origin`, despues de revisar con `git show --stat HEAD` que no viajen archivos sensibles
   (`var/`, `.env`, CSV de claves, bases H2, `release/`, `.tools/`, `*.clixml`, `*.pfx`, `*.p12`, `*.pem`,
   `*.sigerpkg`).
8. **Detenerse** e informar: que se hizo, como verificarlo y cual es el gate siguiente. No continuar.

Ademas: los gates EXTERNO (E15, E16) no los ejecuta el implementador ni edita su registro o estado; el responsable
los incorpora a mano. Con un escenario no logrado en E15, el responsable deja E15 BLOQUEADO y agrega `E15b` (ABIERTO,
correccion) y luego `E15c` (EXTERNO, repeticion de los escenarios afectados), y asi sucesivamente; el ciclo de E15 esta
cerrado cuando E15 o su ultima repeticion esta CERRADO. E16 empieza solo con el ciclo de E15 cerrado y audita tambien
todas sus correcciones. Con hallazgos en E16, el responsable deja E16 BLOQUEADO y agrega `E16b` (ABIERTO, correccion) y
luego `E16c` (EXTERNO, repeticion), y asi sucesivamente; una correccion de E16 que toque un escenario U1-U13 se verifica
en su repeticion con ese escenario. E17 exige el ciclo de E15 cerrado, la ultima auditoria CERRADO sobre el commit que
contiene todas las correcciones de E15 y de E16, y ningun gate de correccion ABIERTO. Un gate que resulte mayor se
divide (`ENNa` cerrado con lo verificado y `ENNb` ABIERTO). Un gate BLOQUEADO por una definicion pendiente pasa a
ABIERTO solo cuando el responsable registra la respuesta en la bitacora. Ningun gate implementa la seccion 9.
Mientras E15 esté POSTERGADO, E16 empieza con E14 CERRADO y E17 exige solo el ciclo de E16.

### Corte por cuota o cambio de agente

1. **Prevención.** Con menos del 25 % de cuota disponible se pospone el inicio de corridas largas, incluida la suite
   completa de MySQL y las mutaciones. Con menos del 10 %, se resguarda de inmediato y se detiene la sesión.
2. **Resguardo.** Crear la rama `wip/<gate>-<agente>` desde el HEAD del gate. Usar `git add` con rutas explícitas,
   excluyendo `target/`, `.tools/`, logs, capturas, archivos `.cnf`, claves y credenciales. Hacer el commit
   `wip: <gate> de <agente> sin cerrar (no es cierre de gate)`, con un bloque «Dónde quedó» en el cuerpo que
   registre lo hecho, cada corrida con su log en `D:\Ensayo-<gate>` y lo pendiente. Hacer push de la rama WIP.
   El resguardo nunca se publica en `main`.
3. **Corte sin resguardo.** El responsable se abstiene de modificar `C:\sigera` y la carpeta de ensayo. El siguiente
   agente hace el resguardo como encargo especial con inventario, exclusiones, rama y push. Se detiene tras el push,
   sin implementar ni correr pruebas.
4. **Revisión.** Claude Padre revisa la rama contra el GO y entrega el encargo de cierre. Si el agente que continúa
   no cierra en la misma sesión, registra el GO íntegro en
   [«GO aprobados pendientes de ejecución»](BITACORA_0_8_0.md#go-aprobados-pendientes-de-ejecucion).
5. **Validez de la evidencia del agente anterior.** Una corrida vale si ningún archivo de `src` cambió después.
   Una mutación vale si su archivo no cambió después. El humo vale si `src/main` no cambió. Se repite toda
   evidencia que no cumpla esas condiciones. Cada corrida de MySQL usa una ruta nueva en la unidad D
   (`D:\Ensayo-<gate>b`, `D:\Ensayo-<gate>c`, etc.) y exige comprobar antes que el puerto 3417 esté libre.
6. **Procesos auxiliares.** Cerrar por PID en `finally` los navegadores sin ventana y los servidores de humo.
   Al terminar, comprobar que no quede ningún proceso propio. Nunca cerrar procesos ajenos.
7. **Cierre.** En `main`, usar `git merge --squash` y un solo commit `gate <gate>: ...`. Registrar como desvío
   quién ejecutó, quién cerró y de quién es cada evidencia. Borrar la rama WIP solo después de la verificación de
   Claude Padre.
8. **Traspaso.** En cuanto se acuerde una condición para un gate futuro, escribirla en el detalle de ese gate en
   este plan y hacer un commit de documentación. El plan es la única fuente para el traspaso.

## 6. Gates

Columnas de la tabla: dependencia (gates que deben estar CERRADO) y secciones de `DISENO_0_8_0.md`. Con un solo
ejecutor los gates se ejecutan en el orden de la tabla; la dependencia indica que bloqueo detiene a cada gate. El
detalle de cada gate va despues de la tabla.

| Gate | Titulo | Depende de | DISENO |
|---|---|---|---|
| E00 | Linea base, decisiones, AGENTS.md y contrato del Formulario (documentos y pruebas) | - | 1, 2.2, 13 |
| E01 | Volcado sintetico 0.7.0 y catalogos estadisticos versionados (servidor) | E00 | 3.1, 13, 14 |
| E02 | Administracion: catalogos de referencia, interfaz de TI y facultad GESTION_ESTADISTICA | E01 | 3.1, 9, 11 |
| E03 | Normalizacion integral (servidor): persona canonica, ficha 1.2 y ficha de ingreso de la COM manual | E01 | 2, 3.4, 3.5 |
| E04 | Normalizacion integral (interfaz): componentes compartidos, ficha 1.2 y COM manual | E03 | 2, 10.1 |
| E05 | Clasificacion estadistica de la COM: modelo, via, propuestas y trazabilidad (servidor) | E01 | 3.2, 4.1-4.3 |
| E06 | Derechos, contexto, caracterizacion y actuaciones tipificadas (servidor) | E05 | 3.2, 3.3, 4.3, 4.4, 11 |
| E07 | Dimensiones territoriales y demograficas, completitud y reglas de cierre (servidor) | E03, E06 | 4.5-4.8 |
| E08 | Motor estadistico: fuente unica, agregacion, series temporales y comparaciones (servidor) | E07 | 4.4, 4.7, 5 |
| E08b | Motor estadístico: confinamiento, límites de ejecución y concurrencia (servidor) | E08 | 5.7, 16 |
| E09 | Captura estadistica en el flujo (interfaz): caracterizacion, derechos, actuaciones y cierre | E04, E07 | 10.1, 10.2 |
| E10 | Cortes estadisticos y configuraciones guardadas (servidor) | E02, E08 | 3.7, 6 |
| E11 | Exportaciones CSV, XLSX y ZIP e importacion de series historicas (servidor) | E10 | 7, 8, 12 |
| E12 | Explorador estadistico: tablas, filtros, porcentajes, graficos y comparaciones (interfaz) | E10 | 10.3 |
| E12b | Desestimación directa de una COM (servidor e interfaz) | E12 | 4.5.1 |
| E13 | Explorador: exportacion, cortes, series historicas y calidad (interfaz) | E11, E12 | 10.3, 10.4 |
| E14 | Integracion MySQL, rendimiento, aceptacion y cierre de version | E00-E13 y E12b | 5.7, 13, 14 |
| E15 | Validacion de uso con la Direccion de UEP (EXTERNO) | E14 | 10.5 |
| E16 | Auditoria independiente (EXTERNO) | E14 | todo |
| E17 | Build de entrega y etiqueta | Ciclo de E16 cerrado, sin correcciones ABIERTO | 14 |

Ruta critica (cadena mas larga de dependencias): E00 -> E01 -> E05 -> E06 -> E07 -> E08 -> E10 -> E11 -> E13 -> E14 ->
E16 -> E17 -> paso U. Fuera de ella, pero antes de lo que los necesita: E02 (antes de E10), E03 (antes de E07),
E04 (antes de E09), E09 (antes de E14), E12 (antes de E12b y E13) y E12b (antes de E14).

### Detalle de cada gate

Formato: **Alcance**, **Archivos autorizados**, **Restricciones**, **Pruebas y mutacion**, **Aceptacion y
evidencia**, **Cierre**. En todos rigen la seccion 2 y el protocolo de la seccion 5; "documentacion del gate" incluye
`CLAUDE.md` (arquitectura de lo agregado), `ARQUITECTURA.md` si corresponde y la bitacora. Cuando un gate no detalla un
campo, rige su valor comun:
- Dependencias: la columna "Depende de" de la tabla (gates CERRADO y definiciones respondidas).
- Restricciones: la seccion 2 completa; ningun archivo fuera de "Archivos autorizados".
- Pruebas: suite completa del protocolo (H2; MySQL 8.4 si hay SQL nuevo o cambiado; frontend si se toca; humo si
  cambia algo visible), sin bajar el numero de pruebas del gate anterior.
- Mutacion: al menos una por regla nueva o cambiada, con la evidencia "regla -> prueba que fallo".
- Aceptacion: los escenarios de la seccion 0 que el gate toca, verificados; en gates de interfaz, la regla 12.
- Evidencias: el registro de la seccion 7 con cifras, mutaciones, `git diff` de las condiciones y hallazgos.
- Cierre: todo lo anterior cumplido, estado CERRADO, un commit `gate ENN: <titulo>` y push; luego detenerse.

Los gates son mas amplios que en versiones anteriores. Si al planificar o ejecutar uno se ve que no cabe en una sesion
con todas sus verificaciones, se divide segun la seccion 5 (`ENNa` cerrado con lo verificado y `ENNb` ABIERTO), sin
reducir pruebas ni criterios.

**E00 - Linea base, decisiones, AGENTS.md y contrato del Formulario**
- Alcance, en este orden:
  1. Contrato del Formulario (solo pruebas): copiar a `backend/src/test/resources/formulario/` los vectores
     `formulario-nna-srcei-v1.0.json`, `formulario-nna-srcei-v1.1.json` y sus claves publicas de prueba de
     `formulario-web/contratos/fixtures/` en `6f886eb`, con un `PROCEDENCIA.txt` (commit, ruta y SHA-256 de cada
     archivo). Pruebas: `ExternalOriginal.parse` los admite con la clave de prueba; la COM registrada (camino de
     `conserveExternal` y de `FormularioReception` con productor falso) tiene `external_person.authenticated=false`,
     `run_source='SRCEI'`, `person_reference.authenticated=false` y region 13; un reenvio no reproyecta. Prueba de
     huellas de `territorio.json` y `paises.json` (backend y frontend) contra las del Formulario registradas en
     `PROCEDENCIA.txt`. Si una prueba falla por el codigo de recepcion, el gate queda BLOQUEADO con la evidencia, no se
     corrige nada y no se hace el resto del gate (seccion 3).
  2. Linea base: verificar contra el codigo cada referencia de la seccion 1 de este plan y de `DISENO` 1.2; suite
     completa (H2; MySQL 8.4 si hay servidor aislado; frontend; `Check-Scripts.ps1` si hay Windows).
  3. Decisiones: copiar D25-D51 textuales a `DECISIONES_FUNCIONALES.md` ("Precision 0.8.0"), con D44-D49 rotuladas
     "Hoja de ruta 0.9.0, no vigente en 0.8.0"; `CLAUDE.md`: estado "plan 0.8.0 en curso" con la regla de no trabajar
     sin instruccion intacta; crear `AGENTS.md` breve que remita a `CLAUDE.md` y al prompt de la seccion 8.
- Archivos autorizados: `docs/DECISIONES_FUNCIONALES.md`, `CLAUDE.md`, `AGENTS.md`, pruebas y recursos de prueba nuevos,
  la bitacora.
- Restricciones: ninguna linea de `backend/src/main` ni de `frontend/src` cambia.
- Pruebas y mutacion: conteos 191 / 194 / 112 y 40 scripts antes de agregar pruebas; mutantes de prueba (alterar
  `fuente`, `nivel` o la firma del vector) deben fallar.
- Aceptacion y evidencia: pruebas de contrato en verde; cada diferencia de referencia registrada; copia de D25-D51
  comprobada con `diff` contra el plan.
- Cierre: commit `gate E00: linea base y contrato del Formulario`.

**E01 - Volcado sintetico 0.7.0 y catalogos estadisticos versionados (servidor)**
- Alcance, en este orden:
  1. Volcado: generar `backend/src/test/resources/upgrade/siger-0.7.0-sintetico.sql` con el procedimiento de G13
     (`BITACORA_0_5_0.md`, registro G13) desde `v0.7.0`: cadenas completas, COM del Formulario adulto y NNA (incluidos
     los vectores de E00), fichas 1.0 y 1.1, REQ cerrados y reabiertos, transferencia, derivacion, coberturas, sede
     proyectada; claves invalidadas. Extender `MySql84UpgradeTest` (o una clase hermana) para cargarlo y migrar a la
     ultima version con conteos y huellas de `communication_original` iguales; pasa hasta V16 antes de agregar V17.
  2. Catalogos: migracion de `stat_catalog_version`, `stat_catalog_item`, `stat_catalog_mapping`; `StatCatalogs` (lista
     cerrada de catalogos de `DISENO` 3.1, incluido TRAMO_EDAD, valores especiales, lectura del vigente); comandos de TI
     `CATALOG_IMPORT` (archivo JSON, todo o nada, fundamento), `CATALOG_ACTIVATE` y `CATALOG_MAPPING_IMPORT` en
     `Personnel`; publicacion en `/api/catalog` del vigente de cada catalogo; auditoria `CATALOGO_IMPORTADO` y
     `CATALOGO_ACTIVADO`.
- Archivos autorizados: recursos y pruebas de `backend/src/test`; `db/migration/V17__*.sql`, `StatCatalogs.java`
  (nuevo), `Personnel.java`, `Workflow.java`, `Queries.java`, `Api.java`, documentacion del gate. El generador del
  volcado queda fuera del repositorio (worktree temporal), como en G13.
- Restricciones: el volcado solo contiene datos sinteticos (revision registrada); sin siembra de catalogos; una sola
  VIGENTE por catalogo; TI sin acceso a expedientes (barrido de D2).
- Pruebas y mutacion: actualizacion desde 0.4.0 y desde 0.7.0 sobre MySQL 8.4; importacion invalida sin escrituras,
  codigo repetido, padre inexistente, tramos solapados, segunda activacion retira la anterior, no TI 403,
  idempotencia.
- Cierre: commit `gate E01: volcado 0.7.0 y catalogos`.

**E02 - Administracion: catalogos de referencia, interfaz de TI y facultad GESTION_ESTADISTICA**
- Alcance: pantalla de TI "Catalogos estadisticos" (importar, ver versiones, activar con fundamento, correspondencias);
  archivos `deploy/catalogos/` con la propuesta version 1 de TIPO_SOLICITUD, TIPO_REQUIRENTE, VIA_INGRESO,
  TIPO_ACTUACION, TIPO_INSTITUCION y RELACION (valores de D29-D31, D36 y `DISENO` 2.2), CONTEXTO, CARACTERIZACION y
  TRAMO_EDAD como plantilla vacia para la institucion (DI-02), y `derechos-2022-v2.json` transcrito textual del modelo
  2.0 con numeracion y pagina, mas un `LEEME.txt` con fuente y estado (PROPUESTA). Funcion `GESTION_ESTADISTICA` en
  `Roles` (OPCIONAL, grupo REPORTES), `NEEDS_REPORTES`, incompatibilidad con TI, perfil de la cuenta y archivo
  institucional, sesion `can_manage_stats`.
- Archivos autorizados: `frontend/src/features/admin/` (pantalla y modelo nuevos, `profile.ts`, `ProfileDialog.tsx`),
  `app/menu.ts`, `App.tsx`, `lib/labels.ts`, `deploy/catalogos/*`, `Roles.java`, `Personnel.java`, `Bootstrap.java`,
  `Api.java`, pruebas de frontend y de servidor, documentacion.
- Pruebas y mutacion: cada archivo de `deploy/catalogos/` se importa sin errores en una prueba; frontend por modelo;
  la funcion nueva sin REPORTES rechazada; con TI rechazada.
- Aceptacion: verificacion manual del responsable de la transcripcion de derechos contra el PDF (registrada como
  pendiente o hecha). Facilidad de uso: TI importa y activa sin conocer el formato interno.
- Cierre: commit `gate E02: administracion de catalogos y gestion estadistica`.

**E03 - Normalizacion integral (servidor): persona canonica, ficha 1.2 y ficha de ingreso de la COM manual**
- Alcance:
  - `DeclaredPerson` (normalizacion y validacion de `persona-1`, `DISENO` 2.4), extraida de `AdultIdentity` sin cambio
    de comportamiento de la ficha 1.1.
  - Ficha 1.2 (`DISENO` 3.4): `Attention.normalize` acepta 1.2 con `people[]` `persona-1`, region y comuna con catalogo
    en NNA, `relationship_code`, hechos e instituciones; 1.0 y 1.1 legibles; `change_summary` derivado con marcador
    para versiones existentes; `FICHA_COMPLEMENTADA` informa complemento o rectificacion sin valores.
  - Ficha de ingreso de la COM manual (`DISENO` 3.5): migracion de `intake_record` e `intake_revision`; `COM_CREATE` y
    `COM_IMMEDIATE` la escriben con el esquema canonico y derivan las columnas de resumen; `COM_COMPLETE` (complementar
    o rectificar con motivo, cadena abierta); las COM anteriores siguen con `communication_person`.
  - `PersonIndex` y `OriginRegion` leen 1.2 y la ficha de ingreso (precision de D32 para la COM manual).
- Archivos autorizados: migracion nueva, `AdultIdentity.java`, `DeclaredPerson.java` e `IntakeRecord.java` (nuevos),
  `Attention.java`, `IntakeCommands.java` (sin `conserveExternal`), `PersonIndex.java`, `OriginRegion.java`,
  `Queries.java`, `Workflow.java`, `Bootstrap.java` (marcador), pruebas, documentacion.
- Restricciones: valores historicos conservados sin revalidarse; ningun nombre historico se divide; el texto libre de
  region NNA no se convierte en region; ninguna fuente de verdad duplicada.
- Pruebas y mutacion: las de 1.1 sin cambios (`AdultAttentionTest`); escenario 11; completar no es corregir; indice sin
  persona senalada ni "otra persona"; equivalencia de la region con `Access.suggestedRegion`; MySQL y actualizacion
  desde los dos volcados.
- Cierre: commit `gate E03: normalizacion, servidor`.

**E04 - Normalizacion integral (interfaz): componentes compartidos, ficha 1.2 y COM manual**
- Alcance: `lib/person/` y `components/person/` extraidos de `features/attention/adult/` (identidad, documento,
  nacionalidad con catalogo de paises, fecha de nacimiento, edad, region y comuna, contacto, relacion, instituciones);
  ficha 1.2 en `features/attention/` (personas, region y comuna en NNA, hechos, instituciones, vista imprimible, rotulo
  "Region y comuna del requirente" por DI-08, sin modificar D21); ficha de ingreso de la COM manual en
  `features/intake/` y `ComDetail.tsx` con los mismos componentes; logica en `<nombre>Model.ts`.
- Archivos autorizados: `features/attention/*`, `features/attention/adult/*`, `lib/person/*`, `components/person/*`,
  `features/intake/*`, `features/detail/ComDetail.tsx`, `schema.ts`, estilos, pruebas de frontend.
- Pruebas: las de 1.1 sin cambios (`adult.test.ts`, `adult-render.test.mjs`) y nuevas de los componentes; mutacion por
  regla de validacion.
- Aceptacion: la captura sigue siendo progresiva; nada nuevo es obligatorio; el ingreso urgente no se demora; movil.
- Cierre: commit `gate E04: normalizacion, interfaz`.

**E05 - Clasificacion estadistica de la COM: modelo, via, propuestas y trazabilidad (servidor)**
- Alcance:
  - Migracion de `stat_event`; `StatValues` (valor vigente, conjunto vigente, valor a una fecha, estado) en Java y SQL
    portable; `Access.statWritable`.
  - `STAT_CLASSIFY` (COM, variables de valor unico, motivo obligatorio al corregir un valor informado) y bloque
    opcional `stat` en `COM_CREATE`, `COM_IMMEDIATE`, `ATTENTION_CREATE`, `COM_ROUTE`, `COM_ASSUME`, `SOL_ASSUME` y
    `SOL_DISCARD` con la misma funcion interna.
  - Canales `REDES_SOCIALES` e `INTEGRANTE_DEFENSORIA`; via derivada del canal (informado, sistema); `StatProposals`
    version 1 (`DISENO` 4.2) leyendo `external_field` sin escribir.
  - Origen y referencia a la reapertura asignados por el servidor (`DISENO` 3.2; DI-07): RETROSPECTIVA sobre un REQ con
    un cierre anterior, reabierto con el mecanismo existente.
  - Ficha de COM y de REQ con `characterization` (estado, valor, propuesta, historial legible); inmutabilidad D35.
- Archivos autorizados: migracion nueva, `StatValues.java` y `StatProposals.java` (nuevos), `Access.java`,
  `IntakeCommands.java` (sin `conserveExternal`), `Attention.java`, `Lifecycle.java` (canales), `Workflow.java`,
  `Queries.java`, `frontend/src/lib/fields.ts` y `lib/labels.ts` (solo los canales), pruebas, documentacion.
- Restricciones: la recepcion del Formulario no cambia; las propuestas no son eventos; `git diff v0.7.0` vacio para
  `Reports.java` y para el flujo `REOPEN_*`; el cliente no fija origen ni reapertura.
- Pruebas y mutacion: escenario 4; historia sin borrado; correccion sin motivo rechazada; cada estado especial;
  registro cerrado rechazado y aceptado tras `REOPEN_DECIDE` con origen RETROSPECTIVA; lector institucional, TI y
  REPORTES sin escritura (barrido); equivalencia Java/SQL; MySQL.
- Cierre: commit `gate E05: clasificacion de la COM`.

**E06 - Derechos, contexto, caracterizacion y actuaciones tipificadas (servidor)**
- Alcance:
  - Derechos (`DISENO` 4.3 y 4.4): eventos ADD/REMOVE/STATUS de DERECHO, CONTEXTO y CARACTERIZACION para REQ y Caso;
    `RIGHTS_CLASSIFY` (conjunto agregado y retirado en un acto, fundamento al retirar); consolidacion con
    visibilidad; ficha de REQ y de Caso con `rights` (vigente, consolidado legible e historial).
  - Actuaciones (`DISENO` 3.3): migracion de `management_note.action_type`, `note_institution`,
    `task_item.action_type` y `requirement.immediate_result`; `CASE_NOTE`, `REQ_NOTE`, `NOTE_RECTIFY`, `TASK_CREATE`,
    `TASK_EDIT`, `COM_IMMEDIATE` y `ATTENTION_RESOLVE` aceptan la tipificacion; carga historica del resultado
    inmediato por el prefijo exacto con marcador y bandera de reconstruido; reserva intacta.
- Archivos autorizados: migracion nueva, `Rights.java` (nuevo), `StatValues.java`, `Access.java`, `Lifecycle.java`,
  `CaseCommands.java`, `RequirementCommands.java` (sin el flujo `REOPEN_*`), `IntakeCommands.java` (sin
  `conserveExternal`), `Attention.java`, `Bootstrap.java`, `Workflow.java`, `Queries.java`, pruebas, documentacion.
- Pruebas y mutacion: escenarios 6 y 8; item no seleccionable rechazado; catalogo retirado no admite nuevos; constancia
  reservada sigue reservada; rectificacion conserva la anterior; barrido de inmutabilidad; MySQL.
- Cierre: commit `gate E06: derechos y actuaciones`.

**E07 - Dimensiones territoriales y demograficas, completitud y reglas de cierre (servidor)**
- Alcance: `TerritorialDims` (`DISENO` 4.6 y 4.7) y `PersonDims` (edad y nacionalidad, `DISENO` 4.8), reglas unicas en
  Java y SQL; `StatCompleteness` (`DISENO` 4.5) usado por `Queries` (bloqueos visibles) y por los comandos de cierre y
  de descarte; indicadores de calidad y completitud por variable, ambito y periodo (base del panel de E13).
- Archivos autorizados: `TerritorialDims.java`, `PersonDims.java` y `StatCompleteness.java` (nuevos),
  `RequirementCommands.java` (cierre), `Attention.java` (resolucion), `IntakeCommands.java` (cierre en el acto y
  descarte; sin `conserveExternal`), `Queries.java`, pruebas, documentacion.
- Restricciones: sin cambiar `OriginRegion.current`, `Access.suggestedRegion`, `Territory` ni D22; solo datos
  estructurados; la fecha de nacimiento no sale de la regla; sin catalogo vigente nada bloquea.
- Pruebas y mutacion: escenarios 5, 7 y 14; NNA afectados de dos regiones y dos nacionalidades; COM sin REQ;
  coberturas que cambian en el tiempo; edad el dia del cumpleanos en hora de Chile; descarte duplicado.
- Cierre: commit `gate E07: dimensiones y completitud`.

**E08 - Motor estadistico: fuente unica, agregacion, series temporales y comparaciones (servidor)**
- Alcance: `StatSource` (`DISENO` 5.1-5.3) con todos los modos temporales y la relacion multivaluada de 4.4;
  `StatEngine` y `POST /api/stats/query` (`DISENO` 5.4-5.7): medidas (incluidas las de multiplicidad), filas y
  columnas, filtros, porcentajes con denominador, alcance en SQL, auditoria `STAT_CONSULTA`, salvaguarda tecnica de
  celdas; periodos predefinidos, series por mes, trimestre, semestre, anio y Cuenta Publica, modo a fecha historica,
  comparaciones con el periodo anterior y el mismo periodo del anio anterior, registros tardios. Conciliacion contra
  las tablas base y contra `Reports` donde coinciden (COM por canal; REQ por estado).
- Archivos autorizados: `StatSource.java` y `StatEngine.java` (nuevos), `Api.java`, `Queries.java` (catalogo de
  dimensiones), migracion de indices si el plan de ejecucion la pide, pruebas, documentacion.
- Pruebas y mutacion: escenarios 1 (agregado), 2, 3 y 15 (agregado); la fuente no multiplica filas; alcance por sede y
  por materia; sin REPORTES 403; TI 403; CSRF; GET 405; MySQL.
- Cierre: commit `gate E08: motor estadistico`.

**E08b - Motor estadístico: confinamiento, límites de ejecución y concurrencia (servidor)**
- Alcance:
  1. Límite de concurrencia de consultas estadísticas: 2 en total y 1 por cuenta, configurables. Rechazo inmediato
     con 429 y texto llano, sin cola, liberado también ante error.
  2. Tiempo máximo por consulta, configurable, con 30 s iniciales, cancelando la sentencia en MySQL. Respuesta en
     texto llano, auditada solo con nombres.
  3. Evaluación con medición de los límites por sesión de MySQL (`max_join_size` y temporales en memoria), calibrados
     con 50.005 COM. Se adoptan o se registra por qué no.
  4. Al arrancar sobre MySQL, comprobación de que `@@datadir`, `@@tmpdir` y `@@innodb_temp_tablespaces_dir` están en
     la unidad de la instancia. Si no, el motor estadístico queda deshabilitado con aviso técnico, sin afectar la
     operación.
  5. Procedimiento del MySQL desechable con `datadir` y `tmpdir` en D:, documentado en `CLAUDE.md`.
  6. Las guardas 1 y 2 serán reutilizables por E10 (cortes) y E11 (exportaciones).
- Archivos autorizados: `StatEngine.java`, una clase nueva de guardas, `Api.java`, `application.properties` (solo
  propiedades nuevas comentadas), `CLAUDE.md`, `docs/ARQUITECTURA.md`, `docs/CONTRATO_STATS_0_8_0.md`, pruebas y la
  bitácora.
- Restricciones: la sección 2 completa; sin cambios en `scripts/`, `.mvn/` ni `my.ini`; sin migración.
- Pruebas: concurrencia con hilos; cancelación por tiempo en H2 y MySQL; temporales mal ubicados; liberación ante
  error; una mutación por guarda.
- Cierre: commit `gate E08b: limites del motor estadistico`.

**E09 - Captura estadistica en el flujo (interfaz): caracterizacion, derechos, actuaciones y cierre**
- Alcance (`DISENO` 10.1 y 10.2): bloque "Caracterizacion del ingreso" en el ingreso manual, la distribucion, la
  atencion directa y la resolucion en el acto; tarjeta "Caracterizacion" en COM y REQ; `RightsPanel` en
  `RequirementDetail.tsx` despues de "Datos del REQ", en `CaseDetail.tsx` despues de "Datos del Caso", con acceso desde
  "Registrar actuacion" y desde la primera atencion; dialogos de actuacion y de tarea con tipo e instituciones;
  resolucion de primera atencion con resultado; dialogo de cierre que muestra y permite resolver lo pendiente.
- Archivos autorizados: `features/intake/*`, `features/detail/*` (COM, SOL, REQ, Caso, `actions.ts`, `Notes.tsx`,
  `ClosureStatus.tsx`), `features/attention/*`, `components/RightsPanel.tsx` y `lib/rightsModel.ts` (nuevos),
  componentes nuevos, `lib/fields.ts`, `lib/labels.ts`, estilos, pruebas de frontend.
- Aceptacion: U10; el ingreso urgente no exige campos nuevos; la propuesta se reconoce como propuesta; registro cerrado
  con "Requiere reapertura formal"; lector sin botones; movil.
- Cierre: commit `gate E09: captura estadistica, interfaz`.

**E10 - Cortes estadisticos y configuraciones guardadas (servidor)**
- Alcance: `DISENO` 6 completo (migracion, `STAT_CUT_CREATE`, `STAT_CUT_ANNUL`, verificacion, comparacion, consulta
  sobre cortes con el mismo motor, relacion multivaluada congelada) y `stat_view` personal y compartida (compartir exige
  GESTION_ESTADISTICA), con revision y auditoria `VISTA_COMPARTIDA`.
  Dimensión ADSCRIPCION_INICIAL (DI-13), con la misma regla temporal que SEDE_INICIAL: si el ámbito es regional, el
  código de su sede; si es del nivel central, OFICINA_NACIONAL; sin destino, PENDIENTE con el rótulo 'Pendiente de
  asignación'. Se congela en los cortes.
- Archivos autorizados: migracion nueva, `StatCuts.java` y `StatViews.java` (nuevos), `StatEngine.java`, `StatSource.java`,
  `Workflow.java`, `Api.java`, pruebas, `docs/CONTRATO_STATS_0_8_0.md` (cambio aditivo por DI-13) y documentación.
- Pruebas y mutacion: escenario 9; huella canonica estable (incluida la relacion multivaluada); nadie modifica un
  corte (barrido); un corte reproduce el agregado de la consulta vigente del mismo instante; alcance de cortes y
  vistas. Para DI-13, la matriz usa cuatro ingresos computables registrados antes de `t` (2026-07-10 a las 12:00,
  hora de Chile). D se distribuye y se asume después de `t`. Las cifras esperadas se fijan a mano.

  | Regla y dato sintético | Cifras esperadas | Mutación que debe fallar |
  |---|---|---|
  | A es una COM sin destino. | A aporta 1 a PENDIENTE («Pendiente de asignación»). | Devolver un valor nulo para la COM sin destino. |
  | B se adscribió a un ámbito central. | B aporta 1 a OFICINA_NACIONAL («Oficina Nacional»). | Usar el código del ámbito central como categoría. |
  | C se adscribió a REG-06 y antes de `t` se transfirió a REG-07. | C aporta 1 a REG-06 y 0 a REG-07. | Usar la sede vigente tras la transferencia. |
  | En el REQ de D constan dos NNA de las regiones 05 y 07. | D aporta 1 a REG-05 en adscripción. La dimensión de región del NNA aporta 1 a 05 y 1 a 07 para D y se rotula como no aditiva. | Contar una vez por NNA en el desglose de adscripción. |
  | D se distribuye a REG-05 después de `t`. Consultar a `t` y generar un corte en `t` que se relee tras la distribución. | D aporta 1 a PENDIENTE y 0 a REG-05 en la consulta a `t` y en el corte. En la consulta vigente posterior aporta 1 a REG-05. | Usar una adscripción posterior al instante de consulta o corte. |
  | Suma del desglose de los cuatro ingresos. | A `t` y en el corte, 2 PENDIENTE + 1 OFICINA_NACIONAL + 1 REG-06 = 4. Después, 1 PENDIENTE + 1 OFICINA_NACIONAL + 1 REG-06 + 1 REG-05 = 4. | Excluir Oficina Nacional de las filas del desglose y conservarla en el total general. |

- Cierre: commit `gate E10: cortes y configuraciones`.

**E11 - Exportaciones CSV, XLSX y ZIP e importacion de series historicas (servidor)**
- Alcance: evaluacion registrada de la dependencia (`DISENO` 12); escritor XLSX propio y deterministico y lector
  endurecido (zip limitado, sin DTD); paquete ZIP (`DISENO` 8) desde vigente, fecha historica o corte, con microdatos
  (una fila por requerimiento y archivo relacionado de valores multiples) con EXPORTACION; auditoria
  `STAT_EXPORTACION`. Series (`DISENO` 7): migracion con el ambito, plantilla, vista previa sin escrituras,
  `STAT_SERIES_IMPORT` y `STAT_SERIES_ANNUL` dentro del alcance de GESTION_ESTADISTICA, visibilidad por el alcance de
  REPORTES, superposiciones avisadas, consulta junto a SIGER sin combinar.
- Archivos autorizados: migracion nueva, `Xlsx.java`, `XlsxReader.java`, `StatExport.java` y `StatSeries.java`
  (nuevos), `Csv.java` (solo si hace falta compartir la celda), `StatEngine.java`, `Api.java`, pruebas, documentacion.
- Restricciones: si el escritor o lector propio no basta, detenerse y pedir DI-12.
- Pruebas y mutacion: escenarios 10 y 15; la re-agregacion del microdato y del archivo relacionado reproduce el
  agregado; ningun folio repetido; huellas de `SHA256SUMS.txt`; XLSX leido de vuelta; sin texto ciudadano ni fecha de
  nacimiento (barrido de columnas); importar fuera del propio alcance 403; zip malicioso rechazado; archivo con
  errores sin escrituras.
- Cierre: commit `gate E11: exportaciones y series`.

**E12 - Explorador estadistico: tablas, filtros, porcentajes, graficos y comparaciones (interfaz)**
- Alcance: el `DISENO` 10.3 completo ("Explorador estadístico": `#estadisticas` con Panorama, Informes y Explorar);
  logica en `statsModel.ts`. Exporta CSV y XLSX solo en Informes. Sin cambios de servidor ni dependencias nuevas. Se
  divide solo si es indispensable.
- Archivos autorizados: `features/stats/*` y `components/charts/*` (nuevos), `app/menu.ts`, `App.tsx`,
  `app/routes.ts`, `lib/labels.ts`, estilos (`styles/tokens.css` solo con agregados), pruebas de frontend.
- Pruebas:
  - modelo de consulta y de graficos (el circular suma 100 % y no se ofrece para multivaluadas ni con columnas);
  - de `statsModel.ts`: formato chileno, sugerencia de gráfico, reglas del circular, porcentajes, cola de consultas y
    caché, ida y vuelta de la dirección, traducción de cada aviso y pie de fuente;
  - render SSR de cada sección, con barridos sin códigos y accesibilidad (roles, nombres accesibles y alternativa
    textual);
  - si el ejecutor dispone de navegador, capturas de cada sección en escritorio y móvil, guardadas fuera del
    repositorio y mencionadas en el registro.
- Aceptacion: U1, U2, U3, U7, U8, U11, U12 y U13 en el humo; sin codigos visibles; contraste y lectura sin color
  solamente; `prefers-reduced-motion`; movil.
- Cierre: commit `gate E12: explorador`.

**E12b - Desestimación directa de una COM (servidor e interfaz)**
- **Alcance.** `DISENO` 4.5.1, «Desestimación directa de una COM». El GO define la adscripción (DI-13) y la sede
  inicial de una COM desestimada sin SOL.
- **Restricciones.** Conservar `conserveExternal`, la recepción del Formulario, `external_*` y
  `communication_original` sin cambios. Mantener `Reports.java` intacto. Si hiciera falta modificarlo, detenerse
  y consultar. Conservar V1-V24 y agregar una migración solo si es indispensable.
- **Archivos autorizados.** `IntakeCommands.java` (sin tocar `conserveExternal`), `Workflow.java` (despacho),
  `Lifecycle.java` (hito), `StatSource.java` y `TerritorialDims.java` (solo si el hito afecta las dimensiones),
  `Queries.java` (acciones disponibles), `frontend/src/features/detail/{ComDetail.tsx,actions.ts}`, pruebas y
  documentación.
- **Pruebas.** Preparar una matriz con cifras esperadas escritas a mano.
  - Una `INVALIDA` sale de `INGRESOS` y entra en `EXCLUIDOS`. Una `DUPLICADA` sigue contando en `INGRESOS`.
  - Sin facultad, HTTP 403. Con SOL pendiente o con REQ, HTTP 409.
  - Se exige fundamento y se rechaza una `DUPLICADA` sin caracterización mínima.
  - Verificar `ESTADO_COM` y la adscripción según el GO. La Figura 1 y un corte incluyen la `DUPLICADA`.
  - Los resultados de `Reports` para el resto se conservan.
  - Ejecutar `InstitutionalReaderTest` e `ImmutabilityTest`, pruebas HTTP y mutaciones.
  - Ejecutar las suites H2 y MySQL completas, frontend, humo y `Check-Scripts.ps1`.
- **Cierre.** Commit `gate E12b: desestimacion directa de COM`.

**E13 - Explorador: exportacion, cortes, series historicas y calidad (interfaz)**
- Alcance: exportacion CSV, XLSX y ZIP desde cualquier vista y desde cortes, con los mensajes de las restricciones del
  ZIP (limite de folios, solo medidas de requerimientos, grupos prioritarios y cortes anteriores a V24); pantallas de
  `DISENO` 10.4 (cortes: generar, ver, verificar, comparar y anular; asistente de series; panel de calidad con enlace a
  los registros pendientes que la cuenta puede leer). Ademas: "Congelar estas cifras" desde cualquier vista (tambien el
  boton de Informes), que abre el asistente de corte prellenado y advierte "Generar el corte detiene brevemente las
  operaciones de todos. Hazlo fuera del horario de atención"; la comparacion con un corte resalta las diferencias celda
  a celda; una serie historica en el mismo grafico que SIGER, con la discontinuidad marcada y la nota de ambito, sin
  sumarlas salvo que se pida; y Calidad con barras de completitud por variable y ambito, tendencia mensual y enlace a
  los pendientes. Las bandejas existentes agregan el filtro "Con clasificación pendiente" en `BoardQueries.filter`, con
  las mismas reglas de lectura de la bandeja y sin ruta nueva; el panel de Calidad enlaza a ese filtro.
- **Ajustes de presentación de E12** (revisión de capturas del 2026-11-26, `DISENO` 10.3).
  1. En la Figura 1, `PENDIENTE`, `SIN_INFORMACION`, `NO_DECLARADO` y `NO_APLICA` con 0 se listan en una nota
     bajo la tabla («Sin registros en: ...»), sin fila ni barra. Con más de 0 aparecen como fila. Los ítems del
     catálogo con 0 conservan su fila.
  2. En Informes, desde unos 1.200 px de ancho, la tabla y el gráfico van lado a lado. En móvil van apilados.
  3. El eje de periodo siempre es horizontal, con columnas o líneas. La comparación de periodos usa columnas
     pareadas o dos líneas, sin barras horizontales.
  4. Las tablas anchas muestran una sombra o un indicador de desplazamiento horizontal, fijan la primera columna y
     mantienen completas las cifras y los encabezados.
  5. En tablas, mostrar la variación con flecha y cifra («+1», «-2 (-15,4 %)», «=»). Mostrar «Sin base de
     comparación» una sola vez como nota al pie de la tabla.
  6. Mostrar «Datos de las HH:MM» una sola vez por sección si todas sus consultas son del mismo momento.
- Archivos autorizados: `features/stats/*`, estilos, pruebas de frontend, `BoardQueries.java` y pruebas de backend.
- Pruebas:
  - barrido de que el filtro "Con clasificación pendiente" solo muestra lo que la cuenta puede leer;
  - las cifras del filtro iguales a `PENDIENTE` de la completitud de su alcance;
  - mutacion;
  - pruebas de render y de modelo para cada ajuste de presentación, con su mutación;
  - registrar como desvío autorizado las aserciones de E12 que cambien por estos ajustes y conservar todas las demás;
  - repetir en las capturas de E13 los mismos escenarios de E12 para comparar;
  - suites H2 y MySQL completas.
- Aceptacion: U4, U5, U6 y U9, mas el aviso de espera del corte; el asistente de series se completa sin conocer el
  formato interno.
- Cierre: commit `gate E13: exportacion, cortes, series y calidad`.

**E14 - Integracion MySQL, rendimiento, aceptacion y cierre de version**
- Alcance: suite completa sobre MySQL 8.4 con los dos volcados; medicion de rendimiento con 50.000 COM sinteticas
  (`DISENO` 5.7); escenario 1 completo (pantalla, exportación y corte) con `ADSCRIPCION_INICIAL`; barrido final de
  inmutabilidad y de permisos; version 0.8.0; documentacion de la seccion 4; `PRUEBAS_MANUALES_0_8_0.md` con escenarios
  1-15 y U1-U13; humo y paso U de `DESPLIEGUE_VM.md` con los scripts existentes y las siguientes compensaciones:
  - (a) Consultas previas de solo lectura. Confirmar versión 0.7.0 y esquema 16 (DI-15), diario de `Update.ps1` en
    `COMPLETE`, rutas de `SELECT @@datadir, @@tmpdir, @@innodb_temp_tablespaces_dir` en D: y espacio libre en D: según
    la medición.
  - (b) Ejecutar `Backup.ps1` y `Restore-Check.ps1`, con `completed=true` en la verificación, en la misma ventana e
    inmediatamente antes de `Update.ps1`.
  - (c) Usar una ventana sin usuarios. Si es antes del 15/12, dejar al menos dos días de margen.
  - (d) Ante `MIGRATION_FAILED`, `PROMOTION_FAILED` o `START_FAILED`, detenerse. Se prohíbe reintentar, reparar
    Flyway, borrar el diario o volver al JAR anterior. Conservar el respaldo, el descriptor, el diario y los logs
    (`INSTALACION_VM.md`, «Actualizacion y recuperacion de error»). E14 verifica en el código y documenta qué ocurre
    con las entregas del Formulario mientras SIGER está detenido.
  - (e) Después de actualizar, ejecutar `Estado.ps1` y confirmar el esquema de la última migración, los marcadores
    `RESUMEN_CAMBIOS_V18`, `RESULTADO_INMEDIATO_V20` y `DIMENSIONES_V21`, las filas y huellas de
    `communication_original` iguales a las del respaldo y `stats.enabled` verdadero.
  - (f) TI activa los catálogos con fundamento, citando la conformidad de DI-01 a DI-04.
- **Condiciones comprometidas.**
  - Trasladar la hoja de ruta 0.9.0 (sección 9) a `docs/ALCANCE_Y_PENDIENTES.md`.
  - Revisar con `EXPLAIN` las consultas que leen tablas recién llenadas en MySQL, con estadísticas de tabla vacía
    (hallazgo de E11). Sugerir un índice donde corresponda.
  - Incluir la carga inicial del Panorama en la medición con 50.000 COM.
- **Correcciones de presentación de E13** (revisión de capturas del 2026-11-26; encargo del responsable).
  1. Etiquetas de periodo, en columnas y en líneas: abreviadas («jul 2026») o en dos líneas, nunca superpuestas ni
     cortadas. El nombre completo queda en la tabla y en la información emergente.
  2. Comparación con un corte:
     - el gráfico muestra la cifra actual y la del corte, pareadas;
     - el circular no se ofrece;
     - «Cómo leer esta cifra» dice «Comparación con el corte <código>».
  3. Con un eje de periodo, el texto que explica por qué no se ofrecen barras pareadas dice «Usa columnas pareadas»,
     coherente con el botón.
  4. Periodo sin registros: en lugar del gráfico, «Sin registros en el periodo». La nota «Sin registros en: ...» aparece
     una sola vez por desglose.
  5. Calidad: sin enlace a la bandeja cuando la variable tiene 0 % pendiente.
- Archivos autorizados: pruebas; indices si la medicion los pide (migracion nueva, con evidencia); archivos de version;
  documentos de la seccion 4; para las correcciones de presentación de E13, `frontend/src/features/stats/*` y
  `frontend/src/components/charts/*`.
- Restricciones: si la medicion no cumple, detenerse y proponer la alternativa de `DISENO` 5.7 (decision expresa).
- Pruebas: suite H2 y MySQL, frontend, `Check-Scripts.ps1` (guias enlazadas).
  - Correcciones de presentación de E13: pruebas de modelo y de render para cada punto, cada una con su mutación.
  - Capturas: las de E14 repiten Informes, Explorar comparado (por periodo y con un corte) y Calidad, en escritorio y
    móvil.
- Cierre: commit `gate E14: integracion y cierre de version`.

**E15 - Validacion de uso con la Direccion de UEP** (EXTERNO)
POSTERGADO por decisión del responsable (2026-11-25); ver bitácora.

**E16 - Auditoria independiente** (EXTERNO): Claude Padre audita sobre un clon limpio del cierre de E14 o de su última
corrección. Revisa la evidencia del humo U1-U13 de E09, E12, E13 y E14, el diff completo contra
`v0.7.0`, las condiciones de la seccion 2,
trazabilidad de D25-D51 (las vigentes) y de los escenarios, compatibilidad con el Formulario, migraciones y
actualizacion, evidencia de mutacion, re-ejecucion de la suite por el responsable en un clon limpio; procedimiento de
bloqueo y repeticion de la seccion 5.

Además, E16 revisa el incidente de C: de E08 y el error 1112 de MySQL, el cambio en `Workflow.java`, `StatGuard` y
el lock de los cortes (decisión A de E10), la memoria y el ZIP exclusivo de E11, y los gates de Codex (E07, la
preparación de E08, E11 y E12b si lo ejecuta Codex) junto con los cierres hechos por otro agente.

**E17 - Build de entrega y etiqueta**
- Precondiciones: ultima auditoria (E16 o su ultima repeticion) CERRADO sobre el commit que contiene todas las
  correcciones de E16; ningun gate de correccion ABIERTO; DI-15 respondida.
- Alcance, en este orden:
  1. Build en Windows como T06a, desde ese commit: `manifest.json` con 0.8.0, esquema derivado y
     `automatedTestsExecuted` true; `SHA256SUMS.txt`; `scripts\` identico al del paquete 0.7.0; `Check-Scripts.ps1`;
     nombre y SHA-256 del ZIP.

  Se omite el paso 2 (ensayo de actualización en TEST-SIGER) por decisión del responsable del 2026-11-25.
  Las compensaciones se incorporan al paso U en E14.

  3. Limpieza y `SOURCE_SHA256.txt`. Crear la etiqueta anotada `v0.8.0` solo si todas las comprobaciones del gate
     resultan conformes. Ante un fallo, dejar BLOQUEADO con la evidencia y sin etiqueta.
- Cierre: commit `gate E17: build y etiqueta`, con "Cierre del plan 0.8.0".

## 7. Formato del registro de cierre (en la bitacora)

Maximo 25 lineas por gate; lo importante es lo que necesita la siguiente sesion.

```
### ENN - <titulo>   (CERRADO | BLOQUEADO)
- Fecha: AAAA-MM-DD (commit `gate ENN: ...`), equipo y ejecutor (Claude Code o Codex); base `<commit>`
- GO del responsable: <condiciones del GO>
- Hecho: <cambios principales, en 2-6 lineas>
- Archivos clave: <rutas>
- Migraciones: <Vn y su contenido | no aplica>
- Verificacion: JUnit H2 <n> ok (antes <m>); MySQL <n> ok | no aplica; frontend <n> ok; build ok; scripts <ok | no aplica>; humo <ok | no aplica>
- Evidencia de mutacion: <regla -> prueba que fallo | no aplica>
- Condiciones de la seccion 2: <comprobadas con git diff>
- Facilidad de uso: <como se comprobo | no aplica>
- Definiciones usadas: <DI-xx y su respuesta | ninguna>
- Desvios del plan: <ninguno | que y por que>
- Hallazgos para gates siguientes: <datos que la proxima sesion debe saber>
- Pendientes: <tarea -> gate o paso que la toma>
- Verificacion manual sugerida al usuario: <pasos | no aplica>
```

## 8. Prompt universal de continuidad (identico en todas las sesiones)

El responsable inicia cada sesion de Claude Code con `/clear`, o una conversacion nueva de Codex, y pega siempre este
mismo texto, sin cambios. No nombra ningun gate. La fuente de verdad es el repositorio, no la conversacion.

> Lee íntegramente `AGENTS.md` (si existe), `CLAUDE.md`, `docs/PLAN_0_8_0.md` y `docs/BITACORA_0_8_0.md`, y las secciones de `docs/DISENO_0_8_0.md` que indique el gate.
> Identifica el primer gate `ABIERTO` según la bitácora (salta los `CERRADO`, `BLOQUEADO`, `POSTERGADO` y `EXTERNO`) y trabaja exclusivamente en él, respetando el protocolo de la sección 5 del plan.
> Antes de modificar cualquier archivo:
> * Verifica el estado de Git, el commit del último gate cerrado, las dependencias del gate y las definiciones que exige.
> * Revisa los antecedentes, decisiones, restricciones y hallazgos aplicables.
> * Realiza el inventario técnico.
> * Presenta el plan de implementación, los archivos que propones modificar, las pruebas previstas (incluidas las de mutación y MySQL) y los riesgos.
> * DETENTE y espera mi aprobación explícita (GO).
>
> Únicamente después del GO:
> * Ejecuta el gate dentro del alcance aprobado.
> * Realiza las pruebas exigidas, incluidas las de regresión y mutación cuando correspondan.
> * Comprueba las condiciones de la sección 2 del plan.
> * Documenta el cierre en la bitácora con el formato de la sección 7.
> * Revisa el alcance del commit y la ausencia de archivos sensibles.
> * Realiza commit y push a `main` en `origin`.
> * Informa el cierre y DETENTE. No comiences el siguiente gate.
>
> No modifiques estados ni registros de gates `EXTERNO`. No dependas de conversaciones anteriores ni de información no documentada. Si falta información, existe una discrepancia o necesitas ampliar el alcance, detente y consulta antes de proceder.
> Responde siempre en español.

Cada cierre deja en la bitacora todo lo que la sesion siguiente necesita: ninguna sesion depende de lo conversado en
otra.

## 9. Hoja de ruta 0.9.0 (postergado; ningun gate de 0.8.0 lo implementa)

Postergado por decision del responsable del 2026-11-24 para concentrar 0.8.0 en sus dos objetivos. Las decisiones y
definiciones ya aprobadas se conservan como base de la planificacion de 0.9.0; el diseno de cada punto, con alcance,
archivos previstos y pruebas, esta en `DISENO_0_8_0.md` seccion 15.

| Punto | Decision | Definicion aprobada | DISENO |
|---|---|---|---|
| Fechas de las sedes: apertura proyectada, apertura efectiva y activacion | D44 | DI-06 | 3.6, 15.1 |
| Distribucion territorial del Formulario por coordinacion regional | D45 | DI-05 | 15.2 |
| Correo de avisos: asunto por evento, folio, descripcion breve y enlace | D46 | DI-09 | 15.3 |
| Observaciones de funcionarios (registro en la bitacora de 0.8.0) | D47 | - | 15.6 |
| Indice de `inbox_notice`: evaluacion con medicion | D48 | - | 15.4 |
| Antiguedad del respaldo en `Update.ps1` | D49 | - | 15.5 |
| Paso de RDP a acceso por red (requisito del enlace directo del correo) | D49 | - | 15.5 |
| Renovacion del certificado autofirmado | D49 | DI-10: desestimada; se mantienen certificado y HTTPS | 15.5 |
| Dimension estadistica "instituciones de los hechos" (ficha, ficha de ingreso y Formulario) | D38 | GO de E08 (2026-11-25) | 15.7 |
| Pantallas de resolución en el acto con derechos (COM_IMMEDIATE y ATTENTION_CREATE con resolve_now) | - | GO de E09, decisión D | - |
| Caracterización de la COM en la ficha de la SOL (evita leer la ficha de la COM en el descarte) | - | hallazgo de E09 | - |
| Corte sin lock global: instantánea consistente con bloqueo de milisegundos (opción D de la detención de E10), si el volumen se acerca al plazo | - | detención de E10 | - |
| Microdatos a nivel de actuación y de Caso en el paquete estadístico | - | GO de E11 | - |
| Paquete ZIP escrito por flujo a un temporal (menos memoria; opción 4 de la detención de E11) | - | detención de E11 | - |
| Figura 1 en un solo libro XLSX (cuatro hojas) | - | GO de E12 | - |
| Otras mejoras operacionales sin relacion directa con los objetivos de 0.8.0 | - | - | - |

En 0.8.0 la seguridad de la actualizacion se mantiene con los scripts y procedimientos vigentes: respaldo con
`Backup.ps1` y verificacion con `Restore-Check.ps1` en la misma ventana, inmediatamente antes de `Update.ps1` (seccion 3
y paso U de `DESPLIEGUE_VM.md`), verificación de la migración en MySQL 8.4 y auditoria en E16.
