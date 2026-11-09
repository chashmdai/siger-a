package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;

/**
 * Estado durable del consumidor del Formulario (formulario_reception), sin red: cada metodo es una transaccion corta.
 * register() crea en un solo COMMIT la COM (IntakeCommands.conserveExternal), sus originales en cuarentena, el
 * comprobante institucional firmado y la reserva con el ACK pendiente; un reintento o una reentrega devuelve el mismo
 * comprobante (mismo UUID institucional y folio) y solo actualiza la reserva. No crea SOL, REQ ni Caso.
 * Se registra con la identidad tecnica interna, que no inicia sesion ni recibe funciones.
 */
@Service
class FormularioReception {
    static final String TECHNICAL="sistema:formulario-publico";
    static final String TECHNICAL_NAME="Recepción técnica Formulario Público";
    private final Db db;private final Access access;private final IntakeCommands intake;private final Documents documents;private final TransactionTemplate tx;

    FormularioReception(Db db,Access access,IntakeCommands intake,Documents documents,PlatformTransactionManager manager){
        this.db=db;this.access=access;this.intake=intake;this.documents=documents;this.tx=new TransactionTemplate(manager);
    }

    record Registered(long communication,String receipt,String folio,boolean created) { }
    record Pending(String operation,String lease,String receipt,long communication) { }
    record Stored(String key,long size,String sha256) { }

    /**
     * Registra la entrega verificada o, si ya estaba registrada con las mismas huellas, devuelve su comprobante
     * original y deja vigente la nueva reserva. Los originales deben estar custodiados antes de llamar.
     */
    Registered register(ExternalOriginal o,long scope,UUID lease,long until,int attempt,FormularioAck ack){
        return tx.execute(status->{
            db.lock();
            var previous=existing(o,lease,until,attempt);
            if(previous!=null)return previous;
            Problem.require(db.count("SELECT COUNT(*) FROM communication_original WHERE source=? AND external_id=?",o.source(),o.externalId())==0,
                "La entrega ya fue conservada sin el consumidor del Formulario; requiere revision antes de confirmar.");
            for(var a:o.attachments())if(!documents.externalPresent(Documents.externalKey(o.externalId(),a.id()),a.size()))
                throw new Problem(503,"Original ciudadano no custodiado; no se registra la entrega.");
            Actor t=technical(scope);
            long com=intake.conserveExternal(t,scope,o).id();
            long now=System.currentTimeMillis();
            for(var a:o.attachments()){
                String key=Documents.externalKey(o.externalId(),a.id());
                long doc=db.insert("INSERT INTO document_item(communication_id,name,storage_key,sha256,size_bytes,content_type,uploaded_by,created_ms,document_type) VALUES(?,?,?,?,?,'application/octet-stream',?,?,'INGRESO')",
                    com,a.name(),key,a.sha256(),a.size(),t.id(),now);
                db.update("INSERT INTO external_document(document_id,communication_id,operation_id,attachment_id,media_type) VALUES(?,?,?,?,?)",doc,com,o.externalId(),a.id(),a.type());
            }
            if(!o.attachments().isEmpty())db.audit(t.id(),"COM_ORIGINALES_CUSTODIADOS","COM",com,scope,o.attachments().size()+" originales del Formulario en cuarentena");
            var c=db.one("SELECT folio,created_ms FROM communication WHERE id=?",com);
            var registro=new FormularioAck.Registro(FormularioAck.version(o.channel()),UUID.fromString(o.externalId()),UUID.randomUUID(),com,s(c,"folio"),
                Instant.ofEpochMilli(n(c,"created_ms")),o.channel(),o.contentHash(),o.provenanceHash());
            String receipt=ack.sign(registro);
            int changed=db.update("UPDATE formulario_reception SET state='REGISTRADA',document_version=?,channel=?,content_hash=?,provenance_hash=?,lease_id=?,lease_until_ms=?,attempt=?,communication_id=?,institutional_id=?,folio_number=?,folio=?,registered_ms=?,receipt=?,receipt_key_id=?,ack_pending=TRUE,ack_attempts=0,ack_next_ms=0,ack_blocked=FALSE,last_error=NULL,updated_ms=? WHERE operation_id=? AND state='RECHAZADA'",
                o.version(),o.channel(),o.contentHash(),o.provenanceHash(),lease.toString(),until,attempt,com,registro.communication().toString(),com,registro.folio(),registro.registered().toEpochMilli(),receipt,ack.keyId(),now,o.externalId());
            if(changed==0)db.update("INSERT INTO formulario_reception(operation_id,state,document_version,channel,content_hash,provenance_hash,lease_id,lease_until_ms,attempt,communication_id,institutional_id,folio_number,folio,registered_ms,receipt,receipt_key_id,ack_pending,created_ms,updated_ms) VALUES(?,'REGISTRADA',?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,?,?)",
                o.externalId(),o.version(),o.channel(),o.contentHash(),o.provenanceHash(),lease.toString(),until,attempt,com,registro.communication().toString(),com,registro.folio(),registro.registered().toEpochMilli(),receipt,ack.keyId(),now,now);
            return new Registered(com,receipt,registro.folio(),true);
        });
    }
    /** Reentrega de una operacion ya registrada: mismas huellas o conflicto; conserva el comprobante y renueva la reserva. */
    private Registered existing(ExternalOriginal o,UUID lease,long until,int attempt){
        var rows=db.rows("SELECT * FROM formulario_reception WHERE operation_id=? AND state IN ('REGISTRADA','CONFIRMADA')",o.externalId());
        if(rows.isEmpty())return null;
        var r=rows.getFirst();
        Problem.require(s(r,"content_hash").equals(o.contentHash())&&s(r,"provenance_hash").equals(o.provenanceHash())&&s(r,"channel").equals(o.channel()),
            "La operacion ya registrada se reentrego con otro contenido; no se confirma.");
        if(b(r,"ack_pending"))db.update("UPDATE formulario_reception SET lease_id=?,lease_until_ms=?,attempt=?,ack_attempts=0,ack_next_ms=0,ack_blocked=FALSE,updated_ms=? WHERE id=?",
            lease.toString(),until,attempt,System.currentTimeMillis(),n(r,"id"));
        return new Registered(n(r,"communication_id"),s(r,"receipt"),s(r,"folio"),false);
    }

