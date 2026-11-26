package cl.nexbyte.sigera;

import java.text.Collator;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cl.nexbyte.sigera.StatSource.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Motor estadistico (0.8.0, D25-D41, gate E08; contrato en docs/CONTRATO_STATS_0_8_0.md). POST /api/stats/query: valida
 * la peticion contra listas cerradas, agrega en SQL sobre StatSource y calcula en Java porcentajes y comparaciones. Solo
 * cuentas con REPORTES, en su alcance vigente (Reports.reach); TI nunca (D2). Una consulta, con su comparacion, se lee en
 * una sola transaccion REPEATABLE READ, dentro de las guardas de StatGuard (E08b: concurrencia, tiempo maximo comun y
 * confinamiento de los archivos de MySQL).
 *
 * Cifras exactas (D41): sin supresion ni redondeo de conteos. siger.stats.max-cells es solo una salvaguarda tecnica de la
 * respuesta: si la consulta y su comparacion la superan se responde 409, nunca un resultado parcial. La auditoria
 * STAT_CONSULTA guarda nombres de medida, dimensiones y filtros; jamas valores, fechas ni resultados.
 */
@Service
public class StatEngine {
    static final String METHODOLOGY="1",AUDIT="STAT_CONSULTA";
    static final List<String> PERIODS=List.of("PERSONALIZADO","MES","TRIMESTRE","SEMESTRE","ANIO","CUENTA_PUBLICA");
    static final List<String> COMPARES=List.of("PERIODO_ANTERIOR","MISMO_PERIODO_ANIO_ANTERIOR");
    /** Comparacion con un corte (E10): {"type":"CORTE","cut_id":n}. */
    static final String CUT="CORTE";
    private static final List<String> PERCENTS=List.of("TOTAL","FILA","COLUMNA");
    private static final Set<String> FIELDS=Set.of("measure","rows","columns","filters","period","time_mode","as_of","cut_id","compare","percent","include_proposals","mention_dimension");
    private static final Set<String> DEMOGRAPHIC=Set.of("EDAD_NNA","TRAMO_EDAD_NNA","NACIONALIDAD_NNA","GRUPO_NACIONALIDAD_NNA","EDAD_REQUIRENTE","TRAMO_EDAD_REQUIRENTE","NACIONALIDAD_REQUIRENTE","GRUPO_NACIONALIDAD_REQUIRENTE");
    private static final Set<String> HISTORY=Set.of("COMPLEJIDAD","MATERIA","MODALIDAD");
    private static final int MAX_FILTER=500;
    private static final Locale ES=Locale.of("es","CL");

    private final Db db;
    private final Reports reports;
    private final StatGuard guard;
    private final StatCuts cuts;
    /** Celdas de la consulta mas las de su comparacion. */
    private final int maxCells;

    @Autowired
    public StatEngine(Db db,Reports reports,StatGuard guard,StatCuts cuts,@Value("${siger.stats.max-cells:20000}")int maxCells){
        if(maxCells<1)throw new IllegalArgumentException("siger.stats.max-cells debe ser positivo.");
        this.db=db;
        this.reports=reports;
        this.guard=guard;
        this.cuts=cuts;
        this.maxCells=maxCells;
    }
    public StatEngine(Db db,Reports reports,StatGuard guard,int maxCells){this(db,reports,guard,new StatCuts(db,reports,guard),maxCells);}
    /** Alcance de una consulta sobre un corte: el corte ya guarda solo su alcance. */
    private static final Reports.Reach ALL=new Reports.Reach(true,Set.of(),Set.of());

    static boolean allowed(Access.Actor a){return Reports.canReport(a)&&!a.role(Roles.TI);}

    // --- Catalogo publicado (GET /api/catalog, solo con REPORTES) -----------------------------------------------------
    public Map<String,Object> catalog(){
        var out=new LinkedHashMap<String,Object>();
        out.put("methodology_version",METHODOLOGY);out.put("max_cells",maxCells);out.put("enabled",guard.enabled());
        out.put("measures",StatSource.MEASURES.values().stream().map(m->{
            var row=new LinkedHashMap<String,Object>();
            row.put("code",m.code());row.put("label",m.label());row.put("unit",m.unit().name());
            row.put("percent_unit",percentUnit(m));row.put("needs_multiple",m.code().equals("REQUERIMIENTOS_CON_VALOR")||m.mentions());
            row.put("default_date_basis",basisOf(m).name());
            return row;
        }).toList());
        out.put("dimensions",StatSource.DIMS.values().stream().map(d->{
            var row=new LinkedHashMap<String,Object>();
            row.put("code",d.code());row.put("label",d.label());row.put("type",d.kind().name());row.put("cardinality",d.multiple()?"MULTIPLE":"UNICA");
            row.put("catalog",d.catalog());row.put("filterable",d.kind()!=Kind.PERIODO);
            row.put("measures",d.only()?StatSource.MEASURES.values().stream().filter(m->m.unit()==d.unit()).map(Measure::code).toList():null);
            row.put("options",d.options().entrySet().stream().map(e->Map.of("code",e.getKey(),"label",e.getValue())).toList());
            return row;
        }).toList());
        out.put("special",StatSource.SPECIAL.entrySet().stream().map(e->Map.of("code",e.getKey(),"label",e.getValue())).toList());
        out.put("periods",PERIODS);out.put("date_basis",Arrays.stream(Basis.values()).map(Enum::name).toList());
        out.put("time_modes",Arrays.stream(Mode.values()).map(Enum::name).toList());out.put("compares",java.util.stream.Stream.concat(COMPARES.stream(),java.util.stream.Stream.of(CUT)).toList());out.put("percents",PERCENTS);
        return out;
    }
    private static String percentUnit(Measure m){return m.own()?m.unit().name():Unit.COM.name();}

