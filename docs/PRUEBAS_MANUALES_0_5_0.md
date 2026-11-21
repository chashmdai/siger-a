# SIGER 0.5.0 - pruebas manuales

Recorridos de cada iteracion de 0.5.0, para el PC (perfil local con semillas sinteticas) o la instancia de validacion.
Usar solo datos sinteticos. No compartir claves, CSV de acceso ni capturas con datos reales. En local, las claves estan
en `var\bootstrap-usuarios.csv` (o en la carpeta `--siger.data` de una instancia de prueba). Cuentas demo citadas:
`ti`, `distribucion`, `administrativo`, `profesional` y `coordinacion.requerimientos` (UPR-REQ), `profesional.proteccion`
(UPR-PD), `direccion.upr`, `direccion.upd`, `direccion.uep`, `coordinacion.regional` y `profesional.regional` (sede
sintetica REG-DEMO, region 05), `auditoria` y `gabinete` (LECTURA).

Para cada punto informar CORRECTO / FALLA / NO PROBADO. En una falla: cuenta, accion, resultado esperado y mensaje
exacto, sin datos personales ni secretos.

## 0. Version y arranque

1. El pie de la barra lateral dice "Version 0.5.0". Como `ti`, Inicio > Estado del sistema dice lo mismo.
2. `https://localhost:<puerto>/api/health` (o `http://localhost:18543` en local) responde `version` 0.5.0 y `schema` 14.
3. Ingresar como `TI` (mayusculas) entra como `ti`. `tí` no entra.

## 1. Administracion en TI y perfil de cuenta (G01-G04b)

1. Como `ti`, Usuarios y permisos > crear una cuenta nueva. Abrir su **Perfil** y, en un solo paso, marcar Distribucion,
   Administrativo (UPR-REQ) y Profesional (UPR-REQ). El paso 2 muestra "Se otorgan: ..." y pide fundamento. Confirmar.
2. Abrir de nuevo el perfil y guardar sin cambios: no se puede confirmar ("El perfil no tiene cambios.").
3. Marcar TI: las demas casillas se limpian y quedan bloqueadas, con la explicacion visible. Cancelar.
4. Entrar con la cuenta nueva (clave temporal, cambio obligatorio): el menu muestra Ingreso por clasificar, Nueva
   atencion y Primeras atenciones, ademas de las bandejas.
5. Como `ti`, Subrogancias: predesignar una subrogante para `coordinacion.requerimientos`, activarla con fundamento y
   terminarla. No interviene ninguna autoridad.
6. Movil (ancho de 390 px o menos): el perfil muestra una tarjeta por ambito con "Sin funciones" o "N funciones", el
   filtro "Solo ambitos con funciones" y el buscador; las tarjetas se expanden y las casillas se recorren con Tab y
   Espacio. Sin desplazamiento horizontal.
7. Una cuenta con TI no tiene bandejas, buscador ni fichas (ver seccion 10).

## 2. Recuperacion de TI (G03; en la VM o en Windows, con SIGER detenido)

Solo en una instancia de pruebas. Dejar la instancia sin TI permanente (por ejemplo, programar el termino de la unica
TI desde otra TI y esperar a que venza, o deshabilitarla por SQL en una copia), detener SIGER y ejecutar
`Recover-Ti.ps1 -Usuario ... -Fundamento ...` como la cuenta de servicio (`docs/INSTALACION_VM.md`). El usuario debe
tener 3 a 64 caracteres: con las semillas demo la cuenta `ti` (2 caracteres) no se puede recuperar; usar una cuenta nueva
(`-Usuario recuperada.ti -Nombre ... -Ambito UGI`). Probar tambien una cuenta con otra funcion (por ejemplo
`profesional`): se rechaza (D2).
Esperado: la clave temporal queda solo en `data\recuperacion-ti-*.csv`; el log no la contiene; en Auditoria aparece
`TI_RECUPERADA` a nombre de `sistema:actualizacion`. Repetirlo con una TI permanente vigente: se rechaza sin cambios.

