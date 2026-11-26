package cl.nexbyte.sigera;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.Access.Actor;
import static cl.nexbyte.sigera.Db.*;

/** Series agregadas de fuentes históricas. La importación y la anulación pasan por Workflow. */
@Service
final class StatSeries {
    static final List<String> COLUMNS=List.of("periodo_desde","periodo_hasta","dimension","categoria","valor");
    static final String ACTIVE="VIGENTE",VOID="ANULADA";
    private static final Set<String> FIELDS=Set.of("title","indicator","scope_id","source","methodology","granularity","period_from","period_to","reason","format","content","columns","mappings");
    private static final int MAX_COLUMNS=256,MAX_CELL=12_000;
    private final Db db;
    private final JdbcTemplate jdbc;
    private final Reports reports;
    private final StatCuts cuts;
    private final StatEngine engine;
    private final XlsxReader xlsx;
    private final int bytes,rows;

    StatSeries(Db db,Reports reports,StatCuts cuts,StatEngine engine,
        @Value("${siger.stats.series.max-file-bytes:2097152}")int bytes,
        @Value("${siger.stats.series.max-rows:20000}")int rows,
        @Value("${siger.stats.series.xlsx.max-entries:96}")int entries,
        @Value("${siger.stats.series.xlsx.max-expanded-bytes:33554432}")int expanded,
        @Value("${siger.stats.series.xlsx.max-compression-ratio:160}")int ratio){
        if(bytes<1||bytes>12_000_000)throw new IllegalArgumentException("Límite de archivo de serie inválido.");
        this.db=db;this.jdbc=new JdbcTemplate(db.dataSource());this.reports=reports;this.cuts=cuts;this.engine=engine;
        this.bytes=bytes;this.rows=rows;this.xlsx=new XlsxReader(entries,expanded,ratio,rows);
    }

    record Meta(Long scope,String title,String indicator,String source,String methodology,String granularity,LocalDate from,LocalDate to,String reason){}
    record Category(String dimension,String label){}
    record Period(LocalDate from,LocalDate to){}
    record SeriesValue(Period period,Category category,String code,boolean total,long amount){
        Map<String,Object> display(){
            var out=new LinkedHashMap<String,Object>();out.put("period_from",period.from().toString());out.put("period_to",period.to().toString());
            out.put("dimension",category.dimension());out.put("category_label",category.label());out.put("category_code",code);
            out.put("is_total",total);out.put("value",amount);return out;
        }
    }
    record Data(Meta meta,List<String> headers,List<SeriesValue> values,String hash){}
    private record Group(Period period,String dimension){}

    Workflow.Result run(Actor a,String type,long id,Long revision,Input input){
        return switch(type){
            case "STAT_SERIES_IMPORT"->importSeries(a,input);
            case "STAT_SERIES_ANNUL"->annul(a,id,revision,input);
            default->throw failure("Comando desconocido.");
        };
    }

    Map<String,Object> preview(Actor a,Map<String,Object> body){
        var data=parse(a,new Input(body),true);
        var m=data.meta();
        var out=new LinkedHashMap<String,Object>();
        out.put("headers",data.headers());out.put("needs_columns",data.values()==null);out.put("file_sha256",data.hash());
        if(data.values()!=null){
            out.put("values",data.values().stream().map(SeriesValue::display).toList());
            out.put("warnings",overlap(m)?List.of(Map.of("code","PERIODOS_SUPERPUESTOS")):List.of());
            var suggestion=new Category("ADSCRIPCION_INICIAL","Metropolitana");
            if(data.values().stream().anyMatch(v->v.category().equals(suggestion)&&v.code()==null))
                out.put("suggested_mappings",List.of(Map.of("dimension",suggestion.dimension(),"category",suggestion.label(),"code","OFICINA_NACIONAL")));
        }
        return out;
    }

