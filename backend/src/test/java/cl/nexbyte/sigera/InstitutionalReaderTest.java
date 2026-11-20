package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.ByteArrayInputStream;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Consulta institucional (0.5.0, D4, gate G05): lectura de todas las fichas COM, SOL, REQ, CAS, FLUJO y de primera
 * atencion (NNA y versiones), proyeccion del Formulario y documentos no reservados. Lo reservado queda fuera y ningun
 * comando de Workflow.run prospera para un lector puro. Combinada con Profesional conserva exactamente sus facultades.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.datasource.url=jdbc:h2:mem:sigerainstitutionalreader;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "siger.seed-demo=true","siger.data=./target/test-datareader-${random.uuid}","siger.folio-prefix=COMA"})
@ActiveProfiles("test")
class InstitutionalReaderTest {
 @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
 static final String HEAD="coordinacion.requerimientos",PROF="profesional",ADMIN="administrativo",DIST="distribucion",READER="lector.institucional",MIXED="lector.profesional";
 @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired Attention attention;@Autowired Documents documents;@Autowired FormularioReception reception;@Autowired FormularioKeys keys;

 /** Expedientes sinteticos compartidos por las pruebas; ninguna prueba los modifica con exito salvo la combinada (objetos propios). */
 record Fixture(long comNew,long solPending,long solReturned,long solAssumed,long req,long caseId,long reservedNote,long openNote,long task,long flow,long link,
                long closedReq,long reopening,long attention,long reservedReqNote,long openReqNote,long handoff,long external,
                long caseOpenDoc,long caseReservedDoc,long reqOpenDoc,long reqReservedDoc,long designation,long substitution){}
 static Fixture fx;

