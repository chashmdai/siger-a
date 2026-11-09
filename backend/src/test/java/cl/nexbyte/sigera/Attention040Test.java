package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/** Tres recorridos integrados: pocos tests, centrados en las nuevas transiciones. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.datasource.url=jdbc:h2:mem:sigera040;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "siger.seed-demo=true","siger.data=./target/test-data040-${random.uuid}"})
@ActiveProfiles("test")
class Attention040Test {
 @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Attention attention;@Autowired BoardQueries boards;
 long scope(){return n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");}
 long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
 long revision(long id){return n(db.one("SELECT revision FROM requirement WHERE id=?",id),"revision");}
 Map<String,Object> form(String course){return new LinkedHashMap<>(Map.of("schema_version","1.0","modality","NNA","motive","Primer contacto sintetico, sin identidad disponible","course",course,"participation","NO","people",List.of(Map.of("role","NNA","name",""))));}
 Map<String,Object> creation(String course){return new LinkedHashMap<>(Map.of("scope_id",scope(),"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(120).toString(),"form",form(course)));}
 Workflow.Result run(String user,String type,Long id,Map<String,Object> data){db.update("UPDATE staff SET must_change=FALSE WHERE username=?",user);return flow.run(user,new Workflow.Command(UUID.randomUUID(),type,id,id==null?null:revision(id),data));}
 @Test void registroParcialEntregaProfesionalVersionesYPermisos(){
  db.update("UPDATE staff SET must_change=FALSE WHERE username IN ('administrativo','profesional')");
  var cmd=new Workflow.Command(UUID.randomUUID(),"ATTENTION_CREATE",null,null,creation("EN_ATENCION"));
  long req=flow.run("administrativo",cmd).id();assertEquals(req,flow.run("administrativo",cmd).id());
  var original=db.one("SELECT * FROM requirement WHERE id=?",req);long com=n(original,"communication_id");
  assertNull(original.get("solicitation_id"));assertEquals(0,db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",req));
  assertEquals(0,db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",com));
  assertThrows(Problem.class,()->run("profesional.upd","ATTENTION_SAVE",req,Map.of("form",form("EN_ATENCION"),"reason","Fuera de alcance")));
  run("administrativo","ATTENTION_SEND",req,Map.of("recipient_kind","PROFESIONAL","scope_id",scope(),"user_id",user("profesional"),"reason","Continuar primera atencion"));
  assertEquals(user("administrativo"),n(db.one("SELECT attention_user FROM requirement WHERE id=?",req),"attention_user"));
  assertTrue(((Number)boards.page(access.actor("profesional"),"atenciones",0,25,"","incoming",null).get("total")).longValue()>0);
  long handoff=n(db.one("SELECT id FROM attention_handoff WHERE requirement_id=?",req),"id");
  run("profesional","ATTENTION_DECIDE",req,Map.of("handoff_id",handoff,"decision","RECIBIDA","reason","Atiendo a la persona"));
  assertEquals(scope(),n(db.one("SELECT principal_scope FROM requirement WHERE id=?",req),"principal_scope"));
  var data=form("EN_ATENCION");data.put("observations","Se complementa sin alterar el motivo original");
  run("profesional","ATTENTION_SAVE",req,Map.of("form",data,"reason","Complementacion del profesional"));
  assertEquals(2,db.count("SELECT COUNT(*) FROM attention_revision WHERE requirement_id=?",req));
  assertEquals("Primer contacto sintetico, sin identidad disponible",s(db.one("SELECT body FROM communication WHERE id=?",com),"body"));
  assertFalse(attention.mayRead(access.actor("administrativo"),db.one("SELECT * FROM requirement WHERE id=?",req)));
  assertThrows(Problem.class,()->run("administrativo","ATTENTION_SAVE",req,Map.of("form",data,"reason","No conserva el encargo")));
 }
 @Test void gestionInmediataSinIdentificacionNiCaso(){
  for(String who:List.of("administrativo","profesional","coordinacion.requerimientos")){
   var data=creation("EN_ATENCION");data.put("resolve_now",true);data.put("result","ORIENTACION");data.put("effective_at",Instant.now().minusSeconds(30).toString());data.put("reason","Orientacion sintetica efectivamente concluida");
   long req=run(who,"ATTENTION_CREATE",null,data).id();
   assertEquals("CERRADO",s(db.one("SELECT state FROM requirement WHERE id=?",req),"state"));
   assertEquals(0,db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",req));
   assertEquals(1,db.count("SELECT COUNT(*) FROM closure_record WHERE requirement_id=? AND mode='PRIMERA_ATENCION'",req));
   assertThrows(Problem.class,()->run(who,"ATTENTION_SAVE",req,Map.of("form",form("EN_ATENCION"),"reason","No se sobrescribe cerrado")));
  }
 }
 @Test void seguimientoRequiereJefaturaYEntregaNoEsTraslado(){
  long req=run("administrativo","ATTENTION_CREATE",null,creation("SEGUIMIENTO")).id();
  assertThrows(Problem.class,()->run("administrativo","ATTENTION_RESOLVE",req,Map.of("result","ORIENTACION","effective_at",Instant.now().minusSeconds(1).toString(),"reason","Tiene seguimiento")));
  assertThrows(Problem.class,()->run("coordinacion.requerimientos","REQ_CLOSE",req,Map.of("reason","No se cierra el seguimiento sin Caso")));
  run("administrativo","ATTENTION_SEND",req,Map.of("recipient_kind","JEFATURA","scope_id",scope(),"reason","Se necesita un Caso"));
  var opening=Map.<String,Object>of("scope_id",scope(),"responsible_id",user("profesional"),"objective","Seguimiento sintetico","matter","UPR");
  assertThrows(Problem.class,()->run("coordinacion.requerimientos","REQ_CASE_CREATE",req,opening));
  long handoff=n(db.one("SELECT id FROM attention_handoff WHERE requirement_id=?",req),"id");
  run("coordinacion.requerimientos","ATTENTION_DECIDE",req,Map.of("handoff_id",handoff,"decision","RECIBIDA","reason","Recepcion de la ficha"));
  assertThrows(Problem.class,()->run("administrativo","REQ_CASE_CREATE",req,opening));
  long cas=run("coordinacion.requerimientos","REQ_CASE_CREATE",req,opening).id();
  assertEquals(req,n(db.one("SELECT requirement_id FROM case_record WHERE id=?",cas),"requirement_id"));
  assertEquals("CON_CASO",attention.view(access.actor("coordinacion.requerimientos"),db.one("SELECT * FROM requirement WHERE id=?",req)).get("state"));
  assertEquals(0,db.count("SELECT COUNT(*) FROM solicitation s JOIN requirement r ON r.communication_id=s.communication_id WHERE r.id=?",req));
  assertThrows(Problem.class,()->run("coordinacion.requerimientos","ATTENTION_SAVE",req,Map.of("form",form("SEGUIMIENTO"),"reason","Continuar en actuaciones del Caso")));
 }
}
