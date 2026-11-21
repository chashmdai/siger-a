package cl.nexbyte.sigera;

import cl.nexbyte.sigera.Access.Actor;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import static cl.nexbyte.sigera.Db.*;

/**
 * Reportes de gestion (0.5.0, D6, gate G11): cifras agregadas sin datos de personas, calculadas en Java sobre los hitos
 * (V14) con el calendario habil (V13). POST /api/reports; solo cuentas con REPORTES (TI recibe 403 por D2).
 *
 * Alcance de cada concesion REPORTES (o REPORTES_PERSONAS para el desglose por funcionario): nulo = institucion; raiz de
 * una unidad (ambito central sin area) = todos sus ambitos centrales; area o sede = ese ambito; raiz de UPR, UPD o UEP =
 * ademas los registros de sedes con su materia (D9). Una concesion sobre una sede cerrada deja de regir (Access.GRANTS),
 * pero los registros historicos de sedes cerradas siguen en los reportes de institucion, unidad o materia: el alcance no
 * exige org_scope.enabled. El alcance y los filtros se aplican en SQL antes de contar.
 *
 * Tiempos: cada tramo empareja un hito de fin con el ultimo hito de inicio valido de la misma clave (los "reset"
 * anulan el inicio abierto). Un tramo entra en el periodo si su fin cae dentro de el. Por grupo: n, mediana, p90
 * (interpolacion lineal, como PERCENTIL.INCLUSIVE: rango p*(n-1)) y maximo, en horas corridas y en dias habiles (feriados
 * nacionales mas los de la region de la sede; nivel central = solo nacionales), y cuantos tramos son reconstruidos. El
 * ambito del tramo es el del hito ancla (fin, o inicio en Tenencia de SOL y Apertura de Caso). Persona = quien realiza el
 * acto que termina el tramo; en Gestion del Caso, el responsable del Caso. Limite de filas: 409, nunca un resultado truncado.
 */
@Service
public class Reports {
    static final int LIMIT=20_000,CHUNK=500;
    /** Tramos por reporte (las pruebas lo reducen). */
    int limit=LIMIT;
    static final String CALENDAR_NOTE="Calculado con el calendario hábil vigente.";
    static final String TOO_MANY="El reporte supera el límite de %s filas: acota el periodo o el ámbito.";

    enum Family{COM,REQ,CAS}
    enum Anchor{START,END}
    /** Un tramo de tiempo: clave de emparejamiento (columna y tipo de recurso), inicios, fines, anulaciones y ancla. */
    record Spec(String code,Family family,Anchor anchor,String key,String type,Set<String> starts,Set<String> ends,Set<String> resets,boolean firstStart,boolean waits,boolean responsible,boolean received){
        Set<String> events(){var all=new LinkedHashSet<String>();all.addAll(starts);all.addAll(ends);all.addAll(resets);if(waits){all.add("CASO_ESPERA");all.add("CASO_REANUDADO");}return all;}
    }
    private static Spec spec(String code,Family f,Anchor a,String key,String type,Set<String> starts,Set<String> ends,Set<String> resets){
        return new Spec(code,f,a,key,type,starts,ends,resets,false,false,false,false);
    }
    private static final Set<String> ARRIVALS=Set.of("SOL_DISTRIBUIDA","SOL_REDIRIGIDA");
    static final List<Spec> TIMES=List.of(
        new Spec("REGISTRO",Family.COM,Anchor.END,"communication_id",null,Set.of(),Set.of("COM_REGISTRADA"),Set.of(),false,false,false,true),
        spec("DISTRIBUCION",Family.COM,Anchor.END,"communication_id",null,Set.of("COM_REGISTRADA"),Set.of("SOL_DISTRIBUIDA"),Set.of()),
        // Cada llegada a una jefatura hasta su salida (asumida, redirigida, devuelta o descartada).
        spec("TENENCIA_SOL",Family.COM,Anchor.START,"resource_id","SOL",ARRIVALS,Set.of("SOL_ASUMIDA","SOL_REDIRIGIDA","SOL_DEVUELTA","SOL_DESCARTADA"),Set.of()),
        // Ultima llegada hasta la asuncion.
        spec("ASUNCION",Family.COM,Anchor.END,"resource_id","SOL",ARRIVALS,Set.of("SOL_ASUMIDA"),Set.of("SOL_DEVUELTA","SOL_DESCARTADA")),
        // Primera distribucion hasta la asuncion.
        new Spec("SOL_TOTAL",Family.COM,Anchor.END,"resource_id","SOL",Set.of("SOL_DISTRIBUIDA"),Set.of("SOL_ASUMIDA"),Set.of("SOL_DESCARTADA"),true,false,false,false),
        spec("APERTURA_CASO",Family.REQ,Anchor.START,"requirement_id",null,Set.of("REQ_CREADO"),Set.of("CASO_CREADO"),Set.of()),
        spec("ACUSE",Family.CAS,Anchor.END,"resource_id","ASIGNACION",Set.of("CASO_ASIGNADO"),Set.of("ASIGNACION_ACUSADA"),Set.of()),
        new Spec("GESTION_CASO",Family.CAS,Anchor.END,"case_id",null,Set.of("CASO_CREADO","CASO_REABIERTO"),Set.of("CASO_CERRADO"),Set.of(),false,true,true,false),
        // Sin solicitud previa no hay revision; retirar la solicitud la anula.
        spec("REVISION_CIERRE",Family.CAS,Anchor.END,"case_id",null,Set.of("CIERRE_SOLICITADO"),Set.of("CASO_CERRADO","CIERRE_DEVUELTO"),Set.of("CIERRE_RETIRADO")),
        spec("REQ_TOTAL",Family.REQ,Anchor.END,"requirement_id",null,Set.of("REQ_CREADO","REQ_REACTIVADO"),Set.of("REQ_CERRADO"),Set.of()),
        spec("PRIMERA_ATENCION",Family.REQ,Anchor.END,"resource_id","ENTREGA",Set.of("ENTREGA_ENVIADA"),Set.of("ENTREGA_DECIDIDA"),Set.of()),
        spec("FLUJOS",Family.REQ,Anchor.END,"resource_id","FLUJO",Set.of("FLUJO_SOLICITADO"),Set.of("FLUJO_DECIDIDO"),Set.of()));
    static final String VOLUME="VOLUMEN",AGE="ANTIGUEDAD";
    static final List<String> GROUPS=List.of("NINGUNA","UNIDAD","AMBITO","REGION","MATERIA","ORIGEN","AMBITO_MATERIA","SALIDA","PERSONA");
    static final List<String> BRACKETS=List.of("0-5","6-10","11-20","21+");

