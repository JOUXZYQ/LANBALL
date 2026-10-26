package lanball;

import java.io.*;
import java.net.*;
import java.net.http.*;

/** Real HTTP browser protocol against the same host as a real desktop TCP connection. */
public class MobileTest {
    static HttpClient client = HttpClient.newHttpClient();
    static String base, token = "";

    static HttpResponse<String> request(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("X-LanBall", "1")
                        .header("X-Session", token)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public static void main(String[] args) throws Exception {
        File root =
                java.nio.file.Files.createTempDirectory(new File(args[0]).toPath(), "mobile-")
                        .toFile();
        Server host = new Server(root, 0);
        host.mobile = new MobileServer(host, 0);
        base = "http://127.0.0.1:" + host.mobile.port();
        try (ProjectTest.Client desktop = new ProjectTest.Client(host, "Desktop", true)) {
            HttpResponse<String> page =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            ProjectTest.check(
                    page.statusCode() == 200 && page.body().contains("joinForm"),
                    "mobile page served from Java resources");
            HttpResponse<String> join = request("/join", "Phone\ndemo123\nCollege\n9\ncreate");
            token = join.body().split("\n")[0];
            ProjectTest.check(
                    join.statusCode() == 200 && host.match.connectedCount() == 2,
                    "phone and desktop share one roster");
            desktop.send(0, 0, "", "");
            desktop.send(32, -1, "", "");
            request("/input", "0\n1\n");
            request("/input", "32\n-1\nHello from phone");
            ProjectTest.check(
                    host.startRound(30, 1).startsWith("Kick-off"),
                    "mixed clients can ready and start");
            synchronized (host) {
                host.match.resumeAt = 0;
            }
            double before = host.match.find("Phone").x;
            request("/input", "1\n-1\n");
            Thread.sleep(150);
            request("/input", "0\n-1\n");
            ProjectTest.check(
                    host.match.find("Phone").x < before - 10,
                    "phone movement reaches authoritative physics");
            request("/input", "17\n-1\n");
            ProjectTest.check(
                    (host.match.find("Phone").input & 17) == 17,
                    "move and kick can be held together");
            Thread.sleep(600);
            ProjectTest.check(
                    !host.match.find("Phone").holds(1, System.nanoTime()),
                    "stale phone input expires");
            request("/leave", "");
            ProjectTest.check(
                    host.match.find("Phone").input == 0 && !host.match.find("Phone").connected,
                    "leave clears input and reserves running match slot");
            join = request("/join", "Phone\ndemo123\nCollege\n9\nlogin");
            token = join.body().split("\n")[0];
            ProjectTest.check(
                    join.statusCode() == 200 && host.match.find("Phone").team == 1,
                    "phone reconnect retains its team");
            synchronized (host) {
                host.match.ball.x = -7;
                host.match.ball.y = 270;
                host.match.ball.vx = -500;
                host.match.lastTouch = "Phone";
                host.previousTick = System.nanoTime() - 20000000;
                host.tick();
            }
            String result = request("/input", "0\n-1\n").body();
            ProjectTest.check(
                    result.contains("H\tResult") && result.contains("BLUE GOAL"),
                    "phone receives score and goal celebration event");
            ProjectTest.check(
                    request("/profile", "").body().contains("Elo 1012"),
                    "phone winner receives saved Elo and profile");
            synchronized (host) {
                host.mobile.expire(System.nanoTime() + 9000000000L);
            }
            ProjectTest.check(
                    request("/input", "0\n-1\n").statusCode() == 401,
                    "abandoned mobile session expires");
            HttpResponse<String> bad =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/join"))
                                    .POST(HttpRequest.BodyPublishers.ofString("anything"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            ProjectTest.check(bad.statusCode() == 403, "cross-site form requests rejected");
        } finally {
            host.close();
        }
        System.out.println("MOBILE CHECKS: " + ProjectTest.count + " passed");
    }
}
