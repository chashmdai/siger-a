[CmdletBinding()]
param(
    # Instalacion de SMR de origen. Solo se LEE; no se detiene ni modifica SMR.
    [string]$SmrRoot='C:\req\smr',
    # Archivo de salida (nuevo). Llevarlo a la VM por un medio controlado y borrarlo despues de importarlo.
    [Parameter(Mandatory=$true)][string]$Destination,
    # Agrega las credenciales de la aplicacion Graph SIGER-Notificaciones (se piden de forma privada).
    [switch]$IncluirGraph,
    [string]$GraphSender='siger@defensorianinez.cl'
)
# Paquete de traslado de secretos entre maquinas y cuentas Windows. Ejecutar en el equipo de SMR con la cuenta duena
# de su DPAPI. Lee el Bearer del Formulario, la identidad ACK Ed25519 (keyId incluido), la confianza de custodia y la CA
# de SMR; los descifra solo en memoria y los cifra con una frase de paso (PBKDF2-SHA256 300.000 iteraciones,
# AES-256-CBC y HMAC-SHA256). En la VM, Import-SecretPackage.ps1 los vuelve a proteger con DPAPI de la cuenta de
# servicio de SIGER. Nunca imprime secretos. Autonomo: no necesita otros scripts. Compatible con Windows PowerShell 5.1.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Security
if(Test-Path -LiteralPath $Destination){throw 'El archivo de destino ya existe. Indica uno nuevo.'}
function Plain([Security.SecureString]$s){$p=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s);try{return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($p)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($p)}}
function Source([string]$Relative){
    $full=Join-Path $SmrRoot $Relative
    if(-not(Test-Path -LiteralPath $full -PathType Leaf)){throw "No existe $Relative en la instalacion de SMR."}
    if((Get-Item -LiteralPath $full -Force).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'No se leen enlaces.'}
    return $full
}
function Read-SmrDpapi([string]$Relative){
    $bytes=$null
    try{$bytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Source $Relative)),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)}
    catch{throw "Esta cuenta no descifra $Relative (DPAPI de otra identidad). Ejecuta con la cuenta duena de SMR."}
    try{return ([Text.Encoding]::UTF8.GetString($bytes)|ConvertFrom-Json)}finally{[Array]::Clear($bytes,0,$bytes.Length)}
}

$instance=Get-Content -LiteralPath (Source 'instances\recepcion\instance.json') -Raw|ConvertFrom-Json
$origin=[string]$instance.origin
if($origin -notmatch '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/?$'){throw 'El origen configurado en SMR no es https://host.'}
$bearer=[string](Read-SmrDpapi 'instances\recepcion\private\producer-token.dpapi').token
if($bearer -notmatch '^[A-Za-z0-9._~+/-]{32,400}=*$'){throw 'El Bearer de SMR no tiene el formato esperado.'}
$ack=Read-SmrDpapi 'secrets\ack-identity.dpapi'
if([string]$ack.keyId -notmatch '^[A-Za-z0-9_-]{1,64}$' -or ([string]$ack.privatePem).Trim() -notmatch '^-----BEGIN PRIVATE KEY-----' -or ([string]$ack.publicPem).Trim() -notmatch '^-----BEGIN PUBLIC KEY-----'){throw 'Identidad ACK de SMR con formato inesperado.'}
# [IO.File] y no Get-Content: en PowerShell 5.1 ConvertTo-Json serializa como objeto las propiedades extra (PSPath...)
# de las cadenas de Get-Content, y la confianza y la CA no llegarian intactas.
$trust=[IO.File]::ReadAllText((Source 'config\formulario-signing-trust.json'))
$null=$trust|ConvertFrom-Json
$ca=[IO.File]::ReadAllText((Source 'config\public-edge-root-ca.crt'))
if($ca -notmatch '-----BEGIN CERTIFICATE-----'){throw 'La CA de SMR no esta en formato PEM.'}
$payload=[ordered]@{format='SIGER-SECRETOS-1';createdUtc=(Get-Date).ToUniversalTime().ToString('o');source=$env:COMPUTERNAME
    formulario=[ordered]@{origin=$origin.TrimEnd('/');bearer=$bearer;ack=[ordered]@{keyId=[string]$ack.keyId;privatePem=[string]$ack.privatePem;publicPem=[string]$ack.publicPem};trustJson=$trust;caPem=$ca}}
