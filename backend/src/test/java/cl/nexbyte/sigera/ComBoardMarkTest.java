package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;

/**
 * Marca territorial y de riesgo de la bandeja de comunicaciones (0.7.0, D23, gate T03b; escenario 8, parte servidor).
 * La lista de COM (Todas, Por distribuir, Mis ingresos) y la cola "Ingresos por distribuir" de Inicio agregan a cada
 * fila origin_region (region vigente con la regla del selector de la COM, Access.suggestedRegion(id,null): Formulario
 * nna-region y luego adult-region, nunca la ficha), risk (primer campo CAMPO riesgo afirmativo: si o true sin distinguir
 * mayusculas, decidido en Java) y to_distribute (la condicion del filtro "Por distribuir"). Las filas, su orden, los
 * totales y la paginacion no cambian, y el valor del campo riesgo no sale del servidor.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_bandeja_marca;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-bandeja-marca-${random.uuid}"})
@ActiveProfiles("test")
class ComBoardMarkTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Access access;@Autowired Db db;@Autowired BoardQueries boards;

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    long user(String name){return n(db.one("SELECT id FROM staff WHERE username=?",name),"id");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    /** COM del Formulario recibida en Requerimientos con los campos dados (null quita el campo). */
    long formulario(String... kv){
        var doc=cuVulneration();var campos=(ObjectNode)doc.path("datos").path("campos");
        campos.remove("riesgo");campos.remove("adult-region");
        for(int i=0;i<kv.length;i+=2)if(kv[i+1]==null)campos.remove(kv[i]);else campos.put(kv[i],kv[i+1]);
        return flow.conserveExternal("administrativo",scope("UPR-REQ"),sign(doc)).id();
    }
    /** COM manual con filas CAMPO insertadas (para el orden de varias filas riesgo). */
    long manual(String... kv){
        long com=run("administrativo","COM_CREATE",null,null,Map.of("intake",Map.of(),"scope_id",scope("UPR-REQ"),"channel","TELEFONO","received_at",Instant.now().minusSeconds(120).toString(),"subject","COM manual sintetica","body","Relato sintetico")).id();
        for(int i=0;i<kv.length;i+=2)db.update("INSERT INTO external_field(communication_id,field_group,item,field_key,value_text) VALUES(?,'CAMPO',?,?,?)",com,i,kv[i],kv[i+1]);
        return com;
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> items(Map<String,Object> page){return (List<Map<String,Object>>)page.get("items");}
    static List<Long> ids(Map<String,Object> page){return items(page).stream().map(r->n(r,"id")).toList();}
    Map<String,Object> page(String who,String filter){return boards.page(access.actor(who),"com",0,100,"",filter,null);}
    List<Long> sql(String where,Object... args){return db.rows("SELECT c.id FROM communication c WHERE "+where+" ORDER BY c.id DESC",args).stream().map(r->n(r,"id")).toList();}
    static Map<Long,Map<String,Object>> byId(Map<String,Object> page){var out=new LinkedHashMap<Long,Map<String,Object>>();for(var r:items(page))out.put(n(r,"id"),r);return out;}

    @Test void marcaDeRegionYRiesgoSinCambiarFilasNiOrden(){
        db.update("UPDATE staff SET must_change=FALSE WHERE technical=FALSE");
        // Region vigente: nna-region y luego adult-region, solo codigos validos del catalogo.
        long maule=formulario("adult-region","07");
        long rmRisk=formulario("adult-region","13","riesgo","si");
        long noRegionRisk=formulario("riesgo","SI");
        long nuble=formulario("adult-region","16","riesgo","TRUE");
        long atacamaNo=formulario("adult-region","03","riesgo","no");
        long accent=formulario("adult-region","14","riesgo","sí");
        long nnaFirst=formulario("adult-region","07","nna-region","09","riesgo","verdadero");
        long invalidNna=formulario("nna-region","XX","adult-region","06","riesgo","True");
        long twice=manual("riesgo","no","riesgo","si");
        long manualPlain=manual();
        // Un riesgo afirmativo fuera de los campos del Formulario (otro grupo) no cuenta.
        long otherGroup=manual();
        db.update("INSERT INTO external_field(communication_id,field_group,item,field_key,value_text) VALUES(?,'INSTITUCION',0,'riesgo','si')",otherGroup);
        // Atencion directa: nace ATENDIDA con region en la ficha; la lista no usa la ficha (regla del selector de la COM).
        long req=run("administrativo","ATTENTION_CREATE",null,null,new LinkedHashMap<>(Map.of("scope_id",scope("UPR-REQ"),"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(600).toString(),
            "form",new LinkedHashMap<>(Map.of("schema_version","1.1","modality","GENERAL","motive","Consulta sintetica","course","EN_ATENCION","people",List.of(),"region","05"))))).id();
        long direct=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
        // Distribucion de una COM con riesgo: deja de estar por distribuir y conserva la marca de riesgo.
        long routed=formulario("adult-region","07","riesgo","si");
        run("distribucion","COM_ROUTE",routed,n(db.one("SELECT revision FROM communication WHERE id=?",routed),"revision"),Map.of("scope_id",scope("UPR"),"reason","Clasificacion sintetica"));

        // El orden de la lista es por id descendente, no por fecha de registro: una fecha posterior no lo cambia.
        db.update("UPDATE communication SET created_ms=created_ms+86400000 WHERE id=?",maule);

        var expected=new LinkedHashMap<Long,List<Object>>(); // region, riesgo
        expected.put(maule,Arrays.asList("07",false));expected.put(rmRisk,Arrays.asList("13",true));expected.put(noRegionRisk,Arrays.asList(null,true));
        expected.put(nuble,Arrays.asList("16",true));expected.put(atacamaNo,Arrays.asList("03",false));expected.put(accent,Arrays.asList("14",false));
        expected.put(nnaFirst,Arrays.asList("09",false));expected.put(invalidNna,Arrays.asList("06",true));expected.put(twice,Arrays.asList(null,false));
        expected.put(manualPlain,Arrays.asList(null,false));expected.put(otherGroup,Arrays.asList(null,false));expected.put(direct,Arrays.asList(null,false));expected.put(routed,Arrays.asList("07",true));

        String central="EXISTS(SELECT 1 FROM org_scope o WHERE o.id=c.origin_scope AND o.regional=FALSE)";
        var all=page("distribucion","all");var pending=page("distribucion","pending");var mine=page("administrativo","mine");
        // Mismas filas y mismo orden que la condicion de cada vista (la distribucion central lee las COM de origen central).
        assertEquals(sql(central),ids(all));
        assertEquals(sql("c.state='NUEVA' AND "+central),ids(pending));
        assertEquals(sql("c.created_by=?",user("administrativo")),ids(mine));
        assertTrue(ids(all).containsAll(expected.keySet()));
        assertFalse(ids(pending).contains(routed));assertFalse(ids(pending).contains(direct));assertTrue(ids(pending).contains(maule));
        // Totales y paginacion: la pagina de 2 en 2 recorre las mismas filas en el mismo orden.
        for(var view:List.of(all,pending,mine))assertEquals((long)items(view).size(),view.get("total"));
        var paged=new ArrayList<Long>();long total=(long)pending.get("total");
        for(int p=0;p*2<total;p++)paged.addAll(ids(boards.page(access.actor("distribucion"),"com",p,2,"","pending",null)));
        assertEquals(ids(pending),paged);
        // Busqueda por folio sin cambio.
        String folio=s(db.one("SELECT folio FROM communication WHERE id=?",nuble),"folio");
        assertEquals(List.of(nuble),ids(boards.page(access.actor("distribucion"),"com",0,25,folio,"all",null)));

        for(var view:List.of(all,pending,mine))for(var r:items(view)){
            long id=n(r,"id");
            // Regla unica con el selector de la COM y sin el valor del campo.
            assertEquals(access.suggestedRegion(id,null),r.get("origin_region"),"COM "+id);
            assertFalse(r.containsKey("risk_value"),"COM "+id);
            assertInstanceOf(Boolean.class,r.get("risk"));assertInstanceOf(Boolean.class,r.get("to_distribute"));
            if(expected.containsKey(id)){
                assertEquals(expected.get(id).get(0),r.get("origin_region"),"region COM "+id);
                assertEquals(expected.get(id).get(1),r.get("risk"),"riesgo COM "+id);
            }
        }
        // Por distribuir = la condicion del filtro, tambien para quien ve la COM sin distribuir (Mis ingresos).
        for(var r:items(all))assertEquals(ids(pending).contains(n(r,"id")),r.get("to_distribute"),"COM "+n(r,"id"));
        for(var r:items(pending))assertEquals(true,r.get("to_distribute"));
        for(var r:items(mine))assertEquals(false,r.get("to_distribute"),"administrativo no distribuye: COM "+n(r,"id"));
        assertEquals("NUEVA",s(byId(mine).get(rmRisk),"state"));
        assertEquals(true,byId(all).get(routed).get("risk"));assertEquals(false,byId(all).get(routed).get("to_distribute"));
        assertFalse(all.toString().contains("verdadero"),"el valor del campo no viaja");

        // Cola "Ingresos por distribuir" de Inicio: las primeras 5 de Por distribuir, con la misma marca y el mismo total.
        @SuppressWarnings("unchecked")
        var queues=(List<Map<String,Object>>)boards.dashboard(access.actor("distribucion")).get("queues");
        var queue=queues.stream().filter(q->"Ingresos por distribuir".equals(q.get("title"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var qpage=(Map<String,Object>)queue.get("page");
        assertEquals(ids(pending).subList(0,5),ids(qpage));
        assertEquals(pending.get("total"),qpage.get("total"));
        for(var r:items(qpage)){
            assertEquals(byId(pending).get(n(r,"id")),r);
            assertEquals(true,r.get("to_distribute"));
        }
    }

    @Test void riesgoAfirmativoSoloSiOTrue(){
        for(var yes:List.of("si","SI","Si","sI","true","TRUE","True"))assertTrue(BoardQueries.riskAffirmative(yes),yes);
        for(var no:Arrays.asList("no","false","sí","SÍ"," si","si ","s","1","yes","verdadero","sı","",null,1,true))assertFalse(BoardQueries.riskAffirmative(no),String.valueOf(no));
    }
}
