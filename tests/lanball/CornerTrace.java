package lanball;

import java.io.File;
import java.nio.file.Files;

/** Steps the host by hand (its own clock thread is stopped) and prints every tick for a corner squeeze. */
public class CornerTrace {
    public static void main(String[] args) throws Exception {
        int scenario = Integer.parseInt(args[0]);
        File data = Files.createTempDirectory("lanball-corner").toFile();
        Server server = new Server(data, 45998);
        server.running = false;
        Thread.sleep(100);
        Server.Player red = new Server.Player(), blue = new Server.Player();
        red.name = "Red"; red.team = 0; blue.name = "Blue"; blue.team = 1;
        server.players.add(red); server.players.add(blue);
        server.phase = "Playing"; server.goalLimit = 99; server.remaining = 99; server.resumeAt = 0;
        // 0: bottom-right corner, both chase the ball into it. 1: top wall, head to head. 2: left corner below the goal.
        double[][] start = {{880, 480, 945, 440, 925, 515}, {450, 30, 510, 30, 480, 15}, {60, 480, 20, 440, 30, 520}};
        double[] s = start[scenario];
        red.x = s[0]; red.y = s[1]; blue.x = s[2]; blue.y = s[3]; server.ballX = s[4]; server.ballY = s[5];
        int flipsX = 0, flipsY = 0; double path = 0, lastVx = 0, lastVy = 0, bx0 = server.ballX, by0 = server.ballY;
        long t = System.nanoTime();
        for (int i = 0; i < 120; i++) { // two seconds of 60 Hz ticks
            t += 16_666_667L;
            server.previousTick = t - 16_666_667L;
            red.lastInput = blue.lastInput = Long.MAX_VALUE / 2;
            // each player chases the ball like a person would
            red.input = chase(red, server); blue.input = chase(blue, server);
            double ox = server.ballX, oy = server.ballY;
            tickAt(server, t);
            path += Math.hypot(server.ballX - ox, server.ballY - oy);
            if (server.velocityX * lastVx < 0) flipsX++;
            if (server.velocityY * lastVy < 0) flipsY++;
            lastVx = server.velocityX; lastVy = server.velocityY;
            if (i < 24 || i % 20 == 0) System.out.printf("tick %3d ball %6.1f %6.1f  v %7.1f %7.1f  red %5.1f %5.1f  blue %5.1f %5.1f%n",
                    i, server.ballX, server.ballY, server.velocityX, server.velocityY, red.x, red.y, blue.x, blue.y);
        }
        System.out.printf("SCENARIO %d: velocity flips x %d, y %d in 120 ticks; ball path %.0f units, net move %.0f units%n",
                scenario, flipsX, flipsY, path, Math.hypot(server.ballX - bx0, server.ballY - by0));
        server.close();
    }
    static int chase(Server.Player p, Server server) {
        int bits = 0;
        if (server.ballX < p.x - 2) bits |= 1; if (server.ballX > p.x + 2) bits |= 2;
        if (server.ballY < p.y - 2) bits |= 4; if (server.ballY > p.y + 2) bits |= 8;
        return bits;
    }
    // Server.tick reads System.nanoTime(); the clock is close enough when called right away.
    static void tickAt(Server server, long t) { server.previousTick = System.nanoTime() - 16_666_667L; server.tick(); }
}
