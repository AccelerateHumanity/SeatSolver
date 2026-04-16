package seating.layout;

import seating.constraint.ConstraintSet;
import seating.solver.SeatGraph;
import seating.solver.SolverResult;
import seating.ui.ArrangementPanel;
import seating.ui.ClassroomPanel;

/**
 * Command that swaps the currently-displayed seating result. Used to
 * make "Generate Seating" undoable — the solver runs once in the UI,
 * its output is passed to this command's constructor as the "new" state,
 * and the prior ArrangementPanel state is captured so undo restores it.
 */
public class GenerateSeatingCommand implements Command {

    private final ClassroomPanel classroomPanel;
    private final ArrangementPanel arrangementPanel;

    private final SolverResult newResult;
    private final ConstraintSet newConstraints;
    private final SeatGraph newGraph;

    private final SolverResult oldResult;
    private final ConstraintSet oldConstraints;
    private final SeatGraph oldGraph;

    public GenerateSeatingCommand(ClassroomPanel cp, ArrangementPanel ap,
            SolverResult newResult, ConstraintSet newConstraints, SeatGraph newGraph,
            SolverResult oldResult, ConstraintSet oldConstraints, SeatGraph oldGraph) {
        this.classroomPanel = cp;
        this.arrangementPanel = ap;
        this.newResult = newResult;
        this.newConstraints = newConstraints;
        this.newGraph = newGraph;
        this.oldResult = oldResult;
        this.oldConstraints = oldConstraints;
        this.oldGraph = oldGraph;
    }

    public void execute() {
        classroomPanel.setConstraintData(newConstraints, newGraph);
        arrangementPanel.setResult(newResult, newConstraints, newGraph);
    }

    public void undo() {
        classroomPanel.setConstraintData(oldConstraints, oldGraph);
        arrangementPanel.setResult(oldResult, oldConstraints, oldGraph);
    }

    public String describe() { return "Generate seating"; }
}
