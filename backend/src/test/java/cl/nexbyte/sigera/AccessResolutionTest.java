package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/** La resolucion en lote (avisos, catalogo) debe coincidir con la resolucion por cuenta. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigeraaccess;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-access-${random.uuid}"})
@ActiveProfiles("test")
class AccessResolutionTest {
    @Autowired Workflow flow; @Autowired Access access; @Autowired Db db; @Autowired Queries queries;
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long revision(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}

    @Test void loteCoincideConCuentaYAvisosLleganAlSubrogante(){
        db.update("UPDATE staff SET must_change=FALSE");
        long root=scope("UPD"),titular=user("direccion.upd"),sub=user("profesional.upd");
        // Cartera personal del titular: al subrogarse conserva PROFESIONAL en ese ambito.
        long com=run("direccion.upd","COM_CREATE",null,null,Map.of("scope_id",root,"channel","TELEFONO","received_at",Instant.now().minusSeconds(60).toString(),"subject","Propio","body","Sintetico")).id();
        long req=run("direccion.upd","COM_ASSUME",com,revision("communication",com),Map.of("responsible_id",titular,"reason","Propio")).id();
        run("direccion.upd","REQ_CASE_CREATE",req,revision("requirement",req),Map.of("scope_id",root,"responsible_id",titular,"objective","Cartera","matter","UPD"));
        long designation=run("autoridad","DESIGNATE",null,null,Map.of("scope_id",root,"titular_id",titular,"substitute_id",sub,"reason","Predesignacion")).id();
        run("autoridad","SUBSTITUTE_ACTIVATE",designation,null,Map.of("from_at",Instant.now().minusSeconds(1).toString(),"until_at",Instant.now().plusSeconds(3600).toString(),"reason","Ausencia"));
        db.update("UPDATE staff SET enabled=FALSE WHERE username='gabinete'");

        var batch=access.enabledActors();
        assertEquals(db.count("SELECT COUNT(*) FROM staff WHERE enabled=TRUE"),batch.size());
        for(var b:batch){
            var single=access.actor(b.username());
            assertEquals(single.id(),b.id());
            assertEquals(new HashSet<>(single.grants()),new HashSet<>(b.grants()),b.username());
            assertEquals(single.substitutions(),b.substitutions(),b.username());
        }
        var acting=batch.stream().filter(x->x.id()==sub).findFirst().orElseThrow();
        var away=batch.stream().filter(x->x.id()==titular).findFirst().orElseThrow();
        assertTrue(acting.at("DIRECCION",root));assertFalse(away.head(root));assertTrue(away.professional(root));

        long incoming=run("distribucion","COM_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","CORREO","received_at",Instant.now().minusSeconds(30).toString(),"subject","Para UPD","body","Sintetico")).id();
        long sol=run("distribucion","COM_ROUTE",incoming,revision("communication",incoming),Map.of("scope_id",root,"reason","Competencia UPD")).id();
        String notice="SELECT COUNT(*) FROM inbox_notice WHERE user_id=? AND resource_type='SOL' AND resource_id=?";
        assertEquals(1,db.count(notice,sub,sol));
        assertEquals(0,db.count(notice,titular,sol));

        // El catalogo no ofrece funciones a cuentas deshabilitadas.
        @SuppressWarnings("unchecked") var people=(List<Map<String,Object>>)queries.catalog(access.actor("direccion.upr")).get("people");
        var disabled=people.stream().filter(p->n(p,"id")==user("gabinete")).findFirst().orElseThrow();
        assertEquals(List.of(),disabled.get("assignable_scopes"));
    }
}
