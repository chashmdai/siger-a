package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Transiciones compartidas por los comandos: creacion de REQ y Caso, asignacion, cierre de Caso
 * y constancias. Se ejecutan siempre dentro de la transaccion y el lock abiertos por Workflow.
 */
@Component
public class Lifecycle {
    static final String[] CHANNELS={"PRESENCIAL","TELEFONO","CORREO","OFICIO","OTRO"};
    static final String[] MATTERS={"UPR","UPD","UEP","OTRA"};
    static final String[] IMMEDIATE_RESULTS={"ORIENTACION","RESPUESTA_DIRECTA","REMISION_CONCLUIDA"};
    static final String OPEN_TASK="('PENDIENTE','EN_CURSO')";

    private final Db db;
    private final Access access;
    private final String comPrefix;

    public Lifecycle(Db db,Access access,@Value("${siger.folio-prefix:COM-A}")String prefix){
        if(!prefix.matches("[A-Z][A-Z0-9-]{1,14}"))throw new IllegalArgumentException("Prefijo COM invalido");
        this.db=db;
        this.access=access;
        this.comPrefix=prefix;
    }

    static long now(){return System.currentTimeMillis();}
    static Workflow.Result done(String kind,long id){return new Workflow.Result(kind,id,"Operacion registrada.");}

    // --- Folios ----------------------------------------------------------------------------
    void comFolio(long com){db.update("UPDATE communication SET folio=? WHERE id=?",String.format(Locale.ROOT,"%s-%06d",comPrefix,com),com);}
    private void reqFolio(long req){db.update("UPDATE requirement SET folio=? WHERE id=?",String.format(Locale.ROOT,"REQ-A-%06d",req),req);}

    // --- Guardas de estado -----------------------------------------------------------------
    void openReq(Map<String,Object> r){require(s(r,"state").equals("ABIERTO"),"El REQ esta cerrado; requiere nueva gestion autorizada.");}
    void openCase(Map<String,Object> c){require(!s(c,"state").equals("CERRADO"),"El Caso esta cerrado.");}
    long pendingTasks(long cas){return db.count("SELECT COUNT(*) FROM task_item WHERE case_id=? AND state IN "+OPEN_TASK,cas);}
    long pendingDerivations(long cas){return db.count("SELECT COUNT(*) FROM flow_request WHERE origin_case_id=? AND state='PENDIENTE'",cas);}
    /** Decisiones de continuidad abiertas en el REQ: entregas, flujos y reaperturas. */
    long pendingReq(long req){
        return access.pendingHandoffs(req)
            +db.count("SELECT COUNT(*) FROM flow_request WHERE requirement_id=? AND state='PENDIENTE'",req)
            +db.count("SELECT COUNT(*) FROM reopening WHERE requirement_id=? AND state='PENDIENTE'",req);
    }

    // --- Historial y revisiones ------------------------------------------------------------
    void changed(Actor a,String action,String kind,long id,long scope,String note){db.audit(a.id(),action,kind,id,scope,note);}
    void bumpCase(long id){db.update("UPDATE case_record SET revision=revision+1 WHERE id=?",id);}
    void bumpReq(long id){db.update("UPDATE requirement SET revision=revision+1 WHERE id=?",id);}

    // --- REQ -------------------------------------------------------------------------------
    /** Inserta el REQ y su folio, sin historial; cada recorrido registra su propio evento. */
    long insertReq(Actor a,long com,Long sol,long principal,long attentionUser,long attentionScope){
        long id=db.insert("INSERT INTO requirement(communication_id,solicitation_id,principal_scope,attention_user,attention_scope,created_ms,created_by) VALUES(?,?,?,?,?,?,?)",
            com,sol,principal,attentionUser,attentionScope,now(),a.id());
        reqFolio(id);
        return id;
    }
    long newReq(Actor a,long com,Long sol,long scope,long attention,long attentionScope){
        long id=insertReq(a,com,sol,scope,attention,attentionScope);
        changed(a,"REQ_CREADO","REQ",id,scope,"COM "+com);
        return id;
    }

