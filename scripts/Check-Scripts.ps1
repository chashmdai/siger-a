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
# Regresion del modo de instancia: solo INSTITUCIONAL, PRUEBAS o un descriptor antiguo de pruebas; todo lo demas falla cerrado.
. (Join-Path $PSScriptRoot 'Environment.ps1')
$cases=@(
    @{settings=[PSCustomObject]@{namespace='SIGER-A-2026'};expected='PRUEBAS'},
    @{settings=[PSCustomObject]@{mode='PRUEBAS';ownerSid='S-1-5-21-1'};expected='PRUEBAS'},
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL';ownerSid='S-1-5-21-1'};expected='INSTITUCIONAL'},
    @{settings=[PSCustomObject]@{mode='institucional'};expected=$null},
    @{settings=[PSCustomObject]@{mode='PRODUCCION'};expected=$null},
    @{settings=[PSCustomObject]@{mode=''};expected=$null},
    @{settings=[PSCustomObject]@{mode=$null};expected=$null},
    @{settings=[PSCustomObject]@{ownerSid='S-1-5-21-1'};expected=$null})
foreach($case in $cases){
    $got=$null;try{$got=Get-InstanceMode $case.settings}catch{$got=$null}
    if($got -ne $case.expected){throw ('Modo de instancia: se esperaba '+$(if($case.expected){$case.expected}else{'rechazo'})+' y se obtuvo '+$(if($got){$got}else{'rechazo'})+' para '+($case.settings|ConvertTo-Json -Compress)+'.')}
}
# Proposito institucional: la recepcion real exige PRODUCCION o una VALIDACION expresamente autorizada.
$purposes=@(
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL';purpose='VALIDACION'};expected='VALIDACION';pull=$false},
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL';purpose='VALIDACION';validacionPullAutorizada=$false};expected='VALIDACION';pull=$false},
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL';purpose='VALIDACION';validacionPullAutorizada=$true};expected='VALIDACION';pull=$true},
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL';purpose='VALIDACION';validacionPullAutorizada='true'};expected='VALIDACION';pull=$false},
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL';purpose='PRODUCCION'};expected='PRODUCCION';pull=$true},
    @{settings=[PSCustomObject]@{mode='PRUEBAS';purpose='PRODUCCION';validacionPullAutorizada=$true};expected='PRUEBAS';pull=$false},
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL'};expected=$null;pull=$false},
    @{settings=[PSCustomObject]@{mode='INSTITUCIONAL';purpose='produccion'};expected=$null;pull=$false})
foreach($case in $purposes){
    $got=$null;try{$got=Get-InstancePurpose $case.settings}catch{$got=$null}
    $pull=$true;try{Require-ProductionPull $case.settings}catch{$pull=$false}
    if($got -ne $case.expected -or $pull -ne $case.pull){throw ('Proposito de instancia inesperado para '+($case.settings|ConvertTo-Json -Compress)+'.')}
}
# Un modo desconocido detiene Set-SigerEnvironment antes de definir variables del proceso.
try{Set-SigerEnvironment ([PSCustomObject]@{mode='OTRO';root='X:\no-existe';mysqlPort=1;webPort=2;database='siger_a_x'}) $null;throw 'Set-SigerEnvironment acepto un modo desconocido.'}
catch{if($_.Exception.Message -like 'Set-SigerEnvironment acepto*'){throw}}
if($env:SPRING_PROFILES_ACTIVE -or $env:SIGER_DB_URL){Clear-SigerEnvironment;throw 'Un modo desconocido dejo variables del proceso definidas.'}
# Credencial Graph (Import-GraphCredential.ps1) y destinatario de la prueba controlada (Test-GraphMail.ps1): mismas reglas
# que GraphMail.java y GraphCheck.java; cada condicion tiene un caso que se rechaza.
$tenant='11111111-2222-4333-8444-555555555555';$clientId='aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';$sender='siger@defensorianinez.cl'
$secretValue='secreto-sintetico-de-prueba-0123456789';$domainList=@('defensorianinez.cl')
$graph=@(
    @{args=@($tenant,$clientId,$sender,$secretValue,$domainList);ok=$true},
    @{args=@('no-guid',$clientId,$sender,$secretValue,$domainList);ok=$false},
    @{args=@($tenant,'aaaaaaaa-bbbb-4ccc-8ddd',$sender,$secretValue,$domainList);ok=$false},
    @{args=@($tenant,$clientId,'sin-arroba',$secretValue,$domainList);ok=$false},
    @{args=@($tenant,$clientId,$sender,'corto',$domainList);ok=$false},
    @{args=@($tenant,$clientId,$sender,('x'*513),$domainList);ok=$false},
    @{args=@($tenant,$clientId,$sender,($secretValue+[char]10),$domainList);ok=$false},
    @{args=@($tenant,$clientId,$sender,$secretValue,@());ok=$false},
    @{args=@($tenant,$clientId,$sender,$secretValue,@('@defensorianinez.cl'));ok=$false})
foreach($case in $graph){
    $values=$case.args;$got=$true;try{Assert-GraphCredential @values}catch{$got=$false}
    if($got -ne $case.ok){throw ('Credencial Graph: se esperaba '+$(if($case.ok){'aceptar'}else{'rechazar'})+' el caso '+[array]::IndexOf($graph,$case)+'.')}
}
$recipients=@(@{to='persona@defensorianinez.cl';ok=$true},@{to='Persona@DefensoriaNinez.cl';ok=$true},@{to='alguien@example.com';ok=$false},
    @{to='no es correo';ok=$false},@{to='';ok=$false},@{to='persona@defensorianinez.cl.example.com';ok=$false})
foreach($case in $recipients){if((Test-GraphRecipient $case.to $domainList) -ne $case.ok){throw ('Destinatario de la prueba Graph inesperado: caso '+[array]::IndexOf($recipients,$case)+'.')}}
# Cierre de la validacion (Close-Validation.ps1, D11): un caso que se rechaza por cada condicion, los casos validos y
# cifras ilegibles. Orden de las cifras: ver $script:ValidationClosureFields en Environment.ps1.
$valid="9`t0`t0`t1`t0`t9`t0`t30`t1`t1765000000000`t0`t0"
$none="0`t0`t0`t0`t0`t0`t0`t12`t0`t-1`t0`t0"
$rejectedOnly="0`t0`t0`t2`t0`t0`t0`t12`t1`t1765000000000`t0`t0"
$closure=@(
    @{purpose='VALIDACION';figures=$valid;declared=9;expected=''},
    @{purpose='VALIDACION';figures=$none;declared=$null;expected=''},
    @{purpose='VALIDACION';figures=$none;declared=0;expected=''},
    @{purpose='VALIDACION';figures=$rejectedOnly;declared=0;expected=''},
    @{purpose='PRODUCCION';figures=$valid;declared=9;expected='PROPOSITO'},
    @{purpose='PRUEBAS';figures=$none;declared=$null;expected='PROPOSITO'},
    @{purpose='VALIDACION';figures=$none;declared=3;expected='CANTIDAD_DISTINTA'},
    @{purpose='VALIDACION';figures=$valid;declared=$null;expected='SIN_DECLARACION'},
    @{purpose='VALIDACION';figures=$rejectedOnly;declared=$null;expected='SIN_DECLARACION'},
    @{purpose='VALIDACION';figures=$valid;declared=10;expected='CANTIDAD_DISTINTA'},
    @{purpose='VALIDACION';figures=$valid;declared=8;expected='CANTIDAD_DISTINTA'},
    @{purpose='VALIDACION';figures="8`t1`t1`t0`t0`t9`t0`t30`t1`t1765000000000`t0`t0";declared=8;expected='REGISTRADA,ACK_PENDIENTE'},
    @{purpose='VALIDACION';figures="9`t0`t1`t0`t0`t9`t0`t30`t1`t1765000000000`t0`t0";declared=9;expected='ACK_PENDIENTE'},
    @{purpose='VALIDACION';figures="9`t0`t0`t0`t1`t9`t0`t30`t1`t1765000000000`t0`t0";declared=9;expected='ESTADO_DESCONOCIDO'},
    @{purpose='VALIDACION';figures="9`t0`t0`t0`t0`t10`t1`t30`t1`t1765000000000`t0`t0";declared=9;expected='COM_EXTERNA_SIN_RECEPCION'},
    @{purpose='VALIDACION';figures="0`t0`t0`t0`t0`t1`t1`t30`t1`t1765000000000`t0`t0";declared=0;expected='COM_EXTERNA_SIN_RECEPCION'},
    @{purpose='VALIDACION';figures="9`t0`t0`t1`t0`t9`t0`t30`t0`t-1`t0`t0";declared=9;expected='SIN_PAUSA'},
    @{purpose='VALIDACION';figures="0`t0`t0`t2`t0`t0`t0`t12`t0`t-1`t0`t0";declared=0;expected='SIN_PAUSA'},
    @{purpose='VALIDACION';figures="9`t0`t0`t1`t0`t9`t0`t30`t1`t1765000000000`t1`t0";declared=9;expected='POSTERIOR_A_LA_PAUSA'},
    @{purpose='VALIDACION';figures="9`t0`t0`t1`t0`t9`t0`t30`t1`t1765000000000`t0`t1";declared=9;expected='POSTERIOR_A_LA_PAUSA'},
    @{purpose='VALIDACION';figures="0`t0`t0`t2`t0`t0`t0`t12`t1`t1765000000000`t1`t0";declared=0;expected='POSTERIOR_A_LA_PAUSA'})
foreach($case in $closure){
    $got=(@(Get-ValidationClosureBlockers $case.purpose (ConvertTo-ValidationClosureFigures $case.figures) $case.declared)) -join ','
    if($got -cne $case.expected){throw ('Cierre de la validacion: caso '+[array]::IndexOf($closure,$case)+' esperaba "'+$case.expected+'" y obtuvo "'+$got+'".')}
}
$unreadable=@("9`t0`t0`t1`t0`t9`t0`t30`t1`t1765000000000`t0","9`t0`t0`t1`t0`t9`t0`t30`t1`t1765000000000`t0`tx",'',
    "-1`t0`t0`t0`t0`t0`t0`t12`t0`t-1`t0`t0","9`t0`t0`t1`t0`t9`t0`t30`t1`t-1`t0`t0","9`t0`t0`t1`t0`t9`t0`t30`t0`t1765000000000`t0`t0",
    "9`t0`t0`t1`t0`t9`t0`t30`t2`t1765000000000`t0`t0","9 0 0 1 0 9 0 30 1 1765000000000 0 0")
foreach($text in $unreadable){
    $read=$true;try{$null=ConvertTo-ValidationClosureFigures $text}catch{$read=$false}
    if($read){throw ('Cierre de la validacion: se acepto la cifra ilegible del caso '+[array]::IndexOf($unreadable,$text)+'.')}
}
# Archivos temporales con credenciales (hallazgo de la revision de H02): con -WhatIf, $WhatIfPreference se propaga y
# Remove-Item no borra. Remove-SecretFile borra igual; el control negativo acredita que la prueba detecta el problema.
$onWindows=[Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$scratch=Join-Path ([IO.Path]::GetTempPath()) ('siger-check-'+[Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($scratch)
function Test-SecretCleanup {
    [CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
    param([string]$Path,[switch]$WithRemoveItem)
    [IO.File]::WriteAllText($Path,'password=sintetica',[Text.Encoding]::ASCII)
    if($WithRemoveItem){Remove-Item -LiteralPath $Path}else{Remove-SecretFile $Path}
}
$cnf=Join-Path $scratch 'client-prueba.cnf'
Test-SecretCleanup -Path $cnf -WhatIf
if([IO.File]::Exists($cnf)){throw 'Remove-SecretFile no borro el archivo de credenciales con -WhatIf.'}
Test-SecretCleanup -Path $cnf -WithRemoveItem -WhatIf
if(-not [IO.File]::Exists($cnf)){throw 'Control negativo: Remove-Item con -WhatIf deberia dejar el archivo; la prueba ya no detecta el problema.'}
[IO.File]::Delete($cnf)
# Ningun Remove-Item sobre variables de archivos con credenciales (directo o por un foreach sobre ellas): los pendientes
# de H04 se corrigieron en H04a con Remove-SecretFile y ya no se admite ninguno.
$secretVars=@('client','init','passFile','src','dst','candidate')
$isVar={param($a) $a -is [System.Management.Automation.Language.VariableExpressionAst]}
foreach($file in $files){
    $ast=[System.Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$null,[ref]$null);$count=0
    foreach($cmd in $ast.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ieq 'Remove-Item'},$true)){
        $names=@($cmd.FindAll($isVar,$true)|ForEach-Object {$_.VariablePath.UserPath})
        for($p=$cmd.Parent;$null -ne $p;$p=$p.Parent){
            if($p -is [System.Management.Automation.Language.ForEachStatementAst] -and $names -contains $p.Variable.VariablePath.UserPath){$names+=@($p.Condition.FindAll($isVar,$true)|ForEach-Object {$_.VariablePath.UserPath})}
        }
        if(@($names|Where-Object {$secretVars -contains $_}).Count -gt 0){$count++}
    }
    if($count -ne 0){throw ("Archivos con credenciales: $($file.Name) tiene $count Remove-Item sobre "+($secretVars -join ', ')+'. Usa Remove-SecretFile (Common.ps1).')}
}
# Eliminacion de la validacion (Remove-Validation.ps1, D12): un rechazo por condicion y los casos validos.
function Merge-Case($Base,$Override){$h=@{};foreach($k in $Base.Keys){$h[$k]=$Base[$k]};foreach($k in $Override.Keys){$h[$k]=$Override[$k]};return [PSCustomObject]$h}
$protectedCase=[ordered]@{PRODUCCION='D:\SIGER';CONSTANCIAS='D:\SIGER-CONSTANCIAS';PAQUETE='D:\Entregas\SIGER-FECHA';MYSQLHOME_VALIDACION='D:\Herramientas\mysql-8.4';JAVAHOME_PRODUCCION='D:\SIGER\releases\initial\runtime\java25'}
$pathCases=@(
    @{t='D:\SIGER-VALIDACION';o=@();e=''},@{t='D:\Respaldos-SIGER-VALIDACION';o=@('D:\SIGER-VALIDACION');e=''},@{t='d:\siger-validacion\';o=@();e=''},
    @{t='C:\SIGER-VALIDACION';o=@();e='FUERA_DE_D'},@{t='D:\';o=@();e='FUERA_DE_D'},@{t='D:';o=@();e='FUERA_DE_D'},@{t='\\servidor\SIGER-VALIDACION';o=@();e='FUERA_DE_D'},
    @{t='D:\SIGER-VALIDACION\..\SIGER';o=@();e='FUERA_DE_D'},@{t='D:\SIGER-VALIDACION/x';o=@();e='FUERA_DE_D'},
    @{t='D:\SIGER';o=@();e='PROTEGIDA_PRODUCCION,PROTEGIDA_JAVAHOME_PRODUCCION'},@{t='d:\siger\data';o=@();e='PROTEGIDA_PRODUCCION'},
    @{t='D:\Herramientas';o=@();e='PROTEGIDA_MYSQLHOME_VALIDACION'},@{t='D:\Herramientas\mysql-8.4\data';o=@();e='PROTEGIDA_MYSQLHOME_VALIDACION'},
    @{t='D:\SIGER-CONSTANCIAS';o=@();e='PROTEGIDA_CONSTANCIAS'},@{t='D:\SIGER-CONSTANCIAS\x';o=@();e='PROTEGIDA_CONSTANCIAS'},@{t='D:\Entregas';o=@();e='PROTEGIDA_PAQUETE'},
    @{t='D:\Respaldos-SIGER-VALIDACION\a';o=@('D:\Respaldos-SIGER-VALIDACION');e='SOLAPADA'},@{t='D:\Respaldos-SIGER-VALIDACION';o=@('D:\Respaldos-SIGER-VALIDACION\a');e='SOLAPADA'})
foreach($case in $pathCases){
    $got=(@(Get-DeletionPathBlockers $case.t $protectedCase $case.o)) -join ','
    if($got -cne $case.e){throw ('Rutas de la eliminacion: caso '+[array]::IndexOf($pathCases,$case)+' esperaba "'+$case.e+'" y obtuvo "'+$got+'".')}
}
$vRoot='D:\SIGER-VALIDACION';$vBase='siger_a_inst';$vSid='S-1-5-21-1000'
$backupCases=@(
    @{m=[PSCustomObject]@{namespace='SIGER-A-2026';sourceRoot='D:\SIGER-VALIDACION';database='siger_a_inst';complete=$true};ok=$true},
    @{m=[PSCustomObject]@{namespace='SIGER-A-2026';sourceRoot='d:\siger-validacion\';database='siger_a_inst'};ok=$true},
    @{m=[PSCustomObject]@{namespace='OTRO';sourceRoot='D:\SIGER-VALIDACION';database='siger_a_inst'};ok=$false},
    @{m=[PSCustomObject]@{namespace='SIGER-A-2026';sourceRoot='D:\SIGER';database='siger_a_inst'};ok=$false},
    @{m=[PSCustomObject]@{namespace='SIGER-A-2026';sourceRoot='D:\SIGER-VALIDACION-2';database='siger_a_inst'};ok=$false},
    @{m=[PSCustomObject]@{namespace='SIGER-A-2026';sourceRoot='D:\SIGER-VALIDACION';database='siger_a_lab'};ok=$false},
    @{m=[PSCustomObject]@{namespace='SIGER-A-2026';database='siger_a_inst'};ok=$false},@{m=$null;ok=$false})
foreach($case in $backupCases){if((Test-BackupOfInstance $case.m $vRoot $vBase) -ne $case.ok){throw ('Respaldo de la instancia: caso '+[array]::IndexOf($backupCases,$case)+' inesperado.')}}
$good=[PSCustomObject]@{Path='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109-v2';Meta=$backupCases[0].m}
$abort=[PSCustomObject]@{Path='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';Meta=$null}
$containerCases=@(
    @{c=@($good);d=@();ok=$true},@{c=@();d=@();ok=$true},
    @{c=@($good,$abort);d=@('D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109');ok=$true},
    @{c=@($good,$abort);d=@();ok=$false},
    @{c=@($good,$abort);d=@('D:\Respaldos-SIGER-VALIDACION\otro');ok=$false},
    @{c=@($good,[PSCustomObject]@{Path='D:\Respaldos-SIGER-VALIDACION\nota.txt';Meta=$null});d=@();ok=$false},
    @{c=@($good,[PSCustomObject]@{Path='D:\Respaldos-SIGER-VALIDACION\de-produccion';Meta=$backupCases[3].m});d=@();ok=$false})
foreach($case in $containerCases){if((Test-BackupContainer $case.c $vRoot $vBase $case.d) -ne $case.ok){throw ('Carpeta de respaldos: caso '+[array]::IndexOf($containerCases,$case)+' inesperado.')}}
$incCases=@(
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$false;l=0;h=$false;i=$null;e=''},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$false;l=0;h=$true;i=[PSCustomObject]@{root='d:\siger-validacion';ownerSid=$vSid};e=''},
    @{p='D:\Otra\previo-pull-20261109';b=$false;r=$false;l=0;h=$false;i=$null;e='NO_ES_HIJO_DE_RESPALDOS'},
    @{p='D:\Respaldos-SIGER-VALIDACION\a\previo-pull-20261109';b=$false;r=$false;l=0;h=$false;i=$null;e='NO_ES_HIJO_DE_RESPALDOS'},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$true;r=$false;l=0;h=$false;i=$null;e='CON_BACKUP_JSON'},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$true;l=0;h=$false;i=$null;e='CON_RESTORE_RESULT'},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$false;l=1;h=$false;i=$null;e='CON_ENLACES'},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$false;l=0;h=$true;i=[PSCustomObject]@{root='D:\SIGER';ownerSid=$vSid};e='INSTANCIA_AJENA'},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$false;l=0;h=$true;i=[PSCustomObject]@{root=$vRoot;ownerSid='S-1-5-21-2000'};e='INSTANCIA_AJENA'},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$false;l=0;h=$true;i=[PSCustomObject]@{root=$vRoot};e='INSTANCIA_AJENA'},
    @{p='D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109';b=$false;r=$false;l=0;h=$true;i=$null;e='INSTANCIA_AJENA'})
foreach($case in $incCases){
    $got=(@(Get-IncompleteBackupBlockers $case.p @('D:\Respaldos-SIGER-VALIDACION') $case.b $case.r $case.l $case.h $case.i $vRoot $vSid)) -join ','
    if($got -cne $case.e){throw ('Respaldo incompleto declarado: caso '+[array]::IndexOf($incCases,$case)+' esperaba "'+$case.e+'" y obtuvo "'+$got+'".')}
}
$restoreCases=@(
    @{r=[PSCustomObject]@{namespace='SIGER-A-RESTORE-CHECK';completed=$true;schema='siger_a_restore_20261109120000_a1b2c3'};e='siger_a_restore_20261109120000_a1b2c3'},
    @{r=[PSCustomObject]@{completed=$false;schema='siger_a_restore_20261109120000_a1b2c3'};e='siger_a_restore_20261109120000_a1b2c3'},
    @{r=[PSCustomObject]@{completed=$true;schema='siger_a_restore_20261109120000_a1b2c3'};e=''},
    @{r=[PSCustomObject]@{namespace='OTRO';completed=$true;schema='siger_a_restore_20261109120000_a1b2c3'};e=''},
    @{r=[PSCustomObject]@{namespace='SIGER-A-RESTORE-CHECK';completed=$true;schema='siger_a_inst'};e=''},
    @{r=[PSCustomObject]@{namespace='SIGER-A-RESTORE-CHECK';completed=$true;schema='siger_a_restore_20261109120000_a1b2c3\..\x'};e=''},
    @{r=$null;e=''})
foreach($case in $restoreCases){if((Get-RestoreSchema $case.r) -cne $case.e){throw ('Ensayo de restauracion: caso '+[array]::IndexOf($restoreCases,$case)+' inesperado.')}}
$okFacts=@{Purpose='VALIDACION';Root=$vRoot;Marker=[PSCustomObject]@{kind='CIERRE_VALIDACION';root=$vRoot};MarkerCopy=$true;OwnerMatch=$true;ServiceJson=$false;ServicesForRoot=0
    EngineActive=$false;InUse=0;Links=0;Production=[PSCustomObject]@{Purpose='PRODUCCION';Initialized=$true;Root='D:\SIGER';OwnerMatch=$true}
    FormularioValidation=[PSCustomObject]@{Origin='https://formulario.example.cl';AckKeyId='smr-ack-1'};FormularioProduction=[PSCustomObject]@{Mode='AUTOMATICO';Origin='https://formulario.example.cl';AckKeyId='smr-ack-1'}
    GraphValidation=$false;GraphProduction=$false;PreviousOmitted=0}
$deletion=@(
    @{o=@{};e=''},@{o=@{GraphValidation=$true;GraphProduction=$true};e=''},@{o=@{GraphProduction=$true};e=''},
    @{o=@{FormularioProduction=[PSCustomObject]@{Mode='AUTOMATICO';Origin='https://Formulario.example.cl/';AckKeyId='smr-ack-1'}};e=''},
    @{o=@{Purpose='PRODUCCION'};e='PROPOSITO'},@{o=@{Purpose=''};e='PROPOSITO'},
    @{o=@{Marker=$null};e='SIN_MARCADOR'},@{o=@{Marker=[PSCustomObject]@{closedUtc='2026-12-04T12:00:00Z';root=$vRoot}};e='MARCADOR_SIN_TIPO'},
    @{o=@{Marker=[PSCustomObject]@{kind='CIERRE_VALIDACION';root='D:\SIGER'}};e='MARCADOR_OTRA_RAIZ'},@{o=@{MarkerCopy=$false};e='SIN_COPIA_DEL_CIERRE'},
    @{o=@{OwnerMatch=$false};e='CUENTA'},@{o=@{ServiceJson=$true};e='SERVICIOS_REGISTRADOS'},@{o=@{ServicesForRoot=2};e='SERVICIOS_REGISTRADOS'},
    @{o=@{EngineActive=$true};e='MOTOR_ACTIVO'},@{o=@{InUse=1};e='ARCHIVOS_EN_USO'},@{o=@{Links=1};e='ENLACES'},
    @{o=@{Production=$null};e='PRODUCCION'},@{o=@{Production=[PSCustomObject]@{Purpose='PRODUCCION';Initialized=$false;Root='D:\SIGER';OwnerMatch=$true}};e='PRODUCCION'},
    @{o=@{Production=[PSCustomObject]@{Purpose='VALIDACION';Initialized=$true;Root='D:\SIGER';OwnerMatch=$true}};e='PRODUCCION'},
    @{o=@{Production=[PSCustomObject]@{Purpose='PRODUCCION';Initialized=$true;Root='D:\SIGER';OwnerMatch=$false}};e='PRODUCCION'},
    @{o=@{Production=[PSCustomObject]@{Purpose='PRODUCCION';Initialized=$true;Root=$vRoot;OwnerMatch=$true}};e='PRODUCCION'},
    @{o=@{Production=[PSCustomObject]@{Purpose='PRODUCCION';Initialized=$true;Root='D:\SIGER-VALIDACION\prod';OwnerMatch=$true}};e='PRODUCCION'},
    @{o=@{FormularioValidation=$null};e='FORMULARIO_VALIDACION'},@{o=@{FormularioProduction=$null};e='FORMULARIO_PRODUCCION'},
    @{o=@{FormularioProduction=[PSCustomObject]@{Mode='MANUAL';Origin='https://formulario.example.cl';AckKeyId='smr-ack-1'}};e='FORMULARIO_NO_AUTOMATICO'},
    @{o=@{FormularioProduction=[PSCustomObject]@{Mode='AUTOMATICO';Origin='https://otro.example.cl';AckKeyId='smr-ack-1'}};e='FORMULARIO_ORIGEN_DISTINTO'},
    @{o=@{FormularioProduction=[PSCustomObject]@{Mode='AUTOMATICO';Origin='https://formulario.example.cl';AckKeyId='nueva-ack'}};e='FORMULARIO_ACK_DISTINTO'},
    @{o=@{FormularioProduction=[PSCustomObject]@{Mode='AUTOMATICO';Origin='https://formulario.example.cl';AckKeyId='SMR-ACK-1'}};e='FORMULARIO_ACK_DISTINTO'},
    @{o=@{GraphValidation=$true};e='GRAPH_PRODUCCION'},@{o=@{PreviousOmitted=1};e='PENDIENTE_ANTERIOR'})
