package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Ficha de ingreso de la COM manual (0.8.0, D33, gate E03; DISENO 3.5, V18): presentador persona-1 opcional (una COM
 * que llega por oficio de una institucion se registra con la institucion y su rol, sin inventar una persona natural),
 * personas relacionadas persona-1, restricciones de contacto, hechos e instituciones, con las mismas reglas de la ficha
 * 1.2 (DeclaredPerson). Es la unica fuente de la COM que la tiene: las columnas de resumen de communication (presenter,
 * identity_document, contact_email, contact_phone, contact, presenter_domicile, contact_restrictions) se derivan de ella
 * y communication_person no recibe filas. Cada version queda en intake_revision con autor, motivo, region del
 * presentador y resumen de cambios. COM_COMPLETE complementa o rectifica con motivo mientras la cadena este abierta.
 * La recepcion del Formulario no la usa (sus datos estan en external_*).
 */
@Service
public class IntakeRecord {
    static final String SCHEMA="1.0";
    static final List<String> KEYS=List.of("schema_version","presenter","persons","contact_restrictions","facts_region_code","facts_commune","facts_place","institutions_list");
    static final Set<String> PERSON_ROLES=Set.of("NNA","ACOMPANANTE","OTRA_PERSONA");
    static final int MAX_PERSONS=12;
    /** Campos del ingreso que antes de 0.8.0 llegaban sueltos; con la ficha de ingreso no se admiten (una sola fuente). */
    static final List<String> LEGACY_FIELDS=List.of("presenter","contact","identity_document","contact_email","contact_phone","contact_restrictions","presenter_domicile","persons");
    private final Db db;
    private final Access access;
    private final PersonIndex index;
    private final JsonMapper json=JsonMapper.builder().build();
    private final PersonDims personDims;
    public IntakeRecord(Db db,Access access,PersonIndex index,PersonDims personDims){
        this.db=db;this.access=access;this.index=index;this.personDims=personDims;
    }

    static boolean exists(Db db,long com){return db.count("SELECT COUNT(*) FROM intake_record WHERE communication_id=?",com)>0;}
    @SuppressWarnings("unchecked")
    private Map<String,Object> decode(String value){return (Map<String,Object>)json.readValue(value,Map.class);}
    private Map<String,Object> current(long com){return decode(s(db.one("SELECT data_json FROM intake_record WHERE communication_id=?",com),"data_json"));}

