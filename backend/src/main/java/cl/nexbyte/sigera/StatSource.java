package cl.nexbyte.sigera;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fuente unica del motor estadistico (0.8.0, D25, D28, D31-D32, D34-D38, gate E08; DISENO 4.4, 4.7 y 5.1-5.3). Arma la
 * consulta SQL portable (H2 MODE=MySQL y MySQL 8.4) con una fila por COM y, aparte, una relacion (com, val, w) por cada
 * dimension multivaluada y una relacion de unidades (u) para las medidas que no cuentan COM. Ninguna tabla guarda lo calculado: no hay
 * deriva entre la fuente y lo calculado. El alcance, el periodo y el estado D25 se aplican a la base antes de expandir
 * valores.
 *
 * Etapas (CTE): pre (columnas directas de la cadena), base0 (dimensiones derivadas y estado D25 a la fecha), m_* y u
 * (relaciones), base (filtros) y src (claves pedidas). Reutiliza las expresiones de E05 a E07 (StatValues,
 * StatProposals, OriginRegion, TerritorialDims, PersonDims). Alias externos: c COM, q REQ, sol SOL, ar ficha, s ambito de
 * referencia; los internos de esas expresiones no chocan con ellos.
 *
 * Las uniones y filtros sobre columnas de un CTE van como expresion (b.id+0, CONCAT(x,'')): MySQL 8.4 crea claves
 * automaticas sobre un CTE referenciado varias veces y con ellas falla (error 1112, "uses an extension", o peor con
 * internal_tmp_mem_storage_engine=MEMORY); sin claves usa hash join. Lo prueba el barrido de StatEngineTest con EXPLAIN.
 *
 * Los identificadores de ambito y las materias del alcance los resuelve el servidor (Reports.reach) y van como literales;
 * los valores de los filtros van siempre como parametros.
 */
final class StatSource {
    private StatSource(){}

    enum Mode {
        VIGENTE(TerritorialDims.Mode.VIGENTE),AL_INGRESO(TerritorialDims.Mode.INGRESO),AL_REQ(TerritorialDims.Mode.REQ),FECHA(TerritorialDims.Mode.HISTORICO);
        final TerritorialDims.Mode dims;
        Mode(TerritorialDims.Mode dims){this.dims=dims;}
        /** Modos que exigen evidencia hasta un instante: sin ella el valor es PENDIENTE (D37). */
        boolean evidence(){return this==AL_REQ||this==FECHA;}
    }
    enum Basis {RECEPCION,REGISTRO,CONSTITUCION_REQ,CREACION_CASO}
    /** Unidad que cuenta la medida. */
    enum Unit {COM,ACTUACION,CLASIFICACION,CASO,VARIABLE}
    enum Population {COMPUTABLE,EXCLUIDA,ANTECEDENTE,REQUERIMIENTO}
    enum Kind {PERIODO,LISTA,CATALOGO,REGION,COMUNA,AMBITO,UNIDAD,EDAD,PAIS}

    /** Medida: unidad, poblacion y si n cuenta la unidad propia (frecuencia), las COM o las menciones. */
    record Measure(String code,String label,Unit unit,Population population,boolean own,boolean mentions){}
    static final Map<String,Measure> MEASURES=measures(
        new Measure("INGRESOS","Ingresos",Unit.COM,Population.COMPUTABLE,false,false),
        new Measure("EXCLUIDOS","Ingresos inválidos (excluidos)",Unit.COM,Population.EXCLUIDA,false,false),
        new Measure("ANTECEDENTES","Antecedentes vinculados",Unit.COM,Population.ANTECEDENTE,false,false),
        new Measure("REQ_CONSTITUIDOS","Requerimientos constituidos (REQ)",Unit.COM,Population.REQUERIMIENTO,false,false),
        new Measure("CASOS","Casos",Unit.CASO,Population.REQUERIMIENTO,true,false),
        new Measure("ACTUACIONES","Actuaciones",Unit.ACTUACION,Population.COMPUTABLE,true,false),
        new Measure("REQUERIMIENTOS_CON_ACTUACION","Requerimientos con actuaciones",Unit.ACTUACION,Population.COMPUTABLE,false,false),
        new Measure("CLASIFICACIONES_DERECHO","Clasificaciones de derechos",Unit.CLASIFICACION,Population.COMPUTABLE,true,false),
        new Measure("REQUERIMIENTOS_CON_DERECHO","Requerimientos con derechos clasificados",Unit.CLASIFICACION,Population.COMPUTABLE,false,false),
        new Measure("REQUERIMIENTOS_CON_VALOR","Requerimientos con el valor",Unit.COM,Population.COMPUTABLE,false,false),
        new Measure("MENCIONES","Menciones",Unit.COM,Population.COMPUTABLE,false,true),
        new Measure("COMPLETITUD","Completitud por variable",Unit.VARIABLE,Population.COMPUTABLE,true,false));
    private static Map<String,Measure> measures(Measure... list){
        var m=new LinkedHashMap<String,Measure>();for(var x:list)m.put(x.code(),x);return Collections.unmodifiableMap(m);
    }

