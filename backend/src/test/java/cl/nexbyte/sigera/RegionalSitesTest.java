package cl.nexbyte.sigera;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Sedes regionales (0.5.0, D7-D8, gate R01): estado PROYECTADA/ACTIVA/CERRADA, dotacion preparada que rige al activar,
 * cierre con bloqueos y lectura posterior, Coordinacion regional como DIRECCION (sin JEFATURA en sedes), conversion
 * idempotente de las JEFATURA existentes y destinos publicados en el catalogo = aceptados por su comando.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_sedes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-sedes-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegionalSitesTest {
    @Autowired Workflow flow; @Autowired Access access; @Autowired Db db; @Autowired Queries queries; @Autowired Bootstrap bootstrap;
    static final String PASSWORD="Clave-Temporal-Sintetica-2026",BASIS="Resolucion exenta sintetica";
    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    long rev(String table,long id){return n(db.one("SELECT revision FROM "+table+" WHERE id=?",id),"revision");}
    long version(long user){return n(db.one("SELECT security_version FROM staff WHERE id=?",user),"security_version");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    String refusal(String who,String type,Long id,Long rev,Map<String,Object> data){
        return assertThrows(Problem.class,()->run(who,type,id,rev,data)).getMessage();
    }
    Map<String,Object> reason(){return Map.of("reason",BASIS);}
    @SuppressWarnings("unchecked") List<Map<String,Object>> scopes(){return (List<Map<String,Object>>)queries.catalog(access.actor("ti")).get("scopes");}
    Map<String,Object> published(long id){return scopes().stream().filter(s->n(s,"id")==id).findFirst().orElseThrow();}
    long account(String username,long home){
        long id=run("ti","STAFF_CREATE",null,null,Map.of("username",username,"name","Cuenta sintetica "+username,"scope_id",home,"password",PASSWORD,"reason",BASIS)).id();
        db.update("UPDATE staff SET must_change=FALSE WHERE id=?",id);return id;
    }
    void profile(long user,List<Map<String,Object>> grants){run("ti","PROFILE_SET",user,version(user),Map.of("grants",grants,"reason",BASIS));}
    static Map<String,Object> at(String role,long scope){return Map.of("role",role,"scope_id",scope);}
    long site(String code,String region,String state){
        return run("ti","SCOPE_CREATE",null,null,Map.of("unit_code","REG","code",code,"name","Sede sintetica "+code,"site_code","SEDE-"+code,"regional",true,"region_code",region,"state",state,"reason",BASIS)).id();
    }
    long com(String subject){
        return run("distribucion","COM_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",ago(60),"subject",subject,"body","Relato sintetico")).id();
    }

    @Test @Order(1) void demoConCoordinacionComoDireccionYCatalogoDeDestinos(){
        db.update("UPDATE staff SET must_change=FALSE");
        long demo=scope("REG-DEMO"),coord=user("coordinacion.regional");
        var row=db.one("SELECT * FROM org_scope WHERE id=?",demo);
        assertEquals("05",s(row,"region_code"));assertEquals("ACTIVA",s(row,"state"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='DIRECCION' AND scope_id=? AND revoked_at IS NULL",coord,demo));
        assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment g JOIN org_scope s ON s.id=g.scope_id WHERE g.role_code='JEFATURA' AND s.regional=TRUE"));
        assertTrue(access.actor("coordinacion.regional").head(demo));
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",Bootstrap.REGIONAL_HEAD_MARKER));
        assertTrue(Personnel.warnings(db).stream().noneMatch(w->s(w,"code").equals("JEFATURA_EN_SEDE")));

        // Regiones en el orden del Formulario (de norte a sur) y nivel de cada ambito.
        var catalog=queries.catalog(access.actor("distribucion"));
        @SuppressWarnings("unchecked") var regions=(List<Map<String,Object>>)catalog.get("regions");
        assertEquals(16,regions.size());assertEquals("15",s(regions.getFirst(),"code"));assertEquals("12",s(regions.getLast(),"code"));
        assertEquals("REGIONAL",s(published(demo),"level"));assertEquals("CENTRAL",s(published(scope("UPR")),"level"));

        // Destinos de prueba no publicados: sede proyectada, sede cerrada (via V11: deshabilitada = CERRADA) y una unidad sin recepcion.
        long projected=site("REG-PROY","08","PROYECTADA");
        long closed=site("REG-CERR","09","ACTIVA");db.update("UPDATE org_scope SET state='CERRADA',enabled=FALSE WHERE id=?",closed);
        long def=run("ti","SCOPE_CREATE",null,null,Map.of("unit_code","DEF","code","DEF","name","Defensoria","site_code","CENTRAL","reason",BASIS)).id();
        var all=scopes();
        assertEquals(db.count("SELECT COUNT(*) FROM org_scope"),all.size(),"el catalogo publica todos los ambitos, tambien proyectados y cerrados");
        assertEquals("PROYECTADA",s(published(projected),"state"));assertEquals("CERRADA",s(published(closed),"state"));
        var destinations=all.stream().filter(s->!((List<?>)s.get("receives")).isEmpty()).map(s->s(s,"code")).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(List.of("REG-DEMO","UGI","UEP","UPD","UPR","UPR-REQ")),destinations);
        assertEquals(Queries.DESTINATION_OPERATIONS,published(demo).get("receives"));
        // Cada destino publicado lo acepta su comando (la SOL queda en ese mismo ambito); uno no publicado se rechaza.
        for(var s:all){
            long target=n(s,"id"),c=com("Destino "+s(s,"code"));
            if(((List<?>)s.get("receives")).isEmpty()){
                if(s(s,"area_code").isBlank())assertThrows(Problem.class,()->run("distribucion","COM_ROUTE",c,rev("communication",c),Map.of("scope_id",target,"reason",BASIS)),s(s,"code"));
            }else{
                long sol=run("distribucion","COM_ROUTE",c,rev("communication",c),Map.of("scope_id",target,"reason",BASIS)).id();
                assertEquals(target,n(db.one("SELECT destination_scope FROM solicitation WHERE id=?",sol),"destination_scope"),s(s,"code"));
                assertEquals(target,access.solDestination(new Input(Map.of("scope_id",target))).scope(),"flujos y entregas usan la misma regla: "+s(s,"code"));
            }
        }
        for(long rejected:List.of(projected,closed,def))
            assertThrows(Problem.class,()->access.solDestination(new Input(Map.of("scope_id",rejected))));
    }

    @Test @Order(2) void dotacionPreparadaRigeSoloAlActivar(){
        // Region obligatoria y valida en las sedes; ausente en el nivel central; solo una sede se crea proyectada.
        assertTrue(refusal("ti","SCOPE_CREATE",null,null,Map.of("unit_code","REG","code","REG-X","name","Sin region","site_code","SEDE-X","regional",true,"reason",BASIS)).contains("region"));
        assertTrue(refusal("ti","SCOPE_CREATE",null,null,Map.of("unit_code","REG","code","REG-X","name","Region","site_code","SEDE-X","regional",true,"region_code","99","reason",BASIS)).contains("region"));
        assertTrue(refusal("ti","SCOPE_CREATE",null,null,Map.of("unit_code","UPR","code","UPR-X","name","Central","site_code","CENTRAL","region_code","05","reason",BASIS)).contains("region"));
        assertTrue(refusal("ti","SCOPE_CREATE",null,null,Map.of("unit_code","UPR","code","UPR-X","name","Central","site_code","CENTRAL","state","PROYECTADA","reason",BASIS)).contains("proyectada"));
        assertTrue(refusal("ti","SCOPE_CREATE",null,null,Map.of("unit_code","REG","code","REG-DEMO","name","Repetido","site_code","SEDE-X","regional",true,"region_code","05","reason",BASIS)).contains("codigo"));

        long sur=site("REG-SUR","10","PROYECTADA");
        var row=db.one("SELECT * FROM org_scope WHERE id=?",sur);
        assertFalse(b(row,"enabled"));assertNull(row.get("opens_ms"));assertEquals("10",s(row,"region_code"));
        // TI prepara la dotacion: cuentas adscritas a la sede y funciones en ella, que todavia no rigen.
        long coord=account("coord.sur",sur),prof=account("prof.sur",sur);
        assertTrue(refusal("ti","GRANT_CREATE",null,null,Map.of("user_id",coord,"role","JEFATURA","scope_id",sur,"from_at",ago(60),"reason",BASIS)).contains("Jefatura"));
        assertTrue(refusal("ti","PROFILE_SET",coord,version(coord),Map.of("grants",List.of(at("JEFATURA",sur)),"reason",BASIS)).contains("Jefatura"));
        profile(coord,List.of(at("DIRECCION",sur),at("PROFESIONAL",sur)));
        run("ti","GRANT_CREATE",null,null,Map.of("user_id",prof,"role","PROFESIONAL","scope_id",sur,"from_at",ago(60),"reason",BASIS));
        assertFalse(access.actor("coord.sur").head(sur));assertFalse(access.actor("prof.sur").professional(sur));
        assertTrue(((List<?>)published(sur).get("receives")).isEmpty());
        long c=com("Antes de activar");
        assertThrows(Problem.class,()->run("distribucion","COM_ROUTE",c,rev("communication",c),Map.of("scope_id",sur,"reason",BASIS)));

        // Edicion con concurrencia optimista: nombre, region y apertura; sin cambios se rechaza.
        long r0=rev("org_scope",sur);
        assertTrue(refusal("ti","SCOPE_UPDATE",sur,r0+1,Map.of("name","Sede Sur","region_code","10","reason",BASIS)).contains("cambio"));
        assertTrue(refusal("ti","SCOPE_UPDATE",sur,r0,Map.of("name",s(row,"name"),"region_code","10","reason",BASIS)).contains("no tiene cambios"));
        run("ti","SCOPE_UPDATE",sur,r0,Map.of("name","Sede sintetica Sur","region_code","14","reason",BASIS));
        assertEquals("14",s(db.one("SELECT region_code FROM org_scope WHERE id=?",sur),"region_code"));
        assertEquals(r0+1,rev("org_scope",sur));
        assertTrue(refusal("ti","SCOPE_CLOSE",sur,rev("org_scope",sur),reason()).contains("activa"));
        assertTrue(refusal("ti","SCOPE_ACTIVATE",scope("UPR"),rev("org_scope",scope("UPR")),reason()).contains("sedes regionales"));

        // Activar: la dotacion preparada rige desde ese momento y sus sesiones se renuevan.
        long before=version(coord);
        assertTrue(refusal("ti","SCOPE_ACTIVATE",sur,rev("org_scope",sur)-1,reason()).contains("cambio"));
        run("ti","SCOPE_ACTIVATE",sur,rev("org_scope",sur),reason());
        row=db.one("SELECT * FROM org_scope WHERE id=?",sur);
        assertEquals("ACTIVA",s(row,"state"));assertTrue(b(row,"enabled"));assertNotNull(row.get("opens_ms"));
        assertEquals(before+1,version(coord));
        assertTrue(access.actor("coord.sur").head(sur));assertTrue(access.actor("prof.sur").professional(sur));
        assertEquals(Queries.DESTINATION_OPERATIONS,published(sur).get("receives"));
        assertTrue(refusal("ti","SCOPE_ACTIVATE",sur,rev("org_scope",sur),reason()).contains("proyectada"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='AMBITO_ACTIVADO' AND resource_id=?",sur));
    }

    @Test @Order(3) void cerrarSedeExigeNadaPendienteYSiguesLegible(){
        long sur=scope("REG-SUR"),coord=user("coord.sur"),prof=user("prof.sur");
        long c=com("COM para la sede Sur");
        long sol=run("distribucion","COM_ROUTE",c,rev("communication",c),Map.of("scope_id",sur,"reason",BASIS)).id();
        String blocked=refusal("ti","SCOPE_CLOSE",sur,rev("org_scope",sur),reason());
        assertTrue(blocked.contains("1 SOL pendientes")&&blocked.contains("funciones vigentes o programadas")&&blocked.contains("2 cuentas con adscripcion principal"),blocked);

        // Gestion regional completa: la Coordinacion (DIRECCION) asume, abre un Caso y cierra; el profesional cierra su Caso BAJA.
        long req=run("coord.sur","SOL_ASSUME",sol,rev("solicitation",sol),Map.of("responsible_id",prof,"reason","Asume la sede")).id();
        assertEquals(sur,n(db.one("SELECT principal_scope FROM requirement WHERE id=?",req),"principal_scope"));
        blocked=refusal("ti","SCOPE_CLOSE",sur,rev("org_scope",sur),reason());
        assertTrue(blocked.contains("1 REQ abiertos")&&!blocked.contains("SOL pendientes"),blocked);
        run("coord.sur","REQ_CLASSIFY",req,rev("requirement",req),Map.of("complexity","BAJA","reason","Clasificacion sintetica"));
        long cas=run("coord.sur","REQ_CASE_CREATE",req,rev("requirement",req),Map.of("scope_id",sur,"responsible_id",prof,"objective","Seguimiento regional","matter","UPR")).id();
        assertTrue(refusal("ti","SCOPE_CLOSE",sur,rev("org_scope",sur),reason()).contains("1 Casos abiertos"));
        run("prof.sur","CASE_CLOSE",cas,rev("case_record",cas),Map.of("reason","Gestion concluida"));
        run("coord.sur","REQ_CLOSE",req,rev("requirement",req),Map.of("reason","Cierre expreso"));

        // Quedan las funciones y las adscripciones: TI las revoca y traslada las cuentas.
        blocked=refusal("ti","SCOPE_CLOSE",sur,rev("org_scope",sur),reason());
        assertFalse(blocked.contains("REQ")||blocked.contains("Casos"),blocked);
        profile(coord,List.of());profile(prof,List.of());
        long central=scope("UPR-REQ");
        for(long u:List.of(coord,prof))run("ti","STAFF_UPDATE",u,null,Map.of("name","Cuenta trasladada","enabled",true,"scope_id",central,"reason",BASIS));
        assertEquals(central,n(db.one("SELECT home_scope FROM staff WHERE id=?",prof),"home_scope"));
        assertTrue(refusal("ti","SCOPE_CLOSE",sur,rev("org_scope",sur)+1,reason()).contains("cambio"));
        run("ti","SCOPE_CLOSE",sur,rev("org_scope",sur),reason());
        var row=db.one("SELECT * FROM org_scope WHERE id=?",sur);
        assertEquals("CERRADA",s(row,"state"));assertFalse(b(row,"enabled"));

        // Una sede cerrada no recibe nada: ni destinos, ni cuentas, ni funciones, ni ediciones.
        assertEquals("CERRADA",s(published(sur),"state"));assertTrue(((List<?>)published(sur).get("receives")).isEmpty());
        long other=com("Despues del cierre");
        assertThrows(Problem.class,()->run("distribucion","COM_ROUTE",other,rev("communication",other),Map.of("scope_id",sur,"reason",BASIS)));
        assertTrue(refusal("ti","PROFILE_SET",prof,version(prof),Map.of("grants",List.of(at("PROFESIONAL",sur)),"reason",BASIS)).contains("cerrado"));
        assertTrue(refusal("ti","STAFF_CREATE",null,null,Map.of("username","nueva.sur","name","Nueva","scope_id",sur,"password",PASSWORD,"reason",BASIS)).contains("cerrado"));
        assertTrue(refusal("ti","SCOPE_UPDATE",sur,rev("org_scope",sur),Map.of("name","Otro nombre","region_code","14","reason",BASIS)).contains("cerrado"));
        assertTrue(refusal("ti","SCOPE_ACTIVATE",sur,rev("org_scope",sur),reason()).contains("proyectada"));

        // Sigue legible: ficha COM/SOL/REQ/Caso e historial para la consulta institucional y la COM para quien la registro.
        long reader=account("lector.sur",central);profile(reader,List.of(Map.of("role",Roles.CONSULTA_INSTITUCIONAL)));
        var lector=access.actor("lector.sur");
        var reqDetail=queries.detail(lector,"REQ",req);
        assertEquals("CERRADO",s(reqDetail,"state"));
        assertFalse(((List<?>)reqDetail.get("history")).isEmpty());
        assertTrue(((List<?>)reqDetail.get("case_scopes")).isEmpty());
        var casDetail=queries.detail(lector,"CAS",cas);
        assertEquals("CERRADO",s(casDetail,"state"));assertFalse(((List<?>)casDetail.get("history")).isEmpty());
        var solDetail=queries.detail(lector,"SOL",sol);
        assertTrue(s(solDetail,"receiving_authority").startsWith("Coordinacion regional"));assertEquals(false,solDetail.get("can_decide"));
        assertEquals(c,n(queries.detail(lector,"COM",c),"id"));
        assertEquals(c,n(queries.detail(access.actor("distribucion"),"COM",c),"id"));
    }

    @Test @Order(4) void conversionDeJefaturasRegionalesIdempotenteConHistoria(){
        long now=System.currentTimeMillis(),week=7L*24*3600*1000;
        long a=site("REG-CONV-A","04","ACTIVA"),b=site("REG-CONV-B","06","ACTIVA");
        long x=account("titular.a",a),y=account("subrogante.a",a),z=account("programada.a",a),w=account("jefatura.b",b),v=account("direccion.b",b);
        // Concesiones heredadas de 0.4.0 (por SQL: hoy JEFATURA en sedes se rechaza).
        long gx=sql(x,"JEFATURA",a,now-week,now+week),old=sql(x,"JEFATURA",a,now-3*week,now-2*week);
        long gz=sql(z,"JEFATURA",a,now+2*week,now+4*week);sql(y,"PROFESIONAL",a,now-week,null);
        long gw=sql(w,"JEFATURA",b,now-week,null);sql(v,"DIRECCION",b,now-week,null);
        // Subrogancia vigente sobre la JEFATURA del titular, activada por TI.
        long d=run("ti","DESIGNATE",null,null,Map.of("scope_id",a,"titular_id",x,"substitute_id",y,"reason",BASIS)).id();
        long sub=run("ti","SUBSTITUTE_ACTIVATE",d,null,Map.of("from_at",ago(60),"until_at",Instant.now().plusSeconds(86400).toString(),"reason",BASIS)).id();
        assertTrue(access.actor("subrogante.a").head(a));
        assertTrue(Personnel.warnings(db).stream().anyMatch(e->s(e,"code").equals("JEFATURA_EN_SEDE")&&n(e,"count")==3));

        db.update("DELETE FROM application_marker WHERE code=?",Bootstrap.REGIONAL_HEAD_MARKER);
        long events=db.count("SELECT COUNT(*) FROM audit_event"),vx=version(x),vy=version(y),vw=version(w);
        bootstrap.convertRegionalHeads();
        long actor=user(Bootstrap.MAINTENANCE);
        // Vigente: revocada (no borrada) y reemplazada por DIRECCION con el mismo periodo.
        assertNotNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",gx).get("revoked_at"));
        var nx=db.one("SELECT * FROM role_assignment WHERE user_id=? AND role_code='DIRECCION' AND scope_id=?",x,a);
        assertEquals(now-week,n(nx,"valid_from"));assertEquals(now+week,n(nx,"valid_until"));assertEquals(actor,n(nx,"granted_by"));
        // Programada: mismo inicio y termino. Vencida: intacta.
        var nz=db.one("SELECT * FROM role_assignment WHERE user_id=? AND role_code='DIRECCION' AND scope_id=?",z,a);
        assertEquals(now+2*week,n(nz,"valid_from"));assertEquals(now+4*week,n(nz,"valid_until"));
        assertNotNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",gz).get("revoked_at"));
        assertNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",old).get("revoked_at"));
        // La subrogancia sigue rigiendo, ahora sobre la DIRECCION; el titular no ejerce mientras dura.
        assertEquals(n(nx,"id"),n(db.one("SELECT grant_id FROM substitution_scope WHERE substitution_id=?",sub),"grant_id"));
        var substitute=access.actor("subrogante.a");
        assertTrue(substitute.at("DIRECCION",a)&&substitute.head(a));assertFalse(access.actor("titular.a").head(a));
        assertEquals(vx+1,version(x));assertEquals(vy+1,version(y));
        // Superpuesta con una DIRECCION en la misma sede: no se convierte y queda como aviso.
        assertNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",gw).get("revoked_at"));assertEquals(vw,version(w));
        assertTrue(Personnel.warnings(db).stream().anyMatch(e->s(e,"code").equals("JEFATURA_EN_SEDE")&&n(e,"count")==1));
        // Historia: 2 revocadas + 2 otorgadas + 1 subrogancia, todas por la identidad tecnica.
        assertEquals(events+5,db.count("SELECT COUNT(*) FROM audit_event"));
        assertEquals(5,db.count("SELECT COUNT(*) FROM audit_event WHERE id>? AND actor_id=?",maxEventBefore(events),actor));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='SUBROGANCIA_ACTUALIZADA' AND resource_id=?",sub));

        // Idempotente: con el marcador no hace nada; sin el, solo queda la superpuesta, que no se toca.
        long after=db.count("SELECT COUNT(*) FROM audit_event"),grants=db.count("SELECT COUNT(*) FROM role_assignment");
        bootstrap.convertRegionalHeads();
        db.update("DELETE FROM application_marker WHERE code=?",Bootstrap.REGIONAL_HEAD_MARKER);
        bootstrap.convertRegionalHeads();
        assertEquals(after,db.count("SELECT COUNT(*) FROM audit_event"));assertEquals(grants,db.count("SELECT COUNT(*) FROM role_assignment"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",Bootstrap.REGIONAL_HEAD_MARKER));

        // El rechazo de JEFATURA en sedes aplica solo a concesiones nuevas: reenviarla sin cambios no falla.
        profile(w,List.of(at("JEFATURA",b),at("PROFESIONAL",b)));
        assertNull(db.one("SELECT revoked_at FROM role_assignment WHERE id=?",gw).get("revoked_at"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='PROFESIONAL' AND scope_id=? AND revoked_at IS NULL",w,b));
    }
    /**
     * Region sugerida (R03a): el servidor la entrega para proponer la sede en el selector de destino. Formulario: la del
     * NNA y, si falta, la del adulto; ficha de primera atencion: su region de atencion; solo codigos del catalogo.
     */
    @Test @Order(5) void regionSugeridaParaProponerLaSede()throws Exception{
        db.update("UPDATE staff SET must_change=FALSE");
        var reader=access.actor("distribucion");
        // Campos tal como los proyecta ExternalProjection desde el vector NNA 1.1 del productor (nna-region 13).
        var entrega=JsonMapper.builder().build().readTree(Files.readString(Path.of("src/test/resources/smr/entrega-sintetica-1.1.json"))).path("comunicacion");
        var campos=ExternalProjection.of(entrega,Instant.now()).fields().stream().filter(f->f.group().equals("CAMPO")).toList();
        assertTrue(campos.stream().anyMatch(f->f.key().equals("nna-region")&&"13".equals(f.value())));
        long nna=com("Formulario NNA sintetico");campos.forEach(f->field(nna,f.key(),f.value()));
        assertEquals("13",regionOf(reader,nna));
        long adult=com("Formulario adulto sintetico");field(adult,"adult-region","05");
        assertEquals("05",regionOf(reader,adult));
        // Con ambas prevalece la del NNA; un codigo fuera del catalogo no se propone y cede a la otra region.
        long both=com("Ambas regiones");field(both,"adult-region","05");field(both,"nna-region","08");
        assertEquals("08",regionOf(reader,both));
        long fallback=com("Region NNA invalida");field(fallback,"nna-region","99");field(fallback,"adult-region","05");
        assertEquals("05",regionOf(reader,fallback));
        long invalid=com("Sin region valida");field(invalid,"nna-region","Valparaiso");
        assertNull(regionOf(reader,invalid));
        assertNull(regionOf(reader,com("COM manual")));

        // La SOL la entrega en su comunicacion, para redirigir o redistribuir con la misma propuesta.
        long sol=run("distribucion","COM_ROUTE",adult,rev("communication",adult),Map.of("scope_id",scope("REG-DEMO"),"reason",BASIS)).id();
        @SuppressWarnings("unchecked") var communication=(Map<String,Object>)queries.detail(access.actor("coordinacion.regional"),"SOL",sol).get("communication");
        assertEquals("05",communication.get("suggested_region"));

        // Ficha 1.1: region de atencion; sin region o con un texto historico de la ficha 1.0, no hay propuesta.
        assertEquals("13",attentionRegion(Map.of("schema_version","1.1","region","13","commune","Ñuñoa")));
        assertNull(attentionRegion(Map.of("schema_version","1.1")));
        assertNull(attentionRegion(Map.of("schema_version","1.0","region","Santiago Centro")));
    }
    void field(long com,String key,String value){
        db.update("INSERT INTO external_field(communication_id,field_group,person_key,item,field_key,value_text) VALUES(?,'CAMPO',NULL,0,?,?)",com,key,value);
    }
    String regionOf(Access.Actor a,long com){return (String)queries.detail(a,"COM",com).get("suggested_region");}
    @SuppressWarnings("unchecked") String attentionRegion(Map<String,Object> extra){
        var form=new LinkedHashMap<String,Object>(Map.of("modality","GENERAL","motive","Consulta sintetica de persona adulta","course","EN_ATENCION","people",List.of()));
        form.putAll(extra);
        long req=run("administrativo","ATTENTION_CREATE",null,null,Map.of("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL","received_at",ago(60),"form",form)).id();
        return (String)((Map<String,Object>)queries.detail(access.actor("administrativo"),"REQ",req).get("attention")).get("suggested_region");
    }
    long sql(long user,String role,long scope,long from,Long until){
        return db.insert("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,valid_until,basis) VALUES(?,?,?,?,?,?)",user,role,scope,from,until,"Concesion sintetica de 0.4.0");
    }
    long maxEventBefore(long count){return n(db.one("SELECT id FROM audit_event ORDER BY id LIMIT 1 OFFSET ?",count-1),"id");}
}
