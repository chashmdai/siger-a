package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/** TI administra cuentas. Autoridad habilitada concede funciones y activa subrogancias. */
@Service
public class Personnel {
    static final Set<String> SCOPED_ROLES=Set.of("ADMINISTRATIVO","PROFESIONAL","JEFATURA","DIRECCION","AUTORIDAD","LECTURA","AUDITORIA");
    private final Db db;private final Access access;private final PasswordEncoder passwords;private final Integrations integrations;private final MailOutbox mail;
    private final Documents documents;
    public Personnel(Db db,Access access,PasswordEncoder passwords,Integrations integrations,MailOutbox mail,Documents documents){this.db=db;this.access=access;this.passwords=passwords;this.integrations=integrations;this.mail=mail;this.documents=documents;}
    public Workflow.Result run(Actor a,String type,long id,Long revision,Input in){
        switch(type){
            case "STAFF_CREATE" -> {
                ti(a);String username=in.text("username",64,true).toLowerCase(Locale.ROOT);
                require(username.matches("[a-z0-9][a-z0-9._-]{2,63}"),"Usuario: 3 a 64 caracteres sin espacios.");
                long scope=in.id("scope_id");access.scope(scope);String password=password(in.text("password",72,true));
                long user=db.insert("INSERT INTO staff(username,name,email,password_hash,home_scope) VALUES(?,?,?,?,?)",username,in.text("name",160,true),in.text("email",200,false),passwords.encode(password),scope);
                audit(a,"CUENTA_CREADA","USUARIO",user,null,in.reason());return Workflow.done("USUARIO",user);
            }
            case "STAFF_UPDATE" -> {
                ti(a);require(id!=a.id()||in.flag("enabled"),"No puedes desactivar tu propia sesion.");
                db.one("SELECT id FROM staff WHERE id=? AND technical=FALSE",id);
                db.update("UPDATE staff SET name=?,email=?,enabled=?,security_version=security_version+1 WHERE id=?",in.text("name",160,true),in.text("email",200,false),in.flag("enabled"),id);
                audit(a,"CUENTA_ACTUALIZADA","USUARIO",id,null,in.reason());return Workflow.done("USUARIO",id);
            }
            case "STAFF_RESET" -> {
                ti(a);db.one("SELECT id FROM staff WHERE id=? AND technical=FALSE",id);String pass=password(in.text("password",72,true));
                db.update("UPDATE staff SET password_hash=?,must_change=TRUE,security_version=security_version+1 WHERE id=?",passwords.encode(pass),id);
                audit(a,"CLAVE_TEMPORAL_REPUESTA","USUARIO",id,null,in.reason());return Workflow.done("USUARIO",id);
            }
            case "GRANT_CREATE" -> {
                long scope=in.id("scope_id"),user=in.id("user_id");authority(a,scope);require(user!=a.id(),"No puedes otorgarte facultades.");access.user(user);
                String role=in.text("role",32,true);require(SCOPED_ROLES.contains(role)||role.equals("DISTRIBUIDOR"),"Rol no disponible en esta operacion.");
                long from=in.instant("from_at",true),until=in.instant("until_at",false);require(until==0||until>from,"Periodo invalido.");
                if(role.equals("DISTRIBUIDOR")){
                    require(!access.regional(scope),"Distribucion central no se concede desde un ambito regional.");
                    require(db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='DISTRIBUIDOR' AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?) AND valid_from<?",from,until==0?Long.MAX_VALUE:until)==0,"Revoca o finaliza la designacion central anterior; no se permiten periodos superpuestos.");
                }
                if(Set.of("JEFATURA","DIRECCION").contains(role))require(db.count("SELECT COUNT(*) FROM role_assignment WHERE scope_id=? AND user_id<>? AND role_code IN ('JEFATURA','DIRECCION') AND revoked_at IS NULL AND valid_from<? AND (valid_until IS NULL OR valid_until>?)",scope,user,until==0?Long.MAX_VALUE:until,from)==0,"Ya existe una jefatura efectiva. Usa delegacion autorizada o subrogancia, no jefaturas superpuestas.");
                long grant=db.insert("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,valid_until,granted_by,basis) VALUES(?,?,?,?,?,?,?)",user,role,role.equals("DISTRIBUIDOR")?null:scope,from,until==0?null:until,a.id(),in.text("reason",1000,true));
                epoch(user);audit(a,"FUNCION_OTORGADA","USUARIO",user,scope,role+". "+in.reason());return Workflow.done("PERMISO",grant);
            }
            case "GRANT_REVOKE" -> {
                var grant=db.one("SELECT * FROM role_assignment WHERE id=?",id);Long scope=nullableId(grant,"scope_id");
                if(scope==null){require(s(grant,"role_code").equals("DISTRIBUIDOR"),"Las cuentas tecnicas iniciales requieren gestion fuera de esta pantalla.");authority(a,in.id("scope_id"));}
                else authority(a,scope);
                require(n(grant,"user_id")!=a.id(),"No modifiques tus propias facultades por esta ruta.");
                db.update("UPDATE role_assignment SET revoked_at=? WHERE id=? AND revoked_at IS NULL",System.currentTimeMillis(),id);epoch(n(grant,"user_id"));
                audit(a,"FUNCION_REVOCADA","USUARIO",n(grant,"user_id"),scope,in.reason());return Workflow.done("PERMISO",id);
            }
            case "SCOPE_CREATE" -> {
                ti(a);String unit=in.choice("unit_code","UPR","UPD","UEP","UGI","REG","DAI","GAB","DEF","AUD");String code=in.text("code",48,true).toUpperCase(Locale.ROOT);
                require(code.matches("[A-Z0-9_-]{2,48}"),"Codigo de ambito invalido.");
                long scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional) VALUES(?,?,?,?,?,?)",code,in.text("name",160,true),unit,in.text("area_code",48,false),in.text("site_code",48,true),in.flag("regional"));
                audit(a,"AMBITO_CREADO","AMBITO",scope,scope,in.reason());return Workflow.done("AMBITO",scope);
            }
            case "DESIGNATE" -> {
                long scope=in.id("scope_id"),titular=in.id("titular_id"),substitute=in.id("substitute_id");authority(a,scope);
                require(titular!=substitute&&a.id()!=substitute,"Personas incompatibles para la designacion.");
                require(access.user(titular).head(scope),"El titular no ejerce jefatura en ese ambito.");access.substituteEligible(substitute,scope);
                long d=db.insert("INSERT INTO substitution_designation(scope_id,titular_id,substitute_id,basis) VALUES(?,?,?,?)",scope,titular,substitute,in.text("reason",1000,true));
                audit(a,"SUBROGANTE_PREDESIGNADO","SUBROGANCIA",d,scope,in.reason());return Workflow.done("DESIGNACION",d);
            }
            case "SUBSTITUTE_ACTIVATE" -> {
                var d=db.one("SELECT * FROM substitution_designation WHERE id=?",id);long scope=n(d,"scope_id"),sub=n(d,"substitute_id");authority(a,scope);
                require(b(d,"enabled")&&a.id()!=sub&&a.id()!=n(d,"titular_id"),"La autoridad debe ser independiente del titular y del subrogante.");
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
                access.notice(sub,"Una autoridad habilito tu subrogancia durante el periodo registrado.","SUBROGANCIA",x);
                access.notice(n(d,"titular_id"),"Se registro una subrogancia de tu funcion; tu cartera profesional no se transfiere.","SUBROGANCIA",x);
                return Workflow.done("SUBROGANCIA",x);
            }
            case "SUBSTITUTE_END" -> {
                var x=db.one("SELECT x.*,d.scope_id,d.titular_id,d.substitute_id FROM substitution x JOIN substitution_designation d ON d.id=x.designation_id WHERE x.id=?",id);Db.revision(x,revision);
                long scope=n(x,"scope_id");if(a.id()!=n(x,"titular_id"))authority(a,scope);require(x.get("ended_ms")==null,"Ya fue terminada.");
                db.update("UPDATE substitution SET ended_ms=?,ended_by=?,revision=revision+1 WHERE id=?",System.currentTimeMillis(),a.id(),id);
                epoch(n(x,"substitute_id"));epoch(n(x,"titular_id"));audit(a,"SUBROGANCIA_TERMINADA","SUBROGANCIA",id,scope,in.reason());
                access.notice(n(x,"titular_id"),"Retornan tus facultades; revisa los pendientes. No se cerro ningun Caso automaticamente.","SUBROGANCIA",id);return Workflow.done("SUBROGANCIA",id);
            }
            // Liberacion de cuarentena: analisis antivirus confiable previo (o, solo en instancias de pruebas, revision manual).
            case "DOC_RELEASE" -> {return documents.release(a,id,in);}
            // Operacion de integraciones: TI pausa o reanuda y decide sobre correos ambiguos. No da acceso a expedientes.
            case "INTEGRATION_PAUSE","INTEGRATION_RESUME" -> {return integrations.control(a,id,revision,type.equals("INTEGRATION_PAUSE"),in.text("reason",1000,true));}
            case "MAIL_RESOLVE" -> {return mail.resolve(a,id,in.choice("decision","REENVIAR","DESCARTAR"),in.reason());}
            default -> throw new Problem(400,"Operacion de administracion desconocida.");
        }
    }
    void epoch(long id){db.update("UPDATE staff SET security_version=security_version+1 WHERE id=?",id);}
    void authority(Actor a,long scope){access.scope(scope);if(!a.authority(scope))Problem.denied();}
    void ti(Actor a){if(!a.role("TI"))Problem.denied();}
    void audit(Actor a,String action,String kind,long id,Long scope,String reason){db.audit(a.id(),action,kind,id,scope,reason);}
    static String password(String p){require(p.length()>=14&&p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"La clave necesita al menos 14 caracteres y un maximo de 72 bytes.");return p;}
}
