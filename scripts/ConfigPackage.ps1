# Paquete portable de la configuracion de una instancia (formato SIGER-CONFIG-1; gates H06r y H06s de
# docs/PLAN_0_6_0.md). Lo cargan Export-InstanceConfig.ps1 (cuenta duena) y Test-InstanceConfig.ps1 (otra cuenta).
# Contenido: todo config\ (salvo las carpetas rotacion-<UTC>\ que deja la importacion) y mysql\my.ini. Cada *.clixml se
# descifra con la DPAPI de la cuenta duena solo en memoria y se guarda en el paquete como sus valores; los .p12 y el
# resto de archivos van tal cual, con su SHA-256 (un .p12 de nombre conocido debe abrir con su clave de secrets.clixml;
# uno sin clave conocida viaja sin abrirse y la verificacion lo informa como SIN_CLAVE_CONOCIDA; H06r2). Todo el contenido va cifrado con una frase de paso: PBKDF2-SHA256 de al
# menos 300.000 iteraciones (96 bytes: clave AES, clave HMAC y clave de huellas), AES-256-CBC y HMAC-SHA256 sobre
# formato, iteraciones, sal, IV y cifrado (las primitivas de Import-SecretPackage.ps1). Cada valor de un clixml lleva
# su huella (HMAC-SHA256 con la clave de huellas). Ninguna funcion imprime secretos. Reglas puras con casos y
# mutaciones en Check-Scripts.ps1.
. (Join-Path $PSScriptRoot 'Common.ps1')
$script:ConfigFormat='SIGER-CONFIG-1'
$script:ConfigMinIterations=300000
$script:ConfigMaxIterations=5000000
$script:ConfigMaxFileBytes=1MB
$script:ConfigMaxTotalBytes=8MB
# Cuentas del MySQL propio por secreto de secrets.clixml (Init-Server.ps1). root@localhost no se usa por TCP.
$script:ConfigMySqlAccounts=[ordered]@{Owner='siger_a_owner';App='siger_a_app';Migrator='siger_a_migrator';Backup='siger_a_backup'}
$script:ConfigStamp='yyyyMMddTHHmmssZ'

function Get-PackageField($Object,[string]$Name){
    if($null -eq $Object){return $null}
    $p=$Object.PSObject.Properties[$Name]
    if($p){return $p.Value}
    return $null
}
function ConvertTo-PackageStamp([datetime]$Value){return $Value.ToUniversalTime().ToString($script:ConfigStamp,[Globalization.CultureInfo]::InvariantCulture)}
function Get-Sha256Hex([byte[]]$Bytes){
    $sha=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
}
function ConvertTo-Utf8Base64([string]$Text){return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Text))}
function ConvertFrom-Utf8Base64([string]$Text){return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Text))}
function Test-BytesEqual([byte[]]$A,[byte[]]$B){
    # Comparacion en tiempo constante (como Import-SecretPackage.ps1).
    if($null -eq $A -or $null -eq $B){return $false}
    $diff=$A.Length -bxor $B.Length
    for($i=0;$i -lt [Math]::Min($A.Length,$B.Length);$i++){$diff=$diff -bor ($A[$i] -bxor $B[$i])}
    return ($diff -eq 0)
}
function Clear-Bytes($Bytes){if($Bytes -is [byte[]]){[Array]::Clear($Bytes,0,$Bytes.Length)}}