    /** Catalogo publicado en /api/catalog. */
    static List<Map<String,Object>> catalog(){
        var out=new ArrayList<Map<String,Object>>();
        out.add(Map.of("code",VOLUME,"kind","VOLUMEN"));
        for(var t:TIMES)out.add(Map.of("code",t.code(),"kind","TIEMPO"));
        out.add(Map.of("code",AGE,"kind","ANTIGUEDAD"));
        return out;
    }

    /** Cuerpo de POST /api/reports. Fechas aaaa-mm-dd en hora de Chile, ambas incluidas. */
    public record Request(String report,String from,String to,String unit,Long scope,String region,String level,String complexity,
                          String channel,String matter,String modality,String origin,String group,Boolean by_origin,Boolean by_exit){ }

    private final Db db;
    public Reports(Db db){this.db=db;}

    // --- Alcance ---------------------------------------------------------------------------------
    /** Alcance resuelto: institucion completa, o ambitos mas materias en sedes. */
    record Reach(boolean all,Set<Long> scopes,Set<String> matters){
        boolean empty(){return !all&&scopes.isEmpty()&&matters.isEmpty();}
    }
    static boolean canReport(Actor a){return a.role(Roles.REPORTES);}
    /** REPORTES_PERSONAS que rige: con REPORTES vigente en el mismo alcance. */
    static List<Access.Grant> peopleGrants(Actor a){
        return a.grants().stream().filter(g->g.role().equals(Roles.REPORTES_PERSONAS)&&a.grants().stream().anyMatch(x->x.role().equals(Roles.REPORTES)&&Objects.equals(x.scope(),g.scope()))).toList();
    }
    static boolean canReportPeople(Actor a){return !peopleGrants(a).isEmpty();}

    Reach reach(List<Access.Grant> grants){
        boolean all=false;var scopes=new TreeSet<Long>();var matters=new TreeSet<String>();
        for(var g:grants){
            if(g.scope()==null){all=true;continue;}
            var s=db.one("SELECT id,unit_code,area_code,site_code,regional FROM org_scope WHERE id=?",g.scope());
            boolean root=!b(s,"regional")&&s(s,"site_code").equals("CENTRAL")&&s(s,"area_code").isEmpty();
            if(root){
                for(var x:db.rows("SELECT id FROM org_scope WHERE unit_code=? AND regional=FALSE",s(s,"unit_code")))scopes.add(n(x,"id"));
                if(Access.NATIONAL_MATTERS.contains(s(s,"unit_code")))matters.add(s(s,"unit_code"));
            }else scopes.add(n(s,"id"));
        }
        return new Reach(all,scopes,matters);
    }

