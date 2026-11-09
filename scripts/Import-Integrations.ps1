[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string]$Root='D:\SIGER-A',
    # Instalacion de SMR de la que se reutilizan credenciales compatibles. Solo se LEE; no se detiene ni modifica SMR.
    [string]$SmrRoot='',
    [switch]$FormularioBearer,[switch]$AckIdentity,[switch]$FormularioTrust,[switch]$FormularioCa,
    # Secreto del cliente Graph: archivo existente (por ejemplo el custodiado para la prueba de Graph) o entrada privada.
    [string]$GraphSecretFile='',[switch]$GraphSecretPrompt,
    # Certificado HTTPS existente (PFX con clave privada) para reemplazar el autofirmado de localhost.
    [string]$HttpsPfx=''
)
# Reutiliza certificados, claves y credenciales existentes sin exponerlos: se descifran con DPAPI de la cuenta actual
# solo en memoria y se vuelven a proteger con DPAPI (Export-Clixml) en config\ de SIGER, que hereda su ACL privada.
# Nunca se imprimen ni se pasan como argumentos; nunca se sobrescribe un archivo existente (salvo el keystore HTTPS,
# del que se conserva copia). DPAPI CurrentUser: debe ejecutarlo la misma cuenta que custodia SMR y que ejecuta SIGER.
. (Join-Path $PSScriptRoot 'Common.ps1')
Add-Type -AssemblyName System.Security
$s=Read-Settings $Root;$config=Join-Path $Root 'config'
$me=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
function Target([string]$Name){$path=Join-Path $config $Name;if(Test-Path -LiteralPath $path){throw "Ya existe config\$Name. No se sobrescribe; retiralo de forma controlada si corresponde reemplazarlo."};return $path}
function Source([string]$Path){
    if(-not $SmrRoot){throw 'Indica -SmrRoot con la raiz de la instalacion de SMR.'}
    $full=Join-Path $SmrRoot $Path
    if(-not(Test-Path -LiteralPath $full -PathType Leaf)){throw "No existe $Path en la instalacion de SMR."}
    if((Get-Item -LiteralPath $full -Force).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'No se leen enlaces.'}
    return $full
}
function Read-SmrDpapi([string]$Path){
    # Formato de SMR: JSON UTF-8 protegido con ProtectedData (CurrentUser), sin entropia adicional.
    $bytes=$null
    try{$bytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($Path),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)}
    catch{throw 'Esta cuenta no descifra el secreto de SMR (DPAPI pertenece a otra identidad). No se importo.'}
    try{return ([Text.Encoding]::UTF8.GetString($bytes)|ConvertFrom-Json)}finally{[Array]::Clear($bytes,0,$bytes.Length)}
}
function Protect([string]$Plain){return ConvertTo-SecureString -String $Plain -AsPlainText -Force}
$done=@()

