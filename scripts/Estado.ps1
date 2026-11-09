param([string]$Root='D:\SIGER-A')
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root;$running=App-Running $s
$result=[ordered]@{Root=$s.root;AppRunning=$running;JarSha256Matches=((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ieq $s.jarSha256);MySqlPort=$s.mysqlPort;WebUrl="https://localhost:$($s.webPort)";Schema=$s.database;Health='NOT_CHECKED'}
try{$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');if($running){$h=Invoke-SigerHealth $s $secret;$result.Health=$h.status;$result.Version=$h.version;$result.Migration=$h.schema}}catch{$result.Health='UNAVAILABLE';Write-Warning 'La aplicacion no respondio a la sonda local TLS fijada.'}
$journal=Join-Path $Root 'config\update-state.json';if(Test-Path $journal){$result.Update=(Get-Content $journal -Raw|ConvertFrom-Json).phase}
[PSCustomObject]$result
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -in @($s.mysqlPort,$s.webPort)} | Select-Object LocalAddress,LocalPort,OwningProcess
Write-Host 'No se muestran credenciales, configuracion privada ni contenido de expedientes.'
