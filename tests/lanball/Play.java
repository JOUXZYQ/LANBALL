package lanball;

import java.awt.event.ActionEvent;
import javax.swing.SwingUtilities;

/** Opens the real game window, hosts, and fills the other three places with bots (Chitra on Red; Binu, Dev on Blue). */
public class Play {
    public static void main(String[] args) throws Exception {
        System.setProperty("lanball.data", args[0]);
        LanBall[] made = new LanBall[1];
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            LanBall game = new LanBall();
            game.name.setText("You"); game.create.setSelected(!new java.io.File(args[0], "player-you.profile").exists()); game.pin.setText("123456"); game.club.setText("Red Lions"); game.shirt.setText("10");
            game.seconds.setText("120"); game.target.setText("5");
            game.setVisible(true);
            game.actionPerformed(new ActionEvent(game.host, 0, ""));
            made[0] = game;
        } });
        LanBall game = made[0];
        while (game.server == null || game.pitch.phase.equals("Offline")) Thread.sleep(50);
        Server.background(new Demo.Bot("Chitra", 0, true));
        Server.background(new Demo.Bot("Binu", 1, false));
        Server.background(new Demo.Bot("Dev", 1, true));
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            game.ready.setSelected(true); game.updateInput(); game.pitch.requestFocusInWindow(); } });
    }
}
