package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_e11_series;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-series-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatSeriesTest extends StatFixture {
    static final String USER="serie.gestor", REG="serie.regional", OTHER="serie.otra", READER="serie.lectura";
    static final String DATA="periodo_desde;periodo_hasta;dimension;categoria;valor\n"
        +"2024-01-01;2024-01-31;ADSCRIPCION_INICIAL;Metropolitana;2\n"
        +"2024-01-01;2024-01-31;ADSCRIPCION_INICIAL;Total;2\n"
        +"2024-02-01;2024-02-29;ADSCRIPCION_INICIAL;Metropolitana;3\n"
        +"2024-02-01;2024-02-29;ADSCRIPCION_INICIAL;Total;3\n"
        +"2024-03-01;2024-03-31;ADSCRIPCION_INICIAL;Metropolitana;5\n"
        +"2024-03-01;2024-03-31;ADSCRIPCION_INICIAL;Total;5\n";
    @Autowired StatSeries series;
    @Autowired StatExport exports;

    @BeforeEach void accounts(){
        site("E11-R6","06","ACTIVA");site("E11-R7","07","ACTIVA");
        staff(USER,"REPORTES","GESTION_ESTADISTICA","EXPORTACION");
        staff(REG,"REPORTES@E11-R6","GESTION_ESTADISTICA@E11-R6");
        staff(OTHER,"REPORTES@E11-R7","GESTION_ESTADISTICA@E11-R7");staff(READER,"REPORTES");
    }
    Map<String,Object> seriesInput(String data,Long scope){
        return map("title","Serie sintética", "indicator","INGRESOS", "scope_id",scope,"source","Informe sintético",
            "methodology","Conteos mensuales", "granularity","MES", "period_from","2024-01-01","period_to","2024-03-31",
            "reason","Validación sintética", "format","csv","content",Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
    }

    // Enero 2, febrero 3, marzo 5. Total declarado distinto rechaza todo. La superposición conserva ambas series aparte.
    @Test void tresPeriodosValidacionVistaPreviaCorrespondenciaYSuperposicion(){
        var good=seriesInput(DATA,null);
        long headers=db.count("SELECT COUNT(*) FROM stat_series"),values=db.count("SELECT COUNT(*) FROM stat_series_value"),audit=db.count("SELECT COUNT(*) FROM audit_event");
        var preview=series.preview(access.actor(USER),good);
        assertEquals(List.of(2L,3L,5L),list(preview.get("values")).stream().filter(x->!b(x,"is_total")).map(x->n(x,"value")).toList());
        assertNull(list(preview.get("values")).getFirst().get("category_code"));
        assertEquals("OFICINA_NACIONAL",s(list(preview.get("suggested_mappings")).getFirst(),"code"));
        assertEquals(headers,db.count("SELECT COUNT(*) FROM stat_series"));assertEquals(values,db.count("SELECT COUNT(*) FROM stat_series_value"));
        assertEquals(audit,db.count("SELECT COUNT(*) FROM audit_event"));
        var bad=seriesInput(DATA.replace("Total;3","Total;4"),null);
        assertEquals(400,assertThrows(Problem.class,()->run(USER,"STAT_SERIES_IMPORT",null,null,bad)).status);
        assertEquals(headers,db.count("SELECT COUNT(*) FROM stat_series"));assertEquals(values,db.count("SELECT COUNT(*) FROM stat_series_value"));
        good.put("mappings",List.of(Map.of("dimension","ADSCRIPCION_INICIAL","category","Metropolitana","code","OFICINA_NACIONAL")));
        long first=run(USER,"STAT_SERIES_IMPORT",null,null,good).id();
        assertTrue(list(series.detail(access.actor(READER),first).get("values")).stream().filter(r->!b(r,"is_total")).allMatch(r->s(r,"category_code").equals("OFICINA_NACIONAL")));
        long second=run(USER,"STAT_SERIES_IMPORT",null,null,seriesInput(DATA,null)).id();
        var together=series.compare(access.actor(READER),map("series_ids",List.of(first,second),"sum",true));
        assertTrue(list(together.get("warnings")).stream().anyMatch(r->s(r,"code").equals("PERIODOS_SUPERPUESTOS")));
        assertNull(together.get("sum"));assertEquals(2,list(together.get("series")).size());
    }

    @Test void facultadesDeSedeYAnulacionConservaFilas(){
        var regional=seriesInput(DATA,scope("E11-R6"));
        assertEquals(403,assertThrows(Problem.class,()->run(OTHER,"STAT_SERIES_IMPORT",null,null,regional)).status);
        assertEquals(403,assertThrows(Problem.class,()->run(REG,"STAT_SERIES_IMPORT",null,null,seriesInput(DATA,null))).status);
        long id=run(REG,"STAT_SERIES_IMPORT",null,null,regional).id();
        assertEquals(403,assertThrows(Problem.class,()->series.detail(access.actor(OTHER),id)).status);
        assertEquals(403,assertThrows(Problem.class,()->series.list(access.actor("ti"))).status);
        assertTrue(series.list(access.actor(REG)).stream().anyMatch(r->n(r,"id")==id));
        assertEquals(403,assertThrows(Problem.class,()->exports.series(access.actor(REG),"csv",id)).status);
        assertTrue(exports.series(access.actor(USER),"csv",id).bytes().length>0);
        assertEquals(403,assertThrows(Problem.class,()->run(OTHER,"STAT_SERIES_ANNUL",id,0L,Map.of("reason","Prueba"))).status);
        assertEquals(409,assertThrows(Problem.class,()->run(REG,"STAT_SERIES_ANNUL",id,9L,Map.of("reason","Prueba"))).status);
        run(REG,"STAT_SERIES_ANNUL",id,0L,Map.of("reason","Prueba"));
        var row=series.detail(access.actor(REG),id);
        assertEquals("ANULADA",s(row,"state"));assertEquals(6,list(row.get("values")).size());
    }

    @Test void sumaExplicitaPeriodosSeparadosConTotalesSinDuplicar(){
        var january=seriesInput(DATA.lines().limit(3).collect(java.util.stream.Collectors.joining("\n")),null);january.put("period_to","2024-01-31");
        var february=seriesInput(DATA.lines().limit(1).findFirst().orElseThrow()+"\n"+DATA.lines().skip(3).limit(2).collect(java.util.stream.Collectors.joining("\n")),null);
        february.put("period_from","2024-02-01");february.put("period_to","2024-02-29");
        long first=run(USER,"STAT_SERIES_IMPORT",null,null,january).id(),second=run(USER,"STAT_SERIES_IMPORT",null,null,february).id();
        var ids=List.of(first,second);
        assertNull(series.compare(access.actor(READER),map("series_ids",ids)).get("sum"));
        var sum=list(series.compare(access.actor(READER),map("series_ids",ids,"sum",true)).get("sum"));
        assertEquals(2,sum.size());assertEquals(5,n(sum.getFirst(),"value"));assertEquals(5,n(sum.getLast(),"value"));
        assertFalse(b(sum.getFirst(),"is_total"));assertTrue(b(sum.getLast(),"is_total"));
    }

    @Test void xlsxConColumnasRelacionadasSeConfirmaIdempotente(){
        var grid=List.of(List.of("Grupo","Desde","Cantidad","Eje","Hasta"),List.of("Metropolitana","2024-01-01",2L,"ADSCRIPCION_INICIAL","2024-01-31"));
        var data=seriesInput(DATA,null);data.put("format","xlsx");data.put("period_to","2024-01-31");
        data.put("content",Base64.getEncoder().encodeToString(Xlsx.write(List.of(new Xlsx.Sheet("Histórica",grid)))));
        assertEquals(true,series.preview(access.actor(USER),data).get("needs_columns"));
        data.put("columns",Map.of("periodo_desde","Desde","periodo_hasta","Hasta","dimension","Eje","categoria","Grupo","valor","Cantidad"));
        assertEquals(2,n(list(series.preview(access.actor(USER),data).get("values")).getFirst(),"value"));
        long headers=db.count("SELECT COUNT(*) FROM stat_series"),values=db.count("SELECT COUNT(*) FROM stat_series_value");
        var command=new Workflow.Command(UUID.randomUUID(),"STAT_SERIES_IMPORT",null,null,data);
        var created=flow.run(USER,command);assertEquals(created,flow.run(USER,command));
        assertEquals(headers+1,db.count("SELECT COUNT(*) FROM stat_series"));assertEquals(values+1,db.count("SELECT COUNT(*) FROM stat_series_value"));
    }

    @Test void comparacionConSigerAvisaSoloCuandoDifiereElAlcance(){
        long org=run(USER,"STAT_SERIES_IMPORT",null,null,seriesInput(DATA,null)).id();
        long regional=run(REG,"STAT_SERIES_IMPORT",null,null,seriesInput(DATA,scope("E11-R6"))).id();
        var reader=access.actor(staff("serie.mixta","REPORTES","REPORTES@E11-R6"));
        var query=map("measure","INGRESOS","period",days("2024-01-01","2024-03-31"));
        var whole=series.compare(reader,map("series_ids",List.of(org),"query",query));
        assertNotNull(whole.get("siger"));assertTrue(list(whole.get("warnings")).isEmpty());
        var partial=series.compare(reader,map("series_ids",List.of(regional),"query",query));
        assertTrue(list(partial.get("warnings")).stream().anyMatch(w->s(w,"code").equals("ALCANCES_DISTINTOS")));
        assertNull(whole.get("sum"));assertNull(partial.get("sum"));
    }
}