    // --- Consulta --------------------------------------------------------------------------------
    /** Condiciones SQL con sus valores. */
    static final class Where{
        final StringBuilder sql=new StringBuilder("1=1");final List<Object> args=new ArrayList<>();
        Where and(String condition,Object... values){sql.append(" AND ").append(condition);args.addAll(Arrays.asList(values));return this;}
    }
    private static String marks(int n){return String.join(",",Collections.nCopies(n,"?"));}
    static final String ORIGIN="CASE WHEN r.id IS NOT NULL AND r.solicitation_id IS NULL THEN 'ATENCION_DIRECTA' ELSE 'SOL' END";
    static final String INTAKE="CASE WHEN c.external_source IS NOT NULL THEN 'FORMULARIO' WHEN r.id IS NOT NULL AND r.solicitation_id IS NULL THEN 'ATENCION_DIRECTA' ELSE 'MANUAL' END";
    static String matter(Family f){return switch(f){case COM->"COALESCE(r.matter,sol.suggested_matter)";case REQ->"r.matter";case CAS->"cs.matter";};}

    private record Filters(long from,long to,LocalDate fromDay,LocalDate toDay,boolean period,List<String> used,Request q){ }
    private static final Pattern CODE=Pattern.compile("[A-Z][A-Z_]{0,31}");
    private static String opt(String v){return v==null||v.isBlank()?null:v.strip();}
    private static String closed(String v,String label,String... allowed){
        v=opt(v);if(v!=null&&!List.of(allowed).contains(v))throw new Problem(400,label+" no valido.");return v;
    }
    private static LocalDate day(String v,String label){
        try{return LocalDate.parse(v);}catch(DateTimeParseException e){throw new Problem(400,label+": usa el formato aaaa-mm-dd.");}
    }
    private Filters filters(Request q){
        var used=new ArrayList<String>();
        LocalDate today=BusinessCalendar.date(System.currentTimeMillis());
        LocalDate to=opt(q.to())==null?today:day(q.to(),"Hasta"),from=opt(q.from())==null?to.minusYears(1).plusDays(1):day(q.from(),"Desde");
        if(opt(q.from())!=null||opt(q.to())!=null)used.add("periodo");
        if(from.isAfter(to))throw new Problem(400,"El periodo es invalido: Desde es posterior a Hasta.");
        if(from.getYear()<2000||to.getYear()>2099)throw new Problem(400,"El periodo debe estar entre 2000 y 2099.");
        closed(q.complexity(),"Complejidad","BAJA","MEDIANA","ALTA","SIN");closed(q.matter(),"Materia","UPR","UPD","UEP","OTRA","SIN");
        closed(q.modality(),"Modalidad","GENERAL","NNA");closed(q.origin(),"Origen","SOL","ATENCION_DIRECTA");closed(q.level(),"Nivel","CENTRAL","REGIONAL");
        if(opt(q.channel())!=null&&!CODE.matcher(opt(q.channel())).matches())throw new Problem(400,"Canal no valido.");
        if(opt(q.unit())!=null&&!CODE.matcher(opt(q.unit())).matches())throw new Problem(400,"Unidad no valida.");
        if(opt(q.region())!=null&&!AdultIdentity.validRegion(opt(q.region())))throw new Problem(400,"Region no valida.");
        String[][] names={{q.unit(),"unidad"},{q.scope()==null?null:"x","ambito"},{q.region(),"region"},{q.level(),"nivel"},{q.complexity(),"complejidad"},
            {q.channel(),"canal"},{q.matter(),"materia"},{q.modality(),"modalidad"},{q.origin(),"origen"}};
        for(var nm:names)if(opt(nm[0])!=null)used.add(nm[1]);
        long fromMs=from.atStartOfDay(BusinessCalendar.ZONE).toInstant().toEpochMilli(),toMs=to.plusDays(1).atStartOfDay(BusinessCalendar.ZONE).toInstant().toEpochMilli();
        return new Filters(fromMs,toMs,from,to,used.contains("periodo"),used,q);
    }
    /** Alcance y filtros sobre los alias comunes: s (ambito), c, r, sol, ar y la expresion de materia. */
    private static void apply(Where w,Reach reach,Filters f,String matter){
        if(!reach.all()){
            var parts=new ArrayList<String>();
            if(!reach.scopes().isEmpty()){parts.add("s.id IN ("+marks(reach.scopes().size())+")");}
            if(!reach.matters().isEmpty())parts.add("(s.regional=TRUE AND "+matter+" IN ("+marks(reach.matters().size())+"))");
            var args=new ArrayList<Object>(reach.scopes());args.addAll(reach.matters());
            w.and("("+String.join(" OR ",parts)+")",args.toArray());
        }
        var q=f.q();
        if(opt(q.unit())!=null)w.and("s.unit_code=?",opt(q.unit()));
        if(q.scope()!=null)w.and("s.id=?",q.scope());
        if(opt(q.region())!=null)w.and("s.region_code=?",opt(q.region()));
        if(opt(q.level())!=null)w.and("s.regional=?",opt(q.level()).equals("REGIONAL"));
        if(opt(q.complexity())!=null){if(opt(q.complexity()).equals("SIN"))w.and("r.complexity IS NULL");else w.and("r.complexity=?",opt(q.complexity()));}
        if(opt(q.channel())!=null)w.and("c.channel=?",opt(q.channel()));
        if(opt(q.matter())!=null){if(opt(q.matter()).equals("SIN"))w.and(matter+" IS NULL");else w.and(matter+"=?",opt(q.matter()));}
        if(opt(q.modality())!=null)w.and("ar.modality=?",opt(q.modality()));
        if(opt(q.origin())!=null)w.and(ORIGIN+"=?",opt(q.origin()));
    }

