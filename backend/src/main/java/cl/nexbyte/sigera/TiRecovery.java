package cl.nexbyte.sigera;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static cl.nexbyte.sigera.Db.*;

/**
 * Recuperacion de una instancia sin TI permanente (0.5.0, D1). Solo actua con siger.recover-ti.user en un proceso sin
 * servidor web ni tareas programadas (scripts\Recover-Ti.ps1) y solo si NO queda ninguna cuenta TI habilitada, vigente
 * y sin termino: con una TI vigente, la administracion se hace por la interfaz y aqui se rechaza.
 * - Cuenta existente: se habilita y recibe clave temporal con cambio obligatorio. Conserva su TI permanente si la tiene
 *   (solo estaba deshabilitada); si no, se revocan sus TI con termino o programadas y recibe TI desde ahora sin
 *   termino. Debe no tener otras funciones vigentes o programadas (D2); si las tiene, se usa otra cuenta o una nueva.
 * - Cuenta nueva: exige nombre y ambito de adscripcion habilitado.
 * Todo en una transaccion con el lock global, auditado a nombre de sistema:actualizacion con el fundamento del acto
 * institucional. La clave temporal va solo a un archivo privado nuevo en siger.data; nunca al log.
 */
@Component
class TiRecovery {
    private static final Logger LOG=LoggerFactory.getLogger(TiRecovery.class);
    record Result(long user,boolean created,Path credentials){ }

    private final Db db;private final PasswordEncoder passwords;private final TransactionTemplate tx;private final Path data;
    private final String user,name,scope,basis;
    TiRecovery(Db db,PasswordEncoder passwords,PlatformTransactionManager manager,@Value("${siger.data}")String data,
               @Value("${siger.recover-ti.user:}")String user,@Value("${siger.recover-ti.name:}")String name,
               @Value("${siger.recover-ti.scope:}")String scope,@Value("${siger.recover-ti.basis:}")String basis){
        this.db=db;this.passwords=passwords;this.tx=new TransactionTemplate(manager);this.data=Path.of(data).toAbsolutePath().normalize();
        this.user=user.strip();this.name=name.strip();this.scope=scope.strip();this.basis=basis.strip();
    }

    /** Proceso de recuperacion: corre despues de Bootstrap, informa sin datos personales y cierra el contexto. */
    @EventListener(ApplicationReadyEvent.class) void ready(ApplicationReadyEvent event){
        if(user.isEmpty())return;
        ConfigurableApplicationContext context=event.getApplicationContext();Environment env=context.getEnvironment();
        if(context instanceof WebServerApplicationContext||env.getProperty("siger.scheduling",Boolean.class,true)){
            context.close();throw new IllegalStateException("La recuperacion de TI se ejecuta sin servidor web ni tareas programadas (scripts\\Recover-Ti.ps1).");
        }
        try{
            var r=recover(user,name,scope,basis);
            LOG.info("Recuperacion de TI: completada (cuenta {}). Clave temporal en {}.",r.created()?"nueva":"existente",r.credentials().getFileName());
        }catch(RuntimeException e){
            LOG.error("Recuperacion de TI: rechazada. {}",e.getMessage());
            context.close();throw e;
        }
        context.close();
    }

    Result recover(String username,String rawName,String rawScope,String basis){
        String u=username.strip().toLowerCase(Locale.ROOT),fullName=rawName.strip(),scopeCode=rawScope.strip(),reason=basis.strip();
        if(!u.matches("[a-z0-9][a-z0-9._-]{2,63}"))throw new IllegalStateException("Usuario invalido: 3 a 64 caracteres sin espacios.");
        if(reason.length()<10||reason.length()>1000||reason.chars().anyMatch(Character::isISOControl))
            throw new IllegalStateException("El fundamento (acto institucional) es obligatorio: 10 a 1000 caracteres.");
        Path credentials=data.resolve("recuperacion-ti-"+System.currentTimeMillis()+".csv");
        String pass=password();
        var result=tx.execute(status->{
            db.lock();
            if(Personnel.permanentTi(db)>0)throw new IllegalStateException("La instancia conserva una cuenta TI habilitada, vigente y sin termino: la recuperacion no procede. Administra las cuentas desde la interfaz.");
            long now=System.currentTimeMillis(),actor=Bootstrap.maintenance(db);String note="Recuperacion de TI. "+reason;
            var rows=db.rows("SELECT id,technical FROM staff WHERE username=?",u);
            long id;boolean created=rows.isEmpty();String display;
            if(created){
                if(fullName.isEmpty()||fullName.length()>160||fullName.chars().anyMatch(Character::isISOControl))throw new IllegalStateException("Una cuenta nueva exige nombre (hasta 160 caracteres).");
                var home=db.rows("SELECT id FROM org_scope WHERE code=? AND enabled=TRUE",scopeCode);
                if(home.isEmpty())throw new IllegalStateException("Una cuenta nueva exige un ambito de adscripcion existente y habilitado.");
                id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,?,?,TRUE)",u,fullName,passwords.encode(pass),n(home.getFirst(),"id"));
                db.audit(actor,"CUENTA_CREADA","USUARIO",id,null,note);display=fullName;
            }else{
                if(b(rows.getFirst(),"technical"))throw new IllegalStateException("Una identidad tecnica no puede recibir TI.");
                id=n(rows.getFirst(),"id");
                if(db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code<>'TI' AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?)",id,now)>0)
                    throw new IllegalStateException("La cuenta tiene otras funciones vigentes o programadas y TI es incompatible con ellas (D2): usa otra cuenta o crea una nueva.");
                db.update("UPDATE staff SET enabled=TRUE,password_hash=?,must_change=TRUE,security_version=security_version+1 WHERE id=?",passwords.encode(pass),id);
                db.audit(actor,"CUENTA_ACTUALIZADA","USUARIO",id,null,note);
                db.audit(actor,"CLAVE_TEMPORAL_REPUESTA","USUARIO",id,null,note);
                display=s(db.one("SELECT name FROM staff WHERE id=?",id),"name");
            }
            var current=db.rows("SELECT id,valid_from,valid_until FROM role_assignment WHERE user_id=? AND role_code='TI' AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?)",id,now);
            if(current.stream().noneMatch(g->g.get("valid_until")==null&&n(g,"valid_from")<=now)){
                // Sin superponer periodos: las TI con termino o programadas se reemplazan por una permanente.
                for(var g:current){
                    db.update("UPDATE role_assignment SET revoked_at=? WHERE id=?",now,n(g,"id"));
                    db.audit(actor,"FUNCION_REVOCADA","USUARIO",id,null,"TI. "+note);
                }
                db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,?,?)",id,now,note);
                db.audit(actor,"FUNCION_OTORGADA","USUARIO",id,null,"TI. "+note);
            }
            db.audit(actor,"TI_RECUPERADA","SISTEMA",0,null,(created?"Cuenta nueva. ":"Cuenta existente. ")+note);
            try{
                Files.createDirectories(data);
                Files.writeString(credentials,"usuario,nombre,clave_temporal\n"+u+","+display.replace(',',' ')+","+pass+"\n",StandardOpenOption.CREATE_NEW);
                if(Files.getFileStore(credentials).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(credentials,PosixFilePermissions.fromString("rw-------"));
            }catch(Exception e){throw new IllegalStateException("No fue posible crear el archivo privado con la clave temporal; no se aplica la recuperacion.");}
            return new Result(id,created,credentials);
        });
        return Objects.requireNonNull(result);
    }
    private static String password(){byte[] bytes=new byte[18];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"a7!";}
}
