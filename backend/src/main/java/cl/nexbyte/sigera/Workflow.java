package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Punto unico de escritura de negocio. Un COMMIT incluye estado, historial, avisos e idempotencia:
 * lock global corto, actor resuelto en servidor, clave de operacion y registro del resultado.
 * Cada dominio implementa sus comandos en su propio servicio.
 */
@Service
public class Workflow {
    public record Command(UUID key,String type,Long id,Long revision,Map<String,Object> data) { }
    public record Result(String kind,long id,String message) { }

    private final Db db;
    private final Access access;
    private final IntakeCommands intake;
    private final RequirementCommands requirements;
    private final CaseCommands cases;
    private final Attention attention;
    private final Personnel personnel;
    private final JsonMapper json=JsonMapper.builder().build();

    public Workflow(Db db,Access access,IntakeCommands intake,RequirementCommands requirements,CaseCommands cases,Attention attention,Personnel personnel){
        this.db=db;
        this.access=access;
        this.intake=intake;
        this.requirements=requirements;
        this.cases=cases;
        this.attention=attention;
        this.personnel=personnel;
    }

    @Transactional
    public Result run(String username,Command cmd){
        if(cmd==null||cmd.key()==null||cmd.type()==null)throw new Problem(400,"Falta clave de operacion o comando.");
        db.lock();
        Actor a=access.actor(username);
        if(a.mustChange())throw new Problem(428,"Cambia la clave temporal antes de operar.");
        String hash=hash(Arrays.asList(cmd.type(),cmd.id(),cmd.revision(),ordered(cmd.data())));
        var previous=db.rows("SELECT * FROM command_log WHERE command_key=?",cmd.key().toString());
        if(!previous.isEmpty()){
            var p=previous.getFirst();
            require(n(p,"actor_id")==a.id()&&s(p,"payload_hash").equals(hash),"La clave de operacion ya se uso con otros datos.");
            return new Result(s(p,"result_kind"),n(p,"result_id"),s(p,"result_message"));
        }
        var in=new Input(cmd.data());
        long id=cmd.id()==null?0:cmd.id();
        String type=cmd.type();
        Long expected=cmd.revision();
        Result result=switch(type){
            case "ATTENTION_CREATE","ATTENTION_SAVE","ATTENTION_SEND","ATTENTION_DECIDE","ATTENTION_RESOLVE" -> attention.run(a,type,id,expected,in);
            case "COM_CREATE","COM_IMMEDIATE","COM_ROUTE","COM_ASSUME","SOL_ROUTE","SOL_RETURN","SOL_ASSUME","SOL_DISCARD","COM_LINK","LINK_DECIDE","LINK_RETURN" -> intake.run(a,type,id,expected,in);
            case "REQ_CLASSIFY","REQ_CASE_CREATE","REQ_CLOSE","REQ_NOTE","FLOW_REQUEST","FLOW_DECIDE","REOPEN_REQUEST","REOPEN_AUTHORIZE","REOPEN_DECIDE" -> requirements.run(a,type,id,expected,in);
            case "CASE_NOTE","CASE_ASSIGN","CASE_ACK","CASE_MEMBER_ADD","CASE_MEMBER_REMOVE","CASE_REQUEST_CLOSE","CASE_WITHDRAW_CLOSE","CASE_CLOSE","CASE_RETURN","CASE_WAIT","CASE_RESUME","CASE_MATTER",
                 "NOTE_RECTIFY","TASK_CREATE","TASK_EDIT","TASK_COMPLETE","TASK_CANCEL","TASK_ATTEMPT" -> cases.run(a,type,id,expected,in);
            case "NOTICE_READ" -> readNotice(a,id);
            case "STAFF_CREATE","STAFF_UPDATE","STAFF_RESET","GRANT_CREATE","GRANT_REVOKE","SCOPE_CREATE","DESIGNATE","SUBSTITUTE_ACTIVATE","SUBSTITUTE_END","DOC_RELEASE","INTEGRATION_PAUSE","INTEGRATION_RESUME","MAIL_RESOLVE" -> personnel.run(a,type,id,expected,in);
            default -> throw new Problem(400,"Comando desconocido.");
        };
        db.update("INSERT INTO command_log(command_key,actor_id,payload_hash,result_kind,result_id,result_message,created_ms) VALUES(?,?,?,?,?,?,?)",
            cmd.key().toString(),a.id(),hash,result.kind(),result.id(),result.message(),Lifecycle.now());
        return result;
    }

    /**
     * Entrada interna para conservar el original de una Comunicacion externa (Formulario completo via SMR).
     * No es un comando: no figura en run(), por lo que /api/commands lo rechaza como desconocido para
     * cualquier sesion funcionaria, sin importar sus funciones. Queda reservada al futuro receptor tecnico
     * autorizado, que sera responsable de autenticar la fuente. Misma transaccion y lock global que run().
     * Antes de escribir se verifican huellas y sello Ed25519 de custodia con las claves confiables del
     * operador (FormularioKeys); sin claves configuradas, todo original se rechaza.
     */
    @Transactional
    public Result conserveExternal(String username,long scope,String document){
        db.lock();
        return intake.conserveExternal(access.actor(username),scope,document);
    }

    static Result done(String kind,long id){return Lifecycle.done(kind,id);}

    private Result readNotice(Actor a,long id){
        db.update("UPDATE inbox_notice SET read_ms=? WHERE id=? AND user_id=? AND read_ms IS NULL",Lifecycle.now(),id,a.id());
        return done("AVISO",id);
    }
    private static Object ordered(Object value){
        if(value instanceof Map<?,?> m){var sorted=new TreeMap<String,Object>();m.forEach((k,v)->sorted.put(k.toString(),ordered(v)));return sorted;}
        if(value instanceof List<?> l)return l.stream().map(Workflow::ordered).toList();
        return value;
    }
    private String hash(Object value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new Problem(400,"No se pudo interpretar el comando.");}
    }
}
