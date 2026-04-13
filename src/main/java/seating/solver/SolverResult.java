package seating.solver;

import seating.model.SeatingArrangement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Holds the results from the CSP solver: a list of seating arrangements
 * ranked by constraint satisfaction score (best first).
 */
public class SolverResult {

    private List<SeatingArrangement> arrangements;
    private long solveTimeMs;

    /** Creates an empty result. */
    public SolverResult() {
        arrangements = new ArrayList<SeatingArrangement>();
        solveTimeMs = 0;
    }

    /** Adds an arrangement and keeps the list sorted by descending score. */
    public void add(SeatingArrangement arrangement) {
        arrangements.add(arrangement);
        Collections.sort(arrangements, new Comparator<SeatingArrangement>() {
            public int compare(SeatingArrangement a, SeatingArrangement b) {
                return Double.compare(b.getScore(), a.getScore()); // descending
            }
        });
    }

    /** Returns the best-scoring arrangement, or null if empty. */
    public SeatingArrangement getBest() {
        return arrangements.isEmpty() ? null : arrangements.get(0);
    }

    /** Returns arrangement at the given index. */
    public SeatingArrangement get(int index) { return arrangements.get(index); }

    /** Returns the number of arrangements found. */
    public int size() { return arrangements.size(); }

    /** Returns true if no arrangements were found. */
    public boolean isEmpty() { return arrangements.isEmpty(); }

    /** Returns all arrangements (best first). */
    public List<SeatingArrangement> getAll() { return arrangements; }

    public long getSolveTimeMs() { return solveTimeMs; }
    public void setSolveTimeMs(long ms) { this.solveTimeMs = ms; }
}
