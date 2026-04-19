package seating.model;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Represents a (possibly rotated) rectangular zone in the classroom
 * (e.g. "Front", "Back", "Window"). Zones are used by ZoneConstraints
 * to restrict where students can sit.
 */
public class Zone {

    private String label;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private Color color;
    private double rotation; // degrees

    public Zone(String label, int gridX, int gridY, int gridWidth, int gridHeight, Color color) {
        this.label = label;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.color = color;
        this.rotation = 0;
    }

    /**
     * Returns the AffineTransform for this zone's position and rotation.
     * Rotates around the zone's center.
     */
    public AffineTransform getTransform(int gridSize) {
        AffineTransform at = new AffineTransform();
        double px = gridX * gridSize;
        double py = gridY * gridSize;
        double cx = (gridWidth * gridSize) / 2.0;
        double cy = (gridHeight * gridSize) / 2.0;
        at.translate(px + cx, py + cy);
        at.rotate(Math.toRadians(rotation));
        at.translate(-cx, -cy);
        return at;
    }

    /**
     * Returns the axis-aligned bounding box (ignores rotation).
     */
    public Rectangle2D getBounds(int gridSize) {
        if (rotation == 0) {
            return new Rectangle2D.Double(
                gridX * gridSize, gridY * gridSize,
                gridWidth * gridSize, gridHeight * gridSize);
        }
        // Rotated: compute AABB of the 4 transformed corners
        AffineTransform at = getTransform(gridSize);
        double w = gridWidth * gridSize;
        double h = gridHeight * gridSize;
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
     * Checks whether a global point falls within this zone,
     * accounting for rotation via inverse transform.
     */
    public boolean contains(double px, double py, int gridSize) {
        if (rotation == 0) {
            return new Rectangle2D.Double(
                gridX * gridSize, gridY * gridSize,
                gridWidth * gridSize, gridHeight * gridSize).contains(px, py);
        }
        try {
            AffineTransform inv = getTransform(gridSize).createInverse();
            Point2D local = inv.transform(new Point2D.Double(px, py), null);
            return local.getX() >= 0 && local.getX() <= gridWidth * gridSize
                && local.getY() >= 0 && local.getY() <= gridHeight * gridSize;
        } catch (NoninvertibleTransformException e) {
            return false;
        }
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }

    public void setBounds(int gx, int gy, int gw, int gh) {
        this.gridX = gx;
        this.gridY = gy;
        this.gridWidth = gw;
        this.gridHeight = gh;
    }

    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    public double getRotation() { return rotation; }
    public void setRotation(double degrees) {
        this.rotation = degrees % 360;
        if (this.rotation < 0) this.rotation += 360;
    }
    public void rotate(double degrees) {
        setRotation(this.rotation + degrees);
    }

    public String toString() {
        return "Zone[" + label + " (" + gridX + "," + gridY + ") " + gridWidth + "x" + gridHeight
            + (rotation != 0 ? " @" + (int) rotation + "\u00B0" : "") + "]";
    }
}
