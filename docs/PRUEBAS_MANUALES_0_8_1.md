# SIGER 0.8.1 - pruebas manuales

Verificacion visual del explorador estadistico redisenado y de las tarjetas del Panorama (`docs/PLAN_0_8_1.md`, D52-D57,
gate F01). Se hacen con una cuenta con la funcion Reportes, en una instancia de pruebas con datos sinteticos (nunca con
datos ciudadanos en capturas ni notas), en un navegador de escritorio (1440 px de ancho) y en uno movil o con la vista
movil del navegador (390 px). 0.8.1 no cambia cifras: cada pantalla debe mostrar las mismas cifras que 0.8.0 para la
misma consulta. Las pruebas automatizadas que acompanan cada punto estan en `frontend/tests/statsF01.test.ts`,
`statsF01-render.test.mjs` e `icons-render.test.mjs`, y las de las correcciones de F02b (seccion 6) en `statsF02b.test.ts`
y `statsF02b-render.test.mjs`.

## 0. Version

`/api/health` responde `"version": "0.8.1"` y `"schema": "24"`. El pie de la barra lateral dice "Version 0.8.1".

## 1. Tarjetas del Panorama (D53)

En "Explorador estadistico", seccion Panorama, para cada una de las seis tarjetas (Ingresos, REQ constituidos, Casos,
Actuaciones, Ingresos con caracterizacion completa y Registros tardios):

1. Clic en la cifra: se abre Explorar con la consulta del indicador (la medida y el periodo del Panorama, por mes).
2. Volver atras y clic en la linea o en las columnas mensuales: mismo resultado.
3. Volver atras y clic en el pie "Abrir en Explorar": mismo resultado.
4. Con el teclado: Tab hasta la tarjeta (el anillo de foco rodea toda la tarjeta) y Enter: mismo resultado.
5. Clic central (o clic derecho y "Abrir en una pestana nueva"): la consulta se abre en otra pestana.
6. Mientras una tarjeta calcula (esqueleto), tambien abre su consulta. Una tarjeta en error no es enlace y ofrece
   "Reintentar".

Prueba automatizada: `statsF01-render.test.mjs` (tarjeta = un solo `<a>` con el `href` de su consulta y sin otro control)
y la guardia de CSS de `statsF01.test.ts` (ninguna regla transforma un descendiente de la tarjeta al presionarla).

## 2. Sentido de la variacion (D56)

Con "Comparar con" el mismo periodo del anio anterior:

- Ingresos, REQ constituidos, Casos y Actuaciones: la pastilla de variacion es neutra (gris) suba o baje.
- Ingresos con caracterizacion completa: verde si sube, ambar si baja.
- Registros tardios: verde si baja, ambar si sube.
- Siempre se leen la cifra, el porcentaje (o los puntos), la flecha y el texto; sin base de comparacion, neutra.
- "Como leer esta cifra" del Panorama trae la linea que explica los colores y nombra el verde y el ambar en los dos
  indicadores que tienen juicio (F02b).

## 3. Presentacion de las cinco secciones

| Seccion | Que mirar |
|---|---|
| Marco | Pestanas con icono y texto en un control segmentado; en movil, una sola fila que se desplaza y deja a la vista la activa sin mover la pagina en vertical |
| Panorama | Periodo como titular con las marcas de alcance y comparacion; Ingresos como indicador principal con sus columnas por mes (mes y cifra); el resto con su icono, cifra, pastilla y linea mensual; anillo en caracterizacion completa |
| Informes | Encabezado con la figura, el total del periodo y la Cuenta Publica destacada; cada desglose numerado, con tabla y grafico (lado a lado solo en pantallas anchas: ver seccion 6), barra de datos en "% del total" y botones CSV y XLSX con icono de archivo |
| Explorar | Preguntas como mosaicos con icono; campos con icono por familia y "En uso" con icono y texto; zonas Filas, Columnas y Filtros con texto guia y borde al arrastrar; banda con el total, la consulta y la hora; tipo de grafico con icono; acciones agrupadas; en movil, boton "Campos" que pliega el panel |
| Cortes y series | Cada corte y cada serie como tarjeta con icono, estado en texto y acciones agrupadas |
| Calidad | Leyenda de los estados; barras mas gruesas con extremos redondeados; la variable elegida resaltada. No hay cifra global: la pantalla no la calculaba en 0.8.0 y 0.8.1 no agrega consultas (hallazgo de F01) |

## 4. Graficos y tablas

1. En Explorar, con un resultado, pasar el mouse por una barra, columna, punto o porcion: esa marca se realza, las demas
   se atenuan y aparece una ayuda con categoria, serie y valor. Con Tab sobre las marcas ocurre lo mismo.
2. Con el sistema en "reducir movimiento" (Windows: Configuracion > Accesibilidad > Efectos visuales > Efectos de
   animacion desactivados), los graficos, las tarjetas y las pestanas no se animan.
3. "Descargar SVG" y "Descargar PNG": el archivo trae titulo, notas y fuente como en 0.8.0, con los mismos colores y
   cifras (prueba automatizada: `statsF01-render.test.mjs` compara el SVG exportado de cada tipo de grafico con la
   referencia de 0.8.0, `frontend/tests/chart-export-080.json`).
4. Tablas: encabezado fijo al desplazar, filas alternas, realce de fila y total separado; columnas, orden y cifras como
   en 0.8.0.

## 5. Iconos del resto de SIGER

Recorrer el menu lateral, Inicio, una bandeja y una ficha: los iconos cambiaron de dibujo (lucide-react) pero no de
significado ni de posicion, y siempre acompanan a un texto o a un nombre accesible. El escudo institucional no cambia.

## 6. Correcciones de la revision visual de F02 (gate F02b)

1. **Informes se lee.** A 1440 px, cada grafico va bajo su tabla, en la misma columna, con unos 840 px de ancho; sus
   rotulos, ejes y cifras se leen sin acercarse (ningun texto bajo 10 px). En una pantalla ancha (por ejemplo 1920 px),
   tabla y grafico van lado a lado y el grafico se dibuja a su tamano natural. Lado a lado nunca se achica el grafico.
2. **Panorama sin franjas vacias.** En cada tarjeta, la linea mensual ocupa todo el alto que queda entre la variacion y la
   ayuda; la ayuda y "Abrir en Explorar" quedan abajo. La linea, su area y su punto van en el color de la ficha del icono
   de su tarjeta (verde en REQ constituidos, rosado en Casos, violeta en Actuaciones, verde oscuro en caracterizacion
   completa y ambar oscuro en Registros tardios); el punto final se ve redondo tambien en movil. Ingresos conserva sus
   columnas por mes.
3. **Graficos en movil (390 px).** En Explorar (con un resultado), Informes y Calidad, sobre un grafico que no cabe a lo
   ancho aparece "Desliza para ver el grafico completo" y el borde derecho se difumina. Al deslizar hasta el final el
   difuminado pasa al borde izquierdo y la ultima barra o punto se ve entero, sin nada encima. El aviso no aparece cuando
   el grafico cabe, no forma parte del SVG ni del PNG descargados y la pagina no se desplaza a lo ancho.
