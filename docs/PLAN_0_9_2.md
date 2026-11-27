# SIGER 0.9.2 - plan de implementación

Operación y pendientes: seguimiento de la gestión por folio, selector de antecedentes, distribución ágil, sedes y
distribución territorial del Formulario, correo de avisos, hallazgos de 0.8.0 y scripts de operación. Decisiones D44-D49
(heredadas de 0.8.0, `DECISIONES_FUNCIONALES.md`, con DI-05, DI-06 y DI-09 respondidas) y D69-D72 del responsable
(2026-11-26 y 2026-11-27). Versión 0.9.2, prefijo de gates **B**, registro en `docs/BITACORA_0_9_2.md`, diseño en
`docs/DISENO_0_9_2.md`, serie en `docs/PLAN_0_9_0.md`. Empieza con A07 (0.9.1) CERRADO. Solo SIGER: el repositorio
`formulario-web` se lee. Este plan solo cambia con aprobación del responsable.

## 0. Decisiones (B00 copia D69-D72 a `DECISIONES_FUNCIONALES.md` como "Precisión 0.9.2" y retira el rótulo "hoja de ruta" de D44-D49)

- **D69 Selector de antecedentes (P-001).** En el diálogo "Vincular al REQ existente" de la COM, el campo de folio
  exacto se reemplaza por el buscador institucional (folio SIGER, COM o REQ, RUN o nombre), sin salir de la COM. Si la
  COM trae RUN, las gestiones de ese mismo RUN aparecen arriba como "posibles gestiones relacionadas". Cada resultado
  muestra su REQ con sus Casos y estados. **El destino sigue siendo solo el REQ**: la gestión lo revisa y lo encamina al
  Caso por el circuito actual. Observación opcional (vigente desde 0.8.0). Validaciones, permisos y auditoría sin
  cambios; los resultados muestran solo lo que el actor ya puede leer.
- **D70 Seguimiento por folio (P-008), como ajuste de la lectura existente.** Al buscar un folio `SIGER-XXXXXX` (o el de
  cualquier registro de la cadena), el buscador muestra primero una **vista de seguimiento** de la gestión en vez de
  abrir automáticamente la ficha: COM de origen, SOL con destino y estado, REQ si existe, Casos con número, estado y
  responsable, y las actuaciones del REQ y de cada Caso, con fechas e hitos; desde ahí se abre la ficha completa. No se
  crea una facultad nueva: la vista muestra exactamente lo que el actor ya puede leer (las reservadas siguen ocultas).
  Para que un administrativo que atiende llamadas vea toda la cadena, TI le concede la lectura institucional existente.
  Las consultas de seguimiento quedan auditadas como hoy. Confirmar la identidad de quien llama es protocolo de
  atención, no código.
- **D71 Distribución ágil (P-007.1 y P-007.3).** "Usar todas las propuestas" aplica a la vez las propuestas de tipo de
  solicitud, tipo de requirente y vía, que siguen visibles y editables antes de confirmar. "Distribuir" desde la bandeja
  «Ingreso por clasificar» abre el mismo diálogo de distribución sin entrar a la ficha. Nada se clasifica, distribuye ni
  desestima automáticamente.
- **D72 Reportes sin TI inconsistente (hallazgo de E14).** `Reports` y su exportación excluyen a una cuenta TI con una
  concesión de REPORTES insertada fuera de la aplicación, igual que el resto del sistema (D2). Se retira la excepción
  `TI_MIXED_KNOWN` de `PermisosFinalesTest`.

Heredadas (diseño en `DISENO_0_8_0.md` 15 y `DISENO_0_9_2.md`): D44 fechas de las sedes (DI-06); D45 distribución
territorial del Formulario (DI-05); D46 correo de avisos (DI-09); D48 índice de `inbox_notice` con medición; D49
antigüedad del respaldo en `Update.ps1` y script de acceso por red. D47 (observaciones de funcionarios) va en 0.9.3.

