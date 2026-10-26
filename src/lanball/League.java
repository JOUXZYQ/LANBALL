package lanball;

import java.io.*;
import java.util.*;

/** Match files are the source of truth. Totals and Elo are rebuilt in date order. */
public class League {
    static class Totals {
        final String name;
        int played, won, drawn, lost, goals, assists, elo = 1000;

        Totals(String name) {
            this.name = name;
        }

        int points() {
            return 3 * won + drawn;
        }

        String row() {
            return "L\t" + name + "\t" + played + "\t" + won + "\t" + drawn + "\t" + lost + "\t"
                    + goals + "\t" + assists + "\t" + points() + "\t" + elo + "\n";
        }
    }

    static class Result {
        String id, season, pitch;
        int red, blue;
        final Vector<Player> players = new Vector<Player>();

        String text() {
            String s = "R\t" + id + "\t" + season + "\t" + pitch + "\t" + red + "\t" + blue + "\n";
            for (Player p : players)
                s += p.name + "\t" + p.matchTeam + "\t" + p.goals + "\t" + p.assists + "\n";
            return s;
        }

        String summary() {
            return id + " | " + season + " | " + pitch + " | Red " + red + " - " + blue + " Blue";
        }
    }

    final File folder;
    final Vector<Result> history = new Vector<Result>();
    final Vector<Totals> records = new Vector<Totals>();
    String season = "Semester-1", skipped = "";

    League(File folder) throws IOException {
        this.folder = folder;
        folder.mkdirs();
        File config = new File(folder, "season.txt");
        if (config.exists()) season = Files.clean(Files.read(config), 24);
        File[] list = folder.listFiles();
        if (list == null) throw new IOException("Cannot read league folder.");
        Arrays.sort(list);
        for (File file : list)
            if (file.getName().endsWith(".match"))
                try {
                    Result r = parse(Files.read(file));
                    history.add(r);
                } catch (IOException e) {
                    skipped += file.getName() + " ";
                }
        rebuild();
    }

    Totals totals(String name) {
        for (Totals t : records) if (t.name.equalsIgnoreCase(name)) return t;
        Totals t = new Totals(name);
        records.add(t);
        return t;
    }

    static Result parse(String text) throws IOException {
        String[] lines = text.trim().split("\n");
        Result r = new Result();
        try {
            String[] h = lines[0].trim().split("\t");
            if (h.length != 6
                    || !h[0].equals("R")
                    || !h[1].matches("M[0-9]+-[a-f0-9-]+")
                    || !h[2].matches("[A-Za-z0-9 _-]{1,24}")
                    || !h[3].matches("[A-Za-z0-9 _-]{2,24}")
                    || lines.length < 3
                    || lines.length > 7) throw new IOException("Invalid match header.");
            r.id = h[1];
            r.season = h[2];
            r.pitch = h[3];
            r.red = Integer.parseInt(h[4]);
            r.blue = Integer.parseInt(h[5]);
            if (r.red < 0 || r.blue < 0 || r.red > 99 || r.blue > 99)
                throw new IOException("Invalid score.");
            int[] counts = {0, 0}, goals = {0, 0}, assists = {0, 0};
            for (int i = 1; i < lines.length; i++) {
                String[] c = lines[i].trim().split("\t");
                if (c.length != 4 || !Accounts.validName(c[0]))
                    throw new IOException("Invalid participant.");
                for (Player p : r.players)
                    if (p.name.equalsIgnoreCase(c[0]))
                        throw new IOException("Duplicate participant.");
                Player p = new Player(c[0]);
                p.matchTeam = Integer.parseInt(c[1]);
                p.goals = Integer.parseInt(c[2]);
                p.assists = Integer.parseInt(c[3]);
                if (p.matchTeam < 0
                        || p.matchTeam > 1
                        || p.goals < 0
                        || p.assists < 0
                        || p.goals > 99
                        || p.assists > 99) throw new IOException("Invalid player totals.");
                counts[p.matchTeam]++;
                goals[p.matchTeam] += p.goals;
                assists[p.matchTeam] += p.assists;
                r.players.add(p);
            }
            if (counts[0] < 1
                    || counts[1] < 1
                    || counts[0] > 3
                    || counts[1] > 3
                    || goals[0] > r.red
                    || goals[1] > r.blue
                    || assists[0] > goals[0]
                    || assists[1] > goals[1]) throw new IOException("Invalid team totals.");
        } catch (NumberFormatException e) {
            throw new IOException("Invalid match number.");
        }
        return r;
    }

