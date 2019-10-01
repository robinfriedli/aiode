package net.robinfriedli.botify.concurrent;

import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;

/**
 * Type of thread used to run commands that sets up the current {@link CommandContext} before running and extends
 * {@link QueuedThread} to be added to a {@link ThreadExecutionQueue}
 */
public class CommandExecutionThread extends QueuedThread {

    private final Command command;

    public CommandExecutionThread(Command command, ThreadExecutionQueue queue, Runnable runnable) {
        super(queue, runnable);
        this.command = command;
    }

    @Override
    public void run() {
        CommandContext.Current.set(getCommandContext());
        command.setThread(this);
        super.run();
    }

    @Override
    protected boolean isPrivileged() {
        return command.isPrivileged();
    }

    public Command getCommand() {
        return command;
    }

    public CommandContext getCommandContext() {
        return command.getContext();
    }
}