    // --- Consulta -----------------------------------------------------------------------------------------------------
    /**
     * Permiso (403), motor habilitado (503), cupos de concurrencia (429, antes de pedir conexion) y una sola transaccion
     * REPEATABLE READ con el plazo comun de StatGuard para la consulta, su comparacion y las lecturas auxiliares.
     */
    public Map<String,Object> query(Access.Actor a,Map<String,Object> body){
        if(!allowed(a))Problem.denied();
        guard.requireEnabled();
        var names=new java.util.concurrent.atomic.AtomicReference<String>();
        return guard.run(a.id(),()->guard.read(a.id(),names::get,()->execute(a,body,names)));
    }
    private Map<String,Object> execute(Access.Actor a,Map<String,Object> body,java.util.concurrent.atomic.AtomicReference<String> names){
        var r=parse(a,body==null?Map.of():body);
        var q=r.query();
        var dims=q.axesDims().stream().map(Dim::code).toList();
        names.set("Medida "+q.measure().code()+"; modo "+q.mode().name()+(r.cut()==null?"":"; sobre un corte")+(dims.isEmpty()?"":"; dimensiones: "+String.join(", ",dims))
            +(q.filters().isEmpty()?"":"; filtros: "+q.filters().keySet().stream().map(Dim::code).collect(Collectors.joining(", ")))+(r.compare()==null?"":"; comparacion "+r.compare()));
        var out=calculate(r);
        db.audit(a.id(),AUDIT,"SISTEMA",0,null,names.get());
        return out;
    }
    /** Comparación y agregado idénticos para la consulta interactiva y la exportación. */
    Map<String,Object> calculate(Parsed r){
        var q=r.query();
        var main=read(q,q.axes(),0,extras(q));
        Result base=null;Span baseSpan=null;
        if(r.baseCut()!=null){
            // Con un corte: el mismo periodo y la misma consulta sobre sus filas congeladas, celda por celda.
            baseSpan=q.span();
            base=read(q.on(r.baseCut().id(),ALL),q.axes(),main.cells().size(),null);
        }else if(r.compare()!=null){
            baseSpan=previous(r.compare(),r.periodType(),q.span());
            base=read(q.over(baseSpan,axes(q.axesDims(),baseSpan)),q.axes(),main.cells().size(),null);
        }
        return response(r,main,base,baseSpan);
    }
    private Problem tooMany(){
        return new Problem(409,"La consulta tiene más de "+String.format(Locale.ROOT,"%,d",maxCells).replace(',','.')+" celdas, incluida su comparación. Quita una dimensión, agrega un filtro o acorta el periodo.");
    }

    // --- Validacion ---------------------------------------------------------------------------------------------------
    /** cut: corte consultado (nulo = fuente vigente); baseCut: corte con que se compara (compare CORTE). */
    record Parsed(Query query,String periodType,String compare,String percent,StatCuts.Cut cut,StatCuts.Cut baseCut){}
    static Reports.Reach effectiveReach(Parsed r){return r.cut()==null?r.query().reach():r.cut().reach();}
    /** Periodo de un corte (E10): sin fecha de analisis, porque el corte guarda todas. */
    record Period(String type,Span span){}
    static Period cutPeriod(Object raw){
        var p=object(raw,"Periodo",Set.of("type","from","to"));
        String type=name(p.get("type"),"Tipo de periodo",PERIODS,null);
        if(type==null)throw bad("Indica el tipo de periodo.");
        return new Period(type,span(type,p));
    }
    /** Valida una definicion guardada (vistas, E10) como la consulta que ejecutaria la cuenta, sin ejecutarla. */
    void validate(Access.Actor a,Map<String,Object> body){
        if(!allowed(a))Problem.denied();
        parse(a,body==null?Map.of():body);
    }
    private static Problem bad(String message){return new Problem(400,message);}
    private static String name(Object v,String field,Collection<String> allowed,String absent){
        if(v==null)return absent;
        if(!(v instanceof String s)||!allowed.contains(s))throw bad(field+" no válido.");
        return s;
    }
    private static Map<String,Object> object(Object v,String field,Set<String> keys){
        if(!(v instanceof Map<?,?> m))throw bad(field+": revisa los campos enviados.");
        var out=new LinkedHashMap<String,Object>();
        m.forEach((k,x)->out.put(String.valueOf(k),x));
        if(!keys.containsAll(out.keySet()))throw bad(field+": hay campos no reconocidos.");
        return out;
    }
    private static List<Dim> dimensions(Object v,String field){
        if(v==null)return List.of();
        if(!(v instanceof List<?> list))throw bad(field+": envía una lista de dimensiones.");
        var out=new ArrayList<Dim>();
        for(Object x:list)out.add(dimension(x));
        return List.copyOf(out);
    }
    private static Dim dimension(Object v){
        var d=v instanceof String s?StatSource.DIMS.get(s):null;
        if(d==null)throw bad("Dimensión no reconocida.");
        return d;
    }
    private static LocalDate day(Object v,String field){
        if(!(v instanceof String s)||!s.matches("\\d{4}-\\d{2}-\\d{2}"))throw bad(field+": usa el formato aaaa-mm-dd.");
        try{return LocalDate.parse(s);}catch(DateTimeParseException e){throw bad(field+": usa el formato aaaa-mm-dd.");}
    }
    private static List<String> names(Enum<?>[] values){return Arrays.stream(values).map(Enum::name).toList();}
    private static Basis basisOf(Measure m){return m.unit()==Unit.CASO?Basis.CREACION_CASO:m.code().equals("REQ_CONSTITUIDOS")?Basis.CONSTITUCION_REQ:Basis.RECEPCION;}

