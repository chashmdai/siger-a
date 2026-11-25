package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * Indice de personas (0.5.0, D5, V12, gate G07): normalizacion de nombres, RUN y documentos; una referencia por
 * persona de cada fuente (COM manual y sus personas, ficha vigente, Formulario), refresco al guardar la ficha, reenvio
 * del Formulario sin duplicar y carga inicial idempotente que reproduce lo que escriben los comandos. G07b: la persona
 * senalada y "otra persona" no se indexan, el nombre de preferencia de quien presenta en la ficha si, y el marcador
 * nuevo reconstruye un indice construido por G07.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_personas;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-personas-${random.uuid}"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersonIndexTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    @Autowired Workflow flow;@Autowired Db db;@Autowired PersonIndex index;

    long scope(String code){return n(db.one("SELECT id FROM org_scope WHERE code=?",code),"id");}
    static String ago(long s){return Instant.now().minusSeconds(s).toString();}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    /** Referencias de una COM como texto comparable: fuente|rol|nombre mostrado|clave|RUN|DV|documento|autenticada|REQ. */
    List<String> refs(long com){
        return db.rows("SELECT * FROM person_reference WHERE communication_id=? ORDER BY id",com).stream().map(PersonIndexTest::line).toList();
    }
    static String line(Map<String,Object> r){
        return String.join("|",s(r,"source"),s(r,"person_role"),s(r,"display_name"),s(r,"name_key"),s(r,"run_number"),s(r,"run_dv"),s(r,"document_key"),String.valueOf(b(r,"authenticated")),s(r,"requirement_id"));
    }
    List<String> all(){
        return db.rows("SELECT * FROM person_reference ORDER BY communication_id,id").stream().map(r->s(r,"communication_id")+"#"+line(r)).toList();
    }

    @Test @Order(1) void normalizaNombresRunYDocumentos(){
        assertEquals("jose nunez perez hijo",PersonIndex.nameKey("  José   Ñúñez-PÉREZ, (hijo) "));
        assertEquals("maria jose de los angeles",PersonIndex.nameKey("MARÍA JOSÉ DE LOS ÁNGELES"));
        assertNull(PersonIndex.nameKey(" -- . "));
        for(String text:List.of("12.345.678-5","12345678-5","RUN 12.345.678-5","RUT: 12345678 - 5","012.345.678-5","12.345.678-5 (vigente)"))
            assertEquals(new PersonIndex.Identity("12345678","5",null),PersonIndex.parse(text),text);
        assertEquals(new PersonIndex.Identity("9876543","K",null),PersonIndex.parse(" 9.876.543 - k "));
        // DV que no cumple modulo 11: se indexa tal como se declaro (el buscador avisa).
        assertEquals(new PersonIndex.Identity("12345678","9",null),PersonIndex.parse("12.345.678-9"));
        // Sin guion y DV no se reconoce un RUN: queda como documento.
        assertEquals(new PersonIndex.Identity(null,null,"123456785"),PersonIndex.parse("123456785"));
        assertEquals(new PersonIndex.Identity(null,null,"AB123456"),PersonIndex.parse("Pasaporte AB-123.456"));
        assertEquals(new PersonIndex.Identity(null,null,"X99123"),PersonIndex.parse("Documento de identidad extranjero x99 123"));
        assertEquals(new PersonIndex.Identity(null,null,"AB123456"),PersonIndex.parse("AB12345-6"));
        assertEquals(new PersonIndex.Identity(null,null,"P4567"),PersonIndex.parse("N° P-4567"));
        for(String text:List.of("","No tiene","Sin documento","desconocido"))assertTrue(PersonIndex.parse(text).empty(),text);
        assertEquals(new PersonIndex.Identity("123","K",null),PersonIndex.run("000.123","k"));
        assertTrue(PersonIndex.run("0","5").empty());
    }

    @Test @Order(2) void cadaFuenteQuedaIndexadaYSeRefrescaAlGuardarLaFicha(){
        db.update("UPDATE staff SET must_change=FALSE");
        long upr=scope("UPR-REQ");

        // COM manual: quien presenta y las personas relacionadas; una persona sin nombre ni documento no genera referencia
        // y "otra persona" no se indexa (D5).
        // COM anterior a 0.8.0 (formato retirado en E05): sus datos historicos se insertan por SQL (LegacyCom).
        long manual=LegacyCom.create(flow,db,index,"distribucion",Map.of("scope_id",upr,"channel","TELEFONO","received_at",ago(60),"subject","Ingreso sintetico","body","Relato sintetico"),Map.of(
            "presenter","Ana Pérez Soto","identity_document","RUN 12.345.678-5","persons",List.of(
                Map.of("role","NNA","name","Benjamín Rojas"),
                Map.of("role","ACOMPANANTE","location","Calle Sintética 1"),
                Map.of("role","ACOMPANANTE","name","Luisa Vera"),
                Map.of("role","OTRA_PERSONA","name","Hugo Lagos","identity_document","Pasaporte AB-123.456"))));
        assertEquals(4,db.count("SELECT COUNT(*) FROM communication_person WHERE communication_id=?",manual));
        assertEquals(List.of(
            "COM|REQUIRENTE|Ana Pérez Soto|ana perez soto|12345678|5||false|",
            "COM_PERSONA|NNA|Benjamín Rojas|benjamin rojas||||false|",
            "COM_PERSONA|ACOMPANANTE|Luisa Vera|luisa vera||||false|"),refs(manual));

        // Ficha 1.1: solo la ficha vigente (no la copia de la COM), con su REQ, curador/a y nombres de preferencia (el de
        // nivel superior es de quien presenta); "otra persona" no se indexa.
        var form=new LinkedHashMap<String,Object>(Map.of("schema_version","1.1","modality","GENERAL","course","EN_ATENCION","motive","Consulta sintetica",
            "identity_type","RUN","presenter_first_name","Carla","presenter_last_name","Muñoz","presenter_run_number","12.345.678","presenter_run_dv","5"));
        form.put("preferred_name","Caro");form.put("guardian_name","Pedro Soto");
        form.put("people",List.of(Map.of("role","NNA","name","Matías","preferred_name","Mati","identity_document","21.234.567-K"),
            Map.of("role","OTRA_PERSONA","name","Hugo Lagos","identity_document","12.345.678-5"),Map.of("role","ACOMPANANTE","name","Rosa Díaz")));
        long req=run("administrativo","ATTENTION_CREATE",null,null,Map.of("scope_id",upr,"channel","PRESENCIAL","received_at",ago(60),"form",form)).id();
        long com=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
        assertEquals("Carla Muñoz",s(db.one("SELECT presenter FROM communication WHERE id=?",com),"presenter"));
        assertEquals(List.of(
            "FICHA|REQUIRENTE|Carla Muñoz|carla munoz caro|12345678|5||false|"+req,
            "FICHA|CURADOR|Pedro Soto|pedro soto||||false|"+req,
            "FICHA|NNA|Matías|matias mati|21234567|K||false|"+req,
            "FICHA|ACOMPANANTE|Rosa Díaz|rosa diaz||||false|"+req),refs(com));

        // Guardar la ficha recalcula las referencias: la version anterior deja de estar indexada (la COM no cambia).
        form.remove("guardian_name");
        form.put("people",List.of(Map.of("role","NNA","name","Matías Andrés","preferred_name","Mati","identity_document","21.234.567-K")));
        long rev=n(db.one("SELECT revision FROM requirement WHERE id=?",req),"revision");
        run("administrativo","ATTENTION_SAVE",req,rev,Map.of("form",form,"reason","Complemento sintetico"));
        assertEquals(List.of(
            "FICHA|REQUIRENTE|Carla Muñoz|carla munoz caro|12345678|5||false|"+req,
            "FICHA|NNA|Matías Andrés|matias andres mati|21234567|K||false|"+req),refs(com));
        assertEquals("Carla Muñoz",s(db.one("SELECT presenter FROM communication WHERE id=?",com),"presenter"));

        // Ficha NNA sin nombre de quien presenta: el nombre de preferencia forma la referencia por si solo, con su rol.
        long solo=run("administrativo","ATTENTION_CREATE",null,null,Map.of("scope_id",upr,"channel","PRESENCIAL","received_at",ago(60),
            "form",Map.of("modality","NNA","course","EN_ATENCION","motive","Consulta sintetica de NNA","preferred_name","Sol"))).id();
        assertEquals(List.of("FICHA|REQUIRENTE|Sol|sol||||false|"+solo),refs(n(db.one("SELECT communication_id FROM requirement WHERE id=?",solo),"communication_id")));

        // Formulario: sus personas con su rol y autenticacion; la persona senalada no se indexa aunque traiga nombre (D5).
        var vector=cuVulneration();
        ((ObjectNode)vector.withArrayProperty("personas").get(3)).withObjectProperty("atributos").set("primerNombre",attr("Hugo",DECL,NOCONTR));
        String signed=sign(vector);
        long external=flow.conserveExternal("administrativo",upr,signed).id();
        assertEquals("Hugo",s(db.one("SELECT given_names FROM external_person WHERE communication_id=? AND person_role='PERSONA_SENALADA'",external),"given_names"));
        var expected=List.of(
            "FORMULARIO|PRESENTADOR|María José De los Ángeles Del Río González|maria jose de los angeles del rio gonzalez|12345678|5||true|",
            "FORMULARIO|AFECTADO_NNA|Tomás|tomas||||false|",
            "FORMULARIO|ADULTO_RELACIONADO|Rosa|rosa||||false|");
        assertEquals(expected,refs(external));
        var ids=db.rows("SELECT id FROM person_reference WHERE communication_id=? ORDER BY id",external);
        // Un reenvio idempotente devuelve la misma COM y no reproyecta ni duplica.
        assertEquals(external,flow.conserveExternal("administrativo",upr,signed).id());
        assertEquals(expected,refs(external));
        assertEquals(ids,db.rows("SELECT id FROM person_reference WHERE communication_id=? ORDER BY id",external));
        assertEquals(0,db.count("SELECT COUNT(*) FROM person_reference WHERE person_role IN ('PERSONA_SENALADA','OTRA_PERSONA') OR name_key LIKE '%hugo%'"));
    }

    @Test @Order(3) void cargaInicialIdempotenteReproduceLosComandos(){
        var written=all();
        assertTrue(written.size()>=9);
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",PersonIndex.MARKER));

        // Con el marcador, la carga no vuelve a recorrer las COM.
        db.update("DELETE FROM person_reference");
        index.rebuild();
        assertEquals(0,db.count("SELECT COUNT(*) FROM person_reference"));

        // Sin el marcador (base 0.4.0 o carga interrumpida) reconstruye exactamente lo que escribieron los comandos.
        db.update("DELETE FROM application_marker WHERE code=?",PersonIndex.MARKER);
        index.rebuild();
        assertEquals(written,all());
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",PersonIndex.MARKER));

        // Repetir sobre un indice parcial lo completa sin duplicar.
        db.update("DELETE FROM person_reference WHERE source='FICHA'");
        db.update("DELETE FROM application_marker WHERE code=?",PersonIndex.MARKER);
        index.rebuild();index.rebuild();
        assertEquals(written,all());
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",PersonIndex.MARKER));

        // Instancia con el indice de G07 (marcador PERSON_INDEX_V12, persona senalada indexada): el marcador nuevo la reconstruye.
        long external=n(db.one("SELECT communication_id FROM external_person WHERE person_role='PERSONA_SENALADA'"),"communication_id");
        db.update("INSERT INTO person_reference(communication_id,source,person_role,display_name,name_key,authenticated,updated_ms) VALUES(?,'FORMULARIO','PERSONA_SENALADA','Hugo','hugo',FALSE,0)",external);
        db.update("DELETE FROM application_marker WHERE code=?",PersonIndex.MARKER);
        db.update("INSERT INTO application_marker(code,applied_ms) VALUES('PERSON_INDEX_V12',0)");
        index.rebuild();
        assertEquals(written,all());
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",PersonIndex.MARKER));
    }
}