    /** unit: la dimension pertenece a esa unidad (y solo en ella es de valor unico); only: admite solo las medidas de esa unidad. */
    record Dim(String code,String label,Kind kind,boolean multiple,String catalog,Unit unit,boolean only,Map<String,String> options){
        boolean numeric(){return kind==Kind.EDAD||kind==Kind.PERIODO;}
    }
    static final String PENDING="PENDIENTE",NO_INFO="SIN_INFORMACION",NOT_APPLY="NO_APLICA",NO_DETAIL="SIN_DESGLOSE",NATIONAL_OFFICE="OFICINA_NACIONAL";
    /** Sentinelas de las dimensiones numericas (edad): mismo significado que los codigos especiales. */
    static final Map<Long,String> NUMERIC_SPECIAL=Map.of(-1L,NO_INFO,-2L,NOT_APPLY,-3L,PENDING);
    private static final String SPECIAL_SQL="'PENDIENTE','SIN_INFORMACION','NO_DECLARADO','NO_APLICA'";
    static final Map<String,String> SPECIAL=special();
    private static Map<String,String> special(){
        var m=new LinkedHashMap<>(StatCatalogs.SPECIAL);m.put(NO_DETAIL,"Sin subcategoría");return Collections.unmodifiableMap(m);
    }
    /** Variables de completitud (las de StatCompleteness.indicators). */
    static final List<String> VARIABLES=List.of("TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_INGRESO","REGION_REQUIRENTE","DERECHO","CONTEXTO");
    private static final Map<String,String> STATES=options("INFORMADO","Informado","PENDIENTE","Pendiente","SIN_INFORMACION","Sin información","NO_DECLARADO","No declarado","NO_APLICA","No aplica");
    private static final Map<String,String> NATIONALITY=options("CHILE","Chile","EXTRANJERA","Extranjera");
    private static final String[] CHANNELS={"PRESENCIAL","Presencial","TELEFONO","Teléfono","CORREO","Correo","OFICIO","Oficio","REDES_SOCIALES","Redes sociales","INTEGRANTE_DEFENSORIA","Integrante de la Defensoría","OTRO","Otro",StatProposals.ADULT,"Formulario Público","FORMULARIO_WEB_NNA","Formulario Público NNA"};
    private static final String[] RESULTS={"ORIENTACION","Orientación","RESPUESTA_DIRECTA","Respuesta directa","REMISION_CONCLUIDA","Remisión concluida"};
    static final Map<String,Dim> DIMS=dims();
    private static Map<String,Dim> dims(){
        var m=new LinkedHashMap<String,Dim>();
        period(m,"DIA","Día");period(m,"MES","Mes");period(m,"TRIMESTRE","Trimestre");period(m,"SEMESTRE","Semestre");period(m,"ANIO","Año");period(m,"CUENTA_PUBLICA","Cuenta Pública");
        list(m,"TARDANZA","Registro tardío","SI","Registrado después del periodo","NO","Registrado dentro del periodo");
        list(m,"ESTADO_COM","Estado de la comunicación","NUEVA","Nueva","ENVIADA","Distribuida","ATENDIDA","Atendida","VINCULADA","Vinculada como antecedente","INVALIDA","Inválida","DUPLICADA","Duplicada");
        list(m,"ESTADO_SOL","Estado de la solicitud a jefatura","PENDIENTE","Pendiente de asumir","DEVUELTA","Devuelta","ASUMIDA","Asumida","DESCARTADA","Descartada");
        list(m,"ESTADO_REQ","Estado del requerimiento","ABIERTO","Abierto","CERRADO","Cerrado");
        list(m,"CANAL","Canal original",CHANNELS);
        list(m,"MECANISMO","Mecanismo de ingreso","FORMULARIO","Formulario Público","ATENCION_DIRECTA","Atención directa","MANUAL","Registro manual");
        catalog(m,"VIA_INGRESO","Vía de ingreso","VIA_INGRESO",false,null);
        catalog(m,"VIA_AGRUPACION","Vía de ingreso agrupada","VIA_INGRESO",false,null);
        catalog(m,"TIPO_SOLICITUD","Tipo de solicitud","TIPO_SOLICITUD",false,null);
        catalog(m,"TIPO_REQUIRENTE","Tipo de requirente","TIPO_REQUIRENTE",false,null);
        list(m,"MODALIDAD","Modalidad de atención","GENERAL","General","NNA","NNA");
        simple(m,"REGION_REQUIRENTE","Región del requirente",Kind.REGION,false);
        simple(m,"COMUNA_REQUIRENTE","Comuna del requirente",Kind.COMUNA,false);
        simple(m,"REGION_ORIGEN","Región de origen registrada",Kind.REGION,false);
        simple(m,"REGION_NNA","Región del NNA afectado",Kind.REGION,true);
        simple(m,"COMUNA_NNA","Comuna del NNA afectado",Kind.COMUNA,true);
        simple(m,"REGION_HECHOS","Región de los hechos",Kind.REGION,false);
        simple(m,"COMUNA_HECHOS","Comuna de los hechos",Kind.COMUNA,false);
        simple(m,"AMBITO","Ámbito de referencia",Kind.AMBITO,false);
        simple(m,"SEDE_INICIAL","Ámbito responsable inicial",Kind.AMBITO,false);
        // DI-13 (E10): la sede regional u Oficina Nacional a la que se adscribio primero el ingreso; todo el nivel central es una sola categoria.
        m.put("ADSCRIPCION_INICIAL",new Dim("ADSCRIPCION_INICIAL","Adscripción institucional",Kind.AMBITO,false,null,null,false,
            options(NATIONAL_OFFICE,"Oficina Nacional",PENDING,"Pendiente de asignación")));
        simple(m,"SEDE_VIGENTE","Ámbito responsable",Kind.AMBITO,false);
        m.put("TERRITORIO_COBERTURA",new Dim("TERRITORIO_COBERTURA","Quién atendía la región de origen al recibir",Kind.AMBITO,false,null,null,false,
            options("NIVEL_CENTRAL","Nivel central","SIN_ATENCION","Sin atención territorial")));
        m.put("REGION_SEDE",new Dim("REGION_SEDE","Región de la sede",Kind.REGION,false,null,null,false,options("NIVEL_CENTRAL","Nivel central")));
        simple(m,"UNIDAD","Unidad",Kind.UNIDAD,false);
        list(m,"NIVEL","Nivel","CENTRAL","Nivel central","REGIONAL","Sede regional");
        list(m,"MATERIA","Materia","UPR","UPR","UPD","UPD","UEP","UEP","OTRA","Otra");
        list(m,"COMPLEJIDAD","Complejidad","BAJA","Baja","MEDIANA","Mediana","ALTA","Alta");
        list(m,"ORIGEN_REQ","Origen del requerimiento","SOL","Solicitud a jefatura","ATENCION_DIRECTA","Atención directa");
        list(m,"RESULTADO_PRIMERA_ATENCION","Resultado de la primera atención",RESULTS);
        catalog(m,"DERECHO_EJE","Eje de derechos","DERECHO",true,Unit.CLASIFICACION);
        catalog(m,"DERECHO","Derecho","DERECHO",true,Unit.CLASIFICACION);
        catalog(m,"DERECHO_SUBCATEGORIA","Subcategoría del derecho","DERECHO",true,Unit.CLASIFICACION);
        catalog(m,"CONTEXTO","Contexto","CONTEXTO",true,null);
        catalog(m,"CARACTERIZACION","Caracterización de la vulneración","CARACTERIZACION",true,null);
        catalog(m,"ROL_ESTADO","Rol del Estado","ROL_ESTADO",true,null);
        catalog(m,"GRUPO_PRIORITARIO","Grupos prioritarios","GRUPO_PRIORITARIO",true,null);
        catalog(m,"TIPO_ACTUACION","Tipo de actuación","TIPO_ACTUACION",true,Unit.ACTUACION);
        m.put("ORIGEN_ACTUACION",new Dim("ORIGEN_ACTUACION","Origen de la actuación",Kind.LISTA,true,null,Unit.ACTUACION,false,
            options("PRIMERA_ATENCION","Primera atención","GESTION_REQ","Gestión del requerimiento","GESTION_CASO","Gestión del Caso")));
        catalog(m,"TIPO_INSTITUCION","Tipo de institución de las actuaciones","TIPO_INSTITUCION",true,Unit.ACTUACION);
        simple(m,"EDAD_NNA","Edad del NNA afectado",Kind.EDAD,true);
        catalog(m,"TRAMO_EDAD_NNA","Tramo de edad del NNA afectado","TRAMO_EDAD",true,null);
        simple(m,"NACIONALIDAD_NNA","Nacionalidad del NNA afectado",Kind.PAIS,true);
        m.put("GRUPO_NACIONALIDAD_NNA",new Dim("GRUPO_NACIONALIDAD_NNA","Nacionalidad agrupada del NNA afectado",Kind.LISTA,true,null,null,false,NATIONALITY));
        simple(m,"EDAD_REQUIRENTE","Edad del requirente",Kind.EDAD,false);
        catalog(m,"TRAMO_EDAD_REQUIRENTE","Tramo de edad del requirente","TRAMO_EDAD",false,null);
        simple(m,"NACIONALIDAD_REQUIRENTE","Nacionalidad del requirente",Kind.PAIS,false);
        m.put("GRUPO_NACIONALIDAD_REQUIRENTE",new Dim("GRUPO_NACIONALIDAD_REQUIRENTE","Nacionalidad agrupada del requirente",Kind.LISTA,false,null,null,false,NATIONALITY));
        for(String v:List.of("TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_INGRESO","DERECHO","CONTEXTO","CARACTERIZACION","ROL_ESTADO","GRUPO_PRIORITARIO"))
            m.put("ESTADO_"+v,new Dim("ESTADO_"+v,"Estado de la clasificación: "+StatCatalogs.CATALOGS.get(v).toLowerCase(Locale.ROOT),Kind.LISTA,false,null,null,false,STATES));
        var variables=new LinkedHashMap<String,String>();
        for(String v:VARIABLES)variables.put(v,v.equals("REGION_REQUIRENTE")?"Región del requirente":StatCatalogs.CATALOGS.get(v));
        m.put("VARIABLE",new Dim("VARIABLE","Variable",Kind.LISTA,false,null,Unit.VARIABLE,true,Collections.unmodifiableMap(variables)));
        m.put("ESTADO_VARIABLE",new Dim("ESTADO_VARIABLE","Estado de la variable",Kind.LISTA,false,null,Unit.VARIABLE,true,STATES));
        return Collections.unmodifiableMap(m);
    }
    /** Resultados de la primera atencion: codigos de actuacion sin version de catalogo. */
    static final Map<String,String> IMMEDIATE=options(RESULTS);
    static Map<String,String> options(String... pairs){
        var m=new LinkedHashMap<String,String>();for(int i=0;i<pairs.length;i+=2)m.put(pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m);
    }
    private static void put(Map<String,Dim> m,Dim d){m.put(d.code(),d);}
    private static void period(Map<String,Dim> m,String axis,String label){put(m,new Dim("PERIODO_"+axis,label,Kind.PERIODO,false,null,null,false,Map.of()));}
    private static void list(Map<String,Dim> m,String code,String label,String... values){put(m,new Dim(code,label,Kind.LISTA,false,null,null,false,options(values)));}
    private static void simple(Map<String,Dim> m,String code,String label,Kind kind,boolean multiple){put(m,new Dim(code,label,kind,multiple,null,null,false,Map.of()));}
    private static void catalog(Map<String,Dim> m,String code,String label,String catalog,boolean multiple,Unit unit){put(m,new Dim(code,label,Kind.CATALOGO,multiple,catalog,unit,false,Map.of()));}

    // --- Peticion ya validada -------------------------------------------------------------------------------------
    /** Un intervalo del eje de periodo; to = su fin en milisegundos, excluido y recortado al periodo consultado. */
    record Interval(String key,String label,long to){}
    record Span(LocalDate fromDay,LocalDate toDay,long from,long to){}
    /** cut: corte cuyas filas congeladas se leen (E10); nulo = la fuente vigente. */
    record Query(Measure measure,List<Dim> rows,List<Dim> columns,Map<Dim,List<Object>> filters,Span span,Basis basis,Mode mode,Long at,Dim mention,
                 boolean proposals,Reports.Reach reach,Map<String,List<Interval>> axes,Long cut){
        List<Dim> axesDims(){var all=new ArrayList<>(rows);all.addAll(columns);return all;}
        Query over(Span other,Map<String,List<Interval>> otherAxes){return new Query(measure,rows,columns,filters,other,basis,mode,at,mention,proposals,reach,otherAxes,cut);}
        /** La misma consulta sobre otro origen: un corte (con el alcance ya congelado) o la fuente vigente con el alcance dado. */
        Query on(Long otherCut,Reports.Reach otherReach){return new Query(measure,rows,columns,filters,span,basis,mode,at,mention,proposals,otherReach,axes,otherCut);}
    }
    record Sql(String text,Object[] args){}

    // --- Cortes (E10) -----------------------------------------------------------------------------------------------
    /**
     * Dimensiones de valor unico que un corte congela en stat_cut_row (columna d_ + codigo en minusculas). El periodo y el
     * registro tardio se calculan de las fechas congeladas; las multivaluadas y las de unidad, de las relaciones congeladas.
     */
    static final List<Dim> FROZEN=DIMS.values().stream().filter(d->!d.multiple()&&d.unit()==null&&d.kind()!=Kind.PERIODO&&!d.code().equals("TARDANZA")).toList();
    static String frozenColumn(Dim d){return "d_"+d.code().toLowerCase(Locale.ROOT);}
    /** Variables de valor unico con version de catalogo y propuesta congeladas en la fila. */
    static final List<String> KEYED=List.of(StatValues.VIA_INGRESO,StatValues.TIPO_SOLICITUD,StatValues.TIPO_REQUIRENTE);
    static final List<String> PROPOSED=List.of(StatValues.TIPO_SOLICITUD,StatValues.TIPO_REQUIRENTE);
    /** Variables de clasificacion multiple cuyos eventos vigentes congela stat_cut_event. */
    static final List<String> CLASSIFIED=List.of("DERECHO","CONTEXTO","CARACTERIZACION","ROL_ESTADO","GRUPO_PRIORITARIO");

    // --- Consulta -------------------------------------------------------------------------------------------------
    /** Dia civil de Chile (dias desde 1970) de una expresion en milisegundos entre from y to: offset por tramo entre cambios de hora. */
    static String daySql(String expression,long from,long to){
        var rules=BusinessCalendar.ZONE.getRules();
        var sql=new StringBuilder();
        Instant cursor=Instant.ofEpochMilli(from);
        int offset=rules.getOffset(cursor).getTotalSeconds()*1000;
        for(var t=rules.nextTransition(cursor);t!=null&&t.toEpochSecond()*1000<to;t=rules.nextTransition(t.getInstant())){
            sql.append(" WHEN ").append(expression).append('<').append(t.toEpochSecond()*1000).append(" THEN ").append(offset);
            offset=t.getOffsetAfter().getTotalSeconds()*1000;
        }
        String shift=sql.isEmpty()?"("+offset+")":"CASE"+sql+" ELSE "+offset+" END";
        return "FLOOR(("+expression+"+"+shift+")/86400000)";
    }

    /**
     * Arma las sentencias de una peticion; una instancia por sentencia. Tres origenes: la fuente vigente; un corte (E10,
     * q.cut() no nulo), que lee solo stat_cut_* (y los items de catalogo, inmutables) con las mismas etapas y expresiones;
     * y el congelado de un corte (freezer), que entrega sobre la fuente vigente las filas y relaciones que el corte guarda.
     */
    static final class Plan {
        private final Query q;
        private final Map<String,String> raw=new LinkedHashMap<>(),derived=new LinkedHashMap<>(),tables=new LinkedHashMap<>(),aliases=new HashMap<>();
        private final String at,now; // instante de corte en pre (alias q) y en las relaciones (alias b)
        private final String reqMode,solMode;
        /** Corte leido (nulo = fuente vigente) y congelado de un corte (poblacion completa del periodo). */
        private final Long cut;
        private final boolean freeze;

