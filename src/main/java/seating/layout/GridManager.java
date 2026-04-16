package seating.layout;

import seating.model.Classroom;
import seating.model.Desk;
import seating.model.Landmark;

/**
 * Handles grid snapping and collision detection for desk placement.
 * Part of the spatial layout engine (Person 2's algorithm).
 */
public class GridManager {

    private Classroom classroom;

    /**
     * Creates a GridManager for the given classroom.
     *
     * @param classroom the classroom to manage
     */
    public GridManager(Classroom classroom) {
        this.classroom = classroom;
    }

    /**
     * Snaps pixel coordinates to the nearest grid cell.
     *
     * @param pixelX raw pixel x coordinate
     * @param pixelY raw pixel y coordinate
     * @return array of [gridX, gridY]
     */
    public int[] snapToGrid(int pixelX, int pixelY) {
        int gs = classroom.getGridSize();
        int gx = Math.round((float) pixelX / gs);
        int gy = Math.round((float) pixelY / gs);
        return new int[] { gx, gy };
    }

    /**
     * Clamps a desk's grid position to stay within classroom bounds.
     *
     * @param desk the desk to clamp
     * @param gx proposed grid x
     * @param gy proposed grid y
     * @return array of [clampedGx, clampedGy]
     */
    public int[] clampToClassroom(Desk desk, int gx, int gy) {
        // Rotation-aware clamp: use getRotatedBounds so a rotated desk's
        // actual visual footprint stays inside the grid.
        int gs = classroom.getGridSize();
        int cols = classroom.getGridColumns();
        int rows = classroom.getGridRows();
        int origX = desk.getGridX();
        int origY = desk.getGridY();
        desk.setPosition(gx, gy);
        java.awt.geom.Rectangle2D rb = desk.getRotatedBounds(gs);
        desk.setPosition(origX, origY);

        int rbLeft = (int) Math.floor(rb.getX() / gs);
        int rbTop = (int) Math.floor(rb.getY() / gs);
        int rbRight = (int) Math.ceil((rb.getX() + rb.getWidth()) / gs);
        int rbBottom = (int) Math.ceil((rb.getY() + rb.getHeight()) / gs);

        int shiftX = 0;
        if (rbLeft < 0) shiftX = -rbLeft;
        else if (rbRight > cols) shiftX = cols - rbRight;

        int shiftY = 0;
        if (rbTop < 0) shiftY = -rbTop;
        else if (rbBottom > rows) shiftY = rows - rbBottom;

        return new int[] { gx + shiftX, gy + shiftY };
    }

    /**
     * Clamps a landmark's grid position so its ROTATED footprint stays
     * fully within the classroom. Uses {@link Landmark#getRotatedBounds(int)}
     * so rotated landmarks are clamped by their true AABB.
     *
     * @param lm the landmark
     * @param gx proposed grid x
     * @param gy proposed grid y
     * @return array of [clampedGx, clampedGy]
     */
    public int[] clampLandmarkToClassroom(Landmark lm, int gx, int gy) {
        int gs = classroom.getGridSize();
        int cols = classroom.getGridColumns();
        int rows = classroom.getGridRows();

        // Probe rotated AABB as if landmark were at (gx, gy)
        int origX = lm.getGridX();
        int origY = lm.getGridY();
        lm.setPosition(gx, gy);
        java.awt.geom.Rectangle2D rb = lm.getRotatedBounds(gs);
        lm.setPosition(origX, origY);

        int rbLeft = (int) Math.floor(rb.getX() / gs);
        int rbTop = (int) Math.floor(rb.getY() / gs);
        int rbRight = (int) Math.ceil((rb.getX() + rb.getWidth()) / gs);
        int rbBottom = (int) Math.ceil((rb.getY() + rb.getHeight()) / gs);

        // Shift the landmark so the rotated AABB falls inside [0, cols] x [0, rows]
        int shiftX = 0;
        if (rbLeft < 0) shiftX = -rbLeft;
        else if (rbRight > cols) shiftX = cols - rbRight;

        int shiftY = 0;
        if (rbTop < 0) shiftY = -rbTop;
        else if (rbBottom > rows) shiftY = rows - rbBottom;

        return new int[] { gx + shiftX, gy + shiftY };
    }

    /**
     * Checks if placing a desk at the proposed position would collide
     * with any existing desk (excluding itself).
     *
     * @param desk the desk to check
     * @param proposedGx proposed grid x
     * @param proposedGy proposed grid y
     * @return true if the position is free (no collision)
     */
    public boolean isPositionFree(Desk desk, int proposedGx, int proposedGy) {
        int origX = desk.getGridX();
        int origY = desk.getGridY();
        desk.setPosition(proposedGx, proposedGy);
        boolean free = !classroom.hasCollision(desk, desk);
        desk.setPosition(origX, origY);
        return free;
    }

    /**
     * Attempts to place a desk at the given grid position.
     * Returns true if placement succeeded (no collision).
     *
     * @param desk the desk to place
     * @param gx target grid x
     * @param gy target grid y
     * @return true if placed successfully
     */
    public boolean tryPlace(Desk desk, int gx, int gy) {
        int[] clamped = clampToClassroom(desk, gx, gy);
        if (isPositionFree(desk, clamped[0], clamped[1])) {
            desk.setPosition(clamped[0], clamped[1]);
            return true;
        }
        return false;
    }
}
