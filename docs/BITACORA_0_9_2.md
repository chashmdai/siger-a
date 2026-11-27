# SIGER 0.9.2 - bitácora de gates

Estado vivo de `docs/PLAN_0_9_2.md` (diseño: `docs/DISENO_0_9_2.md`; serie: `docs/PLAN_0_9_0.md`). Mismos estados y
reglas que `BITACORA_0_9_1.md`. Cada sesión empieza con el prompt universal de `PLAN_0_9_0.md` 5.

## Estado

| Gate | Título | Estado |
|---|---|---|
| B00 | Línea base, decisiones y diseño verificado | ABIERTO |
| B01 | Seguimiento de la gestión por folio (servidor e interfaz) | ABIERTO |
| B02 | Vínculo con buscador y distribución ágil (servidor e interfaz) | ABIERTO |
| B03 | Sedes: fechas de apertura y distribución territorial del Formulario (servidor e interfaz) | ABIERTO |
| B04 | Correo de avisos, índice de avisos y reportes sin TI inconsistente (servidor) | ABIERTO |
| B05 | Captura: resolución en el acto con derechos y caracterización de la COM en la ficha de la SOL | ABIERTO |
| B06 | Estadística: microdatos de actuaciones y Casos, ZIP por flujo y Figura 1 en un XLSX | ABIERTO |
| B07 | Scripts: antigüedad del respaldo, acceso por red y retención del esquema de restauración | ABIERTO |
| B08 | Integración MySQL, rendimiento, aceptación y cierre de versión | ABIERTO |
| B09 | Auditoría independiente | EXTERNO |
| B10 | Build de entrega y etiqueta | ABIERTO |

0.9.2 empieza con A07 de 0.9.1 CERRADO (`PLAN_0_9_0.md` 1). Mientras 0.9.1 esté EN CURSO, el prompt universal no lee
esta bitácora.

## Decisiones del responsable

| Decisión | Antes de | Respuesta |
|---|---|---|
| D69 selector de antecedentes; destino solo el REQ | B02 | 2026-11-27: aprobada por el responsable. |
| D70 seguimiento por folio como ajuste de la lectura existente, sin facultad nueva | B01 | 2026-11-26: aprobada por el responsable. |
| D71 "usar todas las propuestas" y distribuir desde la bandeja | B02 | 2026-11-27: aprobada (dentro del alcance confirmado de 0.9.2). |
| D72 reportes sin TI inconsistente | B04 | 2026-11-27: aprobada (hallazgo de E14). |
| Alcance de 0.9.2 y lo que queda fuera (`PLAN_0_9_0.md` 7) | B00 | 2026-11-27: aprobado por el responsable. |

## Definiciones pendientes

| Def. | Pregunta | Antes de | Propuesta | Respuesta |
|---|---|---|---|---|
| DI-21 | Antigüedad máxima del respaldo aceptado por `Update.ps1` | B07 | 24 horas por defecto, parametrizable; exige la evidencia de `Restore-Check.ps1` del mismo respaldo | pendiente |
| DI-22 | Cuántos esquemas `siger_a_restore_*` conservar en producción | B07 | El más reciente; los demás se borran con confirmación | pendiente |

## GO aprobados pendientes de ejecución

(vacío)

## Pasos en producción (los ejecuta el responsable, con autorización expresa)

| Paso | Descripción | Requisito | Estado |
|---|---|---|---|
| U-0.9.2 | Actualización de SIGER a 0.9.2 en DDN-SIGER (solo `D:`) | B10 CERRADO | PENDIENTE |
| R-0.9.2 | Acceso por red con el script de B07 | TI y red institucional | PENDIENTE (fuera de plazo si TI no lo autoriza) |

## Registro de gates

(sin registros todavía)
