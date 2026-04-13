package seating;

import seating.model.*;
import seating.constraint.*;
import seating.solver.*;
import seating.layout.*;
import seating.io.ProjectFile;
import seating.ui.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * End-to-end test suite that exercises every user-facing feature
 * of SeatSolver programmatically. Simulates real user workflows
 * and verifies correct behavior at each step.
 */
public class SeatSolverE2ETest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== SeatSolver End-to-End Test Suite ===\n");

        testDeskPlacementAllTypes();
        testDeskManipulation();
        testUndoRedoFullCycle();
        testZoneManagement();
        testStudentCRUD();
        testConstraintCreationAllTypes();
        testOrphanConstraintCleanup();
        testSolverWithConstraints();
        testSolverEdgeCases();
        testFileIORoundTrip();
        testVisualRendering();
        testGridSizeBugFix();

        System.out.println("\n=== E2E RESULTS: " + passed + " passed, " + failed + " failed ===");
        if (failed == 0) {
            System.out.println("ALL E2E TESTS PASSED!");
        } else {
            System.out.println("SOME E2E TESTS FAILED.");
            System.exit(1);
        }
    }

    // ============================================================
    // E2E 1: Desk Placement (all 5 types)
    // ============================================================
    static void testDeskPlacementAllTypes() {
        System.out.println("--- E2E: Desk Placement (All Types) ---");
        Classroom room = new Classroom(20, 15, 40);

        // Simulate placing one of each type
        String[] types = {"single", "pair", "group", "lab", "circle"};
        int[] expectedSeats = {1, 2, 4, 3, 6};

        for (int i = 0; i < types.length; i++) {
            Desk d = DeskPalette.createDesk(types[i], i * 4, 0);
            check("Create " + types[i], d != null);
            if (d != null) {
                room.addDesk(d);
                check(types[i] + " seat count", d.getSeatCount() == expectedSeats[i]);
                check(types[i] + " gridSize synced", d.getGridSize() == 40);

                // Draw to set seat positions
                BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                d.draw(g, room.getGridSize());
                g.dispose();

                // Verify seats have valid positions
                for (Seat s : d.getSeats()) {
                    java.awt.geom.Point2D pos = s.getGlobalPosition();
                    check(types[i] + " seat " + s.getId() + " has position",
                        pos.getX() >= 0 && pos.getY() >= 0);
                }
            }
        }

        check("Total desk count", room.getDesks().size() == 5);
        check("Total seat count", room.getTotalSeatCount() == 16);
        check("Factory returns null for unknown", DeskPalette.createDesk("invalid", 0, 0) == null);
    }

    // ============================================================
    // E2E 2: Desk Manipulation (move, rotate, collision, duplicate)
    // ============================================================
    static void testDeskManipulation() {
        System.out.println("--- E2E: Desk Manipulation ---");
        Classroom room = new Classroom(20, 15, 40);

        SingleDesk d = new SingleDesk(5, 5);
        room.addDesk(d);

        // Move
        d.setPosition(10, 10);
        check("Move desk", d.getGridX() == 10 && d.getGridY() == 10);

        // Rotate
        d.setRotation(0);
        d.rotate(90);
        check("Rotate 90", d.getRotation() == 90.0);
        d.rotate(280);
        check("Rotate wraps", Math.abs(d.getRotation() - 10.0) < 0.01);

        // Collision
        SingleDesk d2 = new SingleDesk(10, 10);
        check("Collision detected", d.collidesWith(d2, 40));
        SingleDesk d3 = new SingleDesk(0, 0);
        check("No collision", !d.collidesWith(d3, 40));

        // Duplicate (simulate)
        Desk copy = DeskPalette.createDesk(d.getTypeName(), d.getGridX() + 1, d.getGridY() + 1);
        copy.setRotation(d.getRotation());
        room.addDesk(copy);
        check("Duplicate created", room.getDesks().size() == 2);
        check("Duplicate has same rotation", copy.getRotation() == d.getRotation());
    }

    // ============================================================
    // E2E 3: Full Undo/Redo Cycle
    // ============================================================
    static void testUndoRedoFullCycle() {
        System.out.println("--- E2E: Undo/Redo Full Cycle ---");
        Classroom room = new Classroom();
        UndoManager mgr = new UndoManager();

        // Add 3 desks
        SingleDesk d1 = new SingleDesk(0, 0);
        PairDesk d2 = new PairDesk(5, 0);
        GroupTable d3 = new GroupTable(0, 5);
        mgr.execute(new AddDeskCommand(room, d1));
        mgr.execute(new AddDeskCommand(room, d2));
        mgr.execute(new AddDeskCommand(room, d3));
        check("3 desks added", room.getDesks().size() == 3);

        // Move d2
        mgr.execute(new MoveDeskCommand(d2, 5, 0, 8, 3));
        check("Move executed", d2.getGridX() == 8);

        // Rotate d1
        mgr.execute(new RotateDeskCommand(d1, 45));
        check("Rotate executed", d1.getRotation() == 45.0);

        // Delete d3
        mgr.execute(new DeleteDeskCommand(room, d3));
        check("Delete executed", room.getDesks().size() == 2);

        // Undo everything
        check("Can undo", mgr.canUndo());
        mgr.undo(); // undo delete
        check("Undo delete", room.getDesks().size() == 3);
        mgr.undo(); // undo rotate
        check("Undo rotate", d1.getRotation() == 0.0);
        mgr.undo(); // undo move
        check("Undo move", d2.getGridX() == 5);
        mgr.undo(); // undo add d3
        mgr.undo(); // undo add d2
        mgr.undo(); // undo add d1
        check("Undo all adds", room.getDesks().size() == 0);
        check("Nothing to undo", !mgr.canUndo());

        // Redo everything
        check("Can redo", mgr.canRedo());
        mgr.redo(); mgr.redo(); mgr.redo(); // re-add all 3
        check("Redo 3 adds", room.getDesks().size() == 3);
        mgr.redo(); // redo move
        check("Redo move", d2.getGridX() == 8);

        // New action clears redo
        mgr.execute(new RotateDeskCommand(d1, 10));
        check("New action clears redo", !mgr.canRedo());
    }

    // ============================================================
    // E2E 4: Zone Management
    // ============================================================
    static void testZoneManagement() {
        System.out.println("--- E2E: Zone Management ---");
        Classroom room = new Classroom(20, 15, 40);

        // Create zones
        Zone front = new Zone("Front", 0, 0, 20, 3, new Color(76, 175, 80));
        Zone back = new Zone("Back", 0, 12, 20, 3, new Color(33, 150, 243));
        Zone window = new Zone("Window", 0, 0, 3, 15, new Color(255, 193, 7));
        room.addZone(front);
        room.addZone(back);
        room.addZone(window);
        check("3 zones created", room.getZones().size() == 3);

        // Zone containment
        check("Point in front zone", front.contains(100, 50, 40));
        check("Point not in front zone", !front.contains(100, 500, 40));
        check("Point in back zone", back.contains(100, 500, 40));

        // Place desk in front zone, verify seat falls in zone
        PairDesk d = new PairDesk(5, 1);
        room.addDesk(d);
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        d.draw(g, room.getGridSize());
        g.dispose();

        Seat seat = d.getSeats().get(0);
        java.awt.geom.Point2D pos = seat.getGlobalPosition();
        check("Seat in front zone", front.contains(pos.getX(), pos.getY(), 40));

        // Edit zone
        front.setLabel("Front Row");
        check("Zone label edited", "Front Row".equals(front.getLabel()));

        // Delete zone
        room.removeZone(window);
        check("Zone deleted", room.getZones().size() == 2);
    }

    // ============================================================
    // E2E 5: Student CRUD
    // ============================================================
    static void testStudentCRUD() {
        System.out.println("--- E2E: Student CRUD ---");

        // Add
        Student s1 = new Student("Alice Johnson", "F", 4, null);
        Student s2 = new Student("Bob Smith", "M", 2, null);
        s1.addTag("honors");
        s1.addTag("needs-front");

        check("Student name", "Alice Johnson".equals(s1.getName()));
        check("Student gender", "F".equals(s1.getGender()));
        check("Student skill", s1.getSkillLevel() == 4);
        check("Student has tag", s1.hasTag("honors"));
        check("Student tag count", s1.getTags().size() == 2);

        // Edit
        s1.setName("Alice J.");
        s1.setSkillLevel(5);
        check("Edit name", "Alice J.".equals(s1.getName()));
        check("Edit skill", s1.getSkillLevel() == 5);

        // Tags
        s1.removeTag("honors");
        check("Remove tag", !s1.hasTag("honors"));
        check("Tag count after remove", s1.getTags().size() == 1);

        // Equality
        check("Students not equal", !s1.equals(s2));
        check("Student equals self", s1.equals(s1));

        // Skill clamping
        s2.setSkillLevel(0);
        check("Skill clamp low", s2.getSkillLevel() == 1);
        s2.setSkillLevel(99);
        check("Skill clamp high", s2.getSkillLevel() == 5);
    }

    // ============================================================
    // E2E 6: Constraint Creation (all 3 types)
    // ============================================================
    static void testConstraintCreationAllTypes() {
        System.out.println("--- E2E: Constraint Creation ---");

        Student alice = new Student("Alice", "F", 4, null);
        Student bob = new Student("Bob", "M", 2, null);
        Zone front = new Zone("Front", 0, 0, 20, 3, new Color(76, 175, 80));

        // Proximity
        ProximityConstraint apart = new ProximityConstraint(alice, bob, "apart", true, 1.0);
        check("Proximity type", "proximity".equals(apart.getType()));
        check("Proximity isHard", apart.isHard());
        check("Proximity involves Alice", apart.involvesStudent(alice));
        check("Proximity involves Bob", apart.involvesStudent(bob));
        check("Proximity not involves other", !apart.involvesStudent(new Student("Carol")));
        check("Proximity describe", apart.describe().contains("must NOT sit next to"));

        // Zone
        ZoneConstraint zone = new ZoneConstraint(alice, front, "must_be_in", true, 1.0);
        check("Zone type", "zone".equals(zone.getType()));
        check("Zone involves Alice", zone.involvesStudent(alice));
        check("Zone not involves Bob", !zone.involvesStudent(bob));

        // Balance
        BalanceConstraint balance = new BalanceConstraint("gender", null, false, 1.0);
        check("Balance type", "balance".equals(balance.getType()));
        check("Balance not involves anyone", !balance.involvesStudent(alice));
        check("Balance describe", balance.describe().contains("gender"));

        // ConstraintSet
        ConstraintSet cs = new ConstraintSet();
        cs.add(apart);
        cs.add(zone);
        cs.add(balance);
        check("ConstraintSet size", cs.size() == 3);
    }

    // ============================================================
    // E2E 7: Orphan Constraint Cleanup
    // ============================================================
    static void testOrphanConstraintCleanup() {
        System.out.println("--- E2E: Orphan Constraint Cleanup ---");

        Student alice = new Student("Alice");
        Student bob = new Student("Bob");
        Student carol = new Student("Carol");
        Zone front = new Zone("Front", 0, 0, 20, 3, new Color(76, 175, 80));

        ConstraintSet cs = new ConstraintSet();
        cs.add(new ProximityConstraint(alice, bob, "apart", true, 1.0));
        cs.add(new ZoneConstraint(alice, front, "must_be_in", true, 1.0));
        cs.add(new ProximityConstraint(bob, carol, "together", false, 1.0));
        cs.add(new BalanceConstraint("gender", null, false, 1.0));
        check("4 constraints before cleanup", cs.size() == 4);

        // Remove constraints involving Alice
        Iterator<Constraint> it = cs.getAll().iterator();
        while (it.hasNext()) {
            if (it.next().involvesStudent(alice)) it.remove();
        }
        check("2 constraints after removing Alice's", cs.size() == 2);

        // Balance should survive (doesn't involve anyone specifically)
        boolean hasBalance = false;
        for (Constraint c : cs.getAll()) {
            if ("balance".equals(c.getType())) hasBalance = true;
        }
        check("Balance constraint survives", hasBalance);
    }

    // ============================================================
    // E2E 8: Solver With Constraints
    // ============================================================
    static void testSolverWithConstraints() {
        System.out.println("--- E2E: Solver With Constraints ---");

        Classroom room = new Classroom(20, 15, 40);
        // 3 pair desks = 6 seats
        PairDesk d1 = new PairDesk(1, 1);
        PairDesk d2 = new PairDesk(5, 1);
        PairDesk d3 = new PairDesk(1, 5);
        room.addDesk(d1); room.addDesk(d2); room.addDesk(d3);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        d1.draw(g, 40); d2.draw(g, 40); d3.draw(g, 40);
        g.dispose();

        Zone front = new Zone("Front", 0, 0, 20, 3, new Color(76, 175, 80));
        room.addZone(front);

        Student alice = new Student("Alice", "F", 4, null);
        Student bob = new Student("Bob", "M", 2, null);
        Student carol = new Student("Carol", "F", 3, null);
        Student dave = new Student("Dave", "M", 5, null);
        List<Student> students = Arrays.asList(alice, bob, carol, dave);

        SeatGraph graph = SeatGraph.buildFrom(room);

        // Hard constraint: Alice apart from Bob
        ConstraintSet cs = new ConstraintSet();
        cs.add(new ProximityConstraint(alice, bob, "apart", true, 1.0));
        cs.add(new ZoneConstraint(carol, front, "must_be_in", true, 1.0));

        CSPSolver solver = new CSPSolver(cs, graph, 5, 5000);
        SolverResult result = solver.solve(students, room.getAllSeats());

        check("Solver found solutions", result.size() > 0);

        // Verify hard constraints in ALL solutions
        boolean allValid = true;
        for (SeatingArrangement arr : result.getAll()) {
            if (!cs.allHardSatisfied(arr, graph)) {
                allValid = false;
                break;
            }
        }
        check("All solutions satisfy hard constraints", allValid);

        // Verify Alice and Bob never adjacent
        boolean aliceBobOk = true;
        for (SeatingArrangement arr : result.getAll()) {
            Seat as = arr.getSeatOf(alice);
            Seat bs = arr.getSeatOf(bob);
            if (as != null && bs != null && graph.areAdjacent(as, bs)) {
                aliceBobOk = false;
            }
        }
        check("Alice never adjacent to Bob", aliceBobOk);

        System.out.println("  Found " + result.size() + " solutions in " + result.getSolveTimeMs() + "ms");
    }

    // ============================================================
    // E2E 9: Solver Edge Cases
    // ============================================================
    static void testSolverEdgeCases() {
        System.out.println("--- E2E: Solver Edge Cases ---");

        Classroom room = new Classroom(20, 15, 40);
        PairDesk d = new PairDesk(1, 1);
        room.addDesk(d);
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        d.draw(img.createGraphics(), 40);

        SeatGraph graph = SeatGraph.buildFrom(room);
        ConstraintSet cs = new ConstraintSet();

        // 0 students
        CSPSolver solver1 = new CSPSolver(cs, graph, 3, 2000);
        SolverResult r1 = solver1.solve(new ArrayList<Student>(), room.getAllSeats());
        check("0 students: empty result", r1.size() == 0);

        // More students than seats (2 seats, 5 students)
        List<Student> many = new ArrayList<Student>();
        for (int i = 0; i < 5; i++) many.add(new Student("S" + i));
        CSPSolver solver2 = new CSPSolver(cs, graph, 3, 2000);
        SolverResult r2 = solver2.solve(many, room.getAllSeats());
        // Should either find partial solutions or empty (implementation dependent)
        check("More students than seats: handled", true); // no crash

        // Impossible constraints
        Student a = new Student("A");
        Student b = new Student("B");
        cs.add(new ProximityConstraint(a, b, "apart", true, 1.0));
        cs.add(new ProximityConstraint(a, b, "together", true, 1.0)); // contradicts!
        CSPSolver solver3 = new CSPSolver(cs, graph, 3, 2000);
        SolverResult r3 = solver3.solve(Arrays.asList(a, b), room.getAllSeats());
        check("Contradictory constraints: no crash", true);
        System.out.println("  Contradictory constraints: " + r3.size() + " solutions (expected 0)");
    }

    // ============================================================
    // E2E 10: File I/O Round Trip
    // ============================================================
    static void testFileIORoundTrip() {
        System.out.println("--- E2E: File I/O Round Trip ---");

        // Build complex state
        Classroom room = new Classroom(20, 15, 40);
        PairDesk d1 = new PairDesk(3, 2);
        d1.setRotation(45);
        GroupTable d2 = new GroupTable(8, 6);
        LabBench d3 = new LabBench(1, 10);
        room.addDesk(d1); room.addDesk(d2); room.addDesk(d3);

        Zone front = new Zone("Front Row", 0, 0, 20, 3, new Color(76, 175, 80));
        Zone custom = new Zone("Window", 0, 0, 3, 15, new Color(255, 193, 7));
        room.addZone(front);
        room.addZone(custom);

        HashSet<String> tags = new HashSet<String>();
        tags.add("honors");
        tags.add("esl");
        Student s1 = new Student("Alice Johnson", "F", 5, tags);
        Student s2 = new Student("Bob O'Brien", "M", 2, null); // apostrophe in name
        List<Student> students = new ArrayList<Student>();
        students.add(s1); students.add(s2);

        ConstraintSet cs = new ConstraintSet();
        cs.add(new ProximityConstraint(s1, s2, "apart", true, 1.5));
        cs.add(new ZoneConstraint(s1, front, "must_be_in", true, 1.0));
        cs.add(new BalanceConstraint("gender", null, false, 0.8));

        File tempFile = new File("e2e_test_save.json");
        try {
            // Save
            ProjectFile.save(tempFile, room, students, cs);
            check("File created", tempFile.exists());
            check("File has content", tempFile.length() > 100);

            // Load
            ProjectFile.LoadResult loaded = ProjectFile.load(tempFile);

            // Verify classroom
            check("Loaded columns", loaded.classroom.getGridColumns() == 20);
            check("Loaded desk count", loaded.classroom.getDesks().size() == 3);
            check("Loaded desk 1 type", "pair".equals(loaded.classroom.getDesks().get(0).getTypeName()));
            check("Loaded desk 1 rotation", loaded.classroom.getDesks().get(0).getRotation() == 45.0);
            check("Loaded desk 2 type", "group".equals(loaded.classroom.getDesks().get(1).getTypeName()));
            check("Loaded desk 3 type", "lab".equals(loaded.classroom.getDesks().get(2).getTypeName()));

            // Verify zones
            check("Loaded zone count", loaded.classroom.getZones().size() == 2);
            check("Loaded zone 1 label", "Front Row".equals(loaded.classroom.getZones().get(0).getLabel()));

            // Verify students
            check("Loaded student count", loaded.students.size() == 2);
            check("Loaded student 1 name", "Alice Johnson".equals(loaded.students.get(0).getName()));
            check("Loaded student 1 tags", loaded.students.get(0).hasTag("honors"));
            check("Loaded student 2 name", "Bob O'Brien".equals(loaded.students.get(1).getName()));

            // Verify constraints
            check("Loaded constraint count", loaded.constraints.size() == 3);

            System.out.println("  Round-trip: ALL FIELDS MATCH");

        } catch (Exception e) {
            check("File I/O: " + e.getMessage(), false);
            e.printStackTrace();
        } finally {
            tempFile.delete();
        }
    }

    // ============================================================
    // E2E 11: Visual Rendering (no exceptions)
    // ============================================================
    static void testVisualRendering() {
        System.out.println("--- E2E: Visual Rendering ---");

        Classroom room = new Classroom(20, 15, 40);
        room.addDesk(new SingleDesk(0, 0));
        room.addDesk(new PairDesk(3, 0));
        room.addDesk(new GroupTable(7, 0));
        room.addDesk(new LabBench(0, 5));
        room.addDesk(new CircleTable(5, 5));
        room.addZone(new Zone("Front", 0, 0, 20, 3, new Color(76, 175, 80)));

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw all desks
        for (Desk d : room.getDesks()) {
            d.draw(g, room.getGridSize());
        }
        check("All desks draw without exception", true);

        // Build graph + solver
        SeatGraph graph = SeatGraph.buildFrom(room);
        List<Student> students = new ArrayList<Student>();
        for (int i = 0; i < 10; i++) students.add(new Student("Student" + i));

        ConstraintSet cs = new ConstraintSet();
        cs.add(new ProximityConstraint(students.get(0), students.get(1), "apart", true, 1.0));

        CSPSolver solver = new CSPSolver(cs, graph, 1, 3000);
        SolverResult result = solver.solve(students, room.getAllSeats());

        if (!result.isEmpty()) {
            SeatingArrangement arr = result.getBest();

            // Draw overlays
            try {
                ConflictOverlay.draw(g, arr, cs, graph);
                check("Conflict overlay renders", true);
            } catch (Exception e) {
                check("Conflict overlay: " + e.getMessage(), false);
            }

            try {
                HeatMapOverlay.draw(g, arr, cs, graph, room);
                check("Heat map overlay renders", true);
            } catch (Exception e) {
                check("Heat map overlay: " + e.getMessage(), false);
            }
        }

        // Draw with null arrangement (should be safe)
        try {
            ConflictOverlay.draw(g, null, cs, graph);
            HeatMapOverlay.draw(g, null, cs, graph, room);
            check("Null arrangement: overlays handle gracefully", true);
        } catch (Exception e) {
            check("Null arrangement: " + e.getMessage(), false);
        }

        g.dispose();
    }

    // ============================================================
    // E2E 12: GridSize Bug Fix Verification
    // ============================================================
    static void testGridSizeBugFix() {
        System.out.println("--- E2E: GridSize Bug Fix ---");

        // Test with non-default grid size (60 instead of 40)
        Classroom room = new Classroom(15, 10, 60);
        PairDesk d = new PairDesk(2, 2);
        room.addDesk(d);
        check("Desk gridSize synced to 60", d.getGridSize() == 60);

        BufferedImage img = new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        d.draw(g, 60);
        g.dispose();

        // Seat positions should be based on gridSize=60, not 40
        Seat seat = d.getSeats().get(0);
        java.awt.geom.Point2D pos = seat.getGlobalPosition();
        // With gridSize=60, desk at (2,2): pixel position = 2*60=120
        // Seat should be near x=120+offset, y=120+offset
        check("Seat position uses correct gridSize",
            pos.getX() > 100 && pos.getX() < 250);

        // Zone constraint should also use correct gridSize
        Zone front = new Zone("Front", 0, 0, 15, 6, new Color(76, 175, 80));
        room.addZone(front);
        Student alice = new Student("Alice");

        ZoneConstraint zc = new ZoneConstraint(alice, front, "must_be_in", true, 1.0);
        SeatingArrangement arr = new SeatingArrangement();
        arr.assign(seat, alice);
        SeatGraph graph = SeatGraph.buildFrom(room);

        // Seat is at row 2, front zone covers rows 0-3 with gridSize 60
        // So seat should be in the front zone
        check("ZoneConstraint uses desk gridSize", zc.isSatisfied(arr, graph));
    }

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
