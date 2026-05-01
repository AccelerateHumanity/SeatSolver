package seating.ui;

import seating.model.*;
import seating.constraint.ConstraintSet;
import seating.layout.*;
import seating.solver.SeatGraph;
import java.util.List;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * The main classroom canvas panel. Renders the grid, zones, desks,
 * and seat markers using Graphics2D. Handles mouse input for
 * desk selection, dragging, rotation, and placement via Commands
 * routed through the UndoManager.
 */
public class ClassroomPanel extends JPanel {

    private Classroom classroom;
    private UndoManager undoManager;
    private GridManager gridManager;

    // Interaction state
    private Desk selectedDesk;
    private Desk ghostDesk;          // desk being placed from palette
    private Landmark ghostLandmark;  // landmark being placed from palette
    private boolean isDragging;
    private int dragStartGx, dragStartGy; // original position before drag
    private int dragOffsetX, dragOffsetY;
    private int mouseX, mouseY;

    // Landmark interaction
    private Landmark selectedLandmark;
    private boolean isDraggingLandmark;
    private int lmDragOffsetX, lmDragOffsetY;
    private int lmDragStartGx, lmDragStartGy;

    // Student name display + overlays
    private SeatingArrangement currentArrangement;
    private ConstraintSet constraintSet;
    private SeatGraph seatGraph;
    private boolean heatMapEnabled;
    private ConstraintPanel constraintPanel; // for right-click "Add Rule" on student dots

    // Multi-select
    private boolean isRectSelecting;
    private int rectSelStartX, rectSelStartY, rectSelEndX, rectSelEndY;
    private java.util.ArrayList<Desk> selectedDesks = new java.util.ArrayList<Desk>();
    private java.util.ArrayList<Landmark> selectedLandmarks = new java.util.ArrayList<Landmark>();
    private boolean isMultiDragging;
    private int multiDragLastX, multiDragLastY;
    private java.util.HashMap<Desk, int[]> multiDragStartPos = new java.util.HashMap<Desk, int[]>();
    private java.util.HashMap<Landmark, int[]> multiDragLmStartPos = new java.util.HashMap<Landmark, int[]>();
    private double displayScale = 1.0; // uniform scale for filling available space

    // Manual student seat drag (post-generation)
    private Student draggedStudent;
    // (Zone drag-to-move removed — zones are immutable after creation)

    // Rotation batching: accumulates scroll-wheel rotation ticks into a single
    // undo entry. A 500ms timer fires after the last tick to commit the batch.
    private javax.swing.Timer rotationBatchTimer;
    private double rotationBatchTotal; // accumulated degrees this batch
    // Pre-rotation positions captured at batch start so revert can restore
    // the exact grid position (snapDeskIntoBounds only clamps, it can't undo
    // a bump).
    private java.util.HashMap<Desk, int[]> rotationBatchStartDeskPos =
        new java.util.HashMap<Desk, int[]>();
    private java.util.HashMap<Landmark, int[]> rotationBatchStartLmPos =
        new java.util.HashMap<Landmark, int[]>();
    // Multi-duplicate ghost batch: copies follow cursor as a group
    private java.util.List<Desk> pendingDuplicates;
    private int[] pendingDupOffsetsGx, pendingDupOffsetsGy; // offset from cursor per copy
    private Seat draggedStudentSourceSeat;
    private int draggedStudentX, draggedStudentY;

    // Zone label rects from the most recent paint — used by drawStudentNames
    // so student name labels can nudge around zone labels instead of overlapping.
    private java.util.ArrayList<Rectangle2D> lastZoneLabelRects =
        new java.util.ArrayList<Rectangle2D>();

    // Disco mode (easter egg: Ctrl+D)
    private boolean discoMode;
    private javax.swing.Timer discoTimer;
    private double[] discoPx, discoPy; // smooth pixel positions
    private double[] discoDx, discoDy; // velocity per desk
    private double[] discoRotAngle;    // current rotation angle per desk
    private double[] discoRotSpeed;    // rotation speed per desk
    private int[] savedGridX, savedGridY; // original positions to restore
    private double[] savedRotation;
    // Parallel arrays for landmarks during disco
    private double[] discoLmPx, discoLmPy;
    private double[] discoLmDx, discoLmDy;
    private double[] discoLmRotAngle, discoLmRotSpeed;
    private int[] savedLmGridX, savedLmGridY;
    private double[] savedLmRotation;
    private float discoHue;
    private double discoRayAngle;

    // Snap animation
    private Desk animatingDesk;
    private double animFromPx, animFromPy; // pixel start
    private double animToPx, animToPy;     // pixel target
    private int animTargetGx, animTargetGy; // final grid position (authoritative)
    private int animStep;
    private static final int ANIM_STEPS = 8;
    private javax.swing.Timer animTimer;

    // Zone drawing mode
    private boolean zoneDrawMode;
    private String pendingZoneLabel;
    private Color pendingZoneColor;
    private int zoneDragStartX, zoneDragStartY;
    private int zoneDragEndX, zoneDragEndY;
    private boolean zoneDragging;

    // Colors
    private static final Color GRID_COLOR = new Color(220, 220, 225);
    private static final Color GRID_BG = new Color(245, 245, 248);
    private static final Color SELECTION_COLOR = new Color(41, 128, 185, 160);

    /**
     * Creates the classroom canvas panel.
     *
     * @param classroom the classroom model to render
     * @param undoManager the undo/redo manager for commands
     */
    public ClassroomPanel(Classroom classroom, UndoManager undoManager) {
        this.classroom = classroom;
        this.undoManager = undoManager;
        this.gridManager = new GridManager(classroom);
        this.selectedDesk = null;
        this.ghostDesk = null;
        this.isDragging = false;
        this.currentArrangement = null;

        setBackground(GRID_BG);

        setupMouseListeners();
        setupKeyBindings();
    }