    Parsed parse(Access.Actor a,Map<String,Object> body){
        var b=object(body,"Consulta",FIELDS);
        var measure=StatSource.MEASURES.get(name(b.get("measure"),"Medida",StatSource.MEASURES.keySet(),""));
        if(measure==null)throw bad("Indica la medida.");
        var rows=dimensions(b.get("rows"),"Filas");var columns=dimensions(b.get("columns"),"Columnas");
        var all=new ArrayList<>(rows);all.addAll(columns);
        if(new HashSet<>(all).size()!=all.size())throw bad("Una dimensión se usa una sola vez, en filas o en columnas.");
        var p=object(b.get("period"),"Periodo",Set.of("type","from","to","date_basis"));
        String type=name(p.get("type"),"Tipo de periodo",PERIODS,null);
        if(type==null)throw bad("Indica el tipo de periodo.");
        var basis=Basis.valueOf(name(p.get("date_basis"),"Fecha de análisis",names(Basis.values()),basisOf(measure).name()));
        if(basis==Basis.CREACION_CASO&&measure.unit()!=Unit.CASO)throw bad("La creación del Caso es fecha de análisis solo de la medida Casos.");
        var span=span(type,p);
        var mode=Mode.valueOf(name(b.get("time_mode"),"Modo temporal",names(Mode.values()),Mode.VIGENTE.name()));
        Long at=asOf(mode,b.get("as_of"));
        if(at!=null&&span.from()>at)throw bad("El periodo empieza después de la fecha histórica.");
        var own=reports.reach(a.grants().stream().filter(g->g.role().equals(Roles.REPORTES)).toList());
        var cut=cut(own,b.get("cut_id"),"Corte");
        var comparison=compare(own,b.get("compare"));
        String compare=comparison==null?null:comparison.type();
        if(cut!=null){
            if(mode!=Mode.VIGENTE)throw bad("Un corte guarda los valores de su instante: se consulta en el modo vigente.");
            within(cut,span);
            if(comparison!=null&&comparison.cut()==null)throw bad("Un corte se compara solo con otro corte.");
        }
        if(comparison!=null&&comparison.cut()!=null){
            if(mode!=Mode.VIGENTE)throw bad("La comparación con un corte se hace en el modo vigente.");
            within(comparison.cut(),span);
        }
        String percent=name(b.get("percent"),"Porcentaje",PERCENTS,null);
        Object proposals=b.get("include_proposals");
        if(proposals!=null&&!(proposals instanceof Boolean))throw bad("Propuestas: envía sí o no.");
        var filters=new LinkedHashMap<Dim,List<Object>>();
        if(b.get("filters")!=null)object(b.get("filters"),"Filtros",StatSource.DIMS.keySet()).forEach((k,v)->filters.put(StatSource.DIMS.get(k),filter(StatSource.DIMS.get(k),v,cut)));
        var used=new ArrayList<>(all);used.addAll(filters.keySet());
        for(var d:used){
            if(d.only()&&d.unit()!=measure.unit())throw bad(d.label()+" se usa solo con la medida Completitud por variable.");
            if(d.code().equals("TARDANZA")&&basis!=Basis.RECEPCION)throw bad("El registro tardío se analiza por fecha de recepción.");
        }
        var multiple=all.stream().filter(Dim::multiple).toList();
        Dim mention=null;
        if(b.get("mention_dimension")!=null){
            mention=dimension(b.get("mention_dimension"));
            if(!mention.multiple())throw bad("La dimensión de menciones debe ser de valores múltiples.");
            if(measure.unit()!=Unit.COM)throw bad("Las menciones se cuentan en medidas de requerimientos.");
        }else if(measure.unit()==Unit.COM&&multiple.size()==1)mention=multiple.getFirst();
        if(measure.mentions()&&mention==null)throw bad("Indica la dimensión cuyas menciones se suman.");
        if(measure.code().equals("REQUERIMIENTOS_CON_VALOR")&&multiple.isEmpty())throw bad("Requerimientos con el valor necesita una dimensión de valores múltiples en filas o columnas.");
        // Sobre un corte, sus filas (ya acotadas a su alcance); comparada con un corte, la fuente vigente con el alcance de ese corte.
        var reach=cut!=null?ALL:comparison!=null&&comparison.cut()!=null?comparison.cut().reach():own;
        return new Parsed(new Query(measure,rows,columns,Collections.unmodifiableMap(filters),span,basis,mode,at,mention,Boolean.TRUE.equals(proposals),reach,axes(all,span),cut==null?null:cut.id()),
            type,compare,percent,cut,comparison==null?null:comparison.cut());
    }
    /** Corte de la peticion: 400 si no existe o no se indica bien; 403 si el alcance de REPORTES de la cuenta no lo cubre. */
    private StatCuts.Cut cut(Reports.Reach own,Object raw,String field){
        if(raw==null)return null;
        if(!(raw instanceof Integer||raw instanceof Long)||((Number)raw).longValue()<=0)throw bad(field+": indica el corte.");
        var c=cuts.find(((Number)raw).longValue());
        if(c==null)throw bad("El corte indicado no existe.");
        if(!StatCuts.covers(own,c.reach()))Problem.denied();
        return c;
    }
    private static void within(StatCuts.Cut c,Span span){
        if(span.fromDay().isBefore(c.from())||span.toDay().isAfter(c.to()))throw bad("El periodo consultado está fuera del periodo del corte "+c.code()+".");
    }
    private static Span span(String type,Map<String,Object> p){
        LocalDate from=day(p.get("from"),"Desde"),to;
        if(type.equals("PERSONALIZADO"))to=day(p.get("to"),"Hasta");
        else{
            from=start(type,from);to=next(type,from).minusDays(1);
            if(p.get("to")!=null&&!day(p.get("to"),"Hasta").equals(to))throw bad("Hasta no coincide con el fin del periodo.");
        }
        if(from.isAfter(to))throw bad("El periodo no es válido: Desde es posterior a Hasta.");
        if(from.getYear()<2000||to.getYear()>2099)throw bad("El periodo debe estar entre 2000 y 2099.");
        return span(from,to);
    }
    private static Long asOf(Mode mode,Object raw){
        if(mode!=Mode.FECHA){
            if(raw!=null)throw bad("La fecha histórica se usa solo en el modo a una fecha.");
            return null;
        }
        if(!(raw instanceof String s))throw bad("Indica la fecha histórica con su zona horaria.");
        long at;
        try{at=OffsetDateTime.parse(s).toInstant().toEpochMilli();}catch(DateTimeException e){throw bad("La fecha histórica debe ser ISO 8601 con zona u offset.");}
        if(at<0||at>System.currentTimeMillis())throw bad("La fecha histórica no puede ser futura.");
        return at;
    }
    private record Comparison(String type,StatCuts.Cut cut){}
    private Comparison compare(Reports.Reach own,Object raw){
        if(raw==null)return null;
        var c=object(raw,"Comparación",Set.of("type","cut_id"));
        if(CUT.equals(c.get("type"))){
            var cut=cut(own,c.get("cut_id"),"Comparación");
            if(cut==null)throw bad("Indica el corte con que se compara.");
            return new Comparison(CUT,cut);
        }
        if(c.get("cut_id")!=null)throw bad("La comparación con un corte se indica con el tipo CORTE.");
        String type=name(c.get("type"),"Comparación",COMPARES,null);
        if(type==null)throw bad("Indica la comparación.");
        return new Comparison(type,null);
    }
    /** Valores admitidos de un filtro; sobre un corte, los ambitos y unidades de su instante. */
    private List<Object> filter(Dim d,Object v,StatCuts.Cut cut){
        if(d.kind()==Kind.PERIODO)throw bad("El periodo se acota con las fechas de la consulta, no con un filtro.");
        if(!(v instanceof List<?> list)||list.isEmpty()||list.size()>MAX_FILTER)throw bad(d.label()+": envía una lista de valores.");
        Set<String> known=switch(d.kind()){
            case REGION -> AdultIdentity.REGIONS.keySet();
            case COMUNA -> AdultIdentity.COMMUNES.values().stream().flatMap(List::stream).collect(Collectors.toSet());
            case AMBITO -> cut!=null?cut.scopes().keySet():codes("SELECT code FROM org_scope");
            case UNIDAD -> cut!=null?cut.units().keySet():codes("SELECT code FROM org_unit");
            case PAIS -> AdultIdentity.COUNTRIES;
            case CATALOGO -> codes("SELECT DISTINCT i.code FROM stat_catalog_item i JOIN stat_catalog_version x ON x.id=i.version_id WHERE x.activated_ms IS NOT NULL AND x.catalog=?",d.catalog());
            default -> Set.of();
        };
        var out=new LinkedHashSet<Object>();
        for(Object x:list){
            if(d.kind()==Kind.EDAD)out.add(age(d,x));
            else if(x instanceof String s&&(known.contains(s)||d.options().containsKey(s)||StatSource.SPECIAL.containsKey(s)||d.code().equals("TIPO_ACTUACION")&&StatSource.IMMEDIATE.containsKey(s)))out.add(s);
            else throw bad(d.label()+": valor no reconocido.");
        }
        return List.copyOf(out);
    }
    private static Long age(Dim d,Object x){
        if((x instanceof Integer||x instanceof Long)&&((Number)x).longValue()>=0&&((Number)x).longValue()<=StatCatalogs.AGE_MAX)return ((Number)x).longValue();
        return StatSource.NUMERIC_SPECIAL.entrySet().stream().filter(e->e.getValue().equals(x)).map(Map.Entry::getKey).findFirst().orElseThrow(()->bad(d.label()+": valor no reconocido."));
    }
    private Set<String> codes(String sql,Object... args){return db.rows(sql,args).stream().map(x->s(x,"code")).collect(Collectors.toSet());}

