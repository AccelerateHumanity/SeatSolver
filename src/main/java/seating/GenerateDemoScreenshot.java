package seating;

import seating.constraint.ConstraintSet;
import seating.io.ProjectFile;
import seating.model.Classroom;
import seating.model.Desk;
import seating.model.SeatingArrangement;
import seating.model.Student;
import seating.solver.CSPSolver;
import seating.solver.SeatGraph;
import seating.solver.SolverResult;
import seating.ui.MainFrame;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Standalone utility that loads example_full_demo.json, runs the CSP solver,
 * and saves a screenshot of the resulting UI to seatsolver_screenshot.png.
 * Used by create_slides.js / create_writeup.js / create_prep_guide.js.
 *
 * <p>Run from the project root: {@code java -cp out seating.GenerateDemoScreenshot}.
 */
public final class GenerateDemoScreenshot {

    private GenerateDemoScreenshot() {}

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {}

                MainFrame frame = new MainFrame();
                frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                frame.setVisible(true);

                try {
                    File demoFile = new File("example_full_demo.json");
                    ProjectFile.LoadResult loaded = ProjectFile.load(demoFile);

                    // Wire the loaded state into the live MainFrame.
                    frame.loadProjectState(loaded);

                    // Lay out desks so seat global positions are set before scoring.
                    Classroom room = frame.getClassroom();
                    int gs = room.getGridSize();
                    BufferedImage scratch = new BufferedImage(
                        room.getPixelWidth(), room.getPixelHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = scratch.createGraphics();
                    for (Desk d : room.getDesks()) d.draw(g, gs);
                    g.dispose();

                    // Solve and apply the best arrangement.
                    ConstraintSet cs = frame.getConstraintSet();
                    SeatGraph graph = SeatGraph.buildFrom(room);
                    CSPSolver solver = new CSPSolver(cs, graph, 5, 5000);
                    SolverResult result = solver.solve(
                        frame.getStudentPanel().getStudents(), room.getAllSeats());
                    if (!result.isEmpty()) {
                        SeatingArrangement best = result.getBest();
                        frame.getClassroomPanel().setCurrentArrangement(best);
                        frame.getClassroomPanel().setConstraintData(cs, graph);
                        System.out.println("Solver: " + result.size() + " solutions, best score "
                            + String.format("%.2f", best.getScore()));
                    } else {
                        System.out.println("Solver found no arrangements — screenshot will have empty seats.");
                    }
                } catch (Exception loadEx) {
                    System.out.println("Demo load/solve failed: " + loadEx.getMessage());
                    loadEx.printStackTrace();
                }

                // Give Swing one paint cycle, then capture.
                javax.swing.Timer timer = new javax.swing.Timer(1500, new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        try {
                            BufferedImage shot = new BufferedImage(
                                frame.getWidth(), frame.getHeight(),
                                BufferedImage.TYPE_INT_RGB);
                            frame.paint(shot.getGraphics());
                            File out = new File("seatsolver_screenshot.png");
                            ImageIO.write(shot, "png", out);
                            System.out.println("Screenshot saved: " + out.getAbsolutePath());
                        } catch (Exception ex) {
                            System.out.println("Screenshot failed: " + ex.getMessage());
                            ex.printStackTrace();
                        } finally {
                            frame.dispose();
                            System.exit(0);
                        }
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }
}
