package seating.constraint;

import seating.model.SeatingArrangement;
import seating.solver.SeatGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of all active constraints. Computes aggregate satisfaction
 * scores and checks whether all hard constraints are met.
 */
public class ConstraintSet {

    private List<Constraint> constraints;

    /** Creates an empty constraint set. */
    public ConstraintSet() {
        constraints = new ArrayList<Constraint>();
    }

    public void add(Constraint c) { constraints.add(c); }
    public void remove(Constraint c) { constraints.remove(c); }
    public void clear() { constraints.clear(); }
    public List<Constraint> getAll() { return constraints; }
    public int size() { return constraints.size(); }

    /**
     * Computes the weighted average score across all constraints.
     *
     * @param arrangement the seating to evaluate
     * @param graph the adjacency graph
     * @return aggregate score from 0.0 to 1.0
     */
    /**
     * Computes the overall score. Hard constraints are pass/fail (1.0 if all
     * satisfied, 0.0 if any violated). Soft constraints are weighted average.
     * Final score = hardPass * softScore. This means 100% is achievable when
     * all hard constraints pass and soft constraints are optimized.
     */
    public double evaluate(SeatingArrangement arrangement, SeatGraph graph) {
        if (constraints.isEmpty()) return 1.0;

        // Hard constraints: binary pass/fail
        boolean allHardPass = true;
        for (Constraint c : constraints) {
            if (c.isHard() && !c.isSatisfied(arrangement, graph)) {
                allHardPass = false;
                break;
            }
        }

        // Soft constraints: weighted average
        double softWeight = 0;
        double softScore = 0;
        for (Constraint c : constraints) {
            if (!c.isHard()) {
                double w = c.getWeight();
                softScore += c.evaluate(arrangement, graph) * w;
                softWeight += w;
            }
        }
        double softAvg = softWeight > 0 ? softScore / softWeight : 1.0;

        // If any hard constraint fails, cap score at 40%
        // Otherwise score is purely based on soft constraint optimization
        return allHardPass ? softAvg : Math.min(0.4, softAvg * 0.4);
    }

    /**
     * Checks whether ALL hard constraints are satisfied.
     *
     * @param arrangement the seating to check
     * @param graph the adjacency graph
     * @return true if every hard constraint passes
     */
    public boolean allHardSatisfied(SeatingArrangement arrangement, SeatGraph graph) {
        for (Constraint c : constraints) {
            if (c.isHard() && !c.isSatisfied(arrangement, graph)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a list of constraints that are violated.
     *
     * @param arrangement the seating to check
     * @param graph the adjacency graph
     * @return list of violated constraints
     */
    public List<Constraint> getViolations(SeatingArrangement arrangement, SeatGraph graph) {
        List<Constraint> violations = new ArrayList<Constraint>();
        for (Constraint c : constraints) {
            if (!c.isSatisfied(arrangement, graph)) {
                violations.add(c);
            }
        }
        return violations;
    }
}
