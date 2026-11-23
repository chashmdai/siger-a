# SIGER 0.7.0 - plan de implementacion

Cobertura territorial de las sedes y region de origen de la persona, antes de la salida a produccion (decision del
responsable del 2026-11-23). Version 0.7.0, gates T00-T06b, registro en `docs/BITACORA_0_7_0.md`. No cambia la salida a
produccion de 0.6.0 (`docs/PLAN_0_6_0.md`, `docs/DESPLIEGUE_VM.md`) salvo el paquete de V4, y solo si 0.7.0 queda
acreditada antes del corte (seccion 3).

## 0. Decisiones (las copia T00 a `docs/DECISIONES_FUNCIONALES.md` como "Precision 0.7.0")

D18. **Cobertura territorial de las sedes.** Cada sede regional conserva su region fisica (`org_scope.region_code`), que
siempre cubre y que no se quita, y puede cubrir ademas otras regiones del catalogo del Formulario (cobertura adicional).
Solo las sedes regionales tienen region fisica y cobertura adicional; la cobertura adicional no repite la region fisica
ni una region dos veces. Una region puede quedar cubierta por mas de una sede (como D8 ya admite varias sedes en una
region). La cobertura de una sede PROYECTADA queda preparada y rige al activarse; una sede CERRADA no tiene cobertura
vigente.
   - **Nivel central** (precision del responsable, 2026-11-23): la Region Metropolitana (13) la atiende la Oficina
     Nacional, sin sede regional. TI declara las regiones que atiende el nivel central ("cobertura del nivel central"),
     con el mismo mecanismo de vigencia, fundamento y auditoria de la cobertura de una sede. No se siembra ninguna region
     en el codigo ni en migraciones. La cobertura del nivel central no agrega destinos al selector (D20): solo cuenta como
     region atendida en el aviso `REGION_SIN_SEDE` y en el efecto territorial (D19).

D19. **Administracion, historia y efecto territorial.**
   - TI administra desde la interfaz la cobertura adicional de cada sede y la del nivel central, con fundamento,
     concurrencia optimista y auditoria (cobertura anterior -> nueva, solo con codigos de region). Cada region cubierta es
     un periodo con inicio y termino; terminar no borra: la historia queda consultable en Estructura.
   - Archivo de estructura inicial: `"cobertura": ["03"]` opcional en cada sede regional y `"coberturaNivelCentral":
     ["13"]` opcional en la raiz, con las mismas reglas que los comandos. Un archivo sin esas claves sigue valido. 0.6.0
     ignora esas claves sin aviso: si V4 vuelve a 0.6.0, se retiran del archivo.
   - Efecto territorial: para cada region que hoy atiende la sede (primero la region fisica, luego la cobertura
     adicional) o el nivel central, el servidor informa "sigue atendida por <sedes ACTIVA y/o nivel central>" o "queda
     sin atencion". Las sedes PROYECTADA se informan solo como dato (no reciben). Se calcula en `SCOPE_CLOSE`, en el
     cambio de cobertura de una sede o del nivel central y en `SCOPE_UPDATE` cuando cambia la region fisica.
   - Si alguna region queda sin atencion, la orden exige `regiones_sin_sede` igual a la lista que calcula el servidor (si
     difiere, 409: la situacion cambio) y su fundamento registra la decision institucional. Sin regiones afectadas basta
     el fundamento de siempre. La auditoria del acto registra el efecto (solo codigos de region y de sede).
   - Al cerrar una sede, su cobertura adicional termina en el mismo acto. El efecto territorial no es un bloqueo de
     `Personnel.closeBlockers` (dejaria Cerrar siempre deshabilitado): Estructura lo muestra antes de confirmar. Los
     bloqueos de cierre de D8 no cambian.
   - Aviso permanente `REGION_SIN_SEDE` en Administracion: regiones del catalogo que hoy no atiende ninguna sede ACTIVA
     (por region fisica o cobertura adicional vigente) ni el nivel central (cobertura del nivel central vigente). Con 13
     declarada como del nivel central, 13 no genera aviso.

