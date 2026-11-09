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
    Set-Acl -LiteralPath $Path -AclObject $acl
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
function Require-Java25([string]$JavaDirectory){
    if(-not(Test-Path (Join-Path $JavaDirectory 'bin\java.exe'))){throw 'No existe java.exe portable.'}
    $v=(& (Join-Path $JavaDirectory 'bin\java.exe') --version | Out-String)
    if($v -notmatch '(?m)^(openjdk|java) 25[.\s]'){throw 'Runtime debe ser Java 25.'}
}
function App-Running($Settings) {
    $pidfile=Join-Path $Settings.root 'run\app.pid'
    if(-not(Test-Path $pidfile)){return $false}
    $processId=[int](Get-Content $pidfile -Raw).Trim()
    return $null -ne (Own-Process $processId (Join-Path $Settings.javaHome 'bin\java.exe') $Settings.activeJar)
}

function Invoke-SigerSql($Settings,[string]$Client,[string]$Sql,[string]$Database='') {
    $arguments=@("--defaults-file=$Client",'--batch','--raw','--skip-column-names','--default-character-set=utf8mb4')
    if($Database){if($Database -notmatch '^siger_a_[a-z0-9_]{1,40}$'){throw 'Esquema ajeno.'};$arguments+="--database=$Database"}
    $arguments+=@('-e',$Sql)
    $result=& (Join-Path $Settings.mysqlHome 'bin\mysql.exe') @arguments
    if($LASTEXITCODE -ne 0){throw 'Consulta del motor propio fallida. Revisa el error local sin publicar secretos.'}
    return $result
}
function Require-OwnMySql($Settings,[string]$Client) {
    $pidfile=Join-Path $Settings.root 'mysql\data\siger-a.pid'
    if(-not(Test-Path $pidfile)){throw 'No se identifica el proceso MySQL propio. No se interviene otro motor.'}
    $p=Own-Process ([int](Get-Content $pidfile -Raw).Trim()) (Join-Path $Settings.mysqlHome 'bin\mysqld.exe') (Join-Path $Settings.root 'mysql\my.ini')
    if(-not $p){throw 'MySQL propio no esta activo.'}
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
    if(Test-Path -LiteralPath $Path){[IO.File]::Replace($temp,$Path,$null)}else{[IO.File]::Move($temp,$Path)}
}
function Invoke-SigerHealth($Settings,$Secrets) {
    if(-not('SigerLocalHealth' -as [type])){Add-Type -Path (Join-Path $PSScriptRoot 'HealthProbe.cs')}
    $password=Secret-Text $Secrets.Tls
    return ([SigerLocalHealth]::Read("https://localhost:$($Settings.webPort)/api/health",(Join-Path $Settings.root 'config\https.p12'),$password) | ConvertFrom-Json)
}
