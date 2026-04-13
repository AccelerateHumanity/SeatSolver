package seating.ui;

import seating.constraint.*;
import seating.model.*;
import seating.solver.SeatGraph;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Draws dashed red lines between students whose seating violates
 * proximity constraints. Provides immediate visual feedback on
 * which rules are broken in the current arrangement.
 */
public class ConflictOverlay {

    /**
     * Draws conflict lines on the given graphics context.
     *
     * @param g the Graphics2D context
     * @param arrangement the current seating arrangement
     * @param constraints the active constraints
     * @param graph the seat adjacency graph
     */
    public static void draw(Graphics2D g, SeatingArrangement arrangement,
                             ConstraintSet constraints, SeatGraph graph) {
        if (arrangement == null || constraints == null || graph == null) return;

        // Save graphics state
        Stroke savedStroke = g.getStroke();
        Color savedColor = g.getColor();
        Composite savedComposite = g.getComposite();

        // Dashed red line style
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10.0f, new float[]{8.0f, 6.0f}, 0.0f));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

        List<Constraint> violations = constraints.getViolations(arrangement, graph);

        for (Constraint c : violations) {
            if (c instanceof ProximityConstraint) {
                ProximityConstraint pc = (ProximityConstraint) c;
                Seat seatA = arrangement.getSeatOf(pc.getStudentA());
                Seat seatB = arrangement.getSeatOf(pc.getStudentB());

                if (seatA != null && seatB != null) {
                    Point2D posA = seatA.getGlobalPosition();
                    Point2D posB = seatB.getGlobalPosition();

                    // Red line for "apart" violations, orange for "together" violations
                    if (ProximityConstraint.APART.equals(pc.getMode())) {
                        g.setColor(new Color(220, 50, 50));
                    } else {
                        g.setColor(new Color(230, 126, 34));
                    }

                    g.draw(new Line2D.Double(posA.getX(), posA.getY(),
                        posB.getX(), posB.getY()));

                    // Draw X marks at midpoint
                    double mx = (posA.getX() + posB.getX()) / 2;
                    double my = (posA.getY() + posB.getY()) / 2;
                    g.setStroke(new BasicStroke(2.5f));
                    int xSize = 6;
                    g.draw(new Line2D.Double(mx - xSize, my - xSize, mx + xSize, my + xSize));
                    g.draw(new Line2D.Double(mx - xSize, my + xSize, mx + xSize, my - xSize));

                    // Reset to dashed for next line
                    g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10.0f, new float[]{8.0f, 6.0f}, 0.0f));
                }
            }

            if (c instanceof ZoneConstraint) {
                ZoneConstraint zc = (ZoneConstraint) c;
                Seat seat = arrangement.getSeatOf(zc.getStudent());
                if (seat != null) {
                    Point2D pos = seat.getGlobalPosition();
                    // Draw a warning circle around the misplaced student
                    g.setColor(new Color(220, 50, 50));
                    g.setStroke(new BasicStroke(2.0f));
                    int radius = 15;
                    g.drawOval((int)(pos.getX() - radius), (int)(pos.getY() - radius),
                        radius * 2, radius * 2);
                }
            }
        }

        // Restore graphics state
        g.setStroke(savedStroke);
        g.setColor(savedColor);
        g.setComposite(savedComposite);
    }
}
