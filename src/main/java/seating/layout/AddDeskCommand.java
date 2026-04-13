package seating.layout;

import seating.model.Classroom;
import seating.model.Desk;

/**
 * Command that adds a desk to the classroom.
 * Undo removes it; redo adds it back.
 */
public class AddDeskCommand implements Command {

    private Classroom classroom;
    private Desk desk;

    /**
     * Creates an add-desk command.
     *
     * @param classroom the classroom to add to
     * @param desk the desk to add
     */
    public AddDeskCommand(Classroom classroom, Desk desk) {
        this.classroom = classroom;
        this.desk = desk;
    }

    public void execute() {
        classroom.addDesk(desk);
    }

    public void undo() {
        classroom.removeDesk(desk);
    }

    public String describe() {
        return "Add " + desk.getTypeName() + " desk";
    }
}
