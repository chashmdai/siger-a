package cl.nexbyte.sigera;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static cl.nexbyte.sigera.TerritorialDims.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_dimterritorio;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-dimterritorio-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class TerritorialDimsTest extends E07Fixture {
    @Autowired TerritorialDims dims;
    @Autowired PersonDims people;

    @Test void escenario5OrigenCoberturaYSedeConTransferencia(){
        long com=manual(Map.of("presenter",Map.of("first_name","Persona","region_code","07","commune","Talca"),
            "facts_region_code","13","facts_commune","Santiago",
            "persons",List.of(Map.of("role","NNA","first_name","Una","region_code","07","commune","Talca"),
                Map.of("role","NNA","first_name","Otra","region_code","06","commune","Rancagua"))));
        long site=scope("REG-DEMO");
        long received=n(db.one("SELECT received_ms FROM communication WHERE id=?",com),"received_ms");
        db.update("UPDATE org_scope SET region_code='06' WHERE id=?",site);
        db.update("INSERT INTO territorial_coverage(scope_id,region_code,from_ms,from_by,from_basis,until_ms) VALUES(?,'07',?,?,?,?)",
            site,received-100,user("ti"),"Periodo sintético",received+100);
        var initial=dims.read(com,INGRESO,null);
        assertNull(initial.get("scope_id"));
        assertEquals("07",s(initial,"requester_region"));
        assertEquals("07",s(initial,"origin_region"));
        assertEquals("Talca",s(initial,"requester_commune"));
        assertEquals("13",s(initial,"facts_region"));
        assertEquals("REG-DEMO",s(initial,"coverage"));
        assertEquals(Set.of("06","07"),new HashSet<>(people.affected(com,VIGENTE,null).stream().map(p->s(p,"region_code")).toList()));
        assertEquals("SIN_ATENCION",s(db.one("SELECT "+TerritorialDims.coverageSql("'07'",String.valueOf(received+100))+" AS coverage"),"coverage"));
        assertNull(dims.read(com,HISTORICO,received-1).get("requester_region"));

        long req=assume(com);
        long first=scope("UPR-REQ");
        assertEquals(first,n(dims.read(com,INGRESO,null),"scope_id"));
        assertEquals(first,n(dims.read(com,REQ,null),"scope_id"));
        long upd=scope("UPD");
        on(HEAD,"FLOW_REQUEST","requirement",req,Map.of("kind","TRANSFERENCIA","target_scope",upd,"reason","Traslado de prueba"));
        long transfer=n(db.one("SELECT id FROM flow_request WHERE requirement_id=?",req),"id");
        on("direccion.upd","FLOW_DECIDE","flow_request",transfer,Map.of("decision","ACEPTADA","reason","Traslado aceptado"));
        long decision=n(db.one("SELECT decided_ms FROM flow_request WHERE id=?",transfer),"decided_ms");
        assertEquals(upd,n(dims.read(com,VIGENTE,null),"scope_id"));
        assertEquals(upd,n(dims.read(com,HISTORICO,decision),"scope_id"));
        assertEquals(first,n(dims.read(com,INGRESO,null),"scope_id"));
        assertEquals(first,n(dims.read(com,HISTORICO,decision-1),"scope_id"));
    }
}
