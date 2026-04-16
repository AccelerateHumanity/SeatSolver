package seating.layout;

import seating.model.Classroom;
import seating.model.Desk;
import seating.model.Landmark;
import seating.model.Zone;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Command that resets the classroom to an initial/starting snapshot.
 * Captures the CURRENT state at construction so undo returns the user
 * to the state they were in just before clicking Reset.
 *
 * <p>The initial snapshot is provided by the caller (MainFrame captures
 * this once, right after defaults are populated at app startup).
 */
public class ResetCommand implements Command {

    public static class Snapshot {
        public int cols, rows;
        public List<DeskEntry> desks = new ArrayList<DeskEntry>();
        public List<LandmarkEntry> landmarks = new ArrayList<LandmarkEntry>();
        public List<ZoneEntry> zones = new ArrayList<ZoneEntry>();

        public static Snapshot capture(Classroom c) {
            Snapshot s = new Snapshot();
            s.cols = c.getGridColumns();
            s.rows = c.getGridRows();
            for (Desk d : c.getDesks()) {
                s.desks.add(new DeskEntry(d, d.getGridX(), d.getGridY(), d.getRotation()));
            }
            for (Landmark lm : c.getLandmarks()) {
                s.landmarks.add(new LandmarkEntry(lm.getType(), lm.getLabel(),
                    lm.getGridX(), lm.getGridY(), lm.getGridW(), lm.getGridH(),
                    lm.getRotation()));
            }
            for (Zone z : c.getZones()) {
                s.zones.add(new ZoneEntry(z.getLabel(),
                    z.getGridX(), z.getGridY(),
                    z.getGridWidth(), z.getGridHeight(),
                    z.getColor()));
            }
            return s;
        }
    }

    public static class DeskEntry {
        final Desk desk; final int gx, gy; final double rotation;
        DeskEntry(Desk d, int gx, int gy, double r) {
            this.desk = d; this.gx = gx; this.gy = gy; this.rotation = r;
        }
    }

    public static class LandmarkEntry {
        final String type, label;
        final int gx, gy, gw, gh;
        final double rotation;
        LandmarkEntry(String type, String label, int gx, int gy, int gw, int gh, double r) {
            this.type = type; this.label = label;
            this.gx = gx; this.gy = gy; this.gw = gw; this.gh = gh;
            this.rotation = r;
        }
    }

    public static class ZoneEntry {
        final String label;
        final int gx, gy, gw, gh;
        final Color color;
        ZoneEntry(String label, int gx, int gy, int gw, int gh, Color color) {
            this.label = label;
            this.gx = gx; this.gy = gy; this.gw = gw; this.gh = gh;
            this.color = color;
        }
    }

    private final Classroom classroom;
    private final Snapshot target;   // what to restore on execute()
    private Snapshot priorState;     // what to restore on undo()

    public ResetCommand(Classroom classroom, Snapshot target) {
        this.classroom = classroom;
        this.target = target;
        this.priorState = Snapshot.capture(classroom);
    }

    public void execute() {
        applySnapshot(target);
    }

    public void undo() {
        applySnapshot(priorState);
    }

    public String describe() { return "Reset classroom"; }

    private void applySnapshot(Snapshot s) {
        // Resize first so zones scale correctly; then overwrite items
        classroom.resize(s.cols, s.rows);
        classroom.getDesks().clear();
        classroom.getLandmarks().clear();
        classroom.getZones().clear();
        for (DeskEntry de : s.desks) {
            de.desk.setPosition(de.gx, de.gy);
            de.desk.setRotation(de.rotation);
            classroom.getDesks().add(de.desk);
        }
        for (LandmarkEntry le : s.landmarks) {
            Landmark lm = new Landmark(le.type, le.gx, le.gy, le.gw, le.gh);
            lm.setLabel(le.label);
            lm.setRotation(le.rotation);
            classroom.getLandmarks().add(lm);
        }
        for (ZoneEntry ze : s.zones) {
            Zone z = new Zone(ze.label, ze.gx, ze.gy, ze.gw, ze.gh, ze.color);
            classroom.getZones().add(z);
        }
    }
}
