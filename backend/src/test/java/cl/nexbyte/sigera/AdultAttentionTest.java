package cl.nexbyte.sigera;

import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Ficha de primera atencion adulta 1.1: catalogos del Formulario Publico, reglas declaradas (RUN, celular, correo,
 * nombres, documento, nacimiento, nacionalidad), guardado parcial, edicion, versiones y compatibilidad con fichas 1.0
 * y con la modalidad NNA. Datos sinteticos; no se consulta ningun servicio.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_adult;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-adult-${random.uuid}"})
@ActiveProfiles("test")
class AdultAttentionTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Autowired Workflow flow;@Autowired Db db;@Autowired Attention attention;@Autowired Access access;

    long scope(){return n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");}
    long revision(long id){return n(db.one("SELECT revision FROM requirement WHERE id=?",id),"revision");}
    Workflow.Result run(String type,Long id,Map<String,Object> data){
        db.update("UPDATE staff SET must_change=FALSE WHERE username='administrativo'");
        return flow.run("administrativo",new Workflow.Command(UUID.randomUUID(),type,id,id==null?null:revision(id),data));
    }
    static Map<String,Object> form(String version,String modality,Object... pairs){
        var f=new LinkedHashMap<String,Object>(Map.of("schema_version",version,"modality",modality,"motive","Consulta sintetica de persona adulta","course","EN_ATENCION","people",List.of()));
        for(int i=0;i<pairs.length;i+=2)f.put((String)pairs[i],pairs[i+1]);
        return f;
    }
    long create(Map<String,Object> form){
        return run("ATTENTION_CREATE",null,new LinkedHashMap<>(Map.of("scope_id",scope(),"channel","PRESENCIAL","received_at",Instant.now().minusSeconds(60).toString(),"form",form))).id();
    }
    void save(long req,Map<String,Object> form){run("ATTENTION_SAVE",req,Map.of("form",form,"reason","Complementacion de la ficha"));}
    @SuppressWarnings("unchecked") Map<String,Object> stored(long req){return JSON.readValue(s(db.one("SELECT data_json FROM attention_record WHERE requirement_id=?",req),"data_json"),Map.class);}
    long count(){return db.count("SELECT COUNT(*) FROM requirement");}

    @Test void catalogosIdenticosAlFrontendYAlFormulario()throws Exception{
        for(String name:List.of("territorio.json","paises.json"))
            assertEquals(JSON.readTree(Files.readString(Path.of("../frontend/src/features/attention/adult/"+name))),
                JSON.readTree(Files.readString(Path.of("src/main/resources/catalog/"+name))),name);
        assertEquals(16,AdultIdentity.REGIONS.size());
        assertEquals(346,AdultIdentity.COMMUNES.values().stream().mapToInt(List::size).sum());
        assertEquals(249,AdultIdentity.COUNTRIES.size());
        assertTrue(AdultIdentity.validCommune("13","Ñuñoa"));assertFalse(AdultIdentity.validCommune("05","Ñuñoa"));
        assertEquals("Otra / situación especial: ",AdultIdentity.NATIONALITY_PREFIX);
    }

    @Test void reglasDeclaradasIgualesAlFrontend(){
        assertTrue(AdultIdentity.validRun("12345678","5"));assertTrue(AdultIdentity.validRun("10000013","k"));
        assertFalse(AdultIdentity.validRun("12345678","6"));assertFalse(AdultIdentity.validRun("012345678","5"));
        assertEquals("12.345.678-5",AdultIdentity.formatRun("12345678","5"));assertEquals("1.000.001-3",AdultIdentity.formatRun("1000001","3"));
        assertTrue(AdultIdentity.validMobile("+56912345678"));
        for(String bad:List.of("+5691234567","56912345678","+56 9 1234 5678","+56212345678"))assertFalse(AdultIdentity.validMobile(bad),bad);
        assertTrue(AdultIdentity.validEmail("persona@ejemplo.cl"));assertTrue(AdultIdentity.validEmail("María.José@niñez.cl"));
        for(String bad:List.of("sin-arroba","a@b","a..b@ejemplo.cl","a b@ejemplo.cl","a@ejemplo.cl\nBcc: x"))assertFalse(AdultIdentity.validEmail(bad),bad);
        assertNull(AdultIdentity.nameProblem("María José"));assertNull(AdultIdentity.nameProblem("O'Higgins"));assertNull(AdultIdentity.nameProblem("J"));
        assertNotNull(AdultIdentity.nameProblem("Juan2"));assertNotNull(AdultIdentity.nameProblem("Juan  -"));
        assertEquals("María José",AdultIdentity.normalizeName("  María  José "));
        assertNull(AdultIdentity.documentProblem("AB123456"));assertNotNull(AdultIdentity.documentProblem("A1"));
        var today=LocalDate.of(2026,11,9);
        assertNull(AdultIdentity.birthProblem("1990-02-28",today));assertNotNull(AdultIdentity.birthProblem("1990-02-30",today));
        assertNotNull(AdultIdentity.birthProblem("2026-11-10",today));assertNotNull(AdultIdentity.birthProblem("1890-01-01",today));
        assertTrue(AdultIdentity.validNationality("Chile"));assertTrue(AdultIdentity.validNationality("Otra / situación especial: Apátrida"));
        assertFalse(AdultIdentity.validNationality("chilena"));assertFalse(AdultIdentity.validNationality("Otra / situación especial:  "));
    }

    @Test void parcialCompletaYDerivadosEnLaComSinVerificarIdentidad(){
        long partial=create(form("1.1","GENERAL"));
        assertEquals("1.1",stored(partial).get("schema_version"));assertEquals("",stored(partial).get("presenter_name"));
        long req=create(form("1.1","GENERAL","presenter_first_name"," María  José ","presenter_last_name","Del Río","presenter_second_last_name","González",
            "identity_type","RUN","presenter_run_number","12.345.678","presenter_run_dv","5","presenter_birth_date","1990-02-28",
            "contact_phone","+56912345678","contact_email"," maria@ejemplo.cl ","region","13","commune","Ñuñoa","nationality","Chile","preferred_name","Majo","pronouns","ella"));
        var f=stored(req);
        assertEquals("María José",f.get("presenter_first_name"));assertEquals("12345678",f.get("presenter_run_number"));
        assertEquals("María José Del Río González",f.get("presenter_name"));assertEquals("12.345.678-5",f.get("identity_document"));
        assertEquals("maria@ejemplo.cl",f.get("contact_email"));assertEquals("Majo",f.get("preferred_name"));assertEquals("ella",f.get("pronouns"));
        var com=db.one("SELECT c.* FROM communication c JOIN requirement r ON r.communication_id=c.id WHERE r.id=?",req);
        assertEquals("María José Del Río González",s(com,"presenter"));assertEquals("12.345.678-5",s(com,"identity_document"));assertEquals("+56912345678",s(com,"contact_phone"));
        // Documento extranjero: tipo y numero, sin RUN.
        long foreign=create(form("1.1","GENERAL","identity_type","OTRO","presenter_document_kind","PASAPORTE","presenter_document_number"," AB  123456 ","nationality","Otra / situación especial: Apátrida"));
        assertEquals("Pasaporte AB 123456",stored(foreign).get("identity_document"));
    }

    @Test void datosInvalidosSeRechazanSinEscribir(){
        long before=count();
        List<Map<String,Object>> invalid=List.of(
            form("1.1","GENERAL","identity_type","RUN","presenter_run_number","12345678","presenter_run_dv","6"),
            form("1.1","GENERAL","identity_type","RUN","presenter_run_number","12345678"),
            form("1.1","GENERAL","identity_type","OTRO","presenter_run_number","12345678","presenter_run_dv","5"),
            form("1.1","GENERAL","identity_type","RUN","presenter_document_number","AB123456"),
            form("1.1","GENERAL","identity_type","OTRO","presenter_document_kind","CEDULA_LUNAR"),
            form("1.1","GENERAL","contact_phone","+56 9 1234 5678"),
            form("1.1","GENERAL","contact_email","no-es-correo"),
            form("1.1","GENERAL","region","Santiago"),
            form("1.1","GENERAL","region","05","commune","Ñuñoa"),
            form("1.1","GENERAL","nationality","chilena"),
            form("1.1","GENERAL","presenter_first_name","Juan2"),
            form("1.1","GENERAL","presenter_birth_date","2999-01-01"),
            form("1.1","GENERAL","identity_document","texto libre nuevo"),
            form("1.0","GENERAL","presenter_first_name","Ana"),
            form("2.0","GENERAL"));
        for(var f:invalid){
            var e=assertThrows(Problem.class,()->create(f),f.toString());
            assertTrue(e.status==400||e.status==409,f+" -> "+e.status);
        }
        assertEquals(before,count());
    }

    @Test void fichaAnteriorSeConservaYSeEditaComo11(){
        // Ficha 1.0 con texto libre (como las existentes): sigue aceptandose sin cambios.
        var legacy=form("1.0","GENERAL","presenter_name","Ana Pérez","identity_type","RUN","identity_document","Cédula vencida, no recuerda número",
            "contact_phone","22 345 6789","contact_email","ana@ejemplo","region","Santiago Centro","commune","Centro","nationality","chilena");
        long req=create(legacy);
        String original=s(db.one("SELECT data_json FROM attention_revision WHERE requirement_id=? ORDER BY id LIMIT 1",req),"data_json");
        assertEquals("1.0",stored(req).get("schema_version"));
        // Se edita como 1.1 conservando los valores historicos sin cambiarlos: se aceptan aunque no sigan el formato nuevo.
        var upgraded=new LinkedHashMap<>(legacy);upgraded.put("schema_version","1.1");upgraded.put("observations","Se complementa sin tocar lo anterior");
        save(req,upgraded);
        var f=stored(req);
        assertEquals("1.1",f.get("schema_version"));assertEquals("22 345 6789",f.get("contact_phone"));assertEquals("Santiago Centro",f.get("region"));
        assertEquals("chilena",f.get("nationality"));assertEquals("Cédula vencida, no recuerda número",f.get("identity_document"));assertEquals("Ana Pérez",f.get("presenter_name"));
        // Cambiar un valor historico exige el formato nuevo.
        var changed=new LinkedHashMap<>(upgraded);changed.put("region","Santiago Poniente");
        assertThrows(Problem.class,()->save(req,changed));
        changed.put("region","13");changed.put("commune","Santiago");changed.put("contact_phone","+56987654321");
        changed.put("identity_document","");changed.put("presenter_run_number","12345678");changed.put("presenter_run_dv","5");
        save(req,changed);
        f=stored(req);
        assertEquals("13",f.get("region"));assertEquals("+56987654321",f.get("contact_phone"));assertEquals("12.345.678-5",f.get("identity_document"));
        // Historial completo: la primera version conserva exactamente la ficha 1.0 y se puede consultar.
        assertEquals(3,db.count("SELECT COUNT(*) FROM attention_revision WHERE requirement_id=?",req));
        assertEquals(original,s(db.one("SELECT data_json FROM attention_revision WHERE requirement_id=? ORDER BY id LIMIT 1",req),"data_json"));
        long first=n(db.one("SELECT id FROM attention_revision WHERE requirement_id=? ORDER BY id LIMIT 1",req),"id");
        var v=attention.version(access.actor("administrativo"),req,first);
        assertEquals("1.0",((Map<?,?>)v.get("form")).get("schema_version"));assertEquals("Santiago Centro",((Map<?,?>)v.get("form")).get("region"));
        // La COM original no se reescribe al complementar la ficha.
        var com=db.one("SELECT c.* FROM communication c JOIN requirement r ON r.communication_id=c.id WHERE r.id=?",req);
        assertEquals("Ana Pérez",s(com,"presenter"));assertEquals("22 345 6789",s(com,"contact_phone"));
    }

    @Test void modalidadNnaConservaSusCamposLibres(){
        long req=create(form("1.1","NNA","contact_phone","+56 9 1234 5678","region","Biobío","commune","Concepción centro","nationality","chilena","participation","NO","people",List.of(Map.of("role","NNA","name",""))));
        var f=stored(req);
        assertEquals("+56 9 1234 5678",f.get("contact_phone"));assertEquals("Biobío",f.get("region"));assertEquals("chilena",f.get("nationality"));
        long legacy=create(form("1.0","NNA","participation","NO"));
        assertEquals("1.0",stored(legacy).get("schema_version"));
    }
}
