package seating.layout;

import seating.model.Desk;

/**
 * Command that moves a desk from one grid position to another.
 * Stores both old and new positions for undo/redo.
 */
public class MoveDeskCommand implements Command {

    private Desk desk;
    private int oldX, oldY;
    private int newX, newY;

    /**
     * Creates a move command.
     *
     * @param desk the desk being moved
     * @param oldX original grid column
     * @param oldY original grid row
     * @param newX destination grid column
     * @param newY destination grid row
     */
    public MoveDeskCommand(Desk desk, int oldX, int oldY, int newX, int newY) {
        this.desk = desk;
        this.oldX = oldX;
        this.oldY = oldY;
        this.newX = newX;
        this.newY = newY;
    }

    public void execute() {
        desk.setPosition(newX, newY);
    }

    public void undo() {
        desk.setPosition(oldX, oldY);
    }

    public String describe() {
        return "Move " + desk.getTypeName() + " to (" + newX + "," + newY + ")";
    }
}