Hallazgos de 0.8.0 incluidos: resolución en el acto con derechos (GO de E09, decisión D); caracterización de la COM
en la ficha de la SOL (E09); microdatos de actuaciones y Casos (GO de E11); ZIP escrito por flujo (detención de E11);
Figura 1 en un solo XLSX (GO de E12); retención del esquema `siger_a_restore_*` del paso U (E16).

Fuera de 0.9.2 (`PLAN_0_9_0.md` 7): P-005, P-006 (salvo el ZIP por flujo), P-007.2, P-007.4, D38, corte sin lock
global y división del código del frontend.

## 1. Punto de partida

Se verifica en B00 sobre el HEAD posterior a A07: versión 0.9.1, esquema 25, conteos de pruebas de A05, `v0.9.1`
etiquetada y paso U-0.9.1 registrado (puede estar pendiente: 0.9.2 no depende de él).

## 2. Reglas para todos los gates

1. **Inmutables**: `v0.5.0` a `v0.9.1`, sus paquetes y evidencias, respaldos y constancias. El ejecutor no modifica
   DDN-SIGER ni DDN-UBUNTU.
2. **Formulario**: `formulario-web` solo se lee. La recepción de SIGER (clases de la regla 2 de `PLAN_0_9_1.md`) no
   cambia: `git diff v0.9.1` vacío para ellas.
3. **Scripts**: `git diff v0.9.1 -- scripts/ .mvn/` vacío salvo en B07 (y la generación del paquete en B10).
4. **Migraciones**: V1-V25 no se modifican; cada cambio es la siguiente `Vn__...` aditiva y portable;
   `MySql84UpgradeTest` (desde 0.4.0, 0.7.0 y 0.8.0) pasa hasta la última.
5. **Escritura y lectura**: toda escritura por `Workflow.run`; ninguna lectura recibe rol, autor ni alcance del
   navegador; visibilidad doble (`Access` y `BoardQueries.Visibility`) con su prueba de equivalencia cuando cambie
   (B01, B02, B03).
6. **Reglas vigentes**: D1-D68 no cambian salvo lo que digan D44-D49 y D69-D72.
7. **Inmutabilidad** (D35) y barridos de `ImmutabilityTest` e `InstitutionalReaderTest` para todo comando nuevo.
8. **Privacidad**: el seguimiento y el selector no revelan registros que el actor no puede leer, ni por inferencia
   (conteos, órdenes, mensajes); auditoría sin términos de búsqueda en claro, como hoy.
9. **Datos personales**: sin datos ciudadanos ni secretos en logs, URLs, auditoría, salidas ni bitácora.
10. **Dependencias**: ninguna librería nueva sin autorización expresa.
11. **Mutación**: cada regla con una prueba que falla si se rompe.
12. **Facilidad de uso**: todo gate de interfaz declara cómo lo comprobó (texto, flujo, ayuda, móvil).
13. **Comandos y entornos**: los de `CLAUDE.md`; MySQL 8.4 aislado y desechable; Graph falso; rutas de ensayo nuevas
    (`D:\Ensayo-B0n`).
14. **Documentos**: órdenes `-File` solo en las guías que enlaza `Check-Scripts.ps1`.
15. **Rendimiento**: lo que toque el motor estadístico o las exportaciones conserva los criterios medidos en E14
    (consultas del Panorama < 3 s con 50.005 COM; corte <= 13 s; ZIP <= 25 s; memoria <= 75 % del heap).

## 3. Relación con producción y plazo

- 0.9.2 no cambia producción hasta su paso U (redacta B08, ejecuta el responsable con autorización expresa).
- El acceso por red (B07) entrega el script; ejecutarlo en producción requiere TI y la red institucional. Sin acceso por
  red, el correo de avisos (D46) sale sin enlace (`siger.mail.link` sin configurar).
- Calendario de referencia: B00-B10 del 12/12 al 21/12; paso U el 22-23/12 (`PLAN_0_9_0.md` 2).

## 4. Documentación que se actualiza

