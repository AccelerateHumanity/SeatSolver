package seating.layout;

import seating.model.Classroom;
import seating.model.Landmark;

/**
 * Command that deletes a landmark. Supports undo/redo.
 */
public class DeleteLandmarkCommand implements Command {

    private Classroom classroom;
    private Landmark landmark;

    public DeleteLandmarkCommand(Classroom classroom, Landmark landmark) {
        this.classroom = classroom;
        this.landmark = landmark;
    }

    public void execute() { classroom.removeLandmark(landmark); }
    public void undo() { classroom.addLandmark(landmark); }
    public String describe() { return "Delete " + landmark.getLabel(); }
}
