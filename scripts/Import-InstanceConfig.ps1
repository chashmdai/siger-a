[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
param(
    # Raiz de la instancia de la que se exporto el paquete (en la VM, D:\SIGER-VALIDACION). Se ejecuta COMO su cuenta duena.
    [Parameter(Mandatory=$true)][string]$Root,
    # Paquete de Export-InstanceConfig.ps1 (formato SIGER-CONFIG-1; tambien los de los scripts de d792507, gate H06r2).
    [Parameter(Mandatory=$true)][string]$Package
)
# Importacion del paquete portable de la configuracion (H06s de docs/PLAN_0_6_0.md, paso V0 de docs/DESPLIEGUE_VM.md),
# despues de restablecer la contrasena de la cuenta de servicio: su DPAPI anterior ya no descifra los clixml. Ejecutar
# COMO la cuenta duena (ownerSid de instance.json) en una sesion abierta DESPUES del restablecimiento (runas nuevo), con
# la instancia detenida (servicios, SIGER y motor). Pide la frase de paso una vez.
#   - Rechaza antes de la frase: otra cuenta, instancia activa, archivo que no es un paquete. Despues: paquete alterado o
#     frase incorrecta (INTEGRIDAD), contenido con hallazgos (incluidos P12_MAYUSCULAS y P12_CLAVE), paquete de otra
#     cuenta o de otra raiz.
#   - Por archivo: cada clixml se vuelve a proteger SIEMPRE con la DPAPI de esta sesion (REEMPLAZAR o RESTAURAR) y queda
#     solo para la cuenta, SYSTEM y Administradores; el resto (tambien los .p12, y uno sin clave conocida solo como bytes,
#     sin abrirlo) se compara por SHA-256: SIN_CAMBIO, RESTAURAR (falta) o REEMPLAZAR (difiere). Lo del disco que no esta
#     en el paquete no se toca.
#   - Lo anterior se conserva sin borrarlo en config\rotacion-<UTC>\anterior\, con importacion.json (rutas, acciones y
#     SHA-256; sin secretos). Repetirla es seguro: vuelve a proteger los clixml y restaura lo que falte.
#   - Al terminar vuelve a leer todo el disco con la DPAPI de esta sesion y lo compara con el paquete (huellas, SHA-256,
#     certificados de los .p12 conocidos, ACL privada e identidad ACK). MySQL lo acreditan despues Start.ps1 y Backup.ps1.
# Si esta sesion ya descifra los clixml del disco (al repetir una importacion), lo informa en una linea, sin pedir nada:
# el control de que un inicio de sesion nuevo los lee es Set-ServicePassword.ps1 ("descifra N de N"). Pide confirmacion
# (ConfirmImpact High); -WhatIf pide la frase, muestra el plan y no escribe. Nunca imprime secretos. Codigo 0 si la
# verificacion es CORRECTA; 2 con observaciones.
# Ejemplo: powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Import-InstanceConfig.ps1' -Root 'D:\SIGER-VALIDACION' -Package 'D:\Rotacion-SIGER\validacion-config.sigerpkg' -WhatIf
. (Join-Path $PSScriptRoot 'ConfigPackage.ps1')
$s=Read-Settings $Root
Assert-InstanceOwner $s 'Import-InstanceConfig.ps1'
$rootFull=[IO.Path]::GetFullPath($Root).TrimEnd('\')
$me=[Security.Principal.WindowsIdentity]::GetCurrent()
function Assert-InstanceStopped($Settings){
    $codes=@(Get-ImportRunBlockers (Get-InstanceRunFacts $Settings))
    if($codes.Count -gt 0){throw ('La instancia no esta detenida ('+($codes -join ', ')+'): deten SIGER y su motor (Stop.ps1 -AlsoMySql, o los servicios como administrador) y vuelve a importar. No se importo nada.')}
}
Assert-InstanceStopped $s
$pkgPath=[IO.Path]::GetFullPath($Package)
if($pkgPath.StartsWith($rootFull+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'El paquete no puede estar dentro de la raiz de la instancia. No se importo nada.'}
$raw=[IO.File]::ReadAllBytes($pkgPath)
$pkgSha=Get-Sha256Hex $raw
$text=(New-Object Text.UTF8Encoding($false)).GetString($raw)
$envelope=Read-ConfigEnvelope $text
Write-Host "Instancia $rootFull, cuenta $($me.Name). Paquete $pkgPath (SHA-256 $pkgSha, $($envelope.Iterations) iteraciones): compara este SHA-256 con el anotado al exportar."
$pass=Read-ConfigPassphrase
$open=$null
try{
    $open=Open-ConfigPackage $text $pass
    $pass=$null
    $payload=$open.Json|ConvertFrom-Json
    $check=Test-ConfigPayload $payload $open.Crypto.Fp
    $codes=@(Get-ImportPackageBlockers $payload $s $me.User.Value $rootFull $check.Findings)
    if($codes.Count -gt 0){throw ('El paquete no se puede importar en esta instancia ('+($codes -join ', ')+$(if(@($check.Findings).Count){': '+(@($check.Findings) -join ', ')})+'). No se importo nada.')}
    Write-Host "Paquete integro: creado $([string]$payload.createdUtc) en $([string]$payload.computer) por $([string]$payload.account); $($check.Files) archivos, $($check.Secrets) secretos con su huella."
    $plan=Get-ImportPlan $payload $rootFull
    foreach($l in (Format-ImportPlan $plan)){Write-Host $l}
    if($plan.OldSecretsDecrypt){Write-Host $script:ImportCurrentSecretsNote}
    if(-not $PSCmdlet.ShouldProcess($rootFull,'Restaurar la configuracion del paquete y volver a proteger sus secretos con la DPAPI de esta sesion')){return}
    # La frase y la confirmacion toman tiempo: se vuelve a comprobar que la instancia sigue detenida y el plan se rehace.
    Assert-InstanceStopped $s
    $plan=Get-ImportPlan $payload $rootFull
    $stamp=ConvertTo-PackageStamp (Get-Date)
    $info=[ordered]@{path=$pkgPath;sha256=$pkgSha;createdUtc=[string]$payload.createdUtc;computer=[string]$payload.computer;account=[string]$payload.account}
    $record=Invoke-ConfigImport $plan $rootFull $stamp $info
    Write-Host "Importacion $($record.estado): $(@($record.items|Where-Object {$_.estado -ceq 'APLICADO'}).Count) archivos aplicados, $(@($record.items|Where-Object {$_.estado -ceq 'SIN_CAMBIO'}).Count) sin cambio; anteriores conservados en config\rotacion-$stamp\anterior\ (registro importacion.json)."
    $result=Test-ImportedConfig $payload $open.Crypto.Fp $rootFull (Join-Path $PSScriptRoot 'AckKeyCheck.java')
    foreach($l in (Format-ImportedConfig $result)){Write-Host $l}
}finally{if($open){Clear-ConfigCrypto $open.Crypto};$pass=$null;$payload=$null}
Write-Host 'No se mostraron secretos. Siguiente paso: Set-ServicePassword.ps1 como administrador (cuadro de credenciales) y despues Start.ps1 como la cuenta.'
if(@($result.Findings).Count -gt 0){exit 2}
