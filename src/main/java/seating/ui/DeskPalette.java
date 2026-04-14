package seating.ui;

import seating.model.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Side panel containing buttons to create each desk type.
 * Clicking a button enters placement mode on the classroom canvas.
 */
public class DeskPalette extends JPanel {

    private ClassroomPanel canvasPanel;

    /**
     * Creates the desk palette linked to the given canvas.
     *
     * @param canvasPanel the classroom canvas for desk placement
     */
    public DeskPalette(ClassroomPanel canvasPanel) {
        this.canvasPanel = canvasPanel;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(UIScale.dimension(180, 0));
        setBackground(new Color(250, 250, 252));

        addDeskSection();
    }

    private void addDeskSection() {
        JLabel header = new JLabel("Desk Types");
        header.setFont(UIScale.font("SansSerif", Font.BOLD, 13));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(8));

        addDeskButton("Single (1)", "1 seat", "single");
        addDeskButton("Pair (2)", "2 seats side by side", "pair");
        addDeskButton("Group (4)", "4 seats around table", "group");
        addDeskButton("Lab Bench (3)", "3 seats, one side", "lab");
        addDeskButton("Circle (6)", "6 seats, round table", "circle");

        add(Box.createVerticalStrut(16));

        // Zone section
        JLabel zoneHeader = new JLabel("Zones");
        zoneHeader.setFont(UIScale.font("SansSerif", Font.BOLD, 13));
        zoneHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(zoneHeader);
        add(Box.createVerticalStrut(8));

        JButton addZoneBtn = new JButton("+ Add Zone");
        addZoneBtn.setToolTipText("Click-drag on canvas to define a zone");
        addZoneBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addZoneBtn.setMaximumSize(UIScale.dimension(170, 34));
        addZoneBtn.setFont(UIScale.font("SansSerif", Font.PLAIN, 12));
        addZoneBtn.setFocusPainted(false);
        addZoneBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addZoneBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Prompt for zone details, then enter zone drawing mode
                JTextField labelField = new JTextField("Custom Zone", 15);
                JComboBox<String> colorBox = new JComboBox<String>(new String[]{
                    "Green", "Blue", "Yellow", "Orange", "Purple", "Red"
                });
                JPanel panel = new JPanel(new java.awt.GridLayout(2, 2, 5, 5));
                panel.add(new JLabel("Zone name:"));
                panel.add(labelField);
                panel.add(new JLabel("Color:"));
                panel.add(colorBox);