foreach($case in $deletion){
    $got=(@(Get-ValidationDeletionBlockers (Merge-Case $okFacts $case.o))) -join ','
    if($got -cne $case.e){throw ('Eliminacion de la validacion: caso '+[array]::IndexOf($deletion,$case)+' esperaba "'+$case.e+'" y obtuvo "'+$got+'".')}
}
# Inventario, constancia y motor de borrado sobre una carpeta temporal: enlace y archivo en uso detectados; constancia
# escrita antes de borrar (si ya existe, no se borra nada) y con su lista cerrada de campos; fallo con NO_INTENTADO y
# reintento con lo que queda que termina y referencia a la constancia anterior.
function New-CheckLink([string]$Path,[string]$Target){
    if($onWindows){New-Item -ItemType Junction -Path $Path -Value $Target|Out-Null}else{New-Item -ItemType SymbolicLink -Path $Path -Target $Target|Out-Null}
}
function Write-CheckFile([string]$Path,[string]$Text){[void][IO.Directory]::CreateDirectory((Split-Path $Path -Parent));[IO.File]::WriteAllText($Path,$Text,[Text.Encoding]::ASCII)}
try{
    $outside=Join-Path $scratch 'fuera';Write-CheckFile (Join-Path $outside 'no-tocar.txt') 'x'
    $val=Join-Path $scratch 'validacion';$ens=Join-Path $scratch 'ensayo';$resp=Join-Path $scratch 'respaldos';$inc=Join-Path $resp 'abortado';$const=Join-Path $scratch 'constancias'
    Write-CheckFile (Join-Path $val 'config\instance.json') '{}';Write-CheckFile (Join-Path $val 'config\piloto-cerrado.json') '{}'
    Write-CheckFile (Join-Path $val 'config\formulario-pull.json') '{}';Write-CheckFile (Join-Path $val 'config\secrets.clixml') 'clave-secreta-sintetica'
    Write-CheckFile (Join-Path $val 'data\documents\nombre-ciudadano-sintetico.bin') 'datos'
    Write-CheckFile (Join-Path $ens 'documents\d.bin') 'datos';Write-CheckFile (Join-Path $ens 'restore-result.json') '{}'
    Write-CheckFile (Join-Path $resp 'b1\backup.json') '{}';Write-CheckFile (Join-Path $resp 'b1\siger-a.sql') 'sql'
    Write-CheckFile (Join-Path $inc 'siger-a.sql') 'sql-parcial';[void][IO.Directory]::CreateDirectory($const)
    $inv=Get-TreeInventory $val
    if($inv.Files -ne 5 -or $inv.Links -ne 0 -or $inv.InUse -ne 0 -or $inv.Bytes -ne 34){throw 'Inventario: cifras inesperadas de la raiz.'}
    $held=[IO.File]::Open((Join-Path $val 'data\documents\nombre-ciudadano-sintetico.bin'),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
    try{if((Get-TreeInventory $val).InUse -ne 1){throw 'Inventario: no detecto el archivo en uso.'}}finally{$held.Dispose()}
    $link=Join-Path $resp 'enlace';New-CheckLink $link $outside
    $withLink=Get-TreeInventory $resp
    if($withLink.Links -ne 1 -or $withLink.Files -ne 3){throw 'Inventario: no detecto el enlace o lo siguio.'}
    $elementsCase=@(
        [PSCustomObject]@{Type='ENSAYO';Path=$ens;Files=2;Bytes=7;Evidence='esquema siger_a_restore_20261109120000_a1b2c3'},
        [PSCustomObject]@{Type='RESPALDO_INCOMPLETO_DECLARADO';Path=$inc;Files=1;Bytes=11;Evidence='declarado por el responsable; sin config-private\instance.json (aceptado por la declaracion)'},
        [PSCustomObject]@{Type='RESPALDO';Path=$resp;Files=2;Bytes=5;Evidence='1 respaldo(s) de esta validacion'},
        [PSCustomObject]@{Type='RAIZ';Path=$val;Files=5;Bytes=34;Evidence='instancia VALIDACION, base siger_a_inst'})
    $last=@('config\formulario-pull.json','config\instance.json','config\piloto-cerrado.json')
    $targetsCase=@($elementsCase|ForEach-Object {[PSCustomObject]@{Path=$_.Path;Last=$(if($_.Type -eq 'RAIZ'){$last}else{@()})}})
    $settingsCase=[PSCustomObject]@{root=$val;database='siger_a_inst';jarSha256='ab'*32}
    $closureCase=[PSCustomObject]@{file='cierre-validacion-20261204T120000Z.json';sha256='cd'*32;closedUtc='2026-12-04T12:00:00Z';communications=30;confirmed=9}
    $first=Join-Path $const 'eliminacion-validacion-20261210T120000Z.json'
    # Constancia previa imposible de escribir (ya existe): no se borra nada.
    Write-CheckFile $first 'ocupado'
    $blocked=$false;try{$null=Invoke-ValidationDeletion (New-ValidationDeletionRecord $settingsCase $closureCase 'D:\SIGER' $null $elementsCase 0 'Prueba' 'EQUIPO\svc-siger') $first $targetsCase}catch{$blocked=$true}
    if(-not $blocked -or -not [IO.Directory]::Exists($ens) -or -not [IO.Directory]::Exists($val)){throw 'Eliminacion: borro sin poder escribir antes la constancia.'}
    [IO.File]::Delete($first)
    $record=New-ValidationDeletionRecord $settingsCase $closureCase 'D:\SIGER' $null $elementsCase 0 'Prueba de eliminacion' 'EQUIPO\svc-siger'
    if((@($record.Keys) -join ',') -cne ($script:DeletionRecordFields -join ',') -or (@($record.elements[0].Keys) -join ',') -cne ($script:DeletionElementFields -join ',')){throw 'Constancia de eliminacion: campos distintos de la lista cerrada.'}
    $record=Invoke-ValidationDeletion $record $first $targetsCase
    $results=(@($record.elements|ForEach-Object {$_.result})) -join ','
    if($record.result -cne 'INCOMPLETA' -or $results -cne 'ELIMINADO,ELIMINADO,FALLIDO,NO_INTENTADO' -or $null -eq $record.elements[2].remainingFiles -or $record.elements[2].remainingFiles -gt 2){throw "Eliminacion con fallo: resultado inesperado ($($record.result): $results)."}
    if([IO.Directory]::Exists($ens) -or [IO.Directory]::Exists($inc) -or -not [IO.Directory]::Exists($val) -or -not [IO.File]::Exists((Join-Path $outside 'no-tocar.txt'))){throw 'Eliminacion con fallo: estado de las carpetas inesperado (o siguio el enlace).'}
    $saved=Get-Content -LiteralPath $first -Raw
    foreach($forbidden in @('clave-secreta-sintetica','nombre-ciudadano','siger-a.sql','d.bin','backup.json')){if($saved.Contains($forbidden)){throw "Constancia de eliminacion: contiene un nombre o contenido de archivo ($forbidden)."}}
    $previous=Get-PreviousDeletion $const $val
    if(-not $previous -or $previous.File -cne (Split-Path $first -Leaf) -or $previous.Sha256 -cne (Get-SigerFileHash -LiteralPath $first -Algorithm SHA256).Hash.ToLowerInvariant()){throw 'Reintento: no encontro la constancia anterior.'}
    $exists={param($p) [IO.Directory]::Exists($p)}
    if((Get-PreviousOmitted $previous @($val) $exists) -ne 1 -or (Get-PreviousOmitted $previous @($resp,$val) $exists) -ne 0){throw 'Reintento: no detecto un elemento pendiente omitido.'}
    [IO.Directory]::Delete($link)
    $rest=@($elementsCase[2],$elementsCase[3]);$restTargets=@($targetsCase[2],$targetsCase[3])
    $second=Join-Path $const 'eliminacion-validacion-20261210T130000Z.json'
    $again=Invoke-ValidationDeletion (New-ValidationDeletionRecord $settingsCase $closureCase 'D:\SIGER' $previous $rest 0 'Reintento' 'EQUIPO\svc-siger') $second $restTargets
    if($again.result -cne 'COMPLETA' -or (@($again.elements|ForEach-Object {$_.result}) -join ',') -cne 'ELIMINADO,ELIMINADO' -or $again.previous.sha256 -cne $previous.Sha256 -or $again.previous.file -cne $previous.File){throw 'Reintento: no termino o no referencia la constancia anterior.'}
    if([IO.Directory]::Exists($resp) -or [IO.Directory]::Exists($val) -or -not [IO.File]::Exists((Join-Path $outside 'no-tocar.txt'))){throw 'Reintento: quedaron carpetas o se borro algo fuera de las rutas.'}
    $complete=$false;try{$null=Get-PreviousDeletion $const $val}catch{$complete=$true}
    if(-not $complete){throw 'Reintento: una constancia COMPLETA de la misma raiz no detiene una nueva ejecucion.'}
}finally{
    foreach($candidateLink in @((Join-Path $scratch 'respaldos\enlace'))){if([IO.Directory]::Exists($candidateLink)){[IO.Directory]::Delete($candidateLink)}}
    if([IO.Directory]::Exists($scratch)){[IO.Directory]::Delete($scratch,$true)}
}
$deletionCount=$pathCases.Count+$backupCases.Count+$containerCases.Count+$incCases.Count+$restoreCases.Count+$deletion.Count
# --- Proceso propio acreditado por el servicio (App-Running y Require-OwnMySql, H04a) --------------------------------
# Un caso por condicion de Get-ServiceProcessBlockers y la decision de Test-AppProcess: si WMI oculta ruta y argumentos y
# la cadena no calza, se lanza el error; nunca se responde "no esta corriendo" (de eso dependen Backup, Restore-Check,
# Migrate, Update y Recover-Ti).
function New-ServiceFacts([string]$Kind,$Change){
    $f=[PSCustomObject]@{Kind=$Kind;ServiceSid='S-1-5-21-9';ExpectedSid='S-1-5-21-9';Host=$null
        Service=[PSCustomObject]@{State='Running';ProcessId=100;PathName='"D:\SIGER\service\SigerService.exe" "D:\SIGER"'}
        ExpectedPath='D:\SIGER\service\SigerService.exe D:\SIGER';Process=[PSCustomObject]@{Name='java.exe';ParentProcessId=200}}
    if($Kind -ceq 'APP'){$f.Host=[PSCustomObject]@{ProcessId=200;Name='powershell.exe';ParentProcessId=100}}
    if($Kind -ceq 'MYSQL'){
        $f.Service.PathName='"D:\Herramientas\mysql-8.4\bin\mysqld.exe" --defaults-file=D:\SIGER\mysql\my.ini SIGERMySQL'
        $f.ExpectedPath='D:\Herramientas\mysql-8.4\bin\mysqld.exe --defaults-file=D:\SIGER\mysql\my.ini SIGERMySQL'
        $f.Process=[PSCustomObject]@{Name='mysqld.exe';ParentProcessId=100}
    }
    if($Change){& $Change $f}
    return $f
}
$serviceCases=@(
    @{k='APP';c=$null;e=''},@{k='MYSQL';c=$null;e=''},
    @{k='APP';c={param($f) $f.Process.Name='JAVA.EXE';$f.Host.Name='PowerShell.exe'};e=''},
    @{k='APP';c={param($f) $f.Service=$null};e='SIN_SERVICIO'},
    @{k='APP';c={param($f) $f.Service.State='Stopped'};e='SERVICIO_DETENIDO'},
    @{k='APP';c={param($f) $f.Service.ProcessId=0;$f.Host.ParentProcessId=0};e='SERVICIO_DETENIDO'},
    @{k='APP';c={param($f) $f.ServiceSid=''};e='CUENTA'},
    @{k='APP';c={param($f) $f.ServiceSid='S-1-5-21-8'};e='CUENTA'},
    @{k='APP';c={param($f) $f.ExpectedSid=''};e='CUENTA'},
    @{k='APP';c={param($f) $f.Service.PathName='"D:\SIGER-VALIDACION\service\SigerService.exe" "D:\SIGER-VALIDACION"'};e='RUTA_SERVICIO'},
    @{k='APP';c={param($f) $f.ExpectedPath=''};e='RUTA_SERVICIO'},
    @{k='APP';c={param($f) $f.Process.Name='javaw.exe'};e='EJECUTABLE'},
    @{k='APP';c={param($f) $f.Process=$null};e='EJECUTABLE,CADENA'},
    @{k='APP';c={param($f) $f.Host=$null};e='CADENA'},
    @{k='APP';c={param($f) $f.Host.Name='cmd.exe'};e='CADENA'},
    @{k='APP';c={param($f) $f.Host.ParentProcessId=101};e='CADENA'},
    @{k='APP';c={param($f) $f.Host.ProcessId=201};e='CADENA'},
    @{k='MYSQL';c={param($f) $f.Process.ParentProcessId=101};e='CADENA'},
    @{k='MYSQL';c={param($f) $f.Process.Name='java.exe'};e='EJECUTABLE'},
    @{k='MYSQL';c={param($f) $f.Service.PathName='D:\Herramientas\mysql-8.4\bin\mysqld.exe --defaults-file=D:\OTRA\mysql\my.ini SIGERMySQL'};e='RUTA_SERVICIO'},
    @{k='OTRO';c=$null;e='TIPO'})
foreach($case in $serviceCases){
    $got=(@(Get-ServiceProcessBlockers (New-ServiceFacts $case.k $case.c)) -join ',')
    if($got -cne $case.e){throw ('Proceso por el servicio: caso '+[array]::IndexOf($serviceCases,$case)+' esperaba "'+$case.e+'" y obtuvo "'+$got+'".')}
}
$javaExe='D:\SIGER\releases\initial\runtime\java25\bin\java.exe';$jarPath='D:\SIGER\releases\initial\app\siger-a.jar'
$visible=[PSCustomObject]@{ExecutablePath=$javaExe;CommandLine="`"$javaExe`" -jar `"$jarPath`""}
$hidden=[PSCustomObject]@{ExecutablePath=$null;CommandLine=$null}
$partial=[PSCustomObject]@{ExecutablePath=$javaExe;CommandLine=''}
$okChain={@()};$badChain={@('CADENA')};$failChain={throw 'servicio no legible'};$notCalled={throw 'no debia consultar el servicio'}
$appCases=@(
    @{p=$null;x=$javaExe;m=$jarPath;s=$notCalled;e='FALSE'},
    @{p=$visible;x=$javaExe.ToUpperInvariant();m=$jarPath;s=$notCalled;e='TRUE'},
    @{p=$visible;x='D:\SIGER-VALIDACION\releases\initial\runtime\java25\bin\java.exe';m=$jarPath;s=$okChain;e='ERROR'},
    @{p=$visible;x=$javaExe;m='D:\SIGER-VALIDACION\releases\initial\app\siger-a.jar';s=$okChain;e='ERROR'},
    @{p=$hidden;x=$javaExe;m=$jarPath;s=$okChain;e='TRUE'},
    @{p=$hidden;x=$javaExe;m=$jarPath;s=$badChain;e='ERROR'},
    @{p=$hidden;x=$javaExe;m=$jarPath;s=$failChain;e='ERROR'},
    @{p=$partial;x=$javaExe;m=$jarPath;s=$okChain;e='TRUE'},
    @{p=$partial;x=$javaExe;m=$jarPath;s=$badChain;e='ERROR'})
foreach($case in $appCases){
    $got='ERROR';try{$got=if(Test-AppProcess $case.p $case.x $case.m $case.s){'TRUE'}else{'FALSE'}}catch{if($_.Exception.Message -like 'no debia*'){throw 'Test-AppProcess consulto el servicio con ruta y argumentos visibles.'}}
    if($got -cne $case.e){throw ('Proceso propio (App-Running): caso '+[array]::IndexOf($appCases,$case)+' esperaba '+$case.e+' y obtuvo '+$got+'. Ante una duda debe lanzar el error, nunca responder que no corre.')}
}
# --- Correcciones para Windows (H06c, hallazgos del ensayo de H06b) ---------------------------------------------------
# Defecto 1: el StartName '.\cuenta' de una cuenta local se traduce con el nombre del equipo, nunca como la cuenta sola.
$accountCases=@(
    @{n='.\siger-ensayo';c='EQUIPO';e='EQUIPO\siger-ensayo'},@{n='.\svc-siger';c='DDN-SIGER';e='DDN-SIGER\svc-siger'},
    @{n='DEFENSORIANINEZ\svc-siger';c='EQUIPO';e='DEFENSORIANINEZ\svc-siger'},@{n='DDN-SIGER\svc-siger';c='EQUIPO';e='DDN-SIGER\svc-siger'},
    @{n='LocalSystem';c='EQUIPO';e='LocalSystem'},@{n='NT AUTHORITY\LocalService';c='EQUIPO';e='NT AUTHORITY\LocalService'},
    @{n='svc@defensorianinez.cl';c='EQUIPO';e='svc@defensorianinez.cl'},@{n='.\x';c='';e='ERROR'},@{n='.\x';c=' ';e='ERROR'})
foreach($case in $accountCases){
    $got='ERROR';try{$got=ConvertTo-ServiceAccountName $case.n $case.c}catch{$got='ERROR'}
    if($got -cne $case.e){throw ('Cuenta del servicio: '+$case.n+' esperaba "'+$case.e+'" y obtuvo "'+$got+'" (una cuenta local se traduce como EQUIPO\cuenta, nunca sola).')}
}
$commonAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Common.ps1'),[ref]$null,[ref]$null)
$factsFn=@($commonAst.FindAll({param($a) $a -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $a.Name -ceq 'Get-ServiceProcessFacts'},$true))
if($factsFn.Count -ne 1 -or @($factsFn[0].FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ceq 'ConvertTo-ServiceAccountName'},$true)).Count -ne 1){throw 'Get-ServiceProcessFacts debe traducir el StartName con ConvertTo-ServiceAccountName.'}
$h06cCount=$accountCases.Count+1
if($onWindows){
    # Traduccion real de una cuenta local del equipo escrita como la registra Windows ('.\cuenta').
    $local=@(Get-CimInstance Win32_UserAccount -Filter 'LocalAccount=True' -ErrorAction Stop|Where-Object {$_.SID -like 'S-1-5-21-*'})|Select-Object -First 1
    if(-not $local){throw 'Cuenta del servicio: no hay una cuenta local para la prueba real de traduccion.'}
    $asService=ConvertTo-ServiceAccountName ('.\'+$local.Name) $env:COMPUTERNAME
    if((New-Object Security.Principal.NTAccount($asService)).Translate([Security.Principal.SecurityIdentifier]).Value -cne $local.SID){throw 'Cuenta del servicio: .\cuenta local no se tradujo al SID de la cuenta.'}
    if((Resolve-AccountSid $local.Name $env:COMPUTERNAME) -cne $local.SID -or (Resolve-AccountSid "$env:COMPUTERNAME\$($local.Name)" $env:COMPUTERNAME) -cne $local.SID -or (Resolve-AccountSid ('.\'+$local.Name) $env:COMPUTERNAME) -cne $local.SID){throw 'Resolve-AccountSid: no resolvio la cuenta local por nombre.'}
    if((Resolve-AccountSid ('no-existe-'+[Guid]::NewGuid().ToString('N').Substring(0,8)) $env:COMPUTERNAME) -ne ''){throw 'Resolve-AccountSid: resolvio una cuenta inexistente.'}
    $h06cCount+=5
}
# Hallazgo (e): la cuenta duena (ownerSid) se exige antes de descifrar.
$ownerCases=@(
    @{s=[PSCustomObject]@{ownerSid='S-1-5-21-1-2-3-1005'};u='S-1-5-21-1-2-3-1005';e=$true},
    @{s=[PSCustomObject]@{ownerSid='S-1-5-21-1-2-3-1005'};u='S-1-5-21-1-2-3-1006';e=$false},
    @{s=[PSCustomObject]@{ownerSid='S-1-5-21-1-2-3-1005'};u='';e=$false},
    @{s=[PSCustomObject]@{ownerSid=''};u='';e=$false},
    @{s=[PSCustomObject]@{root='D:\SIGER'};u='S-1-5-21-1-2-3-1005';e=$false},@{s=$null;u='S-1-5-21-1-2-3-1005';e=$false})
foreach($case in $ownerCases){if((Test-InstanceOwner $case.s $case.u) -ne $case.e){throw ('Cuenta duena: caso '+[array]::IndexOf($ownerCases,$case)+' esperaba '+$case.e+'.')}}
foreach($scriptName in @('Close-Validation.ps1','Test-Service.ps1')){
    $ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $scriptName),[ref]$null,[ref]$null)
    $calls=@($ast.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst]},$true))
    $assert=@($calls|Where-Object {$_.GetCommandName() -ceq 'Assert-InstanceOwner'})
    $clixml=@($calls|Where-Object {$_.GetCommandName() -ceq 'Import-Clixml'})
    if($assert.Count -ne 1 -or $clixml.Count -eq 0 -or @($clixml|Where-Object {$_.Extent.StartOffset -lt $assert[0].Extent.StartOffset}).Count -gt 0){throw "$scriptName debe llamar a Assert-InstanceOwner antes de descifrar (Import-Clixml)."}
}
$h06cCount+=$ownerCases.Count+2
# Hallazgo (a): el derecho de inicio como servicio por SID, tambien cuando secedit escribe la cuenta por su nombre.
$rightMap=@{'siger-ensayo'='S-1-5-21-1-2-3-1005';'otra'='S-1-5-21-1-2-3-1006'}
$rightResolve={param($n) if($rightMap.ContainsKey($n)){$rightMap[$n]}else{''}}
$rightCases=@(
    @{l='SeServiceLogonRight = siger-ensayo,*S-1-5-80-0,*S-1-5-83-0';s='S-1-5-21-1-2-3-1005';e=$true},
    @{l='SeServiceLogonRight = siger-ensayo,*S-1-5-80-0,*S-1-5-83-0';s='S-1-5-21-1-2-3-1006';e=$false},
    @{l='SeServiceLogonRight = *S-1-5-80-0,otra';s='S-1-5-21-1-2-3-1006';e=$true},
    @{l='SeServiceLogonRight = *S-1-5-80-0,*S-1-5-21-1-2-3-1005';s='S-1-5-21-1-2-3-1005';e=$true},
    @{l='SeServiceLogonRight = *S-1-5-21-1-2-3-10050,*S-1-5-80-0';s='S-1-5-21-1-2-3-1005';e=$false},
    @{l='SeServiceLogonRight = desconocida';s='S-1-5-21-1-2-3-1005';e=$false},
    @{l='SeServiceLogonRight = ';s='S-1-5-21-1-2-3-1005';e=$false},
    @{l='SeNetworkLogonRight = *S-1-5-21-1-2-3-1005';s='S-1-5-21-1-2-3-1005';e=$false},
    @{l='';s='S-1-5-21-1-2-3-1005';e=$false},@{l='SeServiceLogonRight = *';s='';e=$false})
foreach($case in $rightCases){if((Test-LogonRightEntry $case.l $case.s $rightResolve) -ne $case.e){throw ('Derecho de inicio como servicio: "'+$case.l+'" con '+$case.s+' esperaba '+$case.e+'.')}}
$isAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Install-Service.ps1'),[ref]$null,[ref]$null)
if(@($isAst.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ceq 'Test-LogonRightEntry'},$true)).Count -ne 2 -or $isAst.Extent.Text.Contains("'*'+`$sid")){throw 'Install-Service.ps1 debe comprobar el derecho de inicio como servicio con Test-LogonRightEntry (por SID), antes y despues de concederlo.'}
$h06cCount+=$rightCases.Count+1
# Condicion del responsable (control 200): CR solo para SYSTEM, Administradores y la cuenta de servicio.
$acct='S-1-5-21-109785577-53871645-4221270051-1005'
$defaultSd='D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWLOCRRC;;;IU)(A;;CCLCSWLOCRRC;;;SU)S:(AU;FA;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;WD)'
$expectedSd="D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWLORC;;;IU)(A;;CCLCSWLORC;;;SU)(A;;CCLCSWRPWPDTLOCRRC;;;$acct)S:(AU;FA;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;WD)"
$sdCases=@(
    @{i=$defaultSd;e=$expectedSd},
    @{i='D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWLOCRRC;;;IU)(A;;CCLCSWLOCRRC;;;SU)';e="D:(A;;CCLCSWRPWPDTLOCRRC;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWLORC;;;IU)(A;;CCLCSWLORC;;;SU)(A;;CCLCSWRPWPDTLOCRRC;;;$acct)"},
    @{i="D:(A;;CCLCSWLOCRRC;;;AU)(A;;CR;;;WD)(A;;RPCR;;;BU)(D;;CR;;;IU)(A;;LC;;;$acct)";e="D:(A;;CCLCSWLORC;;;AU)(A;;RP;;;BU)(D;;CR;;;IU)(A;;CCLCSWRPWPDTLOCRRC;;;$acct)"},
    @{i='D:P(A;;CCLCSWRPWPDTLOCRRC;;;S-1-5-18)(A;;CCLCCR;;;S-1-5-21-9-9-9-500)';e="D:P(A;;CCLCSWRPWPDTLOCRRC;;;S-1-5-18)(A;;CCLC;;;S-1-5-21-9-9-9-500)(A;;CCLCSWRPWPDTLOCRRC;;;$acct)"},
    @{i='D:(A;;0x100;;;IU)';e='ERROR'},@{i='O:BAD:(A;;CR;;;IU)';e='ERROR'},@{i='D:(A;;CR;;IU)';e='ERROR'},@{i='';e='ERROR'})
