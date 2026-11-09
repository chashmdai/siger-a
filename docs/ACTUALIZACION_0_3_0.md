# SIGER A 0.3.0 — actualizacion de organigrama y SOL

**Sin datos reales, sin tocar la VM ni SMR.** Es una actualizacion incremental de 0.2.0.

1. Salir de SIGER y detener `Run-Local.ps1` con Ctrl+C. Cerrar sesiones existentes en el navegador.
2. Respaldar privadamente toda la carpeta `var/` mientras SIGER esta detenido, incluido `local-db` y los CSV de claves. No borrar ningun archivo.
3. Extraer `SIGER-A-cambios-0.3.0.zip` en `C:\Users\Benja\Documents` (la carpeta **padre** de `siger-a`), aceptando reemplazar. No extraer dentro de `siger-a`.
4. Conservar `.tools/`, `frontend/package-lock.json`, `frontend/node_modules/`, `var/`, la BD local y todas las claves privadas.
5. Desde `siger-a`, ejecutar:

   `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -IncludeJava`

6. Si termina correctamente, ejecutar:

   `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-Local.ps1`

7. Flyway aplica **V3** al arrancar. No editar V1/V2, borrar H2 ni ejecutar `flyway repair`. Si falla, detener y compartir solo el error, sin claves.

## Si la base proviene de 0.2.0

- `jefatura.upr` pasa a llamarse **`coordinacion.requerimientos`** (misma clave; ID, Casos e historial intactos).
- `jefatura.upd` pasa a llamarse **`direccion.upd`** (misma clave; deja de tener jefatura ficticia del area Educacion).
- `direccion.upr`, `distribucion`, `profesional` y otras cuentas existentes **mantienen sus claves**. No modificar manualmente `bootstrap-usuarios.csv`: los dos nombres son alias historicos de prueba.
- Se crea, una sola vez, `var/bootstrap-usuarios-0.3.0.csv` con las claves de **solo las nuevas** cuentas demo. Si instalas en una BD vacia directamente la 0.3.0, todas las cuentas estaran en `bootstrap-usuarios.csv`.
- Ninguna SOL ya asumida, REQ o Caso cambia de responsable. Las SOL pendientes heredadas conservan su identificador y su ambito anterior; la autoridad de recepcion se evalua con la nueva regla.

## Cuatro pruebas de enrutamiento

1. **UPR sin area:** Distribucion registra COM y selecciona UPR sin area. La SOL aparece solo a Direccion UPR. Direccion la asume y asigna responsable de UPR.
2. **UPR / Requerimientos:** Distribucion selecciona UPR, area Requerimientos. La SOL aparece a Coordinadora de Requerimientos, **no** a Direccion UPR. La Coordinadora la asume y puede abrir un Caso con sus profesionales.
3. **UPR / Proteccion de Derechos:** area indicada, pero la SOL llega a Direccion UPR. La Coordinadora de Requerimientos no la recibe.
4. **UPD / Difusion:** area indicada, pero recibe Direccion UPD. Puede asignar `profesional.difusion`, asumir REQ y abrir un Caso gestionado en UPD-DIF. Como comprobacion adicional breve, UEP/Estudios y UGI sin area deben llegar a sus Direcciones.

En todos los casos: una sola SOL por COM, sin REQ hasta asumir, Casos solo cuando se requiera seguimiento, misma persistencia y permisos backend. No repetir la bateria de 0.2.0 ni probar SMR/Graph.
