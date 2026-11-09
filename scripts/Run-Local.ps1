[CmdletBinding()]
param([string]$JavaHome,[int]$Port=18543)
. (Join-Path $PSScriptRoot 'Common.ps1')
$project=Split-Path $PSScriptRoot -Parent
if(-not $JavaHome){$JavaHome=Join-Path $project '.tools\java25'}
Require-Java25 $JavaHome
Free-Port $Port
$jar=Join-Path $project 'backend\target\siger-a.jar'
if(-not(Test-Path $jar)){throw 'Compila primero con Build.ps1.'}
New-Item -ItemType Directory -Force -Path (Join-Path $project 'var\tmp') | Out-Null
# H2 abre en solo lectura, sin avisar, un archivo que esta cuenta no puede escribir (por ejemplo, creado
# desde una consola de administrador). Se verifica antes de iniciar; solo se abre el archivo, no se modifica.
$localDb=Join-Path $project 'var\local-db.mv.db'
if(Test-Path $localDb){
  try{$probe=[IO.File]::Open($localDb,'Open','ReadWrite','ReadWrite');$probe.Close()}
  catch{throw ("Esta consola no puede escribir {0}. Fue creada con otros permisos (por ejemplo, desde una consola de administrador). Ejecuta Run-Local con la misma elevacion con que se creo, o concede Modificar a tu cuenta sobre la carpeta var. No se inicio SIGER A." -f $localDb)}
}
Push-Location $project
try { & (Join-Path $JavaHome 'bin\java.exe') '-Xms128m' '-Xmx1024m' '-Djava.io.tmpdir=./var/tmp' '-jar' $jar '--spring.profiles.active=local' "--server.port=$Port" }
finally { Pop-Location }
