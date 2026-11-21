# SIGER 0.5.0 - actualizar la instancia de validacion de la VM desde 0.4.0

Esquema V10 -> V14. Produccion no se actualiza: se instala limpia con el mismo release (`docs/DESPLIEGUE_VM.md`, seccion 4).
Esta guia es para la instancia de validacion que hoy corre 0.4.0 (`D:\SIGER-VALIDACION` en los ejemplos). El ensayo de
restauracion se hace igual que en una instancia productiva, porque la instancia puede tener datos ingresados durante la
validacion. Cada orden es una linea de PowerShell, **como la cuenta de servicio** salvo que se indique otra cosa.
`FECHA` es el sufijo del ZIP del release 0.5.0 (`SIGER-FECHA.zip`), compilado con pruebas (`Build.ps1 -IncludeJava`).

## Que cambia al actualizar

- Cinco rutinas de arranque, cada una una sola vez (marcador en `application_marker`), auditadas a nombre de la identidad
  tecnica `sistema:actualizacion` cuando cambian datos. Corren con `migrate-only`, es decir, dentro de `Update.ps1`:
  1. `ROL_AUTORIDAD_SUPRIMIDO_050`: revoca las AUTORIDAD vigentes o programadas (sin borrar filas).
  2. `COORDINACION_REGIONAL_050`: convierte cada `JEFATURA` de una sede en `DIRECCION` con el mismo periodo y repunta
     sus subrogancias vivas; si hay una `DIRECCION` superpuesta no la convierte (aviso `JEFATURA_EN_SEDE`).
  3. `PERSON_INDEX_V12B`: construye el indice de personas del buscador (dato derivado, sin auditoria).
  4. `MILESTONES_V14`: reconstruye los hitos de los reportes desde la historia (`reconstructed=TRUE`).
  5. Avisos de administracion en el log (`SIN_TI_PERMANENTE`, `TI_INCOMPATIBLE`, `JEFATURA_EN_SEDE`, `HITOS_OMITIDOS`).
- El ingreso pasa el usuario a minusculas y exige coincidencia exacta.
- El CSV de las bandejas exige la funcion `EXPORTACION` (en 0.4.0 lo tenia cualquier cuenta con bandeja).
- Las descargas de documentos llevan un nombre neutro (`<FOLIO>-documento-<id>.<ext>`); el nombre original sigue en la ficha.

## 1. Antes de actualizar (instancia 0.4.0 en marcha)

