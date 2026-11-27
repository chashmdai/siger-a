package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Generador de los fixtures del contrato de antecedentes complementarios 1.0 (gate A00 de 0.9.1;
 * {@code contratos/antecedentes-complementarios-v1.0.md} de formulario-web). No es una prueba de la suite: su nombre no
 * sigue el patron de Surefire y solo corre con {@code -Dtest=AntecedentesFixtures -Dsiger.antecedentes.fixtures=<dir>},
 * con un directorio nuevo (no existe o esta vacio). Nunca corre en la suite normal ni en la CI.
 * Claves efimeras: un par Ed25519 hace de Formulario (sello de custodia) y otro de SIGER (firma de invitaciones y ACK);
 * las privadas solo existen en memoria y nunca se escriben. Se escriben solo las publicas. Datos exclusivamente
 * sinteticos (RUN 11111111-1).
 * La fuente canonica de los fixtures es {@code contratos/fixtures/} del Formulario; SIGER guarda una copia en
 * {@code src/test/resources/formulario/} con PROCEDENCIA.txt.
 */
class AntecedentesFixtures {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String LABEL="SIGER-INVITACION-v1\n";
    static final String FORM_KID="antecedentes-formulario-test",SIGER_KID="antecedentes-siger-test";
    static final String PUBLIC_ID="77777777-7777-4777-8777-777777777701";
    static final String TEXT_ID="77777777-7777-4777-8777-777777777710",FILE_ID="77777777-7777-4777-8777-777777777711";
    static final String ATTACHMENT_ID="77777777-7777-4777-8777-777777777712",EVIDENCE="77777777-7777-4777-8777-777777777720";
    static final String COM_ID="77777777-7777-4777-8777-777777777730";
    static final String NOTICE="ANT_SINTETICO_1",FOLIO="COMA-123";
    static final String RUN="11111111",DV="1";
    /** Bytes del adjunto sintetico del vector con adjunto (no es un archivo del contrato). */
    static byte[] attachment(){return "Antecedente exclusivamente sintetico ANT-1.0.\n".getBytes(StandardCharsets.UTF_8);}

    @Test void generar()throws Exception{
        String dir=System.getProperty("siger.antecedentes.fixtures");
        assertNotNull(dir,"falta -Dsiger.antecedentes.fixtures=<directorio nuevo>");
        Path out=Path.of(dir);
        if(Files.exists(out))try(var s=Files.list(out)){assertFalse(s.findAny().isPresent(),"el directorio de salida debe estar vacio");}
        Files.createDirectories(out);
        var kpg=KeyPairGenerator.getInstance("Ed25519");
        KeyPair form=kpg.generateKeyPair(),siger=kpg.generateKeyPair();
        var rnd=new SecureRandom();byte[] raw=new byte[32];rnd.nextBytes(raw);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        // Invitacion: creacion, revocacion y vector negativo sin etiqueta.
        var body=JSON.createObjectNode();
        body.put("version","1.0");body.put("publicId",PUBLIC_ID);body.put("tokenSha256",ExternalOriginal.sha256(token.getBytes(StandardCharsets.US_ASCII)));
        var run=body.putObject("runEsperado");run.put("numero",RUN);run.put("dv",DV);
        body.put("textoSolicitado","Copia sintética del certificado solicitado, para el contrato de prueba.");
        body.put("venceEn","2026-12-14T02:59:59Z");body.put("folioSiger",FOLIO);
        String create=JSON.writeValueAsString(body);
        var revoke=JSON.createObjectNode();
        revoke.put("version","1.0");revoke.put("publicId",PUBLIC_ID);revoke.put("causa","MANUAL");revoke.put("revocadaEn","2026-11-28T15:00:00Z");
        String revokeText=JSON.writeValueAsString(revoke);
        write(out,"antecedentes-invitacion-creacion-v1.0.json",invitation("POST","/integracion/v3/antecedentes/invitaciones",create,sign(siger.getPrivate(),label(create)),201,token,
            "Creacion de una invitacion firmada por SIGER con su clave ACK y la etiqueta SIGER-INVITACION-v1."));
        write(out,"antecedentes-invitacion-revocacion-v1.0.json",invitation("POST","/integracion/v3/antecedentes/invitaciones/"+PUBLIC_ID+"/revocacion",revokeText,sign(siger.getPrivate(),label(revokeText)),200,null,
            "Revocacion de la misma invitacion, firmada igual que la creacion."));
        write(out,"antecedentes-invitacion-sin-etiqueta-v1.0.json",invitation("POST","/integracion/v3/antecedentes/invitaciones",create,sign(siger.getPrivate(),create.getBytes(StandardCharsets.UTF_8)),403,null,
            "Vector negativo: la firma cubre solo el cuerpo, sin la etiqueta (como un ACK). Debe rechazarse con 403."));

        // Entregas ANT-1.0: solo texto, y texto con un adjunto.
        String textOnly=delivery(form.getPrivate(),TEXT_ID,"Antecedentes exclusivamente sintéticos para el contrato ANT-1.0.",List.of());
        var file=JSON.createObjectNode();
        file.put("id",ATTACHMENT_ID);file.put("nombre","antecedente-sintetico.txt");file.put("tamanio",attachment().length);
        file.put("sha256",ExternalOriginal.sha256(attachment()));file.put("tipo","text/plain");file.put("estado","VALIDADO");
        String withFile=delivery(form.getPrivate(),FILE_ID,"Adjunto el antecedente solicitado (sintético).",List.of(file));
        write(out,"antecedentes-entrega-texto-v1.0.json",pretty(textOnly));
        write(out,"antecedentes-entrega-adjunto-v1.0.json",pretty(withFile));

        // ACK sintetico de la entrega con adjunto (FormularioAck: version 1.0, canal FORMULARIO_WEB_PUBLICO).
        var sobre=JSON.readTree(withFile);
        var ack=new FormularioAck(SIGER_KID,siger.getPrivate(),siger.getPublic());
        var registro=new FormularioAck.Registro("1.0",UUID.fromString(FILE_ID),UUID.fromString(COM_ID),123,FOLIO,java.time.Instant.parse("2026-11-28T16:00:00Z"),
            "FORMULARIO_WEB_PUBLICO",sobre.path("sha256").asString(),sobre.path("huellaProcedencia").asString());
        write(out,"registro-antecedentes-v1.0.json",pretty(ack.sign(registro)));

        // Claves publicas de prueba ({keyId: PEM}).
        write(out,"public-antecedentes-test.json",keys(FORM_KID,form.getPublic()));
        write(out,"institution-public-antecedentes-test.json",keys(SIGER_KID,siger.getPublic()));
    }

