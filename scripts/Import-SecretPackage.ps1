[CmdletBinding()]
param(
    [string]$Root='D:\SIGER-A',
    [Parameter(Mandatory=$true)][string]$Package,
    # Codigo del ambito que recibe las Comunicaciones del Formulario (debe existir en la estructura inicial).
    [Parameter(Mandatory=$true)][string]$Ambito,
    # Borra el paquete despues de importarlo correctamente.
    [switch]$EliminarPaquete
)
# Importa en la VM el paquete de Export-SecretPackage.ps1, COMO la cuenta de servicio de SIGER: verifica integridad
# (HMAC) y frase de paso, y vuelve a proteger cada secreto con DPAPI de esta cuenta en config\ (ACL privada). Deja
# formulario-pull.json en modo MANUAL (no consume) y correo-graph.json en DESACTIVADO (no envia). No contacta el
# Formulario ni Graph y no imprime secretos. No sobrescribe archivos existentes.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root;$config=Join-Path $Root 'config'
if($Ambito -notmatch '^[A-Z0-9_-]{2,48}$'){throw 'Codigo de ambito invalido.'}
function Plain([Security.SecureString]$v){$p=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($v);try{return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($p)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($p)}}
function Target([string]$Name){$path=Join-Path $config $Name;if(Test-Path -LiteralPath $path){throw "Ya existe config\$Name. No se sobrescribe; retiralo de forma controlada si corresponde reemplazarlo."};return $path}

$p=Get-Content -LiteralPath $Package -Raw|ConvertFrom-Json
if($p.format -ne 'SIGER-SECRETOS-1' -or [int]$p.iterations -lt 300000){throw 'El archivo no es un paquete de secretos de SIGER.'}
$salt=[Convert]::FromBase64String($p.salt);$iv=[Convert]::FromBase64String($p.iv);$cipher=[Convert]::FromBase64String($p.data);$mac=[Convert]::FromBase64String($p.mac)
$pass=Plain (Read-Host 'Frase de paso del paquete' -AsSecureString)
$kdf=New-Object Security.Cryptography.Rfc2898DeriveBytes($pass,$salt,[int]$p.iterations,[Security.Cryptography.HashAlgorithmName]::SHA256);$key=$kdf.GetBytes(64);$kdf.Dispose();$pass=$null
$plain=$null
try{
    $hmac=New-Object Security.Cryptography.HMACSHA256(,[byte[]]$key[32..63])
    $expected=$hmac.ComputeHash([byte[]]([Text.Encoding]::ASCII.GetBytes('SIGER-SECRETOS-1')+$salt+$iv+$cipher));$hmac.Dispose()
    $diff=$expected.Length -bxor $mac.Length;for($i=0;$i -lt [Math]::Min($expected.Length,$mac.Length);$i++){$diff=$diff -bor ($expected[$i] -bxor $mac[$i])}
    if($diff -ne 0){throw 'Frase de paso incorrecta o paquete alterado. No se importo nada.'}
    $aes=[Security.Cryptography.Aes]::Create();try{$aes.Key=[byte[]]$key[0..31];$aes.IV=$iv;$aes.Mode='CBC';$aes.Padding='PKCS7';$plain=$aes.CreateDecryptor().TransformFinalBlock($cipher,0,$cipher.Length)}finally{$aes.Dispose()}
    $data=[Text.Encoding]::UTF8.GetString($plain)|ConvertFrom-Json
}finally{[Array]::Clear($key,0,$key.Length);if($plain){[Array]::Clear($plain,0,$plain.Length)}}
$f=$data.formulario
if([string]$f.origin -notmatch '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?$' -or [string]$f.bearer -notmatch '^[A-Za-z0-9._~+/-]{32,400}=*$' -or [string]$f.ack.keyId -notmatch '^[A-Za-z0-9_-]{1,64}$'){throw 'Contenido del paquete con formato inesperado. No se importo nada.'}
$trust=[string]$f.trustJson|ConvertFrom-Json
foreach($k in $trust.PSObject.Properties){if($k.Name -notmatch '^[A-Za-z0-9_-]{1,64}$' -or ([string]$k.Value).Trim() -notmatch '^-----BEGIN PUBLIC KEY-----'){throw 'Confianza de custodia con formato inesperado.'}}
if([string]$f.caPem -notmatch '-----BEGIN CERTIFICATE-----'){throw 'CA con formato inesperado.'}

# Todos los destinos se comprueban antes de escribir el primero.
$files=@{token=Target 'formulario-pull-token.clixml';identity=Target 'formulario-ack-identity.clixml';trust=Target 'formulario-trusted-keys.json';ca=Target 'formulario-ca.pem';pull=Target 'formulario-pull.json'}
if($data.PSObject.Properties['graph']){$files.secret=Target 'graph-client-secret.clixml';$files.mail=Target 'correo-graph.json'}
$utf8=New-Object Text.UTF8Encoding($false)
ConvertTo-SecureString -String ([string]$f.bearer) -AsPlainText -Force|Export-Clixml -LiteralPath $files.token
[PSCustomObject]@{KeyId=[string]$f.ack.keyId;PrivatePem=(ConvertTo-SecureString -String ([string]$f.ack.privatePem) -AsPlainText -Force);PublicPem=[string]$f.ack.publicPem;Source="SMR $($data.source)";ImportedUtc=(Get-Date).ToUniversalTime().ToString('o')}|Export-Clixml -LiteralPath $files.identity
[IO.File]::WriteAllText($files.trust,[string]$f.trustJson,$utf8)
[IO.File]::WriteAllText($files.ca,[string]$f.caPem,$utf8)
Save-InstanceJson ([PSCustomObject]@{origin=[string]$f.origin;scope=$Ambito;ackKeyId=[string]$f.ack.keyId;caFile='formulario-ca.pem';mode='MANUAL';consumidorUnicoConfirmado=$false}) $files.pull
if($files.ContainsKey('secret')){
    $g=$data.graph
    ConvertTo-SecureString -String ([string]$g.secret) -AsPlainText -Force|Export-Clixml -LiteralPath $files.secret
    Save-InstanceJson ([PSCustomObject]@{mode='DESACTIVADO';tenant=[string]$g.tenant;client=[string]$g.client;sender=[string]$g.sender;allowedDomains=@('defensorianinez.cl');link=''}) $files.mail
}
$data=$null;$f=$null
foreach($x in @($files.token,$files.identity)){Require-PrivateFile $x}
$null=Get-FormularioPullConfig $s
Write-Host "Importado en config\ con DPAPI de esta cuenta: Bearer, identidad ACK keyId=$((Import-Clixml -LiteralPath $files.identity).KeyId), confianza de custodia y CA$(if($files.ContainsKey('secret')){', credenciales Graph (correo DESACTIVADO)'})."
Write-Host "Formulario en modo MANUAL: no consume. Siguiente paso: Invoke-FormularioPull.ps1 -SoloComprobar."
if($EliminarPaquete){Remove-Item -LiteralPath $Package;Write-Host 'Paquete eliminado.'}else{Write-Warning 'Borra el paquete de traslado de forma controlada; ya no se necesita.'}
