package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;

/**
 * Buscador de personas y folios (0.5.0, D5, gate G08a). Busca sobre el indice person_reference (RUN, documento o
 * nombre) y sobre los folios de COM, REQ, Caso y comprobante del Formulario. Es un indice de coincidencias: no fusiona
 * identidades ni autentica personas.
 *
 * Permisos (decision del responsable, G08): una coincidencia de persona se muestra solo si la cuenta puede leer el
 * registro donde esta el dato: la COM (Visibility.comRead) o, para la fuente FICHA, tambien la ficha de primera atencion
 * (Visibility.attentionRead = Attention.mayRead). Quien lee solo un Caso no lo encuentra por persona, pero si por su
 * folio. Cada paso de la cadena (COM, SOL, REQ, Casos, flujos pendientes) se filtra con su propio predicado. Lo que la
 * cuenta no lee no se muestra ni se cuenta (totales, resumen y filtros). Solo busca una cuenta que lee expedientes
 * (Actor.readsRecords, G08b): TI y AUDITORIA sola reciben 403.
 *
 * Auditoria: un evento BUSQUEDA por consulta con el tipo de cada lectura y su cantidad de resultados. El termino nunca
 * se guarda en claro ni va al log. Con siger.audit-search-key se agrega la huella HMAC-SHA256 (hex) del termino
 * canonico de cada lectura: "FOLIO:<folio o correlativo>", "RUN:<numero sin DV>", "DOCUMENTO:<clave>" o
 * "NOMBRE:<nombre normalizado>". Con la misma clave la institucion puede comprobar si un termino fue buscado; un
 * SHA-256 simple de un RUN se revertiria por fuerza bruta.
 */
@Service
public class PersonSearch {
    static final int LIMIT=50,CAP=5000,MAX_WORDS=8,CHUNK=500;
    static final ZoneId ZONE=ZoneId.of("America/Santiago");
    enum Kind{FOLIO,RUN,DOCUMENTO,NOMBRE}
    /** Movimientos que no cuentan como ultima actividad del REQ o Caso. */
    private static final String READ_ACTIONS="'CONSULTA','DESCARGA','DENEGADO','BUSQUEDA','FICHA_VERSION_CONSULTADA'";
    private final Db db;private final BoardQueries boards;private final String comPrefix;private final byte[] key;

    public PersonSearch(Db db,BoardQueries boards,@Value("${siger.folio-prefix:COM-A}")String prefix,@Value("${siger.audit-search-key:}")String key){
        this.db=db;this.boards=boards;this.comPrefix=prefix;
        if(!key.isBlank()&&key.length()<32)throw new IllegalStateException("siger.audit-search-key debe tener al menos 32 caracteres (o quedar vacia).");
        this.key=key.isBlank()?null:key.getBytes(StandardCharsets.UTF_8);
    }

    // --- Termino ----------------------------------------------------------------------------------
    /** Una lectura del termino con sus valores normalizados (solo los de su tipo). */
    record Reading(Kind kind,String canonical,List<String> folios,String runNumber,String runDv,String altNumber,String altDv,List<String> documents,List<String> words,String nameKey,String warning){
        static Reading folio(String canonical,List<String> folios){return new Reading(Kind.FOLIO,canonical,folios,null,null,null,null,List.of(),List.of(),null,null);}
    }
    /** Interpretaciones posibles del texto: las detectadas (busqueda automatica) y las admitidas al elegir otra. */
    record Term(List<Kind> detected,Map<Kind,Reading> available){}

    private static final Pattern FOLIO=Pattern.compile("^([A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*)-([0-9]{1,9})$");
    private static final Pattern DIGITS=Pattern.compile("^[0-9]{1,9}$");
    private static final Pattern DOTTED=Pattern.compile("^[0-9]{1,3}(?:\\.[0-9]{3})+$");
    private static final Pattern RUN_DV=Pattern.compile("^([0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]{1,9})-([0-9K])$");
    private static final Pattern RUN_K=Pattern.compile("^([0-9]{1,9})K$");

