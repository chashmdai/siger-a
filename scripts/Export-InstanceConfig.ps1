[CmdletBinding(SupportsShouldProcess=$true)]
param(
    # Raiz de la instancia (por ejemplo la validacion 0.4.0 de la VM). Se ejecuta COMO su cuenta duena (ownerSid).
    [Parameter(Mandatory=$true)][string]$Root,
    # Archivo nuevo .sigerpkg en una carpeta existente fuera de la raiz (en la VM, D:\Rotacion-SIGER). No se sobrescribe.
    [Parameter(Mandatory=$true)][string]$Destination
)
# Exportacion portable de la configuracion de una instancia (H06r de docs/PLAN_0_6_0.md, paso P0 de
# docs/DESPLIEGUE_VM.md), antes de restablecer la contrasena de la cuenta de servicio: restablecerla deja sin descifrar
# lo protegido con su DPAPI. Ejecutar COMO la cuenta duena (ownerSid de instance.json), por ejemplo desde una PowerShell
# ya abierta con esa cuenta; no necesita privilegios administrativos ni detener la instancia, y no cambia nada en ella.
# Incluye todo config\ (salvo las carpetas rotacion-<UTC>\) y mysql\my.ini: cada *.clixml se descifra solo en memoria
# y se cifra con la frase de paso; los .p12 deben abrir con su clave de secrets.clixml; cada archivo lleva su SHA-256 y
# cada valor de clixml su huella (formato SIGER-CONFIG-1, ConfigPackage.ps1). La frase se escribe dos veces a mano. El
# paquete queda solo para la cuenta, SYSTEM y Administradores, y se verifica leyendolo de nuevo antes de terminar. Nunca
# imprime secretos. -WhatIf muestra el inventario sin pedir la frase ni escribir. Funciona sobre una instancia 0.4.0 con
# estos scripts copiados aparte (Export-InstanceConfig.ps1, Test-InstanceConfig.ps1, ConfigPackage.ps1,
# AckKeyCheck.java, Common.ps1 y Hash.ps1). La verificacion independiente es Test-InstanceConfig.ps1, como otra cuenta.
# Ejemplo: powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Rotacion-SIGER\scripts\Export-InstanceConfig.ps1' -Root 'D:\SIGER-VALIDACION' -Destination 'D:\Rotacion-SIGER\validacion-config.sigerpkg' -WhatIf
. (Join-Path $PSScriptRoot 'ConfigPackage.ps1')
$s=Read-Settings $Root
Assert-InstanceOwner $s 'Export-InstanceConfig.ps1'
$rootFull=[IO.Path]::GetFullPath($Root).TrimEnd('\')
$dest=[IO.Path]::GetFullPath($Destination)
$parent=Split-Path $dest -Parent
$codes=@(Get-ExportDestinationBlockers $dest $rootFull ([IO.File]::Exists($dest) -or [IO.Directory]::Exists($dest)) ([IO.Directory]::Exists($parent)))
if($codes.Count -gt 0){throw ('Destino no admitido ('+($codes -join ', ')+'): indica un archivo nuevo .sigerpkg en una carpeta existente, fuera de la raiz. No se escribio nada.')}
$inv=Get-InstanceConfigInventory $rootFull
$byKind=@($inv.Items|Group-Object Kind|ForEach-Object {"$($_.Count) $($_.Name)"}) -join ', '
Write-Host "Instancia $rootFull ($([string](Get-PackageField $s 'mode')) $([string](Get-PackageField $s 'purpose'))), cuenta $([Security.Principal.WindowsIdentity]::GetCurrent().Name)."
Write-Host "Inventario: $(@($inv.Items).Count) archivos ($byKind), $($inv.Total) bytes$(if(@($inv.Omitted).Count){'; se omiten '+(@($inv.Omitted) -join ', ')})."
foreach($i in $inv.Items){Write-Host "  $($i.Kind.PadRight(7)) $($i.Rel) ($($i.Size) bytes)"}
if(@($inv.Problems).Count -gt 0){throw ('La configuracion no se puede exportar ('+(@($inv.Problems) -join ', ')+'). No se escribio nada.')}
if(-not $PSCmdlet.ShouldProcess($dest,'Crear el paquete portable de la configuracion (SIGER-CONFIG-1)')){return}

$pass=Read-ConfigPassphrase -Twice
$crypto=$null
try{
    $crypto=New-ConfigCrypto $pass $null $script:ConfigMinIterations
    $pass=$null
    $payload=New-InstanceConfigPayload $s $inv $crypto.Fp
    $json=$payload|ConvertTo-Json -Depth 10 -Compress
    $payload=$null
    $check=Test-ConfigPayload ($json|ConvertFrom-Json) $crypto.Fp
    if(@($check.Findings).Count -gt 0){throw ('El contenido no paso su propia revision ('+(@($check.Findings) -join ', ')+'). No se escribio nada.')}
    $text=Protect-ConfigPayload $crypto $json
    # Archivo nuevo (CreateNew: nunca sobrescribe) y despues solo para la cuenta, SYSTEM y Administradores.
    $stream=New-Object IO.FileStream($dest,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$bytes=(New-Object Text.UTF8Encoding($false)).GetBytes($text);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
    $acl=New-Object Security.AccessControl.FileSecurity;$acl.SetAccessRuleProtection($true,$false)
    foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')){$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier($sid)),'FullControl','Allow')))}
    Set-Acl -LiteralPath $dest -AclObject $acl
    # Autoverificacion: se lee el archivo escrito y se descifra con las mismas claves.
    $read=[IO.File]::ReadAllText($dest)
    $again=Unprotect-ConfigEnvelope (Read-ConfigEnvelope $read) $crypto
    if($again -cne $json){throw "El paquete escrito no coincide al leerlo de nuevo: no lo uses ($dest)."}
    $again=$null;$json=$null
    $sha=Get-Sha256Hex ([IO.File]::ReadAllBytes($dest))
}finally{Clear-ConfigCrypto $crypto;$pass=$null}
Write-Host "Paquete escrito y leido de nuevo: $dest"
Write-Host "Contenido: $($check.Files) archivos con SHA-256, $($check.Secrets) secretos con su huella, $(@($check.P12).Count) almacen(es) .p12 que abren con su clave."
Write-Host "SHA-256 del paquete (anotalo para comparar despues): $sha"
Write-Host 'No se mostraron secretos ni se cambio la instancia. Siguiente paso: Test-InstanceConfig.ps1 como OTRA cuenta (administrador), con la frase de paso.'
