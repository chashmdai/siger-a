import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba que una clave privada Ed25519 (PKCS#8, PEM "PRIVATE KEY") corresponde a una publica (SPKI, PEM "PUBLIC KEY"):
 * firma un desafio aleatorio con la privada y lo verifica con la publica. Lee las dos PEM de la entrada estandar
 * (Test-InstanceConfig.ps1, H06r): nunca de argumentos ni de archivos, y no escribe nada. Imprime solo COINCIDE (codigo
 * 0), NO_COINCIDE (1) o INVALIDA (2): nunca las claves. Uso: java -XX:-UsePerfData AckKeyCheck.java &lt; pem
 */
public class AckKeyCheck {
    public static void main(String[] args) {
        String result;
        try {
            String input = new String(System.in.readAllBytes(), StandardCharsets.US_ASCII);
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(block(input, "PRIVATE KEY")));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(block(input, "PUBLIC KEY")));
            byte[] challenge = new byte[64];
            new SecureRandom().nextBytes(challenge);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(challenge);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            result = verifier.verify(signature) ? "COINCIDE" : "NO_COINCIDE";
        } catch (Exception e) {
            result = "INVALIDA";
        }
        System.out.println(result);
        System.exit(result.equals("COINCIDE") ? 0 : result.equals("NO_COINCIDE") ? 1 : 2);
    }

    /** Un solo bloque PEM con esa etiqueta; dos o ninguno es INVALIDA. */
    private static byte[] block(String text, String label) {
        Matcher m = Pattern.compile("-----BEGIN " + label + "-----([A-Za-z0-9+/=\\s]+)-----END " + label + "-----").matcher(text);
        if (!m.find()) throw new IllegalArgumentException("sin bloque");
        byte[] value = Base64.getMimeDecoder().decode(m.group(1));
        if (m.find()) throw new IllegalArgumentException("bloque repetido");
        return value;
    }
}
