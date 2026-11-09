[CmdletBinding()]
param([string]$Root='D:\SIGER-A')
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root;$exe=Join-Path $s.mysqlHome 'bin\mysqld.exe';$ini=Join-Path $Root 'mysql\my.ini';$pidfile=Join-Path $Root 'mysql\data\siger-a.pid'
$services=Get-SigerServices $s
if($services){if($services.MySql.Status -ne 'Running'){Start-Service -Name $services.Info.mysqlServiceName};Write-Host 'Servicio del motor propio activo.';return}
if(Test-Path $pidfile){$proc=Own-Process ([int](Get-Content $pidfile -Raw).Trim()) $exe $ini;if($proc){Write-Host 'MySQL propio ya esta activo.';return}}
Free-Port $s.mysqlPort
Start-Process -FilePath $exe -ArgumentList "`"--defaults-file=$ini`"" -WindowStyle Hidden | Out-Null
Write-Host 'Inicio solicitado unicamente para el MySQL propio de SIGER. Comprueba Estado.ps1.'
