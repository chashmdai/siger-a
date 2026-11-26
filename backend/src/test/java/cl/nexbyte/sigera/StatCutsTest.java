package cl.nexbyte.sigera;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cortes estadisticos (0.8.0, D39 y D43, gate E10): escenario 9 (una correccion posterior no cambia el corte y la
 * comparacion la muestra), huellas canonicas y verificacion de integridad ante alteraciones por SQL en la fila y en cada
 * relacion congelada, inmutabilidad (analisis estatico y barrido dinamico) y anulacion, facultad GESTION_ESTADISTICA y
 * cobertura del alcance (incluida la lectura por materia), lectura solo del propio corte, reserva, lista cerrada de
 * columnas (information_schema) y guardas de StatGuard dentro del comando (cupo sin espera y plazo con rollback).
 * Corre en H2 y, con siger.mysql.suite=true, en MySQL 8.4 (EXPLAIN de las sentencias de congelado).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_cortes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-cortes-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatCutsTest extends StatFixture {
    static final String H06="cabeza.c06",P06="profesional.c06",H07="cabeza.c07",P07="profesional.c07";
    static final String GI="gest.c.inst",G6="gest.c.s06",GX="gest.c.mixta",RI="rep.c.inst",R6="rep.c.s06",RU="rep.c.upd",TI="ti";
    static final boolean MYSQL=MySqlSuite.enabled();
    static final Set<String> FROZEN_TABLES=Set.of("stat_cut_row","stat_cut_event","stat_cut_action","stat_cut_action_institution","stat_cut_person","stat_cut_case");
    static long c1,c2,c3,r1,case1,reserved,rectified,original;
    @Autowired PlatformTransactionManager transactions;
    @Autowired StatCuts cuts;

    static Map<String,Object> march(){return period("MES","2026-03-01");}

    @BeforeEach void data()throws Exception{
        if(c1!=0)return;
        catalogs(CATALOGS);
        site("C06","06","ACTIVA");site("C07","07","ACTIVA");
        staff(H06,"DIRECCION@C06","PROFESIONAL@C06");staff(P06,"PROFESIONAL@C06");staff(H07,"DIRECCION@C07","PROFESIONAL@C07");staff(P07,"PROFESIONAL@C07");
        staff(GI,"REPORTES","GESTION_ESTADISTICA");staff(G6,"REPORTES@C06","GESTION_ESTADISTICA@C06");staff(GX,"REPORTES","GESTION_ESTADISTICA@C06");
        staff(RI,"REPORTES");staff(R6,"REPORTES@C06");staff(RU,"REPORTES@UPD");
        var nna=Map.<String,Object>of("presenter",Map.of("first_name","Persona","region_code","06","commune",AdultIdentity.COMMUNES.get("06").getFirst()),
            "persons",List.of(Map.of("role","NNA","first_name","Una","age_years","8","region_code","06","commune",AdultIdentity.COMMUNES.get("06").getFirst()),Map.of("role","NNA","first_name","Dos","age_years","15")));
        c1=com(P06,"C06","CORREO","2026-03-05T15:00:00Z",nna,stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA"));
        route(H06,c1,"C06",null);
        r1=assume(H06,c1,P06,null);
        rights(H06,"REQ",r1,"DERECHO",add("3.1"));
        on(H06,"REQ_NOTE","requirement",r1,map("effective_at",ago(60),"body","Orientacion sintetica","no_followup",true,"action_type","ORIENTACION",
            "institutions",List.of(Map.of("name","Liceo sintetico","type_code","ESTABLECIMIENTO_EDUCATIVO"))));
        on(H06,"REQ_NOTE","requirement",r1,map("effective_at",ago(60),"body","Querella reservada sintetica","no_followup",true,"action_type","QUERELLA","reserved",true,
            "institutions",List.of(Map.of("name","Juzgado reservado","type_code","ORGANISMO_PUBLICO"))));
        reserved=last("management_note");
        on(H06,"REQ_NOTE","requirement",r1,map("effective_at",ago(60),"body","Oficio mal tipificado","no_followup",true,"action_type","OFICIO"));
        original=last("management_note");
        run(H06,"NOTE_RECTIFY",original,null,map("effective_at",ago(60),"body","Era una denuncia","action_type","DENUNCIA_PENAL","reason","Correccion"));
        rectified=last("management_note");
        case1=on(H06,"REQ_CASE_CREATE","requirement",r1,Map.of("scope_id",scope("C06"),"responsible_id",user(P06),"objective","Seguimiento","matter","UPD")).id();
        rights(P06,"CAS",case1,"DERECHO",add("4.1"));
        c2=com(P06,"C06","TELEFONO","2026-03-10T15:00:00Z",form("06"),stat("VULNERACION","NNA"));
        c3=com(P07,"C07","CORREO","2026-03-20T15:00:00Z",form("07"),stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA"));
        route(H07,c3,"C07",null);
    }
    Map<String,Object> byType(String who,Long cut){
        var body=map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"columns",List.of("CANAL"),"period",march(),"include_proposals",true);
        if(cut!=null)body.put("cut_id",cut);
        return engine.query(access.actor(who),body);
    }
    Map<String,Object> verify(String who,long cut){return cuts.verify(access.actor(who),cut);}
    long stamp(String table,long cut){return db.count("SELECT COUNT(*) FROM "+table+" WHERE cut_id=?",cut);}

    // --- Escenario 9 ---------------------------------------------------------------------------------------------------
    @Test @Order(1) void escenario9CorreccionPosteriorNoCambiaElCorteYLaComparacionLaMuestra(){
        long cut=cut(GI,null,march());
        var header=db.one("SELECT * FROM stat_cut WHERE id=?",cut);
        assertTrue(s(header,"code").matches("CORTE-\\d{4}-\\d{3}"),s(header,"code"));
        assertEquals("VIGENTE",s(header,"state"));assertEquals(3,n(header,"row_count"));
        var before=byType(GI,cut);
        assertEquals(of("CONSULTA_ORIENTACION|CORREO",2,"VULNERACION|TELEFONO",1),cells(before));
        // Correcciones posteriores: tipo de solicitud de c1, un derecho mas en el REQ y un ingreso nuevo del periodo.
        run(H06,"STAT_CLASSIFY",c1,db.count("SELECT COUNT(*) FROM stat_event WHERE communication_id=?",c1),map("stat",map(TS,code("VULNERACION"),"reason","Correccion sintetica")));
        rights(H06,"REQ",r1,"DERECHO",add("3.2"));
        com(P06,"C06","CORREO","2026-03-25T15:00:00Z",form("06"),stat("PROMOCION","PERSONA_NATURAL_ADULTA"));
        var after=byType(GI,cut);
        assertEquals(comparable(before),comparable(after),"el corte no cambia");
        assertEquals(before.get("cells"),after.get("cells"));
        assertEquals(of("CONSULTA_ORIENTACION|CORREO",1,"VULNERACION|CORREO",1,"VULNERACION|TELEFONO",1,"PROMOCION|CORREO",1),cells(byType(GI,null)));
        // La comparacion vigente / corte muestra la correccion celda por celda y en los margenes.
        var r=engine.query(access.actor(GI),map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"columns",List.of("CANAL"),"period",march(),"compare",Map.of("type","CORTE","cut_id",cut)));
        var cmp=obj(r.get("comparison"));
        var deltas=new TreeMap<String,Long>();
        for(var c:list(cmp.get("cells")))if(((Number)c.get("delta_n")).longValue()!=0)deltas.put(keys(c.get("row")).getFirst()+"|"+keys(c.get("column")).getFirst(),((Number)c.get("delta_n")).longValue());
        assertEquals(new TreeMap<>(of("CONSULTA_ORIENTACION|CORREO",-1,"VULNERACION|CORREO",1,"PROMOCION|CORREO",1)),deltas);
        assertEquals(1L,((Number)obj(obj(cmp.get("totals")).get("grand")).get("delta_n")).longValue());
        assertEquals(obj(obj(r.get("metadata")).get("period")).get("from"),obj(cmp.get("period")).get("from"));
        // Derechos: el corte conserva 3.1 y 4.1; la fuente vigente agrega 3.2.
        var rights=map("measure","REQUERIMIENTOS_CON_VALOR","rows",List.of("DERECHO"),"period",march());
        var frozen=new LinkedHashMap<>(rights);frozen.put("cut_id",cut);
        var kept=cells(engine.query(access.actor(GI),frozen));
        assertEquals(1L,kept.get("3.1"));assertEquals(1L,kept.get("4.1"));assertFalse(kept.containsKey("3.2"));
        assertTrue(cells(engine.query(access.actor(GI),rights)).containsKey("3.2"));
        // Corte contra corte: un segundo corte muestra la misma correccion respecto del primero.
        long second=cut(GI,null,march());
        var cc=obj(engine.query(access.actor(GI),map("measure","INGRESOS","rows",List.of("TIPO_SOLICITUD"),"period",march(),"cut_id",second,"compare",Map.of("type","CORTE","cut_id",cut))).get("comparison"));
        assertEquals(1L,((Number)obj(obj(cc.get("totals")).get("grand")).get("delta_n")).longValue());
        assertTrue((boolean)verify(GI,cut).get("ok"));
        assertEquals(s(header,"rows_sha256"),s(db.one("SELECT rows_sha256 FROM stat_cut WHERE id=?",cut),"rows_sha256"));
    }

    // --- Huellas y verificacion --------------------------------------------------------------------------------------------
    @Test @Order(2) void huellaCanonicaEstableYVerificacionQueDetectaAlteraciones(){
        long a=cut(GI,null,march()),b=cut(GI,null,march());
        var ha=db.one("SELECT rows_sha256,metadata_sha256 FROM stat_cut WHERE id=?",a);var hb=db.one("SELECT rows_sha256,metadata_sha256 FROM stat_cut WHERE id=?",b);
        assertEquals(s(ha,"rows_sha256"),s(hb,"rows_sha256"),"sin cambios entre ellos, las mismas filas");
        assertNotEquals(s(ha,"metadata_sha256"),s(hb,"metadata_sha256"),"otro codigo e instante");
        for(var t:FROZEN_TABLES)assertTrue(stamp(t,a)>0,t);
        var ok=verify(RI,a);
        assertEquals(true,ok.get("ok"));assertEquals(true,ok.get("rows_ok"));assertEquals(true,ok.get("metadata_ok"));
        // Una alteracion por SQL en la fila y en cada relacion congelada se detecta; al restaurarla, la verificacion vuelve a pasar.
        var changes=new LinkedHashMap<String,String[]>();
        changes.put("stat_cut_row",new String[]{"d_canal","communication_id"});
        changes.put("stat_cut_event",new String[]{"code","event_id"});
        changes.put("stat_cut_action",new String[]{"action_type","unit_id"});
        changes.put("stat_cut_action_institution",new String[]{"type_code","institution_id"});
        changes.put("stat_cut_person",new String[]{"commune","ordinal"});
        changes.put("stat_cut_case",new String[]{"created_ms","case_id"});
        for(var e:changes.entrySet()){
            String table=e.getKey(),column=e.getValue()[0],key=e.getValue()[1];
            var row=db.rows("SELECT "+key+" AS k,"+column+" AS v FROM "+table+" WHERE cut_id=? ORDER BY "+key,a).getFirst();
            Object altered=row.get("v") instanceof Number x?(Object)(x.longValue()+1):"OTRO_VALOR";
            db.update("UPDATE "+table+" SET "+column+"=? WHERE cut_id=? AND "+key+"=?",altered,a,row.get("k"));
            var bad=verify(RI,a);
            assertEquals(false,bad.get("rows_ok"),table);assertEquals(false,bad.get("ok"),table);assertEquals(true,bad.get("metadata_ok"),table);
            db.update("UPDATE "+table+" SET "+column+"=? WHERE cut_id=? AND "+key+"=?",row.get("v"),a,row.get("k"));
            assertEquals(true,verify(RI,a).get("ok"),table);
        }
        // Una fila quitada o agregada tambien.
        var gone=db.rows("SELECT * FROM stat_cut_case WHERE cut_id=?",a).getFirst();
        db.update("DELETE FROM stat_cut_case WHERE cut_id=? AND case_id=?",a,gone.get("case_id"));
        assertEquals(false,verify(RI,a).get("rows_ok"));
        db.update("INSERT INTO stat_cut_case(cut_id,case_id,communication_id,created_ms) VALUES(?,?,?,?)",a,gone.get("case_id"),gone.get("communication_id"),gone.get("created_ms"));
        // La cabecera: los metadatos incluyen la huella de las filas.
        String title=s(db.one("SELECT title FROM stat_cut WHERE id=?",a),"title");
        db.update("UPDATE stat_cut SET title='Otro titulo' WHERE id=?",a);
        assertEquals(false,verify(RI,a).get("metadata_ok"));
        db.update("UPDATE stat_cut SET title=? WHERE id=?",title,a);
        String rows=s(db.one("SELECT rows_sha256 FROM stat_cut WHERE id=?",a),"rows_sha256");
        db.update("UPDATE stat_cut SET rows_sha256=? WHERE id=?","0".repeat(64),a);
        var both=verify(RI,a);
        assertEquals(false,both.get("rows_ok"));assertEquals(false,both.get("metadata_ok"));
        db.update("UPDATE stat_cut SET rows_sha256=? WHERE id=?",rows,a);
        assertEquals(true,verify(RI,a).get("ok"));
        var audit=db.one("SELECT note,resource_type FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action='CORTE_VERIFICADO')");
        assertTrue(s(audit,"note").matches("Corte CORTE-\\d{4}-\\d{3}; integro"),s(audit,"note"));assertEquals("SISTEMA",s(audit,"resource_type"));
    }
    /** La serializacion canonica documentada en el contrato, recalculada en la prueba sobre lo guardado, da la huella guardada. */
    @Test @Order(2) void serializacionCanonicaDocumentada(){
        long cut=cut(GI,null,march());
        var digest=new StringBuilder();
        for(var t:StatCuts.TABLES){
            digest.append('#').append(t.name()).append('\n').append(String.join("\t",t.columns())).append('\n');
            for(var r:db.rows("SELECT "+String.join(",",t.columns())+" FROM "+t.name()+" WHERE cut_id=? ORDER BY "+String.join(",",t.key()),cut)){
                var line=new ArrayList<String>();
                for(String c:t.columns()){Object v=r.get(c);line.add(v==null?"\\N":v instanceof Number x?Long.toString(x.longValue()):v.toString().replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r"));}
                digest.append(String.join("\t",line)).append('\n');
            }
        }
        assertEquals(StatCuts.sha256(digest.toString()),s(db.one("SELECT rows_sha256 FROM stat_cut WHERE id=?",cut),"rows_sha256"));
        assertEquals(List.of("stat_cut_row","stat_cut_event","stat_cut_action","stat_cut_action_institution","stat_cut_person","stat_cut_case"),StatCuts.TABLES.stream().map(StatCuts.Table::name).toList());
    }

    // --- Lo congelado: columnas, reserva y lectura solo del corte ------------------------------------------------------------
    /** Lista cerrada de columnas de cada tabla del corte (information_schema), sin datos personales ni texto libre. */
    @Test @Order(3) void listaCerradaDeColumnasSinDatosPersonales(){
        var allowed=new TreeMap<String,Set<String>>();
        for(var t:StatCuts.TABLES){var c=new TreeSet<>(t.columns());c.add("cut_id");allowed.put(t.name(),c);}
        var found=new TreeMap<String,Set<String>>();
        for(var r:db.rows("SELECT LOWER(table_name) AS t,LOWER(column_name) AS c FROM information_schema.columns WHERE LOWER(table_schema)=LOWER(SCHEMA()) AND LOWER(table_name) LIKE 'stat_cut_%'"))
            found.computeIfAbsent(s(r,"t"),k->new TreeSet<>()).add(s(r,"c"));
        assertEquals(allowed,found);
        var forbidden=Set.of("name","nombre","run","rut","document","documento","birth","nacimiento","email","mail","phone","celular","body","relato","basis","reason","motivo","title","titulo","text","texto","address","domicilio","note");
        for(var e:found.entrySet())for(String c:e.getValue())for(String part:c.split("_"))assertFalse(forbidden.contains(part)&&!c.equals("note_id"),e.getKey()+"."+c);
        // Toda dimension de valor unico que congela StatSource tiene su columna.
        for(var d:StatSource.FROZEN)assertTrue(found.get("stat_cut_row").contains(StatSource.frozenColumn(d)),d.code());
        // Sin claves foraneas con borrado en cascada (la migracion no las declara).
        String ddl=read("src/main/resources/db/migration/V23__cortes_y_vistas.sql").lines().filter(l->!l.strip().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
        assertFalse(ddl.toUpperCase(Locale.ROOT).contains("CASCADE"));
        for(String t:FROZEN_TABLES)assertFalse(ddl.toUpperCase(Locale.ROOT).matches("(?s).*CREATE TABLE "+t.toUpperCase(Locale.ROOT)+" \\([^;]*FOREIGN KEY.*"),t);
    }
    @Test @Order(3) void reservaYRectificacionNoEntranAlCorte(){
        long cut=cut(GI,null,march());
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_cut_action WHERE cut_id=? AND note_id=?",cut,reserved),"una constancia reservada nunca entra");
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_cut_action_institution WHERE cut_id=? AND note_id=?",cut,reserved));
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_cut_action WHERE cut_id=? AND note_id=?",cut,original),"la version rectificada no esta vigente");
        assertEquals(1,db.count("SELECT COUNT(*) FROM stat_cut_action WHERE cut_id=? AND note_id=? AND action_type='DENUNCIA_PENAL'",cut,rectified));
        var r=engine.query(access.actor(GI),map("measure","ACTUACIONES","rows",List.of("TIPO_ACTUACION"),"period",march(),"cut_id",cut));
        assertFalse(cells(r).containsKey("QUERELLA"));
        // NNA afectados: sin fecha de nacimiento, con su tramo congelado.
        assertEquals(2,db.count("SELECT COUNT(*) FROM stat_cut_person WHERE cut_id=? AND communication_id=?",cut,c1));
        assertEquals(List.of("8","15"),db.rows("SELECT age_years,age_bracket FROM stat_cut_person WHERE cut_id=? AND communication_id=? ORDER BY ordinal",cut,c1).stream().map(x->s(x,"age_years")).sorted(Comparator.comparingInt(Integer::parseInt)).toList());
        assertFalse(db.rows("SELECT age_bracket FROM stat_cut_person WHERE cut_id=? AND age_bracket IS NOT NULL",cut).isEmpty());
    }
    /** Una consulta sobre un corte lee solo stat_cut_* y los items de catalogo (inmutables); nunca la fuente vigente ni stat_event. */
    @Test @Order(3) void elCorteSeLeeSoloASiMismo(){
        long cut=cut(GI,null,march());
        var statements=new ArrayList<String>();
        var jdbc=new JdbcTemplate(db.dataSource()){
            @Override public List<Map<String,Object>> queryForList(String sql,Object... args){if(sql.startsWith("WITH pre"))statements.add(sql);return super.queryForList(sql,args);}
        };
        var watched=new Db(jdbc);
        var local=new StatEngine(watched,new Reports(watched),guard,new StatCuts(watched,new Reports(watched),guard),20000);
        var queries=new ArrayList<Map<String,Object>>();
        for(var m:StatSource.MEASURES.values()){
            var rows=m.code().equals("REQUERIMIENTOS_CON_VALOR")||m.mentions()?List.of("DERECHO_SUBCATEGORIA"):m.unit()==StatSource.Unit.VARIABLE?List.of("VARIABLE"):List.of("SEDE_VIGENTE");
            queries.add(map("measure",m.code(),"rows",rows,"columns",List.of("TIPO_SOLICITUD"),"period",march(),"include_proposals",true,"percent","TOTAL"));
        }
        for(String d:List.of("REGION_NNA","TRAMO_EDAD_NNA","NACIONALIDAD_NNA","TIPO_INSTITUCION","CONTEXTO","VIA_AGRUPACION","AMBITO","UNIDAD","TERRITORIO_COBERTURA","ADSCRIPCION_INICIAL","TRAMO_EDAD_REQUIRENTE","PERIODO_DIA"))
            queries.add(map("measure","INGRESOS","rows",List.of(d),"period",march()));
        queries.add(map("measure","ACTUACIONES","rows",List.of("TIPO_INSTITUCION"),"columns",List.of("ORIGEN_ACTUACION"),"period",march(),"filters",Map.of("TIPO_ACTUACION",List.of("ORIENTACION"))));
        queries.add(map("measure","CASOS","rows",List.of("PERIODO_DIA"),"period",basisOf(march(),"CREACION_CASO")));
        queries.add(map("measure","INGRESOS","rows",List.of("CANAL"),"period",march(),"filters",Map.of("REGION_NNA",List.of("06"),"DERECHO",List.of("3.1"),"AMBITO",List.of("C06"))));
        for(var q:queries){q.put("cut_id",cut);local.query(access.actor(GI),q);}
        assertFalse(statements.isEmpty());
        var table=Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([a-z_][a-z0-9_]*)");
        var ctes=Pattern.compile("(?i)(?:WITH|,)\\s*([a-z_][a-z0-9_]*)\\s+AS\\s*\\(");
        for(String sql:statements){
            var named=new HashSet<String>();var m=ctes.matcher(sql);while(m.find())named.add(m.group(1).toLowerCase(Locale.ROOT));
            var t=table.matcher(sql);
            while(t.find()){
                String name=t.group(1).toLowerCase(Locale.ROOT);
                if(named.contains(name))continue;
                assertTrue(FROZEN_TABLES.contains(name)||name.equals("stat_catalog_item"),"una consulta sobre un corte lee "+name+": "+sql.substring(0,Math.min(400,sql.length())));
            }
        }
    }
    static Map<String,Object> basisOf(Map<String,Object> p,String basis){var x=new LinkedHashMap<>(p);x.put("date_basis",basis);return x;}

    // --- Facultades y alcance ------------------------------------------------------------------------------------------------
    @Test @Order(4) void soloGestionEstadisticaEnSuAlcanceYNuncaTi(){
        long cuts0=db.count("SELECT COUNT(*) FROM stat_cut");
        for(String who:List.of(RI,R6,GX,TI,"profesional"))assertEquals(403,assertThrows(Problem.class,()->cut(who,null,march())).status,who);
        assertEquals(403,assertThrows(Problem.class,()->cut(G6,null,march())).status,"GESTION de una sede no genera cortes institucionales");
        assertEquals(403,assertThrows(Problem.class,()->cut(G6,"C07",march())).status,"ni de otra sede");
        assertEquals(403,assertThrows(Problem.class,()->cut(GX,"C06",march())).status,"GESTION sin REPORTES en el mismo alcance");
        var bad=List.<Map<String,Object>>of(map("period",march(),"reason","x"),map("title","x","reason","x"),map("title","x","period",map("type","MES"),"reason","x"),
            map("title","x","period",march()),map("title","x","period",march(),"reason","x","rows",List.of()),map("title","x","period",map("type","MES","from","2026-03-01","date_basis","REGISTRO"),"reason","x"));
        for(var data:bad)assertEquals(400,assertThrows(Problem.class,()->run(GI,"STAT_CUT_CREATE",null,null,data)).status,String.valueOf(data));
        assertEquals(cuts0,db.count("SELECT COUNT(*) FROM stat_cut"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_cut_row WHERE cut_id NOT IN (SELECT id FROM stat_cut)"),"un rechazo no deja filas");
        long site=cut(G6,"C06",march());
        assertEquals(scope("C06"),n(db.one("SELECT scope_id FROM stat_cut WHERE id=?",site),"scope_id"));
        var audit=db.one("SELECT note FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action='CORTE_GENERADO')");
        assertTrue(s(audit,"note").matches("Corte CORTE-\\d{4}-\\d{3}; alcance C06; periodo MES"),s(audit,"note"));
        assertFalse(s(audit,"note").contains("sintetico"),"sin titulo ni fundamento");
    }
    @Test @Order(5) void coberturaDelAlcance(){
        long inst=cut(GI,null,march()),site=cut(G6,"C06",march());
        // REPORTES de una sede no ve el corte institucional; la institucional ve ambos; la de materia (UPD) no ve la sede completa.
        assertEquals(403,assertThrows(Problem.class,()->byType(R6,inst)).status);
        assertEquals(403,assertThrows(Problem.class,()->verify(R6,inst)).status);
        assertEquals(403,assertThrows(Problem.class,()->cuts.detail(access.actor(R6),inst)).status);
        assertEquals(403,assertThrows(Problem.class,()->byType(RU,site)).status,"lectura por materia sobre una sede no cubre la sede completa");
        assertEquals(403,assertThrows(Problem.class,()->verify(RU,site)).status);
        assertNotNull(byType(R6,site).get("cells"));assertNotNull(byType(RI,site).get("cells"));assertNotNull(byType(RI,inst).get("cells"));
        var seen=cuts.list(access.actor(R6)).stream().map(x->((Number)x.get("id")).longValue()).toList();
        assertTrue(seen.contains(site)&&!seen.contains(inst),String.valueOf(seen));
        assertTrue(cuts.list(access.actor(RU)).stream().noneMatch(x->((Number)x.get("id")).longValue()==site));
        for(String who:List.of(TI,"profesional"))assertEquals(403,assertThrows(Problem.class,()->cuts.list(access.actor(who))).status,who);
        assertEquals(403,assertThrows(Problem.class,()->verify(TI,inst)).status);
        // El corte de sede reproduce la consulta vigente de esa sede, y un corte inexistente es una peticion invalida.
        assertEquals(comparable(byType(R6,null)),comparable(byType(R6,site)));
        assertEquals(400,assertThrows(Problem.class,()->byType(RI,999_999L)).status);
        assertEquals(404,assertThrows(Problem.class,()->cuts.detail(access.actor(RI),999_999L)).status);
        // Comparada con un corte, la consulta vigente usa el alcance del corte (que la cuenta cubre), no el de la cuenta.
        var cmp=engine.query(access.actor(RI),map("measure","INGRESOS","rows",List.of(),"period",march(),"compare",Map.of("type","CORTE","cut_id",site)));
        assertEquals(grand(byType(R6,null)),grand(cmp));
        assertTrue(grand(cmp)<grand(byType(RI,null)));
    }
    /**
     * Cubrir exige ambitos y materias (regla pura): con los mismos ambitos y sin la materia no se cubre; la materia sola no
     * cubre una sede. Con cuentas reales: la raiz de UPD cubre su propio corte y no el de una sede.
     */
    @Test @Order(5) void cubrirExigeTambienLaMateria(){
        var cutUpd=new Reports.Reach(false,Set.of(1L,2L),Set.of("UPD"));
        assertTrue(StatCuts.covers(new Reports.Reach(false,Set.of(1L,2L,3L),Set.of("UPD","UPR")),cutUpd));
        assertFalse(StatCuts.covers(new Reports.Reach(false,Set.of(1L,2L),Set.of()),cutUpd),"sin la materia");
        assertFalse(StatCuts.covers(new Reports.Reach(false,Set.of(1L),Set.of("UPD")),cutUpd),"sin todos los ambitos");
        assertFalse(StatCuts.covers(new Reports.Reach(false,Set.of(1L),Set.of("UPD")),new Reports.Reach(false,Set.of(9L),Set.of())),"la materia no cubre una sede");
        assertFalse(StatCuts.covers(new Reports.Reach(false,Set.of(1L),Set.of("UPD")),new Reports.Reach(true,Set.of(),Set.of())),"ni la institucion");
        assertTrue(StatCuts.covers(new Reports.Reach(true,Set.of(),Set.of()),cutUpd));
        String root=staff("gest.c.upd","REPORTES@UPD","GESTION_ESTADISTICA@UPD");
        long upd=cut(root,"UPD",march());
        assertNotNull(byType(RU,upd).get("cells"),"la misma concesion (ambitos y materia) lo cubre");
        assertEquals(403,assertThrows(Problem.class,()->byType(R6,upd)).status);
        assertNotNull(byType(RI,upd).get("cells"));
    }
    /** Los rotulos de ambito salen del corte (labels_json), no de la tabla vigente. */
    @Test @Order(5) void rotulosDelInstanteDelCorte(){
        long cut=cut(GI,null,march());
        String name=s(db.one("SELECT name FROM org_scope WHERE code='C06'"),"name");
        db.update("UPDATE org_scope SET name='Sede renombrada' WHERE code='C06'");
        try{
            var body=map("measure","INGRESOS","rows",List.of("SEDE_VIGENTE"),"period",march());
            var frozen=new LinkedHashMap<>(body);frozen.put("cut_id",cut);
            Map<String,String> live=new HashMap<>(),stored=new HashMap<>();
            for(var v:list(obj(engine.query(access.actor(GI),body).get("values")).get("SEDE_VIGENTE")))live.put(s(v,"key"),s(v,"label"));
            for(var v:list(obj(engine.query(access.actor(GI),frozen).get("values")).get("SEDE_VIGENTE")))stored.put(s(v,"key"),s(v,"label"));
            assertEquals("Sede renombrada",live.get("C06"));assertEquals(name,stored.get("C06"));
        }finally{db.update("UPDATE org_scope SET name=? WHERE code='C06'",name);}
    }
    @Test @Order(5) void periodoYModoDelCorte(){
        long cut=cut(GI,null,march());
        var base=map("measure","INGRESOS","rows",List.of("CANAL"),"cut_id",cut);
        for(var change:List.<Map<String,Object>>of(Map.of("period",period("MES","2026-04-01")),Map.of("period",days("2026-02-28","2026-03-10")),
            Map.of("period",march(),"time_mode","FECHA","as_of","2026-03-20T00:00:00Z"),Map.of("period",march(),"time_mode","AL_INGRESO"),
            Map.of("period",march(),"compare",Map.of("type","PERIODO_ANTERIOR")),Map.of("period",march(),"compare",Map.of("type","MISMO_PERIODO_ANIO_ANTERIOR")),
            Map.of("period",march(),"cut_id","uno"),Map.of("period",march(),"compare",Map.of("type","CORTE"))))
        {
            var body=new LinkedHashMap<>(base);body.putAll(change);
            assertEquals(400,assertThrows(Problem.class,()->engine.query(access.actor(GI),body),String.valueOf(change)).status,String.valueOf(change));
        }
        var live=map("measure","INGRESOS","rows",List.of("CANAL"),"period",march(),"time_mode","AL_REQ","compare",Map.of("type","CORTE","cut_id",cut));
        assertEquals(400,assertThrows(Problem.class,()->engine.query(access.actor(GI),live)).status);
        // Un subperiodo dentro del corte se consulta con su propio eje y fecha de analisis.
        var sub=map("measure","INGRESOS","rows",List.of("PERIODO_DIA"),"period",days("2026-03-05","2026-03-12"),"cut_id",cut);
        assertEquals(2,grand(engine.query(access.actor(GI),sub)));
        var audit=db.one("SELECT note FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action='STAT_CONSULTA')");
        assertEquals("Medida INGRESOS; modo VIGENTE; sobre un corte; dimensiones: PERIODO_DIA",s(audit,"note"));
    }

    // --- Anulacion e inmutabilidad ------------------------------------------------------------------------------------------
    @Test @Order(6) void anulacionConservaElCorte(){
        long cut=cut(G6,"C06",march());
        var before=db.one("SELECT rows_sha256,metadata_sha256,row_count FROM stat_cut WHERE id=?",cut);
        var counts=FROZEN_TABLES.stream().map(t->stamp(t,cut)).toList();
        assertEquals(403,assertThrows(Problem.class,()->run(R6,"STAT_CUT_ANNUL",cut,0L,Map.of("reason","x"))).status);
        assertEquals(403,assertThrows(Problem.class,()->run(TI,"STAT_CUT_ANNUL",cut,0L,Map.of("reason","x"))).status);
        assertEquals(400,assertThrows(Problem.class,()->run(G6,"STAT_CUT_ANNUL",cut,0L,Map.of())).status,"fundamento obligatorio");
        assertEquals(409,assertThrows(Problem.class,()->run(G6,"STAT_CUT_ANNUL",cut,5L,Map.of("reason","x"))).status,"revision");
        long other=cut(GI,null,march());
        assertEquals(403,assertThrows(Problem.class,()->run(G6,"STAT_CUT_ANNUL",other,0L,Map.of("reason","x"))).status,"una sede no anula el institucional");
        run(G6,"STAT_CUT_ANNUL",cut,0L,Map.of("reason","Corte generado con un periodo equivocado"));
        var after=db.one("SELECT * FROM stat_cut WHERE id=?",cut);
        assertEquals("ANULADO",s(after,"state"));assertEquals(1,n(after,"revision"));assertEquals(user(G6),n(after,"annulled_by"));
        assertEquals(s(before,"rows_sha256"),s(after,"rows_sha256"));assertEquals(s(before,"metadata_sha256"),s(after,"metadata_sha256"));
        assertEquals(counts,FROZEN_TABLES.stream().map(t->stamp(t,cut)).toList(),"las filas se conservan");
        assertEquals(true,verify(G6,cut).get("ok"));
        assertEquals(409,assertThrows(Problem.class,()->run(G6,"STAT_CUT_ANNUL",cut,1L,Map.of("reason","x"))).status,"ya anulado");
        assertEquals("ANULADO",s(cuts.detail(access.actor(R6),cut),"state"));
        assertNotNull(byType(R6,cut).get("cells"),"un corte anulado se conserva y se puede consultar");
        assertEquals("Corte "+s(after,"code"),s(db.one("SELECT note FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action='CORTE_ANULADO')"),"note"));
        // GESTION institucional cubre el corte de sede y tambien puede anular otro.
        run(GI,"STAT_CUT_ANNUL",other,0L,Map.of("reason","Fundamento"));
    }
    /** Analisis estatico: en src/main ninguna sentencia modifica o borra lo congelado; la unica es la cabecera al anular. */
    @Test @Order(7) void ningunCodigoModificaUnCorte()throws Exception{
        var writes=Pattern.compile("(?i)(UPDATE|DELETE\\s+FROM|TRUNCATE(\\s+TABLE)?|DROP\\s+TABLE|ALTER\\s+TABLE|REPLACE\\s+INTO|MERGE\\s+INTO)\\s+(stat_cut\\w*)");
        var found=new ArrayList<String>();
        try(var files=Files.walk(Path.of("src/main"))){
            for(var f:files.filter(Files::isRegularFile).toList()){
                if(f.toString().endsWith("V23__cortes_y_vistas.sql"))continue;
                var m=writes.matcher(Files.readString(f));
                while(m.find())found.add(f.getFileName()+": "+m.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+"," ")+" "+m.group(3));
            }
        }
        assertEquals(List.of("StatCuts.java: UPDATE stat_cut"),found);
        String source=read("src/main/java/cl/nexbyte/sigera/StatCuts.java");
        var update=Pattern.compile("UPDATE stat_cut SET ([^\"]*) WHERE").matcher(source);
        assertTrue(update.find());
        assertEquals("state=?,annulled_by=?,annulled_ms=?,annul_basis=?,revision=revision+1",update.group(1),"solo estado, anulacion y revision");
    }
    /** Barrido dinamico: despues de comandos de gestion, clasificacion, cierre, reapertura, otro corte y una anulacion, el corte sigue integro. */
    @Test @Order(8) void comandosPosterioresNoCambianElCorte(){
        long cut=cut(GI,null,march());
        var before=FROZEN_TABLES.stream().map(t->stamp(t,cut)).toList();
        String rows=s(db.one("SELECT rows_sha256 FROM stat_cut WHERE id=?",cut),"rows_sha256");
        rights(P06,"CAS",case1,"CONTEXTO",add("FAMILIAR"));
        on(P06,"CASE_NOTE","case_record",case1,map("effective_at",ago(30),"body","Otra orientacion","action_type","ORIENTACION"));
        route(H06,c2,"C06",null);
        long r3=assume(H07,c3,P07,null);
        on(H07,"REQ_CLOSE","requirement",r3,map("reason","Cierre sintetico"));
        run(H07,"REOPEN_REQUEST",r3,null,Map.of("reason","Nueva gestion"));
        on(H07,"REOPEN_DECIDE","reopening",last("reopening"),Map.of("decision","ACEPTADA","reason","Procede"));
        run(H06,"STAT_CLASSIFY",c2,db.count("SELECT COUNT(*) FROM stat_event WHERE communication_id=?",c2),map("stat",map(TS,code("PROMOCION"),"reason","Correccion posterior")));
        long next=cut(GI,null,march());
        run(GI,"STAT_CUT_ANNUL",next,0L,Map.of("reason","Prueba"));
        assertEquals(before,FROZEN_TABLES.stream().map(t->stamp(t,cut)).toList());
        assertEquals(rows,s(db.one("SELECT rows_sha256 FROM stat_cut WHERE id=?",cut),"rows_sha256"));
        assertEquals(true,verify(GI,cut).get("ok"));
        assertEquals("VIGENTE",s(db.one("SELECT state FROM stat_cut WHERE id=?",cut),"state"));
    }

    // --- Guardas dentro del comando -------------------------------------------------------------------------------------------
    /** Sin cupo, 429 de inmediato tambien dentro del lock global, sin escrituras; el cupo se libera al terminar. */
    @Test @Order(9) void cupoSinEsperaDentroDelComando()throws Exception{
        long cuts0=db.count("SELECT COUNT(*) FROM stat_cut"),log0=db.count("SELECT COUNT(*) FROM command_log");
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            for(long account:List.of(-1L,-2L))pool.submit(()->guard.run(account,()->{entered.countDown();try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return null;}));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            long t0=System.nanoTime();
            var p=assertThrows(Problem.class,()->cut(GI,null,march()));
            assertEquals(429,p.status);assertEquals(StatGuard.BUSY,p.getMessage());
            assertTrue((System.nanoTime()-t0)/1_000_000<5_000,"sin esperar");
        }finally{release.countDown();pool.shutdown();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
        assertEquals(cuts0,db.count("SELECT COUNT(*) FROM stat_cut"));assertEquals(log0,db.count("SELECT COUNT(*) FROM command_log"));
        assertEquals(guard.maxConcurrent(),guard.available());
        cut(GI,null,march());
        assertEquals(guard.maxConcurrent(),guard.available(),"liberado tras el comando");
        assertTrue(guard.active().isEmpty());
    }
    /** Al vencer el plazo dentro del comando: rollback sin escrituras parciales, 503 y STAT_CONSULTA_CANCELADA despues. */
    @Test @Order(10) void plazoVencidoDentroDelComando(){
        if(!MYSQL)db.update("CREATE ALIAS IF NOT EXISTS STAT_LENTO FOR 'cl.nexbyte.sigera.StatGuardTest.slow'");
        var slow=new StatGuard(db,transactions,2,1,1);
        var local=new StatCuts(db,reports,slow,new JdbcTemplate(db.dataSource()),()->db.rows(StatGuardTest.slowSql(5)));
        long cuts0=db.count("SELECT COUNT(*) FROM stat_cut"),rows0=db.count("SELECT COUNT(*) FROM stat_cut_row"),cancelled=db.count("SELECT COUNT(*) FROM audit_event WHERE action=?",StatGuard.CANCELLED);
        var input=new Input(map("title","Corte lento","period",march(),"reason","Fundamento"));
        long t0=System.nanoTime();
        var p=assertThrows(Problem.class,()->new TransactionTemplate(transactions).execute(s->{db.lock();return local.run(access.actor(GI),"STAT_CUT_CREATE",0,null,input);}));
        assertEquals(503,p.status);assertEquals(slow.timeoutMessage(),p.getMessage());
        assertTrue((System.nanoTime()-t0)/1_000_000<4_000,"cancelado por el plazo");
        assertEquals(cuts0,db.count("SELECT COUNT(*) FROM stat_cut"));assertEquals(rows0,db.count("SELECT COUNT(*) FROM stat_cut_row"),"sin escrituras parciales");
        assertEquals(cancelled+1,db.count("SELECT COUNT(*) FROM audit_event WHERE action=?",StatGuard.CANCELLED));
        assertEquals("Generacion de corte; cancelada por tiempo (1 s)",s(db.one("SELECT note FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action=?)",StatGuard.CANCELLED),"note"));
        assertEquals(2,slow.available());
        // Sin vencer, el resto del comando ya no queda acotado por el plazo.
        var quick=new StatCuts(db,reports,slow,new JdbcTemplate(db.dataSource()),()->{});
        new TransactionTemplate(transactions).execute(s->{db.lock();var r=quick.run(access.actor(GI),"STAT_CUT_CREATE",0,null,input);try{Thread.sleep(1_500);}catch(InterruptedException e){Thread.currentThread().interrupt();}db.count("SELECT COUNT(*) FROM stat_cut");return r;});
        assertEquals(cuts0+1,db.count("SELECT COUNT(*) FROM stat_cut"));
    }
    /** En MySQL, ninguna sentencia de congelado usa claves automaticas sobre un CTE (defecto 1112): EXPLAIN de cada una. */
    @Test @Order(11) void congeladoSinClavesAutomaticas(){
        int[] explained={0};
        var jdbc=new JdbcTemplate(db.dataSource()){
            @Override public void query(String sql,RowCallbackHandler rch,Object... args){
                if(MYSQL&&sql.startsWith("WITH pre")){
                    explained[0]++;
                    for(var row:super.queryForList("EXPLAIN "+sql,args))assertFalse(String.valueOf(row.get("key")).startsWith("<auto_key"),()->"clave automatica en "+row+": "+sql.substring(0,300));
                }
                super.query(sql,rch,args);
            }
        };
        var local=new StatCuts(db,reports,guard,jdbc,()->{});
        long id=new TransactionTemplate(transactions).execute(s->{db.lock();return local.run(access.actor(GI),"STAT_CUT_CREATE",0,null,new Input(map("title","Corte","period",march(),"reason","Fundamento"))).id();});
        assertEquals(MYSQL?6:0,explained[0]);
        assertEquals(true,verify(GI,id).get("ok"));
    }

    /**
     * Decision A del responsable (2026-11-26): el corte se genera con el lock global. Un comando que llega mientras tanto
     * espera y termina bien; en MySQL, innodb_lock_wait_timeout es mayor que el plazo de StatGuard (el corte cancela antes).
     * H2 (perfiles test y local) espera un lock a lo mas 2 s: ahi el corte simulado dura menos que eso.
     */
    @Test @Order(12) void comandoQueLlegaDuranteElCorteEsperaYTerminaBien()throws Exception{
        if(MYSQL)assertTrue(db.count("SELECT @@innodb_lock_wait_timeout")>guard.timeoutSeconds(),"innodb_lock_wait_timeout mayor que el plazo del corte");
        var inside=new CountDownLatch(1);
        var local=new StatCuts(db,reports,guard,new JdbcTemplate(db.dataSource()),()->{
            inside.countDown();
            try{Thread.sleep(MYSQL?5_000:1_200);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        });
        var pool=Executors.newSingleThreadExecutor();
        try{
            var generating=pool.submit(()->new TransactionTemplate(transactions).execute(s->{db.lock();return local.run(access.actor(GI),"STAT_CUT_CREATE",0,null,new Input(map("title","Corte","period",march(),"reason","Fundamento"))).id();}));
            assertTrue(inside.await(30,TimeUnit.SECONDS),"el corte se esta generando con el lock tomado");
            long t0=System.nanoTime();
            long com=com(P06,"C06","CORREO","2026-03-28T15:00:00Z",form("06"),null);
            long waited=(System.nanoTime()-t0)/1_000_000;
            long cut=generating.get(30,TimeUnit.SECONDS);
            assertTrue(waited>=(MYSQL?4_000:800),"el comando espero al corte: "+waited+" ms");
            assertEquals("NUEVA",s(db.one("SELECT state FROM communication WHERE id=?",com),"state"),"y termino bien");
            assertEquals(0,db.count("SELECT COUNT(*) FROM stat_cut_row WHERE cut_id=? AND communication_id=?",cut,com),"lo posterior al corte no entra");
            assertTrue(n(db.one("SELECT created_ms FROM communication WHERE id=?",com),"created_ms")>n(db.one("SELECT as_of_ms FROM stat_cut WHERE id=?",cut),"as_of_ms"));
            long after=cut(GI,null,march());
            assertEquals(1,db.count("SELECT COUNT(*) FROM stat_cut_row WHERE cut_id=? AND communication_id=?",after,com),"el corte siguiente si lo incluye");
        }finally{pool.shutdown();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}
    }

    static String read(String path){try{return Files.readString(Path.of(path));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
