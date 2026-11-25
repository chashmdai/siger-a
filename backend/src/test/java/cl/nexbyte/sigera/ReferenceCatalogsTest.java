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
 * Archivos de referencia de los catalogos estadisticos (0.8.0, gates E02 y E06) en deploy/config-ejemplo/catalogos, que
 * viajan en el paquete: cada archivo se importa sin errores como PROPUESTA con los conteos calculados a mano, su huella es
 * la de LEEME.txt, los valores de TIPO_INSTITUCION y RELACION son los del Formulario (formulario-web 6f886eb), el modelo de
 * derechos 2.0 conserva su estructura sin el eje VII y los catalogos de E06 (DI-01 a DI-04, docs/VALIDACION_CATALOGOS_0_8_0.md)
 * traen sus definiciones en descripcion, que SIGER guarda y publica.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_catalogos_referencia;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-catalogos-referencia-${random.uuid}"})
@ActiveProfiles("test")
class ReferenceCatalogsTest {
    static final Path DIR=Path.of("../deploy/config-ejemplo/catalogos");
    static final JsonMapper JSON=JsonMapper.builder().build();
    /** Archivo -> catalogo, items y seleccionables (contados a mano sobre las fuentes de LEEME.txt). */
    static final Map<String,List<Object>> EXPECTED=Map.ofEntries(
        Map.entry("tipo-solicitud-v1.json",List.of("TIPO_SOLICITUD",5,5)),
        Map.entry("tipo-requirente-v1.json",List.of("TIPO_REQUIRENTE",4,4)),
        Map.entry("via-ingreso-v1.json",List.of("VIA_INGRESO",15,9)),
        Map.entry("tipo-actuacion-v1.json",List.of("TIPO_ACTUACION",14,14)),
        Map.entry("tipo-institucion-v1.json",List.of("TIPO_INSTITUCION",5,5)),
        Map.entry("relacion-v1.json",List.of("RELACION",11,11)),
        Map.entry("derechos-2022-v2.json",List.of("DERECHO",81,77)),
        Map.entry("rol-estado-v1.json",List.of("ROL_ESTADO",5,5)),
        Map.entry("grupos-prioritarios-v1.json",List.of("GRUPO_PRIORITARIO",11,11)),
        Map.entry("contexto-v1.json",List.of("CONTEXTO",12,12)),
        Map.entry("caracterizacion-v1.json",List.of("CARACTERIZACION",19,19)),
        Map.entry("tramo-edad-v1.json",List.of("TRAMO_EDAD",8,8)));
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
        assertEquals(new TreeSet<>(EXPECTED.keySet()),new TreeSet<>(hashes.keySet()),"LEEME.txt declara la huella de cada archivo");
        assertFalse(Files.exists(DIR.resolve("plantillas")),"las plantillas vacias se reemplazaron por los archivos completos (E06)");
        assertEquals(new TreeSet<>(StatCatalogs.CATALOGS.keySet()),new TreeSet<>(EXPECTED.values().stream().map(v->(String)v.get(0)).toList()),"un archivo por catalogo");
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

