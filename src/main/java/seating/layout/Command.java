package seating.layout;

/**
 * Interface for undoable operations in the classroom editor.
 * Each command encapsulates a single action (add, move, rotate, delete)
 * and can reverse it. Used with UndoManager's dual Stack system.
 */
public interface Command {

    /** Executes (or re-executes) this command. */
    void execute();

    /** Reverses this command, restoring the prior state. */
    void undo();

    /**
     * Returns a human-readable description of this command.
     *
     * @return description for UI display
     */
    String describe();
}
