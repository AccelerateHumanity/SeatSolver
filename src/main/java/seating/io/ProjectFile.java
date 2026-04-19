package seating.io;

import seating.model.*;
import seating.constraint.*;
import seating.ui.DeskPalette;

import java.awt.Color;
import java.io.*;
import java.util.*;

/**
 * Handles saving and loading the entire project state to/from JSON files.
 * Uses a simple custom JSON format — no external libraries required.
 *
 * <p>Saves: classroom layout (desks + zones), student list, and constraints.
 * Everything needed to restore the full application state.</p>
 */
public class ProjectFile {

    /**
     * Saves the full project state to a JSON file.
     *
     * @param file destination file
     * @param classroom the classroom layout
     * @param students the student list
     * @param constraints the constraint set
     * @throws IOException if writing fails
     */
    public static void save(File file, Classroom classroom,
                             List<Student> students, ConstraintSet constraints)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Classroom
        sb.append("  \"classroom\": {\n");
        sb.append("    \"columns\": ").append(classroom.getGridColumns()).append(",\n");
        sb.append("    \"rows\": ").append(classroom.getGridRows()).append(",\n");
        sb.append("    \"gridSize\": ").append(classroom.getGridSize()).append(",\n");

        // Desks
        sb.append("    \"desks\": [\n");
        List<Desk> desks = classroom.getDesks();
        for (int i = 0; i < desks.size(); i++) {
            Desk d = desks.get(i);
            sb.append("      {");
            sb.append("\"type\":\"").append(d.getTypeName()).append("\",");
            sb.append("\"id\":\"").append(d.getId()).append("\",");
            sb.append("\"gridX\":").append(d.getGridX()).append(",");
            sb.append("\"gridY\":").append(d.getGridY()).append(",");
            sb.append("\"rotation\":").append(d.getRotation());
            sb.append("}");
            if (i < desks.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n");

        // Zones
        sb.append("    \"zones\": [\n");
        List<Zone> zones = classroom.getZones();
        for (int i = 0; i < zones.size(); i++) {
            Zone z = zones.get(i);
            sb.append("      {");
            sb.append("\"label\":\"").append(escapeJson(z.getLabel())).append("\",");
            sb.append("\"x\":").append(z.getGridX()).append(",");
            sb.append("\"y\":").append(z.getGridY()).append(",");
            sb.append("\"w\":").append(z.getGridWidth()).append(",");
            sb.append("\"h\":").append(z.getGridHeight()).append(",");
            sb.append("\"color\":\"").append(colorToHex(z.getColor())).append("\"");
            if (z.getRotation() != 0) {
                sb.append(",\"rotation\":").append(z.getRotation());
            }
            sb.append("}");
            if (i < zones.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n");

        // Landmarks
        sb.append("    \"landmarks\": [\n");
        List<Landmark> landmarks = classroom.getLandmarks();
        for (int i = 0; i < landmarks.size(); i++) {
            Landmark lm = landmarks.get(i);
            sb.append("      {");
            sb.append("\"type\":\"").append(escapeJson(lm.getType())).append("\",");
            sb.append("\"label\":\"").append(escapeJson(lm.getLabel())).append("\",");
            sb.append("\"x\":").append(lm.getGridX()).append(",");
            sb.append("\"y\":").append(lm.getGridY()).append(",");
            sb.append("\"w\":").append(lm.getGridW()).append(",");
            sb.append("\"h\":").append(lm.getGridH()).append(",");
            sb.append("\"rotation\":").append(lm.getRotation());
            sb.append("}");
            if (i < landmarks.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ]\n");
        sb.append("  },\n");

        // Students
        sb.append("  \"students\": [\n");
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            sb.append("    {");
            sb.append("\"id\":\"").append(s.getId()).append("\",");
            sb.append("\"name\":\"").append(escapeJson(s.getName())).append("\",");
            sb.append("\"gender\":\"").append(s.getGender()).append("\",");
            sb.append("\"skill\":").append(s.getSkillLevel()).append(",");
            sb.append("\"tags\":\"").append(String.join(";", s.getTags())).append("\"");
            sb.append("}");
            if (i < students.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Constraints
        sb.append("  \"constraints\": [\n");
        List<Constraint> cList = constraints.getAll();
        for (int i = 0; i < cList.size(); i++) {
            Constraint c = cList.get(i);
            sb.append("    ").append(serializeConstraint(c, students));
            if (i < cList.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");

        sb.append("}\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(sb.toString());
        }
    }

    /**
     * Loads the full project state from a JSON file.
     * Returns a LoadResult containing all reconstructed objects.
     *
     * @param file the JSON file to load
     * @return the loaded state
     * @throws IOException if reading/parsing fails
     */
    public static LoadResult load(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        String json = sb.toString();
        LoadResult result = new LoadResult();
        Student.resetIdCounter();
        Desk.resetIdCounter();

        // Parse classroom dimensions
        result.classroom = new Classroom(
            parseIntField(json, "columns", 20),
            parseIntField(json, "rows", 15),
            parseIntField(json, "gridSize", 20)
        );

        // Parse desks
        String desksArr = extractArray(json, "desks");
        if (desksArr != null) {
            for (String deskJson : splitJsonArray(desksArr)) {
                String type = parseStringField(deskJson, "type");
                int gx = parseIntField(deskJson, "gridX", 0);
                int gy = parseIntField(deskJson, "gridY", 0);
                double rot = parseDoubleField(deskJson, "rotation", 0);
                String id = parseStringField(deskJson, "id");

                Desk desk = DeskPalette.createDesk(type, gx, gy);
                if (desk != null) {
                    if (id != null) desk.setId(id);
                    desk.setRotation(rot);
                    result.classroom.addDesk(desk);
                }
            }
        }

        // Parse zones
        result.classroom.getZones().clear();
        String zonesArr = extractArray(json, "zones");
        if (zonesArr != null) {
            for (String zoneJson : splitJsonArray(zonesArr)) {
                String label = parseStringField(zoneJson, "label");
                int x = parseIntField(zoneJson, "x", 0);
                int y = parseIntField(zoneJson, "y", 0);
                int w = parseIntField(zoneJson, "w", 5);
                int h = parseIntField(zoneJson, "h", 3);
                Color color = hexToColor(parseStringField(zoneJson, "color"));
                double rotation = parseDoubleField(zoneJson, "rotation", 0.0);
                if (label != null) {
                    Zone z = new Zone(label, x, y, w, h, color);
                    z.setRotation(rotation);
                    result.classroom.addZone(z);
                }
            }
        }

        // Parse landmarks
        String landmarksArr = extractArray(json, "landmarks");
        if (landmarksArr != null) {
            for (String lmJson : splitJsonArray(landmarksArr)) {
                String type = parseStringField(lmJson, "type");
                String label = parseStringField(lmJson, "label");
                int lx = parseIntField(lmJson, "x", 0);
                int ly = parseIntField(lmJson, "y", 0);
                int lw = parseIntField(lmJson, "w", 2);
                int lh = parseIntField(lmJson, "h", 2);
                double rot = parseDoubleField(lmJson, "rotation", 0);
                if (type != null) {
                    Landmark lm = new Landmark(type, lx, ly, lw, lh);
                    if (label != null) lm.setLabel(label);
                    lm.setRotation(rot);
                    result.classroom.addLandmark(lm);
                }
            }
        }

        // Parse students
        result.students = new ArrayList<Student>();
        String studentsArr = extractArray(json, "students");
        if (studentsArr != null) {
            for (String sJson : splitJsonArray(studentsArr)) {
                String name = parseStringField(sJson, "name");
                String gender = parseStringField(sJson, "gender");
                int skill = parseIntField(sJson, "skill", 3);
                String tagsStr = parseStringField(sJson, "tags");
                String id = parseStringField(sJson, "id");

                HashSet<String> tags = new HashSet<String>();
                if (tagsStr != null && !tagsStr.isEmpty()) {
                    for (String t : tagsStr.split(";")) {
                        if (!t.trim().isEmpty()) tags.add(t.trim());
                    }
                }
                Student student = new Student(name != null ? name : "Unknown",
                    gender != null ? gender : "", skill, tags);
                if (id != null) student.setId(id);
                result.students.add(student);
            }
        }

        // Parse constraints
        result.constraints = new ConstraintSet();
        int droppedConstraints = 0;
        String constraintsArr = extractArray(json, "constraints");
        if (constraintsArr != null) {
            for (String cJson : splitJsonArray(constraintsArr)) {
                Constraint c = deserializeConstraint(cJson, result.students,
                    result.classroom.getZones());
                if (c != null) {
                    result.constraints.add(c);
                } else {
                    droppedConstraints++;
                }
            }
        }
        if (droppedConstraints > 0) {
            result.loadWarning = droppedConstraints + " constraint(s) dropped "
                + "(referenced student or zone no longer exists).";
        }

        return result;
    }

    // ---- Helper methods ----

    private static String serializeConstraint(Constraint c, List<Student> students) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":\"").append(c.getType()).append("\",");
        sb.append("\"hard\":").append(c.isHard()).append(",");
        sb.append("\"weight\":").append(c.getWeight()).append(",");

        if (c instanceof ProximityConstraint) {
            ProximityConstraint pc = (ProximityConstraint) c;
            sb.append("\"studentA\":\"").append(pc.getStudentA().getId()).append("\",");
            sb.append("\"studentB\":\"").append(pc.getStudentB().getId()).append("\",");
            sb.append("\"mode\":\"").append(pc.getMode()).append("\"");
        } else if (c instanceof ZoneConstraint) {
            ZoneConstraint zc = (ZoneConstraint) c;
            sb.append("\"student\":\"").append(zc.getStudent().getId()).append("\",");
            sb.append("\"zone\":\"").append(escapeJson(zc.getZone().getLabel())).append("\",");
            sb.append("\"mode\":\"").append(zc.getMode()).append("\"");
        } else if (c instanceof BalanceConstraint) {
            BalanceConstraint bc = (BalanceConstraint) c;
            sb.append("\"attribute\":\"").append(bc.getAttribute()).append("\"");
            if (bc.getTagName() != null) {
                sb.append(",\"tagName\":\"").append(escapeJson(bc.getTagName())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static Constraint deserializeConstraint(String json,
            List<Student> students, List<Zone> zones) {
        String type = parseStringField(json, "type");
        boolean hard = parseBoolField(json, "hard", true);
        double weight = parseDoubleField(json, "weight", 1.0);

        if ("proximity".equals(type)) {
            String aId = parseStringField(json, "studentA");
            String bId = parseStringField(json, "studentB");
            String mode = parseStringField(json, "mode");
            Student a = findStudentById(students, aId);
            Student b = findStudentById(students, bId);
            if (a != null && b != null) {
                return new ProximityConstraint(a, b, mode, hard, weight);
            }
        } else if ("zone".equals(type)) {
            String sId = parseStringField(json, "student");
            String zoneLabel = parseStringField(json, "zone");
            String mode = parseStringField(json, "mode");
            Student s = findStudentById(students, sId);
            Zone z = findZoneByLabel(zones, zoneLabel);
            if (s != null && z != null) {
                return new ZoneConstraint(s, z, mode, hard, weight);
            }
        } else if ("balance".equals(type)) {
            String attr = parseStringField(json, "attribute");
            String tagName = parseStringField(json, "tagName");
            return new BalanceConstraint(attr, tagName, hard, weight);
        }
        return null;
    }

    private static Student findStudentById(List<Student> students, String id) {
        if (id == null) return null;
        for (Student s : students) {
            if (id.equals(s.getId())) return s;
        }
        return null;
    }

    private static Zone findZoneByLabel(List<Zone> zones, String label) {
        if (label == null) return null;
        for (Zone z : zones) {
            if (label.equals(z.getLabel())) return z;
        }
        return null;
    }

    // ---- Simple JSON parsing helpers (no external library) ----

    private static String parseStringField(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return unescapeJson(json.substring(start, end));
    }

    private static int parseIntField(String json, String key, int defaultVal) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return defaultVal;
        start += pattern.length();
        StringBuilder num = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) num.append(c);
            else if (num.length() > 0) break;
        }
        try { return Integer.parseInt(num.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static double parseDoubleField(String json, String key, double defaultVal) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return defaultVal;
        start += pattern.length();
        StringBuilder num = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '-' || c == '.' || (c >= '0' && c <= '9')) num.append(c);
            else if (num.length() > 0) break;
        }
        try { return Double.parseDouble(num.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static boolean parseBoolField(String json, String key, boolean defaultVal) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return defaultVal;
        start += pattern.length();
        String rest = json.substring(start).trim();
        if (rest.startsWith("true")) return true;
        if (rest.startsWith("false")) return false;
        return defaultVal;
    }

    private static String extractArray(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start = json.indexOf("[", start);
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            if (json.charAt(i) == ']') depth--;
            if (depth == 0) return json.substring(start + 1, i);
        }
        return null;
    }

    private static List<String> splitJsonArray(String arrayContent) {
        List<String> items = new ArrayList<String>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    items.add(arrayContent.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return items;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private static String colorToHex(Color c) {
        if (c == null) return "#cccccc";
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color hexToColor(String hex) {
        if (hex == null || hex.length() < 7) return new Color(128, 128, 128);
        try {
            return new Color(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16));
        } catch (Exception e) {
            return new Color(128, 128, 128);
        }
    }

    /**
     * Container for all data loaded from a file.
     */
    public static class LoadResult {
        public Classroom classroom;
        public List<Student> students;
        public ConstraintSet constraints;
        public String loadWarning; // non-null if constraints were dropped during load
    }
}
