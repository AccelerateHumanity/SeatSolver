package seating.layout;

import java.util.List;

/**
 * Command that wraps a list of sub-commands and executes/undoes them
 * as a single unit. Use when a user action produces several mutations
 * (e.g. rotating a multi-selection) that the user perceives as one
 * action and should undo/redo together.
 */
public class CompoundCommand implements Command {

    private final List<Command> commands;
    private final String label;

    public CompoundCommand(List<Command> commands) {
        this(commands, null);
    }

    public CompoundCommand(List<Command> commands, String label) {
        this.commands = commands;
        this.label = label;
    }

    public void execute() {
        for (Command c : commands) c.execute();
    }

    public void undo() {
        // Undo in reverse order so effects unwind correctly.
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    public String describe() {
        if (label != null) return label;
        return commands.size() + " actions";
    }
}
