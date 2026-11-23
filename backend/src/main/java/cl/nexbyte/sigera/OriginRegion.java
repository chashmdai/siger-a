package cl.nexbyte.sigera;

import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.sigera.Db.*;

/**
 * Region de origen de la persona (0.7.0, D21, gate T02): regla unica, en Java y en SQL portable.
 *
 * Fuentes inmutables: en una COM del Formulario, la proyeccion (external_field CAMPO nna-region y, si falta o no es un
 * codigo valido, adult-region), que no cambia despues de crear la COM; en una atencion directa, las versiones de la ficha
 * (attention_revision.region_code, V16: el codigo de data_json.region de cada version si es valido). Una COM manual no
 * tiene region. Valida = codigo del catalogo del Formulario (AdultIdentity.REGIONS); un texto libre no es region.
 *
 * Region registrada: la del Formulario o, en una atencion directa, la de la primera version con region (completar una
 * ficha sin region no es una correccion). Region vigente: la primera valida de (ficha vigente, nna-region, adult-region),
 * la misma regla de Access.suggestedRegion (propuesta de destino, D20). Corregida: hay region registrada y la vigente es
 * distinta (tambien si se quito). Los reportes filtran y agrupan por la registrada (estable ante correcciones).
 */
@Service
public class OriginRegion {
    private static final Logger LOG=LoggerFactory.getLogger(OriginRegion.class);
    static final String MARKER="REGION_FICHA_V16";
    static final int BATCH=500;
    static final List<String> FORM_KEYS=List.of("nna-region","adult-region");
    /** Codigos del catalogo como literales SQL (solo digitos: se verifica al cargar). */
    static final String CODES;
    static{
        if(!AdultIdentity.REGIONS.keySet().stream().allMatch(c->c.matches("[0-9]{2}")))throw new IllegalStateException("Codigos de region inesperados.");
        CODES=AdultIdentity.REGIONS.keySet().stream().sorted().map(c->"'"+c+"'").collect(Collectors.joining(","));
    }

    private final Db db;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public OriginRegion(Db db,PlatformTransactionManager manager){this.db=db;this.tx=new TransactionTemplate(manager);}

    // --- Regla en Java ---------------------------------------------------------------------------
    /** Codigo de region si es valido; si no, nulo (sin region declarada). */
    static String of(Object region){return region instanceof String r&&AdultIdentity.validRegion(r)?r:null;}
    /** Region de una version de la ficha (sus propios datos). */
    static String ofForm(Map<String,Object> form){return form==null?null:of(form.get("region"));}
    /**
     * Region vigente (y propuesta de destino): la primera valida de la ficha vigente, nna-region y adult-region; fields
     * son las filas CAMPO del Formulario ordenadas por id (la primera valida de cada clave).
     */
    static String current(String formRegion,List<Map<String,Object>> fields){
        if(of(formRegion)!=null)return formRegion;
        for(String key:FORM_KEYS)for(var f:fields)
            if(key.equals(s(f,"field_key"))&&of(f.get("value_text"))!=null)return s(f,"value_text");
        return null;
    }
    /** Filas CAMPO de region del Formulario de una COM, por id (vacio si la COM no es del Formulario). */
    static final String FORM_FIELDS="SELECT field_key,value_text FROM external_field WHERE communication_id=? AND field_group='CAMPO' AND field_key IN ('nna-region','adult-region') ORDER BY id";
    static boolean corrected(Object registered,Object current){return registered!=null&&!Objects.equals(registered,current);}
    /** Region registrada, vigente y si fue corregida, de un REQ (para la ficha). */
    Map<String,Object> ofRequirement(long req){
        var row=db.one("SELECT "+registeredSql("c","r")+" AS registered,"+currentSql("c","r")+" AS current_region FROM requirement r JOIN communication c ON c.id=r.communication_id WHERE r.id=?",req);
        var out=new LinkedHashMap<String,Object>();
        Object reg=row.get("registered")==null?null:s(row,"registered"),cur=row.get("current_region")==null?null:s(row,"current_region");
        out.put("registered",reg);out.put("current",cur);out.put("corrected",corrected(reg,cur));
        return out;
    }

    // --- Regla en SQL (H2 MODE=MySQL y MySQL 8.4) ------------------------------------------------
    /** Primera fila CAMPO valida de la clave (por id) de la COM del alias. */
    private static String formField(String com,String key){
        return "(SELECT f.value_text FROM external_field f WHERE f.id=(SELECT MIN(g.id) FROM external_field g WHERE g.communication_id="+com
            +".id AND g.field_group='CAMPO' AND g.field_key='"+key+"' AND g.value_text IN ("+CODES+")))";
    }
    static String formularioSql(String com){return "COALESCE("+formField(com,"nna-region")+","+formField(com,"adult-region")+")";}
    /** Region registrada: primera version de la ficha con region o, sin ella, el Formulario (alias de communication y requirement). */
    static String registeredSql(String com,String req){
        return "COALESCE((SELECT v.region_code FROM attention_revision v WHERE v.id=(SELECT MIN(w.id) FROM attention_revision w WHERE w.requirement_id="+req
            +".id AND w.region_code IS NOT NULL)),"+formularioSql(com)+")";
    }
    /** Region vigente: la de la version actual de la ficha y luego el Formulario (el mismo orden de current()). */
    static String currentSql(String com,String req){
        return "COALESCE((SELECT v.region_code FROM attention_revision v WHERE v.id=(SELECT MAX(w.id) FROM attention_revision w WHERE w.requirement_id="+req
            +".id)),"+formularioSql(com)+")";
    }

    // --- Carga inicial (V16) ---------------------------------------------------------------------
    /** Calcula region_code de todas las versiones de la ficha, por lotes con el lock global; una sola vez (marcador). */
    void rebuild(){
        if(Boolean.TRUE.equals(tx.execute(st->db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",MARKER)>0)))return;
        long last=0,versions=0;
        while(true){
            final long from=last;
            List<Long> ids=tx.execute(st->{
                db.lock();
                var batch=db.rows("SELECT id,data_json FROM attention_revision WHERE id>? ORDER BY id LIMIT "+BATCH,from);
                for(var v:batch)db.update("UPDATE attention_revision SET region_code=? WHERE id=?",ofForm(decode(s(v,"data_json"))),n(v,"id"));
                return batch.stream().map(v->n(v,"id")).toList();
            });
            if(ids==null||ids.isEmpty())break;
            last=ids.getLast();versions+=ids.size();
        }
        long with=Objects.requireNonNull(tx.execute(st->{
            db.lock();
            if(db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",MARKER)==0)
                db.update("INSERT INTO application_marker(code,applied_ms) VALUES(?,?)",MARKER,System.currentTimeMillis());
            return db.count("SELECT COUNT(*) FROM attention_revision WHERE region_code IS NOT NULL");
        }));
        if(versions>0)LOG.info("Region de origen: {} versiones de ficha, {} con region.",versions,with);
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> decode(String value){
        try{return (Map<String,Object>)json.readValue(value,Map.class);}catch(RuntimeException e){return Map.of();}
    }
}