    // --- Entrada ---------------------------------------------------------------------------------
    public Map<String,Object> report(Actor a,Request q){
        if(!canReport(a))Problem.denied();
        String code=opt(q.report());
        boolean volume=VOLUME.equals(code),age=AGE.equals(code);
        var spec=TIMES.stream().filter(t->t.code().equals(code)).findFirst().orElse(null);
        if(!volume&&!age&&spec==null)throw new Problem(400,"Reporte desconocido.");
        String group=opt(q.group())==null?"NINGUNA":opt(q.group());
        if(!GROUPS.contains(group))throw new Problem(400,"Agrupacion no valida.");
        if(volume&&!group.equals("NINGUNA"))throw new Problem(400,"El volumen se entrega con su desglose fijo; no admite agrupacion.");
        if(age&&(group.equals("SALIDA")||group.equals("PERSONA")))throw new Problem(400,"La antiguedad de abiertos no admite esa agrupacion.");
        // El desglose por funcionario solo con REPORTES_PERSONAS, y en el alcance de esas concesiones.
        boolean people=group.equals("PERSONA");
        if(people&&!canReportPeople(a))Problem.denied();
        var reach=reach(people?peopleGrants(a):a.grants().stream().filter(g->g.role().equals(Roles.REPORTES)).toList());
        var f=filters(q);
        var calendar=BusinessCalendar.load(db);
        var out=new LinkedHashMap<String,Object>();
        out.put("report",code);out.put("group",group);out.put("limit",limit);
        if(volume)out.putAll(volume(reach,f));
        else if(age)out.putAll(age(reach,f,group,calendar));
        else out.putAll(times(spec,reach,f,group,calendar));
        if(!age){out.put("from",f.fromDay().toString());out.put("to",f.toDay().toString());}
        db.audit(a.id(),"REPORTE","SISTEMA",0,null,"Reporte "+code+"; agrupacion "+group+(f.used().isEmpty()?"":"; filtros: "+String.join(", ",f.used())));
        return out;
    }
    private static Map<String,Object> calendarInfo(BusinessCalendar cal,long from,long to){
        var years=cal.missingYears(from,Math.max(from,to));
        var out=new LinkedHashMap<String,Object>();
        out.put("note",CALENDAR_NOTE);out.put("missing_years",years);
        out.put("warnings",years.stream().map(y->"Sin feriados cargados para "+y+".").toList());
        return out;
    }
    private static String region(Map<String,Object> row){return b(row,"regional")&&row.get("region_code")!=null?s(row,"region_code"):null;}

    // --- Tiempos ---------------------------------------------------------------------------------
    private static final String FROM="milestone e JOIN communication c ON c.id=e.communication_id LEFT JOIN requirement r ON r.communication_id=e.communication_id"
        +" LEFT JOIN solicitation sol ON sol.communication_id=e.communication_id LEFT JOIN case_record cs ON cs.id=e.case_id"
        +" LEFT JOIN attention_record ar ON ar.requirement_id=r.id JOIN org_scope s ON s.id=e.scope_id";
    private record Ev(long id,String event,long at,long actor,Long scope,boolean rebuilt){ }
    /** Un tramo calculado, con sus dimensiones. */
    private record Span(Map<String,Object> dims,String exit,Long person,double hours,double days,boolean rebuilt,double waitHours,double waitDays,boolean waited){ }

