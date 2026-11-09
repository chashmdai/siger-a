package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Comprobante institucional del ACK al Formulario ({registro, comprobante}; ComprobanteInstitucional y
 * RegistroInstitucional del productor). El payload es el JSON compacto del registro en el orden de campos del
 * record del productor (no alfabetico), firmado con Ed25519; ambos en base64url sin relleno.
 * La version del ACK depende del canal (1.1 para FORMULARIO_WEB_NNA, 1.0 para FORMULARIO_WEB_PUBLICO), nunca
 * de la version del documento. La clave privada vive fuera del repositorio; al cargarla se comprueba que firma
 * de forma verificable con la clave publica que el operador entrega al Formulario.
 */
final class FormularioAck {
    static final String EMITTER="SIGER_DDN",FACT="COMUNICACION_REGISTRADA";
    static final String FOLIO="[A-Z][A-Z0-9]{1,15}-[0-9]{1,19}",PREFIX="[A-Z][A-Z0-9]{1,15}";
    private static final List<String> FIELDS=List.of("version","emisor","hecho","operacionPublicaId","comunicacionId","numeroFolio","folio","registradaEn","canalOrigen","sha256","huellaProcedencia");
    private static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
    private static final int MAX_PEM=8192;

    /** Contenido del registro; valida las mismas reglas que ComprobanteInstitucional.Contenido del productor. */
    record Registro(String version,UUID operation,UUID communication,long number,String folio,Instant registered,String channel,String sha256,String provenance) {
        Registro {
            if(!FormularioAck.version(channel).equals(version)||operation==null||communication==null||operation.equals(communication)||number<1
                ||folio==null||!folio.matches(FOLIO)||registered==null||sha256==null||!sha256.matches("[a-f0-9]{64}")||provenance==null||!provenance.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Registro institucional invalido.");
            try{if(Long.parseLong(folio.substring(folio.lastIndexOf('-')+1))!=number)throw new IllegalArgumentException("Folio incoherente.");}
            catch(NumberFormatException e){throw new IllegalArgumentException("Folio fuera de rango.");}
        }
    }

    private final String keyId;private final PrivateKey key;private final PublicKey publicKey;

