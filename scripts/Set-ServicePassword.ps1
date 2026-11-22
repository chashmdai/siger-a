[CmdletBinding(SupportsShouldProcess=$true)]
param(
    # Raiz de la instancia cuyos servicios SIGER y SIGERMySQL se actualizan (en la VM, D:\SIGER-VALIDACION).
    [Parameter(Mandatory=$true)][string]$Root,
    # Cuenta de servicio con su contrasena NUEVA. Con -File se pasa el nombre ('DOMINIO\svc-siger') y Windows PowerShell
    # 5.1 pide la contrasena en el cuadro de credenciales (consola interactiva).
    [Parameter(Mandatory=$true)][pscredential][System.Management.Automation.Credential()]$ServiceCredential
)
# Actualizacion de la contrasena guardada de los servicios SIGERMySQL y SIGER sin reinstalarlos (H06s de
# docs/PLAN_0_6_0.md, paso V0 de docs/DESPLIEGUE_VM.md), despues de restablecer la contrasena de la cuenta de servicio
# e importar su configuracion (Import-InstanceConfig.ps1). Lo ejecuta un administrador en una PowerShell elevada.
#   - Exige los servicios registrados de esta instancia (config\service.json), los dos detenidos, y la cuenta indicada
#     igual a la registrada (service.json), a la duena de la instancia (ownerSid) y a la de ambos servicios.
#   - Antes de cambiar nada comprueba COMO la cuenta, en un inicio de sesion nuevo con la contrasena indicada
#     (Test-ServiceIdentity.ps1): sin privilegios administrativos y que descifra TODOS los clixml de config\. Si la
#     contrasena es incorrecta o la importacion falta, se niega sin cambiar nada.
#   - Cambia solo la contrasena (Win32_Service.Change con el mismo StartName), primero SIGERMySQL y despues SIGER, y
#     comprueba que la cuenta, el tipo de inicio y el descriptor de seguridad de cada uno no cambiaron. No inicia nada.
#   - No pide confirmacion (decision del responsable en H06s): con las comprobaciones correctas aplica el cambio
#     directamente; -WhatIf las ejecuta y muestra lo que haria sin cambiar nada.
#   - Repetirlo es seguro: vuelve a comprobar todo y fija la misma contrasena en ambos. Si falla con el primero ya
#     actualizado, se repite la misma orden antes de iniciar los servicios.
# La contrasena pasa al sistema como objeto, nunca como argumento de proceso; no se imprime.
# Ejemplo: powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Set-ServicePassword.ps1' -Root 'D:\SIGER-VALIDACION' -ServiceCredential 'DDN-SIGER\svc-siger'
. (Join-Path $PSScriptRoot 'Common.ps1')
$me=[Security.Principal.WindowsIdentity]::GetCurrent()
if(-not([Security.Principal.WindowsPrincipal]$me).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Ejecuta en PowerShell elevada (administrador). No se cambio nada.'}
$s=Read-Settings $Root
$svc=Get-SigerServices $s
if(-not $svc){throw 'No hay servicios registrados para esta instancia (config\service.json). No se cambio nada.'}
$names=@([string]$svc.Info.mysqlServiceName,[string]$svc.Info.serviceName)
function Get-ServicePasswordState([string]$Name){
    $c=Get-CimInstance Win32_Service -Filter "Name='$($Name.Replace("'","''"))'"
    if(-not $c){throw "No se encontro el servicio $Name. No se cambio nada."}
    $startSid='';try{$startSid=(New-Object Security.Principal.NTAccount((ConvertTo-ServiceAccountName ([string]$c.StartName) $env:COMPUTERNAME))).Translate([Security.Principal.SecurityIdentifier]).Value}catch{}
    $sd=((& sc.exe sdshow $Name)|Where-Object {$_.Trim()}) -join ''
    return [PSCustomObject]@{Name=$Name;State=[string]$c.State;StartName=[string]$c.StartName;StartMode=[string]$c.StartMode;Delayed=[string](Get-ObjectField $c 'DelayedAutoStart');StartSid=$startSid;Sddl=$sd;Cim=$c}
}
$sid='';try{$sid=(New-Object Security.Principal.NTAccount((ConvertTo-ServiceAccountName $ServiceCredential.UserName $env:COMPUTERNAME))).Translate([Security.Principal.SecurityIdentifier]).Value}catch{}
$before=@(foreach($n in $names){Get-ServicePasswordState $n})
$codes=@(Get-ServicePasswordBlockers ([PSCustomObject]@{Sid=$sid;AdminSid=$me.User.Value;OwnerSid=[string](Get-ObjectField $s 'ownerSid');RecordSid=[string](Get-ObjectField $svc.Info 'accountSid');Services=$before}))
if($codes.Count -gt 0){throw ('No se actualiza la contrasena de los servicios ('+($codes -join ', ')+'). Deben estar detenidos (Stop.ps1 -AlsoMySql) y la cuenta debe ser la de la instancia y de sus servicios. No se cambio nada.')}

$check=Invoke-ServiceIdentityCheck $Root $ServiceCredential
$codes=@(Get-ServiceIdentityBlockers $check $sid)
if($codes.Count -gt 0){
    $hint=if($codes -ccontains 'DPAPI'){" La cuenta descifra $([string](Get-ObjectField $check 'clixmlDecrypted')) de $([string](Get-ObjectField $check 'clixmlTotal')) clixml de config\ en un inicio de sesion nuevo: importa la configuracion con Import-InstanceConfig.ps1, como la cuenta y despues del restablecimiento, y repite esta orden."}else{''}
    throw ('La comprobacion como la cuenta de servicio no es apta ('+($codes -join ', ')+').'+$hint+' No se cambio nada.')
}
Write-Host "Cuenta $($ServiceCredential.UserName): contrasena valida, sin privilegios administrativos, duena de la instancia; descifra $($check.clixmlDecrypted) de $($check.clixmlTotal) clixml en un inicio de sesion nuevo."
if(-not $PSCmdlet.ShouldProcess("$($names -join ', ') ($($ServiceCredential.UserName))",'Actualizar la contrasena guardada de los servicios, sin iniciarlos')){return}

$net=$ServiceCredential.GetNetworkCredential()
$done=@()
try{
    foreach($b in $before){
        $r=Invoke-CimMethod -InputObject $b.Cim -MethodName Change -Arguments @{StartName=$b.StartName;StartPassword=$net.Password}
        if($r.ReturnValue -ne 0){throw "Windows no actualizo la contrasena de $($b.Name) (codigo $($r.ReturnValue))."}
        $a=Get-ServicePasswordState $b.Name
        if($a.StartName -cne $b.StartName -or $a.StartMode -cne $b.StartMode -or $a.Delayed -cne $b.Delayed -or $a.Sddl -cne $b.Sddl -or $a.State -cne 'Stopped'){throw "La configuracion de $($b.Name) cambio al actualizar la contrasena (cuenta, tipo de inicio, descriptor o estado). Revisalo con sc.exe qc y sc.exe sdshow $($b.Name)."}
        $done+=$b.Name
    }
}catch{
    $pending=@($names|Where-Object {$done -notcontains $_})
    throw ("$($_.Exception.Message) Actualizados: $(if($done.Count){$done -join ', '}else{'ninguno'}); pendientes: $($pending -join ', '). Repite esta misma orden (vuelve a comprobar todo y fija la misma contrasena en ambos) antes de iniciar los servicios.")
}finally{$net=$null}
Write-Host "Contrasena guardada actualizada en $($done -join ' y ') con la cuenta $($before[0].StartName); cuenta, tipo de inicio y descriptor sin cambios. No se inicio ningun servicio."
Write-Host 'Siguiente paso, COMO la cuenta de servicio en una sesion nueva: Start.ps1 y Estado.ps1.'
