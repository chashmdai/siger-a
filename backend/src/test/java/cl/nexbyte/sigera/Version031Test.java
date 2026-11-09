package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/** Cuatro recorridos focales, no una bateria masiva. Windows/MySQL se validan manualmente. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera031;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-data031-${random.uuid}"})
@ActiveProfiles("test")
class Version031Test {
    @Autowired Workflow flow; @Autowired Access access; @Autowired Db db;
    @Autowired Documents docs; @Autowired BoardQueries boards; @Autowired Bootstrap bootstrap;
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long revision(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){db.update("UPDATE staff SET must_change=FALSE WHERE username=?",who);return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    Map<String,Object> intake(long scope,String subject){var m=new LinkedHashMap<String,Object>();m.put("scope_id",scope);m.put("subject",subject);m.put("body","Solo datos sinteticos");m.put("channel","TELEFONO");m.put("received_at",Instant.now().minusSeconds(60).toString());return m;}
    long ownReq(String head,long root,long professional,String subject){long com=run(head,"COM_CREATE",null,null,intake(root,subject)).id();return run(head,"COM_ASSUME",com,revision("communication",com),Map.of("responsible_id",professional,"reason","Gestion propia de unidad")).id();}
    @Test void subroganciaDireccionRecibeSolYConservaCarteraTitular(){
        long root=scope("UPD"),area=scope("UPD-DIF"),titular=user("direccion.upd"),sub=user("profesional.upd");
        long req=ownReq("direccion.upd",root,titular,"Cartera personal de titular");
        long cas=run("direccion.upd","REQ_CASE_CREATE",req,revision("requirement",req),Map.of("scope_id",area,"responsible_id",titular,"objective","Gestion personal","matter","UPD")).id();
        long designation=run("autoridad","DESIGNATE",null,null,Map.of("scope_id",root,"titular_id",titular,"substitute_id",sub,"reason","Predesignacion sintetica")).id();
        long period=run("autoridad","SUBSTITUTE_ACTIVATE",designation,null,Map.of("from_at",Instant.now().minusSeconds(1).toString(),"until_at",Instant.now().plusSeconds(3600).toString(),"reason","Ausencia temporal")).id();
        var acting=access.actor("profesional.upd");var old=access.actor("direccion.upd");
        assertTrue(acting.at("DIRECCION",root));assertTrue(acting.head(area));assertTrue(access.solReceiver(acting,root));
        assertFalse(old.head(root));assertFalse(old.head(area));assertTrue(access.caseWrite(old,db.one("SELECT * FROM case_record WHERE id=?",cas)));
        assertFalse(acting.head(scope("UPR-REQ")));
        run("autoridad","SUBSTITUTE_END",period,revision("substitution",period),Map.of("reason","Retorno"));
        assertTrue(access.actor("direccion.upd").head(area));assertFalse(access.actor("profesional.upd").head(root));
    }
    @Test void reinicioDemoNoReponePermisoRevocado()throws Exception{
        long uid=user("direccion.uep"),sc=scope("UEP-EST");
        long id=n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND scope_id=? AND role_code='DIRECCION' AND revoked_at IS NULL",uid,sc),"id");
        db.update("UPDATE role_assignment SET revoked_at=? WHERE id=?",System.currentTimeMillis(),id);
        try{bootstrap.run(null);assertFalse(access.actor("direccion.uep").head(sc));assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND scope_id=? AND role_code='DIRECCION' AND revoked_at IS NULL",uid,sc));}
        finally{db.update("UPDATE role_assignment SET revoked_at=NULL WHERE id=?",id);}
    }
    @Test void inSituConAdjuntoYAntecedenteRechazadoVuelveSinDuplicar()throws Exception{
        long sc=scope("UPR-REQ");db.update("UPDATE staff SET must_change=FALSE WHERE username='administrativo'");
        UUID token=UUID.randomUUID();docs.stage("administrativo",sc,token,new MockMultipartFile("file","constancia.txt","text/plain","Sintetico".getBytes()));
        var data=intake(sc,"Atencion resuelta con evidencia");data.put("attachment_tokens",List.of(token.toString()));data.put("effective_at",Instant.now().minusSeconds(5).toString());data.put("result","ORIENTACION");data.put("reason","Orientacion concluida");data.put("persons",List.of(Map.of("role","NNA","name","NNA sintetico","location","Localizacion declarada")));data.put("contact_restrictions","No llamar");
        long req=run("administrativo","COM_IMMEDIATE",null,null,data).id();long com=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
        assertEquals(1,db.count("SELECT COUNT(*) FROM document_item WHERE communication_id=? AND quarantined=TRUE",com));assertEquals(1,db.count("SELECT COUNT(*) FROM communication_person WHERE communication_id=?",com));assertEquals(0,db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",req));
        long child=run("distribucion","COM_CREATE",null,null,intake(sc,"Otro ingreso")).id();
        run("distribucion","COM_LINK",child,revision("communication",child),Map.of("folio",s(db.one("SELECT folio FROM requirement WHERE id=?",req),"folio"),"reason","Posible relacion"));
        long link=n(db.one("SELECT id FROM communication_link WHERE communication_id=?",child),"id");
        run("coordinacion.requerimientos","LINK_DECIDE",link,revision("communication_link",link),Map.of("decision","RECHAZADA","reason","Asunto distinto"));
        run("coordinacion.requerimientos","LINK_RETURN",link,revision("communication_link",link),Map.of("reason","Reclasificar sin borrar"));
        assertNotNull(db.one("SELECT returned_ms FROM communication_link WHERE id=?",link).get("returned_ms"));
        assertEquals("NUEVA",s(db.one("SELECT state FROM communication WHERE id=?",child),"state"));
        run("distribucion","COM_ROUTE",child,revision("communication",child),Map.of("scope_id",scope("UPD"),"reason","Destino correcto"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",child));
    }
    @Test void bandejasAisladasYDocumentoAsociadoSinSobrescribir()throws Exception{
        long root=scope("UPR"),area=scope("UPR-PD"),prof=user("profesional.proteccion");
        long req=ownReq("direccion.upr",root,prof,"Documento de una actuacion");
        long cas=run("direccion.upr","REQ_CASE_CREATE",req,revision("requirement",req),Map.of("scope_id",area,"responsible_id",prof,"objective","Seguir antecedente","matter","UPR")).id();
        run("profesional.proteccion","CASE_NOTE",cas,revision("case_record",cas),Map.of("body","Se redacto informe","effective_at",Instant.now().minusSeconds(1).toString()));
        long note=n(db.one("SELECT id FROM management_note WHERE case_id=?",cas),"id");UUID key=UUID.randomUUID();
        var file=new MockMultipartFile("file","informe.txt","text/plain","Version 1".getBytes());
        long doc=((Number)docs.upload("profesional.proteccion","CAS",cas,false,note,null,"INFORME","Antecedente de gestion",key,file).get("id")).longValue();
        assertEquals(doc,((Number)docs.upload("profesional.proteccion","CAS",cas,false,note,null,"INFORME","Antecedente de gestion",key,file).get("id")).longValue());
        docs.upload("profesional.proteccion","CAS",cas,false,note,doc,"INFORME","Version posterior",UUID.randomUUID(),new MockMultipartFile("file","informe-2.txt","text/plain","Version 2".getBytes()));
        assertEquals(2,db.count("SELECT COUNT(*) FROM document_item WHERE case_id=? AND note_id=?",cas,note));
        assertEquals(0L,boards.page(access.actor("ti"),"cas",0,25,"","all",null).get("total"));
        var own=boards.page(access.actor("profesional.proteccion"),"cas",0,1,"","mine",null);
        assertTrue(((Number)own.get("total")).longValue()>0);assertTrue(((List<?>)own.get("items")).size()<=1);
        assertThrows(Problem.class,()->docs.upload("profesional.upd","CAS",cas,false,note,null,"INFORME","No autorizado",UUID.randomUUID(),file));
        assertFalse(((List<?>)boards.dashboard(access.actor("ti")).get("sections")).isEmpty());
    }
}
