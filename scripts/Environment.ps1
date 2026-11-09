# Dot-source desde Run/Migrate. Secretos DPAPI solo en el proceso; nunca argumentos Java.
. (Join-Path $PSScriptRoot 'Common.ps1')
function Set-SigerEnvironment($Settings,$Secrets,[switch]$Migration,[switch]$Seed) {
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
    # Claves publicas confiables del Formulario (opcional). Sin archivo, SIGER A rechaza todo original externo.
    $keys=Join-Path $Settings.root 'config\formulario-trusted-keys.json'
    $env:SIGER_FORMULARIO_TRUSTED_KEYS=if(Test-Path -LiteralPath $keys -PathType Leaf){$keys}else{$null}
}
function Clear-SigerEnvironment {
    foreach($key in @('SPRING_PROFILES_ACTIVE','SIGER_DB_URL','SIGER_DB_USER','SIGER_DB_PASSWORD','SIGER_DB_TRUSTSTORE','SIGER_DB_TRUST_PASSWORD','SIGER_DATA','SIGER_TEMP','SIGER_BIND','SIGER_PORT','SIGER_HTTPS','SIGER_KEYSTORE','SIGER_TLS_PASSWORD','SIGER_MIGRATE','SIGER_SEED_DEMO','SIGER_FORMULARIO_TRUSTED_KEYS')){[Environment]::SetEnvironmentVariable($key,$null,'Process')}
}
