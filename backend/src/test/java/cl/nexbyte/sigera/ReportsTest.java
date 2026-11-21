package cl.nexbyte.sigera;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Reportes (0.5.0, D6, gate G11). Conjunto sintetico: las cadenas se crean con comandos y los instantes de sus hitos se
 * fijan por SQL, para tener medianas, p90, maximos y dias habiles esperados (con feriados nacional y regional). Cubre el
 * origen (filtro, agrupacion y separacion por defecto en REQ total), el cruce ambito x materia, el alcance de cada
 * concesion (institucion, raiz de unidad con su materia en sedes, area, sede), la Tenencia de SOL con dos redirecciones,
 * Asuncion y SOL total, Gestion con espera descontada, Revision de cierre anulada al retirar la solicitud, tramos
 * reconstruidos, volumen (REQ regionales sin materia), antiguedad, el desglose por persona solo con REPORTES_PERSONAS,
 * TI 403, el limite de filas, la auditoria sin valores de filtros y una sede cerrada que sigue en los reportes.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_reportes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-reportes-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportsTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Db db;@Autowired Reports reports;@Autowired Access access;

    static final String HEAD="coordinacion.requerimientos",PROF="profesional",ADMIN="administrativo",DIST="distribucion",DIR_UPD="direccion.upd",COORD="coordinacion.regional",PROF_REG="profesional.regional";
    static final double H=3_600_000d;
    static Data dx;
    record Data(long tenure,long tenureSol,long tenureReq,long cas,long regionalOpen){}

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    /** Instante en hora de Chile. */
    static long at(int month,int day,int hour){return LocalDateTime.of(2026,month,day,hour,0).atZone(BusinessCalendar.ZONE).toInstant().toEpochMilli();}
    long run(String who,String type,String table,Long id,Map<String,Object> data){
        Long rev=table==null||id==null?null:n(db.one("SELECT "+(table.equals("staff")?"security_version":"revision")+" AS revision FROM "+table+" WHERE id=?",id),"revision");
        return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data)).id();
    }
    long com(String subject){
        return run(ADMIN,"COM_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",ago(300),"subject",subject,"body","Relato sintetico"));
    }
    long solOf(long com){return n(db.one("SELECT id FROM solicitation WHERE communication_id=?",com),"id");}
    /** COM distribuida a un destino (con materia sugerida en una sede) y asumida: devuelve el REQ. */
    long req(String subject,String target,String matter,String head,String responsible){
        long com=com(subject);var data=new HashMap<String,Object>(Map.of("scope_id",scope(target),"reason","Clasificacion sintetica"));
        if(matter!=null)data.put("suggested_matter",matter);
        run(DIST,"COM_ROUTE","communication",com,data);
        return run(head,"SOL_ASSUME","solicitation",solOf(com),Map.of("responsible_id",user(responsible),"reason","Asumida"));
    }
    void move(long req,String event,long when){assertEquals(1,db.update("UPDATE milestone SET at_ms=? WHERE requirement_id=? AND event=?",when,req,event),event);}
    void milestone(String event,long cas,long actor,long when){
        var c=db.one("SELECT c.requirement_id,c.scope_id,r.communication_id FROM case_record c JOIN requirement r ON r.id=c.requirement_id WHERE c.id=?",cas);
        db.update("INSERT INTO milestone(event,resource_type,resource_id,communication_id,requirement_id,case_id,actor_id,scope_id,at_ms) VALUES(?,'CAS',?,?,?,?,?,?,?)",
            event,cas,n(c,"communication_id"),n(c,"requirement_id"),cas,actor,n(c,"scope_id"),when);
    }
    void reporter(String username,String... grants){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",username)>0)return;
        long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'x',?,FALSE)",username,"Cuenta "+username,scope("UGI"));
        for(var g:grants){var p=g.split("@");db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,0,'Prueba sintetica')",id,p[0],p.length>1?scope(p[1]):null);}
    }
    void holiday(String day,String region){db.update("INSERT INTO business_holiday(holiday_day,region_code,region_key,name,created_by,created_ms) VALUES(?,?,?,'Feriado sintetico',?,0)",day,region,region==null?"NAC":region,user("ti"));}

    synchronized Data data(){
        if(dx!=null)return dx;
        db.update("UPDATE staff SET must_change=FALSE");
        reporter("rep.inst","REPORTES");reporter("rep.upr","REPORTES@UPR");reporter("rep.upd","REPORTES@UPD");reporter("rep.area","REPORTES@UPR-REQ");
        reporter("rep.sede","REPORTES@REG-DEMO");reporter("rep.pers","REPORTES","REPORTES_PERSONAS");reporter("rep.mixto","REPORTES@UPR","REPORTES_PERSONAS");
        reporter("rep.solo.personas","REPORTES_PERSONAS");
        holiday("2026-03-04",null);holiday("2026-03-05","05");holiday("2027-01-01",null);
        // REQ total, origen SOL en UPR-REQ: 10, 20, 30, 40 y 100 horas desde el lunes 2-3-2026 09:00 (sin cambio de horario).
        long start=at(3,2,9);
        for(int hours:new int[]{10,20,30,40,100}){
            long req=req("REQ sintetico "+hours,"UPR-REQ",null,HEAD,PROF);
            run(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Cierre sintetico"));
            move(req,"REQ_CREADO",start);move(req,"REQ_CERRADO",start+(long)(hours*H));
        }
        // Atenciones resueltas en el acto (origen atencion directa, duracion 0).
        for(int i=0;i<2;i++){
            long req=run(ADMIN,"COM_IMMEDIATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL","received_at",ago(600),"subject","Orientacion","body","Relato sintetico",
                "reason","Orientacion entregada","effective_at",ago(300),"result","ORIENTACION"));
            move(req,"REQ_CREADO",at(3,3,10));move(req,"REQ_CERRADO",at(3,3,10));
        }
        // Sede (region 05) con materias UPD y UPR: lunes 09:00 a viernes 13:00 (feriado nacional el 4 y regional el 5).
        for(String matter:List.of("UPD","UPR")){
            long req=req("REQ regional "+matter,"REG-DEMO",matter,COORD,PROF_REG);
            run(COORD,"REQ_CLOSE","requirement",req,Map.of("reason","Cierre sintetico"));
            move(req,"REQ_CREADO",start);move(req,"REQ_CERRADO",start+(long)(100*H));
        }
        long regionalOpen=req("REQ regional sin materia","REG-DEMO",null,COORD,PROF_REG);
        // SOL redirigida dos veces: UPR-REQ -> UPD -> UPR-REQ y asumida (febrero).
        long com=com("SOL con dos redirecciones");
        run(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",scope("UPR-REQ"),"reason","Clasificacion"));
        long sol=solOf(com);
        run(HEAD,"SOL_ROUTE","solicitation",sol,Map.of("scope_id",scope("UPD"),"reason","Corresponde a UPD"));
        run(DIR_UPD,"SOL_ROUTE","solicitation",sol,Map.of("scope_id",scope("UPR-REQ"),"reason","Corresponde a UPR"));
        long req=run(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF),"reason","Asumida"));
        long t0=at(2,2,9);
        db.update("UPDATE milestone SET at_ms=? WHERE communication_id=? AND event='COM_REGISTRADA'",t0-(long)H,com);
        db.update("UPDATE milestone SET at_ms=? WHERE communication_id=? AND event='SOL_DISTRIBUIDA'",t0,com);
        var redirects=db.rows("SELECT id FROM milestone WHERE communication_id=? AND event='SOL_REDIRIGIDA' ORDER BY id",com);
        assertEquals(2,redirects.size());
        db.update("UPDATE milestone SET at_ms=? WHERE id=?",t0+(long)(2*H),n(redirects.get(0),"id"));
        db.update("UPDATE milestone SET at_ms=? WHERE id=?",t0+(long)(5*H),n(redirects.get(1),"id"));
        db.update("UPDATE milestone SET at_ms=? WHERE communication_id=? AND event IN ('SOL_ASUMIDA','REQ_CREADO')",t0+(long)(11*H),com);
        // Caso del REQ anterior (mayo): espera de un dia, solicitud retirada, nueva solicitud y cierre.
        long cas=run(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",scope("UPR-REQ"),"responsible_id",user(PROF),"objective","Seguimiento sintetico","matter","UPR"));
        db.update("UPDATE milestone SET at_ms=? WHERE case_id=? AND event IN ('CASO_CREADO','CASO_ASIGNADO')",at(5,4,9),cas);
        long prof=user(PROF),head=user(HEAD);
        milestone("CASO_ESPERA",cas,prof,at(5,5,9));milestone("CASO_REANUDADO",cas,prof,at(5,6,9));
        milestone("CIERRE_SOLICITADO",cas,prof,at(5,7,9));milestone("CIERRE_RETIRADO",cas,prof,at(5,7,12));
        milestone("CIERRE_SOLICITADO",cas,prof,at(5,8,9));milestone("CASO_CERRADO",cas,head,at(5,11,9));
        dx=new Data(com,sol,req,cas,regionalOpen);
        return dx;
    }

    Reports.Request q(String report,String from,String to){return new Reports.Request(report,from,to,null,null,null,null,null,null,null,null,null,null,null,null);}
    Reports.Request q(String report,String from,String to,String group){return new Reports.Request(report,from,to,null,null,null,null,null,null,null,null,null,group,null,null);}
    Map<String,Object> report(String who,Reports.Request q){return reports.report(access.actor(who),q);}
    @SuppressWarnings("unchecked") static Map<String,Object> m(Object o){return (Map<String,Object>)o;}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Map<String,Object> r){return (List<Map<String,Object>>)r.get("rows");}
    static Map<String,Object> row(Map<String,Object> r,String field,Object value){
        var found=rows(r).stream().filter(x->Objects.equals(m(x.get("group")).get(field),value)).toList();
        assertEquals(1,found.size(),field+"="+value+" en "+rows(r));return found.getFirst();
    }
    static void stats(Map<String,Object> row,long n,double median,double p90,double max,String unit){
        assertEquals(n,((Number)row.get("n")).longValue(),"n");
        var s=m(row.get(unit));
        assertEquals(median,((Number)s.get("median")).doubleValue(),1e-9,unit+" mediana");
        assertEquals(p90,((Number)s.get("p90")).doubleValue(),1e-9,unit+" p90");
        assertEquals(max,((Number)s.get("max")).doubleValue(),1e-9,unit+" maximo");
    }
    static long count(Map<String,Object> r){return ((Number)r.get("n")).longValue();}

    @Test @Order(1) void percentilInclusivoComoExcel(){
        var v=List.of(10d,20d,30d,40d,100d);
        assertEquals(30,Reports.percentile(v,0.5),1e-9);assertEquals(76,Reports.percentile(v,0.9),1e-9);
        assertEquals(5,Reports.percentile(List.of(5d),0.9),1e-9);
        assertEquals(1.9,Reports.percentile(List.of(1d,2d),0.9),1e-9);
        assertEquals(List.of("0-5","0-5","6-10","11-20","11-20","21+"),List.of(0L,5L,6L,11L,20L,21L).stream().map(Reports::bracket).toList());
    }

    @Test @Order(2) void reqTotalConMedianaP90DiasHabilesYOrigen(){
        data();
        // Por defecto, REQ total viene separado por origen: las atenciones resueltas en el acto duran 0.
        var r=report("rep.inst",q("REQ_TOTAL","2026-03-01","2026-03-31","AMBITO"));
        assertEquals(List.of("AMBITO","ORIGEN"),r.get("dimensions"));
        var central=rows(r).stream().filter(x->m(x.get("group")).get("scope_code").equals("UPR-REQ")).toList();
        var sol=central.stream().filter(x->m(x.get("group")).get("origin").equals("SOL")).findFirst().orElseThrow();
        stats(sol,5,30,76,100,"hours");
        // Dias habiles con el feriado nacional del miercoles 4: [0,1,1,1,3].
        stats(sol,5,1,2.2,3,"business_days");
        var direct=central.stream().filter(x->m(x.get("group")).get("origin").equals("ATENCION_DIRECTA")).findFirst().orElseThrow();
        stats(direct,2,0,0,0,"hours");
        // La sede usa los feriados de su region: lunes 09:00 a viernes 13:00 = 2 dias habiles (el nivel central contaria 3).
        var site=rows(r).stream().filter(x->m(x.get("group")).get("scope_code").equals("REG-DEMO")).toList();
        assertEquals(1,site.size());stats(site.getFirst(),2,100,100,100,"hours");stats(site.getFirst(),2,2,2,2,"business_days");
        assertEquals(Reports.CALENDAR_NOTE,m(r.get("calendar")).get("note"));
        assertEquals(List.of(),m(r.get("calendar")).get("missing_years"));
        // Sin separar por origen, un solo grupo con las 9; filtro por origen.
        var all=report("rep.inst",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,null,null,false,null));
        assertEquals(List.of(),all.get("dimensions"));assertEquals(9,count(m(all.get("total"))));assertEquals(1,rows(all).size());
        var onlyDirect=report("rep.inst",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,"ATENCION_DIRECTA",null,false,null));
        stats(m(onlyDirect.get("total")),2,0,0,0,"hours");
        var byOrigin=report("rep.inst",q("REQ_TOTAL","2026-03-01","2026-03-31","ORIGEN"));
        assertEquals(List.of("ORIGEN"),byOrigin.get("dimensions"));
        assertEquals(7,count(row(byOrigin,"origin","SOL")));assertEquals(2,count(row(byOrigin,"origin","ATENCION_DIRECTA")));
        // Origen tambien filtra los demas tramos: el Caso nace de un REQ con SOL.
        var gestion=report("rep.inst",new Reports.Request("GESTION_CASO","2026-05-01","2026-05-31",null,null,null,null,null,null,null,null,"ATENCION_DIRECTA",null,null,null));
        assertEquals(0,count(m(gestion.get("total"))));
        // Un periodo sin feriados nacionales cargados lo advierte.
        var later=report("rep.inst",q("REQ_TOTAL","2025-12-01","2026-03-31"));
        assertEquals(List.of(2025),m(later.get("calendar")).get("missing_years"));
        assertEquals(List.of("Sin feriados cargados para 2025."),m(later.get("calendar")).get("warnings"));
    }

    @Test @Order(3) void cruceAmbitoPorMateriaYAlcance(){
        data();
        var cross=report("rep.inst",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,null,"AMBITO_MATERIA",false,null));
        assertEquals(List.of("AMBITO","MATERIA"),cross.get("dimensions"));
        var keys=rows(cross).stream().map(x->m(x.get("group")).get("scope_code")+"/"+m(x.get("group")).get("matter")+"="+x.get("n")).sorted().toList();
        assertEquals(List.of("REG-DEMO/UPD=1","REG-DEMO/UPR=1","UPR-REQ/null=7"),keys);
        // Raiz de UPD: sus ambitos centrales (sin REQ) mas su materia en sedes.
        var upd=report("rep.upd",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,null,"AMBITO_MATERIA",false,null));
        assertEquals(List.of("REG-DEMO/UPD"),rows(upd).stream().map(x->m(x.get("group")).get("scope_code")+"/"+m(x.get("group")).get("matter")).toList());
        // Raiz de UPR: UPR-REQ (ambito central de la unidad) y la materia UPR en la sede.
        var upr=report("rep.upr",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,null,"AMBITO_MATERIA",false,null));
        assertEquals(List.of("REG-DEMO/UPR=1","UPR-REQ/null=7"),rows(upr).stream().map(x->m(x.get("group")).get("scope_code")+"/"+m(x.get("group")).get("matter")+"="+x.get("n")).sorted().toList());
        // Area: solo ese ambito. Sede: solo la sede, con sus dos materias.
        assertEquals(7,count(m(report("rep.area",q("REQ_TOTAL","2026-03-01","2026-03-31")).get("total"))));
        var site=report("rep.sede",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,null,"MATERIA",false,null));
        assertEquals(1,count(row(site,"matter","UPD")));assertEquals(1,count(row(site,"matter","UPR")));
        // Un filtro solo acota dentro del alcance: el area pidiendo la sede no recibe nada.
        var outside=report("rep.area",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,scope("REG-DEMO"),null,null,null,null,null,null,null,null,null,null));
        assertEquals(0,count(m(outside.get("total"))));
        var matterFilter=report("rep.inst",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,"REGIONAL",null,null,"UPD",null,null,null,false,null));
        assertEquals(1,count(m(matterFilter.get("total"))));
    }

    @Test @Order(4) void tenenciaAsuncionYSolTotalConDosRedirecciones(){
        var d=data();
        var tenure=report("rep.inst",q("TENENCIA_SOL","2026-02-01","2026-02-28","AMBITO"));
        assertEquals(List.of("AMBITO","SALIDA"),tenure.get("dimensions"));
        var keys=rows(tenure).stream().map(x->m(x.get("group")).get("scope_code")+"/"+m(x.get("group")).get("exit")+"="+m(x.get("hours")).get("max")).sorted().toList();
        assertEquals(List.of("UPD/SOL_REDIRIGIDA=3.0","UPR-REQ/SOL_ASUMIDA=6.0","UPR-REQ/SOL_REDIRIGIDA=2.0"),keys);
        assertEquals(3,count(m(tenure.get("total"))));
        stats(m(report("rep.inst",q("ASUNCION","2026-02-01","2026-02-28")).get("total")),1,6,6,6,"hours");
        stats(m(report("rep.inst",q("SOL_TOTAL","2026-02-01","2026-02-28")).get("total")),1,11,11,11,"hours");
        stats(m(report("rep.inst",q("DISTRIBUCION","2026-02-01","2026-02-28")).get("total")),1,1,1,1,"hours");
        // La tenencia en UPD es de la jefatura que la recibio: la ve la raiz de UPD, no el area de UPR.
        assertEquals(1,count(m(report("rep.upd",q("TENENCIA_SOL","2026-02-01","2026-02-28")).get("total"))));
        assertEquals(2,count(m(report("rep.area",q("TENENCIA_SOL","2026-02-01","2026-02-28")).get("total"))));
        // Desglose por persona: quien realiza la salida (la jefatura receptora).
        var people=report("rep.pers",q("TENENCIA_SOL","2026-02-01","2026-02-28","PERSONA"));
        var names=rows(people).stream().map(x->m(x.get("group")).get("person_name")+"/"+m(x.get("group")).get("exit")).sorted().toList();
        assertEquals(List.of("Coordinadora de Requerimientos sintetica/SOL_ASUMIDA","Coordinadora de Requerimientos sintetica/SOL_REDIRIGIDA","Direccion UPD sintetica/SOL_REDIRIGIDA"),names);
        assertTrue(d.tenureSol()>0);
    }

    @Test @Order(5) void gestionConEsperaRevisionYApertura(){
        data();
        // Lunes 4-5 09:00 a lunes 11-5 09:00 = 168 h; espera martes a miercoles (24 h, 1 dh) descontada e informada aparte.
        var g=report("rep.inst",q("GESTION_CASO","2026-05-01","2026-05-31"));
        var total=m(g.get("total"));stats(total,1,144,144,144,"hours");stats(total,1,4,4,4,"business_days");
        var wait=m(total.get("wait"));assertEquals(1L,((Number)wait.get("n")).longValue());
        assertEquals(24.0,((Number)m(wait.get("hours")).get("max")).doubleValue(),1e-9);assertEquals(1.0,((Number)m(wait.get("business_days")).get("max")).doubleValue(),1e-9);
        // La primera solicitud se retiro: solo cuenta viernes 8 09:00 -> lunes 11 09:00.
        stats(m(report("rep.inst",q("REVISION_CIERRE","2026-05-01","2026-05-31")).get("total")),1,72,72,72,"hours");
        // Apertura: REQ creado en febrero (inicio, ancla del tramo) hasta el primer Caso en mayo.
        var opening=m(report("rep.inst",q("APERTURA_CASO","2026-05-01","2026-05-31")).get("total"));
        assertEquals(1,count(opening));
        assertEquals((at(5,4,9)-(at(2,2,9)+(long)(11*H)))/H,((Number)m(opening.get("hours")).get("max")).doubleValue(),0.01);
        // Gestion por persona = responsable del Caso.
        var people=report("rep.pers",q("GESTION_CASO","2026-05-01","2026-05-31","PERSONA"));
        assertEquals("Profesional Requerimientos sintetico",m(rows(people).getFirst().get("group")).get("person_name"));
    }

    @Test @Order(6) void reconstruidosVolumenYAntiguedad(){
        var d=data();
        long req=n(db.one("SELECT requirement_id FROM milestone WHERE event='REQ_CERRADO' AND at_ms=? ORDER BY id LIMIT 1",at(3,2,9)+(long)(10*H)),"requirement_id");
        db.update("UPDATE milestone SET reconstructed=TRUE WHERE requirement_id=? AND event='REQ_CREADO'",req);
        try{
            var r=report("rep.area",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,null,null,false,null));
            assertEquals(1L,((Number)m(r.get("total")).get("reconstructed")).longValue());
        }finally{db.update("UPDATE milestone SET reconstructed=FALSE WHERE requirement_id=?",req);}
        // Volumen del ultimo anio (fechas de registro reales): COM por ingreso, REQ por origen y REQ regionales sin materia.
        var v=report("rep.inst",q("VOLUMEN",null,null));
        var com=m(v.get("com"));var reqs=m(v.get("req"));var cases=m(v.get("cases"));
        assertEquals(11L,com.get("total"));
        assertEquals(List.of(Map.of("value","ATENCION_DIRECTA","count",2L),Map.of("value","MANUAL","count",9L)),com.get("by_intake"));
        assertEquals(11L,reqs.get("total"));assertEquals(1L,reqs.get("regional_without_matter"));
        assertEquals(1L,reqs.get("with_case"));assertEquals(10L,reqs.get("without_case"));
        assertEquals(List.of(Map.of("value","ATENCION_DIRECTA","count",2L),Map.of("value","SOL","count",9L)),reqs.get("by_origin"));
        assertEquals(1L,cases.get("total"));
        // La raiz de UPD ve solo la materia UPD de la sede.
        var upd=m(report("rep.upd",q("VOLUMEN",null,null)).get("req"));
        assertEquals(1L,upd.get("total"));assertEquals(0L,upd.get("regional_without_matter"));
        // Antiguedad: REQ abiertos (el de la SOL redirigida y el regional sin materia) y el Caso abierto, por dias habiles.
        db.update("UPDATE requirement SET created_ms=? WHERE id=?",System.currentTimeMillis()-60L*86_400_000,d.regionalOpen());
        var age=report("rep.inst",q("ANTIGUEDAD",null,null,"AMBITO"));
        var open=m(age.get("req"));assertEquals(2L,open.get("n"));
        assertEquals(1L,m(open.get("brackets")).get("21+"));assertEquals(1L,m(open.get("brackets")).get("0-5"));
        assertEquals(1L,m(age.get("cases")).get("n"));
        assertThrows(Problem.class,()->report("rep.inst",q("ANTIGUEDAD",null,null,"PERSONA")));
    }

    @Test @Order(7) void personaSoloConSuRolTiYLimite(){
        data();
        // Sin REPORTES_PERSONAS: la agrupacion por persona es 403 y ningun resultado trae datos de personas.
        assertEquals(403,assertThrows(Problem.class,()->report("rep.inst",q("TENENCIA_SOL","2026-02-01","2026-02-28","PERSONA"))).status);
        for(var t:Reports.TIMES)assertFalse(report("rep.inst",q(t.code(),"2026-01-01","2026-12-31")).toString().contains("person"),t.code());
        // REPORTES_PERSONAS institucional sin REPORTES institucional (solo de UPR) no rige.
        assertFalse(Reports.canReportPeople(access.actor("rep.mixto")));
        assertEquals(403,assertThrows(Problem.class,()->report("rep.mixto",q("REQ_TOTAL","2026-03-01","2026-03-31","PERSONA"))).status);
        assertEquals(403,assertThrows(Problem.class,()->report("rep.solo.personas",q("REQ_TOTAL",null,null))).status);
        // TI (D2) y cuentas operativas sin REPORTES: 403.
        for(var who:List.of("ti",HEAD,"auditoria","gabinete"))assertEquals(403,assertThrows(Problem.class,()->report(who,q("VOLUMEN",null,null))).status,who);
        // Limite de filas: 409 con la indicacion de acotar; acotando el ambito, el mismo reporte responde.
        reports.limit=2;
        try{
            var e=assertThrows(Problem.class,()->report("rep.inst",q("REQ_TOTAL","2026-03-01","2026-03-31")));
            assertEquals(409,e.status);assertTrue(e.getMessage().contains("acota el periodo o el ámbito"),e.getMessage());
            var narrow=report("rep.inst",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,scope("REG-DEMO"),null,null,null,null,null,null,null,null,null,null));
            assertEquals(2,count(m(narrow.get("total"))));
        }finally{reports.limit=Reports.LIMIT;}
        // Entradas invalidas: 400.
        assertEquals(400,assertThrows(Problem.class,()->report("rep.inst",q("NO_EXISTE",null,null))).status);
        assertEquals(400,assertThrows(Problem.class,()->report("rep.inst",q("REQ_TOTAL","2026-03-31","2026-03-01"))).status);
        assertEquals(400,assertThrows(Problem.class,()->report("rep.inst",q("VOLUMEN",null,null,"AMBITO"))).status);
    }

    @Test @Order(8) void auditoriaSinValoresDeFiltros(){
        data();
        long before=db.count("SELECT COUNT(*) FROM audit_event WHERE action='REPORTE'");
        report("rep.inst",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31","UPR",scope("UPR-REQ"),null,null,"BAJA","TELEFONO","UPR",null,"SOL","AMBITO",null,null));
        assertEquals(before+1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='REPORTE'"));
        var e=db.one("SELECT * FROM audit_event WHERE action='REPORTE' ORDER BY id DESC LIMIT 1");
        String note=s(e,"note").isEmpty()?e.values().toString():s(e,"note");
        assertTrue(note.contains("REQ_TOTAL")&&note.contains("periodo")&&note.contains("unidad")&&note.contains("canal"),note);
        for(var value:List.of("2026-03","TELEFONO","BAJA","UPR-REQ","SOL;"))assertFalse(note.contains(value),value+" en "+note);
        assertEquals(user("rep.inst"),n(e,"actor_id"));
    }

    @Test @Order(9) void perfilConservaReportesPorAmbitoYExigeReportes(){
        data();
        long ti=user("ti");
        reporter("rep.perfil","REPORTES@UPR","REPORTES_PERSONAS@UPR");
        long id=user("rep.perfil");
        // El perfil reenviado tal como lo hace la interfaz (REPORTES por ambito hecha por API) no tiene cambios.
        Map<String,Object> same=Map.of("grants",List.of(Map.of("role","REPORTES","scope_id",scope("UPR"),"from_at",Instant.ofEpochMilli(0).toString()),Map.of("role","REPORTES_PERSONAS","scope_id",scope("UPR"))),"reason","Sin cambios");
        var e=assertThrows(Problem.class,()->run("ti","PROFILE_SET","staff",id,same));
        assertEquals("El perfil no tiene cambios.",e.getMessage());
        // Agregar REPORTES institucional conserva las concesiones por ambito.
        run("ti","PROFILE_SET","staff",id,Map.of("grants",List.of(Map.of("role","REPORTES","scope_id",scope("UPR")),Map.of("role","REPORTES_PERSONAS","scope_id",scope("UPR")),Map.of("role","REPORTES")),"reason","Agrega institucional"));
        assertEquals(List.of("REPORTES@"+scope("UPR"),"REPORTES@null","REPORTES_PERSONAS@"+scope("UPR")),db.rows("SELECT role_code,scope_id FROM role_assignment WHERE user_id=? AND revoked_at IS NULL",id).stream().map(r->s(r,"role_code")+"@"+r.get("scope_id")).sorted().toList());
        // REPORTES_PERSONAS exige REPORTES en el mismo alcance: en el perfil, al otorgar y al revocar.
        var orphan=assertThrows(Problem.class,()->run("ti","PROFILE_SET","staff",id,Map.of("grants",List.of(Map.of("role","REPORTES"),Map.of("role","REPORTES_PERSONAS","scope_id",scope("UPR"))),"reason","Quita REPORTES de UPR")));
        assertEquals(Personnel.REPORT_PEOPLE_REQUIRES,orphan.getMessage());
        long other=user("rep.upd");
        var grant=assertThrows(Problem.class,()->run("ti","GRANT_CREATE",null,null,Map.of("user_id",other,"role","REPORTES_PERSONAS","from_at",Instant.now().toString(),"reason","Sin REPORTES institucional")));
        assertEquals(Personnel.REPORT_PEOPLE_REQUIRES,grant.getMessage());
        run("ti","GRANT_CREATE",null,null,Map.of("user_id",other,"role","REPORTES_PERSONAS","scope_id",scope("UPD"),"from_at",Instant.now().toString(),"reason","Con REPORTES de UPD"));
        long reportes=n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND role_code='REPORTES' AND revoked_at IS NULL",other),"id");
        assertEquals(409,assertThrows(Problem.class,()->run("ti","GRANT_REVOKE",null,reportes,Map.of("reason","Revoca")))
            .status);
        // TI no combina con REPORTES (D2), en ningun sentido.
        assertThrows(Problem.class,()->run("ti","GRANT_CREATE",null,null,Map.of("user_id",ti,"role","REPORTES","from_at",Instant.now().toString(),"reason","Autoconcesion")));
        var mixed=assertThrows(Problem.class,()->run("ti","PROFILE_SET","staff",other,Map.of("grants",List.of(Map.of("role","TI"),Map.of("role","REPORTES")),"reason","Mezcla")));
        assertTrue(mixed.getMessage().contains("TI es incompatible"),mixed.getMessage());
        // Catalogo y sesion.
        assertTrue(Roles.json().contains(Map.of("code","REPORTES","reach","OPCIONAL","group","REPORTES")));
        assertTrue(Reports.canReportPeople(access.actor("rep.upd")));
    }

    @Test @Order(10) void sedeCerradaSigueEnLosReportes(){
        data();
        long site=scope("REG-DEMO");
        db.update("UPDATE org_scope SET state='CERRADA',enabled=FALSE WHERE id=?",site);
        try{
            // La concesion sobre la sede cerrada deja de regir...
            assertFalse(Reports.canReport(access.actor("rep.sede")));
            assertEquals(403,assertThrows(Problem.class,()->report("rep.sede",q("REQ_TOTAL","2026-03-01","2026-03-31"))).status);
            // ...pero sus registros historicos siguen en los reportes de institucion y de materia.
            var inst=report("rep.inst",new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,null,null,null,null,null,null,null,null,"AMBITO",false,null));
            assertEquals(2,count(row(inst,"scope_code","REG-DEMO")));
            var upd=report("rep.upd",q("REQ_TOTAL","2026-03-01","2026-03-31","AMBITO_MATERIA"));
            assertEquals(1,count(m(upd.get("total"))));
            assertEquals(1L,m(report("rep.inst",q("VOLUMEN",null,null)).get("req")).get("regional_without_matter"));
        }finally{db.update("UPDATE org_scope SET state='ACTIVA',enabled=TRUE WHERE id=?",site);}
    }

    /** CSV de la exportacion: sin BOM, filas separadas por punto y coma y celdas sin comillas. */
    static List<List<String>> parse(byte[] bytes){
        String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.startsWith("\ufeff"),"BOM UTF-8");
        var out=new ArrayList<List<String>>();
        for(var line:text.substring(1).split("\r\n")){
            if(line.isEmpty())continue;
            var cells=new ArrayList<String>();
            for(var c:line.split(";",-1)){assertTrue(c.startsWith("\"")&&c.endsWith("\""),"celda entre comillas: "+line);cells.add(c.substring(1,c.length()-1).replace("\"\"","\""));}
            out.add(cells);
        }
        return out;
    }
    static Map<String,String> line(List<List<String>> csv,String folio){
        var header=csv.getFirst();var found=csv.stream().skip(1).filter(r->r.getFirst().equals(folio)).toList();
        assertEquals(1,found.size(),folio);var out=new LinkedHashMap<String,String>();
        for(int i=0;i<header.size();i++)out.put(header.get(i),found.getFirst().get(i));
        return out;
    }
    String folio(String table,long id){return s(db.one("SELECT folio FROM "+table+" WHERE id=?",id),"folio");}

    @Test @Order(11) void exportacionCsvSinDatosCiudadanosConAlcanceYLimite(){
        var d=data();
        reporter("rep.exp","REPORTES","EXPORTACION");reporter("rep.exp.upr","REPORTES@UPR","EXPORTACION@UPR");
        reporter("rep.exp.sola","EXPORTACION");reporter("rep.exp.mixto","REPORTES@UPR","EXPORTACION");
        // Formato: coma decimal, fechas en hora de Chile, inyeccion de formulas neutralizada.
        assertEquals("1,5",Csv.decimal(1.5));assertEquals("2,25",Csv.decimal(2.25));assertEquals("30",Csv.decimal(30));assertEquals("0",Csv.decimal(0));
        assertEquals("2026-03-02 09:00",Csv.when(at(3,2,9)));assertNull(Csv.when(null));
        assertEquals("\"'=1+1\"",Csv.cell("=1+1"));assertEquals("\"a \"\"b\"\"\"",Csv.cell("a \"b\""));
        // EXPORTACION solo rige con REPORTES en el mismo alcance; sin ella (o como TI), 403.
        assertTrue(Reports.canExport(access.actor("rep.exp")));assertTrue(Reports.canExport(access.actor("rep.exp.upr")));
        for(var who:List.of("rep.exp.sola","rep.exp.mixto","rep.inst","ti",HEAD)){
            assertFalse(Reports.canExport(access.actor(who)),who);
            assertEquals(403,assertThrows(Problem.class,()->reports.export(access.actor(who),"REQ",q(null,null,null))).status,who);
        }
        assertEquals(400,assertThrows(Problem.class,()->reports.export(access.actor("rep.exp"),"COM",q(null,null,null))).status);
        // REQ: columnas exactas, ambito por su codigo y tiempos de los tramos (30 h = 1 dh con el feriado del miercoles 4).
        var req=reports.export(access.actor("rep.exp"),"REQ",q(null,null,null));
        var csv=parse(req.bytes());
        assertEquals(Reports.REQ_COLUMNS,csv.getFirst());
        assertEquals(csv.size()-1,req.rows());assertTrue(req.filename().matches("siger-req-\\d{4}-\\d{2}-\\d{2}-\\d{4}-\\d{2}-\\d{2}\\.csv"),req.filename());
        long thirty=n(db.one("SELECT r.id FROM requirement r JOIN communication c ON c.id=r.communication_id WHERE c.subject='REQ sintetico 30'"),"id");
        var row=line(csv,folio("requirement",thirty));
        assertEquals("UPR-REQ",row.get("ambito"));assertEquals("UPR",row.get("unidad"));assertEquals("CENTRAL",row.get("nivel"));assertEquals("SOL",row.get("origen"));
        assertEquals("TELEFONO",row.get("canal"));assertEquals("MANUAL",row.get("ingreso"));assertEquals("CERRADO",row.get("estado"));
        assertEquals("30",row.get("h_req_total"));assertEquals("1",row.get("dh_req_total"));assertEquals("0",row.get("casos"));assertEquals("",row.get("h_primer_caso"));
        assertTrue(row.get("creado").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")&&row.get("cerrado").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),row.toString());
        var regional=line(csv,folio("requirement",d.regionalOpen()));
        assertEquals("REG-DEMO",regional.get("ambito"));assertEquals("REGIONAL",regional.get("nivel"));assertEquals("05",regional.get("region"));
        assertEquals("ABIERTO",regional.get("estado"));assertEquals("",regional.get("cerrado"));assertEquals("",regional.get("h_req_total"));
        var withCase=line(csv,folio("requirement",d.tenureReq()));
        assertEquals("1",withCase.get("casos"));
        // Ningun dato ciudadano ni de funcionarios: asunto, relato, motivo, objetivo, nombres.
        String text=new String(req.bytes(),java.nio.charset.StandardCharsets.UTF_8);
        var people=db.rows("SELECT name FROM staff WHERE technical=FALSE").stream().map(r->s(r,"name")).toList();
        for(var forbidden:List.of("REQ sintetico","Relato sintetico","Orientacion","Cierre sintetico","Asumida","Clasificacion"))assertFalse(text.contains(forbidden),forbidden);
        for(var name:people)assertFalse(text.contains(name),name);
        // Casos: gestion de 168 h menos 24 h de espera (5 dh menos 1), contada solo con el Caso cerrado.
        String caseState=s(db.one("SELECT state FROM case_record WHERE id=?",d.cas()),"state");
        db.update("UPDATE case_record SET state='CERRADO',closed_ms=? WHERE id=?",at(5,11,9),d.cas());
        try{
            var cases=reports.export(access.actor("rep.exp"),"CAS",q(null,null,null));
            var ccsv=parse(cases.bytes());
            assertEquals(Reports.CASE_COLUMNS,ccsv.getFirst());
            var c=line(ccsv,folio("case_record",d.cas()));
            assertEquals(folio("requirement",d.tenureReq()),c.get("folio_req"));assertEquals("UPR-REQ",c.get("ambito"));assertEquals("UPR",c.get("materia"));
            assertEquals("144",c.get("h_gestion"));assertEquals("4",c.get("dh_gestion"));assertEquals("24",c.get("h_espera"));assertEquals("1",c.get("dh_espera"));
            assertEquals("2026-05-11 09:00",c.get("cerrado"));
            String ctext=new String(cases.bytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(ctext.contains("Seguimiento sintetico"));for(var name:people)assertFalse(ctext.contains(name),name);
        }finally{db.update("UPDATE case_record SET state=?,closed_ms=NULL WHERE id=?",caseState,d.cas());}
        // Alcance: la raiz de UPR ve sus ambitos centrales y, en sedes, solo su materia.
        var upr=parse(reports.export(access.actor("rep.exp.upr"),"REQ",q(null,null,null)).bytes());
        int scopeCol=Reports.REQ_COLUMNS.indexOf("ambito"),matterCol=Reports.REQ_COLUMNS.indexOf("materia");
        var codes=new HashSet<String>();for(var r:db.rows("SELECT code FROM org_scope WHERE unit_code='UPR' AND regional=FALSE"))codes.add(s(r,"code"));
        assertTrue(upr.size()>1);
        for(var r:upr.subList(1,upr.size()))assertTrue(codes.contains(r.get(scopeCol))||r.get(scopeCol).equals("REG-DEMO")&&r.get(matterCol).equals("UPR"),r.toString());
        assertEquals(1,upr.stream().skip(1).filter(r->r.get(scopeCol).equals("REG-DEMO")).count());
        assertTrue(csv.stream().skip(1).filter(r->r.get(scopeCol).equals("REG-DEMO")).count()>=3);
        // Limite: 409 sin archivo truncado; acotando el ambito y la materia, responde.
        reports.limit=2;
        try{
            var e=assertThrows(Problem.class,()->reports.export(access.actor("rep.exp"),"REQ",q(null,null,null)));
            assertEquals(409,e.status);assertTrue(e.getMessage().contains("acota el periodo o el ámbito"),e.getMessage());
            var narrow=reports.export(access.actor("rep.exp"),"REQ",new Reports.Request(null,null,null,null,scope("REG-DEMO"),null,null,null,null,"UPD",null,null,null,null,null));
            assertEquals(1,narrow.rows());
        }finally{reports.limit=Reports.LIMIT;}
        // Auditoria: tipo, cantidad y solo los nombres de los filtros.
        var e=db.one("SELECT * FROM audit_event WHERE action='EXPORTACION' ORDER BY id DESC LIMIT 1");
        assertEquals(user("rep.exp"),n(e,"actor_id"));
        String note=s(e,"note");
        assertTrue(note.contains("Exportacion REQ")&&note.contains("filas 1")&&note.contains("ambito")&&note.contains("materia"),note);
        for(var value:List.of("REG-DEMO","UPD;",String.valueOf(scope("REG-DEMO"))))assertFalse(note.contains(value),value+" en "+note);
    }

    @Test @Order(12) void exportacionExigeReportesEnElMismoAlcance(){
        data();
        reporter("rep.exp.perfil","REPORTES@UPD");
        long id=user("rep.exp.perfil"),ti=user("ti");
        var grant=assertThrows(Problem.class,()->run("ti","GRANT_CREATE",null,null,Map.of("user_id",id,"role","EXPORTACION","from_at",Instant.now().toString(),"reason","Sin REPORTES institucional")));
        assertEquals(Personnel.EXPORT_REQUIRES,grant.getMessage());
        var orphan=assertThrows(Problem.class,()->run("ti","PROFILE_SET","staff",id,Map.of("grants",List.of(Map.of("role","REPORTES","scope_id",scope("UPD")),Map.of("role","EXPORTACION")),"reason","Sin REPORTES institucional")));
        assertEquals(Personnel.EXPORT_REQUIRES,orphan.getMessage());
        run("ti","PROFILE_SET","staff",id,Map.of("grants",List.of(Map.of("role","REPORTES","scope_id",scope("UPD")),Map.of("role","EXPORTACION","scope_id",scope("UPD"))),"reason","Exportacion de UPD"));
        assertTrue(Reports.canExport(access.actor("rep.exp.perfil")));
        long reportes=n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND role_code='REPORTES' AND revoked_at IS NULL",id),"id");
        var revoke=assertThrows(Problem.class,()->run("ti","GRANT_REVOKE",null,reportes,Map.of("reason","Revoca")));
        assertEquals(409,revoke.status);assertTrue(revoke.getMessage().contains("Exportacion"),revoke.getMessage());
        // TI no combina con EXPORTACION (D2) y el catalogo la publica con ambito opcional.
        assertThrows(Problem.class,()->run("ti","GRANT_CREATE",null,null,Map.of("user_id",ti,"role","EXPORTACION","from_at",Instant.now().toString(),"reason","Autoconcesion")));
        var mixed=assertThrows(Problem.class,()->run("ti","PROFILE_SET","staff",id,Map.of("grants",List.of(Map.of("role","TI"),Map.of("role","EXPORTACION")),"reason","Mezcla")));
        assertTrue(mixed.getMessage().contains("TI es incompatible"),mixed.getMessage());
        assertTrue(Roles.json().contains(Map.of("code","EXPORTACION","reach","OPCIONAL","group","REPORTES")));
    }
}
