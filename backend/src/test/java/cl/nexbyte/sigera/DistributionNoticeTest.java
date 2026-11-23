package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;

/**
 * Aviso a distribucion de las COM del Formulario (0.7.0, D24, gate T03c; escenario 9) con el correo desactivado: una
 * tarea periodica aparte de la recepcion avisa una sola vez por COM del Formulario por distribuir a quienes pueden
 * distribuirla (Access.notifyDistributors), con el texto de riesgo segun la regla de D23. Ni la COM manual, ni la ya
 * distribuida (tambien la devuelta despues de un vinculo), ni la atencion directa reciben este aviso; sin quien
 * distribuya, el aviso sale cuando haya quien pueda. El ciclo se llama directamente (siger.scheduling=false).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_aviso_distribucion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-aviso-distribucion-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class DistributionNoticeTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Access access;@Autowired Db db;@Autowired DistributionNotices notices;@Autowired PlatformTransactionManager transactions;
    static final String NOTICES="SELECT COUNT(*) FROM inbox_notice WHERE resource_type='COM' AND resource_id=?";

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long revision(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    /** Segunda cuenta de distribucion (una vez por contexto): el aviso va a cada una. */
    static long second;
    List<Long> distributors(){return List.of(user("distribucion"),second);}
    @BeforeEach void setup(){
        db.update("UPDATE staff SET must_change=FALSE WHERE technical=FALSE");
        if(second==0){
            second=db.insert("INSERT INTO staff(username,name,password_hash,enabled,home_scope,must_change) VALUES('distribucion.dos','Segunda distribucion sintetica','test-non-login',TRUE,?,FALSE)",scope("UPR-REQ"));
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'DISTRIBUIDOR',NULL,0,'PRUEBA AUTOMATICA')",second);
        }
        notices.deliver();
    }
    /** COM del Formulario recibida en Requerimientos, con riesgo (null = sin el campo). */
    long formulario(String riesgo){
        var doc=cuVulneration();var campos=(ObjectNode)doc.path("datos").path("campos");
        campos.remove("riesgo");if(riesgo!=null)campos.put("riesgo",riesgo);
        return flow.conserveExternal("administrativo",scope("UPR-REQ"),sign(doc)).id();
    }
    long notices(long com,String text){return db.count(NOTICES+" AND text_value=?",com,text);}
    long notices(long com,long user,String text){return db.count(NOTICES+" AND user_id=? AND text_value=?",com,user,text);}
    long arrivals(long com){return db.count(NOTICES+" AND text_value IN (?,?)",com,DistributionNotices.TEXT,DistributionNotices.RISK_TEXT);}
    int restart(){return new TransactionTemplate(transactions).execute(st->new DistributionNotices(db,access).deliver());}

    @Test void textosDeD24SinDatosDePersonas(){
        assertEquals("Hay un ingreso del Formulario pendiente de distribucion.",DistributionNotices.TEXT);
        assertEquals("Hay un ingreso del Formulario con NNA en riesgo inminente, pendiente de distribucion.",DistributionNotices.RISK_TEXT);
    }

    @Test void unAvisoPorComAQuienDistribuyeConElTextoDeRiesgo(){
        long plain=formulario(null),si=formulario("si"),upper=formulario("TRUE"),no=formulario("no"),accent=formulario("sí");
        // La recepcion no avisa: el aviso sale del ciclo.
        for(long c:List.of(plain,si,upper,no,accent))assertEquals(0,db.count(NOTICES,c));
        assertEquals(5,notices.deliver());
        Map<Long,String> expected=Map.of(plain,DistributionNotices.TEXT,si,DistributionNotices.RISK_TEXT,upper,DistributionNotices.RISK_TEXT,
            no,DistributionNotices.TEXT,accent,DistributionNotices.TEXT);
        for(var e:expected.entrySet()){
            long com=e.getKey();
            for(long d:distributors())assertEquals(1,notices(com,d,e.getValue()),"un aviso por cuenta que distribuye");
            assertEquals(2,db.count(NOTICES,com),"solo quienes distribuyen");
            assertEquals(0,arrivals(com)-notices(com,e.getValue()),"un solo texto por COM");
        }
        for(String other:List.of("administrativo","coordinacion.requerimientos","direccion.upr","ti","gabinete"))
            assertEquals(0,db.count("SELECT COUNT(*) FROM inbox_notice WHERE user_id=? AND resource_type='COM'",user(other)));
        // Otro ciclo y un reinicio (otra instancia, el estado esta solo en la base) no repiten.
        assertEquals(0,notices.deliver());assertEquals(0,restart());
        for(long c:expected.keySet())assertEquals(2,db.count(NOTICES,c));
        // Correo desactivado: solo el aviso interno.
        assertEquals(0,db.count("SELECT COUNT(*) FROM mail_outbox"));
    }

    @Test void niManualNiDistribuidaNiDevueltaNiAtencionDirecta(){
        long sc=scope("UPR-REQ");
        long manual=run("administrativo","COM_CREATE",null,null,Map.of("scope_id",sc,"channel","TELEFONO","received_at",Instant.now().minusSeconds(120).toString(),"subject","COM manual sintetica","body","Relato sintetico")).id();
        long routed=formulario(null);
        run("distribucion","COM_ROUTE",routed,revision("communication",routed),Map.of("scope_id",scope("UPR"),"reason","Clasificacion sintetica"));
        long req=run("administrativo","ATTENTION_CREATE",null,null,new LinkedHashMap<>(Map.of("scope_id",sc,"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(600).toString(),
            "form",new LinkedHashMap<>(Map.of("schema_version","1.1","modality","GENERAL","motive","Consulta sintetica","course","EN_ATENCION","people",List.of()))))).id();
        long direct=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
        // Vinculada como antecedente, rechazada y devuelta: vuelve a NUEVA con su aviso de rechazo, no con el de llegada.
        long returned=formulario("si");
        run("distribucion","COM_LINK",returned,revision("communication",returned),Map.of("folio",s(db.one("SELECT folio FROM requirement WHERE id=?",req),"folio"),"reason","Posible relacion"));
        long link=n(db.one("SELECT id FROM communication_link WHERE communication_id=?",returned),"id");
        run("coordinacion.requerimientos","LINK_DECIDE",link,revision("communication_link",link),Map.of("decision","RECHAZADA","reason","Asunto distinto"));
        run("coordinacion.requerimientos","LINK_RETURN",link,revision("communication_link",link),Map.of("reason","Reclasificar"));
        assertEquals("NUEVA",s(db.one("SELECT state FROM communication WHERE id=?",returned),"state"));
        // Con una SOL anterior y de nuevo en NUEVA (por comandos una SOL devuelta deja la COM ENVIADA; regla defensiva
        // para datos historicos, simulada por SQL): ya paso por distribucion, no recibe el aviso de llegada.
        long withSol=formulario(null);
        run("distribucion","COM_ROUTE",withSol,revision("communication",withSol),Map.of("scope_id",scope("UPR"),"reason","Clasificacion sintetica"));
        db.update("UPDATE communication SET state='NUEVA' WHERE id=?",withSol);
        // Fuera de NUEVA sin SOL ni vinculo (datos historicos, simulado por SQL): ya no esta por distribuir.
        long discarded=formulario("si");
        db.update("UPDATE communication SET state='INVALIDA' WHERE id=?",discarded);
        // Una COM del Formulario por distribuir que no paso por SOL ni vinculo si se avisa (control).
        long control=formulario(null);
        assertEquals(1,notices.deliver());
        assertEquals(2,arrivals(control));
        for(long c:List.of(manual,routed,direct,returned,withSol,discarded))assertEquals(0,arrivals(c));
        // Los otros avisos siguen igual.
        for(long d:distributors()){
            assertEquals(1,notices(manual,d,"Hay un ingreso pendiente de clasificacion."));
            assertEquals(1,notices(returned,d,"Una COM rechazada como antecedente requiere nueva clasificacion."));
        }
        assertEquals(0,db.count(NOTICES,routed));
    }

    @Test void sinQuienDistribuyaElAvisoSaleDespuesUnaVez(){
        try{
            for(long d:distributors())db.update("UPDATE staff SET enabled=FALSE WHERE id=?",d);
            long com=formulario("true");
            assertEquals(0,notices.deliver());assertEquals(0,notices.deliver());
            assertEquals(0,db.count(NOTICES,com),"sin quien distribuya no se escribe nada");
            db.update("UPDATE staff SET enabled=TRUE WHERE id=?",user("distribucion"));
            assertEquals(1,notices.deliver());
            assertEquals(1,notices(com,user("distribucion"),DistributionNotices.RISK_TEXT));
            assertEquals(1,db.count(NOTICES,com));
            // Una sola vez por COM: quien se habilita despues no genera otro aviso de llegada.
            db.update("UPDATE staff SET enabled=TRUE WHERE id=?",second);
            assertEquals(0,notices.deliver());assertEquals(1,db.count(NOTICES,com));
        }finally{for(long d:distributors())db.update("UPDATE staff SET enabled=TRUE WHERE id=?",d);}
    }

    @Test void tareaPeriodicaCadaMinutoEnUnaTransaccion()throws Exception{
        var method=DistributionNotices.class.getMethod("deliver");
        var scheduled=method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled,"tarea programada");
        assertEquals(60000,scheduled.fixedDelay());assertEquals(60000,scheduled.initialDelay());
        assertNotNull(method.getAnnotation(Transactional.class),"lock global dentro de una transaccion");
    }
}
