[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-VALIDACION',[Parameter(Mandatory=$true)][string]$Motivo)
# Cierra el piloto de validacion SIN borrar nada: comprueba que la instancia nunca recibio entregas reales del
# Formulario (no hay ACK reales que conservar), la detiene con su motor y la marca cerrada para que no vuelva a
# iniciarse. Base, documentos, logs y configuracion se conservan como evidencia del piloto. Ejecutar como la cuenta de
# servicio; despues un administrador retira el registro de servicios con Uninstall-Service.ps1.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root
if((Get-InstancePurpose $s) -ne 'VALIDACION'){throw 'Solo se cierra una instancia de VALIDACION. La produccion nunca se cierra con este script.'}
$marker=Join-Path $Root 'config\piloto-cerrado.json'
if(Test-Path -LiteralPath $marker){throw 'El piloto ya esta cerrado.'}
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml')
& (Join-Path $PSScriptRoot 'Start-MySql.ps1') -Root $Root | Out-Null
$client=MySql-ClientFile $s $secret 'owner'
try{
    Start-Sleep 2;Require-OwnMySql $s $client
    $received=[int]((Invoke-SigerSql $s $client 'SELECT COUNT(*) FROM formulario_reception' $s.database)|Out-String).Trim()
    $external=[int]((Invoke-SigerSql $s $client "SELECT COUNT(*) FROM communication WHERE external_source IS NOT NULL" $s.database)|Out-String).Trim()
    $coms=[int]((Invoke-SigerSql $s $client 'SELECT COUNT(*) FROM communication' $s.database)|Out-String).Trim()
}finally{Remove-Item -LiteralPath $client}
# Una entrega del Formulario registrada (con o sin ACK) es un dato ciudadano real: nunca se descarta.
if($received -gt 0 -or $external -gt 0){throw "La instancia contiene $received recepciones y $external Comunicaciones externas del Formulario. No se cierra ni se descarta: conservala y revisa con el responsable antes de continuar."}
if(-not $PSCmdlet.ShouldProcess($Root,'Detener y marcar cerrada la instancia de validacion (sin borrar datos)')){return}
& (Join-Path $PSScriptRoot 'Stop.ps1') -Root $Root -AlsoMySql
Save-InstanceJson ([PSCustomObject]@{closedUtc=(Get-Date).ToUniversalTime().ToString('o');closedBy=[Security.Principal.WindowsIdentity]::GetCurrent().Name;reason=$Motivo;communications=$coms;formularioReceptions=$received;jarSha256=$s.jarSha256}) $marker
Write-Host "Piloto cerrado: $coms Comunicaciones de prueba conservadas como evidencia; ninguna entrega real del Formulario. La instancia no volvera a iniciarse."
Write-Host 'Siguiente paso (administrador): Uninstall-Service.ps1 de esta raiz, y luego inicializar la instancia de produccion con el mismo release.'
