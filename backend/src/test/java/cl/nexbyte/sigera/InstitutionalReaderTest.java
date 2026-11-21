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
 * G06: las bandejas (BoardQueries.Visibility) coinciden con Access en todas ellas, Inicio tiene su seccion institucional
 * y la ficha avisa cuando se lee solo por la consulta.
 * R02 (D9): las Direcciones nacionales leen por materia lo de las sedes (segundo fixture en REG-DEMO, materia UPD), con
 * la misma equivalencia de bandejas, el mismo barrido sin efecto y los reservados excluidos; un Caso de su materia no
 * abre la COM, sus originales ni la ficha de primera atencion.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.datasource.url=jdbc:h2:mem:sigerainstitutionalreader;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "siger.seed-demo=true","siger.data=./target/test-datareader-${random.uuid}","siger.folio-prefix=COMA"})
@ActiveProfiles("test")
class InstitutionalReaderTest {
 @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
 static final String HEAD="coordinacion.requerimientos",PROF="profesional",ADMIN="administrativo",DIST="distribucion",READER="lector.institucional",MIXED="lector.profesional";
 @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired Attention attention;@Autowired Documents documents;@Autowired FormularioReception reception;@Autowired FormularioKeys keys;@Autowired BoardQueries boards;

 /** Expedientes sinteticos compartidos por las pruebas; ninguna prueba los modifica con exito salvo la combinada (objetos propios). */
 record Fixture(long comNew,long solPending,long solReturned,long solAssumed,long req,long caseId,long reservedNote,long openNote,long task,long flow,long link,
                long closedReq,long reopening,long attention,long reservedReqNote,long openReqNote,long handoff,long external,
                long caseOpenDoc,long caseReservedDoc,long reqOpenDoc,long reqReservedDoc,long designation,long substitution){}
 static Fixture fx;
 /** Donde se arma un fixture: el nivel central (UPR-REQ) o una sede con materia sugerida (R02). */
 record Site(String scope,String head,String prof,String admin,String dist,String caseMatter,String matter,String flowTarget){}
 static final String COORD="coordinacion.regional",PROF_REG="profesional.regional",DIR_UPR="direccion.upr",DIR_UPD="direccion.upd",DIR_UEP="direccion.uep";
 static final Site CENTRAL=new Site("UPR-REQ",HEAD,PROF,ADMIN,DIST,"UPR",null,"UPD");
 static final Site REGIONAL=new Site("REG-DEMO",COORD,PROF_REG,PROF_REG,COORD,"UPD","UPD","UEP");
 static Fixture rx;

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
 long com(String who,String subject){return com(CENTRAL,who,subject);}
 long com(Site x,String who,String subject){
  return run(who,"COM_CREATE","communication",null,Map.of("scope_id",scope(x.scope()),"channel","TELEFONO","received_at",ago(300),"subject",subject,"body","Relato sintetico"));
 }
 long route(long com){return route(CENTRAL,com);}
 /** SOL al ambito del fixture; en una sede, con su materia sugerida. */
 long route(Site x,long com){
  var data=new HashMap<String,Object>(Map.of("scope_id",scope(x.scope()),"reason","Clasificacion sintetica"));
  if(x.matter()!=null)data.put("suggested_matter",x.matter());
  run(x.dist(),"COM_ROUTE","communication",com,data);
  return n(db.one("SELECT id FROM solicitation WHERE communication_id=?",com),"id");
 }
 /** En una sede, la Coordinacion registra la materia de un REQ nacido sin SOL o por asuncion propia (REQ_MATTER). */
 void matter(Site x,long req){if(x.matter()!=null)run(x.head(),"REQ_MATTER","requirement",req,Map.of("matter",x.matter(),"reason","Materia de la sede"));}
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
  if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",READER)==0){staff(READER,"CONSULTA_INSTITUCIONAL");staff(MIXED,"CONSULTA_INSTITUCIONAL","PROFESIONAL@UPR-REQ");}
  fx=build(CENTRAL);
  return fx;
 }
 /** Mismos expedientes en REG-DEMO con materia UPD: la Coordinacion regional los gestiona y la Direccion UPD los lee (R02). */
 synchronized Fixture regional(){
  fixture();
  if(rx==null)rx=build(REGIONAL);
  return rx;
 }
 Fixture build(Site x){
  long site=scope(x.scope());String head=x.head(),prof=x.prof(),admin=x.admin(),dist=x.dist();
  long comNew=com(x,admin,"COM nueva sintetica");
  long solPending=route(x,com(x,admin,"COM con SOL pendiente"));
  long solReturned=route(x,com(x,admin,"COM con SOL devuelta"));
  run(head,"SOL_RETURN","solicitation",solReturned,Map.of("reason","Corresponde a otra unidad"));
  // REQ por SOL asumida, con Caso, constancias (una reservada), tarea, documentos y colaboracion pendiente.
  long solAssumed=route(x,com(x,admin,"COM con REQ y Caso"));
  long req=run(head,"SOL_ASSUME","solicitation",solAssumed,Map.of("responsible_id",user(prof),"reason","Asumida"));
  long caseId=run(head,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",site,"responsible_id",user(prof),"objective","Seguimiento sintetico","matter",x.caseMatter()));
  run(prof,"CASE_NOTE","case_record",caseId,note("Constancia reservada sintetica",true));
  run(prof,"CASE_NOTE","case_record",caseId,note("Constancia abierta sintetica",false));
  long reservedNote=n(db.one("SELECT id FROM management_note WHERE case_id=? AND reserved=TRUE",caseId),"id");
  long openNote=n(db.one("SELECT id FROM management_note WHERE case_id=? AND reserved=FALSE",caseId),"id");
  run(prof,"TASK_CREATE","case_record",caseId,Map.of("executor_id",user(prof),"title","Llamada sintetica"));
  long task=n(db.one("SELECT id FROM task_item WHERE case_id=?",caseId),"id");
  long caseOpenDoc=upload(prof,"CAS",caseId,false,"informe-caso.txt"),caseReservedDoc=upload(prof,"CAS",caseId,true,"reservado-caso.txt");
  run(head,"FLOW_REQUEST","requirement",req,Map.of("kind","COLABORACION","target_scope",scope(x.flowTarget()),"reason","Colaboracion sintetica","objective","Apoyo sintetico"));
  long flowId=n(db.one("SELECT id FROM flow_request WHERE requirement_id=?",req),"id");
  long linked=com(x,admin,"COM propuesta como antecedente");
  run(dist,"COM_LINK","communication",linked,Map.of("folio",s(db.one("SELECT folio FROM requirement WHERE id=?",req),"folio"),"reason","Antecedente sintetico"));
  long link=n(db.one("SELECT id FROM communication_link WHERE communication_id=?",linked),"id");
  // REQ cerrado por su jefatura con un Caso cerrado y una peticion de nueva gestion pendiente.
  long closedReq=run(head,"COM_ASSUME","communication",com(x,head,"COM de REQ cerrado"),Map.of("responsible_id",user(prof),"reason","Ingreso propio"));
  matter(x,closedReq);
  run(head,"REQ_CLASSIFY","requirement",closedReq,Map.of("complexity","BAJA","reason","Clasificacion sintetica"));
  long closedCase=run(head,"REQ_CASE_CREATE","requirement",closedReq,Map.of("scope_id",site,"responsible_id",user(prof),"objective","Gestion breve","matter",x.caseMatter()));
  run(prof,"CASE_REQUEST_CLOSE","case_record",closedCase,Map.of("reason","Gestion concluida"));
  run(head,"CASE_CLOSE","case_record",closedCase,Map.of("reason","Cierre confirmado"));
  run(head,"REQ_CLOSE","requirement",closedReq,Map.of("reason","Motivo de cierre sintetico"));
  run(prof,"REOPEN_REQUEST","requirement",closedReq,Map.of("case_id",closedCase,"reason","Nuevos antecedentes"));
  long reopening=n(db.one("SELECT id FROM reopening WHERE requirement_id=?",closedReq),"id");
  // Primera atencion NNA con dos versiones, constancias del REQ (una reservada), documentos del REQ y entrega pendiente.
  long att=run(admin,"ATTENTION_CREATE","requirement",null,Map.of("scope_id",site,"channel","PRESENCIAL","received_at",ago(600),"form",form("EN_ATENCION")));
  matter(x,att);
  run(admin,"ATTENTION_SAVE","requirement",att,Map.of("form",form("EN_ATENCION"),"reason","Complemento sintetico"));
  run(admin,"REQ_NOTE","requirement",att,note("Constancia de REQ reservada",true));
  run(admin,"REQ_NOTE","requirement",att,note("Constancia de REQ abierta",false));
  long reservedReqNote=n(db.one("SELECT id FROM management_note WHERE requirement_id=? AND case_id IS NULL AND reserved=TRUE",att),"id");
  long openReqNote=n(db.one("SELECT id FROM management_note WHERE requirement_id=? AND case_id IS NULL AND reserved=FALSE",att),"id");
  long reqOpenDoc=upload(head,"REQ",att,false,"oficio-req.txt"),reqReservedDoc=upload(head,"REQ",att,true,"reservado-req.txt");
  run(admin,"ATTENTION_SEND","requirement",att,Map.of("recipient_kind","JEFATURA","scope_id",site,"reason","Requiere decision de jefatura"));
  long handoff=n(db.one("SELECT id FROM attention_handoff WHERE requirement_id=?",att),"id");
  if(x!=CENTRAL)return new Fixture(comNew,solPending,solReturned,solAssumed,req,caseId,reservedNote,openNote,task,flowId,link,closedReq,reopening,att,reservedReqNote,openReqNote,handoff,fx.external(),
   caseOpenDoc,caseReservedDoc,reqOpenDoc,reqReservedDoc,fx.designation(),fx.substitution());
  long external=flow.conserveExternal(ADMIN,site,FormularioDocs.sign(FormularioDocs.cuVulneration())).id();
  // Subrogancia predesignada y programada (no vigente): objetivo de SUBSTITUTE_ACTIVATE y SUBSTITUTE_END.
  long designation=run("ti","DESIGNATE",null,null,Map.of("scope_id",site,"titular_id",user(HEAD),"substitute_id",user(PROF),"reason","Resolucion sintetica"));
  long substitution=run("ti","SUBSTITUTE_ACTIVATE",null,designation,Map.of("from_at",Instant.now().plusSeconds(864000).toString(),"until_at",Instant.now().plusSeconds(950400).toString(),"reason","Feriado legal sintetico"));
  return new Fixture(comNew,solPending,solReturned,solAssumed,req,caseId,reservedNote,openNote,task,flowId,link,closedReq,reopening,att,reservedReqNote,openReqNote,handoff,external,
   caseOpenDoc,caseReservedDoc,reqOpenDoc,reqReservedDoc,designation,substitution);
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

 record Attempt(String table,Long id,Map<String,Object> data){}
 /** Un intento por comando de Workflow.run sobre los expedientes del fixture, con revision vigente y datos validos. */
 Map<String,Attempt> attempts(Fixture f,Site x){
  long upr=scope(x.scope()),prof=user(x.prof());
  Map<String,Object> anyReason=Map.of("reason","Intento de lector");
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
  attempts.put("REQ_MATTER",new Attempt("requirement",f.req(),Map.of("matter","UPR","reason","Intento")));
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
  attempts.put("SCOPE_UPDATE",new Attempt("org_scope",upr,Map.of("name","Intento","reason","Intento")));
  attempts.put("SCOPE_ACTIVATE",new Attempt("org_scope",upr,anyReason));
  attempts.put("SCOPE_CLOSE",new Attempt("org_scope",upr,anyReason));
  attempts.put("DESIGNATE",new Attempt(null,null,Map.of("scope_id",upr,"titular_id",user(HEAD),"substitute_id",prof,"reason","Intento")));
  attempts.put("SUBSTITUTE_ACTIVATE",new Attempt(null,f.designation(),Map.of("from_at",ago(0),"until_at",Instant.now().plusSeconds(3600).toString(),"reason","Intento")));
  attempts.put("SUBSTITUTE_END",new Attempt("substitution",f.substitution(),anyReason));
  attempts.put("DOC_RELEASE",new Attempt(null,f.caseOpenDoc(),Map.of("reason","Intento","synthetic_review",true)));
  attempts.put("INTEGRATION_PAUSE",new Attempt(null,1L,anyReason));
  attempts.put("INTEGRATION_RESUME",new Attempt(null,1L,anyReason));
  attempts.put("MAIL_RESOLVE",new Attempt(null,1L,Map.of("decision","DESCARTAR","reason","Intento")));

  return attempts;
 }
 /** Cada intento termina en 403 y deja la base identica (filas, revisiones y auditoria). */
 void sweep(String who,Map<String,Attempt> attempts)throws Exception{
  // NOTICE_READ marca como leido un aviso propio (no es gestion); sobre un aviso ajeno no cambia nada.
  var expected=new TreeSet<>(workflowCommands());expected.remove("NOTICE_READ");
  assertEquals(expected,new TreeSet<>(attempts.keySet()),"el barrido cubre todos los comandos de Workflow.run");

  var outcomes=new TreeMap<String,Object>();
  db.update("UPDATE staff SET must_change=FALSE WHERE username=?",who);
  var changes=new TreeMap<String,Object>();
  for(var e:attempts.entrySet()){
   var a=e.getValue();var before=snapshot();
   try{flow.run(who,new Workflow.Command(UUID.randomUUID(),e.getKey(),a.id(),rev(a.table(),a.id()),a.data()));outcomes.put(e.getKey(),"EXITO");}
   catch(Problem p){if(p.status!=403)outcomes.put(e.getKey(),p.status+" "+p.getMessage());}
   var after=snapshot();
   if(!after.equals(before)){
    var diff=new TreeMap<String,String>();
    for(var k:before.keySet())if(!Objects.equals(before.get(k),after.get(k)))diff.put(k,before.get(k)+" -> "+after.get(k));
    changes.put(e.getKey(),diff);
   }
  }
  assertEquals(Map.of(),outcomes,who+": todo comando termina en 403");
  assertEquals(Map.of(),changes,who+": ningun intento deja rastro en la base (filas, revisiones ni auditoria)");
 }

 @Test void ningunComandoProsperaParaUnLectorPuro()throws Exception{
  var f=fixture();long prof=user(PROF);
  sweep(READER,attempts(f,CENTRAL));

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
 // --- G06: bandejas, Inicio y aviso en la ficha ---------------------------------------------------------------------
 static final String HEAD_READER="lector.direccion";
 /** Direccion UPD con la consulta institucional y una SOL pendiente propia (ademas de las de UPR-REQ del fixture). */
 long headReader(){
  if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",HEAD_READER)==0){
   staff(HEAD_READER,"CONSULTA_INSTITUCIONAL","DIRECCION@UPD");
   run(DIST,"COM_ROUTE","communication",com("COM para Direccion UPD"),Map.of("scope_id",scope("UPD"),"reason","Competencia UPD"));
  }
  return user(HEAD_READER);
 }
 /** Todos los ids de una bandeja con el filtro dado, recorriendo todas sus paginas. */
 Set<Long> board(Access.Actor a,String kind,String filter){
  var out=new HashSet<Long>();
  for(int p=0;;p++){
   var page=boards.page(a,kind,p,100,"",filter,null);var items=list(page,"items");
   items.forEach(r->assertTrue(out.add(n(r,"id")),kind+": fila repetida"));
   if(items.isEmpty()||out.size()>=n(page,"total"))return out;
  }
 }
 Set<Long> where(String sql,java.util.function.Predicate<Map<String,Object>> visible){
  var out=new HashSet<Long>();for(var r:db.rows(sql))if(visible.test(r))out.add(n(r,"id"));return out;
 }
 /** Lo que Access (o Queries, para SOL y flujos) deja leer, por bandeja. Las SOL asumidas o descartadas solo estan en la bandeja del lector y en la de la Direccion de su materia (R02). */
 Map<String,Set<Long>> expected(Access.Actor a){
  var out=new LinkedHashMap<String,Set<Long>>();
  out.put("com",where("SELECT * FROM communication",c->access.comRead(a,c)));
  out.put("sol",where("SELECT * FROM solicitation",x->queries.solVisible(a,x)&&(a.institutionalReader()||access.matterSol(a,x)||Set.of("PENDIENTE","DEVUELTA").contains(s(x,"state")))));
  out.put("req",where("SELECT * FROM requirement",r->access.reqRead(a,r)));
  out.put("cas",where("SELECT * FROM case_record",c->access.caseRead(a,c)));
  out.put("flujos",where("SELECT * FROM flow_request",f->queries.flowVisible(a,f)));
  out.put("atenciones",where("SELECT r.* FROM requirement r WHERE EXISTS(SELECT 1 FROM attention_record t WHERE t.requirement_id=r.id)",r->access.reqRead(a,r)));
  out.put("tareas",where("SELECT * FROM task_item WHERE executor_id="+a.id(),t->access.caseRead(a,db.one("SELECT * FROM case_record WHERE id=?",n(t,"case_id")))));
  out.put("avisos",where("SELECT * FROM inbox_notice WHERE user_id="+a.id(),x->true));
  return out;
 }

 @Test void bandejasEquivalentesAAccess(){
  fixture();headReader();var x=regional();
  for(String who:List.of(READER,MIXED,HEAD_READER,HEAD,PROF,ADMIN,DIST,"gabinete",DIR_UPR,DIR_UPD,DIR_UEP,COORD,PROF_REG,"ti")){
   var a=access.actor(who);
   expected(a).forEach((kind,ids)->assertEquals(ids,board(a,kind,"all"),who+" / "+kind));
   // G08a: predicados SQL del buscador = Attention.mayRead, Queries.solVisible y la regla de coincidencias visibles.
   var v=boards.visibility(a);
   assertEquals(where("SELECT r.* FROM requirement r WHERE EXISTS(SELECT 1 FROM attention_record t WHERE t.requirement_id=r.id)",r->attention.mayRead(a,r)),
    where("SELECT r.id FROM requirement r WHERE EXISTS(SELECT 1 FROM attention_record t WHERE t.requirement_id=r.id) AND "+v.attentionRead("r"),r->true),who+" / ficha");
   assertEquals(where("SELECT * FROM solicitation",q->queries.solVisible(a,q)),where("SELECT s.id FROM solicitation s JOIN communication c ON c.id=s.communication_id WHERE "+v.solChain("s","c"),q->true),who+" / SOL de la cadena");
   assertEquals(where("SELECT * FROM person_reference",p->access.comRead(a,db.one("SELECT * FROM communication WHERE id=?",n(p,"communication_id")))
     ||s(p,"source").equals("FICHA")&&attention.mayRead(a,db.one("SELECT * FROM requirement WHERE id=?",n(p,"requirement_id")))),
    where("SELECT p.id FROM "+PersonSearch.REFERENCES+" WHERE "+PersonSearch.visible(v),q->true),who+" / coincidencias");
  }
  assertTrue(db.count("SELECT COUNT(*) FROM person_reference WHERE source='FICHA'")>0&&db.count("SELECT COUNT(*) FROM person_reference WHERE source='FORMULARIO'")>0,"el fixture tiene coincidencias de ficha y del Formulario");
  // El lector ve las bandejas de expedientes completas; tareas y avisos siguen siendo personales.
  var reader=access.actor(READER);
  for(var e:Map.of("com","communication","sol","solicitation","req","requirement","cas","case_record","flujos","flow_request").entrySet())
   assertEquals(db.count("SELECT COUNT(*) FROM "+e.getValue()),board(reader,e.getKey(),"all").size(),e.getKey());
  assertTrue(board(reader,"sol","all").size()>board(reader,"sol","pending").size()+board(reader,"sol","returned").size(),"incluye SOL asumidas");
  assertEquals(Set.of(),board(reader,"tareas","all"));
  // R02: la Direccion UPD ve en sus bandejas la cadena regional de su materia; UPR y UEP no (UEP solo el flujo que recibe).
  var upd=access.actor(DIR_UPD);long com=n(db.one("SELECT communication_id FROM requirement WHERE id=?",x.req()),"communication_id");
  assertTrue(board(upd,"com","all").contains(com),"COM de la cadena");
  assertTrue(board(upd,"sol","all").containsAll(List.of(x.solPending(),x.solReturned(),x.solAssumed())),"SOL de su materia en todo estado");
  assertTrue(board(upd,"req","all").containsAll(List.of(x.req(),x.closedReq(),x.attention())));
  assertTrue(board(upd,"cas","all").contains(x.caseId()));
  assertTrue(board(upd,"flujos","all").contains(x.flow()));
  assertTrue(board(upd,"atenciones","all").contains(x.attention()));
  assertFalse(board(upd,"sol","pending").contains(x.solPending()),"SOL por asumir: solo las de su recepcion");
  for(String other:List.of(DIR_UPR,DIR_UEP)){
   var a=access.actor(other);
   for(var e:Map.of("com",com,"sol",x.solAssumed(),"req",x.req(),"cas",x.caseId()).entrySet())assertFalse(board(a,e.getKey(),"all").contains(e.getValue()),other+" / "+e.getKey());
  }
  assertTrue(board(access.actor(DIR_UEP),"flujos","all").contains(x.flow()),"la Direccion UEP recibe la colaboracion");
 }

 @SuppressWarnings("unchecked") Map<String,Map<String,Object>> sections(Access.Actor a){
  var out=new LinkedHashMap<String,Map<String,Object>>();for(var x:(List<Map<String,Object>>)boards.dashboard(a).get("sections"))out.put(s(x,"id"),x);return out;
 }
 Map<String,Long> counts(Map<String,Object> section){
  var out=new LinkedHashMap<String,Long>();for(var m:list(section,"metrics"))out.put(s(m,"route")+":"+s(m,"filter"),n(m,"count"));return out;
 }

 @Test void inicioDelLectorYFiltrosDeDecisionPropia(){
  fixture();headReader();
  var reader=sections(access.actor(READER));
  assertEquals(List.of("institutional"),List.copyOf(reader.keySet()),"sin secciones de gestion ni resumen de alcance");
  assertEquals(Map.of("com:all",db.count("SELECT COUNT(*) FROM communication"),"req:open",db.count("SELECT COUNT(*) FROM requirement WHERE state='ABIERTO'"),
   "cas:open",db.count("SELECT COUNT(*) FROM case_record WHERE state<>'CERRADO'"),"flujos:pending",db.count("SELECT COUNT(*) FROM flow_request WHERE state='PENDIENTE'")),counts(reader.get("institutional")));
  assertEquals(List.of(),boards.dashboard(access.actor(READER)).get("queues"));

  // Con funciones propias, cada seccion de gestion cuenta lo mismo que sin la consulta (los filtros llevan su facultad).
  for(String who:List.of(HEAD_READER,MIXED)){
   var a=access.actor(who);var own=a.withoutInstitutionalReader();var with=sections(a);var without=sections(own);
   assertTrue(with.containsKey("institutional")&&!with.containsKey("overview"),who);
   assertTrue(without.containsKey("overview"),who);
   without.forEach((id,sec)->{if(!id.equals("overview"))assertEquals(counts(sec),counts(with.get(id)),who+" / "+id);});
   assertEquals(boards.dashboard(own).get("queues").toString().replaceAll("generated_ms=\\d+",""),boards.dashboard(a).get("queues").toString().replaceAll("generated_ms=\\d+",""),who+": colas");
  }
  var head=access.actor(HEAD_READER);
  long own=board(head,"sol","pending").size(),all=db.count("SELECT COUNT(*) FROM solicitation WHERE state='PENDIENTE'");
  assertTrue(own>=1&&own<all,"SOL por asumir: solo las de su recepcion ("+own+" de "+all+")");
  assertEquals(board(head.withoutInstitutionalReader(),"sol","pending"),board(head,"sol","pending"));
  // Distribucion central redistribuye las devueltas de ingresos centrales; las de una sede son de su Coordinacion (R02).
  assertEquals(board(access.actor(DIST),"sol","returned"),where("SELECT s.* FROM solicitation s JOIN communication c ON c.id=s.communication_id JOIN org_scope o ON o.id=c.origin_scope WHERE s.state='DEVUELTA' AND o.regional=FALSE",x->true));
  assertEquals(Set.of(),board(access.actor(READER),"sol","returned"),"devolver a Distribucion no es del lector");
 }

 @Test void fichaAvisaLaLecturaSoloPorConsulta(){
  var f=fixture();headReader();
  long solUpd=n(db.one("SELECT s.id FROM solicitation s JOIN communication c ON c.id=s.communication_id WHERE c.subject='COM para Direccion UPD'"),"id");
  for(var e:List.of(Map.entry("COM",f.comNew()),Map.entry("SOL",f.solAssumed()),Map.entry("REQ",f.req()),Map.entry("CAS",f.caseId()),Map.entry("FLUJO",f.flow()),Map.entry("REQ",f.attention())))
   assertEquals(true,detail(READER,e.getKey(),e.getValue()).get("institutional_read"),"lector: "+e);
  assertEquals(true,detail(MIXED,"CAS",f.caseId()).get("institutional_read"),"Caso ajeno de la cuenta mixta");
  assertEquals(false,detail(HEAD_READER,"FLUJO",f.flow()).get("institutional_read"),"la Direccion destinataria lo lee por su funcion");
  assertEquals(false,detail(HEAD_READER,"SOL",solUpd).get("institutional_read"));
  assertEquals(true,detail(HEAD_READER,"CAS",f.caseId()).get("institutional_read"));
  assertEquals(false,detail(HEAD,"CAS",f.caseId()).get("institutional_read"));
  assertEquals(false,detail(PROF,"REQ",f.req()).get("institutional_read"));
 }

 // --- R02: materia sugerida, materia del REQ y lectura nacional por materia (D9) ------------------------------------
 Map<String,Object> data(Object...kv){var m=new HashMap<String,Object>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
 boolean reads(String who,String kind,long id){
  try{detail(who,kind,id);return true;}catch(Problem p){assertEquals(403,p.status,who+" "+kind+" "+id);return false;}
 }

 @Test void materiaSugeridaYMateriaDelReq(){
  fixture();long sede=scope("REG-DEMO"),upr=scope("UPR-REQ");
  long com=com("COM central para una sede");
  var central=refuse(DIST,"COM_ROUTE","communication",com,data("scope_id",upr,"suggested_matter","UPD","reason","Clasificacion"));
  assertEquals(409,central.status);assertEquals("La materia sugerida se indica solo cuando el destino es una sede regional.",central.getMessage());
  assertEquals(400,refuse(DIST,"COM_ROUTE","communication",com,data("scope_id",sede,"suggested_matter","OTRA","reason","Clasificacion")).status);
  run(DIST,"COM_ROUTE","communication",com,data("scope_id",sede,"suggested_matter","UPD","reason","Competencia regional"));
  long sol=n(db.one("SELECT id FROM solicitation WHERE communication_id=?",com),"id");
  assertEquals("UPD",detail(COORD,"SOL",sol).get("suggested_matter"));
  assertTrue(s(db.one("SELECT note FROM audit_event WHERE action='SOL_DISTRIBUIDA' AND resource_id=?",sol),"note").startsWith("Materia sugerida UPD."));
  assertTrue(reads(DIR_UPD,"SOL",sol));assertFalse(reads(DIR_UPR,"SOL",sol));

  // Redirigir dentro de la sede solo para cambiar la materia es un cambio; repetirla no.
  run(COORD,"SOL_ROUTE","solicitation",sol,data("scope_id",sede,"suggested_matter","UPR","reason","Materia corregida"));
  assertEquals(409,refuse(COORD,"SOL_ROUTE","solicitation",sol,data("scope_id",sede,"suggested_matter","UPR","reason","Sin cambio")).status);
  assertTrue(reads(DIR_UPR,"SOL",sol));assertFalse(reads(DIR_UPD,"SOL",sol));
  // Al nivel central no lleva materia: indicarla se rechaza y omitirla la limpia.
  assertEquals(409,refuse(COORD,"SOL_ROUTE","solicitation",sol,data("scope_id",scope("UPD"),"suggested_matter","UPD","reason","A central")).status);
  run(COORD,"SOL_ROUTE","solicitation",sol,data("scope_id",scope("UPD"),"reason","Competencia central"));
  assertNull(db.one("SELECT suggested_matter FROM solicitation WHERE id=?",sol).get("suggested_matter"));
  assertFalse(reads(DIR_UPR,"SOL",sol));
  run(DIR_UPD,"SOL_ROUTE","solicitation",sol,data("scope_id",sede,"suggested_matter","UEP","reason","Vuelve a la sede"));

  // El REQ hereda la materia sugerida al asumir; la Coordinacion la rectifica con fundamento.
  long req=run(COORD,"SOL_ASSUME","solicitation",sol,data("responsible_id",user(PROF_REG),"reason","Asumida"));
  assertEquals("UEP",db.one("SELECT matter FROM requirement WHERE id=?",req).get("matter"));
  assertTrue(reads(DIR_UEP,"REQ",req));assertFalse(reads(DIR_UPD,"REQ",req));
  assertEquals(403,refuse(DIR_UEP,"REQ_MATTER","requirement",req,data("matter","UPD","reason","Intento")).status);
  assertEquals("La materia del REQ no cambio.",refuse(COORD,"REQ_MATTER","requirement",req,data("matter","UEP","reason","Sin cambio")).getMessage());
  assertEquals(400,refuse(COORD,"REQ_MATTER","requirement",req,data("matter","OTRA","reason","Fuera de catalogo")).status);
  long revision=rev("requirement",req);
  run(COORD,"REQ_MATTER","requirement",req,data("matter","UPD","reason","Materia rectificada por la Coordinacion"));
  assertEquals(revision+1,rev("requirement",req));
  assertEquals("UEP -> UPD. Materia rectificada por la Coordinacion",s(db.one("SELECT note FROM audit_event WHERE action='REQ_MATTER' AND resource_id=?",req),"note"));
  assertTrue(reads(DIR_UPD,"REQ",req));assertFalse(reads(DIR_UEP,"REQ",req));
  // Solo en sedes.
  long centralReq=run(HEAD,"COM_ASSUME","communication",com(HEAD,"COM central sin materia"),data("responsible_id",user(PROF),"reason","Ingreso propio"));
  assertEquals("La materia del REQ se registra solo en las sedes regionales.",refuse(HEAD,"REQ_MATTER","requirement",centralReq,data("matter","UPR","reason","Intento")).getMessage());
 }

 @Test void direccionLeeSuMateriaEnLaSedeSinGestionNiReservados()throws Exception{
  var x=regional();long com=n(db.one("SELECT communication_id FROM requirement WHERE id=?",x.req()),"communication_id");
  for(var e:List.of(Map.entry("COM",com),Map.entry("SOL",x.solPending()),Map.entry("SOL",x.solReturned()),Map.entry("SOL",x.solAssumed()),Map.entry("REQ",x.req()),
    Map.entry("REQ",x.closedReq()),Map.entry("REQ",x.attention()),Map.entry("CAS",x.caseId()),Map.entry("FLUJO",x.flow()))){
   var v=detail(DIR_UPD,e.getKey(),e.getValue());noFaculties("UPD "+e,v);
   assertEquals(true,v.get("matter_read"),"aviso de lectura por materia: "+e);assertEquals(false,v.get("institutional_read"));
  }
  // Registro del REQ por su materia, sin reservados.
  var att=detail(DIR_UPD,"REQ",x.attention());
  @SuppressWarnings("unchecked") var view=(Map<String,Object>)att.get("attention");
  assertEquals(true,view.get("can_read"));assertNotNull(view.get("form"),"ficha NNA de su materia");
  assertEquals(List.of(x.openReqNote()),ids(att,"notes"));assertEquals(List.of(x.reqOpenDoc()),ids(att,"documents"));
  var closed=detail(DIR_UPD,"REQ",x.closedReq());
  assertEquals("Motivo de cierre sintetico",closed.get("close_reason"));assertEquals(List.of(x.reopening()),ids(closed,"reopenings"));
  var req=detail(DIR_UPD,"REQ",x.req());assertEquals(List.of(x.link()),ids(req,"links"));assertEquals(List.of(x.flow()),ids(req,"flows"));
  var cas=detail(DIR_UPD,"CAS",x.caseId());
  assertEquals(List.of(x.openNote()),ids(cas,"notes"));assertEquals(List.of(x.caseOpenDoc()),ids(cas,"documents"));
  db.update("UPDATE staff SET must_change=FALSE WHERE username=?",DIR_UPD);var upd=access.actor(DIR_UPD);
  assertEquals("informe-caso.txt",documents.download(upd,x.caseOpenDoc()).name());
  for(long reserved:List.of(x.caseReservedDoc(),x.reqReservedDoc()))assertEquals(403,assertThrows(Problem.class,()->documents.download(upd,reserved)).status);

  // La Coordinacion regional conserva la gestion y no ve el aviso.
  var coord=detail(COORD,"REQ",x.req());assertEquals(true,coord.get("can_head"));assertEquals(false,coord.get("matter_read"));
  assertEquals(true,detail(COORD,"CAS",x.caseId()).get("can_head"));
  assertEquals(true,detail(COORD,"SOL",x.solPending()).get("can_decide"));
  // Otra materia no lee nada de la cadena (UEP solo el flujo que recibe, por su funcion); el COM nuevo sin SOL tampoco.
  for(String other:List.of(DIR_UPR,DIR_UEP))for(var e:List.of(Map.entry("COM",com),Map.entry("SOL",x.solAssumed()),Map.entry("REQ",x.req()),Map.entry("CAS",x.caseId())))
   assertFalse(reads(other,e.getKey(),e.getValue()),other+" "+e);
  assertEquals(false,detail(DIR_UEP,"FLUJO",x.flow()).get("matter_read"));
  assertFalse(reads(DIR_UPD,"COM",x.comNew()));
  // Regional sin materia, y central con materia: fuera de la lectura nacional.
  long plain=run(COORD,"COM_ASSUME","communication",com(REGIONAL,COORD,"COM regional sin materia"),data("responsible_id",user(PROF_REG),"reason","Ingreso propio"));
  assertFalse(reads(DIR_UPD,"REQ",plain));
  long centralReq=run(HEAD,"COM_ASSUME","communication",com(HEAD,"COM central con materia"),data("responsible_id",user(PROF),"reason","Ingreso propio"));
  long centralCase=run(HEAD,"REQ_CASE_CREATE","requirement",centralReq,data("scope_id",scope("UPR-REQ"),"responsible_id",user(PROF),"objective","Caso central","matter","UPD"));
  db.update("UPDATE requirement SET matter='UPD' WHERE id=?",centralReq);
  assertFalse(reads(DIR_UPD,"REQ",centralReq));assertFalse(reads(DIR_UPD,"CAS",centralCase));

  // Una sede cerrada sigue legible por materia (D8).
  db.update("UPDATE org_scope SET enabled=FALSE,state='CERRADA' WHERE code='REG-DEMO'");
  try{assertTrue(reads(DIR_UPD,"CAS",x.caseId()));assertTrue(board(access.actor(DIR_UPD),"cas","all").contains(x.caseId()));}
  finally{db.update("UPDATE org_scope SET enabled=TRUE,state='ACTIVA' WHERE code='REG-DEMO'");}
 }

 @Test void casoDeSuMateriaNoAbreLaFichaNiLosOriginales()throws Exception{
  regional();long sede=scope("REG-DEMO");
  // REQ con materia UPR (primera atencion con un original de ingreso) y Caso con materia UPD.
  long req=run(PROF_REG,"ATTENTION_CREATE","requirement",null,data("scope_id",sede,"channel","PRESENCIAL","received_at",ago(600),"form",form("EN_ATENCION")));
  long com=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
  long original=upload(PROF_REG,"COM",com,false,"original-ingreso.txt");
  run(COORD,"REQ_MATTER","requirement",req,data("matter","UPR","reason","Materia de la sede"));
  long cas=run(COORD,"REQ_CASE_CREATE","requirement",req,data("scope_id",sede,"responsible_id",user(PROF_REG),"objective","Linea UPD","matter","UPD"));
  run(PROF_REG,"CASE_NOTE","case_record",cas,note("Constancia abierta del Caso",false));
  run(PROF_REG,"CASE_NOTE","case_record",cas,note("Constancia reservada del Caso",true));
  long open=n(db.one("SELECT id FROM management_note WHERE case_id=? AND reserved=FALSE",cas),"id");
  long caseDoc=upload(PROF_REG,"CAS",cas,false,"informe-linea.txt");

  // UPD: el Caso, sus constancias y documentos no reservados y el resumen del REQ; nada de la COM ni de la ficha.
  db.update("UPDATE staff SET must_change=FALSE WHERE username IN (?,?)",DIR_UPD,DIR_UPR);var upd=access.actor(DIR_UPD);
  var c=detail(DIR_UPD,"CAS",cas);noFaculties("CAS UPD",c);
  assertEquals(true,c.get("matter_read"));
  assertEquals(List.of(open),ids(c,"notes"));assertEquals(List.of(caseDoc),ids(c,"documents"));
  assertEquals(List.of(),c.get("citizen_documents"),"sin originales de ingreso");
  assertFalse(access.caseSourceDocuments(upd,db.one("SELECT * FROM case_record WHERE id=?",cas)));
  var r=detail(DIR_UPD,"REQ",req);noFaculties("REQ UPD",r);
  @SuppressWarnings("unchecked") var view=(Map<String,Object>)r.get("attention");
  assertEquals(false,view.get("can_read"));assertNull(view.get("form"),"sin ficha de primera atencion");
  assertEquals(false,r.get("can_read_com"));assertEquals(List.of(),r.get("citizen_documents"));
  assertEquals(List.of(),r.get("notes"));assertEquals(List.of(),r.get("documents"));assertEquals(List.of(),r.get("history"));
  assertFalse(reads(DIR_UPD,"COM",com));
  assertEquals(403,assertThrows(Problem.class,()->documents.download(upd,original)).status);
  long version=n(db.one("SELECT MAX(id) AS id FROM attention_revision WHERE requirement_id=?",req),"id");
  assertEquals(403,assertThrows(Problem.class,()->attention.version(upd,req,version)).status);
  assertFalse(board(upd,"com","all").contains(com),"la COM no entra en su bandeja");

  // UPR, por la materia del REQ: la cadena completa, pero no el Caso de otra materia.
  var upr=access.actor(DIR_UPR);
  @SuppressWarnings("unchecked") var full=(Map<String,Object>)detail(DIR_UPR,"REQ",req).get("attention");
  assertEquals(true,full.get("can_read"));assertNotNull(full.get("form"));
  assertTrue(reads(DIR_UPR,"COM",com));
  assertEquals("original-ingreso.txt",documents.download(upr,original).name());
  assertFalse(reads(DIR_UPR,"CAS",cas));
 }

 @Test void subroganciaDeLaDireccionTrasladaLaLecturaPorMateria()throws Exception{
  var x=regional();String substitute="profesional.upd";
  assertEquals(Set.of("UPD"),access.actor(DIR_UPD).matters());
  assertEquals(Set.of(),access.actor(substitute).matters());
  long designation=run("ti","DESIGNATE",null,null,data("scope_id",scope("UPD"),"titular_id",user(DIR_UPD),"substitute_id",user(substitute),"reason","Resolucion sintetica"));
  long substitution=run("ti","SUBSTITUTE_ACTIVATE",null,designation,data("from_at",ago(5),"until_at",Instant.now().plusSeconds(3600).toString(),"reason","Feriado legal sintetico"));
  try{
   assertEquals(Set.of("UPD"),access.actor(substitute).matters());
   assertEquals(Set.of(),access.actor(DIR_UPD).matters(),"la lectura sigue a la funcion subrogada");
   assertTrue(reads(substitute,"CAS",x.caseId()));assertFalse(reads(DIR_UPD,"CAS",x.caseId()));
   for(String who:List.of(substitute,DIR_UPD)){var a=access.actor(who);expected(a).forEach((kind,ids)->assertEquals(ids,board(a,kind,"all"),who+" / "+kind));}
  }finally{
   run("ti","SUBSTITUTE_END","substitution",substitution,data("reason","Termino sintetico"));
  }
  assertEquals(Set.of("UPD"),access.actor(DIR_UPD).matters());
 }

 @Test void ningunComandoProsperaPorLecturaDeMateria()throws Exception{
  var x=regional();
  assertTrue(reads(DIR_UPD,"CAS",x.caseId())&&reads(DIR_UPD,"REQ",x.attention()),"el barrido opera sobre lo que lee");
  sweep(DIR_UPD,attempts(x,REGIONAL));
 }
}
