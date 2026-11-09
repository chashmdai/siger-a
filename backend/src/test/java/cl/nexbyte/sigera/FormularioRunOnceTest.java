package cl.nexbyte.sigera;

import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/**
 * La aplicacion completa como la lanza el ciclo manual (sin servidor web, siger.scheduling=false, run-once):
 * ejecuta un ciclo contra el productor SINTETICO local y cierra su contexto; con incidentes termina en error.
 * No es un PULL real.
 */
class FormularioRunOnceTest {
    static final String DB="jdbc:h2:mem:sigera_run_once;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test void unCicloEnProcesoSinWebYCierreDelContexto()throws Exception{
        var k=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path dir=Files.createDirectories(Path.of("target","run-once-"+UUID.randomUUID()));
        Path priv=dir.resolve("ack-private.pem"),pub=dir.resolve("ack-public.pem"),token=dir.resolve("token.txt");
        Files.writeString(priv,"-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder().encodeToString(k.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder().encodeToString(k.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        Files.writeString(token,FormularioConsumerTest.TOKEN);
        try(var producer=new FormularioConsumerTest.Producer(k.getPublic())){
            String document=FormularioConsumerTest.nna10();
            producer.offer(document,Map.of());
            // Como en la VM: el esquema inicializado y la instancia de servicio activa mientras corre el ciclo manual.
            // (Mantenerla abierta evita ademas reabrir la base H2 en memoria, que invalida sus restricciones CHECK.)
            var service=run(app(),List.of("spring.datasource.url="+DB,"siger.data="+dir.resolve("data"),"siger.seed-demo=true"));
            try{
            assertEquals(0,producer.requests.get());
            assertTrue(service.getBeanProvider(FormularioConsumer.class).stream().findAny().isEmpty());
            var props=new ArrayList<>(List.of("spring.datasource.url="+DB,"siger.data="+dir.resolve("data"),
                "siger.scheduling=false","siger.formulario.pull.enabled=true","siger.formulario.pull.run-once=true",
                "siger.folio-prefix=COMA","siger.formulario-trusted-keys="+FormularioDocs.trustFile(),
                "siger.formulario.pull.origin="+producer.origin(),"siger.formulario.pull.loopback-http=true","siger.formulario.pull.token-file="+token,
                "siger.formulario.pull.scope=UPR-REQ","siger.formulario.ack.key-id=SIGER_A_PRUEBA",
                "siger.formulario.ack.private-key-file="+priv,"siger.formulario.ack.public-key-file="+pub));
            ConfigurableApplicationContext ctx=run(app(),props);
            assertFalse(ctx.isActive());
            assertEquals(1,producer.acks.size());
            try(var c=DriverManager.getConnection(DB,"sa","");var s=c.createStatement();
                var r=s.executeQuery("SELECT state FROM formulario_reception WHERE operation_id='"+FormularioConsumerTest.operation(document)+"'")){
                assertTrue(r.next());assertEquals("CONFIRMADA",r.getString(1));
            }
            // Con incidentes (credencial rechazada), el proceso termina con error y no registra nada.
            var wrong=dir.resolve("otra.txt");Files.writeString(wrong,"credencial-rechazada-0123456789abcdefghij");
            props.removeIf(p->p.startsWith("siger.formulario.pull.token-file="));props.add("siger.formulario.pull.token-file="+wrong);
            var failed=assertThrows(IllegalStateException.class,()->run(app(),props));
            assertTrue(failed.getMessage().contains("RECLAMO_HTTP_401"),failed.getMessage());
            // Con el servidor web activo no se ejecuta el ciclo: el contexto no arranca.
            var web=new ArrayList<>(props);web.add("server.port=0");
            var refused=assertThrows(Exception.class,()->run(app().web(WebApplicationType.SERVLET),web));
            Throwable cause=refused;while(cause.getCause()!=null)cause=cause.getCause();
            assertTrue(cause.getMessage().contains("sin servidor web"),cause.getMessage());
            assertEquals(1,producer.acks.size());
            }finally{service.close();}
        }
    }
    private static SpringApplicationBuilder app(){
        return new SpringApplicationBuilder(SigerApplication.class).web(WebApplicationType.NONE).profiles("test");
    }
    /** Argumentos de linea de comando, como el script de ciclo manual (precedencia sobre application.properties). */
    private static ConfigurableApplicationContext run(SpringApplicationBuilder app,List<String> props){
        return app.run(props.stream().map(p->"--"+p).toArray(String[]::new));
    }
}
