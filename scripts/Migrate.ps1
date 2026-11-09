[CmdletBinding()]
param([string]$Root='D:\SIGER-A',[switch]$Initial,[string]$BackupDirectory,[string]$CandidateJar,[string]$CandidateSha256)
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root
if(App-Running $s){throw 'Deten solo SIGER A antes de migrar.'}
if(-not $Initial){
    if(-not $BackupDirectory){throw 'Se requiere BackupDirectory antes de migrar.'}
    $backup=Test-SigerBackup $BackupDirectory
    if($backup.sourceRoot -ine $s.root -or $backup.database -ne $s.database -or $backup.jarSha256 -ine $s.jarSha256){throw 'El respaldo no corresponde al estado activo de esta instancia.'}
}
if($Initial -and (Test-Path (Join-Path $Root 'config\initialized.marker'))){throw 'No se repite la inicializacion.'}
Require-Java25 $s.javaHome
$jar=$s.activeJar;$hash=$s.jarSha256
if($CandidateJar){$jar=(Resolve-Path -LiteralPath $CandidateJar).Path;$hash=$CandidateSha256}
if(-not $hash -or (Get-SigerFileHash $jar -Algorithm SHA256).Hash -ine $hash){throw 'JAR no coincide con manifiesto.'}
$secret=Import-Clixml (Join-Path $Root 'config\secrets.clixml');$client=MySql-ClientFile $s $secret 'owner'
try{Require-OwnMySql $s $client}finally{Remove-Item -LiteralPath $client}
try {
    Set-SigerEnvironment $s $secret -Migration -Seed:$Initial
    & (Join-Path $s.javaHome 'bin\java.exe') '-Xms128m' '-Xmx768m' "-Djava.io.tmpdir=$(Join-Path $Root 'tmp')" '-jar' $jar '--spring.main.web-application-type=none' '--siger.migrate-only=true'
    if($LASTEXITCODE -ne 0){throw 'Fallo la migracion. No inicies una version anterior sobre un esquema incompatible.'}
    if($Initial){Set-Content -Encoding ASCII (Join-Path $Root 'config\initialized.marker') (Get-Date).ToUniversalTime().ToString('o')}
} finally {Clear-SigerEnvironment}
