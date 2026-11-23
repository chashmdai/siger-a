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
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;
import static cl.nexbyte.sigera.ReportsTest.m;
import static cl.nexbyte.sigera.ReportsTest.rows;
import static cl.nexbyte.sigera.ReportsTest.parse;
import static cl.nexbyte.sigera.ReportsTest.line;

/**
 * Region de origen de la persona (0.7.0, D21-D22, gate T02). Escenarios 1-4 del plan con sedes que cubren otra region
 * (Coquimbo 04 cubre Atacama 03, O'Higgins 06 cubre Maule 07, Biobio 08 cubre Nuble 16, La Araucania 09 cubre Los Rios
 * 14), por atencion directa y por el Formulario: region registrada, region de la sede y sede responsable distintas en
 * reportes (filtro origin_region, agrupaciones REGION_ORIGEN y REGION, corrected) y en la exportacion (region_sede,
 * region_registrada, region_vigente). Una correccion no cambia el reporte, completar no es correccion y quitar si lo es.
 * Auditoria de FICHA_COMPLEMENTADA sin codigos. Region de la ficha solo si es legible. Regla unica: la region vigente
 * del SQL de los reportes es la propuesta de destino (Access.suggestedRegion) en todos los casos. Feriados (D22): los
 * dias habiles descuentan los de la region de la sede, no los de la region de origen. Reconstruccion de V16 con marcador.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_region_origen;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-region-origen-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OriginRegionTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Access access;@Autowired Db db;@Autowired Reports reports;@Autowired Attention attention;@Autowired OriginRegion regions;

    static final String PASSWORD="Clave-Temporal-Sintetica-2026",BASIS="Resolucion exenta sintetica de cobertura";
    static final double H=3_600_000d;
    static Data dx;
    /** REQ de cada escenario y de los casos de correccion. */
    record Data(long atacama,long maule,long nuble,long losRios,long fixed,long completed,long removed,long manual,long nnaText,long caseAtacama,long caseFixed){}

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long rev(String table,long id){return n(db.one("SELECT "+(table.equals("staff")?"security_version":"revision")+" AS revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    long site(String code,String region,String covers){
        long id=run("ti","SCOPE_CREATE",null,null,Map.of("unit_code","REG","code",code,"name","Sede sintetica "+code,"site_code","SEDE-"+code,"regional",true,"region_code",region,"state","ACTIVA","reason",BASIS)).id();
        run("ti","SCOPE_COVERAGE",id,rev("org_scope",id),Map.of("regions",List.of(covers),"reason",BASIS));
        String head="coord."+region;
        long staff=run("ti","STAFF_CREATE",null,null,Map.of("username",head,"name","Cuenta sintetica "+head,"scope_id",id,"password",PASSWORD,"reason",BASIS)).id();
        db.update("UPDATE staff SET must_change=FALSE WHERE id=?",staff);
        run("ti","PROFILE_SET",staff,rev("staff",staff),Map.of("grants",List.of(Map.of("role","DIRECCION","scope_id",id),Map.of("role","PROFESIONAL","scope_id",id)),"reason",BASIS));
        return id;
    }
    static Map<String,Object> form(String modality,String region){
        var f=new LinkedHashMap<String,Object>(Map.of("schema_version","1.1","modality",modality,"motive","Consulta sintetica","course","EN_ATENCION","people",List.of()));
        if(region!=null)f.put("region",region);
        return f;
    }
    long attend(String site,String modality,String region){
        return run("coord."+region(site),"ATTENTION_CREATE",null,null,new LinkedHashMap<>(Map.of("scope_id",scope(site),"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(600).toString(),"form",form(modality,region)))).id();
    }
    static String region(String site){return site.substring(4);}
    void save(String site,long req,String region){
        run("coord."+region(site),"ATTENTION_SAVE",req,rev("requirement",req),Map.of("form",form("GENERAL",region),"reason","Complementacion sintetica de la ficha"));
    }
    /** COM del Formulario recibida en el nivel central, distribuida a la sede y asumida por su coordinacion. */
    long formulario(ObjectNode doc,String site){
        long com=flow.conserveExternal("administrativo",scope("UPR-REQ"),sign(doc)).id();
        long sol=run("distribucion","COM_ROUTE",com,rev("communication",com),Map.of("scope_id",scope(site),"reason","Clasificacion sintetica")).id();
        return run("coord."+region(site),"SOL_ASSUME",sol,rev("solicitation",sol),Map.of("responsible_id",user("coord."+region(site)),"reason","Asume la sede")).id();
    }
    long openCase(String site,long req){
        return run("coord."+region(site),"REQ_CASE_CREATE",req,rev("requirement",req),Map.of("scope_id",scope(site),"responsible_id",user("coord."+region(site)),"objective","Seguimiento sintetico","matter","UPR")).id();
    }
    void reporter(String username,String... roles){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",username)>0)return;
        long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'x',?,FALSE)",username,"Cuenta "+username,scope("UGI"));
        for(var r:roles)db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'Prueba sintetica')",id,r);
    }

    synchronized Data data(){
        if(dx!=null)return dx;
        db.update("UPDATE staff SET must_change=FALSE");
        reporter("rep.region","REPORTES","EXPORTACION");
        site("REG-04","04","03");site("REG-06","06","07");site("REG-08","08","16");site("REG-09","09","14");
        // Escenario 1: atencion directa en Coquimbo de una persona de Atacama.
        long atacama=attend("REG-04","GENERAL","03");
        // Escenario 2: Formulario NNA de Maule, distribuido a O'Higgins.
        var campos=new LinkedHashMap<String,String>();
        campos.put("nna-region","07");campos.put("nna-commune","Talca");campos.put("nna-story","Relato NNA sintetico");campos.put("nna-primary-method","unsafe");
        campos.put("nna-extra-contact","true");campos.put("nna-secondary-method","email");campos.put("nna-secondary-email","camila@example.cl");
        campos.put("nna-contact-instructions","Solo despues de clases");
        long maule=formulario(com("NNA",RECEIVED,campos,List.of(person("presentador-nna","PRESENTADOR",false,null,null,"primerNombre",attr("Camila",DECL,NOCONTR),"edad",attr("14",DECL,NOCONTR))),List.of(),List.of()),"REG-06");
        // Escenario 3: Formulario adulto de Nuble, distribuido a Biobio.
        var adult=cuVulneration();((ObjectNode)adult.path("datos").path("campos")).put("adult-region","16").put("adult-commune","Chillán");
        long nuble=formulario(adult,"REG-08");
        // Escenario 4: atencion directa en La Araucania de una persona de Los Rios.
        long losRios=attend("REG-09","GENERAL","14");
        // Correccion (03 -> 04), completar (sin region -> 03) y quitar (03 -> sin region), todas en Coquimbo.
        long fixed=attend("REG-04","GENERAL","03");save("REG-04",fixed,"04");
        long completed=attend("REG-04","GENERAL",null);save("REG-04",completed,"03");
        long removed=attend("REG-04","GENERAL","03");save("REG-04",removed,"");
        // COM manual sin region, distribuida y asumida en Coquimbo; ficha NNA con region en texto libre.
        long com=run("administrativo","COM_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",Instant.now().minusSeconds(300).toString(),"subject","COM manual","body","Relato sintetico")).id();
        long sol=run("distribucion","COM_ROUTE",com,rev("communication",com),Map.of("scope_id",scope("REG-04"),"reason","Clasificacion")).id();
        long manual=run("coord.04","SOL_ASSUME",sol,rev("solicitation",sol),Map.of("responsible_id",user("coord.04"),"reason","Asume la sede")).id();
        long nnaText=attend("REG-04","NNA","Atacama");
        long caseAtacama=openCase("REG-04",atacama),caseFixed=openCase("REG-04",fixed);
        dx=new Data(atacama,maule,nuble,losRios,fixed,completed,removed,manual,nnaText,caseAtacama,caseFixed);
        return dx;
    }
    Reports.Request q(String report,Long scope,String originRegion,String group){
        return new Reports.Request(report,null,null,null,scope,null,null,null,null,null,null,null,group,false,null,originRegion);
    }
    Map<String,Object> report(Reports.Request q){return reports.report(access.actor("rep.region"),q);}
    Map<String,Object> region(long req){return regions.ofRequirement(req);}
    void origin(long req,String registered,String current,boolean corrected){
        assertEquals(Arrays.asList(registered,current,corrected),Arrays.asList(region(req).get("registered"),region(req).get("current"),region(req).get("corrected")),"REQ "+req);
    }
    String folio(String table,long id){return s(db.one("SELECT folio FROM "+table+" WHERE id=?",id),"folio");}
    /** Filas de antiguedad (REQ) de una sede agrupadas: valor de la dimension -> n. */
    Map<Object,Long> ageBy(String site,String group,String field){
        var out=new HashMap<Object,Long>();
        for(var r:rows(m(report(q("ANTIGUEDAD",scope(site),null,group)).get("req"))))out.put(m(r.get("group")).get(field),((Number)r.get("n")).longValue());
        return out;
    }

    @Test @Order(1) void escenarios1a4RegionRegistradaSedeYRegionDeLaSede(){
        var d=data();
        origin(d.atacama(),"03","03",false);origin(d.maule(),"07","07",false);origin(d.nuble(),"16","16",false);origin(d.losRios(),"14","14",false);
        origin(d.manual(),null,null,false);origin(d.nnaText(),null,null,false); // NNA: texto libre, sin region declarada
        // Sede responsable y region de la sede: la del ambito principal, no la de la persona.
        for(var x:List.of(List.of(d.atacama(),"REG-04"),List.of(d.maule(),"REG-06"),List.of(d.nuble(),"REG-08"),List.of(d.losRios(),"REG-09")))
            assertEquals(scope((String)x.get(1)),n(db.one("SELECT principal_scope FROM requirement WHERE id=?",x.get(0)),"principal_scope"));
        // Antiguedad: por region de origen (registrada) y por region de la sede.
        assertEquals(Map.of("07",1L),ageBy("REG-06","REGION_ORIGEN","origin_region"));
        assertEquals(Map.of("06",1L),ageBy("REG-06","REGION","region"));
        assertEquals(Map.of("16",1L),ageBy("REG-08","REGION_ORIGEN","origin_region"));
        assertEquals(Map.of("08",1L),ageBy("REG-08","REGION","region"));
        assertEquals(Map.of("14",1L),ageBy("REG-09","REGION_ORIGEN","origin_region"));
        assertEquals(Map.of("09",1L),ageBy("REG-09","REGION","region"));
        var coquimbo=ageBy("REG-04","REGION_ORIGEN","origin_region");
        assertEquals(2,coquimbo.size());assertEquals(4L,coquimbo.get("03"));assertEquals(2L,coquimbo.get(null)); // la corregida sigue en 03
        assertEquals(Map.of("04",6L),ageBy("REG-04","REGION","region"));
        // Filtro por region de origen: estable ante la correccion (la corregida sigue en 03, nada en 04) y SIN.
        var vol=m(report(q("VOLUMEN",scope("REG-04"),"03",null)).get("req"));
        assertEquals(4L,vol.get("total"));assertEquals(2L,vol.get("corrected"));
        assertEquals(0L,m(report(q("VOLUMEN",scope("REG-04"),"04",null)).get("req")).get("total"));
        assertEquals(2L,m(report(q("VOLUMEN",scope("REG-04"),"SIN",null)).get("req")).get("total"));
        assertEquals(1L,m(report(q("VOLUMEN",null,"07",null)).get("com")).get("total"));
        var nuble=report(q("VOLUMEN",null,"16",null));
        assertEquals(1L,m(nuble.get("com")).get("total"));assertEquals(1L,m(nuble.get("req")).get("total"));assertEquals(0L,m(nuble.get("req")).get("corrected"));
        assertEquals(2L,m(report(q("VOLUMEN",scope("REG-04"),null,null)).get("cases")).get("total"));
        assertEquals(1L,m(report(q("VOLUMEN",scope("REG-04"),null,null)).get("cases")).get("corrected"));
        // Antiguedad: total de corregidas en Coquimbo (la corregida y la que quito la region).
        assertEquals(2L,m(report(q("ANTIGUEDAD",scope("REG-04"),null,null)).get("req")).get("corrected"));
        // Tiempo (Apertura de Caso): agrupado por region de origen y por region de la sede, con la corregida contada.
        var opening=report(q("APERTURA_CASO",scope("REG-04"),null,"REGION_ORIGEN"));
        assertEquals(1,rows(opening).size());var g=rows(opening).getFirst();
        assertEquals("03",m(g.get("group")).get("origin_region"));assertEquals(2,((Number)g.get("n")).intValue());assertEquals(1L,g.get("corrected"));
        assertEquals(1L,m(opening.get("total")).get("corrected"));
        var bySite=rows(report(q("APERTURA_CASO",scope("REG-04"),null,"REGION")));
        assertEquals(1,bySite.size());assertEquals("04",m(bySite.getFirst().get("group")).get("region"));
        // Validacion del filtro y auditoria solo con el nombre del filtro.
        assertEquals(400,assertThrows(Problem.class,()->report(q("VOLUMEN",null,"99",null))).status);
        assertEquals(400,assertThrows(Problem.class,()->report(q("VOLUMEN",null,"Atacama",null))).status);
        report(q("VOLUMEN",null,"14",null));
        String note=s(db.one("SELECT note FROM audit_event WHERE action='REPORTE' ORDER BY id DESC LIMIT 1"),"note");
        assertTrue(note.endsWith("filtros: region de origen"),note);assertFalse(note.contains("14"),note);
    }

    @Test @Order(2) void exportacionConLasTresRegionesSoloCodigos(){
        var d=data();
        var req=parse(reports.export(access.actor("rep.region"),"REQ",q(null,null,null,null)).bytes());
        int at=req.getFirst().indexOf("region_sede");
        assertEquals(List.of("region_sede","region_registrada","region_vigente"),req.getFirst().subList(at,at+3));
        Object[][] expected={{d.atacama(),"04","03","03"},{d.maule(),"06","07","07"},{d.nuble(),"08","16","16"},{d.losRios(),"09","14","14"},
            {d.fixed(),"04","03","04"},{d.completed(),"04","03","03"},{d.removed(),"04","03",""},{d.manual(),"04","",""},{d.nnaText(),"04","",""}};
        for(var e:expected){
            var row=line(req,folio("requirement",(Long)e[0]));
            assertEquals(List.of(e[1],e[2],e[3]),List.of(row.get("region_sede"),row.get("region_registrada"),row.get("region_vigente")),row.toString());
        }
        // Solo codigos: ninguna celda trae comunas ni nombres de region (el Formulario adulto declara Chillan).
        var text=new String(reports.export(access.actor("rep.region"),"REQ",q(null,null,null,null)).bytes(),java.nio.charset.StandardCharsets.UTF_8);
        for(String forbidden:List.of("Chill","Talca","Atacama","Ñuble","Maule"))assertFalse(text.contains(forbidden),forbidden);
        var cases=parse(reports.export(access.actor("rep.region"),"CAS",q(null,null,null,null)).bytes());
        var c1=line(cases,folio("case_record",d.caseAtacama()));var c2=line(cases,folio("case_record",d.caseFixed()));
        assertEquals(List.of("04","03","03"),List.of(c1.get("region_sede"),c1.get("region_registrada"),c1.get("region_vigente")));
        assertEquals(List.of("04","03","04"),List.of(c2.get("region_sede"),c2.get("region_registrada"),c2.get("region_vigente")));
    }

    @Test @Order(3) void correccionCompletarQuitarAuditoriaYFicha(){
        var d=data();
        origin(d.fixed(),"03","04",true);origin(d.completed(),"03","03",false);origin(d.removed(),"03",null,true);
        // Region de cada version, escrita al crearla.
        assertEquals(Arrays.asList("03","04"),versions(d.fixed()));
        assertEquals(Arrays.asList(null,"03"),versions(d.completed()));
        assertEquals(Arrays.asList("03",null),versions(d.removed()));
        // FICHA_COMPLEMENTADA: frase unica en todo cambio de la region derivada (corregir, completar, quitar), sin codigos.
        for(long req:List.of(d.fixed(),d.completed(),d.removed())){
            String note=complemented(req);
            assertTrue(note.startsWith("Version 1. Region de origen modificada. Complementacion"),note);
            assertFalse(note.matches(".*\\b(0[0-9]|1[0-6])\\b.*"),note);
        }
        // Guardar sin cambiar la region (ni un texto libre que no es region) no la marca.
        long same=attend("REG-04","GENERAL","03");save("REG-04",same,"03");
        assertEquals("Version 1. Complementacion sintetica de la ficha",complemented(same));
        // La ficha entrega la region registrada y la vigente solo a quien la lee.
        var view=attention.view(access.actor("coord.04"),db.one("SELECT * FROM requirement WHERE id=?",d.fixed()));
        assertEquals(Map.of("registered","03","current","04","corrected",true),view.get("origin_region"));
        var foreign=attention.view(access.actor("coord.06"),db.one("SELECT * FROM requirement WHERE id=?",d.fixed()));
        assertEquals(false,foreign.get("can_read"));assertFalse(foreign.containsKey("origin_region"));
    }
    List<String> versions(long req){return db.rows("SELECT region_code FROM attention_revision WHERE requirement_id=? ORDER BY id",req).stream().map(r->r.get("region_code")==null?null:s(r,"region_code")).toList();}
    String complemented(long req){return s(db.one("SELECT note FROM audit_event WHERE action='FICHA_COMPLEMENTADA' AND resource_id=? ORDER BY id DESC LIMIT 1",req),"note");}

    @Test @Order(4) void regionVigenteEsLaPropuestaDeDestinoEnTodosLosCasos(){
        data();
        // Casos sinteticos de la proyeccion (COM manuales con filas CAMPO): validez y orden nna-region -> adult-region.
        long invalidNna=projected("nna-region","XX","adult-region","05");
        long both=projected("adult-region","05","nna-region","02");
        long onlyText=projected("adult-region","Valparaiso");
        long twice=projected("nna-region","","nna-region","11","nna-region","12");
        long none=projected();
        var expected=new LinkedHashMap<Long,String>();expected.put(invalidNna,"05");expected.put(both,"02");expected.put(onlyText,null);expected.put(twice,"11");expected.put(none,null);
        // Equivalencia en todas las COM de la prueba: SQL de los reportes (registrada y vigente) = propuesta de destino.
        int checked=0;
        for(var c:db.rows("SELECT c.id,r.id AS req,"+Reports.ORIGIN_COLS+" FROM communication c LEFT JOIN requirement r ON r.communication_id=c.id ORDER BY c.id")){
            long com=n(c,"id");Long req=nullableId(c,"req");
            String form=null; // como Attention.view: la region de la ficha vigente, tal como esta
            if(req!=null&&attention.exists(req))form=FormularioDocs.JSON.readValue(s(db.one("SELECT data_json FROM attention_record WHERE requirement_id=?",req),"data_json"),Map.class).get("region") instanceof String r?r:null;
            String proposal=access.suggestedRegion(com,form);
            assertEquals(proposal,c.get("origin_current")==null?null:s(c,"origin_current"),"COM "+com);
            if(req==null||!attention.exists(req))assertEquals(proposal,c.get("origin_region")==null?null:s(c,"origin_region"),"COM "+com+" sin ficha: registrada = vigente");
            if(expected.containsKey(com))assertEquals(expected.get(com),proposal,"COM "+com);
            checked++;
        }
        assertEquals(db.count("SELECT COUNT(*) FROM communication"),checked,"todas las COM, una vez");assertTrue(checked>=14,"COM revisadas: "+checked);
        // La ficha vigente manda sobre el Formulario en la regla Java (misma regla de currentSql).
        assertEquals("09",OriginRegion.current("09",List.of(Map.of("field_key","nna-region","value_text","02"))));
        assertEquals("02",OriginRegion.current("Atacama",List.of(Map.of("field_key","adult-region","value_text","05"),Map.of("field_key","nna-region","value_text","02"))));
    }
    long projected(String... kv){
        long com=run("administrativo","COM_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",Instant.now().minusSeconds(120).toString(),"subject","COM proyectada","body","Relato sintetico")).id();
        for(int i=0;i<kv.length;i+=2)db.update("INSERT INTO external_field(communication_id,field_group,item,field_key,value_text) VALUES(?,'CAMPO',?,?,?)",com,i,kv[i],kv[i+1]);
        return com;
    }

    static long at(int month,int day,int hour){return LocalDateTime.of(2026,month,day,hour,0).atZone(BusinessCalendar.ZONE).toInstant().toEpochMilli();}
    void holiday(String day,String region){db.update("INSERT INTO business_holiday(holiday_day,region_code,region_key,name,created_by,created_ms) VALUES(?,?,?,'Feriado sintetico',?,0)",day,region,region==null?"NAC":region,user("ti"));}

    @Test @Order(5) void feriadosDeLaRegionDeLaSedeNoDeLaRegionDeOrigen(){
        data();
        // Atencion de Atacama en Coquimbo, resuelta: lunes 2-3-2026 09:00 a viernes 6-3-2026 13:00 (4 dias habiles sin feriados).
        long req=run("coord.04","ATTENTION_CREATE",null,null,new LinkedHashMap<>(Map.of("scope_id",scope("REG-04"),"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(900).toString(),
            "form",form("GENERAL","03"),"resolve_now",true,"result","ORIENTACION","reason","Orientacion entregada","effective_at",Instant.now().minusSeconds(60).toString()))).id();
        assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"));
        assertEquals(1,db.update("UPDATE milestone SET at_ms=? WHERE requirement_id=? AND event='REQ_CREADO'",at(3,2,9),req));
        assertEquals(1,db.update("UPDATE milestone SET at_ms=? WHERE requirement_id=? AND event='REQ_CERRADO'",at(3,6,13),req));
        db.update("UPDATE requirement SET created_ms=? WHERE id=?",at(3,2,9),req);
        // Feriados de Atacama (region de origen) el miercoles 4 y el viernes 6; de Coquimbo (region de la sede) el jueves 5.
        // Sede: 4-1 = 3. Origen: 4-2 = 2. Ambos: 1. Solo nacionales: 4.
        holiday("2026-03-04","03");holiday("2026-03-06","03");holiday("2026-03-05","04");
        var r=reports.report(access.actor("rep.region"),new Reports.Request("REQ_TOTAL","2026-03-01","2026-03-31",null,scope("REG-04"),null,null,null,null,null,null,null,"REGION_ORIGEN",false,null,null));
        assertEquals(1,rows(r).size());var g=rows(r).getFirst();
        assertEquals("03",m(g.get("group")).get("origin_region"));assertEquals(1,((Number)g.get("n")).intValue());
        assertEquals(100.0,((Number)m(g.get("hours")).get("median")).doubleValue(),1e-9);
        assertEquals(3.0,((Number)m(g.get("business_days")).get("median")).doubleValue(),1e-9);
        var csv=parse(reports.export(access.actor("rep.region"),"REQ",new Reports.Request(null,"2026-03-01","2026-03-31",null,scope("REG-04"),null,null,null,null,null,null,null,null,null,null,null)).bytes());
        var row=line(csv,folio("requirement",req));
        assertEquals("100",row.get("h_req_total"));assertEquals("3",row.get("dh_req_total"));
        assertEquals(List.of("04","03","03"),List.of(row.get("region_sede"),row.get("region_registrada"),row.get("region_vigente")));
    }

    @Test @Order(6) void reconstruccionDeLaRegionPorVersionConMarcador(){
        var d=data();
        var before=db.rows("SELECT id,region_code FROM attention_revision ORDER BY id");
        assertTrue(before.size()>=10,"versiones: "+before.size());
        // Con el marcador, la carga no vuelve a correr.
        db.update("UPDATE attention_revision SET region_code=NULL");
        regions.rebuild();
        assertEquals(0,db.count("SELECT COUNT(*) FROM attention_revision WHERE region_code IS NOT NULL"));
        // Sin el marcador (instancia que llega desde 0.6.0), calcula cada version desde sus propios datos y lo deja.
        db.update("DELETE FROM application_marker WHERE code=?",OriginRegion.MARKER);
        regions.rebuild();
        assertEquals(before,db.rows("SELECT id,region_code FROM attention_revision ORDER BY id"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",OriginRegion.MARKER));
        assertEquals(Arrays.asList("03","04"),versions(d.fixed()));assertEquals(Arrays.asList((String)null),versions(d.nnaText()));
        origin(d.fixed(),"03","04",true);
    }
}
