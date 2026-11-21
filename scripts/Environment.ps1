# Dot-source desde Start/Service/Migrate/Invoke. Secretos DPAPI solo en el proceso; nunca argumentos Java.
. (Join-Path $PSScriptRoot 'Common.ps1')
function Set-SigerEnvironment($Settings,$Secrets,[switch]$Migration,[switch]$Seed) {
    # Primero el modo: un descriptor dudoso detiene todo antes de exponer variables al proceso.
    $instanceMode=Get-InstanceMode $Settings
    $env:SPRING_PROFILES_ACTIVE='mysql'
    $env:SIGER_DB_URL="jdbc:mysql://127.0.0.1:$($Settings.mysqlPort)/$($Settings.database)?sslMode=VERIFY_CA&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    $env:SIGER_DB_USER=if($Migration){'siger_a_migrator'}else{'siger_a_app'}
    $env:SIGER_DB_PASSWORD=if($Migration){Secret-Text $Secrets.Migrator}else{Secret-Text $Secrets.App}
    $env:SIGER_DB_TRUSTSTORE=([Uri](Join-Path $Settings.root 'config\mysql-trust.p12')).AbsoluteUri
    $env:SIGER_DB_TRUST_PASSWORD=Secret-Text $Secrets.Trust
    $env:SIGER_DATA=Join-Path $Settings.root 'data';$env:SIGER_TEMP=Join-Path $Settings.root 'data\tmp'
    # Solo local (127.0.0.1) salvo bindAddress 0.0.0.0 (Init-Server -AccesoRed); el acceso lo restringe el firewall de TI.
    $bind=$Settings.PSObject.Properties['bindAddress'];$bindValue=if($bind){[string]$bind.Value}else{'127.0.0.1'}
    if(@('127.0.0.1','0.0.0.0') -notcontains $bindValue){throw "bindAddress no admitido en instance.json ('$bindValue'): usa 127.0.0.1 o 0.0.0.0."}
    $env:SIGER_BIND=$bindValue;$env:SIGER_PORT=[string]$Settings.webPort;$env:SIGER_HTTPS='true'
    $env:SIGER_KEYSTORE=([Uri](Join-Path $Settings.root 'config\https.p12')).AbsoluteUri
    $env:SIGER_TLS_PASSWORD=Secret-Text $Secrets.Tls
    $env:SIGER_MIGRATE=if($Migration){'true'}else{'false'}
    $env:SIGER_SEED_DEMO=if($Seed){'true'}else{'false'}
    # Claves publicas confiables del Formulario (opcional). Sin archivo, SIGER rechaza todo original externo.
    $keys=Join-Path $Settings.root 'config\formulario-trusted-keys.json'
    $env:SIGER_FORMULARIO_TRUSTED_KEYS=if(Test-Path -LiteralPath $keys -PathType Leaf){$keys}else{$null}
    # Prefijo de folio COM comun al servicio y al ciclo del Formulario (instance.json folioPrefix; por defecto COM-A).
    $prefix=$Settings.PSObject.Properties['folioPrefix']
    $env:SIGER_FOLIO_PREFIX=if($prefix -and $prefix.Value){[string]$prefix.Value}else{$null}
    # Modo de la instancia: la de pruebas lo anuncia en pantalla y admite la liberacion manual de archivos de prueba;
    # la institucional no muestra etiqueta y solo libera originales tras un analisis antivirus del servidor.
    $purpose=Get-InstancePurpose $Settings
    if($instanceMode -eq 'PRUEBAS'){
        $env:SIGER_ENVIRONMENT_LABEL="Entorno de pruebas $([char]0xB7) Datos sint$([char]0xE9)ticos";$env:SIGER_DOCUMENTS_SYNTHETIC_RELEASE='true'
    }elseif($purpose -eq 'VALIDACION'){
        # Solo la validacion institucional expresamente autorizada puede recibir comunicaciones reales.
        if(Test-ValidationPullAuthorized $Settings){$env:SIGER_ENVIRONMENT_LABEL="Validacion institucional - recepcion real controlada";$env:SIGER_DOCUMENTS_SYNTHETIC_RELEASE='false'}else{$env:SIGER_ENVIRONMENT_LABEL="Validaci$([char]0xF3)n institucional $([char]0xB7) Datos ficticios, no ingresar casos reales";$env:SIGER_DOCUMENTS_SYNTHETIC_RELEASE='false'}
    }else{$env:SIGER_ENVIRONMENT_LABEL=$null;$env:SIGER_DOCUMENTS_SYNTHETIC_RELEASE='false'}
    # Motor antivirus (config\antivirus.json: {"engine":"CLAMAV"|"DEFENDER","executable":"...","database":"..."}).
    $av=Join-Path $Settings.root 'config\antivirus.json'
    if(Test-Path -LiteralPath $av -PathType Leaf){
        $cfg=Get-Content -LiteralPath $av -Raw|ConvertFrom-Json
        $env:SIGER_AV_ENGINE=[string]$cfg.engine;$env:SIGER_AV_EXECUTABLE=[string]$cfg.executable
        $env:SIGER_AV_DATABASE=if($cfg.PSObject.Properties['database']){[string]$cfg.database}else{$null}
    }else{$env:SIGER_AV_ENGINE=$null;$env:SIGER_AV_EXECUTABLE=$null;$env:SIGER_AV_DATABASE=$null}
}
# Modo de la instancia, fallando cerrado ante cualquier valor desconocido, vacio o mal escrito. Compatibilidad
# explicita: un descriptor anterior a los modos (sin "mode" ni "ownerSid", creado por Init-Server de 0.4.0 previo) es
# una instancia de PRUEBAS, porque esas versiones solo creaban datos sinteticos. Un descriptor con ownerSid y sin modo
# es inconsistente y no se interpreta.
function Get-InstanceMode($Settings){
    $mode=$Settings.PSObject.Properties['mode']
    if(-not $mode){
        if($Settings.PSObject.Properties['ownerSid']){throw 'instance.json tiene ownerSid pero no indica "mode". No se asume ningun modo; corrige el descriptor.'}
        return 'PRUEBAS'
    }
    $value=[string]$mode.Value
    if($value -ceq 'INSTITUCIONAL' -or $value -ceq 'PRUEBAS'){return $value}
    throw "Modo de instancia desconocido en instance.json ('$value'). Se admiten INSTITUCIONAL y PRUEBAS; no se inicia con un modo dudoso."
}
# Proposito de una instancia institucional: VALIDACION (piloto con datos ficticios; nunca recibe entregas reales del
# Formulario) o PRODUCCION. Falla cerrado si falta o es desconocido. Las instancias de pruebas son PRUEBAS.
function Get-InstancePurpose($Settings){
    if((Get-InstanceMode $Settings) -eq 'PRUEBAS'){return 'PRUEBAS'}
    $purpose=$Settings.PSObject.Properties['purpose']
    $value=if($purpose){[string]$purpose.Value}else{''}
    if($value -ceq 'VALIDACION' -or $value -ceq 'PRODUCCION'){return $value}
    throw "Proposito de instancia ausente o desconocido en instance.json ('$value'). Se admiten VALIDACION y PRODUCCION."
}
# La habilitacion de recepcion real en validacion es explicita y solo admite un booleano JSON real.
function Test-ValidationPullAuthorized($Settings){
    if((Get-InstanceMode $Settings) -ne 'INSTITUCIONAL' -or (Get-InstancePurpose $Settings) -ne 'VALIDACION'){return $false}
    $flag=$Settings.PSObject.Properties['validacionPullAutorizada']
    if(-not $flag){return $false}
    return (($flag.Value -is [bool]) -and ($flag.Value -eq $true))
}
# El corte de cualquier consumidor paralelo de SMR debe confirmarse operativamente.
# Esta bandera declara la autorizacion; NO verifica por red que SMR este desconectado.
function Require-ProductionPull($Settings){
    if((Get-InstancePurpose $Settings) -eq 'PRODUCCION'){return}
    if(Test-ValidationPullAuthorized $Settings){return}
    throw 'Recepcion real bloqueada: requiere PRODUCCION o validacion institucional expresamente autorizada y consumidor SMR detenido.'
}
# Cierre de una validacion (Close-Validation.ps1, D11 de docs/PLAN_0_6_0.md). Las cifras salen de una sola consulta que
# usa solo tablas y columnas de V8/V9 (tambien vale sobre una validacion 0.4.0, esquema V10), en este orden y separadas
# por tabulador: confirmadas, registradas, ACK pendientes, rechazadas, estados desconocidos, COM externas, COM externas
# sin recepcion, COM totales, pausa vigente (0/1), instante de la pausa en ms (-1 sin pausa), recepciones posteriores
# a la pausa y COM externas posteriores a la pausa. Falla cerrado ante cualquier cifra que no sea un entero valido.
$script:ValidationClosureFields=@('confirmed','registered','ackPending','rejected','unknownStates','external','externalWithoutReception','communications','paused','pauseMs','afterPause','externalAfterPause')
$script:ValidationClosureSql=(@(
    'SELECT',
    ' (SELECT COUNT(*) FROM formulario_reception WHERE state=''CONFIRMADA''),',
    ' (SELECT COUNT(*) FROM formulario_reception WHERE state=''REGISTRADA''),',
    ' (SELECT COUNT(*) FROM formulario_reception WHERE ack_pending=TRUE),',
    ' (SELECT COUNT(*) FROM formulario_reception WHERE state=''RECHAZADA''),',
    ' (SELECT COUNT(*) FROM formulario_reception WHERE state NOT IN (''RECHAZADA'',''REGISTRADA'',''CONFIRMADA'')),',
    ' (SELECT COUNT(*) FROM communication WHERE external_source IS NOT NULL),',
    ' (SELECT COUNT(*) FROM communication c WHERE c.external_source IS NOT NULL AND NOT EXISTS(SELECT 1 FROM formulario_reception f WHERE f.communication_id=c.id AND f.state IN (''REGISTRADA'',''CONFIRMADA''))),',
    ' (SELECT COUNT(*) FROM communication),',
    ' (SELECT COUNT(*) FROM integration_control WHERE name=''FORMULARIO_PULL'' AND paused=TRUE),',
    ' (SELECT COALESCE(MAX(updated_ms),-1) FROM integration_control WHERE name=''FORMULARIO_PULL'' AND paused=TRUE),',
    ' (SELECT COUNT(*) FROM formulario_reception f JOIN integration_control i ON i.name=''FORMULARIO_PULL'' AND i.paused=TRUE WHERE f.created_ms>=i.updated_ms OR f.registered_ms>=i.updated_ms),',
    ' (SELECT COUNT(*) FROM communication c JOIN integration_control i ON i.name=''FORMULARIO_PULL'' AND i.paused=TRUE WHERE c.external_source IS NOT NULL AND c.created_ms>=i.updated_ms)') -join ' ')
