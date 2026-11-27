# SIGER 0.9.1 - bitácora de gates

Estado vivo del trabajo de `docs/PLAN_0_9_1.md` (diseño de referencia: `docs/DISENO_0_9_1.md`; serie:
`docs/PLAN_0_9_0.md`). Cada sesión de Claude Code o Codex toma el primer gate ABIERTO, lo cierra aquí con el formato de
la sección 7 del plan, hace commit y push, y se detiene.
Estados: ABIERTO (por hacer), CERRADO (verificado, con commit y push), BLOQUEADO (detenido o a la espera de una
definición), POSTERGADO (fuera del cierre por decisión expresa del responsable, con fundamento), EXTERNO (lo realiza el
responsable o Claude Padre; el ejecutor lo salta y nunca edita su registro ni su estado).
Cada sesión empieza con el prompt universal de `PLAN_0_9_0.md` sección 5, sin nombrar gates.

## Estado

| Gate | Título | Repos | Equipo | Estado |
|---|---|---|---|---|
| A00 | Línea base, decisiones, contrato de antecedentes y diseño verificado | ambos [FORMULARIO] | TEST-SIGER | ABIERTO |
| A01 | Invitaciones en SIGER: modelo, comandos, revocación y canal firmado hacia el Formulario (servidor) | SIGER | TEST-SIGER | ABIERTO |
| A02 | Recepción de antecedentes y admisión automática (servidor) | SIGER | TEST-SIGER | ABIERTO |
| A03 | Interfaz del Caso y correos al ciudadano: invitación y aviso de cierre | SIGER | TEST-SIGER | ABIERTO |
| A04 | Formulario: invitaciones, ingreso con ClaveÚnica, aporte de antecedentes y variante de entrega | Formulario [FORMULARIO] | TEST-SIGER | ABIERTO |
| A05 | Integración de punta a punta, MySQL, aceptación y cierre de versión | ambos [FORMULARIO] | TEST-SIGER (CachyOS si A00 lo determina) | ABIERTO |
| A06 | Auditoría independiente | ambos | - | EXTERNO |
| A07 | Build de entrega de SIGER y etiqueta | SIGER | TEST-SIGER | ABIERTO |
| A08 | Paquete del Formulario, simulacro aislado y etiqueta | Formulario [FORMULARIO] | CachyOS | ABIERTO |

Un gate ABIERTO cuyas dependencias (sección 6 del plan) no están CERRADO se detiene en sus precondiciones. A07 exige
el ciclo de A06 cerrado y ningún gate de corrección ABIERTO. Un gate que se abre en un equipo distinto del de la
columna "Equipo" se detiene sin editar e indica al responsable a cuál cambiarse (plan, sección 5).

## Equipos y clones

| Equipo | Sistema | Clon de SIGER | Clon del Formulario | Uso |
|---|---|---|---|---|
| TEST-SIGER | Windows Server | `C:\sigera` | `D:\formulario-web` | Equipo principal: todo SIGER y el desarrollo del Formulario. La sesión se abre en `C:\sigera` con acceso adicional a `D:\formulario-web`. |
| CachyOS | Linux (Arch) con Docker | `~/sigera` | `~/formulario-web` | Solo A08 (paquete y simulacro del Formulario) y A05 si A00 lo determina. |

## Decisiones del responsable

| Decisión | Antes de | Respuesta |
|---|---|---|
| D58-D67 antecedentes complementarios | A00 | 2026-11-26: aprobadas por el responsable (invitación solo con RUN ClaveÚnica y correo declarado; enlace con token por correo de Graph; ingreso exclusivo con ClaveÚnica y RUN coincidente; 17 días corridos; una entrega con texto y adjuntos; admisión automática al CAS solicitante; la invitación muere al cerrarse el CAS que la emitió; reutilización de lo existente; apagada hasta la certificación de ClaveÚnica; texto de lo requerido visible solo tras ClaveÚnica; estado visible y revocación en el CAS). |
| D68-D72 aviso de cierre | A00 | 2026-11-26: aprobadas (check opcional al cerrar el CAS, desmarcado; a todo requirente con correo declarado, sin exigir ClaveÚnica; texto mínimo en 0.9.1 y PDF en el bloque PDF/SMS; textos fijos definidos por el equipo). |
| D73 despliegue del Formulario | A00 | 2026-11-26: actualización en sitio, sin reinstalación limpia; primero SIGER y después el Formulario. |
| D74 confianza Formulario-SIGER | A00 | 2026-11-26: sin credenciales nuevas; invitaciones firmadas con la clave ACK de SIGER y etiqueta `SIGER-INVITACION-v1`. |
| Destino del vínculo de antecedentes solicitados | A02 | 2026-11-26: al CAS que lo solicitó (admisión automática). El vínculo manual de Distribución sigue siendo solo al REQ (D75, 0.9.2). |
| Plan 0.9.1 (prefijo A, A00-A08, con equipo por gate), serie 0.9.x y prompt universal de la serie | A00 | 2026-11-27: aprobado por el responsable para iniciar la ejecución. |

