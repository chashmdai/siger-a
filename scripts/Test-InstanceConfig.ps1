[CmdletBinding()]
param(
    # Paquete de Export-InstanceConfig.ps1.
    [Parameter(Mandatory=$true)][string]$Package,
    # Raiz de la instancia de la que se exporto (se compara con el disco y con su motor MySQL).
    [Parameter(Mandatory=$true)][string]$Root
)
# Verificacion independiente del paquete de configuracion (H06r de docs/PLAN_0_6_0.md, paso P0 de
# docs/DESPLIEGUE_VM.md). Se ejecuta como OTRA cuenta que la duena de la instancia (en la VM, el administrador en una
# consola elevada) y solo con la frase de paso: acredita que el paquete no depende de la DPAPI de la cuenta duena.
# En memoria y sin escribir nada: no crea archivos ni cambia permisos (ni takeown ni icacls); lo que no puede leer lo
# informa como no comparado. Comprueba:
#   - integridad (HMAC) y formato del paquete; huella de cada secreto y SHA-256 de cada archivo;
#   - cada archivo contra el disco (IGUAL, DISTINTO, FALTA o NO_LEGIBLE) y lo que esta en config\ y no en el paquete;
#   - cada .p12 abre con su clave del paquete y sus certificados (huella digital y vencimiento) son los registrados y
#     los del archivo del disco;
#   - la clave privada ACK corresponde a su clave publica (AckKeyCheck.java, Java de la instancia, por la entrada
#     estandar) y su keyId es valido e igual al de formulario-pull.json;
#   - SELECT 1 con cada contrasena de MySQL del paquete contra el motor propio (127.0.0.1, su puerto, TLS con la CA de
#     su datadir; contrasena en MYSQL_PWD del proceso hijo, nunca en argumentos ni archivos).
# Informa solo nombres de archivo, cifras y resultados. Codigo 0 solo si todo es CORRECTO; 2 con observaciones.
# Ejemplo: powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Rotacion-SIGER\scripts\Test-InstanceConfig.ps1' -Package 'D:\Rotacion-SIGER\validacion-config.sigerpkg' -Root 'D:\SIGER-VALIDACION'
. (Join-Path $PSScriptRoot 'ConfigPackage.ps1')
$me=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$raw=[IO.File]::ReadAllBytes(([IO.Path]::GetFullPath($Package)))
$text=(New-Object Text.UTF8Encoding($false)).GetString($raw)
$envelope=Read-ConfigEnvelope $text
$pass=Read-ConfigPassphrase
$open=$null
try{
    $open=Open-ConfigPackage $text $pass
    $pass=$null
    $payload=$open.Json|ConvertFrom-Json
    if(-not (Test-VerifierAccount ([string](Get-PackageField $payload 'ownerSid')) $me)){throw 'Ejecuta Test-InstanceConfig.ps1 como OTRA cuenta (por ejemplo el administrador): la verificacion acredita que el paquete no depende de la DPAPI de la cuenta duena. No se comprobo nada mas.'}
    $result=Invoke-ConfigVerification $payload $open.Crypto.Fp $Root (Join-Path $PSScriptRoot 'AckKeyCheck.java')
    $lines=Format-ConfigVerification $payload $result
}finally{if($open){Clear-ConfigCrypto $open.Crypto};$pass=$null;$payload=$null}
Write-Host "Paquete: $([IO.Path]::GetFullPath($Package)) (SHA-256 $(Get-Sha256Hex $raw),$($envelope.Iterations) iteraciones)."
foreach($l in $lines){Write-Host $l}
Write-Host 'No se mostraron secretos, no se escribio nada y no se cambiaron permisos.'
if(@($result.Problems).Count -gt 0){exit 2}