    private void setupMouseListeners() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                handleMousePress(e);
            }

            public void mouseReleased(MouseEvent e) {
                handleMouseRelease(e);
                // If the user was scroll-rotating while holding the mouse,
                // fire the batch commit immediately now that they've let go.
                if (rotationBatchTimer != null && rotationBatchTimer.isRunning()
                        && rotationBatchTotal != 0) {
                    rotationBatchTimer.stop();
                    commitRotationBatch();
                }
            }

            public void mouseDragged(MouseEvent e) {
                handleMouseDrag(e);
            }

            public void mouseMoved(MouseEvent e) {
                mouseX = toModelX(e.getX());
                mouseY = toModelY(e.getY());
                if (ghostDesk != null) {
                    int[] snapped = gridManager.snapToGrid(mouseX, mouseY);
                    int[] clamped = gridManager.clampToClassroom(ghostDesk, snapped[0], snapped[1]);
                    ghostDesk.setPosition(clamped[0], clamped[1]);
                    repaint();
                } else if (ghostLandmark != null) {
                    int[] snapped = gridManager.snapToGrid(mouseX, mouseY);
                    int[] clamped = gridManager.clampLandmarkToClassroom(ghostLandmark, snapped[0], snapped[1]);
                    ghostLandmark.setPosition(clamped[0], clamped[1]);
                    repaint();
                } else if (pendingDuplicates != null) {
                    int[] snapped = gridManager.snapToGrid(mouseX, mouseY);
                    int anchorGx = snapped[0];
                    int anchorGy = snapped[1];
                    for (int i = 0; i < pendingDuplicates.size(); i++) {
                        Desk d = pendingDuplicates.get(i);
                        d.setPosition(anchorGx + pendingDupOffsetsGx[i],
                                      anchorGy + pendingDupOffsetsGy[i]);
                    }
                    repaint();
                }
            }

            public void mouseClicked(MouseEvent e) {
                if (ghostDesk != null && SwingUtilities.isLeftMouseButton(e)) {
                    placeGhostDesk();
                    return;
                }
                if (ghostLandmark != null && SwingUtilities.isLeftMouseButton(e)) {
                    placeGhostLandmark();
                    return;
                }
                if (pendingDuplicates != null && SwingUtilities.isLeftMouseButton(e)) {
                    commitPendingDuplicates();
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // Scroll wheel for rotation
        addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (discoMode) return;
                double amount = e.getWheelRotation() * 15.0;

                // Ghost placement: rotate the in-flight preview (no command; ghost
                // isn't persisted until placed).
                if (ghostDesk != null) {
                    ghostDesk.rotate(amount);
                    snapDeskIntoBounds(ghostDesk);
                    repaint();
                    return;
                }
                if (ghostLandmark != null) {
                    ghostLandmark.rotate(amount);
                    snapLandmarkIntoBounds(ghostLandmark);
                    repaint();
                    return;
                }

                // Multi-duplicate ghosts: rotate all copies together. No snap
                // or collision check — ghosts aren't committed yet.
                if (pendingDuplicates != null && !pendingDuplicates.isEmpty()) {
                    for (Desk d : pendingDuplicates) d.rotate(amount);
                    repaint();
                    return;
                }

                if (!selectedDesks.isEmpty() || !selectedLandmarks.isEmpty()) {
                    // Apply rotation directly — NO snap and NO collision
                    // blocking during scroll. Items may clip out of bounds or
                    // overlap mid-rotation; the 500ms batch timer snaps and
                    // collision-checks on commit, reverting if either fails.
                    for (Desk d : selectedDesks) d.rotate(amount);
                    for (Landmark lm : selectedLandmarks) lm.rotate(amount);
                    accumulateRotationUndo(amount);
                    repaint();
                } else if (selectedDesk != null) {
                    selectedDesk.rotate(amount);
                    accumulateRotationUndo(amount);
                    repaint();
                } else if (selectedLandmark != null) {
                    selectedLandmark.rotate(amount);
                    accumulateRotationUndo(amount);
                    repaint();
                }
            }
        });
    }

    private void setupKeyBindings() {
        setFocusable(true);

        // Use InputMap/ActionMap for reliable key bindings
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        // Delete selected desk
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteDesk");
        am.put("deleteDesk", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                // Delete multi-selected desks AND landmarks
                if (!selectedDesks.isEmpty() || !selectedLandmarks.isEmpty()) {
                    boolean deletedDesk = !selectedDesks.isEmpty();
                    for (Desk d : new java.util.ArrayList<Desk>(selectedDesks)) {
                        undoManager.execute(new DeleteDeskCommand(classroom, d));
                    }
                    for (Landmark lm : new java.util.ArrayList<Landmark>(selectedLandmarks)) {
                        undoManager.execute(new DeleteLandmarkCommand(classroom, lm));
                    }
                    selectedDesks.clear();
                    selectedLandmarks.clear();
                    if (deletedDesk) invalidateArrangement();
                    repaint();
                    return;
                }
                if (selectedLandmark != null) {
                    undoManager.execute(new DeleteLandmarkCommand(classroom, selectedLandmark));
                    selectedLandmark = null;
                    repaint();
                    return;
                }
                if (selectedDesk != null) {
                    undoManager.execute(new DeleteDeskCommand(classroom, selectedDesk));
                    selectedDesk = null;
                    invalidateArrangement();
                    repaint();
                }
            }
        });

        // Escape - cancel ghost or deselect
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
        am.put("escape", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                ghostDesk = null;
                ghostLandmark = null;
                pendingDuplicates = null;
                pendingDupOffsetsGx = null;
                pendingDupOffsetsGy = null;
                selectedDesk = null;
                selectedLandmark = null;
                selectedDesks.clear();
                selectedLandmarks.clear();
                isDraggingLandmark = false;
                isDragging = false;
                isMultiDragging = false;
                zoneDrawMode = false;
                zoneDragging = false;
                rotationBatchStartDeskPos.clear();
                rotationBatchStartLmPos.clear();
                if (rotationBatchTimer != null) rotationBatchTimer.stop();
                rotationBatchTotal = 0;
                if (animTimer != null) animTimer.stop();
                animatingDesk = null;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }
        });

        // Ctrl+Z - undo
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        am.put("undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (undoManager.undo()) {
                    selectedDesk = null;
                    selectedLandmark = null;
                    selectedDesks.clear();
                    selectedLandmarks.clear();
                    refreshLiveScore();
                    repaint();
                }
            }
        });

        // Ctrl+Y - redo
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        am.put("redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (undoManager.redo()) {
                    selectedDesk = null;
                    selectedLandmark = null;
                    selectedDesks.clear();
                    selectedLandmarks.clear();
                    refreshLiveScore();
                    repaint();
                }
            }
        });

        // Ctrl+D — disco mode easter egg
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "disco");
        am.put("disco", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                toggleDisco();
            }
        });
    }

    private void toggleDisco() {
        discoMode = !discoMode;
        if (discoMode) {
            // Cancel any in-flight drag/ghost interaction. If the user was
            // mid-drag when disco started, the drop logic would otherwise
            // never run — leaving the desk in a half-moved (and possibly
            // colliding) state on disco exit.
            if (isDragging && selectedDesk != null) {
                selectedDesk.setPosition(dragStartGx, dragStartGy);
            }
            if (isDraggingLandmark && selectedLandmark != null) {
                selectedLandmark.setPosition(lmDragStartGx, lmDragStartGy);
            }
            if (isMultiDragging) {
                for (Desk d : selectedDesks) {
                    int[] pos = multiDragStartPos.get(d);
                    if (pos != null) d.setPosition(pos[0], pos[1]);
                }
                for (Landmark lm : selectedLandmarks) {
                    int[] pos = multiDragLmStartPos.get(lm);
                    if (pos != null) lm.setPosition(pos[0], pos[1]);
                }
            }
            isDragging = false;
            isDraggingLandmark = false;
            isMultiDragging = false;
            isRectSelecting = false;
            draggedStudent = null;
            draggedStudentSourceSeat = null;
            ghostDesk = null;
            ghostLandmark = null;
            pendingDuplicates = null;
            pendingDupOffsetsGx = null;
            pendingDupOffsetsGy = null;
            if (rotationBatchTimer != null) rotationBatchTimer.stop();
            rotationBatchTotal = 0;
            rotationBatchStartDeskPos.clear();
            rotationBatchStartLmPos.clear();
            setCursor(Cursor.getDefaultCursor());

            java.util.List<Desk> desks = classroom.getDesks();
            int n = desks.size();
            if (n == 0) { discoMode = false; return; }
            int gs = classroom.getGridSize();

            // Save original positions to restore later
            savedGridX = new int[n];
            savedGridY = new int[n];
            savedRotation = new double[n];
            discoPx = new double[n];
            discoPy = new double[n];
            discoDx = new double[n];
            discoDy = new double[n];
            discoRotAngle = new double[n];
            discoRotSpeed = new double[n];

            java.util.Random rnd = new java.util.Random();
            for (int i = 0; i < n; i++) {
                savedGridX[i] = desks.get(i).getGridX();
                savedGridY[i] = desks.get(i).getGridY();
                savedRotation[i] = desks.get(i).getRotation();
                discoPx[i] = desks.get(i).getGridX() * gs;
                discoPy[i] = desks.get(i).getGridY() * gs;
                double speed = 1.0 + rnd.nextDouble() * 2.0;
                double angle = rnd.nextDouble() * Math.PI * 2;
                discoDx[i] = Math.cos(angle) * speed;
                discoDy[i] = Math.sin(angle) * speed;
                discoRotAngle[i] = desks.get(i).getRotation();
                discoRotSpeed[i] = (rnd.nextDouble() - 0.5) * 4;
            }

            // Set up landmarks to also join the disco
            java.util.List<Landmark> lms = classroom.getLandmarks();
            int nLm = lms.size();
            savedLmGridX = new int[nLm]; savedLmGridY = new int[nLm]; savedLmRotation = new double[nLm];
            discoLmPx = new double[nLm]; discoLmPy = new double[nLm];
            discoLmDx = new double[nLm]; discoLmDy = new double[nLm];
            discoLmRotAngle = new double[nLm]; discoLmRotSpeed = new double[nLm];
            for (int i = 0; i < nLm; i++) {
                Landmark lm = lms.get(i);
                savedLmGridX[i] = lm.getGridX();
                savedLmGridY[i] = lm.getGridY();
                savedLmRotation[i] = lm.getRotation();
                discoLmPx[i] = lm.getGridX() * gs;
                discoLmPy[i] = lm.getGridY() * gs;
                double lmSpeed = 1.0 + rnd.nextDouble() * 2.0;
                double lmAngle = rnd.nextDouble() * Math.PI * 2;
                discoLmDx[i] = Math.cos(lmAngle) * lmSpeed;
                discoLmDy[i] = Math.sin(lmAngle) * lmSpeed;
                discoLmRotAngle[i] = lm.getRotation();
                discoLmRotSpeed[i] = (rnd.nextDouble() - 0.5) * 4;
            }

            discoHue = 0;
            discoRayAngle = 0;

            discoTimer = new javax.swing.Timer(16, new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    int gs2 = classroom.getGridSize();
                    int maxW = classroom.getGridColumns() * gs2;
                    int maxH = classroom.getGridRows() * gs2;
                    java.util.List<Desk> dks = classroom.getDesks();
                    int n2 = Math.min(dks.size(), discoPx.length);

                    for (int i = 0; i < n2; i++) {
                        discoPx[i] += discoDx[i];
                        discoPy[i] += discoDy[i];
                        discoRotAngle[i] += discoRotSpeed[i];
                        int dw = dks.get(i).getWidthInCells() * gs2;
                        int dh = dks.get(i).getHeightInCells() * gs2;

                        if (discoPx[i] <= 0) { discoPx[i] = 0; discoDx[i] = Math.abs(discoDx[i]); }
                        if (discoPx[i] + dw >= maxW) { discoPx[i] = maxW - dw; discoDx[i] = -Math.abs(discoDx[i]); }
                        if (discoPy[i] <= 0) { discoPy[i] = 0; discoDy[i] = Math.abs(discoDy[i]); }
                        if (discoPy[i] + dh >= maxH) { discoPy[i] = maxH - dh; discoDy[i] = -Math.abs(discoDy[i]); }
                    }

                    // Elastic desk-desk collision
                    for (int i = 0; i < n2; i++) {
                        int w1 = dks.get(i).getWidthInCells() * gs2;
                        int h1 = dks.get(i).getHeightInCells() * gs2;
                        double cx1 = discoPx[i] + w1 / 2.0;
                        double cy1 = discoPy[i] + h1 / 2.0;
                        for (int j = i + 1; j < n2; j++) {
                            int w2 = dks.get(j).getWidthInCells() * gs2;
                            int h2 = dks.get(j).getHeightInCells() * gs2;
                            double cx2 = discoPx[j] + w2 / 2.0;
                            double cy2 = discoPy[j] + h2 / 2.0;
                            double dx = cx2 - cx1;
                            double dy = cy2 - cy1;
                            double minDist = (Math.max(w1, h1) + Math.max(w2, h2)) / 2.5;
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (dist < minDist && dist > 0.1) {
                                double nx = dx / dist, ny = dy / dist;
                                double rv = (discoDx[i] - discoDx[j]) * nx + (discoDy[i] - discoDy[j]) * ny;
                                if (rv > 0) {
                                    discoDx[i] -= rv * nx; discoDy[i] -= rv * ny;
                                    discoDx[j] += rv * nx; discoDy[j] += rv * ny;
                                }
                                double push = (minDist - dist) / 2 + 0.5;
                                discoPx[i] -= nx * push; discoPy[i] -= ny * push;
                                discoPx[j] += nx * push; discoPy[j] += ny * push;
                            }
                        }
                    }

                    // Landmark physics: wall bounce + rotation accumulate
                    java.util.List<Landmark> lms2 = classroom.getLandmarks();
                    int nL2 = Math.min(lms2.size(), discoLmPx.length);
                    for (int i = 0; i < nL2; i++) {
                        discoLmPx[i] += discoLmDx[i];
                        discoLmPy[i] += discoLmDy[i];
                        discoLmRotAngle[i] += discoLmRotSpeed[i];
                        int lw = lms2.get(i).getGridW() * gs2;
                        int lh = lms2.get(i).getGridH() * gs2;
                        if (discoLmPx[i] <= 0) { discoLmPx[i] = 0; discoLmDx[i] = Math.abs(discoLmDx[i]); }
                        if (discoLmPx[i] + lw >= maxW) { discoLmPx[i] = maxW - lw; discoLmDx[i] = -Math.abs(discoLmDx[i]); }
                        if (discoLmPy[i] <= 0) { discoLmPy[i] = 0; discoLmDy[i] = Math.abs(discoLmDy[i]); }
                        if (discoLmPy[i] + lh >= maxH) { discoLmPy[i] = maxH - lh; discoLmDy[i] = -Math.abs(discoLmDy[i]); }
                    }

                    // Landmark-landmark elastic collisions
                    for (int i = 0; i < nL2; i++) {
                        int w1 = lms2.get(i).getGridW() * gs2;
                        int h1 = lms2.get(i).getGridH() * gs2;
                        double cx1 = discoLmPx[i] + w1 / 2.0;
                        double cy1 = discoLmPy[i] + h1 / 2.0;
                        for (int j = i + 1; j < nL2; j++) {
                            int w2 = lms2.get(j).getGridW() * gs2;
                            int h2 = lms2.get(j).getGridH() * gs2;
                            double cx2 = discoLmPx[j] + w2 / 2.0;
                            double cy2 = discoLmPy[j] + h2 / 2.0;
                            double dx = cx2 - cx1, dy = cy2 - cy1;
                            double minDist = (Math.max(w1, h1) + Math.max(w2, h2)) / 2.5;
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (dist < minDist && dist > 0.1) {
                                double nx = dx / dist, ny = dy / dist;
                                double rv = (discoLmDx[i] - discoLmDx[j]) * nx + (discoLmDy[i] - discoLmDy[j]) * ny;
                                if (rv > 0) {
                                    discoLmDx[i] -= rv * nx; discoLmDy[i] -= rv * ny;
                                    discoLmDx[j] += rv * nx; discoLmDy[j] += rv * ny;
                                }
                                double push = (minDist - dist) / 2 + 0.5;
                                discoLmPx[i] -= nx * push; discoLmPy[i] -= ny * push;
                                discoLmPx[j] += nx * push; discoLmPy[j] += ny * push;
                            }
                        }
                    }

                    // Landmark-desk cross collisions
                    for (int i = 0; i < nL2; i++) {
                        int lw = lms2.get(i).getGridW() * gs2;
                        int lh = lms2.get(i).getGridH() * gs2;
                        double cx1 = discoLmPx[i] + lw / 2.0;
                        double cy1 = discoLmPy[i] + lh / 2.0;
                        for (int j = 0; j < n2; j++) {
                            int dw = dks.get(j).getWidthInCells() * gs2;
                            int dh = dks.get(j).getHeightInCells() * gs2;
                            double cx2 = discoPx[j] + dw / 2.0;
                            double cy2 = discoPy[j] + dh / 2.0;
                            double dx = cx2 - cx1, dy = cy2 - cy1;
                            double minDist = (Math.max(lw, lh) + Math.max(dw, dh)) / 2.5;
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (dist < minDist && dist > 0.1) {
                                double nx = dx / dist, ny = dy / dist;
                                double rv = (discoLmDx[i] - discoDx[j]) * nx + (discoLmDy[i] - discoDy[j]) * ny;
                                if (rv > 0) {
                                    discoLmDx[i] -= rv * nx; discoLmDy[i] -= rv * ny;
                                    discoDx[j] += rv * nx; discoDy[j] += rv * ny;
                                }
                                double push = (minDist - dist) / 2 + 0.5;
                                discoLmPx[i] -= nx * push; discoLmPy[i] -= ny * push;
                                discoPx[j] += nx * push; discoPy[j] += ny * push;
                            }
                        }
                    }

                    discoHue = (discoHue + 0.003f) % 1.0f;
                    discoRayAngle += 0.015;
                    repaint();
                }
            });
            discoTimer.start();
        } else {
            // Stop and RESTORE original positions
            if (discoTimer != null) { discoTimer.stop(); discoTimer = null; }
            java.util.List<Desk> desks = classroom.getDesks();
            if (savedGridX != null) {
                for (int i = 0; i < desks.size() && i < savedGridX.length; i++) {
                    desks.get(i).setPosition(savedGridX[i], savedGridY[i]);
                    desks.get(i).setRotation(savedRotation[i]);
                }
            }
            java.util.List<Landmark> lms = classroom.getLandmarks();
            if (savedLmGridX != null) {
                for (int i = 0; i < lms.size() && i < savedLmGridX.length; i++) {
                    lms.get(i).setPosition(savedLmGridX[i], savedLmGridY[i]);
                    lms.get(i).setRotation(savedLmRotation[i]);
                }
            }
            // Null all disco arrays to prevent stale references if classroom
            // changes between toggles (desks added/removed)
            savedGridX = null; savedGridY = null; savedRotation = null;
            discoPx = null; discoPy = null; discoDx = null; discoDy = null;
            discoRotAngle = null; discoRotSpeed = null;
            savedLmGridX = null; savedLmGridY = null; savedLmRotation = null;
            discoLmPx = null; discoLmPy = null; discoLmDx = null; discoLmDy = null;
            discoLmRotAngle = null; discoLmRotSpeed = null;
            repaint();
        }
    }

    private void handleMousePress(MouseEvent e) {
        if (discoMode) return; // lock editing during disco
        if (zoneDrawMode && SwingUtilities.isLeftMouseButton(e)) {
            zoneDragStartX = toModelX(e.getX());
            zoneDragStartY = toModelY(e.getY());
            zoneDragEndX = toModelX(e.getX());
            zoneDragEndY = toModelY(e.getY());
            zoneDragging = true;
            repaint();
            return;
        }
        if (ghostDesk != null || ghostLandmark != null || pendingDuplicates != null) return;
        requestFocusInWindow();

        // Ensure seat global positions are up-to-date before hit-testing
        if (currentArrangement != null) refreshSeatPositions();
        // Student drag (post-generation): if a seat is occupied and clicked,
        // pick up the student so the user can drag them to a new seat.
        if (currentArrangement != null && SwingUtilities.isLeftMouseButton(e)) {
            Seat hitSeat = getSeatAt(toModelX(e.getX()), toModelY(e.getY()));
            if (hitSeat != null) {
                Student s = currentArrangement.getStudentAt(hitSeat);
                if (s != null) {
                    draggedStudent = s;
                    draggedStudentSourceSeat = hitSeat;
                    draggedStudentX = toModelX(e.getX());
                    draggedStudentY = toModelY(e.getY());
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    repaint();
                    return;
                }
            }
        }

        Desk hit = classroom.getDeskAt(toModelX(e.getX()), toModelY(e.getY()));

        // Ctrl+click on a desk → toggle it in multi-selection (add/remove)
        if (hit != null && SwingUtilities.isLeftMouseButton(e) && e.isControlDown()) {
            if (selectedDesks.contains(hit)) selectedDesks.remove(hit);
            else selectedDesks.add(hit);
            selectedDesk = null;
            selectedLandmark = null;
            repaint();
            return;
        }

        // If clicking on a desk that's part of multi-selection, start group drag
        if (hit != null && selectedDesks.contains(hit) && SwingUtilities.isLeftMouseButton(e)) {
            isMultiDragging = true;
            multiDragLastX = toModelX(e.getX());
            multiDragLastY = toModelY(e.getY());
            saveMultiDragStartPositions();
            repaint();
            return;
        }

        if (hit != null && SwingUtilities.isLeftMouseButton(e)) {
            selectedDesk = hit;
            selectedLandmark = null;
            isDragging = true;
            dragStartGx = hit.getGridX();
            dragStartGy = hit.getGridY();
            int gs = classroom.getGridSize();
            dragOffsetX = toModelX(e.getX()) - hit.getGridX() * gs;
            dragOffsetY = toModelY(e.getY()) - hit.getGridY() * gs;
            repaint();
        } else if (SwingUtilities.isLeftMouseButton(e)) {
            // Check landmarks
            Landmark lmHit = classroom.getLandmarkAt(toModelX(e.getX()), toModelY(e.getY()));
            // Ctrl+click on a landmark → toggle in multi-selection
            if (lmHit != null && e.isControlDown()) {
                if (selectedLandmarks.contains(lmHit)) selectedLandmarks.remove(lmHit);
                else selectedLandmarks.add(lmHit);
                selectedDesk = null;
                selectedLandmark = null;
                repaint();
                return;
            }
            // If clicking a landmark that's in multi-select, start group drag
            if (lmHit != null && selectedLandmarks.contains(lmHit)) {
                isMultiDragging = true;
                multiDragLastX = toModelX(e.getX());
                multiDragLastY = toModelY(e.getY());
                saveMultiDragStartPositions();
                repaint();
                return;
            }
            if (lmHit != null) {
                selectedLandmark = lmHit;
                selectedDesk = null;
                isDraggingLandmark = true;
                lmDragStartGx = lmHit.getGridX();
                lmDragStartGy = lmHit.getGridY();
                int gs = classroom.getGridSize();
                lmDragOffsetX = toModelX(e.getX()) - lmHit.getGridX() * gs;
                lmDragOffsetY = toModelY(e.getY()) - lmHit.getGridY() * gs;
            } else {
                // Empty space — start rectangle selection
                selectedDesk = null;
                selectedLandmark = null;
                selectedDesks.clear();
                selectedLandmarks.clear();
                int mx = toModelX(e.getX());
                int my = toModelY(e.getY());
                isRectSelecting = true;
                rectSelStartX = mx;
                rectSelStartY = my;
                rectSelEndX = mx;
                rectSelEndY = my;
            }
            repaint();
        }
    }

    private void handleMouseDrag(MouseEvent e) {
        if (discoMode) return;
        // Follow the cursor with the picked-up student
        if (draggedStudent != null) {
            draggedStudentX = toModelX(e.getX());
            draggedStudentY = toModelY(e.getY());
            repaint();
            return;
        }
        // (Zone drag removed — zones are immutable after creation)
        if (isMultiDragging && (!selectedDesks.isEmpty() || !selectedLandmarks.isEmpty())) {
            int gs = classroom.getGridSize();
            int dx = toModelX(e.getX()) - multiDragLastX;
            int dy = toModelY(e.getY()) - multiDragLastY;
            // Move in grid-cell increments
            int gridDx = dx / gs;
            int gridDy = dy / gs;
            if (gridDx != 0 || gridDy != 0) {
                // Compute group envelope so the whole selection stays in the grid.
                int envMinX = Integer.MAX_VALUE, envMinY = Integer.MAX_VALUE;
                int envMaxX = Integer.MIN_VALUE, envMaxY = Integer.MIN_VALUE;
                for (Desk d : selectedDesks) {
                    envMinX = Math.min(envMinX, d.getGridX());
                    envMinY = Math.min(envMinY, d.getGridY());
                    envMaxX = Math.max(envMaxX, d.getGridX() + d.getWidthInCells());
                    envMaxY = Math.max(envMaxY, d.getGridY() + d.getHeightInCells());
                }
                for (Landmark lm : selectedLandmarks) {
                    java.awt.geom.Rectangle2D rb = lm.getRotatedBounds(gs);
                    int lx = (int) Math.floor(rb.getX() / gs);
                    int ly = (int) Math.floor(rb.getY() / gs);
                    int rx = (int) Math.ceil((rb.getX() + rb.getWidth()) / gs);
                    int ry = (int) Math.ceil((rb.getY() + rb.getHeight()) / gs);
                    envMinX = Math.min(envMinX, lx);
                    envMinY = Math.min(envMinY, ly);
                    envMaxX = Math.max(envMaxX, rx);
                    envMaxY = Math.max(envMaxY, ry);
                }
                // Clamp the delta so no item would exit the grid
                int cols = classroom.getGridColumns();
                int rows = classroom.getGridRows();
                if (gridDx < 0) gridDx = Math.max(gridDx, -envMinX);
                else            gridDx = Math.min(gridDx, cols - envMaxX);
                if (gridDy < 0) gridDy = Math.max(gridDy, -envMinY);
                else            gridDy = Math.min(gridDy, rows - envMaxY);

                if (gridDx != 0 || gridDy != 0) {
                    for (Desk d : selectedDesks) {
                        d.setPosition(d.getGridX() + gridDx, d.getGridY() + gridDy);
                    }
                    for (Landmark lm : selectedLandmarks) {
                        lm.setPosition(lm.getGridX() + gridDx, lm.getGridY() + gridDy);
                    }
                    multiDragLastX += gridDx * gs;
                    multiDragLastY += gridDy * gs;
                }
                repaint();
            }
            return;
        }
        if (zoneDrawMode && zoneDragging) {
            zoneDragEndX = toModelX(e.getX());
            zoneDragEndY = toModelY(e.getY());
            repaint();
            return;
        }
        if (isRectSelecting) {
            rectSelEndX = toModelX(e.getX());
            rectSelEndY = toModelY(e.getY());
            // Find desks AND landmarks inside the selection rectangle
            selectedDesks.clear();
            selectedLandmarks.clear();
            int rx = Math.min(rectSelStartX, rectSelEndX);
            int ry = Math.min(rectSelStartY, rectSelEndY);
            int rw = Math.abs(rectSelEndX - rectSelStartX);
            int rh = Math.abs(rectSelEndY - rectSelStartY);
            Rectangle2D selRect = new Rectangle2D.Double(rx, ry, rw, rh);
            int gs = classroom.getGridSize();
            for (Desk d : classroom.getDesks()) {
                if (selRect.intersects(d.getBounds(gs))) {
                    selectedDesks.add(d);
                }
            }
            for (Landmark lm : classroom.getLandmarks()) {
                if (selRect.intersects(lm.getBounds(gs))) {
                    selectedLandmarks.add(lm);
                }
            }
            repaint();
            return;
        }
        if (isDraggingLandmark && selectedLandmark != null) {
            int gs = classroom.getGridSize();
            int newGx = (toModelX(e.getX()) - lmDragOffsetX + gs / 2) / gs;
            int newGy = (toModelY(e.getY()) - lmDragOffsetY + gs / 2) / gs;
            // Rotation-aware clamp so rotated landmarks stay fully on-grid
            int[] clamped = gridManager.clampLandmarkToClassroom(selectedLandmark, newGx, newGy);
            selectedLandmark.setPosition(clamped[0], clamped[1]);
            repaint();
            return;
        }
        if (isDragging && selectedDesk != null) {
            int gs = classroom.getGridSize();
            int newGx = (toModelX(e.getX()) - dragOffsetX + gs / 2) / gs;
            int newGy = (toModelY(e.getY()) - dragOffsetY + gs / 2) / gs;
            int[] clamped = gridManager.clampToClassroom(selectedDesk, newGx, newGy);
            selectedDesk.setPosition(clamped[0], clamped[1]);
            repaint();
        }
    }

    private void handleMouseRelease(MouseEvent e) {
        if (discoMode) return;
        // Drop a dragged student onto a target seat (swap or move)
        if (draggedStudent != null) {
            // Arrangement might have been invalidated mid-drag (Clear All, delete desk, etc.)
            if (currentArrangement == null) {
                draggedStudent = null;
                draggedStudentSourceSeat = null;
                setCursor(Cursor.getDefaultCursor());
                repaint();
                return;
            }
            Seat target = getAnySeatAt(toModelX(e.getX()), toModelY(e.getY()));
            if (target != null && target != draggedStudentSourceSeat) {
                Student targetStudent = currentArrangement.getStudentAt(target);
                currentArrangement.unassign(draggedStudentSourceSeat);
                if (targetStudent != null) {
                    // Swap
                    currentArrangement.unassign(target);
                    currentArrangement.assign(draggedStudentSourceSeat, targetStudent);
                }
                currentArrangement.assign(target, draggedStudent);
                // Recompute score with updated placement
                if (constraintSet != null && seatGraph != null) {
                    currentArrangement.setScore(
                        constraintSet.evaluate(currentArrangement, seatGraph));
                }
                // Notify listener so the Results tab updates
                if (arrangementChangeListener != null) {
                    arrangementChangeListener.onArrangementChanged(currentArrangement);
                }
            }
            draggedStudent = null;
            draggedStudentSourceSeat = null;
            setCursor(Cursor.getDefaultCursor());
            repaint();
            return;
        }
        if (isMultiDragging) {
            isMultiDragging = false;
            // Check collision for all moved items — snap ALL back if any collide
            boolean collision = false;
            for (Desk d : selectedDesks) {
                if (classroom.hasCollision(d, d)) { collision = true; break; }
            }
            if (!collision) {
                for (Landmark lm : selectedLandmarks) {
                    if (classroom.hasLandmarkCollision(lm, lm)) { collision = true; break; }
                }
            }
            if (collision) {
                for (Desk d : selectedDesks) {
                    int[] pos = multiDragStartPos.get(d);
                    if (pos != null) d.setPosition(pos[0], pos[1]);
                }
                for (Landmark lm : selectedLandmarks) {
                    int[] pos = multiDragLmStartPos.get(lm);
                    if (pos != null) lm.setPosition(pos[0], pos[1]);
                }
            }
            repaint();
            return;
        }
        if (isRectSelecting) {
            isRectSelecting = false;
            repaint();
            return;
        }
        if (isDraggingLandmark) {
            isDraggingLandmark = false;
            if (selectedLandmark == null) { repaint(); return; }
            int newGx = selectedLandmark.getGridX();
            int newGy = selectedLandmark.getGridY();
            // Check collision — snap back if overlapping a desk or another landmark
            if (classroom.hasLandmarkCollision(selectedLandmark, selectedLandmark)) {
                selectedLandmark.setPosition(lmDragStartGx, lmDragStartGy);
            } else if (newGx != lmDragStartGx || newGy != lmDragStartGy) {
                selectedLandmark.setPosition(lmDragStartGx, lmDragStartGy);
                undoManager.execute(new MoveLandmarkCommand(selectedLandmark,
                    lmDragStartGx, lmDragStartGy, newGx, newGy));
                refreshLiveScore();
            }
            repaint();
            return;
        }
        if (zoneDrawMode && zoneDragging) {
            zoneDragging = false;
            zoneDrawMode = false;
            setCursor(Cursor.getDefaultCursor());
            int gs = classroom.getGridSize();
            // Convert pixel coords to grid coords
            int gx1 = Math.min(zoneDragStartX, zoneDragEndX) / gs;
            int gy1 = Math.min(zoneDragStartY, zoneDragEndY) / gs;
            int gx2 = (Math.max(zoneDragStartX, zoneDragEndX) + gs - 1) / gs;
            int gy2 = (Math.max(zoneDragStartY, zoneDragEndY) + gs - 1) / gs;
            int gw = Math.max(1, gx2 - gx1);
            int gh = Math.max(1, gy2 - gy1);
            if (gw >= 1 && gh >= 1) {
                Zone newZone = new Zone(pendingZoneLabel, gx1, gy1, gw, gh, pendingZoneColor);
                undoManager.execute(new seating.layout.AddZoneCommand(classroom, newZone));
            }
            repaint();
            return;
        }
        if (isDragging && selectedDesk != null) {
            isDragging = false;
            int newGx = selectedDesk.getGridX();
            int newGy = selectedDesk.getGridY();

            // Check collision — if colliding, animate snap back to original
            if (classroom.hasCollision(selectedDesk, selectedDesk)) {
                int gs = classroom.getGridSize();
                startSnapAnimation(selectedDesk, newGx * gs, newGy * gs, dragStartGx, dragStartGy);
            } else if (newGx != dragStartGx || newGy != dragStartGy) {
                // Execute as command (for undo), then animate
                selectedDesk.setPosition(dragStartGx, dragStartGy);
                undoManager.execute(new MoveDeskCommand(selectedDesk,
                    dragStartGx, dragStartGy, newGx, newGy));
                // Live-refresh arrangement score after desk moved
                refreshLiveScore();
            }
            repaint();
        }
    }

    private void placeGhostDesk() {
        if (ghostDesk == null) return;
        int gs = classroom.getGridSize();
        int[] clamped = gridManager.clampToClassroom(ghostDesk, ghostDesk.getGridX(), ghostDesk.getGridY());
        ghostDesk.setPosition(clamped[0], clamped[1]);

        // Check collision
        if (!classroom.hasCollision(ghostDesk, null)) {
            undoManager.execute(new AddDeskCommand(classroom, ghostDesk));
            selectedDesk = ghostDesk;
        }
        ghostDesk = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /** Commits the pending multi-duplicate batch as a single compound command. */
    private void commitPendingDuplicates() {
        if (pendingDuplicates == null || pendingDuplicates.isEmpty()) return;
        java.util.List<Command> batch = new java.util.ArrayList<Command>();
        int skipped = 0;
        for (Desk copy : pendingDuplicates) {
            // Rotation-aware bounds check: clampToClassroom accounts for the
            // rotated footprint, so ghosts rotated before commit get the right
            // check (plain gx+dw>cols would ignore rotation).
            int[] clamped = gridManager.clampToClassroom(copy, copy.getGridX(), copy.getGridY());
            if (clamped[0] != copy.getGridX() || clamped[1] != copy.getGridY()) {
                // Would need to shift — treat as "no room" so placement stays truthful.
                skipped++;
                continue;
            }
            if (classroom.hasCollision(copy, null)) {
                skipped++;
                continue;
            }
            AddDeskCommand ac = new AddDeskCommand(classroom, copy);
            ac.execute();
            batch.add(ac);
        }
        if (!batch.isEmpty()) {
            undoManager.pushExecuted(new seating.layout.CompoundCommand(
                batch, "Duplicate " + batch.size() + " desks"));
            invalidateArrangement();
        }
        pendingDuplicates = null;
        pendingDupOffsetsGx = null;
        pendingDupOffsetsGy = null;
        setCursor(Cursor.getDefaultCursor());
        if (skipped > 0) {
            JOptionPane.showMessageDialog(this,
                "Placed " + batch.size() + " copy/copies. " + skipped + " skipped (no room or collision).",
                "Duplicate", JOptionPane.INFORMATION_MESSAGE);
        }
        repaint();
    }

    private void placeGhostLandmark() {
        if (ghostLandmark == null) return;
        int[] clamped = gridManager.clampLandmarkToClassroom(
            ghostLandmark, ghostLandmark.getGridX(), ghostLandmark.getGridY());
        ghostLandmark.setPosition(clamped[0], clamped[1]);

        if (!classroom.hasLandmarkCollision(ghostLandmark, null)) {
            undoManager.execute(new seating.layout.AddLandmarkCommand(classroom, ghostLandmark));
            selectedLandmark = ghostLandmark;
        }
        ghostLandmark = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    private void showContextMenu(MouseEvent e) {
        if (discoMode) return;
        // Don't show context menu while placing a ghost or pending duplicates
        if (ghostDesk != null || ghostLandmark != null || pendingDuplicates != null) return;
        int gs = classroom.getGridSize();

        // Student seat right-click → quick rule menu (takes priority over everything)
        if (currentArrangement != null) {
            Seat hitSeat = getSeatAt(toModelX(e.getX()), toModelY(e.getY()));
            if (hitSeat != null) {
                Student s = currentArrangement.getStudentAt(hitSeat);
                if (s != null) {
                    showStudentContextMenu(e, s, hitSeat);
                    return;
                }
            }
        }

        // Multi-select context menu (desks + landmarks)
        // If exactly 1 desk is selected (and 0 landmarks), treat as a single-desk
        // right-click so the user gets the full context menu (with Duplicate... dialog)
        // instead of the multi-select menu showing "Duplicate (1)".
        int totalSelected = selectedDesks.size() + selectedLandmarks.size();
        if (totalSelected == 1 && selectedDesks.size() == 1 && selectedLandmarks.isEmpty()) {
            selectedDesk = selectedDesks.get(0);
            selectedDesks.clear();
            repaint();
            // Fall through to single-desk context menu below
        } else if (totalSelected == 1 && selectedLandmarks.size() == 1 && selectedDesks.isEmpty()) {
            Landmark lm = selectedLandmarks.get(0);
            selectedLandmarks.clear();
            showLandmarkContextMenu(e, lm);
            return;
        }
        totalSelected = selectedDesks.size() + selectedLandmarks.size();
        if (totalSelected > 0) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem rotateAll = new JMenuItem("Rotate Selected 90\u00B0");
            rotateAll.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    // Apply rotations, snap to bounds, then collision-check.
                    // Revert any item that ends up overlapping after the snap.
                    for (Desk d : selectedDesks) {
                        d.rotate(90);
                        snapDeskIntoBounds(d);
                        if (classroom.hasCollision(d, d)) {
                            d.rotate(-90);
                            snapDeskIntoBounds(d);
                        } else {
                            undoManager.pushExecuted(new RotateDeskCommand(d, 90));
                        }
                    }
                    for (Landmark lm : selectedLandmarks) {
                        lm.rotate(90);
                        snapLandmarkIntoBounds(lm);
                        if (classroom.hasLandmarkCollision(lm, lm)) {
                            lm.rotate(-90);
                            snapLandmarkIntoBounds(lm);
                        } else {
                            undoManager.pushExecuted(new RotateLandmarkCommand(lm, 90));
                        }
                    }
                    refreshLiveScore();
                    repaint();
                }
            });
            JMenuItem deleteAll = new JMenuItem("Delete Selected (" + totalSelected + ")");
            deleteAll.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    boolean deletedDesk = !selectedDesks.isEmpty();
                    for (Desk d : new java.util.ArrayList<Desk>(selectedDesks)) {
                        undoManager.execute(new DeleteDeskCommand(classroom, d));
                    }
                    for (Landmark lm : new java.util.ArrayList<Landmark>(selectedLandmarks)) {
                        undoManager.execute(new DeleteLandmarkCommand(classroom, lm));
                    }
                    selectedDesks.clear();
                    selectedLandmarks.clear();
                    if (deletedDesk) invalidateArrangement();
                    repaint();
                }
            });
            JMenuItem dupAll = new JMenuItem("Duplicate Desks (" + selectedDesks.size() + ")");
            dupAll.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    // Build a batch of copies that follow the cursor as a group,
                    // preserving the original layout's relative spacing. One
                    // click commits them all (Phase 6c).
                    java.util.ArrayList<Desk> copies = new java.util.ArrayList<Desk>();
                    int[] offX = new int[selectedDesks.size()];
                    int[] offY = new int[selectedDesks.size()];
                    int anchorGx = selectedDesks.get(0).getGridX();
                    int anchorGy = selectedDesks.get(0).getGridY();
                    for (int i = 0; i < selectedDesks.size(); i++) {
                        Desk d = selectedDesks.get(i);
                        Desk copy = DeskPalette.createDesk(d.getTypeName(),
                            d.getGridX(), d.getGridY());
                        if (copy == null) continue;
                        copy.setRotation(d.getRotation());
                        copies.add(copy);
                        offX[copies.size() - 1] = d.getGridX() - anchorGx;
                        offY[copies.size() - 1] = d.getGridY() - anchorGy;
                    }
                    if (copies.isEmpty()) return;
                    pendingDuplicates = copies;
                    pendingDupOffsetsGx = java.util.Arrays.copyOf(offX, copies.size());
                    pendingDupOffsetsGy = java.util.Arrays.copyOf(offY, copies.size());
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    repaint();
                }
            });
            menu.add(rotateAll);
            if (!selectedDesks.isEmpty()) menu.add(dupAll);
            menu.addSeparator();
            menu.add(deleteAll);
            menu.show(this, e.getX(), e.getY());
            return;
        }

        // Check landmarks (desk takes priority over landmark)
        if (classroom.getDeskAt(toModelX(e.getX()), toModelY(e.getY())) == null) {
            Landmark lmHit = classroom.getLandmarkAt(toModelX(e.getX()), toModelY(e.getY()));
            if (lmHit != null) {
                showLandmarkContextMenu(e, lmHit);
                return;
            }
        }

        // Check zones — collect ALL at this point so overlapping zones can
        // be disambiguated via a submenu.
        if (classroom.getDeskAt(toModelX(e.getX()), toModelY(e.getY())) == null) {
            java.util.List<Zone> zonesAtPoint = new java.util.ArrayList<Zone>();
            for (Zone zone : classroom.getZones()) {
                if (zone.contains(toModelX(e.getX()), toModelY(e.getY()), gs)) {
                    zonesAtPoint.add(zone);
                }
            }
            if (zonesAtPoint.size() == 1) {
                showZoneContextMenu(e, zonesAtPoint.get(0));
                return;
            } else if (zonesAtPoint.size() > 1) {
                // Show a picker submenu: "Edit which zone?"
                final MouseEvent origE = e;
                JPopupMenu picker = new JPopupMenu();
                JMenuItem header = new JMenuItem("Edit which zone?");
                header.setEnabled(false);
                header.setFont(new Font("SansSerif", Font.BOLD, 12));
                picker.add(header);
                picker.addSeparator();
                for (final Zone z : zonesAtPoint) {
                    JMenuItem item = new JMenuItem(z.getLabel() + "  ("
                        + z.getGridWidth() + "\u00D7" + z.getGridHeight() + ")");
                    item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent ae) {
                            showZoneContextMenu(origE, z);
                        }
                    });
                    picker.add(item);
                }
                picker.show(this, e.getX(), e.getY());
                return;
            }
        }

        Desk hit = classroom.getDeskAt(toModelX(e.getX()), toModelY(e.getY()));
        if (hit == null) return;
        selectedDesk = hit;
        repaint();

        JPopupMenu menu = new JPopupMenu();

        JMenuItem rotateCW = new JMenuItem("Rotate 90\u00B0 CW");
        rotateCW.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                tryRotateDesk(selectedDesk, 90);
            }
        });

        JMenuItem rotateCCW = new JMenuItem("Rotate 90\u00B0 CCW");
        rotateCCW.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                tryRotateDesk(selectedDesk, -90);
            }
        });

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                undoManager.execute(new DeleteDeskCommand(classroom, selectedDesk));
                selectedDesk = null;
                invalidateArrangement();
                repaint();
            }
        });

        JMenuItem duplicate = new JMenuItem("Duplicate...");
        duplicate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                String input = JOptionPane.showInputDialog(ClassroomPanel.this,
                    "How many copies?", "Duplicate " + selectedDesk.getTypeName(), JOptionPane.PLAIN_MESSAGE);
                if (input == null) return;
                int count;
                try { count = Math.max(1, Math.min(50, Integer.parseInt(input.trim()))); }
                catch (NumberFormatException ex) { return; }

                // Build N ghost copies that follow the cursor as a group.
                // Click commits; Esc cancels. Scroll wheel rotates all ghosts.
                int dw = selectedDesk.getWidthInCells();
                int dh = selectedDesk.getHeightInCells();
                int cols = classroom.getGridColumns();
                int perRow = Math.max(1, cols / Math.max(1, dw));
                java.util.ArrayList<Desk> copies = new java.util.ArrayList<Desk>();
                int[] offX = new int[count];
                int[] offY = new int[count];
                for (int i = 0; i < count; i++) {
                    int row = i / perRow;
                    int col = i % perRow;
                    Desk copy = DeskPalette.createDesk(selectedDesk.getTypeName(), 0, 0);
                    if (copy == null) continue;
                    copy.setRotation(selectedDesk.getRotation());
                    int idx = copies.size();
                    copies.add(copy);
                    offX[idx] = col * dw;
                    offY[idx] = row * dh;
                }
                if (copies.isEmpty()) return;
                pendingDuplicates = copies;
                pendingDupOffsetsGx = java.util.Arrays.copyOf(offX, copies.size());
                pendingDupOffsetsGy = java.util.Arrays.copyOf(offY, copies.size());
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                repaint();
            }
        });

        menu.add(rotateCW);
        menu.add(rotateCCW);
        menu.addSeparator();
        menu.add(duplicate);
        menu.add(delete);
        menu.show(this, e.getX(), e.getY());
    }

    private void showLandmarkContextMenu(MouseEvent e, final Landmark landmark) {
        selectedLandmark = landmark;
        selectedDesk = null;
        repaint();

        JPopupMenu menu = new JPopupMenu();

        JMenuItem rotateItem = new JMenuItem("Rotate 90\u00B0");
        rotateItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                landmark.rotate(90);
                snapLandmarkIntoBounds(landmark);
                if (classroom.hasLandmarkCollision(landmark, landmark)) {
                    landmark.rotate(-90);
                    snapLandmarkIntoBounds(landmark);
                    JOptionPane.showMessageDialog(ClassroomPanel.this,
                        "Cannot rotate — would overlap another item.",
                        "Rotate", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    undoManager.pushExecuted(new RotateLandmarkCommand(landmark, 90));
                    refreshLiveScore();
                }
                repaint();
            }
        });

        JMenuItem editItem = new JMenuItem("Edit Label");
        editItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                JTextField labelField = new JTextField(landmark.getLabel(), 15);
                JPanel panel = new JPanel(new java.awt.GridLayout(1, 2, 5, 5));
                panel.add(new JLabel("Label:"));
                panel.add(labelField);
                int result = JOptionPane.showConfirmDialog(ClassroomPanel.this, panel,
                    "Edit Landmark", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (result == JOptionPane.OK_OPTION && !labelField.getText().trim().isEmpty()) {
                    landmark.setLabel(labelField.getText().trim());
                    repaint();
                }
            }
        });

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                undoManager.execute(new DeleteLandmarkCommand(classroom, landmark));
                selectedLandmark = null;
                repaint();
            }
        });

        menu.add(rotateItem);
        menu.add(editItem);
        menu.addSeparator();
        menu.add(deleteItem);
        menu.show(this, e.getX(), e.getY());
    }

    private void showZoneContextMenu(MouseEvent e, final Zone zone) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem editItem = new JMenuItem("Rename Zone: " + zone.getLabel());
        editItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                JTextField labelField = new JTextField(zone.getLabel(), 15);
                JPanel panel = new JPanel(new java.awt.GridLayout(1, 2, 5, 5));
                panel.add(new JLabel("Label:"));
                panel.add(labelField);
                int result = JOptionPane.showConfirmDialog(ClassroomPanel.this, panel,
                    "Rename Zone", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (result == JOptionPane.OK_OPTION && !labelField.getText().trim().isEmpty()) {
                    undoManager.execute(new seating.layout.EditZoneCommand(zone,
                        labelField.getText().trim(),
                        zone.getGridX(), zone.getGridY(),
                        zone.getGridWidth(), zone.getGridHeight(),
                        zone.getColor()));
                    repaint();
                }
            }
        });

        JMenuItem deleteItem = new JMenuItem("Delete Zone");
        deleteItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                undoManager.execute(new seating.layout.DeleteZoneCommand(classroom, zone));
                if (constraintPanel != null) constraintPanel.removeConstraintsForZone(zone);
                invalidateArrangement();
                repaint();
            }
        });

        menu.add(editItem);
        menu.addSeparator();
        menu.add(deleteItem);
        menu.show(this, e.getX(), e.getY());
    }

    /**
     * Shows the right-click context menu for a student seated in the current
     * arrangement. Gives quick access to "Add Proximity/Zone Rule" dialogs
     * pre-filled for the student, and a "Remove from Seat" action.
     */
    private void showStudentContextMenu(MouseEvent e, final Student student, final Seat sourceSeat) {
        JPopupMenu menu = new JPopupMenu();

        // Header (disabled)
        JMenuItem header = new JMenuItem("Student: " + student.getName());
        header.setEnabled(false);
        header.setFont(new Font("SansSerif", Font.BOLD, 12));
        menu.add(header);
        menu.addSeparator();

        JMenuItem proxItem = new JMenuItem("Add Proximity Rule for " + student.getName() + "\u2026");
        proxItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if (constraintPanel != null) constraintPanel.addProximityRuleFor(student);
            }
        });
        menu.add(proxItem);

        JMenuItem zoneItem = new JMenuItem("Add Zone Rule for " + student.getName() + "\u2026");
        zoneItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if (constraintPanel != null) constraintPanel.addZoneRuleFor(student);
            }
        });
        menu.add(zoneItem);

        menu.addSeparator();

        JMenuItem removeItem = new JMenuItem("Remove from Seat");
        removeItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if (currentArrangement == null) return;
                currentArrangement.unassign(sourceSeat);
                if (constraintSet != null && seatGraph != null) {
                    currentArrangement.setScore(
                        constraintSet.evaluate(currentArrangement, seatGraph));
                }
                if (arrangementChangeListener != null) {
                    arrangementChangeListener.onArrangementChanged(currentArrangement);
                }
                repaint();
            }
        });
        menu.add(removeItem);

        menu.show(this, e.getX(), e.getY());
    }

    /**
     * Sets a ghost desk for placement mode.
     *
     * @param desk the desk to place
     */
    public void setGhostDesk(Desk desk) {
        this.ghostDesk = desk;
        this.selectedDesk = null;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public void clearGhostDesk() {
        this.ghostDesk = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /**
     * Enters landmark placement mode with a ghost that follows the cursor.
     * Mirrors {@link #setGhostDesk(Desk)} — click places the landmark, Escape
     * cancels.
     */
    public void setGhostLandmark(Landmark lm) {
        this.ghostLandmark = lm;
        this.ghostDesk = null;
        this.selectedLandmark = null;
        this.selectedDesk = null;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public void clearGhostLandmark() {
        this.ghostLandmark = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /**
     * Sets the current seating arrangement to display student names on seats.
     *
     * @param arrangement the arrangement to display, or null to clear
     */
    /**
     * Sets the current seating arrangement to display student names on seats.
     *
     * @param arrangement the arrangement to display, or null to clear
     */
    public void setCurrentArrangement(SeatingArrangement arrangement) {
        this.currentArrangement = arrangement;
        repaint();
    }

    /** Listener for manual changes to the current arrangement (e.g. student drag). */
    public interface ArrangementChangeListener {
        void onArrangementChanged(SeatingArrangement arr);
    }

    private ArrangementChangeListener arrangementChangeListener;

    /** Registers a listener to be notified when the user manually edits the arrangement. */
    public void setArrangementChangeListener(ArrangementChangeListener l) {
        this.arrangementChangeListener = l;
    }

    /**
     * Invalidates the current seating arrangement and associated constraint
     * data. Call whenever desks, students, zones, or constraints change in a
     * way that makes the drawn conflict overlay stale (deletion, template
     * apply, clear action, etc.). Notifies listeners via
     * {@link #arrangementChangeListener} with {@code null}.
     */
    public void invalidateArrangement() {
        if (currentArrangement == null && constraintSet == null && seatGraph == null) return;
        currentArrangement = null;
        constraintSet = null;
        seatGraph = null;
        if (arrangementChangeListener != null) {
            arrangementChangeListener.onArrangementChanged(null);
        }
        repaint();
    }

    /**
     * Sets the constraint set and graph for conflict overlay rendering.
     *
     * @param constraintSet the active constraints
     * @param graph the seat adjacency graph
     */
    public void setConstraintData(ConstraintSet constraintSet, SeatGraph graph) {
        this.constraintSet = constraintSet;
        this.seatGraph = graph;
    }

    /**
     * Sets the ConstraintPanel reference used by the right-click menu
     * on student dots to launch "Add Rule" dialogs pre-filled for that student.
     */
    public void setConstraintPanel(ConstraintPanel cp) {
        this.constraintPanel = cp;
    }

    /**
     * Enters zone drawing mode. User click-drags on canvas to define a zone rectangle.
     *
     * @param label the zone label
     * @param color the zone color
     */
    /**
     * Starts a smooth snap animation for a desk sliding to its grid position.
     * Uses a Swing Timer to interpolate over ~120ms.
     */
    private void startSnapAnimation(Desk desk, double fromPx, double fromPy,
                                     int targetGx, int targetGy) {
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
            if (animatingDesk != null) {
                // Finish previous animation instantly at exact grid position
                animatingDesk.setPosition(animTargetGx, animTargetGy);
            }
        }
        animatingDesk = desk;
        animFromPx = fromPx;
        animFromPy = fromPy;
        animTargetGx = targetGx;
        animTargetGy = targetGy;
        animToPx = targetGx * classroom.getGridSize();
        animToPy = targetGy * classroom.getGridSize();
        animStep = 0;

        animTimer = new javax.swing.Timer(15, new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                animStep++;
                // Ease-out interpolation: t = 1 - (1-progress)^2
                double progress = (double) animStep / ANIM_STEPS;
                double t = 1.0 - (1.0 - progress) * (1.0 - progress);

                double px = animFromPx + (animToPx - animFromPx) * t;
                double py = animFromPy + (animToPy - animFromPy) * t;
                int gs = classroom.getGridSize();
                animatingDesk.setPosition((int) Math.round(px / gs), (int) Math.round(py / gs));

                if (animStep >= ANIM_STEPS) {
                    animTimer.stop();
                    animatingDesk.setPosition(animTargetGx, animTargetGy);
                    animatingDesk = null;
                }
                repaint();
            }
        });
        animTimer.start();
    }

    public void enterZoneDrawMode(String label, Color color) {
        this.zoneDrawMode = true;
        this.pendingZoneLabel = label;
        this.pendingZoneColor = color;
        this.zoneDragging = false;
        this.ghostDesk = null;
        this.selectedDesk = null;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    /** Toggles heat map mode on/off. */
    public void toggleHeatMap() {
        heatMapEnabled = !heatMapEnabled;
        repaint();
    }

    public boolean isHeatMapEnabled() { return heatMapEnabled; }

    /**
     * Exports the current canvas to a PNG image at 2x resolution.
     *
     * @param file the output file
     * @throws java.io.IOException if writing fails
     */
    public void exportToPng(java.io.File file) throws java.io.IOException {
        int scale = 2;
        int w = classroom.getPixelWidth() * scale;
        int h = classroom.getPixelHeight() * scale;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);

        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, classroom.getPixelWidth(), classroom.getPixelHeight());

        int gs = classroom.getGridSize();
        drawGrid(g, gs);
        drawZones(g, gs);
        drawLandmarks(g, gs);
        drawDesks(g, gs);
        // Zone labels BEFORE student names so names nudge around them
        drawZoneLabels(g, gs);
        drawStudentNames(g, gs);
        if (currentArrangement != null && constraintSet != null && seatGraph != null) {
            ConflictOverlay.draw(g, currentArrangement, constraintSet, seatGraph);
        }
        drawFrontLabel(g);

        g.dispose();
        javax.imageio.ImageIO.write(img, "png", file);
    }

    public Desk getSelectedDesk() { return selectedDesk; }
    /** Exposes the currently-displayed arrangement + constraint refs for undo capture. */
    public SeatingArrangement getCurrentArrangement() { return currentArrangement; }
    public ConstraintSet getConstraintSet() { return constraintSet; }
    public SeatGraph getSeatGraph() { return seatGraph; }

    /** Clamps a desk's position so its (possibly rotated) footprint stays in the grid. */
    /**
     * Live-refreshes the arrangement score after a desk/zone mutation. Forces a
     * draw pass so seat positions update, then re-evaluates constraints and
     * notifies the Results tab listener.
     */
    // Cached scratch image for seat position computation — avoids 12MB+
    // garbage per click from creating a new BufferedImage every time.
    private java.awt.image.BufferedImage scratchImage;
    private java.awt.Graphics2D scratchGraphics;

    /**
     * Forces all desks to compute their seat global positions by running
     * a scratch draw pass. Reuses a cached image to avoid GC pressure.
     */
    private void refreshSeatPositions() {
        int w = Math.max(1, classroom.getPixelWidth());
        int h = Math.max(1, classroom.getPixelHeight());
        // Recreate only if dimensions changed
        if (scratchImage == null || scratchImage.getWidth() != w || scratchImage.getHeight() != h) {
            if (scratchGraphics != null) scratchGraphics.dispose();
            scratchImage = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
            scratchGraphics = scratchImage.createGraphics();
        }
        int gs = classroom.getGridSize();
        for (Desk d : classroom.getDesks()) d.draw(scratchGraphics, gs);
    }

    private void refreshLiveScore() {
        if (currentArrangement == null || constraintSet == null || seatGraph == null) return;
        refreshSeatPositions();
        currentArrangement.setScore(constraintSet.evaluate(currentArrangement, seatGraph));
        if (arrangementChangeListener != null) {
            arrangementChangeListener.onArrangementChanged(currentArrangement);
        }
    }

    /**
     * Accumulates scroll-wheel rotation into a batched undo entry. On the
     * first tick, starts a 500ms timer. Each tick adds to the accumulated
     * total. When the timer fires, pushes ONE compound undo entry covering
     * the full rotation, so one Ctrl+Z reverts the entire scroll session.
     */
    private void accumulateRotationUndo(final double amount) {
        boolean starting = (rotationBatchTimer == null || !rotationBatchTimer.isRunning());
        if (starting) {
            // Snapshot pre-rotation grid positions so the revert path can
            // restore them exactly (snap-only revert loses bumped positions).
            rotationBatchStartDeskPos.clear();
            rotationBatchStartLmPos.clear();
            for (Desk d : selectedDesks) {
                rotationBatchStartDeskPos.put(d, new int[]{d.getGridX(), d.getGridY()});
            }
            if (selectedDesk != null && !rotationBatchStartDeskPos.containsKey(selectedDesk)) {
                rotationBatchStartDeskPos.put(selectedDesk,
                    new int[]{selectedDesk.getGridX(), selectedDesk.getGridY()});
            }
            for (Landmark lm : selectedLandmarks) {
                rotationBatchStartLmPos.put(lm, new int[]{lm.getGridX(), lm.getGridY()});
            }
            if (selectedLandmark != null && !rotationBatchStartLmPos.containsKey(selectedLandmark)) {
                rotationBatchStartLmPos.put(selectedLandmark,
                    new int[]{selectedLandmark.getGridX(), selectedLandmark.getGridY()});
            }
        }
        rotationBatchTotal += amount;
        if (!starting) {
            rotationBatchTimer.restart();
        } else {
            rotationBatchTimer = new javax.swing.Timer(500, new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    // If the user is still holding the mouse, defer commit.
                    // They get to finish positioning/rotating before we
                    // collision-check or push an undo entry.
                    if (isDragging || isDraggingLandmark || isMultiDragging) {
                        rotationBatchTimer.restart();
                        return;
                    }
                    rotationBatchTimer.stop();
                    commitRotationBatch();
                }
            });
            rotationBatchTimer.setRepeats(false);
            rotationBatchTimer.start();
        }
    }

    /** True iff the snapshot keys still match the live selection. */
    private boolean rotationBatchSelectionStillValid() {
        java.util.Set<Desk> currentDesks = new java.util.HashSet<Desk>(selectedDesks);
        if (selectedDesk != null) currentDesks.add(selectedDesk);
        java.util.Set<Landmark> currentLms = new java.util.HashSet<Landmark>(selectedLandmarks);
        if (selectedLandmark != null) currentLms.add(selectedLandmark);
        return currentDesks.equals(rotationBatchStartDeskPos.keySet())
            && currentLms.equals(rotationBatchStartLmPos.keySet());
    }

    /**
     * Commits the current rotation batch: snaps items into bounds, checks
     * collision, and either pushes an undo entry or reverts both the rotation
     * and any position shift back to the pre-batch state.
     */
    private void commitRotationBatch() {
        // Guard: if the user changed selection since the batch started, the
        // snapshot refers to items the user is no longer interacting with.
        // Discard silently — those items were already rotated live during
        // scroll, and we don't want to push an undo entry they didn't expect.
        if (!rotationBatchSelectionStillValid()) {
            rotationBatchStartDeskPos.clear();
            rotationBatchStartLmPos.clear();
            rotationBatchTotal = 0;
            return;
        }
        double total = rotationBatchTotal;
        rotationBatchTotal = 0;
        if (total == 0) {
            rotationBatchStartDeskPos.clear();
            rotationBatchStartLmPos.clear();
            return;
        }

        // Snap everything into bounds, then collision-check.
        for (Desk d : rotationBatchStartDeskPos.keySet()) snapDeskIntoBounds(d);
        for (Landmark lm : rotationBatchStartLmPos.keySet()) snapLandmarkIntoBounds(lm);

        boolean collision = false;
        for (Desk d : rotationBatchStartDeskPos.keySet()) {
            if (classroom.hasCollision(d, d)) { collision = true; break; }
        }
        if (!collision) {
            for (Landmark lm : rotationBatchStartLmPos.keySet()) {
                if (classroom.hasLandmarkCollision(lm, lm)) { collision = true; break; }
            }
        }

        if (collision) {
            // Full revert: undo rotation AND restore the original grid
            // position (the snap may have bumped items into neighbors).
            for (java.util.Map.Entry<Desk, int[]> e : rotationBatchStartDeskPos.entrySet()) {
                Desk d = e.getKey();
                d.rotate(-total);
                d.setPosition(e.getValue()[0], e.getValue()[1]);
            }
            for (java.util.Map.Entry<Landmark, int[]> e : rotationBatchStartLmPos.entrySet()) {
                Landmark lm = e.getKey();
                lm.rotate(-total);
                lm.setPosition(e.getValue()[0], e.getValue()[1]);
            }
            rotationBatchStartDeskPos.clear();
            rotationBatchStartLmPos.clear();
            refreshLiveScore();
            repaint();
            return;
        }

        // Clean commit: push ONE compound undo entry.
        java.util.List<Command> batch = new java.util.ArrayList<Command>();
        for (Desk d : rotationBatchStartDeskPos.keySet()) batch.add(new RotateDeskCommand(d, total));
        for (Landmark lm : rotationBatchStartLmPos.keySet()) batch.add(new RotateLandmarkCommand(lm, total));
        if (!batch.isEmpty()) {
            undoManager.pushExecuted(new seating.layout.CompoundCommand(
                batch, "Rotate " + (int) total + "\u00B0"));
        }
        rotationBatchStartDeskPos.clear();
        rotationBatchStartLmPos.clear();
        refreshLiveScore();
        repaint();
    }

    /** Rotates a desk by deltaDegrees if the result fits without collision; otherwise reverts. */
    private void tryRotateDesk(Desk d, double deltaDegrees) {
        d.rotate(deltaDegrees);
        snapDeskIntoBounds(d);
        if (classroom.hasCollision(d, d)) {
            d.rotate(-deltaDegrees);
            snapDeskIntoBounds(d);
            JOptionPane.showMessageDialog(ClassroomPanel.this,
                "Cannot rotate — would overlap another item.",
                "Rotate", JOptionPane.INFORMATION_MESSAGE);
        } else {
            undoManager.pushExecuted(new RotateDeskCommand(d, deltaDegrees));
            refreshLiveScore();
        }
        repaint();
    }

    private void snapDeskIntoBounds(Desk d) {
        int[] p = gridManager.clampToClassroom(d, d.getGridX(), d.getGridY());
        if (p[0] != d.getGridX() || p[1] != d.getGridY()) {
            d.setPosition(p[0], p[1]);
        }
    }

    /** Clamps a landmark's position so its rotated footprint stays in the grid. */
    private void snapLandmarkIntoBounds(Landmark lm) {
        int[] p = gridManager.clampLandmarkToClassroom(lm, lm.getGridX(), lm.getGridY());
        if (p[0] != lm.getGridX() || p[1] != lm.getGridY()) {
            lm.setPosition(p[0], p[1]);
        }
    }

    /** Snapshots the grid positions of all multi-selected items before a group drag. */
    private void saveMultiDragStartPositions() {
        multiDragStartPos.clear();
        multiDragLmStartPos.clear();
        for (Desk d : selectedDesks) {
            multiDragStartPos.put(d, new int[]{d.getGridX(), d.getGridY()});
        }
        for (Landmark lm : selectedLandmarks) {
            multiDragLmStartPos.put(lm, new int[]{lm.getGridX(), lm.getGridY()});
        }
    }

    /** Resets all selection and drag state. Call after Clear All, template apply, etc. */
    public void clearSelection() {
        selectedDesk = null;
        selectedLandmark = null;
        selectedDesks.clear();
        selectedLandmarks.clear();
        isDragging = false;
        isMultiDragging = false;
        isDraggingLandmark = false;
        isRectSelecting = false;
        draggedStudent = null;
        draggedStudentSourceSeat = null;
        zoneDragging = false;
        ghostDesk = null;
        ghostLandmark = null;
        pendingDuplicates = null;
        pendingDupOffsetsGx = null;
        pendingDupOffsetsGy = null;
        multiDragStartPos.clear();
        multiDragLmStartPos.clear();
        // Stop any active timers so they don't fire against a cleared selection.
        if (rotationBatchTimer != null) rotationBatchTimer.stop();
        rotationBatchTotal = 0;
        rotationBatchStartDeskPos.clear();
        rotationBatchStartLmPos.clear();
        if (animTimer != null) animTimer.stop();
        animatingDesk = null;
        setCursor(Cursor.getDefaultCursor());
    }
    public Classroom getClassroom() { return classroom; }
    public UndoManager getUndoManager() { return undoManager; }

    /** Converts screen pixel coordinates to model coordinates using inverse scale. */
    private int toModelX(int screenX) { return displayScale > 0 ? (int)(screenX / displayScale) : screenX; }
    private int toModelY(int screenY) { return displayScale > 0 ? (int)(screenY / displayScale) : screenY; }

    /**
     * Finds the seat closest to a model-space point, within a threshold.
     * Used by the manual student-drag feature to hit-test seats after a
     * seating arrangement has been generated. ONLY considers seats that
     * currently have a student assigned — this prevents empty seats (for
     * example on an unoccupied lab bench) from intercepting clicks that
     * should hit the desk itself. Iterates every occupied seat and returns
     * the nearest within 12 model-pixels of the point.
     *
     * @param modelX x in model coordinates
     * @param modelY y in model coordinates
     * @return the nearest occupied seat, or null if none is close enough
     */
    private Seat getSeatAt(int modelX, int modelY) {
        if (currentArrangement == null) return null;
        Seat best = null;
        double bestDist = 16.0; // threshold — matches seat dot visual radius
        for (Desk d : classroom.getDesks()) {
            for (Seat s : d.getSeats()) {
                if (currentArrangement.getStudentAt(s) == null) continue;
                java.awt.geom.Point2D p = s.getGlobalPosition();
                double dx = p.getX() - modelX;
                double dy = p.getY() - modelY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = s;
                }
            }
        }
        return best;
    }

    /** Same as getSeatAt but accepts empty seats too — for drag drop targets. */
    private Seat getAnySeatAt(int modelX, int modelY) {
        Seat best = null;
        double bestDist = 16.0;
        for (Desk d : classroom.getDesks()) {
            for (Seat s : d.getSeats()) {
                java.awt.geom.Point2D p = s.getGlobalPosition();
                double dx = p.getX() - modelX;
                double dy = p.getY() - modelY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = s;
                }
            }
        }
        return best;
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Disco mode — dark background
        if (discoMode) {
            setBackground(new Color(10, 10, 15));
            if (getParent() != null) getParent().setBackground(new Color(10, 10, 15));
            setOpaque(true);
        } else if (getBackground().getRed() < 50) {
            // Restore normal background when disco ends
            setBackground(GRID_BG);
            if (getParent() != null) getParent().setBackground(new Color(245, 245, 248));
        }

        // Compute uniform scale to fill panel while maintaining aspect ratio
        int pw = getWidth();
        int ph = getHeight();
        int mw = classroom.getPixelWidth();
        int mh = classroom.getPixelHeight();
        if (pw > 0 && ph > 0 && mw > 0 && mh > 0) {
            displayScale = Math.min((double)pw / mw, (double)ph / mh);
        }
        g.scale(displayScale, displayScale);

        int gs = classroom.getGridSize();

        if (!discoMode) {
            drawGrid(g, gs);
            drawZones(g, gs);
        } else {
            // Draw faint grid lines during disco
            g.setColor(new Color(40, 40, 50));
            g.setStroke(new BasicStroke(0.5f));
            for (int x = 0; x <= classroom.getGridColumns(); x++)
                g.drawLine(x * gs, 0, x * gs, classroom.getPixelHeight());
            for (int y = 0; y <= classroom.getGridRows(); y++)
                g.drawLine(0, y * gs, classroom.getPixelWidth(), y * gs);
        }
        // Always draw landmarks — disco-aware rendering inside drawLandmarks
        drawLandmarks(g, gs);
        drawDesks(g, gs);
        // Draw zone labels BEFORE student names so names can nudge around them
        // and remain readable on top of zone labels.
        if (!discoMode) {
            drawZoneLabels(g, gs);
        }
        drawStudentNames(g, gs);

        // Draw heat map overlay (green-yellow-red per seat).
        // In disco mode, pass desk pixel positions so heat dots ride along
        // with the bouncing desks instead of staying at the original grid spots.
        if (heatMapEnabled && currentArrangement != null && constraintSet != null && seatGraph != null) {
            if (discoMode && discoPx != null) {
                HeatMapOverlay.draw(g, currentArrangement, constraintSet, seatGraph,
                    classroom, discoPx, discoPy, discoRotAngle, gs);
            } else {
                HeatMapOverlay.draw(g, currentArrangement, constraintSet, seatGraph, classroom);
            }
        }

        // Draw conflict overlay (red lines between violating students)
        if (currentArrangement != null && constraintSet != null && seatGraph != null) {
            ConflictOverlay.draw(g, currentArrangement, constraintSet, seatGraph);
        }

        // Selection UI hidden during disco to prevent accidental edits
        if (!discoMode && ghostDesk != null) {
            drawGhostDesk(g, gs);
        }
        if (!discoMode && ghostLandmark != null) {
            drawGhostLandmark(g, gs);
        }
        if (!discoMode && pendingDuplicates != null) {
            AffineTransform savedAT = g.getTransform();
            Composite savedC = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            for (Desk d : pendingDuplicates) {
                AffineTransform sv = g.getTransform();
                g.transform(d.getTransform(gs));
                d.draw(g, gs);
                g.setTransform(sv);
            }
            g.setComposite(savedC);
            g.setTransform(savedAT);
        }
        if (!discoMode && selectedDesk != null && ghostDesk == null) {
            drawSelection(g, gs);
        }
        if (!discoMode && selectedLandmark != null && selectedDesk == null) {
            AffineTransform svLm = g.getTransform();
            g.transform(selectedLandmark.getTransform(gs));
            double lmW = selectedLandmark.getGridW() * gs;
            double lmH = selectedLandmark.getGridH() * gs;
            g.setColor(SELECTION_COLOR);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{8.0f, 4.0f}, 0.0f));
            g.draw(new Rectangle2D.Double(-2, -2, lmW + 4, lmH + 4));
            if (selectedLandmark.getRotation() != 0) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(new Color(41, 128, 185));
                g.drawString((int) selectedLandmark.getRotation() + "\u00B0", 0, -4);
            }
            g.setTransform(svLm);
        }

        // Draw multi-select rectangle and highlights (hidden during disco)
        if (!discoMode && isRectSelecting) {
            int rx = Math.min(rectSelStartX, rectSelEndX);
            int ry = Math.min(rectSelStartY, rectSelEndY);
            int rw = Math.abs(rectSelEndX - rectSelStartX);
            int rh = Math.abs(rectSelEndY - rectSelStartY);
            g.setColor(new Color(41, 128, 185, 30));
            g.fillRect(rx, ry, rw, rh);
            g.setColor(new Color(41, 128, 185, 140));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(rx, ry, rw, rh);
        }
        if (!discoMode && (!selectedDesks.isEmpty() || !selectedLandmarks.isEmpty())) {
            g.setColor(SELECTION_COLOR);
            g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{6.0f, 4.0f}, 0.0f));
            for (Desk d : selectedDesks) {
                AffineTransform svd = g.getTransform();
                g.transform(d.getTransform(gs));
                double dw = d.getWidthInCells() * gs;
                double dh = d.getHeightInCells() * gs;
                g.draw(new Rectangle2D.Double(-2, -2, dw + 4, dh + 4));
                g.setTransform(svd);
            }
            for (Landmark lm : selectedLandmarks) {
                java.awt.geom.Rectangle2D lb = lm.getBounds(gs);
                AffineTransform svl = g.getTransform();
                g.transform(lm.getTransform(gs));
                g.draw(new Rectangle2D.Double(-2, -2, lb.getWidth() + 4, lb.getHeight() + 4));
                g.setTransform(svl);
            }
        }

        // Draw floating student drag preview (on top of everything)
        if (draggedStudent != null) {
            // Highlight the source seat so the user sees where the student came from
            if (draggedStudentSourceSeat != null) {
                java.awt.geom.Point2D sp = draggedStudentSourceSeat.getGlobalPosition();
                g.setColor(new Color(180, 180, 180, 120));
                g.fillOval((int) sp.getX() - 7, (int) sp.getY() - 7, 14, 14);
                g.setColor(new Color(100, 100, 100));
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval((int) sp.getX() - 7, (int) sp.getY() - 7, 14, 14);
            }
            // Highlight the target seat under the cursor (occupied or empty)
            Seat targetSeat = getAnySeatAt(draggedStudentX, draggedStudentY);
            if (targetSeat != null && targetSeat != draggedStudentSourceSeat) {
                java.awt.geom.Point2D tp = targetSeat.getGlobalPosition();
                g.setColor(new Color(46, 204, 113, 100));
                g.fillOval((int) tp.getX() - 10, (int) tp.getY() - 10, 20, 20);
                g.setColor(new Color(39, 174, 96));
                g.setStroke(new BasicStroke(2.0f));
                g.drawOval((int) tp.getX() - 10, (int) tp.getY() - 10, 20, 20);
            }
            // Floating preview — blue seat with student name at cursor
            g.setColor(new Color(41, 128, 185));
            g.fillOval(draggedStudentX - 7, draggedStudentY - 7, 14, 14);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(draggedStudentX - 7, draggedStudentY - 7, 14, 14);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics dfm = g.getFontMetrics();
            String nm = draggedStudent.getName();
            int nw = dfm.stringWidth(nm);
            // Label above the cursor with a translucent background
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(draggedStudentX - nw / 2 - 4, draggedStudentY - 28, nw + 8, 16, 6, 6);
            g.setColor(Color.WHITE);
            g.drawString(nm, draggedStudentX - nw / 2, draggedStudentY - 16);
        }

        // Draw zone drag preview
        if (zoneDrawMode && zoneDragging) {
            int rx = Math.min(zoneDragStartX, zoneDragEndX);
            int ry = Math.min(zoneDragStartY, zoneDragEndY);
            int rw = Math.abs(zoneDragEndX - zoneDragStartX);
            int rh = Math.abs(zoneDragEndY - zoneDragStartY);
            g.setColor(new Color(pendingZoneColor.getRed(), pendingZoneColor.getGreen(),
                pendingZoneColor.getBlue(), 60));
            g.fillRect(rx, ry, rw, rh);
            g.setColor(new Color(pendingZoneColor.getRed(), pendingZoneColor.getGreen(),
                pendingZoneColor.getBlue(), 180));
            g.setStroke(new BasicStroke(2.0f));
            g.drawRect(rx, ry, rw, rh);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(pendingZoneLabel, rx + 4, ry + 15);
        }

        // Draw empty state hint — auto-wraps to fit narrow rooms
        if (classroom.getDesks().isEmpty() && classroom.getLandmarks().isEmpty()
                && ghostDesk == null && ghostLandmark == null && !zoneDrawMode) {
            g.setColor(new Color(160, 160, 170));
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            FontMetrics fmHint = g.getFontMetrics();
            int hintPw = classroom.getPixelWidth();
            int hintPh = classroom.getPixelHeight();
            String[] lines;
            String full = "Click a desk type in the palette, then click here to place it.";
            if (fmHint.stringWidth(full) + 40 > hintPw) {
                // Wrap onto two lines
                lines = new String[] {
                    "Click a desk type in the palette,",
                    "then click here to place it."
                };
            } else {
                lines = new String[] { full };
            }
            int lineH = fmHint.getHeight();
            int totalH = lineH * lines.length;
            int hy = hintPh / 2 - totalH / 2 + fmHint.getAscent();
            for (String ln : lines) {
                g.drawString(ln, (hintPw - fmHint.stringWidth(ln)) / 2, hy);
                hy += lineH;
            }
        }

        // Disco rays overlay
        if (discoMode) {
            int dmw = classroom.getPixelWidth();
            int dmh = classroom.getPixelHeight();
            int dcx = dmw / 2, dcy = dmh / 2;
            int rayCount = 10;
            double rayWidth = Math.PI / 14;
            Composite discoComp = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            int maxR = (int)(Math.sqrt(dmw * dmw + dmh * dmh));
            for (int r = 0; r < rayCount; r++) {
                float hue = (discoHue + r * 1.0f / rayCount) % 1.0f;
                g.setColor(Color.getHSBColor(hue, 0.95f, 1.0f));
                double ang = discoRayAngle + r * Math.PI * 2 / rayCount;
                g.fillArc(dcx - maxR, dcy - maxR, maxR * 2, maxR * 2,
                    (int) Math.toDegrees(-ang - rayWidth / 2),
                    (int) Math.toDegrees(rayWidth));
            }
            g.setComposite(discoComp);

            // Disco ball in center — always on top
            int ballRadius = Math.min(dmw, dmh) / 10;
            // Outer glow
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            g.setColor(Color.WHITE);
            g.fillOval(dcx - ballRadius * 2, dcy - ballRadius * 2, ballRadius * 4, ballRadius * 4);
            g.setComposite(discoComp);

            // Ball body — silver gradient
            java.awt.GradientPaint ballGrad = new java.awt.GradientPaint(
                dcx - ballRadius, dcy - ballRadius, new Color(220, 220, 230),
                dcx + ballRadius, dcy + ballRadius, new Color(120, 120, 140));
            g.setPaint(ballGrad);
            g.fillOval(dcx - ballRadius, dcy - ballRadius, ballRadius * 2, ballRadius * 2);

            // Mirror facets — grid of small reflective squares
            g.setClip(new java.awt.geom.Ellipse2D.Double(
                dcx - ballRadius, dcy - ballRadius, ballRadius * 2, ballRadius * 2));
            int facetSize = Math.max(4, ballRadius / 5);
            for (int fy = dcy - ballRadius; fy < dcy + ballRadius; fy += facetSize + 1) {
                for (int fx = dcx - ballRadius; fx < dcx + ballRadius; fx += facetSize + 1) {
                    // Each facet reflects a different color based on position + time
                    float fHue = (discoHue + (fx + fy) * 0.005f) % 1.0f;
                    float brightness = 0.5f + 0.5f * (float) Math.sin(
                        discoRayAngle * 3 + fx * 0.1 + fy * 0.07);
                    g.setColor(Color.getHSBColor(fHue, 0.3f, brightness));
                    g.fillRect(fx, fy, facetSize, facetSize);
                }
            }
            g.setClip(null);

            // Ball outline
            g.setColor(new Color(80, 80, 90));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(dcx - ballRadius, dcy - ballRadius, ballRadius * 2, ballRadius * 2);

            // Hanging line from top
            g.setColor(new Color(150, 150, 160));
            g.setStroke(new BasicStroke(1.0f));
            g.drawLine(dcx, 0, dcx, dcy - ballRadius);

            // Specular highlight
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
            g.setColor(Color.WHITE);
            int hlSize = ballRadius / 3;
            g.fillOval(dcx - ballRadius / 3, dcy - ballRadius / 3, hlSize, hlSize);
            g.setComposite(discoComp);
        }

        // FRONT label — always on top (skip during disco)
        if (!discoMode) {
            drawFrontLabel(g);
        }
    }

    /** Draws the "FRONT OF CLASSROOM" pill label centered at the top of the canvas. */
    /** Font scale factor so labels shrink proportionally on small rooms. */
    private double getFontScale() {
        return Math.min(1.0, classroom.getPixelHeight() / 400.0);
    }

    private void drawFrontLabel(Graphics2D g) {
        int fontSize = Math.max(7, (int)(13 * getFontScale()));
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fmFront = g.getFontMetrics();
        String frontLabel = "FRONT OF CLASSROOM";
        int labelW = fmFront.stringWidth(frontLabel);
        int labelX = (classroom.getPixelWidth() - labelW) / 2;
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRoundRect(labelX - 8, 2, labelW + 16, fmFront.getHeight() + 4, 6, 6);
        g.setColor(new Color(120, 120, 130));
        g.drawString(frontLabel, labelX, 4 + fmFront.getAscent());
    }

    private void drawGrid(Graphics2D g, int gs) {
        g.setColor(GRID_COLOR);
        g.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x <= classroom.getGridColumns(); x++) {
            g.drawLine(x * gs, 0, x * gs, classroom.getPixelHeight());
        }
        for (int y = 0; y <= classroom.getGridRows(); y++) {
            g.drawLine(0, y * gs, classroom.getPixelWidth(), y * gs);
        }
    }

    private void drawLandmarks(Graphics2D g, int gs) {
        java.util.List<Landmark> lms = classroom.getLandmarks();
        for (int i = 0; i < lms.size(); i++) {
            Landmark lm = lms.get(i);
            java.awt.geom.AffineTransform saved = g.getTransform();
            if (discoMode && discoLmPx != null && i < discoLmPx.length) {
                // Smooth pixel-based rendering during disco
                double w = lm.getGridW() * gs;
                double h = lm.getGridH() * gs;
                double cx = discoLmPx[i] + w / 2.0;
                double cy = discoLmPy[i] + h / 2.0;
                g.translate(cx, cy);
                g.rotate(Math.toRadians(discoLmRotAngle[i]));
                g.translate(-w / 2.0, -h / 2.0);
            } else {
                g.transform(lm.getTransform(gs));
            }
            lm.draw(g, gs);
            g.setTransform(saved);
        }
    }

    private void drawZones(Graphics2D g, int gs) {
        for (Zone zone : classroom.getZones()) {
            Color c = zone.getColor();
            AffineTransform saved = g.getTransform();
            if (zone.getRotation() != 0) {
                g.transform(zone.getTransform(gs));
                // Draw at local (0,0) when rotated
                double w = zone.getGridWidth() * gs;
                double h = zone.getGridHeight() * gs;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                g.fill(new Rectangle2D.Double(0, 0, w, h));
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[]{6.0f, 4.0f}, 0.0f));
                g.draw(new Rectangle2D.Double(0, 0, w, h));
            } else {
                Rectangle2D bounds = zone.getBounds(gs);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                g.fill(bounds);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[]{6.0f, 4.0f}, 0.0f));
                g.draw(bounds);
            }
            g.setTransform(saved);
        }
    }

    /** Draws zone labels on top of everything with background pills. Truncates long labels and nudges to avoid overlap. */
    private void drawZoneLabels(Graphics2D g, int gs) {
        int zoneFontSize = Math.max(6, (int)(11 * getFontScale()));
        g.setFont(new Font("SansSerif", Font.BOLD, zoneFontSize));
        FontMetrics fm = g.getFontMetrics();
        // Reset shared list so drawStudentNames can read the current frame's rects
        lastZoneLabelRects.clear();
        java.util.ArrayList<Rectangle2D> usedRects = lastZoneLabelRects;

        for (Zone zone : classroom.getZones()) {
            Rectangle2D bounds = zone.getBounds(gs);
            Color c = zone.getColor();
            String fullLabel = zone.getLabel();
            String label = fullLabel;

            // Decide orientation: rotate 90° CW (top-to-bottom) for tall-skinny zones.
            boolean rotate = bounds.getHeight() > bounds.getWidth() * 1.3;

            // Truncate based on the axis the label runs along
            int maxLabelPx = (int) Math.max(20,
                (rotate ? bounds.getHeight() : bounds.getWidth()) - 10);
            while (fm.stringWidth(label) > maxLabelPx && label.length() > 3) {
                label = label.substring(0, label.length() - 1);
            }
            if (label.length() < fullLabel.length()) label += "\u2026"; // ellipsis

            int tw = fm.stringWidth(label);
            int th = fm.getHeight() + 2;

            if (rotate) {
                // Rotated pill: width=th (across the zone), height=tw+6 (down the zone)
                int ax = (int)(bounds.getX() + 3);
                int ay = (int)(bounds.getY() + 3);
                Rectangle2D labelRect = new Rectangle2D.Double(ax - 2, ay, th, tw + 6);
                // Nudge RIGHT to avoid overlapping already-drawn labels
                for (int attempt = 0; attempt < 5; attempt++) {
                    boolean overlaps = false;
                    for (Rectangle2D used : usedRects) {
                        if (labelRect.intersects(used)) { overlaps = true; break; }
                    }
                    if (!overlaps) break;
                    ax += th + 2;
                    labelRect = new Rectangle2D.Double(ax - 2, ay, th, tw + 6);
                }
                usedRects.add(labelRect);

                // Draw rotated: translate to top-right of pill, rotate CW, then draw
                // in local coords where x runs along tw and y runs across th.
                AffineTransform saved = g.getTransform();
                g.translate(ax + th, ay);
                g.rotate(Math.PI / 2);
                g.setColor(new Color(255, 255, 255, 200));
                g.fillRoundRect(-2, -th + 2, tw + 6, th, 4, 4);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 220));
                g.drawString(label, 1, -th + 2 + fm.getAscent() + 1);
                g.setTransform(saved);
            } else {
                int lx = (int)(bounds.getX() + 4);
                int ly = (int)(bounds.getY() + 3);

                // Nudge DOWN to avoid overlapping already-drawn labels
                Rectangle2D labelRect = new Rectangle2D.Double(lx - 2, ly, tw + 6, th);
                for (int attempt = 0; attempt < 5; attempt++) {
                    boolean overlaps = false;
                    for (Rectangle2D used : usedRects) {
                        if (labelRect.intersects(used)) { overlaps = true; break; }
                    }
                    if (!overlaps) break;
                    ly += th + 2;
                    labelRect = new Rectangle2D.Double(lx - 2, ly, tw + 6, th);
                }
                usedRects.add(labelRect);

                g.setColor(new Color(255, 255, 255, 200));
                g.fillRoundRect(lx - 2, ly, tw + 6, th, 4, 4);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 220));
                g.drawString(label, lx + 1, ly + fm.getAscent() + 1);
            }
        }
    }

    private void drawDesks(Graphics2D g, int gs) {
        java.util.List<Desk> desks = classroom.getDesks();
        for (int i = 0; i < desks.size(); i++) {
            Desk desk = desks.get(i);
            AffineTransform saved = g.getTransform();

            if (discoMode && discoPx != null && i < discoPx.length) {
                // Smooth pixel-based rendering during disco
                double w = desk.getWidthInCells() * gs;
                double h = desk.getHeightInCells() * gs;
                double cx = discoPx[i] + w / 2.0;
                double cy = discoPy[i] + h / 2.0;
                g.translate(cx, cy);
                g.rotate(Math.toRadians(discoRotAngle[i]));
                g.translate(-w / 2.0, -h / 2.0);
            } else {
                g.transform(desk.getTransform(gs));
            }

            desk.draw(g, gs);
            g.setTransform(saved);
        }
    }

    private void drawStudentNames(Graphics2D g, int gs) {
        if (currentArrangement == null) return;

        // Track label positions to avoid overlap. Seed with zone label rects
        // from the current paint pass so student names nudge AROUND zone labels
        // instead of being drawn underneath them.
        java.util.ArrayList<java.awt.geom.Rectangle2D> usedRects =
            new java.util.ArrayList<java.awt.geom.Rectangle2D>(lastZoneLabelRects);

        java.util.List<Desk> allDesks = classroom.getDesks();
        for (Seat seat : classroom.getAllSeats()) {
            Student student = currentArrangement.getStudentAt(seat);
            if (student == null) continue;

            Desk desk = seat.getParentDesk();
            if (desk == null) continue; // desk removed but arrangement still holds stale seats

            java.awt.geom.Point2D pos = seat.getGlobalPosition();

            // During disco, transform seat position to match desk's disco position + rotation
            if (discoMode && discoPx != null && desk != null) {
                int dIdx = allDesks.indexOf(desk);
                if (dIdx >= 0 && dIdx < discoPx.length) {
                    // Get seat's local position relative to desk origin
                    double localX = seat.getLocalX();
                    double localY = seat.getLocalY();
                    double dw = desk.getWidthInCells() * gs;
                    double dh = desk.getHeightInCells() * gs;
                    // Rotate local position around desk center
                    double cx = dw / 2.0, cy = dh / 2.0;
                    double angle = Math.toRadians(discoRotAngle[dIdx]);
                    double rx = cx + (localX - cx) * Math.cos(angle) - (localY - cy) * Math.sin(angle);
                    double ry = cy + (localX - cx) * Math.sin(angle) + (localY - cy) * Math.cos(angle);
                    // Translate to disco pixel position
                    pos = new java.awt.geom.Point2D.Double(
                        discoPx[dIdx] + rx, discoPy[dIdx] + ry);
                }
            }

            int seatSize = gs * 2 / 3;

            // Fill seat dot blue (occupied)
            g.setColor(new Color(70, 130, 180));
            g.fill(new java.awt.geom.Ellipse2D.Double(
                pos.getX() - seatSize / 2.0, pos.getY() - seatSize / 2.0,
                seatSize, seatSize));
            g.setColor(new Color(40, 80, 120));
            g.setStroke(new BasicStroke(1.0f));
            g.draw(new java.awt.geom.Ellipse2D.Double(
                pos.getX() - seatSize / 2.0, pos.getY() - seatSize / 2.0,
                seatSize, seatSize));

            // Smart name formatting
            String name = smartTruncate(student.getName(), desk);
            int seatCount = (desk != null) ? desk.getSeatCount() : 1;
            int baseSize = seatCount <= 2 ? 10 : (seatCount <= 4 ? 9 : 8);
            int fontSize = Math.max(5, (int)(baseSize * getFontScale()));
            g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(name);
            int th = fm.getHeight() + 2;

            // Find non-overlapping position for label
            double labelX = pos.getX() - tw / 2.0 - 3;
            double labelY = pos.getY() + seatSize / 2.0 + 2;
            java.awt.geom.Rectangle2D labelRect =
                new java.awt.geom.Rectangle2D.Double(labelX, labelY, tw + 6, th);

            // Nudge down if overlapping another label (or a zone label)
            for (int attempt = 0; attempt < 5; attempt++) {
                boolean overlaps = false;
                for (java.awt.geom.Rectangle2D used : usedRects) {
                    if (labelRect.intersects(used)) { overlaps = true; break; }
                }
                if (!overlaps) break;
                labelY += th + 1;
                labelRect = new java.awt.geom.Rectangle2D.Double(labelX, labelY, tw + 6, th);
            }

            // Clamp inside canvas bounds: if the label would fall off the bottom,
            // flip it ABOVE the seat dot instead (back-row students).
            double maxY = classroom.getPixelHeight() - th - 2;
            if (labelY > maxY) {
                labelY = pos.getY() - seatSize / 2.0 - th - 2;
            }
            // Clamp X to canvas width
            double maxX = classroom.getPixelWidth() - tw - 8;
            if (labelX > maxX) labelX = maxX;
            if (labelX < 2) labelX = 2;
            labelRect = new java.awt.geom.Rectangle2D.Double(labelX, labelY, tw + 6, th);

            usedRects.add(labelRect);

            // Background pill
            g.setColor(new Color(255, 255, 255, 220));
            g.fillRoundRect((int) labelX, (int) labelY, tw + 6, th, 5, 5);

            // Text
            g.setColor(new Color(25, 25, 30));
            g.drawString(name, (float)(labelX + 3), (float)(labelY + fm.getAscent()));
        }
    }

    /**
     * Smart name truncation: tries "First L." format before hard-cutting.
     * For single desks, uses just first name. For larger desks, uses full name.
     */
    private String smartTruncate(String fullName, Desk desk) {
        int seatCount = (desk != null) ? desk.getSeatCount() : 1;
        int maxLen;
        if (seatCount <= 2) maxLen = 10;
        else if (seatCount <= 4) maxLen = 9;
        else maxLen = 7; // circle tables, etc

        if (fullName.length() <= maxLen) return fullName;

        // Try "First L." format
        String[] parts = fullName.split(" ");
        if (parts.length >= 2) {
            String abbreviated = parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
            if (abbreviated.length() <= maxLen) return abbreviated;
            if (parts[0].length() <= maxLen) return parts[0];
        }
        return fullName.substring(0, maxLen - 1) + ".";
    }

    private void drawGhostDesk(Graphics2D g, int gs) {
        AffineTransform saved = g.getTransform();
        Composite savedComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g.transform(ghostDesk.getTransform(gs));
        ghostDesk.draw(g, gs);
        g.setTransform(saved);
        g.setComposite(savedComposite);
    }

    private void drawGhostLandmark(Graphics2D g, int gs) {
        AffineTransform saved = g.getTransform();
        Composite savedComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g.transform(ghostLandmark.getTransform(gs));
        ghostLandmark.draw(g, gs);
        g.setTransform(saved);
        g.setComposite(savedComposite);
    }

    private void drawSelection(Graphics2D g, int gs) {
        // Draw selection outline using the desk's transform so it matches rotation
        AffineTransform saved = g.getTransform();
        g.transform(selectedDesk.getTransform(gs));

        double w = selectedDesk.getWidthInCells() * gs;
        double h = selectedDesk.getHeightInCells() * gs;
        g.setColor(SELECTION_COLOR);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10.0f, new float[]{8.0f, 4.0f}, 0.0f));
        g.draw(new Rectangle2D.Double(-2, -2, w + 4, h + 4));

        if (selectedDesk.getRotation() != 0) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(41, 128, 185));
            g.drawString((int) selectedDesk.getRotation() + "\u00B0", 0, -4);
        }

        g.setTransform(saved);
    }
}
