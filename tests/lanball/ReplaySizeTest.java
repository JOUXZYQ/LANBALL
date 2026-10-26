package lanball;

/**
 * Worst-case replay: 600 s of play, 19 goal countdowns, a full room of 12 long names and an
 * eight-bumper pitch, sampled exactly like Server.tick (every 50 ms while Playing).
 */
public class ReplaySizeTest {
    public static void main(String[] args) throws Exception {
        Match m = new Match();
        m.arena =
                Arena.parse(
                        "A\tLongest_Pitch_Name_24ch\t110\n"
                            + "O\t140\t90\t40\n"
                            + "O\t140\t450\t40\n"
                            + "O\t820\t90\t40\n"
                            + "O\t820\t450\t40\n"
                            + "O\t380\t90\t15\n"
                            + "O\t580\t90\t15\n"
                            + "O\t380\t450\t15\n"
                            + "O\t580\t450\t15\n");
        for (int i = 0; i < 12; i++) {
            Player p = new Player(String.format("Player_%02d_xxxxx", i)); // 16 characters
            p.team = i < 6 ? i % 2 : 2;
            p.matchTeam = p.team;
            p.shirt = "99";
            p.goals = 19;
            p.assists = 19;
            p.connected = true;
            m.players.add(p);
        }
        m.phase = "Playing";
        m.remaining = 600;
        m.goalLimit = 20;
        long now = 1_000_000_000_000L;
        m.clockNow = now;
        m.kickoff(3);
        Replay replay = new Replay();
        replay.capture(m.frame(now), now);
        int countdowns = 1;
        double nextGoal = 600 - 30;
        while (m.phase.equals("Playing")) {
            now += 15_000_000L; // Server.Clock sleeps 15 ms
            for (Player p : m.players) p.lastInput = now;
            boolean ended = m.update(.015, now);
            if (countdowns < 20 && m.remaining < nextGoal && now >= m.resumeAt) {
                m.goalSerial++;
                m.goalTeam = countdowns % 2;
                m.goalText = "BLUE GOAL Player_05_xxxxx (Player_03_xxxxx)";
                m.kickoff(3);
                countdowns++;
                nextGoal -= 30;
            }
            if (ended || now - replay.lastCapture >= 50_000_000L) replay.capture(m.frame(now), now);
        }
        byte[] bytes = replay.bytes();
        Replay back = Replay.read(bytes);
        int ms = back.times.get(back.times.size() - 1);
        System.out.printf(
                "frames=%d duration=%.1fs countdowns=%d bytes=%d (%.2f MB of %d MB cap)%n",
                back.frames.size(),
                ms / 1000.0,
                countdowns,
                bytes.length,
                bytes.length / 1048576.0,
                Replay.LIMIT / 1048576);
        if (back.frames.size() != replay.frames.size() || bytes.length > Replay.LIMIT / 2)
            throw new AssertionError("replay too close to the cap");
        System.out.println("PASS worst-case 600 s replay saves and reads back under half the cap");
    }
}
