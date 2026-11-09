package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import cl.nexbyte.sigera.Workflow.Result;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Lifecycle.done;
import static cl.nexbyte.sigera.Lifecycle.now;
import static cl.nexbyte.sigera.Problem.require;

/** Caso: equipo, revision y cierre, constancias y agenda de tareas (independiente de actuaciones). */
@Service
public class CaseCommands {
    private final Db db;
    private final Access access;
    private final Lifecycle life;

    public CaseCommands(Db db,Access access,Lifecycle life){
        this.db=db;
        this.access=access;
        this.life=life;
    }

    Result run(Actor a,String type,long id,Long expected,Input in){
        return switch(type){
            case "CASE_NOTE","CASE_ASSIGN","CASE_ACK","CASE_MEMBER_ADD","CASE_MEMBER_REMOVE","CASE_REQUEST_CLOSE","CASE_WITHDRAW_CLOSE","CASE_CLOSE","CASE_RETURN","CASE_WAIT","CASE_RESUME","CASE_MATTER" -> caseCommand(a,type,id,expected,in);
            case "NOTE_RECTIFY" -> rectify(a,id,in);
            case "TASK_CREATE" -> createTask(a,id,expected,in);
            case "TASK_EDIT","TASK_COMPLETE","TASK_CANCEL","TASK_ATTEMPT" -> task(a,type,id,expected,in);
            default -> throw new Problem(400,"Comando desconocido.");
        };
    }

