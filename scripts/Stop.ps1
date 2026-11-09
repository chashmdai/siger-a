[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A',[switch]$AlsoMySql)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root;$pidfile=Join-Path $Root 'run\app.pid'
if(Test-Path $pidfile){$id=[int](Get-Content $pidfile -Raw).Trim();$p=Own-Process $id (Join-Path $s.javaHome 'bin\java.exe') $s.activeJar;if($p -and $PSCmdlet.ShouldProcess("PID $id",'Detener solo SIGER A')){Stop-Process -Id $id;Wait-Process -Id $id -Timeout 20 -ErrorAction SilentlyContinue};if(-not(Get-Process -Id $id -ErrorAction SilentlyContinue)){Remove-Item $pidfile}}
# En Windows esta parada de proceso no equivale a un drenaje de servicio productivo.
if($AlsoMySql){
    if(App-Running $s){throw 'SIGER A debe estar detenido antes de parar su motor.'}
    $secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
    try {if($PSCmdlet.ShouldProcess("127.0.0.1:$($s.mysqlPort)",'Apagar MySQL propio con autenticacion')){& (Join-Path $s.mysqlHome 'bin\mysqladmin.exe') "--defaults-file=$client" shutdown;if($LASTEXITCODE -ne 0){throw 'No se pudo confirmar la parada MySQL.'}}}finally{Remove-Item $client}
}
