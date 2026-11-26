package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.sigera.Access.Actor;
import cl.nexbyte.sigera.StatSource.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.Problem.require;

/**
 * Cortes estadisticos (0.8.0, D39 y D43, gate E10; DISENO 6 y docs/CONTRATO_STATS_0_8_0.md).
 *
 * STAT_CUT_CREATE (GESTION_ESTADISTICA que rige en el alcance pedido) congela, dentro del comando y con el lock global,
 * la fuente del motor en su instante: una fila por COM del alcance con alguna fecha de analisis en el periodo
 * (stat_cut_row) y las relaciones de entrada del motor (stat_cut_event, stat_cut_action, stat_cut_action_institution,
 * stat_cut_person y stat_cut_case), con el cupo y el plazo de StatGuard. Las huellas SHA-256 se calculan sobre una
 * serializacion canonica (orden fijo de tablas, filas por su clave, UTF-8 y LF); la de los metadatos incluye la de las
 * filas. Un corte no se modifica ni se borra: STAT_CUT_ANNUL solo marca la cabecera como anulada.
 *
 * Lo ven, consultan y verifican las cuentas con REPORTES cuyo alcance cubre el del corte (el que da en vivo la concesion
 * en su scope_id, incluida su materia). TI nunca (D2). Lo congelado tiene una lista cerrada de columnas sin datos
 * personales ni texto libre; las constancias reservadas nunca entran.
 */
@Service
public class StatCuts {
    static final String CREATED="CORTE_GENERADO",ANNULLED="CORTE_ANULADO",VERIFIED="CORTE_VERIFICADO";
    static final String ACTIVE="VIGENTE",VOID="ANULADO";
    static final String NOTE="Generacion de corte";
    /**
     * Inserciones de varias filas por sentencia: tantas como caben en 60.000 parametros (MySQL admite 65.535 por sentencia
     * preparada), hasta 2.000. Solo cambia la cantidad de sentencias, no lo que se guarda ni su huella.
     */
    static final int MAX_PARAMETERS=60_000,MAX_ROWS=2_000;
    static int rowsPerStatement(Table t){return Math.max(1,Math.min(MAX_ROWS,MAX_PARAMETERS/(t.columns().size()+1)));}
    private static final JsonMapper JSON=JsonMapper.builder().build();

    /** Tabla congelada: lista cerrada de columnas (sin cut_id), en el orden de la serializacion, y su clave. */
    record Table(String name,List<String> columns,List<String> key){}
    static final List<Table> TABLES=tables();
    private static List<Table> tables(){
        var row=new ArrayList<>(List.of("communication_id","req_id","received_ms","created_ms","req_ms","com_state"));
        for(String v:StatSource.KEYED)row.add("v_"+v.toLowerCase(Locale.ROOT));
        for(String v:StatSource.PROPOSED)row.add("p_"+v.toLowerCase(Locale.ROOT));
        for(var d:StatSource.FROZEN)row.add(StatSource.frozenColumn(d));
        return List.of(
            new Table("stat_cut_row",List.copyOf(row),List.of("communication_id")),
            new Table("stat_cut_event",List.of("event_id","communication_id","variable","code","catalog_version_id"),List.of("event_id")),
            new Table("stat_cut_action",List.of("unit_id","communication_id","action_type","origin","note_id","catalog_version_id"),List.of("unit_id")),
            new Table("stat_cut_action_institution",List.of("institution_id","note_id","type_code","catalog_version_id"),List.of("institution_id")),
            new Table("stat_cut_person",List.of("communication_id","ordinal","region_code","commune","age_years","nationality","age_bracket"),List.of("communication_id","ordinal")),
            new Table("stat_cut_case",List.of("case_id","communication_id","created_ms"),List.of("case_id")));
    }
    static Table table(String name){return TABLES.stream().filter(t->t.name().equals(name)).findFirst().orElseThrow();}

    /** Orden canónico independiente para las tablas de microdatos, incorporadas en V24. */
    private static final Table EXPORT_FOLIO=new Table("stat_export_cut_folio",List.of("communication_id","folio"),List.of("communication_id"));
    private static final Table EXPORT_ORIGIN=new Table("stat_export_cut_origin",List.of("source_kind","value_id","record_type","record_id"),List.of("source_kind","value_id"));
    static final List<Table> EXPORT_TABLES=List.of(EXPORT_FOLIO,EXPORT_ORIGIN);