D20. **Selector de destino.** Una sede aparece bajo su region fisica y bajo cada region de su cobertura adicional
vigente. La propuesta no cambia de regla: una sola sede que atiende la region queda preseleccionada; con varias, solo la
region; sin sede, el nivel central, como hoy (si el nivel central atiende esa region, el selector lo indica con un aviso
y no propone unidad). Si una sede y el nivel central atienden la misma region, rige la propuesta de sede. El servidor no
valida la region del destino: el distribuidor conserva la decision. No hay distribucion automatica. La propuesta usa la
region vigente (D21).

D21. **Region de origen de la persona, con trazabilidad.**
   - Fuentes existentes e inmutables: en una COM del Formulario, la proyeccion (`external_field` `nna-region` y, si falta
     o no es valida, `adult-region`), que no cambia despues de crear la COM; en una atencion directa, las versiones de la
     ficha (`attention_revision`, con autor, fecha y motivo). Una COM del Formulario no tiene ficha. Una COM manual no
     tiene region.
   - Region registrada: la del Formulario o, en una atencion directa, la primera region valida que registro la ficha
     (completar una ficha que no tenia region no es una correccion). Region vigente: la de la version actual de la ficha
     (en el Formulario, igual a la registrada). Correccion: region vigente distinta de una region ya registrada. Sin
     region: "sin region declarada".
   - Mecanismo: V16 agrega una columna derivada con la region de cada version de la ficha (`attention_revision`),
     escrita al crear la version desde sus propios datos y reconstruible con un marcador (patron de `PersonIndex` y
     `Milestones`). No cambia la recepcion del Formulario (seccion 2).
   - Reportes: filtran y agrupan por la region registrada (estable: una correccion posterior no cambia las cifras
     historicas) e informan cuantos registros tienen la region corregida. La region que hoy informan pasa a rotularse
     "region de la sede". Exportacion: region registrada, region vigente y region de la sede. La ficha muestra la region
     registrada cuando difiere de la vigente. `FICHA_COMPLEMENTADA` agrega "Region de origen modificada", sin codigos:
     los valores quedan en las versiones de la ficha, con su lectura protegida.

D22. **Feriados regionales (sin cambio de regla).** Los dias habiles descuentan los feriados nacionales y los de la region
de la sede responsable; en el nivel central, solo los nacionales (regla vigente: `Reports.java:206` y `:296-297`). Se
escribe asi en D6 y se fija con pruebas. Alcance: los dias habiles solo se usan en reportes y antiguedad
(`BusinessCalendar` solo lo usa `Reports`), no en plazos, alertas ni cierres. Usar la region de origen cambiaria tambien
el nivel central y dependeria de datos declarados que pueden faltar o corregirse. Cualquier cambio de esta regla requiere
aprobacion expresa del responsable.

Escenarios de aceptacion (pruebas automatizadas, humo de T04 y, los de interfaz, ensayo de T06b):
1. Atacama (03) atendida por Coquimbo (04).
2. Maule (07) atendida por O'Higgins (06).
3. Nuble (16) atendida por Biobio (08).
4. Los Rios (14) atendida por La Araucania (09).
5. Apertura de una sede propia que asume una region cubierta por otra: sede PROYECTADA con dotacion; al activarse, la
   region tiene dos sedes y no hay preseleccion; TI retira la region de la cobertura de la otra sede con fundamento; desde
   ahi se propone la sede propia; la historia muestra el periodo anterior y los REQ anteriores conservan su sede y su
   region registrada.
6. Region Metropolitana (13) atendida por el nivel central: sin aviso `REGION_SIN_SEDE`; el selector parte en Nivel
   central con su aviso.
7. Cierre de una sede cuya region fisica queda sin atencion: sin `regiones_sin_sede` se rechaza; con una lista distinta,
   409; con la lista exacta y fundamento, se cierra y la auditoria registra el efecto.

## 1. Punto de partida (verificado al planificar)

- Repositorio: `main` = `v0.6.0` = `7c9b89b` (`gate H06e: ensayo y etiquetas`). Version 0.6.0 (`backend/pom.xml`,
  `frontend/package.json`), esquema 14. Cifras de referencia: JUnit en Windows 170 ok, 0 omitidas (H06d-H06e); JUnit H2
  en Linux 169 ok + 1 omitida y suite sobre MySQL 8.4.13 172 ok + 1 omitida (H05); frontend 76 ok en Windows (H06a) y 75
  en Linux (H05); `Check-Scripts.ps1` con Windows PowerShell 5.1: 40 scripts, 73 ordenes documentadas en 60 procesos.
