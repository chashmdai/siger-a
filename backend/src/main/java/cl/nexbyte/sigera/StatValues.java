package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Clasificacion estadistica (0.8.0, D27-D31 y D35, gate E05; DISENO 3.2, 4.2 y 4.3, V19 stat_event). Regla unica, en
 * Java y en SQL portable, del valor vigente, del valor a una fecha, del estado y del conjunto vigente; y la unica
 * funcion de escritura (STAT_CLASSIFY y el bloque opcional "stat" de los comandos de ingreso y distribucion).
 * - Estados: PENDIENTE (sin evento), INFORMADO o un valor especial (SIN_INFORMACION, NO_DECLARADO, NO_APLICA).
 * - Valor unico (TIPO_SOLICITUD, TIPO_REQUIRENTE, VIA_INGRESO; registro = la COM): ultimo SET o STATUS.
 * - Via de ingreso derivada del canal (D31): sin evento propio, la via es la del canal (CHANNEL_VIA, codigos de
 *   deploy/config-ejemplo/catalogos/via-ingreso-v1.json) si es un item seleccionable del catalogo VIA_INGRESO vigente
 *   (o, a una fecha t, del vigente en t y desde el registro de la COM): estado INFORMADO, origen SISTEMA. Se calcula sin
 *   escribir, para todas las COM (tambien las del Formulario, cuya recepcion no cambia, y las anteriores a 0.8.0); una
 *   correccion es un SET con motivo y el canal original no cambia. Diferencia registrada con DISENO 3.2.
 * - Conjunto (DERECHO, CONTEXTO, CARACTERIZACION; comandos en E06): ADD no seguidos de un REMOVE del mismo codigo;
 *   un STATUS vale solo con el conjunto vacio.
 * - A una fecha t: eventos con recorded_ms <= t; sin evidencia anterior, PENDIENTE (no se reconstruye).
 * Escritura: codigos y estados solo con un catalogo VIGENTE de la variable; el motivo es obligatorio al corregir un
 * valor INFORMADO y nunca va a la auditoria (solo nombres de variables). origin y reopening_id los asigna el servidor:
 * RETROSPECTIVA con la ultima reapertura aceptada si la COM tiene un REQ abierto que ya tuvo un cierre (DI-07: el
 * mecanismo de reapertura no cambia); FUNCIONARIO en los demas casos. Inmutabilidad (D35): COM descartada o vinculada
 * como antecedente, o REQ cerrado: 409 sin excepcion de funcion.
 */
@Service
public class StatValues {
    static final String TIPO_SOLICITUD="TIPO_SOLICITUD",TIPO_REQUIRENTE="TIPO_REQUIRENTE",VIA_INGRESO="VIA_INGRESO";
    static final List<String> SINGLE=List.of(TIPO_SOLICITUD,TIPO_REQUIRENTE,VIA_INGRESO);
    /** Variables multiples por REQ o Caso (E06; ROL_ESTADO y GRUPO_PRIORITARIO por decision del 2026-11-24). */
    static final List<String> MULTIPLE=List.of("DERECHO","CONTEXTO","CARACTERIZACION","ROL_ESTADO","GRUPO_PRIORITARIO");
    static final String COM="COM",PENDING="PENDIENTE",INFORMED="INFORMADO";
    static final List<String> SPECIAL=List.of("SIN_INFORMACION","NO_DECLARADO","NO_APLICA");
    static final String OFFICIAL="FUNCIONARIO",SYSTEM="SISTEMA",RETROSPECTIVE="RETROSPECTIVA";
    static final String REGISTERED="CLASIFICACION_REGISTRADA",CORRECTED="CLASIFICACION_CORREGIDA";
    /** Via derivada de cada canal (D31; codigos de via-ingreso-v1.json, que E06 conserva al agregar agrupaciones). */
    static final Map<String,String> CHANNEL_VIA=channelVia();
    private static Map<String,String> channelVia(){
        var m=new LinkedHashMap<String,String>();
        m.put(StatProposals.ADULT,"FORMULARIO_PUBLICO_ADULTO");m.put(StatProposals.NNA,"FORMULARIO_PUBLICO_NNA");
        for(String channel:Lifecycle.CHANNELS)m.put(channel,channel);
        return Collections.unmodifiableMap(m);
    }

