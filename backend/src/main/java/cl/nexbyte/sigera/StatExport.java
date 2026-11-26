package cl.nexbyte.sigera;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;
import cl.nexbyte.sigera.Access.Actor;
import cl.nexbyte.sigera.StatSource.*;
import static cl.nexbyte.sigera.Db.*;

/** Descargas de resultados del motor y de series agregadas. Construye el archivo íntegro antes de responder. */
@Service
final class StatExport {
    private static final String GROUP="GRUPO_PRIORITARIO",GROUP_STATE="ESTADO_"+GROUP;
    private static final String AUDIT="STAT_EXPORTACION";
    private static final Set<Population> MICRO_POPULATION=Set.of(Population.COMPUTABLE,Population.REQUERIMIENTO);
    private static final Set<String> COMMUNES=AdultIdentity.COMMUNES.values().stream().flatMap(Collection::stream).collect(Collectors.toUnmodifiableSet());
    private static final List<String> RELATED=List.of("folio","variable","codigo","rotulo","nivel","en_req","casos","menciones");
    private static final List<String> CATALOGS=List.of("catalogo","version","codigo","rotulo","padre","nivel","seleccionable","descripcion");
    private static final List<String> DICTIONARY=List.of("tipo","dimension","codigo","rotulo","definicion");
    private static final List<String> PERSON_DIMENSIONS=List.of("REGION_NNA","COMUNA_NNA","EDAD_NNA","NACIONALIDAD_NNA","TRAMO_EDAD_NNA","GRUPO_NACIONALIDAD_NNA");
    private static final String NOTE="Un requerimiento puede tener varios valores. El total único cuenta cada COM una vez; los márgenes no suman las celdas.";
    private static final String MENTIONS="En las clasificaciones, el archivo relacionado conserva todas las asignaciones por valor. El motor cuenta una mención por COM y valor; en personas, instituciones y actuaciones cuenta las ocurrencias.";
    private static final String RESERVED="Las constancias reservadas se excluyen después de identificar la versión vigente. No cuentan en los agregados de actuaciones.";
    private static final String PRIVACY="Los folios y los cruces de categorías infrecuentes conservan un riesgo residual de reidentificación. Uso interno según las facultades de la cuenta.";
    private static final String OMIT="Estado y valores de grupos prioritarios omitidos del microdato. Requieren REPORTES_PERSONAS y EXPORTACION en el mismo ámbito.";
    private final Db db;
    private final JdbcTemplate jdbc;
    private final Reports reports;
    private final StatGuard guard;
    private final StatEngine engine;
    private final StatCuts cuts;
    private final StatSeries series;
    private final int maxRows,maxRelations;
    /** Un paquete ZIP a la vez en la instancia (detencion de E11): cupo propio, ademas del de StatGuard, sin cola. */
    private final java.util.concurrent.Semaphore zipSlot=new java.util.concurrent.Semaphore(1);
    static final String ZIP_BUSY="Ya se está generando un paquete ZIP en SIGER. Espera a que termine y vuelve a intentarlo.";

    StatExport(Db db,Reports reports,StatGuard guard,StatEngine engine,StatCuts cuts,StatSeries series,
        @Value("${siger.stats.export.max-rows:30000}")int maxRows,
        @Value("${siger.stats.export.max-relations:500000}")int maxRelations){
        if(maxRows<1||maxRows>=Xlsx.MAX_ROWS||maxRelations<1||maxRelations>=Xlsx.MAX_ROWS)throw new IllegalArgumentException("Límites de exportación inválidos.");
        this.db=db;this.jdbc=new JdbcTemplate(db.dataSource());this.reports=reports;this.guard=guard;this.engine=engine;
        this.cuts=cuts;this.series=series;this.maxRows=maxRows;this.maxRelations=maxRelations;
    }

