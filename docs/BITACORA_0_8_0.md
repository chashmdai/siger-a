# SIGER 0.8.0 - bitacora de gates

Estado vivo del trabajo de `docs/PLAN_0_8_0.md` (diseno de referencia: `docs/DISENO_0_8_0.md`). Cada sesion de Claude
Code o Codex toma el primer gate ABIERTO, lo cierra aqui con el formato de la seccion 7 del plan, hace commit y push, y
se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado, con commit y push), BLOQUEADO (detenido o a la espera de una
definicion; ver su registro o la tabla de definiciones), POSTERGADO (fuera del cierre de 0.8.0 por decision expresa del
responsable, con su fundamento), EXTERNO (lo realiza el responsable fuera del ejecutor; el ejecutor lo salta y nunca
edita su registro ni su estado).
Cada sesion empieza con el prompt universal de la seccion 8 del plan, sin nombrar gates.

## Estado

| Gate | Titulo | Estado |
|---|---|---|
| E00 | Linea base, decisiones, AGENTS.md y contrato del Formulario (documentos y pruebas) | CERRADO |
| E01 | Volcado sintetico 0.7.0 y catalogos estadisticos versionados (servidor) | CERRADO |
| E02 | Administracion: catalogos de referencia, interfaz de TI y facultad GESTION_ESTADISTICA | CERRADO |
| E03 | Normalizacion integral (servidor): persona canonica, ficha 1.2 y ficha de ingreso de la COM manual | CERRADO |
| E04 | Normalizacion integral (interfaz): componentes compartidos, ficha 1.2 y COM manual | CERRADO |
| E05 | Clasificacion estadistica de la COM: modelo, via, propuestas y trazabilidad (servidor) | CERRADO |
| E06 | Derechos, contexto, caracterizacion y actuaciones tipificadas (servidor) | CERRADO |
| E07 | Dimensiones territoriales y demograficas, completitud y reglas de cierre (servidor) | CERRADO |
| E08 | Motor estadistico: fuente unica, agregacion, series temporales y comparaciones (servidor) | CERRADO |
| E08b | Motor estadístico: confinamiento, límites de ejecución y concurrencia (servidor) | CERRADO |
| E09 | Captura estadistica en el flujo (interfaz): caracterizacion, derechos, actuaciones y cierre | CERRADO |
| E10 | Cortes estadisticos y configuraciones guardadas (servidor) | CERRADO |
| E11 | Exportaciones CSV, XLSX y ZIP e importacion de series historicas (servidor) | CERRADO |
| E12 | Explorador estadistico: tablas, filtros, porcentajes, graficos y comparaciones (interfaz) | CERRADO |
| E12b | Desestimación directa de una COM (servidor e interfaz) | ABIERTO |
| E13 | Explorador: exportacion, cortes, series historicas y calidad (interfaz) | ABIERTO |
| E14 | Integracion MySQL, rendimiento, aceptacion y cierre de version | ABIERTO |
| E15 | Validacion de uso con la Direccion de UEP | POSTERGADO |
| E16 | Auditoria independiente | EXTERNO |
| E17 | Build de entrega y etiqueta | ABIERTO |

Todo gate depende de E00 y de su contrato del Formulario (seccion 3 del plan). Un gate ABIERTO cuyas dependencias
(columna "Depende de" de la seccion 6 del plan) no estan CERRADO se detiene en sus precondiciones. Mientras E15 esté
POSTERGADO, E16 empieza con E14 CERRADO y E17 exige el ciclo de E16 cerrado, sin correcciones ABIERTO (seccion 5 del
plan). Los pendientes operacionales postergados no tienen gate en 0.8.0 (seccion 9 del plan).

## Decisiones del responsable

| Decision | Antes de | Respuesta |
|---|---|---|
| DP-01 a DP-18 del diagnostico, resueltas (aqui D25-D51) | E00 | 2026-11-24: resueltas por el responsable; se autoriza la elaboracion del plan, no su implementacion. |
| Plan definitivo 0.8.0: D25-D51, prefijo E, 18 gates (E00-E17), alcance reconsiderado, `DISENO_0_8_0.md` y prompt universal | E00 | 2026-11-24: aprobado por el responsable, sin mas modificaciones. Se mantienen el prompt universal, la autorizacion individual (GO) de cada gate y todas las condiciones de seguridad, pruebas y auditoria. Listo para iniciar E00. |
| Reconsideracion del alcance de 0.8.0 | E00 | 2026-11-24: 0.8.0 se concentra en normalizacion integral e inteligencia de negocios para Estudios. Se postergan a 0.9.0, conservando sus decisiones como hoja de ruta: fechas de apertura efectiva de sedes, distribucion territorial del Formulario, mejoras de correo, optimizacion de `inbox_notice`, acceso por red y otras mejoras operacionales (incluidas la antiguedad del respaldo en `Update.ps1` y la incorporacion de observaciones de funcionarios). Se mantienen compatibilidad con el Formulario, migraciones, seguridad de actualizacion, respaldos y auditoria independiente. Gates consolidados por dependencias reales: de 45 a 18 (E00-E17). Aprobada el 2026-11-24. |
| Funcion GESTION_ESTADISTICA (D43) | E02 | 2026-11-24: aprobada. |
| Control automatico de antiguedad del respaldo en `Update.ps1` | Paso U | 2026-11-24: postergado a 0.9.0 (D49). En 0.8.0 se mantienen el respaldo (`Backup.ps1`) y su verificacion (`Restore-Check.ps1`) inmediatamente antes de actualizar (seccion 3 del plan; E14 lo documenta en el paso U y E17 lo ensaya). |
| Verificacion contractual del Formulario como primer paso de E00 | E00 | 2026-11-24: aprobada como condicion obligatoria para continuar: si no resulta conforme, E00 queda BLOQUEADO y ningun gate avanza (secciones 3 y 5 del plan). |
| El contrato del Formulario debe resultar conforme antes de continuar | E01 | 2026-11-24: incorporado; tras la consolidacion es el paso 1 de E00 y todo gate depende de E00 (secciones 3 y 5 del plan). |
| Validacion de uso cerrada y auditoria independiente sobre sus correcciones antes del build | E16, E17 | 2026-11-24: incorporado (seccion 5 del plan, E15, E16 y E17). |
| Exportacion de dimensiones multivaluadas sin duplicar requerimientos ni perder informacion | E08, E10, E11 | 2026-11-24: incorporado (D40; `DISENO` 4.4 y 8; escenario 15). |
| Ambito y visibilidad de las series historicas | E11 | 2026-11-24: incorporado (D42; `DISENO` 7; escenario 10). |
| Edad y nacionalidad como dimensiones estadisticas cuando exista informacion suficiente | E07, E08, E12 | 2026-11-24: incorporado (D38; `DISENO` 4.8; escenario 14; tramos en TRAMO_EDAD, DI-02). |
| Graficos circulares | E12 | 2026-11-24: incorporado (D38; `DISENO` 10.3). |
| Plazo: 15 de diciembre de 2026, conservando seguridad, pruebas y auditoria | - | 2026-11-24: registrado; ruta critica y calendario de referencia en las secciones 3 y 6 del plan. |
| Ejecutor: Claude Code o Codex, con el mismo prompt | E00 | 2026-11-24: decidido por el responsable. `AGENTS.md` remite a `CLAUDE.md` (E00). |
| Supresion estadistica, modo publicable o limite de dimensiones | - | 2026-11-24: desestimados (D41). |
| Division anticipada de 0.8.0 | - | 2026-11-24: no, en un primer momento (D51); el mismo dia el responsable reconsidero el alcance y posterga los pendientes operacionales a 0.9.0 (fila "Reconsideracion del alcance"). |
| Rol del Estado y Grupos prioritarios como dos catalogos y variables nuevas (ROL_ESTADO, GRUPO_PRIORITARIO), de seleccion multiple a nivel de REQ y Caso, con la misma maquinaria que CONTEXTO | E06 | 2026-11-24: aprobado. La lista cerrada de `StatCatalogs.java` pasa de 10 a 12 catalogos y el eje VII sale del catalogo de derechos. Se implementa en E06 (servidor), E08 (dimensiones), E09 (panel de derechos) y E11 (exportacion), sin gates nuevos. |
| Grupos prioritarios como dato sensible | E08, E11 | 2026-11-24: aprobado. Se muestran en el explorador, en agregados y en cortes como cualquier dimension; en los microdatos solo con REPORTES_PERSONAS y EXPORTACION en el mismo alcance. Riesgo documentado en el diccionario y en `LEEME.txt` del paquete de exportacion. |
| Definicion operativa por item de catalogo | E06, E09, E11 | 2026-11-24: aprobado. Campo opcional `descripcion` por item en el formato `siger-catalogo-1` y columna en la migracion de E06; ayuda en pantalla (E09) y diccionario de la exportacion (E11). |
| Regla de "Oficio" frente a otros tipos de actuacion | E06 | 2026-11-24: aprobada. Una actuacion tiene un solo tipo y se clasifica por su proposito; "Oficio" queda para los oficios cuyo proposito no corresponde a otro tipo. Se escribe en la definicion del item. |
| Resultado de la primera atencion sin doble conteo | E06, E08 | 2026-11-24: aprobado. Se cuenta desde una sola fuente (`requirement.immediate_result`), no tambien desde su constancia; el origen (primera atencion, gestion de REQ o gestion de Caso) se deriva de los datos. |
| Correspondencia de categorias historicas en el asistente de series | E11, E13 | 2026-11-24: aprobada. Al importar una serie se puede indicar a que valor o agrupacion de SIGER corresponde cada categoria historica, sin combinarlas automaticamente. |
| Archivos de referencia segun DI-01 a DI-04 | E06 | 2026-11-24: aprobado. E06 actualiza `deploy/config-ejemplo/catalogos/` desde `docs/VALIDACION_CATALOGOS_0_8_0.md`: completa contexto, caracterizacion y tramos de edad; ajusta tipo de actuacion, relacion y via (agrupaciones como items padre no seleccionables); crea rol del Estado (desde el eje VII ya transcrito) y grupos prioritarios (denominaciones verificadas contra el PDF); agrega las descripciones; ajusta `ReferenceCatalogsTest` y `LEEME.txt`. Todo queda en estado PROPUESTA. |
| Verificación de la transcripción de derechos y grupos prioritarios | Activación | 2026-11-25: el responsable acepta la transcripción tal como está (verificada por programa contra el PDF de huella registrada en E02 y E06), sin revisión manual adicional. |
| Incidente de E08 (temporales de MySQL llenaron C: en el equipo de pruebas) | E08b | 2026-11-25: se agrega E08b para confinamiento, límites de ejecución y concurrencia del motor. E16 revisa el incidente, el cambio en Workflow.java, la forma de evitar el error 1112 de MySQL y las guardas de E08b. E17 y el paso U verifican @@tmpdir y @@innodb_temp_tablespaces_dir en D:. |
| E15 postergado | E16 | 2026-11-25: por la naturaleza del proyecto no habrá otra validación institucional que la ratificación pendiente de DI-13. U1-U10 los acredita el ejecutor en el humo de E09, E12, E13 y E14, y Claude Padre los revisa en E16. Las observaciones de uso posteriores siguen D47 (0.9.0). E16 depende de E14. |
| Actualización sin ensayo en TEST-SIGER (precisión de D50) | E17 | 2026-11-25: se omite el paso 2 de E17. Fundamento: Update.ps1 y Migrate.ps1 sin cambios desde v0.6.0 y ejecutados con éxito en producción al pasar de 0.6.0 a 0.7.0; la migración desde 0.7.0 hasta la última versión se prueba sobre MySQL 8.4 con el volcado sintético en cada gate y en E14. Compensaciones obligatorias en el paso U (detalle de E14). |
| Dimensión de adscripción institucional (DI-13) | E10 | 2026-11-25: aprobada; se agrega a E10 con StatSource.java y docs/CONTRATO_STATS_0_8_0.md (cambio aditivo registrado como desvío). |
| Detención de E10: generación del corte con el lock global (10,6 s con 50.005 COM y 25.084 congeladas, sobre el umbral de 5 s del GO) | E10 | 2026-11-26: opción A. STAT_CUT_CREATE sigue dentro de Workflow.run, con el lock global, como dice el GO. Se admiten optimizaciones que no cambien el resultado (lotes de inserción más grandes) y se registra el tiempo final. Exigido: un comando que llega durante la generación espera y termina bien, probado en MySQL; innodb_lock_wait_timeout mayor que el plazo de StatGuard (si no, detenerse); plazo vencido con rollback completo, 503, nada parcial y la cancelación auditada. Documentación en CLAUDE.md y en el contrato: generar un corte detiene las operaciones unos segundos y se recomienda hacerlo fuera del horario de atención, con la proyección del tiempo según el volumen frente al plazo de 30 s. Hoja de ruta 0.9.0 (sección 9 del plan): corte sin lock global (opción D). El resto del GO de E10 sigue igual. |
| Detención de E11: con 50.005 COM y -Xmx1024m, ZIP vigente de 2 años en 28,1 s (> 25 s), ZIP desde un corte y dos ZIP simultáneos con 503 por plazo, memoria después de recolectar 956 MiB (93 %) con dos ZIP | E11 | 2026-11-26: aplicar 1, 2 y 3; la 4 (ZIP escrito por flujo a un temporal) pasa a 0.9.0 (sección 9 del plan). (1) Un ZIP a la vez en la instancia: el segundo recibe 429 de inmediato, sin cola, con un cupo propio además del de StatGuard, liberado en finally; un CSV o una consulta siguen funcionando mientras corre un ZIP. (2) Corregir la consulta de microdatos sobre el corte (StatSource, índices en V24 o ambos), con el diagnóstico de EXPLAIN y su causa registrados, sin cambiar el resultado (equivalencias y matriz sin tocar aserciones). (3) `siger.stats.export.max-rows` = 30.000, ajustable con `SIGER_STATS_EXPORT_MAX_ROWS`; por sobre el límite, 409 «Acota el periodo o los filtros». Ajuste del mismo día: el volumen real es de unos 200 requerimientos al mes (unos 2.400 al año, con picos estacionales), de modo que se fija 30.000 sin calibrar 40.000; el fundamento va en el contrato y en CLAUDE.md. (4) Nueva medición con el código final, -Xmx1024m y 50.005 COM: ZIP de la Cuenta Pública vigente y desde un corte en 25 s o menos cada uno, memoria después de recolectar en 75 % o menos, el segundo ZIP con 429 y STAT_CUT_CREATE en 13 s o menos; si no, detenerse y consultar. (5) El registro de fases de StatExport se conserva solo si está inactivo fuera de la medición y no escribe en el log ni en la auditoría. (6) Repetir las 46 mutaciones sobre el código final y agregar las del ZIP exclusivo, el límite y la consulta sobre el corte. (7) Corridas finales una vez y sin paralelo: H2, MySQL desechable en una ruta nueva de D: (actualizaciones desde 0.4.0 y 0.7.0 hasta V24), humo con el JAR nuevo y Check-Scripts.ps1 con powershell.exe 5.1. (8) Hoja de ruta: «Paquete ZIP escrito por flujo a un temporal». (9) Cierre como el encargo anterior. |
| Explorador estadístico de nivel profesional | E12 | 2026-11-26: la interfaz estadística debe ser profesional, útil y clara, y aprovechar todo lo que entrega el motor. Se amplían DISENO 10.3 a 10.5 y el alcance de E12 y E13, sin cambios de servidor ni dependencias nuevas. E12 se divide solo si es indispensable. |
| GO de E12 | E12 | 2026-11-26: A (caracterización completa «X de Y ingresos (Z %)», una sola división, variación en puntos porcentuales, filtros sin PENDIENTE armados desde `/api/catalog`), B (1 de julio: Cuenta Pública recién terminada, rotulada), C (Explorar: solo las categorías del servidor; Figura 1: todas las de `/api/catalog` con 0), D (cuatro descargas CSV y XLSX; Figura 1 en un solo libro a 0.9.0), E (U por número máximo de acciones) y condiciones 1 a 10. Tras el corte por memoria, autorización para retomar del mismo día: resguardo en `wip/e12-claude`, validez de las mutaciones por archivo, mutaciones en primer plano con 2 GB libres como mínimo, navegadores cerrados por PID y squash en `main`. |
| Precisiones del explorador | E12 | 2026-11-26: periodo y comparación del Panorama; definición de caracterización completa (seis variables de E07); jerarquía de derechos por nivel; reparto de la exportación entre E12 y E13; filtro de pendientes en bandejas autorizado en E13 (BoardQueries.java); se reponen las preguntas guiadas y la regla de no agrupar categorías. |
| Protocolo de corte por cuota | E12b | 2026-11-26: se adopta el protocolo de la sección 5, que formaliza lo hecho en E11 y E12; las condiciones para gates futuros quedan en el plan. |
| Desestimación directa de una COM | E12b | 2026-11-26: quien puede distribuir una COM podrá desestimarla como inválida o duplicada sin derivarla antes a una SOL. Gate intercalado en 0.8.0, como E08b. |

## Definiciones pendientes

Cada definicion se responde aqui (fecha y respuesta) antes del gate o paso indicado. "Activacion" es el paso de
produccion en que TI activa los catalogos; no bloquea el desarrollo, que usa versiones PROPUESTA. "0.9.0" indica una
definicion ya respondida que se conserva para la hoja de ruta (seccion 9 del plan).

| Id | Definicion | Quien | Antes de | Respuesta |
|---|---|---|---|---|
| DI-01 | Validacion de los catalogos version 1 de tipo de solicitud, tipo de requirente y via (incluido si "integrante de la Defensoria" es una via y "actuacion de oficio" un requirente) | Institucion (UEP) | Activacion | 2026-11-24: respuesta propuesta del responsable, pendiente de conformidad institucional. Tipo de solicitud (5), requirente (4) y via (9) aprobados con definiciones operativas. "Integrante de la Defensoria" es via solo si es el origen efectivo de la comunicacion; "actuacion de oficio" es requirente estadistico especial; con antecedentes de posible afectacion de derechos, un reclamo se clasifica como vulneracion. Agrupaciones de via: Digital, Presencial, Telefonica, Documental, Interna y Otra, conservando la via original. Detalle en `docs/VALIDACION_CATALOGOS_0_8_0.md`. 2026-11-25: conformidad institucional obtenida (Dirección de UEP), sin cambios a la respuesta propuesta. |
| DI-02 | Contenido de los catalogos de contexto, de caracterizacion de la vulneracion y de tramos de edad (TRAMO_EDAD) | Institucion | Activacion | 2026-11-24: respuesta propuesta del responsable, pendiente de validacion de UEP con sus definiciones operativas. Contexto: 7 categorias y 5 subcategorias. Caracterizacion: 10 categorias y 9 subcategorias. Tramos: 0-5, 6-9, 10-13, 14-17, 18-29, 30-44, 45-59 y 60-130, a confirmar contra el Informe Anual. Se agregan Rol del Estado (eje VII, 5 subcategorias) como caracterizacion transversal y Grupos prioritarios (seccion VIII, 11 grupos) como caracterizacion de la poblacion afectada. Detalle en `docs/VALIDACION_CATALOGOS_0_8_0.md`. 2026-11-25: conformidad institucional obtenida (Dirección de UEP), sin cambios a la respuesta propuesta. |
| DI-03 | Catalogos de tipo de actuacion, tipo de institucion y relacion con el NNA (propuesta en `deploy/catalogos/`, E02) | Institucion | Activacion | 2026-11-24: respuesta propuesta del responsable, pendiente de conformidad institucional. Tipo de actuacion (14): "Medida de proteccion" pasa a "Solicitud de medida de proteccion" y se agregan "Solicitud de antecedentes", "Coordinacion interinstitucional" y "Seguimiento de requerimiento". Tipo de institucion (5): sin cambios, valores del Formulario. Relacion (11): se agregan "El propio NNA" y "Representante legal", exclusivos de SIGER, con los valores del Formulario intactos. Primera atencion, gestion de REQ y gestion de Caso forman un solo universo de actuaciones con origen diferenciado y sin doble conteo. Detalle en `docs/VALIDACION_CATALOGOS_0_8_0.md`. 2026-11-25: conformidad institucional obtenida (Dirección de UEP), sin cambios a la respuesta propuesta. |
| DI-04 | Confirmacion del modelo de derechos vigente (version 2.0 de 2022, 4 ejes y 17 derechos; rotulos del Informe Anual 2026), nivel de clasificacion (derecho o subcategoria) y uso de "Rol del Estado" y grupos prioritarios | Institucion | Activacion | 2026-11-24: respuesta propuesta del responsable, pendiente de que UEP ratifique la vigencia y de la verificacion de la transcripcion. Modelo 2.0 (2022): 4 ejes, 17 derechos y 60 subcategorias aprobados. Clasificacion por derecho con subcategoria opcional, seleccion multiple y sin duplicar el conteo del derecho superior. Eje VII y seccion VIII segun DI-02. Rotulos canonicos de la Tabla 2; las 11 diferencias se revisan contra `LEEME.txt`. Detalle en `docs/VALIDACION_CATALOGOS_0_8_0.md`. 2026-11-25: conformidad institucional obtenida (Dirección de UEP), sin cambios a la respuesta propuesta. |
| DI-05 | Distribucion territorial del Formulario: quien distribuye en la sede (Coordinacion regional, funcion de distribucion con ambito u otra), regla de territorio (region vigente y cobertura), respaldo central, devolucion, avisos y COM sin region | Responsable con la institucion | 0.9.0 | 2026-11-24: aprobada la distribucion territorial por coordinacion regional, con respaldo central, visibilidad nacional para la distribucion central, comunicaciones sin region a cargo del nivel central, avisos territoriales y devoluciones a la bandeja correspondiente. Ante concurrencia, prevalece la primera operacion valida. |
| DI-06 | Acto que define la apertura efectiva de una sede y si puede diferir de la activacion | Responsable | 0.9.0 | 2026-11-24: aprobadas tres fechas independientes: apertura proyectada, apertura efectiva y activacion en SIGER. TI declara la apertura efectiva al activar la sede, con la fecha actual por defecto y posibilidad de modificarla. |
| DI-07 | Reapertura con proposito de registro estadistico: si su ciclo se excluye del tramo `REQ_TOTAL` (cambio del motor de tiempos) o se informa aparte | Responsable | E05 | 2026-11-24: conservar integramente el mecanismo de reapertura y el motor de tiempos existentes. No crear modalidades especiales de reapertura ni modificar `REQ_TOTAL`. La trazabilidad retrospectiva queda en E05. |
| DI-08 | Rotulo de la region de la ficha ("Region de atencion" hoy), sin cambiar D21 | Responsable | E04 | 2026-11-24: "Region y comuna del requirente", sin modificar D21. |
| DI-09 | Textos de asunto por tipo de aviso y uso del enlace directo (requiere acceso por red y `siger.mail.link`) | Responsable | 0.9.0 | 2026-11-24: aprobados los asuntos diferenciados segun los eventos existentes, folio, descripcion breve y enlace directo cuando este habilitado el acceso por red. Sin datos personales. |
| DI-10 | Renovacion del certificado: autofirmado renovado o PFX institucional; autorizacion de TI para el firewall al pasar a red (solo para la ejecucion en produccion) | Responsable con TI | 0.9.0 | 2026-11-24: desestimada. Se mantienen el certificado y la configuracion HTTPS actuales. Sin gate. |
| DI-11 | Alcance de la clasificacion retrospectiva en produccion (desde que fecha, que registros, quien) | Responsable con la institucion | Paso U | |
| DI-12 | Autorizacion de una dependencia XLSX (`org.dhatim:fastexcel`) solo si el escritor o lector propio no basta | Responsable | E11, si se pide | 2026-11-26: no aplica; E11 cerró con el escritor y el lector XLSX propios, sin dependencias. |
| DI-13 | Correspondencia entre la "region de origen" publicada en el Informe Anual y las dimensiones territoriales de SIGER | Institucion | E15 | 2026-11-25: respuesta del responsable; la regla temporal y la homologación quedan sujetas a ratificación institucional. La Figura 1 se desglosa por la adscripción institucional del requerimiento: la sede regional u Oficina Nacional a la que está adscrito el ingreso (los Informes Anuales 2022-2026 siguen el despliegue de las sedes, no las 16 regiones). Se usa la primera adscripción acreditada en el instante de la consulta o del corte; sin adscripción, 'Pendiente de asignación'. Cada requerimiento computable cuenta una vez: NNA, Casos, actuaciones y transferencias no lo multiplican. La región del requirente, la del NNA afectado y la de los hechos siguen como dimensiones independientes; la del NNA es no aditiva y se rotula así. La homologación de la Oficina Nacional con 'Metropolitana' rige solo al comparar con series históricas (correspondencia del asistente de series, E11), sin cambiar el rótulo en SIGER. Se implementa en E10 (dimensión ADSCRIPCION_INICIAL). |
| DI-14 | Funcionarios de UEP que participan en la validacion de uso | Responsable con UEP | E15 | 2026-11-25: no aplica; E15 postergado. |
| DI-15 | Version efectiva de produccion para el ensayo de actualizacion (0.6.0 o 0.7.0, segun el corte del 07/12) | Responsable | E17 | 2026-11-25: 0.7.0 (esquema 16). Producción ya corre 0.7.0, instalada con 0.6.0 y actualizada a 0.7.0 con Update.ps1, según confirmó el responsable. El paso U parte de esa versión; el diario de Update.ps1 de esa actualización debe estar en COMPLETE. |

## Antecedentes para E00

- Base: `3938e4e` (`gate T06b: ensayo diferencial y etiqueta`, igual a `v0.7.0`) y el commit que agrega este plan, esta
  bitacora y `docs/DISENO_0_8_0.md` en su version con alcance reconsiderado del 2026-11-24 (lo hace el responsable de
  forma manual; no es un gate). E00 comprueba con `git log` que ese commit existe y que `git status` esta limpio.
- El plan y el diseno se redactaron fuera del ejecutor: E00 verifica contra el codigo cada referencia de la seccion 1
  del plan y de la seccion 1.2 del diseno (archivos, lineas, conteos) y registra cualquier diferencia antes de seguir.
  Una diferencia de referencia se corrige en el registro de E00; una que cambie una decision, el alcance o una
  condicion detiene el gate.
- Cifras de referencia: 191 JUnit en H2; 194 sobre MySQL 8.4; 112 de frontend; `Check-Scripts.ps1`: 40 scripts y 73
  ordenes documentadas en 60 procesos.
- `SOURCE_SHA256.txt` queda desactualizado desde el commit del plan; se regenera en E17.
- La salida a produccion de 0.6.0 o 0.7.0 (V0-V6 desde el 04/12) se sigue registrando en `docs/BITACORA_0_6_0.md`.
- Hallazgo F10 del diseno: el contrato del Formulario es el paso 1 de E00 y debe resultar conforme antes de cualquier
  otro trabajo, por su relacion con la apertura del Formulario del 15/12/2026 (seccion 3 del plan).
- E00 copia a `DECISIONES_FUNCIONALES.md` las decisiones D25-D51 con sus precisiones, incluidas las respuestas DI-05 a
  DI-10 y los ajustes de aprobacion, tal como estan en la seccion 0 del plan; D44-D49 con el rotulo "Hoja de ruta 0.9.0,
  no vigente en 0.8.0".
- Plazo: 15/12/2026 (seccion 3 del plan). El plazo no reduce verificaciones.
- Correspondencia con la version anterior del plan (no ejecutada): E00 = E00 + E01; E01 = E02 + E03; E02 = E04 + E19;
  E03 = E08 (servidor) + E09 (servidor) + E10; E04 = E08 (interfaz) + E09 (interfaz) + E11; E05 = E05 + E06 + E18;
  E06 = E13 + E15; E07 = E12 + E17 (servidor); E08 = E20 + E21 + E22; E09 = E07 + E14 + E16 + E17 (interfaz);
  E10 = E23 + E26; E11 = E24 + E25; E12 = E27 + E28 (graficos); E13 = E28 (exportacion) + E29; E14 = E39 + E40;
  E15 = E41; E16 = E42; E17 = E43 + E44. Postergados a 0.9.0: E30-E38.

## Observaciones de funcionarios (hoja de ruta 0.9.0)

Registro del responsable (D47). En 0.8.0 las observaciones solo se registran y validan; su incorporacion pasa a 0.9.0,
salvo que el responsable decida expresamente incluir en 0.8.0 una que corresponda a sus objetivos, caso en que Claude
Padre propone su gate y el responsable lo agrega a la tabla de estado.

| Id | Fecha | Origen (funcion y ambito, sin nombres) | Observacion | Validacion del responsable | Gate |
|---|---|---|---|---|---|

## GO aprobados pendientes de ejecucion

Plan presentado por el ejecutor y GO del responsable para un gate que se ejecuta en una sesion posterior. El estado del
gate no cambia (sigue ABIERTO) hasta su registro de cierre; la sesion que lo ejecute toma de aqui el plan y las
condiciones del GO, verifica que la base no haya cambiado y no necesita volver a pedir el GO.

### E07 - plan y GO del 2026-11-25 (ejecutado)

Plan presentado (Claude Code, DDN-TEST-SIGER, base `8a297ae` = cierre de E06; sin cambios en archivos):

- Precondiciones: `git status` limpio, `main` al dia con `origin`; E00, E03 y E06 CERRADO; ninguna DI exigida (los
  catalogos de referencia PROPUESTA sirven a las pruebas: `VULNERACION` en TIPO_SOLICITUD, `PERSONA_NATURAL_ADULTA` y
  `NNA` en TIPO_REQUIRENTE); base 251 JUnit H2, 255 MySQL, 154 frontend.
- Inventario: los bloqueos de cierre se calculan dos veces (S3): `Queries.reqSummary` (`closure_blockers`/`can_close`,
  solo jefatura) y REQ_CLOSE en `RequirementCommands` (`Rules.requirementClosable` y `require`); `Attention.resolve`
  sin bloqueos visibles; COM_IMMEDIATE y ATTENTION_CREATE con `resolve_now` crean y cierran el REQ en el mismo comando;
  SOL_DISCARD (INVALIDA/DUPLICADA) aplica `stats.inline` antes de descartar. Territorio: `OriginRegion` ya tiene en SQL
  la region registrada y vigente (ficha, Formulario, ficha de ingreso); sede inicial y vigente desde los hitos
  `REQ_CREADO`/`FLUJO_DECIDIDO`, `principal_scope` y `solicitation`; la cobertura tiene historia en
  `territorial_coverage`; el estado de las sedes en el tiempo solo esta en `audit_event` (`AMBITO_ACTIVADO`,
  `AMBITO_CERRADO`). Edad y nacionalidad: el Formulario es relacional (`external_person.age_at_intake`/`declared_age`,
  `external_attribute`, `external_field` PERSONA); la ficha y la ficha de ingreso solo en JSON (`data_json`). Pruebas
  que cierran registros: 12 clases (entre ellas `ImmutabilityTest`, `RightsTest`, `StatClassificationTest`,
  `ActionTypesTest`); las que activan catalogos deben clasificar antes de cerrar.
