package lanball;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;

/** Shared file operations: UTF-8 text and replace only after a complete write. */
public class Files {
    static String read(File file) throws IOException {
        if (file.length() > 2000000) throw new IOException("Text file is too large.");
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    static void write(File file, String text) throws IOException {
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8));
    }

    static void writeBytes(File file, byte[] bytes) throws IOException {
        file.getParentFile().mkdirs();
        File pending = new File(file.getPath() + ".pending");
        java.nio.file.Files.write(pending.toPath(), bytes);
        try {
            java.nio.file.Files.move(
                    pending.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            java.nio.file.Files.move(
                    pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String clean(String s, int max) {
        s = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
