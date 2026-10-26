package lanball;

import java.awt.*;
import java.util.Random;
import java.util.Vector;

import javax.swing.JPanel;

/** The view only draws snapshots. It never decides goals or moves the real ball. */
public class Pitch extends JPanel {
    static final Color BACK = new Color(0x10151f),
            PANEL = new Color(0x1b2432),
            TEXT = new Color(0xe8eef6);
    static final Color RED = new Color(0xff6678),
            BLUE = new Color(0x63b5ff),
            GOLD = new Color(0xffd27b),
            MUTED = new Color(0x96a6bb);

    static class Dot {
        String name, shirt;
        int team, goals, assists, x, y;
        double drawX, drawY;
        boolean ready, connected;
    }

    Vector<Dot> dots = new Vector<Dot>();
    final Vector<double[]> confetti = new Vector<double[]>();
    Arena arena = new Arena();
    String phase = "Offline", me = "", goalText = "";
    int remaining,
            red,
            blue,
            ballX = 480,
            ballY = 270,
            countdown,
            goalLimit,
            goalSerial = -1,
            goalTeam;
    double drawBallX = 480, drawBallY = 270, celebration;
    boolean smooth = true;

    Pitch() {
        setPreferredSize(new Dimension(880, 575));
        setBackground(BACK);
        setFocusable(true);
    }

    Dot mine() {
        for (Dot d : dots) if (d.name.equalsIgnoreCase(me)) return d;
        return null;
    }

    void read(String frame, String myName) {
        Vector<Dot> next = new Vector<Dot>();
        String[] h = null;
        String arenaText = "";
        int serial = goalSerial, team = 0;
        String call = "";
        for (String line : frame.split("\n")) {
            String[] c = line.split("\t", -1);
            if (c[0].equals("H")) h = c;
            if (c[0].equals("A") || c[0].equals("O")) arenaText += line + "\n";
            if (c[0].equals("G")) {
                if (c.length != 4) throw new IllegalArgumentException();
                serial = Integer.parseInt(c[1]);
                team = Integer.parseInt(c[2]);
                call = c[3];
            }
            if (c[0].equals("P")) {
                if (c.length != 10) throw new IllegalArgumentException("Invalid player row");
                Dot d = new Dot();
                d.name = c[1];
                d.team = Integer.parseInt(c[2]);
                d.x = Integer.parseInt(c[3]);
                d.y = Integer.parseInt(c[4]);
                d.ready = c[5].equals("true");
                d.connected = c[6].equals("true");
                d.shirt = c[7];
                d.goals = Integer.parseInt(c[8]);
                d.assists = Integer.parseInt(c[9]);
                if (!Accounts.validName(d.name) || d.team < 0 || d.team > 2)
                    throw new IllegalArgumentException("Invalid player");
                d.drawX = d.x;
                d.drawY = d.y;
                for (Dot old : dots)
                    if (old.name.equals(d.name)
                            && Math.hypot(old.drawX - d.x, old.drawY - d.y) < 100) {
                        d.drawX = old.drawX;
                        d.drawY = old.drawY;
                    }
                next.add(d);
            }
        }
        if (h == null || h.length != 9) throw new IllegalArgumentException("Missing header");
        int[] n = new int[7];
        for (int i = 0; i < 7; i++) n[i] = Integer.parseInt(h[i + 2]);
        Arena nextArena;
        try {
            nextArena = Arena.parse(arenaText);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException(e);
        }
        if (goalSerial >= 0 && serial > goalSerial) celebrate(team);
        goalSerial = serial;
        goalTeam = team;
        goalText = call;
        phase = h[1];
        remaining = n[0];
        red = n[1];
        blue = n[2];
        ballX = n[3];
        ballY = n[4];
        countdown = n[5];
        goalLimit = n[6];
        dots = next;
        arena = nextArena;
        me = myName;
        if (Math.hypot(ballX - drawBallX, ballY - drawBallY) > 100) {
            drawBallX = ballX;
            drawBallY = ballY;
        }
    }

    void clear() {
        phase = "Offline";
        dots.clear();
        confetti.clear();
        celebration = 0;
        red = blue = remaining = countdown = 0;
        goalSerial = -1;
        ballX = 480;
        ballY = 270;
        drawBallX = 480;
        drawBallY = 270;
        goalText = "";
        repaint();
    }

