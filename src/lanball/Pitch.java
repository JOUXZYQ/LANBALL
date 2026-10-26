package lanball;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import javax.imageio.ImageIO;
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
    // Pitch-only colours, shared with the phone page (game.js).
    static final Color DISC_RED = new Color(0xe44842),
            DISC_BLUE = new Color(0x3e76dd),
            CREAM = new Color(0xf5efe5);

    static class Dot {
        String name, shirt;
        int team, goals, assists, x, y;
        double drawX, drawY, heading, stride, sway;
        boolean ready, connected, kick;
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
    // Shared by every Pitch (match, replay, editor); rebuilt only when the goal width changes.
    static BufferedImage stadium;
    static int stadiumGoal = -1;
    static final BufferedImage grass = loadImage("turf.jpg"), playersImage = loadImage("players.png");
    static final BufferedImage bodyShadow = bodyShadow();
    static final BufferedImage[] discs = new BufferedImage[3];

    static BufferedImage loadImage(String name) {
        try (var in = Pitch.class.getResourceAsStream("/mobile/" + name)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null; // A plain pitch still works if an asset was omitted from a manual build.
        }
    }

    Pitch() {
        setPreferredSize(new Dimension(880, 510));
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
            if (c[0].equals("K") && c.length == 2)
                for (Dot d : next) if (d.name.equals(c[1])) d.kick = true;
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
                d.heading = d.team == 0 ? Math.PI / 2 : -Math.PI / 2;
                for (Dot old : dots)
                    if (old.name.equals(d.name)
                            && Math.hypot(old.drawX - d.x, old.drawY - d.y) < 100) {
                        d.drawX = old.drawX;
                        d.drawY = old.drawY;
                        d.heading = old.heading;
                        d.stride = old.stride;
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
            double dx = d.x - d.drawX, dy = d.y - d.drawY, travel = Math.hypot(dx, dy) * blend;
            if (travel > .15) {
                double aim = Math.atan2(dy, dx) + Math.PI / 2;
                d.heading += Math.atan2(Math.sin(aim - d.heading), Math.cos(aim - d.heading)) * Math.min(1, dt * 18);
            }
            d.stride += travel * .18;
            d.sway = travel > .15 ? Math.sin(d.stride) * .045 : 0;
            if (d.kick) d.heading = Math.atan2(ballY - d.drawY, ballX - d.drawX) + Math.PI / 2;
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
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double scale = Math.min(getWidth() / 1000.0, getHeight() / 640.0);
        g.translate((getWidth() - 1000 * scale) / 2, (getHeight() - 640 * scale) / 2);
        g.scale(scale, scale);
        g.translate(20, 80);
        drawField(g);
        if (phase.equals("Playing") || phase.equals("Result") || phase.equals("Save failed"))
            drawPlayers(g);
        else drawLobby(g);
        g.translate(0, -64);
        drawScore(g);
        g.translate(0, 64);
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

    /**
     * Turf, markings, goals and lights, baked once at double size. game.js draws the same stadium
     * for phones, so keep the two in step.
     */
    BufferedImage stadium() {
        int goal = arena.goalHalf;
        if (stadium != null && stadiumGoal == goal) return stadium;
        BufferedImage stadium = new BufferedImage(2000, 1160, BufferedImage.TYPE_INT_RGB);
        Graphics2D f = stadium.createGraphics();
        f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        f.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        f.scale(2, 2);
        f.translate(20, 20);
        f.setColor(new Color(0x35482c));
        f.fillRect(0, 0, 960, 540);
        if (grass != null) f.drawImage(grass, 0, 0, 960, 540, null);
        f.setColor(new Color(11, 32, 27, 64));
        f.fillRect(0, 0, 960, 540);
        // Dark boards round the turf.
        f.setColor(new Color(0x0b0d0a));
        Area boards = new Area(new Rectangle(-20, -20, 1000, 580));
        boards.subtract(new Area(new Rectangle(0, 0, 960, 540)));
        f.fill(boards);
        f.setStroke(new BasicStroke(2));
        f.setColor(new Color(0x3b4034));
        f.drawRect(-17, -17, 994, 574);
        f.setPaint(
                new RadialGradientPaint(
                        new Point2D.Float(600, 200),
                        750,
                        new Point2D.Float(700, 100),
                        new float[] {.09f, 1},
                        new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 136)},
                        MultipleGradientPaint.CycleMethod.NO_CYCLE));
        f.fillRect(0, 0, 960, 540);
        // Markings: touchline, halfway, centre circle, boxes, penalty arcs and corner arcs.
        f.setColor(new Color(239, 239, 223, 172));
        f.setStroke(new BasicStroke(1.6f));
        f.drawRect(3, 3, 954, 534);
        f.drawLine(480, 3, 480, 537);
        f.drawOval(410, 200, 140, 140);
        f.drawRect(3, 155, 105, 230);
        f.drawRect(852, 155, 105, 230);
        f.drawRect(3, 206, 38, 128);
        f.drawRect(919, 206, 38, 128);
        double arc = Math.toDegrees(Math.acos(32 / 70.0));
        f.draw(new Arc2D.Double(6, 200, 140, 140, -arc, 2 * arc, Arc2D.OPEN));
        f.draw(new Arc2D.Double(814, 200, 140, 140, 180 - arc, 2 * arc, Arc2D.OPEN));
        int[][] corners = {{3, 3, -90}, {957, 3, 180}, {957, 537, 90}, {3, 537, 0}};
        for (int[] c : corners)
            f.draw(new Arc2D.Double(c[0] - 11, c[1] - 11, 22, 22, c[2], 90, Arc2D.OPEN));
        for (int x : new int[] {480, 76, 884}) f.fill(round(x, 270, 2.6));
        // Goals: shadowed net box with white posts and crossbar.
        for (int x : new int[] {-18, 961}) {
            f.setColor(new Color(0, 0, 0, 70));
            f.setStroke(new BasicStroke(5));
            f.drawRect(x - 13, 270 - goal + 22, 17, goal * 2);
            f.setColor(new Color(0, 0, 0, 102));
            f.fillRect(x + 3, 270 - goal + 4, 16, goal * 2);
            f.setColor(new Color(216, 220, 203, 28));
            f.fillRect(x, 270 - goal, 17, goal * 2);
            f.setColor(new Color(230, 233, 216, 153));
            f.setStroke(new BasicStroke(.8f));
            for (int y = 270 - goal; y <= 270 + goal; y += 6) f.drawLine(x, y, x + 17, y);
            for (int n = 0; n <= 17; n += 4) f.drawLine(x + n, 270 - goal, x + n, 270 + goal);
            f.setColor(new Color(0xf4f4ea));
            f.setStroke(new BasicStroke(3));
            f.drawRect(x, 270 - goal, 17, goal * 2);
            f.fillRect(x < 0 ? -4 : 961, 270 - goal - 3, 3, goal * 2 + 6);
        }
        // Warm floodlight strips on the boards.
        for (int y : new int[] {-11, 549})
            for (int x : new int[] {50, 250, 680, 880}) light(f, x, y, 32, 2.5);
        for (int x : new int[] {-11, 969})
            for (int y : new int[] {60, 460}) light(f, x, y, 2.5, 22);
        f.dispose();
        Pitch.stadium = stadium;
        stadiumGoal = goal;
        return stadium;
    }

    /** Stadium plus bumpers, in field units (the field is 0..960 x 0..540). */
    void drawField(Graphics2D g) {
        g.drawImage(stadium(), -20, -20, 1000, 580, null);
        for (Circle c : arena.bumpers) {
            g.setColor(new Color(0x435267));
            g.fill(round(c.x, c.y, c.radius));
            g.setColor(new Color(0xa0b0c4));
            g.draw(round(c.x, c.y, c.radius));
        }
    }

    static void light(Graphics2D f, double x, double y, double w, double h) {
        for (int glow = 12; glow > 0; glow -= 3) {
            f.setColor(new Color(255, 203, 122, 14));
            f.fill(
                    new RoundRectangle2D.Double(
                            x - glow, y - glow, w + 2 * glow, h + 2 * glow, 2 * glow, 2 * glow));
        }
        f.setColor(new Color(0xffe2b0));
        f.fill(new Rectangle2D.Double(x, y, w, h));
    }

    static Ellipse2D round(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
    }

    /** One cached sprite per team and ball, including the long afternoon shadow. */
    static void disc(Graphics2D g, double x, double y, double r, Color color, boolean ball) {
        int index = ball ? 2 : color.equals(DISC_RED) ? 0 : 1;
        if (discs[index] == null) {
            BufferedImage image = new BufferedImage(192, 224, BufferedImage.TYPE_INT_ARGB);
            Graphics2D s = image.createGraphics();
            s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            s.scale(2, 2);
            s.translate(48, 24);
            for (int n = 24; n >= 1; n--) {
                double width = r * (1 - n * .016);
                s.setColor(new Color(0, 0, 0, 9));
                s.fill(
                        new Ellipse2D.Double(
                                -n * .7 - width, n * 1.3 - r * .75, width * 2, r * 1.5));
            }
            s.setColor(new Color(0, 0, 0, 100));
            s.fill(round(-2, 3, r + 1));
            s.setColor(color);
            s.fill(round(0, 0, r));
            s.setPaint(
                    new RadialGradientPaint(
                            new Point2D.Double(0, 0),
                            (float) r,
                            new Point2D.Double(r * .35, -r * .4),
                            new float[] {0, .5f, 1},
                            new Color[] {
                                new Color(255, 255, 255, 101),
                                new Color(255, 255, 255, 0),
                                new Color(0, 0, 0, 153)
                            },
                            MultipleGradientPaint.CycleMethod.NO_CYCLE));
            s.fill(round(0, 0, r));
            s.setStroke(new BasicStroke(ball ? 1 : 1.4f));
            s.setColor(new Color(244, 238, 227, 208));
            s.draw(round(0, 0, r));
            if (ball) {
                Path2D pentagon = new Path2D.Double();
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5 - Math.PI / 2;
                    if (i == 0) pentagon.moveTo(3 * Math.cos(a), 3 * Math.sin(a));
                    else pentagon.lineTo(3 * Math.cos(a), 3 * Math.sin(a));
                }
                pentagon.closePath();
                s.setColor(new Color(0x32392e));
                s.fill(pentagon);
            } else {
                s.setStroke(new BasicStroke(.8f));
                s.setColor(new Color(255, 255, 255, 136));
                s.draw(new Arc2D.Double(-r + 3, -r + 3, r * 2 - 6, r * 2 - 6, 9, 162, Arc2D.OPEN));
            }
            s.dispose();
            discs[index] = image;
        }
        g.drawImage(
                discs[index], (int) Math.round(x - 48), (int) Math.round(y - 24), 96, 112, null);
    }

    void drawPlayers(Graphics2D g) {
        Composite normal = g.getComposite();
        for (Dot d : dots)
            if (d.team < 2) {
                double x = d.drawX, y = d.drawY;
                if (!d.connected) g.setComposite(AlphaComposite.SrcOver.derive(.4f));
                footballer(g, d);
                if (d.name.equalsIgnoreCase(me)) {
                    g.setColor(CREAM);
                    g.setStroke(new BasicStroke(2));
                    g.draw(round(x, y, 21));
                }
                text(g, d.name + (d.connected ? "" : " (away)"), x, y - 29, 12, TEXT);
                g.setComposite(normal);
            }
        disc(g, drawBallX, drawBallY, 8, new Color(0xf5f2e7), true);
    }

    static BufferedImage bodyShadow() {
        BufferedImage image = new BufferedImage(96, 112, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 12));
        for (int n = 24; n >= 0; n--)
            g.fill(new Ellipse2D.Double(48 - n * .7 - (10 - n * .2), 16 + n * 1.3, 20 - n * .4, 16));
        g.dispose();
        return image;
    }

    static void footballer(Graphics2D g, Dot d) {
        if (playersImage == null) {
            disc(g, d.drawX, d.drawY, 16, d.team == 0 ? DISC_RED : DISC_BLUE, false);
            return;
        }
        g.drawImage(bodyShadow, (int) d.drawX - 48, (int) d.drawY - 24, null);
        Graphics2D body = (Graphics2D) g.create();
        body.translate(d.drawX, d.drawY);
        body.rotate(d.heading + d.sway);
        if (d.kick) {
            body.setColor(new Color(0xf1eee1));
            body.fillRect(11, -8, 3, 13);
            body.setColor(new Color(0x20231c));
            body.fillRoundRect(9, -15, 6, 9, 4, 4);
            body.setColor(new Color(0xb5b8a5));
            body.fillRect(10, -11, 4, 1);
        }
        body.drawImage(playersImage, -17, -21, 17, 21, d.team * 256, 0, (d.team + 1) * 256, 256, null);
        text(body, d.shirt, 0, 8, 8, CREAM);
        body.dispose();
    }

    /** Score stays above the field so it cannot cover a player near the touchline. */
    void drawScore(Graphics2D g) {
        RoundRectangle2D pill = new RoundRectangle2D.Double(368, -16, 224, 58, 32, 32);
        g.setColor(new Color(18, 20, 17, 242));
        g.fill(pill);
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(255, 255, 255, 31));
        g.draw(pill);
        g.setColor(DISC_RED);
        g.fill(round(392, 8, 5));
        g.setColor(DISC_BLUE);
        g.fill(round(568, 8, 5));
        text(g, "RED", 418, 13, 11, TEXT);
        text(g, "BLUE", 540, 13, 11, TEXT);
        text(g, String.format("%02d : %02d", red, blue), 480, 17, 22, CREAM);
        text(
                g,
                phase.equals("Playing")
                        ? String.format(
                                "%d:%02d  -  FIRST TO %d",
                                remaining / 60, remaining % 60, goalLimit)
                        : phase.toUpperCase(),
                480,
                34,
                9,
                new Color(0x9a9f92));
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