$bearer=$null;$keyId=[string]$ack.keyId;$ack=$null
if($IncluirGraph){
    $tenant=(Read-Host 'Tenant ID de SIGER-Notificaciones').Trim();$client=(Read-Host 'Client ID de SIGER-Notificaciones').Trim()
    $guid='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
    if($tenant -notmatch $guid -or $client -notmatch $guid){throw 'Tenant ID y Client ID deben ser GUID.'}
    $secret=Plain (Read-Host 'VALOR del secreto de cliente (no el Secret ID; entrada privada)' -AsSecureString)
    if($secret.Length -lt 20 -or $secret.Length -gt 512){throw 'El secreto de cliente no tiene un largo valido.'}
    $payload.graph=[ordered]@{tenant=$tenant;client=$client;sender=$GraphSender;secret=$secret};$secret=$null
}
$pass=Plain (Read-Host 'Frase de paso para el paquete (minimo 20 caracteres; entregala por otro canal)' -AsSecureString)
if($pass.Length -lt 20){throw 'La frase de paso debe tener al menos 20 caracteres.'}
if($pass -cne (Plain (Read-Host 'Repite la frase de paso' -AsSecureString))){throw 'Las frases de paso no coinciden.'}

$salt=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();$rng.GetBytes($salt)
$kdf=New-Object Security.Cryptography.Rfc2898DeriveBytes($pass,$salt,300000,[Security.Cryptography.HashAlgorithmName]::SHA256);$key=$kdf.GetBytes(64);$kdf.Dispose();$pass=$null
$plain=[Text.Encoding]::UTF8.GetBytes(($payload|ConvertTo-Json -Depth 6 -Compress));$payload=$null
$aes=[Security.Cryptography.Aes]::Create()
try{
    $aes.Key=[byte[]]$key[0..31];$aes.Mode='CBC';$aes.Padding='PKCS7';$aes.GenerateIV()
    $cipher=$aes.CreateEncryptor().TransformFinalBlock($plain,0,$plain.Length)
    $hmac=New-Object Security.Cryptography.HMACSHA256(,[byte[]]$key[32..63])
    $mac=$hmac.ComputeHash([byte[]]([Text.Encoding]::ASCII.GetBytes('SIGER-SECRETOS-1')+$salt+$aes.IV+$cipher));$hmac.Dispose()
    $package=[ordered]@{format='SIGER-SECRETOS-1';iterations=300000;salt=[Convert]::ToBase64String($salt);iv=[Convert]::ToBase64String($aes.IV);data=[Convert]::ToBase64String($cipher);mac=[Convert]::ToBase64String($mac)}
}finally{$aes.Dispose();[Array]::Clear($plain,0,$plain.Length);[Array]::Clear($key,0,$key.Length)}
[IO.File]::WriteAllText($Destination,($package|ConvertTo-Json -Compress),(New-Object Text.UTF8Encoding($false)))
# El archivo queda solo para la cuenta actual, SYSTEM y Administradores.
$acl=New-Object Security.AccessControl.FileSecurity;$acl.SetAccessRuleProtection($true,$false)
foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')){$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier($sid)),'FullControl','Allow')))}
Set-Acl -LiteralPath $Destination -AclObject $acl
$sha=[BitConverter]::ToString((New-Object Security.Cryptography.SHA256Managed).ComputeHash([IO.File]::ReadAllBytes($Destination))).Replace('-','').ToLowerInvariant()
Write-Host "Paquete cifrado: $Destination"
Write-Host "SHA-256 del paquete (verificar en la VM): $sha"
Write-Host "Contenido: Bearer del Formulario, identidad ACK keyId=$keyId, confianza de custodia, CA$(if($IncluirGraph){', credenciales Graph'})."
Write-Host 'No se mostraron secretos. SMR no fue detenido ni modificado.'
