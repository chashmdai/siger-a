# SIGER 0.8.0 - contrato de `POST /api/stats/query`

Contrato del motor estadístico (gate E08; D25-D41 y D43; `DISENO_0_8_0.md` 4.4, 4.7 y 5). E10 a E13 lo usan sin cambios;
cualquier cambio posterior se registra como desvío. Código: `StatEngine` (validación, cálculo y auditoría) y `StatSource`
(fuente única). Las medidas, dimensiones y opciones vigentes las publica `GET /api/catalog` en `stats`.

## 1. Acceso

- Solo cuentas con REPORTES vigente, en su alcance. TI nunca (D2), ni con una concesión inconsistente. Sin facultad: 403.
- CSRF como todo POST (403 sin token), `application/json` (415 con otro tipo), GET 405 con `Allow: POST`.
- Petición inválida 400; límite técnico de celdas 409. Un rechazo no consulta ni audita `STAT_CONSULTA`.
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
| `cut_id` | Reservado a E10; en E08 solo `null`. |
| `compare` | `null`, `{"type":"PERIODO_ANTERIOR"}` o `{"type":"MISMO_PERIODO_ANIO_ANTERIOR"}`. `CORTE` lo agrega E10. |
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
  `COMUNA_HECHOS`, `AMBITO` (de referencia vigente), `SEDE_INICIAL`, `SEDE_VIGENTE`, `TERRITORIO_COBERTURA`, `REGION_SEDE`,
  `UNIDAD`, `NIVEL`.
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

## 8. Respuesta

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
- `Reports` cuenta las COM por fecha de registro y ámbito de origen. Con fecha de registro y alcance institucional,
  `INGRESOS + EXCLUIDOS + ANTECEDENTES` por canal coincide con su volumen; `REQ_CONSTITUIDOS` por estado coincide con sus REQ.
