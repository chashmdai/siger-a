package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrato de antecedentes complementarios 1.0 (gate A00 de 0.9.1; {@code contratos/antecedentes-complementarios-v1.0.md}
 * de formulario-web): comprueba que los fixtures copiados del Formulario se leen y se firman como dice el contrato. No
 * hay codigo de produccion nuevo: el verificador de esta prueba es la referencia que A01 (firma de invitaciones) y A02
 * (recepcion de la variante ANT-1.0) deben cumplir. Tambien deja constancia de que la recepcion vigente rechaza hoy la
 * variante (ExternalOriginal.parse acepta solo 1.0 y 1.1).
 * Los recursos se leen con CRLF pasado a LF (copia de trabajo de Windows) y PROCEDENCIA.txt fija su SHA-256.
 */
class AntecedentesContractTest {
    static final Path DIR=Path.of("src/test/resources/formulario");
    static final JsonMapper STRICT=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
    static final String LABEL="SIGER-INVITACION-v1\n";
    static final List<String> FILES=List.of("antecedentes-invitacion-creacion-v1.0.json","antecedentes-invitacion-revocacion-v1.0.json",
        "antecedentes-invitacion-sin-etiqueta-v1.0.json","antecedentes-entrega-texto-v1.0.json","antecedentes-entrega-adjunto-v1.0.json",
        "registro-antecedentes-v1.0.json","public-antecedentes-test.json","institution-public-antecedentes-test.json");
    static final List<String> CREATE_FIELDS=List.of("version","publicId","tokenSha256","runEsperado","textoSolicitado","venceEn","folioSiger");
    static final List<String> REVOKE_FIELDS=List.of("version","publicId","causa","revocadaEn");
    static final List<String> COM_FIELDS=List.of("version","id","invitacion","recibidaEn","avisoVersion","identidad","texto","adjuntos");
    static final List<String> IDENTITY_FIELDS=List.of("runNumero","runDv","fuente","nivel","verificadaEn","evidencia");
    static final List<String> ATTACHMENT_FIELDS=List.of("id","nombre","tamanio","sha256","tipo","estado");
    static final List<String> SEAL_FIELDS=List.of("version","id","recibidaEn","tipoRecepcion","huellaContenido","huellaProcedencia","emisor","avisoVersion");

    static String read(String name)throws Exception{return Files.readString(DIR.resolve(name),StandardCharsets.UTF_8).replace("\r\n","\n");}
    static JsonNode json(String name)throws Exception{return STRICT.readTree(read(name));}
    static Map<String,PublicKey> keys(String name)throws Exception{
        var out=new HashMap<String,PublicKey>();
        for(var e:json(name).properties()){
            String pem=e.getValue().asString().strip();
            byte[] der=Base64.getMimeDecoder().decode(pem.substring(26,pem.length()-24));
            out.put(e.getKey(),KeyFactory.getInstance("Ed25519").generatePublic(new java.security.spec.X509EncodedKeySpec(der)));
        }
        return out;
    }
    static List<String> names(JsonNode n){var out=new ArrayList<String>();n.properties().forEach(e->out.add(e.getKey()));return out;}
    static boolean ed25519(PublicKey key,byte[] data,String signature){
        try{
            if(!signature.matches("[A-Za-z0-9_-]{86}"))return false;
            var s=Signature.getInstance("Ed25519");s.initVerify(key);s.update(data);
            return s.verify(Base64.getUrlDecoder().decode(signature));
        }catch(GeneralSecurityException|IllegalArgumentException e){return false;}
    }

