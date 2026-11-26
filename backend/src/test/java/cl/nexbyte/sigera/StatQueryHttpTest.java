package cl.nexbyte.sigera;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import static cl.nexbyte.sigera.Db.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * POST /api/stats/query por HTTP real (0.8.0, gate E08): solo cuentas con REPORTES (nunca TI, ni con una concesion
 * inconsistente), CSRF como todo POST, GET 405 con Allow, 400 y 409 legibles, y ninguna consulta auditada en un rechazo.
 * El catalogo publica las medidas y dimensiones solo a quien puede consultar. E08b: sin cupo 429 y motor deshabilitado 503.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_motor_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-motor-http-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatQueryHttpTest {
    static final String PASSWORD="Clave-sintetica-estadistica-2026";
    static final String QUERY="{\"measure\":\"INGRESOS\",\"rows\":[\"CANAL\"],\"period\":{\"type\":\"CUENTA_PUBLICA\",\"from\":\"2025-07-01\"}}";
    @LocalServerPort int port;
    @Autowired Db db;
    @Autowired PasswordEncoder passwords;
    @Autowired StatGuard guard;
    @Autowired Workflow flow;
    final JsonMapper mapper=JsonMapper.builder().build();

    final class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String header,token;
        Browser(String user)throws Exception{
            session();
            var login=send("POST","/login","application/x-www-form-urlencoded","username="+user+"&password="+PASSWORD,true);
            assertEquals(204,login.statusCode());
            session();
        }
        void session()throws Exception{
            var r=mapper.readTree(send("GET","/session",null,null,false).body()).path("csrf");
            header=r.path("header").asString();token=r.path("token").asString();
        }
        HttpResponse<String> send(String method,String path,String type,String body,boolean csrf)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
            if(csrf)b.header(header,token);
            if(type!=null)b.header("Content-Type",type);
            b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> query(String body)throws Exception{return send("POST","/stats/query","application/json",body,true);}
    }
    /** Cuenta con una clave conocida y las funciones institucionales indicadas. */
    String account(String user,String... roles){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",user)==0){
            long home=n(db.one("SELECT id FROM org_scope WHERE code='UGI'"),"id");
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,?,?,FALSE)",user,"Cuenta "+user,passwords.encode(PASSWORD),home);
            for(String role:roles)db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'Prueba sintetica')",id,role);
        }
        return user;
    }
    long consulted(){return db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_CONSULTA'");}

    @Test void soloConReportes()throws Exception{
        long before=consulted();
        assertEquals(403,new Browser(account("sin.reportes","DISTRIBUIDOR")).query(QUERY).statusCode());
        assertEquals(403,new Browser(account("consulta.inst","CONSULTA_INSTITUCIONAL")).query(QUERY).statusCode());
        assertEquals(403,new Browser(account("solo.exporta","EXPORTACION")).query(QUERY).statusCode());
        assertEquals(before,consulted());
    }
    @Test void tiNunca()throws Exception{
        long before=consulted();
        assertEquals(403,new Browser(account("ti.sola","TI")).query(QUERY).statusCode());
        // Concesion inconsistente (TI no combina con otras funciones): tampoco consulta ni ve el catalogo.
        var mixed=new Browser(account("ti.mixta","TI","REPORTES"));
        assertEquals(403,mixed.query(QUERY).statusCode());
        assertFalse(mapper.readTree(mixed.send("GET","/catalog",null,null,false).body()).has("stats"));
        assertEquals(before,consulted());
    }
    @Test void csrfObligatorio()throws Exception{
        long before=consulted();
        var browser=new Browser(account("rep.csrf","REPORTES"));
        assertEquals(403,browser.send("POST","/stats/query","application/json",QUERY,false).statusCode(),"POST sin token CSRF");
        assertEquals(415,browser.send("POST","/stats/query","text/plain",QUERY,true).statusCode());
        assertEquals(before,consulted());
        assertEquals(200,browser.query(QUERY).statusCode());
        assertEquals(before+1,consulted());
    }
    @Test void soloPorPost()throws Exception{
        long before=consulted();
        var browser=new Browser(account("rep.get","REPORTES"));
        var get=browser.send("GET","/stats/query?measure=INGRESOS",null,null,false);
        assertEquals(405,get.statusCode(),get.body());
        assertTrue(get.headers().firstValue("Allow").orElse("").contains("POST"));
        assertEquals(before,consulted());
    }
    @Test void respuestaErroresYCatalogo()throws Exception{
        var browser=new Browser(account("rep.ok","REPORTES"));
        var response=browser.query(QUERY);
        assertEquals(200,response.statusCode(),response.body());
        var json=mapper.readTree(response.body());
        for(String field:List.of("metadata","values","cells","totals","comparison","late_records","data_coverage","proposals","warnings"))assertTrue(json.has(field),field);
        assertEquals("1",json.path("metadata").path("methodology_version").asString());
        assertEquals("2026-06-30",json.path("metadata").path("period").path("to").asString());
        assertEquals("America/Santiago",json.path("metadata").path("zone").asString());
        for(String body:List.of("{}","[]","{\"measure\":\"INGRESOS\"",QUERY.replace("\"rows\"","\"filas\"")))assertEquals(400,browser.query(body).statusCode(),body);
        var tooMany=browser.query(QUERY.replace("CANAL","PERIODO_DIA").replace("\"CUENTA_PUBLICA\",\"from\":\"2025-07-01\"","\"PERSONALIZADO\",\"from\":\"2000-01-01\",\"to\":\"2099-12-31\""));
        assertEquals(409,tooMany.statusCode());assertTrue(mapper.readTree(tooMany.body()).path("message").asString().contains("20.000 celdas"),tooMany.body());
        var stats=mapper.readTree(browser.send("GET","/catalog",null,null,false).body()).path("stats");
        assertEquals(StatSource.MEASURES.size(),stats.path("measures").size());assertEquals(StatSource.DIMS.size(),stats.path("dimensions").size());
        assertEquals(20000,stats.path("max_cells").asInt());
        assertTrue(stats.path("enabled").asBoolean());
        assertFalse(mapper.readTree(new Browser(account("sin.catalogo","DISTRIBUIDOR")).send("GET","/catalog",null,null,false).body()).has("stats"));
    }

    /** E08b: sin cupo de la cuenta, 429 en texto llano, sin auditar ni consultar; al liberarse, la consulta responde. */
    @Test void sinCupoResponde429()throws Exception{
        var browser=new Browser(account("rep.cupo","REPORTES"));
        long id=n(db.one("SELECT id FROM staff WHERE username='rep.cupo'"),"id");
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var holder=new Thread(()->guard.run(id,()->{entered.countDown();try{release.await(60,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return null;}));
        holder.start();
        assertTrue(entered.await(30,java.util.concurrent.TimeUnit.SECONDS));
        long before=consulted();
        try{
            var busy=browser.query(QUERY);
            assertEquals(429,busy.statusCode(),busy.body());
            assertEquals(StatGuard.BUSY,mapper.readTree(busy.body()).path("message").asString());
            assertEquals(before,consulted());
        }finally{release.countDown();holder.join(30_000);}
        assertEquals(200,browser.query(QUERY).statusCode());
        assertEquals(before+1,consulted());
    }
    /** E08b: con los archivos de MySQL fuera de la unidad de la instancia, 503 legible y aviso en el catalogo; los expedientes siguen. */
    @Test void motorDeshabilitadoSinAfectarLaOperacion()throws Exception{
        var browser=new Browser(account("rep.apagado","REPORTES"));
        long before=consulted();
        guard.confined(List.of("tmpdir"));
        try{
            var off=browser.query(QUERY);
            assertEquals(503,off.statusCode(),off.body());
            assertEquals(StatGuard.DISABLED,mapper.readTree(off.body()).path("message").asString());
            assertFalse(mapper.readTree(browser.send("GET","/catalog",null,null,false).body()).path("stats").path("enabled").asBoolean(true));
            assertEquals(before,consulted());
            db.update("UPDATE staff SET must_change=FALSE");
            long com=flow.run("administrativo",new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,Map.of("scope_id",n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id"),
                "received_at",java.time.Instant.now().minusSeconds(300).toString(),"channel","PRESENCIAL","subject","Ingreso sintetico","body","Antecedentes sinteticos","intake",Map.of()))).id();
            assertEquals("NUEVA",s(db.one("SELECT state FROM communication WHERE id=?",com),"state"));
        }finally{guard.confined(List.of());}
        assertEquals(200,browser.query(QUERY).statusCode());
    }
}
