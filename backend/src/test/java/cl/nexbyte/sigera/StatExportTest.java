package cl.nexbyte.sigera;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static cl.nexbyte.sigera.Db.*;
import static cl.nexbyte.sigera.StatClassificationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_e11_export;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.seed-demo=true","siger.data=./target/test-export-${random.uuid}","siger.scheduling=false"})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatExportTest extends StatFixture {
    static final String EXPORT="e11.export", PEOPLE="e11.people", MANAGER="e11.manager";
    static final String HEAD="e11.cabeza", PRO="e11.pro", SITE="E11-S06";
    static final String SECRET="Relato ciudadano sintético 29458271";
    static final List<String> PRIVATE=List.of(SECRET,"Nombreficticiozeta","1996-03-16","nombreficticio.e11@example.invalid","DOC-SINT-913524","12345678-5","+56987654321");
    @Autowired StatExport exports;
    @Autowired StatCuts cuts;
    long a,b,c,d,e,cut;

    static Map<String,Object> cp(){return period("CUENTA_PUBLICA","2025-07-01");}
    static Map<String,Object> body(String measure,String... dims){return map("measure",measure,"rows",List.of(dims),"period",cp());}
    String comFolio(long id){return s(db.one("SELECT folio FROM communication WHERE id=?",id),"folio");}

    // Datos ciudadanos sinteticos de la COM A (cierre de E11, barrido): quien presenta y un NNA, con RUN, nacimiento y contacto.
    // A se recibe el 2025-07-08: el requirente tiene 29 anios y el NNA 11.
    static final String BIRTH_ADULT="1996-03-16",BIRTH_NNA="2013-09-21";
    static Map<String,Object> citizenIntake(){
        var presenter=new LinkedHashMap<String,Object>();
        presenter.put("first_name","Nombreficticiozeta");presenter.put("last_name","Apellidoficticioomega");
        presenter.put("identity_type","RUN");presenter.put("run_number","12345678");presenter.put("run_dv","5");presenter.put("birth_date",BIRTH_ADULT);
        presenter.put("email","nombreficticio.e11@example.invalid");presenter.put("mobile","+56987654321");
        presenter.put("address","Calle Ficticia 4821");presenter.put("other_contact","Contacto ficticio 7731");presenter.put("preferred_name","Apodoficticiokappa");
        var nna=new LinkedHashMap<String,Object>();
        nna.put("role","NNA");nna.put("first_name","Ninoficticioalfa");nna.put("last_name","Apellidoficticioomega");
        nna.put("identity_type","RUN");nna.put("run_number","11111111");nna.put("run_dv","1");nna.put("birth_date",BIRTH_NNA);
        return Map.of("presenter",presenter,"persons",List.of(nna));
    }
    /** Textos ciudadanos de la poblacion (sin distinguir mayusculas ni tildes): ninguno puede aparecer en un archivo exportado. */
    static final List<String> CITIZEN_TEXT=List.of(SECRET,"Nombreficticiozeta","Apellidoficticioomega","Ninoficticioalfa","Apodoficticiokappa",
        "nombreficticio.e11@example.invalid","Calle Ficticia 4821","Contacto ficticio 7731","DOC-SINT-913524","+56987654321",
        "12.345.678-5","12345678-5","11.111.111-1","11111111-1",
        "1996-03-16","16-03-1996","16/03/1996","1996/03/16","2013-09-21","21-09-2013","21/09/2013","2013/09/21");
    /** Numeros que no pueden aparecer como valor aislado: RUN sin DV o sin guion, celular sin prefijo y fechas compactas. */
    static final List<String> CITIZEN_NUMBERS=List.of("12345678","123456785","11111111","111111111","987654321","19960316","20130921");
    /** Seriales de Excel de ambas fechas de nacimiento (1996-03-16 = 35140; 2013-09-21 = 41538), por si viajaran como fecha. */
    static final Set<String> BIRTH_SERIALS=Set.of("35140","41538");
    static final String FORBIDDEN_HEADER="(?i).*(run|rut|documento|nacimiento|birth|correo|mail|celular|mobile|telefono|phone|direcci|address|domicilio"
        +"|nombre|name|apellido|relato|body|contacto|contact|apodo).*";

    @BeforeEach void exportPopulation() throws Exception {
        if(cut!=0)return;
        catalogs(CATALOGS);
        site(SITE,"06","ACTIVA");
        staff(HEAD,"DIRECCION@"+SITE,"PROFESIONAL@"+SITE);staff(PRO,"PROFESIONAL@"+SITE);
        staff(EXPORT,"REPORTES","EXPORTACION");staff(PEOPLE,"REPORTES","EXPORTACION","REPORTES_PERSONAS");
        staff(MANAGER,"REPORTES","GESTION_ESTADISTICA");staff("e11.narrow","REPORTES","REPORTES@"+SITE,"EXPORTACION@"+SITE);
        staff("e11.mismatch","REPORTES","EXPORTACION@"+SITE);
        db.update("UPDATE staff SET must_change=FALSE");
        a=com(PRO,SITE,"CORREO","2025-07-08T15:00:00Z",citizenIntake(),stat("VULNERACION","PERSONA_NATURAL_ADULTA"));
        db.update("UPDATE communication SET body=?,identity_document=?,contact=? WHERE id=?",SECRET,"DOC-SINT-913524","12345678-5 +56987654321 nombreficticio.e11@example.invalid",a);
        db.update("UPDATE intake_record SET data_json=REPLACE(data_json,'Nombreficticiozeta','Nombreficticiozeta 1996-03-16') WHERE communication_id=?",a);
        route(HEAD,a,SITE,"UPD");long req=assume(HEAD,a,PRO,null);
        long cas1=on(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",scope(SITE),"responsible_id",user(PRO),"objective","Seguimiento","matter","UPD")).id();
        long cas2=on(HEAD,"REQ_CASE_CREATE","requirement",req,Map.of("scope_id",scope(SITE),"responsible_id",user(PRO),"objective","Segundo seguimiento","matter","UPD")).id();
        rights(HEAD,"REQ",req,"DERECHO",add("3.1"));rights(PRO,"CAS",cas1,"DERECHO",add("3.1"));rights(PRO,"CAS",cas2,"DERECHO",add("3.1"));
        rights(HEAD,"REQ",req,"GRUPO_PRIORITARIO",add(s(db.one("SELECT i.code FROM stat_catalog_item i JOIN stat_catalog_version v ON v.id=i.version_id WHERE v.catalog='GRUPO_PRIORITARIO' AND i.selectable=TRUE ORDER BY i.code LIMIT 1"),"code")));
        db.update("INSERT INTO management_note(requirement_id,author_id,body,reserved,effective_ms,registered_ms,action_type) VALUES(?,?,?,TRUE,?,?,?)",req,user(HEAD),SECRET,ms("2025-07-15T12:00:00Z"),System.currentTimeMillis(),"OFICIO");
        b=com(ADMIN,"UPR-REQ","TELEFONO","2025-08-08T15:00:00Z",form("13"),stat("CONSULTA_ORIENTACION","NNA"));route(DIST,b,"UPR-REQ",null);
        assume(StatClassificationTest.HEAD,b,PROF,null);
        c=com(PRO,SITE,"PRESENCIAL","2025-10-08T15:00:00Z",Map.of(),stat("PROMOCION","PERSONA_JURIDICA_ORGANISMO"));route(HEAD,c,SITE,"UPR");discard(HEAD,c,"DUPLICADA");
        d=com(PRO,SITE,"OFICIO","2026-01-08T15:00:00Z",Map.of(),null);route(HEAD,d,SITE,"UPR");discard(HEAD,d,"INVALIDA");
        e=com(PRO,SITE,"CORREO","2026-06-08T15:00:00Z",form("06"),stat("CONSULTA_ORIENTACION","PERSONA_NATURAL_ADULTA"));
        cut=cut(MANAGER,null,cp());
    }

    // Matriz del GO, cifras fijadas a mano. A y C se adscriben a E11-S06, B al centro, E queda pendiente.
    @Test void figuraUnoReproducible() throws Exception {
        var expected=Map.of(
            "TIPO_SOLICITUD",of("VULNERACION",1,"CONSULTA_ORIENTACION",2,"PROMOCION",1),
            "TIPO_REQUIRENTE",of("PERSONA_NATURAL_ADULTA",2,"NNA",1,"PERSONA_JURIDICA_ORGANISMO",1),
            "VIA_INGRESO",of("CORREO",2,"TELEFONO",1,"PRESENCIAL",1),
            "ADSCRIPCION_INICIAL",of(SITE,2,"OFICINA_NACIONAL",1,"PENDIENTE",1));
        for(var row:expected.entrySet()){
            var q=body("INGRESOS",row.getKey());
            var screen=engine.query(access.actor(EXPORT),q);
            assertEquals(row.getValue(),cells(screen));assertEquals(4,grand(screen));
            var files=unzip(download(EXPORT,"zip",q));
            assertEquals(row.getValue(),aggregate(csv(files.get("agregado.csv"))));
            assertEquals(row.getValue(),aggregate(XlsxReader.standard().read(files.get("agregado.xlsx"))));
            assertEquals(row.getValue(),aggregate(csv(download(EXPORT,"csv",q))));
            assertEquals(row.getValue(),aggregate(XlsxReader.standard().read(download(EXPORT,"xlsx",q))));
            var sums=new TreeMap<String,Long>();csv(files.get("microdatos.csv")).forEach(r->sums.merge(r.get(row.getKey()),1L,Long::sum));
            assertEquals(row.getValue(),sums);assertEquals(4,sums.values().stream().mapToLong(Long::longValue).sum());
            q.put("cut_id",cut);
            assertEquals(row.getValue(),cells(engine.query(access.actor(EXPORT),q)));
            assertEquals(row.getValue(),aggregate(csv(unzip(download(EXPORT,"zip",q)).get("agregado.csv"))));
        }
    }

    @Test void derechoConTresOrigenesYCuatroFolios() throws Exception {
        var files=unzip(download(EXPORT,"zip",body("REQUERIMIENTOS_CON_VALOR","DERECHO")));
        var micro=csv(files.get("microdatos.csv"));
        assertEquals(4,micro.size());
        assertEquals(Set.of(comFolio(a),comFolio(b),comFolio(c),comFolio(e)),micro.stream().map(r->r.get("folio")).collect(java.util.stream.Collectors.toSet()));
        assertEquals("PENDIENTE",micro.stream().filter(r->r.get("folio").equals(comFolio(b))).findFirst().orElseThrow().get("ESTADO_DERECHO"));
        var relation=csv(files.get("microdatos_multivalor.csv"));
        var rights=relation.stream().filter(r->r.get("variable").equals("DERECHO")&&r.get("codigo").equals("3.1")).toList();
        assertEquals(1,rights.size());assertEquals(comFolio(a),rights.getFirst().get("folio"));
        assertEquals(List.of("1","2","3"),List.of(rights.getFirst().get("en_req"),rights.getFirst().get("casos"),rights.getFirst().get("menciones")));
        var byFolio=new HashMap<String,Set<String>>();
        for(var row:relation)if(row.get("variable").equals("DERECHO"))byFolio.computeIfAbsent(row.get("folio"),ignored->new HashSet<>()).add(row.get("codigo"));
        var reaggregated=new TreeMap<String,Long>();
        for(var row:micro)for(String value:byFolio.getOrDefault(row.get("folio"),Set.of(row.get("ESTADO_DERECHO"))))reaggregated.merge(value,1L,Long::sum);
        assertEquals(of("3.1",1,"PENDIENTE",3),reaggregated);
        assertEquals(reaggregated,aggregate(csv(files.get("agregado.csv"))));
        var total=csv(files.get("agregado.csv")).stream().filter(r->r.get("tipo").equals("TOTAL_GENERAL")).findFirst().orElseThrow();
        assertEquals("4",total.get("unique"));assertEquals("1",total.get("mentions"));
        assertFalse(relation.stream().anyMatch(r->r.get("variable").equals("TIPO_ACTUACION")&&r.get("codigo").equals("OFICIO")));
        var acts=body("ACTUACIONES","TIPO_ACTUACION");assertTrue(aggregate(csv(download(EXPORT,"csv",acts))).isEmpty());
        assertEquals(400,assertThrows(Problem.class,()->download(EXPORT,"zip",acts)).status);
    }

    @Test void gruposPrioritariosYAlcance() throws Exception {
        var q=body("INGRESOS");
        var limited=unzip(download(EXPORT,"zip",q));var full=unzip(download(PEOPLE,"zip",q));
        assertFalse(csv(limited.get("microdatos.csv")).getFirst().keySet().stream().anyMatch(k->k.contains("GRUPO_PRIORITARIO")));
        assertFalse(csv(limited.get("microdatos_multivalor.csv")).stream().anyMatch(r->r.get("variable").equals("GRUPO_PRIORITARIO")));
        assertTrue(csv(full.get("microdatos_multivalor.csv")).stream().anyMatch(r->r.get("variable").equals("GRUPO_PRIORITARIO")));
        assertTrue(new String(limited.get("diccionario.csv"),StandardCharsets.UTF_8).contains("REPORTES_PERSONAS"));
        for(var dimension:List.of("GRUPO_PRIORITARIO","ESTADO_GRUPO_PRIORITARIO")){
            var cross=body("INGRESOS","ADSCRIPCION_INICIAL");cross.put("columns",List.of(dimension));
            assertEquals(403,assertThrows(Problem.class,()->download(EXPORT,"zip",cross)).status);
            assertTrue(download(EXPORT,"xlsx",cross).length>0);assertTrue(download(PEOPLE,"zip",cross).length>0);
        }
        assertEquals(403,assertThrows(Problem.class,()->download("e11.narrow","csv",q)).status);
        assertEquals(403,assertThrows(Problem.class,()->download("e11.narrow","zip",map("measure","INGRESOS","period",cp(),"compare",Map.of("type","CORTE","cut_id",cut)))).status);
        assertEquals(403,assertThrows(Problem.class,()->download(MANAGER,"csv",q)).status);
        assertEquals(403,assertThrows(Problem.class,()->download("ti","csv",q)).status);
        var filter=body("INGRESOS");filter.put("filters",Map.of("ESTADO_GRUPO_PRIORITARIO",List.of("INFORMADO")));
        assertEquals(403,assertThrows(Problem.class,()->download(EXPORT,"zip",filter)).status);
    }

    @Test void corteV23SeExportaAgregadoYDetectaCambiosEnSuComplemento() throws Exception {
        long old=cut(MANAGER,null,cp());
        db.update("DELETE FROM stat_export_cut_folio WHERE cut_id=?",old);
        db.update("DELETE FROM stat_export_cut_origin WHERE cut_id=?",old);
        db.update("DELETE FROM stat_export_cut_integrity WHERE cut_id=?",old);
        var header=db.one("SELECT * FROM stat_cut WHERE id=?",old);
        var counts=new LinkedHashMap<String,Object>();
        for(var table:StatCuts.TABLES)counts.put(table.name(),db.count("SELECT COUNT(*) FROM "+table.name()+" WHERE cut_id=?",old));
        header.put("counts_json",StatCuts.json(counts));
        db.update("UPDATE stat_cut SET counts_json=?,metadata_sha256=? WHERE id=?",header.get("counts_json"),StatCuts.metadata(header),old);
        var q=body("INGRESOS","CANAL");q.put("cut_id",old);
        assertTrue(download(EXPORT,"csv",q).length>0);assertTrue(download(EXPORT,"xlsx",q).length>0);
        var incompatible=assertThrows(Problem.class,()->download(EXPORT,"zip",q));
        assertEquals(409,incompatible.status);assertTrue(incompatible.getMessage().contains("antes de incorporar"));
        assertEquals(true,cuts.verify(access.actor(EXPORT),old).get("ok"));
        long modified=cut(MANAGER,null,cp());
        assertEquals(true,cuts.verify(access.actor(EXPORT),modified).get("export_ok"));
        db.update("UPDATE stat_export_cut_folio SET folio='ALTERADO' WHERE cut_id=? AND communication_id=?",modified,a);
        var verification=cuts.verify(access.actor(EXPORT),modified);
        assertEquals(false,verification.get("ok"));assertEquals(false,verification.get("export_ok"));assertEquals(true,verification.get("rows_ok"));
    }

    @Test void huellasExactasYContenidoSinDatosCiudadanos() throws Exception {
        var q=body("INGRESOS","CANAL");
        var first=unzip(download(EXPORT,"zip",q));var second=unzip(download(EXPORT,"zip",q));
        String manifest=new String(first.get("SHA256SUMS.txt"),StandardCharsets.UTF_8);
        for(var entry:first.entrySet()){
            var name=entry.getKey();var bytes=entry.getValue();
            if(!name.equals("SHA256SUMS.txt"))assertTrue(manifest.contains(sha(bytes)+"  "+name));
            if(!Set.of("SHA256SUMS.txt","metadatos.json").contains(name))assertArrayEquals(bytes,second.get(name),name);
            var parts=name.endsWith(".xlsx")?unzip(bytes).values():List.of(bytes);
            for(var part:parts){var decoded=new String(part,StandardCharsets.UTF_8);for(String token:PRIVATE)assertFalse(decoded.contains(token),name);}
        }
        var altered=first.get("agregado.csv").clone();altered[altered.length-1]^=1;
        assertFalse(manifest.contains(sha(altered)+"  agregado.csv"));
        assertTrue(csv(first.get("microdatos.csv")).getFirst().keySet().stream().noneMatch(k->k.matches("(?i).*(RUN|documento|nacimiento|correo|relato|nombre|celular).*")));
        var dictionary=csv(first.get("diccionario.csv"));
        assertTrue(dictionary.stream().map(r->r.get("definicion")).anyMatch(x->x.contains("constancias reservadas")));
        assertTrue(dictionary.stream().map(r->r.get("definicion")).anyMatch(x->x.contains("total único")));
        assertTrue(dictionary.stream().map(r->r.get("definicion")).anyMatch(x->x.contains("riesgo residual de reidentificación")));
        assertTrue(dictionary.stream().anyMatch(r->r.get("tipo").equals("CATALOGO")&&r.get("dimension").equals("TIPO_ACTUACION")&&!r.get("definicion").isBlank()));
    }

    @Test void primerCorteYComparacionExigenMateriaYCupo(){
        staff("e11.materia", "REPORTES", "REPORTES@UPD", "EXPORTACION@UPD");
        staff("e11.sede", "REPORTES", "REPORTES@"+SITE, "EXPORTACION@"+SITE);
        staff("e11.gest.sede", "REPORTES@"+SITE, "GESTION_ESTADISTICA@"+SITE);
        staff("e11.gest.upd", "REPORTES@UPD", "GESTION_ESTADISTICA@UPD");
        long site=cut("e11.gest.sede",SITE,cp()),matter=cut("e11.gest.upd","UPD",cp());
        var q=body("INGRESOS","CANAL");q.put("cut_id",site);
        assertEquals(403,assertThrows(Problem.class,()->download("e11.materia","csv",q)).status);
        assertEquals(403,assertThrows(Problem.class,()->download("e11.mismatch","csv",q)).status);
        assertTrue(download("e11.sede","csv",q).length>0);
        q.put("compare",Map.of("type","CORTE","cut_id",matter));
        assertEquals(403,assertThrows(Problem.class,()->download("e11.sede","csv",q)).status);
        assertTrue(download(EXPORT,"csv",q).length>0);
        var actor=access.actor(EXPORT);long audits=db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_EXPORTACION'");
        guard.run(actor.id(),()->{assertEquals(429,assertThrows(Problem.class,()->exports.export(actor,"zip",q)).status);return null;});
        assertEquals(audits,db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_EXPORTACION'"));
        assertTrue(guard.active().isEmpty());
        var confined=guard.disabledBy();
        try{
            guard.confined(List.of("tmpdir"));
            assertEquals(503,assertThrows(Problem.class,()->download(EXPORT,"csv",q)).status);
        }finally{guard.confined(confined);}
    }

    @Test void complementoConListaCerradaYProvenienciaQueSeVerifica(){
        var found=new TreeMap<String,Set<String>>();
        for(var r:db.rows("SELECT LOWER(table_name) AS t,LOWER(column_name) AS c FROM information_schema.columns WHERE LOWER(table_schema)=LOWER(SCHEMA()) AND LOWER(table_name) LIKE 'stat_export_cut_%'"))
            found.computeIfAbsent(s(r,"t"),ignored->new HashSet<>()).add(s(r,"c"));
        assertEquals(Map.of("stat_export_cut_folio",Set.of("cut_id","communication_id","folio"),
            "stat_export_cut_origin",Set.of("cut_id","source_kind","value_id","record_type","record_id"),"stat_export_cut_integrity",Set.of("cut_id","export_sha256")),found);
        long id=cut(MANAGER,null,cp());
        var one=db.one("SELECT value_id FROM stat_export_cut_origin WHERE cut_id=? AND source_kind='EVENTO' ORDER BY value_id LIMIT 1",id);
        db.update("UPDATE stat_export_cut_origin SET record_id=record_id+10000 WHERE cut_id=? AND source_kind='EVENTO' AND value_id=?",id,n(one,"value_id"));
        assertEquals(false,cuts.verify(access.actor(EXPORT),id).get("export_ok"));
    }

    @Test void exportacionHistoricaConLaMismaReglaYAuditoriaSinValores(){
        var q=body("INGRESOS","CANAL");q.put("filters",Map.of("CANAL",List.of("TELEFONO")));
        q.put("time_mode","FECHA");q.put("as_of",java.time.Instant.now().toString());
        assertEquals(of("TELEFONO",1),aggregate(csv(download(EXPORT,"csv",q))));
        var note=s(db.one("SELECT note FROM audit_event WHERE action='STAT_EXPORTACION' ORDER BY id DESC LIMIT 1"),"note");
        assertTrue(note.contains("filtros CANAL"));assertFalse(note.contains("TELEFONO")||note.contains("2025")||note.contains("2026"));
    }

    // Matriz (h): un archivo alterado dentro del ZIP (el paquete reescrito con ese archivo cambiado) no coincide con
    // SHA256SUMS.txt y los demas siguen coincidiendo. Paquete de 9 archivos; el manifiesto declara los 8 restantes.
    @Test void archivoAlteradoDentroDelZipNoCoincideConSha256Sums() throws Exception {
        var original=unzip(download(EXPORT,"zip",body("INGRESOS","CANAL")));
        assertEquals(List.of("LEEME.txt","metadatos.json","agregado.csv","agregado.xlsx","microdatos.csv","microdatos_multivalor.csv",
            "diccionario.csv","catalogos.csv","SHA256SUMS.txt"),List.copyOf(original.keySet()));
        assertEquals(8,manifest(original).size());
        assertEquals(Set.of(),mismatches(original));
        int altered=0;
        for(String name:original.keySet()){
            if(name.equals("SHA256SUMS.txt"))continue;
            var copy=new LinkedHashMap<>(original);var bytes=copy.get(name).clone();bytes[bytes.length/2]^=1;copy.put(name,bytes);
            var reread=unzip(Xlsx.zip(copy));
            assertArrayEquals(bytes,reread.get(name),name);
            assertEquals(Set.of(name),mismatches(reread),name);
            altered++;
        }
        assertEquals(8,altered);
        var extra=new LinkedHashMap<>(original);extra.put("agregado2.csv",original.get("agregado.csv"));
        assertEquals(Set.of("agregado2.csv"),mismatches(unzip(Xlsx.zip(extra))));
        var missing=new LinkedHashMap<>(original);missing.remove("microdatos.csv");
        assertEquals(Set.of("microdatos.csv"),mismatches(unzip(Xlsx.zip(missing))));
    }

    // Barrido del cierre de E11: ningun archivo del ZIP, ninguna parte ni hoja de un XLSX (encabezados y contenido) y ningun
    // CSV lleva nombres, RUN, documento, contacto, domicilio, relato ni fecha de nacimiento; la edad si viaja (NNA de A: 11).
    // 2 cuentas (sin y con REPORTES_PERSONAS) x 5 consultas x (ZIP: 8 archivos + 9 partes de agregado.xlsx; CSV: 1; XLSX: 5) = 230 partes.
    @Test void barridoDeDatosCiudadanosEnTodosLosArchivosYHojas() throws Exception {
        var queries=new ArrayList<Map<String,Object>>();
        queries.add(body("INGRESOS","CANAL"));
        var rights=body("REQUERIMIENTOS_CON_VALOR","DERECHO");rights.put("columns",List.of("CONTEXTO"));queries.add(rights);
        queries.add(body("INGRESOS","TRAMO_EDAD_NNA"));
        queries.add(body("INGRESOS","TRAMO_EDAD_REQUIRENTE"));
        var frozen=body("INGRESOS","CANAL");frozen.put("cut_id",cut);queries.add(frozen);
        int swept=0;
        for(String who:List.of(EXPORT,PEOPLE))for(var q:queries)for(String format:List.of("zip","csv","xlsx"))
            for(var part:parts(format,download(who,format,q)).entrySet()){sweep(part.getKey(),part.getValue());swept++;}
        assertEquals(230,swept);
        var acts=body("ACTUACIONES","TIPO_ACTUACION");
        for(String format:List.of("csv","xlsx"))for(var part:parts(format,download(PEOPLE,format,acts)).entrySet())sweep(part.getKey(),part.getValue());
        var relation=csv(unzip(download(PEOPLE,"zip",body("INGRESOS","TRAMO_EDAD_NNA"))).get("microdatos_multivalor.csv"));
        assertEquals(List.of("11"),relation.stream().filter(r->r.get("folio").equals(comFolio(a))&&r.get("variable").equals("EDAD_NNA")).map(r->r.get("codigo")).toList());
    }

    // Detencion de E11, decision 1: un ZIP a la vez en la instancia, con un cupo propio ademas del de StatGuard. Con el cupo
    // tomado, un ZIP de cualquier cuenta recibe 429 de inmediato y sin auditoria; CSV, XLSX y la consulta siguen (CANAL:
    // CORREO 2, TELEFONO 1, PRESENCIAL 1; total 4). Un ZIP rechazado libera el cupo.
    @Test void unZipALaVezEnLaInstancia() throws Exception {
        var field=StatExport.class.getDeclaredField("zipSlot");field.setAccessible(true);
        var slot=(java.util.concurrent.Semaphore)field.get(exports);
        var q=body("INGRESOS","CANAL");
        long audits=db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_EXPORTACION'");
        assertTrue(slot.tryAcquire());
        try{
            for(String who:List.of(EXPORT,PEOPLE)){
                var busy=assertThrows(Problem.class,()->download(who,"zip",q));
                assertEquals(429,busy.status);assertEquals(StatExport.ZIP_BUSY,busy.getMessage());
            }
            assertEquals(audits,db.count("SELECT COUNT(*) FROM audit_event WHERE action='STAT_EXPORTACION'"));
            assertEquals(of("CORREO",2,"PRESENCIAL",1,"TELEFONO",1),aggregate(csv(download(EXPORT,"csv",q))));
            assertEquals(of("CORREO",2,"PRESENCIAL",1,"TELEFONO",1),aggregate(XlsxReader.standard().read(download(PEOPLE,"xlsx",q))));
            assertEquals(4,grand(engine.query(access.actor(EXPORT),q)));
            assertTrue(guard.active().isEmpty());
        }finally{slot.release();}
        assertEquals(1,slot.availablePermits());
        assertEquals(400,assertThrows(Problem.class,()->download(EXPORT,"zip",body("ACTUACIONES","TIPO_ACTUACION"))).status);
        assertEquals(1,slot.availablePermits());
        assertEquals(4,csv(unzip(download(EXPORT,"zip",q)).get("microdatos.csv")).size());
        assertEquals(1,slot.availablePermits());
    }

    @Autowired StatSeries seriesService;
    @org.springframework.beans.factory.annotation.Value("${siger.stats.export.max-rows}") int maxRows;

    // Decision 3: siger.stats.export.max-rows = 30.000 (SIGER_STATS_EXPORT_MAX_ROWS). Con la poblacion de 4 folios, un
    // limite de 4 genera el paquete y uno de 3 responde 409 sin archivo; CSV y XLSX agregados no tienen ese limite.
    @Test void limiteDeFoliosDelPaquete() throws Exception {
        assertEquals(30_000,maxRows);
        var properties=new String(getClass().getResourceAsStream("/application.properties").readAllBytes(),StandardCharsets.UTF_8);
        assertTrue(properties.contains("siger.stats.export.max-rows=${SIGER_STATS_EXPORT_MAX_ROWS:30000}"));
        var q=body("INGRESOS","CANAL");
        var four=new StatExport(db,reports,guard,engine,cuts,seriesService,4,500_000);
        assertEquals(4,csv(unzip(four.export(access.actor(EXPORT),"zip",q).bytes()).get("microdatos.csv")).size());
        var three=new StatExport(db,reports,guard,engine,cuts,seriesService,3,500_000);
        var over=assertThrows(Problem.class,()->three.export(access.actor(EXPORT),"zip",q));
        assertEquals(409,over.status);assertEquals("El paquete supera el máximo de 3 folios. Acota el periodo o los filtros.",over.getMessage());
        assertEquals(of("CORREO",2,"PRESENCIAL",1,"TELEFONO",1),aggregate(csv(three.export(access.actor(EXPORT),"csv",q).bytes())));
        assertTrue(guard.active().isEmpty());
    }

    // Decision 2: sobre un corte, las proyecciones de microdatos fijan el indice por COM de cada tabla congelada (con las
    // estadisticas de InnoDB aun sin recalcular, el optimizador recorria todo el corte por COM). En MySQL, EXPLAIN muestra
    // ese indice; la fuente vigente no lleva sugerencias de indice.
    @Test void proyeccionesSobreElCorteFijanElIndicePorCom(){
        var span=StatEngine.span(java.time.LocalDate.parse("2025-07-01"),java.time.LocalDate.parse("2026-06-30"));
        var all=new Reports.Reach(true,Set.of(),Set.of());
        var frozen=new StatSource.Query(StatSource.MEASURES.get("INGRESOS"),List.of(),List.of(),Map.of(),span,StatSource.Basis.RECEPCION,StatSource.Mode.VIGENTE,null,null,false,all,Map.of(),cut);
        var live=new StatSource.Query(StatSource.MEASURES.get("INGRESOS"),List.of(),List.of(),Map.of(),span,StatSource.Basis.RECEPCION,StatSource.Mode.VIGENTE,null,null,false,all,Map.of(),null);
        var classifications=new StatSource.Plan(frozen).exportClassifications(true);var actions=new StatSource.Plan(frozen).exportActions();
        assertTrue(classifications.text().contains("INDEX(e ix_stat_cut_event_com)"));
        assertTrue(actions.text().contains("INDEX(a ix_stat_cut_action_com)"));
        assertTrue(actions.text().contains("INDEX(ni ix_stat_cut_institution_note)"));
        for(var sql:List.of(new StatSource.Plan(live).exportClassifications(true),new StatSource.Plan(live).exportActions()))assertFalse(sql.text().contains("INDEX("));
        if(!MySqlSuite.enabled())return;
        var expected=Map.of("e","ix_stat_cut_event_com","a","ix_stat_cut_action_com","ni","ix_stat_cut_institution_note");
        var seen=new TreeMap<String,Set<String>>();
        for(var sql:List.of(classifications,actions))
            for(var r:db.rows("EXPLAIN "+sql.text(),sql.args()))if(expected.containsKey(s(r,"table")))seen.computeIfAbsent(s(r,"table"),k->new TreeSet<>()).add(s(r,"key"));
        assertEquals(Map.of("e",Set.of("ix_stat_cut_event_com"),"a",Set.of("ix_stat_cut_action_com"),"ni",Set.of("ix_stat_cut_institution_note")),seen);
    }

    /** Partes legibles de una descarga: cada archivo del ZIP y cada parte XML de un XLSX (tambien del que va dentro del ZIP). */
    static Map<String,byte[]> parts(String format,byte[] bytes) throws Exception {
        var out=new LinkedHashMap<String,byte[]>();
        Map<String,byte[]> top=format.equals("zip")?unzip(bytes):Map.of("descarga."+format,bytes);
        for(var e:top.entrySet()){
            if(e.getKey().endsWith(".xlsx"))for(var p:unzip(e.getValue()).entrySet())out.put(e.getKey()+"!"+p.getKey(),p.getValue());
            else out.put(e.getKey(),e.getValue());
        }
        return out;
    }
    static void sweep(String name,byte[] bytes) throws Exception {
        String text=new String(bytes,StandardCharsets.UTF_8),plain=fold(text);
        for(String token:CITIZEN_TEXT)assertFalse(plain.contains(fold(token)),name+": "+token);
        for(String number:CITIZEN_NUMBERS)
            assertFalse(java.util.regex.Pattern.compile("(?<![0-9A-Za-z])"+java.util.regex.Pattern.quote(number)+"(?![0-9A-Za-z])").matcher(text).find(),name+": "+number);
        List<List<String>> grid=name.endsWith(".csv")?StatSeries.csv(bytes,1_000_000):name.matches(".*!xl/worksheets/sheet\\d+\\.xml")?sheet(text):null;
        if(grid==null)return;
        assertFalse(grid.isEmpty(),name);
        for(String header:grid.getFirst())assertFalse(header.matches(FORBIDDEN_HEADER),name+": encabezado "+header);
        for(var row:grid)for(String cell:row)assertFalse(BIRTH_SERIALS.contains(cell),name+": serial de fecha "+cell);
    }
    static String fold(String s){
        return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
    }
    /** Filas de una hoja escrita por Xlsx: valores numericos y textos en linea, en orden. */
    static List<List<String>> sheet(String xml) throws Exception {
        var rows=new ArrayList<List<String>>();List<String> row=null;var text=new StringBuilder();boolean value=false;
        var r=javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(new java.io.StringReader(xml));
        while(r.hasNext()){
            int event=r.next();
            if(event==javax.xml.stream.XMLStreamConstants.START_ELEMENT){
                String tag=r.getLocalName();
                if(tag.equals("row"))row=new ArrayList<>();
                else if(tag.equals("v")||tag.equals("t")){value=true;text.setLength(0);}
            }else if(event==javax.xml.stream.XMLStreamConstants.CHARACTERS&&value)text.append(r.getText());
            else if(event==javax.xml.stream.XMLStreamConstants.END_ELEMENT){
                String tag=r.getLocalName();
                if(tag.equals("v")||tag.equals("t")){row.add(text.toString());value=false;}
                else if(tag.equals("row"))rows.add(row);
            }
        }
        return rows;
    }
    static Map<String,String> manifest(Map<String,byte[]> files){
        var out=new LinkedHashMap<String,String>();
        for(String line:new String(files.get("SHA256SUMS.txt"),StandardCharsets.UTF_8).split("\n")){
            if(line.isEmpty())continue;
            var m=java.util.regex.Pattern.compile("([0-9a-f]{64})  (.+)").matcher(line);
            assertTrue(m.matches(),line);assertNull(out.put(m.group(2),m.group(1)),line);
        }
        return out;
    }
    /** Archivos que no coinciden con SHA256SUMS.txt: huella distinta, no declarado o declarado y ausente. */
    static Set<String> mismatches(Map<String,byte[]> files) throws Exception {
        var sums=manifest(files);var out=new TreeSet<String>();
        for(var e:files.entrySet())if(!e.getKey().equals("SHA256SUMS.txt")&&!sha(e.getValue()).equals(sums.get(e.getKey())))out.add(e.getKey());
        for(String listed:sums.keySet())if(!files.containsKey(listed))out.add(listed);
        return out;
    }

    byte[] download(String who,String format,Map<String,Object> q){return exports.export(access.actor(who),format,q).bytes();}
    static Map<String,byte[]> unzip(byte[] bytes) throws Exception {
        var files=new LinkedHashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())files.put(entry.getName(),zip.readAllBytes());
        }
        return files;
    }
    static String sha(byte[] b) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static List<Map<String,String>> csv(byte[] bytes){return tabulate(StatSeries.csv(bytes,1_000_000));}
    static List<Map<String,String>> tabulate(List<List<String>> grid){
        var header=grid.getFirst();var rows=new ArrayList<Map<String,String>>();
        for(var line:grid.subList(1,grid.size())){
            var row=new LinkedHashMap<String,String>();for(int i=0;i<header.size();i++)row.put(header.get(i),i<line.size()?line.get(i):"");rows.add(row);
        }
        return rows;
    }
    static Map<String,Long> aggregate(List<List<String>> grid){return aggregate(tabulate(grid));}
    static Map<String,Long> aggregate(Collection<Map<String,String>> rows){
        var values=new TreeMap<String,Long>();
        for(var row:rows)if("CELDA".equals(row.get("tipo")))values.put(row.get("fila_1"),Long.parseLong(row.get("n")));
        return values;
    }
}