    /** Corte resuelto para leerlo: periodo, instante, alcance en vivo de su concesion y rotulos congelados. */
    record Cut(long id,String code,Long scope,Reports.Reach reach,LocalDate from,LocalDate to,long asOf,String state,
               Map<String,String> scopes,Map<String,String> units,Map<String,Long> versions){
        /** Version VIGENTE de un catalogo en el instante del corte (nula si no habia). */
        Long version(String catalog){return versions.get(catalog);}
    }

    private final Db db;
    private final Reports reports;
    private final StatGuard guard;
    private final JdbcTemplate jdbc;
    /** Solo pruebas: corre dentro del plazo, con las filas ya copiadas (un plazo vencido no deja escrituras parciales). */
    private final Runnable probe;

    @Autowired
    public StatCuts(Db db,Reports reports,StatGuard guard){this(db,reports,guard,new JdbcTemplate(db.dataSource()),()->{});}
    /** Pruebas: otra plantilla JDBC (EXPLAIN en MySQL) y una sonda dentro del plazo del congelado. */
    StatCuts(Db db,Reports reports,StatGuard guard,JdbcTemplate jdbc,Runnable probe){
        this.db=db;
        this.reports=reports;
        this.guard=guard;
        this.jdbc=jdbc;
        this.probe=probe;
    }

    // --- Comandos (Workflow.run, con el lock global) --------------------------------------------------------------------
    Workflow.Result run(Actor a,String type,long id,Long expected,Input in){
        return switch(type){
            case "STAT_CUT_CREATE" -> create(a,in);
            case "STAT_CUT_ANNUL" -> annul(a,id,expected,in);
            default -> throw new Problem(400,"Comando desconocido.");
        };
    }

    private Workflow.Result create(Actor a,Input in){
        var ruling=Roles.ruling(a,Roles.GESTION_ESTADISTICA);
        if(ruling.isEmpty()||a.role(Roles.TI))Problem.denied();
        for(String k:in.values().keySet())if(!Set.of("title","scope_id","period","reason").contains(k))throw new Problem(400,"Corte: hay campos no reconocidos.");
        String title=in.text("title",200,true);
        Long scope=in.optionalId("scope_id");
        String reason=in.reason();
        var period=StatEngine.cutPeriod(in.values().get("period"));
        // GESTION_ESTADISTICA genera cortes en el alcance de su concesion (con REPORTES en el mismo alcance).
        if(ruling.stream().noneMatch(g->Objects.equals(g.scope(),scope)))Problem.denied();
        if(scope!=null)db.one("SELECT id FROM org_scope WHERE id=?",scope);
        guard.requireEnabled();
        return guard.run(a.id(),()->guard.read(a.id(),()->NOTE,()->freeze(a,title,scope,period,reason)));
    }

