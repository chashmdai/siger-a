# Lanzador pequeno de Maven fijado, verificado contra SHA-512 oficial.
# Solo modifica .tools del proyecto; no configura variables globales.
$ErrorActionPreference = 'Stop'
$project = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $project 'scripts\Hash.ps1')
$tools = Join-Path $project '.tools'
$mavenHome = Join-Path $tools 'apache-maven-3.9.11'
if (-not (Test-Path (Join-Path $mavenHome 'bin\mvn.cmd'))) {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
    $zip = Join-Path $tools 'maven-3.9.11.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
    $expected = ([string](Invoke-WebRequest -UseBasicParsing -Uri ($url + '.sha512')).Content).Trim().Split([char[]]" `t`r`n",[StringSplitOptions]::RemoveEmptyEntries)[0]
    if ($expected -notmatch '^[a-fA-F0-9]{128}$' -or (Get-SigerFileHash $zip -Algorithm SHA512).Hash -ine $expected) { throw 'SHA-512 de Maven incorrecto.' }
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
    Remove-Item -LiteralPath $zip
}
& (Join-Path $mavenHome 'bin\mvn.cmd') @args
exit $LASTEXITCODE
