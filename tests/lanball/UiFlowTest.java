package lanball;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;

import javax.imageio.ImageIO;
import javax.swing.*;

/** Exercises the two real client windows, including their worker threads. */
public class UiFlowTest {
    static LanBall host, guest;
    static File shots;

    interface Check {
        boolean ok();
    }

    static void swing(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    static void waitFor(Check check, String name) throws Exception {
        for (int i = 0; i < 160; i++) {
            final boolean[] ok = {false};
            swing(() -> ok[0] = check.ok());
            if (ok[0]) {
                System.out.println("PASS " + name);
                return;
            }
            Thread.sleep(40);
        }
        throw new AssertionError(name);
    }

    static void click(LanBall ui, JButton button) {
        ui.actionPerformed(new ActionEvent(button, 0, ""));
    }

    static void image(LanBall ui, String name) {
        try {
            ui.setSize(1320, 850);
            ui.validate();
            BufferedImage image =
                    new BufferedImage(
                            ui.getContentPane().getWidth(),
                            ui.getContentPane().getHeight(),
                            BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            ui.getContentPane().printAll(g);
            g.dispose();
            ImageIO.write(image, "png", new File(shots, name + ".png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        shots = new File(args[0]);
        shots.mkdirs();
        swing(
                () -> {
                    LanBall.darkDefaults();
                    host = new LanBall();
                    guest = new LanBall();
                    image(host, "01-welcome");
                    host.name.setText("DemoRed");
                    guest.name.setText("DemoBlue");
                    host.password.setText("demo123");
                    guest.password.setText("demo123");
                    click(host, host.host);
                });
        waitFor(() -> host.connected && host.pitch.phase.equals("Lobby"), "host account and lobby");
        swing(() -> click(guest, guest.join));
        waitFor(() -> guest.connected && guest.pitch.dots.size() == 2, "second client joins");
        swing(
                () -> {
                    host.team.setSelectedIndex(0);
                    guest.team.setSelectedIndex(1);
                });
        waitFor(
                () ->
                        host.pitch.mine() != null
                                && host.pitch.mine().team == 0
                                && guest.pitch.mine() != null
                                && guest.pitch.mine().team == 1,
                "accepted teams reflected in both selectors");
        swing(
                () -> {
                    host.ready.setSelected(true);
                    guest.ready.setSelected(true);
                    host.updateControls();
                    guest.updateControls();
                });
        waitFor(
                () -> {
                    synchronized (host.server) {
                        return host.server.match.find("DemoRed").ready()
                                && host.server.match.find("DemoBlue").ready();
                    }
                },
                "both ready");
        swing(
                () -> {
                    image(host, "02-lobby");
                    host.target.setText("1");
                    click(host, host.start);
                });
        waitFor(() -> guest.pitch.phase.equals("Playing"), "match reaches both windows");
        swing(() -> image(host, "03-countdown"));
        Thread.sleep(3100);
        swing(
                () -> {
                    synchronized (host.server) {
                        host.server.match.find("DemoRed").x = 440;
                        host.server.match.find("DemoRed").y = 270;
                        host.server.match.find("DemoBlue").x = 720;
                        host.server.match.find("DemoBlue").y = 330;
                    }
                    host.setKey(KeyEvent.VK_D, true);
                });
        Thread.sleep(120);
        swing(
                () -> {
                    host.setKey(KeyEvent.VK_D, false);
                    host.setKey(KeyEvent.VK_SPACE, true);
                });
        Thread.sleep(120);
        swing(() -> host.setKey(KeyEvent.VK_SPACE, false));
        swing(
                () -> {
                    image(host, "04-match");
                    synchronized (host.server) {
                        host.server.match.resumeAt = 0;
                        host.server.match.ball.x = 967;
                        host.server.match.ball.y = 270;
                        host.server.match.ball.vx = 500;
                        host.server.match.lastTouch = "DemoRed";
                    }
                });
        waitFor(() -> host.pitch.celebration > 0, "winning goal celebration");
        swing(() -> image(host, "05-goal"));
        waitFor(
                () -> host.pitch.phase.equals("Result") && host.historyModel.size() > 0,
                "saved result and history refresh");
        Thread.sleep(2200);
        swing(
                () -> {
                    host.tabs.setSelectedIndex(1);
                    image(host, "06-result");
                    host.tabs.setSelectedIndex(2);
                    host.historyList.setSelectedIndex(0);
                    click(host, host.replay);
                });
        waitFor(
                () -> {
                    for (Window w : Window.getWindows())
                        if (w instanceof ReplayWindow && w.isVisible()) return true;
                    return false;
                },
                "replay opens from downloaded history");
        swing(
                () -> {
                    PitchEditor editor = new PitchEditor(host, host.server.match.arena);
                    editor.name.setText("Training");
                    Circle c = new Circle(24);
                    c.x = 480;
                    c.y = 100;
                    editor.arena.bumpers.add(c);
                    try {
                        host.server.useArena(editor.checked());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    editor.dispose();
                });
        waitFor(
                () -> guest.pitch.arena.name.equals("Training"),
                "custom pitch reaches other client");
        swing(
                () -> {
                    click(guest, guest.leave);
                    guest.create.setSelected(false);
                    click(guest, guest.join);
                });
        waitFor(
                () -> guest.connected && guest.pitch.phase.equals("Lobby"),
                "sign in again after Leave");
        swing(
                () -> {
                    click(host, host.leave);
                    host.dispose();
                    guest.dispose();
                    for (Window w : Window.getWindows()) w.dispose();
                });
        System.out.println("UI FLOW COMPLETE");
        System.exit(0);
    }
}
