[CmdletBinding()]
param([string]$Root='D:\SIGER-A',[int]$WaitSeconds=90)
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root;$journal=Join-Path $Root 'config\update-state.json';$update=$null
if(Test-Path $journal){$update=Get-Content $journal -Raw|ConvertFrom-Json;if($update.phase -notin @('PENDING_START','START_FAILED','COMPLETE')){throw "Arranque bloqueado por actualizacion: $($update.phase). No se inicia el JAR anterior."}}
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
Require-Java25 $s.javaHome
if((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ine $s.jarSha256){throw 'JAR alterado.'}
$manifestPath=Join-Path (Split-Path (Split-Path $s.activeJar -Parent) -Parent) 'manifest.json'
$manifest=Get-Content $manifestPath -Raw|ConvertFrom-Json
& (Join-Path $PSScriptRoot 'Start-MySql.ps1') -Root $Root
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try{
    $ok=$false
    for($i=0;$i -lt 30;$i++){& (Join-Path $s.mysqlHome 'bin\mysql.exe') "--defaults-file=$client" --batch -e 'SELECT 1' *> $null;if($LASTEXITCODE -eq 0){$ok=$true;break};Start-Sleep 1}
    if(-not $ok){throw 'MySQL propio no responde.'};Require-OwnMySql $s $client
}finally{Remove-Item -LiteralPath $client}
if(-not(App-Running $s)){
    Free-Port $s.webPort
    try{
        Set-SigerEnvironment $s $secret
        $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
        $arguments=@('-Xms128m','-Xmx1024m',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"")
        $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput (Join-Path $Root "logs\app-$stamp.out.log") -RedirectStandardError (Join-Path $Root "logs\app-$stamp.err.log") -PassThru -WindowStyle Hidden
        [IO.File]::WriteAllText((Join-Path $Root 'run\app.pid'),[string]$p.Id,[Text.Encoding]::ASCII)
    }finally{Clear-SigerEnvironment}
}
try{
    $health=$null
    for($i=0;$i -lt $WaitSeconds;$i++){
        if(-not(App-Running $s)){throw 'El proceso termino antes de estar listo. Revisa el primer error del log privado.'}
        try{$h=Invoke-SigerHealth $s $secret;if($h.status -eq 'UP' -and $h.version -eq $manifest.version -and [int]$h.schema -eq 7){$health=$h;break}}catch{}
        Start-Sleep -Seconds 1
    }
    if(-not $health){throw 'No se acredito salud HTTPS/version dentro del tiempo de espera. No se informa arranque correcto.'}
    if($update -and $update.phase -ne 'COMPLETE'){$update.phase='COMPLETE';$update.message='Arranque confirmado por HTTPS con certificado local fijado, version y esquema.';Save-InstanceJson $update $journal}
    Write-Host "SIGER A $($health.version) disponible; esquema $($health.schema). Abre https://localhost:$($s.webPort) desde RDP."
    Write-Host 'No se abrio firewall ni se habilitaron SMR o correo.'
}catch{
    if($update -and $update.phase -ne 'COMPLETE'){$update.phase='START_FAILED';$update.message='No se confirmo salud. Conserva logs; no selecciones el JAR viejo sobre el esquema actualizado.';Save-InstanceJson $update $journal}
    throw
}
