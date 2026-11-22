# SIGER 0.6.0 - alcance y pendientes

## Incluido en el fuente

Monolito de COM/SOL/REQ/Caso con roles por ambito y organigrama; derivaciones, colaboraciones y transferencias;
subrogancias; actuaciones, tareas y recordatorios; cierres e historial; documentos privados en cuarentena con analisis
antivirus para liberarlos; primera atencion progresiva (general y NNA, con versiones y entregas); dashboard por
funciones; consumidor PULL/ACK del Formulario Publico, correo por Microsoft Graph y servicio Windows (inactivos hasta
configurarlos).

Nuevo en 0.5.0 (decisiones D1-D9 de `docs/DECISIONES_FUNCIONALES.md`, "Precision 0.5.0"):

- **Administracion en TI** (D1-D3): catalogo unico de roles; TI concede y revoca funciones con el perfil de la cuenta
  (`PROFILE_SET`, un acto con fundamento) y administra subrogancias, sedes y calendario. Se suprime AUTORIDAD. TI no
  combina su funcion con otras ni lee expedientes; la instancia conserva siempre una TI permanente y, si la pierde, se
  recupera con `Recover-Ti.ps1`. Varios distribuidores.
- **Consulta institucional** (D4): lectura de todo, salvo constancias y documentos reservados, sin gestion.
- **Sedes regionales** (D7-D9): un ambito por sede con region, estado PROYECTADA/ACTIVA/CERRADA y apertura; la
  Coordinacion regional es `DIRECCION`; panel Estructura; selector de destino por nivel, region y sede; materia
  sugerida (UPR, UPD, UEP) y lectura nacional por materia de las Direcciones.
- **Buscador de personas y folios** (D5): indice de coincidencias (`person_reference`) por RUN, documento, nombre o
  folio, filtrado por lo que la cuenta lee, con auditoria sin el termino en claro. El termino nunca va en una URL.
- **Calendario habil** (D6): feriados nacionales y regionales que carga TI.
- **Hitos y reportes** (D6): hitos de cada transicion (con carga historica), reportes de volumen, antiguedad y 12
  tiempos en horas corridas y dias habiles (mediana, p90, maximo), por unidad, ambito, region, materia u origen y, con
  `REPORTES_PERSONAS`, por funcionario; exportacion CSV de REQ y Casos sin datos ciudadanos (`EXPORTACION`).
- Ingreso sin distinguir mayusculas (el usuario se guarda y se compara en minusculas). Descargas con nombre neutro.

Nuevo en 0.6.0 (decisiones D10-D17, "Precision 0.6.0"; salida a produccion, sin funcionalidad nueva de expedientes ni
migraciones):

- **Cierre de la validacion con recepciones de prueba autorizadas** (D11): `Close-Validation.ps1 -RecepcionesDeclaradas`
  exige la pausa vigente, la cantidad declarada igual a las confirmadas, ningun ACK pendiente y nada posterior a la pausa;
  conserva los datos y deja la constancia en `D:\SIGER-CONSTANCIAS`.
- **Eliminacion de la validacion cerrada, con constancia** (D12): `Remove-Validation.ps1`, el unico script que borra
  datos de una instancia; la eliminacion es logica, no forense.
- **Graph**: credencial instalada como la cuenta de servicio, sin SMR (`Import-GraphCredential.ps1`), y prueba
  controlada de token o de un envio de texto fijo (`Test-GraphMail.ps1`); el correo puede partir desactivado (D16).
- **Scripts invocables con `-File`** (listas por texto, cuentas literales), proceso propio acreditado por el servicio
  cuando WMI oculta la ruta, y guia de la salida a produccion (`docs/DESPLIEGUE_VM.md`, V1-V6 y plan B).
- **Liberacion por analisis en origen** (D17, modo de emergencia): mientras no haya un motor antivirus admitido, los
  originales del Formulario se liberan al registrarse, despues de verificar la firma de la entrega y el SHA-256 de cada
  original, con auditoria propia (nunca como un analisis local); los adjuntos subidos en SIGER siguen en cuarentena. Se
  activa de forma expresa (`Set-OriginRelease.ps1`) y no tiene fecha de termino: rige hasta que se configure un motor
  admitido o se desactive de forma expresa; la revision periodica queda a cargo del responsable, segun el acta
  institucional. Mientras rige, TI ve un aviso permanente. Al regir de nuevo libera los originales del Formulario que
  quedaron en cuarentena.

## Verificado en la VM

- **PULL/ACK real con el Formulario Publico** (verificado por el responsable el 2026-11-21 en "Auditoria y operacion" de
  la instancia de validacion 0.4.0): consumidor en modo automatico, estado "Esperando entregas", ultimo exito
  21-11-26 16:22, sin incidentes, 9 recepciones confirmadas (PULL y ACK completos), credenciales instaladas con DPAPI.
  Las 9 entregas son pruebas del responsable, hechas exclusivamente con sus propios datos y con autorizacion; no hay
  datos de terceros. La validacion recibe porque su `instance.json` tiene `"validacionPullAutorizada": true`.
- Correo por Microsoft Graph: **desactivado** en esa instancia (sigue pendiente).

## Fecha critica: 15/12/2026 00:00 (America/Santiago)

Hasta esa fecha el Formulario Publico esta protegido por un acceso temporal con contrasena; despues queda abierto al
publico. **Antes del 15/12/2026, produccion debe estar consumiendo (a mas tardar el 10/12, D13), o la validacion debe
estar pausada y cerrada** (V1-V2) **o en Manual por el plan B** (12/12), para que ninguna entrega ciudadana real llegue a
la validacion. Solo un consumidor por cola: al activar produccion, el de la validacion ya debe estar detenido
(`docs/DESPLIEGUE_VM.md`).

