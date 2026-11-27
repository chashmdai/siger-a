# SIGER 0.9.0 - hoja de ruta de la serie 0.9.x

Documento rector de la serie 0.9.x. Planificación consolidada por el responsable el 2026-11-26 y el 2026-11-27
sobre `v0.8.0` (`842d012`), en producción desde el 2026-11-26. Cada versión 0.9.X es un ciclo completo con plan,
bitácora, diseño, gates, etiqueta y paso U propios. Este documento solo cambia con aprobación del responsable.

Plazo de la serie: **31 de diciembre de 2026**.

## 1. Versiones de la serie

| Versión | Contenido | Prefijo de gates | Documentos | Estado |
|---|---|---|---|---|
| 0.9.1 | Ampliación contractual, parte habilitable: antecedentes complementarios vía Formulario con ClaveÚnica y aviso de cierre por correo | A | `PLAN_0_9_1.md`, `BITACORA_0_9_1.md`, `DISENO_0_9_1.md` | **EN CURSO** |
| 0.9.2 | Operación y pendientes: seguimiento por folio, selector de antecedentes, distribución ágil, sedes, distribución territorial, correo de avisos, hallazgos de 0.8.0 y scripts | B | `PLAN_0_9_2.md`, `BITACORA_0_9_2.md`, `DISENO_0_9_2.md` | PLANIFICADA |
| 0.9.3 | Correcciones de la validación institucional (UEP, E15 postergado de 0.8.0) y observaciones de funcionarios (D47) | C | `PLAN_0_9_3.md`, `BITACORA_0_9_3.md` | PLANIFICADA (gates por definir) |
| siguiente número libre | Bloque PDF/SMS: PDF de cierre adjunto al aviso y SMS al requirente | P | `PLAN_0_9_X_PDF_SMS.md` | BLOQUEADA (insumos institucionales) |

Reglas de la serie:
- Un solo ejecutor a la vez. Una versión empieza cuando la anterior tiene su gate de build CERRADO; el paso U de
  producción de la anterior puede ir en paralelo con el inicio de la siguiente.
- La **versión en curso** es la que esta tabla marca EN CURSO. El gate de build de cada versión la deja CERRADA y marca
  EN CURSO la siguiente, en el mismo commit.
- El bloque PDF/SMS toma el número siguiente al de la última versión etiquetada cuando el responsable registre los
  insumos (plantilla del PDF de cierre y proveedor de SMS) y lo marque EN CURSO; hasta entonces no tiene gates
  ejecutables.
- La validación institucional no es una versión: es una actividad del responsable sobre lo que esté en producción.
  Sus observaciones alimentan 0.9.3.

## 2. Calendario de referencia

| Fechas | Hito |
|---|---|
| 27-30/11 | Gates A00-A02 |
| 30/11-06/12 | Gates A03-A05 |
| 07-10/12 | A06 (auditoría), A07 (build) |
| 10-12/12 | Paso U de SIGER 0.9.1; actualización en sitio del Formulario |
| 12-21/12 | 0.9.2 (B00-B10) |
| 22-23/12 | Paso U de SIGER 0.9.2 |
| 24-31/12 | 0.9.3, y el bloque PDF/SMS si llegaron sus insumos |

Fechas de corte para la Defensoría (las comunica el responsable):
1. Plantilla del PDF de cierre y proveedor de SMS: antes del **19/12/2026**. Después, el bloque pasa a enero.
2. Validación institucional sobre 0.8.0/0.9.1 en producción: primera quincena de diciembre, para que sus correcciones
   quepan en 0.9.3.
3. Certificación de ClaveÚnica (Gobierno Digital): habilita las invitaciones de 0.9.1 por configuración. Si no llega,
   0.9.1 se entrega igual, con las invitaciones apagadas.

El plazo no reduce pruebas, mutaciones, auditoría ni ensayos. Un gate sin sus verificaciones no se cierra; solo el
responsable puede pasarlo a POSTERGADO con fundamento.

## 3. Trabajo en el Formulario Público

Solo 0.9.1 modifica el repositorio `formulario-web`. 0.9.2 y 0.9.3 solo lo leen (B00 confirma que D45 no necesita
cambios del Formulario; si los necesitara, ese punto sale de 0.9.2 junto con D38).

- Un solo plan y una sola bitácora: los gates del Formulario están en `PLAN_0_9_1.md` marcados **[FORMULARIO]** y se
  registran en `BITACORA_0_9_1.md` del repositorio de SIGER.
- La sesión del ejecutor se abre en el clon de SIGER con acceso adicional al clon del Formulario (ruta en la bitácora
  de 0.9.1, definición DI-17). En un gate [FORMULARIO] el ejecutor lee también `CLAUDE.md` y `AGENTS.md` del Formulario
  y respeta sus reglas.
- El código del Formulario se commitea y se publica en `formulario-web` (`main`); el cierre del gate se registra en la
  bitácora de SIGER con el commit del Formulario. Un gate [FORMULARIO] se cierra solo con la CI remota del Formulario
  verde en sus tres jobs (Linux, Windows y Docker).
- Producción del Formulario corre RC1 (`c16d135`, 04/11/2026) en DDN-UBUNTU. Se actualiza **en sitio** con
  `docs/ACTUALIZACION_UBUNTU_PRODUCCION.md` del Formulario (paquete de release, simulacro aislado de restauración y
  migración, ventana coordinada), después del paso U de SIGER 0.9.1. Esa actualización lleva todo lo acumulado en
  `main` del Formulario desde RC1 más lo de 0.9.1.

