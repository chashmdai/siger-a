package cl.nexbyte.sigera;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.*;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTPS tecnico de /integracion/v3/formulario-completo/entregas (contrato del productor Formulario).
 * Nunca sigue redirecciones; el Bearer solo se envia al origen configurado y a rutas formadas por UUID validados.
 * TLS con verificacion de cadena y nombre de host del JDK; opcionalmente una CA propia del operador.
 * HTTP sin TLS solo se admite hacia la interfaz local (127.0.0.1/localhost) para pruebas aisladas.
 */
final class FormularioClient {
    static final String ROOT="/integracion/v3/formulario-completo/entregas";
    static final int MAX_CLAIM=ExternalOriginal.MAX_BYTES+64*1024;
    private static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(20000).maxNumberLength(30).build()).build()).build();

    /** Fallo de transporte o de protocolo; status 0 si no hubo respuesta HTTP. Sin cuerpo ni datos del recurso. */
    static final class Failure extends Exception {
        final int status;final String code;
        Failure(int status,String code){super(code);this.status=status;this.code=code;}
    }
    /** Entrega reservada: el texto de la entrega se valida despues con ExternalOriginal.parse. */
    record Claim(UUID lease,long until,int attempt,String operation,String document) {
        @Override public String toString(){return "Claim[operation="+operation+",attempt="+attempt+"]";}
    }

    private final URI origin;private final String token;private final HttpClient http;

    FormularioClient(URI origin,String token,Path ca,boolean loopbackHttp){
        if(origin==null||origin.getHost()==null||origin.getRawUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null
            ||!Set.of("","/").contains(Objects.toString(origin.getRawPath(),"")))throw new IllegalStateException("Origen del Formulario invalido.");
        boolean loopback=Set.of("127.0.0.1","localhost").contains(origin.getHost());
        if(!"https".equals(origin.getScheme())&&!(loopbackHttp&&loopback&&"http".equals(origin.getScheme())))throw new IllegalStateException("El origen del Formulario debe usar HTTPS.");
        if(token==null||!token.matches("[A-Za-z0-9._~+/-]{32,400}=*"))throw new IllegalStateException("Credencial del Formulario ausente o invalida.");
        this.origin=URI.create(origin.getScheme()+"://"+origin.getRawAuthority());this.token=token;
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1);
        if(ca!=null)builder.sslContext(context(ca));
        http=builder.build();
    }
    /** Confianza TLS limitada a una CA del operador (un certificado PEM con restriccion basica de CA). */
    private static SSLContext context(Path ca){
        try{
            if(Files.size(ca)>32768)throw new IOException();
            X509Certificate cert;try(var in=Files.newInputStream(ca)){cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}
            cert.checkValidity();if(cert.getBasicConstraints()<0)throw new IOException();
            var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);store.setCertificateEntry("formulario",cert);
            var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
            var ssl=SSLContext.getInstance("TLS");ssl.init(null,trust.getTrustManagers(),null);return ssl;
        }catch(Exception e){throw new IllegalStateException("Certificado de CA del Formulario invalido o ilegible.");}
    }
    private static String id(String uuid){var u=UUID.fromString(uuid).toString();if(!u.equals(uuid))throw new IllegalArgumentException();return u;}
    private HttpRequest.Builder request(String suffix,Duration timeout){
        return HttpRequest.newBuilder(URI.create(origin+ROOT+suffix)).timeout(timeout)
            .header("User-Agent","SIGER-A/0.4.0").header("Authorization","Bearer "+token);
    }
    private <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler)throws Failure{
        try{return http.send(request,handler);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new Failure(0,"INTERRUMPIDO");}
        catch(HttpTimeoutException e){throw new Failure(0,"TIEMPO_AGOTADO");}
        catch(SSLException e){throw new Failure(0,"TLS_NO_VERIFICADO");}
        catch(IOException e){throw new Failure(0,"ORIGEN_NO_DISPONIBLE");}
    }
    private static byte[] read(InputStream in,int max)throws IOException{
        try(in){byte[] out=in.readNBytes(max+1);if(out.length>max)throw new IOException("Respuesta excesiva");return out;}
    }

    /**
     * POST /reclamar con {"limite":1} y X-Formulario-Versiones: 1.0,1.1. Devuelve 0 o 1 reservas.
     * Solo 200 con un lote bien formado; cualquier otro estado es un fallo, nunca un lote vacio.
     */
    List<Claim> claim()throws Failure{
        var response=send(request("/reclamar",Duration.ofSeconds(30)).header("Content-Type","application/json").header("Accept","application/json")
            .header("X-Formulario-Versiones","1.0,1.1").POST(HttpRequest.BodyPublishers.ofString("{\"limite\":1}",StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofInputStream());
        byte[] body;
        try{if(response.statusCode()!=200){response.body().close();throw new Failure(response.statusCode(),"RECLAMO_HTTP_"+response.statusCode());}body=read(response.body(),MAX_CLAIM);}
        catch(IOException e){throw new Failure(response.statusCode(),"RECLAMO_INCOMPLETO");}
        try{
            JsonNode root=JSON.readTree(body),list=root.path("entregas");
            if(!root.isObject()||root.size()!=1||!list.isArray()||list.size()>1)throw new IllegalArgumentException();
            var out=new ArrayList<Claim>();
            for(var item:list){
                if(!item.isObject()||item.size()!=4||!item.path("venceEn").canConvertToExactIntegral()||!item.path("intento").canConvertToExactIntegral()||!item.path("entrega").isObject())throw new IllegalArgumentException();
                var lease=UUID.fromString(id(item.path("reserva").asString()));
                long until=item.path("venceEn").asLong();int attempt=item.path("intento").asInt();
                if(attempt<1||attempt>10||until<=System.currentTimeMillis())throw new IllegalArgumentException();
                String operation=id(item.path("entrega").path("comunicacion").path("id").asString());
                out.add(new Claim(lease,until,attempt,operation,JSON.writeValueAsString(item.path("entrega"))));
            }
            return List.copyOf(out);
        }catch(RuntimeException e){throw new Failure(200,"RECLAMO_MAL_FORMADO");}
    }

    /** Destino de una descarga por streaming; recibe el cuerpo una sola vez. */
    @FunctionalInterface interface Sink { void accept(InputStream body)throws IOException; }

    /**
     * GET /{id}/adjuntos/{adjunto} con X-Reserva, sin Range. Solo 200 completo; Content-Length, si viene, debe ser
     * el tamano firmado. La transferencia se corta al vencer el plazo (antes del fin de la reserva).
     */
    void download(String operation,String attachment,UUID lease,long size,long deadline,Sink sink)throws Failure{
        long remaining=deadline-System.currentTimeMillis();
        if(remaining<1000)throw new Failure(0,"RESERVA_POR_VENCER");
        var response=send(request("/"+id(operation)+"/adjuntos/"+id(attachment),Duration.ofMillis(remaining)).header("X-Reserva",lease.toString()).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        var body=response.body();
        var watchdog=Thread.ofVirtual().start(()->{
            try{Thread.sleep(Math.max(1,deadline-System.currentTimeMillis()));body.close();}catch(InterruptedException|IOException ignored){}
        });
        try{
            if(response.statusCode()!=200)throw new Failure(response.statusCode(),"ADJUNTO_HTTP_"+response.statusCode());
            long length=response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if(length!=-1&&length!=size)throw new Failure(200,"ADJUNTO_LONGITUD_DISTINTA");
            try{sink.accept(body);}catch(IOException e){throw new Failure(200,System.currentTimeMillis()>=deadline?"ADJUNTO_RESERVA_VENCIDA":"ADJUNTO_NO_CUSTODIADO");}
        }finally{
            watchdog.interrupt();
            try{body.close();}catch(IOException ignored){}
        }
    }

    /** POST /{id}/confirmacion con la reserva y la recepcion firmada conservada. Devuelve el estado HTTP (204 = aceptado). */
    int confirm(String operation,UUID lease,String receipt)throws Failure{
        String body="{\"reserva\":\""+lease+"\",\"recepcion\":"+receipt+"}";
        var response=send(request("/"+id(operation)+"/confirmacion",Duration.ofSeconds(30)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    /** POST /{id}/fallo con un codigo del contrato. Devuelve el estado HTTP (204 = aceptado). */
    int fail(String operation,UUID lease,String code)throws Failure{
        if(!Set.of("TRANSITORIO","CONTRATO_INVALIDO","RECEPTOR_NO_DISPONIBLE","CONFIRMACION_AMBIGUA").contains(code))throw new IllegalArgumentException();
        String body="{\"reserva\":\""+lease+"\",\"codigo\":\""+code+"\"}";
        var response=send(request("/"+id(operation)+"/fallo",Duration.ofSeconds(30)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    @Override public String toString(){return "FormularioClient["+origin+"]";}
}
