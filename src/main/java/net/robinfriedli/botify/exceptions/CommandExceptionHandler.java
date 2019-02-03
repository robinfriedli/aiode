package net.robinfriedli.botify.exceptions;

import org.slf4j.Logger;

import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.AlertService;

public class CommandExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final CommandContext commandContext;
    private final Logger logger;

    public CommandExceptionHandler(CommandContext commandContext, Logger logger) {
        this.commandContext = commandContext;
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        StringBuilder responseBuilder = new StringBuilder();
        MessageChannel channel = commandContext.getChannel();
        String command = commandContext.getMessage().getContentDisplay();
        AlertService alertService = new AlertService(logger);

        Throwable exception = e instanceof CommandRuntimeException ? e.getCause() : e;
        responseBuilder.append(String.format("Uncaught exception while handling command '%s'. Error (%s): %s",
            command, exception.getClass().getSimpleName(), exception.getMessage()));
        recursiveCause(responseBuilder, exception);
        alertService.send(responseBuilder.toString(), channel);
        logger.error(String.format("Exception while handling command %s on guild %s", command, commandContext.getGuild().getName()), exception);
    }

    private void recursiveCause(StringBuilder responseBuilder, Throwable exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            responseBuilder.append(System.lineSeparator()).append(String.format("Caused by %s: %s", cause.getClass().getSimpleName(), cause.getMessage()));
            recursiveCause(responseBuilder, cause);
        }
    }
}
