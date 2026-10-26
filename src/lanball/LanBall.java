package lanball;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

import javax.swing.*;
import javax.swing.Timer;

/** Main window: ordinary Swing controls around a snapshot-only pitch view. */
public class LanBall extends JFrame
        implements ActionListener, KeyListener, FocusListener, MouseListener {
    final JTextField address = new JTextField("127.0.0.1", 10),
            name = new JTextField(9),
            club = new JTextField("College", 8),
            shirt = new JTextField("7", 2);
    final JPasswordField password = new JPasswordField(9);
    final JCheckBox create = new JCheckBox("Create account", true), ready = new JCheckBox("Ready");
    final JComboBox<String> team = new JComboBox<String>(new String[] {"Red", "Blue", "Watch"});
    final JTextField seconds = new JTextField("180", 3),
            target = new JTextField("5", 2),
            chatInput = new JTextField();
    final JButton roomToggle = new JButton("Hide room"),
            host = new JButton("Host"),
            join = new JButton("Join"),
            leave = new JButton("Leave"),
            start = new JButton("Start match");
    final JButton editor = new JButton("Pitch editor"),
            phones = new JButton("Phones"),
            season = new JButton("Season"),
            refresh = new JButton("Refresh"),
            details = new JButton("Summary"),
            replay = new JButton("Replay"),
            profile = new JButton("My profile");
    final JButton prev = new JButton("Previous"),
            next = new JButton("Next"),
            historyPrev = new JButton("Previous"),
            historyNext = new JButton("Next");
    final JLabel
            status =
                    new JLabel(
                            "Create an account or sign in. One player hosts; the others join the"
                                    + " host IP."),
            room = new JLabel(" ");
    final JTextArea chatLog = area(), league = area(), profileText = area();
    final DefaultListModel<String> historyModel = new DefaultListModel<String>();
    final JList<String> historyList = new JList<String>(historyModel);
    final JTabbedPane tabs = new JTabbedPane();
    final JPanel connection = new JPanel(new GridLayout(2, 1, 0, 4));
    final Pitch pitch = new Pitch();
    final Timer timer = new Timer(16, this);
    final boolean[] down = new boolean[256];
    final Vector<String> commands = new Vector<String>(), responses = new Vector<String>();
    String myName = "", incoming, failure, outgoingChat = "";
    int controls, pendingTeam = -1, leaguePage, historyPage;
    boolean updating, connected;
    Server server;
    Link link;
    Replay downloaded;
    long lastFrame = System.nanoTime(), noticeUntil;

    LanBall() {
        super("LanBall v9");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel identity = row(new JLabel("Name"), name, new JLabel("Password"), password, create);
        JPanel network =
                row(
                        new JLabel("Host IP"),
                        address,
                        new JLabel("Club"),
                        club,
                        new JLabel("Shirt"),
                        shirt,
                        host,
                        join);
        connection.add(identity);
        connection.add(network);
        team.setSelectedIndex(2);
        JPanel controls =
                row(
                        new JLabel("Team"),
                        team,
                        ready,
                        new JLabel("Seconds"),
                        seconds,
                        new JLabel("First to"),
                        target,
                        start,
                        leave);
        JPanel tools = row(editor, season, phones, roomToggle);
        JLabel brand = new JLabel("LANBALL  /  LOCAL FOOTBALL");
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(brand, BorderLayout.WEST);
        heading.add(tools, BorderLayout.EAST);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(heading);
        top.add(connection);
        top.add(controls);
        top.add(row(status));
        top.add(row(room));
        JPanel chat = new JPanel(new BorderLayout(0, 6));
        chat.add(new JScrollPane(chatLog));
        chat.add(chatInput, BorderLayout.SOUTH);
        JPanel table = new JPanel(new BorderLayout());
        table.add(new JScrollPane(league));
        table.add(row(prev, next), BorderLayout.SOUTH);
        JPanel history = new JPanel(new BorderLayout());
        history.add(new JScrollPane(historyList));
        JPanel historyButtons = new JPanel(new GridLayout(2, 1));
        historyButtons.add(row(refresh, details, replay));
        historyButtons.add(row(historyPrev, historyNext));
        history.add(historyButtons, BorderLayout.SOUTH);
        JPanel card = new JPanel(new BorderLayout());
        card.add(new JScrollPane(profileText));
        card.add(row(profile), BorderLayout.SOUTH);
        tabs.addTab("Chat", chat);
        tabs.addTab("League", table);
        tabs.addTab("History", history);
        tabs.addTab("Profile", card);
        tabs.setPreferredSize(new Dimension(340, 450));
        JPanel middle = new JPanel(new BorderLayout(12, 0));
        middle.add(pitch);
        middle.add(tabs, BorderLayout.EAST);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        content.add(top, BorderLayout.NORTH);
        content.add(middle);
        content.add(
                new JLabel(
                        "WASD / arrows  move     SPACE  kick     Click the pitch to return to the"
                                + " game"),
                BorderLayout.SOUTH);
        setContentPane(content);
        for (JButton b :
                new JButton[] {
                    host,
                    roomToggle,
                    join,
                    leave,
                    start,
                    editor,
                    phones,
                    season,
                    refresh,
                    details,
                    replay,
                    profile,
                    prev,
                    next,
                    historyPrev,
                    historyNext
                }) {
            b.addActionListener(this);
            b.setFocusable(false);
        }
        ready.addActionListener(this);
        team.addActionListener(this);
        chatInput.addActionListener(this);
        ready.setFocusable(false);
        team.setFocusable(false);
        pitch.addKeyListener(this);
        pitch.addFocusListener(this);
        pitch.addMouseListener(this);
        style(content);
        brand.setFont(new Font("SansSerif", Font.BOLD, 18));
        brand.setForeground(Pitch.GOLD);
        status.setForeground(Pitch.MUTED);
        room.setForeground(Pitch.MUTED);
        for (JButton primary : new JButton[] {host, join, start}) {
            primary.setBackground(Pitch.GOLD);
            primary.setForeground(Pitch.BACK);
        }
        chatInput.setToolTipText("Type a message and press Enter");
        setMinimumSize(new Dimension(1000, 650));
        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1320, screen.width - 50), Math.min(850, screen.height - 70));
        setLocationRelativeTo(null);
        offlineControls();
        timer.start();
    }

    static class Launch implements Runnable {
        public void run() {
            darkDefaults();
            new LanBall().setVisible(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Launch());
    }

    static void darkDefaults() {
        for (String key :
                new String[] {
                    "Panel.background",
                    "Viewport.background",
                    "OptionPane.background",
                    "TextField.background",
                    "PasswordField.background",
                    "TextArea.background",
                    "List.background",
                    "ComboBox.background",
                    "TabbedPane.background",
                    "Button.background",
                    "Spinner.background"
                }) UIManager.put(key, Pitch.PANEL);
        for (String key :
                new String[] {
                    "Label.foreground",
                    "OptionPane.messageForeground",
                    "TextField.foreground",
                    "PasswordField.foreground",
                    "TextArea.foreground",
                    "List.foreground",
                    "ComboBox.foreground",
                    "TabbedPane.foreground",
                    "Button.foreground",
                    "CheckBox.foreground"
                }) UIManager.put(key, Pitch.TEXT);
        UIManager.put("TextField.caretForeground", Pitch.GOLD);
        UIManager.put("PasswordField.caretForeground", Pitch.GOLD);
        UIManager.put("TextArea.caretForeground", Pitch.GOLD);
        UIManager.put("List.selectionBackground", new Color(0x35536d));
        UIManager.put("TabbedPane.selected", new Color(0x35536d));
        UIManager.put("TabbedPane.contentAreaColor", Pitch.PANEL);
        UIManager.put("TabbedPane.focus", Pitch.PANEL);
        UIManager.put("TabbedPane.highlight", Pitch.PANEL);
        UIManager.put("TabbedPane.light", Pitch.PANEL);
        UIManager.put("TabbedPane.shadow", Pitch.PANEL);
        UIManager.put("TabbedPane.darkShadow", Pitch.PANEL);
        UIManager.put("TabbedPane.tabInsets", new Insets(10, 12, 10, 12));
        UIManager.put("ComboBox.disabledForeground", Pitch.MUTED);
        UIManager.put("ComboBox.disabledBackground", Pitch.PANEL);
    }

    static JTextArea area() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(new Font("Monospaced", Font.PLAIN, 12));
        a.setMargin(new Insets(9, 9, 9, 9));
        return a;
    }

    static JPanel row(Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (Component c : parts) p.add(c);
        return p;
    }

    static void style(Component c) {
        c.setForeground(Pitch.TEXT);
        c.setBackground(Pitch.PANEL);
        if (!(c instanceof JTextArea)) c.setFont(new Font("SansSerif", Font.PLAIN, 13));
        if (c instanceof JCheckBox) ((JCheckBox) c).setOpaque(false);
        if (c instanceof JTextField) ((JTextField) c).setCaretColor(Pitch.GOLD);
        if (c instanceof JTextField)
            ((JTextField) c)
                    .setBorder(
                            BorderFactory.createCompoundBorder(
                                    BorderFactory.createLineBorder(new Color(0x354153)),
                                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        if (c instanceof JButton) {
            JButton b = (JButton) c;
            b.setFocusPainted(false);
            b.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(0x3b4a5d)),
                            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        }
        if (c instanceof JScrollPane)
            ((JScrollPane) c).setBorder(BorderFactory.createLineBorder(new Color(0x3b4a5d)));
        if (c instanceof Container)
            for (Component child : ((Container) c).getComponents()) style(child);
        if (c instanceof Pitch) c.setBackground(Pitch.BACK);
    }

    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == timer) {
            nextFrame();
            return;
        }
        try {
            if (source == roomToggle) {
                tabs.setVisible(!tabs.isVisible());
                roomToggle.setText(tabs.isVisible() ? "Hide room" : "Show room");
                revalidate();
            }
            if (source == host || source == join) connect(source == host);
            if (source == leave) {
                disconnect();
                notice("Disconnected.");
            }
            if (source == start) {
                notice(
                        server.startRound(
                                Integer.parseInt(seconds.getText().trim()),
                                Integer.parseInt(target.getText().trim())));
            }
            if (source == team && !updating) {
                ready.setSelected(false);
                synchronized (this) {
                    pendingTeam = team.getSelectedIndex();
                }
            }
            if (source == chatInput) {
                synchronized (this) {
                    outgoingChat = Files.clean(chatInput.getText(), 80);
                }
                chatInput.setText("");
            }
            if (source == refresh) queue("HISTORY\t" + historyPage);
            if (source == historyPrev || source == historyNext) {
                historyPage = Math.max(0, historyPage + (source == historyPrev ? -1 : 1));
                queue("HISTORY\t" + historyPage);
            }
            if (source == prev || source == next) {
                leaguePage = Math.max(0, leaguePage + (source == prev ? -1 : 1));
                queue("LEAGUE\t" + leaguePage);
            }
            if (source == profile) queue("PROFILE\t" + myName);
            if (source == details || source == replay) {
                String selected = historyList.getSelectedValue();
                if (selected == null) {
                    notice("Select a match in History first.");
                    return;
                }
                String id = selected.split(" \\| ")[0];
                queue(
                        (source == details ? "DETAIL\t" : "REPLAY\t")
                                + id
                                + (source == replay ? "\t0" : ""));
            }
            if (source == editor) {
                PitchEditor dialog = new PitchEditor(this, server.match.arena);
                dialog.setVisible(true);
                if (dialog.selected != null) {
                    server.useArena(dialog.selected);
                    notice("Pitch saved and selected: " + dialog.selected.name);
                }
            }
            if (source == season) {
                String choice =
                        JOptionPane.showInputDialog(
                                this,
                                "Season name (existing results stay saved):",
                                server.league.season);
                if (choice != null) server.useSeason(choice.trim());
            }
            if (source == phones && server != null) {
                JTextArea links = area();
                links.setText(phoneLinks());
                links.setEditable(false);
                JOptionPane.showMessageDialog(
                        this, links, "Join from iPhone or Android", JOptionPane.PLAIN_MESSAGE);
            }
        } catch (NumberFormatException error) {
            notice("Enter whole numbers for seconds and goals.");
        } catch (IOException error) {
            notice(error.getMessage());
        }
        updateControls();
        pitch.requestFocusInWindow();
    }

    void notice(String text) {
        status.setText(text);
        noticeUntil = System.nanoTime() + 4000000000L;
    }

    synchronized void queue(String command) {
        if (connected && commands.size() < 20) commands.add(command);
    }

    void offlineControls() {
        host.setEnabled(true);
        join.setEnabled(true);
        leave.setEnabled(false);
        start.setEnabled(false);
        editor.setEnabled(false);
        season.setEnabled(false);
        phones.setEnabled(false);
        team.setEnabled(true);
        ready.setEnabled(false);
    }

    void connect(boolean hosting) {
        char[] secret = password.getPassword();
        if (secret.length < 6 || secret.length > 40) {
            Arrays.fill(secret, '\0');
            notice("Use a password of 6-40 characters.");
            return;
        }
        if (!Accounts.validName(name.getText().trim())
                || !club.getText().trim().matches("[A-Za-z0-9 _-]{2,24}")
                || !shirt.getText().matches("[1-9][0-9]?")) {
            Arrays.fill(secret, '\0');
            notice("Name: 2-16 letters/digits, club: 2-24, shirt: 1-99.");
            return;
        }
        disconnect();
        myName = name.getText().trim();
        synchronized (this) {
            link =
                    new Link(
                            hosting,
                            hosting ? "127.0.0.1" : address.getText().trim(),
                            "LB9\t"
                                    + myName
                                    + "\t"
                                    + club.getText().trim()
                                    + "\t"
                                    + shirt.getText()
                                    + "\t"
                                    + (create.isSelected() ? "create" : "login"),
                            secret);
        }
        host.setEnabled(false);
        join.setEnabled(false);
        leave.setEnabled(true);
        notice("Connecting...");
        Server.start(link);
    }

    synchronized void disconnect() {
        Link old = link;
        link = null;
        if (old != null) old.stop();
        if (server != null) server.close();
        server = null;
        connected = false;
        Arrays.fill(down, false);
        controls = 0;
        pendingTeam = -1;
        incoming = failure = null;
        outgoingChat = "";
        commands.clear();
        responses.clear();
        downloaded = null;
        pitch.clear();
        chatLog.setText("");
        league.setText("");
        historyModel.clear();
        profileText.setText("");
        ready.setSelected(false);
        room.setText(" ");
        connection.setVisible(true);
        offlineControls();
    }

    synchronized void updateControls() {
        controls = 0;
        if (down[KeyEvent.VK_A] || down[KeyEvent.VK_LEFT]) controls |= 1;
        if (down[KeyEvent.VK_D] || down[KeyEvent.VK_RIGHT]) controls |= 2;
        if (down[KeyEvent.VK_W] || down[KeyEvent.VK_UP]) controls |= 4;
        if (down[KeyEvent.VK_S] || down[KeyEvent.VK_DOWN]) controls |= 8;
        if (down[KeyEvent.VK_SPACE]) controls |= 16;
        if (ready.isSelected()) controls |= 32;
    }

    void setKey(int code, boolean held) {
        if (code >= 0 && code < down.length) {
            down[code] = held;
            updateControls();
        }
    }

    public void keyPressed(KeyEvent e) {
        setKey(e.getKeyCode(), true);
    }

    public void keyReleased(KeyEvent e) {
        setKey(e.getKeyCode(), false);
    }

    public void focusLost(FocusEvent e) {
        Arrays.fill(down, false);
        updateControls();
    }

    public void focusGained(FocusEvent e) {}

    public void keyTyped(KeyEvent e) {}

    public void mousePressed(MouseEvent e) {
        pitch.requestFocusInWindow();
    }

    public void mouseClicked(MouseEvent e) {}

    public void mouseReleased(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}

    class Link implements Runnable {
        final boolean hosting;
        final String destination, hello;
        final char[] secret;
        final Socket socket = new Socket();
        ByteArrayOutputStream replayBytes;
        String replayId;

        Link(boolean hosting, String destination, String hello, char[] secret) {
            this.hosting = hosting;
            this.destination = destination;
            this.hello = hello;
            this.secret = secret;
        }

        public void run() {
            try {
                if (hosting) {
                    Server created =
                            new Server(
                                    new File(System.getProperty("lanball.data", "lanball-data")),
                                    Server.PORT);
                    synchronized (LanBall.this) {
                        if (link != this) {
                            created.close();
                            return;
                        }
                        server = created;
                        server.enableMobile();
                    }
                }
                socket.connect(new InetSocketAddress(destination, Server.PORT), 5000);
                socket.setSoTimeout(8000);
                socket.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out =
                        new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                out.writeUTF(hello);
                out.flush();
                String[] challenge = in.readUTF().split("\t");
                if (challenge.length != 3) throw new IOException(challenge[0]);
                String verifier = Accounts.verifier(secret, challenge[0]);
                out.writeUTF(
                        challenge[2].equals("new")
                                ? verifier
                                : Accounts.proof(verifier, challenge[1]));
                out.flush();
                String answer = in.readUTF();
                if (!answer.equals("OK")) throw new IOException(answer);
                synchronized (LanBall.this) {
                    if (link != this) return;
                    responses.add(
                            "CONNECTED\n"
                                    + (hosting
                                            ? addresses() + " | " + server.mobileStatus
                                            : "Joined " + destination));
                    connected = true;
                    commands.add("HISTORY\t0");
                    commands.add("PROFILE\t" + myName);
                }
                while (true) {
                    int keys, wanted;
                    String command, chat;
                    synchronized (LanBall.this) {
                        if (link != this) return;
                        keys = controls;
                        wanted = pendingTeam;
                        pendingTeam = -1;
                        command = commands.isEmpty() ? "" : commands.remove(0);
                        chat = outgoingChat;
                        outgoingChat = "";
                    }
                    out.writeInt(keys);
                    out.writeInt(wanted);
                    out.writeUTF(command);
                    out.writeUTF(chat);
                    out.flush();
                    String frame = in.readUTF(), reply = in.readUTF();
                    synchronized (LanBall.this) {
                        if (link != this) return;
                        incoming = frame;
                    }
                    if (reply.startsWith("REPLAY\t")) download(reply);
                    else if (!reply.isEmpty())
                        synchronized (LanBall.this) {
                            if (link == this) responses.add(reply);
                        }
                    Thread.sleep(16);
                }
            } catch (Exception e) {
                synchronized (LanBall.this) {
                    if (link == this) failure = "Connection ended: " + e.getMessage();
                }
            } finally {
                Arrays.fill(secret, '\0');
                Server.quietClose(socket);
            }
        }

        void download(String reply) throws IOException {
            String[] p = reply.split("\t", -1);
            int offset = Integer.parseInt(p[2]), total = Integer.parseInt(p[3]);
            if (total < 1 || total > Replay.LIMIT) throw new IOException("Invalid replay size.");
            if (offset == 0) {
                replayId = p[1];
                replayBytes = new ByteArrayOutputStream();
            }
            if (!p[1].equals(replayId) || offset != replayBytes.size())
                throw new IOException("Incomplete replay transfer.");
            byte[] chunk = Base64.getDecoder().decode(p[4]);
            replayBytes.write(chunk);
            if (replayBytes.size() > total) throw new IOException("Invalid replay chunk.");
            if (replayBytes.size() == total) {
                Replay result = Replay.read(replayBytes.toByteArray());
                synchronized (LanBall.this) {
                    if (link == this) {
                        downloaded = result;
                        responses.add("REPLAY_READY\n" + replayId);
                    }
                }
                replayBytes = null;
            } else
                synchronized (LanBall.this) {
                    if (link == this)
                        commands.add(0, "REPLAY\t" + replayId + "\t" + replayBytes.size());
                }
        }

        void stop() {
            Server.quietClose(socket);
        }
    }

    String phoneLinks() throws SocketException {
        if (server.mobile == null) return server.mobileStatus;
        String text =
                "1. Connect the phone to the same Wi-Fi as this laptop.\n"
                        + "2. Open Safari or Chrome and enter one of these addresses:\n\n";
        for (String ip : lanAddresses())
            text += "http://" + ip + ":" + server.mobile.port() + "/\n";
        return text
                + "\n3. Join, choose a team and mark Ready.\n"
                + "The laptop host starts the match. Rotate the phone to landscape.\n"
                + "If blocked, allow Java on Private networks in Windows Firewall.\n"
                + "A laptop hotspot also works if campus Wi-Fi isolates devices.";
    }

    static String addresses() throws SocketException {
        String text = "Host IP: ";
        for (String ip : lanAddresses()) text += ip + "  ";
        return text + " | Port " + Server.PORT;
    }

    /**
     * IPv4 addresses another device could reach. WSL/Hyper-V and VM adapters are skipped because a
     * phone can never reach them, but the Windows hotspot adapter (also "virtual") is kept.
     */
    static ArrayList<String> lanAddresses() throws SocketException {
        ArrayList<String> found = new ArrayList<String>();
        Enumeration<NetworkInterface> adapters = NetworkInterface.getNetworkInterfaces();
        while (adapters.hasMoreElements()) {
            NetworkInterface adapter = adapters.nextElement();
            String label = (adapter.getDisplayName() + " " + adapter.getName()).toLowerCase();
            if (!adapter.isUp()
                    || label.contains("vethernet")
                    || label.contains("hyper-v")
                    || label.contains("wsl")
                    || label.contains("vmware")
                    || label.contains("virtualbox")) continue;
            Enumeration<InetAddress> list = adapter.getInetAddresses();
            while (list.hasMoreElements()) {
                InetAddress ip = list.nextElement();
                if (ip instanceof Inet4Address
                        && !ip.isLoopbackAddress()
                        && !ip.isLinkLocalAddress()) found.add(ip.getHostAddress());
            }
        }
        return found;
    }

    void nextFrame() {
        if (!isDisplayable()) {
            timer.stop();
            disconnect();
            return;
        }
        String frame, error;
        Vector<String> replies = new Vector<String>();
        synchronized (this) {
            frame = incoming;
            error = failure;
            incoming = failure = null;
            replies.addAll(responses);
            responses.clear();
        }
        if (error != null) {
            disconnect();
            notice(error);
            return;
        }
        if (frame != null)
            try {
                show(frame);
            } catch (RuntimeException invalid) {
                notice("Skipped an invalid game update.");
            }
        for (String reply : replies) {
            int newline = reply.indexOf('\n');
            String type = newline < 0 ? reply : reply.substring(0, newline),
                    text = newline < 0 ? "" : reply.substring(newline + 1);
            if (type.equals("CONNECTED")) {
                create.setSelected(false);
                connection.setVisible(false);
                room.setText(text);
                ready.setEnabled(true);
                start.setEnabled(server != null);
                editor.setEnabled(server != null);
                season.setEnabled(server != null);
                phones.setEnabled(server != null);
                notice("Connected. Choose a team and mark Ready.");
            }
            if (type.equals("HISTORY")) {
                historyModel.clear();
                for (String line : text.split("\n"))
                    if (!line.isEmpty()) historyModel.addElement(line);
            }
            if (type.equals("PROFILE")) profileText.setText(text);
            if (type.equals("DETAIL")) {
                JTextArea a = area();
                a.setText(text);
                JScrollPane pane = new JScrollPane(a);
                pane.setPreferredSize(new Dimension(620, 240));
                JOptionPane.showMessageDialog(
                        this, pane, "Match summary", JOptionPane.PLAIN_MESSAGE);
            }
            if (type.equals("ERROR")) notice(text);
            if (type.equals("REPLAY_READY")) {
                Replay r;
                synchronized (this) {
                    r = downloaded;
                    downloaded = null;
                }
                if (r != null) new ReplayWindow(r, text).setVisible(true);
            }
        }
        long now = System.nanoTime();
        pitch.animate(Math.min(.05, (now - lastFrame) / 1e9));
        lastFrame = now;
    }

    void show(String frame) {
        String before = pitch.phase;
        pitch.read(frame, myName);
        StringBuilder chat = new StringBuilder(), table = new StringBuilder();
        table.append("Player          Elo  W D L  G A\n");
        for (String line : frame.split("\n")) {
            String[] c = line.split("\t", -1);
            if (c[0].equals("C")) chat.append(c[1]).append('\n');
            if (c[0].equals("M") && System.nanoTime() > noticeUntil) status.setText(c[1]);
            if (c[0].equals("N")) notice(c[1]);
            if (c[0].equals("S")) table.insert(0, c[1] + " | page " + (leaguePage + 1) + "\n\n");
            if (c[0].equals("L"))
                table.append(
                        String.format(
                                "%-14s %4s %2s %2s %2s %2s %2s%n",
                                c[1], c[9], c[3], c[4], c[5], c[6], c[7]));
        }
        if (!chatLog.getText().equals(chat.toString())) chatLog.setText(chat.toString());
        if (!league.getText().equals(table.toString())) league.setText(table.toString());
        Pitch.Dot mine = pitch.mine();
        synchronized (this) {
            if (mine != null && pendingTeam < 0 && team.getSelectedIndex() != mine.team) {
                updating = true;
                team.setSelectedIndex(mine.team);
                updating = false;
            }
        }
        boolean locked = pitch.phase.equals("Playing") || pitch.phase.equals("Save failed");
        team.setEnabled(!locked);
        editor.setEnabled(server != null && !locked);
        season.setEnabled(server != null && !locked);
        start.setText(pitch.phase.equals("Save failed") ? "Retry save" : "Start match");
        if (before.equals("Playing") && !pitch.phase.equals("Playing")) {
            ready.setSelected(false);
            queue("HISTORY\t0");
            queue("PROFILE\t" + myName);
        }
        updateControls();
    }
}