    // --- Casos -----------------------------------------------------------------------------
    long newCase(Actor a,long req,long scope,long responsible,String objective,String matter,Long origin){
        access.head(a,scope);
        access.assignable(responsible,scope);
        require(access.pendingHandoffs(req)==0,"Resuelve la entrega de primera atencion antes de abrir gestion formal.");
        long id=db.insert("INSERT INTO case_record(requirement_id,scope_id,responsible_id,objective,matter,created_ms,created_by,origin_case_id) VALUES(?,?,?,?,?,?,?,?)",
            req,scope,responsible,objective,matter,now(),a.id(),origin);
        db.update("UPDATE case_record SET folio=? WHERE id=?",String.format(Locale.ROOT,"CAS-A-%06d",id),id);
        assignment(a,id,responsible);
        changed(a,"CASO_CREADO","CAS",id,scope,objective);
        bumpReq(req);
        return id;
    }
    void assignment(Actor a,long cas,long user){
        db.update("UPDATE case_assignment SET current_flag=FALSE WHERE case_id=?",cas);
        db.update("INSERT INTO case_assignment(case_id,user_id,assigned_by,assigned_ms,current_flag) VALUES(?,?,?,?,TRUE)",cas,user,a.id(),now());
        access.notice(user,"Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.","CAS",cas);
    }
    void finishCase(Actor a,Map<String,Object> c,String mode,String reason){
        long id=n(c,"id");
        db.update("UPDATE case_record SET state='CERRADO',closed_ms=?,closed_by=?,closure_mode=?,close_reason=?,revision=revision+1 WHERE id=?",now(),a.id(),mode,reason,id);
        db.update("INSERT INTO closure_record(requirement_id,case_id,actor_id,closed_ms,mode,reason) SELECT requirement_id,id,closed_by,closed_ms,closure_mode,close_reason FROM case_record WHERE id=?",id);
        changed(a,"CASO_CERRADO_"+mode,"CAS",id,n(c,"scope_id"),reason);
        access.notice(n(c,"responsible_id"),"El Caso fue cerrado. El cierre del REQ es una decision separada.","CAS",id);
        var req=db.one("SELECT principal_scope FROM requirement WHERE id=?",n(c,"requirement_id"));
        access.notifyHeads(n(req,"principal_scope"),"Cambio el estado de un Caso del REQ.","REQ",n(c,"requirement_id"));
        bumpReq(n(c,"requirement_id"));
    }

    // --- Constancias -----------------------------------------------------------------------
    /** Registra una actuacion de REQ o Caso; una rectificacion crea una nueva constancia. */
    long note(Actor a,long req,Long cas,long scope,Input in,Long rectifies){
        long effective=in.instant("effective_at",true);
        require(effective<=now(),"La gestion no puede estar fechada en el futuro.");
        String body=in.text("body",20000,true);
        boolean reserved=in.flag("reserved");
        if(reserved&&!a.head(scope)&&cas!=null&&n(db.one("SELECT responsible_id FROM case_record WHERE id=?",cas),"responsible_id")!=a.id())Problem.denied();
        long id=db.insert("INSERT INTO management_note(requirement_id,case_id,author_id,effective_ms,registered_ms,body,reserved,rectifies_id,correction_reason) VALUES(?,?,?,?,?,?,?,?,?)",
            req,cas,a.id(),effective,now(),body,reserved,rectifies,rectifies==null?null:in.reason());
        String action=rectifies==null?"ACTUACION_REGISTRADA":"ACTUACION_RECTIFICADA";
        String detail=rectifies==null?"Constancia "+id:"Nueva constancia "+id+" corrige "+rectifies+". "+(reserved?"Motivo protegido en la constancia.":in.reason());
        changed(a,action,cas==null?"REQ":"CAS",cas==null?req:cas,scope,detail);
        return id;
    }
}