    private Result caseCommand(Actor a,String type,long id,Long expected,Input in){
        var c=access.caseForWrite(a,id);
        revision(c,expected);
        long scope=n(c,"scope_id"),req=n(c,"requirement_id");
        boolean head=a.head(scope),owner=n(c,"responsible_id")==a.id();
        if(type.equals("CASE_ACK")){
            if(!owner)Problem.denied();
            db.update("UPDATE case_assignment SET acknowledged_ms=? WHERE case_id=? AND current_flag=TRUE AND user_id=? AND acknowledged_ms IS NULL",now(),id,a.id());
            life.changed(a,"ASIGNACION_ACUSADA","CAS",id,scope,"Acuse manual.");
            life.bumpCase(id);
            return done("CAS",id);
        }
        life.openCase(c);
        life.openReq(db.one("SELECT * FROM requirement WHERE id=?",req));
        if(type.equals("CASE_NOTE")){
            life.note(a,req,id,scope,in,null);
            life.bumpCase(id);
            return done("CAS",id);
        }
        String reason=in.reason();
        switch(type){
            case "CASE_ASSIGN" -> {
                // Reasignar transfiere todas las tareas no terminales del Caso al nuevo responsable.
                access.head(a,scope);
                long target=in.id("responsible_id");
                access.assignable(target,scope);
                require(target!=n(c,"responsible_id"),"El profesional ya es responsable.");
                db.update("UPDATE case_record SET responsible_id=? WHERE id=?",target,id);
                life.assignment(a,id,target);
                for(var t:db.rows("SELECT id,executor_id FROM task_item WHERE case_id=? AND state IN "+Lifecycle.OPEN_TASK,id)){
                    db.update("UPDATE task_item SET executor_id=?,revision=revision+1 WHERE id=?",target,n(t,"id"));
                    life.changed(a,"TAR09_TRASPASO","CAS",id,scope,"Tarea "+n(t,"id")+" de "+n(t,"executor_id")+" a "+target);
                }
                db.update("DELETE FROM case_member WHERE case_id=? AND user_id=?",id,target);
                access.notice(n(c,"responsible_id"),"Cambio tu asignacion; se conserva tu autoria historica.","CAS",id);
            }
            case "CASE_MEMBER_ADD" -> {
                if(!head&&!owner)Problem.denied();
                long target=in.id("user_id");
                access.assignable(target,scope);
                require(target!=n(c,"responsible_id"),"El responsable ya integra el Caso.");
                db.update("INSERT INTO case_member(case_id,user_id,added_by,added_ms) VALUES(?,?,?,?)",id,target,a.id(),now());
                access.notice(target,"Fuiste incorporado al equipo de un Caso.","CAS",id);
            }
            case "CASE_MEMBER_REMOVE" -> {
                if(!head&&!owner)Problem.denied();
                long target=in.id("user_id");
                require(db.count("SELECT COUNT(*) FROM task_item WHERE case_id=? AND executor_id=? AND state IN "+Lifecycle.OPEN_TASK,id,target)==0,"Reasigna o resuelve explicitamente sus tareas pendientes antes de retirarlo.");
                db.update("DELETE FROM case_member WHERE case_id=? AND user_id=?",id,target);
            }
            case "CASE_REQUEST_CLOSE" -> {
                if(!owner)Problem.denied();
                require(!s(c,"state").equals("EN_REVISION"),"Ya esta en revision.");
                require(life.pendingDerivations(id)==0,"Hay una derivacion pendiente.");
                require(life.pendingTasks(id)==0,"Completa o cancela las tareas pendientes antes de solicitar el cierre.");
                db.update("UPDATE case_record SET state='EN_REVISION' WHERE id=?",id);
                access.notifyHeads(scope,"Un Caso requiere revision de termino.","CAS",id);
            }
            case "CASE_WITHDRAW_CLOSE" -> {
                if(!owner)Problem.denied();
                require(s(c,"state").equals("EN_REVISION"),"Solo puedes retirar una solicitud de cierre en revision.");
                db.update("UPDATE case_record SET state='EN_GESTION' WHERE id=?",id);
                access.notifyHeads(scope,"El responsable retiro su solicitud de cierre; el Caso vuelve a gestion.","CAS",id);
            }
            case "CASE_CLOSE" -> {
                var r=db.one("SELECT * FROM requirement WHERE id=?",req);
                String mode=Rules.closure(s(r,"complexity"),owner,head,a.substitutions().contains(scope),s(c,"state"),(int)life.pendingTasks(id),life.pendingDerivations(id)>0);
                life.finishCase(a,c,mode,reason);
                return done("CAS",id);
            }
            case "CASE_RETURN" -> {
                access.head(a,scope);
                require(s(c,"state").equals("EN_REVISION"),"El Caso no esta en revision.");
                db.update("UPDATE case_record SET state='EN_GESTION' WHERE id=?",id);
                access.notice(n(c,"responsible_id"),"La jefatura devolvio el termino con observaciones.","CAS",id);
            }
            case "CASE_WAIT" -> {
                access.head(a,scope);
                require(s(c,"state").equals("EN_GESTION"),"Solo se pone en espera un Caso en gestion.");
                db.update("UPDATE case_record SET state='EN_ESPERA' WHERE id=?",id);
            }
            case "CASE_RESUME" -> {
                access.head(a,scope);
                require(s(c,"state").equals("EN_ESPERA"),"El Caso no esta en espera.");
                db.update("UPDATE case_record SET state='EN_GESTION' WHERE id=?",id);
            }
            case "CASE_MATTER" -> {
                access.head(a,scope);
                db.update("UPDATE case_record SET matter=? WHERE id=?",in.choice("matter",Lifecycle.MATTERS),id);
            }
            default -> throw new Problem(400,"Accion no reconocida.");
        }
        life.bumpCase(id);
        life.changed(a,type,"CAS",id,scope,reason);
        return done("CAS",id);
    }