## Definiciones pendientes

| Def. | Pregunta | Antes de | Propuesta | Respuesta |
|---|---|---|---|---|
| DI-16 | Si el cierre del Caso pasa por solicitud y aprobación, ¿en qué acto se marca el aviso? | A01 | Se marca al cerrar directamente o al solicitar el cierre; si hay aprobación, el correo sale al concretarse el cierre; si la solicitud se retira, no sale. La jefatura que aprueba ve el check y puede desmarcarlo. | 2026-11-27: Se aprueba la propuesta, con estas precisiones. El aviso se marca al cerrar directamente o al solicitar el cierre. La marca queda en el Caso (quién y cuándo, en V25). Quien concreta el cierre ve la marca y puede cambiarla, y vale lo que se decide en ese acto. Retirar la solicitud (CASE_WITHDRAW_CLOSE) o la devolución de la jefatura (CASE_RETURN) borran la marca. El correo se encola solo en el mismo COMMIT del cierre concretado. Si la COM tiene restricciones, la confirmación de DI-20 se pide en ese acto. |
| DI-17 | Ruta del clon de `formulario-web` en cada equipo del ejecutor | A00 | `D:\formulario-web` en TEST-SIGER; la sesión se abre en `C:\sigera` con acceso adicional a esa ruta | 2026-11-27: TEST-SIGER `D:\formulario-web` (principal; sesión en `C:\sigera` con acceso adicional); CachyOS `~/formulario-web` solo para A08 y, si A00 lo determina, A05 (tabla "Equipos y clones"). Actualizada 2026-11-27: clon en D:\formulario-web por espacio en C:. |
| DI-18 | ¿Una sola invitación vigente por Caso a la vez? | A01 | Sí: para una nueva, el profesional revoca la vigente o espera a que se use o venza | 2026-11-27: Sí. Una sola invitación activa por Caso. Cuentan como activas PUBLICANDO y VIGENTE no vencida; FALLIDA, ENTREGADA, VENCIDA y REVOCADA liberan el cupo. El servidor lo comprueba bajo el lock del comando, no con un índice parcial, para que funcione igual en H2 y MySQL. Si ya hay una activa, invite_eligible responde "Ya hay una solicitud de antecedentes vigente". |
| DI-19 | Texto final de los correos al ciudadano | A03 | `DISENO_0_9_1.md` 5.3; se aprueba en el GO de A03 | pendiente |
| DI-20 | Si la COM registra restricciones de contacto o "no tiene una forma segura de contacto" (p. ej., un NNA), ¿se puede invitar o avisar por correo? | A01 | No se ofrece por defecto; el profesional ve la restricción y solo puede continuar confirmando expresamente que leyó la restricción y que el envío es seguro (queda auditado) | 2026-11-27: Se permite, pero con confirmación expresa. Si contact_restrictions de la COM de origen (la misma de la que sale el correo) no está vacío, SIGER muestra ese texto y exige marcar "Leí las restricciones de contacto y enviar a este correo es seguro". Sin esa marca, el servidor rechaza el comando, no solo la interfaz. Rige igual para la invitación (CASE_INVITE) y para el aviso de cierre (en el acto de DI-16). Queda auditado quién confirmó y cuándo, sin copiar el texto de la restricción. |

## GO aprobados pendientes de ejecución

### A00 - plan y GO aprobados (2026-11-27; plan de Claude Code (Opus 5.5); base SIGER 187bcd2, Formulario 6f886eb)

**a) Plan de A00 (presentado por Claude Code; sin ediciones al presentarlo)**