    private Map<String,Object> times(Spec t,Reach reach,Filters f,String group,BusinessCalendar cal){
        var w=new Where();
        w.and("e.event IN ("+marks((t.anchor()==Anchor.END?t.ends():t.starts()).size())+")",(t.anchor()==Anchor.END?t.ends():t.starts()).toArray());
        String keyType=t.type()==null?"":" AND y.resource_type=?";
        if(t.anchor()==Anchor.END)w.and("e.at_ms>=? AND e.at_ms<?",f.from(),f.to());
        else{
            var args=new ArrayList<Object>();if(t.type()!=null)args.add(t.type());args.addAll(t.ends());args.add(f.from());args.add(f.to());args.add(f.to());
            w.and("EXISTS(SELECT 1 FROM milestone y WHERE y."+t.key()+"=e."+t.key()+keyType+" AND y.event IN ("+marks(t.ends().size())+") AND y.at_ms>=? AND y.at_ms<?) AND e.at_ms<?",args.toArray());
        }
        if(t.type()!=null)w.and("e.resource_type=?",t.type());
        apply(w,reach,f,matter(t.family()));
        long total=db.count("SELECT COUNT(*) FROM "+FROM+" WHERE "+w.sql,w.args.toArray());
        Problem.require(total<=limit,TOO_MANY.formatted(String.format(Locale.ROOT,"%,d",limit).replace(',','.')));
        var anchors=new HashMap<Long,Map<String,Object>>();
        for(var r:db.rows("SELECT e.id AS mid,e."+t.key()+" AS key_id,c.received_ms,s.id AS scope_id,s.code AS scope_code,s.name AS scope_name,s.unit_code,s.regional,s.region_code,"
            +matter(t.family())+" AS matter,"+ORIGIN+" AS origin,cs.responsible_id FROM "+FROM+" WHERE "+w.sql,w.args.toArray()))anchors.put(n(r,"mid"),r);
        // Secuencia completa de cada clave (tambien fuera del periodo) para emparejar inicio y fin.
        var keys=anchors.values().stream().map(r->n(r,"key_id")).distinct().toList();
        var byKey=new HashMap<Long,List<Ev>>();
        var events=t.events();
        for(int i=0;i<keys.size();i+=CHUNK){
            var part=keys.subList(i,Math.min(keys.size(),i+CHUNK));
            var args=new ArrayList<Object>(events);if(t.type()!=null)args.add(t.type());args.addAll(part);
            for(var r:db.rows("SELECT id,event,at_ms,actor_id,scope_id,reconstructed,"+t.key()+" AS key_id FROM milestone WHERE event IN ("+marks(events.size())+")"
                +(t.type()==null?"":" AND resource_type=?")+" AND "+t.key()+" IN ("+marks(part.size())+")",args.toArray()))
                byKey.computeIfAbsent(n(r,"key_id"),k->new ArrayList<>()).add(new Ev(n(r,"id"),s(r,"event"),n(r,"at_ms"),n(r,"actor_id"),nullableId(r,"scope_id"),b(r,"reconstructed")));
        }
        var spans=new ArrayList<Span>();long earliest=f.from();
        for(var seq:byKey.values()){
            // Orden temporal; con el mismo instante, un inicio puro antes que un fin (atencion resuelta en el acto).
            seq.sort(Comparator.comparingLong(Ev::at).thenComparingInt(e->t.starts().contains(e.event())&&!t.ends().contains(e.event())?0:1).thenComparingLong(Ev::id));
            Ev open=null;Long waitFrom=null;var waits=new ArrayList<long[]>();
            for(var e:seq){
                if(t.waits()){
                    if(e.event().equals("CASO_ESPERA")&&open!=null&&waitFrom==null)waitFrom=e.at();
                    if(e.event().equals("CASO_REANUDADO")&&waitFrom!=null){waits.add(new long[]{waitFrom,e.at()});waitFrom=null;}
                }
                if(t.ends().contains(e.event())){
                    Ev start=t.received()?e:open;
                    if(start!=null){
                        if(waitFrom!=null)waits.add(new long[]{waitFrom,e.at()});
                        var anchor=anchors.get(t.anchor()==Anchor.END?e.id():start.id());
                        boolean inPeriod=e.at()>=f.from()&&e.at()<f.to();
                        if(anchor!=null&&inPeriod){
                            long from=t.received()?Math.min(n(anchor,"received_ms"),e.at()):start.at();
                            earliest=Math.min(earliest,from);
                            spans.add(span(t,anchor,start,e,from,waits,cal));
                        }
                    }
                    open=null;waitFrom=null;waits.clear();
                }
                if(t.resets().contains(e.event())){open=null;waitFrom=null;waits.clear();}
                if(t.starts().contains(e.event())&&(open==null||!t.firstStart())){open=e;waitFrom=null;waits.clear();}
            }
        }
        var dims=dimensions(group,t.code().equals("REQ_TOTAL")&&!Boolean.FALSE.equals(f.q().by_origin())||Boolean.TRUE.equals(f.q().by_origin()),
            t.code().equals("TENENCIA_SOL")&&!Boolean.FALSE.equals(f.q().by_exit())||Boolean.TRUE.equals(f.q().by_exit()));
        var names=people(dims.contains("PERSONA")?spans.stream().map(Span::person).filter(Objects::nonNull).distinct().toList():List.of());
        var groups=new TreeMap<String,List<Span>>();var keysOf=new HashMap<String,Map<String,Object>>();
        for(var sp:spans){
            var key=key(dims,sp,names);String k=key.values().toString();
            groups.computeIfAbsent(k,x->new ArrayList<>()).add(sp);keysOf.putIfAbsent(k,key);
        }
        var rows=new ArrayList<Map<String,Object>>();
        for(var g:groups.entrySet()){var row=new LinkedHashMap<String,Object>();row.put("group",keysOf.get(g.getKey()));row.putAll(stats(g.getValue(),t.waits()));rows.add(row);}
        var out=new LinkedHashMap<String,Object>();
        out.put("kind","TIEMPO");out.put("dimensions",dims);out.put("total",stats(spans,t.waits()));out.put("rows",rows);
        out.put("calendar",calendarInfo(cal,earliest,f.to()-1));
        return out;
    }
    private Span span(Spec t,Map<String,Object> anchor,Ev start,Ev end,long from,List<long[]> waits,BusinessCalendar cal){
        long to=Math.max(from,end.at());String region=region(anchor);
        double hours=BusinessCalendar.hours(from,to);double days=cal.businessDays(from,to,region);
        double wh=0,wd=0;
        for(var x:waits){long a=Math.max(x[0],from),b=Math.min(x[1],to);if(b>a){wh+=BusinessCalendar.hours(a,b);wd+=cal.businessDays(a,b,region);}}
        Long person=t.responsible()?nullableId(anchor,"responsible_id"):end.actor();
        return new Span(anchor,end.event(),person,hours-wh,days-wd,start.rebuilt()||end.rebuilt(),wh,wd,!waits.isEmpty());
    }
    /** Dimensiones de la agrupacion, mas la separacion por origen (REQ total) o por salida (Tenencia de SOL) por defecto. */
    static List<String> dimensions(String group,boolean byOrigin,boolean byExit){
        var dims=new ArrayList<String>();
        switch(group){
            case "NINGUNA" -> {}
            case "AMBITO_MATERIA" -> {dims.add("AMBITO");dims.add("MATERIA");}
            default -> dims.add(group);
        }
        if(byOrigin&&!dims.contains("ORIGEN"))dims.add("ORIGEN");
        if(byExit&&!dims.contains("SALIDA"))dims.add("SALIDA");
        return dims;
    }
    private Map<Long,String> people(List<Long> ids){
        var out=new HashMap<Long,String>();
        for(int i=0;i<ids.size();i+=CHUNK){var part=ids.subList(i,Math.min(ids.size(),i+CHUNK));
            for(var r:db.rows("SELECT id,name FROM staff WHERE id IN ("+marks(part.size())+")",part.toArray()))out.put(n(r,"id"),s(r,"name"));}
        return out;
    }
    private static Map<String,Object> key(List<String> dims,Span sp,Map<Long,String> names){
        var d=sp.dims();var key=new LinkedHashMap<String,Object>();
        for(var dim:dims)switch(dim){
            case "UNIDAD" -> key.put("unit",s(d,"unit_code"));
            case "AMBITO" -> {key.put("scope_code",s(d,"scope_code"));key.put("scope_id",n(d,"scope_id"));key.put("scope_name",s(d,"scope_name"));key.put("regional",b(d,"regional"));key.put("region",region(d));}
            case "REGION" -> {key.put("level",b(d,"regional")?"REGIONAL":"CENTRAL");key.put("region",region(d));}
            case "MATERIA" -> key.put("matter",d.get("matter")==null?null:s(d,"matter"));
            case "ORIGEN" -> key.put("origin",s(d,"origin"));
            case "SALIDA" -> key.put("exit",sp.exit());
            case "PERSONA" -> {key.put("person_name",sp.person()==null?null:names.get(sp.person()));key.put("person_id",sp.person());}
            default -> {}
        }
        return key;
    }

