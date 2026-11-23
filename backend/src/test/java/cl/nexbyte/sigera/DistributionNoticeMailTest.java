package cl.nexbyte.sigera;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;

/**
 * Aviso a distribucion de las COM del Formulario (D24, gate T03c; escenario 9) con el correo Graph activo: cada aviso
 * encola un correo por destinatario en el mismo COMMIT (MailOutbox, via Access.notice), sin enviarlo: sin
 * siger.scheduling no hay trabajador ni transporte de red; las pruebas nunca salen a la red.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_aviso_distribucion_correo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-aviso-distribucion-correo-${random.uuid}","siger.scheduling=false",
    "siger.mail.mode=GRAPH","siger.mail.allowed-domains=defensorianinez.cl","siger.mail.link=https://siger.defensorianinez.cl"})
@ActiveProfiles("test")
class DistributionNoticeMailTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Db db;@Autowired DistributionNotices notices;@Autowired ApplicationContext context;

    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}

    @Test void unCorreoEncoladoPorDestinatarioSinEnviar(){
        long reqScope=n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");
        long second=db.insert("INSERT INTO staff(username,name,email,password_hash,enabled,home_scope,must_change) VALUES('distribucion.dos','Segunda distribucion sintetica','dos@defensorianinez.cl','test-non-login',TRUE,?,FALSE)",reqScope);
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'DISTRIBUIDOR',NULL,0,'PRUEBA AUTOMATICA')",second);
        var doc=cuVulneration();((tools.jackson.databind.node.ObjectNode)doc.path("datos").path("campos")).put("riesgo","si");
        long com=flow.conserveExternal("administrativo",reqScope,sign(doc)).id();
        assertEquals(0,db.count("SELECT COUNT(*) FROM mail_outbox"),"la recepcion no encola correo");
        assertEquals(1,notices.deliver());
        var rows=db.rows("SELECT o.recipient_id,o.state,n.text_value FROM mail_outbox o JOIN inbox_notice n ON n.id=o.notice_id WHERE n.resource_type='COM' AND n.resource_id=? ORDER BY o.recipient_id",com);
        assertEquals(2,rows.size(),"un correo por destinatario");
        assertEquals(Set.of(user("distribucion"),second),Set.of(n(rows.get(0),"recipient_id"),n(rows.get(1),"recipient_id")));
        for(var r:rows){assertEquals("PENDIENTE",s(r,"state"));assertEquals(DistributionNotices.RISK_TEXT,s(r,"text_value"));}
        assertEquals(2,db.count("SELECT COUNT(*) FROM mail_outbox"));
        // Otro ciclo no encola de nuevo; nada sale a la red.
        assertEquals(0,notices.deliver());assertEquals(2,db.count("SELECT COUNT(*) FROM mail_outbox"));
        assertTrue(context.getBeanProvider(MailWorker.class).stream().findAny().isEmpty());
        assertTrue(context.getBeanProvider(GraphMail.class).stream().findAny().isEmpty());
    }
}
