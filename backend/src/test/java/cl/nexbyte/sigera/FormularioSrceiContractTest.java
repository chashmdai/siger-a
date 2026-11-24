package cl.nexbyte.sigera;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Contrato del Formulario Publico (0.8.0, gate E00): vectores NNA con el RUN del presentador de fuente SRCEI y nivel
 * CONTRASTADO_EN_FUENTE, no autenticado (nota contractual del 15/11/2026, formulario-web 6f886eb; procedencia y huellas
 * en src/test/resources/formulario/PROCEDENCIA.txt). Verifica con la clave publica de prueba del Formulario que
 * ExternalOriginal.parse los admite, que la COM registrada por conserveExternal conserva la procedencia sin elevarla a
 * autenticacion (external_person, external_attribute y person_reference), con region 13, y que un reenvio no reproyecta.
 * Los recursos se leen con CRLF pasado a LF (la copia de trabajo de Windows usa CRLF; git guarda LF). La recepcion
 * PULL/ACK con productor falso esta en FormularioSrceiReceptionTest.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:srcei_contrato;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.formulario-trusted-keys=src/test/resources/formulario/public-nna-srcei-test.json"})
@ActiveProfiles("test")
class FormularioSrceiContractTest {
    static final Path DIR=Path.of("src/test/resources/formulario");
    static final List<String> VECTORS=List.of("formulario-nna-srcei-v1.0.json","formulario-nna-srcei-v1.1.json");
    static final String KEY_ID="nna-srcei-public-test";
    static final JsonMapper JSON=JsonMapper.builder().build();
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired FormularioKeys keys;
    static long scope;

    /** Texto de un archivo con CRLF pasado a LF: el mismo contenido que guarda git, en Windows y en Linux. */
    static String text(Path file){
        try{return Files.readString(file,StandardCharsets.UTF_8).replace("\r\n","\n");}
        catch(IOException e){throw new IllegalStateException(e);}
    }
    static String vector(String name){return text(DIR.resolve(name));}
    static String sha(String text){return ExternalOriginal.sha256(text.getBytes(StandardCharsets.UTF_8));}
    /** Huellas de PROCEDENCIA.txt por nombre: lineas "<sha256>  <nombre>  <origen>". */
    static Map<String,String> provenance(){
        var out=new LinkedHashMap<String,String>();
        var line=Pattern.compile("^([0-9a-f]{64})  (\\S+)  (.+)$");
        for(String l:text(DIR.resolve("PROCEDENCIA.txt")).split("\n")){
            var m=line.matcher(l);
            if(m.matches()&&out.put(m.group(2),m.group(1))!=null)throw new IllegalStateException("Nombre repetido en PROCEDENCIA.txt");
        }
        return out;
    }

