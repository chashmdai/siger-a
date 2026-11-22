package cl.nexbyte.sigera;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static cl.nexbyte.sigera.Db.*;

/**
 * Prueba controlada de Graph (H01) como la lanza scripts\Test-GraphMail.ps1: la aplicacion completa sin servidor web
 * ni tareas, con el transporte Graph FALSO de GraphMailTest (nunca red real). Comprueba que TOKEN no envia, que ENVIO
 * manda un solo correo de texto fijo al destinatario pedido, que la salida no expone credenciales ni destinatario, que
 * cada rechazo ocurre antes de la red y que ninguna tabla de la base cambia (instantanea completa, con una fila
 * PENDIENTE en la cola de avisos que debe quedar intacta).
 */
@ExtendWith(OutputCaptureExtension.class)
class GraphCheckTest {
    static final String DB="jdbc:h2:mem:sigera_graph_check;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    static final String TO="prueba.graph@defensorianinez.cl";
    static final JsonMapper JSON=JsonMapper.builder().build();
    static ConfigurableApplicationContext service;static Db db;static Path dir;

    /** Instancia "de servicio" que migra el esquema y siembra un aviso con su correo PENDIENTE en la cola. */
    @BeforeAll static void start()throws Exception{
        dir=Files.createDirectories(Path.of("target","graph-check-"+UUID.randomUUID()));
        service=app(null).run("--spring.datasource.url="+DB,"--siger.data="+dir.resolve("data"));
        db=service.getBean(Db.class);
        db.update("INSERT INTO org_unit VALUES('GCU','Unidad de prueba de Graph')");
        long scope=db.insert("INSERT INTO org_scope(code,name,unit_code,area_code,site_code) VALUES('GCU-1','Prueba Graph','GCU','A','CENTRAL')");
        long user=db.insert("INSERT INTO staff(username,name,email,password_hash,enabled,home_scope,must_change) VALUES('ana.graph','Ana Graph',?,'test-non-login',TRUE,?,FALSE)","ana.graph@defensorianinez.cl",scope);
        long now=System.currentTimeMillis();
        long notice=db.insert("INSERT INTO inbox_notice(user_id,at_ms,text_value,resource_type,resource_id) VALUES(?,?,?,?,?)",user,now,"Aviso sintetico pendiente","CAS",1);
        db.update("INSERT INTO mail_outbox(notice_id,recipient_id,correlation_id,state,attempts,next_ms,created_ms,updated_ms) VALUES(?,?,?,'PENDIENTE',0,?,?,?)",
            notice,user,UUID.randomUUID().toString(),now-1000,now,now);
        db.update("UPDATE integration_status SET state='DETENIDO',cycles=7,updated_ms=? WHERE name='CORREO_GRAPH'",now);
    }
    @AfterAll static void stop(){if(service!=null)service.close();}

    static SpringApplicationBuilder app(GraphMail.Connection fake){
        var b=new SpringApplicationBuilder(SigerApplication.class).web(WebApplicationType.NONE).profiles("test");
        if(fake!=null)b.initializers((ApplicationContextInitializer<ConfigurableApplicationContext>)ctx->ctx.getBeanFactory().registerSingleton("graphConexionFalsa",fake));
        return b;
    }
    static List<String> props(String mode,String to){
        var p=new ArrayList<>(List.of("spring.datasource.url="+DB,"siger.data="+dir.resolve("data"),"siger.scheduling=false",
            "siger.mail.graph.tenant="+GraphMailTest.TENANT,"siger.mail.graph.client="+GraphMailTest.CLIENT,
            "siger.mail.graph.sender="+GraphMailTest.SENDER,"siger.mail.graph.secret="+GraphMailTest.SECRET,
            "siger.mail.allowed-domains=defensorianinez.cl"));
        if(mode!=null)p.add("siger.mail.check.mode="+mode);
        if(to!=null)p.add("siger.mail.check.to="+to);
        return p;
    }
    /** Reemplaza una propiedad (un argumento repetido no reemplaza al anterior). */
    static List<String> with(List<String> props,String key,String value){props.removeIf(x->x.startsWith(key+"="));props.add(key+"="+value);return props;}
    static ConfigurableApplicationContext run(SpringApplicationBuilder app,List<String> props){
        return app.run(props.stream().map(x->"--"+x).toArray(String[]::new));
    }
    static Throwable root(Throwable t){while(t.getCause()!=null)t=t.getCause();return t;}

    /** La salida del proceso solo informa modo, estado, fase, HTTP y request-id. */
    static void noExpone(CapturedOutput out){
        String all=out.getAll();
        for(var value:List.of(GraphMailTest.SECRET,"bearer-sintetico",GraphMailTest.TENANT,GraphMailTest.CLIENT,GraphMailTest.SENDER,TO,"ana.graph@defensorianinez.cl"))
            assertFalse(all.contains(value),"la salida no expone credenciales ni destinatarios");
    }
    static GraphMailTest.Fake token(GraphMailTest.Fake f,String requestId){
        f.answers.add(new GraphMail.Response(200,"{\"access_token\":\"bearer-sintetico\",\"expires_in\":3600}".getBytes(java.nio.charset.StandardCharsets.UTF_8),null,requestId));return f;
    }