        Plan(Query q){this(q,false);}
        /** Plan del congelado de un corte: todas las COM del alcance con alguna de sus fechas de analisis en el periodo. */
        static Plan freezer(Query q){
            if(q.cut()!=null||q.mode()!=Mode.VIGENTE)throw new IllegalArgumentException("El congelado parte de la fuente vigente.");
            return new Plan(q,true);
        }
        private Plan(Query q,boolean freeze){
            this.q=q;
            this.cut=q.cut();
            this.freeze=freeze;
            if(cut!=null&&q.mode()!=Mode.VIGENTE)throw new IllegalArgumentException("Un corte se consulta solo en el modo vigente.");
            boolean date=q.mode()==Mode.FECHA;
            at=switch(q.mode()){case FECHA->String.valueOf(q.at());case AL_REQ->TerritorialDims.constitutedSql("q");default->null;};
            now=switch(q.mode()){case FECHA->String.valueOf(q.at());case AL_REQ->"b.act_ms";default->null;};
            reqMode=date?" AND q.created_ms<="+q.at():"";
            solMode=date?" AND sol.created_ms<="+q.at():"";
            if(cut!=null){
                raw("id","r.communication_id");raw("req","r.req_id");raw("received_ms","r.received_ms");raw("created_ms","r.created_ms");raw("req_ms","r.req_ms");
                raw("com_state","r.com_state");
            }else{
                raw("id","c.id");raw("req","q.id");raw("received_ms","c.received_ms");raw("created_ms","c.created_ms");raw("req_ms","q.created_ms");
                raw("com_state",comState());
            }
            if(q.mode()==Mode.AL_REQ)raw("act_ms",at);
            for(var d:q.filters().keySet())if(single(d))column(d);
        }
        private void raw(String name,String sql){raw.putIfAbsent(name,sql);}
        private void derive(String name,String sql){derived.putIfAbsent(name,sql);}
        /** La dimension es una columna de la base (no de una relacion ni de la unidad). */
        private boolean single(Dim d){return !d.multiple()&&d.unit()==null&&!unitPeriod(d);}
        private boolean ofUnit(Dim d){return d.unit()!=null&&d.unit()==q.measure().unit()||unitPeriod(d);}
        private boolean unitPeriod(Dim d){return d.kind()==Kind.PERIODO&&q.basis()==Basis.CREACION_CASO;}

        // --- Base por COM ------------------------------------------------------------------------------------------
        private String caseReach(String cas,String scope){
            var r=q.reach();
            if(r.all())return "1=1";
            var parts=new ArrayList<String>();
            if(!r.scopes().isEmpty())parts.add(scope+".id IN ("+r.scopes().stream().map(String::valueOf).collect(Collectors.joining(","))+")");
            if(!r.matters().isEmpty())parts.add("("+scope+".regional=TRUE AND "+cas+" IN ("+r.matters().stream().map(StatSource::code).collect(Collectors.joining(","))+"))");
            return parts.isEmpty()?"1=0":"("+String.join(" OR ",parts)+")";
        }
        private String pre(){
            String from=String.valueOf(q.span().from()),to=String.valueOf(q.span().to());
            var sql=new StringBuilder("SELECT ");
            sql.append(raw.entrySet().stream().map(e->e.getValue()+" AS "+e.getKey()).collect(Collectors.joining(",")));
            if(cut!=null){
                // El corte ya esta acotado a su alcance: solo el periodo y la fecha de analisis sobre las fechas congeladas.
                sql.append(" FROM stat_cut_row r WHERE r.cut_id=").append(cut).append(" AND ").append(switch(q.basis()){
                    case RECEPCION -> "r.received_ms>="+from+" AND r.received_ms<"+to;
                    case REGISTRO -> "r.created_ms>="+from+" AND r.created_ms<"+to;
                    case CONSTITUCION_REQ -> "r.req_ms>="+from+" AND r.req_ms<"+to;
                    case CREACION_CASO -> "EXISTS(SELECT /*+ INDEX(cx "+CUT_CASE_INDEX+") */ 1 FROM stat_cut_case cx WHERE cx.cut_id=r.cut_id AND cx.communication_id=r.communication_id AND cx.created_ms>="+from+" AND cx.created_ms<"+to+")";
                });
                if(q.measure().population()==Population.REQUERIMIENTO)sql.append(" AND r.req_id IS NOT NULL");
                return sql.toString();
            }
            boolean date=q.mode()==Mode.FECHA;
            String req=date?"qr":"q",sol=date?"solr":"sol";
            sql.append(" FROM communication c LEFT JOIN requirement q ON q.communication_id=c.id").append(reqMode)
                .append(" LEFT JOIN solicitation sol ON sol.communication_id=c.id").append(solMode)
                .append(" LEFT JOIN attention_record ar ON ar.requirement_id=q.id");
            if(date)sql.append(" LEFT JOIN requirement qr ON qr.communication_id=c.id LEFT JOIN solicitation solr ON solr.communication_id=c.id");
            sql.append(" JOIN org_scope s ON s.id=").append(TerritorialDims.referenceScopeSql("c",req,sol)).append(" WHERE ");
            String received="c.received_ms>="+from+" AND c.received_ms<"+to,registered="c.created_ms>="+from+" AND c.created_ms<"+to,
                constituted="q.created_ms>="+from+" AND q.created_ms<"+to,
                opened="EXISTS(SELECT 1 FROM case_record cx WHERE cx.requirement_id=q.id AND cx.created_ms>="+from+" AND cx.created_ms<"+to+")";
            // El congelado guarda toda COM que alguna fecha de analisis ubica en el periodo; la consulta del corte elige la suya.
            if(freeze)sql.append("((").append(received).append(") OR (").append(registered).append(") OR (").append(constituted).append(") OR ").append(opened).append(')');
            else sql.append(switch(q.basis()){
                case RECEPCION -> received;
                case REGISTRO -> registered;
                case CONSTITUCION_REQ -> constituted;
                case CREACION_CASO -> opened;
            });
            if(date)sql.append(" AND c.created_ms<=").append(q.at());
            if(q.measure().population()==Population.REQUERIMIENTO)sql.append(" AND q.id IS NOT NULL");
            // Alcance de REPORTES sobre el ambito de referencia vigente, antes de contar (DISENO 5.6).
            sql.append(" AND ").append(caseReach("(CASE WHEN "+req+".id IS NULL THEN "+sol+".suggested_matter ELSE "+req+".matter END)","s"));
            return sql.toString();
        }
        private String state(){
            if(freeze)return "1=1"; // el corte guarda toda la poblacion: computables, excluidas y antecedentes
            return switch(q.measure().population()){
                case COMPUTABLE -> "b0.com_state NOT IN ('INVALIDA','VINCULADA')"; // D25
                case EXCLUIDA -> text("b0.com_state")+"='INVALIDA'";
                case ANTECEDENTE -> text("b0.com_state")+"='VINCULADA'";
                case REQUERIMIENTO -> "1=1";
            };
        }
        /** Estado de la COM: vigente o, a una fecha, el que acreditan los hitos y el vinculo (una COM se vincula una sola vez). */
        private String comState(){
            if(q.mode()!=Mode.FECHA)return "c.state";
            String t=String.valueOf(q.at());
            String discard=" FROM milestone m WHERE m.communication_id=c.id AND m.event IN ('SOL_DESCARTADA','COM_DESCARTADA')";
            return "COALESCE((SELECT m.detail"+discard+" AND m.at_ms<="+t+" ORDER BY m.at_ms DESC,m.id DESC LIMIT 1),"
                +"CASE WHEN c.state IN ('INVALIDA','DUPLICADA') AND NOT EXISTS(SELECT 1"+discard+") THEN c.state"
                +" WHEN EXISTS(SELECT 1 FROM communication_link k WHERE k.communication_id=c.id AND k.proposed_ms<="+t+" AND (k.returned_ms IS NULL OR k.returned_ms>"+t+")) THEN 'VINCULADA'"
                +" WHEN q.id IS NOT NULL AND q.solicitation_id IS NULL THEN 'ATENDIDA'"
                +" WHEN q.id IS NOT NULL OR EXISTS(SELECT 1 FROM milestone m WHERE m.communication_id=c.id AND m.event='SOL_DISTRIBUIDA' AND m.at_ms<="+t+") THEN 'ENVIADA' ELSE 'NUEVA' END)";
        }

