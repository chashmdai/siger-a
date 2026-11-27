# SIGER 0.9.3 - plan de implementación (correcciones)

Correcciones derivadas de la **validación institucional** (validación de uso con la Dirección de UEP, E15 de 0.8.0,
postergado) y de las **observaciones de funcionarios** (D47). Versión 0.9.3, prefijo de gates **C**, registro en
`docs/BITACORA_0_9_3.md`, serie en `docs/PLAN_0_9_0.md`. Empieza con B10 (0.9.2) CERRADO. Solo SIGER, salvo decisión
expresa del responsable. Este plan solo cambia con aprobación del responsable.

## 0. Cómo se arman los gates

Los gates de corrección no existen al planificar: dependen de lo que observe la institución.
1. El responsable registra cada observación en la bitácora (sección "Observaciones"): fecha, origen (UEP, funcionario,
   rol), pantalla o flujo, texto, evidencia (sin datos personales reales).
2. Claude Padre las clasifica: defecto (no cumple una decisión vigente), ajuste de uso (texto, flujo, ayuda), cambio de
   regla (requiere decisión nueva) o fuera de alcance.
3. El responsable decide y aprueba un lote. Claude Padre redacta los gates del lote en la sección 6 de este plan
   (alcance, archivos autorizados, pruebas, aceptación), con un commit de documentación, y los agrega a la bitácora como
   ABIERTO. Un cambio de regla lleva su decisión nueva (siguiente número libre en `DECISIONES_FUNCIONALES.md`).
4. Se ejecutan con el protocolo de siempre. Al final: integración, auditoría y build.

Criterio de corte: lo que llegue después del **26/12/2026** o requiera más de un gate grande pasa a una versión
posterior, salvo un defecto que bloquee la operación.

## 1. Punto de partida

Se verifica en C00 sobre el HEAD posterior a B10: versión 0.9.2, conteos de B08, `v0.9.2` etiquetada.

## 2. Reglas

Las de `PLAN_0_9_2.md` 2, con `v0.9.2` como referencia de `git diff` (Formulario solo lectura; `scripts/` sin cambios
salvo un gate de corrección que lo autorice expresamente).

## 3. Relación con producción y plazo

- La validación institucional se hace sobre producción (0.8.0, 0.9.1 o 0.9.2, la que esté instalada), con usuarios
  reales y datos reales, por el responsable con UEP. No es tarea del ejecutor.
- Paso U de 0.9.3 antes del 31/12/2026 (redacta el gate de integración, ejecuta el responsable).

## 4. Documentación

`DECISIONES_FUNCIONALES.md` (si hay decisiones nuevas), `CLAUDE.md`, `ALCANCE_Y_PENDIENTES.md`, `DESPLIEGUE_VM.md`
(paso U de 0.9.3), `docs/PRUEBAS_MANUALES_0_9_3.md`.

## 5. Protocolo

`PLAN_0_9_1.md` 5 con el prefijo C, esta bitácora y sin gates [FORMULARIO].

## 6. Gates

| Gate | Título | Depende de |
|---|---|---|
| C00 | Línea base y registro de observaciones | B10 |
| CV | Validación de uso con la Dirección de UEP (EXTERNO; antes E15) | - |
| C01-Cnn | Correcciones por lote (se redactan según la sección 0) | C00 y su lote aprobado |
| Cxx-I | Integración MySQL, aceptación y cierre de versión | todos los de corrección |
| Cxx-A | Auditoría independiente (EXTERNO) | Cxx-I |
| Cxx-B | Build de entrega y etiqueta `v0.9.3`; marcar 0.9.3 CERRADA en `PLAN_0_9_0.md` 1 | Ciclo de Cxx-A cerrado |

**C00 - Línea base y registro de observaciones**: conteos sobre el HEAD; `CLAUDE.md` estado "plan 0.9.3 en curso";
copiar a la sección "Observaciones" de la bitácora las que el responsable haya dejado en `BITACORA_0_8_0.md`
(observaciones de funcionarios) y en sus notas; sin cambios de `src`.

## 7. Formato del registro de cierre

`PLAN_0_9_1.md` 7 con el prefijo C y sin la línea del Formulario.

## 8. Prompt universal

El de `docs/PLAN_0_9_0.md` sección 5.
