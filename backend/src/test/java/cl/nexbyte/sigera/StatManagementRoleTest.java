package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Funcion GESTION_ESTADISTICA (0.8.0, D43, gate E02): ambito opcional del grupo REPORTES, exige REPORTES en el mismo
 * alcance (GRANT_CREATE, PROFILE_SET sobre el estado final y GRANT_REVOKE de REPORTES) y es incompatible con TI en
 * ambos sentidos. La regla de la sesion es Roles.ruling. Todavia no habilita nada (E10 y E11).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_gestion_estadistica;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-gestion-estadistica-${random.uuid}"})
@ActiveProfiles("test")
class StatManagementRoleTest {
    static final String ROLE=Roles.GESTION_ESTADISTICA;
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;

    @BeforeEach void clean(){db.update("UPDATE staff SET must_change=FALSE");}
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long account(String username){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",username)==0)
            db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'x',?,FALSE)",username,"Cuenta "+username,scope("UEP"));
        return user(username);
    }
    /** PROFILE_SET exige la revision de la cuenta (staff.security_version); los demas comandos, ninguna. */
    Workflow.Result run(String type,Long id,Map<String,Object> data){
        Long rev=type.equals("PROFILE_SET")?n(db.one("SELECT security_version FROM staff WHERE id=?",id),"security_version"):null;
        return flow.run("ti",new Workflow.Command(UUID.randomUUID(),type,id,rev,data));
    }
    Problem refused(String type,Long id,Map<String,Object> data){return assertThrows(Problem.class,()->run(type,id,data));}
    static Map<String,Object> grant(String role,Long scope){var m=new LinkedHashMap<String,Object>();m.put("role",role);if(scope!=null)m.put("scope_id",scope);return m;}
    Map<String,Object> create(long user,String role,Long scope){
        var m=new LinkedHashMap<String,Object>(Map.of("user_id",user,"role",role,"from_at",Instant.now().minusSeconds(60).toString(),"reason","Designacion sintetica"));
        if(scope!=null)m.put("scope_id",scope);return m;
    }
    long grants(long user,String role){return db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code=? AND revoked_at IS NULL",user,role);}

    @Test void catalogoOpcionalDelGrupoReportes(){
        assertTrue(Roles.json().contains(Map.of("code",ROLE,"reach","OPCIONAL","group","REPORTES")));
        assertTrue(Roles.NEEDS_REPORTES.contains(ROLE));
        assertEquals(List.of(Roles.REPORTES_PERSONAS,Roles.EXPORTACION,ROLE),List.copyOf(Roles.NEEDS_REPORTES));
        assertFalse(Roles.readsRecords(ROLE),"no lee expedientes");assertFalse(Roles.opensIntake(ROLE));
    }

    @Test void exigeReportesEnElMismoAlcance(){
        long id=account("estudios.grant"),uep=scope("UEP");
        // Sin REPORTES, o con REPORTES en otro alcance, se rechaza sin escribir.
        assertEquals(Personnel.STATS_REQUIRES,refused("GRANT_CREATE",null,create(id,ROLE,null)).getMessage());
        run("GRANT_CREATE",null,create(id,Roles.REPORTES,uep));
        var other=refused("GRANT_CREATE",null,create(id,ROLE,null));
        assertEquals(409,other.status);assertEquals(Personnel.STATS_REQUIRES,other.getMessage());
        assertEquals(0,grants(id,ROLE));
        // Con REPORTES en el mismo alcance se concede y rige (Roles.ruling).
        assertTrue(Roles.ruling(access.actor("estudios.grant"),ROLE).isEmpty());
        run("GRANT_CREATE",null,create(id,ROLE,uep));
        var ruling=Roles.ruling(access.actor("estudios.grant"),ROLE);
        assertEquals(1,ruling.size());assertEquals(uep,ruling.getFirst().scope());
        // Revocar REPORTES con la funcion vigente en ese alcance se rechaza y nombra la dependiente.
        long reportes=n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND role_code='REPORTES' AND revoked_at IS NULL",id),"id");
        var revoke=refused("GRANT_REVOKE",reportes,Map.of("reason","Revoca"));
        assertEquals(409,revoke.status);assertTrue(revoke.getMessage().contains("Gestion estadistica"),revoke.getMessage());
        assertEquals(1,grants(id,Roles.REPORTES));
        // Revocada primero la dependiente, REPORTES se revoca.
        long stats=n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND role_code=? AND revoked_at IS NULL",id,ROLE),"id");
        run("GRANT_REVOKE",stats,Map.of("reason","Retiro"));run("GRANT_REVOKE",reportes,Map.of("reason","Retiro"));
        assertEquals(0,grants(id,Roles.REPORTES)+grants(id,ROLE));
    }

    @Test void perfilSobreElEstadoFinal(){
        long id=account("estudios.perfil"),uep=scope("UEP");
        var orphan=refused("PROFILE_SET",id,Map.of("grants",List.of(grant(Roles.REPORTES,uep),grant(ROLE,null)),"reason","Sin REPORTES institucional"));
        assertEquals(Personnel.STATS_REQUIRES,orphan.getMessage());
        run("PROFILE_SET",id,Map.of("grants",List.of(grant(Roles.REPORTES,null),grant(ROLE,null),grant(Roles.REPORTES,uep),grant(ROLE,uep)),"reason","Estudios"));
        assertEquals(2,Roles.ruling(access.actor("estudios.perfil"),ROLE).size());
        // Quitar REPORTES institucional dejando la dependiente se rechaza; quitar ambas se admite.
        assertEquals(Personnel.STATS_REQUIRES,refused("PROFILE_SET",id,Map.of("grants",List.of(grant(ROLE,null),grant(Roles.REPORTES,uep),grant(ROLE,uep)),"reason","Mal")).getMessage());
        run("PROFILE_SET",id,Map.of("grants",List.of(grant(Roles.REPORTES,uep),grant(ROLE,uep)),"reason","Solo UEP"));
        assertEquals(List.of(uep),Roles.ruling(access.actor("estudios.perfil"),ROLE).stream().map(Access.Grant::scope).toList());
    }

    @Test void incompatibleConTi(){
        long ti=user("ti"),id=account("estudios.ti");
        var self=refused("GRANT_CREATE",null,create(ti,ROLE,null));assertEquals(409,self.status);
        long other=account("ti.segunda");
        run("GRANT_CREATE",null,create(other,Roles.TI,null));
        // D2: el rechazo es por TI, antes de considerar REPORTES.
        assertEquals("Una cuenta con TI no puede recibir otras funciones.",refused("GRANT_CREATE",null,create(other,ROLE,null)).getMessage());
        // Y en sentido inverso: una cuenta con la funcion no recibe TI.
        run("GRANT_CREATE",null,create(id,Roles.REPORTES,null));run("GRANT_CREATE",null,create(id,ROLE,null));
        assertTrue(refused("GRANT_CREATE",null,create(id,Roles.TI,null)).getMessage().startsWith("TI es incompatible"));
        var mixed=refused("PROFILE_SET",id,Map.of("grants",List.of(grant(Roles.TI,null),grant(Roles.REPORTES,null),grant(ROLE,null)),"reason","Mezcla"));
        assertTrue(mixed.getMessage().startsWith("TI es incompatible"),mixed.getMessage());
        assertEquals(0,grants(ti,ROLE)+grants(other,ROLE)+grants(id,Roles.TI));
    }
}
