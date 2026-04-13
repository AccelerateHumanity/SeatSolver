package seating.model;

import java.awt.geom.Point2D;

/**
 * Represents a single seat within a desk.
 * Each seat has a local position relative to its parent desk
 * and can compute its global position via the desk's transform.
 */
public class Seat {

    private String id;
    private double localX;
    private double localY;
    private Desk parentDesk;
    private String label;

    /**
     * Creates a new Seat with a local position relative to its desk.
     *
     * @param id unique identifier for this seat
     * @param localX x offset from the desk's origin in pixels
     * @param localY y offset from the desk's origin in pixels
     */
    public Seat(String id, double localX, double localY) {
        this.id = id;
        this.localX = localX;
        this.localY = localY;
        this.label = "";
    }

    /**
     * Returns the global position of this seat on the classroom canvas,
     * accounting for the parent desk's position and rotation.
     *
     * @return the global coordinates as a Point2D
     */
    public Point2D getGlobalPosition() {
        if (parentDesk == null) {
            return new Point2D.Double(localX, localY);
        }
        return parentDesk.getGlobalSeatPosition(this);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getLocalX() { return localX; }
    public double getLocalY() { return localY; }

    /**
     * Updates the local position of this seat relative to its desk.
     *
     * @param x x offset from desk origin
     * @param y y offset from desk origin
     */
    public void setLocalPosition(double x, double y) {
        this.localX = x;
        this.localY = y;
    }

    public Desk getParentDesk() { return parentDesk; }
    public void setParentDesk(Desk desk) { this.parentDesk = desk; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String toString() {
        return "Seat[" + id + "]";
    }

    public int hashCode() {
        return id.hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Seat)) return false;
        return id.equals(((Seat) obj).id);
    }
}