    Result result(Match m) {
        Result r = new Result();
        r.id = m.resultId;
        r.season = season;
        r.pitch = m.arena.name;
        r.red = m.red;
        r.blue = m.blue;
        for (Player p : m.players)
            if (p.matchTeam < 2) {
                Player copy = new Player(p.name);
                copy.matchTeam = p.matchTeam;
                copy.goals = p.goals;
                copy.assists = p.assists;
                r.players.add(copy);
            }
        return r;
    }

    void save(Result r) throws IOException {
        parse(r.text());
        for (Result old : history) if (old.id.equals(r.id)) return;
        Files.write(new File(folder, r.id + ".match"), r.text());
        history.add(r);
        rebuild();
    }

    void rebuild() {
        records.clear();
        for (Result r : history)
            if (r.season.equals(season)) {
                double[] average = {0, 0};
                int[] count = {0, 0};
                for (Player p : r.players) {
                    average[p.matchTeam] += totals(p.name).elo;
                    count[p.matchTeam]++;
                }
                double expected =
                        1
                                / (1
                                        + Math.pow(
                                                10,
                                                (average[1] / count[1] - average[0] / count[0])
                                                        / 400));
                int change =
                        (int)
                                Math.round(
                                        24
                                                * ((r.red > r.blue ? 1 : r.red == r.blue ? .5 : 0)
                                                        - expected));
                for (Player p : r.players) {
                    Totals t = totals(p.name);
                    int own = p.matchTeam == 0 ? r.red : r.blue,
                            other = p.matchTeam == 0 ? r.blue : r.red;
                    t.played++;
                    t.goals += p.goals;
                    t.assists += p.assists;
                    if (own > other) t.won++;
                    else if (own == other) t.drawn++;
                    else t.lost++;
                    t.elo = Math.max(100, t.elo + (p.matchTeam == 0 ? change : -change));
                }
            }
    }

    void season(String name) throws IOException {
        if (!name.matches("[A-Za-z0-9 _-]{1,24}"))
            throw new IOException("Season: 1-24 letters, digits or spaces.");
        Files.write(new File(folder, "season.txt"), name);
        season = name;
        rebuild();
    }

    String rows(int page) {
        Vector<Totals> table = new Vector<Totals>();
        for (Totals t : records) {
            int i = 0;
            while (i < table.size()
                    && (table.get(i).elo > t.elo
                            || table.get(i).elo == t.elo && table.get(i).points() >= t.points()))
                i++;
            table.add(i, t);
        }
        String text = "S\t" + season + "\t" + table.size() + "\n";
        int from = Math.min(Math.max(0, page), table.size() / 50) * 50;
        for (int i = from; i < Math.min(from + 50, table.size()); i++) text += table.get(i).row();
        return text;
    }

    String history(int page) {
        String text = "HISTORY\n";
        int start = history.size() - 1 - Math.min(Math.max(0, page), history.size() / 30) * 30;
        for (int i = start; i >= 0 && i > start - 30; i--) text += history.get(i).summary() + "\n";
        return text;
    }

    String detail(String id) {
        for (Result r : history)
            if (r.id.equals(id)) {
                String text = r.summary() + "\n\n";
                for (Player p : r.players)
                    text +=
                            p.name
                                    + " | "
                                    + (p.matchTeam == 0 ? "Red" : "Blue")
                                    + " | goals "
                                    + p.goals
                                    + " | assists "
                                    + p.assists
                                    + "\n";
                return text;
            }
        return "Match not found.";
    }

    String profile(String name) {
        Totals t = new Totals(name);
        for (Totals existing : records)
            if (existing.name.equalsIgnoreCase(name)) {
                t = existing;
                break;
            }
        String text =
                name
                        + " | "
                        + season
                        + "\nElo "
                        + t.elo
                        + " | Points "
                        + t.points()
                        + "\nPlayed "
                        + t.played
                        + " | W "
                        + t.won
                        + " D "
                        + t.drawn
                        + " L "
                        + t.lost
                        + "\nGoals "
                        + t.goals
                        + " | Assists "
                        + t.assists
                        + "\n\nRecent matches\n";
        int n = 0;
        for (int i = history.size() - 1; i >= 0 && n < 15; i--) {
            Result r = history.get(i);
            for (Player p : r.players)
                if (p.name.equalsIgnoreCase(name)) {
                    text += r.summary() + "\n";
                    n++;
                    break;
                }
        }
        return text;
    }
}
