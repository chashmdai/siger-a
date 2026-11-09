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
 * La aplicacion completa como servicio permanente con el consumidor automatico del Formulario contra el productor
 * SINTETICO local: recibe en segundo plano, informa su estado en /api/health y se detiene de forma ordenada con el
 * archivo de parada (run\stop.request), liberando el lease. Sin confirmacion de consumidor unico no arranca.
 * No es un PULL real.
 */
class FormularioServiceTest {
    static final String DB="jdbc:h2:mem:sigera_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test void consumidorAutomaticoEnElServicioConSaludYParadaOrdenada()throws Exception{
        var k=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path dir=Files.createDirectories(Path.of("target","service-"+UUID.randomUUID()));
        Path priv=dir.resolve("ack-private.pem"),pub=dir.resolve("ack-public.pem"),token=dir.resolve("token.txt"),stop=dir.resolve("stop.request");
        Files.writeString(priv,"-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder().encodeToString(k.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder().encodeToString(k.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        Files.writeString(token,FormularioConsumerTest.TOKEN);
        try(var producer=new FormularioConsumerTest.Producer(k.getPublic())){
            String document=FormularioConsumerTest.nna10();
            var props=new ArrayList<>(List.of("spring.datasource.url="+DB,"siger.data="+dir.resolve("data"),"siger.seed-demo=true",
                "siger.formulario.pull.enabled=true","siger.formulario.pull.automatic=true","siger.formulario.pull.poll-ms=1000",
                "siger.folio-prefix=COMA","siger.formulario-trusted-keys="+FormularioDocs.trustFile(),
                "siger.formulario.pull.origin="+producer.origin(),"siger.formulario.pull.loopback-http=true","siger.formulario.pull.token-file="+token,
                "siger.formulario.pull.scope=UPR-REQ","siger.formulario.ack.key-id=SIGER_A_PRUEBA",
                "siger.formulario.ack.private-key-file="+priv,"siger.formulario.ack.public-key-file="+pub,
                "siger.stop-file="+stop,"siger.stop-file-exit=false"));
            // Esquema y semillas listos (como una instancia ya inicializada); mantenerla abierta evita ademas reabrir la
            // base H2 en memoria, que invalida sus restricciones CHECK.
            var base=run(List.of("spring.datasource.url="+DB,"siger.data="+dir.resolve("data"),"siger.seed-demo=true"));
            try{
            // Sin la declaracion de consumidor unico (SMR detenido) el servicio no arranca ni contacta el origen.
            Throwable refused=assertThrows(Exception.class,()->run(props));
            while(refused.getCause()!=null)refused=refused.getCause();
            assertTrue(refused.getMessage().contains("single-consumer-confirmed"),refused.getMessage());
            assertEquals(0,producer.requests.get());

            props.add("siger.formulario.pull.single-consumer-confirmed=true");
            producer.offer(document,Map.of());
            var ctx=run(props);
            try{
                long end=System.currentTimeMillis()+20_000;
                while(producer.acks.isEmpty()&&System.currentTimeMillis()<end)Thread.sleep(50);
                assertEquals(1,producer.acks.size());
                var health=ctx.getBean(Api.class).health().getBody();
                assertEquals("UP",health.get("status"));assertEquals("10",health.get("schema"));
                String state=((Map<?,?>)health.get("integrations")).get("formulario").toString();
                assertTrue(Set.of("RECIBIENDO","ESPERANDO_ENTREGAS").contains(state),state);
                assertEquals("DESACTIVADO",((Map<?,?>)health.get("integrations")).get("correo"));
                // Parada ordenada: el lanzador crea el archivo; el contexto se cierra y el lease queda libre.
                Files.writeString(stop,"");
                end=System.currentTimeMillis()+20_000;
                while(ctx.isActive()&&System.currentTimeMillis()<end)Thread.sleep(50);
                assertFalse(ctx.isActive());
                end=System.currentTimeMillis()+5_000;
                while(Files.exists(stop)&&System.currentTimeMillis()<end)Thread.sleep(50);
                assertFalse(Files.exists(stop));
            }finally{if(ctx.isActive())ctx.close();}
            try(var c=DriverManager.getConnection(DB,"sa","");var s=c.createStatement()){
                try(var r=s.executeQuery("SELECT state FROM formulario_reception WHERE operation_id='"+FormularioConsumerTest.operation(document)+"'")){assertTrue(r.next());assertEquals("CONFIRMADA",r.getString(1));}
                try(var r=s.executeQuery("SELECT holder FROM formulario_consumer_lock WHERE id=1")){assertTrue(r.next());assertNull(r.getString(1));}
                try(var r=s.executeQuery("SELECT state FROM integration_status WHERE name='FORMULARIO_PULL'")){assertTrue(r.next());assertEquals("DETENIDO",r.getString(1));}
                try(var r=s.executeQuery("SELECT COUNT(*) FROM communication WHERE external_id='"+FormularioConsumerTest.operation(document)+"'")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
            }
            }finally{base.close();}
        }
    }
    /** Argumentos de linea de comando (precedencia sobre application.properties), como el lanzador del servicio. */
    private static ConfigurableApplicationContext run(List<String> props){
        return new SpringApplicationBuilder(SigerApplication.class).web(WebApplicationType.NONE).profiles("test")
            .run(props.stream().map(p->"--"+p).toArray(String[]::new));
    }
}
