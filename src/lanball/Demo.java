package lanball;

import java.io.File;

import javax.swing.SwingUtilities;

/** Optional rehearsal launcher. Its two named demo accounts use a separate data folder. */
public class Demo implements Runnable {
    public static void main(String[] args) throws Exception {
        System.setProperty("lanball.data", "demo-data");
        System.setProperty("lanball.port", "45889");
        System.setProperty("lanball.mobilePort", "45891");
        Accounts accounts = new Accounts(new File("demo-data"));
        for (String name : new String[] {"DemoRed", "DemoBlue"})
            if (accounts.read(name) == null) {
                String salt = Accounts.random();
                String verifier = Accounts.verifier("demo123".toCharArray(), salt);
                accounts.create(
                        new String[] {
                            name, "Demo Club", name.equals("DemoRed") ? "7" : "9", salt, verifier
                        });
            }
        SwingUtilities.invokeLater(new Demo());
    }

    public void run() {
        LanBall.darkDefaults();
        LanBall red = new LanBall(), blue = new LanBall();
        red.setTitle("LanBall rehearsal - RED window");
        blue.setTitle("LanBall rehearsal - BLUE window");
        red.name.setText("DemoRed");
        blue.name.setText("DemoBlue");
        red.password.setText("demo123");
        blue.password.setText("demo123");
        red.create.setSelected(false);
        blue.create.setSelected(false);
        red.seconds.setText("60");
        red.target.setText("1");
        blue.setVisible(true);
        red.setVisible(true);
        red.notice(
                "REHEARSAL: click Host here, then Join in the BLUE window. Demo password: demo123");
        blue.notice(
                "REHEARSAL: after RED hosts, click Join. Alt-Tab switches between the two"
                        + " windows.");
    }
}
