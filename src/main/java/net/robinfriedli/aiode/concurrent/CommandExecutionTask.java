package net.robinfriedli.aiode.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.interactions.InteractionHook;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.exceptions.handler.CommandExceptionHandlerExecutor;
import net.robinfriedli.aiode.exceptions.handler.ExceptionHandlerExecutor;

/**
 * Type of thread used to run commands that sets up the current {@link CommandContext} before running and extends
 * {@link QueuedTask} to be added to a {@link ThreadExecutionQueue}
 */
public class CommandExecutionTask extends QueuedTask {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Command command;

    public CommandExecutionTask(Command command, ThreadExecutionQueue queue, CommandManager commandManager) {
        super(queue, () -> commandManager.doRunCommand(command));
        this.command = command;
    }

    @Override
    public void run() {
        ExecutionContext.Current.set(getCommandContext());
        ThreadContext.Current.install(command);
        command.setTask(this);
        try {
            super.run();
        } finally {
            InteractionHook interactionHook = command.getContext().getInteractionHook();
            if (interactionHook != null) {
                try {
                    interactionHook.deleteOriginal().queue();
                } catch (Exception e) {
                    logger.error("Failed to complete interaction hook", e);
                }
            }
            ThreadContext.Current.clear();
        }
    }

    @Override
    protected boolean isPrivileged() {
        return command.isPrivileged();
    }

    @Override
    protected ExceptionHandlerExecutor createExceptionHandlerExecutor() {
        return new CommandExceptionHandlerExecutor(command);
    }

    public Command getCommand() {
        return command;
    }

    public CommandContext getCommandContext() {
        return command.getContext();
    }
}
