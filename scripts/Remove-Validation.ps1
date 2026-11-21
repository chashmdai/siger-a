[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
param(
    [string]$Root='D:\SIGER-VALIDACION',
    # Produccion ya acreditada (D13): debe existir, con la misma cuenta y las credenciales del Formulario (y de Graph).
    [string]$Produccion='D:\SIGER',
    # Las tres listas son un solo texto (powershell.exe -File no pasa arreglos): rutas separadas por | o NINGUNO, que
    # declara expresamente que no hay ninguna (ConvertFrom-PathListArgument en Environment.ps1).
    # Respaldos de Backup.ps1 de esta validacion: cada ruta es un respaldo (backup.json) o una carpeta cuyos hijos son
    # todos respaldos de esta validacion (por ejemplo D:\Respaldos-SIGER-VALIDACION).
    [Parameter(Mandatory=$true)][string]$Respaldos,
    # Respaldos abortados (sin backup.json) que declara el responsable: hijos directos de una carpeta de -Respaldos, sin
    # restore-result.json ni enlaces y, si traen config-private\instance.json, de esta validacion.
    [Parameter(Mandatory=$true)][string]$RespaldosIncompletos,
    # Destinos de Restore-Check.ps1 de esta validacion (restore-result.json con su esquema en el motor propio).
    [Parameter(Mandatory=$true)][string]$Ensayos,
    [Parameter(Mandatory=$true)][string]$Motivo,
    [string]$Constancias='D:\SIGER-CONSTANCIAS')
# ELIMINA una instancia de VALIDACION ya cerrada (D12 de docs/PLAN_0_6_0.md): su raiz completa (base y esquemas
# siger_a_restore_* de su motor, documentos, logs, config\ con los secretos DPAPI y releases\), los respaldos y los
# ensayos de restauracion indicados. Es el unico procedimiento destructivo de SIGER (excepcion registrada en
# docs/DECISIONES_FUNCIONALES.md). Nunca toca el mysqlHome (compartido con produccion), la raiz de produccion ni nada
# fuera de D:.
#
# Se ejecuta como la cuenta de servicio (ownerSid de ambas instancias) desde la carpeta del paquete 0.6.0, tambien sobre
# una validacion que sigue en 0.4.0, despues de Close-Validation.ps1, de Uninstall-Service.ps1 y de acreditar
# produccion (paso V6). Ejemplo:
#
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'D:\Entregas\SIGER-FECHA\scripts\Remove-Validation.ps1' -Root 'D:\SIGER-VALIDACION' -Produccion 'D:\SIGER' -Respaldos 'D:\Respaldos-SIGER-VALIDACION' -RespaldosIncompletos 'D:\Respaldos-SIGER-VALIDACION\previo-pull-20261109' -Ensayos NINGUNO -Motivo 'Eliminacion de la validacion tras acreditar produccion'
#
# Con -WhatIf solo muestra el inventario y los bloqueos, sin escribir nada. Sin -WhatIf pide la confirmacion de
# PowerShell (ConfirmImpact High) y ademas que se escriba la ruta de la raiz: -Confirm:$false no basta para borrar.
# Varias rutas en una lista: -Respaldos 'D:\uno|D:\dos'. Se invoca exactamente asi, con -File (tambien en H06b).
#
# Bloqueos (Get-ValidationDeletionBlockers y Get-DeletionPathBlockers en Environment.ps1, con casos en Check-Scripts.ps1):
# marcador de cierre de 0.6.0 (kind CIERRE_VALIDACION) y su copia identica en -Constancias; servicios retirados; motor
# detenido y ningun archivo en uso ni enlace; produccion inicializada, de la misma cuenta, con el mismo origen e
# identidad ACK del Formulario en AUTOMATICO y con Graph si la validacion lo tiene (son las unicas copias de esas
# credenciales en NEW-SIGER); cada ruta en D:, fuera de produccion, de los motores, de Java, de las constancias y del
# paquete; cada respaldo con backup.json de esta raiz y base (un hijo sin backup.json solo se acepta declarado en
# -RespaldosIncompletos; va en la constancia como RESPALDO_INCOMPLETO_DECLARADO, con archivos y bytes y sin nombres);
# cada ensayo con su esquema en el mysql\data de la raiz.
#
# Tras confirmar repite el inventario y exige que sea identico. La constancia eliminacion-validacion-<UTC>.json se
# escribe en -Constancias ANTES de borrar y se completa tras cada elemento (ensayos, respaldos incompletos declarados,
# respaldos y al final la raiz, cuyo
# instance.json y marcador se borran al ultimo). Ante un fallo se detiene: ese elemento queda FALLIDO y los siguientes
# NO_INTENTADO. Se vuelve a ejecutar con lo que queda (sin lo ya ELIMINADO) y la nueva constancia referencia a la
# anterior (archivo y SHA-256). La constancia lleva solo rutas, cifras, fechas, cuenta, motivo y huellas: ningun dato
# ciudadano ni secreto. Despues se registra en el registro institucional.
. (Join-Path $PSScriptRoot 'Environment.ps1')

function Full([string]$Path,[string]$What){
    if(-not $Path -or -not [IO.Path]::IsPathRooted($Path) -or $Path.StartsWith('\\')){throw "$What debe ser una ruta absoluta local."}
    return [IO.Path]::GetFullPath($Path).TrimEnd('\')
}
if([string]::IsNullOrWhiteSpace($Motivo) -or $Motivo.Length -gt 500 -or $Motivo -match '[\x00-\x1F\x7F]'){throw 'El motivo es obligatorio, de hasta 500 caracteres y en una sola linea.'}
$respaldosList=ConvertFrom-PathListArgument $Respaldos '-Respaldos'
$incompletosList=ConvertFrom-PathListArgument $RespaldosIncompletos '-RespaldosIncompletos'
$ensayosList=ConvertFrom-PathListArgument $Ensayos '-Ensayos'
$rootFull=Full $Root 'La raiz';$prodFull=Full $Produccion 'La produccion';$constDir=Full $Constancias 'La carpeta de constancias'
$package=[IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent)).TrimEnd('\')
$here=[IO.Path]::GetFullPath((Get-Location).ProviderPath).TrimEnd('\')
$identity=[Security.Principal.WindowsIdentity]::GetCurrent();$me=$identity.User.Value

$messages=[ordered]@{
    PROPOSITO='la instancia no es de VALIDACION';
    SIN_MARCADOR='falta config\piloto-cerrado.json o no se puede leer: cierra antes con Close-Validation.ps1';
    MARCADOR_SIN_TIPO='el marcador de cierre no es de Close-Validation.ps1 de 0.6.0 (kind CIERRE_VALIDACION)';
    MARCADOR_OTRA_RAIZ='el marcador de cierre es de otra raiz';
    SIN_COPIA_DEL_CIERRE='no hay en la carpeta de constancias una copia identica del marcador de cierre (cierre-validacion-*.json)';
    CUENTA='ejecuta como la cuenta de servicio de la instancia (ownerSid de instance.json)';
    SERVICIOS_REGISTRADOS='la instancia tiene servicios registrados: un administrador ejecuta antes Uninstall-Service.ps1';
    MOTOR_ACTIVO='el motor propio sigue activo o no se detuvo de forma ordenada (mysql\data\siger-a.pid o ibdata1 en uso)';
    ARCHIVOS_EN_USO='hay archivos abiertos por otro proceso o sin acceso para esta cuenta';
    ENLACES='hay enlaces (simbolicos o de union) en las rutas: no se siguen ni se borran';
    PRODUCCION='la produccion indicada no existe, no esta inicializada, no es PRODUCCION, es de otra cuenta o coincide con la validacion';
    FORMULARIO_VALIDACION='no se pudo leer el origen y la identidad ACK del Formulario de la validacion';
    FORMULARIO_PRODUCCION='la configuracion del Formulario de produccion falta o no es valida (Get-FormularioPullConfig)';
    FORMULARIO_ORIGEN_DISTINTO='produccion no tiene el mismo origen del Formulario que la validacion';
    FORMULARIO_ACK_DISTINTO='produccion no tiene la misma identidad ACK (ackKeyId) que la validacion';
    FORMULARIO_NO_AUTOMATICO='el Formulario de produccion no esta en AUTOMATICO (D13 sin acreditar)';
    GRAPH_PRODUCCION='la validacion tiene credencial de Graph y produccion no tiene una valida';
    PENDIENTE_ANTERIOR='la constancia anterior tiene elementos sin eliminar que no estan en esta ejecucion'}
$elementMessages=@{
    NO_EXISTE='no existe o no es una carpeta';FUERA_DE_D='debe ser una carpeta dentro de D: (no la unidad completa)';
    SOLAPADA='se solapa con otra ruta de la eliminacion';RESPALDO_AJENO='no es un respaldo de esta validacion (backup.json con su raiz y su base) o contiene otra cosa que no esta declarada en -RespaldosIncompletos';
    NO_ES_HIJO_DE_RESPALDOS='un respaldo incompleto debe ser hijo directo de una carpeta de -Respaldos que no sea a su vez un respaldo';
    CON_BACKUP_JSON='tiene backup.json: no es un respaldo incompleto (va en -Respaldos)';CON_RESTORE_RESULT='tiene restore-result.json: es un ensayo (va en -Ensayos)';
    CON_ENLACES='contiene enlaces';INSTANCIA_AJENA='su config-private\instance.json no es de la raiz y la cuenta de esta validacion';
    DIRECTORIO_ACTUAL='la consola esta dentro de esta ruta: ejecuta desde otra carpeta (por ejemplo la del paquete)';
    ENSAYO_AJENO='no es un ensayo de restauracion sobre el motor de esta validacion (restore-result.json y su esquema en mysql\data)'}

function Get-DeletionPlan {
    $s=Read-Settings $rootFull
    $purpose='';try{$purpose=Get-InstancePurpose $s}catch{$purpose=''}
    $config=Join-Path $rootFull 'config';$datadir=Join-Path $rootFull 'mysql\data'
    # Marcador de cierre y su copia identica en las constancias.
    $markerPath=Join-Path $config 'piloto-cerrado.json';$marker=$null;$copy=$null
    if(Test-Path -LiteralPath $markerPath -PathType Leaf){
        try{$marker=Get-Content -LiteralPath $markerPath -Raw|ConvertFrom-Json}catch{$marker=$null}
        $markerBytes=[Convert]::ToBase64String([IO.File]::ReadAllBytes($markerPath))
        if([IO.Directory]::Exists($constDir)){foreach($c in @(Get-ChildItem -LiteralPath $constDir -Filter 'cierre-validacion-*.json' -File|Sort-Object Name)){if([Convert]::ToBase64String([IO.File]::ReadAllBytes($c.FullName)) -ceq $markerBytes){$copy=$c}}}
    }
    # Servicios: ni service.json ni un servicio de Windows cuya ruta apunte a esta raiz (produccion usa los mismos nombres).
    $pattern=[regex]::Escape($rootFull)+'(\\|"|\s|$)'
    $services=@(Get-CimInstance Win32_Service -ErrorAction Stop|Where-Object {$_.PathName -and [regex]::IsMatch([string]$_.PathName,$pattern,[Text.RegularExpressions.RegexOptions]::IgnoreCase)}).Count
    $ibdata=Join-Path $datadir 'ibdata1'
    $engine=(Test-Path -LiteralPath (Join-Path $datadir 'siger-a.pid')) -or ((Test-Path -LiteralPath $ibdata -PathType Leaf) -and (Test-FileInUse $ibdata))
    # Produccion y sus credenciales, leidas como esta cuenta.
    $prodSettings=$null;$production=$null
    try{
        $prodSettings=Read-Settings $prodFull
        $production=[PSCustomObject]@{Purpose=(Get-InstancePurpose $prodSettings);Initialized=(Test-Path -LiteralPath (Join-Path $prodFull 'config\initialized.marker') -PathType Leaf);Root=$prodFull;OwnerMatch=([string](Get-Prop $prodSettings 'ownerSid') -eq $me)}
    }catch{$prodSettings=$null;$production=$null}
    $formValidation=$null;try{$formValidation=Get-FormularioIdentity $s}catch{$formValidation=$null}
    $formProduction=$null
    if($prodSettings){try{$c=Get-FormularioPullConfig $prodSettings;if($c){$formProduction=[PSCustomObject]@{Mode=$c.Mode;Origin=$c.Origin;AckKeyId=$c.AckKeyId}}}catch{$formProduction=$null}}
    $graphValidation=(Test-Path -LiteralPath (Join-Path $config 'correo-graph.json')) -or (Test-Path -LiteralPath (Join-Path $config 'graph-client-secret.clixml'))
    $graphProduction=$false
    if($prodSettings){try{$graphProduction=$null -ne (Get-GraphMailConfig $prodSettings -CualquierModo)}catch{$graphProduction=$false}}
    # Rutas protegidas: ningun destino puede estar dentro de ellas ni contenerlas.
    $protected=[ordered]@{PRODUCCION=$prodFull;CONSTANCIAS=$constDir;PAQUETE=$package}
    $protected['MYSQLHOME_VALIDACION']=[IO.Path]::GetFullPath([string]$s.mysqlHome).TrimEnd('\')
    $javaValidation=[IO.Path]::GetFullPath([string]$s.javaHome).TrimEnd('\')
    if(-not (Test-PathInside $javaValidation $rootFull)){$protected['JAVAHOME_VALIDACION']=$javaValidation}
    if($prodSettings){
        foreach($pair in @(@('MYSQLHOME_PRODUCCION','mysqlHome'),@('JAVAHOME_PRODUCCION','javaHome'),@('JAR_PRODUCCION','activeJar'))){
            $value=[string](Get-Prop $prodSettings $pair[1]);if($value){$protected[$pair[0]]=[IO.Path]::GetFullPath($value).TrimEnd('\')}
        }
    }
    # Elementos, en el orden en que se borran: ensayos, respaldos incompletos declarados, respaldos y la raiz.
    $elements=New-Object System.Collections.Generic.List[object]
    foreach($path in $ensayosList){
        $full=Full $path 'Cada ensayo';$blockers=@();$evidence=''
        if(-not [IO.Directory]::Exists($full)){$blockers+='NO_EXISTE'}else{
            $result=$null;$file=Join-Path $full 'restore-result.json'
            if(Test-Path -LiteralPath $file -PathType Leaf){try{$result=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json}catch{$result=$null}}
            $schema=Get-RestoreSchema $result
            if(-not $schema -or -not [IO.Directory]::Exists((Join-Path $datadir $schema))){$blockers+='ENSAYO_AJENO'}else{$evidence="esquema $schema"}
        }
        $elements.Add([PSCustomObject]@{Type='ENSAYO';Path=$full;Parent='';Last=@();Evidence=$evidence;Blockers=$blockers;Files=0;Bytes=0})
    }
    $containers=@(foreach($path in $respaldosList){$full=Full $path 'Cada respaldo';if([IO.Directory]::Exists($full) -and -not (Test-Path -LiteralPath (Join-Path $full 'backup.json'))){$full}})
    $declared=@()
    foreach($path in $incompletosList){
        $full=Full $path 'Cada respaldo incompleto';$blockers=@();$evidence='';$parent=Split-Path $full -Parent
        if(-not [IO.Directory]::Exists($full)){$blockers+='NO_EXISTE'}else{
            $instanceFile=Join-Path $full 'config-private\instance.json';$hasInstance=Test-Path -LiteralPath $instanceFile -PathType Leaf;$instance=$null
            if($hasInstance){try{$instance=Get-Content -LiteralPath $instanceFile -Raw|ConvertFrom-Json}catch{$instance=$null}}
            $blockers+=@(Get-IncompleteBackupBlockers $full $containers (Test-Path -LiteralPath (Join-Path $full 'backup.json')) (Test-Path -LiteralPath (Join-Path $full 'restore-result.json')) (Get-TreeInventory $full).Links $hasInstance $instance $rootFull ([string](Get-Prop $s 'ownerSid')))
            $evidence=if($hasInstance){'declarado por el responsable; config-private\instance.json de esta validacion'}else{'declarado por el responsable; sin config-private\instance.json (aceptado por la declaracion)'}
        }
        $declared+=$full
        $elements.Add([PSCustomObject]@{Type='RESPALDO_INCOMPLETO_DECLARADO';Path=$full;Parent=$parent;Last=@();Evidence=$evidence;Blockers=$blockers;Files=0;Bytes=0})
    }
    foreach($path in $respaldosList){
        $full=Full $path 'Cada respaldo';$blockers=@();$evidence=''
        if(-not [IO.Directory]::Exists($full)){$blockers+='NO_EXISTE'}else{
            # Un respaldo (backup.json en la propia ruta) o una carpeta de respaldos (Test-BackupContainer).
            $single=Test-Path -LiteralPath (Join-Path $full 'backup.json') -PathType Leaf
            $items=if($single){@((Get-Item -LiteralPath $full))}else{@(Get-ChildItem -LiteralPath $full -Force)}
            $children=@(foreach($item in $items){
                $meta=$null;$file=Join-Path $item.FullName 'backup.json'
                if($item -is [IO.DirectoryInfo] -and (Test-Path -LiteralPath $file -PathType Leaf)){try{$meta=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json}catch{$meta=$null}}
                [PSCustomObject]@{Path=$item.FullName;Meta=$meta}
            })
            $own=if($single){Test-BackupOfInstance $children[0].Meta $rootFull ([string]$s.database)}else{Test-BackupContainer $children $rootFull ([string]$s.database) $declared}
            $dates=@($children|Where-Object {$null -ne $_.Meta}|ForEach-Object {[string](Get-Prop $_.Meta 'createdUtc')})
            if(-not $own){$blockers+='RESPALDO_AJENO'}else{$evidence="$($dates.Count) respaldo(s) de esta validacion"+$(if($dates.Count){' ('+($dates -join ', ')+')'}else{''})}
        }
        $elements.Add([PSCustomObject]@{Type='RESPALDO';Path=$full;Parent='';Last=@();Evidence=$evidence;Blockers=$blockers;Files=0;Bytes=0})
    }
    # En la raiz se borran al final, en este orden, los archivos que un reintento necesita leer: credenciales del Formulario
    # y de Graph (comparacion con produccion), instance.json y el marcador de cierre.
    $last=@('config\formulario-pull.json','config\formulario-ack-identity.clixml','config\correo-graph.json','config\graph-client-secret.clixml','config\instance.json','config\piloto-cerrado.json')
    $elements.Add([PSCustomObject]@{Type='RAIZ';Path=$rootFull;Parent='';Last=$last;Evidence="instancia $purpose, base $($s.database)";Blockers=@();Files=0;Bytes=0})
    $links=0;$inUse=0
    foreach($e in $elements){
        # Un respaldo incompleto declarado esta dentro de su carpeta de -Respaldos por diseno: se borra antes que ella.
        $others=@($elements|Where-Object {-not [object]::ReferenceEquals($_,$e) -and -not ($e.Parent -and $_.Path -ieq $e.Parent) -and -not ($_.Parent -and $_.Parent -ieq $e.Path)}|ForEach-Object {$_.Path})
        $e.Blockers=@($e.Blockers)+@(Get-DeletionPathBlockers $e.Path $protected $others)
        # La consola no puede estar dentro de lo que se borra (Windows no borra la carpeta actual de un proceso).
        if(Test-PathInside $here $e.Path){$e.Blockers=@($e.Blockers)+'DIRECTORIO_ACTUAL'}
        if([IO.Directory]::Exists($e.Path)){$inv=Get-TreeInventory $e.Path;$e.Files=$inv.Files;$e.Bytes=$inv.Bytes;$links+=$inv.Links;$inUse+=$inv.InUse}
    }
    # La carpeta de -Respaldos se informa sin los archivos de sus respaldos incompletos (van en su propio elemento).
    foreach($e in @($elements|Where-Object {$_.Parent})){foreach($c in @($elements|Where-Object {$_.Path -ieq $e.Parent})){$c.Files-=$e.Files;$c.Bytes-=$e.Bytes}}
    $previous=Get-PreviousDeletion $constDir $rootFull
    $omitted=Get-PreviousOmitted $previous @($elements|ForEach-Object {$_.Path}) {param($p) [IO.Directory]::Exists($p)}
    $facts=[PSCustomObject]@{Purpose=$purpose;Root=$rootFull;Marker=$marker;MarkerCopy=($null -ne $copy);OwnerMatch=([string](Get-Prop $s 'ownerSid') -eq $me)
        ServiceJson=(Test-Path -LiteralPath (Join-Path $config 'service.json'));ServicesForRoot=$services;EngineActive=$engine;InUse=$inUse;Links=$links
        Production=$production;FormularioValidation=$formValidation;FormularioProduction=$formProduction;GraphValidation=$graphValidation;GraphProduction=$graphProduction;PreviousOmitted=$omitted}
    $restoreSchemas=if([IO.Directory]::Exists($datadir)){@(Get-ChildItem -LiteralPath $datadir -Directory -Filter 'siger_a_restore_*').Count}else{0}
    $closure=[PSCustomObject]@{file=$(if($copy){$copy.Name}else{''});sha256=$(if($copy){(Get-SigerFileHash -LiteralPath $copy.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}else{''})
        closedUtc=[string](Get-Prop $marker 'closedUtc');communications=(Get-Prop $marker 'communications');confirmed=(Get-Prop (Get-Prop $marker 'formulario') 'confirmed')}
    $blockers=@(Get-ValidationDeletionBlockers $facts)
    $signature=(@($elements|ForEach-Object {"$($_.Type)|$($_.Path)|$($_.Files)|$($_.Bytes)|$($_.Evidence)|$(@($_.Blockers) -join ',')"})+@($blockers -join ',')+@("$restoreSchemas|$($closure.sha256)|$(if($previous){$previous.Sha256})")) -join "`n"
    return [PSCustomObject]@{Settings=$s;Elements=$elements;Blockers=$blockers;Closure=$closure;Previous=$previous;RestoreSchemas=$restoreSchemas;Signature=$signature}
}
function Show-DeletionPlan($Plan){
    foreach($e in $Plan.Elements){
        $size=[Math]::Round($e.Bytes/1MB,1)
        Write-Host ("[$($e.Type)] $($e.Path): $($e.Files) archivos, $size MB. $($e.Evidence)"+$(if(@($e.Blockers).Count){' BLOQUEOS: '+((@($e.Blockers)|ForEach-Object {if($elementMessages.ContainsKey($_)){$elementMessages[$_]}else{$_}}) -join '; ')}else{''}))
    }
    Write-Host ("Esquemas siger_a_restore_* en el motor de la validacion: $($Plan.RestoreSchemas) (se borran con la raiz). Cierre: $($Plan.Closure.file) ($($Plan.Closure.closedUtc)).")
    if($Plan.Previous){Write-Host "Reintento: constancia anterior $($Plan.Previous.File) ($([string](Get-Prop $Plan.Previous.Record 'result')))."}
}
function Get-AllBlockers($Plan){
    $list=@($Plan.Blockers|ForEach-Object {$messages[$_]})
    foreach($e in $Plan.Elements){foreach($b in @($e.Blockers)){$list+="$($e.Path): "+$(if($elementMessages.ContainsKey($b)){$elementMessages[$b]}elseif($b -like 'PROTEGIDA_*'){'esta dentro de (o contiene) '+$b.Substring(10)}else{$b})}}
    return $list
}

$plan=Get-DeletionPlan
Show-DeletionPlan $plan
$all=@(Get-AllBlockers $plan)
if($all.Count -gt 0){throw ('No se elimina la validacion (nada se borro): '+($all -join '; ')+'. Revisa con el responsable antes de continuar.')}
if(-not $PSCmdlet.ShouldProcess($rootFull,'ELIMINAR la validacion cerrada, sus respaldos y sus ensayos indicados (no se puede deshacer)')){return}
$typed=Read-Host "Para confirmar, escribe la ruta de la raiz que se elimina ($rootFull)"
if(([string]$typed).Trim().TrimEnd('\') -ine $rootFull){throw 'La ruta escrita no coincide. No se borro nada.'}

# Inventario repetido tras la confirmacion: cualquier cambio detiene la eliminacion antes de escribir o borrar.
$again=Get-DeletionPlan
if($again.Signature -cne $plan.Signature -or @(Get-AllBlockers $again).Count -gt 0){Show-DeletionPlan $again;throw 'El inventario cambio despues de la confirmacion. No se borro nada: revisa y vuelve a ejecutar.'}

if(-not [IO.Directory]::Exists($constDir)){[void][IO.Directory]::CreateDirectory($constDir)}
if(([IO.DirectoryInfo]$constDir).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'La carpeta de constancias no puede ser un enlace.'}
$stamp=(Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$recordPath=Join-Path $constDir "eliminacion-validacion-$stamp.json"
$record=New-ValidationDeletionRecord $again.Settings $again.Closure $prodFull $again.Previous $again.Elements $again.RestoreSchemas $Motivo $identity.Name
$targets=@($again.Elements|ForEach-Object {[PSCustomObject]@{Path=$_.Path;Last=$_.Last}})
$record=Invoke-ValidationDeletion $record $recordPath $targets
$hash=(Get-SigerFileHash -LiteralPath $recordPath -Algorithm SHA256).Hash.ToLowerInvariant()
foreach($e in $record.elements){Write-Host "[$($e.type)] $($e.path): $($e.result)$(if($null -ne $e.remainingFiles){" (quedan $($e.remainingFiles) archivos)"})"}
Write-Host "Constancia: $recordPath (SHA-256 $hash)."
if($record.result -ne 'COMPLETA'){throw 'Eliminacion INCOMPLETA: revisa la causa del elemento FALLIDO y vuelve a ejecutar con lo que queda (sin lo ya ELIMINADO). La nueva constancia referenciara a esta.'}
Write-Host 'Validacion eliminada. Registra la constancia (archivo y SHA-256) en el registro institucional.'
