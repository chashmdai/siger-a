# SIGER 0.8.0 - contrato de `POST /api/stats/query`

Contrato del motor estadístico (gate E08; D25-D41 y D43; `DISENO_0_8_0.md` 4.4, 4.7 y 5). E10 a E13 lo usan sin cambios;
cualquier cambio posterior se registra como desvío. E10 completó lo reservado (`cut_id`, comparación `CORTE`, secciones 11 a
13) y agregó la dimensión `ADSCRIPCION_INICIAL` (DI-13, cambio aditivo registrado como desvío). Código: `StatEngine` (validación, cálculo y auditoría) y `StatSource`
(fuente única). Las medidas, dimensiones y opciones vigentes las publica `GET /api/catalog` en `stats`.

## 1. Acceso

- Solo cuentas con REPORTES vigente, en su alcance. TI nunca (D2), ni con una concesión inconsistente. Sin facultad: 403.
- CSRF como todo POST (403 sin token), `application/json` (415 con otro tipo), GET 405 con `Allow: POST`.
- Petición inválida 400; límite técnico de celdas 409. Un rechazo no consulta ni audita `STAT_CONSULTA`.
- Límites de ejecución (E08b, `StatGuard`): orden de controles 403 (facultad), 503 (motor deshabilitado), 429 (sin cupo) y
  luego la consulta. Sin cupo (`siger.stats.max-concurrent`, 2 en total, y `max-concurrent-per-account`, 1 por cuenta) responde
  429 de inmediato en texto llano, sin cola, sin pedir conexión y sin auditar. Una consulta, su comparación y sus lecturas
  auxiliares comparten un plazo (`siger.stats.timeout-seconds`, 30 s) en una sola transacción REPEATABLE READ; al vencer se
  cancela la sentencia en el servidor, se revierte, responde 503 en texto llano sin resultados parciales y audita
  `STAT_CONSULTA_CANCELADA` con los mismos nombres de `STAT_CONSULTA` (o "Consulta cancelada antes de validarse") más
  "cancelada por tiempo (N s)". Si los archivos de MySQL (`@@datadir`, `@@tmpdir`, `@@innodb_temp_tablespaces_dir`) no están en
  la unidad de la instancia, el motor queda deshabilitado: 503 en texto llano y `stats.enabled` falso en `/api/catalog`.
- Alcance en SQL antes de contar (`Reports.reach`): una COM está en el alcance si su ámbito de referencia **vigente**
  (principal del REQ; sin REQ, destino de la SOL; sin SOL, ámbito de origen) está en él o, para UPR, UPD y UEP, si es una
  sede con esa materia (la del REQ; sin REQ, la sugerida de la SOL). El modo temporal cambia los valores, nunca el alcance.
- Lo que procede de un Caso (derechos y demás clasificaciones, actuaciones y los propios Casos) exige además que el Caso
  esté en el alcance, por su ámbito o por su materia en una sede.

## 2. Petición

Lista cerrada de campos; uno ajeno es 400. `rows` y `columns` son listas ordenadas de dimensiones distintas.

| Campo | Contenido |
|---|---|
| `measure` | Medida (sección 3). Obligatorio. |
| `rows`, `columns` | Dimensiones (sección 4). Vacías por defecto. |
| `filters` | `{DIMENSION: [valores]}`: O entre los valores de una lista, Y entre dimensiones (sección 6). |
| `period` | `type`, `from`, `to`, `date_basis` (sección 5). Obligatorio. |
| `time_mode` | `VIGENTE` (por defecto), `AL_INGRESO`, `AL_REQ`, `FECHA` (sección 7). |
| `as_of` | Solo con `FECHA`: ISO 8601 con zona u offset, no futura y no anterior al inicio del periodo. |
| `cut_id` | Corte que se consulta (sección 11); `null` = fuente vigente. |
| `compare` | `null`, `{"type":"PERIODO_ANTERIOR"}`, `{"type":"MISMO_PERIODO_ANIO_ANTERIOR"}` o `{"type":"CORTE","cut_id":n}` (sección 11). |
| `percent` | `null`, `TOTAL`, `FILA` o `COLUMNA`. |
| `include_proposals` | Booleano; agrega las propuestas pendientes de confirmar, aparte. |
| `mention_dimension` | Dimensión múltiple cuyas menciones se suman. Con una sola dimensión múltiple en filas o columnas es esa. |