if($FormularioBearer){
    $target=Target 'formulario-pull-token.clixml'
    $value=Read-SmrDpapi (Source 'instances\recepcion\private\producer-token.dpapi')
    $token=[string]$value.token;$value=$null
    if($token -notmatch '^[A-Za-z0-9._~+/-]{32,400}=*$'){throw 'El Bearer de SMR no tiene el formato esperado. No se importo.'}
    if($PSCmdlet.ShouldProcess($target,'Proteger el Bearer del Formulario con DPAPI')){Protect $token|Export-Clixml -LiteralPath $target;Require-PrivateFile $target;$done+='Bearer del Formulario -> config\formulario-pull-token.clixml'}
    $token=$null
}
if($AckIdentity){
    # Misma identidad ACK que el Formulario ya confia para SMR: evita instalar otra clave en su lista de confianza.
    $target=Target 'formulario-ack-identity.clixml'
    $id=Read-SmrDpapi (Source 'secrets\ack-identity.dpapi')
    if($id.PSObject.Properties['ownerSid'] -and [string]$id.ownerSid -and [string]$id.ownerSid -ne $me){throw 'La identidad ACK de SMR pertenece a otra cuenta. No se importo.'}
    if([string]$id.keyId -notmatch '^[A-Za-z0-9_-]{1,64}$' -or ([string]$id.privatePem).Trim() -notmatch '^-----BEGIN PRIVATE KEY-----' -or ([string]$id.publicPem).Trim() -notmatch '^-----BEGIN PUBLIC KEY-----'){throw 'Identidad ACK de SMR con formato inesperado. No se importo.'}
    $publicHash=(New-Object Security.Cryptography.SHA256Managed).ComputeHash([Text.Encoding]::ASCII.GetBytes([string]$id.publicPem))
    $object=[PSCustomObject]@{KeyId=[string]$id.keyId;PrivatePem=(Protect ([string]$id.privatePem));PublicPem=[string]$id.publicPem;Source='SMR';ImportedUtc=(Get-Date).ToUniversalTime().ToString('o')}
    $keyId=[string]$id.keyId;$id=$null
    if($PSCmdlet.ShouldProcess($target,'Proteger la identidad ACK con DPAPI')){$object|Export-Clixml -LiteralPath $target;Require-PrivateFile $target;$done+="Identidad ACK keyId=$keyId (SHA-256 de la publica: $(([BitConverter]::ToString($publicHash)).Replace('-','').ToLowerInvariant())) -> config\formulario-ack-identity.clixml"}
    $object=$null
}
if($FormularioTrust){
    $target=Target 'formulario-trusted-keys.json';$source=Source 'config\formulario-signing-trust.json'
    $trust=Get-Content -LiteralPath $source -Raw|ConvertFrom-Json
    $names=@($trust.PSObject.Properties|ForEach-Object {$_.Name})
    if($names.Count -lt 1 -or $names.Count -gt 20){throw 'Archivo de confianza del Formulario con cantidad de claves no admitida.'}
    foreach($p in $trust.PSObject.Properties){if($p.Name -notmatch '^[A-Za-z0-9_-]{1,64}$' -or ([string]$p.Value).Trim() -notmatch '^-----BEGIN PUBLIC KEY-----'){throw 'Archivo de confianza del Formulario con formato inesperado.'}}
    if($PSCmdlet.ShouldProcess($target,'Copiar las claves publicas de custodia del Formulario')){Copy-Item -LiteralPath $source -Destination $target;$done+="Claves publicas de custodia ($($names -join ', ')) -> config\formulario-trusted-keys.json"}
}
if($FormularioCa){
    $target=Target 'formulario-ca.pem';$source=Source 'config\public-edge-root-ca.crt'
    if((Get-Content -LiteralPath $source -Raw) -notmatch '-----BEGIN CERTIFICATE-----'){throw 'El certificado de CA de SMR no esta en formato PEM. Conviertelo antes de importarlo.'}
    if($PSCmdlet.ShouldProcess($target,'Copiar el certificado de CA del origen del Formulario')){Copy-Item -LiteralPath $source -Destination $target;$done+='CA del origen -> config\formulario-ca.pem (usar "caFile":"formulario-ca.pem" en formulario-pull.json)'}
}
if($SmrRoot -and ($FormularioBearer -or $AckIdentity)){
    $instance=Join-Path $SmrRoot 'instances\recepcion\instance.json'
    if(Test-Path -LiteralPath $instance){$smr=Get-Content -LiteralPath $instance -Raw|ConvertFrom-Json;if($smr.PSObject.Properties['origin']){$done+="Origen configurado en SMR (referencia para formulario-pull.json): $($smr.origin)"}}
}
if($GraphSecretFile -or $GraphSecretPrompt){
    $target=Target 'graph-client-secret.clixml'
    if($GraphSecretFile){
        if(-not(Test-Path -LiteralPath $GraphSecretFile -PathType Leaf) -or (Get-Item -LiteralPath $GraphSecretFile).Length -gt 1024){throw 'Archivo de secreto Graph ausente o demasiado grande.'}
        $plain=([IO.File]::ReadAllText($GraphSecretFile)).Trim()
        if($plain.Length -lt 20 -or $plain.Length -gt 512 -or $plain -match '[\x00-\x1f]'){throw 'El archivo no contiene un secreto de cliente con formato valido.'}
        $secure=Protect $plain;$plain=$null
    }else{$secure=Read-Host 'Secreto del cliente Graph (entrada privada; no pegar en chats)' -AsSecureString}
    if($PSCmdlet.ShouldProcess($target,'Proteger el secreto del cliente Graph con DPAPI')){$secure|Export-Clixml -LiteralPath $target;Require-PrivateFile $target;$done+='Secreto del cliente Graph -> config\graph-client-secret.clixml (el archivo de origen no se modifico; retiralo de forma controlada si ya no se necesita)'}
    $secure=$null
}
if($HttpsPfx){
    # Se conserva la clave del keystore de la instancia (secrets.clixml): el PFX se importa con ella, sin cambiar secretos.
    $store=Join-Path $config 'https.p12'
    $pfxPassword=Read-Host 'Clave del PFX (entrada privada)' -AsSecureString
    $collection=New-Object Security.Cryptography.X509Certificates.X509Certificate2Collection
    $collection.Import((Resolve-Path -LiteralPath $HttpsPfx).Path,(Secret-Text $pfxPassword),[Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet)
    $keys=@($collection|Where-Object {$_.HasPrivateKey})
    if($keys.Count -ne 1){throw 'El PFX debe contener exactamente un certificado con clave privada.'}
    $cert=$keys[0]
    if($cert.NotBefore -gt (Get-Date) -or $cert.NotAfter -lt (Get-Date).AddDays(30)){throw 'Certificado no vigente o vence en menos de 30 dias.'}
    $secret=Import-Clixml (Join-Path $config 'secrets.clixml')
    $candidate=Join-Path $Root ('tmp\https-'+[Guid]::NewGuid().ToString('N')+'.p12')
    $src=Join-Path $Root ('tmp\pfx-'+[Guid]::NewGuid().ToString('N')+'.txt');$dst=Join-Path $Root ('tmp\p12-'+[Guid]::NewGuid().ToString('N')+'.txt')
    try{
        [IO.File]::WriteAllText($src,(Secret-Text $pfxPassword),[Text.Encoding]::ASCII);[IO.File]::WriteAllText($dst,(Secret-Text $secret.Tls),[Text.Encoding]::ASCII)
        if((Invoke-Native {& (Join-Path $s.javaHome 'bin\keytool.exe') -importkeystore -noprompt -srckeystore $HttpsPfx -srcstoretype PKCS12 '-srcstorepass:file' $src -destkeystore $candidate -deststoretype PKCS12 '-deststorepass:file' $dst} -Quiet) -ne 0){throw 'keytool no pudo importar el PFX.'}
    }finally{foreach($f in @($src,$dst)){if(Test-Path -LiteralPath $f){Remove-Item -LiteralPath $f}}}
    if($PSCmdlet.ShouldProcess($store,'Reemplazar el certificado HTTPS (se conserva el anterior)')){
        $previous=Join-Path $config ('https-anterior-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.p12')
        Move-Item -LiteralPath $store -Destination $previous;Move-Item -LiteralPath $candidate -Destination $store
        $done+="Certificado HTTPS $($cert.Subject) (huella $($cert.Thumbprint), vence $($cert.NotAfter.ToString('yyyy-MM-dd'))) -> config\https.p12; anterior en $(Split-Path $previous -Leaf). Reinicia SIGER para usarlo."
    }elseif(Test-Path -LiteralPath $candidate){Remove-Item -LiteralPath $candidate}
}
if($done.Count -eq 0){throw 'Indica que importar: -FormularioBearer, -AckIdentity, -FormularioTrust, -FormularioCa, -GraphSecretFile/-GraphSecretPrompt o -HttpsPfx.'}
$done|ForEach-Object {Write-Host $_}
Write-Host 'No se mostraron ni registraron secretos. SMR no fue detenido ni modificado.'
