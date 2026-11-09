[CmdletBinding()]
param([string]$Root='D:\SIGER-A')
# Genera una sola vez el par Ed25519 del comprobante institucional (ACK) en config\. No sobrescribe ni imprime la privada.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$s=Read-Settings $Root
Require-Java25 $s.javaHome
$config=Join-Path $Root 'config'
$private=Join-Path $config 'formulario-ack-private.pem';$public=Join-Path $config 'formulario-ack-public.pem'
if((Test-Path -LiteralPath $private) -or (Test-Path -LiteralPath $public)){throw 'Ya existe un par de claves ACK. No se reemplaza: rotarlo exige coordinar la confianza ACK con el Formulario y no tener ACK pendientes.'}
& (Join-Path $s.javaHome 'bin\java.exe') (Join-Path $PSScriptRoot 'FormularioAckKey.java') $private $public
if($LASTEXITCODE -ne 0){throw 'No se genero el par de claves ACK.'}
Require-PrivateFile $private
Write-Host "Clave publica para la confianza ACK del Formulario: $public"
Write-Host 'La clave privada queda solo en config\ (permisos heredados de la raiz privada). No la copies ni la publiques.'
