package seating.ui;

import seating.constraint.ConstraintSet;
import seating.model.Student;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Panel for managing the student list. Provides a JTable for viewing
 * students and buttons for add, edit, delete, and CSV import.
 */
public class StudentPanel extends JPanel {

    private List<Student> students;
    private ConstraintSet constraintSet;
    private ConstraintPanel constraintPanel;
    private JTable table;
    private StudentTableModel tableModel;

    /** Creates the student management panel. */
    public StudentPanel() {
        students = new ArrayList<Student>();
        setLayout(new BorderLayout(0, 4));
        setBackground(new Color(250, 250, 252));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Table
        tableModel = new StudentTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);  // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(20);  // Gender
        table.getColumnModel().getColumn(2).setPreferredWidth(25);  // Skill
        table.getColumnModel().getColumn(3).setPreferredWidth(55);  // Tags
        table.getColumnModel().getColumn(4).setPreferredWidth(25);  // Rules
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getTableHeader().setToolTipText("Name | Gender | Skill (1-5) | Tags | Rule Count");

        // Right-click context menu: quick rule creation for the clicked student
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0 || row >= students.size()) return;
                table.setRowSelectionInterval(row, row);
                final Student student = students.get(row);
                JPopupMenu menu = new JPopupMenu();

                JMenuItem proxItem = new JMenuItem("Add Proximity Rule for " + student.getName() + "...");
                proxItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        if (constraintPanel != null) constraintPanel.addProximityRuleFor(student);
                    }
                });
                menu.add(proxItem);

                JMenuItem zoneItem = new JMenuItem("Add Zone Rule for " + student.getName() + "...");
                zoneItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        if (constraintPanel != null) constraintPanel.addZoneRuleFor(student);
                    }
                });
                menu.add(zoneItem);

                menu.addSeparator();

                JMenuItem editItem = new JMenuItem("Edit Student...");
                editItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) { editStudent(); }
                });
                menu.add(editItem);

                JMenuItem delItem = new JMenuItem("Delete Student");
                delItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) { deleteStudent(); }
                });
                menu.add(delItem);

                menu.show(table, e.getX(), e.getY());
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new java.awt.GridLayout(2, 2, 4, 4));
        buttonPanel.setOpaque(false);

        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addStudent(); }
        });

        JButton editBtn = new JButton("Edit");
        editBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { editStudent(); }
        });

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { deleteStudent(); }
        });

        JButton importBtn = new JButton("Import CSV");
        importBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { importCsv(); }
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(importBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // Count label
        JLabel countLabel = new JLabel("Students: 0");
        countLabel.setFont(UIScale.font("SansSerif", Font.BOLD, 12));
        add(countLabel, BorderLayout.NORTH);
    }

    private void addStudent() {
        JTextField nameField = new JTextField(20);
        JComboBox<String> genderBox = new JComboBox<String>(new String[]{"", "M", "F"});
        JSpinner skillSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
        JTextField tagsField = new JTextField(20);

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        panel.add(new JLabel("Name:")); panel.add(nameField);
        panel.add(new JLabel("Gender:")); panel.add(genderBox);
        panel.add(new JLabel("Skill (1-5):")); panel.add(skillSpinner);
        panel.add(new JLabel("Tags (;separated):")); panel.add(tagsField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Student",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            HashSet<String> tags = parseTags(tagsField.getText());
            Student s = new Student(nameField.getText().trim(),
                (String) genderBox.getSelectedItem(),
                (Integer) skillSpinner.getValue(), tags);
            students.add(s);
            tableModel.fireTableDataChanged();
            updateCountLabel();
        }
    }

    private void editStudent() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        Student s = students.get(row);

        JTextField nameField = new JTextField(s.getName(), 20);
        JComboBox<String> genderBox = new JComboBox<String>(new String[]{"", "M", "F"});
        genderBox.setSelectedItem(s.getGender());
        JSpinner skillSpinner = new JSpinner(new SpinnerNumberModel(s.getSkillLevel(), 1, 5, 1));
        JTextField tagsField = new JTextField(String.join(";", s.getTags()), 20);

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
        panel.add(new JLabel("Name:")); panel.add(nameField);
        panel.add(new JLabel("Gender:")); panel.add(genderBox);
        panel.add(new JLabel("Skill (1-5):")); panel.add(skillSpinner);
        panel.add(new JLabel("Tags (;separated):")); panel.add(tagsField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Student",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            s.setName(nameField.getText().trim());
            s.setGender((String) genderBox.getSelectedItem());
            s.setSkillLevel((Integer) skillSpinner.getValue());
            s.getTags().clear();
            for (String tag : parseTags(tagsField.getText())) {
                s.addTag(tag);
            }
            tableModel.fireTableDataChanged();
        }
    }

    private void deleteStudent() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        Student s = students.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete student \"" + s.getName() + "\"?\nAny constraints referencing this student will also be removed.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        students.remove(row);
        // Notify listener to clean up constraints
        if (deleteListener != null) deleteListener.onStudentDeleted(s);
        tableModel.fireTableDataChanged();
        updateCountLabel();
    }

    /** Listener for student deletion events. */
    public interface StudentDeleteListener {
        void onStudentDeleted(Student student);
    }

    private StudentDeleteListener deleteListener;

    /** Sets a listener to be notified when a student is deleted. */
    public void setDeleteListener(StudentDeleteListener listener) {
        this.deleteListener = listener;
    }

    private void importCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            int count = 0;
            int skipped = 0;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                        new java.io.FileInputStream(chooser.getSelectedFile()), "UTF-8"))) {
                String line = reader.readLine(); // skip header
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", -1);
                    if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                        String name = stripQuotes(parts[0].trim());
                        String gender = parts.length > 1 ? stripQuotes(parts[1].trim()) : "";
                        int skill = 3;
                        if (parts.length > 2) {
                            try {
                                skill = Integer.parseInt(stripQuotes(parts[2].trim()));
                                skill = Math.max(1, Math.min(5, skill)); // clamp 1-5
                            } catch (NumberFormatException ex) { /* keep default */ }
                        }
                        HashSet<String> tags = new HashSet<String>();
                        if (parts.length > 3) {
                            tags = parseTags(stripQuotes(parts[3]));
                        }
                        students.add(new Student(name, gender, skill, tags));
                        count++;
                    } else {
                        skipped++;
                    }
                }
                tableModel.fireTableDataChanged();
                updateCountLabel();
                String msg = "Imported " + count + " students.";
                if (skipped > 0) msg += "\n" + skipped + " line(s) skipped (empty or malformed).";
                JOptionPane.showMessageDialog(this, msg);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error reading CSV: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String stripQuotes(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private HashSet<String> parseTags(String text) {
        HashSet<String> tags = new HashSet<String>();
        if (text == null || text.trim().isEmpty()) return tags;
        for (String tag : text.split(";")) {
            String t = tag.trim().toLowerCase();
            if (!t.isEmpty()) tags.add(t);
        }
        return tags;
    }

    private void updateCountLabel() {
        Component north = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (north instanceof JLabel) {
            ((JLabel) north).setText("Students: " + students.size());
        }
    }

    public List<Student> getStudents() { return students; }

    /** Sets the constraint set for displaying rule counts per student. */
    public void setConstraintSet(ConstraintSet cs) {
        this.constraintSet = cs;
    }

    /**
     * Sets the constraint panel used by the right-click context menu to
     * launch "Add Rule" dialogs pre-filled for the clicked student.
     */
    public void setConstraintPanel(ConstraintPanel cp) {
        this.constraintPanel = cp;
    }

    /** Refreshes the table display and count label. Call after modifying students externally. */
    public void refresh() {
        tableModel.fireTableDataChanged();
        updateCountLabel();
    }

    /** Table model for the student JTable. */
    private class StudentTableModel extends AbstractTableModel {
        private String[] columns = {"Name", "G", "Skill", "Tags", "Rules"};

        public int getRowCount() { return students.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int col) { return columns[col]; }

        public Object getValueAt(int row, int col) {
            Student s = students.get(row);
            switch (col) {
                case 0: return s.getName();
                case 1: return s.getGender();
                case 2: return s.getSkillLevel();
                case 3: return String.join("; ", s.getTags());
                case 4: return countRulesFor(s);
                default: return "";
            }
        }

        private String countRulesFor(Student s) {
            if (constraintSet == null) return "";
            int count = 0;
            for (seating.constraint.Constraint c : constraintSet.getAll()) {
                if (c.involvesStudent(s)) count++;
            }
            return count > 0 ? String.valueOf(count) : "";
        }
    }
}
