# SIGER 0.9.1 - diseño de referencia

Diseño de `docs/PLAN_0_9_1.md` (decisiones D52-D68). Las referencias de la sección 1 se verificaron al planificar
(SIGER `4b482be`, Formulario `6f886eb`); A00 las vuelve a verificar. Una diferencia aprobada se registra al final de
este documento ("Diferencias aprobadas").

## 1. Referencias verificadas

### 1.1 SIGER

| Ref. | Pieza | Hecho relevante |
|---|---|---|
| S1 | `V1__siger_a.sql` `document_item` | Un documento cuelga de exactamente una COM, un REQ o un CAS (CHECK). |
| S2 | `Documents.java` (~l. 151, 157, 169) | Los de la COM son originales de ingreso (`document_type` INGRESO), no se versionan ni se reservan; los de REQ/CAS son de gestión (OFICIO, INFORME, CORREO, CONSTANCIA, OTRO). |
| S3 | `V8__consumidor_formulario.sql` `external_document` | Vínculo del original del Formulario con su operación y adjunto; el archivo vive en `document_item` de la COM. |
| S4 | `V1__siger_a.sql` `communication_link` | `communication_id` UNIQUE (un vínculo por COM), `requirement_id`, `state` (PROPUESTA por defecto), `target_case_id`, decisión, `returned_*` (V4). |
| S5 | `Queries.java` (~l. 185, 247) | La ficha del Caso entrega `citizen_documents` = originales de la COM de origen del REQ más los de COM con vínculo ADMITIDA al Caso y no devuelto (`incomingDocuments`), si `Access.caseSourceDocuments`. |
| S6 | `frontend/src/features/detail/Files.tsx` (~l. 67) | Panel "Documentos del requiriente" (solo lectura, con folio de origen) separado del panel de documentos de gestión. |
| S7 | `FormularioClient.java` (l. 26) | Cliente de `/integracion/v3/formulario-completo/entregas`, Bearer, CA propia, sin redirecciones. |
| S8 | `FormularioReception.java` (~l. 45-65) | Registra COM, originales (cuarentena o D17), comprobante y ACK pendiente en un COMMIT; no crea SOL, REQ ni Caso. |
| S9 | `FormularioPull.java` (~l. 50) | Par ACK desde DPAPI (PEM descifrado por el lanzador) o archivos privados. |
| S10 | `MailOutbox.java` | Outbox durable de avisos internos (estados PENDIENTE, EN_INTENTO, ACEPTADO, REINTENTABLE, RECHAZADO, AMBIGUO); exige `siger.mail.allowed-domains`; destinatario = cuenta vigente; sin datos de expedientes. |
| S11 | `V4__bandejas_y_continuidad.sql` | `communication.contact_email`, `contact_phone`, `contact_restrictions`. |
| S12 | `V7__normalizacion_comunicacion_externa.sql` `external_person` | `person_role`, `authenticated`, `run_number`, `run_dv`, `run_source`, `run_valid`. |
| S13 | `CaseCommands.java` (~l. 27, 100) | `CASE_CLOSE`, `CASE_REQUEST_CLOSE`, `CASE_WITHDRAW_CLOSE` en `caseCommand`. |
| S14 | `ImmutabilityTest`, `InstitutionalReaderTest` | Todo comando nuevo se clasifica y entra al barrido de lectura institucional. |

### 1.2 Formulario

| Ref. | Pieza | Hecho relevante |
|---|---|---|
| F1 | `IntegracionFormularioController.java` | Rutas `/integracion/v3/formulario-completo/entregas` (extensión optativa). |
| F2 | `custodia/GarantiaCustodia.java` | Verifica firmas con claves públicas propias y con claves `institution` (SIGER) ya configuradas. |
| F3 | `identidad/ClaveUnicaProperties.java`, `CallbackAvailabilityFilter.java` | ClaveÚnica con `callback-approved` (`CU_CALLBACK_APPROVED`); sin aprobación, el flujo de autorización no se ofrece. |
| F4 | `identidad/IdentidadVerificada.java` | Identidad autenticada de la sesión. |
| F5 | `formulario/AdjuntosFormulario.java`, `EscanerClamAv.java` | Carga, inspección y límites de adjuntos. |
| F6 | `db/publico/{h2,mysql}/V13__origen_reserva_multimedia.sql` | Última migración. |
| F7 | `docs/ACTUALIZACION_UBUNTU_PRODUCCION.md` | Procedimiento de actualización en sitio (no ejecutado aún en producción). |

