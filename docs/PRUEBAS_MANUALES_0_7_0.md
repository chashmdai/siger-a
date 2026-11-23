# SIGER 0.7.0 - pruebas manuales

Recorridos de la cobertura territorial de las sedes, la region de origen de la persona, la bandeja de comunicaciones y
el aviso a distribucion (`docs/PLAN_0_7_0.md`, decisiones D18-D24 y escenarios 1-9). Sirven para el humo de T04 (instancia aislada con datos
sinteticos), el ensayo de T06b (instancia de ensayo con el paquete de T06a) y, si V4 usa 0.7.0, la revision posterior en
produccion. Los scripts de 0.7.0 son identicos a los de 0.6.0: la instalacion, el servicio y el Formulario se prueban
con `docs/PRUEBAS_MANUALES_0_6_0.md` y las ordenes de `docs/DESPLIEGUE_VM.md`; este documento no tiene ordenes propias.

Para cada punto informar CORRECTO / FALLA / NO PROBADO. En una falla: punto, cuenta de SIGER (por su funcion), pantalla,
resultado esperado y mensaje exacto. Se anotan solo codigos de region y de sede, cifras, estados y folios: sin datos
personales ni claves (tampoco en capturas). En produccion no se crean registros de prueba: alli solo se revisan
Estructura, el aviso y la cobertura, con la estructura real.

Datos del ensayo (sinteticos, en el archivo de estructura inicial o creados por TI en Estructura): sedes ACTIVA de
Coquimbo (04), O'Higgins (06), Biobio (08) y La Araucania (09); una sede PROYECTADA de Atacama (03) con dotacion
(Coordinacion regional); cobertura de Coquimbo 03, de O'Higgins 07, de Biobio 16 y de La Araucania 14; cobertura del
nivel central 13. Cuentas: TI, Distribucion, la Coordinacion regional de cada sede y una cuenta con REPORTES y
EXPORTACION para toda la institucion.

## 0. Version y arranque

1. El pie de la barra lateral dice "Version 0.7.0". Como TI, Inicio > Estado del sistema dice lo mismo.
2. `/api/health` responde `version` 0.7.0 y `schema` 16; `Start.ps1` solo informa exito con esa version y ese esquema
   del `manifest.json` del paquete.
3. `manifest.json` del paquete: version 0.7.0, esquema 16 y `automatedTestsExecuted` verdadero (T06a). El SHA-256 del
   ZIP es el del registro de T06a en `docs/BITACORA_0_7_0.md`, comprobado antes de descomprimirlo.
4. Archivo de estructura con `cobertura` en las sedes y `coberturaNivelCentral`: la inicializacion termina bien y, como
   TI, Estructura muestra cada region cubierta como un periodo vigente a nombre de la TI inicial. Un archivo con la
   region fisica de una sede en su `cobertura`, una region repetida, un codigo inexistente o `cobertura` en un ambito
   central se rechaza sin escribir datos.

## 1. Estructura y cobertura (TI)

1. Estructura muestra en cada sede su region fisica, su cobertura vigente (en una PROYECTADA, "preparada") y la
   historia de periodos (inicio, termino, autor y fundamento). Hay un bloque con la cobertura del nivel central.
2. Cobertura de una sede: agregar o quitar regiones con fundamento. La region fisica no se ofrece. Una sede CERRADA no
   tiene el boton. Antes de confirmar se ve el efecto territorial: cada region que la sede atiende hoy, con "sigue
   atendida por ..." o "queda sin atencion".
3. Si alguna region queda sin atencion, la confirmacion exige marcar la casilla que nombra todas esas regiones y dice que
   el fundamento registra la decision institucional. Sin marcarla no se envia.
4. Auditoria: `COBERTURA_ACTUALIZADA` (sede) y `COBERTURA_NIVEL_CENTRAL_ACTUALIZADA` (nivel central) con "anterior ->
   nueva" solo con codigos de region, el efecto y el fundamento.
5. Si otra persona cambio la sede o la cobertura del nivel central mientras el dialogo estaba abierto, la orden se rechaza
   (409) y el panel se recarga.

## 2. Escenarios 1-4: sedes que atienden otra region

Para cada par: Atacama (03) por Coquimbo (04), Maule (07) por O'Higgins (06), Nuble (16) por Biobio (08) y Los Rios (14)
por La Araucania (09).

1. Como Distribucion, al clasificar una COM con region de origen de la region cubierta, el selector propone la sede que
   la cubre, con el rotulo " · cubre esta región", listada despues de las sedes propias de la region.
2. Como la Coordinacion de la sede, registrar una primera atencion con la region de origen cubierta (modalidad GENERAL;
   en la modalidad NNA la region es texto libre y queda "sin region declarada", 0.8.0).
3. Reportes (cuenta con REPORTES): el filtro y la agrupacion "Region de origen" muestran la region cubierta (por ejemplo,
   03); "Region de la sede" muestra la region fisica de la sede (04). Los dias habiles descuentan los feriados de la
   region de la sede, no los de la region de origen (D22): con un feriado regional solo de 03 el resultado no cambia y
   con uno de 04 si.
4. Exportar REQ y Casos: columnas `region_sede` (04), `region_registrada` (03) y `region_vigente` (03).

