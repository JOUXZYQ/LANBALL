package lanball;

import java.io.*;
import java.util.Vector;

/** A replay stores sampled host snapshots, with their times in milliseconds. */
public class Replay {
    static final int LIMIT = 32 * 1024 * 1024;
    final Vector<String> frames = new Vector<String>();
    final Vector<Integer> times = new Vector<Integer>();
    long began, lastCapture;

    void capture(String frame, long now) {
        if (frames.size() == 0) began = now;
        lastCapture = now;
        frames.add(frame);
        times.add((int) ((now - began) / 1000000));
    }

    byte[] bytes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeUTF("LB9R");
        out.writeInt(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            out.writeInt(times.get(i));
            out.writeUTF(frames.get(i));
        }
        out.flush();
        if (buffer.size() > LIMIT) throw new IOException("Replay too large.");
        return buffer.toByteArray();
    }

    static Replay read(byte[] bytes) throws IOException {
        if (bytes.length > LIMIT) throw new IOException("Replay too large.");
        Replay r = new Replay();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (!in.readUTF().equals("LB9R")) throw new IOException("Not a LanBall v9 replay.");
        int count = in.readInt();
        if (count < 1 || count > 100000) throw new IOException("Invalid replay length.");
        int last = -1;
        Pitch validator = new Pitch();
        for (int i = 0; i < count; i++) {
            int time = in.readInt();
            String frame = in.readUTF();
            if (time < 0 || time < last || time > 1500000)
                throw new IOException("Invalid replay time.");
            validator.read(frame, "");
            r.times.add(time);
            r.frames.add(frame);
            last = time;
        }
        if (in.available() != 0) throw new IOException("Unexpected replay data.");
        return r;
    }
}
