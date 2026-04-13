package seating.layout;

import seating.model.Classroom;
import seating.model.Zone;

/**
 * Command that deletes a zone from the classroom. Supports undo/redo.
 */
public class DeleteZoneCommand implements Command {

    private Classroom classroom;
    private Zone zone;

    public DeleteZoneCommand(Classroom classroom, Zone zone) {
        this.classroom = classroom;
        this.zone = zone;
    }

    public void execute() { classroom.removeZone(zone); }
    public void undo() { classroom.addZone(zone); }
    public String describe() { return "Delete zone: " + zone.getLabel(); }
}