    private Workflow.Result importSeries(Actor a,Input input){
        var data=parse(a,input,false);var m=data.meta();
        long id=db.insert("INSERT INTO stat_series(title,indicator,scope_id,source,methodology,granularity,period_from,period_to,imported_by,imported_ms,basis,file_sha256,state,revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
            m.title(),m.indicator(),m.scope(),m.source(),m.methodology(),m.granularity(),m.from().toString(),m.to().toString(),a.id(),Lifecycle.now(),m.reason(),data.hash(),ACTIVE);
        var rows=new ArrayList<Object[]>();int ordinal=0;
        for(var v:data.values())rows.add(new Object[]{id,++ordinal,v.period().from().toString(),v.period().to().toString(),v.category().dimension(),v.category().label(),v.code(),v.total(),v.amount()});
        jdbc.batchUpdate("INSERT INTO stat_series_value(series_id,ordinal,period_from,period_to,dimension,category_label,category_code,is_total,amount) VALUES(?,?,?,?,?,?,?,?,?)",rows);
        db.audit(a.id(),"SERIE_IMPORTADA","SISTEMA",0,null,"Serie "+id);
        return Workflow.done("SERIE",id);
    }
    private Workflow.Result annul(Actor a,long id,Long revision,Input input){
        var grants=managers(a);
        if(!Set.of("reason").equals(input.values().keySet()))throw failure("Indica el fundamento de la anulación.");
        String reason=input.reason();var row=db.one("SELECT id,scope_id,state,revision FROM stat_series WHERE id=?",id);
        var reach=cuts.reach(nullableId(row,"scope_id"));
        if(grants.stream().noneMatch(g->StatCuts.covers(reports.reach(List.of(g)),reach)))Problem.denied();
        Db.revision(row,revision);
        if(!s(row,"state").equals(ACTIVE))throw new Problem(409,"La serie ya está anulada.");
        db.update("UPDATE stat_series SET state=?,annulled_by=?,annulled_ms=?,annul_basis=?,revision=revision+1 WHERE id=?",VOID,a.id(),Lifecycle.now(),reason,id);
        db.audit(a.id(),"SERIE_ANULADA","SISTEMA",0,null,"Serie "+id);
        return Workflow.done("SERIE",id);
    }

    private Data parse(Actor a,Input in,boolean preview){
        var grants=managers(a);
        if(!FIELDS.containsAll(in.values().keySet()))throw failure("Serie: hay campos no reconocidos.");
        Long scope=in.optionalId("scope_id");
        if(grants.stream().noneMatch(g->Objects.equals(g.scope(),scope)))Problem.denied();
        if(scope!=null&&!b(db.one("SELECT regional FROM org_scope WHERE id=?",scope),"regional"))throw failure("Elige una sede o el alcance institucional.");
        var m=new Meta(scope,in.text("title",200,true),in.text("indicator",40,true),in.text("source",300,true),in.text("methodology",12_000,true),
            in.choice("granularity","PERSONALIZADO","DIA","MES","TRIMESTRE","SEMESTRE","ANIO","CUENTA_PUBLICA"),day(in.text("period_from",10,true)),day(in.text("period_to",10,true)),in.reason());
        if(m.from().isAfter(m.to()))throw failure("El periodo de la serie está invertido.");
        String format=in.choice("format","csv","xlsx");
        String content=in.text("content",4*((bytes+2)/3),true);
        byte[] file;
        try{file=Base64.getDecoder().decode(content);}catch(IllegalArgumentException e){throw failure("El archivo no tiene un formato válido.");}
        if(file.length>bytes)throw failure("El archivo supera el tamaño admitido.");
        var grid=format.equals("csv")?csv(file,rows):xlsx.read(file);
        var headers=grid.getFirst().stream().map(String::strip).toList();
        if(headers.stream().anyMatch(String::isBlank)||new HashSet<>(headers).size()!=headers.size())throw failure("Usa encabezados de columna únicos y completos.");
        String hash=StatExport.sha(file);
        if(preview&&in.values().get("columns")==null&&!headers.containsAll(COLUMNS))return new Data(m,headers,null,hash);
        var columns=columns(headers,in.values().get("columns"));
        return new Data(m,headers,values(grid.subList(1,grid.size()),columns,m,mappings(in)),hash);
    }

