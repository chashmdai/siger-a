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
$listParams=@{'Remove-Validation.ps1'=@{Respaldos='ConvertFrom-PathListArgument';RespaldosIncompletos='ConvertFrom-PathListArgument';Ensayos='ConvertFrom-PathListArgument'};'Import-GraphCredential.ps1'=@{DominiosPermitidos='ConvertFrom-DomainListArgument'}}
foreach($file in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File)){
    $ast=[System.Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$null,[ref]$null)
    $unsafe=@(Get-FileUnsafeParameters $ast)
    if($unsafe.Count -gt 0){throw ("$($file.Name): el parametro -"+($unsafe -join ', -')+' no llega con powershell.exe -File (arreglo, coleccion o scriptblock). Usa un texto: rutas separadas por | o NINGUNO (ConvertFrom-PathListArgument).')}
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
    foreach($guide in @(@('docs','DESPLIEGUE_VM.md'),@('docs','INSTALACION_VM.md'),@('README.md'),@('CLAUDE.md'))){
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
    $controlCount=$controls.Count+1
}finally{if([IO.Directory]::Exists($stubDir)){[IO.Directory]::Delete($stubDir,$true)}}
$invocationSummary="proceso propio por el servicio ($($serviceCases.Count+$appCases.Count) casos); prueba de Graph para activar el correo ($($probeCases.Count) casos); invocacion con -File ($($orders.Count) ordenes documentadas en $invoked procesos, $($pathListCases.Count+$domainListCases.Count) casos de listas y $controlCount controles)"
Write-Host ('Sintaxis PowerShell y hash .NET correctos: '+$files.Count+' scripts; modo de instancia falla cerrado ('+$cases.Count+' casos); credencial y destinatario Graph ('+($graph.Count+$recipients.Count)+' casos); cierre de la validacion ('+($closure.Count+$unreadable.Count)+' casos); archivos con credenciales (-WhatIf, sin Remove-Item); eliminacion de la validacion ('+$deletionCount+' casos y borrado con reintento); '+$invocationSummary+'. No se modifico la configuracion del equipo.')
