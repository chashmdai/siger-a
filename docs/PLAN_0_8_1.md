# SIGER 0.8.1 - plan de implementacion

Rediseno visual integral del explorador estadistico: sus cinco secciones y sus graficos con una presentacion a la
altura de la fuente estadistica institucional, tarjetas del Panorama que responden en toda su superficie e iconografia
de una biblioteca mantenida.
Decisiones del responsable del 2026-11-27 (D52-D57). Version 0.8.1, prefijo de gates **F**, registro en
`docs/BITACORA_0_8_1.md`. El diseno de referencia esta en la seccion 3 de este plan (no hay documento de diseno aparte).
Este plan solo cambia con aprobacion del responsable; si una sesion encuentra que es incorrecto o incompleto, lo
registra como desvio o hallazgo y, si bloquea el gate, se detiene.

0.8.1 no cambia datos, cifras, consultas, contratos, exportaciones, servidor, migraciones ni scripts: es solo
interfaz. Los planes 0.8.0 y anteriores estan cerrados y sus documentos (`PLAN_0_8_0.md`, `BITACORA_0_8_0.md`,
`DISENO_0_8_0.md`) no se editan. La hoja de ruta 0.9.0 sigue fuera de alcance.

## 0. Decisiones (las copia F01 a `docs/DECISIONES_FUNCIONALES.md` como "Precision 0.8.1")

- **D52 - Alcance.** 0.8.1 redisena por completo la presentacion del explorador estadistico (`#estadisticas`:
  Panorama, Informes, Explorar, Cortes y series, Calidad), incluido el dibujo de sus graficos y tablas, y cambia la
  iconografia de toda la aplicacion. Es un cambio de presentacion, no de contenido: sin cambios en el servidor (salvo
  el numero de version del `pom.xml`), en migraciones (esquema 24), en `scripts/` ni `.mvn/`, en las consultas que se
  envian, en el modelo de graficos (`chartModel.ts`: paleta, escalas, barras desde 0) ni en su exportacion
  (`chartExport.ts`), en el significado de ninguna cifra ni en los textos de ayuda y de "Como leer esta cifra" salvo lo
  que agrega D56. Ninguna pantalla agrega consultas nuevas al servidor para decorar.
- **D53 - Tarjetas del Panorama.** Cada tarjeta de indicador es, en toda su superficie, un enlace real a su consulta
  en Explorar: un `<a href="#estadisticas?...">` con el `href` que hoy calcula `encodeRoute('explorar', q)`. Responde
  al clic en cualquier punto (titulo, cifra, variacion, linea mensual, ayuda, pie), al teclado (Tab y Enter), al clic
  central y a "abrir en una pestana nueva". La tarjeta en error no es enlace (conserva "Reintentar"); la tarjeta que
  calcula si lo es, como hoy. Se retira el patron de boton con capa `::after`.
- **D54 - Iconos: autorizacion de dependencia (DI-12 de 0.8.0, para este caso).** Se autoriza una sola dependencia
  nueva de frontend: **`lucide-react`** (licencia ISC), en `dependencies`, con **version exacta** (sin `^` ni `~`),
  cuya `peerDependencies` admita React 19, verificada con `npm info lucide-react@<version> peerDependencies` y
  registrada en el cierre. Se adopta a traves del componente existente `components/Icon.tsx`, que conserva su API
  (`<Icon name size className/>`, `aria-hidden`, `focusable="false"`): ningun archivo importa `lucide-react`
  directamente salvo `Icon.tsx`. Solo se importan iconos por nombre (sin importacion global ni iconos dinamicos por
  cadena). El texto de la licencia ISC queda en `docs/LICENCIAS_TERCEROS.md` (con la de Cabin, OFL). Ninguna otra
  dependencia (ni de graficos, animacion, estilos o componentes) sin autorizacion expresa.
- **D55 - Direccion visual.** "Tablero institucional": claro, sobrio y con jerarquia fuerte, dentro de
  `docs/MANUAL_IDENTIDAD_DDN_SIGER_A.md`. Se construye con los tokens existentes y, si hacen falta, tokens nuevos en
  `styles/tokens.css` derivados de la paleta (los colores del emblema solo como acentos, nunca de fondo de texto
  largo). Contraste: textos >= 4,5:1 e iconos y marcas >= 3:1 sobre su fondo, con prueba que lo calcula para cada
  token nuevo (como `tests/charts.test.ts`). Movimiento breve (<= 500 ms) y siempre desactivado con
  `prefers-reduced-motion: reduce`. Sin modo oscuro.
