package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Ficha del Caso: origen real del REQ (con o sin SOL) y facultades separadas de solicitar el termino
 * (responsable) y confirmar el cierre (jefatura, BAJA o jefatura propia).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.datasource.url=jdbc:h2:mem:sigeracasefaculties;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "siger.seed-demo=true","siger.data=./target/test-datafaculties-${random.uuid}"})
@ActiveProfiles("test")
class CaseDetailFacultiesTest {
 static final String HEAD="coordinacion.requerimientos",PROF="profesional";
 @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;
 long scope(){return n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");}
 long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
 Long rev(String table,Long id){return id==null?null:n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
 long run(String who,String type,String table,Long id,Map<String,Object> data){
  db.update("UPDATE staff SET must_change=FALSE WHERE username=?",who);
  return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev(table,id),data)).id();
 }
 Map<String,Object> cas(String who,long id){return queries.detail(access.actor(who),"CAS",id);}
 long openCase(long req){
  return run(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",scope(),"responsible_id",user(PROF),"objective","Seguimiento sintetico","matter","UPR"));
 }
 /** Primera atencion directa con seguimiento: COM+REQ sin SOL; la jefatura recibe y abre el Caso. */
 long directAttentionReq(){
  var form=Map.<String,Object>of("schema_version","1.0","modality","NNA","motive","Contacto sintetico de prueba","course","SEGUIMIENTO","participation","NO","people",List.of(Map.of("role","NNA","name","")));
  long req=run("administrativo","ATTENTION_CREATE","requirement",null,Map.of("scope_id",scope(),"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(120).toString(),"form",form));
  run("administrativo","ATTENTION_SEND","requirement",req,Map.of("recipient_kind","JEFATURA","scope_id",scope(),"reason","Requiere Caso"));
  long handoff=n(db.one("SELECT id FROM attention_handoff WHERE requirement_id=?",req),"id");
  run(HEAD,"ATTENTION_DECIDE","requirement",req,Map.of("handoff_id",handoff,"decision","RECIBIDA","reason","Recepcion de la ficha"));
  return req;
 }

 @Test void cadenaDelCasoUsaElOrigenRealDelReq(){
  long direct=directAttentionReq();
  assertNull(db.one("SELECT solicitation_id FROM requirement WHERE id=?",direct).get("solicitation_id"));
  assertEquals("ATENCION_DIRECTA",cas(PROF,openCase(direct)).get("req_origin"));

  long com=run(HEAD,"COM_CREATE","communication",null,Map.of("scope_id",scope(),"channel","TELEFONO","received_at",Instant.now().minusSeconds(60).toString(),"subject","Ingreso sintetico","body","Relato sintetico"));
  long assumed=run(HEAD,"COM_ASSUME","communication",com,Map.of("responsible_id",user(PROF),"reason","Ingreso propio de la jefatura"));
  assertNotNull(db.one("SELECT solicitation_id FROM requirement WHERE id=?",assumed).get("solicitation_id"),"asumir crea una SOL ASUMIDA");
  assertEquals("SOLICITUD",cas(HEAD,openCase(assumed)).get("req_origin"));
 }

 @Test void responsableSolicitaTerminoAunqueNoPuedaConfirmarlo(){
  long req=directAttentionReq(),c=openCase(req);
  for(String level:List.of("","ALTA")){
   if(!level.isEmpty())run(HEAD,"REQ_CLASSIFY","requirement",req,Map.of("complexity",level,"reason","Clasificacion sintetica"));
   var view=cas(PROF,c);
   assertEquals(false,view.get("can_close"),"el profesional no confirma un Caso "+(level.isEmpty()?"sin clasificar":level));
   assertEquals(true,view.get("can_request_close"),"pero si puede solicitar su termino");
   assertEquals(List.of(),view.get("close_request_blockers"));
  }
  run(PROF,"TASK_CREATE","case_record",c,Map.of("executor_id",user(PROF),"title","Llamada sintetica pendiente"));
  var blocked=cas(PROF,c);
  assertEquals(false,blocked.get("can_request_close"));
  assertTrue(blocked.get("close_request_blockers").toString().contains("tareas pendientes"));
  assertThrows(Problem.class,()->run(PROF,"CASE_REQUEST_CLOSE","case_record",c,Map.of("reason","Aun hay una tarea")),"la ficha y el comando coinciden");
  long task=n(db.one("SELECT id FROM task_item WHERE case_id=?",c),"id");
  run(PROF,"TASK_CANCEL","task_item",task,Map.of("reason","Ya no corresponde"));
  assertEquals(true,cas(PROF,c).get("can_request_close"));
  assertEquals(false,cas(HEAD,c).get("can_request_close"),"la jefatura no solicita termino de un Caso ajeno");

  run(PROF,"CASE_REQUEST_CLOSE","case_record",c,Map.of("reason","Gestion sintetica concluida"));
  assertEquals("EN_REVISION",s(db.one("SELECT state FROM case_record WHERE id=?",c),"state"));
  assertEquals(false,cas(PROF,c).get("can_request_close"),"ya esta en revision");
  assertEquals(true,cas(HEAD,c).get("can_close"),"la jefatura confirma en revision");
  assertThrows(Problem.class,()->run(PROF,"CASE_CLOSE","case_record",c,Map.of("reason","No se autoconfirma")));
 }
}
