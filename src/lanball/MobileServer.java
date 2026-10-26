package lanball;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;

/** A small LAN browser adapter. The Java Match remains the only physics engine. */
public class MobileServer implements HttpHandler {
    final Server host;
    final HttpServer http;
    final ExecutorService workers = Executors.newFixedThreadPool(8);
    final Map<String, Session> sessions = new HashMap<String, Session>();

    static class Session {
        Player player;
        long seen = System.nanoTime();

        Session(Player p) {
            player = p;
        }
    }

    MobileServer(Server host, int port) throws IOException {
        this.host = host;
        http = HttpServer.create(new InetSocketAddress(port), 16);
        http.createContext("/", this);
        http.setExecutor(workers);
        http.start();
    }

    int port() {
        return http.getAddress().getPort();
    }

    public void handle(HttpExchange request) throws IOException {
        try {
            String path = request.getRequestURI().getPath();
            if (request.getRequestMethod().equals("GET")) {
                String file = path.equals("/") ? "index.html" : path.substring(1);
                if (!file.equals("index.html")
                        && !file.equals("game.js")
                        && !file.equals("style.css")) {
                    send(request, 404, "text/plain", "Not found");
                    return;
                }
                try (InputStream in = getClass().getResourceAsStream("/mobile/" + file)) {
                    if (in == null)
                        throw new IOException("Browser files missing. Rebuild the JAR.");
                    String type =
                            file.endsWith(".js")
                                    ? "text/javascript"
                                    : file.endsWith(".css") ? "text/css" : "text/html";
                    send(request, 200, type, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    return;
                }
            }
            if (!request.getRequestMethod().equals("POST")
                    || !"1".equals(request.getRequestHeaders().getFirst("X-LanBall"))) {
                send(request, 403, "text/plain", "Use the LanBall page on this host.");
                return;
            }
            String origin = request.getRequestHeaders().getFirst("Origin");
            if (origin != null
                    && !origin.equals("http://" + request.getRequestHeaders().getFirst("Host"))) {
                send(request, 403, "text/plain", "Wrong origin.");
                return;
            }
            byte[] bytes = request.getRequestBody().readNBytes(2049);
            if (bytes.length > 2048) {
                send(request, 413, "text/plain", "Request too large.");
                return;
            }
            String[] fields = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
            if (path.equals("/join")) {
                join(request, fields);
                return;
            }
            int status = 200;
            String reply;
            synchronized (host) {
                String token = request.getRequestHeaders().getFirst("X-Session");
                Session session = sessions.get(token);
                if (session == null) {
                    status = 401;
                    reply = "Session ended. Sign in again.";
                } else if (path.equals("/leave")) {
                    host.leavePlayer(session.player);
                    sessions.remove(token);
                    reply = "Left the match.";
                } else if (path.equals("/input") && fields.length == 3) {
                    int keys = Integer.parseInt(fields[0]) & 63, team = Integer.parseInt(fields[1]);
                    Player p = session.player;
                    session.seen = System.nanoTime();
                    p.receiveInput(keys, session.seen);
                    String notice = host.match.chooseTeam(p, team);
                    String chat = Files.clean(fields[2], 80);
                    if (!chat.isEmpty()) host.match.say(p.name + ": " + chat);
                    reply = host.snapshot(0) + (notice.isEmpty() ? "" : "N\t" + notice + "\n");
                } else if (path.equals("/profile"))
                    reply = host.league.profile(session.player.name);
                else {
                    status = 400;
                    reply = "Invalid request.";
                }
            }
            // A slow phone must never hold the physics lock while its reply is sent.
            send(request, status, "text/plain", reply);
        } catch (IllegalArgumentException e) {
            send(request, 400, "text/plain", "Invalid input.");
        } catch (IOException e) {
            send(request, 400, "text/plain", Files.clean(e.getMessage(), 160));
        } finally {
            request.close();
        }
    }

    void join(HttpExchange request, String[] p) throws IOException {
        if (p.length != 5
                || !Accounts.validName(p[0])
                || p[1].length() < 6
                || p[1].length() > 40
                || !p[2].matches("[A-Za-z0-9 _-]{2,24}")
                || !p[3].matches("[1-9][0-9]?"))
            throw new IOException("Name 2-16 characters; password 6-40; club 2-24; shirt 1-99.");
        boolean create = p[4].equals("create");
        String[] account = host.accounts.read(p[0]);
        if (create && account != null)
            throw new IOException("Account exists. Untick Create account.");
        if (!create && account == null)
            throw new IOException("Account not found. Tick Create account.");
        String salt = create ? Accounts.random() : account[3];
        try {
            String verifier = Accounts.verifier(p[1].toCharArray(), salt);
            if (!create && !Accounts.same(verifier, account[4]))
                throw new IOException("Incorrect password.");
            String reply;
            synchronized (host) {
                if (!host.running) throw new IOException("Host closed.");
                if (create)
                    account = host.accounts.create(new String[] {p[0], p[2], p[3], salt, verifier});
                Player player = host.joinPlayer(account);
                String token = Accounts.random();
                sessions.put(token, new Session(player));
                reply = token + "\n" + player.name;
            }
            send(request, 200, "text/plain", reply);
        } catch (GeneralSecurityException e) {
            throw new IOException("Password verification unavailable.");
        }
    }

    void expire(long now) {
        Iterator<Session> it = sessions.values().iterator();
        while (it.hasNext()) {
            Session s = it.next();
            if (now - s.seen > 8000000000L) {
                host.leavePlayer(s.player);
                it.remove();
            }
        }
    }

    static void send(HttpExchange request, int status, String type, String text)
            throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        request.getResponseHeaders().set("Cache-Control", "no-store");
        request.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        request.getResponseHeaders()
                .set(
                        "Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self'; connect-src"
                                + " 'self'; frame-ancestors 'none'");
        request.sendResponseHeaders(status, bytes.length);
        request.getResponseBody().write(bytes);
    }

    void close() {
        http.stop(0);
        workers.shutdownNow();
        sessions.clear();
    }
}
