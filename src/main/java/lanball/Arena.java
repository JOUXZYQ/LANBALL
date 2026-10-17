package lanball;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.scene.media.AudioClip;
import java.util.*;

// Render independently of the network; interpolate positions, never scores or game phases.
final class Arena extends Canvas {
    record Frame(String[] header, Map<String,String[]> players, long received) { }
    Frame previous, latest;
    final Map<String,AudioClip> sounds = new HashMap<>();
    boolean smoothing = true, reducedMotion, muted;
    double volume = .35;
    int lastEvent = -1;
    long celebration;
    final AnimationTimer animation = new AnimationTimer() { public void handle(long now) { paint(now); } };
    Arena() {
        setWidth(900); setHeight(525);
        for (String name : List.of("kick", "goal", "wall", "whistle")) try {
            sounds.put(name, new AudioClip(getClass().getResource("/sounds/"+name+".wav").toExternalForm()));
        } catch (RuntimeException ignored) { }
        animation.start();
    }
    void accept(String data) {
        Map<String,String[]> bodies = new LinkedHashMap<>(); String[] header = null;
        for (String line : data.split("\n")) {
            String[] cells = line.split("\t", -1);
            if (cells[0].equals("H")) header = cells;
            if (cells[0].equals("P")) bodies.put(cells[1], cells);
        }
        if (header == null) return;
        previous = latest; latest = new Frame(header, bodies, System.nanoTime());
        int event = Integer.parseInt(header[10]);
        if (lastEvent != -1 && event != lastEvent) {
            AudioClip sound = sounds.get(header[11]); if (!muted && sound != null) sound.play(volume);
            if (header[11].equals("goal")) celebration = System.nanoTime();
        }
        lastEvent = event;
    }
    double position(String[] current, String[] old, int index, double alpha) {
        double value = Double.parseDouble(current[index]);
        if (old == null || Math.abs(value-Double.parseDouble(old[index])) > 80) return value;
        return Double.parseDouble(old[index]) * (1-alpha) + value * alpha;
    }
    void paint(long now) {
        GraphicsContext g = getGraphicsContext2D(); double w = 960, h = 560, opening = 180;
        if (latest != null) { w = Double.parseDouble(latest.header[7]); h = Double.parseDouble(latest.header[8]);
            opening = Double.parseDouble(latest.header[9]); }
        g.setFill(Color.web("#08111e")); g.fillRect(0,0,getWidth(),getHeight()); g.save();
        double scale = Math.min(getWidth()/(w+56),getHeight()/(h+56));
        g.translate((getWidth()-w*scale)/2,(getHeight()-h*scale)/2); g.scale(scale,scale);
        g.setFill(Color.web("#113c36")); g.fillRoundRect(-14,-14,w+28,h+28,18,18);
        for (int strip=0; strip<10; strip++) {
            g.setFill(Color.web(strip%2==0 ? "#175448" : "#194e45")); g.fillRect(strip*w/10,0,w/10,h);
        }
        g.setStroke(Color.web("#b8d9c3",.62)); g.setLineWidth(2);
        g.strokeRect(2,2,w-4,h-4); g.strokeLine(w/2,2,w/2,h-2); g.strokeOval(w/2-70,h/2-70,140,140);
        g.strokeRect(0,h/2-120,130,240); g.strokeRect(w-130,h/2-120,130,240);
        for (int side=0;side<2;side++) {
            double x=side==0 ? -18 : w; g.setStroke(Color.web(side==0 ? "#ff6879" : "#60caff"));
            g.setLineWidth(4); g.strokeRect(x,(h-opening)/2,18,opening);
            g.setLineWidth(1); for(double y=(h-opening)/2;y<(h+opening)/2;y+=12) g.strokeLine(x,y,x+18,y);
        }
        if (latest != null) {
            boolean blend = smoothing && previous != null && previous.header[12].equals(latest.header[12]);
            double alpha = blend ? Math.min(1,(now-latest.received)/(double)Math.max(1,latest.received-previous.received)) : 1;
            for (String[] player : latest.players.values()) {
                int side=Integer.parseInt(player[2]); if(side==2) continue;
                String[] old=blend ? previous.players.get(player[1]) : null;
                double x=position(player,old,3,alpha), y=position(player,old,4,alpha);
                g.setFill(Color.color(0,0,0,.3)); g.fillOval(x-18,y-10,36,33);
                g.setFill(Color.web(side==0 ? "#ff6879" : "#60caff")); g.fillOval(x-16,y-16,32,32);
                g.setStroke(Color.WHITE); g.setLineWidth(2); g.strokeOval(x-16,y-16,32,32);
                g.setTextAlign(TextAlignment.CENTER); g.setFont(Font.font("System",FontWeight.BOLD,14));
                g.setFill(Color.web("#08111e")); g.fillText(player[7],x,y+5);
                g.setFill(Color.WHITE); g.setFont(Font.font(13)); g.fillText(player[1],x,y-25);
                if(player[6].equals("false")) { g.setFill(Color.ORANGE); g.fillText("OFFLINE",x,y+33); }
            }
            String[] old=blend ? previous.header : null;
            double x=position(latest.header,old,5,alpha), y=position(latest.header,old,6,alpha);
            g.setFill(Color.color(0,0,0,.3)); g.fillOval(x-9,y-3,20,15);
            g.setFill(Color.WHITE); g.fillOval(x-8,y-8,16,16); g.setFill(Color.web("#243248")); g.fillOval(x-3,y-3,6,6);
            String phase=latest.header[1];
            if(!phase.equals("Playing")) {
                g.setFill(Color.web("#08111e",.8)); g.fillRoundRect(w/2-155,h/2-28,310,56,18,18);
                g.setFill(Color.WHITE); g.setFont(Font.font("System",FontWeight.BOLD,26));
                g.setTextAlign(TextAlignment.CENTER); g.fillText(phase.toUpperCase(Locale.ROOT),w/2,h/2+10);
            }
            if(!reducedMotion && now-celebration<1_200_000_000L) {
                double time=(now-celebration)/1e9;
                for(int i=0;i<40;i++) { g.setFill(i%2==0 ? Color.GOLD : Color.WHITE);
                    g.fillRect((i*173)% (int)w, ((i*61)%100)+time*220,5,8); }
            }
        }
        g.restore();
    }
}