1. Precondiciones (verificadas al planificar):
   - Equipo `DDN-TEST-SIGER` (Windows Server); clones `C:\sigera` y `D:\formulario-web` (DI-17).
   - SIGER: `main` limpio y al día con `origin`, HEAD `187bcd2`; último gate cerrado F03 (`99c31b2` = `v0.8.1`),
     seguido solo de 5 commits de planificación. Formulario: `main` limpio y al día, HEAD `6f886eb`; última CI de
     `main` (run 43776774182 sobre `6f886eb`) exitosa.
   - Dependencias: ninguna; DI-17 respondida (DI-16, DI-18 y DI-20 también, no se usan en A00).
   - Herramientas: MySQL 8.4 en `D:\Herramientas\mysql-8.4`; libre C: 36 GB y D: 45 GB; WSL Ubuntu-24.04 con JDK 25,
     Maven 3.9.15, Node 24.22.1 y Docker (servicio inactivo); sin `gh` (la CI se consulta con la API pública de GitHub).
   - `git diff v0.8.0 v0.8.1 -- backend/src/main` vacío.
2. Referencias `DISENO_0_9_1.md` 1: S1-S14 y F1-F7 coinciden con el código; sin diferencias que afecten decisiones.
   Tres precisiones:
   - Condición exacta de D58 (DISENO 2.2): `external_person` de la COM de origen del REQ con
     `person_role='PRESENTADOR'`, `authenticated=TRUE`, `run_source='CLAVE_UNICA_USERINFO'` (fuente del Formulario,
     `IdentificacionCiudadana.Fuente`, nivel `IDENTIDAD_AUTENTICADA`) y `run_valid=TRUE`. Solo REQ originados en el
     Formulario de adultos con ClaveÚnica; nunca COM manuales ni NNA.
   - F2: las claves públicas de SIGER están en el Formulario en `publico.comprobante.institutional-trusted-keys`
     (mapa keyId -> PEM): la firma de invitación debe llevar el keyId.
   - El ACK de SIGER firma el JSON crudo sin etiqueta (`FormularioAck.sign`); el prefijo `SIGER-INVITACION-v1\n` separa
     ambos dominios.
3. Hallazgos: H1 (`mail_outbox` de V9 exige `notice_id` y `recipient_id` NOT NULL con FK a `inbox_notice`/`staff`, sin
   destinatario externo, plantilla ni cuerpo; CASE_INVITE de A01 debe encolar el correo en el mismo COMMIT, pero la
   extensión del outbox era de A03 sin migración). H2 (RC1 responde 400 ante una versión desconocida en
   `X-Formulario-Versiones`, `RecepcionFormularioJdbc:125`: SIGER 0.9.1 no puede pedir la versión nueva antes de
   F-0.9.1). H3 (el Formulario exige POSIX; WSL de TEST-SIGER lo ofrece: comprobar si su JAR arranca en WSL con H2 e
   identidad simulada; si no, hallazgo "A05 en CachyOS"; la QA WSL existente `~/proyectos/formulario-web` (`bfb9506`)
   no se toca, se trabaja en `~/ensayo-a00/` clonado localmente de `D:\formulario-web`).
4. Vía (DISENO 4.1): misma ruta `/integracion/v3/formulario-completo/entregas`, misma tabla `citizen_submission`,
   misma reserva, descarga por rangos, ACK y `/fallo`; versión documental negociada `ANT-1.0` (cabe en
   `document_version VARCHAR(8)`; la reserva filtra por versión y la compara con el documento firmado). Evidencia:
   reutiliza `ComprobanteDigital`, `CanonFormulario` y el ACK; un consumidor que no la pide no la recibe; una cola
   propia exigiría rutas, tabla y ciclo nuevos sin beneficio.
5. Contrato `contratos/antecedentes-complementarios-v1.0.md` (Formulario):
   - Invitación: `POST /integracion/v3/antecedentes/invitaciones` con `Authorization: Bearer`, `X-Siger-Clave: <keyId>`,
     `X-Siger-Firma` (Ed25519 base64url sin relleno sobre `"SIGER-INVITACION-v1\n"` + bytes exactos del cuerpo; se
     verifica antes de leer el JSON; lectura estricta). Cuerpo: `version` "1.0", `publicId`, `tokenSha256`,
     `runEsperado` (número y DV), `textoSolicitado` (hasta 1.000), `venceEn`, `folioSiger`. Respuestas 201, 200
     idempotente, 409 (mismo `publicId`, otro cuerpo), 400, 401, 403 (firma, clave o etiqueta), 503 apagado.
   - Revocación: `POST .../invitaciones/{publicId}/revocacion`, misma firma, idempotente (el GO fija el caso del
     `publicId` desconocido).
   - Entrega `ANT-1.0`: sobre `{comunicacion,sha256,huellaProcedencia,custodia}`; `comunicacion` con `version`, `id`,
     `invitacion` (publicId), `recibidaEn`, `avisoVersion`, `identidad` (solo RUN número y DV, fuente
     `CLAVE_UNICA_USERINFO`, nivel y evidencia; sin nombres), `texto` y `adjuntos` (descriptores como 1.1; al menos
     uno de los dos); huellas `CanonFormulario` en dominios propios; sello `ComprobanteDigital` sin cambios; ACK sin
     cambios (versión 1.0, canal `FORMULARIO_WEB_PUBLICO`); códigos de fallo, entre ellos `CONTRATO_INVALIDO`.
   - Fixtures en `contratos/fixtures/` firmados con claves de prueba: invitación de creación y de revocación (cuerpo
     exacto guardado como texto, inmune a CRLF), vector negativo sin etiqueta, entrega solo texto y entrega con texto
     y un adjunto (bytes definidos en el generador), ACK sintético, claves públicas de prueba del Formulario y de SIGER.
   - Generador `AntecedentesFixtures` en las pruebas de SIGER (claves efímeras, nunca escribe privadas, directorio de
     salida nuevo). Verificación cruzada con `CanonFormulario` y `ComprobanteDigital` reales en una prueba temporal en
     la copia WSL, no commiteada, como evidencia. `docs/API.md` del Formulario: rutas nuevas marcadas
     "implementación en A04".