    enum Format {
        CSV("text/csv; charset=UTF-8"),XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),ZIP("application/zip");
        final String mime;
        Format(String mime){this.mime=mime;}
        String extension(){return name().toLowerCase(Locale.ROOT);}
        static Format parse(String f){return switch(f){case "csv"->CSV;case "xlsx"->XLSX;case "zip"->ZIP;default->throw new Problem(400,"Formato de exportación no reconocido.");};}
    }
    record Download(String name,String contentType,byte[] bytes){}
    /**
     * Medicion (StatLoadMeasureTest): si el hilo la activa, acumula los ms de cada fase de la descarga (consulta,
     * microdatos, csv, xlsx, compresion); sin activarla no registra nada. No cambia el archivo ni la respuesta.
     */
    static final ThreadLocal<Map<String,Long>> PHASES=new ThreadLocal<>();
    private static long phase(String name,long since){
        long now=System.nanoTime();var phases=PHASES.get();
        if(phases!=null)phases.merge(name,TimeUnit.NANOSECONDS.toMillis(now-since),Long::sum);
        return now;
    }
    private record Table(List<String> header,Iterable<? extends List<?>> rows){
        Iterable<List<?>> grid(){
            return ()->new Iterator<>(){
                boolean first=true;final Iterator<? extends List<?>> next=rows.iterator();
                public boolean hasNext(){return first||next.hasNext();}
                public List<?> next(){if(first){first=false;return header;}return next.next();}
            };
        }
    }

    Download export(Actor a,String requested,Map<String,Object> body){
        var grants=exportGrants(a);var format=Format.parse(requested);guard.requireEnabled();
        if(format!=Format.ZIP)return export(a,grants,format,body);
        if(!zipSlot.tryAcquire())throw new Problem(429,ZIP_BUSY);
        try{return export(a,grants,format,body);}
        finally{zipSlot.release();}
    }
    private Download export(Actor a,List<Access.Grant> grants,Format format,Map<String,Object> body){
        var note=new AtomicReference<>("Exportación estadística; formato "+format.name());
        return guard.run(a.id(),()->guard.read(a.id(),note::get,()->{
            var deadline=new Deadline(guard.timeoutSeconds());long started=System.nanoTime();
            var parsed=engine.parse(a,body==null?Map.of():body);var q=parsed.query();
            var reach=StatEngine.effectiveReach(parsed);
            requireCover(reports.reach(grants),parsed);
            var people=Reports.peopleGrants(a);
            var sensitiveReach=reports.reach(grants.stream().filter(g->people.stream().anyMatch(p->Objects.equals(g.scope(),p.scope()))).toList());
            boolean groups=covers(sensitiveReach,parsed);
            var dims=q.axesDims().stream().map(Dim::code).toList();
            var filters=q.filters().keySet().stream().map(Dim::code).toList();
            note.set(note.get()+"; medida "+q.measure().code()+"; modo "+q.mode().name()+"; dimensiones "+String.join(",",dims)+"; filtros "+String.join(",",filters));
            if(format==Format.ZIP){
                if(!groups&&usesGroups(q))Problem.denied();
                if(q.measure().own()||!MICRO_POPULATION.contains(q.measure().population()))throw new Problem(400,"El paquete ZIP requiere una medida de requerimientos computables. Descarga esta consulta como CSV o XLSX.");
                if(parsed.cut()!=null&&cuts.exportHash(parsed.cut().id())==null)throw new Problem(409,"Este corte se generó antes de incorporar los datos del paquete ZIP. Descarga sus cifras como CSV o XLSX.");
            }
            var result=engine.calculate(parsed);var aggregate=aggregate(q,result);phase("consulta",started);
            byte[] content=switch(format){
                case CSV->csv(aggregate,deadline);
                case XLSX->workbook(Map.of("Agregado",aggregate),deadline);
                case ZIP->packageFile(a,parsed,reach,groups,result,aggregate,deadline);
            };
            deadline.check();
            db.audit(a.id(),AUDIT,"SISTEMA",0,null,note.get());
            return file("siger-estadisticas",format,content);
        }));
    }

    private static boolean usesGroups(Query q){
        return q.axesDims().stream().anyMatch(StatExport::sensitive)||q.filters().keySet().stream().anyMatch(StatExport::sensitive)
            ||q.mention()!=null&&sensitive(q.mention());
    }
    private static boolean sensitive(Dim d){return d.code().equals(GROUP)||d.code().equals(GROUP_STATE);}
    private static List<Access.Grant> exportGrants(Actor a){
        if(!StatEngine.allowed(a))Problem.denied();
        var grants=Reports.exportGrants(a);if(grants.isEmpty())Problem.denied();return grants;
    }
    private static void requireCover(Reports.Reach permitted,StatEngine.Parsed parsed){
        if(!covers(permitted,parsed))Problem.denied();
    }
    private static boolean covers(Reports.Reach permitted,StatEngine.Parsed parsed){
        return StatCuts.covers(permitted,StatEngine.effectiveReach(parsed))&&(parsed.baseCut()==null||StatCuts.covers(permitted,parsed.baseCut().reach()));
    }

    Download series(Actor a,String requested,long id){
        var grants=exportGrants(a);var format=Format.parse(requested);guard.requireEnabled();
        if(format==Format.ZIP)throw new Problem(400,"Exporta la serie histórica como CSV o XLSX.");
        String audit="Serie "+id+"; formato "+format.name();
        return guard.run(a.id(),()->guard.read(a.id(),()->audit,()->{
            var deadline=new Deadline(guard.timeoutSeconds());
            var header=series.readable(a,id);
            if(!StatCuts.covers(reports.reach(grants),cuts.reach(nullableId(header,"scope_id"))))Problem.denied();
            var rows=series.values(id).stream().map(r->Arrays.asList(r.get("period_from"),r.get("period_to"),r.get("dimension"),r.get("category_label"),r.get("value"),r.get("category_code"))).toList();
            var columns=new ArrayList<>(StatSeries.COLUMNS);columns.add("codigo_siger");
            var table=new Table(columns,rows);
            byte[] bytes=format==Format.CSV?csv(table,deadline):workbook(Map.of("Serie",table),deadline);
            deadline.check();db.audit(a.id(),AUDIT,"SISTEMA",0,null,audit);
            return file("siger-serie-"+id,format,bytes);
        }));
    }
    Download template(Actor a,String requested){
        if(!StatEngine.allowed(a)||Roles.ruling(a,Roles.GESTION_ESTADISTICA).isEmpty())Problem.denied();
        var format=Format.parse(requested);if(format==Format.ZIP)throw new Problem(400,"Descarga la plantilla como CSV o XLSX.");
        var table=new Table(StatSeries.COLUMNS,List.of());var deadline=new Deadline(guard.timeoutSeconds());
        return file("siger-plantilla-serie",format,format==Format.CSV?csv(table,deadline):workbook(Map.of("Serie",table),deadline));
    }
    private static Download file(String name,Format format,byte[] bytes){return new Download(name+"."+format.extension(),format.mime,bytes);}

    private byte[] packageFile(Actor a,StatEngine.Parsed parsed,Reports.Reach reach,boolean groups,Map<String,Object> result,Table aggregate,Deadline deadline){
        var q=parsed.query();
        var singles=new ArrayList<>(StatSource.FROZEN.stream().filter(d->groups||!sensitive(d)).toList());
        q.axesDims().stream().filter(d->d.kind()==Kind.PERIODO).forEach(singles::add);
        if(q.basis()==Basis.RECEPCION)singles.add(StatSource.DIMS.get("TARDANZA"));
        var multis=StatSource.DIMS.values().stream().filter(Dim::multiple).filter(d->groups||!sensitive(d)).toList();
        var micro=new Micro(singles,multis);long t=System.nanoTime(),m=t;
        collect(new Plan(q).exportRows(singles,maxRows),deadline,rs->micro.row(rs,q));m=phase("microdatos.filas",m);
        collect(new Plan(q).exportClassifications(groups),deadline,micro::classification);m=phase("microdatos.clasificaciones",m);
        collect(new Plan(q).exportActions(),deadline,micro::action);m=phase("microdatos.actuaciones",m);
        collect(new Plan(q).exportPersons(),deadline,micro::person);m=phase("microdatos.personas",m);
        var current=new TreeMap<String,Long>();
        if(parsed.cut()==null)db.rows("SELECT id,catalog FROM stat_catalog_version WHERE `current_catalog` IS NOT NULL").forEach(r->current.put(s(r,"catalog"),n(r,"id")));
        else current.putAll(parsed.cut().versions());
        if(!groups)current.remove(GROUP);
        var versions=new TreeSet<>(micro.versions);versions.addAll(current.values());
        var definitions=catalogs(versions,groups);
        Table dictionary=dictionary(groups,definitions.rows(),singles,multis);
        var tables=new LinkedHashMap<String,Table>();
        tables.put("Agregado",aggregate);tables.put("Microdatos",micro.singles());tables.put("Multivalor",micro.multis(definitions,current));
        tables.put("Diccionario",dictionary);tables.put("Catalogos",definitions.table());
        t=phase("microdatos",t);
        var files=new LinkedHashMap<String,byte[]>();
        String readme="Paquete estadístico de SIGER\n\n"+NOTE+"\n"+MENTIONS+"\n"+RESERVED+"\n"+PRIVACY+"\n"+(groups?"":OMIT+"\n")
            +"El microdato describe la consulta principal. Las cifras de comparación se incluyen en el agregado.\n"
            +"CSV y XLSX son reproducibles para los mismos datos y orden. El instante de metadatos.json y su entrada en SHA256SUMS.txt cambian entre descargas.\n";
        files.put("LEEME.txt",readme.getBytes(StandardCharsets.UTF_8));
        files.put("metadatos.json",metadata(a,parsed,reach,result,current).getBytes(StandardCharsets.UTF_8));
        files.put("agregado.csv",csv(aggregate,deadline));
        t=phase("csv",t);
        files.put("agregado.xlsx",workbook(tables,deadline));
        t=phase("xlsx",t);
        files.put("microdatos.csv",csv(tables.get("Microdatos"),deadline));
        files.put("microdatos_multivalor.csv",csv(tables.get("Multivalor"),deadline));
        files.put("diccionario.csv",csv(dictionary,deadline));
        files.put("catalogos.csv",csv(definitions.table(),deadline));
        var sums=new StringBuilder();
        files.forEach((name,bytes)->{deadline.check();sums.append(sha(bytes)).append("  ").append(name).append('\n');});
        files.put("SHA256SUMS.txt",sums.toString().getBytes(StandardCharsets.UTF_8));
        t=phase("csv",t);
        var zip=Xlsx.zip(files);
        phase("compresion",t);
        return zip;
    }
    private String metadata(Actor a,StatEngine.Parsed p,Reports.Reach reach,Map<String,Object> response,Map<String,Long> current){
        var out=new LinkedHashMap<>(object(response.get("metadata")));
        out.put("account_id",a.id());out.put("reach",Map.of("institutional",reach.all(),"scopes",new TreeSet<>(reach.scopes()),"matters",new TreeSet<>(reach.matters())));
        var values=new TreeMap<String,Object>();p.query().filters().forEach((d,v)->values.put(d.code(),v));out.put("filter_codes",values);
        out.put("active_catalog_versions",current);
        out.put("calendar_sha256",p.cut()==null?cuts.calendar():s(db.one("SELECT calendar_sha256 FROM stat_cut WHERE id=?",p.cut().id()),"calendar_sha256"));
        if(p.cut()!=null){out.put("cut_code",p.cut().code());out.put("cut_export_sha256",cuts.exportHash(p.cut().id()));}
        out.put("comparison_type",p.compare());out.put("comparison_cut_code",p.baseCut()==null?null:p.baseCut().code());
        return StatCuts.json(out);
    }

    private record CatalogData(List<Map<String,Object>> rows,Map<Long,Map<String,Map<String,Object>>> keyed,Table table){}
    private CatalogData catalogs(Set<Long> versions,boolean groups){
        List<Map<String,Object>> rows=versions.isEmpty()?List.of():db.rows("SELECT v.id,v.catalog,i.code,i.label,i.parent_code,i.level,i.selectable,i.description"
            +" FROM stat_catalog_version v JOIN stat_catalog_item i ON i.version_id=v.id WHERE v.id IN ("
            +versions.stream().map(String::valueOf).collect(Collectors.joining(","))+")"+(groups?"":" AND v.catalog<>'GRUPO_PRIORITARIO'")+" ORDER BY v.catalog,v.id,i.sort,i.code");
        var keyed=new HashMap<Long,Map<String,Map<String,Object>>>();
        for(var r:rows)keyed.computeIfAbsent(n(r,"id"),k->new HashMap<>()).put(s(r,"code"),r);
        var items=rows.stream().map(r->Arrays.asList(r.get("catalog"),r.get("id"),r.get("code"),r.get("label"),r.get("parent_code"),r.get("level"),r.get("selectable"),r.get("description"))).toList();
        return new CatalogData(rows,keyed,new Table(CATALOGS,items));
    }
    private static Table dictionary(boolean groups,List<Map<String,Object>> catalogs,List<Dim> singles,List<Dim> multis){
        var rows=new ArrayList<List<?>>();
        for(String text:List.of(NOTE,MENTIONS,RESERVED,PRIVACY))rows.add(List.of("METODOLOGIA","","","",text));
        if(!groups)rows.add(List.of("PERMISO","","","",OMIT));
        for(var d:singles)rows.add(List.of("VALOR_UNICO",d.code(),"",d.label(),"Un valor o estado por requerimiento."));
        for(var d:multis)rows.add(List.of("MULTIVALOR",d.code(),"",d.label(),"Una fila relacionada por folio, dimensión y código."));
        for(var r:catalogs)rows.add(List.of("CATALOGO",s(r,"catalog"),s(r,"code"),s(r,"label"),s(r,"description")));
        return new Table(DICTIONARY,rows);
    }

    private static Table aggregate(Query q,Map<String,Object> result){
        var header=new ArrayList<>(List.of("tipo"));
        for(int i=1;i<=q.rows().size();i++){header.add("fila_"+i);header.add("rotulo_fila_"+i);}
        for(int i=1;i<=q.columns().size();i++){header.add("columna_"+i);header.add("rotulo_columna_"+i);}
        header.addAll(List.of("n","unique","mentions","numerador","denominador","porcentaje","n_base","diferencia","variacion_porcentual"));
        var labels=new HashMap<String,Map<String,String>>();
        object(result.get("values")).forEach((dim,items)->{
            var m=new HashMap<String,String>();objects(items).forEach(r->m.put(s(r,"key"),s(r,"label")));labels.put(dim,m);
        });
        var rows=new ArrayList<List<?>>();var totals=object(result.get("totals"));
        var comparison=object(result.get("comparison"));var comparisonTotals=object(comparison.get("totals"));
        var cells=index(objects(comparison.get("cells")),"row","column");
        var rowTotals=index(objects(comparisonTotals.get("rows")),"key");var colTotals=index(objects(comparisonTotals.get("columns")),"key");
        for(var cell:objects(result.get("cells")))rows.add(aggregateRow(q,"CELDA",keys(cell,"row"),keys(cell,"column"),cell,cells.remove(cellKey(cell,"row","column")),labels));
        for(var cell:cells.values())rows.add(aggregateRow(q,"COMPARACION_CELDA",keys(cell,"row"),keys(cell,"column"),Map.of("n",0L),cell,labels));
        for(var data:objects(totals.get("rows")))rows.add(aggregateRow(q,"TOTAL_FILA",keys(data,"key"),List.of(),data,rowTotals.remove(cellKey(data,"key")),labels));
        for(var data:rowTotals.values())rows.add(aggregateRow(q,"COMPARACION_FILA",keys(data,"key"),List.of(),Map.of("n",0L),data,labels));
        for(var data:objects(totals.get("columns")))rows.add(aggregateRow(q,"TOTAL_COLUMNA",List.of(),keys(data,"key"),data,colTotals.remove(cellKey(data,"key")),labels));
        for(var data:colTotals.values())rows.add(aggregateRow(q,"COMPARACION_COLUMNA",List.of(),keys(data,"key"),Map.of("n",0L),data,labels));
        rows.add(aggregateRow(q,"TOTAL_GENERAL",List.of(),List.of(),object(totals.get("grand")),object(comparisonTotals.get("grand")),labels));
        return new Table(header,rows);
    }
    private static Map<List<Object>,Map<String,Object>> index(List<Map<String,Object>> values,String... names){
        var indexed=new LinkedHashMap<List<Object>,Map<String,Object>>();
        values.forEach(v->indexed.put(cellKey(v,names),v));return indexed;
    }
    private static List<Object> cellKey(Map<String,Object> row,String... keys){return Arrays.stream(keys).map(row::get).toList();}
    private static List<?> aggregateRow(Query q,String type,List<?> row,List<?> col,Map<String,Object> counts,Map<String,Object> delta,Map<String,Map<String,String>> labels){
        var out=new ArrayList<Object>();out.add(type);
        axes(out,q.rows(),row,labels);axes(out,q.columns(),col,labels);
        for(String k:List.of("n","unique","mentions","percent_numerator","denominator","percent"))out.add(counts.get(k));
        for(String k:List.of("base_n","delta_n","delta_percent"))out.add(delta==null?null:delta.get(k));
        return out;
    }
    private static void axes(List<Object> out,List<Dim> dims,List<?> axis,Map<String,Map<String,String>> labels){
        for(int i=0;i<dims.size();i++){
            Object value=i<axis.size()?axis.get(i):null;
            out.add(value);out.add(value==null?null:labels.getOrDefault(dims.get(i).code(),Map.of()).getOrDefault(value,value.toString()));
        }
    }

    private record MicroRow(String folio,Object[] fields,boolean evidence,int[] counts){}
    private record RelatedKey(long com,Dim dim,String code){}
    private static final class RelatedValue {
        Long version;
        boolean req;
        long firstCase,mentions;
        Set<Long> otherCases;
        void mention(Long version,String kind,long id){
            mentions++;
            if(version!=null&&(this.version==null||version>this.version))this.version=version;
            if(kind.equals("REQ"))req=true;
            if(kind.equals("CAS")&&id>0){
                if(firstCase==0)firstCase=id;
                else if(firstCase!=id){if(otherCases==null)otherCases=new HashSet<>();otherCases.add(id);}
            }
        }
        int cases(){return firstCase==0?0:1+(otherCases==null?0:otherCases.size());}
    }
    private final class Micro {
        private final List<Dim> singles,multis;
        private final Map<Long,MicroRow> rows=new LinkedHashMap<>();
        private final Set<String> folios=new HashSet<>();
        private final Set<Long> versions=new HashSet<>();
        private final Map<RelatedKey,RelatedValue> related=new LinkedHashMap<>();
        private final Map<String,Integer> multiIndex=new HashMap<>();
        private final Map<String,Integer> singleIndex=new HashMap<>();
        private long lastActionCom,lastUnit;

        Micro(List<Dim> singles,List<Dim> multis){
            this.singles=singles;this.multis=multis;
            for(int i=0;i<multis.size();i++)multiIndex.put(multis.get(i).code(),i);
            for(int i=0;i<singles.size();i++)singleIndex.put(singles.get(i).code(),i);
        }
        void row(ResultSet rs,Query q)throws SQLException{
            if(rows.size()>=maxRows)throw limit();
            String folio=rs.getString("folio");
            if(folio==null||!folio.matches("[A-Z0-9][A-Z0-9-]{0,47}")||!folios.add(folio))throw new Problem(409,"Hay un ingreso sin folio válido y único en el periodo. Descarga las cifras como CSV o XLSX.");
            var fields=new Object[singles.size()];
            for(int i=0;i<singles.size();i++){
                var d=singles.get(i);String key=StatEngine.key(d,q,q.axes(),rs.getObject(StatSource.frozenColumn(d)));
                fields[i]=code(d,key);
            }
            for(String v:StatSource.KEYED){Long ver=number(rs,"v_"+v);if(ver!=null)versions.add(ver);}
            rows.put(rs.getLong("id"),new MicroRow(folio,fields,rs.getInt("evid")==1,new int[multis.size()]));
        }
        void classification(ResultSet r)throws SQLException{
            relation(r.getLong("com"),r.getString("variable"),r.getString("val"),number(r,"ver"),r.getString("record_type"),r.getLong("record_id"));
        }
        void action(ResultSet r)throws SQLException{
            long com=r.getLong("com"),unit=r.getLong("unit"),record=r.getLong("record_id");String kind=r.getString("record_type");
            if(com!=lastActionCom||unit!=lastUnit){
                relation(com,"TIPO_ACTUACION",r.getString("action_type"),number(r,"ver"),kind,record);
                relation(com,"ORIGEN_ACTUACION",r.getString("origin"),null,kind,record);
                lastActionCom=com;lastUnit=unit;
            }
            if(number(r,"institution_id")!=null)relation(com,"TIPO_INSTITUCION",Objects.toString(r.getString("type_code"),"SIN_INFORMACION"),number(r,"catalog_version_id"),kind,record);
        }
        void person(ResultSet r)throws SQLException{
            long com=r.getLong("com");var keys=List.of("region_code","commune","age_years","nationality","age_bracket","nationality_group");
            for(int i=0;i<keys.size();i++)relation(com,PERSON_DIMENSIONS.get(i),r.getString(keys.get(i)),null,"",0);
        }
        void relation(long com,String dimension,String key,Long ver,String kind,long id){
            if(key==null)return;
            var d=StatSource.DIMS.get(dimension);var micro=rows.get(com);if(micro==null)throw new IllegalStateException("Relación fuera de la exportación.");
            var k=new RelatedKey(com,d,code(d,key));
            var v=related.get(k);
            if(v==null){if(related.size()>=maxRelations)throw relationsLimit();v=new RelatedValue();related.put(k,v);micro.counts()[multiIndex.get(dimension)]++;}
            v.mention(ver,kind,id);if(ver!=null)versions.add(ver);
        }
        Table singles(){
            var header=new ArrayList<>(List.of("folio"));singles.forEach(d->header.add(d.code()));
            for(var d:multis){
                if(!singleIndex.containsKey("ESTADO_"+d.code()))header.add("ESTADO_"+d.code());
                header.add("CANTIDAD_"+d.code());
            }
            return new Table(header,()->rows.values().stream().<List<?>>map(r->{
                var cells=new ArrayList<Object>(header.size());cells.add(r.folio());Collections.addAll(cells,r.fields());
                for(int i=0;i<multis.size();i++){
                    Dim d=multis.get(i);int count=r.counts()[i];
                    if(!singleIndex.containsKey("ESTADO_"+d.code()))cells.add(state(r,d,count));
                    cells.add(count);
                }
                return cells;
            }).iterator());
        }
        private String state(MicroRow row,Dim d,int count){
            if(count>0)return "INFORMADO";
            if(d.code().equals("DERECHO_EJE")||d.code().equals("DERECHO_SUBCATEGORIA")){
                String rights=String.valueOf(row.fields()[singleIndex.get("ESTADO_DERECHO")]);
                return d.code().equals("DERECHO_SUBCATEGORIA")&&rights.equals("INFORMADO")?"SIN_DESGLOSE":rights;
            }
            if(d.unit()==Unit.ACTUACION)return "NO_APLICA";
            return row.evidence()?"SIN_INFORMACION":"PENDIENTE";
        }
        Table multis(CatalogData catalog,Map<String,Long> current){
            var order=related.entrySet().stream().sorted(Comparator.comparingLong((Map.Entry<RelatedKey,RelatedValue> e)->e.getKey().com())
                .thenComparing(e->e.getKey().dim().code()).thenComparing(e->e.getKey().code())).toList();
            return new Table(RELATED,()->order.stream().<List<?>>map(e->{
                var k=e.getKey();var v=e.getValue();var d=k.dim();
                Long ver=v.version!=null?v.version:d.catalog()==null?null:current.get(d.catalog());
                var item=ver==null?Map.<String,Object>of():catalog.keyed().getOrDefault(ver,Map.of()).getOrDefault(k.code(),Map.of());
                String label=item.isEmpty()?d.options().getOrDefault(k.code(),StatSource.SPECIAL.getOrDefault(k.code(),StatSource.IMMEDIATE.getOrDefault(k.code(),k.code()))):s(item,"label");
                return Arrays.asList(rows.get(k.com()).folio(),d.code(),k.code(),label,item.get("level"),v.req?1:0,v.cases(),v.mentions);
            }).iterator());
        }
    }

    private static String code(Dim d,String value){
        if(StatSource.SPECIAL.containsKey(value)||d.options().containsKey(value))return value;
        return switch(d.kind()){
            case REGION->AdultIdentity.REGIONS.containsKey(value)?value:"SIN_INFORMACION";
            case COMUNA->COMMUNES.contains(value)?value:"SIN_INFORMACION";
            case PAIS->AdultIdentity.COUNTRIES.contains(value)?value:"SIN_INFORMACION";
            default->value;
        };
    }
    private static Long number(ResultSet rs,String name)throws SQLException{Object n=rs.getObject(name);return n==null?null:((Number)n).longValue();}
    private static Problem relationsLimit(){return new Problem(409,"El paquete supera el tamaño permitido. Acota el periodo o los filtros.");}
    /** Mas de siger.stats.export.max-rows folios (30.000 por omision; SIGER_STATS_EXPORT_MAX_ROWS). */
    private Problem limit(){return new Problem(409,"El paquete supera el máximo de "+String.format(Locale.ROOT,"%,d",maxRows).replace(',','.')+" folios. Acota el periodo o los filtros.");}
    private void collect(Sql sql,Deadline deadline,RowCallbackHandler reader){
        jdbc.query(sql.text(),rs->{deadline.check();reader.processRow(rs);},sql.args());
    }

    private static byte[] csv(Table table,Deadline deadline){
        var out=new ByteArrayOutputStream();
        try(var writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8))){
            writer.write('\ufeff');
            for(var row:table.grid()){
                deadline.check();
                for(int i=0;i<row.size();i++){if(i>0)writer.write(Csv.SEPARATOR);writer.write(Csv.cell(row.get(i)));}
                writer.write("\r\n");
            }
        }catch(IOException e){throw new UncheckedIOException(e);}
        return out.toByteArray();
    }
    private static byte[] workbook(Map<String,Table> tables,Deadline deadline){
        var sheets=tables.entrySet().stream().map(e->new Xlsx.Sheet(e.getKey(),()->new Iterator<>(){
            final Iterator<List<?>> rows=e.getValue().grid().iterator();
            public boolean hasNext(){return rows.hasNext();}
            public List<?> next(){deadline.check();return rows.next();}
        })).toList();
        return Xlsx.write(sheets);
    }
    static String sha(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object x){return x==null?Map.of():(Map<String,Object>)x;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> objects(Object x){return x==null?List.of():(List<Map<String,Object>>)x;}
    private static List<?> keys(Map<String,Object> row,String key){return (List<?>)row.getOrDefault(key,List.of());}
    private static final class Deadline {
        private final long end;
        Deadline(int seconds){end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);}
        void check(){if(System.nanoTime()-end>=0)throw new TransactionTimedOutException("Plazo de exportación agotado.");}
    }
}
