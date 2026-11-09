package cl.nexbyte.sigera;

import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Envio permanente del outbox por Graph (solo con siger.mail.mode=GRAPH). Cada ciclo: recupera intentos vencidos como
 * AMBIGUO, toma un lote acotado y envia uno a uno con intento durable previo a la red. Un fallo de token detiene el
 * lote (no se consumen intentos de mensajes). Respeta la pausa auditada de TI y registra su latido.
 */
final class MailWorker {
    private static final Logger LOG=LoggerFactory.getLogger(MailWorker.class);
    private final MailOutbox outbox;private final GraphMail graph;private final Integrations integrations;
    private final long intervalMs;private final int batch;private final long stopWaitMs;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"siger-correo");t.setDaemon(true);return t;});
    private volatile boolean stopping;private int failures;private String lastState="";
    /** Cota de un ciclo (lote de hasta 10 envios, cada uno con token y sendMail de 40 s como maximo): pasado el plazo el latido vence. */
    static final long MAX_CYCLE_MS=900_000L;

    MailWorker(MailOutbox outbox,GraphMail graph,Integrations integrations,long intervalMs,int batch,long stopWaitMs){
        if(intervalMs<1_000||batch<1||batch>10)throw new IllegalStateException("Intervalo o lote del correo fuera de rango.");
        this.outbox=outbox;this.graph=graph;this.integrations=integrations;this.intervalMs=intervalMs;this.batch=batch;this.stopWaitMs=stopWaitMs;
    }

    void start(){
        integrations.state(Integrations.CORREO,"INICIANDO",null,System.currentTimeMillis()+MAX_CYCLE_MS);
        LOG.info("Envio de correo Graph iniciado (cada {} ms, lote {}).",intervalMs,batch);
        executor.schedule(this::loop,0,TimeUnit.MILLISECONDS);
    }
    private void loop(){
        if(stopping)return;
        long delay;
        try{delay=tick();}catch(Throwable t){delay=300_000L;LOG.error("Ciclo de correo interrumpido ({}).",t.getClass().getSimpleName());}
        if(!stopping)try{executor.schedule(this::loop,delay,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}
    }

    /** Un ciclo y la espera siguiente en milisegundos. Visible para pruebas dirigidas. */
    long tick(){
        String name=Integrations.CORREO;
        integrations.started(name,System.currentTimeMillis()+MAX_CYCLE_MS);
        try{
            if(integrations.paused(name)){integrations.record(name,"PAUSADO",null,true,0,null,System.currentTimeMillis()+intervalMs);changed("PAUSADO");return intervalMs;}
            int recovered=outbox.recover();
            if(recovered>0)LOG.warn("{} correo(s) quedaron AMBIGUO tras un intento interrumpido; requieren decision de TI.",recovered);
            var due=outbox.due(batch);String error=null;int sent=0;
            for(long id:due){
                if(stopping)break;
                var claim=outbox.reserve(id);if(claim==null)continue;
                var result=graph.send(outbox.message(claim));
                outbox.finish(claim,result);
                if(result.state().equals("ACEPTADO"))sent++;
                if(result.phase().equals("TOKEN")){error="TOKEN_HTTP_"+(result.http()==null?"SIN_RESPUESTA":result.http());break;}
                if(result.state().equals("AMBIGUO"))error="ENVIO_AMBIGUO";
            }
            if(error!=null){
                failures++;long delay=Math.min(300_000L,10_000L<<Math.min(failures-1,5));
                integrations.record(name,"DEGRADADO",null,false,failures,error,System.currentTimeMillis()+delay);changed("DEGRADADO");
                LOG.warn("Ciclo de correo con incidente: {} (aceptados={}).",error,sent);
                return delay;
            }
            failures=0;
            boolean more=due.size()==batch;long delay=more?1_000L:intervalMs;String state=more?"ENVIANDO":"OPERATIVO";
            integrations.record(name,state,null,true,0,null,System.currentTimeMillis()+delay);changed(state);
            return delay;
        }catch(RuntimeException e){
            failures++;long delay=Math.min(300_000L,10_000L<<Math.min(failures-1,5));
            integrations.record(name,"DEGRADADO",null,false,failures,"CICLO_"+e.getClass().getSimpleName(),System.currentTimeMillis()+delay);changed("DEGRADADO");
            return delay;
        }
    }
    private void changed(String state){if(!state.equals(lastState))LOG.info("Correo Graph: {}.",state);lastState=state;}

    void stop(){
        stopping=true;executor.shutdown();
        try{if(!executor.awaitTermination(stopWaitMs,TimeUnit.MILLISECONDS)){executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);}}
        catch(InterruptedException e){Thread.currentThread().interrupt();executor.shutdownNow();}
        integrations.state(Integrations.CORREO,"DETENIDO",null,null);
        LOG.info("Envio de correo Graph detenido.");
    }

    /**
     * Beans de red del correo: solo con siger.mail.mode=GRAPH y en el proceso de servicio (siger.scheduling=true); el
     * ciclo manual del Formulario (scheduling=false) encola avisos pero no envia. Configuracion invalida detiene el arranque.
     */
    @Configuration
    @ConditionalOnExpression("'${siger.mail.mode:OFF}'=='GRAPH' and '${siger.scheduling:true}'=='true'")
    static class Config {
        @Bean GraphMail graphMail(Environment env){
            return new GraphMail(env.getProperty("siger.mail.graph.tenant"),env.getProperty("siger.mail.graph.client"),
                env.getProperty("siger.mail.graph.sender"),env.getProperty("siger.mail.graph.secret"));
        }
        @Bean(destroyMethod="stop") MailWorker mailWorker(MailOutbox outbox,GraphMail graph,Integrations integrations,Environment env){
            return new MailWorker(outbox,graph,integrations,env.getProperty("siger.mail.interval-ms",Long.class,15_000L),
                env.getProperty("siger.mail.batch",Integer.class,10),env.getProperty("siger.mail.stop-wait-ms",Long.class,45_000L));
        }
        @Bean MailStart mailStart(MailWorker worker){return new MailStart(worker);}
    }
    static final class MailStart {
        private final MailWorker worker;MailStart(MailWorker worker){this.worker=worker;}
        @EventListener(ApplicationReadyEvent.class) void ready(){worker.start();}
    }
}
