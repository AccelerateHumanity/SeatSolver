package seating.layout;

import seating.model.Classroom;
import seating.model.Landmark;

/**
 * Command that adds a landmark to the classroom. Supports undo/redo.
 */
public class AddLandmarkCommand implements Command {

    private Classroom classroom;
    private Landmark landmark;

    public AddLandmarkCommand(Classroom classroom, Landmark landmark) {
        this.classroom = classroom;
        this.landmark = landmark;
    }

    public void execute() { classroom.addLandmark(landmark); }
    public void undo() { classroom.removeLandmark(landmark); }
    public String describe() { return "Add " + landmark.getLabel(); }
}
