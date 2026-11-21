package cl.nexbyte.sigera;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.sigera.Db.*;

/**
 * Indice de personas (0.5.0, D5, V12): coincidencias sobre los datos declarados de cada COM, para el buscador por
 * RUN, nombre o documento. Es un dato derivado y reconstruible, no un registro maestro: no fusiona identidades ni
 * autentica personas. refresh(com) se ejecuta en la misma transaccion del comando que cambia los datos (bajo el lock
 * global de Workflow) y recalcula todas las referencias de esa COM con los datos vigentes.
 *
 * Una sola fuente principal por COM, para no duplicar coincidencias: el Formulario (FORMULARIO, todas sus personas;
 * la COM proyectada repite al presentador), la ficha vigente de primera atencion (FICHA; la COM es la copia de su
 * primera version y las versiones antiguas no se indexan) o, en una COM manual, la COM (COM) y sus personas
 * relacionadas (COM_PERSONA). Una persona sin nombre, RUN ni documento no genera referencia.
 *
 * Solo se indexan quien presenta o requiere, NNA, adulto relacionado, acompanante y curador/a (D5, gate G07b):
 * nunca la persona senalada del Formulario ni "otra persona" de la COM manual o de la ficha. Es una lista de roles
 * permitidos: un rol desconocido tampoco se indexa.
 */
@Service
public class PersonIndex {
    private static final Logger LOG=LoggerFactory.getLogger(PersonIndex.class);
    /** G07 uso PERSON_INDEX_V12 e indexaba todos los roles; el marcador nuevo obliga a reconstruir sin los excluidos. */
    static final String MARKER="PERSON_INDEX_V12B";
    /** Roles indexados (D5): COM y ficha (REQUIRENTE, CURADOR, NNA, ACOMPANANTE) y Formulario (PRESENTADOR, AFECTADO_NNA, ADULTO_RELACIONADO). */
    static final Set<String> ROLES=Set.of("REQUIRENTE","CURADOR","NNA","ACOMPANANTE","PRESENTADOR","AFECTADO_NNA","ADULTO_RELACIONADO");
    static final int BATCH=500;
    private final Db db;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public PersonIndex(Db db,PlatformTransactionManager manager){this.db=db;this.tx=new TransactionTemplate(manager);}

    /** Identificacion normalizada: RUN (numero y DV) o, si no es un RUN reconocido, la clave de otro documento. */
    record Identity(String runNumber,String runDv,String documentKey){
        static final Identity NONE=new Identity(null,null,null);
        boolean empty(){return runNumber==null&&documentKey==null;}
    }
    record Ref(String source,String role,String display,String nameKey,Identity identity,boolean authenticated,Long requirement){}