    /**
     * Verificacion de una invitacion como la hara el Formulario (seccion 2.3 del contrato): clave por X-Siger-Clave entre
     * las institucionales, firma sobre la etiqueta mas los bytes exactos del cuerpo, antes de leer el JSON.
     */
    static boolean signedBySiger(Map<String,PublicKey> institutional,String keyId,String signature,String body){
        var key=keyId==null?null:institutional.get(keyId);
        if(key==null)return false;
        return ed25519(key,(LABEL+body).getBytes(StandardCharsets.UTF_8),signature);
    }
    /** Lectura estricta del cuerpo de creacion (seccion 2.4): lista cerrada y ordenada, sin claves repetidas, formatos. */
    static JsonNode creation(String body){
        JsonNode n=STRICT.readTree(body);
        if(!n.isObject()||!names(n).equals(CREATE_FIELDS)||!"1.0".equals(n.path("version").asString()))throw new IllegalArgumentException("campos");
        uuid(n.path("publicId"));
        if(!n.path("tokenSha256").asString().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("token");
        var run=n.path("runEsperado");
        if(!run.isObject()||!names(run).equals(List.of("numero","dv"))||!run.path("numero").asString().matches("[1-9][0-9]{0,7}")
            ||!run.path("dv").asString().matches("[0-9K]")||!ExternalProjection.modulo11(run.path("numero").asString(),run.path("dv").asString()))throw new IllegalArgumentException("run");
        String text=n.path("textoSolicitado").asString();
        if(!n.path("textoSolicitado").isString()||text.isBlank()||!text.equals(text.strip())||text.codePointCount(0,text.length())>1000
            ||text.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("texto");
        instant(n.path("venceEn"));
        if(!n.path("folioSiger").asString().matches(FormularioAck.FOLIO))throw new IllegalArgumentException("folio");
        return n;
    }
    static JsonNode revocation(String body,String pathId){
        JsonNode n=STRICT.readTree(body);
        if(!n.isObject()||!names(n).equals(REVOKE_FIELDS)||!"1.0".equals(n.path("version").asString())||!Set.of("MANUAL","CIERRE_CASO").contains(n.path("causa").asString()))throw new IllegalArgumentException("campos");
        if(!uuid(n.path("publicId")).equals(pathId))throw new IllegalArgumentException("ruta");
        instant(n.path("revocadaEn"));
        return n;
    }
    static String uuid(JsonNode n){
        if(!n.isString())throw new IllegalArgumentException("uuid");
        String s=n.asString();if(!UUID.fromString(s).toString().equals(s))throw new IllegalArgumentException("uuid");
        return s;
    }
    static Instant instant(JsonNode n){
        if(!n.isString())throw new IllegalArgumentException("instante");
        var at=Instant.parse(n.asString());if(!at.toString().equals(n.asString()))throw new IllegalArgumentException("instante");
        return at;
    }

    @Test void procedenciaFijaLaHuellaDeCadaFixture()throws Exception{
        var listed=new TreeMap<String,String>();
        for(String line:read("PROCEDENCIA.txt").split("\n")){
            var m=java.util.regex.Pattern.compile("([a-f0-9]{64})  (\\S+)  contratos/fixtures/(\\S+)").matcher(line);
            if(m.matches()&&m.group(2).equals(m.group(3)))listed.put(m.group(2),m.group(1));
        }
        for(String f:FILES)assertEquals(listed.get(f),ExternalOriginal.sha256(read(f).getBytes(StandardCharsets.UTF_8)),"huella de "+f+" en PROCEDENCIA.txt");
    }

    @Test void creacionYRevocacionFirmadasConLaEtiqueta()throws Exception{
        var institutional=keys("institution-public-antecedentes-test.json");
        var create=json("antecedentes-invitacion-creacion-v1.0.json");
        assertEquals("/integracion/v3/antecedentes/invitaciones",create.path("ruta").asString());
        String body=create.path("cuerpo").asString(),kid=create.path("cabeceras").path("X-Siger-Clave").asString(),sig=create.path("cabeceras").path("X-Siger-Firma").asString();
        assertTrue(signedBySiger(institutional,kid,sig,body),"firma de la creacion sobre la etiqueta y el cuerpo");
        assertFalse(ed25519(institutional.get(kid),body.getBytes(StandardCharsets.UTF_8),sig),"la firma no vale sobre el cuerpo solo");
        var n=creation(body);
        // El token viaja solo al ciudadano: el cuerpo lleva su huella SHA-256 (hex del texto base64url de 43 caracteres).
        String token=create.path("tokenPrueba").asString();
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32,Base64.getUrlDecoder().decode(token).length);
        assertEquals(n.path("tokenSha256").asString(),ExternalOriginal.sha256(token.getBytes(StandardCharsets.US_ASCII)));
        assertFalse(body.contains(token),"el token en claro no viaja en el cuerpo");
        assertEquals(201,create.path("resultadoEsperado").asInt());

        var revoke=json("antecedentes-invitacion-revocacion-v1.0.json");
        String rbody=revoke.path("cuerpo").asString();
        assertTrue(signedBySiger(institutional,revoke.path("cabeceras").path("X-Siger-Clave").asString(),revoke.path("cabeceras").path("X-Siger-Firma").asString(),rbody));
        String path=revoke.path("ruta").asString();
        assertTrue(path.matches("/integracion/v3/antecedentes/invitaciones/[0-9a-f-]{36}/revocacion"));
        assertEquals(n.path("publicId").asString(),revocation(rbody,path.split("/")[5]).path("publicId").asString());
        assertEquals(200,revoke.path("resultadoEsperado").asInt());
    }

    @Test void lecturaEstrictaDelCuerpo()throws Exception{
        String body=json("antecedentes-invitacion-creacion-v1.0.json").path("cuerpo").asString();
        creation(body);
        var bad=List.of(
            body.replace("\"folioSiger\"","\"rut\":\"1-9\",\"folioSiger\""),           // campo fuera de la lista
            body.replace("{\"version\":\"1.0\",","{\"version\":\"1.0\",\"version\":\"1.0\","), // clave repetida
            body.replace("\"version\":\"1.0\"","\"version\":\"1.1\""),
            body.replace("\"dv\":\"1\"","\"dv\":\"2\""),                                  // RUN invalido por modulo 11
            body.replace("2026-12-14T02:59:59Z","2026-12-14T02:59:59.000Z"),               // instante no canonico
            body.substring(0,body.indexOf(",\"folioSiger\""))+"}");                       // falta un campo
        for(String b:bad)assertThrows(RuntimeException.class,()->creation(b),b);
    }

    @Test void vectorSinEtiquetaYClaveDesconocidaSeRechazan()throws Exception{
        var institutional=keys("institution-public-antecedentes-test.json");
        var v=json("antecedentes-invitacion-sin-etiqueta-v1.0.json");
        String body=v.path("cuerpo").asString(),kid=v.path("cabeceras").path("X-Siger-Clave").asString(),sig=v.path("cabeceras").path("X-Siger-Firma").asString();
        assertEquals(403,v.path("resultadoEsperado").asInt());
        assertTrue(ed25519(institutional.get(kid),body.getBytes(StandardCharsets.UTF_8),sig),"el vector firma solo el cuerpo, como un ACK");
        assertFalse(signedBySiger(institutional,kid,sig,body),"sin la etiqueta no vale como invitacion");
        var create=json("antecedentes-invitacion-creacion-v1.0.json");
        String good=create.path("cabeceras").path("X-Siger-Firma").asString(),cbody=create.path("cuerpo").asString();
        assertFalse(signedBySiger(institutional,"clave-desconocida",good,cbody),"keyId fuera del mapa de claves institucionales");
        assertFalse(signedBySiger(institutional,null,good,cbody),"sin X-Siger-Clave");
        assertFalse(signedBySiger(keys("public-antecedentes-test.json"),"antecedentes-formulario-test",good,cbody),"la clave del Formulario no firma invitaciones");
        byte[] altered=cbody.getBytes(StandardCharsets.UTF_8);altered[altered.length-3]^=1;
        assertFalse(signedBySiger(institutional,create.path("cabeceras").path("X-Siger-Clave").asString(),good,new String(altered,StandardCharsets.UTF_8)),"un byte distinto del cuerpo");
    }

    @Test void entregasAnt10ConHuellasYSello()throws Exception{
        var trusted=keys("public-antecedentes-test.json");
        var invitation=creation(json("antecedentes-invitacion-creacion-v1.0.json").path("cuerpo").asString());
        for(String f:List.of("antecedentes-entrega-texto-v1.0.json","antecedentes-entrega-adjunto-v1.0.json")){
            var root=json(f);var com=root.path("comunicacion");var idn=com.path("identidad");var custody=root.path("custodia");
            assertEquals(List.of("comunicacion","sha256","huellaProcedencia","custodia"),names(root),f);
            assertEquals(COM_FIELDS,names(com),f);assertEquals(IDENTITY_FIELDS,names(idn),f);
            assertEquals("ANT-1.0",com.path("version").asString());
            uuid(com.path("id"));assertEquals(invitation.path("publicId").asString(),uuid(com.path("invitacion")),"referencia a la invitacion");
            var received=instant(com.path("recibidaEn"));
            // Identidad: solo RUN autenticado por ClaveUnica, nivel y evidencia; sin nombres, sesion, token ni cookie.
            assertEquals("CLAVE_UNICA_USERINFO",idn.path("fuente").asString());assertEquals("IDENTIDAD_AUTENTICADA",idn.path("nivel").asString());
            assertEquals(invitation.path("runEsperado").path("numero").asString(),idn.path("runNumero").asString(),"RUN autenticado = esperado");
            assertEquals(invitation.path("runEsperado").path("dv").asString(),idn.path("runDv").asString());
            assertFalse(instant(idn.path("verificadaEn")).isAfter(received));
            uuid(idn.path("evidencia"));
            boolean text=com.path("texto").isString();
            assertTrue(text||com.path("texto").isNull());
            if(text)assertFalse(com.path("texto").asString().isBlank());
            assertTrue(text||!com.path("adjuntos").isEmpty(),"al menos texto o un adjunto");
            for(var a:com.path("adjuntos")){
                assertEquals(ATTACHMENT_FIELDS,names(a));uuid(a.path("id"));
                assertEquals("VALIDADO",a.path("estado").asString());assertTrue(a.path("sha256").asString().matches("[a-f0-9]{64}"));
                assertEquals(AntecedentesFixtures.attachment().length,a.path("tamanio").asLong());
                assertEquals(ExternalOriginal.sha256(AntecedentesFixtures.attachment()),a.path("sha256").asString(),"bytes del adjunto sintetico");
            }
            // Huellas con dominios propios (CanonFormulario) y sello de custodia sin cambios (ComprobanteDigital 1.0).
            assertEquals(ExternalOriginal.huella("ANTECEDENTES_CONTENIDO",com),root.path("sha256").asString(),f);
            assertEquals(ExternalOriginal.huella("ANTECEDENTES_PROCEDENCIA",idn),root.path("huellaProcedencia").asString(),f);
            assertEquals("Ed25519",custody.path("algoritmo").asString());
            byte[] payload=Base64.getUrlDecoder().decode(custody.path("payload").asString());
            assertTrue(ed25519(trusted.get(custody.path("keyId").asString()),payload,custody.path("firma").asString()),"sello del Formulario");
            var seal=STRICT.readTree(payload);
            assertEquals(SEAL_FIELDS,names(seal));
            assertArrayEquals(payload,JsonMapper.builder().build().writeValueAsBytes(seal),"payload canonico");
            assertEquals(List.of("1.0",com.path("id").asString(),com.path("recibidaEn").asString(),"CUSTODIA_PUBLICA",root.path("sha256").asString(),
                root.path("huellaProcedencia").asString(),"FORMULARIO_PUBLICO_DDN",com.path("avisoVersion").asString()),
                SEAL_FIELDS.stream().map(k->seal.path(k).asString()).toList());
        }
    }

    @Test void ackDeLaEntregaSinCambios()throws Exception{
        var institutional=keys("institution-public-antecedentes-test.json");
        var root=json("registro-antecedentes-v1.0.json");var entrega=json("antecedentes-entrega-adjunto-v1.0.json");
        var proof=root.path("comprobante");
        byte[] payload=Base64.getUrlDecoder().decode(proof.path("payload").asString());
        assertTrue(ed25519(institutional.get(proof.path("keyId").asString()),payload,proof.path("firma").asString()),"ACK firmado por SIGER sin etiqueta");
        var signed=STRICT.readTree(payload);
        assertEquals(signed,root.path("registro"));
        var r=new FormularioAck.Registro(signed.path("version").asString(),UUID.fromString(signed.path("operacionPublicaId").asString()),
            UUID.fromString(signed.path("comunicacionId").asString()),signed.path("numeroFolio").asLong(),signed.path("folio").asString(),
            Instant.parse(signed.path("registradaEn").asString()),signed.path("canalOrigen").asString(),signed.path("sha256").asString(),signed.path("huellaProcedencia").asString());
        assertArrayEquals(FormularioAck.payload(r),payload,"payload del ACK en el orden del productor");
        assertEquals("1.0",r.version());assertEquals("FORMULARIO_WEB_PUBLICO",r.channel());
        assertEquals(entrega.path("comunicacion").path("id").asString(),r.operation().toString());
        assertEquals(entrega.path("sha256").asString(),r.sha256());assertEquals(entrega.path("huellaProcedencia").asString(),r.provenance());
        // Un ACK no sirve como invitacion aunque lo firme la misma clave: le falta la etiqueta.
        assertFalse(signedBySiger(institutional,proof.path("keyId").asString(),proof.path("firma").asString(),new String(payload,StandardCharsets.UTF_8)));
    }

    @Test void laRecepcionVigenteRechazaHoyLaVarianteAnt10()throws Exception{
        var trusted=keys("public-antecedentes-test.json");
        for(String f:List.of("antecedentes-entrega-texto-v1.0.json","antecedentes-entrega-adjunto-v1.0.json")){
            var p=assertThrows(Problem.class,()->ExternalOriginal.parse(read(f),trusted),f);
            assertEquals(400,p.status);
            assertEquals("El documento original no corresponde al contrato del Formulario completo.",p.getMessage());
        }
    }
}
