package seating.constraint;

import seating.model.*;
import seating.solver.SeatGraph;

import java.awt.geom.Point2D;

/**
 * Constraint that requires a student to sit within (or outside of)
 * a specific classroom zone. Used for vision needs, ADHD accommodations, etc.
 */
public class ZoneConstraint implements Constraint {

    /** Mode: student MUST be in the zone. */
    public static final String MUST_BE_IN = "must_be_in";
    /** Mode: student must NOT be in the zone. */
    public static final String MUST_NOT_BE_IN = "must_not_be_in";

    private Student student;
    private Zone zone;
    private String mode;
    private boolean hard;
    private double weight;

    /**
     * Creates a zone constraint.
     *
     * @param student the student this applies to
     * @param zone the target zone
     * @param mode "must_be_in" or "must_not_be_in"
     * @param hard true if this must be satisfied
     * @param weight relative importance
     */
    public ZoneConstraint(Student student, Zone zone, String mode,
                           boolean hard, double weight) {
        this.student = student;
        this.zone = zone;
        this.mode = mode;
        this.hard = hard;
        this.weight = weight;
    }

    public double evaluate(SeatingArrangement arrangement, SeatGraph graph) {
        Seat seat = arrangement.getSeatOf(student);
        if (seat == null) return 1.0; // not assigned yet

        Point2D pos = seat.getGlobalPosition();
        // Use default grid size of 40 for zone bounds check
        // Use gridSize from the seat's parent desk (synced from Classroom)
        int gs = (seat.getParentDesk() != null) ? seat.getParentDesk().getGridSize() : 20;
        boolean inZone = zone.contains(pos.getX(), pos.getY(), gs);

        if (MUST_BE_IN.equals(mode)) {
            return inZone ? 1.0 : 0.0;
        } else { // MUST_NOT_BE_IN
            return inZone ? 0.0 : 1.0;
        }
    }

    public boolean isSatisfied(SeatingArrangement arrangement, SeatGraph graph) {
        return evaluate(arrangement, graph) >= 1.0;
    }

    public boolean isHard() { return hard; }
    public double getWeight() { return weight; }
    public String getType() { return "zone"; }

    public String describe() {
        String action = MUST_BE_IN.equals(mode) ? "must sit in" : "must NOT sit in";
        String strength = hard ? "[REQUIRED] " : "";
        return strength + student.getName() + " " + action + " \"" + zone.getLabel() + "\" zone";
    }

    public boolean involvesStudent(Student s) {
        return student.equals(s);
    }

    public Student getStudent() { return student; }
    public Zone getZone() { return zone; }
    public String getMode() { return mode; }
}
