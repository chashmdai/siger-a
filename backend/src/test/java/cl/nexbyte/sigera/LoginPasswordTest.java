package cl.nexbyte.sigera;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Regresion del ingreso y del cambio obligatorio de clave por HTTP real (sesion, CSRF y filtros).
 * Origen: en una instancia local cuya base H2 quedo de solo lectura, el ingreso respondia 500 dejando
 * la sesion autenticada sin auditoria, y el cambio de clave respondia 500 sin poder continuar.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:sigeralogin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-datalogin-${random.uuid}"})
@ActiveProfiles("test")
class LoginPasswordTest {
    @LocalServerPort int port;
    @Autowired Db db;
    @Autowired Bootstrap bootstrap;

    /** Navegador minimo: conserva cookies y envia el token CSRF vigente. */
    final class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String csrfHeader, csrfToken;
        HttpResponse<String> send(String method,String path,String type,String body)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
            if(!method.equals("GET")){b.header(csrfHeader,csrfToken);if(type!=null)b.header("Content-Type",type);b.method(method,HttpRequest.BodyPublishers.ofString(body==null?"":body));}
            return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        }
        String session()throws Exception{
            var r=send("GET","/session",null,null);assertEquals(200,r.statusCode());
            csrfHeader=field(r.body(),"header");csrfToken=field(r.body(),"token");return r.body();
        }
        int login(String user,String pass)throws Exception{
            session();
            return send("POST","/login","application/x-www-form-urlencoded","username="+enc(user)+"&password="+enc(pass)).statusCode();
        }
        HttpResponse<String> password(String current,String next)throws Exception{
            session();
            return send("POST","/password","application/json","{\"current\":\""+current+"\",\"password\":\""+next+"\"}");
        }
        boolean authenticated()throws Exception{return session().contains("\"authenticated\":true");}
    }
    static String enc(String s){return java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}
    static String field(String json,String key){var m=Pattern.compile("\""+key+"\":\"([^\"]*)\"").matcher(json);assertTrue(m.find(),"falta "+key);return m.group(1);}
    /** Clave temporal del bootstrap sintetico; nunca se imprime. */
    String temporary(String user)throws Exception{
        return Files.readAllLines(bootstrap.data.resolve("bootstrap-usuarios.csv")).stream().skip(1).map(l->l.split(","))
            .filter(c->c[0].equals(user)).map(c->c[c.length-1]).findFirst().orElseThrow();
    }
    long audits(String user,String action){return db.count("SELECT COUNT(*) FROM audit_event e JOIN staff s ON s.id=e.actor_id WHERE s.username=? AND e.action=?",user,action);}
    Map<String,Object> staff(String user){return db.one("SELECT must_change,security_version FROM staff WHERE username=?",user);}

    @Test void ingresoCambioObligatorioYNuevoIngreso()throws Exception{
        String user="profesional",temp=temporary(user),fresh="Nueva-clave-regresion-2026";
        var browser=new Browser();
        assertEquals(204,browser.login(user,temp));
        assertTrue(browser.session().contains("\"must_change\":true"));
        assertEquals(428,browser.send("GET","/catalog",null,null).statusCode(),"con clave temporal solo se permite cambiarla");
        assertEquals(1,audits(user,"LOGIN"));

        assertEquals(403,browser.password("clave-incorrecta-123456",fresh).statusCode());
        assertEquals(409,browser.password(temp,"corta").statusCode());
        var changed=browser.password(temp,fresh);
        assertEquals(200,changed.statusCode(),changed.body());
        assertFalse(browser.authenticated(),"el cambio cierra la sesion");
        assertFalse(b(staff(user),"must_change"));
        assertEquals(1,n(staff(user),"security_version"));
        assertEquals(1,audits(user,"CLAVE_CAMBIADA"));

        var again=new Browser();
        assertEquals(401,again.login(user,temp),"la clave temporal deja de servir");
        assertFalse(again.authenticated());
        assertEquals(204,again.login(user,fresh));
        assertTrue(again.session().contains("\"must_change\":false"));
        assertEquals(200,again.send("GET","/catalog",null,null).statusCode());
        assertEquals(2,audits(user,"LOGIN"));
    }

    /**
     * G13: el mismo resultado en H2 y en MySQL (utf8mb4_0900_ai_ci). El ingreso no distingue mayusculas (las cuentas se
     * guardan en minusculas), no acepta otra cuenta por una tilde y todas las variantes comparten el cupo de intentos.
     */
    @Test void ingresoSinDistinguirMayusculasNiAceptarTildesYUnSoloCupo()throws Exception{
        String user="auditoria",temp=temporary(user);
        var accented=new Browser();
        assertEquals(401,accented.login("auditoría",temp),"una tilde no es la misma cuenta");
        var upper=new Browser();
        assertEquals(204,upper.login("AUDITORIA",temp));
        assertTrue(upper.session().contains("\"username\":\"auditoria\""),"la sesion usa la cuenta canonica");
        // El ingreso anterior ya uso 1 de los 30 intentos del cupo; 29 mas con otras mayusculas lo agotan.
        for(int i=0;i<29;i++)assertEquals(401,new Browser().login(i%2==0?"Auditoria":"auditoriA","clave-incorrecta-"+i));
        assertEquals(429,new Browser().login(user,temp),"las variantes de mayusculas agotan el mismo cupo");
    }

    @Test void ingresoSinAuditoriaNoQuedaAbiertoYElCambioDeClaveEsAtomico()throws Exception{
        String user="administrativo",temp=temporary(user);
        var browser=new Browser();
        db.update("ALTER TABLE audit_event RENAME TO audit_event_fuera");
        try{
            assertEquals(503,browser.login(user,temp));
            assertFalse(browser.authenticated(),"sin auditoria no puede quedar una sesion iniciada");
        }finally{db.update("ALTER TABLE audit_event_fuera RENAME TO audit_event");}
        assertEquals(0,audits(user,"LOGIN"));

        assertEquals(204,browser.login(user,temp));
        db.update("ALTER TABLE audit_event RENAME TO audit_event_fuera");
        HttpResponse<String> failed;
        try{failed=browser.password(temp,"Nueva-clave-atomica-2026");}
        finally{db.update("ALTER TABLE audit_event_fuera RENAME TO audit_event");}
        assertTrue(failed.statusCode()>=500,"falla de auditoria: "+failed.statusCode());
        assertTrue(b(staff(user),"must_change"),"la clave no cambia si no se audita");
        assertEquals(0,n(staff(user),"security_version"));
        assertTrue(browser.authenticated(),"la sesion sigue vigente para reintentar");
        assertEquals(200,browser.password(temp,"Nueva-clave-atomica-2026").statusCode());
    }

    /**
     * G08c: el texto del buscador y de las bandejas viaja en el cuerpo de un POST con CSRF, nunca en la URL. GET responde
     * 405 (antes del manejador generico, que daria 500) y la respuesta por HTTP es la misma del servicio.
     */
    @Test void buscadorYBandejasSoloPorPostConCsrf()throws Exception{
        String user="distribucion",fresh="Nueva-clave-buscador-2026";
        var first=new Browser();
        assertEquals(204,first.login(user,temporary(user)));
        assertEquals(200,first.password(temporary(user),fresh).statusCode());
        var browser=new Browser();
        assertEquals(204,browser.login(user,fresh));
        browser.session();

        var get=browser.send("GET","/search?q="+enc("Ana Pérez"),null,null);
        assertEquals(405,get.statusCode(),get.body());
        assertTrue(get.headers().firstValue("Allow").orElse("").contains("POST"));
        assertFalse(get.body().contains("Ana"),"el error no devuelve el termino");
        assertEquals(405,browser.send("GET","/worklist/com?q="+enc("Ana"),null,null).statusCode());

        String json="{\"q\":\"Ana Pérez\",\"open\":true}";
        var noCsrf=browser.http.send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/search")).header("Content-Type","application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403,noCsrf.statusCode(),"POST sin token CSRF");
        assertEquals(415,browser.send("POST","/search","text/plain",json).statusCode());

        var post=browser.send("POST","/search","application/json",json);
        assertEquals(200,post.statusCode(),post.body());
        var expected=search.search(access.actor(user),"Ana Pérez",null,PersonSearch.Filters.of(null,true,null,null,null,null));
        assertEquals(mapper.readTree(mapper.writeValueAsString(expected)),mapper.readTree(post.body()));

        var board=browser.send("POST","/worklist/com","application/json","{\"page\":0,\"size\":25,\"q\":\"\",\"filter\":\"all\"}");
        assertEquals(200,board.statusCode(),board.body());
        assertEquals(mapper.readTree(mapper.writeValueAsString(boards.page(access.actor(user),"com",0,25,"","all",null))),mapper.readTree(board.body()));
        var defaults=browser.send("POST","/worklist/com","application/json","{}");
        assertEquals(mapper.readTree(board.body()),mapper.readTree(defaults.body()),"mismos valores por defecto que el GET anterior");
    }
    /** G11: reportes solo por POST con CSRF; sin REPORTES, 403; la sesion publica can_report. */
    @Test void reportesSoloPorPostConCsrf()throws Exception{
        String user="gabinete",fresh="Nueva-clave-reportes-2026";
        var first=new Browser();
        assertEquals(204,first.login(user,temporary(user)));
        assertEquals(200,first.password(temporary(user),fresh).statusCode());
        var browser=new Browser();
        assertEquals(204,browser.login(user,fresh));
        assertTrue(browser.session().contains("\"can_report\":false"));
        String json="{\"report\":\"VOLUMEN\"}";
        assertEquals(403,browser.send("POST","/reports","application/json",json).statusCode(),"sin REPORTES");
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) SELECT id,'REPORTES',NULL,0,'Prueba sintetica' FROM staff WHERE username=?",user);
        assertTrue(browser.session().contains("\"can_report\":true"));
        var get=browser.send("GET","/reports?report=VOLUMEN",null,null);
        assertEquals(405,get.statusCode(),get.body());
        assertTrue(get.headers().firstValue("Allow").orElse("").contains("POST"));
        var noCsrf=browser.http.send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/reports")).header("Content-Type","application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403,noCsrf.statusCode(),"POST sin token CSRF");
        var post=browser.send("POST","/reports","application/json",json);
        assertEquals(200,post.statusCode(),post.body());
        assertEquals("VOLUMEN",mapper.readTree(post.body()).get("report").asString());
        var period=browser.send("POST","/reports","application/json","{\"report\":\"REQ_TOTAL\",\"from\":\"2026-01-01\",\"to\":\"2026-12-31\",\"group\":\"AMBITO_MATERIA\",\"by_origin\":false}");
        assertEquals(200,period.statusCode(),period.body());
        assertEquals("[\"AMBITO\",\"MATERIA\"]",mapper.readTree(period.body()).get("dimensions").toString());
    }
    /**
     * Vista previa del efecto territorial (0.7.0, D19) por HTTP: solo TI (403 al resto), POST con CSRF (sin token 403),
     * GET 405 con Allow y sin escrituras (ni cobertura, ni revisiones, ni auditoria, ni comandos).
     */
    @Test void efectoTerritorialSoloTiPorPostConCsrfYSinEscrituras()throws Exception{
        String json="{\"kind\":\"CIERRE\",\"scope_id\":"+db.one("SELECT id FROM org_scope WHERE code='REG-DEMO'").get("id")+"}";
        var other=new Browser();String user="direccion.upd",fresh="Nueva-clave-territorio-2026";
        assertEquals(204,other.login(user,temporary(user)));assertEquals(200,other.password(temporary(user),fresh).statusCode());
        other=new Browser();assertEquals(204,other.login(user,fresh));assertTrue(other.authenticated()); // token CSRF de la sesion autenticada
        assertEquals(403,other.send("POST","/administration/territorial-effect","application/json",json).statusCode(),"sin TI");
        var ti=new Browser();String freshTi="Nueva-clave-territorio-ti-2026";
        assertEquals(204,ti.login("ti",temporary("ti")));assertEquals(200,ti.password(temporary("ti"),freshTi).statusCode());
        ti=new Browser();assertEquals(204,ti.login("ti",freshTi));assertTrue(ti.authenticated());
        var get=ti.send("GET","/administration/territorial-effect",null,null);
        assertEquals(405,get.statusCode(),get.body());assertTrue(get.headers().firstValue("Allow").orElse("").contains("POST"));
        var noCsrf=ti.http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/administration/territorial-effect")).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,noCsrf.statusCode(),"POST sin token CSRF");
        String writes="SELECT (SELECT COUNT(*) FROM territorial_coverage)+(SELECT COUNT(*) FROM audit_event)*1000+(SELECT COUNT(*) FROM command_log)*1000000+(SELECT COALESCE(SUM(revision),0) FROM org_scope)*1000000000";
        long before=db.count(writes);
        var post=ti.send("POST","/administration/territorial-effect","application/json",json);
        assertEquals(200,post.statusCode(),post.body());
        assertEquals("[\"05\"]",mapper.readTree(post.body()).get("regiones_sin_sede").toString());
        assertEquals("05",mapper.readTree(post.body()).get("effect").get(0).get("region").asString());
        assertEquals(before,db.count(writes),"la vista previa no escribe");
    }
    /**
     * Exportaciones (G12) por HTTP: el CSV de bandejas exige EXPORTACION (403 sin ella) y sigue acotado a lo que la cuenta
     * lee; ambos CSV traen BOM, punto y coma, fecha aaaa-mm-dd hh:mm y el ambito por su codigo. La exportacion de Reportes
     * es POST con CSRF (GET 405).
     */
    @Test void exportacionesConPuntoYComaYCodigoDeAmbito()throws Exception{
        String user="coordinacion.requerimientos",fresh="Nueva-clave-exportacion-2026";
        var first=new Browser();
        assertEquals(204,first.login(user,temporary(user)));
        assertEquals(200,first.password(temporary(user),fresh).statusCode());
        db.update("UPDATE staff SET must_change=FALSE WHERE username IN (?,'profesional.regional')",user);
        long scope=n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");
        var mine=flow.run(user,new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,Map.of("scope_id",scope,"channel","TELEFONO","received_at",java.time.Instant.now().minusSeconds(60).toString(),"subject","Asunto exportable","body","Relato exportable")));
        long site=n(db.one("SELECT id FROM org_scope WHERE code='REG-DEMO'"),"id");
        var foreign=flow.run("profesional.regional",new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,Map.of("scope_id",site,"channel","TELEFONO","received_at",java.time.Instant.now().minusSeconds(60).toString(),"subject","Asunto ajeno","body","Relato ajeno")));
        var browser=new Browser();
        assertEquals(204,browser.login(user,fresh));
        assertTrue(browser.session().contains("\"can_export\":false"));
        assertEquals(403,browser.send("GET","/export?kind=COM",null,null).statusCode(),"bandejas sin EXPORTACION");
        assertEquals(403,browser.send("POST","/reports/export/REQ","application/json","{}").statusCode(),"reportes sin EXPORTACION");
        // EXPORTACION sin REPORTES en el mismo alcance no rige.
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) SELECT id,'EXPORTACION',NULL,0,'Prueba sintetica' FROM staff WHERE username=?",user);
        assertTrue(browser.session().contains("\"can_export\":false"));
        assertEquals(403,browser.send("GET","/export?kind=COM",null,null).statusCode());
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) SELECT id,'REPORTES',NULL,0,'Prueba sintetica' FROM staff WHERE username=?",user);
        assertTrue(browser.session().contains("\"can_export\":true"));
        var board=browser.send("GET","/export?kind=COM",null,null);
        assertEquals(200,board.statusCode(),board.body());
        assertTrue(board.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
        String body=board.body();
        assertTrue(body.startsWith("\ufeff\"folio\";\"estado\";\"ambito\";\"creado\"\r\n"),body);
        String folio=s(db.one("SELECT folio FROM communication WHERE id=?",mine.id()),"folio");
        var row=body.lines().filter(l->l.startsWith("\""+folio+"\";")).findFirst().orElseThrow(()->new AssertionError(body));
        assertTrue(row.matches("\"[^\"]+\";\"NUEVA\";\"UPR-REQ\";\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}\""),row);
        assertFalse(body.contains(";\""+scope+"\";"),"el ambito va por su codigo, no por su id");
        // Acotado a lo que la cuenta lee: la COM registrada por otra cuenta (sin encaminar) no aparece; ni asuntos ni relatos.
        assertFalse(body.contains(s(db.one("SELECT folio FROM communication WHERE id=?",foreign.id()),"folio")),body);
        assertFalse(body.contains("Asunto")||body.contains("Relato"),body);
        var get=browser.send("GET","/reports/export/REQ",null,null);
        assertEquals(405,get.statusCode());assertTrue(get.headers().firstValue("Allow").orElse("").contains("POST"));
        var noCsrf=browser.http.send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/reports/export/REQ")).header("Content-Type","application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403,noCsrf.statusCode(),"POST sin token CSRF");
        var rows=browser.send("POST","/reports/export/REQ","application/json","{\"from\":\"2026-01-01\",\"to\":\"2026-12-31\"}");
        assertEquals(200,rows.statusCode(),rows.body());
        assertTrue(rows.headers().firstValue("Content-Disposition").orElse("").contains("siger-req-2026-01-01-2026-12-31.csv"));
        assertTrue(rows.body().startsWith("\ufeff\""+String.join("\";\"",Reports.REQ_COLUMNS)+"\"\r\n"),rows.body());
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event e JOIN staff s ON s.id=e.actor_id WHERE s.username=? AND e.action='EXPORTACION' AND e.note LIKE 'Exportacion REQ%'",user));
    }
    @Autowired Workflow flow;
    @Autowired PersonSearch search;@Autowired Access access;@Autowired BoardQueries boards;
    final tools.jackson.databind.json.JsonMapper mapper=tools.jackson.databind.json.JsonMapper.builder().build();

    @Test void arranqueRechazaBaseDeSoloLectura()throws Exception{
        String file=Path.of("target","ro-"+UUID.randomUUID()).toAbsolutePath().toString().replace('\\','/');
        try(var c=DriverManager.getConnection("jdbc:h2:file:"+file+";MODE=MySQL","sa","");var st=c.createStatement()){
            st.execute("CREATE TABLE operation_lock(id INT PRIMARY KEY)");st.execute("INSERT INTO operation_lock VALUES(1)");
            Db.requireWritable(c);
            try(var r=st.executeQuery("SELECT COUNT(*) FROM operation_lock WHERE id=1")){r.next();assertEquals(1,r.getInt(1),"la verificacion no altera datos");}
            st.execute("SHUTDOWN");
        }
        try(var c=DriverManager.getConnection("jdbc:h2:file:"+file+";MODE=MySQL;ACCESS_MODE_DATA=r","sa","")){
            var error=assertThrows(IllegalStateException.class,()->Db.requireWritable(c));
            assertTrue(error.getMessage().contains("solo lectura"));
        }
        assertTrue(Db.readOnly(new RuntimeException(new java.sql.SQLException("h2","90097"))));
        assertTrue(Db.readOnly(new java.sql.SQLException("mysql","HY000",1290)));
        assertFalse(Db.readOnly(new java.sql.SQLException("otra","42S02")));
    }
}
