[CmdletBinding()]
param([string]$JavaHome, [string]$NodeHome, [switch]$IncludeJava, [switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Hash.ps1')
& (Join-Path $PSScriptRoot 'Check-Scripts.ps1')
$project = Split-Path $PSScriptRoot -Parent
# Unica version del release: backend/pom.xml. El frontend debe declarar la misma.
[xml]$pom = Get-Content -LiteralPath (Join-Path $project 'backend\pom.xml') -Raw
$releaseVersion = [string]$pom.project.version
if ($releaseVersion -cnotmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?$') { throw 'Version de backend/pom.xml invalida o no resuelta.' }
$frontendVersion = [string](Get-Content -LiteralPath (Join-Path $project 'frontend\package.json') -Raw | ConvertFrom-Json).version
if ($frontendVersion -cne $releaseVersion) { throw "Versiones inconsistentes: backend=$releaseVersion, frontend=$frontendVersion. Sincroniza ambos antes de compilar." }
# El ultimo numero de Flyway se obtiene de los archivos SQL que entran al JAR.
$migrations = @(Get-ChildItem -LiteralPath (Join-Path $project 'backend\src\main\resources\db\migration') -File -Filter 'V*.sql')
if ($migrations.Count -eq 0) { throw 'No hay migraciones Flyway para declarar el esquema del release.' }
$schemaVersion = 0; $seenVersions = @{}
foreach ($migration in $migrations) {
    if ($migration.Name -cnotmatch '^V([1-9][0-9]*)__.+\.sql$') { throw "Migracion Flyway no admitida: $($migration.Name). Usa Vn__descripcion.sql." }
    $number = [int]$Matches[1]
    if ($seenVersions.ContainsKey($number)) { throw "Numero de migracion Flyway duplicado: $number." }
    $seenVersions[$number] = $true
    if ($number -gt $schemaVersion) { $schemaVersion = $number }
}
if (-not $JavaHome) { $JavaHome = Join-Path $project '.tools\java25' }
if (-not $NodeHome) { $NodeHome = Join-Path $project '.tools\node-v24.22.0-win-x64' }
$JavaHome = (Resolve-Path $JavaHome).Path; $NodeHome = (Resolve-Path $NodeHome).Path
$java = Join-Path $JavaHome 'bin\java.exe'; $npm = Join-Path $NodeHome 'npm.cmd'
if (-not (Test-Path (Join-Path $JavaHome 'bin\javac.exe')) -or -not (Test-Path $npm)) { throw 'Indica JDK 25 y Node 24 completos.' }
$javaText = (& $java --version | Out-String)
if ($javaText -notmatch '(?m)^(openjdk|java) 25[.\s]' ) { throw 'La compilacion requiere Java 25.' }
$nodeText = (& (Join-Path $NodeHome 'node.exe') --version)
if ($nodeText -notmatch '^v24\.') { throw 'Esta entrega fija Node 24 para el build.' }
$oldPath=$env:PATH; $oldJava=$env:JAVA_HOME
try {
    $env:JAVA_HOME=$JavaHome; $env:PATH="$NodeHome;$JavaHome\bin;$oldPath"
    Push-Location (Join-Path $project 'frontend')
    try {
        if (-not (Test-Path 'package-lock.json')) {
            Write-Host 'Primer build: resolviendo dependencias exactas y creando package-lock.json. Conserva este archivo en Git.'
            & $npm install --package-lock-only --no-fund --no-audit
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear package-lock.json.' }
        }
        # Node ya esta disponible. Su parser JSON admite claves vacias y sensibles a mayusculas.
        # Evita ConvertFrom-Json sobre package-lock (AsHashtable no existe en Windows PowerShell 5.1).
        $lockCheck = & (Join-Path $NodeHome 'node.exe') -e "const fs=require('node:fs');const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));const p=read('package.json'),l=read('package-lock.json');console.log(p.version===l.version?'OK':'SYNC');"
        if ($LASTEXITCODE -ne 0) { throw 'package-lock.json no es JSON valido. No se elimina ni sustituye automaticamente.' }
        if ($lockCheck.Trim() -eq 'SYNC') {
            & $npm install --package-lock-only --no-fund --no-audit
            if ($LASTEXITCODE -ne 0) { throw 'No fue posible sincronizar package-lock.json.' }
        }
        & $npm ci --no-fund --no-audit
        if ($LASTEXITCODE -ne 0) { throw 'Fallo npm ci.' }
        & $npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Fallo TypeScript/Vite. No se empaqueta una version anterior.' }
        if (-not $SkipChecks) { & $npm test; if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas del frontend.' } }
    } finally { Pop-Location }
    Push-Location $project
    try {
        $arguments=@('-B','-ntp','-f','backend\pom.xml','clean','verify')
        if ($SkipChecks) { $arguments += '-DskipTests' }
        & (Join-Path $project 'mvnw.cmd') @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Fallo Maven. Revisa el primer error; no hay release acreditado.' }
    } finally { Pop-Location }
    $buildInfo = Join-Path $project 'backend\target\classes\siger\build-info.properties'
    if (-not (Test-Path -LiteralPath $buildInfo -PathType Leaf) -or -not ((Get-Content -LiteralPath $buildInfo) -ccontains "build.version=$releaseVersion")) { throw 'Maven no genero build-info.properties con la version esperada. No se empaqueta.' }
    # El archivo debe viajar dentro del classpath del JAR ejecutable, no en su META-INF raiz.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jarPath = Join-Path $project 'backend\target\siger-a.jar'
    $archive = [IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $entry = $archive.GetEntry('BOOT-INF/classes/siger/build-info.properties')
        if (-not $entry) { throw 'El JAR no contiene los metadatos de version en BOOT-INF/classes/siger. No se empaqueta.' }
        $reader = New-Object IO.StreamReader($entry.Open())
        try { $jarBuildInfo = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if (($jarBuildInfo -split '\r?\n') -cnotcontains "build.version=$releaseVersion") { throw 'El JAR no contiene la version del POM. No se empaqueta.' }
    } finally { $archive.Dispose() }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $release = Join-Path $project "release\SIGER-$stamp"
    New-Item -ItemType Directory -Force -Path (Join-Path $release 'app') | Out-Null
    Copy-Item (Join-Path $project 'backend\target\siger-a.jar') (Join-Path $release 'app\siger-a.jar')
    Copy-Item $PSScriptRoot (Join-Path $release 'scripts') -Recurse
    Copy-Item (Join-Path $project 'docs') (Join-Path $release 'docs') -Recurse
    Copy-Item (Join-Path $project 'deploy\config-ejemplo') (Join-Path $release 'config-ejemplo') -Recurse
    if ($IncludeJava) { New-Item -ItemType Directory -Force -Path (Join-Path $release 'runtime') | Out-Null; Copy-Item $JavaHome (Join-Path $release 'runtime\java25') -Recurse }
    New-Item -ItemType Directory -Force -Path (Join-Path $release 'build-info') | Out-Null
    Copy-Item (Join-Path $project 'frontend\package-lock.json') (Join-Path $release 'build-info\package-lock.json')
    Copy-Item (Join-Path $project 'backend\pom.xml') (Join-Path $release 'build-info\pom.xml')
    if (Test-Path (Join-Path $project '.tools\java25-manifest.json')) { Copy-Item (Join-Path $project '.tools\java25-manifest.json') (Join-Path $release 'build-info\java25-manifest.json') }
    $hash=(Get-SigerFileHash (Join-Path $release 'app\siger-a.jar') -Algorithm SHA256).Hash.ToLower()
    @{name='SIGER';version=$releaseVersion;createdUtc=(Get-Date).ToUniversalTime().ToString('o');jarSha256=$hash;java=$javaText.Trim();node=$nodeText;automatedTestsExecuted=(-not $SkipChecks);databaseTarget='MySQL independiente';integrations='Formulario PULL/ACK, correo Graph y servicio Windows inactivos hasta configurarlos';schema=$schemaVersion} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $release 'manifest.json')
    # Manifiesto SHA-256 de todos los archivos del paquete (sin secretos: ninguno se incluye).
    $sums=Get-ChildItem -LiteralPath $release -Recurse -File | Sort-Object FullName | ForEach-Object { (Get-SigerFileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower()+'  '+$_.FullName.Substring($release.Length+1).Replace('\','/') }
    [IO.File]::WriteAllLines((Join-Path $release 'SHA256SUMS.txt'),[string[]]$sums,(New-Object Text.UTF8Encoding($false)))
    foreach($forbidden in @('*.clixml','*.dpapi','*.pem','*.p12','*.pfx','*.sigerpkg','bootstrap-usuarios*.csv')){if(Get-ChildItem -LiteralPath $release -Recurse -File -Filter $forbidden){throw "El paquete contiene un archivo no permitido ($forbidden). No se genera el ZIP."}}
    Compress-Archive -Path $release -DestinationPath ($release+'.zip')
    Write-Host ('Paquete generado: ' + $release + '.zip')
    Write-Host ('JAR SHA-256: ' + $hash)
    Write-Host 'La validacion manual y MySQL/Windows siguen siendo necesarias.'
} finally { $env:PATH=$oldPath; $env:JAVA_HOME=$oldJava }