```json
{
  "measure": "REQUERIMIENTOS_CON_VALOR", "rows": ["DERECHO"], "columns": [],
  "filters": {"CANAL": ["FORMULARIO_WEB_PUBLICO", "TELEFONO"]},
  "period": {"type": "CUENTA_PUBLICA", "from": "2025-07-01", "to": "2026-06-30", "date_basis": "RECEPCION"},
  "time_mode": "VIGENTE", "as_of": null, "cut_id": null, "compare": null,
  "percent": "TOTAL", "include_proposals": false, "mention_dimension": null
}
```

## 3. Medidas

`n` cuenta la unidad de la medida; `unique`, las COM distintas.

| Medida | Unidad de `n` | Población |
|---|---|---|
| `INGRESOS` | COM | Computables (D25): ni INVALIDA ni VINCULADA. Cuentan las duplicadas, pendientes y primeras atenciones. |
| `EXCLUIDOS`, `ANTECEDENTES` | COM | INVALIDA; VINCULADA. Informativas. Un vínculo rechazado sigue siendo antecedente hasta que se devuelve. |
| `REQ_CONSTITUIDOS` | REQ (uno por COM) | Con REQ. |
| `CASOS` | Caso | Casos dentro del alcance. |
| `ACTUACIONES` | Actuación | Constancias tipificadas vigentes y resultado de la primera atención. |
| `REQUERIMIENTOS_CON_ACTUACION` | COM | Con al menos una actuación. |
| `CLASIFICACIONES_DERECHO` | Asignación vigente por registro (REQ o Caso) | Computables con derechos. |
| `REQUERIMIENTOS_CON_DERECHO` | COM | Con al menos un derecho. |
| `REQUERIMIENTOS_CON_VALOR` | COM | Computables; exige una dimensión múltiple. Quien no tiene valores aparece en su estado. |
| `MENCIONES` | Mención | Computables. En clasificaciones, una combinación COM–valor; en personas, actuaciones e instituciones, las ocurrencias. |
| `COMPLETITUD` | Par COM–variable | Computables, sobre las seis variables de E07, con `VARIABLE` y `ESTADO_VARIABLE`. |

Actuaciones (D36): primero se determina qué constancia está vigente en el instante (sin rectificación hasta él) y después se
**excluyen las reservadas** (D41); la misma regla va en el diccionario de la exportación (E11). Una tarea es un compromiso y
no cuenta. El resultado de la primera atención sale solo de `requirement.immediate_result` (su constancia no se cuenta);
`REMISION_CONCLUIDA` conserva su código y suma al total. Los actos no tienen "valor al ingreso": en `AL_INGRESO` se informan
los vigentes.

Completitud: una cuenta de sede **puede ver PENDIENTE un derecho o contexto registrado solo en un Caso ajeno a su alcance**.
El cierre no cambia: `StatCompleteness` evalúa el expediente completo. Con alcance institucional coincide con
`StatCompleteness.indicators`.

## 4. Dimensiones

Cada COM tiene un valor o un estado en toda dimensión; ninguna queda fuera. Estados: `PENDIENTE` (sin evento o sin evidencia
en el instante), `SIN_INFORMACION`, `NO_DECLARADO`, `NO_APLICA` y `SIN_DESGLOSE` (derecho sin subcategoría).

- Periodo: `PERIODO_DIA`, `_MES`, `_TRIMESTRE`, `_SEMESTRE`, `_ANIO`, `_CUENTA_PUBLICA`, sobre la fecha de análisis.
- Cadena: `TARDANZA` (solo por recepción), `ESTADO_COM`, `ESTADO_SOL`, `ESTADO_REQ`, `ORIGEN_REQ`, `RESULTADO_PRIMERA_ATENCION`,
  `MODALIDAD`, `MATERIA`, `COMPLEJIDAD`. En `ESTADO_SOL`, `PENDIENTE` es la SOL por asumir y la falta de evidencia es
  `SIN_INFORMACION`.
