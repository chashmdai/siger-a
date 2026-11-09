package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import cl.nexbyte.sigera.Workflow.Result;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Lifecycle.done;
import static cl.nexbyte.sigera.Lifecycle.now;
import static cl.nexbyte.sigera.Problem.require;

/** REQ: clasificacion, apertura de Casos, cierre expreso, flujos entre unidades y nueva gestion. */
@Service
public class RequirementCommands {
    private final Db db;
    private final Access access;
    private final Lifecycle life;

    public RequirementCommands(Db db,Access access,Lifecycle life){
        this.db=db;
        this.access=access;
        this.life=life;
    }

    Result run(Actor a,String type,long id,Long expected,Input in){
        return switch(type){
            case "REQ_CLASSIFY","REQ_CASE_CREATE","REQ_CLOSE","REQ_NOTE" -> req(a,type,id,expected,in);
            case "FLOW_REQUEST" -> requestFlow(a,id,expected,in);
            case "FLOW_DECIDE" -> decideFlow(a,id,expected,in);
            case "REOPEN_REQUEST","REOPEN_AUTHORIZE","REOPEN_DECIDE" -> reopen(a,type,id,expected,in);
            default -> throw new Problem(400,"Comando desconocido.");
        };
    }

    private Result req(Actor a,String type,long id,Long expected,Input in){
        var r=access.reqForRead(a,id);
        revision(r,expected);
        life.openReq(r);
        long scope=n(r,"principal_scope");
        if(type.equals("REQ_NOTE")){
            if(!access.reqMain(a,r))Problem.denied();
            require(db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",id)==0,"Registra actuaciones en el Caso correspondiente.");
            require(in.flag("no_followup"),"Si requiere seguimiento, la jefatura debe abrir un Caso en este REQ.");
            life.note(a,id,null,a.head(scope)?scope:n(r,"attention_scope"),in,null);
            life.bumpReq(id);
            return done("REQ",id);
        }
        access.head(a,scope);
        if(type.equals("REQ_CLASSIFY")){
            String level=in.choice("complexity","BAJA","MEDIANA","ALTA");
            String reason=in.reason();
            db.update("UPDATE requirement SET complexity=?,revision=revision+1 WHERE id=?",level,id);
            life.changed(a,"REQ_COMPLEJIDAD","REQ",id,scope,s(r,"complexity")+" -> "+level+". "+reason);
            return done("REQ",id);
        }
        if(type.equals("REQ_CASE_CREATE")){
            require(access.pendingHandoffs(id)==0,"Recibe o retira la entrega de primera atencion antes de abrir un Caso.");
            long caseScope=Optional.ofNullable(in.optionalId("scope_id")).orElse(scope);
            require(access.canOpenCaseUnder(scope,caseScope),"El Caso debe pertenecer a la unidad principal. Para otras unidades usa colaboracion.");
            long c=life.newCase(a,id,caseScope,in.id("responsible_id"),in.text("objective",3000,true),in.choice("matter",Lifecycle.MATTERS),null);
            return done("CAS",c);
        }
        // REQ_CLOSE: decision expresa de la jefatura principal, nunca automatica.
        require(db.count("SELECT COUNT(*) FROM attention_record ar WHERE ar.requirement_id=? AND ar.followup_required=TRUE AND NOT EXISTS(SELECT 1 FROM case_record ca WHERE ca.requirement_id=ar.requirement_id)",id)==0,"La primera atencion requiere seguimiento. Abre un Caso o documenta primero la rectificacion de ese curso de accion.");
        Rules.requirementClosable((int)db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=? AND state<>'CERRADO'",id),(int)life.pendingReq(id),a.head(scope));
        require(db.count("SELECT COUNT(*) FROM task_item t JOIN case_record c ON c.id=t.case_id WHERE c.requirement_id=? AND t.state IN "+Lifecycle.OPEN_TASK,id)==0,"El REQ todavia tiene tareas pendientes de sus Casos.");
        String reason=in.reason();
        db.update("UPDATE requirement SET state='CERRADO',closed_ms=?,closed_by=?,close_reason=?,revision=revision+1 WHERE id=?",now(),a.id(),reason,id);
        db.update("INSERT INTO closure_record(requirement_id,actor_id,closed_ms,mode,reason) SELECT id,closed_by,closed_ms,'JEFATURA_PRINCIPAL',close_reason FROM requirement WHERE id=?",id);
        life.changed(a,"REQ_CIERRE_EXPLICITO","REQ",id,scope,reason);
        access.notice(n(r,"created_by"),"Un REQ fue cerrado expresamente por su jefatura principal.","REQ",id);
        return done("REQ",id);
    }

    private Result requestFlow(Actor a,long req,Long expected,Input in){
        String kind=in.choice("kind","COLABORACION","TRANSFERENCIA","DERIVACION");
        var r=access.reqForRead(a,req);
        revision(r,expected);
        life.openReq(r);
        require(access.pendingHandoffs(req)==0,"Resuelve primero la entrega de primera atencion.");
        Long cas=null;
        long origin=n(r,"principal_scope");
        if(kind.equals("DERIVACION")){
            cas=in.id("case_id");
            var c=access.caseForWrite(a,cas);
            require(n(c,"requirement_id")==req,"El Caso pertenece a otro REQ.");
            access.head(a,n(c,"scope_id"));
            require(s(c,"state").equals("EN_GESTION"),"La derivacion se solicita con el Caso en gestion.");
            origin=n(c,"scope_id");
            require(life.pendingDerivations(cas)==0,"Ya hay una derivacion pendiente.");
        }else{
            access.head(a,origin);
            if(kind.equals("TRANSFERENCIA"))require(db.count("SELECT COUNT(*) FROM flow_request WHERE requirement_id=? AND kind='TRANSFERENCIA' AND state='PENDIENTE'",req)==0,"Ya hay una transferencia pendiente.");
        }
        long selected=in.id("target_scope");
        var destination=access.solDestination(new Input(Map.of("scope_id",selected)));
        long target=destination.scope();
        require(target!=origin,"El destino debe ser distinto.");
        long id=db.insert("INSERT INTO flow_request(kind,requirement_id,origin_case_id,origin_scope,target_scope,requested_by,created_ms,reason,objective) VALUES(?,?,?,?,?,?,?,?,?)",
            kind,req,cas,origin,target,a.id(),now(),in.reason(),in.text("objective",3000,!kind.equals("TRANSFERENCIA")));
        life.changed(a,kind+"_SOLICITADA","FLUJO",id,origin,in.reason());
        access.notifyHeads(target,"Tienes una peticion de "+kind.toLowerCase(Locale.ROOT)+".","FLUJO",id);
        life.bumpReq(req);
        return done("REQ",req);
    }

    private Result decideFlow(Actor a,long id,Long expected,Input in){
        var f=db.one("SELECT * FROM flow_request WHERE id=?",id);
        revision(f,expected);
        require(s(f,"state").equals("PENDIENTE"),"La solicitud ya fue decidida.");
        String decision=in.choice("decision","ACEPTADA","RECHAZADA","RETIRADA"),kind=s(f,"kind"),reason=in.reason();
        long req=n(f,"requirement_id"),origin=n(f,"origin_scope"),target=n(f,"target_scope");
        var r=db.one("SELECT * FROM requirement WHERE id=?",req);
        Long successor=null;
        if(decision.equals("RETIRADA")){
            // Retira quien solicito: jefatura del Caso de origen o del principal.
            if(kind.equals("DERIVACION"))access.head(a,n(db.one("SELECT scope_id FROM case_record WHERE id=?",n(f,"origin_case_id")),"scope_id"));
            else access.head(a,n(r,"principal_scope"));
        }else access.head(a,target);
        if(decision.equals("ACEPTADA")){
            life.openReq(r);
            if(kind.equals("TRANSFERENCIA")){
                // Cambia solo el principal; Casos, profesionales y tareas permanecen.
                require(n(r,"principal_scope")==origin,"El principal cambio desde la solicitud.");
                db.update("UPDATE requirement SET principal_scope=?,revision=revision+1 WHERE id=?",target,req);
            }else{
                long user=in.id("responsible_id");
                Long chosen=in.optionalId("case_scope_id");
                long management=chosen==null?access.solAttentionScope(a,user,target,null):chosen;
                require(access.managementScopes(a,target).contains(management),"Selecciona un area de gestion bajo la funcion receptora.");
                access.assignable(user,management);
                if(kind.equals("COLABORACION"))successor=life.newCase(a,req,management,user,s(f,"objective"),in.choice("matter",Lifecycle.MATTERS),null);
                else {
                    // DERIVACION: sucesor y cierre del origen en la misma transaccion.
                    var c=db.one("SELECT * FROM case_record WHERE id=?",n(f,"origin_case_id"));
                    require(n(c,"scope_id")==origin,"El contexto del Caso cambio; sustituye la solicitud.");
                    Rules.derivable(s(c,"state").equals("EN_GESTION"),(int)life.pendingTasks(n(c,"id")));
                    successor=life.newCase(a,req,management,user,s(f,"objective"),s(c,"matter"),n(c,"id"));
                    life.finishCase(a,c,"DERIVACION",reason+"; sucesor "+successor);
                }
            }
        }
        db.update("UPDATE flow_request SET state=?,decided_by=?,decided_ms=?,decision_reason=?,successor_case_id=?,revision=revision+1 WHERE id=?",decision,a.id(),now(),reason,successor,id);
        life.changed(a,kind+"_"+decision,"FLUJO",id,target,reason);
        life.bumpReq(req);
        access.notifyHeads(origin,"Se resolvio una peticion de "+kind.toLowerCase(Locale.ROOT)+".","FLUJO",id);
        return done("FLUJO",id);
    }

    private Result reopen(Actor a,String type,long id,Long expected,Input in){
        if(type.equals("REOPEN_REQUEST"))return requestReopening(a,id,in);
        var p=db.one("SELECT * FROM reopening WHERE id=?",id);
        revision(p,expected);
        require(s(p,"state").equals("PENDIENTE"),"Ya fue resuelta.");
        long req=n(p,"requirement_id");
        var r=db.one("SELECT * FROM requirement WHERE id=?",req);
        long principal=n(r,"principal_scope");
        Long cas=nullableId(p,"case_id");
        if(type.equals("REOPEN_AUTHORIZE")){
            access.head(a,principal);
            db.update("UPDATE reopening SET principal_approved_by=?,principal_approved_ms=?,principal_scope_at_approval=?,revision=revision+1 WHERE id=?",a.id(),now(),principal,id);
            life.changed(a,"REACTIVACION_REQ_AUTORIZADA","REQ",req,principal,in.reason());
            return done("REQ",req);
        }
        // REOPEN_DECIDE
        String decision=in.choice("decision","ACEPTADA","RECHAZADA");
        Map<String,Object> c=cas==null?null:db.one("SELECT * FROM case_record WHERE id=?",cas);
        long scope=c==null?principal:n(c,"scope_id");
        access.head(a,scope);
        if(c!=null&&a.id()==n(c,"responsible_id")&&a.substitutions().contains(scope))Problem.denied();
        if(decision.equals("ACEPTADA")){
            if(s(r,"state").equals("CERRADO")){
                if(!a.head(principal)){
                    Long approver=nullableId(p,"principal_approved_by");
                    require(approver!=null&&Objects.equals(nullableId(p,"principal_scope_at_approval"),principal)&&access.user(approver).head(principal),"Falta autorizacion vigente de la jefatura principal.");
                }
                db.update("UPDATE requirement SET state='ABIERTO',closed_ms=NULL,closed_by=NULL,close_reason=NULL,revision=revision+1 WHERE id=?",req);
                life.changed(a,"REQ_REACTIVADO","REQ",req,principal,in.reason());
            }
            if(cas!=null){
                var closedCase=Objects.requireNonNull(c);
                require(s(closedCase,"state").equals("CERRADO"),"El Caso ya esta abierto.");
                db.update("UPDATE case_record SET state='EN_GESTION',closed_ms=NULL,closed_by=NULL,closure_mode=NULL,close_reason=NULL,revision=revision+1 WHERE id=?",cas);
                life.changed(a,"CASO_REABIERTO","CAS",cas.longValue(),scope,in.reason());
            }
        }
        db.update("UPDATE reopening SET state=?,decided_by=?,decided_ms=?,decision_reason=?,revision=revision+1 WHERE id=?",decision,a.id(),now(),in.reason(),id);
        life.changed(a,"REAPERTURA_"+decision,"REAPERTURA",id,scope,in.reason());
        return done("REQ",req);
    }

    private Result requestReopening(Actor a,long id,Input in){
        var r=access.reqForRead(a,id);
        Long cas=in.optionalId("case_id");
        long scope=n(r,"principal_scope");
        if(cas==null){
            access.head(a,scope);
            require(s(r,"state").equals("CERRADO"),"El REQ ya esta abierto.");
        }else{
            var c=access.caseForWrite(a,cas);
            require(n(c,"requirement_id")==id&&s(c,"state").equals("CERRADO"),"Selecciona un Caso cerrado del REQ.");
            require(!s(c,"closure_mode").equals("DERIVACION"),"El Caso derivado continua mediante el sucesor o nueva gestion, no reapertura ordinaria.");
            if(n(c,"responsible_id")!=a.id())Problem.denied();
            scope=n(c,"scope_id");
        }
        require(db.count("SELECT COUNT(*) FROM reopening WHERE requirement_id=? AND state='PENDIENTE'",id)==0,"Ya existe una peticion de nueva gestion pendiente.");
        long p=db.insert("INSERT INTO reopening(requirement_id,case_id,requested_by,requested_ms,reason) VALUES(?,?,?,?,?)",id,cas,a.id(),now(),in.reason());
        life.changed(a,"REAPERTURA_SOLICITADA","REAPERTURA",p,scope,in.reason());
        access.notifyHeads(scope,"Hay una solicitud de reapertura.","REQ",id);
        return done("REQ",id);
    }
}