    private Workflow.Result freeze(Actor a,String title,Long scope,StatEngine.Period period,String reason){
        long id=db.count("SELECT COALESCE(MAX(id),0)+1 FROM stat_cut");
        long asOf=Lifecycle.now();
        var reach=reach(scope);
        var q=new Query(StatSource.MEASURES.get("INGRESOS"),List.of(),List.of(),Map.of(),period.span(),Basis.RECEPCION,Mode.VIGENTE,null,null,false,reach,Map.of(),null);
        var canonical=new Canonical();
        var counts=new LinkedHashMap<String,Object>();
        counts.put("stat_cut_row",copy(id,table("stat_cut_row"),Plan.freezer(q).freezeRows(),canonical));
        counts.put("stat_cut_event",copy(id,table("stat_cut_event"),Plan.freezer(q).freezeEvents(),canonical));
        counts.put("stat_cut_action",copy(id,table("stat_cut_action"),Plan.freezer(q).freezeActions(),canonical));
        counts.put("stat_cut_action_institution",copy(id,table("stat_cut_action_institution"),Plan.freezer(q).freezeInstitutions(),canonical));
        counts.put("stat_cut_person",copyPersons(id,Plan.freezer(q).freezePersons(),canonical));
        counts.put("stat_cut_case",copy(id,table("stat_cut_case"),Plan.freezer(q).freezeCases(),canonical));
        String rows=canonical.hex();
        freezeExport(id,counts);
        probe.run();

        var header=new LinkedHashMap<String,Object>();
        header.put("code",code(asOf));header.put("title",title);header.put("scope_id",scope);
        header.put("period_type",period.type());header.put("period_from",period.span().fromDay().toString());header.put("period_to",period.span().toDay().toString());
        header.put("as_of_ms",asOf);header.put("methodology_version",StatEngine.METHODOLOGY);
        header.put("catalog_versions",json(catalogVersions()));header.put("calendar_sha256",calendar());
        header.put("dimensions_json",json(new ArrayList<>(StatSource.DIMS.keySet())));header.put("labels_json",json(labels()));
        header.put("counts_json",json(counts));header.put("row_count",counts.get("stat_cut_row"));header.put("rows_sha256",rows);
        header.put("created_by",a.id());header.put("created_ms",asOf);header.put("basis",reason);
        header.put("metadata_sha256",metadata(header));
        header.put("state",ACTIVE);header.put("revision",0L);
        var names=new ArrayList<>(header.keySet());names.addFirst("id");
        var values=new ArrayList<>(header.values());values.addFirst(id);
        db.update("INSERT INTO stat_cut("+String.join(",",names)+") VALUES("+String.join(",",Collections.nCopies(names.size(),"?"))+")",values.toArray());
        db.audit(a.id(),CREATED,"SISTEMA",0,null,"Corte "+header.get("code")+"; alcance "+scopeName(scope)+"; periodo "+period.type());
        return Workflow.done("CORTE",id);
    }

    private void freezeExport(long id,Map<String,Object> counts){
        var canonical=new Canonical();
        var folios=new Sql("SELECT r.communication_id,c.folio FROM stat_cut_row r JOIN communication c ON c.id=r.communication_id WHERE r.cut_id=? ORDER BY r.communication_id",new Object[]{id});
        // La población y la reserva ya se decidieron al congelar E10. Aquí solo se toma la procedencia de esas filas.
        var origins=new Sql("SELECT 'EVENTO' AS source_kind,e.event_id AS value_id,v.record_type,v.record_id FROM stat_cut_event e JOIN stat_event v ON v.id=e.event_id WHERE e.cut_id=?"
            +" UNION ALL SELECT 'ACTO',a.unit_id,CASE WHEN m.case_id IS NULL THEN 'REQ' ELSE 'CAS' END,COALESCE(m.case_id,r.req_id)"
            +" FROM stat_cut_action a JOIN stat_cut_row r ON r.cut_id=a.cut_id AND r.communication_id=a.communication_id LEFT JOIN management_note m ON m.id=a.note_id"
            +" WHERE a.cut_id=? ORDER BY source_kind,value_id",new Object[]{id,id});
        counts.put(EXPORT_FOLIO.name(),copy(id,EXPORT_FOLIO,folios,canonical));
        counts.put(EXPORT_ORIGIN.name(),copy(id,EXPORT_ORIGIN,origins,canonical));
        db.update("INSERT INTO stat_export_cut_integrity(cut_id,export_sha256) VALUES(?,?)",id,canonical.hex());
    }

    private Workflow.Result annul(Actor a,long id,Long expected,Input in){
        var ruling=Roles.ruling(a,Roles.GESTION_ESTADISTICA);
        if(ruling.isEmpty()||a.role(Roles.TI))Problem.denied();
        String reason=in.reason();
        var row=db.one("SELECT id,code,scope_id,state,revision FROM stat_cut WHERE id=?",id);
        revision(row,expected);
        require(s(row,"state").equals(ACTIVE),"El corte ya está anulado.");
        var reach=reach(nullableId(row,"scope_id"));
        if(ruling.stream().noneMatch(g->covers(reports.reach(List.of(g)),reach)))Problem.denied();
        // Unica escritura sobre un corte existente: la cabecera pasa a anulada; filas y huellas se conservan.
        int changed=db.update("UPDATE stat_cut SET state=?,annulled_by=?,annulled_ms=?,annul_basis=?,revision=revision+1 WHERE id=? AND revision=? AND state=?",
            VOID,a.id(),Lifecycle.now(),reason,id,expected,ACTIVE);
        require(changed==1,"El registro cambio. Actualiza la ficha antes de confirmar.");
        db.audit(a.id(),ANNULLED,"SISTEMA",0,null,"Corte "+s(row,"code"));
        return Workflow.done("CORTE",id);
    }

