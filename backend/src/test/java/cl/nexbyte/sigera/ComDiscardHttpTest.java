package cl.nexbyte.sigera;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_descarte_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-descarte-http-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class ComDiscardHttpTest extends StatFixture {
    static final String PASSWORD="Clave-sintetica-descarte-2026";
    static final String LECTOR="lector.descarte";
    static final String JUSTIFICATION="Motivo de desestimación de ensayo";
    @LocalServerPort int port;
    @Autowired PasswordEncoder passwords;
    final JsonMapper json=JsonMapper.builder().build();

    @Test void permisosEstadosFundamentoEIdempotencia()throws Exception{
        staff(LECTOR,"CONSULTA_INSTITUCIONAL");
        var distributor=new Browser(DIST);
        var reader=new Browser(LECTOR);
        var registrar=new Browser(ADMIN);
        long a=manual(Map.of());
        long pending=manual(Map.of());
        route(pending,scope("UPR-REQ"));
        long withReq=manual(Map.of());
        assume(withReq);
        long returned=manual(Map.of());
        long solReturned=route(returned,scope("UPR-REQ"));
        on(HEAD,"SOL_RETURN","solicitation",solReturned,Map.of("reason","Devuelta"));
        long noReason=manual(Map.of());
        long solNoReason=route(noReason,scope("UPR-REQ"));
        var head=new Browser(HEAD);

        assertTrue(distributor.canDiscard(a));
        assertTrue(distributor.canDiscard(returned));
        assertFalse(distributor.canDiscard(pending));
        assertFalse(distributor.canDiscard(withReq));
        assertFalse(reader.canDiscard(a));
        assertEquals(403,reader.command(discard(a,0)).statusCode());
        assertEquals(403,registrar.command(discard(a,0)).statusCode());
        assertEquals(403,distributor.send("POST","/commands","application/json",json.writeValueAsString(discard(a,0)),false).statusCode());
        assertEquals(409,distributor.command(discard(pending,rev("communication",pending))).statusCode());
        assertEquals(409,distributor.command(discard(withReq,rev("communication",withReq))).statusCode());
        assertEquals(409,distributor.command(discard(a,5)).statusCode());
        assertEquals(400,distributor.command(new Workflow.Command(UUID.randomUUID(),"COM_DISCARD",a,0L,Map.of("disposition","ATENDIDA","reason",JUSTIFICATION))).statusCode());
        assertEquals(400,distributor.command(new Workflow.Command(UUID.randomUUID(),"COM_DISCARD",a,0L,Map.of("disposition","INVALIDA"))).statusCode());
        assertEquals(400,head.command(new Workflow.Command(UUID.randomUUID(),"SOL_DISCARD",solNoReason,0L,Map.of("disposition","INVALIDA"))).statusCode());
        assertEquals(5,db.count("SELECT COUNT(*) FROM communication WHERE id IN (?,?,?,?,?) AND state IN ('NUEVA','ENVIADA')",a,pending,withReq,returned,noReason));

        var direct=discard(a,0);
        var first=distributor.command(direct);
        assertEquals(200,first.statusCode(),first.body());
        assertFalse(first.body().contains(JUSTIFICATION));
        assertEquals(first.body(),distributor.command(direct).body());
        assertEquals(409,distributor.command(discard(a,rev("communication",a))).statusCode());
        assertFalse(distributor.canDiscard(a));
        assertTrue(reader.detail(a).path("history").toString().contains(JUSTIFICATION));
        assertEquals(0,db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",a));
        assertEquals(0,db.count("SELECT COUNT(*) FROM requirement WHERE communication_id=?",a));
        assertEquals(1,db.count("SELECT COUNT(*) FROM milestone m JOIN audit_event e ON e.resource_id=m.resource_id AND e.resource_type=m.resource_type"
            +" AND m.at_ms=e.at_ms AND m.actor_id=e.actor_id AND m.scope_id=e.scope_id WHERE m.communication_id=?"
            +" AND m.resource_type='COM' AND m.event='COM_DESCARTADA' AND m.detail='INVALIDA' AND e.action='COM_INVALIDA' AND e.note=?",a,JUSTIFICATION));
        assertEquals(1,db.count("SELECT COUNT(*) FROM command_log WHERE command_key=?",direct.key().toString()));

        assertTrue(distributor.returnedContains(solReturned));
        long solRevision=rev("solicitation",solReturned);
        assertEquals(200,distributor.command(discard(returned,rev("communication",returned))).statusCode());
        assertFalse(distributor.returnedContains(solReturned));
        assertEquals("DESCARTADA",s(db.one("SELECT state FROM solicitation WHERE id=?",solReturned),"state"));
        assertEquals(solRevision+1,rev("solicitation",solReturned));
        assertEquals(2,db.count("SELECT COUNT(*) FROM milestone WHERE resource_type='SOL' AND resource_id=? AND event IN ('SOL_DISTRIBUIDA','SOL_DEVUELTA')",solReturned));
        assertNotEquals(200,distributor.command(new Workflow.Command(UUID.randomUUID(),"SOL_ROUTE",solReturned,solRevision+1,
            Map.of("scope_id",scope("UPD")))).statusCode());
    }

    @Test void inclusoConEstadoNuevoUnaComConReqSeRechaza()throws Exception{
        long req=attention(ADMIN,"UPR-REQ","PRESENCIAL",ago(300),Map.of());
        long com=comOf(req);
        // Simula una inconsistencia histórica: el REQ sigue asociado a la COM sin SOL.
        db.update("UPDATE communication SET state='NUEVA' WHERE id=?",com);
        var distributor=new Browser(DIST);
        assertFalse(distributor.canDiscard(com));
        assertEquals(409,distributor.command(discard(com,rev("communication",com))).statusCode());
        assertEquals(0,db.count("SELECT COUNT(*) FROM milestone WHERE communication_id=? AND event='COM_DESCARTADA'",com));
    }

    @Test void duplicadaExigeCompletitudYRevierteClasificacionParcial()throws Exception{
        catalogs("tipo-solicitud-v1.json","tipo-requirente-v1.json","via-ingreso-v1.json");
        long com=manual(Map.of());
        var distributor=new Browser(DIST);
        assertEquals(2,distributor.detail(com).path("discard_blockers").size());
        var body=map("disposition","DUPLICADA","reason",JUSTIFICATION,"stat",stat("CONSULTA_ORIENTACION",null));
        assertEquals(409,distributor.command(new Workflow.Command(UUID.randomUUID(),"COM_DISCARD",com,0L,body)).statusCode());
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_event WHERE communication_id=?",com));
        assertEquals(0,db.count("SELECT COUNT(*) FROM milestone WHERE communication_id=? AND event='COM_DESCARTADA'",com));
        assertEquals("NUEVA",s(db.one("SELECT state FROM communication WHERE id=?",com),"state"));
        body.put("stat",stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA"));
        assertEquals(200,distributor.command(new Workflow.Command(UUID.randomUUID(),"COM_DISCARD",com,0L,body)).statusCode());
        assertEquals(2,db.count("SELECT COUNT(*) FROM stat_event WHERE communication_id=?",com));
        assertEquals(409,assertThrows(Problem.class,()->run(DIST,"STAT_CLASSIFY",com,2L,Map.of("stat",stat("PROMOCION",null)))).status);
    }

    @Test void conservaOriginalFirmadoYProyecciones()throws Exception{
        long com=formulario("UPR-REQ",Instant.parse("2025-07-01T15:00:00Z"),"13","Santiago",Map.of());
        var before=externalRows(com);
        var distributor=new Browser(DIST);
        assertEquals(200,distributor.command(discard(com,rev("communication",com))).statusCode());
        assertEquals(before,externalRows(com));
        assertEquals("INVALIDA",distributor.detail(com).path("state").asString());
    }

    private List<List<Map<String,Object>>> externalRows(long com){
        return List.of(
            db.rows("SELECT * FROM communication_original WHERE communication_id=?",com),
            db.rows("SELECT * FROM external_person WHERE communication_id=? ORDER BY id",com),
            db.rows("SELECT a.* FROM external_attribute a JOIN external_person p ON p.id=a.person_id WHERE p.communication_id=? ORDER BY a.id",com),
            db.rows("SELECT * FROM external_field WHERE communication_id=? ORDER BY id",com));
    }

    private Workflow.Command discard(long com,long revision){
        return new Workflow.Command(UUID.randomUUID(),"COM_DISCARD",com,revision,Map.of("disposition","INVALIDA","reason",JUSTIFICATION));
    }

    private long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}

    private final class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String header,token;
        Browser(String username)throws Exception{
            db.update("UPDATE staff SET password_hash=?,must_change=FALSE WHERE username=?",passwords.encode(PASSWORD),username);
            csrf();
            assertEquals(204,send("POST","/login","application/x-www-form-urlencoded","username="+username+"&password="+PASSWORD,true).statusCode());
            csrf();
        }
        void csrf()throws Exception{
            var data=json.readTree(send("GET","/session",null,null,false).body()).path("csrf");
            header=data.path("header").asString();token=data.path("token").asString();
        }
        HttpResponse<String> send(String method,String path,String type,String body,boolean csrf)throws Exception{
            var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
            if(csrf)req.header(header,token);
            if(type!=null)req.header("Content-Type",type);
            return http.send(req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> command(Workflow.Command command)throws Exception{return send("POST","/commands","application/json",json.writeValueAsString(command),true);}
        tools.jackson.databind.JsonNode detail(long com)throws Exception{
            var response=send("GET","/detail/COM/"+com,null,null,false);
            assertEquals(200,response.statusCode());
            return json.readTree(response.body());
        }
        boolean canDiscard(long com)throws Exception{return detail(com).path("can_discard").asBoolean();}
        boolean returnedContains(long sol)throws Exception{
            var response=send("POST","/worklist/sol","application/json","{\"page\":0,\"size\":100,\"filter\":\"returned\"}",true);
            assertEquals(200,response.statusCode());
            for(var row:json.readTree(response.body()).path("items"))if(row.path("id").asLong()==sol)return true;
            return false;
        }
    }
}