- **D56 - Sentido de la variacion.** La variacion conserva cifra, porcentaje (o puntos), flecha y texto. El color es
  apoyo y solo expresa juicio donde hay uno: en "Ingresos con caracterizacion completa" subir es favorable; en
  "Registros tardios" bajar es favorable; en Ingresos, REQ constituidos, Casos y Actuaciones el color es neutro (mas
  ingresos no es mejor ni peor). Favorable usa los tonos `--ok-*`; desfavorable, `--warn-*` (nunca `--danger-*`);
  neutro, `--info-*` o la tinta. "Como leer esta cifra" del Panorama agrega una linea que lo explica.
- **D57 - Entrega y produccion.** 0.8.1 se entrega con su propio paquete y la etiqueta `v0.8.1`; el paquete 0.8.0
  (`SIGER-20261126-212826.zip`) y las etiquetas anteriores quedan inmutables. La actualizacion de produccion
  (DDN-SIGER) de 0.8.0 a 0.8.1 la ejecuta el responsable, con autorizacion expresa, por el paso U81 (seccion 9),
  despues de F03. La activacion de los catalogos (U7 de 0.8.0) sigue su propio curso con UEP y no depende de 0.8.1.

## 1. Punto de partida (verificado al planificar, 2026-11-27)

- `main` en `4b482be` (`docs: respuesta de DI-11`), posterior a la etiqueta `v0.8.0` (objeto `2a7fb90`). Produccion
  corre 0.8.0 (paso U del 2026-11-26, U0-U6 conformes; U7 pendiente).
- Frontend: React 19.3.0, TypeScript 6.0.3, Vite 8.3.4; `dependencies` solo `react` y `react-dom`. Pruebas
  `node --test` (252 ok en E17), incluidas las de render en servidor (`tests/*-render.test.mjs`, con
  `vite.ssrLoadModule` y `renderToStaticMarkup`).
- Iconos: `frontend/src/components/Icon.tsx`, 49 trazos propios (`PATHS`), usados en toda la aplicacion.
- **Defecto de las tarjetas (causa verificada en el codigo).** `features/stats/Panorama.tsx` (`IndicatorCard`) hace
  clicable solo el `<button className="stat-card-link">` del titulo y extiende su area con
  `.stat-card-link::after {position: absolute; inset: 0}` (`styles/screens.css`, lineas 549-551). Pero
  `button:active:not(:disabled)` de `styles/base.css` (linea 71, especificidad 0-2-1) gana a `.stat-card-link {all:
  unset}` (0-1-0) y aplica `transform: translateY(1px) scale(.985)` al boton al presionar. Un `transform` convierte al
  boton en el bloque contenedor de su `::after`: al bajar el mouse la capa se encoge al tamano del titulo, el
  `mouseup` cae fuera del boton y el clic se pierde. Por eso solo funciona presionando el titulo. D53 lo resuelve de
  raiz (enlace real, sin capa).
- Tarjetas actuales: titulo, cifra, variacion en texto plano, linea mensual de 120x32 sin area ni puntos, ayuda y
  "Abrir en Explorar"; todas iguales, sin icono ni jerarquia. Pestanas del explorador: texto sin iconos que se
  envuelve en movil.

## 2. Reglas para todos los gates (condiciones del responsable)

1. **Inmutables**: `v0.5.0`-`v0.8.0`, sus paquetes y evidencias, y los respaldos y constancias de la VM. No se
   modifica la VM durante el desarrollo.
2. **Servidor**: `git diff v0.8.0 -- backend/src/main` vacio; en `backend/pom.xml` solo cambia la version
   (0.8.0 -> 0.8.1). Migraciones: ninguna (esquema 24). Formulario y su recepcion intactos por consecuencia.
3. **Scripts**: `git diff v0.8.0 -- scripts/ .mvn/` vacio.
4. **Graficos**: `git diff v0.8.0` vacio para `frontend/src/components/charts/chartModel.ts` y `chartExport.ts`
   (paleta, escalas, barras desde 0, exportacion SVG y PNG). `Chart.tsx` puede cambiar solo su dibujo (formas,
   etiquetas, realce, animacion) segun 3.6; `tests/charts.test.ts` pasa sin cambios y el SVG exportado sigue autonomo
   (colores como atributos, sin animacion ni clases que dependan de la hoja de estilos).
