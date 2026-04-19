package seating.constraint;

import seating.model.Seat;
import seating.model.SeatingArrangement;
import seating.model.Student;
import seating.solver.SeatGraph;

import java.util.Set;

/**
 * Constraint that enforces proximity rules between two students.
 * Can require students to be kept APART (not adjacent) or kept
 * TOGETHER (adjacent to each other).
 */
public class ProximityConstraint implements Constraint {

    /** Mode indicating students should be kept apart. */
    public static final String APART = "apart";
    /** Mode indicating students should be kept together. */
    public static final String TOGETHER = "together";
    /** Pixel distance beyond which closeness saturates to 0.0. Calibrated to
     *  roughly the diagonal of a default classroom so "far apart" across the
     *  whole room scores the extreme. */
    public static final double MAX_PROXIMITY_DISTANCE = 400.0;

    private Student studentA;
    private Student studentB;
    private String mode; // "apart" or "together"
    private boolean hard;
    private double weight;

    /**
     * Creates a proximity constraint.
     *
     * @param studentA first student
     * @param studentB second student
     * @param mode "apart" or "together"
     * @param hard true if this must be satisfied
     * @param weight relative importance (default 1.0)
     */
    public ProximityConstraint(Student studentA, Student studentB,
                                String mode, boolean hard, double weight) {
        this.studentA = studentA;
        this.studentB = studentB;
        this.mode = mode;
        this.hard = hard;
        this.weight = weight;
    }

    /**
     * Scores how well this proximity rule is satisfied by the current seating.
     * <p>
     * Both modes use a shared "closeness" gradient derived from the pixel
     * distance between the two students' seats. Closeness ranges from 1.0
     * (seats touching / same desk) down to 0.0 at {@link #MAX_PROXIMITY_DISTANCE}
     * and further. Graph-adjacent seats (same desk) pin closeness to 1.0
     * regardless of computed pixel distance, so "same desk" always scores
     * the extreme on both sides.
     * <p>
     * Return values:
     * <ul>
     *   <li>{@link #TOGETHER}: score = closeness (high when close)
     *   <li>{@link #APART}: score = 1 - closeness (high when far apart)
     * </ul>
     * Returns 1.0 when either student is unassigned (no penalty for work-in-progress).
     */
    public double evaluate(SeatingArrangement arrangement, SeatGraph graph) {
        Seat seatA = arrangement.getSeatOf(studentA);
        Seat seatB = arrangement.getSeatOf(studentB);
        if (seatA == null || seatB == null) return 1.0; // not assigned yet

        java.awt.geom.Point2D posA = seatA.getGlobalPosition();
        java.awt.geom.Point2D posB = seatB.getGlobalPosition();
        double dist = posA.distance(posB);
        double closeness = Math.max(0.0, Math.min(1.0, 1.0 - dist / MAX_PROXIMITY_DISTANCE));
        if (graph.areAdjacent(seatA, seatB)) closeness = 1.0;

        if (APART.equals(mode)) {
            return 1.0 - closeness; // high when far apart
        }
        return closeness; // TOGETHER: high when close
    }

    public boolean isSatisfied(SeatingArrangement arrangement, SeatGraph graph) {
        // Satisfaction is still based on graph adjacency (same-desk / very
        // close) so hard constraints keep their original semantics — the
        // solver and UI "✓/✗" match. The continuous distance score from
        // evaluate() is for display weighting only.
        Seat seatA = arrangement.getSeatOf(studentA);
        Seat seatB = arrangement.getSeatOf(studentB);
        if (seatA == null || seatB == null) return true;
        boolean adjacent = graph.areAdjacent(seatA, seatB);
        return APART.equals(mode) ? !adjacent : adjacent;
    }

    public boolean isHard() { return hard; }
    public double getWeight() { return weight; }
    public String getType() { return "proximity"; }

    public String describe() {
        String action = APART.equals(mode) ? "must NOT sit next to" : "should sit next to";
        String strength = hard ? "[REQUIRED] " : "";
        return strength + studentA.getName() + " " + action + " " + studentB.getName();
    }

    public boolean involvesStudent(Student student) {
        return studentA.equals(student) || studentB.equals(student);
    }

    public Student getStudentA() { return studentA; }
    public Student getStudentB() { return studentB; }
    public String getMode() { return mode; }
}
