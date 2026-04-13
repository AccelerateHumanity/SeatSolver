package seating.layout;

import seating.model.Zone;
import java.awt.Color;

/**
 * Command that edits a zone's label, bounds, or color. Stores the old
 * and new values so undo restores the previous state. Supports undo/redo.
 */
public class EditZoneCommand implements Command {

    private Zone zone;
    private String oldLabel, newLabel;
    private int oldX, oldY, oldW, oldH;
    private int newX, newY, newW, newH;
    private Color oldColor, newColor;

    public EditZoneCommand(Zone zone,
            String newLabel, int newX, int newY, int newW, int newH, Color newColor) {
        this.zone = zone;
        this.oldLabel = zone.getLabel();
        this.oldX = zone.getGridX();
        this.oldY = zone.getGridY();
        this.oldW = zone.getGridWidth();
        this.oldH = zone.getGridHeight();
        this.oldColor = zone.getColor();
        this.newLabel = newLabel;
        this.newX = newX;
        this.newY = newY;
        this.newW = newW;
        this.newH = newH;
        this.newColor = newColor;
    }

    public void execute() {
        zone.setLabel(newLabel);
        zone.setBounds(newX, newY, newW, newH);
        zone.setColor(newColor);
    }

    public void undo() {
        zone.setLabel(oldLabel);
        zone.setBounds(oldX, oldY, oldW, oldH);
        zone.setColor(oldColor);
    }

    public String describe() { return "Edit zone: " + oldLabel; }
}
