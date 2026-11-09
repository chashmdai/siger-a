package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import cl.nexbyte.sigera.Workflow.Result;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Lifecycle.done;
import static cl.nexbyte.sigera.Lifecycle.now;
import static cl.nexbyte.sigera.Problem.require;

/** Ingreso y distribucion: COM externa o inmediata, SOL a la funcion receptora y antecedentes. */
@Service
public class IntakeCommands {
    private final Db db;
    private final Access access;
    private final Lifecycle life;
    private final Documents documents;

    public IntakeCommands(Db db,Access access,Lifecycle life,Documents documents){
        this.db=db;
        this.access=access;
        this.life=life;
        this.documents=documents;
    }

    Result run(Actor a,String type,long id,Long expected,Input in){
        return switch(type){
            case "COM_CREATE" -> createCom(a,in,false);
            case "COM_IMMEDIATE" -> createCom(a,in,true);
            case "COM_ROUTE" -> routeCom(a,id,expected,in);
            case "COM_ASSUME" -> assumeOwn(a,id,expected,in);
            case "SOL_ROUTE","SOL_RETURN","SOL_ASSUME","SOL_DISCARD" -> sol(a,type,id,expected,in);
            case "COM_LINK" -> link(a,id,expected,in);
            case "LINK_DECIDE" -> decideLink(a,id,expected,in);
            case "LINK_RETURN" -> returnLink(a,id,expected,in);
            default -> throw new Problem(400,"Comando desconocido.");
        };
    }

    private Result createCom(Actor a,Input in,boolean immediate){
        long scope=in.id("scope_id");
        access.scope(scope);
        if(!a.registrar(scope))Problem.denied();
        String channel=in.choice("channel",Lifecycle.CHANNELS);
        long received=in.instant("received_at",true);
        require(received<=now(),"La fecha de recepcion no puede ser futura.");
        String subject=in.text("subject",240,true),body=in.text("body",20000,true);
        long com=db.insert("INSERT INTO communication(channel,received_ms,created_ms,created_by,origin_scope,presenter,contact,identity_document,subject,body) VALUES(?,?,?,?,?,?,?,?,?,?)",
            channel,received,now(),a.id(),scope,in.text("presenter",200,false),in.text("contact",300,false),in.text("identity_document",100,false),subject,body);
        life.comFolio(com);
        db.update("UPDATE communication SET contact_email=?,contact_phone=?,contact_restrictions=?,presenter_domicile=? WHERE id=?",
            in.text("contact_email",200,false),in.text("contact_phone",80,false),in.text("contact_restrictions",1000,false),in.text("presenter_domicile",500,false),com);
        persons(com,in.values().get("persons"));
        documents.attachIntake(a,scope,com,in.values().get("attachment_tokens"));
        life.changed(a,"COM_REGISTRADA","COM",com,scope,"Canal "+channel);
        if(!immediate){
            access.notifyDistributors(db.one("SELECT * FROM communication WHERE id=?",com),"Hay un ingreso pendiente de clasificacion.");
            return done("COM",com);
        }
        // Atencion resuelta en el mismo acto: COM + REQ cerrado, sin SOL ni Caso.
        String response=in.reason();
        long effective=in.instant("effective_at",true);
        require(effective>=received&&effective<=now(),"Revisa la fecha efectiva de la atencion.");
        String result=in.choice("result",Lifecycle.IMMEDIATE_RESULTS);
        long req=life.newReq(a,com,null,scope,a.id(),scope);
        db.update("INSERT INTO management_note(requirement_id,author_id,effective_ms,registered_ms,body) VALUES(?,?,?,?,?)",req,a.id(),effective,now(),result+": "+response);
        db.update("UPDATE requirement SET state='CERRADO',closed_ms=?,closed_by=?,close_reason=?,revision=1 WHERE id=?",effective,a.id(),response,req);
        db.update("UPDATE communication SET state='ATENDIDA',revision=1 WHERE id=?",com);
        db.update("INSERT INTO closure_record(requirement_id,actor_id,closed_ms,mode,reason) VALUES(?,?,?,'PRIMERA_ATENCION',?)",req,a.id(),effective,response);
        life.changed(a,"PRIMERA_ATENCION_RESUELTA","REQ",req,scope,response);
        return done("REQ",req);
    }