    record Value(String variable,String state,String code,Long version,String origin,Long event){}

    private final Db db;
    private final Access access;
    private final StatProposals proposals;
    public StatValues(Db db,Access access,StatProposals proposals){this.db=db;this.access=access;this.proposals=proposals;}

    // --- Lectura (Java) ------------------------------------------------------------------------------------------
    /** Valor vigente de una variable de valor unico de la COM. */
    Value current(long com,String variable){return value(com,variable,null);}
    /** Valor de la variable en el instante t (eventos registrados hasta t). */
    Value at(long com,String variable,long t){return value(com,variable,t);}
    private Value value(long com,String variable,Long at){
        require(SINGLE.contains(variable),"Variable de clasificacion no reconocida.");
        var events=db.rows("SELECT id,status,code,catalog_version_id,origin FROM stat_event WHERE communication_id=? AND record_type='COM' AND record_id=? AND variable=? AND event IN ('SET','STATUS')"
            +(at==null?"":" AND recorded_ms<="+at)+" ORDER BY id DESC",com,com,variable);
        if(!events.isEmpty()){var e=events.getFirst();return new Value(variable,s(e,"status"),e.get("code")==null?null:s(e,"code"),nullableId(e,"catalog_version_id"),s(e,"origin"),n(e,"id"));}
        if(variable.equals(VIA_INGRESO)){
            var c=db.one("SELECT channel,created_ms FROM communication WHERE id=?",com);
            String code=CHANNEL_VIA.get(s(c,"channel"));
            if(code!=null&&(at==null||n(c,"created_ms")<=at)){
                Long version=null;
                for(var r:db.rows("SELECT v.id,i.code FROM stat_catalog_version v JOIN stat_catalog_item i ON i.version_id=v.id WHERE v.catalog=? AND i.selectable=TRUE AND "+active("v",at)+" ORDER BY v.id",VIA_INGRESO))
                    if(s(r,"code").equals(code))version=n(r,"id");
                if(version!=null)return new Value(variable,INFORMED,code,version,SYSTEM,null);
            }
        }
        return new Value(variable,PENDING,null,null,null,null);
    }
    /** Conjunto vigente de una variable multiple del registro (o a la fecha t). */
    Set<String> set(String type,long record,String variable,Long at){
        require(MULTIPLE.contains(variable),"Variable de clasificacion no reconocida.");
        var out=new TreeSet<String>();
        for(var e:db.rows("SELECT event,code FROM stat_event WHERE record_type=? AND record_id=? AND variable=? AND event IN ('ADD','REMOVE')"+(at==null?"":" AND recorded_ms<="+at)+" ORDER BY id",type,record,variable)){
            if(s(e,"event").equals("ADD"))out.add(s(e,"code"));else out.remove(s(e,"code"));
        }
        return out;
    }
    /** Estado de una variable multiple: INFORMADO con valores; si no, el ultimo STATUS o PENDIENTE. */
    String setState(String type,long record,String variable,Long at){
        if(!set(type,record,variable,at).isEmpty())return INFORMED;
        var last=db.rows("SELECT status FROM stat_event WHERE record_type=? AND record_id=? AND variable=? AND event='STATUS'"+(at==null?"":" AND recorded_ms<="+at)+" ORDER BY id DESC",type,record,variable);
        return last.isEmpty()?PENDING:s(last.getFirst(),"status");
    }
    /** Cantidad de eventos de la COM: revision de STAT_CLASSIFY (concurrencia optimista sin tocar la revision de la COM). */
    long revision(long com){return db.count("SELECT COUNT(*) FROM stat_event WHERE communication_id=?",com);}
    /** Codigo seleccionable del catalogo VIGENTE (igualdad exacta en Java). */
    static boolean selectableCurrent(Db db,String catalog,String code){
        if(code==null)return false;
        return db.rows("SELECT i.code FROM stat_catalog_item i JOIN stat_catalog_version v ON v.id=i.version_id WHERE v.`current_catalog`=? AND i.selectable=TRUE",catalog)
            .stream().anyMatch(r->s(r,"code").equals(code));
    }
    static Long currentVersion(Db db,String catalog){
        var rows=db.rows("SELECT id FROM stat_catalog_version WHERE `current_catalog`=?",catalog);
        return rows.isEmpty()?null:n(rows.getFirst(),"id");
    }
    private Long currentVersion(String catalog){
        var rows=db.rows("SELECT id FROM stat_catalog_version WHERE `current_catalog`=?",catalog);
        return rows.isEmpty()?null:n(rows.getFirst(),"id");
    }
    /** Version del catalogo activa en t (activada hasta t y no retirada antes o en t), o la VIGENTE si t es null. */
    private static String active(String v,Long at){
        return at==null?v+".`current_catalog`="+v+".catalog":v+".activated_ms<="+at+" AND ("+v+".retired_ms IS NULL OR "+v+".retired_ms>"+at+")";
    }

