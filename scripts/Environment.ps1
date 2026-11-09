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
    $env:SIGER_BIND='127.0.0.1';$env:SIGER_PORT=[string]$Settings.webPort;$env:SIGER_HTTPS='true'
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
    if($instanceMode -eq 'PRUEBAS'){
        $env:SIGER_ENVIRONMENT_LABEL="Entorno de pruebas $([char]0xB7) Datos sint$([char]0xE9)ticos";$env:SIGER_DOCUMENTS_SYNTHETIC_RELEASE='true'
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
function Clear-SigerEnvironment {
    foreach($key in @('SPRING_PROFILES_ACTIVE','SIGER_DB_URL','SIGER_DB_USER','SIGER_DB_PASSWORD','SIGER_DB_TRUSTSTORE','SIGER_DB_TRUST_PASSWORD','SIGER_DATA','SIGER_TEMP','SIGER_BIND','SIGER_PORT','SIGER_HTTPS','SIGER_KEYSTORE','SIGER_TLS_PASSWORD','SIGER_MIGRATE','SIGER_SEED_DEMO','SIGER_FORMULARIO_TRUSTED_KEYS','SIGER_FOLIO_PREFIX','SIGER_SCHEDULING','SIGER_FORMULARIO_PULL_ENABLED','SIGER_FORMULARIO_PULL_RUN_ONCE','SIGER_FORMULARIO_PULL_ORIGIN','SIGER_FORMULARIO_PULL_TOKEN','SIGER_FORMULARIO_PULL_TOKEN_FILE','SIGER_FORMULARIO_PULL_CA_FILE','SIGER_FORMULARIO_PULL_SCOPE','SIGER_FORMULARIO_ACK_KEY_ID','SIGER_FORMULARIO_ACK_PRIVATE_KEY_FILE','SIGER_FORMULARIO_ACK_PUBLIC_KEY_FILE','SIGER_FORMULARIO_PULL_AUTOMATIC','SIGER_FORMULARIO_PULL_SINGLE_CONSUMER','SIGER_FORMULARIO_ACK_PRIVATE_KEY','SIGER_FORMULARIO_ACK_PUBLIC_KEY','SIGER_MAIL_MODE','SIGER_GRAPH_TENANT','SIGER_GRAPH_CLIENT','SIGER_GRAPH_SENDER','SIGER_GRAPH_CLIENT_SECRET','SIGER_MAIL_ALLOWED_DOMAINS','SIGER_MAIL_LINK','SIGER_STOP_FILE','SIGER_LOG_FILE','SIGER_ENVIRONMENT_LABEL','SIGER_DOCUMENTS_SYNTHETIC_RELEASE','SIGER_AV_ENGINE','SIGER_AV_EXECUTABLE','SIGER_AV_DATABASE','SIGER_BOOTSTRAP_FILE')){[Environment]::SetEnvironmentVariable($key,$null,'Process')}
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
function Set-FormularioPullEnvironment($Pull,[switch]$RunOnce) {
    $env:SIGER_FORMULARIO_PULL_ENABLED='true'
    if($RunOnce){$env:SIGER_FORMULARIO_PULL_RUN_ONCE='true'}else{$env:SIGER_FORMULARIO_PULL_AUTOMATIC='true';$env:SIGER_FORMULARIO_PULL_SINGLE_CONSUMER='true'}
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
function Get-GraphMailConfig($Settings) {
    $config=Join-Path $Settings.root 'config'
    $file=Join-Path $config 'correo-graph.json'
    if(-not(Test-Path -LiteralPath $file -PathType Leaf)){return $null}
    $mail=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    $mode=if($mail.PSObject.Properties['mode']){([string]$mail.mode).ToUpperInvariant()}else{''}
    if($mode -eq 'DESACTIVADO'){return $null}
    if($mode -ne 'GRAPH'){throw 'mode en correo-graph.json debe ser GRAPH o DESACTIVADO.'}
    foreach($name in @('tenant','client','sender')){if(-not $mail.PSObject.Properties[$name] -or -not [string]$mail.$name){throw "Falta '$name' en correo-graph.json."}}
    $domains=@();if($mail.PSObject.Properties['allowedDomains']){$domains=@($mail.allowedDomains|ForEach-Object {[string]$_}|Where-Object {$_})}
    if($domains.Count -eq 0){throw 'correo-graph.json exige allowedDomains (dominios institucionales de los destinatarios).'}
    $secret=Join-Path $config 'graph-client-secret.clixml';Require-PrivateFile $secret
    $link=if($mail.PSObject.Properties['link']){[string]$mail.link}else{''}
    return [PSCustomObject]@{Tenant=[string]$mail.tenant;Client=[string]$mail.client;Sender=[string]$mail.sender;Domains=($domains -join ',');Link=$link;Secret=$secret}
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
}
