package lanball;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * A real 2v2 match: the real host (in a LanBall window, player "Asha", Red) plus three bot clients over TCP.
 * Every player, including Asha, is steered by the same simple bot. Args: frameFolder [live]
 * Without "live" the window stays hidden and its content is painted into PNG frames (no desktop capture).
 */
public class Demo {
    // Simple bot: get behind the ball (relative to the goal it attacks), then run through it and kick.
    static int brain(double x, double y, double bx, double by, int team, boolean defender, long seed) {
        double goalX = team == 0 ? 960 : 0, ownX = team == 0 ? 0 : 960;
        double gx = goalX - bx, gy = 270 - by, gl = Math.hypot(gx, gy);
        gx /= gl; gy /= gl;
        double tx = bx - gx * 24, ty = by - gy * 24;
        boolean behind = (bx - x) * gx + (by - y) * gy > 0;
        if (!behind) { tx = bx - gx * 40; ty = by + (y < by ? -34 : 34); } // go round the ball
        if (defender && Math.abs(bx - ownX) > 430) { tx = (bx + ownX * 2) / 3; ty = (by + 270) / 2; } // hold back
        tx += 18 * Math.sin(seed / 700.0); ty += 18 * Math.cos(seed / 900.0); // a little human wobble
        int bits = 32;
        if (tx < x - 6) bits |= 1; if (tx > x + 6) bits |= 2;
        if (ty < y - 6) bits |= 4; if (ty > y + 6) bits |= 8;
        if (behind && Math.hypot(bx - x, by - y) < 38) bits |= 16;
        return bits;
    }

    static class Bot implements Runnable {
        final String name; final int team; final boolean defender;
        Bot(String name, int team, boolean defender) { this.name = name; this.team = team; this.defender = defender; }
        public void run() {
            try {
                DataInputStream in = null; DataOutputStream out = null;
                for (String mode : new String[]{"login", "create"}) { // a bot keeps its profile between sessions
                    Socket s = new Socket("127.0.0.1", Server.PORT);
                    in = new DataInputStream(s.getInputStream());
                    out = new DataOutputStream(s.getOutputStream());
                    out.writeUTF("LB7\t" + name + "\t" + (team == 0 ? "Red Lions" : "Blue Bay") + "\t" + (team == 0 ? "7" : name.length())
                            + "\t" + mode);
                    String[] c = in.readUTF().split("\t");
                    if (c.length != 3) { s.close(); continue; }
                    String v = Server.verifier("111111", c[0]);
                    out.writeUTF(c[2].equals("new") ? v : Server.proof(v, c[1]));
                    if (in.readUTF().equals("OK")) break;
                }
                double x = 0, y = 0, bx = 480, by = 270;
                String said = "";
                while (true) {
                    out.writeInt(brain(x, y, bx, by, team, defender, System.currentTimeMillis() + name.hashCode()));
                    out.writeInt(team); out.writeUTF(said); said = "";
                    for (String row : in.readUTF().split("\n")) {
                        String[] f = row.split("\t");
                        if (f[0].equals("H")) { bx = Double.parseDouble(f[5]); by = Double.parseDouble(f[6]); }
                        if (f[0].equals("P") && f[1].equals(name)) { x = Double.parseDouble(f[3]); y = Double.parseDouble(f[4]); }
                    }
                    Thread.sleep(25);
                }
            } catch (Exception ended) { }
        }
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        boolean live = args.length > 1 && args[1].equals("live");
        System.setProperty("lanball.data", args.length > 2 ? args[2] : Files.createTempDirectory("lanball-demo").toString());
        LanBall[] made = new LanBall[1];
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            made[0] = new LanBall();
            made[0].name.setText("Asha"); made[0].create.setSelected(!new File(System.getProperty("lanball.data"), "player-asha.profile").exists()); made[0].pin.setText("123456"); made[0].club.setText("Red Lions"); made[0].shirt.setText("9");
            made[0].seconds.setText("75"); made[0].target.setText("3");
            if (live) made[0].setVisible(true); else { Sound.muted = true; made[0].addNotify(); made[0].validate(); }
            made[0].actionPerformed(new ActionEvent(made[0].host, 0, ""));
        } });
        LanBall game = made[0];
        while (game.server == null || game.pitch.phase.equals("Offline")) Thread.sleep(50);
        Server.background(new Bot("Chitra", 0, true));
        Server.background(new Bot("Binu", 1, false));
        Server.background(new Bot("Dev", 1, true));
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { game.ready.setSelected(true); game.updateInput(); } });
        long chatAt = System.currentTimeMillis() + 2500;
        int frame = 0, afterEnd = 0;
        long startAt = System.currentTimeMillis() + 2500, next = System.currentTimeMillis(), stopAt = Long.MAX_VALUE;
        boolean started = false;
        while (System.currentTimeMillis() < stopAt) {
            Server server = game.server;
            if (!started && System.currentTimeMillis() > startAt) {
                SwingUtilities.invokeAndWait(new Runnable() { public void run() {
                    game.actionPerformed(new ActionEvent(game.start, 0, "")); } });
                started = game.pitch.phase.equals("Playing") || server.phase.equals("Playing");
            }
            if (chatAt > 0 && System.currentTimeMillis() > chatAt) { game.say("good luck everyone!"); chatAt = 0; }
            double x, y, bx, by;
            synchronized (server) {
                Server.Player me = server.player("Asha");
                x = me == null ? 0 : me.x; y = me == null ? 0 : me.y; bx = server.ballX; by = server.ballY;
                if (started && server.phase.equals("Result") && stopAt == Long.MAX_VALUE) stopAt = System.currentTimeMillis() + 4500;
            }
            final int bits = brain(x, y, bx, by, 0, false, System.currentTimeMillis()) & ~32;
            SwingUtilities.invokeAndWait(new Runnable() { public void run() {
                game.keys = server.phase.equals("Result") ? 0 : bits; game.updateInput(); } });
            afterEnd++; // loop counter: keep every 6th frame, and every frame while goal confetti flies
            if (!live && (afterEnd % 6 == 0 || game.pitch.confetti.size() > 60)) {
                final BufferedImage image = new BufferedImage(game.getContentPane().getWidth(), game.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                SwingUtilities.invokeAndWait(new Runnable() { public void run() {
                    game.validate(); Graphics2D g = image.createGraphics(); game.getContentPane().paint(g); g.dispose(); } });
                ImageIO.write(image, "png", new File(dir, String.format("f%05d.png", frame++)));
            }
            next += 1000 / 15;
            Thread.sleep(Math.max(1, next - System.currentTimeMillis()));
            if (afterEnd > 15 * 120) break; // safety: two minutes
        }
        System.out.println("frames " + frame + ", final: " + game.status.getText());
        if (!live) System.exit(0);
    }
}
