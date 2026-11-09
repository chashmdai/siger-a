package cl.nexbyte.sigera;

import java.util.UUID;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumidor automatico permanente del Formulario dentro del servicio (siger.formulario.pull.automatic=true).
 * Reutiliza el mismo ciclo que el modo manual (FormularioConsumer): concilia ACK pendientes antes de cada reclamo,
 * valida, custodia, registra en un COMMIT y confirma despues. Agrega:
 * - exclusividad: un lease SQL propio del proceso, renovado por un latido y conservado entre ciclos; otro proceso de
 *   SIGER (incluido el ciclo manual) queda OCUPADO. Si el lease se pierde, el ciclo se detiene sin reclamar ni ACK;
 * - espera: sondeo normal, drenaje inmediato tras una entrega y espera exponencial 10 s a 5 min ante incidentes;
 * - pausa auditada de TI (integration_control) respetada en cada ciclo, liberando el lease;
 * - latido en integration_status y detencion ordenada que deja terminar el ciclo en curso.
 * Tras un reinicio no hay estado en memoria que recuperar: la autoridad es formulario_reception y el lease vence solo.
 */
final class FormularioAutomatic {
    private static final Logger LOG=LoggerFactory.getLogger(FormularioAutomatic.class);
    static final long MIN_BACKOFF_MS=10_000L,MAX_BACKOFF_MS=300_000L,DRAIN_MS=1_000L;
    private final FormularioConsumer consumer;private final FormularioReception reception;private final Integrations integrations;
    private final long pollMs,leaseMs,stopWaitMs;
    final String holder=UUID.randomUUID().toString();
    private final ScheduledThreadPoolExecutor executor;
    private volatile boolean stopping;private volatile boolean leaseHeld;
    private int failures;private String lastState="";

    FormularioAutomatic(FormularioConsumer consumer,FormularioReception reception,Integrations integrations,long pollMs,long leaseMs,long stopWaitMs){
        if(pollMs<1_000||leaseMs<60_000||stopWaitMs<1_000)throw new IllegalStateException("Intervalos del consumidor automatico fuera de rango.");
        this.consumer=consumer;this.reception=reception;this.integrations=integrations;this.pollMs=pollMs;this.leaseMs=leaseMs;this.stopWaitMs=stopWaitMs;
        this.executor=new ScheduledThreadPoolExecutor(2,r->{var t=new Thread(r,"siger-formulario");t.setDaemon(true);return t;});
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    void start(){
        integrations.state(Integrations.FORMULARIO,"INICIANDO",holder);
        LOG.info("Consumidor automatico del Formulario iniciado (sondeo {} ms).",pollMs);
        // El latido renueva el lease mientras el proceso lo conserva, tambien durante una descarga larga.
        executor.scheduleWithFixedDelay(this::heartbeat,leaseMs/4,leaseMs/4,TimeUnit.MILLISECONDS);
        executor.schedule(this::loop,0,TimeUnit.MILLISECONDS);
    }

    private void loop(){
        if(stopping)return;
        long delay;
        try{delay=tick();}catch(Throwable t){delay=MAX_BACKOFF_MS;LOG.error("Ciclo automatico del Formulario interrumpido ({}).",t.getClass().getSimpleName());}
        if(!stopping)try{executor.schedule(this::loop,delay,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}
    }

    /** Un ciclo y la espera siguiente en milisegundos. Visible para pruebas dirigidas. */
    long tick(){
        String name=Integrations.FORMULARIO;
        try{
            if(integrations.paused(name)){
                releaseLease();failures=0;
                integrations.record(name,"PAUSADO",holder,true,0,null,System.currentTimeMillis()+pollMs);
                changed("PAUSADO");return pollMs;
            }
        }catch(RuntimeException e){return backoff(name,"BASE_NO_DISPONIBLE");}
        FormularioConsumer.Cycle c;
        leaseHeld=true; // el latido renueva tambien durante este ciclo; si no se obtiene el lease se corrige abajo
        try{c=consumer.run(holder,leaseMs,false);}
        catch(RuntimeException e){leaseHeld=false;return backoff(name,"CICLO_"+e.getClass().getSimpleName());}
        if(c.busy()){
            leaseHeld=false;failures=0;
            integrations.record(name,"OCUPADO",holder,false,0,"OTRO_PROCESO_CON_LEASE",System.currentTimeMillis()+pollMs);
            changed("OCUPADO");return pollMs;
        }
        leaseHeld=!c.incidents().contains("EXCLUSION_PERDIDA");
        if(!c.clean()){
            LOG.warn("Ciclo automatico del Formulario con incidentes: {} (reclamadas={} registradas={} confirmadas={}).",c.incidents(),c.claimed(),c.registered(),c.confirmed());
            return backoff(name,c.incidents().getFirst());
        }
        failures=0;
        if(c.claimed()>0||c.reconciled()>0)
            LOG.info("Ciclo automatico del Formulario: conciliadas={} reclamadas={} registradas={} confirmadas={}.",c.reconciled(),c.claimed(),c.registered(),c.confirmed());
        long delay=c.claimed()>0?DRAIN_MS:pollMs;
        String state=c.claimed()>0?"RECIBIENDO":"ESPERANDO_ENTREGAS";
        integrations.record(name,state,holder,true,0,null,System.currentTimeMillis()+delay);
        changed(state);
        return delay;
    }
    private long backoff(String name,String error){
        failures++;
        long delay=Math.min(MAX_BACKOFF_MS,MIN_BACKOFF_MS<<Math.min(failures-1,5));
        integrations.record(name,"DEGRADADO",holder,false,failures,error,System.currentTimeMillis()+delay);
        changed("DEGRADADO");
        return delay;
    }
    private void changed(String state){
        if(!state.equals(lastState))LOG.info("Consumidor automatico del Formulario: {}.",state);
        lastState=state;
    }

    private void heartbeat(){
        if(stopping||!leaseHeld)return;
        try{if(!reception.renew(holder,System.currentTimeMillis()+leaseMs)){leaseHeld=false;LOG.warn("Lease del consumidor del Formulario perdido; no se reclama ni confirma hasta recuperarlo.");}}
        catch(RuntimeException e){LOG.warn("Renovacion del lease del Formulario fallida ({}).",e.getClass().getSimpleName());}
    }
    private void releaseLease(){
        if(!leaseHeld)return;
        try{reception.release(holder);}catch(RuntimeException ignored){}
        leaseHeld=false;
    }

    /** Detencion ordenada: no inicia ciclos nuevos, espera el ciclo en curso y libera el lease. */
    void stop(){
        stopping=true;
        executor.shutdown();
        try{
            if(!executor.awaitTermination(stopWaitMs,TimeUnit.MILLISECONDS)){
                LOG.warn("El ciclo del Formulario no termino en {} ms; se interrumpe (la entrega sin COMMIT se reintenta).",stopWaitMs);
                executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();executor.shutdownNow();}
        try{reception.release(holder);}catch(RuntimeException ignored){}
        leaseHeld=false;
        integrations.state(Integrations.FORMULARIO,"DETENIDO",null);
        LOG.info("Consumidor automatico del Formulario detenido.");
    }
}