    @Test void tokenPideSoloElTokenYNoEnvia(CapturedOutput out){
        var before=snapshot();
        var fake=token(new GraphMailTest.Fake(),"abcdef01-2345-4678-9abc-def012345678");
        var ctx=run(app(fake),props("TOKEN",null));
        assertFalse(ctx.isActive(),"el proceso cierra su contexto");
        assertEquals(1,fake.calls.size(),"solo la solicitud del token");
        assertEquals("https://login.microsoftonline.com/"+GraphMailTest.TENANT+"/oauth2/v2.0/token",fake.calls.getFirst().uri().toString());
        assertTrue(fake.calls.stream().noneMatch(c->c.uri().toString().contains("sendMail")),"TOKEN nunca llama a sendMail");
        assertTrue(out.getAll().contains("Prueba de Graph: modo=TOKEN estado=ACEPTADO fase=TOKEN http=200 requestId=abcdef01-2345-4678-9abc-def012345678"),out.getAll());
        noExpone(out);
        assertEquals(before,snapshot(),"ninguna tabla cambia");
    }

    @Test void envioMandaUnSoloCorreoDeTextoFijoAlDestinatario(CapturedOutput out){
        var before=snapshot();
        var fake=token(new GraphMailTest.Fake(),null).status(202,null);
        var ctx=run(app(fake),props("ENVIO",TO));
        assertFalse(ctx.isActive());
        assertEquals(2,fake.calls.size(),"token y un solo sendMail");
        var send=fake.calls.get(1);
        assertEquals("https://graph.microsoft.com/v1.0/users/avisos.siger%40defensorianinez.cl/sendMail",send.uri().toString());
        var message=JSON.readTree(send.body()).path("message");
        assertEquals(GraphCheck.SUBJECT,message.path("subject").asString());
        assertEquals(GraphCheck.TEXT,message.path("body").path("content").asString());
        assertEquals(1,message.path("toRecipients").size());
        assertEquals(TO,message.path("toRecipients").get(0).path("emailAddress").path("address").asString());
        assertTrue(out.getAll().contains("Prueba de Graph: modo=ENVIO estado=ACEPTADO fase=GRAPH http=202 requestId=12345678-1234-4234-8234-123456789abc"),out.getAll());
        noExpone(out);
        assertEquals(before,snapshot(),"la cola de avisos y el resto de la base quedan intactos");
    }

    @Test void fallosDeGraphTerminanEnErrorConSuRegistro(CapturedOutput out){
        var before=snapshot();
        // Token rechazado: no se intenta el envio.
        var denied=new GraphMailTest.Fake().status(401,null);
        var e=assertThrows(IllegalStateException.class,()->run(app(denied),props("ENVIO",TO)));
        assertTrue(root(e).getMessage().contains("no acreditada"),root(e).getMessage());
        assertEquals(1,denied.calls.size());
        assertTrue(out.getAll().contains("Prueba de Graph: modo=ENVIO estado=REINTENTABLE fase=TOKEN http=401"),out.getAll());
        // Remitente sin Mail.Send: Graph rechaza el envio.
        var forbidden=token(new GraphMailTest.Fake(),null).status(403,null);
        assertThrows(IllegalStateException.class,()->run(app(forbidden),props("ENVIO",TO)));
        assertEquals(2,forbidden.calls.size());
        assertTrue(out.getAll().contains("Prueba de Graph: modo=ENVIO estado=RECHAZADO fase=GRAPH http=403"),out.getAll());
        // Sin red en el token.
        var down=new GraphMailTest.Fake().fail(new java.io.IOException("sin red"));
        assertThrows(IllegalStateException.class,()->run(app(down),props("TOKEN",null)));
        assertTrue(out.getAll().contains("Prueba de Graph: modo=TOKEN estado=REINTENTABLE fase=TOKEN http=-"),out.getAll());
        noExpone(out);
        assertEquals(before,snapshot());
    }