    // --- Estadisticas ----------------------------------------------------------------------------
    /** Percentil inclusivo (PERCENTIL.INCLUSIVE de Excel): rango p*(n-1) con interpolacion lineal; lista ordenada. */
    static double percentile(List<Double> sorted,double p){
        if(sorted.isEmpty())return 0;
        double rank=p*(sorted.size()-1);int lo=(int)Math.floor(rank),hi=(int)Math.ceil(rank);
        return sorted.get(lo)+(rank-lo)*(sorted.get(hi)-sorted.get(lo));
    }
    static Map<String,Object> summary(List<Double> values){
        var sorted=values.stream().sorted().toList();var out=new LinkedHashMap<String,Object>();
        if(sorted.isEmpty()){out.put("median",null);out.put("p90",null);out.put("max",null);return out;}
        out.put("median",round(percentile(sorted,0.5)));out.put("p90",round(percentile(sorted,0.9)));out.put("max",round(sorted.getLast()));
        return out;
    }
    static double round(double v){return Math.round(v*100)/100d;}
    private static Map<String,Object> stats(List<Span> spans,boolean waits){
        var out=new LinkedHashMap<String,Object>();
        out.put("n",spans.size());out.put("reconstructed",spans.stream().filter(Span::rebuilt).count());
        out.put("hours",summary(spans.stream().map(Span::hours).toList()));out.put("business_days",summary(spans.stream().map(Span::days).toList()));
        if(waits){
            var wait=new LinkedHashMap<String,Object>();
            wait.put("n",spans.stream().filter(Span::waited).count());
            wait.put("hours",summary(spans.stream().map(Span::waitHours).toList()));wait.put("business_days",summary(spans.stream().map(Span::waitDays).toList()));
            out.put("wait",wait);
        }
        return out;
    }