    // --- Lectura (SQL portable; misma regla, probada contra la de Java en H2 y MySQL) ----------------------------
    private static String single(String variable){
        if(!SINGLE.contains(variable))throw new IllegalArgumentException(variable);
        return variable;
    }
    private static String last(String c,String variable,Long at){
        return "(SELECT MAX(e.id) FROM stat_event e WHERE e.communication_id="+c+".id AND e.record_type='COM' AND e.record_id="+c+".id AND e.variable='"+single(variable)
            +"' AND e.event IN ('SET','STATUS')"+(at==null?"":" AND e.recorded_ms<="+at)+")";
    }
    private static String field(String c,String variable,Long at,String column){
        return "(SELECT e2."+column+" FROM stat_event e2 WHERE e2.id="+last(c,variable,at)+")";
    }
    private static String derivedCode(String c){
        var sql=new StringBuilder("CASE "+c+".channel");
        CHANNEL_VIA.forEach((channel,via)->sql.append(" WHEN '").append(channel).append("' THEN '").append(via).append("'"));
        return sql.append(" END").toString();
    }
    /** Version del catalogo VIA_INGRESO (vigente, o activa en t) que tiene la via derivada del canal; null si no aplica. */
    private static String derivedVersion(String c,String variable,Long at){
        if(!variable.equals(VIA_INGRESO))return "NULL";
        return "(SELECT MAX(dv.id) FROM stat_catalog_version dv JOIN stat_catalog_item di ON di.version_id=dv.id WHERE dv.catalog='"+VIA_INGRESO+"' AND di.selectable=TRUE AND "
            +active("dv",at)+" AND di.code=("+derivedCode(c)+")"+(at==null?"":" AND "+c+".created_ms<="+at)+")";
    }
    /** Estado de la variable de valor unico de la COM con alias c (vigente si at es null). */
    static String stateSql(String c,String variable,Long at){
        return "COALESCE("+field(c,variable,at,"status")+",CASE WHEN "+derivedVersion(c,variable,at)+" IS NOT NULL THEN '"+INFORMED+"' ELSE '"+PENDING+"' END)";
    }
    static String codeSql(String c,String variable,Long at){
        return "CASE WHEN "+last(c,variable,at)+" IS NULL THEN (CASE WHEN "+derivedVersion(c,variable,at)+" IS NOT NULL THEN "+derivedCode(c)+" END) ELSE "+field(c,variable,at,"code")+" END";
    }
    static String versionSql(String c,String variable,Long at){
        return "CASE WHEN "+last(c,variable,at)+" IS NULL THEN "+derivedVersion(c,variable,at)+" ELSE "+field(c,variable,at,"catalog_version_id")+" END";
    }
    static String originSql(String c,String variable,Long at){
        return "CASE WHEN "+last(c,variable,at)+" IS NULL THEN (CASE WHEN "+derivedVersion(c,variable,at)+" IS NOT NULL THEN '"+SYSTEM+"' END) ELSE "+field(c,variable,at,"origin")+" END";
    }
    private static String multiple(String variable){
        if(!MULTIPLE.contains(variable))throw new IllegalArgumentException(variable);
        return variable;
    }
    /** Codigos del conjunto vigente de la variable multiple para el registro (type constante, record = expresion SQL). */
    static String setSql(String type,String record,String variable,Long at){
        String t=at==null?"":" AND a.recorded_ms<="+at,tr=at==null?"":" AND r.recorded_ms<="+at;
        return "SELECT DISTINCT a.code FROM stat_event a WHERE a.record_type='"+type+"' AND a.record_id="+record+" AND a.variable='"+multiple(variable)+"' AND a.event='ADD'"+t
            +" AND NOT EXISTS(SELECT 1 FROM stat_event r WHERE r.record_type=a.record_type AND r.record_id=a.record_id AND r.variable=a.variable AND r.event='REMOVE' AND r.code=a.code AND r.id>a.id"+tr+")";
    }
    static String setStateSql(String type,String record,String variable,Long at){
        String t=at==null?"":" AND e.recorded_ms<="+at;
        return "CASE WHEN EXISTS("+setSql(type,record,variable,at)+") THEN '"+INFORMED+"' ELSE COALESCE((SELECT e2.status FROM stat_event e2 WHERE e2.id=(SELECT MAX(e.id) FROM stat_event e WHERE e.record_type='"
            +type+"' AND e.record_id="+record+" AND e.variable='"+multiple(variable)+"' AND e.event='STATUS'"+t+")),'"+PENDING+"') END";
    }