    void setup(){
        if(scope!=0)return;
        db.update("INSERT INTO org_unit VALUES('SRC','Unidad receptora SRCeI')");
        scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('SRC-REC','Recepcion SRCeI','SRC','REC','CENTRAL')");
        long id=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES('receptor.srcei','receptor.srcei','test-non-login',?,FALSE)",scope);
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'ADMINISTRATIVO',?,0,'PRUEBA AUTOMATICA')",id,scope);
    }

    /**
     * Procedencia SRCeI conservada sin elevarla a autenticacion, en la COM registrada por cualquiera de los dos
     * caminos (conserveExternal o FormularioReception): un solo presentador no autenticado, RUN y fecha de nacimiento
     * de fuente SRCEI, todos los atributos CONTRASTADO_EN_FUENTE, indice sin autenticacion y region 13.
     */
    static void assertSrcei(Db db,Access access,long com,String document){
        var c=db.one("SELECT * FROM communication WHERE id=?",com);
        assertEquals("FORMULARIO_WEB_NNA",s(c,"channel"));assertEquals("NUEVA",s(c,"state"));
        var original=db.one("SELECT * FROM communication_original WHERE communication_id=?",com);
        // El mismo documento (el cliente PULL lo recibe compactado dentro de la respuesta: igual como arbol JSON), con
        // su SHA-256 y las huellas firmadas.
        assertEquals(JSON.readTree(document),JSON.readTree(s(original,"document")));
        assertEquals(sha(s(original,"document")),s(original,"document_sha256"));
        assertEquals(JSON.readTree(document).path("sha256").asString(),s(original,"content_hash"));
        assertEquals(JSON.readTree(document).path("huellaProcedencia").asString(),s(original,"provenance_hash"));
        assertEquals(KEY_ID,s(original,"custody_key_id"));assertEquals("NNA",s(original,"document_kind"));
        var people=db.rows("SELECT * FROM external_person WHERE communication_id=?",com);
        assertEquals(1,people.size());
        var p=people.getFirst();
        assertEquals("presentador-nna",s(p,"person_key"));assertEquals("PRESENTADOR",s(p,"person_role"));
        assertFalse(b(p,"authenticated"));
        assertEquals("99999999",s(p,"run_number"));assertEquals("9",s(p,"run_dv"));assertEquals("SRCEI",s(p,"run_source"));assertTrue(b(p,"run_valid"));
        assertEquals("2012-03-04",s(p,"birth_date"));assertEquals("SRCEI",s(p,"birth_date_source"));
        assertEquals("SRCEI",s(p,"given_names_source"));assertEquals("SRCEI",s(p,"surnames_source"));
        var attrs=db.rows("SELECT * FROM external_attribute WHERE person_id=?",n(p,"id"));
        assertEquals(5,attrs.size());
        for(var a:attrs){
            assertEquals("SRCEI",s(a,"source"),s(a,"attr_key"));assertEquals("CONTRASTADO_EN_FUENTE",s(a,"level_code"),s(a,"attr_key"));
            assertFalse(b(a,"discrepancy"));
        }
        var refs=db.rows("SELECT * FROM person_reference WHERE communication_id=?",com);
        assertFalse(refs.isEmpty());
        for(var r:refs){assertEquals("FORMULARIO",s(r,"source"));assertFalse(b(r,"authenticated"));}
        assertEquals(1,refs.stream().filter(r->"99999999".equals(s(r,"run_number"))&&"9".equals(s(r,"run_dv"))).count());
        assertEquals("13",s(db.one("SELECT value_text FROM external_field WHERE communication_id=? AND field_group='CAMPO' AND field_key='nna-region'",com),"value_text"));
        assertEquals("13",access.suggestedRegion(com,null));
    }
    /** Todo lo que proyecta el registro de la COM, con identificadores e instantes: un reenvio no debe cambiar nada. */
    static List<Object> snapshot(Db db,long com){
        return List.of(
            db.rows("SELECT * FROM communication WHERE id=?",com),
            db.rows("SELECT * FROM communication_original WHERE communication_id=?",com),
            db.rows("SELECT * FROM external_person WHERE communication_id=? ORDER BY id",com),
            db.rows("SELECT a.* FROM external_attribute a JOIN external_person p ON p.id=a.person_id WHERE p.communication_id=? ORDER BY a.id",com),
            db.rows("SELECT * FROM external_field WHERE communication_id=? ORDER BY id",com),
            db.rows("SELECT * FROM person_reference WHERE communication_id=? ORDER BY id",com),
            db.rows("SELECT * FROM milestone WHERE communication_id=? ORDER BY id",com),
            totals(db,PROJECTION));
    }
    /** Tablas que llena el registro de una COM externa. */
    static final List<String> PROJECTION=List.of("communication","communication_original","external_person","external_attribute","external_field","person_reference");
    static List<Long> totals(Db db,List<String> tables){
        var out=new ArrayList<Long>();
        for(String t:tables)
            out.add(db.count("SELECT COUNT(*) FROM "+t));
        return out;
    }

    // --- Pruebas -----------------------------------------------------------------------------------

    @Test void recursosCopiadosConLaHuellaDelFormularioYAdmitidosConSuClaveDePrueba(){
        var hashes=provenance();
        for(String name:List.of("formulario-nna-srcei-v1.0.json","formulario-nna-srcei-v1.1.json","public-nna-srcei-test.json"))
            assertEquals(hashes.get(name),sha(text(DIR.resolve(name))),name);
        // Solo la clave publica de prueba del Formulario; ninguna generada por SIGER.
        assertEquals(Set.of(KEY_ID),keys.keys().keySet());
        for(String name:VECTORS){
            String document=vector(name);
            var o=ExternalOriginal.parse(document,keys.keys());
            var root=JSON.readTree(document);
            assertEquals(name.contains("v1.0")?"1.0":"1.1",o.version());
            assertEquals("NNA",o.kind());assertEquals("FORMULARIO_WEB_NNA",o.channel());assertEquals(KEY_ID,o.custodyKeyId());
            assertEquals(root.path("sha256").asString(),o.contentHash());assertEquals(root.path("huellaProcedencia").asString(),o.provenanceHash());
            assertEquals(name.contains("v1.0")?0:1,o.attachments().size());
            var person=o.projection().persons().getFirst();
            assertFalse(person.authenticated());assertEquals("SRCEI",person.runSource());
        }
    }

    @Test void catalogosDelServidorConLaHuellaDelFormulario(){
        var hashes=provenance();
        for(String name:List.of("territorio.json","paises.json"))
            assertEquals(hashes.get(name),sha(text(Path.of("src/main/resources/catalog",name))),name);
    }

    @Test void conserveExternalRegistraLaProcedenciaSinAutenticarYElReenvioNoReproyecta(){
        setup();
        for(String name:VECTORS){
            String document=vector(name);
            long com=flow.conserveExternal("receptor.srcei",scope,document).id();
            assertSrcei(db,access,com,document);
            // conserveExternal guarda el texto recibido tal cual: el blob del Formulario, con la huella de PROCEDENCIA.txt.
            var original=db.one("SELECT document,document_sha256 FROM communication_original WHERE communication_id=?",com);
            assertEquals(document,s(original,"document"));assertEquals(provenance().get(name),s(original,"document_sha256"));
            var before=snapshot(db,com);
            assertEquals(com,flow.conserveExternal("receptor.srcei",scope,document).id(),name);
            assertEquals(before,snapshot(db,com),name);
        }
    }

    @Test void vectoresAlteradosSeRechazanSinEscrituras(){
        setup();
        String contract="no corresponde al contrato",hashes="huellas",seal="firma de custodia",trust="clave confiable";
        var cases=new LinkedHashMap<String,Map.Entry<String,Consumer<ObjectNode>>>();
        // Fuente ClaveUnica sin autenticacion, nivel autenticado para SRCEI y persona marcada autenticada: el contrato.
        cases.put("fuente",Map.entry(contract,r->presenter(r).withObjectProperty("atributos").withObjectProperty("runNumero").put("fuente","CLAVE_UNICA_USERINFO")));
        cases.put("nivel",Map.entry(contract,r->presenter(r).withObjectProperty("atributos").withObjectProperty("runNumero").put("nivel","IDENTIDAD_AUTENTICADA")));
        cases.put("autenticada",Map.entry(contract,r->presenter(r).put("autenticada",true)));
        // Valor cambiado sin volver a sellar: las huellas.
        cases.put("valor",Map.entry(hashes,r->presenter(r).withObjectProperty("atributos").withObjectProperty("nombres").put("valor","OTRO NOMBRE")));
        // Firma alterada y clave no confiable: el sello.
        cases.put("firma",Map.entry(seal,r->{
            var custody=r.withObjectProperty("custodia");String f=custody.path("firma").asString();
            custody.put("firma",f.substring(0,5)+(f.charAt(5)=='A'?'B':'A')+f.substring(6));
        }));
        cases.put("clave",Map.entry(trust,r->r.withObjectProperty("custodia").put("keyId","clave-no-confiable")));
        var tables=new ArrayList<>(PROJECTION);tables.add("audit_event");
        var before=totals(db,tables);
        for(String name:VECTORS)for(var e:cases.entrySet()){
            var root=(ObjectNode)JSON.readTree(vector(name));
            e.getValue().getValue().accept(root);
            String altered=JSON.writeValueAsString(root);
            var problem=assertThrows(Problem.class,()->flow.conserveExternal("receptor.srcei",scope,altered),name+" "+e.getKey());
            assertEquals(400,problem.status,name+" "+e.getKey());
            assertTrue(problem.getMessage().contains(e.getValue().getKey()),name+" "+e.getKey()+": "+problem.getMessage());
            assertEquals(before,totals(db,tables),name+" "+e.getKey());
        }
    }
    static ObjectNode presenter(ObjectNode root){return (ObjectNode)root.withObjectProperty("comunicacion").withArrayProperty("personas").get(0);}
}
