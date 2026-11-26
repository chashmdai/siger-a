package cl.nexbyte.sigera;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Adscripcion institucional (DI-13, gate E10): la sede regional u Oficina Nacional a la que se adscribio primero el
 * ingreso, con la misma regla temporal que SEDE_INICIAL, en los cuatro modos y en un corte. Matriz del plan con cuatro
 * ingresos computables registrados antes de t (2026-07-10 12:00 de Chile): A sin destino; B adscrito a un ambito
 * central; C adscrito a la sede A6 y transferido a A7 antes de t; D con dos NNA de las regiones 05 y 07, distribuido y
 * asumido en A5 despues de t (y despues del corte). Cifras escritas a mano.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_adscripcion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-adscripcion-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class StatAdscriptionTest extends StatFixture {
    static final String H5="cabeza.a5",P5="profesional.a5",H6="cabeza.a6",P6="profesional.a6",H7="cabeza.a7",P7="profesional.a7",GI="gest.a.inst",RI="rep.a.inst";
    static final String T="2026-07-10T12:00:00-04:00",DIM="ADSCRIPCION_INICIAL";

    Map<String,Long> adscription(String mode,Long cut){
        var body=map("measure","INGRESOS","rows",List.of(DIM),"period",period("MES","2026-07-01"),"time_mode",mode);
        if(mode.equals("FECHA"))body.put("as_of",T);
        if(cut!=null)body.put("cut_id",cut);
        var r=engine.query(access.actor(RI),body);
        var out=cells(r);
        assertEquals(4,grand(r),mode+": cada ingreso computable cuenta una vez");
        assertEquals(4L,out.values().stream().mapToLong(Long::longValue).sum(),mode+": el desglose suma el total (Oficina Nacional incluida)");
        for(var v:list(obj(r.get("values")).get(DIM)))assertFalse(s(v,"label").contains("Metropolitana"),String.valueOf(v));
        return out;
    }
    /** Hitos, SOL y REQ de una COM antes de t, en su orden original. */
    void before(long com,String iso){
        long base=ms(iso);
        db.update("UPDATE milestone SET at_ms=?+id WHERE communication_id=?",base,com);
        db.update("UPDATE solicitation SET created_ms=? WHERE communication_id=?",base,com);
        db.update("UPDATE requirement SET created_ms=? WHERE communication_id=?",base+1,com);
    }

    @Test void matrizDi13EnLosCuatroModosYEnUnCorte(){
        site("A5","05","ACTIVA");site("A6","06","ACTIVA");site("A7","07","ACTIVA");
        staff(H5,"DIRECCION@A5","PROFESIONAL@A5");staff(P5,"PROFESIONAL@A5");staff(H6,"DIRECCION@A6","PROFESIONAL@A6");staff(P6,"PROFESIONAL@A6");
        staff(H7,"DIRECCION@A7","PROFESIONAL@A7");staff(P7,"PROFESIONAL@A7");staff(GI,"REPORTES","GESTION_ESTADISTICA");staff(RI,"REPORTES");
        db.update("UPDATE staff SET must_change=FALSE");
        long a=com(P6,"A6","CORREO","2026-07-01T15:00:00Z",form("06"),null);
        long b=com(ADMIN,"UPR-REQ","CORREO","2026-07-02T15:00:00Z",form("13"),null);
        route(DIST,b,"UPR-REQ",null);
        long c=com(P6,"A6","TELEFONO","2026-07-03T15:00:00Z",form("06"),null);
        route(H6,c,"A6",null);
        long reqC=assume(H6,c,P6,null);
        on(H6,"FLOW_REQUEST","requirement",reqC,Map.of("kind","TRANSFERENCIA","target_scope",scope("A7"),"reason","Corresponde a A7"));
        on(H7,"FLOW_DECIDE","flow_request",last("flow_request"),Map.of("decision","ACEPTADA","reason","Aceptada"));
        var nna=Map.<String,Object>of("presenter",Map.of("first_name","Persona","region_code","05","commune",AdultIdentity.COMMUNES.get("05").getFirst()),
            "persons",List.of(Map.of("role","NNA","first_name","Una","region_code","05","commune",AdultIdentity.COMMUNES.get("05").getFirst()),
                Map.of("role","NNA","first_name","Dos","region_code","07","commune",AdultIdentity.COMMUNES.get("07").getFirst())));
        long d=com(P5,"A5","PRESENCIAL","2026-07-04T15:00:00Z",nna,null);
        int day=1;
        for(long x:List.of(a,b,c,d)){registered(x,"2026-07-0"+day+"T16:00:00Z");day++;}
        before(b,"2026-07-05T15:00:00Z");before(c,"2026-07-06T15:00:00Z");

        // A t y antes de distribuir D: 2 pendientes (A y D), Oficina Nacional (B) y A6 (C, primera adscripcion aunque se transfirio).
        var atT=of("A6",1,"OFICINA_NACIONAL",1,"PENDIENTE",2);
        assertEquals(atT,adscription("FECHA",null));
        assertEquals(atT,adscription("VIGENTE",null));
        assertEquals(atT,adscription("AL_INGRESO",null));
        assertEquals(of("A6",1,"PENDIENTE",3),adscription("AL_REQ",null),"al constituirse el REQ: solo C tiene REQ");
        long cut=cut(GI,null,period("MES","2026-07-01"));
        assertEquals(atT,adscription("VIGENTE",cut));

        // D se distribuye y se asume en A5 despues de t y del corte.
        route(H5,d,"A5",null);
        assume(H5,d,P5,null);
        assertEquals(atT,adscription("FECHA",null),"a t sigue pendiente");
        assertEquals(atT,adscription("VIGENTE",cut),"el corte no cambia");
        var now=of("A5",1,"A6",1,"OFICINA_NACIONAL",1,"PENDIENTE",1);
        assertEquals(now,adscription("VIGENTE",null));
        assertEquals(now,adscription("AL_INGRESO",null));
        assertEquals(of("A5",1,"A6",1,"PENDIENTE",2),adscription("AL_REQ",null));

        // Rotulos: Oficina Nacional es una sola categoria del nivel central; sin destino, pendiente de asignacion.
        var r=engine.query(access.actor(RI),map("measure","INGRESOS","rows",List.of(DIM),"period",period("MES","2026-07-01")));
        var labels=new LinkedHashMap<String,String>();for(var v:list(obj(r.get("values")).get(DIM)))labels.put(s(v,"key"),s(v,"label"));
        assertEquals("Oficina Nacional",labels.get("OFICINA_NACIONAL"));assertEquals("Pendiente de asignación",labels.get("PENDIENTE"));
        assertEquals("Sede sintetica A5",labels.get("A5"));
        assertEquals(List.of("A5","A6","OFICINA_NACIONAL","PENDIENTE"),new ArrayList<>(labels.keySet()));
        var dim=StatSource.DIMS.get(DIM);
        assertEquals(Map.of("OFICINA_NACIONAL","Oficina Nacional","PENDIENTE","Pendiente de asignación"),dim.options());
        assertFalse(dim.multiple());assertFalse(dim.label().contains("Metropolitana"));

        // La region del NNA afectado es otra dimension, multiple (no aditiva): D aporta 1 a 05 y 1 a 07, y una sola vez a A5.
        var nnaRegion=engine.query(access.actor(RI),map("measure","REQUERIMIENTOS_CON_VALOR","rows",List.of(DIM),"columns",List.of("REGION_NNA"),"period",period("MES","2026-07-01")));
        assertEquals(1L,cell(nnaRegion,List.of("A5"),List.of("05")).get("n"));assertEquals(1L,cell(nnaRegion,List.of("A5"),List.of("07")).get("n"));
        assertEquals(of("A5",1,"A6",1,"OFICINA_NACIONAL",1,"PENDIENTE",1),totals(nnaRegion,"rows","n"));
        assertTrue(warnings(nnaRegion).contains("PORCENTAJES_MULTIVALOR"));
        assertTrue(StatSource.DIMS.get("REGION_NNA").multiple());
        var stats=obj(queries.catalog(access.actor(RI)).get("stats"));
        assertEquals("MULTIPLE",list(stats.get("dimensions")).stream().filter(x->x.get("code").equals("REGION_NNA")).findFirst().orElseThrow().get("cardinality"));
        assertEquals("UNICA",list(stats.get("dimensions")).stream().filter(x->x.get("code").equals(DIM)).findFirst().orElseThrow().get("cardinality"));
    }
}
