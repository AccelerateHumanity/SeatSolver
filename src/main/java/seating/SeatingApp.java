package seating;

import seating.ui.MainFrame;
import seating.ui.UIScale;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point for the SeatSolver application.
 * A customizable classroom seating arrangement generator with
 * drag-and-drop desk placement and constraint-based auto-assignment.
 *
 * @author SeatSolver Team
 * @version 1.0
 */
public class SeatingApp {

    /**
     * Launches the SeatSolver application.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        // Set system look and feel for a native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default look and feel
        }

        // HiDPI: floor every default Swing font at 13pt (× DPI on legacy JVMs)
        // so JFileChooser, JOptionPane, tooltips, menus, and any widget that
        // doesn't go through UIScale.font(...) inherit a readable size on 4K.
        UIScale.installUIDefaults(13);

        // Launch on the Event Dispatch Thread
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            }
        });
    }
}
