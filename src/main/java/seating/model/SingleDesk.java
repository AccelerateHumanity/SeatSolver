package seating.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A single-student desk with one seat.
 * Smallest desk unit, occupies a 1x1 grid cell.
 */
public class SingleDesk extends Desk {

    private List<Seat> seats;

    /**
     * Creates a single desk at the given grid position.
     *
     * @param gridX column position
     * @param gridY row position
     */
    public SingleDesk(int gridX, int gridY) {
        super(gridX, gridY);
        seats = new ArrayList<Seat>();
        Seat s = new Seat(getId() + "_s1", 0, 0);
        s.setParentDesk(this);
        seats.add(s);
    }

    public List<Seat> getSeats() { return seats; }
    public int getWidthInCells() { return 2; }
    public int getHeightInCells() { return 2; }
    public int getSeatCount() { return 1; }
    public String getTypeName() { return "single"; }

    public void draw(Graphics2D g, int gridSize) {
        int w = gridSize * getWidthInCells();
        int h = gridSize * getHeightInCells();
        int deskH = (int)(h * 0.55);

        // Desk body (top portion)
        g.setColor(new Color(180, 140, 100));
        g.fill(new RoundRectangle2D.Double(2, 2, w - 4, deskH, 8, 8));
        g.setColor(new Color(120, 90, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(2, 2, w - 4, deskH, 8, 8));

        // Seat (empty chair outline) below the desk
        int seatSize = w / 3;
        double sx = w / 2.0;
        double sy = deskH + (h - deskH) / 2.0 + 2;
        g.setColor(new Color(180, 190, 200));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(sx - seatSize / 2.0, sy - seatSize / 2.0, seatSize, seatSize));

        seats.get(0).setLocalPosition(sx, sy);
    }
}
