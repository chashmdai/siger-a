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
 * mismas restricciones que GRANT_CREATE (una jefatura por ambito, una distribucion central).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_institucional_staff;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class InstitutionalStaffTest {
    static final Path DIR=Path.of("target","institucional-staff-"+UUID.randomUUID());
    static final String STAFF=InstitutionalBootstrapTest.VALID.replace("\"ambitos\":[\"*\"]}}","\"ambitos\":[\"*\"]},"
        +"\"funcionarios\":[{\"usuario\":\"jefa.req\",\"nombre\":\"Jefa REQ\",\"correo\":\"jefa@defensorianinez.cl\",\"ambito\":\"UPR-REQ\",\"funciones\":[{\"funcion\":\"JEFATURA\",\"ambito\":\"UPR-REQ\"},{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]},"
        +"{\"usuario\":\"distribuidora\",\"nombre\":\"Distribuidora\",\"ambito\":\"UPR-REQ\",\"funciones\":[{\"funcion\":\"DISTRIBUIDOR\"},{\"funcion\":\"ADMINISTRATIVO\",\"ambito\":\"UPR-REQ\"}]},"
        +"{\"usuario\":\"profesional.req\",\"nombre\":\"Profesional REQ\",\"ambito\":\"UPR-REQ\",\"funciones\":[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}");
    @DynamicPropertySource static void file(DynamicPropertyRegistry r)throws Exception{
        Files.createDirectories(DIR);Files.writeString(DIR.resolve("estructura.json"),STAFF);
        r.add("siger.bootstrap-file",()->DIR.resolve("estructura.json").toString());
        r.add("siger.data",()->DIR.resolve("data").toString());
    }
    @Autowired Db db;@Autowired Bootstrap bootstrap;@Autowired Workflow flow;

    @Test void funcionariosConFuncionesRealesSinDatosDePrueba()throws Exception{
        assertEquals(5,db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE"));
        assertEquals(5,db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE AND must_change=TRUE"));
        long jefa=n(db.one("SELECT id FROM staff WHERE username='jefa.req'"),"id"),scope=n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='JEFATURA' AND scope_id=?",jefa,scope));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='DISTRIBUIDOR' AND scope_id IS NULL"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE basis NOT LIKE 'INICIALIZACION INSTITUCIONAL.%'"));
        assertEquals(6,Files.readAllLines(DIR.resolve("data").resolve("bootstrap-usuarios.csv")).size());
        assertEquals(0,db.count("SELECT COUNT(*) FROM communication"));
        // Flujo real con datos ficticios: la distribuidora registra una COM y la jefatura la recibe (reglas existentes).
        db.update("UPDATE staff SET must_change=FALSE WHERE technical=FALSE");
        long com=flow.run("distribuidora",new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,Map.of("scope_id",scope,"channel","TELEFONO",
            "received_at",java.time.Instant.now().minusSeconds(60).toString(),"subject","Consulta ficticia de validacion","body","Datos ficticios"))).id();
        assertTrue(s(db.one("SELECT folio FROM communication WHERE id=?",com),"folio").endsWith("-000001"));
    }

    @Test void funcionesInvalidasSeRechazanAntesDeEscribir()throws Exception{
        long before=db.count("SELECT COUNT(*) FROM staff");
        String twoHeads=STAFF.replace("{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","{\"funcion\":\"JEFATURA\",\"ambito\":\"UPR-REQ\"}]}]}");
        String twoDistributors=STAFF.replace("[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","[{\"funcion\":\"DISTRIBUIDOR\"}]}]}");
        String authority=STAFF.replace("[{\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","[{\"funcion\":\"AUTORIDAD\",\"ambito\":\"UPR-REQ\"}]}]}");
        String repeated=STAFF.replace("\"usuario\":\"profesional.req\"","\"usuario\":\"jefa.req\"");
        String unknownScope=STAFF.replace("\"funcion\":\"PROFESIONAL\",\"ambito\":\"UPR-REQ\"}]}]}","\"funcion\":\"PROFESIONAL\",\"ambito\":\"NO-EXISTE\"}]}]}");
        String centralWithScope=STAFF.replace("{\"funcion\":\"DISTRIBUIDOR\"}","{\"funcion\":\"DISTRIBUIDOR\",\"ambito\":\"UPR-REQ\"}");
        int i=0;
        for(String bad:List.of(twoHeads,twoDistributors,authority,repeated,unknownScope,centralWithScope)){
            assertNotEquals(STAFF,bad,"caso "+i);
            Path f=DIR.resolve("invalido-"+(i++)+".json");Files.writeString(f,bad);
            var e=assertThrows(IllegalStateException.class,()->bootstrap.institutional(f));
            assertTrue(e.getMessage().startsWith("Archivo de inicializacion institucional invalido"),e.getMessage());
        }
        assertEquals(before,db.count("SELECT COUNT(*) FROM staff"));
        // El ejemplo que se entrega en el ZIP (deploy/config-ejemplo) es un archivo valido: la validacion completa pasa y
        // solo se detiene ante el archivo de accesos ya creado en esta instancia.
        var example=assertThrows(IllegalStateException.class,()->bootstrap.institutional(Path.of("../deploy/config-ejemplo/estructura-inicial.json")));
        assertTrue(example.getMessage().startsWith("Existe un archivo de acceso anterior"),example.getMessage());
    }
}
