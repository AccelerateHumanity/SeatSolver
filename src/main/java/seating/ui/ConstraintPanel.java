package seating.ui;

import seating.constraint.*;
import seating.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Panel for creating and managing seating constraints (rules).
 * Provides a list of active constraints and dialogs to add new ones.
 */
public class ConstraintPanel extends JPanel {

    private ConstraintSet constraintSet;
    private DefaultListModel<String> listModel;
    private JList<String> constraintList;
    private List<Student> students;   // reference from StudentPanel
    private List<Zone> zones;         // reference from Classroom
    private StudentPanel studentPanel; // for refreshing rules column

    /**
     * Creates the constraint editor panel.
     *
     * @param constraintSet the constraint set to manage
     * @param students the student list (for building rules)
     * @param zones the zone list (for zone rules)
     */
    public ConstraintPanel(ConstraintSet constraintSet, List<Student> students, List<Zone> zones) {
        this.constraintSet = constraintSet;
        this.students = students;
        this.zones = zones;

        setLayout(new BorderLayout(0, 4));
        setBackground(new Color(250, 250, 252));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header
        JLabel header = new JLabel("Rules: 0");
        header.setFont(new Font("SansSerif", Font.BOLD, 12));
        add(header, BorderLayout.NORTH);

        // Constraint list
        listModel = new DefaultListModel<String>();
        constraintList = new JList<String>(listModel);
        constraintList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Double-click to edit; right-click for context menu
        constraintList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editRule();
            }
            public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) return;
                int row = constraintList.locationToIndex(e.getPoint());
                if (row < 0) return;
                constraintList.setSelectedIndex(row);
                JPopupMenu popup = new JPopupMenu();
                JMenuItem editItem = new JMenuItem("Edit Rule");
                editItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ae) { editRule(); }
                });
                JMenuItem delItem = new JMenuItem("Delete Rule");
                delItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ae) { deleteRule(); }
                });
                popup.add(editItem);
                popup.add(delItem);
                popup.show(constraintList, e.getX(), e.getY());
            }
        });
        add(new JScrollPane(constraintList), BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.setOpaque(false);

        JButton proximityBtn = new JButton("+ Proximity");
        proximityBtn.setToolTipText("Keep students apart or together");
        proximityBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addProximityRule(); }
        });

        JButton zoneBtn = new JButton("+ Zone");
        zoneBtn.setToolTipText("Assign student to a zone");
        zoneBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addZoneRule(); }
        });

        JButton balanceBtn = new JButton("+ Balance");
        balanceBtn.setToolTipText("Balance attribute across groups");
        balanceBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addBalanceRule(); }
        });

        JButton editBtn = new JButton("Edit");
        editBtn.setToolTipText("Select a rule, then click to edit it (or double-click the rule)");
        editBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { editRule(); }
        });

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setToolTipText("Select a rule, then click to delete it");
        deleteBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { deleteRule(); }
        });

        buttonPanel.add(proximityBtn);
        buttonPanel.add(zoneBtn);
        buttonPanel.add(balanceBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // Populate list + header from any pre-existing constraints (e.g. after Load Project)
        refreshList();
    }

    private void addProximityRule() {
        addProximityRuleFor(null);
    }

    /**
     * Opens the Add Proximity Rule dialog, optionally pre-selecting
     * {@code preselected} as Student A. Called from the student
     * right-click context menu.
     *
     * @param preselected student to auto-fill as A, or null for no preselection
     */
    public void addProximityRuleFor(Student preselected) {
        if (students.size() < 2) {
            JOptionPane.showMessageDialog(this, "Need at least 2 students.");
            return;
        }
        Student[] studentArr = students.toArray(new Student[0]);
        JComboBox<Student> studentABox = new JComboBox<Student>(studentArr);
        JComboBox<Student> studentBBox = new JComboBox<Student>(studentArr);
        if (preselected != null) {
            studentABox.setSelectedItem(preselected);
            // Auto-advance B to a different student
            for (int i = 0; i < studentArr.length; i++) {
                if (studentArr[i] != preselected) { studentBBox.setSelectedIndex(i); break; }
            }
        } else if (studentArr.length > 1) {
            studentBBox.setSelectedIndex(1);
        }
        JComboBox<String> modeBox = new JComboBox<String>(
            new String[]{"Must sit APART", "Should sit TOGETHER"});
        JCheckBox hardCheck = new JCheckBox("Required (hard constraint)", true);

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        panel.add(new JLabel("Student A:")); panel.add(studentABox);
        panel.add(new JLabel("Student B:")); panel.add(studentBBox);
        panel.add(new JLabel("Rule:")); panel.add(modeBox);
        panel.add(new JLabel("")); panel.add(hardCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Proximity Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            Student a = (Student) studentABox.getSelectedItem();
            Student b = (Student) studentBBox.getSelectedItem();
            if (a == b) {
                JOptionPane.showMessageDialog(this, "Select two different students.");
                return;
            }
            String mode = modeBox.getSelectedIndex() == 0
                ? ProximityConstraint.APART : ProximityConstraint.TOGETHER;
            Constraint c = new ProximityConstraint(a, b, mode, hardCheck.isSelected(), 1.0);
            constraintSet.add(c);
            refreshList();
        }
    }

    private void addZoneRule() {
        addZoneRuleFor(null);
    }

    /**
     * Opens the Add Zone Rule dialog, optionally pre-selecting
     * {@code preselected} as the student. Called from the student
     * right-click context menu.
     *
     * @param preselected student to auto-fill, or null for no preselection
     */
    public void addZoneRuleFor(Student preselected) {
        if (students.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add students first.");
            return;
        }
        if (zones.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No zones defined in classroom.");
            return;
        }
        Student[] studentArr = students.toArray(new Student[0]);
        Zone[] zoneArr = zones.toArray(new Zone[0]);

        JComboBox<Student> studentBox = new JComboBox<Student>(studentArr);
        JComboBox<Zone> zoneBox = new JComboBox<Zone>(zoneArr);
        if (preselected != null) studentBox.setSelectedItem(preselected);
        JComboBox<String> modeBox = new JComboBox<String>(
            new String[]{"Must be IN zone", "Must NOT be in zone"});
        JCheckBox hardCheck = new JCheckBox("Required (hard constraint)", true);

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        panel.add(new JLabel("Student:")); panel.add(studentBox);
        panel.add(new JLabel("Zone:")); panel.add(zoneBox);
        panel.add(new JLabel("Rule:")); panel.add(modeBox);
        panel.add(new JLabel("")); panel.add(hardCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Zone Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            Student s = (Student) studentBox.getSelectedItem();
            Zone z = (Zone) zoneBox.getSelectedItem();
            String mode = modeBox.getSelectedIndex() == 0
                ? ZoneConstraint.MUST_BE_IN : ZoneConstraint.MUST_NOT_BE_IN;
            Constraint c = new ZoneConstraint(s, z, mode, hardCheck.isSelected(), 1.0);
            constraintSet.add(c);
            refreshList();
        }
    }

    private void addBalanceRule() {
        JComboBox<String> attrBox = new JComboBox<String>(
            new String[]{"Gender", "Skill Level", "Custom Tag"});
        JTextField tagField = new JTextField(15);
        tagField.setEnabled(false);

        attrBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tagField.setEnabled(attrBox.getSelectedIndex() == 2);
            }
        });

        // Balance rules are ALWAYS soft constraints — no "Required" option here.
        // (A hard balance rule would be near-impossible for the solver to satisfy
        // in most classrooms, so the option is deliberately removed.)
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Balance by:")); panel.add(attrBox);
        panel.add(new JLabel("Tag name:")); panel.add(tagField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Balance Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String attr;
            String tagName = null;
            switch (attrBox.getSelectedIndex()) {
                case 0: attr = BalanceConstraint.GENDER; break;
                case 1: attr = BalanceConstraint.SKILL; break;
                default:
                    attr = BalanceConstraint.TAG;
                    tagName = tagField.getText().trim();
                    if (tagName.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Enter a tag name.");
                        return;
                    }
                    break;
            }
            Constraint c = new BalanceConstraint(attr, tagName, false, 1.0);
            constraintSet.add(c);
            refreshList();
        }
    }

    private void deleteRule() {
        int idx = constraintList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this, "Select a rule first, then click Delete.");
            return;
        }
        constraintSet.getAll().remove(idx);
        refreshList();
    }

    /**
     * Opens an edit dialog for the currently selected rule, pre-filled
     * with the rule's existing values. On OK, replaces the rule at the
     * same list index so order is preserved.
     */
    private void editRule() {
        int idx = constraintList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this, "Select a rule first, then click Edit.");
            return;
        }
        Constraint c = constraintSet.getAll().get(idx);
        if (c instanceof ProximityConstraint) editProximity((ProximityConstraint) c, idx);
        else if (c instanceof ZoneConstraint) editZone((ZoneConstraint) c, idx);
        else if (c instanceof BalanceConstraint) editBalance((BalanceConstraint) c, idx);
    }

    private void editProximity(ProximityConstraint old, int idx) {
        if (students.size() < 2) {
            JOptionPane.showMessageDialog(this, "Need at least 2 students.");
            return;
        }
        Student[] studentArr = students.toArray(new Student[0]);
        JComboBox<Student> studentABox = new JComboBox<Student>(studentArr);
        JComboBox<Student> studentBBox = new JComboBox<Student>(studentArr);
        studentABox.setSelectedItem(old.getStudentA());
        studentBBox.setSelectedItem(old.getStudentB());
        JComboBox<String> modeBox = new JComboBox<String>(
            new String[]{"Must sit APART", "Should sit TOGETHER"});
        modeBox.setSelectedIndex(ProximityConstraint.APART.equals(old.getMode()) ? 0 : 1);
        JCheckBox hardCheck = new JCheckBox("Required (hard constraint)", old.isHard());

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        panel.add(new JLabel("Student A:")); panel.add(studentABox);
        panel.add(new JLabel("Student B:")); panel.add(studentBBox);
        panel.add(new JLabel("Rule:")); panel.add(modeBox);
        panel.add(new JLabel("")); panel.add(hardCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Proximity Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        Student a = (Student) studentABox.getSelectedItem();
        Student b = (Student) studentBBox.getSelectedItem();
        if (a == b) {
            JOptionPane.showMessageDialog(this, "Select two different students.");
            return;
        }
        String mode = modeBox.getSelectedIndex() == 0
            ? ProximityConstraint.APART : ProximityConstraint.TOGETHER;
        Constraint updated = new ProximityConstraint(a, b, mode, hardCheck.isSelected(), old.getWeight());
        constraintSet.getAll().set(idx, updated);
        refreshList();
    }

    private void editZone(ZoneConstraint old, int idx) {
        if (students.isEmpty() || zones.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Need students and zones.");
            return;
        }
        Student[] studentArr = students.toArray(new Student[0]);
        Zone[] zoneArr = zones.toArray(new Zone[0]);
        JComboBox<Student> studentBox = new JComboBox<Student>(studentArr);
        JComboBox<Zone> zoneBox = new JComboBox<Zone>(zoneArr);
        studentBox.setSelectedItem(old.getStudent());
        zoneBox.setSelectedItem(old.getZone());
        JComboBox<String> modeBox = new JComboBox<String>(
            new String[]{"Must be IN zone", "Must NOT be in zone"});
        modeBox.setSelectedIndex(ZoneConstraint.MUST_BE_IN.equals(old.getMode()) ? 0 : 1);
        JCheckBox hardCheck = new JCheckBox("Required (hard constraint)", old.isHard());

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        panel.add(new JLabel("Student:")); panel.add(studentBox);
        panel.add(new JLabel("Zone:")); panel.add(zoneBox);
        panel.add(new JLabel("Rule:")); panel.add(modeBox);
        panel.add(new JLabel("")); panel.add(hardCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Zone Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        Student s = (Student) studentBox.getSelectedItem();
        Zone z = (Zone) zoneBox.getSelectedItem();
        String mode = modeBox.getSelectedIndex() == 0
            ? ZoneConstraint.MUST_BE_IN : ZoneConstraint.MUST_NOT_BE_IN;
        Constraint updated = new ZoneConstraint(s, z, mode, hardCheck.isSelected(), old.getWeight());
        constraintSet.getAll().set(idx, updated);
        refreshList();
    }

    private void editBalance(BalanceConstraint old, int idx) {
        JComboBox<String> attrBox = new JComboBox<String>(
            new String[]{"Gender", "Skill Level", "Custom Tag"});
        // Preselect based on old attribute
        if (BalanceConstraint.GENDER.equals(old.getAttribute())) attrBox.setSelectedIndex(0);
        else if (BalanceConstraint.SKILL.equals(old.getAttribute())) attrBox.setSelectedIndex(1);
        else attrBox.setSelectedIndex(2);
        final JTextField tagField = new JTextField(old.getTagName() == null ? "" : old.getTagName(), 15);
        tagField.setEnabled(attrBox.getSelectedIndex() == 2);
        attrBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tagField.setEnabled(attrBox.getSelectedIndex() == 2);
            }
        });

        // No "Required" checkbox — balance rules are always soft (matches add dialog)
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Balance by:")); panel.add(attrBox);
        panel.add(new JLabel("Tag name:")); panel.add(tagField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Balance Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String attr;
        String tagName = null;
        switch (attrBox.getSelectedIndex()) {
            case 0: attr = BalanceConstraint.GENDER; break;
            case 1: attr = BalanceConstraint.SKILL; break;
            default:
                attr = BalanceConstraint.TAG;
                tagName = tagField.getText().trim();
                if (tagName.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Enter a tag name.");
                    return;
                }
                break;
        }
        Constraint updated = new BalanceConstraint(attr, tagName, false, old.getWeight());
        constraintSet.getAll().set(idx, updated);
        refreshList();
    }

    /**
     * Removes all constraints that reference the given student.
     * Called when a student is deleted to avoid orphaned constraints.
     */
    public void removeConstraintsFor(Student student) {
        java.util.Iterator<Constraint> it = constraintSet.getAll().iterator();
        while (it.hasNext()) {
            if (it.next().involvesStudent(student)) {
                it.remove();
            }
        }
        refreshList();
    }

    /**
     * Removes all ZoneConstraints that reference the given zone.
     * Called when a zone is deleted to avoid orphaned "phantom" constraints
     * that reference a zone no longer in the classroom.
     */
    public void removeConstraintsForZone(Zone zone) {
        java.util.Iterator<Constraint> it = constraintSet.getAll().iterator();
        while (it.hasNext()) {
            Constraint c = it.next();
            if (c instanceof ZoneConstraint && ((ZoneConstraint) c).getZone() == zone) {
                it.remove();
            }
        }
        refreshList();
    }

    /** Refreshes the list display from the constraint set. */
    public void refreshList() {
        listModel.clear();
        for (Constraint c : constraintSet.getAll()) {
            listModel.addElement(c.describe());
        }
        Component north = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (north instanceof JLabel) {
            ((JLabel) north).setText("Rules: " + constraintSet.size());
        }
        if (studentPanel != null) studentPanel.refresh();
    }

    /** Sets the StudentPanel reference for refreshing after constraint changes. */
    public void setStudentPanel(StudentPanel sp) { this.studentPanel = sp; }

    public ConstraintSet getConstraintSet() { return constraintSet; }
}