    /** Antecedentes declarados de personas del ingreso; no es un registro maestro de personas. */
    private void persons(long com,Object persons){
        if(persons==null)return;
        if(!(persons instanceof List<?> people)||people.size()>12)throw new Problem(400,"Maximo 12 personas relacionadas por ingreso.");
        for(Object item:people){
            if(!(item instanceof Map<?,?> raw))throw new Problem(400,"Persona relacionada invalida.");
            Map<String,Object> values=new LinkedHashMap<>();
            raw.forEach((k,v)->values.put(k.toString(),v));
            var person=new Input(values);
            String role=person.choice("role","NNA","ACOMPANANTE","OTRA_PERSONA");
            String name=person.text("name",200,false),location=person.text("location",500,false),identity=person.text("identity_document",100,false);
            require(!name.isBlank()||!location.isBlank()||!identity.isBlank(),"Registra algun antecedente disponible de la persona, sin inventar identidad.");
            db.update("INSERT INTO communication_person(communication_id,person_role,name,identity_document,relationship,location_text) VALUES(?,?,?,?,?,?)",
                com,role,name,identity,person.text("relationship",200,false),location);
        }
    }

    private Result assumeOwn(Actor a,long comId,Long expected,Input in){
        var com=access.comForRead(a,comId);
        revision(com,expected);
        long scope=n(com,"origin_scope");
        access.head(a,scope);
        require(s(com,"state").equals("NUEVA")&&db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",comId)==0,"La COM ya fue encaminada.");
        long user=in.id("responsible_id");
        long attention=access.solAttentionScope(a,user,scope,in.optionalId("area_scope_id"));
        long sol=db.insert("INSERT INTO solicitation(communication_id,destination_scope,state,classification,created_by,created_ms) VALUES(?,?,'ASUMIDA',?,?,?)",comId,scope,in.reason(),a.id(),now());
        long req=life.newReq(a,comId,sol,scope,user,attention);
        db.update("UPDATE communication SET state='ENVIADA',revision=revision+1 WHERE id=?",comId);
        life.changed(a,"ASUNCION_INGRESO_PROPIO","SOL",sol,scope,in.reason());
        access.notice(user,"Se asigno un REQ por asuncion directa de jefatura.","REQ",req);
        return done("REQ",req);
    }

    private Result routeCom(Actor a,long id,Long expected,Input in){
        var com=access.comForRead(a,id);
        revision(com,expected);
        if(!access.canDistribute(a,com))Problem.denied();
        require(s(com,"state").equals("NUEVA"),"La COM ya esta encaminada.");
        require(db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",id)==0,"Ya existe una SOL. Usa su redireccion.");
        var destination=access.solDestination(in);
        long scope=destination.scope();
        if(access.regional(n(com,"origin_scope")))require(scope==n(com,"origin_scope"),"La distribucion regional se limita a su sede; usa redireccion de SOL posteriormente.");
        long sol=db.insert("INSERT INTO solicitation(communication_id,destination_scope,requested_area_scope,classification,created_by,created_ms) VALUES(?,?,?,?,?,?)",id,scope,destination.area(),in.reason(),a.id(),now());
        db.update("UPDATE communication SET state='ENVIADA',revision=revision+1 WHERE id=?",id);
        life.changed(a,"SOL_DISTRIBUIDA","SOL",sol,scope,in.reason());
        access.notifySolReceivers(scope,"Tienes una SOL pendiente de asumir.",sol);
        return done("SOL",sol);
    }

    private Result sol(Actor a,String type,long id,Long expected,Input in){
        var r=db.one("SELECT * FROM solicitation WHERE id=?",id);
        revision(r,expected);
        long comId=n(r,"communication_id"),oldScope=n(r,"destination_scope");
        var com=db.one("SELECT * FROM communication WHERE id=?",comId);
        boolean returned=s(r,"state").equals("DEVUELTA");
        if(returned){
            // Una SOL devuelta solo la redistribuye Clasificacion y Distribucion.
            if(!access.canDistribute(a,com)||!type.equals("SOL_ROUTE"))Problem.denied();
        }else{
            if(!access.solReceiver(a,oldScope))Problem.denied();
            require(s(r,"state").equals("PENDIENTE"),"La SOL no esta pendiente.");
        }
        String reason=in.reason();
        switch(type){
            case "SOL_ROUTE" -> {
                var destination=access.solDestination(in);
                long target=destination.scope();
                if(returned&&access.regional(n(com,"origin_scope")))require(target==n(com,"origin_scope"),"Coordinacion redistribuye su sede; la jefatura puede redirigir la SOL a otra sede.");
                require(returned||target!=oldScope||!Objects.equals(destination.area(),nullableId(r,"requested_area_scope")),"El destino y el area sugerida no cambiaron.");
                db.update("UPDATE solicitation SET destination_scope=?,requested_area_scope=?,classification=?,state='PENDIENTE',revision=revision+1 WHERE id=?",target,destination.area(),reason,id);
                life.changed(a,"SOL_REDIRIGIDA","SOL",id,target,"Destino anterior "+oldScope+". "+reason);
                access.notifySolReceivers(target,"Una SOL fue remitida a tu ambito.",id);
                return done("SOL",id);
            }
            case "SOL_RETURN" -> {
                db.update("UPDATE solicitation SET state='DEVUELTA',revision=revision+1 WHERE id=?",id);
                life.changed(a,"SOL_DEVUELTA_DISTRIBUCION","SOL",id,oldScope,reason);
                access.notifyDistributors(com,"Una SOL fue devuelta a clasificacion.");
                return done("SOL",id);
            }
            case "SOL_DISCARD" -> {
                String disposition=in.choice("disposition","INVALIDA","DUPLICADA");
                db.update("UPDATE solicitation SET state='DESCARTADA',revision=revision+1 WHERE id=?",id);
                db.update("UPDATE communication SET state=?,revision=revision+1 WHERE id=?",disposition,comId);
                life.changed(a,"SOL_"+disposition,"SOL",id,oldScope,reason);
                return done("SOL",id);
            }
            default -> {
                // SOL_ASSUME: al asumir nace el REQ, aun sin Caso.
                long user=in.id("responsible_id");
                long attentionScope=access.solAttentionScope(a,user,oldScope,nullableId(r,"requested_area_scope"));
                long req=life.newReq(a,comId,id,oldScope,user,attentionScope);
                db.update("UPDATE solicitation SET state='ASUMIDA',revision=revision+1 WHERE id=?",id);
                life.changed(a,"SOL_ASUMIDA","SOL",id,oldScope,reason);
                access.notice(user,"Se te asigno la atencion de un REQ; si requiere seguimiento debe abrirse un Caso.","REQ",req);
                return done("REQ",req);
            }
        }
    }

    private Result link(Actor a,long com,Long expected,Input in){
        var c=access.comForRead(a,com);
        revision(c,expected);
        if(!access.canDistribute(a,c)&&!a.head(n(c,"origin_scope")))Problem.denied();
        require(s(c,"state").equals("NUEVA"),"La COM ya tiene un encaminamiento.");
        String folio=in.text("folio",48,true).toUpperCase(Locale.ROOT);
        var matches=db.rows("SELECT * FROM requirement WHERE folio=?",folio);
        if(matches.isEmpty())throw new Problem(404,"No se encontro un REQ con ese folio exacto.");
        var r=matches.getFirst();
        long req=n(r,"id");
        require(n(r,"communication_id")!=com,"No se vincula el origen a si mismo.");
        db.insert("INSERT INTO communication_link(communication_id,requirement_id,reason,proposed_by,proposed_ms) VALUES(?,?,?,?,?)",com,req,in.reason(),a.id(),now());
        db.update("UPDATE communication SET state='VINCULADA',revision=revision+1 WHERE id=?",com);
        life.changed(a,"COM_HIJA_PROPUESTA","COM",com,n(c,"origin_scope"),"REQ "+req+". "+in.reason());
        access.notifyHeads(n(r,"principal_scope"),"Una COM fue propuesta como antecedente de tu REQ.","REQ",req);
        return done("COM",com);
    }

    private Result decideLink(Actor a,long id,Long expected,Input in){
        var l=db.one("SELECT * FROM communication_link WHERE id=?",id);
        revision(l,expected);
        require(l.get("returned_ms")==null,"El antecedente ya volvio a clasificacion; no se altera la decision historica.");
        long req=n(l,"requirement_id");
        var r=db.one("SELECT * FROM requirement WHERE id=?",req);
        access.head(a,n(r,"principal_scope"));
        String decision=in.choice("decision","ADMITIDA","RECHAZADA");
        Long target=in.optionalId("case_id");
        if(target!=null){
            var c=db.one("SELECT * FROM case_record WHERE id=?",target);
            require(n(c,"requirement_id")==req,"El Caso no pertenece al REQ.");
        }
        db.update("UPDATE communication_link SET state=?,target_case_id=?,decided_by=?,decided_ms=?,decision_reason=?,revision=revision+1 WHERE id=?",decision,target,a.id(),now(),in.reason(),id);
        life.changed(a,"ANTECEDENTE_"+decision,"REQ",req,n(r,"principal_scope"),"COM "+n(l,"communication_id")+". "+in.reason());
        if(target!=null&&decision.equals("ADMITIDA"))
            access.notice(n(db.one("SELECT responsible_id FROM case_record WHERE id=?",target),"responsible_id"),"Un antecedente fue habilitado para tu Caso; no implica reapertura.","CAS",target);
        return done("REQ",req);
    }

    private Result returnLink(Actor a,long id,Long expected,Input in){
        var l=db.one("SELECT * FROM communication_link WHERE id=?",id);
        revision(l,expected);
        var r=db.one("SELECT * FROM requirement WHERE id=?",n(l,"requirement_id"));
        access.head(a,n(r,"principal_scope"));
        require(s(l,"state").equals("RECHAZADA")&&l.get("returned_ms")==null,"Solo se devuelve un antecedente rechazado que no haya sido devuelto.");
        long com=n(l,"communication_id");
        var c=db.one("SELECT * FROM communication WHERE id=?",com);
        require(s(c,"state").equals("VINCULADA"),"El ingreso ya tiene otro encaminamiento.");
        require(db.count("SELECT COUNT(*) FROM requirement WHERE communication_id=?",com)==0,"El ingreso ya origino otro REQ.");
        String reason=in.reason();
        db.update("UPDATE communication_link SET returned_ms=?,returned_by=?,return_reason=?,revision=revision+1 WHERE id=?",now(),a.id(),reason,id);
        db.update("UPDATE communication SET state='NUEVA',revision=revision+1 WHERE id=?",com);
        db.update("UPDATE solicitation SET state='DEVUELTA',revision=revision+1 WHERE communication_id=? AND state<>'ASUMIDA'",com);
        life.changed(a,"ANTECEDENTE_DEVUELTO","REQ",n(r,"id"),n(r,"principal_scope"),"COM "+com+". "+reason);
        life.changed(a,"COM_DEVUELTA_CLASIFICACION","COM",com,n(c,"origin_scope"),"Vinculo "+id+" rechazado. "+reason);
        access.notifyDistributors(c,"Una COM rechazada como antecedente requiere nueva clasificacion.");
        return done("REQ",n(r,"id"));
    }
}