    private static int[] columns(List<String> headers,Object raw){
        var custom=raw==null?Map.of():object(raw);
        if(!COLUMNS.containsAll(custom.keySet()))throw failure("La correspondencia de columnas tiene campos no reconocidos.");
        var used=new HashSet<Integer>();int[] result=new int[COLUMNS.size()];
        for(int i=0;i<result.length;i++){
            String name=COLUMNS.get(i);
            Object header=custom.containsKey(name)?custom.get(name):name;
            int pos=headers.indexOf(header);
            if(pos<0||!used.add(pos))throw failure("Asocia una columna distinta a cada campo de la plantilla.");
            result[i]=pos;
        }
        return result;
    }
    private Map<Category,String> mappings(Input in){
        if(!in.values().containsKey("mappings"))return Map.of();
        var mappings=new HashMap<Category,String>();
        for(var x:in.list("mappings",1000)){
            if(!x.values().keySet().equals(Set.of("dimension","category","code")))throw failure("Revisa los campos de la correspondencia de categorías.");
            var category=new Category(x.text("dimension",40,true),x.text("category",200,true));
            String code=x.text("code",200,true);
            engine.validateSeriesCode(category.dimension(),code);
            if(mappings.putIfAbsent(category,code)!=null)throw failure("Una categoría tiene más de una correspondencia.");
        }
        return mappings;
    }
    private static List<SeriesValue> values(List<List<String>> grid,int[] columns,Meta m,Map<Category,String> mappings){
        var values=new ArrayList<SeriesValue>();var groups=new HashMap<Group,Long>();var totals=new HashMap<Group,Long>();
        var unique=new HashSet<List<Object>>();var found=new HashSet<Category>();var periods=new TreeMap<LocalDate,LocalDate>();
        int line=1;
        for(var row:grid){
            line++;
            if(row.stream().allMatch(String::isBlank))continue;
            try{
                var period=new Period(day(at(row,columns[0])),day(at(row,columns[1])));
                if(period.from().isAfter(period.to())||period.from().isBefore(m.from())||period.to().isAfter(m.to()))throw failure("Periodo fuera del intervalo declarado.");
                if(!m.granularity().equals("PERSONALIZADO")&&(!StatEngine.start(m.granularity(),period.from()).equals(period.from())||!StatEngine.next(m.granularity(),period.from()).minusDays(1).equals(period.to())))throw failure("Periodo distinto de la granularidad declarada.");
                var previous=periods.putIfAbsent(period.from(),period.to());
                if(previous!=null&&!previous.equals(period.to()))throw failure("Periodos superpuestos dentro del archivo.");
                var category=new Category(field(at(row,columns[2]),40),field(at(row,columns[3]),200));
                boolean total=category.label().equalsIgnoreCase("TOTAL");
                String number=at(row,columns[4]);
                if(!number.matches("[0-9]{1,19}"))throw failure("El valor debe ser un entero no negativo.");
                long value=Long.parseLong(number);
                if(!unique.add(List.of(period,category.dimension(),total?"TOTAL":category.label())))throw failure("Categoría repetida en el mismo periodo.");
                var group=new Group(period,category.dimension());
                if(total)totals.put(group,value);else {groups.merge(group,value,Math::addExact);found.add(category);}
                values.add(new SeriesValue(period,category,total?null:mappings.get(category),total,value));
            }catch(ArithmeticException|NumberFormatException e){throw failure("Fila "+line+": el valor o su suma supera el entero admitido.");}
            catch(Problem p){throw failure("Fila "+line+": "+p.getMessage());}
        }
        if(values.isEmpty())throw failure("El archivo no contiene filas de datos.");
        LocalDate end=null;
        for(var p:periods.entrySet()){
            if(end!=null&&!p.getKey().isAfter(end))throw failure("Hay periodos superpuestos dentro del archivo.");
            end=p.getValue();
        }
        for(var t:totals.entrySet())if(groups.containsKey(t.getKey())&&!Objects.equals(groups.get(t.getKey()),t.getValue()))throw failure("Un total declarado no coincide con la suma de sus categorías.");
        if(!found.containsAll(mappings.keySet()))throw failure("Una correspondencia señala una categoría ausente en el archivo.");
        return List.copyOf(values);
    }

    private static LocalDate day(String text){
        try{var d=LocalDate.parse(text);if(d.getYear()>=2000&&d.getYear()<=2099&&text.length()==10)return d;}
        catch(DateTimeParseException ignored){ }
        throw failure("Usa una fecha válida aaaa-mm-dd entre 2000 y 2099.");
    }
    private static String at(List<String> row,int index){return index<row.size()?row.get(index).strip():"";}
    private static String field(String value,int max){
        if(value.isEmpty()||value.length()>max||value.chars().anyMatch(Character::isISOControl))throw failure("Campo de categoría o dimensión no válido.");
        return value;
    }
    private static Map<?,?> object(Object raw){
        if(raw instanceof Map<?,?> m)return m;
        throw failure("Revisa los campos enviados.");
    }
    private boolean overlap(Meta m){
        String scope=m.scope()==null?"scope_id IS NULL":"scope_id=?";
        var args=new ArrayList<Object>(List.of(m.indicator(),ACTIVE,m.to().toString(),m.from().toString()));
        if(m.scope()!=null)args.add(m.scope());
        return db.count("SELECT COUNT(*) FROM stat_series WHERE indicator=? AND state=? AND period_from<=? AND period_to>=? AND "+scope,args.toArray())>0;
    }

