[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Root='D:\SIGER-A',[Parameter(Mandatory=$true)][string]$Release,[Parameter(Mandatory=$true)][string]$MySqlHome,[string]$JavaHome,[int]$MySqlPort=13326,[int]$WebPort=18543,
    # Institucional: sin cuentas, sedes ni datos de prueba; requiere -EstructuraInicial y se ejecuta como la cuenta de
    # servicio SIN privilegios administrativos (sera la duena de DPAPI). Pruebas: datos sinteticos como hasta ahora.
    [Parameter(Mandatory=$true)][ValidateSet('Institucional','Pruebas')][string]$Mode,
    [string]$EstructuraInicial='')
. (Join-Path $PSScriptRoot 'Common.ps1')
$Root=[IO.Path]::GetFullPath($Root).TrimEnd('\');$Release=(Resolve-Path $Release).Path;$MySqlHome=(Resolve-Path $MySqlHome).Path
if($Root -notmatch '^[D-Z]:\\[^\r\n]+$'){throw 'Usa una carpeta nueva de D: u otra unidad de datos; no C: ni la raiz del disco.'}
# La raiz no existe, o TI la creo vacia para la cuenta de servicio (que debe poder escribirla).
if(Test-Path $Root){if(@(Get-ChildItem -LiteralPath $Root -Force).Count -gt 0){throw 'La raiz ya existe y no esta vacia. No se inicializa encima de datos ni se borra nada.'}}
$me=[Security.Principal.WindowsIdentity]::GetCurrent()
if($Mode -eq 'Institucional'){
    $groups=(& "$env:SystemRoot\System32\whoami.exe" /groups /fo csv /nh) -join "`n"
    if($groups -match 'S-1-5-32-544'){throw 'La instancia institucional se inicializa COMO la cuenta de servicio de SIGER, sin privilegios administrativos (runas /user:<cuenta>). Esa cuenta sera la duena de la custodia DPAPI.'}
    if(-not $EstructuraInicial -or -not(Test-Path -LiteralPath $EstructuraInicial -PathType Leaf)){throw 'El modo Institucional exige -EstructuraInicial con el archivo de ambitos reales, TI y autoridad habilitante (ver docs\INSTALACION_VM.md).'}
}elseif($EstructuraInicial){throw '-EstructuraInicial solo corresponde al modo Institucional.'}
if($MySqlPort -lt 1024 -or $WebPort -lt 1024 -or $MySqlPort -eq $WebPort -or $MySqlPort -in @(3306,33060,1433,13316) -or $WebPort -in @(80,443,8080,3389)){throw 'Elige puertos propios no reservados para servicios existentes.'}
Free-Port $MySqlPort;Free-Port $WebPort
$jar=Join-Path $Release 'app\siger-a.jar';$manifest=Get-Content (Join-Path $Release 'manifest.json') -Raw | ConvertFrom-Json
if((Get-SigerFileHash $jar -Algorithm SHA256).Hash -ine $manifest.jarSha256){throw 'SHA-256 del JAR no coincide.'}
if(-not $JavaHome){$JavaHome=Join-Path $Release 'runtime\java25'}
$JavaHome=(Resolve-Path $JavaHome).Path;Require-Java25 $JavaHome
$mysql=Join-Path $MySqlHome 'bin\mysqld.exe';$mysqlClient=Join-Path $MySqlHome 'bin\mysql.exe'
if(-not(Test-Path $mysql) -or -not(Test-Path $mysqlClient)){throw 'MySqlHome debe contener una distribucion ZIP completa.'}
$mysqlVersion=(& $mysql --version | Out-String)
if($LASTEXITCODE -ne 0 -or $mysqlVersion -notmatch 'Ver 8\.(0|4)\.'){throw 'Se requiere MySQL 8.0/8.4 x64. Revisa tambien el runtime Visual C++ instalado.'}
$what=if($Mode -eq 'Institucional'){'Crear instancia institucional SIGER (sin datos de prueba) y MySQL propio'}else{'Crear instancia de pruebas SIGER, MySQL propio y usuarios sinteticos'}
if(-not $PSCmdlet.ShouldProcess($Root,$what)){return}
# Esquema propio por modo (siger_a_* exigido por MigrationGuard); el nombre interno no cambia el producto visible.
$schema=if($Mode -eq 'Institucional'){'siger_a_inst'}else{'siger_a_lab'}
Private-Directory $Root
foreach($d in @('config','data','data\documents','data\tmp','logs','tmp','run','mysql','mysql\data','mysql\tmp','releases')){New-Item -ItemType Directory -Force -Path (Join-Path $Root $d)|Out-Null}
$tag='initial-'+(Get-Date -Format 'yyyyMMdd-HHmmss');$installed=Join-Path $Root "releases\$tag"
Copy-Item $Release $installed -Recurse
$jar=Join-Path $installed 'app\siger-a.jar'
if(Test-Path (Join-Path $installed 'runtime\java25')){$JavaHome=Join-Path $installed 'runtime\java25'}
$owner=Random-Secret;$app=Random-Secret;$migrator=Random-Secret;$backup=Random-Secret;$tls=Random-Secret;$trust=Random-Secret
$secretObject=[PSCustomObject]@{Owner=(ConvertTo-SecureString $owner -AsPlainText -Force);App=(ConvertTo-SecureString $app -AsPlainText -Force);Migrator=(ConvertTo-SecureString $migrator -AsPlainText -Force);Backup=(ConvertTo-SecureString $backup -AsPlainText -Force);Tls=(ConvertTo-SecureString $tls -AsPlainText -Force);Trust=(ConvertTo-SecureString $trust -AsPlainText -Force)}
$secretObject | Export-Clixml (Join-Path $Root 'config\secrets.clixml')
$ini=Join-Path $Root 'mysql\my.ini';$data=(Join-Path $Root 'mysql\data').Replace('\','/');$base=$MySqlHome.Replace('\','/')
@("[mysqld]","basedir=$base","datadir=$data","tmpdir=$((Join-Path $Root 'mysql\tmp').Replace('\','/'))",'bind-address=127.0.0.1',"port=$MySqlPort",'mysqlx=OFF','skip-name-resolve=ON',"pid-file=$data/siger-a.pid","log-error=$data/mysql-error.log",'innodb-buffer-pool-size=256M','max-connections=20','innodb-flush-log-at-trx-commit=1','require-secure-transport=ON','local-infile=OFF','character-set-server=utf8mb4','collation-server=utf8mb4_0900_ai_ci') | Set-Content -Encoding ASCII $ini
& $mysql "--defaults-file=$ini" --initialize-insecure
if($LASTEXITCODE -ne 0){throw 'MySQL no se inicializo. Revisa el log privado; no repitas sobre el datadir creado.'}
$init=Join-Path $Root 'tmp\init-users.sql'
$sql=@("ALTER USER 'root'@'localhost' IDENTIFIED BY '$owner';","CREATE USER 'siger_a_owner'@'127.0.0.1' IDENTIFIED BY '$owner';","GRANT ALL PRIVILEGES ON *.* TO 'siger_a_owner'@'127.0.0.1' WITH GRANT OPTION;","CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;","CREATE USER 'siger_a_app'@'127.0.0.1' IDENTIFIED BY '$app';","GRANT SELECT,INSERT,UPDATE,DELETE ON $schema.* TO 'siger_a_app'@'127.0.0.1';","CREATE USER 'siger_a_migrator'@'127.0.0.1' IDENTIFIED BY '$migrator';","GRANT ALL PRIVILEGES ON $schema.* TO 'siger_a_migrator'@'127.0.0.1';","CREATE USER 'siger_a_backup'@'127.0.0.1' IDENTIFIED BY '$backup';","GRANT SELECT,SHOW VIEW,TRIGGER,EVENT ON $schema.* TO 'siger_a_backup'@'127.0.0.1';")
[IO.File]::WriteAllLines($init,$sql,[Text.Encoding]::ASCII)
$settings=[PSCustomObject]@{namespace='SIGER-A-2026';root=$Root;mysqlHome=$MySqlHome;mysqlPort=$MySqlPort;webPort=$WebPort;javaHome=$JavaHome;activeJar=$jar;jarSha256=$manifest.jarSha256;database=$schema;mode=$Mode.ToUpperInvariant();ownerSid=$me.User.Value;createdUtc=(Get-Date).ToUniversalTime().ToString('o')}
$settings | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $Root 'config\instance.json')
$started=$null;$client=$null
try {
    $started=Start-Process -FilePath $mysql -ArgumentList @("`"--defaults-file=$ini`"","`"--init-file=$init`"") -PassThru -WindowStyle Hidden
    $client=MySql-ClientFile $settings $secretObject 'owner'
    $ready=$false
    for($i=0;$i -lt 60;$i++){Start-Sleep -Seconds 1;& $mysqlClient "--defaults-file=$client" --batch --skip-column-names -e 'SELECT 1' *> $null;if($LASTEXITCODE -eq 0){$ready=$true;break}}
    if(-not $ready){throw 'MySQL propio no estuvo disponible; revisar su log privado. No se intervino MySQL80.'}
} finally { if(Test-Path $init){Remove-Item $init};if($client -and (Test-Path $client)){Remove-Item $client} }
# Certificado HTTPS SOLO localhost; no se instala confianza global.
$passFile=Join-Path $Root 'tmp\keytool-pass.txt';$keytool=Join-Path $JavaHome 'bin\keytool.exe';$store=Join-Path $Root 'config\https.p12'
try {
    [IO.File]::WriteAllText($passFile,$tls,[Text.Encoding]::ASCII)
    & $keytool -genkeypair -alias siger-a-local -keyalg RSA -keysize 3072 -validity 365 -dname 'CN=localhost,OU=SIGER' -ext 'SAN=dns:localhost,ip:127.0.0.1' -storetype PKCS12 -keystore $store "-storepass:file" $passFile -noprompt
    if($LASTEXITCODE -ne 0){throw 'No se creo el certificado HTTPS local.'}
    [IO.File]::WriteAllText($passFile,$trust,[Text.Encoding]::ASCII)
    & $keytool -importcert -alias mysql-local-ca -file (Join-Path $Root 'mysql\data\ca.pem') -keystore (Join-Path $Root 'config\mysql-trust.p12') -storetype PKCS12 '-storepass:file' $passFile -noprompt
    if($LASTEXITCODE -ne 0){throw 'No se creo el truststore de MySQL.'}
} finally {if(Test-Path $passFile){Remove-Item $passFile}}
Copy-Item $PSScriptRoot (Join-Path $Root 'scripts') -Recurse
# La estructura inicial queda en config\ (ACL privada) y la aplica la primera migracion, una sola vez.
if($Mode -eq 'Institucional'){Copy-Item -LiteralPath $EstructuraInicial -Destination (Join-Path $Root 'config\estructura-inicial.json')}
& (Join-Path $Root 'scripts\Migrate.ps1') -Root $Root -Initial
if($LASTEXITCODE -and $LASTEXITCODE -ne 0){throw 'La migracion necesita revision.'}
Write-Host "Instancia $($Mode.ToLowerInvariant()) preparada en $Root. MySQL propio permanece activo. No se inicio SIGER ni se abrio firewall."
if($Mode -eq 'Institucional'){Write-Host 'Claves temporales de la cuenta TI y de la autoridad habilitante en data\bootstrap-usuarios.csv: entregarlas por canal seguro y retirar el archivo. No hay cuentas ni datos de prueba.'}
else{Write-Host 'Las claves sinteticas estan en data\bootstrap-usuarios.csv. Nunca pegarlas en el chat.'}
