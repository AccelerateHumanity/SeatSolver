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
        setPreferredSize(new Dimension(200, 0));
        setBackground(new Color(250, 250, 252));

        addDeskSection();
    }

    /**
     * Adds toolbar-style control buttons at the TOP of the palette.
     * Called by MainFrame after construction, passing pre-wired buttons.
     */
    public void addControlButtons(java.util.List<javax.swing.JComponent> buttons) {
        // Insert at position 0 (before Desk Types header)
        int insertAt = 0;
        for (javax.swing.JComponent btn : buttons) {
            btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            // Ensure ALL components (JButton and JPanel rows) stretch to full width
            btn.setMaximumSize(new Dimension(Short.MAX_VALUE, 34));
            add(btn, insertAt++);
            add(Box.createVerticalStrut(3), insertAt++);
        }
        add(Box.createVerticalStrut(8), insertAt++);
        javax.swing.JSeparator sep = new javax.swing.JSeparator(javax.swing.SwingConstants.HORIZONTAL);
        sep.setMaximumSize(new Dimension(Short.MAX_VALUE, 2));
        add(sep, insertAt++);
        add(Box.createVerticalStrut(8), insertAt);
    }

    private void addDeskSection() {
        JLabel header = new JLabel("Desk Types");
        header.setFont(new Font("SansSerif", Font.BOLD, 13));
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
        zoneHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        zoneHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(zoneHeader);
        add(Box.createVerticalStrut(8));

        JButton addZoneBtn = new JButton("+ Add Zone");
        addZoneBtn.setToolTipText("Click-drag on canvas to define a zone");
        addZoneBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addZoneBtn.setMaximumSize(new Dimension(Short.MAX_VALUE, 32));
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
        landmarkHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        landmarkHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(landmarkHeader);
        add(Box.createVerticalStrut(8));

        addLandmarkButton("Teacher's Desk", 6, 2);
        addLandmarkButton("Door", 2, 3);
        addLandmarkButton("Screen/Board", 8, 2);
        addLandmarkButton("Window", 2, 6);

        add(Box.createVerticalStrut(16));

        // Instructions — centered text that fills the palette width
        JLabel tip = new JLabel("<html><div style='text-align:center; color:#828285;'>"
            + "Click a desk type, then click<br>canvas to place it.<br><br>"
            + "Scroll wheel to rotate selected.<br><br>"
            + "Right-click for more options.<br><br>"
            + "Right-click a zone to edit or delete."
            + "</div></html>");
        tip.setHorizontalAlignment(SwingConstants.CENTER);
        tip.setAlignmentX(Component.LEFT_ALIGNMENT);
        tip.setMaximumSize(new Dimension(Short.MAX_VALUE, 200));
        add(tip);

        add(Box.createVerticalGlue());
    }

    private void addDeskButton(String label, String tooltip, final String type) {
        JButton btn = new JButton(label);
        btn.setToolTipText(tooltip);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Short.MAX_VALUE, 32));
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
    private void addLandmarkButton(final String type, final int w, final int h) {
        JButton btn = new JButton(type);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Ghost placement — hand a landmark to the canvas, user picks
                // its spot with a click (mirrors desk placement flow).
                Landmark lm = new Landmark(type, 0, 0, w, h);
                canvasPanel.setGhostLandmark(lm);
            }
        });
        add(btn);
        add(Box.createVerticalStrut(3));
    }

    public void showRoomSetupDialog() {
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
            // Wrap in a command so Ctrl+Z undoes the room resize
            canvasPanel.getUndoManager().execute(
                new seating.layout.ResizeRoomCommand(room, cols, rows));
            canvasPanel.setPreferredSize(new java.awt.Dimension(
                room.getPixelWidth(), room.getPixelHeight()));
            canvasPanel.clearSelection();
            canvasPanel.invalidateArrangement();
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
