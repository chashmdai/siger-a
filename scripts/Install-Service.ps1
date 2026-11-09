[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A',[Parameter(Mandatory=$true)][pscredential]$ServiceCredential)
# Registra el MySQL propio (SIGERMySQL) y SIGER (aplicacion) como servicios permanentes de Windows Server, con inicio
# automatico (SIGER diferido y dependiente de su motor) y recuperacion ante caidas. NO inicia ningun servicio, no cambia
# politicas, firewall, PATH ni JAVA_HOME, y no toca MySQL80, Tomcat, SQL Server ni SMR.
# SIGER se ejecuta con la MISMA cuenta que inicializo la instancia: DPAPI (config\*.clixml) solo descifra con ella.
. (Join-Path $PSScriptRoot 'Common.ps1')
if(-not([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Ejecuta en PowerShell elevada (administrador). No se registro nada.'}
$s=Read-Settings $Root
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
$app='SIGER';$db='SIGERMySQL'
if(Test-Path -LiteralPath (Join-Path $Root 'config\service.json')){throw 'Los servicios ya fueron registrados para esta instancia (config\service.json).'}
foreach($name in @($app,$db)){if(Get-Service -Name $name -ErrorAction SilentlyContinue){throw "Ya existe un servicio $name. No se sobrescribe ni se reutiliza."}}

# La cuenta del servicio es la identidad actual (propietaria de la raiz privada y de DPAPI).
$current=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
try{$sid=(New-Object Security.Principal.NTAccount($ServiceCredential.UserName)).Translate([Security.Principal.SecurityIdentifier]).Value}catch{throw 'La cuenta indicada no existe o no se puede resolver.'}
if($sid -ne $current){throw 'La cuenta del servicio debe ser la misma que ejecuta este script y custodia DPAPI de la instancia. Ejecuta Install-Service.ps1 con esa cuenta.'}
try{$null=Import-Clixml (Join-Path $Root 'config\secrets.clixml')}catch{throw 'Esta cuenta no descifra config\secrets.clixml. DPAPI requiere la identidad que inicializo la instancia.'}

# "Iniciar sesion como servicio": solo se comprueba (lectura); concederlo es decision de TI.
$policy=Join-Path $Root ('tmp\derechos-'+[Guid]::NewGuid().ToString('N')+'.inf')
$right=$false
try{
    & secedit.exe /export /areas USER_RIGHTS /cfg $policy *> $null
    $line=@(Get-Content -LiteralPath $policy -ErrorAction SilentlyContinue | Where-Object {$_ -like 'SeServiceLogonRight*'})
    if($line.Count -gt 0){$right=$line[0] -match [regex]::Escape('*'+$sid) -or $line[0] -match [regex]::Escape($ServiceCredential.UserName)}
}finally{if(Test-Path -LiteralPath $policy){Remove-Item -LiteralPath $policy}}

$serviceDir=Join-Path $Root 'service';$exe=Join-Path $serviceDir 'SigerService.exe'
if(Test-Path -LiteralPath $exe){throw 'Ya existe service\SigerService.exe. Revisa una instalacion previa; no se reemplaza.'}
if(-not $PSCmdlet.ShouldProcess("$app, $db",'Registrar servicios permanentes sin iniciarlos')){return}

# Host del servicio compilado aqui con el compilador de .NET Framework de Windows (sin binarios externos).
New-Item -ItemType Directory -Force -Path $serviceDir | Out-Null
Add-Type -TypeDefinition ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'SigerService.cs'))) -ReferencedAssemblies System.ServiceProcess -OutputAssembly $exe -OutputType ConsoleApplication
$hostHash=(Get-SigerFileHash $exe -Algorithm SHA256).Hash

# Motor propio: servicio de MySQL con su my.ini (LocalSystem; el datadir ya admite SYSTEM).
$ini=Join-Path $Root 'mysql\my.ini'
& (Join-Path $s.mysqlHome 'bin\mysqld.exe') --install $db "--defaults-file=$ini"
if($LASTEXITCODE -ne 0){throw 'No se registro el servicio del motor propio. Revisa el mensaje de mysqld.'}
Set-Service -Name $db -StartupType Automatic
& sc.exe failure $db reset= 86400 actions= restart/30000/restart/60000/restart/300000 | Out-Null

# Aplicacion: la contrasena de la cuenta pasa al administrador de servicios como objeto, nunca como argumento.
New-Service -Name $app -BinaryPathName "`"$exe`" `"$Root`"" -Credential $ServiceCredential -StartupType Automatic -DependsOn $db -DisplayName 'SIGER - Defensoria de la Ninez' -Description 'SIGER: Comunicaciones, Solicitudes, Requerimientos y Casos. Instancia aislada con MySQL propio.' | Out-Null
& sc.exe config $app start= delayed-auto | Out-Null
& sc.exe failure $app reset= 86400 actions= restart/30000/restart/60000/restart/300000 | Out-Null
& sc.exe failureflag $app 1 | Out-Null
if(-not [Diagnostics.EventLog]::SourceExists($app)){New-EventLog -LogName Application -Source $app}

$info=[PSCustomObject]@{serviceName=$app;mysqlServiceName=$db;account=$ServiceCredential.UserName;accountSid=$sid;hostSha256=$hostHash;registeredUtc=(Get-Date).ToUniversalTime().ToString('o')}
Save-InstanceJson $info (Join-Path $Root 'config\service.json')
Write-Host "Servicios $db y $app registrados con inicio automatico y recuperacion. No se inicio ninguno."
if(-not $right){Write-Warning 'La cuenta no figura con el derecho "Iniciar sesion como servicio". TI debe concederlo (directiva local o de dominio) antes de iniciar; este script no cambia directivas.'}
Write-Host 'Para pasar a operacion por servicio: Stop.ps1 -AlsoMySql (si la instancia corre a mano) y luego Start.ps1, que inicia el servicio y comprueba la salud.'
