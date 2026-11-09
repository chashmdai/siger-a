[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string]$Root='D:\SIGER-A',
    # Cuenta de servicio de SIGER: SIN privilegios administrativos, la misma que inicializo la instancia (DPAPI).
    [Parameter(Mandatory=$true)][pscredential]$ServiceCredential,
    # Concede a la cuenta el derecho "Iniciar sesion como servicio" en la directiva local (una directiva de dominio puede prevalecer).
    [switch]$ConcederInicioComoServicio
)
# Registro ELEVADO y unico de los servicios SIGERMySQL y SIGER. Lo ejecuta un administrador; los servicios corren con la
# cuenta de servicio, que no necesita ni recibe privilegios administrativos: solo el derecho de iniciar sesion como
# servicio y permiso para iniciar, detener y consultar estos dos servicios. NO inicia ningun servicio y no toca MySQL80,
# Tomcat, SQL Server, SMR, firewall, PATH ni JAVA_HOME. La contrasena de la cuenta pasa al sistema como objeto, nunca
# como argumento de proceso.
. (Join-Path $PSScriptRoot 'Common.ps1')
$me=[Security.Principal.WindowsIdentity]::GetCurrent()
if(-not([Security.Principal.WindowsPrincipal]$me).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Ejecuta en PowerShell elevada (administrador). No se registro nada.'}
$s=Read-Settings $Root
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
$app='SIGER';$db='SIGERMySQL'
if(Test-Path -LiteralPath (Join-Path $Root 'config\service.json')){throw 'Los servicios ya fueron registrados para esta instancia (config\service.json).'}
foreach($name in @($app,$db)){if(Get-Service -Name $name -ErrorAction SilentlyContinue){throw "Ya existe un servicio $name. No se sobrescribe ni se reutiliza."}}

# Identidad de ejecucion separada de la identidad que instala.
try{$sid=(New-Object Security.Principal.NTAccount($ServiceCredential.UserName)).Translate([Security.Principal.SecurityIdentifier]).Value}catch{throw 'La cuenta de servicio no existe o no se puede resolver.'}
if($sid -eq $me.User.Value){throw 'La cuenta de servicio debe ser distinta de la cuenta administrativa que instala.'}
$owner=if($s.PSObject.Properties['ownerSid']){[string]$s.ownerSid}else{(New-Object Security.Principal.NTAccount((Get-Acl -LiteralPath (Join-Path $Root 'config\secrets.clixml')).Owner)).Translate([Security.Principal.SecurityIdentifier]).Value}
if($owner -ne $sid){throw 'La instancia no fue inicializada por la cuenta de servicio indicada: su custodia DPAPI pertenece a otra identidad. Inicializa con Init-Server.ps1 ejecutado como la cuenta de servicio.'}

# Comprobacion COMO la cuenta de servicio (perfil cargado para DPAPI): sin privilegios administrativos y descifra la custodia.
$net=$ServiceCredential.GetNetworkCredential()
$info=New-Object Diagnostics.ProcessStartInfo
$info.FileName=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$info.Arguments="-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$(Join-Path $PSScriptRoot 'Test-ServiceIdentity.ps1')`" -Root `"$Root`""
$info.UserName=$net.UserName;$info.Domain=if($net.Domain){$net.Domain}else{'.'};$info.Password=$ServiceCredential.Password
$info.LoadUserProfile=$true;$info.UseShellExecute=$false;$info.RedirectStandardOutput=$true;$info.CreateNoWindow=$true;$info.WorkingDirectory=$Root
try{$probe=[Diagnostics.Process]::Start($info);$line=$probe.StandardOutput.ReadToEnd();$probe.WaitForExit()}catch{throw 'No se pudo ejecutar la comprobacion con la cuenta de servicio (clave incorrecta o cuenta sin inicio de sesion local).'}
try{$check=$line.Trim()|ConvertFrom-Json}catch{throw 'La comprobacion de la cuenta de servicio no respondio.'}
if($check.administrator){throw 'La cuenta de servicio pertenece a Administradores. SIGER debe ejecutarse con una cuenta sin privilegios administrativos.'}
if(-not $check.dpapi){throw 'La cuenta de servicio no descifra config\secrets.clixml. Inicializa la instancia con esa cuenta.'}
if(-not $check.instanceOwner -or $check.sid -ne $sid){throw 'La comprobacion no corresponde a la cuenta de servicio indicada.'}
# Defender configurado pero no utilizable por la cuenta: SIGER funciona, pero ningun original real podra liberarse.
if($check.PSObject.Properties['defender'] -and @('NO_CONFIGURADO','NO_APLICA','OPERATIVO') -notcontains [string]$check.defender){
    Write-Warning "Defender no es utilizable por la cuenta de servicio ($($check.defender)). Los originales permaneceran en cuarentena hasta resolverlo con TI; no se elevan privilegios."
}

# "Iniciar sesion como servicio": se lee siempre; se concede solo con la opcion explicita.
function Get-LogonRight([string]$Policy){
    & secedit.exe /export /areas USER_RIGHTS /cfg $Policy *> $null
    return @(Get-Content -LiteralPath $Policy -ErrorAction SilentlyContinue | Where-Object {$_ -like 'SeServiceLogonRight*'})
}
$policy=Join-Path $Root ('tmp\derechos-'+[Guid]::NewGuid().ToString('N')+'.inf');$database=[IO.Path]::ChangeExtension($policy,'.sdb')
$right=$false
try{
    $line=Get-LogonRight $policy
    $right=$line.Count -gt 0 -and $line[0] -match [regex]::Escape('*'+$sid)
}finally{foreach($f in @($policy,$database)){if(Test-Path -LiteralPath $f){Remove-Item -LiteralPath $f}}}

$serviceDir=Join-Path $Root 'service';$exe=Join-Path $serviceDir 'SigerService.exe'
if(Test-Path -LiteralPath $exe){throw 'Ya existe service\SigerService.exe. Revisa una instalacion previa; no se reemplaza.'}
if(-not $PSCmdlet.ShouldProcess("$app, $db ($($ServiceCredential.UserName))",'Registrar servicios permanentes sin iniciarlos')){return}

if(-not $right -and $ConcederInicioComoServicio){
    try{
        $line=Get-LogonRight $policy
        $entry=if($line.Count -gt 0){$line[0].TrimEnd()+",*$sid"}else{"SeServiceLogonRight = *$sid"}
        @('[Unicode]','Unicode=yes','[Version]','signature="$CHICAGO$"','Revision=1','[Privilege Rights]',$entry)|Set-Content -LiteralPath $policy -Encoding Unicode
        & secedit.exe /configure /db $database /cfg $policy /areas USER_RIGHTS *> $null
        if($LASTEXITCODE -ne 0){throw 'secedit no aplico el derecho.'}
        $right=(Get-LogonRight $policy)[0] -match [regex]::Escape('*'+$sid)
    }finally{foreach($f in @($policy,$database)){if(Test-Path -LiteralPath $f){Remove-Item -LiteralPath $f}}}
}

# Host del servicio compilado aqui con el compilador de .NET Framework de Windows (sin binarios externos).
New-Item -ItemType Directory -Force -Path $serviceDir | Out-Null
Add-Type -TypeDefinition ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'SigerService.cs'))) -ReferencedAssemblies System.ServiceProcess -OutputAssembly $exe -OutputType ConsoleApplication
$hostHash=(Get-SigerFileHash $exe -Algorithm SHA256).Hash

