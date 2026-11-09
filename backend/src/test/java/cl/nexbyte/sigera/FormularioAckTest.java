package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprobante institucional del ACK: mismos bytes que los vectores sinteticos del productor (docs/smr-ejemplos de
 * formulario-web) y misma ida y vuelta que ComprobanteInstitucional.verificar con Jackson. Version por canal.
 */
class FormularioAckTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String FIXTURE_ACK_PUBLIC="MCowBQYDK2VwAyEAzwetq1pF+fnnX5KTsMKR9O8ZlmpVHTfv+fqC/qRBVgE=";

    /** Copia del record ComprobanteInstitucional.Contenido del productor (mismo orden de componentes). */
    record Contenido(String version,String emisor,String hecho,UUID operacionPublicaId,UUID comunicacionId,
        long numeroFolio,String folio,Instant registradaEn,String canalOrigen,String sha256,String huellaProcedencia) { }

    static KeyPair pair()throws Exception{return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();}
    static FormularioAck ack(KeyPair k){return new FormularioAck("SIGER_A_PRUEBA",k.getPrivate(),k.getPublic());}
    static FormularioAck.Registro registro(String channel,Instant at){
        return new FormularioAck.Registro(FormularioAck.version(channel),UUID.randomUUID(),UUID.randomUUID(),42,"COMA-000042",at,channel,"a".repeat(64),"b".repeat(64));
    }

    @Test void payloadIdenticoALosVectoresDelProductor()throws Exception{
        // fixture-ack-public.pem de formulario-web/docs/smr-ejemplos (clave publica sintetica SMR_FIXTURE_ACK).
        var producerTrust=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(FIXTURE_ACK_PUBLIC)));
        for(String v:List.of("1.0","1.1")){
            JsonNode fixture=JSON.readTree(Files.readString(Path.of("src/test/resources/smr/ack-solicitud-"+v+".json"))).path("recepcion");
            JsonNode r=fixture.path("registro");
            var mine=new FormularioAck.Registro(r.path("version").asString(),UUID.fromString(r.path("operacionPublicaId").asString()),UUID.fromString(r.path("comunicacionId").asString()),
                r.path("numeroFolio").asLong(),r.path("folio").asString(),Instant.parse(r.path("registradaEn").asString()),r.path("canalOrigen").asString(),r.path("sha256").asString(),r.path("huellaProcedencia").asString());
            byte[] payload=FormularioAck.payload(mine);
            // Mismos bytes que firmo el vector del productor (orden del record, compacto), y esa firma los cubre.
            assertEquals(fixture.path("comprobante").path("payload").asString(),Base64.getUrlEncoder().withoutPadding().encodeToString(payload),v);
            var verifier=Signature.getInstance("Ed25519");verifier.initVerify(producerTrust);verifier.update(payload);
            assertTrue(verifier.verify(Base64.getUrlDecoder().decode(fixture.path("comprobante").path("firma").asString())),v);
            // Documento 1.0 y 1.1 del canal NNA: ambos ACK 1.1. La version sigue al canal, no al documento.
            assertEquals("1.1",mine.version());
            assertEquals(r,JSON.readTree(payload));
        }
    }

    @Test void idaYVueltaJacksonComoElProductorYFirmaVerificable()throws Exception{
        var k=pair();var ack=ack(k);
        for(Instant at:List.of(Instant.parse("2026-11-04T12:00:00Z"),Instant.parse("2026-11-04T12:00:00.120Z"),Instant.parse("2026-11-04T12:00:00.007Z"))){
            for(String channel:List.of("FORMULARIO_WEB_PUBLICO","FORMULARIO_WEB_NNA")){
                var r=registro(channel,at);
                byte[] payload=FormularioAck.payload(r);
                var content=new Contenido(r.version(),"SIGER_DDN","COMUNICACION_REGISTRADA",r.operation(),r.communication(),r.number(),r.folio(),r.registered(),r.channel(),r.sha256(),r.provenance());
                // ComprobanteInstitucional.firmar/verificar: writeValueAsBytes(record) y readValue+reserializacion identicos.
                assertArrayEquals(JSON.writeValueAsBytes(content),payload,at+" "+channel);
                assertArrayEquals(payload,JSON.writeValueAsBytes(JSON.readValue(payload,Contenido.class)));
                String receipt=ack.sign(r);
                assertEquals(r,ack.verify(receipt));
                var root=JSON.readTree(receipt);
                // RegistroInstitucional: el registro enviado es igual al contenido firmado.
                assertEquals(content,JSON.treeToValue(root.path("registro"),Contenido.class));
                assertEquals("SIGER_A_PRUEBA",root.path("comprobante").path("keyId").asString());
                assertEquals(86,root.path("comprobante").path("firma").asString().length());
                var verifier=Signature.getInstance("Ed25519");verifier.initVerify(k.getPublic());verifier.update(Base64.getUrlDecoder().decode(root.path("comprobante").path("payload").asString()));
                assertTrue(verifier.verify(Base64.getUrlDecoder().decode(root.path("comprobante").path("firma").asString())));
                assertEquals("FORMULARIO_WEB_NNA".equals(channel)?"1.1":"1.0",root.path("registro").path("version").asString());
            }
        }
    }

    @Test void reglasDelRegistroYAlteracionesRechazadas()throws Exception{
        var at=Instant.parse("2026-11-04T12:00:00Z");var op=UUID.randomUUID();var id=UUID.randomUUID();
        String h="c".repeat(64);
        // Version incoherente con el canal, folio fuera del formato del contrato o con sufijo distinto del numero.
        assertThrows(IllegalArgumentException.class,()->new FormularioAck.Registro("1.0",op,id,1,"COMA-000001",at,"FORMULARIO_WEB_NNA",h,h));
        assertThrows(IllegalArgumentException.class,()->new FormularioAck.Registro("1.1",op,id,1,"COMA-000001",at,"FORMULARIO_WEB_PUBLICO",h,h));
        assertThrows(IllegalArgumentException.class,()->new FormularioAck.Registro("1.0",op,id,1,"COM-A-000001",at,"FORMULARIO_WEB_PUBLICO",h,h));
        assertThrows(IllegalArgumentException.class,()->new FormularioAck.Registro("1.0",op,id,2,"COMA-000001",at,"FORMULARIO_WEB_PUBLICO",h,h));
        assertThrows(IllegalArgumentException.class,()->new FormularioAck.Registro("1.0",op,id,0,"COMA-0",at,"FORMULARIO_WEB_PUBLICO",h,h));
        assertThrows(IllegalArgumentException.class,()->new FormularioAck.Registro("1.0",op,op,1,"COMA-000001",at,"FORMULARIO_WEB_PUBLICO",h,h));
        assertThrows(IllegalArgumentException.class,()->FormularioAck.version("OTRO_CANAL"));
        assertDoesNotThrow(()->new FormularioAck.Registro("1.0",op,id,1,"COMA-000001",at,"FORMULARIO_WEB_PUBLICO",h,h));

        var k=pair();var ack=ack(k);
        String receipt=ack.sign(registro("FORMULARIO_WEB_PUBLICO",at));
        var root=(tools.jackson.databind.node.ObjectNode)JSON.readTree(receipt);
        // Registro enviado distinto del firmado.
        var changed=root.deepCopy();((tools.jackson.databind.node.ObjectNode)changed.path("registro")).put("numeroFolio",43);
        assertThrows(IllegalArgumentException.class,()->ack.verify(changed.toString()));
        // Firma alterada.
        var forged=root.deepCopy();String firma=root.path("comprobante").path("firma").asString();
        ((tools.jackson.databind.node.ObjectNode)forged.path("comprobante")).put("firma",(firma.charAt(0)=='A'?"B":"A")+firma.substring(1));
        assertThrows(IllegalArgumentException.class,()->ack.verify(forged.toString()));
        // Payload en orden alfabetico (no el del record), aun firmado con la clave correcta.
        var sorted=new TreeMap<String,Object>(JSON.convertValue(root.path("registro"),Map.class));
        byte[] alpha=JSON.writeValueAsBytes(sorted);
        var s=Signature.getInstance("Ed25519");s.initSign(k.getPrivate());s.update(alpha);
        var reordered=root.deepCopy();var proof=(tools.jackson.databind.node.ObjectNode)reordered.path("comprobante");
        proof.put("payload",Base64.getUrlEncoder().withoutPadding().encodeToString(alpha));proof.put("firma",Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign()));
        assertThrows(IllegalArgumentException.class,()->ack.verify(reordered.toString()));
        // Otra clave no verifica; un par inconsistente no se acepta como clave de firma.
        assertThrows(IllegalArgumentException.class,()->ack(pair()).verify(receipt));
        assertThrows(IllegalStateException.class,()->new FormularioAck("SIGER_A_PRUEBA",k.getPrivate(),pair().getPublic()));
        assertThrows(IllegalStateException.class,()->new FormularioAck("clave con espacios",k.getPrivate(),k.getPublic()));
    }

    @Test void cargaDeClavesDesdeArchivosPem()throws Exception{
        var k=pair();Path dir=Files.createDirectories(Path.of("target","ack-"+UUID.randomUUID()));
        Path priv=dir.resolve("ack-private.pem"),pub=dir.resolve("ack-public.pem");
        Files.writeString(priv,"-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder().encodeToString(k.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n",StandardCharsets.US_ASCII);
        Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder().encodeToString(k.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n",StandardCharsets.US_ASCII);
        var ack=FormularioAck.load("SIGER_A_PRUEBA",priv,pub);
        var r=registro("FORMULARIO_WEB_NNA",Instant.parse("2026-11-04T12:00:00Z"));
        assertEquals(r,ack.verify(ack.sign(r)));
        assertFalse(ack.toString().contains(Base64.getMimeEncoder().encodeToString(k.getPrivate().getEncoded())));
        // Publica de otro par o archivos intercambiados: no arranca.
        Path other=dir.resolve("otra-public.pem");var o=pair();
        Files.writeString(other,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder().encodeToString(o.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n",StandardCharsets.US_ASCII);
        assertThrows(IllegalStateException.class,()->FormularioAck.load("SIGER_A_PRUEBA",priv,other));
        assertThrows(IllegalStateException.class,()->FormularioAck.load("SIGER_A_PRUEBA",pub,priv));
    }
}
