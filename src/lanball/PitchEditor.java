package lanball;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;

/** A small pitch editor: click to place round bumpers; right-click to remove them. */
public class PitchEditor extends JDialog implements ActionListener, MouseListener {
    final JTextField name = new JTextField("My pitch", 16);
    final JSpinner goal = new JSpinner(new SpinnerNumberModel(144, 120, 220, 2));
    final JSpinner radius = new JSpinner(new SpinnerNumberModel(24, 15, 40, 1));
    final JButton save = new JButton("Save .pitch"),
            open = new JButton("Open .pitch"),
            clear = new JButton("Clear bumpers"),
            use = new JButton("Use pitch");
    final JLabel status =
            new JLabel("Click to add a bumper. Right-click to remove. Keep spawn areas clear.");
    final Canvas canvas = new Canvas();
    Arena arena = new Arena();
    Arena selected;

    class Canvas extends JPanel {
        Canvas() {
            setPreferredSize(new Dimension(768, 432));
            setBackground(Pitch.BACK);
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.scale(getWidth() / 960.0, getHeight() / 540.0);
            Pitch view = new Pitch();
            view.arena = arena;
            view.drawField(g);
            g.setColor(Pitch.MUTED);
            g.drawRect(100, 70, 760, 400);
            for (int t = 0; t < 2; t++)
                for (int n = 0; n < 3; n++) {
                    g.setColor(t == 0 ? Pitch.RED : Pitch.BLUE);
                    g.drawOval((t == 0 ? 240 : 720) - 18, 150 + n * 120 - 18, 36, 36);
                }
            g.dispose();
        }
    }

    PitchEditor(JFrame owner, Arena source) {
        super(owner, "Pitch editor", true);
        try {
            arena = Arena.parse(source.rows());
        } catch (IOException e) {
        }
        name.setText(arena.name);
        goal.setValue(arena.goalHalf * 2);
        JPanel top = new JPanel();
        top.add(new JLabel("Name"));
        top.add(name);
        top.add(new JLabel("Goal opening"));
        top.add(goal);
        top.add(new JLabel("Bumper radius"));
        top.add(radius);
        JPanel bottom = new JPanel();
        for (JButton b : new JButton[] {open, save, clear, use}) {
            bottom.add(b);
            b.addActionListener(this);
        }
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(bottom, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        canvas.addMouseListener(this);
        pack();
        setLocationRelativeTo(owner);
    }

    Arena checked() throws IOException {
        arena.name = name.getText().trim();
        arena.goalHalf = (Integer) goal.getValue() / 2;
        return Arena.parse(arena.rows());
    }

    public void actionPerformed(ActionEvent event) {
        try {
            if (event.getSource() == clear) {
                arena.bumpers.clear();
                canvas.repaint();
                return;
            }
            if (event.getSource() == use) {
                selected = checked();
                dispose();
                return;
            }
            JFileChooser choose =
                    new JFileChooser(
                            new File(
                                    System.getProperty("lanball.data", "lanball-data"), "pitches"));
            choose.setSelectedFile(new File(name.getText().trim() + ".pitch"));
            if (event.getSource() == save
                    && choose.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = choose.getSelectedFile();
                if (f.exists()
                        && JOptionPane.showConfirmDialog(
                                        this,
                                        "Replace this pitch file?",
                                        "Save",
                                        JOptionPane.YES_NO_OPTION)
                                != JOptionPane.YES_OPTION) return;
                Files.write(f, checked().rows());
                status.setText("Saved. Copy this .pitch file to share it.");
            }
            if (event.getSource() == open
                    && choose.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                arena = Arena.parse(Files.read(choose.getSelectedFile()));
                name.setText(arena.name);
                goal.setValue(arena.goalHalf * 2);
                canvas.repaint();
            }
        } catch (IOException e) {
            status.setText(e.getMessage());
        }
    }

    public void mousePressed(MouseEvent e) {
        double x = e.getX() * 960.0 / canvas.getWidth(), y = e.getY() * 540.0 / canvas.getHeight();
        if (SwingUtilities.isRightMouseButton(e)) {
            for (int i = arena.bumpers.size() - 1; i >= 0; i--) {
                Circle c = arena.bumpers.get(i);
                if (Math.hypot(c.x - x, c.y - y) < c.radius + 8) {
                    arena.bumpers.remove(i);
                    break;
                }
            }
        } else {
            Circle c = new Circle((Integer) radius.getValue());
            c.x = (int) x;
            c.y = (int) y;
            arena.bumpers.add(c);
            try {
                checked();
                status.setText("Bumper added.");
            } catch (IOException error) {
                arena.bumpers.remove(c);
                status.setText(error.getMessage());
            }
        }
        canvas.repaint();
    }

    public void mouseClicked(MouseEvent e) {}

    public void mouseReleased(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}
}
