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
 * Las huellas solo prueban coherencia interna del documento, NO su autenticidad: cualquiera puede
 * recalcularlas sobre un documento fabricado. La firma Ed25519 de custodia todavia no se verifica
 * (requiere la clave publica confiable del Formulario); hasta entonces, conservar un original no
 * acredita que provenga del Formulario Publico.
 */
record ExternalOriginal(String source,String externalId,String version,String kind,String channel,long receivedMs,
        String contentHash,String provenanceHash,String custodyKeyId,String document,String documentSha256,long documentBytes) {

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

    static ExternalOriginal parse(String document){
        if(document==null||document.isBlank())throw new Problem(400,"Falta el documento original.");
        byte[] bytes=document.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new Problem(400,"El documento original supera el tamano admitido.");
        JsonNode root,com;
        String id,version,kind,content,provenance,keyId;
        Instant received;
        var sources=WRITER.createObjectNode();
        try{
            root=READER.readTree(document);
            com=root.path("comunicacion");
            if(!root.isObject()||!com.isObject())throw new IllegalArgumentException();
            version=com.path("version").asString();
            kind=com.path("tipo").asString();
            id=com.path("id").asString();
            if(!Set.of("1.0","1.1").contains(version)||!KINDS.contains(kind)||!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();
            received=Instant.parse(com.path("recibidaEn").asString());
            content=hex(root.path("sha256"));
            provenance=hex(root.path("huellaProcedencia"));
            var custody=root.path("custodia");
            keyId=custody.path("keyId").asString();
            if(!custody.isObject()||!"Ed25519".equals(custody.path("algoritmo").asString())||!keyId.matches("[A-Za-z0-9_-]{1,64}")
                ||!custody.path("payload").isString()||!custody.path("firma").isString())throw new IllegalArgumentException();
            if(!com.path("personas").isArray())throw new IllegalArgumentException();
            for(var p:com.path("personas")){
                String person=p.path("id").asString();
                if(!p.path("id").isString()||person.isBlank()||sources.has(person)||!p.path("atributos").isObject()||!p.path("discrepancias").isObject())throw new IllegalArgumentException();
                sources.set(person,WRITER.createArrayNode().add(p.path("atributos")).add(p.path("discrepancias")));
            }
        }catch(RuntimeException e){
            throw new Problem(400,INVALID);
        }
        if(!huella("CONTENIDO",com).equals(content)||!huella("PROCEDENCIA",sources).equals(provenance))
            throw new Problem(400,"Las huellas del documento original no coinciden con su contenido.");
        if(received.toEpochMilli()>System.currentTimeMillis())throw new Problem(400,"La fecha de recepcion del documento original es futura.");
        return new ExternalOriginal(SOURCE,id,version,kind,"NNA".equals(kind)?"FORMULARIO_WEB_NNA":"FORMULARIO_WEB_PUBLICO",received.toEpochMilli(),
            content,provenance,keyId,document,sha256(bytes),bytes.length);
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
