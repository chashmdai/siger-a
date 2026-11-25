package cl.nexbyte.sigera;

import java.net.URI;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Verificacion en un MySQL 8.4 AISLADO y desechable (no el de ninguna instancia ni MySQL80): solo se ejecuta con
 * -Dsiger.mysql.base=jdbc:mysql://127.0.0.1:<puerto> -Dsiger.mysql.user=... -Dsiger.mysql.password=... y esquemas
 * siger_a_mysql_pruebas / siger_a_mysql_inst creados para la prueba. Aplica V1-V15 con Flyway y ejercita sobre MySQL el
 * SQL nuevo: ciclo PULL/ACK con productor SINTETICO, consumidor automatico, latidos, outbox con Graph FALSO, comandos
 * de TI, consultas de administracion, liberacion documental, rutinas de arranque de 0.5.0, unicidad del calendario
 * (V13) e inicializacion institucional. Sin red real. El resto de 0.5.0 (sedes, materia, buscador, reportes, perfil)
 * se verifica con la suite completa sobre MySQL ({@link MySqlSuite}) y la actualizacion desde 0.4.0 con
 * {@link MySql84UpgradeTest}.
 */
class MySql84IntegrationTest {
    static final String BASE=System.getProperty("siger.mysql.base");
    @BeforeAll static void requireIsolatedMySql(){Assumptions.assumeTrue(BASE!=null&&BASE.startsWith("jdbc:mysql://127.0.0.1:"),"Sin MySQL 8.4 aislado configurado");}

    static ConfigurableApplicationContext app(String schema,List<String> extra){
        var args=new ArrayList<>(List.of(
            "spring.datasource.url="+BASE+"/"+schema+"?sslMode=REQUIRED&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
            "spring.datasource.username="+System.getProperty("siger.mysql.user"),"spring.datasource.password="+System.getProperty("siger.mysql.password"),
            "spring.flyway.enabled=true","siger.scheduling=false","siger.data=./target/mysql84-"+UUID.randomUUID()));
        args.addAll(extra);
        return new SpringApplicationBuilder(SigerApplication.class).web(WebApplicationType.NONE).profiles("test").run(args.stream().map(a->"--"+a).toArray(String[]::new));
    }

