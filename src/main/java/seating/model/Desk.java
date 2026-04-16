package seating.model;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Abstract base class for all desk types in a classroom.
 * Each desk has a position on the grid, a rotation angle,
 * and one or more seats. Subclasses define specific geometry
 * and seat layouts.
 *
 * <p>This class is the root of the desk inheritance hierarchy:
 * SingleDesk, PairDesk, GroupTable, LabBench, UShapeDesk.</p>
 */
public abstract class Desk {

    private static int nextId = 1;

    /** Resets the ID counter. Call when loading a new project. */
    public static void resetIdCounter() { nextId = 1; }

    private String id;
    private int gridX;
    private int gridY;
    private double rotation; // degrees
    private int gridSize = 40; // pixels per grid cell, synced from Classroom

    /**
     * Creates a new desk at the given grid position.
     *
     * @param gridX column position on the classroom grid
     * @param gridY row position on the classroom grid
     */
    public Desk(int gridX, int gridY) {
        this.id = "desk_" + nextId++;
        this.gridX = gridX;
        this.gridY = gridY;
        this.rotation = 0;
    }

    // ---- Abstract methods that subclasses MUST implement ----

    /**
     * Returns the list of seats belonging to this desk.
     * Seat positions are relative to the desk's origin.
     *
     * @return list of seats
     */
    public abstract List<Seat> getSeats();

    /**
     * Draws this desk on the given graphics context.
     * The graphics context is already translated/rotated
     * to the desk's position and orientation.
     *
     * @param g the Graphics2D context to draw on
     * @param gridSize the size of one grid cell in pixels
     */
    public abstract void draw(Graphics2D g, int gridSize);

    /**
     * Returns the width of this desk in grid cells.
     *
     * @return width in grid cells
     */
    public abstract int getWidthInCells();

    /**
     * Returns the height of this desk in grid cells.
     *
     * @return height in grid cells
     */
    public abstract int getHeightInCells();

    /**
     * Returns the number of seats this desk provides.
     *
     * @return seat count
     */
    public abstract int getSeatCount();

    /**
     * Returns a short type name for serialization.
     *
     * @return type name (e.g. "single", "pair", "group")
     */
    public abstract String getTypeName();

    // ---- Concrete methods (shared logic) ----

    /**
     * Computes the AffineTransform for this desk's position and rotation.
     *
     * @param gridSize the size of one grid cell in pixels
     * @return the transform to apply before drawing
     */
    public AffineTransform getTransform(int gridSize) {
        AffineTransform at = new AffineTransform();
        double px = gridX * gridSize;
        double py = gridY * gridSize;
        double cx = px + (getWidthInCells() * gridSize) / 2.0;
        double cy = py + (getHeightInCells() * gridSize) / 2.0;
        at.translate(cx, cy);
        at.rotate(Math.toRadians(rotation));
        at.translate(-cx, -cy);
        at.translate(px, py);
        return at;
    }

    /**
     * Returns the global position of a seat on the classroom canvas,
     * accounting for this desk's grid position and rotation.
     *
     * @param seat the seat whose global position to compute
     * @return global coordinates
     */
    public Point2D getGlobalSeatPosition(Seat seat) {
        AffineTransform at = getTransform(gridSize);
        Point2D local = new Point2D.Double(seat.getLocalX(), seat.getLocalY());
        Point2D global = new Point2D.Double();
        at.transform(local, global);
        return global;
    }

    /**
     * Returns the axis-aligned bounding box for collision detection.
     *
     * @param gridSize the size of one grid cell in pixels
     * @return bounding rectangle in global coordinates
     */
    public Rectangle2D getBounds(int gridSize) {
        double px = gridX * gridSize;
        double py = gridY * gridSize;
        double w = getWidthInCells() * gridSize;
        double h = getHeightInCells() * gridSize;
        return new Rectangle2D.Double(px, py, w, h);
    }

    /**
     * Returns the axis-aligned bounding box of the ROTATED desk shape.
     * For non-zero rotations, transforms the four corners through the
     * desk's AffineTransform and computes the enclosing AABB. Used by
     * collision + clamping so rotated desks can't clip off the grid or
     * overlap other items.
     */
    public Rectangle2D getRotatedBounds(int gridSize) {
        if (rotation == 0) return getBounds(gridSize);
        AffineTransform at = getTransform(gridSize);
        double w = getWidthInCells() * gridSize;
        double h = getHeightInCells() * gridSize;
        double[] corners = {0, 0, w, 0, w, h, 0, h};
        at.transform(corners, 0, corners, 0, 4);
        double minX = corners[0], maxX = corners[0];
        double minY = corners[1], maxY = corners[1];
        for (int i = 2; i < 8; i += 2) {
            minX = Math.min(minX, corners[i]);
            maxX = Math.max(maxX, corners[i]);
        }
        for (int i = 3; i < 8; i += 2) {
            minY = Math.min(minY, corners[i]);
            maxY = Math.max(maxY, corners[i]);
        }
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Checks whether this desk's bounding box overlaps another desk.
     *
     * @param other the other desk to check
     * @param gridSize the size of one grid cell in pixels
     * @return true if the desks overlap
     */
    public boolean collidesWith(Desk other, int gridSize) {
        if (other == this) return false;
        return getBounds(gridSize).intersects(other.getBounds(gridSize));
    }

    // ---- Getters and setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getGridSize() { return gridSize; }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }

    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }

    public void setPosition(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public double getRotation() { return rotation; }

    public void setRotation(double degrees) {
        this.rotation = degrees % 360;
        if (this.rotation < 0) this.rotation += 360;
    }

    /**
     * Rotates the desk by the given amount.
     *
     * @param degrees degrees to add to current rotation
     */
    public void rotate(double degrees) {
        setRotation(this.rotation + degrees);
    }

    public String toString() {
        return getTypeName() + "[" + id + " at (" + gridX + "," + gridY + ") rot=" + rotation + "]";
    }
}
