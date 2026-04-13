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

    public double evaluate(SeatingArrangement arrangement, SeatGraph graph) {
        Seat seatA = arrangement.getSeatOf(studentA);
        Seat seatB = arrangement.getSeatOf(studentB);
        if (seatA == null || seatB == null) return 1.0; // not assigned yet

        boolean adjacent = graph.areAdjacent(seatA, seatB);

        if (APART.equals(mode)) {
            return adjacent ? 0.0 : 1.0;
        } else { // TOGETHER
            if (adjacent) return 1.0;
            // Distance-based gradient: closer seats score higher
            java.awt.geom.Point2D posA = seatA.getGlobalPosition();
            java.awt.geom.Point2D posB = seatB.getGlobalPosition();
            double dist = posA.distance(posB);
            // Score drops linearly from 0.9 (very close) to 0.1 (far apart)
            double maxDist = 400.0;
            double score = Math.max(0.1, 0.9 * (1.0 - dist / maxDist));
            return score;
        }
    }

    public boolean isSatisfied(SeatingArrangement arrangement, SeatGraph graph) {
        if (APART.equals(mode)) {
            return evaluate(arrangement, graph) >= 1.0;
        }
        // "Together" is satisfied if adjacent OR very close
        return evaluate(arrangement, graph) >= 0.7;
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
