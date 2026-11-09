# Actualizar SIGER A desde 0.1.0 a 0.2.0

**Primero probar en el PC, no en la VM.** Esta actualizacion no debe borrar `var/`, reemplazar certificados ni modificar servicios. La aplicacion 0.1.0 ya compilo en el PC del operador; 0.2.0 requiere su primera compilacion.

1. Cerrar SIGER local (Ctrl+C en la consola donde corre `Run-Local.ps1`). No borrar H2.
2. Copiar la carpeta `var` a un respaldo privado local con la aplicacion detenida. No compartir `bootstrap-usuarios.csv`.
3. Extraer `SIGER-A-cambios-0.2.0.zip` **sobre** `C:\Users\Benja\Documents\siger-a`, aceptando reemplazar los archivos correspondientes. El ZIP de cambios contiene rutas `siger-a/...`: extraer a la carpeta padre `C:\Users\Benja\Documents`. No extraer dentro de `siger-a` o se creara una carpeta anidada.
4. Conservar `frontend/package-lock.json`, `.tools/`, `var/` y las claves/usuarios de prueba existentes. No es necesario borrar `node_modules`.
5. Desde la carpeta del proyecto en PowerShell, ejecutar `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1 -IncludeJava`.
6. Si Maven finaliza y genera release, iniciar con `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-Local.ps1`. La migracion `V2__agenda_recordatorios.sql` se aplica automaticamente en perfil local.
7. Verificar primero la entrada a una cuenta existente y la preservacion de COM, SOL, REQ y Casos, luego seguir `docs/PRUEBAS_MANUALES.md` apartado 13.

**Si falla una migracion**, no editar V1 ni ejecutar `flyway repair` o borrar H2 sin investigar; conservar el error y el respaldo. `Build.ps1` produce el JAR nuevo solo si se cumplen sus comprobaciones.

**Alcance nuevo:** confirmacion de distribucion sin 403; selector por ambito; tareas de equipo con recordatorio interno repetido, todas las pendientes bloquean cierre; retiro de solicitud de cierre; separacion de documentos de entrada y de gestion.

**Limites:** sin correo Graph, SMR, visor/antivirus ni datos reales. Migracion probada solo estructuralmente hasta que corras la compilacion y el arranque en tu PC. El ZIP de cambios no incluye `var`, contrasenas ni componentes binarios.
