package seating.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a student who can be assigned to a seat.
 * Students have a name, gender, skill level, and custom tags
 * used by the constraint system for balancing and zone rules.
 */
public class Student {

    private static int nextId = 1;

    /** Resets the ID counter. Call when loading a new project. */
    public static void resetIdCounter() { nextId = 1; }

    private String id;
    private String name;
    private String gender;     // "M", "F", or ""
    private int skillLevel;    // 1-5 scale
    private Set<String> tags;  // custom labels like "honors", "ESL", "IEP"

    /**
     * Creates a new student.
     *
     * @param name the student's display name
     */
    public Student(String name) {
        this.id = "s_" + nextId++;
        this.name = name;
        this.gender = "";
        this.skillLevel = 3;
        this.tags = new HashSet<String>();
    }

    /**
     * Creates a student with all fields specified.
     *
     * @param name student name
     * @param gender "M", "F", or ""
     * @param skillLevel 1-5 scale
     * @param tags set of custom tag strings
     */
    public Student(String name, String gender, int skillLevel, Set<String> tags) {
        this.id = "s_" + nextId++;
        this.name = name;
        this.gender = gender;
        this.skillLevel = Math.max(1, Math.min(5, skillLevel));
        this.tags = (tags != null) ? tags : new HashSet<String>();
    }

    public void addTag(String tag) { tags.add(tag.toLowerCase().trim()); }
    public void removeTag(String tag) { tags.remove(tag.toLowerCase().trim()); }
    public boolean hasTag(String tag) { return tags.contains(tag.toLowerCase().trim()); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int level) { this.skillLevel = Math.max(1, Math.min(5, level)); }

    public Set<String> getTags() { return tags; }

    public String toString() {
        return name;
    }

    public int hashCode() {
        return id.hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Student)) return false;
        return id.equals(((Student) obj).id);
    }
}