- Ingreso: `CANAL`, `MECANISMO`, `VIA_INGRESO`, `VIA_AGRUPACION`, `TIPO_SOLICITUD`, `TIPO_REQUIRENTE`.
- Territorio: `REGION_REQUIRENTE`, `COMUNA_REQUIRENTE`, `REGION_ORIGEN` (D21), `REGION_NNA`*, `COMUNA_NNA`*, `REGION_HECHOS`,
  `COMUNA_HECHOS`, `AMBITO` (de referencia vigente), `SEDE_INICIAL`, `ADSCRIPCION_INICIAL`, `SEDE_VIGENTE`, `TERRITORIO_COBERTURA`,
  `REGION_SEDE`, `UNIDAD`, `NIVEL`.
- `ADSCRIPCION_INICIAL` ("Adscripción institucional", DI-13, E10): la primera adscripción acreditada en el instante de la
  consulta o del corte, con la misma regla temporal que `SEDE_INICIAL` en los cuatro modos. Si el primer ámbito de destino es
  una sede regional, el código de la sede; si es del nivel central, `OFICINA_NACIONAL` ("Oficina Nacional", una sola
  categoría para todo el nivel central); sin destino, `PENDIENTE` ("Pendiente de asignación"). Es de valor único: cada
  requerimiento computable cuenta una vez y el desglose suma el total. La homologación de la Oficina Nacional con
  "Metropolitana" rige solo al comparar con series históricas (E11); ningún rótulo de esta dimensión dice "Metropolitana". La
  región del NNA afectado (`REGION_NNA`) es otra dimensión, múltiple y no aditiva.
- E12b: para una COM desestimada sin SOL, el hito `COM_DESCARTADA` acredita el ámbito en `SEDE_INICIAL` y la sede
  regional u Oficina Nacional en `ADSCRIPCION_INICIAL`. A una fecha anterior al hito figura «Pendiente de asignación».
  Una COM con SOL devuelta conserva su primer destino. El ámbito de referencia para el alcance de REPORTES
  sigue la regla de la sección 1.
- Derechos y caracterización: `DERECHO_EJE`*, `DERECHO`*, `DERECHO_SUBCATEGORIA`* (una subcategoría implica su derecho y su eje),
  `CONTEXTO`*, `CARACTERIZACION`*, `ROL_ESTADO`*, `GRUPO_PRIORITARIO`*.
- Actuaciones: `TIPO_ACTUACION`*, `ORIGEN_ACTUACION`* y `TIPO_INSTITUCION`* ("Tipo de institución de las actuaciones", solo de
  `note_institution`; no son las instituciones de los hechos, que quedan en la hoja de ruta 0.9.0).
- Demografía: `EDAD_NNA`*, `TRAMO_EDAD_NNA`*, `NACIONALIDAD_NNA`*, `GRUPO_NACIONALIDAD_NNA`* y las mismas del requirente con
  sufijo `_REQUIRENTE` (solo con el tipo de requirente informado como persona natural adulta o NNA; si no, `NO_APLICA`).
- Estado de cada clasificación: `ESTADO_` + `TIPO_SOLICITUD`, `TIPO_REQUIRENTE`, `VIA_INGRESO`, `DERECHO`, `CONTEXTO`,
  `CARACTERIZACION`, `ROL_ESTADO` o `GRUPO_PRIORITARIO`.

(*) Múltiples. Sin dimensiones de funcionario. La fecha de nacimiento nunca sale. En las medidas de actuaciones o de derechos,
sus propias dimensiones son de la unidad (una actuación tiene un tipo y un origen) y las demás múltiples, de la COM.

Catálogos: valor y rótulo según la versión con que se registró; con varias, el rótulo de la más reciente y el aviso
`VERSIONES_DISTINTAS`. No se aplican correspondencias. `TRAMO_EDAD` usa siempre el vigente (`SIN_CATALOGO_VIGENTE` si no hay).

## 5. Periodos

