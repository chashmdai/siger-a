package cl.nexbyte.sigera;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Barrido de inmutabilidad (0.8.0, D35, gate E06; DISENO 13.4): sobre un REQ cerrado (con su Caso, constancias, tarea y
 * flujo terminados) y una primera atencion resuelta, todo comando de Workflow.run que actua sobre el expediente se rechaza
 * sin escribir nada (409, o 403 en cuatro guardas vigentes que combinan estado y facultad), tambien para quien tiene la facultad (jefatura, responsable, encargado), y tambien toda
 * clasificacion (STAT_CLASSIFY y RIGHTS_CLASSIFY). Excepciones documentadas (DISENO 1.2, S2), que no modifican el
 * expediente cerrado: CASE_ACK (acusa una asignacion), COM_LINK, LINK_DECIDE y LINK_RETURN (deciden sobre una COM
 * posterior) y el mecanismo formal de reapertura REOPEN_* (DI-07). Los comandos que crean registros nuevos, NOTICE_READ y
 * los de administracion no actuan sobre un expediente. Un comando nuevo que no este en ninguna categoria hace fallar la
 * prueba.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_inmutabilidad;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-inmutabilidad-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class ImmutabilityTest {
    static final String HEAD="coordinacion.requerimientos",PROF="profesional",ADMIN="administrativo",DIST="distribucion",UPD="direccion.upd";
    static final Set<String> EXCEPTIONS=Set.of("CASE_ACK","COM_LINK","LINK_DECIDE","LINK_RETURN","REOPEN_REQUEST","REOPEN_AUTHORIZE","REOPEN_DECIDE");
    static final Set<String> CREATE=Set.of("COM_CREATE","COM_IMMEDIATE","ATTENTION_CREATE","NOTICE_READ");
    /** Cortes y vistas del motor estadistico (E10): no actuan sobre un expediente (un corte es inmutable por si mismo: StatCutsTest). */
    static final Set<String> STATISTICS=Set.of("STAT_CUT_CREATE","STAT_CUT_ANNUL","STAT_VIEW_SAVE","STAT_VIEW_DELETE","STAT_VIEW_SHARE","STAT_SERIES_IMPORT","STAT_SERIES_ANNUL");
    @Autowired Workflow flow;@Autowired Db db;

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    Long rev(String table,Long id){
        if(table==null||id==null)return null;
        if(table.equals("stat_event"))return db.count("SELECT COUNT(*) FROM stat_event WHERE communication_id=?",id);
        if(table.startsWith("stat_event_"))return db.count("SELECT COUNT(*) FROM stat_event WHERE record_type=? AND record_id=?",table.substring(11).toUpperCase(Locale.ROOT),id);
        return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");
    }
    long run(String who,String type,String table,Long id,Map<String,Object> data){
        db.update("UPDATE staff SET must_change=FALSE WHERE username=?",who);
        return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev(table,id),data)).id();
    }
    static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    Map<String,Long> snapshot(){
        var out=new TreeMap<String,Long>();
        for(var row:db.rows("SELECT LOWER(table_name) AS t FROM information_schema.tables WHERE LOWER(table_schema)=LOWER(SCHEMA()) AND table_type='BASE TABLE' ORDER BY 1")){
            String table=s(row,"t");out.put(table+".filas",db.count("SELECT COUNT(*) FROM "+table));
            if(db.count("SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_schema)=LOWER(SCHEMA()) AND LOWER(table_name)=? AND LOWER(column_name)='revision'",table)>0)
                out.put(table+".revision",db.count("SELECT COALESCE(SUM(revision),0) FROM "+table));
        }
        return out;
    }
    /** Comandos de administracion de Workflow.run (los que despacha Personnel): no actuan sobre expedientes. */
    static Set<String> administration()throws Exception{
        String source=Files.readString(Path.of("src/main/java/cl/nexbyte/sigera/Workflow.java"));
        int end=source.indexOf("-> personnel.run"),start=source.lastIndexOf("case ",end);
        var out=new TreeSet<String>();var m=java.util.regex.Pattern.compile("\"([A-Z_]+)\"").matcher(source.substring(start,end));
        while(m.find())out.add(m.group(1));
        assertTrue(out.contains("STAFF_CREATE")&&out.contains("CATALOG_IMPORT"),"lista de administracion");
        return out;
    }

    record Attempt(String who,String table,Long id,Map<String,Object> data){}

    @Test void todoComandoSobreUnExpedienteCerradoSeRechazaSinEscribir()throws Exception{
        long upr=scope("UPR-REQ");
        // Catalogos vigentes de las clasificaciones y del tipo de actuacion: la unica causa de rechazo es el cierre.
        for(String file:List.of("tipo-solicitud-v1.json","derechos-2022-v2.json","tipo-actuacion-v1.json")){
            String content=Files.readString(ReferenceCatalogsTest.DIR.resolve(file)).replace("\r\n","\n");
            long v=run("ti","CATALOG_IMPORT",null,null,Map.of("content",content,"reason","Fundamento sintetico"));
            run("ti","CATALOG_ACTIVATE","stat_catalog_version",v,Map.of("reason","Activacion sintetica"));
        }
        // REQ por SOL asumida, con Caso, constancia, tarea completada y colaboracion rechazada; Caso y REQ cerrados.
        long com=run(HEAD,"COM_CREATE",null,null,map("scope_id",upr,"channel","TELEFONO","received_at",ago(900),"subject","Ingreso sintetico","body","Relato sintetico","intake",Map.of()));
        long sol=run(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",upr,"reason","Distribucion sintetica"));
        long req=run(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF),"reason","Asume"));
        run(HEAD,"REQ_CLASSIFY","requirement",req,Map.of("complexity","BAJA","reason","Clasificacion sintetica"));
        long cas=run(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",upr,"responsible_id",user(PROF),"objective","Gestion sintetica","matter","UPR"));
        run(PROF,"CASE_NOTE","case_record",cas,Map.of("effective_at",ago(60),"body","Constancia sintetica"));
        long caseNote=n(db.one("SELECT MAX(id) AS id FROM management_note WHERE case_id=?",cas),"id");
        run(HEAD,"TASK_CREATE","case_record",cas,Map.of("executor_id",user(PROF),"title","Llamada sintetica"));
        long task=n(db.one("SELECT MAX(id) AS id FROM task_item WHERE case_id=?",cas),"id");
        run(PROF,"TASK_COMPLETE","task_item",task,Map.of("reason","Hecha"));
        run(HEAD,"FLOW_REQUEST","requirement",req,Map.of("kind","COLABORACION","target_scope",scope("UPD"),"reason","Colaboracion sintetica","objective","Apoyo"));
        long flowId=n(db.one("SELECT MAX(id) AS id FROM flow_request WHERE requirement_id=?",req),"id");
        run(UPD,"FLOW_DECIDE","flow_request",flowId,Map.of("decision","RECHAZADA","reason","Sin capacidad"));
        run(PROF,"CASE_CLOSE","case_record",cas,Map.of("reason","Cierre del Caso"));
        run(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Cierre del REQ","stat",Map.of("TIPO_SOLICITUD",Map.of("status","SIN_INFORMACION"))));
        // Primera atencion con una constancia del REQ y una entrega decidida, resuelta.
        long att=run(ADMIN,"ATTENTION_CREATE",null,null,map("scope_id",upr,"channel","PRESENCIAL","received_at",ago(900),"form",ActionTypesTest.form()));
        run(ADMIN,"REQ_NOTE","requirement",att,Map.of("effective_at",ago(60),"body","Constancia de la atencion","no_followup",true));
        long attNote=n(db.one("SELECT MAX(id) AS id FROM management_note WHERE requirement_id=?",att),"id");
        run(ADMIN,"ATTENTION_SEND","requirement",att,Map.of("recipient_kind","JEFATURA","scope_id",upr,"reason","Entrega sintetica"));
        long handoff=n(db.one("SELECT id FROM attention_handoff WHERE requirement_id=?",att),"id");
        run(ADMIN,"ATTENTION_DECIDE","requirement",att,Map.of("handoff_id",handoff,"decision","RETIRADA","reason","Retiro"));
        run(ADMIN,"ATTENTION_RESOLVE","requirement",att,Map.of("result","ORIENTACION","reason","Orientacion entregada","effective_at",ago(10),"stat",Map.of("TIPO_SOLICITUD",Map.of("status","SIN_INFORMACION"))));
        long attCom=n(db.one("SELECT communication_id FROM requirement WHERE id=?",att),"communication_id");
        assertEquals(List.of("CERRADO","CERRADO","CERRADO"),List.of(s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"),s(db.one("SELECT state FROM case_record WHERE id=?",cas),"state"),s(db.one("SELECT state FROM requirement WHERE id=?",att),"state")));

        var attempts=new LinkedHashMap<String,Attempt>();
        attempts.put("ATTENTION_SAVE",new Attempt(ADMIN,"requirement",att,Map.of("form",ActionTypesTest.form(),"reason","Intento")));
        attempts.put("ATTENTION_SEND",new Attempt(ADMIN,"requirement",att,Map.of("recipient_kind","JEFATURA","scope_id",upr,"reason","Intento")));
        attempts.put("ATTENTION_DECIDE",new Attempt(HEAD,"requirement",att,Map.of("handoff_id",handoff,"decision","RECIBIDA","reason","Intento")));
        attempts.put("ATTENTION_RESOLVE",new Attempt(ADMIN,"requirement",att,Map.of("result","ORIENTACION","reason","Intento","effective_at",ago(5))));
        attempts.put("COM_COMPLETE",new Attempt(HEAD,"communication",com,Map.of("intake",Map.of("contact_restrictions","Intento"),"reason","Intento")));
        attempts.put("COM_COMPLETE@ATENCION",new Attempt(ADMIN,"communication",attCom,Map.of("intake",Map.of("contact_restrictions","Intento"),"reason","Intento")));
        attempts.put("COM_ROUTE",new Attempt(DIST,"communication",com,Map.of("scope_id",upr,"reason","Intento")));
        attempts.put("COM_DISCARD",new Attempt(DIST,"communication",com,Map.of("disposition","INVALIDA","reason","Intento")));
        attempts.put("COM_ASSUME",new Attempt(HEAD,"communication",com,Map.of("responsible_id",user(PROF),"reason","Intento")));
        var solData=Map.<String,Object>of("scope_id",upr,"responsible_id",user(PROF),"disposition","DUPLICADA","reason","Intento");
        attempts.put("SOL_ROUTE",new Attempt(DIST,"solicitation",sol,solData));
        for(String t:List.of("SOL_RETURN","SOL_ASSUME","SOL_DISCARD"))attempts.put(t,new Attempt(HEAD,"solicitation",sol,solData));
        attempts.put("STAT_CLASSIFY",new Attempt(HEAD,"stat_event",com,Map.of("stat",Map.of("TIPO_SOLICITUD",Map.of("status","SIN_INFORMACION")))));
        attempts.put("STAT_CLASSIFY@ATENCION",new Attempt(ADMIN,"stat_event",attCom,Map.of("stat",Map.of("TIPO_SOLICITUD",Map.of("status","SIN_INFORMACION")))));
        attempts.put("RIGHTS_CLASSIFY",new Attempt(HEAD,"stat_event_req",req,Map.of("record","REQ","variables",Map.of("DERECHO",Map.of("status","SIN_INFORMACION")))));
        attempts.put("RIGHTS_CLASSIFY@CAS",new Attempt(PROF,"stat_event_cas",cas,Map.of("record","CAS","variables",Map.of("DERECHO",Map.of("status","SIN_INFORMACION")))));
        attempts.put("RIGHTS_CLASSIFY@ATENCION",new Attempt(ADMIN,"stat_event_req",att,Map.of("record","REQ","variables",Map.of("DERECHO",Map.of("status","SIN_INFORMACION")))));
        attempts.put("REQ_CLASSIFY",new Attempt(HEAD,"requirement",req,Map.of("complexity","ALTA","reason","Intento")));
        attempts.put("REQ_CASE_CREATE",new Attempt(HEAD,"requirement",req,Map.of("scope_id",upr,"responsible_id",user(PROF),"objective","Intento","matter","UPR")));
        attempts.put("REQ_CLOSE",new Attempt(HEAD,"requirement",req,Map.of("reason","Intento")));
        attempts.put("REQ_MATTER",new Attempt(HEAD,"requirement",req,Map.of("matter","UPR","reason","Intento")));
        attempts.put("REQ_NOTE",new Attempt(ADMIN,"requirement",att,Map.of("effective_at",ago(5),"body","Intento","no_followup",true,"action_type","ORIENTACION")));
        attempts.put("FLOW_REQUEST",new Attempt(HEAD,"requirement",req,Map.of("kind","TRANSFERENCIA","target_scope",scope("UPD"),"reason","Intento")));
        attempts.put("FLOW_DECIDE",new Attempt(UPD,"flow_request",flowId,Map.of("decision","ACEPTADA","responsible_id",user("profesional.upd"),"reason","Intento")));
        var caseData=Map.<String,Object>of("reason","Intento","responsible_id",user(HEAD),"user_id",user(HEAD),"matter","UPD","effective_at",ago(5),"body","Intento");
        for(String t:List.of("CASE_NOTE","CASE_ASSIGN","CASE_MEMBER_ADD","CASE_MEMBER_REMOVE","CASE_CLOSE","CASE_RETURN","CASE_WAIT","CASE_RESUME","CASE_MATTER"))attempts.put(t,new Attempt(HEAD,"case_record",cas,caseData));
        for(String t:List.of("CASE_REQUEST_CLOSE","CASE_WITHDRAW_CLOSE"))attempts.put(t,new Attempt(PROF,"case_record",cas,caseData));
        attempts.put("NOTE_RECTIFY",new Attempt(PROF,null,caseNote,Map.of("effective_at",ago(5),"body","Correccion","reason","Intento","action_type","OFICIO")));
        attempts.put("NOTE_RECTIFY@REQ",new Attempt(ADMIN,null,attNote,Map.of("effective_at",ago(5),"body","Correccion","reason","Intento")));
        attempts.put("TASK_CREATE",new Attempt(HEAD,"case_record",cas,Map.of("executor_id",user(PROF),"title","Intento","action_type","QUERELLA")));
        for(String t:List.of("TASK_EDIT","TASK_CANCEL","TASK_ATTEMPT"))attempts.put(t,new Attempt(HEAD,"task_item",task,Map.of("executor_id",user(PROF),"title","Intento","reason","Intento")));
        attempts.put("TASK_COMPLETE",new Attempt(PROF,"task_item",task,Map.of("reason","Intento")));

        // Cobertura: cada comando de Workflow.run esta en una sola categoria.
        var rejected=new TreeSet<String>();for(String k:attempts.keySet())rejected.add(k.contains("@")?k.substring(0,k.indexOf('@')):k);
        var categorized=new TreeSet<String>(rejected);categorized.addAll(EXCEPTIONS);categorized.addAll(CREATE);categorized.addAll(administration());categorized.addAll(STATISTICS);
        assertEquals(InstitutionalReaderTest.workflowCommands(),categorized,"todo comando de Workflow.run tiene categoria de inmutabilidad");
        assertEquals(rejected.size()+EXCEPTIONS.size()+CREATE.size()+administration().size()+STATISTICS.size(),categorized.size(),"categorias disjuntas");

        var outcomes=new TreeMap<String,String>();var changes=new TreeMap<String,Object>();
        for(var e:attempts.entrySet()){
            var a=e.getValue();String type=e.getKey().contains("@")?e.getKey().substring(0,e.getKey().indexOf('@')):e.getKey();
            var before=snapshot();
            try{run(a.who(),type,a.table(),a.id(),a.data());outcomes.put(e.getKey(),"EXITO");}
            catch(Problem p){
                if(p.status!=409)outcomes.put(e.getKey(),String.valueOf(p.status));
                else if(p.getMessage().contains("listado vigente"))outcomes.put(e.getKey(),"409 sin listado vigente");
            }
            var after=snapshot();
            if(!after.equals(before)){
                var diff=new TreeMap<String,String>();
                for(var k:before.keySet())if(!Objects.equals(before.get(k),after.get(k)))diff.put(k,before.get(k)+" -> "+after.get(k));
                changes.put(e.getKey(),diff);
            }
        }
        // Todo se rechaza; con 409, salvo cuatro guardas vigentes desde 0.4.0 que combinan estado y facultad y responden 403:
        // la ficha de primera atencion solo es editable con el REQ abierto (Access.attentionWritable) y SOL_ROUTE solo
        // redistribuye una SOL devuelta.
        assertEquals(Map.of("ATTENTION_SAVE","403","ATTENTION_SEND","403","ATTENTION_RESOLVE","403","SOL_ROUTE","403"),outcomes,"todo comando sobre un expediente cerrado se rechaza");
        assertEquals(Map.of(),changes,"ningun intento escribe");

        // Excepciones documentadas (S2): no modifican el expediente cerrado.
        var classifications=db.rows("SELECT * FROM stat_event WHERE communication_id IN (?,?) ORDER BY id",com,attCom);
        var closedReq=db.one("SELECT state,closed_ms,closed_by,close_reason,principal_scope,complexity,revision FROM requirement WHERE id=?",req);
        var closedCase=db.one("SELECT state,closed_ms,closed_by,closure_mode,close_reason,responsible_id FROM case_record WHERE id=?",cas);
        run(PROF,"CASE_ACK","case_record",cas,Map.of());
        long later=run(HEAD,"COM_CREATE",null,null,map("scope_id",upr,"channel","CORREO","received_at",ago(300),"subject","Posterior","body","Relato posterior","intake",Map.of()));
        run(DIST,"COM_LINK","communication",later,Map.of("folio",s(db.one("SELECT folio FROM requirement WHERE id=?",req),"folio"),"reason","Antecedente"));
        long link=n(db.one("SELECT id FROM communication_link WHERE communication_id=?",later),"id");
        run(HEAD,"LINK_DECIDE","communication_link",link,Map.of("decision","RECHAZADA","reason","Otro asunto"));
        run(HEAD,"LINK_RETURN","communication_link",link,Map.of("reason","Vuelve a clasificacion"));
        assertEquals(closedReq,db.one("SELECT state,closed_ms,closed_by,close_reason,principal_scope,complexity,revision FROM requirement WHERE id=?",req));
        assertEquals(closedCase,db.one("SELECT state,closed_ms,closed_by,closure_mode,close_reason,responsible_id FROM case_record WHERE id=?",cas));
        assertEquals(classifications,db.rows("SELECT * FROM stat_event WHERE communication_id IN (?,?) ORDER BY id",com,attCom));
    }
}
