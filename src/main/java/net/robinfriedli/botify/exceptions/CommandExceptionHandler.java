package net.robinfriedli.botify.exceptions;

import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.AlertService;

public class CommandExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final CommandContext commandContext;

    public CommandExceptionHandler(CommandContext commandContext) {
        this.commandContext = commandContext;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        StringBuilder responseBuilder = new StringBuilder();
        MessageChannel channel = commandContext.getChannel();
        String command = commandContext.getMessage().getContentDisplay();
        AlertService alertService = new AlertService();

        Throwable exception = e instanceof CommandRuntimeException ? e.getCause() : e;
        responseBuilder.append(String.format("Uncaught exception while handling command '%s'. Error (%s): %s",
            command, exception.getClass().getSimpleName(), exception.getMessage()));
        recursiveCause(responseBuilder, exception);
        alertService.send(responseBuilder.toString(), channel);
        e.printStackTrace();
        // TODO logging
    }

    private void recursiveCause(StringBuilder responseBuilder, Throwable exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            responseBuilder.append(System.lineSeparator()).append(String.format("Caused by %s: %s", cause.getClass().getSimpleName(), cause.getMessage()));
            recursiveCause(responseBuilder, cause);
        }
    }
}
