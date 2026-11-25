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
    /** Canales del registro manual y de la atencion directa (0.8.0, D31: REDES_SOCIALES e INTEGRANTE_DEFENSORIA). */
    static final String[] CHANNELS={"PRESENCIAL","TELEFONO","CORREO","OFICIO","REDES_SOCIALES","INTEGRANTE_DEFENSORIA","OTRO"};
    static final String[] MATTERS={"UPR","UPD","UEP","OTRA"};
    /** Materia sugerida de una SOL a una sede y materia del REQ regional (D9): las de las Direcciones nacionales. */
    static final String[] REGIONAL_MATTERS={"UPR","UPD","UEP"};
    static final String[] IMMEDIATE_RESULTS={"ORIENTACION","RESPUESTA_DIRECTA","REMISION_CONCLUIDA"};
    static final String OPEN_TASK="('PENDIENTE','EN_CURSO')";

    private final Db db;
    private final Access access;
    private final Milestones milestones;
    private final String comPrefix;

    public Lifecycle(Db db,Access access,Milestones milestones,@Value("${siger.folio-prefix:COM-A}")String prefix){
        if(!prefix.matches("[A-Z][A-Z0-9-]{1,14}"))throw new IllegalArgumentException("Prefijo COM invalido");
        this.db=db;
        this.access=access;
        this.milestones=milestones;
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
    /** Registra el evento de historial y devuelve su instante (el de los hitos que se reconstruyen desde la auditoria). */
    long changed(Actor a,String action,String kind,long id,long scope,String note){long at=now();db.audit(a.id(),action,kind,id,scope,note,at);return at;}
    void bumpCase(long id){db.update("UPDATE case_record SET revision=revision+1 WHERE id=?",id);}
    void bumpReq(long id){db.update("UPDATE requirement SET revision=revision+1 WHERE id=?",id);}

    // --- REQ -------------------------------------------------------------------------------
    /** Inserta el REQ y su folio, sin historial; cada recorrido registra su propio evento. */
    long insertReq(Actor a,long com,Long sol,long principal,long attentionUser,long attentionScope){
        long t=now();
        long id=db.insert("INSERT INTO requirement(communication_id,solicitation_id,principal_scope,attention_user,attention_scope,created_ms,created_by) VALUES(?,?,?,?,?,?,?)",
            com,sol,principal,attentionUser,attentionScope,t,a.id());
        reqFolio(id);
        milestone("REQ_CREADO","REQ",id,com,id,null,a.id(),attentionUser,principal,t,null);
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
        long t=now();
        long id=db.insert("INSERT INTO case_record(requirement_id,scope_id,responsible_id,objective,matter,created_ms,created_by,origin_case_id) VALUES(?,?,?,?,?,?,?,?)",
            req,scope,responsible,objective,matter,t,a.id(),origin);
        db.update("UPDATE case_record SET folio=? WHERE id=?",String.format(Locale.ROOT,"CAS-A-%06d",id),id);
        caseMilestone("CASO_CREADO","CAS",id,id,a.id(),responsible,t,null);
        assignment(a,id,responsible);
        changed(a,"CASO_CREADO","CAS",id,scope,objective);
        bumpReq(req);
        return id;
    }
    void assignment(Actor a,long cas,long user){
        db.update("UPDATE case_assignment SET current_flag=FALSE WHERE case_id=?",cas);
        long t=now();
        long id=db.insert("INSERT INTO case_assignment(case_id,user_id,assigned_by,assigned_ms,current_flag) VALUES(?,?,?,?,TRUE)",cas,user,a.id(),t);
        caseMilestone("CASO_ASIGNADO","ASIGNACION",id,cas,a.id(),user,t,null);
        access.notice(user,"Tienes una asignacion de Caso. El acuse es independiente de la responsabilidad.","CAS",cas);
    }
    void finishCase(Actor a,Map<String,Object> c,String mode,String reason){
        long id=n(c,"id"),t=now();
        db.update("UPDATE case_record SET state='CERRADO',closed_ms=?,closed_by=?,closure_mode=?,close_reason=?,revision=revision+1 WHERE id=?",t,a.id(),mode,reason,id);
        caseMilestone("CASO_CERRADO","CAS",id,id,a.id(),null,t,mode);
        db.update("INSERT INTO closure_record(requirement_id,case_id,actor_id,closed_ms,mode,reason) SELECT requirement_id,id,closed_by,closed_ms,closure_mode,close_reason FROM case_record WHERE id=?",id);
        changed(a,"CASO_CERRADO_"+mode,"CAS",id,n(c,"scope_id"),reason);
        access.notice(n(c,"responsible_id"),"El Caso fue cerrado. El cierre del REQ es una decision separada.","CAS",id);
        var req=db.one("SELECT principal_scope FROM requirement WHERE id=?",n(c,"requirement_id"));
        access.notifyHeads(n(req,"principal_scope"),"Cambio el estado de un Caso del REQ.","REQ",n(c,"requirement_id"));
        bumpReq(n(c,"requirement_id"));
    }

    // --- Hitos (V14, Milestones) ------------------------------------------------------------
    /** Hito de la transicion, en la misma transaccion; at es el instante que guarda su fuente (columna o auditoria). */
    void milestone(String event,String type,long resource,long com,Long req,Long cas,long actor,Long subject,Long scope,long at,String detail){
        milestones.write(new Milestones.Hito(event,type,resource,com,req,cas,actor,subject,scope,at,detail));
    }
    /** Hito de un Caso o de lo que cuelga de el (asignacion, tarea): cadena y ambito del Caso. */
    void caseMilestone(String event,String type,long resource,long cas,long actor,Long subject,long at,String detail){
        var c=db.one("SELECT c.requirement_id,c.scope_id,r.communication_id FROM case_record c JOIN requirement r ON r.id=c.requirement_id WHERE c.id=?",cas);
        milestone(event,type,resource,n(c,"communication_id"),n(c,"requirement_id"),cas,actor,subject,n(c,"scope_id"),at,detail);
    }
    /** Hito de una SOL sin REQ (distribuir, redirigir, devolver, descartar). */
    void solMilestone(String event,long sol,long actor,long scope,long at,String detail){
        milestone(event,"SOL",sol,n(db.one("SELECT communication_id FROM solicitation WHERE id=?",sol),"communication_id"),null,null,actor,null,scope,at,detail);
    }
    /** SOL asumida: el REQ nace en el mismo acto, con su instante, su principal y su encargado. */
    void solAssumed(Actor a,long sol,long req,boolean direct){
        var r=db.one("SELECT communication_id,created_ms,principal_scope,attention_user FROM requirement WHERE id=?",req);
        milestone("SOL_ASUMIDA","SOL",sol,n(r,"communication_id"),req,null,a.id(),nullableId(r,"attention_user"),n(r,"principal_scope"),n(r,"created_ms"),direct?"DIRECTA":null);
    }
    /** REQ cerrado: nunca antes de su creacion (la atencion resuelta en el acto guarda una fecha efectiva declarada). */
    void reqClosed(Actor a,long req,long closed,String mode){
        var r=db.one("SELECT communication_id,created_ms,principal_scope FROM requirement WHERE id=?",req);
        milestone("REQ_CERRADO","REQ",req,n(r,"communication_id"),req,null,a.id(),null,n(r,"principal_scope"),Math.max(closed,n(r,"created_ms")),mode);
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