# Motor propio con su my.ini, ejecutado tambien con la cuenta de servicio (duena del datadir).
$ini=Join-Path $Root 'mysql\my.ini'
& (Join-Path $s.mysqlHome 'bin\mysqld.exe') --install $db "--defaults-file=$ini"
if($LASTEXITCODE -ne 0){throw 'No se registro el servicio del motor propio. Revisa el mensaje de mysqld.'}
$account=if($net.Domain){"$($net.Domain)\$($net.UserName)"}else{".\$($net.UserName)"}
$change=Invoke-CimMethod -InputObject (Get-CimInstance Win32_Service -Filter "Name='$db'") -MethodName Change -Arguments @{StartName=$account;StartPassword=$net.Password;StartMode='Automatic'}
$net=$null
if($change.ReturnValue -ne 0){throw "No se asigno la cuenta de servicio al motor propio (codigo $($change.ReturnValue))."}
& sc.exe failure $db reset= 86400 actions= restart/30000/restart/60000/restart/300000 | Out-Null

New-Service -Name $app -BinaryPathName "`"$exe`" `"$Root`"" -Credential $ServiceCredential -StartupType Automatic -DependsOn $db -DisplayName 'SIGER - Defensoria de la Ninez' -Description 'SIGER: Comunicaciones, Solicitudes, Requerimientos y Casos. Instancia aislada con MySQL propio.' | Out-Null
& sc.exe config $app start= delayed-auto | Out-Null
& sc.exe failure $app reset= 86400 actions= restart/30000/restart/60000/restart/300000 | Out-Null
& sc.exe failureflag $app 1 | Out-Null

# La cuenta de servicio opera sus dos servicios (iniciar, detener, consultar) sin ser administradora.
foreach($name in @($db,$app)){
    $sd=((& sc.exe sdshow $name)|Where-Object {$_.Trim()}) -join ''
    if($sd -notmatch '^D:'){throw "No se pudo leer el descriptor de seguridad de $name."}
    $ace="(A;;CCLCSWRPWPDTLOCRRC;;;$sid)"
    $new=if($sd.Contains('S:')){$sd.Insert($sd.IndexOf('S:'),$ace)}else{$sd+$ace}
    & sc.exe sdset $name $new | Out-Null
    if($LASTEXITCODE -ne 0){throw "No se concedio a la cuenta de servicio el control de $name."}
}
if(-not [Diagnostics.EventLog]::SourceExists($app)){New-EventLog -LogName Application -Source $app}

$record=[PSCustomObject]@{serviceName=$app;mysqlServiceName=$db;account=$account;accountSid=$sid;installedBy=$me.Name;hostSha256=$hostHash;registeredUtc=(Get-Date).ToUniversalTime().ToString('o')}
Save-InstanceJson $record (Join-Path $Root 'config\service.json')
Write-Host "Servicios $db y $app registrados con la cuenta $account (sin privilegios administrativos), inicio automatico y recuperacion. No se inicio ninguno."
if(-not $right){Write-Warning 'La cuenta no tiene el derecho "Iniciar sesion como servicio". Concedelo (TI o -ConcederInicioComoServicio) antes de iniciar; una directiva de dominio puede prevalecer sobre la local.'}
Write-Host 'Siguiente paso, COMO la cuenta de servicio: detener la instancia manual si corre (Stop.ps1 -AlsoMySql) y ejecutar Start.ps1, que inicia el servicio y comprueba la salud.'