    void celebrate(int team) {
        celebration = 2.1;
        confetti.clear();
        Random random = new Random();
        for (int i = 0; i < 90; i++)
            confetti.add(
                    new double[] {
                        480,
                        180,
                        (random.nextDouble() - .5) * 420,
                        -60 - random.nextDouble() * 250,
                        random.nextDouble() * 3
                    });
    }

    void animate(double dt) {
        celebration = Math.max(0, celebration - dt);
        double blend = smooth ? 1 - Math.exp(-40 * dt) : 1;
        for (Dot d : dots) {
            d.drawX += (d.x - d.drawX) * blend;
            d.drawY += (d.y - d.drawY) * blend;
        }
        drawBallX += (ballX - drawBallX) * blend;
        drawBallY += (ballY - drawBallY) * blend;
        if (celebration > 0)
            for (double[] c : confetti) {
                c[3] += 250 * dt;
                c[0] += c[2] * dt;
                c[1] += c[3] * dt;
            }
        else confetti.clear();
        repaint();
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = Math.min(getWidth() / 1000.0, getHeight() / 650.0);
        g.translate((getWidth() - 1000 * scale) / 2, (getHeight() - 650 * scale) / 2);
        g.scale(scale, scale);
        g.setColor(PANEL);
        g.fillRoundRect(240, 4, 520, 65, 20, 20);
        text(g, "RED", 300, 31, 14, RED);
        text(g, "BLUE", 700, 31, 14, BLUE);
        text(g, red + "  :  " + blue, 500, 33, 28, TEXT);
        text(
                g,
                phase.equals("Playing")
                        ? String.format(
                                "%d:%02d  /  FIRST TO %d",
                                remaining / 60, remaining % 60, goalLimit)
                        : phase.toUpperCase(),
                500,
                56,
                12,
                MUTED);
        g.translate(20, 82);
        drawField(g);
        if (phase.equals("Playing") || phase.equals("Result") || phase.equals("Save failed"))
            drawPlayers(g);
        else drawLobby(g);
        if (celebration > 0) {
            g.setColor(new Color(8, 13, 22, 190));
            g.fillRoundRect(155, 165, 650, 150, 28, 28);
            text(g, "GOAL!", 480, 230, 64, goalTeam == 0 ? RED : BLUE);
            text(g, goalText, 480, 276, 17, TEXT);
            for (double[] c : confetti) {
                g.setColor(c[4] < 1 ? GOLD : c[4] < 2 ? TEXT : goalTeam == 0 ? RED : BLUE);
                g.fillRect((int) c[0], (int) c[1], 5, 9);
            }
        } else if (phase.equals("Result") || phase.equals("Save failed")) {
            Dot p = mine();
            int winner = red > blue ? 0 : blue > red ? 1 : -1;
            String title =
                    phase.equals("Save failed")
                            ? "RESULT PENDING"
                            : p == null || p.team == 2
                                    ? "FULL TIME"
                                    : winner < 0
                                            ? "DRAW"
                                            : p.team == winner ? "VICTORY" : "GOOD GAME";
            g.setColor(new Color(8, 13, 22, 215));
            g.fillRoundRect(150, 145, 660, 220, 24, 24);
            text(g, title, 480, 215, 48, GOLD);
            text(g, "RED " + red + "   -   " + blue + " BLUE", 480, 260, 25, TEXT);
            text(
                    g,
                    p == null
                            ? "Open History for the match summary"
                            : p.goals + " goals  /  " + p.assists + " assists",
                    480,
                    297,
                    17,
                    MUTED);
            text(
                    g,
                    phase.equals("Save failed")
                            ? "Host: retry saving before the next match"
                            : "Mark Ready for a rematch. History includes the replay.",
                    480,
                    335,
                    15,
                    MUTED);
        } else if (phase.equals("Playing") && countdown > 0) {
            text(g, "" + countdown, 480, 270, 100, GOLD);
        }
        g.dispose();
    }

