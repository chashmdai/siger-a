# SIGER 0.6.0 - pruebas manuales

Recorridos de la salida a produccion (`docs/PLAN_0_6_0.md`, decisiones D10-D17). Las ordenes y su contexto estan en
`docs/DESPLIEGUE_VM.md` (Preparacion, V1-V6 y B); aqui se indica que comprobar y que anotar en cada paso. Sirven para el
ensayo en Windows (H06e, con una instancia propia y recepciones simuladas) y para la VM (pasos V1-V6). Cada orden se
ejecuta **tal como en la guia**, con `powershell.exe` y `-File` desde una consola de PowerShell y con los mismos
argumentos (no con `&` ni dentro de la sesion). En la VM, "como la cuenta de servicio" es una
consola abierta con `runas /user:DDN-SIGER\svc-siger powershell.exe`.

Para cada punto informar CORRECTO / FALLA / NO PROBADO. En una falla: paso, cuenta de Windows, orden, resultado esperado y
mensaje exacto. Se anotan solo cifras, fechas, estados y folios: sin datos personales, claves ni secretos (tampoco en
capturas). En la VM no hay entregas de prueba (D13 y D14).

## 0. Version y arranque

1. El pie de la barra lateral dice "Version 0.6.0". Como TI, Inicio > Estado del sistema dice lo mismo.
2. `/api/health` responde `version` 0.6.0 y `schema` 14; `Start.ps1` solo informa exito con esa version y ese esquema
   del `manifest.json` del paquete.
3. `manifest.json` del paquete: version 0.6.0, esquema 14 y `automatedTestsExecuted` verdadero (H06d).
4. `certutil -hashfile` del ZIP (Preparacion de `docs/DESPLIEGUE_VM.md`) da el SHA-256 del registro de H06d, antes de
   descomprimirlo.

## 1. Antes de V1: cifras del cierre sin detener nada

Como la cuenta de servicio, con la validacion en marcha y aun sin pausar, la orden de la Preparacion con `-WhatIf`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Close-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -RecepcionesDeclaradas 9 -Motivo 'Fin de la validacion: salida a produccion' -WhatIf
```

1. Las cifras muestran 9 recepciones confirmadas, 0 registradas y 0 ACK pendientes; anotar tambien las rechazadas y las
   COM externas.
2. El **unico** bloqueo es `SIN_PAUSA`. Cualquier otro (por ejemplo `COM_EXTERNA_SIN_RECEPCION` o
   `CANTIDAD_DISTINTA`) se informa al responsable antes de V1, sin seguir.
3. Nada cambia: SIGER sigue arriba (`Estado.ps1` de la validacion) y no aparece `config\piloto-cerrado.json` ni una
   copia en `D:\SIGER-CONSTANCIAS`.

## 2. V1-V3: pausa, cierre y retiro de servicios de la validacion 0.4.0

1. V1: Auditoria y operacion muestra el Formulario en pausa, con el motivo y la hora. Repetir la orden del punto 1: ya
   no hay bloqueos y las cifras no cambiaron.
2. V2 (desde la carpeta del paquete): la orden muestra cifras y bloqueos y pide confirmacion; al confirmar detiene
   SIGER, vuelve a contar, detiene el motor y termina con "Validacion cerrada: ...". Anotar la ruta del marcador y de la
   copia `cierre-validacion-<fecha>.json`; ambas tienen `kind` CIERRE_VALIDACION, 9 declaradas y 9 confirmadas, y no
   contienen nombres, RUN ni secretos.
3. `Start.ps1` de la validacion se niega a iniciarla (marcador de cierre).
4. V3 (administrador, desde la carpeta del paquete): `Uninstall-Service.ps1` retira `SIGER` y `SIGERMySQL`; la raiz,
   la base, `config\` y `logs\` siguen en su lugar.
5. Solo en el ensayo (H06e), un rechazo por condicion antes del caso valido: cantidad distinta
   (`-RecepcionesDeclaradas 8`), sin pausa, una recepcion `REGISTRADA` o con ACK pendiente, una recepcion posterior a
   la pausa, una instancia de proposito distinto de VALIDACION, la confirmacion negada (no detiene nada) y `-WhatIf`.
   Cada rechazo nombra su bloqueo y no deja marcador.

## 3. V4: produccion con el paquete 0.6.0

1. `Init-Server.ps1` sin `-MismoReleaseQue` y con los mismos puertos (libres desde V2): termina con el MySQL propio
   activo y SIGER sin iniciar. Anotar la fecha (vencimiento del autofirmado, 365 dias) y si se uso `-AccesoRed`.
2. Las claves de `D:\SIGER\data\bootstrap-usuarios.csv` se entregan por un canal seguro y el archivo se retira.
3. La copia de la configuracion del Formulario trae exactamente sus cinco archivos (ningun `*.bak-*`), y `Set-Integration.ps1 -Formulario Manual`
   deja `formulario-pull.json` en MANUAL antes del primer inicio (si no, produccion consumiria sin sonda).
4. `New-AuditSearchKey.ps1` crea `config\audit-search-key.clixml` y no imprime la clave; repetirla no la sobrescribe.
5. Modo D17 activado antes del primer inicio (seccion 4 de este documento).
6. `Install-Service.ps1` (administrador, consola interactiva) abre el cuadro de credenciales de Windows con
   `DDN-SIGER\svc-siger`, pide solo la contrasena y registra los dos servicios sin iniciarlos; si la cuenta ya tiene el
   derecho "Iniciar sesion como servicio", no avisa que le falta. `sc.exe sdshow SIGER` muestra el derecho CR (control
   definido por el usuario) solo en SY, BA y el SID de la cuenta (IU y SU sin CR).
7. **`Estado.ps1` como `svc-siger`** (despues de `Start.ps1`):

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Estado.ps1' -Root 'D:\SIGER'
   ```

   Anotar el resultado tal cual: `AppRunning`, `JarSha256Matches`, `Health`, `Version`, `Migration` y `Formulario`
   (DESACTIVADO: en Manual el servicio no tiene consumidor), o el mensaje exacto si informa que el PID no coincide.
   Las correcciones de H04a (proceso propio acreditado por el servicio cuando WMI oculta ruta y argumentos) y de H06c
   (cuenta local `.\svc-siger`) se acreditan en H06e; si en la VM falla igual, se informa al responsable y la sonda
   HTTPS de `Start.ps1` sigue siendo la prueba de salud.