    // --- Alcance --------------------------------------------------------------------------------------------------------
    /** Alcance del corte: el que da en vivo una concesion de REPORTES en su scope_id (nulo = institucion), incluida su materia. */
    Reports.Reach reach(Long scope){return reports.reach(List.of(new Access.Grant(Roles.REPORTES,scope)));}
    /** El alcance de la cuenta incluye el del corte en ambitos y en materias, o la cuenta es institucional. */
    static boolean covers(Reports.Reach account,Reports.Reach cut){
        return account.all()||!cut.all()&&account.scopes().containsAll(cut.scopes())&&account.matters().containsAll(cut.matters());
    }
    private Reports.Reach account(Actor a){return reports.reach(a.grants().stream().filter(g->g.role().equals(Roles.REPORTES)).toList());}

    /** Corte que la cuenta puede leer: 404 si no existe, 403 si su alcance no lo cubre. */
    Cut readable(Actor a,long id){
        if(!StatEngine.allowed(a))Problem.denied();
        var cut=find(id);
        if(cut==null)throw new Problem(404,"Registro no disponible.");
        if(!covers(account(a),cut.reach()))Problem.denied();
        return cut;
    }
    /** Corte por su id, o nulo si no existe. */
    Cut find(long id){
        var rows=db.rows("SELECT id,code,scope_id,period_from,period_to,as_of_ms,state,labels_json,catalog_versions FROM stat_cut WHERE id=?",id);
        if(rows.isEmpty())return null;
        var r=rows.getFirst();
        var labels=JSON.readTree(s(r,"labels_json"));
        var scopes=new LinkedHashMap<String,String>();labels.path("scopes").forEach(x->scopes.put(x.get(0).asString(),x.get(1).asString()));
        var units=new LinkedHashMap<String,String>();labels.path("units").forEach(x->units.put(x.get(0).asString(),x.get(1).asString()));
        var versions=new HashMap<String,Long>();
        JSON.readTree(s(r,"catalog_versions")).properties().forEach(e->{if(e.getValue().isNumber())versions.put(e.getKey(),e.getValue().asLong());});
        return new Cut(n(r,"id"),s(r,"code"),nullableId(r,"scope_id"),reach(nullableId(r,"scope_id")),LocalDate.parse(s(r,"period_from")),LocalDate.parse(s(r,"period_to")),
            n(r,"as_of_ms"),s(r,"state"),Collections.unmodifiableMap(scopes),Collections.unmodifiableMap(units),Collections.unmodifiableMap(versions));
    }

    // --- Lecturas (GET /api/stats/cuts) ---------------------------------------------------------------------------------
    public List<Map<String,Object>> list(Actor a){
        if(!StatEngine.allowed(a))Problem.denied();
        var mine=account(a);
        var out=new ArrayList<Map<String,Object>>();
        for(var r:db.rows("SELECT c.*,s.name AS creator FROM stat_cut c JOIN staff s ON s.id=c.created_by ORDER BY c.id DESC"))
            if(covers(mine,reach(nullableId(r,"scope_id"))))out.add(summary(a,r));
        return out;
    }
    public Map<String,Object> detail(Actor a,long id){
        readable(a,id);
        var r=db.one("SELECT c.*,s.name AS creator,x.name AS annuller FROM stat_cut c JOIN staff s ON s.id=c.created_by LEFT JOIN staff x ON x.id=c.annulled_by WHERE c.id=?",id);
        var out=summary(a,r);
        out.put("basis",s(r,"basis"));
        out.put("catalog_versions",JSON.readValue(s(r,"catalog_versions"),Map.class));
        out.put("counts",JSON.readValue(s(r,"counts_json"),Map.class));
        out.put("calendar_sha256",s(r,"calendar_sha256"));out.put("metadata_sha256",s(r,"metadata_sha256"));out.put("methodology_version",s(r,"methodology_version"));
        out.put("export_sha256",exportHash(id));
        out.put("annulled_by",r.get("annuller"));out.put("annulled_at",r.get("annulled_ms")==null?null:iso(n(r,"annulled_ms")));out.put("annul_basis",r.get("annul_basis"));
        return out;
    }
    private Map<String,Object> summary(Actor a,Map<String,Object> r){
        var m=new LinkedHashMap<String,Object>();
        Long scope=nullableId(r,"scope_id");
        m.put("id",n(r,"id"));m.put("code",s(r,"code"));m.put("title",s(r,"title"));
        m.put("scope",scope==null?null:s(db.one("SELECT code FROM org_scope WHERE id=?",scope),"code"));m.put("scope_label",scopeName(scope));
        m.put("period",Map.of("type",s(r,"period_type"),"from",s(r,"period_from"),"to",s(r,"period_to")));
        m.put("as_of",iso(n(r,"as_of_ms")));m.put("state",s(r,"state"));m.put("row_count",n(r,"row_count"));m.put("rows_sha256",s(r,"rows_sha256"));
        m.put("created_by",s(r,"creator"));m.put("revision",n(r,"revision"));
        var reach=reach(scope);
        m.put("can_annul",s(r,"state").equals(ACTIVE)&&Roles.ruling(a,Roles.GESTION_ESTADISTICA).stream().anyMatch(g->covers(reports.reach(List.of(g)),reach)));
        return m;
    }
    private String scopeName(Long scope){return scope==null?"institucional":s(db.one("SELECT code FROM org_scope WHERE id=?",scope),"code");}
    private static String iso(long ms){return Instant.ofEpochMilli(ms).atZone(BusinessCalendar.ZONE).toOffsetDateTime().toString();}

