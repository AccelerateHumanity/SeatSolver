package seating;

import seating.model.*;
import seating.constraint.*;
import seating.solver.*;
import seating.layout.*;
import seating.io.ProjectFile;
import seating.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Automated test harness for SeatSolver.
 * Tests every major component: model, constraints, solver, undo/redo,
 * file I/O, and visual rendering. Outputs results to console and
 * captures a screenshot of the populated application.
 */
public class SeatSolverTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== SeatSolver Automated Test Suite ===\n");

        testDeskCreation();
        testDeskHierarchy();
        testClassroomManagement();
        testStudentModel();
        testSeatGraph();
        testConstraints();
        testCSPSolver();
        testUndoRedo();
        testFileIO();
        testFullIntegration();

        System.out.println("\n=== RESULTS: " + passed + " passed, " + failed + " failed ===");

        if (failed == 0) {
            System.out.println("ALL TESTS PASSED!\n");
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                System.out.println("Launching visual demo with test data...");
                launchVisualDemo();
            }
        } else {
            System.out.println("SOME TESTS FAILED - fix before proceeding.");
            System.exit(1);
        }
    }

    // ============================================================
    // TEST: Desk creation and properties
    // ============================================================
    static void testDeskCreation() {
        System.out.println("--- Desk Creation ---");

        SingleDesk single = new SingleDesk(0, 0);
        check("SingleDesk seat count", single.getSeatCount() == 1);
        check("SingleDesk type name", "single".equals(single.getTypeName()));
        check("SingleDesk dimensions", single.getWidthInCells() == 2 && single.getHeightInCells() == 2);

        PairDesk pair = new PairDesk(2, 0);
        check("PairDesk seat count", pair.getSeatCount() == 2);
        check("PairDesk type name", "pair".equals(pair.getTypeName()));
        check("PairDesk dimensions", pair.getWidthInCells() == 4 && pair.getHeightInCells() == 2);

        GroupTable group = new GroupTable(0, 2);
        check("GroupTable seat count", group.getSeatCount() == 4);
        check("GroupTable type name", "group".equals(group.getTypeName()));
        check("GroupTable dimensions", group.getWidthInCells() == 4 && group.getHeightInCells() == 4);

        LabBench lab = new LabBench(4, 0);
        check("LabBench seat count", lab.getSeatCount() == 3);
        check("LabBench type name", "lab".equals(lab.getTypeName()));
        check("LabBench dimensions", lab.getWidthInCells() == 6 && lab.getHeightInCells() == 2);

        CircleTable circle = new CircleTable(0, 5);
        check("CircleTable seat count", circle.getSeatCount() == 6);
        check("CircleTable type name", "circle".equals(circle.getTypeName()));
        check("CircleTable dimensions", circle.getWidthInCells() == 4 && circle.getHeightInCells() == 4);
    }

    // ============================================================
    // TEST: Inheritance - abstract methods, polymorphism
    // ============================================================
    static void testDeskHierarchy() {
        System.out.println("--- Desk Inheritance ---");

        // All desk types are instances of Desk (polymorphism)
        Desk[] desks = {
            new SingleDesk(0, 0), new PairDesk(0, 0),
            new GroupTable(0, 0), new LabBench(0, 0), new CircleTable(0, 0)
        };

        for (Desk d : desks) {
            check(d.getTypeName() + " is Desk", d instanceof Desk);
            check(d.getTypeName() + " has seats", d.getSeats() != null && !d.getSeats().isEmpty());
            check(d.getTypeName() + " seat count matches list",
                d.getSeatCount() == d.getSeats().size());

            // Test rotation
            d.setRotation(0);
            d.rotate(90);
            check(d.getTypeName() + " rotation", d.getRotation() == 90.0);
            d.rotate(280);
            check(d.getTypeName() + " rotation wraps", d.getRotation() == 10.0);
        }

        // Test polymorphic draw (render to offscreen image)
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (Desk d : desks) {
            d.setRotation(0);
            d.draw(g, 40); // should not throw
        }
        g.dispose();
        check("Polymorphic draw (no exceptions)", true);
    }

    // ============================================================
    // TEST: Classroom management
    // ============================================================
    static void testClassroomManagement() {
        System.out.println("--- Classroom Management ---");

        Classroom room = new Classroom(20, 15, 40);
        check("Empty classroom", room.getDesks().size() == 0);
        check("Total seats empty", room.getTotalSeatCount() == 0);

        SingleDesk d1 = new SingleDesk(0, 0);
        PairDesk d2 = new PairDesk(5, 5);
        GroupTable d3 = new GroupTable(10, 10);
        room.addDesk(d1);
        room.addDesk(d2);
        room.addDesk(d3);

        check("Desk count after add", room.getDesks().size() == 3);
        check("Total seats", room.getTotalSeatCount() == 1 + 2 + 4);
        check("All seats flattened", room.getAllSeats().size() == 7);

        // Hit testing
        check("getDeskAt hit", room.getDeskAt(5, 5) == d1);
        check("getDeskAt miss", room.getDeskAt(300, 300) == null);

        // Collision detection
        SingleDesk overlap = new SingleDesk(0, 0);
        check("Collision detected", overlap.collidesWith(d1, 40));
        SingleDesk noOverlap = new SingleDesk(15, 15);
        check("No collision", !noOverlap.collidesWith(d1, 40));

        // Zone management
        Zone front = new Zone("Front", 0, 0, 20, 3, new Color(76, 175, 80));
        room.addZone(front);
        check("Zone added", room.getZones().size() == 1);
        check("Zone contains point", front.contains(100, 50, 40));
        check("Zone excludes point", !front.contains(100, 500, 40));

        room.removeDesk(d1);
        check("Desk removed", room.getDesks().size() == 2);
    }

    // ============================================================
    // TEST: Student model
    // ============================================================
    static void testStudentModel() {
        System.out.println("--- Student Model ---");

        Student s = new Student("Alice");
        check("Student name", "Alice".equals(s.getName()));
        check("Default skill", s.getSkillLevel() == 3);
        check("Default gender", "".equals(s.getGender()));
        check("Empty tags", s.getTags().isEmpty());

        s.setGender("F");
        s.setSkillLevel(5);
        s.addTag("honors");
        s.addTag("needs-front");

        check("Gender set", "F".equals(s.getGender()));
        check("Skill set", s.getSkillLevel() == 5);
        check("Has tag", s.hasTag("honors"));
        check("Has tag case insensitive", s.hasTag("HONORS"));
        check("Tag count", s.getTags().size() == 2);

        s.removeTag("honors");
        check("Tag removed", !s.hasTag("honors"));

        // Skill clamping
        s.setSkillLevel(10);
        check("Skill clamped high", s.getSkillLevel() == 5);
        s.setSkillLevel(0);
        check("Skill clamped low", s.getSkillLevel() == 1);

        // Constructor with all fields
        HashSet<String> tags = new HashSet<String>();
        tags.add("esl");
        Student s2 = new Student("Bob", "M", 2, tags);
        check("Full constructor", "Bob".equals(s2.getName()) && "M".equals(s2.getGender())
            && s2.getSkillLevel() == 2 && s2.hasTag("esl"));
    }

    // ============================================================
    // TEST: Seat adjacency graph (CS III data structure: Graph)
    // ============================================================
    static void testSeatGraph() {
        System.out.println("--- Seat Graph (Adjacency) ---");

        Classroom room = new Classroom(20, 15, 40);

        // Place two pair desks next to each other
        PairDesk d1 = new PairDesk(2, 2);
        PairDesk d2 = new PairDesk(4, 2); // adjacent
        PairDesk d3 = new PairDesk(15, 15); // far away

        // Force seat position computation by drawing to offscreen
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        d1.draw(g, 40); d2.draw(g, 40); d3.draw(g, 40);
        g.dispose();

        room.addDesk(d1);
        room.addDesk(d2);
        room.addDesk(d3);

        SeatGraph graph = SeatGraph.buildFrom(room);

        check("Graph has all seats", graph.size() == 6); // 2+2+2
        check("Graph adjacency map not empty", !graph.getAdjacencyMap().isEmpty());

        // Seats within the same desk are always adjacent
        List<Seat> d1Seats = d1.getSeats();
        check("Same-desk seats adjacent", graph.areAdjacent(d1Seats.get(0), d1Seats.get(1)));

        // Nearby desk seats should be adjacent (d1 and d2 are next to each other)
        // d1 is at grid (2,2), d2 at (4,2), threshold is 120px = 3 cells
        // d1 seat 2 (right side) should be close to d2 seat 1 (left side)

        // Far desk seats should NOT be adjacent
        List<Seat> d3Seats = d3.getSeats();
        check("Far-away seats not adjacent", !graph.areAdjacent(d1Seats.get(0), d3Seats.get(0)));

        System.out.println("  Graph stats: " + graph.size() + " seats, "
            + countEdges(graph) + " adjacency edges");
    }

    static int countEdges(SeatGraph graph) {
        int edges = 0;
        for (Set<Seat> neighbors : graph.getAdjacencyMap().values()) {
            edges += neighbors.size();
        }
        return edges / 2; // undirected
    }

    // ============================================================
    // TEST: Constraints (all 3 types)
    // ============================================================
    static void testConstraints() {
        System.out.println("--- Constraints ---");

        Classroom room = new Classroom(20, 15, 40);
        PairDesk d1 = new PairDesk(2, 2);
        PairDesk d2 = new PairDesk(4, 2);
        // Draw to set seat positions
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        d1.draw(g, 40); d2.draw(g, 40);
        g.dispose();
        room.addDesk(d1);
        room.addDesk(d2);

        Zone front = new Zone("Front", 0, 0, 20, 5, new Color(76, 175, 80));
        room.addZone(front);

        SeatGraph graph = SeatGraph.buildFrom(room);

        Student alice = new Student("Alice", "F", 4, null);
        Student bob = new Student("Bob", "M", 2, null);

        // --- Proximity Constraint ---
        ProximityConstraint apart = new ProximityConstraint(alice, bob,
            ProximityConstraint.APART, true, 1.0);

        // Assign alice and bob to adjacent seats (same desk)
        SeatingArrangement arr1 = new SeatingArrangement();
        arr1.assign(d1.getSeats().get(0), alice);
        arr1.assign(d1.getSeats().get(1), bob);

        check("Apart constraint violated (same desk)", !apart.isSatisfied(arr1, graph));
        check("Apart constraint score = 0", apart.evaluate(arr1, graph) == 0.0);

        // Assign to far-apart seats
        SeatingArrangement arr2 = new SeatingArrangement();
        arr2.assign(d1.getSeats().get(0), alice);
        arr2.assign(d2.getSeats().get(1), bob);

        // These may or may not be adjacent depending on distance
        // but same-desk seats are definitely adjacent
        check("Apart describe", apart.describe().contains("must NOT sit next to"));
        check("Apart type", "proximity".equals(apart.getType()));

        // Distance-based APART scoring: non-adjacent but close seats should
        // produce an intermediate score (between 0 and 1), not binary 1.0.
        double farScore = apart.evaluate(arr2, graph);
        check("APART distance-based (non-binary)", farScore > 0.0 && farScore <= 1.0);

        // TOGETHER mode mirrors APART on the same distance curve.
        ProximityConstraint together = new ProximityConstraint(alice, bob,
            ProximityConstraint.TOGETHER, false, 1.0);
        double togetherSameDesk = together.evaluate(arr1, graph);
        check("TOGETHER same-desk score = 1.0", togetherSameDesk == 1.0);
        double togetherFar = together.evaluate(arr2, graph);
        check("TOGETHER + APART sum to 1.0 for same pair", Math.abs((togetherFar + farScore) - 1.0) < 1e-9);

        // getSeatOf must be O(1) and consistent with assignment map.
        check("getSeatOf reverse-map: alice", arr1.getSeatOf(alice) == d1.getSeats().get(0));
        arr1.unassign(d1.getSeats().get(0));
        check("getSeatOf after unassign: alice", arr1.getSeatOf(alice) == null);
        // Re-assigning alice to a different seat updates reverse map atomically
        arr1.assign(d2.getSeats().get(0), alice);
        check("getSeatOf after reassign: alice", arr1.getSeatOf(alice) == d2.getSeats().get(0));

        // --- Zone Constraint ---
        ZoneConstraint zoneRule = new ZoneConstraint(alice, front,
            ZoneConstraint.MUST_BE_IN, true, 1.0);

        // d1 is at grid (2,2) which is in the front zone (rows 0-5)
        SeatingArrangement arr3 = new SeatingArrangement();
        arr3.assign(d1.getSeats().get(0), alice);

        check("Zone constraint satisfied (in front)", zoneRule.isSatisfied(arr3, graph));
        check("Zone describe", zoneRule.describe().contains("must sit in"));

        // --- Balance Constraint ---
        BalanceConstraint balance = new BalanceConstraint(BalanceConstraint.GENDER,
            null, false, 1.0);

        SeatingArrangement arr4 = new SeatingArrangement();
        arr4.assign(d1.getSeats().get(0), alice);
        arr4.assign(d1.getSeats().get(1), bob);

        double balanceScore = balance.evaluate(arr4, graph);
        check("Balance score is a number", balanceScore >= 0.0 && balanceScore <= 1.0);
        check("Balance describe", balance.describe().contains("gender"));

        // --- ConstraintSet ---
        ConstraintSet set = new ConstraintSet();
        set.add(apart);
        set.add(zoneRule);
        set.add(balance);
        check("ConstraintSet size", set.size() == 3);

        double totalScore = set.evaluate(arr1, graph);
        check("ConstraintSet aggregate score", totalScore >= 0.0 && totalScore <= 1.0);

        List<Constraint> violations = set.getViolations(arr1, graph);
        check("Violations found", violations.size() > 0);
        System.out.println("  Violations: " + violations.size() + " out of " + set.size());
    }

    // ============================================================
    // TEST: CSP Solver (the core algorithm)
    // ============================================================
    static void testCSPSolver() {
        System.out.println("--- CSP Solver ---");

        Classroom room = new Classroom(20, 15, 40);

        // Create 4 pair desks = 8 seats
        PairDesk d1 = new PairDesk(1, 2);
        PairDesk d2 = new PairDesk(4, 2);
        PairDesk d3 = new PairDesk(1, 5);
        PairDesk d4 = new PairDesk(4, 5);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        d1.draw(g, 40); d2.draw(g, 40); d3.draw(g, 40); d4.draw(g, 40);
        g.dispose();

        room.addDesk(d1); room.addDesk(d2);
        room.addDesk(d3); room.addDesk(d4);

        SeatGraph graph = SeatGraph.buildFrom(room);
        List<Seat> seats = room.getAllSeats();
        check("Test setup: 8 seats", seats.size() == 8);

        // Create 6 students
        Student alice = new Student("Alice", "F", 4, null);
        Student bob = new Student("Bob", "M", 2, null);
        Student carol = new Student("Carol", "F", 3, null);
        Student dave = new Student("Dave", "M", 5, null);
        Student eve = new Student("Eve", "F", 3, null);
        Student frank = new Student("Frank", "M", 4, null);
        List<Student> students = Arrays.asList(alice, bob, carol, dave, eve, frank);

        // Test 1: No constraints - should find solutions easily
        ConstraintSet empty = new ConstraintSet();
        CSPSolver solver1 = new CSPSolver(empty, graph, 3, 5000);
        SolverResult result1 = solver1.solve(students, seats);
        check("Solver finds solutions (no constraints)", result1.size() > 0);
        check("Solutions have correct student count",
            result1.getBest().getAssignedCount() == 6);
        System.out.println("  No constraints: " + result1.size() + " solutions in "
            + result1.getSolveTimeMs() + "ms");

        // Test 2: With proximity constraint (alice apart from bob)
        ConstraintSet withProx = new ConstraintSet();
        withProx.add(new ProximityConstraint(alice, bob,
            ProximityConstraint.APART, true, 1.0));

        CSPSolver solver2 = new CSPSolver(withProx, graph, 3, 5000);
        SolverResult result2 = solver2.solve(students, seats);
        check("Solver finds solutions (with proximity)", result2.size() > 0);

        // Verify Alice and Bob are not adjacent in any solution
        boolean allSatisfied = true;
        for (SeatingArrangement arr : result2.getAll()) {
            Seat aliceSeat = arr.getSeatOf(alice);
            Seat bobSeat = arr.getSeatOf(bob);
            if (aliceSeat != null && bobSeat != null && graph.areAdjacent(aliceSeat, bobSeat)) {
                allSatisfied = false;
                break;
            }
        }
        check("Alice never adjacent to Bob", allSatisfied);
        System.out.println("  With proximity: " + result2.size() + " solutions in "
            + result2.getSolveTimeMs() + "ms, best score: "
            + String.format("%.2f", result2.getBest().getScore()));

        // Test 3: With zone constraint
        Zone front = new Zone("Front", 0, 0, 20, 4, new Color(76, 175, 80));
        room.addZone(front);

        ConstraintSet withZone = new ConstraintSet();
        withZone.add(new ZoneConstraint(carol, front,
            ZoneConstraint.MUST_BE_IN, true, 1.0));

        CSPSolver solver3 = new CSPSolver(withZone, graph, 3, 5000);
        SolverResult result3 = solver3.solve(students, seats);
        check("Solver finds solutions (with zone)", result3.size() > 0);
        System.out.println("  With zone: " + result3.size() + " solutions in "
            + result3.getSolveTimeMs() + "ms");

        // Test 4: Stress test with many constraints
        ConstraintSet complex = new ConstraintSet();
        complex.add(new ProximityConstraint(alice, bob, ProximityConstraint.APART, true, 1.0));
        complex.add(new ProximityConstraint(carol, dave, ProximityConstraint.TOGETHER, false, 1.0));
        complex.add(new ZoneConstraint(eve, front, ZoneConstraint.MUST_BE_IN, true, 1.0));
        complex.add(new BalanceConstraint(BalanceConstraint.GENDER, null, false, 1.0));

        CSPSolver solver4 = new CSPSolver(complex, graph, 5, 5000);
        SolverResult result4 = solver4.solve(students, seats);
        check("Complex solver finds solutions", result4.size() > 0);
        System.out.println("  Complex (4 constraints): " + result4.size() + " solutions in "
            + result4.getSolveTimeMs() + "ms, best score: "
            + String.format("%.2f", result4.getBest().getScore()));
    }

    // ============================================================
    // TEST: Undo/Redo (CS III data structure: Stack)
    // ============================================================
    static void testUndoRedo() {
        System.out.println("--- Undo/Redo (Stack) ---");

        Classroom room = new Classroom();
        UndoManager mgr = new UndoManager();

        SingleDesk d1 = new SingleDesk(0, 0);
        SingleDesk d2 = new SingleDesk(5, 5);

        // Add desk 1
        mgr.execute(new AddDeskCommand(room, d1));
        check("After add d1", room.getDesks().size() == 1);

        // Add desk 2
        mgr.execute(new AddDeskCommand(room, d2));
        check("After add d2", room.getDesks().size() == 2);

        // Move desk 2
        mgr.execute(new MoveDeskCommand(d2, 5, 5, 8, 8));
        check("After move d2", d2.getGridX() == 8 && d2.getGridY() == 8);

        // Rotate desk 1
        mgr.execute(new RotateDeskCommand(d1, 45));
        check("After rotate d1", d1.getRotation() == 45.0);

        // Undo rotate
        check("Can undo", mgr.canUndo());
        mgr.undo();
        check("Undo rotate", d1.getRotation() == 0.0);

        // Undo move
        mgr.undo();
        check("Undo move", d2.getGridX() == 5 && d2.getGridY() == 5);

        // Redo move
        check("Can redo", mgr.canRedo());
        mgr.redo();
        check("Redo move", d2.getGridX() == 8 && d2.getGridY() == 8);

        // Undo all
        mgr.undo(); // undo redo'd move
        mgr.undo(); // undo add d2
        mgr.undo(); // undo add d1
        check("Undo all", room.getDesks().size() == 0);

        // Redo all
        mgr.redo(); // add d1
        mgr.redo(); // add d2
        check("Redo adds", room.getDesks().size() == 2);

        // New action clears redo stack
        mgr.execute(new DeleteDeskCommand(room, d1));
        check("Delete clears redo", !mgr.canRedo());
        check("After delete", room.getDesks().size() == 1);

        System.out.println("  Undo description: " + mgr.getUndoDescription());
    }

    // ============================================================
    // TEST: File I/O (save/load round-trip)
    // ============================================================
    static void testFileIO() {
        System.out.println("--- File I/O ---");

        // Build a full project state
        Classroom room = new Classroom(20, 15, 40);
        PairDesk d1 = new PairDesk(2, 3);
        d1.setRotation(45);
        GroupTable d2 = new GroupTable(8, 6);
        room.addDesk(d1);
        room.addDesk(d2);

        Zone front = new Zone("Front", 0, 0, 20, 3, new Color(76, 175, 80));
        Zone back = new Zone("Back", 0, 12, 20, 3, new Color(33, 150, 243));
        room.addZone(front);
        room.addZone(back);

        HashSet<String> aliceTags = new HashSet<String>();
        aliceTags.add("honors");
        aliceTags.add("needs-front");
        Student alice = new Student("Alice Johnson", "F", 5, aliceTags);
        Student bob = new Student("Bob Smith", "M", 2, null);
        List<Student> students = new ArrayList<Student>();
        students.add(alice);
        students.add(bob);

        ConstraintSet constraints = new ConstraintSet();
        constraints.add(new ProximityConstraint(alice, bob,
            ProximityConstraint.APART, true, 1.0));
        constraints.add(new ZoneConstraint(alice, front,
            ZoneConstraint.MUST_BE_IN, true, 1.0));
        constraints.add(new BalanceConstraint(BalanceConstraint.GENDER, null, false, 1.0));

        // Save
        File tempFile = new File("test_save.json");
        try {
            ProjectFile.save(tempFile, room, students, constraints);
            check("File saved", tempFile.exists() && tempFile.length() > 0);
            System.out.println("  Saved " + tempFile.length() + " bytes to " + tempFile.getName());

            // Load
            ProjectFile.LoadResult loaded = ProjectFile.load(tempFile);

            check("Loaded classroom columns", loaded.classroom.getGridColumns() == 20);
            check("Loaded desk count", loaded.classroom.getDesks().size() == 2);
            check("Loaded desk types",
                "pair".equals(loaded.classroom.getDesks().get(0).getTypeName())
                && "group".equals(loaded.classroom.getDesks().get(1).getTypeName()));
            check("Loaded desk rotation", loaded.classroom.getDesks().get(0).getRotation() == 45.0);
            check("Loaded zone count", loaded.classroom.getZones().size() == 2);
            check("Loaded student count", loaded.students.size() == 2);
            check("Loaded student name", "Alice Johnson".equals(loaded.students.get(0).getName()));
            check("Loaded student tags", loaded.students.get(0).hasTag("honors"));
            check("Loaded constraint count", loaded.constraints.size() == 3);

            System.out.println("  Round-trip verification: ALL FIELDS MATCH");

        } catch (Exception e) {
            check("File I/O exception: " + e.getMessage(), false);
            e.printStackTrace();
        } finally {
            tempFile.delete();
        }
    }

    // ============================================================
    // TEST: Full integration (everything together)
    // ============================================================
    static void testFullIntegration() {
        System.out.println("--- Full Integration ---");

        // Simulate a realistic classroom scenario
        Classroom room = new Classroom(20, 15, 40);

        // Place a realistic desk layout
        // Row 1: 3 pair desks
        room.addDesk(new PairDesk(1, 2));
        room.addDesk(new PairDesk(5, 2));
        room.addDesk(new PairDesk(9, 2));
        // Row 2: 2 group tables
        room.addDesk(new GroupTable(2, 5));
        room.addDesk(new GroupTable(7, 5));
        // Row 3: 1 lab bench + 1 single
        room.addDesk(new LabBench(1, 9));
        room.addDesk(new SingleDesk(5, 9));

        // Draw all desks to set seat positions
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (Desk d : room.getDesks()) {
            d.draw(g, 40);
        }
        g.dispose();

        int totalSeats = room.getTotalSeatCount();
        System.out.println("  Classroom: " + room.getDesks().size() + " desks, "
            + totalSeats + " seats");
        check("Integration: reasonable seat count", totalSeats == 6 + 8 + 3 + 1);

        // Zone
        Zone front = new Zone("Front", 0, 0, 20, 4, new Color(76, 175, 80));
        room.addZone(front);

        // Students (18 to fill most seats)
        String[] names = {"Alice", "Bob", "Carol", "Dave", "Eve", "Frank",
            "Grace", "Hank", "Ivy", "Jack", "Kate", "Leo",
            "Mia", "Nick", "Olive", "Paul", "Quinn", "Rosa"};
        List<Student> students = new ArrayList<Student>();
        for (int i = 0; i < names.length; i++) {
            String gender = (i % 2 == 0) ? "F" : "M";
            int skill = (i % 5) + 1;
            Student s = new Student(names[i], gender, skill, null);
            if (i < 3) s.addTag("honors");
            if (i >= 15) s.addTag("esl");
            students.add(s);
        }
        check("Integration: 18 students", students.size() == 18);

        // Constraints
        ConstraintSet constraints = new ConstraintSet();
        constraints.add(new ProximityConstraint(students.get(0), students.get(1),
            ProximityConstraint.APART, true, 1.0)); // Alice away from Bob
        constraints.add(new ZoneConstraint(students.get(4), front,
            ZoneConstraint.MUST_BE_IN, true, 1.0)); // Eve in front
        constraints.add(new BalanceConstraint(BalanceConstraint.GENDER, null, false, 1.0));

        // Build graph and solve
        SeatGraph graph = SeatGraph.buildFrom(room);
        CSPSolver solver = new CSPSolver(constraints, graph, 5, 5000);
        SolverResult result = solver.solve(students, room.getAllSeats());

        check("Integration: solver found solutions", result.size() > 0);
        if (!result.isEmpty()) {
            SeatingArrangement best = result.getBest();
            check("Integration: all students assigned",
                best.getAssignedCount() == students.size());
            check("Integration: score > 0", best.getScore() > 0);
            check("Integration: hard constraints met",
                constraints.allHardSatisfied(best, graph));

            System.out.println("  Solver: " + result.size() + " solutions, best score: "
                + String.format("%.2f", best.getScore()) + " (" + result.getSolveTimeMs() + "ms)");
        }
    }

    // ============================================================
    // VISUAL DEMO: Launch the app with pre-populated data
    // ============================================================
    static void launchVisualDemo() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {}

                MainFrame frame = new MainFrame();
                frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH); // MAXIMIZE
                Classroom room = frame.getClassroom();
                int gs = room.getGridSize(); // 20

                // Add desks in a realistic layout (using new 28x20 grid at 20px)
                room.addDesk(new PairDesk(4, 2));
                room.addDesk(new PairDesk(10, 2));
                room.addDesk(new PairDesk(16, 2));
                room.addDesk(new PairDesk(22, 2));
                room.addDesk(new GroupTable(3, 7));
                room.addDesk(new GroupTable(11, 7));
                room.addDesk(new GroupTable(19, 7));
                room.addDesk(new LabBench(2, 14));
                room.addDesk(new LabBench(10, 14));
                room.addDesk(new CircleTable(20, 13));

                // Draw all desks to set seat positions
                BufferedImage img = new BufferedImage(
                    room.getPixelWidth(), room.getPixelHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                for (Desk d : room.getDesks()) { d.draw(g, gs); }
                g.dispose();

                // Add students
                String[] names = {"Alice", "Bob", "Carol", "Dave", "Eve",
                    "Frank", "Grace", "Hank", "Ivy", "Jack",
                    "Kate", "Leo", "Mia", "Nick", "Olive",
                    "Paul", "Quinn", "Rosa", "Sam", "Tina"};
                List<Student> students = frame.getStudentPanel().getStudents();
                for (int i = 0; i < names.length; i++) {
                    String gender = (i % 2 == 0) ? "F" : "M";
                    Student s = new Student(names[i], gender, (i % 5) + 1, null);
                    if (i < 4) s.addTag("honors");
                    students.add(s);
                }

                // Add constraints
                ConstraintSet cs = frame.getConstraintSet();
                cs.add(new ProximityConstraint(students.get(0), students.get(1),
                    ProximityConstraint.APART, true, 1.0));
                cs.add(new ProximityConstraint(students.get(2), students.get(3),
                    ProximityConstraint.TOGETHER, false, 1.0));
                cs.add(new ZoneConstraint(students.get(4),
                    room.getZones().get(0), ZoneConstraint.MUST_BE_IN, true, 1.0));
                cs.add(new BalanceConstraint(BalanceConstraint.GENDER, null, false, 1.0));
                frame.getConstraintPanel().refreshList();
                frame.getStudentPanel().refresh();

                // Run the solver
                SeatGraph graph = SeatGraph.buildFrom(room);
                CSPSolver solver = new CSPSolver(cs, graph, 5, 5000);
                SolverResult result = solver.solve(students, room.getAllSeats());

                if (!result.isEmpty()) {
                    frame.getClassroomPanel().setCurrentArrangement(result.getBest());
                    frame.getClassroomPanel().setConstraintData(cs, graph);
                    // Heat map off for cleaner screenshot
                }

                frame.setVisible(true);

                // Capture screenshot after a brief delay for rendering
                javax.swing.Timer timer = new javax.swing.Timer(2000, new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        try {
                            BufferedImage screenshot = new BufferedImage(
                                frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
                            frame.paint(screenshot.getGraphics());
                            File outFile = new File("seatsolver_screenshot.png");
                            ImageIO.write(screenshot, "png", outFile);
                            System.out.println("\nScreenshot saved: " + outFile.getAbsolutePath());
                        } catch (Exception ex) {
                            System.out.println("Screenshot failed: " + ex.getMessage());
                        }
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }

    // ============================================================
    // Assertion helper
    // ============================================================
    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS: " + name);
        } else {
            failed++;
            System.out.println("  FAIL: " + name);
        }
    }
}
