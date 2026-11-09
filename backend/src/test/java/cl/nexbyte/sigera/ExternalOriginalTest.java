package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/** Original de Comunicacion externa (Formulario completo via SMR) con los vectores sinteticos de SMR. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_external;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.formulario-trusted-keys="+ExternalOriginalTest.TRUST})
@ActiveProfiles("test")
class ExternalOriginalTest {
    static final String TRUST="src/test/resources/smr/claves-formulario-sinteticas.json";
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Autowired Workflow flow;@Autowired Db db;

    static String fixture(String version)throws Exception{
        try(var in=ExternalOriginalTest.class.getResourceAsStream("/smr/entrega-sintetica-"+version+".json")){
            return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    static Map<String,PublicKey> trust(){return new FormularioKeys(TRUST).keys();}
    static String custody(String document,String field,String value){
        var root=(ObjectNode)JSON.readTree(document);((ObjectNode)root.get("custodia")).put(field,value);return root.toString();
    }
    /** Vuelve a sellar el documento con una clave de prueba, aplicando un cambio al contenido firmado. */
    static String reseal(String document,KeyPair pair,UnaryOperator<String> edit)throws Exception{
        var root=(ObjectNode)JSON.readTree(document);var c=(ObjectNode)root.get("custodia");
        byte[] payload=edit.apply(new String(Base64.getUrlDecoder().decode(c.get("payload").asString()),StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        var signer=Signature.getInstance("Ed25519");signer.initSign(pair.getPrivate());signer.update(payload);
        c.put("keyId","PRUEBA");c.put("payload",Base64.getUrlEncoder().withoutPadding().encodeToString(payload));
        c.put("firma",Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        return root.toString();
    }
    static void rejected(String document,Map<String,PublicKey> trust,String reason){
        var p=assertThrows(Problem.class,()->ExternalOriginal.parse(document,trust));
        assertEquals(400,p.status);
        assertTrue(p.getMessage().contains(reason),p.getMessage());
    }
    Workflow.Result run(String user,String type,Map<String,Object> data){return flow.run(user,new Workflow.Command(UUID.randomUUID(),type,null,null,data));}

    @Test void huellasCoincidenConSmrYAlteracionSeRechaza()throws Exception{
        var o=ExternalOriginal.parse(fixture("1.1"),trust());
        assertEquals("bccac2446b7cd6f2a1d8af086622e46274aebf855dcefabd704252a138de10dc",o.contentHash());
        assertEquals("10000000-0000-4000-8000-000000000001",o.externalId());
        assertEquals("FORMULARIO_WEB_NNA",o.channel());
        assertEquals(Instant.parse("2026-11-04T12:00:00Z").toEpochMilli(),o.receivedMs());
        assertEquals("FORM_FIXTURE_PUBLIC",o.custodyKeyId());
        ExternalOriginal.parse(fixture("1.0"),trust());
        var tampered=fixture("1.1").replace("Relato sintetico completo","Relato alterado");
        rejected(tampered,trust(),"huellas");
        var duplicated=fixture("1.1").replaceFirst("\\{","{\"sha256\":\"x\",");
        rejected(duplicated,trust(),"contrato");
        // Ningun campo fuera de lo cubierto por el sello.
        rejected(fixture("1.1").replaceFirst("\\{","{\"nota\":\"sin firma\","),trust(),"contrato");
        rejected(custody(fixture("1.1"),"clavePublica","no se usa"),trust(),"contrato");
    }

    @Test void selloDeCustodiaExigeClaveConfiableFirmaValidaYCorrespondencia()throws Exception{
        String document=fixture("1.1");
        // Sin claves configuradas (valor por defecto) no se acepta ningun original.
        assertTrue(new FormularioKeys("").keys().isEmpty());
        rejected(document,Map.of(),"clave confiable");
        rejected(document,null,"clave confiable");
        // keyId desconocido: el documento no puede elegir otra clave ni aportar la suya.
        rejected(custody(document,"keyId","OTRA_CLAVE"),trust(),"clave confiable");
        // Mismo keyId asociado por el operador a otra clave: la firma no verifica.
        var other=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        rejected(document,Map.of("FORM_FIXTURE_PUBLIC",other.getPublic()),"firma");
        // Firma alterada o ilegible.
        String firma=JSON.readTree(document).path("custodia").path("firma").asString();
        rejected(custody(document,"firma",(firma.charAt(0)=='A'?"B":"A")+firma.substring(1)),trust(),"firma");
        rejected(custody(document,"firma","%%%"),trust(),"firma");
        // Sello autentico de otro documento (1.0, mismo id): firma valida, huellas distintas.
        String foreign=JSON.readTree(fixture("1.0")).path("custodia").toString();
        var swapped=(ObjectNode)JSON.readTree(document);swapped.set("custodia",JSON.readTree(foreign));
        rejected(swapped.toString(),trust(),"no corresponde a este documento");

        // Sellos correctamente firmados con una clave confiable pero cuyo contenido no corresponde.
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var lab=Map.of("PRUEBA",pair.getPublic());
        assertEquals("PRUEBA",ExternalOriginal.parse(reseal(document,pair,p->p),lab).custodyKeyId());
        rejected(reseal(document,pair,p->p.replace("2026-11-04T12:00:00Z","2026-11-04T12:00:01Z")),lab,"no corresponde");
        rejected(reseal(document,pair,p->p.replace("10000000-0000-4000-8000-000000000001","10000000-0000-4000-8000-000000000002")),lab,"no corresponde");
        rejected(reseal(document,pair,p->p.replace("NNA_LAB_1","NNA_LAB_2")),lab,"no corresponde");
        rejected(reseal(document,pair,p->p.replace("FORMULARIO_PUBLICO_DDN","OTRO_EMISOR")),lab,"no corresponde");
        rejected(reseal(document,pair,p->p.replace("CUSTODIA_PUBLICA","OTRA")),lab,"no corresponde");
        rejected(reseal(document,pair,p->p.replace("4fd1dd38","4fd1dd39")),lab,"no corresponde");
        rejected(reseal(document,pair,p->p.replace("}",",\"extra\":\"x\"}")),lab,"no corresponde");
        rejected(reseal(document,pair,p->p.replace(",",", ")),lab,"no corresponde");

        // Un archivo de claves configurado pero invalido detiene el arranque (no degrada a sin verificacion).
        assertThrows(IllegalStateException.class,()->new FormularioKeys("src/test/resources/smr/no-existe.json"));
        assertThrows(IllegalStateException.class,()->new FormularioKeys("src/test/resources/smr/entrega-sintetica-1.1.json"));
    }

    long staff(String username,long scope){return db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'test-non-login',?,FALSE)",username,username,scope);}
    void grant(long user,String role,Long scope){db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,?,?)",user,role,scope,0,"PRUEBA AUTOMATICA");}
    /** Ninguna sesion funcionaria puede invocarlo por /api/commands (Workflow.run): se rechaza sin escribir nada. */
    void rejectedAsCommand(String user,long scope,String document){
        long coms=db.count("SELECT COUNT(*) FROM communication"),originals=db.count("SELECT COUNT(*) FROM communication_original");
        var cmd=new Workflow.Command(UUID.randomUUID(),"COM_EXTERNAL",null,null,Map.of("scope_id",scope,"document",document));
        assertEquals(400,assertThrows(Problem.class,()->flow.run(user,cmd)).status);
        assertEquals(coms,db.count("SELECT COUNT(*) FROM communication"));
        assertEquals(originals,db.count("SELECT COUNT(*) FROM communication_original"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM command_log WHERE command_key=?",cmd.key().toString()));
    }

    @Test void conservaOriginalIntegroIdempotenteSinSolReqNiCaso()throws Exception{
        db.update("INSERT INTO org_unit VALUES('EXT','Unidad externa de prueba')");
        long scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('EXT-TEST','Area externa prueba','EXT','TEST','CENTRAL')");
        long registrar=staff("registro.ext",scope);
        grant(registrar,"ADMINISTRATIVO",scope);
        long broad=staff("amplio.ext",scope);
        for(String role:List.of("ADMINISTRATIVO","PROFESIONAL","JEFATURA","DIRECCION","AUTORIDAD"))grant(broad,role,scope);
        for(String role:List.of("DISTRIBUIDOR","TI","AUDITORIA"))grant(broad,role,null);

        // Quien puede registrar COM manualmente (COM_CREATE) no puede ingresar un original externo.
        long manual=run("registro.ext","COM_CREATE",Map.of("scope_id",scope,"channel","TELEFONO","received_at",Instant.now().minusSeconds(60).toString(),"subject","Ingreso manual","body","Relato sintetico")).id();
        String manualFolio=s(db.one("SELECT folio FROM communication WHERE id=?",manual),"folio");
        String document=fixture("1.1");
        rejectedAsCommand("registro.ext",scope,document);
        rejectedAsCommand("amplio.ext",scope,document);
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE action='COM_ORIGINAL_CONSERVADO'"));

        // Sin sello autentico no se crea COM ni original ni auditoria.
        long comsBefore=db.count("SELECT COUNT(*) FROM communication");
        assertEquals(400,assertThrows(Problem.class,()->flow.conserveExternal("registro.ext",scope,custody(document,"keyId","OTRA_CLAVE"))).status);
        assertEquals(comsBefore,db.count("SELECT COUNT(*) FROM communication"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM communication_original"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE action='COM_ORIGINAL_CONSERVADO'"));

        // Persistencia interna (futuro receptor tecnico autorizado).
        long com=flow.conserveExternal("registro.ext",scope,document).id();

        var c=db.one("SELECT * FROM communication WHERE id=?",com);
        assertEquals(String.format(Locale.ROOT,"COM-A-%06d",com),s(c,"folio"));
        assertEquals(manualFolio,s(db.one("SELECT folio FROM communication WHERE id=?",manual),"folio"));
        assertEquals("FORMULARIO_PUBLICO_DDN",s(c,"external_source"));
        assertEquals("10000000-0000-4000-8000-000000000001",s(c,"external_id"));
        assertEquals("FORMULARIO_WEB_NNA",s(c,"channel"));
        assertEquals("NUEVA",s(c,"state"));
        // Presentador proyectado con su procedencia; el vector NNA no trae RUN, asi que no hay identificacion.
        assertEquals("PERSONA SINTETICA (declarado)",s(c,"presenter"));
        assertNull(c.get("identity_document"));
        assertNull(c.get("contact"));
        var o=db.one("SELECT * FROM communication_original WHERE communication_id=?",com);
        assertEquals(document,s(o,"document"));
        assertEquals(ExternalOriginal.sha256(document.getBytes(StandardCharsets.UTF_8)),s(o,"document_sha256"));
        assertEquals("4fd1dd380ac13cf8b1eaba03e8212be44711560cdb35405a32baf07340e30dc7",s(o,"provenance_hash"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",com));
        assertEquals(0,db.count("SELECT COUNT(*) FROM requirement WHERE communication_id=?",com));
        assertEquals(0,db.count("SELECT COUNT(*) FROM case_record c JOIN requirement r ON r.id=c.requirement_id WHERE r.communication_id=?",com));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='COM_ORIGINAL_CONSERVADO' AND resource_id=?",com));

        // Mismo original repetido o reserializado: misma COM, sin nueva auditoria, y el texto conservado no cambia.
        String compact=JsonMapper.builder().build().readTree(document).toString();
        assertEquals(com,flow.conserveExternal("registro.ext",scope,document).id());
        assertEquals(com,flow.conserveExternal("registro.ext",scope,compact).id());
        assertEquals(document,s(db.one("SELECT document FROM communication_original WHERE communication_id=?",com),"document"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='COM_ORIGINAL_CONSERVADO' AND resource_id=?",com));
        // Un reenvio no vuelve a proyectar sobre una COM ya registrada (por ejemplo, corregida despues).
        db.update("UPDATE communication SET presenter='Correccion posterior' WHERE id=?",com);
        assertEquals(com,flow.conserveExternal("registro.ext",scope,document).id());
        assertEquals("Correccion posterior",s(db.one("SELECT presenter FROM communication WHERE id=?",com),"presenter"));
        // Reenvio del mismo original con la firma alterada: se rechaza antes de la idempotencia, no devuelve la COM.
        String firma=JSON.readTree(document).path("custodia").path("firma").asString();
        String forged=custody(document,"firma",(firma.charAt(0)=='A'?"B":"A")+firma.substring(1));
        assertEquals(400,assertThrows(Problem.class,()->flow.conserveExternal("registro.ext",scope,forged)).status);
        // Mismo identificador de origen con otro contenido: conflicto, sin nada nuevo.
        long before=db.count("SELECT COUNT(*) FROM communication");
        assertEquals(409,assertThrows(Problem.class,()->flow.conserveExternal("registro.ext",scope,fixture("1.0"))).status);
        assertEquals(before,db.count("SELECT COUNT(*) FROM communication"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM communication_original"));
        // Con el original ya conservado, el comando sigue rechazado y no revela la COM existente.
        rejectedAsCommand("registro.ext",scope,document);
    }
}
