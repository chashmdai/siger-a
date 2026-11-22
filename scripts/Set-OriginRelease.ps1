[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
param(
    [Parameter(Mandatory=$true)][string]$Root,
    # Ultimo dia en que rige el modo (aaaa-mm-dd, hora de Chile): hasta las 24:00 de ese dia. A lo mas 90 dias despues de hoy.
    [string]$Hasta,
    # Fundamento (1 a 500 caracteres, una sola linea): la decision del responsable y su constancia institucional.
    [string]$Motivo,
    # Retira el modo: al configurar un motor antivirus admitido (config\antivirus.json) o al terminar la emergencia.
    [switch]$Desactivar
)
# Liberacion por analisis en origen de los originales del Formulario (D17 de docs/PLAN_0_6_0.md, gate H04c; modo de
# emergencia mientras la instancia no tenga un motor antivirus admitido). Ejecutar COMO la cuenta de servicio de la
# instancia (ownerSid de instance.json). Escribe config\liberacion-origen.json con la lista cerrada de campos que valida
# SIGER al arrancar (OriginRelease.java): identificador nuevo, instante de activacion, termino (00:00 de Chile del dia
# siguiente a -Hasta, en UTC), cuenta de Windows y motivo. Rige desde el proximo inicio del servicio (Stop.ps1 y
# Start.ps1) y SIGER lo audita al arrancar; al vencer, vuelve la cuarentena sin reiniciar y TI ve el aviso. Con un archivo
# ilegible o con un motor configurado SIGER no libera nada (falla cerrado). Solo libera originales del Formulario: los
# adjuntos subidos en SIGER siguen en cuarentena. Volver a ejecutarlo reemplaza el modo vigente (renovacion), previa
# confirmacion. -Desactivar renombra el archivo a liberacion-origen.retirado-<UTC>.json (no borra nada).
# Ejemplo:
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\SIGER\scripts\Set-OriginRelease.ps1' -Root 'D:\SIGER' -Hasta '2027-03-01' -Motivo 'Decision del responsable del 2026-11-21 (D17)'
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root;$config=Join-Path $Root 'config'
$me=[Security.Principal.WindowsIdentity]::GetCurrent()
$owner=if($s.PSObject.Properties['ownerSid']){[string]$s.ownerSid}else{''}
if(-not $owner){throw 'instance.json no registra ownerSid (cuenta de servicio). No se cambia el modo.'}
if($owner -ne $me.User.Value){throw 'Ejecuta este script como la cuenta de servicio de la instancia (ownerSid de instance.json).'}
$file=Join-Path $config 'liberacion-origen.json'
$previous=$null
if(Test-Path -LiteralPath $file -PathType Leaf){try{$previous=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json}catch{$previous=$null}}
$previousText=if($previous -and $previous.PSObject.Properties['until']){" (registro vigente con termino $([string]$previous.until) UTC)"}else{''}
if($Desactivar){
    if($Hasta -or $Motivo){throw '-Desactivar no lleva -Hasta ni -Motivo.'}
    if(-not(Test-Path -LiteralPath $file)){Write-Host 'El modo no esta activado (no existe config\liberacion-origen.json). No se cambia nada.';return}
    $target=Join-Path $config ('liberacion-origen.retirado-'+(Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')+'.json')
    if(Test-Path -LiteralPath $target){throw 'Ya existe un registro retirado con la misma hora. Repite en un momento.'}
    if(-not $PSCmdlet.ShouldProcess($file,"Desactivar la liberacion por analisis en origen$previousText; desde el proximo inicio los originales del Formulario vuelven a quedar en cuarentena")){return}
    Move-Item -LiteralPath $file -Destination $target
    Write-Host "Modo desactivado: el registro queda como config\$(Split-Path $target -Leaf). Reinicia SIGER (Stop.ps1 y Start.ps1) para que rija."
    return
}
if(Test-Path -LiteralPath (Join-Path $config 'antivirus.json')){throw 'Hay un motor antivirus configurado (config\antivirus.json): rige el analisis local y el modo de emergencia no se activa.'}
$record=New-OriginReleaseRecord $Hasta $Motivo $me.Name ((Get-Date).ToUniversalTime())
$action=if(Test-Path -LiteralPath $file){"Reemplazar el modo vigente$previousText por uno que rige hasta el fin del $Hasta (hora de Chile)"}else{"Activar la liberacion por analisis en origen de los originales del Formulario hasta el fin del $Hasta (hora de Chile)"}
if(-not $PSCmdlet.ShouldProcess($file,$action)){return}
Save-InstanceJson $record $file
Write-Host "Modo de emergencia D17 registrado (id $($record.id), termino $($record.until) UTC). Rige desde el proximo inicio de SIGER (Stop.ps1 y Start.ps1); verifica el aviso en Auditoria y operacion."