    /** Digito verificador modulo 11 de un numero de RUN sin puntos. */
    static String dv(String number){
        int sum=0,m=2;
        for(int i=number.length()-1;i>=0;i--){sum+=(number.charAt(i)-'0')*m;m=m==7?2:m+1;}
        int r=11-sum%11;return r==11?"0":r==10?"K":String.valueOf(r);
    }
    /**
     * Claves de documento que puede tener ese RUN escrito sin guion en el texto libre (PersonIndex lo guarda como
     * document_key): lo escrito, el numero solo y el numero con su DV correcto (con un DV erroneo se busca por numero).
     */
    private static List<String> documents(String...written){
        var out=new LinkedHashSet<String>(List.of(written));
        for(String w:written)if(w.matches("[0-9]+"))out.add(w+dv(w));
        return List.copyOf(out);
    }
    private static String fold(String value){return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}+","");}
    private static String pad(long n){return String.format(Locale.ROOT,"%06d",n);}

    /** Clasifica el texto. Lanza 400 si no es buscable. */
    Term term(String q){
        String text=q==null?"":q.strip();
        if(text.length()<3||text.length()>160)throw new Problem(400,"Escribe entre 3 y 160 caracteres.");
        String compact=fold(text).toUpperCase(Locale.ROOT).replaceAll("\\s+","");
        var available=new EnumMap<Kind,Reading>(Kind.class);var detected=new ArrayList<Kind>();
        Matcher m;
        if((m=FOLIO.matcher(compact)).matches()){
            String folio=m.group(1)+"-"+pad(Long.parseLong(m.group(2)));
            available.put(Kind.FOLIO,Reading.folio(folio,List.of(folio)));detected.add(Kind.FOLIO);
        }else if(DIGITS.matcher(compact).matches()){
            long n=Long.parseLong(compact);
            if(n>0){
                available.put(Kind.FOLIO,Reading.folio(String.valueOf(n),List.of(comPrefix+"-"+pad(n),"REQ-A-"+pad(n),"CAS-A-"+pad(n))));detected.add(Kind.FOLIO);
            }
            var run=PersonIndex.run(compact,null);
            if(!run.empty()){
                // Sin separador el ultimo digito puede ser el DV: se prueban ambas lecturas (la segunda exige el mismo DV guardado).
                var alt=compact.length()>1?PersonIndex.run(compact.substring(0,compact.length()-1),null):PersonIndex.Identity.NONE;
                available.put(Kind.RUN,new Reading(Kind.RUN,run.runNumber(),List.of(),run.runNumber(),null,alt.empty()?null:alt.runNumber(),alt.empty()?null:compact.substring(compact.length()-1),
                    documents(compact,run.runNumber()),List.of(),null,null));
                if(compact.length()>=5)detected.add(Kind.RUN);
            }
        }else if(DOTTED.matcher(compact).matches()){
            var run=PersonIndex.run(compact,null);
            if(!run.empty()){available.put(Kind.RUN,new Reading(Kind.RUN,run.runNumber(),List.of(),run.runNumber(),null,null,null,documents(run.runNumber()),List.of(),null,null));detected.add(Kind.RUN);}
        }else if((m=RUN_DV.matcher(compact)).matches()||(m=RUN_K.matcher(compact)).matches()){
            String digits=m.group(1);String check=m.groupCount()>1?m.group(2):"K";
            var run=PersonIndex.run(digits,check);
            if(!run.empty()){
                String warning=dv(run.runNumber()).equals(run.runDv())?null:"El dígito verificador no corresponde a ese RUN: se busca igual por el número.";
                available.put(Kind.RUN,new Reading(Kind.RUN,run.runNumber(),List.of(),run.runNumber(),run.runDv(),null,null,documents(run.runNumber()+run.runDv(),run.runNumber()),List.of(),null,warning));
                detected.add(Kind.RUN);
            }
        }
        var document=PersonIndex.document(text);
        if(document.documentKey()!=null){
            available.put(Kind.DOCUMENTO,new Reading(Kind.DOCUMENTO,document.documentKey(),List.of(),null,null,null,null,List.of(document.documentKey()),List.of(),null,null));
            if(detected.isEmpty())detected.add(Kind.DOCUMENTO);
        }
        String name=PersonIndex.nameKey(text);
        if(name!=null&&name.matches(".*\\p{L}.*")){
            var words=Arrays.stream(name.split(" ")).distinct().toList();
            if(words.size()<=MAX_WORDS)available.put(Kind.NOMBRE,new Reading(Kind.NOMBRE,name,List.of(),null,null,null,null,List.of(),words,name,null));
            if(detected.isEmpty()){
                if(words.size()>MAX_WORDS)throw new Problem(400,"Usa hasta "+MAX_WORDS+" palabras para buscar por nombre.");
                detected.add(Kind.NOMBRE);
            }
        }
        if(detected.isEmpty())throw new Problem(400,"Escribe un RUN, un nombre, un documento o un folio.");
        return new Term(List.copyOf(detected),available);
    }