    FormularioAck(String keyId,PrivateKey key,PublicKey publicKey){
        if(keyId==null||!keyId.matches("[A-Za-z0-9_-]{1,64}")||key==null||publicKey==null)throw new IllegalStateException("Clave del comprobante institucional invalida.");
        this.keyId=keyId;this.key=key;this.publicKey=publicKey;
        try{
            byte[] probe=("SIGER_A_ACK_PRUEBA\0"+UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
            if(!verify(probe,sign(probe)))throw new IllegalStateException();
        }catch(GeneralSecurityException|RuntimeException e){
            throw new IllegalStateException("La clave privada del comprobante institucional no corresponde a su clave publica.");
        }
    }
    static FormularioAck load(String keyId,Path privatePem,Path publicPem){
        try{
            if(Files.size(privatePem)>MAX_PEM||Files.size(publicPem)>MAX_PEM)throw new java.io.IOException();
            return fromPem(keyId,Files.readString(privatePem,StandardCharsets.US_ASCII),Files.readString(publicPem,StandardCharsets.US_ASCII));
        }catch(IllegalStateException e){throw e;}
        catch(Exception e){throw new IllegalStateException("Claves del comprobante institucional invalidas o ilegibles.");}
    }
    /**
     * Par desde texto PEM (PKCS#8 / SPKI), por ejemplo una identidad ACK existente que el lanzador descifra desde DPAPI
     * y entrega solo al proceso. Mismas validaciones que desde archivos.
     */
    static FormularioAck fromPem(String keyId,String privatePem,String publicPem){
        try{
            if(privatePem.length()>MAX_PEM||publicPem.length()>MAX_PEM)throw new java.io.IOException();
            var factory=KeyFactory.getInstance("Ed25519");
            var priv=factory.generatePrivate(new PKCS8EncodedKeySpec(pem(privatePem,"PRIVATE KEY")));
            var pub=factory.generatePublic(new X509EncodedKeySpec(pem(publicPem,"PUBLIC KEY")));
            return new FormularioAck(keyId,priv,pub);
        }catch(IllegalStateException e){throw e;}
        catch(Exception e){throw new IllegalStateException("Claves del comprobante institucional invalidas o ilegibles.");}
    }
    private static byte[] pem(String text,String label)throws java.io.IOException{
        String body=text.strip();
        String begin="-----BEGIN "+label+"-----",end="-----END "+label+"-----";
        if(!body.startsWith(begin)||!body.endsWith(end))throw new java.io.IOException();
        return Base64.getMimeDecoder().decode(body.substring(begin.length(),body.length()-end.length()));
    }

    String keyId(){return keyId;}

    static String version(String channel){
        if("FORMULARIO_WEB_NNA".equals(channel))return "1.1";
        if("FORMULARIO_WEB_PUBLICO".equals(channel))return "1.0";
        throw new IllegalArgumentException("Canal de origen sin version de ACK.");
    }

    /** JSON compacto del registro en el orden del record del productor; Instant en ISO-8601 como lo serializa Jackson. */
    static byte[] payload(Registro r){
        var o=JSON.createObjectNode();
        o.put("version",r.version());o.put("emisor",EMITTER);o.put("hecho",FACT);
        o.put("operacionPublicaId",r.operation().toString());o.put("comunicacionId",r.communication().toString());
        o.put("numeroFolio",r.number());o.put("folio",r.folio());o.put("registradaEn",r.registered().toString());
        o.put("canalOrigen",r.channel());o.put("sha256",r.sha256());o.put("huellaProcedencia",r.provenance());
        return JSON.writeValueAsBytes(o);
    }

    /** Recepcion firmada {registro, comprobante} lista para enviar en la confirmacion y para conservar sin cambios. */
    String sign(Registro r){
        try{
            byte[] payload=payload(r);
            var root=JSON.createObjectNode();
            root.set("registro",JSON.readTree(payload));
            var proof=root.putObject("comprobante");
            proof.put("algoritmo","Ed25519");proof.put("keyId",keyId);
            proof.put("payload",b64(payload));proof.put("firma",b64(sign(payload)));
            String text=JSON.writeValueAsString(root);
            if(!verify(text).equals(r))throw new IllegalStateException();
            return text;
        }catch(GeneralSecurityException e){throw new IllegalStateException("No se pudo firmar el comprobante institucional.");}
    }

    /**
     * Comprueba un comprobante conservado tal como lo hara el Formulario: firma con la clave propia, payload canonico
     * en el orden del record y registro identico al contenido firmado. Devuelve el registro.
     */
    Registro verify(String receipt){
        try{
            JsonNode root=JSON.readTree(receipt),proof=root.path("comprobante");
            if(!root.isObject()||root.size()!=2||!proof.isObject()||proof.size()!=4||!"Ed25519".equals(proof.path("algoritmo").asString())
                ||!keyId.equals(proof.path("keyId").asString())||!proof.path("firma").asString().matches("[A-Za-z0-9_-]{86}")
                ||!proof.path("payload").asString().matches("[A-Za-z0-9_-]{1,8192}"))throw new IllegalArgumentException();
            byte[] payload=Base64.getUrlDecoder().decode(proof.path("payload").asString());
            if(!verify(payload,Base64.getUrlDecoder().decode(proof.path("firma").asString())))throw new IllegalArgumentException();
            var signed=(ObjectNode)JSON.readTree(payload);
            var order=new ArrayList<String>();signed.properties().forEach(e->order.add(e.getKey()));
            if(!order.equals(FIELDS)||!signed.equals(root.path("registro"))||!EMITTER.equals(signed.path("emisor").asString())||!FACT.equals(signed.path("hecho").asString())
                ||!signed.path("numeroFolio").canConvertToExactIntegral())throw new IllegalArgumentException();
            String at=signed.path("registradaEn").asString();
            var r=new Registro(signed.path("version").asString(),uuid(signed.path("operacionPublicaId")),uuid(signed.path("comunicacionId")),signed.path("numeroFolio").asLong(),
                signed.path("folio").asString(),Instant.parse(at),signed.path("canalOrigen").asString(),signed.path("sha256").asString(),signed.path("huellaProcedencia").asString());
            if(!Arrays.equals(payload,payload(r))||!r.registered().toString().equals(at))throw new IllegalArgumentException();
            return r;
        }catch(RuntimeException|GeneralSecurityException e){
            throw new IllegalArgumentException("Comprobante institucional no verificable.");
        }
    }
    private static UUID uuid(JsonNode n){var u=UUID.fromString(n.asString());if(!u.toString().equals(n.asString()))throw new IllegalArgumentException();return u;}
    private byte[] sign(byte[] payload)throws GeneralSecurityException{var s=Signature.getInstance("Ed25519");s.initSign(key);s.update(payload);return s.sign();}
    private boolean verify(byte[] payload,byte[] signature)throws GeneralSecurityException{var s=Signature.getInstance("Ed25519");s.initVerify(publicKey);s.update(payload);return s.verify(signature);}
    private static String b64(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    @Override public String toString(){return "FormularioAck[keyId="+keyId+"]";}
}
