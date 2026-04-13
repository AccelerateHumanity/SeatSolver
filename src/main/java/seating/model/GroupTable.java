package seating.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A group table with 4 seats arranged around a rectangular table.
 * Two seats on top, two on bottom. Occupies a 2x2 grid area.
 */
public class GroupTable extends Desk {

    private List<Seat> seats;

    /**
     * Creates a group table at the given grid position.
     *
     * @param gridX column position
     * @param gridY row position
     */
    public GroupTable(int gridX, int gridY) {
        super(gridX, gridY);
        seats = new ArrayList<Seat>();
        for (int i = 1; i <= 4; i++) {
            Seat s = new Seat(getId() + "_s" + i, 0, 0);
            s.setParentDesk(this);
            seats.add(s);
        }
    }

    public List<Seat> getSeats() { return seats; }
    public int getWidthInCells() { return 4; }
    public int getHeightInCells() { return 4; }
    public int getSeatCount() { return 4; }
    public String getTypeName() { return "group"; }

    public void draw(Graphics2D g, int gridSize) {
        int totalW = gridSize * getWidthInCells();
        int totalH = gridSize * getHeightInCells();
        int seatSize = totalW / 6;
        int margin = seatSize + 4;

        // Table body (centered, leaving room for chairs)
        g.setColor(new Color(180, 140, 100));
        g.fill(new RoundRectangle2D.Double(margin / 2, margin / 2,
            totalW - margin, totalH - margin, 10, 10));
        g.setColor(new Color(120, 90, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(margin / 2, margin / 2,
            totalW - margin, totalH - margin, 10, 10));

        // 4 chairs OUTSIDE the table: 2 above, 2 below
        double[][] positions = {
            { totalW * 0.25,  seatSize * 0.5 },
            { totalW * 0.75,  seatSize * 0.5 },
            { totalW * 0.25,  totalH - seatSize * 0.5 },
            { totalW * 0.75,  totalH - seatSize * 0.5 }
        };

        g.setColor(new Color(180, 190, 200));
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 4; i++) {
            double cx = positions[i][0];
            double cy = positions[i][1];
            g.draw(new Ellipse2D.Double(cx - seatSize / 2.0, cy - seatSize / 2.0, seatSize, seatSize));
            seats.get(i).setLocalPosition(cx, cy);
        }
    }
}
