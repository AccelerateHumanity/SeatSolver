package seating.layout;

import seating.model.Classroom;
import seating.model.Desk;

/**
 * Command that deletes a desk from the classroom.
 * Undo restores it to the desk list.
 */
public class DeleteDeskCommand implements Command {

    private Classroom classroom;
    private Desk desk;

    /**
     * Creates a delete command.
     *
     * @param classroom the classroom to remove from
     * @param desk the desk to delete
     */
    public DeleteDeskCommand(Classroom classroom, Desk desk) {
        this.classroom = classroom;
        this.desk = desk;
    }

    public void execute() {
        classroom.removeDesk(desk);
    }

    public void undo() {
        classroom.addDesk(desk);
    }

    public String describe() {
        return "Delete " + desk.getTypeName() + " desk";
    }
}
