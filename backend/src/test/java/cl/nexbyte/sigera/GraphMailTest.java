package cl.nexbyte.sigera;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Transporte Graph con una conexion FALSA (sin red ni credenciales reales): OAuth client_credentials, sendMail,
 * cache del token, clasificacion de resultados y ausencia de secretos en textos. No acredita un envio real.
 */
class GraphMailTest {
    static final String TENANT="11111111-2222-4333-8444-555555555555",CLIENT="aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    static final String SECRET="secreto-sintetico-de-prueba-0123456789",SENDER="avisos.siger@defensorianinez.cl";
    static final String CORRELATION="0f0e0d0c-0b0a-4908-8706-050403020100";
    static final JsonMapper JSON=JsonMapper.builder().build();

    record Call(URI uri,Map<String,String> headers,String body){}
    /** Conexion programable: responde en orden; registra cada solicitud. */
    static final class Fake implements GraphMail.Connection {
        final List<Call> calls=new ArrayList<>();final Deque<Object> answers=new ArrayDeque<>();
        Fake token(){answers.add(new GraphMail.Response(200,"{\"access_token\":\"bearer-sintetico\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8),null,null));return this;}
        Fake status(int status,String retryAfter){answers.add(new GraphMail.Response(status,new byte[0],retryAfter,"12345678-1234-4234-8234-123456789abc"));return this;}
        Fake fail(IOException e){answers.add(e);return this;}
        @Override public GraphMail.Response post(URI uri,Map<String,String> headers,byte[] body)throws IOException{
            calls.add(new Call(uri,headers,new String(body,StandardCharsets.UTF_8)));
            Object next=answers.removeFirst();if(next instanceof IOException e)throw e;return (GraphMail.Response)next;
        }
    }
    static GraphMail graph(Fake f){return new GraphMail(TENANT,CLIENT,SENDER,SECRET,f);}
    static GraphMail.Message message(){return new GraphMail.Message("funcionaria@defensorianinez.cl",MailOutbox.SUBJECT,"Tienes un aviso nuevo en SIGER.",CORRELATION);}

    @Test void tokenYEnvioAceptadoConCredencialesSoloEnSuDestino(){
        var f=new Fake().token().status(202,null).status(202,null);var g=graph(f);
        var r=g.send(message());
        assertEquals("ACEPTADO",r.state());assertEquals(202,r.http());assertEquals("12345678-1234-4234-8234-123456789abc",r.requestId());
        var token=f.calls.get(0);var send=f.calls.get(1);
        assertEquals(URI.create("https://login.microsoftonline.com/"+TENANT+"/oauth2/v2.0/token"),token.uri());
        assertTrue(token.body().contains("client_secret="+SECRET));assertFalse(token.headers().containsKey("Authorization"));
        assertEquals(URI.create("https://graph.microsoft.com/v1.0/users/avisos.siger%40defensorianinez.cl/sendMail"),send.uri());
        assertEquals("Bearer bearer-sintetico",send.headers().get("Authorization"));assertFalse(send.body().contains(SECRET));
        var payload=JSON.readTree(send.body()).path("message");
        assertEquals(MailOutbox.SUBJECT,payload.path("subject").asString());
        assertEquals("funcionaria@defensorianinez.cl",payload.path("toRecipients").get(0).path("emailAddress").path("address").asString());
        assertEquals(CORRELATION,payload.path("internetMessageHeaders").get(0).path("value").asString());
        assertEquals(CORRELATION,send.headers().get("client-request-id"));
        // Token en cache: el segundo envio no vuelve a OAuth.
        assertEquals("ACEPTADO",g.send(message()).state());assertEquals(3,f.calls.size());
        // Ningun texto expone el secreto ni el bearer.
        assertFalse(g.toString().contains(SECRET));assertFalse(message().toString().contains("funcionaria"));
    }

    @Test void resultadosDistinguenNoEnviadoDeAmbiguo(){
        // Limitacion 429: no salio, espera Retry-After. 4xx: rechazado. 5xx o corte despues de enviar: AMBIGUO.
        var r=graph(new Fake().token().status(429,"120")).send(message());
        assertEquals("REINTENTABLE",r.state());assertEquals(120,r.retryAfterSeconds());
        assertEquals("RECHAZADO",graph(new Fake().token().status(400,null)).send(message()).state());
        assertEquals("AMBIGUO",graph(new Fake().token().status(503,"30")).send(message()).state());
        assertEquals("AMBIGUO",graph(new Fake().token().status(500,null)).send(message()).state());
        assertEquals("AMBIGUO",graph(new Fake().token().fail(new IOException("corte"))).send(message()).state());
        // Conexion nunca establecida: con seguridad no salio.
        var notSent=graph(new Fake().token().fail(new GraphMail.NotSent(new java.net.ConnectException()))).send(message());
        assertEquals("REINTENTABLE",notSent.state());assertEquals("GRAPH_SIN_CONEXION",notSent.phase());
        // 401 en sendMail: token rechazado, se descarta y se reintenta con uno nuevo.
        var f=new Fake().token().status(401,null).token().status(202,null);var g=graph(f);
        assertEquals("REINTENTABLE",g.send(message()).state());assertEquals("ACEPTADO",g.send(message()).state());assertEquals(4,f.calls.size());
    }

    @Test void falloDeTokenNoEnviaNada(){
        var f=new Fake().status(401,null);
        var r=graph(f).send(message());
        assertEquals("REINTENTABLE",r.state());assertEquals("TOKEN",r.phase());assertEquals(401,r.http());assertEquals(1,f.calls.size());
        var bad=new Fake();bad.answers.add(new GraphMail.Response(200,"{\"access_token\":\"\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8),null,null));
        assertEquals("TOKEN",graph(bad).send(message()).phase());assertEquals(1,bad.calls.size());
        var down=new Fake().fail(new IOException("sin red"));
        assertEquals("TOKEN",graph(down).send(message()).phase());
    }

    @Test void configuracionYMensajeValidados(){
        assertThrows(IllegalStateException.class,()->new GraphMail("no-guid",CLIENT,SENDER,SECRET,new Fake()));
        assertThrows(IllegalStateException.class,()->new GraphMail(TENANT,CLIENT,"sin-arroba",SECRET,new Fake()));
        assertThrows(IllegalStateException.class,()->new GraphMail(TENANT,CLIENT,SENDER,"corto",new Fake()));
        assertThrows(IllegalStateException.class,()->new GraphMail(TENANT,CLIENT,SENDER,null,new Fake()));
        var f=new Fake();var g=graph(f);
        assertEquals("RECHAZADO",g.send(new GraphMail.Message("no es correo",MailOutbox.SUBJECT,"x",CORRELATION)).state());
        assertEquals("RECHAZADO",g.send(new GraphMail.Message("a@defensorianinez.cl",MailOutbox.SUBJECT,"x","sin-uuid")).state());
        assertTrue(f.calls.isEmpty());
    }
}
