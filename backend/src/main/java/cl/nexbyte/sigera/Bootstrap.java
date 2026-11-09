package cl.nexbyte.sigera;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static cl.nexbyte.sigera.Db.*;

@Component
public class Bootstrap implements ApplicationRunner {
    final Db db;final PasswordEncoder passwords;final TransactionTemplate tx;final ConfigurableApplicationContext ctx;
    final Path data;final boolean seed,migrate;final String institutional;
    public Bootstrap(Db db,PasswordEncoder passwords,PlatformTransactionManager manager,ConfigurableApplicationContext ctx,
                     @Value("${siger.data}")String data,@Value("${siger.seed-demo:false}")boolean seed,@Value("${siger.migrate-only:false}")boolean migrate,
                     @Value("${siger.bootstrap-file:}")String institutional){
        this.db=db;this.passwords=passwords;this.tx=new TransactionTemplate(manager);this.ctx=ctx;this.data=Path.of(data).toAbsolutePath().normalize();this.seed=seed;this.migrate=migrate;
        this.institutional=institutional.strip();
    }
    @Override public void run(ApplicationArguments args)throws Exception{
        requireDatabase();
        Files.createDirectories(data.resolve("tmp"));Files.createDirectories(data.resolve("documents"));
        if(Files.isSymbolicLink(data)||Files.isSymbolicLink(data.resolve("documents")))throw new IllegalStateException("Raiz de datos no debe ser enlace.");
        if(seed&&!institutional.isEmpty())throw new IllegalStateException("Inicializacion institucional y datos de prueba son excluyentes.");
        if(!institutional.isEmpty()&&db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE")==0)institutional(Path.of(institutional));
        if(seed){
            if(db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE")==0)seed();
            reconcileDemoOrganization(); // Actualiza solo cuentas sinteticas; nunca nombres de funcionarios reales.
        }
        if(migrate)ctx.close();
    }
    private void requireDatabase(){
        var source=db.dataSource();
        try(var c=source.getConnection()){
            String url=c.getMetaData().getURL();
            if(url.startsWith("jdbc:mysql:")){
                String name=c.getCatalog();if(name==null||!name.matches("siger_a_[a-z0-9_]{1,40}"))throw new IllegalStateException("Esquema MySQL no exclusivo de SIGER.");
            }else if(!url.startsWith("jdbc:h2:"))throw new IllegalStateException("Motor no permitido.");
            if(!s(db.one("SELECT namespace FROM app_meta WHERE id=1"),"namespace").equals("SIGER_A_2026"))throw new IllegalStateException("Esquema ajeno.");
            // Sin esta comprobacion la instancia arranca "UP" sobre una base de solo lectura y falla en el primer ingreso.
            Db.requireWritable(c);
        }catch(java.sql.SQLException e){throw new IllegalStateException("No fue posible verificar el esquema aislado.");}
    }
    private void seed() throws Exception {
        Path credentials=data.resolve("bootstrap-usuarios.csv");
        if(Files.exists(credentials))throw new IllegalStateException("Existe un archivo de acceso anterior; no se sobreescribe.");
        var csv=new StringBuilder("usuario,nombre,clave_temporal\n");
        tx.executeWithoutResult(status->{
            db.lock();if(db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE")>0)return;
            String[][] units={{"UPR","Proteccion de Derechos y Representacion Judicial"},{"UPD","Promocion y Difusion de Derechos"},{"UEP","Estudios Publicaciones y Estadisticas"},{"UGI","Gestion Institucional"},{"REG","Sedes Regionales"},{"DAI","Desarrollo y Analisis Institucional"},{"GAB","Gabinete"},{"DEF","Defensoria"},{"AUD","Auditoria"}};
            for(var u:units)db.update("INSERT INTO org_unit(code,name) VALUES(?,?)",u[0],u[1]);
            String[][] scopes={{"UPR","Direccion UPR","UPR",""},{"UPD","Direccion UPD","UPD",""},{"UEP","Direccion UEP","UEP",""},{"UPR-REQ","Requerimientos / Atencion de Personas","UPR","REQ"},{"UPR-NCE","NNA bajo cuidado del Estado","UPR","NCE"},{"UPR-RJ","Representacion Judicial","UPR","RJ"},{"UPR-PD","Proteccion de Derechos","UPR","PD"},{"UPD-EDU","Educacion","UPD","EDU"},{"UPD-DIF","Difusion","UPD","DIF"},{"UPD-PAR","Participacion","UPD","PAR"},{"UEP-EST","Estudios","UEP","EST"},{"UEP-OBS","Observatorio de Derechos","UEP","OBS"},{"UGI","Gestion Institucional","UGI",""},{"REG-DEMO","Sede sintetica - no oficial","REG",""}};
            Map<String,Long> ids=new LinkedHashMap<>();
            for(var s:scopes){boolean regional=s[0].equals("REG-DEMO");long id=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional) VALUES(?,?,?,?,?,?)",s[0],s[1],s[2],s[3].isBlank()?null:s[3],regional?"SEDE-DEMO":"CENTRAL",regional);ids.put(s[0],id);}
            String[][] users={
                {"distribucion","Distribuidor sintetico","UPR-REQ","DISTRIBUIDOR"},
                {"administrativo","Primera atencion sintetica","UPR-REQ","ADMINISTRATIVO"},
                {"profesional","Profesional Requerimientos sintetico","UPR-REQ","PROFESIONAL"},
                {"coordinacion.requerimientos","Coordinadora de Requerimientos sintetica","UPR-REQ","JEFATURA"},
                {"direccion.upr","Direccion UPR sintetica","UPR","DIRECCION"},
                {"profesional.proteccion","Profesional Proteccion sintetico","UPR-PD","PROFESIONAL"},
                {"direccion.upd","Direccion UPD sintetica","UPD","DIRECCION"},
                {"profesional.upd","Profesional Educacion UPD sintetico","UPD-EDU","PROFESIONAL"},
                {"profesional.difusion","Profesional Difusion UPD sintetico","UPD-DIF","PROFESIONAL"},
                {"direccion.uep","Direccion UEP sintetica","UEP","DIRECCION"},
                {"profesional.uep","Profesional Estudios sintetico","UEP-EST","PROFESIONAL"},
                {"direccion.ugi","Direccion UGI sintetica","UGI","DIRECCION"},
                {"profesional.ugi","Profesional UGI sintetico","UGI","PROFESIONAL"},
                {"coordinacion.regional","Coordinacion regional sintetica","REG-DEMO","JEFATURA"},
                {"profesional.regional","Profesional regional sintetico","REG-DEMO","PROFESIONAL"},
                {"autoridad","Autoridad habilitante sintetica","UGI","AUTORIDAD"},
                {"ti","Administracion tecnica sintetica","UGI","TI"},
                {"auditoria","Auditoria de prueba","UPR-REQ","AUDITORIA"},
                {"gabinete","Consulta autorizada de prueba","UPR-REQ","LECTURA"}
            };
            Map<String,Long> people=new HashMap<>();
            for(var u:users){
                String pass=randomPassword();long scope=ids.get(u[2]);
                long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope) VALUES(?,?,?,?)",u[0],u[1],passwords.encode(pass),scope);
                people.put(u[0],id);csv.append(u[0]).append(',').append(u[1]).append(',').append(pass).append('\n');
                if(u[3].equals("AUTORIDAD")){for(long sc:ids.values())grant(id,"AUTORIDAD",sc);}
                else if(u[3].equals("DIRECCION")){
                    String unit=u[2];
                    for(var e:ids.entrySet())if((e.getKey().equals(unit)||e.getKey().startsWith(unit+"-"))&&!e.getKey().equals("UPR-REQ"))grant(id,"DIRECCION",e.getValue());
                    if(unit.equals("UPR"))grant(id,"LECTURA",ids.get("UPR-REQ"));
                }else grant(id,u[3],Set.of("TI","DISTRIBUIDOR").contains(u[3])?null:scope);
                if(u[3].equals("JEFATURA"))grant(id,"PROFESIONAL",scope);
                if(u[3].equals("DISTRIBUIDOR"))grant(id,"ADMINISTRATIVO",scope);
            }
            db.update("INSERT INTO substitution_designation(scope_id,titular_id,substitute_id,basis) VALUES(?,?,?,?)",ids.get("REG-DEMO"),people.get("coordinacion.regional"),people.get("profesional.regional"),"Designacion exclusivamente sintetica para probar subrogancia.");
            db.update("INSERT INTO application_marker(code,applied_ms) VALUES('DEMO_ORGANIZATION_030',?)",System.currentTimeMillis());
            db.audit(people.get("ti"),"CONFIGURACION_SINTETICA","SISTEMA",0,null,"Cuentas y sede ficticias. No son designaciones institucionales.");
            try{
                Files.writeString(credentials,csv.toString(),StandardOpenOption.CREATE_NEW);
                if(Files.getFileStore(credentials).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(credentials,PosixFilePermissions.fromString("rw-------"));
            }catch(Exception e){throw new IllegalStateException("No fue posible crear el archivo privado de accesos iniciales.");}
        });
    }
    /** La version 0.2.0 ya puede contener COM/REQ/Casos; solo ampliar catalogo demo, sin borrarlos. */
    private void reconcileDemoOrganization(){
        if(db.count("SELECT COUNT(*) FROM staff WHERE username='distribucion'")==0)return;
        tx.executeWithoutResult(status->{
            db.lock();
            if(db.count("SELECT COUNT(*) FROM application_marker WHERE code='DEMO_ORGANIZATION_030'")>0)return;
            String[][] roots={{"UPR","Direccion UPR"},{"UPD","Direccion UPD"},{"UEP","Direccion UEP"}};
            for(var r:roots){
                if(db.count("SELECT COUNT(*) FROM org_scope WHERE code=?",r[0])==0)
                    db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional) VALUES(?,?,?,NULL,'CENTRAL',FALSE)",r[0],r[1],r[0]);
            }
            // Conserva el ID, los documentos, la clave actual y el historial de las cuentas antiguas.
            String[][] aliases={{"jefatura.upr","coordinacion.requerimientos"},{"jefatura.upd","direccion.upd"}};
            for(var alias:aliases)if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",alias[0])==1
                &&db.count("SELECT COUNT(*) FROM staff WHERE username=?",alias[1])==0)
                db.update("UPDATE staff SET username=?,security_version=security_version+1 WHERE username=?",alias[1],alias[0]);
            var coordinator=db.rows("SELECT id,name FROM staff WHERE username='coordinacion.requerimientos'");
            if(!coordinator.isEmpty()&&s(coordinator.getFirst(),"name").contains("sintetic"))
                db.update("UPDATE staff SET name='Coordinadora de Requerimientos sintetica' WHERE id=?",n(coordinator.getFirst(),"id"));
            var oldUpd=db.rows("SELECT id,name FROM staff WHERE username='direccion.upd'");
            if(!oldUpd.isEmpty()){
                long updid=n(oldUpd.getFirst(),"id");
                if(s(oldUpd.getFirst(),"name").contains("sintetic"))db.update("UPDATE staff SET name='Direccion UPD sintetica' WHERE id=?",updid);
                db.update("UPDATE role_assignment SET revoked_at=? WHERE user_id=? AND role_code='JEFATURA' AND basis LIKE 'CONFIGURACION SINTETICA%' AND revoked_at IS NULL",System.currentTimeMillis(),updid);
            }
            String[][] additional={
                {"direccion.uep","Direccion UEP sintetica","UEP","DIRECCION"},
                {"direccion.ugi","Direccion UGI sintetica","UGI","DIRECCION"},
                {"profesional.proteccion","Profesional Proteccion sintetico","UPR-PD","PROFESIONAL"},
                {"profesional.difusion","Profesional Difusion UPD sintetico","UPD-DIF","PROFESIONAL"},
                {"profesional.uep","Profesional Estudios sintetico","UEP-EST","PROFESIONAL"},
                {"profesional.ugi","Profesional UGI sintetico","UGI","PROFESIONAL"}
            };
            var csv=new StringBuilder("usuario,nombre,clave_temporal\n");
            boolean willCreate=Arrays.stream(additional).anyMatch(u->db.count("SELECT COUNT(*) FROM staff WHERE username=?",u[0])==0);
            Path supplemental=data.resolve("bootstrap-usuarios-0.3.0.csv");
            if(willCreate&&Files.exists(supplemental))throw new IllegalStateException("Archivo de nuevas claves ya existe. No se sobreescribe.");
            for(var u:additional)if(db.count("SELECT COUNT(*) FROM staff WHERE username=?",u[0])==0){
                long sc=n(db.one("SELECT id FROM org_scope WHERE code=?",u[2]),"id");String pass=randomPassword();
                long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope) VALUES(?,?,?,?)",u[0],u[1],passwords.encode(pass),sc);
                csv.append(u[0]).append(',').append(u[1]).append(',').append(pass).append('\n');
                if(u[3].equals("PROFESIONAL"))grant(id,"PROFESIONAL",sc);
            }
            // La Direccion cubre areas sin coordinacion. UPR-REQ conserva su propia Coordinadora.
            String[] units={"UPR","UPD","UEP","UGI"};
            for(var unit:units){
                var directors=db.rows("SELECT id FROM staff WHERE username=?","direccion."+unit.toLowerCase(Locale.ROOT));
                if(directors.isEmpty())continue;
                long uid=n(directors.getFirst(),"id");
                long root=n(db.one("SELECT id FROM org_scope WHERE code=?",unit),"id");
                db.update("UPDATE staff SET home_scope=? WHERE id=? AND home_scope<>?",root,uid,root);
                for(var sc:db.rows("SELECT id,code FROM org_scope WHERE unit_code=? AND enabled=TRUE",unit)){
                    if(s(sc,"code").equals("UPR-REQ"))ensureDemoGrant(uid,"LECTURA",n(sc,"id"));
                    else ensureDemoGrant(uid,"DIRECCION",n(sc,"id"));
                }
            }
            var authorities=db.rows("SELECT id FROM staff WHERE username='autoridad'");
            if(!authorities.isEmpty())for(var r:roots)ensureDemoGrant(n(authorities.getFirst(),"id"),"AUTORIDAD",n(db.one("SELECT id FROM org_scope WHERE code=?",r[0]),"id"));
            if(willCreate){
                try{
                    Files.writeString(supplemental,csv.toString(),StandardOpenOption.CREATE_NEW);
                    if(Files.getFileStore(supplemental).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(supplemental,PosixFilePermissions.fromString("rw-------"));
                }catch(Exception e){throw new IllegalStateException("No se pudo guardar el archivo privado de nuevos accesos.",e);}
            }
            db.update("INSERT INTO application_marker(code,applied_ms) VALUES('DEMO_ORGANIZATION_030',?)",System.currentTimeMillis());
        });
    }
    // --- Inicializacion institucional --------------------------------------------------------------
    /** Codigos de unidad que reconoce el producto (SCOPE_CREATE) con su denominacion institucional. */
    static final Map<String,String> UNITS=new LinkedHashMap<>();
    static{
        UNITS.put("UPR","Proteccion de Derechos y Representacion Judicial");UNITS.put("UPD","Promocion y Difusion de Derechos");
        UNITS.put("UEP","Estudios Publicaciones y Estadisticas");UNITS.put("UGI","Gestion Institucional");UNITS.put("REG","Sedes Regionales");
        UNITS.put("DAI","Desarrollo y Analisis Institucional");UNITS.put("GAB","Gabinete");UNITS.put("DEF","Defensoria");UNITS.put("AUD","Auditoria");
    }
    /**
     * Instancia institucional vacia: sin cuentas, sedes ni datos de prueba. Desde el archivo del operador
     * (siger.bootstrap-file) se crean solo los ambitos reales informados, una cuenta TI (administra cuentas, sin
     * acceso a expedientes) y una autoridad habilitante (concede funciones en los ambitos indicados), ambas con clave
     * temporal individual y cambio obligatorio. Todo lo demas se registra despues por la interfaz, con autoria y
     * fundamento. Solo actua con la tabla de personal vacia; un archivo invalido detiene el arranque sin escribir.
     */
    void institutional(Path file)throws Exception{
        if(!Files.isRegularFile(file)||Files.size(file)>262_144)throw new IllegalStateException("Archivo de inicializacion institucional ausente o demasiado grande.");
        var reader=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
        tools.jackson.databind.JsonNode root;
        // Se admite la marca BOM de UTF-8 que agregan Windows PowerShell 5.1 y algunos editores.
        String content=Files.readString(file,java.nio.charset.StandardCharsets.UTF_8);
        if(!content.isEmpty()&&content.charAt(0)==0xFEFF)content=content.substring(1);
        try{root=reader.readTree(content);}catch(RuntimeException e){throw new IllegalStateException("Archivo de inicializacion institucional con JSON invalido.");}
        String basis=text(root,"fundamento",1000,true);
        var scopes=root.path("ambitos");if(!scopes.isArray()||scopes.isEmpty()||scopes.size()>500)throw invalid("ambitos (1 a 500)");
        var codes=new LinkedHashSet<String>();
        for(var sc:scopes){
            String code=text(sc,"codigo",48,true);
            if(!code.matches("[A-Z0-9_-]{2,48}")||!codes.add(code))throw invalid("codigo de ambito "+code);
            if(!UNITS.containsKey(text(sc,"unidad",24,true)))throw invalid("unidad del ambito "+code);
            text(sc,"nombre",160,true);text(sc,"sede",48,true);text(sc,"area",48,false);
        }
        var ti=root.path("ti");var authority=root.path("autoridad");
        String tiUser=user(ti),authorityUser=user(authority);
        if(tiUser.equals(authorityUser))throw invalid("TI y autoridad deben ser personas distintas");
        for(var a:List.of(ti,authority))if(!codes.contains(text(a,"ambito",48,true)))throw invalid("ambito de adscripcion de "+text(a,"usuario",64,true));
        var granted=new LinkedHashSet<String>();
        var list=authority.path("ambitos");if(!list.isArray()||list.isEmpty())throw invalid("ambitos de la autoridad");
        for(var g:list){String c=g.asString("");if(c.equals("*"))granted.addAll(codes);else if(codes.contains(c))granted.add(c);else throw invalid("ambito de la autoridad "+c);}

        Path credentials=data.resolve("bootstrap-usuarios.csv");
        if(Files.exists(credentials))throw new IllegalStateException("Existe un archivo de acceso anterior; no se sobreescribe.");
        var csv=new StringBuilder("usuario,nombre,clave_temporal\n");
        tx.executeWithoutResult(status->{
            db.lock();if(db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE")>0)return;
            for(var u:UNITS.entrySet())if(db.count("SELECT COUNT(*) FROM org_unit WHERE code=?",u.getKey())==0)db.update("INSERT INTO org_unit(code,name) VALUES(?,?)",u.getKey(),u.getValue());
            Map<String,Long> ids=new HashMap<>();
            for(var sc:scopes){
                String area=text(sc,"area",48,false);
                ids.put(text(sc,"codigo",48,true),db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional) VALUES(?,?,?,?,?,?)",
                    text(sc,"codigo",48,true),text(sc,"nombre",160,true),text(sc,"unidad",24,true),area.isEmpty()?null:area,text(sc,"sede",48,true),sc.path("regional").asBoolean(false)));
            }
            long tiId=account(ti,ids,csv),authorityId=account(authority,ids,csv);
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,?,?)",tiId,System.currentTimeMillis(),"INICIALIZACION INSTITUCIONAL. "+basis);
            for(var c:granted)db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'AUTORIDAD',?,?,?)",authorityId,ids.get(c),System.currentTimeMillis(),"INICIALIZACION INSTITUCIONAL. "+basis);
            db.audit(tiId,"CONFIGURACION_INSTITUCIONAL_INICIAL","SISTEMA",0,null,ids.size()+" ambitos, una cuenta TI y una autoridad habilitante. "+basis);
            db.update("INSERT INTO application_marker(code,applied_ms) VALUES('INSTITUTIONAL_BOOTSTRAP',?)",System.currentTimeMillis());
            try{
                Files.writeString(credentials,csv.toString(),StandardOpenOption.CREATE_NEW);
                if(Files.getFileStore(credentials).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(credentials,PosixFilePermissions.fromString("rw-------"));
            }catch(Exception e){throw new IllegalStateException("No fue posible crear el archivo privado de accesos iniciales.");}
        });
    }
    private long account(tools.jackson.databind.JsonNode a,Map<String,Long> ids,StringBuilder csv){
        String pass=randomPassword(),username=user(a),name=text(a,"nombre",160,true),email=text(a,"correo",200,false);
        long id=db.insert("INSERT INTO staff(username,name,email,password_hash,home_scope,must_change) VALUES(?,?,?,?,?,TRUE)",username,name,email.isEmpty()?null:email,passwords.encode(pass),ids.get(text(a,"ambito",48,true)));
        csv.append(username).append(',').append(name.replace(',',' ')).append(',').append(pass).append('\n');
        return id;
    }
    private static String user(tools.jackson.databind.JsonNode a){
        if(!a.isObject())throw invalid("cuenta TI o autoridad");
        String u=text(a,"usuario",64,true).toLowerCase(Locale.ROOT);
        if(!u.matches("[a-z0-9][a-z0-9._-]{2,63}")||u.contains(":"))throw invalid("usuario "+u);
        return u;
    }
    private static String text(tools.jackson.databind.JsonNode node,String key,int max,boolean required){
        var v=node.path(key);
        if(v.isMissingNode()||v.isNull()){if(required)throw invalid(key);return "";}
        if(!v.isString())throw invalid(key);
        String s=v.asString().strip();
        if((required&&s.isEmpty())||s.length()>max||s.chars().anyMatch(Character::isISOControl))throw invalid(key);
        return s;
    }
    private static IllegalStateException invalid(String what){return new IllegalStateException("Archivo de inicializacion institucional invalido: "+what+".");}

    private void ensureDemoGrant(long user,String role,long scope){
        if(db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code=? AND scope_id=? AND revoked_at IS NULL",user,role,scope)==0)
            grant(user,role,scope);
    }
    private void grant(long user,String role,Long scope){db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,?,?)",user,role,scope,0,"CONFIGURACION SINTETICA DE PRUEBA");}
    private String randomPassword(){byte[] bytes=new byte[18];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"a7!";}
}
