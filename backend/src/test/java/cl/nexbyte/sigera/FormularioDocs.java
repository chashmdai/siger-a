package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Documentos sinteticos del contrato Formulario completo 1.0/1.1, sellados con la clave de prueba PRUEBA_SIGER
 * (src/test/resources/smr). Mismo algoritmo de huellas y sello que el Formulario; sin datos reales.
 */
final class FormularioDocs {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String CU="CLAVE_UNICA_USERINFO",SRCEI="SRCEI",DECL="DECLARACION_CIUDADANA",SIN="SIN_DATO";
    static final String AUTH="IDENTIDAD_AUTENTICADA",CONTR="CONTRASTADO_EN_FUENTE",NOCONTR="DECLARADO_NO_CONTRASTADO";
    static final String EVIDENCE="50000000-0000-4000-8000-000000000001";
    private static final PrivateKey KEY=load();
    private FormularioDocs(){}

    private static PrivateKey load(){
        try{
            String pem=Files.readString(Path.of("src/test/resources/smr/clave-firma-prueba-siger.pem")).replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","");
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(pem)));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static Map<String,PublicKey> trust(){return new FormularioKeys(ExternalOriginalTest.TRUST).keys();}

    static ObjectNode attr(String valor,String fuente,String nivel){return attr(valor,null,fuente,nivel);}
    static ObjectNode attr(String valor,String codigo,String fuente,String nivel){
        var a=JSON.createObjectNode();
        if(valor==null)a.putNull("valor");else a.put("valor",valor);
        if(codigo==null)a.putNull("codigo");else a.put("codigo",codigo);
        a.put("fuente",fuente);a.put("nivel",nivel);a.put("observadoEn","2026-11-04T12:00:00Z");a.put("evidencia",EVIDENCE);
        return a;
    }
    /** Persona con atributos y discrepancias dados como pares clave/atributo. */
    static ObjectNode person(String id,String rol,boolean auth,String run,String dv,Object... attrsThenDiscrepancies){
        var p=JSON.createObjectNode();
        p.put("id",id);p.put("rol",rol);
        if(run==null)p.putNull("runNumero");else p.put("runNumero",run);
        if(dv==null)p.putNull("runDv");else p.put("runDv",dv);
        p.put("modulo11",run!=null&&!auth);p.put("autenticada",auth);p.put("estadoConsulta","NO_HABILITADO");p.putNull("consultadaEn");p.putNull("consultaRef");
        var attrs=p.putObject("atributos");var disc=p.putObject("discrepancias");var target=attrs;
        for(int i=0;i<attrsThenDiscrepancies.length;i++){
            if("|".equals(attrsThenDiscrepancies[i])){target=disc;continue;}
            target.set((String)attrsThenDiscrepancies[i],(ObjectNode)attrsThenDiscrepancies[++i]);
        }
        return p;
    }
    /** Comunicacion minima valida: campos, personas y relaciones {personaId, detalles...}. */
    static ObjectNode com(String kind,Instant received,Map<String,String> campos,List<ObjectNode> persons,List<Map<String,String>> related,List<Map<String,String>> institutions){
        var c=JSON.createObjectNode();
        c.put("version","1.0");c.put("id",UUID.randomUUID().toString());c.put("recorridoId",UUID.randomUUID().toString());c.put("tipo",kind);
        c.put("recibidaEn",received.toString());c.put("avisoVersion","LAB_1");c.put("aceptaCanalDigital",!"NNA".equals(kind));
        var datos=c.putObject("datos");var f=datos.putObject("campos");campos.forEach(f::put);
        ArrayNode rel=datos.putArray("personas");
        for(var r:related){var o=rel.addObject();o.put("personaId",r.get("personaId"));var d=o.putObject("detalles");r.forEach((k,v)->{if(!k.equals("personaId"))d.put(k,v);});}
        ArrayNode inst=datos.putArray("instituciones");for(var i:institutions){var o=inst.addObject();i.forEach(o::put);}
        datos.putArray("adjuntos");
        var people=c.putArray("personas");persons.forEach(people::add);
        c.putArray("adjuntos");
        return c;
    }
    /** Calcula huellas, sella con PRUEBA_SIGER y devuelve el texto del documento. */
    static String sign(ObjectNode com){
        try{
            var sources=JSON.createObjectNode();
            for(var p:com.path("personas"))sources.set(p.path("id").asString(),JSON.createArrayNode().add(p.path("atributos")).add(p.path("discrepancias")));
            String content=ExternalOriginal.huella("CONTENIDO",com),provenance=ExternalOriginal.huella("PROCEDENCIA",sources);
            var seal=JSON.createObjectNode();
            seal.put("version","1.0");seal.put("id",com.path("id").asString());seal.put("recibidaEn",com.path("recibidaEn").asString());
            seal.put("tipoRecepcion","CUSTODIA_PUBLICA");seal.put("huellaContenido",content);seal.put("huellaProcedencia",provenance);
            seal.put("emisor","FORMULARIO_PUBLICO_DDN");seal.put("avisoVersion",com.path("avisoVersion").asString());
            byte[] payload=JSON.writeValueAsBytes(seal);
            var signer=Signature.getInstance("Ed25519");signer.initSign(KEY);signer.update(payload);
            var root=JSON.createObjectNode();
            root.set("comunicacion",com);root.put("sha256",content);root.put("huellaProcedencia",provenance);
            var c=root.putObject("custodia");
            c.put("algoritmo","Ed25519");c.put("keyId","PRUEBA_SIGER");
            c.put("payload",Base64.getUrlEncoder().withoutPadding().encodeToString(payload));
            c.put("firma",Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
            return new String(JSON.writeValueAsBytes(root),StandardCharsets.UTF_8);
        }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }

    // --- Casos de referencia ------------------------------------------------------------------
    static final Instant RECEIVED=Instant.parse("2026-11-04T12:00:00Z");
    /** Adulto ClaveUnica (12345678-5), vulneracion con NNA afectado, adulto relacionado y persona senalada. */
    static ObjectNode cuVulneration(){
        var presenter=person("presentador-adulto","PRESENTADOR",true,"12345678","5",
            "runNumero",attr("12345678",CU,AUTH),"runDv",attr("5",CU,AUTH),
            "nombres",attr("María José De los Ángeles",CU,AUTH),"apellidos",attr("Del Río González",CU,AUTH),
            "primerNombre",attr("María José",CU,AUTH),"segundoNombre",attr("De los Ángeles",CU,AUTH));
        var nna=person("afectado-1","AFECTADO_NNA",false,null,null,"primerNombre",attr("Tomás",DECL,NOCONTR),"edad",attr("9",DECL,NOCONTR));
        var adult=person("relacionado-1","ADULTO_RELACIONADO",false,null,null,"primerNombre",attr("Rosa",DECL,NOCONTR));
        var accused=person("senalado-1","PERSONA_SENALADA",false,null,null);
        var campos=new LinkedHashMap<String,String>();
        campos.put("motivo","vulneracion");campos.put("adult-region","13");campos.put("adult-commune","Ñuñoa");campos.put("adult-address","Avenida Irarrázaval 1234, depto. 5");
        campos.put("email","maria.jose@example.cl");campos.put("telefono","+56912345678");campos.put("relato","Relato sintético con tildes: niño, señal, acción.");
        campos.put("riesgo","si");campos.put("relacion","madre");campos.put("identifica-responsables","true");
        return com("ADULTO_CU",RECEIVED,campos,List.of(presenter,nna,adult,accused),
            List.of(Map.of("personaId","afectado-1","region","13","comuna","Ñuñoa"),Map.of("personaId","relacionado-1","relacion","Familiar"),Map.of("personaId","senalado-1","alias","El vecino")),
            List.of(Map.of("nombre","Escuela Básica Sintética","tipo","Establecimiento educativo","region","13","comuna","Ñuñoa")));
    }
}