`DECISIONES_FUNCIONALES.md` (B00), `CLAUDE.md`, `AGENTS.md` si cambia, `ARQUITECTURA.md`, `ALCANCE_Y_PENDIENTES.md`,
`DESPLIEGUE_VM.md` (paso U de 0.9.2, B08; uso de los scripts de B07), `INSTALACION_VM.md` si corresponde,
`deploy/config-ejemplo/*`, `docs/PRUEBAS_MANUALES_0_9_2.md` (B08) y `DISENO_0_9_2.md` para diferencias aprobadas.

## 5. Protocolo de cada sesión y corte por cuota

Idénticos a `PLAN_0_9_1.md` secciones 5 y 5 «Corte por cuota o cambio de agente», con el prefijo B, esta bitácora y sin
gates [FORMULARIO]. Gate EXTERNO: B09 (ciclo `B09b`/`B09c` como en 0.9.1). B10 exige el ciclo de B09 cerrado.

## 6. Gates

| Gate | Título | Depende de | DISENO |
|---|---|---|---|
| B00 | Línea base, decisiones y diseño verificado | A07 | 1 |
| B01 | Seguimiento de la gestión por folio (servidor e interfaz) | B00 | 2 |
| B02 | Vínculo con buscador y distribución ágil (servidor e interfaz) | B01 | 3, 4 |
| B03 | Sedes: fechas de apertura y distribución territorial del Formulario (servidor e interfaz) | B00 | 5 |
| B04 | Correo de avisos, índice de avisos y reportes sin TI inconsistente (servidor) | B00 | 6 |
| B05 | Captura: resolución en el acto con derechos y caracterización de la COM en la ficha de la SOL | B00 | 7 |
| B06 | Estadística: microdatos de actuaciones y Casos, ZIP por flujo y Figura 1 en un XLSX (servidor e interfaz) | B00 | 8 |
| B07 | Scripts: antigüedad del respaldo, acceso por red y retención del esquema de restauración | B00 | 9 |
| B08 | Integración MySQL, rendimiento, aceptación y cierre de versión | B01-B07 | 10 |
| B09 | Auditoría independiente (EXTERNO) | B08 | todo |
| B10 | Build de entrega y etiqueta | Ciclo de B09 cerrado | 10 |

### Detalle de cada gate

Valores comunes como en `PLAN_0_9_1.md` 6.

**B00 - Línea base, decisiones y diseño verificado**
- Alcance: conteos de pruebas sobre el HEAD; verificar cada referencia de `DISENO_0_9_2.md` 1 y registrar diferencias;
  confirmar con evidencia que D45 no requiere cambios del Formulario (la región vigente de la COM ya llega en sus
  entregas); si los requiriera, B03 queda solo con D44 y D45 pasa a POSTERGADO con fundamento del responsable;
  copiar decisiones; `CLAUDE.md` estado "plan 0.9.2 en curso"; prueba de actualización desde un volcado sintético de
  0.9.1 (V25).
- Archivos autorizados: documentos, pruebas y recursos de prueba, bitácora. Ninguna línea de `src/main` cambia.

**B01 - Seguimiento de la gestión por folio (D70)**
- Alcance: lectura de la cadena con actuaciones para un folio (consulta bajo demanda, solo del registro buscado, no de
  todos los resultados de una búsqueda amplia), reutilizando `PersonSearch.chains()` y los filtros de actuaciones de
  `Queries`; vista de seguimiento en el buscador cuando la búsqueda es por folio (reemplaza la apertura automática de
  `exactTarget` para folios; la búsqueda por persona sigue igual); enlaces a cada ficha solo si es legible; auditoría.
- Pruebas: roles autorizados y denegados; lectura institucional ve toda la cadena salvo reservadas; folio inexistente
  y folio no legible con la misma respuesta; casos múltiples, antecedentes admitidos y devueltos, transferencias y
  cerrados; sin inferencia por conteos; equivalencia `Access`/`Visibility` si se toca; móvil 390×844.

**B02 - Vínculo con buscador y distribución ágil (D69, D71)**
- Alcance: diálogo de vínculo con buscador embebido, sugeridos por RUN de la COM y cadena REQ-CAS por resultado;
  `COM_LINK` sin cambios de regla (destino REQ); botón "Usar todas las propuestas"; acción "Distribuir" en la bandeja
  «Ingreso por clasificar» que abre el mismo diálogo y comando de la ficha (revisión, permisos y auditoría iguales).
