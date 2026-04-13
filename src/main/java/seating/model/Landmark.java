package seating.model;

import seating.ui.UIScale;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A non-seatable classroom feature: teacher's desk, door, screen/whiteboard, window, etc.
 * Landmarks are drawn on the canvas but don't contain seats.
 */
public class Landmark {

    /** Predefined landmark types. */
    public static final String TEACHER_DESK = "Teacher's Desk";
    public static final String DOOR = "Door";
    public static final String SCREEN = "Screen/Board";
    public static final String WINDOW = "Window";
    public static final String BOOKSHELF = "Bookshelf";

    private String label;
    private int gridX, gridY;
    private int gridW, gridH;
    private String type;
    private double rotation; // degrees

    /**
     * Creates a landmark.
     *
     * @param type the landmark type (TEACHER_DESK, DOOR, etc.)
     * @param gridX grid column
     * @param gridY grid row
     * @param gridW width in grid cells
     * @param gridH height in grid cells
     */
    public Landmark(String type, int gridX, int gridY, int gridW, int gridH) {
        this.type = type;
        this.label = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridW = gridW;
        this.gridH = gridH;
    }

    /**
     * Draws this landmark on the given graphics context.
     *
     * @param g the Graphics2D context
     * @param gs grid cell size in pixels
     */
    public void draw(Graphics2D g, int gs) {
        // Draw in LOCAL space (0,0). The transform in drawLandmarks handles positioning.
        int px = 0;
        int py = 0;
        int pw = gridW * gs;
        int ph = gridH * gs;

        Color fillColor, borderColor, textColor;
        String icon;

        if (TEACHER_DESK.equals(type)) {
            fillColor = new Color(139, 90, 43);
            borderColor = new Color(100, 65, 30);
            textColor = new Color(30, 30, 30);
            icon = "\u2302";
        } else if (DOOR.equals(type)) {
            fillColor = new Color(180, 130, 70);
            borderColor = new Color(120, 85, 45);
            textColor = new Color(30, 30, 30);
            icon = "\u25AF"; // rectangle
        } else if (SCREEN.equals(type)) {
            fillColor = new Color(220, 225, 230);
            borderColor = new Color(150, 155, 160);
            textColor = new Color(60, 60, 65);
            icon = "\u25A3"; // square with fill
        } else if (WINDOW.equals(type)) {
            fillColor = new Color(173, 216, 230, 150);
            borderColor = new Color(100, 149, 237);
            textColor = new Color(30, 60, 100);
            icon = "\u2600"; // sun
        } else {
            fillColor = new Color(200, 200, 205);
            borderColor = new Color(150, 150, 155);
            textColor = new Color(60, 60, 65);
            icon = "\u25A0";
        }

        // Draw body
        g.setColor(fillColor);
        g.fill(new RoundRectangle2D.Double(px + 1, py + 1, pw - 2, ph - 2, 6, 6));
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(px + 1, py + 1, pw - 2, ph - 2, 6, 6));

        // Draw diagonal hatch for non-seatable indication
        g.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 40));
        g.setStroke(new BasicStroke(1.0f));
        for (int i = -ph; i < pw; i += 8) {
            g.drawLine(px + Math.max(0, i), py + Math.max(0, -i),
                px + Math.min(pw, i + ph), py + Math.min(ph, -i + pw));
        }

        // Draw label — counter-rotate so text stays readable at any landmark rotation
        g.setColor(textColor);
        int fontSize = Math.min(11, Math.min(pw, ph) / 4 + 3);
        g.setFont(UIScale.font("SansSerif", Font.BOLD, Math.max(8, fontSize)));
        FontMetrics fm = g.getFontMetrics();

        // Counter-rotate around the center of the landmark so text is always upright
        java.awt.geom.AffineTransform saved = g.getTransform();
        double cx = pw / 2.0;
        double cy = ph / 2.0;
        g.rotate(-Math.toRadians(rotation), cx, cy);

        String displayLabel = label;
        // Fit text to the bounding box (use max of w/h since text is always horizontal)
        int fitW = Math.max(pw, ph) - 6;
        while (fm.stringWidth(displayLabel) > fitW && displayLabel.length() > 2) {
            displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
        }
        if (displayLabel.length() < label.length()) displayLabel += ".";
        int tw = fm.stringWidth(displayLabel);
        g.drawString(displayLabel, (int)(cx - tw / 2.0), (int)(cy + fm.getAscent() / 2.0 - 1));

        g.setTransform(saved);
    }

    // Getters and setters
    public String getType() { return type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public int getGridW() { return gridW; }
    public int getGridH() { return gridH; }

    public void setPosition(int gx, int gy) {
        this.gridX = gx;
        this.gridY = gy;
    }

    /** Rotates the landmark by the given degrees (same as desks). */
    public void rotate(double degrees) {
        rotation = (rotation + degrees) % 360;
        if (rotation < 0) rotation += 360;
    }

    public void rotate90() { rotate(90); }

    public double getRotation() { return rotation; }
    public void setRotation(double deg) {
        this.rotation = deg % 360;
        if (this.rotation < 0) this.rotation += 360;
    }

    /**
     * Returns the AffineTransform for this landmark's position and rotation.
     * draw() uses local (0,0) coords, so this handles all positioning + rotation.
     */
    public java.awt.geom.AffineTransform getTransform(int gs) {
        java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
        double px = gridX * gs;
        double py = gridY * gs;
        double cx = (gridW * gs) / 2.0;
        double cy = (gridH * gs) / 2.0;
        at.translate(px + cx, py + cy);
        at.rotate(Math.toRadians(rotation));
        at.translate(-cx, -cy);
        return at;
    }

    /** Returns the axis-aligned bounding box (ignores rotation). */
    public java.awt.geom.Rectangle2D getBounds(int gs) {
        return new java.awt.geom.Rectangle2D.Double(
            gridX * gs, gridY * gs, gridW * gs, gridH * gs);
    }

    /**
     * Tests if a point (in canvas pixel coords) falls inside this landmark,
     * accounting for rotation. Uses inverse transform to convert the click
     * point into the landmark's local coordinate space.
     */
    public boolean containsPoint(double px, double py, int gs) {
        if (rotation == 0) return getBounds(gs).contains(px, py);
        try {
            java.awt.geom.AffineTransform inv = getTransform(gs).createInverse();
            java.awt.geom.Point2D local = new java.awt.geom.Point2D.Double();
            inv.transform(new java.awt.geom.Point2D.Double(px, py), local);
            return local.getX() >= 0 && local.getX() <= gridW * gs
                && local.getY() >= 0 && local.getY() <= gridH * gs;
        } catch (java.awt.geom.NoninvertibleTransformException e) {
            return getBounds(gs).contains(px, py);
        }
    }

    public String toString() {
        return "Landmark[" + type + " at (" + gridX + "," + gridY + ") " + gridW + "x" + gridH + "]";
    }
}
