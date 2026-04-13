package seating.solver;

import seating.constraint.Constraint;
import seating.constraint.ConstraintSet;
import seating.model.*;

import java.util.*;

/**
 * Constraint Satisfaction Problem (CSP) solver for seating arrangements.
 * Uses backtracking search with the MRV (Minimum Remaining Values) heuristic,
 * forward checking, and score-guided domain ordering to efficiently assign
 * students to seats while maximizing constraint satisfaction.
 *
 * <p>Key techniques:</p>
 * <ul>
 *   <li><b>MRV Heuristic</b>: PriorityQueue picks the most constrained student first</li>
 *   <li><b>Forward Checking</b>: Prunes impossible seats from domains after each assignment</li>
 *   <li><b>Score-Guided Ordering</b>: Tries best-scoring seats first for greedy optimization</li>
 *   <li><b>Multi-Pass</b>: Greedy pass first, then randomized passes for variety</li>
 * </ul>
 */
public class CSPSolver {

    private ConstraintSet constraintSet;
    private SeatGraph graph;
    private int maxSolutions;
    private long timeoutMs;
    private long startTime;
    private int returnCount;

    /**
     * Creates a new CSP solver.
     *
     * @param constraintSet the constraints to satisfy
     * @param graph the seat adjacency graph
     * @param maxSolutions maximum number of solutions to return
     * @param timeoutMs maximum time in milliseconds
     */
    public CSPSolver(ConstraintSet constraintSet, SeatGraph graph,
                      int maxSolutions, long timeoutMs) {
        this.constraintSet = constraintSet;
        this.graph = graph;
        this.maxSolutions = maxSolutions;
        this.returnCount = maxSolutions;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Solves the seating problem using multiple passes:
     * Pass 1: Greedy (best seats first) for high-quality solution
     * Passes 2-N: Randomized for variety
     * Returns the top-ranked solutions.
     */
    public SolverResult solve(List<Student> students, List<Seat> seats) {
        startTime = System.currentTimeMillis();
        SolverResult result = new SolverResult();

        if (students.isEmpty() || seats.isEmpty()) {
            result.setSolveTimeMs(0);
            return result;
        }

        // Build initial domains
        HashMap<Student, Set<Seat>> baseDomains = new HashMap<Student, Set<Seat>>();
        for (Student s : students) {
            baseDomains.put(s, new HashSet<Seat>(seats));
        }
        pruneInitialDomains(baseDomains, students, seats);

        // Pass 1: Greedy (score-guided domain ordering)
        int internalMax = Math.max(maxSolutions * 2, 10); // find more, return best
        solvePass(students, baseDomains, result, internalMax, false);

        // Pass 2+: Randomized passes for variety (if time remains)
        for (int pass = 0; pass < 3; pass++) {
            if (System.currentTimeMillis() - startTime > timeoutMs * 0.8) break;
            solvePass(students, baseDomains, result, internalMax, true);
        }

        // Post-process: optimize each arrangement with local search (seat swaps)
        List<SeatingArrangement> optimized = new ArrayList<SeatingArrangement>();
        for (SeatingArrangement arr : result.getAll()) {
            SeatingArrangement improved = localSearchOptimize(arr, seats);
            optimized.add(improved);
        }
        // Rebuild result with optimized arrangements
        SolverResult finalResult = new SolverResult();
        for (SeatingArrangement arr : optimized) {
            finalResult.add(arr);
        }

        // Trim to requested count
        while (finalResult.size() > returnCount) {
            finalResult.getAll().remove(finalResult.size() - 1);
        }

        finalResult.setSolveTimeMs(System.currentTimeMillis() - startTime);
        return finalResult;
    }

    private void solvePass(List<Student> students,
                            HashMap<Student, Set<Seat>> baseDomains,
                            SolverResult result, int maxTotal, boolean randomize) {
        // Deep-copy domains for this pass
        HashMap<Student, Set<Seat>> domains = new HashMap<Student, Set<Seat>>();
        for (Map.Entry<Student, Set<Seat>> e : baseDomains.entrySet()) {
            domains.put(e.getKey(), new HashSet<Seat>(e.getValue()));
        }

        HashMap<Seat, Student> assignment = new HashMap<Seat, Student>();
        List<Student> unassigned = new ArrayList<Student>(students);

        backtrack(assignment, domains, unassigned, result, maxTotal, randomize);
    }

    private void pruneInitialDomains(HashMap<Student, Set<Seat>> domains,
                                      List<Student> students, List<Seat> seats) {
        for (Constraint c : constraintSet.getAll()) {
            if (!c.isHard()) continue;
            if ("zone".equals(c.getType())) {
                seating.constraint.ZoneConstraint zc = (seating.constraint.ZoneConstraint) c;
                Student student = zc.getStudent();
                Zone zone = zc.getZone();
                Set<Seat> domain = domains.get(student);
                if (domain == null) continue;

                Iterator<Seat> it = domain.iterator();
                while (it.hasNext()) {
                    Seat seat = it.next();
                    java.awt.geom.Point2D pos = seat.getGlobalPosition();
                    int gs = (seat.getParentDesk() != null) ? seat.getParentDesk().getGridSize() : 20;
                    boolean inZone = zone.contains(pos.getX(), pos.getY(), gs);
                    if ("must_be_in".equals(zc.getMode()) && !inZone) {
                        it.remove();
                    } else if ("must_not_be_in".equals(zc.getMode()) && inZone) {
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Recursive backtracking with MRV + forward checking.
     * When randomize=false, tries seats in score-guided order (greedy).
     * When randomize=true, shuffles for variety.
     */
    private boolean backtrack(HashMap<Seat, Student> assignment,
                               HashMap<Student, Set<Seat>> domains,
                               List<Student> unassigned,
                               SolverResult result, int maxTotal, boolean randomize) {

        if (System.currentTimeMillis() - startTime > timeoutMs) return false;
        if (result.size() >= maxTotal) return false;

        // Base case: all students assigned
        if (unassigned.isEmpty()) {
            SeatingArrangement arr = new SeatingArrangement(
                new HashMap<Seat, Student>(assignment), 0.0);
            arr.setScore(constraintSet.evaluate(arr, graph));

            if (constraintSet.allHardSatisfied(arr, graph)) {
                result.add(arr);
            }
            return result.size() < maxTotal;
        }

        Student selected = selectMRV(unassigned, domains);
        if (selected == null) return true;

        Set<Seat> domain = domains.get(selected);
        if (domain == null || domain.isEmpty()) return true;

        // Order domain: greedy (score-guided) or randomized
        List<Seat> orderedDomain = new ArrayList<Seat>(domain);
        if (randomize) {
            Collections.shuffle(orderedDomain);
        } else {
            // Score-guided: try seats that best satisfy soft constraints first
            orderByScore(orderedDomain, selected, assignment);
        }

        for (Seat seat : orderedDomain) {
            if (assignment.containsKey(seat)) continue;
            if (!isConsistent(selected, seat, assignment)) continue;

            assignment.put(seat, selected);
            unassigned.remove(selected);

            HashMap<Student, Set<Seat>> savedRemovals = forwardCheck(
                selected, seat, domains, unassigned, assignment);

            boolean deadEnd = false;
            for (Student u : unassigned) {
                Set<Seat> d = domains.get(u);
                if (d != null && d.isEmpty()) {
                    deadEnd = true;
                    break;
                }
            }

            if (!deadEnd) {
                boolean keepGoing = backtrack(assignment, domains, unassigned, result, maxTotal, randomize);
                if (!keepGoing) {
                    restoreDomains(savedRemovals, domains);
                    unassigned.add(selected);
                    assignment.remove(seat);
                    return false;
                }
            }

            restoreDomains(savedRemovals, domains);
            unassigned.add(selected);
            assignment.remove(seat);
        }

        return true;
    }

    /**
     * Orders seats by how well they satisfy soft constraints for this student.
     * Evaluates a partial arrangement with each candidate seat and scores it.
     */
    private void orderByScore(List<Seat> seats, final Student student,
                               final HashMap<Seat, Student> currentAssignment) {
        // Quick heuristic: score each seat by checking proximity to assigned students
        final HashMap<Seat, Double> seatScores = new HashMap<Seat, Double>();

        for (Seat seat : seats) {
            double score = 0;
            int checks = 0;

            for (Constraint c : constraintSet.getAll()) {
                if (!c.involvesStudent(student)) continue;

                if (c instanceof seating.constraint.ProximityConstraint) {
                    seating.constraint.ProximityConstraint pc =
                        (seating.constraint.ProximityConstraint) c;
                    Student other = pc.getStudentA().equals(student) ? pc.getStudentB() : pc.getStudentA();

                    // Check if the other student is already assigned
                    Seat otherSeat = null;
                    for (Map.Entry<Seat, Student> e : currentAssignment.entrySet()) {
                        if (e.getValue().equals(other)) {
                            otherSeat = e.getKey();
                            break;
                        }
                    }

                    if (otherSeat != null) {
                        boolean adj = graph.areAdjacent(seat, otherSeat);
                        if ("apart".equals(pc.getMode())) {
                            score += adj ? 0.0 : 1.0;
                        } else {
                            score += adj ? 1.0 : 0.3;
                        }
                        checks++;
                    }
                }

                if (c instanceof seating.constraint.ZoneConstraint) {
                    seating.constraint.ZoneConstraint zc =
                        (seating.constraint.ZoneConstraint) c;
                    java.awt.geom.Point2D pos = seat.getGlobalPosition();
                    int gs = (seat.getParentDesk() != null) ? seat.getParentDesk().getGridSize() : 20;
                    boolean inZone = zc.getZone().contains(pos.getX(), pos.getY(), gs);
                    if ("must_be_in".equals(zc.getMode())) {
                        score += inZone ? 1.0 : 0.0;
                    } else {
                        score += inZone ? 0.0 : 1.0;
                    }
                    checks++;
                }
            }

            seatScores.put(seat, checks > 0 ? score / checks : 0.5);
        }

        // Sort descending by score (best seats first)
        Collections.sort(seats, new Comparator<Seat>() {
            public int compare(Seat a, Seat b) {
                double sa = seatScores.containsKey(a) ? seatScores.get(a) : 0;
                double sb = seatScores.containsKey(b) ? seatScores.get(b) : 0;
                return Double.compare(sb, sa);
            }
        });
    }

    /**
     * Local search optimization: repeatedly try swapping pairs of students.
     * If a swap improves the score without violating hard constraints, keep it.
     * Runs up to 100 iterations or until no improvement found.
     */
    private SeatingArrangement localSearchOptimize(SeatingArrangement arr,
                                                     List<Seat> allSeats) {
        HashMap<Seat, Student> best = arr.getAssignmentMap();
        double bestScore = arr.getScore();

        // Get list of occupied seats
        List<Seat> occupied = new ArrayList<Seat>();
        for (Seat s : allSeats) {
            if (best.containsKey(s)) occupied.add(s);
        }

        // Also include unoccupied seats for move-to-empty optimization
        List<Seat> empty = new ArrayList<Seat>();
        for (Seat s : allSeats) {
            if (!best.containsKey(s)) empty.add(s);
        }

        boolean improved = true;
        int iterations = 0;
        while (improved && iterations < 100) {
            improved = false;
            iterations++;

            // Try swapping every pair of occupied seats
            for (int i = 0; i < occupied.size(); i++) {
                for (int j = i + 1; j < occupied.size(); j++) {
                    Seat seatA = occupied.get(i);
                    Seat seatB = occupied.get(j);
                    Student studentA = best.get(seatA);
                    Student studentB = best.get(seatB);

                    // Swap
                    best.put(seatA, studentB);
                    best.put(seatB, studentA);

                    SeatingArrangement trial = new SeatingArrangement(best, 0);
                    trial.setScore(constraintSet.evaluate(trial, graph));

                    if (trial.getScore() > bestScore && constraintSet.allHardSatisfied(trial, graph)) {
                        bestScore = trial.getScore();
                        improved = true;
                    } else {
                        // Swap back
                        best.put(seatA, studentA);
                        best.put(seatB, studentB);
                    }
                }
            }

            // Try moving students to empty seats
            for (int i = 0; i < occupied.size(); i++) {
                for (int j = 0; j < empty.size(); j++) {
                    Seat fromSeat = occupied.get(i);
                    Seat toSeat = empty.get(j);
                    Student student = best.get(fromSeat);

                    best.remove(fromSeat);
                    best.put(toSeat, student);

                    SeatingArrangement trial = new SeatingArrangement(best, 0);
                    trial.setScore(constraintSet.evaluate(trial, graph));

                    if (trial.getScore() > bestScore && constraintSet.allHardSatisfied(trial, graph)) {
                        bestScore = trial.getScore();
                        occupied.set(i, toSeat);
                        empty.set(j, fromSeat);
                        improved = true;
                    } else {
                        best.remove(toSeat);
                        best.put(fromSeat, student);
                    }
                }
            }
        }

        return new SeatingArrangement(best, bestScore);
    }

    /**
     * MRV heuristic: selects the unassigned student with the fewest valid seats.
     * Uses a PriorityQueue (min-heap) — a CS III data structure.
     */
    private Student selectMRV(List<Student> unassigned,
                               HashMap<Student, Set<Seat>> domains) {
        PriorityQueue<Student> pq = new PriorityQueue<Student>(
            Math.max(1, unassigned.size()),
            new Comparator<Student>() {
                public int compare(Student a, Student b) {
                    int sizeA = domains.containsKey(a) ? domains.get(a).size() : 0;
                    int sizeB = domains.containsKey(b) ? domains.get(b).size() : 0;
                    return Integer.compare(sizeA, sizeB);
                }
            }
        );
        pq.addAll(unassigned);
        return pq.isEmpty() ? null : pq.poll();
    }

    private boolean isConsistent(Student student, Seat seat,
                                  HashMap<Seat, Student> assignment) {
        for (Constraint c : constraintSet.getAll()) {
            if (!c.isHard()) continue;

            // Check proximity constraints
            if ("proximity".equals(c.getType())) {
                seating.constraint.ProximityConstraint pc =
                    (seating.constraint.ProximityConstraint) c;

                Student other;
                if (pc.getStudentA().equals(student)) {
                    other = pc.getStudentB();
                } else if (pc.getStudentB().equals(student)) {
                    other = pc.getStudentA();
                } else {
                    continue;
                }

                Seat otherSeat = null;
                for (Map.Entry<Seat, Student> entry : assignment.entrySet()) {
                    if (entry.getValue().equals(other)) {
                        otherSeat = entry.getKey();
                        break;
                    }
                }
                if (otherSeat == null) continue;

                boolean adjacent = graph.areAdjacent(seat, otherSeat);
                if ("apart".equals(pc.getMode()) && adjacent) return false;
                if ("together".equals(pc.getMode()) && !adjacent) return false;
            }

            // Check zone constraints
            if ("zone".equals(c.getType())) {
                seating.constraint.ZoneConstraint zc =
                    (seating.constraint.ZoneConstraint) c;
                if (!zc.getStudent().equals(student)) continue;
                java.awt.geom.Point2D pos = seat.getGlobalPosition();
                int gs = (seat.getParentDesk() != null) ? seat.getParentDesk().getGridSize() : 20;
                boolean inZone = zc.getZone().contains(pos.getX(), pos.getY(), gs);
                if ("must_be_in".equals(zc.getMode()) && !inZone) return false;
                if ("must_not_be_in".equals(zc.getMode()) && inZone) return false;
            }
        }
        return true;
    }

    private HashMap<Student, Set<Seat>> forwardCheck(
            Student assigned, Seat assignedSeat,
            HashMap<Student, Set<Seat>> domains,
            List<Student> unassigned,
            HashMap<Seat, Student> assignment) {

        HashMap<Student, Set<Seat>> removals = new HashMap<Student, Set<Seat>>();

        for (Student other : unassigned) {
            Set<Seat> domain = domains.get(other);
            if (domain == null) continue;

            Set<Seat> toRemove = new HashSet<Seat>();

            if (domain.contains(assignedSeat)) {
                toRemove.add(assignedSeat);
            }

            for (Constraint c : constraintSet.getAll()) {
                if (!c.isHard() || !"proximity".equals(c.getType())) continue;
                seating.constraint.ProximityConstraint pc =
                    (seating.constraint.ProximityConstraint) c;

                boolean involves = (pc.getStudentA().equals(other) && pc.getStudentB().equals(assigned))
                    || (pc.getStudentB().equals(other) && pc.getStudentA().equals(assigned));
                if (!involves) continue;

                for (Seat candidate : domain) {
                    if (toRemove.contains(candidate)) continue;
                    boolean adj = graph.areAdjacent(candidate, assignedSeat);
                    if ("apart".equals(pc.getMode()) && adj) {
                        toRemove.add(candidate);
                    }
                    if ("together".equals(pc.getMode()) && !adj) {
                        toRemove.add(candidate);
                    }
                }
            }

            if (!toRemove.isEmpty()) {
                domain.removeAll(toRemove);
                removals.put(other, toRemove);
            }
        }

        return removals;
    }

    private void restoreDomains(HashMap<Student, Set<Seat>> removals,
                                 HashMap<Student, Set<Seat>> domains) {
        for (Map.Entry<Student, Set<Seat>> entry : removals.entrySet()) {
            Set<Seat> domain = domains.get(entry.getKey());
            if (domain != null) {
                domain.addAll(entry.getValue());
            }
        }
    }
}