- Pruebas: el sugerido no muestra gestiones ilegibles; vínculo idéntico al de 0.9.1 en servidor (mismo comando y
  validaciones); propuestas aplicadas siguen editables; distribución desde la bandeja con revisión obsoleta da 409;
  ninguna acción automática.

**B03 - Sedes: fechas y distribución territorial del Formulario (D44, D45)**
- Alcance y pruebas: `DISENO_0_8_0.md` 15.1 y 15.2 completos (DI-05 y DI-06), con la equivalencia de bandejas.

**B04 - Correo de avisos, índice de avisos y reportes (D46, D48, D72)**
- Alcance: `DISENO_0_8_0.md` 15.3 (asunto por evento, folio, descripción breve, enlace solo con `siger.mail.link`) y
  15.4 (medir el plan de `DistributionNotices` en MySQL con volumen alto; índice solo si hay recorrido completo); D72.
  Las plantillas ciudadanas de 0.9.1 no cambian.
- Pruebas: Graph falso; un asunto por evento; ningún asunto ni cuerpo con datos personales; sin `siger.mail.link`, sin
  enlace; `PermisosFinalesTest` sin `TI_MIXED_KNOWN` y con la mutación que reintroduce la inconsistencia.

**B05 - Captura en el flujo (hallazgos de E09)**
- Alcance: pantallas de resolución en el acto con derechos para `COM_IMMEDIATE` y `ATTENTION_CREATE` con `resolve_now`
  (usan `Rights.inline` ya existente desde E09, decisión C del GO); la ficha de la SOL entrega la caracterización de su
  COM, y el diálogo de descarte deja de leer la ficha de la COM.
- Pruebas: mismas validaciones de servidor que E09; la lectura de la COM ya no se audita en el descarte; móvil.

**B06 - Estadística (hallazgos de E11 y E12)**
- Alcance: microdatos a nivel de actuación y de Caso en el paquete estadístico (con el contrato
  `CONTRATO_EXPORTACION_SERIES_0_8_0.md` extendido de forma aditiva); ZIP escrito por flujo a un temporal en `D:` (menos
  memoria); Figura 1 en un solo libro XLSX de cuatro hojas.
- Pruebas: equivalencia de cifras con 0.9.1; criterios de la regla 15 medidos con 50.005 COM; memoria del ZIP menor o
  igual a la de E14; temporales borrados ante falla.

**B07 - Scripts (D49, E16)**
- Alcance: `DISENO_0_8_0.md` 15.5 (antigüedad del respaldo en `Update.ps1` con parámetro y evidencia de
  `Restore-Check.ps1`; script nuevo para pasar `bindAddress` a red con instancia detenida y confirmación, sin tocar el
  firewall) y retención del esquema `siger_a_restore_*` que deja el paso U (listar y borrar con confirmación, nunca el
  esquema vigente).
- Pruebas: `Check-Scripts.ps1` (PS 5.1), ensayo en TEST-SIGER en `D:\Ensayo-B07`, casos de rechazo (respaldo viejo,
  instancia corriendo, esquema vigente).

**B08 - Integración y cierre de versión**
- Alcance: suite MySQL completa; mediciones de la regla 15; humo de B01-B06 en 1366×900 y 390×844; versión 0.9.2;
  paso U de 0.9.2 en `DESPLIEGUE_VM.md`; `PRUEBAS_MANUALES_0_9_2.md`; documentación.

**B09 - Auditoría independiente** (EXTERNO): Claude Padre, sobre el commit de B08.

**B10 - Build y etiqueta**: build de entrega, `SOURCE_SHA256.txt`, etiqueta `v0.9.2`; marcar 0.9.2 CERRADA y 0.9.3
EN CURSO en `PLAN_0_9_0.md` 1. Cierre "Cierre del plan 0.9.2".

## 7. Formato del registro de cierre

El de `PLAN_0_9_1.md` 7 con el prefijo B y sin la línea del Formulario.

## 8. Prompt universal

El de `docs/PLAN_0_9_0.md` sección 5.
