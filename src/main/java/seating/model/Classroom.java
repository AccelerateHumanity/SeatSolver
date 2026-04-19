package seating.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the entire classroom layout.
 * Contains all desks, zones, and grid configuration.
 */
public class Classroom {

    /** Default grid width in cells for a new classroom. */
    public static final int DEFAULT_COLS = 28;
    /** Default grid height in cells for a new classroom. */
    public static final int DEFAULT_ROWS = 20;
    /** Default pixel size of each grid cell. */
    public static final int DEFAULT_GRID_SIZE = 20;
    /** Seats within this many grid cells count as "adjacent" for constraints. */
    public static final int ADJACENCY_MULTIPLIER = 3;

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
        this.adjacencyThreshold = gridSize * ADJACENCY_MULTIPLIER;
        this.desks = new ArrayList<Desk>();
        this.zones = new ArrayList<Zone>();
        this.landmarks = new ArrayList<Landmark>();
    }

    /** Creates a default classroom (28x20 grid, 20px cells). */
    public Classroom() {
        this(DEFAULT_COLS, DEFAULT_ROWS, DEFAULT_GRID_SIZE);
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
        // Use the exact rotated shape for hit-testing so rotated desks are
        // clickable only where they're actually visible (not the inflated AABB).
        for (int i = desks.size() - 1; i >= 0; i--) {
            Desk d = desks.get(i);
            if (d.getCollisionShape(gridSize).contains(px, py)) {
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
        // Desk vs landmarks — pixel-perfect Area intersection
        java.awt.geom.Area deskArea = new java.awt.geom.Area(desk.getCollisionShape(gridSize));
        for (Landmark lm : landmarks) {
            java.awt.geom.Area lmArea = new java.awt.geom.Area(lm.getCollisionShape(gridSize));
            lmArea.intersect(deskArea);
            if (!lmArea.isEmpty()) return true;
        }
        return false;
    }

    /**
     * Checks if a landmark at its current position overlaps any desk or other landmark.
     * Uses pixel-perfect Area intersection instead of AABB to avoid false positives
     * when items are rotated at odd angles.
     */
    public boolean hasLandmarkCollision(Landmark landmark, Landmark exclude) {
        java.awt.geom.Area lmArea = new java.awt.geom.Area(landmark.getCollisionShape(gridSize));
        // Check vs desks
        for (Desk d : desks) {
            java.awt.geom.Area dArea = new java.awt.geom.Area(d.getCollisionShape(gridSize));
            dArea.intersect(lmArea);
            if (!dArea.isEmpty()) return true;
        }
        // Check vs other landmarks
        for (Landmark lm : landmarks) {
            if (lm == exclude) continue;
            java.awt.geom.Area other = new java.awt.geom.Area(lm.getCollisionShape(gridSize));
            other.intersect(lmArea);
            if (!other.isEmpty()) return true;
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

    public void addLandmark(Landmark lm) {
        // Clamp position so landmark stays inside the grid
        lm.setPosition(
            Math.max(0, Math.min(lm.getGridX(), gridColumns - lm.getGridW())),
            Math.max(0, Math.min(lm.getGridY(), gridRows - lm.getGridH())));
        landmarks.add(lm);
    }
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

        double scaleX = (double) gridColumns / oldCols;
        double scaleY = (double) gridRows / oldRows;

        // Re-sync desk gridSize
        for (Desk d : desks) {
            d.setGridSize(gridSize);
        }

        // Scale zones proportionally
        for (Zone z : zones) {
            int newGx = (int) Math.round(z.getGridX() * scaleX);
            int newGy = (int) Math.round(z.getGridY() * scaleY);
            int newGw = Math.max(1, (int) Math.round(z.getGridWidth() * scaleX));
            int newGh = Math.max(1, (int) Math.round(z.getGridHeight() * scaleY));
            newGx = Math.min(newGx, gridColumns - 1);
            newGy = Math.min(newGy, gridRows - 1);
            newGw = Math.min(newGw, gridColumns - newGx);
            newGh = Math.min(newGh, gridRows - newGy);
            z.setBounds(newGx, newGy, newGw, newGh);
        }

        // Scale desk positions proportionally (keeps their layout in the
        // same relative area as the room shrinks/grows).
        for (Desk d : desks) {
            int newGx = (int) Math.round(d.getGridX() * scaleX);
            int newGy = (int) Math.round(d.getGridY() * scaleY);
            // Keep desk inside grid; getRotatedBounds accounts for rotation
            newGx = Math.max(0, Math.min(newGx, gridColumns - d.getWidthInCells()));
            newGy = Math.max(0, Math.min(newGy, gridRows - d.getHeightInCells()));
            d.setPosition(newGx, newGy);
        }

        // Scale landmark positions proportionally
        for (Landmark lm : landmarks) {
            int newGx = (int) Math.round(lm.getGridX() * scaleX);
            int newGy = (int) Math.round(lm.getGridY() * scaleY);
            newGx = Math.max(0, Math.min(newGx, gridColumns - lm.getGridW()));
            newGy = Math.max(0, Math.min(newGy, gridRows - lm.getGridH()));
            lm.setPosition(newGx, newGy);
        }

        // Remove desks that STILL don't fit after scaling (e.g. desk bigger
        // than new grid). Fallback for edge cases.
        java.util.Iterator<Desk> it = desks.iterator();
        while (it.hasNext()) {
            Desk d = it.next();
            if (d.getGridX() + d.getWidthInCells() > gridColumns ||
                d.getGridY() + d.getHeightInCells() > gridRows) {
                it.remove();
            }
        }

        // Resolve desk-desk collisions after scaling. Try nudging overlapping
        // desks; drop them if they can't be resolved in a few attempts.
        boolean changed = true;
        int passes = 0;
        while (changed && passes < 3) {
            changed = false;
            passes++;
            java.util.Iterator<Desk> cit = desks.iterator();
            while (cit.hasNext()) {
                Desk d = cit.next();
                if (hasCollision(d, d)) {
                    // Try nudging right, then down, then diagonal
                    boolean resolved = false;
                    for (int[] nudge : new int[][]{{1,0},{0,1},{1,1},{-1,0},{0,-1}}) {
                        int nx = d.getGridX() + nudge[0];
                        int ny = d.getGridY() + nudge[1];
                        if (nx >= 0 && ny >= 0 &&
                            nx + d.getWidthInCells() <= gridColumns &&
                            ny + d.getHeightInCells() <= gridRows) {
                            d.setPosition(nx, ny);
                            if (!hasCollision(d, d)) { resolved = true; changed = true; break; }
                        }
                    }
                    if (!resolved) { cit.remove(); changed = true; }
                }
            }
        }

        // Remove landmarks that no longer fit (checks ROTATED AABB so rotated
        // landmarks whose visual footprint exceeds the new grid are dropped).
        java.util.Iterator<Landmark> lit = landmarks.iterator();
        double maxPixW = gridColumns * gridSize;
        double maxPixH = gridRows * gridSize;
        while (lit.hasNext()) {
            Landmark lm = lit.next();
            java.awt.geom.Rectangle2D rb = lm.getRotatedBounds(gridSize);
            if (rb.getX() < 0 || rb.getY() < 0 ||
                rb.getX() + rb.getWidth() > maxPixW ||
                rb.getY() + rb.getHeight() > maxPixH) {
                lit.remove();
            }
        }

        // Resolve landmark collisions similarly
        for (java.util.Iterator<Landmark> lcit = landmarks.iterator(); lcit.hasNext();) {
            Landmark lm = lcit.next();
            if (hasLandmarkCollision(lm, lm)) {
                boolean resolved = false;
                for (int[] nudge : new int[][]{{1,0},{0,1},{1,1},{-1,0},{0,-1}}) {
                    int nx = lm.getGridX() + nudge[0];
                    int ny = lm.getGridY() + nudge[1];
                    if (nx >= 0 && ny >= 0 &&
                        nx + lm.getGridW() <= gridColumns &&
                        ny + lm.getGridH() <= gridRows) {
                        lm.setPosition(nx, ny);
                        if (!hasLandmarkCollision(lm, lm)) { resolved = true; break; }
                    }
                }
                if (!resolved) lcit.remove();
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
