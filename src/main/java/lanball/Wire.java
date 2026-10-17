package lanball;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

// TLS protects registration, credentials, input, chat and downloaded reports.
final class Wire {
    static final int PORT = 45888;
    static void background(Runnable task) { Thread t = new Thread(task, "lanball"); t.setDaemon(true); t.start(); }
    static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static String digest(byte[] value) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    static String hash(String pin, String salt) throws GeneralSecurityException {
        var spec = new PBEKeySpec(pin.toCharArray(), bytes(salt), 60000, 256);
        return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded());
    }
    static SSLContext identity(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve("host.p12");
        if (!Files.exists(file)) {
            String executable = System.getProperty("os.name").contains("Windows") ? "keytool.exe" : "keytool";
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                    "-genkeypair", "-alias", "host", "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "3650",
                    "-dname", "CN=LanBall", "-storetype", "PKCS12", "-keystore", file.toString(),
                    "-storepass", "lanball-local-key", "-noprompt").redirectErrorStream(true)
                    .redirectOutput(directory.resolve("certificate.log").toFile()).start();
            if (process.waitFor() != 0) throw new IOException("Could not create host certificate. Hosting needs a JDK.");
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) { store.load(in, "lanball-local-key".toCharArray()); }
        Files.writeString(directory.resolve("fingerprint.txt"), digest(store.getCertificate("host").getEncoded()));
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "lanball-local-key".toCharArray());
        SSLContext context = SSLContext.getInstance("TLSv1.3"); context.init(keys.getKeyManagers(), null, null);
        return context;
    }
    static SSLSocket client(String fingerprint) throws Exception {
        if (!fingerprint.matches("[a-fA-F0-9]{64}")) throw new IOException("Paste the host's 64-character fingerprint.");
        X509TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException {
                throw new CertificateException("Client certificates are not used.");
            }
            public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
                try {
                    chain[0].checkValidity();
                    if (!digest(chain[0].getEncoded()).equalsIgnoreCase(fingerprint))
                        throw new CertificateException("Host fingerprint does not match. No credentials sent.");
                } catch (GeneralSecurityException error) { throw new CertificateException(error); }
            }
        };
        SSLContext context = SSLContext.getInstance("TLSv1.3"); context.init(null, new TrustManager[]{trust}, null);
        return (SSLSocket)context.getSocketFactory().createSocket();
    }
    static void send(DataOutputStream out, String value) throws IOException {
        byte[] data = bytes(value); synchronized (out) { out.writeInt(data.length); out.write(data); out.flush(); }
    }
    static String read(DataInputStream in, int maximum) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maximum) throw new IOException("Message exceeds limit.");
        byte[] data = new byte[length]; in.readFully(data); return new String(data, StandardCharsets.UTF_8);
    }
    static String clean(String text, int maximum) {
        String clean=text.replaceAll("[\\p{Cntrl}]", " ").strip();return clean.substring(0,Math.min(maximum,clean.length()));
    }
}
