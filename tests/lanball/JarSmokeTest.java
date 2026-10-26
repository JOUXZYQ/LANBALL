package lanball;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import javax.swing.*;

/** Run with only the release JAR plus this class: catches packaging mistakes. */
public class JarSmokeTest {
    static int count;
    static LanBall ui;

    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        count++;
        System.out.println("PASS " + label);
    }

    static String get(String path, StringBuilder headers) throws IOException {
        HttpURLConnection c =
                (HttpURLConnection) new URL("http://127.0.0.1:45890" + path).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        if (c.getResponseCode() != 200) throw new IOException(path + " -> " + c.getResponseCode());
        if (headers != null) headers.append(c.getHeaderField("Content-Security-Policy"));
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] args) throws Exception {
        String from = LanBall.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        check(from.endsWith(".jar"), "game classes load from the JAR: " + from);
        check(
                LanBall.class.getResource("/mobile/index.html") != null
                        && LanBall.class.getResource("/mobile/game.js") != null
                        && LanBall.class.getResource("/mobile/style.css") != null,
                "phone files are inside the JAR");

        System.setProperty("lanball.data", args[0]);
        SwingUtilities.invokeAndWait(
                () -> {
                    LanBall.darkDefaults();
                    ui = new LanBall();
                    ui.setVisible(true);
                    ui.name.setText("SmokeHost");
                    ui.password.setText("demo123");
                    ui.actionPerformed(new java.awt.event.ActionEvent(ui.host, 0, ""));
                });
        for (int i = 0; i < 200 && !ui.connected; i++) Thread.sleep(25);
        check(ui.connected, "Host button starts the desktop host and signs in");
        check(ui.server.listener.getLocalPort() == 45888, "desktop host uses TCP 45888");

        StringBuilder csp = new StringBuilder();
        String page = get("/", csp);
        check(page.contains("game.js") && page.contains("style.css"), "phone page served");
        check(csp.toString().contains("default-src"), "phone page sends a security policy");
        check(get("/game.js", null).contains("X-LanBall"), "phone script served");
        check(get("/style.css", null).length() > 100, "phone stylesheet served");

        final String[] links = new String[1];
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        links[0] = ui.phoneLinks();
                    } catch (SocketException e) {
                        links[0] = e.toString();
                    }
                });
        check(links[0].contains(":45890/"), "Phones dialog lists the phone address");
        check(!links[0].contains("//169.254."), "Phones dialog hides unreachable link-local addresses");
        System.out.println(links[0]);
        System.out.println("JAR SMOKE CHECKS: " + count + " passed");
        System.exit(0);
    }
}