    // --- Volumen ---------------------------------------------------------------------------------
    private Map<String,Object> volume(Reach reach,Filters f){
        var out=new LinkedHashMap<String,Object>();out.put("kind","VOLUMEN");
        // COM por canal e ingreso (manual, atencion directa o Formulario), por su ambito de origen.
        var w=new Where().and("c.created_ms>=? AND c.created_ms<?",f.from(),f.to());apply(w,reach,f,matter(Family.COM));
        var coms=db.rows("SELECT channel,intake,COUNT(*) AS n FROM (SELECT c.channel,"+INTAKE+" AS intake FROM communication c LEFT JOIN requirement r ON r.communication_id=c.id"
            +" LEFT JOIN solicitation sol ON sol.communication_id=c.id LEFT JOIN attention_record ar ON ar.requirement_id=r.id JOIN org_scope s ON s.id=c.origin_scope WHERE "+w.sql+") x GROUP BY channel,intake",w.args.toArray());
        var com=new LinkedHashMap<String,Object>();com.put("total",sum(coms));
        com.put("by_channel",coms.stream().map(r->Map.<String,Object>of("channel",s(r,"channel"),"intake",s(r,"intake"),"count",n(r,"n"))).sorted(Comparator.comparing(m->m.get("channel")+"|"+m.get("intake"))).toList());
        com.put("by_intake",tally(coms,"intake"));
        out.put("com",com);
        // REQ por estado, unidad, complejidad y origen; con y sin Caso; regionales sin materia.
        w=new Where().and("r.created_ms>=? AND r.created_ms<?",f.from(),f.to());apply(w,reach,f,matter(Family.REQ));
        var reqs=db.rows("SELECT state,unit,complexity,origin,has_case,no_matter,COUNT(*) AS n FROM (SELECT r.state,s.unit_code AS unit,r.complexity,"+ORIGIN+" AS origin,"
            +"CASE WHEN EXISTS(SELECT 1 FROM case_record x WHERE x.requirement_id=r.id) THEN 1 ELSE 0 END AS has_case,CASE WHEN s.regional=TRUE AND r.matter IS NULL THEN 1 ELSE 0 END AS no_matter"
            +" FROM requirement r JOIN communication c ON c.id=r.communication_id LEFT JOIN solicitation sol ON sol.communication_id=r.communication_id"
            +" LEFT JOIN attention_record ar ON ar.requirement_id=r.id JOIN org_scope s ON s.id=r.principal_scope WHERE "+w.sql+") x GROUP BY state,unit,complexity,origin,has_case,no_matter",w.args.toArray());
        var req=new LinkedHashMap<String,Object>();req.put("total",sum(reqs));
        req.put("by_state",tally(reqs,"state"));req.put("by_unit",tally(reqs,"unit"));req.put("by_complexity",tally(reqs,"complexity"));req.put("by_origin",tally(reqs,"origin"));
        long with=reqs.stream().filter(r->n(r,"has_case")==1).mapToLong(r->n(r,"n")).sum();
        req.put("with_case",with);req.put("without_case",sum(reqs)-with);
        req.put("regional_without_matter",reqs.stream().filter(r->n(r,"no_matter")==1).mapToLong(r->n(r,"n")).sum());
        out.put("req",req);
        // Casos por estado y materia, por el ambito del Caso.
        w=new Where().and("cs.created_ms>=? AND cs.created_ms<?",f.from(),f.to());apply(w,reach,f,matter(Family.CAS));
        var cases=db.rows("SELECT state,matter,COUNT(*) AS n FROM (SELECT cs.state,cs.matter FROM case_record cs JOIN requirement r ON r.id=cs.requirement_id JOIN communication c ON c.id=r.communication_id"
            +" LEFT JOIN solicitation sol ON sol.communication_id=r.communication_id LEFT JOIN attention_record ar ON ar.requirement_id=r.id JOIN org_scope s ON s.id=cs.scope_id WHERE "+w.sql+") x GROUP BY state,matter",w.args.toArray());
        var cas=new LinkedHashMap<String,Object>();cas.put("total",sum(cases));cas.put("by_state",tally(cases,"state"));cas.put("by_matter",tally(cases,"matter"));
        out.put("cases",cas);
        return out;
    }
    private static long sum(List<Map<String,Object>> rows){return rows.stream().mapToLong(r->n(r,"n")).sum();}
    /** Conteo por un valor (nulo = sin valor), ordenado por el valor. */
    private static List<Map<String,Object>> tally(List<Map<String,Object>> rows,String col){
        var counts=new TreeMap<String,Long>();
        for(var r:rows)counts.merge(r.get(col)==null?"":s(r,col),n(r,"n"),Long::sum);
        return counts.entrySet().stream().map(e->{var m=new LinkedHashMap<String,Object>();m.put("value",e.getKey().isEmpty()?null:e.getKey());m.put("count",e.getValue());return (Map<String,Object>)m;}).toList();
    }

