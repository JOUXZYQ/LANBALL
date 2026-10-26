package lanball;

import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

import javax.swing.*;

/** Run against temporary data, never against your real league. */
public class ProjectTest {
    static int count;

    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        count++;
        System.out.println("PASS " + label);
    }

    static class Client implements AutoCloseable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        String frame, reply;

        Client(Server s, String name, boolean create) throws Exception {
            socket = new Socket("127.0.0.1", s.listener.getLocalPort());
            socket.setSoTimeout(4000);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF("LB9\t" + name + "\tCollege\t7\t" + (create ? "create" : "login"));
            String[] ch = in.readUTF().split("\t");
            if (ch.length != 3) throw new IOException(ch[0]);
            String v = Accounts.verifier("demo123".toCharArray(), ch[0]);
            out.writeUTF(ch[2].equals("new") ? v : Accounts.proof(v, ch[1]));
            String answer = in.readUTF();
            if (!answer.equals("OK")) throw new IOException(answer);
        }

        void send(int keys, int team, String command, String chat) throws Exception {
            out.writeInt(keys);
            out.writeInt(team);
            out.writeUTF(command);
            out.writeUTF(chat);
            frame = in.readUTF();
            reply = in.readUTF();
        }

        public void close() throws Exception {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception {
        File root =
                java.nio.file.Files.createTempDirectory(new File(args[0]).toPath(), "project-")
                        .toFile();
        Server s = new Server(root, 0);
        String id = "";
        try (Client a = new Client(s, "Alice", true)) {
            Client b = new Client(s, "Bobby", true);
            check(s.match.connectedCount() == 2, "register and connect two accounts");
            a.send(0, 0, "", "hello\nH\tINVALID");
            b.send(0, 1, "", "");
            a.send(32, -1, "", "");
            b.send(32, -1, "", "");
            check(!a.frame.contains("\nH\tINVALID"), "chat stays within one protocol row");
            check(s.startRound(30, 1).startsWith("Kick-off"), "ready and kickoff");
            b.close();
            for (int i = 0; i < 100 && s.match.find("Bobby").connected; i++) Thread.sleep(10);
            b = new Client(s, "Bobby", false);
            check(s.match.find("Bobby").team == 1, "rejoin retains the match team");
            synchronized (s) {
                s.match.resumeAt = 0;
                s.match.ball.x = 967;
                s.match.ball.y = 270;
                s.match.ball.vx = 500;
                s.match.lastTouch = "Alice";
                s.previousTick = System.nanoTime() - 20000000L;
                s.tick();
            }
            check(s.match.phase.equals("Result"), "goal limit finishes the match");
            check(
                    s.league.totals("Alice").won == 1 && s.league.totals("Bobby").lost == 1,
                    "rejoined match saves both participants");
            check(
                    s.league.totals("Alice").elo == 1012 && s.league.totals("Bobby").elo == 988,
                    "Elo update for equal initial teams");
            id = s.match.resultId;
            check(new File(root, "replays/" + id + ".replay").exists(), "binary replay saved");
            a.send(0, -1, "HISTORY\t0", "");
            check(a.reply.contains(id), "history lists saved match");
            a.send(0, -1, "DETAIL\t" + id, "");
            check(
                    a.reply.contains("goals 1") && a.reply.contains("assists"),
                    "summary includes goals and assists");
            a.send(0, -1, "PROFILE\tAlice", "");
            check(
                    a.reply.contains("Elo 1012") && a.reply.contains(id),
                    "profile from match history");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int total = 1;
            while (bytes.size() < total) {
                a.send(0, -1, "REPLAY\t" + id + "\t" + bytes.size(), "");
                String[] r = a.reply.split("\t", -1);
                check(r[0].equals("REPLAY"), "replay chunk reply");
                total = Integer.parseInt(r[3]);
                bytes.write(Base64.getDecoder().decode(r[4]));
            }
            Replay replay = Replay.read(bytes.toByteArray());
            check(
                    replay.frames.size() > 1 && replay.frames.lastElement().contains("Result"),
                    "downloaded replay parses through full time");
            Arena arena = Arena.parse("A\tPractice\t80\nO\t480\t100\t24\n");
            s.useArena(arena);
            check(new File(root, "pitches/Practice.pitch").exists(), "edited pitch persists");
            s.useSeason("Demo-2");
            check(s.league.records.isEmpty(), "new season has a fresh ladder");
            s.useSeason("Semester-1");
            check(s.league.totals("Alice").won == 1, "old season remains available");
            b.close();
        } finally {
            s.close();
        }
        Server reloaded = new Server(root, 0);
        try {
            check(reloaded.league.totals("Alice").elo == 1012, "history and Elo survive restart");
            try (Client c = new Client(reloaded, "Alice", false)) {
                check(true, "account sign-in survives restart");
            }
        } finally {
            reloaded.close();
        }
        final Match m = new Match();
        Player a = new Player("Alice"), b = new Player("Bobby"), c = new Player("Carol");
        a.team = a.matchTeam = 0;
        b.team = b.matchTeam = 0;
        c.team = c.matchTeam = 1;
        m.players.add(a);
        m.players.add(b);
        m.players.add(c);
        m.clockNow = 100000000000L;
        m.touch(a, m.clockNow);
        m.touch(b, m.clockNow + 1000000000L);
        m.clockNow += 2000000000L;
        m.goal(0);
        check(a.assists == 1 && b.goals == 1, "teammate touch credits one assist");
        SwingUtilities.invokeAndWait(
                () -> {
                    LanBall.darkDefaults();
                    LanBall ui = new LanBall();
                    ui.timer.stop();
                    try {
                        ui.setKey(KeyEvent.VK_D, true);
                        ui.disconnect();
                        check(
                                ui.controls == 0 && !ui.down[KeyEvent.VK_D],
                                "disconnect releases all keys");
                        ui.setKey(KeyEvent.VK_A, true);
                        ui.setKey(KeyEvent.VK_LEFT, true);
                        ui.setKey(KeyEvent.VK_A, false);
                        check((ui.controls & 1) != 0, "aliased movement keys stay independent");
                        ui.myName = "Alice";
                        ui.team.setSelectedIndex(0);
                        ui.pendingTeam = -1;
                        Match lobby = new Match();
                        Player p = new Player("Alice");
                        lobby.players.add(p);
                        ui.show(lobby.frame(System.nanoTime()));
                        check(ui.team.getSelectedIndex() == 2, "selector follows accepted team");
                        ui.pitch.goalSerial = 0;
                        m.goalSerial = 1;
                        ui.pitch.read(m.frame(System.nanoTime()), "Alice");
                        check(
                                ui.pitch.celebration > 0 && ui.pitch.confetti.size() == 90,
                                "goal celebration triggers on new goal");
                    } finally {
                        ui.dispose();
                    }
                });
        System.out.println("PROJECT CHECKS: " + count + " passed");
        System.exit(0);
    }
}
