package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Outbox durable del correo institucional (V9). Con siger.mail.mode=GRAPH cada aviso interno crea su correo en el
 * MISMO COMMIT que el aviso (Access.notice); sin ese modo no se encola nada, de modo que activar el correo no envia
 * avisos retrospectivos. Estados: PENDIENTE -> EN_INTENTO (intento registrado antes de la red) -> ACEPTADO |
 * REINTENTABLE (con seguridad no salio) | RECHAZADO | AMBIGUO (pudo salir). Un AMBIGUO, incluido un intento
 * interrumpido por caida o reinicio, nunca se reenvia automaticamente: TI decide reenviar o descartar (auditado).
 * El correo no lleva datos de expedientes: solo el texto generico del aviso, un enlace opcional a SIGER y la
 * referencia de correlacion. El destinatario se resuelve en cada intento desde su cuenta vigente.
 */
@Service
public class MailOutbox {
    static final long LEASE_MS=120_000L;
    static final String SUBJECT="SIGER · Tienes un aviso nuevo";
    private final Db db;private final TransactionTemplate tx;
    private final boolean enabled;private final Set<String> domains;private final String link;private final int maxAttempts;

    record Claim(long id,String claim,String address,String correlation,String text){@Override public String toString(){return "Claim["+id+"]";}}