    // --- Escritura -------------------------------------------------------------------------------------------------
    /**
     * Inmutabilidad (D35): motivo por el que la COM no admite clasificacion, o null. Descartada (INVALIDA o
     * DUPLICADA: queda como se registro al descartarla), vinculada como antecedente (VINCULADA; un vinculo devuelto la
     * deja NUEVA) o con un REQ cerrado (solo con reapertura formal).
     */
    String blocked(Map<String,Object> com){
        String state=s(com,"state");
        if(state.equals("INVALIDA")||state.equals("DUPLICADA"))return "La COM fue descartada: su clasificación queda como se registró al descartarla.";
        if(state.equals("VINCULADA"))return "La COM está vinculada como antecedente de otro REQ: no se clasifica como un requerimiento nuevo.";
        if(db.count("SELECT COUNT(*) FROM requirement WHERE communication_id=? AND state<>'ABIERTO'",n(com,"id"))>0)return "El REQ está cerrado: la clasificación requiere reapertura formal.";
        return null;
    }

    /** STAT_CLASSIFY: id = COM y revision = cantidad de eventos de la COM; data = {stat:{VARIABLE:{code}|{status}, reason}}. */
    Workflow.Result classify(Actor a,long id,Long expected,Input in){
        var com=access.comForRead(a,id);
        require(expected!=null&&revision(id)==expected,"La clasificación cambió. Actualiza la ficha antes de confirmar.");
        // El cliente no fija origen, reapertura, autor ni estado: solo el bloque de clasificacion.
        if(!in.values().keySet().equals(Set.of("stat")))throw new Problem(400,"Envía solo la clasificación.");
        String blocked=blocked(com);
        require(blocked==null,blocked);
        if(!access.statWritable(a,com))Problem.denied();
        require(apply(a,com,in.values().get("stat"))>0,"La clasificación no cambió.");
        return Lifecycle.done("COM",id);
    }

    /**
     * Bloque opcional "stat" de COM_CREATE, COM_IMMEDIATE, ATTENTION_CREATE, COM_ROUTE, COM_ASSUME, SOL_ASSUME y
     * SOL_DISCARD: misma regla y misma escritura, con la facultad que ya valido el propio comando. Se aplica antes de
     * descartar o cerrar en el mismo acto.
     */
    void inline(Actor a,long com,Object block){
        if(block==null)return;
        var c=db.one("SELECT * FROM communication WHERE id=?",com);
        String blocked=blocked(c);
        require(blocked==null,blocked);
        apply(a,c,block);
    }

