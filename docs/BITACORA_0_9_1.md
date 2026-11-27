# SIGER 0.9.1 - bitácora de gates

Estado vivo del trabajo de `docs/PLAN_0_9_1.md` (diseño de referencia: `docs/DISENO_0_9_1.md`; serie:
`docs/PLAN_0_9_0.md`). Cada sesión de Claude Code o Codex toma el primer gate ABIERTO, lo cierra aquí con el formato de
la sección 7 del plan, hace commit y push, y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado, con commit y push), BLOQUEADO (detenido o a la espera de una
definición), POSTERGADO (fuera del cierre por decisión expresa del responsable, con fundamento), EXTERNO (lo realiza el
responsable o Claude Padre; el ejecutor lo salta y nunca edita su registro ni su estado).
Cada sesión empieza con el prompt universal de `PLAN_0_9_0.md` sección 5, sin nombrar gates.

## Estado

| Gate | Título | Repos | Estado |
|---|---|---|---|
| A00 | Línea base, decisiones, contrato de antecedentes y diseño verificado | ambos [FORMULARIO] | ABIERTO |
| A01 | Invitaciones en SIGER: modelo, comandos, revocación y canal firmado hacia el Formulario (servidor) | SIGER | ABIERTO |
| A02 | Recepción de antecedentes y admisión automática (servidor) | SIGER | ABIERTO |
| A03 | Interfaz del Caso y correos al ciudadano: invitación y aviso de cierre | SIGER | ABIERTO |
| A04 | Formulario: invitaciones, ingreso con ClaveÚnica, aporte de antecedentes y variante de entrega | Formulario [FORMULARIO] | ABIERTO |
| A05 | Integración de punta a punta, MySQL, aceptación y cierre de versión | ambos [FORMULARIO] | ABIERTO |
| A06 | Auditoría independiente | ambos | EXTERNO |
| A07 | Build de entrega, paquete del Formulario y etiqueta | ambos [FORMULARIO] | ABIERTO |

Un gate ABIERTO cuyas dependencias (sección 6 del plan) no están CERRADO se detiene en sus precondiciones. A07 exige
el ciclo de A06 cerrado y ningún gate de corrección ABIERTO.

## Clon del Formulario

| Equipo | Ruta del clon de `formulario-web` | Registrado |
|---|---|---|
| TEST-SIGER (Windows) | (DI-17) | - |
| Equipo Linux del responsable | (DI-17) | - |

## Decisiones del responsable

| Decisión | Antes de | Respuesta |
|---|---|---|
| D52-D61 antecedentes complementarios | A00 | 2026-11-26: aprobadas por el responsable (invitación solo con RUN ClaveÚnica y correo declarado; enlace con token por correo de Graph; ingreso exclusivo con ClaveÚnica y RUN coincidente; 17 días corridos; una entrega con texto y adjuntos; admisión automática al CAS solicitante; la invitación muere al cerrarse el CAS que la emitió; reutilización de lo existente; apagada hasta la certificación de ClaveÚnica; texto de lo requerido visible solo tras ClaveÚnica; estado visible y revocación en el CAS). |
| D62-D66 aviso de cierre | A00 | 2026-11-26: aprobadas (check opcional al cerrar el CAS, desmarcado; a todo requirente con correo declarado, sin exigir ClaveÚnica; texto mínimo en 0.9.1 y PDF en el bloque PDF/SMS; textos fijos definidos por el equipo). |
| D67 despliegue del Formulario | A00 | 2026-11-26: actualización en sitio, sin reinstalación limpia; primero SIGER y después el Formulario. |
| D68 confianza Formulario-SIGER | A00 | 2026-11-26: sin credenciales nuevas; invitaciones firmadas con la clave ACK de SIGER y etiqueta `SIGER-INVITACION-v1`. |
| Destino del vínculo de antecedentes solicitados | A02 | 2026-11-26: al CAS que lo solicitó (admisión automática). El vínculo manual de Distribución sigue siendo solo al REQ (D69, 0.9.2). |
| Plan 0.9.1 (prefijo A, A00-A07), serie 0.9.x y prompt universal de la serie | A00 | 2026-11-27: aprobado por el responsable para iniciar la ejecución. |

## Definiciones pendientes

| Def. | Pregunta | Antes de | Propuesta | Respuesta |
|---|---|---|---|---|
| DI-16 | Si el cierre del Caso pasa por solicitud y aprobación, ¿en qué acto se marca el aviso? | A03 | Se marca al cerrar directamente o al solicitar el cierre; si hay aprobación, el correo sale al concretarse el cierre; si la solicitud se retira, no sale. La jefatura que aprueba ve el check y puede desmarcarlo. | pendiente |
| DI-17 | Ruta del clon de `formulario-web` en cada equipo del ejecutor | A00 | `C:\formulario-web` en TEST-SIGER; la sesión se abre en `C:\sigera` con acceso adicional a esa ruta | pendiente |
| DI-18 | ¿Una sola invitación vigente por Caso a la vez? | A01 | Sí: para una nueva, el profesional revoca la vigente o espera a que se use o venza | pendiente |
| DI-19 | Texto final de los correos al ciudadano | A03 | `DISENO_0_9_1.md` 5.3; se aprueba en el GO de A03 | pendiente |
| DI-20 | Si la COM registra restricciones de contacto o "no tiene una forma segura de contacto" (p. ej., un NNA), ¿se puede invitar o avisar por correo? | A01 | No se ofrece por defecto; el profesional ve la restricción y solo puede continuar confirmando expresamente que leyó la restricción y que el envío es seguro (queda auditado) | pendiente |

## GO aprobados pendientes de ejecución

(vacío)

## Pasos en producción (los ejecuta el responsable, con autorización expresa)

| Paso | Descripción | Requisito | Estado |
|---|---|---|---|
| U-0.9.1 | Actualización de SIGER a 0.9.1 en DDN-SIGER (solo `D:`), con respaldo y verificación en la misma ventana | A07 CERRADO | PENDIENTE |
| F-0.9.1 | Actualización en sitio del Formulario en DDN-UBUNTU con el paquete de A07 y su procedimiento | U-0.9.1 conforme | PENDIENTE |
| C-0.9.1 | Encender el aviso de cierre (`siger.mail.citizen.enabled=true`) | U-0.9.1 conforme | PENDIENTE |
| H-0.9.1 | Encender las invitaciones en ambos sistemas y acreditar un ingreso real con datos propios del responsable | F-0.9.1 conforme y ClaveÚnica certificada | PENDIENTE |

## Registro de gates

(sin registros todavía)