    List<Map<String,Object>> list(Actor a){
        var reach=viewer(a);
        return db.rows("SELECT id,title,indicator,scope_id,source,methodology,granularity,period_from,period_to,imported_by,imported_ms,basis,file_sha256,state,revision,annulled_by,annulled_ms,annul_basis FROM stat_series ORDER BY id DESC")
            .stream().filter(r->StatCuts.covers(reach,cuts.reach(nullableId(r,"scope_id")))).toList();
    }
    Map<String,Object> detail(Actor a,long id){
        var row=readable(a,id);
        db.audit(a.id(),"STAT_CONSULTA","SISTEMA",0,null,"Serie "+id);
        return withValues(row,id);
    }
    Map<String,Object> readable(Actor a,long id){
        var reach=viewer(a);
        var row=db.one("SELECT * FROM stat_series WHERE id=?",id);
        if(!StatCuts.covers(reach,cuts.reach(nullableId(row,"scope_id"))))Problem.denied();
        return row;
    }
    List<Map<String,Object>> values(long id){
        var rows=db.rows("SELECT period_from,period_to,dimension,category_label,category_code,is_total,amount FROM stat_series_value WHERE series_id=? ORDER BY ordinal",id);
        for(var r:rows)r.put("value",r.remove("amount"));
        return rows;
    }
    private Map<String,Object> withValues(Map<String,Object> row,long id){var out=new LinkedHashMap<>(row);out.put("values",values(id));return out;}
    private Reports.Reach viewer(Actor a){
        if(!StatEngine.allowed(a))Problem.denied();
        return reports.reach(a.grants().stream().filter(g->g.role().equals(Roles.REPORTES)).toList());
    }
    private static List<Access.Grant> managers(Actor a){
        var m=Roles.ruling(a,Roles.GESTION_ESTADISTICA);
        if(a.role(Roles.TI)||m.isEmpty())Problem.denied();
        return m;
    }

