[CmdletBinding()]
param([string]$Root='D:\SIGER-A',[switch]$ConsumidorUnicoConfirmado)
# UN ciclo manual del consumidor del Formulario (PULL, originales, COMMIT y ACK) en un proceso sin servidor web ni
# tareas programadas, junto al servicio activo. No programa ejecuciones, no deja procesos y no toca SMR.
# Secretos DPAPI solo en variables del proceso; nunca como argumentos Java.
. (Join-Path $PSScriptRoot 'Environment.ps1')
if(-not $ConsumidorUnicoConfirmado){throw 'Confirma con -ConsumidorUnicoConfirmado que el consumidor del Formulario de SMR esta detenido con autorizacion: dos consumidores de la misma cola se repartirian las entregas.'}
$s=Read-Settings $Root
$journal=Join-Path $Root 'config\update-state.json'
if(Test-Path $journal){$update=Get-Content $journal -Raw|ConvertFrom-Json;if($update.phase -ne 'COMPLETE'){throw "Actualizacion en curso ($($update.phase)). No se ejecuta el ciclo."}}
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
Require-Java25 $s.javaHome
if((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ine $s.jarSha256){throw 'JAR alterado.'}
if(-not(App-Running $s)){throw 'SIGER debe estar activo y sano (Start.ps1) antes del ciclo: el esquema y la custodia son los del servicio.'}
$config=Join-Path $Root 'config'
$pull=Get-FormularioPullConfig $s
if(-not $pull){throw 'Falta config\formulario-pull.json (origen, ambito receptor y keyId del ACK).'}
if($pull.Mode -eq 'AUTOMATICO'){throw 'formulario-pull.json esta en modo AUTOMATICO: el servicio consume la cola de forma permanente. No se ejecuta un ciclo manual en paralelo.'}
$mail=Get-GraphMailConfig $s

$secret=Import-Clixml (Join-Path $config 'secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try{Require-OwnMySql $s $client}finally{Remove-Item -LiteralPath $client}
New-Item -ItemType Directory -Force -Path (Join-Path $Root 'logs') | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$out=Join-Path $Root "logs\formulario-pull-$stamp.out.log";$err=Join-Path $Root "logs\formulario-pull-$stamp.err.log"
try{
    Set-SigerEnvironment $s $secret
    # Sin tareas programadas: este proceso no envia correo; con Graph configurado solo encola los avisos que genere,
    # que despues envia el servicio.
    $env:SIGER_SCHEDULING='false'
    Set-FormularioPullEnvironment $pull -RunOnce
    if($mail){Set-GraphMailEnvironment $mail -EnqueueOnly}
    $arguments=@('-Xms64m','-Xmx768m',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"",'--spring.main.web-application-type=none')
    $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -Wait -WindowStyle Hidden
    $code=$p.ExitCode
}finally{Clear-SigerEnvironment}
$summary=Select-String -LiteralPath $out -Pattern 'Ciclo del Formulario:' -SimpleMatch | Select-Object -Last 1
if($summary){Write-Host $summary.Line.Substring($summary.Line.IndexOf('Ciclo del Formulario:'))}
if($code -ne 0){throw "El ciclo termino con incidentes o no inicio (codigo $code). Revisa logs\formulario-pull-$stamp.*.log antes de repetir; no se reintenta automaticamente."}
Write-Host 'Ciclo del Formulario completado sin incidentes. No queda ningun proceso ni tarea de consumo activo.'
