package lanball;

import java.io.File;

/** Regression cases discovered during the final gameplay/presentation review. */
public class ReviewTest {
    public static void main(String[] args) throws Exception {
        File root = java.nio.file.Files.createTempDirectory("lanball-review-").toFile();
        League league = new League(new File(root, "league"));
        for (int i = 0; i < 200; i++) league.profile("Visitor" + i);
        if (!league.records.isEmpty())
            throw new AssertionError("Viewing profiles creates fake standings entries");
        System.out.println("PASS viewing profiles does not modify standings");

        Server server = new Server(new File(root, "host"), 0);
        Arena pitch = Arena.parse("A\tTraining\t90\nO\t480\t100\t24\n");
        try {
            server.useArena(pitch);
        } finally {
            server.close();
        }
        server = new Server(new File(root, "host"), 0);
        try {
            if (!server.match.arena.name.equals("Training") || server.match.ball.goalHalf != 90)
                throw new AssertionError("Selected pitch forgotten on restart");
            // A failed write must not change the pitch the players are using.
            File blocked = new File(server.pitches, "Blocked.pitch");
            blocked.mkdirs();
            Files.write(new File(blocked, "keep.txt"), "block replacement");
            Arena bad = new Arena();
            bad.name = "Blocked";
            try {
                server.useArena(bad);
                throw new AssertionError("Expected a save failure");
            } catch (java.io.IOException expected) {
            }
            if (!server.match.arena.name.equals("Training"))
                throw new AssertionError("Failed save changed live pitch");
        } finally {
            server.close();
        }
        System.out.println("PASS pitch selection persists and failed saves preserve live pitch");
        Match match = new Match();
        Player player = new Player("TapTest");
        player.team = 0;
        player.x = 450;
        player.y = 270;
        match.players.add(player);
        long now = System.nanoTime();
        player.receiveInput(16, now);
        player.receiveInput(0, now + 1000000L);
        match.step(Match.STEP, now + 8000000L);
        if (match.ball.vx < 499) throw new AssertionError("Quick kick between ticks was dropped");
        if (player.wantsKick(now + 20000000L))
            throw new AssertionError("Buffered tap bypassed cooldown");
        System.out.println(
                "PASS quick kick survives release before physics tick; cooldown remains enforced");
        Pitch slow = new Pitch(), fast = new Pitch();
        slow.drawBallX = fast.drawBallX = 0;
        slow.ballX = fast.ballX = 50;
        for (int i = 0; i < 3; i++) slow.animate(1.0 / 30);
        for (int i = 0; i < 12; i++) fast.animate(1.0 / 120);
        if (Math.abs(slow.drawBallX - fast.drawBallX) > 1e-6)
            throw new AssertionError("Smoothing varies by frame rate");
        System.out.println("PASS identical smoothing over 100 ms at 30 and 120 Hz");
    }
}