        /** Columna d_* de una dimension de valor unico. */
        private String column(Dim d){
            String name="d_"+d.code();
            if(aliases.containsKey(name))return aliases.get(name);
            if(derived.containsKey(name))return name;
            if(cut!=null&&FROZEN.contains(d)){
                // En un corte, el valor congelado: nada se recalcula desde la fuente vigente.
                raw(name,"r."+frozenColumn(d));aliases.put(name,name);
                return name;
            }
            switch(d.code()){
                case "TARDANZA" -> derive(name,"CASE WHEN b0.created_ms>="+q.span().to()+" THEN 'SI' ELSE 'NO' END");
                case "ESTADO_COM" -> derive(name,q.mode()==Mode.AL_INGRESO?"CASE WHEN b0.direct=1 THEN 'ATENDIDA' ELSE 'NUEVA' END"
                    :q.mode()==Mode.AL_REQ?"CASE WHEN b0.req IS NULL THEN 'PENDIENTE' WHEN b0.direct=1 THEN 'ATENDIDA' ELSE 'ENVIADA' END":"b0.com_state");
                case "ESTADO_SOL" -> {raw("sol_state",solState());derive(name,"b0.sol_state");}
                case "ESTADO_REQ" -> {raw("req_state",reqState());derive(name,"b0.req_state");}
                case "CANAL" -> {raw("channel","c.channel");derive(name,"b0.channel");}
                case "MECANISMO" -> derive(name,"CASE WHEN b0.external=1 THEN 'FORMULARIO' WHEN b0.direct=1 THEN 'ATENCION_DIRECTA' ELSE 'MANUAL' END");
                case "VIA_INGRESO","TIPO_SOLICITUD","TIPO_REQUIRENTE" -> {
                    key(d.code());
                    derive(name,"b0.k_"+d.code());
                }
                case "VIA_AGRUPACION" -> {
                    key("VIA_INGRESO");version("VIA_INGRESO");
                    derive(name,"COALESCE((SELECT vi.parent_code FROM stat_catalog_item vi WHERE vi.version_id=b0.v_VIA_INGRESO AND vi.code=b0.k_VIA_INGRESO),b0.k_VIA_INGRESO)");
                }
                case "ESTADO_TIPO_SOLICITUD","ESTADO_TIPO_REQUIRENTE","ESTADO_VIA_INGRESO" -> {
                    String v=d.code().substring(7);key(v);
                    derive(name,"CASE WHEN b0.k_"+v+" IN ("+SPECIAL_SQL+") THEN b0.k_"+v+" ELSE 'INFORMADO' END");
                }
                case "ESTADO_DERECHO","ESTADO_CONTEXTO","ESTADO_CARACTERIZACION","ESTADO_ROL_ESTADO","ESTADO_GRUPO_PRIORITARIO" -> {consolidated(d.code().substring(7));aliases.put(name,"z_"+d.code().substring(7));return aliases.get(name);}
                case "MODALIDAD" -> derive(name,"CASE WHEN b0.modality IS NULL THEN "+absent()+" WHEN b0.h_MODALIDAD=1 THEN 'PENDIENTE' ELSE b0.modality END");
                case "REGION_REQUIRENTE" -> declared(name,"requester_region");
                case "COMUNA_REQUIRENTE" -> declared(name,"requester_commune");
                case "REGION_HECHOS" -> declared(name,"facts_region");
                case "COMUNA_HECHOS" -> declared(name,"facts_commune");
                case "REGION_ORIGEN" -> {origin();derive(name,known("b0.o_region"));}
                case "TERRITORIO_COBERTURA" -> {
                    origin();
                    derive(name,"CASE WHEN b0.o_region IS NULL AND b0.evid=0 THEN 'PENDIENTE' ELSE "+TerritorialDims.coverageSql("b0.o_region","b0.received_ms")+" END");
                }
                case "AMBITO" -> {raw("ref_scope","s.code");derive(name,"b0.ref_scope");}
                case "SEDE_INICIAL" -> {first();derive(name,scopeColumn("sc_first","code","'PENDIENTE'"));}
                // DI-13: la primera adscripcion acreditada en el instante (misma regla temporal que SEDE_INICIAL): una sede
                // por su codigo, el nivel central como una sola categoria y, sin destino, pendiente de asignacion.
                case "ADSCRIPCION_INICIAL" -> {first();derive(name,"COALESCE((SELECT CASE WHEN x.regional=TRUE THEN x.code ELSE '"+NATIONAL_OFFICE+"' END FROM org_scope x WHERE x.id=b0.sc_first),'"+PENDING+"')");}
                case "SEDE_VIGENTE" -> {current();derive(name,scopeColumn("sc","code","'PENDIENTE'"));}
                case "UNIDAD" -> {current();derive(name,scopeColumn("sc","unit_code","'PENDIENTE'"));}
                case "NIVEL" -> {current();derive(name,"COALESCE((SELECT CASE WHEN x.regional=TRUE THEN 'REGIONAL' ELSE 'CENTRAL' END FROM org_scope x WHERE x.id=b0.sc),'PENDIENTE')");}
                case "REGION_SEDE" -> {current();derive(name,"COALESCE((SELECT CASE WHEN x.regional=TRUE THEN COALESCE(x.region_code,'SIN_INFORMACION') ELSE 'NIVEL_CENTRAL' END FROM org_scope x WHERE x.id=b0.sc),'PENDIENTE')");}
                case "MATERIA" -> derive(name,"CASE WHEN b0.h_MATERIA=1 THEN 'PENDIENTE' WHEN b0.matter IS NOT NULL THEN b0.matter"
                    +(q.mode()==Mode.AL_REQ?" WHEN b0.req IS NULL THEN 'PENDIENTE'":"")+" WHEN b0.ref_regional=1 THEN 'PENDIENTE' ELSE 'NO_APLICA' END");
                case "COMPLEJIDAD" -> derive(name,"CASE WHEN b0.req IS NULL THEN "+absent()+" WHEN b0.h_COMPLEJIDAD=1 THEN 'PENDIENTE' ELSE COALESCE(b0.complexity,'PENDIENTE') END");
                case "ORIGEN_REQ" -> derive(name,"CASE WHEN b0.req IS NULL THEN "+absent()+" WHEN b0.direct=1 THEN 'ATENCION_DIRECTA' ELSE 'SOL' END");
                case "RESULTADO_PRIMERA_ATENCION" -> {raw("result",immediate("q"));derive(name,"CASE WHEN b0.result IS NOT NULL THEN b0.result"+(q.mode()==Mode.AL_REQ?" WHEN b0.req IS NULL THEN 'PENDIENTE'":"")+" ELSE 'NO_APLICA' END");}
                case "EDAD_REQUIRENTE" -> {requester("age_years","a_req");derive(name,"CASE WHEN b0.st_req='NO_APLICA' THEN -2 WHEN b0.a_req IS NOT NULL THEN b0.a_req WHEN b0.evid=1 THEN -1 ELSE -3 END");}
                case "TRAMO_EDAD_REQUIRENTE" -> {requester("age_years","a_req");derive(name,"CASE WHEN b0.st_req='NO_APLICA' THEN 'NO_APLICA' ELSE COALESCE("+PersonDims.bracketSql("b0.a_req")+","+unknown()+") END");}
                case "NACIONALIDAD_REQUIRENTE" -> {requester("nationality","n_req");derive(name,"CASE WHEN b0.st_req='NO_APLICA' THEN 'NO_APLICA' ELSE "+known("b0.n_req")+" END");}
                case "GRUPO_NACIONALIDAD_REQUIRENTE" -> {requester("nationality","n_req");derive(name,"CASE WHEN b0.st_req='NO_APLICA' THEN 'NO_APLICA' WHEN b0.n_req IS NULL THEN "+unknown()+" ELSE "+PersonDims.nationalityGroupSql("b0.n_req")+" END");}
                default -> {
                    if(d.kind()!=Kind.PERIODO)throw new IllegalArgumentException(d.code());
                    derive(name,bucket(d,"b0."+switch(q.basis()){case REGISTRO->"created_ms";case CONSTITUCION_REQ->"req_ms";default->"received_ms";}));
                }
            }
            raws(d.code());
            return name;
        }
        /** Columnas directas que usan algunas dimensiones. */
        private void raws(String code){
            switch(code){
                case "ESTADO_COM","MECANISMO","ORIGEN_REQ" -> {
                    raw("direct","CASE WHEN q.id IS NOT NULL AND q.solicitation_id IS NULL THEN 1 ELSE 0 END");
                    raw("external","CASE WHEN c.external_source IS NOT NULL THEN 1 ELSE 0 END");
                }
                case "MODALIDAD" -> {raw("modality","ar.modality");raw("h_MODALIDAD",history("MODALIDAD"));}
                case "MATERIA" -> {
                    raw("matter","CASE WHEN q.id IS NOT NULL THEN q.matter"+(q.mode()==Mode.AL_REQ?"":" ELSE sol.suggested_matter")+" END");
                    raw("h_MATERIA",history("MATERIA"));raw("ref_regional","CASE WHEN s.regional=TRUE THEN 1 ELSE 0 END");
                }
                case "COMPLEJIDAD" -> {raw("complexity","q.complexity");raw("h_COMPLEJIDAD",history("COMPLEJIDAD"));}
                default -> {}
            }
        }
        /** Sin REQ: no aplica, salvo "al constituirse el REQ", donde aun no hay evidencia. */
        private String absent(){return q.mode()==Mode.AL_REQ?"(CASE WHEN b0.req IS NULL THEN 'PENDIENTE' ELSE 'NO_APLICA' END)":"'NO_APLICA'";}
        /**
         * 1 si el valor vigente no acredita el del instante: hay un cambio auditado posterior (por codigo de accion o hito,
         * nunca por texto) o una version posterior de la ficha. Al ingreso, si hubo mas de un valor.
         */
        private String history(String code){
            String audits="SELECT %s FROM audit_event h WHERE h.resource_type='REQ' AND h.resource_id=q.id AND h.action='"+(code.equals("MATERIA")?"REQ_MATTER":"REQ_COMPLEJIDAD")+"'";
            String versions="SELECT %s FROM attention_revision h WHERE h.requirement_id=q.id";
            String redirects="SELECT 1 FROM milestone h WHERE h.communication_id=c.id AND h.event='SOL_REDIRIGIDA'";
            String condition=switch(q.mode()){
                case VIGENTE -> "1=0";
                case AL_INGRESO -> switch(code){
                    case "COMPLEJIDAD" -> "("+audits.formatted("COUNT(*)")+")>1";
                    case "MODALIDAD" -> "("+versions.formatted("COUNT(*)")+")>1";
                    default -> "EXISTS("+audits.formatted("1")+") OR EXISTS("+redirects+")";
                };
                default -> switch(code){
                    case "COMPLEJIDAD" -> "EXISTS("+audits.formatted("1")+" AND h.at_ms>"+at+")";
                    case "MODALIDAD" -> "EXISTS("+versions.formatted("1")+" AND h.registered_ms>"+at+")";
                    default -> "q.id IS NOT NULL AND EXISTS("+audits.formatted("1")+" AND h.at_ms>"+at+")"
                        +(q.mode()==Mode.FECHA?" OR q.id IS NULL AND sol.id IS NOT NULL AND EXISTS("+redirects+" AND h.at_ms>"+at+")":"");
                };
            };
            return "CASE WHEN "+condition+" THEN 1 ELSE 0 END";
        }
        private String solState(){
            String last="(SELECT CASE m.event WHEN 'SOL_DEVUELTA' THEN 'DEVUELTA' WHEN 'SOL_DESCARTADA' THEN 'DESCARTADA'"
                +" WHEN 'COM_DESCARTADA' THEN 'DESCARTADA' WHEN 'SOL_ASUMIDA' THEN 'ASUMIDA' ELSE 'PENDIENTE' END FROM milestone m"
                +" WHERE m.communication_id=c.id AND %s ORDER BY m.at_ms %s,m.id %s LIMIT 1)";
            return switch(q.mode()){
                case VIGENTE -> "COALESCE(sol.state,'NO_APLICA')";
                case FECHA -> "CASE WHEN sol.id IS NULL THEN 'NO_APLICA' ELSE COALESCE("
                    +last.formatted("(m.resource_type='SOL' OR m.event='COM_DESCARTADA') AND m.at_ms<="+at,"DESC","DESC")+",'SIN_INFORMACION') END";
                case AL_INGRESO -> "CASE WHEN sol.id IS NULL THEN 'NO_APLICA' ELSE COALESCE("+last.formatted("m.resource_type='SOL'","ASC","ASC")+",'SIN_INFORMACION') END";
                case AL_REQ -> "CASE WHEN q.id IS NULL OR q.solicitation_id IS NULL THEN 'NO_APLICA' ELSE 'ASUMIDA' END";
            };
        }
        private String reqState(){
            return switch(q.mode()){
                case VIGENTE -> "COALESCE(q.state,'NO_APLICA')";
                case FECHA -> "CASE WHEN q.id IS NULL THEN 'NO_APLICA' ELSE COALESCE((SELECT CASE m.event WHEN 'REQ_CERRADO' THEN 'CERRADO' ELSE 'ABIERTO' END FROM milestone m WHERE m.requirement_id=q.id"
                    +" AND m.event IN ('REQ_CREADO','REQ_CERRADO','REQ_REACTIVADO') AND m.at_ms<="+at+" ORDER BY m.at_ms DESC,m.id DESC LIMIT 1),'ABIERTO') END";
                case AL_INGRESO -> "CASE WHEN q.id IS NULL THEN 'NO_APLICA' ELSE 'ABIERTO' END";
                case AL_REQ -> "CASE WHEN q.id IS NULL THEN 'PENDIENTE' ELSE 'ABIERTO' END";
            };
        }
        /** Resultado de la primera atencion (unica fuente: requirement.immediate_result), con su cierre hasta el corte. */
        private String immediate(String req){
            if(!q.mode().evidence())return req+".immediate_result";
            String cut=req.equals("q")?at:now;
            return "CASE WHEN "+req+".immediate_result IS NOT NULL AND EXISTS(SELECT 1 FROM milestone m WHERE m.requirement_id="+req+".id AND m.event='REQ_CERRADO' AND m.detail='PRIMERA_ATENCION' AND m.at_ms<="+cut+") THEN "+req+".immediate_result END";
        }
        private String firstEvent(String variable){
            return "(SELECT MIN(fe.recorded_ms) FROM stat_event fe WHERE fe.communication_id=c.id AND fe.variable='"+variable+"')";
        }
        private String eventAt(String variable){return q.mode()==Mode.AL_INGRESO?firstEvent(variable):at;}
        /** La via al ingreso es la derivada del canal; sin ella, el primer evento. */
        private boolean derivedVia(String variable){return q.mode()==Mode.AL_INGRESO&&variable.equals(StatValues.VIA_INGRESO);}
        /** k_: codigo de una variable de valor unico en el modo temporal o, si no esta informada, su estado. */
        private void key(String variable){
            if(cut!=null){raw("k_"+variable,"r.d_"+variable.toLowerCase(Locale.ROOT));return;}
            String t=eventAt(variable);
            String value="COALESCE("+StatValues.codeSqlAt("c",variable,t)+","+StatValues.stateSqlAt("c",variable,t)+")";
            raw("k_"+variable,derivedVia(variable)?"CASE WHEN "+StatValues.derivedVersion("c",variable,null)+" IS NOT NULL THEN "+StatValues.derivedCode("c")+" ELSE "+value+" END":value);
        }
        /** v_: version del catalogo con que se registro. */
        private void version(String variable){
            if(cut!=null){raw("v_"+variable,"r.v_"+variable.toLowerCase(Locale.ROOT));return;}
            String value=StatValues.versionSqlAt("c",variable,eventAt(variable));
            raw("v_"+variable,derivedVia(variable)?"COALESCE("+StatValues.derivedVersion("c",variable,null)+","+value+")":value);
        }
        /**
         * z_: estado consolidado del REQ y de los Casos dentro del alcance, con la precedencia de
         * Rights.consolidatedStateSql (INFORMADO, estado especial del REQ, el del primer Caso con uno).
         */
        private void consolidated(String variable){
            if(cut!=null){raw("z_"+variable,"r.d_estado_"+variable.toLowerCase(Locale.ROOT));return;}
            if(derived.containsKey("z_"+variable))return;
            raw("zd_"+variable,StatValues.setStateSqlAt("REQ","q.id",variable,eventAt(variable)));
            if(q.mode()==Mode.AL_INGRESO)raw("f_"+variable,firstEvent(variable));
            String child=StatValues.setStateSqlAt("CAS","cc.id",variable,switch(q.mode()){case AL_INGRESO->"b0.f_"+variable;case AL_REQ->"b0.act_ms";default->at;});
            String cases=" FROM case_record cc JOIN org_scope cs ON cs.id=cc.scope_id WHERE cc.requirement_id=b0.req AND "+caseReach("cc.matter","cs");
            derive("z_"+variable,"CASE WHEN b0.req IS NULL THEN 'PENDIENTE' WHEN b0.zd_"+variable+"='INFORMADO' OR EXISTS(SELECT 1"+cases+" AND "+child+"='INFORMADO') THEN 'INFORMADO' WHEN b0.zd_"+variable+"<>'PENDIENTE' THEN b0.zd_"+variable
                +" ELSE COALESCE((SELECT "+child+cases+" AND "+child+"<>'PENDIENTE' ORDER BY cc.id LIMIT 1),'PENDIENTE') END");
        }
        private void evidence(){
            if(cut!=null){raw("evid","1");return;} // un corte es vigente en su instante
            raw("evid",q.mode().evidence()?"CASE WHEN ("+TerritorialDims.externalEvidenceSql("c","q",q.mode().dims,q.at())+") OR "
                +TerritorialDims.versionSql("c","q",q.mode().dims,q.at())+" IS NOT NULL THEN 1 ELSE 0 END":"1");
        }
        private String unknown(){evidence();return "CASE WHEN b0.evid=1 THEN 'SIN_INFORMACION' ELSE 'PENDIENTE' END";}
        private String known(String column){return "COALESCE("+column+","+unknown()+")";}
        private void declared(String name,String field){raw("x_"+field,TerritorialDims.locationSql("c","q",q.mode().dims,q.at(),field));derive(name,known("b0.x_"+field));}
        private void requester(String field,String name){
            evidence();
            raw("st_req",PersonDims.requesterSql("c","q",q.mode().dims,q.at(),"state"));
            raw(name,PersonDims.requesterSql("c","q",q.mode().dims,q.at(),field));
        }
        /** Region de origen registrada (D21), con su evidencia hasta el corte. */
        private void origin(){
            evidence();
            if(!q.mode().evidence()){raw("o_region",OriginRegion.registeredSql("c","q"));return;}
            String cut=" AND w.registered_ms<="+at;
            raw("o_region","COALESCE((SELECT v.region_code FROM attention_revision v WHERE v.id=(SELECT MIN(w.id) FROM attention_revision w WHERE w.requirement_id=q.id AND w.region_code IS NOT NULL"+cut+")),"
                +"CASE WHEN "+TerritorialDims.externalEvidenceSql("c","q",q.mode().dims,q.at())+" THEN "+OriginRegion.formularioSql("c")+" END,"
                +"(SELECT v.region_code FROM intake_revision v WHERE v.id=(SELECT MIN(w.id) FROM intake_revision w WHERE w.communication_id=c.id AND w.region_code IS NOT NULL"+cut+")))");
        }
        private void current(){raw("sc",TerritorialDims.scopeSql("c","q","sol",q.mode().dims,q.at()));}
        /** Primer ambito de destino acreditado en el instante (SEDE_INICIAL y ADSCRIPCION_INICIAL). */
        private void first(){
            raw("sc_first",TerritorialDims.initialScopeSql("c","q","sol",q.mode().dims,q.at()));
        }
        private String scopeColumn(String column,String field,String missing){return "COALESCE((SELECT x."+field+" FROM org_scope x WHERE x.id=b0."+column+"),"+missing+")";}
        /** Posicion del intervalo (o dia civil) de una fecha que ya esta dentro del periodo. */
        private String bucket(Dim d,String expression){
            if(d.code().equals("PERIODO_DIA"))return daySql(expression,q.span().from(),q.span().to());
            var list=q.axes().get(d.code());
            if(list.size()==1)return "0";
            var sql=new StringBuilder("CASE");
            for(int i=0;i<list.size()-1;i++)sql.append(" WHEN ").append(expression).append('<').append(list.get(i).to()).append(" THEN ").append(i);
            return sql.append(" ELSE ").append(list.size()-1).append(" END").toString();
        }