6. Archivos: SIGER `docs/DECISIONES_FUNCIONALES.md` ("Precisión 0.9.1", D58-D74 textuales), `CLAUDE.md` (estado
   "plan 0.9.1 en curso"), `AGENTS.md` (remite a `CLAUDE.md` y al prompt de `PLAN_0_9_0.md` 5), esta bitácora,
   `docs/DISENO_0_9_1.md` (solo diferencias aprobadas), `backend/src/test/resources/formulario/` (fixtures y
   `PROCEDENCIA.txt` con commit, ruta y SHA-256), `AntecedentesFixtures.java`, `AntecedentesContractTest.java`,
   `resources/upgrade/siger-0.8.0-sintetico.sql` (MySQL) y `siger-0.8.0-sintetico-h2.sql`,
   `MySql84Upgrade080Test.java`, `H2Upgrade080Test.java`. Formulario: `contratos/antecedentes-complementarios-v1.0.md`,
   fixtures nuevos en `contratos/fixtures/`, `docs/API.md`. Sin cambios en `backend/src/main` ni `frontend/src` de
   SIGER, nada del Formulario fuera de contratos y documentación, ni en `scripts/` o `.mvn/`.
7. Volcados 0.8.0 (V24): copia temporal de `v0.8.0` en el scratchpad con una clase generadora solo de esa copia
   (procedimiento de E01), escenario con los comandos de 0.8.0: base de E01, COM del Formulario adulto con ClaveÚnica
   (`FormularioDocs`) con correo, REQ y Caso abierto, otra con restricciones de contacto, Caso cerrado, vínculo
   ADMITIDA con `target_case_id`, catálogos vigentes con clasificaciones y derechos, ficha de ingreso, un corte y una
   vista guardada. Dos volcados del mismo escenario: `mysqldump` (opciones de G13) y `SCRIPT` de H2 (H2 no carga un
   volcado de mysqldump); claves de cuentas invalidadas; revisión del archivo como en E01. Pruebas: carga,
   `migrate-only` y arranque normal; filas por tabla iguales; huellas de `communication_original` y `external_*`
   iguales; Flyway hasta la última versión (hoy sin nada que migrar; cobra sentido con V25).
8. Pruebas: línea base sobre el HEAD antes de editar (JUnit H2 completo; MySQL 8.4 aislado en `D:\Ensayo-A00\mysql`,
   puerto 3417, procedimiento E08b, con apagado, puerto libre y carpeta borrada al final; frontend `typecheck` y
   `test`; `Check-Scripts.ps1`; Formulario en la copia WSL: React lint y test, Maven sin carga y controles Python);
   después, las mismas suites con las pruebas nuevas sin bajar conteos y la CI remota del Formulario verde en los tres
   jobs. Mutaciones temporales y revertidas: quitar la etiqueta al verificar; alterar un byte del cuerpo firmado;
   aceptar un campo fuera de la lista; aceptar el vector sin etiqueta; huella de `PROCEDENCIA.txt` distinta; fila de
   `external_field` alterada en la prueba de actualización. Una prueba demuestra que `ExternalOriginal.parse` rechaza
   hoy `ANT-1.0`.
9. Riesgos: canonicalización portada de SIGER distinta de Jackson 3 del Formulario (mitiga la verificación cruzada);
   CRLF en fixtures (cuerpo como texto y huellas normalizadas a LF); tiempo de la suite MySQL y de generar el volcado
   con `v0.8.0`; CI remota consultada por la API pública; material sensible en commits (revisión de volcados y
   `git show --stat` antes de cada push).
