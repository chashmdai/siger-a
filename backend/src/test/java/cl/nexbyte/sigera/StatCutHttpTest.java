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
 * Cortes y vistas por HTTP real (0.8.0, gate E10): se crean, anulan y comparten por POST /api/commands (CSRF); las
 * lecturas GET /api/stats/cuts, /cuts/{id} y /views y la verificacion POST /api/stats/cuts/{id}/verify (CSRF, GET 405)
 * exigen REPORTES y nunca TI; la consulta sobre un corte va por POST /api/stats/query con cut_id.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_cortes_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-cortes-http-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatCutHttpTest {
    static final String PASSWORD="Clave-sintetica-cortes-2026";
    static final String PERIOD="{\"type\":\"CUENTA_PUBLICA\",\"from\":\"2025-07-01\"}";
    @LocalServerPort int port;
    @Autowired Db db;
    @Autowired PasswordEncoder passwords;
    final JsonMapper mapper=JsonMapper.builder().build();

    final class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String header,token;
        Browser(String user)throws Exception{
            session();
            assertEquals(204,send("POST","/login","application/x-www-form-urlencoded","username="+user+"&password="+PASSWORD,true).statusCode());
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
        HttpResponse<String> command(String type,Long id,Long revision,String data,boolean csrf)throws Exception{
            String body="{\"key\":\""+UUID.randomUUID()+"\",\"type\":\""+type+"\",\"id\":"+id+",\"revision\":"+revision+",\"data\":"+data+"}";
            return send("POST","/commands","application/json",body,csrf);
        }
    }
    String account(String user,String... roles){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",user)==0){
            long home=n(db.one("SELECT id FROM org_scope WHERE code='UGI'"),"id");
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,?,?,FALSE)",user,"Cuenta "+user,passwords.encode(PASSWORD),home);
            for(String role:roles)db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'Prueba sintetica')",id,role);
        }
        return user;
    }

    @Test void cortesYVistasPorHttp()throws Exception{
        var gestion=new Browser(account("gest.http","REPORTES","GESTION_ESTADISTICA"));
        String data="{\"title\":\"Cuenta Publica\",\"period\":"+PERIOD+",\"reason\":\"Fundamento sintetico\"}";
        long cuts0=db.count("SELECT COUNT(*) FROM stat_cut");
        assertEquals(403,gestion.command("STAT_CUT_CREATE",null,null,data,false).statusCode(),"sin CSRF");
        assertEquals(cuts0,db.count("SELECT COUNT(*) FROM stat_cut"));
        var created=gestion.command("STAT_CUT_CREATE",null,null,data,true);
        assertEquals(200,created.statusCode(),created.body());
        long cut=mapper.readTree(created.body()).path("id").asLong();
        // Lecturas.
        var list=gestion.send("GET","/stats/cuts",null,null,false);
        assertEquals(200,list.statusCode());assertEquals(cut,mapper.readTree(list.body()).get(0).path("id").asLong());
        var detail=mapper.readTree(gestion.send("GET","/stats/cuts/"+cut,null,null,false).body());
        assertEquals("VIGENTE",detail.path("state").asString());assertEquals(64,detail.path("metadata_sha256").asString().length());
        assertEquals(404,gestion.send("GET","/stats/cuts/999999",null,null,false).statusCode());
        // Verificacion: solo POST con CSRF.
        assertEquals(403,gestion.send("POST","/stats/cuts/"+cut+"/verify",null,null,false).statusCode());
        var get=gestion.send("GET","/stats/cuts/"+cut+"/verify",null,null,false);
        assertEquals(405,get.statusCode());assertTrue(get.headers().firstValue("Allow").orElse("").contains("POST"));
        var verified=gestion.send("POST","/stats/cuts/"+cut+"/verify",null,null,true);
        assertEquals(200,verified.statusCode(),verified.body());assertTrue(mapper.readTree(verified.body()).path("ok").asBoolean());
        // Consulta sobre el corte.
        var q=gestion.send("POST","/stats/query","application/json","{\"measure\":\"INGRESOS\",\"rows\":[\"CANAL\"],\"period\":"+PERIOD+",\"cut_id\":"+cut+"}",true);
        assertEquals(200,q.statusCode(),q.body());assertEquals(cut,mapper.readTree(q.body()).path("metadata").path("cut_id").asLong());
        // Vistas.
        var saved=gestion.command("STAT_VIEW_SAVE",null,null,"{\"title\":\"Por canal\",\"definition\":{\"measure\":\"INGRESOS\",\"rows\":[\"CANAL\"],\"period\":"+PERIOD+"}}",true);
        assertEquals(200,saved.statusCode(),saved.body());
        long view=mapper.readTree(saved.body()).path("id").asLong();
        assertEquals(200,gestion.command("STAT_VIEW_SHARE",view,0L,"{\"shared\":true}",true).statusCode());
        var reader=new Browser(account("rep.http","REPORTES"));
        var views=reader.send("GET","/stats/views",null,null,false);
        assertEquals(200,views.statusCode());assertEquals(view,mapper.readTree(views.body()).get(0).path("id").asLong());
        assertEquals(403,reader.command("STAT_CUT_ANNUL",cut,0L,"{\"reason\":\"x\"}",true).statusCode());
        // TI y una cuenta sin REPORTES: 403 en todo.
        for(var b:List.of(new Browser(account("ti.http","TI")),new Browser(account("sin.rep.http","DISTRIBUIDOR")))){
            for(String path:List.of("/stats/cuts","/stats/cuts/"+cut,"/stats/views"))assertEquals(403,b.send("GET",path,null,null,false).statusCode(),path);
            assertEquals(403,b.send("POST","/stats/cuts/"+cut+"/verify",null,null,true).statusCode());
            assertEquals(403,b.command("STAT_CUT_CREATE",null,null,data,true).statusCode());
        }
        // Anulacion por la cuenta de gestion, con revision.
        assertEquals(200,gestion.command("STAT_CUT_ANNUL",cut,0L,"{\"reason\":\"Fundamento de anulacion\"}",true).statusCode());
        assertEquals("ANULADO",mapper.readTree(gestion.send("GET","/stats/cuts/"+cut,null,null,false).body()).path("state").asString());
    }
}
