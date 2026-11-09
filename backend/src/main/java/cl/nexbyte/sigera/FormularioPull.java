package cl.nexbyte.sigera;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Activacion explicita del consumidor del Formulario. Sin siger.formulario.pull.enabled=true no existe ningun bean de
 * red: no se lee la credencial ni se contacta el origen. Con enabled=true se elige exactamente un modo:
 * - run-once=true: UN ciclo en un proceso sin servidor web ni tareas programadas, que luego cierra el contexto
 *   (scripts\Invoke-FormularioPull.ps1); codigo de salida distinto de cero si el ciclo no termino limpio;
 * - automatic=true: consumidor permanente dentro del servicio (FormularioAutomatic), que exige ademas
 *   single-consumer-confirmed=true: el operador declara que ningun otro sistema (SMR) consume la misma cola.
 * En ningun modo hay endpoint ni comando de funcionario que reclame entregas.
 */
@Configuration
@ConditionalOnProperty(name="siger.formulario.pull.enabled",havingValue="true")
class FormularioPull {
    private static final Logger LOG=LoggerFactory.getLogger(FormularioPull.class);

    @Bean FormularioConsumer formularioConsumer(Environment env,FormularioReception reception,Documents documents,FormularioKeys keys,Db db){
        boolean once=env.getProperty("siger.formulario.pull.run-once",Boolean.class,false),automatic=env.getProperty("siger.formulario.pull.automatic",Boolean.class,false),
            probe=env.getProperty("siger.formulario.pull.probe",Boolean.class,false);
        if((once?1:0)+(automatic?1:0)+(probe?1:0)!=1)throw new IllegalStateException("Consumidor del Formulario: elige exactamente un modo (run-once, automatic o probe).");
        // Esquema V8 presente antes de contactar el origen (el proceso manual no migra).
        try{db.count("SELECT COUNT(*) FROM formulario_consumer_lock WHERE id=1");db.count("SELECT COUNT(*) FROM formulario_reception WHERE id=0");}
        catch(RuntimeException e){throw new IllegalStateException("El esquema no tiene la migracion V8 del consumidor del Formulario.");}
        if(keys.keys().isEmpty())throw new IllegalStateException("El consumidor requiere claves confiables del Formulario (siger.formulario-trusted-keys).");
        String prefix=env.getProperty("siger.folio-prefix","");
        if(!prefix.matches(FormularioAck.PREFIX))throw new IllegalStateException("El prefijo de folio COM no es compatible con el comprobante del Formulario ([A-Z][A-Z0-9]{1,15}).");
        var scopes=db.rows("SELECT id FROM org_scope WHERE code=? AND enabled=TRUE",required(env,"siger.formulario.pull.scope"));
        if(scopes.size()!=1)throw new IllegalStateException("Ambito receptor del Formulario inexistente o deshabilitado.");
        var ack=ackKeys(env);
        String ca=env.getProperty("siger.formulario.pull.ca-file","");
        var client=new FormularioClient(URI.create(required(env,"siger.formulario.pull.origin")),token(env),ca.isBlank()?null:Path.of(ca),
            env.getProperty("siger.formulario.pull.loopback-http",Boolean.class,false));
        return new FormularioConsumer(client,reception,documents,keys,ack,Db.n(scopes.getFirst(),"id"));
    }
    /** Par ACK desde variables del proceso (PEM descifrado por el lanzador desde DPAPI) o desde archivos privados. */
    private static FormularioAck ackKeys(Environment env){
        String keyId=required(env,"siger.formulario.ack.key-id"),privatePem=env.getProperty("siger.formulario.ack.private-key","");
        if(!privatePem.isBlank())return FormularioAck.fromPem(keyId,privatePem,required(env,"siger.formulario.ack.public-key"));
        return FormularioAck.load(keyId,Path.of(required(env,"siger.formulario.ack.private-key-file")),Path.of(required(env,"siger.formulario.ack.public-key-file")));
    }
    private static String required(Environment env,String key){
        String v=env.getProperty(key,"");if(v.isBlank())throw new IllegalStateException("Falta configuracion del consumidor del Formulario: "+key);return v;
    }
    /** Credencial desde variable de proceso (preferida) o archivo privado; nunca desde argumentos ni el repositorio. */
    private static String token(Environment env){
        String value=env.getProperty("siger.formulario.pull.token","");
        if(value.isBlank()){
            String file=required(env,"siger.formulario.pull.token-file");
            try{if(Files.size(Path.of(file))>1024)throw new IllegalStateException();value=Files.readString(Path.of(file),StandardCharsets.US_ASCII).strip();}
            catch(Exception e){throw new IllegalStateException("Credencial del Formulario ilegible.");}
        }
        return value;
    }

