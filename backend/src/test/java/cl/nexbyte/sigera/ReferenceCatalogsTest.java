package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Archivos de referencia de los catalogos estadisticos (0.8.0, gate E02) en deploy/config-ejemplo/catalogos, que viajan
 * en el paquete: cada archivo se importa sin errores como PROPUESTA con los conteos calculados a mano, su huella es la de
 * LEEME.txt, las plantillas (DI-02) solo se rechazan por no tener items y sin escrituras, los valores de TIPO_INSTITUCION y
 * RELACION son los del Formulario (formulario-web 6f886eb) y el modelo de derechos 2.0 conserva su estructura.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_catalogos_referencia;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-catalogos-referencia-${random.uuid}"})
@ActiveProfiles("test")
class ReferenceCatalogsTest {
    static final Path DIR=Path.of("../deploy/config-ejemplo/catalogos");
    static final JsonMapper JSON=JsonMapper.builder().build();
    /** Archivo -> catalogo, items y seleccionables (contados a mano sobre las fuentes de LEEME.txt). */
    static final Map<String,List<Object>> EXPECTED=Map.of(
        "tipo-solicitud-v1.json",List.of("TIPO_SOLICITUD",5,5),
        "tipo-requirente-v1.json",List.of("TIPO_REQUIRENTE",4,4),
        "via-ingreso-v1.json",List.of("VIA_INGRESO",9,9),
        "tipo-actuacion-v1.json",List.of("TIPO_ACTUACION",11,11),
        "tipo-institucion-v1.json",List.of("TIPO_INSTITUCION",5,5),
        "relacion-v1.json",List.of("RELACION",9,9),
        "derechos-2022-v2.json",List.of("DERECHO",87,77));
    static final Map<String,String> TEMPLATES=Map.of("contexto.json","CONTEXTO","caracterizacion.json","CARACTERIZACION","tramo-edad.json","TRAMO_EDAD");
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;

