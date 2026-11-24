package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cl.nexbyte.sigera.Db.*;

@RestController
@RequestMapping("/api")
public class Api {
    final Attention attention;final BoardQueries boards;volatile boolean ready;
    final Access access;final Workflow flow;final Queries queries;final Documents documents;final Db db;final PasswordEncoder passwords;
    final Integrations integrations;final PersonSearch search;final Holidays holidays;final Reports reports;final Personnel personnel;final StatCatalogs catalogs;final IntakeRecord intake;final String environment;final String releaseVersion;
    public Api(Access access,Workflow flow,Queries queries,Documents documents,Db db,PasswordEncoder passwords,BoardQueries boards,Attention attention,Integrations integrations,PersonSearch search,Holidays holidays,Reports reports,Personnel personnel,StatCatalogs catalogs,IntakeRecord intake,
               @org.springframework.beans.factory.annotation.Value("${siger.environment-label:}")String environment,BuildProperties buildProperties){
        this.attention=attention;this.access=access;this.flow=flow;this.queries=queries;this.documents=documents;this.db=db;this.passwords=passwords;this.boards=boards;
        this.integrations=integrations;this.search=search;this.holidays=holidays;this.reports=reports;this.personnel=personnel;this.catalogs=catalogs;this.intake=intake;this.environment=environment.strip();this.releaseVersion=buildProperties.getVersion();
    }
    private String name(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof SecurityConfig.Principal))throw new Problem(401,"Inicia sesion.");return auth.getName();}
    @GetMapping("/session") public Map<String,Object> session(Authentication auth,CsrfToken csrf){
        Map<String,Object> result=new LinkedHashMap<>();result.put("csrf",Map.of("token",csrf.getToken(),"header",csrf.getHeaderName()));
        boolean logged=auth!=null&&auth.getPrincipal() instanceof SecurityConfig.Principal;result.put("authenticated",logged);result.put("environment",environment);
        if(logged){var a=access.actor(name(auth));var user=new LinkedHashMap<String,Object>();user.put("id",a.id());user.put("username",a.username());user.put("name",a.name());user.put("home_scope",a.homeScope());user.put("must_change",a.mustChange());
            user.put("grants",a.grants());user.put("substitutions",a.substitutions());user.put("can_search",a.readsRecords());
            // G11/G12: reportes agregados, su desglose por funcionario y la exportacion de filas (cada uno con REPORTES en su alcance).
            user.put("can_report",Reports.canReport(a));user.put("can_report_people",Reports.canReportPeople(a));user.put("can_export",Reports.canExport(a));
            // E02 (D43): gestion estadistica con REPORTES en el mismo alcance (sin efecto hasta E10 y E11).
            user.put("can_manage_stats",!Roles.ruling(a,Roles.GESTION_ESTADISTICA).isEmpty());result.put("user",user);}
        return result;
    }
    @PostMapping("/password") @Transactional public Map<String,String> password(Authentication auth,@RequestBody Map<String,Object> body,HttpServletRequest req){
        db.lock();var a=access.actor(name(auth));var in=new Input(body);var user=db.one("SELECT password_hash FROM staff WHERE id=?",a.id());
        if(!passwords.matches(in.text("current",72,true),s(user,"password_hash")))throw new Problem(403,"La clave actual no es correcta.");
        String pass=Personnel.password(in.text("password",72,true));Problem.require(!passwords.matches(pass,s(user,"password_hash")),"La clave nueva debe ser diferente.");
        db.update("UPDATE staff SET password_hash=?,must_change=FALSE,security_version=security_version+1 WHERE id=?",passwords.encode(pass),a.id());
        db.audit(a.id(),"CLAVE_CAMBIADA","USUARIO",a.id(),null,"");
        // La sesion se cierra solo si la nueva clave y su auditoria quedaron confirmadas.
        var session=req.getSession(false);
        if(session!=null)org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){try{session.invalidate();}catch(IllegalStateException alreadyClosed){}}});
        return Map.of("message","Clave actualizada. Ingresa nuevamente.");
    }
    @GetMapping("/attention/{id}/versions/{version}") public Map<String,Object> attentionVersion(Authentication a,@PathVariable long id,@PathVariable long version){return attention.version(access.actor(name(a)),id,version);}
    /** Version anterior de la ficha de ingreso de una COM manual (0.8.0, E03), con el acceso de la lectura de la COM. */
    @GetMapping("/intake/{id}/versions/{version}") public Map<String,Object> intakeVersion(Authentication a,@PathVariable long id,@PathVariable long version){return intake.version(access.actor(name(a)),id,version);}
    @GetMapping("/catalog") public Map<String,Object> catalog(Authentication a){return queries.catalog(access.actor(name(a)));}
    @GetMapping("/notices/unread") public Map<String,Long> unread(Authentication a){return Map.of("unread",db.count("SELECT COUNT(*) FROM inbox_notice WHERE user_id=? AND read_ms IS NULL",access.actor(name(a)).id()));}
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void ready(){ready=true;}
    @GetMapping("/health") public ResponseEntity<Map<String,Object>> health(){
        try{
            if(!ready)return ResponseEntity.status(503).body(Map.of("status","STARTING","version",releaseVersion));
            Problem.require(db.count("SELECT COUNT(*) FROM app_meta WHERE namespace='SIGER_A_2026'")==1,"Instancia no disponible.");
            var last=db.one("SELECT version FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1");
            // Codigos de estado de las integraciones (sin contadores, errores ni datos) para Estado.ps1 y monitoreo local.
            return ResponseEntity.ok(Map.of("status","UP","version",releaseVersion,"schema",s(last,"version"),
                "integrations",Map.of("formulario",integrations.health(Integrations.FORMULARIO),"correo",integrations.health(Integrations.CORREO))));
        }catch(RuntimeException e){return ResponseEntity.status(503).body(Map.of("status","UNAVAILABLE","version",releaseVersion));}
    }
    @GetMapping("/dashboard") public Map<String,Object> dashboard(Authentication a){return boards.dashboard(access.actor(name(a)));}
    /** Bandeja paginada (G08c): POST con el texto en el cuerpo, nunca en la URL; CSRF como todo POST. GET responde 405. */
    record WorklistBody(Integer page,Integer size,String q,String filter,Long scope){}
    @PostMapping("/worklist/{kind}") public Map<String,Object> page(Authentication a,@PathVariable String kind,@RequestBody WorklistBody b){
        return boards.page(access.actor(name(a)),kind,b.page()==null?0:b.page(),b.size()==null?25:b.size(),b.q()==null?"":b.q(),b.filter()==null||b.filter().isBlank()?"all":b.filter(),b.scope());
    }
    /**
     * Buscador de personas y folios (G08): filtra por los permisos de lectura de la sesion y audita sin el termino en claro.
     * Desde G08c es POST con el termino y los filtros en el cuerpo JSON, nunca en la URL; CSRF como todo POST. GET responde 405.
     */
    record SearchBody(String q,String as,String role,Boolean open,Long scope,String region,String from,String to){}
    @PostMapping("/search") public Map<String,Object> search(Authentication a,@RequestBody SearchBody b){
        return search.search(access.actor(name(a)),b.q()==null?"":b.q(),b.as(),PersonSearch.Filters.of(b.role(),Boolean.TRUE.equals(b.open()),b.scope(),b.region(),b.from(),b.to()));
    }
    /** Reportes (G11): filtros en el cuerpo JSON, con CSRF como todo POST; GET responde 405. */
    @PostMapping("/reports") public Map<String,Object> reports(Authentication a,@RequestBody Reports.Request b){return reports.report(access.actor(name(a)),b);}
    /** CSV de REQ o Casos (G12, EXPORTACION): filtros en el cuerpo como en /reports; CSRF; GET responde 405. */
    @PostMapping("/reports/export/{kind}") public ResponseEntity<byte[]> exportRows(Authentication a,@PathVariable String kind,@RequestBody Reports.Request b){
        var x=reports.export(access.actor(name(a)),kind,b);return csv(x.filename(),x.bytes());
    }
    @GetMapping("/intake/documents") public List<Map<String,Object>> draftDocuments(Authentication a){return documents.drafts(name(a));}
    @PostMapping(value="/intake/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> stageDocument(Authentication a,@RequestParam long scope,@RequestParam UUID key,@RequestParam MultipartFile file)throws Exception{return documents.stage(name(a),scope,key,file);}
    @DeleteMapping("/intake/documents/{key}") public ResponseEntity<Void> discardDocument(Authentication a,@PathVariable UUID key)throws Exception{documents.discard(name(a),key);return ResponseEntity.noContent().build();}
    @GetMapping("/workspace") public Map<String,Object> workspace(Authentication a){return queries.workspace(access.actor(name(a)));}
    @GetMapping("/detail/{kind}/{id}") public Map<String,Object> detail(Authentication a,@PathVariable String kind,@PathVariable long id){return queries.detail(access.actor(name(a)),kind,id);}
    /** Calendario habil (G09): feriados para TI; las demas cuentas reciben 403. */
    @GetMapping("/calendar") public Map<String,Object> calendar(Authentication a){return holidays.calendar(access.actor(name(a)));}
    /** Catalogos estadisticos (0.8.0, E01): todas las versiones y sus correspondencias, y el detalle de una version; solo TI (403 al resto). */
    @GetMapping("/stat-catalogs") public Map<String,Object> statCatalogs(Authentication a){return catalogs.administration(access.actor(name(a)));}
    @GetMapping("/stat-catalogs/{id}") public Map<String,Object> statCatalog(Authentication a,@PathVariable long id){return catalogs.version(access.actor(name(a)),id);}
    @GetMapping("/administration") public Map<String,Object> administration(Authentication a){return queries.administration(access.actor(name(a)));}
    /** Efecto territorial de un cambio hipotetico (0.7.0, D19; solo TI, POST con CSRF, sin escrituras): misma regla que los comandos. */
    @PostMapping("/administration/territorial-effect") public Map<String,Object> territorialEffect(Authentication a,@RequestBody Map<String,Object> body){return personnel.territorialPreview(access.actor(name(a)),new Input(body));}
    @PostMapping("/commands") public Workflow.Result command(Authentication a,@RequestBody Workflow.Command command){return flow.run(name(a),command);}
    @PostMapping(value="/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> upload(Authentication a,@RequestParam String kind,@RequestParam long target,@RequestParam(defaultValue="false")boolean reserved,@RequestParam(required=false)Long note,@RequestParam(required=false)Long previous,@RequestParam(defaultValue="OTRO")String type,@RequestParam(defaultValue="")String description,@RequestParam UUID key,@RequestParam MultipartFile file)throws Exception{return documents.upload(name(a),kind,target,reserved,note,previous,type,description,key,file);}
    /** Analisis antivirus de un original en cuarentena (solo TI). Registra evidencia; no libera ni entrega contenido. */
    @PostMapping("/documents/{id}/scan") public Map<String,Object> scan(Authentication a,@PathVariable long id){return documents.scan(name(a),id);}
    @GetMapping("/documents/{id}/download") public ResponseEntity<FileSystemResource> download(Authentication a,@PathVariable long id)throws Exception{
        var d=documents.download(access.actor(name(a)),id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(d.size()).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox").header("Content-Disposition",ContentDisposition.attachment().filename(d.name(),StandardCharsets.UTF_8).build().toString()).body(new FileSystemResource(d.path()));
    }
    /**
     * Resumen de bandejas (0.4.0; desde G12 exige EXPORTACION, Reports.canExport): folio, estado, ambito por su codigo y
     * creacion, solo de lo que la cuenta lee en esa bandeja. Sobre 10000 filas: 409, nunca un archivo truncado.
     */
    @GetMapping("/export") public ResponseEntity<byte[]> export(Authentication auth,@RequestParam String kind){
        var a=access.actor(name(auth));if(!Reports.canExport(a))Problem.denied();if(!Set.of("REQ","CAS","COM").contains(kind))throw new Problem(400,"Reporte desconocido.");
        String board=switch(kind){case "REQ"->"req";case "CAS"->"cas";default->"com";};
        String scopeColumn=switch(kind){case "REQ"->"principal_scope";case "CAS"->"scope_id";default->"origin_scope";};
        var rows=new ArrayList<Map<String,Object>>();
        for(int page=0;;page++){
            var chunk=boards.page(a,board,page,100,"","all",null);
            Problem.require(((Number)chunk.get("total")).longValue()<=10000,"El resumen excede 10000 filas. Utiliza una extraccion acotada; no se entrega un archivo truncado.");
            @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)chunk.get("items");rows.addAll(items);
            if(items.isEmpty()||rows.size()>=((Number)chunk.get("total")).longValue())break;
        }
        var codes=new HashMap<Long,String>();for(var x:db.rows("SELECT id,code FROM org_scope"))codes.put(n(x,"id"),s(x,"code"));
        var out=new Csv(List.of("folio","estado","ambito","creado"));
        for(var row:rows)out.row(Arrays.asList(s(row,"folio"),s(row,"state"),row.get(scopeColumn)==null?null:codes.get(((Number)row.get(scopeColumn)).longValue()),Csv.when(nullableId(row,"created_ms"))));
        db.audit(a.id(),"EXPORTACION","SISTEMA",0,null,"Resumen "+kind+"; filas autorizadas "+rows.size());
        return csv("siger-"+kind.toLowerCase(Locale.ROOT)+".csv",out.bytes());
    }
    private static ResponseEntity<byte[]> csv(String filename,byte[] body){
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition",ContentDisposition.attachment().filename(filename).build().toString()).body(body);
    }
}