    /** Entrega rechazada antes de registrar (sin COM): solo identificador y codigo, nunca contenido. */
    void rejected(String operation,String version,String code){
        tx.executeWithoutResult(status->{
            long now=System.currentTimeMillis();
            // Una operacion ya registrada conserva su estado y su comprobante; solo se anota un rechazo previo o nuevo.
            if(db.count("SELECT COUNT(*) FROM formulario_reception WHERE operation_id=?",operation)>0)
                db.update("UPDATE formulario_reception SET last_error=?,updated_ms=? WHERE operation_id=? AND state='RECHAZADA'",code,now,operation);
            else db.update("INSERT INTO formulario_reception(operation_id,state,document_version,last_error,created_ms,updated_ms) VALUES(?,'RECHAZADA',?,?,?,?)",operation,version,code,now,now);
        });
    }

    /** ACK pendientes con reserva conocida y fuera de espera, en orden de registro. */
    List<Pending> pendingAcks(int limit){
        return db.rows("SELECT operation_id,lease_id,receipt,communication_id FROM formulario_reception WHERE state='REGISTRADA' AND ack_pending=TRUE AND ack_blocked=FALSE AND lease_id IS NOT NULL AND ack_next_ms<=? ORDER BY registered_ms,id LIMIT "+Math.max(1,Math.min(limit,20)),System.currentTimeMillis())
            .stream().map(r->new Pending(s(r,"operation_id"),s(r,"lease_id"),s(r,"receipt"),n(r,"communication_id"))).toList();
    }
    /** Originales ligados a la COM de una operacion, para verificarlos completos antes de cada ACK. */
    List<Stored> custody(long communication){
        return db.rows("SELECT d.storage_key,d.size_bytes,d.sha256 FROM external_document x JOIN document_item d ON d.id=x.document_id WHERE x.communication_id=? ORDER BY d.id",communication)
            .stream().map(r->new Stored(s(r,"storage_key"),n(r,"size_bytes"),s(r,"sha256"))).toList();
    }
    /** Cantidad de descriptores de adjunto del original conservado (texto firmado e inmutable). */
    int signedOriginals(long communication){
        var rows=db.rows("SELECT document FROM communication_original WHERE communication_id=?",communication);
        if(rows.isEmpty())return -1;
        var adjuntos=tools.jackson.databind.json.JsonMapper.builder().build().readTree(s(rows.getFirst(),"document")).path("comunicacion").path("adjuntos");
        return adjuntos.isArray()?adjuntos.size():-1;
    }

