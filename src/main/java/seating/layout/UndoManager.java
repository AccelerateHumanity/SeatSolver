package seating.layout;

import java.util.Stack;

/**
 * Manages undo/redo operations using the Command pattern with a dual Stack.
 * Every desk operation (add, move, rotate, delete) is wrapped in a Command
 * and pushed onto the undo stack. Undoing pops from the undo stack
 * and pushes to the redo stack.
 *
 * <p>This class demonstrates the Stack data structure from CS III.</p>
 */
public class UndoManager {

    private Stack<Command> undoStack;
    private Stack<Command> redoStack;

    /** Creates a new UndoManager with empty stacks. */
    public UndoManager() {
        undoStack = new Stack<Command>();
        redoStack = new Stack<Command>();
    }

    /**
     * Executes a command and pushes it onto the undo stack.
     * Clears the redo stack since a new action invalidates
     * the redo history.
     *
     * @param cmd the command to execute
     */
    public void execute(Command cmd) {
        cmd.execute();
        pushExecuted(cmd);
    }

    /**
     * Registers a command that was ALREADY executed (its state mutation is
     * already applied). Useful when a caller wants to mutate state first,
     * inspect the result, and only then commit to the undo stack — for
     * example after collision-check-then-rollback logic during rotation.
     *
     * @param cmd the already-executed command to register for undo
     */
    public void pushExecuted(Command cmd) {
        undoStack.push(cmd);
        redoStack.clear();
        while (undoStack.size() > 100) {
            undoStack.remove(0);
        }
    }

    /**
     * Undoes the most recent command. Pops from the undo stack,
     * calls undo(), and pushes onto the redo stack.
     *
     * @return true if an operation was undone, false if nothing to undo
     */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        Command cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
        return true;
    }

    /**
     * Redoes the most recently undone command. Pops from the redo stack,
     * calls execute(), and pushes back onto the undo stack.
     *
     * @return true if an operation was redone, false if nothing to redo
     */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        Command cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);
        return true;
    }

    /** Returns true if there are operations that can be undone. */
    public boolean canUndo() { return !undoStack.isEmpty(); }

    /** Returns true if there are operations that can be redone. */
    public boolean canRedo() { return !redoStack.isEmpty(); }

    /**
     * Returns the description of the command that would be undone next.
     *
     * @return description string, or empty string if nothing to undo
     */
    public String getUndoDescription() {
        return undoStack.isEmpty() ? "" : undoStack.peek().describe();
    }

    /**
     * Returns the description of the command that would be redone next.
     *
     * @return description string, or empty string if nothing to redo
     */
    public String getRedoDescription() {
        return redoStack.isEmpty() ? "" : redoStack.peek().describe();
    }

    /** Clears both stacks. */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
