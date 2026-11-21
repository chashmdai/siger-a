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
# Ningun Remove-Item nuevo sobre variables de archivos con credenciales (directo o por un foreach sobre ellas). Las
# apariciones conocidas son pendientes de H04, con su conteo exacto: una nueva falla, y tambien una que ya no aparece
# (la lista se actualiza al corregir).
$secretVars=@('client','init','passFile','src','dst','candidate')
$pendingH04=@{'Backup.ps1'=2;'Init-Server.ps1'=3;'Invoke-FormularioPull.ps1'=1;'Migrate.ps1'=1;'Recover-Ti.ps1'=1;'Restore-Check.ps1'=1;'Service.ps1'=1;'Start.ps1'=1;'Stop.ps1'=1;'Test-Service.ps1'=1}
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
    $expected=if($pendingH04.ContainsKey($file.Name)){$pendingH04[$file.Name]}else{0}
    if($count -ne $expected){throw ("Archivos con credenciales: $($file.Name) tiene $count Remove-Item sobre "+($secretVars -join ', ')+" y se esperaban $expected. Usa Remove-SecretFile (Common.ps1); si corregiste un pendiente de H04, actualiza la lista.")}
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
Write-Host ('Sintaxis PowerShell y hash .NET correctos: '+$files.Count+' scripts; modo de instancia falla cerrado ('+$cases.Count+' casos); credencial y destinatario Graph ('+($graph.Count+$recipients.Count)+' casos); cierre de la validacion ('+($closure.Count+$unreadable.Count)+' casos); archivos con credenciales (-WhatIf y '+$pendingH04.Count+' pendientes de H04); eliminacion de la validacion ('+$deletionCount+' casos y borrado con reintento). No se modifico la configuracion del equipo.')