8. `Test-Service.ps1 -VentanaAutorizada -IncluirMySql`, como `svc-siger`, termina sin error y deja
   `logs\prueba-servicio-<fecha>.json`. La caida simulada (control 200) deja en el registro de eventos Application
   (origen SIGER) "caida simulada solicitada" y "termino inesperadamente", y Windows reinicia el servicio. Con otra
   cuenta se niega antes de descifrar.
9. `Invoke-FormularioPull.ps1 -SoloComprobar` acredita la sonda sin reclamar ni confirmar nada (ninguna COM nueva).
10. `Set-Integration.ps1 -Formulario Automatico -SmrDetenidoConfirmado`, reinicio y `Estado.ps1`: Formulario en
    `ESPERANDO_ENTREGAS`.
11. Acceso por red (`-AccesoRed`, decidido el 2026-11-22): 18543 escucha en `::` o `0.0.0.0` y 13326 solo en
    `127.0.0.1`; desde un PC de 192.168.100.0/24, `https://<nombre o IP de la VM>:18543` muestra el ingreso. Si no
    responde, anotar y revisar con TI el firewall de Windows y el de Kaspersky (prueba final de V4 en la guia).

## 4. Modo de emergencia D17 (liberacion por analisis en origen, sin fecha de termino)

Activacion, como la cuenta de servicio, antes del primer inicio (orden de V4):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-OriginRelease.ps1' -Root 'D:\SIGER' -Motivo 'Decision del responsable del 2026-11-21 (D17), con correo y constancia institucional'
```

1. La orden pide confirmacion y dice "sin fecha de termino (rige hasta configurar un motor antivirus admitido o
   desactivarlo)". `config\liberacion-origen.json` tiene solo `kind`, `version` (2), `id`, `activatedUtc`, `account` y
   `reason`.
2. La orden no acepta fecha de termino: agregarle `-Hasta 2027-03-03` hace que PowerShell la rechace por parametro
   desconocido, sin escribir nada.
3. Tras el inicio, Administracion (TI) muestra el aviso permanente "Rige el modo de emergencia D17" con la fecha de
   activacion, y el panel Cuarentena documental lo repite. No hay avisos de vencimiento. Auditoria: un evento
   `LIBERACION_ORIGEN_ACTIVADA` (recurso SISTEMA 17) con la cuenta y el motivo; reiniciar no agrega otro.
4. Solo en el ensayo (H06e):
   - con otra cuenta de Windows la orden se niega; con `config\antivirus.json` presente, tambien;
   - `-WhatIf` y la confirmacion negada no escriben el archivo; repetir la orden pide confirmar el reemplazo;
   - un archivo con un campo `until` (registro de la version 1) deja el modo sin efecto: aviso "Liberacion por analisis
     en origen sin efecto" con `CAMPOS`, los originales quedan en cuarentena y la auditoria registra
     `LIBERACION_ORIGEN_INVALIDA`;
   - con `antivirus.json` y el archivo presente, el aviso es `LIBERACION_ORIGEN_CON_MOTOR` y no se libera nada;
   - una recepcion simulada con el modo vigente: el original se ve en la ficha de la COM como
     "Disponible · analisis en origen", con la auditoria `DOCUMENTO_LIBERADO_EN_ORIGEN` (motivo "analisis en origen
     del Formulario") y sin analisis en `document_scan`; un adjunto subido en SIGER sigue en cuarentena;
   - `-Desactivar` (pide confirmacion) renombra el archivo a `liberacion-origen.retirado-<UTC>.json`; tras el reinicio
     desaparece el aviso y la auditoria registra `LIBERACION_ORIGEN_DESACTIVADA`; una recepcion simulada queda en
     cuarentena;
   - reactivar y reiniciar libera ese original pendiente (liberacion retroactiva, con la nota "liberado al regir de
     nuevo el modo") y nunca uno con una deteccion previa.

## 5. Graph (opcional, D16)

1. `Import-GraphCredential.ps1` como la cuenta de servicio pide Tenant ID, Client ID y el secreto por consola, no los
   muestra y deja `correo-graph.json` en DESACTIVADO. Con otra cuenta se niega; si ya existe la credencial, no la
   sobrescribe.
2. `Test-GraphMail.ps1 -Modo Token` informa solo modo, estado, fase, HTTP y request-id, y deja `run\graph-prueba.json`.
3. `-Modo Envio` pide confirmacion antes de enviar; negada o con `-WhatIf` no envia. Aceptado, el destinatario fijo
   recibe el correo de texto fijo. Anotar estado y request-id.
4. `Set-Integration.ps1 -Correo Graph` se niega sin un Envio ACEPTADO de menos de 24 horas con el JAR activo; con el, el
   correo queda en GRAPH desde el reinicio.

## 6. V5: produccion segun D13

1. A mas tardar el 10/12, TI en Auditoria y operacion: Formulario "Esperando entregas", ciclos corriendo y ultimo exito
   reciente, sin incidentes. Anotar fecha y estado (un ciclo en pausa tambien registra exito: lo que cuenta es el
   estado).
2. Tareas de TI: clave temporal cambiada, feriados 2026 y 2027 importados, region de cada sede revisada y el aviso
   permanente de D17 visible.
3. No se hacen entregas de prueba en produccion.

## 7. V6: eliminacion de la validacion con constancia

1. La orden de V6 con `-WhatIf` muestra el inventario (raiz, respaldos declarados y el incompleto) sin bloqueos y no
   borra nada.
2. La eliminacion pide la confirmacion de PowerShell y que se escriba la ruta de la raiz; escribe
   `eliminacion-validacion-<fecha>.json` en `D:\SIGER-CONSTANCIAS` antes de borrar y la completa con ELIMINADO por
   elemento. La constancia no tiene nombres de archivos de expedientes, contenido ni secretos.
3. Despues: `D:\SIGER-VALIDACION` y los respaldos declarados ya no existen; `D:\SIGER`, `D:\Herramientas\mysql-8.4`,
   `D:\SIGER-CONSTANCIAS` y la carpeta del paquete siguen intactos y produccion sigue en `ESPERANDO_ENTREGAS`.
4. Solo en el ensayo (H06e), rechazos: sin marcador de cierre, con servicios registrados, raiz de produccion o fuera de
   `D:`, respaldo de otra instancia, ruta dentro del `mysqlHome`, otra cuenta y una ruta escrita distinta.

## 8. Plan B (solo si aplica, 12/12)

Si al 12/12 la validacion no se ha pausado ni cerrado: pausa (TI), `Set-Integration.ps1 -Formulario Manual` de la propia
instancia 0.4.0, retiro de `validacionPullAutorizada` y reinicio, en ese orden. `Start.ps1` inicia la validacion en
Manual; anotar fecha y estado. Si la validacion ya esta cerrada y produccion aun no consume, no se hace nada: las
entregas esperan en la cola del Formulario.

## 9. Primera entrega ciudadana (despues del 15/12)

Es la unica prueba de punta a punta en produccion, porque no hay entregas de prueba (D13 y D14): la primera recepcion con
el codigo de 0.5.0 y 0.6.0 ocurre aqui. Cuando llegue, TI y Distribucion comprueban, sin abrir ni copiar datos de la
persona:

1. **COM creada**: aparece en la bandeja de Comunicaciones, filtro "Por distribuir", con canal Formulario Publico (o
   Formulario Publico NNA) y estado nueva. Anotar solo el folio y la hora. (Hoy no genera aviso ni correo a
   distribucion: es un pendiente posterior a 0.6.0.)
2. **Adjunto liberado**: si la entrega trae originales, en la ficha de la COM cada uno figura como
   "Disponible · analisis en origen" (D17), con la auditoria `DOCUMENTO_LIBERADO_EN_ORIGEN`.
3. **ACK confirmado**: en Auditoria y operacion las recepciones confirmadas suben en uno y no hay ACK pendientes ni
   incidentes.
4. **Formulario en `ESPERANDO_ENTREGAS`**: el consumidor vuelve a esperar, con ultimo exito reciente.
5. **Ningun aviso nuevo en Administracion**: solo el aviso permanente de D17 que ya estaba.

Si algo falla, no se reintenta a mano ni se borra nada: si el consumidor no confirmo el ACK, la entrega sigue en la cola
del Formulario. Se informa al responsable con el paso, la hora y el mensaje exacto.
