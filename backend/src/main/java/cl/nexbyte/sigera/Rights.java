package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Derechos, contexto, caracterizacion, rol del Estado y grupos prioritarios de un REQ o de un Caso (0.8.0, D34-D35,
 * gate E06; DISENO 3.2, 4.3 y 4.4). Variables multiples de StatValues.MULTIPLE escritas como eventos ADD/REMOVE/STATUS
 * en stat_event (record_type REQ o CAS); el conjunto vigente y su estado los calcula StatValues (regla unica en Java y
 * SQL, ya probada en E05).
 * <ul>
 * <li>RIGHTS_CLASSIFY: id = REQ o Caso segun data.record; revision = cantidad de eventos del registro (concurrencia
 * optimista sin tocar la revision del registro); data = {record, variables:{VARIABLE:{add:[], remove:[], status}}, reason}.
 * Se agregan solo codigos seleccionables del catalogo VIGENTE (un codigo de un listado retirado se puede quitar, no
 * agregar); un estado especial vale solo con el conjunto vacio; el motivo es obligatorio al quitar un valor o al
 * reemplazar un estado especial (nunca va a la auditoria); un valor igual no escribe.</li>
 * <li>Orden de rechazo: lectura del registro, revision, formato (400), inmutabilidad (409, D35: REQ o Caso cerrado; la
 * unica via es la reapertura formal existente) y facultad (403): REQ = jefatura principal o encargado
 * (Access.reqMain, que incluye a quien edita la ficha); Caso = jefatura del ambito, responsable o integrante
 * (Access.caseWrite). Nunca lectura institucional, por materia, LECTURA, AUDITORIA, REPORTES ni TI.</li>
 * <li>origin RETROSPECTIVA con la reapertura vigente si el registro ya tuvo un cierre (StatValues.reopenedRecord), si no
 * FUNCIONARIO; los asigna el servidor.</li>
 * <li>Auditoria CLASIFICACION_REGISTRADA / CLASIFICACION_CORREGIDA en el REQ o el Caso con solo los nombres de las
 * variables (nunca codigos ni motivo).</li>
 * </ul>
 * La ficha de cada registro entrega su conjunto vigente con rotulos por version, historial y el consolidado del REQ y de
 * los Casos que la cuenta puede leer (un mismo valor en el REQ y en un Caso es una sola fila).
 */
@Service
public class Rights {
    static final String REQ="REQ",CAS="CAS";
    static final int MAX_CODES=200;
    private static final Set<String> FIELDS=Set.of("record","variables","reason"),ITEM=Set.of("add","remove","status");

    private final Db db;
    private final Access access;
    private final StatValues stats;
    public Rights(Db db,Access access,StatValues stats){this.db=db;this.access=access;this.stats=stats;}

    /** Cantidad de eventos de clasificacion del registro: revision de RIGHTS_CLASSIFY. */
    long revision(String type,long record){return db.count("SELECT COUNT(*) FROM stat_event WHERE record_type=? AND record_id=?",type,record);}

    record Target(String type,long id,Map<String,Object> record,Map<String,Object> requirement){
        long com(){return n(requirement,"communication_id");}
        long scope(){return type.equals(CAS)?n(record,"scope_id"):n(requirement,"principal_scope");}
    }
    private Target read(Actor a,String type,long id){
        if(type.equals(REQ)){var r=access.reqForRead(a,id);return new Target(REQ,id,r,r);}
        var c=access.caseForRead(a,id);
        return new Target(CAS,id,c,db.one("SELECT * FROM requirement WHERE id=?",n(c,"requirement_id")));
    }
    /** Inmutabilidad (D35): motivo por el que el registro no admite clasificacion, o null. */
    static String blocked(Target t){
        if(t.type().equals(CAS)&&s(t.record(),"state").equals("CERRADO"))return "El Caso está cerrado: la clasificación requiere reapertura formal.";
        if(!s(t.requirement(),"state").equals("ABIERTO"))return "El REQ está cerrado: la clasificación requiere reapertura formal.";
        return null;
    }
    boolean writable(Actor a,Target t){return t.type().equals(REQ)?access.reqMain(a,t.record()):access.caseWrite(a,t.record());}

