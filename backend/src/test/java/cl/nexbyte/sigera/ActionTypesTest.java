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
 * Actuaciones tipificadas (0.8.0, D36, gate E06; DISENO 3.3): escenario 8 (dos denuncias penales son dos actuaciones
 * tipificadas; una tarea "compromiso de querella" completada no crea una actuacion), tipo del catalogo vigente,
 * instituciones con las reglas de la ficha 1.2, rectificacion que conserva la anterior (clave ausente = conserva; vacio
 * = quita), constancia reservada que sigue reservada con su tipo e instituciones, y resultado de la primera atencion en
 * requirement.immediate_result como unica fuente (su constancia no se tipifica), con la carga historica por el prefijo
 * exacto del codigo solo si coincide con el cierre registrado (marcador RESULTADO_INMEDIATO_V20).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_actuaciones;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-actuaciones-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class ActionTypesTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String HEAD="coordinacion.requerimientos",PROF="profesional",ADMIN="administrativo";
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired Attention attention;

    @BeforeEach void ready()throws Exception{
        db.update("UPDATE staff SET must_change=FALSE");
        for(var e:Map.of("TIPO_ACTUACION","tipo-actuacion-v1.json","TIPO_INSTITUCION","tipo-institucion-v1.json").entrySet()){
            String text=Files.readString(ReferenceCatalogsTest.DIR.resolve(e.getValue())).replace("\r\n","\n");
            if(db.count("SELECT COUNT(*) FROM stat_catalog_version WHERE `current_catalog`=? AND file_sha256=?",e.getKey(),StatCatalogs.sha(text))>0)continue;
            long v=run("ti","CATALOG_IMPORT",null,null,Map.of("content",text,"reason","Fundamento sintetico")).id();
            run("ti","CATALOG_ACTIVATE",v,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",v),"revision"),Map.of("reason","Activacion sintetica"));
        }
    }
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    Workflow.Result on(String who,String type,String table,long id,Map<String,Object> data){return run(who,type,id,rev(table,id),data);}
    Problem refused(Runnable r){return assertThrows(Problem.class,r::run);}
    static String ago(long s){return java.time.Instant.now().minusSeconds(s).toString();}
    static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    static Map<String,Object> institution(String name,String type){return map("name",name,"type_code",type,"role","Institucion requerida","region_code","13","commune","Santiago");}
    Map<String,Object> note(String body,Object... extra){var m=map("effective_at",ago(60),"body",body);for(int i=0;i<extra.length;i+=2)m.put((String)extra[i],extra[i+1]);return m;}

    long req(){
        long com=run(HEAD,"COM_CREATE",null,null,map("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",ago(600),"subject","Ingreso sintetico","body","Relato sintetico","intake",Map.of())).id();
        return on(HEAD,"COM_ASSUME","communication",com,Map.of("responsible_id",user(PROF),"reason","Gestion propia")).id();
    }
    long newCase(long req){return on(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",scope("UPR-REQ"),"responsible_id",user(PROF),"objective","Gestion sintetica","matter","UPR")).id();}
    Map<String,Object> lastNote(long req){return db.one("SELECT * FROM management_note WHERE requirement_id=? ORDER BY id DESC LIMIT 1",req);}
    @SuppressWarnings("unchecked") List<Map<String,Object>> notesOf(String who,String kind,long id){return (List<Map<String,Object>>)queries.detail(access.actor(who),kind,id).get("notes");}

    // --- Escenario 8 ------------------------------------------------------------------------------------------------
    @Test void escenario8DosDenunciasYUnCompromisoQueNoEsActuacion(){
        long req=req(),cas=newCase(req);
        on(PROF,"CASE_NOTE","case_record",cas,note("Denuncia penal sintetica 1","action_type","DENUNCIA_PENAL","institutions",List.of(institution("Fiscalia sintetica","ORGANISMO_PUBLICO"))));
        on(PROF,"CASE_NOTE","case_record",cas,note("Denuncia penal sintetica 2","action_type","DENUNCIA_PENAL"));
        assertEquals(2,db.count("SELECT COUNT(*) FROM management_note WHERE requirement_id=? AND action_type='DENUNCIA_PENAL'",req),"dos actuaciones");
        long version=n(db.one("SELECT id FROM stat_catalog_version WHERE `current_catalog`='TIPO_ACTUACION'"),"id");
        assertEquals(2,db.count("SELECT COUNT(*) FROM management_note WHERE requirement_id=? AND action_catalog_version=?",req,version),"version con que se registro");
        var inst=db.one("SELECT * FROM note_institution WHERE note_id=(SELECT MIN(id) FROM management_note WHERE requirement_id=? AND action_type='DENUNCIA_PENAL')",req);
        assertEquals("Fiscalia sintetica",s(inst,"name"));assertEquals("ORGANISMO_PUBLICO",s(inst,"type_code"));
        assertEquals(n(db.one("SELECT id FROM stat_catalog_version WHERE `current_catalog`='TIPO_INSTITUCION'"),"id"),n(inst,"catalog_version_id"));
        // Tarea con compromiso de querella: completada no crea constancia ni actuacion.
        long task=run(HEAD,"TASK_CREATE",cas,rev("case_record",cas),Map.of("executor_id",user(PROF),"title","Presentar querella","action_type","QUERELLA")).id();
        long taskId=n(db.one("SELECT MAX(id) AS id FROM task_item WHERE case_id=?",cas),"id");
        assertEquals("QUERELLA",s(db.one("SELECT action_type FROM task_item WHERE id=?",taskId),"action_type"));
        long notes=db.count("SELECT COUNT(*) FROM management_note WHERE requirement_id=?",req);
        on(PROF,"TASK_COMPLETE","task_item",taskId,Map.of("reason","Querella presentada"));
        assertEquals(notes,db.count("SELECT COUNT(*) FROM management_note WHERE requirement_id=?",req),"completar no crea actuacion");
        assertEquals(0,db.count("SELECT COUNT(*) FROM management_note WHERE requirement_id=? AND action_type='QUERELLA'",req));
        assertTrue(task>0);
        // La ficha del Caso entrega el rotulo del tipo y las instituciones de cada constancia visible.
        var first=notesOf(PROF,"CAS",cas).getFirst();
        assertEquals("Denuncia penal",first.get("action_label"));assertEquals(1,((List<?>)first.get("institutions")).size());
        @SuppressWarnings("unchecked") var tasks=(List<Map<String,Object>>)queries.detail(access.actor(PROF),"CAS",cas).get("tasks");
        assertEquals("Querella",tasks.getLast().get("action_label"));
    }

    // --- Tipo e instituciones: catalogo vigente y reglas de la ficha 1.2 --------------------------------------------
    @Test void tipoDelCatalogoVigenteEInstitucionesValidadas(){
        long req=req(),cas=newCase(req);
        long notes=db.count("SELECT COUNT(*) FROM management_note");
        assertEquals(409,refused(()->on(PROF,"CASE_NOTE","case_record",cas,note("Tipo inexistente","action_type","NO_EXISTE"))).status);
        assertEquals(400,refused(()->on(PROF,"CASE_NOTE","case_record",cas,note("Institucion sin nombre","institutions",List.of(map("type_code","ORGANISMO_PUBLICO"))))).status);
        assertEquals(400,refused(()->on(PROF,"CASE_NOTE","case_record",cas,note("Region invalida","institutions",List.of(map("name","X","region_code","99"))))).status);
        assertEquals(notes,db.count("SELECT COUNT(*) FROM management_note"),"sin escrituras");
        // Sin tipo sigue siendo una constancia valida (captura progresiva).
        on(PROF,"CASE_NOTE","case_record",cas,note("Constancia sin tipificar"));
        assertTrue(lastNote(req).get("action_type")==null);
        // Constancia del REQ sin Caso (REQ_NOTE) tambien se tipifica.
        long single=req();
        on(PROF,"REQ_NOTE","requirement",single,note("Orientacion sintetica","no_followup",true,"action_type","ORIENTACION"));
        assertEquals("ORIENTACION",s(lastNote(single),"action_type"));
        // Sin listado vigente el tipo no se admite (simulado por SQL y restaurado).
        long v=n(db.one("SELECT id FROM stat_catalog_version WHERE `current_catalog`='TIPO_ACTUACION'"),"id");
        db.update("UPDATE stat_catalog_version SET `current_catalog`=NULL WHERE id=?",v);
        try{
            var p=refused(()->on(PROF,"CASE_NOTE","case_record",cas,note("Sin listado","action_type","OFICIO")));
            assertEquals(409,p.status);assertTrue(p.getMessage().contains("no hay un listado vigente"),p.getMessage());
            on(PROF,"CASE_NOTE","case_record",cas,note("Sin listado y sin tipo"));
        }finally{db.update("UPDATE stat_catalog_version SET `current_catalog`='TIPO_ACTUACION' WHERE id=?",v);}
    }

    // --- Rectificacion -----------------------------------------------------------------------------------------------
    @Test void rectificacionConservaLaAnteriorYLaClaveAusente(){
        long req=req(),cas=newCase(req);
        on(PROF,"CASE_NOTE","case_record",cas,note("Oficio sintetico","action_type","OFICIO","institutions",List.of(institution("Municipalidad sintetica","ORGANISMO_PUBLICO"))));
        long first=n(lastNote(req),"id");
        // Sin action_type ni institutions: la rectificacion los conserva.
        run(PROF,"NOTE_RECTIFY",first,null,note("Oficio sintetico corregido","reason","Correccion de redaccion"));
        long second=n(lastNote(req),"id");
        assertEquals("OFICIO",s(lastNote(req),"action_type"));
        assertEquals(List.of("Municipalidad sintetica"),db.rows("SELECT name FROM note_institution WHERE note_id=?",second).stream().map(r->s(r,"name")).toList());
        // La anterior queda intacta con su tipo e instituciones.
        assertEquals("OFICIO",s(db.one("SELECT action_type FROM management_note WHERE id=?",first),"action_type"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM note_institution WHERE note_id=?",first));
        // Valor vacio explicito: los quita (con el motivo que ya exige la rectificacion).
        run(PROF,"NOTE_RECTIFY",second,null,note("Constancia sin tipo","action_type","","institutions",List.of(),"reason","No era un oficio"));
        var third=lastNote(req);
        assertNull(third.get("action_type"));assertNull(third.get("action_catalog_version"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM note_institution WHERE note_id=?",n(third,"id")));
        // Otro tipo en la rectificacion.
        run(PROF,"NOTE_RECTIFY",n(third,"id"),null,note("Solicitud de antecedentes","action_type","SOLICITUD_ANTECEDENTES","reason","Tipificacion"));
        assertEquals("SOLICITUD_ANTECEDENTES",s(lastNote(req),"action_type"));
    }

    // --- Reserva ----------------------------------------------------------------------------------------------------
    @Test void constanciaReservadaSigueReservadaConSuTipoEInstituciones(){
        String reader="lector.institucional.e06b";
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",reader)==0){
            long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'test-non-login',?,FALSE)",reader,"Cuenta sintetica",scope("UGI"));
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'PRUEBA AUTOMATICA')",id,Roles.CONSULTA_INSTITUCIONAL);
        }
        long req=req(),cas=newCase(req);
        on(PROF,"CASE_NOTE","case_record",cas,note("Querella reservada sintetica","reserved",true,"action_type","QUERELLA","institutions",List.of(institution("Juzgado reservado sintetico","ORGANISMO_PUBLICO"))));
        long reserved=n(lastNote(req),"id");
        run(PROF,"NOTE_RECTIFY",reserved,null,note("Querella reservada corregida","reserved",true,"reason","Correccion"));
        assertTrue(b(lastNote(req),"reserved"));assertEquals("QUERELLA",s(lastNote(req),"action_type"));
        // Quien ve reservadas (responsable) ve tipo e instituciones; la lectura institucional no ve nada de ellas.
        assertTrue(notesOf(PROF,"CAS",cas).stream().anyMatch(x->"Querella".equals(x.get("action_label"))));
        var detail=queries.detail(access.actor(reader),"CAS",cas).toString();
        assertFalse(detail.contains("Juzgado reservado sintetico"),"institucion de una constancia reservada");
        assertFalse(detail.contains("QUERELLA"),"tipo de una constancia reservada");
        // Una rectificacion no desclasifica.
        assertEquals(409,refused(()->run(PROF,"NOTE_RECTIFY",n(lastNote(req),"id"),null,note("Intento","reason","Intento"))).status);
    }

    // --- Tareas: compromiso al crear y editar -----------------------------------------------------------------------
    @Test void tareaConCompromisoClaveAusenteConservaYVacioQuita(){
        long req=req(),cas=newCase(req);
        run(HEAD,"TASK_CREATE",cas,rev("case_record",cas),Map.of("executor_id",user(PROF),"title","Coordinar","action_type","COORDINACION_INTERINSTITUCIONAL"));
        long task=n(db.one("SELECT MAX(id) AS id FROM task_item WHERE case_id=?",cas),"id");
        on(HEAD,"TASK_EDIT","task_item",task,Map.of("executor_id",user(PROF),"reason","Ajuste de plazo"));
        assertEquals("COORDINACION_INTERINSTITUCIONAL",s(db.one("SELECT action_type FROM task_item WHERE id=?",task),"action_type"));
        on(HEAD,"TASK_EDIT","task_item",task,Map.of("executor_id",user(PROF),"action_type","","reason","Sin compromiso"));
        assertNull(db.one("SELECT action_type FROM task_item WHERE id=?",task).get("action_type"));
        assertEquals(409,refused(()->on(HEAD,"TASK_EDIT","task_item",task,Map.of("executor_id",user(PROF),"action_type","NO_EXISTE","reason","Intento"))).status);
        assertEquals(0,db.count("SELECT COUNT(*) FROM management_note WHERE requirement_id=?",req),"una tarea no es una actuacion");
    }

    // --- Resultado de la primera atencion: una sola fuente ------------------------------------------------------------
    @Test void resultadoInmediatoSinConstanciaTipificada(){
        long req=run(ADMIN,"COM_IMMEDIATE",null,null,map("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL","received_at",ago(600),"subject","Orientacion","body","Relato sintetico",
            "intake",Map.of(),"reason","Respuesta entregada","effective_at",ago(300),"result","RESPUESTA_DIRECTA")).id();
        var r=db.one("SELECT immediate_result,immediate_result_reconstructed FROM requirement WHERE id=?",req);
        assertEquals("RESPUESTA_DIRECTA",s(r,"immediate_result"));assertFalse(b(r,"immediate_result_reconstructed"));
        assertNull(lastNote(req).get("action_type"),"la constancia del resultado no se cuenta como actuacion");
        assertEquals("RESPUESTA_DIRECTA: Respuesta entregada",s(lastNote(req),"body"));
        // ATTENTION_RESOLVE.
        long att=run(ADMIN,"ATTENTION_CREATE",null,null,map("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL","received_at",ago(600),"form",form())).id();
        on(ADMIN,"ATTENTION_RESOLVE","requirement",att,Map.of("result","REMISION_CONCLUIDA","reason","Remision concluida","effective_at",ago(10)));
        assertEquals("REMISION_CONCLUIDA",s(db.one("SELECT immediate_result FROM requirement WHERE id=?",att),"immediate_result"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM management_note WHERE requirement_id IN (?,?) AND action_type IS NOT NULL",req,att));
    }
    static Map<String,Object> form(){
        return Map.of("schema_version","1.0","modality","NNA","motive","Contacto sintetico NNA","course","EN_ATENCION","participation","NO",
            "safety_note","Nota de resguardo sintetica","people",List.of(Map.of("role","NNA","name","Nombre NNA sintetico")));
    }

    // --- Rotulo de un codigo segun la version con que se registro (pendiente de E04) -------------------------------
    @Test @SuppressWarnings("unchecked") void rotuloDelCodigoSegunSuVersionAunqueElListadoSeRetire()throws Exception{
        long com=run(HEAD,"COM_CREATE",null,null,map("scope_id",scope("UPR-REQ"),"channel","OFICIO","received_at",ago(600),"subject","Oficio sintetico","body","Relato sintetico",
            "intake",Map.of("institutions_list",List.of(map("name","Liceo sintetico","type_code","ESTABLECIMIENTO_EDUCATIVO","role","Remite el oficio"))))).id();
        long v=n(db.one("SELECT id FROM stat_catalog_version WHERE `current_catalog`='TIPO_INSTITUCION'"),"id");
        assertEquals(Map.of(v+":ESTABLECIMIENTO_EDUCATIVO","Establecimiento educativo"),queries.detail(access.actor(HEAD),"COM",com).get("code_labels"));
        // Otro listado vigente: el codigo registrado conserva el rotulo de su version.
        long next=run("ti","CATALOG_IMPORT",null,null,Map.of("content",StatCatalogsTest.file("TIPO_INSTITUCION","OTRO_TIPO"),"reason","Version sintetica")).id();
        run("ti","CATALOG_ACTIVATE",next,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",next),"revision"),Map.of("reason","Activacion sintetica"));
        try{
            assertEquals(Map.of(v+":ESTABLECIMIENTO_EDUCATIVO","Establecimiento educativo"),queries.detail(access.actor(HEAD),"COM",com).get("code_labels"));
            // Regla comun (tambien la ficha de primera atencion): relacion y tipo a cualquier profundidad.
            var labels=StatValues.codeLabels(db,Map.of("relationship_code","MADRE","relationship_version","999","people",List.of(Map.of("type_code","ESTABLECIMIENTO_EDUCATIVO","type_version",v))));
            assertEquals(Map.of(v+":ESTABLECIMIENTO_EDUCATIVO","Establecimiento educativo"),labels,"una version inexistente no aporta rotulo");
        }finally{ready();}
    }

    // --- Carga historica (RESULTADO_INMEDIATO_V20) ------------------------------------------------------------------
    @Test void cargaHistoricaPorElPrefijoExactoQueCoincideConElCierre(){
        long upr=scope("UPR-REQ");
        var made=new ArrayList<Long>();
        for(String result:Lifecycle.IMMEDIATE_RESULTS)
            made.add(run(ADMIN,"COM_IMMEDIATE",null,null,map("scope_id",upr,"channel","PRESENCIAL","received_at",ago(600),"subject","Historico","body","Relato sintetico",
                "intake",Map.of(),"reason","Atencion "+result,"effective_at",ago(300),"result",result)).id());
        // Cuatro REQ sin evidencia exacta: otro texto, minusculas (MySQL las igualaria), otra separacion y una constancia
        // posterior con el prefijo pero sin el cierre.
        var decoys=new ArrayList<Long>();
        for(String body:List.of("ORIENTACION: otro texto","orientacion: Atencion decoy","ORIENTACION:Atencion decoy","ORIENTACION - Atencion decoy")){
            long d=run(ADMIN,"COM_IMMEDIATE",null,null,map("scope_id",upr,"channel","PRESENCIAL","received_at",ago(600),"subject","Historico","body","Relato sintetico",
                "intake",Map.of(),"reason","Atencion decoy","effective_at",ago(300),"result","ORIENTACION")).id();
            db.update("UPDATE management_note SET body=? WHERE requirement_id=?",body,d);
            decoys.add(d);
        }
        // Estado de una base anterior a V20: sin resultado estructurado y sin marcador.
        db.update("UPDATE requirement SET immediate_result=NULL,immediate_result_reconstructed=FALSE");
        db.update("DELETE FROM application_marker WHERE code=?",Attention.RESULT_MARKER);
        attention.rebuildImmediateResults();
        for(int i=0;i<made.size();i++){
            var r=db.one("SELECT immediate_result,immediate_result_reconstructed FROM requirement WHERE id=?",made.get(i));
            assertEquals(Lifecycle.IMMEDIATE_RESULTS[i],s(r,"immediate_result"));assertTrue(b(r,"immediate_result_reconstructed"));
        }
        for(long d:decoys)assertNull(db.one("SELECT immediate_result FROM requirement WHERE id=?",d).get("immediate_result"),"sin evidencia exacta: se omite");
        var marker=db.one("SELECT amount FROM application_marker WHERE code=?",Attention.RESULT_MARKER);
        // Las demas primeras atenciones de esta base tienen su constancia exacta: solo se omiten las cuatro sin evidencia.
        assertEquals(decoys.size(),n(marker,"amount"),"omitidos contados");
        // Una sola vez: un segundo arranque no vuelve a recorrer.
        db.update("UPDATE requirement SET immediate_result=NULL WHERE id=?",made.getFirst());
        attention.rebuildImmediateResults();
        assertNull(db.one("SELECT immediate_result FROM requirement WHERE id=?",made.getFirst()).get("immediate_result"));
    }
}