    public MailOutbox(Db db,Environment env,PlatformTransactionManager transactions){
        this.db=db;this.tx=new TransactionTemplate(transactions);
        String mode=env.getProperty("siger.mail.mode","OFF");
        if(!Set.of("OFF","GRAPH").contains(mode))throw new IllegalStateException("siger.mail.mode debe ser OFF o GRAPH.");
        this.enabled=mode.equals("GRAPH");
        var list=new TreeSet<String>();
        for(var d:env.getProperty("siger.mail.allowed-domains","").split(","))if(!d.isBlank())list.add(d.strip().toLowerCase(Locale.ROOT));
        this.domains=Set.copyOf(list);
        this.link=env.getProperty("siger.mail.link","").strip();
        this.maxAttempts=env.getProperty("siger.mail.max-attempts",Integer.class,8);
        if(enabled){
            // Destinatarios institucionales: dominio permitido explicito, nunca cualquier direccion escrita en una cuenta.
            if(domains.isEmpty()||!domains.stream().allMatch(d->d.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")))throw new IllegalStateException("El correo Graph exige siger.mail.allowed-domains (dominios institucionales separados por coma).");
            if(!link.isEmpty()&&!link.matches("https://[A-Za-z0-9.-]+(:[0-9]{1,5})?/?"))throw new IllegalStateException("siger.mail.link debe ser una URL https sin ruta.");
            if(maxAttempts<1||maxAttempts>20)throw new IllegalStateException("siger.mail.max-attempts fuera de rango.");
        }
    }
    boolean enabled(){return enabled;}

    /** En la transaccion del aviso. Sin modo GRAPH no hace nada. */
    void enqueue(long notice,long recipient){
        if(!enabled)return;
        long now=System.currentTimeMillis();
        db.update("INSERT INTO mail_outbox(notice_id,recipient_id,correlation_id,state,attempts,next_ms,created_ms,updated_ms) VALUES(?,?,?,'PENDIENTE',0,?,?,?)",
            notice,recipient,UUID.randomUUID().toString(),now,now,now);
    }

    /** Un intento cuya reserva vencio (caida, reinicio, red colgada) queda AMBIGUO: pudo haber salido. */
    int recover(){
        return Objects.requireNonNull(tx.execute(st->{
            long now=System.currentTimeMillis();int count=0;
            for(var r:db.rows("SELECT id,claim_id FROM mail_outbox WHERE state='EN_INTENTO' AND lease_until_ms<?",now)){
                if(db.update("UPDATE mail_outbox SET state='AMBIGUO',lease_until_ms=NULL,last_phase='RECUPERACION',updated_ms=? WHERE id=? AND state='EN_INTENTO' AND claim_id=?",now,n(r,"id"),s(r,"claim_id"))==1){
                    db.update("UPDATE mail_attempt SET state='AMBIGUO',phase='RECUPERACION',finished_ms=? WHERE claim_id=? AND finished_ms IS NULL",now,s(r,"claim_id"));count++;
                }
            }
            return count;
        }));
    }

    List<Long> due(int limit){
        return db.rows("SELECT id FROM mail_outbox WHERE state IN ('PENDIENTE','REINTENTABLE') AND next_ms<=? ORDER BY next_ms,id LIMIT "+Math.max(1,Math.min(limit,50)),System.currentTimeMillis())
            .stream().map(r->n(r,"id")).toList();
    }

    /**
     * Valida el destinatario vigente y registra el intento antes de la red (EN_INTENTO con reserva). null si no
     * corresponde enviar: cuenta inactiva o tecnica, aviso ya leido en SIGER, sin correo o dominio no permitido
     * (SUPRIMIDO, sin intento), o si otro proceso ya lo reservo.
     */
    Claim reserve(long id){
        return tx.execute(st->{
            long now=System.currentTimeMillis();
            var rows=db.rows("SELECT o.correlation_id,n.text_value,n.read_ms,u.email,u.enabled,u.technical FROM mail_outbox o JOIN inbox_notice n ON n.id=o.notice_id JOIN staff u ON u.id=o.recipient_id WHERE o.id=? AND o.state IN ('PENDIENTE','REINTENTABLE') AND o.next_ms<=?",id,now);
            if(rows.isEmpty())return null;
            var r=rows.getFirst();
            String address=s(r,"email").strip().toLowerCase(Locale.ROOT),reason=null;
            if(!b(r,"enabled")||b(r,"technical"))reason="CUENTA_INACTIVA";
            else if(r.get("read_ms")!=null)reason="AVISO_LEIDO";
            else if(!address.matches(GraphMail.ADDRESS))reason="SIN_CORREO";
            else if(!domains.contains(address.substring(address.indexOf('@')+1)))reason="DOMINIO_NO_PERMITIDO";
            if(reason!=null){
                db.update("UPDATE mail_outbox SET state='SUPRIMIDO',last_phase=?,updated_ms=? WHERE id=? AND state IN ('PENDIENTE','REINTENTABLE')",reason,now,id);
                return null;
            }
            String claim=UUID.randomUUID().toString();
            if(db.update("UPDATE mail_outbox SET state='EN_INTENTO',claim_id=?,lease_until_ms=?,updated_ms=? WHERE id=? AND state IN ('PENDIENTE','REINTENTABLE')",claim,now+LEASE_MS,now,id)!=1)return null;
            db.update("INSERT INTO mail_attempt(outbox_id,claim_id,address,started_ms,state,phase) VALUES(?,?,?,?,'EN_INTENTO','PREVIO_RED')",id,claim,address,now);
            return new Claim(id,claim,address,s(r,"correlation_id"),body(s(r,"text_value"),s(r,"correlation_id")));
        });
    }
    private String body(String notice,String correlation){
        return "Tienes un aviso nuevo en SIGER:\n\n"+notice+"\n\n"
            +(link.isEmpty()?"Ingresa a SIGER con tu cuenta institucional para revisarlo.":"Ingresa a SIGER con tu cuenta institucional para revisarlo: "+link)+"\n\n"
            +"Este correo es automático. No contiene datos de expedientes ni reemplaza la revisión en el sistema.\nReferencia: "+correlation+"\n";
    }
    GraphMail.Message message(Claim c){return new GraphMail.Message(c.address(),SUBJECT,c.text(),c.correlation());}

    /**
     * Resultado despues de la red. Tambien completa un intento ya recuperado como AMBIGUO (respuesta tardia con la
     * misma reserva). Un REINTENTABLE espera Retry-After o espera exponencial; al agotar intentos queda RECHAZADO.
     */
    void finish(Claim c,GraphMail.Result result){
        tx.executeWithoutResult(st->{
            long now=System.currentTimeMillis();
            var rows=db.rows("SELECT attempts FROM mail_outbox WHERE id=? AND claim_id=? AND state IN ('EN_INTENTO','AMBIGUO')",c.id(),c.claim());
            if(!rows.isEmpty()){
                String state=result.state(),phase=result.phase();
                int attempts=(int)n(rows.getFirst(),"attempts")+(phase.startsWith("GRAPH")?1:0);
                if(state.equals("REINTENTABLE")&&attempts>=maxAttempts){state="RECHAZADO";phase="REINTENTOS_AGOTADOS";}
                long next=state.equals("REINTENTABLE")?now+Math.max(result.retryAfterSeconds(),Math.min(3600L,60L<<Math.min(attempts,6)))*1000L:now;
                db.update("UPDATE mail_outbox SET state=?,attempts=?,next_ms=?,claim_id=?,lease_until_ms=NULL,last_phase=?,last_http=?,last_request_id=?,accepted_ms=CASE WHEN ?='ACEPTADO' THEN ? ELSE accepted_ms END,updated_ms=? WHERE id=?",
                    state,attempts,next,state.equals("AMBIGUO")?c.claim():null,phase,result.http(),result.requestId(),state,now,now,c.id());
            }
            db.update("UPDATE mail_attempt SET state=?,phase=?,http_status=?,request_id=?,finished_ms=? WHERE claim_id=?",result.state(),result.phase(),result.http(),result.requestId(),now,c.claim());
        });
    }

    /** MAIL_RESOLVE: TI decide sobre un correo AMBIGUO (REENVIAR o DESCARTAR); dentro de la transaccion de Workflow. */
    Workflow.Result resolve(Actor a,long id,String decision,String reason){
        if(!a.role("TI"))Problem.denied();
        var row=db.one("SELECT id,state FROM mail_outbox WHERE id=?",id);
        require(s(row,"state").equals("AMBIGUO"),"Solo se resuelve un correo en estado ambiguo.");
        long now=System.currentTimeMillis();boolean resend=decision.equals("REENVIAR");
        db.update("UPDATE mail_outbox SET state=?,next_ms=?,claim_id=NULL,resolution=?,resolved_by=?,resolved_ms=?,updated_ms=? WHERE id=? AND state='AMBIGUO'",
            resend?"PENDIENTE":"DESCARTADO",now,decision,a.id(),now,now,id);
        db.audit(a.id(),resend?"CORREO_AMBIGUO_REENVIO":"CORREO_AMBIGUO_DESCARTADO","SISTEMA",id,null,"Correo #"+id+". "+reason);
        return Workflow.done("CORREO",id);
    }
}