    void validateSeriesCode(String dimension,String code){
        Dim d=dimension(dimension);
        if(d.only())throw bad("La dimensión no admite correspondencias históricas.");
        Object value=code;
        if(d.kind()==Kind.EDAD&&code.matches("[0-9]{1,3}"))value=Integer.valueOf(code);
        filter(d,List.of(value),null);
    }

    // --- Periodos -----------------------------------------------------------------------------------------------------
    static long ms(LocalDate d){return d.atStartOfDay(BusinessCalendar.ZONE).toInstant().toEpochMilli();}
    /** Ambos dias incluidos en hora de Chile: hasta la medianoche siguiente, excluida. */
    static Span span(LocalDate from,LocalDate to){return new Span(from,to,ms(from),ms(to.plusDays(1)));}
    static LocalDate start(String type,LocalDate d){
        return switch(type){
            case "DIA" -> d;
            case "MES" -> d.withDayOfMonth(1);
            case "TRIMESTRE" -> LocalDate.of(d.getYear(),(d.getMonthValue()-1)/3*3+1,1);
            case "SEMESTRE" -> LocalDate.of(d.getYear(),d.getMonthValue()<=6?1:7,1);
            case "ANIO" -> LocalDate.of(d.getYear(),1,1);
            case "CUENTA_PUBLICA" -> LocalDate.of(d.getMonthValue()>=7?d.getYear():d.getYear()-1,7,1);
            default -> throw new IllegalArgumentException(type);
        };
    }
    static LocalDate next(String type,LocalDate start){
        return switch(type){
            case "DIA" -> start.plusDays(1);
            case "MES" -> start.plusMonths(1);
            case "TRIMESTRE" -> start.plusMonths(3);
            case "SEMESTRE" -> start.plusMonths(6);
            case "ANIO","CUENTA_PUBLICA" -> start.plusYears(1);
            default -> throw new IllegalArgumentException(type);
        };
    }
    static String key(String axis,LocalDate s){
        return switch(axis){
            case "DIA" -> s.toString();
            case "MES" -> "%04d-%02d".formatted(s.getYear(),s.getMonthValue());
            case "TRIMESTRE" -> s.getYear()+"-T"+((s.getMonthValue()-1)/3+1);
            case "SEMESTRE" -> s.getYear()+"-S"+(s.getMonthValue()<=6?1:2);
            case "ANIO" -> String.valueOf(s.getYear());
            default -> s.getYear()+"-"+(s.getYear()+1);
        };
    }
    private static String label(String axis,LocalDate s){
        return switch(axis){
            case "DIA" -> s.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            case "MES" -> s.getMonth().getDisplayName(TextStyle.FULL,ES)+" de "+s.getYear();
            case "TRIMESTRE" -> ((s.getMonthValue()-1)/3+1)+".º trimestre de "+s.getYear();
            case "SEMESTRE" -> (s.getMonthValue()<=6?1:2)+".º semestre de "+s.getYear();
            case "ANIO" -> String.valueOf(s.getYear());
            default -> "Cuenta Pública "+s.getYear()+"-"+(s.getYear()+1);
        };
    }
    private Map<String,List<Interval>> axes(List<Dim> dims,Span span){
        var out=new HashMap<String,List<Interval>>();
        for(var d:dims)if(d.kind()==Kind.PERIODO){
            String axis=d.code().substring("PERIODO_".length());
            var list=new ArrayList<Interval>();
            for(LocalDate s=start(axis,span.fromDay());!s.isAfter(span.toDay());s=next(axis,s)){
                if(list.size()>=maxCells)throw tooMany();
                list.add(new Interval(key(axis,s),label(axis,s),Math.min(ms(next(axis,s)),span.to())));
            }
            out.put(d.code(),list);
        }
        return out;
    }
    /** Periodo de la base de comparacion. */
    static Span previous(String compare,String type,Span s){
        if(compare.equals("MISMO_PERIODO_ANIO_ANTERIOR")){
            LocalDate from=s.fromDay().minusYears(1);
            return span(from,type.equals("PERSONALIZADO")?s.toDay().minusYears(1):next(type,from).minusDays(1));
        }
        LocalDate to=s.fromDay().minusDays(1);
        return span(type.equals("PERSONALIZADO")?to.minusDays(s.toDay().toEpochDay()-s.fromDay().toEpochDay()):start(type,to),to);
    }

