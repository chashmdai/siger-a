[CmdletBinding()]
param([string]$Root='D:\SIGER-A')
# Proceso del servicio Windows SIGER (lo lanza SigerService.exe; no ejecutar a mano). Mismas comprobaciones que
# Start.ps1, secretos DPAPI de la cuenta del servicio solo en variables del proceso y Java en primer plano: su codigo de
# salida es el del servicio. La parada ordenada llega por run\stop.request (SigerService.exe o Stop.ps1).
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root
$journal=Join-Path $Root 'config\update-state.json'
if(Test-Path $journal){$update=Get-Content $journal -Raw|ConvertFrom-Json;if($update.phase -notin @('PENDING_START','START_FAILED','COMPLETE')){throw "Arranque bloqueado por actualizacion: $($update.phase)."}}
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
if(Test-Path (Join-Path $Root 'config\piloto-cerrado.json')){throw 'Instancia de validacion cerrada: se conserva como evidencia y no se vuelve a iniciar.'}
Require-Java25 $s.javaHome
if((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ine $s.jarSha256){throw 'JAR alterado.'}
if(App-Running $s){throw 'Ya hay un proceso SIGER de esta instancia fuera del servicio. Detenlo con Stop.ps1 antes de iniciar el servicio.'}
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml')
# El servicio SIGERMySQL es dependencia; tras un reinicio del equipo puede tardar en aceptar conexiones.
$client=MySql-ClientFile $s $secret 'owner'
try{
    $ok=$false
    for($i=0;$i -lt 120;$i++){if((Invoke-Native {& (Join-Path $s.mysqlHome 'bin\mysql.exe') "--defaults-file=$client" --batch -e 'SELECT 1'} -Quiet) -eq 0){$ok=$true;break};Start-Sleep 1}
    if(-not $ok){throw 'MySQL propio no responde.'};Require-OwnMySql $s $client
}finally{Remove-Item -LiteralPath $client}
Free-Port $s.webPort
$stop=Join-Path $Root 'run\stop.request';if(Test-Path -LiteralPath $stop){Remove-Item -LiteralPath $stop}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
try{
    Set-SigerServiceEnvironment $s $secret
    # Log rotativo propio (logs\siger.log, 20 MB x 30); la consola solo conserva errores tempranos de la JVM.
    $env:SIGER_LOG_FILE=Join-Path $Root 'logs\siger.log'
    $arguments=@('-Xms128m','-Xmx1024m','-XX:+ExitOnOutOfMemoryError',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"",'--logging.pattern.console=')
    $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput (Join-Path $Root "logs\service-$stamp.out.log") -RedirectStandardError (Join-Path $Root "logs\service-$stamp.err.log") -PassThru -NoNewWindow
    $null=$p.Handle
    [IO.File]::WriteAllText((Join-Path $Root 'run\app.pid'),[string]$p.Id,[Text.Encoding]::ASCII)
}finally{Clear-SigerEnvironment}
$p.WaitForExit()
$pidfile=Join-Path $Root 'run\app.pid'
if((Test-Path -LiteralPath $pidfile) -and (Get-Content -LiteralPath $pidfile -Raw).Trim() -eq [string]$p.Id){Remove-Item -LiteralPath $pidfile}
exit $p.ExitCode
