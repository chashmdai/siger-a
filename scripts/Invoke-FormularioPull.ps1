[CmdletBinding()]
param([string]$Root='D:\SIGER-A',[switch]$ConsumidorUnicoConfirmado,
    # Comprobacion previa SIN reclamar ni confirmar entregas: configuracion completa, DNS, TLS con la CA, ruta y Bearer.
    [switch]$SoloComprobar)
# UN ciclo manual del consumidor del Formulario (PULL, originales, COMMIT y ACK), o con -SoloComprobar la sonda que no
# toca la cola, en un proceso sin servidor web ni tareas programadas. No programa ejecuciones, no deja procesos y no toca
# SMR. Secretos DPAPI solo en variables del proceso; nunca como argumentos Java.
. (Join-Path $PSScriptRoot 'Environment.ps1')
if(-not $SoloComprobar -and -not $ConsumidorUnicoConfirmado){throw 'Confirma con -ConsumidorUnicoConfirmado que el consumidor del Formulario de SMR esta detenido con autorizacion: dos consumidores de la misma cola se repartirian las entregas.'}
$s=Read-Settings $Root
$journal=Join-Path $Root 'config\update-state.json'
if(Test-Path $journal){$update=Get-Content $journal -Raw|ConvertFrom-Json;if($update.phase -ne 'COMPLETE'){throw "Actualizacion en curso ($($update.phase)). No se ejecuta el ciclo."}}
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
Require-Java25 $s.javaHome
if((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ine $s.jarSha256){throw 'JAR alterado.'}
if(-not $SoloComprobar){
    Require-ProductionPull $s
    if(-not(App-Running $s)){throw 'SIGER debe estar activo y sano (Start.ps1) antes del ciclo: el esquema y la custodia son los del servicio.'}
}
$config=Join-Path $Root 'config'
$pull=Get-FormularioPullConfig $s
if(-not $pull){throw 'Falta config\formulario-pull.json (Import-SecretPackage.ps1 lo crea).'}
if(-not $SoloComprobar -and $pull.Mode -eq 'AUTOMATICO'){throw 'formulario-pull.json esta en modo AUTOMATICO: el servicio consume la cola de forma permanente. No se ejecuta un ciclo manual en paralelo.'}
$mail=Get-GraphMailConfig $s

$secret=Import-Clixml (Join-Path $config 'secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try{Require-OwnMySql $s $client}finally{Remove-Item -LiteralPath $client}
New-Item -ItemType Directory -Force -Path (Join-Path $Root 'logs') | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss';$kind=if($SoloComprobar){'formulario-sonda'}else{'formulario-pull'}
$out=Join-Path $Root "logs\$kind-$stamp.out.log";$err=Join-Path $Root "logs\$kind-$stamp.err.log"
try{
    Set-SigerEnvironment $s $secret
    # Sin tareas programadas: este proceso no envia correo; con Graph configurado solo encola los avisos que genere,
    # que despues envia el servicio.
    $env:SIGER_SCHEDULING='false'
    if($SoloComprobar){Set-FormularioPullEnvironment $pull -Probe}else{Set-FormularioPullEnvironment $pull -RunOnce}
    if($mail -and -not $SoloComprobar){Set-GraphMailEnvironment $mail -EnqueueOnly}
    $arguments=@('-Xms64m','-Xmx768m',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"",'--spring.main.web-application-type=none')
    $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -Wait -WindowStyle Hidden
    $code=$p.ExitCode
}finally{Clear-SigerEnvironment}
$marker=if($SoloComprobar){'Sonda del Formulario:'}else{'Ciclo del Formulario:'}
$summary=Select-String -LiteralPath $out -Pattern $marker -SimpleMatch | Select-Object -Last 1
$line=if($summary){$summary.Line.Substring($summary.Line.IndexOf($marker))}else{''}
if($line){Write-Host $line}
if($SoloComprobar){
    # Constancia local (sin secretos) que exige Set-Integration.ps1 antes de activar el consumo automatico.
    $record=[PSCustomObject]@{utc=(Get-Date).ToUniversalTime().ToString('o');ready=($code -eq 0);origin=$pull.Origin;ackKeyId=$pull.AckKeyId;summary=$line;jarSha256=$s.jarSha256}
    Save-InstanceJson $record (Join-Path $Root 'run\formulario-sonda.json')
    if($code -ne 0){throw "Sonda no acreditada (codigo $code). Revisa logs\formulario-sonda-$stamp.*.log: configuracion, DNS, TLS/CA o Bearer. No se reclamo ninguna entrega."}
    Write-Host 'Sonda acreditada: configuracion, TLS y Bearer correctos. No se reclamo ni confirmo ninguna entrega.'
    return
}
if($code -ne 0){throw "El ciclo termino con incidentes o no inicio (codigo $code). Revisa logs\formulario-pull-$stamp.*.log antes de repetir; no se reintenta automaticamente."}
Write-Host 'Ciclo del Formulario completado sin incidentes. No queda ningun proceso ni tarea de consumo activo.'