function ConvertTo-ValidationClosureFigures([string]$Text){
    $parts=@(([string]$Text).Trim() -split "`t")
    if($parts.Count -ne $script:ValidationClosureFields.Count){throw 'Cifras de cierre incompletas. No se cierra.'}
    $figures=[ordered]@{}
    for($i=0;$i -lt $parts.Count;$i++){
        if($parts[$i] -notmatch '^-?[0-9]{1,18}$'){throw 'Cifra de cierre no valida. No se cierra.'}
        $figures[$script:ValidationClosureFields[$i]]=[long]$parts[$i]
    }
    foreach($name in $script:ValidationClosureFields){if($name -ne 'pauseMs' -and $figures[$name] -lt 0){throw 'Cifra de cierre negativa. No se cierra.'}}
    if($figures.paused -gt 1 -or ($figures.paused -eq 1 -and $figures.pauseMs -lt 0) -or ($figures.paused -eq 0 -and $figures.pauseMs -ne -1)){throw 'Estado de pausa incoherente. No se cierra.'}
    return [PSCustomObject]$figures
}
# Bloqueos del cierre (lista vacia = se puede cerrar). Sin recepciones ni COM externas se cierra como antes (sin exigir
# pausa). Con recepciones rigen las tres condiciones de D11 y la coherencia de los datos:
# - PROPOSITO: solo VALIDACION; la produccion nunca se cierra.
# - SIN_DECLARACION / CANTIDAD_DISTINTA: la cantidad que el responsable declaro al pausar es igual a las CONFIRMADA.
# - REGISTRADA / ACK_PENDIENTE: ninguna recepcion sin confirmar al Formulario.
# - SIN_PAUSA / POSTERIOR_A_LA_PAUSA: pausa de FORMULARIO_PULL vigente y ninguna recepcion (en cualquier estado, por su
#   creacion o su registro) ni COM externa creada en el instante de la pausa o despues. El ciclo manual (run-once) no
#   consulta la pausa: por eso se comprueba aqui. Un ACK confirmado despues de la pausa no es una recepcion.
# - ESTADO_DESCONOCIDO / COM_EXTERNA_SIN_RECEPCION: datos que este cierre no sabe explicar.
# Las RECHAZADA no tienen COM ni datos ciudadanos: no entran en la cantidad declarada y bloquean solo si son posteriores
# a la pausa (prueban que se reclamo despues de pausar).
function Get-ValidationClosureBlockers([string]$Purpose,$Figures,$Declared){
    $f=$Figures;$out=New-Object System.Collections.Generic.List[string]
    if($Purpose -cne 'VALIDACION'){$out.Add('PROPOSITO')}
    $any=($f.confirmed+$f.registered+$f.ackPending+$f.rejected+$f.unknownStates+$f.external) -gt 0
    if(-not $any){
        if($null -ne $Declared -and [long]$Declared -ne 0){$out.Add('CANTIDAD_DISTINTA')}
        return $out.ToArray()
    }
    if($null -eq $Declared){$out.Add('SIN_DECLARACION')}elseif([long]$Declared -ne $f.confirmed){$out.Add('CANTIDAD_DISTINTA')}
    if($f.registered -gt 0){$out.Add('REGISTRADA')}
    if($f.ackPending -gt 0){$out.Add('ACK_PENDIENTE')}
    if($f.unknownStates -gt 0){$out.Add('ESTADO_DESCONOCIDO')}
    if($f.externalWithoutReception -gt 0){$out.Add('COM_EXTERNA_SIN_RECEPCION')}
    if($f.paused -ne 1){$out.Add('SIN_PAUSA')}elseif(($f.afterPause+$f.externalAfterPause) -gt 0){$out.Add('POSTERIOR_A_LA_PAUSA')}
    return $out.ToArray()
}
function Clear-SigerEnvironment {
    foreach($key in @('SPRING_PROFILES_ACTIVE','SIGER_DB_URL','SIGER_DB_USER','SIGER_DB_PASSWORD','SIGER_DB_TRUSTSTORE','SIGER_DB_TRUST_PASSWORD','SIGER_DATA','SIGER_TEMP','SIGER_BIND','SIGER_PORT','SIGER_HTTPS','SIGER_KEYSTORE','SIGER_TLS_PASSWORD','SIGER_MIGRATE','SIGER_SEED_DEMO','SIGER_FORMULARIO_TRUSTED_KEYS','SIGER_FOLIO_PREFIX','SIGER_SCHEDULING','SIGER_FORMULARIO_PULL_ENABLED','SIGER_FORMULARIO_PULL_RUN_ONCE','SIGER_FORMULARIO_PULL_ORIGIN','SIGER_FORMULARIO_PULL_TOKEN','SIGER_FORMULARIO_PULL_TOKEN_FILE','SIGER_FORMULARIO_PULL_CA_FILE','SIGER_FORMULARIO_PULL_SCOPE','SIGER_FORMULARIO_ACK_KEY_ID','SIGER_FORMULARIO_ACK_PRIVATE_KEY_FILE','SIGER_FORMULARIO_ACK_PUBLIC_KEY_FILE','SIGER_FORMULARIO_PULL_AUTOMATIC','SIGER_FORMULARIO_PULL_SINGLE_CONSUMER','SIGER_FORMULARIO_ACK_PRIVATE_KEY','SIGER_FORMULARIO_ACK_PUBLIC_KEY','SIGER_MAIL_MODE','SIGER_GRAPH_TENANT','SIGER_GRAPH_CLIENT','SIGER_GRAPH_SENDER','SIGER_GRAPH_CLIENT_SECRET','SIGER_MAIL_ALLOWED_DOMAINS','SIGER_MAIL_LINK','SIGER_STOP_FILE','SIGER_LOG_FILE','SIGER_ENVIRONMENT_LABEL','SIGER_DOCUMENTS_SYNTHETIC_RELEASE','SIGER_AV_ENGINE','SIGER_AV_EXECUTABLE','SIGER_AV_DATABASE','SIGER_BOOTSTRAP_FILE','SIGER_FORMULARIO_PULL_PROBE','SIGER_AUDIT_SEARCH_KEY','SIGER_MAIL_CHECK_MODE','SIGER_MAIL_CHECK_TO')){[Environment]::SetEnvironmentVariable($key,$null,'Process')}
}