    @BeforeEach void clean(){
        db.update("UPDATE staff SET must_change=FALSE");
        for(String t:List.of("stat_catalog_mapping","stat_catalog_item","stat_catalog_version"))db.update("DELETE FROM "+t);
    }
    static String read(Path p)throws Exception{return Files.readString(p,StandardCharsets.UTF_8);}
    Workflow.Result load(String content){return flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_IMPORT",null,null,Map.of("content",content,"reason","Propuesta de referencia")));}
    List<Long> snapshot(){
        var out=new ArrayList<Long>();
        for(String t:List.of("stat_catalog_version","stat_catalog_item","stat_catalog_mapping","audit_event","command_log"))out.add(db.count("SELECT COUNT(*) FROM "+t));
        return out;
    }
    /** Huellas de LEEME.txt: lineas "<sha256>  <archivo>". */
    static Map<String,String> readmeHashes()throws Exception{
        var out=new LinkedHashMap<String,String>();var line=Pattern.compile("^\\s*([0-9a-f]{64})\\s{2}(\\S+)\\s*$");
        for(var l:Files.readAllLines(DIR.resolve("LEEME.txt"),StandardCharsets.UTF_8)){var m=line.matcher(l);if(m.matches())out.put(m.group(2),m.group(1));}
        return out;
    }

    @Test void cadaArchivoSeImportaComoPropuestaConSuHuella()throws Exception{
        Set<String> files;
        try(Stream<Path> s=Files.list(DIR)){files=new TreeSet<>(s.filter(p->p.toString().endsWith(".json")).map(p->p.getFileName().toString()).toList());}
        assertEquals(new TreeSet<>(EXPECTED.keySet()),files,"todo archivo de referencia esta cubierto por esta prueba");
        var hashes=readmeHashes();
        var expectedHashes=new TreeSet<String>(EXPECTED.keySet());TEMPLATES.keySet().forEach(t->expectedHashes.add("plantillas/"+t));
        assertEquals(expectedHashes,new TreeSet<>(hashes.keySet()),"LEEME.txt declara la huella de cada archivo");
        for(var e:EXPECTED.entrySet()){
            String name=e.getKey(),catalog=(String)e.getValue().get(0);
            long id=load(read(DIR.resolve(name))).id();
            var v=db.one("SELECT * FROM stat_catalog_version WHERE id=?",id);
            assertEquals(catalog,s(v,"catalog"),name);assertEquals("PROPUESTA",s(v,"state"),name);
            assertEquals(hashes.get(name),s(v,"file_sha256"),name+": huella de LEEME.txt (sin BOM y con LF)");
            assertEquals(((Integer)e.getValue().get(1)).longValue(),db.count("SELECT COUNT(*) FROM stat_catalog_item WHERE version_id=?",id),name);
            assertEquals(((Integer)e.getValue().get(2)).longValue(),db.count("SELECT COUNT(*) FROM stat_catalog_item WHERE version_id=? AND selectable=TRUE",id),name);
        }
        // Un mismo archivo no se importa dos veces (409) y una propuesta no se publica.
        assertEquals(409,assertThrows(Problem.class,()->load(read(DIR.resolve("relacion-v1.json")))).status);
        assertEquals(0,db.count("SELECT COUNT(*) FROM stat_catalog_version WHERE state<>'PROPUESTA'"));
    }

    @Test void plantillasSoloSeRechazanPorNoTenerItems()throws Exception{
        var hashes=readmeHashes();
        for(var t:TEMPLATES.entrySet()){
            String text=read(DIR.resolve("plantillas").resolve(t.getKey()));
            JsonNode root=JSON.readTree(text);
            assertEquals(Set.of("format","catalog","label","source","items"),new TreeSet<>(root.properties().stream().map(Map.Entry::getKey).toList()),t.getKey());
            assertEquals(StatCatalogs.FORMAT,root.path("format").asString());assertEquals(t.getValue(),root.path("catalog").asString());
            assertTrue(root.path("items").isArray()&&root.path("items").isEmpty(),t.getKey());
            assertEquals(hashes.get("plantillas/"+t.getKey()),StatCatalogs.sha(text.replace("\r\n","\n")),t.getKey());
            var before=snapshot();
            var p=assertThrows(Problem.class,()->load(text));
            assertEquals(400,p.status);assertEquals("El catálogo no tiene ítems.",p.getMessage(),t.getKey());
            assertEquals(before,snapshot(),t.getKey()+": sin escrituras");
        }
    }

    @Test void institucionYRelacionConLosValoresDelFormulario()throws Exception{
        // Valores de formulario-web 6f886eb (ValidacionAdultos.java: instituciones[].tipo y campo relacion), en source_ref.
        assertEquals(List.of("Establecimiento educativo","Organismo público","Empresa o entidad privada","Fundación u ONG","Otra institución"),refs("tipo-institucion-v1.json"));
        assertEquals(List.of("madre","padre","cuidador","otro-familiar","docente","salud","testigo","organizacion","otro"),refs("relacion-v1.json"));
        // La via conserva el canal de origen de cada valor (D31).
        assertEquals(List.of("FORMULARIO_WEB_PUBLICO","FORMULARIO_WEB_NNA","PRESENCIAL","CORREO","TELEFONO","OFICIO","REDES_SOCIALES","INTEGRANTE_DEFENSORIA","OTRO"),
            refs("via-ingreso-v1.json").stream().map(r->r.substring(r.indexOf("canal ")+6)).toList());
    }
    static List<String> refs(String name)throws Exception{
        var out=new ArrayList<String>();for(var i:JSON.readTree(read(DIR.resolve(name))).path("items"))out.add(i.path("source_ref").asString());return out;
    }

    @Test void derechosModeloDosCeroConSuEstructura()throws Exception{
        long id=load(read(DIR.resolve("derechos-2022-v2.json"))).id();
        var items=db.rows("SELECT code,parent_code,level,selectable,source_ref FROM stat_catalog_item WHERE version_id=? ORDER BY sort",id);
        // Ejes III-VI y seccion VII (nivel 1, no seleccionables); 17 derechos (4, 4, 5, 4) y 65 subcategorias.
        assertEquals(List.of("3","4","5","6","7"),items.stream().filter(r->n(r,"level")==1).map(r->s(r,"code")).toList());
        assertTrue(items.stream().filter(r->n(r,"level")==1).noneMatch(r->b(r,"selectable")));
        var rights=items.stream().filter(r->n(r,"level")==2&&!"7".equals(s(r,"parent_code"))).toList();
        assertEquals(17,rights.size());
        for(var axis:Map.of("3",4,"4",4,"5",5,"6",4).entrySet())assertEquals((long)axis.getValue(),rights.stream().filter(r->s(r,"parent_code").equals(axis.getKey())).count(),axis.getKey());
        assertTrue(rights.stream().allMatch(r->b(r,"selectable")&&s(r,"code").matches("[3-6]\\.[1-5]")));
        var subs=items.stream().filter(r->n(r,"level")==3).toList();
        assertEquals(60,subs.size());assertTrue(subs.stream().allMatch(r->b(r,"selectable")&&s(r,"code").matches("[3-6]\\.[1-5]\\.\\d{2}")&&s(r,"code").startsWith(s(r,"parent_code")+".")));
        // VII Rol del Estado: cinco subcategorias transcritas, no seleccionables (DI-04); VIII no se transcribe.
        var state=items.stream().filter(r->"7".equals(s(r,"parent_code"))).toList();
        assertEquals(5,state.size());assertTrue(state.stream().noneMatch(r->b(r,"selectable")));
        assertEquals(87,items.size());
        assertTrue(items.stream().allMatch(r->s(r,"source_ref").matches("Tabla 2, p\\. (9|10); .+, p\\. \\d{1,2}")),"cada item cita la Tabla 2 y su pagina");
        // Activada (solo en esta prueba), /api/catalog la publica con sus 87 items.
        flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_ACTIVATE",id,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",id),"revision"),Map.of("reason","Activacion sintetica")));
        @SuppressWarnings("unchecked") var all=(List<Map<String,Object>>)((Map<String,Object>)queries.catalog(access.actor("profesional")).get("stat_catalogs")).get("catalogs");
        var derechos=all.stream().filter(c->"DERECHO".equals(c.get("catalog"))).findFirst().orElseThrow();
        assertEquals(87,((List<?>)derechos.get("items")).size());
    }
}
