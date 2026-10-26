package lanball;

import java.io.File;

/** Test-only host: automatically starts when browser testers have chosen opposing teams. */
public class BrowserHost {
    public static void main(String[] args) throws Exception {
        Server host = new Server(new File(args[0]), 0);
        host.mobile = new MobileServer(host, 45930);
        System.out.println("Browser test host: http://127.0.0.1:45930");
        long end = System.currentTimeMillis() + 300000;
        try {
            while (System.currentTimeMillis() < end) {
                synchronized (host) {
                    if (host.match.phase.equals("Lobby")
                            && host.match.teamSize(0) > 0
                            && host.match.teamSize(1) > 0) host.startRound(60, 1);
                }
                Thread.sleep(100);
            }
        } finally {
            host.close();
        }
    }
}
