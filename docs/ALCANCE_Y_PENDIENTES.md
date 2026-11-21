# SIGER 0.5.0 - alcance y pendientes

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

## Verificado en la VM

- **PULL/ACK real con el Formulario Publico** (verificado por el responsable el 2026-11-21 en "Auditoria y operacion" de
  la instancia de validacion 0.4.0): consumidor en modo automatico, estado "Esperando entregas", ultimo exito
  21-11-26 16:22, sin incidentes, 9 recepciones confirmadas (PULL y ACK completos), credenciales instaladas con DPAPI.
  Las 9 entregas son pruebas del responsable, hechas exclusivamente con sus propios datos y con autorizacion; no hay
  datos de terceros. La validacion recibe porque su `instance.json` tiene `"validacionPullAutorizada": true`.
- Correo por Microsoft Graph: **desactivado** en esa instancia (sigue pendiente).

## Fecha critica: 15/12/2026 00:00 (America/Santiago)

Hasta esa fecha el Formulario Publico esta protegido por un acceso temporal con contrasena; despues queda abierto al
publico. **Antes del 15/12/2026, produccion debe estar consumiendo, o el consumidor de la validacion debe estar
pausado** (pausa de TI en Auditoria y operacion y `Set-Integration.ps1 -Formulario Manual`), para que ninguna entrega
ciudadana real llegue a la validacion. Solo un consumidor por cola: al activar produccion, el de la validacion ya debe
estar detenido (`docs/DESPLIEGUE_VM.md`).

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
  G15 de 0.5.0; `SOURCE_SHA256.txt` en H06b): `Build.ps1 -IncludeJava` con Node 24 portable, `Check-Scripts.ps1` en
  Windows PowerShell 5.1 y el paquete 0.6.0 `release\SIGER-<fecha>.zip`. La VM se instala con ese paquete:
  `Init-Server.ps1` y `Update.ps1` exigen su `manifest.json` (y `Update.ps1`, que se haya compilado con pruebas). El
  orden completo de lo pendiente esta en las secciones 3 y 6 de `docs/PLAN_0_6_0.md`.

- Ejecucion en la VM de la instalacion de 0.6.0, de la actualizacion de la validacion 0.4.0 -> 0.6.0 en
  `docs/ACTUALIZACION_0_6_0.md` (se crea en H05; `docs/ACTUALIZACION_0_5_0.md` no se ejecuta), de `Recover-Ti.ps1`, de un envio real por Graph y del analisis con el motor antivirus institucional.
- **Produccion (decision para 0.6.0, fecha objetivo antes del 15/12/2026)**: instancia nueva inicializada en modo
  institucional, no una migracion que borre datos. La validacion se cierra con `Close-Validation.ps1` y luego se elimina
  por completo (base, documentos y respaldos), dejando constancia documentada de la eliminacion. Para hacerlo hay que
  resolver en 0.6.0 que `Close-Validation.ps1` hoy rechaza cerrar una validacion con recepciones del Formulario (la
  de la VM tiene 9) y que no existe un procedimiento de eliminacion (los scripts no incluyen comandos destructivos).
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
