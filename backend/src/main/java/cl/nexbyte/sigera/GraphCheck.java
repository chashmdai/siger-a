package cl.nexbyte.sigera;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Prueba controlada de Microsoft Graph (0.6.0, H01; la lanza scripts\Test-GraphMail.ps1). Solo existe con
 * siger.mail.check.mode, en un proceso sin servidor web ni tareas programadas y sin siger.mail.mode=GRAPH:
 * - TOKEN: pide un token OAuth client_credentials (credencial valida y salida hacia login.microsoftonline.com) y no
 *   llama a sendMail;
 * - ENVIO: ademas envia UN correo de asunto y texto fijos al destinatario siger.mail.check.to, que debe pertenecer a
 *   siger.mail.allowed-domains (salida hacia graph.microsoft.com y permiso Mail.Send sobre el remitente).
 * No usa la cola de avisos (mail_outbox, mail_attempt), ni avisos, ni datos ciudadanos, y no escribe en la base.
 * Informa en una sola linea modo, estado, fase, HTTP y request-id (nunca destinatario, remitente, tenant, cliente,
 * secreto ni token), cierra el contexto y, si no queda acreditada, termina en error (codigo de salida distinto de cero).
 */
@Configuration
@ConditionalOnExpression("'${siger.mail.check.mode:}'!=''")
class GraphCheck {
    private static final Logger LOG=LoggerFactory.getLogger(GraphCheck.class);
    static final String SUBJECT="SIGER · Prueba controlada del correo";
    static final String TEXT="Prueba controlada del correo institucional de SIGER. Este mensaje no contiene datos de expedientes y no requiere respuesta.";
    static final String MARKER="Prueba de Graph:";

    /**
     * Valida todo antes de cualquier contacto de red: un rechazo aqui impide que el contexto arranque. El transporte
     * es el real salvo que exista un bean GraphMail.Connection (solo en las pruebas automatizadas, que nunca usan red).
     */
    @Bean Runner graphCheckRunner(Environment env,ConfigurableApplicationContext context,ObjectProvider<GraphMail.Connection> connection){
        if(context instanceof WebServerApplicationContext||env.getProperty("siger.scheduling",Boolean.class,true))
            throw new IllegalStateException("La prueba de Graph se ejecuta sin servidor web ni tareas programadas (scripts\\Test-GraphMail.ps1).");
        if(env.getProperty("siger.mail.mode","OFF").equalsIgnoreCase("GRAPH"))
            throw new IllegalStateException("La prueba de Graph no se ejecuta con siger.mail.mode=GRAPH: no usa la cola de avisos.");
        String mode=env.getProperty("siger.mail.check.mode","").strip(),to=env.getProperty("siger.mail.check.to","").strip();
        if(!mode.equals("TOKEN")&&!mode.equals("ENVIO"))throw new IllegalStateException("Modo de la prueba de Graph desconocido: se admiten TOKEN y ENVIO.");
        if(mode.equals("TOKEN")&&!to.isEmpty())throw new IllegalStateException("El modo TOKEN no lleva destinatario: no envia correo.");
        if(mode.equals("ENVIO")){
            var domains=new HashSet<String>();
            for(var d:env.getProperty("siger.mail.allowed-domains","").split(","))if(!d.isBlank())domains.add(d.strip().toLowerCase(Locale.ROOT));
            if(domains.isEmpty()||!domains.stream().allMatch(d->d.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")))
                throw new IllegalStateException("El modo ENVIO exige siger.mail.allowed-domains (dominios institucionales).");
            if(!to.matches(GraphMail.ADDRESS)||!domains.contains(to.substring(to.indexOf('@')+1).toLowerCase(Locale.ROOT)))
                throw new IllegalStateException("El modo ENVIO exige un destinatario institucional de un dominio permitido.");
        }
        String tenant=env.getProperty("siger.mail.graph.tenant"),client=env.getProperty("siger.mail.graph.client"),
            sender=env.getProperty("siger.mail.graph.sender"),secret=env.getProperty("siger.mail.graph.secret");
        var fake=connection.getIfAvailable();
        var graph=fake==null?new GraphMail(tenant,client,sender,secret):new GraphMail(tenant,client,sender,secret,fake);
        return new Runner(graph,mode,to,context);
    }

    static final class Runner {
        private final GraphMail graph;private final String mode,to;private final ConfigurableApplicationContext context;
        Runner(GraphMail graph,String mode,String to,ConfigurableApplicationContext context){this.graph=graph;this.mode=mode;this.to=to;this.context=context;}
        @EventListener(ApplicationReadyEvent.class) void ready(){
            GraphMail.Result r;
            try{
                r=mode.equals("TOKEN")?graph.checkToken()
                    :graph.send(new GraphMail.Message(to,SUBJECT,TEXT,UUID.randomUUID().toString()));
            }catch(RuntimeException e){r=new GraphMail.Result("ERROR","PROCESO",null,0,null);}
            String line="modo="+mode+" estado="+r.state()+" fase="+r.phase()+" http="+(r.http()==null?"-":r.http())+" requestId="+(r.requestId()==null?"-":r.requestId());
            LOG.info("{} {}",MARKER,line);
            context.close();
            if(!r.state().equals("ACEPTADO"))throw new IllegalStateException("Prueba de Graph no acreditada: "+line);
        }
    }
}