## 2. Modelo de datos de SIGER (V25)

`case_invitation`
- `id`, `case_id`, `requirement_id`, `public_id` (UUID que conoce el Formulario), `token_sha256` (CHAR 64, UNIQUE),
  `expected_run` (RUN normalizado del requirente autenticado), `request_text` (hasta 1.000 caracteres, D60),
  `contact_email` (instantánea del correo declarado al invitar), `created_by`, `created_ms`, `expires_ms`,
  `state` (PUBLICANDO, VIGENTE, ENTREGADA, VENCIDA, REVOCADA, FALLIDA), `published_ms`, `delivered_ms`,
  `delivered_communication_id`, `revoked_ms`, `revoked_by`, `revoke_cause` (MANUAL, CIERRE_CASO), `revision`.
- Vigente: `state='VIGENTE' AND expires_ms > now`. VENCIDA se calcula por tiempo y se fija al leer o al recibir.
- `expires_ms`: fin del día 17 contado desde la creación, 23:59:59 de America/Santiago (D55); el correo informa la fecha.
- Una vigente por Caso (DI-18): índice o comprobación bajo el lock del comando.

`invitation_sync` (outbox hacia el Formulario)
- `id`, `invitation_id`, `operation` (CREAR, REVOCAR), `state` (PENDIENTE, EN_INTENTO, CONFIRMADA, REINTENTABLE,
  RECHAZADA), `attempts`, `last_attempt_ms`, `last_status`, `created_ms`. Sin token ni texto (el payload se arma al
  enviar desde `case_invitation`; el token no se guarda: ver 3).

Elegibilidad (D52), regla única en el servidor y publicada en la ficha como `invite_eligible` con razón:
1. Caso abierto y actor con escritura en el Caso.
2. Requirente con identidad autenticada por ClaveÚnica: `external_person` del requirente de la COM de origen del REQ
   con `authenticated = true` y RUN válido (A00 fija la condición exacta: rol del requirente y `run_source`).
3. `communication.contact_email` de esa COM no vacío y con formato válido.
4. Restricciones de contacto (DI-20): si `contact_restrictions` tiene contenido o la vía elegida es "No tiene una forma
   segura de contacto", aplica la respuesta de DI-20.
5. Sin otra invitación vigente en el Caso (DI-18).
6. `siger.antecedentes.enabled=true`.

## 3. Canal SIGER → Formulario

- Crear: `CASE_INVITE` genera el token (32 bytes de `SecureRandom`, base64url), guarda solo su SHA-256, crea la
  invitación en PUBLICANDO y una fila CREAR en `invitation_sync`, todo en el mismo COMMIT. **El token en claro solo vive
  en memoria hasta encolar el correo de invitación** (ver 5.1): el correo se encola en el mismo COMMIT con el enlace,
  en estado RETENIDO hasta que la publicación se confirme.
- El trabajador del canal (mismo patrón que `MailWorker`, solo con `siger.scheduling=true`) envía
  `POST /integracion/v3/antecedentes/invitaciones` con `public_id`, `token_sha256`, `expected_run`, `request_text`,
  `expires_at`, y el folio SIGER de la gestión. Cabeceras: Bearer existente y `X-Siger-Firma` = Ed25519 con la clave
  ACK sobre `SIGER-INVITACION-v1\n` + cuerpo exacto. 201/200 idempotente → CONFIRMADA, invitación VIGENTE y el correo
  pasa de RETENIDO a PENDIENTE. 4xx definitivo → RECHAZADA, invitación FALLIDA, correo descartado, aviso al profesional.
  5xx o red → REINTENTABLE con espera creciente.
- Revocar: `INVITE_REVOKE` o `CASE_CLOSE` fijan REVOCADA y encolan REVOCAR (`POST .../invitaciones/{public_id}/revocacion`,
  misma firma). Un correo RETENIDO o PENDIENTE de esa invitación se descarta.
- El texto requerido (D60) viaja al Formulario porque allí se muestra tras ClaveÚnica; nunca va en el correo.
- Sin red real en pruebas: Formulario falso en proceso con la clave pública de prueba.

## 4. Recepción y admisión automática

4.1 Vía de la variante (A00 decide con evidencia): preferida, la misma cola `formulario-completo` con un
`document_kind` nuevo (`ANTECEDENTE_COMPLEMENTARIO`, versión 1.0), para no tocar `FormularioClient` ni el ciclo de
`FormularioConsumer`; alternativa, una cola propia con su ruta.

