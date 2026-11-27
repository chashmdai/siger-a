# SIGER 0.9.2 - diseño de referencia

Diseño de `docs/PLAN_0_9_2.md`. Referencias tomadas al planificar sobre SIGER `4b482be` (antes de 0.8.1); B00 las verifica sobre el HEAD
posterior a 0.9.1 y registra las diferencias al final.

## 1. Referencias

| Ref. | Pieza | Hecho relevante |
|---|---|---|
| R1 | `PersonSearch.java` `chains()` (~l. 302-320) | Reconstruye COM, SOL, REQ, Casos y flujos legibles, incluidos REQ asociados por antecedentes admitidos y no devueltos. |
| R2 | `frontend/src/features/search/Search.tsx` (~l. 45) y `searchModel.ts` `exactTarget` (~l. 128) | Con coincidencia exacta abre la ficha automáticamente. |
| R3 | `Queries.java` (~l. 158-192) y `features/detail/Notes.tsx` | Actuaciones del REQ y del Caso con sus filtros de reserva. |
| R4 | `Access.java` (~l. 219-292) | Reglas de lectura por registro (D4, D5, D9) y lectura institucional (`CONSULTA_INSTITUCIONAL`). |
| R5 | `features/detail/ComDetail.tsx` (~l. 25-27) | `COM_LINK` con folio exacto del REQ y observación opcional (P-003 de 0.8.0). |
| R6 | `IntakeCommands.java` (`COM_LINK`, propuestas) y `StatProposals` | Vínculo al REQ; propuestas `PROPUESTA_V1` de tipo de solicitud, requirente y vía. |
| R7 | `BoardQueries.java` | Bandeja «Ingreso por clasificar» y equivalencia con `Access`. |
| R8 | `Reports.java`, `PermisosFinalesTest` (`TI_MIXED_KNOWN`) | Excepción de E14. |
| R9 | `DISENO_0_8_0.md` 15.1-15.5 | Diseño aprobado de D44, D45, D46, D48 y D49. |
| R10 | `BITACORA_0_8_0.md` E09, E11, E12, E16 | GO y hallazgos de origen de B05, B06 y B07. |

## 2. Seguimiento de la gestión por folio (B01, D76)

- Entrada: el buscador reconoce un folio (SIGER-XXXXXX o folio de COM, SOL, REQ o Caso). Con una sola coincidencia por
  folio, muestra la vista de seguimiento en lugar de abrir la ficha (R2 se mantiene para búsquedas por persona).
- Servidor: lectura `GET /api/tracking/{kind}/{id}` (o extensión de la respuesta de búsqueda bajo demanda) que arma la
  cadena con R1 y agrega, solo para el REQ y los Casos legibles, las actuaciones visibles con R3. Una sola cadena por
  pedido; nunca actuaciones de todos los resultados.
- Vista: línea de tiempo con COM de origen (fecha, canal), SOL (destino, estado, fechas), REQ (estado, principal),
  cada Caso (número, estado, responsable, fechas de apertura y cierre) y sus actuaciones (fecha, tipo, resumen visible);
  estado actual destacado arriba en palabras ("En gestión en el Caso N° ...", "Cerrada el ..."); botón "Abrir ficha" por
  registro legible.
- Privacidad: lo no legible no aparece ni se cuenta; un folio inexistente y uno ilegible responden igual.
- Sin facultad nueva: la amplitud la da la lectura que ya tenga la cuenta (D76).

## 3. Vínculo con buscador (B02, D75)

- El diálogo de vínculo de la COM reemplaza "Folio exacto del REQ" por un campo de búsqueda que usa el mismo servicio
  del buscador (folio, RUN o nombre) y lista resultados con su cadena REQ-Casos y estados.
- Sugeridos: al abrir, si la COM tiene RUN (de su ficha de ingreso o del Formulario), se consultan las gestiones de ese
  RUN y se muestran arriba como "Posibles gestiones relacionadas", solo las legibles.
- Selección: el usuario elige un REQ; el comando sigue siendo `COM_LINK` con el REQ (sin `target_case_id`). Observación
  opcional. Errores de servidor iguales a 0.9.1.

## 4. Distribución ágil (B02, D77)

- "Usar todas las propuestas": botón en el diálogo de distribución que copia a los selectores las propuestas vigentes de
  R6 (solo códigos seleccionables del catálogo vigente); el usuario revisa y confirma. Sin propuesta, no hace nada.
- "Distribuir" en la bandeja «Ingreso por clasificar»: acción por fila que abre el mismo componente de diálogo con la
  COM cargada por su ficha (misma revisión); el comando es el mismo (`COM_ROUTE`), con 409 si la revisión cambió.

## 5. Sedes y distribución territorial (B03, D44, D45)

Diseño completo en `DISENO_0_8_0.md` 15.1 y 15.2 (R9), con DI-05 y DI-06. B00 confirma que la región vigente de la COM
del Formulario (`OriginRegion.current`) basta sin cambios en el Formulario.

## 6. Correo de avisos, índice y reportes (B04, D46, D48, D78)

- D46 y D48: `DISENO_0_8_0.md` 15.3 y 15.4. Las plantillas ciudadanas de 0.9.1 quedan fuera del cambio de asuntos.
- D78: `Reports` (consultas y exportación) aplica la misma regla que el resto: una cuenta TI no consulta reportes aunque
  tenga una concesión REPORTES insertada fuera de la aplicación; Administración sigue avisando `TI_INCOMPATIBLE`.

## 7. Captura en el flujo (B05)

- Pantallas para `COM_IMMEDIATE` y `ATTENTION_CREATE` con `resolve_now`: bloque de derechos con el mismo componente de
  E09 (`RightsPanel`), enviado como `Rights.inline` (decisión C del GO de E09).
- Ficha de la SOL con `characterization` de su COM (misma regla de lectura que tiene quien decide la SOL); el diálogo de
  descarte usa ese dato.

## 8. Estadística (B06)

- Microdatos de actuaciones y Casos: archivos adicionales en el paquete estadístico, con columnas definidas en el
  contrato extendido (sin datos personales directos, mismas reglas de D40-D42).
- ZIP por flujo: escribir cada entrada al temporal en `D:` y luego entregar, en lugar de armarlo en memoria; borrar el
  temporal en `finally`.
- Figura 1: un libro XLSX con cuatro hojas en lugar de cuatro archivos.

## 9. Scripts (B07, D49, E16)

- `Update.ps1`: rechaza un respaldo más antiguo que el parámetro (DI-21) o sin evidencia de `Restore-Check.ps1`.
- Script nuevo de acceso por red: cambia `bindAddress` en una instancia detenida, con confirmación; no toca el firewall.
- Retención de `siger_a_restore_*`: listar y borrar con confirmación según DI-22; nunca el esquema vigente.

## 10. Cierre y riesgos

- Mediciones de la regla 15 del plan con 50.005 COM, como E14.
- Riesgos: inferencia en el seguimiento (R: pruebas de denegación y misma respuesta); carga del seguimiento (consulta de
  una sola cadena); cambios en bandejas (equivalencia `Access`/`Visibility`); scripts en producción (ensayo en
  TEST-SIGER y `Check-Scripts.ps1`).

## Diferencias aprobadas

(ninguna)
