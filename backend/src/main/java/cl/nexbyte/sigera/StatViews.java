package cl.nexbyte.sigera;

import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Configuraciones guardadas del explorador estadistico (0.8.0, D38 y D43, gate E10; DISENO 3.7). Una vista es una
 * definicion de consulta (la de POST /api/stats/query), validada por StatEngine como la ejecutaria su dueno, sin datos.
 * Solo crean vistas las cuentas con REPORTES; solo el dueno la edita o la borra. Compartir exige GESTION_ESTADISTICA
 * que rija (auditoria VISTA_COMPARTIDA y VISTA_NO_COMPARTIDA); una vista compartida la ve toda cuenta con REPORTES y cada
 * una la ejecuta con su propio alcance (si apunta a un corte que la cuenta no ve, la consulta responde 403).
 */
@Service
public class StatViews {
    static final String SHARED="VISTA_COMPARTIDA",UNSHARED="VISTA_NO_COMPARTIDA";
    static final int MAX_DEFINITION=20_000;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final Db db;
    private final StatEngine engine;

    public StatViews(Db db,StatEngine engine){
        this.db=db;
        this.engine=engine;
    }

    Workflow.Result run(Actor a,String type,long id,Long expected,Input in){
        if(!StatEngine.allowed(a))Problem.denied();
        return switch(type){
            case "STAT_VIEW_SAVE" -> save(a,id,expected,in);
            case "STAT_VIEW_DELETE" -> delete(a,id,expected);
            case "STAT_VIEW_SHARE" -> share(a,id,expected,in);
            default -> throw new Problem(400,"Comando desconocido.");
        };
    }

    @SuppressWarnings("unchecked")
    private Workflow.Result save(Actor a,long id,Long expected,Input in){
        for(String k:in.values().keySet())if(!Set.of("title","definition").contains(k))throw new Problem(400,"Vista: hay campos no reconocidos.");
        String title=in.text("title",200,true);
        if(!(in.values().get("definition") instanceof Map<?,?> definition))throw new Problem(400,"Vista: falta la definición de la consulta.");
        engine.validate(a,(Map<String,Object>)definition);
        String json=StatCuts.json(definition);
        if(json.length()>MAX_DEFINITION)throw new Problem(400,"Vista: la definición es demasiado extensa.");
        long now=Lifecycle.now();
        if(id==0)return Workflow.done("VISTA",db.insert("INSERT INTO stat_view(owner_id,title,definition_json,shared,created_ms,updated_ms,revision) VALUES(?,?,?,FALSE,?,?,0)",a.id(),title,json,now,now));
        var row=own(a,id);
        revision(row,expected);
        require(!(s(row,"title").equals(title)&&s(row,"definition_json").equals(json)),"La vista no tiene cambios.");
        db.update("UPDATE stat_view SET title=?,definition_json=?,updated_ms=?,revision=revision+1 WHERE id=?",title,json,now,id);
        return Workflow.done("VISTA",id);
    }
    private Workflow.Result delete(Actor a,long id,Long expected){
        var row=own(a,id);
        revision(row,expected);
        db.update("DELETE FROM stat_view WHERE id=?",id);
        return Workflow.done("VISTA",id);
    }
    private Workflow.Result share(Actor a,long id,Long expected,Input in){
        if(Roles.ruling(a,Roles.GESTION_ESTADISTICA).isEmpty())Problem.denied();
        if(!(in.values().get("shared") instanceof Boolean shared))throw new Problem(400,"Vista: indica si se comparte.");
        var row=own(a,id);
        revision(row,expected);
        require(b(row,"shared")!=shared,shared?"La vista ya está compartida.":"La vista no está compartida.");
        db.update("UPDATE stat_view SET shared=?,updated_ms=?,revision=revision+1 WHERE id=?",shared,Lifecycle.now(),id);
        db.audit(a.id(),shared?SHARED:UNSHARED,"SISTEMA",0,null,"Vista "+id);
        return Workflow.done("VISTA",id);
    }
    /** Vista propia: 404 si no existe; 403 si es de otra cuenta. */
    private Map<String,Object> own(Actor a,long id){
        var row=db.one("SELECT * FROM stat_view WHERE id=?",id);
        if(n(row,"owner_id")!=a.id())Problem.denied();
        return row;
    }

    /** Vistas propias y compartidas (GET /api/stats/views). */
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> list(Actor a){
        if(!StatEngine.allowed(a))Problem.denied();
        boolean manager=!Roles.ruling(a,Roles.GESTION_ESTADISTICA).isEmpty();
        var out=new ArrayList<Map<String,Object>>();
        for(var r:db.rows("SELECT v.*,s.name AS owner_name FROM stat_view v JOIN staff s ON s.id=v.owner_id WHERE v.owner_id=? OR v.shared=TRUE ORDER BY v.id",a.id())){
            var m=new LinkedHashMap<String,Object>();
            boolean mine=n(r,"owner_id")==a.id();
            m.put("id",n(r,"id"));m.put("title",s(r,"title"));m.put("definition",JSON.readValue(s(r,"definition_json"),Map.class));
            m.put("shared",b(r,"shared"));m.put("mine",mine);m.put("owner",s(r,"owner_name"));m.put("revision",n(r,"revision"));
            m.put("can_edit",mine);m.put("can_share",mine&&manager);
            out.add(m);
        }
        return out;
    }
}