    @Test void migracionesV1aV15YOperacionContinuaSobreMySql84()throws Exception{
        try(var ctx=app("siger_a_mysql_pruebas",List.of("siger.seed-demo=true","siger.folio-prefix=COMA","siger.formulario-trusted-keys="+FormularioDocs.trustFile(),
                "siger.mail.mode=GRAPH","siger.mail.allowed-domains=defensorianinez.cl"))){
            var db=ctx.getBean(Db.class);
            assertTrue(s(db.one("SELECT VERSION() AS v"),"v").startsWith("8.4."),"MySQL 8.4");
            for(int v=1;v<=19;v++)assertEquals(1,db.count("SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=TRUE",String.valueOf(v)),"V"+v);
            assertEquals("22",ctx.getBean(Api.class).health().getBody().get("schema"));
            // Rutinas de arranque de 0.5.0 sobre las semillas: marcadores, hitos sin omisiones y la sede con Coordinacion como DIRECCION.
            for(String marker:List.of(Bootstrap.AUTHORITY_MARKER,Bootstrap.REGIONAL_HEAD_MARKER,PersonIndex.MARKER,Milestones.MARKER))
                assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",marker),marker);
            assertEquals(0,Milestones.omitted(db));
            assertEquals(1,db.count("SELECT COUNT(*) FROM role_assignment g JOIN org_scope o ON o.id=g.scope_id JOIN staff u ON u.id=g.user_id WHERE o.code='REG-DEMO' AND u.username='coordinacion.regional' AND g.role_code='DIRECCION' AND g.revoked_at IS NULL"));
            assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment g JOIN org_scope o ON o.id=g.scope_id WHERE o.regional=TRUE AND g.role_code='JEFATURA' AND g.revoked_at IS NULL"));
            // V13: el UNIQUE (holiday_day, region_key) rige tambien con region nula (nacional = 'NAC') en MySQL.
            db.update("DELETE FROM business_holiday WHERE holiday_day LIKE '2098-%'");
            long tiId=n(db.one("SELECT id FROM staff WHERE username='ti'"),"id");
            db.update("INSERT INTO business_holiday(holiday_day,region_code,region_key,name,created_by,created_ms) VALUES('2098-01-02',NULL,'NAC','Prueba nacional',?,1)",tiId);
            db.update("INSERT INTO business_holiday(holiday_day,region_code,region_key,name,created_by,created_ms) VALUES('2098-01-02','05','05','Prueba regional',?,1)",tiId);
            assertThrows(org.springframework.dao.DuplicateKeyException.class,()->db.update("INSERT INTO business_holiday(holiday_day,region_code,region_key,name,created_by,created_ms) VALUES('2098-01-02',NULL,'NAC','Repetido',?,1)",tiId));
            db.update("DELETE FROM business_holiday WHERE holiday_day LIKE '2098-%'");

            // Ciclo PULL/ACK con el productor sintetico: COM, originales, comprobante y ACK; respuesta perdida conciliada.
            var k=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var ack=new FormularioAck("SIGER_MYSQL_PRUEBA",k.getPrivate(),k.getPublic());
            long scope=n(db.one("SELECT id FROM org_scope WHERE code='UPR-REQ'"),"id");
            try(var producer=new FormularioConsumerTest.Producer(k.getPublic())){
                var reception=ctx.getBean(FormularioReception.class);var integrations=ctx.getBean(Integrations.class);
                var consumer=new FormularioConsumer(new FormularioClient(URI.create(producer.origin()),FormularioConsumerTest.TOKEN,null,true),reception,ctx.getBean(Documents.class),ctx.getBean(FormularioKeys.class),ack,scope);
                String doc=FormularioConsumerTest.adultWithFile(FormularioConsumerTest.original());
                producer.offer(doc,Map.of(FormularioConsumerTest.attachment(doc),FormularioConsumerTest.original()));
                producer.ackOutcome.add(500);
                var auto=new FormularioAutomatic(consumer,reception,integrations,1_000,60_000,2_000);
                try{
                    assertEquals(FormularioAutomatic.MIN_BACKOFF_MS,auto.tick());
                    var r=db.one("SELECT * FROM formulario_reception WHERE operation_id=?",FormularioConsumerTest.operation(doc));
                    assertEquals("REGISTRADA",s(r,"state"));
                    db.update("UPDATE formulario_reception SET ack_next_ms=0 WHERE id=?",n(r,"id"));
                    assertEquals(1_000,auto.tick());
                    assertEquals("CONFIRMADA",s(db.one("SELECT state FROM formulario_reception WHERE id=?",n(r,"id")),"state"));
                    assertEquals(1,db.count("SELECT COUNT(*) FROM communication WHERE external_id=?",FormularioConsumerTest.operation(doc)));
                    assertEquals(1,db.count("SELECT COUNT(*) FROM external_document WHERE communication_id=?",n(r,"communication_id")));
                    assertEquals(auto.holder,s(db.one("SELECT holder FROM formulario_consumer_lock WHERE id=1"),"holder"));
                }finally{auto.stop();}
                assertEquals("DETENIDO",s(db.one("SELECT state FROM integration_status WHERE name='FORMULARIO_PULL'"),"state"));
                var live=new Integrations(db,new org.springframework.mock.env.MockEnvironment().withProperty("siger.formulario.pull.enabled","true").withProperty("siger.formulario.pull.automatic","true"));
                assertTrue(live.stale(Integrations.FORMULARIO,System.currentTimeMillis()));
                assertEquals("SIN_LATIDO",live.health(Integrations.FORMULARIO));
            }

            // Outbox de correo en el mismo COMMIT que el aviso y envio con Graph FALSO; ambiguo resuelto por TI.
            db.update("UPDATE staff SET email='profesional.prueba@defensorianinez.cl' WHERE username='profesional'");
            db.update("UPDATE staff SET must_change=FALSE WHERE username='ti'");
            long user=n(db.one("SELECT id FROM staff WHERE username='profesional'"),"id");
            var tx=new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));var access=ctx.getBean(Access.class);
            // El esquema desechable puede conservar ejecuciones anteriores: se cuenta solo lo de esta.
            long since=n(db.one("SELECT COALESCE(MAX(id),0) AS id FROM mail_outbox"),"id");
            db.update("UPDATE mail_outbox SET state='DESCARTADO' WHERE state IN ('PENDIENTE','REINTENTABLE')");
            tx.executeWithoutResult(st->access.notice(user,"Tienes una tarea asignada en un Caso.","CAS",1));
            tx.executeWithoutResult(st->access.notice(user,"Una tarea fue actualizada o reasignada.","CAS",1));
            var fake=new GraphMailTest.Fake().token().status(202,null).fail(new java.io.IOException("respuesta perdida"));
            var worker=new MailWorker(ctx.getBean(MailOutbox.class),GraphMailTest.graph(fake),ctx.getBean(Integrations.class),1_000,10,1_000);
            worker.tick();
            assertEquals(1,db.count("SELECT COUNT(*) FROM mail_outbox WHERE id>? AND state='ACEPTADO'",since));
            long ambiguous=n(db.one("SELECT id FROM mail_outbox WHERE id>? AND state='AMBIGUO'",since),"id");
            assertEquals(2,db.count("SELECT COUNT(*) FROM mail_attempt WHERE outbox_id>?",since));
            var flow=ctx.getBean(Workflow.class);
            flow.run("ti",new Workflow.Command(UUID.randomUUID(),"MAIL_RESOLVE",ambiguous,null,Map.of("decision","DESCARTAR","reason","Prueba en MySQL 8.4")));
            assertEquals("DESCARTADO",s(db.one("SELECT state FROM mail_outbox WHERE id=?",ambiguous),"state"));
            long revision=n(db.one("SELECT revision FROM integration_control WHERE id=2"),"revision");
            flow.run("ti",new Workflow.Command(UUID.randomUUID(),"INTEGRATION_PAUSE",2L,revision,Map.of("reason","Prueba en MySQL 8.4")));
            flow.run("ti",new Workflow.Command(UUID.randomUUID(),"INTEGRATION_RESUME",2L,revision+1,Map.of("reason","Fin de prueba")));

