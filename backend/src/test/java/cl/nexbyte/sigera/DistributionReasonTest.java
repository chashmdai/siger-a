package cl.nexbyte.sigera;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_observacion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-observacion-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class DistributionReasonTest extends StatFixture {
    @Test void cadaActoDeDistribucionAdmiteOmitirElFundamento(){
        long com=manual(Map.of());
        long sol=on(DIST,"COM_ROUTE","communication",com,Map.of("scope_id",scope("UPR-REQ"))).id();
        assertEmptyClassification(sol);
        assertEquals("",history("SOL_DISTRIBUIDA","SOL",sol));

        long oldScope=scope("UPR-REQ"),newScope=scope("UPD");
        on(HEAD,"SOL_ROUTE","solicitation",sol,Map.of("scope_id",newScope));
        assertEmptyClassification(sol);
        assertEquals("Destino anterior "+oldScope+".",history("SOL_REDIRIGIDA","SOL",sol));
        on("direccion.upd","SOL_RETURN","solicitation",sol,Map.of());
        assertEmptyClassification(sol);
        assertEquals("",history("SOL_DEVUELTA_DISTRIBUCION","SOL",sol));
        on(DIST,"SOL_ROUTE","solicitation",sol,Map.of("scope_id",oldScope));
        assertEmptyClassification(sol);
        assertEquals("Destino anterior "+newScope+".",history("SOL_REDIRIGIDA","SOL",sol));
        long req=on(HEAD,"SOL_ASSUME","solicitation",sol,Map.of("responsible_id",user(PROF))).id();
        assertEmptyClassification(sol);
        assertEquals("",history("SOL_ASUMIDA","SOL",sol));

        long own=com(HEAD,"UPR-REQ","PRESENCIAL",ago(300),Map.of(),null);
        on(HEAD,"COM_ASSUME","communication",own,Map.of("responsible_id",user(PROF)));
        assertEmptyClassification(sol(own));
        assertEquals("",history("ASUNCION_INGRESO_PROPIO","SOL",sol(own)));

        long related=manual(Map.of());
        on(DIST,"COM_LINK","communication",related,Map.of("folio",folio(req)));
        assertEquals("",s(db.one("SELECT reason FROM communication_link WHERE communication_id=?",related),"reason"));
        assertEquals("REQ "+req+".",history("COM_HIJA_PROPUESTA","COM",related));
    }

    @Test void observacionOpcionalConservaValidacionYTexto(){
        assertEquals("",new Input(Map.of()).optionalReason());
        assertEquals("",new Input(Map.of("reason"," ")).optionalReason());
        assertEquals("Nota",new Input(Map.of("reason"," Nota ")).optionalReason());
        assertEquals(12000,new Input(Map.of("reason","a".repeat(12000))).optionalReason().length());
        assertEquals(400,assertThrows(Problem.class,()->new Input(Map.of("reason","a".repeat(12001))).optionalReason()).status);
        assertEquals(400,assertThrows(Problem.class,()->new Input(Map.of("reason",33)).optionalReason()).status);
        long com=manual(Map.of()),sol=route(com,scope("UPR-REQ"));
        on(HEAD,"SOL_RETURN","solicitation",sol,Map.of("reason"," Observación sintética "));
        assertEquals("Observación sintética",history("SOL_DEVUELTA_DISTRIBUCION","SOL",sol));
    }

    private void assertEmptyClassification(long sol){
        assertEquals("",db.one("SELECT classification FROM solicitation WHERE id=?",sol).get("classification"));
    }

    private String history(String action,String kind,long id){
        return s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_type=? AND resource_id=? ORDER BY id DESC LIMIT 1",action,kind,id),"note");
    }
}
