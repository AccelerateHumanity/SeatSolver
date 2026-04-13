package seating.layout;

import seating.model.Landmark;

/**
 * Command that moves a landmark. Supports undo/redo.
 */
public class MoveLandmarkCommand implements Command {

    private Landmark landmark;
    private int oldX, oldY, newX, newY;

    public MoveLandmarkCommand(Landmark landmark, int oldX, int oldY, int newX, int newY) {
        this.landmark = landmark;
        this.oldX = oldX;
        this.oldY = oldY;
        this.newX = newX;
        this.newY = newY;
    }

    public void execute() { landmark.setPosition(newX, newY); }
    public void undo() { landmark.setPosition(oldX, oldY); }
    public String describe() { return "Move " + landmark.getLabel(); }
}
