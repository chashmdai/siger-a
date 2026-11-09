[CmdletBinding()]
param([string]$JavaHome, [string]$NodeHome, [switch]$IncludeJava, [switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Hash.ps1')
& (Join-Path $PSScriptRoot 'Check-Scripts.ps1')
$project = Split-Path $PSScriptRoot -Parent
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
    } finally { Pop-Location }
    Push-Location $project
    try {
        $arguments=@('-B','-ntp','-f','backend\pom.xml','clean','verify')
        if ($SkipChecks) { $arguments += '-DskipTests' }
        & (Join-Path $project 'mvnw.cmd') @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Fallo Maven. Revisa el primer error; no hay release acreditado.' }
    } finally { Pop-Location }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $release = Join-Path $project "release\SIGER-$stamp"
    New-Item -ItemType Directory -Force -Path (Join-Path $release 'app') | Out-Null
    Copy-Item (Join-Path $project 'backend\target\siger-a.jar') (Join-Path $release 'app\siger-a.jar')
    Copy-Item $PSScriptRoot (Join-Path $release 'scripts') -Recurse
    Copy-Item (Join-Path $project 'docs') (Join-Path $release 'docs') -Recurse
    if ($IncludeJava) { New-Item -ItemType Directory -Force -Path (Join-Path $release 'runtime') | Out-Null; Copy-Item $JavaHome (Join-Path $release 'runtime\java25') -Recurse }
    New-Item -ItemType Directory -Force -Path (Join-Path $release 'build-info') | Out-Null
    Copy-Item (Join-Path $project 'frontend\package-lock.json') (Join-Path $release 'build-info\package-lock.json')
    Copy-Item (Join-Path $project 'backend\pom.xml') (Join-Path $release 'build-info\pom.xml')
    if (Test-Path (Join-Path $project '.tools\java25-manifest.json')) { Copy-Item (Join-Path $project '.tools\java25-manifest.json') (Join-Path $release 'build-info\java25-manifest.json') }
    $hash=(Get-SigerFileHash (Join-Path $release 'app\siger-a.jar') -Algorithm SHA256).Hash.ToLower()
    @{name='SIGER';version='0.4.0';createdUtc=(Get-Date).ToUniversalTime().ToString('o');jarSha256=$hash;java=$javaText.Trim();node=$nodeText;automatedTestsExecuted=(-not $SkipChecks);databaseTarget='MySQL independiente';integrations='Formulario PULL/ACK, correo Graph y servicio Windows inactivos hasta configurarlos';schema=10} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $release 'manifest.json')
    Compress-Archive -Path $release -DestinationPath ($release+'.zip')
    Write-Host ('Paquete generado: ' + $release + '.zip')
    Write-Host ('JAR SHA-256: ' + $hash)
    Write-Host 'La validacion manual y MySQL/Windows siguen siendo necesarias.'
} finally { $env:PATH=$oldPath; $env:JAVA_HOME=$oldJava }
