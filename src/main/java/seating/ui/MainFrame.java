package seating.ui;

import seating.model.*;
import seating.constraint.ConstraintSet;
import seating.layout.UndoManager;
import seating.solver.*;
import seating.io.ProjectFile;

import javax.swing.*;
import java.awt.*;

/**
 * The main application window for SeatSolver.
 * Arranges the desk palette, classroom canvas, and side panel
 * in a BorderLayout with toolbar and status bar.
 */
public class MainFrame extends JFrame {

    private Classroom classroom;
    private UndoManager undoManager;
    private ConstraintSet constraintSet;
    private ClassroomPanel classroomPanel;
    private DeskPalette deskPalette;
    private StudentPanel studentPanel;
    private ConstraintPanel constraintPanel;
    private ArrangementPanel arrangementPanel;
    private JTabbedPane sidePanel;
    private JLabel statusLabel;
    private JButton undoBtn, redoBtn, generateBtn;

    /**
     * Creates the main application frame.
     */
    public MainFrame() {
        super("SeatSolver - Classroom Seating Arrangement Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        classroom = new Classroom();
        undoManager = new UndoManager();
        constraintSet = new ConstraintSet();
        classroomPanel = new ClassroomPanel(classroom, undoManager);
        deskPalette = new DeskPalette(classroomPanel);
        studentPanel = new StudentPanel();
        studentPanel.setConstraintSet(constraintSet);
        constraintPanel = new ConstraintPanel(constraintSet,
            studentPanel.getStudents(), classroom.getZones());
        constraintPanel.setStudentPanel(studentPanel);
        studentPanel.setConstraintPanel(constraintPanel);
        classroomPanel.setConstraintPanel(constraintPanel);
        studentPanel.setDeleteListener(new StudentPanel.StudentDeleteListener() {
            public void onStudentDeleted(Student s) {
                constraintPanel.removeConstraintsFor(s);
            }
        });
        arrangementPanel = new ArrangementPanel(classroomPanel);
        // When the user manually drags a student in the canvas, refresh the
        // Results tab so scores reflect the new placement.
        classroomPanel.setArrangementChangeListener(new ClassroomPanel.ArrangementChangeListener() {
            public void onArrangementChanged(seating.model.SeatingArrangement arr) {
                arrangementPanel.refreshCurrentArrangement();
            }
        });

        buildUI();
        addDefaultZones();

        pack();
        setMinimumSize(UIScale.dimension(900, 600));
        setLocationRelativeTo(null);
        // Start maximized so the canvas always gets the full display —
        // avoids pack() sizing surprises on HiDPI monitors.
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        // Toolbar
        add(createToolbar(), BorderLayout.NORTH);

        // Left: Desk palette
        JScrollPane paletteScroll = new JScrollPane(deskPalette);
        paletteScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        paletteScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(210, 210, 215)));
        add(paletteScroll, BorderLayout.WEST);

        // Center: Classroom canvas
        JScrollPane canvasScroll = new JScrollPane(classroomPanel);
        canvasScroll.getViewport().setBackground(new Color(245, 245, 248));
        add(canvasScroll, BorderLayout.CENTER);

        // Right: Tabbed side panel (Students/Rules/Results tabs)
        sidePanel = new JTabbedPane();
        sidePanel.setPreferredSize(UIScale.dimension(380, 0));
        sidePanel.addTab("Students", studentPanel);
        sidePanel.addTab("Rules", constraintPanel);
        sidePanel.addTab("Results", arrangementPanel);
        add(sidePanel, BorderLayout.EAST);

