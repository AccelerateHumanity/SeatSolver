package seating.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A circular table with 6 seats evenly spaced around the perimeter.
 * Occupies a 2x2 grid area. Replaces the U-Shape desk with a more
 * common and intuitive round-table configuration.
 */
public class CircleTable extends Desk {

    private List<Seat> seats;

    /**
     * Creates a circle table at the given grid position.
     *
     * @param gridX column position
     * @param gridY row position
     */
    public CircleTable(int gridX, int gridY) {
        super(gridX, gridY);
        seats = new ArrayList<Seat>();
        for (int i = 1; i <= 6; i++) {
            Seat s = new Seat(getId() + "_s" + i, 0, 0);
            s.setParentDesk(this);
            seats.add(s);
        }
    }

    public List<Seat> getSeats() { return seats; }
    public int getWidthInCells() { return 4; }
    public int getHeightInCells() { return 4; }
    public int getSeatCount() { return 6; }
    public String getTypeName() { return "circle"; }

    public void draw(Graphics2D g, int gridSize) {
        int totalW = gridSize * getWidthInCells();
        int totalH = gridSize * getHeightInCells();
        double centerX = totalW / 2.0;
        double centerY = totalH / 2.0;

        // Table surface (circle)
        double tableRadius = Math.min(totalW, totalH) / 2.0 - 14;
        g.setColor(new Color(180, 140, 100));
        g.fill(new Ellipse2D.Double(
            centerX - tableRadius, centerY - tableRadius,
            tableRadius * 2, tableRadius * 2));
        g.setColor(new Color(120, 90, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Double(
            centerX - tableRadius, centerY - tableRadius,
            tableRadius * 2, tableRadius * 2));

        // Wood grain lines (subtle)
        g.setColor(new Color(140, 105, 70, 60));
        g.setStroke(new BasicStroke(0.5f));
        for (int i = -3; i <= 3; i++) {
            double offset = i * 8;
            double r2 = tableRadius - 4;
            g.drawLine(
                (int)(centerX + offset - r2 * 0.3), (int)(centerY - r2 * 0.7),
                (int)(centerX + offset + r2 * 0.3), (int)(centerY + r2 * 0.7));
        }

        // 6 seats around the perimeter, evenly spaced at 60-degree intervals
        int seatSize = totalW / 6;
        double seatRadius = tableRadius + seatSize * 0.4; // slightly outside the table edge

        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60 - 90); // start from top
            double sx = centerX + seatRadius * Math.cos(angle);
            double sy = centerY + seatRadius * Math.sin(angle);

            g.setColor(new Color(180, 190, 200));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Ellipse2D.Double(
                sx - seatSize / 2.0, sy - seatSize / 2.0,
                seatSize, seatSize));

            seats.get(i).setLocalPosition(sx, sy);
        }
    }
}
