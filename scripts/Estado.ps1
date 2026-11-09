# -Alerta: codigo de salida para monitoreo (Programador de tareas u otra herramienta): 0 sano; 2 aplicacion no sana o
# integracion permanente SIN_LATIDO, DEGRADADO o NO_DISPONIBLE.
param([string]$Root='D:\SIGER-A',[switch]$Alerta)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root;$running=App-Running $s
$result=[ordered]@{Root=$s.root;AppRunning=$running;JarSha256Matches=((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ieq $s.jarSha256);MySqlPort=$s.mysqlPort;WebUrl="https://localhost:$($s.webPort)";Schema=$s.database;Health='NOT_CHECKED'}
try{$services=Get-SigerServices $s;if($services){$result.Service="$($services.Info.serviceName)=$($services.App.Status)";$result.MySqlService="$($services.Info.mysqlServiceName)=$($services.MySql.Status)"}else{$result.Service='NO_REGISTRADO'}}catch{$result.Service='INCONSISTENTE';Write-Warning $_.Exception.Message}
try{$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');if($running){$h=Invoke-SigerHealth $s $secret;$result.Health=$h.status;$result.Version=$h.version;$result.Migration=$h.schema;if($h.PSObject.Properties['integrations']){$result.Formulario=$h.integrations.formulario;$result.Correo=$h.integrations.correo}}}catch{$result.Health='UNAVAILABLE';Write-Warning 'La aplicacion no respondio a la sonda local TLS fijada.'}
$journal=Join-Path $Root 'config\update-state.json';if(Test-Path $journal){$result.Update=(Get-Content $journal -Raw|ConvertFrom-Json).phase}
[PSCustomObject]$result
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -in @($s.mysqlPort,$s.webPort)} | Select-Object LocalAddress,LocalPort,OwningProcess
Write-Host 'Detalle de integraciones (ultimo ciclo, incidentes, ACK y correos ambiguos): Auditoria y operacion, con cuenta TI.'
Write-Host 'No se muestran credenciales, configuracion privada ni contenido de expedientes.'
if($Alerta){
    $bad=@('SIN_LATIDO','DEGRADADO','NO_DISPONIBLE')
    if($result.Health -ne 'UP' -or $bad -contains [string]$result['Formulario'] -or $bad -contains [string]$result['Correo']){Write-Warning 'ALERTA: SIGER o una integracion permanente requiere revision.';exit 2}
}
