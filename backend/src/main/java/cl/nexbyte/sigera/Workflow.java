package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Rights rights;
    private final StatCuts cuts;
    private final StatViews views;
    private final StatSeries series;
    private final JsonMapper json=JsonMapper.builder().build();
    private static final Logger LOG=LoggerFactory.getLogger(Workflow.class);
    /** Codigo fijo del log tecnico cuando la espera entre comandos llega a su tope (reloj del servidor atrasado). */
    static final String CLOCK_BEHIND="RELOJ_ATRASADO";
    /** Tope de la espera entre comandos: solo se alcanza si el reloj del servidor retrocede. */
    static final long MAX_WAIT_MS=200;
    /** Instante de fin (command_log.created_ms) del ultimo comando de este proceso. */
    static volatile long lastEnd;

    public Workflow(Db db,Access access,IntakeCommands intake,RequirementCommands requirements,CaseCommands cases,Attention attention,Personnel personnel,Rights rights,
                    StatCuts cuts,StatViews views,StatSeries series){
        this.db=db;
        this.access=access;
        this.intake=intake;
        this.requirements=requirements;
        this.cases=cases;
        this.attention=attention;
        this.personnel=personnel;
        this.rights=rights;
        this.cuts=cuts;
        this.views=views;
        this.series=series;
    }

    @Transactional
    public Result run(String username,Command cmd){
        if(cmd==null||cmd.key()==null||cmd.type()==null)throw new Problem(400,"Falta clave de operacion o comando.");
        db.lock();
        afterPreviousCommand();
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
            case "COM_CREATE","COM_IMMEDIATE","COM_COMPLETE","COM_ROUTE","COM_ASSUME","COM_DISCARD","SOL_ROUTE","SOL_RETURN","SOL_ASSUME","SOL_DISCARD","COM_LINK","LINK_DECIDE","LINK_RETURN","STAT_CLASSIFY" -> intake.run(a,type,id,expected,in);
            case "REQ_CLASSIFY","REQ_CASE_CREATE","REQ_CLOSE","REQ_NOTE","REQ_MATTER","FLOW_REQUEST","FLOW_DECIDE","REOPEN_REQUEST","REOPEN_AUTHORIZE","REOPEN_DECIDE" -> requirements.run(a,type,id,expected,in);
            case "CASE_NOTE","CASE_ASSIGN","CASE_ACK","CASE_MEMBER_ADD","CASE_MEMBER_REMOVE","CASE_REQUEST_CLOSE","CASE_WITHDRAW_CLOSE","CASE_CLOSE","CASE_RETURN","CASE_WAIT","CASE_RESUME","CASE_MATTER",
                 "NOTE_RECTIFY","TASK_CREATE","TASK_EDIT","TASK_COMPLETE","TASK_CANCEL","TASK_ATTEMPT" -> cases.run(a,type,id,expected,in);
            case "RIGHTS_CLASSIFY" -> rights.classify(a,id,expected,in);
            case "NOTICE_READ" -> readNotice(a,id);
            case "STAFF_CREATE","STAFF_UPDATE","STAFF_RESET","GRANT_CREATE","GRANT_REVOKE","PROFILE_SET","SCOPE_CREATE","SCOPE_UPDATE","SCOPE_ACTIVATE","SCOPE_CLOSE","SCOPE_COVERAGE","CENTRAL_COVERAGE","DESIGNATE","SUBSTITUTE_ACTIVATE","SUBSTITUTE_END","DOC_RELEASE","INTEGRATION_PAUSE","INTEGRATION_RESUME","MAIL_RESOLVE",
                 "HOLIDAY_ADD","HOLIDAY_IMPORT","HOLIDAY_REMOVE","CATALOG_IMPORT","CATALOG_ACTIVATE","CATALOG_MAPPING_IMPORT" -> personnel.run(a,type,id,expected,in);
            case "STAT_CUT_CREATE","STAT_CUT_ANNUL" -> cuts.run(a,type,id,expected,in);
            case "STAT_VIEW_SAVE","STAT_VIEW_DELETE","STAT_VIEW_SHARE" -> views.run(a,type,id,expected,in);
            case "STAT_SERIES_IMPORT","STAT_SERIES_ANNUL" -> series.run(a,type,id,expected,in);
            default -> throw new Problem(400,"Comando desconocido.");
        };
        long end=Lifecycle.now();
        db.update("INSERT INTO command_log(command_key,actor_id,payload_hash,result_kind,result_id,result_message,created_ms) VALUES(?,?,?,?,?,?,?)",
            cmd.key().toString(),a.id(),hash,result.kind(),result.id(),result.message(),end);
        lastEnd=end;
        return result;
    }

    /**
     * Todo lo que escribe un comando lleva un instante posterior al fin del comando anterior (0.8.0, E08): con el lock
     * global tomado, espera a que el reloj lo supere (normalmente menos de 1 ms). Asi el corte "al constituirse el REQ"
     * (TerritorialDims.constitutedSql) incluye todo lo escrito por el comando que creo el REQ y nada del siguiente, sin
     * empates de milisegundo. Con el reloj atrasado la espera termina en MAX_WAIT_MS: no detiene el servicio y deja una
     * linea en el log tecnico, sin datos (codigo CLOCK_BEHIND y milisegundos esperados): una al empezar cada episodio y a
     * lo mas una por minuto. Mientras falte mas de 1 ms la espera duerme (sin consumir CPU); el ultimo tramo es activo.
     */
    static void afterPreviousCommand(){
        long start=System.nanoTime(),until=start+MAX_WAIT_MS*1_000_000;
        while(true){
            long gap=lastEnd-Lifecycle.now(); // falta gap+1 ms para superar el fin del comando anterior
            if(gap<0){behind=false;return;}
            long left=until-System.nanoTime();
            if(left<=0)break;
            if(gap>=1)LockSupport.parkNanos(Math.min(gap*1_000_000,left));
            else Thread.onSpinWait();
        }
        long now=System.nanoTime();
        if(!behind&&(lastWarning==0||now-lastWarning>=WARNING_EVERY_NS)){
            LOG.warn("{} {}",CLOCK_BEHIND,(now-start)/1_000_000);
            lastWarning=now;
        }
        behind=true;
    }
    /** Episodio de reloj atrasado en curso y ultima linea de log (System.nanoTime). */
    private static volatile boolean behind;
    static volatile long lastWarning;
    private static final long WARNING_EVERY_NS=60_000_000_000L;

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
