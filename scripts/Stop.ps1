[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A',[switch]$AlsoMySql,[int]$WaitSeconds=180)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root;$pidfile=Join-Path $Root 'run\app.pid'
$services=Get-SigerServices $s
if($services){
    # Servicio: el host pide la parada ordenada (run\stop.request) y espera a que terminen los ciclos en curso.
    if($services.App.Status -ne 'Stopped' -and $PSCmdlet.ShouldProcess($services.Info.serviceName,'Detener el servicio SIGER')){Stop-Service -Name $services.Info.serviceName}
    if($AlsoMySql -and $services.MySql.Status -ne 'Stopped' -and $PSCmdlet.ShouldProcess($services.Info.mysqlServiceName,'Detener el servicio del motor propio')){Stop-Service -Name $services.Info.mysqlServiceName}
    return
}
if(Test-Path $pidfile){
    $id=[int](Get-Content $pidfile -Raw).Trim();$p=Own-Process $id (Join-Path $s.javaHome 'bin\java.exe') $s.activeJar
    if($p -and $PSCmdlet.ShouldProcess("PID $id",'Detener solo SIGER (parada ordenada)')){
        # Parada ordenada: SIGER cierra el contexto, termina el ciclo del Formulario o del correo en curso y libera leases.
        [IO.File]::WriteAllText((Join-Path $Root 'run\stop.request'),'STOP',[Text.Encoding]::ASCII)
        Wait-Process -Id $id -Timeout $WaitSeconds -ErrorAction SilentlyContinue
        if(Get-Process -Id $id -ErrorAction SilentlyContinue){Write-Warning 'No termino en el plazo; se detiene el proceso (una entrega sin COMMIT se reintenta; un ACK pendiente se concilia al volver).';Stop-Process -Id $id;Wait-Process -Id $id -Timeout 20 -ErrorAction SilentlyContinue}
    }
    if(-not(Get-Process -Id $id -ErrorAction SilentlyContinue)){Remove-Item $pidfile}
}
if($AlsoMySql){
    if(App-Running $s){throw 'SIGER debe estar detenido antes de parar su motor.'}
    $secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
    try {if($PSCmdlet.ShouldProcess("127.0.0.1:$($s.mysqlPort)",'Apagar MySQL propio con autenticacion')){if((Invoke-Native {& (Join-Path $s.mysqlHome 'bin\mysqladmin.exe') "--defaults-file=$client" shutdown}) -ne 0){throw 'No se pudo confirmar la parada MySQL.'}}}finally{Remove-Item $client}
    # mysqladmin vuelve al aceptar la orden; el motor termina despues de vaciar sus archivos. Se espera a que salga su
    # propio proceso (mismo ejecutable y my.ini) para que el cierre del piloto, el retiro de servicios o un respaldo no
    # actuen con el motor aun escribiendo. Nunca se fuerza su termino.
    $exe=Join-Path $s.mysqlHome 'bin\mysqld.exe';$ini=Join-Path $Root 'mysql\my.ini'
    for($i=0;$i -lt 120;$i++){
        $alive=@(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction SilentlyContinue|Where-Object{$_.ExecutablePath -ieq $exe -and $_.CommandLine -and $_.CommandLine.IndexOf($ini,[StringComparison]::OrdinalIgnoreCase) -ge 0})
        if($alive.Count -eq 0){break};Start-Sleep 1
    }
    if($alive.Count -gt 0){throw 'El motor propio acepto la parada pero su proceso sigue activo tras 120 s. Revisa mysql\data\mysql-error.log; no se fuerza su termino.'}
    Write-Host 'Motor propio detenido.'
}