    /** Rotulos y descripciones textuales de E06 (docs/VALIDACION_CATALOGOS_0_8_0.md y PDF del modelo 2.0); se guardan y publican. */
    @Test void catalogosDeE06ConSusDefiniciones()throws Exception{
        // Cada descripcion del archivo queda igual en stat_catalog_item.description.
        for(var name:EXPECTED.keySet()){
            long id=load(read(DIR.resolve(name))).id();
            var saved=new HashMap<String,Object>();
            for(var r:db.rows("SELECT code,description FROM stat_catalog_item WHERE version_id=?",id))saved.put(s(r,"code"),r.get("description"));
            for(var i:JSON.readTree(read(DIR.resolve(name))).path("items"))
                assertEquals(i.has("descripcion")?i.path("descripcion").asString():null,saved.get(i.path("code").asString()),name+" "+i.path("code").asString());
        }
        // Tipo de actuacion: 14 valores con los codigos de E02 conservados y la regla de "Oficio" en su descripcion; publicada al activar.
        assertTrue(codes("tipo-actuacion-v1.json").containsAll(List.of("ORIENTACION","RESPUESTA_DIRECTA","DERIVACION_EXTERNA","OFICIO","DENUNCIA_ADMINISTRATIVA","DENUNCIA_PENAL","MEDIDA_PROTECCION","QUERELLA","ACCION_CONSTITUCIONAL","AMICUS_CURIAE","OTRA")));
        long act=n(db.one("SELECT id FROM stat_catalog_version WHERE catalog='TIPO_ACTUACION'"),"id");
        flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_ACTIVATE",act,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",act),"revision"),Map.of("reason","Activacion sintetica")));
        @SuppressWarnings("unchecked") var all=(List<Map<String,Object>>)((Map<String,Object>)queries.catalog(access.actor("profesional")).get("stat_catalogs")).get("catalogs");
        assertEquals(12,all.size(),"/api/catalog publica los 12 catalogos (con o sin vigente)");
        @SuppressWarnings("unchecked") var published=(List<Map<String,Object>>)all.stream().filter(c->"TIPO_ACTUACION".equals(c.get("catalog"))).findFirst().orElseThrow().get("items");
        assertEquals("Oficio cuyo propósito no corresponde a otro tipo de actuación.",published.stream().filter(i->"OFICIO".equals(i.get("code"))).findFirst().orElseThrow().get("description"));
        assertEquals("Solicitud de medida de protección",published.stream().filter(i->"MEDIDA_PROTECCION".equals(i.get("code"))).findFirst().orElseThrow().get("label"));
        // Via: 6 agrupaciones como padres no seleccionables y las 9 vias, con sus codigos de siempre, bajo una agrupacion.
        long via=n(db.one("SELECT id FROM stat_catalog_version WHERE catalog='VIA_INGRESO'"),"id");
        var vias=db.rows("SELECT code,parent_code,level,selectable FROM stat_catalog_item WHERE version_id=? ORDER BY sort",via);
        assertEquals(List.of("GRUPO_DIGITAL","GRUPO_PRESENCIAL","GRUPO_TELEFONICA","GRUPO_DOCUMENTAL","GRUPO_INTERNA","GRUPO_OTRA"),vias.stream().filter(r->n(r,"level")==1).map(r->s(r,"code")).toList());
        assertTrue(vias.stream().filter(r->n(r,"level")==1).noneMatch(r->b(r,"selectable")));
        assertEquals(new TreeSet<>(StatValues.CHANNEL_VIA.values()),new TreeSet<>(vias.stream().filter(r->n(r,"level")==2&&b(r,"selectable")).map(r->s(r,"code")).toList()));
        // Grupos prioritarios: las 11 denominaciones textuales de la seccion VIII del PDF (pags. 93-95).
        assertEquals(List.of("Niños, niñas y adolescentes bajo cuidado alternativo y privación de libertad del Estado","Niños, niñas y adolescentes bajo medidas administrativas o judiciales",
            "Niños, niñas y adolescentes con discapacidad","Niños, niñas y adolescentes de pueblos originarios y tribales","Niños, niñas y adolescentes migrantes",
            "Niños, niñas y adolescentes en situación de calle","Niñas y adolescentes (mujeres)","Niños, niñas y adolescentes en unidades de hospitalización",
            "Niños, niñas y adolescentes con necesidades especiales de atención de salud (Naneas)","Niños, niñas y adolescentes diversos en su sexualidad",
            "Niños, niñas y adolescentes de zonas aisladas e insulares"),labels("grupos-prioritarios-v1.json"));
        // Rol del Estado: el eje VII transcrito en E02, con sus codigos y rotulos de la Tabla 2.
        assertEquals(List.of("7.01","7.02","7.03","7.04","7.05"),codes("rol-estado-v1.json"));
        assertEquals(List.of("Estrategia y coordinación efectiva para la aplicación de la Convención","Enfoque de derechos en las políticas públicas",
            "Presupuesto con enfoque de derechos de la niñez y la adolescencia","Vigilancia independiente de derechos humanos de la niñez y adolescencia",
            "Participación de la sociedad civil, sector empresarial y cooperación internacional"),labels("rol-estado-v1.json"));
        // Contexto (7 + 5 bajo Institucional), caracterizacion (10 + 9) y tramos de 0 a 130.
        for(var e:Map.of("CONTEXTO",List.of(7L,5L),"CARACTERIZACION",List.of(10L,9L)).entrySet()){
            long v=n(db.one("SELECT id FROM stat_catalog_version WHERE catalog=?",e.getKey()),"id");
            assertEquals(e.getValue(),List.of(db.count("SELECT COUNT(*) FROM stat_catalog_item WHERE version_id=? AND level=1",v),db.count("SELECT COUNT(*) FROM stat_catalog_item WHERE version_id=? AND level=2",v)),e.getKey());
        }
        long age=n(db.one("SELECT id FROM stat_catalog_version WHERE catalog='TRAMO_EDAD'"),"id");
        assertEquals(List.of("0-5","6-9","10-13","14-17","18-29","30-44","45-59","60-130"),db.rows("SELECT range_from,range_to FROM stat_catalog_item WHERE version_id=? ORDER BY sort",age).stream().map(r->n(r,"range_from")+"-"+n(r,"range_to")).toList());
    }
    @Test void descripcionAcotadaYTodoONada(){
        var before=snapshot();
        String text=StatCatalogsTest.catalog("CONTEXTO",List.of(StatCatalogsTest.item("A","descripcion","Definicion valida"),StatCatalogsTest.item("B","descripcion","d".repeat(1001))));
        var p=assertThrows(Problem.class,()->load(text));
        assertEquals(400,p.status);assertTrue(p.getMessage().contains("descripcion admite"),p.getMessage());
        assertEquals(before,snapshot(),"sin escrituras");
    }
    static List<String> labels(String name)throws Exception{
        var out=new ArrayList<String>();for(var i:JSON.readTree(read(DIR.resolve(name))).path("items"))out.add(i.path("label").asString());return out;
    }
    static List<String> codes(String name)throws Exception{
        var out=new ArrayList<String>();for(var i:JSON.readTree(read(DIR.resolve(name))).path("items"))out.add(i.path("code").asString());return out;
    }

    @Test void institucionYRelacionConLosValoresDelFormulario()throws Exception{
        // Valores de formulario-web 6f886eb (ValidacionAdultos.java: instituciones[].tipo y campo relacion), en source_ref.
        assertEquals(List.of("Establecimiento educativo","Organismo público","Empresa o entidad privada","Fundación u ONG","Otra institución"),refs("tipo-institucion-v1.json"));
        // E06 (DI-03): los 9 valores del Formulario intactos y dos exclusivos de SIGER.
        assertEquals(List.of("madre","padre","cuidador","otro-familiar","docente","salud","testigo","organizacion","exclusivo de SIGER (DI-03)","exclusivo de SIGER (DI-03)","otro"),refs("relacion-v1.json"));
        assertEquals(List.of("MADRE","PADRE","CUIDADOR","OTRO_FAMILIAR","DOCENTE","SALUD","TESTIGO","ORGANIZACION","PROPIO_NNA","REPRESENTANTE_LEGAL","OTRO"),codes("relacion-v1.json"));
        // La via conserva el canal de origen de cada valor (D31); las agrupaciones (E06) no tienen canal.
        assertEquals(new TreeSet<>(List.of("FORMULARIO_WEB_PUBLICO","FORMULARIO_WEB_NNA","PRESENCIAL","CORREO","TELEFONO","OFICIO","REDES_SOCIALES","INTEGRANTE_DEFENSORIA","OTRO")),
            new TreeSet<>(refs("via-ingreso-v1.json").stream().filter(r->r.contains("canal ")).map(r->r.substring(r.indexOf("canal ")+6)).toList()));
    }
    static List<String> refs(String name)throws Exception{
        var out=new ArrayList<String>();for(var i:JSON.readTree(read(DIR.resolve(name))).path("items"))out.add(i.path("source_ref").asString());return out;
    }

    @Test void derechosModeloDosCeroConSuEstructura()throws Exception{
        long id=load(read(DIR.resolve("derechos-2022-v2.json"))).id();
        var items=db.rows("SELECT code,parent_code,level,selectable,source_ref FROM stat_catalog_item WHERE version_id=? ORDER BY sort",id);
        // Ejes III-VI (nivel 1, no seleccionables); 17 derechos (4, 4, 5, 4) y 60 subcategorias. El eje VII salio a ROL_ESTADO (E06).
        assertEquals(List.of("3","4","5","6"),items.stream().filter(r->n(r,"level")==1).map(r->s(r,"code")).toList());
        assertTrue(items.stream().filter(r->n(r,"level")==1).noneMatch(r->b(r,"selectable")));
        var rights=items.stream().filter(r->n(r,"level")==2&&!"7".equals(s(r,"parent_code"))).toList();
        assertEquals(17,rights.size());
        for(var axis:Map.of("3",4,"4",4,"5",5,"6",4).entrySet())assertEquals((long)axis.getValue(),rights.stream().filter(r->s(r,"parent_code").equals(axis.getKey())).count(),axis.getKey());
        assertTrue(rights.stream().allMatch(r->b(r,"selectable")&&s(r,"code").matches("[3-6]\\.[1-5]")));
        var subs=items.stream().filter(r->n(r,"level")==3).toList();
        assertEquals(60,subs.size());assertTrue(subs.stream().allMatch(r->b(r,"selectable")&&s(r,"code").matches("[3-6]\\.[1-5]\\.\\d{2}")&&s(r,"code").startsWith(s(r,"parent_code")+".")));
        assertTrue(items.stream().noneMatch(r->s(r,"code").startsWith("7")),"sin el eje VII");
        assertEquals(81,items.size());
        assertTrue(items.stream().allMatch(r->s(r,"source_ref").matches("Tabla 2, p\\. (9|10); .+, p\\. \\d{1,2}")),"cada item cita la Tabla 2 y su pagina");
        // Activada (solo en esta prueba), /api/catalog la publica con sus 81 items.
        flow.run("ti",new Workflow.Command(UUID.randomUUID(),"CATALOG_ACTIVATE",id,n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",id),"revision"),Map.of("reason","Activacion sintetica")));
        @SuppressWarnings("unchecked") var all=(List<Map<String,Object>>)((Map<String,Object>)queries.catalog(access.actor("profesional")).get("stat_catalogs")).get("catalogs");
        var derechos=all.stream().filter(c->"DERECHO".equals(c.get("catalog"))).findFirst().orElseThrow();
        assertEquals(81,((List<?>)derechos.get("items")).size());
    }
}