    // --- Antiguedad de abiertos --------------------------------------------------------------------
    private Map<String,Object> age(Reach reach,Filters f,String group,BusinessCalendar cal){
        long now=System.currentTimeMillis();
        var dims=dimensions(group,Boolean.TRUE.equals(f.q().by_origin()),false);
        var out=new LinkedHashMap<String,Object>();out.put("kind","ANTIGUEDAD");out.put("as_of",now);out.put("dimensions",dims);
        long[] earliest={now};
        String cols="s.id AS scope_id,s.code AS scope_code,s.name AS scope_name,s.unit_code,s.regional,s.region_code,"+ORIGIN+" AS origin,";
        var w=new Where().and("r.state<>'CERRADO'");apply(w,reach,f,matter(Family.REQ));
        String reqFrom=" FROM requirement r JOIN communication c ON c.id=r.communication_id LEFT JOIN solicitation sol ON sol.communication_id=r.communication_id"
            +" LEFT JOIN attention_record ar ON ar.requirement_id=r.id JOIN org_scope s ON s.id=r.principal_scope WHERE ";
        var w2=new Where().and("cs.state<>'CERRADO'");apply(w2,reach,f,matter(Family.CAS));
        String caseFrom=" FROM case_record cs JOIN requirement r ON r.id=cs.requirement_id JOIN communication c ON c.id=r.communication_id LEFT JOIN solicitation sol ON sol.communication_id=r.communication_id"
            +" LEFT JOIN attention_record ar ON ar.requirement_id=r.id JOIN org_scope s ON s.id=cs.scope_id WHERE ";
        long total=db.count("SELECT COUNT(*)"+reqFrom+w.sql,w.args.toArray())+db.count("SELECT COUNT(*)"+caseFrom+w2.sql,w2.args.toArray());
        Problem.require(total<=limit,TOO_MANY.formatted(String.format(Locale.ROOT,"%,d",limit).replace(',','.')));
        out.put("req",brackets(db.rows("SELECT "+cols+"r.matter AS matter,r.created_ms"+reqFrom+w.sql,w.args.toArray()),dims,cal,now,earliest));
        out.put("cases",brackets(db.rows("SELECT "+cols+"cs.matter AS matter,cs.created_ms"+caseFrom+w2.sql,w2.args.toArray()),dims,cal,now,earliest));
        out.put("calendar",calendarInfo(cal,earliest[0],now));
        return out;
    }
    static String bracket(long days){return days<=5?"0-5":days<=10?"6-10":days<=20?"11-20":"21+";}
    private static Map<String,Object> brackets(List<Map<String,Object>> rows,List<String> dims,BusinessCalendar cal,long now,long[] earliest){
        var groups=new TreeMap<String,Map<String,Object>>();var totals=new LinkedHashMap<String,Long>();BRACKETS.forEach(x->totals.put(x,0L));
        for(var r:rows){
            long created=Math.min(n(r,"created_ms"),now);earliest[0]=Math.min(earliest[0],created);
            String b=bracket(cal.businessDays(created,now,region(r)));totals.merge(b,1L,Long::sum);
            var key=key(dims,new Span(r,null,null,0,0,false,0,0,false),Map.of());
            var g=groups.computeIfAbsent(key.values().toString(),k->{var m=new LinkedHashMap<String,Object>();m.put("group",key);m.put("n",0L);var bs=new LinkedHashMap<String,Long>();BRACKETS.forEach(x->bs.put(x,0L));m.put("brackets",bs);return m;});
            g.put("n",(Long)g.get("n")+1);
            @SuppressWarnings("unchecked") var bs=(Map<String,Long>)g.get("brackets");bs.merge(b,1L,Long::sum);
        }
        var out=new LinkedHashMap<String,Object>();out.put("n",(long)rows.size());out.put("brackets",totals);out.put("rows",List.copyOf(groups.values()));
        return out;
    }
}