    // --- Normalizacion ----------------------------------------------------------------------------
    private static String fold(String value){
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKD).replaceAll("\\p{M}+","");
    }
    /** Minusculas, sin tildes ni puntuacion, espacios simples; null si no queda texto. */
    static String nameKey(String value){
        String key=fold(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").strip();
        return key.isEmpty()?null:cut(key,500);
    }
    /** RUN con guion y DV en texto libre: 12.345.678-9, 12345678-9, RUN 12.345.678-k, 9.876.543 - K. */
    private static final Pattern RUN=Pattern.compile("(?<![0-9A-Z.])([0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]{5,9})\\s*-\\s*([0-9K])(?![0-9A-Z])");
    /** Etiquetas que preceden al numero de un documento en el texto libre o en la COM derivada de la ficha 1.1. */
    private static final Set<String> LABELS=Set.of("RUN","RUT","PASAPORTE","PASSPORT","DNI","CEDULA","CI","DOCUMENTO","DOC","DE","DEL",
        "IDENTIDAD","IDENTIFICACION","EXTRANJERO","EXTRANJERA","OTRO","NUMERO","NRO","NO","N");
    /** Numero de RUN sin puntos ni ceros iniciales y DV en mayuscula; el DV no se valida (dato declarado). */
    static Identity run(String number,String dv){
        String digits=(number==null?"":number).replaceAll("[^0-9]","").replaceFirst("^0+","");
        if(!digits.matches("[1-9][0-9]{0,8}"))return Identity.NONE;
        String check=dv==null?"":dv.strip().toUpperCase(Locale.ROOT);
        return new Identity(digits,check.matches("[0-9K]")?check:null,null);
    }
    /** Documento no RUN: mayusculas, solo letras y digitos, sin las etiquetas iniciales; exige al menos un digito. */
    static Identity document(String text){
        var tokens=new ArrayList<>(List.of(fold(text).toUpperCase(Locale.ROOT).strip().split("[\\s:]+")));
        while(!tokens.isEmpty()&&LABELS.contains(tokens.getFirst().replaceAll("[^A-Z0-9]","")))tokens.removeFirst();
        String key=String.join("",tokens).replaceAll("[^A-Z0-9]","");
        return key.matches(".*[0-9].*")?new Identity(null,null,cut(key,100)):Identity.NONE;
    }
    /** Texto libre de identificacion: un RUN reconocido o, si no, la clave de documento. */
    static Identity parse(String text){
        String upper=fold(text).toUpperCase(Locale.ROOT);
        var m=RUN.matcher(upper);
        if(m.find()){var id=run(m.group(1),m.group(2));if(!id.empty())return id;}
        return document(text);
    }
    private static String cut(String value,int max){return value.length()>max?value.substring(0,max):value;}

    // --- Fuentes ----------------------------------------------------------------------------------
    private static Optional<Ref> person(String source,String role,String name,String alias,Identity identity,boolean authenticated,Long requirement){
        if(!ROLES.contains(role))return Optional.empty();
        String display=name==null||name.isBlank()?(alias==null?"":alias.strip()):name.strip();
        String key=nameKey((name==null?"":name)+" "+(alias==null?"":alias));
        if(key==null&&identity.empty())return Optional.empty();
        return Optional.of(new Ref(source,role,display.isEmpty()?null:cut(display,500),key,identity,authenticated,requirement));
    }
    private static String join(String...parts){
        return String.join(" ",Arrays.stream(parts).filter(p->p!=null&&!p.isBlank()).map(String::strip).toList());
    }
    List<Ref> references(long com){
        var c=db.one("SELECT id,external_source,presenter,identity_document FROM communication WHERE id=?",com);
        var out=new ArrayList<Ref>();
        if(!s(c,"external_source").isEmpty()){
            for(var p:db.rows("SELECT * FROM external_person WHERE communication_id=? ORDER BY person_index,id",com)){
                Identity id=p.get("run_number")!=null?run(s(p,"run_number"),s(p,"run_dv"))
                    :p.get("foreign_document_number")!=null?document(s(p,"foreign_document_number")):Identity.NONE;
                person("FORMULARIO",s(p,"person_role"),join(s(p,"given_names"),s(p,"surnames")),null,id,b(p,"authenticated"),null).ifPresent(out::add);
            }
            return out;
        }
        var fichas=db.rows("SELECT ar.requirement_id,ar.data_json FROM attention_record ar JOIN requirement r ON r.id=ar.requirement_id WHERE r.communication_id=? ORDER BY ar.requirement_id",com);
        if(!fichas.isEmpty()){
            for(var f:fichas)ficha(n(f,"requirement_id"),decode(s(f,"data_json")),out);
            return out;
        }
        person("COM","REQUIRENTE",s(c,"presenter"),null,parse(s(c,"identity_document")),false,null).ifPresent(out::add);
        for(var p:db.rows("SELECT person_role,name,identity_document FROM communication_person WHERE communication_id=? ORDER BY id",com))
            person("COM_PERSONA",s(p,"person_role"),s(p,"name"),null,parse(s(p,"identity_document")),false,null).ifPresent(out::add);
        return out;
    }
    /**
     * Ficha vigente (1.0 o 1.1): requirente, curador/a conocido/a y personas relacionadas con su nombre de preferencia.
     * El preferred_name de nivel superior esta en la seccion de quien presenta en ambas modalidades (persona adulta en
     * GENERAL, NNA que solicita en NNA): se une a su nombre y, si la ficha no trae nombre, forma la referencia por si solo.
     */
    private void ficha(long req,Map<String,Object> data,List<Ref> out){
        String name=s(data,"presenter_name");
        if(name.isBlank())name=join(s(data,"presenter_first_name"),s(data,"presenter_middle_name"),s(data,"presenter_last_name"),s(data,"presenter_second_last_name"));
        Identity id=!s(data,"presenter_run_number").isEmpty()?run(s(data,"presenter_run_number"),s(data,"presenter_run_dv")):Identity.NONE;
        if(id.empty()&&!s(data,"presenter_document_number").isEmpty())id=document(s(data,"presenter_document_number"));
        if(id.empty())id=parse(s(data,"identity_document"));
        person("FICHA","REQUIRENTE",name,s(data,"preferred_name"),id,false,req).ifPresent(out::add);
        person("FICHA","CURADOR",s(data,"guardian_name"),null,Identity.NONE,false,req).ifPresent(out::add);
        if(data.get("people") instanceof List<?> people)for(Object raw:people){
            if(!(raw instanceof Map<?,?> p))continue;
            var row=new HashMap<String,Object>();p.forEach((k,v)->row.put(k.toString(),v));
            String role=s(row,"role").isEmpty()?"OTRA_PERSONA":s(row,"role");
            person("FICHA",role,s(row,"name"),s(row,"preferred_name"),parse(s(row,"identity_document")),false,req).ifPresent(out::add);
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> decode(String value){return value.isBlank()?Map.of():(Map<String,Object>)json.readValue(value,Map.class);}

    /** Recalcula las referencias de la COM. Se llama dentro de la transaccion (y del lock) del comando que la cambia. */
    public void refresh(long com){
        db.update("DELETE FROM person_reference WHERE communication_id=?",com);
        long now=System.currentTimeMillis();
        for(var r:references(com))db.update("INSERT INTO person_reference(communication_id,requirement_id,source,person_role,display_name,name_key,run_number,run_dv,document_key,authenticated,updated_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            com,r.requirement(),r.source(),cut(r.role(),24),r.display(),r.nameKey(),r.identity().runNumber(),r.identity().runDv(),r.identity().documentKey(),r.authenticated(),now);
    }

    /**
     * Carga inicial al arrancar (marcador PERSON_INDEX_V12): recorre las COM existentes por lotes, cada uno en su
     * transaccion y con el lock global, y deja el marcador al final. Si se interrumpe, el siguiente arranque la rehace
     * entera (refresh es idempotente); las COM creadas mientras tanto ya las indexa su propio comando. El log solo
     * registra cantidades.
     */
    void rebuild(){
        if(Boolean.TRUE.equals(tx.execute(st->db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",MARKER)>0)))return;
        long last=0,coms=0;
        while(true){
            final long from=last;
            List<Long> ids=tx.execute(st->{
                db.lock();
                var batch=db.rows("SELECT id FROM communication WHERE id>? ORDER BY id LIMIT "+BATCH,from).stream().map(r->n(r,"id")).toList();
                batch.forEach(this::refresh);
                return batch;
            });
            if(ids==null||ids.isEmpty())break;
            last=ids.getLast();coms+=ids.size();
        }
        long total=Objects.requireNonNull(tx.execute(st->{
            db.lock();
            if(db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",MARKER)==0)
                db.update("INSERT INTO application_marker(code,applied_ms) VALUES(?,?)",MARKER,System.currentTimeMillis());
            return db.count("SELECT COUNT(*) FROM person_reference");
        }));
        if(coms>0)LOG.info("Indice de personas: {} referencias en {} comunicaciones.",total,coms);
    }
}
