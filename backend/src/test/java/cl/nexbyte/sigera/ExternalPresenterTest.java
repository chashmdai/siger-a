package cl.nexbyte.sigera;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

/** Proyeccion del presentador desde personas/atributos del Formulario completo (sin Spring ni base). */
class ExternalPresenterTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String CU="CLAVE_UNICA_USERINFO",SRCEI="SRCEI",DECL="DECLARACION_CIUDADANA";
    static final String AUTH="IDENTIDAD_AUTENTICADA",CONTR="CONTRASTADO_EN_FUENTE",NOCONTR="DECLARADO_NO_CONTRASTADO";

    static String attr(String valor,String fuente,String nivel){return attr(valor,null,fuente,nivel);}
    static String attr(String valor,String codigo,String fuente,String nivel){
        return "{\"valor\":"+(valor==null?"null":"\""+valor+"\"")+",\"codigo\":"+(codigo==null?"null":"\""+codigo+"\"")
            +",\"fuente\":\""+fuente+"\",\"nivel\":\""+nivel+"\",\"observadoEn\":\"2026-11-04T12:00:00Z\",\"evidencia\":\"50000000-0000-4000-8000-000000000001\"}";
    }
    static String person(String id,String rol,boolean auth,String run,String dv,String attrs,String discrepancies){
        return "{\"id\":\""+id+"\",\"rol\":\""+rol+"\",\"runNumero\":"+(run==null?"null":"\""+run+"\"")+",\"runDv\":"+(dv==null?"null":"\""+dv+"\"")
            +",\"modulo11\":"+(run!=null&&!auth)+",\"autenticada\":"+auth+",\"estadoConsulta\":\"NO_HABILITADO\",\"consultadaEn\":null,\"consultaRef\":null"
            +",\"atributos\":{"+attrs+"},\"discrepancias\":{"+discrepancies+"}}";
    }
    static JsonNode com(String... persons){return JSON.readTree("{\"personas\":["+String.join(",",persons)+"]}");}
    static ExternalPresenter project(String... persons){return ExternalPresenter.project(com(persons));}

    static final String CU_ADULT=person("presentador","PRESENTADOR",true,"12345678","5",
        "\"runNumero\":"+attr("12345678",CU,AUTH)+",\"runDv\":"+attr("5",CU,AUTH)
        +",\"nombres\":"+attr("María José De los Ángeles",CU,AUTH)+",\"apellidos\":"+attr("Del Río González",CU,AUTH)
        +",\"primerNombre\":"+attr("María José",CU,AUTH)+",\"segundoNombre\":"+attr("De los Ángeles",CU,AUTH),"");
    static final String AFFECTED_NNA=person("nna-1","AFECTADO_NNA",false,"22222222","2",
        "\"primerNombre\":"+attr("Hijo",DECL,NOCONTR)+",\"primerApellido\":"+attr("Afectado",DECL,NOCONTR)
        +",\"runNumero\":"+attr("22222222",DECL,NOCONTR)+",\"runDv\":"+attr("2",DECL,NOCONTR),"");

    @Test void claveUnicaUsaAgregadosSinDividirYNoConfundeAlNnaAfectado(){
        var p=project(AFFECTED_NNA,CU_ADULT);
        assertEquals("María José De los Ángeles Del Río González (autenticado con ClaveÚnica)",p.presenter());
        assertEquals("RUN 12345678-5 (autenticado con ClaveÚnica)",p.identityDocument());
    }

    @Test void runDeclaradoYNombresSrceiNoSeAtribuyenAutenticacion(){
        var p=project(person("presentador","PRESENTADOR",false,"12345678","5",
            "\"runNumero\":"+attr("12345678",DECL,NOCONTR)+",\"runDv\":"+attr("5",DECL,NOCONTR)
            +",\"nombres\":"+attr("ANA MARIA",SRCEI,CONTR)+",\"primerApellido\":"+attr("PEREZ",SRCEI,CONTR)
            +",\"segundoApellido\":"+attr("SOTO",DECL,NOCONTR)+",\"sexo\":"+attr("Femenino","F",SRCEI,CONTR),
            "\"segundoApellido\":"+attr("SOTTO",SRCEI,CONTR)));
        assertEquals("ANA MARIA PEREZ SOTO (nombres: contrastado en SRCeI, no autenticado; primer apellido: contrastado en SRCeI, no autenticado; segundo apellido: declarado)",p.presenter());
        assertEquals("RUN 12345678-5 (declarado)",p.identityDocument());
        assertFalse(p.presenter().contains("ClaveÚnica"));
        // Todo contrastado en SRCeI: una sola procedencia.
        var srcei=project(person("presentador","PRESENTADOR",false,null,null,
            "\"nombres\":"+attr("ANA MARIA",SRCEI,CONTR)+",\"primerApellido\":"+attr("PEREZ",SRCEI,CONTR),""));
        assertEquals("ANA MARIA PEREZ (contrastado en SRCeI, no autenticado)",srcei.presenter());
        assertNull(srcei.identityDocument());
    }

    @Test void declaradoSinRunNoInventaNiDivideYDescartaDesconocidos(){
        var p=project(person("presentador","PRESENTADOR",false,null,null,
            "\"primerNombre\":"+attr("Juan Carlos",DECL,NOCONTR)+",\"segundoNombre\":"+attr("Desconocido",DECL,NOCONTR)
            +",\"primerApellido\":"+attr("  ",DECL,NOCONTR)+",\"segundoApellido\":"+attr("Soto","U",DECL,NOCONTR),""));
        assertEquals("Juan Carlos (declarado)",p.presenter());
        assertNull(p.identityDocument());
        // Sin ningun nombre util: no se proyecta nada.
        var empty=project(person("presentador","PRESENTADOR",false,null,null,"\"edad\":"+attr("12",DECL,NOCONTR)+",\"primerNombre\":"+attr(null,"SIN_DATO",NOCONTR),""));
        assertNull(empty.presenter());assertNull(empty.identityDocument());
    }

    @Test void procedenciaInconsistenteOPresentadorAmbiguoNoSeProyecta(){
        // Atributos con fuente ClaveUnica en persona no autenticada: no se atribuye autenticacion.
        var fake=project(person("presentador","PRESENTADOR",false,"12345678","5",
            "\"runNumero\":"+attr("12345678",CU,AUTH)+",\"runDv\":"+attr("5",CU,AUTH)+",\"nombres\":"+attr("Falso",CU,AUTH)
            +",\"primerApellido\":"+attr("Declarado",DECL,NOCONTR),""));
        assertEquals("Declarado (declarado)",fake.presenter());
        assertNull(fake.identityDocument());
        // Nivel que no corresponde a la fuente.
        assertNull(project(person("presentador","PRESENTADOR",false,null,null,"\"primerNombre\":"+attr("Ana",SRCEI,NOCONTR)+",\"primerApellido\":"+attr("Paz",DECL,AUTH),"")).presenter());
        // RUN con procedencias distintas, formato invalido o distinto del informado en la persona.
        assertNull(project(person("presentador","PRESENTADOR",false,"12345678","5","\"runNumero\":"+attr("12345678",DECL,NOCONTR)+",\"runDv\":"+attr("5",SRCEI,CONTR),"")).identityDocument());
        assertNull(project(person("presentador","PRESENTADOR",false,"12.345.678","5","\"runNumero\":"+attr("12.345.678",DECL,NOCONTR)+",\"runDv\":"+attr("5",DECL,NOCONTR),"")).identityDocument());
        assertNull(project(person("presentador","PRESENTADOR",false,"11111111","5","\"runNumero\":"+attr("12345678",DECL,NOCONTR)+",\"runDv\":"+attr("5",DECL,NOCONTR),"")).identityDocument());
        // Sin presentador o con dos: no se elige uno.
        assertEquals(new ExternalPresenter(null,null),project(AFFECTED_NNA));
        assertEquals(new ExternalPresenter(null,null),project(CU_ADULT,CU_ADULT.replace("\"presentador\"","\"otro\"")));
        // No se trunca: si no cabe en la columna, queda vacio.
        var longName=project(person("presentador","PRESENTADOR",false,null,null,"\"nombres\":"+attr("A".repeat(190),SRCEI,CONTR),""));
        assertNull(longName.presenter());
    }

    /** Documento completo autentico (firmado con clave de prueba) que pasa por la verificacion real. */
    @Test void documentoAutenticoProyectaSinAlterarElOriginal()throws Exception{
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String document=signed(ExternalOriginalTest.fixture("1.1"),"ADULTO_CU",List.of(AFFECTED_NNA,CU_ADULT),pair);
        var o=ExternalOriginal.parse(document,Map.of("PRUEBA",pair.getPublic()));
        assertEquals("María José De los Ángeles Del Río González (autenticado con ClaveÚnica)",o.identification().presenter());
        assertEquals("RUN 12345678-5 (autenticado con ClaveÚnica)",o.identification().identityDocument());
        assertEquals(document,o.document());
        // Fixture firmado de SMR (NNA presentador con datos declarados, sin RUN).
        var nna=ExternalOriginal.parse(ExternalOriginalTest.fixture("1.1"),ExternalOriginalTest.trust());
        assertEquals("PERSONA SINTETICA (declarado)",nna.identification().presenter());
        assertNull(nna.identification().identityDocument());
    }

    /** Reemplaza tipo y personas, recalcula huellas y sella con la clave de prueba (mismo contrato que el Formulario). */
    static String signed(String base,String kind,List<String> persons,KeyPair pair)throws Exception{
        var root=(ObjectNode)JSON.readTree(base);
        var com=(ObjectNode)root.get("comunicacion");
        com.put("tipo",kind);
        com.set("personas",JSON.readTree("["+String.join(",",persons)+"]"));
        var sources=JSON.createObjectNode();
        for(var p:com.get("personas"))sources.set(p.get("id").asString(),JSON.createArrayNode().add(p.get("atributos")).add(p.get("discrepancias")));
        String content=ExternalOriginal.huella("CONTENIDO",com),provenance=ExternalOriginal.huella("PROCEDENCIA",sources);
        root.put("sha256",content);root.put("huellaProcedencia",provenance);
        var seal=JSON.createObjectNode();
        seal.put("version","1.0");seal.put("id",com.get("id").asString());seal.put("recibidaEn",com.get("recibidaEn").asString());
        seal.put("tipoRecepcion","CUSTODIA_PUBLICA");seal.put("huellaContenido",content);seal.put("huellaProcedencia",provenance);
        seal.put("emisor","FORMULARIO_PUBLICO_DDN");seal.put("avisoVersion",com.get("avisoVersion").asString());
        byte[] payload=JSON.writeValueAsBytes(seal);
        var signer=Signature.getInstance("Ed25519");signer.initSign(pair.getPrivate());signer.update(payload);
        var c=(ObjectNode)root.get("custodia");
        c.put("keyId","PRUEBA");c.put("payload",Base64.getUrlEncoder().withoutPadding().encodeToString(payload));
        c.put("firma",Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        return new String(JSON.writeValueAsBytes(root),StandardCharsets.UTF_8);
    }
}
