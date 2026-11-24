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
| E00 | Linea base, decisiones, AGENTS.md y contrato del Formulario (documentos y pruebas) | ABIERTO |
| E01 | Volcado sintetico 0.7.0 y catalogos estadisticos versionados (servidor) | ABIERTO |
| E02 | Administracion: catalogos de referencia, interfaz de TI y facultad GESTION_ESTADISTICA | ABIERTO |
| E03 | Normalizacion integral (servidor): persona canonica, ficha 1.2 y ficha de ingreso de la COM manual | ABIERTO |
| E04 | Normalizacion integral (interfaz): componentes compartidos, ficha 1.2 y COM manual | ABIERTO |
| E05 | Clasificacion estadistica de la COM: modelo, via, propuestas y trazabilidad (servidor) | ABIERTO |
| E06 | Derechos, contexto, caracterizacion y actuaciones tipificadas (servidor) | ABIERTO |
| E07 | Dimensiones territoriales y demograficas, completitud y reglas de cierre (servidor) | ABIERTO |
| E08 | Motor estadistico: fuente unica, agregacion, series temporales y comparaciones (servidor) | ABIERTO |
| E09 | Captura estadistica en el flujo (interfaz): caracterizacion, derechos, actuaciones y cierre | ABIERTO |
| E10 | Cortes estadisticos y configuraciones guardadas (servidor) | ABIERTO |
| E11 | Exportaciones CSV, XLSX y ZIP e importacion de series historicas (servidor) | ABIERTO |
| E12 | Explorador estadistico: tablas, filtros, porcentajes, graficos y comparaciones (interfaz) | ABIERTO |
| E13 | Explorador: exportacion, cortes, series historicas y calidad (interfaz) | ABIERTO |
| E14 | Integracion MySQL, rendimiento, aceptacion y cierre de version | ABIERTO |
| E15 | Validacion de uso con la Direccion de UEP | EXTERNO |
| E16 | Auditoria independiente | EXTERNO |
| E17 | Build de entrega, ensayo de actualizacion y etiqueta | ABIERTO |

Todo gate depende de E00 y de su contrato del Formulario (seccion 3 del plan). Un gate ABIERTO cuyas dependencias
(columna "Depende de" de la seccion 6 del plan) no estan CERRADO se detiene en sus precondiciones. E16 empieza con el
ciclo de E15 cerrado (E15 o su ultima repeticion CERRADO), y E17 con ese ciclo y la ultima auditoria CERRADO (seccion 5
del plan). Los pendientes operacionales postergados no tienen gate en 0.8.0 (seccion 9 del plan).

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

## Definiciones pendientes

Cada definicion se responde aqui (fecha y respuesta) antes del gate o paso indicado. "Activacion" es el paso de
produccion en que TI activa los catalogos; no bloquea el desarrollo, que usa versiones PROPUESTA. "0.9.0" indica una
definicion ya respondida que se conserva para la hoja de ruta (seccion 9 del plan).

| Id | Definicion | Quien | Antes de | Respuesta |
|---|---|---|---|---|
| DI-01 | Validacion de los catalogos version 1 de tipo de solicitud, tipo de requirente y via (incluido si "integrante de la Defensoria" es una via y "actuacion de oficio" un requirente) | Institucion (UEP) | Activacion | |
| DI-02 | Contenido de los catalogos de contexto, de caracterizacion de la vulneracion y de tramos de edad (TRAMO_EDAD) | Institucion | Activacion | |
| DI-03 | Catalogos de tipo de actuacion, tipo de institucion y relacion con el NNA (propuesta en `deploy/catalogos/`, E02) | Institucion | Activacion | |
| DI-04 | Confirmacion del modelo de derechos vigente (version 2.0 de 2022, 4 ejes y 17 derechos; rotulos del Informe Anual 2026), nivel de clasificacion (derecho o subcategoria) y uso de "Rol del Estado" y grupos prioritarios | Institucion | Activacion | |
| DI-05 | Distribucion territorial del Formulario: quien distribuye en la sede (Coordinacion regional, funcion de distribucion con ambito u otra), regla de territorio (region vigente y cobertura), respaldo central, devolucion, avisos y COM sin region | Responsable con la institucion | 0.9.0 | 2026-11-24: aprobada la distribucion territorial por coordinacion regional, con respaldo central, visibilidad nacional para la distribucion central, comunicaciones sin region a cargo del nivel central, avisos territoriales y devoluciones a la bandeja correspondiente. Ante concurrencia, prevalece la primera operacion valida. |
| DI-06 | Acto que define la apertura efectiva de una sede y si puede diferir de la activacion | Responsable | 0.9.0 | 2026-11-24: aprobadas tres fechas independientes: apertura proyectada, apertura efectiva y activacion en SIGER. TI declara la apertura efectiva al activar la sede, con la fecha actual por defecto y posibilidad de modificarla. |
| DI-07 | Reapertura con proposito de registro estadistico: si su ciclo se excluye del tramo `REQ_TOTAL` (cambio del motor de tiempos) o se informa aparte | Responsable | E05 | 2026-11-24: conservar integramente el mecanismo de reapertura y el motor de tiempos existentes. No crear modalidades especiales de reapertura ni modificar `REQ_TOTAL`. La trazabilidad retrospectiva queda en E05. |
| DI-08 | Rotulo de la region de la ficha ("Region de atencion" hoy), sin cambiar D21 | Responsable | E04 | 2026-11-24: "Region y comuna del requirente", sin modificar D21. |
| DI-09 | Textos de asunto por tipo de aviso y uso del enlace directo (requiere acceso por red y `siger.mail.link`) | Responsable | 0.9.0 | 2026-11-24: aprobados los asuntos diferenciados segun los eventos existentes, folio, descripcion breve y enlace directo cuando este habilitado el acceso por red. Sin datos personales. |
| DI-10 | Renovacion del certificado: autofirmado renovado o PFX institucional; autorizacion de TI para el firewall al pasar a red (solo para la ejecucion en produccion) | Responsable con TI | 0.9.0 | 2026-11-24: desestimada. Se mantienen el certificado y la configuracion HTTPS actuales. Sin gate. |
| DI-11 | Alcance de la clasificacion retrospectiva en produccion (desde que fecha, que registros, quien) | Responsable con la institucion | Paso U | |
| DI-12 | Autorizacion de una dependencia XLSX (`org.dhatim:fastexcel`) solo si el escritor o lector propio no basta | Responsable | E11, si se pide | |
| DI-13 | Correspondencia entre la "region de origen" publicada en el Informe Anual y las dimensiones territoriales de SIGER | Institucion | E15 | |
| DI-14 | Funcionarios de UEP que participan en la validacion de uso | Responsable con UEP | E15 | |
| DI-15 | Version efectiva de produccion para el ensayo de actualizacion (0.6.0 o 0.7.0, segun el corte del 07/12) | Responsable | E17 | |

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

## Registros de cierre

(Sin registros.)
