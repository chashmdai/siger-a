[CmdletBinding()]
param(
    [string]$Root='D:\SIGER-A',
    # Interrumpe el servicio varias veces: ejecutar solo en una ventana sin usuarios.
    [switch]$VentanaAutorizada,
    # Incluye la detencion y recuperacion del motor propio (SIGERMySQL), que detiene tambien a SIGER.
    [switch]$IncluirMySql,
    [int]$WaitSeconds=240
)
# Prueba de arranque, parada ordenada y recuperacion de los servicios SIGER/SIGERMySQL de ESTA instancia. Se ejecuta
# como la cuenta de servicio (tiene permiso para iniciar y detener sus servicios y descifra DPAPI para la sonda HTTPS).
# No borra datos ni modifica configuracion; no toca otros servicios. Resultado en logs\prueba-servicio-<fecha>.json.
. (Join-Path $PSScriptRoot 'Common.ps1')
if(-not $VentanaAutorizada){throw 'Confirma con -VentanaAutorizada que no hay usuarios operando: la prueba detiene y reinicia SIGER.'}
$s=Read-Settings $Root
$services=Get-SigerServices $s
if(-not $services){throw 'No hay servicios registrados para esta instancia (Install-Service.ps1).'}
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml')
$app=[string]$services.Info.serviceName;$db=[string]$services.Info.mysqlServiceName
$steps=New-Object System.Collections.ArrayList
function Step([string]$Name,[bool]$Ok,[string]$Detail){$null=$steps.Add([PSCustomObject]@{paso=$Name;ok=$Ok;detalle=$Detail;utc=(Get-Date).ToUniversalTime().ToString('o')});Write-Host ("{0,-34} {1} {2}" -f $Name,$(if($Ok){'OK   '}else{'FALLA'}),$Detail)}
function Healthy{try{$h=Invoke-SigerHealth $s $secret;return $h.status -eq 'UP'}catch{return $false}}
function Wait-Healthy([int]$Seconds){$t=[Diagnostics.Stopwatch]::StartNew();while($t.Elapsed.TotalSeconds -lt $Seconds){if(Healthy){return [int]$t.Elapsed.TotalSeconds};Start-Sleep 2};return -1}
function AppPid{$f=Join-Path $Root 'run\app.pid';if(Test-Path -LiteralPath $f){return [int](Get-Content -LiteralPath $f -Raw).Trim()};return $null}
function Lock-Holder{
    $client=MySql-ClientFile $s $secret 'owner'
    try{return ((Invoke-SigerSql $s $client 'SELECT IFNULL(holder,'''') FROM formulario_consumer_lock WHERE id=1' $s.database)|Out-String).Trim()}finally{Remove-Item -LiteralPath $client}
}
$log=Join-Path $Root 'logs\siger.log'

# 1. Arranque.
if((Get-Service $app).Status -ne 'Running'){Start-Service -Name $app}
$t=Wait-Healthy $WaitSeconds
Step 'arranque' ($t -ge 0) "salud en $t s"
$h=Invoke-SigerHealth $s $secret;Step 'version y esquema' ([int]$h.schema -ge 10) "version $($h.version), esquema $($h.schema), formulario $($h.integrations.formulario), correo $($h.integrations.correo)"

# 2. Parada ordenada: el host pide la parada, SIGER cierra el contexto y libera el lease.
$before=(Get-Item -LiteralPath $log -ErrorAction SilentlyContinue).Length
$t=[Diagnostics.Stopwatch]::StartNew();Stop-Service -Name $app;$elapsed=[int]$t.Elapsed.TotalSeconds
$orderly=$false;if(Test-Path -LiteralPath $log){$fs=[IO.File]::Open($log,'Open','Read','ReadWrite');try{if($before){$null=$fs.Seek($before,'Begin')};$tail=(New-Object IO.StreamReader($fs)).ReadToEnd()}finally{$fs.Dispose()};$orderly=$tail -match 'Solicitud de parada ordenada recibida'}
Step 'parada ordenada' (((Get-Service $app).Status -eq 'Stopped') -and $orderly -and $elapsed -lt 180) "$elapsed s; registro de parada ordenada=$orderly"
Step 'sin proceso residual' (-not (App-Running $s)) "app.pid=$(AppPid)"
Step 'lease del Formulario libre' ((Lock-Holder) -eq '') 'formulario_consumer_lock sin titular'

# 3. Caida de Java: Windows reinicia el servicio (30 s) y vuelve a estar sano.
Start-Service -Name $app;$t=Wait-Healthy $WaitSeconds;Step 'nuevo arranque' ($t -ge 0) "salud en $t s"
$old=AppPid
Stop-Process -Id $old -Force
$t=[Diagnostics.Stopwatch]::StartNew();Start-Sleep 5
$back=Wait-Healthy ($WaitSeconds+60);$new=AppPid
Step 'recuperacion tras caida' (($back -ge 0) -and $new -and $new -ne $old) "proceso $old terminado; nuevo $new sano en $([int]$t.Elapsed.TotalSeconds) s"
$events=@(Get-EventLog -LogName Application -Source SIGER -Newest 5 -ErrorAction SilentlyContinue|Where-Object {$_.Message -like '*termino inesperadamente*'})
Step 'evento de caida registrado' ($events.Count -gt 0) "eventos=$($events.Count)"

# 4. Motor propio (opcional): detenerlo detiene SIGER; al iniciar SIGER vuelve con su dependencia.
if($IncluirMySql){
    Stop-Service -Name $db -Force
    Step 'dependencia detenida' (((Get-Service $app).Status -eq 'Stopped') -and ((Get-Service $db).Status -eq 'Stopped')) 'SIGER se detiene con su motor'
    Start-Service -Name $app;$t=Wait-Healthy $WaitSeconds
    Step 'recuperacion con dependencia' (($t -ge 0) -and ((Get-Service $db).Status -eq 'Running')) "salud en $t s"
}
$h=Invoke-SigerHealth $s $secret
Step 'estado final' ($h.status -eq 'UP') "formulario $($h.integrations.formulario), correo $($h.integrations.correo)"
$file=Join-Path $Root ('logs\prueba-servicio-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.json')
[PSCustomObject]@{instancia=$s.root;servicio=$app;motor=$db;pasos=$steps}|ConvertTo-Json -Depth 4|Set-Content -LiteralPath $file -Encoding UTF8
$failed=@($steps|Where-Object {-not $_.ok}).Count
Write-Host "Resultado en $file. Fallas: $failed. El reinicio del equipo se verifica aparte (Restart-Computer en ventana autorizada y luego Estado.ps1)."
if($failed -gt 0){exit 1}
