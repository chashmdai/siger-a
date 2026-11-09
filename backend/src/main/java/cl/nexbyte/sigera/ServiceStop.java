package cl.nexbyte.sigera;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Parada ordenada en Windows (servicio o Stop.ps1): Java no recibe una senal de terminacion utilizable desde el
 * administrador de servicios, asi que el lanzador crea el archivo siger.stop-file (run\stop.request). Al detectarlo
 * se cierra el contexto de Spring: el servidor deja de aceptar solicitudes, los trabajadores (Formulario, correo)
 * terminan su ciclo en curso y liberan sus leases, y el proceso termina con codigo 0. Sin la propiedad no hace nada.
 */
@Component
@ConditionalOnExpression("'${siger.stop-file:}'!=''")
class ServiceStop {
    private static final Logger LOG=LoggerFactory.getLogger(ServiceStop.class);
    private final Path file;private final ConfigurableApplicationContext context;private final boolean exit;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"siger-parada");t.setDaemon(true);return t;});

    ServiceStop(Environment env,ConfigurableApplicationContext context){
        this.file=Path.of(env.getRequiredProperty("siger.stop-file"));this.context=context;
        this.exit=env.getProperty("siger.stop-file-exit",Boolean.class,true); // false solo en pruebas dentro de la JVM de Maven
    }

    @EventListener(ApplicationReadyEvent.class) void ready(){
        // Una solicitud previa a este arranque (proceso anterior terminado) no detiene la instancia nueva.
        try{Files.deleteIfExists(file);}catch(Exception e){LOG.warn("No se pudo limpiar la solicitud de parada previa ({}).",e.getClass().getSimpleName());}
        executor.scheduleWithFixedDelay(this::check,2,2,TimeUnit.SECONDS);
    }
    private void check(){
        if(!Files.exists(file))return;
        executor.shutdown();
        LOG.info("Solicitud de parada ordenada recibida.");
        var t=new Thread(()->{int code=SpringApplication.exit(context,()->0);try{Files.deleteIfExists(file);}catch(Exception ignored){}if(exit)System.exit(code);},"siger-salida");
        t.setDaemon(false);t.start();
    }
}
