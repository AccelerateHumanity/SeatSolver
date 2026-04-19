package seating.layout;

import seating.model.Zone;
import java.awt.Color;

/**
 * Command that edits a zone's label, bounds, color, and/or rotation.
 * Stores old and new values so undo restores the previous state.
 */
public class EditZoneCommand implements Command {

    private Zone zone;
    private String oldLabel, newLabel;
    private int oldX, oldY, oldW, oldH;
    private int newX, newY, newW, newH;
    private Color oldColor, newColor;
    private double oldRotation, newRotation;

    public EditZoneCommand(Zone zone,
            String newLabel, int newX, int newY, int newW, int newH, Color newColor) {
        this(zone, newLabel, newX, newY, newW, newH, newColor, zone.getRotation());
    }

    public EditZoneCommand(Zone zone,
            String newLabel, int newX, int newY, int newW, int newH,
            Color newColor, double newRotation) {
        this.zone = zone;
        this.oldLabel = zone.getLabel();
        this.oldX = zone.getGridX();
        this.oldY = zone.getGridY();
        this.oldW = zone.getGridWidth();
        this.oldH = zone.getGridHeight();
        this.oldColor = zone.getColor();
        this.oldRotation = zone.getRotation();
        this.newLabel = newLabel;
        this.newX = newX;
        this.newY = newY;
        this.newW = newW;
        this.newH = newH;
        this.newColor = newColor;
        this.newRotation = newRotation;
    }

    public void execute() {
        zone.setLabel(newLabel);
        zone.setBounds(newX, newY, newW, newH);
        zone.setColor(newColor);
        zone.setRotation(newRotation);
    }

    public void undo() {
        zone.setLabel(oldLabel);
        zone.setBounds(oldX, oldY, oldW, oldH);
        zone.setColor(oldColor);
        zone.setRotation(oldRotation);
    }

    public String describe() { return "Edit zone: " + oldLabel; }
}