        // --- Relaciones --------------------------------------------------------------------------------------------
        /** Relacion m_* (com, val, w) de una dimension multivaluada, con la fila de estado de las COM sin valores. */
        private String relation(Dim d){
            String name="m_"+d.code();
            if(tables.containsKey(name))return name;
            String values,missing;
            switch(d.code()){
                case "DERECHO","DERECHO_EJE","DERECHO_SUBCATEGORIA" -> {
                    consolidated("DERECHO");
                    values="SELECT y.com AS com,y.val AS val,1 AS w FROM ("+rights(level(d.code()),"")+") y WHERE y.val IS NOT NULL GROUP BY y.com,y.val";
                    missing=d.code().equals("DERECHO_SUBCATEGORIA")?"CASE WHEN b.z_DERECHO='INFORMADO' THEN 'SIN_DESGLOSE' ELSE b.z_DERECHO END":"b.z_DERECHO";
                }
                case "CONTEXTO","CARACTERIZACION","ROL_ESTADO","GRUPO_PRIORITARIO" -> {
                    consolidated(d.code());
                    tables.putIfAbsent("ev_"+d.code(),events(d.code()));
                    values="SELECT y.com AS com,y.val AS val,1 AS w FROM ev_"+d.code()+" y GROUP BY y.com,y.val";
                    missing="b.z_"+d.code();
                }
                case "TIPO_ACTUACION" -> {actions();values="SELECT y.com AS com,y.action_type AS val,COUNT(*) AS w FROM acts y GROUP BY y.com,y.action_type";missing="'NO_APLICA'";}
                case "ORIGEN_ACTUACION" -> {actions();values="SELECT y.com AS com,y.origin AS val,COUNT(*) AS w FROM acts y GROUP BY y.com,y.origin";missing="'NO_APLICA'";}
                case "TIPO_INSTITUCION" -> {
                    actions();
                    values="SELECT "+(cut==null?"":"/*+ "+institutionIndex("ni")+" */ ")+"y.com AS com,COALESCE(ni.type_code,'SIN_INFORMACION') AS val,COUNT(*) AS w FROM acts y JOIN "+institution("ni","y.note+0")+" GROUP BY y.com,COALESCE(ni.type_code,'SIN_INFORMACION')";
                    missing="'NO_APLICA'";
                }
                default -> {
                    evidence();
                    String val=switch(d.code()){
                        case "REGION_NNA" -> "pa.region_code";
                        case "COMUNA_NNA" -> "pa.commune";
                        case "EDAD_NNA" -> "pa.age_years";
                        case "TRAMO_EDAD_NNA" -> cut!=null?"pa.age_bracket":PersonDims.bracketSql("pa.age_years");
                        case "NACIONALIDAD_NNA" -> "pa.nationality";
                        case "GRUPO_NACIONALIDAD_NNA" -> "CASE WHEN pa.nationality IS NULL THEN NULL ELSE "+PersonDims.nationalityGroupSql("pa.nationality")+" END";
                        default -> throw new IllegalArgumentException(d.code());
                    };
                    values="SELECT y.com AS com,y.val AS val,COUNT(*) AS w FROM (SELECT pa.com AS com,"+val+" AS val FROM ("+affected()
                        +") pa) y WHERE y.val IS NOT NULL GROUP BY y.com,y.val";
                    missing=d.numeric()?"CASE WHEN b.evid=1 THEN -1 ELSE -3 END":"CASE WHEN b.evid=1 THEN 'SIN_INFORMACION' ELSE 'PENDIENTE' END";
                }
            }
            tables.put(name,"SELECT "+FIXED+"b.id AS com,COALESCE(x.val,"+missing+") AS val,COALESCE(x.w,0) AS w FROM base0 b LEFT JOIN ("+values+") x ON x.com+0=b.id+0");
            return name;
        }
        /** PersonDims.affectedSql sobre las COM de la base, sin recorrer las demas. */
        private String affected(){
            if(cut!=null)return "SELECT "+FIXED+"b.id AS com,p.region_code AS region_code,p.commune AS commune,p.age_years AS age_years,p.nationality AS nationality,p.age_bracket AS age_bracket"
                +" FROM base0 b JOIN stat_cut_person p ON p.cut_id="+cut+" AND p.communication_id=b.id+0";
            String all=" FROM communication c LEFT JOIN",some=" FROM base0 b JOIN communication c ON c.id=b.id+0 LEFT JOIN";
            String sql=PersonDims.affectedSql(q.mode().dims,q.at());
            if(sql.split(java.util.regex.Pattern.quote(all),-1).length!=4)throw new IllegalStateException("Consulta de NNA afectados inesperada.");
            return sql.replace(all,some).replace("SELECT c.id","SELECT "+FIXED+"c.id");
        }
        /** Eventos que agregan un valor vigente (o en el corte) de la variable: en el REQ o en un Caso dentro del alcance. */
        private String events(String variable){
            return events(variable,false);
        }
        private String events(String variable,boolean origin){
            if(this.cut!=null)return "SELECT "+fixed(CUT_EVENT)+"b.id AS com,e.event_id AS event,e.code AS val,e.catalog_version_id AS ver"
                +(origin?",o.record_type,o.record_id":"")+" FROM base0 b JOIN stat_cut_event e ON e.cut_id="+this.cut
                +" AND e.communication_id=b.id+0 AND e.variable='"+variable+"'"
                +(origin?" JOIN stat_export_cut_origin o ON o.cut_id=e.cut_id AND o.source_kind='EVENTO' AND o.value_id=e.event_id":"");
            String cut=now;
            if(q.mode()==Mode.AL_INGRESO){raw("f_"+variable,firstEvent(variable));cut="b.f_"+variable;}
            return "SELECT "+FIXED+"b.id AS com,a.id AS event,a.code AS val,a.catalog_version_id AS ver"
                +(origin?",a.record_type,a.record_id":"")+" FROM base0 b JOIN stat_event a ON a.communication_id=b.id+0 AND a.variable='"+variable+"'"
                +" LEFT JOIN case_record cc ON a.record_type='CAS' AND cc.id=a.record_id LEFT JOIN org_scope cs ON cs.id=cc.scope_id"
                +" WHERE a.record_type IN ('REQ','CAS') AND "+StatValues.addedSql(cut)+" AND (a.record_type='REQ' OR "+caseReach("cc.matter","cs")+")";
        }
        private static String level(String code){
            return switch(code){
                case "DERECHO_EJE" -> "CASE WHEN i.level=1 THEN i.code WHEN i.level=2 THEN i.parent_code ELSE p.parent_code END";
                case "DERECHO_SUBCATEGORIA" -> "CASE WHEN i.level>=3 THEN i.code END";
                default -> "CASE WHEN i.level>=3 THEN i.parent_code ELSE i.code END";
            };
        }
        /** Derechos con su nivel: una subcategoria implica su derecho y su eje en el conteo. */
        private String rights(String value,String extra){
            tables.putIfAbsent("ev_DERECHO",events("DERECHO"));
            return "SELECT "+FIXED+"e.com AS com,"+value+" AS val,e.ver AS ver"+extra+" FROM ev_DERECHO e JOIN stat_catalog_item i ON i.version_id=e.ver+0 AND i.code="+text("e.val")
                +" LEFT JOIN stat_catalog_item p ON p.version_id=i.version_id AND p.code=i.parent_code";
        }
        /**
         * Actuaciones (D36): constancias tipificadas vigentes en el corte (sin rectificacion hasta el) y, despues, sin las
         * reservadas; mas el resultado de la primera atencion desde su unica fuente. Los actos no tienen "valor al
         * ingreso": en ese modo se informan los vigentes.
         */
        private void actions(){
            if(tables.containsKey("acts"))return;
            if(this.cut!=null){
                tables.put("acts","SELECT "+fixed(CUT_ACTION)+"b.id AS com,a.unit_id AS unit,a.action_type AS action_type,a.origin AS origin,a.note_id AS note,a.catalog_version_id AS ver"
                    +" FROM base0 b JOIN stat_cut_action a ON a.cut_id="+this.cut+" AND a.communication_id=b.id+0");
                return;
            }
            String cut=q.mode().evidence()?" AND mn.registered_ms<="+now:"",later=q.mode().evidence()?" AND rx.registered_ms<="+now:"";
            tables.put("acts","SELECT "+FIXED+"b.id AS com,mn.id*2 AS unit,mn.action_type AS action_type,CASE WHEN mn.case_id IS NULL THEN 'GESTION_REQ' ELSE 'GESTION_CASO' END AS origin,mn.id AS note,mn.action_catalog_version AS ver"
                +" FROM base0 b JOIN management_note mn ON mn.requirement_id=b.req+0 LEFT JOIN case_record cc ON cc.id=mn.case_id LEFT JOIN org_scope cs ON cs.id=cc.scope_id"
                +" WHERE mn.action_type IS NOT NULL"+cut+" AND NOT EXISTS(SELECT 1 FROM management_note rx WHERE rx.requirement_id=mn.requirement_id AND rx.rectifies_id=mn.id"+later+") AND mn.reserved=FALSE AND (mn.case_id IS NULL OR "+caseReach("cc.matter","cs")+")"
                +" UNION ALL SELECT y.com,y.unit,y.action_type,'PRIMERA_ATENCION',NULL,NULL FROM (SELECT "+FIXED+"b.id AS com,rq.id*2+1 AS unit,"+immediate("rq")+" AS action_type FROM base0 b JOIN requirement rq ON rq.id=b.req+0) y WHERE y.action_type IS NOT NULL");
        }
        /** Relacion u de las unidades de la medida. */
        private void units(){
            if(tables.containsKey("u"))return;
            switch(q.measure().unit()){
                case ACTUACION -> {actions();tables.put("u","SELECT y.com AS com,y.unit AS unit,y.action_type AS d_TIPO_ACTUACION,y.origin AS d_ORIGEN_ACTUACION,y.note AS note FROM acts y");}
                case CLASIFICACION -> tables.put("u",rights("e.val",",e.event AS unit,"+level("DERECHO_EJE")+" AS d_DERECHO_EJE,"+level("DERECHO")+" AS d_DERECHO,COALESCE("+level("DERECHO_SUBCATEGORIA")+",'SIN_DESGLOSE') AS d_DERECHO_SUBCATEGORIA"));
                case CASO -> tables.put("u",cut!=null
                    ?"SELECT "+fixed("INDEX(cc "+CUT_CASE_INDEX+")")+"b.id AS com,cc.case_id AS unit,cc.created_ms AS case_ms FROM base0 b JOIN stat_cut_case cc ON cc.cut_id="+cut+" AND cc.communication_id=b.id+0 WHERE 1=1"
                        +(q.basis()==Basis.CREACION_CASO?" AND cc.created_ms>="+q.span().from()+" AND cc.created_ms<"+q.span().to():"")
                    :"SELECT "+FIXED+"b.id AS com,cc.id AS unit,cc.created_ms AS case_ms FROM base0 b JOIN case_record cc ON cc.requirement_id=b.req+0 JOIN org_scope cs ON cs.id=cc.scope_id WHERE "+caseReach("cc.matter","cs")
                    +(q.mode()==Mode.FECHA?" AND cc.created_ms<="+q.at():"")
                    +(q.basis()==Basis.CREACION_CASO?" AND cc.created_ms>="+q.span().from()+" AND cc.created_ms<"+q.span().to():""));
                case VARIABLE -> {
                    for(String v:VARIABLES)column(variableState(v));
                    tables.put("u","SELECT b.id AS com,b.id*10+vv.pos AS unit,vv.name AS d_VARIABLE,CASE vv.name"
                        +VARIABLES.stream().map(v->" WHEN '"+v+"' THEN "+(v.equals("REGION_REQUIRENTE")
                            ?"(CASE WHEN b.d_REGION_REQUIRENTE IN ('SIN_INFORMACION','PENDIENTE') THEN b.d_REGION_REQUIRENTE ELSE 'INFORMADO' END)":"b."+column(variableState(v)))).collect(Collectors.joining())
                        +" END AS d_ESTADO_VARIABLE FROM base0 b CROSS JOIN ("+VARIABLES.stream().map(v->"SELECT '"+v+"' AS name,"+VARIABLES.indexOf(v)+" AS pos").collect(Collectors.joining(" UNION ALL "))+") vv");
                }
                case COM -> {}
            }
        }
        private static Dim variableState(String v){return DIMS.get(v.equals("REGION_REQUIRENTE")?v:"ESTADO_"+v);}

