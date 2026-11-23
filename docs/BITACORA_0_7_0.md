# SIGER 0.7.0 - bitacora de gates

Estado vivo del trabajo de `docs/PLAN_0_7_0.md`. Cada sesion de Claude Code toma el primer gate ABIERTO, lo cierra aqui con el formato de la seccion 7 del plan, hace commit y push, y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado, con commit y push), BLOQUEADO (detenido; ver su registro), POSTERGADO (fuera del cierre de 0.7.0, con su motivo), EXTERNO (lo realiza el responsable fuera de Claude Code; Claude Code lo salta y nunca edita su registro ni su estado: el responsable los incorpora a mano, segun la fila T05 de la seccion 6 del plan).
Cada sesion de Claude Code empieza con el prompt universal de la seccion 8 del plan, sin nombrar gates.

## Estado

| Gate | Titulo | Estado |
|---|---|---|
| T00 | Linea base y decisiones | CERRADO |
| T01 | Cobertura y efecto territorial (servidor) | ABIERTO |
| T02 | Region de origen y reportes | ABIERTO |
| T03 | Interfaz | ABIERTO |
| T04 | Cierre de version | ABIERTO |
| T05 | Auditoria independiente | EXTERNO |
| T06a | Build de entrega en Windows | ABIERTO |
| T06b | Ensayo diferencial y etiqueta | ABIERTO |

## Decisiones del responsable

| Decision | Antes de | Respuesta |
|---|---|---|
| Plan 0.7.0: D18-D22, version 0.7.0, prefijo T y gates T00-T06b | T00 | 2026-11-23: aprobados. Solo se autoriza el gate de planificacion; cada gate de implementacion requiere autorizacion expresa. |
| Region Metropolitana (13), atendida por la Oficina Nacional | T01 | 2026-11-23 (confirmada por el responsable antes de versionar el plan): cobertura del nivel central declarada por TI (D18), sin sembrar regiones en el codigo; cuenta como region atendida en `REGION_SIN_SEDE` y en el efecto territorial; no agrega destinos al selector. |
| Feriados regionales (D22) | T02 | 2026-11-23: se mantiene la region de la sede responsable (nivel central: solo nacionales). Un cambio requiere aprobacion expresa. |
| Auditoria independiente (T05) | T06a | 2026-11-23: la realiza el asistente del responsable en claude.ai ("Claude Padre"), separada de la implementacion de Claude Code. |
| Corte para V4 con 0.7.0 | V4 | 2026-11-23: 07/12. V4 con 0.7.0 solo con T06b CERRADO, etiqueta `v0.7.0` y aprobacion expresa; si no, V4 con 0.6.0. Respuesta final el 07/12: |

## Antecedentes para T00

- Base: `7c9b89b` (`gate H06e: ensayo y etiquetas`, igual a `v0.6.0`) y el commit `plan 0.7.0: planificacion`, que agrega este plan y esta bitacora (lo hace el responsable de forma manual; no es un gate). Cifras de referencia en la seccion 1 del plan.
- El plan se redacto fuera de Claude Code: T00 verifica contra el codigo cada referencia de la seccion 1 del plan (archivos, lineas, conteos y cifras) y registra cualquier diferencia antes de seguir. Una diferencia de referencia se corrige en el registro de T00; una que cambie una decision, el alcance o una condicion detiene el gate y se consulta al responsable.
- `SOURCE_SHA256.txt` queda desactualizado desde el commit de planificacion; se regenera en T06b.
- La salida a produccion de 0.6.0 (P0 hecho; V0-V6 desde el 04/12) se sigue registrando en `docs/BITACORA_0_6_0.md`; esta bitacora solo registra los gates T.
- Condiciones de la seccion 2 del plan: sin cambios en `scripts/` ni `.mvn/`, sin cambios en la recepcion del Formulario y V1-V14 intactas; cada gate las comprueba con `git diff v0.6.0`.

## Registros de cierre

### T00 - Linea base y decisiones   (CERRADO)
- Fecha: 2026-11-23 (commit `gate T00: linea base y decisiones`), en Windows (TEST-SIGER) con `.tools\`; base `abef27d`.
- Hecho: sin codigo. Cada referencia de la seccion 1 del plan verificada contra el codigo (archivos, lineas y conteos: coinciden). D18-D22 copiados textuales en `DECISIONES_FUNCIONALES.md` ("Precision 0.7.0"), salvo la frase de alcance de D22 (instruccion del responsable: "el calculo de dias habiles (`businessDays`) solo esta en `Reports`; `Csv` y `Holidays` usan solo sus constantes"); copia comprobada con `diff` contra el plan. D6 precisado con D22 y marcado "(precisado en 0.7.0, D22)". `CLAUDE.md`: estado "plan 0.7.0 en curso" (regla de no trabajar sin instruccion intacta).
- Archivos clave: `docs/DECISIONES_FUNCIONALES.md`, `CLAUDE.md`, `docs/BITACORA_0_7_0.md`.
- Verificacion: JUnit 170 ok, 0 omitidas (antes 170, H06d-H06e); frontend typecheck ok, 76 ok (antes 76, H06a), build ok; scripts: `Check-Scripts.ps1` con Windows PowerShell 5.1 antes y despues de editar `CLAUDE.md` y `DECISIONES_FUNCIONALES.md`, ambas corridas 40 scripts y 73 ordenes documentadas en 60 procesos; humo no aplica. MySQL no aplica (sin SQL).
- Evidencia de mutacion: no aplica (sin codigo).
- Condiciones de la seccion 2: comprobadas con `git diff v0.6.0` (vacio en `scripts/`, `.mvn/`, los 7 archivos de la recepcion del Formulario, `IntakeCommands.java` y `db/migration/`).
- Desvios del plan: ninguno. Diferencias de referencia (no cambian decisiones, alcance ni condiciones):
  1. Antecedentes: sobre `v0.6.0` hay dos commits del plan, ambos solo de `docs/PLAN_0_7_0.md` y esta bitacora: `8c4a7be plan 0.7.0: planificacion` y `abef27d plan 0.7.0: prompt universal y registro de T05` (los antecedentes nombran solo el primero).
  2. D22 (seccion 0 del plan): `BusinessCalendar` tambien aparece en `Csv` (`ZONE`) y `Holidays` (`ZONE`, `NATIONAL`), solo por constantes; `businessDays` solo se usa en `Reports` (`:297`, `:299`, `:448-449`, `:530`). La regla no cambia; la frase quedo precisada en D22.
  3. `Attention.java:155-189` abarca `create` (`:155-178`) y tambien `save` con `FICHA_COMPLEMENTADA` (`:182-190`); `:145` es el despacho de `ATTENTION_CREATE`.
- Hallazgos para gates siguientes: la region que usan los dias habiles es `Reports.region(row)` (`:206`, region de la sede del ambito ancla; antiguedad en `:530` con el principal del REQ o el ambito del Caso). `Access.suggestedRegion` (`:306`) ya prioriza la region de la ficha y luego `nna-region`/`adult-region`. El unico INSERT de `external_field` esta en `IntakeCommands.java:128` (dentro de `conserveExternal`, intocable).
- Pendientes: T01 (cobertura y efecto territorial, servidor). `SOURCE_SHA256.txt` sigue desactualizado hasta T06b.
- Verificacion manual sugerida al usuario: leer "Precision 0.7.0" y D6 en `docs/DECISIONES_FUNCIONALES.md`.

## Cierre del plan 0.7.0
