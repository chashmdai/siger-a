# SIGER 0.9.X - bloque PDF/SMS (plan y diseño preliminar)

Resto de la ampliación contractual (cotización `NX-SIGER-AMP-2026-11`): **PDF de cierre** adjunto al aviso de cierre y
**SMS** al requirente. **BLOQUEADO** por insumos de la Defensoría: plantilla del PDF de cierre y proveedor de SMS
contratado (Convenio Marco). Toma el número de versión siguiente a la última etiquetada cuando el responsable registre
los insumos y lo marque EN CURSO en `PLAN_0_9_0.md` 1; en ese momento Claude Padre completa este plan (gates
detallados) y crea su bitácora. Prefijo de gates **P**. Fecha de corte para los insumos: 19/12/2026.

## 0. Decisiones y propuestas

Vigentes (de 0.9.1): D68-D72 (aviso de cierre por correo con check opcional; el PDF se agrega a ese mismo correo).

Propuestas pendientes de validación institucional (se convierten en decisiones, con el siguiente número libre, al validarse):
- **DS-1 Eventos SMS.** "En gestión" al abrirse el Caso; "cierre" al cerrar el Caso con el mismo check del correo
  (D68); "solicitud de antecedentes" con el mismo enlace de la invitación de 0.9.1 (un solo token para correo y SMS;
  una sola entrega).
- **DS-2 Textos.** Fijos, breves, con el folio SIGER-XXXXXX como única referencia, sin datos del expediente. Ejemplos:
  "Defensoría de la Niñez: su gestión SIGER-XXXXXX está en curso." / "Defensoría de la Niñez: su gestión
  SIGER-XXXXXX fue cerrada." / "Defensoría de la Niñez: se requieren antecedentes para su gestión SIGER-XXXXXX.
  Ingrese con ClaveÚnica en <enlace>."
- **DS-3 Destinatario.** Teléfono declarado; sin teléfono, no hay SMS (el correo sigue su propia regla). Restricciones
  de contacto según DI-20 de 0.9.1.
- **DS-4 Validación.** La Defensoría valida eventos, textos y si "en gestión" se envía siempre o con check. Si un REQ
  abre varios Casos, "en gestión" saldría una vez por Caso; si se prefiere uno solo, el evento es la apertura del
  primer Caso del REQ.
- **Reevaluación del PDF (D71).** Al conocer el contenido de la plantilla, el responsable decide si el aviso con PDF
  exige verificación adicional del destinatario (por ejemplo, solo requirentes con ClaveÚnica o confirmación del correo
  por el profesional).

## 1. Requerimiento del proveedor de SMS (texto para la Defensoría)

Se necesita un proveedor de SMS que ofrezca una API HTTPS en la que todas las comunicaciones las inicie SIGER. El
proveedor no debe necesitar llamar al sistema: SIGER no recibe conexiones entrantes, así que el estado de entrega de
cada mensaje (enviado, entregado, fallido o rechazado) debe poder consultarse por API, sin depender de webhooks ni
callbacks. La API debe autenticar con una credencial que se pueda rotar, y aceptar una referencia del cliente por
mensaje para que un reintento no genere un envío duplicado. Debe enviar a números de Chile en formato +56, con un
remitente identificable de la Defensoría, y soportar caracteres en español (tildes y ñ), informando cuántos segmentos y
cuánto costo tiene cada envío. Por la naturaleza de los datos que maneja SIGER, el proveedor debe tratar los números y
textos solo para el envío, sin usarlos para otros fines, con una retención acotada y cumpliendo la normativa chilena de
protección de datos personales. También debe ofrecer un ambiente de pruebas, o números de prueba, para validar la
integración sin enviar mensajes reales.

## 2. Diseño preliminar

PDF de cierre
- Generación en el servidor desde la plantilla institucional, con los datos que la plantilla exija (se revisan contra
  D71 y la reserva); adjunto al correo AVISO_CIERRE de 0.9.1 (Graph acepta adjuntos de hasta unos 3 MB en envío
  directo; un PDF de cierre es menor).
- Librería de PDF: requiere autorización expresa (regla de dependencias); alternativa sin librería si la plantilla lo
  permite.
- Copia del PDF enviado custodiada como documento de gestión del Caso (con huella), para acreditar qué se envió.

SMS
- Outbox `sms_outbox` con el mismo patrón que el correo: cola durable, intento registrado antes de la red, estados,
  AMBIGUO sin reenvío automático, reintentos con referencia del cliente, consulta periódica del estado de entrega.
- Credencial del proveedor en DPAPI con un script de importación como el de Graph; salida de red desde la VM hacia el
  proveedor (TI).
- Textos fijos con contador de segmentos (GSM-7 o UCS-2); propiedad `siger.sms.enabled=false` por defecto.

## 3. Gates previstos (se detallan al desbloquear)

| Gate | Título |
|---|---|
| P00 | Línea base, decisiones validadas (DS-1 a DS-4 con el siguiente número libre) y diseño con la plantilla y la API reales |
| P01 | PDF de cierre adjunto al aviso (servidor) |
| P02 | SMS: outbox, proveedor, estados y textos (servidor e interfaz) |
| P03 | Integración, aceptación y cierre de versión |
| P04 | Auditoría independiente (EXTERNO) |
| P05 | Build y etiqueta |

No toca el Formulario.
