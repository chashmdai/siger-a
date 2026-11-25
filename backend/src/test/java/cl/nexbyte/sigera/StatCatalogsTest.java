package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Catalogos estadisticos versionados (0.8.0, D27, gate E01): importacion de TI todo o nada con lista cerrada de campos,
 * activacion con fundamento que retira la vigente anterior, tramos de edad contiguos de 0 a 130, correspondencias entre
 * versiones, huella sin BOM y con LF, reimportacion de una retirada, idempotencia, solo TI (comandos y lecturas), TI sin
 * expedientes ni reportes (D2), auditoria sin contenido y publicacion en /api/catalog solo de lo vigente.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_catalogos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-catalogos-${random.uuid}"})
@ActiveProfiles("test")
class StatCatalogsTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String REASON="Resolucion exenta sintetica de catalogos";
    @Autowired Workflow flow;@Autowired Db db;@Autowired Access access;@Autowired Queries queries;@Autowired StatCatalogs catalogs;@Autowired Reports reports;

    @BeforeEach void clean(){
        db.update("UPDATE staff SET must_change=FALSE");
        for(String t:List.of("stat_catalog_mapping","stat_catalog_item","stat_catalog_version"))db.update("DELETE FROM "+t);
    }

    // --- Archivos sinteticos -----------------------------------------------------------------------------------------
    static String json(Object value){return JSON.writeValueAsString(value);}
    static Map<String,Object> item(String code,Object... more){
        var m=new LinkedHashMap<String,Object>();m.put("code",code);m.put("label","Valor "+code);
        for(int i=0;i<more.length;i+=2)m.put((String)more[i],more[i+1]);return m;
    }
    static String catalog(String catalog,List<?> items){
        var m=new LinkedHashMap<String,Object>();m.put("format",StatCatalogs.FORMAT);m.put("catalog",catalog);m.put("label","Version sintetica");
        m.put("source","Documento sintetico de prueba, version 1");m.put("items",items);return json(m);
    }
    /** Catalogo plano con los codigos dados (usado tambien por el barrido de InstitutionalReaderTest). */
    static String file(String catalog,String... codes){return catalog(catalog,Arrays.stream(codes).map(c->item(c)).toList());}
    /** Correspondencia con pares (origen, destino) alternados. */
    static String mapping(String... pairs){
        var list=new ArrayList<Map<String,Object>>();for(int i=0;i<pairs.length;i+=2)list.add(Map.of("from",pairs[i],"to",pairs[i+1]));
        return json(Map.of("format",StatCatalogs.MAPPING_FORMAT,"pairs",list));
    }
    static String ages(int... bounds){
        var items=new ArrayList<Map<String,Object>>();
        for(int i=0;i<bounds.length;i+=2)items.add(item("T"+i,"range_from",bounds[i],"range_to",bounds[i+1]));
        return catalog("TRAMO_EDAD",items);
    }

    // --- Ejecucion ----------------------------------------------------------------------------------------------------
    Long rev(long version){return n(db.one("SELECT revision FROM stat_catalog_version WHERE id=?",version),"revision");}
    Workflow.Result run(String who,String type,Long id,Long rev,Map<String,Object> data){return flow.run(who,new Workflow.Command(UUID.randomUUID(),type,id,rev,data));}
    long load(String content){return run("ti","CATALOG_IMPORT",null,null,Map.of("content",content,"reason",REASON)).id();}
    void activate(long version){run("ti","CATALOG_ACTIVATE",version,rev(version),Map.of("reason","Activacion sintetica"));}
    Problem refusal(String who,String type,Long id,Long rev,Map<String,Object> data){return assertThrows(Problem.class,()->run(who,type,id,rev,data));}
    Problem refusedImport(String content){return refusal("ti","CATALOG_IMPORT",null,null,Map.of("content",content,"reason",REASON));}
    static int read(Supplier<?> call){try{call.get();return 200;}catch(Problem p){return p.status;}}
    String state(long version){return s(db.one("SELECT state FROM stat_catalog_version WHERE id=?",version),"state");}
    /** Filas de las tablas de catalogos y de la auditoria: una operacion rechazada no las cambia. */
    List<Long> snapshot(){
        var out=new ArrayList<Long>();
        for(String t:List.of("stat_catalog_version","stat_catalog_item","stat_catalog_mapping","audit_event","command_log"))out.add(db.count("SELECT COUNT(*) FROM "+t));
        out.add(db.count("SELECT COALESCE(SUM(revision),0) FROM stat_catalog_version"));
        return out;
    }
    @SuppressWarnings("unchecked") Map<String,Object> published(String catalog){
        var all=(List<Map<String,Object>>)((Map<String,Object>)queries.catalog(access.actor("profesional")).get("stat_catalogs")).get("catalogs");
        return all.stream().filter(c->catalog.equals(c.get("catalog"))).findFirst().orElseThrow();
    }
    static String sha(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}

    @Test void importarActivarRetirarYReimportar()throws Exception{
        // Archivo con BOM y CRLF: la huella es la del texto sin BOM y con LF; jerarquia, orden y nivel los calcula el servidor.
        String lf=catalog("TIPO_SOLICITUD",List.of(item("VULNERACION","source_ref","p. 3"),item("VULNERACION.GRAVE","parent","VULNERACION"),
            item("CONSULTA"),item("OTRO","selectable",false)))+"\n";
        long v1=load("﻿"+lf.replace("\n","\r\n"));
        var row=db.one("SELECT * FROM stat_catalog_version WHERE id=?",v1);
        assertEquals("PROPUESTA",s(row,"state"));assertNull(row.get("current_catalog"));assertEquals(sha(lf),s(row,"file_sha256"));
        assertEquals("Documento sintetico de prueba, version 1",s(row,"source"));assertEquals(REASON,s(row,"basis"));
        var items=db.rows("SELECT code,parent_code,level,sort,selectable,source_ref FROM stat_catalog_item WHERE version_id=? ORDER BY sort",v1);
        assertEquals(List.of("VULNERACION","VULNERACION.GRAVE","CONSULTA","OTRO"),items.stream().map(i->s(i,"code")).toList());
        assertEquals(List.of(1L,2L,1L,1L),items.stream().map(i->n(i,"level")).toList());
        assertEquals("VULNERACION",s(items.get(1),"parent_code"));assertEquals("p. 3",s(items.get(0),"source_ref"));assertFalse(b(items.get(3),"selectable"));
        var imported=db.one("SELECT * FROM audit_event WHERE action=? AND resource_id=?",StatCatalogs.IMPORTED,v1);
        assertEquals(StatCatalogs.KIND,s(imported,"resource_type"));
        assertEquals("TIPO_SOLICITUD versión "+v1+" propuesta: 4 ítems, huella "+sha(lf).substring(0,12)+". "+REASON,s(imported,"note"),"sin contenido del archivo");
        assertNull(published("TIPO_SOLICITUD").get("version"),"una propuesta no se publica");

        // Activar exige fundamento (ajuste 5 del GO) y la revision vigente; un rechazo no escribe.
        var before=snapshot();
        assertEquals(400,refusal("ti","CATALOG_ACTIVATE",v1,rev(v1),Map.of()).status);
        assertEquals(400,refusal("ti","CATALOG_ACTIVATE",v1,rev(v1),Map.of("reason","   ")).status);
        assertEquals(409,refusal("ti","CATALOG_ACTIVATE",v1,rev(v1)+1,Map.of("reason","x")).status);
        assertEquals(before,snapshot());
        activate(v1);
        row=db.one("SELECT * FROM stat_catalog_version WHERE id=?",v1);
        assertEquals("VIGENTE",s(row,"state"));assertEquals("TIPO_SOLICITUD",s(row,"current_catalog"));assertEquals("Activacion sintetica",s(row,"activation_basis"));
        assertEquals(n(db.one("SELECT id FROM staff WHERE username='ti'"),"id"),n(row,"activated_by"));
        assertEquals("TIPO_SOLICITUD versión "+v1+" vigente. Activacion sintetica",s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=?",StatCatalogs.ACTIVATED,v1),"note"));
        var pub=published("TIPO_SOLICITUD");
        assertEquals(v1,((Number)((Map<?,?>)pub.get("version")).get("id")).longValue());
        assertEquals(4,((List<?>)pub.get("items")).size());
        assertNull(published("DERECHO").get("version"),"un catalogo sin vigente se publica sin version");
        assertEquals(4,((List<?>)((Map<?,?>)queries.catalog(access.actor("profesional")).get("stat_catalogs")).get("special")).size());

        // Segunda activacion: retira la anterior en el mismo acto; una sola VIGENTE por catalogo.
        long v2=load(file("TIPO_SOLICITUD","VULNERACION","CONSULTA","PROMOCION"));
        activate(v2);
        assertEquals("RETIRADA",state(v1));assertEquals("VIGENTE",state(v2));
        row=db.one("SELECT * FROM stat_catalog_version WHERE id=?",v1);assertNull(row.get("current_catalog"));assertNotNull(row.get("retired_ms"));assertNotNull(row.get("retired_by"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM stat_catalog_version WHERE catalog='TIPO_SOLICITUD' AND state='VIGENTE'"));
        assertTrue(s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=?",StatCatalogs.ACTIVATED,v2),"note").contains("retira la versión "+v1));
        assertEquals(v2,((Number)((Map<?,?>)published("TIPO_SOLICITUD").get("version")).get("id")).longValue());
        assertEquals(409,refusal("ti","CATALOG_ACTIVATE",v1,rev(v1),Map.of("reason","x")).status,"una retirada no se reactiva");
        assertEquals(409,refusal("ti","CATALOG_ACTIVATE",v2,rev(v2),Map.of("reason","x")).status,"la vigente no se reactiva");

        // Un archivo identico a una PROPUESTA o VIGENTE se rechaza; identico a una RETIRADA crea otra PROPUESTA (ajuste 6).
        assertEquals(409,refusedImport(file("TIPO_SOLICITUD","VULNERACION","CONSULTA","PROMOCION")).status);
        long v3=load(lf);
        assertEquals("PROPUESTA",state(v3));assertEquals(sha(lf),s(db.one("SELECT file_sha256 FROM stat_catalog_version WHERE id=?",v3),"file_sha256"));
        assertEquals(409,refusedImport("﻿"+lf).status,"misma huella que la propuesta");
        assertEquals("VIGENTE",state(v2));
        // El mismo archivo en otro catalogo es otra version.
        assertEquals("PROPUESTA",state(load(file("TIPO_REQUIRENTE","VULNERACION","CONSULTA","PROMOCION"))));
    }

    @Test void importacionInvalidaSinEscrituras(){
        load(file("VIA_INGRESO","A"));
        var before=snapshot();
        var cases=new LinkedHashMap<String,String>();
        cases.put(file("VIA_INGRESO","A","B","A"),"está repetido");
        cases.put(catalog("VIA_INGRESO",List.of(item("A"),item("B","parent","X"))),"el padre X no existe");
        cases.put(catalog("VIA_INGRESO",List.of(item("B","parent","A"),item("A"))),"el padre A no existe o aparece después");
        cases.put(file("VIA_INGRESO","A","SIN_INFORMACION"),"reservado para un valor especial");
        cases.put(file("VIA_INGRESO","a"),"código inválido");
        cases.put(catalog("VIA_INGRESO",List.of(item("A","color","rojo"))),"campos no admitidos [color]");
        cases.put(catalog("VIA_INGRESO",List.of(item("A","range_from",0,"range_to",5))),"solo los tramos de edad tienen rango");
        cases.put(catalog("VIA_INGRESO",List.of(item("A","selectable",false))),"ningún ítem seleccionable");
        cases.put(catalog("VIA_INGRESO",List.of(item("A","selectable","si"))),"selectable debe ser verdadero o falso");
        cases.put(file("ESTADO_CIVIL","A"),"Catálogo desconocido");
        cases.put(file("VIA_INGRESO").replace(StatCatalogs.FORMAT,"otro-formato"),"Formato desconocido");
        cases.put(file("VIA_INGRESO","A").replace("\"format\"","\"extra\":1,\"format\""),"Campos no admitidos en el archivo: [extra]");
        cases.put(file("VIA_INGRESO"),"no tiene ítems");
        cases.put("{\"format\":\""+StatCatalogs.FORMAT+"\",\"format\":\""+StatCatalogs.FORMAT+"\"}","claves repetidas");
        cases.put("{no es json","no es un catálogo válido");
        cases.put("[]","debe ser un objeto JSON");
        for(var e:cases.entrySet()){
            var p=refusedImport(e.getKey());
            assertEquals(400,p.status,e.getValue());assertTrue(p.getMessage().contains(e.getValue()),p.getMessage());
            assertEquals(before,snapshot(),"sin escrituras: "+e.getValue());
        }
        // Varios problemas: todos en un mensaje, ninguno grabado.
        var many=refusedImport(catalog("VIA_INGRESO",List.of(item("A"),item("A"),item("B","parent","Z"))));
        assertTrue(many.getMessage().contains("Ítem 2:")&&many.getMessage().contains("Ítem 3:"),many.getMessage());
        // Sin fundamento, sin archivo o con un archivo vacio: 400 sin escrituras.
        assertEquals(400,refusal("ti","CATALOG_IMPORT",null,null,Map.of("content",file("VIA_INGRESO","B"))).status);
        assertEquals(400,refusal("ti","CATALOG_IMPORT",null,null,Map.of("reason",REASON)).status);
        assertEquals(400,refusal("ti","CATALOG_IMPORT",null,null,Map.of("content","  ","reason",REASON)).status);
        assertEquals(before,snapshot());
    }

    @Test void tramosDeEdadContiguosDeCeroACientoTreinta(){
        long ok=load(ages(0,5,6,11,12,17,18,130));
        assertEquals(List.of("0-5","6-11","12-17","18-130"),db.rows("SELECT range_from,range_to FROM stat_catalog_item WHERE version_id=? ORDER BY sort",ok)
            .stream().map(r->s(r,"range_from")+"-"+s(r,"range_to")).toList());
        assertEquals("PROPUESTA",state(load(ages(18,130,0,17))),"el orden del archivo no importa");
        var before=snapshot();
        var cases=new LinkedHashMap<String,String>();
        cases.put(ages(0,5,5,17,18,130),"se superpone");
        cases.put(ages(0,5,7,17,18,130),"Falta un tramo para las edades 6-6");
        cases.put(ages(0,17),"Falta un tramo para las edades 18-130");
        cases.put(ages(1,130),"Falta un tramo para las edades 0-0");
        cases.put(ages(0,10,10,130),"se superpone");
        cases.put(ages(0,131),"en años enteros entre 0 y 130");
        cases.put(ages(0,5,6,-1),"en años enteros entre 0 y 130");
        cases.put(ages(10,5),"range_from no puede ser mayor");
        cases.put(catalog("TRAMO_EDAD",List.of(item("T0","range_from",0,"range_to",130),item("T1","parent","T0","range_from",0,"range_to",130))),"no tienen jerarquía");
        cases.put(catalog("TRAMO_EDAD",List.of(item("T0","range_from",0))),"indica range_from y range_to");
        cases.put(catalog("TRAMO_EDAD",List.of(item("T0","range_from",0.5,"range_to",130))),"indica range_from y range_to");
        for(var e:cases.entrySet()){
            var p=refusedImport(e.getKey());
            assertEquals(400,p.status,e.getValue());assertTrue(p.getMessage().contains(e.getValue()),p.getMessage());
        }
        assertEquals(before,snapshot());
    }

    @Test void correspondenciasEntreVersiones(){
        long v1=load(file("TIPO_ACTUACION","DENUNCIA","OFICIO","OTRA")),v2=load(file("TIPO_ACTUACION","DENUNCIA_PENAL","DENUNCIA_ADMINISTRATIVA","OFICIO","OTRA"));
        long other=load(file("RELACION","DENUNCIA"));
        var before=snapshot();
        var data=new HashMap<String,Object>(Map.of("from_version",v1,"to_version",v2,"reason",REASON));
        // Codigo inexistente, par repetido, formato o campos: 400 sin escrituras.
        for(var bad:Map.of(mapping("DENUNCIA","DENUNCIA_PENAL","QUERELLA","OTRA"),"QUERELLA no existe en la versión de origen",
                mapping("DENUNCIA","QUERELLA"),"QUERELLA no existe en la versión de destino",
                mapping("OFICIO","OFICIO","OFICIO","OFICIO"),"está repetido",
                json(Map.of("format","otro","pairs",List.of())),"Formato desconocido",
                json(Map.of("format",StatCatalogs.MAPPING_FORMAT,"pairs",List.of(Map.of("from","OFICIO","to","OFICIO","nota","x")))),"debe tener solo from y to").entrySet()){
            data.put("content",bad.getKey());
            var p=refusal("ti","CATALOG_MAPPING_IMPORT",null,null,data);
            assertEquals(400,p.status,bad.getValue());assertTrue(p.getMessage().contains(bad.getValue()),p.getMessage());
        }
        data.put("content",mapping("OFICIO","OFICIO"));
        assertEquals(409,refusal("ti","CATALOG_MAPPING_IMPORT",null,null,Map.of("from_version",v1,"to_version",v1,"content",mapping("OFICIO","OFICIO"),"reason",REASON)).status);
        assertEquals(409,refusal("ti","CATALOG_MAPPING_IMPORT",null,null,Map.of("from_version",v1,"to_version",other,"content",mapping("DENUNCIA","DENUNCIA"),"reason",REASON)).status);
        assertEquals(404,refusal("ti","CATALOG_MAPPING_IMPORT",null,null,Map.of("from_version",v1,"to_version",v2+99,"content",mapping("OFICIO","OFICIO"),"reason",REASON)).status);
        assertEquals(400,refusal("ti","CATALOG_MAPPING_IMPORT",null,null,Map.of("from_version",v1,"to_version",v2,"content",mapping("OFICIO","OFICIO"))).status,"fundamento obligatorio");
        assertEquals(before,snapshot());

        // Division (1 -> 2) y continuidad: todo en un acto, auditado sin contenido; una segunda carga del mismo par se rechaza.
        var result=run("ti","CATALOG_MAPPING_IMPORT",null,null,Map.of("from_version",v1,"to_version",v2,"reason",REASON,
            "content",mapping("DENUNCIA","DENUNCIA_PENAL","DENUNCIA","DENUNCIA_ADMINISTRATIVA","OFICIO","OFICIO","OTRA","OTRA")));
        assertEquals(v2,result.id());
        assertEquals(4,db.count("SELECT COUNT(*) FROM stat_catalog_mapping WHERE from_version=? AND to_version=?",v1,v2));
        assertEquals("TIPO_ACTUACION: versión "+v1+" -> versión "+v2+", 4 pares. "+REASON,
            s(db.one("SELECT note FROM audit_event WHERE action=? AND resource_id=?",StatCatalogs.MAPPED,v2),"note"));
        assertEquals(409,refusal("ti","CATALOG_MAPPING_IMPORT",null,null,Map.of("from_version",v1,"to_version",v2,"content",mapping("OTRA","OTRA"),"reason",REASON)).status);

        // Lectura de TI: versiones con su cantidad de items y la correspondencia; detalle de una version.
        var admin=catalogs.administration(access.actor("ti"));
        assertEquals(3,((List<?>)admin.get("versions")).size());
        var summary=(Map<?,?>)((List<?>)admin.get("mappings")).getFirst();
        assertEquals(4L,((Number)summary.get("pairs")).longValue());
        var detail=catalogs.version(access.actor("ti"),v1);
        assertEquals("PROPUESTA",detail.get("state"));assertEquals("Tipo de actuación",detail.get("catalog_label"));
        assertEquals(3,((List<?>)detail.get("items")).size());assertEquals(4,((List<?>)detail.get("mappings")).size());
        assertEquals(404,read(()->catalogs.version(access.actor("ti"),v2+99)));
    }

    @Test void soloTiYTiSinExpedientes(){
        long v=load(file("CONTEXTO","ESCUELA","SALUD"));long w=load(file("CONTEXTO","ESCUELA","SALUD","CALLE"));
        long uep=n(db.one("SELECT id FROM staff WHERE username='direccion.uep'"),"id");
        for(String role:List.of("REPORTES","EXPORTACION"))db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,?,NULL,0,'PRUEBA AUTOMATICA')",uep,role);
        db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'CONSULTA_INSTITUCIONAL',NULL,0,'PRUEBA AUTOMATICA')",n(db.one("SELECT id FROM staff WHERE username='profesional.ugi'"),"id"));
        var before=snapshot();
        var attempts=new LinkedHashMap<String,Object[]>();
        attempts.put("CATALOG_IMPORT",new Object[]{null,null,Map.of("content",file("CONTEXTO","OTRO"),"reason",REASON)});
        attempts.put("CATALOG_ACTIVATE",new Object[]{v,rev(v),Map.of("reason",REASON)});
        attempts.put("CATALOG_MAPPING_IMPORT",new Object[]{null,null,Map.of("from_version",v,"to_version",w,"content",mapping("SALUD","SALUD"),"reason",REASON)});
        // REPORTES con EXPORTACION, AUDITORIA, DIRECCION, LECTURA, Coordinacion regional, ADMINISTRATIVO y CONSULTA_INSTITUCIONAL.
        for(String who:List.of("direccion.uep","auditoria","direccion.upr","gabinete","coordinacion.regional","administrativo","profesional.ugi")){
            for(var e:attempts.entrySet())assertEquals(403,refusal(who,e.getKey(),(Long)e.getValue()[0],(Long)e.getValue()[1],(Map<String,Object>)e.getValue()[2]).status,who+" "+e.getKey());
            assertEquals(403,read(()->catalogs.administration(access.actor(who))),who);
            assertEquals(403,read(()->catalogs.version(access.actor(who),v)),who);
        }
        assertEquals(before,snapshot());
        assertEquals(200,read(()->catalogs.administration(access.actor("ti"))));

        // D2: administrar catalogos no da a TI lectura de expedientes ni reportes.
        long com=run("administrativo","COM_CREATE",null,null,Map.of("intake",Map.of(),"scope_id",n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id"),"channel","TELEFONO",
            "received_at",java.time.Instant.now().minusSeconds(60).toString(),"subject","Ingreso sintetico","body","Relato sintetico")).id();
        activate(v);
        var ti=access.actor("ti");
        assertEquals(403,read(()->queries.detail(ti,"COM",com)));
        assertEquals(403,read(()->reports.report(ti,new Reports.Request("VOLUMEN",null,null,null,null,null,null,null,null,null,null,null,null,false,null,null))));
        // La auditoria del recurso la ven TI y AUDITORIA en Administracion.
        for(String who:List.of("ti","auditoria")){
            @SuppressWarnings("unchecked") var audit=(List<Map<String,Object>>)queries.administration(access.actor(who)).get("audit");
            assertTrue(audit.stream().anyMatch(r->StatCatalogs.KIND.equals(r.get("resource_type"))&&StatCatalogs.ACTIVATED.equals(r.get("action"))),who);
        }
    }

    @Test void idempotenciaDeLaClave(){
        var key=UUID.randomUUID();var data=Map.<String,Object>of("content",file("RELACION","MADRE","PADRE"),"reason",REASON);
        var first=flow.run("ti",new Workflow.Command(key,"CATALOG_IMPORT",null,null,data));
        var again=flow.run("ti",new Workflow.Command(key,"CATALOG_IMPORT",null,null,data));
        assertEquals(first,again);
        assertEquals(1,db.count("SELECT COUNT(*) FROM stat_catalog_version"));
        assertEquals(409,assertThrows(Problem.class,()->flow.run("ti",new Workflow.Command(key,"CATALOG_IMPORT",null,null,Map.of("content",file("RELACION","OTRA"),"reason",REASON)))).status);
        assertEquals(1,db.count("SELECT COUNT(*) FROM stat_catalog_version"));
    }
}
