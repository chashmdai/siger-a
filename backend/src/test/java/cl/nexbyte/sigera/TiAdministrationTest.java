package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/** 0.5.0 (D1, D2): TI administra funciones y subrogancias, no combina funciones y no lee expedientes. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigerati;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-ti-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TiAdministrationTest {
    @Autowired Workflow flow; @Autowired Access access; @Autowired Db db; @Autowired Queries queries; @Autowired BoardQueries boards; @Autowired Personnel personnel;
    static final String PASSWORD="Clave-Temporal-Sintetica-2026";
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    static String in(long s){return Instant.now().plusSeconds(s).toString();}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    int status(String who,String type,Long id,Long rev,Map<String,Object> data){
        try{run(who,type,id,rev,data);return 200;}catch(Problem p){return p.status;}
    }
    String refusal(String who,String type,Long id,Long rev,Map<String,Object> data){
        return assertThrows(Problem.class,()->run(who,type,id,rev,data)).getMessage();
    }
    static final String KEEP_TI="La instancia debe conservar al menos una cuenta TI habilitada, vigente y sin fecha de termino.";
    int read(Runnable r){try{r.run();return 200;}catch(Problem p){return p.status;}}
    Map<String,Object> grant(long user,String role,Long scope){
        var m=new HashMap<String,Object>(Map.of("user_id",user,"role",role,"from_at",ago(60),"reason","Resolucion exenta sintetica"));
        if(scope!=null)m.put("scope_id",scope);
        return m;
    }
    long grantId(long user,String role){return n(db.one("SELECT id FROM role_assignment WHERE user_id=? AND role_code=? AND revoked_at IS NULL",user,role),"id");}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> list(Map<String,Object> m,String key){return (List<Map<String,Object>>)m.get(key);}

    @Test @Order(1) void tiAdministraFuncionesYSubroganciasSinLeerExpedientes(){
        db.update("UPDATE staff SET must_change=FALSE");
        long reqScope=scope("UPR-REQ"),upd=scope("UPD"),ti=user("ti");

        // Expedientes sinteticos: COM -> SOL -> REQ -> Caso -> derivacion pendiente.
        long com=run("distribucion","COM_CREATE",null,null,Map.of("scope_id",reqScope,"channel","TELEFONO","received_at",ago(60),"subject","Ingreso para UPR","body","Relato sintetico")).id();
        long sol=run("distribucion","COM_ROUTE",com,rev("communication",com),Map.of("scope_id",scope("UPR"),"reason","Competencia UPR")).id();
        long req=run("direccion.upr","SOL_ASSUME",sol,rev("solicitation",sol),Map.of("responsible_id",user("profesional.proteccion"),"reason","Asuncion sintetica")).id();
        run("direccion.upr","REQ_CLASSIFY",req,rev("requirement",req),Map.of("complexity","BAJA","reason","Clasificacion sintetica"));
        long cas=run("direccion.upr","REQ_CASE_CREATE",req,rev("requirement",req),Map.of("scope_id",scope("UPR-PD"),"responsible_id",user("profesional.proteccion"),"objective","Seguimiento sintetico","matter","UPR")).id();
        run("direccion.upr","FLOW_REQUEST",req,rev("requirement",req),Map.of("kind","DERIVACION","case_id",cas,"target_scope",scope("UPD-EDU"),"objective","Destino sintetico","reason","Competencia de otra unidad"));
        long flowId=n(db.one("SELECT id FROM flow_request WHERE requirement_id=?",req),"id");

        // Sin TI, ninguna operacion de administracion procede.
        long demoDesignation=n(db.one("SELECT id FROM substitution_designation ORDER BY id LIMIT 1"),"id");
        long target=user("profesional"),targetGrant=grantId(target,"PROFESIONAL");
        Map<String,Object[]> admin=new LinkedHashMap<>();
        admin.put("STAFF_CREATE",new Object[]{null,null,Map.of("username","sin.permiso","name","Sin permiso","scope_id",reqScope,"password",PASSWORD,"reason","x")});
        admin.put("STAFF_UPDATE",new Object[]{target,null,Map.of("name","Cambio","enabled",true,"reason","x")});
        admin.put("STAFF_RESET",new Object[]{target,null,Map.of("password",PASSWORD,"reason","x")});
        admin.put("GRANT_CREATE",new Object[]{null,null,grant(target,"LECTURA",reqScope)});
        admin.put("GRANT_REVOKE",new Object[]{targetGrant,null,Map.of("reason","x")});
        admin.put("SCOPE_CREATE",new Object[]{null,null,Map.of("unit_code","UPR","code","UPR-X","name","Ambito x","site_code","CENTRAL","reason","x")});
        long regDemo=scope("REG-DEMO");
        admin.put("SCOPE_UPDATE",new Object[]{regDemo,0L,Map.of("name","Cambio","region_code","05","reason","x")});
        admin.put("SCOPE_ACTIVATE",new Object[]{regDemo,0L,Map.of("reason","x")});
        admin.put("SCOPE_CLOSE",new Object[]{regDemo,0L,Map.of("reason","x")});
        admin.put("SCOPE_COVERAGE",new Object[]{regDemo,0L,Map.of("regions",List.of("03"),"reason","x")});
        admin.put("CENTRAL_COVERAGE",new Object[]{0L,0L,Map.of("regions",List.of("13"),"reason","x")});
        admin.put("DESIGNATE",new Object[]{null,null,Map.of("scope_id",upd,"titular_id",user("direccion.upd"),"substitute_id",user("profesional.upd"),"reason","x")});
        admin.put("SUBSTITUTE_ACTIVATE",new Object[]{demoDesignation,null,Map.of("from_at",ago(1),"until_at",in(3600),"reason","x")});
        admin.put("DOC_RELEASE",new Object[]{1L,null,Map.of("reason","x")});
        admin.put("INTEGRATION_PAUSE",new Object[]{2L,0L,Map.of("reason","x")});
        admin.put("INTEGRATION_RESUME",new Object[]{2L,0L,Map.of("reason","x")});
        admin.put("MAIL_RESOLVE",new Object[]{1L,null,Map.of("decision","DESCARTAR","reason","x")});
        admin.put("HOLIDAY_ADD",new Object[]{null,null,Map.of("day","2026-12-25","name","Navidad","reason","x")});
        admin.put("HOLIDAY_IMPORT",new Object[]{null,null,Map.of("lines","2026-12-25 Navidad","reason","x")});
        admin.put("HOLIDAY_REMOVE",new Object[]{1L,null,Map.of("reason","x")});
        for(String who:List.of("gabinete","direccion.upd","auditoria","distribucion"))
            for(var e:admin.entrySet()){var v=e.getValue();@SuppressWarnings("unchecked") var data=(Map<String,Object>)v[2];
                assertEquals(403,status(who,e.getKey(),(Long)v[0],(Long)v[1],data),who+" "+e.getKey());}
        assertEquals(0,db.count("SELECT COUNT(*) FROM staff WHERE username='sin.permiso'"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM territorial_coverage"));
        // Vista previa del efecto territorial (0.7.0, D19): solo TI; 403 al resto, con cualquier tipo de cambio.
        for(String who:List.of("gabinete","direccion.upd","auditoria","distribucion","coordinacion.regional"))
            for(var body:List.<Map<String,Object>>of(Map.of("kind","CIERRE","scope_id",regDemo),Map.of("kind","COBERTURA","scope_id",regDemo,"regions",List.of("03")),
                    Map.of("kind","REGION","scope_id",regDemo,"region_code","06"),Map.of("kind","NIVEL_CENTRAL","regions",List.of("13"))))
                assertEquals(403,read(()->personnel.territorialPreview(access.actor(who),new Input(body))),who+" "+body.get("kind"));

        // TI crea y edita una cuenta y le concede Distribucion + Administrativo + Profesional.
        long multi=run("ti","STAFF_CREATE",null,null,Map.of("username","multi.funcion","name","Multifuncion sintetica","scope_id",reqScope,"password",PASSWORD,"reason","Alta sintetica")).id();
        run("ti","STAFF_UPDATE",multi,null,Map.of("name","Multifuncion sintetica editada","enabled",true,"reason","Correccion de nombre"));
        db.update("UPDATE staff SET must_change=FALSE WHERE id=?",multi);
        run("ti","GRANT_CREATE",null,null,grant(multi,"DISTRIBUIDOR",null));
        run("ti","GRANT_CREATE",null,null,grant(multi,"ADMINISTRATIVO",reqScope));
        run("ti","GRANT_CREATE",null,null,grant(multi,"PROFESIONAL",reqScope));
        var m=access.actor("multi.funcion");
        assertTrue(m.role("DISTRIBUIDOR")&&m.at("ADMINISTRATIVO",reqScope)&&m.at("PROFESIONAL",reqScope));
        assertNull(db.one("SELECT scope_id FROM role_assignment WHERE user_id=? AND role_code='DISTRIBUIDOR'",multi).get("scope_id"));
        assertEquals(3,db.count("SELECT COUNT(*) FROM audit_event WHERE action='FUNCION_OTORGADA' AND resource_id=?",multi));

        // Reglas de concesion: sin autoconcesion, sin periodos superpuestos, una jefatura por ambito, sin roles suprimidos.
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(ti,"AUDITORIA",reqScope)));
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(multi,"PROFESIONAL",reqScope)));
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(multi,"DISTRIBUIDOR",null)));
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(multi,"JEFATURA",reqScope)));
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(multi,"AUTORIDAD",reqScope)));
        // El termino debe ser posterior al inicio y a la fecha actual (como PROFILE_SET); uno pasado no crea nada.
        long lectura=db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='LECTURA'",multi);
        for(String end:List.of(ago(30),ago(0))){
            var past=grant(multi,"LECTURA",reqScope);past.put("from_at",ago(3600));past.put("until_at",end);
            assertEquals("Periodo invalido para LECTURA: el termino debe ser posterior al inicio y a la fecha actual.",refusal("ti","GRANT_CREATE",null,null,past));
        }
        var early=grant(multi,"LECTURA",reqScope);early.put("from_at",in(7200));early.put("until_at",in(3600));
        assertEquals(409,status("ti","GRANT_CREATE",null,null,early));
        assertEquals(lectura,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='LECTURA'",multi));

        // D2: TI es incompatible con cualquier otra funcion, en ambos sentidos.
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(multi,"TI",null)));
        long backup=run("ti","STAFF_CREATE",null,null,Map.of("username","ti.respaldo","name","TI respaldo sintetica","scope_id",scope("UGI"),"password",PASSWORD,"reason","Alta sintetica")).id();
        run("ti","GRANT_CREATE",null,null,grant(backup,"TI",null));
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(backup,"PROFESIONAL",reqScope)));
        assertEquals(409,status("ti","GRANT_CREATE",null,null,grant(backup,"DISTRIBUIDOR",null)));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",backup));

        // Revocacion: conserva la fila, no repite y no aplica a la propia cuenta.
        long administrative=grantId(multi,"ADMINISTRATIVO");
        run("ti","GRANT_REVOKE",administrative,null,Map.of("reason","Termino de destinacion sintetica"));
        assertNotNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",administrative).get("revoked_at"));
        assertEquals(409,status("ti","GRANT_REVOKE",administrative,null,Map.of("reason","Otra vez")));
        assertEquals(409,status("ti","GRANT_REVOKE",grantId(ti,"TI"),null,Map.of("reason","Propia")));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='FUNCION_REVOCADA' AND resource_id=?",multi));
        assertFalse(access.actor("multi.funcion").at("ADMINISTRATIVO",reqScope));

        // Varios distribuidores vigentes reciben el aviso de una COM por clasificar.
        long pending=run("administrativo","COM_CREATE",null,null,Map.of("scope_id",reqScope,"channel","CORREO","received_at",ago(30),"subject","Por clasificar","body","Relato sintetico")).id();
        for(long d:List.of(user("distribucion"),multi))
            assertEquals(1,db.count("SELECT COUNT(*) FROM inbox_notice WHERE user_id=? AND resource_type='COM' AND resource_id=?",d,pending));

        // Subrogancias: TI predesigna, activa y termina; el titular termina la suya; otra persona no.
        long titular=user("direccion.upd"),sub=user("profesional.upd");
        long designation=run("ti","DESIGNATE",null,null,Map.of("scope_id",upd,"titular_id",titular,"substitute_id",sub,"reason","Resolucion sintetica de predesignacion")).id();
        long first=run("ti","SUBSTITUTE_ACTIVATE",designation,null,Map.of("from_at",ago(1),"until_at",in(3600),"reason","Feriado legal sintetico")).id();
        assertTrue(access.actor("profesional.upd").at("DIRECCION",upd));
        assertEquals(1,db.count("SELECT COUNT(*) FROM inbox_notice WHERE user_id=? AND resource_type='SUBROGANCIA' AND resource_id=?",sub,first));
        assertEquals(403,status("profesional.difusion","SUBSTITUTE_END",first,rev("substitution",first),Map.of("reason","x")));
        run("direccion.upd","SUBSTITUTE_END",first,rev("substitution",first),Map.of("reason","Retorno anticipado"));
        assertFalse(access.actor("profesional.upd").at("DIRECCION",upd));
        long second=run("ti","SUBSTITUTE_ACTIVATE",designation,null,Map.of("from_at",ago(1),"until_at",in(7200),"reason","Comision de servicio sintetica")).id();
        long scheduled=run("ti","SUBSTITUTE_ACTIVATE",n(db.one("SELECT id FROM substitution_designation WHERE scope_id=?",scope("REG-DEMO")),"id"),null,
            Map.of("from_at",in(86400),"until_at",in(172800),"reason","Periodo programado sintetico")).id();

        // Vistas de administracion.
        var workspace=queries.workspace(access.actor("ti"));
        assertTrue(list(workspace,"designations").size()>=2);
        assertTrue(list(workspace,"substitutions").stream().anyMatch(x->n(x,"id")==second&&s(x,"state").equals("ACTIVA")));
        assertTrue(list(workspace,"substitutions").stream().anyMatch(x->n(x,"id")==scheduled&&s(x,"state").equals("PROGRAMADA")));
        var forTi=queries.administration(access.actor("ti"));
        assertTrue(list(forTi,"grants").stream().anyMatch(g->n(g,"id")==administrative&&g.get("revoked_at")!=null));
        // Instancia sana: sin incompatibilidades (la rutina de supresion no encontro AUTORIDAD en las semillas) y, como
        // unico aviso, REGION_SIN_SEDE (0.7.0, D19): la demo solo tiene REG-DEMO (05) y ninguna cobertura del nivel central.
        assertEquals(List.of("REGION_SIN_SEDE"),list(forTi,"warnings").stream().map(w->s(w,"code")).toList());
        assertEquals(15L,n(list(forTi,"warnings").getFirst(),"count"));assertTrue(list(forTi,"incompatibilities").isEmpty());
        assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='AUTORIDAD'"));
        assertFalse(list(forTi,"users").isEmpty());assertFalse(list(forTi,"designations").isEmpty());assertFalse(list(forTi,"substitutions").isEmpty());
        var forAudit=queries.administration(access.actor("auditoria"));
        assertTrue(list(forAudit,"users").isEmpty());assertTrue(list(forAudit,"grants").isEmpty());
        assertTrue(list(forAudit,"audit").stream().anyMatch(e->s(e,"action").equals("SUBROGANCIA_ACTIVADA")));
        assertTrue(list(forAudit,"audit").stream().anyMatch(e->s(e,"action").equals("FUNCION_REVOCADA")));
        assertEquals(403,read(()->queries.administration(access.actor("direccion.upd"))));
        assertEquals(403,read(()->queries.administration(access.actor("gabinete"))));
        var dashboard=list(boards.dashboard(access.actor("ti")),"sections");
        assertEquals(List.of("technical"),dashboard.stream().map(x->s(x,"id")).toList());
        var metrics=list(dashboard.getFirst(),"metrics");
        assertEquals(1L,metrics.stream().filter(x->s(x,"label").equals("Subrogancias activas")).findFirst().orElseThrow().get("count"));
        assertEquals(1L,metrics.stream().filter(x->s(x,"label").equals("Subrogancias programadas")).findFirst().orElseThrow().get("count"));
        var codes=list(queries.catalog(access.actor("ti")),"roles").stream().map(r->s(r,"code")).toList();
        assertTrue(codes.contains("TI")&&codes.contains("DISTRIBUIDOR")&&!codes.contains("AUTORIDAD"));

        // Despues de administrar, TI sigue sin acceso a expedientes: 403 en fichas y bandejas vacias.
        var tiActor=access.actor("ti");
        for(var d:List.of(Map.entry("COM",com),Map.entry("SOL",sol),Map.entry("REQ",req),Map.entry("CAS",cas),Map.entry("FLUJO",flowId),Map.entry("COM",pending)))
            assertEquals(403,read(()->queries.detail(tiActor,d.getKey(),d.getValue())),d.getKey());
        for(String kind:List.of("com","sol","req","cas","flujos","atenciones","tareas"))
            assertEquals(0L,boards.page(tiActor,kind,0,25,"","all",null).get("total"),kind);
        assertTrue(boards.page(access.actor("distribucion"),"com",0,25,"","all",null).get("total") instanceof Long t&&t>0);
    }

    @Test @Order(2) void nuncaQuedaSinUnaCuentaTiPermanente(){
        db.update("UPDATE staff SET must_change=FALSE");
        long ti=user("ti"),now=System.currentTimeMillis(),tiGrant=grantId(ti,"TI");
        // Escenario fijo: la cuenta que opera tiene TI con termino; solo "ti.ultima" tiene TI permanente.
        db.update("UPDATE role_assignment SET revoked_at=? WHERE role_code='TI' AND revoked_at IS NULL AND user_id<>?",now,ti);
        long last=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES('ti.ultima','TI ultima sintetica','test-non-login',?,FALSE)",scope("UGI"));
        long lastGrant=db.insert("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,0,'PRUEBA')",last);
        db.update("UPDATE role_assignment SET valid_until=? WHERE id=?",now+86_400_000L,tiGrant);
        try{
            assertEquals(KEEP_TI,refusal("ti","GRANT_REVOKE",lastGrant,null,Map.of("reason","Dejaria la instancia sin TI")));
            assertNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",lastGrant).get("revoked_at"));
            assertEquals(KEEP_TI,refusal("ti","STAFF_UPDATE",last,null,Map.of("name","TI ultima sintetica","enabled",false,"reason","Dejaria la instancia sin TI")));
            assertTrue(b(db.one("SELECT enabled FROM staff WHERE id=?",last),"enabled"));
            run("ti","STAFF_UPDATE",last,null,Map.of("name","TI ultima renombrada","enabled",true,"reason","Edicion sin efecto en TI"));

            // Sin ninguna TI permanente, solo procede la concesion que la repone.
            db.update("UPDATE role_assignment SET valid_until=? WHERE id=?",now+86_400_000L,lastGrant);
            long person=user("profesional.uep");
            assertEquals(KEEP_TI,refusal("ti","GRANT_CREATE",null,null,grant(person,"LECTURA",scope("UEP"))));
            assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='LECTURA'",person));
            long fresh=run("ti","STAFF_CREATE",null,null,Map.of("username","ti.nueva","name","TI nueva sintetica","scope_id",scope("UGI"),"password",PASSWORD,"reason","Alta sintetica")).id();
            assertEquals(KEEP_TI,refusal("ti","GRANT_CREATE",null,null,Map.of("user_id",fresh,"role","TI","from_at",ago(60),"until_at",in(3600),"reason","Temporal no basta")));
            run("ti","GRANT_CREATE",null,null,grant(fresh,"TI",null));
            run("ti","GRANT_REVOKE",lastGrant,null,Map.of("reason","Ya existe otra TI permanente"));
            run("ti","GRANT_CREATE",null,null,grant(person,"LECTURA",scope("UEP")));
        }finally{
            db.update("UPDATE role_assignment SET valid_until=NULL WHERE id=?",tiGrant);
        }
    }
}
