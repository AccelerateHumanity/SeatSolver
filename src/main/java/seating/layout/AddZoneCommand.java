package seating.layout;

import seating.model.Classroom;
import seating.model.Zone;

/**
 * Command that adds a zone to the classroom. Supports undo/redo.
 */
public class AddZoneCommand implements Command {

    private Classroom classroom;
    private Zone zone;

    public AddZoneCommand(Classroom classroom, Zone zone) {
        this.classroom = classroom;
        this.zone = zone;
    }

    public void execute() { classroom.addZone(zone); }
    public void undo() { classroom.removeZone(zone); }
    public String describe() { return "Add zone: " + zone.getLabel(); }
}
