[CmdletBinding()]
param([string]$Root='D:\SIGER-A',[int]$WaitSeconds=120)
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root;$journal=Join-Path $Root 'config\update-state.json';$update=$null
if(Test-Path $journal){$update=Get-Content $journal -Raw|ConvertFrom-Json;if($update.phase -notin @('PENDING_START','START_FAILED','COMPLETE')){throw "Arranque bloqueado por actualizacion: $($update.phase). No se inicia el JAR anterior."}}
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
if(Test-Path (Join-Path $Root 'config\piloto-cerrado.json')){throw 'Instancia de validacion cerrada: se conserva como evidencia y no se vuelve a iniciar.'}
Require-Java25 $s.javaHome
if((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ine $s.jarSha256){throw 'JAR alterado.'}
$manifestPath=Join-Path (Split-Path (Split-Path $s.activeJar -Parent) -Parent) 'manifest.json'
$manifest=Get-Content $manifestPath -Raw|ConvertFrom-Json
# La configuracion de integraciones se valida antes de iniciar (un error aqui no deja un servicio en bucle de reinicio).
$null=Get-FormularioPullConfig $s;$null=Get-GraphMailConfig $s
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml')
$services=Get-SigerServices $s
if($services){
    # Instancia registrada como servicio: el administrador de servicios inicia MySQL propio y SIGER (Service.ps1).
    if($services.App.Status -ne 'Running'){
        if(App-Running $s){throw 'Hay un proceso SIGER iniciado a mano. Detenlo con Stop.ps1 antes de iniciar el servicio.'}
        Start-Service -Name $services.Info.serviceName
    }
}else{
    & (Join-Path $PSScriptRoot 'Start-MySql.ps1') -Root $Root
    $client=MySql-ClientFile $s $secret 'owner'
    try{
        $ok=$false
        for($i=0;$i -lt 30;$i++){if((Invoke-Native {& (Join-Path $s.mysqlHome 'bin\mysql.exe') "--defaults-file=$client" --batch -e 'SELECT 1'} -Quiet) -eq 0){$ok=$true;break};Start-Sleep 1}
        if(-not $ok){throw 'MySQL propio no responde.'};Require-OwnMySql $s $client
    }finally{Remove-Item -LiteralPath $client}
    if(-not(App-Running $s)){
        Free-Port $s.webPort
        $stop=Join-Path $Root 'run\stop.request';if(Test-Path -LiteralPath $stop){Remove-Item -LiteralPath $stop}
        try{
            Set-SigerServiceEnvironment $s $secret
            $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
            $arguments=@('-Xms128m','-Xmx1024m','-XX:+ExitOnOutOfMemoryError',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"")
            $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput (Join-Path $Root "logs\app-$stamp.out.log") -RedirectStandardError (Join-Path $Root "logs\app-$stamp.err.log") -PassThru -WindowStyle Hidden
            [IO.File]::WriteAllText((Join-Path $Root 'run\app.pid'),[string]$p.Id,[Text.Encoding]::ASCII)
        }finally{Clear-SigerEnvironment}
    }
}
try{
    $health=$null
    for($i=0;$i -lt $WaitSeconds;$i++){
        $alive=if($services){(Get-Service -Name $services.Info.serviceName).Status -ne 'Stopped'}else{App-Running $s}
        if(-not $alive){throw 'El proceso termino antes de estar listo. Revisa el primer error del log privado (logs\) y el registro de eventos Application (origen SIGER).'}
        try{$h=Invoke-SigerHealth $s $secret;if($h.status -eq 'UP' -and $h.version -eq $manifest.version -and [int]$h.schema -eq 10){$health=$h;break}}catch{}
        Start-Sleep -Seconds 1
    }
    if(-not $health){throw 'No se acredito salud HTTPS/version dentro del tiempo de espera. No se informa arranque correcto.'}
    if($update -and $update.phase -ne 'COMPLETE'){$update.phase='COMPLETE';$update.message='Arranque confirmado por HTTPS con certificado local fijado, version y esquema.';Save-InstanceJson $update $journal}
    Write-Host "SIGER $($health.version) disponible; esquema $($health.schema). Abre https://localhost:$($s.webPort) desde RDP."
    Write-Host "Formulario: $($health.integrations.formulario). Correo: $($health.integrations.correo). $(if($services){'Operando como servicio de Windows.'}else{'Iniciado a mano (sin servicio registrado).'})"
}catch{
    if($update -and $update.phase -ne 'COMPLETE'){$update.phase='START_FAILED';$update.message='No se confirmo salud. Conserva logs; no selecciones el JAR viejo sobre el esquema actualizado.';Save-InstanceJson $update $journal}
    throw
}
