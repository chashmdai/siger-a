[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A',[Parameter(Mandatory=$true)][string]$BackupDirectory,[Parameter(Mandatory=$true)][string]$Destination)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root;$backup=Test-SigerBackup $BackupDirectory
if(App-Running $s){throw 'Deten SIGER A antes del ensayo. El motor propio se mantiene encendido.'}
$BackupDirectory=(Resolve-Path $BackupDirectory).Path
$Destination=[IO.Path]::GetFullPath($Destination).TrimEnd('\')
if($Destination -notmatch '^[D-Z]:\\[^\r\n]+$' -or (Test-Path $Destination)){throw 'Usa una carpeta NUEVA en unidad de datos; nunca encima de una instancia.'}
if($Destination.StartsWith(([IO.Path]::GetFullPath($Root).TrimEnd('\')+'\'),[StringComparison]::OrdinalIgnoreCase)){throw 'El destino debe estar fuera de la instancia activa.'}
$schema='siger_a_restore_'+(Get-Date -Format 'yyyyMMddHHmmss')+'_'+[Guid]::NewGuid().ToString('N').Substring(0,6)
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try {
    Require-OwnMySql $s $client
    if(-not $PSCmdlet.ShouldProcess("$Destination / $schema",'Restaurar copia en NUEVO esquema del MySQL propio y verificar')){return}
    Private-Directory $Destination
    $exists=(Invoke-SigerSql $s $client "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$schema'" | Out-String).Trim()
    if($exists -ne '0'){throw 'El esquema destino ya existe. No se sobrescribe.'}
    Invoke-SigerSql $s $client "CREATE DATABASE ``$schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" | Out-Null
    # Entrada estandar en UTF-8; sin contrasenas en argumentos ni modificaciones del SQL.
    $psi=New-Object Diagnostics.ProcessStartInfo
    $psi.FileName=Join-Path $s.mysqlHome 'bin\mysql.exe'
    $psi.Arguments="`"--defaults-file=$client`" --default-character-set=utf8mb4 --binary-mode=1 --database=$schema"
    $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardInput=$true
    $psi.StandardInputEncoding=New-Object Text.UTF8Encoding($false)
    $proc=New-Object Diagnostics.Process;$proc.StartInfo=$psi
    if(-not $proc.Start()){throw 'No inicio el cliente de restauracion.'}
    $reader=[IO.File]::OpenText((Join-Path $BackupDirectory 'siger-a.sql'))
    try{$buffer=New-Object char[] 65536;while(($read=$reader.ReadBlock($buffer,0,$buffer.Length)) -gt 0){$proc.StandardInput.Write($buffer,0,$read)}}finally{$reader.Dispose();$proc.StandardInput.Close()}
    $proc.WaitForExit();if($proc.ExitCode -ne 0){throw 'Restore SQL fallido; se conserva el esquema incompleto para diagnostico.'}
    foreach($count in (Import-Csv (Join-Path $BackupDirectory 'table-counts.csv'))){
        if($count.Table -notmatch '^[a-zA-Z0-9_]+$'){throw 'Tabla inesperada en inventario.'}
        $actual=(Invoke-SigerSql $s $client "SELECT COUNT(*) FROM ``$($count.Table)``" $schema | Out-String).Trim()
        if($actual -ne [string]$count.Rows){throw "Conteo distinto en $($count.Table)."}
    }
    $namespace=(Invoke-SigerSql $s $client 'SELECT namespace FROM app_meta WHERE id=1' $schema | Out-String).Trim()
    if($namespace -ne 'SIGER_A_2026'){throw 'Namespace restaurado no coincide.'}
    Copy-Item (Join-Path $BackupDirectory 'documents') (Join-Path $Destination 'documents') -Recurse
    $docs=@(Invoke-SigerSql $s $client 'SELECT storage_key,sha256,size_bytes FROM document_item ORDER BY id' $schema)
    $verified=0
    foreach($line in $docs){
        $parts=([string]$line).Split("`t");if($parts.Length -ne 3 -or $parts[0] -notmatch '^[a-f0-9-]{36}\.bin$'){throw 'Descriptor documental invalido.'}
        $file=Join-Path (Join-Path $Destination 'documents') $parts[0]
        if(-not(Test-Path -LiteralPath $file) -or (Get-Item -LiteralPath $file).Length -ne [long]$parts[2] -or (Get-SigerFileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $parts[1]){throw 'Original restaurado no coincide con SQL.'}
        $verified++
    }
    $orphans=(Invoke-SigerSql $s $client 'SELECT COUNT(*) FROM case_record c LEFT JOIN requirement r ON r.id=c.requirement_id WHERE r.id IS NULL' $schema | Out-String).Trim()
    if($orphans -ne '0'){throw 'Casos restaurados sin REQ.'}
    @{namespace='SIGER-A-RESTORE-CHECK';completed=$true;verifiedUtc=(Get-Date).ToUniversalTime().ToString('o');schema=$schema;documentsVerified=$verified;sourceBackup=$BackupDirectory;sourceJarSha256=$backup.jarSha256;note='Ensayo SQL/documentos. No arranca app, no cambia instancia activa, no acredita recuperacion de otra identidad DPAPI.'} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $Destination 'restore-result.json')
    Write-Host "Restauracion comprobada en $schema y $Destination. No se borro ni sobrescribio la base activa."
} catch {if(Test-Path $Destination){@{completed=$false;schema=$schema;note='Ensayo fallido; conservar destino y revisar error local.'}|ConvertTo-Json|Set-Content -Encoding UTF8 (Join-Path $Destination 'restore-result.json')};throw}
finally {if(Test-Path $client){Remove-Item -LiteralPath $client}}
