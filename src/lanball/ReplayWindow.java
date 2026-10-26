package lanball;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

/** Playback, pause and seek for an already downloaded match. */
public class ReplayWindow extends JFrame implements ActionListener, ChangeListener {
    final Replay replay;
    final Pitch pitch = new Pitch();
    final JButton play = new JButton("Pause");
    final JSlider seek;
    final JLabel time = new JLabel();
    final Timer timer = new Timer(16, this);
    int position, index;
    long previous = System.nanoTime();
    boolean playing = true, changing;

    ReplayWindow(Replay replay, String title) {
        super("Replay - " + title);
        this.replay = replay;
        pitch.smooth = false;
        seek = new JSlider(0, replay.times.lastElement(), 0);
        JPanel controls = LanBall.row(play, time);
        controls.setLayout(new BorderLayout());
        controls.add(play, BorderLayout.WEST);
        controls.add(seek);
        controls.add(time, BorderLayout.EAST);
        add(pitch);
        add(controls, BorderLayout.SOUTH);
        play.addActionListener(this);
        seek.addChangeListener(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        LanBall.style(controls);
        pack();
        setLocationByPlatform(true);
        timer.start();
        showFrame();
    }

    void showFrame() {
        while (index + 1 < replay.frames.size() && replay.times.get(index + 1) <= position) index++;
        while (index > 0 && replay.times.get(index) > position) index--;
        pitch.read(replay.frames.get(index), "");
        pitch.animate(.016);
        time.setText(String.format(" %d:%02d ", position / 60000, position / 1000 % 60));
        changing = true;
        seek.setValue(position);
        changing = false;
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == play) {
            if (position >= seek.getMaximum()) {
                position = 0;
                index = 0;
                pitch.clear();
            }
            playing = !playing;
            play.setText(playing ? "Pause" : "Play");
            previous = System.nanoTime();
            return;
        }
        if (!isDisplayable()) {
            timer.stop();
            return;
        }
        long now = System.nanoTime();
        if (playing && !seek.getValueIsAdjusting())
            position = Math.min(seek.getMaximum(), position + (int) ((now - previous) / 1000000));
        previous = now;
        if (position == seek.getMaximum()) {
            playing = false;
            play.setText("Play");
        }
        showFrame();
    }

    public void stateChanged(ChangeEvent e) {
        if (!changing) {
            position = seek.getValue();
            pitch.goalSerial = -1;
            pitch.confetti.clear();
            pitch.celebration = 0;
            showFrame();
        }
    }
}
