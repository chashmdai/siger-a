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

- Ejecucion en la VM de la instalacion de 0.5.0, de la actualizacion de la validacion (`docs/ACTUALIZACION_0_5_0.md`),
  de `Recover-Ti.ps1` y de un PULL real, un envio real por Graph y el analisis con el motor antivirus institucional.
- Build de entrega en Windows (gate G15 de `docs/BITACORA_0_5_0.md`): `Build.ps1 -IncludeJava` con Node 24,
  `Check-Scripts.ps1` en Windows PowerShell 5.1 y `SOURCE_SHA256.txt`.
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