    /** Aplica el bloque y devuelve la cantidad de variables que cambiaron (una sin cambio no escribe). */
    private int apply(Actor a,Map<String,Object> com,Object block){
        if(!(block instanceof Map<?,?> raw))throw new Problem(400,"Revisa la clasificación.");
        var values=new LinkedHashMap<String,Object>();raw.forEach((k,v)->values.put(String.valueOf(k),v));
        for(String key:values.keySet())if(!key.equals("reason")&&!SINGLE.contains(key))throw new Problem(400,"Variable de clasificación no reconocida.");
        String reason=new Input(values).text("reason",2000,false);
        long id=n(com,"id"),now=Lifecycle.now();
        var registered=new ArrayList<String>();var corrected=new ArrayList<String>();
        Long reopening=null;String origin=OFFICIAL;
        var reopened=reopened(id);
        if(reopened!=null){origin=RETROSPECTIVE;reopening=reopened.orElse(null);}
        for(String variable:SINGLE){
            if(!values.containsKey(variable))continue;
            if(!(values.get(variable) instanceof Map<?,?> item))throw new Problem(400,"Revisa la clasificación.");
            var fields=new LinkedHashMap<String,Object>();item.forEach((k,v)->fields.put(String.valueOf(k),v));
            if(!Set.of("code","status").containsAll(fields.keySet()))throw new Problem(400,"Revisa la clasificación.");
            var in=new Input(fields);String code=in.text("code",40,false),status=in.text("status",40,false);
            if(code.isEmpty()==status.isEmpty())throw new Problem(400,"Indica un valor o un estado para cada variable.");
            String label=StatCatalogs.CATALOGS.get(variable);
            Long version=currentVersion(variable);
            require(version!=null,label+": no hay un listado vigente; no se puede clasificar.");
            if(!code.isEmpty())require(selectableCurrent(db,variable,code),label+": selecciona una opción del listado vigente.");
            else if(!SPECIAL.contains(status))throw new Problem(400,"Estado de clasificación no reconocido.");
            var before=current(id,variable);
            boolean same=code.isEmpty()?status.equals(before.state()):INFORMED.equals(before.state())&&code.equals(before.code());
            if(same)continue;
            boolean correction=INFORMED.equals(before.state());
            if(correction&&reason.isEmpty())throw new Problem(400,label+": indica el motivo de la corrección.");
            db.insert("INSERT INTO stat_event(communication_id,record_type,record_id,variable,event,status,catalog_version_id,code,origin,author_id,recorded_ms,basis,reopening_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,COM,id,variable,code.isEmpty()?"STATUS":"SET",code.isEmpty()?status:INFORMED,version,code.isEmpty()?null:code,origin,a.id(),now,reason.isEmpty()?null:reason,reopening);
            (correction?corrected:registered).add(variable);
        }
        // Auditoria: solo variables y registro; codigos y motivo quedan en stat_event (lectura protegida).
        long scope=n(com,"origin_scope");
        if(!registered.isEmpty())db.audit(a.id(),REGISTERED,"COM",id,scope,"Variables: "+String.join(", ",registered)+"."+(reopened!=null?" Retrospectiva.":""));
        if(!corrected.isEmpty())db.audit(a.id(),CORRECTED,"COM",id,scope,"Variables: "+String.join(", ",corrected)+"."+(reopened!=null?" Retrospectiva.":""));
        return registered.size()+corrected.size();
    }
    /**
     * COM con un REQ abierto que ya tuvo un cierre (closure_record del REQ): devuelve la reapertura vigente (la ultima
     * ACEPTADA de ese REQ, vacia si no hay) o null si no corresponde RETROSPECTIVA.
     */
    private Optional<Long> reopened(long com){
        for(var r:db.rows("SELECT id FROM requirement WHERE communication_id=? AND state='ABIERTO' ORDER BY id",com)){
            var p=reopenedRecord(db,"REQ",n(r,"id"),n(r,"id"));
            if(p!=null)return p;
        }
        return null;
    }
    /**
     * Regla unica de RETROSPECTIVA (DISENO 3.2, DI-07) para un registro abierto: un REQ que ya tuvo un cierre propio
     * (closure_record del REQ), o un Caso que ya tuvo un cierre propio o cuyo REQ lo tuvo (E06: tambien el Caso
     * reabierto). Devuelve la reapertura vigente (la ultima ACEPTADA del Caso, o del REQ; vacia si no hay) o null si el
     * registro nunca se cerro.
     */
    static Optional<Long> reopenedRecord(Db db,String type,long record,long req){
        if(type.equals("CAS")&&db.count("SELECT COUNT(*) FROM closure_record WHERE case_id=?",record)>0){
            var p=db.rows("SELECT MAX(id) AS id FROM reopening WHERE case_id=? AND state='ACEPTADA'",record);
            return Optional.ofNullable(p.isEmpty()?null:nullableId(p.getFirst(),"id"));
        }
        if(db.count("SELECT COUNT(*) FROM closure_record WHERE requirement_id=? AND case_id IS NULL",req)==0)return null;
        var p=db.rows("SELECT MAX(id) AS id FROM reopening WHERE requirement_id=? AND state='ACEPTADA'",req);
        return Optional.ofNullable(p.isEmpty()?null:nullableId(p.getFirst(),"id"));
    }

    // --- Vista para las fichas de COM y REQ --------------------------------------------------------------------------
    /**
     * Caracterizacion de la COM para una ficha ya autorizada: por variable, estado, valor con su rotulo y version,
     * origen, propuesta aparte (nunca como estado) e historial. withBasis: el motivo (texto libre del funcionario) solo
     * para quien lee la COM o el registro del REQ.
     */
    Map<String,Object> view(Actor a,Map<String,Object> com,boolean withBasis){
        long id=n(com,"id");var out=new LinkedHashMap<String,Object>();
        String blocked=blocked(com);
        out.put("revision",revision(id));out.put("blocked",blocked);out.put("can_classify",blocked==null&&access.statWritable(a,com));
        var proposed=proposals.of(id);var list=new ArrayList<Map<String,Object>>();
        for(String variable:SINGLE){
            var v=current(id,variable);var row=new LinkedHashMap<String,Object>();
            row.put("variable",variable);row.put("label",StatCatalogs.CATALOGS.get(variable));
            row.put("state",v.state());row.put("code",v.code());row.put("value_label",itemLabel(v.version(),v.code()));
            row.put("catalog_version_id",v.version());row.put("origin",v.origin());
            row.put("catalog_current",currentVersion(variable)!=null);
            String p=proposed.get(variable);
            row.put("proposal",p==null?null:Map.of("code",p,"label",itemLabel(currentVersion(variable),p),"rule",StatProposals.RULE));
            row.put("history",db.rows("SELECT e.id,e.event,e.status,e.code,e.catalog_version_id,e.origin,e.recorded_ms,e.basis,e.reopening_id,u.name AS author FROM stat_event e JOIN staff u ON u.id=e.author_id WHERE e.communication_id=? AND e.record_type='COM' AND e.variable=? ORDER BY e.id DESC",id,variable)
                .stream().map(e->{var h=new LinkedHashMap<String,Object>(e);h.put("value_label",itemLabel(nullableId(e,"catalog_version_id"),e.get("code")==null?null:s(e,"code")));if(!withBasis)h.remove("basis");return (Map<String,Object>)h;}).toList());
            list.add(row);
        }
        out.put("variables",list);
        return out;
    }
    /**
     * Rotulos de los codigos de RELACION y TIPO_INSTITUCION de una ficha (relationship_code/relationship_version y
     * type_code/type_version, a cualquier profundidad) segun la version con que se registraron: "version:codigo" ->
     * rotulo (E06; la interfaz lo usa para un codigo de un listado retirado). Solo para fichas ya autorizadas.
     */
    static Map<String,String> codeLabels(Db db,Object data){
        var pairs=new TreeSet<String>();collect(data,pairs);
        var out=new TreeMap<String,String>();
        for(String pair:pairs){
            int i=pair.indexOf(':');
            String label=itemLabel(db,Long.valueOf(pair.substring(0,i)),pair.substring(i+1));
            if(label!=null)out.put(pair,label);
        }
        return out;
    }
    private static void collect(Object value,Set<String> out){
        if(value instanceof Map<?,?> m){
            for(String key:List.of("relationship","type")){
                Object code=m.get(key+"_code"),version=m.get(key+"_version");
                if(code instanceof String c&&!c.isEmpty()&&version!=null&&version.toString().matches("[0-9]{1,18}"))out.add(version+":"+c);
            }
            m.values().forEach(v->collect(v,out));
        }else if(value instanceof Collection<?> c)c.forEach(v->collect(v,out));
    }
    /** Rotulo de un codigo segun la version del catalogo con que se registro (null si no hay version o codigo). */
    static String itemLabel(Db db,Long version,String code){
        if(version==null||code==null)return null;
        return db.rows("SELECT code,label FROM stat_catalog_item WHERE version_id=?",version).stream().filter(r->s(r,"code").equals(code)).map(r->s(r,"label")).findFirst().orElse(null);
    }
    private String itemLabel(Long version,String code){
        if(version==null||code==null)return null;
        return db.rows("SELECT code,label FROM stat_catalog_item WHERE version_id=?",version).stream().filter(r->s(r,"code").equals(code)).map(r->s(r,"label")).findFirst().orElse(null);
    }
}
