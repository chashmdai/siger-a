package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Catalogos estadisticos versionados (0.8.0, D27, gate E01; DISENO 3.1). Lista cerrada de catalogos en {@link #CATALOGS}
 * y valores especiales fijos (D28) fuera de ellos. Solo TI, con fundamento:
 * <ul>
 * <li>CATALOG_IMPORT {content, reason}: archivo JSON {@value #FORMAT} con lista cerrada de campos, validado completo antes
 * de escribir (todo o nada); queda PROPUESTA. El nivel y el orden los calcula el servidor; un padre debe aparecer antes
 * que sus hijos. TRAMO_EDAD exige tramos sin jerarquia que cubren exactamente 0-130 anios, contiguos y sin solapes. Un
 * archivo identico a una version PROPUESTA o VIGENTE del mismo catalogo se rechaza; identico a una RETIRADA, se admite.</li>
 * <li>CATALOG_ACTIVATE (id = version PROPUESTA, revision): la deja VIGENTE y retira la VIGENTE anterior en el mismo acto.</li>
 * <li>CATALOG_MAPPING_IMPORT {from_version, to_version, content, reason}: correspondencia {@value #MAPPING_FORMAT} entre dos
 * versiones del mismo catalogo, con codigos existentes, pares unicos y una sola carga por par de versiones.</li>
 * </ul>
 * La huella del archivo es el SHA-256 de su texto sin BOM y con saltos LF (igual en Windows y Linux). La auditoria
 * (recurso {@value #KIND}, visible para TI y AUDITORIA) registra catalogo, version, cantidades y fundamento, nunca el
 * contenido. /api/catalog publica solo la version VIGENTE de cada catalogo; TI lee todas las versiones aparte.
 */
@Service
public class StatCatalogs {
    static final String KIND="CATALOGO_ESTADISTICO",IMPORTED="CATALOGO_IMPORTADO",ACTIVATED="CATALOGO_ACTIVADO",MAPPED="CATALOGO_CORRESPONDENCIA_IMPORTADA";
    static final String PROPOSED="PROPUESTA",CURRENT="VIGENTE",RETIRED="RETIRADA";
    static final String FORMAT="siger-catalogo-1",MAPPING_FORMAT="siger-correspondencia-1",AGE="TRAMO_EDAD";
    static final int MAX_DESCRIPTION=1000,MAX_CONTENT=400_000,MAX_ITEMS=2000,MAX_PAIRS=5000,MAX_DEPTH=5,MAX_ERRORS=20,AGE_MIN=0,AGE_MAX=130;
    /**
     * Catalogos de DISENO 3.1 con su rotulo visible (lista cerrada). Desde E06 (decision del responsable del 2026-11-24)
     * agrega ROL_ESTADO (eje VII del modelo 2.0, que sale del catalogo de derechos) y GRUPO_PRIORITARIO (seccion VIII).
     */
    static final Map<String,String> CATALOGS=ordered(
        "TIPO_SOLICITUD","Tipo de solicitud","TIPO_REQUIRENTE","Tipo de requirente","VIA_INGRESO","Vía de ingreso",
        "CONTEXTO","Contexto","CARACTERIZACION","Caracterización de la vulneración","DERECHO","Derechos",
        "ROL_ESTADO","Rol del Estado","GRUPO_PRIORITARIO","Grupos prioritarios","TIPO_ACTUACION","Tipo de actuación","TIPO_INSTITUCION","Tipo de institución","RELACION","Relación con el NNA",
        AGE,"Tramos de edad");
    /** Valores especiales de una variable estadistica (D28), fijos en Java y fuera de los catalogos. */
    static final Map<String,String> SPECIAL=ordered("PENDIENTE","Pendiente","SIN_INFORMACION","Sin información","NO_DECLARADO","No declarado","NO_APLICA","No aplica");
    static final Pattern CODE=Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,39}");
    private static final Set<String> ROOT_FIELDS=Set.of("format","catalog","label","source","items");
    private static final Set<String> ITEM_FIELDS=Set.of("code","label","parent","selectable","range_from","range_to","source_ref","descripcion");
    private static final Set<String> MAPPING_FIELDS=Set.of("format","pairs"),PAIR_FIELDS=Set.of("from","to");
    private static final JsonMapper READER=JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(MAX_CONTENT).maxNumberLength(12).build()).build()).build();

    private final Db db;
    public StatCatalogs(Db db){this.db=db;}

    record Item(String code,String label,String parent,int level,int sort,boolean selectable,Integer from,Integer to,String ref,String description){}
    record Parsed(String catalog,String label,String source,List<Item> items,String sha){}
    record Pair(String from,String to){}

    Workflow.Result run(Actor a,String type,long id,Long revision,Input in){
        if(!a.role(Roles.TI))Problem.denied();
        return switch(type){
            case "CATALOG_IMPORT" -> importVersion(a,in);
            case "CATALOG_ACTIVATE" -> activate(a,id,revision,in);
            case "CATALOG_MAPPING_IMPORT" -> importMapping(a,in);
            default -> throw new Problem(400,"Operacion de catalogos desconocida.");
        };
    }

    // --- Comandos -------------------------------------------------------------------------------------------------
    private Workflow.Result importVersion(Actor a,Input in){
        var p=parse(content(in));String reason=in.reason();
        for(var same:db.rows("SELECT id,state FROM stat_catalog_version WHERE catalog=? AND file_sha256=? AND state IN (?,?) ORDER BY id",p.catalog(),p.sha(),PROPOSED,CURRENT))
            throw new Problem(409,"Ese archivo ya está importado como versión "+n(same,"id")+" ("+state(s(same,"state"))+") de "+CATALOGS.get(p.catalog())+".");
        long now=Lifecycle.now();
        long version=db.insert("INSERT INTO stat_catalog_version(catalog,label,state,source,file_sha256,loaded_by,loaded_ms,basis) VALUES(?,?,?,?,?,?,?,?)",
            p.catalog(),p.label(),PROPOSED,p.source(),p.sha(),a.id(),now,reason);
        for(var i:p.items())db.update("INSERT INTO stat_catalog_item(version_id,code,label,parent_code,level,sort,selectable,range_from,range_to,source_ref,description) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            version,i.code(),i.label(),i.parent(),i.level(),i.sort(),i.selectable(),i.from(),i.to(),i.ref(),i.description());
        db.audit(a.id(),IMPORTED,KIND,version,null,p.catalog()+" versión "+version+" propuesta: "+p.items().size()+" ítems, huella "+p.sha().substring(0,12)+". "+reason);
        return new Workflow.Result(KIND,version,"Se importó la versión "+version+" de "+CATALOGS.get(p.catalog())+" como propuesta ("+count(p.items().size(),"ítem","ítems")+").");
    }

    private Workflow.Result activate(Actor a,long id,Long revision,Input in){
        var v=db.one("SELECT * FROM stat_catalog_version WHERE id=?",id);Db.revision(v,revision);
        require(s(v,"state").equals(PROPOSED),"Solo una versión propuesta se activa: esta versión está "+state(s(v,"state")).toLowerCase(Locale.ROOT)+".");
        String reason=in.reason(),catalog=s(v,"catalog");long now=Lifecycle.now();
        var previous=db.rows("SELECT id FROM stat_catalog_version WHERE catalog=? AND state=? ORDER BY id",catalog,CURRENT);
        // Primero se retira la vigente (el UNIQUE de current_catalog admite una sola por catalogo).
        for(var old:previous)db.update("UPDATE stat_catalog_version SET state=?,`current_catalog`=NULL,retired_by=?,retired_ms=?,revision=revision+1 WHERE id=?",RETIRED,a.id(),now,n(old,"id"));
        db.update("UPDATE stat_catalog_version SET state=?,`current_catalog`=?,activated_by=?,activated_ms=?,activation_basis=?,revision=revision+1 WHERE id=?",CURRENT,catalog,a.id(),now,reason,id);
        String retired=previous.isEmpty()?"":"; retira la versión "+String.join(", ",previous.stream().map(r->s(r,"id")).toList());
        db.audit(a.id(),ACTIVATED,KIND,id,null,catalog+" versión "+id+" vigente"+retired+". "+reason);
        return new Workflow.Result(KIND,id,"La versión "+id+" de "+CATALOGS.get(catalog)+" quedó vigente"+(previous.isEmpty()?".":"; la anterior quedó retirada."));
    }

    private Workflow.Result importMapping(Actor a,Input in){
        long from=in.id("from_version"),to=in.id("to_version");
        require(from!=to,"La correspondencia une dos versiones distintas.");
        var source=version(from);var target=version(to);
        require(s(source,"catalog").equals(s(target,"catalog")),"Las dos versiones deben ser del mismo catálogo.");
        var pairs=parsePairs(content(in),codes(from),codes(to));String reason=in.reason();
        require(db.count("SELECT COUNT(*) FROM stat_catalog_mapping WHERE from_version=? AND to_version=?",from,to)==0,"Ya hay una correspondencia cargada entre la versión "+from+" y la versión "+to+".");
        long now=Lifecycle.now();
        for(var p:pairs)db.update("INSERT INTO stat_catalog_mapping(from_version,from_code,to_version,to_code,loaded_by,loaded_ms,basis) VALUES(?,?,?,?,?,?,?)",from,p.from(),to,p.to(),a.id(),now,reason);
        db.audit(a.id(),MAPPED,KIND,to,null,s(source,"catalog")+": versión "+from+" -> versión "+to+", "+count(pairs.size(),"par","pares")+". "+reason);
        return new Workflow.Result(KIND,to,"Se cargó la correspondencia de la versión "+from+" a la versión "+to+" ("+count(pairs.size(),"par","pares")+").");
    }

    // --- Validacion de archivos -----------------------------------------------------------------------------------
    /** Texto del archivo sin BOM y con saltos LF; obligatorio y acotado. No se recorta: la huella es la del archivo. */
    static String content(Input in){
        Object value=in.values().get("content");
        if(!(value instanceof String text)||text.isBlank())throw new Problem(400,"Adjunta el archivo del catálogo.");
        if(text.length()>MAX_CONTENT||text.indexOf(0)>=0)throw new Problem(400,"El archivo excede el tamaño admitido o tiene caracteres no admitidos.");
        if(text.startsWith("﻿"))text=text.substring(1);
        return text.replace("\r\n","\n");
    }
    static String sha(String text){return ExternalOriginal.sha256(text.getBytes(StandardCharsets.UTF_8));}
    private static JsonNode read(String text,String what){
        try{return READER.readTree(text);}
        catch(JacksonException e){throw new Problem(400,"El archivo no es un "+what+" válido: revisa que sea JSON sin claves repetidas.");}
    }

    /** Archivo de catalogo validado completo; 400 con hasta 20 problemas y ninguna escritura si algo no cumple. */
    static Parsed parse(String text){
        var root=read(text,"catálogo");
        if(!root.isObject())throw new Problem(400,"El archivo del catálogo debe ser un objeto JSON.");
        var unknown=unknown(root,ROOT_FIELDS);if(!unknown.isEmpty())throw new Problem(400,"Campos no admitidos en el archivo: "+unknown+".");
        if(!FORMAT.equals(root.path("format").asString()))throw new Problem(400,"Formato desconocido: el archivo debe declarar \"format\": \""+FORMAT+"\".");
        String catalog=root.path("catalog").isString()?root.path("catalog").asString():"";
        if(!CATALOGS.containsKey(catalog))throw new Problem(400,"Catálogo desconocido. Admitidos: "+String.join(", ",CATALOGS.keySet())+".");
        String label=text(root.path("label"),200),source=text(root.path("source"),500);
        if(label==null)throw new Problem(400,"Indica el nombre de la versión (label, máximo 200 caracteres).");
        if(source==null)throw new Problem(400,"Indica la fuente de la versión (source: documento y versión de origen, máximo 500 caracteres).");
        var items=root.path("items");
        if(!items.isArray()||items.isEmpty())throw new Problem(400,"El catálogo no tiene ítems.");
        if(items.size()>MAX_ITEMS)throw new Problem(400,"Máximo "+MAX_ITEMS+" ítems por catálogo.");
        boolean age=catalog.equals(AGE);
        var errors=new ArrayList<String>();var out=new ArrayList<Item>();var levels=new HashMap<String,Integer>();
        int number=0;
        for(var node:items){
            number++;String at="Ítem "+number+": ";
            if(!node.isObject()){errors.add(at+"debe ser un objeto.");continue;}
            var extra=unknown(node,ITEM_FIELDS);if(!extra.isEmpty()){errors.add(at+"campos no admitidos "+extra+".");continue;}
            String code=node.path("code").isString()?node.path("code").asString():"";
            if(!CODE.matcher(code).matches()){errors.add(at+"código inválido (mayúsculas, dígitos, punto, guion o guion bajo; máximo 40).");continue;}
            if(SPECIAL.containsKey(code)){errors.add(at+"el código "+code+" está reservado para un valor especial.");continue;}
            if(levels.containsKey(code)){errors.add(at+"el código "+code+" está repetido.");continue;}
            String itemLabel=text(node.path("label"),300);
            if(itemLabel==null){errors.add(at+"indica el rótulo (máximo 300 caracteres).");continue;}
            String parent=null;int level=1;
            if(node.has("parent")&&!node.path("parent").isNull()){
                if(age){errors.add(at+"los tramos de edad no tienen jerarquía.");continue;}
                parent=node.path("parent").isString()?node.path("parent").asString():"";
                if(!levels.containsKey(parent)){errors.add(at+"el padre "+(parent.isEmpty()?"indicado":parent)+" no existe o aparece después en el archivo.");continue;}
                level=levels.get(parent)+1;
                if(level>MAX_DEPTH){errors.add(at+"supera "+MAX_DEPTH+" niveles de jerarquía.");continue;}
            }
            boolean selectable=true;
            if(node.has("selectable")){if(!node.path("selectable").isBoolean()){errors.add(at+"selectable debe ser verdadero o falso.");continue;}selectable=node.path("selectable").booleanValue();}
            Integer from=null,to=null;
            if(age){
                from=years(node.path("range_from"));to=years(node.path("range_to"));
                if(from==null||to==null){errors.add(at+"indica range_from y range_to en años enteros entre "+AGE_MIN+" y "+AGE_MAX+".");continue;}
                if(from>to){errors.add(at+"range_from no puede ser mayor que range_to.");continue;}
            }else if(node.has("range_from")||node.has("range_to")){errors.add(at+"solo los tramos de edad tienen rango.");continue;}
            String ref=null;
            if(node.has("source_ref")&&!node.path("source_ref").isNull()){ref=text(node.path("source_ref"),80);if(ref==null){errors.add(at+"source_ref admite hasta 80 caracteres.");continue;}}
            String description=null;
            if(node.has("descripcion")&&!node.path("descripcion").isNull()){description=text(node.path("descripcion"),MAX_DESCRIPTION);if(description==null){errors.add(at+"descripcion admite texto de hasta "+MAX_DESCRIPTION+" caracteres.");continue;}}
            levels.put(code,level);out.add(new Item(code,itemLabel,parent,level,out.size()+1,selectable,from,to,ref,description));
        }
        if(errors.isEmpty()&&out.stream().noneMatch(Item::selectable))errors.add("El catálogo no tiene ningún ítem seleccionable.");
        if(errors.isEmpty()&&age)errors.addAll(ageGaps(out));
        if(!errors.isEmpty())throw new Problem(400,"No se importó el catálogo. "+limited(errors));
        return new Parsed(catalog,label,source,List.copyOf(out),sha(text));
    }
    /** Tramos de edad: cobertura exacta y contigua de 0 a 130 anios, sin solapes ni vacios (cada edad, un solo tramo). */
    static List<String> ageGaps(List<Item> items){
        var sorted=new ArrayList<>(items);sorted.sort(Comparator.comparing(Item::from).thenComparing(Item::to));
        var errors=new ArrayList<String>();int expected=AGE_MIN;
        for(var i:sorted){
            if(i.from()<expected)errors.add("El tramo "+i.code()+" ("+i.from()+"-"+i.to()+") se superpone con otro tramo.");
            else if(i.from()>expected)errors.add("Falta un tramo para las edades "+expected+"-"+(i.from()-1)+".");
            expected=Math.max(expected,i.to()+1);
        }
        if(expected<=AGE_MAX)errors.add("Falta un tramo para las edades "+expected+"-"+AGE_MAX+" (los tramos cubren de "+AGE_MIN+" a "+AGE_MAX+" años; por ejemplo, un tramo final 18-130).");
        return errors;
    }
    /** Pares de la correspondencia: lista cerrada de campos, codigos existentes en su version y sin repetir. */
    static List<Pair> parsePairs(String text,Set<String> fromCodes,Set<String> toCodes){
        var root=read(text,"archivo de correspondencia");
        if(!root.isObject())throw new Problem(400,"El archivo de correspondencia debe ser un objeto JSON.");
        var unknown=unknown(root,MAPPING_FIELDS);if(!unknown.isEmpty())throw new Problem(400,"Campos no admitidos en el archivo: "+unknown+".");
        if(!MAPPING_FORMAT.equals(root.path("format").asString()))throw new Problem(400,"Formato desconocido: el archivo debe declarar \"format\": \""+MAPPING_FORMAT+"\".");
        var pairs=root.path("pairs");
        if(!pairs.isArray()||pairs.isEmpty())throw new Problem(400,"La correspondencia no tiene pares.");
        if(pairs.size()>MAX_PAIRS)throw new Problem(400,"Máximo "+MAX_PAIRS+" pares por correspondencia.");
        var errors=new ArrayList<String>();var out=new ArrayList<Pair>();var seen=new HashSet<Pair>();int number=0;
        for(var node:pairs){
            number++;String at="Par "+number+": ";
            if(!node.isObject()||!unknown(node,PAIR_FIELDS).isEmpty()){errors.add(at+"debe tener solo from y to.");continue;}
            String from=node.path("from").isString()?node.path("from").asString():"",to=node.path("to").isString()?node.path("to").asString():"";
            if(!fromCodes.contains(from)){errors.add(at+"el código de origen "+(from.isEmpty()?"indicado":from)+" no existe en la versión de origen.");continue;}
            if(!toCodes.contains(to)){errors.add(at+"el código de destino "+(to.isEmpty()?"indicado":to)+" no existe en la versión de destino.");continue;}
            var p=new Pair(from,to);if(!seen.add(p)){errors.add(at+"el par "+from+" -> "+to+" está repetido.");continue;}
            out.add(p);
        }
        if(!errors.isEmpty())throw new Problem(400,"No se cargó la correspondencia. "+limited(errors));
        return out;
    }
    private static Set<String> unknown(JsonNode node,Set<String> allowed){
        var out=new TreeSet<String>();for(var e:node.properties())if(!allowed.contains(e.getKey()))out.add(e.getKey());return out;
    }
    /** Texto no vacio, sin caracteres de control, hasta max; nulo si no cumple. */
    private static String text(JsonNode node,int max){
        if(!node.isString())return null;
        String v=node.asString().strip();
        return v.isEmpty()||v.length()>max||v.chars().anyMatch(Character::isISOControl)?null:v;
    }
    private static Integer years(JsonNode node){
        if(!node.isIntegralNumber()||!node.canConvertToInt())return null;
        int v=node.intValue();return v<AGE_MIN||v>AGE_MAX?null:v;
    }
    private static String limited(List<String> errors){
        return String.join(" ",errors.size()>MAX_ERRORS?errors.subList(0,MAX_ERRORS):errors)+(errors.size()>MAX_ERRORS?" Hay más problemas.":"");
    }

    // --- Lecturas -------------------------------------------------------------------------------------------------
    /**
     * Publicacion en /api/catalog (cualquier sesion): por cada catalogo de la lista cerrada, su version VIGENTE con sus
     * items (nula si no hay), y los valores especiales. Nunca las versiones PROPUESTA ni RETIRADA.
     */
    public Map<String,Object> published(){
        var current=new HashMap<String,Map<String,Object>>();
        for(var v:db.rows("SELECT id,catalog,label,source,activated_ms FROM stat_catalog_version WHERE state=? ORDER BY id",CURRENT))current.put(s(v,"catalog"),v);
        var items=new HashMap<Long,List<Map<String,Object>>>();
        if(!current.isEmpty())for(var i:db.rows("SELECT i.version_id,i.code,i.label,i.parent_code,i.level,i.sort,i.selectable,i.range_from,i.range_to,i.source_ref,i.description FROM stat_catalog_item i "
            +"JOIN stat_catalog_version v ON v.id=i.version_id WHERE v.state=? ORDER BY i.version_id,i.sort",CURRENT))
            items.computeIfAbsent(n(i,"version_id"),k->new ArrayList<>()).add(item(i));
        var catalogs=new ArrayList<Map<String,Object>>();
        CATALOGS.forEach((code,label)->{
            var row=new LinkedHashMap<String,Object>();row.put("catalog",code);row.put("label",label);
            var v=current.get(code);
            if(v==null){row.put("version",null);row.put("items",List.of());}
            else{row.put("version",Map.of("id",n(v,"id"),"label",s(v,"label"),"source",s(v,"source"),"activated_ms",n(v,"activated_ms")));row.put("items",items.getOrDefault(n(v,"id"),List.of()));}
            catalogs.add(row);
        });
        return Map.of("catalogs",catalogs,"special",specials());
    }
    /** Administracion (solo TI): catalogos, valores especiales, todas las versiones con su cantidad de items y las correspondencias cargadas. */
    public Map<String,Object> administration(Actor a){
        if(!a.role(Roles.TI))Problem.denied();
        var out=new LinkedHashMap<String,Object>();
        out.put("catalogs",CATALOGS.entrySet().stream().map(e->Map.of("catalog",e.getKey(),"label",e.getValue())).toList());
        out.put("special",specials());
        out.put("formats",Map.of("catalog",FORMAT,"mapping",MAPPING_FORMAT));
        out.put("versions",db.rows("SELECT v.id,v.catalog,v.label,v.state,v.source,v.file_sha256,v.loaded_ms,v.basis,v.activated_ms,v.activation_basis,v.retired_ms,v.revision,"
            +"l.name AS loaded_by,x.name AS activated_by,r.name AS retired_by,(SELECT COUNT(*) FROM stat_catalog_item i WHERE i.version_id=v.id) AS items "
            +"FROM stat_catalog_version v JOIN staff l ON l.id=v.loaded_by LEFT JOIN staff x ON x.id=v.activated_by LEFT JOIN staff r ON r.id=v.retired_by ORDER BY v.catalog,v.id DESC"));
        out.put("mappings",mappings("1=1"));
        return out;
    }
    /** Una version (solo TI): sus datos, todos sus items y las correspondencias en que participa. */
    public Map<String,Object> version(Actor a,long id){
        if(!a.role(Roles.TI))Problem.denied();
        var v=db.one("SELECT v.*,l.name AS loaded_by_name,x.name AS activated_by_name,r.name AS retired_by_name FROM stat_catalog_version v JOIN staff l ON l.id=v.loaded_by "
            +"LEFT JOIN staff x ON x.id=v.activated_by LEFT JOIN staff r ON r.id=v.retired_by WHERE v.id=?",id);
        var out=new LinkedHashMap<String,Object>(v);out.remove("current_catalog");
        out.put("catalog_label",CATALOGS.get(s(v,"catalog")));
        out.put("items",db.rows("SELECT code,label,parent_code,level,sort,selectable,range_from,range_to,source_ref,description FROM stat_catalog_item WHERE version_id=? ORDER BY sort",id).stream().map(StatCatalogs::item).toList());
        out.put("mappings",db.rows("SELECT m.from_version,m.from_code,m.to_version,m.to_code FROM stat_catalog_mapping m WHERE m.from_version=? OR m.to_version=? ORDER BY m.from_version,m.to_version,m.id",id,id));
        return out;
    }
    private List<Map<String,Object>> mappings(String where){
        return db.rows("SELECT m.from_version,m.to_version,COUNT(*) AS pairs,MIN(m.loaded_ms) AS loaded_ms,MIN(u.name) AS loaded_by FROM stat_catalog_mapping m JOIN staff u ON u.id=m.loaded_by WHERE "+where
            +" GROUP BY m.from_version,m.to_version ORDER BY m.from_version,m.to_version");
    }
    private static Map<String,Object> item(Map<String,Object> i){
        var row=new LinkedHashMap<String,Object>();
        for(String k:List.of("code","label","parent_code","level","sort"))row.put(k,i.get(k));
        row.put("selectable",b(i,"selectable"));row.put("range_from",i.get("range_from"));row.put("range_to",i.get("range_to"));row.put("source_ref",i.get("source_ref"));row.put("description",i.get("description"));
        return row;
    }
    private static List<Map<String,Object>> specials(){return SPECIAL.entrySet().stream().map(e->Map.<String,Object>of("code",e.getKey(),"label",e.getValue())).toList();}
    private Map<String,Object> version(long id){
        var rows=db.rows("SELECT * FROM stat_catalog_version WHERE id=?",id);
        if(rows.isEmpty())throw new Problem(404,"La versión "+id+" del catálogo no existe.");
        return rows.getFirst();
    }
    private Set<String> codes(long version){
        var out=new HashSet<String>();db.rows("SELECT code FROM stat_catalog_item WHERE version_id=?",version).forEach(r->out.add(s(r,"code")));return out;
    }
    private static String state(String state){return switch(state){case PROPOSED->"Propuesta";case CURRENT->"Vigente";case RETIRED->"Retirada";default->state;};}
    private static String count(int n,String one,String many){return n+" "+(n==1?one:many);}
    private static Map<String,String> ordered(String... pairs){
        var out=new LinkedHashMap<String,String>();for(int i=0;i<pairs.length;i+=2)out.put(pairs[i],pairs[i+1]);return Collections.unmodifiableMap(out);
    }
}