    static byte[] label(String body){return (LABEL+body).getBytes(StandardCharsets.UTF_8);}
    static String sign(PrivateKey key,byte[] bytes)throws GeneralSecurityException{
        var s=Signature.getInstance("Ed25519");s.initSign(key);s.update(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }
    static String invitation(String method,String path,String body,String signature,int expected,String token,String description){
        var o=JSON.createObjectNode();
        o.put("descripcion",description);o.put("metodo",method);o.put("ruta",path);
        var h=o.putObject("cabeceras");h.put("X-Siger-Clave",SIGER_KID);h.put("X-Siger-Firma",signature);
        o.put("cuerpo",body);o.put("resultadoEsperado",expected);
        if(token!=null)o.put("tokenPrueba",token);
        return pretty(JSON.writeValueAsString(o));
    }
    /** Sobre {comunicacion, sha256, huellaProcedencia, custodia} de la variante ANT-1.0 con el sello de ComprobanteDigital. */
    static String delivery(PrivateKey key,String id,String text,List<ObjectNode> files)throws GeneralSecurityException{
        var com=JSON.createObjectNode();
        com.put("version","ANT-1.0");com.put("id",id);com.put("invitacion",PUBLIC_ID);
        com.put("recibidaEn","2026-11-28T15:30:00Z");com.put("avisoVersion",NOTICE);
        var idn=com.putObject("identidad");
        idn.put("runNumero",RUN);idn.put("runDv",DV);idn.put("fuente","CLAVE_UNICA_USERINFO");idn.put("nivel","IDENTIDAD_AUTENTICADA");
        idn.put("verificadaEn","2026-11-28T15:20:00Z");idn.put("evidencia",EVIDENCE);
        if(text==null)com.putNull("texto");else com.put("texto",text);
        var list=com.putArray("adjuntos");files.forEach(list::add);
        String content=ExternalOriginal.huella("ANTECEDENTES_CONTENIDO",com),provenance=ExternalOriginal.huella("ANTECEDENTES_PROCEDENCIA",idn);
        var seal=JSON.createObjectNode();
        seal.put("version","1.0");seal.put("id",id);seal.put("recibidaEn",com.path("recibidaEn").asString());seal.put("tipoRecepcion","CUSTODIA_PUBLICA");
        seal.put("huellaContenido",content);seal.put("huellaProcedencia",provenance);seal.put("emisor","FORMULARIO_PUBLICO_DDN");seal.put("avisoVersion",NOTICE);
        byte[] payload=JSON.writeValueAsBytes(seal);
        var root=JSON.createObjectNode();
        root.set("comunicacion",com);root.put("sha256",content);root.put("huellaProcedencia",provenance);
        var c=root.putObject("custodia");
        c.put("algoritmo","Ed25519");c.put("keyId",FORM_KID);
        c.put("payload",Base64.getUrlEncoder().withoutPadding().encodeToString(payload));c.put("firma",sign(key,payload));
        return JSON.writeValueAsString(root);
    }
    static String keys(String kid,PublicKey key){
        String b64=Base64.getMimeEncoder(64,"\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(key.getEncoded());
        var o=JSON.createObjectNode();o.put(kid,"-----BEGIN PUBLIC KEY-----\n"+b64+"\n-----END PUBLIC KEY-----\n");
        return pretty(JSON.writeValueAsString(o));
    }
    static String pretty(String json){JsonNode n=JSON.readTree(json);return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(n)+"\n";}
    static void write(Path dir,String name,String text)throws java.io.IOException{
        Files.writeString(dir.resolve(name),text.replace("\r\n","\n"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
