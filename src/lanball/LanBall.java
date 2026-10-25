package lanball;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Enumeration;
import javax.swing.*;
import javax.swing.Timer;

/*
 * The Swing window: controls, the player card, chat, the league table and the link to the host.
 * Module 4: Swing controls, layout managers and the delegation event model. This class is the listener
 * for its own controls (ActionListener), the keyboard (KeyListener), focus (FocusListener) and the mouse (MouseListener).
 */
public class LanBall extends JFrame implements ActionListener, KeyListener, FocusListener, MouseListener {
    // The look: a night stadium. Dark navy panels, gold for "you" and rewards, red and blue only for the two teams.
    static final Color NAVY = new Color(0x0b1020), PANEL = new Color(0x161f44), TEXT = new Color(0xe8eeff);
    static final String ROW = "%-3s %-12s %3s %3s %3s %3s %4s %4s %5s %4s%n"; // league table columns
    final JTextField address = field("127.0.0.1", 10), name = field("", 9), club = field("", 9), shirt = field("7", 3);
    final JTextField seconds = field("180", 4), target = field("5", 3), chatInput = field("", 22);
    final JPasswordField pin = new JPasswordField(8);
    final JCheckBox create = check("Create profile"), ready = check("READY"), sound = check("Sound");
    final JComboBox<String> team = new JComboBox<String>(new String[]{"Red", "Blue", "Watch"});
    final JLabel status = label("Host on one computer; friends on the same Wi-Fi join its IP address.", 14, TEXT);
    final JLabel hostAddress = label(" ", 13, Pitch.GOLD), level = label("Lv 1", 24, Pitch.GOLD);
    final JLabel progress = label("Play a match to earn XP", 12, Pitch.MUTED), results = label("No matches yet", 14, TEXT);
    final JLabel badges = label("Badges 0/8", 14, TEXT);
    final JProgressBar xpBar = new JProgressBar(0, 1000);
    final JTextArea chatLog = area(), league = area();
    final GameButton host = new GameButton("HOST", true, this), join = new GameButton("JOIN", false, this);
    final GameButton start = new GameButton("START MATCH", true, this), leave = new GameButton("LEAVE", false, this);
    final JPanel connection;
    final Pitch pitch = new Pitch();
    final Timer timer = new Timer(15, this);
    int keys, controls, wantedTeam, generation, myXp = -1, xpAtKickoff;
    long lastFrame = System.nanoTime();
    String previousPhase = "", outgoingChat = "", incoming, failure, linked;
    Socket socket;
    Server server;
    LanBall() {
        super("LanBall - Wi-Fi football");
        create.setSelected(true); sound.setSelected(true); start.setEnabled(false);
        style(team); style(pin); pin.setCaretColor(Color.WHITE); team.setFocusable(false);
        team.addActionListener(this); ready.addActionListener(this); sound.addActionListener(this); chatInput.addActionListener(this);
        xpBar.setForeground(Pitch.GOLD); xpBar.setBackground(PANEL); xpBar.setBorderPainted(false);
        xpBar.setPreferredSize(new Dimension(200, 10));
        JPanel xpBox = new JPanel(new GridLayout(2, 1, 0, 4));
        xpBox.setOpaque(false); xpBox.add(xpBar); xpBox.add(progress);
        badges.setToolTipText("No badges yet");
        connection = row(caption("Host IP", address), caption("Player name", name), caption("PIN (6-12 digits)", pin),
                caption("Club", club), caption("Shirt 1-99", shirt), create, host, join);
        JPanel top = column(row(label("LANBALL", 34, Pitch.GOLD), new Card(level, xpBox, results, badges)), connection,
                row(team, ready, caption("Seconds", seconds), caption("First to", target), start, sound, leave),
                row(status), row(hostAddress));
        JPanel side = column(row(label("CHAT", 16, Pitch.GOLD)), scroll(chatLog, 150), row(chatInput),
                row(label("LEAGUE TABLE", 16, Pitch.GOLD)), scroll(league, 330));
        side.setPreferredSize(new Dimension(430, 600));
        JPanel content = new JPanel(new BorderLayout(14, 10)); // layout managers (Module 4): Border, Box, Flow, Grid
        content.setBackground(NAVY);
        content.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));
        content.add(top, BorderLayout.NORTH); content.add(pitch, BorderLayout.CENTER); content.add(side, BorderLayout.EAST);
        content.add(label("WASD or arrow keys to move, Space to kick. Click the pitch if the keys stop working.", 13,
                Pitch.MUTED), BorderLayout.SOUTH);
        setContentPane(content);
        pitch.addKeyListener(this); pitch.addFocusListener(this); pitch.addMouseListener(this);
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1360, 930); setLocationRelativeTo(null);
        Sound.warmUp(); timer.start();
    }
    public static void main(String[] args) { new LanBall().setVisible(true); }
    // ---------- small builders for the dark game look
    static JLabel label(String text, int size, Color colour) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", size >= 16 ? Font.BOLD : Font.PLAIN, size)); label.setForeground(colour);
        return label;
    }
    static JTextField field(String text, int columns) {
        JTextField field = new JTextField(text, columns);
        style(field); field.setCaretColor(Color.WHITE);
        return field;
    }
    static void style(JComponent part) {
        part.setBackground(PANEL); part.setForeground(Color.WHITE); part.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        part.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x2c3a75)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
    }
    static JCheckBox check(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false); box.setForeground(Color.WHITE); box.setFocusable(false); box.setFont(new Font("Segoe UI", Font.BOLD, 14));
        return box;
    }
    static JTextArea area() {
        JTextArea area = new JTextArea();
        area.setEditable(false); area.setFocusable(false);
        area.setBackground(new Color(0x10173a)); area.setForeground(TEXT); area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return area;
    }
    static JScrollPane scroll(JTextArea area, int height) {
        JScrollPane pane = new JScrollPane(area);
        pane.setBorder(BorderFactory.createLineBorder(new Color(0x2c3a75)));
        pane.setPreferredSize(new Dimension(430, height)); pane.setAlignmentX(LEFT_ALIGNMENT);
        return pane;
    }
    static JPanel row(Component... parts) { // varargs (Module 1): any number of parts in one left-aligned row
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row.setOpaque(false); row.setAlignmentX(LEFT_ALIGNMENT);
        for (Component part : parts) row.add(part);
        return row;
    }
    static JPanel column(Component... parts) { // parts stacked top to bottom
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS)); column.setOpaque(false);
        for (Component part : parts) column.add(part);
        return column;
    }
    static JPanel caption(String text, JComponent input) {
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setOpaque(false); box.add(label(text, 12, Pitch.MUTED), BorderLayout.NORTH); box.add(input, BorderLayout.CENTER);
        return box;
    }
    // Inheritance (Module 2): a JButton that paints itself as a rounded game button. Gold = main action.
    static class GameButton extends JButton {
        final boolean primary;
        GameButton(String text, boolean primary, ActionListener listener) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setFocusable(false);
            setForeground(primary ? new Color(0x1a1300) : Color.WHITE); setFont(new Font("Segoe UI", Font.BOLD, 14));
            setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(listener);
        }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color top = new Color(primary ? 0xffe08a : 0x3d53a8), bottom = new Color(primary ? 0xf4a52a : 0x1b2452);
            if (getModel().isRollover()) top = top.brighter();
            if (getModel().isPressed()) top = bottom;
            if (!isEnabled()) top = bottom = new Color(0x262c48);
            g.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g.dispose();
            super.paintComponent(graphics); // the text on top
        }
    }
    // A rounded dark panel for the player card.
    static class Card extends JPanel {
        Card(Component... parts) { super(new FlowLayout(FlowLayout.LEFT, 18, 8)); setOpaque(false); for (Component p : parts) add(p); }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x131a3a)); g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g.dispose();
        }
    }
    // ---------- events (delegation event model: the source calls the listener method)
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == timer) { nextFrame(); return; }
        Sound.CLICK.play();
        if (source == host || source == join) connect(source == host);
        if (source == leave) { disconnect(); status.setText("Disconnected."); }
        if (source == start) startMatch();
        if (source == team) { wantedTeam = team.getSelectedIndex(); ready.setSelected(false); } // a new team means "not ready"
        if (source == sound) Sound.muted = !sound.isSelected();
        if (source == chatInput) { say(chatInput.getText()); chatInput.setText(""); }
        updateInput();
        pitch.requestFocusInWindow(); // give the keys back to the game
    }
    public void keyPressed(KeyEvent e) { keys |= bit(e.getKeyCode()); updateInput(); }
    public void keyReleased(KeyEvent e) { keys &= ~bit(e.getKeyCode()); updateInput(); }
    public void focusLost(FocusEvent e) { keys = 0; updateInput(); } // no stuck keys after Alt-Tab
    public void mousePressed(MouseEvent e) { pitch.requestFocusInWindow(); }
    // An interface needs every method, so the events this game ignores get empty bodies.
    public void keyTyped(KeyEvent e) { } public void focusGained(FocusEvent e) { } public void mouseClicked(MouseEvent e) { }
    public void mouseReleased(MouseEvent e) { } public void mouseEntered(MouseEvent e) { } public void mouseExited(MouseEvent e) { }

    // Input bits: left 1, right 2, up 4, down 8, kick 16, ready 32.
    static int bit(int code) {
        switch (code) {
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT: return 1;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: return 2;
            case KeyEvent.VK_W: case KeyEvent.VK_UP: return 4;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN: return 8;
            case KeyEvent.VK_SPACE: return 16;
            default: return 0;
        }
    }
    // The network thread reads these values, so every read and write goes through a synchronized method.
    synchronized void updateInput() { controls = keys | (ready.isSelected() ? 32 : 0); }
    synchronized int controls() { return controls; }
    synchronized void say(String text) { outgoingChat = text; }
    synchronized String takeChat() { String text = outgoingChat; outgoingChat = ""; return text; }
    synchronized void deliver(int owner, String frame, String linkedText, String error) {
        if (owner != generation) return; // a message from an old connection
        if (frame != null) incoming = frame;
        if (linkedText != null) linked = linkedText;
        if (error != null) failure = error;
    }
    synchronized boolean adopt(int owner, Server created, Socket connection) throws IOException {
        if (owner != generation) { if (created != null) created.close(); connection.close(); return false; }
        if (created != null) server = created;
        socket = connection; return true;

    }
    void startMatch() {
        try { status.setText(server.startRound(Integer.parseInt(seconds.getText().trim()), Integer.parseInt(target.getText().trim()))); }
        catch (NumberFormatException error) { JOptionPane.showMessageDialog(this, "Enter whole numbers for Seconds and First to."); }
    }
    void connect(boolean hosting) {
        String secret = new String(pin.getPassword());
        if (!secret.matches("[0-9]{6,12}")) { status.setText("The PIN must be 6-12 digits."); return; }
        disconnect();
        String hello = "LB7\t" + name.getText() + "\t" + club.getText() + "\t" + shirt.getText() + "\t"
                + (create.isSelected() ? "create" : "login");
        Link link = new Link(hosting, hosting ? "127.0.0.1" : address.getText().trim(), secret, hello);
        host.setEnabled(false); join.setEnabled(false); status.setText("Connecting...");
        pitch.me = name.getText().trim();
        Server.background(link);
    }
    // Talks to the host on its own thread, so the window never waits for the network.
    class Link implements Runnable {
        final boolean hosting; final String destination, secret, hello; final int owner = generation;
        Link(boolean hosting, String destination, String secret, String hello) {
            this.hosting = hosting; this.destination = destination; this.secret = secret; this.hello = hello;
        }
        public void run() {
            try {
                Server created = hosting ? new Server(new File(System.getProperty("lanball.data", "lanball-v4-data")), Server.PORT)
                        : null;
                Socket connection = new Socket();
                if (!adopt(owner, created, connection)) return;
                connection.connect(new InetSocketAddress(destination, Server.PORT), 5000);
                connection.setSoTimeout(8000); connection.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(connection.getInputStream());
                DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                out.writeUTF(hello);
                String[] challenge = in.readUTF().split("\t"); // salt, nonce, new/old -- or an error
                if (challenge.length != 3) throw new IOException(challenge[0]);
                String verifier = Server.verifier(secret, challenge[0]);
                out.writeUTF(challenge[2].equals("new") ? verifier : Server.proof(verifier, challenge[1]));
                String answer = in.readUTF();
                if (!answer.equals("OK")) throw new IOException(answer);
                deliver(owner, null, hosting ? addresses() : "Connected to " + destination, null);
                while (true) {
                    out.writeInt(controls()); out.writeInt(wantedTeam); out.writeUTF(takeChat());
                    deliver(owner, in.readUTF(), null, null);
                    Thread.sleep(25); // about 30 updates a second
                }
            } catch (Exception error) { deliver(owner, null, null, "Connection ended: " + error.getMessage()); }
        }
    }
    static String addresses() throws SocketException {
        String ips = "";
        Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
        while (networks.hasMoreElements()) {
            Enumeration<InetAddress> list = networks.nextElement().getInetAddresses();
            while (list.hasMoreElements()) {
                InetAddress ip = list.nextElement();
                if (ip.getAddress().length == 4 && !ip.isLoopbackAddress()) ips += " / " + ip.getHostAddress(); // IPv4 only
            }
        }
        return "Friends on the same Wi-Fi join with " + (ips.length() > 0 ? ips.substring(3) : "this computer's IP address")
                + " (port " + Server.PORT + ")";
    }
    // Runs on the Swing timer: take the newest snapshot from the network thread, then animate the pitch.
    void nextFrame() {
        String frame, link, error;
        synchronized (this) { frame = incoming; link = linked; error = failure; incoming = linked = failure = null; }
        if (link != null) { connection.setVisible(false); start.setEnabled(server != null); hostAddress.setText(link); }
        if (frame != null) show(frame);
        if (error != null) { disconnect(); status.setText(error); }
        long now = System.nanoTime();
        pitch.step(Math.min(.05, (now - lastFrame) / 1e9)); lastFrame = now;
    }
    // Reads one host snapshot: the pitch draws the match; this method updates the panels and the player card.
    void show(String frame) {
        pitch.read(frame);
        String chat = "", table = String.format(ROW, "#", "Player", "Lv", "P", "W", "D", "Gls", "Pts", "Elo", "Best");
        int rank = 0;
        for (String row : frame.split("\n")) {
            String[] cells = row.split("\t");
            if (cells[0].equals("M") && !status.getText().equals(cells[1])) status.setText(cells[1]);
            if (cells[0].equals("C")) chat += cells[1] + "\n";
            if (!cells[0].equals("L") || cells.length < 11) continue;
            int[] s = new int[9];
            for (int i = 0; i < 9; i++) s[i] = Integer.parseInt(cells[i + 2]);
            int xp = Server.xp(s);
            table += String.format(ROW, ++rank, cells[1], Server.level(xp), s[0], s[1], s[2], s[3], s[4], s[5], s[8]);
            if (cells[1].equalsIgnoreCase(pitch.me)) playerCard(s, xp);
        }
        String phase = pitch.phase;
        if (previousPhase.equals("Playing") && !phase.equals("Playing")) ready.setSelected(false);
        if (!previousPhase.equals("Playing") && phase.equals("Playing")) xpAtKickoff = Math.max(0, myXp);
        if (!previousPhase.equals("Result") && phase.equals("Result")) pitch.reward = "+" + (myXp - xpAtKickoff) + " XP";
        previousPhase = phase;
        updateInput();
        team.setEnabled(!phase.equals("Playing") && !phase.equals("Save failed"));
        if (!chatLog.getText().equals(chat.trim())) chatLog.setText(chat.trim());
        if (!league.getText().equals(table)) league.setText(table);
    }
    // The player card: level, XP to the next level, results, win streak and badges. Progress you can see is the main reward.
    void playerCard(int[] s, int xp) {
        int lv = Server.level(xp), from = 50 * (lv - 1) * (lv - 1), to = 50 * lv * lv;
        if (myXp >= 0 && lv > Server.level(myXp)) Sound.LEVEL_UP.play();
        myXp = xp;
        level.setText("Lv " + lv);
        xpBar.setValue(1000 * (xp - from) / (to - from));
        progress.setText(xp + " / " + to + " XP");
        results.setText(s[1] + "W " + s[2] + "D " + (s[0] - s[1] - s[2]) + "L   Elo " + s[5] + (s[7] >= 2 ? "   Streak " + s[7] : ""));
        String earned = ""; int count = 0;
        for (int b = 0; b < Server.BADGES.length; b++)
            if ((s[6] & 1 << b) != 0) { earned += "<br>" + Server.BADGES[b]; count++; }
        badges.setText("Badges " + count + "/" + Server.BADGES.length);
        badges.setToolTipText(count == 0 ? "No badges yet" : "<html>" + earned.substring(4) + "</html>");
    }
    synchronized void disconnect() {
        generation++;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        if (server != null) server.close();
        server = null; socket = null; keys = 0; previousPhase = ""; incoming = linked = failure = null; // drop old messages
        ready.setSelected(false); updateInput();
        host.setEnabled(true); join.setEnabled(true); start.setEnabled(false); team.setEnabled(true);
        connection.setVisible(true); pitch.reset(); hostAddress.setText(" ");
    }
}
