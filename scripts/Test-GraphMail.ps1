[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
param(
    [string]$Root='D:\SIGER-A',
    # Token: solo pide un token OAuth (no envia correo). Envio: ademas envia UN correo de texto fijo a -Destinatario.
    [Parameter(Mandatory=$true)][ValidateSet('Token','Envio')][string]$Modo,
    # Solo con -Modo Envio: direccion institucional fija de un dominio permitido de correo-graph.json.
    [string]$Destinatario=''
)
# Prueba controlada de Microsoft Graph (0.6.0, H01). Ejecutar COMO la cuenta de servicio (duena de DPAPI), con el
# MySQL propio activo; el servicio SIGER puede estar activo o detenido. Lanza un proceso de SIGER sin servidor web ni
# tareas programadas (GraphCheck) que:
#   Token  acredita la credencial y la salida de la VM hacia login.microsoftonline.com; nunca llama a sendMail.
#   Envio  acredita ademas la salida hacia graph.microsoft.com y el permiso Mail.Send sobre el remitente.
# No usa la cola de avisos ni datos ciudadanos, no escribe en la base y no cambia el modo del correo (puede seguir
# DESACTIVADO). Muestra solo modo, estado, fase, HTTP y request-id, y deja la misma evidencia (con utc y jarSha256, sin
# destinatario, secreto, token, tenant ni cliente) en run\graph-prueba.json, tambien cuando la prueba falla.
# Confirmacion: ConfirmImpact High y ShouldProcess solo en Envio, por lo que Windows PowerShell PIDE confirmacion antes
# de enviar (con la preferencia por defecto $ConfirmPreference=High). -WhatIf no envia; -Confirm:$false la omite de forma
# expresa. Token no pide confirmacion porque no envia correo.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root
$journal=Join-Path $Root 'config\update-state.json'
if(Test-Path $journal){$update=Get-Content $journal -Raw|ConvertFrom-Json;if($update.phase -ne 'COMPLETE'){throw "Actualizacion en curso ($($update.phase)). No se ejecuta la prueba."}}
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
$me=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if(-not $s.PSObject.Properties['ownerSid'] -or [string]$s.ownerSid -ne $me){throw 'Ejecuta la prueba como la cuenta de servicio de la instancia (ownerSid de instance.json): el secreto esta protegido con su DPAPI.'}
Require-Java25 $s.javaHome
if((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ine $s.jarSha256){throw 'JAR alterado.'}
$mail=Get-GraphMailConfig $s -CualquierModo
if(-not $mail){throw 'Falta config\correo-graph.json. Instala la credencial con Import-GraphCredential.ps1.'}
$mode=if($Modo -eq 'Token'){'TOKEN'}else{'ENVIO'}
$to=$Destinatario.Trim()
if($mode -eq 'TOKEN' -and $to){throw 'El modo Token no lleva destinatario: no envia correo.'}
if($mode -eq 'ENVIO' -and -not (Test-GraphRecipient $to ($mail.Domains -split ','))){throw 'Indica en -Destinatario una direccion institucional de un dominio permitido de correo-graph.json.'}
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try{Require-OwnMySql $s $client}finally{Remove-Item -LiteralPath $client}
if($mode -eq 'TOKEN' -and $WhatIfPreference){Write-Host 'WhatIf: se pediria un token a Graph (sin enviar correo).';return}
if($mode -eq 'ENVIO' -and -not $PSCmdlet.ShouldProcess('un destinatario institucional (texto fijo de prueba)','Enviar UN correo real por Microsoft Graph')){Write-Host 'Envio no confirmado: no se contacto Graph ni se registro una prueba.';return}

New-Item -ItemType Directory -Force -Path (Join-Path $Root 'logs'),(Join-Path $Root 'run') | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$out=Join-Path $Root "logs\graph-prueba-$stamp.out.log";$err=Join-Path $Root "logs\graph-prueba-$stamp.err.log"
$code=-1
try{
    Set-SigerEnvironment $s $secret
    $env:SIGER_SCHEDULING='false'
    Set-GraphCheckEnvironment $mail $mode $to
    $arguments=@('-Xms64m','-Xmx512m',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"",'--spring.main.web-application-type=none')
    $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -Wait -WindowStyle Hidden
    $code=$p.ExitCode
}finally{Clear-SigerEnvironment}
$marker='Prueba de Graph:'
$summary=if(Test-Path -LiteralPath $out){Select-String -LiteralPath $out -Pattern $marker -SimpleMatch | Select-Object -Last 1}else{$null}
$line=if($summary){$summary.Line.Substring($summary.Line.IndexOf($marker))}else{''}
# Evidencia: solo valores con formato cerrado; lo que no calza queda nulo. Sin linea de resultado, la prueba no llego a
# Graph (configuracion o arranque rechazados): estado SIN_RESULTADO y fase PROCESO.
$state='SIN_RESULTADO';$phase='PROCESO';$http=$null;$requestId=$null
if($line -cmatch '^Prueba de Graph: modo=(TOKEN|ENVIO) estado=([A-Z_]{1,24}) fase=([A-Z_]{1,24}) http=([0-9]{3}|-) requestId=([0-9a-f-]{36}|-)$'){
    $state=$Matches[2];$phase=$Matches[3]
    if($Matches[4] -ne '-'){$http=[int]$Matches[4]}
    if($Matches[5] -ne '-'){$requestId=$Matches[5]}
    Write-Host $line
}
$ok=($code -eq 0 -and $state -eq 'ACEPTADO')
$record=[PSCustomObject]@{modo=$mode;estado=$state;fase=$phase;http=$http;requestId=$requestId;utc=(Get-Date).ToUniversalTime().ToString('o');jarSha256=$s.jarSha256}
Save-InstanceJson $record (Join-Path $Root 'run\graph-prueba.json')
if(-not $ok){throw "Prueba de Graph no acreditada (codigo $code, estado $state, fase $phase). Revisa logs\graph-prueba-$stamp.*.log: credencial, salida de red o permiso Mail.Send. Evidencia en run\graph-prueba.json."}
if($mode -eq 'TOKEN'){Write-Host 'Token acreditado: credencial valida y salida hacia login.microsoftonline.com. No se envio correo. Evidencia en run\graph-prueba.json.'}
else{Write-Host 'Envio aceptado por Graph (HTTP 202; no acredita la entrega): confirma con el destinatario que llego el correo de prueba. Evidencia en run\graph-prueba.json.'}
