package cl.nexbyte.sigera;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Control y estado de las integraciones permanentes (V9): consumidor automatico del Formulario y correo Graph.
 * La pausa es una decision auditada de TI que el trabajador respeta en su siguiente ciclo (no cancela una
 * transferencia en curso). El estado es un latido tecnico sin datos ciudadanos ni credenciales.
 */
@Service
public class Integrations {
    private static final Logger LOG=LoggerFactory.getLogger(Integrations.class);
    static final String FORMULARIO="FORMULARIO_PULL",CORREO="CORREO_GRAPH";
    private static final Map<Long,String> IDS=Map.of(1L,FORMULARIO,2L,CORREO);
    private final Db db;private final Environment env;

    public Integrations(Db db,Environment env){this.db=db;this.env=env;}

    /** Modo configurado al arrancar (no se cambia sin reiniciar): lo decide el operador, no la base. */
    String mode(String name){
        if(name.equals(FORMULARIO)){
            if(!env.getProperty("siger.formulario.pull.enabled",Boolean.class,false))return "DESACTIVADO";
            if(env.getProperty("siger.formulario.pull.automatic",Boolean.class,false))return "AUTOMATICO";
            return env.getProperty("siger.formulario.pull.run-once",Boolean.class,false)?"MANUAL":"DESACTIVADO";
        }
        return "GRAPH".equals(env.getProperty("siger.mail.mode","OFF"))?"GRAPH":"DESACTIVADO";
    }

    boolean paused(String name){
        return b(db.one("SELECT paused FROM integration_control WHERE name=?",name),"paused");
    }

    /** Latido de un ciclo. Nunca propaga un fallo de la base: el trabajador sigue su espera. */
    void record(String name,String state,String holder,boolean success,int failures,String error,long nextMs){
        try{
            long now=System.currentTimeMillis();
            db.update("UPDATE integration_status SET state=?,holder=?,cycles=cycles+1,failures=?,last_cycle_ms=?,last_success_ms=CASE WHEN ? THEN ? ELSE last_success_ms END,next_ms=?,last_error=?,updated_ms=? WHERE name=?",
                state,holder,failures,now,success,now,nextMs,error==null?null:error.substring(0,Math.min(64,error.length())),now,name);
        }catch(RuntimeException e){LOG.warn("No se registro el estado de {} ({}).",name,e.getClass().getSimpleName());}
    }
    /**
     * Estado sin ciclo (inicio o detencion del trabajador). nextMs es el plazo del latido siguiente: al iniciar, el
     * primer ciclo debe registrarse antes; al detener se borra (null).
     */
    void state(String name,String state,String holder,Long nextMs){
        try{db.update("UPDATE integration_status SET state=?,holder=?,next_ms=?,updated_ms=? WHERE name=?",state,holder,nextMs,System.currentTimeMillis(),name);}
        catch(RuntimeException e){LOG.warn("No se registro el estado de {} ({}).",name,e.getClass().getSimpleName());}
    }
    /** Inicio de un ciclo: el trabajador promete registrar su resultado antes de expectedEnd (ciclo colgado si no). */
    void started(String name,long expectedEnd){
        try{db.update("UPDATE integration_status SET next_ms=?,updated_ms=? WHERE name=?",expectedEnd,System.currentTimeMillis(),name);}
        catch(RuntimeException e){LOG.warn("No se registro el inicio de ciclo de {} ({}).",name,e.getClass().getSimpleName());}
    }

    /** Margen sobre el plazo prometido antes de declarar el latido vencido. */
    static final long STALE_GRACE_MS=120_000L;
    /** Integracion permanente configurada en este proceso (consumidor automatico o correo Graph). */
    boolean permanent(String name){
        String mode=mode(name);return mode.equals("AUTOMATICO")||mode.equals("GRAPH");
    }
    /**
     * Latido vencido: integracion permanente, no pausada, cuyo trabajador no registro el ciclo prometido (hilo caido,
     * ciclo colgado o trabajador detenido mientras el servicio sigue activo). La pausa no vence el latido.
     */
    boolean stale(String name,long now){
        if(!permanent(name)||paused(name))return false;
        var r=db.one("SELECT state,next_ms,updated_ms FROM integration_status WHERE name=?",name);
        if(s(r,"state").equals("DETENIDO")||s(r,"state").equals("SIN_ACTIVIDAD"))return true;
        long due=r.get("next_ms")==null?n(r,"updated_ms"):n(r,"next_ms");
        return now>due+STALE_GRACE_MS;
    }