    // --- Verificacion (POST /api/stats/cuts/{id}/verify) ------------------------------------------------------------------
    String exportHash(long id){
        var rows=db.rows("SELECT export_sha256 FROM stat_export_cut_integrity WHERE cut_id=?",id);
        return rows.isEmpty()?null:s(rows.getFirst(),"export_sha256");
    }
    private Boolean exportIntact(long id,Map<String,Object> header,String hash){
        boolean expectsExport=JSON.readTree(s(header,"counts_json")).has(EXPORT_FOLIO.name());
        if(!expectsExport&&hash==null)return null;
        if(!expectsExport||hash==null)return false;
        var canonical=new Canonical();
        for(var t:EXPORT_TABLES)stored(id,t,canonical::row,canonical);
        return canonical.hex().equals(hash);
    }
    /** Recalcula las dos huellas desde lo guardado; no escribe nada salvo la auditoria CORTE_VERIFICADO. */
    public Map<String,Object> verify(Actor a,long id){
        if(!StatEngine.allowed(a))Problem.denied();
        guard.requireEnabled();
        return guard.run(a.id(),()->guard.read(a.id(),()->"Verificacion de corte",()->{
            var cut=readable(a,id);
            var header=db.one("SELECT * FROM stat_cut WHERE id=?",id);
            var canonical=new Canonical();
            for(var t:TABLES)stored(id,t,canonical::row,canonical);
            String rows=canonical.hex();
            var fields=new LinkedHashMap<String,Object>();
            for(String k:METADATA)fields.put(k,header.get(k));
            boolean rowsOk=rows.equals(s(header,"rows_sha256")),metadataOk=metadata(fields).equals(s(header,"metadata_sha256"));
            String exportHash=exportHash(id);
            Boolean exportOk=exportIntact(id,header,exportHash);
            boolean ok=rowsOk&&metadataOk&&!Boolean.FALSE.equals(exportOk);
            db.audit(a.id(),VERIFIED,"SISTEMA",0,null,"Corte "+cut.code()+"; "+(ok?"integro":"alterado"));
            var out=new LinkedHashMap<String,Object>();
            out.put("id",id);out.put("code",cut.code());out.put("ok",ok);out.put("rows_ok",rowsOk);out.put("metadata_ok",metadataOk);
            out.put("rows_sha256",s(header,"rows_sha256"));out.put("metadata_sha256",s(header,"metadata_sha256"));
            out.put("export_sha256",exportHash);out.put("export_ok",exportOk);
            return out;
        }));
    }