    Map<String,Object> compare(Actor a,Map<String,Object> body){
        viewer(a);
        if(body==null||!Set.of("series_ids","sum","query").containsAll(body.keySet())||!(body.get("series_ids") instanceof List<?> ids)||ids.isEmpty()||ids.size()>20)
            throw failure("Indica entre una y veinte series para comparar.");
        if(body.containsKey("sum")&&!(body.get("sum") instanceof Boolean))throw failure("Indica si quieres sumar las series.");
        var selected=new ArrayList<Map<String,Object>>();var unique=new HashSet<Long>();
        for(var id:ids){
            long key=new Input(Map.of("id",id==null?"":id)).id("id");
            if(!unique.add(key))throw failure("Elige cada serie una sola vez.");
            selected.add(withValues(readable(a,key),key));
        }
        var warnings=new ArrayList<Map<String,Object>>();
        boolean overlap=overlapping(selected);
        if(overlap)warnings.add(Map.of("code","PERIODOS_SUPERPUESTOS"));
        boolean compatible=compatible(selected);
        if(!compatible)warnings.add(Map.of("code","SERIES_INCOMPATIBLES"));
        var out=new LinkedHashMap<String,Object>();out.put("series",selected);out.put("warnings",warnings);
        out.put("sum",Boolean.TRUE.equals(body.get("sum"))&&compatible&&!overlap?sum(selected):null);
        if(body.get("query")!=null){
            var query=new LinkedHashMap<String,Object>();object(body.get("query")).forEach((k,v)->query.put(String.valueOf(k),v));
            var plan=engine.parse(a,query);var reach=StatEngine.effectiveReach(plan);
            if(selected.stream().map(r->cuts.reach(nullableId(r,"scope_id")))
                .anyMatch(x->!StatCuts.covers(x,reach)||!StatCuts.covers(reach,x)))warnings.add(Map.of("code","ALCANCES_DISTINTOS"));
            out.put("siger",engine.query(a,query));
        }
        for(long id:unique)db.audit(a.id(),"STAT_CONSULTA","SISTEMA",0,null,"Serie "+id);
        return out;
    }
    private static boolean overlapping(List<Map<String,Object>> series){
        var periods=series.stream().sorted(Comparator.comparing(r->s(r,"period_from"))).toList();
        String end="";
        for(var p:periods){if(s(p,"period_from").compareTo(end)<=0)return true;end=s(p,"period_to");}
        return false;
    }
    private static boolean compatible(List<Map<String,Object>> series){
        var first=series.getFirst();var fields=List.of("indicator","scope_id","methodology","granularity");
        Set<String> dimensions=dimensions(first);
        return series.stream().allMatch(r->s(r,"state").equals(ACTIVE)&&fields.stream().allMatch(k->Objects.equals(r.get(k),first.get(k)))&&dimensions(r).equals(dimensions));
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> entries(Map<String,Object> series){return (List<Map<String,Object>>)series.get("values");}
    private static Set<String> dimensions(Map<String,Object> series){
        var set=new HashSet<String>();entries(series).forEach(r->set.add(s(r,"dimension")));return set;
    }
    private static List<Map<String,Object>> sum(List<Map<String,Object>> selected){
        record SumKey(String dimension,String key,boolean total){}
        record PeriodDimension(String from,String to,String dimension){}
        var sums=new TreeMap<SumKey,Long>(Comparator.comparing(SumKey::dimension).thenComparing(SumKey::total).thenComparing(SumKey::key));
        try{
            for(var series:selected){
                var declared=new HashMap<PeriodDimension,Long>();var calculated=new HashMap<PeriodDimension,Long>();
                for(var row:entries(series)){
                    String dimension=s(row,"dimension");long value=n(row,"value");
                    var group=new PeriodDimension(s(row,"period_from"),s(row,"period_to"),dimension);
                    if(b(row,"is_total"))declared.put(group,value);
                    else{
                        calculated.merge(group,value,Math::addExact);
                        String key=row.get("category_code")==null?s(row,"category_label"):s(row,"category_code");
                        sums.merge(new SumKey(dimension,key,false),value,Math::addExact);
                    }
                }
                declared.forEach(calculated::put);
                calculated.forEach((p,total)->sums.merge(new SumKey(p.dimension(),"Total",true),total,Math::addExact));
            }
        }catch(ArithmeticException e){throw failure("La suma supera el entero admitido.");}
        return sums.entrySet().stream().map(e->Map.<String,Object>of("dimension",e.getKey().dimension(),"category",e.getKey().key(),"is_total",e.getKey().total(),"value",e.getValue())).toList();
    }

    static List<List<String>> csv(byte[] bytes,int maxRows){
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw failure("El archivo CSV debe estar en UTF-8.");}
        if(text.startsWith("\ufeff"))text=text.substring(1);
        char sep=separator(text);var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var value=new StringBuilder();
        boolean quoted=false,closed=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quoted){
                if(c!='"')value.append(c);
                else if(i+1<text.length()&&text.charAt(i+1)=='"'){value.append('"');i++;}
                else{quoted=false;closed=true;}
            }else if(c==sep||c=='\r'||c=='\n'){
                add(row,value);closed=false;
                if(c!=sep){append(rows,row,maxRows);row=new ArrayList<>();if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;}
            }else if(c=='"'&&!closed&&value.isEmpty())quoted=true;
            else if(closed||c=='"'||c==0)throw failure("El CSV contiene una celda mal delimitada.");
            else value.append(c);
            if(value.length()>MAX_CELL)throw failure("Una celda del CSV es demasiado larga.");
        }
        if(quoted)throw failure("El CSV contiene una celda sin cerrar.");
        if(!row.isEmpty()||!value.isEmpty()||closed){add(row,value);append(rows,row,maxRows);}
        if(rows.isEmpty())throw failure("El archivo CSV está vacío.");
        return rows;
    }
    private static char separator(String text){
        boolean quoted=false;int comma=0,semicolon=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='"')quoted=!quoted;
            if(!quoted){if(c=='\n'||c=='\r')break;if(c==';')semicolon++;if(c==',')comma++;}
        }
        return semicolon>=comma?';':',';
    }
    private static void add(List<String> row,StringBuilder value){
        if(row.size()>=MAX_COLUMNS)throw failure("El CSV contiene demasiadas columnas.");
        row.add(value.toString());value.setLength(0);
    }
    private static void append(List<List<String>> rows,List<String> row,int limit){
        if(rows.size()>=limit)throw failure("El CSV supera el máximo de filas.");
        rows.add(row);
    }
    private static Problem failure(String message){return new Problem(400,message);}
}
