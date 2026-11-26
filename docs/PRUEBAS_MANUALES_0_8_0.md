# SIGER 0.8.0 - pruebas manuales

Recorridos de la normalizacion de la informacion y de la inteligencia de negocios para Estudios (`docs/PLAN_0_8_0.md`,
decisiones D25-D43, escenarios 1-15 de su seccion 0 y criterios de facilidad de uso U1-U13 de `docs/DISENO_0_8_0.md`
10.5). Cada punto dice que se comprueba, como verlo en pantalla, que se espera y que prueba automatizada lo acredita (o el
recorrido del humo de E09, E12, E13 o E14 que lo midio). Sirven para revisar una instancia de pruebas con datos
sinteticos y, despues del paso U, para la revision en produccion (alli sin crear registros de prueba: solo se miran las
pantallas con los datos reales). Los scripts de 0.8.0 son los mismos de 0.6.0 y 0.7.0: la instalacion, el servicio y el
Formulario se prueban con `docs/PRUEBAS_MANUALES_0_6_0.md` y la actualizacion con el paso U de `docs/DESPLIEGUE_VM.md`
(seccion 5); este documento no tiene ordenes propias.

Para cada punto informar CORRECTO / FALLA / NO PROBADO. En una falla: punto, cuenta de SIGER (por su funcion), pantalla,
resultado esperado y mensaje exacto. Se anotan solo cifras, estados, codigos de region o sede y folios: sin datos
personales ni claves (tampoco en capturas).

