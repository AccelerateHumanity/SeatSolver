package seating.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the entire classroom layout.
 * Contains all desks, zones, and grid configuration.
 */
public class Classroom {

    private int gridColumns;
    private int gridRows;
    private int gridSize;       // pixels per cell
    private int adjacencyThreshold; // pixel distance for "next to"
    private List<Desk> desks;
    private List<Zone> zones;
    private List<Landmark> landmarks;

    /**
     * Creates a classroom with the given grid dimensions.
     *
     * @param gridColumns number of columns
     * @param gridRows number of rows
     * @param gridSize pixel size of each grid cell
     */
    public Classroom(int gridColumns, int gridRows, int gridSize) {
        this.gridColumns = gridColumns;
        this.gridRows = gridRows;
        this.gridSize = gridSize;
        this.adjacencyThreshold = gridSize * 3; // seats within 3 cells are adjacent
        this.desks = new ArrayList<Desk>();
        this.zones = new ArrayList<Zone>();
        this.landmarks = new ArrayList<Landmark>();
    }

    /** Creates a default 28x20 classroom with 20px grid cells. Double resolution for fine positioning. */
    public Classroom() {
        this(28, 20, 20);
    }

    // ---- Desk management ----

    public void addDesk(Desk desk) {
        desk.setGridSize(gridSize);
        desks.add(desk);
    }

    public void removeDesk(Desk desk) {
        desks.remove(desk);
    }

    /**
     * Returns the desk at the given pixel coordinates, or null.
     * Searches in reverse order so top-drawn desks are hit first.
     *
     * @param px pixel x
     * @param py pixel y
     * @return the desk under the point, or null
     */
    public Desk getDeskAt(double px, double py) {
        for (int i = desks.size() - 1; i >= 0; i--) {
            Desk d = desks.get(i);
            if (d.getBounds(gridSize).contains(px, py)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Checks if a desk at the given position would collide with existing desks.
     *
     * @param desk the desk to check
     * @param exclude a desk to exclude from checking (e.g. itself during move)
     * @return true if there is a collision
     */
    public boolean hasCollision(Desk desk, Desk exclude) {
        for (Desk d : desks) {
            if (d == exclude) continue;
            if (desk.collidesWith(d, gridSize)) return true;
        }
        // Also check desk vs landmarks
        java.awt.geom.Rectangle2D deskBounds = desk.getBounds(gridSize);
        for (Landmark lm : landmarks) {
            if (deskBounds.intersects(lm.getBounds(gridSize))) return true;
        }
        return false;
    }

    /**
     * Checks if a landmark at its current position overlaps any desk or other landmark.
     */
    public boolean hasLandmarkCollision(Landmark landmark, Landmark exclude) {
        java.awt.geom.Rectangle2D lmBounds = landmark.getBounds(gridSize);
        // Check vs desks
        for (Desk d : desks) {
            if (lmBounds.intersects(d.getBounds(gridSize))) return true;
        }
        // Check vs other landmarks
        for (Landmark lm : landmarks) {
            if (lm == exclude) continue;
            if (lmBounds.intersects(lm.getBounds(gridSize))) return true;
        }
        return false;
    }

    /**
     * Returns a flat list of all seats across all desks.
     *
     * @return all seats in the classroom
     */
    public List<Seat> getAllSeats() {
        List<Seat> allSeats = new ArrayList<Seat>();
        for (Desk d : desks) {
            allSeats.addAll(d.getSeats());
        }
        return allSeats;
    }

    /**
     * Returns the total number of seats across all desks.
     *
     * @return total seat count
     */
    public int getTotalSeatCount() {
        int count = 0;
        for (Desk d : desks) {
            count += d.getSeatCount();
        }
        return count;
    }

    // ---- Zone management ----

    public void addZone(Zone zone) { zones.add(zone); }
    public void removeZone(Zone zone) { zones.remove(zone); }

    // ---- Landmark management ----

    /**
     * Returns the landmark at the given pixel coordinates, or null.
     */
    public Landmark getLandmarkAt(double px, double py) {
        for (int i = landmarks.size() - 1; i >= 0; i--) {
            if (landmarks.get(i).containsPoint(px, py, gridSize)) {
                return landmarks.get(i);
            }
        }
        return null;
    }

    public void addLandmark(Landmark lm) { landmarks.add(lm); }
    public void removeLandmark(Landmark lm) { landmarks.remove(lm); }
    public List<Landmark> getLandmarks() { return landmarks; }

    /**
     * Resizes the classroom grid. Existing desks outside new bounds are removed.
     *
     * @param cols new column count
     * @param rows new row count
     */
    public void resize(int cols, int rows) {
        int oldCols = this.gridColumns;
        int oldRows = this.gridRows;
        this.gridColumns = Math.max(5, cols);
        this.gridRows = Math.max(5, rows);
        this.adjacencyThreshold = gridSize * 3;

        // Re-sync desk gridSize
        for (Desk d : desks) {
            d.setGridSize(gridSize);
        }

        // Scale zones proportionally
        for (Zone z : zones) {
            int newGx = (int) Math.round((double) z.getGridX() * gridColumns / oldCols);
            int newGy = (int) Math.round((double) z.getGridY() * gridRows / oldRows);
            int newGw = Math.max(1, (int) Math.round((double) z.getGridWidth() * gridColumns / oldCols));
            int newGh = Math.max(1, (int) Math.round((double) z.getGridHeight() * gridRows / oldRows));
            // Clamp to new bounds
            newGx = Math.min(newGx, gridColumns - 1);
            newGy = Math.min(newGy, gridRows - 1);
            newGw = Math.min(newGw, gridColumns - newGx);
            newGh = Math.min(newGh, gridRows - newGy);
            z.setBounds(newGx, newGy, newGw, newGh);
        }

        // Remove desks that no longer fit
        java.util.Iterator<Desk> it = desks.iterator();
        while (it.hasNext()) {
            Desk d = it.next();
            if (d.getGridX() + d.getWidthInCells() > gridColumns ||
                d.getGridY() + d.getHeightInCells() > gridRows) {
                it.remove();
            }
        }

        // Remove landmarks that no longer fit
        java.util.Iterator<Landmark> lit = landmarks.iterator();
        while (lit.hasNext()) {
            Landmark lm = lit.next();
            if (lm.getGridX() + lm.getGridW() > gridColumns ||
                lm.getGridY() + lm.getGridH() > gridRows) {
                lit.remove();
            }
        }
    }

    // ---- Getters ----

    public List<Desk> getDesks() { return desks; }
    public List<Zone> getZones() { return zones; }
    public int getGridColumns() { return gridColumns; }
    public int getGridRows() { return gridRows; }
    public int getGridSize() { return gridSize; }
    public int getAdjacencyThreshold() { return adjacencyThreshold; }

    public int getPixelWidth() { return gridColumns * gridSize; }
    public int getPixelHeight() { return gridRows * gridSize; }
}
