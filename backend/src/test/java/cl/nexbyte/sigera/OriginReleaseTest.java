package cl.nexbyte.sigera;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Liberacion por analisis en origen (D17, H04c). Reglas puras de la configuracion (lista cerrada de campos, termino
 * obligatorio de a lo mas 90 dias, falla cerrado) y, contra el productor SINTETICO local, la liberacion al registrar,
 * la verificacion del SHA-256 antes de liberar, la cuarentena cuando el modo no rige, la liberacion retroactiva al
 * arrancar, la auditoria del modo (una vez por cambio) y que nunca se liberan adjuntos subidos en SIGER.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.datasource.url=jdbc:h2:mem:sigera_origin_release;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "siger.data=./target/origin-release-${random.uuid}",
    "siger.folio-prefix=COMA"})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OriginReleaseTest {
    @DynamicPropertySource static void trustedKeys(DynamicPropertyRegistry registry){FormularioDocs.trustedKeys(registry);}
    static final ZoneId CHILE=ZoneId.of("America/Santiago");
    @Autowired Db db;@Autowired Documents documents;@Autowired FormularioKeys keys;@Autowired Access access;
    @Autowired IntakeCommands intake;@Autowired PlatformTransactionManager manager;@Autowired Workflow flow;@Autowired OriginRelease bean;

    static FormularioAck ack;static KeyPair ackKeys;static long scope,ti;static Path dir;
    FormularioConsumerTest.Producer producer;

    @BeforeAll static void keys()throws Exception{
        ackKeys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();ack=new FormularioAck("SIGER_A_PRUEBA",ackKeys.getPrivate(),ackKeys.getPublic());
        dir=Files.createDirectories(Path.of("target","origin-release-config-"+UUID.randomUUID()));
    }
    @BeforeEach void start()throws Exception{
        if(scope==0){
            db.update("INSERT INTO org_unit VALUES('ORG','Unidad receptora origen')");
            scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('ORG-REC','Recepcion origen','ORG','REC','CENTRAL')");
            ti=db.insert("INSERT INTO staff(username,name,password_hash,home_scope,must_change) VALUES('ti.origen','TI origen','x',?,FALSE)",scope);
            db.update("INSERT INTO role_assignment(user_id,role_code,scope_id,valid_from,basis) VALUES(?,'TI',NULL,0,'PRUEBA AUTOMATICA')",ti);
        }
        producer=new FormularioConsumerTest.Producer(ackKeys.getPublic());
    }
    @AfterEach void stop(){producer.close();}

    // --- Configuracion ------------------------------------------------------------------------------
    /** Termino = fin del dia de Chile que esta days dias despues del dia de activacion. */
    static Instant end(Instant activated,int days){return activated.atZone(CHILE).toLocalDate().plusDays(days+1L).atStartOfDay(CHILE).toInstant();}
    static String config(String id,Instant activated,Instant until,String account,String reason){
        var m=new LinkedHashMap<String,Object>();
        m.put("kind","LIBERACION_ORIGEN");m.put("version",1);m.put("id",id);m.put("activatedUtc",activated.toString());m.put("until",until.toString());m.put("account",account);m.put("reason",reason);
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(m);
    }
    static String valid(Instant activated,int days){return config(UUID.randomUUID().toString(),activated,end(activated,days),"DDN-SIGER\\svc-siger","Decision del responsable del 2026-11-21 (D17)");}
    /** Archivo de configuracion vigente (activado hace una hora, 10 dias) en una carpeta propia. */
    static Path activeFile(Path folder)throws Exception{
        Path f=folder.resolve("liberacion-origen-"+UUID.randomUUID()+".json");Files.writeString(f,valid(Instant.now().minus(Duration.ofHours(1)),10));return f;
    }
    OriginRelease mode(String json,Clock clock,boolean engine,String... props)throws Exception{
        var env=new MockEnvironment();
        if(json!=null){Path f=dir.resolve(UUID.randomUUID()+".json");Files.writeString(f,json);env.withProperty("siger.documents.origin-release.file",f.toString());}
        for(int i=0;i<props.length;i+=2)env.withProperty(props[i],props[i+1]);
        return new OriginRelease(db,documents,engine,manager,env,clock);
    }
    OriginRelease active()throws Exception{return mode(valid(Instant.now().minus(Duration.ofHours(1)),10),Clock.systemUTC(),false);}
    OriginRelease inactive()throws Exception{return mode(null,Clock.systemUTC(),false);}

    static String state(String json,long now){return OriginRelease.parse(json,now).state();}
    static String detail(String json,long now){return OriginRelease.parse(json,now).detail();}

    @Test @Order(2) void reglasDeLaConfiguracion()throws Exception{
        Instant at=Instant.parse("2026-12-04T12:00:00Z");long now=at.plusSeconds(60).toEpochMilli();String id=UUID.randomUUID().toString(),who="DDN-SIGER\\svc-siger",why="Decision D17";
        assertEquals("ACTIVO",state(config(id,at,end(at,10),who,why),now));
        assertEquals("ACTIVO",state("﻿"+config(id,at,end(at,10),who,why),now),"admite BOM de Windows PowerShell");
        // Termino: fin de un dia de Chile, posterior a la activacion y a lo mas 90 dias despues.
        assertEquals("ACTIVO",state(config(id,at,end(at,90),who,why),now));
        assertEquals("TERMINO_EXCEDE_90_DIAS",detail(config(id,at,end(at,91),who,why),now));
        assertEquals("ACTIVO",state(config(id,at,end(at,0),who,why),now),"hasta el fin del mismo dia");
        assertEquals("FECHAS",detail(config(id,at,end(at,10).minusSeconds(3600),who,why),now),"no es el fin de un dia de Chile");
        assertEquals("FECHAS",detail(config(id,at,end(at,-1),who,why),now),"termino anterior a la activacion");
        Instant summer=Instant.parse("2027-01-20T12:00:00Z");
        assertEquals("ACTIVO",state(config(id,summer,Instant.parse("2027-04-06T04:00:00Z"),who,why),summer.toEpochMilli()),"cambio de horario de abril");
        assertEquals("FECHAS",detail(config(id,summer,Instant.parse("2027-04-06T03:00:00Z"),who,why),summer.toEpochMilli()));
        assertEquals("FECHAS",detail(config(id,at,end(at,10),who,why).replace(end(at,10).toString(),"manana"),now));
        // Activacion futura (reloj o archivo dudoso), con tolerancia de 5 minutos.
        assertEquals("ACTIVACION_FUTURA",detail(config(id,at,end(at,10),who,why),at.minusSeconds(600).toEpochMilli()));
        assertEquals("ACTIVO",state(config(id,at,end(at,10),who,why),at.minusSeconds(240).toEpochMilli()));
        // Lista cerrada de campos, tipo, version e identificador.
        assertEquals("CAMPOS",detail(config(id,at,end(at,10),who,why).replace("}",",\"extra\":\"x\"}"),now));
        assertEquals("CAMPOS",detail(config(id,at,end(at,10),who,why).replace(",\"reason\":\"Decision D17\"",""),now));
        assertEquals("CAMPOS",detail(config(id,at,end(at,10),who,why).replace("\"account\":\"DDN-SIGER\\\\svc-siger\"","\"account\":7"),now));
        assertEquals("TIPO",detail(config(id,at,end(at,10),who,why).replace("LIBERACION_ORIGEN","OTRO"),now));
        assertEquals("TIPO",detail(config(id,at,end(at,10),who,why).replace("\"version\":1","\"version\":2"),now));
        assertEquals("TIPO",detail(config(id,at,end(at,10),who,why).replace("\"version\":1","\"version\":\"1\""),now));
        assertEquals("IDENTIFICADOR",detail(config("no-uuid",at,end(at,10),who,why),now));
        assertEquals("CUENTA",detail(config(id,at,end(at,10)," ",why),now));
        assertEquals("MOTIVO",detail(config(id,at,end(at,10),who,""),now));
        assertEquals("MOTIVO",detail(config(id,at,end(at,10),who,"x".repeat(501)),now));
        assertEquals("ACTIVO",state(config(id,at,end(at,10),who,"x".repeat(500)),now));
        assertEquals("MOTIVO",detail(config(id,at,end(at,10),who,"linea\notra"),now));
        assertEquals("ILEGIBLE",detail("no es json",now));
        assertEquals("ILEGIBLE",detail("[]",now));
        // Archivo: ausente, carpeta, demasiado grande; con motor configurado nunca rige.
        assertEquals("ILEGIBLE",OriginRelease.read(dir.resolve("falta.json"),false,now).detail());
        assertEquals("ILEGIBLE",OriginRelease.read(dir,false,now).detail());
        Path big=dir.resolve("grande.json");Files.writeString(big," ".repeat(5000)+config(id,at,end(at,10),who,why));
        assertEquals("ILEGIBLE",OriginRelease.read(big,false,now).detail());
        Path ok=dir.resolve("vigente.json");Files.writeString(ok,config(id,at,end(at,10),who,why));
        assertEquals("ACTIVO",OriginRelease.read(ok,false,now).state());
        assertEquals("CON_MOTOR",OriginRelease.read(ok,true,now).state());
        // Vence sin reiniciar.
        var fixed=mode(config(id,at,end(at,10),who,why),Clock.fixed(end(at,10).minusMillis(1),ZoneOffset.UTC),false);
        assertEquals("ACTIVO",fixed.state(end(at,10).toEpochMilli()-1));assertTrue(fixed.active(end(at,10).toEpochMilli()-1));
        assertEquals("VENCIDO",fixed.state(end(at,10).toEpochMilli()));assertFalse(fixed.active(end(at,10).toEpochMilli()));
        // Sin archivo no rige; en la prueba de Graph, la sonda, migrate-only o la recuperacion de TI tampoco.
        assertEquals("INACTIVO",inactive().config.state());assertFalse(inactive().active(System.currentTimeMillis()));
        String json=valid(Instant.now().minus(Duration.ofHours(1)),10);
        assertTrue(mode(json,Clock.systemUTC(),false).active(System.currentTimeMillis()));
        for(var p:List.of(List.of("siger.migrate-only","true"),List.of("siger.mail.check.mode","TOKEN"),List.of("siger.formulario.pull.probe","true"),List.of("siger.recover-ti.user","ti.nueva")))
            assertFalse(mode(json,Clock.systemUTC(),false,p.get(0),p.get(1)).active(System.currentTimeMillis()),p.toString());
        assertFalse(mode(json,Clock.systemUTC(),true).active(System.currentTimeMillis()),"con motor no rige");
        // El bean de este contexto no recibe archivo: no rige.
        assertEquals("INACTIVO",bean.config.state());
    }

    // --- Recepcion -----------------------------------------------------------------------------------
    FormularioReception reception(OriginRelease o){return new FormularioReception(db,access,intake,documents,manager,o);}
    /** Un ciclo del consumidor con una entrega con un original; devuelve la fila del documento registrado. */
    Map<String,Object> receive(OriginRelease o,byte[] content){
        String document=FormularioConsumerTest.adultWithFile(content);
        producer.offer(document,Map.of(FormularioConsumerTest.attachment(document),content));
        var consumer=new FormularioConsumer(new FormularioClient(URI.create(producer.origin()),FormularioConsumerTest.TOKEN,null,true),reception(o),documents,keys,ack,scope);
        var cycle=consumer.run();
        assertTrue(cycle.clean(),cycle.toString());assertEquals(1,cycle.registered());assertEquals(1,cycle.confirmed());
        var r=db.one("SELECT communication_id FROM formulario_reception WHERE operation_id=?",FormularioConsumerTest.operation(document));
        return db.one("SELECT d.* FROM document_item d JOIN external_document x ON x.document_id=d.id WHERE x.communication_id=?",n(r,"communication_id"));
    }
    long released(long document){return db.count("SELECT COUNT(*) FROM audit_event WHERE action=? AND resource_type='DOCUMENTO' AND resource_id=?",OriginRelease.RELEASED,document);}
    String basis(long document){var r=db.one("SELECT "+OriginRelease.BASIS_SQL+" FROM document_item d WHERE d.id=?",document);return r.get("release_basis")==null?null:s(r,"release_basis");}
    static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}

    @Test @Order(3) void conElModoVigenteSeLiberaAlRegistrarConSuAuditoria()throws Exception{
        var o=active();
        var d=receive(o,bytes("Original sintetico liberado en origen.\n"));long id=n(d,"id");
        assertFalse(b(d,"quarantined"),"liberado en la transaccion de recepcion");
        assertEquals(1,released(id));
        var a=db.one("SELECT * FROM audit_event WHERE action=? AND resource_id=?",OriginRelease.RELEASED,id);
        assertTrue(s(a,"note").startsWith("Motivo: analisis en origen del Formulario. Firma Ed25519 de la entrega y SHA-256 del original verificados por SIGER. Modo D17 "+o.config.id()),s(a,"note"));
        assertEquals(scope,n(a,"scope_id"));
        assertEquals(n(db.one("SELECT id FROM staff WHERE username=?",FormularioReception.TECHNICAL),"id"),n(a,"actor_id"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM document_scan WHERE document_id=?",id),"nunca se presenta como un analisis LIMPIO de un motor");
        assertEquals("ORIGEN",basis(id));
        // Un documento liberado tras un analisis local (otra auditoria del mismo recurso) no se presenta como ORIGEN.
        long local=sigerUpload(null);
        db.update("UPDATE document_item SET quarantined=FALSE WHERE id=?",local);
        db.audit(ti,"LIBERACION_TRAS_ANALISIS","DOCUMENTO",local,null,"Analisis #1 CLAMAV");
        assertNull(basis(local));
        long com=n(d,"communication_id");
        assertEquals("1 originales del Formulario liberados por analisis en origen del Formulario (D17)",s(db.one("SELECT note FROM audit_event WHERE action='COM_ORIGINALES_CUSTODIADOS' AND resource_id=?",com),"note"));
        // Reentrega idempotente: mismo comprobante, sin otra liberacion ni auditoria.
        var r=db.one("SELECT * FROM formulario_reception WHERE communication_id=?",com);
        String document=s(db.one("SELECT document FROM communication_original WHERE communication_id=?",com),"document");
        var again=reception(o).register(ExternalOriginal.parse(document,keys.keys()),scope,UUID.randomUUID(),System.currentTimeMillis()+60_000,2,ack);
        assertFalse(again.created());assertEquals(s(r,"receipt"),again.receipt());
        assertEquals(1,released(id));
    }

    @Test @Order(4) void sinModoVigenteQuedaEnCuarentena()throws Exception{
        Instant at=Instant.now().minus(Duration.ofDays(20));
        var cases=new LinkedHashMap<String,OriginRelease>();
        cases.put("sin archivo",inactive());
        cases.put("vencido",mode(config(UUID.randomUUID().toString(),at,end(at,10),"DDN-SIGER\\svc-siger","D17"),Clock.systemUTC(),false));
        cases.put("ilegible",mode("{",Clock.systemUTC(),false));
        cases.put("termino excedido",mode(config(UUID.randomUUID().toString(),Instant.now(),end(Instant.now(),91),"DDN-SIGER\\svc-siger","D17"),Clock.systemUTC(),false));
        cases.put("con motor",mode(valid(Instant.now().minus(Duration.ofHours(1)),10),Clock.systemUTC(),true));
        cases.put("sonda",mode(valid(Instant.now().minus(Duration.ofHours(1)),10),Clock.systemUTC(),false,"siger.formulario.pull.probe","true"));
        for(var c:cases.entrySet()){
            var d=receive(c.getValue(),bytes("Original en cuarentena: "+c.getKey()+"\n"));long id=n(d,"id");
            assertTrue(b(d,"quarantined"),c.getKey());assertEquals(0,released(id),c.getKey());assertNull(basis(id),c.getKey());
            assertEquals("1 originales del Formulario en cuarentena",s(db.one("SELECT note FROM audit_event WHERE action='COM_ORIGINALES_CUSTODIADOS' AND resource_id=?",n(d,"communication_id")),"note"),c.getKey());
        }
        assertEquals("VENCIDO",cases.get("vencido").state(System.currentTimeMillis()));
    }

    @Test @Order(5) void conElModoVigenteUnShaDistintoNoRegistra()throws Exception{
        byte[] content=bytes("Original firmado con su SHA-256.\n");
        String document=FormularioConsumerTest.adultWithFile(content);
        var o=ExternalOriginal.parse(document,keys.keys());
        String key=Documents.externalKey(o.externalId(),FormularioConsumerTest.attachment(document));
        byte[] altered=content.clone();altered[0]^=1; // mismo tamano, otro contenido
        Files.createDirectories(documents.directory);Files.write(documents.directory.resolve(key),altered);
        long coms=db.count("SELECT COUNT(*) FROM communication"),docs=db.count("SELECT COUNT(*) FROM document_item"),audits=db.count("SELECT COUNT(*) FROM audit_event");
        var e=assertThrows(Problem.class,()->reception(active()).register(o,scope,UUID.randomUUID(),System.currentTimeMillis()+60_000,1,ack));
        assertEquals(503,e.status);
        assertEquals(coms,db.count("SELECT COUNT(*) FROM communication"),"sin COMMIT");assertEquals(docs,db.count("SELECT COUNT(*) FROM document_item"));
        assertEquals(audits,db.count("SELECT COUNT(*) FROM audit_event"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM formulario_reception WHERE operation_id=?",o.externalId()));
        // Sin el modo, el registro solo comprueba presencia y tamano (el SHA-256 completo se verifica antes del ACK).
        var r=reception(inactive()).register(o,scope,UUID.randomUUID(),System.currentTimeMillis()+60_000,1,ack);
        assertTrue(b(db.one("SELECT quarantined FROM document_item WHERE communication_id=?",r.communication()),"quarantined"));
        // Ese registro queda con el ACK pendiente y la custodia alterada: se bloquea para que no lo concilien otros ciclos.
        db.update("UPDATE formulario_reception SET ack_blocked=TRUE WHERE communication_id=?",r.communication());
    }

    /** Adjunto subido en SIGER (sin external_document), en cuarentena. */
    long sigerUpload(Long communication)throws Exception{
        long com=communication!=null?communication:db.insert("INSERT INTO communication(folio,channel,received_ms,subject,body,origin_scope,created_by,created_ms,state) VALUES(?,?,?,?,?,?,?,?,'NUEVA')",
            "ORG-"+UUID.randomUUID().toString().substring(0,8),"PRESENCIAL",1L,"Asunto","Cuerpo",scope,ti,1L);
        byte[] content=bytes("Adjunto subido en SIGER "+UUID.randomUUID());String key=UUID.randomUUID()+".bin";
        Files.createDirectories(documents.directory);Files.write(documents.directory.resolve(key),content);
        return db.insert("INSERT INTO document_item(communication_id,name,storage_key,sha256,size_bytes,content_type,uploaded_by,created_ms,document_type) VALUES(?,?,?,?,?,'application/octet-stream',?,?,'INGRESO')",
            com,"adjunto.pdf",key,ExternalOriginal.sha256(content),content.length,ti,1L);
    }

    @Test @Order(6) void liberacionRetroactivaAcotadaAlArrancar()throws Exception{
        var off=inactive();
        long pending=n(receive(off,bytes("Pendiente que se libera al regir el modo.\n")),"id");
        long infected=n(receive(off,bytes("Pendiente con deteccion previa.\n")),"id");
        db.update("INSERT INTO document_scan(document_id,sha256,engine,engine_version,verdict,exit_code,detail,requested_by,started_ms,finished_ms) SELECT id,sha256,'CLAMAV','1','INFECTADO',1,'FIRMA:Prueba',?,1,1 FROM document_item WHERE id=?",ti,infected);
        var alteredRow=receive(off,bytes("Pendiente alterado en disco.\n"));long altered=n(alteredRow,"id");
        byte[] bad=Files.readAllBytes(documents.directory.resolve(s(alteredRow,"storage_key")));bad[0]^=1;Files.write(documents.directory.resolve(s(alteredRow,"storage_key")),bad);
        long siger=sigerUpload(n(alteredRow,"communication_id")),sigerAlone=sigerUpload(null);
        // Adjunto de un funcionario con una fila external_document forjada, en una COM del Formulario con recepcion:
        // tampoco (exige la identidad tecnica).
        long forged=sigerUpload(n(alteredRow,"communication_id"));
        db.update("INSERT INTO external_document(document_id,communication_id,operation_id,attachment_id,media_type) SELECT id,communication_id,?,?,'text/plain' FROM document_item WHERE id=?",UUID.randomUUID().toString(),UUID.randomUUID().toString(),forged);
        // Original de la identidad tecnica en una COM sin recepcion registrada: tampoco.
        long orphan=pendingFormularioOriginal(db,documents.directory,scope);
        db.update("DELETE FROM formulario_reception WHERE communication_id=(SELECT communication_id FROM document_item WHERE id=?)",orphan);
        long maintenanceBefore=db.count("SELECT COUNT(*) FROM audit_event WHERE action=?",OriginRelease.RELEASED);

        var on=active();on.startup();
        assertFalse(b(db.one("SELECT quarantined FROM document_item WHERE id=?",pending),"quarantined"),"pendiente liberado");
        var a=db.one("SELECT * FROM audit_event WHERE action=? AND resource_id=?",OriginRelease.RELEASED,pending);
        assertTrue(s(a,"note").startsWith("Motivo: analisis en origen del Formulario (liberado al regir de nuevo el modo; estaba en cuarentena). Firma Ed25519"),s(a,"note"));
        assertEquals(n(db.one("SELECT id FROM staff WHERE username=?",Bootstrap.MAINTENANCE),"id"),n(a,"actor_id"));
        assertEquals("ORIGEN",basis(pending));
        for(long id:List.of(infected,altered,siger,sigerAlone,forged,orphan)){
            assertTrue(b(db.one("SELECT quarantined FROM document_item WHERE id=?",id),"quarantined"),"sigue en cuarentena: "+id);assertEquals(0,released(id));
        }
        assertEquals(0,db.count("SELECT COUNT(*) FROM document_scan WHERE document_id=?",pending));
        // Idempotente: otro arranque no libera ni audita de nuevo.
        long total=db.count("SELECT COUNT(*) FROM audit_event");
        on.startup();active().releasePending();
        assertEquals(1,released(pending));
        assertTrue(db.count("SELECT COUNT(*) FROM audit_event WHERE action=?",OriginRelease.RELEASED)>maintenanceBefore);
        assertEquals(total,db.count("SELECT COUNT(*) FROM audit_event"),"sin auditoria nueva");
        // Sin el modo vigente no hay liberacion retroactiva, aunque haya pendientes.
        long more=n(receive(off,bytes("Pendiente con el modo vencido.\n")),"id");
        Instant at=Instant.now().minus(Duration.ofDays(20));
        mode(config(UUID.randomUUID().toString(),at,end(at,10),"DDN-SIGER\\svc-siger","D17"),Clock.systemUTC(),false).startup();
        mode(valid(Instant.now().minus(Duration.ofHours(1)),10),Clock.systemUTC(),false,"siger.migrate-only","true").startup();
        assertTrue(b(db.one("SELECT quarantined FROM document_item WHERE id=?",more),"quarantined"));
        // DOC_RELEASE sigue exigiendo un motor para un adjunto de SIGER aunque el modo este activo.
        var e=assertThrows(Problem.class,()->flow.run("ti.origen",new Workflow.Command(UUID.randomUUID(),"DOC_RELEASE",sigerAlone,null,Map.of("reason","Intento sin motor"))));
        assertEquals(409,e.status);assertTrue(b(db.one("SELECT quarantined FROM document_item WHERE id=?",sigerAlone),"quarantined"));
    }

    // --- Auditoria de arranque ------------------------------------------------------------------------
    List<Map<String,Object>> modeEvents(){return db.rows("SELECT action,note,actor_id FROM audit_event WHERE resource_type='SISTEMA' AND resource_id=? ORDER BY id",OriginRelease.RESOURCE);}

    @Test @Order(1) void auditoriaDeArranqueUnaVezPorCambio()throws Exception{
        assertTrue(modeEvents().isEmpty());
        inactive().startup();
        assertTrue(modeEvents().isEmpty(),"nunca activado: sin evento");
        Instant at=Instant.now().minus(Duration.ofHours(1));String x=UUID.randomUUID().toString();
        String json=config(x,at,end(at,10),"DDN-SIGER\\svc-siger","Decision del responsable (D17)");
        mode(json,Clock.systemUTC(),false).startup();
        var events=modeEvents();assertEquals(1,events.size());
        assertEquals("LIBERACION_ORIGEN_ACTIVADA",s(events.getLast(),"action"));
        String note=s(events.getLast(),"note");
        assertTrue(note.contains(x)&&note.contains("DDN-SIGER\\svc-siger")&&note.contains("Motivo: Decision del responsable (D17)")&&note.contains(OriginRelease.day(end(at,10).toEpochMilli())),note);
        assertEquals(n(db.one("SELECT id FROM staff WHERE username=?",Bootstrap.MAINTENANCE),"id"),n(events.getLast(),"actor_id"));
        mode(json,Clock.systemUTC(),false).startup();
        assertEquals(1,modeEvents().size(),"mismo modo: sin otro evento");
        mode(valid(at,20),Clock.systemUTC(),false).startup();
        assertEquals(2,modeEvents().size(),"renovacion: otro id");
        mode(json,Clock.fixed(end(at,10).plusSeconds(1),ZoneOffset.UTC),false).startup();
        assertEquals("LIBERACION_ORIGEN_VENCIDA",s(modeEvents().getLast(),"action"));
        mode(json,Clock.fixed(end(at,10).plusSeconds(5),ZoneOffset.UTC),false).startup();
        assertEquals(3,modeEvents().size());
        mode("{",Clock.systemUTC(),false).startup();
        assertEquals("LIBERACION_ORIGEN_INVALIDA",s(modeEvents().getLast(),"action"));assertTrue(s(modeEvents().getLast(),"note").contains("ILEGIBLE"));
        mode(json,Clock.systemUTC(),true).startup();
        assertTrue(s(modeEvents().getLast(),"note").contains("CON_MOTOR"));
        assertEquals(5,modeEvents().size());
        inactive().startup();
        assertEquals("LIBERACION_ORIGEN_DESACTIVADA",s(modeEvents().getLast(),"action"));
        inactive().startup();
        assertEquals(6,modeEvents().size(),"desactivado una sola vez");
        // Procesos que no aplican el modo: ni auditoria ni liberacion.
        for(var p:List.of(List.of("siger.migrate-only","true"),List.of("siger.mail.check.mode","ENVIO"),List.of("siger.formulario.pull.probe","true"),List.of("siger.recover-ti.user","ti.nueva")))
            mode(valid(at,5),Clock.systemUTC(),false,p.get(0),p.get(1)).startup();
        assertEquals(6,modeEvents().size());
    }

    // --- Avisos y politica para TI --------------------------------------------------------------------
    static List<String> codes(OriginRelease o){return o.warnings().stream().map(w->s(w,"code")).toList();}

    @Test @Order(7) void avisosYPoliticaParaTi()throws Exception{
        Instant at=Instant.now().minus(Duration.ofHours(1));
        assertEquals(List.of(),codes(inactive()));
        var on=mode(valid(at,30),Clock.systemUTC(),false);
        assertEquals(List.of("LIBERACION_ORIGEN_ACTIVA"),codes(on));
        assertEquals("ACTIVO",on.policy().get("state"));assertEquals(OriginRelease.day(on.config.until()),on.policy().get("until_day"));
        assertEquals(List.of("LIBERACION_ORIGEN_POR_VENCER"),codes(mode(valid(at,3),Clock.systemUTC(),false)));
        String json=config(UUID.randomUUID().toString(),at,end(at,1),"DDN-SIGER\\svc-siger","D17");
        var expired=mode(json,Clock.fixed(end(at,1).plusSeconds(1),ZoneOffset.UTC),false);
        assertEquals(List.of("LIBERACION_ORIGEN_VENCIDA"),codes(expired));
        long pending=db.count("SELECT COUNT(*) FROM document_item d JOIN external_document x ON x.document_id=d.id WHERE d.quarantined=TRUE");
        assertTrue(pending>0);assertEquals(pending,expired.warnings().getFirst().get("count"));
        assertTrue(s(expired.warnings().getFirst(),"message").contains("vuelven a quedar en cuarentena"));
        assertEquals("VENCIDO",expired.policy().get("state"));
        var invalid=mode("[]",Clock.systemUTC(),false);
        assertEquals(List.of("LIBERACION_ORIGEN_INVALIDA"),codes(invalid));assertEquals("ILEGIBLE",invalid.policy().get("detail"));
        assertEquals(List.of("LIBERACION_ORIGEN_CON_MOTOR"),codes(mode(valid(at,30),Clock.systemUTC(),true)));
        for(var w:expired.warnings())for(var v:w.values())assertFalse(String.valueOf(v).contains("svc-siger"),"el aviso no expone la cuenta");
    }

    /** Original del Formulario en cuarentena, registrado como lo hace FormularioReception (para otras pruebas). */
    static long pendingFormularioOriginal(Db db,Path documentsDir,long scope)throws Exception{
        var t=db.rows("SELECT id FROM staff WHERE username=?",FormularioReception.TECHNICAL);
        long tech=t.isEmpty()?db.insert("INSERT INTO staff(username,name,password_hash,enabled,must_change,home_scope,technical) VALUES(?,?,'!',FALSE,FALSE,?,TRUE)",FormularioReception.TECHNICAL,FormularioReception.TECHNICAL_NAME,scope):n(t.getFirst(),"id");
        long com=db.insert("INSERT INTO communication(folio,external_source,external_id,channel,received_ms,subject,body,origin_scope,created_by,created_ms,state) VALUES(?,?,?,?,?,?,?,?,?,?,'NUEVA')",
            "ORIG-"+UUID.randomUUID().toString().substring(0,8),"FORMULARIO_WEB",UUID.randomUUID().toString(),"FORMULARIO_WEB_PUBLICO",1L,"Asunto","Cuerpo",scope,tech,1L);
        byte[] content=bytes("Original pendiente "+UUID.randomUUID());String op=UUID.randomUUID().toString(),file=UUID.randomUUID().toString(),key=Documents.externalKey(op,file);
        Files.createDirectories(documentsDir);Files.write(documentsDir.resolve(key),content);
        long doc=db.insert("INSERT INTO document_item(communication_id,name,storage_key,sha256,size_bytes,content_type,uploaded_by,created_ms,document_type) VALUES(?,?,?,?,?,'application/octet-stream',?,?,'INGRESO')",
            com,"original.txt",key,ExternalOriginal.sha256(content),content.length,tech,1L);
        db.update("INSERT INTO external_document(document_id,communication_id,operation_id,attachment_id,media_type) VALUES(?,?,?,?,'text/plain')",doc,com,op,file);
        db.update("INSERT INTO formulario_reception(operation_id,state,communication_id,created_ms,updated_ms) VALUES(?,'CONFIRMADA',?,1,1)",op,com);
        return doc;
    }
}
