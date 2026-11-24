package cl.nexbyte.sigera;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioSrceiContractTest.*;

/**
 * Contrato del Formulario Publico (0.8.0, gate E00), camino de la recepcion PULL/ACK: los vectores NNA con RUN de
 * fuente SRCEI pasan por FormularioConsumer y FormularioReception contra el productor SINTETICO de
 * FormularioConsumerTest (loopback, sin TLS, con las reglas de ACK del contrato), con la clave publica de prueba del
 * Formulario. COMMIT antes del ACK (version 1.1 por canal NNA), procedencia sin elevar, original del 1.1 custodiado con
 * su SHA-256 y una nueva entrega del mismo documento sin reproyectar. No es un PULL real.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:srcei_recepcion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.data=./target/srcei-recepcion-${random.uuid}",
    "siger.folio-prefix=COMA",
    "siger.formulario-trusted-keys=src/test/resources/formulario/public-nna-srcei-test.json"})
@ActiveProfiles("test")
class FormularioSrceiReceptionTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    /**
     * Adjunto del vector 1.1: los 45 bytes de NnaSrceiFixtures.original() del Formulario (no es un archivo del
     * repositorio del Formulario). Incrustado para que el fin de linea no altere su SHA-256.
     */
    static final byte[] ORIGINAL="Original exclusivamente sintetico NNA SRCeI.\n".getBytes(StandardCharsets.US_ASCII);
    @Autowired Db db;@Autowired FormularioReception reception;@Autowired Documents documents;@Autowired FormularioKeys keys;@Autowired Access access;

    @Test void vectoresSrceiSeRegistranSinAutenticarSeConfirmanYUnaNuevaEntregaNoReproyecta()throws Exception{
        // Adjunto: 45 bytes con la huella registrada en PROCEDENCIA.txt y en el descriptor firmado del vector 1.1.
        assertEquals(45,ORIGINAL.length);
        assertEquals(provenance().get("adjunto-v1.1"),ExternalOriginal.sha256(ORIGINAL));
        var descriptor=JSON.readTree(vector("formulario-nna-srcei-v1.1.json")).path("comunicacion").path("adjuntos").get(0);
        assertEquals(ExternalOriginal.sha256(ORIGINAL),descriptor.path("sha256").asString());assertEquals(45,descriptor.path("tamanio").asInt());

        db.update("INSERT INTO org_unit VALUES('SRR','Unidad receptora SRCeI PULL')");
        long scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('SRR-REC','Recepcion SRCeI PULL','SRR','REC','CENTRAL')");
        var ackKeys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var ack=new FormularioAck("SIGER_A_PRUEBA",ackKeys.getPrivate(),ackKeys.getPublic());
        try(var producer=new FormularioConsumerTest.Producer(ackKeys.getPublic())){
            var consumer=new FormularioConsumer(new FormularioClient(URI.create(producer.origin()),FormularioConsumerTest.TOKEN,null,true),reception,documents,keys,ack,scope);
            for(String name:VECTORS){
                String document=vector(name);
                var com0=JSON.readTree(document).path("comunicacion");
                String operation=com0.path("id").asString();
                boolean withFile=name.contains("v1.1");
                producer.offer(document,withFile?Map.of(com0.path("adjuntos").get(0).path("id").asString(),ORIGINAL):Map.of());
                var cycle=consumer.run();
                assertTrue(cycle.clean(),name+" "+cycle);
                assertEquals(List.of(1,1,1),List.of(cycle.claimed(),cycle.registered(),cycle.confirmed()),name);
                var r=db.one("SELECT * FROM formulario_reception WHERE operation_id=?",operation);
                assertEquals("CONFIRMADA",s(r,"state"));assertFalse(b(r,"ack_pending"));
                long com=n(r,"communication_id");
                assertSrcei(db,access,com,document);
                // ACK unico tras el COMMIT, version 1.1 por canal NNA, con las huellas del documento.
                var registro=JSON.readTree(producer.acks.getLast()).path("recepcion").path("registro");
                assertEquals("1.1",registro.path("version").asString());assertEquals("FORMULARIO_WEB_NNA",registro.path("canalOrigen").asString());
                assertEquals(com,registro.path("numeroFolio").asLong());assertEquals(operation,registro.path("operacionPublicaId").asString());
                assertEquals(JSON.readTree(document).path("sha256").asString(),registro.path("sha256").asString());
                assertEquals(JSON.readTree(document).path("huellaProcedencia").asString(),registro.path("huellaProcedencia").asString());
                // Original del 1.1 custodiado en cuarentena con sus bytes y su SHA-256; el 1.0 no tiene adjuntos.
                var files=db.rows("SELECT d.* FROM document_item d JOIN external_document x ON x.document_id=d.id WHERE d.communication_id=?",com);
                assertEquals(withFile?1:0,files.size(),name);
                if(withFile){
                    var d=files.getFirst();
                    assertTrue(b(d,"quarantined"));assertEquals("INGRESO",s(d,"document_type"));assertEquals("sintetico.txt",s(d,"name"));
                    byte[] stored=Files.readAllBytes(documents.directory.resolve(s(d,"storage_key")));
                    assertArrayEquals(ORIGINAL,stored);assertEquals(provenance().get("adjunto-v1.1"),ExternalOriginal.sha256(stored));
                }
                // Nueva entrega del mismo documento: se confirma con el mismo comprobante, sin otra COM ni reproyeccion.
                var before=snapshot(db,com);
                int acks=producer.acks.size(),downloads=producer.downloads.get();
                producer.offer(document,withFile?Map.of(com0.path("adjuntos").get(0).path("id").asString(),ORIGINAL):Map.of());
                var again=consumer.run();
                assertTrue(again.clean(),name+" "+again);
                assertEquals(List.of(1,0,1),List.of(again.claimed(),again.registered(),again.confirmed()),name);
                assertEquals(before,snapshot(db,com),name);
                assertEquals(acks+1,producer.acks.size());assertEquals(downloads,producer.downloads.get());
                assertEquals(JSON.readTree(producer.acks.get(acks-1)).path("recepcion"),JSON.readTree(producer.acks.getLast()).path("recepcion"));
            }
        }
    }
}
