package lanball;

import java.util.Vector;

/** Football rules and physics. No sockets, windows or files belong here. */
public class Match {
    static final double SPEED = 210,
            KICK_SPEED = 500,
            KICK_REACH = 40,
            STEP = 1.0 / 120,
            TOUCH = 24;
    final Vector<Player> players = new Vector<Player>();
    final Vector<String> chat = new Vector<String>();
    final Ball ball = new Ball();
    Arena arena = new Arena();
    String phase = "Lobby",
            message = "Choose a team and mark Ready.",
            lastTouch = "",
            previousTouch = "";
    String goalText = "", resultId = "";
    int red, blue, goalLimit, goalSerial, goalTeam, rollbacks;
    double remaining;
    long resumeAt, clockNow, previousTouchAt;

    void say(String text) {
        chat.add(text);
        if (chat.size() > 8) chat.remove(0);
    }

    Player find(String name) {
        for (Player p : players) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    int connectedCount() {
        int n = 0;
        for (Player p : players) if (p.connected) n++;
        return n;
    }

    int teamSize(int team) {
        int n = 0;
        for (Player p : players) if (p.connected && p.team == team) n++;
        return n;
    }

    String chooseTeam(Player p, int team) {
        if (team < 0 || team > 2 || team == p.team) return "";
        if (phase.equals("Playing") || phase.equals("Save failed"))
            return "Teams are locked for this match.";
        if (team < 2 && teamSize(team) >= 3)
            return "That team is full. Choose another team or Watch.";
        p.team = team;
        p.input &= ~32;
        if (phase.equals("Result")) phase = "Lobby";
        return "";
    }

    String start(int seconds, int target) {
        if (phase.equals("Playing")) return "A match is already running.";
        if (phase.equals("Save failed")) return "Save the previous result before starting again.";
        if (seconds < 30 || seconds > 600 || target < 1 || target > 20)
            return "Seconds: 30-600. Goals: 1-20.";
        for (int i = players.size() - 1; i >= 0; i--)
            if (!players.get(i).connected) players.remove(i);
        if (teamSize(0) == 0 || teamSize(1) == 0) return "Both teams need a player.";
        for (Player p : players)
            if (p.team < 2 && !p.ready()) return "Every player must mark Ready.";
        for (Player p : players) {
            p.matchTeam = p.team;
            p.goals = p.assists = 0;
            p.lastKick = 0;
        }
        red = blue = 0;
        remaining = seconds;
        goalLimit = target;
        phase = "Playing";
        clockNow = System.nanoTime();
        ball.goalHalf = arena.goalHalf;
        kickoff(3);
        return message = "Kick-off! First to " + target + " goals.";
    }

    void kickoff(int seconds) {
        ball.x = 480;
        ball.y = 270;
        ball.vx = ball.vy = 0;
        lastTouch = previousTouch = "";
        int[] slots = {0, 0};
        for (Player p : players)
            if (p.team < 2) {
                p.x = p.team == 0 ? 240 : 720;
                p.y = 150 + 120 * slots[p.team]++;
                p.vx = p.vy = 0;
                p.kickUntil = 0;
            }
        resumeAt = clockNow + seconds * 1000000000L;
    }

    /** Intersect the host interval with active play; the last substep uses only the time left. */
    boolean update(double elapsed, long now) {
        if (!phase.equals("Playing") || elapsed <= 0 || now < resumeAt) return false;
        double active = Math.min(elapsed, Math.max(0, (now - resumeAt) / 1e9));
        if (active > .25) {
            remaining -= active - .25;
            active = .25;
        } // do not replay seconds of stale controls after an OS stall
        double timeLeft = Math.min(active, Math.max(0, remaining));
        long cursor = now - (long) (active * 1e9);
        while (timeLeft > 1e-10 && remaining > 1e-10) {
            double h = Math.min(STEP, timeLeft);
            cursor += (long) (h * 1e9);
            clockNow = cursor;
            step(h, cursor);
            remaining -= h;
            timeLeft -= h;
            if (red >= goalLimit || blue >= goalLimit || resumeAt > cursor) break;
        }
        if (remaining <= 1e-9 || red >= goalLimit || blue >= goalLimit) {
            remaining = Math.max(0, remaining);
            phase = "Result";
            return true;
        }
        return false;
    }

    void step(double h, long now) {
        clockNow = now;
        Vector<Player> field = new Vector<Player>();
        for (Player p : players) if (p.team < 2 && p.connected) field.add(p);
        // Stable order means that reconnecting does not change a contact's outcome.
        for (int i = 1; i < field.size(); i++) {
            Player p = field.get(i);
            int j = i;
            while (j > 0 && field.get(j - 1).name.compareToIgnoreCase(p.name) > 0) {
                field.set(j, field.get(j - 1));
                j--;
            }
            field.set(j, p);
        }
        double[][] before = new double[field.size() + 1][2];
        for (int i = 0; i < field.size(); i++) {
            Player p = field.get(i);
            before[i][0] = p.x;
            before[i][1] = p.y;
            p.move(now, h);
        }
        before[field.size()][0] = ball.x;
        before[field.size()][1] = ball.y;
        ball.roll(h);
        for (int pass = 0; pass < 60; pass++) {
            for (int i = 0; i < field.size(); i++)
                for (int j = i + 1; j < field.size(); j++) separate(field.get(i), field.get(j), .5);
            for (Player p : field) separate(p, ball, .2);
            for (Circle c : arena.bumpers) {
                for (Player p : field) separate(c, p, 0);
                separate(c, ball, 0);
            }
            for (Player p : field) p.keepInside(18, 18, 942, 522);
            ball.keepInWalls();
            if (clear(field)) break;
        }
        // If a crowded contact cannot settle, keep the last valid positions instead of embedding
        // objects.
        if (!clear(field)) {
            for (int i = 0; i < field.size(); i++) {
                Player p = field.get(i);
                p.x = before[i][0];
                p.y = before[i][1];
                p.vx = p.vy = 0;
            }
            ball.x = before[field.size()][0];
            ball.y = before[field.size()][1];
            ball.vx = ball.vy = 0;
            rollbacks++;
        }
        hitBall(field, now);
        int team = ball.goalFor();
        if (team >= 0) goal(team);
    }

    /** firstShare is how much of the overlap the first circle takes; a bumper takes none. */
    void separate(Circle a, Circle b, double firstShare) {
        double d = a.distanceTo(b), need = a.radius + b.radius;
        if (d >= need) return;
        double nx = d < .00001 ? 1 : (b.x - a.x) / d,
                ny = d < .00001 ? 0 : (b.y - a.y) / d,
                overlap = need - d + .0001;
        a.x -= nx * overlap * firstShare;
        a.y -= ny * overlap * firstShare;
        b.x += nx * overlap * (1 - firstShare);
        b.y += ny * overlap * (1 - firstShare);
        if (firstShare == 0 && b == ball) {
            double closing = ball.vx * nx + ball.vy * ny;
            if (closing < 0) {
                ball.vx -= 1.6 * closing * nx;
                ball.vy -= 1.6 * closing * ny;
            }
        }
    }

    boolean clear(Vector<Player> field) {
        for (int i = 0; i < field.size(); i++) {
            Player p = field.get(i);
            if (p.distanceTo(ball) < TOUCH - .02) return false;
            for (int j = i + 1; j < field.size(); j++)
                if (p.distanceTo(field.get(j)) < 32 - .02) return false;
            for (Circle c : arena.bumpers)
                if (p.distanceTo(c) < p.radius + c.radius - .02) return false;
        }
        for (Circle c : arena.bumpers)
            if (ball.distanceTo(c) < ball.radius + c.radius - .02) return false;
        return true;
    }

    void touch(Player p, long now) {
        if (p.name.equals(lastTouch)) return;
        previousTouch = lastTouch;
        previousTouchAt = now;
        lastTouch = p.name;
    }

    void hitBall(Vector<Player> field, long now) {
        int contacts = 0, kickers = 0;
        double dx = 0, dy = 0;
        for (Player p : field) {
            double d = p.distanceTo(ball),
                    nx = d < .01 ? (p.team == 0 ? 1 : -1) : (ball.x - p.x) / d,
                    ny = d < .01 ? 0 : (ball.y - p.y) / d;
            if (d <= TOUCH + .5) {
                contacts++;
                double closing = (ball.vx - p.vx) * nx + (ball.vy - p.vy) * ny;
                if (closing < -.01) {
                    ball.vx -= closing * nx;
                    ball.vy -= closing * ny;
                    touch(p, now);
                }
            }
            if (d < KICK_REACH && p.wantsKick(now)) {
                kickers++;
                dx += nx;
                dy += ny;
                p.lastKick = now;
                touch(p, now);
            }
        }
        double length = Math.hypot(dx, dy);
        if (kickers > 0 && length > .01) {
            ball.vx = dx / length * KICK_SPEED;
            ball.vy = dy / length * KICK_SPEED;
        } else if (kickers > 1 || contacts > 1) ball.vx = ball.vy = 0;
    }

    void goal(int team) {
        if (team == 0) red++;
        else blue++;
        Player scorer = find(lastTouch), assist = find(previousTouch);
        String who = "";
        if (scorer != null && scorer.team == team) {
            scorer.goals++;
            who = " - " + scorer.name;
            if (assist != null
                    && assist != scorer
                    && assist.team == team
                    && clockNow - previousTouchAt < 10000000000L) {
                assist.assists++;
                who += " (assist: " + assist.name + ")";
            }
        } else if (scorer != null) who = " - own goal by " + scorer.name;
        goalTeam = team;
        goalSerial++;
        goalText = (team == 0 ? "RED" : "BLUE") + " GOAL" + who;
        say(message = goalText);
        kickoff(2);
    }

    String header(long now) {
        long count =
                phase.equals("Playing")
                        ? Math.max(0, (resumeAt - now + 999999999L) / 1000000000L)
                        : 0;
        return "H\t"
                + phase
                + "\t"
                + (int) Math.ceil(remaining)
                + "\t"
                + red
                + "\t"
                + blue
                + "\t"
                + (int) ball.x
                + "\t"
                + (int) ball.y
                + "\t"
                + count
                + "\t"
                + goalLimit
                + "\n";
    }

    String frame(long now) {
        String text =
                header(now)
                        + arena.rows()
                        + "G\t"
                        + goalSerial
                        + "\t"
                        + goalTeam
                        + "\t"
                        + goalText
                        + "\n";
        for (Player p : players)
            if (p.connected || phase.equals("Playing") || p.matchTeam < 2) text += p.row();
        return text;
    }
}