- `PERSONALIZADO` exige `from` y `to`. `MES`, `TRIMESTRE`, `SEMESTRE`, `ANIO` y `CUENTA_PUBLICA` (julio a junio): `from`
  identifica el periodo; `to` se omite o coincide con su fin. Entre 2000 y 2099.
- Ambos días incluidos en America/Santiago: SQL recibe el inicio y la medianoche siguiente al último día, excluida. El día
  civil respeta los cambios de hora.
- `date_basis`: `RECEPCION`, `REGISTRO`, `CONSTITUCION_REQ` y, solo en `CASOS`, `CREACION_CASO`. Por defecto: recepción;
  `REQ_CONSTITUIDOS`, constitución; `CASOS`, creación del Caso.
- Los intervalos vacíos de un eje de periodo se entregan con cero.
- Registro tardío (solo por recepción): recibido en el periodo y registrado en o después de su fin (límite excluido).
  `late_records` trae el total y la distribución por días calendario de Chile entre recepción y registro; `null` en otra fecha.

## 6. Filtros

Valores de la lista o del catálogo de la dimensión (cualquier versión activada), estados especiales o, en la edad, enteros de
0 a 130. El periodo no se filtra. Un filtro sobre una dimensión múltiple selecciona las COM que tienen alguno de los valores
(y se ven todos sus valores); en una medida de actuaciones o de derechos, sus propias dimensiones filtran las unidades.

## 7. Modos temporales (D37)

| Modo | Valor |
|---|---|
| `VIGENTE` | El último. |
| `AL_INGRESO` | El primero registrado (la vía: la derivada del canal; sin ella, el primer evento). Territorio y cobertura, a la recepción. |
| `AL_REQ` | El vigente **al terminar el comando que constituyó el REQ** (primer `command_log` con ese REQ como resultado). |
| `FECHA` | El vigente en `as_of`, sobre las COM registradas hasta entonces; el estado D25 también es el de esa fecha. |

- `AL_REQ`: lo escrito por el comando que constituye el REQ (ficha, clasificación, derechos y resultado del acto) cuenta; lo
  del comando siguiente nunca, porque cada comando espera, con el lock global, a que el reloj supere el fin del anterior
  (`Workflow.afterPreviousCommand`). Con el reloj atrasado la espera termina en 200 ms. Una COM sin REQ queda `PENDIENTE`.
- Sin evidencia en el instante, `PENDIENTE`: nada se reconstruye. Complejidad, materia y modalidad valen en un instante solo
  si no hay después un cambio auditado (`REQ_COMPLEJIDAD`, `REQ_MATTER`, por código y nunca por texto; redirección de la SOL
  por hito) o una versión posterior de la ficha; si no, `PENDIENTE` con el aviso `HISTORIA_INCOMPLETA` y su cantidad.
- La región física de una sede es siempre la actual. El estado D25 de `AL_INGRESO` y `AL_REQ` es el vigente.
- En `FECHA`, `COM_DESCARTADA` cambia `ESTADO_COM` a inválida o duplicada desde su instante. Si había SOL devuelta,
  `ESTADO_SOL` pasa a descartada desde el mismo instante. El fundamento de la desestimación se lee en la ficha
  de la COM con sus permisos habituales y queda fuera de las consultas estadísticas y las exportaciones.

## 8. Respuesta

`GET /api/catalog` publica en `stats` además `enabled` (E08b, cambio aditivo autorizado): falso si el motor está
deshabilitado por el confinamiento de los archivos de MySQL. Desde E10, `compares` incluye `CORTE`.

`metadata` (metodología, medida, dimensiones, nombres de filtros, periodo canónico, zona, modo, `percent_unit`, menciones,
versiones de catálogo y hora del cálculo), `values` (claves y rótulos en orden), `cells`, `totals`, `comparison`,
`late_records`, `data_coverage`, `proposals` y `warnings`.

- Celda: `row`, `column`, `n`, `unique`, `mentions` (`null` sin dimensión de menciones), `percent_numerator`, `denominator` y
  `percent` (dos decimales; `null` con denominador cero o sin `percent`).
