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

/** 0.5.0 (D2, D3): PROFILE_SET fija en un acto las funciones de una cuenta, con diferencia, validacion previa y un solo epoch. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigeraprofile;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-profile-${random.uuid}"})
@ActiveProfiles("test")
class ProfileSetTest {
    @Autowired Workflow flow; @Autowired Access access; @Autowired Db db;
    static final String PASSWORD="Clave-Temporal-Sintetica-2026";
    static final String STALE="El registro cambio. Actualiza la ficha antes de confirmar.";
    static final String NO_CHANGES="El perfil no tiene cambios.";
    static final String DISABLED="La cuenta esta deshabilitada: su perfil solo admite revocaciones. Habilitala antes de otorgar funciones.";
    static final String KEEP_TI="La instancia debe conservar al menos una cuenta TI habilitada, vigente y sin fecha de termino.";

    @BeforeEach void ready(){db.update("UPDATE staff SET must_change=FALSE");}
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long version(long user){return n(db.one("SELECT security_version FROM staff WHERE id=?",user),"security_version");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    static String in(long s){return Instant.now().plusSeconds(s).toString();}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return run(who,UUID.randomUUID(),type,id,rev,data);}
    Workflow.Result run(String who,UUID key,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(key,type,id,rev,data));}
    Problem refusal(String who,String type,Long id,Long rev,Map<String,Object> data){return assertThrows(Problem.class,()->run(who,type,id,rev,data));}

    /** Entrada del perfil: rol, ambito (codigo o null) y, opcionalmente, "from_at"/"until_at" en pares. */
    Map<String,Object> f(String role,String scope,String... period){
        var m=new HashMap<String,Object>(Map.of("role",role));
        if(scope!=null)m.put("scope_id",scope(scope));
        for(int i=0;i<period.length;i+=2)m.put(period[i],period[i+1]);
        return m;
    }
    Map<String,Object> profile(Object grants){return Map.of("grants",grants,"reason","Resolucion exenta sintetica de perfil");}
    Map<String,Object> profile(List<Map<String,Object>> grants){return profile((Object)grants);}
    Workflow.Result set(long user,List<Map<String,Object>> grants){return run("ti","PROFILE_SET",user,version(user),profile(grants));}
    Problem refuseSet(long user,List<Map<String,Object>> grants){return refusal("ti","PROFILE_SET",user,version(user),profile(grants));}

    /** Cuenta sintetica nueva con funciones concedidas una a una (GRANT_CREATE). */
    long account(String username,String... roleScope){
        long id=run("ti","STAFF_CREATE",null,null,Map.of("username",username,"name","Cuenta sintetica "+username,"scope_id",scope("UPR-REQ"),"password",PASSWORD,"reason","Alta sintetica")).id();
        db.update("UPDATE staff SET must_change=FALSE WHERE id=?",id);
        for(int i=0;i<roleScope.length;i+=2){
            var m=new HashMap<String,Object>(Map.of("user_id",id,"role",roleScope[i],"from_at",ago(60),"reason","Concesion sintetica"));
            if(roleScope[i+1]!=null)m.put("scope_id",scope(roleScope[i+1]));
            run("ti","GRANT_CREATE",null,null,m);
        }
        return id;
    }
    long grantId(long user,String role){return n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND role_code=? AND revoked_at IS NULL",user,role),"id");}
    long active(long user){return db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND revoked_at IS NULL",user);}
    long events(long user,String action){return db.count("SELECT COUNT(*) FROM audit_event WHERE action=? AND resource_type='USUARIO' AND resource_id=?",action,user);}
    /** Estado completo de las funciones y su rastro: debe quedar identico tras un rechazo. */
    List<Object> snapshot(long user){
        return List.of(db.rows("SELECT id,role_code,scope_id,valid_from,valid_until,revoked_at FROM role_assignment WHERE user_id=? ORDER BY id",user),
            db.count("SELECT COUNT(*) FROM audit_event WHERE resource_type='USUARIO' AND resource_id=?",user),version(user));
    }

    @Test void diferenciaEnUnSoloActoConIdempotenciaYRevision(){
        long acct=account("perfil.diferencia","ADMINISTRATIVO","UPR-REQ","PROFESIONAL","UPR-REQ");
        long administrative=grantId(acct,"ADMINISTRATIVO"),professional=grantId(acct,"PROFESIONAL"),before=version(acct);
        assertEquals(2,events(acct,"FUNCION_OTORGADA"));

        var grants=List.of(f("PROFESIONAL","UPR-REQ"),f("DISTRIBUIDOR",null),f("LECTURA","UPD"));
        var data=profile(grants);var key=UUID.randomUUID();
        var result=run("ti",key,"PROFILE_SET",acct,before,data);
        assertEquals("USUARIO",result.kind());assertEquals(acct,result.id());

        // Se conserva la misma fila, se revoca sin borrar y se otorga lo nuevo; un evento por cambio y un solo epoch.
        assertNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",professional).get("revoked_at"));
        assertNotNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",administrative).get("revoked_at"));
        assertEquals(3,active(acct));
        assertNull(db.one("SELECT scope_id FROM role_assignment WHERE user_id=? AND role_code='DISTRIBUIDOR'",acct).get("scope_id"));
        assertEquals(4,events(acct,"FUNCION_OTORGADA"));assertEquals(1,events(acct,"FUNCION_REVOCADA"));
        assertEquals(before+1,version(acct));
        assertEquals("ADMINISTRATIVO. Resolucion exenta sintetica de perfil",s(db.one("SELECT note FROM audit_event WHERE action='FUNCION_REVOCADA' AND resource_id=?",acct),"note"));
        var m=access.actor("perfil.diferencia");
        assertTrue(m.role("DISTRIBUIDOR")&&m.at("PROFESIONAL",scope("UPR-REQ"))&&m.at("LECTURA",scope("UPD")));
        assertFalse(m.at("ADMINISTRATIVO",scope("UPR-REQ")));

        // Idempotencia: la misma clave devuelve el resultado previo sin repetir efectos; con otros datos es conflicto.
        var state=snapshot(acct);
        assertEquals(result,run("ti",key,"PROFILE_SET",acct,before,data));
        assertEquals(state,snapshot(acct));
        assertEquals(409,assertThrows(Problem.class,()->run("ti",key,"PROFILE_SET",acct,before,profile(List.of(f("PROFESIONAL","UPR-REQ"))))).status);

        // Revision obsoleta o ausente: 409 sin cambios.
        assertEquals(STALE,refusal("ti","PROFILE_SET",acct,before,profile(List.of(f("PROFESIONAL","UPR-REQ")))).getMessage());
        assertEquals(STALE,refusal("ti","PROFILE_SET",acct,null,profile(List.of(f("PROFESIONAL","UPR-REQ")))).getMessage());
        assertEquals(state,snapshot(acct));

        // Mismo perfil (sin importar el orden): no hay cambios que registrar.
        assertEquals(NO_CHANGES,refuseSet(acct,List.of(f("LECTURA","UPD"),f("PROFESIONAL","UPR-REQ"),f("DISTRIBUIDOR",null))).getMessage());
        assertEquals(state,snapshot(acct));
    }

    @Test void reglaVioladaNoDejaCambiosParciales(){
        long acct=account("perfil.atomico","ADMINISTRATIVO","UPR-REQ","PROFESIONAL","UPR-REQ");
        var state=snapshot(acct);var keep=f("PROFESIONAL","UPR-REQ");
        // Cada perfil revocaria ADMINISTRATIVO y ademas viola una regla: nada debe quedar escrito.
        Map<String,Object[]> cases=new LinkedHashMap<>();
        cases.put("jefatura ya ocupada",new Object[]{409,List.of(keep,f("DIRECCION","UPR"))});
        cases.put("TI con otra funcion",new Object[]{409,List.of(keep,f("TI",null))});
        cases.put("entrada repetida",new Object[]{409,List.of(keep,f("PROFESIONAL","UPR-REQ"))});
        cases.put("rol suprimido",new Object[]{409,List.of(keep,f("AUTORIDAD","UPR"))});
        cases.put("rol inexistente",new Object[]{409,List.of(keep,f("SUPERUSUARIO",null))});
        cases.put("sin ambito",new Object[]{400,List.of(keep,f("LECTURA",null))});
        cases.put("termino vencido",new Object[]{409,List.of(keep,f("LECTURA","UPD","until_at",ago(60)))});
        cases.put("termino antes del inicio",new Object[]{409,List.of(keep,f("LECTURA","UPD","from_at",in(7200),"until_at",in(3600)))});
        for(var c:cases.entrySet()){
            @SuppressWarnings("unchecked") var grants=(List<Map<String,Object>>)c.getValue()[1];
            assertEquals(c.getValue()[0],refuseSet(acct,grants).status,c.getKey());
            assertEquals(state,snapshot(acct),c.getKey());
        }
        assertEquals(400,refusal("ti","PROFILE_SET",acct,version(acct),profile("PROFESIONAL")).status);
        assertEquals(400,refusal("ti","PROFILE_SET",acct,version(acct),Map.of("grants",List.of(keep))).status);

        // Solo TI, y nunca sobre su propia cuenta.
        for(String who:List.of("direccion.upr","auditoria","distribucion","autoridad"))
            assertEquals(403,refusal(who,"PROFILE_SET",acct,version(acct),profile(List.of(keep))).status,who);
        long ti=user("ti");var own=snapshot(ti);
        assertEquals(409,refusal("ti","PROFILE_SET",ti,version(ti),profile(List.of(f("TI",null)))).status);
        assertEquals(own,snapshot(ti));
        assertEquals(state,snapshot(acct));
    }

    @Test void tiEnUnSoloActoYPerfilVacio(){
        // D2 se evalua sobre el estado final: el mismo acto revoca lo operativo y otorga TI.
        long acct=account("perfil.ti","PROFESIONAL","UPR-REQ","ADMINISTRATIVO","UPR-REQ");
        set(acct,List.of(f("TI",null)));
        assertEquals(1,active(acct));assertTrue(access.actor("perfil.ti").role(Roles.TI));
        assertEquals(409,refuseSet(acct,List.of(f("TI",null),f("LECTURA","UPD"))).status);
        // Perfil vacio: revoca todo y la cuenta sigue existiendo (queda la TI permanente de la instancia).
        set(acct,List.of());
        assertEquals(0,active(acct));assertEquals(3,events(acct,"FUNCION_REVOCADA"));
        assertNotNull(db.one("SELECT id FROM staff WHERE id=?",acct));
    }

    @Test void vigenciaPorFuncion(){
        long acct=account("perfil.vigencia","PROFESIONAL","UPR-REQ");
        String until=in(86_400),later=in(172_800),from=in(3600);
        set(acct,List.of(f("PROFESIONAL","UPR-REQ"),f("LECTURA","UPD","until_at",until),f("LECTURA","UEP","from_at",from)));
        var lectura=db.one("SELECT id,valid_until FROM role_assignment WHERE user_id=? AND role_code='LECTURA' AND scope_id=? AND revoked_at IS NULL",acct,scope("UPD"));
        assertEquals(Instant.parse(until).toEpochMilli(),n(lectura,"valid_until"));
        var programmed=db.one("SELECT valid_from FROM role_assignment WHERE user_id=? AND scope_id=? AND revoked_at IS NULL",acct,scope("UEP"));
        assertEquals(Instant.parse(from).toEpochMilli(),n(programmed,"valid_from"));
        assertFalse(access.actor("perfil.vigencia").at("LECTURA",scope("UEP")));

        // Mismo termino e inicio: sin cambios. La programada sin from_at tambien coincide.
        assertEquals(NO_CHANGES,refuseSet(acct,List.of(f("PROFESIONAL","UPR-REQ"),f("LECTURA","UPD","until_at",until),f("LECTURA","UEP","from_at",from))).getMessage());
        assertEquals(NO_CHANGES,refuseSet(acct,List.of(f("PROFESIONAL","UPR-REQ"),f("LECTURA","UPD","until_at",until),f("LECTURA","UEP"))).getMessage());

        // Cambiar el termino revoca la funcion anterior y otorga la nueva; quitarlo la deja sin termino.
        set(acct,List.of(f("PROFESIONAL","UPR-REQ"),f("LECTURA","UPD","until_at",later),f("LECTURA","UEP","from_at",from)));
        assertNotNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",n(lectura,"id")).get("revoked_at"));
        assertEquals(Instant.parse(later).toEpochMilli(),n(db.one("SELECT valid_until FROM role_assignment WHERE user_id=? AND scope_id=? AND revoked_at IS NULL",acct,scope("UPD")),"valid_until"));
        set(acct,List.of(f("PROFESIONAL","UPR-REQ"),f("LECTURA","UPD"),f("LECTURA","UEP","from_at",from)));
        assertNull(db.one("SELECT valid_until FROM role_assignment WHERE user_id=? AND scope_id=? AND revoked_at IS NULL",acct,scope("UPD")).get("valid_until"));
        assertEquals(3,active(acct));
    }

    @Test void cuentaDeshabilitadaSoloAdmiteRevocaciones(){
        long acct=account("perfil.baja","PROFESIONAL","UPR-REQ","ADMINISTRATIVO","UPR-REQ");
        run("ti","STAFF_UPDATE",acct,null,Map.of("name","Cuenta dada de baja","enabled",false,"reason","Termino de funciones sintetico"));
        var state=snapshot(acct);
        // Agregar una funcion nueva (aunque tambien revoque otra) es rechazado con un mensaje claro y sin cambios.
        var refused=refuseSet(acct,List.of(f("PROFESIONAL","UPR-REQ"),f("LECTURA","UPD")));
        assertEquals(409,refused.status);assertEquals(DISABLED,refused.getMessage());
        assertEquals(DISABLED,refuseSet(acct,List.of(f("PROFESIONAL","UPR-REQ","until_at",in(3600)),f("ADMINISTRATIVO","UPR-REQ"))).getMessage());
        assertEquals(state,snapshot(acct));
        // Las revocaciones proceden, incluido el perfil vacio.
        set(acct,List.of(f("PROFESIONAL","UPR-REQ")));
        assertEquals(1,active(acct));
        set(acct,List.of());
        assertEquals(0,active(acct));assertEquals(2,events(acct,"FUNCION_REVOCADA"));
    }

    @Test void nuncaQuedaSinUnaCuentaTiPermanente(){
        long ti=user("ti"),tiGrant=grantId(ti,"TI"),now=System.currentTimeMillis();
        long other=account("perfil.ti.ultima");
        set(other,List.of(f("TI",null)));
        db.update("UPDATE role_assignment SET valid_until=? WHERE id=?",now+86_400_000L,tiGrant);
        try{
            assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='TI' AND revoked_at IS NULL AND valid_until IS NULL AND user_id<>?",other));
            var state=snapshot(other);
            assertEquals(KEEP_TI,refuseSet(other,List.of()).getMessage());
            assertEquals(KEEP_TI,refuseSet(other,List.of(f("TI",null,"until_at",in(3600)))).getMessage());
            assertEquals(state,snapshot(other));
        }finally{
            db.update("UPDATE role_assignment SET valid_until=NULL WHERE id=?",tiGrant);
        }
        set(other,List.of());
        assertEquals(0,active(other));
    }
}