        // --- Sentencias --------------------------------------------------------------------------------------------
        private String with(List<Object> args,Map<String,String> more){
            var conditions=new ArrayList<String>();
            var filters=new ArrayList<Object>();
            for(var f:q.filters().entrySet()){
                if(ofUnit(f.getKey()))continue;
                String marks=String.join(",",Collections.nCopies(f.getValue().size(),"?"));
                conditions.add(single(f.getKey())?plain(f.getKey(),"b."+column(f.getKey()))+" IN ("+marks+")":"b.id+0 IN (SELECT fx.com+0 FROM "+relation(f.getKey())+" fx WHERE "+plain(f.getKey(),"fx.val")+" IN ("+marks+"))");
                filters.addAll(f.getValue());
            }
            var sql=new StringBuilder("WITH pre AS (").append(pre()).append(ONCE).append("), base0 AS (SELECT b0.*");
            derived.forEach((name,e)->sql.append(',').append(e).append(" AS ").append(name));
            sql.append(" FROM pre b0 WHERE ").append(state()).append(ONCE).append(')');
            tables.forEach((name,t)->sql.append(", ").append(name).append(" AS (").append(t).append(ONCE).append(')'));
            sql.append(", base AS (SELECT b.* FROM base0 b").append(conditions.isEmpty()?"":" WHERE "+String.join(" AND ",conditions)).append(')');
            args.addAll(filters);
            more.forEach((name,t)->sql.append(", ").append(name).append(" AS (").append(t).append(')'));
            return sql.append(' ').toString();
        }
        /** Filtros de la unidad (sobre u): con una medida de actuaciones o de derechos acotan las unidades, no las COM. */
        private String unitFilters(List<Object> args){
            var out=new StringBuilder();
            for(var f:q.filters().entrySet())if(ofUnit(f.getKey())){
                out.append(" AND ").append(plain(f.getKey(),unitKey(f.getKey()))).append(" IN (").append(String.join(",",Collections.nCopies(f.getValue().size(),"?"))).append(')');
                args.addAll(f.getValue());
            }
            return out.toString();
        }
        private String unitKey(Dim d){
            if(unitPeriod(d))return bucket(d,"u.case_ms");
            if(d.code().equals("TIPO_INSTITUCION"))return "(CASE WHEN ui.note_id IS NULL THEN 'NO_APLICA' ELSE COALESCE(ui.type_code,'SIN_INFORMACION') END)";
            return "u.d_"+d.code();
        }
        private boolean unitInstitution(){
            return q.measure().unit()==Unit.ACTUACION&&(q.axesDims().stream().anyMatch(d->d.code().equals("TIPO_INSTITUCION"))||q.filters().keySet().stream().anyMatch(d->d.code().equals("TIPO_INSTITUCION")));
        }
        /** src: una fila por COM (o unidad) y combinacion de claves pedidas (k0, k1, ...). */
        private String source(List<Dim> dims,List<Object> args){
            boolean com=q.measure().unit()==Unit.COM;
            if(!com)units();
            var select=new StringBuilder("SELECT "+(unitInstitution()?fixed(institutionIndex("ui")):FIXED)+"b.id AS com,").append(com?"0":"u.unit").append(" AS unit");
            var joins=new StringBuilder(" FROM base b");
            if(!com)joins.append(" JOIN u ON u.com+0=b.id+0");
            if(unitInstitution())joins.append(" LEFT JOIN ").append(institution("ui","u.note+0"));
            var joined=new HashSet<String>();
            int i=0;
            for(var d:dims){
                String key;
                if(ofUnit(d))key=unitKey(d);
                else if(single(d))key="b."+column(d);
                else {String t=relation(d);if(joined.add(t))joins.append(" JOIN ").append(t).append(" ON ").append(t).append(".com+0=b.id+0");key=t+".val";}
                select.append(',').append(key).append(" AS k").append(i++);
            }
            if(q.mention()!=null){
                String t=relation(q.mention());
                if(joined.add(t))joins.append(" JOIN ").append(t).append(" ON ").append(t).append(".com+0=b.id+0");
                select.append(',').append(t).append(".val AS mk,").append(t).append(".w AS w");
            }else select.append(",0 AS mk,0 AS w");
            return select.append(joins).append(" WHERE 1=1").append(unitFilters(args)).append(ONCE).toString();
        }
        /** Relaciones multivaluadas unidas a src: con dos o mas, una COM se repite y las menciones se deduplican. */
        private long multiples(){
            var all=q.axesDims().stream().filter(d->!ofUnit(d)&&!single(d)).map(Dim::code).collect(Collectors.toSet());
            if(q.mention()!=null)all.add(q.mention().code());
            return all.size();
        }
        private String n(String z){
            var m=q.measure();
            if(m.mentions())return "COALESCE(SUM("+z+".w),0)";
            if(m.own())return "COUNT(DISTINCT "+z+".unit)";
            return unique(z);
        }
        private String unique(String z){return q.measure().unit()==Unit.COM&&multiples()==0?"COUNT(*)":"COUNT(DISTINCT "+z+".com)";}
        /**
         * Una sola sentencia: celdas, margenes y total, cada parte agrupada sobre sus propios conjuntos (nunca sumando
         * celdas), y con extras los registros tardios (L), las versiones de catalogo (V), la cobertura demografica (D) y
         * la historia sin evidencia (H), para evaluar la base una vez.
         */
        Sql main(int limit,Extras x){
            var dims=q.axesDims();int rows=q.rows().size(),all=dims.size();
            var unitArgs=new ArrayList<Object>();
            String src=source(dims,unitArgs);
            var parts=new ArrayList<String>();
            if(all>0)parts.add(part("C",0,all,all,limit));
            if(rows>0&&rows<all){parts.add(part("R",0,rows,all,limit));parts.add(part("K",rows,all,all,limit));}
            parts.add(part("G",0,0,all,limit));
            var more=new LinkedHashMap<String,String>();
            more.put("src",src);
            if(x!=null){
                more.put("pop",population());
                if(x.until()!=null){
                    String delay=daySql("b.created_ms",q.span().from(),x.until())+"-"+daySql("b.received_ms",q.span().from(),q.span().to());
                    parts.add(extra("L",all,"z.delay","COUNT(*)","0","(SELECT "+delay+" AS delay FROM pop b WHERE b.created_ms>="+q.span().to()+") z GROUP BY z.delay"));
                }
                for(int i=0;i<x.versions().size();i++)parts.add(extra("V"+i,all,"z.ver","0","0","("+versions(x.versions().get(i))+") z WHERE z.ver IS NOT NULL"));
                for(int i=0;i<x.coverage().size();i++)parts.add(coverage("D"+i,all,x.coverage().get(i)));
                for(int i=0;i<x.history().size();i++){column(x.history().get(i));parts.add(extra("H"+i,all,"COALESCE(SUM(b.h_"+x.history().get(i).code()+"),0)","0","0","pop b"));}
            }
            var args=new ArrayList<Object>();
            String with=with(args,more);
            args.addAll(unitArgs);
            return new Sql(with+String.join(" UNION ALL ",parts),args.toArray());
        }
        record Extras(Long until,List<Dim> versions,List<Dim> coverage,List<Dim> history){}
        private String part(String name,int from,int to,int all,int limit){
            var keys=new ArrayList<String>();for(int i=from;i<to;i++)keys.add("k"+i);
            var columns=new StringBuilder();
            for(int i=0;i<all;i++)columns.append(',').append(i>=from&&i<to?"g.k"+i:"NULL").append(" AS k").append(i);
            String distinct=keys.isEmpty()?"":String.join(",",keys)+",",grouped=keys.isEmpty()?"":" GROUP BY "+keys.stream().map(k->"z."+k).collect(Collectors.joining(","));
            String head=keys.stream().map(k->"z."+k+" AS "+k+",").collect(Collectors.joining());
            // Con dos relaciones multivaluadas, cada (COM, valor) se suma una sola vez en las menciones.
            String table=q.mention()!=null&&multiples()>1?"(SELECT DISTINCT "+distinct+"com,unit,mk,w FROM src)":"src";
            return "SELECT '"+name+"' AS part"+columns+",g.uq AS uq,g.n AS n,g.mt AS mt FROM (SELECT "+head+unique("z")+" AS uq,"+n("z")+" AS n,COALESCE(SUM(z.w),0) AS mt FROM "+table+" z"
                +grouped+(keys.isEmpty()?"":" LIMIT "+(limit+1))+") g";
        }
        private static String extra(String name,int all,String uq,String n,String mt,String from){
            var columns=new StringBuilder();
            for(int i=0;i<all;i++)columns.append(",NULL AS k").append(i);
            return "SELECT '"+name+"' AS part"+columns+","+uq+" AS uq,"+n+" AS n,"+mt+" AS mt FROM "+from;
        }
        /** Requerimientos de la poblacion consultada: en las medidas con unidad propia, los que tienen alguna. */
        private String population(){
            return "SELECT b.* FROM base b"+(q.measure().unit()==Unit.COM?"":" WHERE b.id+0 IN (SELECT sx.com+0 FROM src sx)");
        }
        /** Cobertura del dato de una dimension demografica: con dato, no aplica y total de requerimientos. */
        private String coverage(String name,int all,Dim d){
            String unknown=d.numeric()?"-1,-3":"'SIN_INFORMACION','PENDIENTE'",no=d.numeric()?"-2":"'NO_APLICA'";
            String val,from="pop b";
            if(single(d))val="b."+column(d);
            else {String t=relation(d);val=t+".val";from+=" JOIN "+t+" ON "+t+".com+0=b.id+0";}
            return extra(name,all,"COUNT(DISTINCT CASE WHEN "+val+" NOT IN ("+unknown+","+no+") THEN b.id END)","COUNT(DISTINCT CASE WHEN "+val+" IN ("+no+") THEN b.id END)","COUNT(DISTINCT b.id)",from);
        }
        /** Propuestas pendientes de confirmar, aparte de los valores (D28-D30). */
        Sql proposals(){
            var parts=new ArrayList<String>();
            for(String v:List.of(StatValues.TIPO_SOLICITUD,StatValues.TIPO_REQUIRENTE)){
                key(v);proposal(v);
                parts.add("SELECT '"+v+"' AS variable,b.p_"+v+" AS code,COUNT(*) AS n FROM pop b WHERE "+text("b.k_"+v)+"='PENDIENTE' AND b.p_"+v+" IS NOT NULL GROUP BY b.p_"+v);
            }
            var unitArgs=new ArrayList<Object>();
            var more=new LinkedHashMap<String,String>();
            more.put("src",source(List.of(),unitArgs));more.put("pop",population());
            var args=new ArrayList<Object>();
            String with=with(args,more);
            args.addAll(unitArgs);
            return new Sql(with+String.join(" UNION ALL ",parts),args.toArray());
        }
        /** p_: propuesta de una variable (regla PROPUESTA_V1); en un corte, la congelada (no se recalcula desde external_field). */
        private void proposal(String v){raw("p_"+v,cut!=null?"r.p_"+v.toLowerCase(Locale.ROOT):StatProposals.sqlCode("c",v));}
        /** JOIN_FIXED_ORDER y, sobre un corte, las sugerencias de indice dadas (ver CUT_EVENT). */
        private String fixed(String... indexes){
            return cut==null||indexes.length==0?FIXED:"/*+ JOIN_FIXED_ORDER() "+String.join(" ",indexes)+" */ ";
        }
        /** Sugerencia del indice por nota de las instituciones congeladas, para el alias dado (solo sobre un corte). */
        private String institutionIndex(String alias){return "INDEX("+alias+" "+CUT_INSTITUTION_INDEX+")";}
        /** Tipo de institucion de las actuaciones: note_institution o, en un corte, su copia congelada. */
        private String institution(String alias,String note){
            return cut==null?"note_institution "+alias+" ON "+alias+".note_id="+note
                :"stat_cut_action_institution "+alias+" ON "+alias+".cut_id="+cut+" AND "+alias+".note_id="+note;
        }

