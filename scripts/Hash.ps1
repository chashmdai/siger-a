# Compatible con Windows PowerShell 5.1 y PowerShell 7. Sin dependencia de Get-FileHash.
function Get-SigerFileHash {
    [CmdletBinding()]
    param([Parameter(Mandatory=$true,Position=0)][Alias('LiteralPath')][string]$Path,[ValidateSet('SHA256','SHA512')][string]$Algorithm='SHA256')
    $resolved=(Resolve-Path -LiteralPath $Path -ErrorAction Stop).ProviderPath
    $hash=if($Algorithm -eq 'SHA512'){[Security.Cryptography.SHA512]::Create()}else{[Security.Cryptography.SHA256]::Create()}
    $stream=$null
    try {
        $stream=[IO.File]::Open($resolved,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
        $digest=[BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','')
        [PSCustomObject]@{Algorithm=$Algorithm;Hash=$digest;Path=$resolved}
    } finally {if($stream){$stream.Dispose()};$hash.Dispose()}
}
