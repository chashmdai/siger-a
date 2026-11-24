package cl.nexbyte.sigera;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Ficha de primera atencion 1.2 (0.8.0, D33, gate E03a): reglas de la persona adulta tambien en la modalidad NNA,
 * personas canonicas (persona-1) con las mismas reglas del Formulario (escenario 11), datos historicos de 1.0/1.1
 * conservados sin dividirse ni interpretarse, codigos de relacion y tipo de institucion solo del catalogo vigente con
 * su version derivada, resumen de cambios (completar no es corregir), su carga historica con marcador, la auditoria
 * sin valores y el indice de personas sobre la ficha 1.2. Datos sinteticos.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_ficha12;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-ficha12-${random.uuid}"})
@ActiveProfiles("test")
class Attention12Test {
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Autowired Workflow flow;@Autowired Db db;@Autowired Attention attention;

    @BeforeEach void clean(){
        db.update("UPDATE staff SET must_change=FALSE");
        for(String t:List.of("stat_catalog_mapping","stat_catalog_item","stat_catalog_version"))db.update("DELETE FROM "+t);
    }
    long scope(){return n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");}
    long revision(long id){return n(db.one("SELECT revision FROM requirement WHERE id=?",id),"revision");}
    Workflow.Result run(String type,Long id,Map<String,Object> data){
        return flow.run("administrativo",new Workflow.Command(UUID.randomUUID(),type,id,id==null?null:revision(id),data));
    }
    static Map<String,Object> form(String version,String modality,Object... pairs){
        var f=new LinkedHashMap<String,Object>(Map.of("schema_version",version,"modality",modality,"motive","Consulta sintetica","course","EN_ATENCION","people",List.of()));
        for(int i=0;i<pairs.length;i+=2)f.put((String)pairs[i],pairs[i+1]);
        return f;
    }
    static Map<String,Object> person(Object... pairs){
        var p=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)p.put((String)pairs[i],pairs[i+1]);return p;
    }
    long create(Map<String,Object> form){
        return run("ATTENTION_CREATE",null,new LinkedHashMap<>(Map.of("scope_id",scope(),"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(60).toString(),"form",form))).id();
    }
    void save(long req,Map<String,Object> form){run("ATTENTION_SAVE",req,Map.of("form",form,"reason","Complementacion sintetica"));}
    String refused(Long req,Map<String,Object> form){
        var p=assertThrows(Problem.class,()->{if(req==null)create(form);else save(req,form);});
        assertTrue(p.status==400||p.status==409,p.status+" "+p.getMessage());return p.getMessage();
    }
    @SuppressWarnings("unchecked") Map<String,Object> stored(long req){return JSON.readValue(s(db.one("SELECT data_json FROM attention_record WHERE requirement_id=?",req),"data_json"),Map.class);}
    @SuppressWarnings("unchecked") List<Map<String,Object>> people(long req){return (List<Map<String,Object>>)stored(req).get("people");}
    @SuppressWarnings("unchecked") Map<String,List<String>> lastChanges(long req){
        var v=db.one("SELECT change_summary FROM attention_revision WHERE id=(SELECT MAX(id) FROM attention_revision WHERE requirement_id=?)",req);
        return v.get("change_summary")==null?null:JSON.readValue(s(v,"change_summary"),Map.class);
    }
    long catalogVersion(String catalog,String... codes){
        long id=flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_IMPORT",null,null,Map.of("content",StatCatalogsTest.file(catalog,codes),"reason","Resolucion sintetica"))).id();
        flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_ACTIVATE",id,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",id),"revision"),Map.of("reason","Activacion sintetica")));
        return id;
    }

    @Test void reglasDeLaPersonaAdultaTambienEnLaModalidadNna(){
        long req=create(form("1.2","NNA","region","07","commune","Talca","contact_email","nna@ejemplo.cl","contact_phone","+56912345678","nationality","Chile"));
        assertEquals("07",stored(req).get("region"));assertEquals("1.2",stored(req).get("schema_version"));
        assertTrue(refused(null,form("1.2","NNA","region","Region del Maule")).contains("Region: selecciona una region del listado."));
        assertTrue(refused(null,form("1.2","NNA","contact_email","sin-arroba")).contains("Correo: revisa el formato."));
        assertTrue(refused(null,form("1.2","NNA","contact_phone","22 123 4567")).contains("Celular"));
        assertTrue(refused(null,form("1.2","NNA","nationality","chilena")).contains("Nacionalidad"));
        assertTrue(refused(null,form("1.2","NNA","region","07","commune","Ñuñoa")).contains("Comuna"));
        // 1.1 NNA conserva el texto libre de siempre.
        long old=create(form("1.1","NNA","region","Region del Maule","nationality","chilena"));
        assertEquals("Region del Maule",stored(old).get("region"));
        // Cantidad aproximada entera en 1.2; hechos con catalogo del Formulario.
        assertTrue(refused(null,form("1.2","GENERAL","approximate_count","unos diez")).contains("Cantidad aproximada"));
        assertTrue(refused(null,form("1.2","GENERAL","facts_region_code","99")).contains("Region de los hechos"));
        long facts=create(form("1.2","GENERAL","approximate_count","12","facts_region_code","13","facts_commune","Ñuñoa","facts_place","Plaza sintetica"));
        assertEquals("Ñuñoa",stored(facts).get("facts_commune"));
    }

    @Test void historicoSeConservaSinDividirseNiInterpretarse(){
        var oldPerson=person("role","NNA","name","Juan Pérez Soto","age","doce","identity_document","RUN 12.345.678-5","nationality","chileno","relationship","hijo","location","Talca");
        long req=create(form("1.1","NNA","region","Region del Maule","nationality","chilena","people",List.of(oldPerson)));
        var legacy=person("role","NNA","legacy_name","Juan Pérez Soto","legacy_age","doce","legacy_document","RUN 12.345.678-5","legacy_nationality","chileno","relationship_text","hijo","location_text","Talca");
        save(req,form("1.2","NNA","region","Region del Maule","nationality","chilena","people",List.of(legacy)));
        var p=people(req).getFirst();
        assertEquals("Juan Pérez Soto",p.get("legacy_name"));assertEquals("",p.get("first_name"),"un nombre historico no se divide");
        assertEquals("doce",p.get("legacy_age"));assertEquals("",p.get("age_years"),"una edad historica no se interpreta");
        assertEquals("chileno",p.get("legacy_nationality"));assertEquals("",p.get("nationality"),"una nacionalidad libre no se convierte en pais");
        assertEquals("Region del Maule",stored(req).get("region"),"el texto libre se conserva");
        assertNull(OriginRegion.ofForm(stored(req)),"un texto libre de region no es region");
        assertEquals(Map.of("COMPLEMENTO",List.of(),"RECTIFICACION",List.of()),lastChanges(req),"pasar de 1.1 a 1.2 no es un cambio");
        // Un historico no se crea ni se reescribe; si se puede quitar.
        assertTrue(refused(req,form("1.2","NNA","people",List.of(legacy,person("role","NNA","legacy_name","Otra persona"))))
            .contains("Persona 2: los datos historicos no se crean ni se modifican."));
        var changed=new LinkedHashMap<>(legacy);changed.put("legacy_name","Juan Perez");
        assertTrue(refused(req,form("1.2","NNA","people",List.of(changed))).contains("historicos"));
        // Una ficha 1.2 no vuelve a una version anterior.
        assertTrue(refused(req,form("1.1","NNA")).contains("version posterior"));
        save(req,form("1.2","NNA","people",List.of(person("role","NNA","first_name","Juan","last_name","Pérez","age_years","12"))));
        assertEquals("12",people(req).getFirst().get("age_years"));
        var changes=lastChanges(req);
        assertTrue(changes.get("COMPLEMENTO").containsAll(List.of("people[0].first_name","people[0].last_name","people[0].age_years")),changes.toString());
        assertTrue(changes.get("RECTIFICACION").containsAll(List.of("people[0].legacy_name","region","nationality")),changes.toString());
    }

    @Test void escenario11MismasReglasDelFormularioEnLasPersonas(){
        String[][] bad={{"identity_type","RUN","run_number","12345678","run_dv","6","RUN no coinciden"},{"email","a..b@ejemplo.cl","Correo"},
            {"mobile","912345678","Celular"},{"nationality","chilena","Nacionalidad"},{"identity_type","DOCUMENTO_EXTRANJERO","document_number","A1","Numero de documento"},
            {"age_years","18","Edad"},{"treatment","usted","Trato"},{"first_name","Juan2","Primer nombre"},{"birth_date","2999-01-01","Fecha de nacimiento"},
            {"identity_type","RUN","document_number","AB123456","documento extranjero corresponde"}};
        for(String[] c:bad){
            var p=person("role","NNA");for(int i=0;i<c.length-1;i+=2)p.put(c[i],c[i+1]);
            String message=refused(null,form("1.2","GENERAL","people",List.of(p)));
            assertTrue(message.startsWith("Persona 1: ")&&message.contains(c[c.length-1].split(" ")[0]),message+" / "+c[c.length-1]);
        }
        long req=create(form("1.2","GENERAL","people",List.of(person("role","ACOMPANANTE","first_name"," María  José ","last_name","Rojas","identity_type","RUN","run_number","12.345.678","run_dv","5",
            "email"," acompanante@ejemplo.cl ","mobile","+56912345678","nationality","Chile","age_years",40,"treatment","ella","region_code","13","commune","Ñuñoa"))));
        var p=people(req).getFirst();
        assertEquals("María José",p.get("first_name"));assertEquals("12345678",p.get("run_number"));assertEquals("acompanante@ejemplo.cl",p.get("email"));assertEquals("40",p.get("age_years"));
        // El cliente no envia campos derivados.
        assertEquals("Datos de persona no reconocidos.",refused(null,form("1.2","GENERAL","people",List.of(person("role","NNA","relationship_version",1)))));
        assertEquals("La ficha contiene campos no reconocidos.",refused(null,form("1.2","GENERAL","relationship_version",1)));
        assertEquals("La ficha contiene campos no reconocidos.",refused(null,form("1.1","GENERAL","facts_place","x")),"1.1 no admite campos de 1.2");
        // Un valor anterior identico se conserva sin revalidarse (regla de la ficha 1.1, tambien por persona).
        db.update("UPDATE attention_record SET data_json=REPLACE(data_json,'+56912345678','2212345') WHERE requirement_id=?",req);
        var kept=new LinkedHashMap<>(people(req).getFirst());kept.remove("relationship_version");kept.put("other_contact","Recado sintetico");
        save(req,form("1.2","GENERAL","people",List.of(kept)));
        assertEquals("2212345",people(req).getFirst().get("mobile"));
    }

    @Test void codigosSoloDelCatalogoVigenteConSuVersion(){
        var mother=person("role","ACOMPANANTE","relationship_code","MADRE");
        assertTrue(refused(null,form("1.2","GENERAL","people",List.of(mother))).contains("no hay un listado vigente"));
        assertTrue(refused(null,form("1.2","GENERAL","relationship_code","MADRE")).contains("Relacion con el NNA: no hay un listado vigente"));
        long noCode=create(form("1.2","GENERAL","people",List.of(person("role","ACOMPANANTE","relationship_text","Madre de la nina"))));
        assertEquals("",people(noCode).getFirst().get("relationship_code"));
        long v1=catalogVersion("RELACION","MADRE","PADRE","EL_PROPIO_NNA");
        long req=create(form("1.2","GENERAL","relationship_code","EL_PROPIO_NNA","people",List.of(mother)));
        assertEquals(v1,n(people(req).getFirst(),"relationship_version"));assertEquals(v1,n(stored(req),"relationship_version"));
        assertTrue(refused(null,form("1.2","GENERAL","people",List.of(person("role","NNA","relationship_code","TIA")))).contains("selecciona una opcion del listado vigente"));
        // Nueva version sin MADRE: el codigo anterior identico se conserva con su version; uno nuevo exige la vigente.
        long v2=catalogVersion("RELACION","PADRE","REPRESENTANTE_LEGAL");
        var again=new LinkedHashMap<>(people(req).getFirst());again.remove("relationship_version");
        save(req,form("1.2","GENERAL","relationship_code","EL_PROPIO_NNA","people",List.of(again)));
        assertEquals(v1,n(people(req).getFirst(),"relationship_version"));
        assertTrue(refused(req,form("1.2","GENERAL","people",List.of(again,person("role","NNA","relationship_code","TIA")))).contains("Persona 2: Relacion: selecciona una opcion del listado vigente."));
        save(req,form("1.2","GENERAL","people",List.of(person("role","ACOMPANANTE","relationship_code","REPRESENTANTE_LEGAL"))));
        assertEquals(v2,n(people(req).getFirst(),"relationship_version"));
        // Instituciones: nombre obligatorio y tipo del catalogo vigente de TIPO_INSTITUCION.
        assertTrue(refused(null,form("1.2","GENERAL","institutions_list",List.of(Map.of("type_code","ESCUELA")))).contains("Institucion 1: indica el nombre."));
        assertTrue(refused(null,form("1.2","GENERAL","institutions_list",List.of(Map.of("name","Liceo sintetico","type_code","ESCUELA")))).contains("Institucion 1: Tipo: no hay un listado vigente"));
        long types=catalogVersion("TIPO_INSTITUCION","ESCUELA","SALUD");
        long inst=create(form("1.2","GENERAL","institutions_list",List.of(Map.of("name","Liceo sintetico","type_code","ESCUELA","role","Establecimiento de la nina","region_code","13","commune","Ñuñoa"))));
        @SuppressWarnings("unchecked") var list=(List<Map<String,Object>>)stored(inst).get("institutions_list");
        assertEquals(types,n(list.getFirst(),"type_version"));
        assertEquals("Datos de institucion no reconocidos.",refused(null,form("1.2","GENERAL","institutions_list",List.of(Map.of("name","X","type_version",1)))));
    }

    @Test void completarNoEsCorregirYAuditoriaSinValores(){
        long req=create(form("1.2","GENERAL"));
        save(req,form("1.2","GENERAL","presenter_first_name","Rosalía","presenter_last_name","Quintanilla","identity_type","RUN","presenter_run_number","12345678","presenter_run_dv","5",
            "contact_email","rosalia.sintetica@ejemplo.cl","region","05","commune","Valparaíso"));
        var c=lastChanges(req);
        assertEquals(List.of(),c.get("RECTIFICACION"),"completar no es corregir: "+c);
        assertEquals(List.of("commune","contact_email","identity_type","presenter_first_name","presenter_last_name","presenter_run_dv","presenter_run_number","region"),c.get("COMPLEMENTO"),
            "el nombre y documento derivados no cuentan");
        save(req,form("1.2","GENERAL","presenter_first_name","Rosalía","presenter_last_name","Quintanilla","identity_type","RUN","presenter_run_number","12345678","presenter_run_dv","5",
            "contact_email","rosalia.sintetica@ejemplo.cl","region","06","commune","Rancagua"));
        assertEquals(List.of("commune","region"),lastChanges(req).get("RECTIFICACION"));
        var notes=db.rows("SELECT note FROM audit_event WHERE resource_type='REQ' AND resource_id=? AND action='FICHA_COMPLEMENTADA' ORDER BY id",req).stream().map(r->s(r,"note")).toList();
        assertEquals(List.of("Version 1. Region de origen modificada. Complementacion sintetica Campos complementados: 8; rectificados: 0.",
            "Version 2. Region de origen modificada. Complementacion sintetica Campos complementados: 0; rectificados: 2."),notes);
        // Ninguna nota de auditoria ni de constancia lleva valores declarados.
        for(String value:List.of("12345678","12.345.678","rosalia.sintetica","Rosalía","Quintanilla","Valparaíso","Rancagua"))
            assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE note LIKE ?","%"+value+"%"),value);
    }

    @Test void cargaHistoricaDelResumenConMarcador(){
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",Attention.SUMMARY_MARKER),"el arranque ejecuta la carga (Bootstrap)");
        long req=create(form("1.1","GENERAL"));
        save(req,form("1.1","GENERAL","region","05"));
        save(req,form("1.2","GENERAL","region","06","presenter_first_name","Ana"));
        var live=db.rows("SELECT id,change_summary FROM attention_revision ORDER BY id").stream().map(r->r.get("change_summary")==null?"":s(r,"change_summary")).toList();
        assertTrue(live.stream().filter(x->!x.isEmpty()).count()>=2);
        // Como una instancia 0.7.0: versiones sin resumen y sin marcador.
        db.update("UPDATE attention_revision SET change_summary=NULL");
        db.update("DELETE FROM application_marker WHERE code=?",Attention.SUMMARY_MARKER);
        attention.rebuildSummaries();
        assertEquals(live,db.rows("SELECT id,change_summary FROM attention_revision ORDER BY id").stream().map(r->r.get("change_summary")==null?"":s(r,"change_summary")).toList(),
            "la carga historica calcula lo mismo que el registro en vivo");
        assertNull(db.one("SELECT change_summary FROM attention_revision WHERE id=(SELECT MIN(id) FROM attention_revision WHERE requirement_id=?)",req).get("change_summary"),"la primera version no tiene resumen");
        assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",Attention.SUMMARY_MARKER));
        // Con el marcador no se vuelve a ejecutar.
        db.update("UPDATE attention_revision SET change_summary='{}' WHERE requirement_id=?",req);
        attention.rebuildSummaries();
        assertEquals(3,db.count("SELECT COUNT(*) FROM attention_revision WHERE requirement_id=? AND change_summary='{}'",req));
    }

    @Test void indiceDePersonasLeeLaFichaDos(){
        long req=create(form("1.2","NNA","presenter_first_name","Valentina","presenter_last_name","Sintetica","preferred_name","Vale","people",List.of(
            person("role","NNA","first_name","Benjamín","last_name","Rojas","identity_type","RUN","run_number","12345678","run_dv","5"),
            person("role","OTRA_PERSONA","first_name","Hugo","last_name","Lagos"))));
        long com=n(db.one("SELECT communication_id FROM requirement WHERE id=?",req),"communication_id");
        var refs=db.rows("SELECT person_role,name_key,run_number FROM person_reference WHERE communication_id=? ORDER BY id",com);
        assertEquals(List.of("REQUIRENTE","NNA"),refs.stream().map(r->s(r,"person_role")).toList(),"nunca OTRA_PERSONA");
        assertEquals("valentina sintetica vale",s(refs.get(0),"name_key"));
        assertEquals("benjamin rojas",s(refs.get(1),"name_key"));assertEquals("12345678",s(refs.get(1),"run_number"));
        // Persona historica llevada a 1.2: se indexa por su nombre y su identificacion de texto libre, como antes.
        long old=create(form("1.1","NNA","people",List.of(person("role","NNA","name","Juan Pérez Soto","identity_document","RUN 9.876.543-3"))));
        long oldCom=n(db.one("SELECT communication_id FROM requirement WHERE id=?",old),"communication_id");
        var before=db.rows("SELECT person_role,name_key,run_number,run_dv,document_key FROM person_reference WHERE communication_id=? ORDER BY id",oldCom);
        save(old,form("1.2","NNA","people",List.of(person("role","NNA","legacy_name","Juan Pérez Soto","legacy_document","RUN 9.876.543-3"))));
        assertEquals(before,db.rows("SELECT person_role,name_key,run_number,run_dv,document_key FROM person_reference WHERE communication_id=? ORDER BY id",oldCom));
        assertEquals("juan perez soto",s(before.getFirst(),"name_key"));assertEquals("9876543",s(before.getFirst(),"run_number"));
    }
}
