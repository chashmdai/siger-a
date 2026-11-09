[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A',[Parameter(Mandatory=$true)][string]$Release,[Parameter(Mandatory=$true)][string]$BackupDirectory)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root
if(App-Running $s){throw 'Deten solo SIGER A antes de actualizar.'}
$backup=Test-SigerBackup $BackupDirectory
if($backup.sourceRoot -ine $s.root -or $backup.database -ne $s.database -or $backup.jarSha256 -ine $s.jarSha256){throw 'Falta respaldo de esta version e instancia.'}
$Release=(Resolve-Path $Release).Path;$m=Get-Content (Join-Path $Release 'manifest.json') -Raw | ConvertFrom-Json
if(-not $m.automatedTestsExecuted){throw 'El paquete fue compilado sin pruebas. No se promueve con este actualizador.'}
if((Get-SigerFileHash (Join-Path $Release 'app\siger-a.jar') -Algorithm SHA256).Hash -ine $m.jarSha256){throw 'SHA-256 del release no coincide.'}
$journal=Join-Path $Root 'config\update-state.json'
if(Test-Path $journal){$old=Get-Content $journal -Raw|ConvertFrom-Json;if($old.phase -ne 'COMPLETE'){throw 'Existe una actualizacion pendiente o fallida. Revisar su diario antes de continuar.'}}
if(-not $PSCmdlet.ShouldProcess($Root,'Respaldar descriptor, migrar candidata y seleccionar solo si tiene exito')){return}
$tag='update-'+(Get-Date -Format 'yyyyMMdd-HHmmss');$target=Join-Path $Root "releases\$tag"
if(Test-Path $target){throw 'El directorio de version ya existe.'}
Copy-Item $Release $target -Recurse
Copy-Item (Join-Path $Root 'config\instance.json') (Join-Path $Root "config\instance-$tag.previous.json")
$record=[PSCustomObject]@{phase='MIGRATING';startedUtc=(Get-Date).ToUniversalTime().ToString('o');previousJar=$s.activeJar;previousSha256=$s.jarSha256;candidateJar=(Join-Path $target 'app\siger-a.jar');candidateSha256=$m.jarSha256;candidateVersion=$m.version;backup=$BackupDirectory;message='No iniciar hasta confirmar migracion.'}
Save-InstanceJson $record $journal
# Instalar primero el guard de arranque; no cambia la seleccion del JAR activo.
Copy-Item (Join-Path $target 'scripts\*') (Join-Path $Root 'scripts') -Recurse -Force
try {
    & (Join-Path $target 'scripts\Migrate.ps1') -Root $Root -BackupDirectory $BackupDirectory -CandidateJar $record.candidateJar -CandidateSha256 $record.candidateSha256
    $record.phase='PROMOTING';Save-InstanceJson $record $journal
    $s.activeJar=$record.candidateJar;$s.jarSha256=$record.candidateSha256
    Save-InstanceJson $s (Join-Path $Root 'config\instance.json')
    $record.phase='PENDING_START';$record.message='Migracion completada. Ejecutar Start.ps1 y comprobar salud.';Save-InstanceJson $record $journal
    Write-Host 'Candidata migrada y seleccionada. Ejecuta Start.ps1: confirmara version y esquema antes de declarar disponible.'
} catch {
    $record.phase=if($record.phase -eq 'MIGRATING'){'MIGRATION_FAILED'}else{'PROMOTION_FAILED'}
    $record.message='Arranque bloqueado. Se conserva respaldo y descriptor anterior. No hacer downgrade, repair ni borrar filas.'
    Save-InstanceJson $record $journal
    throw
}
