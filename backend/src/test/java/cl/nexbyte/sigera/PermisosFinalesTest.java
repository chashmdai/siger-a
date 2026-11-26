package cl.nexbyte.sigera;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

import static cl.nexbyte.sigera.Db.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Barrido final de permisos (0.8.0, gate E14): todas las rutas /api de la aplicacion, leidas por reflexion del
 * RequestMappingHandlerMapping, estan clasificadas en {@link #ROUTES}; una ruta nueva sin clasificar (o una clasificada que
 * ya no existe) hace fallar la prueba. Por HTTP real:
 * - toda ruta no publica responde 401 sin sesion (con el token CSRF de la sesion anonima);
 * - las rutas estadisticas y de reportes responden 403 a TI, tambien con una concesion inconsistente (TI con REPORTES,
 *   EXPORTACION y GESTION_ESTADISTICA insertadas por SQL), sin auditar una consulta ni una exportacion;
 * - sus POST sin CSRF responden 403 a una cuenta con REPORTES y EXPORTACION, y un GET sobre ellas responde 405 con Allow POST.
 * Las reglas por ruta (cifras, alcance, 400/409/429/503) estan en StatQueryHttpTest, StatCutHttpTest, StatTimeoutHttpTest,
 * StatExportTest, ReportsTest y LoginPasswordTest; esta clase solo fija que ninguna ruta quede fuera.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_permisos_finales;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-permisos-finales-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class PermisosFinalesTest {
    static final String PASSWORD="Clave-sintetica-permisos-2026";
    static final String QUERY="{\"measure\":\"INGRESOS\",\"rows\":[\"CANAL\"],\"period\":{\"type\":\"CUENTA_PUBLICA\",\"from\":\"2025-07-01\"}}";

    /** Clase de una ruta: PUBLICA (sin sesion), OTRA (exige sesion; sus reglas estan en sus pruebas), ESTADISTICA o REPORTES. */
    enum Kind{PUBLICA,OTRA,ESTADISTICA,REPORTES}
    /** Clasificacion de cada ruta "METODO /ruta" con un ejemplo concreto para llamarla y su cuerpo (null = sin cuerpo). */
    record Route(Kind kind,String sample,String body){}
    static final Map<String,Route> ROUTES=new LinkedHashMap<>();
    static void r(String key,Kind kind,String sample,String body){ROUTES.put(key,new Route(kind,sample,body));}
    static{
        r("GET /api/session",Kind.PUBLICA,"/session",null);
        r("GET /api/health",Kind.PUBLICA,"/health",null);
        r("POST /api/password",Kind.OTRA,"/password","{}");
        r("GET /api/attention/{id}/versions/{version}",Kind.OTRA,"/attention/1/versions/1",null);
        r("GET /api/intake/{id}/versions/{version}",Kind.OTRA,"/intake/1/versions/1",null);
        r("GET /api/catalog",Kind.OTRA,"/catalog",null);
        r("GET /api/notices/unread",Kind.OTRA,"/notices/unread",null);
        r("GET /api/dashboard",Kind.OTRA,"/dashboard",null);
        r("POST /api/worklist/{kind}",Kind.OTRA,"/worklist/com","{}");
        r("POST /api/search",Kind.OTRA,"/search","{}");
        r("GET /api/intake/documents",Kind.OTRA,"/intake/documents",null);
        r("POST /api/intake/documents",Kind.OTRA,"/intake/documents",null);
        r("DELETE /api/intake/documents/{key}",Kind.OTRA,"/intake/documents/x",null);
        r("GET /api/workspace",Kind.OTRA,"/workspace",null);
        r("GET /api/detail/{kind}/{id}",Kind.OTRA,"/detail/COM/1",null);
        r("GET /api/calendar",Kind.OTRA,"/calendar",null);
        r("GET /api/stat-catalogs",Kind.OTRA,"/stat-catalogs",null);
        r("GET /api/stat-catalogs/{id}",Kind.OTRA,"/stat-catalogs/1",null);
        r("GET /api/administration",Kind.OTRA,"/administration",null);
        r("POST /api/administration/territorial-effect",Kind.OTRA,"/administration/territorial-effect","{}");
        r("POST /api/commands",Kind.OTRA,"/commands","{}");
        r("POST /api/documents",Kind.OTRA,"/documents",null);
        r("POST /api/documents/{id}/scan",Kind.OTRA,"/documents/1/scan",null);
        r("GET /api/documents/{id}/download",Kind.OTRA,"/documents/1/download",null);
        // Estadisticas (E08-E13): REPORTES en su alcance, nunca TI.
        r("POST /api/stats/query",Kind.ESTADISTICA,"/stats/query",QUERY);
        r("GET /api/stats/cuts",Kind.ESTADISTICA,"/stats/cuts",null);
        r("GET /api/stats/cuts/{id}",Kind.ESTADISTICA,"/stats/cuts/1",null);
        r("POST /api/stats/cuts/{id}/verify",Kind.ESTADISTICA,"/stats/cuts/1/verify","{}");
        r("GET /api/stats/views",Kind.ESTADISTICA,"/stats/views",null);
        r("POST /api/stats/export/{format}",Kind.ESTADISTICA,"/stats/export/csv",QUERY);
        r("GET /api/stats/series",Kind.ESTADISTICA,"/stats/series",null);
        r("GET /api/stats/series/{id}",Kind.ESTADISTICA,"/stats/series/1",null);
        r("GET /api/stats/series/template/{format}",Kind.ESTADISTICA,"/stats/series/template/csv",null);
        r("POST /api/stats/series/preview",Kind.ESTADISTICA,"/stats/series/preview","{}");
        r("POST /api/stats/series/compare",Kind.ESTADISTICA,"/stats/series/compare","{}");
        r("POST /api/stats/series/{id}/export/{format}",Kind.ESTADISTICA,"/stats/series/1/export/csv","{}");
        // Reportes de gestion y exportaciones (G11, G12): REPORTES o EXPORTACION, nunca TI.
        r("POST /api/reports",Kind.REPORTES,"/reports","{\"report\":\"VOLUMEN\"}");
        r("POST /api/reports/export/{kind}",Kind.REPORTES,"/reports/export/REQ","{\"report\":\"VOLUMEN\"}");
        r("GET /api/export",Kind.REPORTES,"/export?kind=COM",null);
    }

    /**
     * Hallazgo de E14 (preexistente, 0.5.0): los reportes de gestion y sus exportaciones (Reports.java, que 0.8.0 no modifica)
     * se rigen solo por REPORTES/EXPORTACION y no excluyen a una TI con una concesion inconsistente. Esa combinacion no se
     * puede crear por la aplicacion (Personnel y el archivo institucional la rechazan; aviso TI_INCOMPATIBLE). Las rutas
     * estadisticas de 0.8.0 si la excluyen. La lista es cerrada: ninguna otra ruta puede sumarse en silencio.
     */
    static final Set<String> TI_MIXED_KNOWN=Set.of("POST /api/reports","POST /api/reports/export/{kind}","GET /api/export");

    @LocalServerPort int port;
    @Autowired Db db;
    @Autowired PasswordEncoder passwords;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    final JsonMapper mapper=JsonMapper.builder().build();

    final class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String header,token;
        Browser(String user)throws Exception{
            session();
            if(user!=null){
                var login=send("POST","/login","application/x-www-form-urlencoded","username="+user+"&password="+PASSWORD,true);
                assertEquals(204,login.statusCode(),"ingreso de "+user);
                session();
            }
        }
        void session()throws Exception{
            var r=mapper.readTree(send("GET","/session",null,null,false).body()).path("csrf");
            header=r.path("header").asString();token=r.path("token").asString();
        }
        HttpResponse<String> send(String method,String path,String type,String body,boolean csrf)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
            if(csrf&&header!=null&&!header.isEmpty())b.header(header,token);
            if(type!=null)b.header("Content-Type",type);
            b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> call(String method,Route r,boolean csrf)throws Exception{
            return send(method,r.sample(),r.body()==null?null:"application/json",r.body(),csrf);
        }
    }
    /** Cuenta con clave conocida y las funciones institucionales indicadas, insertadas por SQL (sin las reglas de Personnel). */
    String account(String user,String... roles){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",user)==0){
            long home=n(db.one("SELECT id FROM org_scope WHERE code='UGI'"),"id");
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,?,?,FALSE)",user,"Cuenta "+user,passwords.encode(PASSWORD),home);
            for(String role:roles)db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'Prueba sintetica')",id,role);
        }
        return user;
    }
    long audited(){return db.count("SELECT COUNT(*) FROM audit_event WHERE action IN ('STAT_CONSULTA','STAT_EXPORTACION','REPORTE','EXPORTACION','CORTE_VERIFICADO')");}
    static String method(String key){return key.substring(0,key.indexOf(' '));}

    /** Rutas reales: "METODO /api/..." de cada metodo de controlador (solo las de /api). */
    Set<String> actual(){
        var out=new TreeSet<String>();
        for(var info:mappings.getHandlerMethods().keySet()){
            var patterns=info.getPathPatternsCondition()==null?Set.<String>of():info.getPatternValues();
            for(String p:patterns){
                if(!p.startsWith("/api/"))continue;
                String path=p.replace("{id:\\d+}","{id}");
                var methods=info.getMethodsCondition().getMethods();
                assertFalse(methods.isEmpty(),"ruta sin metodo HTTP declarado (aceptaria GET y POST): "+path);
                for(var m:methods)out.add(m.name()+" "+path);
            }
        }
        return out;
    }

    @Test void todasLasRutasEstanClasificadas(){
        var real=actual();
        var missing=real.stream().filter(k->!ROUTES.containsKey(k)).collect(Collectors.toCollection(TreeSet::new));
        var stale=ROUTES.keySet().stream().filter(k->!real.contains(k)).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(),missing,"rutas sin clasificar en PermisosFinalesTest.ROUTES");
        assertEquals(Set.of(),stale,"rutas clasificadas que ya no existen");
        assertEquals(39,real.size(),"cantidad de rutas /api (escrita a mano al cierre de E14)");
    }

    @Test void sinSesionSoloLasPublicas()throws Exception{
        var anonymous=new Browser(null);
        for(var e:ROUTES.entrySet()){
            int status=anonymous.call(method(e.getKey()),e.getValue(),true).statusCode();
            if(e.getValue().kind()==Kind.PUBLICA)assertEquals(200,status,e.getKey());
            else assertEquals(401,status,e.getKey()+": sin sesion");
        }
    }

    @Test void tiNuncaEnEstadisticasNiReportes()throws Exception{
        var ti=new Browser(account("ti.final","TI"));
        var mixed=new Browser(account("ti.mixta","TI","REPORTES","EXPORTACION","GESTION_ESTADISTICA","REPORTES_PERSONAS"));
        long before=audited();
        int checked=0;
        for(var e:ROUTES.entrySet()){
            if(e.getValue().kind()!=Kind.ESTADISTICA&&e.getValue().kind()!=Kind.REPORTES)continue;
            assertEquals(403,ti.call(method(e.getKey()),e.getValue(),true).statusCode(),e.getKey()+": TI");
            if(!TI_MIXED_KNOWN.contains(e.getKey()))assertEquals(403,mixed.call(method(e.getKey()),e.getValue(),true).statusCode(),e.getKey()+": TI con concesion inconsistente");
            checked++;
        }
        assertEquals(15,checked,"rutas estadisticas y de reportes (a mano)");
        assertEquals(before,audited(),"ningun rechazo se audita como consulta o exportacion");
    }

    @Test void postsSinCsrfYGet405()throws Exception{
        var rep=new Browser(account("rep.final","REPORTES","EXPORTACION"));
        long before=audited();
        int posts=0;
        for(var e:ROUTES.entrySet()){
            var r=e.getValue();
            if((r.kind()!=Kind.ESTADISTICA&&r.kind()!=Kind.REPORTES)||!method(e.getKey()).equals("POST"))continue;
            assertEquals(403,rep.call("POST",r,false).statusCode(),e.getKey()+": POST sin CSRF");
            var get=rep.call("GET",new Route(r.kind(),r.sample(),null),true);
            assertEquals(405,get.statusCode(),e.getKey()+": GET");
            assertTrue(get.headers().allValues("Allow").stream().anyMatch(a->a.contains("POST")),e.getKey()+": Allow POST");
            posts++;
        }
        assertEquals(8,posts,"POST estadisticos y de reportes (a mano)");
        assertEquals(before,audited());
    }
}
