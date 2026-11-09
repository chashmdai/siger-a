[CmdletBinding()]
param([string]$Root='D:\SIGER-A')
# Se ejecuta COMO la cuenta de servicio de SIGER (Install-Service.ps1 la lanza con esa identidad; tambien puede usarse
# con runas). Informa, sin mostrar secretos, si la cuenta no tiene privilegios administrativos y si descifra la custodia
# DPAPI de la instancia. Salida: una linea JSON; codigo 0 solo si la identidad es apta.
$ErrorActionPreference='Stop'
$me=[Security.Principal.WindowsIdentity]::GetCurrent()
# Pertenencia a Administradores, incluso como grupo de solo denegacion (token filtrado por UAC): un servicio no se filtra.
$groups=(& "$env:SystemRoot\System32\whoami.exe" /groups /fo csv /nh) -join "`n"
$admin=$groups -match 'S-1-5-32-544' -or $me.Groups.Value -contains 'S-1-5-32-544'
$dpapi=$false
try{$null=Import-Clixml -LiteralPath (Join-Path $Root 'config\secrets.clixml');$dpapi=$true}catch{}
$owner=$null
try{$owner=(Get-Content -LiteralPath (Join-Path $Root 'config\instance.json') -Raw|ConvertFrom-Json).ownerSid}catch{}
# Defender con ESTA cuenta (si config\antivirus.json lo usa): estado legible y activo, y analisis de un archivo limpio
# sintetico con el mismo criterio que SIGER (codigo 0 y "found no threats"). Solo informa; no cambia configuracion.
$defender='NO_CONFIGURADO'
$avFile=Join-Path $Root 'config\antivirus.json'
if(Test-Path -LiteralPath $avFile){
    $av=Get-Content -LiteralPath $avFile -Raw|ConvertFrom-Json
    if([string]$av.engine -eq 'DEFENDER'){
        try{$st=Get-MpComputerStatus -ErrorAction Stop;$readable=$true}catch{$readable=$false}
        if(-not $readable){$defender='ESTADO_NO_LEGIBLE'}
        elseif(-not($st.AMServiceEnabled -and $st.AntivirusEnabled)){$defender='MOTOR_INACTIVO'}
        else{
            $probe=Join-Path $Root ('tmp\av-identidad-'+[Guid]::NewGuid().ToString('N')+'.txt')
            try{
                'SIGER: archivo sintetico limpio para comprobar el analisis con la cuenta de servicio.'|Set-Content -Encoding ASCII -LiteralPath $probe
                $text=(& ([string]$av.executable) -Scan -ScanType 3 -File $probe -DisableRemediation 2>&1|Out-String);$code=$LASTEXITCODE
                $defender=if($code -eq 0 -and $text.Contains("Scanning $probe found no threats")){'OPERATIVO'}elseif($text -match 'Failed with hr = (0x[0-9A-Fa-f]{8})'){"ANALISIS_FALLIDO_$($Matches[1])"}else{"ANALISIS_NO_CONFIRMADO_$code"}
            }finally{if(Test-Path -LiteralPath $probe){Remove-Item -LiteralPath $probe}}
        }
    }else{$defender='NO_APLICA'}
}
$result=[ordered]@{sid=$me.User.Value;account=$me.Name;administrator=[bool]$admin;dpapi=$dpapi;instanceOwner=([string]$owner -eq $me.User.Value);defender=$defender}
($result|ConvertTo-Json -Compress)
if($admin -or -not $dpapi -or -not $result.instanceOwner){exit 3}
