package lanball;

import java.io.*;
import java.net.Socket;

/** File-failure and connection cases, using disposable host data. */
public class RecoveryTest {
    public static void main(String[] args) throws Exception {
        File root =
                java.nio.file.Files.createTempDirectory(new File(args[0]).toPath(), "recovery-")
                        .toFile();
        Server s = new Server(root, 0);
        try (ProjectTest.Client a = new ProjectTest.Client(s, "Alice", true);
                ProjectTest.Client b = new ProjectTest.Client(s, "Bobby", true)) {
            a.send(0, 0, "", "");
            b.send(0, 1, "", "");
            a.send(32, -1, "", "");
            b.send(32, -1, "", "");
            s.startRound(30, 1);
            File blocker = new File(root, "replays");
            try (FileOutputStream out = new FileOutputStream(blocker)) {
                out.write(1);
            }
            synchronized (s) {
                s.match.resumeAt = 0;
                s.match.ball.x = 967;
                s.match.ball.y = 270;
                s.match.ball.vx = 500;
                s.match.lastTouch = "Alice";
                s.previousTick = System.nanoTime() - 20000000;
                s.tick();
            }
            ProjectTest.check(
                    s.match.phase.equals("Save failed")
                            && s.pending != null
                            && s.league.history.isEmpty(),
                    "failed save retains result without awarding Elo");
            String id = s.pending.id;
            ProjectTest.check(
                    !s.match.chooseTeam(s.match.find("Alice"), 2).isEmpty(),
                    "teams stay locked while result awaits saving");
            java.nio.file.Files.delete(blocker.toPath());
            s.startRound(30, 1);
            ProjectTest.check(
                    s.match.phase.equals("Result")
                            && s.league.history.size() == 1
                            && s.league.totals("Alice").elo == 1012,
                    "Retry save persists the frozen result once");
            s.finish();
            ProjectTest.check(
                    s.league.history.size() == 1 && s.league.history.get(0).id.equals(id),
                    "repeat save cannot duplicate result");
            a.send(0, -1, "LEAGUE\t2147483647", "");
            a.send(0, -1, "HISTORY\t2147483647", "");
            ProjectTest.check(
                    a.reply.startsWith("HISTORY"), "oversized page request stays connected");
        } finally {
            s.close();
        }
        Server reload = new Server(root, 0);
        try (Socket socket = new Socket("127.0.0.1", reload.listener.getLocalPort())) {
            socket.setSoTimeout(4000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("LB9\tAlice\tCollege\t7\tlogin");
            String[] challenge = in.readUTF().split("\t");
            out.writeUTF(
                    Accounts.proof(
                            Accounts.verifier("incorrect".toCharArray(), challenge[0]),
                            challenge[1]));
            ProjectTest.check(
                    in.readUTF().contains("Incorrect password"), "wrong password is rejected");
        } finally {
            reload.close();
        }
        System.out.println("RECOVERY CHECKS: " + ProjectTest.count + " passed");
    }
}
