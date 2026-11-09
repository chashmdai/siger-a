package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Documento original de una Comunicacion externa: la entrega del Formulario completo 1.0/1.1
 * ({comunicacion, sha256, huellaProcedencia, custodia}) tal como la conserva SMR.
 * Solo se leen los datos que identifican el original y se recalculan sus huellas con el mismo
 * algoritmo del contrato; el texto recibido se conserva sin cambios.
 * Las huellas por si solas no acreditan autenticidad (pueden recalcularse sobre un documento
 * fabricado). La autenticidad la da el sello de custodia: firma Ed25519 del Formulario, verificada
 * con una clave confiable del operador (FormularioKeys), cuyo contenido firmado debe corresponder
 * exactamente a identificador, fecha, version del aviso y ambas huellas (mismo contrato que
 * ComprobanteDigital/EntregaCompleta de SMR). El documento no puede aportar su propia clave y no
 * admite campos fuera de lo firmado, de modo que todo lo conservado queda cubierto por el sello.
 */
record ExternalOriginal(String source,String externalId,String version,String kind,String channel,long receivedMs,
        String contentHash,String provenanceHash,String custodyKeyId,String document,String documentSha256,long documentBytes,
        ExternalProjection projection,List<Attachment> attachments) {

    /** Descriptor firmado de un original ciudadano (comunicacion.adjuntos), ya validado por contract(). */
    record Attachment(String id,String name,long size,String sha256,String type) { }

    /** Emisor del sello de custodia y origen de la cola de SMR. */
    static final String SOURCE="FORMULARIO_PUBLICO_DDN";
    /** Mismo limite que el contrato fija para las respuestas JSON del Formulario. */
    static final int MAX_BYTES=2*1024*1024;
    private static final String INVALID="El documento original no corresponde al contrato del Formulario completo.";
    private static final Set<String> KINDS=Set.of("ADULTO_CU","ADULTO_DECLARADO","NNA");
    private static final JsonMapper READER=JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(20000).maxNumberLength(30).build()).build()).build();
    private static final JsonMapper WRITER=JsonMapper.builder().build();
    private static final Set<String> ROOT_FIELDS=Set.of("comunicacion","sha256","huellaProcedencia","custodia");
    private static final Set<String> CUSTODY_FIELDS=Set.of("algoritmo","keyId","payload","firma");
    /** Contenido firmado del sello, en el orden canonico de ComprobanteDigital.Contenido de SMR. */
    private static final List<String> SEAL_FIELDS=List.of("version","id","recibidaEn","tipoRecepcion","huellaContenido","huellaProcedencia","emisor","avisoVersion");

    static ExternalOriginal parse(String document,Map<String,java.security.PublicKey> trusted){
        if(document==null||document.isBlank())throw new Problem(400,"Falta el documento original.");
        byte[] bytes=document.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new Problem(400,"El documento original supera el tamano admitido.");
        JsonNode root,com,custody;
        String id,version,kind,content,provenance,keyId,notice;
        Instant received;
        var sources=WRITER.createObjectNode();
        try{
            root=READER.readTree(document);
            com=root.path("comunicacion");
            custody=root.path("custodia");
            if(!root.isObject()||!com.isObject()||!custody.isObject()||!names(root).equals(ROOT_FIELDS)||!names(custody).equals(CUSTODY_FIELDS))throw new IllegalArgumentException();
            version=com.path("version").asString();
            kind=com.path("tipo").asString();
            id=com.path("id").asString();
            if(!Set.of("1.0","1.1").contains(version)||!KINDS.contains(kind)||!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();
            received=Instant.parse(com.path("recibidaEn").asString());
            notice=com.path("avisoVersion").asString();
            if(!com.path("avisoVersion").isString()||notice.isBlank())throw new IllegalArgumentException();
            content=hex(root.path("sha256"));
            provenance=hex(root.path("huellaProcedencia"));
            keyId=custody.path("keyId").asString();
            if(!"Ed25519".equals(custody.path("algoritmo").asString())||!keyId.matches("[A-Za-z0-9_-]{1,64}")
                ||!custody.path("payload").isString()||custody.path("payload").asString().length()>4096
                ||!custody.path("firma").isString()||custody.path("firma").asString().length()>128)throw new IllegalArgumentException();
            if(!com.path("personas").isArray())throw new IllegalArgumentException();
            for(var p:com.path("personas")){
                String person=p.path("id").asString();
                if(!p.path("id").isString()||person.isBlank()||sources.has(person)||!p.path("atributos").isObject()||!p.path("discrepancias").isObject())throw new IllegalArgumentException();
                sources.set(person,WRITER.createArrayNode().add(p.path("atributos")).add(p.path("discrepancias")));
            }
            contract(com,kind,version);
        }catch(RuntimeException e){
            throw new Problem(400,INVALID);
        }
        if(!huella("CONTENIDO",com).equals(content)||!huella("PROCEDENCIA",sources).equals(provenance))
            throw new Problem(400,"Las huellas del documento original no coinciden con su contenido.");
        verifySeal(custody,trusted,id,received,notice,content,provenance);
        if(received.toEpochMilli()>System.currentTimeMillis())throw new Problem(400,"La fecha de recepcion del documento original es futura.");
        return new ExternalOriginal(SOURCE,id,version,kind,"NNA".equals(kind)?"FORMULARIO_WEB_NNA":"FORMULARIO_WEB_PUBLICO",received.toEpochMilli(),
            content,provenance,keyId,document,sha256(bytes),bytes.length,ExternalProjection.of(com,received),attachments(com));
    }
    private static List<Attachment> attachments(JsonNode com){
        var out=new ArrayList<Attachment>();
        for(var a:com.path("adjuntos"))out.add(new Attachment(UUID.fromString(a.path("id").asString()).toString(),a.path("nombre").asString(),a.path("tamanio").asLong(),a.path("sha256").asString(),a.path("tipo").asString()));
        return List.copyOf(out);
    }

    private static final Set<String> ROLES=Set.of("PRESENTADOR","AFECTADO_NNA","ADULTO_RELACIONADO","PERSONA_SENALADA");
    private static final Set<String> SOURCES=Set.of("CLAVE_UNICA_USERINFO","SRCEI","DECLARACION_CIUDADANA","SIN_DATO");
    private static final String CU="CLAVE_UNICA_USERINFO";
    /**
     * Nivel unico por fuente en el Formulario: IdentificacionCiudadana.put (CU autenticada, declaracion no contrastada)
     * y ResultadoSrcei (SRCeI siempre CONTRASTADO_EN_FUENTE). SIN_DATO no tiene emisor en el Formulario: se admite con
     * un nivel no verificado, pero nunca con IDENTIDAD_AUTENTICADA ni CONTRASTADO_EN_FUENTE.
     */
    private static final Map<String,String> LEVELS=Map.of(CU,"IDENTIDAD_AUTENTICADA","SRCEI","CONTRASTADO_EN_FUENTE","DECLARACION_CIUDADANA","DECLARADO_NO_CONTRASTADO");
    private static final int MAX_KEY=255;

    /**
     * Reglas semanticas del contrato, portadas de EntregaCompleta.validar (SMR 02d4184): la firma prueba origen, no
     * conformidad. Tipo de ingreso y autenticacion, roles, presentador unico, procedencias y niveles compatibles,
     * referencias internas y descriptores de adjuntos (limites 1.0/1.1). Campos opcionales ausentes y campos
     * desconocidos de comunicacion se admiten y se conservan. Agrega los limites de largo de las columnas de SIGER A
     * para identificadores y claves (64/255), muy por sobre lo que emite el Formulario.
     */
    static void contract(JsonNode com,String kind,String version){
        var datos=com.path("datos");
        if(!com.path("aceptaCanalDigital").isBoolean()||!datos.isObject()||!datos.path("campos").isObject()
            ||!com.path("personas").isArray()||com.path("personas").size()>30||!com.path("adjuntos").isArray()
            ||!datos.path("personas").isArray()||!datos.path("instituciones").isArray()||!datos.path("adjuntos").isArray())fail();
        UUID.fromString(com.path("recorridoId").asString());
        if(!"NNA".equals(kind)&&!com.path("aceptaCanalDigital").asBoolean())fail();
        var persons=new HashSet<String>();int presenters=0;
        for(var p:com.path("personas")){
            String person=p.path("id").asString();
            if(person.isBlank()||person.length()>64||!persons.add(person)||!p.path("atributos").isObject()||!p.path("discrepancias").isObject()||!p.path("autenticada").isBoolean())fail();
            boolean presenter="PRESENTADOR".equals(p.path("rol").asString()),auth=p.path("autenticada").asBoolean();
            if(presenter)presenters++;
            if(!ROLES.contains(p.path("rol").asString()))fail();
            if(auth)for(var key:List.of("runNumero","runDv","nombres","apellidos"))
                if(!CU.equals(p.path("atributos").path(key).path("fuente").asString())||!p.path("atributos").path(key).path("valor").isString()||p.path("atributos").path(key).path("valor").asString().isBlank())fail();
            if(auth!=(presenter&&"ADULTO_CU".equals(kind)))fail();
            if(auth)for(var key:List.of("runNumero","runDv"))if(!p.path(key).equals(p.path("atributos").path(key).path("valor")))fail();
            // RUN autenticado (RolUnico de ClaveUnica): numero y digito verificador validos por modulo 11; nunca se acepta en silencio.
            if(auth){
                String number=p.path("runNumero").asString(),dv=p.path("runDv").asString().toUpperCase(Locale.ROOT);
                if(!number.matches("[1-9][0-9]{0,8}")||!dv.matches("[0-9K]")||!ExternalProjection.modulo11(number,dv))fail();
            }
            for(var group:List.of(p.path("atributos"),p.path("discrepancias")))for(var entry:group.properties()){
                var a=entry.getValue();String source=a.path("fuente").asString(),level=a.path("nivel").asString();
                if(entry.getKey().length()>MAX_KEY||!a.isObject()||!SOURCES.contains(source)||CU.equals(source)&&!auth)fail();
                if(!a.path("nivel").isString()||level.isBlank()||level.length()>64||!LEVELS.getOrDefault(source,level).equals(level)
                    ||"SIN_DATO".equals(source)&&LEVELS.containsValue(level)&&!"DECLARADO_NO_CONTRASTADO".equals(level))fail();
                for(var key:List.of("valor","codigo"))if(!a.has(key)||!(a.path(key).isNull()||a.path(key).isString()))fail();
                if(a.path("codigo").isString()&&a.path("codigo").asString().length()>MAX_KEY)fail();
                UUID.fromString(a.path("evidencia").asString());
                Instant.parse(a.path("observadoEn").asString());
            }
        }
        if(presenters!=1)fail();
        for(var entry:datos.path("campos").properties())if(entry.getKey().length()>MAX_KEY||!entry.getValue().isString())fail();
        var details=new HashSet<String>();
        for(var detail:datos.path("personas")){
            String person=detail.path("personaId").asString();
            if(!persons.contains(person)||!details.add(person)||!detail.path("detalles").isObject())fail();
            for(var v:detail.path("detalles").properties())if(v.getKey().length()>MAX_KEY||!v.getValue().isString())fail();
        }
        for(var institution:datos.path("instituciones")){
            if(!institution.isObject())fail();
            for(var v:institution.properties())if(v.getKey().length()>MAX_KEY||!(v.getValue().isString()||v.getValue().isNull()))fail();
        }
        var ids=new HashSet<String>();long total=0;
        if(com.path("adjuntos").size()>12)fail();
        for(var a:com.path("adjuntos")){
            String id=a.path("id").asString(),name=a.path("nombre").asString(),type=a.path("tipo").asString();
            if(!ids.add(UUID.fromString(id).toString())||!a.path("nombre").isString()||name.isBlank()||name.length()>180||name.codePoints().anyMatch(Character::isISOControl)
                ||!a.path("tamanio").canConvertToExactIntegral()||!a.path("sha256").isString()||!a.path("sha256").asString().matches("[a-f0-9]{64}")
                ||type.length()>MAX_KEY||!type.matches("[a-z0-9.+-]+/[a-z0-9.+-]+")||!"VALIDADO".equals(a.path("estado").asString()))fail();
            long size=a.path("tamanio").asLong();
            if(size<1||size>250L*1024*1024)fail();
            total=Math.addExact(total,size);
            if("1.0".equals(version)&&size>10L*1024*1024)fail();
        }
        if(total>250L*1024*1024||"1.0".equals(version)&&(ids.size()>5||total>25L*1024*1024))fail();
        var refs=new HashSet<String>();
        for(var a:datos.path("adjuntos"))if(!a.isString()||!refs.add(UUID.fromString(a.asString()).toString()))fail();
        if(!refs.equals(ids))fail();
    }
    private static void fail(){throw new IllegalArgumentException();}

    /**
     * Sello de custodia: la clave sale solo del conjunto confiable (por keyId); la firma Ed25519 cubre los
     * bytes exactos del contenido, que debe ser JSON canonico con los campos del contrato y corresponder
     * a este documento. Se verifica la firma antes de interpretar el contenido firmado.
     */
    private static void verifySeal(JsonNode custody,Map<String,java.security.PublicKey> trusted,String id,Instant received,String notice,String content,String provenance){
        var key=trusted==null?null:trusted.get(custody.path("keyId").asString());
        if(key==null)throw new Problem(400,"El sello de custodia no corresponde a una clave confiable del Formulario. No se registro la operacion.");
        byte[] payload;
        try{
            payload=Base64.getUrlDecoder().decode(custody.path("payload").asString());
            var verifier=java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payload);
            if(!verifier.verify(Base64.getUrlDecoder().decode(custody.path("firma").asString())))throw new IllegalArgumentException();
        }catch(java.security.GeneralSecurityException|IllegalArgumentException e){
            throw new Problem(400,"La firma de custodia del documento original no es valida. No se registro la operacion.");
        }
        boolean matches;
        try{
            var seal=READER.readTree(payload);
            var order=new ArrayList<String>();
            seal.properties().forEach(e->order.add(e.getKey()));
            boolean strings=SEAL_FIELDS.stream().allMatch(f->seal.path(f).isString());
            String at=seal.path("recibidaEn").asString();
            matches=seal.isObject()&&order.equals(SEAL_FIELDS)&&strings&&Arrays.equals(payload,WRITER.writeValueAsBytes(seal))
                &&"1.0".equals(seal.path("version").asString())&&"CUSTODIA_PUBLICA".equals(seal.path("tipoRecepcion").asString())
                &&SOURCE.equals(seal.path("emisor").asString())&&id.equals(seal.path("id").asString())
                &&Instant.parse(at).toString().equals(at)&&Instant.parse(at).equals(received)
                &&notice.equals(seal.path("avisoVersion").asString())
                &&content.equals(seal.path("huellaContenido").asString())&&provenance.equals(seal.path("huellaProcedencia").asString());
        }catch(RuntimeException e){
            matches=false;
        }
        if(!matches)throw new Problem(400,"El sello de custodia no corresponde a este documento original. No se registro la operacion.");
    }
    private static Set<String> names(JsonNode n){
        var out=new HashSet<String>();
        n.properties().forEach(e->out.add(e.getKey()));
        return out;
    }

    /** Huella del contrato: SHA-256 con prefijo de dominio sobre el JSON con claves ordenadas recursivamente. */
    static String huella(String domain,JsonNode value){
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update(("SIGER_FORMULARIO_COMPLETO_1\0"+domain+"\0").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(WRITER.writeValueAsBytes(ordered(value))));
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static JsonNode ordered(JsonNode n){
        if(n.isObject()){
            var out=WRITER.createObjectNode();
            var keys=new TreeSet<String>();
            n.properties().forEach(e->keys.add(e.getKey()));
            keys.forEach(k->out.set(k,ordered(n.get(k))));
            return out;
        }
        if(n.isArray()){
            var out=WRITER.createArrayNode();
            for(var e:n)out.add(ordered(e));
            return out;
        }
        return n;
    }
    private static String hex(JsonNode n){
        if(!n.isString()||!n.asString().matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
        return n.asString();
    }
    static String sha256(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Override public String toString(){return "ExternalOriginal[source="+source+",id="+externalId+"]";}
}