4.2 Documento (contrato A00): identificador de la operación, `invitation_public_id`, RUN autenticado (fuente
CLAVEUNICA), texto aportado, descriptores de adjuntos, sellos y firma Ed25519 del Formulario como en 1.1.

4.3 Proceso (A02), en un COMMIT, con el lock global:
1. Validar firma y contrato (como hoy). Buscar la invitación por `public_id`.
2. Crear la COM por el camino existente con sus originales (custodia y D17 sin cambios), canal de Formulario y
   referencia a la invitación.
3. Si la invitación está VIGENTE, el RUN coincide con `expected_run` y el Caso sigue abierto: `communication_link`
   ADMITIDA al `requirement_id` con `target_case_id`, `proposed_by` y `decided_by` = identidad técnica, `reason` y
   `decision_reason` fijos ("Antecedentes solicitados por la gestión"), COM en estado VINCULADA, invitación ENTREGADA con
   `delivered_communication_id`, aviso al profesional responsable del Caso y auditoría `ANTECEDENTES_RECIBIDOS`.
4. Si no (vencida, revocada, Caso cerrado): COM NUEVA a Distribución como cualquier ingreso, con la referencia visible
   "Llegó por una solicitud de antecedentes del Caso <folio> que ya no estaba vigente" (D57).
5. RUN distinto del esperado: no se crea la COM; la entrega se informa como fallo contractual (`CONTRATO_INVALIDO`) y se
   registra en el estado de integración para TI (no debería ocurrir: el Formulario ya lo valida).
6. Estadística: la COM vinculada sigue las reglas vigentes de VINCULADA (no es un requerimiento nuevo, D25).

## 5. Correos al ciudadano

5.1 Outbox: se extiende `MailOutbox` con un tipo de destinatario EXTERNO (dirección en instantánea) y dos plantillas
cerradas, INVITACION_ANTECEDENTES y AVISO_CIERRE. Estado adicional RETENIDO (solo invitación, hasta confirmar la
publicación). El cuerpo con el enlace se reemplaza por "[enlace retirado]" al llegar a ACEPTADO, RECHAZADO o
descartado, y al vencer la invitación. Mismo trabajador, mismo `GraphMail`, mismos estados y resolución de AMBIGUO por TI.

5.2 Dominios: `siger.mail.allowed-domains` sigue rigiendo para todo aviso interno. Las dos plantillas ciudadanas son la
única excepción, controlada por `siger.mail.citizen.enabled` (apagada por defecto). Una prueba comprueba que un aviso
interno a un dominio no permitido sigue rechazado.

5.3 Textos propuestos (DI-19, se aprueban en el GO de A03):
- Invitación. Asunto: "Defensoría de la Niñez: solicitud de antecedentes". Cuerpo: "Para continuar con la gestión de su
  presentación SIGER-XXXXXX, la Defensoría de la Niñez le solicita aportar antecedentes. Ingrese con su ClaveÚnica en el
  siguiente enlace, disponible hasta el DD/MM/AAAA: <enlace>. Si no realizó una presentación ante la Defensoría de la
  Niñez, ignore este mensaje."
- Aviso de cierre. Asunto: "Defensoría de la Niñez: cierre de gestión". Cuerpo: "Le informamos que la gestión de su
  presentación SIGER-XXXXXX ha finalizado. Ante cualquier consulta, puede comunicarse con la Defensoría de la Niñez
  indicando este número."
- Ambos: remitente `siger@defensorianinez.cl`, texto plano, sin nombres, RUN, relato ni datos del expediente.

5.4 Aviso de cierre (D62-D65): el check viaja en el comando de cierre según DI-16; con el cierre concretado se encola
AVISO_CIERRE a `contact_email` de la COM de origen; auditoría `AVISO_CIERRE_SOLICITADO` (actor, sin dirección).

## 6. Interfaz del Caso (A03)

- Panel "Antecedentes solicitados" en la ficha del Caso: lista de invitaciones (estado en palabras, fecha de envío,
  vigencia, entrega con enlace a la COM vinculada); botón "Solicitar antecedentes" (solo si `invite_eligible`; si no,
  texto breve con la razón, p. ej. "El requirente no ingresó con ClaveÚnica"); diálogo con el texto de lo que debe
  aportar (obligatorio, hasta 1.000 caracteres) y aviso "Se enviará un correo a la dirección declarada"; "Revocar" con
  confirmación.
