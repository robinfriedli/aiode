package net.robinfriedli.botify.concurrent;

import net.robinfriedli.botify.command.CommandContext;

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
