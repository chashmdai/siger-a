package cl.nexbyte.sigera;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOG=LoggerFactory.getLogger(Bootstrap.class);
    final Db db;final PasswordEncoder passwords;final TransactionTemplate tx;final ConfigurableApplicationContext ctx;
    final Path data;final boolean seed,migrate;final String institutional;final PersonIndex persons;final Milestones milestones;final OriginRelease origin;final OriginRegion regions;final Attention attention;
    public Bootstrap(Db db,PasswordEncoder passwords,PlatformTransactionManager manager,ConfigurableApplicationContext ctx,
                     @Value("${siger.data}")String data,@Value("${siger.seed-demo:false}")boolean seed,@Value("${siger.migrate-only:false}")boolean migrate,
                     @Value("${siger.bootstrap-file:}")String institutional,PersonIndex persons,Milestones milestones,OriginRelease origin,OriginRegion regions,Attention attention){
        this.db=db;this.passwords=passwords;this.tx=new TransactionTemplate(manager);this.ctx=ctx;this.data=Path.of(data).toAbsolutePath().normalize();this.seed=seed;this.migrate=migrate;
        this.institutional=institutional.strip();this.persons=persons;this.milestones=milestones;this.origin=origin;this.regions=regions;this.attention=attention;
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
            // Region sintetica de la sede de pruebas (0.5.0, D8); solo si sigue siendo la sede sintetica sin region.
            db.update("UPDATE org_scope SET region_code=?,revision=revision+1 WHERE code='REG-DEMO' AND regional=TRUE AND region_code IS NULL AND LOWER(name) LIKE '%sintetica%'",DEMO_REGION);
        }
        suppressAuthority();
        convertRegionalHeads();
        persons.rebuild(); // Carga inicial del indice de personas (V12), una sola vez.
        milestones.rebuild(); // Carga historica de hitos (V14), una sola vez; cuenta lo que no reconoce.
        regions.rebuild(); // Region de cada version de la ficha (V16, D21), una sola vez.
        attention.rebuildSummaries(); // Resumen de cambios de las versiones existentes de la ficha (V18, E03), una sola vez.
        attention.rebuildImmediateResults(); // Resultado de la primera atencion desde su constancia exacta (V20, E06), una sola vez.
        // D17: auditoria del modo de emergencia y liberacion de originales del Formulario pendientes (no en migrate-only,
        // la prueba de Graph, la sonda ni la recuperacion de TI).
        if(origin!=null)origin.startup();
        warnAdministration();
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
            for(var s:scopes){boolean regional=s[0].equals("REG-DEMO");long id=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional,region_code) VALUES(?,?,?,?,?,?,?)",s[0],s[1],s[2],s[3].isBlank()?null:s[3],regional?"SEDE-DEMO":"CENTRAL",regional,regional?DEMO_REGION:null);ids.put(s[0],id);}
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
                {"coordinacion.regional","Coordinacion regional sintetica","REG-DEMO","DIRECCION"}, // D7: rango de Direccion en su sede
                {"profesional.regional","Profesional regional sintetico","REG-DEMO","PROFESIONAL"},
                {"ti","Administracion tecnica sintetica","UGI","TI"},
                {"auditoria","Auditoria de prueba","UPR-REQ","AUDITORIA"},
                {"gabinete","Consulta autorizada de prueba","UPR-REQ","LECTURA"}
            };
            Map<String,Long> people=new HashMap<>();
            for(var u:users){
                String pass=randomPassword();long scope=ids.get(u[2]);
                long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope) VALUES(?,?,?,?)",u[0],u[1],passwords.encode(pass),scope);
                people.put(u[0],id);csv.append(u[0]).append(',').append(u[1]).append(',').append(pass).append('\n');
                if(u[3].equals("DIRECCION")){
                    String unit=u[2];
                    for(var e:ids.entrySet())if((e.getKey().equals(unit)||e.getKey().startsWith(unit+"-"))&&!e.getKey().equals("UPR-REQ"))grant(id,"DIRECCION",e.getValue());
                    if(unit.equals("UPR"))grant(id,"LECTURA",ids.get("UPR-REQ"));
                }else grant(id,u[3],Set.of("TI","DISTRIBUIDOR").contains(u[3])?null:scope);
                if(u[3].equals("JEFATURA")||u[2].equals("REG-DEMO")&&u[3].equals("DIRECCION"))grant(id,"PROFESIONAL",scope);
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
     * (siger.bootstrap-file) se crean solo los ambitos reales informados, una cuenta TI (administra el sistema, sin
     * acceso a expedientes; desde 0.5.0 tambien concede las funciones, D1) y, opcionalmente, los funcionarios con sus
     * funciones del catalogo Roles, todos con clave temporal individual y cambio obligatorio. Solo actua con la tabla
     * de personal vacia; un archivo invalido detiene el arranque sin escribir.
     */
    void institutional(Path file)throws Exception{
        if(!Files.isRegularFile(file)||Files.size(file)>262_144)throw new IllegalStateException("Archivo de inicializacion institucional ausente o demasiado grande.");
        var reader=tools.jackson.databind.json.JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
        tools.jackson.databind.JsonNode root;
        // Se admite la marca BOM de UTF-8 que agregan Windows PowerShell 5.1 y algunos editores.
        String content=Files.readString(file,java.nio.charset.StandardCharsets.UTF_8);
        if(!content.isEmpty()&&content.charAt(0)==0xFEFF)content=content.substring(1);
        try{root=reader.readTree(content);}catch(RuntimeException e){throw new IllegalStateException("Archivo de inicializacion institucional con JSON invalido.");}
        // AUTORIDAD fue suprimida (D1): un archivo preparado para 0.4.0 se rechaza entero, nunca se ignora en silencio.
        if(!root.path("autoridad").isMissingNode())throw invalid("desde 0.5.0 TI administra funciones; retira la entrada autoridad");
        String basis=text(root,"fundamento",1000,true);
        var scopes=root.path("ambitos");if(!scopes.isArray()||scopes.isEmpty()||scopes.size()>500)throw invalid("ambitos (1 a 500)");
        var codes=new LinkedHashSet<String>();var regionalCodes=new HashSet<String>();
        for(var sc:scopes){
            String code=text(sc,"codigo",48,true);
            if(!code.matches("[A-Z0-9_-]{2,48}")||!codes.add(code))throw invalid("codigo de ambito "+code);
            if(!UNITS.containsKey(text(sc,"unidad",24,true)))throw invalid("unidad del ambito "+code);
            text(sc,"nombre",160,true);text(sc,"sede",48,true);text(sc,"area",48,false);
            coverage(sc,site(sc,code),code);
            if(sc.path("regional").asBoolean(false))regionalCodes.add(code);
        }
        var centralCoverage=coverage(root.path("coberturaNivelCentral"),"coberturaNivelCentral");
        var ti=root.path("ti");String tiUser=user(ti);
        if(!codes.contains(text(ti,"ambito",48,true)))throw invalid("ambito de adscripcion de "+tiUser);
        // Funcionarios opcionales con sus funciones del catalogo (mismas reglas que GRANT_CREATE y PROFILE_SET): una
        // jefatura o direccion por ambito, varios distribuidores (D3) y TI solo como funcion unica (D2).
        var staff=root.path("funcionarios");if(!staff.isMissingNode()&&(!staff.isArray()||staff.size()>500))throw invalid("funcionarios (hasta 500)");
        var usernames=new HashSet<>(List.of(tiUser));var heads=new HashSet<String>();
        for(var f:staff.isArray()?staff:List.<tools.jackson.databind.JsonNode>of()){
            String u=user(f);if(!usernames.add(u))throw invalid("usuario repetido "+u);
            if(!codes.contains(text(f,"ambito",48,true)))throw invalid("ambito de adscripcion de "+u);
            var roles=f.path("funciones");if(!roles.isArray()||roles.isEmpty())throw invalid("funciones de "+u);
            var own=new HashSet<String>();
            for(var g:roles){
                var role=role(text(g,"funcion",32,true),u);String scope="";
                if(role.scoped()){scope=text(g,"ambito",48,true);if(!codes.contains(scope))throw invalid("ambito de la funcion de "+u);
                    if(role.code().equals("JEFATURA")&&regionalCodes.contains(scope))throw invalid("la sede "+scope+" no tiene Jefatura: la Coordinacion regional se registra como DIRECCION ("+u+")");}
                else if(role.optional()){if(!g.path("ambito").isMissingNode()){scope=text(g,"ambito",48,true);if(!codes.contains(scope))throw invalid("ambito de la funcion de "+u);}}
                else if(!g.path("ambito").isMissingNode())throw invalid("la funcion "+role.code()+" es institucional y no lleva ambito ("+u+")");
                if(!own.add(role.code()+"@"+scope))throw invalid("funcion "+role.code()+" repetida para "+u);
                if(Personnel.HEADS.contains(role.code())&&!heads.add(scope))throw invalid("mas de una jefatura o direccion en "+scope);
            }
            if(own.stream().anyMatch(k->k.startsWith(Roles.TI+"@"))&&own.size()>1)throw invalid("TI es incompatible con cualquier otra funcion ("+u+")");
            for(var k:own){String role=k.substring(0,k.indexOf('@'));
                if(Roles.NEEDS_REPORTES.contains(role)&&!own.contains(Roles.REPORTES+k.substring(role.length())))throw invalid(role+" exige REPORTES en el mismo alcance ("+u+")");}
        }

        Path credentials=data.resolve("bootstrap-usuarios.csv");
        if(Files.exists(credentials))throw new IllegalStateException("Existe un archivo de acceso anterior; no se sobreescribe.");
        var csv=new StringBuilder("usuario,nombre,clave_temporal\n");
        tx.executeWithoutResult(status->{
            db.lock();if(db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE")>0)return;
            for(var u:UNITS.entrySet())if(db.count("SELECT COUNT(*) FROM org_unit WHERE code=?",u.getKey())==0)db.update("INSERT INTO org_unit(code,name) VALUES(?,?)",u.getKey(),u.getValue());
            Map<String,Long> ids=new HashMap<>();
            for(var sc:scopes){
                String area=text(sc,"area",48,false),code=text(sc,"codigo",48,true);var site=site(sc,code);
                ids.put(code,db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code,regional,region_code,state,enabled,opens_ms) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    code,text(sc,"nombre",160,true),text(sc,"unidad",24,true),area.isEmpty()?null:area,text(sc,"sede",48,true),site.regional(),
                    site.region(),site.state(),site.state().equals(Access.ACTIVE),site.opens()));
            }
            long tiId=account(ti,ids,csv);
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,?,?)",tiId,System.currentTimeMillis(),"INICIALIZACION INSTITUCIONAL. "+basis);
            // Cobertura territorial inicial (0.7.0, D19): periodos abiertos a nombre de la cuenta TI inicial, con el fundamento del archivo.
            long now=System.currentTimeMillis();String coverageBasis="INICIALIZACION INSTITUCIONAL. "+basis;
            for(var sc:scopes){String code=text(sc,"codigo",48,true);
                Territory.apply(db,tiId,ids.get(code),List.of(),coverage(sc,site(sc,code),code),coverageBasis,now);}
            Territory.apply(db,tiId,null,List.of(),centralCoverage,coverageBasis,now);
            int people=0;
            for(var f:staff.isArray()?staff:List.<tools.jackson.databind.JsonNode>of()){
                long id=account(f,ids,csv);people++;
                for(var g:f.path("funciones")){var role=Roles.require(text(g,"funcion",32,true));
                    db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,?,?)",id,role.code(),role.scoped()||role.optional()&&!g.path("ambito").isMissingNode()?ids.get(text(g,"ambito",48,true)):null,System.currentTimeMillis(),"INICIALIZACION INSTITUCIONAL. "+basis);}
            }
            db.audit(tiId,"CONFIGURACION_INSTITUCIONAL_INICIAL","SISTEMA",0,null,ids.size()+" ambitos, una cuenta TI y "+people+" funcionarios. "+basis);
            db.update("INSERT INTO application_marker(code,applied_ms) VALUES('INSTITUTIONAL_BOOTSTRAP',?)",System.currentTimeMillis());
            try{
                Files.writeString(credentials,csv.toString(),StandardOpenOption.CREATE_NEW);
                if(Files.getFileStore(credentials).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(credentials,PosixFilePermissions.fromString("rw-------"));
            }catch(Exception e){throw new IllegalStateException("No fue posible crear el archivo privado de accesos iniciales.");}
        });
    }
    private record Site(boolean regional,String region,String state,Long opens){ }
    /**
     * Datos de sede de un ambito del archivo (0.5.0, D7-D8): una sede regional lleva region (codigo del Formulario),
     * estado ACTIVA (por defecto) o PROYECTADA y fecha de apertura opcional aaaa-mm-dd; el nivel central no los lleva.
     */
    private static Site site(tools.jackson.databind.JsonNode sc,String code){
        boolean regional=sc.path("regional").asBoolean(false);
        String region=text(sc,"region",4,false),state=text(sc,"estado",12,false),opening=text(sc,"apertura",10,false);
        if(state.isEmpty())state=Access.ACTIVE;
        if(!regional){
            if(!region.isEmpty()||!opening.isEmpty()||!state.equals(Access.ACTIVE))throw invalid("region, estado y apertura solo aplican a sedes regionales ("+code+")");
            return new Site(false,null,state,null);
        }
        if(!AdultIdentity.validRegion(region))throw invalid("region de la sede "+code+" (codigo del Formulario, por ejemplo 05)");
        if(!state.equals(Access.ACTIVE)&&!state.equals(Access.PROJECTED))throw invalid("estado de la sede "+code+" (ACTIVA o PROYECTADA)");
        Long opens=null;
        if(!opening.isEmpty()){
            try{opens=java.time.LocalDate.parse(opening).atStartOfDay(ExternalProjection.ZONE).toInstant().toEpochMilli();}
            catch(java.time.DateTimeException e){throw invalid("apertura de la sede "+code+" (aaaa-mm-dd)");}
        }else if(state.equals(Access.ACTIVE))opens=System.currentTimeMillis();
        return new Site(true,region,state,opens);
    }
    /**
     * Cobertura adicional de un ambito del archivo (0.7.0, D18-D19): "cobertura" opcional, solo en sedes regionales, con
     * las reglas de SCOPE_COVERAGE (codigos del Formulario, sin repetir y sin la region fisica de la sede).
     */
    private static List<String> coverage(tools.jackson.databind.JsonNode sc,Site site,String code){
        var node=sc.path("cobertura");if(node.isMissingNode())return List.of();
        if(!site.regional())throw invalid("cobertura solo aplica a sedes regionales ("+code+")");
        var regions=coverage(node,"cobertura de la sede "+code);
        if(regions.contains(site.region()))throw invalid("cobertura de la sede "+code+": la region fisica "+site.region()+" no va en la cobertura adicional");
        return regions;
    }
    /** Lista de regiones del archivo (cobertura de una sede o del nivel central): arreglo de codigos del Formulario sin repetir. */
    private static List<String> coverage(tools.jackson.databind.JsonNode node,String label){
        if(node.isMissingNode())return List.of();
        if(!node.isArray()||node.size()>AdultIdentity.REGIONS.size())throw invalid(label+" (arreglo de codigos de region)");
        var out=new ArrayList<String>();
        for(var r:node){
            if(!r.isString()||!AdultIdentity.validRegion(r.asString()))throw invalid(label+": region "+(r.isString()?r.asString():"invalida")+" (codigo del Formulario, por ejemplo 05)");
            if(out.contains(r.asString()))throw invalid(label+": region "+r.asString()+" repetida");
            out.add(r.asString());
        }
        return Territory.ordered(out);
    }
    /** Funcion del catalogo unico; AUTORIDAD (suprimida) o un codigo desconocido invalidan el archivo. */
    private static Roles.Role role(String code,String user){
        if(code.equals("AUTORIDAD"))throw invalid("la funcion AUTORIDAD fue suprimida en 0.5.0; TI concede las funciones ("+user+")");
        try{return Roles.require(code);}catch(Problem p){throw invalid("funcion "+code+" de "+user);}
    }

    // --- Supresion de AUTORIDAD (0.5.0, D1) ----------------------------------------------------------
    static final String AUTHORITY_MARKER="ROL_AUTORIDAD_SUPRIMIDO_050";
    /** Identidad tecnica de las rutinas de actualizacion y recuperacion: sin sesion, sin funciones y fuera de las listas. */
    static final String MAINTENANCE="sistema:actualizacion",MAINTENANCE_NAME="Actualización técnica de SIGER";
    /**
     * Rutina idempotente de arranque: revoca (nunca borra) las concesiones AUTORIDAD vigentes o programadas, con un
     * evento FUNCION_REVOCADA por cada una a nombre de la identidad tecnica sistema:actualizacion, y deja el marcador.
     * Las vencidas o ya revocadas quedan intactas. No toca cuentas que combinen TI con otras funciones (solo se avisan).
     */
    void suppressAuthority(){
        tx.executeWithoutResult(status->{
            db.lock();
            if(db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",AUTHORITY_MARKER)>0)return;
            long now=System.currentTimeMillis();
            var live=db.rows("SELECT id,user_id,scope_id FROM role_assignment WHERE role_code='AUTORIDAD' AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until>?) ORDER BY id",now);
            if(!live.isEmpty()){
                long actor=maintenance(db);var users=new LinkedHashSet<Long>();
                for(var g:live){
                    db.update("UPDATE role_assignment SET revoked_at=? WHERE id=? AND revoked_at IS NULL",now,n(g,"id"));
                    db.audit(actor,"FUNCION_REVOCADA","USUARIO",n(g,"user_id"),nullableId(g,"scope_id"),"AUTORIDAD. Rol suprimido en 0.5.0 (D1)");
                    users.add(n(g,"user_id"));
                }
                for(long u:users)db.update("UPDATE staff SET security_version=security_version+1 WHERE id=?",u);
                LOG.info("Supresion de AUTORIDAD: {} concesiones revocadas en {} cuentas.",live.size(),users.size());
            }
            db.update("INSERT INTO application_marker(code,applied_ms) VALUES(?,?)",AUTHORITY_MARKER,now);
        });
    }
    // --- Coordinacion regional como Direccion de la sede (0.5.0, D7) ---------------------------------
    static final String REGIONAL_HEAD_MARKER="COORDINACION_REGIONAL_050",DEMO_REGION="05";
    /**
     * Rutina idempotente de arranque: cada JEFATURA vigente o programada en una sede regional se revoca (nunca se
     * borra) y se otorga DIRECCION en la misma sede con el mismo periodo, a nombre de sistema:actualizacion, con un
     * epoch por cuenta. Las subrogancias vivas sobre esa concesion pasan a la nueva (las terminadas o vencidas
     * conservan su historia). Si una DIRECCION se superpone en la misma sede, la JEFATURA no se convierte: queda
     * como aviso JEFATURA_EN_SEDE para que TI la resuelva en el perfil.
     */
    void convertRegionalHeads(){
        tx.executeWithoutResult(status->{
            db.lock();
            if(db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",REGIONAL_HEAD_MARKER)>0)return;
            long now=System.currentTimeMillis();
            var live=db.rows("SELECT g.* FROM role_assignment g JOIN org_scope s ON s.id=g.scope_id WHERE g.role_code='JEFATURA' AND s.regional=TRUE AND g.revoked_at IS NULL AND (g.valid_until IS NULL OR g.valid_until>?) ORDER BY g.id",now);
            Long actor=null;var users=new LinkedHashSet<Long>();int converted=0,kept=0;
            String basis="Coordinacion regional registrada como Direccion de la sede (0.5.0, D7)";
            for(var g:live){
                long id=n(g,"id"),user=n(g,"user_id"),scope=n(g,"scope_id"),from=n(g,"valid_from");Long until=nullableId(g,"valid_until");
                if(db.count("SELECT COUNT(*) FROM role_assignment WHERE scope_id=? AND role_code='DIRECCION' AND revoked_at IS NULL AND valid_from<? AND (valid_until IS NULL OR valid_until>?)",
                    scope,until==null?Long.MAX_VALUE:until,from)>0){kept++;continue;}
                if(actor==null)actor=maintenance(db);
                db.update("UPDATE role_assignment SET revoked_at=? WHERE id=? AND revoked_at IS NULL",now,id);
                db.audit(actor,"FUNCION_REVOCADA","USUARIO",user,scope,"JEFATURA. "+basis);
                long grant=db.insert("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,valid_until,granted_by,basis) VALUES(?,'DIRECCION',?,?,?,?,?)",user,scope,from,until,actor,basis);
                db.audit(actor,"FUNCION_OTORGADA","USUARIO",user,scope,"DIRECCION. "+basis);
                for(var x:db.rows("SELECT ss.substitution_id,d.substitute_id FROM substitution_scope ss JOIN substitution x ON x.id=ss.substitution_id JOIN substitution_designation d ON d.id=x.designation_id WHERE ss.grant_id=? AND x.ended_ms IS NULL AND x.until_ms>?",id,now)){
                    db.update("UPDATE substitution_scope SET grant_id=?,role_code='DIRECCION' WHERE substitution_id=? AND grant_id=?",grant,n(x,"substitution_id"),id);
                    db.audit(actor,"SUBROGANCIA_ACTUALIZADA","SUBROGANCIA",n(x,"substitution_id"),scope,"La subrogancia sigue sobre la Direccion de la sede. "+basis);
                    users.add(n(x,"substitute_id"));
                }
                users.add(user);converted++;
            }
            for(long u:users)db.update("UPDATE staff SET security_version=security_version+1 WHERE id=?",u);
            if(converted+kept>0)LOG.info("Coordinacion regional: {} jefaturas convertidas en Direccion y {} sin convertir por superposicion.",converted,kept);
            db.update("INSERT INTO application_marker(code,applied_ms) VALUES(?,?)",REGIONAL_HEAD_MARKER,now);
        });
    }
    /** Aviso en el log del servicio (solo codigos y cantidades, sin datos de personas); Administracion muestra lo mismo. */
    void warnAdministration(){
        if(db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE")==0)return;
        var all=new ArrayList<>(Personnel.warnings(db));if(origin!=null)all.addAll(origin.warnings());
        for(var w:all)LOG.warn("Administracion: {} (cantidad {}). Revisar en Administracion.",s(w,"code"),w.get("count"));
    }
    /**
     * Identidad tecnica sistema:actualizacion: cuenta deshabilitada, sin clave utilizable, sin funciones y marcada
     * technical (como la del Formulario). Se crea una vez; nunca adopta una cuenta de funcionario del mismo nombre.
     */
    static long maintenance(Db db){
        var rows=db.rows("SELECT * FROM staff WHERE username=?",MAINTENANCE);
        if(rows.isEmpty()){
            var home=db.rows("SELECT MIN(id) AS id FROM org_scope");
            if(home.isEmpty()||home.getFirst().get("id")==null)throw new IllegalStateException("No hay ambitos: la identidad tecnica de actualizacion no tiene adscripcion.");
            return db.insert("INSERT INTO staff(username,name,password_hash,enabled,must_change,home_scope,technical) VALUES(?,?,'!',FALSE,FALSE,?,TRUE)",MAINTENANCE,MAINTENANCE_NAME,n(home.getFirst(),"id"));
        }
        var u=rows.getFirst();long id=n(u,"id");
        if(!b(u,"technical")||b(u,"enabled")||db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",id)>0)
            throw new IllegalStateException("La identidad tecnica de actualizacion no es valida; no se aplica la rutina.");
        return id;
    }
    private long account(tools.jackson.databind.JsonNode a,Map<String,Long> ids,StringBuilder csv){
        String pass=randomPassword(),username=user(a),name=text(a,"nombre",160,true),email=text(a,"correo",200,false);
        long id=db.insert("INSERT INTO staff(username,name,email,password_hash,home_scope,must_change) VALUES(?,?,?,?,?,TRUE)",username,name,email.isEmpty()?null:email,passwords.encode(pass),ids.get(text(a,"ambito",48,true)));
        csv.append(username).append(',').append(name.replace(',',' ')).append(',').append(pass).append('\n');
        return id;
    }
    private static String user(tools.jackson.databind.JsonNode a){
        if(!a.isObject())throw invalid("cuenta TI o funcionario");
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