foreach($case in $sdCases){
    $got='ERROR';try{$got=ConvertTo-ServiceControlDescriptor $case.i $acct}catch{$got='ERROR'}
    if($got -cne $case.e){throw ('Descriptor del servicio: caso '+[array]::IndexOf($sdCases,$case)+' esperaba "'+$case.e+'" y obtuvo "'+$got+'".')}
    if($got -cne 'ERROR' -and -not (Test-ServiceControlDescriptor $got $acct)){throw ('Descriptor del servicio: el caso '+[array]::IndexOf($sdCases,$case)+' no pasa su propia comprobacion.')}
}
$badSdCase=$false;try{$null=ConvertTo-ServiceControlDescriptor $defaultSd 'IU'}catch{$badSdCase=$true}
if(-not $badSdCase){throw 'Descriptor del servicio: acepto un SID de cuenta no valido.'}
# Lo que dejaba Install-Service.ps1 hasta H06b (IU y SU con CR) y un descriptor sin la cuenta no pasan la comprobacion.
$oldSd=$defaultSd.Insert($defaultSd.IndexOf('S:'),"(A;;CCLCSWRPWPDTLOCRRC;;;$acct)")
if((Test-ServiceControlDescriptor $oldSd $acct) -or (Test-ServiceControlDescriptor ($expectedSd.Replace("(A;;CCLCSWRPWPDTLOCRRC;;;$acct)",'')) $acct) -or (Test-ServiceControlDescriptor $expectedSd 'S-1-5-21-1-2-3-4')){throw 'Descriptor del servicio: la comprobacion acepto CR en IU/SU o un descriptor sin la cuenta.'}
if(@($isAst.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and @('ConvertTo-ServiceControlDescriptor','Test-ServiceControlDescriptor') -ccontains $a.GetCommandName()},$true)).Count -ne 2){throw 'Install-Service.ps1 debe aplicar y comprobar el descriptor con ConvertTo-ServiceControlDescriptor y Test-ServiceControlDescriptor.'}
$h06cCount+=$sdCases.Count+3
# Defecto 3: la caida de Test-Service.ps1 es el control 200 de SigerService.exe, no Stop-Process.
$tsAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Test-Service.ps1'),[ref]$null,[ref]$null)
if(@($tsAst.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and @('Stop-Process','kill','spps','taskkill','taskkill.exe') -contains $a.GetCommandName()},$true)).Count -gt 0){throw 'Test-Service.ps1 no debe terminar procesos: la cuenta de servicio no puede (acceso denegado). La caida se simula con el control 200.'}
$exec=@($tsAst.FindAll({param($a) $a -is [System.Management.Automation.Language.InvokeMemberExpressionAst] -and $a.Member.Extent.Text -ceq 'ExecuteCommand'},$true))
$hostSource=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'SigerService.cs'))
$hostCode=[regex]::Match($hostSource,'public const int SimulatedFailureCommand = (\d+);')
if(-not $hostCode.Success -or $exec.Count -ne 1 -or @($exec[0].Arguments).Count -ne 1 -or [string]$exec[0].Arguments[0].Extent.Text -cne $hostCode.Groups[1].Value -or $hostSource -notmatch 'protected override void OnCustomCommand\(int command\)'){throw 'Test-Service.ps1 debe enviar con ExecuteCommand el mismo control (SimulatedFailureCommand) que atiende SigerService.cs en OnCustomCommand.'}
$h06cCount+=2
if($onWindows -and $PSVersionTable.PSEdition -eq 'Desktop'){
    # SigerService.cs compila con el compilador de .NET Framework (C# 5, como Install-Service.ps1) y atiende el control.
    $hostDir=Join-Path ([IO.Path]::GetTempPath()) ('siger-host-'+[Guid]::NewGuid().ToString('N'));[void][IO.Directory]::CreateDirectory($hostDir)
    try{
        $dll=Join-Path $hostDir 'SigerServiceCheck.dll'
        Add-Type -TypeDefinition $hostSource -ReferencedAssemblies System.ServiceProcess -OutputAssembly $dll -OutputType Library
        $type=[Reflection.Assembly]::Load([IO.File]::ReadAllBytes($dll)).GetType('SigerService')
        $method=$type.GetMethod('OnCustomCommand',[Reflection.BindingFlags]'NonPublic,Instance,DeclaredOnly')
        $const=$type.GetField('SimulatedFailureCommand')
        if(-not $method -or -not $const -or [int]$const.GetValue($null) -ne [int]$hostCode.Groups[1].Value){throw 'SigerService.cs: falta OnCustomCommand o SimulatedFailureCommand.'}
    }finally{[IO.Directory]::Delete($hostDir,$true)}
    $h06cCount++
    # Hallazgo (b): con -WhatIf, Common.ps1 no deja las lineas "Establecer alias" de CimCmdlets (control: sin Common.ps1 si salen).
    $whatIfDir=Join-Path ([IO.Path]::GetTempPath()) ('siger-whatif-'+[Guid]::NewGuid().ToString('N'));[void][IO.Directory]::CreateDirectory($whatIfDir)
    try{
        $common=(Join-Path $PSScriptRoot 'Common.ps1').Replace("'","''")
        foreach($pair in @(@('con-common.ps1',". '$common'"),@('sin-common.ps1','$ErrorActionPreference=''Stop'''))){
            [IO.File]::WriteAllLines((Join-Path $whatIfDir $pair[0]),@('[CmdletBinding(SupportsShouldProcess=$true)]','param()',$pair[1],'$null=Get-CimInstance Win32_OperatingSystem','if($PSCmdlet.ShouldProcess(''prueba'',''accion'')){''ACTUO''}else{''NO_ACTUO''}'),(New-Object Text.UTF8Encoding($true)))
        }
        $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{
            $hostExe=(Get-Process -Id $PID).Path
            $with=@(& $hostExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $whatIfDir 'con-common.ps1') -WhatIf 2>&1|ForEach-Object {[string]$_})
            $without=@(& $hostExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $whatIfDir 'sin-common.ps1') -WhatIf 2>&1|ForEach-Object {[string]$_})
        }finally{$ErrorActionPreference=$old}
        if(@($with|Where-Object {$_ -match 'gcim' -or $_ -match 'cimWhatIf'}).Count -gt 0 -or ($with -join "`n") -notmatch 'NO_ACTUO' -or ($with -join "`n") -match '(?m)^ACTUO$'){throw 'Common.ps1 con -WhatIf: quedan las lineas de alias de CimCmdlets (o la de su variable) o -WhatIf dejo de regir.'}
        if(@($without|Where-Object {$_ -match 'gcim'}).Count -eq 0){throw 'Control de -WhatIf: sin Common.ps1 no aparecieron las lineas de alias; la prueba no distingue.'}
    }finally{[IO.Directory]::Delete($whatIfDir,$true)}
    $h06cCount+=2
}
# Defecto 2: la constancia de eliminacion acepta la List[object] de Get-DeletionPlan, creada como alli o con New-Object.
$listSettings=[PSCustomObject]@{root='D:\SIGER-VALIDACION';database='siger_a_inst';jarSha256='ab'*32}
$listClosure=[PSCustomObject]@{file='cierre-validacion-20261204T120000Z.json';sha256='cd'*32;closedUtc='2026-12-04T12:00:00Z';communications=30;confirmed=9}
$wrapped=New-Object System.Collections.Generic.List[object];$plain=[System.Collections.Generic.List[object]]::new()
foreach($l in @($wrapped,$plain)){foreach($t in @('ENSAYO','RESPALDO','RAIZ')){$l.Add([PSCustomObject]@{Type=$t;Path="D:\x\$t";Files=1;Bytes=2;Evidence='e'})}}
foreach($l in @($wrapped,$plain,@($plain.ToArray()))){
    $r=New-ValidationDeletionRecord $listSettings $listClosure 'D:\SIGER' $null $l 0 'Prueba' 'EQUIPO\svc-siger'
    if(@($r.elements).Count -ne 3 -or ((@($r.elements|ForEach-Object {$_.type})) -join ',') -cne 'ENSAYO,RESPALDO,RAIZ'){throw 'Constancia de eliminacion: no recorrio la lista de elementos.'}
}
$rvAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Remove-Validation.ps1'),[ref]$null,[ref]$null)
if($rvAst.Extent.Text -match 'New-Object\s+''?System\.Collections\.Generic\.List'){throw 'Remove-Validation.ps1: la lista de elementos se crea con ::new(), no con New-Object (envuelta en PSObject, @() falla).'}
$h06cCount+=4
# --- Prueba de envio exigida para activar el correo (Set-Integration.ps1 -Correo Graph, H04a) ------------------------
$probeNow=[datetime]::new(2026,12,8,12,0,0,[DateTimeKind]::Utc)
function New-GraphProbe($Change){$p=[PSCustomObject]@{modo='ENVIO';estado='ACEPTADO';fase='ENVIO';http=202;requestId=$null;utc='2026-12-08T10:00:00.0000000Z';jarSha256='ABCDEF'};if($Change){& $Change $p};return $p}
$probeCases=@(
    @{p=(New-GraphProbe $null);j='ABCDEF';ok=$true},@{p=(New-GraphProbe $null);j='abcdef';ok=$true},
    @{p=(New-GraphProbe {param($p) $p.utc=[datetime]::new(2026,12,8,1,0,0,[DateTimeKind]::Utc)});j='ABCDEF';ok=$true},
    @{p=(New-GraphProbe {param($p) $p.utc='2026-12-07T12:30:00Z'});j='ABCDEF';ok=$true},
    @{p=$null;j='ABCDEF';ok=$false},@{p=(New-GraphProbe $null);j='';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.modo='TOKEN'});j='ABCDEF';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.estado='RECHAZADO'});j='ABCDEF';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.estado='SIN_RESULTADO'});j='ABCDEF';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.jarSha256='012345'});j='ABCDEF';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.utc='2026-12-07T11:59:00Z'});j='ABCDEF';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.utc='2026-12-08T13:00:00Z'});j='ABCDEF';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.utc='ayer'});j='ABCDEF';ok=$false},
    @{p=(New-GraphProbe {param($p) $p.utc=$null});j='ABCDEF';ok=$false})
foreach($case in $probeCases){if((Test-GraphProbeAccredited $case.p $case.j $probeNow) -ne $case.ok){throw ('Prueba de Graph para activar el correo: caso '+[array]::IndexOf($probeCases,$case)+' inesperado.')}}
$setAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Set-Integration.ps1'),[ref]$null,[ref]$null)
if(@($setAst.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ceq 'Test-GraphProbeAccredited'},$true)).Count -ne 1){throw 'Set-Integration.ps1 debe exigir Test-GraphProbeAccredited antes de activar el correo.'}
# --- Liberacion por analisis en origen (Set-OriginRelease.ps1, D17, H04c y H05) --------------------------------------
# Sin fecha de termino (H05): el registro es version 2, sin "until", y Set-OriginRelease.ps1 no tiene -Hasta. Motivo en
# una linea de 1 a 500 caracteres; cuenta de 1 a 200 sin caracteres de control.
$originNow=[datetime]::new(2026,12,4,12,0,0,[DateTimeKind]::Utc)
$originCases=@(
    @{m='Decision del responsable';a='DDN-SIGER\svc-siger';now=$originNow;ok=$true},
    @{m='  Decision con espacios  ';a='DDN-SIGER\svc-siger';now=$originNow;ok=$true;r='Decision con espacios'},
    @{m='Decision del responsable';a='DDN-SIGER\svc-siger';now=[datetime]::new(2036,12,4,12,0,0,[DateTimeKind]::Utc);ok=$true},
    @{m=('x'*500);a='DDN-SIGER\svc-siger';now=$originNow;ok=$true},
    @{m='';a='DDN-SIGER\svc-siger';now=$originNow;ok=$false},
    @{m='   ';a='DDN-SIGER\svc-siger';now=$originNow;ok=$false},
    @{m=('x'*501);a='DDN-SIGER\svc-siger';now=$originNow;ok=$false},
    @{m=('linea'+[char]10+'otra');a='DDN-SIGER\svc-siger';now=$originNow;ok=$false},
    @{m=('tab'+[char]9+'otra');a='DDN-SIGER\svc-siger';now=$originNow;ok=$false},
    @{m='Decision del responsable';a='';now=$originNow;ok=$false},
    @{m='Decision del responsable';a=('x'*201);now=$originNow;ok=$false},
    @{m='Decision del responsable';a=('DDN'+[char]10+'svc');now=$originNow;ok=$false})
