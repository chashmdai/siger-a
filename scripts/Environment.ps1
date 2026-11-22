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
# Proposito de una instancia institucional: VALIDACION (piloto con datos ficticios; no recibe entregas del Formulario,
# salvo las de prueba que autoriza expresamente "validacionPullAutorizada": true en instance.json, como la validacion de
# la VM hasta su cierre en 0.6.0) o PRODUCCION. Falla cerrado si falta o es desconocido. Las de pruebas son PRUEBAS.
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
    foreach($key in @('SPRING_PROFILES_ACTIVE','SIGER_DB_URL','SIGER_DB_USER','SIGER_DB_PASSWORD','SIGER_DB_TRUSTSTORE','SIGER_DB_TRUST_PASSWORD','SIGER_DATA','SIGER_TEMP','SIGER_BIND','SIGER_PORT','SIGER_HTTPS','SIGER_KEYSTORE','SIGER_TLS_PASSWORD','SIGER_MIGRATE','SIGER_SEED_DEMO','SIGER_FORMULARIO_TRUSTED_KEYS','SIGER_FOLIO_PREFIX','SIGER_SCHEDULING','SIGER_FORMULARIO_PULL_ENABLED','SIGER_FORMULARIO_PULL_RUN_ONCE','SIGER_FORMULARIO_PULL_ORIGIN','SIGER_FORMULARIO_PULL_TOKEN','SIGER_FORMULARIO_PULL_TOKEN_FILE','SIGER_FORMULARIO_PULL_CA_FILE','SIGER_FORMULARIO_PULL_SCOPE','SIGER_FORMULARIO_ACK_KEY_ID','SIGER_FORMULARIO_ACK_PRIVATE_KEY_FILE','SIGER_FORMULARIO_ACK_PUBLIC_KEY_FILE','SIGER_FORMULARIO_PULL_AUTOMATIC','SIGER_FORMULARIO_PULL_SINGLE_CONSUMER','SIGER_FORMULARIO_ACK_PRIVATE_KEY','SIGER_FORMULARIO_ACK_PUBLIC_KEY','SIGER_MAIL_MODE','SIGER_GRAPH_TENANT','SIGER_GRAPH_CLIENT','SIGER_GRAPH_SENDER','SIGER_GRAPH_CLIENT_SECRET','SIGER_MAIL_ALLOWED_DOMAINS','SIGER_MAIL_LINK','SIGER_STOP_FILE','SIGER_LOG_FILE','SIGER_ENVIRONMENT_LABEL','SIGER_DOCUMENTS_SYNTHETIC_RELEASE','SIGER_AV_ENGINE','SIGER_AV_EXECUTABLE','SIGER_AV_DATABASE','SIGER_BOOTSTRAP_FILE','SIGER_FORMULARIO_PULL_PROBE','SIGER_AUDIT_SEARCH_KEY','SIGER_MAIL_CHECK_MODE','SIGER_MAIL_CHECK_TO','SIGER_ORIGIN_RELEASE_FILE')){[Environment]::SetEnvironmentVariable($key,$null,'Process')}
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
# Activar el correo (Set-Integration.ps1 -Correo Graph, 0.6.0 H04a): exige la evidencia de Test-GraphMail.ps1
# (run\graph-prueba.json) de un Envio ACEPTADO con el JAR activo y de menos de 24 horas, como la sonda del Formulario.
# Regla pura con casos en Check-Scripts.ps1; un Token, un rechazo, otro JAR, una fecha ilegible o futura no bastan.
function Test-GraphProbeAccredited($Probe,[string]$JarSha256,[datetime]$NowUtc){
    if(-not $Probe -or -not $JarSha256){return $false}
    if([string](Get-Prop $Probe 'modo') -cne 'ENVIO' -or [string](Get-Prop $Probe 'estado') -cne 'ACEPTADO' -or [string](Get-Prop $Probe 'jarSha256') -ine $JarSha256){return $false}
    $raw=Get-Prop $Probe 'utc'
    if($raw -is [datetime]){$at=$raw.ToUniversalTime()}else{
        $at=[datetime]::MinValue
        if(-not [datetime]::TryParse([string]$raw,[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind,[ref]$at)){return $false}
        $at=$at.ToUniversalTime()
    }
    return ($at -le $NowUtc.AddMinutes(5) -and $at -ge $NowUtc.AddHours(-24))
}
# Listas por parametro (0.6.0, H04a). Con powershell.exe -File cada argumento llega como texto: un arreglo no se puede
# pasar (@() llega como el texto '@()' o se pierde, y 'a','b' llega como 'a,b'). Por eso las listas son un solo texto.
# Rutas: separadas por '|' (no es valido en una ruta de Windows) o la palabra NINGUNO, que declara expresamente que no
# hay ninguna. Falla ante texto vacio, @(), una coma (un arreglo de PowerShell convertido a texto), NINGUNO mezclado con
# rutas, tramos vacios o rutas repetidas. No normaliza: cada script valida despues cada ruta.
function ConvertFrom-PathListArgument([string]$Text,[string]$What){
    $t=([string]$Text).Trim()
    $hint="$What`: indica las rutas separadas por | o la palabra NINGUNO."
    if(-not $t){throw $hint}
    if($t -ieq 'NINGUNO'){return ,([string[]]@())}
    if($t.Contains('@(')){throw "$What`: @() no llega al script con -File. Para declarar que no hay ninguna, escribe NINGUNO."}
    if($t.Contains(',')){throw "$What`: una coma indica una lista de PowerShell, que -File recibe como un solo texto. $hint"}
    $items=@($t.Split('|')|ForEach-Object {$_.Trim()})
    foreach($item in $items){
        if(-not $item){throw "$What`: hay un tramo vacio entre separadores |."}
        if($item -ieq 'NINGUNO'){throw "$What`: NINGUNO no se combina con rutas."}
    }
    for($i=0;$i -lt $items.Count;$i++){for($j=$i+1;$j -lt $items.Count;$j++){if($items[$i].TrimEnd('\') -ieq $items[$j].TrimEnd('\')){throw "$What`: una ruta esta repetida."}}}
    return ,([string[]]$items)
}
# Dominios permitidos de Graph: separados por coma o por '|', en minusculas, sin repetidos y con el formato de
# Assert-GraphCredential; al menos uno. 'a.cl','b.cl' escrito en una consola de PowerShell llega con -File como a.cl,b.cl
# (Windows PowerShell 5.1) o como "a.cl","b.cl" (PowerShell 7): se quitan las comillas de cada tramo.
function ConvertFrom-DomainListArgument([string]$Text){
    $t=([string]$Text).Trim()
    if(-not $t -or $t.Contains('@(')){throw 'Indica los dominios permitidos separados por coma (por ejemplo defensorianinez.cl).'}
    $items=@($t.Split([char[]]@(',','|'))|ForEach-Object {$_.Trim().Trim([char[]]@('"',"'")).ToLowerInvariant()})
    foreach($d in $items){if($d -cnotmatch '^[a-z0-9-]+(\.[a-z0-9-]+)+$'){throw 'Dominio permitido con formato invalido (minusculas, sin @, separados por coma).'}}
    if(@($items|Select-Object -Unique).Count -ne $items.Count){throw 'Un dominio permitido esta repetido.'}
    return ,([string[]]$items)
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
    Set-OriginReleaseEnvironment $Settings
}

# --- Liberacion por analisis en origen (D17 de docs/PLAN_0_6_0.md, H04c; modo de emergencia) -----------------------
# config\liberacion-origen.json lo escribe Set-OriginRelease.ps1; SIGER lo lee y lo valida al arrancar (OriginRelease.java,
# que falla cerrado). La ruta solo la reciben los procesos que registran entregas: el servicio (Set-SigerServiceEnvironment)
# y el ciclo manual (Invoke-FormularioPull.ps1 sin -SoloComprobar). Nunca Migrate.ps1, Recover-Ti.ps1, Test-GraphMail.ps1
# ni la sonda, que usan solo Set-SigerEnvironment.
function Set-OriginReleaseEnvironment($Settings){
    $file=Join-Path $Settings.root 'config\liberacion-origen.json'
    $env:SIGER_ORIGIN_RELEASE_FILE=if(Test-Path -LiteralPath $file -PathType Leaf){$file}else{$null}
}
# Zona de Chile continental: id de Windows y, si no existe (PowerShell en Linux), el de IANA.
function Get-ChileTimeZone {
    foreach($id in @('Pacific SA Standard Time','America/Santiago')){try{return [TimeZoneInfo]::FindSystemTimeZoneById($id)}catch{}}
    throw 'No se encontro la zona horaria de Chile en este equipo.'
}
# Registro del modo: -Hasta es el ultimo dia (aaaa-mm-dd, Chile) en que rige, entre hoy y 90 dias despues de hoy; el
# termino ("until") es las 00:00 de Chile del dia siguiente, en UTC. Motivo de 1 a 500 caracteres en una sola linea.
# Las mismas reglas las aplica OriginRelease.java al arrancar (lista cerrada de campos).
function New-OriginReleaseRecord([string]$Hasta,[string]$Motivo,[string]$Account,[datetime]$NowUtc){
    if(([string]$Hasta) -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'){throw 'Indica -Hasta como aaaa-mm-dd (ultimo dia en que rige el modo, hora de Chile).'}
    $last=[datetime]::MinValue
    if(-not [datetime]::TryParseExact($Hasta,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::None,[ref]$last)){throw "Fecha -Hasta invalida ('$Hasta')."}
    $zone=Get-ChileTimeZone
    $now=[datetime]::SpecifyKind($NowUtc,[DateTimeKind]::Utc)
    $today=[TimeZoneInfo]::ConvertTimeFromUtc($now,$zone).Date
    if($last -lt $today){throw 'La fecha -Hasta ya paso: el modo debe regir al menos hasta el fin de hoy.'}
    if($last -gt $today.AddDays(90)){throw 'El modo de emergencia rige a lo mas 90 dias: indica una fecha -Hasta dentro de los 90 dias siguientes a hoy.'}
    $reason=([string]$Motivo).Trim()
    if($reason.Length -lt 1 -or $reason.Length -gt 500 -or $reason -match '[\x00-\x1F\x7F]'){throw 'Indica -Motivo (1 a 500 caracteres, una sola linea): decision del responsable y su constancia.'}
    $account=([string]$Account).Trim()
    if($account.Length -lt 1 -or $account.Length -gt 200 -or $account -match '[\x00-\x1F\x7F]'){throw 'Cuenta de Windows no valida.'}
    $until=[TimeZoneInfo]::ConvertTimeToUtc([datetime]::SpecifyKind($last.AddDays(1),[DateTimeKind]::Unspecified),$zone)
    return [PSCustomObject][ordered]@{kind='LIBERACION_ORIGEN';version=1;id=[guid]::NewGuid().ToString();activatedUtc=$now.ToString('o');until=$until.ToString('o');account=$account;reason=$reason}
}

# --- Eliminacion de la validacion cerrada (Remove-Validation.ps1, D12 de docs/PLAN_0_6_0.md) ------------------------
# Reglas puras con casos en Check-Scripts.ps1. Las rutas llegan ya normalizadas por el script (GetFullPath, sin '\' final).
function Get-Prop($Object,[string]$Name){
    if($null -ne $Object -and $Object.PSObject.Properties[$Name]){return $Object.$Name}
    return $null
}
function Test-PathInside([string]$Child,[string]$Parent){
    if(-not $Child -or -not $Parent){return $false}
    $c=$Child.TrimEnd('\');$p=$Parent.TrimEnd('\')
    return ($c -ieq $p -or $c.StartsWith($p+'\',[StringComparison]::OrdinalIgnoreCase))
}
# Un destino es una carpeta de D: (nunca la raiz de la unidad, otro volumen, una ruta UNC ni con '.' o '..'), que no esta
# dentro ni contiene ninguna ruta protegida ($Protected: nombre -> ruta; produccion, motores, Java, constancias, paquete)
# y que no se solapa con otro destino.
function Get-DeletionPathBlockers([string]$Target,$Protected,[string[]]$Others){
    $out=New-Object System.Collections.Generic.List[string]
    $t=([string]$Target).TrimEnd('\')
    if($t -cnotmatch '^[Dd]:(\\[^\\/:*?"<>|\x00-\x1F]+)+$' -or $t -match '(^|\\)\.{1,2}(\\|$)'){$out.Add('FUERA_DE_D');return $out.ToArray()}
    foreach($name in @($Protected.Keys)){
        $p=[string]$Protected[$name]
        if($p -and ((Test-PathInside $t $p) -or (Test-PathInside $p $t))){$out.Add('PROTEGIDA_'+$name)}
    }
    foreach($o in @($Others)){if($o -and ((Test-PathInside $t $o) -or (Test-PathInside $o $t))){$out.Add('SOLAPADA');break}}
    return $out.ToArray()
}
# Respaldo de Backup.ps1 de esta instancia: backup.json con el namespace, la raiz de origen y la base de la validacion.
function Test-BackupOfInstance($Meta,[string]$Root,[string]$Database){
    $namespace=[string](Get-Prop $Meta 'namespace');$source=[string](Get-Prop $Meta 'sourceRoot');$base=[string](Get-Prop $Meta 'database')
    return ($namespace -ceq 'SIGER-A-2026' -and $source -and $source.TrimEnd('\') -ieq $Root.TrimEnd('\') -and $base -ceq $Database)
}
# Carpeta de -Respaldos que no es un respaldo: cada hijo ($Children: {Path, Meta}; Meta = backup.json leido o $null) es un
# respaldo de esta instancia o un respaldo incompleto declarado ($Declared). Cualquier otro hijo (archivo, carpeta sin
# backup.json no declarada, respaldo de otra instancia) la rechaza.
function Test-BackupContainer($Children,[string]$Root,[string]$Database,[string[]]$Declared){
    foreach($child in @($Children)){
        $p=([string]$child.Path).TrimEnd('\')
        if(@($Declared|Where-Object {$_ -and ([string]$_).TrimEnd('\') -ieq $p}).Count -gt 0){continue}
        if(-not (Test-BackupOfInstance $child.Meta $Root $Database)){return $false}
    }
    return $true
}
# Respaldo abortado que declara el responsable (-RespaldosIncompletos de Remove-Validation.ps1): hijo directo de una
# carpeta de -Respaldos ($Containers: las que no son a su vez un respaldo), sin backup.json ni restore-result.json, sin
# enlaces y, si trae config-private\instance.json ($HasInstance; $Instance es $null si no se pudo leer), con la raiz y el
# ownerSid de la validacion. Sin ese archivo se acepta por la declaracion y la constancia lo dice. Vacia = aceptado.
function Get-IncompleteBackupBlockers([string]$Path,[string[]]$Containers,[bool]$HasBackupJson,[bool]$HasRestoreResult,[int]$Links,[bool]$HasInstance,$Instance,[string]$Root,[string]$OwnerSid){
    $out=New-Object System.Collections.Generic.List[string]
    $t=([string]$Path).TrimEnd('\');$i=$t.LastIndexOf('\');$parent=if($i -gt 0){$t.Substring(0,$i)}else{''}
    if(-not $parent -or @($Containers|Where-Object {$_ -and ([string]$_).TrimEnd('\') -ieq $parent}).Count -eq 0){$out.Add('NO_ES_HIJO_DE_RESPALDOS')}
    if($HasBackupJson){$out.Add('CON_BACKUP_JSON')}
    if($HasRestoreResult){$out.Add('CON_RESTORE_RESULT')}
    if($Links -ne 0){$out.Add('CON_ENLACES')}
    if($HasInstance){
        $instanceRoot=[string](Get-Prop $Instance 'root');$sid=[string](Get-Prop $Instance 'ownerSid')
        if(-not $instanceRoot -or $instanceRoot.TrimEnd('\') -ine $Root.TrimEnd('\') -or -not $sid -or $sid -cne $OwnerSid){$out.Add('INSTANCIA_AJENA')}
    }
    return $out.ToArray()
}
# Esquema de un ensayo de Restore-Check.ps1 (restore-result.json), o '' si no se reconoce. El script exige ademas que la
# carpeta de ese esquema exista en el mysql\data de la validacion: el ensayo se hizo sobre su motor y se borra con su raiz.
function Get-RestoreSchema($Result){
    $schema=[string](Get-Prop $Result 'schema')
    if($schema -cnotmatch '^siger_a_restore_[0-9]{14}_[0-9a-f]{6}$'){return ''}
    $namespace=Get-Prop $Result 'namespace'
    if($null -ne $namespace -and [string]$namespace -cne 'SIGER-A-RESTORE-CHECK'){return ''}
    if((Get-Prop $Result 'completed') -eq $true -and $null -eq $namespace){return ''}
    return $schema
}
# Origen e identidad ACK del Formulario de una instancia, sin el resto de las comprobaciones: la validacion puede seguir
# con formulario-pull.json en AUTOMATICO sin la autorizacion (plan B) y no pasaria Get-FormularioPullConfig. La identidad
# importada se lee con DPAPI: solo la cuenta duena la abre. $null si falta o no se reconoce.
function Get-FormularioIdentity($Settings){
    $config=Join-Path $Settings.root 'config'
    $file=Join-Path $config 'formulario-pull.json'
    if(-not(Test-Path -LiteralPath $file -PathType Leaf)){return $null}
    $pull=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    $origin=[string](Get-Prop $pull 'origin');$keyId=[string](Get-Prop $pull 'ackKeyId')
    $identity=Join-Path $config 'formulario-ack-identity.clixml'
    if(Test-Path -LiteralPath $identity -PathType Leaf){$keyId=[string](Get-Prop (Import-Clixml -LiteralPath $identity) 'KeyId')}
    if($origin -notmatch '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/?$' -or $keyId -notmatch '^[A-Za-z0-9_-]{1,64}$'){return $null}
    return [PSCustomObject]@{Origin=$origin;AckKeyId=$keyId}
}
# Bloqueos generales de la eliminacion (lista vacia = se puede eliminar). Remove-Validation.ps1 arma $Facts con:
#   Purpose, Root, Marker (objeto o $null), MarkerCopy (bool), OwnerMatch (bool), ServiceJson (bool), ServicesForRoot (int),
#   EngineActive (bool), InUse (int), Links (int), Production ({Purpose,Initialized,Root,OwnerMatch} o $null),
#   FormularioValidation ({Origin,AckKeyId} o $null), FormularioProduction ({Mode,Origin,AckKeyId} o $null si falta o
#   Get-FormularioPullConfig falla), GraphValidation (bool), GraphProduction (bool), PreviousOmitted (int).
# - PROPOSITO / SIN_MARCADOR / MARCADOR_SIN_TIPO / MARCADOR_OTRA_RAIZ / SIN_COPIA_DEL_CIERRE: solo una VALIDACION cerrada
#   por Close-Validation.ps1 de 0.6.0 (el marcador de 0.4.0 no tiene kind), con la copia identica en las constancias.
# - CUENTA: la cuenta de ownerSid (lee la identidad ACK y los secretos de produccion con su DPAPI).
# - SERVICIOS_REGISTRADOS / MOTOR_ACTIVO / ARCHIVOS_EN_USO / ENLACES: servicios retirados, motor detenido (sin pid y con
#   ibdata1 libre) y ningun archivo abierto por otro proceso; ningun enlace (no se sigue ninguno).
# - PRODUCCION: una produccion inicializada, de la misma cuenta y con otra raiz.
# - FORMULARIO_* y GRAPH_PRODUCCION: las unicas copias del Bearer, la identidad ACK y el secreto de Graph de NEW-SIGER
#   estan en la validacion y en sus respaldos (SMR queda fuera, D15): produccion debe tener el mismo origen y la misma
#   identidad ACK, en AUTOMATICO (D13 acreditada), y la credencial de Graph si la validacion la tiene.
# - PENDIENTE_ANTERIOR: un reintento incluye todo lo que la constancia anterior no dejo ELIMINADO y sigue existiendo.
function Get-ValidationDeletionBlockers($Facts){
    $f=$Facts;$out=New-Object System.Collections.Generic.List[string]
    if([string]$f.Purpose -cne 'VALIDACION'){$out.Add('PROPOSITO')}
    if($null -eq $f.Marker){$out.Add('SIN_MARCADOR')}else{
        $markerRoot=[string](Get-Prop $f.Marker 'root')
        if([string](Get-Prop $f.Marker 'kind') -cne 'CIERRE_VALIDACION'){$out.Add('MARCADOR_SIN_TIPO')}
        elseif(-not $markerRoot -or $markerRoot.TrimEnd('\') -ine ([string]$f.Root).TrimEnd('\')){$out.Add('MARCADOR_OTRA_RAIZ')}
        if(-not $f.MarkerCopy){$out.Add('SIN_COPIA_DEL_CIERRE')}
    }
    if(-not $f.OwnerMatch){$out.Add('CUENTA')}
    if($f.ServiceJson -or $f.ServicesForRoot -ne 0){$out.Add('SERVICIOS_REGISTRADOS')}
    if($f.EngineActive){$out.Add('MOTOR_ACTIVO')}
    if($f.InUse -ne 0){$out.Add('ARCHIVOS_EN_USO')}
    if($f.Links -ne 0){$out.Add('ENLACES')}
    $p=$f.Production
    if($null -eq $p -or -not $p.Initialized -or [string]$p.Purpose -cne 'PRODUCCION' -or -not $p.OwnerMatch -or (Test-PathInside $p.Root $f.Root) -or (Test-PathInside $f.Root $p.Root)){$out.Add('PRODUCCION')}
    $fv=$f.FormularioValidation;$fp=$f.FormularioProduction
    if($null -eq $fv){$out.Add('FORMULARIO_VALIDACION')}
    if($null -eq $fp){$out.Add('FORMULARIO_PRODUCCION')}else{
        if([string]$fp.Mode -cne 'AUTOMATICO'){$out.Add('FORMULARIO_NO_AUTOMATICO')}
        if($null -ne $fv){
            if(([string]$fp.Origin).TrimEnd('/') -ine ([string]$fv.Origin).TrimEnd('/')){$out.Add('FORMULARIO_ORIGEN_DISTINTO')}
            if([string]$fp.AckKeyId -cne [string]$fv.AckKeyId){$out.Add('FORMULARIO_ACK_DISTINTO')}
        }
    }
    if($f.GraphValidation -and -not $f.GraphProduction){$out.Add('GRAPH_PRODUCCION')}
    if($f.PreviousOmitted -ne 0){$out.Add('PENDIENTE_ANTERIOR')}
    return $out.ToArray()
}
# Recorre un arbol sin seguir enlaces: archivos y bytes, enlaces (en el arbol o en la ruta hasta la unidad) y archivos que
# no se pueden abrir en exclusiva (abiertos por el motor, Java u otro proceso, o sin acceso). No escribe nada.
function Get-TreeInventory([string]$Path){
    $r=[PSCustomObject]@{Files=[long]0;Bytes=[long]0;Links=0;InUse=0}
    $top=New-Object IO.DirectoryInfo($Path)
    $up=$top.Parent
    while($null -ne $up){if(($up.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){$r.Links++};$up=$up.Parent}
    if(($top.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){$r.Links++;return $r}
    $stack=New-Object 'System.Collections.Generic.Stack[IO.DirectoryInfo]';$stack.Push($top)
    while($stack.Count -gt 0){
        $dir=$stack.Pop();$entries=$null
        try{$entries=@($dir.GetFileSystemInfos())}catch{$r.InUse++;continue}
        foreach($e in $entries){
            if(($e.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){$r.Links++;continue}
            if($e -is [IO.DirectoryInfo]){$stack.Push($e);continue}
            $r.Files++;$r.Bytes+=$e.Length
            if(Test-FileInUse $e.FullName){$r.InUse++}
        }
    }
    return $r
}
function Test-FileInUse([string]$Path){
    try{$stream=[IO.File]::Open($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$stream.Dispose();return $false}catch{return $true}
}
# Constancia de la eliminacion: lista cerrada de campos (Check-Scripts.ps1 falla si se agrega uno). Solo rutas, cifras,
# fechas, cuenta, motivo, huellas y codigos: nunca nombres de archivos, contenido, datos ciudadanos ni secretos.
$script:DeletionRecordFields=@('kind','result','startedUtc','completedUtc','executedBy','reason','validation','closure','production','previous','elements','restoreSchemas','outOfScope','note')
$script:DeletionElementFields=@('type','path','files','bytes','evidence','result','remainingFiles')
function New-ValidationDeletionRecord($Validation,$Closure,[string]$ProductionRoot,$Previous,$Elements,[int]$RestoreSchemas,[string]$Reason,[string]$Account){
    $items=@(foreach($e in @($Elements)){[ordered]@{type=[string]$e.Type;path=[string]$e.Path;files=[long]$e.Files;bytes=[long]$e.Bytes;evidence=[string]$e.Evidence;result='PENDIENTE';remainingFiles=$null}})
    return [ordered]@{
        kind='ELIMINACION_VALIDACION';result='EN_CURSO';startedUtc=(Get-Date).ToUniversalTime().ToString('o');completedUtc=$null
        executedBy=$Account;reason=$Reason
        validation=[ordered]@{root=[string]$Validation.root;database=[string]$Validation.database;jarSha256=[string]$Validation.jarSha256}
        closure=[ordered]@{file=[string]$Closure.file;sha256=[string]$Closure.sha256;closedUtc=[string]$Closure.closedUtc;communications=$Closure.communications;confirmed=$Closure.confirmed}
        production=[ordered]@{root=$ProductionRoot}
        previous=$(if($Previous){[ordered]@{file=[string]$Previous.File;sha256=[string]$Previous.Sha256;result=[string](Get-Prop $Previous.Record 'result')}}else{$null})
        elements=$items;restoreSchemas=$RestoreSchemas
        outOfScope=@('Respaldos y snapshots de TI de la VM y de su almacenamiento: sujetos a su propia retencion.',
            'Copia de las entregas que conserva el Formulario (la retirada tras el ACK no esta habilitada).',
            'Datos de esta validacion restaurados en otro motor o copiados fuera de las rutas de esta constancia.')
        note='Eliminacion logica con el sistema de archivos; no es una eliminacion forense (no sobrescribe el disco).'}
}
function Save-DeletionRecord($Record,[string]$Path,[switch]$New){
    $bytes=(New-Object Text.UTF8Encoding($false)).GetBytes(($Record|ConvertTo-Json -Depth 8))
    $target=if($New){$Path}else{$Path+'.new'}
    $stream=[IO.File]::Open($target,$(if($New){[IO.FileMode]::CreateNew}else{[IO.FileMode]::Create}),[IO.FileAccess]::Write)
    try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush()}finally{$stream.Dispose()}
    # [NullString]: PowerShell convierte $null en '' al llamar a .NET, y Replace rechaza una ruta de copia vacia.
    if(-not $New){[IO.File]::Replace($target,$Path,[NullString]::Value)}
}
# Borra un arbol sin seguir enlaces (un enlace detiene la eliminacion). $Last: rutas relativas que se borran al final, en
# ese orden (en la raiz, instance.json y el marcador de cierre: mientras existan, el script puede repetirse).
function Remove-DeletionNode([IO.DirectoryInfo]$Directory,$Skip){
    foreach($e in @($Directory.GetFileSystemInfos())){
        if(($e.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'Enlace encontrado durante la eliminacion: se detiene sin seguirlo.'}
        if($e -is [IO.DirectoryInfo]){
            Remove-DeletionNode $e $Skip
            if(@($e.GetFileSystemInfos()).Count -eq 0){$e.Delete()}
        }elseif(-not $Skip.Contains($e.FullName)){$e.Attributes=[IO.FileAttributes]::Normal;$e.Delete()}
    }
}
function Remove-DeletionTree([string]$Path,[string[]]$Last){
    if(-not [IO.Directory]::Exists($Path)){return}
    $skip=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    $ordered=@(foreach($relative in @($Last|Where-Object {$_})){
        $full=$Path;foreach($part in ($relative -split '[\\/]')){$full=Join-Path $full $part}
        [void]$skip.Add($full);$full
    })
    Remove-DeletionNode (New-Object IO.DirectoryInfo($Path)) $skip
    foreach($file in $ordered){if([IO.File]::Exists($file)){[IO.File]::SetAttributes($file,[IO.FileAttributes]::Normal);[IO.File]::Delete($file)}}
    $skip.Clear();Remove-DeletionNode (New-Object IO.DirectoryInfo($Path)) $skip
    [IO.Directory]::Delete($Path,$false)
}
# Ejecuta la eliminacion: la constancia se escribe (archivo nuevo) antes de borrar y se completa tras cada elemento, en el
# orden de $Targets ({Path,Last}, el mismo de $Record.elements). Se detiene en el primer fallo: ese elemento queda FALLIDO
# con los archivos que le quedan y los siguientes NO_INTENTADO; el reintento se hace con lo que queda.
function Invoke-ValidationDeletion($Record,[string]$RecordPath,$Targets){
    Save-DeletionRecord $Record $RecordPath -New
    $failed=$false
    for($i=0;$i -lt @($Targets).Count;$i++){
        $item=$Record.elements[$i]
        if($failed){$item.result='NO_INTENTADO';continue}
        try{Remove-DeletionTree $Targets[$i].Path $Targets[$i].Last;$item.result='ELIMINADO'}
        catch{$failed=$true;$item.result='FALLIDO';$item.remainingFiles=$(if([IO.Directory]::Exists($Targets[$i].Path)){(Get-TreeInventory $Targets[$i].Path).Files}else{0})}
        Save-DeletionRecord $Record $RecordPath
    }
    $Record.result=if($failed){'INCOMPLETA'}else{'COMPLETA'}
    $Record.completedUtc=(Get-Date).ToUniversalTime().ToString('o')
    Save-DeletionRecord $Record $RecordPath
    return $Record
}
# Ultima constancia de eliminacion de esta raiz en la carpeta de constancias ({File,Sha256,Record} o $null). Una COMPLETA
# con la raiz aun presente es una incoherencia: falla cerrado.
function Get-PreviousDeletion([string]$Directory,[string]$Root){
    if(-not [IO.Directory]::Exists($Directory)){return $null}
    $found=$null
    foreach($file in @(Get-ChildItem -LiteralPath $Directory -Filter 'eliminacion-validacion-*.json' -File|Sort-Object Name)){
        $r=$null;try{$r=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json}catch{throw "Constancia ilegible en la carpeta de constancias ($($file.Name)). Revisala antes de continuar."}
        $root=[string](Get-Prop (Get-Prop $r 'validation') 'root')
        if([string](Get-Prop $r 'kind') -ceq 'ELIMINACION_VALIDACION' -and $root -and $root.TrimEnd('\') -ieq $Root.TrimEnd('\')){
            $found=[PSCustomObject]@{File=$file.Name;Sha256=(Get-SigerFileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant();Record=$r}
        }
    }
    if($found -and [string](Get-Prop $found.Record 'result') -ceq 'COMPLETA'){throw 'Ya hay una constancia COMPLETA de esta raiz y la raiz sigue presente. Revisa con el responsable.'}
    return $found
}
# Elementos de la constancia anterior que no quedaron ELIMINADO, siguen existiendo y no estan en esta ejecucion.
function Get-PreviousOmitted($Previous,[string[]]$Current,[scriptblock]$Exists){
    if($null -eq $Previous){return 0}
    $n=0
    foreach($e in @(Get-Prop $Previous.Record 'elements')){
        if([string](Get-Prop $e 'result') -ceq 'ELIMINADO'){continue}
        $p=([string](Get-Prop $e 'path')).TrimEnd('\')
        if(-not $p -or -not (& $Exists $p)){continue}
        if(@($Current|Where-Object {([string]$_).TrimEnd('\') -ieq $p}).Count -eq 0){$n++}
    }
    return $n
}
