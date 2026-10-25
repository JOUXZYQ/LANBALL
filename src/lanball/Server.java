package lanball;
import java.io.*;
import java.net.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
 * The host. It keeps the only true copy of the match and sends a text snapshot to every player.
 * Module 2: classes, constructors, static and final members, inner classes.
 * Module 3: interfaces (Runnable), a custom exception (LoginException), try / catch / finally, throw, throws.
 */
public class Server {
    static final int PORT = 45888, RADIUS = 16;
    // Badges are earned from real results only. They are rebuilt from the match files, like the league.
    static final String[] BADGES = {"First Goal", "Hat-trick", "On Fire", "Brick Wall", "Veteran", "Elite", "Team Player",
            "Unstoppable"};
    // One person in the game. Team 0 = Red, 1 = Blue, 2 = watching.
    static class Player {
        String name, club, shirt; int team = 2, input, goals; double x, y, vx, vy; long lastInput, lastKick; boolean connected = true;
    }
    // One profile and the totals rebuilt from the match files.
    static class Profile {
        String name, club = "", shirt = "", salt, verifier; // verifier stays null until the profile file exists
        // played, won, drawn, goals, points, Elo, badge bits, current win streak, best win streak
        int[] s = {0, 0, 0, 0, 0, 1000, 0, 0, 0};
        Profile(String name) { this.name = name; }
        void set(String[] cells) { name = cells[0]; club = cells[1]; shirt = cells[2]; salt = cells[3]; verifier = cells[4]; }
    }
    final Vector<Player> players = new Vector<Player>();
    final Vector<Profile> profiles = new Vector<Profile>();
    final Vector<String> chat = new Vector<String>();
    final Vector<Socket> sockets = new Vector<Socket>();
    final File folder;
    final ServerSocket listener;
    final FileLock lock;
    String phase = "Lobby", message = "Choose teams, mark ready, then host starts.", matchId, lastTouch = "";
    int red, blue, goalLimit, kicks, bounces, roundsThisSession;
    double ballX = 480, ballY = 270, velocityX, velocityY, remaining;
    long previousTick = System.nanoTime(), resumeAt;
    boolean running = true;
    Server(File folder, int port) throws IOException {
        this.folder = folder;
        folder.mkdirs();
        lock = new RandomAccessFile(new File(folder, "host.lock"), "rw").getChannel().tryLock();
        if (lock == null) throw new IOException("This data folder is already hosted.");
        String damaged = "";
        File[] files = folder.listFiles();
        Arrays.sort(files); // match files start with their time, so Elo replays in order
        for (File file : files) {
            try {
                if (file.getName().endsWith(".profile")) loadProfile(read(file));
                if (file.getName().endsWith(".match")) addResult(read(file), false);
            } catch (IOException error) { damaged += " " + file.getName();
            } catch (RuntimeException error) { damaged += " " + file.getName(); }
        }
        if (damaged.length() > 0) message = "Skipped damaged files (left unchanged):" + damaged;
        try { listener = new ServerSocket(port); } catch (IOException error) { lock.channel().close(); throw error; }
        background(new Acceptor());
        background(new Clock());
    }
    static void background(Runnable task) { Thread thread = new Thread(task); thread.setDaemon(true); thread.start(); }
    static String read(File file) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(file));
        String text = "", line;
        try { while ((line = in.readLine()) != null) text += line + "\n"; } finally { in.close(); }
        return text;
    }
    static void write(File file, String text) throws IOException {
        FileWriter out = new FileWriter(file);
        try { out.write(text); } finally { out.close(); }
    }
    void loadProfile(String text) throws IOException {
        String[] cells = text.trim().split("\t");
        if (cells.length != 5) throw new IOException("wrong field count");
        stats(cells[0]).set(cells);
    }
    // Module 3 interface: Runnable has one method, run(). A Thread runs it beside the rest of the program.
    class Acceptor implements Runnable {
        public void run() {
            while (!listener.isClosed()) {
                try {
                    Socket socket = listener.accept();
                    if (sockets.size() >= 12) socket.close();
                    else { sockets.add(socket); background(new Connection(socket)); }
                } catch (IOException error) { if (!listener.isClosed()) error.printStackTrace(); }
            }
        }
    }
    // Moves the match forward about 60 times a second.
    class Clock implements Runnable {
        public void run() {
            while (running) { tick(); try { Thread.sleep(15); } catch (InterruptedException stop) { return; } }

        }
    }
    // One connected player: log in, then answer every input message with a snapshot.
    class Connection implements Runnable {
        final Socket socket;
        Connection(Socket socket) { this.socket = socket; }
        public void run() {
            Player player = null;
            try {
                socket.setSoTimeout(8000); socket.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                try { player = login(in, out); }
                catch (LoginException refused) { out.writeUTF("ERROR: " + refused.getMessage()); return; }
                out.writeUTF("OK");
                while (running) {
                    int input = in.readInt(), team = in.readInt();
                    String said = in.readUTF().trim();
                    out.writeUTF(update(player, input, team, said));
                    Thread.sleep(15); // caps one player at about 60 messages a second
                }
            } catch (IOException ended) { } catch (InterruptedException ended) { } finally { leave(player, socket); }
        }
    }
    synchronized String update(Player player, int input, int team, String said) {
        player.input = input & 63; player.lastInput = System.nanoTime();
        if (!phase.equals("Playing") && !phase.equals("Save failed")) chooseTeam(player, team);
        if (said.length() > 0) say(player.name + ": " + (said.length() > 80 ? said.substring(0, 80) : said));
        return snapshot();
    }
    synchronized void leave(Player player, Socket socket) {
        if (player != null) { player.connected = false; player.input = 0; }
        sockets.remove(socket); try { socket.close(); } catch (IOException ignored) { }
    }
    // Challenge-response login: the PIN never crosses the network. A new player sends PBKDF2(PIN, salt) once;
    // later logins send SHA-256(that verifier + a fresh nonce), so a captured login cannot be replayed.
    Player login(DataInputStream in, DataOutputStream out) throws IOException, LoginException {
        String[] hello = in.readUTF().split("\t", -1);
        if (hello.length != 5 || !hello[0].equals("LB7")) throw new LoginException("Use a v7 client.");
        String name = hello[1].trim(), club = hello[2].trim(), shirt = hello[3];
        if (!name.matches("[A-Za-z0-9_-]{2,16}")) throw new LoginException("Name: 2-16 letters, digits, _ or -.");
        Profile profile = find(name);
        boolean fresh = profile == null || profile.verifier == null;
        if (fresh && !hello[4].equals("create")) throw new LoginException("Profile not found. Select Create profile.");
        if (fresh && (!club.matches("[A-Za-z0-9 _-]{2,24}") || !shirt.matches("[1-9][0-9]?")))
            throw new LoginException("Club: 2-24 letters/digits/spaces. Shirt: 1-99.");
        String salt = fresh ? UUID.randomUUID().toString() : profile.salt, nonce = UUID.randomUUID().toString();
        out.writeUTF(salt + "\t" + nonce + "\t" + (fresh ? "new" : "old"));
        return enter(fresh, new String[]{name, club, shirt, salt, in.readUTF()}, nonce);
    }
    synchronized Player enter(boolean fresh, String[] cells, String nonce) throws LoginException {
        Player player = player(cells[0]);
        if (player != null && player.connected) throw new LoginException("That player is already connected.");
        Profile profile = find(cells[0]);
        if (fresh) {
            if (profile != null && profile.verifier != null || profiles.size() >= 500)
                throw new LoginException("Name taken or league full.");
            if (!cells[4].matches("[A-Za-z0-9+/=]{44}")) throw new LoginException("Bad registration.");
            profile = stats(cells[0]);
            try { write(new File(folder, "player-" + cells[0].toLowerCase(Locale.ROOT) + ".profile"), String.join("\t", cells)); }
            catch (IOException error) { throw new LoginException("Could not save the profile."); }
            profile.set(cells);
        } else if (!MessageDigest.isEqual(proof(profile.verifier, nonce).getBytes(), cells[4].getBytes()))
            throw new LoginException("Incorrect PIN.");
        if (player == null) { player = new Player(); players.add(player); }
        player.name = profile.name; player.club = profile.club; player.shirt = profile.shirt; player.connected = true;
        say(player.name + " joined");
        return player;
    }
    static String verifier(String pin, String salt) throws GeneralSecurityException { // slow on purpose: runs on the client
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 60000, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(key);
    }
    static String proof(String verifier, String nonce) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((verifier + nonce).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    // Experience points: every match counts, skill counts more. Level n needs 50 * n * n XP, so early levels come quickly.
    static int xp(int[] s) { return s[0] * 20 + s[1] * 50 + s[2] * 20 + s[3] * 15 + Integer.bitCount(s[6]) * 100; }
    static int level(int xp) { return (int) Math.sqrt(xp / 50.0) + 1; }
    static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    void say(String line) { chat.add(line); if (chat.size() > 6) chat.remove(0); }
    Player player(String name) { for (Player p : players) if (p.name.equalsIgnoreCase(name)) return p; return null; }
    synchronized Profile find(String name) { for (Profile p : profiles) if (p.name.equalsIgnoreCase(name)) return p; return null; }
    Profile stats(String name) {
        Profile profile = find(name);
        if (profile == null) { profile = new Profile(name); profiles.add(profile); }
        return profile;
    }
    void chooseTeam(Player player, int team) {
        if (team < 0 || team > 2 || team == player.team) return;
        int count = 0;
        for (Player p : players) if (p.connected && p.team == team) count++;
        if (team == 2 || count < 3) { player.team = team; player.input &= ~32; } // a new team means "not ready"
    }
    synchronized String startRound(int seconds, int target) {
        if (phase.equals("Save failed")) { finish(); return message; }
        if (phase.equals("Playing")) return message = "A match is already running.";
        if (seconds < 30 || seconds > 600 || target < 1 || target > 20) return message = "Seconds: 30-600. Goals: 1-20.";
        for (int i = players.size() - 1; i >= 0; i--) if (!players.get(i).connected) players.remove(i);
        boolean redFound = false, blueFound = false, allReady = true;
        for (Player p : players) {
            if (p.team == 0) redFound = true; else if (p.team == 1) blueFound = true;
            if (p.team < 2 && (p.input & 32) == 0) allReady = false;
        }
        if (!redFound || !blueFound) return message = "Both teams need a player.";
        if (!allReady) return message = "Every player must mark ready.";
        matchId = "M" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
        remaining = seconds; goalLimit = target; red = blue = 0;
        for (Player p : players) p.goals = 0;
        phase = "Playing"; kickoff(3);
        return message = "Kick-off! First to " + target + " goals";
    }
    void kickoff(int countdownSeconds) {
        ballX = 480; ballY = 270; velocityX = velocityY = 0; lastTouch = "";
        int[] positions = {0, 0};
        for (Player p : players)
            if (p.team < 2) { p.x = p.team == 0 ? 240 : 720; p.y = 150 + positions[p.team]++ * 120; }
        resumeAt = System.nanoTime() + countdownSeconds * 1000000000L;
    }
    synchronized void tick() {
        long now = System.nanoTime();
        double elapsed = Math.min(.1, (now - previousTick) / 1e9); // real time, so late ticks keep the speed right
        previousTick = now;
        if (!phase.equals("Playing") || now < resumeAt) return;
        remaining = Math.max(0, remaining - elapsed);
        Vector<Player> field = new Vector<Player>();
        for (Player p : players) if (p.team < 2 && p.connected) field.add(p);
        for (Player p : field) {
            int input = now - p.lastInput < 500000000L ? p.input : 0; // a silent player stops moving
            double dx = ((input & 2) != 0 ? 1 : 0) - ((input & 1) != 0 ? 1 : 0);
            double dy = ((input & 8) != 0 ? 1 : 0) - ((input & 4) != 0 ? 1 : 0), length = Math.max(1, Math.hypot(dx, dy));
            p.vx = dx / length * 210; p.vy = dy / length * 210; // the ball bounces off a moving player harder
            p.x += p.vx * elapsed; p.y += p.vy * elapsed;
        }
        for (int i = 0; i < field.size(); i++) // players push each other apart
            for (int j = i + 1; j < field.size(); j++) {
                Player a = field.get(i), b = field.get(j);
                double dx = b.x - a.x + (a.x == b.x ? .01 : 0), dy = b.y - a.y, distance = Math.hypot(dx, dy);
                double push = (2 * RADIUS - distance) / 2 / distance;
                if (push > 0) { a.x -= dx * push; a.y -= dy * push; b.x += dx * push; b.y += dy * push; }
            }
        int touches = 0, kicked = 0;
        for (Player p : field) {
            p.x = clamp(p.x, 18, 942); p.y = clamp(p.y, 18, 522);
            double bx = ballX - p.x, by = ballY - p.y, distance = Math.hypot(bx, by);
            boolean kick = (p.input & 16) != 0 && now - p.lastInput < 500000000L && now - p.lastKick > 300000000L;
            double nx = distance < .01 ? (p.team == 0 ? 1 : -1) : bx / distance, ny = distance < .01 ? 0 : by / distance;
            if (distance < RADIUS + 8) { // touch: push the ball out by the overlap only, then bounce it off the player
                ballX = p.x + nx * (RADIUS + 8); ballY = p.y + ny * (RADIUS + 8);
                double closing = (velocityX - p.vx) * nx + (velocityY - p.vy) * ny;
                if (closing < 0) { velocityX -= 1.3 * closing * nx; velocityY -= 1.3 * closing * ny; }
                lastTouch = p.name; touches++;
            }
            if (kick && distance < 40) { velocityX = nx * 500; velocityY = ny * 500; lastTouch = p.name; p.lastKick = now; kicks++; kicked++; }
        }
        if (touches > 1 && kicked == 0) velocityX = velocityY = 0; // squeezed between two players: the ball stays put
        ballX += velocityX * elapsed; ballY += velocityY * elapsed;
        double drag = Math.pow(.985, elapsed * 30); velocityX *= drag; velocityY *= drag;
        boolean fast = Math.hypot(velocityX, velocityY) > 80;
        if (ballY < 8 || ballY > 532) { // a wall only turns a ball that moves into it
            velocityY = (ballY < 8 ? .8 : -.8) * Math.abs(velocityY); ballY = clamp(ballY, 8, 532); if (fast) bounces++;
        }
        if ((ballX < 8 || ballX > 952) && (ballY <= 198 || ballY >= 342)) {
            velocityX = (ballX < 8 ? .8 : -.8) * Math.abs(velocityX); ballX = clamp(ballX, 8, 952); if (fast) bounces++;
        }
        for (Player p : field) { // a ball held by a wall or another player pushes the player back, so it never jumps
            double dx = p.x - ballX, dy = p.y - ballY, distance = Math.max(.01, Math.hypot(dx, dy));
            if (distance >= RADIUS + 8) continue;
            p.x = ballX + dx / distance * (RADIUS + 8); p.y = ballY + dy / distance * (RADIUS + 8);
            velocityX *= .5; velocityY *= .5; // a pinned ball loses its speed instead of storing it
        }
        if (ballX < -8 || ballX > 968) goal(ballX > 968 ? 0 : 1); // the whole ball crossed the line inside the goal mouth
        if (remaining == 0 || Math.max(red, blue) >= goalLimit) finish();
    }
    void goal(int team) {
        if (team == 0) red++; else blue++;
        Player scorer = player(lastTouch);
        if (scorer != null && scorer.team == team) scorer.goals++;
        String call = scorer == null || scorer.team != team ? " (own goal)" : scorer.goals == 3 ? " - HAT-TRICK!"
                : scorer.goals == 2 ? " - that's a brace!" : "";
        say(message = "GOAL! " + (team == 0 ? "Red" : "Blue") + (scorer == null ? "" : ": " + scorer.name) + call);
        kickoff(2);
    }
    void finish() {
        String result = "";
        int winner = red > blue ? 0 : blue > red ? 1 : -1;
        Player best = null; // player of the match: most goals, then the winning side
        for (Player p : players) {
            if (p.team == 2) continue;
            result += p.name + "\t" + p.team + "\t" + p.goals + "\t" + red + "\t" + blue + "\n";
            if (best == null || p.goals > best.goals || p.goals == best.goals && p.team == winner && best.team != winner) best = p;
        }
        try {
            File target = new File(folder, matchId + ".match"), temp = new File(folder, matchId + ".pending");
            write(temp, result);
            if (!temp.renameTo(target)) throw new IOException("Could not rename " + temp);
            addResult(result, true);
            phase = "Result"; say(message = "Full time " + red + " - " + blue + (best == null ? "" : ". Player of the match: " + best.name));
            if (++roundsThisSession % 5 == 0) say(roundsThisSession + " rounds played - nice session! Stretch and hydrate.");
            for (Player p : players) p.input = 0;
        } catch (IOException error) { phase = "Save failed"; message = "Save failed; host presses Start to retry."; }
    }
    // One row per player: name, team, goals, red score, blue score. Elo: K = 24, each side uses its mean rating.
    void addResult(String result, boolean live) {
        String[] lines = result.trim().split("\n");
        String[] names = new String[lines.length];
        int[][] rows = new int[lines.length][]; // team, goals, red, blue -- all read before anything changes
        for (int i = 0; i < lines.length; i++) {
            String[] cells = lines[i].split("\t");
            names[i] = cells[0];
            rows[i] = new int[4];
            for (int c = 0; c < 4; c++) rows[i][c] = Integer.parseInt(cells[c + 1]);
        }
        double[] rating = new double[2], count = new double[2];
        for (int i = 0; i < rows.length; i++) { rating[rows[i][0]] += stats(names[i]).s[5]; count[rows[i][0]]++; }
        int home = rows[0][2], away = rows[0][3];
        double expected = 1 / (1 + Math.pow(10, (rating[1] / count[1] - rating[0] / count[0]) / 400));
        int change = (int) Math.round(24 * ((home > away ? 1 : home == away ? .5 : 0) - expected));
        for (int i = 0; i < rows.length; i++) {
            int[] row = rows[i], s = stats(names[i]).s;
            boolean draw = home == away, win = row[0] == 0 ? home > away : away > home;
            int before = level(xp(s)), badges = s[6], conceded = row[0] == 0 ? away : home;
            s[0]++; s[1] += win ? 1 : 0; s[2] += draw ? 1 : 0; s[3] += row[1]; s[4] += win ? 3 : draw ? 1 : 0;
            s[5] = Math.max(100, s[5] + (row[0] == 0 ? change : -change));
            s[7] = win ? s[7] + 1 : 0; s[8] = Math.max(s[8], s[7]);
            boolean[] earned = {s[3] >= 1, row[1] >= 3, s[7] >= 3, win && conceded == 0, s[0] >= 10, s[5] >= 1100,
                    win && row[1] == 0, s[7] >= 5};
            for (int b = 0; b < earned.length; b++) if (earned[b]) s[6] |= 1 << b;
            if (!live) continue;
            String who = stats(names[i]).name;
            for (int b = 0; b < BADGES.length; b++)
                if ((s[6] & ~badges & 1 << b) != 0) say("* " + who + " unlocked the " + BADGES[b] + " badge!");
            if (level(xp(s)) > before) say("* " + who + " reached level " + level(xp(s)) + "!");
        }
    }
    // Lines: H = header, M = message, P = player, C = chat, L = league row. Fields are separated by tabs.
    synchronized String snapshot() {
        long countdown = phase.equals("Playing") ? Math.max(0, (resumeAt - System.nanoTime() + 999999999L) / 1000000000L) : 0;
        StringBuilder text = new StringBuilder("H\t" + phase + "\t" + (int) Math.ceil(remaining) + "\t" + red + "\t" + blue
                + "\t" + (int) ballX + "\t" + (int) ballY + "\t" + countdown + "\t" + kicks + "\t" + bounces + "\t" + goalLimit
                + "\nM\t" + message + "\n");
        for (Player p : players)
            if (p.connected || phase.equals("Playing"))
                text.append("P\t" + p.name + "\t" + p.team + "\t" + (int) p.x + "\t" + (int) p.y + "\t" + ((p.input & 32) != 0)
                        + "\t" + p.connected + "\t" + p.shirt + "\t" + p.club + "\t" + p.goals + "\n");
        for (String line : chat) text.append("C\t" + line + "\n");
        Profile[] table = new Profile[profiles.size()];
        int n = 0;
        for (Profile p : profiles) { // insertion sort: points first, then Elo
            if (p.s[0] == 0) continue;
            int i = n++;
            for (; i > 0 && (p.s[4] > table[i - 1].s[4] || p.s[4] == table[i - 1].s[4] && p.s[5] > table[i - 1].s[5]); i--)
                table[i] = table[i - 1];
            table[i] = p;
        }
        for (int i = 0; i < n && i < 100; i++) {
            text.append("L\t" + table[i].name);
            for (int value : table[i].s) text.append("\t" + value);
            text.append("\n");
        }
        return text.toString();
    }
    synchronized void close() {
        running = false;
        try { listener.close(); for (Socket socket : sockets) socket.close(); lock.channel().close(); }
        catch (IOException ignored) { }
    }
}
// Module 3: a custom checked exception. The host sends its message back to the player who was refused.
class LoginException extends Exception {
    LoginException(String message) { super(message); }
}
