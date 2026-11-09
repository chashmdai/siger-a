package cl.nexbyte.sigera;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/** Primera atencion sobre COM/REQ existentes. Workflow conserva la transaccion e idempotencia. */
@Service
public class Attention {
    private final Db db;
    private final Access access;
    private final Documents documents;
    private final Lifecycle life;
    private final JsonMapper json=JsonMapper.builder().build();
    public Attention(Db db,Access access,Documents documents,Lifecycle life){
        this.db=db;this.access=access;this.documents=documents;this.life=life;
    }
    private long now(){return System.currentTimeMillis();}
    public boolean exists(long req){return db.count("SELECT COUNT(*) FROM attention_record WHERE requirement_id=?",req)>0;}
    public static String stateSql(String r,String t){return "CASE WHEN "+r+".state='CERRADO' THEN 'RESUELTA' WHEN EXISTS(SELECT 1 FROM case_record ac WHERE ac.requirement_id="+r+".id) THEN 'CON_CASO' WHEN EXISTS(SELECT 1 FROM attention_handoff ah WHERE ah.requirement_id="+r+".id AND ah.state='PENDIENTE') THEN 'PENDIENTE_RECEPCION' WHEN "+t+".followup_required=TRUE THEN 'REQUIERE_CASO' ELSE 'EN_ATENCION' END";}
    private Map<String,Object> req(long id){return db.one("SELECT * FROM requirement WHERE id=?",id);}
    private boolean receiving(Actor a,Map<String,Object> h){return access.handoffRecipient(a,h);}
    public boolean mayRead(Actor a,Map<String,Object> r){
        if(access.reqMain(a,r))return true;
        if(access.pendingAttentionRecipient(a,r))return true;
        return db.rows("SELECT * FROM case_record WHERE requirement_id=?",n(r,"id")).stream().anyMatch(c->access.caseSourceDocuments(a,c));
    }
    public boolean mayEdit(Actor a,Map<String,Object> r){return access.attentionWritable(a,r);}
    private void writable(Actor a,Map<String,Object> r){if(!mayEdit(a,r))throw new Problem(403,"La ficha no esta editable para tu funcion actual. Revisa la entrega pendiente o continua en el Caso.");}
    @SuppressWarnings("unchecked")
    private Map<String,Object> decode(String value){return (Map<String,Object>)json.readValue(value,Map.class);}
    private Map<String,Object> childMap(Input in,String field){
        Object value=in.values().get(field);
        if(!(value instanceof Map<?,?> raw))throw new Problem(400,"Falta la ficha de atencion.");
        var out=new LinkedHashMap<String,Object>();raw.forEach((k,v)->out.put(k.toString(),v));return out;
    }
    private String optionalChoice(Input in,String key,String...values){
        String value=in.text(key,40,false);if(!value.isEmpty())require(Set.of(values).contains(value),"Valor no permitido: "+key);return value;
    }
    /** Campos de la ficha 1.1: identificacion estructurada de la persona adulta (todos opcionales). */
    static final List<String> ADULT_KEYS=List.of("presenter_first_name","presenter_middle_name","presenter_last_name","presenter_second_last_name",
        "presenter_run_number","presenter_run_dv","presenter_birth_date","presenter_document_kind","presenter_document_number");
    /**
     * Esquema pequeno y cerrado, no almacenamiento arbitrario de JSON del navegador. 1.0 se acepta tal como antes;
     * 1.1 agrega la identificacion y el contacto estructurados de la persona adulta. previous es la ficha vigente:
     * un valor historico identico se conserva sin revalidarse (los datos anteriores nunca se reescriben).
     */
    private Map<String,Object> normalize(Input envelope,Map<String,Object> previous){
        var input=new Input(childMap(envelope,"form"));var out=new LinkedHashMap<String,Object>();
        String version=input.values().containsKey("schema_version")?input.text("schema_version",8,true):"1.0";
        require(version.equals("1.0")||version.equals("1.1"),"Version de ficha desconocida.");
        Set<String> allowed=new HashSet<>(List.of("schema_version","modality","course","people"));
        if(version.equals("1.1")){
            for(String key:ADULT_KEYS){int limit=switch(key){case "presenter_run_number"->12;case "presenter_run_dv"->1;case "presenter_birth_date"->10;case "presenter_document_number"->40;default->100;};out.put(key,input.text(key,limit,false));allowed.add(key);}
        }
        String[] shortFields={"presenter_name","identity_document","identity_type","relationship","contact_email","contact_phone","presenter_domicile","region","commune","preferred_name","pronouns","supports","court","rit","ruc","guardian_name","nationality","group_description","approximate_count"};
        String[] longFields={"motive","story","request_text","observations","orientation","actions","commitments","institutions","safety_note","participation_note","child_message","identity_note","priority_note"};
        for(String key:shortFields){int limit=switch(key){case "presenter_name","contact_email"->200;case "identity_document"->100;case "contact_phone"->80;default->500;};out.put(key,input.text(key,limit,false));allowed.add(key);}
        for(String key:longFields){out.put(key,input.text(key,key.equals("safety_note")?1000:12000,key.equals("motive")));allowed.add(key);}
        var choices=new LinkedHashMap<String,String[]>();
        choices.put("identity_type",new String[]{"RUN","OTRO","NO_DISPONIBLE"});
        choices.put("safe_contact",new String[]{"SEGURO","VERIFICAR","NO_CONTACTAR"});
        choices.put("judicial_status",new String[]{"SI","NO","DESCONOCIDO"});
        choices.put("guardian_status",new String[]{"SI","NO","DESCONOCIDO"});
        choices.put("participation",new String[]{"SI","NO","SIN_RESPUESTA","NO_CONSULTADO"});
        choices.put("participation_mode",new String[]{"VERBAL","ALTERNATIVA","NO_ENTREVISTA"});
        choices.put("role_explained",new String[]{"SI","NO","PENDIENTE"});
        choices.put("priority",new String[]{"NO_EVALUADA","SIN_SENALES","PRIORITARIA"});
        choices.put("children_scope",new String[]{"INDIVIDUAL","VARIOS","GRUPO","POR_IDENTIFICAR"});
        choices.forEach((key,values)->{out.put(key,optionalChoice(input,key,values));allowed.add(key);});
        require(input.values().keySet().stream().allMatch(allowed::contains),"La ficha contiene campos no reconocidos.");
        String mode=input.choice("modality","GENERAL","NNA");out.put("modality",mode);
        out.put("schema_version",version);out.put("course",input.choice("course","EN_ATENCION","SEGUIMIENTO"));
        if(version.equals("1.1"))adult(out,mode,previous);
        Object rawPeople=input.values().getOrDefault("people",List.of());
        if(!(rawPeople instanceof List<?> people)||people.size()>30)throw new Problem(400,"La ficha admite hasta 30 personas; para grupos usa la descripcion colectiva.");
        var normalized=new ArrayList<Map<String,Object>>();
        Set<String> personKeys=Set.of("role","name","identity_document","relationship","location","age","birth_date","nationality","schooling","preferred_name");
        for(Object raw:people){
            if(!(raw instanceof Map<?,?> source))throw new Problem(400,"Persona relacionada invalida.");
            var p=new LinkedHashMap<String,Object>();source.forEach((k,v)->p.put(k.toString(),v));
            require(personKeys.containsAll(p.keySet()),"Datos de persona no reconocidos.");var person=new Input(p);var row=new LinkedHashMap<String,Object>();
            row.put("role",person.choice("role","NNA","ACOMPANANTE","REQUIRENTE","OTRA_PERSONA"));
            for(String key:personKeys)if(!key.equals("role"))row.put(key,person.text(key,500,false));
            String birth=s(row,"birth_date");if(!birth.isBlank())try{require(!LocalDate.parse(birth).isAfter(LocalDate.now(java.time.ZoneId.of("America/Santiago"))),"La fecha de nacimiento no puede ser futura.");}catch(java.time.format.DateTimeParseException e){throw new Problem(400,"Fecha de nacimiento invalida.");}
            normalized.add(row); // Permite NNA sin nombre ni RUN: no inventar identidad.
        }
        out.put("people",normalized);return out;
    }