- Los márgenes y el total se calculan sobre sus propios conjuntos; **nunca son la suma de las celdas**.
- Porcentajes: en las medidas de COM y en `MENCIONES`, COM únicas de la celda sobre COM únicas del total o margen; en `CASOS`,
  `ACTUACIONES`, `CLASIFICACIONES_DERECHO` y `COMPLETITUD`, su propia unidad (`percent_unit`). Con dimensiones múltiples
  pueden sumar más de 100 % (`PORCENTAJES_MULTIVALOR`).
- `comparison`: periodo base y, por celda y total, `base_n`, `delta_n` y `delta_percent` (`null` con base cero). Las categorías
  se alinean por clave y los intervalos por posición; uno sin par conserva su propia clave.
- `data_coverage`: por cada dimensión demográfica consultada, requerimientos con dato, en que no aplica, total y porcentaje.
- `proposals`: por variable y código, las COM pendientes con propuesta. Una propuesta nunca es un valor (D28-D30).
- Avisos: `PORCENTAJES_MULTIVALOR`, `VARIABLES_PENDIENTES`, `VERSIONES_DISTINTAS`, `SIN_CATALOGO_VIGENTE`, `HISTORIA_INCOMPLETA`.

## 9. Cifras exactas y límite técnico

D41: se entregan las cifras exactas de toda consulta admitida, también las categorías con un solo requerimiento. No hay
supresión, redondeo de conteos ni límite de dimensiones. `siger.stats.max-cells` (20.000 por defecto) es solo una salvaguarda
técnica de la respuesta: cuenta las celdas de la consulta más las de su comparación, incluidos los intervalos vacíos; al
superarla responde 409 en lenguaje llano, sin resultados parciales.

## 10. Auditoría y conciliación

- `STAT_CONSULTA` (`SISTEMA`/0): medida, modo, nombres de dimensiones y de filtros y comparación. Nunca valores, fechas ni cifras.
- `STAT_CONSULTA_CANCELADA` (`SISTEMA`/0, E08b): los mismos nombres y el plazo, registrada después del rollback en otra
  transacción. Un fallo de esa auditoría queda en el log (solo el tipo de error) y no cambia la respuesta 503.
- `Reports` cuenta las COM por fecha de registro y ámbito de origen. Con fecha de registro y alcance institucional,
  `INGRESOS + EXCLUIDOS + ANTECEDENTES` por canal coincide con su volumen; `REQ_CONSTITUIDOS` por estado coincide con sus REQ.

## 11. Cortes (E10; D39, D43; `DISENO_0_8_0.md` 6)

Un corte congela la fuente del motor en un instante, dentro de su alcance y su periodo, y no se modifica ni se borra.

- **Generar** (`POST /api/commands`, `STAT_CUT_CREATE`, con su clave idempotente): `{title, scope_id?, period{type, from, to},
  reason}`, lista cerrada (otro campo, o `date_basis` en el periodo, es 400). Lo genera GESTION_ESTADISTICA que rige (con
  REPORTES en el mismo alcance) exactamente en `scope_id` (nulo = institución); TI nunca. Congela toda COM del alcance (el que
  da en vivo una concesión de REPORTES en `scope_id`, incluida su materia) cuya recepción, registro, constitución del REQ o
  creación de algún Caso cae en el periodo, en todos sus estados (computables, excluidas y antecedentes), con los valores
  vigentes en el instante del comando (`as_of`). Código `CORTE-AAAA-NNN`. Audita `CORTE_GENERADO` (`SISTEMA`/0) con el código,
  el código del alcance y el tipo de periodo; nunca el título, el fundamento ni valores.
- **Lo congelado** (lista cerrada de columnas, sin datos personales ni texto libre): `stat_cut_row` (una fila por COM: fechas de
  recepción, registro y constitución, estado D25, versiones y propuestas de las variables de valor único y una columna por
  cada dimensión de valor único) y las relaciones de entrada del motor: `stat_cut_event` (clasificaciones vigentes del REQ y
  de los Casos del alcance), `stat_cut_action` (constancias tipificadas vigentes **no reservadas** y resultado de la primera
  atención), `stat_cut_action_institution`, `stat_cut_person` (NNA afectados con su tramo de edad del catálogo vigente en el
  instante; nunca la fecha de nacimiento) y `stat_cut_case`. La cabecera guarda además las versiones VIGENTE de cada
  catálogo, la huella del calendario hábil y los rótulos de ámbitos y unidades del instante. Diferencia de referencia con
  DISENO 6 (`stat_cut_multi`), aprobada en el GO de E10.