- Sedes: `org_scope.region_code`, una region por sede (`V11__sedes_regionales.sql:5`). `SCOPE_CREATE`, `SCOPE_UPDATE`
  (permite cambiar la region), `SCOPE_ACTIVATE` y `SCOPE_CLOSE` en `Personnel.java:67-105`; `region()` en `:167-172`;
  `closeBlockers` en `:188-202` (sin territorio); `warnings` en `:283`. Catalogo de ambitos en `Queries.java:257`.
  Archivo inicial: `Bootstrap.site` (`Bootstrap.java:277`); `Bootstrap` lee solo sus claves conocidas.
- Formulario: toda COM entra al ambito receptor fijo (`FormularioPull.java:42-43`, `IntakeCommands.java:100-113`).
  `routeCom` (`IntakeCommands.java:169`) y `Access.solDestination` (`Access.java:153`) no validan la region.
- Propuesta de destino: `Access.suggestedRegion` (`Access.java:306`); `destinations` y `propose`
  (`frontend/src/lib/destination.ts:35`, `:57` y `:76`) solo listan regiones con una sede que recibe.
- Region de la persona: ningun codigo actualiza ni borra `external_field`; `attention_revision` (V5) guarda `data_json`
  por version; la ficha existe solo en la atencion directa (`Attention.java:145` y `:155-189`).
- Reportes: el filtro de region es la region de la sede (`Reports.java:164`), igual que la agrupacion (`:206`) y la
  exportacion (`:458`).
