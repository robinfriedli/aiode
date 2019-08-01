package net.robinfriedli.botify.concurrent;

import net.robinfriedli.botify.command.CommandContext;

/**
 * Type of thread used to run commands that sets up the current {@link CommandContext} before running and extends
 * {@link QueuedThread} to be added to a {@link ThreadExecutionQueue}
 */
public class CommandExecutionThread extends QueuedThread {

    private final CommandContext commandContext;

    public CommandExecutionThread(CommandContext commandContext, ThreadExecutionQueue queue, Runnable runnable) {
        super(queue, runnable);
        this.commandContext = commandContext;
    }

    @Override
    public void run() {
        CommandContext.Current.set(getCommandContext());
        commandContext.startMonitoring();
        super.run();
    }

    public CommandContext getCommandContext() {
        return commandContext;
    }
}