    /** El Formulario acepto el ACK (204): la entrega sale de pendientes. Idempotente. */
    void acknowledged(String operation,String lease){
        tx.executeWithoutResult(status->{
            db.lock();long now=System.currentTimeMillis();
            var rows=db.rows("SELECT * FROM formulario_reception WHERE operation_id=? AND state='REGISTRADA' AND ack_pending=TRUE AND lease_id=?",operation,lease);
            if(rows.isEmpty())return;
            var r=rows.getFirst();
            db.update("UPDATE formulario_reception SET state='CONFIRMADA',ack_pending=FALSE,acked_ms=?,last_error=NULL,updated_ms=? WHERE id=?",now,now,n(r,"id"));
            long com=n(r,"communication_id");
            var c=db.one("SELECT origin_scope FROM communication WHERE id=?",com);
            var t=db.one("SELECT id FROM staff WHERE username=? AND technical=TRUE",TECHNICAL);
            db.audit(n(t,"id"),"COM_REGISTRO_CONFIRMADO_FORMULARIO","COM",com,n(c,"origin_scope"),"Folio confirmado al Formulario");
        });
    }
    /** 409: la reserva ya no esta vigente o fue reemplazada. Se espera la reentrega; el comprobante no cambia. */
    void leaseLost(String operation,String lease){
        tx.executeWithoutResult(status->db.update("UPDATE formulario_reception SET lease_id=NULL,last_error='ACK_RESERVA_NO_VIGENTE',updated_ms=? WHERE operation_id=? AND lease_id=? AND ack_pending=TRUE",
            System.currentTimeMillis(),operation,lease));
    }
    /**
     * ACK no confirmado (sin respuesta, 5xx, 429 u otro): espera exponencial. Un rechazo persistente del contenido
     * (400/404/422) se bloquea al tercer intento para revision; una reentrega del Formulario lo desbloquea.
     */
    void ackDeferred(String operation,String lease,int httpStatus){
        boolean persistent=Set.of(400,404,422).contains(httpStatus);
        tx.executeWithoutResult(status->{
            var rows=db.rows("SELECT id,ack_attempts FROM formulario_reception WHERE operation_id=? AND lease_id=? AND ack_pending=TRUE AND ack_blocked=FALSE",operation,lease);
            if(rows.isEmpty())return;
            int attempts=(int)n(rows.getFirst(),"ack_attempts")+1;long now=System.currentTimeMillis();
            String code=(persistent?"ACK_RECHAZADO_HTTP_":"ACK_DIFERIDO_")+(httpStatus>=100&&httpStatus<=599?String.valueOf(httpStatus):"SIN_RESPUESTA");
            db.update("UPDATE formulario_reception SET ack_attempts=?,ack_next_ms=?,ack_blocked=?,last_error=?,updated_ms=? WHERE id=?",
                attempts,now+Math.min(300000L,1000L<<Math.min(attempts,8)),persistent&&attempts>=3,code,now,n(rows.getFirst(),"id"));
        });
    }
    /** Originales ausentes o alterados antes del ACK: no se confirma y queda bloqueado para revision. */
    void custodyFailed(String operation){
        tx.executeWithoutResult(status->db.update("UPDATE formulario_reception SET ack_blocked=TRUE,last_error='CUSTODIA_NO_VERIFICADA',updated_ms=? WHERE operation_id=? AND ack_pending=TRUE",
            System.currentTimeMillis(),operation));
    }

    /** Exclusion entre ciclos de SIGER A (lease en SQL). No coordina con consumidores de otros sistemas. */
    boolean acquire(String holder,long until){
        return Boolean.TRUE.equals(tx.execute(status->db.update("UPDATE formulario_consumer_lock SET holder=?,until_ms=? WHERE id=1 AND (holder IS NULL OR until_ms<?)",holder,until,System.currentTimeMillis())==1));
    }
    void release(String holder){
        tx.executeWithoutResult(status->db.update("UPDATE formulario_consumer_lock SET holder=NULL,until_ms=0 WHERE id=1 AND holder=?",holder));
    }

    /**
     * Identidad tecnica interna: cuenta deshabilitada, sin clave utilizable, sin funciones y marcada technical; se crea
     * una vez con el ambito receptor. Nunca adopta una cuenta de funcionario con el mismo nombre.
     */
    Actor technical(long scope){
        access.scope(scope);
        var rows=db.rows("SELECT * FROM staff WHERE username=?",TECHNICAL);
        long id;
        if(rows.isEmpty())id=db.insert("INSERT INTO staff(username,name,password_hash,enabled,must_change,home_scope,technical) VALUES(?,?,'!',FALSE,FALSE,?,TRUE)",TECHNICAL,TECHNICAL_NAME,scope);
        else{
            var u=rows.getFirst();id=n(u,"id");
            if(!b(u,"technical")||b(u,"enabled")||db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",id)>0)
                throw new IllegalStateException("La identidad tecnica del Formulario no es valida; no se registra la entrega.");
        }
        return new Actor(id,TECHNICAL,TECHNICAL_NAME,scope,false,0,List.of(),Set.of());
    }
}
