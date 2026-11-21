[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string]$Root='D:\SIGER-A',
    # Buzon remitente con permiso Mail.Send de la aplicacion SIGER-Notificaciones (el de Export-SecretPackage.ps1).
    [string]$Remitente='siger@defensorianinez.cl',
    # Dominios de los destinatarios permitidos (allowedDomains de correo-graph.json).
    [string[]]$DominiosPermitidos=@('defensorianinez.cl')
)
# Instala SOLO la credencial de Microsoft Graph (0.6.0, H01) en una instancia existente, sin SMR, sin paquete de
# secretos y sin el bloque del Formulario. Ejecutar COMO la cuenta de servicio de la instancia (ownerSid de
# instance.json, duena de DPAPI): de lo contrario se rechaza. Pide Tenant ID y Client ID por consola y el secreto del
# cliente como entrada privada; ninguno va como argumento ni se muestra. Valida el formato con las reglas de
# GraphMail.java y deja:
#   config\graph-client-secret.clixml  secreto protegido con DPAPI de esta cuenta (ACL privada de config\);
#   config\correo-graph.json           mode DESACTIVADO: el servicio no envia correo hasta Set-Integration.ps1 -Correo Graph.
# No sobrescribe: si ya existe cualquiera de los dos archivos, no escribe nada (incluido el secreto que instala
# Import-Integrations.ps1 -GraphSecret*). No contacta Graph: la credencial se acredita con Test-GraphMail.ps1.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root;$config=Join-Path $Root 'config'
$me=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$owner=if($s.PSObject.Properties['ownerSid']){[string]$s.ownerSid}else{''}
if(-not $owner){throw 'instance.json no registra ownerSid (cuenta duena de DPAPI). No se instala la credencial.'}
if($owner -ne $me){throw 'Ejecuta este script como la cuenta de servicio de la instancia (ownerSid de instance.json): DPAPI protege el secreto solo para la cuenta que lo instala.'}
$mailFile=Join-Path $config 'correo-graph.json';$secretFile=Join-Path $config 'graph-client-secret.clixml'
# Ambos destinos se comprueban antes de pedir nada.
foreach($f in @($mailFile,$secretFile)){if(Test-Path -LiteralPath $f){throw "Ya existe config\$(Split-Path $f -Leaf). No se sobrescribe ni se instala nada; retira la credencial anterior de forma controlada si corresponde reemplazarla."}}
$domains=@($DominiosPermitidos|ForEach-Object {([string]$_).Trim().ToLowerInvariant()}|Where-Object {$_})
$sender=$Remitente.Trim()
$tenant=([string](Read-Host 'Tenant ID (GUID)')).Trim()
$client=([string](Read-Host 'Client ID de la aplicacion (GUID)')).Trim()
$secure=Read-Host 'Secreto del cliente Graph (entrada privada; no pegar en chats)' -AsSecureString
$plain=Secret-Text $secure
try{Assert-GraphCredential $tenant $client $sender $plain $domains}finally{$plain=$null}
if(-not $PSCmdlet.ShouldProcess($config,'Instalar la credencial Graph (DPAPI) con el correo DESACTIVADO')){return}
$secure|Export-Clixml -LiteralPath $secretFile
try{
    Require-PrivateFile $secretFile
    Save-InstanceJson ([PSCustomObject]@{mode='DESACTIVADO';tenant=$tenant;client=$client;sender=$sender;allowedDomains=$domains;link=''}) $mailFile
    # Misma lectura que usa Test-GraphMail.ps1; con DESACTIVADO, el servicio no la toma.
    $null=Get-GraphMailConfig $s -CualquierModo
}catch{
    # Sin instalacion parcial: si algo falla despues de escribir el secreto, se retira lo escrito.
    foreach($f in @($mailFile,$secretFile)){if(Test-Path -LiteralPath $f){Remove-Item -LiteralPath $f}}
    throw
}
$secure=$null
Write-Host 'Credencial Graph instalada en config\ (secreto con DPAPI de esta cuenta). Correo DESACTIVADO: el servicio no envia correo.'
Write-Host 'Siguiente paso: Test-GraphMail.ps1 -Modo Token y despues un solo -Modo Envio al destinatario fijo.'
