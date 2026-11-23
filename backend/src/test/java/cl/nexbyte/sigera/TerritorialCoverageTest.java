package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Cobertura territorial (0.7.0, D18-D19, gate T01): cobertura adicional de las sedes y del nivel central con fundamento,
 * concurrencia optimista, historia y auditoria solo con codigos; efecto territorial en SCOPE_COVERAGE, CENTRAL_COVERAGE,
 * SCOPE_CLOSE y SCOPE_UPDATE (cambio de region) con la confirmacion exacta de regiones_sin_sede; termino de la cobertura
 * al cerrar; aviso REGION_SIN_SEDE; catalogo, panel Estructura y vista previa (misma regla, sin escrituras).
 * Escenarios 1-7 del plan 0.7.0.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_territorio;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-territorio-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TerritorialCoverageTest {
    @Autowired Workflow flow; @Autowired Access access; @Autowired Db db; @Autowired Queries queries; @Autowired Personnel personnel;
    static final String PASSWORD="Clave-Temporal-Sintetica-2026",BASIS="Resolucion exenta sintetica de cobertura";
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    long version(long user){return n(db.one("SELECT security_version FROM staff WHERE id=?",user),"security_version");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    Problem problem(String type,Long id,Long rev,Map<String,Object> data){return assertThrows(Problem.class,()->run("ti",type,id,rev,data));}
    String refusal(String type,Long id,Long rev,Map<String,Object> data){
        var p=problem(type,id,rev,data);assertEquals(409,p.status,p.getMessage());return p.getMessage();
    }
    static Map<String,Object> reason(){return Map.of("reason",BASIS);}
    static Map<String,Object> with(Object...kv){var m=new LinkedHashMap<String,Object>();m.put("reason",BASIS);for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
    long site(String code,String region,String state){
        return run("ti","SCOPE_CREATE",null,null,Map.of("unit_code","REG","code",code,"name","Sede sintetica "+code,"site_code","SEDE-"+code,"regional",true,"region_code",region,"state",state,"reason",BASIS)).id();
    }
    void coverage(long site,List<String> regions,Object...extra){
        var data=with(extra);data.put("regions",regions);run("ti","SCOPE_COVERAGE",site,rev("org_scope",site),data);
    }
    @SuppressWarnings("unchecked") long centralRevision(){return n((Map<String,Object>)administration().get("central_coverage"),"revision");}
    @SuppressWarnings("unchecked") Map<String,Object> administration(){return queries.administration(access.actor("ti"));}
    @SuppressWarnings("unchecked") Map<String,Object> structure(long id){
        return ((List<Map<String,Object>>)administration().get("structure")).stream().filter(s->n(s,"id")==id).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked") Map<String,Object> published(long id){
        return ((List<Map<String,Object>>)queries.catalog(access.actor("distribucion")).get("scopes")).stream().filter(s->n(s,"id")==id).findFirst().orElseThrow();
    }
    Map<String,Object> preview(Object...kv){var m=new LinkedHashMap<String,Object>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return personnel.territorialPreview(access.actor("ti"),new Input(m));}
    @SuppressWarnings("unchecked") static List<String> strings(Object o){return (List<String>)o;}
    String lastNote(String action){return s(db.one("SELECT note FROM audit_event WHERE action=? ORDER BY id DESC LIMIT 1",action),"note");}
    @SuppressWarnings("unchecked") List<String> uncovered(){
        return Personnel.warnings(db).stream().filter(w->s(w,"code").equals("REGION_SIN_SEDE")).findFirst().map(w->(List<String>)w.get("regions")).orElse(List.of());
    }
    long account(String username,long home,List<Map<String,Object>> grants){
        long id=run("ti","STAFF_CREATE",null,null,Map.of("username",username,"name","Cuenta sintetica "+username,"scope_id",home,"password",PASSWORD,"reason",BASIS)).id();
        db.update("UPDATE staff SET must_change=FALSE WHERE id=?",id);
        run("ti","PROFILE_SET",id,version(id),Map.of("grants",grants,"reason",BASIS));return id;
    }

    @Test @Order(1) void coberturaDeSedesEscenarios1a4YRechazos(){
        db.update("UPDATE staff SET must_change=FALSE");
        long coquimbo=site("REG-04","04","ACTIVA"),ohiggins=site("REG-06","06","ACTIVA"),biobio=site("REG-08","08","ACTIVA"),araucania=site("REG-09","09","ACTIVA");
        assertEquals(0,db.count("SELECT COUNT(*) FROM territorial_coverage"));

        // Rechazos: region fisica, region repetida, codigo invalido, sin lista, sin cambios, sede del nivel central, revision vencida.
        long r=rev("org_scope",coquimbo);
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,r,with("regions",List.of("03","04"))).contains("region fisica de la sede (Coquimbo (04))"));
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,r,with("regions",List.of("03","03"))).contains("dos veces"));
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,r,with("regions",List.of("99"))).contains("Region invalida: 99"));
        assertEquals(400,problem("SCOPE_COVERAGE",coquimbo,r,reason()).status);
        assertEquals(400,problem("SCOPE_COVERAGE",coquimbo,r,with("regions","03")).status);
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,r,with("regions",List.of())).contains("no tiene cambios"));
        long upr=scope("UPR");
        assertTrue(refusal("SCOPE_COVERAGE",upr,rev("org_scope",upr),with("regions",List.of("13"))).contains("Solo las sedes regionales"));
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,r+1,with("regions",List.of("03"))).contains("cambio"));
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,null,with("regions",List.of("03"))).contains("cambio"));
        // Una lista de regiones sin sede cuando el servidor no calcula ninguna tambien es 409 (igualdad exacta).
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,r,with("regions",List.of("03"),"regiones_sin_sede",List.of("03"))).contains("cambiaron (ahora: ninguna)"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM territorial_coverage"));
        assertEquals(r,rev("org_scope",coquimbo));
        // Sede CERRADA: no tiene cobertura (y su cierre exigio confirmar su region fisica, que quedo sin atencion).
        long aysen=site("REG-11","11","ACTIVA");
        run("ti","SCOPE_CLOSE",aysen,rev("org_scope",aysen),with("regiones_sin_sede",List.of("11")));
        assertTrue(refusal("SCOPE_COVERAGE",aysen,rev("org_scope",aysen),with("regions",List.of("12"))).contains("cerrada"));

        // Escenarios 1-4: Atacama por Coquimbo, Maule por O'Higgins, Nuble por Biobio y Los Rios por La Araucania.
        coverage(coquimbo,List.of("03"));
        assertEquals(r+1,rev("org_scope",coquimbo),"SCOPE_COVERAGE incrementa la revision de la sede");
        assertEquals("Cobertura adicional: ninguna -> 03. Efecto territorial: 04 sigue atendida por REG-04. "+BASIS,lastNote("COBERTURA_ACTUALIZADA"));
        coverage(ohiggins,List.of("07"));coverage(biobio,List.of("16"));coverage(araucania,List.of("14"));
        var territory=Territory.load(db);
        assertEquals(List.of("REG-04"),territory.activeAt("03"));assertEquals(List.of("REG-06"),territory.activeAt("07"));
        assertEquals(List.of("REG-08"),territory.activeAt("16"));assertEquals(List.of("REG-09"),territory.activeAt("14"));
        assertEquals(List.of("09","14"),territory.attendedBy(araucania));
        // Catalogo (todos) y Estructura (TI): cobertura vigente e historia con actor, inicio y fundamento.
        assertEquals(List.of("03"),published(coquimbo).get("coverage"));assertEquals(List.of(),published(upr).get("coverage"));
        var panel=structure(coquimbo);
        assertEquals(List.of("03"),panel.get("coverage"));
        @SuppressWarnings("unchecked") var history=(List<Map<String,Object>>)panel.get("coverage_history");
        assertEquals(1,history.size());assertEquals("03",s(history.getFirst(),"region_code"));assertEquals(user("ti"),n(history.getFirst(),"from_by"));
        assertEquals(BASIS,s(history.getFirst(),"from_basis"));assertNull(history.getFirst().get("until_ms"));
        // La lista se ordena por el catalogo y reemplaza la cobertura completa; retirar 02 (solo la cubria Coquimbo) exige confirmarla.
        coverage(coquimbo,List.of("03","02"));
        assertEquals(List.of("02","03"),Territory.load(db).coverage(coquimbo));
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,rev("org_scope",coquimbo),with("regions",List.of("03"))).contains("deja sin atención: Antofagasta (02)"));
        coverage(coquimbo,List.of("03"),"regiones_sin_sede",List.of("02"));
        assertEquals("Cobertura adicional: 02,03 -> 03. Efecto territorial: 04 sigue atendida por REG-04; 02 sin atencion; 03 sigue atendida por REG-04. "+BASIS,lastNote("COBERTURA_ACTUALIZADA"));
        assertEquals(List.of("03"),Territory.load(db).coverage(coquimbo));
        assertEquals(2,db.count("SELECT COUNT(*) FROM territorial_coverage WHERE scope_id=?",coquimbo),"terminar no borra");

        // Aviso: regiones sin sede ACTIVA ni nivel central, en el orden del catalogo.
        assertEquals(List.of("15","01","02","13","10","11","12"),uncovered());
        var warning=Personnel.warnings(db).stream().filter(w->s(w,"code").equals("REGION_SIN_SEDE")).findFirst().orElseThrow();
        assertEquals(7L,n(warning,"count"));assertTrue(s(warning,"message").startsWith("Hay 7 regiones sin sede activa ni nivel central que las atienda: Arica y Parinacota (15), Tarapacá (01)"),s(warning,"message"));
    }

    @Test @Order(2) void nivelCentralEscenario6(){
        long demo=scope("REG-DEMO");
        assertEquals(0L,centralRevision());
        // Rechazos: codigo invalido, repetido, sin cambios y revision vencida o ausente.
        assertTrue(refusal("CENTRAL_COVERAGE",0L,0L,with("regions",List.of("99"))).contains("Region invalida"));
        assertTrue(refusal("CENTRAL_COVERAGE",0L,0L,with("regions",List.of("13","13"))).contains("dos veces"));
        assertTrue(refusal("CENTRAL_COVERAGE",0L,0L,with("regions",List.of())).contains("no tiene cambios"));
        assertTrue(refusal("CENTRAL_COVERAGE",0L,null,with("regions",List.of("13"))).contains("cambio"));
        // Escenario 6: la Region Metropolitana la atiende la Oficina Nacional; deja de generar aviso.
        run("ti","CENTRAL_COVERAGE",0L,0L,with("regions",List.of("13")));
        assertEquals(1L,centralRevision());
        assertTrue(refusal("CENTRAL_COVERAGE",0L,0L,with("regions",List.of("13","15"))).contains("cambio"),"revision vencida");
        assertFalse(uncovered().contains("13"));
        assertEquals(List.of("13"),queries.catalog(access.actor("distribucion")).get("central_coverage"));
        assertEquals("Cobertura del nivel central: ninguna -> 13. "+BASIS,lastNote("COBERTURA_NIVEL_CENTRAL_ACTUALIZADA"));
        assertEquals(0L,n(db.one("SELECT resource_id FROM audit_event WHERE action='COBERTURA_NIVEL_CENTRAL_ACTUALIZADA'"),"resource_id"));

        // El nivel central tambien puede atender una region con sede (05); quitarla no la deja sin atencion, quitar 15 si.
        run("ti","CENTRAL_COVERAGE",0L,1L,with("regions",List.of("13","15","05")));
        assertEquals(3L,centralRevision());
        var expected=preview("kind","NIVEL_CENTRAL","regions",List.of("13"));
        assertEquals(List.of("15"),expected.get("regiones_sin_sede"));
        assertTrue(refusal("CENTRAL_COVERAGE",0L,3L,with("regions",List.of("13"))).contains("deja sin atención: Arica y Parinacota (15)"));
        assertTrue(refusal("CENTRAL_COVERAGE",0L,3L,with("regions",List.of("13"),"regiones_sin_sede",List.of("13"))).contains("cambiaron (ahora: Arica y Parinacota (15))"));
        run("ti","CENTRAL_COVERAGE",0L,3L,with("regions",List.of("13"),"regiones_sin_sede",List.of("15")));
        assertEquals("Cobertura del nivel central: 15,05,13 -> 13. Efecto territorial: 15 sin atencion; 05 sigue atendida por REG-DEMO; 13 sigue atendida por NIVEL_CENTRAL. "+BASIS,
            lastNote("COBERTURA_NIVEL_CENTRAL_ACTUALIZADA"));
        assertEquals(5L,centralRevision());
        @SuppressWarnings("unchecked") var central=(Map<String,Object>)administration().get("central_coverage");
        assertEquals(List.of("13"),central.get("regions"));assertEquals(3,((List<?>)central.get("history")).size());
        assertEquals(List.of("15","01","02","10","11","12"),uncovered());
        assertEquals(List.of("REG-DEMO"),Territory.load(db).activeAt("05"));
        assertEquals(List.of(),structure(demo).get("coverage"));
        // Fuera de TI no hay cobertura del nivel central en administracion.
        assertEquals(Map.of(),queries.administration(access.actor("auditoria")).get("central_coverage"));
    }

    @Test @Order(3) void aperturaDeSedePropiaEscenario5(){
        long coquimbo=scope("REG-04");
        // REQ de Coquimbo (antes de la sede propia de Atacama).
        long coord=account("coord.04",coquimbo,List.of(Map.of("role","DIRECCION","scope_id",coquimbo),Map.of("role","PROFESIONAL","scope_id",coquimbo)));
        long com=run("distribucion","COM_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",ago(60),"subject","COM para Coquimbo","body","Relato sintetico")).id();
        long sol=run("distribucion","COM_ROUTE",com,rev("communication",com),Map.of("scope_id",coquimbo,"reason",BASIS)).id();
        long req=run("coord.04","SOL_ASSUME",sol,rev("solicitation",sol),Map.of("responsible_id",coord,"reason","Asume la sede")).id();

        // Sede PROYECTADA de Atacama con dotacion: se informa solo como dato y no atiende.
        long atacama=site("REG-03","03","PROYECTADA");
        account("coord.03",atacama,List.of(Map.of("role","DIRECCION","scope_id",atacama)));
        var before=preview("kind","COBERTURA","scope_id",coquimbo,"regions",List.of());
        assertEquals(List.of("03"),before.get("regiones_sin_sede"));
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)before.get("effect");
        assertEquals(List.of("04","03"),rows.stream().map(e->s(e,"region")).toList());
        assertEquals(List.of(),rows.get(1).get("attended_by"));assertEquals(List.of("REG-03"),rows.get(1).get("projected"));assertEquals(true,rows.get(1).get("unattended"));

        // Al activarse, la region tiene dos sedes.
        run("ti","SCOPE_ACTIVATE",atacama,rev("org_scope",atacama),reason());
        assertEquals(List.of("REG-04","REG-03"),Territory.load(db).activeAt("03"));
        assertEquals(List.of(),preview("kind","COBERTURA","scope_id",coquimbo,"regions",List.of()).get("regiones_sin_sede"));
        // TI retira Atacama de la cobertura de Coquimbo con fundamento (sin regiones afectadas: una lista es 409).
        assertTrue(refusal("SCOPE_COVERAGE",coquimbo,rev("org_scope",coquimbo),with("regions",List.of(),"regiones_sin_sede",List.of("03"))).contains("ahora: ninguna"));
        coverage(coquimbo,List.of());
        assertEquals("Cobertura adicional: 03 -> ninguna. Efecto territorial: 04 sigue atendida por REG-04; 03 sigue atendida por REG-03. "+BASIS,lastNote("COBERTURA_ACTUALIZADA"));
        assertEquals(List.of("REG-03"),Territory.load(db).activeAt("03"));
        assertEquals(List.of(),published(coquimbo).get("coverage"));
        // La historia muestra el periodo anterior, terminado con actor y fundamento; el REQ anterior conserva su sede.
        @SuppressWarnings("unchecked") var history=(List<Map<String,Object>>)structure(coquimbo).get("coverage_history");
        var last=history.stream().filter(h->s(h,"region_code").equals("03")).findFirst().orElseThrow();
        assertEquals("03",s(last,"region_code"));assertNotNull(last.get("until_ms"));assertEquals(user("ti"),n(last,"until_by"));assertEquals(BASIS,s(last,"until_basis"));
        assertEquals(coquimbo,n(db.one("SELECT principal_scope FROM requirement WHERE id=?",req),"principal_scope"));
    }

    @Test @Order(4) void cierreEscenario7YTerminoDeCobertura(){
        long araucania=scope("REG-09"),coquimbo=scope("REG-04"),biobio=scope("REG-08");
        // Los bloqueos de D8 van primero: Coquimbo tiene un REQ abierto (el efecto territorial no es un bloqueo).
        assertTrue(refusal("SCOPE_CLOSE",coquimbo,rev("org_scope",coquimbo),reason()).startsWith("No se puede cerrar la sede: 1 REQ abierto"));
        assertTrue(((List<?>)structure(coquimbo).get("close_blockers")).size()>0);
        assertEquals(1,((List<?>)structure(coquimbo).get("territorial_effect")).size());

        // Escenario 7: La Araucania (09, con Los Rios 14) queda sin atencion. Estructura muestra el mismo efecto que el cierre.
        var effect=structure(araucania).get("territorial_effect");
        var expected=preview("kind","CIERRE","scope_id",araucania);
        assertEquals(expected.get("effect"),effect);assertEquals(List.of("09","14"),expected.get("regiones_sin_sede"));
        long r=rev("org_scope",araucania);
        assertTrue(refusal("SCOPE_CLOSE",araucania,r,reason()).contains("deja sin atención: La Araucanía (09), Los Ríos (14)"));
        assertTrue(refusal("SCOPE_CLOSE",araucania,r,with("regiones_sin_sede",List.of("09"))).contains("cambiaron"));
        assertTrue(refusal("SCOPE_CLOSE",araucania,r,with("regiones_sin_sede",List.of("14","09"))).contains("cambiaron"),"igualdad exacta, tambien en el orden");
        assertTrue(refusal("SCOPE_CLOSE",araucania,r,with("regiones_sin_sede",List.of("09","14","14"))).contains("cambiaron"));
        assertEquals("ACTIVA",s(db.one("SELECT state FROM org_scope WHERE id=?",araucania),"state"));
        run("ti","SCOPE_CLOSE",araucania,r,with("regiones_sin_sede",List.of("09","14")));
        assertEquals("CERRADA",s(db.one("SELECT state FROM org_scope WHERE id=?",araucania),"state"));
        assertEquals("Efecto territorial: 09 sin atencion; 14 sin atencion. Cobertura adicional terminada: 14. "+BASIS,lastNote("AMBITO_CERRADO"));
        // La cobertura adicional termino en el mismo acto (sin borrar) y la historia sigue en Estructura.
        assertEquals(0,db.count("SELECT COUNT(*) FROM territorial_coverage WHERE scope_id=? AND until_ms IS NULL",araucania));
        assertEquals(1,db.count("SELECT COUNT(*) FROM territorial_coverage WHERE scope_id=? AND until_basis=?",araucania,BASIS));
        var closed=structure(araucania);
        assertEquals(List.of(),closed.get("coverage"));assertEquals(List.of(),closed.get("territorial_effect"));assertEquals(1,((List<?>)closed.get("coverage_history")).size());
        assertTrue(uncovered().containsAll(List.of("09","14")));

        // Cierre sin regiones afectadas: Biobio (08, con Nuble 16) cuando el nivel central atiende 08 y una sede propia, 16.
        long nuble=site("REG-16","16","ACTIVA");
        run("ti","CENTRAL_COVERAGE",0L,centralRevision(),with("regions",List.of("13","08")));
        assertEquals(List.of(),preview("kind","CIERRE","scope_id",biobio).get("regiones_sin_sede"));
        assertTrue(refusal("SCOPE_CLOSE",biobio,rev("org_scope",biobio),with("regiones_sin_sede",List.of("08"))).contains("ahora: ninguna"));
        run("ti","SCOPE_CLOSE",biobio,rev("org_scope",biobio),reason());
        assertEquals("Efecto territorial: 08 sigue atendida por NIVEL_CENTRAL; 16 sigue atendida por REG-16. Cobertura adicional terminada: 16. "+BASIS,lastNote("AMBITO_CERRADO"));
        assertNotEquals(0,nuble);
    }

    @Test @Order(5) void cambioDeRegionFisica(){
        long ohiggins=scope("REG-06");
        // La nueva region fisica nunca puede estar en la cobertura adicional, en ningun estado de la sede.
        assertTrue(refusal("SCOPE_UPDATE",ohiggins,rev("org_scope",ohiggins),with("name","Sede sintetica REG-06","region_code","07")).contains("cobertura adicional de la sede"));
        long lagos=site("REG-10","10","PROYECTADA");
        coverage(lagos,List.of("11"));
        assertTrue(uncovered().containsAll(List.of("10","11")),"una sede proyectada no atiende");
        assertTrue(refusal("SCOPE_UPDATE",lagos,rev("org_scope",lagos),with("name","Sede sintetica REG-10","region_code","11")).contains("cobertura adicional de la sede"));
        // Una sede proyectada no atiende hoy: cambiar su region no tiene efecto ni pide confirmacion.
        run("ti","SCOPE_UPDATE",lagos,rev("org_scope",lagos),with("name","Sede sintetica REG-10","region_code","12"));
        assertEquals("Region: 10 -> 12. "+BASIS,lastNote("AMBITO_ACTUALIZADO"));

        // O'Higgins pasa de 06 a 02: 06 queda sin atencion y Maule (07) sigue atendida por la misma sede.
        var expected=preview("kind","REGION","scope_id",ohiggins,"region_code","02");
        assertEquals(List.of("06"),expected.get("regiones_sin_sede"));
        long r=rev("org_scope",ohiggins);
        assertTrue(refusal("SCOPE_UPDATE",ohiggins,r,with("name","Sede sintetica REG-06","region_code","02")).contains("deja sin atención: Libertador General Bernardo O'Higgins (06)"));
        run("ti","SCOPE_UPDATE",ohiggins,r,with("name","Sede sintetica REG-06","region_code","02","regiones_sin_sede",List.of("06")));
        assertEquals("Region: 06 -> 02. Efecto territorial: 06 sin atencion; 07 sigue atendida por REG-06. "+BASIS,lastNote("AMBITO_ACTUALIZADO"));
        assertEquals("02",s(db.one("SELECT region_code FROM org_scope WHERE id=?",ohiggins),"region_code"));
        // Sin cambio de region no hay efecto: una lista enviada es 409; editar solo el nombre no pide confirmacion.
        assertTrue(refusal("SCOPE_UPDATE",ohiggins,rev("org_scope",ohiggins),with("name","Otro nombre","region_code","02","regiones_sin_sede",List.of("06"))).contains("ahora: ninguna"));
        run("ti","SCOPE_UPDATE",ohiggins,rev("org_scope",ohiggins),with("name","Otro nombre","region_code","02"));
        assertEquals(BASIS,lastNote("AMBITO_ACTUALIZADO"));
    }

    @Test @Order(6) void vistaPreviaSinEscriturasYConLasMismasValidaciones(){
        long coquimbo=scope("REG-04"),ohiggins=scope("REG-06");
        var before=state();
        preview("kind","CIERRE","scope_id",coquimbo);
        preview("kind","COBERTURA","scope_id",coquimbo,"regions",List.of("03"));
        preview("kind","REGION","scope_id",ohiggins,"region_code","05");
        preview("kind","NIVEL_CENTRAL","regions",List.of());
        assertEquals(before,state(),"la vista previa no escribe");
        // Mismas validaciones que el comando.
        var p=assertThrows(Problem.class,()->preview("kind","COBERTURA","scope_id",coquimbo,"regions",List.of("04")));
        assertEquals(refusal("SCOPE_COVERAGE",coquimbo,rev("org_scope",coquimbo),with("regions",List.of("04"))),p.getMessage());
        assertTrue(assertThrows(Problem.class,()->preview("kind","CIERRE","scope_id",scope("REG-09"))).getMessage().contains("activa"));
        assertTrue(assertThrows(Problem.class,()->preview("kind","COBERTURA","scope_id",scope("REG-09"),"regions",List.of("12"))).getMessage().contains("cerrada"));
        assertTrue(assertThrows(Problem.class,()->preview("kind","REGION","scope_id",ohiggins,"region_code","07")).getMessage().contains("cobertura adicional"));
        assertTrue(assertThrows(Problem.class,()->preview("kind","NIVEL_CENTRAL","regions",List.of("99"))).getMessage().contains("Region invalida"));
        assertEquals(400,assertThrows(Problem.class,()->preview("kind","OTRO")).status);
        assertEquals(before,state());
        // Auditoria de cobertura solo con codigos: ninguna nota menciona nombres de cuentas.
        for(var e:db.rows("SELECT note FROM audit_event WHERE action IN ('COBERTURA_ACTUALIZADA','COBERTURA_NIVEL_CENTRAL_ACTUALIZADA','AMBITO_CERRADO','AMBITO_ACTUALIZADO')"))
            assertFalse(s(e,"note").contains("sintetica coord")||s(e,"note").contains("Cuenta sintetica"),s(e,"note"));
    }
    Map<String,Long> state(){
        var out=new TreeMap<String,Long>();
        for(var t:List.of("territorial_coverage","audit_event","command_log","org_scope","staff","role_assignment"))out.put(t,db.count("SELECT COUNT(*) FROM "+t));
        out.put("org_scope.revision",db.count("SELECT COALESCE(SUM(revision),0) FROM org_scope"));
        out.put("terminos",db.count("SELECT COUNT(*) FROM territorial_coverage WHERE until_ms IS NOT NULL"));
        out.put("staff.security_version",db.count("SELECT COALESCE(SUM(security_version),0) FROM staff"));
        return out;
    }
}
