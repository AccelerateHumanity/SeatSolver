package seating.model;

import java.awt.Color;
import java.awt.geom.Rectangle2D;

/**
 * Represents a rectangular zone in the classroom (e.g. "Front", "Back", "Window").
 * Zones are used by ZoneConstraints to restrict where students can sit.
 */
public class Zone {

    private String label;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private Color color;

    /**
     * Creates a new zone.
     *
     * @param label descriptive name (e.g. "Front", "Window Side")
     * @param gridX starting column
     * @param gridY starting row
     * @param gridWidth width in grid cells
     * @param gridHeight height in grid cells
     * @param color display color (will be drawn with transparency)
     */
    public Zone(String label, int gridX, int gridY, int gridWidth, int gridHeight, Color color) {
        this.label = label;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.color = color;
    }

    /**
     * Returns the bounding rectangle of this zone in pixel coordinates.
     *
     * @param gridSize the size of one grid cell in pixels
     * @return the zone bounds
     */
    public Rectangle2D getBounds(int gridSize) {
        return new Rectangle2D.Double(
            gridX * gridSize, gridY * gridSize,
            gridWidth * gridSize, gridHeight * gridSize
        );
    }

    /**
     * Checks whether a global point falls within this zone.
     *
     * @param px x coordinate in pixels
     * @param py y coordinate in pixels
     * @param gridSize the grid cell size
     * @return true if the point is inside this zone
     */
    public boolean contains(double px, double py, int gridSize) {
        return getBounds(gridSize).contains(px, py);
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

    public String toString() {
        return "Zone[" + label + " (" + gridX + "," + gridY + ") " + gridWidth + "x" + gridHeight + "]";
    }
}
