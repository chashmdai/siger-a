# SIGER 0.8.1 - bitacora de gates

Estado vivo del trabajo de `docs/PLAN_0_8_1.md` (el diseno de referencia es la seccion 3 del plan). Cada sesion de
Claude Code o Codex toma el primer gate ABIERTO, lo cierra aqui con el formato de la seccion 7 del plan, hace commit y
push, y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado, con commit y push), BLOQUEADO (detenido o a la espera de una
definicion; ver su registro), POSTERGADO (fuera del cierre por decision expresa del responsable), EXTERNO (lo realiza
el responsable fuera del ejecutor; el ejecutor lo salta y nunca edita su registro ni su estado).
Cada sesion empieza con el prompt universal de la seccion 8 del plan, sin nombrar gates.
F03 depende del ciclo de F02 cerrado (hoy, F02c CERRADO): mientras la ultima revision del ciclo no este CERRADO, la
sesion que encuentre F03 como primer ABIERTO verifica esa dependencia, informa y se detiene sin editar.

## Estado

| Gate | Titulo | Estado |
|---|---|---|
| F01 | Rediseno visual del explorador estadistico e iconografia (frontend, version y documentos) | CERRADO |
| F02 | Revision visual del responsable y auditoria de Claude Padre | BLOQUEADO |
| F02b | Correcciones de la revision visual de F02 | CERRADO |
| F02c | Nueva revision visual y auditoria de F02b | CERRADO |
| F03 | Build de entrega y etiqueta `v0.8.1` | ABIERTO |

## Decisiones del responsable

| Decision | Antes de | Respuesta |
|---|---|---|
| Plan 0.8.1: D52-D57, prefijo F, gates F01-F03 y prompt universal | F01 | 2026-11-27: aprobado por el responsable al versionar este documento. Se mantienen la autorizacion individual (GO) de cada gate y todas las condiciones de seguridad, pruebas y auditoria. |
| Dependencia nueva de frontend (DI-12 de 0.8.0) | F01 | 2026-11-27: autorizada solo `lucide-react` (ISC), con version exacta, a traves de `components/Icon.tsx` (D54). Ninguna otra. |
| Paquete y produccion | F03 | 2026-11-27: paquete propio y etiqueta `v0.8.1`; el paquete 0.8.0 queda inmutable; la actualizacion de produccion es el paso U81 del responsable (D57). |

## Definiciones pendientes

Ninguna. Una sesion que necesite una definicion la propone aqui y se detiene.

| Id | Definicion | Quien | Antes de | Respuesta |
|---|---|---|---|---|

## Antecedentes para F01

- Base: `main` en `4b482be` (`docs: respuesta de DI-11`), posterior a `v0.8.0`. Produccion en 0.8.0.
- Causa del defecto de las tarjetas: seccion 1 del plan (el `transform` de `button:active` de `base.css`, linea 71,
  reduce la capa `::after` de `.stat-card-link` al tamano del titulo al presionar).
- Pruebas de render existentes que miran el Panorama: `tests/stats-render.test.mjs` (cuenta seis `stat-card`) y
  `tests/statsE14-render.test.mjs` (usa `IndicatorCard`).
- Observacion del responsable que origina 0.8.1: las tarjetas solo redirigen al presionar el titulo, el explorador no
  transmite el nivel del proyecto y los iconos actuales no ayudan. El responsable pide el rediseno visual integral de
  las cinco secciones del explorador, no solo la correccion de las tarjetas (D52).

## GO aprobados pendientes de ejecucion

Plan presentado por el ejecutor y GO del responsable para un gate que se ejecuta en una sesion posterior. El estado
del gate no cambia (sigue ABIERTO) hasta su registro de cierre; la sesion que lo ejecute toma de aqui el plan y las
condiciones del GO, verifica que la base no haya cambiado y no necesita volver a pedir el GO.

(Sin entradas.)

## Registros de cierre

