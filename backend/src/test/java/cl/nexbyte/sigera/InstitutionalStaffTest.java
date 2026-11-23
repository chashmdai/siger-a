package cl.nexbyte.sigera;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Inicializacion institucional con funcionarios y funciones reales en el mismo archivo (instancia de validacion o
 * produccion sin datos de prueba): cuentas con clave temporal y cambio obligatorio, funciones con su fundamento y las
 * mismas restricciones que GRANT_CREATE y PROFILE_SET (una jefatura por ambito, varios distribuidores, TI como funcion
 * unica; AUTORIDAD suprimida).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_institucional_staff;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class InstitutionalStaffTest {
    static final Path DIR=Path.of("target","institucional-staff-"+UUID.randomUUID());
    // Sedes de prueba (D7-D8): una activa con su Coordinacion como DIRECCION y otra proyectada con dotacion preparada.
    // Cobertura territorial inicial (0.7.0, D19): adicional en ambas sedes (la de la proyectada queda preparada) y del nivel central.
    static final String SITES="{\"codigo\":\"REG-SUR\",\"nombre\":\"Sede de prueba Sur\",\"unidad\":\"REG\",\"sede\":\"SEDE-SUR\",\"regional\":true,\"region\":\"10\",\"cobertura\":[\"11\",\"14\"]},"
        +"{\"codigo\":\"REG-NORTE\",\"nombre\":\"Sede de prueba Norte\",\"unidad\":\"REG\",\"sede\":\"SEDE-NORTE\",\"regional\":true,\"region\":\"15\",\"estado\":\"PROYECTADA\",\"apertura\":\"2027-03-01\",\"cobertura\":[\"01\"]}";
    static final String SITE_STAFF="{\"usuario\":\"coord.sur\",\"nombre\":\"Coordinacion Sur\",\"ambito\":\"REG-SUR\",\"funciones\":[{\"funcion\":\"DIRECCION\",\"ambito\":\"REG-SUR\"},{\"funcion\":\"PROFESIONAL\",\"ambito\":\"REG-SUR\"}]},"
        +"{\"usuario\":\"prep.norte\",\"nombre\":\"Dotacion Norte\",\"ambito\":\"REG-NORTE\",\"funciones\":[{\"funcion\":\"DIRECCION\",\"ambito\":\"REG-NORTE\"}]},";
    static final String STAFF=InstitutionalBootstrapTest.VALID.replace("{\"fundamento\"","{\"coberturaNivelCentral\":[\"13\"],\"fundamento\"").replace("\"sede\":\"CENTRAL\"}],","\"sede\":\"CENTRAL\"},"+SITES+"],").replace("\"ambito\":\"UGI\"}}","\"ambito\":\"UGI\"},"
        +"\"funcionarios\":["+SITE_STAFF+"{\"usuario\":\"jefa.req\",\"nombre\":\"Jefa REQ\",\"correo\":\"jefa@defensorianinez.cl\",\"ambito\":\"UPR-REQ\",\"funciones\":[{\"funcion\":\"JEFATURA\",\"ambito\":\"UPR-REQ\"},{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]},"
        +"{\"usuario\":\"distribuidora\",\"nombre\":\"Distribuidora\",\"ambito\":\"UPR-REQ\",\"funciones\":[{\"funcion\":\"DISTRIBUIDOR\"},{\"funcion\":\"ADMINISTRATIVO\",\"ambito\":\"UPR-REQ\"}]},"
        +"{\"usuario\":\"profesional.req\",\"nombre\":\"Profesional REQ\",\"ambito\":\"UPR-REQ\",\"funciones\":[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}");
    @DynamicPropertySource static void file(DynamicPropertyRegistry r)throws Exception{
        Files.createDirectories(DIR);Files.writeString(DIR.resolve("estructura.json"),STAFF);
        r.add("siger.bootstrap-file",()->DIR.resolve("estructura.json").toString());
        r.add("siger.data",()->DIR.resolve("data").toString());
    }
    @Autowired Db db;@Autowired Bootstrap bootstrap;@Autowired Workflow flow;@Autowired Access access;

    @Test void funcionariosConFuncionesRealesSinDatosDePrueba()throws Exception{
        assertEquals(6,db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE"));
        assertEquals(6,db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE AND must_change=TRUE"));
        long jefa=n(db.one("SELECT id FROM staff WHERE username='jefa.req'"),"id"),scope=n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='JEFATURA' AND scope_id=?",jefa,scope));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='DISTRIBUIDOR' AND scope_id IS NULL"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE basis NOT LIKE 'INICIALIZACION INSTITUCIONAL.%'"));
        assertEquals(7,Files.readAllLines(DIR.resolve("data").resolve("bootstrap-usuarios.csv")).size());
        // Sedes: la activa rige con su apertura; la proyectada queda deshabilitada y su dotacion preparada no rige aun.
        var sur=db.one("SELECT * FROM org_scope WHERE code='REG-SUR'");var norte=db.one("SELECT * FROM org_scope WHERE code='REG-NORTE'");
        assertEquals("10",s(sur,"region_code"));assertEquals("ACTIVA",s(sur,"state"));assertTrue(b(sur,"enabled"));assertNotNull(sur.get("opens_ms"));
        assertEquals("PROYECTADA",s(norte,"state"));assertFalse(b(norte,"enabled"));
        assertEquals(java.time.LocalDate.of(2027,3,1).atStartOfDay(java.time.ZoneId.of("America/Santiago")).toInstant().toEpochMilli(),n(norte,"opens_ms"));
        assertEquals("ACTIVA",s(db.one("SELECT state FROM org_scope WHERE code='UGI'"),"state"));assertNull(db.one("SELECT region_code FROM org_scope WHERE code='UGI'").get("region_code"));
        assertTrue(access.actor("coord.sur").head(n(sur,"id")));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=(SELECT id FROM staff WHERE username='prep.norte') AND role_code='DIRECCION'"));
        assertFalse(access.actor("prep.norte").head(n(norte,"id")));
        assertEquals(0,db.count("SELECT COUNT(*) FROM communication"));
        // Cobertura inicial: periodos abiertos a nombre de la TI inicial con el fundamento del archivo, en el orden del catalogo.
        long tiId=n(db.one("SELECT id FROM staff WHERE username='ti.institucional'"),"id");
        assertEquals(4,db.count("SELECT COUNT(*) FROM territorial_coverage WHERE until_ms IS NULL AND from_by=? AND from_basis LIKE 'INICIALIZACION INSTITUCIONAL.%'",tiId));
        var territory=Territory.load(db);
        assertEquals(List.of("14","11"),territory.coverage(n(sur,"id")));assertEquals(List.of("01"),territory.coverage(n(norte,"id")));
        assertEquals(List.of("13"),territory.central());
        assertEquals(List.of("10","14","11"),territory.attendedBy(n(sur,"id")));
        assertTrue(territory.activeAt("01").isEmpty());assertEquals(List.of("REG-NORTE"),territory.projectedAt("01"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM territorial_coverage WHERE scope_id=(SELECT id FROM org_scope WHERE code='UGI')"));
        // Flujo real con datos ficticios: la distribuidora registra una COM y la jefatura la recibe (reglas existentes).
        db.update("UPDATE staff SET must_change=FALSE WHERE technical=FALSE");
        long com=flow.run("distribuidora",new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,Map.of("scope_id",scope,"channel","TELEFONO",
            "received_at",java.time.Instant.now().minusSeconds(60).toString(),"subject","Consulta ficticia de validacion","body","Datos ficticios"))).id();
        assertTrue(s(db.one("SELECT folio FROM communication WHERE id=?",com),"folio").endsWith("-000001"));
    }

    @Test void funcionesInvalidasSeRechazanAntesDeEscribir()throws Exception{
        long before=db.count("SELECT COUNT(*) FROM staff");
        String twoHeads=STAFF.replace("{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","{\"funcion\":\"JEFATURA\",\"ambito\":\"UPR-REQ\"}]}]}");
        String tiCombined=STAFF.replace("[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","[{\"funcion\":\"TI\"},{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}");
        String tiWithScope=STAFF.replace("[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","[{\"funcion\":\"TI\",\"ambito\":\"UGI\"}]}]}");
        String sameTwice=STAFF.replace("[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"},{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}");
        String authority=STAFF.replace("[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","[{\"funcion\":\"AUTORIDAD\",\"ambito\":\"UPR-REQ\"}]}]}");
        String repeated=STAFF.replace("\"usuario\":\"profesional.req\"","\"usuario\":\"jefa.req\"");
        String unknownScope=STAFF.replace("\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","\"funcion\":\"PROFESIONAL\",\"ambito\":\"NO-EXISTE\"}]}]}");
        String centralWithScope=STAFF.replace("{\"funcion\":\"DISTRIBUIDOR\"}","{\"funcion\":\"DISTRIBUIDOR\",\"ambito\":\"UPR-REQ\"}");
        String headInSite=STAFF.replace("{\"funcion\":\"DIRECCION\",\"ambito\":\"REG-SUR\"}","{\"funcion\":\"JEFATURA\",\"ambito\":\"REG-SUR\"}");
        String siteWithoutRegion=STAFF.replace(",\"region\":\"10\"","");
        String unknownRegion=STAFF.replace("\"region\":\"10\"","\"region\":\"99\"");
        String closedSite=STAFF.replace("\"estado\":\"PROYECTADA\"","\"estado\":\"CERRADA\"");
        String badOpening=STAFF.replace("2027-03-01","2027-13-01");
        String centralWithRegion=STAFF.replace("\"unidad\":\"UGI\",\"sede\":\"CENTRAL\"","\"unidad\":\"UGI\",\"sede\":\"CENTRAL\",\"region\":\"13\"");
        // Cobertura (0.7.0, D19): mismas reglas que SCOPE_COVERAGE y CENTRAL_COVERAGE.
        String coverageWithOwnRegion=STAFF.replace("\"cobertura\":[\"11\",\"14\"]","\"cobertura\":[\"11\",\"10\"]");
        String coverageRepeated=STAFF.replace("\"cobertura\":[\"11\",\"14\"]","\"cobertura\":[\"11\",\"11\"]");
        String coverageInvalid=STAFF.replace("\"cobertura\":[\"11\",\"14\"]","\"cobertura\":[\"99\"]");
        String coverageNotList=STAFF.replace("\"cobertura\":[\"11\",\"14\"]","\"cobertura\":\"11\"");
        String coverageNotText=STAFF.replace("\"cobertura\":[\"11\",\"14\"]","\"cobertura\":[11]");
        String coverageInCentral=STAFF.replace("\"unidad\":\"UGI\",\"sede\":\"CENTRAL\"","\"unidad\":\"UGI\",\"sede\":\"CENTRAL\",\"cobertura\":[\"13\"]");
        String centralInvalid=STAFF.replace("\"coberturaNivelCentral\":[\"13\"]","\"coberturaNivelCentral\":[\"99\"]");
        String centralRepeated=STAFF.replace("\"coberturaNivelCentral\":[\"13\"]","\"coberturaNivelCentral\":[\"13\",\"13\"]");
        String centralNotList=STAFF.replace("\"coberturaNivelCentral\":[\"13\"]","\"coberturaNivelCentral\":\"13\"");
        int i=0;
        for(String bad:List.of(twoHeads,tiCombined,tiWithScope,sameTwice,authority,repeated,unknownScope,centralWithScope,
                               headInSite,siteWithoutRegion,unknownRegion,closedSite,badOpening,centralWithRegion,
                               coverageWithOwnRegion,coverageRepeated,coverageInvalid,coverageNotList,coverageNotText,coverageInCentral,centralInvalid,centralRepeated,centralNotList)){
            assertNotEquals(STAFF,bad,"caso "+i);
            Path f=DIR.resolve("invalido-"+(i++)+".json");Files.writeString(f,bad);
            var e=assertThrows(IllegalStateException.class,()->bootstrap.institutional(f));
            assertTrue(e.getMessage().startsWith("Archivo de inicializacion institucional invalido"),e.getMessage());
        }
        assertEquals(before,db.count("SELECT COUNT(*) FROM staff"));
        // Desde 0.5.0 (D3, D2): varios distribuidores y un funcionario solo con TI son validos; la validacion completa
        // pasa y se detiene ante el archivo de accesos ya creado en esta instancia.
        String accepted=STAFF.replace("[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","[{\"funcion\":\"DISTRIBUIDOR\"}]},"
            +"{\"usuario\":\"segunda.ti\",\"nombre\":\"Segunda TI\",\"ambito\":\"UGI\",\"funciones\":[{\"funcion\":\"TI\"}]}]}");
        Path ok=DIR.resolve("valido-0.5.0.json");Files.writeString(ok,accepted);
        assertTrue(assertThrows(IllegalStateException.class,()->bootstrap.institutional(ok)).getMessage().startsWith("Existe un archivo de acceso anterior"));
        // El ejemplo que se entrega en el ZIP (deploy/config-ejemplo) es un archivo valido: la validacion completa pasa y
        // solo se detiene ante el archivo de accesos ya creado en esta instancia.
        var example=assertThrows(IllegalStateException.class,()->bootstrap.institutional(Path.of("../deploy/config-ejemplo/estructura-inicial.json")));
        assertTrue(example.getMessage().startsWith("Existe un archivo de acceso anterior"),example.getMessage());
    }
}