        // Bottom: Status bar
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 215)),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        statusBar.setBackground(new Color(248, 248, 250));
        statusLabel = new JLabel("Desks: 0 | Seats: 0");
        statusLabel.setFont(UIScale.font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(100, 100, 105));
        statusBar.add(statusLabel, BorderLayout.WEST);
        JLabel creditLabel = new JLabel("Created by Harley Chu");
        creditLabel.setFont(UIScale.font("SansSerif", Font.ITALIC, 11));
        creditLabel.setForeground(new Color(140, 140, 145));
        statusBar.add(creditLabel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        // Periodic status refresh
        Timer statusTimer = new Timer(300, new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                updateStatus();
            }
        });
        statusTimer.start();
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 210, 215)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        toolbar.setBackground(new Color(252, 252, 254));

        // File operations
        JButton saveBtn = new JButton("Save");
        saveBtn.setToolTipText("Save project to JSON");
        saveBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) { saveProject(); }
        });
        toolbar.add(saveBtn);

        JButton loadBtn = new JButton("Load");
        loadBtn.setToolTipText("Load project from JSON");
        loadBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) { loadProject(); }
        });
        toolbar.add(loadBtn);

        toolbar.addSeparator();

        // Generate
        generateBtn = new JButton("Generate Seating");
        generateBtn.setToolTipText("Run CSP solver to generate arrangements");
        generateBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                generateSeating();
            }
        });
        toolbar.add(generateBtn);

        toolbar.addSeparator();

        // Undo / Redo
        undoBtn = new JButton("Undo");
        undoBtn.setToolTipText("Undo last action (Ctrl+Z)");
        undoBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.undo()) {
                    classroomPanel.repaint();
                }
            }
        });
        toolbar.add(undoBtn);

        redoBtn = new JButton("Redo");
        redoBtn.setToolTipText("Redo last action (Ctrl+Y)");
        redoBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.redo()) {
                    classroomPanel.repaint();
                }
            }
        });
        toolbar.add(redoBtn);

        toolbar.addSeparator();

        // Clear all
        final JButton clearBtn = new JButton("Clear All \u25BE");
        clearBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                JPopupMenu popup = new JPopupMenu();

                JMenuItem clearAll = new JMenuItem("Clear Everything");
                clearAll.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent ae) {
                        int r = JOptionPane.showConfirmDialog(MainFrame.this,
                            "Remove all desks, landmarks, custom zones, students, and rules?",
                            "Clear Everything", JOptionPane.YES_NO_OPTION);
                        if (r != JOptionPane.YES_OPTION) return;
                        classroom.getDesks().clear();
                        java.util.Iterator<Zone> zit = classroom.getZones().iterator();
                        while (zit.hasNext()) {
                            String label = zit.next().getLabel();
                            if (!"Front".equals(label) && !"Back".equals(label)) zit.remove();
                        }
                        classroom.getLandmarks().clear();
                        studentPanel.getStudents().clear();
                        constraintSet.getAll().clear();
                        undoManager.clear();
                        classroomPanel.setCurrentArrangement(null);
                        classroomPanel.setConstraintData(null, null);
                        arrangementPanel.setResult(null, null, null);
                        classroomPanel.clearSelection();
                        studentPanel.refresh();
                        constraintPanel.refreshList();
                        classroomPanel.repaint();
                        updateStatus();
                    }
                });
                popup.add(clearAll);
                popup.addSeparator();

                JMenuItem clearDesks = new JMenuItem("Clear Desks");
                clearDesks.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent ae) {
                        classroom.getDesks().clear();
                        classroomPanel.clearSelection();
                        classroomPanel.setCurrentArrangement(null);
                        arrangementPanel.setResult(null, null, null);
                        classroomPanel.repaint();
                        updateStatus();
                    }
                });
                popup.add(clearDesks);

                JMenuItem clearLandmarks = new JMenuItem("Clear Landmarks");
                clearLandmarks.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent ae) {
                        classroom.getLandmarks().clear();
                        classroomPanel.clearSelection();
                        classroomPanel.repaint();
                    }
                });
                popup.add(clearLandmarks);

                JMenuItem clearZones = new JMenuItem("Clear Custom Zones");
                clearZones.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent ae) {
                        java.util.Iterator<Zone> zit = classroom.getZones().iterator();
                        while (zit.hasNext()) {
                            String label = zit.next().getLabel();
                            if (!"Front".equals(label) && !"Back".equals(label)) zit.remove();
                        }
                        classroomPanel.repaint();
                    }
                });
                popup.add(clearZones);

                popup.addSeparator();

                JMenuItem clearStudents = new JMenuItem("Clear Students");
                clearStudents.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent ae) {
                        studentPanel.getStudents().clear();
                        studentPanel.refresh();
                        updateStatus();
                    }
                });
                popup.add(clearStudents);

                JMenuItem clearRules = new JMenuItem("Clear Rules");
                clearRules.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent ae) {
                        constraintSet.getAll().clear();
                        constraintPanel.refreshList();
                        updateStatus();
                    }
                });
                popup.add(clearRules);

                JMenuItem clearArrangement = new JMenuItem("Clear Arrangement");
                clearArrangement.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent ae) {
                        classroomPanel.setCurrentArrangement(null);
                        classroomPanel.setConstraintData(null, null);
                        arrangementPanel.setResult(null, null, null);
                        classroomPanel.repaint();
                    }
                });
                popup.add(clearArrangement);

                popup.show(clearBtn, 0, clearBtn.getHeight());
            }
        });
        toolbar.add(clearBtn);

        toolbar.addSeparator();

        // Heat map toggle
        final JButton heatMapBtn = new JButton("Heat Map: OFF");
        heatMapBtn.setToolTipText("Toggle constraint satisfaction heat map");
        heatMapBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                classroomPanel.toggleHeatMap();
                heatMapBtn.setText(classroomPanel.isHeatMapEnabled()
                    ? "Heat Map: ON" : "Heat Map: OFF");
            }
        });
        toolbar.add(heatMapBtn);

        // Export to PNG
        JButton exportBtn = new JButton("Export PNG");
        exportBtn.setToolTipText("Export seating chart as high-res PNG image");
        exportBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                exportPng();
            }
        });
        toolbar.add(exportBtn);

        toolbar.add(Box.createHorizontalGlue());

        JLabel modeLabel = new JLabel("SeatSolver v2.0");
        modeLabel.setFont(UIScale.font("SansSerif", Font.ITALIC, 11));
        modeLabel.setForeground(new Color(130, 130, 135));
        toolbar.add(modeLabel);

        return toolbar;
    }

    private JPanel createPlaceholderPanel(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(250, 250, 252));
        JLabel label = new JLabel("<html><center>" + text + "</center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setForeground(new Color(160, 160, 165));
        p.add(label, BorderLayout.CENTER);
        return p;
    }

    private void addDefaultZones() {
        int cols = classroom.getGridColumns();
        int rows = classroom.getGridRows();
        classroom.addZone(new Zone("Front", 0, 0, cols, 4,
            new Color(76, 175, 80)));
        classroom.addZone(new Zone("Back", 0, rows - 4,
            cols, 4, new Color(33, 150, 243)));
        // Default landmarks
        int screenW = Math.min(10, cols - 4);
        classroom.addLandmark(new Landmark(Landmark.SCREEN,
            (cols - screenW) / 2, 0, screenW, 2));
        classroom.addLandmark(new Landmark(Landmark.DOOR, 0, rows - 3, 2, 3));
    }

    private void updateStatus() {
        int deskCount = classroom.getDesks().size();
        int seatCount = classroom.getTotalSeatCount();
        int studentCount = studentPanel.getStudents().size();
        int ruleCount = constraintSet.size();
        Desk sel = classroomPanel.getSelectedDesk();

        StringBuilder sb = new StringBuilder();
        sb.append("Desks: ").append(deskCount);
        sb.append(" | Seats: ").append(seatCount);
        sb.append(" | Students: ").append(studentCount);
        if (ruleCount > 0) {
            sb.append(" | Rules: ").append(ruleCount);
        }
        if (sel != null) {
            sb.append(" | Selected: ").append(sel.getTypeName())
              .append(" (").append(sel.getSeatCount()).append(")");
        }
        statusLabel.setText(sb.toString());

        // Update toolbar button states
        undoBtn.setEnabled(undoManager.canUndo());
        redoBtn.setEnabled(undoManager.canRedo());
        generateBtn.setEnabled(studentCount > 0 && seatCount > 0);
        undoBtn.setToolTipText(undoManager.canUndo()
            ? "Undo: " + undoManager.getUndoDescription() : "Nothing to undo");
        redoBtn.setToolTipText(undoManager.canRedo()
            ? "Redo: " + undoManager.getRedoDescription() : "Nothing to redo");
    }

    private void exportPng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));
        chooser.setSelectedFile(new java.io.File("seating_chart.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                classroomPanel.exportToPng(chooser.getSelectedFile());
                JOptionPane.showMessageDialog(this,
                    "Seating chart exported to:\n" + chooser.getSelectedFile().getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        chooser.setSelectedFile(new java.io.File("seating_project.json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ProjectFile.save(chooser.getSelectedFile(), classroom,
                    studentPanel.getStudents(), constraintSet);
                JOptionPane.showMessageDialog(this, "Project saved successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ProjectFile.LoadResult loaded = ProjectFile.load(chooser.getSelectedFile());

                // Replace current state
                this.classroom = loaded.classroom;
                this.constraintSet = loaded.constraints;

                // Rebuild student list
                studentPanel.getStudents().clear();
                studentPanel.getStudents().addAll(loaded.students);
                studentPanel.setConstraintSet(constraintSet);

                // Rebuild ALL UI components with new classroom
                undoManager.clear();
                classroomPanel = new ClassroomPanel(classroom, undoManager);
                deskPalette = new DeskPalette(classroomPanel);
                constraintPanel = new ConstraintPanel(constraintSet,
                    studentPanel.getStudents(), classroom.getZones());
                constraintPanel.setStudentPanel(studentPanel);
                studentPanel.setConstraintPanel(constraintPanel);
                classroomPanel.setConstraintPanel(constraintPanel);
                studentPanel.setDeleteListener(new StudentPanel.StudentDeleteListener() {
                    public void onStudentDeleted(Student s) {
                        constraintPanel.removeConstraintsFor(s);
                    }
                });
                arrangementPanel = new ArrangementPanel(classroomPanel);
                classroomPanel.setArrangementChangeListener(new ClassroomPanel.ArrangementChangeListener() {
                    public void onArrangementChanged(seating.model.SeatingArrangement arr) {
                        arrangementPanel.refreshCurrentArrangement();
                    }
                });

                // Refresh the entire UI
                getContentPane().removeAll();
                buildUI();
                revalidate();
                repaint();
                studentPanel.refresh();

                JOptionPane.showMessageDialog(this, "Project loaded successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading: " + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void generateSeating() {
        // Force draw pass to compute seat positions (critical after loading from file)
        java.awt.image.BufferedImage tmp = new java.awt.image.BufferedImage(
            classroom.getPixelWidth(), classroom.getPixelHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D gtmp = tmp.createGraphics();
        for (Desk d : classroom.getDesks()) { d.draw(gtmp, classroom.getGridSize()); }
        gtmp.dispose();

        java.util.List<Student> students = studentPanel.getStudents();
        java.util.List<Seat> seats = classroom.getAllSeats();

        if (students.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add students first (Students tab).");
            return;
        }
        if (seats.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Place desks on the canvas first.");
            return;
        }
        if (students.size() > seats.size()) {
            JOptionPane.showMessageDialog(this,
                "Not enough seats (" + seats.size() + ") for " + students.size() + " students.\n"
                + "Add more desks or remove some students.");
            return;
        }

        // Build adjacency graph from current desk layout
        SeatGraph graph = SeatGraph.buildFrom(classroom);

        // Run CSP solver: find up to 5 solutions, 5-second timeout
        CSPSolver solver = new CSPSolver(constraintSet, graph, 5, 5000);
        SolverResult result = solver.solve(students, seats);

        // Pass constraint data to canvas for conflict overlay
        classroomPanel.setConstraintData(constraintSet, graph);

        // Show results
        arrangementPanel.setResult(result, constraintSet, graph);
        sidePanel.setSelectedIndex(2); // switch to Results tab

        if (result.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No valid arrangements found.\n"
                + "Try relaxing some constraints or adding more seats.",
                "No Solutions", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                "Found " + result.size() + " arrangement(s) in "
                + result.getSolveTimeMs() + "ms.\n"
                + "Best score: " + String.format("%.1f%%", result.getBest().getScore() * 100),
                "Arrangements Generated", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public Classroom getClassroom() { return classroom; }
    public ClassroomPanel getClassroomPanel() { return classroomPanel; }
    public UndoManager getUndoManager() { return undoManager; }
    public ConstraintSet getConstraintSet() { return constraintSet; }
    public StudentPanel getStudentPanel() { return studentPanel; }
    public ConstraintPanel getConstraintPanel() { return constraintPanel; }
    public JTabbedPane getSidePanel() { return sidePanel; }
}