Cuentas del recorrido (sinteticas): TI; Distribucion; una jefatura y un profesional de un ambito; la Coordinacion
regional de una sede; una cuenta de Estudios con Reportes, Exportacion y Gestion estadistica para toda la institucion; y
una cuenta con Reportes solo para una sede. TI importa y activa los doce catalogos de
`config-ejemplo\catalogos\` del paquete (pantalla "Catalogos estadisticos") antes de empezar, salvo donde se indica
"sin catalogos vigentes".

Las pruebas automatizadas se nombran como `Clase.prueba` (servidor, carpeta `backend/src/test`) o por su archivo
(interfaz, carpeta `frontend/tests`). Las cifras esperadas de esas pruebas estan escritas a mano en ellas.

## 0. Version y arranque

1. El pie de la barra lateral dice "Version 0.8.0"; como TI, Inicio > Estado del sistema dice lo mismo.
2. `Estado.ps1` muestra la version 0.8.0 y el esquema de la ultima migracion del paquete (24); `Start.ps1` solo informa
   exito con esa version y ese esquema.
3. Con una cuenta con Reportes, el menu tiene "Explorador estadistico"; TI no lo tiene ni puede abrirlo (D2).
   Automatizado: `PermisosFinalesTest` (toda ruta /api clasificada; estadisticas y reportes 403 para TI).

## 1. Escenarios de aceptacion (seccion 0 del plan)

### Escenario 1. Figura 1

Se comprueba: los cuatro desgloses de la Cuenta Publica (tipo de solicitud, requirente, via y adscripcion institucional,
DI-13), que cada uno sume el total y que pantalla, exportacion y corte den las mismas cifras.
En pantalla: Explorador estadistico > Informes; elegir la Cuenta Publica. Descargar el CSV de un desglose y, con
Gestion estadistica, "Congelar estas cifras" y consultar el corte.
Se espera: cada tabla suma el total de la Figura; el CSV y el corte repiten las cifras; la region del requirente, la del
NNA y la de los hechos no estan en la Figura (se consultan en Explorar).
Automatizado: `StatExportTest.figuraUnoReproducible` (consulta = CSV = XLSX = ZIP = corte = reagregacion de microdatos),
`StatEngineTest.figuraSolicitud`, `figuraRequirente`, `figuraVia`, `StatAdscriptionTest.matrizDi13EnLosCuatroModosYEnUnCorte`;
humo de E14 (Informes comparado con el CSV y con el corte).

### Escenario 2. Que se cuenta como requerimiento (D25)

Se comprueba: invalida excluida, duplicada contada, antecedente no contado, vinculo devuelto vuelve a contar, pendientes y
primera atencion contadas, REQ reabierto, transferido y con varios Casos contado una vez.
En pantalla: Explorar, "Ingresos" y "Ingresos invalidos (excluidos)" del periodo; desestimar un ingreso como invalido o
duplicado (Distribucion, "Desestimar ingreso") y volver a consultar.
Se espera: la invalida pasa de Ingresos a Excluidos; la duplicada sigue en Ingresos.
Automatizado: `StatEngineTest.descarteD25`, `antecedenteD25`, `pendientesYDirectaD25`, `cadenaUnicaD25`,
`StatModesTest.estadoHistoricoD25`.

### Escenario 3. Fechas y periodos (D26)

Se comprueba: el 30-06 a las 23:59:59,999 y el 01-07 a las 00:00 (hora de Chile) en Cuentas Publicas distintas; el
cambio de horario; el registro tardio.
En pantalla: Panorama, indicador "Registros tardios"; Explorar con Mes en filas.
Se espera: un ingreso recibido en el periodo y registrado despues de su fin figura como tardio.
Automatizado: `StatEngineTest.bordeCuentaPublica`, `registroTardio`, `StatModesTest.horarioChile`.

### Escenario 4. Propuestas del Formulario

Se comprueba: vulneracion propone "Vulneracion" y persona natural adulta; promocion con organizacion propone persona
juridica; el Formulario NNA no propone tipo; la propuesta no cuenta como informado.
En pantalla: ficha de una COM del Formulario, tarjeta "Caracterizacion": "Propuesta: X" con "Usar propuesta"; nada
queda elegido solo.
Automatizado: `StatClassificationTest.escenario4PropuestasYViaCalculadasSinEscribir`, `StatModesTest.calidadYPropuesta`,
`frontend/tests/characterization.test.ts`.

### Escenario 5. Territorio

Se comprueba: una COM de Maule (07) atendida por O'Higgins distingue region del requirente 07, sede responsable REG-06,
territorio por cobertura REG-06 y region de la sede 06; una transferencia cambia la sede vigente y no la inicial.
En pantalla: Explorar, con Region del requirente, Sede inicial y Sede vigente en filas.
Automatizado: `TerritorialDimsTest.escenario5OrigenCoberturaYSedeConTransferencia`, `StatModesTest.sedeActivadaYCerrada`,
`sedeProyectada`, `coberturaCentral`.

### Escenario 6. Derechos

Se comprueba: un REQ con dos Casos que comparten un derecho cuenta un requerimiento con ese derecho; quien no lee el
segundo Caso no ve su clasificacion; un REQ cerrado no se clasifica hasta reabrirlo.
En pantalla: ficha del REQ, panel "Derechos y contexto" (consolidado del REQ y sus Casos); en un REQ cerrado el boton
dice "Requiere reapertura formal".
Automatizado: `RightsTest.escenario6ConsolidadoSinDuplicarYSoloLoLegible`, `registroCerradoRechazaYTrasLaReaperturaEsRetrospectiva`,
`ImmutabilityTest`.

### Escenario 7. Cierre con datos estadisticos (D28)

Se comprueba: con catalogos vigentes, un REQ con tipo de solicitud pendiente no cierra; con "Sin informacion" si cierra;
sin catalogos vigentes nada bloquea.
En pantalla: "Cerrar REQ": el dialogo muestra lo pendiente y permite resolverlo ahi mismo.
Automatizado: `StatCompletenessTest.escenario7CierraConEstadosEspecialesYNoConUnaPropuesta`,
`StatClassificationTest.sinCatalogoVigenteElIngresoNoSeBloquea`.

### Escenario 8. Actuaciones

Se comprueba: dos denuncias penales en un REQ cuentan dos actuaciones y un requerimiento con denuncia penal; una tarea
"compromiso de querella" completada no cuenta como querella.
En pantalla: "Registrar actuacion" con tipo; Explorar, "Actuaciones" por Tipo de actuacion.
Automatizado: `ActionTypesTest.escenario8DosDenunciasYUnCompromisoQueNoEsActuacion`, `StatEngineTest.actuacionesVigentes`.

### Escenario 9. Cortes

Se comprueba: una correccion posterior no cambia el corte; la comparacion la muestra; la verificacion recalcula la misma
huella; un corte no se modifica ni se borra.
En pantalla: Cortes y series: generar un corte (el asistente avisa que detiene brevemente las operaciones), corregir una
clasificacion, "Comparar con hoy" (la celda cambiada dice "antes N, ±d") y "Verificar integridad".
Automatizado: `StatCutsTest.escenario9CorreccionPosteriorNoCambiaElCorteYLaComparacionLaMuestra`,
`huellaCanonicaEstableYVerificacionQueDetectaAlteraciones`, `ningunCodigoModificaUnCorte`, `frontend/tests/statsE13.test.ts`.

### Escenario 10. Series historicas

Se comprueba: una serie 2024-2025 se muestra junto a SIGER sin sumarse; una serie superpuesta se avisa; una serie de una
sede no la ve Reportes de otra sede; una serie institucional exige alcance institucional; TI no la ve.
En pantalla: Cortes y series > "Importar una serie" (plantilla, archivo, vista previa, confirmacion) y "Ver junto a
SIGER" (dos lineas, la discontinuidad con texto).
Automatizado: `StatSeriesTest.tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion`,
`facultadesDeSedeYAnulacionConservaFilas`, `sumaExplicitaPeriodosSeparadosConTotalesSinDuplicar`.

### Escenario 11. Ficha 1.2 e ingreso manual

Se comprueba: un nombre historico no se divide; completar una region no es corregir; RUN, documento, nacionalidad,
correo y celular con las mismas reglas del Formulario.
En pantalla: abrir una ficha 1.0, completarla y ver el resumen "Campos complementados: N; rectificados: M".
Automatizado: `Attention12Test.escenario11MismasReglasDelFormularioEnLasPersonas`, `historicoSeConservaSinDividirseNiInterpretarse`,
`completarNoEsCorregirYAuditoriaSinValores`, `IntakeRecordTest.escenario11MismasReglasEnElIngreso`,
`frontend/tests/ficha12.test.ts`, `intake.test.ts`.

### Escenario 12. Contrato del Formulario (SRCeI)

Se comprueba: las entregas NNA con RUN de fuente SRCeI se registran sin autenticacion y sin elevar la procedencia.
En pantalla: ficha de una COM del Formulario NNA: el RUN figura como declarado, no autenticado.
Automatizado: `FormularioSrceiContractTest`, `FormularioSrceiReceptionTest`.

### Escenario 13. Facilidad de uso

Los criterios U1-U13 de la seccion 2. E15 esta postergado: los acredita el ejecutor en el humo y los revisa la auditoria
E16.

### Escenario 14. Edad y nacionalidad

Se comprueba: un NNA con fecha de nacimiento tiene su edad a la recepcion; una edad declarada entera se conserva; un
texto historico de edad no se interpreta ("Sin informacion"); una nacionalidad libre de una ficha 1.0 no se convierte
en pais; la fecha de nacimiento nunca se exporta.
En pantalla: Explorar con "Tramo de edad del NNA"; "Como leer esta cifra" informa el porcentaje con dato.
Automatizado: `PersonDimsTest.escenario14CumpleanosEnChileYTextoHistoricoSinInterpretar`, `StatModesTest.demografiaEstadistica`,
`StatExportTest.barridoDeDatosCiudadanosEnTodosLosArchivosYHojas`.

### Escenario 15. Valores multiples

Se comprueba: un REQ con tres derechos (uno repetido en un Caso) tiene una fila en el microdato con 3 valores y tres
filas en el archivo relacionado; el agregado por derecho cuenta un requerimiento por derecho e informa menciones y total
unico; reagregar los dos archivos reproduce el agregado; el grafico circular no se ofrece.
En pantalla: Explorar, derechos en filas: la nota "Un requerimiento puede tener varios valores" y el circular no
disponible con su razon; "Paquete ZIP".
Automatizado: `StatEngineTest.mencionesDerecho`, `porcentajeMultivalor`, `StatExportTest.derechoConTresOrigenesYCuatroFolios`,
`frontend/tests/charts.test.ts` (reglas del circular).

## 2. Facilidad de uso (U1-U13, DISENO 10.5)

Acciones contadas desde la vista indicada, como se midieron en el humo. Un criterio no logrado es un hallazgo.

| U | Que se logra | Recorrido | Acciones | Humo |
|---|---|---|---|---|
| U1 | Tabla tipo Figura 1 en menos de 2 minutos, sin ayuda | Explorador estadistico > Informes: los cuatro desgloses a la vista | 1 | E12, E14 |
| U2 | Comparar con el periodo anterior | Desde un resultado: "Comparar con" > "Periodo anterior" | 1 | E12, E14 |
| U3 | Tipo de solicitud por region del requirente, en % por fila | Explorar, consulta vacia, Tipo de solicitud a filas, Territorio, Region del requirente a columnas, Cifra > "% por fila" | 6 | E12, E14 |
| U4 | Exportar el paquete ZIP | Desde un resultado: "Paquete ZIP" | 1 | E13 |
| U5 | Generar un corte con fundamento | Desde un resultado: "Congelar estas cifras", dos Continuar, fundamento, Continuar (aviso a la vista), "Generar el corte" | 6 | E13 |
| U6 | Importar una serie desde la plantilla | "Importar una serie": datos, plantilla, archivo, vista previa, confirmacion | 5 pasos | E13 |
| U7 | Hacer un grafico y descargarlo en PNG | Explorar, "Por via de ingreso" (grafico sugerido), "Descargar PNG" | 3 | E12, E14 |
| U8 | Guardar y compartir una vista | "Guardar vista", nombre, Confirmar, "Compartir" en la lista | 4 | E12 |
| U9 | Encontrar los registros pendientes de su alcance | Calidad, "Ver las comunicaciones con datos pendientes" (no aparece si la variable no tiene pendientes) | 2 | E13, E14 |
| U10 | Clasificar derechos de un REQ con busqueda en menos de 1 minuto | Ficha del REQ, "Derechos y contexto" > Clasificar, escribir "educacion", marcar la subcategoria, Confirmar | 4 | E09 |
| U11 | Abrir un indicador del Panorama en Explorar y pasarlo a % por fila | Clic en el indicador, Cifra > "% por fila" | 2 | E12, E14 |
| U12 | Explicar por que un desglose de derechos no suma el total | La nota bajo el grafico y en "Como leer esta cifra" | 0 | E12 |
| U13 | Grafico listo para el informe (titulo, notas y fuente) | "Descargar PNG" o "Descargar SVG" | 1 | E12, E14 |

Ademas, en E14 (correcciones de presentacion de E13):

1. Graficos de columnas y lineas por mes: los rotulos del eje van abreviados ("jul" y "2026" en dos lineas, o "T1 2026")
   y nunca se superponen ni se cortan; el nombre completo aparece al pasar el cursor y en la tabla.
   Automatizado: `frontend/tests/statsE14.test.ts` y `statsE14-render.test.mjs` (correccion 1).
2. Comparacion con un corte: el grafico muestra la cifra actual y la del corte en barras pareadas, no ofrece el circular
   y "Como leer esta cifra" dice "Comparacion con el corte <codigo>". Automatizado: correccion 2.
3. Con meses en filas y una comparacion, la razon de las barras pareadas dice "Usa columnas pareadas", como el boton.
   Automatizado: correccion 3.
4. Un periodo sin registros muestra "Sin registros en el periodo." en lugar del grafico; en la Figura 1 la nota "Sin
   registros en: ..." aparece una sola vez por desglose. Automatizado: correccion 4.
5. Calidad no ofrece el enlace a la bandeja cuando la variable tiene 0 pendientes ("Sin registros pendientes en el
   periodo."). Automatizado: correccion 5.
6. El Panorama muestra cada indicador apenas llega su cifra, sin esperar a los demas. Automatizado:
   `statsE14-render.test.mjs` (Panorama).

## 3. Rendimiento (referencia de E14)

Medido con 50.005 COM sinteticas en MySQL 8.4 aislado (`StatLoadMeasureTest.medicionE14`, resultado en el registro de E14
de `docs/BITACORA_0_8_0.md`): cada consulta del Panorama y del explorador bajo 3 s, y la carga del Panorama completa
proyectada al volumen real (unos 7.200 ingresos al anio). En produccion basta con observar que el Panorama y las
consultas de Explorar responden en pocos segundos; generar un corte detiene las operaciones unos segundos y se hace
fuera del horario de atencion.
