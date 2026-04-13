package seating.ui;

import seating.constraint.Constraint;
import seating.constraint.ConstraintSet;
import seating.model.*;
import seating.solver.SeatGraph;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Draws a green-yellow-red heat map overlay on each seat based on
 * how well the student assigned to that seat satisfies constraints.
 * Green = all constraints satisfied, red = heavily violated.
 */
public class HeatMapOverlay {

    /**
     * Draws heat map circles behind each occupied seat.
     *
     * @param g the Graphics2D context
     * @param arrangement the current seating assignment
     * @param constraints the active constraints
     * @param graph the seat adjacency graph
     * @param classroom the classroom for seat iteration
     */
    public static void draw(Graphics2D g, SeatingArrangement arrangement,
                             ConstraintSet constraints, SeatGraph graph,
                             Classroom classroom) {
        if (arrangement == null || constraints == null || graph == null) return;

        Stroke savedStroke = g.getStroke();
        Composite savedComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));

        for (Seat seat : classroom.getAllSeats()) {
            Student student = arrangement.getStudentAt(seat);
            if (student == null) continue;

            double score = computeSeatScore(student, seat, arrangement, constraints, graph);
            Color heatColor = scoreToColor(score);

            Point2D pos = seat.getGlobalPosition();
            int radius = 15;
            g.setColor(heatColor);
            g.fill(new Ellipse2D.Double(
                pos.getX() - radius, pos.getY() - radius,
                radius * 2, radius * 2));
        }

        g.setComposite(savedComp);
        g.setStroke(savedStroke);
    }

    /**
     * Computes the average constraint satisfaction score for a specific
     * student at a specific seat. Only considers constraints that involve
     * this student.
     */
    private static double computeSeatScore(Student student, Seat seat,
                                            SeatingArrangement arrangement,
                                            ConstraintSet constraints,
                                            SeatGraph graph) {
        List<Constraint> all = constraints.getAll();
        if (all.isEmpty()) return 1.0;

        double total = 0;
        int count = 0;

        for (Constraint c : all) {
            // Check if this constraint involves this student
            boolean involves = c.involvesStudent(student)
                || "balance".equals(c.getType());

            if (involves) {
                total += c.evaluate(arrangement, graph);
                count++;
            }
        }

        return count > 0 ? total / count : 1.0;
    }

    /**
     * Maps a score (0.0 to 1.0) to a green-yellow-red gradient.
     * 1.0 = bright green, 0.5 = yellow, 0.0 = red.
     */
    private static Color scoreToColor(double score) {
        score = Math.max(0.0, Math.min(1.0, score));
        int red, grn;
        if (score >= 0.5) {
            double t = (score - 0.5) * 2.0;
            red = (int)(255 * (1.0 - t));
            grn = 200;
        } else {
            double t = score * 2.0;
            red = 230;
            grn = (int)(200 * t);
        }
        return new Color(red, grn, 50);
    }
}
