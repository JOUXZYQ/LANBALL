package lanball;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Accounts are local to the host. Password derivation uses Java's standard crypto library. */
public class Accounts {
    final File folder;

    Accounts(File root) {
        folder = new File(root, "accounts");
        folder.mkdirs();
    }

    File file(String name) {
        return new File(folder, name.toLowerCase(Locale.ROOT) + ".account");
    }

    static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_-]{2,16}");
    }

    String[] read(String name) throws IOException {
        File f = file(name);
        if (!f.exists()) return null;
        String[] p = Files.read(f).trim().split("\t");
        if (p.length != 5) throw new IOException("Damaged account file.");
        return p;
    }

    synchronized String[] create(String[] p) throws IOException {
        if (file(p[0]).exists()) throw new IOException("That account already exists. Use Sign in.");
        if (!p[4].matches("[A-Za-z0-9+/]{43}="))
            throw new IOException("Invalid password verifier.");
        Files.write(file(p[0]), String.join("\t", p));
        return p;
    }

    static String random() {
        return UUID.randomUUID().toString();
    }

    static String verifier(char[] password, String salt) throws GeneralSecurityException {
        PBEKeySpec spec =
                new PBEKeySpec(password, salt.getBytes(StandardCharsets.UTF_8), 120000, 256);
        try {
            return Base64.getEncoder()
                    .encodeToString(
                            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                                    .generateSecret(spec)
                                    .getEncoded());
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    static String proof(String verifier, String nonce) throws GeneralSecurityException {
        return Base64.getEncoder()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256")
                                .digest((verifier + nonce).getBytes(StandardCharsets.UTF_8)));
    }

    static boolean same(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