    // --- Lectura --------------------------------------------------------------------------------------------------------
    record Cell(List<String> keys,long n,long unique,long mentions){}
    /** Datos que acompanan a las celdas, leidos en la misma sentencia. */
    record More(List<Map<String,Object>> late,Map<String,Set<Long>> versions,Map<String,long[]> coverage,Map<String,Long> history){}
    record Result(Map<List<String>,Cell> cells,Map<List<String>,Cell> rows,Map<List<String>,Cell> columns,Cell grand,More more){}

    private Plan.Extras extras(Query q){
        var dims=q.axesDims();
        var used=new LinkedHashSet<>(dims);used.addAll(q.filters().keySet());
        return new Plan.Extras(q.basis()==Basis.RECEPCION?Math.max(System.currentTimeMillis(),q.span().to())+86_400_000L:null,
            dims.stream().filter(d->d.kind()==Kind.CATALOGO&&!d.catalog().equals(StatCatalogs.AGE)).toList(),
            dims.stream().filter(d->DEMOGRAPHIC.contains(d.code())).toList(),
            q.mode()==Mode.VIGENTE?List.of():used.stream().filter(d->HISTORY.contains(d.code())).toList());
    }
    private Result read(Query q,Map<String,List<Interval>> target,int used,Plan.Extras x){
        var dims=q.axesDims();int rows=q.rows().size();
        var sql=new Plan(q).main(Math.max(0,maxCells-used),x);
        var more=new More(new ArrayList<>(),new HashMap<>(),new HashMap<>(),new HashMap<>());
        var cells=new LinkedHashMap<List<String>,Cell>();var rowTotals=new LinkedHashMap<List<String>,Cell>();var columnTotals=new LinkedHashMap<List<String>,Cell>();
        Cell grand=null;
        for(var r:db.rows(sql.text(),sql.args())){
            String part=s(r,"part");
            int i0=part.length()>1?Integer.parseInt(part.substring(1)):0;
            switch(part.charAt(0)){
                case 'L' -> {more.late().add(Map.of("days",n(r,"uq"),"n",n(r,"n")));continue;}
                case 'V' -> {more.versions().computeIfAbsent(x.versions().get(i0).code(),k->new TreeSet<>()).add(n(r,"uq"));continue;}
                case 'D' -> {more.coverage().put(x.coverage().get(i0).code(),new long[]{n(r,"uq"),n(r,"n"),n(r,"mt")});continue;}
                case 'H' -> {more.history().put(x.history().get(i0).code(),n(r,"uq"));continue;}
                default -> {}
            }
            int from=part.equals("K")?rows:0,to=switch(part){case "G"->0;case "R"->rows;default->dims.size();};
            var keys=new ArrayList<String>();
            for(int i=from;i<to;i++)keys.add(key(dims.get(i),q,target,r.get("k"+i)));
            var cell=new Cell(List.copyOf(keys),n(r,"n"),n(r,"uq"),n(r,"mt"));
            switch(part){
                case "C" -> cells.put(cell.keys(),cell);
                case "R" -> rowTotals.put(cell.keys(),cell);
                case "K" -> columnTotals.put(cell.keys(),cell);
                default -> grand=cell;
            }
        }
        if(used+cells.size()>maxCells)throw tooMany();
        fill(dims,target,cells,used);
        if(used+cells.size()>maxCells)throw tooMany();
        if(q.columns().isEmpty())rowTotals=cells;else fill(q.rows(),target,rowTotals,0);
        if(q.rows().isEmpty())columnTotals=cells;else fill(q.columns(),target,columnTotals,0);
        return new Result(cells,rowTotals,columnTotals,grand,more);
    }
    /** Clave publica de un valor; un intervalo de la base de comparacion toma la clave del intervalo en la misma posicion. */
    static String key(Dim d,Query q,Map<String,List<Interval>> target,Object raw){
        // Toda dimension entrega un valor o un estado: una clave nula uniria dos celdas.
        if(raw==null)throw new IllegalStateException("Dimension sin clave: "+d.code());
        if(d.kind()==Kind.PERIODO){
            int i=((Number)raw).intValue();
            if(d.code().equals("PERIODO_DIA"))i-=(int)q.span().fromDay().toEpochDay();
            var list=target.get(d.code());
            return i<list.size()?list.get(i).key():q.axes().get(d.code()).get(i).key();
        }
        if(d.kind()==Kind.EDAD){long v=((Number)raw).longValue();return v<0?StatSource.NUMERIC_SPECIAL.get(v):String.valueOf(v);}
        return raw.toString();
    }
    /** Intervalos vacios con cero: cada intervalo con cada combinacion observada de las demas claves. */
    private void fill(List<Dim> dims,Map<String,List<Interval>> target,Map<List<String>,Cell> cells,int used){
        var periods=new ArrayList<Integer>();
        for(int i=0;i<dims.size();i++)if(dims.get(i).kind()==Kind.PERIODO)periods.add(i);
        if(periods.isEmpty())return;
        var combos=new LinkedHashSet<List<String>>();
        if(periods.size()==dims.size())combos.add(Collections.nCopies(dims.size(),""));
        for(var c:cells.values()){var k=new ArrayList<>(c.keys());periods.forEach(i->k.set(i,""));combos.add(List.copyOf(k));}
        for(int i:periods){
            var grown=new LinkedHashSet<List<String>>();
            for(var c:combos)for(var interval:target.get(dims.get(i).code())){
                var k=new ArrayList<>(c);k.set(i,interval.key());grown.add(List.copyOf(k));
                if(used+grown.size()>maxCells)throw tooMany();
            }
            combos=grown;
        }
        for(var k:combos)cells.putIfAbsent(k,new Cell(k,0,0,0));
    }

