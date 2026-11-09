package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;

/** COM externa normalizada en H2: Unicode, 30 personas, sin escrituras si es invalida y ficha solo para autorizados. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_external_com;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.formulario-trusted-keys="+ExternalOriginalTest.TRUST})
@ActiveProfiles("test")
class ExternalComIntegrationTest {
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired BoardQueries boards;

    long staff(String username,long scope,String role){
        long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES(?,?,'test-non-login',?,FALSE)",username,username,scope);
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,?,?,?)",id,role,scope,0,"PRUEBA AUTOMATICA");
        return id;
    }
    long count(String table){return db.count("SELECT COUNT(*) FROM "+table);}

    @Test void persisteNormalizacionUnicodeTreintaPersonasYFichaAutorizada(){
        db.update("INSERT INTO org_unit VALUES('EXC','Unidad externa COM')");
        long scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('EXC-A','Area externa A','EXC','A','CENTRAL')");
        long other=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('EXC-B','Area externa B','EXC','B','CENTRAL')");
        staff("receptor.exc",scope,"ADMINISTRATIVO");
        staff("ajeno.exc",other,"PROFESIONAL");

        // Documento incompatible con el contrato (dos presentadores), aun firmado: ninguna escritura.
        var bad=cuVulneration();bad.withArrayProperty("personas").add(((ObjectNode)cuVulneration().path("personas").get(0)).deepCopy().put("id","otro"));
        var before=List.of(count("communication"),count("communication_original"),count("external_person"),count("external_attribute"),count("external_field"),count("audit_event"));
        assertEquals(400,assertThrows(Problem.class,()->flow.conserveExternal("receptor.exc",scope,sign(bad))).status);
        assertEquals(before,List.of(count("communication"),count("communication_original"),count("external_person"),count("external_attribute"),count("external_field"),count("audit_event")));

        // ClaveUnica con tildes, discrepancia SRCeI en persona relacionada y 30 personas (sobre el limite manual de 12).
        var c=cuVulneration();
        ((ObjectNode)c.withArrayProperty("personas").get(2)).withObjectProperty("atributos").set("primerApellido",attr("Muñoz",DECL,NOCONTR));
        ((ObjectNode)c.withArrayProperty("personas").get(2)).withObjectProperty("discrepancias").set("primerApellido",attr("MUÑOZ ÑANCO",SRCEI,CONTR));
        for(int i=0;i<26;i++)c.withArrayProperty("personas").add(person("relacionado-"+(i+2),"ADULTO_RELACIONADO",false,null,null,"primerNombre",attr("Persona "+i,DECL,NOCONTR)));
        long com=flow.conserveExternal("receptor.exc",scope,sign(c)).id();

        var row=db.one("SELECT * FROM communication WHERE id=?",com);
        assertEquals("María José De los Ángeles Del Río González",s(row,"presenter"));
        assertEquals("12345678-5",s(row,"identity_document"));
        assertEquals("Avenida Irarrázaval 1234, depto. 5, Ñuñoa, Metropolitana de Santiago",s(row,"presenter_domicile"));
        assertEquals("Relato sintético con tildes: niño, señal, acción.",s(row,"body"));
        assertEquals("Formulario web adulto · Vulneración de derechos",s(row,"subject"));
        assertEquals(30,db.count("SELECT COUNT(*) FROM external_person WHERE communication_id=?",com));
        assertEquals(0,db.count("SELECT COUNT(*) FROM communication_person WHERE communication_id=?",com));
        var related=db.one("SELECT * FROM external_person WHERE communication_id=? AND person_key='relacionado-1'",com);
        assertEquals("ADULTO_RELACIONADO",s(related,"person_role"));assertEquals("Rosa Muñoz",s(related,"given_names")+" "+s(related,"surnames"));
        var disc=db.one("SELECT * FROM external_attribute WHERE person_id=? AND discrepancy=TRUE",n(related,"id"));
        assertEquals("MUÑOZ ÑANCO",s(disc,"value_text"));assertEquals("SRCEI",s(disc,"source"));assertEquals("CONTRASTADO_EN_FUENTE",s(disc,"level_code"));
        assertEquals("Escuela Básica Sintética",s(db.one("SELECT value_text FROM external_field WHERE communication_id=? AND field_group='INSTITUCION' AND field_key='nombre'",com),"value_text"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM solicitation WHERE communication_id=?",com));
        assertEquals(0,db.count("SELECT COUNT(*) FROM requirement WHERE communication_id=?",com));
        // La auditoria no lleva datos personales.
        assertEquals("Origen FORMULARIO_PUBLICO_DDN "+c.path("id").asString(),s(db.one("SELECT note FROM audit_event WHERE action='COM_ORIGINAL_CONSERVADO' AND resource_id=?",com),"note"));

        // Ficha: autorizado recibe la proyeccion (sin el JSON original); quien no tiene acceso a la COM, 403.
        var detail=queries.detail(access.actor("receptor.exc"),"COM",com);
        @SuppressWarnings("unchecked") var external=(Map<String,Object>)detail.get("external");
        assertNotNull(external);
        assertFalse(((Map<?,?>)external.get("original")).containsKey("document"));
        assertFalse(detail.toString().contains("\"custodia\""));
        assertEquals(30,((List<?>)external.get("persons")).size());
        var presenter=(Map<?,?>)((List<?>)external.get("persons")).getFirst();
        assertEquals("CLAVE_UNICA_USERINFO",presenter.get("run_source"));assertEquals(true,presenter.get("run_valid"));
        assertEquals(403,assertThrows(Problem.class,()->queries.detail(access.actor("ajeno.exc"),"COM",com)).status);
        // Bandeja: no expone presentador, identificacion ni contacto.
        var page=boards.page(access.actor("receptor.exc"),"com",0,20,"",null,null);
        assertFalse(page.toString().contains("María José"));
        assertFalse(page.toString().contains("12345678"));

        // COM_CREATE manual sigue igual (limite y tabla propios).
        long manual=flow.run("receptor.exc",new Workflow.Command(UUID.randomUUID(),"COM_CREATE",null,null,Map.of("scope_id",scope,"channel","PRESENCIAL",
            "received_at",Instant.now().minusSeconds(30).toString(),"subject","Ingreso manual","body","Relato manual","presenter","Ana Pérez"))).id();
        assertEquals("Ana Pérez",s(db.one("SELECT presenter FROM communication WHERE id=?",manual),"presenter"));
        assertNull(queries.detail(access.actor("receptor.exc"),"COM",manual).get("external"));
    }
}