- **Generar un corte detiene las operaciones unos segundos** (decisión A del responsable, 2026-11-26): el congelado corre dentro
  del comando, con el lock global, el cupo y el plazo de `StatGuard`. Mientras tanto, los comandos de los funcionarios esperan
  y terminan bien (MySQL espera un lock hasta `innodb_lock_wait_timeout`, 50 s por omisión y sin cambio en `my.ini`, más que el
  plazo de 30 s). **Se recomienda generar los cortes fuera del horario de atención.** Medición y proyección en la sección 13.
  Al vencer el plazo: rollback completo, 503 en texto llano, nada parcial y `STAT_CONSULTA_CANCELADA` ("Generacion de corte;
  cancelada por tiempo (N s)") después del rollback. Sin cupo: 429 de inmediato, sin escrituras.
- **Consultar** (`POST /api/stats/query` con `cut_id`): la misma petición sobre las filas congeladas, solo en el modo
  `VIGENTE` (otro modo es 400) y con un periodo dentro del periodo del corte (si no, 400); admite toda medida, dimensión,
  filtro, fecha de análisis, porcentaje y propuestas. `PERIODO_ANTERIOR` y `MISMO_PERIODO_ANIO_ANTERIOR` sobre un corte son
  400. Lee solo `stat_cut_*` y los ítems de catálogo (inmutables); los rótulos de ámbito y unidad, del tramo de edad y de las
  propuestas son los del instante del corte. Un corte reproduce exactamente la consulta vigente del mismo instante con su
  alcance (salvo `metadata.cut_id`, `metadata.as_of` = instante del corte y `metadata.calculated_ms`). `STAT_CONSULTA` agrega
  "sobre un corte". Un corte inexistente es 400; uno que la cuenta no cubre, 403.
- **Comparar** (`compare: {"type":"CORTE","cut_id":n}`): la consulta (vigente o sobre otro corte, en modo `VIGENTE`) contra el
  corte `n`, en el mismo periodo, por celdas y márgenes, sin folios. La consulta vigente que se compara con un corte se
  calcula **con el alcance del corte** (que la cuenta cubre), no con el de la cuenta (diferencia de referencia aprobada en el
  GO de E10). `comparison` agrega `cut_id` y `cut_code`.
- **Quién lo ve**: las cuentas con REPORTES cuyo alcance **cubre** el del corte: el alcance de la cuenta incluye el del corte
  en ámbitos y en materias (`Reports.reach` del corte ⊆ el de la cuenta), o la cuenta es institucional. Una lectura por
  materia sobre una sede no cubre el corte de esa sede completa. TI nunca.
- **Lecturas**: `GET /api/stats/cuts` (los que la cuenta cubre: código, título, alcance, periodo, instante, estado, filas,
  huella de filas, autor, revisión y `can_annul`) y `GET /api/stats/cuts/{id}` (además fundamento, versiones de catálogo,
  cantidades, huellas, metodología y anulación; 404 si no existe).
- **Verificar** (`POST /api/stats/cuts/{id}/verify`, CSRF, GET 405): recalcula las dos huellas desde lo guardado; responde
  `ok`, `rows_ok`, `metadata_ok` y las huellas guardadas, con el cupo y el plazo de `StatGuard`. Audita `CORTE_VERIFICADO`
  ("Corte X; integro" o "alterado").
- **Anular** (`STAT_CUT_ANNUL`, id = corte, su `revision` y `reason` obligatorio): GESTION_ESTADISTICA cuyo alcance cubre el
  del corte. Solo la cabecera pasa a `ANULADO` (`annulled_by`, `annulled_ms`, `annul_basis`, `revision`); filas y huellas se
  conservan y el corte se sigue consultando y verificando. Un corte ya anulado: 409. Audita `CORTE_ANULADO` ("Corte X").
- **Huellas**: `rows_sha256` = SHA-256 de la serialización canónica de las seis tablas congeladas, en este orden:
  `stat_cut_row`, `stat_cut_event`, `stat_cut_action`, `stat_cut_action_institution`, `stat_cut_person` y `stat_cut_case`. Por
  tabla, una línea `#tabla` y otra con sus columnas (sin `cut_id`, en el orden de `StatCuts.TABLES`) separadas por tabulador;
  luego cada fila ordenada por su clave (`communication_id`; `event_id`; `unit_id`; `institution_id`; `communication_id` y
  `ordinal`; `case_id`), con sus valores separados por tabulador: entero en decimal, nulo como `\N`, texto con `\`, tabulador,
  LF y CR escritos como `\\`, `\t`, `\n` y `\r`. Cada línea termina en LF; todo en UTF-8. El ordinal de un NNA afectado sale
  del orden de sus datos dentro de la COM (región, comuna, edad, nacionalidad y tramo; nulos primero; texto por código
  Unicode). `metadata_sha256` = SHA-256 del JSON canónico (claves ordenadas, sin espacios) de código, título, alcance,
  periodo, instante, metodología, versiones de catálogo, huella del calendario, dimensiones, rótulos, cantidades, `row_count`,
  **`rows_sha256`**, autor, instante de creación y fundamento. Dos cortes sin cambios entre ellos tienen la misma `rows_sha256`.

## 12. Configuraciones guardadas (E10)

- `STAT_VIEW_SAVE` (`{title, definition}`; id nulo = nueva, si no la propia con su `revision`): solo cuentas con REPORTES; la
  definición es una petición de la sección 2, validada como la consulta de la cuenta (400 o 403) sin ejecutarla, y se guarda
  en JSON canónico (hasta 20.000 caracteres). Sin cambios: 409.
- `STAT_VIEW_DELETE` (la propia, con su `revision`) y `STAT_VIEW_SHARE` (`{shared}` booleano; exige GESTION_ESTADISTICA que
  rija; audita `VISTA_COMPARTIDA` o `VISTA_NO_COMPARTIDA` con "Vista N", sin título). Solo el dueño edita, comparte o borra.
- `GET /api/stats/views`: las propias y las compartidas (estas, para toda cuenta con REPORTES), con `mine`, `can_edit` y
  `can_share`. Cada cuenta ejecuta una vista con su propio alcance; si apunta a un corte que no cubre, la consulta es 403.

## 13. Medición de la generación de un corte (E10)

MySQL 8.4.13 aislado (datadir y temporales en D:), 50.005 COM sintéticas de `StatLoadMeasureTest`, corte institucional de la
Cuenta Pública 2025-2026 con 25.084 COM congeladas (70.357 eventos, 11.355 actuaciones, 18.814 NNA, 9.557 Casos):
`STAT_CUT_CREATE` tardó 10,6 s con el lock global antes de agrandar los lotes de inserción (fila por COM: 6,0 s de consulta y
1,5 s de inserción; el resto, unos 3 s) y, con lotes de hasta 2.000 filas o 60.000 parámetros por sentencia, entre 10,6 y 11,0 s en dos corridas (los lotes más
grandes no cambian el resultado ni mejoran el tiempo de forma medible: la consulta de la fila por COM domina). Las
consultas sobre el corte tardaron entre 0,04 y 0,9 s (la misma consulta vigente, entre 0,1 y 2,7 s) y la verificación, 0,3 s.
**Proyección frente al plazo de 30 s**: el tiempo crece en forma aproximadamente lineal con las COM congeladas (unos 0,4 ms por
COM con esta población): unos 4 s con 10.000 COM, 10 s con 25.000, 20 s con 50.000; el plazo se alcanzaría con unas 70.000 COM
en un solo corte, y entonces la generación responde 503 sin escribir nada. Si el volumen se acerca a ese límite, la hoja de
ruta 0.9.0 prevé el corte sin lock global (instantánea consistente con bloqueo de milisegundos).
