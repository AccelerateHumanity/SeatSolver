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
    private double score;

    /** Creates an empty arrangement. */
    public SeatingArrangement() {
        this.assignment = new HashMap<Seat, Student>();
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
        this.score = score;
    }

    /**
     * Assigns a student to a seat.
     *
     * @param seat the seat
     * @param student the student to assign
     */
    public void assign(Seat seat, Student student) {
        assignment.put(seat, student);
    }

    /**
     * Removes the student from a seat.
     *
     * @param seat the seat to clear
     */
    public void unassign(Seat seat) {
        assignment.remove(seat);
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
     * Returns the seat assigned to a student, or null.
     *
     * @param student the student to look up
     * @return the seat, or null if unassigned
     */
    public Seat getSeatOf(Student student) {
        for (Map.Entry<Seat, Student> entry : assignment.entrySet()) {
            if (entry.getValue().equals(student)) {
                return entry.getKey();
            }
        }
        return null;
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
