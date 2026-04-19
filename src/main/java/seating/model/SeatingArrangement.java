package seating.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a complete seat-to-student assignment for a classroom.
 * Uses a HashMap to map each Seat to its assigned Student.
 * Includes a score indicating how well constraints are satisfied.
 */
public class SeatingArrangement {

    private HashMap<Seat, Student> assignment;
    // Reverse index: student -> seat. Kept in sync with `assignment` so
    // getSeatOf is O(1) instead of O(n). Critical for solver inner loops
    // and live-score recalculation after every interaction.
    private HashMap<Student, Seat> reverseAssignment;
    private double score;

    /** Creates an empty arrangement. */
    public SeatingArrangement() {
        this.assignment = new HashMap<Seat, Student>();
        this.reverseAssignment = new HashMap<Student, Seat>();
        this.score = 0.0;
    }

    /**
     * Creates an arrangement from an existing mapping.
     *
     * @param assignment the seat-to-student mapping
     * @param score the constraint satisfaction score (0.0 to 1.0)
     */
    public SeatingArrangement(HashMap<Seat, Student> assignment, double score) {
        this.assignment = new HashMap<Seat, Student>(assignment);
        this.reverseAssignment = new HashMap<Student, Seat>();
        for (Map.Entry<Seat, Student> e : assignment.entrySet()) {
            reverseAssignment.put(e.getValue(), e.getKey());
        }
        this.score = score;
    }

    /**
     * Assigns a student to a seat.
     *
     * @param seat the seat
     * @param student the student to assign
     */
    public void assign(Seat seat, Student student) {
        // If this student was sitting elsewhere, remove that mapping first.
        Seat previous = reverseAssignment.get(student);
        if (previous != null) {
            assignment.remove(previous);
        }
        // If the seat was occupied by someone else, evict them from the reverse map.
        Student displaced = assignment.get(seat);
        if (displaced != null) {
            reverseAssignment.remove(displaced);
        }
        assignment.put(seat, student);
        reverseAssignment.put(student, seat);
    }

    /**
     * Removes the student from a seat.
     *
     * @param seat the seat to clear
     */
    public void unassign(Seat seat) {
        Student s = assignment.remove(seat);
        if (s != null) reverseAssignment.remove(s);
    }

    /**
     * Returns the student assigned to a seat, or null.
     *
     * @param seat the seat to look up
     * @return the student, or null if unoccupied
     */
    public Student getStudentAt(Seat seat) {
        return assignment.get(seat);
    }

    /**
     * Returns the seat assigned to a student, or null. O(1) via reverse index.
     *
     * @param student the student to look up
     * @return the seat, or null if unassigned
     */
    public Seat getSeatOf(Student student) {
        return reverseAssignment.get(student);
    }

    /** Returns a copy of the full assignment map. */
    public HashMap<Seat, Student> getAssignmentMap() {
        return new HashMap<Seat, Student>(assignment);
    }

    public int getAssignedCount() { return assignment.size(); }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String toString() {
        return "Arrangement[" + assignment.size() + " assigned, score=" + String.format("%.2f", score) + "]";
    }
}
