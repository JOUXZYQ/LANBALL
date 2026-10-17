package lanball;
import java.io.*;
import java.nio.file.*;
import java.util.*;

// Disk writes never run on the physics thread. Each result is a self-contained commit.
final class League {
    final Path directory;
    final Map<String, String[]> profiles = new TreeMap<>();
    final List<String> problems = new ArrayList<>(), records = new ArrayList<>();
    final Properties settings = new Properties();
    final Map<String, int[]> totals = new TreeMap<>();
    League(Path directory) throws IOException {
        this.directory = directory; Files.createDirectories(directory);
        Path configuration = directory.resolve("league.properties");
        try { if (Files.exists(configuration)) settings.load(new StringReader(Files.readString(configuration))); }
        catch (Exception error) { problems.add("league.properties: " + error.getMessage()); }
        try (var files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) try {
                if (file.toString().endsWith(".profile")) {
                    String[] p = Files.readString(file).strip().split("\t", -1);
                    if (p.length != 5 || !p[0].matches("[A-Za-z0-9_-]{2,16}")
                            || Base64.getDecoder().decode(p[4]).length != 32) throw new IOException("Invalid profile");
                    if(profiles.putIfAbsent(p[0].toLowerCase(Locale.ROOT),p)!=null)throw new IOException("Duplicate profile");
                }
                if (file.toString().endsWith(".match")) { String text = Files.readString(file); validate(text);
                    if(records.stream().anyMatch(r->r.split("\t",3)[1].equals(text.split("\t",3)[1])))throw new IOException("Duplicate match ID");
                    records.add(text); }
            } catch (Exception error) { problems.add(file.getFileName() + ": " + error.getMessage() + " (kept unchanged)"); }
        }
        records.sort(Comparator.comparing(text -> text.split("\t", 3)[1])); rebuild();
    }
    static void atomic(Path path, String text) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".pending");
        Files.writeString(temporary, text);
        if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    synchronized String[] profile(String name) { return profiles.get(name.toLowerCase(Locale.ROOT)); }
    String[] login(String[] values) throws Exception {
        if (values.length != 6 || !values[0].equals("LB41")) throw new IOException("Use a v4.1 client.");
        String name = values[1].strip(), pin = values[2], club = values[3].strip(), shirt = values[4];
        if (!name.matches("[A-Za-z0-9_-]{2,16}") || !pin.matches("[0-9]{6,12}"))
            throw new IOException("Name: 2-16 letters/digits/_/-. PIN: 6-12 digits.");
        String[] found = profile(name);
        if (found != null) {
            if (!java.security.MessageDigest.isEqual(Wire.bytes(Wire.hash(pin, found[3])), Wire.bytes(found[4])))
                throw new IOException("Incorrect PIN.");
            return found;
        }
        if (!values[5].equals("create")) throw new IOException("Profile not found. Select Create profile.");
        if (!club.matches("[A-Za-z0-9 _-]{2,24}") || !shirt.matches("[1-9][0-9]?"))
            throw new IOException("Club: 2-24 letters/digits/spaces; shirt: 1-99.");
        String salt = UUID.randomUUID().toString();
        String[] created = {name, club, shirt, salt, Wire.hash(pin, salt)};
        synchronized (this) {
            if (profiles.size() >= 500 || profile(name) != null) throw new IOException("Profile exists or league is full.");
            Files.writeString(directory.resolve("player-" + name.toLowerCase(Locale.ROOT) + ".profile"),
                    String.join("\t", created), StandardOpenOption.CREATE_NEW);
            profiles.put(name.toLowerCase(Locale.ROOT), created);
        }
        return created;
    }
    static void validate(String text) throws IOException {
        String[] lines = text.strip().split("\n"); Set<String> names = new HashSet<>();
        try {
            String[] header = lines[0].split("\t", -1);
            if (header.length != 12 || !header[0].equals("R") || !lines[lines.length - 1].equals("END"))
                throw new IOException("Incomplete or unsupported match");
            if(!header[1].matches("[A-Za-z0-9_-]{1,80}")||!Set.of("Completed","Abandoned").contains(header[3]))throw new IOException("Invalid match identity");
            for (int i : new int[]{4, 5, 6, 7, 8, 9}) if (!Double.isFinite(Double.parseDouble(header[i])))
                throw new IOException("Non-finite result");
            if(Integer.parseInt(header[4])<0||Integer.parseInt(header[5])<0)throw new IOException("Invalid score");
            for (int i = 1; i < lines.length - 1; i++) {
                String[] p = lines[i].split("\t", -1);
                if (p.length != 11 || !p[0].equals("P") || !p[1].matches("[A-Za-z0-9_-]{2,16}") || !names.add(p[1].toLowerCase(Locale.ROOT)))
                    throw new IOException("Invalid participant");
                for (int n = 2; n < p.length; n++) if (Integer.parseInt(p[n]) < 0) throw new IOException("Negative statistic");
                if (Integer.parseInt(p[2]) > 1) throw new IOException("Invalid team");
            }
            if (names.isEmpty()) throw new IOException("No participants");
        } catch (RuntimeException error) { throw new IOException("Damaged match", error); }
    }
    synchronized void commit(String text, List<String> replay) throws IOException {
        validate(text); String id = text.split("\t", 3)[1]; Path file = directory.resolve(id + ".match");
        if (!Files.exists(file)) atomic(file, text);
        if (records.stream().noneMatch(r -> r.split("\t", 3)[1].equals(id))) { records.add(text); rebuild(); }
        try { atomic(directory.resolve(id + ".replay"), String.join("\n~\n", replay)); }
        catch (IOException error) { problems.add("Replay unavailable: " + id); }
    }
    synchronized void rebuild() { totals.clear(); for (String record : records) accumulate(totals, record); }
    static void accumulate(Map<String, int[]> rows, String record) {
        String[] lines = record.split("\n"), header = lines[0].split("\t", -1);
        if (!header[3].equals("Completed")) return;
        int red = Integer.parseInt(header[4]), blue = Integer.parseInt(header[5]);
        for (int i = 1; i < lines.length - 1; i++) {
            String[] p = lines[i].split("\t"); int side = Integer.parseInt(p[2]);
            int[] row = rows.computeIfAbsent(p[1], key -> new int[]{0, 0, 0, 0, 0, 0, 0, 1000});
            boolean win = side == 0 ? red > blue : blue > red, draw = red == blue;
            row[0]++; row[1] += win ? 1 : 0; row[2] += draw ? 1 : 0; row[3] += !win && !draw ? 1 : 0;
            row[4] += Integer.parseInt(p[3]); row[5] += Integer.parseInt(p[4]); row[6] += win ? 3 : draw ? 1 : 0;
            row[7] = Integer.parseInt(p[10]);
        }
    }
    synchronized int rating(String name) { return totals.getOrDefault(name, new int[]{0,0,0,0,0,0,0,1000})[7]; }
    synchronized List<String> matches(){return List.copyOf(records);}
    synchronized String report(String season, String player) {
        Map<String, int[]> rows = new TreeMap<>();
        records.stream().filter(r -> season.isBlank() || r.split("\t", 4)[2].equals(season)).forEach(r -> accumulate(rows, r));
        StringBuilder text = new StringBuilder("Player\tPlayed\tWon\tDrawn\tLost\tGoals\tAssists\tPoints\tElo\n");
        rows.entrySet().stream().filter(e -> player.isBlank() || e.getKey().equalsIgnoreCase(player))
                .sorted((a, b) -> Integer.compare(rating(b.getKey()), rating(a.getKey()))).forEach(e -> {
                    int[] row = e.getValue(); row[7] = rating(e.getKey()); text.append(e.getKey());
                    for (int value : row) text.append('\t').append(value); text.append('\n');
                });
        return text.toString();
    }
    synchronized String history(String player) {
        StringBuilder text = new StringBuilder("Match\tSeason\tStatus\tRed\tBlue\tPlayer\tTeam\tGoals\tAssists\tOwn\tTouches\tKicks\tPresent\tElo before\tElo after\n");
        for (String record : records) {
            String[] lines = record.split("\n"), h = lines[0].split("\t", -1);
            for (int i = 1; i < lines.length - 1; i++) {
                String[] p = lines[i].split("\t"); if (!player.isBlank() && !p[1].equalsIgnoreCase(player)) continue;
                text.append(String.join("\t", h[1], h[2], h[3], h[4], h[5])).append('\t');
                text.append(String.join("\t", Arrays.copyOfRange(p, 1, p.length))).append('\n');
            }
        }
        return text.toString();
    }
    synchronized void setting(String key, String value) throws IOException {
        settings.setProperty(key, value); StringWriter writer = new StringWriter(); settings.store(writer, "LanBall league");
        atomic(directory.resolve("league.properties"), writer.toString());
    }
    synchronized String replay(String id) throws IOException {
        if (!id.matches("[A-Za-z0-9_-]{1,80}")) throw new IOException("Select a valid match ID.");
        Path file = directory.resolve(id + ".replay");
        if (Files.size(file) > 4_000_000) throw new IOException("Replay exceeds download limit.");
        return Files.readString(file);
    }
}