    /** Esquema cerrado de la ficha de ingreso; previous = version vigente (vacio al crear) para conservar lo historico. */
    Map<String,Object> normalize(Object raw,Map<String,Object> previous){
        if(!(raw instanceof Map<?,?> source))throw new Problem(400,"Falta la ficha de ingreso.");
        var values=new LinkedHashMap<String,Object>();source.forEach((k,v)->values.put(k.toString(),v));
        require(KEYS.containsAll(values.keySet()),"La ficha de ingreso contiene campos no reconocidos.");
        var in=new Input(values);var out=new LinkedHashMap<String,Object>();var errors=new ArrayList<String>();
        if(values.containsKey("schema_version"))require(in.text("schema_version",8,true).equals(SCHEMA),"Version de la ficha de ingreso desconocida.");
        out.put("schema_version",SCHEMA);
        var codes=DeclaredPerson.Codes.load(db);
        Object presenter=values.get("presenter");
        var previousPresenter=previous.get("presenter") instanceof Map<?,?> p&&!p.isEmpty()?List.of(DeclaredPerson.canonical(p)):List.<Map<String,Object>>of();
        // Presentador opcional (captura progresiva; un ingreso institucional no exige una persona natural).
        if(DeclaredPerson.blank(presenter))out.put("presenter",Map.of());
        else if(!(presenter instanceof Map<?,?> m))throw new Problem(400,"Persona relacionada invalida.");
        else{
            var person=new LinkedHashMap<String,Object>();m.forEach((k,v)->person.put(k.toString(),v));person.putIfAbsent("role","REQUIRENTE");
            out.put("presenter",DeclaredPerson.person(person,Set.of("REQUIRENTE"),previousPresenter,codes,"Quien presenta",false,errors));
        }
        Object persons=values.getOrDefault("persons",List.of());
        if(!(persons instanceof List<?> list)||list.size()>MAX_PERSONS)throw new Problem(400,"Maximo "+MAX_PERSONS+" personas relacionadas por ingreso.");
        var before=DeclaredPerson.canonicalList(previous.get("persons"));var normalized=new ArrayList<Map<String,Object>>();int i=0;
        for(Object p:list)normalized.add(DeclaredPerson.person(p,PERSON_ROLES,before,codes,"Persona "+(++i),true,errors));
        out.put("persons",normalized);
        out.put("contact_restrictions",in.text("contact_restrictions",1000,false));
        DeclaredPerson.facts(in,out,previous,errors);
        out.put("institutions_list",DeclaredPerson.institutions(values.get("institutions_list"),previous.get("institutions_list"),codes,errors));
        if(!errors.isEmpty())throw new Problem(400,String.join(" ",errors));
        return out;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> presenter(Map<String,Object> data){return data.get("presenter") instanceof Map<?,?> p?(Map<String,Object>)p:Map.of();}
    /** Region de la version: la del presentador si es un codigo valido (OriginRegion). */
    static String region(Map<String,Object> data){return OriginRegion.of(presenter(data).get("region_code"));}
    private static String cut(String value,int max){return value.length()>max?value.substring(0,max):value;}
    /** Columnas de resumen de la COM, derivadas de la ficha de ingreso (no son fuente). */
    private void summarize(long com,Map<String,Object> data){
        var p=presenter(data);
        String document=DeclaredPerson.documentSummary(p,DeclaredPerson.CANONICAL);if(document.isEmpty())document=s(p,"legacy_document");
        db.update("UPDATE communication SET presenter=?,identity_document=?,contact_email=?,contact_phone=?,contact=?,presenter_domicile=?,contact_restrictions=? WHERE id=?",
            cut(DeclaredPerson.name(p),200),cut(document,100),s(p,"email"),s(p,"mobile"),s(p,"other_contact"),s(p,"address"),s(data,"contact_restrictions"),com);
    }
    private void addVersion(Actor a,long com,long revision,String reason,Map<String,Object> data,Map<String,List<String>> changes){
        long at=Lifecycle.now();
        long version=db.insert("INSERT INTO intake_revision(communication_id,revision,author_id,registered_ms,reason,data_json,region_code,change_summary) VALUES(?,?,?,?,?,?,?,?)",
            com,revision,a.id(),at,reason,json.writeValueAsString(data),region(data),changes==null?null:json.writeValueAsString(changes));
        long received=n(db.one("SELECT received_ms FROM communication WHERE id=?",com),"received_ms");
        personDims.save("INGRESO",version,com,at,received,data);
    }

    /** Registro de la ficha de ingreso al crear la COM manual (COM_CREATE o COM_IMMEDIATE con el bloque intake). */
    void create(Actor a,long com,Object raw){
        var data=normalize(raw,Map.of());
        db.update("INSERT INTO intake_record(communication_id,data_json,updated_by,updated_ms) VALUES(?,?,?,?)",com,json.writeValueAsString(data),a.id(),System.currentTimeMillis());
        addVersion(a,com,0,"Registro del ingreso",data,null);
        summarize(com,data);
    }

    /**
     * Cadena abierta (D35): la COM en NUEVA, ENVIADA o VINCULADA, ningun REQ nacido de ella cerrado y, si esta vinculada
     * como antecedente, el REQ del vinculo abierto. Descartada, atendida en el acto o con su REQ cerrado: no se modifica.
     */
    boolean open(Map<String,Object> com){
        long id=n(com,"id");
        if(!Set.of("NUEVA","ENVIADA","VINCULADA").contains(s(com,"state")))return false;
        if(db.count("SELECT COUNT(*) FROM requirement WHERE communication_id=? AND state<>'ABIERTO'",id)>0)return false;
        return db.count("SELECT COUNT(*) FROM communication_link l JOIN requirement r ON r.id=l.requirement_id WHERE l.communication_id=? AND l.returned_ms IS NULL AND r.state<>'ABIERTO'",id)==0;
    }
    /** Quien registra en el ambito de origen, quien distribuye la COM o la jefatura principal de su REQ (no la consulta institucional). */
    boolean writer(Actor a,Map<String,Object> com){
        if(a.registrar(n(com,"origin_scope"))||access.canDistribute(a,com))return true;
        return db.rows("SELECT * FROM requirement WHERE communication_id=?",n(com,"id")).stream().anyMatch(r->access.reqMain(a,r));
    }

    /** COM_COMPLETE: complementar o rectificar la ficha de ingreso con motivo; id = COM y su revision. */
    Workflow.Result complete(Actor a,long id,Long expected,Input in){
        var com=access.comForRead(a,id);
        if(!writer(a,com))Problem.denied();
        revision(com,expected);
        require(exists(db,id),"Esta COM no tiene ficha de ingreso: sus datos se conservan como se registraron.");
        require(open(com),"La cadena de esta COM esta cerrada o descartada; un registro cerrado solo cambia con reapertura formal.");
        String reason=in.text("reason",2000,true);
        var previous=current(id);var data=normalize(in.values().get("intake"),previous);
        var changes=DeclaredPerson.changes(previous,data,Set.of());
        require(!changes.get("COMPLEMENTO").isEmpty()||!changes.get("RECTIFICACION").isEmpty(),"La ficha de ingreso no cambio.");
        boolean regionChanged=!Objects.equals(region(previous),region(data));
        long next=n(com,"revision")+1;
        db.update("UPDATE intake_record SET data_json=?,updated_by=?,updated_ms=? WHERE communication_id=?",json.writeValueAsString(data),a.id(),System.currentTimeMillis(),id);
        db.update("UPDATE communication SET revision=revision+1 WHERE id=?",id);
        addVersion(a,id,next,reason,data,changes);
        summarize(id,data);
        index.refresh(id);
        // Sin valores: solo version, si cambio la region de origen, el motivo y la cantidad de campos.
        db.audit(a.id(),"FICHA_INGRESO_COMPLEMENTADA","COM",id,n(com,"origin_scope"),"Version "+next+". "+(regionChanged?"Region de origen modificada. ":"")+reason+" "+DeclaredPerson.summary(changes));
        return Lifecycle.done("COM",id);
    }

    /** Ficha de ingreso para la ficha de la COM (ya autorizada por comForRead): datos vigentes, versiones y facultad. */
    Map<String,Object> view(Actor a,Map<String,Object> com){
        long id=n(com,"id");var out=new LinkedHashMap<String,Object>();
        var record=db.one("SELECT * FROM intake_record WHERE communication_id=?",id);
        out.put("data",decode(s(record,"data_json")));out.put("updated_ms",record.get("updated_ms"));out.put("updated_by",record.get("updated_by"));
        out.put("can_complete",writer(a,com)&&open(com));
        out.put("versions",db.rows("SELECT v.id,v.revision,v.author_id,v.registered_ms,v.reason,v.change_summary,u.name AS author FROM intake_revision v JOIN staff u ON u.id=v.author_id WHERE v.communication_id=? ORDER BY v.id DESC LIMIT 50",id));
        return out;
    }
    /**
     * Personas de la ficha de ingreso con la forma de communication_person (person_role, name, identity_document,
     * relationship, location_text), para la ficha de la COM: el mismo lector toma la ficha de ingreso cuando existe.
     */
    List<Map<String,Object>> persons(long com){
        var out=new ArrayList<Map<String,Object>>();
        for(var p:DeclaredPerson.canonicalList(current(com).get("persons"))){
            var row=new LinkedHashMap<String,Object>();
            String document=DeclaredPerson.documentSummary(p,DeclaredPerson.CANONICAL);
            row.put("person_role",s(p,"role"));row.put("name",DeclaredPerson.name(p));row.put("identity_document",document.isEmpty()?s(p,"legacy_document"):document);
            row.put("relationship",s(p,"relationship_text"));row.put("location_text",s(p,"location_text"));
            out.add(row);
        }
        return out;
    }
    /** Una version anterior de la ficha de ingreso, con el mismo acceso que la lectura de la COM; queda auditada. */
    public Map<String,Object> version(Actor a,long com,long version){
        var c=access.comForRead(a,com);
        var rows=db.rows("SELECT * FROM intake_revision WHERE communication_id=? AND id=?",com,version);
        if(rows.isEmpty())throw new Problem(404,"No existe esa version de la ficha de ingreso.");
        var v=rows.getFirst();
        db.audit(a.id(),"FICHA_INGRESO_VERSION_CONSULTADA","COM",com,n(c,"origin_scope"),"Version "+n(v,"revision"));
        var out=new LinkedHashMap<String,Object>();
        out.put("form",decode(s(v,"data_json")));out.put("registered_ms",v.get("registered_ms"));out.put("reason",v.get("reason"));
        out.put("author_id",v.get("author_id"));out.put("change_summary",v.get("change_summary"));
        return out;
    }
}
