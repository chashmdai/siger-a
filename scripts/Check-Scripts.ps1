[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Hash.ps1')
$project=Split-Path $PSScriptRoot -Parent
$files=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File)
$files+=Get-Item -LiteralPath (Join-Path $project '.mvn\wrapper\maven-wrapper.ps1')
# Fuentes auxiliares que se compilan en la VM: sin caracteres de control (no se parsean como PowerShell).
foreach($source in @(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {$_.Extension -in @('.cs','.java')})){
    $bytes=[IO.File]::ReadAllBytes($source.FullName)
    for($i=0;$i -lt $bytes.Length;$i++){if($bytes[$i] -lt 32 -and $bytes[$i] -notin @(9,10,13)){throw ('Caracter de control en '+$source.Name+' (byte '+$i+').')}}
}
foreach($file in $files){
    # Un caracter de control invisible (p. ej. BEL en 'run\app.pid') cambia rutas sin error de sintaxis.
    $bytes=[IO.File]::ReadAllBytes($file.FullName)
    for($i=0;$i -lt $bytes.Length;$i++){if($bytes[$i] -lt 32 -and $bytes[$i] -notin @(9,10,13)){throw ('Caracter de control en '+$file.Name+' (byte '+$i+').')}}
    $tokens=$null; $parseErrors=$null
    $null=[System.Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$parseErrors)
    if($parseErrors.Count -gt 0){throw ('Script no valido: '+$file.Name+' - '+(($parseErrors | ForEach-Object {$_.Message}) -join '; '))}
}
# Comprobar nuestra funcion de hash sin depender de modulos opcionales de PowerShell.
$temp=[IO.Path]::GetTempFileName()
try{
    [IO.File]::WriteAllBytes($temp,[byte[]]@())
    $sha256=(Get-SigerFileHash -LiteralPath $temp -Algorithm SHA256).Hash
    $sha512=(Get-SigerFileHash -LiteralPath $temp -Algorithm SHA512).Hash
    if($sha256 -ine 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' -or $sha512 -ine 'cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e'){throw 'La comprobacion .NET SHA-256/SHA-512 fallo.'}
}finally{[IO.File]::Delete($temp)}
Write-Host ('Sintaxis PowerShell y hash .NET correctos: '+$files.Count+' scripts. No se modifico la configuracion del equipo.')