    // --- Copia y serializacion canonica -------------------------------------------------------------------------------------
    /** Lee una sentencia del congelado, inserta sus filas en lotes y las serializa en el mismo orden. */
    private long copy(long id,Table t,Sql sql,Canonical canonical){
        canonical.section(t);
        var batch=new Batch(id,t);
        jdbc.query(sql.text(),rs->{
            var values=new Object[t.columns().size()];
            for(int i=0;i<values.length;i++)values[i]=normal(rs.getObject(t.columns().get(i)));
            canonical.row(values);batch.add(values);
        },sql.args());
        batch.flush();
        return batch.total;
    }
    /** NNA afectados: el ordinal sale del orden canonico de sus datos dentro de cada COM (codigo Unicode, nulos primero). */
    private long copyPersons(long id,Sql sql,Canonical canonical){
        var t=table("stat_cut_person");
        canonical.section(t);
        var batch=new Batch(id,t);
        var pending=new ArrayList<Object[]>();
        Consumer<List<Object[]>> emit=list->{
            list.sort(PERSON_ORDER);
            for(int i=0;i<list.size();i++){
                var p=list.get(i);
                var values=new Object[]{p[0],(long)i+1,p[1],p[2],p[3],p[4],p[5]};
                canonical.row(values);batch.add(values);
            }
            list.clear();
        };
        jdbc.query(sql.text(),rs->{
            var p=new Object[]{normal(rs.getObject("communication_id")),normal(rs.getObject("region_code")),normal(rs.getObject("commune")),normal(rs.getObject("age_years")),
                normal(rs.getObject("nationality")),normal(rs.getObject("age_bracket"))};
            if(!pending.isEmpty()&&!pending.getFirst()[0].equals(p[0]))emit.accept(pending);
            pending.add(p);
        },sql.args());
        emit.accept(pending);
        batch.flush();
        return batch.total;
    }
    private static final Comparator<Object[]> PERSON_ORDER=(x,y)->{
        for(int i=1;i<x.length;i++){int c=compare(x[i],y[i]);if(c!=0)return c;}
        return 0;
    };
    private static int compare(Object x,Object y){
        if(x==null||y==null)return x==null?(y==null?0:-1):1;
        if(x instanceof Long a&&y instanceof Long b)return Long.compare(a,b);
        return x.toString().compareTo(y.toString());
    }
    /** Filas guardadas de una tabla del corte, en el orden de su clave. */
    private void stored(long id,Table t,Consumer<Object[]> each,Canonical canonical){
        canonical.section(t);
        jdbc.query("SELECT "+String.join(",",t.columns())+" FROM "+t.name()+" WHERE cut_id=? ORDER BY "+String.join(",",t.key()),rs->{
            var values=new Object[t.columns().size()];
            for(int i=0;i<values.length;i++)values[i]=normal(rs.getObject(i+1));
            each.accept(values);
        },id);
    }
    /** Inserciones de varias filas por sentencia. */
    private final class Batch {
        final long id;final Table t;final List<Object[]> rows=new ArrayList<>();long total;
        Batch(long id,Table t){this.id=id;this.t=t;}
        void add(Object[] values){rows.add(values);total++;if(rows.size()>=rowsPerStatement(t))flush();}
        void flush(){
            if(rows.isEmpty())return;
            String marks="(?,"+String.join(",",Collections.nCopies(t.columns().size(),"?"))+")";
            var args=new ArrayList<Object>();
            for(var r:rows){args.add(id);args.addAll(Arrays.asList(r));}
            jdbc.update("INSERT INTO "+t.name()+"(cut_id,"+String.join(",",t.columns())+") VALUES "+String.join(",",Collections.nCopies(rows.size(),marks)),args.toArray());
            rows.clear();
        }
    }
    /** Valor normalizado: enteros como Long, texto como String y nulo; nada mas entra a un corte. */
    static Object normal(Object v){
        if(v==null)return null;
        if(v instanceof java.math.BigDecimal d)return d.longValueExact();
        if(v instanceof Number x)return x.longValue();
        if(v instanceof Boolean b)return b?1L:0L;
        return v.toString();
    }
    /**
     * Serializacion canonica (contrato, seccion 11): por tabla, una linea "#tabla" y otra con sus columnas separadas por
     * tabulador; luego cada fila en el orden de su clave, con los valores separados por tabulador: entero en decimal, nulo
     * como \N, texto con \ , tabulador, LF y CR escapados como \\, \t, \n y \r. Cada linea termina en LF; todo en UTF-8.
     */
    static final class Canonical {
        private final MessageDigest digest;
        Canonical(){try{digest=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IllegalStateException(e);}}
        void section(Table t){line("#"+t.name());line(String.join("\t",t.columns()));}
        void row(Object[] values){
            var out=new StringBuilder();
            for(int i=0;i<values.length;i++){if(i>0)out.append('\t');out.append(text(values[i]));}
            line(out.toString());
        }
        private void line(String s){digest.update((s+"\n").getBytes(StandardCharsets.UTF_8));}
        String hex(){return HexFormat.of().formatHex(digest.digest());}
        static String text(Object v){
            if(v==null)return "\\N";
            if(v instanceof Long l)return Long.toString(l);
            return v.toString().replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r");
        }
    }

