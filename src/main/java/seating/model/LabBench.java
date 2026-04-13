package seating.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A lab bench with 3 seats along one side, like a science lab counter.
 * Students sit on the bottom edge facing the bench. Occupies 3x1 grid cells.
 */
public class LabBench extends Desk {

    private List<Seat> seats;

    /**
     * Creates a lab bench at the given grid position.
     *
     * @param gridX column position
     * @param gridY row position
     */
    public LabBench(int gridX, int gridY) {
        super(gridX, gridY);
        seats = new ArrayList<Seat>();
        for (int i = 1; i <= 3; i++) {
            Seat s = new Seat(getId() + "_s" + i, 0, 0);
            s.setParentDesk(this);
            seats.add(s);
        }
    }

    public List<Seat> getSeats() { return seats; }
    public int getWidthInCells() { return 6; }
    public int getHeightInCells() { return 2; }
    public int getSeatCount() { return 3; }
    public String getTypeName() { return "lab"; }

    public void draw(Graphics2D g, int gridSize) {
        int totalW = gridSize * getWidthInCells();
        int totalH = gridSize * getHeightInCells();

        // Bench surface (top portion)
        int benchH = (int)(totalH * 0.55);
        g.setColor(new Color(160, 160, 165));  // gray for lab surface
        g.fill(new RoundRectangle2D.Double(2, 2, totalW - 4, benchH, 6, 6));
        g.setColor(new Color(100, 100, 105));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(2, 2, totalW - 4, benchH, 6, 6));

        // Seats along bottom edge (3 evenly spaced)
        int seatSize = totalH / 3;
        double seatSpacing = totalW / 3.0;
        for (int i = 0; i < 3; i++) {
            double cx = seatSpacing * (i + 0.5);
            double cy = totalH - seatSize * 0.8;
            double drawX = cx - seatSize / 2.0;
            double drawY = cy - seatSize / 2.0;

            g.setColor(new Color(180, 190, 200));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Ellipse2D.Double(drawX, drawY, seatSize, seatSize));

            seats.get(i).setLocalPosition(cx, cy);
        }
    }
}
