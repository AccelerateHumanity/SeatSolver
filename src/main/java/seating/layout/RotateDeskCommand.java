package seating.layout;

import seating.model.Desk;

/**
 * Command that rotates a desk by a given angle.
 * Stores the delta so undo can reverse the exact rotation.
 */
public class RotateDeskCommand implements Command {

    private Desk desk;
    private double deltaDegrees;

    /**
     * Creates a rotate command.
     *
     * @param desk the desk to rotate
     * @param deltaDegrees degrees to rotate (positive = clockwise)
     */
    public RotateDeskCommand(Desk desk, double deltaDegrees) {
        this.desk = desk;
        this.deltaDegrees = deltaDegrees;
    }

    public void execute() {
        desk.rotate(deltaDegrees);
    }

    public void undo() {
        desk.rotate(-deltaDegrees);
    }

    public String describe() {
        return "Rotate " + desk.getTypeName() + " by " + (int) deltaDegrees + "\u00B0";
    }
}