## 4. Decisiones de la serie (numeración continua)

D44-D49 (hoja de ruta heredada de 0.8.0) se implementan en 0.9.2. Las nuevas son D52-D72:

| Decisiones | Versión | Tema |
|---|---|---|
| D52-D61 | 0.9.1 | Antecedentes complementarios (invitación, enlace, ClaveÚnica, vigencia, admisión automática, cierre, reutilización, habilitación, texto de lo requerido, estado visible) |
| D62-D66 | 0.9.1 | Aviso de cierre por correo |
| D67-D68 | 0.9.1 | Despliegue del Formulario y confianza entre sistemas |
| D69-D72 | 0.9.2 | Selector de antecedentes, seguimiento por folio, distribución ágil, reportes sin TI inconsistente |
| propuestas DS-1 a DS-4 | bloque PDF/SMS | SMS al requirente, pendientes de validación institucional |

El texto completo de cada decisión está en el plan de su versión; el gate 00 de cada versión las copia a
`docs/DECISIONES_FUNCIONALES.md`.

## 5. Prompt universal de continuidad (idéntico en todas las sesiones de la serie)

El responsable inicia cada sesión de Claude Code con `/clear`, o una conversación nueva de Codex, y pega siempre este
texto, sin cambios. No nombra versión ni gate. La fuente de verdad es el repositorio.

> Lee íntegramente `AGENTS.md` (si existe), `CLAUDE.md` y `docs/PLAN_0_9_0.md`. Identifica en su sección 1 la versión marcada EN CURSO y lee íntegramente su plan y su bitácora, y las secciones de su diseño que indique el gate.
> Identifica el primer gate `ABIERTO` según esa bitácora (salta los `CERRADO`, `BLOQUEADO`, `POSTERGADO` y `EXTERNO`) y trabaja exclusivamente en él, respetando el protocolo de la sección 5 de su plan. Si el gate está marcado [FORMULARIO], lee además `AGENTS.md` y `CLAUDE.md` del clon del Formulario indicado en la bitácora.
> Antes de modificar cualquier archivo:
> * Verifica el estado de Git (en ambos repositorios si el gate es [FORMULARIO]), el commit del último gate cerrado, las dependencias del gate y las definiciones que exige.
> * Revisa los antecedentes, decisiones, restricciones y hallazgos aplicables.
> * Realiza el inventario técnico.
> * Presenta el plan de implementación, los archivos que propones modificar, las pruebas previstas (incluidas las de mutación y MySQL) y los riesgos.
> * DETENTE y espera mi aprobación explícita (GO).
>
> Únicamente después del GO:
> * Ejecuta el gate dentro del alcance aprobado.
> * Realiza las pruebas exigidas, incluidas las de regresión y mutación cuando correspondan.
> * Comprueba las condiciones de la sección 2 del plan.
> * Documenta el cierre en la bitácora con el formato de la sección 7 del plan.
> * Revisa el alcance de cada commit y la ausencia de archivos sensibles.
> * Realiza commit y push a `main` en `origin` (y en el Formulario, si el gate lo modifica).
> * Informa el cierre y DETENTE. No comiences el siguiente gate.
>
> No modifiques estados ni registros de gates `EXTERNO`. No dependas de conversaciones anteriores ni de información no documentada. Si falta información, existe una discrepancia o necesitas ampliar el alcance, detente y consulta antes de proceder.
> Responde siempre en español.

## 6. Roles

- **Responsable**: decide, da los GO, ejecuta los pasos en producción (SIGER en DDN-SIGER, solo `D:`; Formulario en
  DDN-UBUNTU) con autorización expresa, y gestiona los insumos institucionales.
- **Ejecutor**: Claude Code o Codex (GPT), alternados según disponibilidad, con el mismo prompt. Al cambiar de agente,
  el plan del gate y su GO se registran antes en la bitácora ("GO aprobados pendientes de ejecución").
- **Claude Padre** (Claude en el chat): revisa el plan que presenta el ejecutor, entrega el texto del GO listo para
  pegar, verifica cada cierre en el repositorio y hace la auditoría independiente (gate EXTERNO de cada versión).
- **Apoyo**: ChatGPT acompaña al responsable en la operación de las VM y en el versionado manual cuando corresponda.

## 7. Pendientes fuera de la serie (no tienen gate)

- U7 de 0.8.0: activación de los catálogos estadísticos en producción, coordinada con UEP.
- Limpieza en TEST-SIGER de las carpetas de ensayo de 0.8.0 (`D:\Ensayo-E12` a `D:\Ensayo-E17-registro`,
  `D:\Auditoria-E16`), después de revisarlas.
- Motor antivirus definitivo (ClamAV o Kaspersky): depende de TI; mientras tanto rige D17 (análisis en origen).
- Acceso por red de SIGER (paso de RDP a red): 0.9.2 entrega el script (D49); su ejecución en producción depende de TI
  y de la red institucional.
- D38 (instituciones de los hechos como dimensión estadística): requiere cambios del Formulario; queda para una versión
  posterior a la serie.
- P-005 (memoria JVM): la medición de E14 (máximo 460 MiB, 45 % de 1 GiB) no lo justifica; se reevalúa con datos reales.
- P-006 (concurrencia): fuera de la serie, salvo el ZIP por flujo de 0.9.2. El lock global no se modifica.
- P-007.2 (unidad sugerida del nivel central) y P-007.4 (posibles duplicados): requieren definición institucional y
  revisión de privacidad.
- División del código del frontend (hallazgo de E16) y corte sin lock global (detención de E10): solo si el volumen lo
  exige.
