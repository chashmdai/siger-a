package cl.nexbyte.sigera;

import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Derechos, contexto, caracterizacion, rol del Estado y grupos prioritarios de REQ y Caso (0.8.0, D34-D35, gate E06;
 * DISENO 3.2, 4.3 y 4.4): escenario 6 (un derecho compartido por el REQ y sus Casos es una sola fila del consolidado; una
 * cuenta sin lectura del segundo Caso no ve su clasificacion; un registro cerrado rechaza la clasificacion hasta su
 * reapertura, y lo registrado despues es RETROSPECTIVA con la reapertura vigente, tambien en un Caso reabierto), reglas
 * del conjunto (item no seleccionable, listado retirado, motivo al quitar o reemplazar un estado, estado solo sin valores,
 * concurrencia por revision) y facultades (nunca lectura institucional, LECTURA, REPORTES, AUDITORIA ni TI), con la
 * auditoria solo con nombres de variables.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_derechos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-derechos-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class RightsTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String HEAD="coordinacion.requerimientos",PROF="profesional",ADMIN="administrativo";
    static final Map<String,String> FILES=Map.of("DERECHO","derechos-2022-v2.json","CONTEXTO","contexto-v1.json","CARACTERIZACION","caracterizacion-v1.json",
        "ROL_ESTADO","rol-estado-v1.json","GRUPO_PRIORITARIO","grupos-prioritarios-v1.json");
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired Rights rights;@Autowired StatValues stats;

    @BeforeEach void ready()throws Exception{db.update("UPDATE staff SET must_change=FALSE");catalogs();}
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    Workflow.Result on(String who,String type,String table,long id,Map<String,Object> data){return run(who,type,id,rev(table,id),data);}
    Problem refused(Runnable r){return assertThrows(Problem.class,r::run);}
    static String ago(long s){return java.time.Instant.now().minusSeconds(s).toString();}
    static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    static Map<String,Object> add(String... codes){return Map.of("add",List.of(codes));}

    String reference(String name)throws Exception{return Files.readString(ReferenceCatalogsTest.DIR.resolve(name)).replace("\r\n","\n");}
    /** Catalogos de referencia de las variables multiples, vigentes (si una prueba retiro alguno, se vuelve a importar). */
    void catalogs()throws Exception{
        for(var e:FILES.entrySet()){
            String sha=StatCatalogs.sha(reference(e.getValue()));
            if(db.count("SELECT COUNT(*) FROM stat_catalog_version WHERE `current_catalog`=? AND file_sha256=?",e.getKey(),sha)>0)continue;
            long v=run("ti","CATALOG_IMPORT",null,null,Map.of("content",reference(e.getValue()),"reason","Fundamento sintetico")).id();
            run("ti","CATALOG_ACTIVATE",v,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",v),"revision"),Map.of("reason","Activacion sintetica"));
        }
    }
    /** REQ gestionado por la jefatura de UPR-REQ con el profesional como encargado. */
    long req(){
        long com=run(HEAD,"COM_CREATE",null,null,map("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",ago(600),"subject","Ingreso sintetico","body","Relato sintetico","intake",Map.of())).id();
        return on(HEAD,"COM_ASSUME","communication",com,Map.of("responsible_id",user(PROF),"reason","Gestion propia")).id();
    }
    long newCase(long req,String responsible){
        return on(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",scope("UPR-REQ"),"responsible_id",user(responsible),"objective","Gestion sintetica","matter","UPR")).id();
    }
    Workflow.Result classify(String who,String type,long id,Map<String,Object> variables,String reason){
        var data=map("record",type,"variables",variables);if(reason!=null)data.put("reason",reason);
        return run(who,"RIGHTS_CLASSIFY",id,rights.revision(type,id),data);
    }
    Set<String> set(String type,long id,String variable){return stats.set(type,id,variable,null);}
    @SuppressWarnings("unchecked") Map<String,Object> rightsOf(String who,String kind,long id){return (Map<String,Object>)queries.detail(access.actor(who),kind,id).get("rights");}
    @SuppressWarnings("unchecked") List<Map<String,Object>> consolidated(Map<String,Object> view,String variable){return (List<Map<String,Object>>)((Map<String,Object>)view.get("consolidated")).get(variable);}
    String account(String username,String role,Long scope){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",username)==0){
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'test-non-login',?,FALSE)",username,"Cuenta sintetica "+username,scope("UGI"));
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,0,'PRUEBA AUTOMATICA')",id,role,scope);
        }
        return username;
    }
    List<Long> counts(){
        var out=new ArrayList<Long>();
        for(String t:List.of("stat_event","audit_event","command_log"))out.add(db.count("SELECT COUNT(*) FROM "+t));
        return out;
    }

    // --- Escenario 6 ------------------------------------------------------------------------------------------------
    @Test void escenario6ConsolidadoSinDuplicarYSoloLoLegible(){
        long req=req();
        long a=newCase(req,PROF),b=newCase(req,HEAD);
        classify(HEAD,"REQ",req,Map.of("DERECHO",add("5.2"),"CONTEXTO",add("FAMILIAR")),null);
        classify(PROF,"CAS",a,Map.of("DERECHO",add("5.2","5.2.03"),"GRUPO_PRIORITARIO",add("DISCAPACIDAD")),null);
        classify(HEAD,"CAS",b,Map.of("DERECHO",add("5.2","3.1"),"ROL_ESTADO",add("7.02")),null);
        assertEquals(Set.of("5.2"),set("REQ",req,"DERECHO"));assertEquals(Set.of("5.2","5.2.03"),set("CAS",a,"DERECHO"));
        // Jefatura (lee ambos Casos): 5.2 es una sola fila, en el REQ y en los dos Casos.
        var head=rightsOf(HEAD,"REQ",req);
        var rows=consolidated(head,"DERECHO");
        assertEquals(List.of("3.1","5.2","5.2.03"),rows.stream().map(r->s(r,"code")).toList());
        var shared=rows.stream().filter(r->s(r,"code").equals("5.2")).findFirst().orElseThrow();
        assertEquals(true,shared.get("in_req"));assertEquals(2,((List<?>)shared.get("cases")).size());
        assertEquals("Derecho a estar protegido contra toda forma de violencia",shared.get("label"),"rotulo de la version con que se registro");
        // Profesional (lee el REQ como encargado y solo el Caso A): no ve lo que solo registra el Caso B.
        assertFalse(access.caseRead(access.actor(PROF),db.one("SELECT * FROM case_record WHERE id=?",b)));
        var prof=rightsOf(PROF,"REQ",req);
        assertEquals(List.of("5.2","5.2.03"),consolidated(prof,"DERECHO").stream().map(r->s(r,"code")).toList());
        assertEquals(1,((List<?>)consolidated(prof,"DERECHO").stream().filter(r->s(r,"code").equals("5.2")).findFirst().orElseThrow().get("cases")).size());
        assertTrue(consolidated(prof,"ROL_ESTADO").isEmpty(),"el rol del Estado del Caso B no se ve");
        assertEquals(403,refused(()->queries.detail(access.actor(PROF),"CAS",b)).status);
        // Grupos prioritarios: solo con la lectura del registro; en la ficha del Caso A, el propio.
        var caseA=rightsOf(PROF,"CAS",a);
        @SuppressWarnings("unchecked") var vars=(List<Map<String,Object>>)caseA.get("variables");
        var gp=vars.stream().filter(v->"GRUPO_PRIORITARIO".equals(v.get("variable"))).findFirst().orElseThrow();
        assertEquals("INFORMADO",gp.get("state"));assertEquals(1,((List<?>)gp.get("values")).size());
        // La auditoria solo nombra variables, nunca codigos.
        for(var e:db.rows("SELECT note FROM audit_event WHERE action IN (?,?) AND resource_type IN ('REQ','CAS')",StatValues.REGISTERED,StatValues.CORRECTED)){
            String note=s(e,"note");
            assertTrue(note.startsWith("Variables: "),note);
            for(String code:List.of("5.2","3.1","FAMILIAR","DISCAPACIDAD","7.02"))assertFalse(note.contains(code),note);
        }
    }

    // --- Inmutabilidad (D35) y RETROSPECTIVA (DI-07) ----------------------------------------------------------------
    @Test void registroCerradoRechazaYTrasLaReaperturaEsRetrospectiva(){
        long req=req();
        classify(HEAD,"REQ",req,Map.of("DERECHO",add("4.3")),null);
        assertEquals(StatValues.OFFICIAL,s(db.one("SELECT origin FROM stat_event WHERE record_type='REQ' AND record_id=? ORDER BY id DESC LIMIT 1",req),"origin"));
        on(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Cierre sintetico"));
        var before=counts();
        var p=refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",add("4.3.01")),null));
        assertEquals(409,p.status,"sin excepcion de funcion");assertTrue(p.getMessage().contains("reapertura formal"),p.getMessage());
        assertEquals(before,counts());
        assertEquals("El REQ está cerrado: la clasificación requiere reapertura formal.",rightsOf(HEAD,"REQ",req).get("blocked"));
        assertEquals(false,rightsOf(HEAD,"REQ",req).get("can_classify"));
        on(HEAD,"REOPEN_REQUEST","requirement",req,Map.of("reason","Clasificacion retrospectiva"));
        long reopening=n(db.one("SELECT id FROM reopening WHERE requirement_id=?",req),"id");
        on(HEAD,"REOPEN_DECIDE","reopening",reopening,Map.of("decision","ACEPTADA","reason","Aceptada"));
        classify(HEAD,"REQ",req,Map.of("DERECHO",add("4.3.01")),null);
        var e=db.one("SELECT origin,reopening_id FROM stat_event WHERE record_type='REQ' AND record_id=? ORDER BY id DESC LIMIT 1",req);
        assertEquals(StatValues.RETROSPECTIVE,s(e,"origin"));assertEquals(reopening,n(e,"reopening_id"));
        assertTrue(s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_type='REQ' AND resource_id=? ORDER BY id DESC LIMIT 1",StatValues.REGISTERED,req),"note").endsWith("Retrospectiva."));
    }
    @Test void casoCerradoYReabiertoEsRetrospectiva(){
        long req=req();
        on(HEAD,"REQ_CLASSIFY","requirement",req,Map.of("complexity","BAJA","reason","Clasificacion sintetica"));
        long cas=newCase(req,PROF);
        classify(PROF,"CAS",cas,Map.of("CARACTERIZACION",add("VIOLENCIA")),null);
        on(PROF,"CASE_CLOSE","case_record",cas,Map.of("reason","Cierre del Caso"));
        assertEquals(409,refused(()->classify(PROF,"CAS",cas,Map.of("CARACTERIZACION",add("AMENAZAS")),null)).status);
        assertEquals("El Caso está cerrado: la clasificación requiere reapertura formal.",rightsOf(HEAD,"CAS",cas).get("blocked"));
        run(PROF,"REOPEN_REQUEST",req,rev("requirement",req),Map.of("case_id",cas,"reason","Clasificacion retrospectiva del Caso"));
        long reopening=n(db.one("SELECT id FROM reopening WHERE case_id=?",cas),"id");
        on(HEAD,"REOPEN_DECIDE","reopening",reopening,Map.of("decision","ACEPTADA","reason","Aceptada"));
        classify(PROF,"CAS",cas,Map.of("CARACTERIZACION",add("AMENAZAS")),null);
        var e=db.one("SELECT origin,reopening_id FROM stat_event WHERE record_type='CAS' AND record_id=? ORDER BY id DESC LIMIT 1",cas);
        assertEquals(StatValues.RETROSPECTIVE,s(e,"origin"),"Caso reabierto");assertEquals(reopening,n(e,"reopening_id"));
        // El REQ nunca se cerro: su clasificacion sigue siendo de FUNCIONARIO.
        classify(HEAD,"REQ",req,Map.of("CARACTERIZACION",add("AMENAZAS")),null);
        assertEquals(StatValues.OFFICIAL,s(db.one("SELECT origin FROM stat_event WHERE record_type='REQ' AND record_id=? ORDER BY id DESC LIMIT 1",req),"origin"));
    }

    // --- Reglas del conjunto ----------------------------------------------------------------------------------------
    @Test void reglasDelConjuntoMotivoYEstados(){
        long req=req();
        var before=counts();
        // Item no seleccionable (eje) y codigo inexistente: 409 sin escrituras.
        assertEquals(409,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",add("5")),null)).status);
        assertEquals(409,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",add("9.9")),null)).status);
        // Formato: variable desconocida, campo extra, origen enviado por el cliente.
        assertEquals(400,refused(()->classify(HEAD,"REQ",req,Map.of("TIPO_SOLICITUD",add("OTRO")),null)).status);
        assertEquals(400,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",Map.of("add",List.of("5.2"),"origin","SISTEMA")),null)).status);
        assertEquals(400,refused(()->run(HEAD,"RIGHTS_CLASSIFY",req,0L,Map.of("record","REQ","variables",Map.of("DERECHO",add("5.2")),"origin","RETROSPECTIVA"))).status);
        assertEquals(before,counts());
        classify(HEAD,"REQ",req,Map.of("DERECHO",add("5.2","5.2.03")),null);
        // Estado especial solo sin valores; quitar exige motivo; un valor igual no escribe.
        assertEquals(409,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",Map.of("status","SIN_INFORMACION")),"Motivo")).status);
        assertEquals(400,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",Map.of("remove",List.of("5.2.03"))),null)).status);
        assertEquals(409,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",add("5.2")),null)).status,"sin cambios");
        assertEquals(409,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",Map.of("remove",List.of("3.1"))),"Motivo")).status,"no registrado");
        classify(HEAD,"REQ",req,Map.of("DERECHO",Map.of("remove",List.of("5.2","5.2.03"),"status","SIN_INFORMACION")),"Antecedentes insuficientes");
        assertEquals(Set.of(),set("REQ",req,"DERECHO"));assertEquals("SIN_INFORMACION",stats.setState("REQ",req,"DERECHO",null));
        // Reemplazar un estado especial es una correccion (motivo); agregar valores sobre un estado no lo es.
        assertEquals(400,refused(()->classify(HEAD,"REQ",req,Map.of("DERECHO",Map.of("status","NO_APLICA")),null)).status);
        classify(HEAD,"REQ",req,Map.of("CONTEXTO",Map.of("status","NO_DECLARADO")),null);
        classify(HEAD,"REQ",req,Map.of("CONTEXTO",add("ESCOLAR")),null);
        assertEquals("INFORMADO",stats.setState("REQ",req,"CONTEXTO",null));
        // Historia sin borrado: cada acto queda como evento; el motivo solo en stat_event.
        assertEquals(List.of("ADD","ADD","REMOVE","REMOVE","STATUS"),db.rows("SELECT event FROM stat_event WHERE record_type='REQ' AND record_id=? AND variable='DERECHO' ORDER BY id",req).stream().map(r->s(r,"event")).toList());
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action=? AND resource_type='REQ' AND resource_id=?",StatValues.CORRECTED,req));
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE note LIKE '%Antecedentes insuficientes%'"));
        // Concurrencia: una revision vieja no escribe.
        long stale=rights.revision("REQ",req)-1;
        assertEquals(409,refused(()->run(HEAD,"RIGHTS_CLASSIFY",req,stale,Map.of("record","REQ","variables",Map.of("CONTEXTO",add("DIGITAL"))))).status);
    }
    @Test void listadoRetiradoSeConservaYNoAdmiteNuevos()throws Exception{
        long req=req();
        classify(HEAD,"REQ",req,Map.of("GRUPO_PRIORITARIO",add("MIGRANTES")),null);
        long old=n(db.one("SELECT id FROM stat_catalog_version WHERE `current_catalog`='GRUPO_PRIORITARIO'"),"id");
        // Nueva version vigente sin MIGRANTES: la anterior queda retirada.
        long v=run("ti","CATALOG_IMPORT",null,null,Map.of("content",StatCatalogsTest.file("GRUPO_PRIORITARIO","GRUPO_NUEVO"),"reason","Version sintetica")).id();
        run("ti","CATALOG_ACTIVATE",v,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",v),"revision"),Map.of("reason","Activacion sintetica"));
        try{
            @SuppressWarnings("unchecked") var vars=(List<Map<String,Object>>)rightsOf(HEAD,"REQ",req).get("variables");
            @SuppressWarnings("unchecked") var values=(List<Map<String,Object>>)vars.stream().filter(x->"GRUPO_PRIORITARIO".equals(x.get("variable"))).findFirst().orElseThrow().get("values");
            assertEquals("Niños, niñas y adolescentes migrantes",values.getFirst().get("label"),"rotulo de la version retirada");
            assertEquals(old,n(values.getFirst(),"catalog_version_id"));
            // Agregarlo en otro registro no se admite (409); quitarlo si, con motivo; y agregar el valor vigente si.
            long other=req();
            assertEquals(409,refused(()->classify(HEAD,"REQ",other,Map.of("GRUPO_PRIORITARIO",add("MIGRANTES")),null)).status);
            classify(HEAD,"REQ",req,Map.of("GRUPO_PRIORITARIO",Map.of("remove",List.of("MIGRANTES"),"add",List.of("GRUPO_NUEVO"))),"Cambio de listado");
            assertEquals(Set.of("GRUPO_NUEVO"),set("REQ",req,"GRUPO_PRIORITARIO"));
            assertEquals(old,n(db.one("SELECT catalog_version_id FROM stat_event WHERE record_type='REQ' AND record_id=? AND event='REMOVE'",req),"catalog_version_id"),"el retiro guarda la version con que se agrego");
        }finally{catalogs();}
    }

    // --- Facultades -------------------------------------------------------------------------------------------------
    @Test void facultadesYCuentasSinEscritura(){
        long upr=scope("UPR-REQ");
        String reader=account("lector.institucional.e06",Roles.CONSULTA_INSTITUCIONAL,null),reports=account("reportes.e06","REPORTES",null),audit=account("auditoria.e06","AUDITORIA",null);
        long req=req(),cas=newCase(req,PROF);
        // Encargado del REQ (incluye a quien edita la ficha) y responsable del Caso si.
        classify(PROF,"REQ",req,Map.of("CONTEXTO",add("FAMILIAR")),null);
        classify(PROF,"CAS",cas,Map.of("CONTEXTO",add("FAMILIAR")),null);
        var before=counts();
        for(String who:List.of(reader,"gabinete",reports,audit,"ti",ADMIN,"distribucion")){
            for(var t:List.of(Map.entry("REQ",req),Map.entry("CAS",cas)))
                assertEquals(403,refused(()->classify(who,t.getKey(),t.getValue(),Map.of("CONTEXTO",add("ESCOLAR")),null)).status,who+" "+t.getKey());
        }
        assertEquals(before,counts());
        // La lectura institucional ve la clasificacion sin facultad ni motivo; TI no lee.
        var view=rightsOf(reader,"REQ",req);
        assertEquals(false,view.get("can_classify"));
        assertEquals(403,refused(()->queries.detail(access.actor("ti"),"REQ",req)).status);
        assertTrue(upr>0);
    }
}
