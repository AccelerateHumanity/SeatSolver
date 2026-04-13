package seating.constraint;

import seating.model.SeatingArrangement;
import seating.model.Student;
import seating.solver.SeatGraph;

/**
 * Interface for all seating constraints. Each constraint can evaluate
 * a seating arrangement and produce a satisfaction score.
 *
 * <p>Constraints can be "hard" (must be satisfied or the arrangement
 * is invalid) or "soft" (contribute to the overall score but are
 * not mandatory).</p>
 *
 * <p>This interface is implemented by ProximityConstraint, ZoneConstraint,
 * and BalanceConstraint — demonstrating interface-based inheritance.</p>
 */
public interface Constraint {

    /**
     * Evaluates how well this constraint is satisfied.
     *
     * @param arrangement the current seating assignment
     * @param graph the seat adjacency graph
     * @return score from 0.0 (fully violated) to 1.0 (fully satisfied)
     */
    double evaluate(SeatingArrangement arrangement, SeatGraph graph);

    /**
     * Returns whether this constraint is satisfied (for hard constraints).
     *
     * @param arrangement the current seating assignment
     * @param graph the seat adjacency graph
     * @return true if the constraint is met
     */
    boolean isSatisfied(SeatingArrangement arrangement, SeatGraph graph);

    /** Returns true if this is a hard constraint (must be satisfied). */
    boolean isHard();

    /** Returns the relative weight of this constraint (default 1.0). */
    double getWeight();

    /** Returns a human-readable description for the UI. */
    String describe();

    /** Returns the constraint type name for serialization. */
    String getType();

    /** Returns true if this constraint directly involves the given student. */
    boolean involvesStudent(Student student);
}