    // --- Metadatos ------------------------------------------------------------------------------------------------------------
    /** Campos de la cabecera que cubre metadata_sha256 (incluye rows_sha256), en JSON canonico. */
    static final List<String> METADATA=List.of("code","title","scope_id","period_type","period_from","period_to","as_of_ms","methodology_version","catalog_versions",
        "calendar_sha256","dimensions_json","labels_json","counts_json","row_count","rows_sha256","created_by","created_ms","basis");
    static String metadata(Map<String,Object> header){
        var m=new TreeMap<String,Object>();
        for(String k:METADATA)m.put(k,normal(header.get(k)));
        return sha256(json(m));
    }
    private Map<String,Object> catalogVersions(){
        var m=new TreeMap<String,Object>();
        for(String c:StatCatalogs.CATALOGS.keySet())m.put(c,StatValues.currentVersion(db,c));
        return m;
    }
    /** Huella del calendario habil vigente: dia, region (NAC = nacional) y nombre, ordenados. */
    String calendar(){
        var lines=new ArrayList<String>();
        for(var r:db.rows("SELECT holiday_day,region_key,name FROM business_holiday"))lines.add(Canonical.text(s(r,"holiday_day"))+"\t"+Canonical.text(s(r,"region_key"))+"\t"+Canonical.text(s(r,"name"))+"\n");
        Collections.sort(lines);
        return sha256(String.join("",lines));
    }
    /** Rotulos de ambitos y unidades en el instante del corte (en su orden de codigo): el corte no los toma de la tabla vigente. */
    private Map<String,Object> labels(){
        var m=new TreeMap<String,Object>();
        m.put("scopes",db.rows("SELECT code,name FROM org_scope ORDER BY code").stream().map(r->List.of(s(r,"code"),s(r,"name"))).toList());
        m.put("units",db.rows("SELECT code,name FROM org_unit ORDER BY code").stream().map(r->List.of(s(r,"code"),s(r,"name"))).toList());
        return m;
    }
    private String code(long asOf){
        int year=Instant.ofEpochMilli(asOf).atZone(BusinessCalendar.ZONE).getYear();
        long n=db.count("SELECT COUNT(*) FROM stat_cut WHERE code LIKE ?","CORTE-"+year+"-%")+1;
        return "CORTE-"+year+"-"+String.format(Locale.ROOT,"%03d",n);
    }
    static String sha256(String text){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    /** JSON canonico: claves ordenadas, sin espacios, enteros en decimal y texto con los escapes de JSON. */
    static String json(Object v){
        var out=new StringBuilder();write(out,v);return out.toString();
    }
    private static void write(StringBuilder out,Object v){
        switch(v){
            case null -> out.append("null");
            case Map<?,?> m -> {
                var sorted=new TreeMap<String,Object>();m.forEach((k,x)->sorted.put(String.valueOf(k),x));
                out.append('{');boolean first=true;
                for(var e:sorted.entrySet()){if(!first)out.append(',');first=false;write(out,e.getKey());out.append(':');write(out,e.getValue());}
                out.append('}');
            }
            case Collection<?> c -> {out.append('[');boolean first=true;for(Object x:c){if(!first)out.append(',');first=false;write(out,x);}out.append(']');}
            case Boolean b -> out.append(b);
            case Number n -> out.append(normal(n));
            default -> {
                out.append('"');
                for(char ch:v.toString().toCharArray()){
                    switch(ch){
                        case '"' -> out.append("\\\"");
                        case '\\' -> out.append("\\\\");
                        default -> {if(ch<0x20)out.append(String.format(Locale.ROOT,"\\u%04x",(int)ch));else out.append(ch);}
                    }
                }
                out.append('"');
            }
        }
    }
}
