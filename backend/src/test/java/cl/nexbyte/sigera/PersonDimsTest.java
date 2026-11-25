package cl.nexbyte.sigera;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.FormularioDocs.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static cl.nexbyte.sigera.TerritorialDims.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_demografia;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-demografia-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
class PersonDimsTest extends E07Fixture {
    @Autowired PersonDims dims;
    @Autowired TerritorialDims territory;

    @Test void escenario14CumpleanosEnChileYTextoHistoricoSinInterpretar(){
        long before=Instant.parse("2025-07-01T03:59:59Z").toEpochMilli();
        long birthday=before+1000;
        assertEquals(new PersonDims.Age(10,"NACIMIENTO"),PersonDims.age("2014-07-01","14",before,17));
        assertEquals(new PersonDims.Age(11,"NACIMIENTO"),PersonDims.age("2014-07-01","14",birthday,17));
        assertEquals(new PersonDims.Age(8,"DECLARADA"),PersonDims.age("","8",birthday,17));
        assertNull(PersonDims.age("","18",birthday,17).years());
        var legacy=PersonDims.project("FICHA",Map.of("schema_version","1.0","nationality","Chile","people",
            List.of(Map.of("role","NNA","name","Nombre de prueba","age","8","nationality","Colombia"))),birthday);
        assertNull(legacy.people().getFirst().nationality());
        assertNull(legacy.people().getLast().nationality());
        assertNull(legacy.people().getLast().age().years());
        assertEquals(0,legacy.people().stream().filter(p->p.age().origin()!=null).count());
    }

    @Test void proyeccionRecalculadaYTramoSql()throws Exception{
        catalogs("tipo-requirente-v1.json","tramo-edad-v1.json");
        var data=Map.<String,Object>of("presenter",Map.of("first_name","Persona","age_years","29","nationality","Chile"),
            "persons",List.of(Map.of("role","NNA","first_name","Afectada","age_years","8","nationality","Chile"),
                Map.of("role","NNA","first_name","Afectado","age_years","14","nationality","Colombia"),
                Map.of("role","ACOMPANANTE","first_name","Acompañante","age_years","35","nationality","Chile")));
        long com=manual(data);
        assertEquals("NO_APLICA",s(dims.requester(com,VIGENTE,null),"applicability"));
        run(DIST,"STAT_CLASSIFY",com,0L,Map.of("stat",Map.of(TR,code("PERSONA_NATURAL_ADULTA"))));
        var requester=dims.requester(com,VIGENTE,null);
        assertEquals("INFORMADO",s(requester,"applicability"));
        assertEquals(29,n(requester,"age_years"));
        assertEquals("Chile",s(requester,"nationality"));
        var people=dims.affected(com,VIGENTE,null);
        assertEquals(Set.of("Chile","Colombia"),new HashSet<>(people.stream().map(p->s(p,"nationality")).toList()));
        assertEquals(Set.of(8L,14L),new HashSet<>(people.stream().map(p->n(p,"age_years")).toList()));
        assertTrue(people.stream().noneMatch(p->p.containsKey("birth_date")));
        var saved=db.one("SELECT d.id,v.data_json,c.received_ms FROM stat_declared_version d JOIN intake_revision v ON v.id=d.source_id"
            +" JOIN communication c ON c.id=d.communication_id WHERE c.id=?",com);
        @SuppressWarnings("unchecked")
        var form=(Map<String,Object>)JsonMapper.builder().build().readValue(s(saved,"data_json"),Map.class);
        var projected=PersonDims.project("INGRESO",form,n(saved,"received_ms"));
        var persisted=db.rows("SELECT person_role,age_years,nationality,age_origin FROM stat_declared_person WHERE version_id=? ORDER BY person_order",n(saved,"id"));
        assertEquals(projected.people().stream().map(p->List.of(p.role(),p.age().years(),p.nationality(),p.age().origin())).toList(),
            persisted.stream().map(p->List.of(s(p,"person_role"),(int)n(p,"age_years"),s(p,"nationality"),s(p,"age_origin"))).toList());
        String code=s(db.one("SELECT "+PersonDims.bracketSql("8")+" AS bracket"),"bracket");
        assertEquals("T006-009",code);
        long count=db.count("SELECT COUNT(*) FROM stat_declared_person");
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",PersonDims.MARKER));
        db.update("DELETE FROM stat_declared_person WHERE version_id=?",n(saved,"id"));
        db.update("DELETE FROM stat_declared_version WHERE id=?",n(saved,"id"));
        db.update("DELETE FROM application_marker WHERE code=?",PersonDims.MARKER);
        dims.rebuild();
        assertEquals(count,db.count("SELECT COUNT(*) FROM stat_declared_person"));
        assertEquals(Set.of(8L,14L),new HashSet<>(dims.affected(com,VIGENTE,null).stream().map(p->n(p,"age_years")).toList()));
        dims.rebuild();
        assertEquals(count,db.count("SELECT COUNT(*) FROM stat_declared_person"));
    }

    @Test void formularioConservaRolesYExigeNacionalidadExacta(){
        var document=cuVulneration();
        var people=(tools.jackson.databind.node.ArrayNode)document.path("personas");
        var affected=(tools.jackson.databind.node.ObjectNode)people.get(1).path("atributos");
        affected.set("nacionalidad",attr("Colombia",DECL,NOCONTR));
        people.add(person("afectado-2","AFECTADO_NNA",false,null,null,"primerNombre",attr("Otra",DECL,NOCONTR),
            "nacionalidad",attr("chile",DECL,NOCONTR)));
        long com=flow.conserveExternal(ADMIN,scope("UPR-REQ"),sign(document)).id();
        var affectedRows=dims.affected(com,VIGENTE,null);
        assertEquals(2,affectedRows.size());
        var withRegion=affectedRows.stream().filter(p->"13".equals(s(p,"region_code"))).findFirst().orElseThrow();
        assertEquals("Ñuñoa",s(withRegion,"commune"));
        assertEquals(9,n(withRegion,"age_years"));
        assertEquals("Colombia",s(withRegion,"nationality"));
        assertEquals(1,affectedRows.stream().filter(p->p.get("nationality")==null).count());
        long received=n(db.one("SELECT received_ms FROM communication WHERE id=?",com),"received_ms");
        assertTrue(dims.affected(com,HISTORICO,received-1).isEmpty());
        assertEquals(2,dims.affected(com,HISTORICO,received).size());
        assertTrue(dims.affected(com,REQ,null).isEmpty());
        assertNull(territory.read(com,HISTORICO,received-1).get("requester_region"));
        assertEquals("13",s(territory.read(com,HISTORICO,received),"requester_region"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_declared_version WHERE communication_id=?",com));
    }
}
