package cl.nexbyte.sigera;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Configuraciones guardadas del explorador (0.8.0, D38 y D43, gate E10): solo cuentas con REPORTES; solo el dueno edita o
 * borra (con revision); definicion validada como la consulta de la cuenta y guardada en JSON canonico; compartir exige
 * GESTION_ESTADISTICA (VISTA_COMPARTIDA y VISTA_NO_COMPARTIDA, sin titulo); una compartida la ve toda cuenta con REPORTES
 * y la ejecuta con su alcance, y si apunta a un corte que la cuenta no ve, la consulta responde 403.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_vistas;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-vistas-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatViewsTest extends StatFixture {
    static final String RI="rep.v.inst",GI="gest.v.inst",R6="rep.v.s06";
    @Autowired StatViews views;

    static Map<String,Object> definition(){return map("measure","INGRESOS","rows",List.of("CANAL"),"period",map("type","CUENTA_PUBLICA","from","2025-07-01"),"percent","TOTAL");}
    long save(String who,Long id,Long revision,String title,Map<String,Object> definition){return run(who,"STAT_VIEW_SAVE",id,revision,map("title",title,"definition",definition)).id();}
    long revision(long id){return n(db.one("SELECT revision FROM stat_view WHERE id=?",id),"revision");}
    List<Long> visible(String who){return views.list(access.actor(who)).stream().map(x->((Number)x.get("id")).longValue()).toList();}

    @Test void vistasPersonalesYCompartidas(){
        site("V06","06","ACTIVA");
        staff(RI,"REPORTES");staff(GI,"REPORTES","GESTION_ESTADISTICA");staff(R6,"REPORTES@V06");
        db.update("UPDATE staff SET must_change=FALSE");
        long views0=db.count("SELECT COUNT(*) FROM stat_view");
        // Solo cuentas con REPORTES; nunca TI.
        for(String who:List.of("ti","profesional"))assertEquals(403,assertThrows(Problem.class,()->save(who,null,null,"Vista",definition())).status,who);
        for(String who:List.of("ti","profesional"))assertEquals(403,assertThrows(Problem.class,()->views.list(access.actor(who))).status,who);
        // Definicion validada como consulta; campos cerrados.
        for(var bad:List.<Map<String,Object>>of(map("title","Vista"),map("title","Vista","definition","INGRESOS"),map("title","Vista","definition",map("measure","NADA","period",definition().get("period"))),
            map("title","Vista","definition",definition(),"shared",true),map("definition",definition())))
            assertEquals(400,assertThrows(Problem.class,()->run(RI,"STAT_VIEW_SAVE",null,null,bad)).status,String.valueOf(bad));
        assertEquals(views0,db.count("SELECT COUNT(*) FROM stat_view"));
        long mine=save(RI,null,null,"Ingresos por canal",definition());
        assertEquals(StatCuts.json(definition()),s(db.one("SELECT definition_json FROM stat_view WHERE id=?",mine),"definition_json"),"JSON canonico");
        assertTrue(visible(RI).contains(mine));assertFalse(visible(GI).contains(mine),"una vista personal no la ven otras cuentas");
        var listed=views.list(access.actor(RI)).stream().filter(x->((Number)x.get("id")).longValue()==mine).findFirst().orElseThrow();
        assertEquals(definition().get("measure"),obj(listed.get("definition")).get("measure"));assertEquals(true,listed.get("mine"));assertEquals(false,listed.get("can_share"));
        // Solo el dueno edita, con revision; sin cambios no escribe.
        assertEquals(403,assertThrows(Problem.class,()->save(GI,mine,0L,"Otra",definition())).status);
        assertEquals(409,assertThrows(Problem.class,()->save(RI,mine,7L,"Otra",definition())).status);
        assertEquals(409,assertThrows(Problem.class,()->save(RI,mine,0L,"Ingresos por canal",definition())).status);
        save(RI,mine,0L,"Ingresos por canal (CP)",definition());
        assertEquals(1,revision(mine));
        // Compartir exige GESTION_ESTADISTICA.
        assertEquals(403,assertThrows(Problem.class,()->run(RI,"STAT_VIEW_SHARE",mine,1L,map("shared",true))).status);
        long shared=save(GI,null,null,"Vista institucional",definition());
        assertEquals(400,assertThrows(Problem.class,()->run(GI,"STAT_VIEW_SHARE",shared,0L,map("shared","si"))).status);
        run(GI,"STAT_VIEW_SHARE",shared,0L,map("shared",true));
        var audit=db.one("SELECT note,resource_type FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action='VISTA_COMPARTIDA')");
        assertEquals("Vista "+shared,s(audit,"note"));assertEquals("SISTEMA",s(audit,"resource_type"));
        assertEquals(409,assertThrows(Problem.class,()->run(GI,"STAT_VIEW_SHARE",shared,1L,map("shared",true))).status,"ya compartida");
        // La ve toda cuenta con REPORTES (tambien de una sede), sin poder editarla ni borrarla.
        for(String who:List.of(RI,R6))assertTrue(visible(who).contains(shared),who);
        var seen=views.list(access.actor(R6)).stream().filter(x->((Number)x.get("id")).longValue()==shared).findFirst().orElseThrow();
        assertEquals(false,seen.get("mine"));assertEquals(false,seen.get("can_edit"));
        assertEquals(403,assertThrows(Problem.class,()->run(R6,"STAT_VIEW_DELETE",shared,1L,Map.of())).status);
        assertEquals(403,assertThrows(Problem.class,()->save(R6,shared,1L,"Mia",definition())).status);
        // Cada cuenta la ejecuta con su propio alcance.
        assertNotNull(engine.query(access.actor(R6),obj(seen.get("definition"))).get("cells"));
        // Una vista que apunta a un corte que la cuenta no ve: compartida se lista, pero la consulta responde 403.
        long cut=cut(GI,null,map("type","CUENTA_PUBLICA","from","2025-07-01"));
        var overCut=new LinkedHashMap<>(definition());overCut.put("cut_id",cut);
        assertEquals(403,assertThrows(Problem.class,()->save(R6,null,null,"Corte ajeno",overCut)).status,"no guarda una consulta que no puede hacer");
        long cutView=save(GI,null,null,"Sobre el corte",overCut);
        run(GI,"STAT_VIEW_SHARE",cutView,0L,map("shared",true));
        var listedCut=views.list(access.actor(R6)).stream().filter(x->((Number)x.get("id")).longValue()==cutView).findFirst().orElseThrow();
        assertEquals(403,assertThrows(Problem.class,()->engine.query(access.actor(R6),obj(listedCut.get("definition")))).status);
        assertNotNull(engine.query(access.actor(RI),obj(listedCut.get("definition"))).get("cells"));
        // Dejar de compartir y borrar la propia.
        run(GI,"STAT_VIEW_SHARE",shared,1L,map("shared",false));
        assertEquals("Vista "+shared,s(db.one("SELECT note FROM audit_event WHERE id=(SELECT MAX(id) FROM audit_event WHERE action='VISTA_NO_COMPARTIDA')"),"note"));
        assertFalse(visible(R6).contains(shared));
        assertEquals(409,assertThrows(Problem.class,()->run(RI,"STAT_VIEW_DELETE",mine,0L,Map.of())).status);
        run(RI,"STAT_VIEW_DELETE",mine,1L,Map.of());
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_view WHERE id=?",mine));
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE action IN ('VISTA_COMPARTIDA','VISTA_NO_COMPARTIDA') AND note LIKE '%institucional%'"),"la auditoria no lleva el titulo");
    }
}