1. Ejecutar las cuatro consultas de solo lectura de `docs/DESPLIEGUE_VM.md` ("Actualizacion a 0.5.0: consultas
   previas") y guardar el resultado en un archivo privado. Si la consulta 4 (usuarios con mayusculas) devuelve filas,
   **no actualizar** e informarlo al responsable del proyecto.
2. Anotar las cifras de referencia, con la misma consola `mysql`:

```sql
-- a) AUTORIDAD que se revocaran: concesiones y cuentas.
SELECT COUNT(*) AS concesiones, COUNT(DISTINCT user_id) AS cuentas FROM role_assignment
WHERE role_code='AUTORIDAD' AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>UNIX_TIMESTAMP()*1000);
-- b) Comunicaciones: el indice y los hitos recorren todas.
SELECT COUNT(*) AS comunicaciones FROM communication;
-- c) TI permanente: debe ser 1 o mas; si es 0, ver "Recuperacion cuando no queda ninguna TI vigente" en INSTALACION_VM.md.
SELECT COUNT(*) AS ti_permanente FROM role_assignment g JOIN staff u ON u.id=g.user_id
WHERE g.role_code='TI' AND g.revoked_at IS NULL AND g.valid_until IS NULL AND u.enabled=TRUE;
```

3. Avisar a los funcionarios de la validacion y esperar a que no haya operaciones en curso. Detener solo SIGER (el MySQL
   propio sigue activo):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Stop.ps1' -Root 'D:\SIGER-VALIDACION'
```

## 2. Respaldo y ensayo de restauracion (obligatorio)

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Backup.ps1' -Root 'D:\SIGER-VALIDACION' -Destination 'D:\Respaldos-SIGER\ANTES-050'
```
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Restore-Check.ps1' -Root 'D:\SIGER-VALIDACION' -BackupDirectory 'D:\Respaldos-SIGER\ANTES-050' -Destination 'D:\SIGER-RESTORE-ANTES-050'
```

`Restore-Check.ps1` restaura la copia en un esquema nuevo `siger_a_restore_*` del MySQL propio y en una carpeta nueva;
no toca la base activa ni arranca otra aplicacion. **Antes de ejecutar `Update.ps1`** comprobar:

| Cifra | Donde | Esperado |
|---|---|---|
| `completed` | consola ("Restauracion comprobada en ...") y `D:\SIGER-RESTORE-ANTES-050\restore-result.json` | `true`. Con `false` o un error, detenerse: no actualizar. |
| Conteo por tabla | lo compara el script con `table-counts.csv` del respaldo | sin "Conteo distinto en ..." |
| `documentsVerified` | `restore-result.json` | igual a `SELECT COUNT(*) FROM document_item` de la instancia |
| Casos sin REQ | lo comprueba el script | sin "Casos restaurados sin REQ" |
| Esquema del ensayo | `restore-result.json` (`schema`) | anotarlo; se conserva para revision y se elimina despues de forma expresa |

Opcional: repetir sobre el esquema restaurado las consultas del paso 1 (cambiando de esquema en la consola `mysql`);
deben dar las mismas cifras.

## 3. Actualizar

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Update.ps1' -Root 'D:\SIGER-VALIDACION' -Release 'D:\Entregas\SIGER-FECHA' -BackupDirectory 'D:\Respaldos-SIGER\ANTES-050'
```

`Update.ps1` exige el respaldo de la version activa, copia el release, migra con el JAR candidato **antes** de
seleccionarlo (con las rutinas de arranque) y solo entonces cambia el JAR activo. Si falla queda en `MIGRATION_FAILED`
o `PROMOTION_FAILED` y bloquea el arranque: no arrancar el JAR 0.4.0 sobre el esquema nuevo, no hacer `flyway repair` ni
borrar filas; conservar respaldo, diario (`config\update-state.json`) y salida, e informar.

Guardar la salida de la consola. Lineas esperadas (solo cifras):

| Linea | Debe coincidir con |
|---|---|
| `Supresion de AUTORIDAD: N concesiones revocadas en M cuentas.` | cifra a) del paso 1 (no aparece si era 0) |
| `Coordinacion regional: N jefaturas convertidas en Direccion y K sin convertir por superposicion.` | consulta 1 de `DESPLIEGUE_VM.md` (K = filas con `superpuesta=1`) |
| `Indice de personas: R referencias en C comunicaciones.` | C = cifra b) |
| `Hitos: H reconstruidos en C comunicaciones.` | C = cifra b) |
| `Hitos: O eventos historicos omitidos ...` (advertencia) | idealmente ausente; ver paso 5 |
| `Administracion: <CODIGO> (cantidad N) ...` | ver paso 5 |

Luego:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER-VALIDACION\scripts\Start.ps1' -Root 'D:\SIGER-VALIDACION'
```

`Start.ps1` solo informa exito si `/api/health` responde la version `0.5.0` y el esquema `14` del manifiesto. Con los
servicios registrados, repetir la prueba de servicio (`Test-Service.ps1 -VentanaAutorizada -IncluirMySql`).

## 4. Comprobaciones despues del arranque (solo lectura)

```sql
-- Marcadores de las rutinas: deben estar los cuatro; MILESTONES_V14.amount = eventos omitidos.
SELECT code, FROM_UNIXTIME(applied_ms/1000) AS aplicado, amount FROM application_marker
WHERE code IN ('ROL_AUTORIDAD_SUPRIMIDO_050','COORDINACION_REGIONAL_050','PERSON_INDEX_V12B','MILESTONES_V14');
-- Lo hecho por sistema:actualizacion (revocaciones y conversiones), por accion.
SELECT e.action, COUNT(*) FROM audit_event e JOIN staff u ON u.id=e.actor_id
WHERE u.username='sistema:actualizacion' GROUP BY e.action;
-- Ninguna AUTORIDAD vigente y ninguna JEFATURA vigente en sedes (salvo las K sin convertir).
SELECT g.role_code, COUNT(*) FROM role_assignment g JOIN org_scope s ON s.id=g.scope_id
WHERE g.revoked_at IS NULL AND (g.valid_until IS NULL OR g.valid_until>UNIX_TIMESTAMP()*1000)
  AND (g.role_code='AUTORIDAD' OR (g.role_code='JEFATURA' AND s.regional=TRUE)) GROUP BY g.role_code;
