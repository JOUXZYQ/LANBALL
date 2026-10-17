package lanball;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

final class Server implements AutoCloseable {
    static final class Player {
        String name, club, shirt;
        double x, y, vx, vy;
        int team = 2, input, goals, assists, own, touches, kicks, revision, before = 1000;
        long lastInput, lastKick;
        boolean connected = true, ready, contact;
    }
    final League league;
    final Map<String, Player> players = new LinkedHashMap<>();
    final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    final Semaphore slots = new Semaphore(12);
    final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService disk = Executors.newSingleThreadExecutor();
    final List<String> replay = new ArrayList<>(), chat = new ArrayList<>();
    final ServerSocket listener;
    final FileChannel lockFile;
    DatagramSocket discovery;
    String phase = "Lobby", message = "Choose a team and mark ready.", id = "", lastTouch = "", assist = "";
    String event = "", pendingResult = "", season = "Season 1", fixture = "";
    int red, blue, target = 5, teamSize = 1, eventId, ticks, pitchVersion, duration = 180;
    double width = 960, height = 560, opening = 180, ballX = 480, ballY = 280, vx, vy, remaining;
    long deadline, lastTouchAt, assistAt, started;
    boolean ranked, practice, closed;
    volatile String frame = "", fingerprint;
    Server(Path folder, int port) throws Exception {
        league = new League(folder); lockFile = FileChannel.open(folder.resolve("host.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            if (lockFile.tryLock() == null) throw new IOException("This league is already hosted.");
            listener = Wire.identity(folder).getServerSocketFactory().createServerSocket(port);
            fingerprint = Files.readString(folder.resolve("fingerprint.txt"));
        } catch (Exception error) { lockFile.close(); throw error; }
        season = league.settings.getProperty("season", "Season 1");
        Wire.background(() -> {
            while (!listener.isClosed()) try {
                Socket socket = listener.accept();
                if (!slots.tryAcquire()) { socket.close(); continue; }
                sockets.add(socket); Wire.background(() -> serve(socket));
            } catch (IOException error) { if (!closed) error.printStackTrace(); }
        });
        clock.scheduleAtFixedRate(this::tick, 0, 16666667, TimeUnit.NANOSECONDS);
        try {
            discovery = new DatagramSocket(port + 1);
            Wire.background(() -> {
                while (!closed) try {
                    DatagramPacket packet = new DatagramPacket(new byte[128], 128); discovery.receive(packet);
                    byte[] reply = Wire.bytes("LanBall 4.1 | " + league.settings.getProperty("name", "Campus League"));
                    discovery.send(new DatagramPacket(reply, reply.length, packet.getSocketAddress()));
                } catch (IOException error) { return; }
            });
        } catch (IOException error) { league.problems.add("Discovery unavailable; direct IP still works."); }
    }
    void serve(Socket socket) {
        Player player = null; ArrayBlockingQueue<String> replies = new ArrayBlockingQueue<>(4);
        try (socket) {
            socket.setSoTimeout(10000); socket.setTcpNoDelay(true);
            var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream());
            String[] profile;
            try { profile = league.login(Wire.read(in, 512).split("\t", -1)); }
            catch (Exception error) { Wire.send(out, "E\t" + error.getMessage()); return; }
            synchronized (this) {
                Player existing = players.get(profile[0]);
                if (existing != null && existing.connected) throw new IOException("Already connected.");
                player = existing;
                if (player == null) { player = new Player(); players.put(profile[0], player); }
                player.name = profile[0]; player.club = profile[1]; player.shirt = profile[2]; player.connected = true;
            }
            Wire.send(out, "OK");
            Wire.background(() -> {
                try {
                    long next=System.nanoTime();
                    while (!socket.isClosed()) {
                        String reply = replies.poll(); if (reply != null) Wire.send(out, reply);
                        Wire.send(out, frame);next=Math.max(next+33_333_333L,System.nanoTime());
                        java.util.concurrent.locks.LockSupport.parkNanos(Math.max(1,next-System.nanoTime()));
                    }
                } catch (Exception error) { try { socket.close(); } catch (IOException ignored) { } }
            });
            long rateStart = System.nanoTime(); int messages = 0;
            while (!socket.isClosed()) {
                String[] command = Wire.read(in, 512).split("\t", -1);
                if (System.nanoTime() - rateStart > 1_000_000_000L) { rateStart = System.nanoTime(); messages = 0; }
                if (++messages > 150) throw new IOException("Input rate exceeded");
                if (command[0].equals("Q")) {
                    try {
                    String answer = switch (command[1]) {
                        case "league" -> league.report(command[2], command[3]);
                        case "history" -> league.history(command[2]);
                        case "replay" -> league.replay(command[2]);
                        default -> throw new IOException("Unknown request");
                    };
                    replies.offer("D\t" + command[1] + "\n" + answer);
                    } catch (Exception error) { replies.offer("E\t"+error.getMessage()); } continue;
                }
                synchronized (this) {
                    switch (command[0]) {
                        case "I" -> { player.input = Integer.parseInt(command[1]) & 31; player.lastInput = System.nanoTime(); }
                        case "T" -> chooseTeam(player, Integer.parseInt(command[1]));
                        case "Y" -> { if (editable()) player.ready = command[1].equals("1"); }
                        case "C" -> { chat.add(player.name + ": " + Wire.clean(command[1], 120)); if (chat.size() > 8) chat.remove(0); }
                        default -> throw new IOException("Unknown command");
                    }
                    if(command[0].equals("T")||command[0].equals("Y"))player.revision=command.length>2?Integer.parseInt(command[2]):0;
                }
            }
        } catch (Exception ignored) {
        } finally {
            synchronized (this) { if (player != null) { player.connected = false; player.input = 0; player.ready = false; } }
            sockets.remove(socket); slots.release();
        }
    }
    boolean editable() { return phase.equals("Lobby") || phase.equals("Result"); }
    void chooseTeam(Player player, int side) {
        if (!editable() || side < 0 || side > 2 || side == player.team) return;
        if (side < 2 && players.values().stream().filter(p -> p.connected && p.team == side).count() >= teamSize) return;
        player.team = side; player.ready = false;
    }
    synchronized String configure(int seconds, int goals, int size, boolean rated, double w, double h, double goal) {
        if (!editable()) return "Finish the current match first.";
        if (seconds < 30 || seconds > 600 || goals < 1 || goals > 20 || size < 1 || size > 3
                || !Double.isFinite(w + h + goal) || w < 720 || w > 1280 || h < 420 || h > 760
                || goal < 120 || goal > h * .55) return "Invalid settings or pitch dimensions.";
        duration = seconds; target = goals; teamSize = size; ranked = rated;
        width = rated ? 960 : w; height = rated ? 560 : h; opening = rated ? 180 : goal;
        players.values().forEach(p -> { p.ready = false; p.team = 2; }); pitchVersion++; kickoff(0);
        return message = "Settings applied. Choose teams and mark ready again.";
    }
    synchronized String startRound() {
        if (phase.equals("Save failed")) { persist(); return "Retrying save..."; }
        if (!editable()) return "A round is already active.";
        players.values().removeIf(p -> !p.connected);
        List<Player> roster = players.values().stream().filter(p -> p.team < 2).toList();
        if (!practice && (roster.size() != teamSize * 2 || roster.stream().anyMatch(p -> !p.ready)))
            return "Fill both teams and mark every player ready.";
        if (practice && roster.isEmpty()) return "Choose Red or Blue.";
        if(!fixture.isEmpty())try{
            String[] teams=league.settings.getProperty("cup.rosters","").split("\n"),pair=fixture.split(":")[1].split("-");
            if(league.matches().stream().anyMatch(r->r.split("\n")[0].split("\t",-1)[10].equals(fixture)
                    &&r.split("\t",5)[3].equals("Completed")))return "This fixture is already completed.";
            for(Player p:roster){String members=teams[Integer.parseInt(pair[p.team])].split(":")[1];
                if(Arrays.stream(members.split(",")).noneMatch(n->n.strip().equalsIgnoreCase(p.name)))return "Wrong fixture roster: "+p.name;}
        }catch(RuntimeException error){return "Invalid tournament configuration. Select a valid fixture.";}
        id = System.currentTimeMillis() + "-" + UUID.randomUUID(); started = System.nanoTime();
        remaining = duration; red = blue = 0; replay.clear();
        roster.forEach(p -> { p.goals = p.assists = p.own = p.touches = p.kicks = 0;
            p.before = league.rating(p.name); p.lastKick = 0; });
        kickoff(practice ? 0 : 3000); phase = "Countdown"; cue("whistle", "Get ready"); return message;
    }
    void cue(String sound, String text) { event = sound; eventId++; message = text; }
    void kickoff(int millis) {
        ballX = width / 2; ballY = height / 2; vx = vy = 0; lastTouch = assist = "";
        int[] count = {0, 0};
        for (Player p : players.values()) if (p.team < 2) {
            p.x = width * (p.team == 0 ? .25 : .75); p.y = height * (++count[p.team]) / (teamSize + 1);
            p.vx = p.vy = 0; p.contact = false;
        }
        deadline = System.nanoTime() + millis * 1_000_000L; pitchVersion++;
    }
    synchronized void tick() {
        if (closed) return;
        long now = System.nanoTime(); ticks++;
        if (ranked && (phase.equals("Playing") || phase.equals("Countdown") || phase.equals("Goal"))
                && players.values().stream().anyMatch(p -> p.team < 2 && !p.connected)) {
            phase = "Paused"; deadline = now + 30_000_000_000L; cue("whistle", "Waiting up to 30 seconds for reconnect");
        }
        if (phase.equals("Paused")) {
            if (players.values().stream().filter(p -> p.team < 2).allMatch(p -> p.connected)) {
                kickoff(3000); phase = "Countdown";
            } else if (now > deadline) finish(false);
        }
        if ((phase.equals("Countdown") || phase.equals("Goal")) && now >= deadline) {phase="Playing";message="PLAY / Red attacks right · Blue attacks left";}
        if (phase.equals("Playing")) {
            if (!practice) remaining = Math.max(0, remaining - 1.0 / 60);
            move(now); collide(); ball(now);
            if (!practice && (remaining == 0 || Math.max(red, blue) >= target)) finish(true);
        }
        if (ticks % 2 == 0) { frame = snapshot(); if (!editable() && replay.size() < 19000) replay.add(frame); }
    }
    void move(long now) {
        for (Player p : players.values()) if (p.team < 2) {
            int input = p.connected && now - p.lastInput < 300_000_000L ? p.input : 0;
            double dx = ((input & 2) != 0 ? 1 : 0) - ((input & 1) != 0 ? 1 : 0);
            double dy = ((input & 8) != 0 ? 1 : 0) - ((input & 4) != 0 ? 1 : 0), length = Math.max(1, Math.hypot(dx, dy));
            p.vx = (p.vx + dx / length * 17.5) * .93; p.vy = (p.vy + dy / length * 17.5) * .93;
            p.x = clamp(p.x + p.vx / 60, 16, width - 16); p.y = clamp(p.y + p.vy / 60, 16, height - 16);
        }
    }
    void collide() {
        List<Player> bodies = players.values().stream().filter(p -> p.team < 2 && p.connected).toList();
        for (int pass = 0; pass < 4; pass++) for (int i = 0; i < bodies.size(); i++) for (int j = i + 1; j < bodies.size(); j++) {
            Player a = bodies.get(i), b = bodies.get(j); double dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy);
            if (d >= 32) continue;
            double nx = d < .001 ? 1 : dx / d, ny = d < .001 ? 0 : dy / d, overlap = (32 - d) / 2;
            a.x = clamp(a.x - nx * overlap, 16, width - 16); a.y = clamp(a.y - ny * overlap, 16, height - 16);
            b.x = clamp(b.x + nx * overlap, 16, width - 16); b.y = clamp(b.y + ny * overlap, 16, height - 16);
            double relative = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
            if (relative < 0) { a.vx += nx * relative / 2; a.vy += ny * relative / 2;
                b.vx -= nx * relative / 2; b.vy -= ny * relative / 2; }
        }
    }
    void ball(long now) {
        for (Player p : players.values()) if (p.team < 2 && p.connected) {
            double dx = ballX - p.x, dy = ballY - p.y, d = Math.hypot(dx, dy);
            boolean kick = (p.input & 16) != 0 && now - p.lastInput < 300_000_000L && now - p.lastKick > 300_000_000L;
            if (d < (kick ? 30 : 24)) {
                double nx = d < .001 ? (p.team == 0 ? 1 : -1) : dx / d, ny = d < .001 ? 0 : dy / d;
                if (kick || !p.contact) {
                    p.touches++; Player previous = players.get(lastTouch);
                    if(previous==null || previous.team!=p.team || now-lastTouchAt>=5_000_000_000L)assist="";
                    else if(!previous.name.equals(p.name)){assist=previous.name;assistAt=lastTouchAt;}
                    lastTouch = p.name; lastTouchAt = now;
                }
                ballX = p.x + nx * 24.1; ballY = p.y + ny * 24.1;
                vx = nx * (kick ? 600 : 150) + p.vx * .45; vy = ny * (kick ? 600 : 150) + p.vy * .45;
                if (kick) { p.kicks++; p.lastKick = now; cue("kick", ""); }
                p.contact = true;
            } else p.contact = false;
        }
        ballX += vx / 60; ballY += vy / 60; vx *= .989; vy *= .989;
        if (ballY < 8 || ballY > height - 8) { vy *= -.8; ballY = clamp(ballY, 8, height - 8); cue("wall", ""); }
        for (double x : new double[]{0, width}) for (double y : new double[]{(height-opening)/2, (height+opening)/2}) {
            double dx = ballX-x, dy = ballY-y, d = Math.hypot(dx,dy);
            if (d < 13) { double nx = d < .001 ? 1 : dx/d, ny = d < .001 ? 0 : dy/d;
                ballX = x+nx*13; ballY = y+ny*13; double dot = vx*nx+vy*ny;
                if (dot < 0) { vx -= 1.8*dot*nx; vy -= 1.8*dot*ny; } cue("wall", ""); }
        }
        if (ballX < 8 || ballX > width - 8) {
            if (Math.abs(ballY - height / 2) < opening / 2 - 8) {
                if (ballX < -8 || ballX > width + 8) {
                    int team = ballX > width ? 0 : 1; if (team == 0) red++; else blue++;
                    Player scorer = players.get(lastTouch), helper = players.get(assist);
                    if (scorer != null) { if (scorer.team == team) { scorer.goals++;
                        if (helper != null && helper.team==team && now-assistAt<5_000_000_000L) helper.assists++; }
                        else scorer.own++; }
                    kickoff(2000); phase = "Goal"; cue("goal", (team == 0 ? "RED" : "BLUE") + " GOAL!");
                }
            } else { vx *= -.8; ballX = clamp(ballX, 8, width - 8); cue("wall", ""); }
        }
    }
    static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    synchronized void finish(boolean completed) {
        if (editable() || phase.equals("Saving") || phase.equals("Save failed")) return;
        boolean rated = completed && ranked && players.values().stream().filter(p -> p.team < 2).allMatch(p -> p.connected);
        double home = mean(0), away = mean(1), result = red == blue ? .5 : red > blue ? 1 : 0;
        int change = rated ? (int)Math.round(24 * (result - 1 / (1 + Math.pow(10, (away-home)/400)))) : 0;
        StringBuilder text = new StringBuilder(String.join("\t", "R", id, season, completed ? "Completed" : "Abandoned",
                ""+red, ""+blue, ""+width, ""+height, ""+opening, ""+duration, fixture, ""+rated)).append('\n');
        for (Player p : players.values()) if (p.team < 2) text.append(String.join("\t", "P", p.name, ""+p.team,
                ""+p.goals, ""+p.assists, ""+p.own, ""+p.touches, ""+p.kicks, p.connected ? "1" : "0",
                ""+p.before, ""+Math.max(100, p.before + (p.team == 0 ? change : -change)))).append('\n');
        pendingResult = text.append("END\n").toString();phase=completed?"Result":"Abandoned";replay.add(snapshot());persist();
    }
    double mean(int side) { return players.values().stream().filter(p -> p.team == side).mapToInt(p -> p.before).average().orElse(1000); }
    void persist() {
        phase = "Saving"; String result = pendingResult; List<String> frames = List.copyOf(replay);
        disk.submit(() -> {
            try {
                league.commit(result, frames);
                synchronized (this) { phase = "Result"; players.values().forEach(p -> p.ready = false);
                    cue("whistle", "Saved. Rematch when ready, or take a break."); }
            } catch (IOException error) { synchronized (this) { phase = "Save failed"; message = "Save failed. Start retries."; } }
        });
    }
    synchronized void balance() {
        if (!editable()) return;
        List<Player> list = players.values().stream().filter(p -> p.connected).limit(teamSize*2).toList();
        int n = list.size(), best = 0; double gap = Double.MAX_VALUE;
        if (n != teamSize*2) { message = "Need " + teamSize*2 + " connected players."; return; }
        for (int mask = 0; mask < (1 << n); mask++) if (Integer.bitCount(mask) == teamSize) {
            double difference = 0;
            for (int i = 0; i < n; i++) difference += league.rating(list.get(i).name) * ((mask & 1<<i) == 0 ? -1 : 1);
            if (Math.abs(difference) < gap) { gap = Math.abs(difference); best = mask; }
        }
        for (int i = 0; i < n; i++) { list.get(i).team = (best & 1<<i) == 0 ? 0 : 1; list.get(i).ready = false; }
        message = "Balanced by Elo. Mark ready again.";
    }
    String snapshot() {
        StringBuilder text = new StringBuilder(String.join("\t", "H", phase, ""+(int)Math.ceil(remaining), ""+red, ""+blue,
                ""+ballX, ""+ballY, ""+width, ""+height, ""+opening, ""+eventId, event, ""+pitchVersion, ""+ticks)).append('\n');
        text.append("M\t").append(message).append('\n');
        for (Player p : players.values()) if (p.connected || !editable()) text.append(String.join("\t", "P", p.name,
                ""+p.team, ""+p.x, ""+p.y, ""+p.ready, ""+p.connected, p.shirt, p.club,""+p.revision)).append('\n');
        for (String line : chat) text.append("C\t").append(line).append('\n'); return text.toString();
    }
    public void close() {
        synchronized (this) { if (closed) return; if (!practice) finish(false); closed = true; }
        clock.shutdownNow(); if (discovery != null) discovery.close();
        try { listener.close(); for (Socket socket : sockets) socket.close(); } catch (IOException ignored) { }
        disk.shutdown(); Wire.background(() -> { try { disk.awaitTermination(30, TimeUnit.SECONDS); lockFile.close(); }
            catch (Exception ignored) { } });
    }
}
