import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Firma de los instaladores de Selah (Ed25519).
 *
 *   java tools/Firma.java generar ~/.config/selah          crea la llave privada y muestra la pública
 *   java tools/Firma.java firmar Selah-2.3.0.msi LLAVE     crea Selah-2.3.0.msi.sig
 *   java tools/Firma.java verificar Selah-2.3.0.msi PUBLICA
 *
 * Se firma "proyecta-msi-v1:" + SHA-256 del archivo (en hexadecimal). La app hace lo mismo al
 * descargar una actualización y la rechaza si la firma no corresponde a la llave pública incluida.
 * La llave privada NUNCA va al repositorio: queda en ~/.config/selah y hay que respaldarla.
 */
public class Firma {
    static final String PREFIX = "proyecta-msi-v1:";

    public static void main(String[] a) throws Exception {
        switch (a.length > 0 ? a[0] : "") {
            case "generar" -> {
                Path dir = Path.of(a[1]);
                Files.createDirectories(dir);
                Path key = dir.resolve("firma-ed25519.key");
                if (Files.exists(key)) { System.err.println("Ya existe " + key + " (no se reemplaza)"); System.exit(1); }
                KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                Files.writeString(key, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n");
                try { Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------")); } catch (Exception ignored) {}
                System.out.println(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
            }
            case "firmar" -> {
                byte[] pk = Base64.getDecoder().decode(Files.readString(Path.of(a[2])).trim());
                PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pk));
                Signature s = Signature.getInstance("Ed25519");
                s.initSign(key);
                s.update((PREFIX + sha256(Path.of(a[1]))).getBytes());
                Path sig = Path.of(a[1] + ".sig");
                Files.writeString(sig, Base64.getEncoder().encodeToString(s.sign()) + "\n");
                System.out.println("Firmado: " + sig);
            }
            case "verificar" -> {
                byte[] pub = Base64.getDecoder().decode(a[2].trim());
                PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(pub));
                Signature s = Signature.getInstance("Ed25519");
                s.initVerify(key);
                s.update((PREFIX + sha256(Path.of(a[1]))).getBytes());
                boolean ok = s.verify(Base64.getDecoder().decode(Files.readString(Path.of(a[1] + ".sig")).trim()));
                System.out.println(ok ? "FIRMA VÁLIDA" : "FIRMA INVÁLIDA");
                System.exit(ok ? 0 : 2);
            }
            default -> { System.err.println("Uso: generar DIR | firmar ARCHIVO LLAVE | verificar ARCHIVO PUBLICA"); System.exit(1); }
        }
    }

    static String sha256(Path f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