    // --- Filtros ----------------------------------------------------------------------------------
    /** Filtros para refinar: rol de la persona, solo abiertos, ambito (sede o unidad), region y periodo de recepcion. */
    record Filters(String role,boolean open,Long scope,String region,Long fromMs,Long toMs){
        static Filters of(String role,boolean open,Long scope,String region,String from,String to){
            if(role!=null&&!role.isBlank()&&!PersonIndex.ROLES.contains(role))throw new Problem(400,"Rol de persona desconocido.");
            if(region!=null&&!region.isBlank()&&!AdultIdentity.validRegion(region))throw new Problem(400,"Region desconocida.");
            Long f=day(from,false),t=day(to,true);
            if(f!=null&&t!=null&&f>=t)throw new Problem(400,"El periodo debe terminar despues de su inicio.");
            return new Filters(blank(role),open,scope,blank(region),f,t);
        }
        private static String blank(String v){return v==null||v.isBlank()?null:v;}
        /** aaaa-mm-dd en hora de Chile: inicio del dia (desde) o inicio del dia siguiente (hasta, inclusive). */
        private static Long day(String v,boolean end){
            if(v==null||v.isBlank())return null;
            try{var d=LocalDate.parse(v);return (end?d.plusDays(1):d).atStartOfDay(ZONE).toInstant().toEpochMilli();}
            catch(DateTimeParseException e){throw new Problem(400,"Fecha invalida: usa aaaa-mm-dd.");}
        }
        List<String> used(){
            var out=new ArrayList<String>();
            if(role!=null)out.add("rol");if(open)out.add("solo abiertos");if(scope!=null)out.add("ambito");if(region!=null)out.add("region");if(fromMs!=null||toMs!=null)out.add("periodo");
            return out;
        }
    }

    // --- Busqueda ---------------------------------------------------------------------------------
    /** Referencias legibles por la cuenta (dato de la COM o de la ficha). Expuesto para la prueba de equivalencia. */
    static final String REFERENCES="person_reference p JOIN communication c ON c.id=p.communication_id LEFT JOIN requirement r ON r.id=p.requirement_id";
    static String visible(BoardQueries.Visibility v){return "("+v.comRead("c")+" OR (p.source='FICHA' AND "+v.attentionRead("r")+"))";}

    public Map<String,Object> search(Actor a,String q,String as,Filters filters){
        // Solo cuentas con una funcion de lectura de expedientes (G08b): TI y AUDITORIA sola reciben 403.
        if(!a.readsRecords())Problem.denied();
        var term=term(q);List<Kind> kinds;
        if(as==null||as.isBlank())kinds=term.detected();
        else{
            Kind forced;
            try{forced=Kind.valueOf(as);}catch(IllegalArgumentException e){throw new Problem(400,"Tipo de busqueda desconocido.");}
            if(!term.available().containsKey(forced))throw new Problem(400,"Ese texto no se puede buscar como "+forced.name().toLowerCase(Locale.ROOT)+".");
            kinds=List.of(forced);
        }
        var v=boards.visibility(a);var scopes=scopes();
        var readings=new ArrayList<Map<String,Object>>();var notes=new ArrayList<String>();
        for(Kind k:kinds){
            var reading=term.available().get(k);
            var out=k==Kind.FOLIO?folio(v,reading,filters,scopes):people(v,reading,filters,scopes);
            readings.add(out);
            String note="Tipo "+k+"; resultados "+(out.get("too_many")==Boolean.TRUE?"mas de "+CAP:out.get("total"));
            if(key!=null)note+="; huella "+hmac(k+":"+reading.canonical());
            notes.add(note);
        }
        var used=filters.used();
        db.audit(a.id(),"BUSQUEDA","SISTEMA",0,null,String.join(" | ",notes)+(used.isEmpty()?"":"; filtros: "+String.join(", ",used)));
        var out=new LinkedHashMap<String,Object>();
        out.put("detected",term.detected());out.put("available",List.copyOf(term.available().keySet()));out.put("forced",as==null||as.isBlank()?null:as);
        out.put("limit",LIMIT);out.put("readings",readings);
        // G08c: la pantalla explica por que una cuenta (por ejemplo, solo LECTURA) no ve coincidencias de persona.
        out.put("can_find_people",a.findsPeople());
        return out;
    }
    String hmac(String canonical){
        try{
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("HMAC no disponible.",e);}
    }