# --- Integraciones (configuracion en config\, secretos DPAPI de la cuenta del servicio) -----------------------
# formulario-pull.json: {"origin","scope","ackKeyId","caFile","mode":"MANUAL"|"AUTOMATICO","consumidorUnicoConfirmado":true}
# AUTOMATICO activa el consumidor permanente en el servicio y exige declarar que el consumidor de SMR esta detenido.
function Get-FormularioPullConfig($Settings) {
    $config=Join-Path $Settings.root 'config'
    $file=Join-Path $config 'formulario-pull.json'
    if(-not(Test-Path -LiteralPath $file -PathType Leaf)){return $null}
    $pull=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    foreach($name in @('origin','scope')){if(-not $pull.PSObject.Properties[$name] -or -not [string]$pull.$name){throw "Falta '$name' en formulario-pull.json."}}
    if([string]$pull.origin -notmatch '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/?$'){throw 'El origen del Formulario debe ser https://host sin ruta.'}
    $mode='MANUAL'
    if($pull.PSObject.Properties['mode'] -and [string]$pull.mode){$mode=([string]$pull.mode).ToUpperInvariant()}
    if(@('MANUAL','AUTOMATICO') -notcontains $mode){throw 'mode en formulario-pull.json debe ser MANUAL o AUTOMATICO.'}
    $single=$pull.PSObject.Properties['consumidorUnicoConfirmado'] -and $pull.consumidorUnicoConfirmado -eq $true
    if($mode -eq 'AUTOMATICO' -and -not $single){throw 'El modo AUTOMATICO exige "consumidorUnicoConfirmado": true en formulario-pull.json, despues de detener con autorizacion el consumidor del Formulario de SMR.'}
    if($mode -eq 'AUTOMATICO'){Require-ProductionPull $Settings}
    $prefix=$Settings.PSObject.Properties['folioPrefix']
    if(-not $prefix -or [string]$prefix.Value -cnotmatch '^[A-Z][A-Z0-9]{1,15}$'){throw 'Define en instance.json un folioPrefix compatible con el comprobante del Formulario ([A-Z][A-Z0-9]{1,15}).'}
    if(-not(Test-Path -LiteralPath (Join-Path $config 'formulario-trusted-keys.json') -PathType Leaf)){throw 'Falta config\formulario-trusted-keys.json con la clave publica de custodia del Formulario.'}
    $token=Join-Path $config 'formulario-pull-token.clixml';Require-PrivateFile $token
    # Identidad ACK: importada (DPAPI, por ejemplo la de SMR) o par PEM generado por New-FormularioAckKey.ps1.
    $identity=Join-Path $config 'formulario-ack-identity.clixml';$private=Join-Path $config 'formulario-ack-private.pem';$public=Join-Path $config 'formulario-ack-public.pem'
    $keyId=if($pull.PSObject.Properties['ackKeyId']){[string]$pull.ackKeyId}else{''}
    if(Test-Path -LiteralPath $identity -PathType Leaf){
        Require-PrivateFile $identity
        $idKey=[string](Import-Clixml -LiteralPath $identity).KeyId
        if($keyId -and $keyId -cne $idKey){throw 'ackKeyId de formulario-pull.json no coincide con la identidad ACK importada.'}
        $keyId=$idKey;$private=$null;$public=$null
    }else{
        $identity=$null;Require-PrivateFile $private
        if(-not(Test-Path -LiteralPath $public -PathType Leaf)){throw 'Falta config\formulario-ack-public.pem.'}
    }
    if($keyId -notmatch '^[A-Za-z0-9_-]{1,64}$'){throw 'Falta ackKeyId valido (formulario-pull.json o identidad ACK importada).'}
    $ca=$null
    if($pull.PSObject.Properties['caFile'] -and [string]$pull.caFile){$ca=Join-Path $config ([IO.Path]::GetFileName([string]$pull.caFile));if(-not(Test-Path -LiteralPath $ca -PathType Leaf)){throw 'No existe el certificado de CA indicado en formulario-pull.json.'}}
    return [PSCustomObject]@{Mode=$mode;Origin=[string]$pull.origin;Scope=[string]$pull.scope;AckKeyId=$keyId;Token=$token;Identity=$identity;Private=$private;Public=$public;Ca=$ca}
}
function Set-FormularioPullEnvironment($Pull,[switch]$RunOnce,[switch]$Probe) {
    $env:SIGER_FORMULARIO_PULL_ENABLED='true'
    if($Probe){$env:SIGER_FORMULARIO_PULL_PROBE='true'}
    elseif($RunOnce){$env:SIGER_FORMULARIO_PULL_RUN_ONCE='true'}else{$env:SIGER_FORMULARIO_PULL_AUTOMATIC='true';$env:SIGER_FORMULARIO_PULL_SINGLE_CONSUMER='true'}
    $env:SIGER_FORMULARIO_PULL_ORIGIN=$Pull.Origin;$env:SIGER_FORMULARIO_PULL_SCOPE=$Pull.Scope
    $env:SIGER_FORMULARIO_PULL_TOKEN=Secret-Text (Import-Clixml -LiteralPath $Pull.Token)
    $env:SIGER_FORMULARIO_PULL_CA_FILE=$Pull.Ca
    $env:SIGER_FORMULARIO_ACK_KEY_ID=$Pull.AckKeyId
    if($Pull.Identity){
        $id=Import-Clixml -LiteralPath $Pull.Identity
        $env:SIGER_FORMULARIO_ACK_PRIVATE_KEY=Secret-Text $id.PrivatePem;$env:SIGER_FORMULARIO_ACK_PUBLIC_KEY=[string]$id.PublicPem
    }else{$env:SIGER_FORMULARIO_ACK_PRIVATE_KEY_FILE=$Pull.Private;$env:SIGER_FORMULARIO_ACK_PUBLIC_KEY_FILE=$Pull.Public}
}
# correo-graph.json: {"mode":"GRAPH"|"DESACTIVADO","tenant","client","sender","allowedDomains":["defensorianinez.cl"],"link":"https://..."}
# Secreto del cliente en graph-client-secret.clixml (DPAPI). Sin archivo o con DESACTIVADO no hay correo.
# -CualquierModo (Test-GraphMail.ps1): lee y valida la credencial tambien con DESACTIVADO, sin activar el correo.
function Get-GraphMailConfig($Settings,[switch]$CualquierModo) {
    $config=Join-Path $Settings.root 'config'
    $file=Join-Path $config 'correo-graph.json'
    if(-not(Test-Path -LiteralPath $file -PathType Leaf)){return $null}
    $mail=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    $mode=if($mail.PSObject.Properties['mode']){([string]$mail.mode).ToUpperInvariant()}else{''}
    if(@('GRAPH','DESACTIVADO') -notcontains $mode){throw 'mode en correo-graph.json debe ser GRAPH o DESACTIVADO.'}
    if($mode -eq 'DESACTIVADO' -and -not $CualquierModo){return $null}
    foreach($name in @('tenant','client','sender')){if(-not $mail.PSObject.Properties[$name] -or -not [string]$mail.$name){throw "Falta '$name' en correo-graph.json."}}
    $domains=@();if($mail.PSObject.Properties['allowedDomains']){$domains=@($mail.allowedDomains|ForEach-Object {[string]$_}|Where-Object {$_})}
    if($domains.Count -eq 0){throw 'correo-graph.json exige allowedDomains (dominios institucionales de los destinatarios).'}
    $secret=Join-Path $config 'graph-client-secret.clixml';Require-PrivateFile $secret
    $link=if($mail.PSObject.Properties['link']){[string]$mail.link}else{''}
    return [PSCustomObject]@{Mode=$mode;Tenant=[string]$mail.tenant;Client=[string]$mail.client;Sender=[string]$mail.sender;Domains=($domains -join ',');Link=$link;Secret=$secret}
}
# Formato de la credencial Graph, con las mismas reglas que GraphMail.java (tenant y cliente GUID, remitente, secreto de
# 20 a 512 caracteres sin caracteres de control) y dominios validos. Lanza un mensaje sin repetir ningun valor.
function Assert-GraphCredential([string]$Tenant,[string]$Client,[string]$Sender,[string]$Secret,[string[]]$Domains) {
    if($Tenant -cnotmatch '^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$'){throw 'El Tenant ID no tiene formato GUID.'}
    if($Client -cnotmatch '^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$'){throw 'El Client ID no tiene formato GUID.'}
    if($Sender -cnotmatch '^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$'){throw 'El remitente no es una direccion de correo valida.'}
    if(-not $Secret -or $Secret.Length -lt 20 -or $Secret.Length -gt 512 -or $Secret -match '[\x00-\x1f\x7f-\x9f]'){throw 'El secreto del cliente Graph debe tener entre 20 y 512 caracteres, sin caracteres de control.'}
    $list=@($Domains|Where-Object {$_})
    if($list.Count -eq 0){throw 'Indica al menos un dominio permitido para los destinatarios.'}
    foreach($d in $list){if($d -cnotmatch '^[a-z0-9-]+(\.[a-z0-9-]+)+$'){throw 'Dominio permitido con formato invalido (minusculas, sin @).'}}
}
# Destinatario de la prueba controlada: direccion valida de un dominio permitido (la misma regla que GraphCheck.java).
function Test-GraphRecipient([string]$Address,[string[]]$Domains) {
    if($Address -cnotmatch '^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$'){return $false}
    $domain=$Address.Substring($Address.IndexOf('@')+1).ToLowerInvariant()
    return (@($Domains|ForEach-Object {([string]$_).Trim().ToLowerInvariant()}) -contains $domain)
}
# Proceso de la prueba controlada (Test-GraphMail.ps1): credencial y modo, nunca SIGER_MAIL_MODE (la prueba no usa la
# cola de avisos y GraphCheck rechaza GRAPH). El destinatario va solo en una variable del proceso.
function Set-GraphCheckEnvironment($Mail,[string]$Mode,[string]$To) {
    [Environment]::SetEnvironmentVariable('SIGER_MAIL_MODE',$null,'Process')
    $env:SIGER_GRAPH_TENANT=$Mail.Tenant;$env:SIGER_GRAPH_CLIENT=$Mail.Client;$env:SIGER_GRAPH_SENDER=$Mail.Sender
    $env:SIGER_GRAPH_CLIENT_SECRET=Secret-Text (Import-Clixml -LiteralPath $Mail.Secret)
    $env:SIGER_MAIL_ALLOWED_DOMAINS=$Mail.Domains
    $env:SIGER_MAIL_CHECK_MODE=$Mode;$env:SIGER_MAIL_CHECK_TO=if($To){$To}else{$null}
}
function Set-GraphMailEnvironment($Mail,[switch]$EnqueueOnly) {
    $env:SIGER_MAIL_MODE='GRAPH';$env:SIGER_GRAPH_TENANT=$Mail.Tenant;$env:SIGER_GRAPH_CLIENT=$Mail.Client;$env:SIGER_GRAPH_SENDER=$Mail.Sender
    # Un proceso que solo encola (ciclo manual sin tareas programadas) no recibe el secreto.
    if(-not $EnqueueOnly){$env:SIGER_GRAPH_CLIENT_SECRET=Secret-Text (Import-Clixml -LiteralPath $Mail.Secret)}
    $env:SIGER_MAIL_ALLOWED_DOMAINS=$Mail.Domains;$env:SIGER_MAIL_LINK=if($Mail.Link){$Mail.Link}else{$null}
}
# Proceso de servicio (Service.ps1 o Start.ps1): base + parada ordenada + integraciones permanentes configuradas.
function Set-SigerServiceEnvironment($Settings,$Secrets) {
    Set-SigerEnvironment $Settings $Secrets
    $env:SIGER_STOP_FILE=Join-Path $Settings.root 'run\stop.request'
    $pull=Get-FormularioPullConfig $Settings
    if($pull -and $pull.Mode -eq 'AUTOMATICO'){Set-FormularioPullEnvironment $pull}
    $mail=Get-GraphMailConfig $Settings
    if($mail){Set-GraphMailEnvironment $mail}
    # Clave HMAC del buscador (New-AuditSearchKey.ps1, DPAPI). Sin archivo, la auditoria de busquedas no guarda huella.
    $searchKey=Join-Path $Settings.root 'config\audit-search-key.clixml'
    if(Test-Path -LiteralPath $searchKey -PathType Leaf){Require-PrivateFile $searchKey;$env:SIGER_AUDIT_SEARCH_KEY=Secret-Text (Import-Clixml -LiteralPath $searchKey)}
}
