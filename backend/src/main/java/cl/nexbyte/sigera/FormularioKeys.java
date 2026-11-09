package cl.nexbyte.sigera;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claves publicas Ed25519 confiables del Formulario Publico para verificar el sello de custodia de
 * los originales externos. Vienen de un archivo del operador ({keyId: PEM}, mismo formato que
 * institucional.comprobante.trusted-keys de SMR), nunca del documento recibido.
 * Sin archivo configurado no hay claves y todo original externo se rechaza. Un archivo configurado
 * pero invalido detiene el arranque: no se degrada en silencio a "sin verificacion".
 */
@Component
public class FormularioKeys {
    private static final int MAX_FILE=65536,MAX_KEYS=20;
    private final Map<String,PublicKey> keys;

    public FormularioKeys(@Value("${siger.formulario-trusted-keys:}")String file){
        this.keys=file.isBlank()?Map.of():load(Path.of(file));
    }

    Map<String,PublicKey> keys(){return keys;}

    private static Map<String,PublicKey> load(Path file){
        try{
            if(Files.size(file)>MAX_FILE)throw new IllegalArgumentException();
            var reader=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
            var tree=reader.readTree(Files.readString(file));
            if(!tree.isObject()||tree.isEmpty()||tree.size()>MAX_KEYS)throw new IllegalArgumentException();
            var out=new HashMap<String,PublicKey>();
            for(var entry:tree.properties()){
                if(!entry.getKey().matches("[A-Za-z0-9_-]{1,64}")||!entry.getValue().isString())throw new IllegalArgumentException();
                out.put(entry.getKey(),publicPem(entry.getValue().asString()));
            }
            return Map.copyOf(out);
        }catch(Exception e){
            throw new IllegalStateException("Archivo de claves confiables del Formulario invalido o ilegible (siger.formulario-trusted-keys).");
        }
    }
    private static PublicKey publicPem(String pem)throws Exception{
        String body=pem.strip();
        if(!body.startsWith("-----BEGIN PUBLIC KEY-----")||!body.endsWith("-----END PUBLIC KEY-----"))throw new IllegalArgumentException();
        body=body.substring(26,body.length()-24);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(body)));
    }
}
