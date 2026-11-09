package cl.nexbyte.sigera;

import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Outbox del correo institucional en H2 con transporte Graph FALSO (GraphMailTest.Fake): mismo COMMIT que el aviso,
 * intento durable previo a la red, AMBIGUO sin reenvio automatico y decision de TI, recuperacion tras caida,
 * supresion de destinatarios no vigentes, reintentos acotados y pausa auditada. No contacta Graph.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_mail;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.mail.mode=GRAPH","siger.mail.allowed-domains=defensorianinez.cl","siger.mail.link=https://siger.defensorianinez.cl",
    "siger.scheduling=false"})
@ActiveProfiles("test")
class MailOutboxTest {
    @Autowired Db db;@Autowired Access access;@Autowired MailOutbox outbox;@Autowired Integrations integrations;
    @Autowired Workflow flow;@Autowired PlatformTransactionManager transactions;@Autowired ApplicationContext context;
    static long scope,ti,ana,inactive,external,noMail;

    long staff(String username,String email,boolean enabled){
        return db.insert("INSERT INTO staff(username,name,email,password_hash,enabled,home_scope,must_change) VALUES(?,?,?,'test-non-login',?,?,FALSE)",username,username,email,enabled,scope);
    }
    @BeforeEach void setup(){
        if(scope==0){
            db.update("INSERT INTO org_unit VALUES('MLU','Unidad de correo')");
            scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('MLU-1','Correo','MLU','A','CENTRAL')");
            ti=staff("ti.correo","ti@defensorianinez.cl",true);
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,0,'PRUEBA AUTOMATICA')",ti);
            ana=staff("ana.correo","Ana.Perez@DefensoriaNinez.cl",true);
            inactive=staff("inactiva.correo","inactiva@defensorianinez.cl",false);
            external=staff("externa.correo","alguien@example.com",true);
            noMail=staff("sincorreo.correo",null,true);
        }
        db.update("DELETE FROM mail_attempt");db.update("DELETE FROM mail_outbox");db.update("DELETE FROM inbox_notice");
        db.update("UPDATE integration_control SET paused=FALSE");
    }
    long notice(long user){
        new TransactionTemplate(transactions).executeWithoutResult(st->access.notice(user,"Tienes una tarea asignada en un Caso.","CAS",1));
        return n(db.one("SELECT o.id FROM mail_outbox o JOIN inbox_notice n ON n.id=o.notice_id WHERE n.user_id=? ORDER BY o.id DESC LIMIT 1",user),"id");
    }
    Map<String,Object> row(long id){return db.one("SELECT * FROM mail_outbox WHERE id=?",id);}
    MailWorker worker(GraphMailTest.Fake fake){return new MailWorker(outbox,GraphMailTest.graph(fake),integrations,1_000,10,1_000);}
    Workflow.Result command(String user,String type,long id,Long revision,Map<String,Object> data){
        return flow.run(user,new Workflow.Command(UUID.randomUUID(),type,id,revision,data));
    }

    @Test void correoNaceEnElMismoCommitQueElAvisoYElProcesoSinServicioNoEnvia(){
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(st->{
            access.notice(ana,"Aviso revertido.","CAS",1);throw new IllegalStateException("rollback");}));
        assertEquals(0,db.count("SELECT COUNT(*) FROM inbox_notice"));assertEquals(0,db.count("SELECT COUNT(*) FROM mail_outbox"));
        long id=notice(ana);
        assertEquals("PENDIENTE",s(row(id),"state"));assertEquals(1,db.count("SELECT COUNT(*) FROM inbox_notice"));
        // siger.scheduling=false (como el ciclo manual del Formulario): no hay trabajador ni transporte de red.
        assertTrue(context.getBeanProvider(MailWorker.class).stream().findAny().isEmpty());
        assertTrue(context.getBeanProvider(GraphMail.class).stream().findAny().isEmpty());
    }

    @Test void envioAceptadoAlDestinatarioVigenteSinDatosDeExpedientes(){
        long id=notice(ana);
        var fake=new GraphMailTest.Fake().token().status(202,null);
        assertEquals(1_000,worker(fake).tick());
        var r=row(id);
        assertEquals("ACEPTADO",s(r,"state"));assertEquals(1,n(r,"attempts"));assertNotNull(r.get("accepted_ms"));assertNull(r.get("claim_id"));
        var attempt=db.one("SELECT * FROM mail_attempt WHERE outbox_id=?",id);
        assertEquals("ACEPTADO",s(attempt,"state"));assertEquals("ana.perez@defensorianinez.cl",s(attempt,"address"));assertNotNull(attempt.get("finished_ms"));
        var body=tools.jackson.databind.json.JsonMapper.builder().build().readTree(fake.calls.get(1).body()).path("message").path("body").path("content").asString();
        assertTrue(body.contains("Tienes una tarea asignada en un Caso."));assertTrue(body.contains("https://siger.defensorianinez.cl"));
        assertTrue(body.contains(s(r,"correlation_id")));
        assertEquals("OPERATIVO",s(db.one("SELECT state FROM integration_status WHERE name='CORREO_GRAPH'"),"state"));
    }

    @Test void ambiguoNoSeReenviaSoloYTiDecideReenviarODescartar(){
        long id=notice(ana);
        var fake=new GraphMailTest.Fake().token().fail(new IOException("respuesta perdida"));
        var w=worker(fake);
        assertEquals(10_000,w.tick());
        assertEquals("AMBIGUO",s(row(id),"state"));assertEquals("DEGRADADO",s(db.one("SELECT state FROM integration_status WHERE name='CORREO_GRAPH'"),"state"));
        int calls=fake.calls.size();
        assertEquals(1_000,w.tick());assertEquals(calls,fake.calls.size()); // nunca se reenvia automaticamente
        var overview=integrations.overview();
        var ambiguous=(List<?>)((Map<?,?>)overview.get(Integrations.CORREO)).get("ambiguous");assertEquals(1,ambiguous.size());
        // Solo TI decide, con motivo auditado.
        assertEquals(403,assertThrows(Problem.class,()->command("ana.correo","MAIL_RESOLVE",id,null,Map.of("decision","REENVIAR","reason","x"))).status);
        command("ti.correo","MAIL_RESOLVE",id,null,Map.of("decision","REENVIAR","reason","El destinatario confirma que no lo recibio."));
        assertEquals("PENDIENTE",s(row(id),"state"));assertEquals(ti,n(row(id),"resolved_by"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='CORREO_AMBIGUO_REENVIO' AND resource_id=?",id));
        fake.status(202,null);
        assertEquals(1_000,w.tick());assertEquals("ACEPTADO",s(row(id),"state"));assertEquals(2,n(row(id),"attempts"));
        // Solo un ambiguo puede resolverse; otro ambiguo se descarta y no vuelve a salir.
        assertEquals(409,assertThrows(Problem.class,()->command("ti.correo","MAIL_RESOLVE",id,null,Map.of("decision","DESCARTAR","reason","x"))).status);
        long other=notice(ana);fake.fail(new IOException("corte"));w.tick();
        command("ti.correo","MAIL_RESOLVE",other,null,Map.of("decision","DESCARTAR","reason","Se informo por otro medio."));
        int before=fake.calls.size();w.tick();
        assertEquals("DESCARTADO",s(row(other),"state"));assertEquals(before,fake.calls.size());
    }

    @Test void intentoInterrumpidoQuedaAmbiguoYUnaRespuestaTardiaSeConcilia(){
        long id=notice(ana);
        var claim=outbox.reserve(id); // el proceso cae despues de registrar el intento y antes del resultado
        assertNotNull(claim);assertEquals("EN_INTENTO",s(row(id),"state"));
        assertEquals("PREVIO_RED",s(db.one("SELECT phase FROM mail_attempt WHERE claim_id=?",claim.claim()),"phase"));
        assertNull(outbox.reserve(id)); // ya reservado
        db.update("UPDATE mail_outbox SET lease_until_ms=1 WHERE id=?",id);
        var fake=new GraphMailTest.Fake();
        worker(fake).tick(); // el reinicio recupera el intento vencido como AMBIGUO sin tocar la red
        assertEquals("AMBIGUO",s(row(id),"state"));assertTrue(fake.calls.isEmpty());
        assertEquals("AMBIGUO",s(db.one("SELECT state FROM mail_attempt WHERE claim_id=?",claim.claim()),"state"));
        outbox.finish(claim,new GraphMail.Result("ACEPTADO","GRAPH",202,0,null));
        assertEquals("ACEPTADO",s(row(id),"state"));
    }

    @Test void destinatariosNoVigentesSeSuprimenSinRed(){
        long a=notice(inactive),b=notice(external),c=notice(noMail),d=notice(ana);
        db.update("UPDATE inbox_notice SET read_ms=1 WHERE id=(SELECT notice_id FROM mail_outbox WHERE id=?)",d); // ya leido en SIGER
        var fake=new GraphMailTest.Fake();
        worker(fake).tick();
        assertTrue(fake.calls.isEmpty());
        assertEquals("CUENTA_INACTIVA",s(row(a),"last_phase"));assertEquals("DOMINIO_NO_PERMITIDO",s(row(b),"last_phase"));
        assertEquals("SIN_CORREO",s(row(c),"last_phase"));assertEquals("AVISO_LEIDO",s(row(d),"last_phase"));
        for(long id:List.of(a,b,c,d))assertEquals("SUPRIMIDO",s(row(id),"state"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM mail_attempt"));
    }

    @Test void reintentosAcotadosYTokenFallidoNoConsumeIntentos(){
        long id=notice(ana);
        var fake=new GraphMailTest.Fake().token().status(429,"120");var w=worker(fake);
        long now=System.currentTimeMillis();w.tick();
        assertEquals("REINTENTABLE",s(row(id),"state"));assertTrue(n(row(id),"next_ms")>=now+119_000);assertEquals(1,n(row(id),"attempts"));
        db.update("UPDATE mail_outbox SET next_ms=0,attempts=7 WHERE id=?",id);
        fake.status(429,"5");w.tick();
        assertEquals("RECHAZADO",s(row(id),"state"));assertEquals("REINTENTOS_AGOTADOS",s(row(id),"last_phase"));
        // Credencial de cliente rechazada: se detiene el lote, no se consumen intentos y el estado queda degradado.
        long x=notice(ana),y=notice(ana);
        var bad=new GraphMailTest.Fake().status(401,null);
        assertEquals(10_000,worker(bad).tick());
        assertEquals(1,bad.calls.size());
        assertEquals("REINTENTABLE",s(row(x),"state"));assertEquals(0,n(row(x),"attempts"));assertEquals("TOKEN",s(row(x),"last_phase"));
        assertEquals("PENDIENTE",s(row(y),"state"));
        var st=db.one("SELECT * FROM integration_status WHERE name='CORREO_GRAPH'");
        assertEquals("DEGRADADO",s(st,"state"));assertEquals("TOKEN_HTTP_401",s(st,"last_error"));
    }

    @Test void pausaAuditadaDeTiDetieneLosEnvios(){
        long id=notice(ana);
        var control=db.one("SELECT * FROM integration_control WHERE name='CORREO_GRAPH'");long revision=n(control,"revision");
        assertEquals(403,assertThrows(Problem.class,()->command("ana.correo","INTEGRATION_PAUSE",2,revision,Map.of("reason","x"))).status);
        command("ti.correo","INTEGRATION_PAUSE",2,revision,Map.of("reason","Ventana de mantenimiento del buzon."));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='INTEGRACION_PAUSADA' AND resource_id=2"));
        var fake=new GraphMailTest.Fake();
        assertEquals(1_000,worker(fake).tick());
        assertTrue(fake.calls.isEmpty());assertEquals("PENDIENTE",s(row(id),"state"));
        assertEquals("PAUSADO",integrations.health(Integrations.CORREO));
        // Revision vencida: conflicto; con la vigente se reanuda.
        assertEquals(409,assertThrows(Problem.class,()->command("ti.correo","INTEGRATION_RESUME",2,revision,Map.of("reason","x"))).status);
        command("ti.correo","INTEGRATION_RESUME",2,revision+1,Map.of("reason","Fin de la ventana."));
        fake.token().status(202,null);
        worker(fake).tick();assertEquals("ACEPTADO",s(row(id),"state"));
    }

    @Test void modoYConfiguracionValidados(){
        // Sin modo GRAPH no se encola nada (activar el correo no envia avisos retrospectivos).
        var off=new MailOutbox(null,new MockEnvironment(),null);
        assertFalse(off.enabled());off.enqueue(1,1);
        assertThrows(IllegalStateException.class,()->new MailOutbox(null,new MockEnvironment().withProperty("siger.mail.mode","SMTP"),null));
        assertThrows(IllegalStateException.class,()->new MailOutbox(null,new MockEnvironment().withProperty("siger.mail.mode","GRAPH"),null));
        assertThrows(IllegalStateException.class,()->new MailOutbox(null,new MockEnvironment().withProperty("siger.mail.mode","GRAPH").withProperty("siger.mail.allowed-domains","defensorianinez.cl").withProperty("siger.mail.link","http://siger"),null));
        assertTrue(new MailOutbox(null,new MockEnvironment().withProperty("siger.mail.mode","GRAPH").withProperty("siger.mail.allowed-domains","defensorianinez.cl"),null).enabled());
    }
}
