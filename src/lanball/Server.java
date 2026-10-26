package lanball;

import java.io.*;
import java.net.*;
import java.nio.channels.FileLock;
import java.security.GeneralSecurityException;
import java.util.*;

/** The host owns the match; clients send keys and receive snapshots. */
public class Server {
    static final int PORT = Integer.getInteger("lanball.port", 45888);
    final Match match = new Match();
    final League league;
    final Accounts accounts;
    final File root, pitches;
    final Vector<Connection> connections = new Vector<Connection>();
    final ServerSocket listener;
    final FileLock lock;
    Replay recording;
    MobileServer mobile;
    String mobileStatus = "";
    League.Result pending;
    long previousTick = System.nanoTime();
    volatile boolean running = true;

    Server(File root, int port) throws IOException {
        this.root = root;
        root.mkdirs();
        pitches = new File(root, "pitches");
        pitches.mkdirs();
        java.nio.channels.FileChannel channel =
                new RandomAccessFile(new File(root, "host.lock"), "rw").getChannel();
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("This data folder is already hosted.");
        } catch (RuntimeException e) {
            channel.close();
            throw new IOException("This data folder is already hosted.");
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        try {
            league = new League(new File(root, "matches"));
            accounts = new Accounts(root);
            File selected = new File(root, "selected.pitch");
            if (selected.exists()) {
                try {
                    match.arena = Arena.parse(Files.read(selected));
                    match.ball.goalHalf = match.arena.goalHalf;
                } catch (IOException invalid) {
                    match.message = "Saved pitch unavailable. Using Classic.";
                }
            }
            listener = new ServerSocket(port);
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        start(new Acceptor());
        start(new Clock());
    }

    static void quietClose(Closeable c) {
        if (c != null)
            try {
                c.close();
            } catch (IOException ignored) {
            }
    }

    static void start(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    class Acceptor implements Runnable {
        public void run() {
            while (running)
                try {
                    Socket s = listener.accept();
                    synchronized (Server.this) {
                        if (connections.size() >= 24) s.close();
                        else {
                            Connection c = new Connection(s);
                            connections.add(c);
                            start(c);
                        }
                    }
                } catch (IOException e) {
                    if (running) match.message = "Connection error: " + e.getMessage();
                }
        }
    }

    class Clock implements Runnable {
        public void run() {
            while (running) {
                tick();
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    class Connection implements Runnable {
        final Socket socket;
        final long opened = System.nanoTime();
        Player player;
        int leaguePage;
        String notice = "";

        Connection(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                socket.setSoTimeout(8000);
                socket.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out =
                        new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                try {
                    login(this, in, out);
                    out.writeUTF("OK");
                    out.flush();
                } catch (IOException e) {
                    out.writeUTF("ERROR: " + e.getMessage());
                    out.flush();
                    return;
                }
                while (running) {
                    int keys = in.readInt(), team = in.readInt();
                    String command = in.readUTF(), chat = in.readUTF();
                    String reply, frame;
                    synchronized (Server.this) {
                        player.receiveInput(keys, System.nanoTime());
                        notice = match.chooseTeam(player, team);
                        chat = Files.clean(chat, 80);
                        if (!chat.isEmpty()) match.say(player.name + ": " + chat);
                        try {
                            reply = command(this, command);
                        } catch (IOException e) {
                            reply = "ERROR\n" + e.getMessage();
                        }
                        frame =
                                snapshot(leaguePage)
                                        + (notice.isEmpty() ? "" : "N\t" + notice + "\n");
                    }
                    out.writeUTF(frame);
                    out.writeUTF(reply);
                    out.flush();
                    Thread.sleep(15);
                }
            } catch (IOException e) {
            } catch (InterruptedException e) {
            } finally {
                synchronized (Server.this) {
                    if (player != null) leavePlayer(player);
                    connections.remove(this);
                    quietClose(socket);
                }
            }
        }
    }

    void login(Connection c, DataInputStream in, DataOutputStream out) throws IOException {
        String[] h = in.readUTF().split("\t", -1);
        if (h.length != 5 || !h[0].equals("LB9"))
            throw new IOException("Use the LanBall v9 client.");
        String name = h[1].trim(), club = h[2].trim(), shirt = h[3].trim();
        boolean create = h[4].equals("create");
        if (!Accounts.validName(name)
                || !club.matches("[A-Za-z0-9 _-]{2,24}")
                || !shirt.matches("[1-9][0-9]?"))
            throw new IOException("Name: 2-16 letters/digits; club: 2-24; shirt: 1-99.");
        String[] account = accounts.read(name);
        if (create && account != null) throw new IOException("Account exists. Choose Sign in.");
        if (!create && account == null)
            throw new IOException("Account not found. Choose Create account.");
        String salt = create ? Accounts.random() : account[3], nonce = Accounts.random();
        out.writeUTF(salt + "\t" + nonce + "\t" + (create ? "new" : "old"));
        out.flush();
        String answer = in.readUTF();
        try {
            if (!create && !Accounts.same(answer, Accounts.proof(account[4], nonce)))
                throw new IOException("Incorrect password.");
        } catch (GeneralSecurityException e) {
            throw new IOException("Password verification unavailable.");
        }
        synchronized (this) {
            if (create) account = accounts.create(new String[] {name, club, shirt, salt, answer});
            c.player = joinPlayer(account);
        }
    }

    /** Desktop and browser players enter the same roster and obey the same rules. */
    synchronized Player joinPlayer(String[] account) throws IOException {
        Player p = match.find(account[0]);
        if (p != null && p.connected) throw new IOException("That player is already connected.");
        if (match.connectedCount() >= 12) throw new IOException("Room full: up to 12 connections.");
        if (p == null) {
            p = new Player(account[0]);
            match.players.add(p);
        }
        p.club = account[1];
        p.shirt = account[2];
        p.connected = true;
        p.input = 0;
        if ((match.phase.equals("Playing") || match.phase.equals("Save failed"))
                && p.matchTeam < 2) {
            p.team = p.matchTeam;
            placeReturning(p);
        } else p.team = 2;
        match.say(p.name + " joined");
        return p;
    }

    synchronized void leavePlayer(Player p) {
        p.kickUntil = 0;
        p.connected = false;
        p.input = 0;
        boolean reserved =
                p.matchTeam < 2
                        && (match.phase.equals("Playing") || match.phase.equals("Save failed"));
        if (!reserved) match.players.remove(p);
    }

    synchronized void enableMobile() {
        try {
            mobile = new MobileServer(this, Integer.getInteger("lanball.mobilePort", 45890));
            mobileStatus = "Phone: http://<host IP>:" + mobile.port();
        } catch (IOException e) {
            mobileStatus = "Phone server unavailable: " + e.getMessage();
        }
    }

    void placeReturning(Player p) {
        if (clearSpot(p)) return;
        for (int y = 50; y <= 490; y += 40)
            for (int x = 50; x <= 910; x += 40) {
                p.x = x;
                p.y = y;
                if (clearSpot(p)) return;
            }
    }

    boolean clearSpot(Player p) {
        if (p.distanceTo(match.ball) < Match.TOUCH + 1) return false;
        for (Player other : match.players)
            if (other != p && other.connected && other.team < 2 && p.distanceTo(other) < 33)
                return false;
        for (Circle c : match.arena.bumpers)
            if (p.distanceTo(c) < p.radius + c.radius + 1) return false;
        return true;
    }

    synchronized void tick() {
        long now = System.nanoTime();
        double elapsed = (now - previousTick) / 1e9;
        previousTick = now;
        if (mobile != null) mobile.expire(now);
        for (Connection c : connections)
            if (c.player == null && now - c.opened > 5000000000L) quietClose(c.socket);
        boolean wasPlaying = match.phase.equals("Playing");
        boolean ended = match.update(elapsed, now);
        if (wasPlaying && recording != null && (ended || now - recording.lastCapture >= 50000000L))
            recording.capture(match.frame(now), now);
        if (ended) {
            pending = league.result(match);
            finish();
        }
    }

    void finish() {
        if (pending == null) return;
        try {
            Files.writeBytes(
                    new File(root, "replays/" + pending.id + ".replay"), recording.bytes());
            league.save(pending);
            match.phase = "Result";
            match.message =
                    "Full time: Red " + match.red + " - " + match.blue + " Blue. Match saved.";
            for (Player p : match.players) p.input = 0;
            pending = null;
        } catch (IOException e) {
            match.phase = "Save failed";
            match.message =
                    "Result kept in memory. Press Retry save: " + Files.clean(e.getMessage(), 100);
        }
    }

    synchronized String startRound(int seconds, int goals) {
        if (pending != null) {
            finish();
            return match.message;
        }
        String text = match.start(seconds, goals);
        if (text.startsWith("Kick-off")) {
            match.resultId = "M" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString();
            recording = new Replay();
            recording.capture(match.frame(System.nanoTime()), System.nanoTime());
            previousTick = System.nanoTime();
        }
        return text;
    }

    synchronized String snapshot(int page) {
        String s = match.frame(System.nanoTime()) + "M\t" + Files.clean(match.message, 180) + "\n";
        for (String line : match.chat) s += "C\t" + line + "\n";
        return s + league.rows(page);
    }

    String command(Connection c, String command) throws IOException {
        if (command.isEmpty()) return "";
        String[] p = command.split("\t");
        try {
            if (p[0].equals("HISTORY")) return league.history(Integer.parseInt(p[1]));
            if (p[0].equals("DETAIL")) return "DETAIL\n" + league.detail(p[1]);
            if (p[0].equals("PROFILE")) {
                if (!Accounts.validName(p[1])) throw new IOException("Enter a valid player name.");
                return "PROFILE\n" + league.profile(p[1]);
            }
            if (p[0].equals("LEAGUE")) {
                c.leaguePage = Math.max(0, Integer.parseInt(p[1]));
                return "";
            }
            if (p[0].equals("REPLAY")) {
                if (!p[1].matches("M[0-9]+-[a-f0-9-]+"))
                    throw new IOException("Invalid replay ID.");
                File file = new File(root, "replays/" + p[1] + ".replay");
                long offset = Long.parseLong(p[2]);
                if (!file.exists()
                        || file.length() > Replay.LIMIT
                        || offset < 0
                        || offset > file.length()) throw new IOException("Replay unavailable.");
                byte[] bytes = new byte[(int) Math.min(18000, file.length() - offset)];
                RandomAccessFile input = new RandomAccessFile(file, "r");
                try {
                    input.seek(offset);
                    input.readFully(bytes);
                } finally {
                    input.close();
                }
                return "REPLAY\t"
                        + p[1]
                        + "\t"
                        + offset
                        + "\t"
                        + file.length()
                        + "\t"
                        + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid request number.");
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("Incomplete request.");
        }
        return "ERROR\nUnknown command.";
    }

    synchronized void useArena(Arena a) throws IOException {
        if (match.phase.equals("Playing") || pending != null)
            throw new IOException("Change the pitch between matches.");
        a = Arena.parse(a.rows());
        Files.write(new File(pitches, a.name + ".pitch"), a.rows());
        Files.write(new File(root, "selected.pitch"), a.rows());
        match.arena = a;
        match.ball.goalHalf = a.goalHalf;
        match.phase = "Lobby";
    }

    synchronized void useSeason(String s) throws IOException {
        if (match.phase.equals("Playing") || pending != null)
            throw new IOException("Change seasons between matches.");
        league.season(s);
    }

    synchronized void close() {
        running = false;
        if (mobile != null) mobile.close();
        quietClose(listener);
        for (Connection c : connections) quietClose(c.socket);
        quietClose(lock.channel());
    }
}
