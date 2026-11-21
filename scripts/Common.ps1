. (Join-Path $PSScriptRoot 'Hash.ps1')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Read-Settings([string]$Root) {
    $file=Join-Path $Root 'config\instance.json'
    if(-not(Test-Path $file)){throw 'Instancia no inicializada.'}
    $s=Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
    if($s.namespace -ne 'SIGER-A-2026' -or [IO.Path]::GetFullPath($Root).TrimEnd('\') -ine ([string]$s.root).TrimEnd('\')){throw 'Descriptor no corresponde a esta raiz.'}
    return $s
}
function Secret-Text($Value){return (New-Object Net.NetworkCredential('', $Value)).Password}
function Private-Directory([string]$Path) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
    if((Get-Item -LiteralPath $Path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'La raiz privada no puede ser un enlace.'}
    $acl=New-Object System.Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true,$false)
    $ids=@([Security.Principal.WindowsIdentity]::GetCurrent().User, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')), (New-Object Security.Principal.SecurityIdentifier('S-1-5-32-544')))
    foreach($sid in $ids){$rule=New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow');$acl.AddAccessRule($rule)}
    $current=Get-Acl -LiteralPath $Path; $signature={param($d) @($d.Access | ForEach-Object { "{0}|{1}|{2}|{3}|{4}|{5}" -f $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value,[int]$_.FileSystemRights,[int]$_.AccessControlType,[int]$_.InheritanceFlags,[int]$_.PropagationFlags,$_.IsInherited } | Sort-Object) -join ";" }; if($current.AreAccessRulesProtected -and ((& $signature $current) -ceq (& $signature $acl))){return}; Set-Acl -LiteralPath $Path -AclObject $acl
}
function Require-PrivateFile([string]$Path) {
    # Credenciales y claves: archivo real (no enlace) accesible solo por la cuenta actual, SYSTEM y Administradores.
    if(-not(Test-Path -LiteralPath $Path -PathType Leaf)){throw "Falta el archivo privado $(Split-Path $Path -Leaf)."}
    if((Get-Item -LiteralPath $Path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Un archivo privado no puede ser un enlace.'}
    $allowed=@([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')
    foreach($rule in (Get-Acl -LiteralPath $Path).Access){
        if($rule.AccessControlType -ne 'Allow'){continue}
        try{$sid=$rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value}catch{$sid=''}
        if($allowed -notcontains $sid){throw "Permisos demasiado amplios en $(Split-Path $Path -Leaf). Restringe el acceso antes de continuar."}
    }
}
function Free-Port([int]$Port) {
    if(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue){throw "Puerto $Port ocupado. No se detiene ningun proceso ajeno."}
}
function Random-Secret {
    $bytes=New-Object byte[] 24;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()};return ([Convert]::ToBase64String($bytes).Replace('+','A').Replace('/','z').TrimEnd('=')+'a9!')
}
function Own-Process([int]$ProcessId,[string]$Exe,[string]$Marker) {
    $p=Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
    if(-not $p){return $null}
    if($p.ExecutablePath -ine $Exe -or -not $p.CommandLine -or $p.CommandLine.IndexOf($Marker,[StringComparison]::OrdinalIgnoreCase) -lt 0){throw 'PID no coincide con el ejecutable y configuracion propios. No se toca.'}
    return $p
}
function MySql-ClientFile($Settings,$Secrets,[string]$Account) {
    $path=Join-Path $Settings.root ('tmp\client-'+[Guid]::NewGuid().ToString('N')+'.cnf')
    $user=if($Account -eq 'backup'){'siger_a_backup'}else{'siger_a_owner'}
    $password=if($Account -eq 'backup'){Secret-Text $Secrets.Backup}else{Secret-Text $Secrets.Owner}
    $ca=(Join-Path $Settings.root 'mysql\data\ca.pem').Replace('\','/')
    $lines=@('[client]',"user=$user","password=$password",'host=127.0.0.1',"port=$($Settings.mysqlPort)",'protocol=TCP','ssl-mode=VERIFY_CA',"ssl-ca=$ca")
    [IO.File]::WriteAllLines($path,$lines,[Text.Encoding]::ASCII);return $path
}
# Borra un archivo temporal con credenciales (client-*.cnf, claves de keytool, un .p12 candidato). No usa Remove-Item:
# con -WhatIf del script que llama (o de uno que lo invoco) $WhatIfPreference se propaga y Remove-Item no borra, y con
# -Confirm pregunta y una respuesta negativa deja el archivo. Falla si el archivo sigue en disco.
# Check-Scripts.ps1 lo comprueba con -WhatIf y rechaza un Remove-Item nuevo sobre estas variables.
function Remove-SecretFile([string]$Path) {
    if([string]::IsNullOrEmpty($Path)){return}
    if([IO.File]::Exists($Path)){[IO.File]::SetAttributes($Path,[IO.FileAttributes]::Normal);[IO.File]::Delete($Path)}
    if([IO.File]::Exists($Path)){throw "No se pudo borrar un archivo temporal con credenciales ($(Split-Path $Path -Leaf)). Borralo a mano antes de continuar."}
}
function Require-Java25([string]$JavaDirectory){
    if(-not(Test-Path (Join-Path $JavaDirectory 'bin\java.exe'))){throw 'No existe java.exe portable.'}
    $v=(& (Join-Path $JavaDirectory 'bin\java.exe') --version | Out-String)
    if($v -notmatch '(?m)^(openjdk|java) 25[.\s]'){throw 'Runtime debe ser Java 25.'}
}
# Proceso de un servicio de la instancia cuando WMI oculta su ruta y sus argumentos: a la cuenta de servicio sin
# elevacion, Win32_Process no entrega ExecutablePath ni CommandLine de los procesos que lanzo el Administrador de
# servicios (otra sesion de inicio), aunque sean de la misma cuenta. Se acredita entonces por la cadena hasta el servicio
# registrado en config\service.json: servicio en ejecucion, su cuenta (accountSid), su PathName exacto y el proceso hijo
# con su nombre. APP: java.exe -> powershell.exe (Service.ps1) -> SigerService.exe. MYSQL: mysqld.exe -> servicio.
# Regla pura (casos en Check-Scripts.ps1); devuelve las condiciones que fallan, vacio = acreditado.
function Get-ServiceProcessBlockers($Facts){
    $codes=@()
    $svc=$Facts.Service;$p=$Facts.Process
    if(-not $svc){return @('SIN_SERVICIO')}
    if([string]$svc.State -cne 'Running' -or [int]$svc.ProcessId -le 0){$codes+='SERVICIO_DETENIDO'}
    if(-not $Facts.ServiceSid -or [string]$Facts.ServiceSid -cne [string]$Facts.ExpectedSid){$codes+='CUENTA'}
    if(-not $Facts.ExpectedPath -or ([string]$svc.PathName).Replace('"','').Trim() -ine [string]$Facts.ExpectedPath){$codes+='RUTA_SERVICIO'}
    $child=if($Facts.Kind -ceq 'APP'){'java.exe'}elseif($Facts.Kind -ceq 'MYSQL'){'mysqld.exe'}else{''}
    if(-not $child){return @('TIPO')}
    if(-not $p -or [string]$p.Name -ine $child){$codes+='EJECUTABLE'}
    if($Facts.Kind -ceq 'APP'){
        $h=$Facts.Host
        if(-not $p -or -not $h -or [int]$h.ProcessId -ne [int]$p.ParentProcessId -or [string]$h.Name -ine 'powershell.exe' -or [int]$h.ParentProcessId -ne [int]$svc.ProcessId){$codes+='CADENA'}
    }elseif(-not $p -or [int]$p.ParentProcessId -ne [int]$svc.ProcessId){$codes+='CADENA'}
    return $codes
}
function Get-ServiceProcessFacts($Settings,$Process,[string]$Kind){
    $facts=[PSCustomObject]@{Kind=$Kind;Process=$Process;Host=$null;Service=$null;ServiceSid='';ExpectedSid='';ExpectedPath=''}
    $services=Get-SigerServices $Settings
    if(-not $services){return $facts}
    $info=$services.Info
    $name=if($Kind -ceq 'APP'){[string]$info.serviceName}else{[string]$info.mysqlServiceName}
    $facts.Service=Get-CimInstance Win32_Service -Filter "Name='$($name.Replace("'","''"))'" -ErrorAction SilentlyContinue
    if($facts.Service){try{$facts.ServiceSid=(New-Object Security.Principal.NTAccount([string]$facts.Service.StartName)).Translate([Security.Principal.SecurityIdentifier]).Value}catch{}}
    $facts.ExpectedSid=[string]$info.accountSid
    $facts.ExpectedPath=if($Kind -ceq 'APP'){"$(Join-Path $Settings.root 'service\SigerService.exe') $($Settings.root)"}else{"$(Join-Path $Settings.mysqlHome 'bin\mysqld.exe') --defaults-file=$(Join-Path $Settings.root 'mysql\my.ini') $name"}
    if($Kind -ceq 'APP' -and $Process){$facts.Host=Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$Process.ParentProcessId)" -ErrorAction SilentlyContinue}
    return $facts
}
# Decision de App-Running (regla pura; casos y mutaciones en Check-Scripts.ps1): sin proceso, no corre; con ruta y
# argumentos visibles, deben ser los propios; si WMI los oculta, solo la cadena del servicio lo acredita. Ante cualquier
# duda lanza el error: nunca responde "no esta corriendo" por defecto (Backup, Restore-Check, Migrate, Update y
# Recover-Ti dependen de esa respuesta para actuar).
function Test-AppProcess($Process,[string]$Exe,[string]$Marker,[scriptblock]$ServiceBlockers){
    if(-not $Process){return $false}
    if($Process.ExecutablePath -and $Process.CommandLine){
        if([string]$Process.ExecutablePath -ine $Exe -or ([string]$Process.CommandLine).IndexOf($Marker,[StringComparison]::OrdinalIgnoreCase) -lt 0){throw 'PID no coincide con el ejecutable y configuracion propios. No se toca.'}
        return $true
    }
    $codes=@(& $ServiceBlockers)
    if($codes.Count -gt 0){throw ('PID no coincide con el ejecutable y configuracion propios, y no se acredito por el servicio Windows ('+($codes -join ',')+'). No se toca.')}
    return $true
}
function App-Running($Settings) {
    $pidfile=Join-Path $Settings.root 'run\app.pid'
    if(-not(Test-Path $pidfile)){return $false}
    $processId=[int](Get-Content $pidfile -Raw).Trim()
    $p=Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    return (Test-AppProcess $p (Join-Path $Settings.javaHome 'bin\java.exe') $Settings.activeJar {Get-ServiceProcessBlockers (Get-ServiceProcessFacts $Settings $p 'APP')})
}

# Windows PowerShell 5.1 convierte la salida de error de un programa nativo en un error terminante cuando se redirige
# (o cuando PowerShell corre con su salida redirigida) y $ErrorActionPreference es Stop. keytool, mysql, mysqldump y
# secedit escriben avisos normales en stderr (y mysql un error mientras el motor aun arranca). Aqui se ejecutan con
# Continue y se decide solo por el codigo de salida. -Quiet descarta la salida.
function Invoke-Native([scriptblock]$Command,[switch]$Quiet) {
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{
        if($Quiet){& $Command *> $null}else{& $Command 2>&1 | ForEach-Object {Write-Host ([string]$_)}}
        return $LASTEXITCODE
    }finally{$ErrorActionPreference=$old}
}
function Invoke-SigerSql($Settings,[string]$Client,[string]$Sql,[string]$Database='') {
    $arguments=@("--defaults-file=$Client",'--batch','--raw','--skip-column-names','--default-character-set=utf8mb4')
    if($Database){if($Database -notmatch '^siger_a_[a-z0-9_]{1,40}$'){throw 'Esquema ajeno.'};$arguments+="--database=$Database"}
    $arguments+=@('-e',$Sql)
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$result=& (Join-Path $Settings.mysqlHome 'bin\mysql.exe') @arguments 2>$null}finally{$ErrorActionPreference=$old}
    if($LASTEXITCODE -ne 0){throw 'Consulta del motor propio fallida. Revisa el error local sin publicar secretos.'}
    return $result
}
function Require-OwnMySql($Settings,[string]$Client) {
    $pidfile=Join-Path $Settings.root 'mysql\data\siger-a.pid'
    if(-not(Test-Path -LiteralPath $pidfile -PathType Leaf)){throw 'No se identifica el proceso MySQL propio. No se interviene otro motor.'}
    $dbpid=0
    if(-not [int]::TryParse((Get-Content -LiteralPath $pidfile -Raw).Trim(),[ref]$dbpid) -or $dbpid -le 0){throw 'PID del MySQL propio invalido.'}
    $exe=Join-Path $Settings.mysqlHome 'bin\mysqld.exe'
    $ini=Join-Path $Settings.root 'mysql\my.ini'
    $p=Get-CimInstance Win32_Process -Filter "ProcessId=$dbpid" -ErrorAction SilentlyContinue
    if(-not $p){throw 'MySQL propio no esta activo.'}
    if($p.ExecutablePath -and $p.CommandLine){
        $null=Own-Process $dbpid $exe $ini
    }else{
        # En la cuenta de servicio, WMI puede ocultar ruta y argumentos del proceso hijo. Solo se admite si el servicio,
        # su identidad y su configuracion coinciden con config\service.json y con el PID hijo del archivo propio.
        $codes=@(Get-ServiceProcessBlockers (Get-ServiceProcessFacts $Settings $p 'MYSQL'))
        if($codes.Count -gt 0){throw ('No se acredito el MySQL propio mediante el servicio Windows ('+($codes -join ',')+').')}
    }
    $port=(Invoke-SigerSql $Settings $Client 'SELECT @@port' | Out-String).Trim()
    $dir=(Invoke-SigerSql $Settings $Client 'SELECT @@datadir' | Out-String).Trim().Replace('/','\').TrimEnd('\')
    if($port -ne [string]$Settings.mysqlPort -or $dir -ine (Join-Path $Settings.root 'mysql\data').TrimEnd('\')){throw 'El motor conectado no corresponde a esta instancia.'}
}
function Test-SigerBackup([string]$Directory) {
    $Directory=(Resolve-Path -LiteralPath $Directory).Path.TrimEnd('\')
    $meta=Get-Content -LiteralPath (Join-Path $Directory 'backup.json') -Raw | ConvertFrom-Json
    if($meta.namespace -ne 'SIGER-A-2026' -or -not $meta.PSObject.Properties['complete'] -or -not $meta.complete){throw 'No es un respaldo completo con manifiesto (0.3.1 o posterior). Genera uno nuevo.'}
    $hashFile=Join-Path $Directory 'hashes.csv'
    if((Get-SigerFileHash $hashFile -Algorithm SHA256).Hash -ine $meta.manifestSha256){throw 'Manifiesto de respaldo alterado.'}
    $hashes=@(Import-Csv -LiteralPath $hashFile)
    if($hashes.Count -lt 2){throw 'Manifiesto incompleto.'}
    foreach($item in $hashes){
        if([IO.Path]::IsPathRooted($item.Path) -or $item.Path -match '(^|[\\/])\.\.([\\/]|$)' -or $item.Path.Contains(':')){throw 'Ruta no valida en respaldo.'}
        $path=[IO.Path]::GetFullPath((Join-Path $Directory $item.Path))
        if(-not $path.StartsWith($Directory+'\',[StringComparison]::OrdinalIgnoreCase) -or -not(Test-Path -LiteralPath $path -PathType Leaf)){throw 'Archivo de respaldo ausente o fuera de la raiz.'}
        if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Enlace no admitido en respaldo.'}
        if((Get-SigerFileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $item.SHA256){throw "Integridad fallida: $($item.Path)"}
    }
    return $meta
}
function Save-InstanceJson($Value,[string]$Path) {
    $temp=$Path+'.new'
    [IO.File]::WriteAllText($temp,($Value | ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
    if(Test-Path -LiteralPath $Path){[IO.File]::Replace($temp,$Path,($Path+'.bak-atomic-'+[guid]::NewGuid().ToString('N')))}else{[IO.File]::Move($temp,$Path)}
}
function Invoke-SigerHealth($Settings,$Secrets) {
    if(-not('SigerLocalHealth' -as [type])){Add-Type -Path (Join-Path $PSScriptRoot 'HealthProbe.cs')}
    $password=Secret-Text $Secrets.Tls
    return ([SigerLocalHealth]::Read("https://localhost:$($Settings.webPort)/api/health",(Join-Path $Settings.root 'config\https.p12'),$password) | ConvertFrom-Json)
}

# Servicios Windows de la instancia (Install-Service.ps1). Sin config\service.json la instancia se opera con Start/Stop.
function Get-SigerServices($Settings) {
    $file=Join-Path $Settings.root 'config\service.json'
    if(-not(Test-Path -LiteralPath $file -PathType Leaf)){return $null}
    $info=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    $app=Get-Service -Name ([string]$info.serviceName) -ErrorAction SilentlyContinue
    $db=Get-Service -Name ([string]$info.mysqlServiceName) -ErrorAction SilentlyContinue
    if(-not $app -or -not $db){throw 'config\service.json existe pero los servicios no estan registrados. Revisa la instalacion; no se inicia por otra via.'}
    foreach($pair in @(@($info.serviceName,$Settings.root),@($info.mysqlServiceName,(Join-Path $Settings.root 'mysql\my.ini')))){
        $cim=Get-CimInstance Win32_Service -Filter "Name='$($pair[0])'"
        if(-not $cim -or $cim.PathName.IndexOf([string]$pair[1],[StringComparison]::OrdinalIgnoreCase) -lt 0){throw "El servicio $($pair[0]) registrado no corresponde a esta instancia. No se interviene."}
    }
    return [PSCustomObject]@{App=$app;MySql=$db;Info=$info}
}
