package cl.nexbyte.sigera;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * 0.5.0 (D1): rutina idempotente que suprime AUTORIDAD conservando la historia, avisos de administracion (log y
 * pantalla) y recuperacion auditada de una instancia sin TI permanente. Identidad tecnica sistema:actualizacion.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_supresion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.data=./target/test-supresion-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthoritySuppressionTest {
    static final String BASIS="AUTORIDAD. Rol suprimido en 0.5.0 (D1)",ACT="Resolucion exenta sintetica N 7 de recuperacion de TI";
    @Autowired Db db;@Autowired Bootstrap bootstrap;@Autowired TiRecovery recovery;@Autowired Queries queries;@Autowired Access access;@Autowired PasswordEncoder passwords;
    static long ugi,req;

    long staff(String user){return db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'x',?,FALSE)",user,"Persona sintetica "+user,ugi);}
    long grant(long user,String role,Long scope,long from,Long until,Long revoked){
        return db.insert("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,valid_until,revoked_at,basis) VALUES(?,?,?,?,?,?,'PRUEBA')",user,role,scope,from,until,revoked);
    }
    long id(String user){return n(db.one("SELECT id FROM staff WHERE username=?",user),"id");}
    Object revoked(long grant){return db.one("SELECT revoked_at FROM role_assignment WHERE id=?",grant).get("revoked_at");}
    long version(long user){return n(db.one("SELECT security_version FROM staff WHERE id=?",user),"security_version");}
    List<String> warnings(){return Personnel.warnings(db).stream().map(w->s(w,"code")).toList();}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> list(Map<String,Object> m,String key){return (List<Map<String,Object>>)m.get(key);}

    @Test @Order(1) void supresionIdempotenteConservaHistoriaYAvisa(CapturedOutput output){
        // Al arrancar sin datos la rutina ya dejo el marcador, sin crear la identidad tecnica.
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",Bootstrap.AUTHORITY_MARKER));
        assertEquals(0,db.count("SELECT COUNT(*) FROM staff WHERE username=?",Bootstrap.MAINTENANCE));
        db.update("INSERT INTO org_unit(code,name) VALUES('UGI','Gestion Institucional'),('UPR','Proteccion')");
        ugi=db.insert("INSERT INTO org_scope(code,name,unit_code,site_code) VALUES('UGI','Gestion Institucional','UGI','CENTRAL')");
        req=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('UPR-REQ','Requerimientos','UPR','REQ','CENTRAL')");
        long now=System.currentTimeMillis(),hour=3_600_000;
        long ti=staff("ti.sintetica");grant(ti,"TI",null,0,null,null);
        long holder=staff("autoridad.sintetica");
        long live=grant(holder,"AUTORIDAD",ugi,0,null,null),scheduled=grant(holder,"AUTORIDAD",req,now+hour,now+2*hour,null);
        long expired=grant(holder,"AUTORIDAD",req,0,now-hour,null),revokedBefore=grant(holder,"AUTORIDAD",ugi,0,null,123L);
        long mixed=staff("mixta.sintetica");grant(mixed,"TI",null,0,null,null);grant(mixed,"PROFESIONAL",req,0,null,null);
        long grants=db.count("SELECT COUNT(*) FROM role_assignment"),before=version(holder);

        // Instancia actualizada desde 0.4.0: el marcador aun no existe.
        db.update("DELETE FROM application_marker WHERE code=?",Bootstrap.AUTHORITY_MARKER);
        bootstrap.suppressAuthority();
        assertNotNull(revoked(live));assertNotNull(revoked(scheduled));
        assertNull(revoked(expired));assertEquals(123L,n(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",revokedBefore),"revoked_at"));
        assertEquals(grants,db.count("SELECT COUNT(*) FROM role_assignment"));
        assertEquals(before+1,version(holder));
        var system=db.one("SELECT * FROM staff WHERE username=?",Bootstrap.MAINTENANCE);long actor=n(system,"id");
        assertTrue(b(system,"technical"));assertFalse(b(system,"enabled"));assertEquals("!",s(system,"password_hash"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",actor));
        var events=db.rows("SELECT * FROM audit_event WHERE action='FUNCION_REVOCADA' ORDER BY id");
        assertEquals(2,events.size());
        for(var e:events){assertEquals(actor,n(e,"actor_id"));assertEquals(holder,n(e,"resource_id"));assertEquals("USUARIO",s(e,"resource_type"));assertEquals(BASIS,s(e,"note"));}
        assertEquals(Set.of(ugi,req),new HashSet<>(events.stream().map(e->n(e,"scope_id")).toList()));
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",Bootstrap.AUTHORITY_MARKER));

        // Idempotente: con marcador no hace nada; sin marcador tampoco repite (ya no hay concesiones vigentes).
        bootstrap.suppressAuthority();
        db.update("DELETE FROM application_marker WHERE code=?",Bootstrap.AUTHORITY_MARKER);
        bootstrap.suppressAuthority();
        assertEquals(2,db.count("SELECT COUNT(*) FROM audit_event WHERE action='FUNCION_REVOCADA'"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM staff WHERE username=?",Bootstrap.MAINTENANCE));
        assertEquals(before+1,version(holder));

        // La identidad tecnica no inicia sesion ni figura en listas ni en la administracion.
        assertThrows(Problem.class,()->access.actor(Bootstrap.MAINTENANCE));
        assertTrue(access.enabledActors().stream().noneMatch(a->a.id()==actor));
        var admin=queries.administration(access.actor("ti.sintetica"));
        assertTrue(list(admin,"users").stream().noneMatch(u->n(u,"id")==actor));

        // La cuenta que combina TI con otra funcion no se toca: se lista y se avisa (pantalla y log, sin datos personales).
        assertNull(db.one("SELECT revoked_at FROM role_assignment WHERE user_id=? AND role_code='PROFESIONAL'",mixed).get("revoked_at"));
        var conflicts=list(admin,"incompatibilities");
        assertEquals(1,conflicts.size());assertEquals(mixed,n(conflicts.getFirst(),"id"));assertEquals(List.of("PROFESIONAL","TI"),conflicts.getFirst().get("roles"));
        // 0.7.0 (D19): sin sedes ni cobertura del nivel central, las 16 regiones del catalogo quedan sin atencion.
        assertEquals(List.of("TI_INCOMPATIBLE","REGION_SIN_SEDE"),list(admin,"warnings").stream().map(w->s(w,"code")).toList());
        assertEquals(16L,n(list(admin,"warnings").getLast(),"count"));
        bootstrap.warnAdministration();
        assertTrue(output.getAll().contains("Administracion: TI_INCOMPATIBLE (cantidad 1)"));
        assertTrue(output.getAll().contains("Administracion: REGION_SIN_SEDE (cantidad 16)"));
        assertFalse(output.getAll().contains("mixta.sintetica"));
        // Una identidad tecnica alterada (habilitada) detiene la rutina: falla cerrado.
        db.update("UPDATE staff SET enabled=TRUE WHERE id=?",actor);
        assertThrows(IllegalStateException.class,()->Bootstrap.maintenance(db));
        db.update("UPDATE staff SET enabled=FALSE WHERE id=?",actor);
    }

    @Test @Order(2) void recuperacionDeTiAuditadaSoloSinTiPermanente(CapturedOutput output)throws Exception{
        long actor=Bootstrap.maintenance(db),events=db.count("SELECT COUNT(*) FROM audit_event");
        // Con una TI permanente no procede.
        var e=assertThrows(IllegalStateException.class,()->recovery.recover("nueva.ti","Nueva TI","UGI",ACT));
        assertTrue(e.getMessage().startsWith("La instancia conserva una cuenta TI"),e.getMessage());

        // Instancia sin TI permanente: la TI deshabilitada y con termino; la mixta con termino.
        long ti=id("ti.sintetica"),mixed=id("mixta.sintetica"),tomorrow=System.currentTimeMillis()+86_400_000L;
        db.update("UPDATE staff SET enabled=FALSE WHERE id=?",ti);
        db.update("UPDATE role_assignment SET valid_until=? WHERE user_id IN (?,?) AND role_code='TI'",tomorrow,ti,mixed);
        long termed=n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND role_code='TI'",ti),"id");
        assertTrue(warnings().contains("SIN_TI_PERMANENTE"));
        bootstrap.warnAdministration();
        assertTrue(output.getAll().contains("Administracion: SIN_TI_PERMANENTE"));

        // Rechazos sin cambios: fundamento corto, usuario invalido, cuenta con otras funciones (D2), cuenta nueva sin
        // nombre o con ambito inexistente, identidad tecnica.
        for(var bad:List.<org.junit.jupiter.api.function.Executable>of(
                ()->recovery.recover("nueva.ti","Nueva TI","UGI","corto"),
                ()->recovery.recover("x","Nueva TI","UGI",ACT),
                ()->recovery.recover("mixta.sintetica","","",ACT),
                ()->recovery.recover("nueva.ti","","UGI",ACT),
                ()->recovery.recover("nueva.ti","Nueva TI","NO-EXISTE",ACT)))
            assertThrows(IllegalStateException.class,bad);
        assertThrows(IllegalStateException.class,()->recovery.recover(Bootstrap.MAINTENANCE,"Nueva TI","UGI",ACT));
        assertEquals(events,db.count("SELECT COUNT(*) FROM audit_event"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM staff WHERE username='nueva.ti'"));

        // Cuenta existente deshabilitada: se habilita con clave temporal y cambio obligatorio; su TI con termino se
        // reemplaza por una permanente (sin periodos superpuestos).
        long version=version(ti);
        var r=recovery.recover("TI.Sintetica","",""," "+ACT+" ");
        assertEquals(ti,r.user());assertFalse(r.created());
        var account=db.one("SELECT * FROM staff WHERE id=?",ti);
        assertTrue(b(account,"enabled"));assertTrue(b(account,"must_change"));assertEquals(version+1,n(account,"security_version"));
        var csv=Files.readAllLines(r.credentials());
        assertEquals(2,csv.size());assertTrue(csv.get(1).startsWith("ti.sintetica,"));
        assertTrue(passwords.matches(csv.get(1).substring(csv.get(1).lastIndexOf(',')+1),s(account,"password_hash")));
        assertFalse(output.getAll().contains(csv.get(1).substring(csv.get(1).lastIndexOf(',')+1)));
        assertEquals(1,Personnel.permanentTi(db));assertFalse(warnings().contains("SIN_TI_PERMANENTE"));
        assertNotNull(revoked(termed));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='TI' AND revoked_at IS NULL AND valid_until IS NULL",ti));
        for(String action:List.of("CUENTA_ACTUALIZADA","CLAVE_TEMPORAL_REPUESTA","FUNCION_REVOCADA","FUNCION_OTORGADA","TI_RECUPERADA"))
            assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action=? AND actor_id=? AND note LIKE ?",action,actor,"%Recuperacion de TI. "+ACT),action);
        // Ya hay una TI permanente: una segunda recuperacion no procede.
        assertThrows(IllegalStateException.class,()->recovery.recover("nueva.ti","Nueva TI","UGI",ACT));
        // Cuenta solo deshabilitada con su TI permanente: se habilita sin otorgar otra TI.
        db.update("UPDATE staff SET enabled=FALSE WHERE id=?",ti);
        long granted=db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",ti);
        recovery.recover("ti.sintetica","","",ACT);
        assertEquals(granted,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",ti));
        assertTrue(b(db.one("SELECT enabled FROM staff WHERE id=?",ti),"enabled"));

        // Cuenta nueva (la persona TI ya no esta): se crea con su ambito y la misma auditoria.
        db.update("UPDATE role_assignment SET revoked_at=? WHERE user_id=? AND role_code='TI' AND revoked_at IS NULL",System.currentTimeMillis(),ti);
        var created=recovery.recover("nueva.ti","Nueva TI","UGI",ACT);
        assertTrue(created.created());assertNotEquals(r.credentials(),created.credentials());
        var fresh=db.one("SELECT * FROM staff WHERE id=?",created.user());
        assertEquals(ugi,n(fresh,"home_scope"));assertTrue(b(fresh,"must_change"));assertFalse(b(fresh,"technical"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='CUENTA_CREADA' AND actor_id=? AND resource_id=?",actor,created.user()));
        assertTrue(access.actor("nueva.ti").role(Roles.TI));
    }
}