10. Orden de cierre: commit del Formulario `gate A00: linea base, decisiones y contrato de antecedentes`, push y CI
    verde; luego commit de SIGER con el mismo título, `PROCEDENCIA.txt` y la bitácora citando el commit del
    Formulario; push; detenerse.

**b) GO de A00 (texto íntegro del responsable)**

GO de A00. Se aprueba el plan registrado, con estas condiciones:
- Vía: la misma cola formulario-completo, con la versión documental ANT-1.0.
- Firma: cabeceras X-Siger-Clave, con el keyId del mapa publico.comprobante.institutional-trusted-keys, y X-Siger-Firma, Ed25519 en base64url sin relleno sobre SIGER-INVITACION-v1\n más los bytes exactos del cuerpo. Se verifica antes de leer el JSON, y la lectura es estricta: lista cerrada de campos y sin claves repetidas.
- Condición D58: un external_person de la COM de origen del REQ con person_role='PRESENTADOR', authenticated=TRUE, run_source='CLAVE_UNICA_USERINFO' y run_valid=TRUE. Solo los recorridos ADULTO_CU permiten invitar.
- Revocación: un publicId desconocido no responde 404. El Formulario lo registra como REVOCADA y responde 200. Una creación posterior con ese publicId responde 409 y no revive la invitación. El contrato lo dice expresamente.
- Evidencia de identidad: el contrato fija el contenido exacto de identidad.evidencia, sin identificadores de sesión, tokens ni cookies.

Fixtures:
- La fuente canónica es contratos/fixtures/ del Formulario.
- AntecedentesFixtures se commitea, pero se ejecuta solo con una propiedad de sistema explícita, nunca en la suite normal ni en la CI. Usa claves efímeras y no escribe claves privadas.
- PROCEDENCIA.txt cita el commit del Formulario, la ruta, el SHA-256 y el generador.
- La verificación cruzada se hace en ~/ensayo-a00/ de WSL, sin tocar ~/proyectos/formulario-web, y queda como evidencia.
- Volcados 0.8.0: MySQL y H2, generados desde una copia temporal de v0.8.0, con datos sintéticos y claves invalidadas, y revisados antes del commit. Pruebas H2Upgrade080Test y MySql84Upgrade080Test.
- H3: comprueba si el JAR del Formulario arranca en WSL con H2 y la identidad simulada. Registra el resultado: A05 sigue en TEST-SIGER, o queda el hallazgo "A05 en CachyOS".
- H1 y H2: quedan resueltos en el Traspaso; A00 no los implementa. Una prueba demuestra que ExternalOriginal.parse rechaza hoy ANT-1.0.

Pruebas:
- Línea base completa antes de editar, y al final sin bajar ningún conteo.
- MySQL en D:\Ensayo-A00\mysql, puerto 3417.
- Las mutaciones del plan, más una firma con un keyId desconocido.
- Ante un corte por cuota, aplica la sección 5 del plan.

Cierre:
- Primero el Formulario, con su CI remota verde en los tres jobs; después SIGER, con PROCEDENCIA.txt y la bitácora citando el commit del Formulario.
- Retira la entrada de A00 de "GO aprobados pendientes de ejecución".
- Registra como desvío quién planificó y quién ejecutó.

(El Traspaso de H1 y H2 que acompaña este GO quedó en el detalle de A01 a A05 de `docs/PLAN_0_9_1.md` y en
"Diferencias aprobadas" de `docs/DISENO_0_9_1.md`.)

## Pasos en producción (los ejecuta el responsable, con autorización expresa)

| Paso | Descripción | Requisito | Estado |
|---|---|---|---|
| U-0.9.1 | Actualización de SIGER a 0.9.1 en DDN-SIGER (solo `D:`), con respaldo y verificación en la misma ventana | A07 CERRADO | PENDIENTE |
| F-0.9.1 | Actualización en sitio del Formulario en DDN-UBUNTU con el paquete y el simulacro de A08 y su procedimiento | A08 CERRADO y U-0.9.1 conforme | PENDIENTE |
| C-0.9.1 | Encender el aviso de cierre (`siger.mail.citizen.enabled=true`) | U-0.9.1 conforme | PENDIENTE |
| H-0.9.1 | Encender las invitaciones en ambos sistemas y acreditar un ingreso real con datos propios del responsable | F-0.9.1 conforme y ClaveÚnica certificada | PENDIENTE |

## Registro de gates

(sin registros todavía)