    private Result rectify(Actor a,long id,Input in){
        var old=db.one("SELECT * FROM management_note WHERE id=?",id);
        Long cas=nullableId(old,"case_id");
        long req=n(old,"requirement_id"),scope;
        if(cas!=null){
            var c=access.caseForWrite(a,cas);
            life.openCase(c);
            scope=n(c,"scope_id");
            if(n(old,"author_id")!=a.id()&&!a.head(scope))Problem.denied();
        }else{
            var r=access.reqForRead(a,req);
            life.openReq(r);
            scope=n(r,"principal_scope");
            if(!access.reqMain(a,r))Problem.denied();
        }
        require(db.count("SELECT COUNT(*) FROM management_note WHERE rectifies_id=?",id)==0,"Esta constancia ya tiene una rectificacion; corrige la vigente.");
        if(b(old,"reserved")&&!in.flag("reserved"))throw new Problem(409,"La rectificacion no puede desclasificar una constancia reservada.");
        life.note(a,req,cas,scope,in,id);
        if(cas==null)life.bumpReq(req);else life.bumpCase(cas);
        return done(cas==null?"REQ":"CAS",cas==null?req:cas);
    }

    private Result createTask(Actor a,long cas,Long expected,Input in){
        var c=access.caseForWrite(a,cas);
        revision(c,expected);
        life.openCase(c);
        long user=in.id("executor_id");
        Actor target=access.user(user);
        if(!access.member(target,c))Problem.denied();
        long due=in.instant("due_at",false);
        int hours=reminderHours(in);
        db.insert("INSERT INTO task_item(case_id,title,executor_id,created_by,created_ms,due_ms,mandatory,reminder_interval_hours,next_reminder_ms) VALUES(?,?,?,?,?,?,?,?,?)",
            cas,in.text("title",300,true),user,a.id(),now(),due==0?null:due,false,hours,due==0?null:due);
        life.changed(a,"TAREA_CREADA","CAS",cas,n(c,"scope_id"),"Agenda independiente de actuaciones.");
        access.notice(user,"Tienes una tarea asignada en un Caso.","CAS",cas);
        life.bumpCase(cas);
        return done("CAS",cas);
    }

    private int reminderHours(Input in){
        String raw=in.text("remind_every_hours",4,false);
        if(raw.isEmpty())return 24;
        return Integer.parseInt(in.choice("remind_every_hours","0","24","48","168"));
    }

    private Result task(Actor a,String type,long id,Long expected,Input in){
        var t=db.one("SELECT * FROM task_item WHERE id=?",id);
        revision(t,expected);
        long cas=n(t,"case_id");
        var c=access.caseForWrite(a,cas);
        boolean head=a.head(n(c,"scope_id")),owner=n(c,"responsible_id")==a.id(),executor=n(t,"executor_id")==a.id(),creator=n(t,"created_by")==a.id();
        if(!head&&!owner&&!executor&&!creator)Problem.denied();
        require(Set.of("PENDIENTE","EN_CURSO").contains(s(t,"state")),"La tarea ya termino.");
        String reason=in.reason();
        switch(type){
            case "TASK_COMPLETE" -> {
                if(!executor)Problem.denied();
                db.update("UPDATE task_item SET state='COMPLETADA',next_reminder_ms=NULL,revision=revision+1 WHERE id=?",id);
            }
            case "TASK_CANCEL" -> db.update("UPDATE task_item SET state='CANCELADA',next_reminder_ms=NULL,revision=revision+1 WHERE id=?",id);
            case "TASK_ATTEMPT" -> db.update("UPDATE task_item SET state='EN_CURSO',revision=revision+1 WHERE id=?",id);
            default -> {
                // TASK_EDIT
                life.openCase(c);
                long target=in.id("executor_id");
                if(!head&&!owner&&!creator)Problem.denied();
                Actor u=access.user(target);
                if(!access.member(u,c))Problem.denied();
                long due=in.instant("due_at",false);
                int hours=reminderHours(in);
                db.update("UPDATE task_item SET executor_id=?,due_ms=?,reminder_interval_hours=?,next_reminder_ms=?,revision=revision+1 WHERE id=?",target,due==0?null:due,hours,due==0?null:due,id);
                access.notice(target,"Una tarea fue actualizada o reasignada.","CAS",cas);
            }
        }
        // Regularizar tareas residuales no reabre ni crea actuaciones.
        life.changed(a,type,"CAS",cas,n(c,"scope_id"),"Tarea "+id+". "+reason);
        life.bumpCase(cas);
        return done("CAS",cas);
    }
}
