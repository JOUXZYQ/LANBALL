package lanball;

import java.util.Random;

/** Deterministic regression tests for the gameplay bugs found in v8. */
public class GameplayTest {
    static final long BASE = 1000000000000L;
    static int checks;

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    static Match match() {
        Match m = new Match();
        m.phase = "Playing";
        m.remaining = 600;
        m.goalLimit = 20;
        return m;
    }

    static Player add(Match m, int id, double x, double y, int keys) {
        Player p = new Player("Player" + id);
        p.team = id % 2;
        p.x = x;
        p.y = y;
        p.input = keys;
        p.lastInput = BASE;
        m.players.add(p);
        return p;
    }

    static void step(Match m, int tick) {
        long now = BASE + (long) ((tick + 1) * 1e9 / 120);
        for (Player p : m.players) p.lastInput = now;
        m.step(Match.STEP, now);
    }

    public static void main(String[] args) throws Exception {
        for (int rate : new int[] {30, 60, 65, 66, 80, 100, 119, 120, 121, 144, 240}) {
            Match m = match();
            Player p = add(m, 0, 100, 100, 2);
            for (int i = 0; i < rate; i++) {
                long now = BASE + (long) ((i + 1) * 1e9 / rate);
                p.lastInput = now;
                m.update(1.0 / rate, now);
            }
            check(
                    Math.abs(p.x - 310) < 1e-6 && Math.abs(m.remaining - 599) < 1e-6,
                    "constant speed and clock at " + rate + " Hz");
        }
        for (double left : new double[] {.001, .010}) {
            Match m = match();
            m.remaining = left;
            m.ball.x = 960;
            m.ball.y = 270;
            m.ball.vx = 500;
            check(
                    m.update(.1, BASE) && m.red == 0 && m.remaining == 0,
                    "shot cannot score after whistle (" + left + " s left)");
        }
        Match count = match();
        Player p = add(count, 0, 100, 100, 2);
        count.resumeAt = BASE - 1000000;
        count.update(.2, BASE);
        check(
                Math.abs(p.x - 100.21) < 1e-6 && Math.abs(count.remaining - 599.999) < 1e-6,
                "tick crossing kickoff uses only active time");
        Match bump = match();
        bump.arena = Arena.parse("A\tBumper\t72\nO\t480\t100\t24\n");
        bump.ball.x = 520;
        bump.ball.y = 100;
        bump.ball.vx = -500;
        for (int i = 0; i < 20; i++) step(bump, i);
        check(bump.ball.x > 512 && bump.ball.vx > 0, "ball rebounds from edited bumper");

        Random rng = new Random(9251026);
        int accepted = 0;
        double worst = 0;
        while (accepted < 800) {
            Match m = match();
            int corner = accepted % 4;
            boolean right = corner % 2 == 1, bottom = corner / 2 == 1;
            int keys = (right ? 2 : 1) | (bottom ? 8 : 4);
            boolean valid = true;
            int n = 2 + rng.nextInt(5);
            for (int i = 0; i < n; i++) {
                double x = 18 + rng.nextDouble() * 170, y = 18 + rng.nextDouble() * 170;
                Player q = add(m, i, right ? 960 - x : x, bottom ? 540 - y : y, keys);
                for (int j = 0; j < i; j++) if (q.distanceTo(m.players.get(j)) < 32) valid = false;
            }
            double x = 8 + rng.nextDouble() * 140, y = 8 + rng.nextDouble() * 140;
            m.ball.x = right ? 960 - x : x;
            m.ball.y = bottom ? 540 - y : y;
            for (Player q : m.players) if (q.distanceTo(m.ball) < 24) valid = false;
            if (!valid) continue;
            for (int i = 0; i < 240; i++) {
                step(m, i);
                if (!m.clear(m.players))
                    throw new AssertionError("embedded circles, corner " + corner);
                for (Player q : m.players) {
                    worst = Math.max(worst, 24 - q.distanceTo(m.ball));
                    if (!Double.isFinite(q.x)
                            || !Double.isFinite(q.y)
                            || q.x < 18
                            || q.x > 942
                            || q.y < 18
                            || q.y > 522) throw new AssertionError("invalid player position");
                }
            }
            // After pushing into a corner, all players must still be able to move away.
            double before = 0;
            for (Player q : m.players) {
                before += Math.hypot(q.x - (right ? 960 : 0), q.y - (bottom ? 540 : 0));
                q.input = (right ? 1 : 2) | (bottom ? 4 : 8);
            }
            for (int i = 240; i < 480; i++) step(m, i);
            double after = 0;
            for (Player q : m.players)
                after += Math.hypot(q.x - (right ? 960 : 0), q.y - (bottom ? 540 : 0));
            if (after <= before + 100) throw new AssertionError("players unable to escape corner");
            accepted++;
        }
        check(
                true,
                "800 valid crowd setups across all four corners, with escape checks; max ball"
                        + " penetration="
                        + worst);
        System.out.println("GAMEPLAY CHECKS: " + checks + " passed");
    }
}