    /** RIGHTS_CLASSIFY. */
    Workflow.Result classify(Actor a,long id,Long expected,Input in){
        if(!FIELDS.containsAll(in.values().keySet()))throw new Problem(400,"Envía solo la clasificación.");
        String type=in.choice("record",REQ,CAS);
        var t=read(a,type,id);
        require(expected!=null&&revision(type,id)==expected,"La clasificación cambió. Actualiza la ficha antes de confirmar.");
        if(!(in.values().get("variables") instanceof Map<?,?> raw)||raw.isEmpty())throw new Problem(400,"Revisa la clasificación.");
        var values=new LinkedHashMap<String,Object>();raw.forEach((k,v)->values.put(String.valueOf(k),v));
        for(String key:values.keySet())if(!StatValues.MULTIPLE.contains(key))throw new Problem(400,"Variable de clasificación no reconocida.");
        String reason=in.text("reason",2000,false);
        String blocked=blocked(t);
        require(blocked==null,blocked);
        if(!writable(a,t))Problem.denied();
        Long reopening=null;String origin=StatValues.OFFICIAL;
        var reopened=StatValues.reopenedRecord(db,type,id,n(t.requirement(),"id"));
        if(reopened!=null){origin=StatValues.RETROSPECTIVE;reopening=reopened.orElse(null);}
        long now=Lifecycle.now();
        var registered=new ArrayList<String>();var corrected=new ArrayList<String>();
        for(String variable:StatValues.MULTIPLE){
            if(!values.containsKey(variable))continue;
            if(!(values.get(variable) instanceof Map<?,?> item))throw new Problem(400,"Revisa la clasificación.");
            var fields=new LinkedHashMap<String,Object>();item.forEach((k,v)->fields.put(String.valueOf(k),v));
            if(!ITEM.containsAll(fields.keySet()))throw new Problem(400,"Revisa la clasificación.");
            var vin=new Input(fields);
            var add=new LinkedHashSet<>(codes(vin,"add"));var remove=new LinkedHashSet<>(codes(vin,"remove"));
            String status=vin.text("status",40,false);
            String label=StatCatalogs.CATALOGS.get(variable);
            for(String c:add)if(remove.contains(c))throw new Problem(400,label+": un mismo valor no se agrega y se quita a la vez.");
            if(!status.isEmpty()&&!StatValues.SPECIAL.contains(status))throw new Problem(400,"Estado de clasificación no reconocido.");
            Long version=StatValues.currentVersion(db,variable);
            require(version!=null,label+": no hay un listado vigente; no se puede clasificar.");
            var before=stats.set(type,id,variable,null);String beforeState=stats.setState(type,id,variable,null);
            for(String c:add)if(!before.contains(c))require(StatValues.selectableCurrent(db,variable,c),label+": selecciona una opción del listado vigente.");
            for(String c:remove)require(before.contains(c),label+": el valor que quitas no está registrado. Actualiza la ficha.");
            var after=new TreeSet<>(before);after.removeAll(remove);after.addAll(add);
            if(!status.isEmpty())require(after.isEmpty(),label+": un estado (por ejemplo, Sin información) se registra solo sin valores.");
            var newCodes=add.stream().filter(c->!before.contains(c)).toList();
            boolean statusChange=!status.isEmpty()&&!status.equals(beforeState);
            if(remove.isEmpty()&&newCodes.isEmpty()&&!statusChange)continue;
            boolean correction=!remove.isEmpty()||statusChange&&StatValues.SPECIAL.contains(beforeState);
            if(correction&&reason.isEmpty())throw new Problem(400,label+": indica el motivo de la corrección.");
            String basis=reason.isEmpty()?null:reason;
            for(String c:remove)insert(t,variable,"REMOVE",StatValues.INFORMED,addedVersion(type,id,variable,c),c,origin,a,now,basis,reopening);
            for(String c:newCodes)insert(t,variable,"ADD",StatValues.INFORMED,version,c,origin,a,now,basis,reopening);
            if(statusChange)insert(t,variable,"STATUS",status,version,null,origin,a,now,basis,reopening);
            (correction?corrected:registered).add(variable);
        }
        require(!registered.isEmpty()||!corrected.isEmpty(),"La clasificación no cambió.");
        String retro=reopened!=null?" Retrospectiva.":"";
        if(!registered.isEmpty())db.audit(a.id(),StatValues.REGISTERED,type,id,t.scope(),"Variables: "+String.join(", ",registered)+"."+retro);
        if(!corrected.isEmpty())db.audit(a.id(),StatValues.CORRECTED,type,id,t.scope(),"Variables: "+String.join(", ",corrected)+"."+retro);
        return Lifecycle.done(type,id);
    }
    /** Bloque de derechos del REQ que nace en el mismo comando de resolución. */
    void inline(Actor actor,long req,Object raw){
        if(raw==null)return;
        if(!(raw instanceof Map<?,?> map)||!Set.of("variables","reason").containsAll(map.keySet()))
            throw new Problem(400,"Revisa la clasificación de derechos.");
        var fields=new LinkedHashMap<String,Object>();
        map.forEach((key,value)->fields.put(String.valueOf(key),value));
        fields.put("record",REQ);
        classify(actor,req,revision(REQ,req),new Input(fields));
    }