    // --- Respuesta --------------------------------------------------------------------------------------------------------
    private Map<String,Object> response(Parsed r,Result main,Result base,Span baseSpan){
        var q=r.query();var dims=q.axesDims();int rows=q.rows().size();
        var warnings=new ArrayList<Map<String,Object>>();
        var versions=new TreeMap<String,TreeSet<Long>>();
        var labels=labels(q,r.cut(),dims,main.more(),versions,warnings);
        var values=new LinkedHashMap<String,Object>();
        var order=new HashMap<String,Map<String,Integer>>();
        for(int i=0;i<dims.size();i++){
            var d=dims.get(i);int at=i;
            var present=new LinkedHashSet<String>();
            if(d.kind()==Kind.PERIODO)q.axes().get(d.code()).forEach(x->present.add(x.key()));
            main.cells().keySet().forEach(k->present.add(k.get(at)));
            if(base!=null)base.cells().keySet().forEach(k->present.add(k.get(at)));
            var sorted=sorted(d,present,labels.get(d.code()));
            var positions=new HashMap<String,Integer>();
            for(var x:sorted)positions.put(x.get("key").toString(),positions.size());
            order.put(d.code(),positions);values.put(d.code(),sorted);
        }
        boolean mentions=q.mention()!=null;
        var out=new LinkedHashMap<String,Object>();
        out.put("metadata",metadata(r,versions));
        out.put("values",values);
        out.put("cells",ordered(main.cells().values(),dims,order).map(c->cell(r,main,c,rows,dims.size(),mentions)).toList());
        var totals=new LinkedHashMap<String,Object>();
        totals.put("grand",total(main.grand(),mentions,false));
        totals.put("rows",rows==0?List.of():ordered(main.rows().values(),q.rows(),order).map(c->total(c,mentions,true)).toList());
        totals.put("columns",rows==dims.size()?List.of():ordered(main.columns().values(),q.columns(),order).map(c->total(c,mentions,true)).toList());
        out.put("totals",totals);
        out.put("comparison",base==null?null:comparison(r,main,base,baseSpan,dims,order));
        out.put("late_records",late(q,main.more()));
        out.put("data_coverage",coverage(dims,main.more()));
        out.put("proposals",q.proposals()?proposals(q,r.cut()):List.of());
        warn(q,main,dims,warnings);
        out.put("warnings",warnings);
        return out;
    }
    private static Map<String,Object> cell(Parsed r,Result main,Cell c,int rows,int all,boolean mentions){
        var row=c.keys().subList(0,rows);var column=c.keys().subList(rows,all);
        var m=new LinkedHashMap<String,Object>();
        m.put("row",row);m.put("column",column);m.put("n",c.n());m.put("unique",c.unique());m.put("mentions",mentions?c.mentions():null);
        Long num=null,den=null;
        if(r.percent()!=null){
            Cell total=switch(r.percent()){
                case "FILA" -> rows==0?main.grand():main.rows().get(row);
                case "COLUMNA" -> rows==all?main.grand():main.columns().get(column);
                default -> main.grand();
            };
            boolean own=r.query().measure().own();
            num=own?c.n():c.unique();den=own?total.n():total.unique();
        }
        m.put("percent_numerator",num);m.put("denominator",den);m.put("percent",den==null||den==0?null:(Double)Reports.round(100d*num/den));
        return m;
    }
    private static Map<String,Object> total(Cell c,boolean mentions,boolean keyed){
        var m=new LinkedHashMap<String,Object>();
        if(keyed)m.put("key",c.keys());
        m.put("n",c.n());m.put("unique",c.unique());m.put("mentions",mentions?c.mentions():null);
        return m;
    }
    private static java.util.stream.Stream<Cell> ordered(Collection<Cell> cells,List<Dim> dims,Map<String,Map<String,Integer>> order){
        return cells.stream().sorted((x,y)->{
            for(int i=0;i<dims.size();i++){
                var positions=order.get(dims.get(i).code());
                int c=Integer.compare(positions.getOrDefault(x.keys().get(i),Integer.MAX_VALUE),positions.getOrDefault(y.keys().get(i),Integer.MAX_VALUE));
                if(c!=0)return c;
            }
            return 0;
        });
    }
    private Map<String,Object> metadata(Parsed r,Map<String,TreeSet<Long>> versions){
        var q=r.query();var m=new LinkedHashMap<String,Object>();
        m.put("methodology_version",METHODOLOGY);m.put("measure",q.measure().code());
        m.put("rows",q.rows().stream().map(Dim::code).toList());m.put("columns",q.columns().stream().map(Dim::code).toList());
        m.put("filters",q.filters().keySet().stream().map(Dim::code).toList());
        var p=period(r.periodType(),q.span());p.put("date_basis",q.basis().name());
        m.put("period",p);m.put("zone",BusinessCalendar.ZONE.getId());m.put("time_mode",q.mode().name());
        // Sobre un corte, el instante es el del corte: sus valores son los vigentes en ese instante.
        Long instant=r.cut()!=null?(Long)r.cut().asOf():q.at();
        m.put("as_of",instant==null?null:Instant.ofEpochMilli(instant).atZone(BusinessCalendar.ZONE).toOffsetDateTime().toString());
        m.put("cut_id",r.cut()==null?null:r.cut().id());m.put("percent",r.percent());m.put("percent_unit",percentUnit(q.measure()));
        m.put("mention_dimension",q.mention()==null?null:q.mention().code());m.put("catalog_versions",versions);
        m.put("calculated_ms",System.currentTimeMillis());
        return m;
    }
    private static Map<String,Object> period(String type,Span s){
        var m=new LinkedHashMap<String,Object>();m.put("type",type);m.put("from",s.fromDay().toString());m.put("to",s.toDay().toString());return m;
    }
    private Map<String,Object> comparison(Parsed r,Result main,Result base,Span span,List<Dim> dims,Map<String,Map<String,Integer>> order){
        int rows=r.query().rows().size();
        var out=new LinkedHashMap<String,Object>();
        out.put("type",r.compare());out.put("period",period(r.periodType(),span));
        if(r.baseCut()!=null){out.put("cut_id",r.baseCut().id());out.put("cut_code",r.baseCut().code());}
        var totals=new LinkedHashMap<String,Object>();
        totals.put("grand",delta(main.grand().n(),base.grand().n()));
        totals.put("rows",rows==0?List.of():deltas(main.rows(),base.rows(),r.query().rows(),order));
        totals.put("columns",rows==dims.size()?List.of():deltas(main.columns(),base.columns(),r.query().columns(),order));
        out.put("totals",totals);
        out.put("cells",keys(main.cells(),base.cells(),dims,order).map(c->{
            var m=new LinkedHashMap<String,Object>();
            m.put("row",c.keys().subList(0,rows));m.put("column",c.keys().subList(rows,dims.size()));
            m.putAll(delta(value(main.cells(),c.keys()),value(base.cells(),c.keys())));
            return m;
        }).toList());
        return out;
    }
    private static java.util.stream.Stream<Cell> keys(Map<List<String>,Cell> main,Map<List<String>,Cell> base,List<Dim> dims,Map<String,Map<String,Integer>> order){
        var keys=new LinkedHashSet<>(main.keySet());keys.addAll(base.keySet());
        return ordered(keys.stream().map(k->new Cell(k,0,0,0)).toList(),dims,order);
    }
    private static List<Map<String,Object>> deltas(Map<List<String>,Cell> main,Map<List<String>,Cell> base,List<Dim> dims,Map<String,Map<String,Integer>> order){
        return keys(main,base,dims,order).map(c->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("key",c.keys());m.putAll(delta(value(main,c.keys()),value(base,c.keys())));return m;
        }).toList();
    }
    private static long value(Map<List<String>,Cell> cells,List<String> key){var c=cells.get(key);return c==null?0:c.n();}
    /** Cifra base, diferencia y variacion porcentual sobre la base (nula con base cero). */
    static Map<String,Object> delta(long n,long base){
        var m=new LinkedHashMap<String,Object>();
        m.put("base_n",base);m.put("delta_n",n-base);m.put("delta_percent",base==0?null:(Double)Reports.round(100d*(n-base)/base));
        return m;
    }
    /** Registros tardios de la poblacion consultada; solo por fecha de recepcion (D26). */
    private static Map<String,Object> late(Query q,More more){
        if(q.basis()!=Basis.RECEPCION)return null;
        var rows=more.late().stream().sorted(Comparator.comparingLong(x->(Long)x.get("days"))).toList();
        var m=new LinkedHashMap<String,Object>();
        m.put("n",rows.stream().mapToLong(x->(Long)x.get("n")).sum());m.put("by_delay_days",rows);
        return m;
    }
    private static Map<String,Object> coverage(List<Dim> dims,More more){
        var out=new LinkedHashMap<String,Object>();
        for(var d:dims)if(more.coverage().containsKey(d.code())){
            var c=more.coverage().get(d.code());
            var m=new LinkedHashMap<String,Object>();
            m.put("with_data",c[0]);m.put("not_applicable",c[1]);m.put("total",c[2]);m.put("percent",c[2]==c[1]?null:(Double)Reports.round(100d*c[0]/(c[2]-c[1])));
            out.put(d.code(),m);
        }
        return out;
    }
    /** Propuestas con el rotulo del catalogo vigente (sobre un corte, el vigente en su instante). */
    private List<Map<String,Object>> proposals(Query q,StatCuts.Cut cut){
        var sql=new Plan(q).proposals();
        var current=new HashMap<String,Map<String,String>>();
        var rows=cut==null?db.rows("SELECT x.catalog AS catalog_code,i.code,i.label FROM stat_catalog_item i JOIN stat_catalog_version x ON x.id=i.version_id WHERE x.`current_catalog` IN ('TIPO_SOLICITUD','TIPO_REQUIRENTE')")
            :new ArrayList<Map<String,Object>>();
        if(cut!=null)for(String v:StatSource.PROPOSED)if(cut.version(v)!=null)
            for(var x:db.rows("SELECT i.code,i.label FROM stat_catalog_item i WHERE i.version_id=?",cut.version(v))){var y=new HashMap<>(x);y.put("catalog_code",v);rows.add(y);}
        for(var x:rows)
            current.computeIfAbsent(s(x,"catalog_code"),k->new HashMap<>()).put(s(x,"code"),s(x,"label"));
        return db.rows(sql.text(),sql.args()).stream().map(x->{
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("variable",s(x,"variable"));m.put("code",s(x,"code"));m.put("label",current.getOrDefault(s(x,"variable"),Map.of()).get(s(x,"code")));m.put("rule",StatProposals.RULE);m.put("n",n(x,"n"));
            return m;
        }).sorted(Comparator.comparing(x->x.get("variable")+"|"+x.get("code"))).toList();
    }

    // --- Rotulos, versiones y avisos ------------------------------------------------------------------------------------------
    private record Label(String text,long sort){}
    /** Sobre un corte, los rotulos de ambito y unidad y el catalogo de tramos de edad son los de su instante (congelados). */
    private Map<String,Map<String,Label>> labels(Query q,StatCuts.Cut cut,List<Dim> dims,More more,Map<String,TreeSet<Long>> versions,List<Map<String,Object>> warnings){
        var out=new HashMap<String,Map<String,Label>>();
        var mixed=new ArrayList<String>();var missing=new ArrayList<String>();
        for(var d:dims){
            var map=new HashMap<String,Label>();out.put(d.code(),map);
            switch(d.kind()){
                case PERIODO -> {long i=0;for(var x:q.axes().get(d.code()))map.put(x.key(),new Label(x.label(),i++));}
                case REGION -> {long i=0;for(var x:AdultIdentity.REGIONS.entrySet())map.put(x.getKey(),new Label(x.getValue(),i++));}
                case AMBITO -> {long i=0;for(var x:(cut!=null?cut.scopes():names("SELECT code,name FROM org_scope ORDER BY code")).entrySet())map.put(x.getKey(),new Label(x.getValue(),i++));}
                case UNIDAD -> {long i=0;for(var x:(cut!=null?cut.units():names("SELECT code,name FROM org_unit ORDER BY code")).entrySet())map.put(x.getKey(),new Label(x.getValue(),i++));}
                case CATALOGO -> {
                    var found=d.catalog().equals(StatCatalogs.AGE)?(cut!=null?(cut.version(StatCatalogs.AGE)==null?Set.<Long>of():Set.of(cut.version(StatCatalogs.AGE))):ageVersion()):more.versions().getOrDefault(d.code(),Set.of());
                    if(found.isEmpty()&&d.catalog().equals(StatCatalogs.AGE))missing.add(d.code());
                    if(found.size()>1)mixed.add(d.code());
                    if(!found.isEmpty())versions.computeIfAbsent(d.catalog(),k->new TreeSet<>()).addAll(found);
                }
                default -> {}
            }
            long i=1_000_000;
            for(var x:d.options().entrySet())map.putIfAbsent(x.getKey(),new Label(x.getValue(),i++));
            if(d.code().equals("TIPO_ACTUACION"))for(var x:StatSource.IMMEDIATE.entrySet())map.put(x.getKey(),new Label(x.getValue(),i++));
        }
        // Rotulo y orden segun la version con que se registro el valor; con varias, la mas reciente.
        var ids=versions.values().stream().flatMap(Set::stream).map(String::valueOf).collect(Collectors.joining(","));
        var items=ids.isEmpty()?List.<Map<String,Object>>of():db.rows("SELECT version_id,code,label,sort FROM stat_catalog_item WHERE version_id IN ("+ids+") ORDER BY version_id,sort");
        for(var d:dims)if(d.kind()==Kind.CATALOGO)for(var x:items)
            if(versions.getOrDefault(d.catalog(),new TreeSet<>()).contains(n(x,"version_id")))out.get(d.code()).put(s(x,"code"),new Label(s(x,"label"),n(x,"sort")));
        if(!mixed.isEmpty())warnings.add(warning("VERSIONES_DISTINTAS",mixed,null));
        if(!missing.isEmpty())warnings.add(warning("SIN_CATALOGO_VIGENTE",missing,null));
        return out;
    }
    private Map<String,String> names(String sql){
        var m=new LinkedHashMap<String,String>();for(var x:db.rows(sql))m.put(s(x,"code"),s(x,"name"));return m;
    }
    private Set<Long> ageVersion(){
        Long current=StatValues.currentVersion(db,StatCatalogs.AGE);
        return current==null?Set.of():Set.of(current);
    }
    private static Map<String,Object> warning(String code,List<String> dimensions,Long n){
        var m=new LinkedHashMap<String,Object>();m.put("code",code);m.put("dimensions",dimensions);if(n!=null)m.put("n",n);return m;
    }
    private static List<Map<String,Object>> sorted(Dim d,Set<String> present,Map<String,Label> labels){
        var collator=Collator.getInstance(ES);
        var special=new ArrayList<>(StatSource.SPECIAL.keySet());
        return present.stream().sorted((x,y)->{
            boolean sx=special(d,x),sy=special(d,y);
            if(sx!=sy)return sx?1:-1;
            if(sx)return Integer.compare(special.indexOf(x),special.indexOf(y));
            if(d.kind()==Kind.EDAD)return Long.compare(Long.parseLong(x),Long.parseLong(y));
            var lx=labels.get(x);var ly=labels.get(y);
            int c=Long.compare(lx==null?Long.MAX_VALUE:lx.sort(),ly==null?Long.MAX_VALUE:ly.sort());
            return c!=0?c:collator.compare(x,y);
        }).map(x->{
            var l=labels.get(x);
            String text=special(d,x)?StatSource.SPECIAL.get(x):l!=null?l.text():d.kind()==Kind.EDAD?x+(x.equals("1")?" año":" años"):x;
            return Map.<String,Object>of("key",x,"label",text);
        }).toList();
    }
    private static boolean special(Dim d,String key){return StatSource.SPECIAL.containsKey(key)&&!d.options().containsKey(key);}
    private static void warn(Query q,Result main,List<Dim> dims,List<Map<String,Object>> warnings){
        // Con varias categorias por requerimiento los porcentajes pueden sumar mas de 100 %.
        var multiple=dims.stream().filter(d->d.multiple()&&!(q.measure().own()&&d.unit()==q.measure().unit())).map(Dim::code).toList();
        if(!multiple.isEmpty())warnings.addFirst(warning("PORCENTAJES_MULTIVALOR",multiple,null));
        var pending=new ArrayList<String>();
        for(int i=0;i<dims.size();i++){
            var d=dims.get(i);int at=i;
            if(special(d,StatSource.PENDING)&&main.cells().values().stream().anyMatch(c->c.unique()>0&&c.keys().get(at).equals(StatSource.PENDING)))pending.add(d.code());
        }
        if(!pending.isEmpty())warnings.add(warning("VARIABLES_PENDIENTES",pending,null));
        main.more().history().forEach((d,n)->{if(n>0)warnings.add(warning("HISTORIA_INCOMPLETA",List.of(d),n));});
    }
}