- Cierre del Caso: check "Dar aviso de cierre de gestión" (desmarcado; solo con correo declarado; DI-20 si hay
  restricciones).
- Los documentos entregados aparecen solos en "Documentos del requiriente" (S5, S6); no se toca ese panel.

## 7. Formulario (A04)

7.1 V14 (H2 y MySQL): `invitacion_antecedentes` (`public_id` UUID PK, `token_sha256` UNIQUE, `run_esperado`,
`texto_solicitado`, `folio_siger`, `vence_en`, `estado` VIGENTE/USADA/REVOCADA, `creada_en`, `revocada_en`,
`entrega_id`).

7.2 Integración: `POST /integracion/v3/antecedentes/invitaciones` y `POST .../{public_id}/revocacion`; exigen el
Bearer de integración **y** la firma `X-Siger-Firma` verificada con las claves `institution` ya configuradas (F2) sobre
`SIGER-INVITACION-v1\n` + cuerpo; idempotentes por `public_id` (mismo cuerpo = mismo resultado; distinto = 409).

7.3 Página: `/antecedentes` es estática; su script lee el token de `location.hash`, lo envía por POST a
`/api/antecedentes/canje` y limpia el fragmento con `history.replaceState`. El canje valida la huella y guarda en la
sesión solo el `public_id`. Respuesta genérica ante cualquier falla.

7.4 Identidad: exige sesión ClaveÚnica (sin identificación declarada); compara el RUN de `IdentidadVerificada` con
`run_esperado`; mensaje genérico si no coincide.

7.5 Aporte: muestra `texto_solicitado` y el folio; texto (opcional) y adjuntos (opcionales, al menos uno de los dos)
con los componentes, límites, inspector y ClamAV actuales; al enviar, en una transacción: invitación USADA y entrega
creada en la cola elegida (4.1), firmada como 1.1; comprobante al ciudadano.

7.6 Configuración y registros: `formulario.antecedentes.habilitado=false` por defecto; arranque rechazado si es `true`
con ClaveÚnica sin `callback-approved`. Ni la aplicación ni Caddy registran el token (va en el fragmento y en el cuerpo
del POST, que no se registran); una prueba inspecciona los registros.

## 8. Ensayo de punta a punta (A05)

SIGER local (H2 y luego MySQL aislado) y Formulario local con el perfil de pruebas de identidad del Formulario,
Graph falso. Escenarios: E1 invitar y publicar; E2 correo retenido hasta confirmar; E3 aportar con texto y dos adjuntos;
E4 PULL/ACK y admisión automática; E5 documentos en "Documentos del requiriente"; E6 aviso al profesional; E7 segunda
entrega rechazada; E8 cierre con invitación vigente (revocación) y entrega cruzada (COM NUEVA); E9 aviso de cierre;
E10 apagado por defecto en ambos sistemas; E11 regresión de los recorridos 1.0, 1.1 y NNA.

## 9. Despliegue y habilitación

1. U-0.9.1: paso U de SIGER (respaldo y `Restore-Check.ps1` en la misma ventana, `Update.ps1`); todo apagado.
2. F-0.9.1: actualización en sitio del Formulario con el paquete y el simulacro de A07; todo apagado.
3. C-0.9.1: encender el aviso de cierre en SIGER.
4. H-0.9.1: con ClaveÚnica certificada, encender en el Formulario y en SIGER y acreditar un ingreso real con datos
   propios del responsable (invitación a sí mismo en un Caso de prueba autorizado), luego revocar y documentar.

## 10. Riesgos

- R1 ClaveÚnica sin certificar: la función queda lista y apagada (D59).
- R2 Primera actualización en sitio del Formulario: simulacro aislado obligatorio en A07 y ventana coordinada.
- R3 Correo a dirección errada: el enlace sin la ClaveÚnica del requirente no sirve (D54); el aviso de cierre no lleva
  datos del expediente.
- R4 Contacto no seguro (NNA o restricciones declaradas): DI-20.
- R5 Token filtrado: huella en ambos lados, fragmento de URL, minimización del correo, una entrega, vigencia acotada.
- R6 Carrera entre la entrega y el cierre del Caso: lock global y regla 4.3.
- R7 Regresión de la recepción: cambios aditivos y vectores 1.0, 1.1 y NNA en A02.

## Diferencias aprobadas

(ninguna)
