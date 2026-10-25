package lanball;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.Random;
import java.util.Vector;
import javax.swing.JPanel;

/*
 * Draws the match from host snapshots: floodlit pitch, smooth movement, ball trail, countdown and goal celebrations.
 * Inheritance and overriding (Module 2): Pitch extends JPanel and overrides paintComponent.
 * World size 1000 x 620: a 70-unit scoreboard band, then the 960 x 540 pitch.
 */
public class Pitch extends JPanel {
    static final Color RED = new Color(0xff4d5e), BLUE = new Color(0x3fa9ff), GOLD = new Color(0xffd166);
    static final Color INK = new Color(0x0b1020), MUTED = new Color(0x9fb3d9);
    // What the screen shows for one player. x, y move smoothly towards tx, ty (the host's latest position).
    static class Body { String name, club, shirt; int team, goals; double x, y, tx, ty; boolean ready, connected, seen; }
    final Vector<Body> bodies = new Vector<Body>();
    final Vector<double[]> trail = new Vector<double[]>(), confetti = new Vector<double[]>(); // confetti: x, y, vx, vy, life, colour
    final Random random = new Random();
    String phase = "Offline", me = "", banner = "", reward = "";
    int red, blue, remaining, countdown, goalLimit, kicks = -1, bounces;
    double ballX = 480, ballY = 270, ballTx = 480, ballTy = 270, shake, flash, bannerTime, clock;
    Pitch() { setPreferredSize(new Dimension(1000, 620)); setBackground(INK); setFocusable(true); }
    static int number(String text) { return Integer.parseInt(text); }
    void read(String frame) {
        for (Body b : bodies) b.seen = false;
        for (String row : frame.split("\n")) {
            String[] c = row.split("\t");
            if (c[0].equals("H") && c.length >= 11) header(c);
            if (c[0].equals("M") && c.length > 1 && !c[1].equals(banner)) { banner = c[1]; bannerTime = 3.5; }
            if (!c[0].equals("P") || c.length < 10) continue;
            Body b = body(c[1]);
            boolean jump = b == null || Math.hypot(number(c[3]) - b.x, number(c[4]) - b.y) > 120; // kick-off: no sliding
            if (b == null) { b = new Body(); bodies.add(b); }
            b.name = c[1]; b.team = number(c[2]); b.tx = number(c[3]); b.ty = number(c[4]); b.ready = c[5].equals("true");
            b.connected = c[6].equals("true"); b.shirt = c[7]; b.club = c[8]; b.goals = number(c[9]); b.seen = true;
            if (jump) { b.x = b.tx; b.y = b.ty; }
        }
        for (int i = bodies.size() - 1; i >= 0; i--) if (!bodies.get(i).seen) bodies.remove(i);
    }
    Body body(String name) { for (Body b : bodies) if (b.name.equalsIgnoreCase(name)) return b; return null; }
    // Sounds and effects come from what changed between two snapshots, so every screen reacts to the same events.
    void header(String[] c) {
        int newRed = number(c[3]), newBlue = number(c[4]), newCountdown = number(c[7]), newKicks = number(c[8]);
        if (kicks >= 0) {
            if (newKicks > kicks) Sound.KICK.play();
            if (number(c[9]) > bounces) Sound.BOUNCE.play();
            if (newRed + newBlue > red + blue) celebrate(newRed > red ? 0 : 1);
            if (newCountdown > 0 && newCountdown != countdown) Sound.BEEP.play();
            if (countdown > 0 && newCountdown == 0 && c[1].equals("Playing")) Sound.GO.play();
            if (!c[1].equals(phase) && (c[1].equals("Playing") || c[1].equals("Result"))) Sound.WHISTLE.play();
            Body mine = body(me);
            if (c[1].equals("Result") && !phase.equals("Result") && mine != null && newRed != newBlue
                    && mine.team == (newRed > newBlue ? 0 : 1)) Sound.VICTORY.play();
        }
        phase = c[1]; remaining = number(c[2]); red = newRed; blue = newBlue; countdown = newCountdown;
        kicks = newKicks; bounces = number(c[9]); goalLimit = number(c[10]);
        ballTx = number(c[5]); ballTy = number(c[6]);
        if (Math.hypot(ballTx - ballX, ballTy - ballY) > 150) { ballX = ballTx; ballY = ballTy; trail.clear(); }
    }
    void celebrate(int team) {
        Sound.GOAL.play();
        shake = 14; flash = 1;
        double x = team == 0 ? 960 : 0;
        for (int i = 0; i < 140; i++)
            confetti.add(new double[]{x, 270, (team == 0 ? -1 : 1) * random.nextDouble() * 420,
                    -random.nextDouble() * 520 + 120, 1.6 + random.nextDouble(), random.nextInt(3)});
    }
    void reset() { phase = "Offline"; bodies.clear(); confetti.clear(); trail.clear(); kicks = -1; banner = reward = ""; }
    // Called about 60 times a second by the window's timer: smooth movement, then fade the effects.
    void step(double dt) {
        clock += dt;
        for (Body b : bodies) { b.x += (b.tx - b.x) * Math.min(1, dt * 16); b.y += (b.ty - b.y) * Math.min(1, dt * 16); }
        ballX += (ballTx - ballX) * Math.min(1, dt * 20); ballY += (ballTy - ballY) * Math.min(1, dt * 20);
        trail.add(new double[]{ballX, ballY}); // a resting ball's trail hides under the ball itself
        if (trail.size() > 12) trail.remove(0);
        shake = Math.max(0, shake - dt * 45); flash = Math.max(0, flash - dt * 2.5); bannerTime -= dt;
        for (int i = confetti.size() - 1; i >= 0; i--) {
            double[] p = confetti.get(i);
            p[3] += 600 * dt; p[0] += p[2] * dt; p[1] += p[3] * dt; p[4] -= dt;
            if (p[4] <= 0) confetti.remove(i);
        }
        repaint();
    }
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double scale = Math.min(getWidth() / 1000.0, getHeight() / 620.0);
        g.translate((getWidth() - 1000 * scale) / 2, (getHeight() - 620 * scale) / 2);
        g.scale(scale, scale);
        g.setPaint(new GradientPaint(0, 0, new Color(0x151b3a), 0, 620, INK)); g.fillRect(0, 0, 1000, 620);
        Graphics2D world = (Graphics2D) g.create();
        world.translate(20 + (random.nextDouble() - .5) * shake, 70 + (random.nextDouble() - .5) * shake);
        pitch(world);
        if (phase.equals("Playing") || phase.equals("Result")) match(world); else lobby(world);
        for (double[] p : confetti) {
            alpha(world, Math.min(1, p[4]));
            world.setColor(p[5] == 0 ? GOLD : p[5] == 1 ? Color.WHITE : new Color(0x7cf29a));
            world.fill(new Rectangle2D.Double(p[0], p[1], 6, 9));
        }
        world.dispose();
        alpha(g, 1); scoreboard(g); overlays(g);
        g.dispose();
    }
    void pitch(Graphics2D g) {
        for (int i = 0; i < 12; i++) { g.setColor(new Color(i % 2 == 0 ? 0x1d7a3c : 0x22894a)); g.fillRect(i * 80, 0, 80, 540); }
        g.setPaint(new RadialGradientPaint(480, 270, 620, new float[]{0, 1}, // floodlight glow and dark corners
                new Color[]{new Color(255, 255, 255, 26), new Color(0, 0, 0, 90)}));
        g.fillRect(0, 0, 960, 540);
        g.setColor(new Color(255, 255, 255, 217)); g.setStroke(new BasicStroke(3));
        g.drawRect(8, 8, 944, 524); g.drawLine(480, 8, 480, 532); g.drawOval(410, 200, 140, 140);
        g.drawRect(8, 150, 130, 240); g.drawRect(822, 150, 130, 240); g.drawRect(8, 215, 45, 110); g.drawRect(907, 215, 45, 110);
        g.setColor(Color.WHITE); g.fillOval(475, 265, 10, 10);
        for (int side = 0; side < 2; side++) { // goal nets: Red defends the left goal
            int x = side == 0 ? -22 : 952;
            g.setColor(new Color(255, 255, 255, 30)); g.fillRect(x, 198, 30, 144);
            g.setColor(new Color(255, 255, 255, 64)); g.setStroke(new BasicStroke(1));
            for (int n = 0; n <= 144; n += 12) g.drawLine(x, 198 + n, x + 30, 198 + n);
            for (int n = 0; n <= 30; n += 10) g.drawLine(x + n, 198, x + n, 342);
            g.setColor(side == 0 ? RED : BLUE); g.setStroke(new BasicStroke(5)); g.drawRect(x, 198, 30, 144);
        }
    }
    void match(Graphics2D g) {
        for (int i = 0; i < trail.size(); i++) {
            g.setColor(new Color(255, 255, 255, 8 + 6 * i)); circle(g, trail.get(i)[0], trail.get(i)[1], 3 + i * .45);
        }
        g.setColor(new Color(0, 0, 0, 90)); // shadows
        for (Body b : bodies) if (b.team < 2) g.fill(new Ellipse2D.Double(b.x - 15, b.y - 8, 34, 26));
        g.fill(new Ellipse2D.Double(ballX - 7, ballY - 2, 18, 12));
        for (Body b : bodies) if (b.team < 2) player(g, b);
        g.setPaint(new RadialGradientPaint((float) ballX - 3, (float) ballY - 3, 10, new float[]{0, 1},
                new Color[]{Color.WHITE, new Color(0xc9d3e0)}));
        circle(g, ballX, ballY, 8);
        g.setColor(INK); g.setStroke(new BasicStroke(1.5f)); g.draw(new Ellipse2D.Double(ballX - 8, ballY - 8, 16, 16));
    }
    void player(Graphics2D g, Body b) {
        Color team = b.team == 0 ? RED : BLUE;
        double r = Server.RADIUS;
        boolean mine = b.name.equalsIgnoreCase(me);
        if (mine) { // a pulsing gold ring shows which player is you
            double grow = r + 4 + 3 * Math.sin(clock * 6);
            g.setColor(GOLD); g.setStroke(new BasicStroke(3)); g.draw(new Ellipse2D.Double(b.x - grow, b.y - grow, 2 * grow, 2 * grow));
        }
        g.setPaint(new RadialGradientPaint((float) b.x - 5, (float) b.y - 6, (float) r * 1.4f, new float[]{0, 1},
                new Color[]{team.brighter(), team.darker()}));
        circle(g, b.x, b.y, r);
        g.setColor(b.connected ? Color.WHITE : Color.GRAY); g.setStroke(new BasicStroke(2.5f));
        g.draw(new Ellipse2D.Double(b.x - r, b.y - r, 2 * r, 2 * r));
        text(g, b.shirt, b.x, b.y, 14, Color.WHITE, true);
        String tag = mine ? "YOU" : b.name;
        if (b.goals > 0) tag += " ";
        for (int i = 0; i < b.goals && i < 3; i++) tag += " \u25CF"; // one dot for each goal this match
        double width = tag.length() * 7 + 14;
        g.setColor(new Color(0, 0, 0, 140)); g.fillRoundRect((int) (b.x - width / 2), (int) (b.y - r - 26), (int) width, 18, 9, 9);
        text(g, tag, b.x, b.y - r - 17, 12, mine ? GOLD : Color.WHITE, true);
    }
    void lobby(Graphics2D g) { // the lobby board: who is on which team and who is ready
        g.setColor(new Color(8, 12, 28, 184)); g.fillRoundRect(150, 70, 660, 400, 30, 30);
        if (phase.equals("Offline")) {
            text(g, "LANBALL", 480, 210, 84, GOLD, true);
            text(g, "Wi-Fi football with your friends", 480, 280, 22, Color.WHITE, false);
            text(g, "Host on this computer, or join a friend's game above", 480, 320, 16, MUTED, false);
            return;
        }
        text(g, "LOBBY", 480, 112, 34, GOLD, true);
        String[] titles = {"RED", "BLUE", "WATCHING"};
        Color[] colours = {RED, BLUE, MUTED};
        for (int team = 0; team < 3; team++) {
            double x = 260 + team * 220, y = 165;
            text(g, titles[team], x, y, 22, colours[team], true);
            for (Body b : bodies) {
                if (b.team != team) continue;
                y += 34;
                boolean ready = b.ready && team < 2;
                text(g, (ready ? "\u25CF " : "") + b.name + (b.name.equalsIgnoreCase(me) ? " (you)" : ""), x, y, 17,
                        ready ? GOLD : Color.WHITE, false);
            }
        }
        text(g, "Pick a team, tick READY - the host starts the match", 480, 440, 15, MUTED, false);
    }
    void scoreboard(Graphics2D g) {
        g.setColor(new Color(5, 8, 20, 217)); g.fillRoundRect(330, 8, 340, 54, 27, 27);
        g.setColor(RED); g.fillRoundRect(338, 15, 70, 40, 20, 20);
        g.setColor(BLUE); g.fillRoundRect(592, 15, 70, 40, 20, 20);
        text(g, "RED", 373, 35, 16, Color.WHITE, true);
        text(g, "BLUE", 627, 35, 16, Color.WHITE, true);
        text(g, red + " : " + blue, 500, 30, 30, Color.WHITE, true);
        String time = remaining / 60 + ":" + (remaining % 60 < 10 ? "0" : "") + remaining % 60;
        String line = phase.equals("Playing") ? time + (goalLimit > 0 ? "   first to " + goalLimit : "") : phase.toUpperCase();
        text(g, line, 500, 52, 12, GOLD, true);
    }
    void overlays(Graphics2D g) {
        if (flash > 0) { g.setColor(new Color(255, 255, 255, (int) (flash * 90))); g.fillRect(0, 0, 1000, 620); }
        if (phase.equals("Playing") && countdown > 0) {
            double beat = 1 + .25 * (1 - clock % 1); // each number pops, then settles
            text(g, "" + countdown, 500, 330, (int) (120 * beat), GOLD, true);
        }
        if (bannerTime > 0 && banner.length() > 0 && phase.equals("Playing")) {
            boolean goal = banner.startsWith("GOAL");
            alpha(g, Math.min(1, bannerTime));
            g.setColor(new Color(5, 8, 20, 204)); g.fillRoundRect(200, 520, 600, 50, 25, 25);
            text(g, banner, 500, 545, goal ? 22 : 17, goal ? GOLD : Color.WHITE, true);
            alpha(g, 1);
        }
        if (!phase.equals("Result")) return;
        Body mine = body(me);
        int winner = red > blue ? 0 : blue > red ? 1 : -1;
        String title = mine == null || mine.team == 2 ? "FULL TIME" : winner < 0 ? "DRAW" : mine.team == winner ? "VICTORY" : "DEFEAT";
        g.setColor(new Color(5, 8, 20, 199)); g.fillRect(0, 0, 1000, 620);
        text(g, title, 500, 220, 96, title.equals("VICTORY") ? GOLD : title.equals("DEFEAT") ? MUTED : Color.WHITE, true);
        text(g, banner, 500, 300, 20, Color.WHITE, false);
        text(g, reward, 500, 350, 24, GOLD, true);
        text(g, "Tick READY for a rematch", 500, 420, 16, MUTED, false);
    }
    static void circle(Graphics2D g, double x, double y, double r) { g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)); }
    static void alpha(Graphics2D g, double amount) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, amount))));
    }
    // Centred text with a soft drop shadow.
    static void text(Graphics2D g, String s, double x, double y, int size, Color colour, boolean bold) {
        g.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        FontMetrics metrics = g.getFontMetrics();
        float left = (float) (x - metrics.stringWidth(s) / 2.0), base = (float) (y + (metrics.getAscent() - metrics.getDescent()) / 2.0);
        g.setColor(new Color(0, 0, 0, 128)); g.drawString(s, left + 1.5f, base + 2);
        g.setColor(colour); g.drawString(s, left, base);

    }
}
