package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Liberacion de originales reales: solo con un analisis antivirus LIMPIO, reciente y de los mismos bytes, hecho por
 * el servidor; nunca tras una deteccion, un error, un motor no operativo o un archivo alterado. El motor es un
 * ejecutor FALSO programable (sin escanear con un producto real), salvo la comprobacion de solo lectura de Defender.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_release;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.data=./target/release-${random.uuid}"})
@ActiveProfiles("test")
@Import(DocumentReleaseTest.FakeEngine.class)
class DocumentReleaseTest {
    static final Path ENGINE=Path.of("target","av-falso-"+UUID.randomUUID());
    /** Respuestas del motor falso: version de firmas y codigo de salida del analisis. */
    static final AtomicReference<String> VERSION=new AtomicReference<>();
    static final AtomicReference<Integer> EXIT=new AtomicReference<>(0);
    /** Salida del analisis; %s es la ruta analizada (como la imprime clamscan). */
    static final AtomicReference<String> OUTPUT=new AtomicReference<>("");
    /** Accion durante el analisis (p. ej. dejar archivos en --tempdir o fallar), para probar la limpieza. */
    static final AtomicReference<java.util.function.Consumer<List<String>>> DURING=new AtomicReference<>(c->{});
    static final List<List<String>> COMMANDS=Collections.synchronizedList(new ArrayList<>());
    static String freshVersion(){return "ClamAV 1.4.1/27450/"+LocalDateTime.now().minusHours(2).format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy",Locale.ENGLISH));}
    static MockEnvironment clamav()throws Exception{
        Files.createDirectories(ENGINE.resolve("db"));if(!Files.exists(ENGINE.resolve("clamscan.exe")))Files.writeString(ENGINE.resolve("clamscan.exe"),"");
        return new MockEnvironment().withProperty("siger.documents.av.engine","CLAMAV").withProperty("siger.documents.av.executable",ENGINE.resolve("clamscan.exe").toString())
            .withProperty("siger.documents.av.database",ENGINE.resolve("db").toString());
    }
    static DocumentScanner.Output fake(List<String> command,long timeout){
        COMMANDS.add(command);
        if(command.contains("--version"))return new DocumentScanner.Output(0,VERSION.get());
        DURING.get().accept(command);
        return new DocumentScanner.Output(EXIT.get(),OUTPUT.get().replace("%s",command.getLast()));
    }
    @TestConfiguration static class FakeEngine {
        @Bean @Primary DocumentScanner fakeScanner()throws Exception{return new DocumentScanner(clamav(),DocumentReleaseTest::fake,Clock.systemUTC());}
    }

    @Autowired Db db;@Autowired Documents documents;@Autowired Workflow flow;
    static long scope,ti,other;

    @BeforeEach void setup(){
        VERSION.set(freshVersion());EXIT.set(0);OUTPUT.set("");DURING.set(c->{});COMMANDS.clear();
        if(scope==0){
            db.update("INSERT INTO org_unit VALUES('DOC','Unidad documental')");
            scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('DOC-1','Documentos','DOC','A','CENTRAL')");
            ti=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES('ti.doc','TI','x',?,FALSE)",scope);
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,0,'PRUEBA AUTOMATICA')",ti);
            other=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES('otra.doc','Otra','x',?,FALSE)",scope);
        }
    }
    /** Original en cuarentena ligado a una COM nueva, con su archivo custodiado. */
    long original(String content)throws Exception{
        long com=db.insert("INSERT INTO communication(folio,channel,received_ms,subject,body,origin_scope,created_by,created_ms,state) VALUES(?,?,?,?,?,?,?,?,'NUEVA')",
            "DOC-"+UUID.randomUUID().toString().substring(0,8),"PRESENCIAL",1L,"Asunto","Cuerpo",scope,ti,1L);
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);String key=UUID.randomUUID()+".bin";
        Files.createDirectories(documents.directory);Files.write(documents.directory.resolve(key),bytes);
        return db.insert("INSERT INTO document_item(communication_id,name,storage_key,sha256,size_bytes,content_type,uploaded_by,created_ms,document_type) VALUES(?,?,?,?,?,'application/octet-stream',?,?,'INGRESO')",
            com,"informe.pdf",key,ExternalOriginal.sha256(bytes),bytes.length,ti,1L);
    }
    Workflow.Result release(long id,Map<String,Object> data){return flow.run("ti.doc",new Workflow.Command(UUID.randomUUID(),"DOC_RELEASE",id,null,data));}
    boolean quarantined(long id){return b(db.one("SELECT quarantined FROM document_item WHERE id=?",id),"quarantined");}

    @Test void soloUnAnalisisLimpioRecienteDeLosMismosBytesLibera()throws Exception{
        long id=original("contenido real de prueba");
        // Sin analisis, ni siquiera declarando revision manual (la instancia no es de pruebas).
        assertEquals(409,assertThrows(Problem.class,()->release(id,Map.of("synthetic_review",true,"reason","x"))).status);
        assertEquals(403,assertThrows(Problem.class,()->documents.scan("otra.doc",id)).status);
        var r=documents.scan("ti.doc",id);
        assertEquals("LIMPIO",r.get("verdict"));
        var command=COMMANDS.getLast();
        assertTrue(command.contains("--official-db-only=yes")&&command.contains("--alert-exceeds-max=yes")&&command.getLast().endsWith(".bin"),command.toString());
        assertTrue(quarantined(id)); // analizar no libera
        release(id,Map.of("reason","Analisis limpio verificado por TI"));
        assertFalse(quarantined(id));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='LIBERACION_TRAS_ANALISIS' AND resource_id=?",id));
    }

    @Test void deteccionErrorFirmasViejasOAlteracionNuncaLiberan()throws Exception{
        long infected=original("archivo con deteccion");
        EXIT.set(1);OUTPUT.set("%s: Win.Test.EICAR_HDB-1 FOUND");
        var found=documents.scan("ti.doc",infected);
        assertEquals("INFECTADO",found.get("verdict"));assertEquals("FIRMA:Win.Test.EICAR_HDB-1",found.get("detail"));
        OUTPUT.set("");
        EXIT.set(0);assertEquals("LIMPIO",documents.scan("ti.doc",infected).get("verdict"));
        assertEquals(409,assertThrows(Problem.class,()->release(infected,Map.of("reason","x"))).status); // una deteccion es permanente
        assertTrue(quarantined(infected));

        long error=original("analisis incompleto");
        EXIT.set(2);var r=documents.scan("ti.doc",error);assertEquals("ERROR",r.get("verdict"));assertEquals("ANALISIS_INCOMPLETO",r.get("detail"));
        assertThrows(Problem.class,()->release(error,Map.of("reason","x")));

        long stale=original("firmas antiguas");
        EXIT.set(0);VERSION.set("ClamAV 1.4.1/27000/"+LocalDateTime.now().minusDays(10).format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy",Locale.ENGLISH)));
        COMMANDS.clear();
        r=documents.scan("ti.doc",stale);assertEquals("ERROR",r.get("verdict"));assertEquals("MOTOR_NO_OPERATIVO_O_FIRMAS_DESACTUALIZADAS",r.get("detail"));
        assertEquals(1,COMMANDS.size()); // con firmas viejas no se llega a analizar
        assertThrows(Problem.class,()->release(stale,Map.of("reason","x")));

        VERSION.set(freshVersion());
        long altered=original("bytes originales");
        assertEquals("LIMPIO",documents.scan("ti.doc",altered).get("verdict"));
        Files.writeString(documents.directory.resolve(s(db.one("SELECT storage_key FROM document_item WHERE id=?",altered),"storage_key")),"bytes cambiados");
        assertEquals(409,assertThrows(Problem.class,()->release(altered,Map.of("reason","x"))).status);
        assertEquals("ERROR",documents.scan("ti.doc",altered).get("verdict")); // integridad previa fallida

        long old=original("analisis vencido");
        assertEquals("LIMPIO",documents.scan("ti.doc",old).get("verdict"));
        db.update("UPDATE document_scan SET finished_ms=? WHERE document_id=?",System.currentTimeMillis()-7_200_000L,old);
        assertThrows(Problem.class,()->release(old,Map.of("reason","x")));
        for(long id:List.of(infected,error,stale,altered,old))assertTrue(quarantined(id));
        assertEquals(1,db.count("SELECT COUNT(*) FROM audit_event WHERE action='DOCUMENTO_INFECTADO_DETECTADO' AND resource_id=?",infected));
    }

    @Test void limitesExcedidosOSalidaIncompletaNoSonDeteccionPermanente()throws Exception{
        // ClamAV sale con 1 tambien cuando --alert-exceeds-max marca un limite: queda en cuarentena, sin deteccion.
        long limits=original("archivo que excede limites");
        EXIT.set(1);OUTPUT.set("%s: Heuristics.Limits.Exceeded.MaxFileSize FOUND");
        var r=documents.scan("ti.doc",limits);
        assertEquals("ERROR",r.get("verdict"));assertEquals("LIMITES_EXCEDIDOS",r.get("detail"));
        assertThrows(Problem.class,()->release(limits,Map.of("reason","x")));
        assertTrue(quarantined(limits));
        // Salida 1 sin resultado legible, o con una firma informada para otro archivo: analisis incompleto.
        long unreadable=original("salida sin resultado");
        OUTPUT.set("LibClamAV Error: cli_scanfile: error inesperado");
        r=documents.scan("ti.doc",unreadable);assertEquals("ERROR",r.get("verdict"));assertEquals("ANALISIS_INCOMPLETO",r.get("detail"));
        long other=original("firma de otro archivo");
        OUTPUT.set("C:\\otro\\archivo.bin: Win.Test.EICAR_HDB-1 FOUND");
        r=documents.scan("ti.doc",other);assertEquals("ERROR",r.get("verdict"));assertEquals("ANALISIS_INCOMPLETO",r.get("detail"));
        // Ninguno queda marcado como infectado: un analisis limpio posterior permite liberar.
        assertEquals(0,db.count("SELECT COUNT(*) FROM document_scan WHERE document_id IN (?,?,?) AND verdict='INFECTADO'",limits,unreadable,other));
        assertEquals(0,db.count("SELECT COUNT(*) FROM audit_event WHERE action='DOCUMENTO_INFECTADO_DETECTADO' AND resource_id IN (?,?,?)",limits,unreadable,other));
        EXIT.set(0);OUTPUT.set("");
        for(long id:List.of(limits,unreadable,other)){
            assertEquals("LIMPIO",documents.scan("ti.doc",id).get("verdict"));
            release(id,Map.of("reason","Analisis limpio posterior"));
            assertFalse(quarantined(id));
        }
        // Defender: MpCmdRun devuelve 2 tanto ante amenaza como ante fallo del motor (hr=0x800106ba con el servicio
        // inactivo, observado en este equipo): solo el texto de deteccion acredita INFECTADO.
        Files.createDirectories(ENGINE.resolve("defender"));if(!Files.exists(ENGINE.resolve("defender").resolve("MpCmdRun.exe")))Files.writeString(ENGINE.resolve("defender").resolve("MpCmdRun.exe"),"");
        var defender=new DocumentScanner(new MockEnvironment().withProperty("siger.documents.av.engine","DEFENDER").withProperty("siger.documents.av.executable",ENGINE.resolve("defender").resolve("MpCmdRun.exe").toString()),
            (c,t)->c.contains("-Command")?new DocumentScanner.Output(0,"1.400.0.0|"+Instant.now().minusSeconds(3600)):new DocumentScanner.Output(EXIT.get(),OUTPUT.get().replace("%s",c.get(c.indexOf("-File")+1))),Clock.systemUTC());
        Path x=Path.of("x.bin");
        EXIT.set(2);OUTPUT.set("CmdTool: Failed with hr = 0x800106ba. Check C:\\Users\\u\\AppData\\Local\\Temp\\MpCmdRun.log for more information");
        var v=defender.scan(x,ENGINE);assertEquals("ERROR",v.verdict());assertEquals("MOTOR_FALLIDO_0x800106ba",v.detail());
        OUTPUT.set("");v=defender.scan(x,ENGINE);assertEquals("ERROR",v.verdict());assertEquals("ANALISIS_INCOMPLETO",v.detail()); // codigo 2 sin texto
        OUTPUT.set("Scan starting...\nScan finished.\nScanning %s found 1 threats.\n\nThreat information\nThreat                  : Virus:DOS/EICAR_Test_File\n");
        v=defender.scan(x,ENGINE);assertEquals("INFECTADO",v.verdict());assertEquals("AMENAZA:Virus:DOS/EICAR_Test_File",v.detail());
        OUTPUT.set("Scan starting...\nScan finished.\nScanning C:\\otro.bin found 1 threats.\n");
        assertEquals("ERROR",defender.scan(x,ENGINE).verdict()); // deteccion de otro archivo
        EXIT.set(0);OUTPUT.set("Scan starting...\nScan finished.\nScanning %s found no threats.\n");
        assertEquals("LIMPIO",defender.scan(x,ENGINE).verdict());
        OUTPUT.set("");assertEquals("ERROR",defender.scan(x,ENGINE).verdict()); // 0 sin confirmacion textual
        EXIT.set(1);OUTPUT.set("");assertEquals("ERROR",defender.scan(x,ENGINE).verdict());
    }

    @Test void carpetaTemporalDelAnalisisSeEliminaSiempre()throws Exception{
        Path tmp=documents.directory.getParent().resolve("tmp");
        java.util.function.Supplier<Long> leftovers=()->{try(var l=Files.list(Files.createDirectories(tmp))){return l.filter(p->p.getFileName().toString().startsWith("av-")).count();}catch(Exception e){throw new IllegalStateException(e);}};
        // El motor deja archivos en --tempdir: se eliminan al terminar.
        DURING.set(c->{Path work=Path.of(c.stream().filter(a->a.startsWith("--tempdir=")).findFirst().orElseThrow().substring(10));
            assertTrue(Files.isDirectory(work));
            try{Files.writeString(work.resolve("clamav-resto.tmp"),"x");Files.createDirectories(work.resolve("sub")).resolve("a.tmp").toFile().createNewFile();}catch(Exception e){throw new IllegalStateException(e);}});
        assertEquals("LIMPIO",documents.scan("ti.doc",original("con temporales")).get("verdict"));
        assertEquals(0L,leftovers.get());
        // Tambien si el analisis falla (excepcion del motor o tiempo excedido).
        DURING.set(c->{throw new IllegalStateException("motor caido");});
        var r=documents.scan("ti.doc",original("motor que falla"));
        assertEquals("ERROR",r.get("verdict"));assertEquals("ANALISIS_NO_EJECUTADO",r.get("detail"));
        assertEquals(0L,leftovers.get());
        // Firmas antiguas: no se crea carpeta.
        DURING.set(c->{});VERSION.set("ClamAV 1.4.1/27000/"+LocalDateTime.now().minusDays(10).format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy",Locale.ENGLISH)));
        documents.scan("ti.doc",original("sin analisis"));
        assertEquals(0L,leftovers.get());
    }

    @Test void sinMotorConfiguradoNoHayAnalisisNiLiberacion(){
        var none=new DocumentScanner(new MockEnvironment());
        assertFalse(none.configured());
        assertEquals("SIN_MOTOR",none.scan(Path.of("x.bin"),Path.of("tmp")).detail());
        assertThrows(IllegalStateException.class,()->new DocumentScanner(new MockEnvironment().withProperty("siger.documents.av.engine","OTRO")));
        assertThrows(IllegalStateException.class,()->new DocumentScanner(new MockEnvironment().withProperty("siger.documents.av.engine","CLAMAV").withProperty("siger.documents.av.executable","C:\\no\\existe\\clamscan.exe")));
        assertThrows(IllegalStateException.class,()->new DocumentScanner(new MockEnvironment().withProperty("siger.documents.av.engine","DEFENDER").withProperty("siger.documents.av.executable",ENGINE.resolve("clamscan.exe").toString())));
    }

    /** Comprobacion real, de solo lectura, del estado de Defender: si el motor no esta activo, el veredicto es ERROR. */
    @Test void defenderNoOperativoFallaCerrado()throws Exception{
        Path mp=Path.of("C:\\Program Files\\Windows Defender\\MpCmdRun.exe");
        Assumptions.assumeTrue(Files.isRegularFile(mp),"Sin Microsoft Defender en este equipo");
        var real=new DocumentScanner(new MockEnvironment().withProperty("siger.documents.av.engine","DEFENDER").withProperty("siger.documents.av.executable",mp.toString()));
        Assumptions.assumeTrue(real.signatures()==null,"Defender operativo: este caso solo cubre el motor inactivo");
        var v=real.scan(Path.of("target","no-se-analiza.bin"),Path.of("target"));
        assertEquals("ERROR",v.verdict());assertEquals("MOTOR_NO_OPERATIVO_O_FIRMAS_DESACTUALIZADAS",v.detail());
    }
}