    @Test void rechazosAntesDeLaRedYSinCambios(CapturedOutput out){
        var before=snapshot();
        var fake=new GraphMailTest.Fake();
        var cases=new LinkedHashMap<String,List<String>>();
        cases.put("ENVIO sin destinatario",props("ENVIO",null));
        cases.put("destinatario de otro dominio",props("ENVIO","alguien@example.com"));
        cases.put("destinatario invalido",props("ENVIO","no es correo"));
        cases.put("TOKEN con destinatario",props("TOKEN",TO));
        cases.put("modo en minusculas",props("token",null));
        cases.put("modo desconocido",props("OTRO",null));
        cases.put("con tareas programadas",with(props("TOKEN",null),"siger.scheduling","true"));
        cases.put("con el correo en modo GRAPH",with(props("TOKEN",null),"siger.mail.mode","GRAPH"));
        cases.put("ENVIO sin dominios permitidos",with(props("ENVIO",TO),"siger.mail.allowed-domains",""));
        cases.put("tenant invalido",with(props("TOKEN",null),"siger.mail.graph.tenant","no-guid"));
        cases.put("sin secreto",with(props("TOKEN",null),"siger.mail.graph.secret",""));
        cases.put("secreto invalido",with(props("TOKEN",null),"siger.mail.graph.secret","corto"));
        for(var c:cases.entrySet()){
            assertThrows(Exception.class,()->run(app(fake),c.getValue()),c.getKey()+": se rechaza");
            assertTrue(fake.calls.isEmpty(),c.getKey()+": el rechazo ocurre antes de contactar Graph");
        }
        // Con servidor web el contexto no arranca.
        var web=with(props("TOKEN",null),"server.port","0");
        var refused=assertThrows(Exception.class,()->run(app(fake).web(WebApplicationType.SERVLET),web));
        assertTrue(root(refused).getMessage().contains("sin servidor web"),root(refused).getMessage());
        assertTrue(fake.calls.isEmpty(),"ningun rechazo contacta Graph");
        assertFalse(out.getAll().contains(GraphCheck.MARKER),"un rechazo no informa resultado de Graph");
        noExpone(out);
        assertEquals(before,snapshot());
    }

    @Test void sinModoNoExisteLaPrueba(CapturedOutput out){
        var before=snapshot();
        var fake=token(new GraphMailTest.Fake(),null).status(202,null);
        var ctx=run(app(fake),props(null,null));
        try{
            assertTrue(ctx.getBeanProvider(GraphCheck.Runner.class).stream().findAny().isEmpty());
            assertTrue(fake.calls.isEmpty(),"sin siger.mail.check.mode no se contacta Graph");
        }finally{ctx.close();}
        assertTrue(service.getBeanProvider(GraphCheck.Runner.class).stream().findAny().isEmpty());
        assertFalse(out.getAll().contains(GraphCheck.MARKER));
        assertEquals(before,snapshot());
    }

    @Test void instantaneaDetectaUnCambio(){
        // Control: la instantanea completa si distingue un cambio real en la cola de avisos.
        var before=snapshot();
        db.update("UPDATE mail_outbox SET attempts=attempts+1");
        try{assertNotEquals(before,snapshot());}finally{db.update("UPDATE mail_outbox SET attempts=attempts-1");}
        assertEquals(before,snapshot());
    }

    /**
     * D17 (H04c): con un archivo de liberacion por analisis en origen vigente (aunque la propiedad llegue), la prueba de
     * Graph no audita el modo ni libera el original del Formulario pendiente: la base no cambia. Control: un proceso
     * comun con el mismo archivo si lo libera, asi que la igualdad no es casual.
     */
    @Test void liberacionVigenteNoCambiaLaBase(CapturedOutput out)throws Exception{
        long scope=n(db.one("SELECT id FROM org_scope WHERE code='GCU-1'"),"id");
        long pending=OriginReleaseTest.pendingFormularioOriginal(db,dir.resolve("data").resolve("documents"),scope);
        Path file=OriginReleaseTest.activeFile(dir);
        var before=snapshot();
        var fake=token(new GraphMailTest.Fake(),"abcdef01-2345-4678-9abc-def012345678");
        var ctx=run(app(fake),with(props("TOKEN",null),"siger.documents.origin-release.file",file.toString()));
        assertFalse(ctx.isActive());
        assertEquals(before,snapshot(),"ninguna tabla cambia con el modo vigente");
        assertTrue(b(db.one("SELECT quarantined FROM document_item WHERE id=?",pending),"quarantined"));
        noExpone(out);
        var control=run(app(null),with(props(null,null),"siger.documents.origin-release.file",file.toString()));
        try{
            assertFalse(b(db.one("SELECT quarantined FROM document_item WHERE id=?",pending),"quarantined"),"control: un proceso comun si libera");
            assertNotEquals(before,snapshot());
        }finally{control.close();}
    }

    /** Contenido completo de cada tabla del esquema (todas las filas y columnas, ordenadas), no solo los conteos. */
    static Map<String,List<String>> snapshot(){
        var out=snapshot(db);
        assertFalse(out.get("mail_outbox").isEmpty(),"la cola tiene la fila sembrada");
        return out;
    }
    static Map<String,List<String>> snapshot(Db db){
        var out=new TreeMap<String,List<String>>();
        var tables=db.rows("SELECT LOWER(table_name) AS t FROM information_schema.tables WHERE LOWER(table_schema)=LOWER(SCHEMA()) AND table_type='BASE TABLE' ORDER BY 1");
        assertTrue(tables.size()>40,"tablas del esquema: "+tables.size());
        for(var row:tables){
            String table=Db.s(row,"t");var rows=new ArrayList<String>();
            for(var r:db.rows("SELECT * FROM "+table)){
                var cells=new TreeMap<String,String>();
                r.forEach((k,v)->cells.put(k,v instanceof byte[] b?Base64.getEncoder().encodeToString(b):String.valueOf(v)));
                rows.add(cells.toString());
            }
            Collections.sort(rows);out.put(table,rows);
        }
        return out;
    }
}