## 3. Consulta institucional (G05-G06)

1. Como `ti`, otorgar "Consulta institucional" a una cuenta nueva desde su perfil.
2. Con esa cuenta: el menu tiene Comunicaciones, Solicitudes, Requerimientos, Casos y Colaboraciones y traslados (sin
   Mis tareas ni Nueva atencion); Inicio muestra "Consulta institucional".
3. Una ficha COM, REQ o Caso muestra el aviso "Consulta institucional: lectura sin facultades de gestion" y ningun
   boton de gestion. Una constancia o documento reservado no aparece.
4. Opcional: agregar Profesional a la misma cuenta; en su propio Caso no aparece el aviso y conserva sus acciones.

## 4. Sedes regionales (R01, R03b)

1. Como `ti`, Usuarios y permisos > Estructura: REG-DEMO muestra Coordinacion, dotacion y bloqueos; Cerrar esta
   deshabilitado con los bloqueos a la vista, en singular o plural segun la cantidad ("1 REQ abierto", "2 cuentas con
   adscripcion principal en la sede").
2. Crear una sede regional proyectada (unidad REG, Region, Estado "Proyectada"). No aparece como destino al encaminar
   una COM.
3. Ver funcionarios / perfil de una cuenta: los ambitos se agrupan en Nivel central y Sedes regionales; la sede
   proyectada aparece con casillas; en las sedes no hay Jefatura y Direccion se llama "Coordinacion regional".
   Preparar la dotacion de la sede proyectada y activarla desde el panel: las funciones empiezan a regir.
4. Editar la sede (nombre, region, apertura) y comprobar que la apertura se conserva al guardar sin cambiarla.

## 5. Materia, lectura nacional y selector de destino (R02, R03a)

1. Como `distribucion`, en una COM nueva, "Distribuir SOL": Sede regional, una region, Materia sugerida UPD; revisar
   "Recibe" y confirmar. En otra COM: Nivel central, UPR y un area.
2. Como `direccion.upd`, abrir la SOL regional: aviso "Lectura nacional por materia" y ningun boton de gestion.
3. Como `coordinacion.regional`, asumir la SOL, cambiar la materia del REQ a UPR con el boton "Materia" y abrir un
   Caso (la materia viene precargada). `direccion.upr` ve ahora el REQ y su COM en sus bandejas; `direccion.upd` ya no.
4. Como `administrativo`, Nueva atencion con region de atencion Valparaiso; en "Entregar la atencion" > "Direccion o
   coordinacion competente" se propone REG-DEMO. La lista de regiones parte en Arica y Parinacota.

## 6. Buscador y bandejas (G07-G08c)

1. Como `distribucion`, buscar un RUN desde el campo de Inicio: encabezado de la persona, cadena COM -> SOL -> REQ ->
   Casos con responsables; la direccion del navegador dice solo `#buscar`.
2. Abrir una ficha con Enter y volver con Atras: los resultados siguen. Buscar un nombre con mas de 10 coincidencias y
   usar los filtros; "Puede ser otra persona" en coincidencias solo por nombre.
3. Ctrl+K, escribir un folio exacto (por ejemplo `com-a-1`): abre la ficha. Escribir `12`: aparece la pista.
4. Ctrl+K, escribir un folio y elegir "en Comunicaciones": la direccion dice solo `#com`, con el texto en el campo de la
   bandeja; abrir una fila y volver con Atras: la busqueda sigue. Abrir `#req?q=prueba`: queda `#req` con "prueba".
5. Como `gabinete` (LECTURA), buscar un nombre: no encuentra personas y la pantalla lo explica; un folio de REQ si.
6. Salir y volver a entrar: no queda texto de busqueda en el buscador ni en las bandejas.

## 7. Calendario habil (G09)

1. Como `ti`, Calendario habil: aparece "Sin feriados cargados para AAAA" del anio actual y del siguiente.
2. Importar `2026-12-25 Navidad`, `2026-09-19 Glorias del Ejercito` y `2026-08-20 Prueba [16]` junto con una linea
   mal escrita: no se registra nada y se indica la linea. Quitarla y confirmar.
3. El 19-09 muestra "Fin de semana: no altera el computo"; el 20-08 tiene alcance "Region Nuble". Agregar `2026-08-20`
   como nacional: 409. Retirar uno y ver el evento en Auditoria y operacion.
4. Como `distribucion`, Calendario habil no aparece en el menu.

## 8. Hitos (G10)

Tras actualizar una instancia con historia: si Administracion muestra "Hitos reconstruidos con omisiones", anotar la
cifra (es permanente y no afecta la operacion; ver `docs/ACTUALIZACION_0_5_0.md`). En una instancia nueva no aparece.

## 9. Reportes y exportacion (G11-G12)

1. Como `ti`, en el perfil de una cuenta, bloque "Reportes y exportacion": Exportacion queda deshabilitada hasta marcar
   Reportes en el mismo alcance; marcar Reportes y Exportacion en "Toda la institucion" y agregar el ambito UPR con
   solo Reportes. Al quitar Reportes se quitan sus dependientes.
2. Con esa cuenta: menu "Reportes de gestion". Consultar REQ total agrupado por ambito (avisos del calendario y nota fija
   "Reporte de gestion del sistema...") y luego Volumen (barras con el valor en texto).
3. Exportar REQ y Casos (CSV): se abre en una planilla con columnas separadas, coma decimal, fechas de Chile y el ambito
   por su codigo; sin nombres, RUN ni relatos. En un REQ creado por resolucion inmediata (`COM_IMMEDIATE`) la columna de
   cierre es la fecha declarada (puede ser anterior a la creacion) y los tiempos usan el instante del registro.
4. En una bandeja aparece "Exportar CSV" para esta cuenta.

## 10. Permisos (verificacion cruzada)

1. **Profesional sin consulta no encuentra personas de un REQ ajeno**: con `distribucion` registrar una COM con un RUN
   sintetico y distribuirla a UPR-REQ; como `profesional.proteccion` (UPR-PD, sin Consulta institucional) buscar ese
   RUN: sin resultados y sin conteos que revelen la coincidencia. Tampoco abre la ficha por su numero (`#COM/<id>`: 403).
2. **`direccion.upd` encuentra a una persona de un REQ regional de su materia**: con una SOL distribuida a REG-DEMO con
   materia UPD y asumida por `coordinacion.regional`, `direccion.upd` busca el RUN o el nombre de la persona: aparece
   con la cadena regional y la ficha se abre con el aviso de lectura por materia. Con materia UPR, `direccion.upd` no
   la encuentra.
3. **TI no ve buscador, reportes ni fichas**: como `ti` no hay campo de busqueda en Inicio, entrada en el menu ni opcion
   en Ctrl+K; no hay "Reportes de gestion"; `#COM/<id>`, `#REQ/<id>` y `#CAS/<id>` no muestran la ficha (403).
4. **Una cuenta sin Exportacion no ve los botones de exportar**: con una cuenta con Reportes pero sin Exportacion (y con
   una cuenta operativa sin ninguna de las dos), la pantalla de reportes no ofrece "Exportar REQ/Casos" y las bandejas no
   muestran "Exportar CSV".
5. `auditoria` (AUDITORIA sola) no tiene buscador ni bandejas de expedientes y si ve los eventos de su alcance.

## 11. Descargas con nombre neutro (G14)

En una ficha con un documento liberado (local: liberacion manual de pruebas), Descargar: el archivo se guarda como
`<FOLIO>-documento-<n>.<ext>` (por ejemplo `COM-A-000012-documento-34.pdf`), no con su nombre original, que sigue
visible en la tabla de documentos. La descarga queda en Auditoria como `DESCARGA`.
