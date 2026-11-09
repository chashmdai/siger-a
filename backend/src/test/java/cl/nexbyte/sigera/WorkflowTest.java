package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class WorkflowTest {
    @Autowired Workflow flow;@Autowired Db db;
    long scope1,scope2,dist,head1,head2,prof;
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    long user(String name,long scope,String role){
        long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,?, ?,FALSE)",name,name,"test-non-login",scope);
        grant(id,role,scope);return id;
    }
    void grant(long id,String role,Long scope){db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,?,?)",id,role,scope,0,"PRUEBA AUTOMATICA");}
    Workflow.Result run(String user,String type,long id,Long rev,Map<String,Object> data){return flow.run(user,new Workflow.Command(UUID.randomUUID(),type,id==0?null:id,rev,data));}
    @Test void recorridoPersistentePermisosIdempotenciaYCierreDerivacionAtomico(){
        db.update("INSERT INTO org_unit VALUES('UPR','Proteccion de prueba')");db.update("INSERT INTO org_unit VALUES('UPD','Promocion de prueba')");
        long uprRoot=db.insert("INSERT INTO org_scope(code,name,unit_code,site_code) VALUES('UPR','Direccion UPR','UPR','CENTRAL')");
        long updRoot=db.insert("INSERT INTO org_scope(code,name,unit_code,site_code) VALUES('UPD','Direccion UPD','UPD','CENTRAL')");
        scope1=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('UPR-TEST','Area UPR prueba','UPR','TEST','CENTRAL')");
        scope2=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('UPD-TEST','Area UPD prueba','UPD','TEST','CENTRAL')");
        dist=user("distribuidor",scope1,"ADMINISTRATIVO");grant(dist,"DISTRIBUIDOR",null);
        head1=user("jefe1",scope1,"DIRECCION");head2=user("jefe2",scope2,"DIRECCION");
        grant(head1,"DIRECCION",uprRoot);grant(head2,"DIRECCION",updRoot);
        prof=user("prof",scope1,"PROFESIONAL");
        long com=run("distribuidor","COM_CREATE",0,null,Map.of("scope_id",scope1,"channel","TELEFONO","received_at",Instant.now().minusSeconds(60).toString(),"subject","Prueba sintetica","body","Relato sintetico")).id();
        long sol=run("distribuidor","COM_ROUTE",com,rev("communication",com),Map.of("scope_id",scope1,"reason","Competencia sintetica")).id();
        long req=run("jefe1","SOL_ASSUME",sol,rev("solicitation",sol),Map.of("responsible_id",prof,"reason","Asuncion de prueba")).id();
        assertEquals(0,db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",req));
        run("jefe1","REQ_CLASSIFY",req,rev("requirement",req),Map.of("complexity","BAJA","reason","Clasificacion de prueba"));
        long cas=run("jefe1","REQ_CASE_CREATE",req,rev("requirement",req),Map.of("scope_id",scope1,"responsible_id",prof,"objective","Seguimiento sintetico","matter","UPR")).id();
        assertThrows(Problem.class,()->run("distribuidor","CASE_CLOSE",cas,rev("case_record",cas),Map.of("reason","Sin facultad")));
        assertThrows(Problem.class,()->run("jefe1","REQ_CLOSE",req,rev("requirement",req),Map.of("reason","No procede aun")));
        run("prof","TASK_CREATE",cas,rev("case_record",cas),Map.of("executor_id",prof,"title","Llamar al requiriente sin marca obligatoria"));
        long reminderTask=n(db.one("SELECT id FROM task_item WHERE case_id=?",cas),"id");
        assertThrows(Problem.class,()->run("prof","CASE_REQUEST_CLOSE",cas,rev("case_record",cas),Map.of("reason","Aun hay una tarea")));
        assertThrows(Problem.class,()->run("prof","CASE_CLOSE",cas,rev("case_record",cas),Map.of("reason","Aun hay una tarea")));
        run("prof","TASK_COMPLETE",reminderTask,rev("task_item",reminderTask),Map.of("reason","Contacto realizado"));
        run("prof","CASE_REQUEST_CLOSE",cas,rev("case_record",cas),Map.of("reason","Listo para cerrar"));
        run("prof","CASE_WITHDRAW_CLOSE",cas,rev("case_record",cas),Map.of("reason","Necesito registrar otra gestion"));
        assertEquals("EN_GESTION",s(db.one("SELECT state FROM case_record WHERE id=?",cas),"state"));
        run("prof","CASE_CLOSE",cas,rev("case_record",cas),Map.of("reason","Resultado sintetico concluido"));
        assertEquals("ABIERTO",s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"));
        run("jefe1","REQ_CLOSE",req,rev("requirement",req),Map.of("reason","Cierre expreso"));
        assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"));
        // Segunda gestion para comprobar derivacion sin cierre parcial ni duplicados.
        long com2=run("jefe1","COM_CREATE",0,null,Map.of("scope_id",scope1,"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(10).toString(),"subject","Derivacion sintetica","body","Antecedente de prueba")).id();
        long req2=run("jefe1","COM_ASSUME",com2,rev("communication",com2),Map.of("responsible_id",prof,"reason","Ingreso propio")).id();
        long origin=run("jefe1","REQ_CASE_CREATE",req2,rev("requirement",req2),Map.of("responsible_id",prof,"objective","Objetivo origen","matter","UPR")).id();
        run("prof","TASK_CREATE",origin,rev("case_record",origin),Map.of("executor_id",prof,"title","Obligatoria sintetica","mandatory",true));
        long task=n(db.one("SELECT id FROM task_item WHERE case_id=?",origin),"id");
        run("jefe1","FLOW_REQUEST",req2,rev("requirement",req2),Map.of("kind","DERIVACION","case_id",origin,"target_scope",scope2,"objective","Objetivo destino","reason","Necesidad de otra unidad"));
        long request=n(db.one("SELECT id FROM flow_request WHERE requirement_id=?",req2),"id");
        var acceptance=new Workflow.Command(UUID.randomUUID(),"FLOW_DECIDE",request,rev("flow_request",request),Map.of("decision","ACEPTADA","responsible_id",head2,"reason","Aceptacion fundada"));
        assertThrows(Problem.class,()->flow.run("jefe2",acceptance));
        assertEquals(1,db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",req2));
        assertEquals("EN_GESTION",s(db.one("SELECT state FROM case_record WHERE id=?",origin),"state"));
        run("prof","TASK_COMPLETE",task,rev("task_item",task),Map.of("reason","Tarea realizada"));
        flow.run("jefe2",acceptance);flow.run("jefe2",acceptance);
        assertEquals(2,db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",req2));
        assertEquals("CERRADO",s(db.one("SELECT state FROM case_record WHERE id=?",origin),"state"));
        assertEquals("ABIERTO",s(db.one("SELECT state FROM requirement WHERE id=?",req2),"state"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM closure_record WHERE case_id=? AND mode='DERIVACION'",origin));
        // Enrutamiento 0.3.0: Area habitual va a Direccion, Requerimientos a Coordinadora.
        long reqArea=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('UPR-REQ','Requerimientos','UPR','REQ','CENTRAL')");
        long coordinator=user("coordinadora-prueba",reqArea,"JEFATURA");
        long reqProfessional=user("prof-requerimientos-prueba",reqArea,"PROFESIONAL");
        long com3=run("distribuidor","COM_CREATE",0,null,Map.of("scope_id",scope1,"channel","TELEFONO","received_at",Instant.now().minusSeconds(15).toString(),"subject","Ingreso Requerimientos","body","Sintetico")).id();
        long sol3=run("distribuidor","COM_ROUTE",com3,rev("communication",com3),Map.of("scope_id",uprRoot,"area_scope_id",reqArea,"reason","Enviar a coordinacion")).id();
        assertEquals(reqArea,n(db.one("SELECT destination_scope FROM solicitation WHERE id=?",sol3),"destination_scope"));
        assertThrows(Problem.class,()->run("jefe1","SOL_ASSUME",sol3,rev("solicitation",sol3),Map.of("responsible_id",reqProfessional,"reason","No recibe la directora")));
        long req3=run("coordinadora-prueba","SOL_ASSUME",sol3,rev("solicitation",sol3),Map.of("responsible_id",reqProfessional,"reason","Asume su area")).id();
        assertEquals(reqArea,n(db.one("SELECT principal_scope FROM requirement WHERE id=?",req3),"principal_scope"));
        long com4=run("distribuidor","COM_CREATE",0,null,Map.of("scope_id",scope1,"channel","CORREO","received_at",Instant.now().minusSeconds(15).toString(),"subject","Ingreso Difusion UPD","body","Sintetico")).id();
        long sol4=run("distribuidor","COM_ROUTE",com4,rev("communication",com4),Map.of("scope_id",updRoot,"area_scope_id",scope2,"reason","Area particular sin coordinacion")).id();
        var row4=db.one("SELECT destination_scope,requested_area_scope FROM solicitation WHERE id=?",sol4);
        assertEquals(updRoot,n(row4,"destination_scope"));assertEquals(scope2,n(row4,"requested_area_scope"));
        assertThrows(Problem.class,()->run("coordinadora-prueba","SOL_ASSUME",sol4,rev("solicitation",sol4),Map.of("responsible_id",head2,"reason","No tiene competencia")));
        long req4=run("jefe2","SOL_ASSUME",sol4,rev("solicitation",sol4),Map.of("responsible_id",head2,"reason","Asuncion Direccion UPD")).id();
        assertEquals(updRoot,n(db.one("SELECT principal_scope FROM requirement WHERE id=?",req4),"principal_scope"));
        long com5=run("distribuidor","COM_CREATE",0,null,Map.of("scope_id",scope1,"channel","TELEFONO","received_at",Instant.now().minusSeconds(15).toString(),"subject","UPR sin area","body","Sintetico")).id();
        long sol5=run("distribuidor","COM_ROUTE",com5,rev("communication",com5),Map.of("scope_id",uprRoot,"reason","Sin area decidida")).id();
        assertThrows(Problem.class,()->run("jefe1","SOL_ASSUME",sol5,rev("solicitation",sol5),Map.of("responsible_id",reqProfessional,"reason","No salta la coordinacion")));
        run("jefe1","SOL_ASSUME",sol5,rev("solicitation",sol5),Map.of("responsible_id",prof,"reason","Prof autorizado por su Directora"));
    }
}