        // --- Microdatos (E11): las proyecciones reutilizan la base, alcance, evidencia y relaciones del motor. -----------
        Sql exportRows(List<Dim> singles,int limit){
            var select=new StringBuilder("SELECT b.id,b.folio,b.evid");
            raw("folio",cut==null?"c.folio":"(SELECT f.folio FROM stat_export_cut_folio f WHERE f.cut_id=r.cut_id AND f.communication_id=r.communication_id)");
            evidence();
            for(var d:singles)select.append(",b.").append(column(d)).append(" AS ").append(frozenColumn(d));
            for(String v:KEYED){version(v);select.append(",b.v_").append(v);}
            return exportStatement(select+" FROM pop b ORDER BY b.id LIMIT "+(limit+1));
        }

        Sql exportClassifications(boolean groups){
            var parts=new ArrayList<String>();
            for(String v:CLASSIFIED){
                if(!groups&&v.equals("GRUPO_PRIORITARIO"))continue;
                tables.putIfAbsent("ev_"+v,events(v,true));
                if(v.equals("DERECHO")){
                    // La misma jerarquía que usa el agregado, con la procedencia de cada asignación.
                    for(String d:List.of("DERECHO_EJE","DERECHO","DERECHO_SUBCATEGORIA"))
                        parts.add("SELECT z.com,'"+d+"' AS variable,z.val,z.ver,z.record_type,z.record_id FROM ("
                            +rights(level(d),",e.record_type,e.record_id")+") z WHERE z.val IS NOT NULL");
                }else parts.add("SELECT z.com,'"+v+"' AS variable,z.val,z.ver,z.record_type,z.record_id FROM ev_"+v+" z");
            }
            return exportStatement("SELECT z.* FROM ("+String.join(" UNION ALL ",parts)+") z JOIN pop b ON b.id+0=z.com+0"
                +" ORDER BY z.com,z.variable,z.val,z.record_type,z.record_id,z.ver");
        }