    /** Codigo publicable en /api/health: modo, pausa, latido vencido o el ultimo estado. Sin contadores ni errores. */
    String health(String name){
        try{
            String mode=mode(name);
            if(mode.equals("DESACTIVADO")||mode.equals("MANUAL"))return mode;
            if(paused(name))return "PAUSADO";
            if(stale(name,System.currentTimeMillis()))return "SIN_LATIDO";
            return s(db.one("SELECT state FROM integration_status WHERE name=?",name),"state");
        }catch(RuntimeException e){return "NO_DISPONIBLE";}
    }

    /** Vista de TI: configuracion, control, latido y contadores agregados. Sin documentos ni contenidos. */
    Map<String,Object> overview(){
        var out=new LinkedHashMap<String,Object>();
        for(var name:List.of(FORMULARIO,CORREO)){
            var item=new LinkedHashMap<String,Object>();
            var control=db.one("SELECT id,paused,revision,updated_ms,updated_by,reason FROM integration_control WHERE name=?",name);
            item.put("id",n(control,"id"));item.put("mode",mode(name));item.put("paused",b(control,"paused"));item.put("revision",n(control,"revision"));
            item.put("control_updated_ms",control.get("updated_ms"));item.put("control_reason",control.get("reason"));
            item.put("status",db.one("SELECT state,cycles,failures,last_cycle_ms,last_success_ms,next_ms,last_error,updated_ms FROM integration_status WHERE name=?",name));
            item.put("stale",stale(name,System.currentTimeMillis()));
            out.put(name,item);
        }
        ((Map<String,Object>)out.get(FORMULARIO)).put("counts",counts(
            "SELECT CASE WHEN state='REGISTRADA' AND ack_blocked=TRUE THEN 'ACK_BLOQUEADO' WHEN state='REGISTRADA' THEN 'ACK_PENDIENTE' ELSE state END AS k,COUNT(*) AS c FROM formulario_reception GROUP BY CASE WHEN state='REGISTRADA' AND ack_blocked=TRUE THEN 'ACK_BLOQUEADO' WHEN state='REGISTRADA' THEN 'ACK_PENDIENTE' ELSE state END"));
        var mail=(Map<String,Object>)out.get(CORREO);
        mail.put("counts",counts("SELECT state AS k,COUNT(*) AS c FROM mail_outbox GROUP BY state"));
        mail.put("ambiguous",db.rows("SELECT o.id,o.correlation_id,o.attempts,o.created_ms,o.updated_ms,o.last_phase,o.last_http,u.username FROM mail_outbox o JOIN staff u ON u.id=o.recipient_id WHERE o.state='AMBIGUO' ORDER BY o.id LIMIT 100"));
        return out;
    }
    private Map<String,Long> counts(String sql){
        var out=new TreeMap<String,Long>();for(var r:db.rows(sql))out.put(s(r,"k"),n(r,"c"));return out;
    }

    /** INTEGRATION_PAUSE / INTEGRATION_RESUME: solo TI, con revision y motivo; dentro de la transaccion de Workflow. */
    Workflow.Result control(Actor a,long id,Long revision,boolean pause,String reason){
        if(!a.role("TI"))Problem.denied();
        String name=IDS.get(id);if(name==null)throw new Problem(404,"Integracion inexistente.");
        var row=db.one("SELECT * FROM integration_control WHERE id=?",id);Db.revision(row,revision);
        require(b(row,"paused")!=pause,pause?"La integracion ya esta pausada.":"La integracion no esta pausada.");
        db.update("UPDATE integration_control SET paused=?,revision=revision+1,updated_ms=?,updated_by=?,reason=? WHERE id=?",pause,System.currentTimeMillis(),a.id(),reason,id);
        db.audit(a.id(),pause?"INTEGRACION_PAUSADA":"INTEGRACION_REANUDADA","SISTEMA",id,null,name+". "+reason);
        return Workflow.done("INTEGRACION",id);
    }
}
