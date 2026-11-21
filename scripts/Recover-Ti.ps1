[CmdletBinding()]
param([string]$Root='D:\SIGER-A',
    [Parameter(Mandatory=$true)][string]$Usuario,
    # Acto institucional que respalda la recuperacion (resolucion, memo): queda en la auditoria.
    [Parameter(Mandatory=$true)][string]$Fundamento,
    # Solo para una cuenta nueva: nombre de la persona y codigo del ambito de adscripcion (por ejemplo UGI).
    [string]$Nombre='',[string]$Ambito='')
# Recuperacion de una instancia SIN ninguna cuenta TI habilitada, vigente y sin termino (0.5.0). Ejecutar COMO la
# cuenta de servicio de SIGER, con SIGER detenido y su MySQL activo. Un proceso sin servidor web ni tareas
# programadas otorga TI (desde ahora, sin termino) a la cuenta indicada, la habilita y le asigna una clave temporal
# con cambio obligatorio, auditado a nombre de sistema:actualizacion con el fundamento. Si queda una TI permanente,
# la recuperacion se rechaza: la administracion se hace por la interfaz. La clave temporal queda solo en
# data\recuperacion-ti-*.csv; no se muestra ni va al log.
. (Join-Path $PSScriptRoot 'Environment.ps1')
if($Fundamento.Trim().Length -lt 10){throw 'Indica en -Fundamento el acto institucional que respalda la recuperacion (al menos 10 caracteres).'}
$s=Read-Settings $Root
$journal=Join-Path $Root 'config\update-state.json'
if(Test-Path $journal){$update=Get-Content $journal -Raw|ConvertFrom-Json;if($update.phase -ne 'COMPLETE'){throw "Actualizacion en curso ($($update.phase)). No se ejecuta la recuperacion."}}
if(-not(Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'Falta inicializacion validada.'}
Require-Java25 $s.javaHome
if((Get-SigerFileHash $s.activeJar -Algorithm SHA256).Hash -ine $s.jarSha256){throw 'JAR alterado.'}
$services=Get-SigerServices $s
if($services -and $services.App.Status -ne 'Stopped'){throw 'Deten el servicio SIGER (Stop.ps1) antes de la recuperacion; su MySQL debe seguir activo.'}
if(App-Running $s){throw 'Deten SIGER (Stop.ps1) antes de la recuperacion; su MySQL debe seguir activo.'}
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try{Require-OwnMySql $s $client}finally{Remove-SecretFile $client}
New-Item -ItemType Directory -Force -Path (Join-Path $Root 'logs') | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$out=Join-Path $Root "logs\recuperacion-ti-$stamp.out.log";$err=Join-Path $Root "logs\recuperacion-ti-$stamp.err.log"
$recover=@('SIGER_RECOVER_TI_USER','SIGER_RECOVER_TI_NAME','SIGER_RECOVER_TI_SCOPE','SIGER_RECOVER_TI_BASIS')
try{
    Set-SigerEnvironment $s $secret
    $env:SIGER_SCHEDULING='false'
    # Datos de la recuperacion solo como variables del proceso (sin comillas ni escapes en la linea de Java).
    $env:SIGER_RECOVER_TI_USER=$Usuario;$env:SIGER_RECOVER_TI_NAME=$Nombre;$env:SIGER_RECOVER_TI_SCOPE=$Ambito;$env:SIGER_RECOVER_TI_BASIS=$Fundamento
    $arguments=@('-Xms64m','-Xmx512m',"`"-Djava.io.tmpdir=$(Join-Path $Root 'tmp')`"",'-jar',"`"$($s.activeJar)`"",'--spring.main.web-application-type=none')
    $p=Start-Process -FilePath (Join-Path $s.javaHome 'bin\java.exe') -ArgumentList $arguments -WorkingDirectory $Root -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -Wait -WindowStyle Hidden
    $code=$p.ExitCode
}finally{
    Clear-SigerEnvironment
    foreach($key in $recover){Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue}
}
$marker='Recuperacion de TI:'
$summary=Select-String -LiteralPath $out -Pattern $marker -SimpleMatch | Select-Object -Last 1
if($summary){Write-Host $summary.Line.Substring($summary.Line.IndexOf($marker))}
if($code -ne 0){throw "La recuperacion no se aplico (codigo $code). Revisa logs\recuperacion-ti-$stamp.*.log; no se cambio ninguna cuenta."}
Write-Host 'Recuperacion aplicada y auditada (TI_RECUPERADA). Clave temporal en data\recuperacion-ti-*.csv: entregala por canal seguro y retira el archivo. Inicia SIGER (Start.ps1 o el servicio); la persona cambia la clave al ingresar.'
