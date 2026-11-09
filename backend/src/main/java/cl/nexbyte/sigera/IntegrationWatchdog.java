package cl.nexbyte.sigera;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vigilancia de latidos de las integraciones permanentes, independiente de sus propios hilos: si el consumidor
 * automatico o el envio de correo dejan de registrar el ciclo prometido (hilo caido, ciclo colgado o trabajador
 * detenido con el servicio activo), lo informa una vez como ERROR en el log del servicio y otra al recuperarse.
 * /api/health (SIN_LATIDO), Estado.ps1 y Auditoria y operacion muestran el mismo estado. Solo en el proceso de
 * servicio (tareas programadas); no reinicia ni corrige nada por su cuenta.
 */
@Component
class IntegrationWatchdog {
    private static final Logger LOG=LoggerFactory.getLogger(IntegrationWatchdog.class);
    private final Integrations integrations;
    private final Set<String> stale=new HashSet<>();

    IntegrationWatchdog(Integrations integrations){this.integrations=integrations;}

    @Scheduled(initialDelay=120_000,fixedDelay=60_000)
    void scheduled(){check(System.currentTimeMillis());}

    /** Integraciones con latido vencido en este momento. Visible para pruebas dirigidas. */
    synchronized Set<String> check(long now){
        var current=new TreeSet<String>();
        for(var name:List.of(Integrations.FORMULARIO,Integrations.CORREO)){
            boolean expired;
            try{expired=integrations.stale(name,now);}
            catch(RuntimeException e){LOG.warn("No se pudo verificar el latido de {} ({}).",name,e.getClass().getSimpleName());continue;}
            if(expired){
                current.add(name);
                if(stale.add(name))LOG.error("Latido vencido de {}: el trabajador no registro su ciclo dentro del plazo. Revisa logs\\siger.log y Auditoria y operacion; reinicia el servicio si no se recupera.",name);
            }else if(stale.remove(name))LOG.info("Latido de {} recuperado.",name);
        }
        return current;
    }
}