- Decisiones planteadas:
  - A (bloqueante). Las reglas "en Java y SQL" no pueden leer el JSON de las fichas de forma portable en H2, y los
    archivos autorizados de E07 no incluyen migracion. Recomendacion (precedente de V16): V21 con tablas derivadas
    `stat_declared_version` (una fila por version de ficha o ficha de ingreso: fuente, instante, region y comuna del
    requirente y de los hechos) y `stat_declared_person` (una por persona: rol, region, comuna, edad a la recepcion
    calculada en Java y su origen, nacionalidad; sin fecha de nacimiento), escritas en `Attention.snapshot` e
    `IntakeRecord.create`/`complete`, con carga desde `Bootstrap` (marcador `DIMENSIONES_V21`, tambien con
    `migrate-only`). Amplia a `V21__*.sql`, `IntakeRecord.java`, `Bootstrap.java` y `Attention.snapshot`.
  - B. Region del requirente en la completitud (DISENO 4.5): no tiene catalogo versionado ni estado especial; exigirla
    obligaria a inventar datos (D28) y podria ser irresoluble en una COM del Formulario (inmutable). Opciones: (a,
    recomendada) no bloquea en 0.8.0, solo indicador de calidad, diferencia de referencia; (b) variable
    `REGION_REQUIRENTE` de solo estados especiales en `StatValues`, exigible con TIPO_REQUIRENTE vigente y persona
    natural (amplia `StatValues.java` y E09).
  - C. Resolver en el mismo acto: bloque opcional `stat` (`StatValues.inline`) en REQ_CLOSE y ATTENTION_RESOLVE; en
    COM_IMMEDIATE y ATTENTION_CREATE con `resolve_now` el REQ nace en el comando, por lo que una vulneracion no podria
    cerrarse en el acto sin un bloque de derechos: se propuso un `Rights.inline` minimo (amplia `Rights.java`).
  - D. Territorio por cobertura a la fecha de recepcion: region de origen registrada; sede activa en t = sin
    `AMBITO_ACTIVADO` posterior a t, sin `AMBITO_CERRADO` hasta t y no PROYECTADA (por codigo de accion, nunca el texto
    de la nota); region fisica actual (su cambio no se reconstruye, diferencia); periodos de `territorial_coverage`
    vigentes en t; valor unico por prioridad: sede de la region fisica, sede que la cubre (menor id), NIVEL_CENTRAL,
    SIN_ATENCION; sin region, SIN_INFORMACION.
  - E. Edad y nacionalidad del requirente solo si el tipo de requirente (en el modo temporal) esta INFORMADO como
    `PERSONA_NATURAL_ADULTA` o `NNA`; en otro caso NO_APLICA.
- Plan, con punto de control:
  - E07a: `TerritorialDims` (Java y SQL: region y comuna del requirente al ingreso y vigente; del NNA afectado,
    multiple, con el detalle `afectado-N` del Formulario; de los hechos; sede inicial y vigente, que tambien da el
    ambito de referencia del alcance de DISENO 5.6; territorio por cobertura; modos vigente, al ingreso, al REQ y a una
    fecha t); `PersonDims` (edad desde el nacimiento a la recepcion en hora de Chile, si no la edad entera declarada, un
    texto historico solo si es exactamente un entero en el rango del rol, si no SIN_INFORMACION; nacionalidad pais y
    agrupacion Chile/extranjera/sin informacion; tramo segun TRAMO_EDAD vigente); V21 y su carga (si A).
  - E07b: `StatCompleteness`, regla unica: variables por cierre de DISENO 4.5 (REQ_CLOSE, ATTENTION_RESOLVE,
    COM_IMMEDIATE y ATTENTION_CREATE con `resolve_now`, SOL_DISCARD DUPLICADA), solo con catalogo VIGENTE; una
    propuesta no resuelve; con `VULNERACION`, derecho (o estado especial) en el REQ o sus Casos y contexto. Valida en
    los comandos (409, texto llano) y alimenta `closure_blockers` (Queries, REQ), `resolve_blockers` (vista de la
    atencion), `discard_blockers` (ficha de la SOL) y las variables exigibles por cierre en `/api/catalog` (para E09).
    Indicadores de calidad: `indicators(alcance, desde, hasta)` con conteos por variable, estado y ambito de referencia
    sobre las COM computables (D25), sin endpoint (lo expone E08).
- Archivos propuestos: `TerritorialDims.java`, `PersonDims.java` y `StatCompleteness.java` (nuevos),
  `RequirementCommands.java` (cierre), `Attention.java` (resolucion y `snapshot`), `IntakeCommands.java` (cierre en el
  acto y descarte; nunca `conserveExternal`), `Queries.java`; segun A y C: `V21__*.sql`, `IntakeRecord.java`,
  `Bootstrap.java`, `Rights.java`; pruebas, `CLAUDE.md`, `docs/ARQUITECTURA.md`, bitacora.
- Pruebas previstas: `TerritorialDimsTest` (escenario 5; coberturas que cambian en el tiempo; NNA de dos regiones; COM
  sin REQ), `PersonDimsTest` (escenario 14; cumpleanos en hora de Chile; dos nacionalidades; texto 1.0 no convertido;
  la fecha de nacimiento nunca sale), `StatCompletenessTest` (escenario 7; vulneracion sin derecho; descarte duplicado
  e invalido libre; sin catalogo vigente nada bloquea; bloqueos mostrados = validados); equivalencia Java/SQL en H2 y
  MySQL 8.4 (`siger.mysql.suite=true`, procedimiento de E00); `MySql84Upgrade070Test` y `MySql84UpgradeTest` con la
  carga V21 y `communication_original`/`external_*` identicos; pruebas existentes que cierran con catalogos vigentes
  adaptadas clasificando antes, sin quitar aserciones. Mutaciones (unas 12): propuesta como resuelta; sin catalogo
  vigente bloquea; vulneracion sin derecho; INVALIDA exige variables; bloqueo mostrado distinto del validado; edad sin
  hora de Chile; texto historico interpretado; nacionalidad libre convertida; transferencia cambia la sede inicial;
  cobertura sin su periodo; SQL distinto de Java; carga V21 omitida.
- Riesgos: rendimiento de las subconsultas correlacionadas (medicion en E14); historia de sedes basada en la auditoria
  y sin cambios de region fisica; con catalogos vigentes los cierres pasan a bloquearse (en produccion solo tras
  activarlos); si E07b no cabe con todas sus verificaciones, cierre de E07a y E07b ABIERTO. Sin cambios en
  `OriginRegion.current`, `Access.suggestedRegion`, `Territory`, `Reports` ni `REOPEN_*`.

GO del responsable (2026-11-25), con estas respuestas y condiciones:

- A. Si: tablas derivadas `stat_declared_version` y `stat_declared_person` en V21, escritas al guardar la ficha y la
  ficha de ingreso, mas la carga historica con el marcador `DIMENSIONES_V21` (tambien con `migrate-only`). Se
  autorizan V21, `IntakeRecord.java`, `Bootstrap.java` y `Attention.snapshot`. Condiciones: son datos derivados, nunca
  fuente de verdad, y una prueba recalcula desde las fichas y compara con lo guardado; la carga historica es
  idempotente; la fecha de nacimiento no se guarda; las dimensiones del Formulario se leen de `external_*` sin
  escribir.
- B. Si a (a): la region del requirente no bloquea el cierre en 0.8.0; solo aparece como indicador de calidad. Se
  registra como diferencia de referencia con DISENO 4.5 (D28: no inventar datos; las COM del Formulario no se pueden
  modificar).
- C. Sí a la propuesta (corrige la respuesta anterior, dada sobre un texto truncado): bloque opcional stat (StatValues.inline) en REQ_CLOSE y ATTENTION_RESOLVE, y un Rights.inline mínimo en COM_IMMEDIATE y en ATTENTION_CREATE con resolve_now. Rights.inline usa la misma función interna y las mismas validaciones que RIGHTS_CLASSIFY: catálogo vigente, solo ítems seleccionables y origen asignado por el servidor. No cambia el comportamiento de RIGHTS_CLASSIFY. Rights expone además el conjunto consolidado a StatCompleteness. Prueba exigida: una vulneración se resuelve en el acto si informa derecho (o estado especial) y contexto; sin ellos, responde 409 con texto llano.
- D. Si a la regla de sede activa en una fecha, cobertura por periodos y prioridad (sede de la region fisica, sede que
  la cubre, NIVEL_CENTRAL, SIN_ATENCION; sin region, SIN_INFORMACION). El estado de la sede se lee del codigo de accion
  de la auditoria, nunca del texto. El cambio de region fisica de una sede no se reconstruye y se registra como
  diferencia.
- E. Si a la recomendacion, respetando DISENO 4.8: la edad y la nacionalidad del requirente solo se calculan cuando el
  tipo de requirente esta informado como persona natural adulta o NNA; en otro caso no aplica. Nunca se usan adultos
  relacionados ni la persona senalada. Nunca se interpreta un texto historico.
- Generales: sin division salvo que sea imposible cerrar con todas las verificaciones; `OriginRegion.current`,
  `Access.suggestedRegion`, `Territory`, `Reports` y `REOPEN_*` sin cambios; las pruebas que cierran registros con
  catalogos vigentes se adaptan clasificando antes de cerrar, sin eliminar aserciones, y se registran. Corridas
  finales: H2 completo; MySQL 8.4 con actualizacion desde 0.4.0 y desde 0.7.0 hasta V21, con `communication_original`
  y `external_*` identicos; frontend si cambia `/api/catalog`; `Check-Scripts.ps1` una vez si se toca `CLAUDE.md`, sin
  procesos en paralelo. Mutaciones autorizadas: las listadas, temporales y revertidas. Commit final
  `gate E07: dimensiones y completitud`, revision de que no viajen archivos sensibles, push y detenerse.
- Estado de E07: sigue ABIERTO. El GO se registro en el commit `docs: plan y GO de E07`; la implementacion queda para
  una sesion posterior.

Ejecutado en c9abb9a (gate E07: dimensiones y completitud).

### E08 - plan y GO del 2026-11-25 (ejecutado)

Plan presentado por Codex. Base comprobada `c9abb9a`, cierre de E07, con `main`, `origin/main` y la rama remota en
el mismo commit y árbol de trabajo limpio. E07, sus dependencias y E00 están CERRADOS. El contrato del Formulario
quedó conforme en E00. La conformidad institucional de DI-01 a DI-04 y la decisión DI-07 están registradas.
DI-13 se requiere en E15. La Figura 1 sintética se desglosa por región del requirente.

La base acreditada es de 259 pruebas H2, 263 MySQL y 154 frontend. Se comprobó contra `v0.7.0` la integridad de
los archivos protegidos de recepción, `conserveExternal`, `Reports.java`, `scripts/`, `.mvn/` y V1–V16.

#### Inventario y definiciones planteadas

| Componente | Hallazgo y uso en E08 |
|---|---|
| `communication`, `solicitation`, `requirement`, `communication_link`, `milestone` | SOL y REQ tienen una relación única con la COM. Casos, hitos y eventos son relaciones de uno a muchos. La fuente principal parte de COM. Para D25 histórico se pueden fechar el descarte y el vínculo con el hito de descarte y con `proposed_ms`/`returned_ms`. |
| `Reports.reach`, `TerritorialDims.referenceScopeSql`, `Access` | Reutilizar la resolución de concesiones efectivas. El alcance se aplica en SQL sobre el principal vigente del REQ, el destino vigente de la SOL o el origen de la COM, en ese orden. La dimensión territorial seleccionada cambia el desglose. El predicado de acceso usa las concesiones vigentes. |
| `TerritorialDims`, `OriginRegion`, `PersonDims`, V21, `external_*` | Reutilizar las expresiones de E07 para ficha, cobertura, sedes y Formulario, el corte `registered_ms <= requirement.created_ms`, la edad a recepción y la validación de nacionalidad estructurada. La región física de la sede usa el valor actual según E07. |
| `StatValues`, `Rights`, `StatCatalogs`, `StatProposals`, `StatCompleteness` | Reutilizar las reglas de SET/STATUS y ADD/REMOVE, la vía derivada, las versiones y jerarquías de catálogo y la precedencia de completitud de E07. Consolidar por conjuntos en SQL y entregar las propuestas en un agregado separado. |
| `management_note`, `note_institution`, `requirement.immediate_result` | Una constancia tipificada entra mientras sea la versión vigente al corte. El resultado de primera atención se toma de su columna estructurada. El origen de la actuación distingue primera atención, gestión de REQ y gestión de Caso. |
| `Api`, `SecurityConfig`, `ApiErrors`, `Queries.catalog`, `Db.audit` | POST autenticado con el CSRF existente y 405 para GET autenticado. El catálogo publica dimensiones y medidas para REPORTES. La nota de `STAT_CONSULTA` contiene únicamente nombres validados de medidas, dimensiones y filtros. |

1. **Concordancia con Reports.** `Reports` cuenta todas las COM por fecha de registro (`created_ms`) y ámbito de
   origen. `INGRESOS` aplica D25 y usa por defecto recepción y ámbito de referencia. Se propuso exigir igualdad en
   el universo común y, para el volumen administrativo institucional, conciliar por canal
   `INGRESOS + EXCLUIDOS + ANTECEDENTES` con fecha de registro. Para REQ, exigir igualdad por estado con fecha de
   constitución y alcance principal vigente.
2. **Casos fuera de alcance y reserva.** Se propuso exigir alcance REPORTES sobre la COM y sobre el Caso cuando
   el dato procede de ese Caso, por ámbito o materia. Primero se determina qué constancia está vigente y luego se
   excluyen las reservadas. `TIPO_INSTITUCION` se alimentaría de `note_institution`. La ficha y el ingreso manual
   guardan las instituciones declaradas en JSON y V21 carece de una proyección relacional de ese dato. Incorporarlas
   requiere acordar la semántica y ampliar el alcance técnico. Se propuso agregar `REMISION_CONCLUIDA` al total de
   actuaciones de primera atención y conservar su código de resultado en el desglose hasta que exista una equivalencia
   institucional con `DERIVACION_EXTERNA`.
3. **Historia con evidencia parcial.** Se propuso devolver `PENDIENTE` y `HISTORIA_INCOMPLETA` cuando falte
   evidencia estructurada para el instante solicitado. El campo vigente de complejidad o materia por sí solo no
   acredita su valor anterior. En `AL_REQ`, una primera ficha guardada un milisegundo después de crear el REQ queda
   fuera, incluso si ambos actos ocurrieron en la misma transacción. Se comprueba con una prueba de borde de E07.

#### Contrato propuesto de POST /api/stats/query

Entrada de ejemplo. `period.date_basis` distingue las fechas de análisis y `mention_dimension` indica qué se suma
al cruzar varias dimensiones multivaluadas.

```json
{
  "measure": "REQUERIMIENTOS_CON_VALOR",
  "rows": ["DERECHO"],
  "columns": [],
  "filters": {
    "CANAL": ["FORMULARIO_WEB_PUBLICO", "TELEFONO"]
  },
  "period": {
    "type": "CUENTA_PUBLICA",
    "from": "2025-07-01",
    "to": "2026-06-30",
    "date_basis": "RECEPCION"
  },
  "time_mode": "VIGENTE",
  "as_of": null,
  "cut_id": null,
  "compare": null,
  "percent": "TOTAL",
  "include_proposals": false,
  "mention_dimension": null
}
```

- Medidas `INGRESOS`, `EXCLUIDOS`, `ANTECEDENTES`, `REQ_CONSTITUIDOS`, `CASOS`, `ACTUACIONES`,
  `REQUERIMIENTOS_CON_ACTUACION`, `CLASIFICACIONES_DERECHO`, `REQUERIMIENTOS_CON_DERECHO`,
  `REQUERIMIENTOS_CON_VALOR`, `MENCIONES` y `COMPLETITUD`. Las frecuencias cuentan su propia unidad. La respuesta
  informa por separado las COM únicas. `COMPLETITUD` usa el par COM–variable con su estado. `MENCIONES` requiere
  `mention_dimension` si hay más de una dimensión múltiple candidata.
- `rows` y `columns` son listas ordenadas. `filters` acepta listas de códigos, números o estados especiales según
  la dimensión. Combina los valores de una lista con OR y los filtros de distintas dimensiones con AND.
  `GET /api/catalog` publica `stats` con identificadores, rótulos, tipos, cardinalidad y combinaciones admitidas.
  El catálogo cubre período, tardanza, estados COM/SOL/REQ, canal, mecanismo, vía y agrupación, solicitud, requirente,
  modalidad, territorio, unidad, materia, complejidad, origen del REQ, primera atención, derechos en sus tres niveles,
  contexto, caracterización, rol del Estado, grupos prioritarios, actuación, institución, demografía y estados de
  clasificación. Nombres y valores se validan contra listas y catálogos controlados.
- Tipos de período `PERSONALIZADO`, `MES`, `TRIMESTRE`, `SEMESTRE`, `ANIO` y `CUENTA_PUBLICA`.
  El personalizado exige `from` y `to`. En un predefinido, `from` identifica el período y `to` puede omitirse o
  coincidir con el fin canónico. Ambos días son inclusivos en `America/Santiago`. SQL recibe el inicio y la medianoche
  siguiente al último día como límite superior exclusivo. `date_basis` admite `RECEPCION`, `REGISTRO` y
  `CONSTITUCION_REQ`. `CASOS` usa por defecto la creación del Caso. Las series usan ejes `PERIODO_DIA`,
  `PERIODO_MES`, `PERIODO_TRIMESTRE`, `PERIODO_SEMESTRE`, `PERIODO_ANIO` o `PERIODO_CUENTA_PUBLICA`
  sobre la fecha elegida. Los intervalos vacíos se representan con cero.
- `VIGENTE` usa el último valor disponible y `AL_INGRESO` el primer valor según DISENO 4.7. `AL_REQ` toma la evidencia
  hasta la constitución del REQ. `FECHA` exige `as_of` en ISO 8601 con zona u offset. `compare` admite `null`,
  `{"type":"PERIODO_ANTERIOR"}`, `{"type":"MISMO_PERIODO_ANIO_ANTERIOR"}` y `{"type":"CORTE","cut_id":42}`.
  E08 admite comparaciones temporales. La selección y comparación por corte corresponde a E10. Se alinean las
  categorías por clave y se devuelve cifra base, diferencia absoluta y variación porcentual, `null` con base cero.
- `percent` admite `TOTAL`, `FILA`, `COLUMNA` y `null`. Para cruces multivaluados, el numerador son las COM únicas
  de la celda y el denominador las COM únicas del total o margen seleccionado. Los márgenes se recalculan sobre sus
  propios conjuntos. Para derechos y demás clasificaciones una mención es una combinación COM–valor.
  `CLASIFICACIONES_DERECHO` cuenta asignaciones por registro. Para personas, actuaciones e instituciones se cuentan
  las ocurrencias. El resultado conserva versiones de catálogo y avisa si intervienen versiones distintas. Las
  correspondencias requieren una regla explícita.
- La respuesta incluye claves y rótulos de las categorías, avisos con código estable, metadatos de metodología, fecha,
  modo y versiones utilizadas, tardíos y distribución por días calendario entre recepción y registro, cobertura de
  edad y nacionalidad cuando se consultan y propuestas por variable y código cuando se piden. Petición inválida 400,
  límite técnico de celdas 409, sin REPORTES o TI 403, POST sin CSRF 403 y GET autenticado 405.

Respuesta de ejemplo sobre la población sintética. Los márgenes usan la estructura numérica del total general.
Cuando se compara, `comparison` incluye período base, totales y celdas alineadas con `base_n`, `delta_n` y
`delta_percent`.

```json
{
  "metadata": {
    "methodology_version": "1",
    "measure": "REQUERIMIENTOS_CON_VALOR",
    "rows": ["DERECHO"],
    "columns": [],
    "period": {
      "type": "CUENTA_PUBLICA",
      "from": "2025-07-01",
      "to": "2026-06-30",
      "date_basis": "RECEPCION"
    },
    "zone": "America/Santiago",
    "time_mode": "VIGENTE",
    "as_of": null,
    "cut_id": null,
    "percent": "TOTAL",
    "catalog_versions": {"DERECHO": [1]}
  },
  "values": {
    "DERECHO": [
      {"key": "3.1", "label": "Derecho sintético 1"},
      {"key": "3.2", "label": "Derecho sintético 2"},
      {"key": "4.1", "label": "Derecho sintético 3"}
    ]
  },
  "cells": [
    {"row": ["3.1"], "column": [], "n": 1, "unique": 1, "mentions": 1, "percent_numerator": 1, "denominator": 2, "percent": 50},
    {"row": ["3.2"], "column": [], "n": 2, "unique": 2, "mentions": 2, "percent_numerator": 2, "denominator": 2, "percent": 100},
    {"row": ["4.1"], "column": [], "n": 1, "unique": 1, "mentions": 1, "percent_numerator": 1, "denominator": 2, "percent": 50}
  ],
  "totals": {
    "grand": {"n": 2, "unique": 2, "mentions": 4},
    "rows": [
      {"key": ["3.1"], "n": 1, "unique": 1, "mentions": 1},
      {"key": ["3.2"], "n": 2, "unique": 2, "mentions": 2},
      {"key": ["4.1"], "n": 1, "unique": 1, "mentions": 1}
    ],
    "columns": []
  },
  "comparison": null,
  "late_records": {"n": 0, "by_delay_days": []},
  "data_coverage": {},
  "proposals": [],
  "warnings": [{"code": "PORCENTAJES_MULTIVALOR", "dimensions": ["DERECHO"]}]
}
```

#### Población sintética y matriz de pruebas y mutaciones

La población principal tiene cinco ingresos computables en la Cuenta Pública de julio de 2025 a junio de 2026.
**A** es Formulario adulto, vulneración, región 07, con REQ reabierto y transferido de S06 a S07 y dos Casos.
**B** es correo, consulta, persona adulta, región 13, con COM nueva.
**C** es presencial, promoción, organismo, región sin información, duplicada.
**D** es teléfono, consulta, NNA, región 05, con primera atención cerrada.
**E** es correo, vulneración, persona adulta, región 07, con SOL pendiente, recibida al final del 30/06 y registrada el 02/07.
**F** es un oficio inválido. **G** es un antecedente de canal `OTRO`.
**H** se recibe al comenzar el 01/07 siguiente. S06 tiene B, C, D y E en su alcance vigente. A y E son de materia UPD y C de UPR.

La Cuenta Pública anterior tiene dos ingresos, el 01/07/2024 y el 30/06/2025. Los bloques adicionales de territorio,
historia, demografía y horario tendrán poblaciones fijas aisladas. Las cifras de la tabla serán datos de prueba escritos
a mano. Las cadenas se crearán con comandos, los Formularios con vectores sintéticos firmados y los instantes de borde
se fijarán explícitamente en la base de prueba.

| Regla | Escenario | Dato sintético | Cifra esperada | Mutación y prueba que debe fallar |
|---|---|---|---|---|
| Figura 1, solicitud | 1 | A/E vulneración, B/D consulta, C promoción | `2 + 2 + 1 = 5` | Agrupar por propuesta en vez del valor confirmado → `figuraSolicitud`. |
| Figura 1, requirente | 1 | A/B/E adultos, D NNA, C organismo | `3 + 1 + 1 = 5` | Derivar el requirente del canal → `figuraRequirente`. |
| Figura 1, vía | 1 | Formulario adulto A, correo B/E, presencial C, teléfono D | Vías `1 + 2 + 1 + 1 = 5`. Digital 3, presencial 1, telefónica 1 | Usar canal como clave de vía o perder la agrupación padre → `figuraVia`. |
| Figura 1, región | 1 | A/E en 07, B en 13, D en 05, C sin dato | `07=2`, `13=1`, `05=1`, `SIN_INFORMACION=1`. Total 5 | Sustituir región del requirente por región de sede u omitir el valor especial → `figuraRegion`. |
| Inválida y duplicada | 2 | F inválida, C duplicada | Ingresos 5, excluidos 1. C cuenta 1 y F cuenta 0 en ingresos | Incluir inválidas o excluir duplicadas, en mutaciones separadas → `descarteD25`. |
| Antecedente y devolución | 2 | G propuesta, rechazada y luego devuelta por `LINK_RETURN` | Vinculada o rechazada sin devolver, ingresos 5 y antecedentes 1. Desde la devolución, 6 y 0 | Usar `RECHAZADA` como devolución o ignorar `returned_ms` → `antecedenteD25`. |
| Pendientes y primera atención | 2 | B COM nueva, E SOL pendiente, D directa cerrada | B=1, E=1 y D=1. Subtotal 3 | Exigir REQ o exigir SOL para contar → `pendientesYDirectaD25`. |
| Reapertura, transferencia y Casos | 2 | A con una reapertura, una transferencia y dos Casos | A aporta una COM, un REQ y dos Casos. El conteo de A sigue en 1 en cada hito | Contar hijos o hitos como filas de ingreso → `cadenaUnicaD25`. |
| D25 a fecha histórica | 2, 37 | F antes y después del descarte. G antes del vínculo, vinculado y tras devolución | F `1→0`. G `1→0→1` en ingresos | Aplicar `communication.state` vigente a todos los cortes → `estadoHistoricoD25`. |
| Medianoche de Cuenta Pública | 3 | E `30/06 23:59:59.999` y H `01/07 00:00`, equivalentes a `03:59:59.999Z` y `04:00Z` | E en la Cuenta Pública que termina, H en la siguiente. Una COM en cada lado del borde | Usar UTC para el día civil o incluir el límite superior → `bordeCuentaPublica`. |
| Cambio de horario | 3 | `05/04/2026 02:30Z` y `03:30Z` son ambas `04/04 23:30` en Chile. `04:00Z` ya es `05/04 00:00` | 04/04 cuenta 2 y 05/04 cuenta 1 | Fijar un offset único o agrupar por día UTC → `horarioChile`. |
| Registro tardío | 3 | E recibida el 30/06 y registrada el 02/07. Caso separado registrado justo a la medianoche del cierre | E aporta 1 tardío con desfase de 2 días. El caso de medianoche también aporta 1 | Usar `>` sobre el límite exclusivo o usar `created_ms` como fecha de demanda → `registroTardio`. |
| Requerimientos únicos y menciones | 15 | A tiene R1/R2 en REQ, R1/R3 en un Caso y R1 en otro. D tiene R2. R1/R2/R3 son `3.1`/`3.2`/`4.1` | A sola, 3 valores, 3 menciones y 5 clasificaciones. A+D, celdas `1,2,1`, 4 menciones y total único 2 | Quitar la deduplicación COM–valor o equiparar menciones y clasificaciones → `mencionesDerecho`. |
| Denominador único | 15 | A+D, cuatro pertenencias en tres categorías | `50 %, 100 %, 50 %`. Denominador 2 y aviso de multiplicidad | Dividir por 4 o suprimir el aviso → `porcentajeMultivalor`. |
| Cruce de dos múltiples | 15 | A tiene contextos X/Y y D tiene Y | Para R1/R2/R3 por X/Y, `(1,1)`, `(1,2)`, `(1,1)`. Márgenes por derecho `1,2,1`, por contexto `1,2`, total 2. R1/Y da `100 %` por fila y `50 %` por columna | Sumar celdas para obtener márgenes o unir por Caso en vez de COM → `cruceDosMultiples`. |
| Cruce múltiple y única | 15 | A Formulario y D teléfono | Formulario por derecho `1,1,1`, teléfono `0,1,0`. Margen de cada canal 1 y total 2 | Sumar derechos para el total por canal → `cruceUnicaYMultiple`. |
| Sin valores y filtro múltiple | 15 | B/C/E sin derecho. Filtro R1 o R3, más canal Formulario | `PENDIENTE=3` sobre la población de 5. La consulta filtrada obtiene solo A, total 1 | Usar un `INNER JOIN` que borra pendientes o contar A dos veces con el filtro → `estadosYFiltros`. |
| Jerarquía y retiro de código | 6, 15 | Un REQ con un derecho y su subcategoría, que se retira en un evento posterior | El derecho superior cuenta 1 en ambos cortes. La subcategoría pasa de 1 a 0 | Sumar padre e hijo como dos COM del mismo derecho u omitir `REMOVE` → `jerarquiaYRetiro`. |
| Actuaciones, instituciones y reserva | 8, 36 | A tiene dos denuncias vigentes, una rectificación que sustituye una constancia, una constancia reservada y una tarea de querella completada. Dos instituciones tipificadas del mismo tipo. D tiene orientación y su constancia de respaldo | Denuncias 2 y REQ con denuncia 1. Querella 0. Institución, 2 menciones y 1 COM. Orientación 1. Actuaciones visibles A+D, 3 | Contar la versión rectificada, la nota reservada, la tarea o ambas fuentes de orientación → `actuacionesVigentes`. |
| Cuatro modos temporales | 37 | Una COM recibe versiones de región `03`, `05`, `07`, `13`, con `05` vigente al REQ y `07` a `as_of` | `AL_INGRESO=03`, `AL_REQ=05`, `FECHA=07`, `VIGENTE=13`, una COM por resultado | Sustituir cualquier modo por la versión actual → `cuatroModos`. |
| Límite de ficha al crear el REQ | 37 | Una versión a `tREQ` y otra a `tREQ+1 ms`. Otra COM tiene su primera ficha a `tREQ+1 ms` | Para la primera, 1 en la región de `tREQ`. Para la segunda, 1 en `PENDIENTE` | Usar `<` en vez de `<=` o permitir la versión posterior → `corteVersionReq`. |
| Historia sin evidencia | 37 | Complejidad vigente ALTA registrada después de `as_of` | `ALTA=0`, `PENDIENTE=1`, aviso de historia incompleta | Leer la complejidad actual en `FECHA` → `historiaSinEvidencia`. |
| Serie mensual | 26 | A–E en julio, agosto, octubre, enero y junio | Una COM en cada uno de esos cinco meses y cero en los otros siete | Omitir meses vacíos o usar fecha de registro → `serieMes`. |
| Series trimestre, semestre, año y Cuenta Pública | 26 | Las mismas cinco fechas | Trimestres `2,1,1,1`. Semestres `3,2`. Años calendario `3,2`. Cuenta Pública `5` | Cambiar el inicio de cada familia de períodos, en cuatro mutaciones → `serieTrimestre`, `serieSemestre`, `serieAnio`, `serieCuentaPublica`. |
| Período anterior y mismo período del año anterior | 26 | Junio 2026=1, mayo 2026=0, junio 2025=1. Cuenta Pública actual=5 y anterior=2 | Junio vs. mayo, `+1` y variación `null`. Junio interanual, `0` y `0 %`. Cuenta Pública vs. anterior, `+3` y `150 %` | Usar el mismo desplazamiento para ambas comparaciones o dividir por el período actual → `comparaciones`. |
| REPORTES por sede | 43 | A originada en S06, transferida a S07. B–E de referencia S06 | S06=4, S07=1, filtro S07 desde S06=0. A sigue fuera de S06 en un corte anterior a la transferencia | Autorizar por origen, por la sede histórica o después de agregar → `alcanceSede`. |
| REPORTES por materia y Caso | 34, 43 | UPD cubre A/E, UPR cubre C. Un derecho de A está solo en un Caso S06 | UPD=2, UPR=1. El derecho de ese Caso aporta 0 a S07 y 1 al institucional | Ignorar materia o admitir un Caso de otra sede → `alcanceMateriaYCaso`. |
| REPORTES, TI, CSRF y método | 43 | Cuenta sin REPORTES, lector institucional, TI, POST sin token y GET con sesión | 403 en los cuatro primeros, 405 y `Allow: POST` en GET. Cero eventos `STAT_CONSULTA` por esas solicitudes | Quitar la guarda de REPORTES, la de TI, el requisito CSRF o añadir GET. Una prueba HTTP por mutación. |
| Concordancia de COM por canal | E08 | Fecha administrativa y alcance institucional. A/B/C/D computables en registro, F inválida, G antecedente | `Reports=6`. E08 `4+1+1=6`. Un registro por canal Formulario adulto, correo, presencial, teléfono, oficio y otro. En el subconjunto común presencial/teléfono, ambos dan 2 | Aplicar recepción a la consulta administrativa u omitir una de las categorías informativas → `conciliaComPorCanal`. |
| Concordancia de REQ por estado | E08 | A abierto tras reapertura y D cerrado, ambos constituidos en el período | ABIERTO=1, CERRADO=1, total 2 en ambos motores y en las tablas base | Multiplicar A por sus Casos o usar fecha de COM → `conciliaReqPorEstado`. |
| Sede activada y cerrada | 32 | Tres COM con origen 07, antes de activar S06, durante su cobertura y justo al cerrarla | `SIN_ATENCION=2`, `S06=1`. La COM histórica cubierta sigue visible a REPORTES institucional | Ignorar `AMBITO_ACTIVADO`, ignorar `AMBITO_CERRADO` o exigir `enabled` actual para el conteo histórico → `sedeActivadaYCerrada`. |
| Sede proyectada | 32 | S05 proyectada para región 05, con cobertura preparada para 08 | Dos COM de regiones 05/08 dan `SIN_ATENCION=2` y `S05=0` | Dar cobertura a las proyectadas → `sedeProyectada`. |
| Cobertura de nivel central | 32 | Tres recepciones de región 13, antes, al inicio y al término de un período central | `NIVEL_CENTRAL=1`, `SIN_ATENCION=2` | Omitir `scope_id IS NULL` o incluir el instante de término → `coberturaCentral`. |
| Formulario, requirente y hechos | 32 | Formulario adulto `adult-commune=Talca`, `hechos-region=06`, `hechos-commune=Rancagua`. Formulario NNA `nna-commune=Viña del Mar` | Talca=1, Viña del Mar=1, hechos 06/Rancagua=1. El NNA queda sin dato de hechos en este vector | Leer la clave adulta para el NNA o usar la comuna de quien presenta como comuna de hechos → `formularioTerritorio`. |
| Demografía estructurada | 14 | Un NNA cumple 10 años el día chileno de recepción, otro declara 8 y un tercero solo tiene el texto histórico `12`. Dos países estructurados y uno en texto libre histórico | Edades 10=1, 8=1, sin dato=1. Nacionalidad con dato `2/3`. Cero fechas de nacimiento en la respuesta | Calcular edad en UTC, interpretar el texto histórico o serializar la fecha de nacimiento → `demografiaEstadistica`. |
| Completitud y propuestas | 4, 7, 28 | Tres COM en una sede. Una informada, otra pendiente con propuesta y otra `SIN_INFORMACION` | `INFORMADO=1`, `PENDIENTE=1`, `SIN_INFORMACION=1`. La propuesta aparece aparte y suma 1 | Elevar la propuesta a informada o perder el estado especial → `calidadYPropuesta`. |
| Auditoría y validación | D41, §2.9 | Consulta `INGRESOS` por `CANAL` con filtro `REGION_REQUIRENTE=07`. Petición aparte con clave ajena al contrato | Un `STAT_CONSULTA` con los tres nombres. Cero valores de filtro, fechas, cifras o resultados en la nota. Petición inválida 400 | Serializar el cuerpo o el total en auditoría, o aceptar una clave sin validar → `auditoriaSoloNombres` y `contratoCerrado`. |
| Límite y consultas por conjunto | §5.7 | Misma consulta con 5 y 500 COM. Prueba del límite configurado a 2 y consulta que produce 3 celdas | Igual número de sentencias JDBC para la misma forma de consulta. Con 3 celdas se obtiene 409 y cero resultados parciales | Introducir una lectura JDBC por COM o truncar la tabla en lugar de rechazar → `consultasPorConjunto` y `limiteDeCeldas`. |

