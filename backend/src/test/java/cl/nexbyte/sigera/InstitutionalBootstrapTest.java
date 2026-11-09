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
 * Inicializacion institucional en H2: solo los ambitos del archivo del operador, una cuenta TI y una autoridad
 * habilitante con clave temporal; ninguna cuenta, sede ni dato de prueba. El modo de pruebas sigue en otras clases.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_institucional;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class InstitutionalBootstrapTest {
    static final Path DIR=Path.of("target","institucional-"+UUID.randomUUID());
    static final String VALID="""
        {"fundamento":"Resolucion exenta de prueba N 1 que designa TI y autoridad habilitante",
         "ambitos":[{"codigo":"UGI","nombre":"Gestion Institucional","unidad":"UGI","sede":"CENTRAL"},
                    {"codigo":"UPR-REQ","nombre":"Requerimientos","unidad":"UPR","area":"REQ","sede":"CENTRAL"},
                    {"codigo":"DEF","nombre":"Defensoria","unidad":"DEF","sede":"CENTRAL"}],
         "ti":{"usuario":"ti.institucional","nombre":"Persona TI","correo":"ti@defensorianinez.cl","ambito":"UGI"},
         "autoridad":{"usuario":"autoridad.institucional","nombre":"Persona Autoridad","ambito":"DEF","ambitos":["*"]}}
        """;
    @DynamicPropertySource static void file(DynamicPropertyRegistry r)throws Exception{
        Files.createDirectories(DIR);Files.writeString(DIR.resolve("estructura.json"),VALID);
        r.add("siger.bootstrap-file",()->DIR.resolve("estructura.json").toString());
        r.add("siger.data",()->DIR.resolve("data").toString());
    }
    @Autowired Db db;@Autowired Bootstrap bootstrap;@Autowired Workflow flow;

    @Test void instanciaSinDatosDePruebaConTiYAutoridadReales()throws Exception{
        assertEquals(2,db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE"));
        assertEquals(3,db.count("SELECT COUNT(*) FROM org_scope"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM staff WHERE LOWER(name) LIKE '%sintetic%' OR username IN ('distribucion','autoridad','ti')"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM org_scope WHERE code='REG-DEMO' OR LOWER(name) LIKE '%sintetic%'"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE basis LIKE '%SINTETICA%'"));
        for(String table:List.of("communication","requirement","case_record","substitution_designation","document_item"))assertEquals(0,db.count("SELECT COUNT(*) FROM "+table),table);
        var ti=db.one("SELECT * FROM staff WHERE username='ti.institucional'");
        assertTrue(b(ti,"must_change"));assertEquals("ti@defensorianinez.cl",s(ti,"email"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='TI' AND scope_id IS NULL",n(ti,"id")));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=?",n(ti,"id"))); // TI no recibe funciones sobre expedientes
        long authority=n(db.one("SELECT id FROM staff WHERE username='autoridad.institucional'"),"id");
        assertEquals(3,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='AUTORIDAD'",authority));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='CONFIGURACION_INSTITUCIONAL_INICIAL'"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code='INSTITUTIONAL_BOOTSTRAP'"));
        var csv=Files.readAllLines(DIR.resolve("data").resolve("bootstrap-usuarios.csv"));
        assertEquals(3,csv.size());assertTrue(csv.get(1).startsWith("ti.institucional,"));
        // Reinicio con el mismo archivo: no se repite ni se duplica nada.
        bootstrap.run(null);
        assertEquals(2,db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE"));
        // Funciona con las reglas existentes: la autoridad concede funciones (tras cambiar su clave temporal).
        db.update("UPDATE staff SET must_change=FALSE WHERE technical=FALSE");
        long scope=n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");
        long person=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES('funcionaria.req','Funcionaria','x',?,FALSE)",scope);
        flow.run("autoridad.institucional",new Workflow.Command(UUID.randomUUID(),"GRANT_CREATE",null,null,
            Map.of("scope_id",scope,"user_id",person,"role","PROFESIONAL","from_at",java.time.Instant.now().minusSeconds(60).toString(),"reason","Designacion de prueba")));
        assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment WHERE user_id=? AND role_code='PROFESIONAL'",person));
        assertEquals(403,assertThrows(Problem.class,()->flow.run("ti.institucional",new Workflow.Command(UUID.randomUUID(),"GRANT_CREATE",null,null,
            Map.of("scope_id",scope,"user_id",person,"role","JEFATURA","from_at",java.time.Instant.now().toString(),"reason","x")))).status);
    }

    @Test void archivoInvalidoSeRechazaSinEscribir()throws Exception{
        long before=db.count("SELECT COUNT(*) FROM org_scope");
        List<String> invalid=List.of(
            VALID.replace("\"fundamento\":\"Resolucion exenta de prueba N 1 que designa TI y autoridad habilitante\",",""),
            VALID.replace("\"unidad\":\"UPR\"","\"unidad\":\"XYZ\""),
            VALID.replace("\"usuario\":\"autoridad.institucional\"","\"usuario\":\"ti.institucional\""),
            VALID.replace("\"ambitos\":[\"*\"]","\"ambitos\":[\"NO-EXISTE\"]"),
            VALID.replace("\"codigo\":\"DEF\"","\"codigo\":\"UGI\""),
            VALID.replace("\"usuario\":\"ti.institucional\"","\"usuario\":\"sistema:formulario\""),
            VALID.replace("{\"fundamento\"","{\"fundamento\":\"duplicado\",\"fundamento\""),
            "no es json");
        for(int i=0;i<invalid.size();i++){
            Path f=DIR.resolve("invalido-"+i+".json");Files.writeString(f,invalid.get(i));
            var e=assertThrows(IllegalStateException.class,()->bootstrap.institutional(f),"caso "+i);
            assertTrue(e.getMessage().startsWith("Archivo de inicializacion institucional"),e.getMessage());
        }
        // Archivo valido con BOM UTF-8 (Windows PowerShell 5.1): se acepta; aqui se detiene despues, ante el archivo de
        // accesos ya existente, sin volver a crear nada.
        Path bom=DIR.resolve("con-bom.json");
        Files.write(bom,(new String(new char[]{0xFEFF})+VALID).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var e=assertThrows(IllegalStateException.class,()->bootstrap.institutional(bom));
        assertTrue(e.getMessage().startsWith("Existe un archivo de acceso anterior"),e.getMessage());
        assertEquals(before,db.count("SELECT COUNT(*) FROM org_scope"));
        // Datos de prueba e inicializacion institucional son excluyentes.
        var both=new Bootstrap(db,null,null,null,DIR.resolve("data").toString(),true,false,DIR.resolve("estructura.json").toString());
        assertThrows(IllegalStateException.class,()->both.run(null));
    }
}