    /** Coincidencia de una persona y su nivel de relevancia (1 RUN autenticado ... 5 nombre por palabras). */
    record Match(long com,String role,String source,String display,boolean authenticated,String reason,int level){
        Map<String,Object> json(){
            var m=new LinkedHashMap<String,Object>();
            m.put("role",role);m.put("source",source);m.put("display_name",display);m.put("authenticated",authenticated);m.put("reason",reason);
            m.put("exact",level==4);m.put("uncertain",reason.equals("NOMBRE"));
            return m;
        }
    }
    private Map<String,Object> people(BoardQueries.Visibility v,Reading x,Filters filters,Map<Long,Map<String,Object>> scopes){
        var where=new ArrayList<String>();var args=new ArrayList<Object>();
        switch(x.kind()){
            case RUN->{
                var any=new ArrayList<String>();
                any.add("p.run_number=?");args.add(x.runNumber());
                if(x.altNumber()!=null){any.add("(p.run_number=? AND p.run_dv=?)");args.add(x.altNumber());args.add(x.altDv());}
                any.add("p.document_key IN ("+String.join(",",Collections.nCopies(x.documents().size(),"?"))+")");args.addAll(x.documents());
                where.add("("+String.join(" OR ",any)+")");
            }
            case DOCUMENTO->{where.add("p.document_key=?");args.add(x.documents().getFirst());}
            case NOMBRE->{
                // Palabra completa o inicio de palabra: "ana" encuentra "ana" y "anabel", nunca "mariana".
                for(String w:x.words()){where.add("CONCAT(' ',p.name_key) LIKE ? ESCAPE '!'");args.add("% "+w.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
            }
            default->throw new IllegalStateException();
        }
        var rows=db.rows("SELECT p.communication_id,p.source,p.person_role,p.display_name,p.name_key,p.run_number,p.run_dv,p.document_key,p.authenticated FROM "+REFERENCES
            +" WHERE "+String.join(" AND ",where)+" AND "+visible(v)+" ORDER BY p.communication_id DESC,p.id LIMIT "+(CAP+1),args.toArray());
        if(rows.size()>CAP)return tooMany(x);
        var matches=rows.stream().map(r->match(x,r)).toList();
        var groups=new LinkedHashMap<Long,Group>();
        for(var m:matches)groups.computeIfAbsent(m.com(),Group::new).matches.add(m);
        return result(v,x,groups,filters,scopes,null);
    }
    private static Match match(Reading x,Map<String,Object> r){
        String reason;int level;boolean auth=b(r,"authenticated");
        switch(x.kind()){
            case RUN->{
                boolean run=x.runNumber().equals(s(r,"run_number"))||x.altNumber()!=null&&x.altNumber().equals(s(r,"run_number"))&&x.altDv().equals(s(r,"run_dv"));
                reason=run?"RUN":"DOCUMENTO";level=run?(auth?1:2):3;
            }
            case DOCUMENTO->{reason="DOCUMENTO";level=3;}
            default->{
                reason="NOMBRE";
                boolean exact=x.nameKey().equals(s(r,"name_key"))||x.nameKey().equals(PersonIndex.nameKey(s(r,"display_name")));
                level=exact?4:5;
            }
        }
        return new Match(n(r,"communication_id"),s(r,"person_role"),s(r,"source"),r.get("display_name")==null?null:s(r,"display_name"),auth,reason,level);
    }
    private Map<String,Object> folio(BoardQueries.Visibility v,Reading x,Filters filters,Map<Long,Map<String,Object>> scopes){
        String in=String.join(",",Collections.nCopies(x.folios().size(),"?"));Object[] args=x.folios().toArray();
        var hits=new ArrayList<Map<String,Object>>();
        hits.addAll(db.rows("SELECT c.id AS cid,'COM' AS kind,c.id AS rid,c.folio FROM communication c WHERE c.folio IN ("+in+") AND "+v.comRead("c"),args));
        hits.addAll(db.rows("SELECT c.id AS cid,'COM' AS kind,c.id AS rid,c.folio FROM formulario_reception f JOIN communication c ON c.id=f.communication_id WHERE f.folio IN ("+in+") AND "+v.comRead("c"),args));
        hits.addAll(db.rows("SELECT r.communication_id AS cid,'REQ' AS kind,r.id AS rid,r.folio FROM requirement r WHERE r.folio IN ("+in+") AND "+v.reqRead("r"),args));
        hits.addAll(db.rows("SELECT r.communication_id AS cid,'CAS' AS kind,c.id AS rid,c.folio FROM case_record c JOIN requirement r ON r.id=c.requirement_id WHERE c.folio IN ("+in+") AND "+v.caseRead("c"),args));
        var groups=new LinkedHashMap<Long,Group>();var seen=new HashSet<String>();var records=new ArrayList<Map<String,Object>>();
        for(var h:hits){
            if(!seen.add(s(h,"kind")+"#"+s(h,"rid")))continue;
            var record=Map.<String,Object>of("kind",s(h,"kind"),"id",n(h,"rid"),"folio",s(h,"folio"));
            records.add(record);groups.computeIfAbsent(n(h,"cid"),Group::new).folios.add(record);
        }
        return result(v,x,groups,filters,scopes,records.size()==1?records.getFirst():null);
    }
    private Map<String,Object> tooMany(Reading x){
        var out=head(x);out.put("too_many",true);out.put("total",null);out.put("more",true);out.put("groups",List.of());
        out.put("message","Hay más de "+CAP+" coincidencias. Agrega palabras, usa el RUN o un folio.");
        return out;
    }
    private static Map<String,Object> head(Reading x){
        var out=new LinkedHashMap<String,Object>();out.put("type",x.kind().name());out.put("warning",x.warning());out.put("too_many",false);
        return out;
    }

    // --- Cadena -----------------------------------------------------------------------------------
    /** Una COM del resultado con su cadena legible. */
    static final class Group{
        final long com;final List<Match> matches=new ArrayList<>();final List<Map<String,Object>> folios=new ArrayList<>();
        Map<String,Object> communication;final List<Map<String,Object>> sols=new ArrayList<>(),reqs=new ArrayList<>(),cases=new ArrayList<>(),flows=new ArrayList<>();
        Group(long com){this.com=com;}
        int level(){return matches.stream().mapToInt(Match::level).min().orElse(0);}
        /** Abierto: COM por clasificar, SOL pendiente o devuelta, REQ abierto o Caso no cerrado (solo lo legible). */
        boolean open(){
            return communication!=null&&s(communication,"state").equals("NUEVA")||sols.stream().anyMatch(x->Set.of("PENDIENTE","DEVUELTA").contains(s(x,"state")))
                ||reqs.stream().anyMatch(r->s(r,"state").equals("ABIERTO"))||cases.stream().anyMatch(c->!s(c,"state").equals("CERRADO"));
        }
        Set<Long> scopes(){
            var out=new LinkedHashSet<Long>();
            if(communication!=null)out.add(n(communication,"origin_scope"));
            sols.forEach(x->out.add(n(x,"destination_scope")));reqs.forEach(x->out.add(n(x,"principal_scope")));cases.forEach(x->out.add(n(x,"scope_id")));
            return out;
        }
        /** Fecha de recepcion de la COM legible o, si no se lee, la creacion del primer REQ o Caso legible. */
        Long date(){
            if(communication!=null)return n(communication,"received_ms");
            return java.util.stream.Stream.concat(reqs.stream(),cases.stream()).map(x->n(x,"created_ms")).min(Long::compare).orElse(null);
        }
    }
    private void chains(BoardQueries.Visibility v,Collection<Group> all){
        var byCom=new HashMap<Long,Group>();all.forEach(g->byCom.put(g.com,g));
        for(var ids:chunks(byCom.keySet())){
            String in=BoardQueries.ids(ids);
            for(var c:db.rows("SELECT c.id,c.folio,c.state,c.channel,c.received_ms,c.created_ms,c.origin_scope,c.external_source,c.created_by FROM communication c WHERE c.id IN ("+in+") AND "+v.comRead("c")))byCom.get(n(c,"id")).communication=c;
            for(var s:db.rows("SELECT s.id,s.communication_id,s.state,s.destination_scope,s.suggested_matter,s.created_ms FROM solicitation s JOIN communication c ON c.id=s.communication_id WHERE s.communication_id IN ("+in+") AND "+v.solChain("s","c")+" ORDER BY s.id"))byCom.get(n(s,"communication_id")).sols.add(s);
            String reqColumns="r.id,r.folio,r.state,r.principal_scope,r.attention_user,r.complexity,r.matter,r.created_ms,r.solicitation_id";
            for(var r:db.rows("SELECT r.communication_id AS cid,'ORIGEN' AS relation,"+reqColumns+" FROM requirement r WHERE r.communication_id IN ("+in+") AND "+v.reqRead("r")
                +" UNION ALL SELECT l.communication_id AS cid,'ANTECEDENTE' AS relation,"+reqColumns+" FROM communication_link l JOIN requirement r ON r.id=l.requirement_id WHERE l.communication_id IN ("+in+") AND l.state='ADMITIDA' AND l.returned_ms IS NULL AND "+v.reqRead("r")))
                byCom.get(n(r,"cid")).reqs.add(r);
        }
        var byReq=new HashMap<Long,List<Group>>();
        for(var g:all)for(var r:g.reqs)byReq.computeIfAbsent(n(r,"id"),k->new ArrayList<>()).add(g);
        for(var ids:chunks(byReq.keySet())){
            String in=BoardQueries.ids(ids);
            for(var c:db.rows("SELECT c.id,c.folio,c.state,c.requirement_id,c.scope_id,c.responsible_id,c.matter,c.created_ms FROM case_record c WHERE c.requirement_id IN ("+in+") AND "+v.caseRead("c")+" ORDER BY c.id"))
                byReq.get(n(c,"requirement_id")).forEach(g->g.cases.add(c));
            for(var f:db.rows("SELECT f.id,f.kind,f.requirement_id,f.origin_scope,f.target_scope,f.state,f.created_ms FROM flow_request f WHERE f.requirement_id IN ("+in+") AND f.state='PENDIENTE' AND "+v.flowRead("f")+" ORDER BY f.id"))
                byReq.get(n(f,"requirement_id")).forEach(g->g.flows.add(f));
        }
    }
    private static List<Collection<Long>> chunks(Collection<Long> ids){
        var list=new ArrayList<>(ids);var out=new ArrayList<Collection<Long>>();
        for(int i=0;i<list.size();i+=CHUNK)out.add(list.subList(i,Math.min(list.size(),i+CHUNK)));
        return out;
    }
    private Map<Long,Map<String,Object>> scopes(){
        var out=new HashMap<Long,Map<String,Object>>();
        for(var s:db.rows("SELECT id,code,name,unit_code,regional,region_code,state FROM org_scope")){
            var m=new LinkedHashMap<String,Object>();
            m.put("id",n(s,"id"));m.put("code",s(s,"code"));m.put("name",s(s,"name"));m.put("unit_code",s(s,"unit_code"));
            m.put("level",b(s,"regional")?"REGIONAL":"CENTRAL");m.put("region_code",s.get("region_code"));
            m.put("region_name",s.get("region_code")==null?null:AdultIdentity.REGIONS.get(s(s,"region_code")));m.put("state",s(s,"state"));
            out.put(n(s,"id"),m);
        }
        return out;
    }

    private Map<String,Object> result(BoardQueries.Visibility v,Reading x,Map<Long,Group> groups,Filters filters,Map<Long,Map<String,Object>> scopes,Map<String,Object> exact){
        chains(v,groups.values());
        boolean folio=x.kind()==Kind.FOLIO;
        // Filtros para refinar, sobre todo lo legible antes de aplicarlos (cada grupo cuenta una vez por valor).
        var roles=new TreeMap<String,Integer>();var regions=new TreeMap<String,Integer>();var scopeFacet=new TreeMap<Long,Integer>();int open=0;
        for(var g:groups.values()){
            g.matches.stream().map(Match::role).distinct().forEach(r->roles.merge(r,1,Integer::sum));
            var ids=g.scopes();ids.forEach(id->scopeFacet.merge(id,1,Integer::sum));
            ids.stream().map(id->scopes.get(id)).filter(Objects::nonNull).map(sc->sc.get("region_code")).filter(Objects::nonNull).map(Object::toString).distinct().forEach(r->regions.merge(r,1,Integer::sum));
            if(g.open())open++;
        }
        var kept=new ArrayList<Group>();
        for(var g:groups.values()){
            if(filters.role()!=null&&!folio){g.matches.removeIf(m->!m.role().equals(filters.role()));if(g.matches.isEmpty())continue;}
            if(filters.open()&&!g.open())continue;
            if(filters.scope()!=null&&!g.scopes().contains(filters.scope()))continue;
            if(filters.region()!=null&&g.scopes().stream().map(scopes::get).noneMatch(sc->sc!=null&&filters.region().equals(sc.get("region_code"))))continue;
            Long date=g.date();
            if(filters.fromMs()!=null&&(date==null||date<filters.fromMs()))continue;
            if(filters.toMs()!=null&&(date==null||date>=filters.toMs()))continue;
            kept.add(g);
        }
        kept.sort(Comparator.comparingInt(Group::level).thenComparing(g->g.open()?0:1).thenComparing(g->-g.com));
        var shown=kept.subList(0,Math.min(LIMIT,kept.size()));

        var out=head(x);
        out.put("total",kept.size());out.put("more",kept.size()>LIMIT);
        if(kept.size()>LIMIT)out.put("message","Hay "+kept.size()+" resultados; se muestran los "+LIMIT+" más relevantes. Refina la búsqueda.");
        var facets=new LinkedHashMap<String,Object>();
        facets.put("roles",folio?Map.of():roles);facets.put("open",open);facets.put("closed",groups.size()-open);facets.put("regions",regions);
        facets.put("scopes",scopeFacet.entrySet().stream().map(e->Map.of("scope",scopes.get(e.getKey()),"count",e.getValue())).toList());
        out.put("facets",facets);
        if(folio)out.put("exact",exact);
        if(x.kind()==Kind.RUN||x.kind()==Kind.DOCUMENTO){out.put("summary",summary(kept));out.put("person",person(x,kept));}
        out.put("groups",render(shown,scopes));
        return out;
    }
    /** Totales de lo legible (todas las COM del resultado filtrado, no solo las mostradas); una COM abierta es la de un grupo abierto. */
    private static Map<String,Object> summary(List<Group> groups){
        var reqs=new HashMap<Long,Map<String,Object>>();var cases=new HashMap<Long,Map<String,Object>>();long com=0,comOpen=0;
        for(var g:groups){
            if(g.communication!=null){com++;if(g.open())comOpen++;}
            g.reqs.forEach(r->reqs.put(n(r,"id"),r));g.cases.forEach(c->cases.put(n(c,"id"),c));
        }
        var out=new LinkedHashMap<String,Object>();
        out.put("com",com);out.put("com_open",comOpen);
        out.put("req",reqs.size());out.put("req_open",reqs.values().stream().filter(r->s(r,"state").equals("ABIERTO")).count());
        out.put("cas",cases.size());out.put("cas_open",cases.values().stream().filter(c->!s(c,"state").equals("CERRADO")).count());
        return out;
    }
    /** Encabezado de la persona buscada por RUN o documento: nombres declarados (hasta 5) y si alguna coincidencia esta autenticada. */
    private static Map<String,Object> person(Reading x,List<Group> groups){
        var names=new LinkedHashSet<String>();boolean authenticated=false;
        for(var g:groups)for(var m:g.matches){
            if(m.reason().equals("NOMBRE"))continue;
            if(m.display()!=null&&names.size()<5)names.add(m.display());
            authenticated|=m.authenticated()&&m.reason().equals("RUN");
        }
        var out=new LinkedHashMap<String,Object>();
        out.put("run_number",x.kind()==Kind.RUN?x.runNumber():null);out.put("run_dv",x.kind()==Kind.RUN?x.runDv():null);
        out.put("document",x.kind()==Kind.DOCUMENTO?x.canonical():null);out.put("names",List.copyOf(names));out.put("authenticated",authenticated);
        return out;
    }
    private List<Map<String,Object>> render(List<Group> shown,Map<Long,Map<String,Object>> scopes){
        var reqIds=new HashSet<Long>();var caseIds=new HashSet<Long>();var staffIds=new HashSet<Long>();
        for(var g:shown){
            for(var r:g.reqs){reqIds.add(n(r,"id"));if(r.get("attention_user")!=null)staffIds.add(n(r,"attention_user"));}
            for(var c:g.cases){caseIds.add(n(c,"id"));staffIds.add(n(c,"responsible_id"));}
            if(g.communication!=null)staffIds.add(n(g.communication,"created_by"));
        }
        // Las identidades tecnicas (Formulario, actualizacion) no son responsables: la pantalla indica el canal externo.
        var names=new HashMap<Long,String>();
        for(var ids:chunks(staffIds))for(var u:db.rows("SELECT id,name,technical FROM staff WHERE id IN ("+BoardQueries.ids(ids)+")"))if(!b(u,"technical"))names.put(n(u,"id"),s(u,"name"));
        var last=new HashMap<String,Long>();
        if(!reqIds.isEmpty()||!caseIds.isEmpty())
            for(var e:db.rows("SELECT resource_type,resource_id,MAX(at_ms) AS last_ms FROM audit_event WHERE ((resource_type='REQ' AND resource_id IN ("+BoardQueries.ids(reqIds)+")) OR (resource_type='CAS' AND resource_id IN ("+BoardQueries.ids(caseIds)+"))) AND action NOT IN ("+READ_ACTIONS+") GROUP BY resource_type,resource_id"))
                last.put(s(e,"resource_type")+"#"+n(e,"resource_id"),n(e,"last_ms"));
        Function<Object,Object> scope=id->id==null?null:scopes.get(((Number)id).longValue());
        var out=new ArrayList<Map<String,Object>>();
        for(var g:shown){
            var m=new LinkedHashMap<String,Object>();
            m.put("level",g.level());m.put("open",g.open());
            if(g.communication!=null){
                var c=g.communication;var com=new LinkedHashMap<String,Object>();
                com.put("id",n(c,"id"));com.put("folio",s(c,"folio"));com.put("state",s(c,"state"));com.put("channel",s(c,"channel"));com.put("external",c.get("external_source")!=null);
                com.put("received_ms",c.get("received_ms"));com.put("origin",scope.apply(c.get("origin_scope")));com.put("registered_by",names.get(n(c,"created_by")));
                m.put("communication",com);
            }else m.put("communication",null);
            m.put("solicitations",g.sols.stream().map(s->{var x=new LinkedHashMap<String,Object>();x.put("id",n(s,"id"));x.put("state",s(s,"state"));var dest=scopes.get(n(s,"destination_scope"));x.put("destination",dest);
                x.put("receiver",dest==null?null:Access.receiverLabel("REGIONAL".equals(dest.get("level")),s(dest,"name"),s(dest,"code"),s(dest,"unit_code")));x.put("suggested_matter",s.get("suggested_matter"));x.put("created_ms",s.get("created_ms"));return (Map<String,Object>)x;}).toList());
            m.put("requirements",g.reqs.stream().map(r->{
                var x=new LinkedHashMap<String,Object>();long id=n(r,"id");
                x.put("id",id);x.put("folio",s(r,"folio"));x.put("state",s(r,"state"));x.put("relation",s(r,"relation"));x.put("complexity",r.get("complexity"));x.put("matter",r.get("matter"));
                x.put("principal",scope.apply(r.get("principal_scope")));x.put("attention_user",r.get("attention_user")==null?null:names.get(n(r,"attention_user")));
                x.put("origin",r.get("solicitation_id")==null?"ATENCION_DIRECTA":"SOLICITUD");
                x.put("created_ms",r.get("created_ms"));x.put("last_ms",last.getOrDefault("REQ#"+id,n(r,"created_ms")));
                return (Map<String,Object>)x;
            }).toList());
            m.put("cases",g.cases.stream().map(c->{
                var x=new LinkedHashMap<String,Object>();long id=n(c,"id");
                x.put("id",id);x.put("folio",s(c,"folio"));x.put("state",s(c,"state"));x.put("requirement_id",n(c,"requirement_id"));x.put("matter",c.get("matter"));
                x.put("scope",scope.apply(c.get("scope_id")));x.put("responsible",names.get(n(c,"responsible_id")));
                x.put("created_ms",c.get("created_ms"));x.put("last_ms",last.getOrDefault("CAS#"+id,n(c,"created_ms")));
                return (Map<String,Object>)x;
            }).toList());
            m.put("flows",g.flows.stream().map(f->{var x=new LinkedHashMap<String,Object>();x.put("id",n(f,"id"));x.put("kind",s(f,"kind"));x.put("requirement_id",n(f,"requirement_id"));x.put("origin",scope.apply(f.get("origin_scope")));x.put("target",scope.apply(f.get("target_scope")));x.put("created_ms",f.get("created_ms"));return (Map<String,Object>)x;}).toList());
            m.put("matches",g.matches.stream().sorted(Comparator.comparingInt(Match::level)).map(Match::json).toList());
            m.put("folio_matches",g.folios);
            out.add(m);
        }
        return out;
    }
}