            // Evidencia de analisis (V10) y liberacion: sin motor no se libera un original real.
            long document=n(db.one("SELECT MAX(id) AS id FROM document_item"),"id");
            long ti=n(db.one("SELECT id FROM staff WHERE username='ti'"),"id");
            db.update("INSERT INTO document_scan(document_id,sha256,engine,verdict,requested_by,started_ms,finished_ms) VALUES(?,?,'CLAMAV','LIMPIO',?,1,2)",document,s(db.one("SELECT sha256 FROM document_item WHERE id=?",document),"sha256"),ti);
            assertThrows(Exception.class,()->db.update("INSERT INTO document_scan(document_id,sha256,engine,verdict,requested_by,started_ms,finished_ms) VALUES(?,?,'CLAMAV','DUDOSO',?,1,2)",document,"x",ti));
            assertEquals(409,assertThrows(Problem.class,()->flow.run("ti",new Workflow.Command(UUID.randomUUID(),"DOC_RELEASE",document,null,Map.of("synthetic_review",true,"reason","x")))).status);
            // Vista de TI sobre MySQL (ONLY_FULL_GROUP_BY): integraciones, cuarentena con ultimo analisis y politica.
            var admin=ctx.getBean(Queries.class).administration(access.actor("ti"));
            assertTrue(((Map<?,?>)admin.get("integrations")).containsKey(Integrations.CORREO));
            assertTrue(((List<?>)admin.get("quarantine")).stream().anyMatch(q->"LIMPIO".equals(((Map<?,?>)q).get("scan_verdict"))));
        }
    }

    @Test void inicializacionInstitucionalSobreMySql84()throws Exception{
        Path dir=Files.createDirectories(Path.of("target","mysql84-inst-"+UUID.randomUUID()));
        Files.writeString(dir.resolve("estructura.json"),InstitutionalBootstrapTest.VALID);
        try(var ctx=app("siger_a_mysql_inst",List.of("siger.bootstrap-file="+dir.resolve("estructura.json")))){
            var db=ctx.getBean(Db.class);
            assertEquals(1,db.count("SELECT COUNT(*) FROM flyway_schema_history WHERE version='22' AND success=TRUE"));
            assertEquals(1,db.count("SELECT COUNT(*) FROM staff WHERE technical=FALSE"));
            assertEquals(3,db.count("SELECT COUNT(*) FROM org_scope"));
            assertEquals(0,db.count("SELECT COUNT(*) FROM staff WHERE LOWER(name) LIKE '%sintetic%'"));
            assertEquals(0,db.count("SELECT COUNT(*) FROM role_assignment WHERE role_code='AUTORIDAD'"));
            assertEquals(1,db.count("SELECT COUNT(*) FROM application_marker WHERE code=?",Bootstrap.AUTHORITY_MARKER));
        }
    }
}
