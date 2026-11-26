# SIGER 0.8.0 · Exportaciones y series históricas

## Descargas estadísticas

`POST /api/stats/export/{csv|xlsx|zip}` recibe la petición de `POST /api/stats/query`. Requiere sesión, CSRF,
`application/json` y EXPORTACION con REPORTES en los mismos ámbitos de concesión. El alcance de EXPORTACION debe
cubrir el de la consulta y los cortes comparados en ámbitos y materias. Una cuenta TI recibe 403. Las descargas son
adjuntos con `Cache-Control: no-store`. GET devuelve 405.

CSV y XLSX contienen el agregado del motor y sus comparaciones, con la misma metodología y cifras exactas. `tipo`
distingue `CELDA`, `TOTAL_FILA`, `TOTAL_COLUMNA`, `TOTAL_GENERAL` y las claves que aparecen solo en la comparación.
Las columnas enumeradas de fila y columna llevan código y rótulo. `n`, `unique`, `mentions`, numerador, denominador,
porcentaje, cifra base y variaciones conservan el sentido de la consulta interactiva. El CSV usa BOM UTF-8, `;`,
coma decimal y protección de fórmulas. XLSX guarda el texto como texto y protege los prefijos `=`, `+`, `-` y `@`.

El ZIP admite medidas de requerimientos computables. Si la medida requiere unidades de actuación, Caso, clasificación
o completitud, o mide excluidas o antecedentes, la descarga devuelve 400 y ofrece CSV o XLSX. El paquete contiene
`LEEME.txt`, `metadatos.json`, `agregado.csv`, `agregado.xlsx`, `microdatos.csv`, `microdatos_multivalor.csv`,
`diccionario.csv`, `catalogos.csv` y `SHA256SUMS.txt`. El libro incorpora las cinco tablas del paquete en hojas. El
manifiesto verifica los bytes de cada entrada, incluidas las comillas, BOM y saltos de línea donde corresponden.
Los miembros se crean en orden estable y con fechas internas fijas. El instante de `metadatos.json` cambia en cada
cálculo y cambia su huella en `SHA256SUMS.txt`.

El microdato corresponde a la consulta principal y contiene un folio por COM computable. Para cada dimensión múltiple
la fila informa su estado y cantidad de categorías. El archivo relacionado conserva una fila por folio, variable y
código con rótulo, nivel, `en_req`, cantidad de Casos distintos y menciones. En clasificaciones se guardan todas las
asignaciones en `menciones` y para reagregar la medida Menciones se cuenta un par COM–valor. Para personas,
actuaciones e instituciones se suman sus ocurrencias. Cuando una dimensión carece de valores, se usa el estado de la
fila principal y su número de menciones es cero. Los márgenes y totales únicos se recalculan sobre sus conjuntos.
Las constancias reservadas se excluyen después de seleccionar la constancia vigente. Se omiten nombres, RUN, documentos,
contactos, relatos y fechas de nacimiento. Los folios y la rareza de las combinaciones conservan riesgo residual de
reidentificación. El diccionario incluye la definición de cada ítem de catálogo disponible en el paquete.

Para grupos prioritarios, el ZIP exige la cobertura conjunta de REPORTES, EXPORTACION y REPORTES_PERSONAS en los
mismos ámbitos. Si falta esa cobertura y la petición usa la dimensión o su estado en filas, columnas, filtros o
menciones, devuelve 403. Si la petición no los usa, el ZIP omite el estado, la cantidad y los valores del microdato
y el diccionario lo explica. Las descargas agregadas CSV y XLSX conservan la regla de la consulta interactiva.

La consulta y las lecturas de la exportación comparten `StatGuard.run` y `read`, REPEATABLE READ y un plazo común.
El servidor devuelve 429 si falta cupo y 503 si vence el plazo, sin entregar un adjunto parcial. Después de generar
un archivo íntegro se audita `STAT_EXPORTACION`, solo con nombres de medida, dimensiones, filtros, modo y formato,
o con el identificador de serie y formato. La cancelación se registra por `STAT_CONSULTA_CANCELADA`. La cantidad de
sentencias de microdatos se mantiene fija al crecer la población.

Un solo paquete ZIP a la vez en la instancia (decisión del responsable sobre la detención de E11, 2026-11-26): el ZIP
toma un cupo propio, además del de `StatGuard`, sin cola; otro ZIP, de cualquier cuenta, recibe de inmediato 429 «Ya
se está generando un paquete ZIP en SIGER. Espera a que termine y vuelve a intentarlo.», sin auditoría. El cupo se
libera al terminar, también si la descarga falla. CSV, XLSX y las consultas no usan ese cupo y siguen funcionando
mientras corre un ZIP.

`siger.stats.export.max-rows` (variable `SIGER_STATS_EXPORT_MAX_ROWS`) es el máximo de folios de un ZIP: 30.000. Por
sobre él la descarga responde 409 «El paquete supera el máximo de 30.000 folios. Acota el periodo o los filtros.», sin
archivo. Fundamento: el volumen real es de unos 30 requerimientos por día hábil (unos 600 al mes y 7.200 al año; menos
de 15.000 al año con picos estacionales), de modo que 30.000 folios cubren unos 4 años, más de 2 con picos, y la
Cuenta Pública cabe con holgura; se fijó sin calibrar 40.000.
CSV y XLSX agregados no tienen ese límite. `siger.stats.export.max-relations` (500.000) limita las filas del archivo
relacionado y también responde 409.

## Cortes de V23 y V24

