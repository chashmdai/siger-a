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
if(-not(App-Running $s)){throw 'SIGER A debe estar activo y sano (Start.ps1) antes del ciclo: el esquema y la custodia son los del servicio.'}

$config=Join-Path $Root 'config'
$settingsFile=Join-Path $config 'formulario-pull.json'
if(-not(Test-Path -LiteralPath $settingsFile -PathType Leaf)){throw 'Falta config\formulario-pull.json (origen, ambito receptor y keyId del ACK).'}
$pull=Get-Content -LiteralPath $settingsFile -Raw|ConvertFrom-Json
foreach($name in @('origin','scope','ackKeyId')){if(-not $pull.PSObject.Properties[$name] -or -not [string]$pull.$name){throw "Falta '$name' en formulario-pull.json."}}
if([string]$pull.origin -notmatch '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/?$'){throw 'El origen del Formulario debe ser https://host sin ruta.'}
$prefix=$s.PSObject.Properties['folioPrefix']
if(-not $prefix -or [string]$prefix.Value -cnotmatch '^[A-Z][A-Z0-9]{1,15}$'){throw 'Define en instance.json un folioPrefix compatible con el comprobante del Formulario ([A-Z][A-Z0-9]{1,15}) y reinicia el servicio con ese prefijo antes del primer ciclo.'}
$trusted=Join-Path $config 'formulario-trusted-keys.json'
$private=Join-Path $config 'formulario-ack-private.pem';$public=Join-Path $config 'formulario-ack-public.pem'
$token=Join-Path $config 'formulario-pull-token.clixml'
if(-not(Test-Path -LiteralPath $trusted -PathType Leaf)){throw 'Falta config\formulario-trusted-keys.json con la clave publica de custodia del Formulario.'}
foreach($file in @($private,$token)){Require-PrivateFile $file}
if(-not(Test-Path -LiteralPath $public -PathType Leaf)){throw 'Falta config\formulario-ack-public.pem.'}
$ca=$null
if($pull.PSObject.Properties['caFile'] -and [string]$pull.caFile){$ca=Join-Path $config ([IO.Path]::GetFileName([string]$pull.caFile));if(-not(Test-Path -LiteralPath $ca -PathType Leaf)){throw 'No existe el certificado de CA indicado en formulario-pull.json.'}}

$secret=Import-Clixml (Join-Path $config 'secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try{Require-OwnMySql $s $client}finally{Remove-Item -LiteralPath $client}
New-Item -ItemType Directory -Force -Path (Join-Path $Root 'logs') | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$out=Join-Path $Root "logs\formulario-pull-$stamp.out.log";$err=Join-Path $Root "logs\formulario-pull-$stamp.err.log"
try{
    Set-SigerEnvironment $s $secret
    $env:SIGER_SCHEDULING='false'
    $env:SIGER_FORMULARIO_PULL_ENABLED='true';$env:SIGER_FORMULARIO_PULL_RUN_ONCE='true'
    $env:SIGER_FORMULARIO_PULL_ORIGIN=[string]$pull.origin;$env:SIGER_FORMULARIO_PULL_SCOPE=[string]$pull.scope
    $env:SIGER_FORMULARIO_PULL_TOKEN=Secret-Text (Import-Clixml -LiteralPath $token)
    $env:SIGER_FORMULARIO_PULL_CA_FILE=$ca
    $env:SIGER_FORMULARIO_ACK_KEY_ID=[string]$pull.ackKeyId
    $env:SIGER_FORMULARIO_ACK_PRIVATE_KEY_FILE=$private;$env:SIGER_FORMULARIO_ACK_PUBLIC_KEY_FILE=$public
    $arguments=@('-Xms64m','-Xmx768m',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"",'--spring.main.web-application-type=none')
    $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -Wait -WindowStyle Hidden
    $code=$p.ExitCode
}finally{Clear-SigerEnvironment}
$summary=Select-String -LiteralPath $out -Pattern 'Ciclo del Formulario:' -SimpleMatch | Select-Object -Last 1
if($summary){Write-Host $summary.Line.Substring($summary.Line.IndexOf('Ciclo del Formulario:'))}
if($code -ne 0){throw "El ciclo termino con incidentes o no inicio (codigo $code). Revisa logs\formulario-pull-$stamp.*.log antes de repetir; no se reintenta automaticamente."}
Write-Host 'Ciclo del Formulario completado sin incidentes. No queda ningun proceso ni tarea de consumo activo.'