- Scripts: `scripts\` tiene 44 archivos: 39 `.ps1` y 5 fuentes auxiliares (`AckKeyCheck.java`, `FormularioAckKey.java`,
  `PolicySmoke.java`, `HealthProbe.cs` y `SigerService.cs`). `Check-Scripts.ps1` informa 40 scripts: los 39 `.ps1` de
  `scripts\` mas `.mvn\wrapper\maven-wrapper.ps1` (`Check-Scripts.ps1:6-7`); las 5 fuentes auxiliares solo se revisan
  por caracteres de control. El paquete de H06d trae los 44 archivos de `scripts\`.

## 2. Reglas para todos los gates (condiciones del responsable)

- `v0.6.0`, `v0.5.0`, el ZIP `SIGER-20261122-201515.zip` y las evidencias de H06e (incluidos `D:\SIGER`,
  `D:\SIGER-CONSTANCIAS` y `D:\Ensayo-H06*` de TEST-SIGER) no se tocan. No se modifica la VM institucional durante el
  desarrollo.
- Sin cambios en scripts: `git diff v0.6.0 -- scripts/ .mvn/` vacio. Si un gate necesita cambiar un script, se detiene
  (BLOQUEADO) y el responsable reevalua.
- Sin cambios en la recepcion del Formulario: `git diff v0.6.0` vacio para `FormularioReception.java`,
  `FormularioConsumer.java`, `FormularioAutomatic.java`, `FormularioClient.java`, `FormularioPull.java`,
  `ExternalOriginal.java` y `ExternalProjection.java`, e `IntakeCommands.conserveExternal` sin cambios.
- V1-V14 no se modifican. V15 (cobertura) y V16 (region por version de la ficha) son aditivas, compatibles con H2
  `MODE=MySQL` y MySQL 8.4; `MySql84UpgradeTest` (0.4.0 -> V16) sigue pasando.
- Resto igual que la seccion 2 de `docs/PLAN_0_6_0.md`: toda escritura por `Workflow.run`; visibilidad doble con su
  equivalencia; sin datos ciudadanos ni secretos en logs, URLs, salidas, constancias ni bitacora; cada regla con pruebas
  que fallan si se rompe (mutacion), con la evidencia (regla -> prueba que fallo) en el registro del gate.
- Comandos: los de `CLAUDE.md` del equipo donde se trabaje (Windows en TEST-SIGER o Linux); MySQL 8.4 solo aislado y
  desechable, como indica `CLAUDE.md`.
- Documentos: `docs/PRUEBAS_MANUALES_0_7_0.md` no lleva ordenes `powershell.exe ... -File` (`Check-Scripts.ps1` solo
  enlaza las guias que ya conoce y no se modifica). En `docs/DESPLIEGUE_VM.md` el texto de P0, Preparacion y V0-V3 no
  cambia; solo V4 (paquete y estructura).
- Si V0-V3 encuentra un defecto bloqueante de 0.6.0, la 0.6.1 sale de una rama desde `v0.6.0`, no de `main`.

## 3. Relacion con la salida a produccion y corte

- V0-V3 se ejecutan desde el 04/12 con el paquete 0.6.0, sin cambios. Sus registros siguen en `docs/BITACORA_0_6_0.md`
  (seccion "Salida a produccion en la VM").
- Corte 07/12: V4 usa 0.7.0 solo con T06b CERRADO, la etiqueta `v0.7.0` y la aprobacion expresa del responsable; en ese
  caso V4 se hace entre el 08 y el 10/12. Si no, V4 con 0.6.0 a mas tardar el 10/12, se retiran `cobertura` y
  `coberturaNivelCentral` del archivo de estructura, y 0.7.0 queda para despues del 15/12 con su propio ensayo de
  `Update.ps1`.
- Calendario objetivo: este plan, T00 y T01 hasta el 25/11; T02 el 26-27/11; T03 el 28-29/11; T04 y T05 el 30/11; T06a y
  T06b del 01 al 03/12; margen hasta el 07/12.

## 4. Documentacion que se actualiza

`DECISIONES_FUNCIONALES.md` (D18-D22 y D6 precisada, T00), `CLAUDE.md` (estado en T00; arquitectura en T04),
`ALCANCE_Y_PENDIENTES.md`, `INSTALACION_VM.md`, `DESPLIEGUE_VM.md` (solo V4), `deploy/config-ejemplo/LEEME.txt` y
`estructura-inicial.json` (claves de cobertura), `README.md` si corresponde y el nuevo `PRUEBAS_MANUALES_0_7_0.md` (T04).
Los pendientes de 0.7.x registrados en `BITACORA_0_6_0.md` (observaciones de funcionarios y aviso a distribucion de las
COM del Formulario con `notifyDistributors`) pasan a 0.8.0.

## 5. Protocolo de cada sesion (gate)

Igual que la seccion 5 de `docs/PLAN_0_6_0.md`, con este plan y `docs/BITACORA_0_7_0.md`: un gate por sesion (el primero
ABIERTO; si el prompt nombra otro, detenerse y preguntar); punto de partida (`git status` limpio y el commit del ultimo
gate cerrado); inventario y plan antes de editar, y confirmacion explicita del responsable; suite completa; registro con
el formato de la seccion 7; un solo commit `gate TNN: <titulo>`; revision de archivos sensibles con
`git show --stat HEAD`; push a `main` en `origin`; detencion. Ademas:
- T05 tiene estado EXTERNO: no lo ejecuta Claude Code y la sesion lo salta (seccion 6). Claude Code nunca edita el
  registro ni el estado de una auditoria.
- T06a exige que la ultima auditoria (T05 o la que la repita) este CERRADO y que no quede ningun gate de correccion
  ABIERTO; si no, la sesion se detiene e informa sin editar nada.
- Si un gate resulta mas grande de lo previsto, se divide (`TNNa` cerrado con lo verificado y `TNNb` ABIERTO).

## 6. Gates

| Gate | Titulo | Alcance | Verificacion especifica |
|---|---|---|---|
| T00 | Linea base y decisiones | Sin codigo. Verificacion de cada referencia de la seccion 1 contra el codigo (ver "Antecedentes para T00" en la bitacora). Suite completa. D18-D22 en `DECISIONES_FUNCIONALES.md` ("Precision 0.7.0") y D6 precisada con D22. `CLAUDE.md`: estado "plan 0.7.0 en curso" (la regla de no trabajar sin instruccion se mantiene). | Conteos de la seccion 1; una diferencia se registra antes de seguir. |
| T01 | Cobertura y efecto territorial (servidor) | V15: periodos de cobertura adicional de cada sede y del nivel central (inicio, termino, actor y fundamento). Comandos de TI para la cobertura de una sede y la del nivel central. Efecto territorial en `SCOPE_CLOSE`, en los cambios de cobertura y en `SCOPE_UPDATE` con cambio de region, con `regiones_sin_sede`. Termino de la cobertura adicional al cerrar. `REGION_SIN_SEDE`. Catalogo con la cobertura vigente; `administration.structure` con cobertura vigente, historia y efecto territorial. Claves `cobertura` y `coberturaNivelCentral` del archivo inicial. Si el inventario lo justifica: T01a (cobertura) y T01b (efecto territorial). | Mutacion por regla. Escenarios 5, 6 y 7. Rechazos: region fisica en la cobertura adicional, region repetida, codigo invalido, sede central, sede CERRADA. MySQL 8.4 si hay SQL nuevo. |
| T02 | Region de origen y reportes | V16 y su reconstruccion con marcador. Region registrada y vigente (D21). Reportes: filtro y agrupacion por region registrada, conteo de corregidas, rotulo "region de la sede". Exportacion con las tres regiones. Auditoria de `FICHA_COMPLEMENTADA`. Feriados sin cambio (D22). | Escenarios 1-4 (region registrada, sede responsable y region de la sede distintas en reportes y exportacion). Una correccion no cambia el reporte; completar no es correccion. Feriado de la region cubierta no se descuenta y el de la region de la sede si. `MySql84UpgradeTest` con la reconstruccion. |
| T03 | Interfaz | Estructura: cobertura vigente e historia; editar la cobertura de una sede y la del nivel central con fundamento; efecto territorial antes de confirmar (cerrar, cambiar cobertura o region) con la confirmacion de las regiones sin atencion; aviso `REGION_SIN_SEDE`. Selector con cobertura y aviso del nivel central. Reportes y busqueda con los rotulos "region de la sede" y "region de origen". Ficha con la region registrada cuando difiere de la vigente. | Pruebas del frontend: `destination.test.ts` sin regresion de los casos actuales y casos nuevos por escenario; typecheck y build. |
| T04 | Cierre de version | Version 0.7.0. Documentacion de la seccion 4. `PRUEBAS_MANUALES_0_7_0.md` con los escenarios. | Suite en H2 y sobre MySQL 8.4 (con `MySql84UpgradeTest`); frontend; humo con los escenarios 1-7; condiciones de la seccion 2 comprobadas con `git diff`. |
| T05 | Auditoria independiente (EXTERNO) | La realiza el asistente del responsable en claude.ai ("Claude Padre"), separado de la implementacion y sin acceso a las sesiones de Claude Code: clon limpio del commit de cierre de T04; alcance del diff contra `v0.6.0`; condiciones de la seccion 2; trazabilidad de D18-D22 y de los escenarios a codigo y pruebas; revision de la evidencia de mutacion; re-ejecucion de la suite por el responsable en un clon limpio fuera de `C:\sigera`, con las ordenes que entregue la auditoria. Claude Code no participa: el responsable incorpora a mano a la bitacora el registro de T05 (formato de la seccion 7) y su estado, en un commit `gate T05: auditoria independiente` que solo cambia la bitacora. Con hallazgos, el responsable deja T05 en BLOQUEADO y agrega en la tabla, inmediatamente despues de T05, la fila `T05b` (ABIERTO, correccion de los hallazgos, alcance segun el registro de T05); el prompt universal la toma como primer gate ABIERTO. Cerrado T05b, la auditoria se repite sobre lo corregido y el responsable registra el resultado como `T05c` (EXTERNO), y asi sucesivamente. | Informe CORRECTO o con hallazgos; T05 (o la ultima auditoria) pasa a CERRADO solo con CORRECTO. |
| T06a | Build de entrega en Windows | Como H06d, desde el commit con T05 CERRADO. | `manifest.json` con 0.7.0, esquema 16 y `automatedTestsExecuted` true; `SHA256SUMS.txt`; los 44 archivos de `scripts\` con SHA-256 iguales a los del paquete 0.6.0; `Check-Scripts.ps1`: 40 scripts (las ordenes documentadas solo cambian si cambio una guia que enlaza, con la diferencia explicada); nombre y SHA-256 del ZIP. |
| T06b | Ensayo diferencial y etiqueta | En TEST-SIGER, con el paquete de T06a: raiz de ensayo nueva (por ejemplo `D:\Ensayo-T06b\SIGER`; `D:\SIGER` es evidencia de H06e) y una cuenta de ensayo nueva con otro nombre, creada por el responsable (el perfil de `siger-ensayo` sigue pendiente de retiro). V4 completo como en H06e (estructura sintetica con coberturas, nivel central y una sede PROYECTADA; Formulario sintetico generado para la cuenta nueva; Manual; clave del buscador; D17; `Install-Service.ps1`; `Start.ps1`; `Estado.ps1` con 0.7.0 y esquema 16; `Test-Service.ps1 -VentanaAutorizada -IncluirMySql`; escucha por red). En la interfaz: cobertura e historia, escenarios 5, 6 y 7 y el aviso. Al final, limpieza del ensayo, `SOURCE_SHA256.txt` y etiqueta anotada `v0.7.0`. No se repiten P0, V0-V3, plan B, reinicio real, Graph ni los rechazos de D17: los scripts son identicos y quedaron acreditados en H06e. | Resultado de cada paso; nada fuera de las rutas del ensayo; limpieza verificada. |

## 7. Formato del registro de cierre (en la bitacora)

Maximo 20 lineas por gate; lo importante es lo que necesita la siguiente sesion.

```
### TNN - <titulo>   (CERRADO | BLOQUEADO)
- Fecha: AAAA-MM-DD (commit `gate TNN: ...`)
- Hecho: <cambios principales, en 2-5 lineas>
- Archivos clave: <rutas>
- Verificacion: JUnit <n> ok (antes <m>); frontend <n> ok; build ok; scripts <ok | no aplica>; humo <ok | no aplica>
- Evidencia de mutacion: <regla -> prueba que fallo | no aplica>
- Condiciones de la seccion 2: <comprobadas con git diff | no aplica>
- Desvios del plan: <ninguno | que y por que>
- Hallazgos para gates siguientes: <datos que la proxima sesion debe saber>
- Pendientes: <tarea -> gate o paso que la toma>
- Verificacion manual sugerida al usuario: <pasos | no aplica>
```

## 8. Prompt universal de continuidad (identico en todas las sesiones de Claude Code)

El responsable inicia cada sesion con `/clear` y pega siempre este mismo texto, sin cambios. No nombra ningun gate: la
sesion identifica el primer gate ABIERTO en la bitacora (los EXTERNO, CERRADO, BLOQUEADO y POSTERGADO se saltan). La
fuente de verdad es el repositorio, no la conversacion.

> Lee íntegramente `CLAUDE.md`, `docs/PLAN_0_7_0.md` y `docs/BITACORA_0_7_0.md`.
> Identifica el primer gate `ABIERTO` según la bitácora y trabaja exclusivamente en él, respetando el protocolo de la sección 5 del plan.
> Antes de modificar cualquier archivo:
> * Verifica el estado de Git y las precondiciones del gate.
> * Revisa los antecedentes, decisiones y restricciones aplicables.
> * Realiza el inventario técnico y presenta un plan de implementación con las pruebas previstas.
> * DETENTE y espera mi aprobación explícita (GO).
>
> Únicamente después del GO:
> * Ejecuta el gate dentro del alcance aprobado.
> * Realiza las pruebas exigidas, incluidas las de regresión y mutación cuando correspondan.
> * Comprueba las restricciones del plan.
> * Documenta el cierre en la bitácora con el formato de la sección 7.
> * Revisa el alcance del commit y la ausencia de archivos sensibles.
> * Realiza commit y push a `main` en `origin`.
> * DETENTE. No comiences el siguiente gate.
>
> No dependas de conversaciones anteriores ni de información no documentada. Si falta información, existe una discrepancia o necesitas ampliar el alcance, detente y consulta antes de proceder.
> Responde siempre en español.

Cada cierre deja en la bitacora todo lo que la sesion siguiente necesita (estado, hallazgos, pendientes y evidencias):
ninguna sesion depende de lo conversado en otra.