        Sql exportActions(){
            actions();
            String provenance=cut!=null?" JOIN stat_export_cut_origin o ON o.cut_id="+cut+" AND o.source_kind='ACTO' AND o.value_id=y.unit+0"
                :" LEFT JOIN management_note m ON m.id=y.note+0";
            String record=cut!=null?"o.record_type,o.record_id":"CASE WHEN m.case_id IS NULL THEN 'REQ' ELSE 'CAS' END AS record_type,COALESCE(m.case_id,b.req) AS record_id";
            return exportStatement("SELECT "+fixed(institutionIndex("ni"))+"y.com,y.unit,y.action_type,y.origin,y.ver,"+record+",ni.type_code,ni.catalog_version_id,ni."
                +(cut==null?"id":"institution_id")+" AS institution_id FROM acts y JOIN pop b ON b.id+0=y.com+0"+provenance
                +" LEFT JOIN "+institution("ni","y.note+0")+" ORDER BY y.com,y.unit,institution_id");
        }

        Sql exportPersons(){
            tables.put("pa",affected());
            return exportStatement("SELECT "+FIXED+"pa.com,pa.region_code,pa.commune,pa.age_years,pa.nationality,"
                +(cut!=null?"pa.age_bracket":PersonDims.bracketSql("pa.age_years"))+" AS age_bracket,CASE WHEN pa.nationality IS NOT NULL THEN "
                +PersonDims.nationalityGroupSql("pa.nationality")+" END AS nationality_group FROM pa JOIN pop b ON b.id+0=pa.com+0 ORDER BY pa.com");
        }

        private Sql exportStatement(String select){
            var unitArgs=new ArrayList<Object>();var more=new LinkedHashMap<String,String>();
            if(q.measure().unit()!=Unit.COM)more.put("src",source(List.of(),unitArgs));
            more.put("pop","SELECT u.* FROM ("+population()+") u WHERE u.com_state NOT IN ('INVALIDA','VINCULADA')"+ONCE);
            var args=new ArrayList<Object>();String from=with(args,more);args.addAll(unitArgs);
            return new Sql(from+select,args.toArray());
        }

        // --- Congelado de un corte (E10) ---------------------------------------------------------------------------
        /** Una sentencia por relacion; filas ordenadas por su clave numerica (la serializacion canonica ordena en Java lo demas). */
        Sql freezeRows(){
            requireFreeze();
            var select=new StringBuilder("SELECT b.id AS communication_id,b.req AS req_id,b.received_ms AS received_ms,b.created_ms AS created_ms,b.req_ms AS req_ms,b.com_state AS com_state");
            for(String v:KEYED){key(v);version(v);select.append(",b.v_").append(v).append(" AS v_").append(v.toLowerCase(Locale.ROOT));}
            for(String v:PROPOSED){proposal(v);select.append(",b.p_").append(v).append(" AS p_").append(v.toLowerCase(Locale.ROOT));}
            for(var d:FROZEN)select.append(",b.").append(column(d)).append(" AS ").append(frozenColumn(d));
            return statement(select.append(" FROM base b ORDER BY b.id").toString());
        }
        Sql freezeEvents(){
            requireFreeze();
            var parts=new ArrayList<String>();
            for(String v:CLASSIFIED){
                tables.putIfAbsent("ev_"+v,events(v));
                parts.add("SELECT y.com AS com,y.event AS event,'"+v+"' AS variable,y.val AS val,y.ver AS ver FROM ev_"+v+" y");
            }
            return statement("SELECT z.com AS communication_id,z.event AS event_id,z.variable AS variable,z.val AS code,z.ver AS catalog_version_id FROM ("+String.join(" UNION ALL ",parts)+") z ORDER BY z.event");
        }
        Sql freezeActions(){
            requireFreeze();actions();
            return statement("SELECT y.unit AS unit_id,y.com AS communication_id,y.action_type AS action_type,y.origin AS origin,y.note AS note_id,y.ver AS catalog_version_id FROM acts y ORDER BY y.unit");
        }
        Sql freezeInstitutions(){
            requireFreeze();actions();
            return statement("SELECT ni.id AS institution_id,ni.note_id AS note_id,ni.type_code AS type_code,ni.catalog_version_id AS catalog_version_id FROM acts y JOIN "+institution("ni","y.note+0")+" ORDER BY ni.id");
        }
        /** NNA afectados con el tramo de edad del catalogo vigente en el instante del corte (el tramo no se recalcula despues). */
        Sql freezePersons(){
            requireFreeze();
            tables.put("pa",affected());
            return statement("SELECT pa.com AS communication_id,pa.region_code AS region_code,pa.commune AS commune,pa.age_years AS age_years,pa.nationality AS nationality,"
                +PersonDims.bracketSql("pa.age_years")+" AS age_bracket FROM pa ORDER BY pa.com");
        }
        Sql freezeCases(){
            requireFreeze();
            return statement("SELECT "+FIXED+"cc.id AS case_id,b.id AS communication_id,cc.created_ms AS created_ms FROM base b JOIN case_record cc ON cc.requirement_id=b.req+0 JOIN org_scope cs ON cs.id=cc.scope_id WHERE "
                +caseReach("cc.matter","cs")+" ORDER BY cc.id");
        }
        private void requireFreeze(){if(!freeze)throw new IllegalStateException("Sentencia de congelado fuera del congelado.");}
        private Sql statement(String select){
            var args=new ArrayList<Object>();
            String with=with(args,new LinkedHashMap<>());
            return new Sql(with+select,args.toArray());
        }

        /** Versiones de catalogo con que se registraron los valores de una dimension (TRAMO_EDAD usa siempre la vigente). */
        private String versions(Dim d){
            return switch(d.code()){
                case "VIA_INGRESO","TIPO_SOLICITUD","TIPO_REQUIRENTE","VIA_AGRUPACION" -> {
                    String v=d.code().equals("VIA_AGRUPACION")?"VIA_INGRESO":d.code();
                    key(v);version(v);
                    yield "SELECT DISTINCT b.v_"+v+" AS ver FROM pop b WHERE b.k_"+v+" NOT IN ("+SPECIAL_SQL+")";
                }
                case "TIPO_ACTUACION" -> {actions();yield "SELECT DISTINCT y.ver AS ver FROM acts y JOIN pop b ON b.id+0=y.com+0";}
                case "TIPO_INSTITUCION" -> {actions();yield "SELECT "+fixed(institutionIndex("ni"))+"DISTINCT ni.catalog_version_id AS ver FROM acts y JOIN pop b ON b.id+0=y.com+0 JOIN "+institution("ni","y.note+0");}
                default -> {
                    String v=d.catalog();
                    tables.putIfAbsent("ev_"+v,events(v));
                    yield "SELECT DISTINCT y.ver AS ver FROM ev_"+v+" y JOIN pop b ON b.id+0=y.com+0";
                }
            };
        }
    }
    /** Une en el orden escrito (la base primero): sin estadisticas de los CTE, otro orden multiplicaria filas. H2 lo ignora. */
    private static final String FIXED="/*+ JOIN_FIXED_ORDER() */ ";
    /**
     * Indice por COM de cada tabla congelada de un corte (cierre de E11): recien escrito el corte, InnoDB aun no recalcula
     * sus estadisticas (lo hace en segundo plano, una tabla cada unos 10 s) y, con la tabla tenida por vacia, el
     * optimizador entraba por el prefijo cut_id de la clave primaria y recorria todo el corte por cada COM (una
     * exportacion desde un corte recien generado pasaba de 2 s a mas de 30 s). La sugerencia INDEX equivale a FORCE INDEX
     * en MySQL y H2 la ignora como comentario; no cambia el resultado.
     */
    static final String CUT_EVENT="INDEX(e ix_stat_cut_event_com)",CUT_ACTION="INDEX(a ix_stat_cut_action_com)",CUT_CASE_INDEX="ix_stat_cut_case_com",
        CUT_INSTITUTION_INDEX="ix_stat_cut_institution_note";
    /** Un CTE con LIMIT se calcula una sola vez: MySQL no lo fusiona en las partes que lo usan. */
    private static final String ONCE=" LIMIT 9223372036854775807";
    private static String text(String column){return "CONCAT("+column+",'')";}
    private static String plain(Dim d,String column){return d.numeric()?column+"+0":text(column);}
    private static String code(String value){
        if(!value.matches("[A-Z0-9_]{1,40}"))throw new IllegalArgumentException("Codigo no valido.");
        return "'"+value+"'";
    }
}
