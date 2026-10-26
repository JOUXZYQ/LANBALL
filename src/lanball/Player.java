package lanball;

/*
 * One person in the game. Team 0 = Red, 1 = Blue, 2 = watching.
 * The keys arrive from the player's computer as bits: left 1, right 2, up 4, down 8, kick 16, ready 32.
 */
public class Player extends Circle {
    static final double RADIUS = 16;
    final String name;
    String club = "", shirt = "1";
    int team = 2, matchTeam = 2, input, goals, assists;
    boolean connected = true;
    long lastInput, lastKick, kickUntil; // times in nanoseconds

    /** Remember a quick tap long enough for the next physics tick to see it. */
    void receiveInput(int keys, long now) {
        if ((keys & 16) != 0 && (input & 16) == 0) kickUntil = now + 100000000L;
        input = keys & 63;
        lastInput = now;
    }

    Player(String name) {
        super(RADIUS);
        this.name = name;
    }

    /**
     * True if the key bit is held. Keys older than half a second are ignored, so a silent player
     * stops.
     */
    boolean holds(int key, long now) {
        return now - lastInput < 500000000L && (input & key) != 0;
    }

    boolean ready() {
        return (input & 32) != 0;
    }

    boolean wantsKick(long now) {
        return (holds(16, now) || now < kickUntil) && now - lastKick > 300000000L;
    }

    /** Moves the player for h seconds. A diagonal is divided by its length, so it is not faster. */
    void move(long now, double h) {
        double dx = (holds(2, now) ? 1 : 0) - (holds(1, now) ? 1 : 0);
        double dy = (holds(8, now) ? 1 : 0) - (holds(4, now) ? 1 : 0);
        double length = Math.max(1, Math.hypot(dx, dy));
        vx = dx / length * Match.SPEED;
        vy = dy / length * Match.SPEED;
        x += vx * h;
        y += vy * h;
    }

    /** One snapshot line: P, name, team, x, y, ready, connected, shirt, goals, assists. */
    String row() {
        return "P\t" + name + "\t" + team + "\t" + (int) x + "\t" + (int) y + "\t" + ready() + "\t"
                + connected + "\t" + shirt + "\t" + goals + "\t" + assists + "\n";
    }
}