 long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
 long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
 Long rev(String table,Long id){return id==null||table==null?null:n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
 String ago(long seconds){return Instant.now().minusSeconds(seconds).toString();}
 long run(String who,String type,String table,Long id,Map<String,Object> data){
  db.update("UPDATE staff SET must_change=FALSE WHERE username=?",who);
  return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev(table,id),data)).id();
 }
 Problem refuse(String who,String type,String table,Long id,Map<String,Object> data){
  return assertThrows(Problem.class,()->run(who,type,table,id,data),type);
 }
 long staff(String username,String...roles){
  long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'test-non-login',?,FALSE)",username,username,scope("UGI"));
  for(String role:roles){String[] p=role.split("@");
   db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,?,?)",id,p[0],p.length>1?scope(p[1]):null,0,"PRUEBA AUTOMATICA");}
  return id;
 }
 long com(String subject){return com(ADMIN,subject);}
 long com(String who,String subject){
  return run(who,"COM_CREATE","communication",null,Map.of("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",ago(300),"subject",subject,"body","Relato sintetico"));
 }
 long route(long com){
  run(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",scope("UPR-REQ"),"reason","Clasificacion sintetica"));
  return n(db.one("SELECT id FROM solicitation WHERE communication_id=?",com),"id");
 }
 Map<String,Object> note(String body,boolean reserved){return Map.of("effective_at",ago(60),"body",body,"reserved",reserved,"no_followup",true);}
 long upload(String who,String kind,long target,boolean reserved,String name){
  try{
   db.update("UPDATE staff SET must_change=FALSE WHERE username=?",who);
   long id=n(documents.upload(who,kind,target,reserved,null,null,"INFORME","Documento sintetico",UUID.randomUUID(),
    new MockMultipartFile("file",name,"text/plain",("Contenido sintetico "+name).getBytes(StandardCharsets.UTF_8))),"id");
   db.update("UPDATE document_item SET quarantined=FALSE WHERE id=?",id); // Revision tecnica fuera de esta prueba.
   return id;
  }catch(Exception e){throw new IllegalStateException(e);}
 }
 Map<String,Object> form(String course){
  return Map.of("schema_version","1.0","modality","NNA","motive","Contacto sintetico NNA","course",course,"participation","NO",
   "safety_note","Nota de resguardo sintetica","people",List.of(Map.of("role","NNA","name","Nombre NNA sintetico")));
 }

 synchronized Fixture fixture(){
  if(fx!=null)return fx;
  long upr=scope("UPR-REQ");
  if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",READER)==0){staff(READER,"CONSULTA_INSTITUCIONAL");staff(MIXED,"CONSULTA_INSTITUCIONAL","PROFESIONAL@UPR-REQ");}
  long comNew=com("COM nueva sintetica");
  long solPending=route(com("COM con SOL pendiente"));
  long solReturned=route(com("COM con SOL devuelta"));
  run(HEAD,"SOL_RETURN","solicitation",solReturned,Map.of("reason","Corresponde a otra unidad"));
  // REQ por SOL asumida, con Caso, constancias (una reservada), tarea, documentos y colaboracion pendiente.
  long solAssumed=route(com("COM con REQ y Caso"));
  long req=run(HEAD,"SOL_ASSUME","solicitation",solAssumed,Map.of("responsible_id",user(PROF),"reason","Asumida"));
  long caseId=run(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",upr,"responsible_id",user(PROF),"objective","Seguimiento sintetico","matter","UPR"));
  run(PROF,"CASE_NOTE","case_record",caseId,note("Constancia reservada sintetica",true));
  run(PROF,"CASE_NOTE","case_record",caseId,note("Constancia abierta sintetica",false));
  long reservedNote=n(db.one("SELECT id FROM management_note WHERE case_id=? AND reserved=TRUE",caseId),"id");
  long openNote=n(db.one("SELECT id FROM management_note WHERE case_id=? AND reserved=FALSE",caseId),"id");
  run(PROF,"TASK_CREATE","case_record",caseId,Map.of("executor_id",user(PROF),"title","Llamada sintetica"));
  long task=n(db.one("SELECT id FROM task_item WHERE case_id=?",caseId),"id");
  long caseOpenDoc=upload(PROF,"CAS",caseId,false,"informe-caso.txt"),caseReservedDoc=upload(PROF,"CAS",caseId,true,"reservado-caso.txt");
  run(HEAD,"FLOW_REQUEST","requirement",req,Map.of("kind","COLABORACION","target_scope",scope("UPD"),"reason","Colaboracion sintetica","objective","Apoyo sintetico"));
  long flowId=n(db.one("SELECT id FROM flow_request WHERE requirement_id=?",req),"id");
  long linked=com("COM propuesta como antecedente");
  run(DIST,"COM_LINK","communication",linked,Map.of("folio",s(db.one("SELECT folio FROM requirement WHERE id=?",req),"folio"),"reason","Antecedente sintetico"));
  long link=n(db.one("SELECT id FROM communication_link WHERE communication_id=?",linked),"id");
  // REQ cerrado por su jefatura con un Caso cerrado y una peticion de nueva gestion pendiente.
  long closedReq=run(HEAD,"COM_ASSUME","communication",com(HEAD,"COM de REQ cerrado"),Map.of("responsible_id",user(PROF),"reason","Ingreso propio"));
  run(HEAD,"REQ_CLASSIFY","requirement",closedReq,Map.of("complexity","BAJA","reason","Clasificacion sintetica"));
  long closedCase=run(HEAD,"REQ_CASE_CREATE","requirement",closedReq,Map.of("scope_id",upr,"responsible_id",user(PROF),"objective","Gestion breve","matter","UPR"));
  run(PROF,"CASE_REQUEST_CLOSE","case_record",closedCase,Map.of("reason","Gestion concluida"));
  run(HEAD,"CASE_CLOSE","case_record",closedCase,Map.of("reason","Cierre confirmado"));
  run(HEAD,"REQ_CLOSE","requirement",closedReq,Map.of("reason","Motivo de cierre sintetico"));
  run(PROF,"REOPEN_REQUEST","requirement",closedReq,Map.of("case_id",closedCase,"reason","Nuevos antecedentes"));
  long reopening=n(db.one("SELECT id FROM reopening WHERE requirement_id=?",closedReq),"id");
  // Primera atencion NNA con dos versiones, constancias del REQ (una reservada), documentos del REQ y entrega pendiente.
  long att=run(ADMIN,"ATTENTION_CREATE","requirement",null,Map.of("scope_id",upr,"channel","PRESENCIAL","received_at",ago(600),"form",form("EN_ATENCION")));
  run(ADMIN,"ATTENTION_SAVE","requirement",att,Map.of("form",form("EN_ATENCION"),"reason","Complemento sintetico"));
  run(ADMIN,"REQ_NOTE","requirement",att,note("Constancia de REQ reservada",true));
  run(ADMIN,"REQ_NOTE","requirement",att,note("Constancia de REQ abierta",false));
  long reservedReqNote=n(db.one("SELECT id FROM management_note WHERE requirement_id=? AND case_id IS NULL AND reserved=TRUE",att),"id");
  long openReqNote=n(db.one("SELECT id FROM management_note WHERE requirement_id=? AND case_id IS NULL AND reserved=FALSE",att),"id");
  long reqOpenDoc=upload(HEAD,"REQ",att,false,"oficio-req.txt"),reqReservedDoc=upload(HEAD,"REQ",att,true,"reservado-req.txt");
  run(ADMIN,"ATTENTION_SEND","requirement",att,Map.of("recipient_kind","JEFATURA","scope_id",upr,"reason","Requiere decision de jefatura"));
  long handoff=n(db.one("SELECT id FROM attention_handoff WHERE requirement_id=?",att),"id");
  long external=flow.conserveExternal(ADMIN,upr,FormularioDocs.sign(FormularioDocs.cuVulneration())).id();
  // Subrogancia predesignada y programada (no vigente): objetivo de SUBSTITUTE_ACTIVATE y SUBSTITUTE_END.
  long designation=run("ti","DESIGNATE",null,null,Map.of("scope_id",upr,"titular_id",user(HEAD),"substitute_id",user(PROF),"reason","Resolucion sintetica"));
  long substitution=run("ti","SUBSTITUTE_ACTIVATE",null,designation,Map.of("from_at",Instant.now().plusSeconds(864000).toString(),"until_at",Instant.now().plusSeconds(950400).toString(),"reason","Feriado legal sintetico"));
  fx=new Fixture(comNew,solPending,solReturned,solAssumed,req,caseId,reservedNote,openNote,task,flowId,link,closedReq,reopening,att,reservedReqNote,openReqNote,handoff,external,
   caseOpenDoc,caseReservedDoc,reqOpenDoc,reqReservedDoc,designation,substitution);
  return fx;
 }

 Map<String,Object> detail(String who,String kind,long id){return queries.detail(access.actor(who),kind,id);}
 @SuppressWarnings("unchecked") List<Map<String,Object>> list(Map<String,Object> m,String key){return (List<Map<String,Object>>)m.get(key);}
 List<Long> ids(Map<String,Object> m,String key){return list(m,key).stream().map(r->n(r,"id")).toList();}
 /** Ninguna bandera de facultad (can_*, salvo las de lectura can_read*) es verdadera, en ningun nivel de la ficha. */
 void noFaculties(String where,Object value){
  if(value instanceof Map<?,?> m)m.forEach((k,v)->{
   String key=k.toString();
   if(key.startsWith("can_")&&!key.startsWith("can_read"))assertNotEquals(Boolean.TRUE,v,where+": "+key);
   if(Set.of("attention_scopes","case_scopes").contains(key))assertEquals(List.of(),v,where+": "+key);
   noFaculties(where+"."+key,v);
  });
  else if(value instanceof List<?> l)for(var item:l)noFaculties(where,item);
 }
 long audits(String action,long actor){return db.count("SELECT COUNT(*) FROM audit_event WHERE action=? AND actor_id=?",action,actor);}

 @Test void rolPublicadoEInstitucionalIncompatibleConTi(){
  fixture();
  assertTrue(Roles.json().contains(Map.of("code","CONSULTA_INSTITUCIONAL","reach","INSTITUCIONAL","group","CONSULTA")));
  assertTrue(access.actor(READER).institutionalReader());
  assertFalse(access.actor(PROF).institutionalReader());
  var p=refuse("ti","GRANT_CREATE",null,null,Map.of("user_id",user(READER),"role","TI","from_at",ago(0),"reason","Prueba de incompatibilidad"));
  assertEquals(409,p.status);assertTrue(p.getMessage().startsWith("TI es incompatible"),p.getMessage());
 }

 @Test void lectorLeeTodasLasFichasSinFacultades(){
  var f=fixture();long reader=user(READER),before=audits("CONSULTA",reader);

  var com=detail(READER,"COM",f.comNew());noFaculties("COM",com);
  var external=detail(READER,"COM",f.external());noFaculties("COM externa",external);
  @SuppressWarnings("unchecked") var projection=(Map<String,Object>)external.get("external");
  assertNotNull(projection,"proyeccion del Formulario visible");
  assertFalse(list(projection,"persons").isEmpty());
  assertNotNull(projection.get("original"));

  noFaculties("SOL pendiente",detail(READER,"SOL",f.solPending()));
  var returned=detail(READER,"SOL",f.solReturned());noFaculties("SOL devuelta",returned);
  assertEquals(true,detail(DIST,"SOL",f.solReturned()).get("can_redistribute"),"Distribucion conserva su facultad");
  noFaculties("SOL asumida",detail(READER,"SOL",f.solAssumed()));

  var req=detail(READER,"REQ",f.req());noFaculties("REQ",req);
  assertEquals(List.of(f.caseId()),list(req,"case_rows").stream().filter(r->Boolean.TRUE.equals(r.get("readable"))).map(r->n(r,"id")).toList());
  assertEquals(List.of(f.flow()),ids(req,"flows"));
  assertEquals(List.of(f.link()),ids(req,"links"));
  assertFalse(list(req,"history").isEmpty(),"historial del REQ");
  assertEquals(true,req.get("can_read_com"));
  assertEquals(1,detail(READER,"COM",n(req,"communication_id")).get("requirements") instanceof List<?> l?l.size():0);

  var closed=detail(READER,"REQ",f.closedReq());noFaculties("REQ cerrado",closed);
  assertEquals("Motivo de cierre sintetico",closed.get("close_reason"));
  assertEquals(1,list(closed,"closures").size());
  assertEquals(List.of(f.reopening()),ids(closed,"reopenings"));

  var cas=detail(READER,"CAS",f.caseId());noFaculties("CAS",cas);
  assertEquals(List.of(f.openNote()),ids(cas,"notes"),"constancia reservada del Caso excluida");
  assertEquals(List.of(f.caseOpenDoc()),ids(cas,"documents"),"documento reservado del Caso excluido");
  assertEquals(List.of(f.task()),ids(cas,"tasks"));
  assertTrue(access.caseSourceDocuments(access.actor(READER),db.one("SELECT * FROM case_record WHERE id=?",f.caseId())),"originales de ingreso del Caso");

  noFaculties("FLUJO",detail(READER,"FLUJO",f.flow()));
  assertEquals(10,audits("CONSULTA",reader)-before,"cada ficha consultada queda auditada");
 }

 @Test void fichaNnaVersionesYReservasDelReq(){
  var f=fixture();long reader=user(READER);
  var req=detail(READER,"REQ",f.attention());noFaculties("REQ atencion",req);
  @SuppressWarnings("unchecked") var view=(Map<String,Object>)req.get("attention");
  assertEquals(true,view.get("can_read"));assertEquals(false,view.get("can_edit"));
  assertEquals("NNA",view.get("modality"));
  @SuppressWarnings("unchecked") var form=(Map<String,Object>)view.get("form");
  assertEquals("Nota de resguardo sintetica",form.get("safety_note"),"ficha NNA completa (D4)");
  var versions=list(view,"versions");assertEquals(2,versions.size());
  assertEquals(List.of(f.handoff()),ids(view,"handoffs"));
  long before=audits("FICHA_VERSION_CONSULTADA",reader);
  var old=attention.version(access.actor(READER),f.attention(),n(versions.getLast(),"id"));
  assertNotNull(old.get("form"));
  assertEquals(before+1,audits("FICHA_VERSION_CONSULTADA",reader));

  assertEquals(List.of(f.openReqNote()),ids(req,"notes"),"constancia reservada del REQ excluida");
  assertEquals(List.of(f.reqOpenDoc()),ids(req,"documents"),"documento reservado del REQ excluido");
  var head=detail(HEAD,"REQ",f.attention());
  assertEquals(List.of(f.reservedReqNote(),f.openReqNote()),ids(head,"notes"),"la jefatura principal conserva todas");
  assertEquals(List.of(f.reqOpenDoc(),f.reqReservedDoc()),ids(head,"documents"));
 }

 @Test void descargasAuditadasYReservadasDenegadas()throws Exception{
  var f=fixture();var reader=access.actor(READER);long downloads=audits("DESCARGA",reader.id());
  assertEquals("informe-caso.txt",documents.download(reader,f.caseOpenDoc()).name());
  assertEquals("oficio-req.txt",documents.download(reader,f.reqOpenDoc()).name());
  assertEquals(downloads+2,audits("DESCARGA",reader.id()));
  for(long reserved:List.of(f.caseReservedDoc(),f.reqReservedDoc()))
   assertEquals(403,assertThrows(Problem.class,()->documents.download(reader,reserved)).status,"documento reservado "+reserved);
  // Y no puede cargar documentos donde solo lee.
  assertEquals(403,assertThrows(Problem.class,()->documents.upload(READER,"CAS",f.caseId(),false,null,null,"OTRO","",UUID.randomUUID(),
   new MockMultipartFile("file","x.txt","text/plain","x".getBytes(StandardCharsets.UTF_8)))).status);
  assertEquals(403,assertThrows(Problem.class,()->documents.upload(READER,"REQ",f.attention(),false,null,null,"OTRO","",UUID.randomUUID(),
   new MockMultipartFile("file","x.txt","text/plain","x".getBytes(StandardCharsets.UTF_8)))).status);
 }

 /** Tipos de comando del switch de Workflow.run, leidos del codigo para que un comando nuevo no quede fuera del barrido. */
 static Set<String> workflowCommands()throws Exception{
  String source=Files.readString(Path.of("src/main/java/cl/nexbyte/sigera/Workflow.java"));
  int start=source.indexOf("Result result=switch(type){"),end=source.indexOf("default ->",start);
  assertTrue(start>0&&end>start,"switch de Workflow.run");
  var out=new TreeSet<String>();var m=Pattern.compile("\"([A-Z_]+)\"").matcher(source.substring(start,end));
  while(m.find())out.add(m.group(1));
  return out;
 }

 @Test void ningunComandoProsperaParaUnLectorPuro()throws Exception{
  var f=fixture();long upr=scope("UPR-REQ"),prof=user(PROF);
  Map<String,Object> anyReason=Map.of("reason","Intento de lector");
  record Attempt(String table,Long id,Map<String,Object> data){}
  var attempts=new LinkedHashMap<String,Attempt>();
  attempts.put("ATTENTION_CREATE",new Attempt(null,null,Map.of("scope_id",upr,"channel","PRESENCIAL","received_at",ago(60),"form",form("EN_ATENCION"))));
  attempts.put("ATTENTION_SAVE",new Attempt("requirement",f.attention(),Map.of("form",form("EN_ATENCION"),"reason","Intento")));
  attempts.put("ATTENTION_SEND",new Attempt("requirement",f.attention(),Map.of("recipient_kind","JEFATURA","scope_id",upr,"reason","Intento")));
  attempts.put("ATTENTION_DECIDE",new Attempt("requirement",f.attention(),Map.of("handoff_id",f.handoff(),"decision","RECIBIDA","reason","Intento")));
  attempts.put("ATTENTION_RESOLVE",new Attempt("requirement",f.attention(),Map.of("result","ORIENTACION","reason","Intento","effective_at",ago(10))));
  var newCom=Map.<String,Object>of("scope_id",upr,"channel","TELEFONO","received_at",ago(60),"subject","Intento","body","Intento");
  attempts.put("COM_CREATE",new Attempt(null,null,newCom));
  attempts.put("COM_IMMEDIATE",new Attempt(null,null,newCom));
  attempts.put("COM_ROUTE",new Attempt("communication",f.comNew(),Map.of("scope_id",upr,"reason","Intento")));
  attempts.put("COM_ASSUME",new Attempt("communication",f.comNew(),Map.of("responsible_id",prof,"reason","Intento")));
  attempts.put("COM_LINK",new Attempt("communication",f.comNew(),Map.of("folio",s(db.one("SELECT folio FROM requirement WHERE id=?",f.req()),"folio"),"reason","Intento")));
  var solData=Map.<String,Object>of("scope_id",scope("UPD"),"responsible_id",prof,"disposition","INVALIDA","reason","Intento");
  for(String t:List.of("SOL_ROUTE","SOL_RETURN","SOL_ASSUME","SOL_DISCARD"))attempts.put(t,new Attempt("solicitation",f.solPending(),solData));
  attempts.put("LINK_DECIDE",new Attempt("communication_link",f.link(),Map.of("decision","ADMITIDA","reason","Intento")));
  attempts.put("LINK_RETURN",new Attempt("communication_link",f.link(),anyReason));
  attempts.put("REQ_CLASSIFY",new Attempt("requirement",f.req(),Map.of("complexity","BAJA","reason","Intento")));
  attempts.put("REQ_CASE_CREATE",new Attempt("requirement",f.req(),Map.of("scope_id",upr,"responsible_id",prof,"objective","Intento","matter","UPR")));
  attempts.put("REQ_CLOSE",new Attempt("requirement",f.req(),anyReason));
  attempts.put("REQ_NOTE",new Attempt("requirement",f.attention(),note("Intento",false)));
  attempts.put("FLOW_REQUEST",new Attempt("requirement",f.req(),Map.of("kind","COLABORACION","target_scope",scope("UEP"),"reason","Intento","objective","Intento")));
  attempts.put("FLOW_DECIDE",new Attempt("flow_request",f.flow(),Map.of("decision","ACEPTADA","responsible_id",prof,"reason","Intento")));
  attempts.put("REOPEN_REQUEST",new Attempt("requirement",f.closedReq(),anyReason));
  attempts.put("REOPEN_AUTHORIZE",new Attempt("reopening",f.reopening(),anyReason));
  attempts.put("REOPEN_DECIDE",new Attempt("reopening",f.reopening(),Map.of("decision","ACEPTADA","reason","Intento")));
  var caseData=Map.<String,Object>of("reason","Intento","responsible_id",prof,"user_id",prof,"matter","UPD","effective_at",ago(10),"body","Intento","until_at",Instant.now().plusSeconds(86400).toString());
  for(String t:List.of("CASE_NOTE","CASE_ASSIGN","CASE_ACK","CASE_MEMBER_ADD","CASE_MEMBER_REMOVE","CASE_REQUEST_CLOSE","CASE_WITHDRAW_CLOSE","CASE_CLOSE","CASE_RETURN","CASE_WAIT","CASE_RESUME","CASE_MATTER"))
   attempts.put(t,new Attempt("case_record",f.caseId(),caseData));
  attempts.put("NOTE_RECTIFY",new Attempt(null,f.openNote(),Map.of("effective_at",ago(10),"body","Correccion","reason","Intento")));
  attempts.put("TASK_CREATE",new Attempt("case_record",f.caseId(),Map.of("executor_id",prof,"title","Intento")));
  var taskData=Map.<String,Object>of("title","Intento","reason","Intento");
  for(String t:List.of("TASK_EDIT","TASK_COMPLETE","TASK_CANCEL","TASK_ATTEMPT"))attempts.put(t,new Attempt("task_item",f.task(),taskData));
  attempts.put("STAFF_CREATE",new Attempt(null,null,Map.of("username","intento.lector","name","Intento","scope_id",upr,"password","Clave-sintetica-2026!","reason","Intento")));
  attempts.put("STAFF_UPDATE",new Attempt(null,prof,Map.of("name","Intento","enabled",true,"reason","Intento")));
  attempts.put("STAFF_RESET",new Attempt(null,prof,Map.of("password","Clave-sintetica-2026!","reason","Intento")));
  attempts.put("GRANT_CREATE",new Attempt(null,null,Map.of("user_id",prof,"role","LECTURA","scope_id",upr,"from_at",ago(0),"reason","Intento")));
  attempts.put("GRANT_REVOKE",new Attempt(null,n(db.one("SELECT MIN(id) AS id FROM role_assignment WHERE user_id=? AND revoked_at IS NULL",prof),"id"),anyReason));
  attempts.put("PROFILE_SET",new Attempt(null,prof,Map.of("grants",List.of(),"reason","Intento")));
  attempts.put("SCOPE_CREATE",new Attempt(null,null,Map.of("unit_code","UPR","code","UPR-INTENTO","name","Intento","site_code","CENTRAL","reason","Intento")));
  attempts.put("DESIGNATE",new Attempt(null,null,Map.of("scope_id",upr,"titular_id",user(HEAD),"substitute_id",prof,"reason","Intento")));
  attempts.put("SUBSTITUTE_ACTIVATE",new Attempt(null,f.designation(),Map.of("from_at",ago(0),"until_at",Instant.now().plusSeconds(3600).toString(),"reason","Intento")));
  attempts.put("SUBSTITUTE_END",new Attempt("substitution",f.substitution(),anyReason));
  attempts.put("DOC_RELEASE",new Attempt(null,f.caseOpenDoc(),Map.of("reason","Intento","synthetic_review",true)));
  attempts.put("INTEGRATION_PAUSE",new Attempt(null,1L,anyReason));
  attempts.put("INTEGRATION_RESUME",new Attempt(null,1L,anyReason));
  attempts.put("MAIL_RESOLVE",new Attempt(null,1L,Map.of("decision","DESCARTAR","reason","Intento")));

  // NOTICE_READ marca como leido un aviso propio (no es gestion); sobre un aviso ajeno no cambia nada.
  var expected=new TreeSet<>(workflowCommands());expected.remove("NOTICE_READ");
  assertEquals(expected,new TreeSet<>(attempts.keySet()),"el barrido cubre todos los comandos de Workflow.run");

  var outcomes=new TreeMap<String,Object>();
  db.update("UPDATE staff SET must_change=FALSE WHERE username=?",READER);
  var changes=new TreeMap<String,Object>();
  for(var e:attempts.entrySet()){
   var a=e.getValue();var before=snapshot();
   try{flow.run(READER,new Workflow.Command(UUID.randomUUID(),e.getKey(),a.id(),rev(a.table(),a.id()),a.data()));outcomes.put(e.getKey(),"EXITO");}
   catch(Problem p){if(p.status!=403)outcomes.put(e.getKey(),p.status+" "+p.getMessage());}
   var after=snapshot();
   if(!after.equals(before)){
    var diff=new TreeMap<String,String>();
    for(var k:before.keySet())if(!Objects.equals(before.get(k),after.get(k)))diff.put(k,before.get(k)+" -> "+after.get(k));
    changes.put(e.getKey(),diff);
   }
  }
  assertEquals(Map.of(),outcomes,"todo comando de un lector puro termina en 403");
  assertEquals(Map.of(),changes,"ningun intento del lector deja rastro en la base (filas, revisiones ni auditoria)");

  db.update("UPDATE staff SET must_change=FALSE WHERE username=?",READER);
  long notice=db.insert("INSERT INTO inbox_notice(user_id,at_ms,text_value,resource_type,resource_id) VALUES(?,?,?,?,?)",prof,System.currentTimeMillis(),"Aviso sintetico","CAS",f.caseId());
  run(READER,"NOTICE_READ",null,notice,Map.of());
  assertNull(db.one("SELECT read_ms FROM inbox_notice WHERE id=?",notice).get("read_ms"),"el aviso ajeno sigue sin leer");
  // Control: la instantanea si detecta un cambio real (el lector marca como leido un aviso propio).
  long own=db.insert("INSERT INTO inbox_notice(user_id,at_ms,text_value,resource_type,resource_id) VALUES(?,?,?,?,?)",user(READER),System.currentTimeMillis(),"Aviso propio sintetico","CAS",f.caseId());
  var beforeOwn=snapshot();
  run(READER,"NOTICE_READ",null,own,Map.of());
  var afterOwn=snapshot();
  assertEquals(beforeOwn.get("inbox_notice.leidos")+1,afterOwn.get("inbox_notice.leidos"));
  assertEquals(beforeOwn.get("command_log.filas")+1,afterOwn.get("command_log.filas"),"el exito queda en command_log; los rechazos no");
 }

 /**
  * Estado comparable de toda la base: filas de cada tabla, suma de revision donde existe, version de seguridad de las
  * cuentas, funciones revocadas y avisos leidos. Un intento rechazado debe dejarlo identico (incluido audit_event).
  */
 Map<String,Long> snapshot(){
  var out=new TreeMap<String,Long>();
  var tables=db.rows("SELECT LOWER(table_name) AS t FROM information_schema.tables WHERE LOWER(table_schema)='public' AND table_type='BASE TABLE' ORDER BY 1");
  assertTrue(tables.size()>40,"tablas del esquema: "+tables.size());
  for(var row:tables){
   String table=s(row,"t");out.put(table+".filas",db.count("SELECT COUNT(*) FROM "+table));
   if(db.count("SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_schema)='public' AND LOWER(table_name)=? AND LOWER(column_name)='revision'",table)>0)
    out.put(table+".revision",db.count("SELECT COALESCE(SUM(revision),0) FROM "+table));
  }
  out.put("staff.security_version",db.count("SELECT COALESCE(SUM(security_version),0) FROM staff"));
  out.put("role_assignment.revocadas",db.count("SELECT COUNT(*) FROM role_assignment WHERE revoked_at IS NOT NULL"));
  out.put("inbox_notice.leidos",db.count("SELECT COUNT(*) FROM inbox_notice WHERE read_ms IS NOT NULL"));
  out.put("document_item.cuarentena",db.count("SELECT COUNT(*) FROM document_item WHERE quarantined=TRUE"));
  return out;
 }

 /** Copia del vector de FormularioConsumerTest: adulto ClaveUnica (canal publico, 1.1) con un original adjunto. */
 static String adultWithFile(byte[] content){
  var c=FormularioDocs.cuVulneration();c.put("version","1.1");
  String id=UUID.randomUUID().toString();
  c.withObjectProperty("datos").withArrayProperty("adjuntos").add(id);
  var d=c.withArrayProperty("adjuntos").addObject();
  d.put("id",id);d.put("nombre","informe ciudadano.pdf");d.put("tamanio",content.length);d.put("sha256",ExternalOriginal.sha256(content));d.put("tipo","application/pdf");d.put("estado","VALIDADO");
  return FormularioDocs.sign(c);
 }

 @Test void lectorDescargaElOriginalDeUnaComDelFormulario()throws Exception{
  fixture();long upr=scope("UPR-REQ");var reader=access.actor(READER);
  // Mismo camino que el consumidor: custodia del original, registro de la entrega y comprobante firmado.
  byte[] content="Original sintetico del Formulario para la consulta institucional.\n".getBytes(StandardCharsets.US_ASCII);
  var original=ExternalOriginal.parse(adultWithFile(content),keys.keys());
  var file=original.attachments().getFirst();
  documents.custodyExternal(Documents.externalKey(original.externalId(),file.id()),file.size(),file.sha256(),new ByteArrayInputStream(content));
  var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  long com=reception.register(original,upr,UUID.randomUUID(),System.currentTimeMillis()+60000,1,new FormularioAck("SIGER_A_PRUEBA",pair.getPrivate(),pair.getPublic())).communication();
  long doc=n(db.one("SELECT x.document_id FROM external_document x WHERE x.communication_id=?",com),"document_id");

  var view=detail(READER,"COM",com);noFaculties("COM del Formulario",view);
  assertEquals(List.of(doc),ids(view,"documents"),"el original figura en la ficha");
  assertNotNull(view.get("external"));
  assertEquals(409,assertThrows(Problem.class,()->documents.download(reader,doc)).status,"en cuarentena no se descarga");

  db.update("UPDATE document_item SET quarantined=FALSE WHERE id=?",doc); // Liberacion de TI fuera de esta prueba (DocumentReleaseTest).
  long before=db.count("SELECT COUNT(*) FROM audit_event WHERE action='DESCARGA' AND actor_id=? AND resource_type='COM' AND resource_id=? AND note=?",reader.id(),com,"Documento "+doc);
  var download=documents.download(reader,doc);
  assertEquals("informe ciudadano.pdf",download.name());
  assertEquals(content.length,download.size());
  assertArrayEquals(content,Files.readAllBytes(download.path()));
  assertEquals(before+1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='DESCARGA' AND actor_id=? AND resource_type='COM' AND resource_id=? AND note=?",reader.id(),com,"Documento "+doc),
   "la descarga queda auditada con la COM y el documento");
 }

 @Test void lectorConProfesionalConservaExactamenteSusFacultades(){
  var f=fixture();long upr=scope("UPR-REQ"),mixed=user(MIXED);
  // REQ propio de esta prueba: no altera los expedientes compartidos.
  long ownReq=run(HEAD,"COM_ASSUME","communication",com(HEAD,"COM del lector profesional"),Map.of("responsible_id",user(PROF),"reason","Ingreso propio"));
  long own=run(HEAD,"REQ_CASE_CREATE","requirement",ownReq,Map.of("scope_id",upr,"responsible_id",mixed,"objective","Caso del lector profesional","matter","UPR"));
  run(MIXED,"CASE_NOTE","case_record",own,note("Reservada propia",true));
  long ownReserved=n(db.one("SELECT id FROM management_note WHERE case_id=? AND reserved=TRUE",own),"id");
  var keys=List.of("can_write","can_head","is_owner","own_substitution","can_close","closure_blockers","can_request_close","close_request_blockers");
  var withReader=new HashMap<String,Map<String,Object>>();
  for(long c:List.of(own,f.caseId())){var v=detail(MIXED,"CAS",c);var m=new LinkedHashMap<String,Object>();for(var k:keys)m.put(k,v.get(k));m.put("notes",ids(v,"notes"));m.put("documents",ids(v,"documents"));withReader.put(""+c,m);}
  var reqWithReader=detail(MIXED,"REQ",ownReq);

  // Lectura extra: el Caso ajeno se lee, pero sin escritura ni reservadas.
  assertEquals(false,withReader.get(""+f.caseId()).get("can_write"));
  assertEquals(List.of(f.openNote()),withReader.get(""+f.caseId()).get("notes"));
  assertEquals(List.of(f.caseOpenDoc()),withReader.get(""+f.caseId()).get("documents"));
  assertEquals(true,withReader.get(""+own).get("can_write"));
  assertEquals(List.of(ownReserved),withReader.get(""+own).get("notes"),"su reservada la ve como responsable");
  assertEquals(403,refuse(MIXED,"CASE_NOTE","case_record",f.caseId(),note("Intento",false)).status);
  assertEquals(403,refuse(MIXED,"REQ_CLASSIFY","requirement",f.req(),Map.of("complexity","BAJA","reason","Intento")).status);

  // Sin la consulta institucional, sus facultades sobre su propio Caso son identicas.
  db.update("UPDATE role_assignment SET revoked_at=? WHERE user_id=? AND role_code='CONSULTA_INSTITUCIONAL'",System.currentTimeMillis(),mixed);
  try{
   var v=detail(MIXED,"CAS",own);var m=new LinkedHashMap<String,Object>();for(var k:keys)m.put(k,v.get(k));m.put("notes",ids(v,"notes"));m.put("documents",ids(v,"documents"));
   assertEquals(withReader.get(""+own),m);
   assertEquals(403,assertThrows(Problem.class,()->detail(MIXED,"CAS",f.caseId())).status,"sin la consulta no lee el Caso ajeno");
   var req=detail(MIXED,"REQ",ownReq);
   for(var k:List.of("can_head","can_attention","can_close"))assertEquals(req.get(k),reqWithReader.get(k),k);
   run(MIXED,"CASE_NOTE","case_record",own,note("Sigue gestionando",false));
  }finally{
   db.update("UPDATE role_assignment SET revoked_at=NULL WHERE user_id=? AND role_code='CONSULTA_INSTITUCIONAL'",mixed);
  }
 }
}