-- Hitos reconstruidos.
SELECT COUNT(*) AS hitos_reconstruidos FROM milestone WHERE reconstructed=TRUE;
```

Como `ti`, en Administracion: revisar los avisos y "Incompatibilidades por resolver".

## 5. Si aparecen avisos

- `HITOS_OMITIDOS` ("Hitos reconstruidos con N omisiones"): eventos historicos que la carga inicial no reconocio
  (formatos de versiones anteriores a 0.4.0, por ejemplo una tarea sin "Tarea N." en su nota). La operacion no se ve
  afectada; los reportes de tiempos no consideran esos eventos. Con datos creados por 0.4.0 el ensayo sintetico de G13
  dio 0 omisiones. El aviso es permanente (no se descarta): registrar la cifra en el acta de la actualizacion.
- `JEFATURA_EN_SEDE`: una `JEFATURA` de sede que no se convirtio por superposicion. TI la revoca en el perfil de la cuenta.
- `TI_INCOMPATIBLE`: una cuenta combina TI con otras funciones. TI deja solo una de las dos en el perfil.
- `SIN_TI_PERMANENTE`: recuperar con `Recover-Ti.ps1` (`docs/INSTALACION_VM.md`).

## 6. Tareas de TI y de las Coordinaciones despues de actualizar

1. **Region de cada sede**: Administracion > Estructura > Editar. Mientras no tenga region, la sede aparece en
   "Sede sin region asignada" del selector de destino y no se propone por la region de la persona.
2. **Materia de los REQ regionales abiertos**: cada Coordinacion regional registra con el boton "Materia" (`REQ_MATTER`)
   la materia de sus REQ abiertos; sin ella las Direcciones nacionales no los leen (D9) y los reportes los cuentan en
   "REQ regionales sin materia".
3. **Calendario habil**: TI carga los feriados nacionales y regionales del anio en curso y del siguiente
   (Calendario habil > Importar). La pantalla avisa "Sin feriados cargados para AAAA" y los reportes advierten los anios sin feriados.
4. **Funciones nuevas** (perfil de cada cuenta, con el acto institucional como fundamento): `CONSULTA_INSTITUCIONAL`,
   `REPORTES`, `REPORTES_PERSONAS` y `EXPORTACION` (las dos ultimas exigen `REPORTES` en el mismo alcance). Quien
   exportaba el CSV de las bandejas en 0.4.0 necesita ahora `EXPORTACION`.
5. **Subrogancias**: las activa y termina TI (ya no hay autoridad habilitante). La cuenta que tenia AUTORIDAD sigue
   existiendo, sin esa funcion.
6. Opcional: clave de auditoria del buscador (`New-AuditSearchKey.ps1`, `docs/INSTALACION_VM.md`) antes del primer
   uso del buscador; sin ella la auditoria registra el tipo y la cantidad, sin huella del termino.
7. Recorrer `docs/PRUEBAS_MANUALES_0_5_0.md` con cuentas de la validacion.

## 7. Limites

No se ha ejecutado aun en la VM: esta actualizacion, `Update.ps1`/`Restore-Check.ps1` de 0.5.0 ni `Recover-Ti.ps1`. La
actualizacion 0.4.0 -> 0.5.0 se probo con un volcado sintetico en MySQL 8.4 (`MySql84UpgradeTest`, gate G13). Un
fallo se informa con el paso, el mensaje exacto y las cifras, sin datos personales ni secretos.
