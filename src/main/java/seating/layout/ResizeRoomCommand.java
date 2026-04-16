package seating.layout;

import seating.model.Classroom;
import seating.model.Desk;
import seating.model.Landmark;
import seating.model.Zone;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Command that resizes the classroom grid. Captures a full snapshot of
 * desk/landmark/zone state before the resize so undo can fully restore
 * the prior layout — including any items that were dropped because they
 * no longer fit the new grid.
 */
public class ResizeRoomCommand implements Command {

    private final Classroom classroom;
    private final int newCols, newRows;

    // Snapshot fields captured BEFORE execute()
    private int oldCols, oldRows;
    private List<DeskSnapshot> deskSnapshots;
    private List<LandmarkSnapshot> landmarkSnapshots;
    private List<ZoneSnapshot> zoneSnapshots;

    public ResizeRoomCommand(Classroom classroom, int newCols, int newRows) {
        this.classroom = classroom;
        this.newCols = newCols;
        this.newRows = newRows;
        snapshotCurrentState();
    }

    private void snapshotCurrentState() {
        oldCols = classroom.getGridColumns();
        oldRows = classroom.getGridRows();
        deskSnapshots = new ArrayList<DeskSnapshot>();
        for (Desk d : classroom.getDesks()) {
            deskSnapshots.add(new DeskSnapshot(d, d.getGridX(), d.getGridY(), d.getRotation()));
        }
        landmarkSnapshots = new ArrayList<LandmarkSnapshot>();
        for (Landmark lm : classroom.getLandmarks()) {
            landmarkSnapshots.add(new LandmarkSnapshot(lm,
                lm.getGridX(), lm.getGridY(), lm.getRotation()));
        }
        zoneSnapshots = new ArrayList<ZoneSnapshot>();
        for (Zone z : classroom.getZones()) {
            zoneSnapshots.add(new ZoneSnapshot(z,
                z.getGridX(), z.getGridY(),
                z.getGridWidth(), z.getGridHeight(),
                z.getLabel(), z.getColor()));
        }
    }

    public void execute() {
        classroom.resize(newCols, newRows);
    }

    public void undo() {
        // Restore the old grid dimensions (this will re-scale zones/items
        // back via Classroom.resize's proportional logic, but not exactly
        // — so we also explicitly restore each snapshot).
        classroom.resize(oldCols, oldRows);

        // Ensure every snapshotted desk is in the list and at its original position
        for (DeskSnapshot s : deskSnapshots) {
            if (!classroom.getDesks().contains(s.desk)) {
                classroom.getDesks().add(s.desk);
            }
            s.desk.setPosition(s.gx, s.gy);
            s.desk.setRotation(s.rotation);
        }
        // Same for landmarks
        for (LandmarkSnapshot s : landmarkSnapshots) {
            if (!classroom.getLandmarks().contains(s.landmark)) {
                classroom.getLandmarks().add(s.landmark);
            }
            s.landmark.setPosition(s.gx, s.gy);
            s.landmark.setRotation(s.rotation);
        }
        // Zones: restore bounds/label/color
        for (ZoneSnapshot s : zoneSnapshots) {
            if (!classroom.getZones().contains(s.zone)) {
                classroom.getZones().add(s.zone);
            }
            s.zone.setBounds(s.gx, s.gy, s.gw, s.gh);
            s.zone.setLabel(s.label);
            s.zone.setColor(s.color);
        }
    }

    public String describe() {
        return "Resize room to " + newCols + "\u00D7" + newRows;
    }

    // --- inner snapshot records ---

    private static class DeskSnapshot {
        final Desk desk; final int gx, gy; final double rotation;
        DeskSnapshot(Desk d, int gx, int gy, double r) {
            this.desk = d; this.gx = gx; this.gy = gy; this.rotation = r;
        }
    }

    private static class LandmarkSnapshot {
        final Landmark landmark; final int gx, gy; final double rotation;
        LandmarkSnapshot(Landmark lm, int gx, int gy, double r) {
            this.landmark = lm; this.gx = gx; this.gy = gy; this.rotation = r;
        }
    }

    private static class ZoneSnapshot {
        final Zone zone; final int gx, gy, gw, gh; final String label; final Color color;
        ZoneSnapshot(Zone z, int gx, int gy, int gw, int gh, String label, Color color) {
            this.zone = z; this.gx = gx; this.gy = gy; this.gw = gw; this.gh = gh;
            this.label = label; this.color = color;
        }
    }
}
