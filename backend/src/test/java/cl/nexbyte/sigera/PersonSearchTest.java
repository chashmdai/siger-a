package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
 * Buscador de personas y folios (0.5.0, D5, gate G08a): clasificacion del termino (folio, correlativo, RUN, documento,
 * nombre), coincidencia por palabra completa o inicio de palabra, relevancia, cadena legible por paso, filtros,
 * permisos (lo oculto no se muestra ni se cuenta; TI 403), limite de resultados y auditoria sin el termino en claro.
 * La equivalencia de los predicados SQL nuevos con Access/Attention/Queries esta en InstitutionalReaderTest.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_buscador;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-buscador-${random.uuid}","siger.audit-search-key="+PersonSearchTest.KEY})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersonSearchTest {
    static final String KEY="clave-sintetica-de-pruebas-hmac-0123456789";
    static final String READER="lector.buscador";
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired PersonSearch search;@Autowired BoardQueries boards;

    record Data(long ana,long mariana,long anabel,long external,long ficha,long fichaCom,long caseId,long regional){}
    static Data d;

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    long run(String who,String type,String table,Long id,Map<String,Object> data){
        Long rev=id==null?null:n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");
        return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data)).id();
    }
    long com(String who,String scope,String presenter,String identity){
        var data=new HashMap<String,Object>(Map.of("scope_id",scope(scope),"channel","TELEFONO","received_at",ago(120),"subject","Ingreso sintetico","body","Relato sintetico","presenter",presenter));
        if(identity!=null)data.put("identity_document",identity);
        return run(who,"COM_CREATE",null,null,data);
    }
    /**
     * Datos sinteticos: COM manuales en UPR-REQ (Ana Perez Soto con RUN, NNA y "otra persona"; Mariana con pasaporte;
     * Anabel con el RUN escrito sin guion), un original del Formulario (ClaveUnica 12.345.678-5), una primera atencion
     * con Caso del profesional y una COM de la sede REG-DEMO. Las COM manuales solo las leen su autora y Distribucion.
     */
    synchronized Data data(){
        if(d!=null)return d;
        db.update("UPDATE staff SET must_change=FALSE");
        long reader=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'test-non-login',?,FALSE)",READER,"Lector buscador sintetico",scope("UGI"));
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'PRUEBA AUTOMATICA')",reader,Roles.CONSULTA_INSTITUCIONAL);
        long upr=scope("UPR-REQ");
        long ana=run("distribucion","COM_CREATE",null,null,Map.of("scope_id",upr,"channel","TELEFONO","received_at",ago(120),"subject","Ingreso sintetico","body","Relato sintetico",
            "presenter","Ana Pérez Soto","identity_document","RUN 12.345.678-5","persons",List.of(Map.of("role","NNA","name","Benjamín Rojas"),Map.of("role","OTRA_PERSONA","name","Hugo Lagos"))));
        long mariana=com("distribucion","UPR-REQ","Mariana Ríos","Pasaporte AB-123.456");
        long anabel=com("distribucion","UPR-REQ","Anabel Fuentes","123456785");
        long external=flow.conserveExternal("administrativo",upr,FormularioDocs.sign(FormularioDocs.cuVulneration())).id();
        var form=new LinkedHashMap<String,Object>(Map.of("schema_version","1.1","modality","GENERAL","course","EN_ATENCION","motive","Consulta sintetica",
            "identity_type","RUN","presenter_first_name","Carla","presenter_last_name","Muñoz","presenter_run_number","11.111.111","presenter_run_dv","1"));
        long ficha=run("administrativo","ATTENTION_CREATE",null,null,Map.of("scope_id",upr,"channel","PRESENCIAL","received_at",ago(60),"form",form));
        long fichaCom=n(db.one("SELECT communication_id FROM requirement WHERE id=?",ficha),"communication_id");
        long caseId=run("coordinacion.requerimientos","REQ_CASE_CREATE","requirement",ficha,Map.of("scope_id",upr,"responsible_id",user("profesional"),"objective","Seguimiento sintetico","matter","UPR"));
        long regional=com("coordinacion.regional","REG-DEMO","Ana Lagos",null);
        d=new Data(ana,mariana,anabel,external,ficha,fichaCom,caseId,regional);
        return d;
    }
    static final PersonSearch.Filters NONE=PersonSearch.Filters.of(null,false,null,null,null,null);
    Map<String,Object> find(String who,String q){return find(who,q,null,NONE);}
    Map<String,Object> find(String who,String q,String as,PersonSearch.Filters f){return search.search(access.actor(who),q,as,f);}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> list(Map<String,Object> m,String k){return (List<Map<String,Object>>)m.get(k);}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Map<String,Object> m,String k){return (Map<String,Object>)m.get(k);}
    static Map<String,Object> reading(Map<String,Object> result,String type){return list(result,"readings").stream().filter(r->s(r,"type").equals(type)).findFirst().orElseThrow();}
    static Map<String,Object> only(Map<String,Object> result){var r=list(result,"readings");assertEquals(1,r.size());return r.getFirst();}
    /** COM de cada grupo en orden; un grupo sin COM legible aparece como 0. */
    static List<Long> coms(Map<String,Object> reading){return list(reading,"groups").stream().map(g->g.get("communication")==null?0L:n(map(g,"communication"),"id")).toList();}
    String lastNote(){return s(db.one("SELECT note FROM audit_event WHERE action='BUSQUEDA' ORDER BY id DESC LIMIT 1"),"note");}
    String hmac(String canonical)throws Exception{
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    @Test @Order(1) void clasificaElTermino(){
        var folio=search.term("  req-a-12 ");
        assertEquals(List.of(PersonSearch.Kind.FOLIO),folio.detected());
        assertEquals(List.of("REQ-A-000012"),folio.available().get(PersonSearch.Kind.FOLIO).folios());
        assertEquals(List.of("CAS-A-000012"),search.term("CAS-A-0000012").available().get(PersonSearch.Kind.FOLIO).folios());

        // Un numero solo: correlativo de COM, REQ y CAS y, desde 5 digitos, tambien RUN (con o sin DV al final).
        var number=search.term("12345678");
        assertEquals(List.of(PersonSearch.Kind.FOLIO,PersonSearch.Kind.RUN),number.detected());
        assertEquals(List.of("COM-A-12345678","REQ-A-12345678","CAS-A-12345678"),number.available().get(PersonSearch.Kind.FOLIO).folios());
        var run=number.available().get(PersonSearch.Kind.RUN);
        assertEquals("12345678",run.runNumber());assertEquals("1234567",run.altNumber());assertEquals("8",run.altDv());assertNull(run.warning());
        assertEquals(List.of("COM-A-000012","REQ-A-000012","CAS-A-000012"),search.term("012").available().get(PersonSearch.Kind.FOLIO).folios());
        assertEquals(List.of(PersonSearch.Kind.FOLIO),search.term("012").detected());

        var dotted=search.term("12.345.678-5").available().get(PersonSearch.Kind.RUN);
        assertEquals(List.of(PersonSearch.Kind.RUN),search.term("12.345.678-5").detected());
        assertEquals("5",dotted.runDv());assertNull(dotted.warning());
        assertNotNull(search.term("12.345.678-9").available().get(PersonSearch.Kind.RUN).warning(),"DV que no cumple modulo 11: aviso");
        assertNull(search.term("10.000.013-k").available().get(PersonSearch.Kind.RUN).warning(),"DV K");
        assertEquals("K",search.term("10000013K").available().get(PersonSearch.Kind.RUN).runDv());
        assertNull(search.term("12.345.678").available().get(PersonSearch.Kind.RUN).runDv());
        assertEquals("5",PersonSearch.dv("12345678"));assertEquals("K",PersonSearch.dv("10000013"));assertEquals("1",PersonSearch.dv("11111111"));

        assertEquals(List.of(PersonSearch.Kind.DOCUMENTO),search.term("Pasaporte AB-123.456").detected());
        assertEquals("AB123456",search.term("AB123456").available().get(PersonSearch.Kind.DOCUMENTO).canonical());
        var name=search.term(" Ana  PÉREZ ");
        assertEquals(List.of(PersonSearch.Kind.NOMBRE),name.detected());
        assertEquals(List.of("ana","perez"),name.available().get(PersonSearch.Kind.NOMBRE).words());
        // Elegir otra lectura: un folio tambien se puede buscar como documento.
        assertTrue(search.term("REQ-A-12").available().containsKey(PersonSearch.Kind.DOCUMENTO));

        for(String bad:List.of("ab","  a ","x".repeat(161),"!!!","uno dos tres cuatro cinco seis siete ocho nueve"))
            assertEquals(400,assertThrows(Problem.class,()->search.term(bad),bad).status,bad);
    }

    @Test @Order(2) void runDocumentoYRelevancia()throws Exception{
        var x=data();
        // RUN: autenticado (Formulario), declarado (COM) y escrito sin guion (documento), en ese orden.
        var run=only(find(READER,"12.345.678-5"));
        assertEquals(List.of(x.external(),x.ana(),x.anabel()),coms(run));
        assertEquals(List.of(1,2,3),list(run,"groups").stream().map(g->((Number)g.get("level")).intValue()).toList());
        assertEquals(List.of("RUN","RUN","DOCUMENTO"),list(run,"groups").stream().map(g->s(list(g,"matches").getFirst(),"reason")).toList());
        assertTrue(list(run,"groups").stream().flatMap(g->list(g,"matches").stream()).noneMatch(m->b(m,"uncertain")),"RUN y documento no llevan la marca de otra persona");
        var person=map(run,"person");
        assertTrue(b(person,"authenticated"));assertEquals("12345678",s(person,"run_number"));
        assertTrue(list(run,"groups").stream().flatMap(g->list(g,"matches").stream()).anyMatch(m->s(m,"display_name").equals("Ana Pérez Soto")));
        assertTrue(((List<?>)person.get("names")).contains("Ana Pérez Soto"));
        assertEquals(Map.of("com",3L,"com_open",3L,"req",0,"req_open",0L,"cas",0,"cas_open",0L),map(run,"summary"));
        assertNull(run.get("warning"));
        // Auditoria: tipo, cantidad y huella HMAC del termino canonico; nunca el termino en claro.
        String note=lastNote();
        assertEquals("Tipo RUN; resultados 3; huella "+hmac("RUN:12345678"),note);
        assertFalse(note.contains("12345678")&&!hmac("RUN:12345678").contains("12345678"));

        assertNotNull(only(find(READER,"12345678-9")).get("warning"),"DV invalido: aviso y busqueda por numero");
        assertEquals(List.of(x.external(),x.ana(),x.anabel()),coms(only(find(READER,"12345678-9"))));

        // Un numero solo: correlativos y RUN, en lecturas separadas.
        var number=find(READER,"123456785");
        assertEquals(List.of("FOLIO","RUN"),list(number,"readings").stream().map(r->s(r,"type")).toList());
        assertEquals(0,n(reading(number,"FOLIO"),"total"));
        assertEquals(List.of(x.external(),x.ana(),x.anabel()),coms(reading(number,"RUN")));
        assertTrue(lastNote().startsWith("Tipo FOLIO; resultados 0; huella "+hmac("FOLIO:123456785")+" | Tipo RUN; resultados 3; huella "+hmac("RUN:123456785")));

        var document=only(find(READER,"Pasaporte AB-123.456"));
        assertEquals("DOCUMENTO",s(document,"type"));assertEquals(List.of(x.mariana()),coms(document));assertEquals("AB123456",s(map(document,"person"),"document"));
        assertEquals(List.of(x.mariana()),coms(only(find(READER,"AB123456","DOCUMENTO",NONE))));
        assertEquals(400,assertThrows(Problem.class,()->find(READER,"Ana Pérez","RUN",NONE)).status,"lectura no admitida para el texto");
    }

    @Test @Order(3) void nombrePorPalabraCompletaOInicio(){
        var x=data();
        // "ana" encuentra Ana y Anabel (inicio de palabra), nunca Mariana (fragmento).
        var ana=only(find(READER,"ana"));
        assertEquals(Set.of(x.ana(),x.anabel(),x.regional()),Set.copyOf(coms(ana)));
        assertFalse(coms(ana).contains(x.mariana()));
        assertTrue(list(ana,"groups").stream().flatMap(g->list(g,"matches").stream()).allMatch(m->b(m,"uncertain")&&s(m,"reason").equals("NOMBRE")),"por nombre: puede ser otra persona");
        assertTrue(lastNote().matches("Tipo NOMBRE; resultados 3; huella [0-9a-f]{64}"));
        assertEquals(List.of(x.mariana()),coms(only(find(READER,"marian"))));
        // Nombre completo exacto antes que por palabras; tildes y mayusculas no importan.
        db.update("UPDATE staff SET must_change=FALSE");
        long similar=com("distribucion","UPR-REQ","Ana Pérez Soto Vega",null);
        var full=only(find(READER,"ANA PEREZ SOTO"));
        assertEquals(List.of(x.ana(),similar),coms(full));
        assertTrue(b(list(list(full,"groups").getFirst(),"matches").getFirst(),"exact"));
        assertEquals(List.of(4,5),list(full,"groups").stream().map(g->((Number)g.get("level")).intValue()).toList());
        // "Otra persona" no se indexa (D5); el NNA si, con su rol.
        assertEquals(0,n(only(find(READER,"Hugo Lagos")),"total"));
        var nna=only(find(READER,"benjamin"));
        assertEquals(List.of(x.ana()),coms(nna));assertEquals("NNA",s(list(list(nna,"groups").getFirst(),"matches").getFirst(),"role"));
        assertEquals(0,n(only(find(READER,"z".repeat(3))),"total"));
    }

    @Test @Order(4) void cadenaYPermisos(){
        var x=data();long upr=scope("UPR-REQ");
        // Folio exacto de la COM: una sola coincidencia, que la pantalla abre directamente.
        String comFolio=s(db.one("SELECT folio FROM communication WHERE id=?",x.ana()),"folio");
        var folio=only(find(READER,comFolio.toLowerCase(Locale.ROOT)));
        assertEquals(Map.of("kind","COM","id",x.ana(),"folio",comFolio),folio.get("exact"));
        // Folio del Caso: cadena completa para el lector, con sede, responsable y ultimo movimiento.
        String caseFolio=s(db.one("SELECT folio FROM case_record WHERE id=?",x.caseId()),"folio");
        var chain=list(only(find(READER,caseFolio)),"groups").getFirst();
        assertEquals(x.fichaCom(),n(map(chain,"communication"),"id"));
        var req=list(chain,"requirements").getFirst();
        assertEquals(x.ficha(),n(req,"id"));assertEquals("ATENCION_DIRECTA",s(req,"origin"));assertEquals("UPR-REQ",s(map(req,"principal"),"code"));assertEquals("CENTRAL",s(map(req,"principal"),"level"));
        assertEquals("Primera atencion sintetica",s(req,"attention_user"));assertTrue(n(req,"last_ms")>=n(req,"created_ms"));
        var cas=list(chain,"cases").getFirst();
        assertEquals("Profesional Requerimientos sintetico",s(cas,"responsible"));assertEquals("EN_GESTION",s(cas,"state"));assertEquals(upr,n(map(cas,"scope"),"id"));
        assertEquals(List.of(Map.of("kind","CAS","id",x.caseId(),"folio",caseFolio)),chain.get("folio_matches"));
        // Correlativo: el numero del REQ encuentra su folio.
        long reqNumber=x.ficha();
        assertTrue(list(reading(find(READER,String.valueOf(reqNumber).length()<3?String.format("%03d",reqNumber):String.valueOf(reqNumber)),"FOLIO"),"groups").stream()
            .flatMap(g->list(g,"folio_matches").stream()).anyMatch(m->s(m,"kind").equals("REQ")&&n(m,"id")==reqNumber));

        // LECTURA lee el Caso y el REQ, no la COM ni la ficha: lo encuentra por folio, nunca por la persona.
        var gabinete=list(only(find("gabinete",caseFolio)),"groups").getFirst();
        assertNull(gabinete.get("communication"));assertEquals(1,list(gabinete,"cases").size());assertEquals(List.of(),gabinete.get("matches"));
        for(String q:List.of("Carla Muñoz","11.111.111-1","ana","12.345.678-5")){
            var hidden=only(find("gabinete",q));
            assertEquals(0,n(hidden,"total"),q);assertEquals(0,n(map(hidden,"facets"),"open"),q);assertEquals(0,n(map(hidden,"facets"),"closed"),q);
        }
        // El profesional del Caso lee la ficha (no la COM): encuentra a la persona con la cadena legible.
        var prof=list(only(find("profesional","11111111-1")),"groups");
        assertEquals(1,prof.size());assertNull(prof.getFirst().get("communication"));
        assertEquals("FICHA",s(list(prof.getFirst(),"matches").getFirst(),"source"));assertEquals(1,list(prof.getFirst(),"cases").size());
        assertEquals(0,n(only(find("profesional","Ana Pérez Soto")),"total"),"COM manual ajena");
        // La autora de la COM y Distribucion la leen; el profesional no la encuentra por folio.
        assertEquals(List.of(x.ana()),coms(only(find("distribucion","Ana Perez Soto"))).subList(0,1));
        assertEquals(0,n(only(find("profesional",comFolio)),"total"));
        assertNull(only(find("profesional",comFolio)).get("exact"));
        // La Coordinacion regional solo ve su sede.
        assertEquals(List.of(x.regional()),coms(only(find("coordinacion.regional","ana"))));
        // TI no busca.
        assertEquals(403,assertThrows(Problem.class,()->find("ti","ana")).status);
    }

    @Test @Order(5) void filtrosYOrdenAbiertoPrimero(){
        var x=data();
        // Una COM cerrada (sintetico) queda despues de las abiertas del mismo nivel.
        db.update("UPDATE communication SET state='ATENDIDA' WHERE id=?",x.anabel());
        var all=only(find(READER,"ana"));
        assertEquals(x.anabel(),coms(all).getLast());
        var facets=map(all,"facets");
        assertEquals(1,n(facets,"closed"));assertEquals(Map.of("REQUIRENTE",4),facets.get("roles"));assertEquals(Map.of(Bootstrap.DEMO_REGION,1),facets.get("regions"));
        assertFalse(coms(only(find(READER,"ana",null,PersonSearch.Filters.of(null,true,null,null,null,null)))).contains(x.anabel()));
        assertEquals(List.of(x.regional()),coms(only(find(READER,"ana",null,PersonSearch.Filters.of(null,false,null,Bootstrap.DEMO_REGION,null,null)))));
        assertEquals(List.of(x.regional()),coms(only(find(READER,"ana",null,PersonSearch.Filters.of(null,false,scope("REG-DEMO"),null,null,null)))));
        assertEquals(List.of(x.ana()),coms(only(find(READER,"benjamin",null,PersonSearch.Filters.of("NNA",false,null,null,null,null)))));
        assertEquals(0,n(only(find(READER,"benjamin",null,PersonSearch.Filters.of("CURADOR",false,null,null,null,null))),"total"));
        String today=LocalDate.now(PersonSearch.ZONE).toString(),tomorrow=LocalDate.now(PersonSearch.ZONE).plusDays(1).toString();
        assertEquals(4,n(only(find(READER,"ana",null,PersonSearch.Filters.of(null,false,null,null,today,today))),"total"));
        assertEquals(0,n(only(find(READER,"ana",null,PersonSearch.Filters.of(null,false,null,null,tomorrow,null))),"total"));
        assertTrue(lastNote().endsWith("; filtros: periodo"));
        for(var bad:List.<Runnable>of(()->PersonSearch.Filters.of("PERSONA_SENALADA",false,null,null,null,null),()->PersonSearch.Filters.of(null,false,null,"99",null,null),
            ()->PersonSearch.Filters.of(null,false,null,null,"2026-13-01",null),()->PersonSearch.Filters.of(null,false,null,null,tomorrow,today)))
            assertEquals(400,assertThrows(Problem.class,bad::run).status);
        db.update("UPDATE communication SET state='NUEVA' WHERE id=?",x.anabel());
    }

    @Test @Order(6) void limiteDeResultados(){
        data();db.update("UPDATE staff SET must_change=FALSE");
        for(int i=0;i<PersonSearch.LIMIT+1;i++)com("distribucion","UPR-REQ","Zoe Sintética "+i,null);
        var zoe=only(find(READER,"zoe"));
        assertEquals(PersonSearch.LIMIT+1,n(zoe,"total"));assertTrue(b(zoe,"more"));assertEquals(PersonSearch.LIMIT,list(zoe,"groups").size());assertNotNull(zoe.get("message"));
    }

    @Test @Order(7) void claveDeAuditoria(){
        data();
        // Sin clave: tipo y cantidad, sin huella. Una clave corta detiene el arranque.
        var plain=new PersonSearch(db,boards,"COM-A","");
        plain.search(access.actor(READER),"Ana Pérez Soto",null,NONE);
        assertEquals("Tipo NOMBRE; resultados 2",lastNote());
        assertThrows(IllegalStateException.class,()->new PersonSearch(db,boards,"COM-A","corta"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE action='BUSQUEDA' AND (LOWER(note) LIKE '%ana%' OR LOWER(note) LIKE '%perez%' OR note LIKE '%12.345%' OR note LIKE '%AB123456%')"),"el termino nunca queda en claro");
    }
}