Los cortes V23 admiten CSV y XLSX. Para ZIP se devuelve 409 porque sus filas no guardan el folio ni la procedencia
de cada valor. Desde V24, la generación guarda folios y procedencia por evento o actuación, identificados como
REQ o CAS y su id, en `stat_export_cut_folio` y `stat_export_cut_origin`. El complemento se congela en la misma
transacción, sin cascadas, y las columnas tienen lista cerrada. `stat_export_cut_integrity` guarda `export_sha256`.
La huella usa la serialización canónica del corte con estas dos tablas, en ese orden, y orden de clave dentro de
cada tabla. La verificación recalcula el complemento y publica `export_ok` y `export_sha256`. Ambos son nulos en
V23. La huella `rows_sha256` conserva su definición de seis tablas y se verifica como en E10.

Sobre un corte, las proyecciones de microdatos (y las uniones del motor con las tablas congeladas) fijan con la
sugerencia `INDEX` el índice por COM de cada tabla (`ix_stat_cut_event_com`, `ix_stat_cut_action_com`,
`ix_stat_cut_case_com` e `ix_stat_cut_institution_note`). Causa (EXPLAIN, cierre de E11): recién escrito un corte,
InnoDB todavía no recalcula las estadísticas de sus tablas (lo hace en segundo plano, una tabla cada unos 10 s) y,
con las tablas tenidas por vacías, el optimizador entraba por el prefijo `cut_id` de la clave primaria y recorría
todo el corte por cada COM. Sin la sugerencia, las actuaciones de la Cuenta Pública tardaban 55,8 s y las
clasificaciones más de 180 s; con ella, 0,12 s y 1,2 s. La sugerencia equivale a FORCE INDEX en MySQL, H2 la ignora
como comentario y no cambia el resultado.

Medición final de E11 en MySQL 8.4.13 con 50.005 COM sintéticas y la memoria de producción (`-Xmx1024m`):
`STAT_CUT_CREATE` de la Cuenta Pública 2025–2026, 12,7 s (25.084 filas congeladas). ZIP de la Cuenta Pública con
derechos por contexto: desde la consulta vigente, 13,5 s (consulta 1,3; microdatos 8,5; CSV 0,8; XLSX 2,3;
compresión 0,7) y 15,6 MB; desde el corte, recién generado, 6,5 s (microdatos 2,1). Memoria después de recolectar:
460 MiB como máximo (45 % de 1.024 MiB). Dos ZIP simultáneos: uno se genera en 13,5 s y el otro recibe 429 en
menos de 1 ms. Dos años del periodo sintético (unos 49.000 folios, estimados) responden 409 por el límite. Antes de estas
correcciones, dos ZIP simultáneos agotaban el plazo con 956 MiB después de recolectar y el ZIP desde un corte
recién generado agotaba el plazo (503).

## Series históricas

- `GET /api/stats/series/template/{csv|xlsx}` descarga la plantilla para GESTION_ESTADISTICA. Sus columnas son
  `periodo_desde`, `periodo_hasta`, `dimension`, `categoria` y `valor`.
- `POST /api/stats/series/preview` acepta `title`, `indicator`, `scope_id` opcional, `source`, `methodology`,
  `granularity`, `period_from`, `period_to`, `reason`, `format` (`csv` o `xlsx`) y `content` (bytes en base64).
  El ámbito es la institución o una sede y requiere la concesión de GESTION_ESTADISTICA en ese ámbito. Puede
  enviarse `columns` para relacionar cada campo de la plantilla con el encabezado del archivo y `mappings` como
  lista de objetos `{dimension, category, code}`. Con columnas propias, la respuesta puede indicar `needs_columns`
  y devuelve los encabezados para relacionarlos. La vista previa valida y devuelve los datos, la huella del archivo
  y posibles avisos, sin escrituras.
- `STAT_SERIES_IMPORT` recibe el mismo cuerpo en `data` de `/api/commands`. Revalida los datos, fechas, granularidad,
  enteros no negativos, solapamiento interno, duplicados y totales declarados. «Total» indica un total de periodo y
  dimensión. Las correspondencias se escriben solo si se envían expresamente. «Metropolitana» puede sugerir
  `OFICINA_NACIONAL` para `ADSCRIPCION_INICIAL`, sin cambiar el rótulo original de la serie. Se guardan la huella
  y los datos agregados validados. El archivo original no se conserva.
- `STAT_SERIES_ANNUL` recibe id, revisión y `reason`. Requiere GESTION_ESTADISTICA que cubra la serie. La anulación
  conserva valores y correspondencias. `GET /api/stats/series` y `GET /api/stats/series/{id}` exigen REPORTES que
  cubra el alcance de la serie. TI recibe 403. `POST /api/stats/series/{id}/export/{csv|xlsx}` exige además
  EXPORTACION con la misma cobertura y devuelve los valores originales y `codigo_siger` cuando se confirmó uno.
- `POST /api/stats/series/compare` recibe `{series_ids, sum?, query?}`. Con `query` trae la consulta SIGER en su
  propio resultado y avisa cuando el alcance difiere. Devuelve las series aparte y avisa del solapamiento. `sum`
  solo se aplica si es `true`, con series vigentes compatibles y periodos disjuntos. Se preserva la distinción
  entre los totales y las categorías para evitar doble conteo.

CSV admite UTF-8 con BOM opcional, `;` o `,`, y celdas entre comillas. Para XLSX se lee la primera hoja en orden del
libro. Se aceptan cadenas compartidas o en línea y fechas de planilla en celdas con formato fecha. Se rechazan
fórmulas, DTD, entidades externas, referencias externas y partes de macros. Límites de entrada bajo `siger.stats.series`
en bytes o cantidades. `max-file-bytes` (2.097.152), `max-rows` (20.000), `xlsx.max-entries` (96),
`xlsx.max-expanded-bytes` (33.554.432) y `xlsx.max-compression-ratio` (160). La importación requiere archivos de
agregados con etiquetas de categorías y sus conteos.
