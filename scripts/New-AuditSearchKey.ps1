[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A')
# Genera la clave HMAC del buscador de personas y folios (0.5.0, G08): 32 bytes aleatorios en Base64, protegidos con
# DPAPI de la cuenta actual (Export-Clixml) en config\audit-search-key.clixml. Con ella la auditoria guarda la huella
# HMAC-SHA256 del termino buscado, nunca el termino. Debe ejecutarlo la cuenta del servicio (duena de DPAPI).
# No imprime la clave ni sobrescribe un archivo existente: reemplazarla impide comparar las huellas anteriores.
# Rige desde el siguiente inicio del servicio (Set-SigerServiceEnvironment la entrega al proceso).
. (Join-Path $PSScriptRoot 'Common.ps1')
$null=Read-Settings $Root
$target=Join-Path $Root 'config\audit-search-key.clixml'
if(Test-Path -LiteralPath $target){throw 'Ya existe config\audit-search-key.clixml. No se sobrescribe: reemplazar la clave impide comparar las huellas anteriores.'}
$bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
try{$rng.GetBytes($bytes);$plain=[Convert]::ToBase64String($bytes)}finally{$rng.Dispose();[Array]::Clear($bytes,0,$bytes.Length)}
if($PSCmdlet.ShouldProcess($target,'Proteger la clave del buscador con DPAPI')){
    (ConvertTo-SecureString -String $plain -AsPlainText -Force)|Export-Clixml -LiteralPath $target
    Require-PrivateFile $target
    Write-Host 'Clave del buscador -> config\audit-search-key.clixml (DPAPI de esta cuenta). Reinicia el servicio para que rija.'
}
$plain=$null
