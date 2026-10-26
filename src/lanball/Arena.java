package lanball;

import java.io.*;
import java.util.Vector;

/** A pitch file contains a name, goal size and up to eight round bumpers. */
public class Arena {
    String name = "Classic";
    int goalHalf = 72;
    final Vector<Circle> bumpers = new Vector<Circle>();

    String rows() {
        String text = "A\t" + name + "\t" + goalHalf + "\n";
        for (Circle c : bumpers)
            text += "O\t" + (int) c.x + "\t" + (int) c.y + "\t" + (int) c.radius + "\n";
        return text;
    }

    static Arena parse(String text) throws IOException {
        Arena a = new Arena();
        String[] lines = text.trim().split("\n");
        try {
            String[] head = lines[0].trim().split("\t");
            if (head.length != 3
                    || !head[0].equals("A")
                    || !head[1].matches("[A-Za-z0-9 _-]{2,24}"))
                throw new IOException("Pitch name: 2-24 letters, digits, spaces, _ or -.");
            a.name = head[1];
            a.goalHalf = Integer.parseInt(head[2]);
            if (a.goalHalf < 60 || a.goalHalf > 110 || lines.length > 9)
                throw new IOException("Use goals 120-220 wide and at most 8 bumpers.");
            for (int i = 1; i < lines.length; i++) {
                String[] r = lines[i].trim().split("\t");
                if (r.length != 4 || !r[0].equals("O"))
                    throw new IOException("Invalid bumper row.");
                Circle c = new Circle(Integer.parseInt(r[3]));
                c.x = Integer.parseInt(r[1]);
                c.y = Integer.parseInt(r[2]);
                if (c.radius < 15
                        || c.radius > 40
                        || c.x < 100
                        || c.x > 860
                        || c.y < 70
                        || c.y > 470)
                    throw new IOException("Keep bumpers inside the marked editing area.");
                for (int team = 0; team < 2; team++)
                    for (int slot = 0; slot < 3; slot++)
                        if (Math.hypot(c.x - (team == 0 ? 240 : 720), c.y - (150 + slot * 120))
                                < c.radius + 50)
                            throw new IOException("Leave space around kick-off positions.");
                if (Math.hypot(c.x - 480, c.y - 270) < c.radius + 60)
                    throw new IOException("Keep the centre spot clear.");
                for (Circle other : a.bumpers)
                    if (c.distanceTo(other) < c.radius + other.radius + 65)
                        throw new IOException("Leave a player-width gap between bumpers.");
                a.bumpers.add(c);
            }
        } catch (NumberFormatException e) {
            throw new IOException("Pitch coordinates must be whole numbers.");
        }
        return a;
    }
}
