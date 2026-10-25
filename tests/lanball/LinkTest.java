package lanball;

import java.awt.event.ActionEvent;
import java.nio.file.Files;
import javax.swing.SwingUtilities;

/** Drives two real (never shown) LanBall windows through Host, Join, ready, start and chat. */
public class LinkTest {
    static int passed;
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAILED: " + what);
        passed++;
        System.out.println("ok  " + what);
    }
    static void click(LanBall game, Object source) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { game.actionPerformed(new ActionEvent(source, 0, "")); } });
    }
    static void fill(LanBall game, String name, String pin, String club, String shirt) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            game.name.setText(name); game.pin.setText(pin); game.club.setText(club); game.shirt.setText(shirt);
        } });
    }
    static boolean waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end) { if (condition.call()) return true; Thread.sleep(50); }
        return false;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("lanball.data", Files.createTempDirectory("lanball-v7-link").toString());
        Sound.muted = true;
        LanBall[] made = new LanBall[2];
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { made[0] = new LanBall(); made[1] = new LanBall(); } });
        LanBall host = made[0], guest = made[1];
        Sound.muted = true;
        fill(host, "Asha", "123456", "Red Lions", "9");
        click(host, host.host);
        check(waitFor(() -> host.pitch.phase.equals("Lobby")), "the Host button starts a server and joins it (" + host.status.getText() + ")");
        check(host.hostAddress.getText().contains("port 45888") && host.start.isEnabled(), "the host sees its address and START");
        fill(guest, "Binu", "654321", "Blue Bay", "4");
        click(guest, guest.join);
        check(waitFor(() -> guest.pitch.phase.equals("Lobby") && host.pitch.bodies.size() == 2), "the Join button connects a second player");
        check(!guest.start.isEnabled() && !guest.connection.isVisible(), "a guest cannot start; the profile row hides after joining");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { guest.team.setSelectedIndex(1); } });
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { guest.ready.setSelected(true); } });
        click(guest, guest.ready);
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { host.ready.setSelected(true); } });
        click(host, host.ready);
        check(waitFor(() -> host.pitch.body("Binu") != null && host.pitch.body("Binu").team == 1 && host.pitch.body("Binu").ready
                && host.pitch.body("Asha").ready), "team choice and READY reach the host");
        click(host, host.start);
        check(waitFor(() -> guest.pitch.phase.equals("Playing") && guest.pitch.countdown > 0), "START begins the match with a countdown on both screens");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { guest.chatInput.setText("good luck"); } });
        click(guest, guest.chatInput);
        check(waitFor(() -> host.chatLog.getText().contains("Binu: good luck")), "chat typed in one window shows in the other");
        click(guest, guest.leave);
        check(waitFor(() -> guest.pitch.phase.equals("Offline") && guest.connection.isVisible()), "LEAVE returns to the offline screen");
        click(host, host.leave);
        check(waitFor(() -> host.server == null), "LEAVE on the host stops the server");
        System.out.println("Link tests passed: " + passed + " checks");
        System.exit(0);
    }
}
