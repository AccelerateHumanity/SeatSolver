package seating.ui;

import seating.constraint.Constraint;
import seating.constraint.ConstraintSet;
import seating.model.SeatingArrangement;
import seating.solver.SeatGraph;
import seating.solver.SolverResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Panel for viewing and cycling through generated seating arrangements.
 * Shows a clickable list of all arrangements with scores and violation
 * details for the selected arrangement.
 */
public class ArrangementPanel extends JPanel {

    private SolverResult result;
    private ConstraintSet constraintSet;
    private SeatGraph graph;
    private int currentIndex;
    private ClassroomPanel classroomPanel;

    // UI components
    private JLabel headerLabel;
    private JPanel listPanel;        // scrollable list of arrangement cards
    private JPanel detailPanel;      // violation details for selected
    private JButton prevBtn, nextBtn;

    /**
     * Creates the arrangement viewer.
     *
     * @param classroomPanel the canvas to update when switching arrangements
     */
    public ArrangementPanel(ClassroomPanel classroomPanel) {
        this.classroomPanel = classroomPanel;
        this.result = null;
        this.currentIndex = 0;

        setLayout(new BorderLayout(0, 4));
        setBackground(new Color(250, 250, 252));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header
        headerLabel = new JLabel("No arrangements yet.");
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(headerLabel, BorderLayout.NORTH);

        // Center: arrangement list + detail split
        JPanel centerPanel = new JPanel(new BorderLayout(0, 6));
        centerPanel.setOpaque(false);

        // Arrangement cards list (scrollable)
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        JScrollPane listScroll = new JScrollPane(listPanel);
        listScroll.setBorder(null);
        listScroll.setPreferredSize(new Dimension(0, 160));
        centerPanel.add(listScroll, BorderLayout.NORTH);

        // Violation detail area
        detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setOpaque(false);
        JScrollPane detailScroll = new JScrollPane(detailPanel);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Constraint Details"));
        centerPanel.add(detailScroll, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // Navigation
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        navPanel.setOpaque(false);
        prevBtn = new JButton("\u25C0 Prev");
        nextBtn = new JButton("Next \u25B6");
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        prevBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (result != null && currentIndex > 0) {
                    currentIndex--;
                    refreshSelection();
                }
            }
        });
        nextBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (result != null && currentIndex < result.size() - 1) {
                    currentIndex++;
                    refreshSelection();
                }
            }
        });
        navPanel.add(prevBtn);
        navPanel.add(nextBtn);
        add(navPanel, BorderLayout.SOUTH);

        showEmptyState();
    }

    private void showEmptyState() {
        listPanel.removeAll();
        detailPanel.removeAll();
        JLabel tip = new JLabel("<html><center><br>Click <b>Generate Seating</b><br>in the toolbar to create<br>seating arrangements.</center></html>");
        tip.setForeground(new Color(140, 140, 145));
        tip.setAlignmentX(Component.CENTER_ALIGNMENT);
        listPanel.add(tip);
        // Force both panels to repaint immediately, not just when the tab
        // becomes visible again.
        listPanel.revalidate();
        listPanel.repaint();
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    /**
     * Sets the solver result, constraint set, and graph for display.
     *
     * @param result the solver output
     * @param constraintSet the constraints used
     * @param graph the adjacency graph used
     */
    public void setResult(SolverResult result, ConstraintSet constraintSet, SeatGraph graph) {
        this.result = result;
        this.constraintSet = constraintSet;
        this.graph = graph;
        this.currentIndex = 0;

        if (result == null || result.isEmpty()) {
            headerLabel.setText("No valid arrangements found.");
            showEmptyState();
            prevBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            classroomPanel.setCurrentArrangement(null);
        } else {
            headerLabel.setText(result.size() + " arrangement(s) found (" + result.getSolveTimeMs() + "ms)");
            buildArrangementList();
            refreshSelection();
        }
    }

    /** Builds the clickable list of arrangement cards. */
    private void buildArrangementList() {
        listPanel.removeAll();
        for (int i = 0; i < result.size(); i++) {
            final int idx = i;
            SeatingArrangement arr = result.get(i);
            double score = arr.getScore();
            int violationCount = 0;
            if (constraintSet != null && graph != null) {
                violationCount = constraintSet.getViolations(arr, graph).size();
            }

            // Card panel
            JPanel card = new JPanel(new BorderLayout(6, 0));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 235)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
            card.setBackground(new Color(255, 255, 255));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Score indicator (colored dot)
            JLabel dot = new JLabel("\u25CF");
            dot.setFont(new Font("SansSerif", Font.PLAIN, 14));
            dot.setForeground(scoreColor(score));
            card.add(dot, BorderLayout.WEST);

            // Text: "#1 — 92.0%"
            JLabel label = new JLabel("#" + (i + 1) + " \u2014 " + String.format("%.1f%%", score * 100));
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
            label.setForeground(new Color(30, 30, 35));
            card.add(label, BorderLayout.CENTER);

            // Violation count
            String vText = violationCount == 0 ? "\u2713" : violationCount + " issue" + (violationCount > 1 ? "s" : "");
            JLabel vLabel = new JLabel(vText);
            vLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            vLabel.setForeground(violationCount == 0 ? new Color(46, 125, 50) : new Color(192, 57, 43));
            card.add(vLabel, BorderLayout.EAST);

            card.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    currentIndex = idx;
                    refreshSelection();
                }
                public void mouseEntered(MouseEvent e) {
                    card.setBackground(new Color(240, 245, 250));
                }
                public void mouseExited(MouseEvent e) {
                    card.setBackground(currentIndex == idx
                        ? new Color(225, 238, 250) : new Color(255, 255, 255));
                }
            });

            listPanel.add(card);
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    /** Updates the canvas and detail panel for the currently selected arrangement. */
    private void refreshSelection() {
        if (result == null || result.isEmpty()) return;

        SeatingArrangement arr = result.get(currentIndex);
        classroomPanel.setCurrentArrangement(arr);

        prevBtn.setEnabled(currentIndex > 0);
        nextBtn.setEnabled(currentIndex < result.size() - 1);

        // Highlight the selected card
        for (int i = 0; i < listPanel.getComponentCount(); i++) {
            Component c = listPanel.getComponent(i);
            if (c instanceof JPanel) {
                ((JPanel) c).setBackground(i == currentIndex
                    ? new Color(225, 238, 250) : new Color(255, 255, 255));
            }
        }

        // Build violation details
        detailPanel.removeAll();
        if (constraintSet != null && graph != null) {
            double totalScore = constraintSet.evaluate(arr, graph);
            JLabel scoreLabel = new JLabel("Overall: " + String.format("%.1f%%", totalScore * 100));
            scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            scoreLabel.setForeground(scoreColor(totalScore));
            scoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailPanel.add(scoreLabel);
            detailPanel.add(Box.createVerticalStrut(6));

            List<Constraint> all = constraintSet.getAll();
            for (Constraint c : all) {
                boolean satisfied = c.isSatisfied(arr, graph);
                double cScore = c.evaluate(arr, graph);

                String icon = satisfied ? "\u2713 " : "\u2717 ";
                String colorHex = satisfied ? "2E7D32" : "C0392B";
                String text = icon + c.describe() + " (" + String.format("%.0f%%", cScore * 100) + ")";
                JLabel ruleLabel = new JLabel("<html><div style='width:200px;color:#" + colorHex + "'>" + text + "</div></html>");
                ruleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
                ruleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                detailPanel.add(ruleLabel);
                detailPanel.add(Box.createVerticalStrut(2));
            }

            if (all.isEmpty()) {
                JLabel noRules = new JLabel("No constraints defined.");
                noRules.setFont(new Font("SansSerif", Font.ITALIC, 11));
                noRules.setForeground(new Color(140, 140, 145));
                noRules.setAlignmentX(Component.LEFT_ALIGNMENT);
                detailPanel.add(noRules);
            }
        }
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    /** Scores at or above this render green (excellent). */
    private static final double SCORE_HIGH = 0.8;
    /** Scores at or above this render orange (ok); below renders red. */
    private static final double SCORE_MID = 0.5;

    private Color scoreColor(double score) {
        if (score >= SCORE_HIGH) return new Color(46, 125, 50);
        if (score >= SCORE_MID) return new Color(230, 126, 34);
        return new Color(192, 57, 43);
    }

    /**
     * Re-reads the currently selected arrangement's score and constraint
     * details. Call after the user manually edits seats in the canvas so
     * the Results tab reflects the updated placement.
     */
    public void refreshCurrentArrangement() {
        if (result == null || result.isEmpty()) return;
        // Rebuild card list to pick up any changed scores, then re-show selection
        buildArrangementList();
        refreshSelection();
    }

    /**
     * Re-evaluates the score of EVERY arrangement in the result against
     * the given constraints and graph, then rebuilds the card list so all
     * scores reflect the current desk/zone layout. Called by live-score
     * refresh after desk or zone moves.
     */
    public void rescoreAll(ConstraintSet cs, SeatGraph gr) {
        if (result == null || cs == null || gr == null) return;
        for (seating.model.SeatingArrangement arr : result.getAll()) {
            arr.setScore(cs.evaluate(arr, gr));
        }
        this.constraintSet = cs;
        this.graph = gr;
        buildArrangementList();
        refreshSelection();
    }

    /** Exposes the current result/constraintSet/graph refs for undo capture. */
    public SolverResult getResult() { return result; }
    public ConstraintSet getCurrentConstraintSet() { return constraintSet; }
    public SeatGraph getGraph() { return graph; }
}
