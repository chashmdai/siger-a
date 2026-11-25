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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Hitos (0.5.0, D6, V14, gate G10): un recorrido que produce los 26 hitos con su actor, persona, ambito y cadena; un
 * comando rechazado o repetido no escribe hitos; la carga historica por lotes reproduce exactamente lo escrito por los
 * comandos (salvo reconstructed), es idempotente, no duplica lo escrito en vivo durante el arranque y omite y cuenta
 * los eventos de formato antiguo o desconocido, con el aviso de Administracion.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_hitos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-hitos-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MilestoneTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Db db;@Autowired Milestones milestones;

    static final String HEAD="coordinacion.requerimientos",PROF="profesional",ADMIN="administrativo",DIST="distribucion",DIR_UPD="direccion.upd",PROF_UPD="profesional.upd";
    static final List<String> ALL=List.of("COM_REGISTRADA","SOL_DISTRIBUIDA","SOL_REDIRIGIDA","SOL_DEVUELTA","SOL_DESCARTADA","SOL_ASUMIDA",
        "REQ_CREADO","REQ_CERRADO","REQ_REACTIVADO","CASO_CREADO","CASO_ASIGNADO","ASIGNACION_ACUSADA","CASO_ESPERA","CASO_REANUDADO",
        "CIERRE_SOLICITADO","CIERRE_RETIRADO","CIERRE_DEVUELTO","CASO_CERRADO","CASO_REABIERTO","TAREA_CREADA","TAREA_COMPLETADA","TAREA_CANCELADA",
        "ENTREGA_ENVIADA","ENTREGA_DECIDIDA","FLUJO_SOLICITADO","FLUJO_DECIDIDO");
    static Recorrido rx;
    record Recorrido(long com,long sol,long req,long cas,long collab,long immediateReq,long attentionReq,long directSol,long discarded,long external,long reopened){}

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    long run(String who,String type,String table,Long id,Map<String,Object> data){
        Long rev=table==null||id==null?null:n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");
        return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data)).id();
    }
    long com(String subject){return com(ADMIN,subject);}
    long com(String who,String subject){
        return run(who,"COM_CREATE",null,null,Map.of("intake",Map.of(),"scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",ago(300),"subject",subject,"body","Relato sintetico"));
    }
    long solOf(long com){return n(db.one("SELECT id FROM solicitation WHERE communication_id=?",com),"id");}
    long count(){return db.count("SELECT COUNT(*) FROM milestone");}
    List<Map<String,Object>> of(String event,String type,long resource){
        return db.rows("SELECT * FROM milestone WHERE event=? AND resource_type=? AND resource_id=? ORDER BY at_ms,id",event,type,resource);
    }
    Map<String,Object> one(String event,String type,long resource){var rows=of(event,type,resource);assertEquals(1,rows.size(),event+" "+type+" "+resource);return rows.getFirst();}
    /** Hito comparable: todas las columnas salvo id y reconstructed. */
    static String line(Map<String,Object> r){
        return String.join("|",s(r,"event"),s(r,"resource_type"),s(r,"resource_id"),s(r,"communication_id"),s(r,"requirement_id"),s(r,"case_id"),
            s(r,"actor_id"),s(r,"subject_user"),s(r,"scope_id"),s(r,"at_ms"),s(r,"detail"));
    }
    List<String> lines(){return db.rows("SELECT * FROM milestone").stream().map(MilestoneTest::line).sorted().toList();}
    void forget(){db.update("DELETE FROM milestone");db.update("DELETE FROM application_marker WHERE code=?",Milestones.MARKER);}
    boolean warned(){return Personnel.warnings(db).stream().anyMatch(w->s(w,"code").equals("HITOS_OMITIDOS"));}

    synchronized Recorrido recorrido(){
        if(rx!=null)return rx;
        db.update("UPDATE staff SET must_change=FALSE");
        long upr=scope("UPR-REQ"),upd=scope("UPD");
        // SOL distribuida, redirigida a UPD, devuelta, redistribuida y asumida (nace el REQ).
        long com=com("COM con todo el recorrido");
        run(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",upr,"reason","Clasificacion sintetica"));
        long sol=solOf(com);
        run(HEAD,"SOL_ROUTE","solicitation",sol,Map.of("scope_id",upd,"reason","Corresponde a UPD"));
        run(DIR_UPD,"SOL_RETURN","solicitation",sol,Map.of("reason","Vuelve a clasificacion"));
        run(DIST,"SOL_ROUTE","solicitation",sol,Map.of("scope_id",upr,"reason","Redistribucion"));
        long req=run(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF),"reason","Asumida"));
        run(HEAD,"REQ_CLASSIFY","requirement",req,Map.of("complexity","BAJA","reason","Clasificacion sintetica"));
        // Caso con acuse, espera, tareas, solicitud de cierre retirada y devuelta, reasignacion y cierre.
        long cas=run(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",upr,"responsible_id",user(PROF),"objective","Seguimiento sintetico","matter","UPR"));
        run(PROF,"CASE_ACK","case_record",cas,Map.of());
        run(HEAD,"CASE_WAIT","case_record",cas,Map.of("reason","Espera de antecedentes"));
        run(HEAD,"CASE_RESUME","case_record",cas,Map.of("reason","Llegaron los antecedentes"));
        run(PROF,"TASK_CREATE","case_record",cas,Map.of("executor_id",user(PROF),"title","Llamada sintetica"));
        run(PROF,"TASK_CREATE","case_record",cas,Map.of("executor_id",user(PROF),"title","Oficio sintetico"));
        var tasks=db.rows("SELECT id FROM task_item WHERE case_id=? ORDER BY id",cas);
        run(PROF,"TASK_COMPLETE","task_item",n(tasks.get(0),"id"),Map.of("reason","Llamada realizada"));
        run(PROF,"TASK_CANCEL","task_item",n(tasks.get(1),"id"),Map.of("reason","Ya no corresponde"));
        run(PROF,"CASE_REQUEST_CLOSE","case_record",cas,Map.of("reason","Gestion concluida"));
        run(PROF,"CASE_WITHDRAW_CLOSE","case_record",cas,Map.of("reason","Falta un antecedente"));
        run(PROF,"CASE_REQUEST_CLOSE","case_record",cas,Map.of("reason","Gestion concluida"));
        run(HEAD,"CASE_RETURN","case_record",cas,Map.of("reason","Falta informar al requirente"));
        run(HEAD,"CASE_ASSIGN","case_record",cas,Map.of("responsible_id",user(HEAD),"reason","Continua la coordinacion"));
        // Colaboracion aceptada (Caso paralelo en UPD) y cerrada por su profesional.
        run(HEAD,"FLOW_REQUEST","requirement",req,Map.of("kind","COLABORACION","target_scope",upd,"reason","Colaboracion sintetica","objective","Apoyo sintetico"));
        long flowId=n(db.one("SELECT id FROM flow_request WHERE requirement_id=?",req),"id");
        run(DIR_UPD,"FLOW_DECIDE","flow_request",flowId,Map.of("decision","ACEPTADA","responsible_id",user(PROF_UPD),"case_scope_id",scope("UPD-EDU"),"matter","UPD","reason","Aceptada"));
        long collab=n(db.one("SELECT successor_case_id FROM flow_request WHERE id=?",flowId),"successor_case_id");
        run(PROF_UPD,"CASE_CLOSE","case_record",collab,Map.of("reason","Apoyo entregado"));
        run(HEAD,"CASE_CLOSE","case_record",cas,Map.of("reason","Cierre confirmado"));
        run(HEAD,"REQ_CLOSE","requirement",req,Map.of("reason","Motivo de cierre sintetico"));
        // Nueva gestion: REQ reactivado y luego el Caso reabierto.
        run(HEAD,"REOPEN_REQUEST","requirement",req,Map.of("reason","Nuevos antecedentes"));
        run(HEAD,"REOPEN_DECIDE","reopening",n(db.one("SELECT id FROM reopening WHERE requirement_id=? AND state='PENDIENTE'",req),"id"),Map.of("decision","ACEPTADA","reason","Procede"));
        run(HEAD,"REOPEN_REQUEST","requirement",req,Map.of("case_id",cas,"reason","Reabrir el Caso"));
        run(HEAD,"REOPEN_DECIDE","reopening",n(db.one("SELECT id FROM reopening WHERE requirement_id=? AND state='PENDIENTE'",req),"id"),Map.of("decision","ACEPTADA","reason","Procede"));
        // Asuncion directa de la jefatura y SOL descartada.
        long direct=com(HEAD,"COM asumida por la jefatura");
        run(HEAD,"COM_ASSUME","communication",direct,Map.of("responsible_id",user(PROF),"reason","Ingreso propio"));
        long discarded=com("COM duplicada");
        run(DIST,"COM_ROUTE","communication",discarded,Map.of("scope_id",upr,"reason","Clasificacion sintetica"));
        run(HEAD,"SOL_DISCARD","solicitation",solOf(discarded),Map.of("disposition","DUPLICADA","reason","Ya ingresada"));
        // Atencion resuelta en el acto (fecha efectiva declarada anterior al registro).
        long immediate=run(ADMIN,"COM_IMMEDIATE",null,null,Map.of("intake",Map.of(),"scope_id",upr,"channel","TELEFONO","received_at",ago(600),"subject","Orientacion","body","Relato sintetico",
            "reason","Orientacion entregada","effective_at",ago(300),"result","ORIENTACION"));
        // Primera atencion entregada a un profesional, recibida y resuelta.
        long attention=run(ADMIN,"ATTENTION_CREATE",null,null,Map.of("scope_id",upr,"channel","PRESENCIAL","received_at",ago(600),"form",Map.of("schema_version","1.0","modality","NNA",
            "motive","Contacto sintetico NNA","course","EN_ATENCION","participation","NO","people",List.of(Map.of("role","NNA","name","Nombre NNA sintetico")))));
        run(ADMIN,"ATTENTION_SEND","requirement",attention,Map.of("recipient_kind","PROFESIONAL","scope_id",upr,"user_id",user(PROF),"reason","Continuar la atencion"));
        long handoff=n(db.one("SELECT id FROM attention_handoff WHERE requirement_id=?",attention),"id");
        run(PROF,"ATTENTION_DECIDE","requirement",attention,Map.of("handoff_id",handoff,"decision","RECIBIDA","reason","Recibo la atencion"));
        run(PROF,"ATTENTION_RESOLVE","requirement",attention,Map.of("result","ORIENTACION","effective_at",ago(5),"reason","Orientacion concluida"));
        // Comunicacion del Formulario (identidad de quien registra la entrada tecnica).
        long external=flow.conserveExternal(ADMIN,upr,FormularioDocs.sign(FormularioDocs.cuVulneration())).id();
        rx=new Recorrido(com,sol,req,cas,collab,immediate,attention,solOf(direct),solOf(discarded),external,cas);
        return rx;
    }

    @Test @Order(1) void cadaTransicionEscribeSuHito(){
        var x=recorrido();
        assertEquals(new TreeSet<>(ALL),new TreeSet<>(db.rows("SELECT DISTINCT event FROM milestone").stream().map(r->s(r,"event")).toList()));
        assertEquals(0,db.count("SELECT COUNT(*) FROM milestone WHERE reconstructed=TRUE"));
        long upr=scope("UPR-REQ"),upd=scope("UPD"),prof=user(PROF),head=user(HEAD);
        // La SOL: cuatro tramos con el ambito que debe actuar en cada uno, y su asuncion con el REQ.
        assertEquals(upr,n(one("SOL_DISTRIBUIDA","SOL",x.sol()),"scope_id"));
        var redirected=of("SOL_REDIRIGIDA","SOL",x.sol());
        assertEquals(List.of(upd,upr),redirected.stream().map(r->n(r,"scope_id")).toList());
        assertEquals(upd,n(one("SOL_DEVUELTA","SOL",x.sol()),"scope_id"));
        var assumed=one("SOL_ASUMIDA","SOL",x.sol());
        assertEquals(x.req(),n(assumed,"requirement_id"));assertEquals(prof,n(assumed,"subject_user"));assertNull(assumed.get("detail"));
        assertEquals(x.com(),n(assumed,"communication_id"));
        assertEquals("DIRECTA",s(one("SOL_ASUMIDA","SOL",x.directSol()),"detail"));
        assertEquals("DUPLICADA",s(one("SOL_DESCARTADA","SOL",x.discarded()),"detail"));
        // El instante del hito es el de su fuente.
        assertEquals(n(db.one("SELECT created_ms FROM requirement WHERE id=?",x.req()),"created_ms"),n(one("REQ_CREADO","REQ",x.req()),"at_ms"));
        assertEquals(n(db.one("SELECT at_ms FROM audit_event WHERE action='SOL_DISTRIBUIDA' AND resource_id=?",x.sol()),"at_ms"),n(one("SOL_DISTRIBUIDA","SOL",x.sol()),"at_ms"));
        // REQ: cierre, reactivacion; el Caso: asignaciones con su acuse, espera, revision de cierre, cierre y reapertura.
        assertEquals("JEFATURA_PRINCIPAL",s(one("REQ_CERRADO","REQ",x.req()),"detail"));
        one("REQ_REACTIVADO","REQ",x.req());
        var created=one("CASO_CREADO","CAS",x.cas());
        assertEquals(prof,n(created,"subject_user"));assertEquals(x.req(),n(created,"requirement_id"));assertEquals(upr,n(created,"scope_id"));
        var assignments=db.rows("SELECT * FROM milestone WHERE event='CASO_ASIGNADO' AND case_id=? ORDER BY at_ms,id",x.cas());
        assertEquals(List.of(prof,head),assignments.stream().map(r->n(r,"subject_user")).toList());
        var ack=db.one("SELECT * FROM milestone WHERE event='ASIGNACION_ACUSADA' AND case_id=?",x.cas());
        assertEquals(n(assignments.getFirst(),"resource_id"),n(ack,"resource_id"));assertEquals(prof,n(ack,"actor_id"));
        for(String event:List.of("CASO_ESPERA","CASO_REANUDADO","CIERRE_RETIRADO","CIERRE_DEVUELTO","CASO_REABIERTO"))one(event,"CAS",x.cas());
        assertEquals(2,of("CIERRE_SOLICITADO","CAS",x.cas()).size());
        assertEquals("JEFATURA_PROPIA",s(one("CASO_CERRADO","CAS",x.cas()),"detail"));
        assertEquals("PROFESIONAL_BAJA",s(one("CASO_CERRADO","CAS",x.collab()),"detail"));
        assertEquals(scope("UPD-EDU"),n(one("CASO_CREADO","CAS",x.collab()),"scope_id"));
        // Tareas, con su ejecutor.
        assertEquals(2,db.count("SELECT COUNT(*) FROM milestone WHERE event='TAREA_CREADA' AND case_id=? AND subject_user=?",x.cas(),prof));
        assertEquals(1,db.count("SELECT COUNT(*) FROM milestone WHERE event='TAREA_COMPLETADA' AND case_id=? AND subject_user=?",x.cas(),prof));
        assertEquals(1,db.count("SELECT COUNT(*) FROM milestone WHERE event='TAREA_CANCELADA' AND case_id=?",x.cas()));
        // Colaboracion: solicitada y decidida, con el destino como ambito.
        var flowRow=db.one("SELECT * FROM milestone WHERE event='FLUJO_SOLICITADO' AND requirement_id=?",x.req());
        assertEquals("COLABORACION",s(flowRow,"detail"));assertEquals(upd,n(flowRow,"scope_id"));
        assertEquals("ACEPTADA",s(one("FLUJO_DECIDIDO","FLUJO",n(flowRow,"resource_id")),"detail"));
        // Atencion resuelta en el acto: el cierre nunca antes de la creacion, aunque la fecha efectiva declarada lo sea.
        long created1=n(one("REQ_CREADO","REQ",x.immediateReq()),"at_ms");
        var closed1=one("REQ_CERRADO","REQ",x.immediateReq());
        assertEquals(created1,n(closed1,"at_ms"));assertEquals("PRIMERA_ATENCION",s(closed1,"detail"));
        assertTrue(n(db.one("SELECT closed_ms FROM requirement WHERE id=?",x.immediateReq()),"closed_ms")<created1);
        // Primera atencion: la persona a cargo al crear (administrativo), entrega al profesional, recibida y resuelta.
        assertEquals(user(ADMIN),n(one("REQ_CREADO","REQ",x.attentionReq()),"subject_user"));
        var sent=db.one("SELECT * FROM milestone WHERE event='ENTREGA_ENVIADA' AND requirement_id=?",x.attentionReq());
        assertEquals(prof,n(sent,"subject_user"));
        assertEquals("RECIBIDA",s(one("ENTREGA_DECIDIDA","ENTREGA",n(sent,"resource_id")),"detail"));
        assertEquals("PRIMERA_ATENCION",s(one("REQ_CERRADO","REQ",x.attentionReq()),"detail"));
        // Cada COM registrada (manual, atencion directa, atencion resuelta en el acto y Formulario) tiene su hito.
        assertEquals(db.count("SELECT COUNT(*) FROM communication"),db.count("SELECT COUNT(*) FROM milestone WHERE event='COM_REGISTRADA'"));
        one("COM_REGISTRADA","COM",x.external());
    }

    @Test @Order(2) void comandoRechazadoORepetidoNoEscribeHitos(){
        var x=recorrido();
        long before=count();
        assertThrows(Problem.class,()->run(PROF,"CASE_WAIT","case_record",x.cas(),Map.of("reason","Sin facultad")));
        assertThrows(Problem.class,()->run(PROF,"REQ_CLOSE","requirement",x.req(),Map.of("reason","Sin facultad")));
        assertEquals(before,count());
        var key=UUID.randomUUID();
        var cmd=new Workflow.Command(key,"TASK_CREATE",x.cas(),n(db.one("SELECT revision FROM case_record WHERE id=?",x.cas()),"revision"),Map.of("executor_id",user(HEAD),"title","Tarea repetida"));
        flow.run(HEAD,cmd);flow.run(HEAD,cmd);
        assertEquals(before+1,count());
    }

    @Test @Order(3) void cargaHistoricaReproduceLoEscritoYEsIdempotente(){
        recorrido();
        var live=lines();
        forget();
        milestones.batch=2; // varios lotes de COM, cada uno en su transaccion
        milestones.rebuild();
        assertEquals(live,lines());
        assertEquals(count(),db.count("SELECT COUNT(*) FROM milestone WHERE reconstructed=TRUE"));
        assertEquals(0,Milestones.omitted(db));assertFalse(warned());
        // Con el marcador no vuelve a correr: mismas filas, mismos ids.
        var ids=db.rows("SELECT id FROM milestone ORDER BY id");
        milestones.rebuild();
        assertEquals(ids,db.rows("SELECT id FROM milestone ORDER BY id"));
        // Un corte antes del marcador: los lotes repetidos reemplazan sus hitos reconstruidos, sin duplicar.
        db.update("DELETE FROM application_marker WHERE code=?",Milestones.MARKER);
        // Los 5 ultimos (MySQL no admite LIMIT en una subconsulta IN: se toma el corte aparte).
        long cut=n(db.one("SELECT id FROM milestone ORDER BY id DESC LIMIT 1 OFFSET 4"),"id");
        assertEquals(5,db.update("DELETE FROM milestone WHERE id>=?",cut));
        milestones.rebuild();
        assertEquals(live,lines());
    }

    @Test @Order(4) void cargaDuranteElArranqueNoDuplicaLoEscritoEnVivo(){
        var x=recorrido();
        var history=lines();
        forget();
        // Un comando llega antes de que corra la carga historica: su hito queda en vivo y no se reconstruye otra vez.
        run(HEAD,"TASK_CREATE","case_record",x.reopened(),Map.of("executor_id",user(HEAD),"title","Tarea durante el arranque"));
        long task=n(db.one("SELECT MAX(id) AS id FROM task_item WHERE case_id=?",x.reopened()),"id");
        milestones.rebuild();
        var row=one("TAREA_CREADA","TAREA",task);
        assertFalse(b(row,"reconstructed"));
        assertEquals(history.size()+1,count());
        var expected=new ArrayList<>(history);expected.add(line(row));
        assertEquals(expected.stream().sorted().toList(),lines());
    }

    @Test @Order(5) void eventosAntiguosODesconocidosSeOmitenYSeCuentan(){
        var x=recorrido();
        forget();
        milestones.rebuild();
        var complete=lines();
        forget();
        long actor=user(HEAD),past=System.currentTimeMillis()-86_400_000L;
        long upr=scope("UPR-REQ");
        // Tarea sin identificador en la nota, recurso con el tipo de otra version, SOL inexistente y flujo de un tipo desconocido.
        db.update("INSERT INTO audit_event(actor_id,at_ms,action,resource_type,resource_id,scope_id,note) VALUES(?,?,?,?,?,?,?)",actor,past,"TASK_COMPLETE","CAS",x.cas(),upr,"Tarea completada");
        db.update("INSERT INTO audit_event(actor_id,at_ms,action,resource_type,resource_id,scope_id,note) VALUES(?,?,?,?,?,?,?)",actor,past,"CASE_WAIT","CASO",x.cas(),upr,"Formato anterior");
        db.update("INSERT INTO audit_event(actor_id,at_ms,action,resource_type,resource_id,scope_id,note) VALUES(?,?,?,?,?,?,?)",actor,past,"SOL_REDIRIGIDA","SOL",999_999L,upr,"Destino anterior");
        db.update("INSERT INTO flow_request(kind,requirement_id,origin_scope,target_scope,requested_by,created_ms,reason,state,decided_by,decided_ms) VALUES(?,?,?,?,?,?,?,?,?,?)",
            "TRASPASO",x.req(),upr,scope("UPD"),actor,past,"Flujo de otra version","CONCLUIDA",actor,past+1000);
        milestones.rebuild();
        assertEquals(complete,lines()); // lo reconocido se reconstruye igual
        assertEquals(5,Milestones.omitted(db)); // 3 eventos de auditoria + flujo solicitado y decidido
        var warning=Personnel.warnings(db).stream().filter(w->s(w,"code").equals("HITOS_OMITIDOS")).findFirst().orElseThrow();
        assertEquals(5L,((Number)warning.get("count")).longValue());
        assertTrue(s(warning,"message").startsWith("Hitos reconstruidos con 5 omisiones"));
    }
}
