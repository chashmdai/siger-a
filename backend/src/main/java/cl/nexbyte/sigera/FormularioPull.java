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
 * Activacion explicita del consumidor del Formulario. Sin siger.formulario.pull.enabled=true y
 * siger.formulario.pull.run-once=true no existe ningun bean de red: no se lee la credencial ni se contacta el origen.
 * Con ambas, se ejecuta UN ciclo en un proceso sin servidor web ni tareas programadas y luego se cierra el contexto;
 * no hay planificador, endpoint ni comando de funcionario que lo dispare. Codigo de salida distinto de cero si el
 * ciclo no termino limpio.
 */
@Configuration
@ConditionalOnProperty(name={"siger.formulario.pull.enabled","siger.formulario.pull.run-once"},havingValue="true")
class FormularioPull {
    private static final Logger LOG=LoggerFactory.getLogger(FormularioPull.class);

    @Bean FormularioConsumer formularioConsumer(Environment env,FormularioReception reception,Documents documents,FormularioKeys keys,Db db){
        // Esquema V8 presente antes de contactar el origen (el proceso manual no migra).
        try{db.count("SELECT COUNT(*) FROM formulario_consumer_lock WHERE id=1");db.count("SELECT COUNT(*) FROM formulario_reception WHERE id=0");}
        catch(RuntimeException e){throw new IllegalStateException("El esquema no tiene la migracion V8 del consumidor del Formulario.");}
        if(keys.keys().isEmpty())throw new IllegalStateException("El consumidor requiere claves confiables del Formulario (siger.formulario-trusted-keys).");
        String prefix=env.getProperty("siger.folio-prefix","");
        if(!prefix.matches(FormularioAck.PREFIX))throw new IllegalStateException("El prefijo de folio COM no es compatible con el comprobante del Formulario ([A-Z][A-Z0-9]{1,15}).");
        var scopes=db.rows("SELECT id FROM org_scope WHERE code=? AND enabled=TRUE",required(env,"siger.formulario.pull.scope"));
        if(scopes.size()!=1)throw new IllegalStateException("Ambito receptor del Formulario inexistente o deshabilitado.");
        var ack=FormularioAck.load(required(env,"siger.formulario.ack.key-id"),Path.of(required(env,"siger.formulario.ack.private-key-file")),Path.of(required(env,"siger.formulario.ack.public-key-file")));
        String ca=env.getProperty("siger.formulario.pull.ca-file","");
        var client=new FormularioClient(URI.create(required(env,"siger.formulario.pull.origin")),token(env),ca.isBlank()?null:Path.of(ca),
            env.getProperty("siger.formulario.pull.loopback-http",Boolean.class,false));
        return new FormularioConsumer(client,reception,documents,keys,ack,Db.n(scopes.getFirst(),"id"));
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

    @Bean FormularioRunOnce formularioRunOnce(FormularioConsumer consumer,ConfigurableApplicationContext context,Environment env){
        if(context instanceof WebServerApplicationContext)throw new IllegalStateException("El ciclo manual del Formulario se ejecuta sin servidor web (spring.main.web-application-type=none).");
        if(env.getProperty("siger.scheduling",Boolean.class,true))throw new IllegalStateException("El ciclo manual del Formulario requiere siger.scheduling=false.");
        return new FormularioRunOnce(consumer,context);
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
