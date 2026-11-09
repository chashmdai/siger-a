package cl.nexbyte.sigera;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;

/**
 * Ciclo del consumidor contra un productor SINTETICO local (loopback, sin TLS) que aplica las reglas del contrato:
 * Bearer, versiones, reserva, descarga, verificacion del ACK como ComprobanteInstitucional y fallo. H2 aislado.
 * No es un PULL real ni acredita la conexion con el Formulario oficial.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_formulario_pull;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.data=./target/formulario-pull-${random.uuid}",
    "siger.folio-prefix=COMA"})
@ActiveProfiles("test")
class FormularioConsumerTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String TOKEN="token-sintetico-de-prueba-0123456789abcdef";
    @Autowired Db db;@Autowired FormularioReception reception;@Autowired Documents documents;@Autowired FormularioKeys keys;
    @Autowired Access access;@Autowired UserDetailsService users;@Autowired ApplicationContext context;@Autowired Personnel personnel;
    @Autowired Integrations integrations;

    static KeyPair ackKeys;static FormularioAck ack;static long scope;
    Producer producer;FormularioConsumer consumer;

    @BeforeAll static void keys()throws Exception{ackKeys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();ack=new FormularioAck("SIGER_A_PRUEBA",ackKeys.getPrivate(),ackKeys.getPublic());}
    @BeforeEach void start()throws IOException{
        if(scope==0){
            db.update("INSERT INTO org_unit VALUES('FRM','Unidad receptora Formulario')");
            scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('FRM-REC','Recepcion Formulario','FRM','REC','CENTRAL')");
        }
        producer=new Producer(ackKeys.getPublic());
        consumer=consumer(producer.origin(),TOKEN);
    }
    @AfterEach void stop(){producer.close();}
    FormularioConsumer consumer(String origin,String token){
        return new FormularioConsumer(new FormularioClient(URI.create(origin),token,null,true),reception,documents,keys,ack,scope);
    }
    long count(String sql,Object... v){return db.count(sql,v);}

    // --- Documentos sinteticos -------------------------------------------------------------------
    /**
     * original-sintetico.txt del vector 1.1 (formulario-web/docs/smr-ejemplos), 44 bytes con LF final. Incrustado para
     * que la conversion de fin de linea de Git no altere su SHA-256.
     */
    static byte[] original(){return "Original sintetico para prueba offline SMR.\n".getBytes(StandardCharsets.US_ASCII);}
    /** Adulto ClaveUnica (canal publico) con un original adjunto; documento 1.1. */
    static String adultWithFile(byte[] content){
        var c=cuVulneration();c.put("version","1.1");
        String id=UUID.randomUUID().toString();
        c.withObjectProperty("datos").withArrayProperty("adjuntos").add(id);
        var d=c.withArrayProperty("adjuntos").addObject();
        d.put("id",id);d.put("nombre","../informe ciudadano.pdf");d.put("tamanio",content.length);d.put("sha256",ExternalOriginal.sha256(content));d.put("tipo","application/pdf");d.put("estado","VALIDADO");
        return sign(c);
    }
    /** Vector NNA 1.0 del productor con otro identificador, resellado con la clave de prueba PRUEBA_SIGER. */
    static String nna10()throws IOException{
        var c=(ObjectNode)JSON.readTree(Files.readString(Path.of("src/test/resources/smr/entrega-sintetica-1.0.json"))).path("comunicacion");
        c.put("id",UUID.randomUUID().toString());
        return sign(c);
    }
    static String operation(String document){return JSON.readTree(document).path("comunicacion").path("id").asString();}
    static String attachment(String document){return JSON.readTree(document).path("comunicacion").path("adjuntos").get(0).path("id").asString();}

    // --- Pruebas -----------------------------------------------------------------------------------

    @Test void inactivoSinConfiguracionExplicita(){
        assertTrue(context.getBeanProvider(FormularioConsumer.class).stream().findAny().isEmpty());
        assertTrue(context.getBeanProvider(FormularioPull.FormularioRunOnce.class).stream().findAny().isEmpty());
        assertTrue(context.getBeanProvider(FormularioPull.class).stream().findAny().isEmpty());
    }

    @Test void activacionExigeConfiguracionCompletaYCompatible()throws Exception{
        Path dir=Files.createDirectories(Path.of("target","pull-config-"+UUID.randomUUID()));
        Path priv=dir.resolve("ack-private.pem"),pub=dir.resolve("ack-public.pem"),token=dir.resolve("token.txt");
        Files.writeString(priv,"-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder().encodeToString(ackKeys.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder().encodeToString(ackKeys.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        Files.writeString(token,TOKEN+"\n");
        java.util.function.Supplier<org.springframework.mock.env.MockEnvironment> complete=()->new org.springframework.mock.env.MockEnvironment()
            .withProperty("siger.folio-prefix","COMA").withProperty("siger.formulario.pull.scope","FRM-REC")
            .withProperty("siger.formulario.ack.key-id","SIGER_A_PRUEBA").withProperty("siger.formulario.ack.private-key-file",priv.toString())
            .withProperty("siger.formulario.ack.public-key-file",pub.toString()).withProperty("siger.formulario.pull.origin",producer.origin())
            .withProperty("siger.formulario.pull.token-file",token.toString()).withProperty("siger.formulario.pull.loopback-http","true")
            .withProperty("siger.formulario.pull.run-once","true");
        var config=new FormularioPull();
        assertNotNull(config.formularioConsumer(complete.get(),reception,documents,keys,db));
        // Identidad ACK reutilizada (descifrada de DPAPI por el lanzador) como texto PEM en variables del proceso.
        var fromText=complete.get().withProperty("siger.formulario.ack.private-key-file","").withProperty("siger.formulario.ack.public-key-file","")
            .withProperty("siger.formulario.ack.private-key",Files.readString(priv).lines().collect(java.util.stream.Collectors.joining(new String(new char[]{13,10})))).withProperty("siger.formulario.ack.public-key",Files.readString(pub));
        assertNotNull(config.formularioConsumer(fromText,reception,documents,keys,db));
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(fromText.withProperty("siger.formulario.ack.public-key",Files.readString(priv)),reception,documents,keys,db));
        // Exactamente un modo: ni ninguno ni ciclo manual y automatico a la vez.
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get().withProperty("siger.formulario.pull.run-once","false"),reception,documents,keys,db));
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get().withProperty("siger.formulario.pull.automatic","true"),reception,documents,keys,db));
        // Prefijo de folio por defecto (COM-A) incompatible con el folio del comprobante del Formulario.
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get().withProperty("siger.folio-prefix","COM-A"),reception,documents,keys,db));
        // Sin claves de custodia confiables, ambito inexistente, credencial ausente u origen sin TLS: no se crea.
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get(),reception,documents,new FormularioKeys(""),db));
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get().withProperty("siger.formulario.pull.scope","NO-EXISTE"),reception,documents,keys,db));
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get().withProperty("siger.formulario.pull.token-file",dir.resolve("falta.txt").toString()),reception,documents,keys,db));
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get().withProperty("siger.formulario.pull.loopback-http","false"),reception,documents,keys,db));
        assertThrows(IllegalStateException.class,()->config.formularioConsumer(complete.get().withProperty("siger.formulario.ack.public-key-file",priv.toString()),reception,documents,keys,db));
        // El ciclo manual exige proceso sin tareas programadas.
        var made=config.formularioConsumer(complete.get(),reception,documents,keys,db);
        var ctx=new org.springframework.context.support.GenericApplicationContext();
        assertThrows(IllegalStateException.class,()->config.formularioRunOnce(made,ctx,new org.springframework.mock.env.MockEnvironment()));
        assertNotNull(config.formularioRunOnce(made,ctx,new org.springframework.mock.env.MockEnvironment().withProperty("siger.scheduling","false")));
        assertEquals(0,producer.requests.get()); // configurar no contacta el origen
    }

    @Test void vectorNna11DelProductorRegistraCustodiaYConfirmaConAck11()throws Exception{
        String document=Files.readString(Path.of("src/test/resources/smr/entrega-sintetica-1.1.json"));
        producer.offer(document,Map.of(attachment(document),original()));
        var cycle=consumer.run();
        assertTrue(cycle.clean(),cycle.toString());
        assertEquals(List.of(1,1,1),List.of(cycle.claimed(),cycle.registered(),cycle.confirmed()));
        var r=db.one("SELECT * FROM formulario_reception WHERE operation_id=?",operation(document));
        assertEquals("CONFIRMADA",s(r,"state"));assertFalse(b(r,"ack_pending"));
        long com=n(r,"communication_id");
        var c=db.one("SELECT * FROM communication WHERE id=?",com);
        assertEquals("NUEVA",s(c,"state"));assertEquals("FORMULARIO_WEB_NNA",s(c,"channel"));
        assertEquals(String.format(Locale.ROOT,"COMA-%06d",com),s(c,"folio"));assertEquals(s(c,"folio"),s(r,"folio"));
        // Sin SOL, REQ ni Caso.
        assertEquals(0,count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",com));
        assertEquals(0,count("SELECT COUNT(*) FROM requirement WHERE communication_id=?",com));
        // Original en cuarentena, ligado a la COM, con clave derivada de UUID (no del nombre) y bytes integros.
        var d=db.one("SELECT d.*,x.media_type,x.attachment_id FROM document_item d JOIN external_document x ON x.document_id=d.id WHERE d.communication_id=?",com);
        assertTrue(b(d,"quarantined"));assertEquals("INGRESO",s(d,"document_type"));assertEquals("original-sintetico.txt",s(d,"name"));
        assertEquals("text/plain",s(d,"media_type"));assertEquals("application/octet-stream",s(d,"content_type"));
        assertEquals(Documents.externalKey(operation(document),attachment(document)),s(d,"storage_key"));
        assertArrayEquals(original(),Files.readAllBytes(documents.directory.resolve(s(d,"storage_key"))));
        try(var files=Files.list(documents.directory)){assertTrue(files.noneMatch(p->p.toString().endsWith(".tmp")));}
        // ACK unico, version 1.1 por canal NNA (documento 1.1), aceptado por las reglas del productor.
        assertEquals(1,producer.acks.size());
        var registro=JSON.readTree(producer.acks.getFirst()).path("recepcion").path("registro");
        assertEquals("1.1",registro.path("version").asString());assertEquals("FORMULARIO_WEB_NNA",registro.path("canalOrigen").asString());
        assertEquals(com,registro.path("numeroFolio").asLong());assertEquals(s(r,"institutional_id"),registro.path("comunicacionId").asString());
        assertNotEquals(operation(document),registro.path("comunicacionId").asString());
        assertEquals(JSON.readTree(document).path("sha256").asString(),registro.path("sha256").asString());
        // Identidad tecnica: registra y queda en la historia, pero no inicia sesion ni se resuelve como funcionario.
        var t=db.one("SELECT * FROM staff WHERE username=?",FormularioReception.TECHNICAL);
        assertTrue(b(t,"technical"));assertFalse(b(t,"enabled"));assertEquals(n(t,"id"),n(c,"created_by"));assertEquals(n(t,"id"),n(d,"uploaded_by"));
        assertEquals(0,count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",n(t,"id")));
        assertThrows(UsernameNotFoundException.class,()->users.loadUserByUsername(FormularioReception.TECHNICAL));
        assertEquals(404,assertThrows(Problem.class,()->access.actor(FormularioReception.TECHNICAL)).status);
        assertTrue(access.enabledActors().stream().noneMatch(a->a.id()==n(t,"id")));
        // TI no la habilita ni le repone clave; una autoridad no le otorga funciones.
        long ti=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES('ti.formulario','TI prueba','test-non-login',?,FALSE)",scope);
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,0,'PRUEBA')",ti);
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'AUTORIDAD',?,0,'PRUEBA')",ti,scope);
        var admin=access.actor("ti.formulario");long tech=n(t,"id");
        assertEquals(404,assertThrows(Problem.class,()->personnel.run(admin,"STAFF_UPDATE",tech,null,new Input(Map.of("name","x","enabled",true,"reason","Prueba de resguardo")))).status);
        assertEquals(404,assertThrows(Problem.class,()->personnel.run(admin,"STAFF_RESET",tech,null,new Input(Map.of("password","Clave-Temporal-2026!","reason","Prueba de resguardo")))).status);
        assertThrows(Problem.class,()->personnel.run(admin,"GRANT_CREATE",0,null,new Input(Map.of("scope_id",scope,"user_id",tech,"role","PROFESIONAL","from_at",Instant.now().toString(),"reason","Prueba de resguardo"))));
        assertFalse(b(db.one("SELECT enabled FROM staff WHERE id=?",tech),"enabled"));
        assertEquals("!",s(db.one("SELECT password_hash FROM staff WHERE id=?",tech),"password_hash"));
        assertEquals(0,count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",tech));
        assertEquals(1,count("SELECT COUNT(*) FROM audit_event WHERE action='COM_REGISTRO_CONFIRMADO_FORMULARIO' AND resource_id=?",com));
        assertEquals(0,count("SELECT COUNT(*) FROM audit_event WHERE note LIKE '%PERSONA%' OR note LIKE '%original-sintetico%'"));
        // Ciclo siguiente: cola vacia, nada nuevo.
        var again=consumer.run();
        assertTrue(again.clean());assertEquals(0,again.claimed());assertEquals(1,producer.acks.size());
    }

    @Test void documentoNna10RecibeAck11YAdulto11RecibeAck10()throws Exception{
        String nna=nna10();producer.offer(nna,Map.of());
        {var cy=consumer.run();assertTrue(cy.clean(),cy.toString());}
        assertEquals("1.1",JSON.readTree(producer.acks.getLast()).path("recepcion").path("registro").path("version").asString());
        String adult=adultWithFile(original());producer.offer(adult,Map.of(attachment(adult),original()));
        {var cy=consumer.run();assertTrue(cy.clean(),cy.toString());}
        var registro=JSON.readTree(producer.acks.getLast()).path("recepcion").path("registro");
        assertEquals("1.0",registro.path("version").asString());assertEquals("FORMULARIO_WEB_PUBLICO",registro.path("canalOrigen").asString());
        // El nombre ciudadano con separadores no forma rutas: el archivo usa la clave derivada.
        long com=n(db.one("SELECT communication_id FROM formulario_reception WHERE operation_id=?",operation(adult)),"communication_id");
        assertEquals("../informe ciudadano.pdf",s(db.one("SELECT name FROM document_item WHERE communication_id=?",com),"name"));
        assertTrue(Files.isRegularFile(documents.directory.resolve(Documents.externalKey(operation(adult),attachment(adult)))));
    }

    @Test void respuestaDelAckPerdidaSeConciliaConElMismoComprobante()throws Exception{
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        producer.ackOutcome.add(500); // el productor acepta el ACK pero la respuesta no llega como 204
        var first=consumer.run();
        assertEquals(1,first.registered());assertEquals(0,first.confirmed());assertFalse(first.clean());
        var r=db.one("SELECT * FROM formulario_reception WHERE operation_id=?",operation(doc));
        assertEquals("REGISTRADA",s(r,"state"));assertTrue(b(r,"ack_pending"));assertEquals("ACK_DIFERIDO_500",s(r,"last_error"));
        db.update("UPDATE formulario_reception SET ack_next_ms=0 WHERE id=?",n(r,"id")); // sin esperar el retroceso
        var second=consumer.run();
        assertEquals(1,second.reconciled());assertEquals(0,second.claimed());
        assertEquals("CONFIRMADA",s(db.one("SELECT state FROM formulario_reception WHERE id=?",n(r,"id")),"state"));
        // Mismo ACK byte a byte, una sola COM y un solo original.
        assertEquals(2,producer.acks.size());assertEquals(producer.acks.get(0),producer.acks.get(1));
        assertEquals(1,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
        assertEquals(1,count("SELECT COUNT(*) FROM document_item WHERE communication_id=?",n(r,"communication_id")));
    }

    @Test void reservaVencidaAntesDelAckEsperaLaReentregaYReutilizaElRegistro()throws Exception{
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        producer.expireBeforeAck=true; // la reserva vence entre el COMMIT y el ACK: 409
        var first=consumer.run();
        assertEquals(1,first.registered());assertEquals(0,first.confirmed());
        var r=db.one("SELECT * FROM formulario_reception WHERE operation_id=?",operation(doc));
        assertTrue(r.get("lease_id")==null);assertEquals("ACK_RESERVA_NO_VIGENTE",s(r,"last_error"));
        String receipt=s(r,"receipt"),institutional=s(r,"institutional_id");long com=n(r,"communication_id");
        int downloads=producer.downloads.get();
        // Reentrega con nueva reserva e intento: no hay otra COM ni otro comprobante; se confirma con la nueva reserva.
        var second=consumer.run();
        assertTrue(second.clean(),second.toString());assertEquals(1,second.claimed());assertEquals(0,second.registered());assertEquals(1,second.confirmed());
        var after=db.one("SELECT * FROM formulario_reception WHERE id=?",n(r,"id"));
        assertEquals("CONFIRMADA",s(after,"state"));assertEquals(receipt,s(after,"receipt"));assertEquals(institutional,s(after,"institutional_id"));
        assertEquals(com,n(after,"communication_id"));assertEquals(2,n(after,"attempt"));
        assertEquals(1,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
        assertEquals(downloads,producer.downloads.get()); // original ya custodiado: no se vuelve a descargar
        assertEquals(JSON.readTree(producer.acks.getFirst()).path("recepcion"),JSON.readTree(producer.acks.getLast()).path("recepcion"));
        assertEquals(1,count("SELECT COUNT(*) FROM audit_event WHERE action='COM_ORIGINAL_CONSERVADO' AND resource_id=?",com));
    }

    @Test void originalAlteradoEnTransitoNoRegistraYSeInformaTransitorio()throws Exception{
        String doc=adultWithFile(original());
        byte[] altered=original();altered[0]^=1;
        producer.offer(doc,Map.of(attachment(doc),altered));
        long coms=count("SELECT COUNT(*) FROM communication"),docs=count("SELECT COUNT(*) FROM document_item");
        var cycle=consumer.run();
        assertEquals(0,cycle.registered());assertEquals(List.of("ADJUNTO_NO_CUSTODIADO"),cycle.incidents());
        assertEquals(List.of("TRANSITORIO"),producer.failures);assertTrue(producer.acks.isEmpty());
        assertEquals(coms,count("SELECT COUNT(*) FROM communication"));assertEquals(docs,count("SELECT COUNT(*) FROM document_item"));
        assertFalse(Files.exists(documents.directory.resolve(Documents.externalKey(operation(doc),attachment(doc)))));
        try(var files=Files.list(documents.directory)){assertTrue(files.noneMatch(p->p.toString().endsWith(".tmp")));}
        // Mayor que el tamano firmado: se corta sin publicar.
        producer.files.put(attachment(doc),Arrays.copyOf(original(),original().length+10));
        var bigger=consumer.run();
        assertEquals(0,bigger.registered());assertTrue(bigger.incidents().contains("ADJUNTO_LONGITUD_DISTINTA"),bigger.toString());
        // Con el original correcto, el siguiente intento registra una sola vez.
        producer.files.put(attachment(doc),original());
        {var cy=consumer.run();assertTrue(cy.clean(),cy.toString());}
        assertEquals(1,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
    }

    @Test void contratoInvalidoOClaveNoConfiableNoCreanComYSeInformanSegunSuCausa()throws Exception{
        long coms=count("SELECT COUNT(*) FROM communication");
        // Firma alterada: CONTRATO_INVALIDO (cuarentena en el productor); queda solo el identificador y el codigo.
        String doc=nna10();var root=(ObjectNode)JSON.readTree(doc);String firma=root.path("custodia").path("firma").asString();
        ((ObjectNode)root.path("custodia")).put("firma",(firma.charAt(0)=='A'?"B":"A")+firma.substring(1));
        producer.offer(root.toString(),Map.of());
        assertEquals(List.of("CONTRATO_INVALIDO"),consumer.run().incidents());
        assertEquals(List.of("CONTRATO_INVALIDO"),producer.failures);
        var r=db.one("SELECT * FROM formulario_reception WHERE operation_id=?",operation(doc));
        assertEquals("RECHAZADA",s(r,"state"));assertNull(r.get("receipt"));assertNull(r.get("communication_id"));
        // Clave de custodia no instalada en SIGER A: problema propio, RECEPTOR_NO_DISPONIBLE (reintento, no cuarentena).
        String other=nna10();var unknown=(ObjectNode)JSON.readTree(other);((ObjectNode)unknown.path("custodia")).put("keyId","CLAVE_NO_INSTALADA");
        producer.offer(unknown.toString(),Map.of());
        assertEquals(List.of("CLAVE_CUSTODIA_NO_CONFIABLE"),consumer.run().incidents());
        assertEquals(List.of("CONTRATO_INVALIDO","RECEPTOR_NO_DISPONIBLE"),producer.failures);
        assertEquals(coms,count("SELECT COUNT(*) FROM communication"));
        assertTrue(producer.acks.isEmpty());
    }

    @Test void custodiaAlteradaDespuesDelCommitNoSeConfirma()throws Exception{
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        producer.ackOutcome.add(503);
        consumer.run();
        var r=db.one("SELECT * FROM formulario_reception WHERE operation_id=?",operation(doc));
        Path file=documents.directory.resolve(Documents.externalKey(operation(doc),attachment(doc)));
        byte[] bytes=Files.readAllBytes(file);bytes[1]^=1;Files.write(file,bytes);
        db.update("UPDATE formulario_reception SET ack_next_ms=0 WHERE id=?",n(r,"id"));
        int acks=producer.acks.size();
        var cycle=consumer.run();
        assertEquals(List.of("CUSTODIA_NO_VERIFICADA"),cycle.incidents());
        assertEquals(acks,producer.acks.size());
        var after=db.one("SELECT * FROM formulario_reception WHERE id=?",n(r,"id"));
        assertTrue(b(after,"ack_blocked"));assertTrue(b(after,"ack_pending"));assertEquals("CUSTODIA_NO_VERIFICADA",s(after,"last_error"));
    }

    @Test void registroFallidoNoDejaComYReutilizaElOriginalCustodiado()throws Exception{
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        db.update("UPDATE org_scope SET enabled=FALSE WHERE id=?",scope); // fallo local antes del COMMIT
        try{
            var cycle=consumer.run();
            assertEquals(0,cycle.registered());assertEquals(List.of("RECEPTOR_NO_DISPONIBLE"),producer.failures);
            assertEquals(0,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
            assertEquals(0,count("SELECT COUNT(*) FROM formulario_reception WHERE operation_id=?",operation(doc)));
        }finally{db.update("UPDATE org_scope SET enabled=TRUE WHERE id=?",scope);}
        assertEquals(1,producer.downloads.get());
        {var cy=consumer.run();assertTrue(cy.clean(),cy.toString());}
        assertEquals(1,producer.downloads.get());
        assertEquals(1,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
    }

    // --- Consumidor automatico permanente (mismo ciclo, con lease propio, pausa, espera y latido) -------------
    FormularioAutomatic automatic(FormularioConsumer c){return new FormularioAutomatic(c,reception,integrations,1_000,60_000,2_000);}
    Map<String,Object> status(){return db.one("SELECT * FROM integration_status WHERE name='FORMULARIO_PULL'");}
    Object lockHolder(){return db.one("SELECT holder FROM formulario_consumer_lock WHERE id=1").get("holder");}

    @Test void automaticoDrenaConservaExclusividadRespetaPausaYSeDetieneOrdenado()throws Exception{
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        var auto=automatic(consumer);
        try{
            // Entrega recibida y confirmada: drena de inmediato; cola vacia: sondeo normal.
            assertEquals(FormularioAutomatic.DRAIN_MS,auto.tick());
            assertEquals("RECIBIENDO",s(status(),"state"));assertEquals(1,producer.acks.size());
            assertEquals("CONFIRMADA",s(db.one("SELECT state FROM formulario_reception WHERE operation_id=?",operation(doc)),"state"));
            assertEquals(1_000,auto.tick());
            assertEquals("ESPERANDO_ENTREGAS",s(status(),"state"));assertNotNull(status().get("last_success_ms"));assertEquals(0,n(status(),"failures"));
            // El lease sigue siendo del proceso automatico entre ciclos: un ciclo manual u otro proceso automatico quedan fuera.
            assertEquals(auto.holder,lockHolder());
            assertTrue(consumer.run().busy());
            int before=producer.requests.get();
            assertEquals(1_000,automatic(consumer).tick());
            assertEquals("OCUPADO",s(status(),"state"));assertEquals(before,producer.requests.get());
            // Pausa auditada de TI: no contacta el origen y libera el lease; al reanudar vuelve a operar.
            db.update("UPDATE integration_control SET paused=TRUE WHERE name='FORMULARIO_PULL'");
            assertTrue(integrations.paused(Integrations.FORMULARIO));
            assertEquals(1_000,auto.tick());
            assertEquals("PAUSADO",s(status(),"state"));assertEquals(before,producer.requests.get());assertNull(lockHolder());
            db.update("UPDATE integration_control SET paused=FALSE WHERE name='FORMULARIO_PULL'");
            assertEquals(1_000,auto.tick());assertEquals("ESPERANDO_ENTREGAS",s(status(),"state"));
            assertEquals(1,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
        }finally{db.update("UPDATE integration_control SET paused=FALSE WHERE name='FORMULARIO_PULL'");auto.stop();}
        assertEquals("DETENIDO",s(status(),"state"));assertNull(lockHolder());
    }

    @Test void automaticoEsperaCrecienteAnteIncidentesYTrasReinicioConciliaSinDuplicar()throws Exception{
        var rejected=automatic(consumer(producer.origin(),"credencial-rechazada-0123456789abcdefghij"));
        try{
            assertEquals(10_000,rejected.tick());assertEquals(20_000,rejected.tick());assertEquals(40_000,rejected.tick());
            var st=status();assertEquals("DEGRADADO",s(st,"state"));assertEquals(3,n(st,"failures"));assertEquals("RECLAMO_HTTP_401",s(st,"last_error"));
        }finally{rejected.stop();}
        // Proceso que cae despues del COMMIT: ACK sin respuesta y lease sin liberar.
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        producer.ackOutcome.add(500);
        var crashed=automatic(consumer);
        assertEquals(FormularioAutomatic.MIN_BACKOFF_MS,crashed.tick());
        assertEquals(crashed.holder,lockHolder());
        var restarted=automatic(consumer);
        try{
            // El proceso nuevo espera a que venza el lease del anterior; no consume en paralelo.
            assertEquals(1_000,restarted.tick());assertEquals("OCUPADO",s(status(),"state"));
            db.update("UPDATE formulario_consumer_lock SET until_ms=1 WHERE id=1");
            db.update("UPDATE formulario_reception SET ack_next_ms=0 WHERE operation_id=?",operation(doc));
            assertEquals(1_000,restarted.tick());
            assertEquals(restarted.holder,lockHolder());
            assertEquals("CONFIRMADA",s(db.one("SELECT state FROM formulario_reception WHERE operation_id=?",operation(doc)),"state"));
            assertEquals(2,producer.acks.size());assertEquals(producer.acks.get(0),producer.acks.get(1));
            assertEquals(1,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
        }finally{restarted.stop();}
    }

    @Test void automaticoQuePierdeElLeaseNoRegistraNiConfirma()throws Exception{
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        var auto=automatic(consumer);
        // Otro proceso toma el lease mientras se reclama (p. ej. vencio durante una pausa larga del proceso).
        producer.onClaim=()->db.update("UPDATE formulario_consumer_lock SET holder='otro-proceso',until_ms=? WHERE id=1",System.currentTimeMillis()+60_000);
        try{
            assertEquals(FormularioAutomatic.MIN_BACKOFF_MS,auto.tick());
            assertEquals("EXCLUSION_PERDIDA",s(status(),"last_error"));
            assertEquals(0,count("SELECT COUNT(*) FROM communication WHERE external_id=?",operation(doc)));
            assertEquals(0,count("SELECT COUNT(*) FROM formulario_reception WHERE operation_id=?",operation(doc)));
            // Sin ACK ni informe de fallo: la reserva vence en el Formulario y la entrega se reintenta.
            assertTrue(producer.acks.isEmpty());assertTrue(producer.failures.isEmpty());
            assertEquals("otro-proceso",lockHolder());
        }finally{producer.onClaim=null;db.update("UPDATE formulario_consumer_lock SET holder=NULL,until_ms=0 WHERE id=1");auto.stop();}
    }

    @Test void automaticoEnSegundoPlanoRecibeYSeDetieneOrdenado()throws Exception{
        String doc=adultWithFile(original());producer.offer(doc,Map.of(attachment(doc),original()));
        var auto=automatic(consumer);
        auto.start();
        try{long end=System.currentTimeMillis()+20_000;while(producer.acks.isEmpty()&&System.currentTimeMillis()<end)Thread.sleep(50);}
        finally{auto.stop();}
        assertEquals(1,producer.acks.size());
        assertEquals("CONFIRMADA",s(db.one("SELECT state FROM formulario_reception WHERE operation_id=?",operation(doc)),"state"));
        assertEquals("DETENIDO",s(status(),"state"));assertNull(lockHolder());
    }

    @Test void transporteYCredencial()throws Exception{
        // Lote vacio solo con 200 bien formado; 401 es un fallo, no un lote vacio.
        var empty=consumer.run();assertTrue(empty.clean());assertEquals(0,empty.claimed());
        var wrong=consumer(producer.origin(),"otra-credencial-sintetica-0123456789abcdef").run();
        assertEquals(List.of("RECLAMO_HTTP_401"),wrong.incidents());
        // Redireccion a otro origen: no se sigue y el Bearer no llega al otro servidor.
        try(var elsewhere=new Producer(ackKeys.getPublic())){
            producer.redirectTo=elsewhere.origin();
            var redirected=consumer.run();
            assertEquals(List.of("RECLAMO_HTTP_302"),redirected.incidents());
            assertEquals(0,elsewhere.requests.get());
        }
        // Configuracion: solo HTTPS (HTTP exclusivamente hacia loopback en pruebas), sin ruta, consulta ni credenciales en la URI.
        assertThrows(IllegalStateException.class,()->new FormularioClient(URI.create("http://formulario.example.cl"),TOKEN,null,true));
        assertThrows(IllegalStateException.class,()->new FormularioClient(URI.create(producer.origin()),TOKEN,null,false));
        assertThrows(IllegalStateException.class,()->new FormularioClient(URI.create("https://formulario.example.cl/otra"),TOKEN,null,false));
        assertThrows(IllegalStateException.class,()->new FormularioClient(URI.create("https://usuario@formulario.example.cl"),TOKEN,null,false));
        assertThrows(IllegalStateException.class,()->new FormularioClient(URI.create("https://formulario.example.cl"),"corta",null,false));
        assertThrows(IllegalStateException.class,()->new FormularioClient(URI.create("https://formulario.example.cl"),TOKEN+"\r\nX: y",null,false));
        assertDoesNotThrow(()->new FormularioClient(URI.create("https://formulario.example.cl"),TOKEN,null,false));
        // Un segundo ciclo de SIGER A no corre mientras otro tiene el lease.
        assertTrue(reception.acquire("otro-ciclo",System.currentTimeMillis()+60000));
        try{assertTrue(consumer.run().busy());}finally{reception.release("otro-ciclo");}
    }

    // --- Productor sintetico ------------------------------------------------------------------------

    /** Reglas de IntegracionFormularioController/RecepcionFormularioJdbc del productor, en memoria. */
    static final class Producer implements AutoCloseable {
        final HttpServer server;final PublicKey ackTrust;
        final List<String> acks=new CopyOnWriteArrayList<>(),failures=new CopyOnWriteArrayList<>();
        final Map<String,byte[]> files=new HashMap<>();
        final Deque<Integer> ackOutcome=new ArrayDeque<>();
        final AtomicInteger downloads=new AtomicInteger(),requests=new AtomicInteger();
        volatile String redirectTo;volatile boolean expireBeforeAck;
        /** Se ejecuta al entregar una reserva (antes de responder): simula sucesos concurrentes del lado de SIGER. */
        volatile Runnable onClaim;
        String document,state="VACIA",accepted;UUID lease;long until;int attempt;

        Producer(PublicKey ackTrust)throws IOException{
            this.ackTrust=ackTrust;
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",this::handle);server.start();
        }
        String origin(){return "http://127.0.0.1:"+server.getAddress().getPort();}
        synchronized void offer(String document,Map<String,byte[]> originals){this.document=document;files.clear();files.putAll(originals);state="PENDIENTE";lease=null;attempt=0;accepted=null;}
        @Override public void close(){server.stop(0);}

        private synchronized void handle(HttpExchange x)throws IOException{
            requests.incrementAndGet();
            try(x){
                if(!("Bearer "+TOKEN).equals(x.getRequestHeaders().getFirst("Authorization"))){x.sendResponseHeaders(401,-1);return;}
                String path=x.getRequestURI().getPath(),root=FormularioClient.ROOT;
                if(!path.startsWith(root)){x.sendResponseHeaders(404,-1);return;}
                String[] p=path.substring(root.length()).split("/");
                byte[] body=x.getRequestBody().readAllBytes();
                if(p.length==2&&p[1].equals("reclamar")&&x.getRequestMethod().equals("POST")){
                    if(redirectTo!=null){x.getResponseHeaders().add("Location",redirectTo+root+"/reclamar");x.sendResponseHeaders(302,-1);return;}
                    if(!"1.0,1.1".equals(x.getRequestHeaders().getFirst("X-Formulario-Versiones"))||JSON.readTree(body).path("limite").asInt()!=1){x.sendResponseHeaders(400,-1);return;}
                    long now=System.currentTimeMillis();
                    String out="{\"entregas\":[]}";
                    if("PENDIENTE".equals(state)||"RESERVADA".equals(state)&&until<=now){
                        lease=UUID.randomUUID();until=now+300000;attempt++;state="RESERVADA";
                        out="{\"entregas\":[{\"reserva\":\""+lease+"\",\"venceEn\":"+until+",\"intento\":"+attempt+",\"entrega\":"+document+"}]}";
                        if(onClaim!=null)onClaim.run();
                    }
                    send(x,200,out.getBytes(StandardCharsets.UTF_8));return;
                }
                if(p.length<3||document==null){x.sendResponseHeaders(404,-1);return;}
                String id=p[1];JsonNode com=JSON.readTree(document).path("comunicacion");
                if(!id.equals(com.path("id").asString())){x.sendResponseHeaders(404,-1);return;}
                if(p.length==4&&p[2].equals("adjuntos")&&x.getRequestMethod().equals("GET")){
                    // Descarga con la reserva vigente; bytes tal como estan (pueden estar alterados en la prueba).
                    if(!"RESERVADA".equals(state)||!String.valueOf(lease).equals(x.getRequestHeaders().getFirst("X-Reserva"))||until<=System.currentTimeMillis()){x.sendResponseHeaders(409,-1);return;}
                    byte[] file=files.get(p[3]);if(file==null){x.sendResponseHeaders(404,-1);return;}
                    downloads.incrementAndGet();
                    x.getResponseHeaders().add("Content-Type","application/octet-stream");x.getResponseHeaders().add("Cache-Control","no-store");
                    x.sendResponseHeaders(200,file.length);x.getResponseBody().write(file);return;
                }
                if(p.length!=3){x.sendResponseHeaders(404,-1);return;}
                if(p[2].equals("confirmacion")){
                    var req=JSON.readTree(body);
                    if(expireBeforeAck){expireBeforeAck=false;until=0;}
                    String recepcion=req.path("recepcion").toString();
                    if(!valid(req.path("recepcion"),com,JSON.readTree(document))){x.sendResponseHeaders(409,-1);return;}
                    if(accepted!=null){acks.add(body(body));x.sendResponseHeaders(accepted.equals(recepcion)?204:409,-1);return;}
                    if(!"RESERVADA".equals(state)||!req.path("reserva").asString().equals(String.valueOf(lease))||until<=System.currentTimeMillis()){x.sendResponseHeaders(409,-1);return;}
                    accepted=recepcion;state="CONFIRMADA";acks.add(body(body));
                    Integer outcome=ackOutcome.poll();x.sendResponseHeaders(outcome==null?204:outcome,-1);return;
                }
                if(p[2].equals("fallo")){
                    var req=JSON.readTree(body);
                    if(!req.path("reserva").asString().equals(String.valueOf(lease))){x.sendResponseHeaders(409,-1);return;}
                    failures.add(req.path("codigo").asString());state="PENDIENTE";lease=null;x.sendResponseHeaders(204,-1);return;
                }
                x.sendResponseHeaders(404,-1);
            }
        }
        private static String body(byte[] b){return new String(b,StandardCharsets.UTF_8);}
        private static void send(HttpExchange x,int status,byte[] b)throws IOException{x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);}
        /** EntregaFormulario.validarRegistro: firma con la confianza ACK, registro igual al payload canonico y correspondencia. */
        private boolean valid(JsonNode recepcion,JsonNode com,JsonNode delivery){
            try{
                var proof=recepcion.path("comprobante");
                byte[] payload=Base64.getUrlDecoder().decode(proof.path("payload").asString());
                var v=Signature.getInstance("Ed25519");v.initVerify(ackTrust);v.update(payload);
                if(!"Ed25519".equals(proof.path("algoritmo").asString())||!v.verify(Base64.getUrlDecoder().decode(proof.path("firma").asString())))return false;
                var content=JSON.readValue(payload,FormularioAckTest.Contenido.class);
                if(!Arrays.equals(payload,JSON.writeValueAsBytes(content)))return false;
                if(!content.equals(JSON.treeToValue(recepcion.path("registro"),FormularioAckTest.Contenido.class)))return false;
                String channel="NNA".equals(com.path("tipo").asString())?"FORMULARIO_WEB_NNA":"FORMULARIO_WEB_PUBLICO";
                return content.operacionPublicaId().toString().equals(com.path("id").asString())&&content.sha256().equals(delivery.path("sha256").asString())
                    &&content.huellaProcedencia().equals(delivery.path("huellaProcedencia").asString())&&channel.equals(content.canalOrigen())
                    &&("FORMULARIO_WEB_NNA".equals(channel)?"1.1":"1.0").equals(content.version())&&content.folio().matches("[A-Z][A-Z0-9]{1,15}-[0-9]{1,19}");
            }catch(Exception e){return false;}
        }
    }
}