### F01 - Rediseno visual del explorador estadistico e iconografia   (CERRADO)
- Fecha: 2026-11-27 (commit `gate F01: rediseno visual del explorador estadistico e iconografia`), DDN-TEST-SIGER, Claude Code; base `a47350b`
- GO del responsable (2026-11-27): `lucide-react@1.71.0` exacta; `AGENTS.md` remite a `CLAUDE.md`; LICENSE integro de lucide (ISC + MIT de Feather) y OFL de Cabin; division F01a/F01b solo si no cabia (no hizo falta); pestanas solo con `scrollLeft` (ajuste 5); SVG exportado comparado con 0.8.0 (ajuste 6); Calidad sin consulta ni calculo nuevo (ajuste 7). Instruccion posterior: iterar el diseno en el navegador (Frontend Design, Impeccable, Playwright) sin cambiar las condiciones.
- Hecho: D53 tarjeta-enlace (`<a href={cardHref(q)}>` en toda la superficie, sin capa `::after`; error = `<article>` con Reintentar); D56 tono (`variationTone`, `TONE_NOTE`); Ingresos como indicador principal 2x2 con columnas mensuales; pestanas segmentadas con icono (`tabScroll`); Explorar con mosaicos, iconos por familia, "En uso", zonas de destino, boton "Campos" en movil, banda del resultado e iconos de archivo; Informes con encabezado, total, numeracion y barra de datos (`databarWidth`); Cortes y series como tarjetas; Calidad con leyenda (`legendStates`); `Chart.tsx` solo dibujo (realce, ayuda emergente, entrada, area degradada, rejilla punteada) y `exportMarkup`; tablas (encabezado fijo, alternas, realce); `Icon.tsx` sobre lucide (82 nombres, 49 de 0.8.0 + 33); version 0.8.1; documentos de la seccion 4 y `AGENTS.md`.
- Archivos clave: `frontend/src/components/Icon.tsx`, `charts/Chart.tsx`, `features/stats/{Panorama,Stats,Explore,FieldPicker,ResultView,ExportBar,Informes,Quality,CutsSeries}.tsx`, `statsModel.ts`/`qualityModel.ts` (solo funciones nuevas), `styles/{tokens,screens}.css`; pruebas `tests/statsF01.test.ts`, `statsF01-render.test.mjs`, `icons-render.test.mjs`, `chart-export-fixture.mjs`, `chart-export-080.json` (referencia generada con el `Chart.tsx` de v0.8.0 antes de editarlo); `docs/LICENCIAS_TERCEROS.md` y `docs/PRUEBAS_MANUALES_0_8_1.md` (nuevos).
- Migraciones: no aplica (esquema 24).
- Verificacion (evidencia en `D:\Ensayo-F01`): JUnit H2 388 ok, 4 omitidas (antes 388 y 4; `final-h2.log`); MySQL no aplica; frontend typecheck ok, 269 ok (antes 252; las 252 sin cambios), build ok; `npm ci` en la copia `ci\` acepta `package-lock.json` sin cambiarlo; `Check-Scripts.ps1` (powershell.exe 5.1) 40 scripts, 81 ordenes en 63 procesos (igual que E14: U81 no agrega ordenes); humo con el JAR (perfil local, H2 en `D:\Ensayo-F01\humo`, puerto 18643): HTTP 9/9 y Chrome sin ventana con perfil propio: en las seis tarjetas, clic sostenido (CDP) en la cifra, la linea mensual y el pie abre su consulta; Tab + Enter en orden; clic central abre otra pestana; movil sin desplazar la pagina. Servidor y Chrome cerrados por PID en `finally`, carpeta de humo y perfil borrados, sin procesos propios.
- Capturas (`D:\Ensayo-F01\capturas`, 26, sin datos ciudadanos): panorama, informes, explorar-inicio, cortes-y-series y calidad en 1440 y 390; explorar-{barras,circular,lineas,columnas,agrupadas,apiladas,pareadas} en 1440 y 390; panorama-cargando-1440 y panorama-foco-teclado-1440. Iteracion de diseno en `D:\Ensayo-F01\iter`.
- Registro F01: `lucide-react` 1.71.0 (publicado 2026-11-10; integrity `sha512-8YtvF+bq...Tar4zA==`), `peerDependencies` `react ^16.5.1 || ^17.0.0 || ^18.0.0 || ^19.0.0` y `@types/react *` (opcional). `dist` antes: JS 726.686 B (gzip 220.291), CSS 89.233 B (gzip 18.060); despues: JS 757.013 B (gzip 229.867, +9.576 B < 25 KB), CSS 107.781 B (gzip 21.172). Iconos con trazo propio: ninguno.
- Evidencia de mutacion (`mutaciones\resultado.txt`): M1 boton del titulo -> (a) D53; M2 sentido de tardios -> D56 (pura y render); M3b `aria-hidden="false"` -> iconos; M4 clase en el SVG exportado -> (f); M5 tono favorable a Ingresos -> D56; M6 boton dentro de la tarjeta -> (a); M7 transform en `:active` sobre un descendiente -> guardia CSS; M8 `--stat-databar` bajo 4,5:1 -> D55; M9 lucide fuera de `Icon.tsx` -> iconos; M10 `scrollIntoView` -> ajuste 5; M11 href sin consulta -> D53; M12 tarjeta en error como enlace -> (a). M3 (quitar `aria-hidden`) es equivalente: lucide pone `aria-hidden="true"` por defecto sin nombre accesible.
- Condiciones de la seccion 2: `git diff v0.8.0` vacio en `backend/src/main`, `scripts/`, `.mvn/`, migraciones, `chartModel.ts`, `chartExport.ts` y `charts.test.ts`; `pom.xml` solo 0.8.0 -> 0.8.1; modelos del explorador sin lineas eliminadas; ninguna prueba existente modificada; `requestBody` y las consultas de cada pantalla sin cambio; sin datos personales.
- Facilidad de uso: iterado en el navegador (Playwright) a 1440 y 390 px con critica por captura (se retiro una franja del emblema que duplicaba la de la barra lateral; Ingresos paso a columnas mensuales rotuladas; banda del resultado sin repetir el periodo); textos visibles con ortografia completa; nada depende del hover (ayuda emergente = nombre de la marca y tabla).
- Definiciones usadas: ninguna.
- Desvios del plan: (1) iconos `TriangleExclamationPoint`, `ClockExclamationPoint` y `RotateCcwClock` en lugar de `TriangleAlert`, `ClockAlert` e `History` (no existen en 1.71.0); (2) licencia ISC + MIT de Feather (D54 nombraba solo ISC); (3) `AGENTS.md` fuera de la seccion 4 (autorizado en el GO); (4) sin conteo animado de la cifra (opcional); (5) mutacion M3 equivalente, reemplazada por M3b; (6) `ALCANCE_Y_PENDIENTES.md` y `README.md` decian produccion en 0.7.0: se alinearon con la seccion 1 del plan (0.8.0 desde el 2026-11-26, U7 pendiente).
- Hallazgos para gates siguientes: Calidad no calcula una cifra global de completitud (ajuste 7: no se agrego); las capturas de pagina completa deben tomarse desde arriba (los elementos fijos salen corridos si la pagina esta desplazada); `npm ci` en F03 necesita el registro npm para `lucide-react`.
- Pendientes: revision visual y auditoria del diff `v0.8.0..<commit de F01>` -> F02 (EXTERNO); paquete, `SOURCE_SHA256.txt` y etiqueta `v0.8.1` -> F03; actualizacion de produccion -> U81 (responsable).
- Verificacion manual sugerida al usuario: `docs/PRUEBAS_MANUALES_0_8_1.md` (secciones 1 y 2: clic en la cifra de cada tarjeta, Tab + Enter, otra pestana y colores de la variacion) y las capturas de `D:\Ensayo-F01\capturas`.

### F02 - Revision visual del responsable y auditoria de Claude Padre   (BLOQUEADO)
- Fecha: 2026-11-27; registrado por encargo de documentacion del responsable (commit `docs: revision F02 y gate F02b`).
- Auditoria de codigo de `9277b0d` (Claude Padre): conforme, con las condiciones de la seccion 2 del plan verificadas.
- Revision visual del responsable: con observaciones, que toma F02b (detalle en `docs/PLAN_0_8_1.md`, seccion 6, F02b):
  (1) Informes: cada grafico debe leerse; a 1440 px ningun texto del grafico bajo 10 px efectivos; tabla y grafico lado a
  lado solo si el grafico tiene al menos 560 px, si no, el grafico bajo la tabla a todo el ancho. (2) Panorama: ninguna
  tarjeta con una franja vacia; la linea mensual crece para ocupar el alto disponible, con la ayuda y el pie abajo; linea,
  area y punto con el color de acento de su tarjeta, contraste >= 3:1 probado. (3) Graficos a 390 px en Explorar, Informes
  y Calidad: si se desbordan en horizontal, aviso visible "Desliza para ver el grafico completo" y difuminado en el borde;
  sin cambios en `chartModel.ts`, `chartExport.ts` ni el SVG descargado. (4) Menores: el comentario de `tokens.css` cita
  `tests/tokensF01.test.ts`, que no existe (la prueba esta en `statsF01.test.ts`); `TONE_NOTE` debe nombrar tambien el
  ambar de los registros tardios.
- Siguiente: F02b (ABIERTO, correcciones, pruebas con mutacion y capturas en `D:\Ensayo-F02b`); despues F02c (EXTERNO).

### F02b - Correcciones de la revision visual de F02   (CERRADO)
- Fecha: 2026-11-27 (commit `gate F02b: correcciones de la revision visual de F02`), DDN-TEST-SIGER, Claude Code; base `a6a988e`
- GO del responsable (2026-11-27): (1) opcion A: lado a lado solo si el grafico cabe a su tamano natural (720 px mas el marco); si no, bajo la tabla, hasta 840 px; umbral con `@container` alineado con la constante de `statsModel.ts`; (2) ajuste aprobado de `statsE13-render.test.mjs:121-122`; (3) acento de la linea >= 3:1 sobre el fondo real de cada tarjeta, incluido `--stat-hero-bg`, y punto redondo en Chrome a 1440 y 390 px con capturas; (4) `ChartScrollHint` desconecta su `ResizeObserver` y su escucha pasiva al desmontarse, el aviso no tapa rotulos ni cifras, va con `aria-hidden` y nada entra al SVG descargado; (5) `TONE_NOTE` con verde y ambar en los dos indicadores con juicio.
- Hecho: (1) Informes: `.stat-breakdown-box` con `@container desglose (min-width: 1094px)` = `BREAKDOWN_SIDE_MIN` (tabla 340 + 16 + 720 + 18), columnas `minmax(340px, 1fr) 738px`; bajo el umbral, una columna de 858 px con el grafico hasta 840 px (tabla y grafico alineados); `breakdownLayout`/`effectiveText` y constantes nuevas en `statsModel.ts`. (2) `Spark` en `currentColor` con `data-accent` en la tarjeta y tokens `--stat-spark-{com,req,cas,act,ok,warn}`; `.stat-card-trend.spark` crece (`flex: 1 1 auto`) y el SVG la llena; punto final = trazo de largo cero con extremo redondo y `non-scaling-stroke`; el esqueleto de carga tambien llena la tarjeta; Ingresos conserva sus columnas. (3) `Chart.tsx`: `chartScroll`, `watchScroll`, `ScrollHintView` y `ChartScrollHint` (aviso sobre el lienzo y difuminado `fade-start`/`fade-end` en `.stat-chart-frame`, fuera del SVG). (4) Comentario de `tokens.css` y `TONE_NOTE`.
- Archivos clave: `frontend/src/components/charts/Chart.tsx`, `features/stats/{Informes,Panorama}.tsx`, `statsModel.ts` (solo funciones y constantes nuevas, y el texto de `TONE_NOTE`), `styles/{screens,tokens}.css`; pruebas nuevas `tests/statsF02b.test.ts` y `statsF02b-render.test.mjs`; `CLAUDE.md` y `docs/PRUEBAS_MANUALES_0_8_1.md` (seccion 6).
- Migraciones: no aplica (esquema 24).
- Verificacion (evidencia en `D:\Ensayo-F02b`): JUnit H2 392 ejecutadas, 388 ok y 4 omitidas (antes 388 y 4; `final-h2.log`, con el JAR del gate); MySQL no aplica; frontend typecheck ok, 280 ok (antes 269, sin cambios salvo la asercion de E13), build ok; `Check-Scripts.ps1` (powershell.exe 5.1) ok, 40 scripts y 81 ordenes en 63 procesos, igual que F01 (se edito `CLAUDE.md`, guia enlazada, sin ordenes nuevas); humo con el JAR (perfil local, H2 en `D:\Ensayo-F02b\humo`, puerto 18643, Chrome sin ventana con perfil propio): HTTP 9/9, regresion de F01 (clic sostenido en cifra, linea y pie de las seis tarjetas, Tab + Enter, clic central, movil) y F02b: Informes a 1440 px con los cuatro graficos bajo la tabla de 840 px y texto minimo efectivo de 12,83 px; a 1920 px lado a lado con el grafico a 720 px (11 px); Panorama a 1440 y 390 px sin franja (linea a ayuda = separacion de la tarjeta) y contraste medido req 4,81, cas 6,24, act 5,87, ok 5,37, warn 5,33; aviso y difuminado en Explorar, Informes y Calidad a 390 px, al final solo a la izquierda y la ultima marca entera, sin desplazar la pagina (`humo-f02b-mediciones.txt`). Servidor y Chrome cerrados por PID en `finally`; carpeta de humo y perfil borrados; sin procesos propios.
- Capturas (`D:\Ensayo-F02b\capturas`, 37, sin datos ciudadanos): las 26 de F01 (cinco secciones y cada tipo de grafico en 1440 y 390, Panorama cargando y con foco) mas informes-1920, punto-{act,warn}-{1440,390} (x4) y aviso-{explorar,informes,calidad}-390 con su -final. Iteracion en `D:\Ensayo-F02b\iter`.
- Tamano de `dist`: antes (F01) JS 757.013 B (gzip 229.867), CSS 107.781 B (gzip 21.172); despues JS 758.536 B (gzip 230.334: +467 B; +10,0 KB respecto de 0.8.0, < 25 KB), CSS 109.708 B (gzip 21.535).
- Evidencia de mutacion (`mutaciones\resultado.txt`): M1 umbral de 560 px (constante y CSS alineados) -> (1) layout; M2 `@container` desalineado -> (1) CSS; M3 linea de 48 px fija -> (2) CSS; M4 linea con `CHART_COLORS[0]` -> (2) render; M5 `--stat-spark-warn` bajo 3:1 -> (2) contraste; M6 fin de desplazamiento invertido -> (3) `chartScroll`; M7 aviso que no se muestra -> (3) `ScrollHintView`; M8 `TONE_NOTE` sin ambar -> (4); extra: M9 sin `disconnect` y M10 escucha no pasiva -> (3) `watchScroll`.
- Condiciones de la seccion 2: `git diff v0.8.0` vacio en `backend/src/main`, migraciones, `scripts/`, `.mvn/`, `chartModel.ts`, `chartExport.ts` y `charts.test.ts`; `pom.xml` solo 0.8.0 -> 0.8.1; modelos del explorador sin lineas eliminadas respecto de v0.8.0 (`TONE_NOTE` es de F01); sin dependencias nuevas (`package.json` y `package-lock.json` sin cambios); SVG exportado igual a la referencia de 0.8.0; sin datos personales.
- Regla 6 (prueba existente ajustada): `statsE13-render.test.mjs:121-122` fijaba "lado a lado desde 1.200 px" (`@media`), la regla visual que reemplaza la observacion 1; ahora fija la regla nueva (`@container desglose`, 1094 px, columnas 340/738). Aprobado en el GO.
- Facilidad de uso: iterado en el navegador (Playwright) a 1440, 1920 y 390 px con critica por captura (la tabla a todo el ancho alejaba las cifras de su categoria: se alineo con el grafico en una columna de 858 px); punto revisado ampliado x4; textos con ortografia completa.
- Definiciones usadas: ninguna.
- Desvios del plan: (1) umbral de Informes mas exigente que 560 px (opcion A del GO, por la discrepancia informada: con 560 px el texto se veria a 8,6 px); (2) tabla y grafico apilados en una columna de 858 px (no a todo el panel), por lectura; (3) el aviso tambien aparece en los graficos de Cortes y series (mismo componente); (4) mutaciones extra M9 y M10.
- Hallazgos para gates siguientes: a 1440 px Informes queda apilado (lado a lado desde unos 1.505 px de ventana: el desglose mide 1.030 px a 1440 px y crece con ella); Playwright MCP solo escribe en `C:\sigera\.playwright-mcp` (ignorada por git): las capturas se movieron a D:.
- Pendientes: nueva revision visual y auditoria del diff `9277b0d..<commit de F02b>` -> F02c (EXTERNO); paquete y etiqueta -> F03; produccion -> U81 (responsable).
- Verificacion manual sugerida al usuario: `docs/PRUEBAS_MANUALES_0_8_1.md`, seccion 6, y las capturas de `D:\Ensayo-F02b\capturas`.

### F02c - Nueva revision visual y auditoria de F02b   (CERRADO)
- Fecha: 2026-11-27; registrado por encargo de documentacion del responsable (commit `docs: revision F02c conforme`).
- Auditoria de codigo del diff `9277b0d..ba39b7e`: conforme.
- Las condiciones de la seccion 2 fueron verificadas por Claude Padre: sin cambios en servidor, `scripts/`, `.mvn/`, `chartModel.ts`, `chartExport.ts` ni dependencias.
- `statsModel.ts` solo agrega funciones y cambia el texto de `TONE_NOTE`.
- El ajuste de `statsE13-render.test.mjs` es el autorizado en el GO.
- Revision visual de las capturas de `D:\Ensayo-F02b\capturas`: conforme.
  - Informes legible a 1440 px (apilado) y a 1920 px (lado a lado).
  - Panorama sin franjas vacias y con el color de acento de cada tarjeta.
  - Aviso y difuminado a 390 px.
- Observacion sin bloqueo, para 0.9.x: a 1440 px, la columna apilada de Informes (858 px) deja vacio el costado derecho del panel.
- El ciclo de F02 queda cerrado. El commit auditado para F03 es `ba39b7e`.
