[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A')
# Retira solo el REGISTRO de los servicios SIGER y SIGERMySQL de esta instancia (ya detenidos). No borra datos,
# configuracion, logs ni el host compilado; la instancia vuelve a operarse con Start.ps1/Stop.ps1.
. (Join-Path $PSScriptRoot 'Common.ps1')
if(-not([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Ejecuta en PowerShell elevada (administrador).'}
$s=Read-Settings $Root
$svc=Get-SigerServices $s
if(-not $svc){throw 'No hay servicios registrados para esta instancia.'}
if($svc.App.Status -ne 'Stopped' -or $svc.MySql.Status -ne 'Stopped'){throw 'Deten primero los servicios con Stop.ps1 -AlsoMySql.'}
if(-not $PSCmdlet.ShouldProcess("$($svc.Info.serviceName), $($svc.Info.mysqlServiceName)",'Retirar el registro de los servicios')){return}
& sc.exe delete ([string]$svc.Info.serviceName) | Out-Null
if($LASTEXITCODE -ne 0){throw 'No se retiro el servicio de la aplicacion.'}
if((Invoke-Native {& (Join-Path $s.mysqlHome 'bin\mysqld.exe') --remove ([string]$svc.Info.mysqlServiceName)}) -ne 0){throw 'No se retiro el servicio del motor propio.'}
$file=Join-Path $Root 'config\service.json'
Move-Item -LiteralPath $file -Destination (Join-Path $Root ('config\service-retirado-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.json'))
Write-Host 'Registro de servicios retirado. Datos, configuracion y logs se conservan.'