#### Implementación, archivos, verificaciones y riesgos

1. `StatSource.java` construirá una relación base de una fila por COM y relaciones separadas para valores
   multivaluados y medidas con otra unidad. Aplicará alcance e intervalo al conjunto base antes de expandir valores.
   Las relaciones múltiples se reducirán por COM y código antes del cruce. La fuente tendrá columnas y claves estables
   que E10 podrá obtener también de un corte.
2. `StatEngine.java` validará la petición, compondrá SQL parametrizado, obtendrá celdas y márgenes por agregación
   en SQL y calculará porcentajes y comparaciones en Java. Usará una lectura consistente durante toda la consulta,
   incluida la comparación. Solo proyectará dimensiones solicitadas o necesarias para filtros. Los intervalos se
   calculan una vez en Java y SQL compara milisegundos. Las búsquedas de evidencia reutilizarán los índices de hitos,
   eventos y versiones. Se revisarán los caminos críticos con `EXPLAIN` en MySQL. La medición formal de 50.000 COM
   y el objetivo de menos de 3 s corresponden a E14.
3. `Api.java` y `Queries.java` conectarán el POST y el catálogo de medidas y dimensiones. Se propone
   `siger.stats.max-cells` con valor inicial 20.000, sobre la consulta y su comparación, comprobado antes de
   devolver resultados. La propuesta parte sin migración. La V22 aditiva para índices queda sujeta a la condición
   de `EXPLAIN` del GO.
4. Pruebas nuevas en `StatEngineTest.java`, `StatQueryHttpTest.java` y recursos CSV sintéticos fijos en
   `backend/src/test/resources/stats/`. El contrato aprobado se fijará en `docs/CONTRATO_STATS_0_8_0.md`.
   Documentación de arquitectura en `CLAUDE.md` y `docs/ARQUITECTURA.md`, y cierre en esta bitácora con el
   formato de la sección 7.
5. Verificación final con H2 y MySQL 8.4 completos, `siger.mysql.suite=true` y ambos recorridos de actualización
   desde 0.4.0 y 0.7.0, en MySQL desechable ligado a `127.0.0.1`. Los binarios portables están disponibles.
   Typecheck, pruebas y build del frontend por la ampliación de `/api/catalog`, humo HTTP con datos sintéticos
   en `target/humo-*` y `Check-Scripts.ps1` en PowerShell 5.1 por el cambio en `CLAUDE.md`. Ejecutar cada
   mutación en una copia temporal, registrar la aserción que falla y correr las suites sobre el código final.
   Verificar la sección 2 con `git diff` y revisar el alcance y los archivos sensibles antes del push.
6. Riesgos: multiplicación silenciosa al unir relaciones, fuga de datos a través de Casos o filtros, cambios de
   significado entre versiones de catálogo y costo de cruces con muchas celdas. La matriz fija las cifras y los
   controles de alcance. En E08 se revisarán los planes SQL y el número de sentencias. Se conservan cifras exactas,
   incluidas las celdas de valor 1, según D41.

#### Respuestas y condiciones del GO del responsable

- Se autoriza `docs/CONTRATO_STATS_0_8_0.md`. E10 a E13 lo usarán sin cambios. Cualquier cambio posterior se
  registra como desvío.
- `siger.stats.max-cells` tiene un valor inicial de 20.000 y se aplica a la consulta más su comparación. Al
  superarlo se devuelve 409 en lenguaje llano, sugiriendo reducir dimensiones o período, sin resultados parciales.
  Es una salvaguarda técnica para la respuesta completa. D41 excluye la supresión estadística y exige cifras
  exactas en todas las consultas admitidas. Documentarlo así en `CLAUDE.md` y en el contrato.
- V22 solo si `EXPLAIN` en MySQL muestra un recorrido completo en un camino crítico. Será aditiva y la evidencia
  se registrará antes de incorporarla. Sin evidencia, no hay migración.
- Las constancias reservadas se excluyen de los agregados de actuaciones (D41). Incluir la regla en el contrato y
  en el diccionario de la exportación de E11.
- Corridas una vez al final: H2 completo, MySQL 8.4 completo con `siger.mysql.suite=true` y actualizaciones
  desde 0.4.0 y 0.7.0, frontend completo, humo en `target/humo-*` con borrado al final y `Check-Scripts.ps1`
  con `powershell.exe` 5.1 sin Maven ni npm en paralelo. Detener el MySQL desechable y borrar su entorno.
  Conservar MySQL80 intacto.
- Ejecutar todas las mutaciones de la matriz de forma temporal y revertirlas. Registrar la tabla
  «regla → prueba que falló» con el nombre real de cada prueba.
- Commit final `gate E08: motor estadistico`. Revisar que no viajen archivos sensibles, hacer push a
  `origin/main` y detenerse. El ejecutor anota su propio nombre en el cierre, Claude Code o Codex.
- E08 sigue ABIERTO. El GO se registra con el commit de solo documentación `docs: plan y GO de E08`.
  La implementación se realizará en una sesión posterior.

#### GO de ejecución (2026-11-25, sesión de Claude Code) y evidencia de EXPLAIN

La sesión que ejecuta comprobó la base (`c9abb9a` más el commit de documentación `6a483c7`) y, antes de editar, informó
que `AL_REQ` dependía de milisegundos: en 200 altas de cada tipo sobre H2, dos corridas, quedaban fuera la ficha de
`ATTENTION_CREATE` (78 y 79), su clasificación (116 y 120) y los derechos de `COM_IMMEDIATE` (55 y 64). El responsable
respondió «GO: A(a), B sí, C sí»:

- A(a). `AL_REQ` corta al término del comando que constituyó el REQ. Se autorizan `TerritorialDims.java` y
  `PersonDims.java`, solo en el modo REQ. Corte determinista también con el mismo milisegundo, prueba
  `actoDeConstitucion` con su mutación, `corteVersionReq` para la evidencia de un comando posterior y medición repetida
  de 200 altas en H2 y MySQL, con 0 pérdidas e igual resultado en dos corridas.
- B. Sobrecargas en `StatValues.java` sin cambiar el SQL actual, con prueba carácter por carácter.
- C. Definiciones 1 a 5: conciliación con Reports como está propuesta; alcance de los Casos también en la completitud
  (documentado en el contrato); `TIPO_INSTITUCION` solo de `note_institution`, con el rótulo "Tipo de institución de las
  actuaciones", y la dimensión "instituciones de los hechos" en la hoja de ruta 0.9.0; `REMISION_CONCLUIDA` conserva su
  código y suma al total; complejidad, materia y modalidad históricas por código de auditoría o versión, nunca por texto,
  y sin evidencia pendientes con aviso.
- El resto, como está registrado. Consultado durante el gate, el responsable autorizó además el cambio mínimo en
  `Workflow.java`: el empate de milisegundo no es imposible (el reloj avanza de 1 en 1 ms) ni se resuelve leyendo
  (`ATTENTION_CREATE` con tipo de solicitud seguido de un `STAT_CLASSIFY` que corrige la vía deja las mismas filas que un
  `ATTENTION_CREATE` con ambas); con el lock global tomado, cada comando espera a que el reloj supere el fin del anterior.

Evidencia de EXPLAIN previa a V22 (MySQL 8.4.13 aislado, 50.005 COM sintéticas, 35.001 REQ, 20.000 Casos, 85.000
comandos; consulta de marzo de 2026; columnas tabla, acceso y filas estimadas):

| Camino crítico | Antes de V22 |
|---|---|
| Periodo por recepción (`communication.received_ms`) | `c` ALL, sin índice, 49.801 filas |
| Periodo por registro (`communication.created_ms`) | `c` ALL, sin índice, 49.801 filas |
| Periodo por constitución (`requirement.created_ms`) | `q` ALL, sin índice, 34.894 filas |
| Periodo por creación del Caso (`case_record.created_ms`) | `cx` ALL, sin índice, 20.049 filas |
| Corte `AL_REQ` (`command_log` por resultado) | `l` ALL, sin índice, 84.348 filas por cada REQ |

Con V22 los cinco pasan a `range` o `ref` sobre su índice (4.464, 4.464, 3.123, 1.785 filas y 1 por REQ).

Ejecutado en el commit `gate E08: motor estadistico`.

### E11 - GO del 2026-11-26 y avance del cierre (ejecutado; ver el registro de cierre de E11)

E11 lo implementó Codex en `main` sin commit (sesión de 08:50 a 09:58; se quedó sin cuota). Por encargo especial del
responsable (2026-11-26), Claude Code lo resguardó sin cambios en la rama `wip/e11-codex` (`de494e9`, sobre `58389fd`) y,
en un segundo encargo, empezó el cierre desde esa rama sin reimplementar. Se detuvo en el punto 4 (medición) porque se
cumplieron condiciones de detención del encargo. E11 sigue ABIERTO; nada se fusionó en `main`.

#### GO de E11 (transcrito íntegro)

GO de E11
Respuestas:
- A. Sí.
  - Se autorizan Workflow.java (solo el despacho de STAT_SERIES_IMPORT y STAT_SERIES_ANNUL), StatSource.java (la proyección de microdatos del mismo Plan) y StatCuts.java.
  - Las tablas complementarias del corte van en V24: el folio y el origen de cada valor (REQ o Caso, con su id). Se escriben en la misma transacción del corte, son inmutables, no tienen cascada y su lista de columnas es cerrada.
  - Tienen su propia huella. La verificación del corte la recalcula y detecta una alteración hecha por SQL.
  - La definición de rows_sha256 de V23 no cambia y las pruebas de E10 no cambian sus aserciones.
  - Vuelve a medir STAT_CUT_CREATE con 50.005 COM. Si pasa de 13 s, detente y consulta.
  - Para un corte sin tablas complementarias, CSV y XLSX funcionan; el ZIP responde 409 con un mensaje claro.
- B. Sí, con este ajuste.
  - Exportar exige EXPORTACION con REPORTES en el mismo ámbito, que cubra el alcance efectivo de la consulta y de los cortes que compara, en ámbitos y en materias (la regla de E10). Si no lo cubre, 403.
  - Sin REPORTES_PERSONAS en el mismo ámbito:
    - El ZIP no lleva columnas, estados ni filas de grupos prioritarios.
    - Si la consulta usa GRUPO_PRIORITARIO o ESTADO_GRUPO_PRIORITARIO como dimensión o filtro, el ZIP completo responde 403.
    - El diccionario indica la omisión.
  - CSV y XLSX agregados siguen la regla de la consulta en pantalla.
- C. Sí.
  - El ZIP con microdatos solo para medidas a nivel de COM: requerimientos y dimensiones multivaluadas con n, unique y mentions.
  - Para medidas de actuación o de Caso, solo CSV y XLSX agregados; el ZIP responde 400 con un mensaje en lenguaje llano.
  - Agrega a la sección 9 del plan: «Microdatos a nivel de actuación y de Caso en el paquete estadístico» | - | GO de E11 | -.
- DI-12: sin dependencias. Si el escritor o el lector XLSX de la JDK no basta, detente y consulta.
Condiciones:
1. Guardas: toda exportación (vigente, a una fecha o desde un corte) pasa por StatGuard.run y StatGuard.read, con cupo, plazo, 503 sin archivo parcial y auditoría STAT_EXPORTACION posterior. Las notas de auditoría llevan solo nombres y códigos: nunca valores de filtros, títulos ni fundamentos. TI nunca exporta.
2. Microdatos:
   - Una fila por COM computable y nunca un folio repetido.
   - microdatos_multivalor.csv con una fila por folio, variable y código, con en_req, casos y menciones.
   - Un REQ sin valores figura con su estado.
   - Un barrido de columnas y de contenido demuestra que no hay nombres, RUN, documentos, contactos, relatos, texto libre ni fecha de nacimiento en ningún archivo, CSV, XLSX o ZIP.
3. Diccionario:
   - La descripcion de cada ítem de catálogo.
   - La regla de las constancias reservadas: no se cuentan en los agregados de actuaciones.
   - La explicación de menciones y total único.
   - El riesgo residual de reidentificación.
4. Series (DISENO 7):
   - El ámbito y la visibilidad siguen la regla de los cortes.
   - El asistente permite indicar, categoría por categoría, a qué agrupación de SIGER corresponde, sin combinarlas automáticamente.
   - La correspondencia «Metropolitana» → «Oficina Nacional» (DI-13) se puede sugerir, pero nunca se aplica sin confirmación expresa, y queda en los metadatos de la serie.
   - La vista previa no escribe nada.
   - La importación es todo o nada.
   - La suma solo se hace si se pide y no hay solapamiento.
5. Seguridad de archivos:
   - El lector rechaza fórmulas, DTD, entidades, referencias externas, demasiadas entradas, una proporción de compresión excesiva, un tamaño descomprimido excesivo y demasiadas filas, con límites configurados y probados.
   - El escritor protege las celdas que empiezan con = + - @.
   - XLSX y ZIP son deterministas, con fechas internas fijas: los mismos datos producen los mismos bytes, salvo metadatos.json, y eso queda documentado.
6. Matriz de pruebas con cifras esperadas escritas a mano. Escríbela en las pruebas antes de implementar y transcríbela al registro de cierre:
   - (a) Figura 1 sintética: consulta = agregado.csv = agregado.xlsx = corte = reagregación de los microdatos, en los cuatro desgloses, y cada uno suma el total.
   - (b) Un derecho registrado en el REQ y en 2 Casos da en_req=1, casos=2, menciones=3 y REQUERIMIENTOS_CON_VALOR=1.
   - (c) El número de folios es igual al de COM computables: la inválida no aparece y la duplicada sí.
   - (d) Una actuación reservada queda fuera.
   - (e) Los grupos prioritarios con y sin REPORTES_PERSONAS (B).
   - (f) Una serie con 3 periodos: un total declarado distinto de la suma se rechaza; el solapamiento da aviso y no se suma.
   - (g) Un corte de V23 exporta CSV y XLSX, y su ZIP responde 409.
   - (h) Un archivo alterado dentro del ZIP no coincide con SHA256SUMS.txt.
7. Medición con 50.005 COM: generación del ZIP dentro del plazo, con la memoria máxima registrada. Si supera el plazo, detente y consulta.
8. Mutaciones: una por regla nueva o cambiada, incluidas las de tu plan y las de 1, 2, 5 y la de grupos prioritarios por cruce (B). Regístralas como «regla → prueba que falló».
9. Lecciones que rigen:
   - MySQL desechable en 127.0.0.1:3417, con datadir y tmpdir en una ruta nueva de D:, detenido al terminar y MySQL80 intacto.
   - current_catalog siempre entre acentos graves.
   - Check-Scripts.ps1 con powershell.exe 5.1, sin el PSModulePath de PowerShell 7 y sin Maven ni npm en paralelo.
   - Las corridas finales, una sola vez y sin procesos en paralelo: H2, MySQL (actualizaciones desde 0.4.0 y 0.7.0 hasta V24, con communication_original y external_* idénticos), frontend, humo y Check-Scripts.ps1.
10. Regresión: ninguna aserción se elimina; las pruebas de esquema solo cambian el número, y se registra.
11. Cierre:
    - Registro de la sección 7 con desvíos, diferencias de referencia y la matriz.
    - Commit "gate E11: exportaciones y series".
    - Verificación de la sección 2 y de que no viajen archivos sensibles.
    - Push y detente.

#### Evidencia por ejecutor