foreach($case in $originCases){
    $got=$null;try{$got=New-OriginReleaseRecord $case.m $case.a $case.now}catch{$got=$null}
    if([bool]$got -ne $case.ok){throw ('Liberacion por analisis en origen: caso '+[array]::IndexOf($originCases,$case)+' dio '+$(if($got){'registro'}else{'rechazo'})+'.')}
    if($got){
        $names=@($got.PSObject.Properties|ForEach-Object{$_.Name})
        $reason=if($case.ContainsKey('r')){$case.r}else{$case.m}
        if(($names -join ',') -cne 'kind,version,id,activatedUtc,account,reason' -or $got.kind -cne 'LIBERACION_ORIGEN' -or $got.version -ne 2 -or $got.account -cne $case.a -or $got.reason -cne $reason -or $got.activatedUtc -cne $case.now.ToString('o')){throw 'Liberacion por analisis en origen: registro con otros campos o valores.'}
        $parsed=[guid]::Empty;if(-not [guid]::TryParse([string]$got.id,[ref]$parsed)){throw 'Liberacion por analisis en origen: identificador no es GUID.'}
    }
}
$originAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Set-OriginRelease.ps1'),[ref]$null,[ref]$null)
if((@($originAst.ParamBlock.Parameters|ForEach-Object{$_.Name.VariablePath.UserPath}) -join ',') -cne 'Root,Motivo,Desactivar'){throw 'Set-OriginRelease.ps1: los parametros deben ser -Root, -Motivo y -Desactivar (sin fecha de termino).'}
if([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Set-OriginRelease.ps1')) -match '(?i)\$Hasta|\buntil\b'){throw 'Set-OriginRelease.ps1 no debe usar -Hasta ni un termino (until).'}
# La ruta del archivo solo llega a los procesos que registran entregas (servicio y ciclo manual sin -SoloComprobar).
$originDir=Join-Path ([IO.Path]::GetTempPath()) ('siger-origen-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path (Join-Path $originDir 'config')|Out-Null
try{
    Set-OriginReleaseEnvironment ([PSCustomObject]@{root=$originDir});if($env:SIGER_ORIGIN_RELEASE_FILE){throw 'Sin archivo de liberacion se definio SIGER_ORIGIN_RELEASE_FILE.'}
    [IO.File]::WriteAllText((Join-Path $originDir 'config\liberacion-origen.json'),'{}')
    Set-OriginReleaseEnvironment ([PSCustomObject]@{root=$originDir});if($env:SIGER_ORIGIN_RELEASE_FILE -cne (Join-Path $originDir 'config\liberacion-origen.json')){throw 'Con archivo de liberacion no se definio su ruta.'}
    Clear-SigerEnvironment;if($env:SIGER_ORIGIN_RELEASE_FILE){throw 'Clear-SigerEnvironment no borro SIGER_ORIGIN_RELEASE_FILE.'}
}finally{[IO.Directory]::Delete($originDir,$true)}
function Get-CallsIn([string]$Script,[string]$Function,[string]$Command){
    $ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $Script),[ref]$null,[ref]$null)
    $scope=if($Function){@($ast.FindAll({param($a) $a -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $a.Name -ceq $Function},$true))[0]}else{$ast}
    return @($scope.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ceq $Command},$true)).Count
}
if((Get-CallsIn 'Environment.ps1' 'Set-SigerEnvironment' 'Set-OriginReleaseEnvironment') -ne 0){throw 'Set-SigerEnvironment no debe entregar el modo de liberacion (lo usan Migrate, Recover-Ti, Test-GraphMail y la sonda).'}
if((Get-CallsIn 'Environment.ps1' 'Set-SigerServiceEnvironment' 'Set-OriginReleaseEnvironment') -ne 1){throw 'Set-SigerServiceEnvironment debe entregar el modo de liberacion al servicio.'}
if((Get-CallsIn 'Invoke-FormularioPull.ps1' $null 'Set-OriginReleaseEnvironment') -ne 1){throw 'Invoke-FormularioPull.ps1 debe entregar el modo de liberacion solo al ciclo que registra.'}
$pullText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Invoke-FormularioPull.ps1'))
if($pullText -notmatch 'if\(-not \$SoloComprobar\)\{Set-OriginReleaseEnvironment \$s\}'){throw 'Invoke-FormularioPull.ps1: la sonda (-SoloComprobar) no debe recibir el modo de liberacion.'}
foreach($other in @('Migrate.ps1','Recover-Ti.ps1','Test-GraphMail.ps1','Close-Validation.ps1','Remove-Validation.ps1','Update.ps1','Backup.ps1','Restore-Check.ps1')){
    if((Get-CallsIn $other $null 'Set-OriginReleaseEnvironment')+(Get-CallsIn $other $null 'Set-SigerServiceEnvironment') -ne 0){throw "$other no debe entregar el modo de liberacion."}
}
if((Get-CallsIn 'Set-OriginRelease.ps1' $null 'New-OriginReleaseRecord') -ne 1){throw 'Set-OriginRelease.ps1 debe construir el registro con New-OriginReleaseRecord.'}
# --- Invocacion con powershell.exe -File (hallazgo de la revision de H03; H04a) ----------------------------------------
# Con -File cada argumento llega como texto: desde una consola de PowerShell -Ensayos @() se expande a nada, 'a','b'
# llega como 'a,b' y (Get-Credential ...) como 'System.Management.Automation.PSCredential'; desde cmd, @() llega como el
# texto '@()'. Se comprueba:
# 1) ningun script declara un parametro de tipo arreglo, coleccion, diccionario o scriptblock (Get-FileUnsafeParameters);
# 2) cada lista por texto (ConvertFrom-PathListArgument, ConvertFrom-DomainListArgument) tiene casos y su script la usa;
# 3) cada orden "powershell.exe ... -File ...\X.ps1 ..." de la guia y de los encabezados de los scripts usa argumentos
#    literales y enlaza en un proceso hijo (-NoProfile -NonInteractive -File) contra un script de prueba con el mismo
#    bloque param del real (tipos, ValidateSet, Mandatory y CmdletBinding) y sin su cuerpo; las listas pasan por su
#    funcion. Datos que se piden por consola: un PSCredential se copia al script de prueba como [string] (sin
#    Credential()), y la orden debe pasar el nombre literal de la cuenta (DOMINIO\cuenta): el script real pide entonces
#    solo la contrasena (se acredita en H06b). Lo que el cuerpo pide con Read-Host (Tenant y Client ID, secretos, la ruta
#    escrita de Remove-Validation.ps1) no es un parametro y no interviene en el enlace. Un parametro obligatorio que
#    falta en la orden hace fallar al hijo por -NonInteractive: ninguna orden se apoya en la pregunta de PowerShell;
# 4) controles con -File de lo que fallo en H03 (-Ensayos @(), 'a','b', -Ensayos sin valor, '@()' desde cmd).
# En Windows (Build.ps1) el hijo es powershell.exe 5.1; con pwsh en Linux solo se acredita la sintaxis de la orden.
$psExe=(Get-Process -Id $PID).Path
$lang='System.Management.Automation.Language'
function Get-FileUnsafeParameters($Ast){
    if(-not $Ast.ParamBlock){return @()}
    return @(foreach($p in $Ast.ParamBlock.Parameters){
        $t=$p.StaticType
        if($t.IsArray -or $t -eq [scriptblock] -or [Collections.IDictionary].IsAssignableFrom($t) -or ($t -ne [string] -and [Collections.IEnumerable].IsAssignableFrom($t))){$p.Name.VariablePath.UserPath}
    })
}
$unsafeControl=[System.Management.Automation.Language.Parser]::ParseInput('param([string]$A,[string[]]$B,[hashtable]$C,[scriptblock]$D,[object[]]$E,[array]$F,[int]$G,[switch]$H,[pscredential]$I)',[ref]$null,[ref]$null)
if((@(Get-FileUnsafeParameters $unsafeControl) -join ',') -cne 'B,C,D,E,F'){throw 'Control negativo: la regla de parametros no detecta los tipos que -File no puede pasar.'}
# Windows PowerShell 5.1 deja $PSScriptRoot (y $PSCommandPath) vacio en los valores por defecto del param de un
# script con [CmdletBinding()] (hallazgo de H06a en Get-Tools.ps1): ningun param, del script ni de sus funciones, los usa.
function Get-ParamScriptRootUses($Ast){
    return @(foreach($block in $Ast.FindAll({param($a) $a -is [System.Management.Automation.Language.ParamBlockAst]},$true)){
        $block.FindAll({param($a) $a -is [System.Management.Automation.Language.VariableExpressionAst] -and @('PSScriptRoot','PSCommandPath') -contains $a.VariablePath.UserPath},$true)|ForEach-Object {$_.VariablePath.UserPath}
    })
}
$rootControl=[System.Management.Automation.Language.Parser]::ParseInput('[CmdletBinding()] param([string]$A=(Split-Path $PSScriptRoot -Parent)) function F { param([string]$B=$PSCommandPath) } $C=$PSScriptRoot',[ref]$null,[ref]$null)
if((@(Get-ParamScriptRootUses $rootControl) -join ',') -cne 'PSScriptRoot,PSCommandPath'){throw 'Control negativo: la regla de $PSScriptRoot en param no detecta sus usos.'}
$listParams=@{'Remove-Validation.ps1'=@{Respaldos='ConvertFrom-PathListArgument';RespaldosIncompletos='ConvertFrom-PathListArgument';Ensayos='ConvertFrom-PathListArgument'};'Import-GraphCredential.ps1'=@{DominiosPermitidos='ConvertFrom-DomainListArgument'}}
foreach($file in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File)){
    $ast=[System.Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$null,[ref]$null)
    $unsafe=@(Get-FileUnsafeParameters $ast)
    if($unsafe.Count -gt 0){throw ("$($file.Name): el parametro -"+($unsafe -join ', -')+' no llega con powershell.exe -File (arreglo, coleccion o scriptblock). Usa un texto: rutas separadas por | o NINGUNO (ConvertFrom-PathListArgument).')}
    $rootUses=@(Get-ParamScriptRootUses $ast)
    if($rootUses.Count -gt 0){throw ("$($file.Name): un param usa `$"+($rootUses -join ', $')+', que Windows PowerShell 5.1 deja vacio con [CmdletBinding()]. Calcula el valor por defecto en el cuerpo.')}
    if(-not $listParams.ContainsKey($file.Name)){continue}
    foreach($name in $listParams[$file.Name].Keys){
        $fn=$listParams[$file.Name][$name]
        $param=@($ast.ParamBlock.Parameters|Where-Object {$_.Name.VariablePath.UserPath -ceq $name})
        if($param.Count -ne 1 -or $param[0].StaticType -ne [string]){throw "$($file.Name): falta la lista -$name como texto."}
        $uses=@($ast.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ceq $fn -and $a.CommandElements.Count -ge 2 -and $a.CommandElements[1] -is [System.Management.Automation.Language.VariableExpressionAst] -and $a.CommandElements[1].VariablePath.UserPath -ceq $name},$true))
        if($uses.Count -ne 1){throw "$($file.Name): la lista -$name debe pasar una vez por $fn."}
    }
}
$pathListCases=@(
    @{t='NINGUNO';e=''},@{t='ninguno';e=''},@{t=' NINGUNO ';e=''},@{t='D:\Respaldos-SIGER-VALIDACION';e='D:\Respaldos-SIGER-VALIDACION'},
    @{t='D:\a|D:\b';e='D:\a|D:\b'},@{t=' D:\a | D:\b ';e='D:\a|D:\b'},@{t='D:\con espacio|D:\b';e='D:\con espacio|D:\b'},
    @{t='';e=$null},@{t='   ';e=$null},@{t='@()';e=$null},@{t='@( )';e=$null},@{t='D:\a,D:\b';e=$null},@{t='D:\a|';e=$null},@{t='|D:\a';e=$null},
    @{t='D:\a||D:\b';e=$null},@{t='NINGUNO|D:\a';e=$null},@{t='D:\a|ninguno';e=$null},@{t='D:\a|D:\a\';e=$null},@{t='D:\a|d:\A';e=$null})
foreach($case in $pathListCases){
    $got=$null;try{$r=ConvertFrom-PathListArgument $case.t '-Ensayos';if($r -isnot [string[]]){throw 'tipo'};$got=$r -join '|'}catch{$got=$null}
    if($got -cne $case.e){throw ('Lista de rutas: caso '+[array]::IndexOf($pathListCases,$case)+' esperaba '+$(if($null -eq $case.e){'rechazo'}else{'"'+$case.e+'"'})+' y obtuvo '+$(if($null -eq $got){'rechazo'}else{'"'+$got+'"'})+'.')}
}
$domainListCases=@(
    @{t='defensorianinez.cl';e='defensorianinez.cl'},@{t='DefensoriaNinez.cl, otro.gob.cl';e='defensorianinez.cl|otro.gob.cl'},@{t='a.cl|b.cl';e='a.cl|b.cl'},
    @{t='"a.cl","b.cl"';e='a.cl|b.cl'},@{t='''a.cl''';e='a.cl'},@{t='a".cl';e=$null},
    @{t='';e=$null},@{t='@()';e=$null},@{t='a.cl,,b.cl';e=$null},@{t='a.cl,A.cl';e=$null},@{t='@a.cl';e=$null},@{t='local';e=$null},@{t='a.cl b.cl';e=$null})
foreach($case in $domainListCases){
    $got=$null;try{$got=(ConvertFrom-DomainListArgument $case.t) -join '|'}catch{$got=$null}
    if($got -cne $case.e){throw ('Lista de dominios: caso '+[array]::IndexOf($domainListCases,$case)+' inesperado.')}
}
$stubDir=Join-Path ([IO.Path]::GetTempPath()) ('siger-file-'+[Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($stubDir)
$stubs=@{}
function Get-ParamStub([string]$Name){
    if($stubs.ContainsKey($Name)){return $stubs[$Name]}
    $path=Join-Path $PSScriptRoot $Name
    if(-not [IO.File]::Exists($path)){throw "Orden documentada con un script que no existe: $Name."}
    $ast=[System.Management.Automation.Language.Parser]::ParseFile($path,[ref]$null,[ref]$null)
    $credentials=@();$head='param()'
    if($ast.ParamBlock){
        $head=$ast.Extent.Text.Substring(0,$ast.ParamBlock.Extent.EndOffset-$ast.Extent.StartOffset)
        $credentials=@($ast.ParamBlock.Parameters|Where-Object {$_.StaticType -eq [pscredential]}|ForEach-Object {$_.Name.VariablePath.UserPath})
        $head=($head -replace '(?i)\[System\.Management\.Automation\.Credential\(\)\]','') -replace '(?i)\[(System\.Management\.Automation\.)?pscredential\]','[string]'
    }
    $body=@'

$o=[ordered]@{}
foreach($k in @($PSBoundParameters.Keys)){$v=$PSBoundParameters[$k];$o[$k]=if($v -is [System.Management.Automation.SwitchParameter]){[bool]$v.IsPresent}else{[string]$v}}
[Console]::Out.WriteLine('ENLACE:'+(ConvertTo-Json -InputObject $o -Compress))
exit 0
'@
    $stub=Join-Path $stubDir $Name
    [IO.File]::WriteAllText($stub,$head+$body,(New-Object Text.UTF8Encoding($true)))
    $check=[System.Management.Automation.Language.Parser]::ParseFile($stub,[ref]$null,[ref]$null)
    if($check.ParamBlock -and @($check.ParamBlock.Parameters|Where-Object {$_.StaticType -eq [pscredential]}).Count -gt 0){throw "Script de prueba de $Name con un PSCredential: pediria datos por consola."}
    $stubs[$Name]=[PSCustomObject]@{Name=$Name;Path=$stub;Credentials=$credentials}
    return $stubs[$Name]
}
# Proceso hijo con -File; devuelve los parametros enlazados (texto) o $null si no enlaza.
function Invoke-FileStub($Stub,[string[]]$Arguments){
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$out=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $Stub.Path @Arguments 2>&1|ForEach-Object {[string]$_});$code=$LASTEXITCODE}finally{$ErrorActionPreference=$old}
    $line=@($out|Where-Object {$_.StartsWith('ENLACE:')})
    if($code -ne 0 -or $line.Count -ne 1){$script:lastFileError=(@($out|Where-Object {$_ -and -not $_.StartsWith('ENLACE:')})|Select-Object -First 2) -join ' ';return $null}
    return ($line[0].Substring(7)|ConvertFrom-Json)
}
# Argumentos de una orden tal como los recibe el script con -File: solo texto literal.
function ConvertTo-FileArguments($Elements,[string]$Source){
    $list=New-Object System.Collections.Generic.List[string]
    foreach($e in $Elements){
        if($e -is [System.Management.Automation.Language.CommandParameterAst]){
            if($null -eq $e.Argument){$list.Add('-'+$e.ParameterName);continue}
            if($e.Argument -is [System.Management.Automation.Language.VariableExpressionAst] -and @('true','false') -contains $e.Argument.VariablePath.UserPath){$list.Add('-'+$e.ParameterName+':$'+$e.Argument.VariablePath.UserPath);continue}
        }elseif($e -is [System.Management.Automation.Language.StringConstantExpressionAst]){$list.Add($e.Value);continue}
        elseif($e -is [System.Management.Automation.Language.ExpandableStringExpressionAst] -and $e.NestedExpressions.Count -eq 0){$list.Add($e.Value);continue}
        elseif($e -is [System.Management.Automation.Language.ConstantExpressionAst]){$list.Add([string]$e.Value);continue}
        throw "$Source`: el argumento $($e.Extent.Text) lo evalua la consola y con -File el script recibe texto. Usa texto literal: listas separadas por | o NINGUNO, una cuenta como 'DOMINIO\cuenta'."
    }
    return ,$list.ToArray()
}
function Get-OrderParts([string]$Text,[string]$Source){
    $m=[regex]::Match($Text,'(?i)\s-File\s+(.*)$')
    if(-not $m.Success){throw "$Source`: orden sin -File."}
    $ast=[System.Management.Automation.Language.Parser]::ParseInput('Orden '+$m.Groups[1].Value,[ref]$null,[ref]$errors)
    if($errors.Count -gt 0){throw "$Source`: la orden no es PowerShell valido."}
    $cmd=@($ast.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst]},$false))[0]
    $elements=@($cmd.CommandElements)
    if($elements.Count -lt 2 -or $elements[1] -isnot [System.Management.Automation.Language.StringConstantExpressionAst]){throw "$Source`: falta la ruta literal del script."}
    $leaf=@($elements[1].Value.Split([char[]]@('\','/')))[-1]
    return [PSCustomObject]@{Script=$leaf;Arguments=(ConvertTo-FileArguments @($elements|Select-Object -Skip 2) $Source)}
}
$errors=$null
foreach($bad in @("-File 'D:\x\scripts\Remove-Validation.ps1' -Ensayos @()","-File 'D:\x\scripts\Remove-Validation.ps1' -Ensayos 'D:\a','D:\b'","-File 'D:\x\scripts\Install-Service.ps1' -ServiceCredential (Get-Credential 'DOMINIO\svc')","-File 'D:\x\scripts\Start.ps1' -Root `$root","-File 'D:\x\scripts\Start.ps1' -Root `"`$(`$x)`"")){
    $rejected=$false;try{$null=Get-OrderParts (' '+$bad) 'control'}catch{$rejected=$true}
    if(-not $rejected){throw "Control negativo: se acepto como literal la orden $bad."}
}
try{
    $orders=New-Object System.Collections.Generic.List[object]
    foreach($guide in @(@('docs','DESPLIEGUE_VM.md'),@('docs','INSTALACION_VM.md'),@('docs','PRUEBAS_MANUALES_0_6_0.md'),@('README.md'),@('CLAUDE.md'))){
        $path=[IO.Path]::Combine([string[]](@($project)+$guide))
        if(-not [IO.File]::Exists($path)){throw "Falta la guia $($guide -join '/'): las ordenes documentadas se comprueban con -File."}
        $n=0;foreach($line in [IO.File]::ReadAllLines($path)){$n++;if($line -match '(?i)powershell\.exe\b.*\s-File\s'){$orders.Add([PSCustomObject]@{Source="$($guide -join '/'):$n";Text=$line})}}
    }
    foreach($file in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File)){
        $n=0;foreach($line in [IO.File]::ReadAllLines($file.FullName)){$n++;if($line -match '(?i)^\s*#\s*powershell\.exe\b.*\s-File\s'){$orders.Add([PSCustomObject]@{Source="scripts/$($file.Name):$n";Text=($line -replace '^\s*#','')})}}
    }
    if($orders.Count -lt 30){throw "Se esperaban al menos 30 ordenes documentadas con -File y se encontraron $($orders.Count)."}
    $seen=@{};$invoked=0
    foreach($order in $orders){
        $parts=Get-OrderParts $order.Text $order.Source
        $stub=Get-ParamStub $parts.Script
        $key=$parts.Script+[char]0+($parts.Arguments -join [char]0)
        if(-not $seen.ContainsKey($key)){$seen[$key]=Invoke-FileStub $stub $parts.Arguments;$invoked++}
        $bound=$seen[$key]
        if(-not $bound){throw "$($order.Source): la orden de $($parts.Script) no enlaza con powershell.exe -File ($script:lastFileError)."}
        foreach($c in $stub.Credentials){
            $v=if($bound.PSObject.Properties[$c]){[string]$bound.$c}else{''}
            if($v -cnotmatch '^[^\\\s]+\\[^\\\s]+$' -or $v -like '*PSCredential*'){throw "$($order.Source): -$c debe ser el nombre literal de la cuenta (DOMINIO\cuenta); el script pide la contrasena."}
        }
        if($listParams.ContainsKey($parts.Script)){
            foreach($name in $listParams[$parts.Script].Keys){
                if(-not $bound.PSObject.Properties[$name]){continue}
                try{$null=& $listParams[$parts.Script][$name] ([string]$bound.$name) "-$name"}catch{throw "$($order.Source): -$name no es una lista valida ($($_.Exception.Message))"}
            }
        }
    }
    # Controles con -File de lo que fallo en H03 (como lo pasa una consola de PowerShell) y los casos validos.
    $rv=Get-ParamStub 'Remove-Validation.ps1'
    $fixed=@('-Root','D:\SIGER-VALIDACION','-Produccion','D:\SIGER','-Motivo','Prueba de enlace','-Respaldos','D:\Respaldos-SIGER-VALIDACION','-RespaldosIncompletos','NINGUNO')
    function Get-EnsayosOutcome($Bound){if(-not $Bound){return 'NO_ENLAZA'};try{$l=ConvertFrom-PathListArgument ([string]$Bound.Ensayos) '-Ensayos';return ('ACEPTA:'+$l.Count)}catch{return 'RECHAZA'}}
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{
        $outEmpty=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $rv.Path @fixed -Ensayos @() 2>&1|ForEach-Object {[string]$_})
        $outPair=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $rv.Path @fixed -Ensayos 'D:\a','D:\b' 2>&1|ForEach-Object {[string]$_})
    }finally{$ErrorActionPreference=$old}
    $fromOut={param($out) $l=@($out|Where-Object {$_.StartsWith('ENLACE:')});if($l.Count -eq 1){$l[0].Substring(7)|ConvertFrom-Json}else{$null}}
    $controls=@(
        @{n='-Ensayos @() desde PowerShell';o=(Get-EnsayosOutcome (& $fromOut $outEmpty));bad=$true},
        @{n="-Ensayos 'D:\a','D:\b' desde PowerShell";o=(Get-EnsayosOutcome (& $fromOut $outPair));bad=$true},
        @{n="-Ensayos '@()' desde cmd";o=(Get-EnsayosOutcome (Invoke-FileStub $rv (@($fixed)+@('-Ensayos','@()'))));bad=$true},
        @{n='-Ensayos sin valor';o=(Get-EnsayosOutcome (Invoke-FileStub $rv (@($fixed)+@('-Ensayos'))));bad=$true},
        @{n='-Ensayos NINGUNO';o=(Get-EnsayosOutcome (Invoke-FileStub $rv (@($fixed)+@('-Ensayos','NINGUNO'))));bad=$false;e='ACEPTA:0'},
        @{n="-Ensayos 'D:\a|D:\b'";o=(Get-EnsayosOutcome (Invoke-FileStub $rv (@($fixed)+@('-Ensayos','D:\a|D:\b'))));bad=$false;e='ACEPTA:2'})
    foreach($c in $controls){
        if($c.bad -and $c.o -like 'ACEPTA*'){throw "Invocacion con -File: se acepto $($c.n)."}
        if(-not $c.bad -and $c.o -cne $c.e){throw "Invocacion con -File: $($c.n) dio $($c.o) y se esperaba $($c.e)."}
    }
    $ig=Get-ParamStub 'Import-GraphCredential.ps1'
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$outDomains=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ig.Path -Root 'D:\SIGER' -DominiosPermitidos 'defensorianinez.cl','otro.gob.cl' 2>&1|ForEach-Object {[string]$_})}finally{$ErrorActionPreference=$old}
    $b=& $fromOut $outDomains
    if(-not $b -or ((ConvertFrom-DomainListArgument ([string]$b.DominiosPermitidos)) -join '|') -cne 'defensorianinez.cl|otro.gob.cl'){throw "Invocacion con -File: -DominiosPermitidos 'a','b' desde PowerShell no llego como dos dominios."}
    $is=Get-ParamStub 'Install-Service.ps1'
    if(($is.Credentials -join ',') -cne 'ServiceCredential'){throw 'Install-Service.ps1: no se reconocio el PSCredential que se pide por consola.'}
    # D17 sin fecha de termino (H05): una orden con -Hasta ya no enlaza; con -Motivo o -Desactivar si.
    $or=Get-ParamStub 'Set-OriginRelease.ps1'
    if(Invoke-FileStub $or @('-Root','D:\SIGER','-Hasta','2027-03-03','-Motivo','Prueba de enlace')){throw 'Invocacion con -File: Set-OriginRelease.ps1 acepto -Hasta.'}
    if(-not (Invoke-FileStub $or @('-Root','D:\SIGER','-Motivo','Prueba de enlace')) -or -not (Invoke-FileStub $or @('-Root','D:\SIGER','-Desactivar'))){throw "Invocacion con -File: Set-OriginRelease.ps1 no enlaza con -Motivo o -Desactivar ($script:lastFileError)."}
    $controlCount=$controls.Count+3
}finally{if([IO.Directory]::Exists($stubDir)){[IO.Directory]::Delete($stubDir,$true)}}
# --- H06r: paquete portable de la configuracion (ConfigPackage.ps1, Export-InstanceConfig.ps1, Test-InstanceConfig.ps1) ---
. (Join-Path $PSScriptRoot 'ConfigPackage.ps1')
$h06rCount=0
function Assert-H06r([bool]$Condition,[string]$Message){if(-not $Condition){throw "Paquete de configuracion (H06r): $Message"};$script:h06rCount++}
# Rutas admitidas: config\... o mysql\my.ini; nunca '..', unidad, mayusculas distintas ni dentro de rotacion-<UTC>\.
foreach($c in @(@('config\instance.json',$true),@('config\sub\a.json',$true),@('mysql\my.ini',$true),@('config\rotacion-nota.txt',$true),
    @('',$false),@('config\',$false),@('config\..\instance.json',$false),@('config\.\a',$false),@('C:\config\a',$false),@('\config\a',$false),
    @('Config\a',$false),@('config/a',$false),@('config\a:b',$false),@('config\a ',$false),@('config\a.',$false),@('mysql\my.ini.bak',$false),
    @('data\documents\x',$false),@('config\rotacion-20261122T000000Z\secrets.clixml',$false),@(('config\'+('a'*240)),$false))){
    Assert-H06r ((Test-ConfigItemPath $c[0]) -eq $c[1]) "ruta '$($c[0])' esperaba $($c[1])."
}
foreach($c in @(@('config\secrets.clixml','CLIXML'),@('config\X.CLIXML','CLIXML'),@('config\https.p12','P12'),@('config\a.pfx','P12'),@('config\instance.json','ARCHIVO'),@('config\formulario-pull.json.bak-atomic-1','ARCHIVO'),@('config\a.clixml.bak','ARCHIVO'))){
    Assert-H06r ((Get-ConfigItemKind $c[0]) -ceq $c[1]) "tipo de '$($c[0])' esperaba $($c[1])."
}
foreach($c in @(@('config\https.p12','Tls'),@('config\https-anterior-20261122-101010.p12','Tls'),@('config\mysql-trust.p12','Trust'),@('config\HTTPS.p12',''),
    @('config\sub\https.p12',''),@('config\https-anterior-2026.p12',''),@('config\otro.p12',''),@('config\mysql-trust.p12.bak','') )){
    Assert-H06r ((Get-P12PasswordName $c[0]) -ceq $c[1]) "clave de '$($c[0])' esperaba '$($c[1])'."
}
# H06r2: estado del nombre de un almacen. Un nombre conocido salvo mayusculas es MAYUSCULAS (bloquea), nunca SIN_CLAVE.
foreach($c in @(@('config\https.p12','CONOCIDO'),@('config\https-anterior-20261122-101010.p12','CONOCIDO'),@('config\mysql-trust.p12','CONOCIDO'),
    @('config\HTTPS.p12','MAYUSCULAS'),@('config\https.P12','MAYUSCULAS'),@('config\https-ANTERIOR-20261122-101010.p12','MAYUSCULAS'),@('config\Https-anterior-20261122-101010.P12','MAYUSCULAS'),@('config\MySQL-Trust.p12','MAYUSCULAS'),
    @('config\https-red-candidato.p12','SIN_CLAVE'),@('config\otro.pfx','SIN_CLAVE'),@('config\https.pfx','SIN_CLAVE'),@('config\HTTPS.pfx','SIN_CLAVE'),@('config\sub\https.p12','SIN_CLAVE'),@('config\https-anterior-2026.p12','SIN_CLAVE'),@('config\mysql-trust.p12.p12','SIN_CLAVE'))){
    Assert-H06r ((Get-P12NameState $c[0]) -ceq $c[1]) "estado del nombre '$($c[0])' esperaba '$($c[1])'."
}
foreach($c in @(@('D:\R\a.sigerpkg',$false,$true,''),@('D:\R\a.SIGERPKG',$false,$true,''),@('D:\R\a.zip',$false,$true,'EXTENSION'),@('D:\R\a.sigerpkg',$true,$true,'EXISTE'),
    @('D:\R\a.sigerpkg',$false,$false,'SIN_CARPETA'),@('D:\SIGER-VALIDACION\config\a.sigerpkg',$false,$true,'DENTRO_DE_LA_RAIZ'),@('D:\SIGER-VALIDACION2\a.sigerpkg',$false,$true,''))){
    Assert-H06r ((@(Get-ExportDestinationBlockers $c[0] 'D:\SIGER-VALIDACION' $c[1] $c[2]) -join ',') -ceq $c[3]) "destino '$($c[0])' esperaba '$($c[3])'."
}
foreach($c in @(@('S-1-5-21-1-1005','S-1-5-21-1-500',$true),@('S-1-5-21-1-1005','S-1-5-21-1-1005',$false),@('','S-1-5-21-1-500',$false),@('S-1-5-21-1-1005','',$false))){
    Assert-H06r ((Test-VerifierAccount $c[0] $c[1]) -eq $c[2]) "cuenta verificadora $($c[1]) frente a $($c[0]) esperaba $($c[2])."
}
foreach($c in @(@('siger-ack-1','siger-ack-1',''),@('siger-ack-1','',''),@('siger-ack-1','otra','ACK_KEYID_DISTINTO'),@('mal id','','ACK_KEYID_INVALIDO'),@('','','ACK_KEYID_INVALIDO'),@('SIGER-ACK-1','siger-ack-1','ACK_KEYID_DISTINTO'))){
    Assert-H06r ((@(Get-AckKeyIdFindings ([PSCustomObject]@{KeyId=$c[0];PullKeyId=$c[1]})) -join ',') -ceq $c[2]) "keyId '$($c[0])' con '$($c[1])' esperaba '$($c[2])'."
}
Assert-H06r ((Test-BytesEqual ([byte[]](1,2,3)) ([byte[]](1,2,3))) -and -not (Test-BytesEqual ([byte[]](1,2,3)) ([byte[]](1,2,4))) -and -not (Test-BytesEqual ([byte[]](1,2,3)) ([byte[]](1,2))) -and -not (Test-BytesEqual $null ([byte[]](1)))) 'comparacion de bytes.'
# Cifrado: misma frase y sal = mismas claves; la frase, la sal o cualquier byte cambian el resultado; iteraciones minimas.
$h6Pass='frase de paso de prueba de H06r';$h6Salt=[byte[]](1..32)
$h6Crypto=New-ConfigCrypto $h6Pass $h6Salt 300000
Assert-H06r ($h6Crypto.Enc.Length -eq 32 -and $h6Crypto.Mac.Length -eq 32 -and $h6Crypto.Fp.Length -eq 32 -and -not (Test-BytesEqual $h6Crypto.Enc $h6Crypto.Mac) -and -not (Test-BytesEqual $h6Crypto.Mac $h6Crypto.Fp)) 'claves derivadas de 96 bytes, distintas entre si.'
$h6Fp=Get-SecretFingerprint $h6Crypto.Fp 'config\secrets.clixml' 'Owner' 'SECRETO' 'valor'
foreach($v in @(@('config\secrets.clixml','Owner','SECRETO','valor2'),@('config\secrets.clixml','App','SECRETO','valor'),@('config\otro.clixml','Owner','SECRETO','valor'),@('config\secrets.clixml','Owner','TEXTO','valor'))){
    Assert-H06r ((Get-SecretFingerprint $h6Crypto.Fp $v[0] $v[1] $v[2] $v[3]) -cne $h6Fp) "la huella no cambia con $($v -join '/')."
}
Assert-H06r ((Get-SecretFingerprint $h6Crypto.Mac 'config\secrets.clixml' 'Owner' 'SECRETO' 'valor') -cne $h6Fp -and $h6Fp -cmatch '^[0-9a-f]{64}$') 'la huella depende de la clave derivada de la frase.'
$h6Json='{"format":"SIGER-CONFIG-1","prueba":"contenido de prueba con tildes: n'+[char]0xF1+'"}'
$h6Text=Protect-ConfigPayload $h6Crypto $h6Json
Assert-H06r ((Unprotect-ConfigEnvelope (Read-ConfigEnvelope $h6Text) $h6Crypto) -ceq $h6Json) 'ida y vuelta del cifrado.'
Assert-H06r (-not $h6Text.Contains('contenido de prueba')) 'el contenido no viaja en claro.'
Assert-H06r (($h6Text|ConvertFrom-Json).iv -cne ((Protect-ConfigPayload $h6Crypto $h6Json)|ConvertFrom-Json).iv) 'cada cifrado debe usar un IV nuevo.'
function Get-H06rError([scriptblock]$Block){try{$null=& $Block;return 'SIN_ERROR'}catch{return ([string]$_.Exception.Message).Split(':')[0]}}
$h6Outer=$h6Text|ConvertFrom-Json
function New-H06rEnvelope([hashtable]$Change){$o=[ordered]@{};foreach($p in $h6Outer.PSObject.Properties){$o[$p.Name]=$p.Value};foreach($k in $Change.Keys){if($null -eq $Change[$k]){$o.Remove($k)}else{$o[$k]=$Change[$k]}};return ($o|ConvertTo-Json -Compress)}
function Edit-H06rBase64([string]$Value,[int]$Index){$b=[Convert]::FromBase64String($Value);$b[$Index]=$b[$Index] -bxor 1;return [Convert]::ToBase64String($b)}
foreach($c in @(
    @{n='data alterado';t=(New-H06rEnvelope @{data=(Edit-H06rBase64 $h6Outer.data 5)});e='INTEGRIDAD'},
    @{n='iv alterado';t=(New-H06rEnvelope @{iv=(Edit-H06rBase64 $h6Outer.iv 0)});e='INTEGRIDAD'},
    @{n='mac alterado';t=(New-H06rEnvelope @{mac=(Edit-H06rBase64 $h6Outer.mac 31)});e='INTEGRIDAD'},
    @{n='sal alterada';t=(New-H06rEnvelope @{salt=(Edit-H06rBase64 $h6Outer.salt 7)});e='INTEGRIDAD'},
    @{n='otro formato';t=(New-H06rEnvelope @{format='SIGER-SECRETOS-1'});e='FORMATO'},
    @{n='campo de mas';t=(New-H06rEnvelope @{extra='x'});e='FORMATO'},
    @{n='sin mac';t=(New-H06rEnvelope @{mac=$null});e='FORMATO'},
    @{n='299999 iteraciones';t=(New-H06rEnvelope @{iterations=299999});e='ITERACIONES'},
    @{n='iteraciones como texto';t=(New-H06rEnvelope @{iterations='300000'});e='ITERACIONES'},
    @{n='sal corta';t=(New-H06rEnvelope @{salt=[Convert]::ToBase64String([byte[]](1..16))});e='FORMATO'},
    @{n='no es JSON';t='no es un paquete';e='FORMATO'})){
    Assert-H06r ((Get-H06rError {Unprotect-ConfigEnvelope (Read-ConfigEnvelope $c.t) $h6Crypto}) -ceq $c.e) "envoltura con $($c.n) esperaba $($c.e)."
}
Assert-H06r ((Get-H06rError {Open-ConfigPackage $h6Text 'otra frase de paso de prueba'}) -ceq 'INTEGRIDAD') 'una frase incorrecta debe fallar por integridad.'
$h6Open=Open-ConfigPackage $h6Text $h6Pass
Assert-H06r ($h6Open.Json -ceq $h6Json -and (Test-BytesEqual $h6Open.Crypto.Fp $h6Crypto.Fp)) 'la frase correcta abre el paquete y deriva las mismas claves.'
Assert-H06r ((Get-H06rError {Unprotect-ConfigEnvelope (Read-ConfigEnvelope (New-H06rEnvelope @{iterations=300001})) $h6Crypto}) -ceq 'INTEGRIDAD') 'el HMAC debe cubrir las iteraciones.'
# Revision del contenido con un paquete sintetico (sin DPAPI): cada alteracion da su hallazgo.
$h6Secret='valor-secreto-sintetico-H06r-0123456789'
function New-H06rPayload{
    $inst=[Text.Encoding]::UTF8.GetBytes('{"namespace":"SIGER-A-2026","root":"D:\\SIGER-VALIDACION","ownerSid":"S-1-5-21-1-1005"}')
    $props=@(foreach($n in @('Owner','App','Migrator','Backup','Tls','Trust')){[ordered]@{name=$n;type='SECRETO';value=(ConvertTo-Utf8Base64 "$h6Secret-$n");fingerprint=(Get-SecretFingerprint $h6Crypto.Fp 'config\secrets.clixml' $n 'SECRETO' "$h6Secret-$n")}})
    $p=[ordered]@{format='SIGER-CONFIG-1';createdUtc='20261122T120000Z';computer='EQUIPO';account='EQUIPO\svc';accountSid='S-1-5-21-1-1005';ownerSid='S-1-5-21-1-1005';root='D:\SIGER-VALIDACION';namespace='SIGER-A-2026';database='siger_a_inst';omitted=@();items=@(
        [ordered]@{path='config\instance.json';kind='ARCHIVO';size=$inst.Length;sha256=(Get-Sha256Hex $inst);content=[Convert]::ToBase64String($inst)},
        [ordered]@{path='config\secrets.clixml';kind='CLIXML';size=10;sha256=('ab'*32);shape='OBJETO';properties=$props},
        [ordered]@{path='config\formulario-pull-token.clixml';kind='CLIXML';size=10;sha256=('cd'*32);shape='SECRETO';properties=@([ordered]@{name='';type='SECRETO';value=(ConvertTo-Utf8Base64 "$h6Secret-token");fingerprint=(Get-SecretFingerprint $h6Crypto.Fp 'config\formulario-pull-token.clixml' '' 'SECRETO' "$h6Secret-token")})})}
    return (($p|ConvertTo-Json -Depth 10 -Compress)|ConvertFrom-Json)
}
$h6Base=Test-ConfigPayload (New-H06rPayload) $h6Crypto.Fp
Assert-H06r (@($h6Base.Findings).Count -eq 0 -and $h6Base.Files -eq 3 -and $h6Base.Secrets -eq 7) "el paquete sintetico debe estar correcto ($(@($h6Base.Findings) -join ','))."
$h6Mutations=@(
    @{n='SHA-256 de un archivo';f={param($p) $p.items[0].sha256=('00'*32)};e='SHA256:config\instance.json'},
    @{n='tamano';f={param($p) $p.items[0].size=1};e='TAMANO:config\instance.json'},
    @{n='contenido';f={param($p) $p.items[0].content=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('{}'))};e='SHA256:config\instance.json'},
    @{n='huella de un secreto';f={param($p) $p.items[1].properties[0].fingerprint=('00'*32)};e='HUELLA:config\secrets.clixml#Owner'},
    @{n='valor de un secreto';f={param($p) $p.items[1].properties[2].value=(ConvertTo-Utf8Base64 'otro')};e='HUELLA:config\secrets.clixml#Migrator'},
    @{n='ruta con ..';f={param($p) $p.items[0].path='config\..\instance.json'};e='RUTA:config\..\instance.json'},
    @{n='ruta repetida';f={param($p) $p.items[2].path='config\secrets.clixml'};e='REPETIDO:config\secrets.clixml'},
    @{n='tipo cambiado';f={param($p) $p.items[0].kind='CLIXML'};e='TIPO:config\instance.json'},
    @{n='forma desconocida';f={param($p) $p.items[1].shape='LISTA'};e='FORMA:config\secrets.clixml'},
    @{n='tipo de propiedad';f={param($p) $p.items[1].properties[0].type='ENTERO'};e='FORMA:config\secrets.clixml'},
    @{n='SECRETO con nombre';f={param($p) $p.items[2].properties[0].name='X'};e='FORMA:config\formulario-pull-token.clixml'},
    @{n='sin cabecera';f={param($p) $p.ownerSid=''};e='CABECERA:ownerSid'},
    @{n='otro namespace';f={param($p) $p.namespace='OTRO'};e='CABECERA:namespace'},
    @{n='otro formato';f={param($p) $p.format='X'};e='CONTENIDO_FORMATO'},
    @{n='sin instance.json';f={param($p) $p.items[0].path='config\otro.json'};e='SIN_INSTANCE_JSON'})
foreach($m in $h6Mutations){
    $p=New-H06rPayload;& $m.f $p
    Assert-H06r (@((Test-ConfigPayload $p $h6Crypto.Fp).Findings) -ccontains $m.e) "con $($m.n) se esperaba el hallazgo $($m.e)."
}
Assert-H06r (@((Test-ConfigPayload (New-H06rPayload) $h6Crypto.Mac).Findings|Where-Object {$_ -like 'HUELLA:*'}).Count -eq 7) 'con otra clave todas las huellas deben fallar.'
Assert-H06r ((Get-PackageSecret (New-H06rPayload) 'config\secrets.clixml' 'Tls') -ceq "$h6Secret-Tls" -and $null -eq (Get-PackageSecret (New-H06rPayload) 'config\secrets.clixml' 'Nada')) 'lectura de un secreto del paquete.'
# H06r2: almacen sin clave conocida en el paquete sintetico (sus bytes no son un .p12: si se intentara abrir, fallaria).
function Add-H06rOpaqueP12($Payload,[string]$Path,[hashtable]$Extra){
    $b=[Text.Encoding]::UTF8.GetBytes('almacen sin clave conocida de H06r2')
    $o=[ordered]@{path=$Path;kind='P12';size=$b.Length;sha256=(Get-Sha256Hex $b);content=[Convert]::ToBase64String($b)}
    if($Extra){foreach($k in $Extra.Keys){$o[$k]=$Extra[$k]}}
    $Payload.items=@($Payload.items)+@((($o|ConvertTo-Json -Depth 5 -Compress)|ConvertFrom-Json))
    return $Payload
}
$h6Opaque=Test-ConfigPayload (Add-H06rOpaqueP12 (New-H06rPayload) 'config\https-red-candidato.p12' $null) $h6Crypto.Fp
Assert-H06r (@($h6Opaque.Findings).Count -eq 0 -and $h6Opaque.Files -eq 4 -and @($h6Opaque.P12).Count -eq 1 -and $h6Opaque.P12[0].NameState -ceq 'SIN_CLAVE' -and -not $h6Opaque.P12[0].Opens) "un almacen sin clave conocida debe aceptarse sin abrirlo ($(@($h6Opaque.Findings) -join ','))."
foreach($m in @(
    @{n='sin clave conocida con password';p='config\https-red-candidato.p12';x=@{password='Tls'};e='P12_CLAVE:config\https-red-candidato.p12'},
    @{n='sin clave conocida con certificates';p='config\https-red-candidato.p12';x=@{certificates=@()};e='P12_CLAVE:config\https-red-candidato.p12'},
    @{n='nombre conocido con otras mayusculas';p='config\HTTPS.p12';x=$null;e='P12_MAYUSCULAS:config\HTTPS.p12'},
    @{n='anterior con otras mayusculas y su clave';p='config\https-ANTERIOR-20261122-101010.p12';x=@{password='Tls'};e='P12_MAYUSCULAS:config\https-ANTERIOR-20261122-101010.p12'},
    @{n='conocido sin password';p='config\https.p12';x=$null;e='P12_CLAVE:config\https.p12'},
    @{n='conocido que no abre';p='config\https-anterior-20261122-101010.p12';x=@{password='Tls';certificates=@()};e='P12_NO_ABRE:config\https-anterior-20261122-101010.p12'})){
    Assert-H06r (@((Test-ConfigPayload (Add-H06rOpaqueP12 (New-H06rPayload) $m.p $m.x) $h6Crypto.Fp).Findings) -ccontains $m.e) "con un almacen $($m.n) se esperaba el hallazgo $($m.e)."
}
$h6OpaqueReport=Format-ConfigVerification (Add-H06rOpaqueP12 (New-H06rPayload) 'config\https-red-candidato.p12' $null) ([PSCustomObject]@{Content=$h6Opaque;Disk=[PSCustomObject]@{Equal=@('config\https-red-candidato.p12');Different=@();Missing=@();Unreadable=@();Extra=@();ListUnreadable=$false;P12=@([PSCustomObject]@{Path='config\https-red-candidato.p12';State='SIN_CLAVE_CONOCIDA';Equal=$true})};Ack='NO_APLICA';AckKeyId='';AckFindings=@();MySql=@();MySqlNote='';Problems=@()})
Assert-H06r ($h6OpaqueReport -ccontains 'Almacen config\https-red-candidato.p12: sin clave conocida, se conserva tal cual (tamano y SHA-256 verificados; no se abre ni se comparan certificados).' -and $h6OpaqueReport -ccontains 'Almacen del disco config\https-red-candidato.p12: SIN_CLAVE_CONOCIDA (comparado solo por SHA-256: IGUAL).' -and $h6OpaqueReport[-1] -ceq 'Resultado: CORRECTO.') "el informe debe mostrar el almacen sin clave conocida y seguir CORRECTO ($($h6OpaqueReport -join ' | '))."
# MySQL: la contrasena solo en MYSQL_PWD del proceso hijo, nunca en argumentos; --no-defaults primero; TLS VERIFY_CA.
$h6Psi=New-MySqlProbeStartInfo 'C:\no\existe\mysql.exe' 'siger_a_app' 13326 'D:\SIGER-VALIDACION\mysql\data\ca.pem'
Assert-H06r ($h6Psi.Arguments.StartsWith('--no-defaults ') -and $h6Psi.Arguments.Contains('--ssl-mode=VERIFY_CA') -and $h6Psi.Arguments.Contains('--host=127.0.0.1') -and $h6Psi.Arguments.Contains('--port=13326') -and $h6Psi.Arguments -notmatch '(?i)password|(^|\s)-p' -and -not $h6Psi.EnvironmentVariables.ContainsKey('MYSQL_PWD')) 'argumentos de la prueba de MySQL.'
Assert-H06r ((Invoke-MySqlProbe $h6Psi $h6Secret) -ceq 'NO_COMPROBADA' -and -not $h6Psi.EnvironmentVariables.ContainsKey('MYSQL_PWD') -and -not $h6Psi.Arguments.Contains($h6Secret) -and [string]::IsNullOrEmpty($env:MYSQL_PWD)) 'la contrasena no queda en el proceso ni en los argumentos.'
$cpText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'ConfigPackage.ps1'))
Assert-H06r ($cpText -notmatch '(?i)\$env:MYSQL_PWD' -and $cpText -notmatch '(?i)--password' -and $cpText -notmatch '(?i)defaults-extra-file') 'ConfigPackage.ps1 no usa la variable del proceso, --password ni un archivo de opciones.'
Assert-H06r ($cpText -match 'X509KeyStorageFlags\]::EphemeralKeySet' -and $cpText -notmatch '(?i)PersistKeySet|MachineKeySet|UserKeySet|DefaultKeySet') 'los .p12 se abren solo en memoria (EphemeralKeySet).'
# La verificacion no escribe ni cambia permisos: ninguna funcion que alcanza Test-InstanceConfig.ps1 escribe.
$cpAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'ConfigPackage.ps1'),[ref]$null,[ref]$null)
$cpFunctions=@{};foreach($fn in $cpAst.FindAll({param($a) $a -is [System.Management.Automation.Language.FunctionDefinitionAst]},$true)){$cpFunctions[$fn.Name]=$fn}
$h6WriteCommands=@('Set-Content','Add-Content','Out-File','Export-Clixml','Export-Csv','Set-Acl','New-Item','Copy-Item','Move-Item','Remove-Item','Rename-Item','Set-ItemProperty','Clear-Content','icacls','icacls.exe','takeown','takeown.exe','cacls','Tee-Object','Remove-SecretFile','Save-InstanceJson','Private-Directory')
$h6WriteMembers=@('WriteAllText','WriteAllBytes','WriteAllLines','AppendAllText','AppendAllLines','Delete','Move','Copy','Replace','Create','CreateText','CreateDirectory','SetAccessControl','SetAttributes','Encrypt','Decrypt','SetCreationTime','SetLastWriteTime')
function Get-H06rWrites($Ast){
    $w=@()
    foreach($c in $Ast.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst]},$true)){$n=$c.GetCommandName();if($n -and $h6WriteCommands -contains $n){$w+=$n};if($n -ceq 'New-Object' -and $c.Extent.Text -match 'FileStream|StreamWriter'){$w+='New-Object FileStream'}}
    foreach($m in $Ast.FindAll({param($a) $a -is [System.Management.Automation.Language.InvokeMemberExpressionAst]},$true)){if($m.Static -and $m.Expression.Extent.Text -match '(?i)IO\.(File|Directory)\]' -and $h6WriteMembers -contains [string]$m.Member.Extent.Text){$w+="$($m.Expression.Extent.Text)::$($m.Member.Extent.Text)"}}
    return $w
}
function Get-H06rReachable($Ast){
    $seen=@{};$queue=[System.Collections.Generic.Queue[object]]::new();$queue.Enqueue($Ast)
    while($queue.Count){$a=$queue.Dequeue();foreach($c in $a.FindAll({param($x) $x -is [System.Management.Automation.Language.CommandAst]},$true)){$n=$c.GetCommandName();if($n -and $cpFunctions.ContainsKey($n) -and -not $seen.ContainsKey($n)){$seen[$n]=$true;$queue.Enqueue($cpFunctions[$n].Body)}}}
    return @($seen.Keys)
}
$tiAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Test-InstanceConfig.ps1'),[ref]$null,[ref]$null)
$tiReach=Get-H06rReachable $tiAst
Assert-H06r ($tiReach -contains 'Invoke-ConfigVerification' -and $tiReach -contains 'Compare-ConfigWithDisk' -and $tiReach -contains 'Invoke-MySqlProbe' -and $tiReach -contains 'Invoke-AckKeyCheck') 'no se encontro la cadena de llamadas de la verificacion.'
$tiWrites=@(Get-H06rWrites $tiAst)+@(foreach($n in $tiReach){Get-H06rWrites $cpFunctions[$n].Body})
Assert-H06r ($tiWrites.Count -eq 0) "Test-InstanceConfig.ps1 escribe o cambia permisos ($($tiWrites -join ', '))."
$h6Control=[System.Management.Automation.Language.Parser]::ParseInput('function F{Set-Content -Path a -Value b;icacls x /grant y;[IO.File]::WriteAllText(''a'',''b'');[System.IO.Directory]::CreateDirectory(''d'');$s=New-Object IO.FileStream(''a'',1)}',[ref]$null,[ref]$null)
Assert-H06r ((@(Get-H06rWrites $h6Control) -join ',') -ceq 'Set-Content,icacls,New-Object FileStream,[IO.File]::WriteAllText,[System.IO.Directory]::CreateDirectory') 'control negativo: la regla de escritura no detecta las escrituras.'
# Orden en los scripts: cuenta duena antes de descifrar y de pedir la frase; -WhatIf antes de la frase; archivo nuevo
# (CreateNew); la verificacion rechaza a la cuenta duena antes de comprobar.
function Get-H06rOffset($Ast,[string]$Name){$c=@($Ast.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ceq $Name},$true));if($c.Count -ne 1){return -1};return $c[0].Extent.StartOffset}
$exAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Export-InstanceConfig.ps1'),[ref]$null,[ref]$null)
$exOwner=Get-H06rOffset $exAst 'Assert-InstanceOwner';$exPass=Get-H06rOffset $exAst 'Read-ConfigPassphrase';$exPayload=Get-H06rOffset $exAst 'New-InstanceConfigPayload'
$exShould=@($exAst.FindAll({param($a) $a -is [System.Management.Automation.Language.InvokeMemberExpressionAst] -and $a.Member.Extent.Text -ceq 'ShouldProcess'},$true))
Assert-H06r ($exOwner -ge 0 -and $exPass -gt $exOwner -and $exPayload -gt $exPass -and $exShould.Count -eq 1 -and $exShould[0].Extent.StartOffset -lt $exPass) 'Export-InstanceConfig.ps1: cuenta duena, -WhatIf, frase y contenido en ese orden.'
$exText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Export-InstanceConfig.ps1'))
Assert-H06r ($exText.Contains('[IO.FileMode]::CreateNew') -and $exText -notmatch '\[IO\.FileMode\]::(Create|OpenOrCreate|Truncate|Append)\b' -and $exText -match 'Read-ConfigPassphrase -Twice' -and @(Get-H06rWrites $exAst|Where-Object {$_ -match 'WriteAll'}).Count -eq 0) 'Export-InstanceConfig.ps1 debe crear un archivo nuevo (sin sobrescribir) y pedir la frase dos veces.'
$tiVerifier=Get-H06rOffset $tiAst 'Test-VerifierAccount';$tiVerify=Get-H06rOffset $tiAst 'Invoke-ConfigVerification';$tiPass=Get-H06rOffset $tiAst 'Read-ConfigPassphrase';$tiEnvelope=Get-H06rOffset $tiAst 'Read-ConfigEnvelope'
Assert-H06r ($tiEnvelope -ge 0 -and $tiPass -gt $tiEnvelope -and $tiVerifier -gt $tiPass -and $tiVerify -gt $tiVerifier -and ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Test-InstanceConfig.ps1')) -notmatch 'Read-ConfigPassphrase -Twice')) 'Test-InstanceConfig.ps1: formato antes de la frase y otra cuenta antes de comprobar.'
# El informe nunca lleva valores.
$h6Report=Format-ConfigVerification (New-H06rPayload) ([PSCustomObject]@{Content=$h6Base;Disk=[PSCustomObject]@{Equal=@('config\instance.json');Different=@();Missing=@();Unreadable=@();Extra=@();ListUnreadable=$false;P12=@()};Ack='COINCIDE';AckKeyId='siger-ack-1';AckFindings=@();MySql=@([PSCustomObject]@{User='siger_a_app';State='AUTENTICADA'});MySqlNote='';Problems=@()})
Assert-H06r (($h6Report -join "`n") -notmatch [regex]::Escape($h6Secret) -and ($h6Report[-1]) -ceq 'Resultado: CORRECTO.') 'el informe no debe llevar valores y debe terminar en el resultado.'
# --- H06s: importacion (Import-InstanceConfig.ps1) y contrasena de los servicios (Set-ServicePassword.ps1) ---
$h06sCount=0
function Assert-H06s([bool]$Condition,[string]$Message){if(-not $Condition){throw "Importacion y contrasena de los servicios (H06s): $Message"};$script:h06sCount++}
# Formato fijo: H06s no cambia SIGER-CONFIG-1. Paquete generado con Protect-ConfigPayload de ConfigPackage.ps1 de d792507
# (gate H06r2), con valores sinteticos; la frase es la de abajo. Si cambia el cifrado, la envoltura o el contenido, falla.
$h7FixturePass='frase de paso del paquete fijo SIGER-CONFIG-1'
$h7Fixture='{"format":"SIGER-CONFIG-1","iterations":300000,"salt":"8DV+wXPBqZ1n/NHFPiikDjtpa/aJD583KdFvIYXQMMA=","iv":"rM3WUslMVeR19W3ZQMcQRg==","data":"rvwgqr3wyIVcHmgvTdQAKeL7TMV63BIkgf++EwI5wULLvQpTzdgPxgkKyv1sglES6rAemwgivNW2kqFYGDIuSlwywNXTrm3EpvxgYVFoKdQhf7IqBRc3UtlAUK7ngShGpiG6Xt8pJWBJ4ZXQfY+i67MaoDQlTHbEf9vlDWZzkqnLB0QHqiaU429CPveo5hCj6gALX2WZ4e6zdsdB4fCuV13He89UfG/hJtPwOoXLRHGlKwP+9lwQJwV+W4pMKR5LfnI+ZEk6B+91hz+GJW994LdfD5N4VWfPffjCaOynTBd/O1SQMjOiIvjLGf0p3xBmiPbr9ngm1epxbkhLYEBWsUwDhH++YISvBNG4Ts1R/zGng0J5Qmtx3jH8q18DKTg6tQD8CLSZg5UzeC8MW3fgOiRZ0SzoxSQ3oOEf7Bptt2O8VUJ5bSXmT5mCny9/NnwwqzIhXPoDVQXTkT5HReHgZg3IOVPLixptwTvsapC0g/5TEkV8wdipBGlZpXnj/PufFHnB6PIMqjAsAmSy6MeCJch3S47712T8yetRXSr/Glhg+noRY09nW1/0892B/Ezy52TSXbLYDwy1Lt0sGWJsz7KP1YFKRK+zFBOs1TUbiXKGdqBCK4/FuJJfd3qqAY31oDIAPAQGXFriGcsbG/z4yNoq65KDovKeRSguA2Ca+gzpmYF51aJFjCmJ/pps60za484TW1FYWkZ9ZIRJ9GEHKdZAUjMaI0zVPKNVcwZIRNIVT+SaxR9Ol4UTTUh84KjqwnRWRc0nw3GUY+wi05VMHV+1gh61WMGrT9DOWCmv1dIzP/6itAf+wI6BG6zjXq/5vCiPJj1bB/b3c4ijLnP7Z1bFQ3H1/e28smyHPY01jNujssv8OOBMBCfXn2giSylFd1glD69FxP22eQNEfnAbhl/yZWQjMFOYAKgj2vOvN0XMC60PAO5vVLUAHkZXjmLiw062P86y5q8iL3xQl6SVKXJULIsiK83SkzYnTXtCvZvNmlM1MLFZwfLoG2N+ZYpO+20BUpGlr9X/JLcCCzesT8aykj8ZS+QnDPUfPPzQktiQTBk3DpBfpmQZeaKjw2zFlijgpbhMKj88sPhhzdTiT861Fl8BsUBV3AtTO9pLdIfDF5a4srOupXlUJ7PWBX1gIPfikA1i3Csx2hWt1HVg372/jzjO9xrJKBu1mVedo+QAtXC+If5uM4m953u6It4m1S2s5Trc3HucrPS8BVUr5sykMXjb4xne96wDJY/KHJaFI85WZlthP8XbP0AEid1gblGcCgHZ2wgNZgAdeON1NDl6AtH/toLnCnCU5RH3o04jg5DYJEDTGsxD+PKw73M5+0eL6acvoRdmkk/TdB4pZTcJssw3igm1K8nPOlNagZcLfAIcMO5mAI/RI8HAN1dXMr3deyN9cM4yiMpJP72ghUgzSEjGLlMgKj1XUjPXxq1FkgXLWDNKER8FOicH8RouDUTRVn43lpqUPAMILKFaF5/UY0eYaTw6iJRth4mLs5S3wvpJ3zsLHfVonWO7pIuQmb0cIDfL/KBZmoKzshmd6VP5aLizJKSgnkvfptJ4ZoxQHD5CHN3AVpHc5QedAt9bA70LJQSYcBFkBQv9ffi3ZVIQwNPKq+hjDqrEkJUgYPpBTr3aofcv1eImQQztt9b0Ft6Bm70xwgRLmIsJ5fYw+EO2T9ZA67KExSED/c2PHEiYpy86bWI9usaweH4WuVaj6BJ0E+RgKHJVfVtj0cXDOFRjR2FI96ie4DC1p/tsnJQ8w2Ldfb9MG6wZS55NvH7/kigLiUITpJYofVkMfos2PKY4UCbPjfQJiIgSR+PcQIbGlEbjBZ0kJhsaxTtkvFxk/RiJPv2aH/74/dRqvInKjQR78PAOg9DgkoRX5uxlCYkcWA0SHXJhOINy0KrpHCVJHdjncFYKsGKR2vLMx3yD3ZBxKO8ZqHms7eqSpUg1AUFZOFaN7K0s12OREZVPJ1dfnuSumc6XO+AP99JusF+nyqckB4fSXO61YmRA4UWRWrgVDF+Zn09CZIPIJaTEom05eVC/xeH8SUUszWvig2yBk2b5nCXg5ApCaq5xJDVHjFRmNtfh+eGj+NAvTVQjV9V0GwHFPB1drGuEsS9x/gePpaEoDtnGfSmi9axyKE6C/KTzWwq6L5j+qAbuZuMoUTK7WF4S/itWFwkVBuWWjEbOJKMKW4ir1KY9ZhYE/fRnzb/cEtMN3gKoS+U9519/3FqkbIq9Fwgda+3+9vmt8wOyBX5hYgZhsPmT2TvJNxaI6rthzaKu0EQf0hFtNL3HMZhII4foUZKoRGtSbbyEDqOoOw98PbxWAKH32fIO9x2DHfFh+kIz0B/cTnMTch8BPuJNqOmiaOv6+L+949TkQnGlKmYMHXOi1/ffMi4zZeRScT1t3HRrvJ820pswzwMBtE4qUWE2LdeY6ka5CBa5KbKS9cIRMgn/BMisLHJw/8epE90SPwo6Xmf5ViNpM8xKIvg5g63NEswJZsPf8saHL6nuXLcAgX9u2dZVdoWXl5gOZonc/13S5ODcMtqIV7RESR4vuczypU4P7FJndrakl+RyQL7tWfu0WyPlGjRAzLhwRzXq4P74wEk8UD9pzf21c8n8fB64h3fqcCVHcF93wVMu2aIDiU8hXTtk+vNv4CX7i6zGxaj9BTknpggmlW1bsMpGOcx5uwlCPDHVE/qZaofsQ1eZW4QfoD7H2kOJbECtC0Oqsh1/ZaYJOx3C1kjIS4uM1MSwrg3kzqSJus5FK78szNjBl/5CeBxDiCAsrYIVewDDvNNS0zhdUtptOfpHhrg4OnpVtfCl321o6Ro8/wHD5PHPiRU61QD0/s2yyy/y+szYYLI+MPqFOyvjPYpgoFeL7UIathpZh/gQxDdISjUm9z7VjIw14t7mze8GE9He1HFI5U8tr6zkMrLpK41xSzW6sJHn6zAQAqPDvtAnCrwdXFSByqKISbxh7u64ENNtw9pjUSEPdYh5uz4bo81L+E5SPegNccg6CvhDiuuwF2upN6SdKmlKmLjROXhgqnfc0/i7FR2W/NwN7PEFpP4VwwoyaOodhRGU8mGDAjG2abDvimMiOALz4EGNOoUJPv/ITWfSyfZHHuWTe53iczpLuINvDWHRrSc7VU65Pfxh4wAqpZlOEho16Ib6c+CDIOfX8XftteBxirrhbQkZsP4ZhuaCLTpLLZE+NSvj1D9OzYP1f7trMPQGJw//RDkKQZJnvdl61wW2xBMTZLE3aG9RCfZzpMy+r/hr6Dopu76jxL6Izf/fPQvDkhx/wopjdRB6MuZ1u3v8JTMdh1qoH8hCsIZXwOXO33+BBbk1lkQ/ljJAZ6XRdqxBgrDQGL5aavgiwMX90pfOGKR8YBwDUT275u9/vfkOEcDBJndlNHfJqJVJAdvrZkfRMvqSu/Ph0NLwPYbtoOigPwDa1nQYmouoYczL1b1MokmgmKWpttRyUttS3gI3bjxGbbnyvkXSYUVUx/JM/mreRj3ja1VGntfZG6Fy7TKyRKhn1MMNOakXOKI1WuLnAaAP5XT45P1gkpCriUmieBT+/uIshfh1jtMG9Orzqm+TtvFNSx5vQ22/NcSuEfmyliWISzXkwWxMdp2iAWJTSBnQ6wFMcy1EKNZs1hhAFhj2DCcj8EekIypiuf319AwBHXEotMJ+lLY5KWVCwnYmfJL5x1U4SQiWURoV4+GmhO6UD7MuONzC1Sn8ZSVeVp2oSJb2GajXnV8LTHSk3Ux969nNq0nE+55f13EfQ3tOt67U3GttbPalxlN29I5aS3xReSu+Xg83oU2FFzNj7Uij/URhYlrWeDvqVTL5uD1076sCaLH0tXXVllYIlO6gOXYd2qCJ+5DLBLrNCr91yWvgtXu3WRu3XfYerU0p+NpnsZ7lQotaeAwjDQBJdWAC5w0fSFL2UubLZjkvYE2SjUGhzl4R7Amwh4SAPp/f1ptxSpf0mNQ1UjxL1zBFHjIjwob7HWCDjbStfZoz1Xr3kegPuO5ZlUTWWp905uVKbT16VNfmLE0siiJWZvIGHSEPG/F/VtXYUv6G2IZPO2iX+5e8q/PX3M6e4JzGovRBMkheiYPOFAKqyXZIwRMj/i42wva2vBHDcqyj2fkUyOawnpueb5OKCggs/3ZiJeqx9Yfz8cRPJMxmY+rw5s+B89IOYXcjIld1W0na5DEadq90rYIzgDRBK7IzPEogFe30tAI6utkFK9RAEMZXyYF5hqeUS3hDWR7HTOsyUJYmfxVrT+fwoYS69Lchrvf7ltS9mvIbdZNU9aG5M4nFgsCPOZvViwAB2292PhW76p9gqLdFqIEGv2TozBq2LlRFpDGPfW9mbAFynQFNaj/MZK402Ndu5eCWQ31WSkAlscRTlnHc0QkBdXisFPJ70YkGMkoeouZ0SMTAktSOLDoMA7Dk7z2hJhZ6ShQY5Pl1Pb6wkxRmqmz7+0iSLSxIuUR9J0tvzwW7WGLcj32rd8gpwX9n8Od4JuBwWpwAAnbOTU/GgbY9bijZWWVeWB+x6D6RboMpNzGwILaa6/cwST/h/sMo7rUzIgJiE6zSQnePUisbYViWDONI0IPR/4ZN3LLXx30QW8tkypf/X9Eo0YKezzMOhnP5iw==","mac":"6jSS4W7203I2WJoL+UqhcpYsO2ty1vpJQFPqWoGO7Z8="}'
Assert-H06s ($script:ConfigFormat -ceq 'SIGER-CONFIG-1' -and $script:ConfigMinIterations -eq 300000) 'formato e iteraciones de SIGER-CONFIG-1.'
$h7Shapes=@{envoltura='data,format,iterations,iv,mac,salt';cabecera='account,accountSid,computer,createdUtc,database,format,items,namespace,omitted,ownerSid,root';ARCHIVO='content,kind,path,sha256,size';CLIXML='kind,path,properties,sha256,shape,size';propiedad='fingerprint,name,type,value';P12_SIN_CLAVE='content,kind,path,sha256,size';P12_CONOCIDO='certificates,content,kind,password,path,sha256,size';certificado='notAfter,notBefore,privateKey,thumbprint'}
function Get-H06sFields($Object){return ((@($Object.PSObject.Properties|ForEach-Object {$_.Name})|Sort-Object) -join ',')}
# Formas (tipo=campos) de un contenido descifrado; un tipo con dos formas distintas aparece dos veces.
function Get-H06sShapes($Payload){
    $out=@("cabecera=$(Get-H06sFields $Payload)")
    foreach($i in @($Payload.items)){
        $t=[string]$i.kind;if($t -ceq 'P12'){$t=if((Get-P12NameState ([string]$i.path)) -ceq 'SIN_CLAVE'){'P12_SIN_CLAVE'}else{'P12_CONOCIDO'}}
        $out+="$t=$(Get-H06sFields $i)"
        if($i.PSObject.Properties['properties']){foreach($p in @($i.properties)){$out+="propiedad=$(Get-H06sFields $p)"}}
        if($i.PSObject.Properties['certificates']){foreach($c in @($i.certificates)){$out+="certificado=$(Get-H06sFields $c)"}}
    }
    return @($out|Sort-Object -Unique)
}
function Test-H06sShapes($Payload,[string[]]$Required){
    foreach($s in (Get-H06sShapes $Payload)){$k,$v=$s.Split('=',2);if(-not $h7Shapes.ContainsKey($k) -or $h7Shapes[$k] -cne $v){return "forma no fijada: $s"}}
    $got=@((Get-H06sShapes $Payload)|ForEach-Object {$_.Split('=',2)[0]})
    foreach($r in $Required){if($got -cnotcontains $r){return "falta el tipo $r"}}
    return ''
}
$h7Env=Read-ConfigEnvelope $h7Fixture
Assert-H06s ((Get-H06sFields ($h7Fixture|ConvertFrom-Json)) -ceq $h7Shapes.envoltura -and $h7Env.Iterations -eq 300000) 'envoltura del paquete fijo.'
Assert-H06s ((Get-H06rError {Open-ConfigPackage $h7Fixture 'otra frase'}) -ceq 'INTEGRIDAD') 'el paquete fijo con otra frase debe fallar por integridad.'
$h7Open=Open-ConfigPackage $h7Fixture $h7FixturePass
$h7Payload=$h7Open.Json|ConvertFrom-Json
$h7Check=Test-ConfigPayload $h7Payload $h7Open.Crypto.Fp
Assert-H06s (@($h7Check.Findings).Count -eq 0 -and $h7Check.Files -eq 6 -and $h7Check.Secrets -eq 8 -and @($h7Check.P12|Where-Object {$_.NameState -ceq 'SIN_CLAVE'}).Count -eq 1) "el paquete fijo de d792507 debe abrir y revisarse sin hallazgos ($(@($h7Check.Findings) -join ','))."
$h7ShapeError=Test-H06sShapes $h7Payload @('cabecera','ARCHIVO','CLIXML','propiedad','P12_SIN_CLAVE')
Assert-H06s ($h7ShapeError -ceq '') "campos del paquete fijo ($h7ShapeError)."
Assert-H06s ((Get-PackageSecret $h7Payload 'config\secrets.clixml' 'Migrator') -ceq 'fijo-Migrator-0123456789' -and (Get-PackageSecret $h7Payload 'config\formulario-pull-token.clixml' '') -ceq 'token-fijo-0123456789' -and (Get-PackageSecret $h7Payload 'config\formulario-ack-identity.clixml' 'KeyId') -ceq 'siger-ack-fijo') 'valores del paquete fijo.'
# Control: un campo de mas en el contenido o en un elemento rompe la forma fijada.
$h7Bad=$h7Open.Json|ConvertFrom-Json;$h7Bad|Add-Member -NotePropertyName extra -NotePropertyValue 'x'
$h7Bad2=$h7Open.Json|ConvertFrom-Json;$h7Bad2.items[0]|Add-Member -NotePropertyName extra -NotePropertyValue 'x'
Assert-H06s ((Test-H06sShapes $h7Bad @()) -like 'forma no fijada: cabecera=*' -and (Test-H06sShapes $h7Bad2 @()) -like 'forma no fijada: ARCHIVO=*') 'control: un campo nuevo debe romper la forma fijada.'
# Valor de cada clixml con la forma de Export-Clixml: SecureString sola, u objeto con propiedades en orden.
$h7Tok=ConvertFrom-PackageClixml @($h7Payload.items|Where-Object {$_.path -ceq 'config\formulario-pull-token.clixml'})[0]
$h7Ack=ConvertFrom-PackageClixml @($h7Payload.items|Where-Object {$_.path -ceq 'config\formulario-ack-identity.clixml'})[0]
Assert-H06s ($h7Tok -is [Security.SecureString] -and (Secret-Text $h7Tok) -ceq 'token-fijo-0123456789') 'un clixml SECRETO vuelve como SecureString.'
Assert-H06s ((@($h7Ack.PSObject.Properties|ForEach-Object {$_.Name}) -join ',') -ceq 'KeyId,PrivatePem,PublicPem,Source,ImportedUtc' -and $h7Ack.KeyId -is [string] -and $h7Ack.PrivatePem -is [Security.SecureString] -and (Secret-Text $h7Ack.PrivatePem) -ceq 'privada-fija' -and $h7Ack.ImportedUtc -ceq '2026-11-22T00:00:00.0000000Z') 'un clixml OBJETO vuelve con sus propiedades en orden y sus tipos.'
Assert-H06s ((ConvertTo-ImportSecure '').Length -eq 0) 'un secreto vacio vuelve como SecureString vacia.'
# Accion por archivo: un clixml se reprotege siempre; el resto por SHA-256.
$h7Sha=('ab'*32)
foreach($c in @(@('CLIXML',$true,$h7Sha,$h7Sha,'REEMPLAZAR'),@('CLIXML',$false,'',$h7Sha,'RESTAURAR'),@('ARCHIVO',$true,$h7Sha,$h7Sha,'SIN_CAMBIO'),@('ARCHIVO',$true,('cd'*32),$h7Sha,'REEMPLAZAR'),
    @('ARCHIVO',$false,'',$h7Sha,'RESTAURAR'),@('ARCHIVO',$true,'',$h7Sha,'REEMPLAZAR'),@('ARCHIVO',$true,'',' ','REEMPLAZAR'),@('P12',$true,$h7Sha,$h7Sha,'SIN_CAMBIO'),@('P12',$true,('cd'*32),$h7Sha,'REEMPLAZAR'),@('P12',$false,'',$h7Sha,'RESTAURAR'))){
    Assert-H06s ((Get-ImportItemAction $c[0] $c[1] $c[2] $c[3]) -ceq $c[4]) "accion de $($c[0]) (existe $($c[1])) esperaba $($c[4])."
}
# Instancia detenida.
foreach($c in @(@('NINGUNO',$false,$false,''),@('DETENIDOS',$false,$false,''),@('ACTIVOS',$false,$false,'SERVICIO_ACTIVO'),@('INCONSISTENTE',$false,$false,'SERVICIOS_INCONSISTENTES'),@('OTRO',$false,$false,'SERVICIOS_DESCONOCIDOS'),
    @('NINGUNO',$true,$false,'APLICACION_ACTIVA'),@('NINGUNO',$false,$true,'MOTOR_ACTIVO'),@('ACTIVOS',$true,$true,'SERVICIO_ACTIVO,APLICACION_ACTIVA,MOTOR_ACTIVO'),@('NINGUNO',$null,$null,'APLICACION_ACTIVA,MOTOR_ACTIVO'))){
    Assert-H06s ((@(Get-ImportRunBlockers ([PSCustomObject]@{Services=$c[0];App=$c[1];Motor=$c[2]})) -join ',') -ceq $c[3]) "instancia $($c[0])/$($c[1])/$($c[2]) esperaba '$($c[3])'."
}
# Paquete de esta instancia y de esta cuenta (paquete fijo: raiz D:\SIGER-FIXTURE, cuenta ...-1001).
$h7Sid='S-1-5-21-1000-2000-3000-1001';$h7Set=[PSCustomObject]@{ownerSid=$h7Sid}
foreach($c in @(
    @{n='correcto';p={$h7Open.Json|ConvertFrom-Json};s=$h7Set;u=$h7Sid;r='D:\SIGER-FIXTURE';f=@();e=''},
    @{n='raiz con barra final';p={$h7Open.Json|ConvertFrom-Json};s=$h7Set;u=$h7Sid;r='D:\SIGER-FIXTURE\';f=@();e=''},
    @{n='con hallazgos';p={$h7Open.Json|ConvertFrom-Json};s=$h7Set;u=$h7Sid;r='D:\SIGER-FIXTURE';f=@('P12_MAYUSCULAS:config\HTTPS.p12');e='CONTENIDO'},
    @{n='otra cuenta';p={$h7Open.Json|ConvertFrom-Json};s=[PSCustomObject]@{ownerSid='S-1-5-21-9-9-9-9'};u='S-1-5-21-9-9-9-9';r='D:\SIGER-FIXTURE';f=@();e='CUENTA_DEL_PAQUETE,DESCRIPTOR_DEL_PAQUETE'},
    @{n='exportado por otra cuenta';p={$x=$h7Open.Json|ConvertFrom-Json;$x.accountSid='S-1-5-21-9-9-9-9';$x};s=$h7Set;u=$h7Sid;r='D:\SIGER-FIXTURE';f=@();e='CUENTA_DEL_PAQUETE'},
    @{n='duena del disco distinta';p={$h7Open.Json|ConvertFrom-Json};s=[PSCustomObject]@{ownerSid='S-1-5-21-9-9-9-9'};u=$h7Sid;r='D:\SIGER-FIXTURE';f=@();e='CUENTA_DEL_PAQUETE'},
    @{n='sin cuenta actual';p={$h7Open.Json|ConvertFrom-Json};s=$h7Set;u='';r='D:\SIGER-FIXTURE';f=@();e='CUENTA_DEL_PAQUETE,DESCRIPTOR_DEL_PAQUETE'},
    @{n='otra raiz';p={$h7Open.Json|ConvertFrom-Json};s=$h7Set;u=$h7Sid;r='D:\SIGER';f=@();e='RAIZ_DISTINTA,DESCRIPTOR_DEL_PAQUETE'},
    @{n='sin instance.json';p={$x=$h7Open.Json|ConvertFrom-Json;$x.items=@($x.items|Where-Object {$_.path -cne 'config\instance.json'});$x};s=$h7Set;u=$h7Sid;r='D:\SIGER-FIXTURE';f=@();e='DESCRIPTOR_DEL_PAQUETE'})){
    Assert-H06s ((@(Get-ImportPackageBlockers (& $c.p) $c.s $c.u $c.r $c.f) -join ',') -ceq $c.e) "paquete $($c.n) esperaba '$($c.e)'."
}
# Comparacion despues de importar: el paquete consigo mismo es igual; cada diferencia se informa; lo extra va aparte.
$h7Cmp=Compare-ConfigPayloadItems $h7Payload ($h7Open.Json|ConvertFrom-Json)
Assert-H06s (@($h7Cmp.Findings).Count -eq 0 -and $h7Cmp.Files -eq 6 -and $h7Cmp.Secrets -eq 8 -and (@($h7Cmp.Opaque) -join ',') -ceq 'config\https-red-candidato.p12' -and @($h7Cmp.Extra).Count -eq 0) 'comparacion de un contenido igual.'
foreach($m in @(
    @{n='huella';f={param($d) @($d.items|Where-Object {$_.path -ceq 'config\secrets.clixml'})[0].properties[1].fingerprint=('00'*32)};e='CLIXML_DISTINTO:config\secrets.clixml'},
    @{n='nombre de propiedad';f={param($d) @($d.items|Where-Object {$_.path -ceq 'config\formulario-ack-identity.clixml'})[0].properties[0].name='Otro'};e='CLIXML_DISTINTO:config\formulario-ack-identity.clixml'},
    @{n='tipo de propiedad';f={param($d) @($d.items|Where-Object {$_.path -ceq 'config\formulario-ack-identity.clixml'})[0].properties[2].type='SECRETO'};e='CLIXML_DISTINTO:config\formulario-ack-identity.clixml'},
    @{n='propiedad de menos';f={param($d) $i=@($d.items|Where-Object {$_.path -ceq 'config\secrets.clixml'})[0];$i.properties=@($i.properties|Select-Object -First 5)};e='CLIXML_DISTINTO:config\secrets.clixml'},
    @{n='forma';f={param($d) @($d.items|Where-Object {$_.path -ceq 'config\formulario-pull-token.clixml'})[0].shape='OBJETO'};e='CLIXML_DISTINTO:config\formulario-pull-token.clixml'},
    @{n='SHA-256';f={param($d) @($d.items|Where-Object {$_.path -ceq 'mysql\my.ini'})[0].sha256=('00'*32)};e='DISTINTO:mysql\my.ini'},
    @{n='almacen sin clave cambiado';f={param($d) @($d.items|Where-Object {$_.path -ceq 'config\https-red-candidato.p12'})[0].sha256=('00'*32)};e='DISTINTO:config\https-red-candidato.p12'},
    @{n='falta';f={param($d) $d.items=@($d.items|Where-Object {$_.path -cne 'mysql\my.ini'})};e='FALTA:mysql\my.ini'},
    @{n='tipo';f={param($d) @($d.items|Where-Object {$_.path -ceq 'mysql\my.ini'})[0].kind='P12'};e='TIPO:mysql\my.ini'})){
    $d=$h7Open.Json|ConvertFrom-Json;& $m.f $d
    Assert-H06s (@((Compare-ConfigPayloadItems $h7Payload $d).Findings) -ccontains $m.e) "comparacion con $($m.n) esperaba $($m.e)."
}
$d=$h7Open.Json|ConvertFrom-Json;$d.items=@($d.items)+@([PSCustomObject]@{path='config\nuevo.json';kind='ARCHIVO';sha256=('00'*32)})
$h7Cmp=Compare-ConfigPayloadItems $h7Payload $d
Assert-H06s (@($h7Cmp.Findings).Count -eq 0 -and (@($h7Cmp.Extra) -join ',') -ceq 'config\nuevo.json') 'un archivo del disco que no esta en el paquete se informa aparte, sin observacion.'
$h7CertA=@([PSCustomObject]@{thumbprint='AA';notBefore='20261101T000000Z';notAfter='20271101T000000Z';privateKey=$true})
$h7P12=[PSCustomObject]@{items=@([PSCustomObject]@{path='config\https.p12';kind='P12';sha256=$h7Sha;password='Tls';certificates=$h7CertA})}
$h7P12b=[PSCustomObject]@{items=@([PSCustomObject]@{path='config\https.p12';kind='P12';sha256=$h7Sha;password='Tls';certificates=@([PSCustomObject]@{thumbprint='BB';notBefore='20261101T000000Z';notAfter='20271101T000000Z';privateKey=$true})})}
Assert-H06s (@((Compare-ConfigPayloadItems $h7P12 $h7P12).Findings).Count -eq 0 -and (@((Compare-ConfigPayloadItems $h7P12 $h7P12b).Findings) -join ',') -ceq 'P12_CERTIFICADOS:config\https.p12') 'un almacen conocido se compara por sus certificados.'
# Plan e informe sin valores.
$h7PlanText=(Format-ImportPlan ([PSCustomObject]@{Items=@(foreach($i in $h7Payload.items){[PSCustomObject]@{Path=$i.path;Kind=$i.kind;Action=(Get-ImportItemAction $i.kind $true '' $i.sha256);Opaque=($i.kind -ceq 'P12')}});Extra=@('config\nuevo.json')})) -join "`n"
Assert-H06s ($h7PlanText -match 'REEMPLAZAR  CLIXML  config\\secrets\.clixml, se vuelve a proteger con la DPAPI de esta sesion' -and $h7PlanText -match 'config\\https-red-candidato\.p12, sin clave conocida: solo bytes y SHA-256, sin abrirlo' -and $h7PlanText -match 'SE_CONSERVA ARCHIVO config\\nuevo\.json' -and $h7PlanText -notmatch 'fijo-|token-fijo|privada-fija') "plan de la importacion ($h7PlanText)."
$h7Report=(Format-ImportedConfig ([PSCustomObject]@{Findings=@();Files=6;Secrets=8;Opaque=@('config\https-red-candidato.p12');Extra=@();Ack='COINCIDE'})) -join "`n"
Assert-H06s ($h7Report -match 'Almacen config\\https-red-candidato\.p12: SIN_CLAVE_CONOCIDA' -and $h7Report -match '(?m)^Resultado: CORRECTO\.$' -and (Format-ImportedConfig ([PSCustomObject]@{Findings=@('ACL:config\secrets.clixml');Files=1;Secrets=1;Opaque=@();Extra=@();Ack='NO_APLICA'}))[-1] -ceq 'Resultado: CON OBSERVACIONES: ACL:config\secrets.clixml.') 'informe despues de importar.'
Assert-H06s ($script:ImportCurrentSecretsNote -match 'se vuelven a proteger igual' -and $script:ImportCurrentSecretsNote -notmatch '(?i)cierra|runas|vuelve a importar') 'la nota de una sesion que ya descifra es informativa: no pide cerrar la sesion ni reimportar.'
# Comprobacion como la cuenta (Set-ServicePassword.ps1): debe descifrar TODOS los clixml en un inicio de sesion nuevo.
$h7Ok=@{sid=$h7Sid;administrator=$false;dpapi=$true;instanceOwner=$true;clixmlTotal=3;clixmlDecrypted=3}
foreach($c in @(@{n='apta';x=@{};e=''},@{n='otra cuenta';x=@{sid='S-1-5-21-9-9-9-9'};e='OTRA_CUENTA'},@{n='administradora';x=@{administrator=$true};e='ADMINISTRADOR'},@{n='no duena';x=@{instanceOwner=$false};e='NO_ES_DUENA'},
    @{n='sin secrets';x=@{dpapi=$false};e='DPAPI'},@{n='un clixml sin descifrar';x=@{clixmlDecrypted=2};e='DPAPI'},@{n='ningun clixml';x=@{clixmlTotal=0;clixmlDecrypted=0};e='DPAPI'},@{n='sin conteo';x=@{clixmlTotal=$null};e='DPAPI'},
    @{n='conteo como texto';x=@{clixmlTotal='3'};e='DPAPI'},@{n='administrador ausente';x=@{administrator=$null};e='ADMINISTRADOR'})){
    $o=@{};foreach($k in $h7Ok.Keys){$o[$k]=$h7Ok[$k]};foreach($k in $c.x.Keys){$o[$k]=$c.x[$k]}
    Assert-H06s ((@(Get-ServiceIdentityBlockers ([PSCustomObject]$o) $h7Sid) -join ',') -ceq $c.e) "comprobacion $($c.n) esperaba '$($c.e)'."
}
Assert-H06s ((@(Get-ServiceIdentityBlockers $null $h7Sid) -join ',') -ceq 'SIN_RESPUESTA' -and (@(Get-ServiceIdentityBlockers ([PSCustomObject]$h7Ok) '') -join ',') -ceq 'OTRA_CUENTA') 'comprobacion sin respuesta o sin cuenta.'
# Servicios antes de actualizar la contrasena.
function New-H06sServiceFacts([hashtable]$Change){
    $f=@{Sid=$h7Sid;AdminSid='S-1-5-21-1000-2000-3000-500';OwnerSid=$h7Sid;RecordSid=$h7Sid;Services=@([PSCustomObject]@{Name='SIGERMySQL';State='Stopped';StartSid=$h7Sid},[PSCustomObject]@{Name='SIGER';State='Stopped';StartSid=$h7Sid})}
    foreach($k in $Change.Keys){$f[$k]=$Change[$k]};return [PSCustomObject]$f
}
foreach($c in @(@{n='correcto';x=@{};e=''},@{n='sin cuenta';x=@{Sid=''};e='CUENTA_NO_RESUELTA'},@{n='cuenta del administrador';x=@{AdminSid=$h7Sid};e='MISMA_CUENTA_ADMINISTRATIVA'},@{n='no duena';x=@{OwnerSid='S-1-5-21-9-9-9-9'};e='NO_ES_DUENA'},
    @{n='otra registrada';x=@{RecordSid='S-1-5-21-9-9-9-9'};e='OTRA_CUENTA_REGISTRADA'},
    @{n='motor activo';x=@{Services=@([PSCustomObject]@{Name='SIGERMySQL';State='Running';StartSid=$h7Sid},[PSCustomObject]@{Name='SIGER';State='Stopped';StartSid=$h7Sid})};e='SERVICIO_ACTIVO:SIGERMySQL'},
    @{n='SIGER deteniendose';x=@{Services=@([PSCustomObject]@{Name='SIGERMySQL';State='Stopped';StartSid=$h7Sid},[PSCustomObject]@{Name='SIGER';State='Stop Pending';StartSid=$h7Sid})};e='SERVICIO_ACTIVO:SIGER'},
    @{n='servicio de otra cuenta';x=@{Services=@([PSCustomObject]@{Name='SIGERMySQL';State='Stopped';StartSid=''},[PSCustomObject]@{Name='SIGER';State='Stopped';StartSid=$h7Sid})};e='SERVICIO_OTRA_CUENTA:SIGERMySQL'},
    @{n='un solo servicio';x=@{Services=@([PSCustomObject]@{Name='SIGER';State='Stopped';StartSid=$h7Sid})};e='SERVICIOS'})){
    Assert-H06s ((@(Get-ServicePasswordBlockers (New-H06sServiceFacts $c.x)) -join ',') -ceq $c.e) "servicios $($c.n) esperaba '$($c.e)'."
}
# Orden de los scripts (AST): cuenta duena e instancia detenida antes de la frase; confirmacion antes de escribir; la
# instancia se vuelve a comprobar despues de confirmar. Set-ServicePassword.ps1: reglas, comprobacion como la cuenta y
# confirmacion antes de cambiar; la contrasena nunca en una linea de sc.exe.
$imAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Import-InstanceConfig.ps1'),[ref]$null,[ref]$null)
$imCalls=@($imAst.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst]},$true))
$imMember=@($imAst.FindAll({param($a) $a -is [System.Management.Automation.Language.InvokeMemberExpressionAst] -and $a.Member.Value -ceq 'ShouldProcess'},$true))
function Get-H06sOffsets($Calls,[string]$Name){return @($Calls|Where-Object {$_.GetCommandName() -ceq $Name}|ForEach-Object {$_.Extent.StartOffset})}
$imOwner=@(Get-H06sOffsets $imCalls 'Assert-InstanceOwner');$imStop=@(Get-H06sOffsets $imCalls 'Assert-InstanceStopped');$imPass=@(Get-H06sOffsets $imCalls 'Read-ConfigPassphrase');$imApply=@(Get-H06sOffsets $imCalls 'Invoke-ConfigImport');$imVerify=@(Get-H06sOffsets $imCalls 'Test-ImportedConfig');$imBlock=@(Get-H06sOffsets $imCalls 'Get-ImportPackageBlockers')
Assert-H06s ($imOwner.Count -eq 1 -and $imPass.Count -eq 1 -and $imStop.Count -eq 2 -and $imApply.Count -eq 1 -and $imMember.Count -eq 1 -and $imOwner[0] -lt $imStop[0] -and $imStop[0] -lt $imPass[0] -and $imPass[0] -lt $imBlock[0] -and $imBlock[0] -lt $imMember[0].Extent.StartOffset -and $imMember[0].Extent.StartOffset -lt $imStop[1] -and $imStop[1] -lt $imApply[0] -and $imApply[0] -lt $imVerify[0]) 'Import-InstanceConfig.ps1: cuenta, instancia detenida, frase, reglas del paquete, confirmacion, nueva comprobacion, importacion y verificacion en ese orden.'
$imText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Import-InstanceConfig.ps1'))
Assert-H06s ($imText -match "ConfirmImpact='High'" -and $imText -notmatch 'Read-ConfigPassphrase -Twice' -and $imText -notmatch '(?i)Remove-Item') 'Import-InstanceConfig.ps1: confirmacion High, frase una vez y sin Remove-Item.'
$h7Fn=@{};foreach($f in $cpAst.FindAll({param($a) $a -is [System.Management.Automation.Language.FunctionDefinitionAst]},$true)){$h7Fn[$f.Name]=$f}
$h7Import=$h7Fn['Invoke-ConfigImport'].Extent.Text
Assert-H06s ($h7Import -notmatch '(?i)Remove-Item|::Delete\(\$i\.|::Delete\(\$old' -and $h7Import -match '\[IO\.File\]::Move\(\$i\.Full,\$dest\)' -and $h7Import -match 'Export-Clixml -LiteralPath \$staged -WhatIf:\$false -Confirm:\$false' -and $h7Import -match 'Set-PrivateFileAcl \$i\.Full;Require-PrivateFile \$i\.Full') 'Invoke-ConfigImport: conserva lo anterior (mueve, no borra), protege cada clixml y exige su ACL privada.'
Assert-H06s (@($h7Fn['Test-ImportedConfig'].FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and @('Set-Content','Set-Acl','Export-Clixml','Out-File','Remove-Item','Move-Item','Copy-Item') -ccontains $a.GetCommandName()},$true)).Count -eq 0 -and @(Get-H06rWrites $h7Fn['Test-ImportedConfig'].Body).Count -eq 0 -and @(foreach($n in (Get-H06rReachable $h7Fn['Test-ImportedConfig'].Body)){Get-H06rWrites $cpFunctions[$n].Body}).Count -eq 0) 'Test-ImportedConfig no escribe (ni las funciones que alcanza).'
$spAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Set-ServicePassword.ps1'),[ref]$null,[ref]$null)
$spCalls=@($spAst.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst]},$true))
$spShould=@($spAst.FindAll({param($a) $a -is [System.Management.Automation.Language.InvokeMemberExpressionAst] -and $a.Member.Value -ceq 'ShouldProcess'},$true))
$spRules=@(Get-H06sOffsets $spCalls 'Get-ServicePasswordBlockers');$spCheck=@(Get-H06sOffsets $spCalls 'Invoke-ServiceIdentityCheck');$spIdRules=@(Get-H06sOffsets $spCalls 'Get-ServiceIdentityBlockers');$spChange=@(Get-H06sOffsets $spCalls 'Invoke-CimMethod')
Assert-H06s ($spRules.Count -eq 1 -and $spCheck.Count -eq 1 -and $spIdRules.Count -eq 1 -and $spChange.Count -eq 1 -and $spShould.Count -eq 1 -and $spRules[0] -lt $spCheck[0] -and $spCheck[0] -lt $spIdRules[0] -and $spIdRules[0] -lt $spShould[0].Extent.StartOffset -and $spShould[0].Extent.StartOffset -lt $spChange[0]) 'Set-ServicePassword.ps1: reglas, comprobacion como la cuenta y confirmacion antes de cambiar la contrasena.'
$spText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Set-ServicePassword.ps1'))
Assert-H06s ($spText -match "MethodName Change -Arguments @\{StartName=\`$b\.StartName;StartPassword=\`$net\.Password\}" -and $spText -notmatch '(?i)sc\.exe[^\r\n]*password=' -and $spText -notmatch '(?i)sc\.exe\s+config' -and $spText -notmatch '(?i)New-Service|Remove-Service|sc\.exe\s+(delete|create)' -and $spText -notmatch '(?i)Start-Service') 'Set-ServicePassword.ps1: solo cambia la contrasena con Win32_Service.Change, sin sc.exe config, sin reinstalar ni iniciar.'
$isText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Install-Service.ps1'))
Assert-H06s (@($isAst.FindAll({param($a) $a -is [System.Management.Automation.Language.CommandAst] -and $a.GetCommandName() -ceq 'Invoke-ServiceIdentityCheck'},$true)).Count -eq 1 -and $isText -notmatch 'ProcessStartInfo') 'Install-Service.ps1 usa la comprobacion compartida (Invoke-ServiceIdentityCheck) y no la repite.'
$tsText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Test-ServiceIdentity.ps1'))
Assert-H06s ($tsText -match 'clixmlTotal=\$clixmlTotal;clixmlDecrypted=\$clixmlDecrypted' -and $tsText -match "GetFiles\(\(Join-Path \`$Root 'config'\),'\*\.clixml'\)") 'Test-ServiceIdentity.ps1 informa cuantos clixml de config\ descifra.'
Clear-ConfigCrypto $h7Open.Crypto
# --- fin H06s (la ida y vuelta con DPAPI real esta en el bloque de Windows de H06r) ---
# Identidad ACK (AckKeyCheck.java con un Java disponible): par correcto, cruzado y basura.
$h6JavaBin=@((Join-Path $project '.tools\java25\bin'),$(if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}))|Where-Object {$_ -and (Test-Path -LiteralPath (Join-Path $_ $(if($onWindows){'java.exe'}else{'java'})))}|Select-Object -First 1
$h6Ack='sin Java: AckKeyCheck.java no se ejercito'
$h6Dir=Join-Path ([IO.Path]::GetTempPath()) ('siger-h06r-'+[Guid]::NewGuid().ToString('N'));[void][IO.Directory]::CreateDirectory($h6Dir)
try{
    if($h6JavaBin){
        $h6Java=Join-Path $h6JavaBin $(if($onWindows){'java.exe'}else{'java'})
        $h6Keys=@{}
        foreach($k in @('a','b')){
            $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
            try{& $h6Java (Join-Path $PSScriptRoot 'FormularioAckKey.java') (Join-Path $h6Dir "$k-priv.pem") (Join-Path $h6Dir "$k-pub.pem") *> $null}finally{$ErrorActionPreference=$old}
            $h6Keys[$k]=@([IO.File]::ReadAllText((Join-Path $h6Dir "$k-priv.pem")),[IO.File]::ReadAllText((Join-Path $h6Dir "$k-pub.pem")))
        }
        $src=Join-Path $PSScriptRoot 'AckKeyCheck.java'
        Assert-H06r ((Invoke-AckKeyCheck $h6Java $src $h6Keys.a[0] $h6Keys.a[1]) -ceq 'COINCIDE') 'un par ACK correcto debe COINCIDIR.'
        Assert-H06r ((Invoke-AckKeyCheck $h6Java $src $h6Keys.a[0] $h6Keys.b[1]) -ceq 'NO_COINCIDE') 'un par ACK cruzado no debe coincidir.'
        Assert-H06r ((Invoke-AckKeyCheck $h6Java $src 'basura' $h6Keys.a[1]) -ceq 'INVALIDA') 'una privada ilegible debe dar INVALIDA.'
        Assert-H06r ((Invoke-AckKeyCheck $h6Java $src ($h6Keys.a[0]+$h6Keys.b[0]) $h6Keys.a[1]) -ceq 'INVALIDA') 'dos privadas deben dar INVALIDA.'
        Assert-H06r ((Invoke-AckKeyCheck (Join-Path $h6Dir 'no-java.exe') $src $h6Keys.a[0] $h6Keys.a[1]) -ceq 'NO_COMPROBADA') 'sin Java debe dar NO_COMPROBADA.'
        $h6Ack='AckKeyCheck.java con pares reales'
    }
    if($onWindows -and $h6JavaBin){
        # Ida completa como la cuenta actual (DPAPI real) sobre una instancia temporal: inventario, contenido, cifrado,
        # apertura, revision, comparacion con el disco y salida sin secretos. MySQL no se prueba aqui (ensayo de H06r).
        $root=Join-Path $h6Dir 'instancia';foreach($d in @('config','mysql','tmp')){[void][IO.Directory]::CreateDirectory((Join-Path $root $d))}
        $me=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        $vals=@{};foreach($n in @('Owner','App','Migrator','Backup','Tls','Trust')){$vals[$n]="$h6Secret-$n-"+[Guid]::NewGuid().ToString('N')}
        [PSCustomObject]@{Owner=(ConvertTo-SecureString $vals.Owner -AsPlainText -Force);App=(ConvertTo-SecureString $vals.App -AsPlainText -Force);Migrator=(ConvertTo-SecureString $vals.Migrator -AsPlainText -Force);Backup=(ConvertTo-SecureString $vals.Backup -AsPlainText -Force);Tls=(ConvertTo-SecureString $vals.Tls -AsPlainText -Force);Trust=(ConvertTo-SecureString $vals.Trust -AsPlainText -Force)}|Export-Clixml (Join-Path $root 'config\secrets.clixml')
        [PSCustomObject]@{namespace='SIGER-A-2026';root=$root;mysqlHome=(Join-Path $h6Dir 'sin-mysql');mysqlPort=13399;javaHome=(Split-Path $h6JavaBin -Parent);database='siger_a_inst';mode='INSTITUCIONAL';purpose='VALIDACION';ownerSid=$me}|ConvertTo-Json|Set-Content -Encoding UTF8 (Join-Path $root 'config\instance.json')
        [IO.File]::WriteAllText((Join-Path $root 'config\formulario-pull.json'),'{"origin":"https://formulario.invalid","scope":"UPR-REQ","ackKeyId":"siger-ack-1","mode":"MANUAL"}')
        [IO.File]::WriteAllText((Join-Path $root 'config\formulario-pull.json.bak-atomic-0123'),'{}')
        $vals.Token="$h6Secret-token-"+[Guid]::NewGuid().ToString('N');(ConvertTo-SecureString $vals.Token -AsPlainText -Force)|Export-Clixml (Join-Path $root 'config\formulario-pull-token.clixml')
        [PSCustomObject]@{KeyId='siger-ack-1';PrivatePem=(ConvertTo-SecureString $h6Keys.a[0] -AsPlainText -Force);PublicPem=$h6Keys.a[1];Source='prueba';ImportedUtc=(Get-Date).ToUniversalTime().ToString('o')}|Export-Clixml (Join-Path $root 'config\formulario-ack-identity.clixml')
        [IO.File]::WriteAllText((Join-Path $root 'mysql\my.ini'),"[mysqld]`r`nport=13399`r`n")
        $rot=Join-Path $root 'config\rotacion-20261122T000000Z';[void][IO.Directory]::CreateDirectory($rot);[IO.File]::WriteAllText((Join-Path $rot 'secrets.clixml'),'no es un clixml')
        $keytool=Join-Path $h6JavaBin 'keytool.exe';$pf=Join-Path $root 'tmp\k.txt'
        try{
            [IO.File]::WriteAllText($pf,$vals.Tls)
            $null=Invoke-Native {& $keytool -genkeypair -alias siger-a-local -keyalg RSA -keysize 2048 -validity 30 -dname 'CN=localhost,OU=SIGER' -storetype PKCS12 -keystore (Join-Path $root 'config\https.p12') '-storepass:file' $pf -noprompt} -Quiet
            $null=Invoke-Native {& $keytool -exportcert -rfc -alias siger-a-local -keystore (Join-Path $root 'config\https.p12') '-storepass:file' $pf -file (Join-Path $root 'tmp\ca.pem')} -Quiet
            [IO.File]::WriteAllText($pf,$vals.Trust)
            $null=Invoke-Native {& $keytool -importcert -alias mysql-local-ca -file (Join-Path $root 'tmp\ca.pem') -keystore (Join-Path $root 'config\mysql-trust.p12') -storetype PKCS12 '-storepass:file' $pf -noprompt} -Quiet
        }finally{Remove-SecretFile $pf}
        Assert-H06r ((Test-Path (Join-Path $root 'config\https.p12')) -and (Test-Path (Join-Path $root 'config\mysql-trust.p12'))) 'keytool no creo los almacenes de prueba.'
        $s=Read-Settings $root
        $inv=Get-InstanceConfigInventory $root
        Assert-H06r (@($inv.Items).Count -eq 9 -and @($inv.Problems).Count -eq 0 -and (@($inv.Omitted) -join ',') -ceq 'config\rotacion-20261122T000000Z') "inventario de la instancia temporal ($(@($inv.Items).Count) elementos; $(@($inv.Problems) -join ','))."
        $crypto=New-ConfigCrypto $h6Pass $null 300000
        $json=(New-InstanceConfigPayload $s $inv $crypto.Fp)|ConvertTo-Json -Depth 10 -Compress
        $text=Protect-ConfigPayload $crypto $json
        $opened=Open-ConfigPackage $text $h6Pass
        $payload=$opened.Json|ConvertFrom-Json
        $check=Test-ConfigPayload $payload $opened.Crypto.Fp
        Assert-H06r (@($check.Findings).Count -eq 0 -and $check.Files -eq 9 -and $check.Secrets -eq 8 -and @($check.P12|Where-Object {$_.Opens -and $_.Matches}).Count -eq 2) "ida completa: el contenido debe estar correcto ($(@($check.Findings) -join ','))."
        Assert-H06r ((Get-PackageSecret $payload 'config\secrets.clixml' 'Owner') -ceq $vals.Owner -and (Get-PackageSecret $payload 'config\formulario-pull-token.clixml' '') -ceq $vals.Token -and (Get-PackageSecret $payload 'config\formulario-ack-identity.clixml' 'PrivatePem') -ceq $h6Keys.a[0] -and (Get-PackageSecret $payload 'config\formulario-ack-identity.clixml' 'ImportedUtc') -match '^\d{4}-\d\d-\d\dT') 'ida completa: los valores descifrados con DPAPI deben llegar intactos.'
        $result=Invoke-ConfigVerification $payload $opened.Crypto.Fp $root (Join-Path $PSScriptRoot 'AckKeyCheck.java')
        $nonMySql=@($result.Problems|Where-Object {$_ -notlike 'MYSQL_*'})
        Assert-H06r ($nonMySql.Count -eq 0 -and $result.Ack -ceq 'COINCIDE' -and @($result.Disk.Equal).Count -eq 9 -and @($result.Disk.P12|Where-Object {$_.State -ceq 'IGUALES'}).Count -eq 2 -and @($result.MySql|Where-Object {$_.State -ceq 'NO_COMPROBADA'}).Count -eq 4) "ida completa: verificacion contra el disco ($($result.Problems -join ','))."
        $lines=Format-ConfigVerification $payload $result
        $all=($lines -join "`n")+$text+$json.Length
        foreach($v in $vals.Values){Assert-H06r (-not $all.Contains($v)) 'un secreto aparecio en el informe o en el paquete cifrado.'}
        Assert-H06r (-not $all.Contains('BEGIN PRIVATE KEY')) 'la clave privada ACK aparecio en el informe o en el paquete cifrado.'
        # Cambios en el disco y en el paquete: cada uno se informa.
        [IO.File]::WriteAllText((Join-Path $root 'config\formulario-pull.json'),'{"origin":"https://otro.invalid","scope":"UPR-REQ","ackKeyId":"siger-ack-1","mode":"MANUAL"}')
        [IO.File]::Move((Join-Path $root 'config\formulario-pull.json.bak-atomic-0123'),(Join-Path $h6Dir 'movido.json'))
        [IO.File]::WriteAllText((Join-Path $root 'config\nuevo.json'),'{}')
        $d=Compare-ConfigWithDisk $payload $root
        Assert-H06r ((@($d.Different) -join ',') -ceq 'config\formulario-pull.json' -and (@($d.Missing) -join ',') -ceq 'config\formulario-pull.json.bak-atomic-0123' -and (@($d.Extra) -join ',') -ceq 'config\nuevo.json') 'comparacion con el disco: distinto, falta y extra.'
        $vp=@((Invoke-ConfigVerification $payload $opened.Crypto.Fp $root (Join-Path $PSScriptRoot 'AckKeyCheck.java')).Problems)
        Assert-H06r ($vp -ccontains 'DISCO_DISTINTO:config\formulario-pull.json' -and $vp -ccontains 'DISCO_FALTA:config\formulario-pull.json.bak-atomic-0123' -and $vp -ccontains 'DISCO_EXTRA:config\nuevo.json') "la verificacion debe informar las diferencias con el disco ($($vp -join ','))"
        $bad=$opened.Json|ConvertFrom-Json;foreach($i in $bad.items){if($i.path -ceq 'config\https.p12'){$i.certificates[0].thumbprint='00'*20};if($i.path -ceq 'config\mysql-trust.p12'){$i.password='Tls'}}
        $badF=@((Test-ConfigPayload $bad $opened.Crypto.Fp).Findings)
        Assert-H06r ($badF -ccontains 'P12_CERTIFICADOS:config\https.p12' -and $badF -ccontains 'P12_CLAVE:config\mysql-trust.p12') "almacenes: certificados distintos y clave cambiada ($($badF -join ','))."
        $bad=$opened.Json|ConvertFrom-Json;foreach($i in $bad.items){if($i.path -ceq 'config\secrets.clixml'){foreach($p in $i.properties){if($p.name -ceq 'Tls'){$p.value=(ConvertTo-Utf8Base64 'otra-clave');$p.fingerprint=(Get-SecretFingerprint $opened.Crypto.Fp 'config\secrets.clixml' 'Tls' 'SECRETO' 'otra-clave')}}}}
        Assert-H06r (@((Test-ConfigPayload $bad $opened.Crypto.Fp).Findings) -ccontains 'P12_NO_ABRE:config\https.p12') 'un almacen que no abre con su clave debe informarse.'
        $bad=$opened.Json|ConvertFrom-Json;foreach($i in $bad.items){if($i.path -ceq 'config\formulario-ack-identity.clixml'){foreach($p in $i.properties){if($p.name -ceq 'PublicPem'){$p.value=(ConvertTo-Utf8Base64 $h6Keys.b[1])}}}}
        Assert-H06r ((Invoke-ConfigVerification $bad $opened.Crypto.Fp $root (Join-Path $PSScriptRoot 'AckKeyCheck.java')).Problems -ccontains 'ACK_NO_COINCIDE') 'la verificacion debe detectar una identidad ACK cruzada.'
        # Formas de clixml no admitidas y almacen sin clave conocida: la exportacion no sigue.
        foreach($c in @(@('entero',5),@('tabla',@{a='b'}),@('lista',@('a','b')),@('objeto con entero',[PSCustomObject]@{A='x';B=5}),@('texto','solo texto'))){
            $f=Join-Path $h6Dir 'forma.clixml';$c[1]|Export-Clixml -LiteralPath $f
            Assert-H06r ((Get-H06rError {ConvertTo-ClixmlModel (Import-Clixml -LiteralPath $f) 'config\forma.clixml'}) -like 'Forma no admitida*') "un clixml con $($c[0]) debe rechazarse."
            [IO.File]::Delete($f)
        }
        # H06r2: un almacen con el nombre de uno conocido salvo mayusculas sigue impidiendo la exportacion (en Windows
        # https.p12 y HTTPS.p12 no conviven: se usa el anterior).
        $caseP12=Join-Path $root 'config\https-ANTERIOR-20261122-101010.p12'
        [IO.File]::Copy((Join-Path $root 'config\https.p12'),$caseP12)
        Assert-H06r ((@((Get-InstanceConfigInventory $root).Problems) -join ',') -ceq 'P12_MAYUSCULAS:config\https-ANTERIOR-20261122-101010.p12') 'un almacen conocido con otras mayusculas debe impedir la exportacion.'
        Assert-H06r ((Get-H06rError {New-InstanceConfigPayload $s (Get-InstanceConfigInventory $root) $crypto.Fp}) -like 'No hay clave conocida para config\https-ANTERIOR-*') 'el contenido no debe armarse con un almacen conocido con otras mayusculas.'
        $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{$badInv=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Export-InstanceConfig.ps1') -Root $root -Destination (Join-Path $h6Dir 'inv.sigerpkg') -WhatIf 2>&1|ForEach-Object {[string]$_});$badInvCode=$LASTEXITCODE}finally{$ErrorActionPreference=$old}
        Assert-H06r ($badInvCode -ne 0 -and ($badInv -join ' ') -match 'no se puede exportar' -and ($badInv -join ' ') -match 'P12_MAYUSCULAS:config\\https-ANTERIOR-20261122-101010\.p12' -and ($badInv -join ' ') -match 'nombre conocido con otras mayusculas: no se exporta' -and ($badInv -join ' ') -notmatch 'Whatif: .*Crear el paquete' -and -not [IO.File]::Exists((Join-Path $h6Dir 'inv.sigerpkg'))) 'Export-InstanceConfig.ps1 debe negarse con un inventario con problemas, antes de -WhatIf.'
        [IO.File]::Delete($caseP12)
        $junction=Join-Path $root 'config\enlace'
        $null=& cmd.exe /c mklink /J "$junction" "$h6Dir" 2>&1
        if([IO.Directory]::Exists($junction)){
            Assert-H06r ((Get-H06rError {Get-InstanceConfigInventory $root}) -like 'config\ contiene un enlace*') 'un enlace dentro de config\ debe impedir la exportacion.'
            [IO.Directory]::Delete($junction)
        }
        # Scripts con -File: -WhatIf no escribe ni pide la frase; destino existente o de otra extension y otra cuenta se rechazan.
        $ex=Join-Path $PSScriptRoot 'Export-InstanceConfig.ps1';$ti=Join-Path $PSScriptRoot 'Test-InstanceConfig.ps1';$dest=Join-Path $h6Dir 'paquete.sigerpkg'
        $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{
            $what=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ex -Root $root -Destination $dest -WhatIf 2>&1|ForEach-Object {[string]$_})
            [IO.File]::WriteAllText((Join-Path $h6Dir 'existe.sigerpkg'),'x')
            $exists=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ex -Root $root -Destination (Join-Path $h6Dir 'existe.sigerpkg') 2>&1|ForEach-Object {[string]$_});$existsCode=$LASTEXITCODE
            $ext=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ex -Root $root -Destination (Join-Path $h6Dir 'paquete.zip') 2>&1|ForEach-Object {[string]$_});$extCode=$LASTEXITCODE
            $inside=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ex -Root $root -Destination (Join-Path $root 'tmp\p.sigerpkg') 2>&1|ForEach-Object {[string]$_});$insideCode=$LASTEXITCODE
            [IO.File]::WriteAllText((Join-Path $h6Dir 'no-paquete.sigerpkg'),'{"format":"OTRO"}')
            $notPkg=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ti -Package (Join-Path $h6Dir 'no-paquete.sigerpkg') -Root $root 2>&1|ForEach-Object {[string]$_});$notPkgCode=$LASTEXITCODE
            [IO.File]::WriteAllText((Join-Path $h6Dir 'pocas.sigerpkg'),(New-H06rEnvelope @{iterations=1000}))
            $few=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ti -Package (Join-Path $h6Dir 'pocas.sigerpkg') -Root $root 2>&1|ForEach-Object {[string]$_});$fewCode=$LASTEXITCODE
            $instFile=Join-Path $root 'config\instance.json';$instText=[IO.File]::ReadAllText($instFile)
            [IO.File]::WriteAllText($instFile,$instText.Replace($me,'S-1-5-21-1-2-3-4'))
            $other=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ex -Root $root -Destination $dest -WhatIf 2>&1|ForEach-Object {[string]$_});$otherCode=$LASTEXITCODE
            [IO.File]::WriteAllText($instFile,$instText)
        }finally{$ErrorActionPreference=$old}
        $whatText=$what -join "`n"
        Assert-H06r (-not [IO.File]::Exists($dest) -and $whatText -match 'Inventario: 9 archivos' -and $whatText -match 'se omiten config\\rotacion-20261122T000000Z' -and $whatText -notmatch 'Frase de paso' -and $whatText -notmatch 'cimWhatIf') "Export-InstanceConfig.ps1 -WhatIf debe mostrar el inventario sin escribir ni pedir la frase ($whatText)."
        Assert-H06r ($existsCode -ne 0 -and ($exists -join ' ') -match 'EXISTE' -and [IO.File]::ReadAllText((Join-Path $h6Dir 'existe.sigerpkg')) -ceq 'x') 'Export-InstanceConfig.ps1 no debe sobrescribir un destino existente.'
        Assert-H06r ($extCode -ne 0 -and ($ext -join ' ') -match 'EXTENSION' -and $insideCode -ne 0 -and ($inside -join ' ') -match 'DENTRO_DE_LA_RAIZ') 'Export-InstanceConfig.ps1 debe rechazar otra extension y un destino dentro de la raiz.'
        Assert-H06r ($otherCode -ne 0 -and ($other -join ' ') -match 'como la cuenta de servicio de la instancia' -and -not [IO.File]::Exists($dest)) 'Export-InstanceConfig.ps1 debe rechazar a una cuenta que no es la duena.'
        Assert-H06r ($notPkgCode -ne 0 -and ($notPkg -join ' ') -match 'FORMATO' -and $fewCode -ne 0 -and ($few -join ' ') -match 'ITERACIONES') 'Test-InstanceConfig.ps1 debe rechazar un archivo ajeno y uno con pocas iteraciones antes de pedir la frase.'
        $allOut=(@($what)+@($exists)+@($ext)+@($inside)+@($notPkg)+@($few)+@($other)) -join "`n"
        foreach($v in $vals.Values){Assert-H06r (-not $allOut.Contains($v)) 'un secreto aparecio en la salida de un script.'}
        # Test-InstanceConfig.ps1 ejecutado de verdad (la frase la responde un Read-Host de prueba): la cuenta duena se
        # rechaza despues de abrir el paquete y antes de comprobar; una frase incorrecta falla por integridad.
        $own=Join-Path $h6Dir 'propio.sigerpkg';[IO.File]::WriteAllText($own,$text)
        foreach($c in @(@($h6Pass,'como OTRA cuenta'),@('otra frase de paso de prueba','INTEGRIDAD'))){
            $wrapper=Join-Path $h6Dir 'verificar.ps1'
            [IO.File]::WriteAllLines($wrapper,@('$ErrorActionPreference=''Stop''',"function global:Read-Host{param(`$Prompt,[switch]`$AsSecureString) ConvertTo-SecureString '$($c[0])' -AsPlainText -Force}","& '$($ti.Replace("'","''"))' -Package '$($own.Replace("'","''"))' -Root '$($root.Replace("'","''"))'"))
            $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
            try{$vo=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $wrapper 2>&1|ForEach-Object {[string]$_});$vc=$LASTEXITCODE}finally{$ErrorActionPreference=$old}
            Assert-H06r ($vc -ne 0 -and ($vo -join ' ') -match $c[1] -and ($vo -join ' ') -notmatch 'Resultado:') "Test-InstanceConfig.ps1 con la cuenta duena o una frase incorrecta debe negarse ($($c[1]))."
            foreach($v in $vals.Values){Assert-H06r (-not ($vo -join ' ').Contains($v)) 'un secreto aparecio en la salida de la verificacion.'}
        }
        # H06r2: instancia temporal con los 26 nombres y tipos del inventario de la VM (2026-11-22; contenido sintetico) y
        # un almacen sin clave conocida creado con una contrasena aleatoria que se descarta.
        $vm=Join-Path $h6Dir 'vm';foreach($d in @('config','mysql','tmp')){[void][IO.Directory]::CreateDirectory((Join-Path $vm $d))}
        foreach($n in @('secrets.clixml','formulario-pull-token.clixml','formulario-ack-identity.clixml','https.p12','mysql-trust.p12')){[IO.File]::Copy((Join-Path $root "config\$n"),(Join-Path $vm "config\$n"))}
        [IO.File]::Copy((Join-Path $root 'tmp\ca.pem'),(Join-Path $vm 'config\formulario-ca.pem'))
        foreach($n in @('https.p12.bak-lan-20261109','https.p12.bak-red-final-20261109')){[IO.File]::Copy((Join-Path $vm 'config\https.p12'),(Join-Path $vm "config\$n"))}
        [PSCustomObject]@{namespace='SIGER-A-2026';root=$vm;mysqlHome=(Join-Path $h6Dir 'sin-mysql');mysqlPort=13399;javaHome=(Split-Path $h6JavaBin -Parent);database='siger_a_inst';mode='INSTITUCIONAL';purpose='VALIDACION';ownerSid=$me}|ConvertTo-Json|Set-Content -Encoding UTF8 (Join-Path $vm 'config\instance.json')
        foreach($n in @('02af7615a5904068a2a4c1fdeb21c34f','23d819e48ab242778a9eaa0d13bf8508','25e1a37dbd6e4b2987109cc7b0af81c6','88ab7849c4fe4972bb5c62f7d8c2b9bd')){[IO.File]::WriteAllText((Join-Path $vm "config\instance.json.bak-atomic-$n"),'{"copia":"sintetica"}')}
        foreach($n in @('auto','bind','lan','red-final')){[IO.File]::WriteAllText((Join-Path $vm "config\instance.json.bak-$n-20261109"),'{"copia":"sintetica"}')}
        [IO.File]::WriteAllText((Join-Path $vm 'config\formulario-pull.json'),'{"origin":"https://formulario.invalid","scope":"UPR-REQ","ackKeyId":"siger-ack-1","caFile":"formulario-ca.pem","mode":"AUTOMATICO","consumidorUnicoConfirmado":true}')
        foreach($n in @('formulario-pull.json.bak-atomic-5fb59f7986e9478aa906282d48573273','formulario-pull.json.bak-auto-20261109')){[IO.File]::WriteAllText((Join-Path $vm "config\$n"),'{"copia":"sintetica"}')}
        foreach($n in @('estructura-inicial.json','formulario-trusted-keys.json','service.json')){[IO.File]::WriteAllText((Join-Path $vm "config\$n"),'{}')}
        [IO.File]::WriteAllText((Join-Path $vm 'config\initialized.marker'),'2026-11-09T00:00:00Z')
        [IO.File]::WriteAllText((Join-Path $vm 'mysql\my.ini'),"[mysqld]`r`nport=13399`r`n")
        $pf=Join-Path $vm 'tmp\k.txt'
        try{
            [IO.File]::WriteAllText($pf,[Guid]::NewGuid().ToString('N'))
            $null=Invoke-Native {& $keytool -genkeypair -alias candidato -keyalg RSA -keysize 2048 -validity 30 -dname 'CN=candidato,OU=SIGER' -storetype PKCS12 -keystore (Join-Path $vm 'config\https-red-candidato.p12') '-storepass:file' $pf -noprompt} -Quiet
        }finally{Remove-SecretFile $pf}
        $h6VmItems=@('ARCHIVO config\estructura-inicial.json','CLIXML config\formulario-ack-identity.clixml','ARCHIVO config\formulario-ca.pem','ARCHIVO config\formulario-pull.json',
            'ARCHIVO config\formulario-pull.json.bak-atomic-5fb59f7986e9478aa906282d48573273','ARCHIVO config\formulario-pull.json.bak-auto-20261109','CLIXML config\formulario-pull-token.clixml',
            'ARCHIVO config\formulario-trusted-keys.json','P12 config\https.p12','ARCHIVO config\https.p12.bak-lan-20261109','ARCHIVO config\https.p12.bak-red-final-20261109',
            'P12 config\https-red-candidato.p12','ARCHIVO config\initialized.marker','ARCHIVO config\instance.json','ARCHIVO config\instance.json.bak-atomic-02af7615a5904068a2a4c1fdeb21c34f',
            'ARCHIVO config\instance.json.bak-atomic-23d819e48ab242778a9eaa0d13bf8508','ARCHIVO config\instance.json.bak-atomic-25e1a37dbd6e4b2987109cc7b0af81c6',
            'ARCHIVO config\instance.json.bak-atomic-88ab7849c4fe4972bb5c62f7d8c2b9bd','ARCHIVO config\instance.json.bak-auto-20261109','ARCHIVO config\instance.json.bak-bind-20261109',
            'ARCHIVO config\instance.json.bak-lan-20261109','ARCHIVO config\instance.json.bak-red-final-20261109','P12 config\mysql-trust.p12','CLIXML config\secrets.clixml',
            'ARCHIVO config\service.json','ARCHIVO mysql\my.ini')
        $vmInv=Get-InstanceConfigInventory $vm
        $vmGot=[string[]]@($vmInv.Items|ForEach-Object {"$($_.Kind) $($_.Rel)"});$vmWant=[string[]]$h6VmItems.Clone();[Array]::Sort($vmGot,[StringComparer]::Ordinal);[Array]::Sort($vmWant,[StringComparer]::Ordinal)
        Assert-H06r ($h6VmItems.Count -eq 26 -and ($vmGot -join '|') -ceq ($vmWant -join '|') -and @($vmInv.Problems).Count -eq 0) "inventario con los nombres de la VM ($(@($vmInv.Problems) -join ','))."
        $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{$vmWhat=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $ex -Root $vm -Destination (Join-Path $h6Dir 'vm.sigerpkg') -WhatIf 2>&1|ForEach-Object {[string]$_});$vmWhatCode=$LASTEXITCODE}finally{$ErrorActionPreference=$old}
        $vmWhatText=$vmWhat -join "`n"
        Assert-H06r ($vmWhatCode -eq 0 -and -not [IO.File]::Exists((Join-Path $h6Dir 'vm.sigerpkg')) -and $vmWhatText -match 'Inventario: 26 archivos \(20 ARCHIVO, 3 CLIXML, 3 P12\), [0-9]+ bytes\.' -and $vmWhatText -match '(?m)^  P12     config\\https-red-candidato\.p12 \([0-9]+ bytes, sin clave conocida: se conserva tal cual, sin abrirlo\)$' -and $vmWhatText -match '(?m)^  P12     config\\https\.p12 \([0-9]+ bytes, clave Tls\)$' -and $vmWhatText -match '(?m)^  P12     config\\mysql-trust\.p12 \([0-9]+ bytes, clave Trust\)$' -and $vmWhatText -notmatch 'no se puede exportar') "Export-InstanceConfig.ps1 -WhatIf con los nombres de la VM ($vmWhatText)."
        $vmPayload=((New-InstanceConfigPayload (Read-Settings $vm) $vmInv $crypto.Fp)|ConvertTo-Json -Depth 10 -Compress)|ConvertFrom-Json
        $vmCand=@($vmPayload.items|Where-Object {$_.path -ceq 'config\https-red-candidato.p12'})
        Assert-H06r ($vmCand.Count -eq 1 -and $null -eq $vmCand[0].PSObject.Properties['password'] -and $null -eq $vmCand[0].PSObject.Properties['certificates'] -and $vmCand[0].sha256 -ceq (Get-Sha256Hex ([IO.File]::ReadAllBytes((Join-Path $vm 'config\https-red-candidato.p12'))))) 'el almacen sin clave conocida debe viajar con sus bytes, sin clave ni certificados.'
        $vmCheck=Test-ConfigPayload $vmPayload $crypto.Fp
        Assert-H06r (@($vmCheck.Findings).Count -eq 0 -and $vmCheck.Files -eq 26 -and @($vmCheck.P12|Where-Object {$_.NameState -ceq 'CONOCIDO' -and $_.Opens -and $_.Matches}).Count -eq 2 -and (@($vmCheck.P12|Where-Object {$_.NameState -ceq 'SIN_CLAVE'}|ForEach-Object {$_.Path}) -join ',') -ceq 'config\https-red-candidato.p12') "contenido con los nombres de la VM ($(@($vmCheck.Findings) -join ','))."
        $vmResult=Invoke-ConfigVerification $vmPayload $crypto.Fp $vm (Join-Path $PSScriptRoot 'AckKeyCheck.java')
        $vmLines=Format-ConfigVerification $vmPayload $vmResult
        Assert-H06r (@($vmResult.Problems|Where-Object {$_ -notlike 'MYSQL_*'}).Count -eq 0 -and @($vmResult.Disk.Equal).Count -eq 26 -and $vmLines -ccontains 'Almacen config\https-red-candidato.p12: sin clave conocida, se conserva tal cual (tamano y SHA-256 verificados; no se abre ni se comparan certificados).' -and $vmLines -ccontains 'Almacen del disco config\https-red-candidato.p12: SIN_CLAVE_CONOCIDA (comparado solo por SHA-256: IGUAL).') "verificacion con los nombres de la VM ($(@($vmResult.Problems) -join ','))."
        # Un cambio del almacen sin clave conocida en el disco se informa como DISTINTO (sin DISCO_P12_*).
        $candFile=Join-Path $vm 'config\https-red-candidato.p12';$candBytes=[IO.File]::ReadAllBytes($candFile)
        [IO.File]::WriteAllBytes($candFile,[byte[]]($candBytes+[byte]0))
        $vmDiff=@((Invoke-ConfigVerification $vmPayload $crypto.Fp $vm (Join-Path $PSScriptRoot 'AckKeyCheck.java')).Problems|Where-Object {$_ -notlike 'MYSQL_*'})
        Assert-H06r (($vmDiff -join ',') -ceq 'DISCO_DISTINTO:config\https-red-candidato.p12') "un almacen sin clave conocida cambiado en el disco debe dar solo DISCO_DISTINTO ($($vmDiff -join ','))."
        [IO.File]::WriteAllBytes($candFile,$candBytes)
        # Las reglas de los conocidos no se aflojan: un https.p12 que no abre con su clave impide armar el contenido.
        $httpsFile=Join-Path $vm 'config\https.p12';$httpsBytes=[IO.File]::ReadAllBytes($httpsFile)
        [IO.File]::WriteAllBytes($httpsFile,$candBytes)
        Assert-H06r ((Get-H06rError {New-InstanceConfigPayload (Read-Settings $vm) (Get-InstanceConfigInventory $vm) $crypto.Fp}) -like 'config\https.p12 no abre con su clave*') 'un https.p12 que no abre con su clave debe impedir la exportacion.'
        [IO.File]::WriteAllBytes($httpsFile,$httpsBytes)
        # Copia de V4 (DESPLIEGUE_VM.md): la orden documentada, sobre los nombres de la VM, copia exactamente los cinco
        # archivos del Formulario; control: la orden anterior (con -Exclude '*.bak-atomic-*') arrastra el .bak-auto.
        $guideCopy=@([IO.File]::ReadAllLines((Join-Path $project 'docs\DESPLIEGUE_VM.md'))|Where-Object {$_ -like 'Copy-Item *' -and $_ -like "*'D:\SIGER-VALIDACION\config\formulario*"})
        Assert-H06r ($guideCopy.Count -eq 1) "DESPLIEGUE_VM.md debe tener una sola copia de la configuracion del Formulario ($($guideCopy.Count))."
        function Invoke-H06rGuideCopy([string]$Line,[string]$Name){
            $dst=Join-Path $h6Dir $Name;[void][IO.Directory]::CreateDirectory($dst)
            $null=& ([scriptblock]::Create($Line.Replace("'D:\SIGER-VALIDACION\config\","'$vm\config\").Replace("'D:\SIGER\config'","'$dst'")))
            $got=[string[]]@([IO.Directory]::GetFileSystemEntries($dst)|ForEach-Object {[IO.Path]::GetFileName($_)});[Array]::Sort($got,[StringComparer]::Ordinal)
            return ($got -join ',')
        }
        Assert-H06r ((Invoke-H06rGuideCopy $guideCopy[0] 'copia-v4') -ceq 'formulario-ack-identity.clixml,formulario-ca.pem,formulario-pull-token.clixml,formulario-pull.json,formulario-trusted-keys.json') 'la copia de V4 debe traer exactamente los cinco archivos del Formulario.'
        Assert-H06r ((Invoke-H06rGuideCopy "Copy-Item -Path 'D:\SIGER-VALIDACION\config\formulario-*' -Exclude '*.bak-atomic-*' -Destination 'D:\SIGER\config'" 'copia-anterior') -like '*formulario-pull.json.bak-auto-20261109*') 'control: la orden anterior de V4 debe copiar el .bak-auto (si no, la prueba no detecta nada).'
        # H06s: ida y vuelta real con DPAPI sobre la instancia con los nombres de la VM (sin servicios registrados).
        # Exportacion con la DPAPI de esta cuenta; el restablecimiento se simula con clixml que nadie descifra; la
        # importacion se ejecuta con -File y la frase la responde un Read-Host de prueba.
        [IO.File]::Delete((Join-Path $vm 'config\service.json'))
        $h7Crypto=New-ConfigCrypto $h6Pass $null 300000
        $h7Json=(New-InstanceConfigPayload (Read-Settings $vm) (Get-InstanceConfigInventory $vm) $h7Crypto.Fp)|ConvertTo-Json -Depth 10 -Compress
        $h7Exported=$h7Json|ConvertFrom-Json
        $h7ShapeError=Test-H06sShapes $h7Exported @('cabecera','ARCHIVO','CLIXML','propiedad','P12_SIN_CLAVE','P12_CONOCIDO','certificado')
        Assert-H06s ($h7ShapeError -ceq '') "campos del paquete exportado con DPAPI real ($h7ShapeError)."
        $h7Pkg=Join-Path $h6Dir 'h06s.sigerpkg';[IO.File]::WriteAllText($h7Pkg,(Protect-ConfigPayload $h7Crypto $h7Json))
        $imp=Join-Path $PSScriptRoot 'Import-InstanceConfig.ps1'
        function Invoke-H06sImport([string]$Pass,[string]$Pkg,[string]$Extra){
            $w=Join-Path $h6Dir 'importar.ps1'
            [IO.File]::WriteAllLines($w,@('$ErrorActionPreference=''Stop''',"function global:Read-Host{param(`$Prompt,[switch]`$AsSecureString) ConvertTo-SecureString '$Pass' -AsPlainText -Force}",'$global:LASTEXITCODE=0',"& '$($imp.Replace("'","''"))' -Root '$($vm.Replace("'","''"))' -Package '$($Pkg.Replace("'","''"))' $Extra",'exit $LASTEXITCODE'))
            $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
            try{$o=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $w 2>&1|ForEach-Object {[string]$_});$code=$LASTEXITCODE}finally{$ErrorActionPreference=$old}
            $t=$o -join "`n"
            foreach($v in @($vals.Values)+@($h6Keys.a[0])){Assert-H06s (-not $t.Contains($v)) 'un secreto aparecio en la salida de la importacion.'}
            return [PSCustomObject]@{Code=$code;Text=$t}
        }
        function Get-H06sRotations{return @([IO.Directory]::GetDirectories((Join-Path $vm 'config'),'rotacion-*'))}
        $h7RotBase=@(Get-H06sRotations).Count
        # 1. -WhatIf en una sesion que ya descifra los secretos: plan, nota informativa y nada escrito.
        $r=Invoke-H06sImport $h6Pass $h7Pkg '-WhatIf'
        Assert-H06s ($r.Code -eq 0 -and $r.Text -match 'Plan: 25 archivos del paquete \(3 REEMPLAZAR, 22 SIN_CAMBIO\)' -and $r.Text -match 'ya descifra los clixml del disco' -and $r.Text -notmatch '(?i)ADVERTENCIA|WARNING' -and $r.Text -match [regex]::Escape((Get-Sha256Hex ([IO.File]::ReadAllBytes($h7Pkg)))) -and @(Get-H06sRotations).Count -eq $h7RotBase) "-WhatIf de la importacion ($($r.Text))."
        # 2. Restablecimiento simulado y cambios en el disco.
        $h7Garbage='<Objs Version="1.1.0.1" xmlns="http://schemas.microsoft.com/powershell/2004/04"><SS>01000000d08c9ddf0115d1118c7a00c04fc297eb01000000ffffffffffffffffffffffffffffffff0000000002000000000003660000c000000010000000</SS></Objs>'
        foreach($n in @('secrets.clixml','formulario-pull-token.clixml','formulario-ack-identity.clixml')){[IO.File]::WriteAllText((Join-Path $vm "config\$n"),$h7Garbage,[Text.Encoding]::Unicode)}
        Assert-H06s ((Get-H06rError {Import-Clixml -LiteralPath (Join-Path $vm 'config\secrets.clixml')}) -cne 'SIN_ERROR') 'control: el clixml del restablecimiento simulado no debe descifrarse.'
        [IO.File]::Delete((Join-Path $vm 'config\formulario-ca.pem'))
        [IO.File]::WriteAllText((Join-Path $vm 'config\formulario-pull.json'),'{"cambiado":"despues de exportar"}')
        [IO.File]::WriteAllText((Join-Path $vm 'mysql\my.ini'),"[mysqld]`r`nport=13400`r`n")
        $candOld=[byte[]]($candBytes+[byte]7);[IO.File]::WriteAllBytes($candFile,$candOld)
        [IO.File]::WriteAllText((Join-Path $vm 'config\extra-h06s.json'),'{"solo":"en el disco"}')
        # 3. Rechazos: nada cambia.
        $h7Bad=$h7Exported|ConvertTo-Json -Depth 10|ConvertFrom-Json;foreach($i in $h7Bad.items){if($i.path -ceq 'config\mysql-trust.p12'){$i.path='config\MYSQL-TRUST.p12'}}
        $h7BadPkg=Join-Path $h6Dir 'h06s-mayusculas.sigerpkg';[IO.File]::WriteAllText($h7BadPkg,(Protect-ConfigPayload $h7Crypto ($h7Bad|ConvertTo-Json -Depth 10 -Compress)))
        $h7OtherPkg=Join-Path $h6Dir 'h06s-otra-raiz.sigerpkg';[IO.File]::WriteAllText($h7OtherPkg,(Protect-ConfigPayload $h7Crypto ((New-InstanceConfigPayload (Read-Settings $root) (Get-InstanceConfigInventory $root) $h7Crypto.Fp)|ConvertTo-Json -Depth 10 -Compress)))
        $h7Inside=Join-Path $vm 'tmp\dentro.sigerpkg';[IO.File]::Copy($h7Pkg,$h7Inside)
        $h7NotPkg=Join-Path $h6Dir 'no-paquete-h06s.sigerpkg';[IO.File]::WriteAllText($h7NotPkg,'{"format":"SIGER-SECRETOS-1"}')
        $h7Snapshot={(@([IO.Directory]::GetFiles((Join-Path $vm 'config'),'*',[IO.SearchOption]::AllDirectories)+@(Join-Path $vm 'mysql\my.ini'))|Sort-Object|ForEach-Object {"$_|$(Get-Sha256Hex ([IO.File]::ReadAllBytes($_)))"}) -join "`n"}
        $h7Before=& $h7Snapshot
        $instFile=Join-Path $vm 'config\instance.json';$instText=[IO.File]::ReadAllText($instFile);$instBytes=[IO.File]::ReadAllBytes($instFile)
        [void][IO.Directory]::CreateDirectory((Join-Path $vm 'run'));[void][IO.Directory]::CreateDirectory((Join-Path $vm 'mysql\data'))
        foreach($c in @(
            @{n='frase incorrecta';p='otra frase de paso de prueba';k=$h7Pkg;pre={};post={};e='INTEGRIDAD'},
            @{n='SIGER activo';p=$h6Pass;k=$h7Pkg;pre={[IO.File]::WriteAllText((Join-Path $vm 'run\app.pid'),[string]$PID)};post={[IO.File]::Delete((Join-Path $vm 'run\app.pid'))};e='APLICACION_ACTIVA'},
            @{n='motor activo';p=$h6Pass;k=$h7Pkg;pre={[IO.File]::WriteAllText((Join-Path $vm 'mysql\data\siger-a.pid'),[string]$PID)};post={[IO.File]::Delete((Join-Path $vm 'mysql\data\siger-a.pid'))};e='MOTOR_ACTIVO'},
            @{n='pid ilegible';p=$h6Pass;k=$h7Pkg;pre={[IO.File]::WriteAllText((Join-Path $vm 'run\app.pid'),'x')};post={[IO.File]::Delete((Join-Path $vm 'run\app.pid'))};e='APLICACION_ACTIVA'},
            @{n='otra cuenta';p=$h6Pass;k=$h7Pkg;pre={[IO.File]::WriteAllText($instFile,$instText.Replace($me,'S-1-5-21-1-2-3-4'))};post={[IO.File]::WriteAllBytes($instFile,$instBytes)};e='como la cuenta de servicio de la instancia'},
            @{n='almacen con otras mayusculas';p=$h6Pass;k=$h7BadPkg;pre={};post={};e='CONTENIDO: P12_MAYUSCULAS:config\\MYSQL-TRUST\.p12'},
            @{n='otra raiz';p=$h6Pass;k=$h7OtherPkg;pre={};post={};e='RAIZ_DISTINTA'},
            @{n='paquete dentro de la raiz';p=$h6Pass;k=$h7Inside;pre={};post={};e='dentro de la raiz'},
            @{n='archivo ajeno';p=$h6Pass;k=$h7NotPkg;pre={};post={};e='FORMATO'})){
            & $c.pre
            try{$r=Invoke-H06sImport $c.p $c.k '-Confirm:$false'}finally{& $c.post}
            Assert-H06s ($r.Code -ne 0 -and $r.Text -match $c.e -and $r.Text -notmatch 'Importacion COMPLETA' -and @(Get-H06sRotations).Count -eq $h7RotBase) "la importacion con $($c.n) debe negarse ($($c.e)): $($r.Text)"
        }
        [IO.File]::Delete($h7Inside)
        Assert-H06s ((& $h7Snapshot) -ceq $h7Before) 'un rechazo de la importacion cambio el disco.'
        # 4. Importacion real.
        $r=Invoke-H06sImport $h6Pass $h7Pkg '-Confirm:$false'
        Assert-H06s ($r.Code -eq 0 -and $r.Text -match 'Plan: 25 archivos del paquete \(6 REEMPLAZAR, 1 RESTAURAR, 18 SIN_CAMBIO\); 1 archivo\(s\) del disco' -and $r.Text -match 'Importacion COMPLETA: 7 archivos aplicados, 18 sin cambio' -and $r.Text -match 'Almacen config\\https-red-candidato\.p12: SIN_CLAVE_CONOCIDA' -and $r.Text -match 'Identidad ACK: privada y publica COINCIDE' -and $r.Text -match '(?m)^Resultado: CORRECTO\.$' -and $r.Text -notmatch 'ya descifra los clixml') "importacion real ($($r.Text))."
        $h7Sec=Import-Clixml -LiteralPath (Join-Path $vm 'config\secrets.clixml')
        foreach($n in @('Owner','App','Migrator','Backup','Tls','Trust')){Assert-H06s ((Secret-Text $h7Sec.$n) -ceq $vals[$n]) "el secreto $n debe volver intacto con la DPAPI de esta sesion."}
        Assert-H06s ((@($h7Sec.PSObject.Properties|ForEach-Object {$_.Name}) -join ',') -ceq 'Owner,App,Migrator,Backup,Tls,Trust') 'secrets.clixml conserva el orden de sus propiedades.'
        $h7Id=Import-Clixml -LiteralPath (Join-Path $vm 'config\formulario-ack-identity.clixml')
        Assert-H06s ((Secret-Text (Import-Clixml -LiteralPath (Join-Path $vm 'config\formulario-pull-token.clixml'))) -ceq $vals.Token -and (Secret-Text $h7Id.PrivatePem) -ceq $h6Keys.a[0] -and $h7Id.KeyId -ceq 'siger-ack-1' -and $h7Id.PublicPem -ceq $h6Keys.a[1]) 'Bearer e identidad ACK intactos.'
        foreach($n in @('secrets.clixml','formulario-pull-token.clixml','formulario-ack-identity.clixml')){
            $f=Join-Path $vm "config\$n";$acl=Get-Acl -LiteralPath $f
            $sids=@($acl.Access|ForEach-Object {$_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value}|Sort-Object -Unique)
            Assert-H06s ($acl.AreAccessRulesProtected -and (($sids|Sort-Object) -join ',') -ceq ((@($me,'S-1-5-18','S-1-5-32-544')|Sort-Object) -join ',') -and (Get-H06rError {Require-PrivateFile $f}) -ceq 'SIN_ERROR') "ACL privada de $n."
        }
        foreach($i in @($h7Exported.items|Where-Object {$_.kind -cne 'CLIXML'})){Assert-H06s ((Get-Sha256Hex ([IO.File]::ReadAllBytes((Join-Path $vm $i.path)))) -ceq $i.sha256) "$($i.path) debe quedar igual al paquete."}
        Assert-H06s ([IO.File]::ReadAllText((Join-Path $vm 'config\extra-h06s.json')) -ceq '{"solo":"en el disco"}') 'un archivo que no esta en el paquete no se toca.'
        $h7Rot=@(Get-H06sRotations|Sort-Object);Assert-H06s ($h7Rot.Count -eq $h7RotBase+1 -and [IO.Path]::GetFileName($h7Rot[-1]) -cmatch '^rotacion-[0-9]{8}T[0-9]{6}Z$') 'una carpeta rotacion-<UTC> nueva.'
        $h7Old=Join-Path $h7Rot[-1] 'anterior'
        $h7Kept=[string[]]@([IO.Directory]::GetFiles($h7Old,'*',[IO.SearchOption]::AllDirectories)|ForEach-Object {$_.Substring($h7Old.Length+1)});[Array]::Sort($h7Kept,[StringComparer]::Ordinal)
        Assert-H06s (($h7Kept -join ',') -ceq 'config\formulario-ack-identity.clixml,config\formulario-pull-token.clixml,config\formulario-pull.json,config\https-red-candidato.p12,config\secrets.clixml,mysql\my.ini') "anteriores conservados ($($h7Kept -join ','))."
        Assert-H06s ([IO.File]::ReadAllText((Join-Path $h7Old 'config\secrets.clixml'),[Text.Encoding]::Unicode) -ceq $h7Garbage -and (Get-Sha256Hex ([IO.File]::ReadAllBytes((Join-Path $h7Old 'config\https-red-candidato.p12')))) -ceq (Get-Sha256Hex $candOld) -and -not [IO.Directory]::Exists((Join-Path $h7Rot[-1] 'nuevo'))) 'los anteriores se conservan tal cual y no queda la carpeta de preparacion.'
        $h7RecText=[IO.File]::ReadAllText((Join-Path $h7Rot[-1] 'importacion.json'));$h7Rec=$h7RecText|ConvertFrom-Json
        Assert-H06s ($h7Rec.kind -ceq 'IMPORTACION_CONFIGURACION' -and $h7Rec.estado -ceq 'COMPLETA' -and @($h7Rec.items).Count -eq 25 -and @($h7Rec.items|Where-Object {$_.estado -ceq 'APLICADO'}).Count -eq 7 -and (@($h7Rec.extra) -join ',') -ceq 'config\extra-h06s.json' -and $h7Rec.package.sha256 -ceq (Get-Sha256Hex ([IO.File]::ReadAllBytes($h7Pkg)))) 'registro importacion.json.'
        foreach($v in @($vals.Values)+@($h6Keys.a[0])){Assert-H06s (-not $h7RecText.Contains($v)) 'un secreto aparecio en importacion.json.'}
        # La verificacion de otra cuenta (Test-InstanceConfig.ps1) informa como distintos solo los clixml reprotegidos.
        $h7Diff=@((Invoke-ConfigVerification $h7Exported $h7Crypto.Fp $vm (Join-Path $PSScriptRoot 'AckKeyCheck.java')).Problems|Where-Object {$_ -notlike 'MYSQL_*'})
        Assert-H06s ((($h7Diff|Sort-Object) -join ',') -ceq 'DISCO_DISTINTO:config\formulario-ack-identity.clixml,DISCO_DISTINTO:config\formulario-pull-token.clixml,DISCO_DISTINTO:config\secrets.clixml,DISCO_EXTRA:config\extra-h06s.json') "despues de importar, solo los clixml difieren del paquete por SHA-256 ($($h7Diff -join ','))."
        # 5. Repetir es seguro: los clixml se vuelven a proteger y el resto queda SIN_CAMBIO; esta sesion descifra: nota.
        Start-Sleep -Milliseconds 1100
        $r=Invoke-H06sImport $h6Pass $h7Pkg '-Confirm:$false'
        Assert-H06s ($r.Code -eq 0 -and $r.Text -match 'Plan: 25 archivos del paquete \(3 REEMPLAZAR, 22 SIN_CAMBIO\)' -and $r.Text -match '(?m)^Resultado: CORRECTO\.$' -and $r.Text -match 'ya descifra los clixml' -and @(Get-H06sRotations).Count -eq $h7RotBase+2) "repetir la importacion ($($r.Text))."
        # La verificacion posterior exige la ACL privada de cada clixml: con lectura para Todos, la informa (ACL).
        $h7AclFile=Join-Path $vm 'config\formulario-pull-token.clixml'
        Assert-H06s (@((Test-ImportedConfig $h7Exported $h7Crypto.Fp $vm (Join-Path $PSScriptRoot 'AckKeyCheck.java')).Findings).Count -eq 0) 'control: la verificacion posterior sin cambios debe ser correcta.'
        $h7Acl=Get-Acl -LiteralPath $h7AclFile;$h7Acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier('S-1-1-0')),'Read','Allow')));Set-Acl -LiteralPath $h7AclFile -AclObject $h7Acl
        try{Assert-H06s (@((Test-ImportedConfig $h7Exported $h7Crypto.Fp $vm (Join-Path $PSScriptRoot 'AckKeyCheck.java')).Findings) -ccontains 'ACL:config\formulario-pull-token.clixml') 'la verificacion posterior debe informar un clixml con permisos amplios.'}finally{Set-PrivateFileAcl $h7AclFile}
        # 6. Test-ServiceIdentity.ps1 (comprobacion de Set-ServicePassword.ps1) cuenta los clixml que descifra.
        function Invoke-H06sIdentity{$old=$ErrorActionPreference;$ErrorActionPreference='Continue';try{$o=@(& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-ServiceIdentity.ps1') -Root $vm 2>&1|ForEach-Object {[string]$_})}finally{$ErrorActionPreference=$old};return (($o|Where-Object {$_.StartsWith('{')}|Select-Object -Last 1)|ConvertFrom-Json)}
        $h7IdCheck=Invoke-H06sIdentity
        Assert-H06s ($h7IdCheck.clixmlTotal -eq 3 -and $h7IdCheck.clixmlDecrypted -eq 3 -and $h7IdCheck.dpapi -and $h7IdCheck.instanceOwner -and -not (@(Get-ServiceIdentityBlockers $h7IdCheck $me) -ccontains 'DPAPI')) "Test-ServiceIdentity.ps1 despues de importar ($($h7IdCheck|ConvertTo-Json -Compress))."
        [IO.File]::WriteAllText((Join-Path $vm 'config\formulario-pull-token.clixml'),$h7Garbage,[Text.Encoding]::Unicode)
        $h7IdCheck=Invoke-H06sIdentity
        Assert-H06s ($h7IdCheck.clixmlTotal -eq 3 -and $h7IdCheck.clixmlDecrypted -eq 2 -and $h7IdCheck.dpapi -and (@(Get-ServiceIdentityBlockers $h7IdCheck $me) -ccontains 'DPAPI')) 'Test-ServiceIdentity.ps1 con un clixml que no descifra: DPAPI.'
        Clear-ConfigCrypto $h7Crypto
        $h6Ack+='; ida y vuelta de la importacion con DPAPI real, restablecimiento simulado, rechazos y repeticion (H06s)'
        Clear-ConfigCrypto $crypto;Clear-ConfigCrypto $opened.Crypto
        $h6Ack+='; ida completa con DPAPI, keytool y los scripts con -File; inventario de la VM con almacen sin clave conocida y copia de V4'
    }
}finally{if([IO.Directory]::Exists($h6Dir)){[IO.Directory]::Delete($h6Dir,$true)};Clear-ConfigCrypto $h6Crypto;Clear-ConfigCrypto $h6Open.Crypto}
$h06rSummary="paquete de configuracion de H06r ($h06rCount casos: rutas, tipos, claves de .p12, destino, cuenta verificadora, keyId, cifrado y envoltura, revision del contenido, almacenes sin clave conocida y con otras mayusculas (H06r2), MySQL sin contrasena en argumentos, verificacion sin escrituras, orden de los scripts; $h6Ack); importacion y contrasena de los servicios de H06s ($h06sCount casos: formato fijo con el paquete de d792507, accion por archivo, instancia detenida, paquete de la instancia, comparacion despues de importar, comprobacion como la cuenta, servicios y orden de los scripts)"
# --- fin H06r ---
$invocationSummary="liberacion por analisis en origen ($($originCases.Count) casos y entrega solo al servicio y al ciclo manual); proceso propio por el servicio ($($serviceCases.Count+$appCases.Count) casos); prueba de Graph para activar el correo ($($probeCases.Count) casos); invocacion con -File ($($orders.Count) ordenes documentadas en $invoked procesos, $($pathListCases.Count+$domainListCases.Count) casos de listas y $controlCount controles; sin `$PSScriptRoot en param)"
Write-Host ('Sintaxis PowerShell y hash .NET correctos: '+$files.Count+' scripts; modo de instancia falla cerrado ('+$cases.Count+' casos); credencial y destinatario Graph ('+($graph.Count+$recipients.Count)+' casos); cierre de la validacion ('+($closure.Count+$unreadable.Count)+' casos); archivos con credenciales (-WhatIf, sin Remove-Item); eliminacion de la validacion ('+$deletionCount+' casos y borrado con reintento); '+$invocationSummary+'; correcciones para Windows de H06c ('+$h06cCount+' casos: cuenta local, cuenta duena, derecho de inicio, descriptor del servicio, control 200, -WhatIf y lista de elementos); '+$h06rSummary+'. No se modifico la configuracion del equipo.')
