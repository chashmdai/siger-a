package cl.nexbyte.sigera;

import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/** Primera atencion sobre COM/REQ existentes. Workflow conserva la transaccion e idempotencia. */
@Service
public class Attention {
    private static final Logger LOG=LoggerFactory.getLogger(Attention.class);
    /** Carga del resumen de cambios de las versiones anteriores a 0.8.0 (V18). */
    static final String SUMMARY_MARKER="RESUMEN_CAMBIOS_V18";
    static final int BATCH=500;
    private final Db db;
    private final Access access;
    private final Documents documents;
    private final Lifecycle life;
    private final PersonIndex index;
    private final OriginRegion regions;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public Attention(Db db,Access access,Documents documents,Lifecycle life,PersonIndex index,OriginRegion regions,PlatformTransactionManager manager){
        this.db=db;this.access=access;this.documents=documents;this.life=life;this.index=index;this.regions=regions;this.tx=new TransactionTemplate(manager);
    }
    private long now(){return System.currentTimeMillis();}
    public boolean exists(long req){return db.count("SELECT COUNT(*) FROM attention_record WHERE requirement_id=?",req)>0;}
    public static String stateSql(String r,String t){return "CASE WHEN "+r+".state='CERRADO' THEN 'RESUELTA' WHEN EXISTS(SELECT 1 FROM case_record ac WHERE ac.requirement_id="+r+".id) THEN 'CON_CASO' WHEN EXISTS(SELECT 1 FROM attention_handoff ah WHERE ah.requirement_id="+r+".id AND ah.state='PENDIENTE') THEN 'PENDIENTE_RECEPCION' WHEN "+t+".followup_required=TRUE THEN 'REQUIERE_CASO' ELSE 'EN_ATENCION' END";}
    private Map<String,Object> req(long id){return db.one("SELECT * FROM requirement WHERE id=?",id);}
    private boolean receiving(Actor a,Map<String,Object> h){return access.handoffRecipient(a,h);}
    public boolean mayRead(Actor a,Map<String,Object> r){
        // Por materia (D9) solo la del REQ abre la ficha; la de un Caso no.
        if(access.reqWideRead(a,r)||access.reqMain(a,r))return true;
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
    /** Versiones de la ficha, en orden: una ficha no vuelve a una version anterior (perderia estructura). */
    static final List<String> VERSIONS=List.of("1.0","1.1","1.2");
    /** Campos que agrega la ficha 1.2 (DISENO 3.4); relationship_version la deriva el servidor. */
    static final List<String> KEYS_12=List.of("relationship_code","facts_region_code","facts_commune","facts_place","institutions_list");
    static final Set<String> PEOPLE_ROLES=Set.of("NNA","ACOMPANANTE","REQUIRENTE","OTRA_PERSONA");
    /**
     * Esquema pequeno y cerrado, no almacenamiento arbitrario de JSON del navegador. 1.0 se acepta tal como antes;
     * 1.1 agrega la identificacion y el contacto estructurados de la persona adulta; 1.2 (0.8.0, D33) aplica esas
     * reglas tambien en la modalidad NNA y agrega personas canonicas (persona-1), relacion con el NNA, hechos e
     * instituciones. previous es la ficha vigente: un valor historico identico se conserva sin revalidarse (los datos
     * anteriores nunca se reescriben).
     */
    private Map<String,Object> normalize(Input envelope,Map<String,Object> previous){
        var input=new Input(childMap(envelope,"form"));var out=new LinkedHashMap<String,Object>();
        String version=input.values().containsKey("schema_version")?input.text("schema_version",8,true):"1.0";
        require(VERSIONS.contains(version),"Version de ficha desconocida.");
        String before=previous.isEmpty()?"1.0":s(previous,"schema_version").isEmpty()?"1.0":s(previous,"schema_version");
        require(VERSIONS.indexOf(version)>=VERSIONS.indexOf(before),"La ficha ya usa una version posterior; actualiza la pantalla antes de guardar.");
        boolean v12=version.equals("1.2");
        Set<String> allowed=new HashSet<>(List.of("schema_version","modality","course","people"));
        if(v12)allowed.addAll(KEYS_12);
        if(!version.equals("1.0")){
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
        Object rawPeople=input.values().getOrDefault("people",List.of());
        if(!(rawPeople instanceof List<?> people)||people.size()>30)throw new Problem(400,"La ficha admite hasta 30 personas; para grupos usa la descripcion colectiva.");
        if(v12){version12(input,out,mode,previous,people);return out;}
        if(version.equals("1.1"))adult(out,mode,previous,false);
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
     * Reglas de la persona adulta de nivel superior (DeclaredPerson, las de 1.1). Todo es opcional (solo el motivo es
     * indispensable). Vacio nunca es error; un valor igual al de la version vigente se conserva aunque no siga el
     * formato nuevo. En 1.1, la modalidad NNA conserva el texto libre de contacto, region y nacionalidad; en 1.2
     * (allModalities) las reglas rigen en ambas modalidades. El nombre y el documento resumidos que usa la COM se
     * derivan de las partes estructuradas cuando existen.
     */
    private List<String> adult(Map<String,Object> out,String mode,Map<String,Object> previous,boolean allModalities){
        var errors=new ArrayList<String>();
        java.util.function.Predicate<String> kept=key->!s(out,key).isEmpty()&&s(out,key).equals(s(previous,key));
        var k=DeclaredPerson.FICHA;
        DeclaredPerson.identity(out,k,kept,errors,"");
        if(mode.equals("GENERAL")||allModalities){
            DeclaredPerson.contact(out,k,kept,errors,"");
            String derived=DeclaredPerson.documentSummary(out,k);
            if(!derived.isEmpty())out.put("identity_document",derived);
            else if(!s(out,"identity_document").isEmpty()&&!kept.test("identity_document"))errors.add("Registra el documento en los campos de RUN o de documento extranjero.");
            String name=DeclaredPerson.displayName(out,k);
            if(!name.isEmpty())out.put("presenter_name",name);
        }
        if(!allModalities&&!errors.isEmpty())throw new Problem(400,String.join(" ",errors));
        return errors;
    }
    /**
     * Ficha 1.2 (DISENO 3.4): reglas de la persona adulta en ambas modalidades, relacion con el NNA del catalogo
     * vigente, cantidad aproximada entera, hechos, instituciones y personas canonicas. Lo historico identico se
     * conserva; los textos de 1.0/1.1 de las personas pasan solo como historicos (legacy_*), sin interpretarse.
     */
    private void version12(Input input,Map<String,Object> out,String mode,Map<String,Object> previous,List<?> people){
        var errors=adult(out,mode,previous,true);
        var codes=DeclaredPerson.Codes.load(db);
        out.put("relationship_code",input.text("relationship_code",40,false));
        DeclaredPerson.code(out,"relationship_code","relationship_version",DeclaredPerson.RELATIONSHIP,List.of(previous),codes,errors,"Relacion con el NNA");
        String count=s(out,"approximate_count");
        if(!count.isEmpty()&&!count.equals(s(previous,"approximate_count"))&&!count.matches("[1-9][0-9]{0,5}"))errors.add("Cantidad aproximada: escribe un numero entero mayor que cero.");
        DeclaredPerson.facts(input,out,previous,errors);
        out.put("institutions_list",DeclaredPerson.institutions(input.values().get("institutions_list"),previous.get("institutions_list"),codes,errors));
        var before=DeclaredPerson.canonicalList(previous.get("people"));var normalized=new ArrayList<Map<String,Object>>();int i=0;
        for(Object raw:people)normalized.add(DeclaredPerson.person(raw,PEOPLE_ROLES,before,codes,"Persona "+(++i),false,errors));
        out.put("people",normalized);
        if(!errors.isEmpty())throw new Problem(400,String.join(" ",errors));
    }
    /** Claves que el servidor deriva en esta version (no cuentan en el resumen de cambios). */
    static Set<String> derived(Map<String,Object> form){
        var k=DeclaredPerson.FICHA;var out=new HashSet<String>();
        if(!DeclaredPerson.displayName(form,k).isEmpty())out.add("presenter_name");
        if(!DeclaredPerson.documentSummary(form,k).isEmpty())out.add("identity_document");
        return out;
    }
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
        long t=now();
        long com=db.insert("INSERT INTO communication(channel,received_ms,created_ms,created_by,origin_scope,presenter,identity_document,subject,body,state,contact_email,contact_phone,contact_restrictions,presenter_domicile) VALUES(?,?,?,?,?,?,?,?,?,'ATENDIDA',?,?,?,?)",channel,received,t,a.id(),scope,s(data,"presenter_name"),s(data,"identity_document"),subject,s(data,"motive"),s(data,"contact_email"),s(data,"contact_phone"),safety.length()>1000?safety.substring(0,1000):safety,s(data,"presenter_domicile"));
        life.comFolio(com);
        life.milestone("COM_REGISTRADA","COM",com,com,null,null,a.id(),null,scope,t,null);
        long principal=access.solDestination(new Input(Map.of("scope_id",scope))).scope();
        long req=life.insertReq(a,com,null,principal,a.id(),scope);
        String serialized=json.writeValueAsString(data);boolean follow=s(data,"course").equals("SEGUIMIENTO");
        db.update("INSERT INTO attention_record(requirement_id,modality,data_json,followup_required,updated_by,updated_ms) VALUES(?,?,?,?,?,?)",req,s(data,"modality"),serialized,follow,a.id(),now());
        index.refresh(com);
        documents.attachIntake(a,scope,com,in.values().get("attachment_tokens"));
        snapshot(a,req,0,"Primer registro con antecedentes disponibles",serialized,data,null);
        db.audit(a.id(),"COM_REGISTRADA_ATENCION","COM",com,scope,"Atencion directa. REQ "+req);
        db.audit(a.id(),"ATENCION_INICIADA","REQ",req,principal,"COM "+com+". Sin SOL ni Caso automatico.");
        if(follow)access.notifyHeads(principal,"Una primera atencion requiere un Caso de seguimiento.","REQ",req);
        if(in.flag("resolve_now"))return resolve(a,req(req),in);
        return new Workflow.Result("REQ",req,"Atencion registrada. COM y REQ creados; puedes completar la ficha o entregarla.");
    }
    /**
     * Version de la ficha con su region derivada de sus propios datos (V16, D21) y el resumen de cambios respecto de la
     * version anterior (V18, DISENO 2.5; nulo en la primera). Devuelve el resumen.
     */
    private Map<String,List<String>> snapshot(Actor a,long req,long revision,String reason,String data,Map<String,Object> form,Map<String,Object> previous){
        var changes=DeclaredPerson.changes(previous,form,derived(form));
        db.update("INSERT INTO attention_revision(requirement_id,requirement_revision,author_id,registered_ms,reason,data_json,region_code,change_summary) VALUES(?,?,?,?,?,?,?,?)",
            req,revision,a.id(),now(),reason,data,OriginRegion.ofForm(form),changes==null?null:json.writeValueAsString(changes));
        return changes;
    }
    private Workflow.Result save(Actor a,Map<String,Object> r,Input in){
        long id=n(r,"id");var previous=decode(s(db.one("SELECT data_json FROM attention_record WHERE requirement_id=?",id),"data_json"));
        var data=normalize(in,previous);String reason=in.text("reason",2000,true),serialized=json.writeValueAsString(data);
        // D21: todo cambio de la region derivada respecto de la version anterior (completar, corregir o quitar), sin codigos.
        boolean regionChanged=!Objects.equals(OriginRegion.ofForm(previous),OriginRegion.ofForm(data));
        boolean follow=s(data,"course").equals("SEGUIMIENTO");boolean was=b(db.one("SELECT followup_required FROM attention_record WHERE requirement_id=?",id),"followup_required");
        db.update("UPDATE attention_record SET modality=?,data_json=?,followup_required=?,updated_by=?,updated_ms=? WHERE requirement_id=?",s(data,"modality"),serialized,follow,a.id(),now(),id);
        index.refresh(n(r,"communication_id"));
        db.update("UPDATE requirement SET revision=revision+1 WHERE id=?",id);
        var changes=snapshot(a,id,n(r,"revision")+1,reason,serialized,data,previous);
        // Sin valores: solo version, si cambio la region de origen, el motivo y la cantidad de campos (DISENO 2.5).
        db.audit(a.id(),"FICHA_COMPLEMENTADA","REQ",id,n(r,"principal_scope"),"Version "+(n(r,"revision")+1)+". "+(regionChanged?"Region de origen modificada. ":"")+reason+" "+DeclaredPerson.summary(changes));
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
        long t=now();
        long handoff=db.insert("INSERT INTO attention_handoff(requirement_id,from_user,from_scope,sent_by,sent_ms,target_scope,target_user,recipient_kind,reason) VALUES(?,?,?,?,?,?,?,?,?)",id,n(r,"attention_user"),n(r,"attention_scope"),a.id(),t,target,user,kind,reason);
        life.milestone("ENTREGA_ENVIADA","ENTREGA",handoff,n(r,"communication_id"),id,null,a.id(),user,target,t,null);
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
        long t=now();
        db.update("UPDATE attention_handoff SET state=?,decided_by=?,decided_ms=?,decision_reason=? WHERE id=?",decision,a.id(),t,reason,n(h,"id"));
        life.milestone("ENTREGA_DECIDIDA","ENTREGA",n(h,"id"),n(r,"communication_id"),id,null,a.id(),nullableId(h,"target_user"),n(h,"target_scope"),t,decision);
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
        long t=now();
        db.update("UPDATE requirement SET state='CERRADO',closed_ms=?,closed_by=?,close_reason=?,revision=revision+1 WHERE id=?",t,a.id(),reason,id);
        db.update("INSERT INTO closure_record(requirement_id,case_id,actor_id,closed_ms,mode,reason) VALUES(?,NULL,?,?,?,?)",id,a.id(),t,"PRIMERA_ATENCION",reason);
        life.reqClosed(a,id,t,"PRIMERA_ATENCION");
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
            var form=decode(s(record,"data_json"));out.put("form",form);
            out.put("suggested_region",access.suggestedRegion(n(r,"communication_id"),form.get("region") instanceof String region?region:null));
            out.put("origin_region",regions.ofRequirement(id)); // D21: registrada, vigente y si fue corregida (solo con la ficha legible).
            out.put("versions",db.rows("SELECT v.id,v.requirement_revision,v.author_id,v.registered_ms,v.reason,v.change_summary,u.name AS author FROM attention_revision v JOIN staff u ON u.id=v.author_id WHERE v.requirement_id=? ORDER BY v.id DESC LIMIT 50",id));
            out.put("handoffs",db.rows("SELECT * FROM attention_handoff WHERE requirement_id=? ORDER BY id DESC LIMIT 50",id).stream().map(h->{var row=new LinkedHashMap<>(h);row.put("can_receive",receiving(a,h));row.put("can_withdraw",s(h,"state").equals("PENDIENTE")&&access.reqMain(a,r));return row;}).toList());
        }else out.put("handoffs",db.rows("SELECT h.id,h.state,h.sent_ms,h.target_scope,h.decided_ms FROM attention_handoff h WHERE requirement_id=? AND (sent_by=? OR from_user=?) ORDER BY id DESC LIMIT 50",id,a.id(),a.id()));
        return out;
    }
    /**
     * Resumen de cambios de las versiones existentes (marcador RESUMEN_CAMBIOS_V18): por lotes con el lock global, cada
     * version contra la anterior del mismo REQ; la primera queda sin resumen. Una version ilegible se omite y se cuenta
     * (solo cifras en el log). Corre tambien con migrate-only; si se interrumpe, el siguiente arranque la rehace.
     */
    void rebuildSummaries(){
        if(Boolean.TRUE.equals(tx.execute(st->db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",SUMMARY_MARKER)>0)))return;
        long last=0,versions=0,omitted=0;
        while(true){
            final long from=last;
            long[] result=tx.execute(st->{
                db.lock();
                var batch=db.rows("SELECT id,requirement_id,data_json FROM attention_revision WHERE id>? ORDER BY id LIMIT "+BATCH,from);
                long skipped=0;
                for(var v:batch){
                    var previous=db.rows("SELECT data_json FROM attention_revision WHERE requirement_id=? AND id<? ORDER BY id DESC LIMIT 1",n(v,"requirement_id"),n(v,"id"));
                    if(previous.isEmpty())continue;
                    try{
                        var form=decode(s(v,"data_json"));
                        db.update("UPDATE attention_revision SET change_summary=? WHERE id=?",json.writeValueAsString(DeclaredPerson.changes(decode(s(previous.getFirst(),"data_json")),form,derived(form))),n(v,"id"));
                    }catch(RuntimeException e){skipped++;}
                }
                return new long[]{batch.isEmpty()?0:n(batch.getLast(),"id"),batch.size(),skipped};
            });
            if(result==null||result[1]==0)break;
            last=result[0];versions+=result[1];omitted+=result[2];
        }
        tx.execute(st->{
            db.lock();
            if(db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",SUMMARY_MARKER)==0)
                db.update("INSERT INTO application_marker(code,applied_ms) VALUES(?,?)",SUMMARY_MARKER,System.currentTimeMillis());
            return null;
        });
        if(versions>0)LOG.info("Resumen de cambios de la ficha: {} versiones revisadas, {} omitidas.",versions,omitted);
    }
    public Map<String,Object> version(Actor a,long reqId,long version){
        var r=req(reqId);if(!mayRead(a,r))Problem.denied();var v=db.one("SELECT * FROM attention_revision WHERE requirement_id=? AND id=?",reqId,version);
        db.audit(a.id(),"FICHA_VERSION_CONSULTADA","REQ",reqId,n(r,"principal_scope"),"Version "+version);
        return Map.of("form",decode(s(v,"data_json")),"registered_ms",v.get("registered_ms"),"reason",v.get("reason"),"author_id",v.get("author_id"));
    }
}