    @Bean @ConditionalOnProperty(name="siger.formulario.pull.run-once",havingValue="true")
    FormularioRunOnce formularioRunOnce(FormularioConsumer consumer,ConfigurableApplicationContext context,Environment env){
        if(context instanceof WebServerApplicationContext)throw new IllegalStateException("El ciclo manual del Formulario se ejecuta sin servidor web (spring.main.web-application-type=none).");
        if(env.getProperty("siger.scheduling",Boolean.class,true))throw new IllegalStateException("El ciclo manual del Formulario requiere siger.scheduling=false.");
        return new FormularioRunOnce(consumer,context);
    }

    @Bean(destroyMethod="stop") @ConditionalOnProperty(name="siger.formulario.pull.automatic",havingValue="true")
    FormularioAutomatic formularioAutomatic(FormularioConsumer consumer,FormularioReception reception,Integrations integrations,Environment env){
        if(!env.getProperty("siger.formulario.pull.single-consumer-confirmed",Boolean.class,false))
            throw new IllegalStateException("El consumidor automatico exige siger.formulario.pull.single-consumer-confirmed=true: confirma que el consumidor de SMR esta detenido.");
        return new FormularioAutomatic(consumer,reception,integrations,
            env.getProperty("siger.formulario.pull.poll-ms",Long.class,15_000L),env.getProperty("siger.formulario.pull.lease-ms",Long.class,120_000L),
            env.getProperty("siger.formulario.pull.stop-wait-ms",Long.class,60_000L));
    }
    /** Arranca el ciclo permanente cuando la aplicacion esta lista (migraciones y semillas ya aplicadas). */
    @Bean @ConditionalOnProperty(name="siger.formulario.pull.automatic",havingValue="true")
    FormularioAutomaticStart formularioAutomaticStart(FormularioAutomatic automatic){return new FormularioAutomaticStart(automatic);}
    static final class FormularioAutomaticStart {
        private final FormularioAutomatic automatic;
        FormularioAutomaticStart(FormularioAutomatic automatic){this.automatic=automatic;}
        @EventListener(ApplicationReadyEvent.class) void ready(){automatic.start();}
    }

    /**
     * Comprobacion previa a la activacion (siger.formulario.pull.probe=true, proceso sin web ni tareas): la creacion del
     * consumidor ya valido esquema, claves de custodia, identidad ACK, prefijo de folio, ambito receptor, CA y Bearer;
     * aqui solo se hace la sonda HEAD que no reclama ni confirma entregas. Codigo de salida distinto de cero si falla.
     */
    @Bean @ConditionalOnProperty(name="siger.formulario.pull.probe",havingValue="true")
    FormularioProbe formularioProbe(FormularioConsumer consumer,FormularioReception reception,ConfigurableApplicationContext context,Environment env){
        if(context instanceof WebServerApplicationContext||env.getProperty("siger.scheduling",Boolean.class,true))throw new IllegalStateException("La sonda del Formulario se ejecuta sin servidor web ni tareas programadas.");
        return new FormularioProbe(consumer,reception,context);
    }
    static final class FormularioProbe {
        private final FormularioConsumer consumer;private final FormularioReception reception;private final ConfigurableApplicationContext context;
        FormularioProbe(FormularioConsumer consumer,FormularioReception reception,ConfigurableApplicationContext context){this.consumer=consumer;this.reception=reception;this.context=context;}
        @EventListener(ApplicationReadyEvent.class) void ready(){
            String result;boolean ok;
            try{var p=consumer.probe();ok=p.ready();result="sinCredencial="+p.absent()+" credencialInvalida="+p.invalid()+" credencialConfigurada="+p.valid();}
            catch(FormularioClient.Failure f){ok=false;result="error="+f.code;}
            int pending=reception.pendingAcks(20).size();
            LOG.info("Sonda del Formulario: {} acksPendientes={} resultado={}",result,pending,ok?"LISTO":"NO_LISTO");
            context.close();
            if(!ok)throw new IllegalStateException("Sonda del Formulario no acreditada: "+result);
        }
    }

    static final class FormularioRunOnce {
        private final FormularioConsumer consumer;private final ConfigurableApplicationContext context;
        FormularioRunOnce(FormularioConsumer consumer,ConfigurableApplicationContext context){this.consumer=consumer;this.context=context;}
        @EventListener(ApplicationReadyEvent.class) void ready(){
            var cycle=consumer.run();
            LOG.info("Ciclo del Formulario: conciliadas={} reclamadas={} registradas={} confirmadas={} incidentes={}",
                cycle.reconciled(),cycle.claimed(),cycle.registered(),cycle.confirmed(),cycle.incidents());
            context.close();
            if(!cycle.clean())throw new IllegalStateException("Ciclo del Formulario con incidentes: "+cycle.incidents());
        }
    }
}