## 3. Correccion de la region de origen

1. Complementar la ficha de la atencion del punto 2 cambiando la region (por ejemplo, 03 a 05): la auditoria
   `FICHA_COMPLEMENTADA` dice "Region de origen modificada", sin codigos.
2. La ficha muestra la region registrada (03) y la vigente (05).
3. Reportes: la cifra sigue en 03 (region registrada) y la nota de corregidos cuenta 1. Exportacion: registrada 03,
   vigente 05.
4. Completar una ficha que no tenia region no es una correccion (la nota no cuenta).

## 4. Escenario 5: apertura de una sede propia

1. Con la sede PROYECTADA de Atacama (03) y Coquimbo cubriendo 03: TI la activa. Desde ahi la region 03 tiene dos sedes
   y el selector no preselecciona ninguna (solo la region).
2. TI quita 03 de la cobertura de Coquimbo con fundamento: el efecto dice "sigue atendida por Sede Atacama", sin casilla.
3. El selector propone la Sede Atacama para 03. La historia de Coquimbo muestra el periodo de 03 terminado.
4. Los REQ anteriores conservan su sede y su region registrada (Reportes y exportacion no cambian para ellos).

## 5. Escenario 6: Region Metropolitana por el nivel central

1. Con 13 en la cobertura del nivel central, el aviso `REGION_SIN_SEDE` de Administracion no incluye 13.
2. Al clasificar una COM con region de origen 13, el selector parte en Nivel central, con el aviso de que el nivel
   central atiende la region y sin unidad propuesta.
3. Quitar 13 de la cobertura del nivel central (con la casilla de regiones sin atencion): 13 vuelve al aviso.

## 6. Escenario 7: cierre de una sede cuya region queda sin atencion

1. Cerrar una sede ACTIVA cuya region fisica nadie mas atiende (sin bloqueos de D8): el efecto dice "queda sin
   atencion" y exige la casilla. Su cobertura adicional se informa y termina en el mismo acto.
2. Sin la casilla no se envia; si la situacion cambio mientras el dialogo estaba abierto (otra sede pasa a atender esa
   region), el servidor responde 409 y la revision se descarta.
3. Con la casilla y fundamento la sede queda CERRADA; la auditoria `AMBITO_CERRADO` registra el efecto y la cobertura
   terminada (solo codigos). La region aparece en `REGION_SIN_SEDE`.
4. Cambiar la region fisica de una sede (Editar) muestra el mismo efecto antes de confirmar.

## 7. Escenario 8: marca de la bandeja de comunicaciones

Con COM del Formulario sinteticas (en el ensayo de T06b; en el humo de T04, simuladas): una de 07 con O'Higgins cubriendo
07, una de 13 con el nivel central, una sin region y una con `riesgo` afirmativo.

1. Comunicaciones (Todas, Por distribuir y Mis ingresos) e Inicio ("Ingresos por distribuir"): la de 07 muestra "VII"
   con el color regional y el titulo emergente "Maule - atiende Sede O'Higgins"; la de 13, "RM" neutra; la sin region,
   sin marca.
2. La de riesgo: marca roja con icono y el texto "Riesgo actual informado" (tambien sin region, "Riesgo", o en la RM),
   con respiracion lenta (ciclo de 5 a 6 segundos) mientras esta por distribuir; una vez distribuida, roja fija.
3. Con "reducir movimiento" activado en el sistema operativo, la marca roja no se anima.
4. La lista muestra las mismas filas, en el mismo orden y con los mismos conteos que sin la marca; los filtros no
   cambian. Una COM de atencion directa no lleva numero.

## 8. Escenario 9: aviso a distribucion de las COM del Formulario

Con COM del Formulario sinteticas (en el ensayo de T06b, recibidas con el correo desactivado), una sin `riesgo` y una
con `riesgo` afirmativo, como Distribucion. El aviso lo genera una tarea que corre cada minuto: esperar hasta 2 minutos
despues de la recepcion antes de anotar una falla.

1. "Mis avisos" de cada cuenta de Distribucion: la COM sin riesgo trae "Hay un ingreso del Formulario pendiente de
   distribucion."; la de riesgo, "Hay un ingreso del Formulario con NNA en riesgo inminente, pendiente de distribucion."
   El texto no trae nombres, relato ni region. "Abrir con permisos actuales" abre la COM.
2. Un solo aviso de llegada por COM y por cuenta: tras unos minutos, y despues de reiniciar el servicio, no aparece
   otro.
3. Una COM manual registrada en SIGER solo trae su aviso de siempre ("Hay un ingreso pendiente de clasificacion."). Una
   COM del Formulario distribuida antes de que corra la tarea no recibe el aviso de llegada.
4. Las cuentas que no distribuyen (TI, Coordinacion regional, Reportes) no reciben este aviso.
5. En produccion no se crean registros de prueba: el aviso de la primera entrega ciudadana se revisa en "Mis avisos" de
   Distribucion despues de V5 (antes no hay cuentas de Distribucion y el aviso sale cuando existan). Con el correo Graph
   activo (D16), cada cuenta de Distribucion con correo recibe ademas el mismo texto por correo.