                int result = JOptionPane.showConfirmDialog(DeskPalette.this, panel,
                    "Add Zone", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (result == JOptionPane.OK_OPTION && !labelField.getText().trim().isEmpty()) {
                    Color[] colors = {
                        new Color(76, 175, 80),   // Green
                        new Color(33, 150, 243),  // Blue
                        new Color(255, 193, 7),   // Yellow
                        new Color(255, 152, 0),   // Orange
                        new Color(156, 39, 176),  // Purple
                        new Color(244, 67, 54)    // Red
                    };
                    Color c = colors[colorBox.getSelectedIndex()];
                    canvasPanel.enterZoneDrawMode(labelField.getText().trim(), c);
                }
            }
        });
        add(addZoneBtn);

        add(Box.createVerticalStrut(16));

        // Landmarks
        JLabel landmarkHeader = new JLabel("Landmarks");
        landmarkHeader.setFont(UIScale.font("SansSerif", Font.BOLD, 13));
        landmarkHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(landmarkHeader);
        add(Box.createVerticalStrut(8));

        addLandmarkButton("Teacher's Desk", 6, 2);
        addLandmarkButton("Door", 2, 3);   // matches default door size in MainFrame
        addLandmarkButton("Screen/Board", 8, 2); // default Screen scales with room size
        addLandmarkButton("Window", 2, 6);

        add(Box.createVerticalStrut(10));

        // Room setup
        JButton roomSetup = new JButton("Room Size...");
        roomSetup.setToolTipText("Change classroom dimensions");
        roomSetup.setAlignmentX(Component.LEFT_ALIGNMENT);
        roomSetup.setMaximumSize(UIScale.dimension(170, 30));
        roomSetup.setFont(UIScale.font("SansSerif", Font.PLAIN, 12));
        roomSetup.setFocusPainted(false);
        roomSetup.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showRoomSetupDialog();
            }
        });
        add(roomSetup);

        add(Box.createVerticalStrut(16));

        // Room templates
        JLabel templateHeader = new JLabel("Templates");
        templateHeader.setFont(UIScale.font("SansSerif", Font.BOLD, 13));
        templateHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(templateHeader);
        add(Box.createVerticalStrut(8));

        addTemplateButton("Rows", "5 rows of pair desks");
        addTemplateButton("Groups", "6 group tables in grid");
        addTemplateButton("Lab", "Lab benches + front pairs");
        addTemplateButton("Perimeter", "Desks lining the walls");

        add(Box.createVerticalStrut(16));

        // Instructions
        JLabel tip = new JLabel("<html>Click a desk type,<br>then click canvas<br>to place it.<br><br>"
            + "Scroll wheel to<br>rotate selected.<br><br>"
            + "Right-click for<br>more options.<br><br>"
            + "Right-click a zone<br>to edit or delete.</html>");
        tip.setFont(UIScale.font("SansSerif", Font.PLAIN, 11));
        tip.setForeground(new Color(130, 130, 135));
        tip.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(tip);

        add(Box.createVerticalGlue());
    }

    private void addDeskButton(String label, String tooltip, final String type) {
        JButton btn = new JButton(label);
        btn.setToolTipText(tooltip);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(UIScale.dimension(170, 36));
        btn.setFont(UIScale.font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Desk ghost = createDesk(type, 0, 0);
                if (ghost != null) {
                    canvasPanel.setGhostDesk(ghost);
                }
            }
        });

        add(btn);
        add(Box.createVerticalStrut(4));
    }

    /**
     * Factory method to create a desk by type name.
     *
     * @param type the desk type string
     * @param gx grid x position
     * @param gy grid y position
     * @return a new Desk instance, or null if type is unknown
     */
    private void addTemplateButton(String label, String tooltip) {
        JButton btn = new JButton(label);
        btn.setToolTipText(tooltip);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(UIScale.dimension(170, 32));
        btn.setFocusPainted(false);
        btn.setFont(UIScale.font("SansSerif", Font.PLAIN, 12));
        final String templateName = label;
        btn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int confirm = javax.swing.JOptionPane.showConfirmDialog(DeskPalette.this,
                    "Apply \"" + templateName + "\" template?\nThis will clear all current desks.",
                    "Apply Template", javax.swing.JOptionPane.YES_NO_OPTION);
                if (confirm == javax.swing.JOptionPane.YES_OPTION) {
                    applyTemplate(templateName);
                }
            }
        });
        add(btn);
        add(Box.createVerticalStrut(3));
    }

    private void applyTemplate(String name) {
        Classroom room = canvasPanel.getClassroom();
        room.getDesks().clear();
        room.getLandmarks().clear();

        int cols = room.getGridColumns();
        int rows = room.getGridRows();

        int startRow = Math.max(2, rows * 3 / 10); // leave front 30% open

        if ("Rows".equals(name)) {
            // Single desks (2x2 cells each) in a grid, 70% of room
            for (int y = startRow; y + 2 <= rows; y += 4) {
                for (int x = 2; x + 2 <= cols - 2; x += 4) {
                    room.addDesk(new SingleDesk(x, y));
                }
            }
        } else if ("Groups".equals(name)) {
            // Group tables (4x4 cells each), 70% of room
            for (int y = startRow; y + 4 <= rows; y += 6) {
                for (int x = 2; x + 4 <= cols - 2; x += 6) {
                    room.addDesk(new GroupTable(x, y));
                }
            }
        } else if ("Lab".equals(name)) {
            // Lab benches (6x2 cells each), 70% of room
            for (int y = startRow; y + 2 <= rows; y += 5) {
                for (int x = 0; x + 6 <= cols; x += 7) {
                    room.addDesk(new LabBench(x, y));
                }
            }
        } else if ("Perimeter".equals(name)) {
            // 3-wall layout: BACK + left + right. Front open (teacher/board).
            // Back wall: pair desks (4x2) edge to edge
            for (int x = 0; x + 4 <= cols; x += 4) {
                PairDesk d = new PairDesk(x, rows - 2);
                d.rotate(180);
                room.addDesk(d);
            }
            // Left wall: single desks (2x2), seats face right
            for (int y = 0; y + 2 <= rows - 2; y += 3) {
                SingleDesk d = new SingleDesk(0, y);
                d.rotate(-90);
                room.addDesk(d);
            }
            // Right wall: single desks (2x2), seats face left
            for (int y = 0; y + 2 <= rows - 2; y += 3) {
                SingleDesk d = new SingleDesk(cols - 2, y);
                d.rotate(90);
                room.addDesk(d);
            }
        }

        // Draw all desks to set seat positions
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(800, 600,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        for (Desk d : room.getDesks()) { d.draw(g, room.getGridSize()); }
        g.dispose();

        canvasPanel.repaint();
    }

    private void addLandmarkButton(final String type, final int w, final int h) {
        JButton btn = new JButton(type);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(UIScale.dimension(170, 30));
        btn.setFocusPainted(false);
        btn.setFont(UIScale.font("SansSerif", Font.PLAIN, 12));
        btn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Classroom room = canvasPanel.getClassroom();
                // Find first non-colliding position
                Landmark lm = new Landmark(type, 0, 0, w, h);
                boolean placed = false;
                for (int gy = 0; gy + h <= room.getGridRows() && !placed; gy++) {
                    for (int gx = 0; gx + w <= room.getGridColumns() && !placed; gx++) {
                        lm.setPosition(gx, gy);
                        if (!room.hasLandmarkCollision(lm, null)) {
                            placed = true;
                        }
                    }
                }
                if (placed) {
                    canvasPanel.getUndoManager().execute(
                        new seating.layout.AddLandmarkCommand(room, lm));
                } else {
                    JOptionPane.showMessageDialog(canvasPanel,
                        "No room to place " + type + ".", "Placement",
                        JOptionPane.WARNING_MESSAGE);
                }
                canvasPanel.repaint();
            }
        });
        add(btn);
        add(Box.createVerticalStrut(3));
    }

    private void showRoomSetupDialog() {
        Classroom room = canvasPanel.getClassroom();
        JSpinner colSpinner = new JSpinner(new javax.swing.SpinnerNumberModel(
            room.getGridColumns(), 5, 50, 1));
        JSpinner rowSpinner = new JSpinner(new javax.swing.SpinnerNumberModel(
            room.getGridRows(), 5, 50, 1));

        JPanel panel = new JPanel(new java.awt.GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Width (columns):"));
        panel.add(colSpinner);
        panel.add(new JLabel("Height (rows):"));
        panel.add(rowSpinner);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Room Size", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            int cols = (Integer) colSpinner.getValue();
            int rows = (Integer) rowSpinner.getValue();
            room.resize(cols, rows);
            canvasPanel.setPreferredSize(new java.awt.Dimension(
                room.getPixelWidth(), room.getPixelHeight()));
            canvasPanel.revalidate();
            canvasPanel.repaint();
        }
    }

    public static Desk createDesk(String type, int gx, int gy) {
        switch (type) {
            case "single":  return new SingleDesk(gx, gy);
            case "pair":    return new PairDesk(gx, gy);
            case "group":   return new GroupTable(gx, gy);
            case "lab":     return new LabBench(gx, gy);
            case "circle":  return new CircleTable(gx, gy);
            case "ushape":  return new CircleTable(gx, gy); // backward compat
            default:        return null;
        }
    }
}
