package seating.layout;

import seating.model.Landmark;

/**
 * Command that rotates a landmark by a given angle. Supports undo/redo.
 */
public class RotateLandmarkCommand implements Command {

    private Landmark landmark;
    private double degrees;

    public RotateLandmarkCommand(Landmark landmark, double degrees) {
        this.landmark = landmark;
        this.degrees = degrees;
    }

    public void execute() { landmark.rotate(degrees); }
    public void undo() { landmark.rotate(-degrees); }
    public String describe() { return "Rotate " + landmark.getLabel() + " " + (int)degrees + "\u00B0"; }
}