## Decisiones de implementacion acotadas

- Origen conserva el encargo hasta recibir la entrega. El destino habilitado puede leer la ficha para decidir, sin
  convertirse en jefe principal del REQ. Si cambia el principal se usa la transferencia formal.
- Una sola entrega pendiente; mientras lo este, la ficha no se complementa ni se cierra y no se inicia un traslado.
- Ficha editable mientras la primera atencion este abierta y sin Caso; al abrir Caso queda como antecedente.
- Lo atendido directamente no usa SOL. Una COM externa por clasificar si usa SOL. Las filas antiguas no se convierten
  retrospectivamente por inferencia.
- El buscador no fusiona identidades ni autentica personas: muestra coincidencias, y una coincidencia solo por nombre
  puede ser otra persona. No indexa a la persona senalada ni a "otra persona".
- Los reportes son de gestion del sistema: su caracter oficial lo define la institucion. Se calculan con el calendario
  habil vigente (agregar o retirar un feriado cambia tambien periodos pasados) e informan cuantos tramos se
  reconstruyeron desde la historia anterior a 0.5.0. En un REQ resuelto en el acto (`COM_IMMEDIATE`) la fecha de cierre
  es la declarada y el tiempo usa el instante del registro.
- Los hitos que la carga historica no reconoce se omiten y se cuentan (aviso permanente en Administracion).

## Pendientes que NO se presentan como terminados

- **Requisito previo a instalar en la VM: build de entrega en Windows** (H06a de `docs/PLAN_0_6_0.md`, que absorbe el
  G15 de 0.5.0) y ensayo en Windows con ese paquete (H06b, con `SOURCE_SHA256.txt` y las etiquetas): `Build.ps1
  -IncludeJava` con Node 24 portable, `Check-Scripts.ps1` en Windows PowerShell 5.1 y el paquete 0.6.0
  `release\SIGER-<fecha>.zip`. La VM se instala con ese paquete: `Init-Server.ps1` exige su `manifest.json`. Los scripts
  de 0.6.0 (cierre, eliminacion, Graph, D17, `Estado.ps1` como la cuenta de servicio) solo tienen comprobacion de
  sintaxis y de enlace con `pwsh` en Linux hasta H06b.
- Ejecucion en la VM de la salida a produccion (pasos V1-V6 de `docs/PLAN_0_6_0.md` y `docs/DESPLIEGUE_VM.md`): cierre
  de la validacion 0.4.0 con sus 9 recepciones de prueba, produccion nueva con 0.6.0 en automatico, acreditacion (D13),
  eliminacion de la validacion con constancia y, sin entregas de prueba en produccion, la primera entrega ciudadana
  despues del 15/12 como unica prueba de punta a punta (`docs/PRUEBAS_MANUALES_0_6_0.md`). Tambien `Recover-Ti.ps1` y
  un envio real por Graph.

Pendientes posteriores a 0.6.0 (cada uno toma un gate propio cuando se planifique; detalle en `docs/BITACORA_0_6_0.md`):

- **Aviso a distribucion cuando llega una COM del Formulario**: hoy no genera aviso ni correo; solo aparece en la bandeja
  ("Por distribuir").
- **Mejoras del correo de avisos**, despues de que produccion este estable: folio en el texto, asunto segun el tipo de
  aviso y enlace directo a la ficha (solo con el id; sirve si los funcionarios acceden por red). El correo sigue sin
  antecedentes ni datos ciudadanos.
- **Motor antivirus definitivo**: ClamAV en `D:\Herramientas\clamav` (sin codigo nuevo; requiere autorizacion de TI y
  salida de red para las firmas) o Kaspersky KES 14 (codigo nuevo en `DocumentScanner`, previa confirmacion con TI de
  que la cuenta de servicio puede analizar). Mientras tanto rige D17 para los originales del Formulario y los adjuntos
  subidos en SIGER siguen en cuarentena.
- **Antiguedad del respaldo en `Update.ps1`**: hoy acepta cualquier respaldo integro de la misma raiz, base y JAR; sirve
  al actualizar produccion a 0.7.x.
- **Paso de solo RDP a acceso por red**: no hay script para cambiar `bindAddress` despues de `Init-Server.ps1` sin
  `-AccesoRed`.
- **Renovacion del certificado autofirmado**: no hay script para regenerarlo; sin un PFX institucional, su renovacion
  (vence a los 365 dias de V4) no tiene procedimiento.

Otros pendientes:

- Reserva excepcional por campo de la ficha (por ejemplo, nota de seguridad o de identidad) y autorizaciones
  excepcionales completas.
- Plazos normativos (por ejemplo, 48 horas) sobre el calendario habil; informes oficiales definitivos.
- Migracion historica desde sistemas anteriores; nominas, cargos y sedes reales (se cargan en la VM).
- Copias fuera del anfitrion y recuperacion productiva calificada; SSO/MFA si se solicita.
- Primeras atenciones adicionales sobre un expediente cerrado sin reapertura autorizada; editar la ficha estructurada
  despues de abrir Caso; guardado offline entre sesiones.
- Renombrar una cuenta (no hay comando; el usuario se fija al crearla).

No usar datos personales reales antes de la calificacion institucional y de los pendientes de seguridad y documentos.
La ficha conceptual no es un formulario aprobado.