    /** Estado del expediente para completitud, sin devolver códigos ni referencias de los Casos. */
    boolean resolved(long req,String variable){
        if(!stats.setState(REQ,req,variable,null).equals(StatValues.PENDING))return true;
        return db.rows("SELECT id FROM case_record WHERE requirement_id=?",req).stream()
            .anyMatch(c->!stats.setState(CAS,n(c,"id"),variable,null).equals(StatValues.PENDING));
    }

    static String consolidatedStateSql(String req,String variable){
        String direct=StatValues.setStateSql(REQ,req+".id",variable,null);
        String child=StatValues.setStateSql(CAS,"cc.id",variable,null);
        return "CASE WHEN "+direct+"='INFORMADO' OR EXISTS(SELECT 1 FROM case_record cc WHERE cc.requirement_id="+req+".id"
            +" AND "+child+"='INFORMADO') THEN 'INFORMADO' WHEN "+direct+"<>'PENDIENTE' THEN "+direct
            +" ELSE COALESCE((SELECT "+child+" FROM case_record cc WHERE cc.requirement_id="+req+".id AND "
            +child+"<>'PENDIENTE' ORDER BY cc.id LIMIT 1),'PENDIENTE') END";
    }

    private static List<String> codes(Input in,String key){
        Object raw=in.values().get(key);
        if(raw==null)return List.of();
        if(!(raw instanceof List<?> list)||list.size()>MAX_CODES)throw new Problem(400,"Revisa la clasificación.");
        var out=new ArrayList<String>();
        for(Object v:list){
            if(!(v instanceof String code)||!StatCatalogs.CODE.matcher(code).matches())throw new Problem(400,"Revisa la clasificación.");
            out.add(code);
        }
        return out;
    }
    private void insert(Target t,String variable,String event,String status,Long version,String code,String origin,Actor a,long now,String basis,Long reopening){
        db.insert("INSERT INTO stat_event(communication_id,record_type,record_id,variable,event,status,catalog_version_id,code,origin,author_id,recorded_ms,basis,reopening_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            t.com(),t.type(),t.id(),variable,event,status,version,code,origin,a.id(),now,basis,reopening);
    }
    /** Version con que se agrego el codigo vigente (la del ultimo ADD). */
    private Long addedVersion(String type,long record,String variable,String code){
        var rows=db.rows("SELECT catalog_version_id,code FROM stat_event WHERE record_type=? AND record_id=? AND variable=? AND event='ADD' ORDER BY id DESC",type,record,variable);
        return rows.stream().filter(r->s(r,"code").equals(code)).map(r->nullableId(r,"catalog_version_id")).findFirst().orElse(null);
    }

    // --- Vista para las fichas de REQ y Caso -------------------------------------------------------------------------
    /**
     * Clasificacion del registro (ya autorizado por la ficha): por variable, estado, valores vigentes con su rotulo segun
     * la version con que se registraron, historial (el motivo solo con withBasis) y el consolidado del REQ y de los
     * Casos que la cuenta puede leer.
     */
    Map<String,Object> view(Actor a,String type,Map<String,Object> record,boolean withBasis){
        long id=n(record,"id");
        var requirement=type.equals(REQ)?record:db.one("SELECT * FROM requirement WHERE id=?",n(record,"requirement_id"));
        var t=new Target(type,id,record,requirement);
        String blocked=blocked(t);
        var out=new LinkedHashMap<String,Object>();
        out.put("revision",revision(type,id));out.put("blocked",blocked);out.put("can_classify",blocked==null&&writable(a,t));
        var list=new ArrayList<Map<String,Object>>();
        for(String variable:StatValues.MULTIPLE){
            var row=new LinkedHashMap<String,Object>();
            row.put("variable",variable);row.put("label",StatCatalogs.CATALOGS.get(variable));
            row.put("catalog_current",StatValues.currentVersion(db,variable)!=null);
            row.put("state",stats.setState(type,id,variable,null));
            row.put("values",values(type,id,variable));
            row.put("history",db.rows("SELECT e.id,e.event,e.status,e.code,e.catalog_version_id,e.origin,e.recorded_ms,e.basis,e.reopening_id,u.name AS author FROM stat_event e JOIN staff u ON u.id=e.author_id WHERE e.record_type=? AND e.record_id=? AND e.variable=? ORDER BY e.id DESC",type,id,variable)
                .stream().map(e->{var h=new LinkedHashMap<String,Object>(e);h.put("value_label",StatValues.itemLabel(db,nullableId(e,"catalog_version_id"),e.get("code")==null?null:s(e,"code")));if(!withBasis)h.remove("basis");return (Map<String,Object>)h;}).toList());
            list.add(row);
        }
        out.put("variables",list);
        out.put("consolidated",consolidated(a,requirement));
        return out;
    }
    /** Valores vigentes del registro con el rotulo de la version con que se agregaron. */
    private List<Map<String,Object>> values(String type,long id,String variable){
        var out=new ArrayList<Map<String,Object>>();
        for(String code:stats.set(type,id,variable,null)){
            Long version=addedVersion(type,id,variable,code);
            var v=new LinkedHashMap<String,Object>();v.put("code",code);v.put("label",StatValues.itemLabel(db,version,code));v.put("catalog_version_id",version);
            out.add(v);
        }
        return out;
    }
    /**
     * Consolidado del requerimiento (DISENO 4.4): union del conjunto vigente del REQ y de sus Casos legibles para la
     * cuenta (Access.caseRead). Una fila por valor, con en_req y los Casos que lo registran; un Caso no legible no aporta
     * ni se cuenta.
     */
    Map<String,Object> consolidated(Actor a,Map<String,Object> requirement){
        long req=n(requirement,"id");
        var cases=db.rows("SELECT * FROM case_record WHERE requirement_id=? ORDER BY id",req).stream().filter(c->access.caseRead(a,c)).toList();
        var out=new LinkedHashMap<String,Object>();
        for(String variable:StatValues.MULTIPLE){
            var rows=new TreeMap<String,Map<String,Object>>();
            for(var v:values(REQ,req,variable)){var row=new LinkedHashMap<String,Object>(v);row.put("in_req",true);row.put("cases",new ArrayList<Map<String,Object>>());rows.put(s(v,"code"),row);}
            for(var c:cases)for(var v:values(CAS,n(c,"id"),variable)){
                var row=rows.computeIfAbsent(s(v,"code"),k->{var r=new LinkedHashMap<String,Object>(v);r.put("in_req",false);r.put("cases",new ArrayList<Map<String,Object>>());return r;});
                @SuppressWarnings("unchecked") var list=(List<Map<String,Object>>)row.get("cases");
                list.add(Map.of("id",n(c,"id"),"folio",s(c,"folio")));
            }
            out.put(variable,List.copyOf(rows.values()));
        }
        return out;
    }
}
