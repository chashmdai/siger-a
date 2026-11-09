[CmdletBinding()]
param([string]$Destination = (Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'), [switch]$OnlyJava)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Hash.ps1')
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$Destination = (Resolve-Path $Destination).Path
function Get-CheckedZip([string]$Url,[string]$Expected,[string]$Archive) {
    if (-not $Url.StartsWith('https://')) { throw 'Se requiere HTTPS.' }
    if ($Expected -notmatch '^[a-fA-F0-9]{64}$') { throw 'Huella SHA-256 no valida.' }
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Archive
    if ((Get-SigerFileHash -LiteralPath $Archive -Algorithm SHA256).Hash -ine $Expected) { throw 'Archivo no coincide con SHA-256 publicado.' }
}
$javaHome = Join-Path $Destination 'java25'
if (Test-Path (Join-Path $javaHome 'bin\javac.exe')) {
    $v = (& (Join-Path $javaHome 'bin\java.exe') --version | Out-String)
    if ($v -notmatch '(?m)^(openjdk|java) 25[.\s]') { throw 'java25 existe pero no corresponde a JDK 25.' }
    Write-Host ('Se conserva el JDK existente: ' + $javaHome)
} else {
    if (Test-Path $javaHome) { throw 'java25 existe incompleto. Revisalo; no se borra automaticamente.' }
    # Fija version mayor/proveedor; conserva la version puntual y SHA efectivamente descargados.
    $assets = Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $asset = @($assets)[0]
    if (-not $asset.binary.package.link) { throw 'No se encontro JDK 25 Windows x64.' }
    $zip = Join-Path $Destination 'java25-download.zip'
    Get-CheckedZip $asset.binary.package.link $asset.binary.package.checksum $zip
    $extract = Join-Path $Destination ('java25-extract-' + [Guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $zip -DestinationPath $extract
    $jdk = @(Get-ChildItem -Directory $extract | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') })[0]
    if (-not $jdk) { throw 'El ZIP no contiene un JDK.' }
    Move-Item -LiteralPath $jdk.FullName -Destination $javaHome
    $asset | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 (Join-Path $Destination 'java25-manifest.json')
    Remove-Item -LiteralPath $zip
    if (@(Get-ChildItem $extract -Force).Count -eq 0) { Remove-Item -LiteralPath $extract }
    Write-Host ('JavaHome: ' + $javaHome)
}
if (-not $OnlyJava) {
    $version = 'v24.22.0'; $name = "node-$version-win-x64.zip"
    $nodeHome = Join-Path $Destination "node-$version-win-x64"
    if (Test-Path (Join-Path $nodeHome 'node.exe')) {
        $existing = (& (Join-Path $nodeHome 'node.exe') --version)
        if ($existing -ne $version) { throw 'La carpeta Node contiene otra version.' }
        Write-Host ('Se conserva Node: ' + $nodeHome)
        return
    }
    if (Test-Path $nodeHome) { throw 'La carpeta Node existe incompleta; revisala antes de continuar.' }
    $base = "https://nodejs.org/dist/$version/"
    $sums = [string](Invoke-WebRequest -UseBasicParsing -Uri ($base + 'SHASUMS256.txt')).Content
    $line = @($sums -split "`n" | Where-Object { $_.Trim().EndsWith($name) })[0]
    if (-not $line) { throw 'No se encontro la huella oficial de Node.' }
    $expected = ($line.Trim() -split '\s+')[0]; $zip = Join-Path $Destination $name
    Get-CheckedZip ($base+$name) $expected $zip
    Expand-Archive -LiteralPath $zip -DestinationPath $Destination
    Remove-Item -LiteralPath $zip
    Write-Host ('NodeHome: ' + (Join-Path $Destination "node-$version-win-x64"))
}
Write-Host 'No se modifico PATH, JAVA_HOME, servicios ni el registro de Windows.'
