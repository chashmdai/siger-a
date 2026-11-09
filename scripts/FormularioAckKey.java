import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Genera el par Ed25519 del comprobante institucional (ACK) de SIGER A para el Formulario.
 * Uso: java FormularioAckKey.java <privada.pem> <publica.pem>. No sobrescribe archivos existentes;
 * la privada hereda los permisos del directorio de configuracion privado y nunca se imprime.
 */
public class FormularioAckKey {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Uso: FormularioAckKey <privada.pem> <publica.pem>");
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String priv="-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,"\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n";
        String pub="-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,"\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n";
        Files.writeString(Path.of(args[0]),priv,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        Files.writeString(Path.of(args[1]),pub,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        System.out.println("Par Ed25519 generado. Entrega solo la clave publica al operador del Formulario.");
    }
}
