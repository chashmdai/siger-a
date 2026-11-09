[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string]$Root='D:\SIGER-A',
    # AUTOMATICO: el servicio consume la cola del Formulario de forma permanente. MANUAL: no consume (estado inicial).
    [ValidateSet('Automatico','Manual')][string]$Formulario,
    # Declaracion expresa, exigida para AUTOMATICO: el consumidor del Formulario de SMR ya no consume la cola.
    [switch]$SmrDetenidoConfirmado,
    [ValidateSet('Graph','Desactivado')][string]$Correo
)
# Cambia el modo de las integraciones editando solo config\formulario-pull.json y config\correo-graph.json. Requiere
# reiniciar SIGER (Stop.ps1 y Start.ps1). No contacta el Formulario ni Graph y no muestra secretos. Ejecutar como la
# cuenta de servicio.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root;$config=Join-Path $Root 'config'
if(-not $Formulario -and -not $Correo){throw 'Indica -Formulario Automatico|Manual y/o -Correo Graph|Desactivado.'}
if($Formulario){
    $file=Join-Path $config 'formulario-pull.json'
    if(-not(Test-Path -LiteralPath $file)){throw 'Falta config\formulario-pull.json (Import-SecretPackage.ps1 lo crea).'}
    $pull=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    if($Formulario -eq 'Automatico'){
        Require-ProductionPull $s
        if(-not $SmrDetenidoConfirmado){throw 'Confirma con -SmrDetenidoConfirmado que el consumidor del Formulario de SMR fue detenido con autorizacion y ya no consume: dos consumidores se repartirian las entregas.'}
        # Si SMR esta instalado en esta misma maquina, su servicio debe estar detenido y deshabilitado.
        $smr=Get-CimInstance Win32_Service -Filter "Name='SMRRecepcion'" -ErrorAction SilentlyContinue
        if($smr -and ($smr.State -ne 'Stopped' -or $smr.StartMode -ne 'Disabled')){throw 'El servicio SMRRecepcion de este equipo sigue activo o con inicio habilitado. Detenlo y deshabilitalo con autorizacion antes de activar SIGER.'}
        # Sonda acreditada reciente con la misma configuracion y el release activo.
        $probe=Join-Path $Root 'run\formulario-sonda.json'
        if(-not(Test-Path -LiteralPath $probe)){throw 'Ejecuta antes Invoke-FormularioPull.ps1 -SoloComprobar y verifica que la sonda quede acreditada.'}
        $p=Get-Content -LiteralPath $probe -Raw|ConvertFrom-Json
        if(-not $p.ready -or ([datetime]::Parse($p.utc)).ToUniversalTime() -lt (Get-Date).ToUniversalTime().AddHours(-24) -or $p.origin -ne $pull.origin -or $p.jarSha256 -ne $s.jarSha256){throw 'La ultima sonda no esta acreditada, tiene mas de 24 horas o corresponde a otra configuracion o release. Repite Invoke-FormularioPull.ps1 -SoloComprobar.'}
    }
    $pull|Add-Member -NotePropertyName mode -NotePropertyValue $Formulario.ToUpperInvariant() -Force
    $pull|Add-Member -NotePropertyName consumidorUnicoConfirmado -NotePropertyValue ($Formulario -eq 'Automatico') -Force
    if($PSCmdlet.ShouldProcess($file,"Formulario en modo $Formulario")){
        Save-InstanceJson $pull $file
        $null=Get-FormularioPullConfig $s  # misma validacion que el arranque
        Write-Host "Formulario en modo $($Formulario.ToUpperInvariant()). Reinicia SIGER (Stop.ps1 y Start.ps1) para aplicarlo."
    }
}
if($Correo){
    $file=Join-Path $config 'correo-graph.json'
    if(-not(Test-Path -LiteralPath $file)){throw 'Falta config\correo-graph.json (Import-SecretPackage.ps1 lo crea).'}
    $mail=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    $mail|Add-Member -NotePropertyName mode -NotePropertyValue $(if($Correo -eq 'Graph'){'GRAPH'}else{'DESACTIVADO'}) -Force
    if($PSCmdlet.ShouldProcess($file,"Correo $Correo")){
        Save-InstanceJson $mail $file
        $null=Get-GraphMailConfig $s
        Write-Host "Correo en modo $($mail.mode). Reinicia SIGER para aplicarlo. Con GRAPH, solo los avisos nuevos generan correo."
    }
}