    void drawField(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x20483f), 960, 540, new Color(0x142f2c)));
        g.fillRoundRect(0, 0, 960, 540, 14, 14);
        for (int i = 0; i < 12; i += 2) {
            g.setColor(new Color(255, 255, 255, 5));
            g.fillRect(i * 80, 0, 80, 540);
        }
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(0x648c7b));
        g.drawRect(8, 8, 944, 524);
        g.drawLine(480, 8, 480, 532);
        g.drawOval(410, 200, 140, 140);
        g.fillOval(476, 266, 8, 8);
        g.drawRect(8, 150, 120, 240);
        g.drawRect(832, 150, 120, 240);
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? -12 : 952;
            g.setColor(side == 0 ? RED : BLUE);
            g.drawRect(x, 270 - arena.goalHalf, 20, arena.goalHalf * 2);
            for (int y = 270 - arena.goalHalf; y <= 270 + arena.goalHalf; y += 12)
                g.drawLine(x, y, x + 20, y);
        }
        for (Circle c : arena.bumpers) {
            g.setColor(new Color(0x374b56));
            g.fillOval(
                    (int) (c.x - c.radius),
                    (int) (c.y - c.radius),
                    (int) c.radius * 2,
                    (int) c.radius * 2);
            g.setColor(MUTED);
            g.drawOval(
                    (int) (c.x - c.radius),
                    (int) (c.y - c.radius),
                    (int) c.radius * 2,
                    (int) c.radius * 2);
        }
    }

    void drawPlayers(Graphics2D g) {
        for (Dot d : dots)
            if (d.team < 2) {
                double x = d.drawX, y = d.drawY;
                if (d.name.equalsIgnoreCase(me)) {
                    g.setColor(GOLD);
                    g.setStroke(new BasicStroke(2));
                    g.drawOval((int) x - 21, (int) y - 21, 42, 42);
                }
                g.setColor(new Color(0, 0, 0, 70));
                g.fillOval((int) x - 17, (int) y - 8, 36, 30);
                g.setColor(!d.connected ? new Color(0x52636b) : d.team == 0 ? RED : BLUE);
                g.fillOval((int) x - 16, (int) y - 16, 32, 32);
                text(g, d.shirt, x, y + 5, 13, BACK);
                text(
                        g,
                        d.name + (d.connected ? "" : " (away)"),
                        x,
                        y - 26,
                        12,
                        d.connected ? TEXT : MUTED);
            }
        g.setColor(new Color(0, 0, 0, 75));
        g.fillOval((int) drawBallX - 8, (int) drawBallY - 3, 18, 15);
        g.setColor(TEXT);
        g.fillOval((int) drawBallX - 8, (int) drawBallY - 8, 16, 16);
        g.setColor(BACK);
        g.drawOval((int) drawBallX - 8, (int) drawBallY - 8, 16, 16);
    }

    void drawLobby(Graphics2D g) {
        g.setColor(new Color(10, 17, 27, 220));
        g.fillRoundRect(115, 80, 730, 370, 24, 24);
        if (phase.equals("Offline")) {
            text(g, "LANBALL", 480, 220, 66, TEXT);
            text(g, "A small pitch. Your team. One LAN.", 480, 270, 20, MUTED);
            text(g, "Create an account, then Host or Join", 480, 330, 17, GOLD);
            return;
        }
        text(g, arena.name.toUpperCase(), 480, 122, 22, TEXT);
        String[] titles = {"RED", "BLUE", "WATCH"};
        Color[] colors = {RED, BLUE, MUTED};
        for (int team = 0; team < 3; team++) {
            int x = 245 + team * 235, y = 175;
            text(g, titles[team], x, y, 17, colors[team]);
            for (Dot d : dots)
                if (d.team == team && d.connected) {
                    y += 32;
                    text(
                            g,
                            d.name + (d.ready && team < 2 ? "  READY" : ""),
                            x,
                            y,
                            15,
                            d.ready ? GOLD : TEXT);
                }
        }
        text(g, "Choose a team. Mark Ready. Host starts the match.", 480, 421, 15, MUTED);
    }

    static void text(Graphics2D g, String text, double x, double y, int size, Color color) {
        g.setFont(new Font("SansSerif", size >= 22 ? Font.BOLD : Font.PLAIN, size));
        g.setColor(color);
        g.drawString(text, (float) (x - g.getFontMetrics().stringWidth(text) / 2.0), (float) y);
    }
}
