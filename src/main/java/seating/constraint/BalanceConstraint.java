package seating.constraint;

import seating.model.*;
import seating.solver.SeatGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constraint that distributes students evenly across desk groups
 * based on an attribute: gender, skill level, or a custom tag.
 *
 * <p>For example, "balance gender across groups" ensures each group table
 * has a roughly equal mix of M and F students rather than clustering.</p>
 */
public class BalanceConstraint implements Constraint {

    /** Balance by gender (M/F). */
    public static final String GENDER = "gender";
    /** Balance by skill level (1-5). */
    public static final String SKILL = "skill";
    /** Balance by presence of a custom tag. */
    public static final String TAG = "tag";

    private String attribute; // "gender", "skill", or "tag"
    private String tagName;   // only used when attribute == "tag"
    private boolean hard;
    private double weight;

    /**
     * Creates a balance constraint.
     *
     * @param attribute what to balance: "gender", "skill", or "tag"
     * @param tagName the tag name (only for attribute="tag", null otherwise)
     * @param hard whether this must be satisfied
     * @param weight relative importance
     */
    public BalanceConstraint(String attribute, String tagName,
                              boolean hard, double weight) {
        this.attribute = attribute;
        this.tagName = tagName;
        this.hard = hard;
        this.weight = weight;
    }

    public double evaluate(SeatingArrangement arrangement, SeatGraph graph) {
        // Group students by their desk
        HashMap<Desk, List<Student>> deskGroups = new HashMap<Desk, List<Student>>();
        HashMap<Seat, Student> map = arrangement.getAssignmentMap();

        for (Map.Entry<Seat, Student> entry : map.entrySet()) {
            Desk desk = entry.getKey().getParentDesk();
            if (desk == null) continue;
            if (!deskGroups.containsKey(desk)) {
                deskGroups.put(desk, new ArrayList<Student>());
            }
            deskGroups.get(desk).add(entry.getValue());
        }

        if (deskGroups.size() <= 1) return 1.0; // nothing to balance

        // Calculate the proportion of "target" students in each group
        List<Double> proportions = new ArrayList<Double>();
        for (List<Student> group : deskGroups.values()) {
            if (group.isEmpty()) continue;
            int targetCount = 0;
            for (Student s : group) {
                if (matchesTarget(s)) targetCount++;
            }
            proportions.add((double) targetCount / group.size());
        }

        if (proportions.isEmpty()) return 1.0;

        // Score: 1.0 - standard deviation of proportions (lower variance = better)
        double mean = 0;
        for (double p : proportions) mean += p;
        mean /= proportions.size();

        double variance = 0;
        for (double p : proportions) variance += (p - mean) * (p - mean);
        variance /= proportions.size();

        double stdDev = Math.sqrt(variance);

        // Relative scoring: compare against the WORST possible deviation
        // (all target students on one desk). This makes "pretty good balance"
        // show as 80-90%, not 25%, since absolute stdDev is misleading when
        // desks have different sizes.
        //
        // Worst-case stdDev occurs when one desk has ratio=1.0 and all
        // others have ratio=0.0 (or vice versa). With N groups, worst ≈ 0.5.
        // Curve: score = 1 - (stdDev / 0.5)^0.6  (concave — generous)
        double worstCase = 0.5;
        double normalized = Math.min(1.0, stdDev / worstCase);
        return Math.max(0.0, 1.0 - Math.pow(normalized, 0.6));
    }

    private boolean matchesTarget(Student s) {
        if (GENDER.equals(attribute)) {
            return "F".equalsIgnoreCase(s.getGender());
        } else if (SKILL.equals(attribute)) {
            return s.getSkillLevel() >= 4; // "high skill"
        } else if (TAG.equals(attribute) && tagName != null) {
            return s.hasTag(tagName);
        }
        return false;
    }

    public boolean isSatisfied(SeatingArrangement arrangement, SeatGraph graph) {
        return evaluate(arrangement, graph) >= 0.5; // 50% threshold — soft, generous
    }

    public boolean isHard() { return hard; }
    public double getWeight() { return weight; }
    public String getType() { return "balance"; }

    public String describe() {
        String target;
        if (GENDER.equals(attribute)) {
            target = "gender";
        } else if (SKILL.equals(attribute)) {
            target = "skill level";
        } else {
            target = "\"" + tagName + "\" tag";
        }
        String strength = hard ? "[REQUIRED] " : "";
        return strength + "Balance " + target + " evenly across desk groups";
    }

    public boolean involvesStudent(Student student) {
        return false; // balance constraints apply globally, not to specific students
    }

    public String getAttribute() { return attribute; }
    public String getTagName() { return tagName; }
}