5. **Cifras**: ninguna funcion existente de `features/stats/statsModel.ts`, `seriesModel.ts`, `cutsModel.ts`,
   `qualityModel.ts` ni `statQuery.ts` cambia su resultado; solo se agregan funciones puras nuevas, cada una con su
   prueba. Las consultas que envia cada pantalla (`requestBody`) son las mismas de 0.8.0.
6. **Pruebas existentes**: las 252 del frontend y la suite JUnit pasan sin debilitarse; una prueba existente solo se
   ajusta si verificaba la marca visual que este plan cambia (por ejemplo, el `<button>` de la tarjeta), y el
   registro lo justifica una por una.
7. **Accesibilidad**: nombre accesible en todo control; la tarjeta-enlace dice indicador, cifra y destino ("Ingresos:
   1.234. Abrir en Explorar"); iconos siempre decorativos (`aria-hidden`) junto a texto; foco visible; la variacion
   nunca solo por color; ningun codigo ni termino tecnico a la vista (los barridos de `stats-render.test.mjs` siguen
   vigentes); sin dependencia de hover para ver informacion.
8. **Datos personales**: sin datos ciudadanos en capturas, logs, bitacora ni pruebas (solo datos sinteticos).
9. **Dependencias**: solo `lucide-react` segun D54. `package-lock.json` coherente (`npm ci` lo acepta sin
   cambiarlo).
10. **Tamano**: el registro informa el tamano de `frontend/dist` (JS y CSS, sin comprimir y con gzip) antes y
    despues; el JS no crece mas de 25 KB con gzip.
11. **Mutacion**: cada regla nueva con una prueba que falla si se rompe, con la evidencia "regla -> prueba que fallo".
12. **Facilidad de uso**: el gate de interfaz declara como lo comprobo (texto, flujo, ayuda, movil a 390 px y
    escritorio a 1440 px).
13. **Comandos**: los de `CLAUDE.md` del equipo donde se trabaje; F03 solo en DDN-TEST-SIGER (Windows).
14. **Documentos**: las ordenes `powershell.exe ... -File` solo van en las guias que enlaza `Check-Scripts.ps1`; la
    seccion del paso U81 en `DESPLIEGUE_VM.md` no agrega ordenes nuevas (remite a las de la seccion 5).

## 3. Diseno de referencia

### 3.1 Panorama

- **Encabezado de la seccion**: periodo en texto destacado y, a su lado, dos marcas informativas: alcance
  (`scopeLabel`) y "Comparado con ..." Las listas de periodo y comparacion, y "Datos de las HH:MM / Actualizar", siguen
  en la barra de herramientas, alineadas a la derecha en escritorio y apiladas en movil.
- **Indicador principal**: "Ingresos" ocupa dos columnas en escritorio (una en movil), con cifra mayor y linea mensual
  ancha con etiqueta del primer y ultimo mes. Los otros cinco van en la grilla (3 columnas en >= 1200 px, 2 en
  tableta, 1 en movil).
- **Anatomia de cada tarjeta** (de arriba abajo): icono en una ficha de color suave del indicador + nombre; cifra
  grande con numeros tabulares; pastilla de variacion (flecha, texto y tono de D56) y "respecto de N"; linea mensual
  con area degradada, punto en el ultimo mes y linea base; ayuda en texto pequeno; pie "Abrir en Explorar" con flecha
  que se desplaza levemente al pasar el mouse o con foco. Hora propia de la tarjeta solo cuando corresponde, como hoy.
- **Caracterizacion completa**: ademas de la cifra, un anillo de progreso (SVG, decorativo) con la proporcion; la
  cifra en texto sigue siendo la fuente.
- **Iconos sugeridos por indicador** (nombres de `lucide-react`, se confirman contra la version fijada): Ingresos
  `Inbox`; REQ constituidos `FolderCheck`; Casos `BriefcaseBusiness`; Actuaciones `Activity`; Caracterizacion
  completa `BadgeCheck`; Registros tardios `ClockAlert`. Variacion: `TrendingUp`, `TrendingDown`, `MoveRight`.
- **Estados**: cargando = esqueleto con brillo suave (sin brillo con movimiento reducido) que conserva el tamano
  final de la tarjeta; error = tarjeta con icono de alerta, mensaje y "Reintentar" (no es enlace).
- **Interaccion** (D53): toda la tarjeta es el enlace; hover eleva sombra y borde; `:active` hunde levemente la
  tarjeta completa (no un hijo); foco con el anillo `--ring` alrededor de toda la tarjeta. El `href` lo calcula una
  funcion pura nueva (por ejemplo `cardHref(q)`) con su prueba.
- **Animacion de la cifra** (opcional del implementador): conteo breve al aparecer por primera vez; el render en
  servidor, el texto accesible y el valor tras la animacion son siempre la cifra exacta; sin animacion con
  movimiento reducido.
- Los gradientes y recortes SVG usan identificadores unicos por tarjeta (`useId`).

### 3.2 Marco del explorador (todas las secciones)

- Pestanas como control segmentado con icono + texto (Panorama `LayoutDashboard`, Informes `FileChartColumn`, Explorar
  `ChartNoAxesCombined`, Cortes y series `Layers`, Calidad `ShieldCheck`; nombres indicativos). En movil se desplazan
  en una sola fila, sin envolverse, con la activa visible al entrar. Se conservan `aria-current` y `title`.
- `PageHeader` del explorador sin cambios de texto.

### 3.3 Explorar

- **Entrada "¿Que quieres saber?"**: las preguntas guiadas pasan a ser mosaicos grandes con icono, titulo y una linea
  de descripcion (el texto actual), en grilla adaptable; "Consulta vacia" como mosaico secundario.
- **Constructor**: panel de campos con icono por familia, buscador visible arriba y estado "en uso" marcado con icono
  y texto (no solo color); zonas Filas, Columnas y Filtros como areas de destino claras, con borde animado al arrastrar
  y texto guia; fichas de campo con sus acciones agrupadas.
- **Resultado**: banda superior con el total como cifra destacada, la frase de la consulta (`queryTitle`) y la hora
  de los datos; selector de tipo de grafico como control segmentado con icono por tipo; grafico y tabla en el mismo
  panel con separacion clara; acciones (exportar, congelar, guardar) en una barra propia con iconos de tipo de archivo.
- En movil el panel de campos se pliega sobre el resultado (boton "Campos") en vez de quedar como columna.

### 3.4 Informes (Figura 1 de la Cuenta Publica)

- Encabezado editorial: titulo de la figura, Cuenta Publica elegida como selector destacado y el total del periodo.
- Cada desglose en una tarjeta con su grafico y su tabla lado a lado en escritorio (apilados en movil), numerada como
  en el informe; columna "% del total" con barra de datos en la celda (decorativa: la cifra sigue en texto).
- Botones CSV y XLSX con icono de tipo de archivo en el encabezado de cada tarjeta.

### 3.5 Cortes y series, y Calidad

- **Cortes y series**: cada corte y cada serie como tarjeta con icono, nombre, fecha, periodo y estado en marca de
  texto; acciones agrupadas; estados vacios con icono grande y el texto actual.
- **Calidad**: cifra global de completitud destacada (con anillo como en 3.1) si la pantalla ya la calcula; cada
  variable con su barra apilada (las partes y textos actuales), mayor grosor, extremos redondeados y leyenda clara;
  la variable elegida resaltada; sin umbrales ni juicios nuevos (los colores de las partes son los actuales o neutros).

### 3.6 Graficos (`Chart.tsx`, solo dibujo)

Barras con extremos redondeados y etiqueta de valor al final; lineas con area degradada, puntos y realce del punto
enfocado; al pasar el mouse o enfocar una marca, esa marca se realza y las demas se atenuan; ayuda emergente con
categoria, serie y valor (las mismas que hoy da `title`); entrada animada breve (sin animacion con movimiento
reducido); rejilla mas tenue y ejes con mejor tipografia. La circular puede pasar a anillo con el total al centro
solo si `chartModel.ts` lo permite sin cambios; si no, se mantiene circular. Las marcas siguen siendo enfocables con
nombre accesible, las barras parten de 0 y la paleta es la de `chartModel.ts`.

### 3.7 Tablas estadisticas (`ResultTable.tsx` y tablas de Informes)

Encabezado fijo con mejor contraste, filas alternas suaves, realce de fila al pasar el mouse o enfocar, numeros
tabulares alineados, totales con separacion marcada y barra de datos opcional en las columnas de porcentaje. Sin
cambiar columnas, orden, cifras ni el arbol de derechos.

### 3.8 Iconos de toda la aplicacion

`Icon.tsx` pasa a mapear cada `IconName` actual a un componente de `lucide-react` con un `strokeWidth` uniforme
(1,75 a 2) y el mismo `size`. Mapeo indicativo: home `House`, attention `UserPlus`, people `Users`, inbox `Inbox`,
mail `Mail`, send `Send`, folder `Folder`, briefcase `BriefcaseBusiness`, tasks `SquareCheckBig`, flow `GitBranch`,
bell `Bell`, swap `ArrowLeftRight`, shield `ShieldCheck`, activity `Activity`, logout `LogOut`, refresh `RefreshCw`,
search `Search`, left `ChevronLeft`, right `ChevronRight`, down `ChevronDown`, close `X`, menu `Menu`, info `Info`,
alert `TriangleAlert`, check `Check`, checkCircle `CircleCheck`, clock `Clock`, upload `Upload`, download
`Download`, file `FileText`, eye `Eye`, eyeOff `EyeOff`, printer `Printer`, arrowRight `ArrowRight`, arrowLeft
`ArrowLeft`, plus `Plus`, edit `Pencil`, user `User`, lock `Lock`, message `MessageSquare`, flag `Flag`, calendar
`Calendar`, link `Link`, history `History`, handoff `ArrowRightToLine`, layers `Layers`, heart `Heart`, chart
`ChartColumn`, sparkle `Sparkles`. Se agregan los nombres nuevos que use 3.1-3.7. Si un icono no tiene equivalente
razonable, se conserva su trazo propio y el registro lo dice. El emblema institucional no cambia. Una prueba recorre
todos los `IconName` y verifica que cada uno renderiza un `<svg>` con `aria-hidden="true"`.

### 3.9 Lo que no cambia

Consultas, cifras, cache y cola de consultas, rutas (`#estadisticas?...`), exportaciones CSV/XLSX/ZIP y de graficos
(contenido y formato del archivo), reglas de cortes, series y calidad, permisos (lo decide el servidor), textos de
ayuda existentes, arrastrar y soltar con su alternativa por botones, y el resto de las pantallas de SIGER (salvo el
cambio de iconos).

## 4. Documentacion que se actualiza

`docs/DECISIONES_FUNCIONALES.md` ("Precision 0.8.1", D52-D57), `docs/LICENCIAS_TERCEROS.md` (nuevo),
`docs/ARQUITECTURA.md` (presentacion: iconos `lucide-react` a traves de `Icon.tsx`), `CLAUDE.md` (Estado y la nota de
iconos), `README.md` (version), `docs/ALCANCE_Y_PENDIENTES.md`, `docs/DESPLIEGUE_VM.md` (seccion 6, paso U81) y
`docs/PRUEBAS_MANUALES_0_8_1.md` (nuevo: verificacion visual y de tarjetas). `SOURCE_SHA256.txt` se regenera en F03.

## 5. Protocolo de cada sesion (gate)

Rige integra la seccion 5 de `docs/PLAN_0_8_0.md` (leer, precondiciones, inventario y plan, **GO explicito**, editar
solo lo autorizado, comprobar, cerrar con un solo commit `gate FNN: <titulo>`, push a `main` revisando que no viajen
archivos sensibles, detenerse), incluida su subseccion "Corte por cuota o cambio de agente", con estas diferencias:
se leen `AGENTS.md` (si existe), `CLAUDE.md`, **este plan** y **`docs/BITACORA_0_8_1.md`** completa; el prefijo es
`F`; las carpetas de ensayo son nuevas en D: (`D:\Ensayo-F01`, `D:\Ensayo-F03`...); el GO diferido se registra en
"GO aprobados pendientes de ejecucion" de `BITACORA_0_8_1.md`. Un gate que resulte mayor se divide (`FNNa` cerrado con
lo verificado y `FNNb` ABIERTO). Con observaciones en F02, el responsable deja F02 BLOQUEADO y agrega `F02b`
(ABIERTO, correccion) y `F02c` (EXTERNO, nueva revision), y asi sucesivamente. El ejecutor nunca edita el registro ni
el estado de un gate EXTERNO.

## 6. Gates

| Gate | Titulo | Depende de | Equipo |
|---|---|---|---|
| F01 | Rediseno visual del explorador estadistico e iconografia (frontend, version y documentos) | - | Cualquiera de `CLAUDE.md` |
| F02 | Revision visual del responsable y auditoria de Claude Padre (EXTERNO) | F01 | - |
| F02b | Correcciones de la revision visual de F02 | F02 (BLOQUEADO con observaciones) | Cualquiera de `CLAUDE.md` |
| F02c | Nueva revision visual y auditoria de F02b (EXTERNO) | F02b | - |
| F03 | Build de entrega y etiqueta `v0.8.1` | Ciclo de F02 cerrado (F02c CERRADO), sin correcciones ABIERTO | DDN-TEST-SIGER |

### F01 - Rediseno visual del explorador estadistico e iconografia

- **Hace**: D53 (tarjeta-enlace), D54 (dependencia e `Icon.tsx`), D55 y D56 (seccion 3 completa: las cinco
  secciones, graficos y tablas), version 0.8.1 en `backend/pom.xml`, `frontend/package.json` y
  `package-lock.json`, y la documentacion de la seccion 4 salvo `SOURCE_SHA256.txt`. Es un solo gate; si en la
  ejecucion resulta mayor que una sesion, se divide por la regla de la seccion 5 (por ejemplo, `F01a` con iconos,
  marco y Panorama, y `F01b` con el resto).
- **Archivos autorizados**: `frontend/package.json`, `frontend/package-lock.json`, `frontend/src/components/Icon.tsx`,
  `frontend/src/components/Primitives.tsx` (solo esqueleto, estado vacio y encabezado de panel),
  `frontend/src/components/charts/Chart.tsx` (solo dibujo, regla 4), `frontend/src/features/stats/*.tsx`, funciones
  nuevas en `frontend/src/features/stats/*Model.ts`, `frontend/src/styles/*.css`, pruebas en `frontend/tests/`,
  `backend/pom.xml` (version) y los documentos de la seccion 4. Cualquier otro archivo exige
  detenerse y consultar (los que solo pasan a usar un nombre de icono nuevo se listan en el plan del gate).
- **Pruebas minimas**: (a) render: la tarjeta con datos es un `<a>` con `href` igual a `cardHref(q)`, que contiene
  titulo, cifra, variacion y linea mensual, y no contiene otro control interactivo; la tarjeta en error no es enlace;
  siguen siendo seis tarjetas `stat-card`; (b) `cardHref` y la funcion de tono de D56 (los seis indicadores, subida,
  bajada y sin base); (c) todos los `IconName` renderizan `<svg aria-hidden="true">`; (d) contraste de cada token
  nuevo; (e) guardia de CSS: ninguna regla aplica `transform` a un descendiente de la tarjeta-enlace en `:active` y no
  queda la capa `.stat-card-link::after`; (f) graficos: cada tipo sigue con sus marcas enfocables y su nombre
  accesible, barras desde 0, y el SVG exportado no contiene `class` ni `<style>` ni animaciones; (g) los barridos de
  `stats-render.test.mjs` (sin codigos a la vista, botones con nombre, tablas con encabezados) cubren las cinco
  secciones redisenadas; (h) la suite completa del frontend y JUnit H2 (por el cambio de version).
  Mutaciones minimas: volver al `<button>` del titulo; invertir el sentido de "Registros tardios"; quitar
  `aria-hidden` del icono; dejar una clase en el SVG exportado.
- **Humo (obligatorio)**: instancia local con datos sinteticos en `./target/humo-*` (o la carpeta de ensayo en D: si es
  Windows), navegador sin ventana: en cada una de las seis tarjetas, clic en la cifra, en la linea mensual y en el
  pie abre Explorar con la consulta del indicador; Tab + Enter igual; el `href` permite abrir en otra pestana.
  Capturas de las cinco secciones (Explorar con un resultado y su grafico en cada tipo disponible) en 1440 px y 390 px,
  y del Panorama cargando y con datos, en la carpeta de ensayo (no en el repositorio), con su lista en el registro. Procesos cerrados por PID en `finally`.
- **Registro**: ademas del formato de la seccion 7, la version exacta de `lucide-react` y su `peerDependencies`, los
  tamanos de `dist` antes y despues (regla 10) y los iconos que conservan su trazo propio.

### F02 - Revision visual del responsable y auditoria de Claude Padre (EXTERNO)

El responsable revisa las capturas de F01 (y, si quiere, la instancia local) y decide si la presentacion queda a la
altura; Claude Padre audita el diff `v0.8.0..<commit de F01>` contra este plan y la seccion 2. El responsable registra
el resultado a mano: CERRADO (conforme) o BLOQUEADO con la lista de observaciones, que toma `F02b`.

Resultado (2026-11-27): BLOQUEADO. Auditoria de codigo de `9277b0d` conforme (condiciones de la seccion 2 verificadas
por Claude Padre); la revision visual dejo las observaciones que corrige F02b.

### F02b - Correcciones de la revision visual de F02

- **Hace** (observaciones de F02, 2026-11-27):
  1. **Informes: cada grafico debe leerse.** A 1440 px ningun texto de un grafico queda bajo 10 px efectivos (tamano
     del texto del SVG por la escala con que se dibuja). Para eso, tabla y grafico van lado a lado solo si el grafico
     dispone de al menos 560 px de ancho; si no, el grafico va bajo la tabla, a todo el ancho.
  2. **Panorama: ninguna tarjeta queda con una franja vacia.** La linea mensual crece para ocupar el alto disponible
     de la tarjeta y la ayuda y el pie quedan abajo. La linea, su area y su punto toman el color de acento de su
     tarjeta, con contraste >= 3:1 sobre el fondo de la tarjeta, probado.
  3. **Graficos a 390 px** (Explorar, Informes y Calidad): cuando un grafico se desborda en horizontal se muestra el
     aviso visible "Desliza para ver el grafico completo" y un difuminado en el borde. No cambian `chartModel.ts`,
     `chartExport.ts` ni el SVG descargado.
  4. **Detalles menores**: en `tokens.css`, el comentario de la prueba de contraste cita `tests/tokensF01.test.ts`,
     que no existe; la prueba esta en `tests/statsF01.test.ts`. En la nota de D56 (`TONE_NOTE`) se nombra tambien el
     ambar de los registros tardios.
- **Archivos autorizados**: los mismos de F01 (seccion 6, F01).
- **Pruebas minimas**: las reglas de F01 (pruebas nuevas, cada regla nueva con su mutacion y la evidencia "regla ->
  prueba que fallo"; suite completa del frontend y JUnit H2; condiciones de la seccion 2) y una prueba que verifique
  las reglas 1 y 2 donde sea posible: la regla de ancho de Informes (lado a lado solo con >= 560 px para el grafico) y
  que la linea mensual llene la tarjeta.
- **Humo (obligatorio)**: como F01, con capturas de las cinco secciones a 1440 px y 390 px en `D:\Ensayo-F02b` (no en
  el repositorio), con su lista en el registro; procesos cerrados por PID en `finally`.
- **Registro**: el formato de la seccion 7, con los tamanos de `dist` antes y despues (regla 10).

### F02c - Nueva revision visual y auditoria de F02b (EXTERNO)

Como F02, sobre las capturas de F02b y el diff `9277b0d..<commit de F02b>`. El responsable registra el resultado a
mano: CERRADO (conforme) o BLOQUEADO con observaciones, que toma `F02d` (y su revision `F02e`), y asi sucesivamente.

### F03 - Build de entrega y etiqueta `v0.8.1`

Como E17 de 0.8.0: en DDN-TEST-SIGER, desde el commit auditado en la ultima revision del ciclo de F02 (F02c o la que la siga), `Build.ps1 -IncludeJava` sin `-SkipChecks`
(que corre `Check-Scripts.ps1`, el frontend y JUnit), `git status` vacio antes y despues; `npm ci` necesita acceso al
registro npm para descargar `lucide-react` (sin acceso, F03 queda BLOQUEADO y se detiene). Revision del paquete:
`manifest.json` con `version` 0.8.1 y `schema` 24; `scripts\` con SHA-256 iguales uno a uno a los del ZIP 0.8.0;
`docs\` igual a la copia de trabajo e incluye `LICENCIAS_TERCEROS.md`; sin archivos sensibles. Humo con el JAR del
paquete en `D:\Ensayo-F03`: `/api/health` con `version` 0.8.1 y `schema` 24, y una tarjeta del Panorama abre
Explorar. Registro con nombre, tamano y SHA-256 del ZIP y del JAR (son los que usa U81); `CLAUDE.md`, `README.md`,
`ALCANCE_Y_PENDIENTES.md` y `SOURCE_SHA256.txt` en el mismo commit; etiqueta anotada `v0.8.1` ("SIGER 0.8.1") sobre
el commit de cierre, con push de la etiqueta. Registro final "Cierre del plan 0.8.1" en la bitacora.

## 7. Formato del registro de cierre (en la bitacora)

El de la seccion 7 de `docs/PLAN_0_8_0.md`, con `FNN` en lugar de `ENN` y las condiciones de la seccion 2 de este plan.

## 8. Prompt universal de continuidad (identico en todas las sesiones)

> Lee íntegramente `AGENTS.md` (si existe), `CLAUDE.md`, `docs/PLAN_0_8_1.md` y `docs/BITACORA_0_8_1.md`, y las secciones de `docs/PLAN_0_8_0.md` a las que remite el plan 0.8.1.
> Identifica el primer gate `ABIERTO` según la bitácora (salta los `CERRADO`, `BLOQUEADO`, `POSTERGADO` y `EXTERNO`) y trabaja exclusivamente en él, respetando el protocolo de la sección 5 del plan 0.8.1.
> Antes de modificar cualquier archivo:
> * Verifica el estado de Git, el commit del último gate cerrado, las dependencias del gate y las definiciones que exige.
> * Revisa los antecedentes, decisiones, restricciones y hallazgos aplicables.
> * Realiza el inventario técnico.
> * Presenta el plan de implementación, los archivos que propones modificar, las pruebas previstas (incluidas las de mutación) y los riesgos.
> * DETENTE y espera mi aprobación explícita (GO).
>
> Únicamente después del GO:
> * Ejecuta el gate dentro del alcance aprobado.
> * Realiza las pruebas exigidas, incluidas las de regresión y mutación cuando correspondan.
> * Comprueba las condiciones de la sección 2 del plan 0.8.1.
> * Documenta el cierre en la bitácora con el formato de la sección 7.
> * Revisa el alcance del commit y la ausencia de archivos sensibles.
> * Realiza commit y push a `main` en `origin`.
> * Informa el cierre y DETENTE. No comiences el siguiente gate.
>
> No modifiques estados ni registros de gates `EXTERNO`. No dependas de conversaciones anteriores ni de información no documentada. Si falta información, existe una discrepancia o necesitas ampliar el alcance, detente y consulta antes de proceder.
> Responde siempre en español.

## 9. Paso U81: actualizacion de produccion de 0.8.0 a 0.8.1 (responsable)

Lo ejecuta el responsable en DDN-SIGER (solo D:), con autorizacion expresa, despues de F03. F01 lo documenta en
`docs/DESPLIEGUE_VM.md`, seccion 6, remitiendo a las ordenes de la seccion 5 sin agregar ordenes nuevas:

- **U81-0** Requisitos: F03 CERRADO; ventana acordada; aviso a los usuarios.
- **U81-1** Paquete: copiar el ZIP de F03 a D:, `certutil -hashfile ... SHA256` contra el registro de F03 antes de
  descomprimir (como U1, con la fecha del paquete 0.8.1).
- **U81-2** Detener SIGER (como U3). Las entregas del Formulario esperan en su cola (como en la seccion 5).
- **U81-3** Respaldo y `Restore-Check.ps1` inmediatamente antes de actualizar (como U4).
- **U81-4** `Update.ps1` con el paquete 0.8.1 y arranque (como U5); sin migraciones (esquema 24).
- **U81-5** Verificacion (como U6): `/api/health` con `version` 0.8.1 y `schema` 24; ingreso de una cuenta con
  Reportes y comprobacion de que una tarjeta del Panorama abre Explorar con clic en su cifra.
- Sin U2 (los temporales de MySQL ya se verificaron en U2 de 0.8.0) ni U7. Ante una falla, la vuelta atras es la de la
  seccion 5 (descriptor anterior y respaldo de U81-3).
