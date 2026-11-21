[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
param(
    [string]$Root='D:\SIGER-VALIDACION',
    [Parameter(Mandatory=$true)][string]$Motivo,
    [ValidateRange(0,100000)][int]$RecepcionesDeclaradas,
    [string]$Constancias='D:\SIGER-CONSTANCIAS')
# Cierra una instancia de VALIDACION SIN borrar nada (D11 de docs/PLAN_0_6_0.md): la detiene con su motor y la marca
# cerrada (config\piloto-cerrado.json) para que Start.ps1 y Service.ps1 no vuelvan a iniciarla. Base, documentos, logs y
# configuracion se conservan; la eliminacion es otro procedimiento (H03).
#
# Se ejecuta como la cuenta de servicio desde la carpeta del paquete 0.6.0, tambien sobre una validacion que sigue en
# 0.4.0 (esquema V10): todo lo que llama sale de $PSScriptRoot y el SQL usa solo tablas y columnas de V8/V9. Ejemplo:
#
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Close-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -RecepcionesDeclaradas 9 -Motivo 'Fin de la validacion: salida a produccion'
#
# Con -WhatIf solo muestra las cifras y los bloqueos, sin detener nada. Sin -WhatIf pide confirmacion
# (ConfirmImpact=High) antes de detener la instancia.
#
# Reglas (Get-ValidationClosureBlockers en Environment.ps1, con casos en Check-Scripts.ps1):
# - sin recepciones del Formulario ni COM externas se cierra como antes, sin exigir pausa ni declaracion;
# - con recepciones: -RecepcionesDeclaradas igual a las CONFIRMADA (la cantidad que el responsable declaro al pausar),
#   ninguna REGISTRADA ni ACK pendiente, pausa de FORMULARIO_PULL vigente (Auditoria y operacion) y ninguna recepcion ni
#   COM externa posterior a ella; las RECHAZADA (sin COM) se informan y bloquean solo si son posteriores a la pausa.
# Tras la confirmacion detiene SIGER, vuelve a contar con el motor aun activo y exige cifras identicas antes de detener
# el motor y escribir el marcador; si cambiaron, la instancia queda detenida y sin cerrar.
# El marcador y su copia en -Constancias (fuera de la raiz y del mysqlHome; un archivo nuevo por cierre, sin
# sobrescribir) llevan solo cifras, fecha, cuenta y motivo: ningun dato ciudadano ni secreto. Despues, un administrador
# retira los servicios con Uninstall-Service.ps1.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root
$purpose=Get-InstancePurpose $s
if($purpose -ne 'VALIDACION'){throw 'Solo se cierra una instancia de VALIDACION. La produccion nunca se cierra con este script.'}
$marker=Join-Path $Root 'config\piloto-cerrado.json'
if(Test-Path -LiteralPath $marker){throw 'La validacion ya esta cerrada.'}
if([string]::IsNullOrWhiteSpace($Motivo) -or $Motivo.Length -gt 500 -or $Motivo -match '[\x00-\x1F\x7F]'){throw 'El motivo es obligatorio, de hasta 500 caracteres y en una sola linea.'}
$declared=if($PSBoundParameters.ContainsKey('RecepcionesDeclaradas')){$RecepcionesDeclaradas}else{$null}

# Carpeta de constancias: ruta absoluta, fuera de la raiz y del mysqlHome (que la eliminacion de H03 no toca o borra).
function Inside([string]$Child,[string]$Parent){return ($Child -ieq $Parent -or $Child.StartsWith($Parent+'\',[StringComparison]::OrdinalIgnoreCase))}
if(-not [IO.Path]::IsPathRooted($Constancias)){throw 'La carpeta de constancias debe ser una ruta absoluta.'}
$constDir=[IO.Path]::GetFullPath($Constancias).TrimEnd('\')
foreach($other in @([IO.Path]::GetFullPath($Root).TrimEnd('\'),[IO.Path]::GetFullPath([string]$s.mysqlHome).TrimEnd('\'))){
    if((Inside $constDir $other) -or (Inside $other $constDir)){throw 'La carpeta de constancias debe quedar fuera de la raiz de la instancia y del motor MySQL.'}
}
if((Test-Path -LiteralPath $constDir) -and ((Get-Item -LiteralPath $constDir).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'La carpeta de constancias no puede ser un enlace.'}

$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml')
function Read-ClosureFigures {
    $client=MySql-ClientFile $s $secret 'owner'
    try{
        Require-OwnMySql $s $client
        $text=(Invoke-SigerSql $s $client $script:ValidationClosureSql $s.database | Out-String)
    }finally{Remove-SecretFile $client}
    return (ConvertTo-ValidationClosureFigures $text)
}
function Show-ClosureFigures($f){
    $pause=if($f.paused -eq 1){[DateTimeOffset]::FromUnixTimeMilliseconds($f.pauseMs).UtcDateTime.ToString('yyyy-MM-dd HH:mm:ss')+' UTC'}else{'no'}
    Write-Host ("Comunicaciones: $($f.communications) (externas: $($f.external)). Recepciones del Formulario: confirmadas $($f.confirmed), registradas $($f.registered), ACK pendientes $($f.ackPending), rechazadas $($f.rejected).")
    Write-Host ("Pausa vigente del Formulario: $pause. Posteriores a la pausa: $($f.afterPause) recepciones y $($f.externalAfterPause) COM externas. Declaradas: $(if($null -eq $declared){'sin declarar'}else{$declared}).")
}
$messages=@{
    PROPOSITO='la instancia no es de VALIDACION';
    SIN_DECLARACION='hay recepciones del Formulario: indica con -RecepcionesDeclaradas la cantidad que declaro el responsable al pausar';
    CANTIDAD_DISTINTA='las recepciones confirmadas no coinciden con la cantidad declarada';
    REGISTRADA='hay recepciones REGISTRADA aun no confirmadas al Formulario';
    ACK_PENDIENTE='hay ACK pendientes';
    ESTADO_DESCONOCIDO='hay recepciones en un estado desconocido';
    COM_EXTERNA_SIN_RECEPCION='hay Comunicaciones externas sin una recepcion del consumidor que las explique';
    SIN_PAUSA='el consumidor del Formulario no esta pausado (TI: Auditoria y operacion > Integraciones permanentes > Pausar, con motivo)';
    POSTERIOR_A_LA_PAUSA='hay recepciones o Comunicaciones externas en el instante de la pausa vigente o despues'}

& (Join-Path $PSScriptRoot 'Start-MySql.ps1') -Root $Root | Out-Null
Start-Sleep 2
$before=Read-ClosureFigures
Show-ClosureFigures $before
$blockers=@(Get-ValidationClosureBlockers $purpose $before $declared)
if($blockers.Count -gt 0){throw ('No se cierra la validacion (nada se detuvo ni se modifico): '+(($blockers|ForEach-Object{$messages[$_]}) -join '; ')+'. Revisa con el responsable antes de continuar.')}
if(-not $PSCmdlet.ShouldProcess($Root,'Detener SIGER y su motor y marcar cerrada la validacion (sin borrar datos)')){return}

# Constancias: crear la carpeta y comprobar que se puede escribir antes de detener nada.
New-Item -ItemType Directory -Force -Path $constDir | Out-Null
if((Get-Item -LiteralPath $constDir).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'La carpeta de constancias no puede ser un enlace.'}
$probe=Join-Path $constDir ('.prueba-'+[Guid]::NewGuid().ToString('N'))
[IO.File]::WriteAllText($probe,'',[Text.Encoding]::ASCII);[IO.File]::Delete($probe)

# Detener solo SIGER y volver a contar con el motor activo: ningun ciclo en curso puede quedar fuera de las cifras.
& (Join-Path $PSScriptRoot 'Stop.ps1') -Root $Root
$services=Get-SigerServices $s
if($services){if((Get-Service -Name $services.Info.serviceName).Status -ne 'Stopped'){throw 'El servicio SIGER no quedo detenido. La validacion no se cerro.'}}
elseif(App-Running $s){throw 'SIGER sigue activo. La validacion no se cerro.'}
$after=Read-ClosureFigures
if(($after|ConvertTo-Json -Compress) -cne ($before|ConvertTo-Json -Compress)){
    Show-ClosureFigures $after
    throw 'Las cifras cambiaron mientras SIGER se detenia. SIGER quedo detenido y la validacion NO se cerro: revisa con el responsable.'
}
& (Join-Path $PSScriptRoot 'Stop.ps1') -Root $Root -AlsoMySql

$now=(Get-Date).ToUniversalTime()
$record=[PSCustomObject][ordered]@{
    kind='CIERRE_VALIDACION';closedUtc=$now.ToString('o');closedBy=[Security.Principal.WindowsIdentity]::GetCurrent().Name;reason=$Motivo
    root=[string]$s.root;database=[string]$s.database;jarSha256=[string]$s.jarSha256;communications=$after.communications
    formulario=[PSCustomObject][ordered]@{declared=$declared;confirmed=$after.confirmed;rejected=$after.rejected;externalCommunications=$after.external
        pauseUtc=$(if($after.paused -eq 1){[DateTimeOffset]::FromUnixTimeMilliseconds($after.pauseMs).UtcDateTime.ToString('o')}else{$null})}}
Save-InstanceJson $record $marker
$copy=Join-Path $constDir ('cierre-validacion-'+$now.ToString('yyyyMMddTHHmmssZ')+'.json')
try{
    $stream=[IO.File]::Open($copy,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    try{$bytes=(New-Object Text.UTF8Encoding($false)).GetBytes(($record|ConvertTo-Json -Depth 8));$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
}catch{throw "La validacion quedo cerrada, pero no se pudo escribir la copia en $constDir. Copia a mano $marker a esa carpeta."}
Write-Host "Validacion cerrada: $($after.communications) Comunicaciones y $($after.confirmed) recepciones confirmadas del Formulario conservadas; la instancia no volvera a iniciarse."
Write-Host "Registro de cierre: $marker (copia: $copy)."
Write-Host 'Siguiente paso (administrador): Uninstall-Service.ps1 de esta raiz.'
