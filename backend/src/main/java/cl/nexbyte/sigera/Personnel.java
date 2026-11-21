package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * TI administra el sistema (0.5.0, D1): cuentas, ambitos, concesion y revocacion de funciones, predesignacion y
 * subrogancias, con el acto institucional como fundamento obligatorio. TI no tiene otras funciones (D2).
 */
@Service
public class Personnel {
    private final Db db;private final Access access;private final PasswordEncoder passwords;private final Integrations integrations;private final MailOutbox mail;
    private final Documents documents;private final Holidays holidays;
    public Personnel(Db db,Access access,PasswordEncoder passwords,Integrations integrations,MailOutbox mail,Documents documents,Holidays holidays){this.db=db;this.access=access;this.passwords=passwords;this.integrations=integrations;this.mail=mail;this.documents=documents;this.holidays=holidays;}
    public Workflow.Result run(Actor a,String type,long id,Long revision,Input in){
        switch(type){
            case "STAFF_CREATE" -> {
                ti(a);String username=in.text("username",64,true).toLowerCase(Locale.ROOT);
                require(username.matches("[a-z0-9][a-z0-9._-]{2,63}"),"Usuario: 3 a 64 caracteres sin espacios.");
                long scope=in.id("scope_id");access.staffableScope(scope);String password=password(in.text("password",72,true));
                long user=db.insert("INSERT INTO staff(username,name,email,password_hash,home_scope) VALUES(?,?,?,?,?)",username,in.text("name",160,true),in.text("email",200,false),passwords.encode(password),scope);
                audit(a,"CUENTA_CREADA","USUARIO",user,null,in.reason());return Workflow.done("USUARIO",user);
            }
            case "STAFF_UPDATE" -> {
                ti(a);require(id!=a.id()||in.flag("enabled"),"No puedes desactivar tu propia sesion.");
                var account=db.one("SELECT id,home_scope FROM staff WHERE id=? AND technical=FALSE",id);
                // Adscripcion principal opcional (0.5.0): permite trasladar cuentas antes de cerrar una sede.
                Long home=in.optionalId("scope_id"),previous=nullableId(account,"home_scope");if(home!=null&&!home.equals(previous))access.staffableScope(home);
                db.update("UPDATE staff SET name=?,email=?,enabled=?,home_scope=?,security_version=security_version+1 WHERE id=?",in.text("name",160,true),in.text("email",200,false),in.flag("enabled"),home==null?previous:home,id);
                // Un traslado deja en la historia el codigo del ambito anterior y del nuevo (sin datos de la persona).
                String moved=home!=null&&!home.equals(previous)?"Adscripcion: "+scopeCode(previous)+" -> "+scopeCode(home)+". ":"";
                keepTi();audit(a,"CUENTA_ACTUALIZADA","USUARIO",id,null,moved+in.reason());return Workflow.done("USUARIO",id);
            }
            case "STAFF_RESET" -> {
                ti(a);db.one("SELECT id FROM staff WHERE id=? AND technical=FALSE",id);String pass=password(in.text("password",72,true));
                db.update("UPDATE staff SET password_hash=?,must_change=TRUE,security_version=security_version+1 WHERE id=?",passwords.encode(pass),id);
                audit(a,"CLAVE_TEMPORAL_REPUESTA","USUARIO",id,null,in.reason());return Workflow.done("USUARIO",id);
            }
            case "GRANT_CREATE" -> {
                ti(a);long user=in.id("user_id");require(user!=a.id(),"No puedes otorgarte funciones.");access.user(user);
                var role=Roles.require(in.text("role",32,true));
                Long scope=role.scoped()?Long.valueOf(in.id("scope_id")):role.optional()?in.optionalId("scope_id"):null;if(scope!=null)grantable(role.code(),scope);
                long from=in.instant("from_at",true),until=in.instant("until_at",false),end=until==0?Long.MAX_VALUE:until;
                require(until==0||until>Math.max(from,System.currentTimeMillis()),"Periodo invalido para "+role.code()+": el termino debe ser posterior al inicio y a la fecha actual.");
                List<Object> same=new ArrayList<>(List.of(user,role.code()));if(scope!=null)same.add(scope);same.add(end);same.add(from);
                require(db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code=? AND "+(scope==null?"scope_id IS NULL":"scope_id=?")+" AND revoked_at IS NULL AND valid_from<? AND (valid_until IS NULL OR valid_until>?)",same.toArray())==0,"La persona ya tiene esa funcion en un periodo superpuesto.");
                tiCompatible(user,role.code());
                if(Roles.NEEDS_REPORTES.contains(role.code()))require(liveIn(user,Roles.REPORTES,scope)>0,needsReportes(role.code()));
                if(HEADS.contains(role.code()))headFree(scope,user,from,end);
                long grant=insertGrant(a,user,role.code(),scope,from,until==0?null:until,in.text("reason",1000,true));
                epoch(user);keepTi();return Workflow.done("PERMISO",grant);
            }
            case "PROFILE_SET" -> {return profile(a,id,revision,in);}
            case "GRANT_REVOKE" -> {
                ti(a);var grant=db.one("SELECT * FROM role_assignment WHERE id=?",id);
                require(n(grant,"user_id")!=a.id(),"No modifiques tus propias funciones.");require(grant.get("revoked_at")==null,"La funcion ya fue revocada.");
                if(s(grant,"role_code").equals(Roles.REPORTES)&&liveIn(n(grant,"user_id"),Roles.REPORTES,nullableId(grant,"scope_id"))<=1)
                    for(var dependent:List.of(Roles.REPORTES_PERSONAS,Roles.EXPORTACION))
                        require(liveIn(n(grant,"user_id"),dependent,nullableId(grant,"scope_id"))==0,"Revoca primero "+(dependent.equals(Roles.EXPORTACION)?"Exportacion":"Reportes por persona")+" en ese alcance: exige Reportes en el mismo alcance.");
                db.update("UPDATE role_assignment SET revoked_at=? WHERE id=? AND revoked_at IS NULL",System.currentTimeMillis(),id);epoch(n(grant,"user_id"));keepTi();
                audit(a,"FUNCION_REVOCADA","USUARIO",n(grant,"user_id"),nullableId(grant,"scope_id"),s(grant,"role_code")+". "+in.reason());return Workflow.done("PERMISO",id);
            }
            case "SCOPE_CREATE" -> {
                ti(a);String unit=in.choice("unit_code","UPR","UPD","UEP","UGI","REG","DAI","GAB","DEF","AUD");String code=in.text("code",48,true).toUpperCase(Locale.ROOT);
                require(code.matches("[A-Z0-9_-]{2,48}"),"Codigo de ambito invalido.");
                require(db.count("SELECT COUNT(*) FROM org_scope WHERE code=?",code)==0,"Ya existe un ambito con ese codigo.");
                boolean regional=in.flag("regional");String region=region(in,regional);
                String state=in.text("state",12,false);if(state.isEmpty())state=Access.ACTIVE;
                require(state.equals(Access.ACTIVE)||state.equals(Access.PROJECTED),"Estado inicial invalido: ACTIVA o PROYECTADA.");
                require(regional||state.equals(Access.ACTIVE),"Solo una sede regional puede crearse proyectada.");
                long opens=in.instant("opens_at",false);Long opensMs=null;
                if(opens>0)opensMs=opens;else if(regional&&state.equals(Access.ACTIVE))opensMs=System.currentTimeMillis();
                long scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional,region_code,state,enabled,opens_ms) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    code,in.text("name",160,true),unit,in.text("area_code",48,false),in.text("site_code",48,true),regional,region,state,state.equals(Access.ACTIVE),opensMs);
                audit(a,"AMBITO_CREADO","AMBITO",scope,scope,(regional?"Sede "+state+", region "+region+". ":"")+in.reason());return Workflow.done("AMBITO",scope);
            }
            case "SCOPE_UPDATE" -> {
                ti(a);var s=db.one("SELECT * FROM org_scope WHERE id=?",id);Db.revision(s,revision);
                require(!s(s,"state").equals(Access.CLOSED),"El ambito esta cerrado: no se edita.");
                String name=in.text("name",160,true),region=region(in,b(s,"regional"));long opens=in.instant("opens_at",false);Long opensMs=opens>0?opens:null;
                require(!name.equals(s(s,"name"))||!Objects.equals(region,s.get("region_code"))||!Objects.equals(opensMs,nullableId(s,"opens_ms")),"El ambito no tiene cambios.");
                db.update("UPDATE org_scope SET name=?,region_code=?,opens_ms=?,revision=revision+1 WHERE id=?",name,region,opensMs,id);
                audit(a,"AMBITO_ACTUALIZADO","AMBITO",id,id,in.reason());return Workflow.done("AMBITO",id);
            }
            case "SCOPE_ACTIVATE" -> {
                ti(a);var s=site(id,revision);
                require(s(s,"state").equals(Access.PROJECTED),"Solo una sede proyectada se activa.");
                require(AdultIdentity.validRegion(s(s,"region_code")),"Indica la region de la sede antes de activarla.");
                db.update("UPDATE org_scope SET state=?,enabled=TRUE,opens_ms=COALESCE(opens_ms,?),revision=revision+1 WHERE id=?",Access.ACTIVE,System.currentTimeMillis(),id);
                // La dotacion preparada empieza a regir: las sesiones de esas cuentas se renuevan.
                staffedIn(id).forEach(this::epoch);
                audit(a,"AMBITO_ACTIVADO","AMBITO",id,id,in.reason());return Workflow.done("AMBITO",id);
            }
            case "SCOPE_CLOSE" -> {
                ti(a);var s=site(id,revision);
                require(s(s,"state").equals(Access.ACTIVE),"Solo una sede activa se cierra.");
                var blockers=closeBlockers(db,id);
                require(blockers.isEmpty(),"No se puede cerrar la sede: "+String.join("; ",blockers)+".");
                db.update("UPDATE org_scope SET state=?,enabled=FALSE,revision=revision+1 WHERE id=?",Access.CLOSED,id);
                audit(a,"AMBITO_CERRADO","AMBITO",id,id,in.reason());return Workflow.done("AMBITO",id);
            }
            case "DESIGNATE" -> {
                ti(a);long scope=in.id("scope_id"),titular=in.id("titular_id"),substitute=in.id("substitute_id");access.scope(scope);
                require(titular!=substitute&&a.id()!=substitute,"Personas incompatibles para la designacion.");
                require(access.user(titular).head(scope),"El titular no ejerce jefatura en ese ambito.");access.substituteEligible(substitute,scope);
                long d=db.insert("INSERT INTO substitution_designation(scope_id,titular_id,substitute_id,basis) VALUES(?,?,?,?)",scope,titular,substitute,in.text("reason",1000,true));
                audit(a,"SUBROGANTE_PREDESIGNADO","SUBROGANCIA",d,scope,in.reason());return Workflow.done("DESIGNACION",d);
            }
            case "SUBSTITUTE_ACTIVATE" -> {
                var d=db.one("SELECT * FROM substitution_designation WHERE id=?",id);ti(a);long scope=n(d,"scope_id"),sub=n(d,"substitute_id");access.scope(scope);
                require(b(d,"enabled")&&a.id()!=sub&&a.id()!=n(d,"titular_id"),"TI debe ser independiente del titular y del subrogante, y la designacion debe estar habilitada.");
                access.substituteEligible(sub,scope);require(access.user(n(d,"titular_id")).head(scope),"El titular no tiene una jefatura vigente.");
                long from=in.instant("from_at",true),until=in.instant("until_at",true),now=System.currentTimeMillis();
                require(until>from&&until>now,"Indica una vigencia finita, futura o vigente.");
                require(db.count("SELECT COUNT(*) FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE d.scope_id=? AND x.ended_ms IS NULL AND x.from_ms<? AND x.until_ms>?",scope,until,Math.max(from,now))==0,"Existe una subrogancia superpuesta en ese ambito.");
                long x=db.insert("INSERT INTO substitution(designation_id,activated_by,from_ms,until_ms,reason) VALUES(?,?,?,?,?)",id,a.id(),from,until,in.text("reason",2000,true));
                var grants=access.functionGrants(n(d,"titular_id"),scope);
                require(!grants.isEmpty(),"La funcion titular ya no esta vigente.");
                for(var grant:grants){
                    long child=n(grant,"scope_id");
                    require(db.count("SELECT COUNT(*) FROM substitution_scope ss JOIN substitution x ON x.id=ss.substitution_id WHERE ss.scope_id=? AND x.id<>? AND x.ended_ms IS NULL AND x.from_ms<? AND x.until_ms>?",child,x,until,Math.max(from,now))==0,"Hay otra subrogancia sobre una de las areas de esta funcion.");
                    db.update("INSERT INTO substitution_scope(substitution_id,grant_id,scope_id,role_code) VALUES(?,?,?,?)",x,n(grant,"id"),child,s(grant,"role_code"));
                }
                epoch(sub);epoch(n(d,"titular_id"));audit(a,"SUBROGANCIA_ACTIVADA","SUBROGANCIA",x,scope,in.reason());
                access.notice(sub,"TI registro tu subrogancia durante el periodo indicado en el acto institucional.","SUBROGANCIA",x);
                access.notice(n(d,"titular_id"),"Se registro una subrogancia de tu funcion; tu cartera profesional no se transfiere.","SUBROGANCIA",x);
                return Workflow.done("SUBROGANCIA",x);
            }
            case "SUBSTITUTE_END" -> {
                var x=db.one("SELECT x.*,d.scope_id,d.titular_id,d.substitute_id FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE x.id=?",id);Db.revision(x,revision);
                long scope=n(x,"scope_id");if(a.id()!=n(x,"titular_id"))ti(a);require(x.get("ended_ms")==null,"Ya fue terminada.");
                db.update("UPDATE substitution SET ended_ms=?,ended_by=?,revision=revision+1 WHERE id=?",System.currentTimeMillis(),a.id(),id);
                epoch(n(x,"substitute_id"));epoch(n(x,"titular_id"));audit(a,"SUBROGANCIA_TERMINADA","SUBROGANCIA",id,scope,in.reason());
                access.notice(n(x,"titular_id"),"Retornan tus facultades; revisa los pendientes. No se cerro ningun Caso automaticamente.","SUBROGANCIA",id);return Workflow.done("SUBROGANCIA",id);
            }
            // Liberacion de cuarentena: analisis antivirus confiable previo (o, solo en instancias de pruebas, revision manual).
            case "DOC_RELEASE" -> {return documents.release(a,id,in);}
            // Operacion de integraciones: TI pausa o reanuda y decide sobre correos ambiguos. No da acceso a expedientes.
            case "INTEGRATION_PAUSE","INTEGRATION_RESUME" -> {return integrations.control(a,id,revision,type.equals("INTEGRATION_PAUSE"),in.text("reason",1000,true));}
            case "MAIL_RESOLVE" -> {return mail.resolve(a,id,in.choice("decision","REENVIAR","DESCARTAR"),in.reason());}
            // Calendario habil (G09): feriados nacionales y regionales, con fundamento; retirar borra y deja la historia en auditoria.
            case "HOLIDAY_ADD","HOLIDAY_IMPORT","HOLIDAY_REMOVE" -> {return holidays.run(a,type,id,in);}
            default -> throw new Problem(400,"Operacion de administracion desconocida.");
        }
    }
    static final Set<String> HEADS=Set.of("JEFATURA","DIRECCION");
    static final String REPORT_PEOPLE_REQUIRES="Reportes por persona exige la funcion Reportes en el mismo alcance.";
    static final String EXPORT_REQUIRES="Exportacion exige la funcion Reportes en el mismo alcance.";
    /** Mensaje de la dependencia de REPORTES (Roles.NEEDS_REPORTES). */
    static String needsReportes(String role){return role.equals(Roles.EXPORTACION)?EXPORT_REQUIRES:REPORT_PEOPLE_REQUIRES;}
    /** Funciones vigentes o programadas de la cuenta con ese rol y alcance (nulo = institucional). */
    private long liveIn(long user,String role,Long scope){
        List<Object> args=new ArrayList<>(List.of(user,role,System.currentTimeMillis()));if(scope!=null)args.add(scope);
        return db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code=? AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?) AND "+(scope==null?"scope_id IS NULL":"scope_id=?"),args.toArray());
    }
    static final String REGIONAL_HEAD_REJECTED="Las sedes regionales no tienen Jefatura: la Coordinacion regional se registra como Direccion de la sede (D7).";
    /** Concesion nueva en un ambito: ACTIVA o PROYECTADA (dotacion preparada), nunca CERRADA; sin JEFATURA en sedes (D7). */
    void grantable(String role,long scope){
        var s=access.staffableScope(scope);
        require(!(role.equals("JEFATURA")&&b(s,"regional")),REGIONAL_HEAD_REJECTED);
    }
    /** Region del Formulario: obligatoria en sedes regionales, ausente en el nivel central. */
    private static String region(Input in,boolean regional){
        String region=in.text("region_code",4,false);
        if(regional)require(AdultIdentity.validRegion(region),"Indica la region de la sede (codigo del Formulario).");
        else require(region.isEmpty(),"Solo las sedes regionales tienen region.");
        return regional?region:null;
    }
    /** Sede regional con revision vigente: activar y cerrar solo aplican a sedes (D8). */
    private Map<String,Object> site(long id,Long revision){
        var s=db.one("SELECT * FROM org_scope WHERE id=?",id);Db.revision(s,revision);
        require(b(s,"regional"),"Solo las sedes regionales se activan o cierran.");
        return s;
    }
    private String scopeCode(Long scope){return scope==null?"sin adscripcion":s(db.one("SELECT code FROM org_scope WHERE id=?",scope),"code");}
    /** Cuentas con funciones vigentes o programadas en el ambito. */
    private List<Long> staffedIn(long scope){
        return db.rows("SELECT DISTINCT user_id FROM role_assignment WHERE scope_id=? AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?)",scope,System.currentTimeMillis()).stream().map(r->n(r,"user_id")).toList();
    }
    /**
     * Lo que impide cerrar una sede (D8): nada abierto ni pendiente que quede sin quien lo resuelva, y ninguna funcion
     * ni cuenta adscrita. Solo cantidades, sin datos de personas.
     */
    static List<String> closeBlockers(Db db,long scope){
        long now=System.currentTimeMillis();var out=new ArrayList<String>();
        blocker(out,"REQ abiertos",db.count("SELECT COUNT(*) FROM requirement WHERE principal_scope=? AND state<>'CERRADO'",scope));
        blocker(out,"Casos abiertos",db.count("SELECT COUNT(*) FROM case_record WHERE scope_id=? AND state<>'CERRADO'",scope));
        blocker(out,"SOL pendientes",db.count("SELECT COUNT(*) FROM solicitation WHERE destination_scope=? AND state='PENDIENTE'",scope));
        blocker(out,"COM sin encaminar",db.count("SELECT COUNT(*) FROM communication WHERE origin_scope=? AND state='NUEVA'",scope));
        blocker(out,"SOL devueltas de COM de la sede",db.count("SELECT COUNT(*) FROM solicitation s JOIN communication c ON c.id=s.communication_id WHERE c.origin_scope=? AND s.state='DEVUELTA'",scope));
        blocker(out,"peticiones entre unidades pendientes",db.count("SELECT COUNT(*) FROM flow_request WHERE state='PENDIENTE' AND (origin_scope=? OR target_scope=?)",scope,scope));
        blocker(out,"entregas de primera atencion pendientes",db.count("SELECT COUNT(*) FROM attention_handoff WHERE state='PENDIENTE' AND (from_scope=? OR target_scope=?)",scope,scope));
        blocker(out,"reaperturas pendientes",db.count("SELECT COUNT(*) FROM reopening p JOIN requirement q ON q.id=p.requirement_id LEFT JOIN case_record c ON c.id=p.case_id WHERE p.state='PENDIENTE' AND (q.principal_scope=? OR c.scope_id=?)",scope,scope));
        blocker(out,"vinculaciones propuestas",db.count("SELECT COUNT(*) FROM communication_link l JOIN requirement q ON q.id=l.requirement_id JOIN communication c ON c.id=l.communication_id WHERE l.state='PROPUESTA' AND (q.principal_scope=? OR c.origin_scope=?)",scope,scope));
        blocker(out,"funciones vigentes o programadas",db.count("SELECT COUNT(*) FROM role_assignment WHERE scope_id=? AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?)",scope,now));
        blocker(out,"cuentas con adscripcion principal en la sede",db.count("SELECT COUNT(*) FROM staff WHERE home_scope=? AND technical=FALSE",scope));
        return out;
    }
    private static void blocker(List<String> out,String what,long count){if(count>0)out.add(count+" "+what);}
    private record Wanted(Roles.Role role,Long scope,long from,Long until){ }

    /**
     * PROFILE_SET (0.5.0, D3): fija en un acto todas las funciones de una cuenta. Compara por (rol, ambito) con las
     * funciones vigentes o programadas: una entrada coincide si tiene el mismo termino (sin until_at = sin termino) y,
     * si trae from_at, el mismo inicio; lo que coincide se conserva, lo demas se revoca (nunca se borra) y lo nuevo
     * se otorga. D2 y la jefatura unica se evaluan sobre el estado final. Todo se valida antes de escribir; un solo epoch.
     * Una cuenta deshabilitada admite solo revocaciones.
     */
    private Workflow.Result profile(Actor a,long user,Long revision,Input in){
        ti(a);require(user!=a.id(),"No modifiques tus propias funciones.");
        var account=db.one("SELECT enabled,security_version FROM staff WHERE id=? AND technical=FALSE",user);
        require(revision!=null&&n(account,"security_version")==revision,"El registro cambio. Actualiza la ficha antes de confirmar.");
        String reason=in.text("reason",1000,true);long now=System.currentTimeMillis();
        var wanted=new ArrayList<Wanted>();var keys=new HashSet<String>();
        for(var e:in.list("grants",100)){
            var role=Roles.require(e.text("role",32,true));Long scope=role.scoped()?Long.valueOf(e.id("scope_id")):role.optional()?e.optionalId("scope_id"):null;
            require(keys.add(role.code()+"@"+scope),"La funcion "+role.code()+" figura dos veces en el perfil para el mismo ambito.");
            long from=e.instant("from_at",false),until=e.instant("until_at",false);
            require(until==0||until>Math.max(from,now),"Periodo invalido para "+role.code()+": el termino debe ser posterior al inicio y a la fecha actual.");
            wanted.add(new Wanted(role,scope,from,until==0?null:until));
        }
        var revoke=new ArrayList<>(db.rows("SELECT id,role_code,scope_id,valid_from,valid_until FROM role_assignment WHERE user_id=? AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?) ORDER BY id",user,now));
        var kept=new ArrayList<Map<String,Object>>();var create=new ArrayList<Wanted>();
        for(var w:wanted){
            var match=revoke.stream().filter(g->s(g,"role_code").equals(w.role().code())&&Objects.equals(nullableId(g,"scope_id"),w.scope())
                &&Objects.equals(nullableId(g,"valid_until"),w.until())&&(w.from()==0||n(g,"valid_from")==w.from())).findFirst();
            if(match.isPresent()){revoke.remove(match.get());kept.add(match.get());}else create.add(w);
        }
        require(!revoke.isEmpty()||!create.isEmpty(),"El perfil no tiene cambios.");
        require(create.isEmpty()||b(account,"enabled"),"La cuenta esta deshabilitada: su perfil solo admite revocaciones. Habilitala antes de otorgar funciones.");
        var finalRoles=new ArrayList<String>();kept.forEach(g->finalRoles.add(s(g,"role_code")));create.forEach(w->finalRoles.add(w.role().code()));
        require(!finalRoles.contains(Roles.TI)||finalRoles.size()==1,"TI es incompatible con cualquier otra funcion: el perfil no puede combinarla con otras.");
        // G11/G12: Reportes por persona y Exportacion exigen Reportes en el mismo alcance, sobre el estado final.
        var finalKeys=new HashSet<String>();kept.forEach(g->finalKeys.add(s(g,"role_code")+"@"+nullableId(g,"scope_id")));create.forEach(w->finalKeys.add(w.role().code()+"@"+w.scope()));
        for(var k:finalKeys){String role=k.substring(0,k.indexOf('@'));
            if(Roles.NEEDS_REPORTES.contains(role))require(finalKeys.contains(Roles.REPORTES+k.substring(role.length())),needsReportes(role));}
        for(var w:create){
            if(w.scope()!=null)grantable(w.role().code(),w.scope()); // Solo concesiones nuevas: lo que se conserva no se revalida.
            if(HEADS.contains(w.role().code()))headFree(w.scope(),user,w.from()==0?now:w.from(),w.until()==null?Long.MAX_VALUE:w.until());
        }
        for(var g:revoke){
            db.update("UPDATE role_assignment SET revoked_at=? WHERE id=? AND revoked_at IS NULL",now,n(g,"id"));
            audit(a,"FUNCION_REVOCADA","USUARIO",user,nullableId(g,"scope_id"),s(g,"role_code")+". "+reason);
        }
        for(var w:create)insertGrant(a,user,w.role().code(),w.scope(),w.from()==0?now:w.from(),w.until(),reason);
        epoch(user);keepTi();return Workflow.done("USUARIO",user);
    }
    /** Una jefatura o direccion efectiva por ambito: delegacion o subrogancia, nunca jefaturas superpuestas. */
    void headFree(long scope,long user,long from,long end){
        require(db.count("SELECT COUNT(*) FROM role_assignment WHERE scope_id=? AND user_id<>? AND role_code IN ('JEFATURA','DIRECCION') AND revoked_at IS NULL AND valid_from<? AND (valid_until IS NULL OR valid_until>?)",scope,user,end,from)==0,
            "Ya existe una jefatura efectiva. Usa delegacion autorizada o subrogancia, no jefaturas superpuestas.");
    }
    long insertGrant(Actor a,long user,String role,Long scope,long from,Long until,String basis){
        long grant=db.insert("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,valid_until,granted_by,basis) VALUES(?,?,?,?,?,?,?)",user,role,scope,from,until,a.id(),basis);
        audit(a,"FUNCION_OTORGADA","USUARIO",user,scope,role+". "+basis);return grant;
    }
    void epoch(long id){db.update("UPDATE staff SET security_version=security_version+1 WHERE id=?",id);}
    void ti(Actor a){if(!a.role(Roles.TI))Problem.denied();}
    /** D2: TI no se combina con ninguna otra funcion vigente o programada, en ningun sentido. */
    void tiCompatible(long user,String role){
        long now=System.currentTimeMillis();String live="SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?) AND role_code";
        if(role.equals(Roles.TI))require(db.count(live+"<>'TI'",user,now)==0,"TI es incompatible con cualquier otra funcion: revoca primero las funciones vigentes o programadas de la cuenta.");
        else require(db.count(live+"='TI'",user,now)==0,"Una cuenta con TI no puede recibir otras funciones.");
    }
    /** La instancia nunca queda sin administracion: al menos una cuenta TI habilitada, vigente y sin termino. */
    void keepTi(){
        require(permanentTi(db)>0,"La instancia debe conservar al menos una cuenta TI habilitada, vigente y sin fecha de termino.");
    }
    /** Cuentas TI habilitadas, vigentes y sin termino (la condicion que keepTi preserva y que la recuperacion repone). */
    static long permanentTi(Db db){
        return db.count("SELECT COUNT(DISTINCT u.id) FROM role_assignment g JOIN staff u ON u.id=g.user_id WHERE g.role_code='TI' AND g.scope_id IS NULL AND g.revoked_at IS NULL AND g.valid_from<=? AND g.valid_until IS NULL AND u.enabled=TRUE AND u.technical=FALSE",System.currentTimeMillis());
    }
    /**
     * Avisos de administracion sin datos de personas (codigo, cantidad y texto visible): instancia sin TI permanente o
     * con cuentas que combinan TI con otras funciones, y eventos historicos que la carga de hitos (V14) no reconocio.
     * Los mismos codigos se registran en el log al arrancar.
     */
    static List<Map<String,Object>> warnings(Db db){
        var out=new ArrayList<Map<String,Object>>();
        if(permanentTi(db)==0)out.add(Map.of("code","SIN_TI_PERMANENTE","count",0L,
            "message","La instancia no tiene una cuenta TI habilitada, vigente y sin fecha de término. Recupérala con el procedimiento de recuperación de TI (scripts\\Recover-Ti.ps1)."));
        long heads=regionalHeads(db);
        if(heads>0)out.add(Map.of("code","JEFATURA_EN_SEDE","count",heads,
            "message","Hay "+heads+(heads==1?" función Jefatura vigente o programada":" funciones Jefatura vigentes o programadas")+" en sedes regionales. La Coordinación regional se registra como Dirección: revísalo en el perfil de cada cuenta."));
        long conflicts=tiConflicts(db).size();
        if(conflicts>0)out.add(Map.of("code","TI_INCOMPATIBLE","count",conflicts,
            "message","Hay "+conflicts+(conflicts==1?" cuenta que combina":" cuentas que combinan")+" TI con otras funciones. TI no puede tener otras funciones: resuélvelo en el perfil de cada cuenta."));
        long omitted=Milestones.omitted(db);
        if(omitted>0)out.add(Map.of("code","HITOS_OMITIDOS","count",omitted,
            "message","Hitos reconstruidos con "+omitted+(omitted==1?" omisión":" omisiones")+": eventos históricos de versiones anteriores que la carga inicial no reconoció. La operación no se ve afectada; los reportes de tiempos no consideran esos eventos."));
        return out;
    }
    /** JEFATURA vigentes o programadas en sedes regionales (D7): las que la conversion de arranque no pudo convertir. */
    static long regionalHeads(Db db){
        return db.count("SELECT COUNT(*) FROM role_assignment g JOIN org_scope s ON s.id=g.scope_id WHERE g.role_code='JEFATURA' AND s.regional=TRUE AND g.revoked_at IS NULL AND (g.valid_until IS NULL OR g.valid_until>?)",System.currentTimeMillis());
    }
    /**
     * Cuentas que combinan TI con otra funcion vigente o programada (D2): heredadas de versiones anteriores, nunca se
     * corrigen solas. TI las resuelve en Administracion; aqui solo se listan (cuenta y codigos de funcion).
     */
    static List<Map<String,Object>> tiConflicts(Db db){
        long now=System.currentTimeMillis();String live="g.revoked_at IS NULL AND (g.valid_until IS NULL OR g.valid_until>?)";
        var out=new ArrayList<Map<String,Object>>();
        for(var u:db.rows("SELECT u.id,u.username,u.name FROM staff u WHERE u.technical=FALSE AND EXISTS(SELECT 1 FROM role_assignment g WHERE g.user_id=u.id AND g.role_code='TI' AND "+live+")"
            +" AND EXISTS(SELECT 1 FROM role_assignment g WHERE g.user_id=u.id AND g.role_code<>'TI' AND "+live+") ORDER BY u.id",now,now)){
            var roles=db.rows("SELECT DISTINCT g.role_code FROM role_assignment g WHERE g.user_id=? AND "+live+" ORDER BY g.role_code",n(u,"id"),now).stream().map(r->s(r,"role_code")).toList();
            var row=new LinkedHashMap<String,Object>(u);row.put("roles",roles);out.add(row);
        }
        return out;
    }
    void audit(Actor a,String action,String kind,long id,Long scope,String reason){db.audit(a.id(),action,kind,id,scope,reason);}
    static String password(String p){require(p.length()>=14&&p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"La clave necesita al menos 14 caracteres y un maximo de 72 bytes.");return p;}
}
