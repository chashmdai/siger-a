[CmdletBinding()]
param([string]$Root='D:\SIGER-A')
# Se ejecuta COMO la cuenta de servicio de SIGER (Install-Service.ps1 la lanza con esa identidad; tambien puede usarse
# con runas). Informa, sin mostrar secretos, si la cuenta no tiene privilegios administrativos y si descifra la custodia
# DPAPI de la instancia. Salida: una linea JSON; codigo 0 solo si la identidad es apta.
$ErrorActionPreference='Stop'
$me=[Security.Principal.WindowsIdentity]::GetCurrent()
# Pertenencia a Administradores, incluso como grupo de solo denegacion (token filtrado por UAC): un servicio no se filtra.
$groups=(& "$env:SystemRoot\System32\whoami.exe" /groups /fo csv /nh) -join "`n"
$admin=$groups -match 'S-1-5-32-544' -or $me.Groups.Value -contains 'S-1-5-32-544'
$dpapi=$false
try{$null=Import-Clixml -LiteralPath (Join-Path $Root 'config\secrets.clixml');$dpapi=$true}catch{}
$owner=$null
try{$owner=(Get-Content -LiteralPath (Join-Path $Root 'config\instance.json') -Raw|ConvertFrom-Json).ownerSid}catch{}
$result=[ordered]@{sid=$me.User.Value;account=$me.Name;administrator=[bool]$admin;dpapi=$dpapi;instanceOwner=([string]$owner -eq $me.User.Value)}
($result|ConvertTo-Json -Compress)
if($admin -or -not $dpapi -or -not $result.instanceOwner){exit 3}
