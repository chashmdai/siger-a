package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
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
    final Integrations integrations;final String environment;
    public Api(Access access,Workflow flow,Queries queries,Documents documents,Db db,PasswordEncoder passwords,BoardQueries boards,Attention attention,Integrations integrations,
               @org.springframework.beans.factory.annotation.Value("${siger.environment-label:}")String environment){
        this.attention=attention;this.access=access;this.flow=flow;this.queries=queries;this.documents=documents;this.db=db;this.passwords=passwords;this.boards=boards;
        this.integrations=integrations;this.environment=environment.strip();
    }
    private String name(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof SecurityConfig.Principal))throw new Problem(401,"Inicia sesion.");return auth.getName();}
    @GetMapping("/session") public Map<String,Object> session(Authentication auth,CsrfToken csrf){
        Map<String,Object> result=new LinkedHashMap<>();result.put("csrf",Map.of("token",csrf.getToken(),"header",csrf.getHeaderName()));
        boolean logged=auth!=null&&auth.getPrincipal() instanceof SecurityConfig.Principal;result.put("authenticated",logged);result.put("environment",environment);
        if(logged){var a=access.actor(name(auth));result.put("user",Map.of("id",a.id(),"username",a.username(),"name",a.name(),"home_scope",a.homeScope(),"must_change",a.mustChange(),"grants",a.grants(),"substitutions",a.substitutions()));}
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
    @GetMapping("/catalog") public Map<String,Object> catalog(Authentication a){return queries.catalog(access.actor(name(a)));}
    @GetMapping("/notices/unread") public Map<String,Long> unread(Authentication a){return Map.of("unread",db.count("SELECT COUNT(*) FROM inbox_notice WHERE user_id=? AND read_ms IS NULL",access.actor(name(a)).id()));}
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void ready(){ready=true;}
    @GetMapping("/health") public ResponseEntity<Map<String,Object>> health(){
        try{
            if(!ready)return ResponseEntity.status(503).body(Map.of("status","STARTING","version","0.4.0"));
            Problem.require(db.count("SELECT COUNT(*) FROM app_meta WHERE namespace='SIGER_A_2026'")==1,"Instancia no disponible.");
            var last=db.one("SELECT version FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1");
            // Codigos de estado de las integraciones (sin contadores, errores ni datos) para Estado.ps1 y monitoreo local.
            return ResponseEntity.ok(Map.of("status","UP","version","0.4.0","schema",s(last,"version"),
                "integrations",Map.of("formulario",integrations.health(Integrations.FORMULARIO),"correo",integrations.health(Integrations.CORREO))));
        }catch(RuntimeException e){return ResponseEntity.status(503).body(Map.of("status","UNAVAILABLE","version","0.4.0"));}
    }
    @GetMapping("/dashboard") public Map<String,Object> dashboard(Authentication a){return boards.dashboard(access.actor(name(a)));}
    @GetMapping("/worklist/{kind}") public Map<String,Object> page(Authentication a,@PathVariable String kind,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="all")String filter,@RequestParam(required=false)Long scope){return boards.page(access.actor(name(a)),kind,page,size,q,filter,scope);}
    @GetMapping("/intake/documents") public List<Map<String,Object>> draftDocuments(Authentication a){return documents.drafts(name(a));}
    @PostMapping(value="/intake/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> stageDocument(Authentication a,@RequestParam long scope,@RequestParam UUID key,@RequestParam MultipartFile file)throws Exception{return documents.stage(name(a),scope,key,file);}
    @DeleteMapping("/intake/documents/{key}") public ResponseEntity<Void> discardDocument(Authentication a,@PathVariable UUID key)throws Exception{documents.discard(name(a),key);return ResponseEntity.noContent().build();}
    @GetMapping("/workspace") public Map<String,Object> workspace(Authentication a){return queries.workspace(access.actor(name(a)));}
    @GetMapping("/detail/{kind}/{id}") public Map<String,Object> detail(Authentication a,@PathVariable String kind,@PathVariable long id){return queries.detail(access.actor(name(a)),kind,id);}
    @GetMapping("/administration") public Map<String,Object> administration(Authentication a){return queries.administration(access.actor(name(a)));}
    @PostMapping("/commands") public Workflow.Result command(Authentication a,@RequestBody Workflow.Command command){return flow.run(name(a),command);}
    @PostMapping(value="/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> upload(Authentication a,@RequestParam String kind,@RequestParam long target,@RequestParam(defaultValue="false")boolean reserved,@RequestParam(required=false)Long note,@RequestParam(required=false)Long previous,@RequestParam(defaultValue="OTRO")String type,@RequestParam(defaultValue="")String description,@RequestParam UUID key,@RequestParam MultipartFile file)throws Exception{return documents.upload(name(a),kind,target,reserved,note,previous,type,description,key,file);}
    /** Analisis antivirus de un original en cuarentena (solo TI). Registra evidencia; no libera ni entrega contenido. */
    @PostMapping("/documents/{id}/scan") public Map<String,Object> scan(Authentication a,@PathVariable long id){return documents.scan(name(a),id);}
    @GetMapping("/documents/{id}/download") public ResponseEntity<FileSystemResource> download(Authentication a,@PathVariable long id)throws Exception{
        var d=documents.download(access.actor(name(a)),id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(d.size()).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox").header("Content-Disposition",ContentDisposition.attachment().filename(d.name(),StandardCharsets.UTF_8).build().toString()).body(new FileSystemResource(d.path()));
    }
    @GetMapping("/export") public ResponseEntity<byte[]> export(Authentication auth,@RequestParam String kind){
        var a=access.actor(name(auth));if(!Set.of("REQ","CAS","COM").contains(kind))throw new Problem(400,"Reporte desconocido.");
        String board=switch(kind){case "REQ"->"req";case "CAS"->"cas";default->"com";};
        var rows=new ArrayList<Map<String,Object>>();
        for(int page=0;;page++){
            var chunk=boards.page(a,board,page,100,"","all",null);
            Problem.require(((Number)chunk.get("total")).longValue()<=10000,"El resumen excede 10000 filas. Utiliza una extraccion acotada; no se entrega un archivo truncado.");
            @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)chunk.get("items");rows.addAll(items);
            if(rows.size()>=((Number)chunk.get("total")).longValue())break;
        }
        var out=new StringBuilder("\ufefffolio,estado,ambito,creado\r\n");
        for(var row:rows)out.append(csv(s(row,"folio"))).append(',').append(csv(s(row,"state"))).append(',').append(csv(Objects.toString(row.getOrDefault("principal_scope",row.getOrDefault("scope_id",row.get("origin_scope"))),""))).append(',').append(csv(s(row,"created_ms"))).append("\r\n");
        db.audit(a.id(),"EXPORTACION","SISTEMA",0,null,"Resumen "+kind+"; filas autorizadas "+rows.size());
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).header("Cache-Control","no-store").header("Content-Disposition",ContentDisposition.attachment().filename("siger-"+kind.toLowerCase(Locale.ROOT)+".csv").build().toString()).body(out.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String csv(String s){String safe=s.replace("\r"," ").replace("\n"," ");if(safe.matches("^[=+@\\t-].*"))safe="'"+safe;return "\""+safe.replace("\"","\"\"")+"\"";}
}
