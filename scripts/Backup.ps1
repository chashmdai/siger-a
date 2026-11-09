[CmdletBinding()]
param([string]$Root='D:\SIGER-A',[Parameter(Mandatory=$true)][string]$Destination)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s=Read-Settings $Root
if(App-Running $s){throw 'Deten SIGER antes del respaldo para conservar SQL y documentos coherentes.'}
$Destination=[IO.Path]::GetFullPath($Destination).TrimEnd('\')
if(Test-Path $Destination){throw 'El respaldo necesita una carpeta nueva; nunca sobreescribe.'}
Private-Directory $Destination
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try {
    Require-OwnMySql $s $client
    $tables=@(Invoke-SigerSql $s $client 'SHOW TABLES' $s.database)
    $counts=@(foreach($table in $tables){
        $table=([string]$table).Trim();if($table -notmatch '^[a-zA-Z0-9_]+$'){throw 'Tabla inesperada.'}
        [PSCustomObject]@{Table=$table;Rows=([string](Invoke-SigerSql $s $client "SELECT COUNT(*) FROM ``$table``" $s.database)).Trim()}
    })
    $counts | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $Destination 'table-counts.csv')
    Remove-Item -LiteralPath $client;$client=MySql-ClientFile $s $secret 'backup'
    $dump=Join-Path $Destination 'siger-a.sql'
    if((Invoke-Native {& (Join-Path $s.mysqlHome 'bin\mysqldump.exe') "--defaults-file=$client" --single-transaction --no-tablespaces --set-gtid-purged=OFF --hex-blob --default-character-set=utf8mb4 "--result-file=$dump" $s.database}) -ne 0){throw 'Dump incompleto. No hay respaldo valido.'}
    Copy-Item (Join-Path $Root 'data\documents') (Join-Path $Destination 'documents') -Recurse
    Copy-Item (Join-Path $Root 'config') (Join-Path $Destination 'config-private') -Recurse
    $hashes=Get-ChildItem $Destination -File -Recurse | ForEach-Object {[PSCustomObject]@{Path=$_.FullName.Substring($Destination.Length+1);SHA256=(Get-SigerFileHash $_.FullName -Algorithm SHA256).Hash}}
    $hashes | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $Destination 'hashes.csv')
    @{namespace='SIGER-A-2026';complete=$true;sourceRoot=$s.root;createdUtc=(Get-Date).ToUniversalTime().ToString('o');database=$s.database;jarSha256=$s.jarSha256;manifestSha256=(Get-SigerFileHash (Join-Path $Destination 'hashes.csv') -Algorithm SHA256).Hash;restorationTested=$false;note='Copia privada local. DPAPI requiere la misma identidad Windows; no es recuperacion offsite.'} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $Destination 'backup.json')
    Test-SigerBackup $Destination | Out-Null
    Write-Host 'Respaldo coherente e integridad verificada. Ejecuta Restore-Check.ps1 en destino nuevo para acreditar restauracion.'
} finally {if($client -and (Test-Path $client)){Remove-Item -LiteralPath $client}}
