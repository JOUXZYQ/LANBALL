package lanball;

import java.util.Random;

/** Replays the v7/v8 gameplay-audit reproductions (25 Oct 2026) against the current Match. Uses a synthetic clock. */
public class PhysicsTest {
    static int passed, failed;
    static long now = 1000000000000L;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (ok) passed++; else failed++;
    }

    static Match match() {
        Match m = new Match();
        m.phase = "Playing"; m.goalLimit = 99; m.remaining = 999; m.resumeAt = 0;
        return m;
    }

    static Player add(Match m, String name, int team, double x, double y, int input) {
        Player p = new Player(name);
        p.team = team; p.x = x; p.y = y; p.input = input; p.lastInput = now;
        m.players.add(p);
        return p;
    }

    /** One physics step of 1/120 s, keeping every player's keys fresh. */
    static void step(Match m) {
        now += (long) (Match.STEP * 1e9);
        for (Player p : m.players) p.lastInput = now;
        m.step(Match.STEP, now);
    }

    public static void main(String[] args) {
        // 01 corner loop: the audit's exact start, mirrored into all four corners. Both hold up-left (1 + 4), no kick.
        for (int corner = 0; corner < 4; corner++) {
            boolean flipX = corner % 2 == 1, flipY = corner >= 2;
            Match m = match();
            int left = flipX ? 2 : 1, up = flipY ? 8 : 4;
            Player a = add(m, "A", 0, fx(98.57848, flipX), fy(71.42431, flipY), left | up);
            Player b = add(m, "B", 1, fx(60.89021, flipX), fy(101.25024, flipY), left | up);
            m.ball.x = fx(33.72008, flipX); m.ball.y = fy(27.83012, flipY);
            int reversals = 0;
            double lastVx = 0, lastVy = 0, worstOverlap = 0;
            for (int i = 0; i < 1200; i++) { // 10 seconds
                step(m);
                if (i >= 960) { // the last 2 seconds
                    if (Math.abs(lastVx) > 1 && Math.abs(m.ball.vx) > 1 && lastVx * m.ball.vx < 0) reversals++;
                    if (Math.abs(lastVy) > 1 && Math.abs(m.ball.vy) > 1 && lastVy * m.ball.vy < 0) reversals++;
                    worstOverlap = Math.max(worstOverlap, 2 * Player.RADIUS - a.distanceTo(b));
                    worstOverlap = Math.max(worstOverlap, Match.TOUCH - a.distanceTo(m.ball));
                    worstOverlap = Math.max(worstOverlap, Match.TOUCH - b.distanceTo(m.ball));
                }
                lastVx = m.ball.vx; lastVy = m.ball.vy;
            }
            check(reversals == 0 && worstOverlap < 1.0, "01 corner " + corner + ": ball direction reversals in last 2 s = "
                    + reversals + " (v7: 119), worst overlap = " + String.format("%.2f", worstOverlap) + " (v7: about 6.2)");
        }
        // 02 two players pushing into a wall keep their separation
        {
            Match m = match();
            Player a = add(m, "A", 0, 18, 18, 1), b = add(m, "B", 1, 50, 18, 1);
            m.ball.x = 480; m.ball.y = 270;
            for (int i = 0; i < 1200; i++) step(m);
            check(a.distanceTo(b) > 31, "02 wall: centres " + String.format("%.1f", a.distanceTo(b)) + " apart (need 32; v7: 25)");
        }
        // 03 identical simultaneous kicks do not depend on list order
        {
            double[] result = new double[2];
            for (int order = 0; order < 2; order++) {
                Match m = match();
                if (order == 0) { add(m, "A", 0, 450, 270, 16); add(m, "B", 1, 510, 270, 16); }
                else { add(m, "B", 1, 510, 270, 16); add(m, "A", 0, 450, 270, 16); }
                m.ball.x = 480; m.ball.y = 270;
                step(m);
                result[order] = m.ball.vx;
            }
            check(result[0] == result[1], "03 equal kicks: ball speed " + result[0] + " and " + result[1] + " (v7: -248 vs +248)");
        }
        // 04 a fast shot cannot pass through a defender during a late 100 ms frame
        {
            Match m = match();
            Player d = add(m, "D", 1, 140, 270, 0);
            m.ball.x = 100; m.ball.y = 270; m.ball.vx = 500;
            now += 100000000L;
            d.lastInput = now;
            m.update(0.1, now);
            check(m.ball.x < d.x, "04 tunnelling: ball x " + String.format("%.1f", m.ball.x) + " stays before defender x "
                    + String.format("%.1f", d.x) + " (v7: ball 150, through the defender)");
        }
        // 05 a ball overlapping a post does not score
        {
            int wrong = 0;
            int[] heights = {199, 200, 205, 335, 338, 341};
            for (int y : heights) {
                Match m = match();
                m.ball.x = 951; m.ball.y = y; m.ball.vx = 500;
                for (int i = 0; i < 60; i++) step(m);
                if (m.red > 0) wrong++;
            }
            Match m = match();
            m.ball.x = 951; m.ball.y = 270; m.ball.vx = 500;
            for (int i = 0; i < 60; i++) step(m);
            check(wrong == 0 && m.red == 1, "05 posts: grazing shots that scored = " + wrong + " of 6 (v7: 6); a clean shot scores = " + (m.red == 1));
        }
        // 06 + audit 14: no player ever ends a step outside the pitch (seeded stress, 100,000 steps, 6 players)
        {
            Random random = new Random(2541);
            Match m = match();
            for (int i = 0; i < 6; i++) add(m, "P" + i, i % 2, 100 + random.nextDouble() * 760, 50 + random.nextDouble() * 440, 0);
            int outside = 0, notFinite = 0;
            for (int i = 0; i < 100000; i++) {
                for (Player p : m.players) if (random.nextInt(20) == 0) p.input = random.nextInt(32);
                if (i % 5000 == 0) {
                    for (Player p : m.players) { p.x = 18 + random.nextDouble() * 924; p.y = 18 + random.nextDouble() * 504; }
                    m.ball.x = 30 + random.nextDouble() * 900; m.ball.y = 30 + random.nextDouble() * 480;
                }
                step(m);
                for (Player p : m.players) {
                    if (p.x < 18 - 1e-9 || p.x > 942 + 1e-9 || p.y < 18 - 1e-9 || p.y > 522 + 1e-9) outside++;
                    if (Double.isNaN(p.x) || Double.isNaN(p.y) || Double.isInfinite(p.x)) notFinite++;
                }
                if (Double.isNaN(m.ball.x) || Double.isNaN(m.ball.y)) notFinite++;
                if (m.phase.equals("Playing") == false) m.phase = "Playing";
                m.resumeAt = 0;
            }
            check(outside == 0 && notFinite == 0, "06 bounds: positions outside the pitch = " + outside + ", not finite = " + notFinite
                    + " in 100,000 steps (v7: 2 outside)");
        }
        // 08 a loser switching team after full time does not turn the result into a win
        {
            Match m = match();
            Player asha = add(m, "Asha", 0, 200, 270, 0), bobby = add(m, "Bobby", 1, 700, 270, 0);
            m.red = 1; m.phase = "Result";
            m.chooseTeam(bobby, 0);
            check(m.phase.equals("Lobby"), "08 result: after Bobby changes team the phase is " + m.phase + " (the old result is no longer shown)");
        }
        // 09 the match clock uses real time even after a 2-second stall
        {
            Match m = match();
            add(m, "A", 0, 200, 270, 0); add(m, "B", 1, 700, 270, 0);
            m.remaining = 30;
            now += 2000000000L;
            m.update(2.0, now);
            check(Math.abs(m.remaining - 28) < 1e-9, "09 clock: 30 s - 2 s stall = " + m.remaining + " s (v7: 29.9)");
        }
        // Normal play still works: a player walks into the ball and dribbles it; a kick shoots it
        {
            Match m = match();
            Player a = add(m, "A", 0, 400, 270, 2);
            m.ball.x = 440; m.ball.y = 270;
            for (int i = 0; i < 60; i++) step(m);
            boolean dribbled = m.ball.x > 500 && Math.abs(a.distanceTo(m.ball) - Match.TOUCH) < 2;
            a.input = 16;
            step(m);
            check(dribbled && m.ball.vx > 400, "play: walking pushes the ball along (x = " + (int) m.ball.x + "), a kick shoots it (speed "
                    + (int) m.ball.vx + ")");
        }
        System.out.println("Physics checks: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static double fx(double x, boolean flip) { return flip ? 960 - x : x; }
    static double fy(double y, boolean flip) { return flip ? 540 - y : y; }
}