# --- Reglas de los elementos ---------------------------------------------------------------------------------------
# Ruta relativa admitida: config\<...> (sin '.', '..', unidad ni caracteres no validos, y nunca dentro de una carpeta
# rotacion-<UTC>\ de primer nivel) o exactamente mysql\my.ini.
function Test-ConfigItemPath([string]$Rel){
    if([string]::IsNullOrEmpty($Rel) -or $Rel.Length -gt 240){return $false}
    if($Rel -ceq 'mysql\my.ini'){return $true}
    if(-not $Rel.StartsWith('config\',[StringComparison]::Ordinal)){return $false}
    $parts=$Rel.Substring(7).Split('\')
    if($parts.Count -lt 1){return $false}
    foreach($p in $parts){
        if($p -eq '' -or $p -eq '.' -or $p -eq '..' -or $p -match '[\x00-\x1F/:*?"<>|]' -or $p.EndsWith(' ') -or $p.EndsWith('.')){return $false}
    }
    if($parts.Count -gt 1 -and $parts[0] -match '^rotacion-'){return $false}
    return $true
}
function Get-ConfigItemKind([string]$Rel){
    $ext=[IO.Path]::GetExtension($Rel).ToLowerInvariant()
    if($ext -eq '.clixml'){return 'CLIXML'}
    if($ext -eq '.p12' -or $ext -eq '.pfx'){return 'P12'}
    return 'ARCHIVO'
}
# Clave de cada almacen, por una regla cerrada sobre el nombre (Init-Server.ps1 e Import-Integrations.ps1 -HttpsPfx,
# que conserva la clave Tls en el anterior). Solo estos almacenes los usa la instancia (rutas fijas en Environment.ps1).
function Get-P12PasswordName([string]$Rel){
    if($Rel -ceq 'config\https.p12' -or $Rel -cmatch '^config\\https-anterior-[0-9]{8}-[0-9]{6}\.p12$'){return 'Tls'}
    if($Rel -ceq 'config\mysql-trust.p12'){return 'Trust'}
    return ''
}
# Estado del nombre de un almacen (H06r2): CONOCIDO (tiene clave por la regla de arriba y debe abrir con ella), MAYUSCULAS
# (coincide con un nombre conocido salvo mayusculas: es un problema y bloquea la exportacion, nunca se trata como
# desconocido) o SIN_CLAVE (ningun nombre conocido: ninguna configuracion lo usa y viaja tal cual, con su SHA-256, sin
# abrirlo ni necesitar su contrasena; por ejemplo config\https-red-candidato.p12 de la validacion de la VM).
function Get-P12NameState([string]$Rel){
    if(Get-P12PasswordName $Rel){return 'CONOCIDO'}
    if(Get-P12PasswordName $Rel.ToLowerInvariant()){return 'MAYUSCULAS'}
    return 'SIN_CLAVE'
}
# Destino del paquete: archivo nuevo .sigerpkg en una carpeta existente, fuera de la raiz de la instancia.
function Get-ExportDestinationBlockers([string]$Destination,[string]$Root,[bool]$Exists,[bool]$ParentExists){
    $codes=@()
    if(-not $Destination.EndsWith('.sigerpkg',[StringComparison]::OrdinalIgnoreCase)){$codes+='EXTENSION'}
    if($Exists){$codes+='EXISTE'}
    if(-not $ParentExists){$codes+='SIN_CARPETA'}
    $r=$Root.TrimEnd('\')+'\'
    if($Destination.StartsWith($r,[StringComparison]::OrdinalIgnoreCase)){$codes+='DENTRO_DE_LA_RAIZ'}
    return $codes
}
# La verificacion se hace como OTRA cuenta: acredita que el paquete no depende de la DPAPI de la cuenta duena.
function Test-VerifierAccount([string]$OwnerSid,[string]$CurrentSid){
    return (-not [string]::IsNullOrEmpty($OwnerSid) -and -not [string]::IsNullOrEmpty($CurrentSid) -and $OwnerSid -cne $CurrentSid)
}

# --- Inventario de la instancia ------------------------------------------------------------------------------------
function Add-ConfigTreeFiles([string]$Directory,[string]$Prefix,[bool]$Top,$Out,$Omitted){
    foreach($e in ([IO.DirectoryInfo]$Directory).GetFileSystemInfos()){
        if($e.Attributes -band [IO.FileAttributes]::ReparsePoint){throw "config\ contiene un enlace ($Prefix$($e.Name)). No se exporta nada."}
        if($e -is [IO.DirectoryInfo]){
            if($Top -and $e.Name -cmatch '^rotacion-[0-9]{8}T[0-9]{6}Z$'){$Omitted.Add($Prefix+$e.Name);continue}
            Add-ConfigTreeFiles $e.FullName ($Prefix+$e.Name+'\') $false $Out $Omitted
        }else{$Out.Add([PSCustomObject]@{Rel=$Prefix+$e.Name;Full=$e.FullName;Size=[long]$e.Length;Kind=(Get-ConfigItemKind ($Prefix+$e.Name))})}
    }
}
function Get-InstanceConfigInventory([string]$Root){
    $Root=$Root.TrimEnd('\')
    $config=Join-Path $Root 'config'
    if(-not [IO.Directory]::Exists($config)){throw 'La instancia no tiene config\.'}
    if(([IO.DirectoryInfo]$config).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'config\ es un enlace. No se exporta nada.'}
    $items=[System.Collections.Generic.List[object]]::new();$omitted=[System.Collections.Generic.List[string]]::new()
    Add-ConfigTreeFiles $config 'config\' $true $items $omitted
    $ini=Join-Path $Root 'mysql\my.ini'
    if([IO.File]::Exists($ini)){
        $f=[IO.FileInfo]$ini
        if($f.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'mysql\my.ini es un enlace. No se exporta nada.'}
        $items.Add([PSCustomObject]@{Rel='mysql\my.ini';Full=$f.FullName;Size=[long]$f.Length;Kind='ARCHIVO'})
    }
    $sorted=@($items|Sort-Object -Property @{Expression={$_.Rel.ToLowerInvariant()}})
    $total=[long]0;$problems=@()
    foreach($i in $sorted){
        $total+=$i.Size
        if(-not (Test-ConfigItemPath $i.Rel)){$problems+="NOMBRE_NO_ADMITIDO:$($i.Rel)"}
        if($i.Size -gt $script:ConfigMaxFileBytes){$problems+="DEMASIADO_GRANDE:$($i.Rel)"}
        if($i.Kind -ceq 'P12' -and (Get-P12NameState $i.Rel) -ceq 'MAYUSCULAS'){$problems+="P12_MAYUSCULAS:$($i.Rel)"}
    }
    if($total -gt $script:ConfigMaxTotalBytes){$problems+='TOTAL_DEMASIADO_GRANDE'}
    if(-not @($sorted|Where-Object {$_.Rel -ceq 'config\secrets.clixml'})){$problems+='SIN_SECRETS_CLIXML'}
    if(-not @($sorted|Where-Object {$_.Rel -ceq 'config\instance.json'})){$problems+='SIN_INSTANCE_JSON'}
    return [PSCustomObject]@{Items=$sorted;Omitted=@($omitted);Problems=$problems;Total=$total}
}

# --- Clixml ------------------------------------------------------------------------------------------------------------
# Formas admitidas (las que escriben los scripts de SIGER con Export-Clixml): una SecureString, o un objeto
# PSCustomObject con propiedades de texto o SecureString. Cualquier otra forma rechaza el archivo (la exportacion no
# sigue). Devuelve los valores en claro, solo en memoria.
function ConvertTo-ClixmlModel($Value,[string]$Rel){
    if($Value -is [Security.SecureString]){return [PSCustomObject]@{Shape='SECRETO';Properties=@([PSCustomObject]@{Name='';Type='SECRETO';Value=(Secret-Text $Value)})}}
    if($Value -is [Array] -or $null -eq $Value -or -not ($Value -is [psobject])){throw "Forma no admitida en $Rel. No se exporta nada."}
    $names=@($Value.PSObject.TypeNames)
    if($names.Count -lt 1 -or $names[0] -cne 'Deserialized.System.Management.Automation.PSCustomObject'){throw "Forma no admitida en $Rel. No se exporta nada."}
    $props=@(foreach($p in $Value.PSObject.Properties){
        if([string]$p.MemberType -cne 'NoteProperty'){throw "Forma no admitida en $Rel. No se exporta nada."}
        if($p.Value -is [Security.SecureString]){[PSCustomObject]@{Name=$p.Name;Type='SECRETO';Value=(Secret-Text $p.Value)}}
        elseif($p.Value -is [string]){[PSCustomObject]@{Name=$p.Name;Type='TEXTO';Value=[string]$p.Value}}
        else{throw "Forma no admitida en $Rel (propiedad $($p.Name)). No se exporta nada."}
    })
    if($props.Count -lt 1){throw "Forma no admitida en $Rel. No se exporta nada."}
    return [PSCustomObject]@{Shape='OBJETO';Properties=$props}
}
function Get-SecretFingerprint([byte[]]$Key,[string]$Rel,[string]$Name,[string]$Type,[string]$Value){
    $h=New-Object Security.Cryptography.HMACSHA256(,$Key)
    try{return ([BitConverter]::ToString($h.ComputeHash([Text.Encoding]::UTF8.GetBytes("$Rel`n$Name`n$Type`n$Value")))).Replace('-','').ToLowerInvariant()}finally{$h.Dispose()}
}

# --- .p12 ----------------------------------------------------------------------------------------------------------------
# Certificados de un almacen abierto con su clave, en memoria (EphemeralKeySet: ninguna clave privada se escribe en el
# perfil). Lanza si no abre.
function Get-P12Certificates([byte[]]$Bytes,[string]$Password){
    $c=New-Object Security.Cryptography.X509Certificates.X509Certificate2Collection
    $c.Import($Bytes,$Password,[Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet)
    $list=@(foreach($x in $c){
        [PSCustomObject]@{thumbprint=$x.Thumbprint.ToUpperInvariant();notBefore=(ConvertTo-PackageStamp $x.NotBefore);notAfter=(ConvertTo-PackageStamp $x.NotAfter);privateKey=[bool]$x.HasPrivateKey}
        $x.Reset()
    })
    if($list.Count -lt 1){throw 'El almacen no contiene certificados.'}
    return @($list|Sort-Object thumbprint)
}
function Get-CertificateListKey($List){return (@($List|ForEach-Object {"$($_.thumbprint)|$($_.notBefore)|$($_.notAfter)|$([bool]$_.privateKey)"}) -join ';')}

# --- Cifrado -------------------------------------------------------------------------------------------------------------
function New-ConfigCrypto([string]$Passphrase,[byte[]]$Salt,[int]$Iterations){
    if(-not $Salt){$Salt=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($Salt)}finally{$rng.Dispose()}}
    $kdf=New-Object Security.Cryptography.Rfc2898DeriveBytes($Passphrase,$Salt,$Iterations,[Security.Cryptography.HashAlgorithmName]::SHA256)
    try{$k=$kdf.GetBytes(96)}finally{$kdf.Dispose()}
    try{return [PSCustomObject]@{Salt=$Salt;Iterations=$Iterations;Enc=[byte[]]$k[0..31];Mac=[byte[]]$k[32..63];Fp=[byte[]]$k[64..95]}}finally{Clear-Bytes $k}
}
function Clear-ConfigCrypto($Crypto){if($Crypto){Clear-Bytes $Crypto.Enc;Clear-Bytes $Crypto.Mac;Clear-Bytes $Crypto.Fp}}
# El HMAC cubre formato, iteraciones y sal tal como estan escritos en la envoltura, ademas del IV y el cifrado.
function Get-ConfigMac($Crypto,[int]$Iterations,[byte[]]$Salt,[byte[]]$Iv,[byte[]]$Cipher){
    $h=New-Object Security.Cryptography.HMACSHA256(,$Crypto.Mac)
    try{return $h.ComputeHash([byte[]]([Text.Encoding]::ASCII.GetBytes("$($script:ConfigFormat)|$Iterations|")+$Salt+$Iv+$Cipher))}finally{$h.Dispose()}
}
function Protect-ConfigPayload($Crypto,[string]$Json){
    $plain=[Text.Encoding]::UTF8.GetBytes($Json)
    $aes=[Security.Cryptography.Aes]::Create()
    try{
        $aes.Key=$Crypto.Enc;$aes.Mode='CBC';$aes.Padding='PKCS7';$aes.GenerateIV()
        $enc=$aes.CreateEncryptor();try{$cipher=$enc.TransformFinalBlock($plain,0,$plain.Length)}finally{$enc.Dispose()}
        $mac=Get-ConfigMac $Crypto $Crypto.Iterations $Crypto.Salt $aes.IV $cipher
        $outer=[ordered]@{format=$script:ConfigFormat;iterations=$Crypto.Iterations;salt=[Convert]::ToBase64String($Crypto.Salt);iv=[Convert]::ToBase64String($aes.IV);data=[Convert]::ToBase64String($cipher);mac=[Convert]::ToBase64String($mac)}
        return ($outer|ConvertTo-Json -Compress)
    }finally{$aes.Dispose();Clear-Bytes $plain}
}
# Lee la envoltura y valida forma, formato e iteraciones antes de derivar. Errores: FORMATO, ITERACIONES, INTEGRIDAD.
function Read-ConfigEnvelope([string]$Text){
    try{$o=$Text|ConvertFrom-Json}catch{throw 'FORMATO: el archivo no es un paquete de configuracion de SIGER.'}
    if($o -is [Array] -or [string](Get-PackageField $o 'format') -cne $script:ConfigFormat){throw 'FORMATO: el archivo no es un paquete de configuracion de SIGER (SIGER-CONFIG-1).'}
    $names=@($o.PSObject.Properties|ForEach-Object {$_.Name}|Sort-Object)
    if(($names -join ',') -cne 'data,format,iterations,iv,mac,salt'){throw 'FORMATO: la envoltura del paquete tiene otros campos.'}
    $it=Get-PackageField $o 'iterations'
    if(-not ($it -is [int] -or $it -is [long]) -or [long]$it -lt $script:ConfigMinIterations -or [long]$it -gt $script:ConfigMaxIterations){throw "ITERACIONES: el paquete debe usar entre $($script:ConfigMinIterations) y $($script:ConfigMaxIterations) iteraciones."}
    try{$salt=[Convert]::FromBase64String([string]$o.salt);$iv=[Convert]::FromBase64String([string]$o.iv);$data=[Convert]::FromBase64String([string]$o.data);$mac=[Convert]::FromBase64String([string]$o.mac)}catch{throw 'FORMATO: el paquete tiene campos que no son Base64.'}
    if($salt.Length -ne 32 -or $iv.Length -ne 16 -or $mac.Length -ne 32 -or $data.Length -lt 16 -or ($data.Length % 16) -ne 0){throw 'FORMATO: el paquete tiene campos de largo inesperado.'}
    return [PSCustomObject]@{Iterations=[int]$it;Salt=$salt;Iv=$iv;Data=$data;Mac=$mac}
}
function Unprotect-ConfigEnvelope($Envelope,$Crypto){
    if(-not (Test-BytesEqual (Get-ConfigMac $Crypto $Envelope.Iterations $Envelope.Salt $Envelope.Iv $Envelope.Data) $Envelope.Mac)){throw 'INTEGRIDAD: frase de paso incorrecta o paquete alterado.'}
    $aes=[Security.Cryptography.Aes]::Create();$plain=$null
    try{
        $aes.Key=$Crypto.Enc;$aes.IV=$Envelope.Iv;$aes.Mode='CBC';$aes.Padding='PKCS7'
        $dec=$aes.CreateDecryptor();try{$plain=$dec.TransformFinalBlock($Envelope.Data,0,$Envelope.Data.Length)}finally{$dec.Dispose()}
        return [Text.Encoding]::UTF8.GetString($plain)
    }finally{$aes.Dispose();Clear-Bytes $plain}
}
function Open-ConfigPackage([string]$Text,[string]$Passphrase){
    $envelope=Read-ConfigEnvelope $Text
    $crypto=New-ConfigCrypto $Passphrase $envelope.Salt $envelope.Iterations
    try{$json=Unprotect-ConfigEnvelope $envelope $crypto}catch{Clear-ConfigCrypto $crypto;throw}
    return [PSCustomObject]@{Crypto=$crypto;Json=$json}
}
function Read-ConfigPassphrase([switch]$Twice){
    $a=Secret-Text (Read-Host 'Frase de paso del paquete (minimo 20 caracteres; se escribe a mano, no se pega)' -AsSecureString)
    if($Twice){
        if($a.Length -lt 20){throw 'La frase de paso debe tener al menos 20 caracteres. No se escribio nada.'}
        if($a -cne (Secret-Text (Read-Host 'Repite la frase de paso' -AsSecureString))){throw 'Las frases de paso no coinciden. No se escribio nada.'}
    }
    return $a
}

# --- Contenido -----------------------------------------------------------------------------------------------------------
# Construye el contenido (en claro, solo en memoria) como la cuenta duena. secrets.clixml se lee primero: da las claves
# de los .p12, y cada .p12 de nombre conocido debe abrir con la suya antes de seguir.
function New-InstanceConfigPayload($Settings,$Inventory,[byte[]]$FpKey){
    $models=@{}
    foreach($i in @($Inventory.Items|Where-Object {$_.Kind -ceq 'CLIXML'})){
        try{$value=Import-Clixml -LiteralPath $i.Full}catch{throw "Esta cuenta no descifra $($i.Rel) (DPAPI de otra identidad o archivo danado). No se exporta nada."}
        $models[$i.Rel]=ConvertTo-ClixmlModel $value $i.Rel
        $value=$null
    }
    $secrets=$models['config\secrets.clixml']
    $items=@(foreach($i in $Inventory.Items){
        $bytes=[IO.File]::ReadAllBytes($i.Full)
        $item=[ordered]@{path=$i.Rel;kind=$i.Kind;size=[long]$bytes.Length;sha256=(Get-Sha256Hex $bytes)}
        if($i.Kind -ceq 'CLIXML'){
            $m=$models[$i.Rel];$item.shape=$m.Shape
            $item.properties=@(foreach($p in $m.Properties){[ordered]@{name=$p.Name;type=$p.Type;value=(ConvertTo-Utf8Base64 $p.Value);fingerprint=(Get-SecretFingerprint $FpKey $i.Rel $p.Name $p.Type $p.Value)}})
        }else{
            $item.content=[Convert]::ToBase64String($bytes)
            # Un almacen SIN_CLAVE va solo con sus bytes y su SHA-256: sin password ni certificates, y no se abre.
            if($i.Kind -ceq 'P12' -and (Get-P12NameState $i.Rel) -cne 'SIN_CLAVE'){
                $name=Get-P12PasswordName $i.Rel
                $secret=@($secrets.Properties|Where-Object {$_.Name -ceq $name -and $_.Type -ceq 'SECRETO'})
                if(-not $name -or $secret.Count -ne 1){throw "No hay clave conocida para $($i.Rel). No se exporta nada."}
                try{$certs=@(Get-P12Certificates $bytes $secret[0].Value)}catch{throw "$($i.Rel) no abre con su clave de secrets.clixml ($name). No se exporta nada."}
                $item.password=$name;$item.certificates=@($certs)
            }
        }
        Clear-Bytes $bytes
        $item
    })
    $me=[Security.Principal.WindowsIdentity]::GetCurrent()
    return [ordered]@{format=$script:ConfigFormat;createdUtc=(ConvertTo-PackageStamp (Get-Date));computer=$env:COMPUTERNAME;account=$me.Name;accountSid=$me.User.Value
        ownerSid=[string](Get-PackageField $Settings 'ownerSid');root=[string]$Settings.root;namespace=[string]$Settings.namespace;database=[string](Get-PackageField $Settings 'database');omitted=@($Inventory.Omitted);items=$items}
}
function Get-PackageProperty($Item,[string]$Name){
    return @(@(Get-PackageField $Item 'properties')|Where-Object {$null -ne $_ -and [string](Get-PackageField $_ 'name') -ceq $Name})
}
# Secreto en claro de un clixml del paquete (solo en memoria), o $null.
function Get-PackageSecret($Payload,[string]$Rel,[string]$Name){
    $item=@(@($Payload.items)|Where-Object {[string](Get-PackageField $_ 'path') -ceq $Rel})
    if($item.Count -ne 1){return $null}
    $p=@(Get-PackageProperty $item[0] $Name)
    if($p.Count -ne 1){return $null}
    try{return (ConvertFrom-Utf8Base64 ([string]$p[0].value))}catch{return $null}
}
function Get-PackageFileBytes($Item){return [Convert]::FromBase64String([string](Get-PackageField $Item 'content'))}
function Get-PackageSettings($Payload){
    $item=@(@($Payload.items)|Where-Object {[string](Get-PackageField $_ 'path') -ceq 'config\instance.json'})
    if($item.Count -ne 1){return $null}
    try{return ([Text.Encoding]::UTF8.GetString((Get-PackageFileBytes $item[0])).TrimStart([char]0xFEFF)|ConvertFrom-Json)}catch{return $null}
}

# Revision del contenido descifrado (regla pura salvo abrir los .p12 en memoria): estructura, rutas, SHA-256 de cada
# archivo, huella de cada valor de clixml, cada .p12 de nombre conocido abre con su clave del paquete y da los
# certificados registrados (uno sin clave conocida no trae clave ni certificados; uno con mayusculas cambiadas es hallazgo).
# Devuelve los hallazgos (vacio = correcto) y las cifras.
function Test-ConfigPayload($Payload,[byte[]]$FpKey){
    $f=@();$files=0;$secrets=0;$p12=@()
    if([string](Get-PackageField $Payload 'format') -cne $script:ConfigFormat){return [PSCustomObject]@{Findings=@('CONTENIDO_FORMATO');Files=0;Secrets=0;P12=@()}}
    foreach($n in @('createdUtc','computer','account','accountSid','ownerSid','root','namespace')){if([string]::IsNullOrEmpty([string](Get-PackageField $Payload $n))){$f+="CABECERA:$n"}}
    if([string](Get-PackageField $Payload 'namespace') -cne 'SIGER-A-2026'){$f+='CABECERA:namespace'}
    $items=@(Get-PackageField $Payload 'items')
    if($items.Count -lt 1){$f+='SIN_ELEMENTOS'}
    $seen=@{}
    foreach($i in $items){
        $rel=[string](Get-PackageField $i 'path');$kind=[string](Get-PackageField $i 'kind')
        if(-not (Test-ConfigItemPath $rel)){$f+="RUTA:$rel";continue}
        if($seen.ContainsKey($rel.ToLowerInvariant())){$f+="REPETIDO:$rel";continue};$seen[$rel.ToLowerInvariant()]=$true
        if($kind -cne (Get-ConfigItemKind $rel)){$f+="TIPO:$rel";continue}
        $files++
        if($kind -ceq 'CLIXML'){
            $shape=[string](Get-PackageField $i 'shape');$props=@(Get-PackageField $i 'properties')
            if(@('SECRETO','OBJETO') -cnotcontains $shape -or $props.Count -lt 1 -or ($shape -ceq 'SECRETO' -and ($props.Count -ne 1 -or [string](Get-PackageField $props[0] 'name') -cne '' -or [string](Get-PackageField $props[0] 'type') -cne 'SECRETO'))){$f+="FORMA:$rel";continue}
            $pn=@{}
            foreach($p in $props){
                $name=[string](Get-PackageField $p 'name');$type=[string](Get-PackageField $p 'type')
                if(@('SECRETO','TEXTO') -cnotcontains $type -or $pn.ContainsKey($name) -or ($shape -ceq 'OBJETO' -and $name -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,63}$')){$f+="FORMA:$rel";continue};$pn[$name]=$true
                try{$value=ConvertFrom-Utf8Base64 ([string](Get-PackageField $p 'value'))}catch{$f+="VALOR:$rel#$name";continue}
                if((Get-SecretFingerprint $FpKey $rel $name $type $value) -cne [string](Get-PackageField $p 'fingerprint')){$f+="HUELLA:$rel#$name"}
                if($type -ceq 'SECRETO'){$secrets++}
                $value=$null
            }
            if(-not ([string](Get-PackageField $i 'sha256') -cmatch '^[0-9a-f]{64}$')){$f+="SHA256:$rel"}
            continue
        }
        try{$bytes=Get-PackageFileBytes $i}catch{$f+="CONTENIDO:$rel";continue}
        if([long]$bytes.Length -ne [long](Get-PackageField $i 'size')){$f+="TAMANO:$rel"}
        if((Get-Sha256Hex $bytes) -cne [string](Get-PackageField $i 'sha256')){$f+="SHA256:$rel"}
        if($kind -ceq 'P12'){
            $name=Get-P12PasswordName $rel;$nameState=Get-P12NameState $rel
            $result=[PSCustomObject]@{Path=$rel;NameState=$nameState;Opens=$false;Count=0;NotAfter='';Matches=$false}
            if($nameState -ceq 'SIN_CLAVE'){
                # Sin clave conocida: solo tamano y SHA-256 (arriba). No puede traer clave ni certificados.
                if($null -ne $i.PSObject.Properties['password'] -or $null -ne $i.PSObject.Properties['certificates']){$f+="P12_CLAVE:$rel"}
            }
            elseif($nameState -ceq 'MAYUSCULAS'){$f+="P12_MAYUSCULAS:$rel"}
            elseif([string](Get-PackageField $i 'password') -cne $name){$f+="P12_CLAVE:$rel"}
            else{
                $pass=Get-PackageSecret $Payload 'config\secrets.clixml' $name
                if($null -eq $pass){$f+="P12_SIN_SECRETO:$rel"}
                else{
                    try{$certs=@(Get-P12Certificates $bytes $pass);$result.Count=$certs.Count;$result.NotAfter=[string](@($certs|ForEach-Object {$_.notAfter}|Sort-Object)[0]);$result.Opens=$true}catch{$f+="P12_NO_ABRE:$rel"}
                    if($result.Opens){
                        $result.Matches=((Get-CertificateListKey $certs) -ceq (Get-CertificateListKey @(@(Get-PackageField $i 'certificates')|Sort-Object thumbprint)))
                        if(-not $result.Matches){$f+="P12_CERTIFICADOS:$rel"}
                    }
                }
                $pass=$null
            }
            $p12+=$result
        }
        Clear-Bytes $bytes
    }
    if(-not $seen.ContainsKey('config\secrets.clixml')){$f+='SIN_SECRETS_CLIXML'}
    if(-not $seen.ContainsKey('config\instance.json')){$f+='SIN_INSTANCE_JSON'}
    return [PSCustomObject]@{Findings=$f;Files=$files;Secrets=$secrets;P12=$p12}
}

# Comparacion con la instancia en disco, solo lectura y sin cambiar permisos: por elemento IGUAL, DISTINTO, FALTA o
# NO_LEGIBLE (lo que el administrador no pueda leer se informa, no se fuerza); EXTRA = en config\ pero no en el
# paquete. En los .p12 legibles compara tambien los certificados del archivo del disco abierto con la clave del paquete.
function Compare-ConfigWithDisk($Payload,[string]$Root){
    $Root=$Root.TrimEnd('\')
    $res=[PSCustomObject]@{Equal=@();Different=@();Missing=@();Unreadable=@();Extra=@();ListUnreadable=$false;P12=@()}
    $known=@{}
    foreach($i in @($Payload.items)){
        $rel=[string](Get-PackageField $i 'path');$known[$rel.ToLowerInvariant()]=$true
        if(-not (Test-ConfigItemPath $rel)){continue}
        $full=Join-Path $Root $rel
        $exists=$false;try{$exists=[IO.File]::Exists($full)}catch{$exists=$false}
        if(-not $exists){
            $dirOk=$true;try{$null=[IO.Directory]::GetFiles((Split-Path $full -Parent))}catch{$dirOk=$false}
            if($dirOk){$res.Missing+=$rel}else{$res.Unreadable+=$rel}
            continue
        }
        try{$bytes=[IO.File]::ReadAllBytes($full)}catch{$res.Unreadable+=$rel;continue}
        $equal=((Get-Sha256Hex $bytes) -ceq [string](Get-PackageField $i 'sha256'))
        if($equal){$res.Equal+=$rel}else{$res.Different+=$rel}
        if([string](Get-PackageField $i 'kind') -ceq 'P12'){
            # Un almacen sin clave conocida no se abre: se compara solo por SHA-256 (IGUAL o DISTINTO, arriba).
            if((Get-P12NameState $rel) -ceq 'SIN_CLAVE'){$state='SIN_CLAVE_CONOCIDA'}
            else{
                $pass=Get-PackageSecret $Payload 'config\secrets.clixml' ([string](Get-PackageField $i 'password'))
                $state='NO_ABRE';try{if($null -ne $pass){$disk=@(Get-P12Certificates $bytes $pass);$state=if((Get-CertificateListKey $disk) -ceq (Get-CertificateListKey @(@(Get-PackageField $i 'certificates')|Sort-Object thumbprint))){'IGUALES'}else{'DISTINTOS'}}}catch{$state='NO_ABRE'}
                $pass=$null
            }
            $res.P12+=[PSCustomObject]@{Path=$rel;State=$state;Equal=$equal}
        }
        Clear-Bytes $bytes
    }
    $config=Join-Path $Root 'config'
    try{
        $all=[System.Collections.Generic.List[object]]::new();$om=[System.Collections.Generic.List[string]]::new()
        Add-ConfigTreeFiles $config 'config\' $true $all $om
        foreach($a in $all){if(-not $known.ContainsKey($a.Rel.ToLowerInvariant())){$res.Extra+=$a.Rel}}
    }catch{$res.ListUnreadable=$true}
    return $res
}

# --- Identidad ACK -------------------------------------------------------------------------------------------------------
# Par ACK del paquete: la identidad importada (formulario-ack-identity.clixml: KeyId, PrivatePem, PublicPem) o el par PEM
# de New-FormularioAckKey.ps1; keyId de formulario-pull.json (ackKeyId) si lo trae. $null si la instancia no tiene ACK.
function Get-PackageAck($Payload){
    $pullKey=''
    $pull=@(@($Payload.items)|Where-Object {[string](Get-PackageField $_ 'path') -ceq 'config\formulario-pull.json'})
    if($pull.Count -eq 1){try{$pullKey=[string](Get-PackageField ([Text.Encoding]::UTF8.GetString((Get-PackageFileBytes $pull[0])).TrimStart([char]0xFEFF)|ConvertFrom-Json) 'ackKeyId')}catch{$pullKey='ILEGIBLE'}}
    $priv=Get-PackageSecret $Payload 'config\formulario-ack-identity.clixml' 'PrivatePem'
    if($null -ne $priv){
        return [PSCustomObject]@{Source='IDENTIDAD';KeyId=[string](Get-PackageSecret $Payload 'config\formulario-ack-identity.clixml' 'KeyId');PrivatePem=$priv;PublicPem=[string](Get-PackageSecret $Payload 'config\formulario-ack-identity.clixml' 'PublicPem');PullKeyId=$pullKey}
    }
    $pp=@(@($Payload.items)|Where-Object {[string](Get-PackageField $_ 'path') -ceq 'config\formulario-ack-private.pem'})
    $pu=@(@($Payload.items)|Where-Object {[string](Get-PackageField $_ 'path') -ceq 'config\formulario-ack-public.pem'})
    if($pp.Count -eq 1 -or $pu.Count -eq 1){
        $a='';$b='';try{if($pp.Count -eq 1){$a=[Text.Encoding]::ASCII.GetString((Get-PackageFileBytes $pp[0]))};if($pu.Count -eq 1){$b=[Text.Encoding]::ASCII.GetString((Get-PackageFileBytes $pu[0]))}}catch{}
        return [PSCustomObject]@{Source='PAR_PEM';KeyId=$pullKey;PrivatePem=$a;PublicPem=$b;PullKeyId=$pullKey}
    }
    return $null
}
# keyId: formato del Formulario y, si formulario-pull.json lo trae, el mismo. Regla pura.
function Get-AckKeyIdFindings($Ack){
    $f=@()
    if([string]$Ack.KeyId -notmatch '^[A-Za-z0-9_-]{1,64}$'){$f+='ACK_KEYID_INVALIDO'}
    if($Ack.PullKeyId -and [string]$Ack.PullKeyId -cne [string]$Ack.KeyId){$f+='ACK_KEYID_DISTINTO'}
    return $f
}
# Comprueba con AckKeyCheck.java (Java de la instancia) que la privada firma lo que la publica verifica. Las PEM viajan
# por la entrada estandar del proceso hijo, nunca en argumentos ni archivos; -XX:-UsePerfData evita hsperfdata.
# COINCIDE, NO_COINCIDE, INVALIDA o NO_COMPROBADA (sin Java legible o sin respuesta).
function Invoke-AckKeyCheck([string]$JavaExe,[string]$Source,[string]$PrivatePem,[string]$PublicPem){
    if(-not $JavaExe -or -not (Test-Path -LiteralPath $JavaExe -PathType Leaf) -or -not (Test-Path -LiteralPath $Source -PathType Leaf)){return 'NO_COMPROBADA'}
    $psi=New-Object Diagnostics.ProcessStartInfo
    $psi.FileName=$JavaExe;$psi.Arguments="-XX:-UsePerfData -Xshare:auto `"$Source`""
    $psi.UseShellExecute=$false;$psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true;$psi.CreateNoWindow=$true
    try{
        $p=[Diagnostics.Process]::Start($psi)
        $p.StandardInput.Write($PrivatePem+"`n"+$PublicPem+"`n");$p.StandardInput.Close()
        $out=$p.StandardOutput.ReadToEnd();$null=$p.StandardError.ReadToEnd()
        if(-not $p.WaitForExit(120000)){try{$p.Kill()}catch{};return 'NO_COMPROBADA'}
    }catch{return 'NO_COMPROBADA'}
    $o=$out.Trim()
    if(@('COINCIDE','NO_COINCIDE','INVALIDA') -ccontains $o){return $o}
    return 'NO_COMPROBADA'
}

# --- MySQL propio -------------------------------------------------------------------------------------------------------
# SELECT 1 con cada cuenta contra el motor de la instancia: 127.0.0.1, su puerto, TLS VERIFY_CA con la CA de su datadir
# (solo ese motor la presenta). La contrasena va en MYSQL_PWD del entorno del proceso hijo: nunca en argumentos ni en
# un archivo de opciones, y --no-defaults evita leer archivos de opciones del equipo.
function New-MySqlProbeStartInfo([string]$MySqlExe,[string]$User,[int]$Port,[string]$Ca){
    $psi=New-Object Diagnostics.ProcessStartInfo
    $psi.FileName=$MySqlExe
    $psi.Arguments=(@('--no-defaults','--protocol=TCP','--host=127.0.0.1',"--port=$Port","--user=$User",'--ssl-mode=VERIFY_CA',"`"--ssl-ca=$Ca`"",'--connect-timeout=10','--batch','--skip-column-names','-e','"SELECT 1"') -join ' ')
    $psi.UseShellExecute=$false;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true;$psi.CreateNoWindow=$true
    $null=$psi.EnvironmentVariables.Remove('MYSQL_PWD')
    return $psi
}
# AUTENTICADA (codigo 0 y la fila 1), RECHAZADA (error 1045) o NO_COMPROBADA.
function Invoke-MySqlProbe($StartInfo,[string]$Password){
    $StartInfo.EnvironmentVariables['MYSQL_PWD']=$Password
    try{
        $p=[Diagnostics.Process]::Start($StartInfo)
        $out=$p.StandardOutput.ReadToEnd();$err=$p.StandardError.ReadToEnd()
        if(-not $p.WaitForExit(60000)){try{$p.Kill()}catch{};return 'NO_COMPROBADA'}
    }catch{return 'NO_COMPROBADA'}finally{$null=$StartInfo.EnvironmentVariables.Remove('MYSQL_PWD')}
    if($p.ExitCode -eq 0 -and $out.Trim() -ceq '1'){return 'AUTENTICADA'}
    if($err -match 'ERROR 1045'){return 'RECHAZADA'}
    return 'NO_COMPROBADA'
}

# --- Verificacion completa (Test-InstanceConfig.ps1) -----------------------------------------------------------------
# Solo lectura: no escribe archivos ni cambia permisos. $AckSource = ruta de AckKeyCheck.java.
function Invoke-ConfigVerification($Payload,[byte[]]$FpKey,[string]$Root,[string]$AckSource){
    $r=[PSCustomObject]@{Content=$null;Disk=$null;Ack='NO_APLICA';AckKeyId='';AckFindings=@();MySql=@();MySqlNote='';Problems=@()}
    $r.Content=Test-ConfigPayload $Payload $FpKey
    $r.Problems+=@($r.Content.Findings)
    if([IO.Path]::GetFullPath($Root).TrimEnd('\') -ine ([string](Get-PackageField $Payload 'root')).TrimEnd('\')){$r.Problems+='RAIZ_DISTINTA'}
    $r.Disk=Compare-ConfigWithDisk $Payload $Root
    foreach($x in $r.Disk.Different){$r.Problems+="DISCO_DISTINTO:$x"}
    foreach($x in $r.Disk.Missing){$r.Problems+="DISCO_FALTA:$x"}
    foreach($x in $r.Disk.Unreadable){$r.Problems+="DISCO_NO_LEGIBLE:$x"}
    foreach($x in $r.Disk.Extra){$r.Problems+="DISCO_EXTRA:$x"}
    if($r.Disk.ListUnreadable){$r.Problems+='DISCO_NO_LEGIBLE:config\'}
    foreach($x in @($r.Disk.P12|Where-Object {$_.State -cne 'IGUALES' -and $_.State -cne 'SIN_CLAVE_CONOCIDA'})){$r.Problems+="DISCO_P12_$($x.State):$($x.Path)"}
    $settings=Get-PackageSettings $Payload
    $ack=Get-PackageAck $Payload
    if($ack){
        $r.AckKeyId=[string]$ack.KeyId
        $r.AckFindings=@(Get-AckKeyIdFindings $ack)
        $java=if($settings -and (Get-PackageField $settings 'javaHome')){Join-Path ([string]$settings.javaHome) 'bin\java.exe'}else{''}
        $r.Ack=Invoke-AckKeyCheck $java $AckSource ([string]$ack.PrivatePem) ([string]$ack.PublicPem)
        $ack=$null
        if($r.Ack -cne 'COINCIDE'){$r.Problems+="ACK_$($r.Ack)"}
        $r.Problems+=@($r.AckFindings)
    }
    if(-not $settings){$r.Problems+='SIN_DESCRIPTOR';$r.MySqlNote='sin instance.json legible en el paquete'}
    else{
        $exe=Join-Path ([string](Get-PackageField $settings 'mysqlHome')) 'bin\mysql.exe'
        $ca=Join-Path ([string]$settings.root) 'mysql\data\ca.pem'
        $port=0;$null=[int]::TryParse([string](Get-PackageField $settings 'mysqlPort'),[ref]$port)
        $caOk=$false;try{$null=[IO.File]::ReadAllBytes($ca);$caOk=$true}catch{}
        if(-not (Test-Path -LiteralPath $exe -PathType Leaf)){$r.MySqlNote='sin mysql.exe en el mysqlHome de la instancia'}
        elseif(-not $caOk){$r.MySqlNote='la CA del motor (mysql\data\ca.pem) no se pudo leer'}
        elseif($port -lt 1024){$r.MySqlNote='puerto del motor no valido en instance.json'}
        foreach($name in $script:ConfigMySqlAccounts.Keys){
            $user=$script:ConfigMySqlAccounts[$name]
            $pass=Get-PackageSecret $Payload 'config\secrets.clixml' $name
            $state=if($null -eq $pass){'SIN_SECRETO'}elseif($r.MySqlNote){'NO_COMPROBADA'}else{Invoke-MySqlProbe (New-MySqlProbeStartInfo $exe $user $port $ca) $pass}
            $pass=$null
            $r.MySql+=[PSCustomObject]@{User=$user;State=$state}
            if($state -cne 'AUTENTICADA'){$r.Problems+="MYSQL_$($state):$user"}
        }
    }
    return $r
}
# Informe: solo nombres de archivo, cifras, fechas de vencimiento, keyId y resultados; nunca valores.
function Format-ConfigVerification($Payload,$Result){
    $c=$Result.Content;$d=$Result.Disk
    $lines=@()
    $lines+="Paquete $($script:ConfigFormat) integro (HMAC correcto): creado $([string]$Payload.createdUtc) en $([string]$Payload.computer) por $([string]$Payload.account), raiz $([string]$Payload.root)."
    $lines+="Contenido: $($c.Files) archivos con SHA-256 y forma correctos, $($c.Secrets) secretos con su huella$(if(@($Payload.omitted).Count){'; carpetas omitidas: '+(@($Payload.omitted) -join ', ')})."
    foreach($p in @($c.P12)){
        if($p.NameState -ceq 'SIN_CLAVE'){$lines+="Almacen $($p.Path): sin clave conocida, se conserva tal cual (tamano y SHA-256 verificados; no se abre ni se comparan certificados).";continue}
        if($p.NameState -ceq 'MAYUSCULAS'){$lines+="Almacen $($p.Path): nombre de un almacen conocido con otras mayusculas (P12_MAYUSCULAS).";continue}
        $lines+="Almacen $($p.Path): $(if($p.Opens){"abre con su clave del paquete, $($p.Count) certificado(s), primer vencimiento $($p.NotAfter.Substring(0,4))-$($p.NotAfter.Substring(4,2))-$($p.NotAfter.Substring(6,2)), $(if($p.Matches){'iguales a los registrados'}else{'DISTINTOS a los registrados'})"}else{'NO abre con su clave del paquete'})."}
    $lines+="Contra el disco: $(@($d.Equal).Count) iguales, $(@($d.Different).Count) distintos, $(@($d.Missing).Count) faltan, $(@($d.Unreadable).Count) no legibles, $(@($d.Extra).Count) en disco y no en el paquete$(if($d.ListUnreadable){'; no se pudo listar config\'})."
    foreach($x in @($d.P12)){
        if($x.State -ceq 'SIN_CLAVE_CONOCIDA'){$lines+="Almacen del disco $($x.Path): SIN_CLAVE_CONOCIDA (comparado solo por SHA-256: $(if($x.Equal){'IGUAL'}else{'DISTINTO'}))."}
        else{$lines+="Almacen del disco $($x.Path) con la clave del paquete: $($x.State)."}
    }
    if($Result.Ack -cne 'NO_APLICA'){$lines+="Identidad ACK keyId=$($Result.AckKeyId): privada y publica $($Result.Ack)$(if(@($Result.AckFindings).Count){' ('+(@($Result.AckFindings) -join ', ')+')'}else{'; keyId valido y coherente con formulario-pull.json'})."}else{$lines+='Identidad ACK: no hay en el paquete.'}
    $lines+="MySQL propio (SELECT 1): $(@($Result.MySql|Where-Object {$_.State -ceq 'AUTENTICADA'}).Count) de $(@($Result.MySql).Count) cuentas autenticadas$(if(@($Result.MySql).Count){' ('+((@($Result.MySql)|ForEach-Object {"$($_.User) $($_.State)"}) -join ', ')+')'})$(if($Result.MySqlNote){'; '+$Result.MySqlNote})."
    $lines+=if(@($Result.Problems).Count -eq 0){'Resultado: CORRECTO.'}else{'Resultado: CON OBSERVACIONES: '+(@($Result.Problems) -join ', ')+'.'}
    return $lines
}
