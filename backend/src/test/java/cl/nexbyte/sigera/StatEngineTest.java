package cl.nexbyte.sigera;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Motor estadistico (0.8.0, gate E08): poblacion principal de la Cuenta Publica 2025-2026 (poblacion.csv). A es un
 * Formulario con REQ reabierto y transferido de S06 a S07 y dos Casos; B, una COM nueva; C, duplicada; D, una primera
 * atencion cerrada; E, una SOL pendiente recibida al final del 30/06; F, invalida; G, antecedente; H, del periodo siguiente;
 * P1 y P2, de la Cuenta Publica anterior. Escenarios 1 (agregado), 2, 3 y 15 (agregado), alcance, conciliacion y limites.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_motor;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-motor-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatEngineTest extends StatFixture {
    static final String H06="cabeza.s06",P06="profesional.s06",H07="cabeza.s07",P07="profesional.s07";
    static final String INST="rep.inst",R06="rep.s06",R07="rep.s07",RUPD="rep.upd",RUPR="rep.upr";
    static final Map<String,Long> IDS=new HashMap<>();
    static long caseA1,caseA2,reqA,reqD;

    static Map<String,Object> cp(){return period("CUENTA_PUBLICA","2025-07-01");}
    static Map<String,Object> basis(String basis){var p=cp();p.put("date_basis",basis);return p;}
    long id(String letter){return IDS.get(letter);}

    @BeforeEach void data()throws Exception{
        if(!IDS.isEmpty())return;
        catalogs(CATALOGS);
        site("S06","06","ACTIVA");site("S07","07","ACTIVA");site("S05","05","PROYECTADA");
        staff(H06,"DIRECCION@S06","PROFESIONAL@S06");staff(P06,"PROFESIONAL@S06");staff(H07,"DIRECCION@S07","PROFESIONAL@S07");staff(P07,"PROFESIONAL@S07");
        staff(INST,"REPORTES");staff(R06,"REPORTES@S06");staff(R07,"REPORTES@S07");staff(RUPD,"REPORTES@UPD");staff(RUPR,"REPORTES@UPR");
        var population=population();
        for(String letter:population.keySet()){
            var p=population.get(letter);
            IDS.put(letter,create(p));
            registered(id(letter),p.get("registrada"));
        }
        // Constitucion de los REQ dentro del periodo, para conciliar con Reports.
        db.update("UPDATE requirement SET created_ms=? WHERE id=?",ms("2025-07-11T15:00:00Z"),reqA);
        db.update("UPDATE requirement SET created_ms=? WHERE id=?",ms("2026-01-14T15:30:00Z"),reqD);
    }
    long create(Map<String,String> p){
        var stat=p.get("tipo").isEmpty()?null:stat(p.get("tipo"),p.get("requirente"));
        String matter=p.get("materia").isEmpty()?null:p.get("materia"),region=p.get("region").isEmpty()?null:p.get("region"),received=p.get("recibida");
        switch(p.get("recorrido")){
            case "FORMULARIO_REABIERTO_TRANSFERIDO" -> {return reopenedAndTransferred(Instant.parse(received),region,matter,stat);}
            case "PRIMERA_ATENCION_CERRADA" -> {
                reqD=attention(P06,"S06",p.get("canal"),received,Map.of("region",region,"commune","Valparaíso"),"stat",stat,"resolve_now",true,"effective_at",ago(5),
                    "result","ORIENTACION","reason","Orientacion entregada","rights",Map.of("variables",Map.of("DERECHO",add("3.2"),"CONTEXTO",add("ESCOLAR"))));
                return comOf(reqD);
            }
            default -> {
                long com=com(P06,"S06",p.get("canal"),received,form(region),stat);
                switch(p.get("recorrido")){
                    case "SOL_DUPLICADA" -> {route(H06,com,"S06",matter);discard(H06,com,"DUPLICADA");}
                    case "SOL_INVALIDA" -> {route(H06,com,"S06",matter);discard(H06,com,"INVALIDA");}
                    case "SOL_PENDIENTE" -> route(H06,com,"S06",matter);
                    case "ANTECEDENTE" -> on(H06,"COM_LINK","communication",com,Map.of("folio",folio(reqA),"reason","Antecedente"));
                    default -> {}
                }
                return com;
            }
        }
    }
    /** A: derechos 3.1 y 3.2 en el REQ, 3.1 y 4.1 en un Caso de S06 y 3.1 en un Caso de S07; contextos en el REQ. */
    long reopenedAndTransferred(Instant received,String region,String matter,Map<String,Object> stat){
        long com=formulario("S06",received,region,"Talca",Map.of());
        route(H06,com,"S06",matter);
        reqA=assume(H06,com,P06,stat);
        rights(H06,"REQ",reqA,"DERECHO",add("3.1","3.2"));rights(H06,"REQ",reqA,"CONTEXTO",add("FAMILIAR","ESCOLAR"));
        on(H06,"REQ_CLOSE","requirement",reqA,Map.of("reason","Cierre sintetico"));
        run(H06,"REOPEN_REQUEST",reqA,null,Map.of("reason","Nueva gestion"));
        on(H06,"REOPEN_DECIDE","reopening",last("reopening"),Map.of("decision","ACEPTADA","reason","Procede"));
        caseA1=on(H06,"REQ_CASE_CREATE","requirement",reqA,Map.of("scope_id",scope("S06"),"responsible_id",user(P06),"objective","Seguimiento","matter","UPD")).id();
        rights(P06,"CAS",caseA1,"DERECHO",add("3.1","4.1"));
        on(H06,"FLOW_REQUEST","requirement",reqA,Map.of("kind","TRANSFERENCIA","target_scope",scope("S07"),"reason","Corresponde a S07"));
        on(H07,"FLOW_DECIDE","flow_request",last("flow_request"),Map.of("decision","ACEPTADA","reason","Aceptada"));
        caseA2=on(H07,"REQ_CASE_CREATE","requirement",reqA,Map.of("scope_id",scope("S07"),"responsible_id",user(P07),"objective","Seguimiento","matter","UPD")).id();
        rights(P07,"CAS",caseA2,"DERECHO",add("3.1"));
        return com;
    }

    // --- Escenario 1: Figura 1 sintetica ---------------------------------------------------------------------------
    void figure(String dimension,String expected){
        var r=query(INST,"INGRESOS",List.of(dimension),List.of(),cp());
        assertEquals(expected(expected),cells(r));
        assertEquals(5,grand(r));
        assertEquals(5,cells(r).values().stream().mapToLong(Long::longValue).sum(),"cada desglose suma el total");
    }
    @Test @Order(1) void figuraSolicitud(){figure("TIPO_SOLICITUD","solicitud");}
    @Test @Order(1) void figuraRequirente(){figure("TIPO_REQUIRENTE","requirente");}
    @Test @Order(1) void figuraVia(){figure("VIA_INGRESO","via");figure("VIA_AGRUPACION","via-agrupada");}
    @Test @Order(1) void figuraRegion(){figure("REGION_REQUIRENTE","region");}

    // --- Escenario 2: conteo D25 -------------------------------------------------------------------------------------
    @Test @Order(1) void descarteD25(){
        assertEquals(5,grand(query(INST,"INGRESOS",List.of(),List.of(),cp())));
        assertEquals(of("OFICIO",1),cells(query(INST,"EXCLUIDOS",List.of("CANAL"),List.of(),cp())));
        var states=cells(query(INST,"INGRESOS",List.of("ESTADO_COM"),List.of(),cp()));
        assertEquals(1,states.get("DUPLICADA"));assertNull(states.get("INVALIDA"));assertNull(states.get("VINCULADA"));
    }
    @Test @Order(1) void pendientesYDirectaD25(){
        assertEquals(of("NUEVA",1,"ENVIADA",2,"ATENDIDA",1,"DUPLICADA",1),cells(query(INST,"INGRESOS",List.of("ESTADO_COM"),List.of(),cp())));
        // B nueva, E con SOL pendiente y D de atencion directa: tres ingresos sin gestion previa exigida.
        assertEquals(1,grand(query(INST,"INGRESOS",List.of(),List.of(),cp(),"filters",Map.of("ESTADO_COM",List.of("NUEVA")))));
        assertEquals(1,grand(query(INST,"INGRESOS",List.of(),List.of(),cp(),"filters",Map.of("ESTADO_SOL",List.of("PENDIENTE")))));
        assertEquals(1,grand(query(INST,"INGRESOS",List.of(),List.of(),cp(),"filters",Map.of("ORIGEN_REQ",List.of("ATENCION_DIRECTA")))));
        assertEquals(of("SOL",1,"ATENCION_DIRECTA",1,"NO_APLICA",3),cells(query(INST,"INGRESOS",List.of("ORIGEN_REQ"),List.of(),cp())));
    }
    @Test @Order(1) void cadenaUnicaD25(){
        var onlyA=Map.of("CANAL",List.of("FORMULARIO_WEB_PUBLICO"));
        assertEquals(1,grand(query(INST,"INGRESOS",List.of(),List.of(),cp(),"filters",onlyA)));
        assertEquals(1,grand(query(INST,"REQ_CONSTITUIDOS",List.of(),List.of(),cp(),"filters",onlyA)));
        var cases=query(INST,"CASOS",List.of(),List.of(),basis("RECEPCION"),"filters",onlyA);
        assertEquals(2,grand(cases));assertEquals(1,grand(cases,"unique"));
        // Un solo ingreso en cada hito de la cadena: asumida, cerrada, reactivada, con Casos y transferida.
        for(var m:db.rows("SELECT DISTINCT at_ms FROM milestone WHERE communication_id=? AND event<>'COM_REGISTRADA'",id("A")))
            assertEquals(1,grand(query(INST,"INGRESOS",List.of(),List.of(),cp(),"filters",onlyA,"time_mode","FECHA","as_of",Instant.ofEpochMilli(n(m,"at_ms")).toString())));
    }

    // --- Escenario 3: fechas D26 --------------------------------------------------------------------------------------
    @Test @Order(1) void bordeCuentaPublica(){
        var last=cells(query(INST,"INGRESOS",List.of("PERIODO_DIA"),List.of(),cp()));
        assertEquals(1,last.get("2026-06-30"));
        assertEquals(5,grand(query(INST,"INGRESOS",List.of(),List.of(),cp())));
        var next=query(INST,"INGRESOS",List.of("PERIODO_DIA"),List.of(),period("CUENTA_PUBLICA","2026-07-01"));
        assertEquals(of("2026-07-01",1),cells(next));assertEquals(1,grand(next));
        assertEquals("2026-06-30",obj(obj(query(INST,"INGRESOS",List.of(),List.of(),period("CUENTA_PUBLICA","2026-01-15")).get("metadata")).get("period")).get("to"));
    }
    @Test @Order(1) void registroTardio(){
        var r=query(INST,"INGRESOS",List.of("TARDANZA"),List.of(),cp());
        assertEquals(of("SI",1,"NO",4),cells(r));
        assertEquals(Map.of("n",1L,"by_delay_days",List.of(Map.of("days",2L,"n",1L))),r.get("late_records"));
        assertNull(query(INST,"INGRESOS",List.of(),List.of(),basis("REGISTRO")).get("late_records"));
        // Registrada justo a la medianoche de Chile en que termina el periodo: tardia.
        long com=com(P06,"S06","CORREO","2023-05-31T15:00:00Z",form("06"),null);
        registered(com,"2023-06-01T04:00:00Z");
        var may=query(INST,"INGRESOS",List.of(),List.of(),period("MES","2023-05-01"));
        assertEquals(Map.of("n",1L,"by_delay_days",List.of(Map.of("days",1L,"n",1L))),may.get("late_records"));
        registered(com,"2023-06-01T03:59:59.999Z");
        assertEquals(Map.of("n",0L,"by_delay_days",List.of()),query(INST,"INGRESOS",List.of(),List.of(),period("MES","2023-05-01")).get("late_records"));
    }

    // --- Escenario 15: multivaluadas -----------------------------------------------------------------------------------
    @Test @Order(1) void mencionesDerecho(){
        var onlyA=Map.of("CANAL",List.of("FORMULARIO_WEB_PUBLICO"));
        var a=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),cp(),"filters",onlyA);
        assertEquals(of("3.1",1,"3.2",1,"4.1",1),cells(a));assertEquals(1,grand(a));assertEquals(3,grand(a,"mentions"));
        assertEquals(3,grand(query(INST,"MENCIONES",List.of("DERECHO"),List.of(),cp(),"filters",onlyA)));
        var assignments=query(INST,"CLASIFICACIONES_DERECHO",List.of("DERECHO"),List.of(),cp(),"filters",onlyA);
        assertEquals(of("3.1",3,"3.2",1,"4.1",1),cells(assignments));assertEquals(5,grand(assignments));assertEquals(1,grand(assignments,"unique"));
        var with=query(INST,"REQUERIMIENTOS_CON_DERECHO",List.of(),List.of(),cp());
        assertEquals(2,grand(with));
        var both=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),cp(),"filters",Map.of("DERECHO",List.of("3.1","3.2","4.1")));
        assertEquals(expected("derecho-ad"),cells(both));assertEquals(2,grand(both));assertEquals(4,grand(both,"mentions"));
    }
    @Test @Order(1) void porcentajeMultivalor(){
        var r=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),cp(),"filters",Map.of("DERECHO",List.of("3.1","3.2","4.1")),"percent","TOTAL");
        assertEquals(50,percent(r,List.of("3.1"),List.of()));assertEquals(100,percent(r,List.of("3.2"),List.of()));assertEquals(50,percent(r,List.of("4.1"),List.of()));
        for(var c:list(r.get("cells")))assertEquals(2L,c.get("denominator"));
        assertTrue(warnings(r).contains("PORCENTAJES_MULTIVALOR"));
    }
    @Test @Order(1) void cruceDosMultiples(){
        var filter=Map.of("DERECHO",List.of("3.1","3.2","4.1"));
        var r=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of("CONTEXTO"),cp(),"filters",filter,"percent","FILA");
        assertEquals(expected("derecho-contexto"),cells(r));
        assertEquals(of("3.1",1,"3.2",2,"4.1",1),totals(r,"rows","n"));assertEquals(of("FAMILIAR",1,"ESCOLAR",2),totals(r,"columns","n"));assertEquals(2,grand(r));
        assertEquals(100,percent(r,List.of("3.1"),List.of("ESCOLAR")));
        var column=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of("CONTEXTO"),cp(),"filters",filter,"percent","COLUMNA");
        assertEquals(50,percent(column,List.of("3.1"),List.of("ESCOLAR")));
        // Menciones de una de las dos: cada (COM, valor) una sola vez en los margenes.
        var mentions=query(INST,"MENCIONES",List.of("DERECHO"),List.of("CONTEXTO"),cp(),"filters",filter,"mention_dimension","DERECHO");
        assertEquals(of("3.1",1,"3.2",2,"4.1",1),totals(mentions,"rows","n"));assertEquals(4,grand(mentions));
    }
    @Test @Order(1) void cruceUnicaYMultiple(){
        var r=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of("CANAL"),cp(),"filters",Map.of("DERECHO",List.of("3.1","3.2","4.1")));
        assertEquals(expected("derecho-canal"),cells(r));
        assertEquals(of("TELEFONO",1,"FORMULARIO_WEB_PUBLICO",1),totals(r,"columns","n"));assertEquals(2,grand(r));
    }
    @Test @Order(1) void estadosYFiltros(){
        var all=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),cp());
        assertEquals(of("3.1",1,"3.2",2,"4.1",1,"PENDIENTE",3),cells(all));assertEquals(5,grand(all));
        assertTrue(warnings(all).contains("VARIABLES_PENDIENTES"));
        var filtered=query(INST,"INGRESOS",List.of("CANAL"),List.of(),cp(),"filters",Map.of("DERECHO",List.of("3.1","4.1"),"CANAL",List.of("FORMULARIO_WEB_PUBLICO","TELEFONO")));
        assertEquals(of("FORMULARIO_WEB_PUBLICO",1),cells(filtered));assertEquals(1,grand(filtered));
        assertEquals(3,grand(query(INST,"INGRESOS",List.of(),List.of(),cp(),"filters",Map.of("DERECHO",List.of("PENDIENTE")))));
    }

    // --- Series y comparaciones (D26) -------------------------------------------------------------------------------------
    void series(String axis,String expected){
        var r=query(INST,"INGRESOS",List.of("PERIODO_"+axis),List.of(),cp());
        assertEquals(expected(expected),cells(r,"n",true));assertEquals(5,grand(r));
        assertEquals(expected(expected).size(),list(obj(r.get("values")).get("PERIODO_"+axis)).size());
    }
    @Test @Order(1) void serieMes(){series("MES","serie-mes");}
    @Test @Order(1) void serieTrimestre(){series("TRIMESTRE","serie-trimestre");}
    @Test @Order(1) void serieSemestre(){series("SEMESTRE","serie-semestre");}
    @Test @Order(1) void serieAnio(){series("ANIO","serie-anio");}
    @Test @Order(1) void serieCuentaPublica(){series("CUENTA_PUBLICA","serie-cuenta-publica");}
    @Test @Order(1) void comparaciones(){
        var june=period("MES","2026-06-01");
        var previous=query(INST,"INGRESOS",List.of(),List.of(),june,"compare",Map.of("type","PERIODO_ANTERIOR"));
        assertEquals(StatEngine.delta(1,0),grandDelta(previous));assertNull(grandDelta(previous).get("delta_percent"));
        assertEquals("2026-05-01",obj(obj(previous.get("comparison")).get("period")).get("from"));
        var year=query(INST,"INGRESOS",List.of(),List.of(),june,"compare",Map.of("type","MISMO_PERIODO_ANIO_ANTERIOR"));
        assertEquals(1L,grandDelta(year).get("base_n"));assertEquals(0L,grandDelta(year).get("delta_n"));assertEquals(0d,grandDelta(year).get("delta_percent"));
        var account=query(INST,"INGRESOS",List.of("PERIODO_MES"),List.of(),cp(),"compare",Map.of("type","PERIODO_ANTERIOR"));
        assertEquals(2L,grandDelta(account).get("base_n"));assertEquals(3L,grandDelta(account).get("delta_n"));assertEquals(150d,grandDelta(account).get("delta_percent"));
        // Las categorias se alinean por la posicion del intervalo: julio de 2024 con julio de 2025, junio de 2025 con junio de 2026.
        var cells=list(obj(account.get("comparison")).get("cells"));
        assertEquals(12,cells.size());
        assertEquals(List.of("2025-07","2026-06"),cells.stream().filter(c->(Long)c.get("base_n")==1).map(c->keys(c.get("row")).getFirst()).toList());
        // Periodo personalizado: el anterior tiene los mismos dias.
        var custom=query(INST,"INGRESOS",List.of(),List.of(),days("2026-06-21","2026-06-30"),"compare",Map.of("type","PERIODO_ANTERIOR"));
        assertEquals(Map.of("type","PERSONALIZADO","from","2026-06-11","to","2026-06-20"),obj(custom.get("comparison")).get("period"));
    }
    static Map<String,Object> grandDelta(Map<String,Object> r){return obj(obj(obj(r.get("comparison")).get("totals")).get("grand"));}

    // --- Alcance (D43) ------------------------------------------------------------------------------------------------------
    @Test @Order(1) void alcanceSede(){
        assertEquals(4,grand(query(R06,"INGRESOS",List.of(),List.of(),cp())));
        assertEquals(1,grand(query(R07,"INGRESOS",List.of(),List.of(),cp())));
        assertEquals(0,grand(query(R06,"INGRESOS",List.of(),List.of(),cp(),"filters",Map.of("SEDE_VIGENTE",List.of("S07")))));
        assertEquals(0,grand(query(R06,"INGRESOS",List.of(),List.of(),cp(),"filters",Map.of("SEDE_INICIAL",List.of("S06"),"CANAL",List.of("FORMULARIO_WEB_PUBLICO")))));
        // Antes de la transferencia el principal era S06, pero el alcance es el vigente: A sigue fuera de S06.
        long decided=n(db.one("SELECT decided_ms FROM flow_request WHERE requirement_id=?",reqA),"decided_ms");
        var past=new Object[]{"time_mode","FECHA","as_of",Instant.ofEpochMilli(decided-1).toString(),"filters",Map.of("SEDE_VIGENTE",List.of("S06"),"CANAL",List.of("FORMULARIO_WEB_PUBLICO"))};
        assertEquals(1,grand(query(INST,"INGRESOS",List.of(),List.of(),cp(),past)));
        assertEquals(0,grand(query(R06,"INGRESOS",List.of(),List.of(),cp(),past)));
    }
    @Test @Order(1) void alcanceMateriaYCaso(){
        assertEquals(2,grand(query(RUPD,"INGRESOS",List.of(),List.of(),cp())));
        assertEquals(1,grand(query(RUPR,"INGRESOS",List.of(),List.of(),cp())));
        // 4.1 esta solo en un Caso de S06: no aporta a S07 y si al institucional y a la materia del Caso.
        assertEquals(of("3.1",1,"3.2",1),cells(query(R07,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),cp())));
        assertEquals(3,grand(query(R07,"CLASIFICACIONES_DERECHO",List.of(),List.of(),cp())));
        assertEquals(1,cells(query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),cp())).get("4.1"));
        assertEquals(1,cells(query(RUPD,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),cp())).get("4.1"));
        assertEquals(1,grand(query(R07,"CASOS",List.of(),List.of(),basis("RECEPCION"))));
    }

    // --- Conciliacion con Reports y con las tablas base -----------------------------------------------------------------------
    @Test @Order(1) void conciliaComPorCanal(){
        var com=obj(reports.report(access.actor(INST),new Reports.Request("VOLUMEN","2025-07-01","2026-06-30",null,null,null,null,null,null,null,null,null,null,null,null,null)).get("com"));
        assertEquals(6L,com.get("total"));
        var byReports=new TreeMap<String,Long>();
        for(var c:list(com.get("by_channel")))byReports.merge(s(c,"channel"),(Long)c.get("count"),Long::sum);
        var byEngine=new TreeMap<String,Long>();long[] totals=new long[3];int i=0;
        for(String measure:List.of("INGRESOS","EXCLUIDOS","ANTECEDENTES")){
            var r=query(INST,measure,List.of("CANAL"),List.of(),basis("REGISTRO"));
            cells(r).forEach((k,v)->byEngine.merge(k,v,Long::sum));totals[i++]=grand(r);
        }
        assertArrayEquals(new long[]{4,1,1},totals);
        assertEquals(byReports,byEngine);assertEquals(new TreeMap<>(expected("canal-administrativo")),byEngine);
        assertEquals(6,db.count("SELECT COUNT(*) FROM communication WHERE created_ms>=? AND created_ms<?",ms("2025-07-01T04:00:00Z"),ms("2026-07-01T04:00:00Z")));
        var common=query(INST,"INGRESOS",List.of(),List.of(),basis("REGISTRO"),"filters",Map.of("CANAL",List.of("PRESENCIAL","TELEFONO")));
        assertEquals(2,grand(common));assertEquals(2,byReports.get("PRESENCIAL")+byReports.get("TELEFONO"));
    }
    @Test @Order(1) void conciliaReqPorEstado(){
        var req=obj(reports.report(access.actor(INST),new Reports.Request("VOLUMEN","2025-07-01","2026-06-30",null,null,null,null,null,null,null,null,null,null,null,null,null)).get("req"));
        var byReports=new TreeMap<String,Long>();for(var x:list(req.get("by_state")))byReports.put(s(x,"value"),(Long)x.get("count"));
        var r=query(INST,"REQ_CONSTITUIDOS",List.of("ESTADO_REQ"),List.of(),cp());
        assertEquals(of("ABIERTO",1,"CERRADO",1),cells(r));assertEquals(byReports,new TreeMap<>(cells(r)));assertEquals(2,grand(r));
        assertEquals(2,db.count("SELECT COUNT(*) FROM requirement WHERE created_ms>=? AND created_ms<?",ms("2025-07-01T04:00:00Z"),ms("2026-07-01T04:00:00Z")));
        // La fecha es la de constitucion del REQ (11 de julio), no la de recepcion de su COM (10 de julio).
        assertEquals(1,grand(query(INST,"REQ_CONSTITUIDOS",List.of(),List.of(),days("2025-07-11","2025-07-11"))));
        assertEquals(0,grand(query(INST,"REQ_CONSTITUIDOS",List.of(),List.of(),days("2025-07-10","2025-07-10"))));
    }

    // --- Auditoria, contrato y limites --------------------------------------------------------------------------------------------
    @Test @Order(1) void auditoriaSoloNombres(){
        long before=db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_CONSULTA'");
        query(INST,"INGRESOS",List.of("CANAL"),List.of(),cp(),"filters",Map.of("REGION_REQUIRENTE",List.of("07")));
        assertEquals(before+1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_CONSULTA'"));
        var e=db.one("SELECT * FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action='STAT_CONSULTA')");
        assertEquals("Medida INGRESOS; modo VIGENTE; dimensiones: CANAL; filtros: REGION_REQUIRENTE",s(e,"note"));
        assertFalse(s(e,"note").matches(".*[0-9].*"),"sin valores, fechas ni cifras");
        assertEquals("SISTEMA",s(e,"resource_type"));
    }
    @Test @Order(1) void contratoCerrado(){
        long before=db.count("SELECT COUNT(*) FROM audit_event");
        var ok=Map.<String,Object>of("measure","INGRESOS","rows",List.of("CANAL"),"period",cp());
        var bad=new ArrayList<Map<String,Object>>();
        for(var change:List.<Map<String,Object>>of(Map.of("rol","TI"),Map.of("measure","NADA"),Map.of("rows",List.of("NADA")),Map.of("rows",List.of("CANAL","CANAL")),Map.of("rows","CANAL"),
            Map.of("columns",List.of("CANAL")),Map.of("filters",Map.of("NADA",List.of("x"))),Map.of("filters",Map.of("CANAL",List.of("NADA"))),Map.of("filters",Map.of("CANAL",List.of())),Map.of("filters",Map.of("CANAL","CORREO")),
            Map.of("filters",Map.of("PERIODO_MES",List.of("2025-07"))),Map.of("filters",Map.of("EDAD_NNA",List.of(200))),Map.of("filters",Map.of("SEDE_VIGENTE",List.of("s06"))),
            Map.of("period",Map.of("type","MES")),Map.of("period",Map.of("type","SIGLO","from","2025-07-01")),Map.of("period",Map.of("type","MES","from","2025-13-01")),Map.of("period",Map.of("type","MES","from","2025-07-01","to","2025-07-30")),
            Map.of("period",days("2025-07-02","2025-07-01")),Map.of("period",days("1999-07-02","2025-07-01")),Map.of("period",Map.of("type","MES","from","2025-07-01","zona","UTC")),Map.of("period",basis("CREACION_CASO")),
            Map.of("time_mode","AYER"),Map.of("time_mode","FECHA"),Map.of("time_mode","FECHA","as_of","2026-01-01"),Map.of("time_mode","FECHA","as_of","2099-01-01T00:00:00Z"),Map.of("as_of","2026-01-01T00:00:00Z"),Map.of("time_mode","FECHA","as_of","2025-01-01T00:00:00Z"),
            Map.of("cut_id",42),Map.of("compare",Map.of("type","CORTE","cut_id",42)),Map.of("compare",Map.of("type","OTRO")),Map.of("percent","TODO"),Map.of("include_proposals","si"),
            Map.of("mention_dimension","CANAL"),Map.of("measure","MENCIONES"),Map.of("measure","MENCIONES","rows",List.of("DERECHO","CONTEXTO")),Map.of("measure","REQUERIMIENTOS_CON_VALOR"),Map.of("rows",List.of("VARIABLE")),
            Map.of("measure","ACTUACIONES","mention_dimension","DERECHO"),Map.of("rows",List.of("TARDANZA"),"period",basis("REGISTRO")))){
            var body=new LinkedHashMap<String,Object>(ok);body.putAll(change);bad.add(body);
        }
        bad.add(Map.of());
        for(var body:bad)assertEquals(400,assertThrows(Problem.class,()->engine.query(access.actor(INST),body),String.valueOf(body)).status,String.valueOf(body));
        assertEquals(before,db.count("SELECT COUNT(*) FROM audit_event"),"una peticion invalida no consulta ni audita");
        engine.query(access.actor(INST),ok);
    }
    @Test @Order(1) void limiteDeCeldas(){
        long before=db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_CONSULTA'");
        var two=new StatEngine(db,reports,guard,2);var three=new StatEngine(db,reports,guard,3);
        var body=map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"period",cp());
        var p=assertThrows(Problem.class,()->two.query(access.actor(INST),body));
        assertEquals(409,p.status);assertTrue(p.getMessage().contains("2 celdas")&&p.getMessage().contains("acorta el periodo"),p.getMessage());
        assertEquals(409,assertThrows(Problem.class,()->three.query(access.actor(INST),map("measure","INGRESOS","rows",List.of("PERIODO_MES"),"period",cp()))).status,"tambien cuentan los intervalos vacios");
        assertEquals(409,assertThrows(Problem.class,()->three.query(access.actor(INST),map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"period",cp(),"compare",Map.of("type","PERIODO_ANTERIOR")))).status,"la comparacion suma");
        assertEquals(before,db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_CONSULTA'"),"sin resultado no hay consulta auditada");
        assertEquals(3,cells(three.query(access.actor(INST),body)).size());
        assertEquals(20000,obj(queries.catalog(access.actor(INST)).get("stats")).get("max_cells"));
    }
    @Test @Order(1) void consultasPorConjunto(){
        int[] counted={0};
        var jdbc=new JdbcTemplate(db.dataSource()){
            @Override public List<Map<String,Object>> queryForList(String sql,Object... args){counted[0]++;return super.queryForList(sql,args);}
        };
        var counting=new Db(jdbc);
        var local=new StatEngine(counting,new Reports(counting),guard,20000);
        var body=map("measure","REQUERIMIENTOS_CON_VALOR","rows",List.of("DERECHO"),"columns",List.of("REGION_NNA"),"period",period("MES","2022-03-01"),"percent","TOTAL","include_proposals",true,
            "filters",Map.of("TIPO_SOLICITUD",List.of("PENDIENTE")));
        for(int i=0;i<5;i++)com(P06,"S06","CORREO","2022-03-0"+(i+1)+"T15:00:00Z",form("06"),null);
        var few=local.query(access.actor(INST),body);int first=counted[0];
        assertEquals(5,grand(few));
        long model=n(db.one("SELECT MAX(id) AS id FROM communication"),"id");
        db.update("INSERT INTO communication(channel,received_ms,created_ms,created_by,origin_scope,subject,body,state) SELECT c.channel,c.received_ms,c.created_ms,c.created_by,c.origin_scope,c.subject,c.body,c.state FROM communication c"
            +" CROSS JOIN (SELECT id FROM stat_catalog_item ORDER BY id LIMIT 99) k WHERE c.id>?",model-5);
        counted[0]=0;
        var many=local.query(access.actor(INST),body);
        assertEquals(500,grand(many));
        assertEquals(first,counted[0],"las sentencias dependen de la forma de la consulta, no de la cantidad de COM");
        db.update("UPDATE communication SET received_ms=received_ms-100000000000 WHERE id>?",model);
    }

    // --- Escenario 8: actuaciones, instituciones y reserva ---------------------------------------------------------------------------
    @Test @Order(50) void actuacionesVigentes(){
        var institutions=List.of(Map.of("name","Fiscalia sintetica","type_code","ORGANISMO_PUBLICO"),Map.of("name","Tribunal sintetico","type_code","ORGANISMO_PUBLICO"));
        on(P06,"CASE_NOTE","case_record",caseA1,map("body","Denuncia 1","effective_at",ago(60),"action_type","DENUNCIA_PENAL","institutions",institutions));
        on(P06,"CASE_NOTE","case_record",caseA1,map("body","Oficio mal tipificado","effective_at",ago(60),"action_type","OFICIO"));
        run(P06,"NOTE_RECTIFY",last("management_note"),null,map("body","Denuncia 2","effective_at",ago(60),"action_type","DENUNCIA_PENAL","reason","Era una denuncia"));
        on(P06,"CASE_NOTE","case_record",caseA1,map("body","Reservada","effective_at",ago(60),"action_type","MEDIDA_PROTECCION","reserved",true));
        on(P06,"CASE_NOTE","case_record",caseA1,map("body","Sin tipificar","effective_at",ago(60)));
        on(P06,"TASK_CREATE","case_record",caseA1,Map.of("executor_id",user(P06),"title","Presentar querella","action_type","QUERELLA"));
        on(P06,"TASK_COMPLETE","task_item",last("task_item"),Map.of("reason","Presentada"));
        var r=query(INST,"ACTUACIONES",List.of("TIPO_ACTUACION"),List.of(),cp());
        assertEquals(of("ORIENTACION",1,"DENUNCIA_PENAL",2),cells(r));assertEquals(3,grand(r));assertEquals(2,grand(r,"unique"));
        assertEquals(of("ORIENTACION",1,"DENUNCIA_PENAL",1),cells(query(INST,"REQUERIMIENTOS_CON_ACTUACION",List.of("TIPO_ACTUACION"),List.of(),cp())));
        assertEquals(0,grand(query(INST,"ACTUACIONES",List.of(),List.of(),cp(),"filters",Map.of("TIPO_ACTUACION",List.of("QUERELLA","OFICIO","MEDIDA_PROTECCION")))));
        assertEquals(of("ORIENTACION|PRIMERA_ATENCION",1,"DENUNCIA_PENAL|GESTION_CASO",2),cells(query(INST,"ACTUACIONES",List.of("TIPO_ACTUACION"),List.of("ORIGEN_ACTUACION"),cp())));
        // Instituciones de las actuaciones: dos menciones del mismo tipo en un requerimiento.
        var type=query(INST,"REQUERIMIENTOS_CON_VALOR",List.of("TIPO_INSTITUCION"),List.of(),cp());
        assertEquals(1L,cell(type,List.of("ORGANISMO_PUBLICO"),List.of()).get("n"));assertEquals(2L,cell(type,List.of("ORGANISMO_PUBLICO"),List.of()).get("mentions"));
        assertEquals("Tipo de institución de las actuaciones",StatSource.DIMS.get("TIPO_INSTITUCION").label());
        assertEquals("Denuncia penal",list(obj(r.get("values")).get("TIPO_ACTUACION")).stream().filter(v->v.get("key").equals("DENUNCIA_PENAL")).findFirst().orElseThrow().get("label"));
        // Las constancias del Caso de S06 no aportan a S07.
        assertEquals(0,grand(query(R07,"ACTUACIONES",List.of(),List.of(),cp())));
    }
    @Test @Order(60) void antecedenteD25(){
        assertEquals(5,grand(query(INST,"INGRESOS",List.of(),List.of(),cp())));assertEquals(1,grand(query(INST,"ANTECEDENTES",List.of(),List.of(),cp())));
        long link=n(db.one("SELECT id FROM communication_link WHERE communication_id=?",id("G")),"id");
        on(H07,"LINK_DECIDE","communication_link",link,Map.of("decision","RECHAZADA","reason","Otro asunto"));
        assertEquals(5,grand(query(INST,"INGRESOS",List.of(),List.of(),cp())));assertEquals(1,grand(query(INST,"ANTECEDENTES",List.of(),List.of(),cp())));
        on(H07,"LINK_RETURN","communication_link",link,Map.of("reason","Reclasificar"));
        assertEquals(6,grand(query(INST,"INGRESOS",List.of(),List.of(),cp())));assertEquals(0,grand(query(INST,"ANTECEDENTES",List.of(),List.of(),cp())));
    }

    /** En MySQL, ninguna sentencia del motor usa claves automaticas sobre un CTE (defecto 1112 de MySQL 8.4): se comprueba con EXPLAIN. */
    StatEngine explained(){
        if(!System.getProperty("siger.mysql.suite","false").equals("true"))return engine;
        var jdbc=new JdbcTemplate(db.dataSource()){
            @Override public List<Map<String,Object>> queryForList(String sql,Object... args){
                if(sql.startsWith("WITH pre"))for(var row:super.queryForList("EXPLAIN "+sql,args))assertFalse(String.valueOf(row.get("key")).startsWith("<auto_key"),()->"clave automatica en "+row+": "+sql.substring(sql.length()-400));
                return super.queryForList(sql,args);
            }
        };
        var explaining=new Db(jdbc);
        return new StatEngine(explaining,new Reports(explaining),guard,20000);
    }
    Map<String,Object> sweep(StatEngine engine,String measure,List<String> rows,List<String> columns,Map<String,Object> period,Object... extra){
        var body=map("measure",measure,"rows",rows,"columns",columns,"period",period);
        for(int i=0;i<extra.length;i+=2)body.put((String)extra[i],extra[i+1]);
        return engine.query(access.actor(INST),body);
    }
    /** Filtrar por un valor da lo mismo que su fila, en toda medida y dimension (tambien con un solo valor, que MySQL resuelve por clave). */
    @Test @Order(70) void barridoDeFiltros(){
        var checked=explained();
        for(var m:StatSource.MEASURES.values())for(var d:StatSource.DIMS.values()){
            if(d.kind()==StatSource.Kind.PERIODO||d.only()&&d.unit()!=m.unit()||d.code().equals("TARDANZA"))continue;
            if(m.code().equals("REQUERIMIENTOS_CON_VALOR")||m.mentions())continue;
            for(var t:list(obj(query(INST,m.code(),List.of(d.code()),List.of(),basis("RECEPCION")).get("totals")).get("rows"))){
                String key=keys(t.get("key")).getFirst(),at=m.code()+" "+d.code()+"="+key;
                Object value=d.kind()==StatSource.Kind.EDAD&&key.matches("[0-9]+")?(Object)Integer.valueOf(key):key;
                Map<String,Object> r;
                try{r=sweep(checked,m.code(),List.of(),List.of(),basis("RECEPCION"),"filters",Map.of(d.code(),List.of(value)));}
                catch(RuntimeException e){throw new AssertionError(at+": "+String.valueOf(e.getCause()),e);}
                assertEquals(t.get("unique"),obj(obj(r.get("totals")).get("grand")).get("unique"),at);
                if(!m.own()||d.unit()==m.unit()||!d.multiple())assertEquals(t.get("n"),obj(obj(r.get("totals")).get("grand")).get("n"),at);
            }
        }
    }
    /** Cruces de dos dimensiones multiples: los margenes son los de cada dimension por si sola y el total es el unico. */
    @Test @Order(70) void barridoDeCruces(){
        var checked=explained();
        var multiple=StatSource.DIMS.values().stream().filter(StatSource.Dim::multiple).toList();
        for(var x:multiple){
            var alone=sweep(checked,"REQUERIMIENTOS_CON_VALOR",List.of(x.code()),List.of(),cp());
            for(var y:multiple)if(!x.equals(y)){
                var r=sweep(checked,"REQUERIMIENTOS_CON_VALOR",List.of(x.code()),List.of(y.code()),cp(),"mention_dimension",x.code(),"compare",Map.of("type","PERIODO_ANTERIOR"));
                String at=x.code()+" x "+y.code();
                assertEquals(totals(alone,"rows","n"),totals(r,"rows","n"),at);assertEquals(totals(alone,"rows","mentions"),totals(r,"rows","mentions"),at);
                assertEquals(grand(alone),grand(r),at);assertEquals(grand(alone,"mentions"),grand(r,"mentions"),at);
            }
        }
    }
    // --- E10: un corte reproduce exactamente la consulta vigente del mismo instante ------------------------------------------
    /** La misma consulta sobre la fuente vigente (alcance de la cuenta) y sobre el corte; respuestas completas iguales salvo corte e instante. */
    void same(StatEngine checked,String who,long cut,String measure,List<String> rows,List<String> columns,Map<String,Object> period,Object... extra){
        var body=map("measure",measure,"rows",rows,"columns",columns,"period",period);
        for(int i=0;i<extra.length;i+=2)body.put((String)extra[i],extra[i+1]);
        var frozen=new LinkedHashMap<>(body);frozen.put("cut_id",cut);
        String at=who+" "+measure+" "+rows+columns+" "+period+" "+Arrays.toString(extra);
        Map<String,Object> live,stored;
        try{live=checked.query(access.actor(who),body);stored=checked.query(access.actor(who),frozen);}
        catch(RuntimeException e){throw new AssertionError(at+": "+e.getMessage()+" "+String.valueOf(e.getCause()),e);}
        assertEquals(cut,((Number)obj(stored.get("metadata")).get("cut_id")).longValue(),at);
        assertEquals(comparable(live),comparable(stored),at);
    }
    /**
     * Barrido de equivalencia (GO de E10): todas las medidas por cada dimension (con columnas, porcentaje y propuestas),
     * cruces de dos multiples, filtros de cada dimension, fechas de analisis y ejes de periodo, para un corte institucional
     * y, con su propio alcance, para uno de sede y uno de materia (la consulta vigente con el alcance que el corte guarda).
     */
    @Test @Order(80) void corteReproduceLaConsultaVigente(){
        String gi=staff("gest.inst","REPORTES","GESTION_ESTADISTICA"),g6=staff("gest.s06","REPORTES@S06","GESTION_ESTADISTICA@S06"),gu=staff("gest.upd","REPORTES@UPD","GESTION_ESTADISTICA@UPD");
        long inst=cut(gi,null,cp()),s06=cut(g6,"S06",cp()),upd=cut(gu,"UPD",cp());
        // El corte institucional guarda la poblacion de la Cuenta Publica (computables, excluidas y antecedentes) y sus relaciones.
        assertEquals(grand(query(INST,"INGRESOS",List.of(),List.of(),cp()))+grand(query(INST,"EXCLUIDOS",List.of(),List.of(),cp()))+grand(query(INST,"ANTECEDENTES",List.of(),List.of(),cp())),
            db.count("SELECT COUNT(*) FROM stat_cut_row WHERE cut_id=? AND received_ms>=? AND received_ms<?",inst,ms("2025-07-01T04:00:00Z"),ms("2026-07-01T04:00:00Z")));
        for(String t:List.of("stat_cut_event","stat_cut_action","stat_cut_action_institution","stat_cut_person","stat_cut_case"))
            assertTrue(db.count("SELECT COUNT(*) FROM "+t+" WHERE cut_id=?",inst)>0,t);
        assertTrue(db.count("SELECT COUNT(*) FROM stat_cut_row WHERE cut_id=?",s06)<db.count("SELECT COUNT(*) FROM stat_cut_row WHERE cut_id=?",inst),"el corte de sede guarda solo su alcance");
        var checked=explained();
        for(var m:StatSource.MEASURES.values())for(var d:StatSource.DIMS.values()){
            if(d.only()&&d.unit()!=m.unit()||m.code().equals("REQUERIMIENTOS_CON_VALOR")&&!d.multiple())continue;
            if(d.code().equals("TARDANZA")&&(m.unit()==StatSource.Unit.CASO||m.code().equals("REQ_CONSTITUIDOS")))continue;
            var extra=new ArrayList<Object>(List.of("percent","FILA","include_proposals",true));
            if(m.mentions()&&!d.multiple())extra.addAll(List.of("mention_dimension","DERECHO"));
            same(checked,INST,inst,m.code(),List.of(d.code()),d.code().equals("CANAL")?List.of():List.of("CANAL"),cp(),extra.toArray());
        }
        var multiple=StatSource.DIMS.values().stream().filter(StatSource.Dim::multiple).toList();
        for(var x:multiple)for(var y:multiple)if(!x.equals(y))same(checked,INST,inst,"REQUERIMIENTOS_CON_VALOR",List.of(x.code()),List.of(y.code()),cp(),"mention_dimension",x.code(),"percent","COLUMNA");
        for(var d:StatSource.DIMS.values()){
            if(d.kind()==StatSource.Kind.PERIODO||d.only())continue;
            for(var t:list(obj(query(INST,"INGRESOS",List.of(d.code()),List.of(),cp()).get("totals")).get("rows"))){
                String key=keys(t.get("key")).getFirst();
                Object value=d.kind()==StatSource.Kind.EDAD&&key.matches("[0-9]+")?(Object)Integer.valueOf(key):key;
                same(checked,INST,inst,"INGRESOS",List.of("TIPO_SOLICITUD"),List.of(),cp(),"filters",Map.of(d.code(),List.of(value)));
                same(checked,INST,inst,"ACTUACIONES",List.of("TIPO_ACTUACION"),List.of(),cp(),"filters",Map.of(d.code(),List.of(value)));
            }
        }
        for(String basis:List.of("RECEPCION","REGISTRO","CONSTITUCION_REQ"))for(String axis:List.of("PERIODO_DIA","PERIODO_MES","PERIODO_TRIMESTRE","PERIODO_SEMESTRE","PERIODO_ANIO","PERIODO_CUENTA_PUBLICA"))
            same(checked,INST,inst,"INGRESOS",List.of(axis),List.of("TARDANZA").subList(0,basis.equals("RECEPCION")?1:0),basis(basis));
        same(checked,INST,inst,"CASOS",List.of("PERIODO_MES"),List.of("SEDE_VIGENTE"),basis("CREACION_CASO"));
        same(checked,INST,inst,"REQUERIMIENTOS_CON_VALOR",List.of("DERECHO"),List.of(),period("MES","2025-07-01"),"percent","TOTAL");
        // Sede y materia: la consulta vigente con el alcance de la cuenta, que es el del corte.
        for(var c:List.of(Map.entry(R06,s06),Map.entry(RUPD,upd))){
            for(var m:StatSource.MEASURES.values()){
                if(m.code().equals("REQUERIMIENTOS_CON_VALOR")||m.mentions())same(checked,c.getKey(),c.getValue(),m.code(),List.of("DERECHO"),List.of(),cp());
                else same(checked,c.getKey(),c.getValue(),m.code(),List.of(),List.of(),cp(),"include_proposals",true);
            }
            for(var d:StatSource.DIMS.values()){
                if(d.only()||d.code().equals("TARDANZA"))continue;
                same(checked,c.getKey(),c.getValue(),"INGRESOS",List.of(d.code()),List.of(),cp(),"percent","TOTAL");
            }
        }
        // Comparada con su corte del mismo instante, la consulta vigente no tiene diferencias.
        var r=checked.query(access.actor(INST),map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"columns",List.of("CANAL"),"period",cp(),"compare",Map.of("type","CORTE","cut_id",inst)));
        var comparison=obj(r.get("comparison"));
        assertEquals("CORTE",comparison.get("type"));assertEquals(inst,((Number)comparison.get("cut_id")).longValue());
        assertTrue(list(comparison.get("cells")).stream().allMatch(c->((Number)c.get("delta_n")).longValue()==0),String.valueOf(comparison));
        assertEquals(0L,((Number)obj(obj(comparison.get("totals")).get("grand")).get("delta_n")).longValue());
    }

    /** Toda dimension, medida y modo se consulta sin error (SQL portable en H2 y MySQL) y ningun requerimiento queda fuera. */
    @Test @Order(70) void barridoDeMedidasDimensionesYModos(){
        var checked=explained();
        for(String mode:List.of("VIGENTE","AL_INGRESO","AL_REQ","FECHA"))for(var m:StatSource.MEASURES.values())for(var d:StatSource.DIMS.values()){
            if(d.only()&&d.unit()!=m.unit()||m.code().equals("REQUERIMIENTOS_CON_VALOR")&&!d.multiple())continue;
            if(d.code().equals("TARDANZA")&&(m.unit()==StatSource.Unit.CASO||m.code().equals("REQ_CONSTITUIDOS")))continue;
            var extra=new ArrayList<Object>(List.of("time_mode",mode,"percent","FILA","include_proposals",true));
            if(mode.equals("FECHA"))extra.addAll(List.of("as_of",Instant.now().toString()));
            if(m.mentions()&&!d.multiple())extra.addAll(List.of("mention_dimension","DERECHO"));
            String at=m.code()+" "+d.code()+" "+mode;
            Map<String,Object> r;
            try{r=sweep(checked,m.code(),List.of(d.code()),d.code().equals("CANAL")?List.of():List.of("CANAL"),cp(),extra.toArray());}
            catch(RuntimeException e){throw new AssertionError(at+": "+String.valueOf(e.getCause()),e);}
            for(var v:list(obj(r.get("values")).get(d.code())))assertFalse(s(v,"label").isEmpty(),at);
            long rows=list(obj(r.get("totals")).get("rows")).stream().mapToLong(x->((Number)x.get("unique")).longValue()).sum();
            assertTrue(rows>=grand(r,"unique"),at);
            if(!d.multiple()&&d.unit()==null&&d.kind()!=StatSource.Kind.PERIODO)assertEquals(grand(r,"unique"),rows,at);
        }
    }
}
