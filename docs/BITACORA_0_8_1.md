# SIGER 0.8.1 - bitacora de gates

Estado vivo del trabajo de `docs/PLAN_0_8_1.md` (el diseno de referencia es la seccion 3 del plan). Cada sesion de
Claude Code o Codex toma el primer gate ABIERTO, lo cierra aqui con el formato de la seccion 7 del plan, hace commit y
push, y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado, con commit y push), BLOQUEADO (detenido o a la espera de una
definicion; ver su registro), POSTERGADO (fuera del cierre por decision expresa del responsable), EXTERNO (lo realiza
el responsable fuera del ejecutor; el ejecutor lo salta y nunca edita su registro ni su estado).
Cada sesion empieza con el prompt universal de la seccion 8 del plan, sin nombrar gates.
F03 depende del ciclo de F02 cerrado: mientras F02 no este CERRADO, la sesion que encuentre F03 como primer ABIERTO
verifica esa dependencia, informa y se detiene sin editar.

## Estado

| Gate | Titulo | Estado |
|---|---|---|
| F01 | Rediseno visual del explorador estadistico e iconografia (frontend, version y documentos) | ABIERTO |
| F02 | Revision visual del responsable y auditoria de Claude Padre | EXTERNO |
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

(Sin registros.)
