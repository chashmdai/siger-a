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
