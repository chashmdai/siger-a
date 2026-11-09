package cl.nexbyte.sigera;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transporte Microsoft Graph: OAuth client_credentials y users/{remitente}/sendMail (adaptado de GraphMailService de
 * SMR). Autoridades fijas HTTPS, sin redirecciones; el secreto solo va al endpoint OAuth y el bearer solo a Graph.
 * No registra cuerpos, cabeceras ni credenciales: el resultado es estado, fase, HTTP y request-id validado.
 * Distingue lo que con seguridad no salio (REINTENTABLE) de lo que pudo salir (AMBIGUO), que nunca se reenvia solo.
 */
final class GraphMail {
    static final String TOKEN_HOST="https://login.microsoftonline.com/",GRAPH="https://graph.microsoft.com/v1.0/users/";
    private static final String GUID="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";
    static final String ADDRESS="[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+";

    record Message(String to,String subject,String text,String correlation){@Override public String toString(){return "Message[reservado]";}}
    record Result(String state,String phase,Integer http,long retryAfterSeconds,String requestId){}
    record Response(int status,byte[] body,String retryAfter,String requestId){@Override public String toString(){return "Response[HTTP="+status+"]";}}
    /** Envio HTTP. NotSent: la solicitud con seguridad no llego al servidor (conexion rechazada o sin establecer). */
    @FunctionalInterface interface Connection {Response post(URI uri,Map<String,String> headers,byte[] body)throws IOException,InterruptedException;}
    static final class NotSent extends IOException {NotSent(Throwable cause){super("Sin conexion",cause);}}

    private final String tenant,client,sender,secret;private final Connection connection;
    private final JsonMapper json=JsonMapper.builder().build();
    private String token;private long tokenUntil;

    GraphMail(String tenant,String client,String sender,String secret){this(tenant,client,sender,secret,jdk());}
    GraphMail(String tenant,String client,String sender,String secret,Connection connection){
        if(tenant==null||!tenant.matches(GUID)||client==null||!client.matches(GUID)||sender==null||!sender.matches(ADDRESS))
            throw new IllegalStateException("Configuracion de Graph invalida (tenant, cliente o remitente).");
        if(secret==null||secret.length()<20||secret.length()>512||secret.chars().anyMatch(Character::isISOControl))
            throw new IllegalStateException("Secreto de cliente Graph ausente o con formato invalido.");
        this.tenant=tenant;this.client=client;this.sender=sender;this.secret=secret;this.connection=connection;
    }

    private static Connection jdk(){
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        return (uri,headers,body)->{
            var b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent","SIGER/0.4.0").POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(b::header);
            HttpResponse<java.io.InputStream> r;
            try{r=http.send(b.build(),HttpResponse.BodyHandlers.ofInputStream());}
            catch(HttpConnectTimeoutException|ConnectException e){throw new NotSent(e);}
            try(var in=r.body()){
                byte[] bytes=in.readNBytes(32769);if(bytes.length>32768)throw new IOException("Respuesta fuera de limites");
                return new Response(r.statusCode(),bytes,r.headers().firstValue("Retry-After").orElse(null),r.headers().firstValue("request-id").orElse(null));
            }
        };
    }
    private static String encode(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static String safeId(String id){return id!=null&&id.matches(GUID)?id.toLowerCase(Locale.ROOT):null;}
    private static long wait(String v){try{return Math.max(1,Math.min(3600,Long.parseLong(v.strip())));}catch(Exception ignored){return 60;}}

    /** null si hay token vigente; si no, el resultado del fallo (nunca salio un correo en esta fase). */
    private synchronized Result token()throws InterruptedException{
        if(token!=null&&tokenUntil>System.currentTimeMillis())return null;
        String form="client_id="+encode(client)+"&client_secret="+encode(secret)+"&grant_type=client_credentials&scope="+encode("https://graph.microsoft.com/.default");
        Response r;
        try{r=connection.post(URI.create(TOKEN_HOST+tenant+"/oauth2/v2.0/token"),Map.of("Content-Type","application/x-www-form-urlencoded"),form.getBytes(StandardCharsets.UTF_8));}
        catch(IOException e){return new Result("REINTENTABLE","TOKEN",null,60,null);}
        if(r.status()!=200)return new Result("REINTENTABLE","TOKEN",r.status(),r.status()==429?wait(r.retryAfter()):300,safeId(r.requestId()));
        try{
            var data=json.readTree(r.body());String access=data.path("access_token").asString("");long seconds=data.path("expires_in").asLong(0);
            if(access.isBlank()||access.length()>16000||access.chars().anyMatch(Character::isISOControl)||seconds<60)throw new IllegalArgumentException();
            token=access;tokenUntil=System.currentTimeMillis()+Math.min(3600,seconds-30)*1000;return null;
        }catch(RuntimeException e){return new Result("REINTENTABLE","TOKEN",200,300,null);}
    }

    Result send(Message m){
        if(m.to()==null||!m.to().matches(ADDRESS)||m.correlation()==null||!m.correlation().matches(GUID)||m.subject().isBlank()||m.subject().length()>180||m.text().length()>4000)
            return new Result("RECHAZADO","VALIDACION",null,0,null);
        try{var failure=token();if(failure!=null)return failure;}
        catch(InterruptedException e){Thread.currentThread().interrupt();return new Result("REINTENTABLE","TOKEN",null,60,null);}
        String bearer;synchronized(this){bearer=token;}
        Response r;
        try{
            var payload=Map.of("message",Map.of("subject",m.subject(),"body",Map.of("contentType","Text","content",m.text()),
                "toRecipients",List.of(Map.of("emailAddress",Map.of("address",m.to()))),
                "internetMessageHeaders",List.of(Map.of("name","X-SIGER-Correlation","value",m.correlation()))),"saveToSentItems",true);
            r=connection.post(URI.create(GRAPH+encode(sender)+"/sendMail"),Map.of("Authorization","Bearer "+bearer,"Content-Type","application/json",
                "client-request-id",m.correlation(),"return-client-request-id","true"),json.writeValueAsBytes(payload));
        }catch(NotSent e){return new Result("REINTENTABLE","GRAPH_SIN_CONEXION",null,60,null);}
        catch(InterruptedException e){Thread.currentThread().interrupt();return new Result("AMBIGUO","GRAPH",null,0,null);}
        catch(IOException|RuntimeException e){return new Result("AMBIGUO","GRAPH",null,0,null);}
        int status=r.status();String id=safeId(r.requestId());
        if(status==202)return new Result("ACEPTADO","GRAPH",202,0,id);
        // 401: token rechazado antes de procesar el mensaje; 429: limitacion, Graph no lo acepto. Un 5xx queda AMBIGUO.
        if(status==401){synchronized(this){token=null;tokenUntil=0;}return new Result("REINTENTABLE","GRAPH",401,60,id);}
        if(status==429)return new Result("REINTENTABLE","GRAPH",429,wait(r.retryAfter()),id);
        if(status>=400&&status<500)return new Result("RECHAZADO","GRAPH",status,0,id);
        return new Result("AMBIGUO","GRAPH",status,0,id);
    }

    @Override public String toString(){return "GraphMail[credenciales protegidas]";}
}
