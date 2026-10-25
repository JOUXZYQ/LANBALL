package lanball;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real-TCP checks for LanBall v7 (not part of the game's source count). */
public class V7Test {
    static int passed;
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        passed++;
        System.out.println("ok  " + what);
    }

    /** A test client that speaks the same protocol as LanBall.connect. Records every byte it sends. */
    static final class Client implements Closeable {
        final Socket socket = new Socket();
        DataInputStream in;
        DataOutputStream out;
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        String answer, frame = "";
        int frames;
        Client(int port, String name, String pin, String club, String shirt, boolean create) throws Exception {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout(5000);
            in = new DataInputStream(socket.getInputStream());
            OutputStream real = socket.getOutputStream();
            out = new DataOutputStream(new OutputStream() {
                public void write(int b) throws IOException { sent.write(b); real.write(b); }
                public void write(byte[] b, int o, int l) throws IOException { sent.write(b, o, l); real.write(b, o, l); }
            });
            out.writeUTF(String.join("\t", "LB7", name, club, shirt, create ? "create" : "login"));
            String[] challenge = in.readUTF().split("\t");
            if (challenge.length != 3) { answer = challenge[0]; return; }
            String verifier = Server.verifier(pin, challenge[0]);
            out.writeUTF(challenge[2].equals("new") ? verifier : Server.proof(verifier, challenge[1]));
            answer = in.readUTF();
        }
        String step(int controls, int team, String chat) throws IOException {
            out.writeInt(controls); out.writeInt(team); out.writeUTF(chat);
            frame = in.readUTF();
            frames++;
            return frame;
        }
        public void close() throws IOException { socket.close(); }
    }

    static int freePort() throws IOException { try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); } }

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("lanball-v7");
        // ---------------- damaged files are skipped, not fatal
        Files.writeString(data.resolve("M0-bad.match"), "garbage line without tabs\n");
        Files.writeString(data.resolve("player-broken.profile"), "only\ttwo");
        int port = freePort();
        Server server = new Server(data.toFile(), port);
        check(server.message.contains("M0-bad.match") && server.message.contains("player-broken.profile"),
                "damaged match and profile files are skipped and named; the host still starts");
        check(Files.readString(data.resolve("M0-bad.match")).startsWith("garbage"), "the damaged file is left unchanged");

        // ---------------- registration and login without sending the PIN
        Client asha = new Client(port, "Asha", "123456", "Red Lions", "9", true);
        check(asha.answer.equals("OK"), "a new profile registers");
        check(!new String(asha.sent.toByteArray(), "UTF-8").contains("123456"), "the PIN itself is never sent");
        String profileFile = Files.readString(data.resolve("player-asha.profile"));
        check(!profileFile.contains("123456"), "the PIN is not stored in the profile file");
        Client binu = new Client(port, "Binu", "654321", "Blue Bay", "4", true);
        check(binu.answer.equals("OK"), "a second profile registers");
        asha.close(); binu.close();
        Thread.sleep(300);
        Client wrong = new Client(port, "asha", "999999", "", "", false);
        check(wrong.answer.contains("Incorrect PIN"), "a wrong PIN is refused (" + wrong.answer + ")");
        wrong.close();
        Client nobody = new Client(port, "Nobody", "123456", "", "", false);
        check(nobody.answer.contains("not found"), "an unknown profile is refused");
        nobody.close();
        asha = new Client(port, "asha", "123456", "", "", false);
        check(asha.answer.equals("OK"), "a returning player signs in with the right PIN (any case)");
        Client dup = new Client(port, "Asha", "123456", "", "", false);
        check(dup.answer.contains("already connected"), "the same profile cannot connect twice");
        dup.close();
        binu = new Client(port, "Binu", "654321", "", "", false);
        check(binu.answer.equals("OK"), "the second player signs in");

        // ---------------- logins no longer stall the simulation
        AtomicLong worstGap = new AtomicLong();
        AtomicBoolean watching = new AtomicBoolean(true);
        Thread watcher = new Thread(() -> {
            long last = System.nanoTime();
            while (watching.get()) {
                long tick;
                synchronized (server) { tick = server.previousTick; }
                long now = System.nanoTime();
                if (tick != last) { worstGap.accumulateAndGet(now - last, Math::max); last = tick; }
                Thread.onSpinWait();
            }
        });
        watcher.start();
        ExecutorService logins = Executors.newFixedThreadPool(4);
        List<Future<?>> jobs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int n = i;
            jobs.add(logins.submit(() -> {
                try (Client c = new Client(port, "Guest" + n, "111111", "Guests", "1" + n, true)) { return c.answer; }
            }));
        }
        for (Future<?> job : jobs) check("OK".equals(job.get()), "a guest registers while the game runs");
        logins.shutdown();
        watching.set(false);
        watcher.join();
        check(worstGap.get() < 60_000_000L, "four logins at once never stall the tick (worst gap "
                + worstGap.get() / 1_000_000 + " ms)");

        // ---------------- update rate
        long start = System.nanoTime();
        for (int i = 0; i < 30; i++) { asha.step(0, 0, ""); binu.step(0, 1, ""); Thread.sleep(25); }
        double perSecond = 30 / ((System.nanoTime() - start) / 1e9);
        check(perSecond > 20, String.format("a client gets about %.0f snapshots a second (v4: about 10)", perSecond));

        // ---------------- chat
        asha.step(0, 0, "hello from Asha");
        String frame = binu.step(0, 1, "");
        check(frame.contains("C\tAsha: hello from Asha"), "chat reaches the other player");

        // ---------------- team change clears ready on the server
        asha.step(32, 0, "");
        binu.step(32, 1, "");
        synchronized (server) { check((server.player("asha").input & 32) != 0, "Asha is ready"); }
        asha.step(32, 2, ""); // moves to Watch while still sending ready
        synchronized (server) {
            Server.Player a = server.player("asha");
            check(a.team == 2 && (a.input & 32) == 0, "changing team clears ready on the host for that message");
        }
        asha.step(32, 0, ""); asha.step(32, 0, "");

        // ---------------- start, collision, goal, Elo
        check(server.startRound(60, 1).startsWith("Kick-off"), "the host starts the round");
        String countdownFrame = binu.step(0, 1, "");
        check(countdownFrame.split("\n")[0].split("\t")[7].matches("[123]"), "the snapshot carries a 3-2-1 countdown");
        synchronized (server) {
            Server.Player a = server.player("asha"), b = server.player("binu");
            a.x = b.x = 400; a.y = b.y = 300;
            server.resumeAt = 0;
        }
        Thread.sleep(150);
        synchronized (server) {
            Server.Player a = server.player("asha"), b = server.player("binu");
            check(Math.hypot(a.x - b.x, a.y - b.y) >= 2 * Server.RADIUS - 0.5, "players on the same spot are pushed apart");
            server.ballX = 940; server.ballY = 270; server.velocityX = 600; server.velocityY = 0; server.lastTouch = "asha";
            a.x = 100; a.y = 100; b.x = 100; b.y = 400;
        }
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            asha.step(0, 0, ""); binu.step(0, 1, "");
            synchronized (server) { if (server.phase.equals("Result")) break; }
            Thread.sleep(20);
        }
        synchronized (server) {
            check(server.phase.equals("Result") && server.red == 1, "a goal ends the round at the goal limit (" + server.phase + ")");
            check(server.find("asha").s[5] == 1012 && server.find("binu").s[5] == 988, "Elo: an equal win moves 12 points");
            check(server.find("asha").s[4] == 3 && server.find("asha").s[3] == 1, "points and goals are counted");
        }
        frame = binu.step(0, 1, "");
        check(frame.contains("L\tAsha\t1\t1\t0\t1\t3\t1012\t"), "the league row carries played/won/drawn/goals/points/Elo");
        check(frame.contains("Player of the match: Asha"), "the scorer is named player of the match");
        check(frame.contains("Asha unlocked the First Goal badge") && frame.contains("Asha unlocked the Brick Wall badge"),
                "badges are announced in chat when earned");
        int[] ashaStats = server.find("asha").s;
        check(Server.xp(ashaStats) == 20 + 50 + 15 + 2 * 100 && Server.level(Server.xp(ashaStats)) == 3,
                "XP: played + won + goal + two badges = 285 XP = level 3");
        check(server.find("binu").s[7] == 0 && ashaStats[7] == 1, "win streaks: the winner has 1, the loser 0");
        check(server.kicks >= 0 && frame.split("\n")[0].split("\t").length == 11, "the header carries the sound-event counters");
        String row = String.format(LanBall.ROW, 1, "asha", 3, 1, 1, 0, 1, 3, 1012, 1);
        check(row.startsWith("1   asha ") && row.trim().endsWith("1012    1"), "the client formats the league as aligned columns");

        // ---------------- restart rebuilds the same league
        asha.close(); binu.close();
        server.close();
        Server again = new Server(data.toFile(), port);
        check(again.find("asha").s[5] == 1012 && again.find("binu").s[4] == 0, "a restart rebuilds the same standings and Elo");
        check(again.find("asha").s[6] == server.find("asha").s[6], "a restart rebuilds the same badges");
        again.close();
        System.out.println("V7 tests passed: " + passed + " checks");
    }
}