- Codex (sesión de 08:50 a 09:58; evidencia en `D:\Ensayo-E11`): implementación completa (`Xlsx`, `XlsxReader`, `StatExport`,
  `StatSeries`, V24, cambios en `Api`, `StatEngine`, `Workflow`, `StatSource` y `StatCuts`, pruebas, contrato y documentos);
  46 mutaciones de 09:23 a 09:34 (`mutaciones\`); corridas finales de 09:40 a 09:56: H2 `final-h2.log` 373 corridas,
  2 omitidas, `BUILD SUCCESS`; MySQL 8.4.13 `final-mysql.log` 377 corridas, 2 omitidas (375 ejecutadas); frontend 183,
  typecheck y build; humo 21/21 con el cliente corregido (`final-humo-corregido.log`; el primero falló por el cliente de
  PowerShell que conservaba el encabezado CSRF, no por SIGER); `Check-Scripts.ps1` conforme. Una medición a las 09:16
  (`medicion.log`, `backend\target\medicion-e11.txt` de 09:16): corte 12.463 ms, ZIP 26.766 ms, sin `-Xmx` (montón por
  omisión) y con el código de ese momento. MySQL de `D:\Ensayo-E11\mysql` detenido a las 09:57 ("Shutdown complete").
- Claude Code (este cierre parcial; evidencia en `D:\Ensayo-E11c`): puntos 1 a 4 de abajo.

#### Punto 1: mutaciones y archivos cambiados después

El script `ejecutar-mutaciones.py` restaura cada archivo con sus bytes originales y lo comprueba por SHA-256
(`assert`); `restauracion.log` está vacío porque `javac` recompila sin salida. El registro de la sesión de Codex muestra
cambios posteriores a la mutación de su regla:

| Hora | Archivo | Cambio | Mutaciones afectadas |
|---|---|---|---|
| 09:31:13 | `StatExportTest.java` (+13 -1) | reagregación de los microdatos, total único y menciones, aserciones del diccionario | 01-40 (corridas antes; solo se agregaron aserciones) |
| 09:31:13 | `StatSeriesTest.java` (+13) | prueba `xlsxConColumnasRelacionadasSeConfirmaIdempotente` | 33-39 |
| 09:33:27 | `StatSeries.java` (+2 -1) | aviso `ALCANCES_DISTINTOS` por cobertura (`StatCuts.covers` en ambos sentidos) | 33-39 y 45 corrieron sobre la versión anterior; 46 sobre la final |
| 09:34:18 | `Api.java` (+1 -1) | `GET /stats/series/{id:\d+}` | 40 corrió sobre la versión anterior |
| 09:34:18 | `StatCutHttpTest.java` (+2) | 405 para `GET /stats/series/preview` y `/compare` | - |

Ningún archivo de `src` cambió después de las 09:34:18 hasta esta sesión. Conclusión: no se puede afirmar que las
mutaciones 33-40 y 45 se hayan corrido sobre el código final; hay que repetir las 46 (y agregar las nuevas) sobre el
código final antes del cierre. Las 46 de `evidencia.txt` (la 11 se corrió dos veces, ambas DETECTADA):

| Regla | Prueba que falló |
|---|---|
| 01 cifras del mismo motor | StatExportTest#figuraUnoReproducible |
| 02 duplicadas cuentan D25 | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 03 invalidas fuera D25 | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 04 folio unico | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 05 derecho en REQ | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 06 casos distintos de una clasificacion | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 07 menciones de una clasificacion | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 08 estado de REQ sin valores | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 09 constancia reservada fuera | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 10 excluir grupos del paquete sin facultad | StatExportTest#gruposPrioritariosYAlcance |
| 11 negar ZIP por cruce de grupos prioritarios | StatExportTest#gruposPrioritariosYAlcance |
| 12 negar ZIP por filtro sensible | StatExportTest#gruposPrioritariosYAlcance |
| 13 alcance de exportacion principal | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 14 alcance del corte de comparacion | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 15 ZIP requiere medida reproducible | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 16 V23 ZIP 409 explicito | StatExportTest#corteV23SeExportaAgregadoYDetectaCambiosEnSuComplemento |
| 17 huella del complemento recalculada | StatExportTest#complementoConListaCerradaYProvenienciaQueSeVerifica |
| 18 huella manifiesto por archivo | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 19 barrido de contenido de archivos | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 20 cupo compartido de exportacion | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 21 motor deshabilitado protege exportacion | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 22 plazo de resultado completo | StatTimeoutHttpTest#exportacionVencidaNoAbreUnaDescargaYLiberaCupo |
| 23 auditoria solo nombres | StatExportTest#exportacionHistoricaConLaMismaReglaYAuditoriaSinValores |
| 24 XLSX protege prefijos de formula | XlsxTest#idaYVueltaDeterministaConTextoProtegido |
| 25 XLSX fechas internas fijas | XlsxTest#idaYVueltaDeterministaConTextoProtegido |
| 26 limite de entradas XLSX | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 27 limite descomprimido XLSX | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 28 limite de filas XLSX | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 29 compresion relativa por entrada XLSX | XlsxTest#relacionDeCompresionPorEntradaAunqueElZipCompletoEstePorDebajo |
| 30 XLSX rechaza formula | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 31 XLSX rechaza DTD sintacticamente valida | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 32 XLSX rechaza relacion externa incluso relativa | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 33 preview no escribe | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 34 homologacion historica requiere confirmacion | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 35 totales invalidos rechazan la importacion entera | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 36 no sumar periodos solapados | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 37 no sumar sin solicitud | StatSeriesTest#sumaExplicitaPeriodosSeparadosConTotalesSinDuplicar |
| 38 series visibles solo bajo ambito | StatSeriesTest#facultadesDeSedeYAnulacionConservaFilas |
| 39 anulacion comprueba revision | StatSeriesTest#facultadesDeSedeYAnulacionConservaFilas |
| 40 consulta de exportacion solo POST | StatCutHttpTest#exportacionYSeriePorSusRutasHttp |
| 41 diccionario conserva la descripcion de cada item | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 42 diccionario explica la reserva | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 43 diccionario explica el total unico | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 44 diccionario identifica el riesgo residual | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 45 importacion en el ambito de gestion | StatSeriesTest#facultadesDeSedeYAnulacionConservaFilas |
| 46 aviso de ambito usa cobertura efectiva | StatSeriesTest#comparacionConSigerAvisaSoloCuandoDifiereElAlcance |

#### Punto 2: pruebas omitidas en H2

Las 2 omitidas de `final-h2.log` (y de `final-mysql.log`) son las dos de `StatLoadMeasureTest`: `medicion` (E08b/E10) y
`medicionE11`. La clase lleva `@EnabledIfSystemProperty(siger.stats.measure=true)`, de modo que sin esa propiedad JUnit
omite ambas; `medicionE11` exige además `siger.stats.measure-e11=true` y la suite sobre MySQL. Se omiten a propósito: la
medición genera 50.005 COM y solo corre a mano sobre el MySQL desechable.

#### Punto 3: pruebas completadas (sin cambiar aserciones existentes)

- (h) `StatExportTest.archivoAlteradoDentroDelZipNoCoincideConSha256Sums`, nueva: el paquete tiene 9 archivos y
  `SHA256SUMS.txt` declara los otros 8; el paquete se reescribe 8 veces, cada una con un archivo alterado, y al releerlo
  solo ese archivo no coincide. Un archivo agregado y uno quitado también se detectan. La aserción anterior de Codex
  alteraba una copia fuera del ZIP y queda igual.
- Barrido `StatExportTest.barridoDeDatosCiudadanosEnTodosLosArchivosYHojas`, nueva: 2 cuentas (sin y con
  REPORTES_PERSONAS), 5 consultas (vigente, derechos por contexto, tramo del NNA y del requirente, desde el corte) y
  ZIP, CSV y XLSX = 230 partes (ZIP: 8 archivos + 9 partes de `agregado.xlsx`; CSV: 1; XLSX: 5), más las actuaciones en
  CSV y XLSX. Por cada parte: textos ciudadanos sin distinguir mayúsculas ni tildes (nombres, apellido, nombre social,
  correo, domicilio, otro contacto, documento, celular, relato, RUN con y sin puntos, fecha de nacimiento en 4 formatos);
  RUN sin DV, celular sin prefijo y fecha compacta como valor aislado; encabezados de cada CSV y de cada hoja XLSX contra
  una lista de nombres prohibidos; y los seriales de Excel de ambas fechas (35140 y 41538) en ninguna celda. La edad sí
  viaja: EDAD_NNA = 11 para A (escrito a mano). Para esto la COM A de la población lleva ahora, en su ficha de ingreso,
  quien presenta (RUN 12345678-5, nacimiento 1996-03-16, correo, celular, domicilio, otro contacto, nombre social) y un
  NNA (RUN 11111111-1, nacimiento 2013-09-21): datos sintéticos, sin cambio en ninguna aserción existente.
- Matriz (a)-(h) con cifras escritas a mano:

| Punto | Prueba | Cifras |
|---|---|---|
| (a) | StatExportTest#figuraUnoReproducible | TIPO_SOLICITUD VULNERACION 1, CONSULTA_ORIENTACION 2, PROMOCION 1; TIPO_REQUIRENTE PERSONA_NATURAL_ADULTA 2, NNA 1, PERSONA_JURIDICA_ORGANISMO 1; VIA_INGRESO CORREO 2, TELEFONO 1, PRESENCIAL 1; ADSCRIPCION_INICIAL E11-S06 2, OFICINA_NACIONAL 1, PENDIENTE 1; total 4. Consulta = agregado.csv = agregado.xlsx = CSV = XLSX = reagregación de microdatos.csv = consulta y ZIP sobre el corte |
| (b) | StatExportTest#derechoConTresOrigenesYCuatroFolios | 3.1 en el REQ de A y en 2 Casos: en_req 1, casos 2, menciones 3; REQUERIMIENTOS_CON_VALOR 3.1 = 1, PENDIENTE = 3 (reagregado = agregado.csv); total único 4 |
| (c) | StatExportTest#derechoConTresOrigenesYCuatroFolios, #figuraUnoReproducible | 4 folios (A, B, C, E) = 4 COM computables: D (INVALIDA) no aparece, C (DUPLICADA) sí |
| (d) | StatExportTest#derechoConTresOrigenesYCuatroFolios | la constancia reservada OFICIO de A no figura en el multivalor; ACTUACIONES por tipo sin celdas; ZIP de ACTUACIONES 400 |
| (e) | StatExportTest#gruposPrioritariosYAlcance | sin REPORTES_PERSONAS: sin columnas ni filas de grupos y el diccionario lo indica; con GRUPO_PRIORITARIO o ESTADO_GRUPO_PRIORITARIO como columna o filtro, ZIP 403 y XLSX permitido; con REPORTES_PERSONAS el ZIP los lleva |
| (f) | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion, #sumaExplicitaPeriodosSeparadosConTotalesSinDuplicar | 2, 3 y 5; total de febrero declarado 4 en vez de 3: 400 sin escrituras; dos series superpuestas: aviso PERIODOS_SUPERPUESTOS y sin suma; enero y febrero separados con suma pedida: 5 y total 5 |
| (g) | StatExportTest#corteV23SeExportaAgregadoYDetectaCambiosEnSuComplemento | corte sin complemento: CSV y XLSX con contenido, ZIP 409 ("antes de incorporar") |
| (h) | StatExportTest#archivoAlteradoDentroDelZipNoCoincideConSha256Sums | 9 archivos, 8 declarados; cada uno de los 8 alterado: solo él no coincide |

Corrida dirigida en H2 después de los cambios (`D:\Ensayo-E11c\h2-e11-dirigidas.log`): StatExportTest 10,
StatSeriesTest 5, XlsxTest 4, StatTimeoutHttpTest 2, StatCutHttpTest 2; 23 ok.

#### Punto 4: medición (detención)

Cambio en `src/main` para el desglose: `StatExport.PHASES`, un `ThreadLocal` que solo la medición activa y que acumula
los ms de consulta, microdatos, csv, xlsx y compresión; sin activarlo no registra nada ni cambia el archivo. Por este
cambio el humo del JAR de Codex ya no vale y hay que repetirlo. `StatLoadMeasureTest.medicionE11` conserva sus dos
aserciones (corte <= 13 s y cada ZIP < 30 s), ahora al final, y agrega el ZIP desde el corte, dos ZIP simultáneos, la
memoria después de cada recolección (`GcInfo.getMemoryUsageAfterGc` de los grupos HEAP, por notificación de cada
recolector, con `CollectionUsage` como respaldo) y la exigencia de medir con `-Xmx1024m` (el de `Service.ps1`).

MySQL 8.4.13 desechable en `D:\Ensayo-E11c\mysql` (datadir y tmpdir ahí, `127.0.0.1:3417`), 50.005 COM, montón de
1.024 MiB (`-DargLine=-Xmx1024m`), plazo de 30 s; `D:\Ensayo-E11c\medicion-e11.txt` y `medicion-e11.log`:

| Paso | Total | Consulta | Microdatos | CSV | XLSX | Compresión | Memoria después de recolectar (máx.) | Uso máximo muestreado |
|---|---|---|---|---|---|---|---|---|
| STAT_CUT_CREATE (Cuenta Pública, 25.084 COM congeladas) | 12.551 ms | - | - | - | - | - | 94 MiB | 192 MiB |
| ZIP vigente, 2024-07-01 a 2026-06-30 (unos 49.000 folios, estimados: 50.005 COM menos las INVALIDA) | **28.076 ms** | 2.658 | 17.832 | 1.645 | 4.508 | 1.423 | 609 MiB (59 %) | 785 MiB |
| ZIP vigente, Cuenta Pública | 13.551 ms | 1.289 | 8.451 | 782 | 2.316 | 705 | 434 MiB (42 %) | 512 MiB |
| ZIP desde el corte, Cuenta Pública | **30.463 ms, 503** | 619 | sin terminar | - | - | - | 130 MiB | 266 MiB |
| Dos ZIP simultáneos (2 años, dos cuentas) | **30.246 y 30.274 ms, 503 ambos** | 3.050 / 3.054 | 19.472 / 19.457 | 1.746 / 1.750 | 4.559 / 4.589 | 1.410 / 1.418 | **956 MiB (93 %)** | 948 MiB |

Sin falta de memoria. Condiciones de detención cumplidas: un ZIP supera 25 s (28,1 s); el ZIP desde un corte y los dos
simultáneos agotan el plazo (503); la memoria después de recolectar supera el 75 % de 1.024 MiB (956 MiB con dos ZIP).
El corte (12,6 s) queda bajo 13 s. La fase dominante es microdatos (las 4 proyecciones del `Plan` y el armado en
memoria): 64 % del total vigente. Desde el corte, la proyección de microdatos no termina en 29,8 s, cuando la vigente del
mismo periodo tarda 8,5 s: el problema está en la proyección sobre `stat_cut_*`/`stat_export_cut_*`, no en la memoria.

Propuesta del ejecutor (para decisión del responsable):
1. Un solo ZIP a la vez en la instancia (cupo propio de la exportación ZIP; el segundo recibe 429 en texto llano, como el
   cupo actual). Resuelve la memoria de dos simultáneos (uno solo: 609 MiB, 59 %) y que uno haga vencer al otro.
2. Bajar `siger.stats.export.max-rows` de 100.000 a 30.000 folios por ZIP: la Cuenta Pública (unos 25.000; el corte congela
   25.084 COM en todos sus estados) cabe en 13,6 s; dos años (unos 49.000) responden 409 "acota el periodo o los filtros". Deja margen bajo 25 s con el volumen actual.
3. Diagnosticar con EXPLAIN y corregir la proyección de microdatos en modo corte (`StatSource`, autorizado en A, o índices
   de `stat_export_cut_*` en V24, que aún no está publicada) y volver a medir. Sin esto el ZIP desde un corte no es
   utilizable, y ni 1 ni 2 lo resuelven.
4. Alternativa a 1: escribir el ZIP por flujo a un temporal en la unidad de la instancia (para conservar "503 sin archivo
   parcial") en vez de armarlo en memoria. Baja la memoria pero no el tiempo; más cambio en `StatExport` que 1.
   Recomendación: 1 + 2 + 3, y 4 solo si se quiere admitir más de un ZIP a la vez.

#### Pendiente para cerrar E11 (después de la decisión)

- Aplicar lo que decida el responsable y repetir la medición (`StatLoadMeasureTest#medicionE11` con `-DargLine=-Xmx1024m`).
- Repetir las 46 mutaciones sobre el código final y agregar las de (h), el barrido y la regla que se decida (punto 1).
- Corridas finales, una vez y sin procesos en paralelo: H2 completo, MySQL completo (desechable, actualizaciones desde
  0.4.0 y 0.7.0 hasta V24), frontend, humo con el JAR (cambió `src/main`) y `Check-Scripts.ps1` con powershell.exe 5.1 si
  cambia `CLAUDE.md`.
- Registro de cierre (sección 7), squash de `wip/e11-codex` en `main` con el commit `gate E11: exportaciones y series`, push.
- Responsable: borrar `D:\Ensayo-E11` y `D:\Ensayo-E11c` (MySQL detenidos; datos sintéticos y la clave del usuario
  desechable).

Estado al detenerse: MySQL de `D:\Ensayo-E11c\mysql` detenido con `mysqladmin shutdown` ("Shutdown complete"), sin
proceso escuchando en 3417 (solo conexiones en TIME_WAIT); MySQL80 y la instancia de `D:\sigervalidacion` intactos.

## Registros de cierre

### E00 - Linea base, decisiones, AGENTS.md y contrato del Formulario   (CERRADO)
- Fecha: 2026-11-24 (commit `gate E00: linea base y contrato del Formulario`), DDN-TEST-SIGER (Windows), Claude Code; base `0732d1a` (plan sobre `3938e4e` = `v0.7.0`).
- GO del responsable: cinco mutaciones temporales en `backend/src/main` autorizadas (locales, revertidas antes del commit); sin la clave institucional; CRLF a LF en las pruebas antes de las huellas y de `ExternalOriginal.parse`, sin tocar `.gitattributes`; adjunto 1.1 incrustado y comparado con `PROCEDENCIA.txt`; H2 antes y despues, MySQL, frontend y `Check-Scripts.ps1` una vez cada uno; en el registro solo las diferencias de referencia; paquetes 0.6.0/0.7.0 no buscados.
- Hecho: paso 1, contrato del Formulario CONFORME: vectores `formulario-nna-srcei-v1.{0,1}.json` y `public-nna-srcei-test.json` copiados byte a byte de formulario-web `6f886eb` (clon temporal solo de lectura, fuera del repositorio) con `PROCEDENCIA.txt`; `ExternalOriginal.parse` los admite con la clave de prueba; por `conserveExternal` y por `FormularioReception` (productor falso, ACK 1.1 tras el COMMIT, adjunto de 45 bytes custodiado) la COM queda con `external_person.authenticated=false`, `run_source='SRCEI'`, atributos `CONTRASTADO_EN_FUENTE`, `person_reference.authenticated=false` y region 13; un reenvio o una nueva entrega no reproyecta; seis alteraciones del vector (fuente, nivel, autenticada, valor sin resellar, firma, keyId) se rechazan con 400 y sin escrituras. Paso 2, linea base. Paso 3, D25-D51 en `DECISIONES_FUNCIONALES.md` ("Precision 0.8.0", D44-D49 rotuladas "Hoja de ruta 0.9.0, no vigente en 0.8.0"; `diff` contra las lineas 20-208 del plan: identico salvo encabezado, introduccion, rotulos y sus lineas vacias), estado de `CLAUDE.md` y `AGENTS.md`.
- Archivos clave: `backend/src/test/resources/formulario/*`, `FormularioSrceiContractTest.java` (4), `FormularioSrceiReceptionTest.java` (1), `frontend/tests/formularioCatalogs.test.ts` (1), `docs/DECISIONES_FUNCIONALES.md`, `CLAUDE.md`, `AGENTS.md`.
- Migraciones: no aplica (esquema 16).
- Verificacion: JUnit H2 196 ok, 0 omitidas (antes 191, corrida previa sin cambios); MySQL 8.4.13 (`MySqlSuite` + `MySql84IntegrationTest` + `MySql84UpgradeTest`) 199 ok, 0 omitidas (base 194 acreditada en T03c); frontend typecheck ok, 113 ok (antes 112), build ok; `Check-Scripts.ps1` (5.1): 40 scripts, 73 ordenes en 60 procesos; humo no aplica. MySQL con el procedimiento de T03c: `mysqld` de `D:\Herramientas\mysql-8.4` (solo lectura) en consola, `--defaults-file` y datadir en el scratchpad, `127.0.0.1:3417` libre antes, esquemas vacios y usuario desechable con `--init-file`; las dos clases nuevas en sus esquemas `siger_a_mysql_srcei_contrato` y `siger_a_mysql_srcei_recepcion`; detenido con `mysqladmin shutdown`, puerto libre, sin procesos propios, datadir y `surefire-reports` borrados; `MySQL80` intacto (Running).
- Evidencia de mutacion (codigo revertido; `git diff -- backend/src/main frontend/src` vacio): `ExternalProjection` marca autenticada a la persona con RUN SRCEI -> `assertSrcei:85` (contrato y recepcion) y `recursosCopiados...:140`; `PersonIndex` con `authenticated=true` en FORMULARIO -> `assertSrcei:97`; `ExternalOriginal.LEVELS` sin SRCEI/CONTRASTADO_EN_FUENTE -> los vectores validos se rechazan (`recursosCopiados...:133`, `conserveExternal...:154`, recepcion `CONTRATO_INVALIDO`) y `vectoresAlterados...:189`; reenvio que vuelve a indexar -> `conserveExternal...:161` (instantanea distinta); un byte de `territorio.json` del servidor -> `catalogosDelServidor...:147`, y del frontend -> `formularioCatalogs.test.ts:28`.
- Condiciones de la seccion 2: `git diff v0.7.0` vacio para los 8 archivos del Formulario, `scripts/`, `.mvn/` y `db/migration`; ninguna linea de `backend/src/main` ni `frontend/src` en el commit; vectores sinteticos (RUN 99999999-9); sin dependencias nuevas.
- Facilidad de uso: no aplica (sin interfaz).
- Definiciones usadas: ninguna.
- Desvios del plan: ninguno. La comparacion de texto exacto del documento guardado vale solo para `conserveExternal`: el cliente PULL recibe la entrega compactada dentro de la respuesta, por lo que en la recepcion se compara como arbol JSON (huellas y firma verificadas igual); comportamiento vigente, no del contrato.
- Diferencias de referencia: (1) con `* text=auto` la copia de trabajo de Windows tiene CRLF: el SHA-256 de los bytes en disco difiere del blob (`territorio.json` `a76626d6...` frente a `191621ac...`); S18 se cumple sobre el contenido de git y las pruebas pasan CRLF a LF. (2) El clon local `C:\nformulario` esta atrasado (`5b17003`) y no tiene los vectores SRCeI; se uso un clon temporal en `6f886eb` (`origin/main`). (3) `SOURCE_SHA256.txt` no incluye los tres documentos del plan desde `0732d1a` (el resto coincide con el indice); se regenera en E17. (4) S4: la constancia del resultado inmediato usa como prefijo el codigo del resultado (`ORIENTACION`, `RESPUESTA_DIRECTA`, `REMISION_CONCLUIDA` + ": "), no el texto literal `RESULTADO: `. Paquetes 0.6.0 y 0.7.0: no verificados en E00, acreditados en T06b. El resto de la seccion 1 del plan y de S1-S18 coincide.
- Hallazgos para gates siguientes: los recursos versionados que se comparan por huella deben leerse con CRLF a LF; `assertSrcei`/`snapshot` de `FormularioSrceiContractTest` sirven a E01 para el volcado 0.7.0 con los vectores; `Check-Scripts.ps1` lanzado desde una sesion de PowerShell 7 hereda su `PSModulePath` y falla al cargar `Microsoft.PowerShell.Security` en 5.1: lanzarlo sin esa variable (la primera corrida de E00 no llego a ejecutarse por eso).
- Pendientes: ninguno de E00.
- Verificacion manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=FormularioSrcei*Test` y `cd frontend; node --test tests/formularioCatalogs.test.ts`.

### E01 - Volcado sintetico 0.7.0 y catalogos estadisticos versionados (servidor)   (CERRADO)
- Fecha: 2026-11-24 (commit `gate E01: volcado 0.7.0 y catalogos`), DDN-TEST-SIGER (Windows), Claude Code; base `30d4e73` (E00).
- GO del responsable: lectura de TI `GET /api/stat-catalogs` y `/{id}` (403 al resto, en el barrido); columnas `revision`, `activation_basis`, `retired_by`, `current_catalog` UNIQUE, `source_ref` y metadatos de la correspondencia; accion `CATALOGO_CORRESPONDENCIA_IMPORTADA`; TRAMO_EDAD con cobertura exacta 0-130; huella sin BOM y con LF; activar exige fundamento; reimportar una RETIRADA crea otra PROPUESTA; revision del volcado; corridas sin repeticiones; siete mutaciones.
- Hecho: (1) volcado `upgrade/siger-0.7.0-sintetico.sql` con el procedimiento de G13: worktree temporal de `v0.7.0` en el scratchpad y clase `Upgrade070DataTest` solo del worktree, que ejecuta con los comandos de 0.7.0 sobre `siger_a_mysql_upgrade070` (semillas demo) el recorrido de `MilestoneTest` (26 hitos, REQ cerrado y reactivado, Caso reabierto, colaboracion, SOL devuelta/redirigida/descartada, resolucion en el acto, ficha NNA 1.0 entregada, Formulario adulto) mas los dos vectores NNA SRCeI de E00 por `conserveExternal`, fichas 1.1 GENERAL (region 07, con una version complementada) y NNA, transferencia aceptada con REQ cerrado por el nuevo principal, derivacion aceptada, vinculo admitido (COM VINCULADA) y vinculo rechazado y devuelto, COM INVALIDA, SOL pendiente, COM sin encaminar, region 06 y cobertura 07 de REG-DEMO, cobertura central 13 y sede `REG-PROY` PROYECTADA (05). Resultado: 17 COM, 8 REQ (3 cerrados), 4 Casos, 4 versiones de ficha (1 de 1.0), 3 originales, 2 vinculos, 3 flujos, 2 coberturas, 77 hitos. Claves invalidadas con `UPDATE staff SET password_hash='volcado-sintetico-sin-acceso'` (18 cuentas, ninguna con correo) y `mysqldump` con las opciones de G13; worktree retirado. Ningun escenario quedo sin crear. (2) `MySql84Upgrade070Test` (con `MySql84UpgradeTest.load(schema,dump)`): carga, `migrate-only` y arranque normal; filas por tabla iguales (salvo Flyway), huella de todas las columnas de `communication_original` y `external_*` igual, SHA-256 de cada original recalculado, marcadores y ningun catalogo sembrado; corrido una vez antes de V17 (hasta V16: 1 ok, 0 omitidas). (3) V17 y `StatCatalogs`: comandos de TI `CATALOG_IMPORT`, `CATALOG_ACTIVATE` y `CATALOG_MAPPING_IMPORT` en `Personnel`, `stat_catalogs` en `/api/catalog` (solo VIGENTE), lectura de TI y recurso de auditoria `CATALOGO_ESTADISTICO` para TI y AUDITORIA.
- Revision del volcado (busquedas en el archivo, 158.181 bytes): hashes de clave (`$2a/$2b/$2y`, argon2, pbkdf2, `{bcrypt}`, `{noop}`) 0; `password_hash` de `staff` distinto del marcador 0; `secret` 0 y `token` solo como nombre de columna; rutas `C:\` o `D:\` 0; `nexbyte`, `proveedor`, `chashm`, `claude`, `DDN-`, `TEST-SIGER`, `localhost` 0; correos solo `persona@ejemplo.cl` y `maria.jose@example.cl` (sinteticos); `PRIVATE KEY` 0.
- Archivos clave: `db/migration/V17__catalogos_estadisticos.sql`, `StatCatalogs.java`, `Personnel.java`, `Workflow.java`, `Queries.java`, `Api.java`, `StatCatalogsTest.java` (6), `MySql84Upgrade070Test.java` (1), `upgrade/siger-0.7.0-sintetico.sql`, `MySql84UpgradeTest.java`, `MySql84IntegrationTest.java` y `FormularioServiceTest.java` (esquema 17), `InstitutionalReaderTest.java` (barrido), `CLAUDE.md`, `docs/ARQUITECTURA.md`.
- Migraciones: V17 (`stat_catalog_version`, `stat_catalog_item`, `stat_catalog_mapping`; esquema 17), sin siembra.
- Verificacion: JUnit H2 202 ok, 0 omitidas (antes 196); MySQL 8.4.13 (`MySqlSuite` + `MySql84IntegrationTest` + `MySql84UpgradeTest` 0.4.0 -> V17 + `MySql84Upgrade070Test` 0.7.0 -> V17) 206 ok, 0 omitidas (antes 199); frontend no aplica (sin cambios en `frontend/`); `Check-Scripts.ps1` no aplica; humo no aplica (sin interfaz). MySQL con el procedimiento de E00 (`D:\Herramientas\mysql-8.4` solo lectura, datadir en el scratchpad, `127.0.0.1:3417` libre antes, usuario desechable y esquemas vacios con `--init-file`; detenido con `mysqladmin shutdown`, puerto libre, datadir y `surefire-reports` borrados; `MySQL80` intacto). La corrida H2 compilo antes de quitar una variable sin uso en `importMapping` (sin efecto en el comportamiento); la de MySQL corrio sobre el codigo final.
- Evidencia de mutacion (temporal y restaurada; `git diff -- backend/src/main` solo con el gate): no retirar la VIGENTE al activar -> `StatCatalogsTest.importarActivarRetirarYReimportar:117` (violacion de `ux_stat_catalog_current`); sin la validacion de solapes -> `tramosDeEdadContiguosDeCeroACientoTreinta`; sin la de padre inexistente -> `importacionInvalidaSinEscrituras`; sin `Problem.denied()` para no TI -> `soloTiYTiSinExpedientes` y los dos barridos de `InstitutionalReaderTest`; publicar las PROPUESTA -> `importarActivarRetirarYReimportar:96` ("una propuesta no se publica"); correspondencia con codigos inexistentes -> `correspondenciasEntreVersiones`; rutina de arranque que altera una fila de `external_field` -> `MySql84Upgrade070Test` ("originales del Formulario y external_* identicos").
- Condiciones de la seccion 2: `git diff v0.7.0` vacio para los 8 archivos del Formulario (incluido `IntakeCommands.java`), `Reports.java`, `RequirementCommands.java`, `scripts/`, `.mvn/` y V1-V16; ningun codigo escribe `communication_original` ni `external_*`; TI sigue sin fichas ni reportes (D2, en `StatCatalogsTest`); sin dependencias nuevas.
- Facilidad de uso: no aplica (servidor); los rechazos indican el item y el problema en lenguaje llano para la pantalla de E02.
- Definiciones usadas: ninguna (DI-01 a DI-04 se necesitan en la activacion).
- Desvios del plan: `CURRENT_CATALOG` es palabra reservada en H2 2.x: la columna conserva el nombre aprobado, siempre entre acentos graves (H2 `MODE=MySQL` y MySQL los aceptan). La semilla demo de 0.7.0 ya trae REG-DEMO en la region 05: al pasarla a 06, el generador confirmo `regiones_sin_sede` ["05"], como exige la regla de D19.
- Diferencias de referencia: DISENO 3.1: columnas `revision`, `activation_basis`, `retired_by`, `current_catalog` (UNIQUE), `stat_catalog_item.source_ref` y `stat_catalog_mapping.id`, `loaded_by`, `loaded_ms`, `basis`; lectura de TI `GET /api/stat-catalogs` y `/{id}`. DISENO 9: accion `CATALOGO_CORRESPONDENCIA_IMPORTADA`. TRAMO_EDAD: cobertura exacta y contigua 0-130 (por ejemplo, 18-130 como tramo final).
- Hallazgos para gates siguientes: formato `siger-catalogo-1` (`format`, `catalog`, `label`, `source`, `items[]` con `code`, `label`, `parent`, `selectable`, `range_from`, `range_to`, `source_ref`) y `siger-correspondencia-1` (`format`, `pairs[]` con `from` y `to`) para `deploy/catalogos/` (E02); el archivo viaja como texto en `content` (hasta 400.000 caracteres); `StatCatalogsTest.file`/`mapping` arman catalogos en pruebas; `StatCatalogs.published()` entrega la vigente por catalogo (E05-E08); un comando nuevo debe agregarse al barrido de `InstitutionalReaderTest`.
- Pendientes: ninguno de E01.
- Verificacion manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=StatCatalogsTest`; con el MySQL aislado, `-Dtest=MySql84Upgrade070Test` con las propiedades `siger.mysql.*`.

### E02 - Administracion: catalogos de referencia, interfaz de TI y facultad GESTION_ESTADISTICA   (CERRADO)
- Fecha: 2026-11-24 (commit `gate E02: administracion de catalogos y gestion estadistica`), DDN-TEST-SIGER (Windows), Claude Code; base `835c9e4` (E01).
- GO del responsable: plantillas en `plantillas/` con `items: []` (solo se rechazan por no tener items, sin escrituras); empaquetado (b) `deploy/config-ejemplo/catalogos/` tras dos comprobaciones de solo lectura (cumplidas: `Build.ps1:83` copia `config-ejemplo` con `-Recurse` y `SHA256SUMS.txt` recorre todo el paquete; ningun script de `scripts/` trata `config-ejemplo` como configuracion); derechos: ejes no seleccionables, derechos y subcategorias seleccionables, VII no seleccionable, sin VIII ni dimensiones (anotadas en `LEEME.txt`), rotulos de la Tabla 2 con sus diferencias, PDF fuera del repositorio; H2 de base primero; Formulario en `6f886eb` con clon temporal; MySQL no aplica; rol sin efecto como desvio; siete mutaciones; gate completo.
- Hecho: (1) `GESTION_ESTADISTICA` en `Roles` (OPCIONAL, grupo REPORTES, en `NEEDS_REPORTES` con orden estable) y `Roles.ruling(actor,rol)` (concesiones con REPORTES en el mismo alcance); `Personnel`: mensaje `STATS_REQUIRES` y GRANT_REVOKE recorre `NEEDS_REPORTES`; sesion `can_manage_stats`; `Bootstrap` sin cambios (regla generica). (2) `deploy/config-ejemplo/catalogos/`: TIPO_SOLICITUD (5), TIPO_REQUIRENTE (4), VIA_INGRESO (9, `source_ref` = canal), TIPO_ACTUACION (11), TIPO_INSTITUCION (5) y RELACION (9) version 1 (`source_ref` = valor exacto del Formulario, confirmado en `6f886eb`: `ValidacionAdultos.java` lineas 52 y 63), `derechos-2022-v2.json` (87 items: ejes 3-6 y seccion 7 no seleccionables, 17 derechos y 60 subcategorias seleccionables, VII con 5 no seleccionables; los 87 rotulos comprobados como texto literal de la Tabla 2 del PDF, SHA-256 `4a21a6fe...2eb25e`, 96 pags.), `plantillas/` (CONTEXTO, CARACTERIZACION, TRAMO_EDAD) y `LEEME.txt` (fuentes, 11 diferencias de rotulo con las tablas x.y.2 y la seccion, numeracion impresa erronea, VIII pags. 92-95 y dimensiones sujetas a DI-04, huellas). (3) Pantalla `#catalogos` de TI (`StatCatalogs.tsx`, `statCatalogsModel.ts`): importar con resumen del archivo, versiones por catalogo, ver items en arbol, activar con fundamento y correspondencias; menu "Catalogos estadisticos"; perfil con la columna Gestion estadistica.
- Archivos clave: `Roles.java`, `Personnel.java`, `Api.java`, `deploy/config-ejemplo/catalogos/*`, `deploy/config-ejemplo/LEEME.txt`, `frontend/src/features/admin/{StatCatalogs.tsx,statCatalogsModel.ts,profile.ts,ProfileDialog.tsx}`, `app/menu.ts`, `App.tsx`, `lib/labels.ts`, `ReferenceCatalogsTest.java` (4), `StatManagementRoleTest.java` (4), `InstitutionalStaffTest.java`, `LoginPasswordTest.java`, `tests/statCatalogs.test.ts` (6), `statCatalogs-render.test.mjs` (2), `menu.test.ts`, `profile.test.ts`, `CLAUDE.md`, `docs/ARQUITECTURA.md`.
- Migraciones: no aplica (esquema 17).
- Verificacion: evidencia H2 del commit final de E01: suite sobre `835c9e4` antes de editar, 202 ok, 0 omitidas. E02: JUnit H2 210 ok, 0 omitidas (antes 202; `clean package`); MySQL no aplica (sin SQL nuevo; la importacion ya se probo sobre MySQL en E01); frontend typecheck ok, 123 ok (antes 113), build ok; `Check-Scripts.ps1` (powershell.exe 5.1 sin el `PSModulePath` de PowerShell 7): 40 scripts, 73 ordenes en 60 procesos (la primera corrida, con Maven en paralelo, se detuvo por un error transitorio de E/S de `File.Replace` en un temporal; la segunda, completa y conforme); humo 21/21 con el JAR (perfil local, datos en `backend/target/humo-e02`, puerto 18643, borrados al terminar): TI importa los 7 archivos como PROPUESTA con las huellas de `LEEME.txt`, la plantilla se rechaza, activa derechos y `/api/catalog` publica solo esa version, el JAR sirve la pantalla, PROFESIONAL recibe 403.
- Evidencia de mutacion (temporal y revertida, sin `.orig`): M1 `NEEDS_REPORTES` sin la funcion -> `StatManagementRoleTest` (3 pruebas) e `InstitutionalStaffTest.funcionesInvalidas...:114`; M2 GRANT_REVOKE con la lista escrita a mano -> `StatManagementRoleTest.exigeReportesEnElMismoAlcance:69`; M3 `can_manage_stats` sin REPORTES -> `LoginPasswordTest.exportaciones...:243`; M4 codigo repetido en `tipo-solicitud-v1.json` -> `ReferenceCatalogsTest.cadaArchivo...:70`; M5 `NEEDS_REPORTES` del frontend sin la funcion -> `profile.test.ts` (Gestion estadistica); M6 menu tambien con REPORTES -> `menu.test.ts` (2 pruebas); M7 el resumen acepta un archivo sin items -> `statCatalogs.test.ts` (rechazos legibles).
- Condiciones de la seccion 2: `git diff v0.7.0` vacio para los 8 archivos del Formulario (incluido `IntakeCommands.java`), `Reports.java`, `RequirementCommands.java`, `scripts/` y `.mvn/`; `db/migration` sin cambios respecto de `835c9e4`; sin SQL nuevo; sin escrituras en `communication_original` ni `external_*`; TI sigue sin expedientes ni reportes; sin dependencias nuevas.
- Facilidad de uso: TI elige el archivo y ve en lenguaje llano catalogo, nombre, fuente e items antes de confirmar; los rechazos (plantilla, correspondencia, formato) explican que hacer; estados con texto y color; sin codigos internos de catalogo a la vista (render SSR); tablas con vista de tarjetas en movil.
- Definiciones usadas: ninguna (DI-01 a DI-04 se necesitan en la activacion).
- Desvios del plan: (1) GESTION_ESTADISTICA se concede desde E02 pero no habilita nada hasta E10 y E11 (contra la regla de `Roles` de publicar solo roles con efecto; aceptado en el GO). (2) `Check-Scripts.ps1` corrio dos veces (ver Verificacion).
- Diferencias de referencia: el plan (E02, seccion 4) y el DISENO (3.1 y 11) nombran `deploy/catalogos/`; la ruta real es `deploy/config-ejemplo/catalogos/` (opcion (b) del GO), para que los archivos viajen en el paquete y los cubra `SHA256SUMS.txt`. Las subcategorias del modelo 2.0 no tienen numeracion propia: los codigos `3.1.01...` son de SIGER (orden de la Tabla 2).
- Hallazgos para gates siguientes: E05 deriva la via del canal: `FORMULARIO_WEB_PUBLICO` -> `FORMULARIO_PUBLICO_ADULTO`, `FORMULARIO_WEB_NNA` -> `FORMULARIO_PUBLICO_NNA`, el resto con el mismo codigo (`source_ref` "canal X"); E10 y E11 usan `Roles.ruling(a,Roles.GESTION_ESTADISTICA)`; el tipo `User` del frontend (`lib/access.ts`) aun no declara `can_manage_stats`; `ActionDialog.review.extra` sirve para enviar un archivo como texto sin un campo de archivo; cambiar un archivo de referencia exige actualizar su huella en `LEEME.txt` (`ReferenceCatalogsTest`).
- Pendientes: verificacion de la transcripcion de derechos contra el PDF -> responsable, antes de activar (paso U); DI-01 a DI-04 -> activacion.
- Verificacion manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=ReferenceCatalogsTest,StatManagementRoleTest`; `cd frontend; node --test tests/statCatalogs.test.ts`; en local, Administracion > Catalogos estadisticos > Importar catalogo con `deploy\config-ejemplo\catalogos\derechos-2022-v2.json`.

### E03 - Normalizacion integral (servidor): persona canonica, ficha 1.2 y ficha de ingreso de la COM manual   (CERRADO)
- Fecha: 2026-11-24 (commit `gate E03: normalizacion, servidor`), DDN-TEST-SIGER (Windows), Claude Code; base `9f38b87` (E02 `05b0938` y documentos DI-01 a DI-04).
- GO del responsable: formato anterior de `COM_CREATE`/`COM_IMMEDIATE` (K) como transicion, una sola fuente por COM y todos los lectores de `communication_person` tomando la ficha de ingreso; `legacy_document` y `legacy_nationality`; `relationship_version`/`type_version` derivados y rechazados si los envia el cliente; codigos solo del catalogo VIGENTE, sin valores fijos y con la regla "kept"; endpoint de versiones de la ficha de ingreso en `Api.java`; presentador opcional (ingreso institucional); igualdad de la region en Java, SQL y `Access.suggestedRegion` tambien en MySQL; auditoria sin valores con prueba; division con punto de control; diez mutaciones; tratos confirmados en `6f886eb` (clon temporal de solo lectura, borrado).
- Hecho: (1) E03a: `DeclaredPerson` (persona-1, reglas de 1.1 extraidas de `Attention.adult` sin cambio de mensajes, codigos del catalogo vigente, hechos, instituciones y resumen de cambios COMPLEMENTO/RECTIFICACION sobre la vista canonica); ficha 1.2 en `Attention` (reglas en ambas modalidades, personas canonicas, historicos de 1.0/1.1 como `legacy_*` sin dividir ni interpretar, sin volver a una version anterior); `attention_revision.change_summary` en vivo y carga de lo existente (`RESUMEN_CAMBIOS_V18`, desde `Bootstrap`). Punto de control: E03b cabia con todas sus verificaciones; no se dividio. (2) E03b: `IntakeRecord` (ficha de ingreso, columnas de resumen derivadas, `COM_COMPLETE` con motivo y cadena abierta, vista, personas para la ficha de la COM y `GET /api/intake/{id}/versions/{version}`); `PersonIndex` lee la ficha 1.2 y la de ingreso (sin OTRA_PERSONA); `OriginRegion` agrega la ficha de ingreso en `registeredSql`, `currentSql` y `FORM_FIELDS`. Inventario de lectores de `communication_person`: solo `PersonIndex.references` y `Queries.detail("COM")`; ambos toman la ficha de ingreso cuando existe.
- Archivos clave: `V18__normalizacion_captura.sql`, `DeclaredPerson.java`, `IntakeRecord.java`, `Attention.java`, `IntakeCommands.java` (sin `conserveExternal`), `PersonIndex.java`, `OriginRegion.java`, `Queries.java`, `Workflow.java`, `Api.java`, `Bootstrap.java`; `Attention12Test` (7), `IntakeRecordTest` (7), `InstitutionalReaderTest` (+1 y `COM_COMPLETE` en el barrido), `MySql84Upgrade070Test`, `OriginRegionTest` (nota con conteos), pruebas de esquema 18; `CLAUDE.md`, `docs/ARQUITECTURA.md`.
- Migraciones: V18 (`attention_revision.change_summary`, `intake_record`, `intake_revision`; esquema 18), sin siembra.
- Verificacion: JUnit H2 225 ok, 0 omitidas (antes 210; `clean test`); MySQL 8.4.13 (`MySqlSuite` + `MySql84IntegrationTest` + `MySql84UpgradeTest` 0.4.0 -> V18 + `MySql84Upgrade070Test` 0.7.0 -> V18) 229 ok, 0 omitidas: `communication_original` y `external_*` identicos, solo `application_marker` cambia sus filas (+`RESUMEN_CAMBIOS_V18`), resumen calculado en toda version con anterior y ninguna ficha de ingreso para COM anteriores; frontend y `Check-Scripts.ps1` no aplican; humo no aplica (sin interfaz). MySQL con el procedimiento de E00 (`D:\Herramientas\mysql-8.4` solo lectura, datadir en el scratchpad, `127.0.0.1:3417` libre antes, usuario desechable y esquemas con `--init-file`; `mysqladmin shutdown`, puerto libre, sin procesos propios, datadir y `surefire-reports` borrados; `MySQL80` intacto).
- Evidencia de mutacion (temporal y restaurada, sin respaldos): M1 sin validar el DV del RUN -> `Attention12Test.escenario11...:117`, `IntakeRecordTest.escenario11...:108`, `AdultAttentionTest.datosInvalidos...:112`; M2 sin "kept" -> `Attention12Test.escenario11...:131`, `historico...:88`, `indice...:216`; M3 `legacy_*` nuevo aceptado -> `historico...:97`, `IntakeRecordTest.escenario11...:108`; M4 completar como rectificacion -> `completarNoEsCorregir...:168`, `historico...:106`, `IntakeRecordTest.completarNoEsCorregir...:129`; M5 indexar OTRA_PERSONA -> `indice...:209`, `IntakeRecordTest.unicaFuente...:64`; M6 region vigente del ingreso por la primera version -> `IntakeRecordTest.completarNoEsCorregir...:124`, `regionDeLaComManual...:189`; M7 `COM_COMPLETE` con la cadena cerrada -> `soloConLaCadenaAbierta:163`; M8 texto libre como region en NNA 1.2 -> `reglasDeLaPersonaAdulta...:69`; M9 `relationship_code` sin catalogo vigente -> `codigosSoloDelCatalogoVigente...:137`; M10 `Bootstrap` sin la carga del resumen -> `cargaHistorica...:183`.
- Condiciones de la seccion 2: `git diff v0.7.0` vacio para los 7 archivos del Formulario, `scripts/`, `.mvn/`, `Reports.java` y `RequirementCommands.java`; `conserveExternal` identico al de `v0.7.0` (42 lineas, sin CR); V1-V17 sin cambios; `Access.java`, `StatCatalogs.java` y `AdultIdentity.java` sin cambios; ningun codigo escribe `communication_original` ni `external_*`; TI 403 en `COM_COMPLETE` y en la version de la ficha de ingreso; auditoria sin RUN, correo ni nombres (pruebas con datos sinteticos); sin dependencias nuevas.
- Facilidad de uso: no aplica (servidor); los rechazos nombran la persona o institucion ("Persona 2: ...", "Quien presenta: ...", "Institucion 1: ...") y el problema en lenguaje llano, para la pantalla de E04.
- Definiciones usadas: ninguna (DI-08 rige en E04; DI-01 a DI-04 en la activacion).
- Desvios del plan: `IntakeCommands.java` conserva el formato anterior sin `intake` (decision K del GO, transitoria); `Api.java` (endpoint de versiones) autorizado en el GO; los rechazos de estructura (`require`) responden 409, como los de la ficha 1.1.
- Diferencias de referencia: DISENO 2.4: `persona-1` agrega `legacy_document` y `legacy_nationality`, y los derivados `relationship_version` (persona y ficha) y `type_version` (institucion). DISENO 3.4/3.5: las instituciones usan la clave `institutions_list` en la ficha 1.2 y en la de ingreso (la ficha ya tenia `institutions` como texto) y agregan `role` (texto libre, como `rol` del Formulario, sin catalogo); en la ficha de ingreso el presentador es opcional y su `data_json` lleva `contact_restrictions`; la ficha 1.2 aplica tambien en NNA las reglas de correo, celular y nacionalidad (matriz 2.2). La regla "kept" de las personas compara con cualquier persona de la version anterior (por posicion, quitar una persona impediria conservar los historicos de las siguientes).
- Hallazgos para gates siguientes: E04 envia la ficha 1.2 (`schema_version` "1.2") y la COM manual con `intake` (`presenter`, `persons`, `contact_restrictions`, `facts_*`, `institutions_list`), quitando `relationship_version`/`type_version` antes de reenviar lo guardado; convierte las personas 1.0/1.1 con `DeclaredPerson.FROM_11` (sin dividir); `age_years` viaja como texto; `COM_COMPLETE` usa la revision de la COM; la ficha de la COM trae `intake` (`data`, `versions` con `change_summary`, `can_complete`); `GET /api/intake/{id}/versions/{version}`. E07 lee edad (`age_years`), nacionalidad, region del NNA y hechos de `people[]`/`persons[]` en vista canonica (`DeclaredPerson.canonicalList`).
- Pendientes: retirar el formato anterior de `COM_CREATE`/`COM_IMMEDIATE` (rechazo con 400 sin `intake`) -> E05, despues de que E04 cambie la pantalla, de modo que el camino antiguo nunca llegue a produccion.
- Verificacion manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=Attention12Test,IntakeRecordTest`; con el MySQL aislado, `-Dtest=MySql84Upgrade070Test` con las propiedades `siger.mysql.*`.

### E04 - Normalizacion integral (interfaz): componentes compartidos, ficha 1.2 y COM manual   (CERRADO)
- Fecha: 2026-11-24 (commit `gate E04: normalizacion, interfaz`), DDN-TEST-SIGER (Windows), Claude Code; base `0aa85d2` (E03).
- GO del responsable: en `adult-render.test.mjs` cambian solo las tres expectativas afectadas por DI-08 y D33, reemplazadas (no borradas) y registradas como desvio; `adult.test.ts` intacto; `territorio.json` y `paises.json` quedan en `features/attention/adult/` (huellas de E00 sin cambios); codigo de un listado retirado = texto de relacion existente y debajo "Valor registrado con un listado anterior", sin el codigo; frontend completo; H2 no aplica (`git diff -- backend` vacio); humo con el JAR; `Check-Scripts.ps1` una vez al final; tres comprobaciones de facilidad de uso; nueve mutaciones; division autorizada solo si no cabia (no se dividio).
- Hecho: (1) `lib/person/` (`identity.ts` y `catalog.ts` movidos con `git mv`; `personModel.ts`: persona-1, equivalencia `FROM_11` sin dividir nombres, reglas del servidor con "kept", codigos solo del listado VIGENTE de `stat_catalogs`, limpieza de `relationship_version`/`type_version`, rotulos sin codigos y resumen de cambios en cantidades) y `components/person/` (`Controls.tsx` movido; `PersonCard`/`PersonFields` con "Más datos"; `InstitutionList`; `FactsFields`; `CodeSelect`); los modulos de `adult/` reexportan. (2) Ficha 1.2 (`fichaModel.ts`, `schema.ts`, `FormSections`, `AdultSections`, `Attention`, `Preview`, `AttentionSummary`): el borrador pasa a 1.2; la modalidad NNA usa los controles estructurados con los datos principales de antes a la vista; el texto libre NNA anterior queda como "registrado anteriormente" sin convertirse; relacion con el NNA del listado vigente, cantidad entera, lugar de los hechos e instituciones; rotulo "Región y comuna del requirente" (DI-08); versiones con su resumen. (3) Ingreso manual (`intakeModel.ts`, `IntakeFields.tsx`, `Intake.tsx`): `COM_CREATE` solo con el bloque `intake`; quien presenta = persona o institucion (oficio, sin persona natural). (4) `ComDetail` con `IntakeRecordPanel` (datos, versiones, `IntakePreview` y `COM_COMPLETE` con motivo y la misma clave al reintentar).
- Archivos clave: `frontend/src/lib/person/*`, `frontend/src/components/person/*`, `features/attention/{fichaModel.ts,schema.ts,FormSections.tsx,Attention.tsx,Preview.tsx,AttentionSummary.tsx}`, `features/attention/adult/*`, `features/intake/*`, `features/detail/ComDetail.tsx`, `styles/components.css`, `tests/{person,ficha12,intake}.test.ts`, `tests/person-render.test.mjs`, `tests/adult-render.test.mjs`, `CLAUDE.md`, `docs/ARQUITECTURA.md`.
- Migraciones: no aplica (esquema 18).
- Verificacion: JUnit H2 no aplica (`git diff HEAD -- backend` vacio y sin archivos nuevos; base 225 de E03); MySQL no aplica (sin SQL); frontend typecheck ok, 152 ok (antes 123: +9 persona, +5 ficha 1.2, +6 ingreso, +9 render), build ok; `Check-Scripts.ps1` (powershell.exe 5.1 sin el `PSModulePath` de PowerShell 7, al final y sin Maven ni npm): 40 scripts, 73 ordenes en 60 procesos; humo 25/25 con el JAR (`package -DskipTests` solo para el humo; perfil local, datos en `backend/target/humo-e04`, puerto 18643 libre antes; corrida final sobre datos nuevos tras las de ajuste del script; instancia detenida y datos borrados): TI importa y activa RELACION y TIPO_INSTITUCION de `deploy/config-ejemplo/catalogos/`; con los modelos reales de la interfaz, `ATTENTION_CREATE` 1.2 GENERAL (codigos, personas, hechos, instituciones) y NNA, `ATTENTION_SAVE` de lo guardado preparado por la interfaz (el reenvio crudo con `*_version` da 409, control), resumen "1 dato complementado.", region NNA nueva en texto libre rechazada igual que en la interfaz, `COM_CREATE` minimo, institucional y con persona (columnas de resumen derivadas), `COM_COMPLETE` ("2 datos complementados."), version anterior legible y el JAR sirve la interfaz con los textos nuevos.
- Evidencia de mutacion (temporal y restaurada; `git status` identico antes y despues): M1 RUN sin digito verificador -> `person.test` (reglas del Formulario) e `intake.test` (reglas de persona-1); M2 sin "kept" -> `person.test` (valor identico, codigos), `ficha12.test` (1.0 a 1.2), `intake.test` (complementar); M3 legacy nuevo aceptado -> `person.test` (1.0/1.1 a persona-1), `intake.test` (complementar); M4 edad NNA hasta 130 -> `person.test` (edad), `ficha12.test` (validacion); M5 region NNA en texto convertida -> `ficha12.test` (NNA 1.1), `person-render` (NNA 1.1 guardada); M6 sin quitar `relationship_version` -> `ficha12.test` (reenvio); M7 `intake` con campos sueltos -> `intake.test` (ingreso minimo); M8 relacion sin listado vigente -> `person-render` (relacion); M9 rotulo DI-08 revertido -> `adult-render` (3 pruebas) y `person-render` (NNA 1.2).
- Condiciones de la seccion 2: `git diff v0.7.0` vacio para los 7 archivos del Formulario, `scripts/`, `.mvn/`, `Reports.java` y `RequirementCommands.java`; migraciones: solo V17 y V18 respecto de `v0.7.0`; `backend/` sin cambios respecto de `0aa85d2` (incluido `IntakeCommands.conserveExternal`); version 0.7.0 sin cambios; sin dependencias nuevas; humo solo con datos sinteticos y sin claves en la salida.
- Facilidad de uso: (1) el ingreso manual se completa con lo minimo de hoy: el render solo marca obligatorios Ambito, Fecha y hora de recepcion, Asunto y Relato, y el humo registra `COM_CREATE` con asunto y relato; (2) un ingreso institucional se registra sin datos de persona natural (render sin campos de persona; humo con presentador `{}`); (3) la ficha NNA muestra a la vista nombres, identificacion, nacionalidad, relacion y vinculo, domicilio, nombre de preferencia, trato y apoyos, y en "Más datos" segundo nombre, segundo apellido y fecha de nacimiento (render). Sin codigos visibles (render con la lista de codigos), ayuda en lenguaje llano, estados con texto, tarjetas a una columna en movil (`.form-grid` existente) y `prefers-reduced-motion` en "Más datos".
- Definiciones usadas: DI-08 (rotulo "Región y comuna del requirente", sin modificar D21).
- Desvios del plan: `adult-render.test.mjs` no queda sin cambios (aprobado en el GO; es el cambio que buscan D33 y DI-08): la lista de rotulos de la primera prueba reemplaza "Región de atención" y "Comuna de atención" por "Región y comuna del requirente"; la prueba de orden toma su tramo desde ese rotulo hasta "Selecciona primero una región"; la prueba "modalidad NNA conserva sus controles de texto libre" pasa a "modalidad NNA usa los controles estructurados (ficha 1.2, D33)". El resto del archivo y `adult.test.ts` quedan intactos.
- Diferencias de referencia: DISENO 2.7: los catalogos `territorio.json` y `paises.json` siguen en `features/attention/adult/` y `lib/person/catalog.ts` los importa desde ahi (GO). El par region y comuna es un grupo con leyenda ("Región y comuna del requirente", "... de los hechos", "... de la persona", "... de la institución") y campos "Región" y "Comuna". El rechazo de un campo no reconocido en la ficha responde 409 (`require`), no 400.
- Hallazgos para gates siguientes: tras E04 ninguna pantalla envia el formato anterior de `COM_CREATE` (unico emisor `Intake.tsx`, con `comCreatePayload`; `tests/intake.test.ts` lo barre) y ninguna pantalla envia `COM_IMMEDIATE`: E05 puede retirarlo de ambos con 400. E09 agrega el bloque de caracterizacion a `Intake.tsx` y a `IntakeRecordPanel` reutilizando `components/person/` y `statCodes`; `RETIRED_CODE_TEXT` es el unico texto para un codigo de un listado retirado.
- Pendientes: rotulo del codigo segun la version con que se registro (relacion y tipo de institucion de un listado retirado) -> E06, que modifica `Queries`: con ese rotulo la interfaz lo muestra sin cambios adicionales; retirar el formato anterior de `COM_CREATE`/`COM_IMMEDIATE` -> E05; DI-01 a DI-04 -> activacion.
- Verificacion manual sugerida al usuario: `cd frontend; node --test tests/person.test.ts tests/ficha12.test.ts tests/intake.test.ts tests/person-render.test.mjs`; en local, Nueva atencion en modalidad NNA y Comunicaciones > Ingreso por clasificar con "Una institución", y luego "Completar o rectificar" en la ficha de la COM.

### E05 - Clasificacion estadistica de la COM: modelo, via, propuestas y trazabilidad (servidor)   (CERRADO)
- Fecha: 2026-11-25 (commit `gate E05: clasificacion de la COM`), DDN-TEST-SIGER (Windows), Claude Code; base `a404ee6` (E04).
- GO del responsable: A via calculada sin evento (Java = SQL vigente y a una fecha, codigos de `via-ingreso-v1.json`, diferencia con DISENO 3.2); B revision = eventos de la COM, con prueba de dos clasificaciones concurrentes; C codigos y estados especiales solo con catalogo VIGENTE, y COM_CREATE sin `stat` sin catalogos; D adaptar las llamadas sin `intake` (lectura historica por SQL, sin quitar aserciones); E motivo solo para quien lee la COM o el registro del REQ, nunca en auditoria ni logs; F punto de control E05a/E05b; valores del Formulario confirmados en `6f886eb`; COM del Formulario anteriores calculadas sin escribir, tambien desde el volcado 0.7.0; diez mutaciones.
- Hecho: (1) V19 `stat_event` (solo inserciones) y `StatValues`: regla unica Java/SQL del valor vigente, a una fecha y del conjunto (multiples para E06); via derivada del canal (`CHANNEL_VIA`) calculada sin escribir, INFORMADO/SISTEMA solo con item seleccionable del catalogo VIA_INGRESO vigente (a una fecha, el activo en t y desde `created_ms`); `STAT_CLASSIFY` (revision = eventos de la COM; solo `{stat}`, otro campo 400) y bloque `stat` en COM_CREATE, COM_IMMEDIATE, ATTENTION_CREATE, COM_ROUTE, COM_ASSUME, SOL_ASSUME y SOL_DISCARD; motivo obligatorio al corregir un INFORMADO (incluida la via); origen RETROSPECTIVA con la ultima reapertura ACEPTADA si el REQ abierto ya tuvo un cierre (`closure_record`), sin tocar `REOPEN_*`; inmutabilidad 409 (descartada, vinculada, REQ cerrado) antes de la facultad; auditoria solo con nombres de variables. (2) `StatProposals` (`PROPUESTA_V1`): `motivo` consulta/vulneracion/promocion y `organizacion` si/no confirmados en formulario-web `6f886eb` (clon temporal de solo lectura, borrado), igualdad exacta en Java y `sqlCode` equivalente. (3) `Access.statWritable`. (4) Canales `REDES_SOCIALES` e `INTEGRANTE_DEFENSORIA` (servidor, `fields.ts`, `labels.ts`). (5) Formato anterior de COM_CREATE/COM_IMMEDIATE retirado (400 "Falta la ficha de ingreso."; `persons()` retirado). (6) `characterization` en las fichas de COM y REQ. Punto de control: E05b cupo con todas sus verificaciones; no se dividio.
- Archivos clave: `V19__clasificacion_estadistica.sql`, `StatValues.java`, `StatProposals.java`, `Access.java`, `IntakeCommands.java` (sin `conserveExternal`), `Attention.java` (solo `create`), `Lifecycle.java` (canales), `Workflow.java`, `Queries.java`, `frontend/src/lib/{fields,labels}.ts`; `StatClassificationTest` (10), `LegacyCom.java` (apoyo de pruebas), `MySql84Upgrade070Test` (paso 3), `InstitutionalReaderTest` (barrido), `frontend/tests/channels.test.ts` (2); `CLAUDE.md`, `docs/ARQUITECTURA.md`.
- Migraciones: V19 (`stat_event` con indices por COM y por registro; esquema 19), sin escritura de eventos.
- Verificacion: JUnit H2 235 ok, 0 omitidas (antes 225; `clean test`); MySQL 8.4.13 (suite completa con `siger.mysql.suite=true`: `MySqlSuite` + `MySql84IntegrationTest` + `MySql84UpgradeTest` 0.4.0 -> V19 + `MySql84Upgrade070Test` 0.7.0 -> V19) 239 ok, 0 omitidas (antes 229; `StatClassificationTest` en `siger_a_mysql_sigera_clasificacion`); frontend typecheck ok, 154 ok (antes 152), build ok; `Check-Scripts.ps1` (powershell.exe 5.1 sin el `PSModulePath` de PowerShell 7, al final y sin Maven ni npm): 40 scripts, 73 ordenes en 60 procesos; humo no aplica (sin interfaz nueva). MySQL con el procedimiento de E00 (`D:\Herramientas\mysql-8.4` solo lectura, datadir en el scratchpad, `127.0.0.1:3417` libre antes, usuario desechable y esquemas con `--init-file`; `mysqladmin shutdown`, sin escucha en 3417 (solo TIME_WAIT de las conexiones cerradas), sin procesos propios, datadir y `surefire-reports` borrados; `MySQL80` intacto).
- Pruebas adaptadas (decision D; ninguna asercion eliminada, solo cambia la forma de crear la COM): `"intake",Map.of()` en `AccessResolutionTest`, `CaseDetailFacultiesTest`, `ComBoardMarkTest`, `DistributionNoticeTest`, `ExternalOriginalTest`, `InstitutionalReaderTest`, `InstitutionalStaffTest`, `LoginPasswordTest`, `MilestoneTest`, `OriginRegionTest`, `RegionalSitesTest`, `ReportsTest`, `StatCatalogsTest`, `TerritorialCoverageTest`, `TiAdministrationTest`, `WorkflowTest` y `Version031Test`; lectura historica con `LegacyCom` (COM con columnas libres y `communication_person` por SQL) en `PersonIndexTest`, `PersonSearchTest` e `IntakeRecordTest`; `ExternalComIntegrationTest` registra a quien presenta en la ficha de ingreso (misma asercion "Ana Pérez"). Cambios de forma: `IntakeRecordTest.formatoAnteriorSigueIgualSinFichaDeIngreso` pasa a `formatoAnteriorRetiradoYComAnteriorLegible` (conserva sus 6 aserciones sobre una COM de `LegacyCom` y agrega el 400 de ambos comandos sin escrituras); en `Version031Test` la asercion "1 fila en `communication_person`" pasa a "1 persona en la ficha de ingreso" (`IntakeRecord.persons`), porque por diseno esa tabla ya no recibe filas; esquema 18 -> 19 en `FormularioServiceTest`, `MySql84IntegrationTest` y `MySql84UpgradeTest`.
- Evidencia de mutacion (temporal y restaurada; `git status` identico antes y despues): M1 propuesta como INFORMADO -> `escenario4...:150`, `fichaConCaracterizacion...:418`; M2 sin motivo al corregir -> `clasificarCorregir...:177`, `bloqueStat...:376`; M3 el cliente envia otros campos (origen) -> `clasificarCorregir...:201`; M4 RETROSPECTIVA sin reapertura -> `registroCerrado...:283`; M5 REQ cerrado aceptado -> `registroCerrado...:275`, `fichaConCaracterizacion...:446`; M6 DUPLICADA/INVALIDA aceptada -> `bloqueStat...:406`, `registroCerrado...:301`; M7 lector institucional y TI con escritura -> `facultades...:244` y cuatro de `InstitutionalReaderTest` (barrido y `can_classify` en las fichas); M8 via adulto/NNA cruzada -> `viaDerivada...:109`, `escenario4...:153`, `fichaConCaracterizacion...:421`; M9 SQL por el primer evento -> `mismaReglaEnJavaYEnSql:329`; M10 formato anterior aceptado -> `IntakeRecordTest.formatoAnteriorRetirado...:83`.
- Condiciones de la seccion 2: `git diff v0.7.0` vacio para los 7 archivos del Formulario, `Reports.java`, `RequirementCommands.java` (flujo `REOPEN_*`), `scripts/` y `.mvn/`; `conserveExternal` identico al de `v0.7.0` (41 lineas); V1-V18 sin cambios; ninguna escritura en `communication_original` ni `external_*` (huellas iguales en `StatClassificationTest` y `MySql84Upgrade070Test`); el cliente no fija origen, reapertura ni estado; TI, REPORTES, AUDITORIA, LECTURA y la consulta institucional sin escritura; auditoria sin codigos ni motivo; sin dependencias nuevas.
- Facilidad de uso: no aplica (servidor); los rechazos nombran la variable y el problema en lenguaje llano ("Tipo de solicitud: no hay un listado vigente...", "...: indica el motivo de la corrección.", "La COM fue descartada...", "El REQ está cerrado: la clasificación requiere reapertura formal.") para E09.
- Definiciones usadas: DI-07 (sin modalidades de reapertura ni cambios en `REQ_TOTAL`; trazabilidad RETROSPECTIVA con la reapertura vigente).
- Desvios del plan: ninguno. `LegacyCom.java` es un apoyo de pruebas nuevo (pruebas autorizadas).
- Diferencias de referencia: DISENO 3.2: la via derivada del canal no es un evento SISTEMA sino un valor calculado (sin evento propio; INFORMADO, origen SISTEMA), para cubrir las COM del Formulario y las anteriores sin tocar la recepcion; a una fecha usa la version de VIA_INGRESO activa en ese instante. La revision de `STAT_CLASSIFY` es la cantidad de eventos de la COM (no la de la COM). DISENO 4.3: una COM ATENDIDA con su REQ reabierto admite clasificacion (RETROSPECTIVA).
- Hallazgos para gates siguientes: E06 conserva sin cambio los codigos de via de `via-ingreso-v1.json` al agregar las agrupaciones como items padre no seleccionables (`StatClassificationTest.viaDerivada...` lo exige) y escribe ADD/REMOVE/STATUS de las variables multiples con `StatValues` (`set`, `setSql`, `setStateSql` ya probados contra eventos insertados por SQL); E07/E08 leen el estado con `stateSql`/`codeSql`/`versionSql`/`originSql` y la propuesta con `StatProposals.sqlCode` (compara con la intercalacion de MySQL: igual al Java por el contrato cerrado del Formulario); E09 envia `stat` solo para variables con catalogo vigente, muestra la propuesta como propuesta (`characterization.variables[].proposal`), pide motivo al corregir un INFORMADO (incluida la via derivada) y usa `characterization.revision` en `STAT_CLASSIFY`; una prueba que necesite una COM anterior a 0.8.0 usa `LegacyCom`; un comando nuevo cuya revision no sea la columna de su tabla se agrega al barrido con su propio `rev`.
- Pendientes: DI-01 a DI-04 -> activacion; rotulo del codigo de RELACION/TIPO_INSTITUCION segun la version con que se registro -> E06 (pendiente de E04, sin cambio).
- Verificacion manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=StatClassificationTest,IntakeRecordTest`; con el MySQL aislado, `-Dtest=MySql84Upgrade070Test` con las propiedades `siger.mysql.*`; `cd frontend; node --test tests/channels.test.ts`.

### E06 - Derechos, contexto, caracterizacion y actuaciones tipificadas (servidor)   (CERRADO)
- Fecha: 2026-11-25 (commit `gate E06: derechos y actuaciones`), DDN-TEST-SIGER (Windows), Claude Code; base `ed2fbb4` (E05).
- GO del responsable: plantillas reemplazadas por los archivos completos y frase de `StatCatalogs.tsx` ajustada (A); RETROSPECTIVA tambien en un Caso reabierto; NOTE_RECTIFY con clave ausente conserva tipo e instituciones y un vacio explicito los quita; autorizados `StatCatalogs.java` y `frontend/tests/statCatalogs.test.ts`; PDF descargado a una carpeta temporal (SHA-256 `4a21a6fe...` igual a `LEEME.txt`, borrada al terminar) y 11 denominaciones de la seccion VIII transcritas textuales; via con agrupaciones padre y codigos intactos; una sola fuente del resultado de la primera atencion; regla de "Oficio" en su descripcion; auditoria solo con nombres de variables; sin division; 14 mutaciones (incluida la de la constancia de primera atencion). Durante el gate (2026-11-25): ajuste minimo de `StatClassificationTest.viaDerivada...` (ver Desvios).
- Hecho: (1) V20 y `StatCatalogs`: 12 catalogos (ROL_ESTADO, GRUPO_PRIORITARIO) y campo `descripcion` (hasta 1000, `description`, publicado). (2) `Rights` (nuevo): `RIGHTS_CLASSIFY` de REQ o Caso (ADD/REMOVE/STATUS de las 5 variables multiples; solo seleccionables del VIGENTE; retirado se conserva y se quita, no se agrega; estado solo sin valores; motivo al quitar o reemplazar un estado; revision = eventos del registro; 409 cerrado antes de 403; RETROSPECTIVA con `StatValues.reopenedRecord`, regla unica tambien de STAT_CLASSIFY); fichas REQ y CAS con `rights` y consolidado solo de lo legible. (3) Actuaciones: `Lifecycle.note`/`actionType` (tipo vigente, instituciones con `DeclaredPerson.institutions` en `note_institution`), tareas con compromiso, `requirement.immediate_result` en COM_IMMEDIATE y ATTENTION_RESOLVE (constancia sin tipo) y carga historica `RESULTADO_INMEDIATO_V20` por igualdad exacta en Java con el cierre; fichas con `action_label`, `institutions` y `code_labels` (pendiente de E04). (4) Archivos de referencia segun `VALIDACION_CATALOGOS_0_8_0.md` (12, PROPUESTA) y `LEEME.txt`. (5) `ImmutabilityTest`: barrido D35 de todos los comandos de `Workflow.run`.
- Archivos clave: `V20__derechos_y_actuaciones.sql`, `Rights.java`, `StatValues.java`, `StatCatalogs.java`, `Lifecycle.java`, `CaseCommands.java`, `IntakeCommands.java` (sin `conserveExternal`), `Attention.java`, `Bootstrap.java`, `Workflow.java`, `Queries.java`; `deploy/config-ejemplo/catalogos/*` y `deploy/config-ejemplo/LEEME.txt`; `RightsTest` (6), `ActionTypesTest` (8), `ImmutabilityTest` (1), `ReferenceCatalogsTest` (5), `InstitutionalReaderTest` (barrido), pruebas de esquema 20 y de actualizacion; `frontend/src/features/admin/StatCatalogs.tsx` (una frase), `frontend/tests/statCatalogs.test.ts`; `CLAUDE.md`, `docs/ARQUITECTURA.md`.
- Migraciones: V20 (`stat_catalog_item.description`, `management_note.action_type`/`action_catalog_version`, `note_institution`, `task_item.action_type`/`action_catalog_version`, `requirement.immediate_result`/`immediate_result_reconstructed`; esquema 20), sin siembra.
- Verificacion: JUnit H2 251 ok, 0 omitidas (antes 235; `clean test`); MySQL 8.4.13 (`siger.mysql.suite=true`: `MySqlSuite` + `MySql84IntegrationTest` + `MySql84UpgradeTest` 0.4.0 -> V20 + `MySql84Upgrade070Test` 0.7.0 -> V20) 255 ok, 0 omitidas (antes 239): los 4 resultados de primera atencion de los volcados reconstruidos, `amount` 0, originales y `external_*` identicos; frontend typecheck ok, 154 ok (sin cambio de cantidad: se ajusto una prueba), build ok; `Check-Scripts.ps1` (powershell.exe 5.1 sin el `PSModulePath` de PowerShell 7, al final y sin Maven ni npm): 40 scripts, 73 ordenes en 60 procesos; humo no aplica (sin interfaz nueva). MySQL con el procedimiento de E00 (`D:\Herramientas\mysql-8.4` solo lectura, datadir en el scratchpad, `127.0.0.1:3417` libre antes, usuario desechable; `mysqladmin shutdown`, puerto sin escucha, sin procesos propios, datadir y `surefire-reports` borrados; `MySQL80` intacto).
- Evidencia de mutacion (temporal y restaurada; huellas de `backend/src/main` identicas antes y despues): M1 quitar sin motivo -> `RightsTest.reglas...:172`; M2 estado con valores -> `reglas...:171`; M3 item no seleccionable -> `reglas...:162`; M4 codigo de un listado retirado -> `listadoRetirado...:204`, `reglas...:162`; M5 consolidado sin filtrar por lectura -> `escenario6...:101`; M6 REQ cerrado aceptado -> `registroCerrado...:125` e `ImmutabilityTest:161` (la primera corrida del barrido no lo detecto porque el rechazo venia de la falta de catalogo vigente: el barrido activa ahora los catalogos); M7 sin RETROSPECTIVA en el Caso reabierto -> `casoCerrado...:151`; M8 instituciones de una reservada en la ficha -> `ActionTypesTest.constanciaReservada...:149`; M9 tarea completada como actuacion -> `escenario8...:76`; M10 prefijo no exacto en la carga historica -> `cargaHistorica...:228`; M11 lector institucional con escritura -> `RightsTest.facultades...:222` y tres de `InstitutionalReaderTest` (barrido y `can_classify`); M12 descripcion no publicada -> `ReferenceCatalogsTest.catalogosDeE06...:105`; M13 constancia de primera atencion tipificada (contada como actuacion) -> `resultadoInmediato...:174`; M14 clave ausente que borra el tipo -> `rectificacion...:119`, `tarea...:161`, `constanciaReservada...:145`.
- Condiciones de la seccion 2: `git diff v0.7.0` vacio para los 7 archivos del Formulario, `Reports.java`, `RequirementCommands.java` (incluido `REOPEN_*`), `scripts/` y `.mvn/`; `conserveExternal` identico al de `v0.7.0`; V1-V19 sin cambios; ningun codigo escribe `communication_original` ni `external_*`; clasificacion sin inferencia (solo codigos elegidos); TI, REPORTES, AUDITORIA, LECTURA y la consulta institucional sin escritura; auditoria sin codigos ni motivo; sin dependencias nuevas.
- Facilidad de uso: no aplica (servidor); los rechazos nombran la variable y el problema en lenguaje llano ("Derechos: selecciona una opción del listado vigente.", "...: indica el motivo de la corrección.", "El Caso está cerrado: la clasificación requiere reapertura formal.", "Tipo de actuación: ...") para E09. Unica frase visible cambiada: el aviso de archivos de referencia de `#catalogos`.
- Definiciones usadas: DI-01 a DI-04 (respuesta propuesta del 2026-11-24, contenido de los archivos PROPUESTA); DI-07 (sin modalidades de reapertura).
- Desvios del plan: (1) `StatClassificationTest.viaDerivadaConLosCodigosDelArchivoDeReferencia` (E05) recibio un ajuste minimo autorizado el 2026-11-25: omite los items no seleccionables (agrupaciones, sin canal); sus aserciones sobre las 9 vias y los canales quedan iguales. (2) `Access.java` y `RequirementCommands.java` no se tocaron (la facultad usa `reqMain` y `caseWrite` existentes; REQ_NOTE pasa por `Lifecycle.note`). (3) `ImmutabilityTest` acepta 403 en ATTENTION_SAVE/SEND/RESOLVE y SOL_ROUTE: guardas vigentes desde 0.4.0 que combinan estado y facultad; rechazan sin escribir y no se cambiaron.
- Diferencias de referencia: campo `descripcion` del archivo y columna `description`; `note_institution` agrega `role` y `sort`; `task_item` guarda `action_catalog_version`; VII sale del catalogo de derechos (81 items) a `rol-estado-v1.json`; las 11 denominaciones de la seccion VIII difieren de la lista abreviada de DI-02 (prevalece el PDF; diferencias en `LEEME.txt`).
- Hallazgos para gates siguientes: E07 resuelve los bloqueos de cierre con `StatValues.setStateSql`/`set` por REQ y Caso (STATUS especial con conjunto vacio) y la union REQ+Casos de `Rights.consolidated` (ese consolidado filtra por lectura de la cuenta: los agregados de E08 usan el alcance de REPORTES, no `caseRead`); E08 cuenta actuaciones por `management_note.action_type` de la constancia vigente (sin rectificacion posterior) y el resultado de primera atencion solo desde `requirement.immediate_result` (origen = primera atencion, gestion de REQ si `case_id` nulo, gestion de Caso); una subcategoria implica su derecho en el conteo, no en `stat_event`; E09 usa `rights` (`revision`, `can_classify`, `blocked`, `values`, `consolidated`), `action_label`/`institutions` de las constancias y `code_labels` para un codigo de un listado retirado (reemplaza `RETIRED_CODE_TEXT` cuando hay rotulo); el barrido `ImmutabilityTest` exige categorizar todo comando nuevo.
- Pendientes: DI-01 a DI-04 (conformidad institucional y verificacion de la transcripcion de derechos y de los grupos prioritarios contra el PDF) -> activacion; adopcion de `code_labels` en la interfaz -> E09.
- Verificacion manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=RightsTest,ActionTypesTest,ImmutabilityTest,ReferenceCatalogsTest`; con el MySQL aislado, `-Dtest=MySql84UpgradeTest,MySql84Upgrade070Test` con las propiedades `siger.mysql.*`; en local, Administracion > Catalogos estadisticos > Importar `deploy\config-ejemplo\catalogos\grupos-prioritarios-v1.json`.

### E07 - Dimensiones territoriales y demograficas, completitud y reglas de cierre (servidor)   (CERRADO)
- Fecha: 2026-11-25 (commit `gate E07: dimensiones y completitud`), DDN-TEST-SIGER, Codex (GPT 6.2 Sol). Base `085d7e6`, igual a `origin/main` y limpia antes de editar. Último cierre E06, `8a297ae`.
- GO del responsable: respuestas A–E registradas arriba y confirmadas en esta sesión. Textos históricos de edad sin interpretar aun si son enteros. `Check-Scripts.ps1` con Windows PowerShell 5.1 fuera de Maven y npm. MySQL desechable detenido y eliminado al terminar.
- Hecho: reglas territoriales al ingreso, al constituirse el REQ, vigentes y a una fecha. Sede inicial y vigente con hitos, cobertura con períodos y acciones de auditoría. Datos del Formulario desde `external_*`, con la recepción firmada como límite de disponibilidad histórica.
- Hecho: proyección demográfica declarada por versión, edad cumplida a la recepción en Chile, tramos del catálogo vigente y nacionalidad estructurada. Edades de requirente condicionadas a su tipo informado.
- Hecho: regla de completitud compartida para REQ, primera atención y descarte duplicado. Bloqueos visibles, clasificación en el acto, derechos y contexto del REQ y Casos, requisitos en `/api/catalog` e indicadores por variable, ámbito y período.
- Archivos clave: `TerritorialDims.java`, `PersonDims.java`, `StatCompleteness.java`, `Rights.java`, `RequirementCommands.java`, `Attention.java`, `IntakeCommands.java`, `Queries.java`, `IntakeRecord.java` y `Bootstrap.java`. Pruebas nuevas de territorio, demografía y completitud. Se clasificaron previamente los registros de `ImmutabilityTest` y `StatClassificationTest` que cierran con catálogos vigentes, conservando sus comprobaciones.
- Migraciones: V21 aditiva con `stat_declared_version` y `stat_declared_person`. Carga idempotente `DIMENSIONES_V21` también con `migrate-only`. La prueba compara la proyección con el recálculo desde la ficha, y guarda años y procedencia de la edad.
- Verificación: JUnit H2 259 ok (antes 251). MySQL 8.4.13, 263 ok (antes 255), con los dos volcados de 0.4.0 y 0.7.0 hasta V21 y huellas y filas de originales y `external_*` iguales. Ambas suites con 0 omitidas. Frontend 154 ok, typecheck y build conformes. JAR empaquetado y humo HTTP conforme con catálogo y vulneración en el acto, sin y con derecho y contexto.
- Verificación: `Check-Scripts.ps1` conforme (40 scripts, 73 órdenes en 60 procesos) en Windows PowerShell 5.1, sin Maven ni npm. Al quitar la variable heredada el primer intento dejó vacía la ruta de módulos y falló el control de CimCmdlets. Se repitió con solo rutas nativas de 5.1. MySQL aislado en 127.0.0.1:3417 detenido, puerto libre, carpeta de datos y copia de verificación eliminadas. MySQL80 siguió en ejecución.
- Evidencia de mutación: propuesta como resuelta, bloqueo sin catálogo y omitir bloqueo visible → `StatCompletenessTest.escenario7CierraConEstadosEspecialesYNoConUnaPropuesta`. Omitir derecho u omitir contexto → `resuelveEnElActoConDerechoYContextoYRevierteUnRechazo`. Exigir variables a una inválida → `soloDuplicadaTieneBloqueosDeDescarte`.
- Evidencia de mutación: zona UTC, interpretar edad histórica o aceptar nacionalidad libre histórica → `PersonDimsTest.escenario14CumpleanosEnChileYTextoHistoricoSinInterpretar`. Cambiar la sede inicial tras transferir u omitir término de cobertura → `TerritorialDimsTest.escenario5OrigenCoberturaYSedeConTransferencia`.
- Evidencia de mutación: edad de SQL distinta de Java u omitir reconstrucción V21 → `PersonDimsTest.proyeccionRecalculadaYTramoSql`. Aceptar nacionalidad no exacta u omitir el límite de recepción → `formularioConservaRolesYExigeNacionalidadExacta`. Las 15 mutaciones fallaron en copias temporales eliminadas al terminar.
- Condiciones de la sección 2: `git diff` confirmó integridad de la recepción y `conserveExternal`, scripts, `.mvn/` y V1–V20. `Reports`, `REQ_TOTAL`, `REOPEN_*`, `OriginRegion`, `Access` y `Territory` conservados. Barridos de permisos e inmutabilidad conformes, sin dependencias nuevas ni archivos sensibles en el alcance.
- Facilidad de uso: mensajes HTTP en lenguaje llano, comprobados mediante humo. El diálogo se incorpora en E09.
- Definiciones usadas: ninguna DI pendiente para E07. Tramos del catálogo de referencia aprobado en DI-02 y mecanismo de reapertura según DI-07.
- Desvíos del plan: diferencias acordadas con diseño 4.5 y 4.8. Región del requirente como indicador y sin bloqueo. Los textos históricos de edad quedan sin interpretar incluso si contienen un entero. La región física de una sede se toma del registro actual porque sus cambios no se reconstruyen.
- Hallazgos para gates siguientes: E08 puede consumir las expresiones SQL de dimensiones y los indicadores. El estado consolidado de calidad de derechos y contexto prioriza INFORMADO, luego el estado especial del REQ y después el del primer Caso con uno. E09 dispone de `stat_completeness`, `closure_blockers`, `resolve_blockers` y `discard_blockers`.
- Pendientes: consumo estadístico en E08, captura y diálogos en E09, panel de calidad en E13 y medición de rendimiento en E14.
- Verificación manual sugerida al usuario: en una instancia de ensayo con catálogos vigentes, resolver una vulneración con y sin derecho y contexto. El segundo intento puede completar la clasificación y cerrar en el mismo acto.

### E08 - Motor estadistico: fuente unica, agregacion, series temporales y comparaciones (servidor)   (CERRADO)
- Fecha: 2026-11-25 (commit `gate E08: motor estadistico`), DDN-TEST-SIGER (Windows), Claude Code; base `6a483c7` (cierre de E07 `c9abb9a` más el plan y GO registrados).
- GO del responsable: el plan y las condiciones registrados arriba, más «GO: A(a), B sí, C sí» y la autorización de `Workflow.java` dadas en esta sesión (sección "GO de ejecución").
- Hecho: `StatSource` (fuente única: una sentencia SQL portable con CTE para celdas, márgenes, total, tardíos, versiones, cobertura e historia) y `StatEngine` (`POST /api/stats/query`; validación cerrada, porcentajes, comparaciones, rótulos, avisos y auditoría `STAT_CONSULTA`), con 12 medidas y 63 dimensiones publicadas en `/api/catalog` (`stats`), según `docs/CONTRATO_STATS_0_8_0.md`.
- Hecho: `AL_REQ` corta al fin del comando que constituyó el REQ (`TerritorialDims.constitutedSql`, también en `PersonDims` modo REQ) y `Workflow.afterPreviousCommand` hace imposible el empate de milisegundo (tope 200 ms). Sobrecargas `*At` de `StatValues` sin cambiar el SQL anterior (`StatExpressionsTest`). Definiciones C1-C5 aplicadas; "instituciones de los hechos" en la hoja de ruta 0.9.0 (PLAN 9, DISENO 15.7).
- Archivos clave: `StatSource.java`, `StatEngine.java` (nuevos), `StatValues.java`, `TerritorialDims.java`, `PersonDims.java`, `Workflow.java`, `Api.java`, `Queries.java`, `V22__indices_motor_estadistico.sql`; `StatEngineTest` (33), `StatModesTest` (15), `StatQueryHttpTest` (5), `StatExpressionsTest` (1), `StatFixture`, `src/test/resources/stats/` (poblacion.csv, esperados.csv, expresiones-e07.txt); `docs/CONTRATO_STATS_0_8_0.md`, `CLAUDE.md`, `docs/ARQUITECTURA.md`, PLAN, DISENO.
- Migraciones: V22, cinco índices (`communication.received_ms`, `communication.created_ms`, `requirement.created_ms`, `case_record.created_ms`, `command_log(result_kind,result_id,created_ms)`), con la evidencia de EXPLAIN registrada antes (arriba); esquema 22.
- Verificación: JUnit H2 313 ok, 0 omitidas (antes 259); MySQL 8.4.13 (suite completa con `siger.mysql.suite=true`: `MySqlSuite` + `MySql84IntegrationTest` + `MySql84UpgradeTest` 0.4.0 -> V22 + `MySql84Upgrade070Test` 0.7.0 -> V22) 317 ok, 0 omitidas (antes 263), con `communication_original` y `external_*` idénticos; procedimiento de E00 en `127.0.0.1:3417` (al forzar `internal_tmp_mem_storage_engine=MEMORY` en un diagnóstico el servidor desechable cayó y se reinició), detenido con `mysqladmin shutdown`, puerto sin escucha, sin procesos propios y datadir borrado; `MySQL80` intacto; frontend typecheck ok, 154 ok, build ok; humo 23/23 con el JAR (perfil local, H2 en `backend/target/humo-e08`, puerto 18643, todo borrado al terminar): catálogos de referencia importados y activados por TI, REPORTES concedida, COM manual y atención directa con derechos, cruces, `AL_REQ`, actuaciones, serie con comparación, completitud, 400, 405, 403 sin CSRF, sin REPORTES y TI; `Check-Scripts.ps1` (powershell.exe 5.1 con las rutas nativas de módulos, al final y sin Maven ni npm): 40 scripts, 73 órdenes en 60 procesos.
- Medición de 200 altas de cada tipo (`ATTENTION_CREATE` con ficha y clasificación; `COM_IMMEDIATE` con derechos y contexto): con el corte anterior quedaban fuera, en H2, 85/163/44 y 88/166/43 (ficha/clasificación/derechos) y, en MySQL, 199/200/200 y 198/200/199; con el código final, 0 `PENDIENTE` en ocho dimensiones `AL_REQ` en las dos corridas de cada motor y celdas idénticas (200).
- Rendimiento (MySQL, 50.005 COM sintéticas, Cuenta Pública): la mayoría de las consultas entre 0,2 y 3,5 s; tramo y región del NNA afectado 5-6 s y completitud 8-10 s. Medición formal y decisión en E14 (DISENO 5.7).
- Evidencia de mutación: 92 mutaciones temporales en copias de trabajo, todas fallaron en la prueba prevista (tabla abajo).
- Condiciones de la sección 2: `git diff v0.7.0` vacío para la recepción del Formulario (7 archivos), `Reports.java`, `Territory.java`, `scripts/` y `.mvn/`; `conserveExternal` idéntico (41 líneas); V1-V21 sin cambios; `OriginRegion`, `Access`, `Rights`, `IntakeCommands`, `RequirementCommands` (incluido `REOPEN_*`) y `Attention` sin cambios respecto de `c9abb9a`; ninguna escritura en `communication_original` ni `external_*`; TI 403; sin dependencias nuevas.
- Facilidad de uso: no aplica (servidor); los rechazos explican qué corregir en lenguaje llano (400 por campo y 409 del límite técnico).
- Definiciones usadas: DI-01 a DI-04 (catálogos de referencia en las pruebas); DI-07 (reapertura sin cambios).
- Desvíos del plan: (1) `Workflow.java` fuera de la lista original, autorizado durante el gate. (2) Población: C se registra el 03/11 (no el 08/10) para que la serie por fecha de registro sea distinguible; la cifra por recepción no cambia. (3) "Cumple 10 años el día de recepción" se prueba con quien cumple 11 al día siguiente, porque UTC solo adelanta la fecha.
- Diferencias de referencia: `CREACION_CASO` como fecha de análisis de `CASOS`; `SIN_DESGLOSE` (derecho sin subcategoría); en `ESTADO_SOL` la falta de evidencia es `SIN_INFORMACION`; dimensiones `AMBITO` (de referencia) y `ESTADO_*` agregadas; los actos no tienen valor "al ingreso" (en `AL_INGRESO` se informan los vigentes); intervalos de comparación sin par conservan su clave.
- Hallazgos para gates siguientes: MySQL 8.4 falla (error 1112) o cae con `internal_tmp_mem_storage_engine=MEMORY` (servidor desechable reiniciado) cuando crea claves automáticas sobre un CTE referenciado varias veces: toda unión o filtro sobre un CTE va como expresión (`+0`, `CONCAT`), con `JOIN_FIXED_ORDER` y CTE de una sola evaluación (`LIMIT`), y los barridos lo comprueban con EXPLAIN (`StatEngineTest.explained`). E10 congela la fuente con `StatSource.Plan`; E11 exporta con el mismo motor y debe excluir las constancias reservadas en el diccionario; E12/E13 leen `stats` de `/api/catalog`.
- Pendientes: rendimiento formal y eventual alternativa -> E14; cortes y comparación con un corte (`cut_id`, `CORTE`) -> E10; instituciones de los hechos -> 0.9.0.
- Verificación manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=StatEngineTest,StatModesTest,StatQueryHttpTest`; en local, con REPORTES, `POST /api/stats/query` con `{"measure":"INGRESOS","rows":["CANAL"],"period":{"type":"CUENTA_PUBLICA","from":"2025-07-01"}}`.

Mutaciones de E08 (regla -> prueba que falló):

| Regla | Prueba que falló |
|---|---|
| 01 agrupar por propuesta en vez del valor confirmado | StatEngineTest.figuraSolicitud:99 |
| 02 derivar el requirente del canal | StatEngineTest.figuraRequirente:100 |
| 03a usar el canal como clave de via | StatEngineTest.figuraVia:101 |
| 03b perder la agrupacion padre | StatEngineTest.figuraVia:101 |
| 04a region de la sede en vez de la del requirente | StatEngineTest.figuraRegion:102 |
| 04b omitir el valor especial | StatEngineTest.figuraRegion:102 |
| 05a incluir invalidas | StatEngineTest.descarteD25:106 |
| 05b excluir duplicadas | StatEngineTest.descarteD25:106 |
| 06a usar RECHAZADA como devolucion | StatEngineTest.antecedenteD25:370 |
| 06b ignorar returned_ms (vigente) | StatEngineTest.antecedenteD25:372 |
| 06c ignorar returned_ms (a una fecha) | StatModesTest.estadoHistoricoD25:69 |
| 07a exigir REQ | StatEngineTest.pendientesYDirectaD25:112 |
| 07b exigir SOL | StatEngineTest.pendientesYDirectaD25:112 |
| 08 contar hijos como ingresos | StatEngineTest.cadenaUnicaD25:121 |
| 09 aplicar el estado vigente a todos los cortes | StatModesTest.estadoHistoricoD25:64 |
| 10a dia civil en UTC | StatEngineTest.bordeCuentaPublica:133 |
| 10b incluir el limite superior | StatEngineTest.bordeCuentaPublica:132 |
| 11a offset unico | StatModesTest.horarioChile:78 |
| 11b agrupar por dia UTC | StatModesTest.horarioChile:78 |
| 12a usar > en el limite exclusivo | StatEngineTest.registroTardio:148 |
| 12b usar created_ms como fecha de demanda | StatEngineTest.figuraSolicitud:99, StatEngineTest.registroTardio:141, StatEngineTest.serieMes:204 |
| 13a quitar la deduplicacion COM-valor | StatEngineTest.mencionesDerecho:157 |
| 13b equiparar menciones y clasificaciones | StatEngineTest.mencionesDerecho:160 |
| 14a dividir por las menciones | StatEngineTest.porcentajeMultivalor:168 |
| 14b suprimir el aviso | StatEngineTest.porcentajeMultivalor:170 |
| 15a sumar celdas para los margenes | StatEngineTest.cruceDosMultiples:176 |
| 15b unir solo por Caso | StatEngineTest.cruceDosMultiples:175 |
| 15c menciones sin deduplicar en los margenes | StatEngineTest.cruceDosMultiples:182 |
| 16 sumar derechos para el total por canal | StatEngineTest.cruceUnicaYMultiple:187 |
| 17a INNER JOIN que borra pendientes | StatEngineTest.estadosYFiltros:191 |
| 17b contar dos veces con el filtro | StatEngineTest.estadosYFiltros:194 |
| 18a sumar padre e hijo como dos valores | StatModesTest.jerarquiaYRetiro:226 |
| 18b omitir REMOVE | StatModesTest.jerarquiaYRetiro:228 |
| 19a contar la version rectificada | StatEngineTest.actuacionesVigentes:354 |
| 19b contar la reservada | StatEngineTest.actuacionesVigentes:354 |
| 19c contar la tarea | StatEngineTest.actuacionesVigentes:354 |
| 19d contar ambas fuentes de orientacion | StatEngineTest.actuacionesVigentes:354 |
| 20a AL_INGRESO con la version actual | StatModesTest.cuatroModos:94 |
| 20b AL_REQ con la version actual | StatModesTest.cuatroModos:95 |
| 20c FECHA con la version actual | StatModesTest.cuatroModos:96 |
| 20d clasificacion siempre vigente | StatModesTest.cuatroModos:108 |
| 21a usar < en vez de <= | StatModesTest.corteVersionReq:139 |
| 21b permitir la version posterior | StatModesTest.corteVersionReq:139 |
| 21c cortar en requirement.created_ms | StatModesTest.actoDeConstitucion:154 |
| 21d sin espera entre comandos | StatModesTest.actoDeConstitucion:166 |
| 21e sin tope de espera | StatModesTest.actoDeConstitucion:181 |
| 22 leer la complejidad actual en FECHA | StatModesTest.historiaSinEvidencia:191 |
| 22b leer la materia actual en FECHA | StatModesTest.historiaSinEvidencia:207 |
| 22c leer la modalidad actual en FECHA | StatModesTest.historiaSinEvidencia:215 |
| 23a omitir los meses vacios | StatEngineTest.serieMes:204 |
| 23b serie por fecha de registro | StatEngineTest.serieMes:204 |
| 24a inicio del trimestre | StatEngineTest.serieTrimestre:205 |
| 24b inicio del semestre | StatEngineTest.serieSemestre:206 |
| 24c inicio del anio | StatEngineTest.serieAnio:207 |
| 24d inicio de la Cuenta Publica | StatEngineTest.serieCuentaPublica:208 |
| 25a el mismo desplazamiento para ambas comparaciones | StatEngineTest.comparaciones:215 |
| 25b dividir por el periodo actual | StatEngineTest.comparaciones:217 |
| 26a autorizar por el origen | StatEngineTest.alcanceSede:230 |
| 26b autorizar por la sede historica | StatEngineTest.alcanceSede:238 |
| 26c sin alcance antes de agregar | StatEngineTest.alcanceSede:230 |
| 27a ignorar la materia | StatEngineTest.alcanceMateriaYCaso:241 |
| 27b admitir un Caso de otra sede | StatEngineTest.alcanceMateriaYCaso:244 |
| 28a quitar la guarda de REPORTES | StatQueryHttpTest.soloConReportes:70 |
| 28b quitar la guarda de TI | StatQueryHttpTest.tiNunca:80 |
| 28c quitar el requisito CSRF | StatQueryHttpTest.csrfObligatorio:87 |
| 28d agregar GET | StatQueryHttpTest.soloPorPost:97 |
| 29a recepcion en la consulta administrativa | StatEngineTest.conciliaComPorCanal:262 |
| 29b omitir una categoria informativa | StatEngineTest.conciliaComPorCanal:262 |
| 30a multiplicar A por sus Casos | StatEngineTest.conciliaReqPorEstado:272 |
| 30b usar la fecha de la COM | StatEngineTest.conciliaReqPorEstado:275 |
| 31a ignorar AMBITO_ACTIVADO | StatModesTest.sedeActivadaYCerrada:263 |
| 31b ignorar AMBITO_CERRADO | StatModesTest.sedeActivadaYCerrada:263 |
| 31c exigir enabled actual | StatModesTest.sedeActivadaYCerrada:263 |
| 32 dar cobertura a las proyectadas | StatModesTest.sedeProyectada:274 |
| 33a omitir la cobertura del nivel central | StatModesTest.coberturaCentral:284 |
| 33b incluir el instante de termino | StatModesTest.coberturaCentral:284 |
| 34a leer la clave adulta para el NNA | StatModesTest.formularioTerritorio:295 |
| 34b comuna de quien presenta como comuna de los hechos | StatModesTest.formularioTerritorio:298 |
| 35a edad en UTC | StatModesTest.demografiaEstadistica:313 |
| 35b interpretar el texto historico | StatModesTest.demografiaEstadistica:313 |
| 35c serializar la fecha de nacimiento | StatModesTest.demografiaEstadistica:322 |
| 36a elevar la propuesta a informada | StatModesTest.calidadYPropuesta:338 |
| 36b perder el estado especial | StatModesTest.calidadYPropuesta:338 |
| 36c completitud con Casos ajenos | StatModesTest.completitudConCasoAjeno:364 |
| 36d descartar REMISION_CONCLUIDA | StatModesTest.remisionConcluida:374 |
| 37a serializar el cuerpo en la auditoria | StatEngineTest.auditoriaSoloNombres:285 |
| 37b aceptar una clave sin validar | StatEngineTest.contratoCerrado:305 |
| 38a una lectura JDBC por COM | StatEngineTest.consultasPorConjunto:339 |
| 38b truncar en vez de rechazar | StatEngineTest.limiteDeCeldas:313 |
| 39 cambiar una expresion de E05 | StatExpressionsTest.expresionesIgualesALasDeE07:63 |
| 40 rotulo de las instituciones | StatEngineTest.actuacionesVigentes:361 |
| 41 unir un CTE por su columna (clave automatica de MySQL) | StatEngineTest.barridoDeCruces:414 (MySQL, EXPLAIN) |

### E08b - Motor estadístico: confinamiento, límites de ejecución y concurrencia (servidor)   (CERRADO)
- Fecha: 2026-11-25 (commit `gate E08b: limites del motor estadistico`), DDN-TEST-SIGER (Windows), Claude Code; base `9f86d25` (cierre de E08 `4c32436` más el commit de documentación que agrega E08b).
- GO del responsable: A (503 al vencer el plazo), B (log + 503 + `stats.enabled`; sin tocar `Personnel.java`), C (unidad = raíz de `siger.data`; `instance-drive` solo para ensayos), D (generador versionado y desactivado por defecto) y E (auditar la cancelación, no el 429), más las precisiones 2-10: cupos antes de abrir la transacción y liberados tras ella (también con rollback), contador por cuenta atómico y sin carreras, plazo común a todas las sentencias en REPEATABLE READ, auditoría después del rollback y sin ocultar el error, límites de sesión solo con evidencia, ajuste de unidad sin efecto en una instancia institucional, vigilancia del espacio de D: y un comando con los cupos ocupados.
- Hecho: `StatGuard` (nueva, reutilizable por E10 y E11). (1) Concurrencia: 2 en total y 1 por cuenta (`siger.stats.max-concurrent`, `max-concurrent-per-account`), `tryAcquire` sin cola, 429 en texto llano antes de pedir conexión, contador con `ConcurrentHashMap.compute` (la cuenta sale del mapa en cero) y liberación en `finally`. (2) Plazo: `TransactionTemplate` REPEATABLE READ con `siger.stats.timeout-seconds` (30), común a consulta, comparación y lecturas auxiliares; KILL QUERY de Connector/J en MySQL; al vencer, rollback, 503 sin resultados parciales y `STAT_CONSULTA_CANCELADA` en otra transacción con los nombres validados (su fallo va al log). (3) Confinamiento: al crearse, sobre MySQL, `@@datadir`, cada ruta de `@@tmpdir` y `@@innodb_temp_tablespaces_dir` (relativas contra el datadir, sin distinguir mayúsculas) en la unidad de `siger.data`; si no, `MOTOR_ESTADISTICO_DESHABILITADO` en el log, 503 y `stats.enabled=false`. `siger.stats.instance-drive` solo rige con el perfil test sin el perfil mysql (si no, `UNIDAD_DE_INSTANCIA_IGNORADA`). `StatEngine.query`: 403, 503, cupos y transacción con plazo; ya no es `@Transactional`.
- Archivos clave: `StatGuard.java` (nuevo), `StatEngine.java`, `application.properties` (4 propiedades comentadas); `StatGuardTest` (12), `StatTimeoutHttpTest` (1, vista lenta en su propia base), `StatQueryHttpTest` (+2), `StatLoadMeasureTest` (1, desactivada por defecto), `StatEngineTest` y `StatFixture` (constructor); `CLAUDE.md` (guardas y procedimiento del MySQL desechable en D:), `docs/ARQUITECTURA.md`, `docs/CONTRATO_STATS_0_8_0.md`.
- Migraciones: no aplica (esquema 22). `Api.java` y `Workflow.java` sin cambios.
- Verificación: JUnit H2 328 ok, 1 omitida (la medición) (antes 313, corrida de base sin cambios: 313 ok). MySQL 8.4.13 (suite completa con `siger.mysql.suite=true` y `-Dsiger.stats.instance-drive=D:\`, actualizaciones desde 0.4.0 y 0.7.0 hasta V22): 332 ok, 1 omitida (la medición, ejecutada aparte con `-Dsiger.stats.measure=true`: 1 ok) (antes 317), con `MySql84UpgradeTest` y `MySql84Upgrade070Test` conformes. MySQL desechable con el procedimiento nuevo en `D:\Ensayo-E08b\mysql` (`datadir`, `tmpdir` y `#innodb_temp` en D:, TempTable, `127.0.0.1:3417`), detenido con `mysqladmin shutdown`, puerto 3417 libre y sin procesos propios; `MySQL80` y la instancia ajena de `D:\sigervalidacion` intactos. La carpeta `D:\Ensayo-E08b` (datos sintéticos y la clave del usuario desechable) no se pudo borrar: la protección de Claude Code exige que una persona apruebe ese borrado (ver Pendientes). Frontend no aplica (sin cambios). `Check-Scripts.ps1`: conforme con `powershell.exe` 5.1 y solo las rutas nativas de módulos, sin Maven ni npm en paralelo (40 scripts, 73 órdenes en 60 procesos). Humo no aplica (sin interfaz; 429, 503 y cancelación por HTTP real en las pruebas).
- Medición (50.005 COM deterministas, MySQL 8.4.13 en D:, 10 consultas pesadas): entre 0,2 y 2,8 s (completitud 2,8 s); temporales en disco solo en completitud (2 tablas, 28 MiB máximos de temporales de sesión de InnoDB); `tmpdir` sin archivos; D: nunca bajo 52 GiB libres (no empieza con menos de 20 GiB; cancela con menos de 15 GiB); `max_join_size` mínimo con que pasan: 1e6 (5 consultas) y 1e7 (5). Generación 9,4 s.
- Decisión sobre límites de sesión (con esa evidencia): no se adoptan. `max_join_size` compara estimaciones del optimizador que ya exigen 1e6-1e7 con 50.005 COM y crecen con el volumen: un umbral fijo rechazaría consultas legítimas o no protegería; `tmp_table_size` y `max_heap_table_size` limitan memoria, no disco; `max_execution_time` sería redundante: KILL QUERY resultó efectivo (sentencia ausente de `PROCESSLIST` y conexión reutilizada sana). Sin variables de sesión no hay configuración que restaurar.
- Evidencia de mutación: 25 mutaciones temporales, todas fallaron en la prueba prevista (tabla abajo); archivos restaurados y comprobados por SHA-256.
- Condiciones de la sección 2: `git diff v0.7.0` vacío para `scripts/`, `.mvn/`, los 7 archivos de recepción del Formulario, `Reports.java` y `Territory.java`; V1-V22 sin cambios; ningún `my.ini` tocado; `IntakeCommands`, `Workflow`, `Access` y `REOPEN_*` sin cambios; sin dependencias nuevas; auditoría solo con nombres.
- Facilidad de uso: no aplica (servidor); 429, 503 y el plazo se explican en texto llano, que el frontend muestra tal cual (`lib/api.ts`).
- Definiciones usadas: ninguna.
- Desvíos del plan: `stats.enabled` en `/api/catalog` (cambio aditivo del contrato autorizado en el GO). M23 (lock global dentro de la consulta) falló al tomar el segundo cupo, que ya queda bloqueado, antes del paso del comando.
- Limitaciones residuales: las guardas no son una cuota de disco (MySQL 8.4 no limita por sesión sus temporales en disco): una consulta puede escribir temporales hasta que vence su plazo (30 s más hasta 1 s por el redondeo de `setQueryTimeout`); H2 revisa la cancelación cada 128 filas; los cupos son por proceso (un servicio); la unidad no distingue un MySQL remoto; KILL QUERY usa una conexión adicional del servidor.
- Hallazgos para gates siguientes: E10 y E11 usan `StatGuard.run` (cupos) y `StatGuard.read` (plazo y auditoría de la cancelación) con su propia nota de nombres; `StatLoadMeasureTest` sirve a E14 (`-Dsiger.stats.measure=true`, resultado en `target/medicion-e08b.txt`); en este equipo la suite MySQL necesita `-Dsiger.stats.instance-drive=D:\`; E17 y el paso U verifican `@@tmpdir` y `@@innodb_temp_tablespaces_dir` en D: (si no, el motor queda deshabilitado).
- Pendientes: borrar `D:\Ensayo-E08b` (MySQL desechable ya detenido) -> responsable; medición formal de rendimiento -> E14; rutas de MySQL en la VM -> E17 y paso U. Actualización 2026-11-25: el responsable borró D:\Ensayo-E08b.
- Verificación manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=StatGuardTest,StatTimeoutHttpTest,StatQueryHttpTest`.

Mutaciones de E08b (regla -> prueba que falló):

| Regla | Prueba que falló |
|---|---|
| 01 sin limite total | StatGuardTest.concurrenciaTotalYPorCuenta:78 |
| 02 sin limite por cuenta | StatGuardTest.concurrenciaTotalYPorCuenta:78, StatGuardTest.contadorAtomicoSinCarreras:129, StatQueryHttpTest.sinCupoResponde429:133 |
| 03 liberar solo al terminar bien | StatGuardTest.liberacionAnteErrorYRollback:137 |
| 04 esperar en vez de rechazar | StatGuardTest.concurrenciaTotalYPorCuenta:80 |
| 05 contador sin limpieza | StatGuardTest.concurrenciaTotalYPorCuenta:97, StatGuardTest.contadorAtomicoSinCarreras:131 |
| 06 sin plazo | StatGuardTest.tiempoAgotadoCancelaYAuditaSoloNombres:188, StatTimeoutHttpTest.consultaQueSuperaElPlazoSeCancelaYAudita:81 |
| 07 plazo por sentencia, no comun | StatGuardTest.presupuestoComunEntreSentenciasSinResultadosParciales:206 |
| 08 sin auditoria de la cancelacion | StatGuardTest.tiempoAgotadoCancelaYAuditaSoloNombres:193 |
| 09 auditoria antes del rollback | StatGuardTest.tiempoAgotadoCancelaYAuditaSoloNombres:193 |
| 10 auditoria con contenido de la peticion | StatTimeoutHttpTest.consultaQueSuperaElPlazoSeCancelaYAudita:91 |
| 11 el fallo de la auditoria oculta el rechazo | StatGuardTest.elFalloDeLaAuditoriaNoOcultaElRechazo:252 |
| 12 ignorar innodb_temp_tablespaces_dir | StatGuardTest.confinamientoDeRutas:262 |
| 13 no resolver rutas relativas | StatGuardTest.confinamientoDeRutas:260 |
| 14 revisar solo el primer tmpdir | StatGuardTest.confinamientoDeRutas:264 |
| 15 distinguir mayusculas | StatGuardTest.confinamientoDeRutas:261 |
| 16 el ajuste de unidad rige en la VM | StatGuardTest.unidadDeLaInstanciaSoloAjustableEnEnsayos:278 |
| 17 motor deshabilitado que responde | StatQueryHttpTest.motorDeshabilitadoSinAfectarLaOperacion:147 |
| 18 StatEngine sin la guarda de concurrencia | StatGuardTest.rechazadasNoPidenConexion:157, StatQueryHttpTest.sinCupoResponde429:133 |
| 19 transaccion antes del cupo | StatGuardTest.rechazadasNoPidenConexion:157 |
| 20 sin stats.enabled en el catalogo | StatQueryHttpTest.motorDeshabilitadoSinAfectarLaOperacion:149, StatQueryHttpTest.respuestaErroresYCatalogo:118 |
| 21 rechazo por cuenta que no devuelve el cupo total | StatGuardTest.concurrenciaTotalYPorCuenta:72 |
| 22 no reconocer el plazo vencido | StatGuardTest.tiempoAgotadoCancelaYAuditaSoloNombres:188 |
| 23 lock global dentro de la consulta | StatGuardTest.comandoDeExpedienteConLosCuposOcupados:72 |
| 24 confinamiento que ignora MySQL | StatGuardTest.confinamientoAlArrancar:293 |
| 25 sin plazo (MySQL) | StatGuardTest.cancelacionEfectivaYConexionReutilizable:230 |

### E09 - Captura estadistica en el flujo (interfaz): caracterizacion, derechos, actuaciones y cierre   (CERRADO)
- Fecha: 2026-11-25 (commit `gate E09: captura estadistica, interfaz`), DDN-TEST-SIGER (Windows), Claude Code; base `fbcafb2` (cierre de E08b).
- GO del responsable: A `ActionDialog.tsx` con campo `custom` aditivo (misma clave, revision previa y errores; el valor entra en la revision y en el envio; `actionData.test.ts` y los render existentes sin cambios); B propuesta como "Propuesta: X" con "Usar propuesta", enviada solo si se elige; C `code_labels` con `personModel.ts` y `CodeSelect.tsx`; D resolucion en el acto solo en Resolver primera atencion y la nueva atencion; E habilitar el cierre solo con datos del servidor y humo que compara las cuentas; ayuda desde la `description` del item; ayuda fija de grupos prioritarios sin seleccion sugerida; facilidad de uso (U10 en 3 pasos o menos, sin codigos, ingreso urgente sin obligatorios nuevos, movil); 16 mutaciones; sin division.
- Hecho: (1) Bloque "Caracterizacion del ingreso" (`StatFields`, `characterizationModel.ts`) en el ingreso manual, la nueva atencion, COM_ROUTE, COM_ASSUME, SOL_DISCARD (obligatorio lo pendiente si es duplicada), REQ_CLOSE y ATTENTION_RESOLVE; solo variables con listado vigente, motivo al corregir un INFORMADO, definicion del item como ayuda. (2) Tarjeta "Caracterizacion" en COM y REQ con estado, origen, propuesta aparte, historial y STAT_CLASSIFY. (3) `RightsPanel` (`lib/rightsModel.ts`) despues de "Datos del REQ" y "Datos del Caso": resumen, consolidado con los Casos legibles, historial y clasificacion en dialogo ancho con busqueda sin tildes y arbol eje -> derecho -> subcategoria; acceso desde Actuaciones y la primera atencion. (4) Tipo de actuacion e instituciones en CASE_NOTE/REQ_NOTE/NOTE_RECTIFY (rectificar precarga y conserva) y "Compromiso de" en TASK_CREATE/TASK_EDIT; constancias y tareas los muestran. (5) Cierres: `closeState` habilita "Cerrar REQ" y "Resolver" si todos los bloqueos del servidor son variables exigibles pendientes; derechos y contexto remiten al panel. (6) `code_labels` adoptados (cierra el pendiente de E04 y E06).
- Archivos clave: `components/{ActionDialog,StatFields,RightsPanel}.tsx`, `components/person/CodeSelect.tsx`, `lib/rightsModel.ts`, `lib/person/personModel.ts`, `features/detail/{characterizationModel.ts,Characterization.tsx,ActionFields.tsx,notesModel.ts,ComDetail,SolDetail,RequirementDetail,CaseDetail,Notes,actions.ts}`, `features/intake/{Intake.tsx,IntakeRecordPanel.tsx,intakeModel.ts}`, `features/attention/{Attention,AttentionActions}.tsx`, `styles/components.css`; pruebas `tests/{characterization,rights,notes}.test.ts` y `tests/{characterization,rights}-render.test.mjs`; `CLAUDE.md`, `docs/ARQUITECTURA.md`.
- Migraciones: no aplica (esquema 22).
- Verificacion: JUnit H2 no aplica (`git diff -- backend` vacio; base 328 ok y 1 omitida de E08b); MySQL no aplica (sin SQL); frontend typecheck ok, 183 ok (antes 154), build ok; `Check-Scripts.ps1` (powershell.exe 5.1 con las rutas nativas de modulos, al final y sin Maven ni npm): 40 scripts, 73 ordenes en 60 procesos; humo 37/37 con el JAR (`package -DskipTests` solo para el humo; perfil local, H2 en `backend/target/humo-e09`, puerto 18643 libre antes; datos borrados y proceso detenido al terminar; la instancia ajena en 18543 no se toco): TI importa 8 catalogos y activa 7 (TIPO_REQUIRENTE sin vigente), via derivada interfaz = servidor, variable sin vigente no enviada (enviarla da 409), STAT_CLASSIFY con y sin motivo, COM_ROUTE con `stat`, cuentas de la interfaz = `discard_blockers` (1), `closure_blockers` (1; en la vulneracion 2 = 0 resolubles + 2 derechos) y `resolve_blockers` (1), cierres resueltos en el acto, 409 de registro cerrado, RIGHTS_CLASSIFY con y sin motivo, actuacion tipificada con institucion, rectificacion que conserva, compromiso conservado y textos nuevos en el JAR.
- Evidencia de mutacion: 16 mutaciones temporales, todas fallaron en la prueba prevista (tabla abajo); la 03 tambien en el humo (2 FAIL); archivos restaurados y comprobados por SHA-256 (`git status` identico antes y despues).
- Condiciones de la seccion 2: `git diff -- backend` vacio; `git diff v0.7.0` vacio para `scripts/`, `.mvn/`, los 7 archivos de recepcion del Formulario, `Reports.java` y `Territory.java`; V1-V22 sin cambios; version 0.7.0, `package.json` y `package-lock.json` sin cambios (sin dependencias nuevas); humo solo con datos sinteticos.
- Facilidad de uso: U10 en render (buscar "educación" deja la subcategoria a la vista y a un clic, sin ramas cerradas: Clasificar, escribir, marcar); ningun codigo visible (barridos de render del bloque, la tarjeta y el panel); ingreso urgente sin obligatorios nuevos (render: misma cantidad de marcas con y sin listados vigentes; bloque "Opcional"); movil en una columna (regla `@media (max-width: 640px)` probada; dialogo ancho con `--modal-width`); ayudas en lenguaje llano (definicion del item, regla de "Oficio", "Integrante de la Defensoría", grupos prioritarios); estados con texto, nunca solo color.
- Definiciones usadas: DI-01 a DI-04 (catalogos de referencia en pruebas y humo); DI-07 (sin cambios en la reapertura).
- Desvios del plan: (1) `ActionDialog.tsx`, `personModel.ts` y `CodeSelect.tsx` fuera de la lista original, autorizados en el GO (A y C). (2) La ficha de la SOL no trae la caracterizacion de su COM: el dialogo de descarte la lee de `GET /api/detail/COM/{id}` (lectura que quien decide la SOL ya tiene; queda auditada como CONSULTA de la COM). (3) `newRecordVars` replica la via derivada del canal (D31: item seleccionable con el codigo del canal) para un registro que aun no existe; el humo compara interfaz = servidor. `lib/fields.ts`, `lib/labels.ts` y `ClosureStatus.tsx` no se modificaron.
- Diferencias de referencia: DISENO 10.1: la propuesta no se prellena; se muestra rotulada y se usa solo con "Usar propuesta" (decision B, D29). DISENO 10.2: el panel muestra el resumen en la ficha y clasifica en un dialogo ancho; el acceso desde "Registrar actuación" es un aviso dentro del dialogo y el boton "Derechos y contexto" del panel Actuaciones.
- Hallazgos para gates siguientes: `stat_completeness` no publica el codigo que activa la regla de vulneracion: la interfaz no lo predice y, si se elige vulneracion en un dialogo de cierre, el 409 del servidor lo informa; `rightsPending` solo ve los Casos legibles (el servidor cuenta tambien los estados especiales de Casos no legibles). E12/E13 pueden reutilizar `statLists`/`specials` para rotulos y `CatalogSelect`/`itemHelp` para las definiciones; un dialogo con datos compuestos usa el campo `custom`.
- Pendientes: pantallas para COM_IMMEDIATE y ATTENTION_CREATE con `resolve_now` (resolucion en el acto con derechos) -> posible mejora, sin gate (decision D); caracterizacion en la ficha de la SOL (evitaria la lectura de la COM en el descarte) -> posible mejora de servidor, sin gate; borrar la carpeta del MySQL desechable de E08b -> responsable (sin cambio); DI-01 a DI-04 -> activacion.
- Verificacion manual sugerida al usuario: `cd frontend; node --test tests/characterization.test.ts tests/rights.test.ts tests/notes.test.ts tests/characterization-render.test.mjs tests/rights-render.test.mjs`; en local, con catalogos activados por TI: Distribuir SOL con "Usar propuesta", Cerrar REQ con la caracterizacion pendiente y, en un REQ, Derechos y contexto > Clasificar > buscar "educación".

Mutaciones de E09 (regla -> prueba que fallo):

| Regla | Prueba que fallo |
|---|---|
| 01 propuesta preseleccionada | characterization-render: la propuesta se rotula como propuesta y no se preselecciona |
| 02 correccion sin motivo | characterization.test: corregir un valor informado exige motivo; registro nuevo (via derivada) |
| 03 se envia una variable sin listado vigente | characterization.test: solo variables con listado vigente y que cambian; humo: 2 FAIL (STAT_CLASSIFY) |
| 04 valor sin cambio reenviado | characterization.test: solo variables con listado vigente y que cambian |
| 05 conjunto completo como agregado | rights.test: marcar y quitar se envian como diferencia |
| 06 quitar sin motivo | rights.test: quitar o reemplazar un estado exige motivo |
| 07 estado especial con valores | rights.test: un estado especial solo sin valores |
| 08a busqueda que distingue tildes | rights.test: busqueda sin tildes |
| 08b busqueda que pierde la subcategoria | rights.test: busqueda sin tildes (subcategorias por descendencia) |
| 09 eje seleccionable | rights.test: marcar y quitar (un eje no se marca) |
| 10 registro cerrado sin "Requiere reapertura formal" | rights-render: lector sin botones; registro cerrado |
| 11 lector con botones | rights-render: lector sin botones; registro cerrado |
| 12 rectificacion que borra el tipo | notes.test: rectificar parte del tipo y las instituciones |
| 13 cuenta de bloqueos sin filtrar lo pendiente | characterization.test: cierre: el dialogo resuelve solo las exigibles pendientes |
| 14 codigo visible | rights-render: panel sin codigos |
| 15 code_labels ignorados | notes.test: code_labels |
| 16 descripcion oculta | characterization-render: la definicion del item elegido llega como ayuda |

### E10 - Cortes estadisticos y configuraciones guardadas (servidor)   (CERRADO)
- Fecha: 2026-11-26 (commit `gate E10: cortes y configuraciones`), DDN-TEST-SIGER (Windows), Claude Code; base `17ac793` (cierre de E09 `4c7164f` más el commit de documentación de DI-13).
- GO del responsable (2026-11-25): A (stat_cut_row y relaciones de entrada en vez de stat_cut_multi), B (dentro de STAT_CUT_CREATE con lock, cupo y plazo; detenerse si el lock supera 5 s), C (solo VIGENTE, as_of = instante del comando, comparación por celdas y márgenes, vigente comparada con el alcance del corte), D (cubrir en ámbitos y materias), E (vistas), F (auditoría con CORTE_VERIFICADO), G (StatGuard.java y Javadoc de Roles.java) y H, con sus condiciones. Detención por el lock (10,6 s) y decisión A del 2026-11-26 (tabla de decisiones).
- Hecho: (1) V23 y `StatCuts`: congelado dentro de `Workflow.run` (`StatSource.Plan.freezer`: toda COM del alcance con alguna fecha de análisis en el periodo, en todos sus estados) en `stat_cut_row` y las relaciones de entrada del motor, huellas canónicas, verificación, anulación de la cabecera, lecturas y cobertura. (2) Consulta sobre un corte con el mismo `Plan` en modo corte (lee solo `stat_cut_*` y los ítems de catálogo; rótulos, tramo y propuestas del instante) y `compare CORTE`. (3) `StatViews`. (4) `ADSCRIPCION_INICIAL` (DI-13). (5) `StatGuard.read` dentro de una transacción abierta: plazo como fecha límite de la conexión del comando, levantado al terminar; al vencer, 503 y `STAT_CONSULTA_CANCELADA` después del rollback (`REQUIRES_NEW`). (6) `Workflow.afterPreviousCommand`: duerme mientras falta más de 1 ms y deja `RELOJ_ATRASADO <ms>` al llegar al tope (una por episodio, a lo más una por minuto; condición del GO de E08). (7) Lotes de inserción de hasta 2.000 filas o 60.000 parámetros.
- Archivos clave: `V23__cortes_y_vistas.sql`, `StatCuts.java`, `StatViews.java` (nuevos), `StatSource.java`, `StatEngine.java`, `StatGuard.java`, `Workflow.java`, `Api.java`, `Roles.java` (Javadoc); `StatCutsTest` (18), `StatAdscriptionTest` (1), `StatViewsTest` (1), `StatCutHttpTest` (1), `WorkflowClockTest` (2), `StatEngineTest` (+1, barrido de equivalencia), `StatFixture`, `StatLoadMeasureTest` (sección E10), `InstitutionalReaderTest` y `ImmutabilityTest` (comandos nuevos), pruebas de esquema 23; `docs/CONTRATO_STATS_0_8_0.md` (secciones 11 a 13), `CLAUDE.md`, `docs/ARQUITECTURA.md`, PLAN (sección 9).
- Migraciones: V23 (`stat_cut`, `stat_cut_row`, `stat_cut_event`, `stat_cut_action`, `stat_cut_action_institution`, `stat_cut_person`, `stat_cut_case`, `stat_view`; esquema 23), sin siembra, sin claves foráneas en lo congelado ni cascadas.
- Verificación: JUnit H2 353 ok, 1 omitida (la medición) (antes 328 y 1); MySQL 8.4.13 (suite completa con `siger.mysql.suite=true`, `-Dsiger.stats.instance-drive=D:\`, actualizaciones desde 0.4.0 y 0.7.0 hasta V23 con `communication_original` y `external_*` idénticos) 357 ok, 1 omitida (antes 332 y 1); frontend typecheck ok, 183 ok (sin cambio), build ok; humo 23/23 con el JAR (perfil local, H2 en `backend/target/humo-e10`, puerto 18643; dos arranques previos abortaron en el script, no en SIGER: clave `users` de la administración y nuevo inicio de sesión tras las concesiones; datos borrados y proceso detenido); `Check-Scripts.ps1` (powershell.exe 5.1, rutas nativas de módulos, sin Maven ni npm): 40 scripts, 73 órdenes en 60 procesos. MySQL con el procedimiento de E08b en `D:\Ensayo-E10\mysql` (datadir, tmpdir y `#innodb_temp` en D:, `127.0.0.1:3417`), detenido con `mysqladmin shutdown`, puerto libre, sin procesos propios; MySQL80 y la instancia ajena de `D:\sigervalidacion` intactos.
- Medición (MySQL, 50.005 COM, corte institucional de la Cuenta Pública con 25.084 COM congeladas, 70.357 eventos, 11.355 actuaciones, 18.814 NNA, 9.557 Casos): `STAT_CUT_CREATE` 10,6 s con lotes de 200 filas (fila por COM 6,0 s de consulta y 1,5 s de inserción; eventos 0,8 + 0,7 s; actuaciones 0,4 s; Casos 0,3 s; NNA ≈ 1 s) y 11,0 y 10,6 s con los lotes finales (sin mejora medible: domina la consulta de la fila). Consultas sobre el corte 0,04-0,9 s (vigentes 0,1-2,7 s); verificación 0,3 s. Proyección: unos 0,4 ms por COM congelada; el plazo de 30 s se alcanzaría con unas 70.000 COM en un corte (contrato, sección 13).
- Evidencia de mutación: 40 mutaciones temporales (23 del plan más las de los puntos 1, 2, 4, 5, 6, 8, D y la equivalencia), todas fallaron en la prueba prevista (tabla abajo); cada archivo restaurado y comprobado por SHA-256.
- Condiciones de la sección 2: `git diff v0.7.0` vacío para `scripts/`, `.mvn/`, los 7 archivos de recepción del Formulario, `Reports.java` y `Territory.java`; `IntakeCommands` (incluido `conserveExternal`), `RequirementCommands` (`REOPEN_*`) y `Access` sin cambios; V1-V22 sin cambios; ninguna escritura en `communication_original` ni `external_*`; sin dependencias nuevas; auditoría solo con nombres y códigos de corte; TI sin acceso.
- Facilidad de uso: no aplica (servidor); los rechazos se explican en texto llano ("El periodo consultado está fuera del periodo del corte X", "Un corte guarda los valores de su instante...", "El corte ya está anulado.").
- Definiciones usadas: DI-13 (ADSCRIPCION_INICIAL; ratificación institucional pendiente, no bloquea); DI-01 a DI-04 (catálogos de referencia en pruebas).
- Desvíos del plan: (1) detención por el lock y decisión A (2026-11-26). (2) `ADSCRIPCION_INICIAL` y `compares` con `CORTE` en el catálogo: cambios aditivos del contrato (DI-13 y reservado a E10). (3) `ImmutabilityTest` agrega la categoría `STATISTICS` (cambia la suma de la aserción de categorías disjuntas, no su sentido); las pruebas de esquema 22 → 23 solo cambian el número (`FormularioServiceTest`, `MySql84IntegrationTest` ×2, `MySql84UpgradeTest`); ninguna aserción se eliminó.
- Diferencias de referencia: DISENO 6: relaciones de entrada en vez de `stat_cut_multi`; sin `filters_json` (el corte guarda toda la población del periodo y alcance; los filtros se aplican al consultarlo) y con `counts_json`, `dimensions_json`, `labels_json` y `revision`; id asignado bajo el lock y cabecera escrita al final; el tramo de edad del NNA congelado. La consulta vigente comparada con un corte usa el alcance del corte.
- Hallazgos para gates siguientes: H2 (perfiles test y local) espera un lock 2 s: ahí un corte que tarde más hace fallar los comandos concurrentes (en MySQL rige `innodb_lock_wait_timeout`, 50 s por omisión, sin cambio en `Init-Server.ps1`). Con el pool de 6 conexiones y 10 s de espera de conexión, durante un corte largo más de 5 operaciones en espera podrían hacer fallar una solicitud adicional: otro motivo para generar los cortes fuera del horario de atención. E11 exporta desde un corte con el mismo `Plan` (modo corte) y `StatGuard`; E12/E13 leen `GET /api/stats/cuts` y `/views` y `stats.compares`; `StatLoadMeasureTest` deja `target/medicion-e10.txt` para E14.
- Pendientes: borrar `D:\Ensayo-E10` (MySQL detenido; datos sintéticos y la clave del usuario desechable) -> responsable (la protección de Claude Code bloqueó el borrado); ratificación de DI-13 -> institución; corte sin lock global -> 0.9.0 (sección 9 del plan).
- Verificación manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=StatCutsTest,StatAdscriptionTest,StatViewsTest,StatCutHttpTest,WorkflowClockTest`; en local, con REPORTES y GESTION_ESTADISTICA, `STAT_CUT_CREATE` por `/api/commands` y `POST /api/stats/query` con `cut_id`.

Mutaciones de E10 (regla -> prueba que falló):

| Regla | Prueba que falló |
|---|---|
| 01 A sin destino da una clave nula | StatAdscriptionTest.matrizDi13EnLosCuatroModosYEnUnCorte:31 |
| 02 codigo del ambito central como categoria | StatAdscriptionTest.matrizDi13EnLosCuatroModosYEnUnCorte:69 |
| 03 sede vigente tras la transferencia | StatAdscriptionTest.matrizDi13EnLosCuatroModosYEnUnCorte:69 |
| 04 contar una vez por NNA en la adscripcion | StatAdscriptionTest.matrizDi13EnLosCuatroModosYEnUnCorte:99 |
| 05 adscripcion posterior al instante | StatAdscriptionTest.matrizDi13EnLosCuatroModosYEnUnCorte:79 |
| 06 Oficina Nacional fuera de las filas y en el total | StatAdscriptionTest.matrizDi13EnLosCuatroModosYEnUnCorte:34 |
| 07 consulta sobre el corte que lee la fuente vigente | StatCutsTest.anulacionConservaElCorte:75, StatCutsTest.coberturaDelAlcance:75, StatCutsTest.cubrirExigeTambienLaMateria:75, StatCutsTest.elCorteSeLeeSoloASiMismo:230, StatCutsTest.escenario9CorreccionPosteriorNoCambiaElCorteYLaComparacionLaMuestra:75, StatCutsTest.rotulosDelInstanteDelCorte:315 |
| 08 huella sin la relacion multivaluada | StatCutsTest.huellaCanonicaEstableYVerificacionQueDetectaAlteraciones:141, StatCutsTest.serializacionCanonicaDocumentada:176 |
| 09 verificacion que no recalcula | StatCutsTest.huellaCanonicaEstableYVerificacionQueDetectaAlteraciones:141 |
| 10 anulacion que modifica filas | StatCutsTest.anulacionConservaElCorte:354, StatCutsTest.ningunCodigoModificaUnCorte:374 |
| 11 anulacion sin fundamento | StatCutsTest.anulacionConservaElCorte:346 |
| 12 crear sin GESTION_ESTADISTICA | StatCutsTest.soloGestionEstadisticaEnSuAlcanceYNuncaTi:249 |
| 13 crear en otro alcance | StatCutsTest.soloGestionEstadisticaEnSuAlcanceYNuncaTi:250 |
| 14 corte visible fuera de cobertura | StatCutsTest.anulacionConservaElCorte:349, StatCutsTest.coberturaDelAlcance:267, StatCutsTest.cubrirExigeTambienLaMateria:294 |
| 15 TI con acceso a la lista | StatCutsTest.coberturaDelAlcance:276 |
| 16 COM fuera del alcance congelada | StatCutsTest.coberturaDelAlcance:279 |
| 17 comparacion con la fuente vigente como base | StatCutsTest.escenario9CorreccionPosteriorNoCambiaElCorteYLaComparacionLaMuestra:101 |
| 18 periodo del corte ignorado | StatCutsTest.periodoYModoDelCorte:328 |
| 19 eventos del corte leidos de stat_event | StatCutsTest.elCorteSeLeeSoloASiMismo:240, StatCutsTest.escenario9CorreccionPosteriorNoCambiaElCorteYLaComparacionLaMuestra:108 |
| 20 compartir sin GESTION_ESTADISTICA | StatViewsTest.vistasPersonalesYCompartidas:57 |
| 21 editar una vista ajena | StatViewsTest.vistasPersonalesYCompartidas:51 |
| 22 definicion no validada | StatViewsTest.vistasPersonalesYCompartidas:43 |
| 23 revision de la vista no comprobada | StatViewsTest.vistasPersonalesYCompartidas:52 |
| 24 sin la linea de log del reloj atrasado | WorkflowClockTest.relojAtrasadoDejaUnaLineaPorEpisodio:50 |
| 25 espera activa en vez de dormir | WorkflowClockTest.relojAtrasadoDejaUnaLineaPorEpisodio:49 |
| 26 una linea por comando (sin episodio ni minuto) | WorkflowClockTest.relojAtrasadoDejaUnaLineaPorEpisodio:56 |
| 27 corte sin el cupo de StatGuard | StatCutsTest.cupoSinEsperaDentroDelComando:410 |
| 28 corte sin el plazo de StatGuard | StatCutsTest.plazoVencidoDentroDelComando:428 |
| 29 cancelacion del corte sin auditar | StatCutsTest.plazoVencidoDentroDelComando:432 |
| 30 plazo del corte que sigue rigiendo despues | StatCutsTest.plazoVencidoDentroDelComando:437 |
| 31 dato personal en lo congelado | StatCutsTest.listaCerradaDeColumnasSinDatosPersonales:188 |
| 32 constancia reservada en el corte | StatCutsTest.reservaYRectificacionNoEntranAlCorte:200 |
| 33 rotulos de ambito de la tabla vigente en un corte | StatCutsTest.rotulosDelInstanteDelCorte:316 |
| 34 propuestas recalculadas en un corte | StatCutsTest.anulacionConservaElCorte:75, StatCutsTest.coberturaDelAlcance:75, StatCutsTest.cubrirExigeTambienLaMateria:75, StatCutsTest.elCorteSeLeeSoloASiMismo:230, StatCutsTest.escenario9CorreccionPosteriorNoCambiaElCorteYLaComparacionLaMuestra:75 |
| 35 cubrir por materia una sede completa | StatCutsTest.coberturaDelAlcance:270, StatCutsTest.cubrirExigeTambienLaMateria:295 |
| 36 cubrir sin exigir la materia | StatCutsTest.cubrirExigeTambienLaMateria:294 |
| 37 corte solo con las computables | StatEngineTest.corteReproduceLaConsultaVigente:445 |
| 38 metadatos sin la huella de las filas | StatCutsTest.huellaCanonicaEstableYVerificacionQueDetectaAlteraciones:158 |
| 39 huella de filas que depende del instante | StatCutsTest.anulacionConservaElCorte:355, StatCutsTest.comandosPosterioresNoCambianElCorte:397, StatCutsTest.congeladoSinClavesAutomaticas:455, StatCutsTest.escenario9CorreccionPosteriorNoCambiaElCorteYLaComparacionLaMuestra:114, StatCutsTest.huellaCanonicaEstableYVerificacionQueDetectaAlteraciones:122, StatCutsTest.serializacionCanonicaDocumentada:176 |
| 40 una sede anula el corte institucional | StatCutsTest.anulacionConservaElCorte:349 |

### E11 - Exportaciones CSV, XLSX y ZIP e importacion de series historicas (servidor)   (CERRADO)
- Fecha: 2026-11-26 (commit `gate E11: exportaciones y series`, squash de `wip/e11-codex` en `main`), DDN-TEST-SIGER (Windows); base `58389fd` (cierre de E10). Ejecutores: Codex (implementación) y Claude Code (cierre); desvío 1.
- GO del responsable (2026-11-26): A, B (con su ajuste), C y DI-12 sin dependencias, con las condiciones 1 a 11; transcrito íntegro en "GO aprobados pendientes de ejecución" (E11). Detención por la medición y decisión del responsable del 2026-11-26 (tabla de decisiones: 1, 2 y 3; la 4 a 0.9.0; ajuste del punto 3).
- Hecho: (1) V24 y `StatCuts`: complemento del corte (`stat_export_cut_folio`, `stat_export_cut_origin`, `stat_export_cut_integrity`) en la misma transacción, con lista cerrada, sin cascadas y huella propia; `rows_sha256` de V23 sin cambio. (2) `StatExport`: CSV, XLSX y ZIP del agregado del motor; ZIP con microdatos (un folio por COM computable, archivo multivalor con `en_req`, `casos` y `menciones`), diccionario, catálogos, `LEEME.txt`, metadatos y `SHA256SUMS.txt`; reglas de B y C; `StatGuard.run`/`read` y auditoría `STAT_EXPORTACION` solo con nombres. (3) `StatSeries`: plantilla, vista previa sin escrituras, `STAT_SERIES_IMPORT` todo o nada, correspondencias confirmadas (la sugerencia de DI-13 nunca se aplica sola), anulación, comparación y suma solo pedida y sin solapamiento. (4) `Xlsx`/`XlsxReader` con la JDK (DI-12 sin dependencia). (5) Cierre: un ZIP a la vez en la instancia (`zipSlot`, 429 sin cola); `siger.stats.export.max-rows` = 30.000 con `SIGER_STATS_EXPORT_MAX_ROWS`; índice por COM de las tablas del corte (sugerencias `INDEX` en `StatSource`); registro de fases `StatExport.PHASES`, inactivo fuera de la medición, sin log ni auditoría.
- Archivos clave: `V24__series_y_exportacion.sql`, `StatExport.java`, `StatSeries.java`, `Xlsx.java`, `XlsxReader.java` (nuevos), `StatSource.java`, `StatCuts.java`, `StatEngine.java`, `Workflow.java` (solo el despacho de `STAT_SERIES_*`), `Api.java`, `application.properties`; `StatExportTest` (13), `StatSeriesTest` (5), `XlsxTest` (4), `StatTimeoutHttpTest` (+1), `StatCutHttpTest` (+1), `StatLoadMeasureTest` (`medicionE11`), `InstitutionalReaderTest` e `ImmutabilityTest` (comandos nuevos), pruebas de esquema 24; `docs/CONTRATO_EXPORTACION_SERIES_0_8_0.md`, `CLAUDE.md`, `docs/ARQUITECTURA.md`, PLAN (sección 9, dos filas).
- Migraciones: V24 (complemento del corte, `stat_series`, `stat_series_value`; esquema 24), sin siembra.
- Verificación (Claude Code, `D:\Ensayo-E11d`, una vez y en secuencia, de 11:15 a 11:37): JUnit H2 (`clean test`) 378 ok, 2 omitidas (antes 353 y 1; la corrida de Codex, 373 y 2); MySQL 8.4.13 desechable nuevo en `D:\Ensayo-E11d\mysql` (datadir y tmpdir ahí, `127.0.0.1:3417`; suite con `siger.mysql.suite=true` y `-Dsiger.stats.instance-drive=D:/`; `MySql84UpgradeTest` desde 0.4.0 y `MySql84Upgrade070Test` desde 0.7.0 hasta V24, con `communication_original` y `external_*` idénticos) 382 ok, 2 omitidas; frontend typecheck ok, 183 ok (sin cambio), build ok; humo 21/21 con el JAR nuevo (perfil local, H2 en `backend/target/humo-e11`, puerto 18643; carpeta borrada y proceso detenido); `Check-Scripts.ps1` (powershell.exe 5.1, rutas nativas de módulos, sin Maven ni npm en paralelo): 40 scripts, 73 órdenes en 60 procesos. Las 2 omitidas son `StatLoadMeasureTest.medicion` y `medicionE11` (sin `-Dsiger.stats.measure=true`). MySQL detenido con `mysqladmin shutdown` ("Shutdown complete"), puerto 3417 sin escucha; MySQL80 y la instancia de `D:\sigervalidacion` intactos.
- Medición final (Claude Code, MySQL 8.4.13, 50.005 COM, `-Xmx1024m`, plazo 30 s; `D:\Ensayo-E11c\medicion-final.txt`): `STAT_CUT_CREATE` de la Cuenta Pública 12,7 s (25.084 COM congeladas; criterio <= 13 s). ZIP de la Cuenta Pública desde la consulta vigente 13,5 s (consulta 1,3; microdatos 8,5; CSV 0,8; XLSX 2,3; compresión 0,7), 15,6 MB; desde el corte recién generado 6,5 s (microdatos 2,1) (criterio <= 25 s). Memoria después de recolectar: máximo 460 MiB, 45 % de 1.024 MiB (criterio <= 75 %). Dos ZIP simultáneos: uno en 13,5 s y el otro con 429 en menos de 1 ms. Dos años (unos 49.000 folios, estimados) responden 409 por el límite. La medición previa a las correcciones está en el registro del GO (detención).
- Diagnóstico de la consulta sobre el corte (EXPLAIN, `D:\Ensayo-E11c\explain`): aisladas, las cuatro proyecciones sobre el corte tardaban 1,8 s; en la medición, la de actuaciones no terminaba (subfases de `StatExport.PHASES`). Causa: InnoDB recalcula en segundo plano las estadísticas de una tabla recién llenada (una tabla cada unos 10 s; `mysql.innodb_table_stats` mostró `stat_export_cut_folio` y `stat_export_cut_origin` actualizadas después del ZIP fallido) y, con las tablas del corte tenidas por vacías, el optimizador entraba a `stat_cut_event`, `stat_cut_action` y `stat_cut_action_institution` por el prefijo `cut_id` de la clave primaria y recorría todo el corte por cada COM. Reproducido con las estadísticas en cero: actuaciones 55,8 s y clasificaciones más de 180 s; con la sugerencia `INDEX` del índice por COM, 0,12 s y 1,2 s. Corrección solo en `StatSource`, sin índices nuevos (los índices ya existían en V23); resultado sin cambios (equivalencias y matriz sin tocar aserciones).
- Evidencia de mutación: Claude Code repitió sobre el código final las 46 de Codex y agregó 10 (ZIP exclusivo, su liberación, el límite y su valor configurable, los tres índices del corte, (h) y dos del barrido): 56 de 56 fallaron en la prueba prevista, cada archivo restaurado y comprobado por SHA-256 (`D:\Ensayo-E11c\mutaciones`). Tabla abajo; las de Codex, con la observación sobre su corrida, en el registro del GO.
- Matriz (a)-(h): la del registro del GO (punto 3), sin cambio; (a)-(g) de Codex, (h) y el barrido de Claude Code.
- Condiciones de la sección 2: `git diff v0.7.0` vacío para `scripts/`, `.mvn/` y los archivos de recepción del Formulario (`Formulario*.java`, `ExternalOriginal`, `ExternalProjection`, `Documents`); `IntakeCommands`, `RequirementCommands`, `Access`, `Reports` y `Territory` sin cambios; V1-V23 sin cambios; ninguna escritura en `communication_original` ni `external_*`; sin dependencias nuevas; auditoría solo con nombres y códigos; TI sin acceso. No viajan archivos sensibles: las claves del MySQL desechable y los registros quedan en D:, fuera del repositorio.
- Facilidad de uso: no aplica (servidor); rechazos en texto llano ("Ya se está generando un paquete ZIP en SIGER...", "El paquete supera el máximo de 30.000 folios. Acota el periodo o los filtros.", "Este corte se generó antes de incorporar los datos del paquete ZIP...").
- Definiciones usadas: DI-12 (sin dependencia) y DI-13 (sugerencia «Metropolitana» -> `OFICINA_NACIONAL`, solo con confirmación).
- Desvíos del plan: (1) E11 lo implementó Codex (sesión de 08:50 a 09:58, sin commit por falta de cuota): implementación, sus 46 mutaciones (09:23-09:34) y sus corridas finales (09:40-09:56, evidencia en `D:\Ensayo-E11`). Claude Code lo resguardó en `wip/e11-codex` y lo cerró: puntos 1 a 4 del encargo (revisión de las mutaciones de Codex, omitidas explicadas, (h) y barrido, medición), detención, decisión del responsable, correcciones, nueva medición, 56 mutaciones y corridas finales (evidencia en `D:\Ensayo-E11c` y `D:\Ensayo-E11d`); el humo de Codex dejó de valer al cambiar `src/main` y se repitió. (2) La COM A de la población de `StatExportTest` lleva una ficha de ingreso con RUN, nacimiento y contactos sintéticos para el barrido (sin cambiar aserciones). (3) `ImmutabilityTest` agrega `STAT_SERIES_IMPORT` y `STAT_SERIES_ANNUL` a la categoría `STATISTICS`; las pruebas de esquema 23 -> 24 solo cambian el número (`FormularioServiceTest`, `MySql84IntegrationTest` ×2, `MySql84UpgradeTest`); ninguna aserción se eliminó. (4) `medicionE11` conserva sus dos aserciones (corte <= 13 s y ZIP < 30 s, ahora solo para los ZIP generados) y agrega los criterios de la decisión.
- Diferencias de referencia: DISENO 7 y 8: el ZIP solo para medidas de requerimientos computables (C); un ZIP a la vez y 30.000 folios (detención); el complemento del corte en tablas propias de V24 en vez de columnas de V23.
- Hallazgos para gates siguientes: una tabla que se llena y se lee en seguida (como las del corte) se puede leer en MySQL con estadísticas de tabla vacía: E14 debe vigilar planes con estadísticas recién recalculadas o fijar el índice. `StatExport.PHASES` y `medicionE11` (`-DargLine=-Xmx1024m`, resultado en `target/medicion-e11.txt`) sirven a E14. E13 debe mostrar en texto llano el 429 del ZIP y el 409 del límite.
- Pendientes: borrar `D:\Ensayo-E11`, `D:\Ensayo-E11c` y `D:\Ensayo-E11d` (MySQL detenidos; datos sintéticos y las claves de los usuarios desechables) -> responsable; ZIP escrito por flujo a un temporal -> 0.9.0 (sección 9 del plan); microdatos de actuación y de Caso -> 0.9.0.
- Verificación manual sugerida al usuario: `.\mvnw.cmd -B -ntp -f backend\pom.xml test -Dtest=StatExportTest,StatSeriesTest,XlsxTest,StatCutHttpTest,StatTimeoutHttpTest`; en local, con REPORTES y EXPORTACION, `POST /api/stats/export/zip` con la consulta de `POST /api/stats/query`.

Mutaciones de E11 sobre el código final (regla -> prueba que falló):

| Regla | Prueba que falló |
|---|---|
| 01 cifras del mismo motor | StatExportTest#figuraUnoReproducible |
| 02 duplicadas cuentan D25 | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 03 invalidas fuera D25 | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 04 folio unico | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 05 derecho en REQ | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 06 casos distintos de una clasificacion | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 07 menciones de una clasificacion | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 08 estado de REQ sin valores | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 09 constancia reservada fuera | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 10 excluir grupos del paquete sin facultad | StatExportTest#gruposPrioritariosYAlcance |
| 11 negar ZIP por cruce de grupos prioritarios | StatExportTest#gruposPrioritariosYAlcance |
| 12 negar ZIP por filtro sensible | StatExportTest#gruposPrioritariosYAlcance |
| 13 alcance de exportacion principal | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 14 alcance del corte de comparacion | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 15 ZIP requiere medida reproducible | StatExportTest#derechoConTresOrigenesYCuatroFolios |
| 16 V23 ZIP 409 explicito | StatExportTest#corteV23SeExportaAgregadoYDetectaCambiosEnSuComplemento |
| 17 huella del complemento recalculada | StatExportTest#complementoConListaCerradaYProvenienciaQueSeVerifica |
| 18 huella manifiesto por archivo | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 19 barrido de contenido de archivos | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 20 cupo compartido de exportacion | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 21 motor deshabilitado protege exportacion | StatExportTest#primerCorteYComparacionExigenMateriaYCupo |
| 22 plazo de resultado completo | StatTimeoutHttpTest#exportacionVencidaNoAbreUnaDescargaYLiberaCupo |
| 23 auditoria solo nombres | StatExportTest#exportacionHistoricaConLaMismaReglaYAuditoriaSinValores |
| 24 XLSX protege prefijos de formula | XlsxTest#idaYVueltaDeterministaConTextoProtegido |
| 25 XLSX fechas internas fijas | XlsxTest#idaYVueltaDeterministaConTextoProtegido |
| 26 limite de entradas XLSX | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 27 limite descomprimido XLSX | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 28 limite de filas XLSX | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 29 compresion relativa por entrada XLSX | XlsxTest#relacionDeCompresionPorEntradaAunqueElZipCompletoEstePorDebajo |
| 30 XLSX rechaza formula | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 31 XLSX rechaza DTD sintacticamente valida | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 32 XLSX rechaza relacion externa incluso relativa | XlsxTest#rechazaArchivosFueraDeLimitesYContenidoActivo |
| 33 preview no escribe | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 34 homologacion historica requiere confirmacion | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 35 totales invalidos rechazan la importacion entera | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 36 no sumar periodos solapados | StatSeriesTest#tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion |
| 37 no sumar sin solicitud | StatSeriesTest#sumaExplicitaPeriodosSeparadosConTotalesSinDuplicar |
| 38 series visibles solo bajo ambito | StatSeriesTest#facultadesDeSedeYAnulacionConservaFilas |
| 39 anulacion comprueba revision | StatSeriesTest#facultadesDeSedeYAnulacionConservaFilas |
| 40 consulta de exportacion solo POST | StatCutHttpTest#exportacionYSeriePorSusRutasHttp |
| 41 diccionario conserva la descripcion de cada item | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 42 diccionario explica la reserva | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 43 diccionario explica el total unico | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 44 diccionario identifica el riesgo residual | StatExportTest#huellasExactasYContenidoSinDatosCiudadanos |
| 45 importacion en el ambito de gestion | StatSeriesTest#facultadesDeSedeYAnulacionConservaFilas |
| 46 aviso de ambito usa cobertura efectiva | StatSeriesTest#comparacionConSigerAvisaSoloCuandoDifiereElAlcance |
| 47 un ZIP a la vez en la instancia | StatExportTest#unZipALaVezEnLaInstancia |
| 48 el cupo del ZIP se libera en finally | StatExportTest#unZipALaVezEnLaInstancia |
| 49 limite de folios del paquete | StatExportTest#limiteDeFoliosDelPaquete |
| 50 limite de 30.000 con SIGER_STATS_EXPORT_MAX_ROWS | StatExportTest#limiteDeFoliosDelPaquete |
| 51 corte: indice por COM de los eventos | StatExportTest#proyeccionesSobreElCorteFijanElIndicePorCom |
| 52 corte: indice por COM de las actuaciones | StatExportTest#proyeccionesSobreElCorteFijanElIndicePorCom |
| 53 corte: indice por nota de las instituciones | StatExportTest#proyeccionesSobreElCorteFijanElIndicePorCom |
| 54 (h) el manifiesto declara cada archivo | StatExportTest#archivoAlteradoDentroDelZipNoCoincideConSha256Sums |
| 55 barrido: encabezados de las hojas XLSX | StatExportTest#barridoDeDatosCiudadanosEnTodosLosArchivosYHojas |
| 56 barrido: fecha de nacimiento compacta | StatExportTest#barridoDeDatosCiudadanosEnTodosLosArchivosYHojas |

### E12 - Explorador estadistico: tablas, filtros, porcentajes, graficos y comparaciones (interfaz)   (CERRADO)
- Fecha: 2026-11-26 (commit `gate E12: explorador`, `git merge --squash` de `wip/e12-claude`, rama conservada), DDN-TEST-SIGER (Windows), Claude Code; base `ef12cf4` (E11 `251ac09` más los documentos del explorador).
- GO del responsable: A-E y condiciones 1-10 (tabla de decisiones, «GO de E12»), más la autorización para retomar tras el corte por memoria (resguardo, validez de las mutaciones, primer plano, navegadores por PID).
- Hecho: `#estadisticas` (`features/stats/`, menú «Explorador estadístico» con `can_report`): Panorama (seis indicadores con variación con flecha y texto, base 0 = «sin base de comparación», línea mensual y clic a Explorar; Cuenta Pública en curso hasta ayer en hora de Chile, el 1 de julio la recién terminada), Informes (Figura 1 con todas las categorías de `/api/catalog` con 0, CSV y XLSX por desglose), Explorar (preguntas guiadas, campos por familia con buscador y marcas «valores múltiples» y «no aditiva», filas y columnas por botón o arrastre, filtros como chips, cifra, modos, comparaciones, propuestas aparte, jerarquía de derechos con una consulta por nivel y vistas guardadas). Resultado con gráfico sugerido, tabla, «Datos de las HH:MM», Actualizar, «Cómo leer esta cifra» y pie de fuente. Fila de consultas una a la vez con caché de 5 min; gráficos SVG propios con descarga PNG y SVG; paleta `--chart-*` validada.
- Archivos clave: `frontend/src/features/stats/{Stats,Panorama,Informes,Explore,FieldPicker,ResultView,ResultTable,SavedViews}.tsx`, `statsModel.ts`, `statQuery.ts`, `components/charts/{Chart.tsx,chartModel.ts,chartExport.ts}`, `App.tsx`, `app/menu.ts`, `styles/{tokens,screens}.css`; pruebas `tests/{stats,charts}.test.ts`, `stats-render.test.mjs`, `stats-fixture.mjs`, `menu.test.ts`; `CLAUDE.md`, `docs/ARQUITECTURA.md`, PLAN sección 9 («Figura 1 en un solo libro XLSX (cuatro hojas)»).
- Migraciones: no aplica (esquema 24).
- Verificación: JUnit H2 y MySQL no aplican (`git diff ef12cf4 -- backend` vacío; base 378 H2 y 382 MySQL de E11); frontend typecheck ok, 215 ok (antes 183), build ok; humo 74/74 con el JAR nuevo (perfil local, H2 en `backend/target/humo-e12` nueva, puerto 18643; catálogos de referencia activados por TI, 10 COM sintéticas, consultas armadas con `statsModel.ts`; carpeta borrada y proceso detenido); `Check-Scripts.ps1` (powershell.exe 5.1, rutas nativas de módulos, sin Maven ni npm en paralelo): 40 scripts, 73 órdenes en 60 procesos.
- Capturas (fuera del repositorio): `D:\Ensayo-E12\capturas`, escritorio (1366×900) y móvil (390×844) de Panorama, Informes y Explorar (inicio, cruce con % por fila, vía, derechos y meses comparados), U7/U13 (`descargas\` con el PNG y el SVG, ambos con título y fuente) y el PNG descargado visto en el navegador; 18 archivos.
- Evidencia de mutación: 38 de 38 detectadas (tabla abajo), cada archivo restaurado y comprobado por SHA-256; recorridos U abajo.
- Condiciones de la sección 2: `git diff v0.7.0` vacío para `scripts/`, `.mvn/` y la recepción del Formulario; `backend/` (también V1-V24 y `Reports`) sin cambios respecto de `ef12cf4`; `package.json` y `package-lock.json` sin cambios (sin dependencias nuevas); la dirección solo lleva códigos (sin `q`, sin el texto del buscador ni datos personales); nada en `localStorage`; humo con datos sintéticos.
- Facilidad de uso: recorridos U medidos en acciones (abajo); barridos de render sin códigos ni SQL (texto, `aria-label` y `title`), botones con nombre, tablas con título y encabezados, gráficos con nombre y la tabla como alternativa; el estado nunca solo por color; móvil en una columna con Explorar simplificado (consulta y resultado antes que los campos); `prefers-reduced-motion`; contraste probado (gráficos ≥ 3:1 y texto ≥ 4,5:1).
- Definiciones usadas: DI-13 (adscripción institucional en la Figura 1); DI-01 a DI-04 (catálogos de referencia en pruebas y humo).
- Desvíos del plan: (1) Corte por memoria: el sistema detuvo la corrida de mutaciones en segundo plano durante la 28. Causas: 8 procesos de Chrome sin ventana colgados de la captura de vista previa (el lanzador termina antes que el navegador y `kill` no alcanzó su árbol) y la mutación 28, cuya prueba esperaba sin fin. `statsModel.ts` quedó mutado y se restauró; resguardo en `wip/e12-claude` (`wip:`, sin cierre). (2) El ejecutor de mutaciones no aplicaba las de varias líneas por los saltos CRLF de la copia de trabajo: ahora sigue el salto de línea del archivo. (3) Se repitieron las 14 mutaciones de `statsModel.ts` (04, 06, 09-14, 20 y 23-27), porque ese archivo se restauró a mano después del corte; las otras 7 de la primera corrida (03, 05, 15, 18, 19 y 21a-22b) valen, porque sus archivos solo los escribió el ejecutor al restaurarlos con el SHA-256 comprobado. (4) Pruebas reforzadas después de mutar, sin cambiar código: tope de 1 s en la cancelación (la 28 fallaba quedando en espera) y la marca «valores múltiples» exigida en cada campo del selector (la 30 no se detectaba; repetida y detectada). (5) `menu.test.ts`: la lista esperada de la cuenta con Reportes agrega `estadisticas` (reemplazada, no borrada). (6) El script de capturas dejó 9 procesos colgados en su primera corrida final; se cerraron por PID (solo los del perfil `D:\Ensayo-E12\chrome-perfil`), el script ahora cierra en `finally` su PID con su árbol y los `chrome.exe` de su perfil, y se repitió sin procesos colgados.
- Diferencias de referencia: DISENO 10.3: la comparación se grafica con barras pareadas (sin barras de diferencia; la diferencia va en la tabla); compartir una vista es directo, sin otro diálogo (U8 en 4 acciones); la adscripción de la Figura 1 lista las sedes regionales no proyectadas de `/api/catalog`; el circular también se niega para un eje de periodo y para porcentajes; con más de 8 columnas no se ofrecen barras agrupadas ni líneas (solo 8 colores distinguibles: pasar el campo a filas o usar la tabla).
- Hallazgos para gates siguientes: `statQuery.queue.run` sirve a E13 para que las descargas (ZIP) esperen su turno en la fila de la pestaña; `errorText` ya muestra en lenguaje llano el 429 del ZIP y el 409 del límite; `stats.compares` publica `CORTE`, que E12 no ofrece (E13); `completeFilters` y `howToRead` sirven al panel de Calidad. Chrome sin ventana en Windows: el lanzador termina y el navegador queda con otro padre; se cierra por PID y por el perfil propio. Una mutación que deja una promesa sin resolver cuelga `node --test`: las pruebas de cancelación llevan un tope.
- Pendientes: borrar `D:\Ensayo-E12` (capturas, descargas y perfil de Chrome sintéticos) -> responsable, después de revisar las capturas; E13 (ZIP, cortes, series, Calidad y filtro de pendientes); Figura 1 en un libro XLSX -> 0.9.0.
- Verificación manual sugerida al usuario: `cd frontend; node --test tests/stats.test.ts tests/charts.test.ts tests/stats-render.test.mjs`; en local, con Reportes: menú «Explorador estadístico», Informes y, en Explorar, «Por vía de ingreso» > «Descargar PNG».

Recorridos U de E12 (acciones desde la entrada al explorador; elegir una opción de un selector cuenta como una acción):

| U | Máximo | Recorrido | Acciones |
|---|---|---|---|
| U1 Figura 1 | 3 | 1. pestaña Informes: la Figura 1 de la última Cuenta Pública completa con sus cuatro desgloses | 1 |
| U2 Comparar | 2 desde un resultado | 1. «Comparar con» > «Periodo anterior» | 1 |
| U3 Cruce con % por fila | 6 | 1. Explorar; 2. «Consulta vacía»; 3. «Filas» de Tipo de solicitud; 4. abrir la familia Territorio; 5. «Columnas» de Región del requirente; 6. Cifra > «% por fila» | 6 |
| U7 Gráfico y PNG | 3 | 1. Explorar; 2. «Por vía de ingreso» (gráfico sugerido a la vista); 3. «Descargar PNG» | 3 |
| U8 Guardar y compartir | 4 | 1. «Guardar vista»; 2. nombre; 3. Confirmar; 4. «Compartir» en la lista | 4 |
| U11 Indicador a % por fila | 4 | 1. clic en un indicador del Panorama (abre Explorar con su consulta); 2. Cifra > «% por fila» | 2 |
| U12 Por qué no suma | 0 | la nota «Los niveles inferiores no suman el superior» está a la vista bajo el gráfico y en «Cómo leer esta cifra» | 0 |
| U13 Gráfico para el informe | 2 | 1. «Descargar PNG» (o SVG), con título, subtítulo, notas y fuente | 1 |

Mutaciones de E12 (regla -> prueba que falló; R = repetida sobre el código final; «render» = `stats-render.test.mjs`):

| Regla | Prueba que falló |
|---|---|
| 01 circular para una dimensión múltiple (R) | charts.test: circular: una dimensión de valor único, sin columnas... |
| 02 circular con columnas (R) | charts.test: circular |
| 03 circular con más de 6 categorías | charts.test: circular |
| 04 márgenes sumados en el cliente (R) | stats.test: tabla: categorías del servidor, 0 en la celda vacía...; render: resultado de un cruce |
| 05 niveles de derechos tomados de una sola consulta | render: U12: la jerarquía de derechos explica a la vista |
| 06 «Sin subcategoría» omitida (R) | stats.test: jerarquía de derechos; render: U12 |
| 07 dos consultas a la vez (R) | stats.test: fila de consultas: una a la vez, caché de 5 minutos... |
| 08 caché que confunde cuerpos (R) | stats.test: fila de consultas |
| 09 aviso sin traducir (R) | stats.test: cada aviso del servidor en una frase, sin su código |
| 10 pie de fuente sin alcance (R) | stats.test: pie de fuente: institución, fecha y hora de Chile, alcance... |
| 11 código visible en la tabla (R) | stats.test: tabla; jerarquía de derechos |
| 12 caracterización completa sin la región (R) | stats.test: caracterización completa: cada filtro sale de las opciones de /api/catalog... |
| 13 la dirección pierde los filtros (R) | stats.test: dirección: ida y vuelta solo con códigos... |
| 14 separador de miles incorrecto (R) | stats.test: formato chileno; cada aviso del servidor en una frase |
| 15 gráfico sin nombre accesible | render: resultado de un cruce; una dimensión de valor único |
| 16 mensaje genérico ante 429 (R) | stats.test: mensajes de 429 y 503 en lenguaje llano |
| 17 circular cuyas partes no suman el total (R) | charts.test: circular |
| 18 apiladas con columnas múltiples | charts.test: apiladas solo si las columnas son de valor único... |
| 19 periodo sugerido como barras | charts.test: sugerencia según la forma |
| 20 variación con base 0 como 100 % (R) | stats.test: variación: ... con base 0 dice «sin base de comparación» |
| 21a barras que no parten en 0 (escala) | charts.test: las barras siempre parten en 0 |
| 21b barras que no parten en 0 (dibujo) | render: una dimensión de valor único: barras desde 0 |
| 22 contraste insuficiente en la paleta | charts.test: paleta de tokens.css: contraste 3:1 y 4,5:1 |
| 22b paleta del modelo distinta de tokens.css | charts.test: paleta de tokens.css |
| 23 caché vencida servida (R) | stats.test: fila de consultas |
| 24 «hoy» y «ayer» en la zona del navegador (R) | stats.test: «hoy» y «ayer» en hora de Chile aunque el navegador esté en otra zona |
| 25 región de los NNA sin «no aditiva» (R) | stats.test: «Cómo leer esta cifra»; render: Explorar: campos por familia con sus marcas |
| 26 Figura 1 sin las categorías en 0 (R) | stats.test: Figura 1: todas las categorías del catálogo vigente con 0 |
| 27 1 de julio sin la Cuenta Pública recién terminada (R) | stats.test: periodo del Panorama |
| 28 cancelar no cancela las pendientes | stats.test: fila de consultas (con el tope de 1 s) |
| 29 celda vacía del cruce como raya | stats.test: tabla |
| 30 sin la marca «valores múltiples» en los campos (R) | render: Explorar: campos por familia con sus marcas (prueba reforzada) |
| 31 «Cómo leer» sin la nota de valores múltiples | stats.test: «Cómo leer esta cifra» |
| 32 explorador visible sin can_report | menu.test: Explorador estadístico (E12): solo con can_report...; TI: solo administración |
| 33 la misma consulta pendiente se repite | stats.test: fila de consultas |
| 34 porcentaje de caracterización completa mal dividido | stats.test: caracterización completa |
| 35 «Cómo leer» sin la región no aditiva | stats.test: «Cómo leer esta cifra»; render: resultado de un cruce |
| 36 dirección que acepta valores alterados | stats.test: dirección |
