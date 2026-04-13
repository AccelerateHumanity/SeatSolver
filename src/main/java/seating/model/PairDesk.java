package seating.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A two-student desk with seats side by side.
 * Occupies a 2x1 grid area.
 */
public class PairDesk extends Desk {

    private List<Seat> seats;

    /**
     * Creates a pair desk at the given grid position.
     *
     * @param gridX column position
     * @param gridY row position
     */
    public PairDesk(int gridX, int gridY) {
        super(gridX, gridY);
        seats = new ArrayList<Seat>();
        Seat s1 = new Seat(getId() + "_s1", 0, 0);
        Seat s2 = new Seat(getId() + "_s2", 0, 0);
        s1.setParentDesk(this);
        s2.setParentDesk(this);
        seats.add(s1);
        seats.add(s2);
    }

    public List<Seat> getSeats() { return seats; }
    public int getWidthInCells() { return 4; }
    public int getHeightInCells() { return 2; }
    public int getSeatCount() { return 2; }
    public String getTypeName() { return "pair"; }

    public void draw(Graphics2D g, int gridSize) {
        int totalW = gridSize * getWidthInCells();
        int totalH = gridSize * getHeightInCells();
        int deskH = (int)(totalH * 0.55);

        // Desk body (top portion)
        g.setColor(new Color(180, 140, 100));
        g.fill(new RoundRectangle2D.Double(2, 2, totalW - 4, deskH, 8, 8));
        g.setColor(new Color(120, 90, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(2, 2, totalW - 4, deskH, 8, 8));

        // Center divider
        g.setColor(new Color(140, 105, 70));
        g.setStroke(new BasicStroke(1.0f));
        g.drawLine(totalW / 2, 6, totalW / 2, deskH - 4);

        // Seats (chairs) below desk
        int seatSize = totalH / 3;
        double seatY = deskH + (totalH - deskH) / 2.0 + 2;
        double sx1 = totalW / 4.0;
        double sx2 = totalW * 3.0 / 4.0;

        g.setColor(new Color(180, 190, 200));
        g.setStroke(new BasicStroke(1.2f));
        for (double sx : new double[]{sx1, sx2}) {
            g.draw(new Ellipse2D.Double(sx - seatSize / 2.0, seatY - seatSize / 2.0, seatSize, seatSize));
        }

        seats.get(0).setLocalPosition(sx1, seatY);
        seats.get(1).setLocalPosition(sx2, seatY);
    }
}