    /**
     * Reglas 1.1 de la persona adulta. Todo es opcional (solo el motivo es indispensable). Vacio nunca es error; un
     * valor igual al de la version vigente se conserva aunque no siga el formato nuevo. En la modalidad NNA los campos
     * de contacto y nacionalidad conservan el texto libre de siempre. El nombre y el documento resumidos que usa la COM
     * se derivan de las partes estructuradas cuando existen.
     */
    private void adult(Map<String,Object> out,String mode,Map<String,Object> previous){
        var errors=new ArrayList<String>();
        java.util.function.Predicate<String> kept=key->!s(out,key).isEmpty()&&s(out,key).equals(s(previous,key));
        java.util.function.BiConsumer<String,String> check=(key,problem)->{if(problem!=null&&!kept.test(key))errors.add(problem);};
        Map<String,String> nameLabels=Map.of("presenter_first_name","Primer nombre","presenter_middle_name","Segundo nombre","presenter_last_name","Primer apellido","presenter_second_last_name","Segundo apellido");
        for(String key:List.of("presenter_first_name","presenter_middle_name","presenter_last_name","presenter_second_last_name")){
            out.put(key,AdultIdentity.normalizeName(s(out,key)));String p=AdultIdentity.nameProblem(s(out,key));check.accept(key,p==null?null:nameLabels.get(key)+": "+p+".");
        }
        String birth=AdultIdentity.birthProblem(s(out,"presenter_birth_date"),AdultIdentity.today());check.accept("presenter_birth_date",birth==null?null:"Fecha de nacimiento: "+birth+".");
        String type=s(out,"identity_type"),number=s(out,"presenter_run_number").replaceAll("[.\\s]",""),dv=s(out,"presenter_run_dv").toUpperCase(Locale.ROOT);
        out.put("presenter_run_number",number);out.put("presenter_run_dv",dv);
        if(!number.isEmpty()||!dv.isEmpty()){
            if(!type.equals("RUN"))errors.add("El RUN corresponde a la identificacion RUN chileno.");
            else if(number.isEmpty()||dv.isEmpty())errors.add("Completa el numero y el digito verificador del RUN, o deja el RUN en blanco.");
            else if(!AdultIdentity.validRun(number,dv))errors.add("El numero y el digito verificador del RUN no coinciden.");
        }
        String kind=s(out,"presenter_document_kind");out.put("presenter_document_number",AdultIdentity.normalizeDocument(s(out,"presenter_document_number")));
        if(!kind.isEmpty()||!s(out,"presenter_document_number").isEmpty()){
            if(!type.equals("OTRO"))errors.add("El documento extranjero corresponde a la identificacion Documento extranjero u otro.");
            if(!kind.isEmpty()&&!AdultIdentity.DOCUMENT_KINDS.contains(kind))errors.add("Tipo de documento no permitido.");
            String doc=AdultIdentity.documentProblem(s(out,"presenter_document_number"));check.accept("presenter_document_number",doc==null?null:"Numero de documento: "+doc+".");
        }
        if(mode.equals("GENERAL")){
            out.put("contact_email",AdultIdentity.trim(s(out,"contact_email")));
            String phone=s(out,"contact_phone"),email=s(out,"contact_email"),region=s(out,"region"),commune=s(out,"commune"),nationality=s(out,"nationality");
            if(!phone.isEmpty()&&!AdultIdentity.validMobile(phone))check.accept("contact_phone","Celular: escribe los ocho digitos despues de +56 9.");
            if(!email.isEmpty()&&!AdultIdentity.validEmail(email))check.accept("contact_email","Correo: revisa el formato.");
            if(!region.isEmpty()&&!AdultIdentity.validRegion(region))check.accept("region","Region: selecciona una region del listado.");
            if(!commune.isEmpty()&&!(AdultIdentity.validRegion(region)&&AdultIdentity.validCommune(region,commune)))check.accept("commune","Comuna: selecciona una comuna de la region indicada.");
            if(!nationality.isEmpty()&&!AdultIdentity.validNationality(nationality))check.accept("nationality","Nacionalidad: selecciona un pais o la opcion Otra / situacion especial.");
            String derived=type.equals("RUN")&&!number.isEmpty()&&!dv.isEmpty()?AdultIdentity.formatRun(number,dv)
                :type.equals("OTRO")&&!s(out,"presenter_document_number").isEmpty()?documentLabel(kind)+" "+s(out,"presenter_document_number"):"";
            if(!derived.isEmpty())out.put("identity_document",derived);
            else if(!s(out,"identity_document").isEmpty()&&!kept.test("identity_document"))errors.add("Registra el documento en los campos de RUN o de documento extranjero.");
            String name=String.join(" ",List.of(s(out,"presenter_first_name"),s(out,"presenter_middle_name"),s(out,"presenter_last_name"),s(out,"presenter_second_last_name")).stream().filter(x->!x.isEmpty()).toList());
            if(!name.isEmpty())out.put("presenter_name",name);
        }
        if(!errors.isEmpty())throw new Problem(400,String.join(" ",errors));
    }
    private static String documentLabel(String kind){return switch(kind){case "PASAPORTE"->"Pasaporte";case "IDENTIDAD_EXTRANJERA"->"Documento de identidad extranjero";case "OTRO"->"Otro documento";default->"Documento";};}
    public Workflow.Result run(Actor a,String type,long id,Long expected,Input in){
        if(type.equals("ATTENTION_CREATE"))return create(a,in);
        var r=req(id);require(exists(id),"Este REQ no procede de una ficha de primera atencion.");revision(r,expected);
        if(type.equals("ATTENTION_DECIDE"))return decide(a,r,in);
        writable(a,r);
        return switch(type){
            case "ATTENTION_SAVE" -> save(a,r,in);
            case "ATTENTION_SEND" -> send(a,r,in);
            case "ATTENTION_RESOLVE" -> resolve(a,r,in);
            default -> throw new Problem(400,"Operacion de atencion desconocida.");
        };
    }
    private Workflow.Result create(Actor a,Input in){
        long scope=in.id("scope_id");access.scope(scope);if(!a.registrar(scope))Problem.denied();
        var data=normalize(in,Map.of());long received=in.instant("received_at",true);require(received<=now(),"La recepcion no puede ser futura.");
        String channel=in.choice("channel",Lifecycle.CHANNELS);
        String subject=in.text("subject",240,false);if(subject.isBlank())subject=s(data,"motive").replaceAll("\\s+"," ");if(subject.length()>240)subject=subject.substring(0,240);
        String safety=(s(data,"safe_contact").equals("NO_CONTACTAR")?"NO CONTACTAR. ":s(data,"safe_contact").equals("VERIFICAR")?"Verificar antes de contactar. ":"")+s(data,"safety_note");
        long com=db.insert("INSERT INTO communication(channel,received_ms,created_ms,created_by,origin_scope,presenter,identity_document,subject,body,state,contact_email,contact_phone,contact_restrictions,presenter_domicile) VALUES(?,?,?,?,?,?,?,?,?,'ATENDIDA',?,?,?,?)",channel,received,now(),a.id(),scope,s(data,"presenter_name"),s(data,"identity_document"),subject,s(data,"motive"),s(data,"contact_email"),s(data,"contact_phone"),safety.length()>1000?safety.substring(0,1000):safety,s(data,"presenter_domicile"));
        life.comFolio(com);
        long principal=access.solDestination(new Input(Map.of("scope_id",scope))).scope();
        long req=life.insertReq(a,com,null,principal,a.id(),scope);
        String serialized=json.writeValueAsString(data);boolean follow=s(data,"course").equals("SEGUIMIENTO");
        db.update("INSERT INTO attention_record(requirement_id,modality,data_json,followup_required,updated_by,updated_ms) VALUES(?,?,?,?,?,?)",req,s(data,"modality"),serialized,follow,a.id(),now());
        documents.attachIntake(a,scope,com,in.values().get("attachment_tokens"));
        snapshot(a,req,0,"Primer registro con antecedentes disponibles",serialized);
        db.audit(a.id(),"COM_REGISTRADA_ATENCION","COM",com,scope,"Atencion directa. REQ "+req);
        db.audit(a.id(),"ATENCION_INICIADA","REQ",req,principal,"COM "+com+". Sin SOL ni Caso automatico.");
        if(follow)access.notifyHeads(principal,"Una primera atencion requiere un Caso de seguimiento.","REQ",req);
        if(in.flag("resolve_now"))return resolve(a,req(req),in);
        return new Workflow.Result("REQ",req,"Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.");
    }
    private void snapshot(Actor a,long req,long revision,String reason,String data){
        db.update("INSERT INTO attention_revision(requirement_id,requirement_revision,author_id,registered_ms,reason,data_json) VALUES(?,?,?,?,?,?)",req,revision,a.id(),now(),reason,data);
    }
    private Workflow.Result save(Actor a,Map<String,Object> r,Input in){
        long id=n(r,"id");var data=normalize(in,decode(s(db.one("SELECT data_json FROM attention_record WHERE requirement_id=?",id),"data_json")));String reason=in.text("reason",2000,true),serialized=json.writeValueAsString(data);
        boolean follow=s(data,"course").equals("SEGUIMIENTO");boolean was=b(db.one("SELECT followup_required FROM attention_record WHERE requirement_id=?",id),"followup_required");
        db.update("UPDATE attention_record SET modality=?,data_json=?,followup_required=?,updated_by=?,updated_ms=? WHERE requirement_id=?",s(data,"modality"),serialized,follow,a.id(),now(),id);
        db.update("UPDATE requirement SET revision=revision+1 WHERE id=?",id);
        snapshot(a,id,n(r,"revision")+1,reason,serialized);
        db.audit(a.id(),"FICHA_COMPLEMENTADA","REQ",id,n(r,"principal_scope"),"Version "+(n(r,"revision")+1)+". "+reason);
        if(follow&&!was)access.notifyHeads(n(r,"principal_scope"),"Una primera atencion necesita asignacion de Caso.","REQ",id);
        return new Workflow.Result("REQ",id,"Ficha guardada con historial. El ingreso original permanece intacto.");
    }
    private Workflow.Result send(Actor a,Map<String,Object> r,Input in){
        long id=n(r,"id");require(db.count("SELECT COUNT(*) FROM flow_request WHERE requirement_id=? AND state='PENDIENTE'",id)==0,"Resuelve primero la transferencia o colaboracion pendiente.");
        // send/save/resolve se ejecutan solo tras writable(): REQ abierto, sin Caso ni entrega pendiente.
        String kind=in.choice("recipient_kind","PROFESIONAL","JEFATURA");long target=in.id("scope_id");access.scope(target);Long user=null;
        if(kind.equals("PROFESIONAL")){user=in.id("user_id");access.assignable(user,target);require(!Objects.equals(user,nullableId(r,"attention_user"))||target!=n(r,"attention_scope"),"La persona ya esta a cargo de esta atencion.");}
        else target=access.solDestination(in).scope();
        String reason=in.text("reason",2000,true);
        db.update("INSERT INTO attention_handoff(requirement_id,from_user,from_scope,sent_by,sent_ms,target_scope,target_user,recipient_kind,reason) VALUES(?,?,?,?,?,?,?,?,?)",id,n(r,"attention_user"),n(r,"attention_scope"),a.id(),now(),target,user,kind,reason);
        db.update("UPDATE requirement SET revision=revision+1 WHERE id=?",id);
        db.audit(a.id(),"ATENCION_ENTREGADA","REQ",id,n(r,"principal_scope"),"Destino "+target+". "+reason);
        if(user!=null)access.notice(user,"Tienes una primera atencion para recibir y continuar.","REQ",id);
        else access.notifyReceivers(target,"Tienes una primera atencion para recibir.","REQ",id);
        return new Workflow.Result("REQ",id,"Entrega enviada. Conservas el encargo hasta su recepcion; no se cambia la jefatura principal.");
    }
    private Workflow.Result decide(Actor a,Map<String,Object> r,Input in){
        long id=n(r,"id");var h=db.one("SELECT * FROM attention_handoff WHERE id=? AND requirement_id=?",in.id("handoff_id"),id);
        require(s(h,"state").equals("PENDIENTE"),"La entrega ya fue resuelta.");
        require(s(r,"state").equals("ABIERTO")&&db.count("SELECT COUNT(*) FROM case_record WHERE requirement_id=?",id)==0,"La atencion ya tiene gestion formal o esta cerrada.");
        require(n(h,"from_user")==n(r,"attention_user")&&n(h,"from_scope")==n(r,"attention_scope"),"El encargo cambio; actualiza la ficha.");
        String decision=in.choice("decision","RECIBIDA","DEVUELTA","RETIRADA");String reason=in.text("reason",2000,true);
        if(decision.equals("RETIRADA")){if(!access.reqMain(a,r))Problem.denied();}else if(!receiving(a,h))Problem.denied();
        if(decision.equals("RECIBIDA"))db.update("UPDATE requirement SET attention_user=?,attention_scope=? WHERE id=?",a.id(),n(h,"target_scope"),id);
        db.update("UPDATE attention_handoff SET state=?,decided_by=?,decided_ms=?,decision_reason=? WHERE id=?",decision,a.id(),now(),reason,n(h,"id"));
        db.update("UPDATE requirement SET revision=revision+1 WHERE id=?",id);
        db.audit(a.id(),"ATENCION_"+decision,"REQ",id,n(r,"principal_scope"),"Entrega "+n(h,"id")+". "+reason);
        access.notice(n(h,"sent_by"),"Tu entrega de primera atencion fue "+decision.toLowerCase(Locale.ROOT)+".","REQ",id);
        if(decision.equals("RECIBIDA"))access.notifyHeads(n(r,"principal_scope"),"Se registro continuidad de primera atencion; el principal del REQ no cambia.","REQ",id);
        return new Workflow.Result("REQ",id,"Entrega registrada. El mismo REQ conserva su folio y antecedentes.");
    }
    private Workflow.Result resolve(Actor a,Map<String,Object> r,Input in){
        writable(a,r);long id=n(r,"id");
        var record=db.one("SELECT * FROM attention_record WHERE requirement_id=?",id);
        require(!b(record,"followup_required"),"La ficha indica seguimiento. Documenta primero su cambio si ya no corresponde, o solicita apertura de Caso.");
        require(db.count("SELECT COUNT(*) FROM flow_request WHERE requirement_id=? AND state='PENDIENTE'",id)==0&&db.count("SELECT COUNT(*) FROM reopening WHERE requirement_id=? AND state='PENDIENTE'",id)==0,"Existen decisiones pendientes.");
        String result=in.choice("result",Lifecycle.IMMEDIATE_RESULTS);String reason=in.text("reason",12000,true);
        long effective=in.instant("effective_at",true);long received=n(db.one("SELECT received_ms FROM communication WHERE id=?",n(r,"communication_id")),"received_ms");require(effective>=received&&effective<=now(),"La fecha del resultado debe estar entre recepcion y ahora.");
        db.update("INSERT INTO management_note(requirement_id,author_id,effective_ms,registered_ms,body,reserved) VALUES(?,?,?,?,?,FALSE)",id,a.id(),effective,now(),result+": "+reason);
        db.update("UPDATE requirement SET state='CERRADO',closed_ms=?,closed_by=?,close_reason=?,revision=revision+1 WHERE id=?",now(),a.id(),reason,id);
        db.update("INSERT INTO closure_record(requirement_id,case_id,actor_id,closed_ms,mode,reason) VALUES(?,NULL,?,?,?,?)",id,a.id(),now(),"PRIMERA_ATENCION",reason);
        db.audit(a.id(),"ATENCION_RESUELTA","REQ",id,n(r,"principal_scope"),result+". Resultado registrado por quien atendio.");
        access.notifyHeads(n(r,"principal_scope"),"Una primera atencion se resolvio sin Caso.","REQ",id);
        return new Workflow.Result("REQ",id,"Atencion resuelta y REQ cerrado sin SOL ni Caso. Resultado e historial conservados.");
    }
    public Map<String,Object> view(Actor a,Map<String,Object> r){
        long id=n(r,"id");if(!exists(id))return Map.of();
        var record=db.one("SELECT * FROM attention_record WHERE requirement_id=?",id);boolean readable=mayRead(a,r);
        var out=new LinkedHashMap<String,Object>();out.put("modality",record.get("modality"));out.put("can_read",readable);out.put("can_edit",mayEdit(a,r));
        out.put("state",db.one("SELECT "+stateSql("r","t")+" AS state FROM requirement r JOIN attention_record t ON t.requirement_id=r.id WHERE r.id=?",id).get("state"));
        out.put("updated_ms",record.get("updated_ms"));out.put("updated_by",record.get("updated_by"));out.put("followup_required",record.get("followup_required"));
        if(readable){
            var original=db.one("SELECT folio,channel,received_ms,created_ms,created_by FROM communication WHERE id=?",n(r,"communication_id"));
            out.put("origin",original);
            out.put("form",decode(s(record,"data_json")));
            out.put("versions",db.rows("SELECT v.id,v.requirement_revision,v.author_id,v.registered_ms,v.reason,u.name AS author FROM attention_revision v JOIN staff u ON u.id=v.author_id WHERE v.requirement_id=? ORDER BY v.id DESC LIMIT 50",id));
            out.put("handoffs",db.rows("SELECT * FROM attention_handoff WHERE requirement_id=? ORDER BY id DESC LIMIT 50",id).stream().map(h->{var row=new LinkedHashMap<>(h);row.put("can_receive",receiving(a,h));row.put("can_withdraw",s(h,"state").equals("PENDIENTE")&&access.reqMain(a,r));return row;}).toList());
        }else out.put("handoffs",db.rows("SELECT h.id,h.state,h.sent_ms,h.target_scope,h.decided_ms FROM attention_handoff h WHERE requirement_id=? AND (sent_by=? OR from_user=?) ORDER BY id DESC LIMIT 50",id,a.id(),a.id()));
        return out;
    }
    public Map<String,Object> version(Actor a,long reqId,long version){
        var r=req(reqId);if(!mayRead(a,r))Problem.denied();var v=db.one("SELECT * FROM attention_revision WHERE requirement_id=? AND id=?",reqId,version);
        db.audit(a.id(),"FICHA_VERSION_CONSULTADA","REQ",reqId,n(r,"principal_scope"),"Version "+version);
        return Map.of("form",decode(s(v,"data_json")),"registered_ms",v.get("registered_ms"),"reason",v.get("reason"),"author_id",v.get("author_id"));
    }
}
