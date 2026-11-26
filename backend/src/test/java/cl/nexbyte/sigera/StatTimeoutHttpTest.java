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
 * Tiempo maximo de POST /api/stats/query de punta a punta (0.8.0, gate E08b): en esta base propia, despues del arranque,
 * {@code communication} pasa a ser una vista que tarda por fila, de modo que la consulta real del motor supera el plazo
 * de 1 s. Se cancela en el servidor (tambien en MySQL), responde 503 en texto llano sin resultados parciales, audita
 * STAT_CONSULTA_CANCELADA solo con nombres validados y libera su cupo.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_motor_tiempo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-motor-tiempo-${random.uuid}","siger.scheduling=false","siger.stats.timeout-seconds=1"})
@ActiveProfiles("test")
class StatTimeoutHttpTest {
    static final String PASSWORD="Clave-sintetica-estadistica-2026";
    static final String QUERY="{\"measure\":\"INGRESOS\",\"rows\":[\"CANAL\"],\"filters\":{\"CANAL\":[\"PRESENCIAL\"]},\"period\":{\"type\":\"CUENTA_PUBLICA\",\"from\":\"2025-07-01\"}}";
    static boolean slowed;
    @LocalServerPort int port;
    @Autowired Db db;
    @Autowired PasswordEncoder passwords;
    @Autowired Workflow flow;
    @Autowired StatGuard guard;
    final JsonMapper mapper=JsonMapper.builder().build();

    @BeforeEach void slowView(){
        if(slowed)return;
        db.update("UPDATE staff SET must_change=FALSE");
        flow.run("administrativo",new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,Map.of("scope_id",n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id"),
            "received_at","2026-01-15T15:00:00Z","channel","PRESENCIAL","subject","Ingreso sintetico","body","Antecedentes sinteticos","intake",Map.of())));
        if(StatGuardTest.MYSQL){
            db.update("RENAME TABLE communication TO communication_lenta");
            db.update("CREATE VIEW communication AS SELECT c.* FROM communication_lenta c CROSS JOIN (WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM r WHERE n<60) SELECT n FROM r) r WHERE SLEEP(0.05)=0");
        }else{
            db.update("CREATE ALIAS IF NOT EXISTS STAT_LENTO FOR 'cl.nexbyte.sigera.StatGuardTest.slow'");
            db.update("ALTER TABLE communication RENAME TO communication_lenta");
            db.update("CREATE VIEW communication AS SELECT c.* FROM communication_lenta c CROSS JOIN SYSTEM_RANGE(1,3000) r WHERE STAT_LENTO(1000)=0");
        }
        slowed=true;
    }
    String account(String user){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",user)==0){
            long home=n(db.one("SELECT id FROM org_scope WHERE code='UGI'"),"id");
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,?,?,FALSE)",user,"Cuenta "+user,passwords.encode(PASSWORD),home);
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'REPORTES',NULL,0,'Prueba sintetica')",id);
        }
        return user;
    }
    HttpResponse<String> post(HttpClient http,String header,String token,String path,String type,String body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type",type);
        if(header!=null)b.header(header,token);
        return http.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    long count(String action){return db.count("SELECT COUNT(*) FROM audit_event WHERE action=?",action);}

    @Test void consultaQueSuperaElPlazoSeCancelaYAudita()throws Exception{
        var http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var csrf=mapper.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/session")).build(),HttpResponse.BodyHandlers.ofString()).body()).path("csrf");
        String header=csrf.path("header").asString(),token=csrf.path("token").asString();
        assertEquals(204,post(http,header,token,"/login","application/x-www-form-urlencoded","username="+account("rep.tiempo")+"&password="+PASSWORD).statusCode());
        csrf=mapper.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/session")).build(),HttpResponse.BodyHandlers.ofString()).body()).path("csrf");
        header=csrf.path("header").asString();token=csrf.path("token").asString();
        long cancelled=count(StatGuard.CANCELLED),consulted=count("STAT_CONSULTA");
        for(int i=0;i<2;i++){
            long t0=System.nanoTime();
            var r=post(http,header,token,"/stats/query","application/json",QUERY);
            long ms=(System.nanoTime()-t0)/1_000_000;
            assertEquals(503,r.statusCode(),r.body());
            var json=mapper.readTree(r.body());
            assertEquals(guard.timeoutMessage(),json.path("message").asString());
            assertFalse(json.has("cells"),"sin resultados parciales");
            assertTrue(ms<5_000,"cancelada (sin cancelar tardaria mas de 3 s por sentencia): "+ms+" ms");
            if(StatGuardTest.MYSQL)assertEquals(0,db.count("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE INFO LIKE '%communication%' AND ID<>CONNECTION_ID()"),"ninguna sentencia sigue en MySQL");
        }
        assertEquals(cancelled+2,count(StatGuard.CANCELLED),"la segunda tambien obtuvo cupo: el primero se libero");
        assertEquals(consulted,count("STAT_CONSULTA"));
        var note=s(db.one("SELECT note FROM audit_event WHERE action=? ORDER BY id DESC LIMIT 1",StatGuard.CANCELLED),"note");
        assertEquals("Medida INGRESOS; modo VIGENTE; dimensiones: CANAL; filtros: CANAL; cancelada por tiempo (1 s)",note);
        assertFalse(note.contains("PRESENCIAL")||note.contains("2025"),"sin valores ni fechas de la peticion");
        assertEquals(guard.maxConcurrent(),guard.available());assertTrue(guard.active().isEmpty());
    }

    @Test void exportacionVencidaNoAbreUnaDescargaYLiberaCupo()throws Exception{
        var http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var endpoint="http://127.0.0.1:"+port+"/api/session";
        String who=account("exp.tiempo");
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) SELECT id,'EXPORTACION',NULL,0,'Prueba sintética' FROM staff WHERE username=?",who);
        var csrf=mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(endpoint)).build(),HttpResponse.BodyHandlers.ofString()).body()).path("csrf");
        assertEquals(204,post(http,csrf.path("header").asString(),csrf.path("token").asString(),"/login","application/x-www-form-urlencoded","username="+who+"&password="+PASSWORD).statusCode());
        csrf=mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(endpoint)).build(),HttpResponse.BodyHandlers.ofString()).body()).path("csrf");
        long cancelled=count(StatGuard.CANCELLED),exports=count("STAT_EXPORTACION");
        var r=post(http,csrf.path("header").asString(),csrf.path("token").asString(),"/stats/export/zip","application/json",QUERY);
        assertEquals(503,r.statusCode(),r.body());assertTrue(r.headers().firstValue("Content-Disposition").isEmpty());
        assertEquals(guard.timeoutMessage(),mapper.readTree(r.body()).path("message").asString());
        assertEquals(cancelled+1,count(StatGuard.CANCELLED));assertEquals(exports,count("STAT_EXPORTACION"));
        assertEquals(guard.maxConcurrent(),guard.available());assertTrue(guard.active().isEmpty());
    }
}
